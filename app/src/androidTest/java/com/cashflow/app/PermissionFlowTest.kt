package com.cashflow.app

import android.app.NotificationManager
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.cashflow.app.ui.CashflowViewModel
import org.junit.Assert.*
import org.junit.Test

/** Run on the emulator with fresh runtime permission flags; never changes financial records. */
class PermissionFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun voiceUsesAndroidServiceByDefaultAndRemembersOfflineChoice() {
        val base = instrumentation.targetContext
        val names = mutableSetOf<String>()
        val prefix = "voice-default-" + java.util.UUID.randomUUID()
        val context = object : android.content.ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                val isolated = prefix + name
                names.add(isolated)
                return base.getSharedPreferences(isolated, mode)
            }
        }
        try {
            val prefs = com.cashflow.app.data.preferences.PreferenceManager(context)
            assertTrue("Voice must work without an additional online opt-in", prefs.isOnlineVoiceEnabled())
            prefs.setOnlineVoiceEnabled(false)
            assertFalse(com.cashflow.app.data.preferences.PreferenceManager(context).isOnlineVoiceEnabled())
            prefs.setOnlineVoiceEnabled(true)
            assertTrue(com.cashflow.app.data.preferences.PreferenceManager(context).isOnlineVoiceEnabled())
        } finally { names.forEach { base.deleteSharedPreferences(it) } }
    }

    @Test fun voiceExamplesAreVisibleAndStillOpenPreviewWithoutSaving() = withApp { vm ->
        val before = vm.transactions.value.toList()
        instrumentation.runOnMainSync { vm.isRecordVoiceOpen.value = true }
        click(node { it.text?.toString() == "Beli kopi 20 ribu pakai GoPay" })
        node { it.text?.toString() == "DITANGKAP" }
        node { it.text?.toString()?.contains("20.000") == true }
        instrumentation.runOnMainSync { assertEquals(before, vm.transactions.value) }
    }

    @Test fun deniedNotificationsShowRecoveryAndDoNotEnableDailyReminder() = withApp { vm ->
        val before = vm.isDailyReminderEnabled.value
        openNotifications(vm)
        click(node { it.text?.toString() == "IZINKAN NOTIFIKASI" })
        click(node { it.viewIdResourceName?.endsWith("permission_deny_button") == true })
        node { it.text?.toString() == "Izin notifikasi belum diberikan" }
        node { it.text?.toString() == "Buka Pengaturan" }
        click(node { it.text?.toString() == "Nanti" })
        assertEquals(before, vm.isDailyReminderEnabled.value)
        assertFalse(com.cashflow.app.data.service.NotificationHelper.hasNotificationPermission(instrumentation.targetContext))
    }

    @Test fun allowingNotificationsFromTestButtonActuallyPostsOne() = withApp { vm ->
        openNotifications(vm)
        click(node { it.text?.toString() == "Kirim notifikasi uji" })
        click(node { it.viewIdResourceName?.endsWith("permission_allow_button") == true })
        node { it.text?.toString() == "Notifikasi diizinkan" }
        val manager = instrumentation.targetContext.getSystemService(NotificationManager::class.java)
        try {
            val deadline = SystemClock.uptimeMillis() + 5000
            while (manager.activeNotifications.none { it.id == 3099 } && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
            assertTrue(manager.activeNotifications.any { it.id == 3099 })
        } finally { manager.cancel(3099) }
    }

    @Test fun deniedMicrophoneStillAllowsManualInput() = withApp { vm ->
        instrumentation.runOnMainSync { vm.isRecordVoiceOpen.value = true }
        click(node { it.text?.toString() == "IZINKAN MIKROFON" })
        click(node { it.viewIdResourceName?.endsWith("permission_deny_button") == true })
        node { it.text?.toString() == "IZIN MIKROFON DITOLAK" }
        click(node { it.text?.toString() == "CATAT MANUAL" })
        instrumentation.runOnMainSync {
            assertFalse(vm.isRecordVoiceOpen.value)
            assertTrue(vm.isAddTransactionOpen.value)
        }
    }

    private fun openNotifications(vm: CashflowViewModel) {
        instrumentation.runOnMainSync { vm.isSettingsOpen.value = true }
        click(node { it.text?.toString() == "Notifikasi & Anggaran" })
        node { it.text?.toString() == "Notifikasi belum diizinkan" }
    }

    private fun withApp(test: (CashflowViewModel) -> Unit) {
        val prefs = instrumentation.targetContext.getSharedPreferences("duit_aing_preferences", 0)
        val hadKey = prefs.contains("requested_notifications")
        val previous = prefs.getBoolean("requested_notifications", false)
        prefs.edit().remove("requested_notifications").commit()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        lateinit var vm: CashflowViewModel
        instrumentation.runOnMainSync { vm = ViewModelProvider(activity)[CashflowViewModel::class.java] }
        try {
            val deadline = SystemClock.uptimeMillis() + 10000
            while (!vm.initialLoadComplete.value && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
            assertTrue(vm.initialLoadComplete.value)
            test(vm)
        } finally {
            instrumentation.runOnMainSync { vm.isRecordVoiceOpen.value = false; vm.isAddTransactionOpen.value = false; activity.finish() }
            if (hadKey) prefs.edit().putBoolean("requested_notifications", previous).commit()
            else prefs.edit().remove("requested_notifications").commit()
        }
    }

    private fun node(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 7000
        while (SystemClock.uptimeMillis() < deadline) {
            find(instrumentation.uiAutomation.rootInActiveWindow, predicate)?.let { return it }
            SystemClock.sleep(50)
        }
        error("Permission control not found")
    }
    private fun find(root: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (root == null) return null
        if (predicate(root)) return root
        for (i in 0 until root.childCount) find(root.getChild(i), predicate)?.let { return it }
        return null
    }
    private fun click(node: AccessibilityNodeInfo) {
        var button: AccessibilityNodeInfo? = node
        while (button != null && !button.isClickable) button = button.parent
        assertTrue(button?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        instrumentation.waitForIdleSync()
    }
}
