package com.cashflow.app

import android.graphics.Bitmap
import android.net.Uri
import android.os.StrictMode
import android.os.Looper
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.cashflow.app.data.service.ReceiptOcrService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Scratch images only; never creates a transaction or modifies an existing receipt. */
class ReceiptSmoothnessTest {
    @Test fun previewsAreBoundedAndMissingFilesAreSafe() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File.createTempFile("receipt-preview-", ".jpg", context.cacheDir)
        val original = Bitmap.createBitmap(2049, 2049, Bitmap.Config.ARGB_8888)
        try {
            source.outputStream().use { original.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            original.recycle()
            val preview = withContext(Dispatchers.Main) { ReceiptOcrService.loadReceiptPreview(source.path, 256) }
            assertNotNull(preview)
            try {
                assertTrue(preview!!.width in 1..256)
                assertTrue(preview.height in 1..256)
            } finally { preview?.recycle() }
            assertNull(ReceiptOcrService.loadReceiptPreview(source.path + ".missing", 256))
            assertNull(ReceiptOcrService.loadReceiptPreview(null, 256))
        } finally {
            if (!original.isRecycled) original.recycle()
            source.delete()
        }
    }

    @Test fun compressingAReceiptDoesNotReadOrWriteFilesOnTheUiThread() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val fileAccessOnUiThread = CopyOnWriteArrayList<Boolean>()
        val context = object : ContextWrapper(base) {
            override fun getFilesDir(): File {
                fileAccessOnUiThread += Looper.myLooper() == Looper.getMainLooper()
                return base.filesDir
            }
        }
        val source = File.createTempFile("receipt-smoothness-", ".jpg", context.cacheDir)
        var saved: String? = null
        val bitmap = Bitmap.createBitmap(2400, 3200, Bitmap.Config.ARGB_8888)
        try {
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            bitmap.recycle()
            val violations = CopyOnWriteArrayList<String>()
            withContext(Dispatchers.Main) {
                val previous = StrictMode.getThreadPolicy()
                StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads().detectDiskWrites()
                    .penaltyListener({ it.run() }) { violations += it.javaClass.simpleName }.build())
                try { saved = ReceiptOcrService.saveAndCompressReceipt(context, Uri.fromFile(source)) }
                finally { StrictMode.setThreadPolicy(previous) }
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertNotNull("Compression must still produce a receipt", saved)
            assertFalse("Receipt compression must run away from the UI thread", fileAccessOnUiThread.any { it })
            assertTrue("Receipt processing blocked UI with file operations: $violations", violations.isEmpty())
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
            source.delete()
            saved?.let { File(it).delete() }
        }
    }
}
