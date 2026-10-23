package com.cashflow.app

import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.platform.app.InstrumentationRegistry
import com.cashflow.app.ui.screens.settings.NotificationSection
import com.cashflow.app.ui.theme.CashflowTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

/** Standalone UI using platform accessibility; no saved preferences or ledger writes. */
class ReminderTimePickerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val saved = AtomicReference<String?>()

    private fun withPicker(test: () -> Unit) {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, ComponentActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
        try {
            instrumentation.runOnMainSync { activity.setContent { CashflowTheme {
                NotificationSection(
                    isDailyReminderEnabled = true, dailyReminderTime = "21:00",
                    isBudgetAlertsEnabled = true, isBudgetRolloverEnabled = false,
                    activeAssets = emptyList(), defaultAsset = null,
                    onDailyReminderToggled = {}, onDailyReminderTimeChanged = { saved.set(it) },
                    onBudgetAlertsToggled = {}, onBudgetRolloverToggled = {}, onDefaultAssetSelected = {})
            } } }
            click(node { it.text?.toString() == "Ubah jam" })
            node { it.className?.toString() == "android.widget.NumberPicker" }
            test()
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun cancellingTimePickerDoesNotSave() = withPicker {
        click(node { it.text?.toString() == "Batal" })
        node { it.text?.toString() == "Ubah jam" }
        assertNull(saved.get())
        assertNull(find(instrumentation.uiAutomation.rootInActiveWindow) { it.className?.toString() == "android.widget.NumberPicker" })
    }

    @Test fun savingTimePickerUsesTwentyFourHourFormat() = withPicker {
        click(node { it.text?.toString() == "Simpan jam" })
        assertEquals("21:00", saved.get())
    }

    @Test fun choosingHourAndMinuteDoesNotRequireTyping() = withPicker {
        for (index in 0..1) {
            val wheels = mutableListOf<AccessibilityNodeInfo>()
            fun collect(root: AccessibilityNodeInfo?) {
                if (root == null) return
                if (root.className?.toString() == "android.widget.NumberPicker") wheels.add(root)
                for (child in 0 until root.childCount) collect(root.getChild(child))
            }
            collect(instrumentation.uiAutomation.rootInActiveWindow)
            assertEquals(2, wheels.size)
            assertTrue(wheels[index].performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
            SystemClock.sleep(600)
        }
        click(node { it.text?.toString() == "Simpan jam" })
        assertEquals("22:01", saved.get())
    }

    private fun node(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 7000
        while (SystemClock.uptimeMillis() < deadline) {
            find(instrumentation.uiAutomation.rootInActiveWindow, predicate)?.let { return it }
            SystemClock.sleep(50)
        }
        error("Reminder control not found")
    }
    private fun find(root: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (root == null) return null
        if (predicate(root)) return root
        for (index in 0 until root.childCount) find(root.getChild(index), predicate)?.let { return it }
        return null
    }
    private fun click(node: AccessibilityNodeInfo) {
        var button: AccessibilityNodeInfo? = node
        while (button != null && !button.isClickable) button = button.parent
        assertTrue(button?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        instrumentation.waitForIdleSync()
    }
}
