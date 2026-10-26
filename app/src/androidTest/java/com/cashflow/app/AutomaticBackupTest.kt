package com.cashflow.app

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.app.NotificationManager
import android.app.Notification
import android.content.ComponentName
import android.os.PersistableBundle
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.cashflow.app.data.backup.*
import com.cashflow.app.data.preferences.PreferenceManager
import com.cashflow.app.data.service.AutomaticBackupJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalTime
import java.time.Instant
import java.time.ZonedDateTime

class AutomaticBackupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun preservingSettings(block: (BackupCoordinator, BackupSettingsStore) -> Unit) {
        val coordinator = BackupCoordinator(context)
        val store = coordinator.store
        val original = store.preferences.all.toMap()
        val legacy = PreferenceManager(context)
        val time = legacy.getLastBackupTime()
        val format = legacy.getLastBackupFormat()
        try { block(coordinator, store) }
        finally {
            val edit = store.preferences.edit().clear()
            original.forEach { (key, value) -> when (value) {
                is String -> edit.putString(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is Float -> edit.putFloat(key, value)
            } }
            check(edit.commit())
            legacy.setBackupSaved(format, time)
            AutomaticBackupJob.schedule(context, replace = true)
        }
    }

    @Test fun completeArchiveCanBeSavedVerifiedEncryptedAndInspectedWithoutEditingTransactions() = preservingSettings { coordinator, store -> runBlocking {
        val repo = (context.applicationContext as CashflowApplication).repository
        repo.awaitInitialLoad()
        val before = repo.transactions.value.toList()
        val source = coordinator.prepareLocal()
        val saved = File.createTempFile("backup-audit-", ".zip", context.cacheDir)
        val encrypted = File.createTempFile("backup-audit-", ".duitaing", context.cacheDir)
        var restored: File? = null
        try {
            coordinator.saveLocal(source, Uri.fromFile(saved))
            assertTrue(store.success(BackupDestination.LOCAL) > 0)
            assertArrayEquals(source.readBytes(), saved.readBytes())
            assertEquals(before.size, coordinator.inspect(saved).validation.summary.transactionCount)
            BackupArchive.encrypt(saved, encrypted, "audit-recovery-password")
            val prepared = coordinator.inspect(encrypted, "audit-recovery-password")
            restored = prepared.zip
            assertEquals(before.size, prepared.validation.summary.transactionCount)
            assertNotNull(prepared.zip)
            assertArrayEquals(saved.readBytes(), prepared.zip!!.readBytes())
            assertEquals(before, repo.transactions.value)
        } finally { source.delete(); saved.delete(); encrypted.delete(); restored?.delete() }
    } }

    @Test fun failedLocalSaveDoesNotAdvanceTheLastSuccessfulBackup() = preservingSettings { coordinator, store -> runBlocking {
        val source = coordinator.prepareLocal()
        store.recordSuccess(BackupDestination.LOCAL, false, 1234)
        try {
            try { coordinator.saveLocal(source, Uri.fromFile(context.cacheDir)); fail("A directory is not a writable backup document") }
            catch (_: Exception) {
                assertEquals(1234L, store.success(BackupDestination.LOCAL))
                assertFalse(store.error(BackupDestination.LOCAL).isNullOrBlank())
            }
        } finally { source.delete() }
    } }

    @Test fun recoveryPasswordIsDeviceProtectedAndDoesNotEnterThePortableBackup() = preservingSettings { _, store -> runBlocking {
        val password = "audit-recovery-password"
        val passwords = BackupPasswordStore(context)
        passwords.save(password)
        assertTrue(passwords.isConfigured())
        assertEquals(password, passwords.read())
        assertFalse(store.preferences.all.values.toString().contains(password))
        val repo = (context.applicationContext as CashflowApplication).repository
        repo.awaitInitialLoad()
        val portable = repo.createBackupJson().getOrThrow()
        assertFalse(portable.contains(password))
        assertFalse(portable.contains("protected_password"))
    } }

