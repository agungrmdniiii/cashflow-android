package com.cashflow.app.data.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.PersistableBundle
import com.cashflow.app.data.backup.*
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZonedDateTime

/** Best-effort time windows; network and battery constraints are never overridden by a deadline. */
class AutomaticBackupJob : JobService() {
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        running = CoroutineScope(Dispatchers.IO).launch {
            var retry = false
            var cancelled = false
            var destination: BackupDestination? = null
            try {
                val store = BackupSettingsStore(this@AutomaticBackupJob)
                val settings = store.automation()
                val now = ZonedDateTime.now()
                val window = settings.nextWindow(now, store.automaticSuccess(settings.destination).takeIf { it > 0 }?.let(Instant::ofEpochMilli))
                val requestedRetry = params.extras.getBoolean("manual_retry") &&
                    params.extras.getString("destination") == settings.destination.name &&
                    store.error(settings.destination) != null &&
                    System.currentTimeMillis() < params.extras.getLong("window_end")
                val eligible = if (params.extras.getBoolean("manual_retry")) requestedRetry
                    else window != null && !now.isBefore(window.start) && now.isBefore(window.end)
                if (settings.enabled && eligible && ready(this@AutomaticBackupJob, store)) {
                    destination = settings.destination
                    BackupCoordinator(this@AutomaticBackupJob).runAutomaticOrDrive(settings.destination, automatic = true,
                        requestedNetwork = if (Build.VERSION.SDK_INT >= 28) params.network else null)
                }
            } catch (failure: Exception) {
                val store = BackupSettingsStore(this@AutomaticBackupJob)
                if (failure is CancellationException && (failure !is TimeoutCancellationException || !currentCoroutineContext().isActive)) {
                    // Restore cancellation is normal; a Google request timeout is an actual failure.
                    cancelled = !currentCoroutineContext().isActive
                    retry = !cancelled && store.automation().enabled
                } else {
                    val options = store.automation()
                    destination?.takeIf { options.enabled && options.destination == it }?.let {
                        store.recordError(it, NotificationHelper.backupFailureMessage(it, failure))
                        runCatching { NotificationHelper.showBackupFailure(this@AutomaticBackupJob, it, params.extras.getLong("window_end"), failure) }
                    }
                    retry = options.enabled && !store.preferences.getBoolean("drive_reauth", false) &&
                        System.currentTimeMillis() < params.extras.getLong("window_end")
                }
            } finally {
                if (!cancelled) withContext(NonCancellable + Dispatchers.Main) {
                    jobFinished(params, retry)
                    // jobFinished posts to the main queue; schedule its successor after that message.
                    if (!retry) Handler(mainLooper).post { schedule(this@AutomaticBackupJob, replace = true) }
                }
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return BackupSettingsStore(this).automation().enabled
    }

    companion object {
        const val JOB_ID = 4102

        private fun ready(context: Context, store: BackupSettingsStore): Boolean = !store.preferences.getBoolean("data_change", false) && when (store.automation().destination) {
            BackupDestination.LOCAL -> store.localFolder != null
            BackupDestination.DRIVE -> store.driveAccount != null && BackupPasswordStore(context).isConfigured() &&
                !store.preferences.getBoolean("drive_reauth", false)
        }

        fun retry(context: Context, destination: String?, failureWindow: Long) {
            val store = BackupSettingsStore(context)
            val options = store.automation()
            if (!options.enabled || options.destination.name != destination || store.error(options.destination) == null ||
                store.preferences.getLong("failure_notice_$destination", 0) != failureWindow || !ready(context, store)) return
            runCatching { schedule(context, replace = true, retryNow = true) }.onFailure {
                store.recordError(options.destination, "Android belum dapat menjadwalkan cadangan. Coba lagi melalui Pengaturan.")
            }
        }

        private fun schedule(context: Context, replace: Boolean, retryNow: Boolean) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            val store = BackupSettingsStore(context)
            val options = store.automation()
            if (!options.enabled) {
                scheduler.cancel(JOB_ID)
                BackupDestination.entries.forEach { NotificationHelper.clearBackupFailure(context, it) }
                return
            }
            BackupDestination.entries.filter { it != options.destination }.forEach { NotificationHelper.clearBackupFailure(context, it) }
            if (!ready(context, store)) { scheduler.cancel(JOB_ID); return }
            val now = ZonedDateTime.now()
            val pending = scheduler.getPendingJob(JOB_ID)?.extras
            if (!replace && store.error(options.destination) != null && pending?.getBoolean("manual_retry") == true && pending.getString("destination") == options.destination.name &&
                System.currentTimeMillis() < pending.getLong("window_end")) return
            val window = options.nextWindow(now, store.automaticSuccess(options.destination).takeIf { it > 0 }?.let(Instant::ofEpochMilli)) ?: return
            val signature = listOf(options.frequency.name, options.destination.name, options.network.name, options.startHour, options.endHour,
                options.monthDay, window.start.toInstant().toEpochMilli(), window.end.toInstant().toEpochMilli()).joinToString("|")
            if (!replace && scheduler.getPendingJob(JOB_ID)?.extras?.getString("schedule") == signature) return
            val extras = PersistableBundle().apply {
                putString("schedule", signature)
                putLong("window_end", window.end.toInstant().toEpochMilli())
                putString("destination", options.destination.name)
                if (retryNow) {
                    putBoolean("manual_retry", true)
                    putLong("window_end", System.currentTimeMillis() + 60 * 60 * 1000L)
                }
            }
            val builder = JobInfo.Builder(JOB_ID, ComponentName(context, AutomaticBackupJob::class.java))
                .setPersisted(true).setExtras(extras).setRequiresBatteryNotLow(true)
                .setMinimumLatency(if (retryNow) 0 else (window.start.toInstant().toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(0))
                .setBackoffCriteria(15 * 60 * 1000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            if (options.destination == BackupDestination.DRIVE) builder.setRequiredNetworkType(
                if (options.network == BackupNetwork.WIFI) JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY)
            check(scheduler.schedule(builder.build()) == JobScheduler.RESULT_SUCCESS) { "Android belum dapat menjadwalkan cadangan. Coba lagi." }
        }

        fun schedule(context: Context, replace: Boolean = false) = schedule(context, replace, retryNow = false)
    }
}

class BackupClockReceiver : BroadcastReceiver() {
    companion object { const val ACTION_RETRY = "com.cashflow.app.RETRY_AUTOMATIC_BACKUP" }
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_RETRY)
            AutomaticBackupJob.retry(context, intent.getStringExtra("destination"), intent.getLongExtra("failure_window", 0))
        else if (intent.action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED))
            AutomaticBackupJob.schedule(context, replace = intent.action != Intent.ACTION_BOOT_COMPLETED)
    }
}
