package com.cashflow.app

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.platform.app.InstrumentationRegistry
import com.cashflow.app.data.preferences.PreferenceManager
import com.cashflow.app.ui.screens.OnboardingSheet
import com.cashflow.app.ui.screens.AppTourStep
import com.cashflow.app.ui.screens.AppGuideSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses platform accessibility because the installed Espresso injector does not support API 37. */
class AppTourTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun pendingTourPersistsButExistingUsersAreNotAutomaticallyEnrolled() {
        val base = instrumentation.targetContext
        val prefix = "tour_test_${System.nanoTime()}_"
        val isolated = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) =
                base.getSharedPreferences(prefix + name, Context.MODE_PRIVATE)
        }
        try {
            val prefs = PreferenceManager(isolated)
            assertEquals(false, prefs.isAppTourPending())
            prefs.setAppTourPending(true)
            assertEquals(true, PreferenceManager(isolated).isAppTourPending())
            prefs.setAppTourPending(false)
            assertEquals(false, PreferenceManager(isolated).isAppTourPending())
        } finally {
            base.dataDir.resolve("shared_prefs").listFiles()
                ?.filter { it.name.startsWith(prefix) && it.extension == "xml" }
                ?.forEach { base.deleteSharedPreferences(it.nameWithoutExtension) }
        }
    }

    @Test fun firstStepCanBeSkippedWithoutStartingATransaction() {
        var skipped = 0
        var advanced = 0
        val activity = showTour(0, onNext = { advanced++ }, onDismiss = { skipped++ })
        try {
            node("Pahami saldo awal")
            assertEquals(null, findNode(instrumentation.uiAutomation.rootInActiveWindow, "Kembali"))
            click("Lewati tur")
            instrumentation.runOnMainSync { assertEquals(1, skipped); assertEquals(0, advanced) }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun lastStepOffersARealTransactionAndAllowsGoingBack() {
        var started = 0
        var returned = 0
        val activity = showTour(AppTourStep.entries.lastIndex, onNext = { started++ }, onBack = { returned++ })
        try {
            node("Baca pola pengeluaran")
            click("Kembali")
            click("Mulai mencatat")
            instrumentation.runOnMainSync { assertEquals(1, returned); assertEquals(1, started) }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun transactionWalkthroughExplainsAmountWithAnUnsavedExample() {
        val activity = showTour(AppTourStep.AMOUNT.ordinal)
        try {
            node("Isi nominal transaksi")
            node("Contoh: makan siang Rp20.000. Contoh tur tidak disimpan.")
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun aiTourExplainsLocalModeAndHowToAsk() {
        val activity = showTour(AppTourStep.AI_MODE.ordinal)
        try {
            node("Kenali mode Vina")
            node("AI online bersifat pilihan. Atur melalui Pengaturan > AI & Suara bila ingin menggunakannya.")
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun featureGuideCanExpandBackupInstructions() {
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, ComponentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as ComponentActivity
        instrumentation.runOnMainSync { activity.setContent { AppGuideSheet(onStartTour = {}, onDismiss = {}) } }
        try {
            click("Backup & pemulihan")
            node("1. Cadangkan sebelum ganti HP atau menghapus aplikasi.")
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    private fun showTour(step: Int, onNext: () -> Unit = {}, onBack: () -> Unit = {}, onDismiss: () -> Unit = {}): ComponentActivity {
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, ComponentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as ComponentActivity
        instrumentation.runOnMainSync {
            activity.setContent {
                OnboardingSheet(step, target = null, onNext, onBack, onDismiss)
            }
        }
        return activity
    }

    private fun node(text: String): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < deadline) {
            findNode(instrumentation.uiAutomation.rootInActiveWindow, text)?.let { return it }
            SystemClock.sleep(100)
        }
        error("Tour control not visible: $text")
    }

    private fun findNode(root: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.text?.toString() == text) return root
        for (index in 0 until root.childCount) {
            findNode(root.getChild(index), text)?.let { return it }
        }
        return null
    }

    private fun click(text: String) {
        var target: AccessibilityNodeInfo? = node(text)
        while (target != null && !target.isClickable) target = target.parent
        assertTrue("Tour control not clickable: $text", target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        instrumentation.waitForIdleSync()
    }
}
