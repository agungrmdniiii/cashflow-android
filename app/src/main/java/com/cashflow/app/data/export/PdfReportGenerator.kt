package com.cashflow.app.data.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.cashflow.app.data.model.Asset
import com.cashflow.app.data.model.Category
import com.cashflow.app.data.model.Transaction
import com.cashflow.app.data.model.TransactionType
import com.cashflow.app.ui.components.Formatters
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Native Android PDF Report Generator for "Duit Aing"
 * Zero external libraries (uses android.graphics.pdf.PdfDocument from Android standard framework).
 * Creates beautiful, clean A4 PDF statements with summaries and full transactions table.
 */
object PdfReportGenerator {

    private const val PAGE_WIDTH = 595 // A4 width in points at 72 DPI
    private const val PAGE_HEIGHT = 842 // A4 height in points at 72 DPI
    private const val MARGIN_LEFT = 36f
    private const val MARGIN_RIGHT = 559f
    private const val BOTTOM_LIMIT = 790f

    fun generatePdfReport(
        context: Context,
        file: File,
        transactions: List<Transaction>,
        categories: List<Category>,
        assets: List<Asset>,
        periodTitle: String
    ): Result<File> {
        val pdfDocument = PdfDocument()
        return try {
            val catMap = categories.associateBy { it.id }
            val assetMap = assets.associateBy { it.id }

            val totalIncome = transactions.filter { it.type == TransactionType.INCOME.value }.sumOf { it.amount }
            val totalExpense = transactions.filter { it.type == TransactionType.EXPENSE.value }.sumOf { it.amount }
            val netCashflow = totalIncome - totalExpense

            val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("id-ID")))

            // Paints setup
            val textPrimaryColor = Color.rgb(27, 38, 32)
            val textSecondaryColor = Color.rgb(72, 88, 80)
            val borderColor = Color.rgb(218, 214, 202)
            val cardBgColor = Color.rgb(247, 245, 240)
            val tableHeaderBg = Color.rgb(239, 236, 228)
            val jadeGreen = Color.rgb(45, 106, 79)
            val terraRed = Color.rgb(186, 26, 26)
            val transferBlue = Color.rgb(71, 108, 142)

            val titlePaint = Paint().apply {
                isAntiAlias = true
                textSize = 17f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = textPrimaryColor
            }

            val subtitlePaint = Paint().apply {
                isAntiAlias = true
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                color = textSecondaryColor
            }

            val metaRightPaint = Paint().apply {
                isAntiAlias = true
                textSize = 8.5f
                textAlign = Paint.Align.RIGHT
                color = textSecondaryColor
            }

            val metaRightBoldPaint = Paint().apply {
                isAntiAlias = true
                textSize = 8.5f
                textAlign = Paint.Align.RIGHT
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = textPrimaryColor
            }

            val linePaint = Paint().apply {
                isAntiAlias = true
                strokeWidth = 1f
                color = borderColor
            }

