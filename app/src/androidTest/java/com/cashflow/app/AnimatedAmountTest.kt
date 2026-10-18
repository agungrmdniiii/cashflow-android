package com.cashflow.app

import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import com.cashflow.app.ui.components.AnimatedNumberText
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimatedAmountTest {
    @Test fun updatedAmountsRemainExactAndReadableIncludingLongBoundaries() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val amount = mutableLongStateOf(0L)
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, ComponentActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as ComponentActivity
        instrumentation.runOnMainSync {
            activity.setContent {
                AnimatedNumberText(amount.longValue, TextStyle(fontSize = 24.sp), Color.Black, dimThousands = true)
            }
        }
        try {
            for ((value, label) in listOf(
                0L to "Rp 0",
                1_234_567L to "Rp 1.234.567",
                -1_234L to "Rp -1.234",
                Long.MAX_VALUE to "Rp 9.223.372.036.854.775.807",
                Long.MIN_VALUE to "Rp -9.223.372.036.854.775.808"
            )) {
                instrumentation.runOnMainSync { amount.longValue = value }
                val deadline = SystemClock.uptimeMillis() + 5000
                while (SystemClock.uptimeMillis() < deadline &&
                    !containsLabel(instrumentation.uiAutomation.rootInActiveWindow, label)) {
                    SystemClock.sleep(50)
                }
                assertTrue("Amount must remain exact: $label",
                    containsLabel(instrumentation.uiAutomation.rootInActiveWindow, label))
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    private fun containsLabel(node: AccessibilityNodeInfo?, label: String): Boolean {
        if (node == null) return false
        if (node.contentDescription?.toString() == label) return true
        return (0 until node.childCount).any { containsLabel(node.getChild(it), label) }
    }
}
