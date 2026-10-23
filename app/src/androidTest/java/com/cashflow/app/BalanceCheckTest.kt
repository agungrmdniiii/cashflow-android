package com.cashflow.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.platform.app.InstrumentationRegistry
import com.cashflow.app.data.model.Asset
import com.cashflow.app.ui.screens.BalanceCheckDialog
import com.cashflow.app.ui.theme.CashflowTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

/** Isolated balance dialog: never writes the user's ledger. */
class BalanceCheckTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @Test fun previewAndCancelNeverWriteAndSaveRequiresConfirmation() {
        val writes = AtomicInteger()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, ComponentActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
        try {
            instrumentation.runOnMainSync { activity.setContent { CashflowTheme {
                BalanceCheckDialog(Asset("test", "Rekening uji", "Bank", 100000, 100000), {}, { target, _, done ->
                    assertEquals(90000L, target)
                    writes.incrementAndGet()
                    done(true)
                })
            } } }
            val input = node { it.className?.toString() == "android.widget.EditText" }
            assertTrue(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "90000")
            }))
            click(node { it.text?.toString() == "Tinjau penyesuaian" })
            node { it.text?.toString() == "Konfirmasi penyesuaian" }
            assertEquals(0, writes.get())
            click(node { it.text?.toString() == "Kembali" })
            assertEquals(0, writes.get())
            click(node { it.text?.toString() == "Tinjau penyesuaian" })
            click(node { it.text?.toString() == "Simpan penyesuaian" })
            assertEquals(1, writes.get())
        } finally { instrumentation.runOnMainSync { activity.finish() } }
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
