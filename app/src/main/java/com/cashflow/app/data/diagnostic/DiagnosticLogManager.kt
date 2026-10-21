package com.cashflow.app.data.diagnostic

import android.content.Context
import android.os.Build
import com.cashflow.app.data.db.DatabaseHelper
import com.cashflow.app.data.repository.BalanceDiscrepancy
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticLogManager {

    private const val LOG_FILE_PATH = "logs/diagnostic.log"

    fun logDiscrepancy(context: Context, discrepancies: List<BalanceDiscrepancy>) {
        if (discrepancies.isEmpty()) return
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val sb = StringBuilder()
        for (d in discrepancies) {
            sb.appendLine("[$dateStr] [INTEGRITY_DISCREPANCY] Aset: '${d.assetName}' (id=${d.assetId}), Tersimpan: Rp ${d.recordedBalance}, Dihitung: Rp ${d.calculatedBalance}, Selisih: Rp ${d.difference}")
        }
        appendLog(context, sb.toString())
    }

    fun logError(context: Context, tag: String, throwable: Throwable) {
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val msg = throwable.message?.take(200) ?: throwable.javaClass.simpleName
        val entry = "[$dateStr] [ERROR] [$tag] ${throwable.javaClass.simpleName}: $msg\n"
        appendLog(context, entry)
    }

    private fun appendLog(context: Context, text: String) {
        try {
            val file = File(context.filesDir, LOG_FILE_PATH)
            file.parentFile?.mkdirs()
            file.appendText(text)
        } catch (_: Exception) {}
    }

    fun generateSanitizedReport(
        context: Context,
        transactionCount: Int,
        assetCount: Int,
        categoryCount: Int,
        budgetCount: Int,
        debtCount: Int,
        goalCount: Int,
        recurringCount: Int
    ): String {
        val runtime = Runtime.getRuntime()
        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMem = runtime.maxMemory() / (1024 * 1024)
        val totalMem = runtime.totalMemory() / (1024 * 1024)

        val dbFile = context.getDatabasePath(DatabaseHelper.DATABASE_NAME)
        val dbSizeBytes = if (dbFile.exists()) dbFile.length() else 0L
        val dbSizeKb = dbSizeBytes / 1024

        val crashFile = File(context.filesDir, "logs/crash_latest.log")
        val crashLogContent = if (crashFile.exists()) {
            crashFile.readText().take(1500)
        } else {
            "Tidak ada catatan crash (Sistem berjalan stabil)."
        }

        val diagFile = File(context.filesDir, LOG_FILE_PATH)
        val diagLogContent = if (diagFile.exists()) {
            diagFile.readText().takeLast(1500)
        } else {
            "Tidak ada catatan anomali atau selisih integritas."
        }

        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        return buildString {
            appendLine("=== DUIT AING SISTEM DIAGNOSTIK & LOG ===")
            appendLine("Waktu Laporan   : $dateStr")
            appendLine("Versi Aplikasi  : ${com.cashflow.app.BuildConfig.VERSION_NAME} (Build ${com.cashflow.app.BuildConfig.VERSION_CODE})")
            appendLine("Arsitektur      : Offline-First Hybrid")
            appendLine()
            appendLine("--- INFORMASI PERANGKAT ---")
            appendLine("Model           : ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Versi Android   : Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Memori JVM      : Terpakai ${usedMem}MB / Alokasi ${totalMem}MB (Maks ${maxMem}MB)")
            appendLine()
            appendLine("--- METRIK BASIS DATA (SANITIZED) ---")
            appendLine("Ukuran Berkas DB: ${dbSizeKb} KB ($dbSizeBytes bytes)")
            appendLine("Total Transaksi : $transactionCount baris")
            appendLine("Total Dompet/Aset: $assetCount baris")
            appendLine("Total Kategori  : $categoryCount baris")
            appendLine("Total Anggaran  : $budgetCount baris")
            appendLine("Total Hutang    : $debtCount baris")
            appendLine("Total Target    : $goalCount baris")
            appendLine("Total Berulang  : $recurringCount baris")
            appendLine("Catatan Privasi : TIDAK ADA data nominal sensitif, deskripsi transaksi, atau kunci rahasia yang direkam.")
            appendLine()
            appendLine("--- LOG TERAKHIR SISTEM & INTEGRITAS ---")
            appendLine(crashLogContent)
            appendLine(diagLogContent)
            appendLine("=== AKHIR LAPORAN ===")
        }
    }
}
