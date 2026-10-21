package com.cashflow.app

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.cashflow.app.data.update.UpdateRelease
import com.cashflow.app.data.update.verifyUpdateApk
import com.cashflow.app.data.update.updateInstallIntent
import org.junit.Assert.*
import org.junit.Test
import java.io.File

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
