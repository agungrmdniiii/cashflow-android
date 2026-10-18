package com.cashflow.app

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.FileInputStream

/** Opt-in, read-only benchmark: -e auditScroll true. Uses 60 Hz touch samples rather than a busy shell swipe. */
class HomeSlowScrollTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation

    @Test fun slowDragReachesDashboardCardsWithoutOpeningATransaction() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("auditScroll") == "true")
        instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        val deadline = SystemClock.uptimeMillis() + 15000
        while (find(automation.rootInActiveWindow, "Transaksi Terbaru") == null && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(100)
        }
        assertNotNull("Home must finish loading before measurement", find(automation.rootInActiveWindow, "Transaksi Terbaru"))
        SystemClock.sleep(1500)
        val bounds = Rect().also { automation.rootInActiveWindow.getBoundsInScreen(it) }
        val x = bounds.centerX().toFloat()
        val bottom = bounds.height() * 0.72f
        val top = bounds.height() * 0.30f
        // Warm the scrolling path; exclude navigation, compilation and first composition.
        repeat(3) { drag(x, bottom, top) }; repeat(3) { drag(x, top, bottom) }
        SystemClock.sleep(500)
        shell("dumpsys gfxinfo com.cashflow.app reset")
        repeat(2) {
            repeat(3) { drag(x, bottom, top) }
            val deadline = SystemClock.uptimeMillis() + 5000
            while (find(automation.rootInActiveWindow, "Anggaran Belanja") == null && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(100)
            }
            assertNotNull("Vertical dragging must reach the budget card: ${labels(automation.rootInActiveWindow)}", find(automation.rootInActiveWindow, "Anggaran Belanja"))
            repeat(3) { drag(x, top, bottom) }
        }
        val frames = shell("dumpsys gfxinfo com.cashflow.app framestats")
        val rendered = Regex("Total frames rendered: (\\d+)").find(frames)?.groupValues?.get(1)?.toInt() ?: 0
        assertTrue("No scrolling frames collected", rendered > 30)
        val summary = frames.lineSequence().filter {
            it.startsWith("Total frames") || it.startsWith("Janky frames:") || it.startsWith("95th percentile")
        }.joinToString("; ")
        instrumentation.targetContext.cacheDir.resolve("home-slow-scroll-frames.txt").writeText(frames)
        instrumentation.addResults(android.os.Bundle().apply { putString("home_scroll", summary) })
        var home = find(automation.rootInActiveWindow, "Beranda")
        while (home != null && !home.isSelected) home = home.parent
        assertNotNull("Scroll must stay on Home", home)
    }

    private fun drag(x: Float, start: Float, end: Float) {
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, y: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(automation.injectInputEvent(event, false)) } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, start)
        for (step in 1..90) {
            SystemClock.sleep((down + step * 16L - SystemClock.uptimeMillis()).coerceAtLeast(0))
            send(MotionEvent.ACTION_MOVE, start + (end - start) * step / 90)
        }
        send(MotionEvent.ACTION_UP, end)
        SystemClock.sleep(300)
    }

    private fun shell(command: String): String = automation.executeShellCommand(command).use {
        FileInputStream(it.fileDescriptor).bufferedReader().readText()
    }

    private fun find(node: AccessibilityNodeInfo?, label: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString()?.contains(label) == true || node.contentDescription?.toString()?.contains(label) == true) return node
        for (index in 0 until node.childCount) find(node.getChild(index), label)?.let { return it }
        return null
    }

    private fun labels(node: AccessibilityNodeInfo?): String = if (node == null) "<no window>" else
        listOfNotNull(node.text?.toString(), node.contentDescription?.toString()).joinToString(" ") +
            (0 until node.childCount).joinToString(" ") { labels(node.getChild(it)) }
}
