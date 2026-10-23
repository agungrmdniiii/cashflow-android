package com.cashflow.app

import android.app.NotificationManager
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.cashflow.app.data.model.*
import com.cashflow.app.data.preferences.PreferenceManager
import com.cashflow.app.data.service.NotificationHelper
import org.junit.Assert.*
import org.junit.Test

/** Isolated preference keys and notification IDs; never modifies financial records. */
class NotificationDeliveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Test fun aBlockedChannelMustNotReportSuccessfulDelivery() {
        val id = "notification_test_blocked"
        manager.createNotificationChannel(android.app.NotificationChannel(id, "Kanal uji", NotificationManager.IMPORTANCE_NONE))
        try {
            assertFalse(NotificationHelper.showLocalNotification(context, 9099, "Uji", "Kanal diblokir", id))
            assertFalse(manager.activeNotifications.any { it.id == 9099 })
        } finally { manager.cancel(9099); manager.deleteNotificationChannel(id) }
    }

    @Test fun deliverySucceedsOnlyWhenSystemPermissionIsGranted() {
        NotificationHelper.initNotificationChannels(context)
        try {
            val sent = NotificationHelper.showLocalNotification(context, 9098, "Uji Duit Aing", "Notifikasi uji berhasil.", NotificationHelper.CHANNEL_DAILY_REMINDER)
            assertEquals(NotificationHelper.hasNotificationPermission(context) && androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled(), sent)
            if (sent) {
                android.os.SystemClock.sleep(300)
                assertTrue(manager.activeNotifications.any { it.id == 9098 })
                val notification = manager.activeNotifications.first { it.id == 9098 }.notification
                assertNotNull("Notifikasi harus menampilkan logo Duit Aing", notification.getLargeIcon())
                assertEquals(android.app.Notification.CATEGORY_REMINDER, notification.category)
                assertEquals("Catatan harian", notification.extras.getString(android.app.Notification.EXTRA_SUB_TEXT))
                assertEquals("Catat transaksi", notification.actions.single().title.toString())
                assertEquals(android.app.Notification.VISIBILITY_PRIVATE, notification.visibility)
                assertEquals(0, notification.flags and android.app.Notification.FLAG_ONLY_ALERT_ONCE)
            }
        } finally { manager.cancel(9098) }
    }

    @Test fun scheduledJobPostsTheDailyReminder() {
        val repo = (context.applicationContext as CashflowApplication).repository
        kotlinx.coroutines.runBlocking { repo.awaitInitialLoad() }
        org.junit.Assume.assumeTrue("Do not execute a user's automatic schedules in this test", repo.recurringTransactions.value.none {
            it.isActive && it.mode == "auto"
        })
        org.junit.Assume.assumeTrue(NotificationHelper.canSendNotifications(context))
        val prefs = PreferenceManager(context)
        val daily = prefs.isDailyReminderEnabled()
        val time = prefs.getDailyReminderTime()
        val backup = prefs.isBackupReminderEnabled()
        val budgets = prefs.isBudgetAlertsEnabled()
        val key = "daily_${java.time.LocalDate.now()}"
        val notified = prefs.getBoolean(key)
        val dueKey = "due_${java.time.LocalDate.now()}"
        val dueNotified = prefs.getBoolean(dueKey)
        val existingIds = manager.activeNotifications.map { it.id }.toSet()
        try {
            prefs.setDailyReminderEnabled(true)
            prefs.setDailyReminderTime("00:00")
            prefs.setBackupReminderEnabled(false)
            prefs.setBudgetAlertsEnabled(false)
            prefs.setBoolean(key, false)
            com.cashflow.app.data.service.LocalReminderJob.schedule(context)
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("cmd jobscheduler run -f com.cashflow.app 4101"))
                .bufferedReader().use { it.readText() }
            val deadline = android.os.SystemClock.uptimeMillis() + 10000
            while (manager.activeNotifications.none { it.id == 3001 } && android.os.SystemClock.uptimeMillis() < deadline) android.os.SystemClock.sleep(100)
            assertTrue("Scheduled service must post daily notification", manager.activeNotifications.any { it.id == 3001 })
            assertTrue(prefs.getBoolean(key))
            android.os.SystemClock.sleep(500)
        } finally {
            manager.cancel(3001)
            if (3002 !in existingIds) manager.cancel(3002)
            prefs.setBoolean(dueKey, dueNotified)
            prefs.setDailyReminderEnabled(daily)
            prefs.setDailyReminderTime(time)
            prefs.setBackupReminderEnabled(backup)
            prefs.setBudgetAlertsEnabled(budgets)
            prefs.setBoolean(key, notified)
        }
    }
}
