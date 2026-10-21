package com.cashflow.app

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.test.platform.app.InstrumentationRegistry
import com.cashflow.app.ui.components.AnimatedEmptyState
import com.cashflow.app.ui.theme.CashflowTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertTrue
import org.junit.Test

class EmptyStatePerformanceTest {
    @Test fun emptyStateStopsRequestingFramesAfterItsEntrance() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, ComponentActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
        val frames = AtomicInteger()
        val listener = Window.OnFrameMetricsAvailableListener { _, _, _ -> frames.incrementAndGet() }
        try {
            instrumentation.runOnMainSync {
                activity.setContent { CashflowTheme {
                    AnimatedEmptyState(Icons.Rounded.Autorenew, "Belum ada jadwal", "Buat jadwal pertama Anda.")
                } }
            }
            SystemClock.sleep(1800)
            instrumentation.runOnMainSync { activity.window.addOnFrameMetricsAvailableListener(listener, Handler(Looper.getMainLooper())) }
            SystemClock.sleep(1200)
            assertTrue("Idle empty state must stop drawing, observed ${frames.get()} frames", frames.get() <= 3)
        } finally { instrumentation.runOnMainSync {
            activity.window.removeOnFrameMetricsAvailableListener(listener)
            activity.finish()
        } }
    }
}
