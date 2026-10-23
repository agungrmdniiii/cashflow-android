package com.cashflow.app.data.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.cashflow.app.data.preferences.PreferenceManager
import com.cashflow.app.data.repository.CashflowRepository
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.LocalTime

/** Inexact local checks: Android may defer these while the device is asleep. */
class LocalReminderJob : JobService() {
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        running = CoroutineScope(Dispatchers.IO).launch {
            var retry = false
            try {
                val prefs = PreferenceManager(this@LocalReminderJob)
                val repo = (application as com.cashflow.app.CashflowApplication).repository
                repo.awaitInitialLoad()
                RecurringSchedulerService.processDueRecurringTransactions(repo)
                val today = LocalDate.now()
                repo.refreshCalendar(today)
                NotificationHelper.checkAndNotifyBudgets(this@LocalReminderJob, repo.budgets.value, prefs, today)
                fun notifyOnce(key: String, id: Int, title: String, message: String, channel: String) {
                    if (!prefs.getBoolean("${key}_$today") && NotificationHelper.showLocalNotification(
                            this@LocalReminderJob, id, title, message, channel)) {
                        prefs.setBoolean("${key}_$today", true)
                    }
                }
                if (prefs.isDailyReminderEnabled() && !LocalTime.now().isBefore(LocalTime.parse(prefs.getDailyReminderTime()))) {
                    notifyOnce("daily", 3001, "Lengkapi catatan hari ini", "Catat pemasukan dan pengeluaran hari ini agar ringkasan keuangan tetap lengkap.", NotificationHelper.CHANNEL_DAILY_REMINDER)
                }
                if (RecurringSchedulerService.getUpcomingBills(repo, repo.recurringTransactions.value, 0, today).isNotEmpty()) {
                    notifyOnce("due", 3002, "Periksa tagihan hari ini", "Ada tagihan yang jatuh tempo. Buka jadwal untuk meninjau dan mencatat pembayarannya.", NotificationHelper.CHANNEL_DAILY_REMINDER)
                }
                if (prefs.isBackupReminderEnabled() && System.currentTimeMillis() - prefs.getLastBackupTime() >= 7L * 24 * 60 * 60 * 1000) {
                    notifyOnce("backup", 3003, "Cadangkan catatan keuangan", "Simpan cadangan terbaru agar catatan Anda dapat dipulihkan saat berganti HP atau kehilangan data.", NotificationHelper.CHANNEL_BACKUP_REMINDER)
                }
            } catch (_: CancellationException) {
                return@launch
            } catch (_: Exception) {
                retry = true
            } finally {
                jobFinished(params, retry)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return true
    }

    companion object {
        const val JOB_ID = 4101
        fun schedule(context: Context) {
            NotificationHelper.initNotificationChannels(context)
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (scheduler.getPendingJob(JOB_ID) == null) {
                scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, LocalReminderJob::class.java))
                    .setPeriodic(15 * 60 * 1000L).setPersisted(true).build())
            }
        }
    }
}
