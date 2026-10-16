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
    val details: List<BalanceDiscrepancy>,
    val allItems: List<BalanceDiscrepancy> = emptyList()
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
            TransactionType.LOAN_RECEIVED.value, TransactionType.LOAN_GIVEN.value,
            TransactionType.DEBT_PAYMENT.value, TransactionType.RECEIVABLE_PAYMENT.value -> {
                if (tx.debtId.isNullOrBlank()) return Result.failure(IllegalArgumentException("Catatan hutang/piutang wajib dipilih."))
            }
            TransactionType.ALLOCATION.value, TransactionType.RELEASE.value -> {
                if (tx.goalId.isNullOrBlank()) {
                    return Result.failure(IllegalArgumentException("Target tabungan wajib dipilih."))
                }
            }
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
                    TransactionType.INCOME.value, TransactionType.LOAN_RECEIVED.value, TransactionType.RECEIVABLE_PAYMENT.value -> balance += tx.amount
                    TransactionType.EXPENSE.value, TransactionType.LOAN_GIVEN.value, TransactionType.DEBT_PAYMENT.value -> balance -= tx.amount
                    TransactionType.TRANSFER.value -> balance -= tx.amount
                    TransactionType.ADJUSTMENT.value -> {
                        if (tx.adjustmentDirection == -1) {
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

    /** Old goal entries were classified as spending; retain their history as internal allocations. */
    fun migrateLegacyGoalTransactions(db: SQLiteDatabase) {
        db.execSQL("""
            UPDATE ${DatabaseHelper.TABLE_ASSETS}
            SET current_balance = current_balance + COALESCE((
                SELECT SUM(CASE WHEN type = 'expense' THEN amount ELSE -amount END)
                FROM ${DatabaseHelper.TABLE_TRANSACTIONS}
                WHERE asset_id = ${DatabaseHelper.TABLE_ASSETS}.id
                  AND goal_id IS NOT NULL AND goal_id != '' AND type IN ('expense', 'income')
            ), 0)
        """.trimIndent())
        db.execSQL("""
            UPDATE ${DatabaseHelper.TABLE_TRANSACTIONS}
            SET type = CASE WHEN type = 'expense' THEN 'allocation' ELSE 'release' END
            WHERE goal_id IS NOT NULL AND goal_id != '' AND type IN ('expense', 'income')
        """.trimIndent())
    }

    fun migrateLegacyDebtTransactions(db: SQLiteDatabase) {
        db.execSQL("UPDATE ${DatabaseHelper.TABLE_TRANSACTIONS} SET type = CASE WHEN type = 'income' THEN 'loan_received' ELSE 'debt_payment' END WHERE debt_id IS NOT NULL AND debt_id != '' AND type IN ('income', 'expense')")
    }

    /**
     * Menjalankan penghitungan ulang saldo seluruh aset dari seluruh riwayat transaksi di SQLite secara atomik.
     * Menggunakan query agregat (GROUP BY asset_id) untuk efisiensi tinggi (menghindari O(N) query terpisah).
     *
     * @param autoFix jika true, langsung memperbarui current_balance di database SQLite dalam transaksi.
     *                jika false (misal saat init), hanya mendeteksi selisih tanpa mengubah database secara diam-diam.
     */
    fun recalculateAllBalances(db: SQLiteDatabase, autoFix: Boolean = true): LedgerRecalculateResult {
        val discrepancies = mutableListOf<BalanceDiscrepancy>()
        var totalAssets = 0

        val wasInTransaction = db.inTransaction()
        if (!wasInTransaction) {
            db.beginTransaction()
        }

        try {
            // Query 1 Agregat: transaksi per asset_id, type, dan adjustment_direction untuk SEMUA aset sekaligus
            val txQuery = """
                SELECT asset_id, type, adjustment_direction, COALESCE(SUM(amount), 0)
                FROM ${DatabaseHelper.TABLE_TRANSACTIONS}
                WHERE asset_id IS NOT NULL AND asset_id != ''
                GROUP BY asset_id, type, adjustment_direction
            """.trimIndent()

            val incomeMap = mutableMapOf<String, Long>()
            val expenseMap = mutableMapOf<String, Long>()
            val transferOutMap = mutableMapOf<String, Long>()
            val adjInMap = mutableMapOf<String, Long>()
            val adjOutMap = mutableMapOf<String, Long>()

            val txCursor = db.rawQuery(txQuery, null)
            txCursor.use { cursor ->
                while (cursor.moveToNext()) {
                    val assetId = cursor.getString(0)
                    val type = cursor.getString(1)
                    val adjDir = cursor.getInt(2)
                    val totalAmount = cursor.getLong(3)

                    when (type) {
                        TransactionType.INCOME.value, TransactionType.LOAN_RECEIVED.value, TransactionType.RECEIVABLE_PAYMENT.value -> {
                            incomeMap[assetId] = (incomeMap[assetId] ?: 0L) + totalAmount
                        }
                        TransactionType.EXPENSE.value, TransactionType.LOAN_GIVEN.value, TransactionType.DEBT_PAYMENT.value -> {
                            expenseMap[assetId] = (expenseMap[assetId] ?: 0L) + totalAmount
                        }
                        TransactionType.TRANSFER.value -> {
                            transferOutMap[assetId] = (transferOutMap[assetId] ?: 0L) + totalAmount
                        }
                        TransactionType.ADJUSTMENT.value -> {
                            if (adjDir == -1) {
                                adjOutMap[assetId] = (adjOutMap[assetId] ?: 0L) + totalAmount
                            } else {
                                adjInMap[assetId] = (adjInMap[assetId] ?: 0L) + totalAmount
                            }
                        }
                    }
                }
            }

            // Query 2 Agregat: transfer masuk per destination_asset_id untuk SEMUA aset sekaligus
            val transferInQuery = """
                SELECT destination_asset_id, COALESCE(SUM(amount), 0)
                FROM ${DatabaseHelper.TABLE_TRANSACTIONS}
                WHERE type = '${TransactionType.TRANSFER.value}'
                  AND destination_asset_id IS NOT NULL
                  AND destination_asset_id != ''
                GROUP BY destination_asset_id
            """.trimIndent()

            val transferInMap = mutableMapOf<String, Long>()
            val transferInCursor = db.rawQuery(transferInQuery, null)
            transferInCursor.use { cursor ->
                while (cursor.moveToNext()) {
                    val destAssetId = cursor.getString(0)
                    val totalAmount = cursor.getLong(1)
                    transferInMap[destAssetId] = (transferInMap[destAssetId] ?: 0L) + totalAmount
                }
            }

            // Query 3: Ambil daftar seluruh aset
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
            val now = System.currentTimeMillis()
            val allItems = mutableListOf<BalanceDiscrepancy>()

            for ((assetId, assetName, balances) in assetsToProcess) {
                val (openingBalance, recordedBalance) = balances
                val totalIncome = incomeMap[assetId] ?: 0L
                val totalExpense = expenseMap[assetId] ?: 0L
                val totalTransferOut = transferOutMap[assetId] ?: 0L
                val totalTransferIn = transferInMap[assetId] ?: 0L
                val totalAdjIn = adjInMap[assetId] ?: 0L
                val totalAdjOut = adjOutMap[assetId] ?: 0L

                val calculatedBalance = openingBalance + totalIncome - totalExpense - totalTransferOut + totalTransferIn + totalAdjIn - totalAdjOut
                val diff = calculatedBalance - recordedBalance

                val item = BalanceDiscrepancy(
                    assetId = assetId,
                    assetName = assetName,
                    recordedBalance = recordedBalance,
                    calculatedBalance = calculatedBalance,
                    difference = diff
                )
                allItems.add(item)

                if (recordedBalance != calculatedBalance) {
                    discrepancies.add(item)

                    if (autoFix) {
                        val cv = ContentValues().apply {
                            put("current_balance", calculatedBalance)
                            put("updated_at", now)
                        }
                        db.update(DatabaseHelper.TABLE_ASSETS, cv, "id = ?", arrayOf(assetId))
                    }
                }
            }

            if (!wasInTransaction) {
                db.setTransactionSuccessful()
            }

            return LedgerRecalculateResult(
                totalAssetsChecked = totalAssets,
                discrepanciesFound = discrepancies.size,
                details = discrepancies,
                allItems = allItems
            )
        } finally {
            if (!wasInTransaction) {
                db.endTransaction()
            }
        }
    }

    /**
     * Membalik efek transaksi pada aset (Revert balance effect).
     */
    fun revertTransactionEffect(db: SQLiteDatabase, tx: Transaction, now: Long = System.currentTimeMillis()) {
        when (tx.type) {
            TransactionType.INCOME.value, TransactionType.LOAN_RECEIVED.value, TransactionType.RECEIVABLE_PAYMENT.value -> {
                db.execSQL(
                    "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance - ?, updated_at = ? WHERE id = ?",
                    arrayOf<Any>(tx.amount, now, tx.assetId)
                )
            }
            TransactionType.EXPENSE.value, TransactionType.LOAN_GIVEN.value, TransactionType.DEBT_PAYMENT.value -> {
                db.execSQL(
                    "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance + ?, updated_at = ? WHERE id = ?",
                    arrayOf<Any>(tx.amount, now, tx.assetId)
                )
            }
            TransactionType.TRANSFER.value -> {
                db.execSQL(
                    "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance + ?, updated_at = ? WHERE id = ?",
                    arrayOf<Any>(tx.amount, now, tx.assetId)
                )
                if (!tx.destinationAssetId.isNullOrBlank()) {
                    db.execSQL(
                        "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance - ?, updated_at = ? WHERE id = ?",
                        arrayOf<Any>(tx.amount, now, tx.destinationAssetId)
                    )
                }
            }
            TransactionType.ADJUSTMENT.value -> {
                val isNegative = tx.adjustmentDirection == -1
                val sql = if (isNegative) {
                    "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance + ?, updated_at = ? WHERE id = ?"
                } else {
                    "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance - ?, updated_at = ? WHERE id = ?"
                }
                db.execSQL(sql, arrayOf<Any>(tx.amount, now, tx.assetId))
            }
        }

        // Sinkronisasi hutang terkait jika ada (hanya untuk transaksi cicilan/pelunasan expense)
        if (!tx.debtId.isNullOrBlank() && tx.type in listOf(TransactionType.EXPENSE.value, TransactionType.DEBT_PAYMENT.value, TransactionType.RECEIVABLE_PAYMENT.value)) {
            revertDebtPaymentSync(db, tx.debtId, tx.amount, now)
        }

        // Sinkronisasi target keuangan terkait jika ada
        if (!tx.goalId.isNullOrBlank()) {
            if (tx.type == TransactionType.EXPENSE.value || tx.type == TransactionType.ALLOCATION.value) {
                // Membatalkan alokasi tabungan: kurangi saldo target
                revertGoalDepositSync(db, tx.goalId, tx.amount, now)
            } else if (tx.type == TransactionType.INCOME.value || tx.type == TransactionType.RELEASE.value) {
                // Membatalkan penarikan/pencairan: kembalikan saldo ke target
                revertGoalWithdrawSync(db, tx.goalId, tx.amount, now)
            }
        }
    }

    /**
     * Menerapkan efek transaksi pada saldo aset (Apply balance effect).
     */
    fun applyTransactionEffect(db: SQLiteDatabase, tx: Transaction, now: Long = System.currentTimeMillis()) {
        validateTransaction(tx).getOrThrow()
        val accountIds = if (tx.type == TransactionType.TRANSFER.value) listOf(tx.assetId, tx.destinationAssetId.orEmpty()) else listOf(tx.assetId)
        for (id in accountIds) {
            db.rawQuery("SELECT 1 FROM ${DatabaseHelper.TABLE_ASSETS} WHERE id = ?", arrayOf(id)).use {
                require(it.moveToFirst()) { "Rekening transaksi tidak ditemukan." }
            }
        }
        when (tx.type) {
            TransactionType.INCOME.value, TransactionType.LOAN_RECEIVED.value, TransactionType.RECEIVABLE_PAYMENT.value -> {
                db.execSQL(
                    "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance + ?, updated_at = ? WHERE id = ?",
                    arrayOf<Any>(tx.amount, now, tx.assetId)
                )
            }
            TransactionType.EXPENSE.value, TransactionType.LOAN_GIVEN.value, TransactionType.DEBT_PAYMENT.value -> {
                db.execSQL(
                    "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance - ?, updated_at = ? WHERE id = ?",
                    arrayOf<Any>(tx.amount, now, tx.assetId)
                )
            }
            TransactionType.TRANSFER.value -> {
                db.execSQL(
                    "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance - ?, updated_at = ? WHERE id = ?",
                    arrayOf<Any>(tx.amount, now, tx.assetId)
                )
                db.execSQL(
                    "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance + ?, updated_at = ? WHERE id = ?",
                    arrayOf<Any>(tx.amount, now, tx.destinationAssetId ?: "")
                )
            }
            TransactionType.ADJUSTMENT.value -> {
                val isNegative = tx.adjustmentDirection == -1
                val sql = if (isNegative) {
                    "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance - ?, updated_at = ? WHERE id = ?"
                } else {
                    "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance + ?, updated_at = ? WHERE id = ?"
                }
                db.execSQL(sql, arrayOf<Any>(tx.amount, now, tx.assetId))
            }
        }

        // Sinkronisasi hutang terkait jika ada (hanya untuk transaksi cicilan/pelunasan expense)
        if (!tx.debtId.isNullOrBlank() && tx.type in listOf(TransactionType.EXPENSE.value, TransactionType.DEBT_PAYMENT.value, TransactionType.RECEIVABLE_PAYMENT.value)) {
            applyDebtPaymentSync(db, tx.debtId, tx.amount, now)
        }

        // Sinkronisasi target keuangan terkait jika ada
        if (!tx.goalId.isNullOrBlank()) {
            if (tx.type == TransactionType.EXPENSE.value || tx.type == TransactionType.ALLOCATION.value) {
                // Alokasi tabungan: tambah saldo target
                applyGoalDepositSync(db, tx.goalId, tx.amount, now)
            } else if (tx.type == TransactionType.INCOME.value || tx.type == TransactionType.RELEASE.value) {
                // Pencairan dari target: kurangi saldo target
                applyGoalWithdrawSync(db, tx.goalId, tx.amount, now)
            }
        }
    }

    private fun revertDebtPaymentSync(db: SQLiteDatabase, debtId: String, amount: Long, now: Long) {
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

    private fun applyDebtPaymentSync(db: SQLiteDatabase, debtId: String, amount: Long, now: Long) {
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
                require(amount <= totalAmount - currentPaid) { "Nominal melebihi sisa hutang/piutang." }
                val newPaid = currentPaid + amount
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

    private fun revertGoalDepositSync(db: SQLiteDatabase, goalId: String, amount: Long, now: Long) {
        val cursor = db.query(
            DatabaseHelper.TABLE_GOALS,
            arrayOf("current_amount"),
            "id = ?", arrayOf(goalId),
            null, null, null
        )
        cursor.use {
            if (it.moveToNext()) {
                val currentAmount = it.getLong(0)
                require(amount <= currentAmount) { "Alokasi sudah digunakan atau dilepas. Batalkan pelepasan terlebih dahulu." }
                val newAmount = currentAmount - amount
                val cv = ContentValues().apply {
                    put("current_amount", newAmount)
                    put("updated_at", now)
                }
                db.update(DatabaseHelper.TABLE_GOALS, cv, "id = ?", arrayOf(goalId))
            }
        }
    }

    private fun applyGoalDepositSync(db: SQLiteDatabase, goalId: String, amount: Long, now: Long) {
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

    private fun revertGoalWithdrawSync(db: SQLiteDatabase, goalId: String, amount: Long, now: Long) {
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

    private fun applyGoalWithdrawSync(db: SQLiteDatabase, goalId: String, amount: Long, now: Long) {
        val cursor = db.query(
            DatabaseHelper.TABLE_GOALS,
            arrayOf("current_amount"),
            "id = ?", arrayOf(goalId),
            null, null, null
        )
        cursor.use {
            if (it.moveToNext()) {
                val currentAmount = it.getLong(0)
                require(amount <= currentAmount) { "Nominal melebihi dana yang dialokasikan." }
                val newAmount = currentAmount - amount
                val cv = ContentValues().apply {
                    put("current_amount", newAmount)
                    put("updated_at", now)
                }
                db.update(DatabaseHelper.TABLE_GOALS, cv, "id = ?", arrayOf(goalId))
            }
        }
    }
}
