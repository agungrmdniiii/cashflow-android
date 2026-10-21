package com.cashflow.app

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.cashflow.app.data.model.Transaction
import com.cashflow.app.ui.components.TransactionItemCard
import com.cashflow.app.ui.theme.CashflowTheme
import org.junit.Assert.*
import org.junit.Test

/** Large isolated UI list: callbacks count requests and never touch the ledger. */
class TransactionScrollTest {
    @Test fun fastFullListFlingReachesBothEndsWithoutEditingOrDeleting() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val state = LazyListState()
        var edits = 0
        var deletes = 0
        val rows = (1..200).map { Transaction("fling-$it", "expense", it * 1000L, "Transaksi $it",
            assetId = "test", transactionDate = "2026-10-21") }
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, ComponentActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
        instrumentation.runOnMainSync { activity.setContent { CashflowTheme {
            LazyColumn(state = state) {
                items(rows, key = { it.id }, contentType = { "transaction" }) { tx ->
                    TransactionItemCard(tx, null, null, null, onClick = {}, onEdit = { edits++ }, onDelete = { deletes++ })
                }
            }
        } } }
        try {
            node(automation) { it.text?.toString() == "Transaksi 1" }
            val screen = Rect().also { automation.rootInActiveWindow.getBoundsInScreen(it) }
            fun drag(forward: Boolean) {
                val down = SystemClock.uptimeMillis()
                fun send(action: Int, fraction: Float) {
                    val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                        screen.centerX().toFloat(), screen.height() * fraction, 0)
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                    try { assertTrue(automation.injectInputEvent(event, false)) } finally { event.recycle() }
                }
                val start = if (forward) .85f else .25f
                val end = if (forward) .25f else .85f
                send(MotionEvent.ACTION_DOWN, start)
                for (step in 1..9) {
                    SystemClock.sleep((down + step * 16L - SystemClock.uptimeMillis()).coerceAtLeast(0))
                    send(MotionEvent.ACTION_MOVE, start + (end - start) * step / 9)
                }
                send(MotionEvent.ACTION_UP, end)
                SystemClock.sleep(300)
            }
            for (forward in listOf(true, false)) {
                var reached = false
                for (attempt in 0 until 40) {
                    instrumentation.runOnMainSync { reached = if (forward) !state.canScrollForward else !state.canScrollBackward }
                    if (reached) break
                    drag(forward)
                }
                instrumentation.runOnMainSync {
                    assertTrue("Fast scrolling must reach the ${if (forward) "bottom" else "top"}",
                        if (forward) !state.canScrollForward else !state.canScrollBackward)
                    assertEquals(0, edits)
                    assertEquals(0, deletes)
                }
            }
            node(automation) { it.text?.toString() == "Transaksi 1" }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun swipingRequestsOneActionAndKeepsTheRow() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        var edits = 0
        var deletes = 0
        val tx = Transaction("swipe-test", "expense", 1000, "Tetap ada", assetId = "test", transactionDate = "2026-10-21")
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, ComponentActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
        instrumentation.runOnMainSync { activity.setContent { CashflowTheme {
            Box(Modifier.fillMaxSize().padding(top = 80.dp)) {
                TransactionItemCard(tx, null, null, null, onClick = {}, onEdit = { edits++ }, onDelete = { deletes++ })
            }
        } } }
        try {
            val bounds = Rect().also { node(automation) { it.contentDescription?.toString() == "Opsi transaksi" }.getBoundsInScreen(it) }
            val screen = Rect().also { automation.rootInActiveWindow.getBoundsInScreen(it) }
            fun swipe(start: Float, end: Float) {
                val down = SystemClock.uptimeMillis()
                fun send(action: Int, x: Float) {
                    val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, screen.width() * x, bounds.centerY().toFloat(), 0)
                    e.source = InputDevice.SOURCE_TOUCHSCREEN
                    try { assertTrue(automation.injectInputEvent(e, false)) } finally { e.recycle() }
                }
                send(MotionEvent.ACTION_DOWN, start)
                for (step in 1..30) {
                    SystemClock.sleep((down + step * 16L - SystemClock.uptimeMillis()).coerceAtLeast(0))
                    send(MotionEvent.ACTION_MOVE, start + (end - start) * step / 30)
                }
                send(MotionEvent.ACTION_UP, end)
                SystemClock.sleep(700)
            }
            swipe(.7f, .68f)
            instrumentation.runOnMainSync { assertEquals(0, edits); assertEquals(0, deletes) }
            swipe(.8f, .15f)
            instrumentation.runOnMainSync { assertEquals(1, deletes); assertEquals(0, edits) }
            node(automation) { it.text?.toString() == "Tetap ada" }
            swipe(.15f, .8f)
            instrumentation.runOnMainSync { assertEquals(1, deletes); assertEquals(1, edits) }
            node(automation) { it.text?.toString() == "Tetap ada" }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun scrollingDoesNotEditOrDeleteAndRowMenuStillWorks() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        var edits = 0
        var deletes = 0
        val rows = (1..200).map { Transaction("scroll-$it", "expense", it * 1000L, "Transaksi $it",
            assetId = "test", transactionDate = "2026-10-21") }
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, ComponentActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
        instrumentation.runOnMainSync { activity.setContent { CashflowTheme {
            LazyColumn { items(rows, key = { it.id }, contentType = { "transaction" }) { tx ->
                TransactionItemCard(tx, null, null, null, onClick = {}, onEdit = { edits++ }, onDelete = { deletes++ })
            } }
        } } }
        try {
            SystemClock.sleep(800)
            val screen = Rect().also { automation.rootInActiveWindow.getBoundsInScreen(it) }
            repeat(4) {
                val down = SystemClock.uptimeMillis()
                fun send(action: Int, y: Float) {
                    val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, screen.centerX().toFloat(), y, 0)
                    e.source = InputDevice.SOURCE_TOUCHSCREEN
                    try { assertTrue(automation.injectInputEvent(e, false)) } finally { e.recycle() }
                }
                send(MotionEvent.ACTION_DOWN, screen.height() * .8f)
                for (step in 1..40) {
                    SystemClock.sleep((down + step * 16L - SystemClock.uptimeMillis()).coerceAtLeast(0))
                    send(MotionEvent.ACTION_MOVE, screen.height() * (.8f - .5f * step / 40))
                }
                send(MotionEvent.ACTION_UP, screen.height() * .3f)
                SystemClock.sleep(200)
            }
            instrumentation.runOnMainSync { assertEquals(0, edits); assertEquals(0, deletes) }
            click(node(automation) { it.contentDescription?.toString() == "Opsi transaksi" })
            click(node(automation) { it.text?.toString() == "Hapus Transaksi" })
            instrumentation.runOnMainSync { assertEquals(1, deletes) }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    private fun node(automation: android.app.UiAutomation, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        fun find(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (n == null) return null
            if (predicate(n)) return n
            for (i in 0 until n.childCount) find(n.getChild(i))?.let { return it }
            return null
        }
        val deadline = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < deadline) {
            find(automation.rootInActiveWindow)?.let { return it }
            SystemClock.sleep(50)
        }
        error("Transaction control not found")
    }
    private fun click(node: AccessibilityNodeInfo) {
        var n: AccessibilityNodeInfo? = node
        while (n != null && !n.isClickable) n = n.parent
        assertTrue(n?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
}
