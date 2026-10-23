package com.cashflow.app

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.cashflow.app.data.update.UpdateRelease
import com.cashflow.app.data.update.verifyUpdateApk
import com.cashflow.app.data.update.updateInstallIntent
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import android.app.Application
import com.cashflow.app.data.update.*
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest

/** Only reads installed APKs; never launches the installer or writes the ledger. */
class AppUpdateTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun release(code: Long = BuildConfig.VERSION_CODE.toLong()) = UpdateRelease(
        code, BuildConfig.VERSION_NAME, 26, 1, "0".repeat(64),
        "https://github.com/agungrmdniiii/cashflow-android/releases/download/v1/DuitAing.apk", "- Memperbaiki pencatatan suara.")

    @Test fun acceptsMatchingInstalledPackageAndSigningCertificate() {
        verifyUpdateApk(context, File(context.applicationInfo.sourceDir), release())
    }
    @Test fun verifiedPackagePersistsInPrivateFilesAndCanBeReopened() = runBlocking {
        val source = File(context.applicationInfo.sourceDir)
        val hash = MessageDigest.getInstance("SHA-256").apply {
            source.inputStream().use { stream -> val buffer = ByteArray(65536); while (true) {
                val count = stream.read(buffer); if (count < 0) break; update(buffer, 0, count)
            } }
        }.digest().joinToString("") { "%02x".format(it) }
        val metadata = release().copy(size = source.length(), sha256 = hash)
        val destination = updateApkFile(context, metadata)
        org.junit.Assume.assumeFalse("Do not overwrite an existing update package", destination.exists())
        try {
            val file = prepareDownloadedUpdate(context, source, metadata)
            assertEquals(File(context.filesDir, "updates").canonicalFile, file.canonicalFile.parentFile)
            val reopened = updateApkFile(context, UpdateRelease.parse(metadata.toJson()))
            verifyDownloadedFile(reopened, metadata)
            verifyUpdateApk(context, reopened, metadata)
            assertEquals("content", updateInstallIntent(context, reopened).data?.scheme)
        } finally { destination.delete() }
    }

    @Test fun pendingMetadataSurvivesNewViewModelWithoutNetwork() {
        val preferences = context.getSharedPreferences("pending_app_update", android.content.Context.MODE_PRIVATE)
        val oldRelease = preferences.getString("release", null)
        val oldId = preferences.getLong("download_id", -1)
        val metadata = release(BuildConfig.VERSION_CODE + 1000L)
        val stores = mutableListOf<androidx.lifecycle.ViewModelStore>()
        try {
            preferences.edit().clear().putString("release", metadata.toJson()).commit()
            repeat(2) {
                val manager = AppUpdateViewModel(context.applicationContext as Application)
                val store = androidx.lifecycle.ViewModelStore().also { stores.add(it); it.put("update", manager) }
                val deadline = android.os.SystemClock.uptimeMillis() + 5000
                while (manager.status.value.phase == UpdatePhase.CHECKING && android.os.SystemClock.uptimeMillis() < deadline) android.os.SystemClock.sleep(30)
                assertEquals(UpdatePhase.AVAILABLE, manager.status.value.phase)
                assertEquals(metadata, manager.status.value.release)
                instrumentation.runOnMainSync { store.clear() }
            }
        } finally {
            instrumentation.runOnMainSync { stores.forEach { it.clear() } }
            preferences.edit().clear().apply { oldRelease?.let { putString("release", it) }; if (oldId >= 0) putLong("download_id", oldId) }.commit()
        }
    }
    @Test fun rejectsOtherPackageBeforeInstallation() {
        assertThrows(IllegalArgumentException::class.java) {
            verifyUpdateApk(context, File(instrumentation.context.applicationInfo.sourceDir), release())
        }
    }
    @Test fun rejectsAnArchiveWithMismatchedVersion() {
        assertThrows(IllegalArgumentException::class.java) {
            verifyUpdateApk(context, File(context.applicationInfo.sourceDir), release(BuildConfig.VERSION_CODE + 1L))
        }
    }
    @Test fun installerIntentOnlyGrantsReadAccessToAnUpdateInPrivateCache() {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File.createTempFile("intent-check-", ".apk", directory)
        try {
            val intent = updateInstallIntent(context, file)
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("content", intent.data?.scheme)
            assertEquals("${context.packageName}.fileprovider", intent.data?.authority)
            assertEquals("application/vnd.android.package-archive", intent.type)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } finally { file.delete() }
        assertThrows(IllegalArgumentException::class.java) { updateInstallIntent(context, file) }
        assertThrows(IllegalArgumentException::class.java) {
            updateInstallIntent(context, File(context.filesDir, "not-an-update.apk"))
        }
    }
}
