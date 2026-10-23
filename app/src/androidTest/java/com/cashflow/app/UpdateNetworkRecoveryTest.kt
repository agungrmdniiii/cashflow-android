package com.cashflow.app

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.cashflow.app.data.update.*
import org.junit.Assert.*
import org.junit.Test

/** Emulator-only; restores network and pending-update preferences. Never starts an installer. */
class UpdateNetworkRecoveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
    private fun awaitPhase(manager: AppUpdateViewModel, timeout: Long, predicate: (UpdatePhase) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (!predicate(manager.status.value.phase) && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
        assertTrue(manager.status.value.toString(), predicate(manager.status.value.phase))
    }
    @Test fun androidKeepsTheSameDownloadAcrossOfflineProcessRecovery() {
        org.junit.Assume.assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val connection = openGitHubConnection(UpdateRelease.FEED_URL)
        val published = try { connection.inputStream.bufferedReader().use { UpdateRelease.parse(it.readText()) } }
            finally { connection.disconnect() }
        // Keep the existing public asset; mismatched test version must be rejected after download.
        val fixture = published.copy(versionCode = BuildConfig.VERSION_CODE + 1000L)
        val preferences = context.getSharedPreferences("pending_app_update", Context.MODE_PRIVATE)
        val oldRelease = preferences.getString("release", null)
        val oldId = preferences.getLong("download_id", -1)
        val wifi = shell("settings get global wifi_on")
        val mobile = shell("settings get global mobile_data")
        val stores = mutableListOf<ViewModelStore>()
        var requestId = -1L
        val apk = updateApkFile(context, fixture)
        org.junit.Assume.assumeFalse(apk.exists())
        fun manager(): AppUpdateViewModel {
            val result = AppUpdateViewModel(context.applicationContext as Application)
            ViewModelStore().also { it.put("update", result); stores.add(it) }
            return result
        }
        try {
            preferences.edit().clear().putString("release", fixture.toJson()).commit()
            shell("svc wifi disable"); shell("svc data disable")
            val first = manager()
            awaitPhase(first, 5000) { it == UpdatePhase.AVAILABLE }
            first.download()
            awaitPhase(first, 20000) { it == UpdatePhase.PAUSED || it == UpdatePhase.ERROR }
            assertEquals(UpdatePhase.PAUSED, first.status.value.phase)
            requestId = preferences.getLong("download_id", -1)
            assertTrue(requestId >= 0)
            instrumentation.runOnMainSync { stores.first().clear() }
            val restored = manager()
            awaitPhase(restored, 5000) { it == UpdatePhase.PAUSED || it == UpdatePhase.ERROR }
            assertEquals(UpdatePhase.PAUSED, restored.status.value.phase)
            assertEquals(requestId, preferences.getLong("download_id", -1))
            shell("svc wifi enable"); shell("svc data enable")
            val deadline = SystemClock.uptimeMillis() + 90000
            var retries = 0
            var retryAt = 0L
            while (restored.status.value.phase !in listOf(UpdatePhase.ERROR, UpdatePhase.READY) && SystemClock.uptimeMillis() < deadline) {
                val now = SystemClock.uptimeMillis()
                if (restored.status.value.phase == UpdatePhase.PAUSED && now >= retryAt && retries < 3) {
                    assertEquals(requestId, preferences.getLong("download_id", -1))
                    // Exercise the same native retry without waiting several minutes for Android's backoff.
                    val result = shell("cmd jobscheduler run -f com.android.providers.downloads $requestId")
                    assertTrue(result, result.contains("Running job"))
                    retries++
                    retryAt = now + 15000
                }
                SystemClock.sleep(200)
            }
            assertEquals(UpdatePhase.ERROR, restored.status.value.phase)
            assertTrue(restored.status.value.message, restored.status.value.message.contains("Versi paket"))
            assertFalse(apk.exists())
        } finally {
            instrumentation.runOnMainSync { stores.forEach { it.clear() } }
            if (requestId >= 0) context.getSystemService(DownloadManager::class.java).remove(requestId)
            apk.delete(); java.io.File(apk.parentFile, "${apk.name}.part").delete()
            preferences.edit().clear().apply { oldRelease?.let { putString("release", it) }; if (oldId >= 0) putLong("download_id", oldId) }.commit()
            shell("svc wifi ${if (wifi == "1") "enable" else "disable"}")
            shell("svc data ${if (mobile == "1") "enable" else "disable"}")
        }
    }
}
