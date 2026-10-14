package com.cashflow.app.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.regex.Pattern
import kotlin.coroutines.resume

data class ReceiptOcrResult(
    val merchantName: String? = null,
    val totalAmount: Long? = null,
    val date: String? = null, // YYYY-MM-DD
    val rawText: String = "",
    val confidenceNotes: String = ""
)

object ReceiptOcrService {

    private const val MAX_DIMENSION = 1280
    private const val JPEG_QUALITY = 75

    /**
     * Mengompresi gambar struk (maksimal 1280px dan JPEG quality ~75%) dan menyimpannya di folder privat aplikasi.
     * Mengembalikan absolute path file yang disimpan.
     */
    fun saveAndCompressReceipt(context: Context, sourceUri: Uri): String? {
        return try {
            val receiptsDir = File(context.filesDir, "receipts").apply {
                if (!exists()) mkdirs()
            }
            val destinationFile = File(receiptsDir, "receipt_${UUID.randomUUID()}.jpg")

            // Baca orientasi EXIF
            var rotationDegrees = 0
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                try {
                    val exif = ExifInterface(input)
                    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    rotationDegrees = when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                } catch (_: Exception) {}
            }

            // Decode ukuran gambar terlebih dahulu
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }

            var inSampleSize = 1
            val maxSide = maxOf(options.outWidth, options.outHeight)
            if (maxSide > MAX_DIMENSION) {
                inSampleSize = maxSide / MAX_DIMENSION
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }

