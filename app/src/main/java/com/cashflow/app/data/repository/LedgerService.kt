package com.cashflow.app.data.repository

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.cashflow.app.data.db.DatabaseHelper
import com.cashflow.app.data.model.DebtStatus
import com.cashflow.app.data.model.Transaction
import com.cashflow.app.data.model.TransactionType
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class BalanceDiscrepancy(
    val assetId: String,
    val assetName: String,
    val recordedBalance: Long,
    val calculatedBalance: Long,
    val difference: Long
)

data class LedgerRecalculateResult(
    val totalAssetsChecked: Int,
    val discrepanciesFound: Int,
    val details: List<BalanceDiscrepancy>
)

object LedgerService {

    private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE
    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Validasi ketat aturan transaksi keuangan.
     */
    fun validateTransaction(tx: Transaction): Result<Unit> {
        if (tx.amount <= 0L) {
            return Result.failure(IllegalArgumentException("Nominal transaksi harus lebih dari Rp 0 dan berupa bilangan bulat."))
        }
        if (tx.assetId.isBlank()) {
            return Result.failure(IllegalArgumentException("Aset sumber/tujuan wajib dipilih."))
        }
        try {
            LocalDate.parse(tx.transactionDate, DATE_FORMATTER)
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("Format tanggal tidak valid. Gunakan format YYYY-MM-DD."))
        }
        if (tx.transactionTime.isNotBlank()) {
            try {
                LocalTime.parse(tx.transactionTime, TIME_FORMATTER)
            } catch (e: Exception) {
                return Result.failure(IllegalArgumentException("Format waktu tidak valid. Gunakan format HH:mm."))
            }
        }
        when (tx.type) {
            TransactionType.INCOME.value, TransactionType.EXPENSE.value, TransactionType.ADJUSTMENT.value -> {
                // Valid
            }
            TransactionType.TRANSFER.value -> {
                if (tx.destinationAssetId.isNullOrBlank()) {
                    return Result.failure(IllegalArgumentException("Aset tujuan transfer wajib dipilih."))
                }
                if (tx.assetId == tx.destinationAssetId) {
                    return Result.failure(IllegalArgumentException("Aset sumber dan aset tujuan transfer tidak boleh sama."))
                }
            }
            else -> {
                return Result.failure(IllegalArgumentException("Jenis transaksi '${tx.type}' tidak valid."))
            }
        }
        return Result.success(Unit)
    }

    /**
     * Formula dasar ledger:
     * Saldo Aset = Saldo Awal + Total Pemasukan - Total Pengeluaran - Total Transfer Keluar + Total Transfer Masuk +/- Penyesuaian
     */
    fun calculateAssetBalance(openingBalance: Long, transactions: List<Transaction>, assetId: String): Long {
        var balance = openingBalance
        for (tx in transactions) {
            if (tx.assetId == assetId) {
                when (tx.type) {
                    TransactionType.INCOME.value -> balance += tx.amount
                    TransactionType.EXPENSE.value -> balance -= tx.amount
                    TransactionType.TRANSFER.value -> balance -= tx.amount
                    TransactionType.ADJUSTMENT.value -> {
                        if (tx.note.contains("(-)")) {
                            balance -= tx.amount
                        } else {
                            balance += tx.amount
                        }
                    }
                }
            }
            if (tx.type == TransactionType.TRANSFER.value && tx.destinationAssetId == assetId) {
                balance += tx.amount
            }
        }
        return balance
    }

    /**
     * Memeriksa kecukupan saldo untuk transaksi pengeluaran atau transfer keluar.
     * Mengembalikan true jika saldo mencukupi, false jika defisit.
     */
    fun checkBalanceSufficiency(currentBalance: Long, amount: Long): Boolean {
        return currentBalance >= amount
    }

    /**
     * Menjalankan penghitungan ulang saldo seluruh aset dari seluruh riwayat transaksi di SQLite.
     * Mendeteksi selisih antara current_balance tersimpan dengan hasil kalkulasi riwayat transaksi,
     * lalu memperbaiki current_balance di database.
     */
    fun recalculateAllBalances(db: SQLiteDatabase): LedgerRecalculateResult {
        val discrepancies = mutableListOf<BalanceDiscrepancy>()
        var totalAssets = 0

        // Ambil semua aset
        val assetQuery = "SELECT id, name, opening_balance, current_balance FROM ${DatabaseHelper.TABLE_ASSETS}"
        val assetCursor = db.rawQuery(assetQuery, null)

        val assetsToProcess = mutableListOf<Triple<String, String, Pair<Long, Long>>>()
        assetCursor.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val name = cursor.getString(1)
                val openingBalance = cursor.getLong(2)
                val currentBalance = cursor.getLong(3)
                assetsToProcess.add(Triple(id, name, Pair(openingBalance, currentBalance)))
            }
        }

        totalAssets = assetsToProcess.size

        for ((assetId, assetName, balances) in assetsToProcess) {
            val (openingBalance, recordedBalance) = balances

            // Hitung total pemasukan
            val incCursor = db.rawQuery(
                "SELECT COALESCE(SUM(amount), 0) FROM ${DatabaseHelper.TABLE_TRANSACTIONS} WHERE asset_id = ? AND type = ?",
                arrayOf(assetId, TransactionType.INCOME.value)
            )
            val totalIncome = incCursor.use { if (it.moveToNext()) it.getLong(0) else 0L }

            // Hitung total pengeluaran
            val expCursor = db.rawQuery(
                "SELECT COALESCE(SUM(amount), 0) FROM ${DatabaseHelper.TABLE_TRANSACTIONS} WHERE asset_id = ? AND type = ?",
                arrayOf(assetId, TransactionType.EXPENSE.value)
            )
            val totalExpense = expCursor.use { if (it.moveToNext()) it.getLong(0) else 0L }

            // Hitung total transfer keluar
            val transferOutCursor = db.rawQuery(
                "SELECT COALESCE(SUM(amount), 0) FROM ${DatabaseHelper.TABLE_TRANSACTIONS} WHERE asset_id = ? AND type = ?",
                arrayOf(assetId, TransactionType.TRANSFER.value)
            )
            val totalTransferOut = transferOutCursor.use { if (it.moveToNext()) it.getLong(0) else 0L }

            // Hitung total transfer masuk
            val transferInCursor = db.rawQuery(
                "SELECT COALESCE(SUM(amount), 0) FROM ${DatabaseHelper.TABLE_TRANSACTIONS} WHERE destination_asset_id = ? AND type = ?",
                arrayOf(assetId, TransactionType.TRANSFER.value)
            )
            val totalTransferIn = transferInCursor.use { if (it.moveToNext()) it.getLong(0) else 0L }

            // Hitung penyesuaian saldo positif dan negatif
            val adjInCursor = db.rawQuery(
                "SELECT COALESCE(SUM(amount), 0) FROM ${DatabaseHelper.TABLE_TRANSACTIONS} WHERE asset_id = ? AND type = ? AND note NOT LIKE '%(-)%'" ,
                arrayOf(assetId, TransactionType.ADJUSTMENT.value)
            )
            val totalAdjIn = adjInCursor.use { if (it.moveToNext()) it.getLong(0) else 0L }

            val adjOutCursor = db.rawQuery(
                "SELECT COALESCE(SUM(amount), 0) FROM ${DatabaseHelper.TABLE_TRANSACTIONS} WHERE asset_id = ? AND type = ? AND note LIKE '%(-)%'" ,
                arrayOf(assetId, TransactionType.ADJUSTMENT.value)
            )
            val totalAdjOut = adjOutCursor.use { if (it.moveToNext()) it.getLong(0) else 0L }

            val calculatedBalance = openingBalance + totalIncome - totalExpense - totalTransferOut + totalTransferIn + totalAdjIn - totalAdjOut

            if (recordedBalance != calculatedBalance) {
                discrepancies.add(
                    BalanceDiscrepancy(
                        assetId = assetId,
                        assetName = assetName,
                        recordedBalance = recordedBalance,
                        calculatedBalance = calculatedBalance,
                        difference = calculatedBalance - recordedBalance
                    )
                )
                // Perbaiki saldo di database
                val cv = ContentValues().apply {
                    put("current_balance", calculatedBalance)
                    put("updated_at", System.currentTimeMillis())
                }
                db.update(DatabaseHelper.TABLE_ASSETS, cv, "id = ?", arrayOf(assetId))
            }
        }

        return LedgerRecalculateResult(
            totalAssetsChecked = totalAssets,
            discrepanciesFound = discrepancies.size,
            details = discrepancies
        )
    }

    /**
     * Membalik efek transaksi pada aset (Revert balance effect).
     */
    fun revertTransactionEffect(db: SQLiteDatabase, tx: Transaction, now: Long = System.currentTimeMillis()) {
        when (tx.type) {
            TransactionType.INCOME.value -> {
                db.execSQL(
                    "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance - ?, updated_at = ? WHERE id = ?",
                    arrayOf(tx.amount, now, tx.assetId)
                )
            }
            TransactionType.EXPENSE.value -> {
                db.execSQL(
                    "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance + ?, updated_at = ? WHERE id = ?",
                    arrayOf(tx.amount, now, tx.assetId)
                )
            }
            TransactionType.TRANSFER.value -> {
                db.execSQL(
                    "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance + ?, updated_at = ? WHERE id = ?",
                    arrayOf(tx.amount, now, tx.assetId)
                )
                if (!tx.destinationAssetId.isNullOrBlank()) {
                    db.execSQL(
                        "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance - ?, updated_at = ? WHERE id = ?",
                        arrayOf(tx.amount, now, tx.destinationAssetId)
                    )
                }
            }
            TransactionType.ADJUSTMENT.value -> {
                val isNegative = tx.note.contains("(-)")
                val sql = if (isNegative) {
                    "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance + ?, updated_at = ? WHERE id = ?"
                } else {
                    "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance - ?, updated_at = ? WHERE id = ?"
                }
                db.execSQL(sql, arrayOf(tx.amount, now, tx.assetId))
            }
        }

        // Sinkronisasi hutang terkait jika ada
        if (!tx.debtId.isNullOrBlank()) {
            revertDebtSync(db, tx.debtId, tx.amount, now)
        }

        // Sinkronisasi target keuangan terkait jika ada
        if (!tx.goalId.isNullOrBlank()) {
            revertGoalSync(db, tx.goalId, tx.amount, now)
        }
    }

    /**
     * Menerapkan efek transaksi pada saldo aset (Apply balance effect).
     */
    fun applyTransactionEffect(db: SQLiteDatabase, tx: Transaction, now: Long = System.currentTimeMillis()) {
        when (tx.type) {
            TransactionType.INCOME.value -> {
                db.execSQL(
                    "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance + ?, updated_at = ? WHERE id = ?",
                    arrayOf(tx.amount, now, tx.assetId)
                )
            }
            TransactionType.EXPENSE.value -> {
                db.execSQL(
                    "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance - ?, updated_at = ? WHERE id = ?",
                    arrayOf(tx.amount, now, tx.assetId)
                )
            }
            TransactionType.TRANSFER.value -> {
                db.execSQL(
                    "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance - ?, updated_at = ? WHERE id = ?",
                    arrayOf(tx.amount, now, tx.assetId)
                )
                db.execSQL(
                    "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance + ?, updated_at = ? WHERE id = ?",
                    arrayOf(tx.amount, now, tx.destinationAssetId)
                )
            }
            TransactionType.ADJUSTMENT.value -> {
                val isNegative = tx.note.contains("(-)")
                val sql = if (isNegative) {
                    "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance - ?, updated_at = ? WHERE id = ?"
                } else {
                    "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance + ?, updated_at = ? WHERE id = ?"
                }
                db.execSQL(sql, arrayOf(tx.amount, now, tx.assetId))
            }
        }

        // Sinkronisasi hutang terkait jika ada
        if (!tx.debtId.isNullOrBlank()) {
            applyDebtSync(db, tx.debtId, tx.amount, now)
        }

        // Sinkronisasi target keuangan terkait jika ada
        if (!tx.goalId.isNullOrBlank()) {
            applyGoalSync(db, tx.goalId, tx.amount, now)
        }
    }

    private fun revertDebtSync(db: SQLiteDatabase, debtId: String, amount: Long, now: Long) {
        val cursor = db.query(
            DatabaseHelper.TABLE_DEBTS,
            arrayOf("total_amount", "paid_amount"),
            "id = ?", arrayOf(debtId),
            null, null, null
        )
        cursor.use {
            if (it.moveToNext()) {
                val totalAmount = it.getLong(0)
                val currentPaid = it.getLong(1)
                val newPaid = (currentPaid - amount).coerceAtLeast(0L)
                val newStatus = if (newPaid >= totalAmount) DebtStatus.PAID.key
                else if (newPaid > 0) DebtStatus.PARTIAL.key
                else DebtStatus.UNPAID.key

                val cv = ContentValues().apply {
                    put("paid_amount", newPaid)
                    put("status", newStatus)
                    put("updated_at", now)
                }
                db.update(DatabaseHelper.TABLE_DEBTS, cv, "id = ?", arrayOf(debtId))
            }
        }
    }

    private fun applyDebtSync(db: SQLiteDatabase, debtId: String, amount: Long, now: Long) {
        val cursor = db.query(
            DatabaseHelper.TABLE_DEBTS,
            arrayOf("total_amount", "paid_amount"),
            "id = ?", arrayOf(debtId),
            null, null, null
        )
        cursor.use {
            if (it.moveToNext()) {
                val totalAmount = it.getLong(0)
                val currentPaid = it.getLong(1)
                val newPaid = (currentPaid + amount).coerceAtMost(totalAmount)
                val newStatus = if (newPaid >= totalAmount) DebtStatus.PAID.key
                else if (newPaid > 0) DebtStatus.PARTIAL.key
                else DebtStatus.UNPAID.key

                val cv = ContentValues().apply {
                    put("paid_amount", newPaid)
                    put("status", newStatus)
                    put("updated_at", now)
                }
                db.update(DatabaseHelper.TABLE_DEBTS, cv, "id = ?", arrayOf(debtId))
            }
        }
    }

    private fun revertGoalSync(db: SQLiteDatabase, goalId: String, amount: Long, now: Long) {
        val cursor = db.query(
            DatabaseHelper.TABLE_GOALS,
            arrayOf("current_amount"),
            "id = ?", arrayOf(goalId),
            null, null, null
        )
        cursor.use {
            if (it.moveToNext()) {
                val currentAmount = it.getLong(0)
                val newAmount = (currentAmount - amount).coerceAtLeast(0L)
                val cv = ContentValues().apply {
                    put("current_amount", newAmount)
                    put("updated_at", now)
                }
                db.update(DatabaseHelper.TABLE_GOALS, cv, "id = ?", arrayOf(goalId))
            }
        }
    }

    private fun applyGoalSync(db: SQLiteDatabase, goalId: String, amount: Long, now: Long) {
        val cursor = db.query(
            DatabaseHelper.TABLE_GOALS,
            arrayOf("current_amount"),
            "id = ?", arrayOf(goalId),
            null, null, null
        )
        cursor.use {
            if (it.moveToNext()) {
                val currentAmount = it.getLong(0)
                val newAmount = currentAmount + amount
                val cv = ContentValues().apply {
                    put("current_amount", newAmount)
                    put("updated_at", now)
                }
                db.update(DatabaseHelper.TABLE_GOALS, cv, "id = ?", arrayOf(goalId))
            }
        }
    }
}
