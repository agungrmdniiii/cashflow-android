package com.cashflow.app

import com.cashflow.app.data.update.UpdateRelease
import com.cashflow.app.data.update.copyVerifiedDownload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest

class AppUpdateDownloadTest {
    private val bytes = ByteArray(140000) { (it % 251).toByte() }
    private fun release(size: Long = bytes.size.toLong(), hash: String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }) =
        UpdateRelease(112, "1.0.0", 26, size, hash, "https://github.com/agungrmdniiii/cashflow-android/releases/download/v1/DuitAing.apk", "- Memperbaiki pencatatan suara.")
    private fun temporary(test: (File) -> Unit) {
        val file = File.createTempFile("duitaing-update-", ".part")
        try { test(file) } finally { file.delete() }
    }
    @Test fun streamsTheEntireFileAndReportsProgress() = temporary { file ->
        val progress = mutableListOf<Int>()
        runBlocking { copyVerifiedDownload(ByteArrayInputStream(bytes), file, release(), progress::add) }
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(100, progress.last())
        assertTrue(progress.zipWithNext().all { it.first < it.second })
    }
    @Test fun rejectsTruncatedDownloadAndDeletesPartialFile() = temporary { file ->
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { copyVerifiedDownload(ByteArrayInputStream(bytes.copyOf(100)), file, release()) {} }
        }
        assertFalse(file.exists())
    }
    @Test fun rejectsOversizedDownloadAndDeletesPartialFile() = temporary { file ->
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { copyVerifiedDownload(ByteArrayInputStream(bytes), file, release(size = 10)) {} }
        }
        assertFalse(file.exists())
    }
    @Test fun rejectsWrongChecksumAndDeletesPartialFile() = temporary { file ->
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { copyVerifiedDownload(ByteArrayInputStream(bytes), file, release(hash = "0".repeat(64))) {} }
        }
        assertFalse(file.exists())
    }
    @Test fun cancellationDeletesPartialFile() = temporary { file ->
        assertThrows(CancellationException::class.java) {
            runBlocking { copyVerifiedDownload(ByteArrayInputStream(bytes), file, release()) { throw CancellationException() } }
        }
        assertFalse(file.exists())
    }
}