            val cardFillPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
            }

            val cardBorderPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = borderColor
            }

            val cardLabelPaint = Paint().apply {
                isAntiAlias = true
                textSize = 7.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = textSecondaryColor
            }

            val cardValuePaint = Paint().apply {
                isAntiAlias = true
                textSize = 10.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val tableHeaderPaint = Paint().apply {
                isAntiAlias = true
                textSize = 8f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = textPrimaryColor
            }

            val rowTextPaint = Paint().apply {
                isAntiAlias = true
                textSize = 8f
                color = textPrimaryColor
            }

            val rowSecondaryPaint = Paint().apply {
                isAntiAlias = true
                textSize = 8f
                color = textSecondaryColor
            }

            val amountIncomePaint = Paint().apply {
                isAntiAlias = true
                textSize = 8f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
                color = jadeGreen
            }

            val amountExpensePaint = Paint().apply {
                isAntiAlias = true
                textSize = 8f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
                color = terraRed
            }

            val amountTransferPaint = Paint().apply {
                isAntiAlias = true
                textSize = 8f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
                color = transferBlue
            }

            val footerPaint = Paint().apply {
                isAntiAlias = true
                textSize = 7.5f
                color = Color.rgb(140, 155, 148)
            }

            val zebraPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
                color = Color.rgb(250, 249, 247)
            }

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas

            // --- PAGE 1: HEADER & SUMMARY ---
            // 1. Top Brand Header
            canvas.drawText("DUIT AING", MARGIN_LEFT, 48f, titlePaint)
            canvas.drawText("Laporan Keuangan & Mutasi Kas Pribadi", MARGIN_LEFT, 61f, subtitlePaint)

            canvas.drawText("Tanggal Cetak: $dateStr", MARGIN_RIGHT, 48f, metaRightPaint)
            canvas.drawText("Periode: $periodTitle", MARGIN_RIGHT, 61f, metaRightBoldPaint)

            canvas.drawLine(MARGIN_LEFT, 72f, MARGIN_RIGHT, 72f, linePaint)

            // 2. Summary Metric Boxes (3 boxes)
            val boxWidth = (MARGIN_RIGHT - MARGIN_LEFT - 16f) / 3f
            val boxHeight = 44f
            val boxY = 82f

            // Box 1: Pemasukan
            val b1 = RectF(MARGIN_LEFT, boxY, MARGIN_LEFT + boxWidth, boxY + boxHeight)
            cardFillPaint.color = Color.rgb(235, 245, 240)
            canvas.drawRoundRect(b1, 6f, 6f, cardFillPaint)
            canvas.drawRoundRect(b1, 6f, 6f, cardBorderPaint)
            canvas.drawText("TOTAL PEMASUKAN", b1.left + 10f, b1.top + 16f, cardLabelPaint)
            cardValuePaint.color = jadeGreen
            canvas.drawText("+" + Formatters.formatRupiah(totalIncome), b1.left + 10f, b1.top + 33f, cardValuePaint)

            // Box 2: Pengeluaran
            val b2 = RectF(b1.right + 8f, boxY, b1.right + 8f + boxWidth, boxY + boxHeight)
            cardFillPaint.color = Color.rgb(253, 238, 238)
            canvas.drawRoundRect(b2, 6f, 6f, cardFillPaint)
            canvas.drawRoundRect(b2, 6f, 6f, cardBorderPaint)
            canvas.drawText("TOTAL PENGELUARAN", b2.left + 10f, b2.top + 16f, cardLabelPaint)
            cardValuePaint.color = terraRed
            canvas.drawText("-" + Formatters.formatRupiah(totalExpense), b2.left + 10f, b2.top + 33f, cardValuePaint)

            // Box 3: Arus Kas Bersih
            val b3 = RectF(b2.right + 8f, boxY, MARGIN_RIGHT, boxY + boxHeight)
            cardFillPaint.color = cardBgColor
            canvas.drawRoundRect(b3, 6f, 6f, cardFillPaint)
            canvas.drawRoundRect(b3, 6f, 6f, cardBorderPaint)
            canvas.drawText("ARUS KAS BERSIH", b3.left + 10f, b3.top + 16f, cardLabelPaint)
            cardValuePaint.color = if (netCashflow >= 0) jadeGreen else terraRed
            val netPrefix = if (netCashflow >= 0) "+" else ""
            canvas.drawText(netPrefix + Formatters.formatRupiah(netCashflow), b3.left + 10f, b3.top + 33f, cardValuePaint)

            // 3. Table Header Function
            fun drawTableHeader(c: Canvas, topY: Float) {
                val headerRect = RectF(MARGIN_LEFT, topY, MARGIN_RIGHT, topY + 18f)
                cardFillPaint.color = tableHeaderBg
                c.drawRoundRect(headerRect, 4f, 4f, cardFillPaint)
                c.drawRoundRect(headerRect, 4f, 4f, cardBorderPaint)

                val textY = topY + 12f
                c.drawText("NO", 42f, textY, tableHeaderPaint)
                c.drawText("TANGGAL", 66f, textY, tableHeaderPaint)
                c.drawText("DESKRIPSI", 136f, textY, tableHeaderPaint)
                c.drawText("KATEGORI", 296f, textY, tableHeaderPaint)
                c.drawText("ASET / AKUN", 390f, textY, tableHeaderPaint)
                tableHeaderPaint.textAlign = Paint.Align.RIGHT
                c.drawText("NOMINAL", MARGIN_RIGHT - 8f, textY, tableHeaderPaint)
                tableHeaderPaint.textAlign = Paint.Align.LEFT
            }

            fun drawPageFooter(c: Canvas, pageIdx: Int) {
                c.drawLine(MARGIN_LEFT, BOTTOM_LIMIT + 10f, MARGIN_RIGHT, BOTTOM_LIMIT + 10f, linePaint)
                c.drawText("Duit Aing - Catatan Kas Pribadi Terenkripsi Lokal", MARGIN_LEFT, BOTTOM_LIMIT + 24f, footerPaint)
                footerPaint.textAlign = Paint.Align.RIGHT
                c.drawText("Halaman $pageIdx", MARGIN_RIGHT, BOTTOM_LIMIT + 24f, footerPaint)
                footerPaint.textAlign = Paint.Align.LEFT
            }

            // Draw Table Header on Page 1
            var currentY = 142f
            drawTableHeader(canvas, currentY)
            currentY += 22f

            val rowHeight = 18f

            if (transactions.isEmpty()) {
                canvas.drawText("Tidak ada transaksi pada periode ini.", MARGIN_LEFT + 10f, currentY + 15f, rowSecondaryPaint)
                currentY += 30f
            } else {
                transactions.forEachIndexed { index, tx ->
                    if (currentY + rowHeight > BOTTOM_LIMIT) {
                        drawPageFooter(canvas, pageNumber)
                        pdfDocument.finishPage(page)

                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas

                        // Header on subsequent pages
                        canvas.drawText("DUIT AING - LAPORAN KAS (LANJUTAN)", MARGIN_LEFT, 44f, subtitlePaint)
                        canvas.drawLine(MARGIN_LEFT, 50f, MARGIN_RIGHT, 50f, linePaint)
                        currentY = 56f
                        drawTableHeader(canvas, currentY)
                        currentY += 22f
                    }

                    // Zebra stripe on alternate rows
                    if (index % 2 == 1) {
                        canvas.drawRect(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY + rowHeight, zebraPaint)
                    }

                    val rowBaseline = currentY + 12f
                    val catName = catMap[tx.categoryId]?.name ?: "Lainnya"
                    val assetName = assetMap[tx.assetId]?.name ?: "Tunai"
                    val destAssetName = tx.destinationAssetId?.let { assetMap[it]?.name }

                    val descDisplay = if (tx.description.length > 30) tx.description.take(28) + "…" else tx.description
                    val catDisplay = if (catName.length > 18) catName.take(16) + "…" else catName
                    val assetDisplay = if (destAssetName != null) {
                        "$assetName ➔ $destAssetName"
                    } else assetName
                    val safeAssetDisplay = if (assetDisplay.length > 18) assetDisplay.take(16) + "…" else assetDisplay

                    // Render Row Columns
                    canvas.drawText("${index + 1}", 42f, rowBaseline, rowSecondaryPaint)
                    canvas.drawText(tx.transactionDate, 66f, rowBaseline, rowTextPaint)
                    canvas.drawText(descDisplay, 136f, rowBaseline, rowTextPaint)
                    canvas.drawText(catDisplay, 296f, rowBaseline, rowSecondaryPaint)
                    canvas.drawText(safeAssetDisplay, 390f, rowBaseline, rowSecondaryPaint)

                    when (tx.type) {
                        TransactionType.INCOME.value -> {
                            canvas.drawText("+" + Formatters.formatRupiah(tx.amount), MARGIN_RIGHT - 8f, rowBaseline, amountIncomePaint)
                        }
                        TransactionType.EXPENSE.value -> {
                            canvas.drawText("-" + Formatters.formatRupiah(tx.amount), MARGIN_RIGHT - 8f, rowBaseline, amountExpensePaint)
                        }
                        else -> {
                            canvas.drawText(Formatters.formatRupiah(tx.amount), MARGIN_RIGHT - 8f, rowBaseline, amountTransferPaint)
                        }
                    }

                    currentY += rowHeight
                }
            }

            drawPageFooter(canvas, pageNumber)
            pdfDocument.finishPage(page)

            // Save PDF to destination file
            file.parentFile?.mkdirs()
            val fos = FileOutputStream(file)
            pdfDocument.writeTo(fos)
            fos.flush()
            fos.close()

            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            pdfDocument.close()
        }
    }
}
