package com.cashflow.app.data.benchmark

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.cashflow.app.data.db.DatabaseHelper
import com.cashflow.app.data.model.Transaction
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.random.Random

data class BenchmarkResult(
    val totalTransactions: Int,
    val generationTimeMs: Long,
    val insertionTimeMs: Long,
    val homeSummaryQueryMs: Long,
    val transactionPaginationMs: Long,
    val categoryReportQueryMs: Long,
    val monthlyTrendQueryMs: Long,
    val budgetUsageQueryMs: Long,
    val isTargetMet: Boolean
)

object PerformanceBenchmarker {

    private val sampleCategories = listOf(
        "cat_makanan" to "expense",
        "cat_transportasi" to "expense",
        "cat_tagihan" to "expense",
        "cat_belanja" to "expense",
        "cat_hiburan" to "expense",
        "cat_kesehatan" to "expense",
        "cat_rumah" to "expense",
        "cat_keluarga" to "expense",
        "cat_donasi" to "expense",
        "cat_gaji" to "income",
        "cat_freelance" to "income",
        "cat_bonus" to "income",
        "cat_investasi_inc" to "income"
    )

    private val descriptionsByCat = mapOf(
        "cat_makanan" to listOf("Makan Siang Nasi Padang", "Kopi Susu Gula Aren", "Malam Sate Ayam", "Supermarket Camilan", "Gofood Makan Malam"),
        "cat_transportasi" to listOf("Bensin Pertamax", "Ojek Online Kantor", "Tol Trans Jawa", "Parkir Mall", "Servis Motor Bulanan"),
        "cat_tagihan" to listOf("Listrik PLN Token", "Air PDAM", "Internet Fiber Rumah", "Pulsa & Paket Data"),
        "cat_belanja" to listOf("Belanja Bulanan Superindo", "Sabun & Odol", "Baju Kaos Santai"),
        "cat_hiburan" to listOf("Langganan Netflix", "Tiket Bioskop", "Buku Bacaan"),
        "cat_kesehatan" to listOf("Vitamin C & Zinc", "Obat Flu & Batuk"),
        "cat_rumah" to listOf("Gas Elpiji 3kg", "Lampu LED Rumah"),
        "cat_keluarga" to listOf("Uang Jajan Adik", "Susu Anak"),
        "cat_donasi" to listOf("Sedekah Jumat", "Zakat Mal"),
        "cat_gaji" to listOf("Gaji Bulanan Perusahaan"),
        "cat_freelance" to listOf("Honor Desain Grafis", "Fee Konsultasi Web"),
        "cat_bonus" to listOf("Bonus Project Kinerja", "THR Keagamaan"),
        "cat_investasi_inc" to listOf("Dividen Saham", "Bunga Tabungan")
    )

    /**
     * Menghasilkan list transaksi sintetis realistis sebanyak [count]
     * yang terdistribusi merata dalam rentang waktu [years] tahun ke belakang dari [referenceDate].
     */
    fun generateSyntheticTransactions(
        count: Int = 10_000,
        years: Int = 3,
        referenceDate: LocalDate = LocalDate.of(2026, 10, 14),
        assetIds: List<String> = listOf("asset_tunai", "asset_bank", "asset_ewallet")
    ): List<Transaction> {
        val transactions = ArrayList<Transaction>(count)
        val startDate = referenceDate.minusYears(years.toLong())
        val totalDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, referenceDate).toInt().coerceAtLeast(1)
        val random = Random(42) // Fixed seed for reproducible benchmarks

        for (i in 0 until count) {
            val dayOffset = random.nextInt(totalDays + 1)
            val txDate = startDate.plusDays(dayOffset.toLong())
            val dateStr = txDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

            val catPair = sampleCategories[random.nextInt(sampleCategories.size)]
            val catId = catPair.first
            val type = catPair.second

            val descs = descriptionsByCat[catId] ?: listOf("Transaksi $catId")
            val desc = descs[random.nextInt(descs.size)]

            val amount = when (type) {
                "income" -> {
                    if (catId == "cat_gaji") (8_000_000L..25_000_000L step 500_000L).toList().random(random)
                    else (100_000L..2_500_000L step 50_000L).toList().random(random)
                }
                else -> {
                    // Expense
                    when (random.nextInt(10)) {
                        in 0..6 -> (15_000L..150_000L step 5_000L).toList().random(random) // Micro daily
                        in 7..8 -> (200_000L..1_500_000L step 50_000L).toList().random(random) // Mid expenses
                        else -> (2_000_000L..5_000_000L step 250_000L).toList().random(random) // Big expenses
                    }
                }
            }

            val assetId = assetIds[random.nextInt(assetIds.size)]
            val hour = random.nextInt(8, 22)
            val minute = random.nextInt(0, 60)
            val timeStr = "%02d:%02d".format(hour, minute)

            transactions.add(
                Transaction(
                    id = "syn_${i + 1}_${UUID.randomUUID().toString().substring(0, 8)}",
                    type = type,
                    amount = amount,
                    description = desc,
                    categoryId = catId,
                    assetId = assetId,
                    transactionDate = dateStr,
                    transactionTime = timeStr,
                    note = "Synthetic benchmark data #$i",
                    inputMethod = if (random.nextBoolean()) "manual" else "voice",
                    isSample = true,
                    createdAt = txDate.toEpochDay() * 86_400_000L
                )
            )
        }

        // Sort descending by date & time like a real ledger
        transactions.sortWith(compareByDescending<Transaction> { it.transactionDate }.thenByDescending { it.transactionTime })
        return transactions
    }

    /**
     * Memasukkan data transaksi sintetis secara batch berkecepatan tinggi menggunakan compiled SQLiteStatement.
     */
    fun insertBatchFast(db: SQLiteDatabase, transactions: List<Transaction>): Long {
        val start = System.currentTimeMillis()
        val sql = """
            INSERT INTO ${DatabaseHelper.TABLE_TRANSACTIONS} (
                id, type, amount, description, category_id, asset_id, destination_asset_id,
                transaction_date, transaction_time, note, attachment, input_method,
                debt_id, goal_id, is_sample, tags, is_adjustment, adjustment_direction, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        val statement = db.compileStatement(sql)
        db.beginTransaction()
        try {
            for (tx in transactions) {
                statement.clearBindings()
                statement.bindString(1, tx.id)
                statement.bindString(2, tx.type)
                statement.bindLong(3, tx.amount)
                statement.bindString(4, tx.description)
                if (tx.categoryId != null) statement.bindString(5, tx.categoryId) else statement.bindNull(5)
                statement.bindString(6, tx.assetId)
                if (tx.destinationAssetId != null) statement.bindString(7, tx.destinationAssetId) else statement.bindNull(7)
                statement.bindString(8, tx.transactionDate)
                statement.bindString(9, tx.transactionTime)
                statement.bindString(10, tx.note)
                if (tx.attachment != null) statement.bindString(11, tx.attachment) else statement.bindNull(11)
                statement.bindString(12, tx.inputMethod)
                if (tx.debtId != null) statement.bindString(13, tx.debtId) else statement.bindNull(13)
                if (tx.goalId != null) statement.bindString(14, tx.goalId) else statement.bindNull(14)
                statement.bindLong(15, if (tx.isSample) 1L else 0L)
                statement.bindString(16, tx.tags)
                statement.bindLong(17, if (tx.isAdjustment) 1L else 0L)
                statement.bindLong(18, tx.adjustmentDirection.toLong())
                statement.bindLong(19, tx.createdAt)
                statement.bindLong(20, tx.updatedAt)
                statement.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return System.currentTimeMillis() - start
    }
}
