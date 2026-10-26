package com.cashflow.app

import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.platform.app.InstrumentationRegistry
import com.cashflow.app.ui.screens.SettingsSheet
import com.cashflow.app.ui.theme.CashflowTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.flow.first

class UpdateHistoryUiTest {
    @Test fun publicHistoryRefreshWritesAnOfflineCacheWhenNetworkAuditIsEnabled() = kotlinx.coroutines.runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("releaseHistoryNetwork") == "approved")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as CashflowApplication
        val models = androidx.lifecycle.ViewModelStore()
        lateinit var manager: com.cashflow.app.data.update.ReleaseHistoryViewModel
        instrumentation.runOnMainSync {
            manager = androidx.lifecycle.ViewModelProvider(models,
                androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(app))[com.cashflow.app.data.update.ReleaseHistoryViewModel::class.java]
        }
        try {
            val result = kotlinx.coroutines.withTimeout(45000) { manager.status.first { !it.loading } }
            assertNull(result.error, result.error)
            assertTrue(result.entries.any { it.versionCode == 117L })
            val file = java.io.File(app.filesDir, "release-history.json")
            assertTrue("Successful refresh must preserve notes for offline use", file.isFile)
            val saved = com.cashflow.app.data.update.parseReleaseHistory(file.readText())
            assertTrue(saved.any { it.versionCode == 117L })
            assertTrue(saved.all { it.publishedAt != null })
        } finally { instrumentation.runOnMainSync { models.clear() } }
    }

    @Test fun olderReleaseNotesCanBeOpenedFromAbout() {
        for ((dark, scale) in listOf(false to 1f, true to 1.5f)) verifyHistory(dark, scale)
    }

    private fun verifyHistory(dark: Boolean, scale: Float) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(Intent(context, ComponentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
        val models = androidx.lifecycle.ViewModelStore()
        try {
            instrumentation.runOnMainSync {
                val app = context.applicationContext as CashflowApplication
                val model = androidx.lifecycle.ViewModelProvider(models,
                    androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(app))[com.cashflow.app.ui.CashflowViewModel::class.java]
                activity.setContent {
                    val density = LocalDensity.current.density
                    CompositionLocalProvider(LocalDensity provides Density(density, fontScale = scale)) {
                        CashflowTheme(darkTheme = dark) { SettingsSheet(model, onDismiss = {}) }
                    }
                }
            }
            click("Tentang Aplikasi")
            assertNotNull("About must expose older update notes", findEventually("Riwayat pembaruan"))
            click("Riwayat pembaruan")
            assertNotNull(findEventually("Versi 1.0.0 · Build 117"))
            click("Versi 1.0.0 · Build 117")
            assertNotNull(findEventually("- Menampilkan nomor build di halaman Tentang Aplikasi agar versi terpasang lebih mudah dikenali saat pemeriksaan pembaruan."))
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            val image = java.io.File(context.externalCacheDir, if (dark) "history-dark-large.png" else "history-light.png")
            image.outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            screenshot.recycle()
            click("Versi 1.0.0 · Build 117")
            click("Kembali ke menu pengaturan")
            assertNotNull(findEventually("DUIT AING"))
        } finally { instrumentation.runOnMainSync { activity.finish(); models.clear() } }
    }

    private fun find(root: AccessibilityNodeInfo?, label: String): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.text?.toString() == label || root.contentDescription?.toString() == label) return root
        for (i in 0 until root.childCount) find(root.getChild(i), label)?.let { return it }
        return null
    }
    private fun findEventually(label: String): AccessibilityNodeInfo? {
        val until = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < until) {
            find(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow, label)?.let { return it }
            SystemClock.sleep(50)
        }
        return null
    }
    private fun click(label: String) {
        var node = findEventually(label)
        repeat(7) {
            if (node == null) {
                fun scroll(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                    if (root == null || root.isScrollable) return root
                    for (i in 0 until root.childCount) scroll(root.getChild(i))?.let { return it }
                    return null
                }
                scroll(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow)
                    ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                SystemClock.sleep(150)
                node = find(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow, label)
            }
        }
        assertNotNull("Control missing: $label", node)
        while (node != null && !node.isClickable) node = node.parent
        assertTrue(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
    }
}