            var bitmap = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            } ?: return null

            // Koreksi rotasi jika perlu
            if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) {
                    bitmap.recycle()
                    bitmap = rotated
                }
            }

            // Downscale presisi jika masih melebihi MAX_DIMENSION
            val currentMax = maxOf(bitmap.width, bitmap.height)
            if (currentMax > MAX_DIMENSION) {
                val scale = MAX_DIMENSION.toFloat() / currentMax
                val scaledWidth = (bitmap.width * scale).toInt()
                val scaledHeight = (bitmap.height * scale).toInt()
                val scaled = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
                if (scaled != bitmap) {
                    bitmap.recycle()
                    bitmap = scaled
                }
            }

            FileOutputStream(destinationFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            bitmap.recycle()

            destinationFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Menghapus file foto struk secara bersih.
     */
    fun deleteReceiptFile(filePath: String?): Boolean {
        if (filePath.isNullOrBlank()) return false
        return try {
            val file = File(filePath)
            if (file.exists()) file.delete() else false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Menjalankan OCR lokal menggunakan Google ML Kit Text Recognition dari file foto.
     */
    suspend fun scanReceipt(context: Context, imagePath: String): ReceiptOcrResult = suspendCancellableCoroutine { cont ->
        try {
            val file = File(imagePath)
            if (!file.exists()) {
                cont.resume(ReceiptOcrResult(rawText = "File foto tidak ditemukan."))
                return@suspendCancellableCoroutine
            }

            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap == null) {
                cont.resume(ReceiptOcrResult(rawText = "Gagal memproses gambar foto."))
                return@suspendCancellableCoroutine
            }

            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    bitmap.recycle()
                    val parsed = parseReceiptText(visionText.text)
                    cont.resume(parsed)
                }
                .addOnFailureListener { e ->
                    bitmap.recycle()
                    cont.resume(ReceiptOcrResult(rawText = "Gagal membaca teks: ${e.message}"))
                }
        } catch (e: Exception) {
            cont.resume(ReceiptOcrResult(rawText = "Kesalahan scan: ${e.message}"))
        }
    }

    /**
     * Parsing pintar teks struk belanja untuk mencari:
     * 1. Nama toko (baris atas yang bukan angka/tanggal).
     * 2. Total belanja (angka di sebelah TOTAL / GRAND TOTAL / BAYAR / RP).
     * 3. Tanggal transaksi.
     */
    fun parseReceiptText(rawText: String): ReceiptOcrResult {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return ReceiptOcrResult(rawText = rawText)
        }

        // 1. Ekstrak Merchant Name (ambil dari 3 baris pertama yang bermakna dan bukan sekadar angka atau simbol)
        var merchant: String? = null
        for (i in 0 until minOf(4, lines.size)) {
            val line = lines[i]
            val cleaned = line.replace(Regex("[^a-zA-Z0-9 &.'-]"), "").trim()
            if (cleaned.length >= 3 && !cleaned.matches(Regex("^[0-9\\s/.:-]+$"))) {
                // Abaikan kata umum non-toko
                val lower = cleaned.lowercase()
                if (!lower.contains("struk") && !lower.contains("receipt") && !lower.contains("selamat datang")) {
                    merchant = cleaned
                    break
                }
            }
        }

        // 2. Ekstrak Total Nominal
        var detectedAmount: Long? = null
        val totalKeywords = listOf("total", "grand total", "total bayar", "tagihan", "jumlah", "net total", "subtotal")

        // Prioritas A: Cari baris yang mengandung keyword total
        for (i in lines.indices.reversed()) {
            val line = lines[i].lowercase()
            for (kw in totalKeywords) {
                if (line.contains(kw)) {
                    val amount = extractAmountFromLine(lines[i])
                    if (amount != null && amount > 0) {
                        detectedAmount = amount
                        break
                    }
                    // Cek baris berikutnya jika nominal diletakkan di baris bawahnya
                    if (i + 1 < lines.size) {
                        val nextAmount = extractAmountFromLine(lines[i + 1])
                        if (nextAmount != null && nextAmount > 0) {
                            detectedAmount = nextAmount
                            break
                        }
                    }
                }
            }
            if (detectedAmount != null) break
        }

        // Prioritas B: Jika tidak ada baris ber-keyword total, cari angka terbesar yang masuk akal di bagian bawah struk
        if (detectedAmount == null) {
            val candidateAmounts = mutableListOf<Long>()
            val searchLines = lines.takeLast(minOf(10, lines.size))
            for (l in searchLines) {
                extractAmountFromLine(l)?.let {
                    if (it in 1000L..500000000L) candidateAmounts.add(it)
                }
            }
            detectedAmount = candidateAmounts.maxOrNull()
        }

        // 3. Ekstrak Tanggal Transaksi
        var detectedDate: String? = null
        val datePattern = Pattern.compile("(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{2,4})")
        for (line in lines) {
            val matcher = datePattern.matcher(line)
            if (matcher.find()) {
                val p1 = matcher.group(1)?.toIntOrNull() ?: continue
                val p2 = matcher.group(2)?.toIntOrNull() ?: continue
                var year = matcher.group(3)?.toIntOrNull() ?: continue
                if (year < 100) year += 2000

                // Asumsikan DD/MM/YYYY
                var day = p1
                var month = p2
                if (month > 12 && day <= 12) {
                    // Terbalik MM/DD
                    val temp = day
                    day = month
                    month = temp
                }
                if (month in 1..12 && day in 1..31) {
                    try {
                        val d = LocalDate.of(year, month, day)
                        detectedDate = d.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        break
                    } catch (_: Exception) {}
                }
            }
        }

        val noteBuilder = StringBuilder()
        if (detectedAmount != null) noteBuilder.append("Total: Rp $detectedAmount. ")
        if (merchant != null) noteBuilder.append("Toko: $merchant. ")
        if (detectedDate != null) noteBuilder.append("Tanggal: $detectedDate.")

        return ReceiptOcrResult(
            merchantName = merchant,
            totalAmount = detectedAmount,
            date = detectedDate,
            rawText = rawText,
            confidenceNotes = noteBuilder.toString().trim()
        )
    }

    private fun extractAmountFromLine(line: String): Long? {
        val clean = line.replace("Rp", "", ignoreCase = true)
            .replace("IDR", "", ignoreCase = true)
            .trim()

        val matcher = Pattern.compile("([0-9]{1,3}(?:[.,][0-9]{3})*(?:[.,][0-9]{2})?|[0-9]+)").matcher(clean)
        var lastValid: Long? = null
        while (matcher.find()) {
            var rawNum = matcher.group(1)?.replace(" ", "") ?: continue
            // Tangani pemisah ribuan titik/koma
            if (rawNum.contains(".") && rawNum.contains(",")) {
                // Contoh: 15.000,00 atau 15,000.00
                rawNum = if (rawNum.lastIndexOf(",") > rawNum.lastIndexOf(".")) {
                    rawNum.substring(0, rawNum.lastIndexOf(",")).replace(".", "")
                } else {
                    rawNum.substring(0, rawNum.lastIndexOf(".")).replace(",", "")
                }
            } else if (rawNum.contains(".")) {
                // Cek apakah desimal (misal 15000.00) atau ribuan (15.000)
                val parts = rawNum.split(".")
                rawNum = if (parts.size == 2 && parts[1].length == 2) {
                    parts[0]
                } else {
                    rawNum.replace(".", "")
                }
            } else if (rawNum.contains(",")) {
                val parts = rawNum.split(",")
                rawNum = if (parts.size == 2 && parts[1].length == 2) {
                    parts[0]
                } else {
                    rawNum.replace(",", "")
                }
            }
            val num = rawNum.toLongOrNull()
            if (num != null && num > 0) {
                lastValid = num
            }
        }
        return lastValid
    }
}
