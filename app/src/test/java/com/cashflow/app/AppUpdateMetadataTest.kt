package com.cashflow.app

import com.cashflow.app.data.update.UpdateRelease
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AppUpdateMetadataTest {
    @Test fun persistedReleaseRoundTripsAllTrustedMetadata() {
        val release = UpdateRelease.parse(metadata().toString())
        assertEquals(release, UpdateRelease.parse(release.toJson()))
    }
    private fun metadata() = JSONObject().put("versionCode", 112).put("versionName", "1.0.0")
        .put("minSdk", 26).put("size", 12345).put("sha256", "a".repeat(64))
        .put("apkUrl", "https://github.com/agungrmdniiii/cashflow-android/releases/download/v1.0.0-build.112/DuitAing.apk")
        .put("notes", "- Memperbaiki pencatatan suara dan tampilan insight.")

    @Test fun comparesInternalVersionEvenWhenVisibleVersionWasReset() {
        val release = UpdateRelease.parse(metadata().toString())
        assertEquals("1.0.0", release.versionName)
        assertTrue(release.isNewerThan(110))
        assertFalse(release.isNewerThan(112))
        assertFalse(release.isNewerThan(113))
    }
    @Test fun rejectsUntrustedDownloadLocations() {
        listOf("http://github.com/agungrmdniiii/cashflow-android/releases/download/v1/DuitAing.apk",
            "https://evil.example/DuitAing.apk", "https://github.com/another/repo/releases/download/v1/DuitAing.apk",
            "https://github.com@evil.example/agungrmdniiii/cashflow-android/releases/download/v1/DuitAing.apk",
            "https://github.com/agungrmdniiii/cashflow-android/releases/download/../DuitAing.apk").forEach {
            assertThrows(IllegalArgumentException::class.java) { UpdateRelease.parse(metadata().put("apkUrl", it).toString()) }
        }
    }
    @Test fun rejectsIncompleteUnsafeAndOversizedMetadata() {
        listOf("sha256" to "not-a-hash", "notes" to "", "size" to 0,
            "size" to 2147483647, "versionCode" to -1, "versionName" to "", "minSdk" to 0).forEach { (key, value) ->
            assertThrows("Must reject $key=$value", IllegalArgumentException::class.java) {
                UpdateRelease.parse(metadata().put(key, value).toString())
            }
        }
    }
    @Test fun reportsAndroidCompatibility() {
        val release = UpdateRelease.parse(metadata().put("minSdk", 36).toString())
        assertFalse(release.supportsAndroid(35))
        assertTrue(release.supportsAndroid(36))
    }
}