    @Suppress("DEPRECATION")
    @Test fun schedulerEnforcesSelectedNetworkPersistsAndCancelsWhenDisabled() = preservingSettings { _, store ->
        val scheduler = context.getSystemService(JobScheduler::class.java)
        store.driveAccount = "backup-audit@example.invalid"
        store.preferences.edit().remove("drive_reauth").remove("automatic_success_DRIVE").commit()
        BackupPasswordStore(context).save("audit-recovery-password")
        val hour = LocalTime.now().hour
        val options = BackupAutomation(enabled = true, startHour = (hour + 1) % 24, endHour = (hour + 2) % 24)
        store.save(options)
        AutomaticBackupJob.schedule(context, replace = true)
        val wifi = scheduler.getPendingJob(AutomaticBackupJob.JOB_ID)!!
        assertTrue(wifi.isPersisted)
        assertEquals(JobInfo.NETWORK_TYPE_UNMETERED, wifi.networkType)
        assertTrue(wifi.minLatencyMillis > 0)
        assertFalse(wifi.extras.toString().contains("example.invalid"))
        store.save(options.copy(network = BackupNetwork.ANY))
        AutomaticBackupJob.schedule(context, replace = true)
        assertEquals(JobInfo.NETWORK_TYPE_ANY, scheduler.getPendingJob(AutomaticBackupJob.JOB_ID)!!.networkType)
        store.save(options.copy(enabled = false))
        AutomaticBackupJob.schedule(context, replace = true)
        assertNull(scheduler.getPendingJob(AutomaticBackupJob.JOB_ID))
    }

    @Test fun malformedDeviceSettingsFallBackToAValidDisabledSchedule() = preservingSettings { _, store ->
        store.preferences.edit().putString("enabled", "invalid").putInt("start", 100).putInt("end", 23).putString("frequency", "UNKNOWN").commit()
        val options = store.automation()
        assertFalse(options.enabled)
        assertNotEquals(options.startHour, options.endHour)
        assertEquals(BackupFrequency.DAILY, options.frequency)
    }

    @Test fun appStartupPreservesAnUnexpiredUserRetry() = preservingSettings { _, store ->
        store.driveAccount = "backup-audit@example.invalid"
        BackupPasswordStore(context).save("audit-recovery-password")
        store.preferences.edit().remove("drive_reauth").commit()
        store.recordError(BackupDestination.DRIVE, "Audit retry")
        val hour = LocalTime.now().hour
        store.save(BackupAutomation(enabled = true, startHour = (hour + 1) % 24, endHour = (hour + 2) % 24))
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val extras = PersistableBundle().apply {
            putBoolean("manual_retry", true)
            putString("destination", "DRIVE")
            putLong("window_end", System.currentTimeMillis() + 3600000)
        }
        scheduler.schedule(JobInfo.Builder(AutomaticBackupJob.JOB_ID, ComponentName(context, AutomaticBackupJob::class.java))
            .setMinimumLatency(3600000).setExtras(extras).build())
        AutomaticBackupJob.schedule(context)
        assertTrue("Opening the app must not postpone a requested retry", scheduler.getPendingJob(AutomaticBackupJob.JOB_ID)!!.extras.getBoolean("manual_retry"))
    }

    @Test fun failedAutomaticBackupPostsAPrivateActionableNotification() = preservingSettings { _, store ->
        org.junit.Assume.assumeTrue(com.cashflow.app.data.service.NotificationHelper.canSendNotifications(context))
        val manager = context.getSystemService(NotificationManager::class.java)
        val hour = LocalTime.now().hour
        store.localFolder = Uri.parse("content://com.cashflow.invalid/tree/missing")
        store.preferences.edit().remove("automatic_success_LOCAL").remove("error_LOCAL").remove("failure_notice_LOCAL").commit()
        store.save(BackupAutomation(enabled = true, destination = BackupDestination.LOCAL, startHour = hour, endHour = (hour + 1) % 24))
        try {
            AutomaticBackupJob.schedule(context, replace = true)
            android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("cmd jobscheduler run -f com.cashflow.app 4102")).bufferedReader().use { it.readText() }
            val deadline = android.os.SystemClock.uptimeMillis() + 10000
            while (manager.activeNotifications.none { it.id == 3104 } && android.os.SystemClock.uptimeMillis() < deadline) android.os.SystemClock.sleep(100)
            assertNotNull("The actual backup operation must have failed", store.error(BackupDestination.LOCAL))
            val posted = manager.activeNotifications.firstOrNull { it.id == 3104 }
            assertNotNull("A failed automatic backup needs an actionable notification", posted)
            val notification = posted!!.notification
            assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
            assertEquals(Notification.CATEGORY_ERROR, notification.category)
            assertNotNull(notification.getLargeIcon())
            assertEquals("Coba lagi", notification.actions.single().title.toString())
            assertFalse(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("com.cashflow.invalid"))
            assertNotNull(notification.contentIntent)
            notification.actions.single().actionIntent.send()
            val retryDeadline = android.os.SystemClock.uptimeMillis() + 3000
            val scheduler = context.getSystemService(JobScheduler::class.java)
            while (scheduler.getPendingJob(AutomaticBackupJob.JOB_ID)?.extras?.getBoolean("manual_retry") != true && android.os.SystemClock.uptimeMillis() < retryDeadline) android.os.SystemClock.sleep(50)
            val requested = scheduler.getPendingJob(AutomaticBackupJob.JOB_ID)!!
            assertTrue("The action must request an actual retry", requested.extras.getBoolean("manual_retry"))
            assertTrue(requested.isRequireBatteryNotLow)
            assertEquals(0L, requested.maxExecutionDelayMillis)
            assertTrue(requested.extras.getLong("window_end") <= System.currentTimeMillis() + 3600000)
            // A notification retained by Android must never restart automation after the user turns it off.
            store.save(store.automation().copy(enabled = false))
            AutomaticBackupJob.schedule(context, replace = true)
            notification.actions.single().actionIntent.send()
            android.os.SystemClock.sleep(300)
            assertNull(context.getSystemService(JobScheduler::class.java).getPendingJob(AutomaticBackupJob.JOB_ID))
        } finally {
            context.getSystemService(JobScheduler::class.java).cancel(AutomaticBackupJob.JOB_ID)
            manager.cancel(3104)
        }
    }

    @Test fun failureNotificationsAreDeduplicatedAndRequireCurrentUserIntent() = preservingSettings { coordinator, store -> runBlocking {
        val notifications = com.cashflow.app.data.service.NotificationHelper
        org.junit.Assume.assumeTrue(notifications.canSendNotifications(context))
        val manager = context.getSystemService(NotificationManager::class.java)
        val scheduler = context.getSystemService(JobScheduler::class.java)
        scheduler.cancel(AutomaticBackupJob.JOB_ID)
        store.save(BackupAutomation(enabled = true, destination = BackupDestination.LOCAL))
        store.preferences.edit().remove("failure_notice_LOCAL").commit()
        store.recordError(BackupDestination.LOCAL, "Audit failure")
        val failure = SecurityException("private-account-and-path")
        val end = System.currentTimeMillis() + 3600000
        val output = File.createTempFile("backup-notice-", ".zip", context.cacheDir)
        var source: File? = null
        try {
            assertTrue(notifications.showBackupFailure(context, BackupDestination.LOCAL, end, failure))
            assertFalse("Backoff in the same window must not send another alert", notifications.showBackupFailure(context, BackupDestination.LOCAL, end, failure))
            assertEquals(end, store.preferences.getLong("failure_notice_LOCAL", 0))
            android.os.SystemClock.sleep(300)
            val action = manager.activeNotifications.first { it.id == 3104 }.notification.actions.single().actionIntent
            store.save(store.automation().copy(destination = BackupDestination.DRIVE))
            AutomaticBackupJob.schedule(context, replace = true)
            assertFalse("Changing destination must clear the old failure marker", store.preferences.contains("failure_notice_LOCAL"))
            android.os.SystemClock.sleep(300)
            assertFalse(manager.activeNotifications.any { it.id == 3104 })
            action.send()
            android.os.SystemClock.sleep(300)
            assertNull("Old destination actions must not start a job", scheduler.getPendingJob(AutomaticBackupJob.JOB_ID))
            store.save(store.automation().copy(destination = BackupDestination.LOCAL))
            source = coordinator.prepareLocal()
            coordinator.saveLocal(source, Uri.fromFile(output))
            assertNull(store.error(BackupDestination.LOCAL))
            assertFalse(store.preferences.contains("failure_notice_LOCAL"))
            android.os.SystemClock.sleep(300)
            assertFalse(manager.activeNotifications.any { it.id == 3104 })
            action.send()
            android.os.SystemClock.sleep(300)
            assertNull("An already successful backup must not be retried", scheduler.getPendingJob(AutomaticBackupJob.JOB_ID))
        } finally { source?.delete(); output.delete(); manager.cancel(3104) }
    } }

    @Test fun authorizationFailuresOfferAccountReconnectionRatherThanBlindRetry() = preservingSettings { _, store ->
        val notifications = com.cashflow.app.data.service.NotificationHelper
        org.junit.Assume.assumeTrue(notifications.canSendNotifications(context))
        store.save(BackupAutomation(enabled = true))
        store.preferences.edit().putBoolean("drive_reauth", false).remove("failure_notice_DRIVE").commit()
        val manager = context.getSystemService(NotificationManager::class.java)
        try {
            val end = System.currentTimeMillis() + 3600000
            assertTrue(notifications.showBackupFailure(context, BackupDestination.DRIVE, end, java.io.IOException("Private details")))
            store.preferences.edit().putBoolean("drive_reauth", true).commit()
            assertTrue("An authorization failure must replace the now unusable retry action", notifications.showBackupFailure(context, BackupDestination.DRIVE, end, DriveAuthorizationRequired(null)))
            android.os.SystemClock.sleep(300)
            val notification = manager.activeNotifications.first { it.id == 3105 }.notification
            assertEquals("Hubungkan ulang", notification.actions.single().title.toString())
            assertEquals(notification.contentIntent, notification.actions.single().actionIntent)
        } finally { manager.cancel(3105) }
    }

    @Test fun restoringTheLedgerRequestsAFreshBackupInTheCurrentWindow() = preservingSettings { _, store -> runBlocking {
        val repo = (context.applicationContext as CashflowApplication).repository
        repo.awaitInitialLoad()
        val before = repo.transactions.value.toList()
        val snapshot = repo.createBackupJson().getOrThrow()
        val now = ZonedDateTime.now()
        val options = BackupAutomation(startHour = now.hour, endHour = (now.hour + 1) % 24)
        store.save(options)
        store.recordSuccess(BackupDestination.DRIVE, true, now.toInstant().toEpochMilli())
        assertTrue(options.copy(enabled = true).nextWindow(now, Instant.ofEpochMilli(store.automaticSuccess(BackupDestination.DRIVE)))!!.start.isAfter(now))
        val valid = repo.validateBackupJson(snapshot) as BackupValidationResult.Valid
        repo.restoreFromJson(valid.parsedData).getOrThrow()
        val after = options.copy(enabled = true).nextWindow(now, store.automaticSuccess(BackupDestination.DRIVE).takeIf { it > 0 }?.let(Instant::ofEpochMilli))!!
        assertFalse("Restored data must not wait for tomorrow's window", after.start.isAfter(now))
        assertEquals(before, repo.transactions.value)
    } }
}
