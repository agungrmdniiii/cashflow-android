package com.cashflow.app.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.cashflow.app.data.db.DatabaseHelper
import com.cashflow.app.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class CashflowRepository(private val context: Context) {

    private val dbHelper = DatabaseHelper(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _assets = MutableStateFlow<List<Asset>>(emptyList())
    val assets: StateFlow<List<Asset>> = _assets.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private val _budgets = MutableStateFlow<List<BudgetWithProgress>>(emptyList())
    val budgets: StateFlow<List<BudgetWithProgress>> = _budgets.asStateFlow()

    private val _goals = MutableStateFlow<List<FinancialGoal>>(emptyList())
    val goals: StateFlow<List<FinancialGoal>> = _goals.asStateFlow()

    private val _debts = MutableStateFlow<List<Debt>>(emptyList())
    val debts: StateFlow<List<Debt>> = _debts.asStateFlow()

    init {
        refreshAll()
    }

    fun refreshAll() {
        scope.launch {
            loadFromDb()
        }
    }

    private suspend fun loadFromDb() = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase

        // 1. Load Assets
        val assetList = mutableListOf<Asset>()
        val assetCursor = db.query(
            DatabaseHelper.TABLE_ASSETS,
            null, null, null, null, null,
            "is_default DESC, name ASC"
        )
        assetCursor.use { cursor ->
            while (cursor.moveToNext()) {
                assetList.add(cursorToAsset(cursor))
            }
        }
        _assets.value = assetList

        // 2. Load Categories
        val categoryList = mutableListOf<Category>()
        val catCursor = db.query(
            DatabaseHelper.TABLE_CATEGORIES,
            null, "is_active = 1", null, null, null,
            "name ASC"
        )
        catCursor.use { cursor ->
            while (cursor.moveToNext()) {
                categoryList.add(cursorToCategory(cursor))
            }
        }
        _categories.value = categoryList

        // 3. Load Transactions (sorted newest first)
        val txList = mutableListOf<Transaction>()
        val txCursor = db.query(
            DatabaseHelper.TABLE_TRANSACTIONS,
            null, null, null, null, null,
            "transaction_date DESC, transaction_time DESC, created_at DESC"
        )
        txCursor.use { cursor ->
            while (cursor.moveToNext()) {
                txList.add(cursorToTransaction(cursor))
            }
        }
        _transactions.value = txList

        // 4. Load Budgets with calculated spending
        val budgetList = mutableListOf<Budget>()
        val bCursor = db.query(DatabaseHelper.TABLE_BUDGETS, null, null, null, null, null, null)
        bCursor.use { cursor ->
            while (cursor.moveToNext()) {
                budgetList.add(cursorToBudget(cursor))
            }
        }

        // Calculate actual spending for current month for each budget (O(T + B) linear pass instead of O(B * T))
        val currentMonthPrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
        val catMap = categoryList.associateBy { it.id }
        val spentByCategory = mutableMapOf<String, Long>()
        for (tx in txList) {
            if (tx.type == TransactionType.EXPENSE.value &&
                tx.categoryId != null &&
                tx.transactionDate.startsWith(currentMonthPrefix)
            ) {
                spentByCategory[tx.categoryId] = (spentByCategory[tx.categoryId] ?: 0L) + tx.amount
            }
        }

        val budgetWithProgressList = budgetList.map { budget ->
            val cat = catMap[budget.categoryId]
            val spent = spentByCategory[budget.categoryId] ?: 0L

            val remaining = (budget.targetAmount - spent).coerceAtLeast(0L)
            val percentage = if (budget.targetAmount > 0) {
                (spent.toFloat() / budget.targetAmount.toFloat()).coerceIn(0f, 2f)
            } else 0f

            val status = when {
                spent > budget.targetAmount -> BudgetStatus.EXCEEDED
                percentage >= 0.85f -> BudgetStatus.NEAR_LIMIT
                percentage >= 0.60f -> BudgetStatus.WARNING
                else -> BudgetStatus.SAFE
            }

            BudgetWithProgress(
                budget = budget,
                category = cat,
                spentAmount = spent,
                remainingAmount = remaining,
                progressPercentage = percentage,
                status = status
            )
        }
        _budgets.value = budgetWithProgressList

        // 5. Load Financial Goals
        val goalList = mutableListOf<FinancialGoal>()
        val gCursor = db.query(DatabaseHelper.TABLE_GOALS, null, null, null, null, null, "deadline ASC")
        gCursor.use { cursor ->
            while (cursor.moveToNext()) {
                goalList.add(cursorToGoal(cursor))
            }
        }
        _goals.value = goalList

        // 6. Load Debts
        val debtList = mutableListOf<Debt>()
        val dCursor = db.query(
            DatabaseHelper.TABLE_DEBTS,
            null, null, null, null, null,
            "CASE WHEN status = 'paid' THEN 1 ELSE 0 END ASC, due_date ASC, created_at DESC"
        )
        dCursor.use { cursor ->
            while (cursor.moveToNext()) {
                debtList.add(cursorToDebt(cursor))
            }
        }
        _debts.value = debtList

        try {
            com.cashflow.app.widget.DuitAingQuickWidget.updateAllWidgets(context)
        } catch (_: Exception) {
        }
    }

    // --- TRANSACTION OPERATIONS (WITH ATOMIC BALANCE UPDATE & VALIDATION) ---

    suspend fun addTransaction(transaction: Transaction): Result<Unit> = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            // Validation
            if (transaction.amount <= 0) {
                return@withContext Result.failure(IllegalArgumentException("Nominal transaksi harus lebih dari 0"))
            }

            when (transaction.type) {
                TransactionType.INCOME.value -> {
                    if (transaction.assetId.isBlank()) {
                        return@withContext Result.failure(IllegalArgumentException("Aset tujuan pemasukan wajib dipilih"))
                    }
                    // Update asset balance: balance = balance + amount
                    db.execSQL(
                        "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance + ?, updated_at = ? WHERE id = ?",
                        arrayOf(transaction.amount, System.currentTimeMillis(), transaction.assetId)
                    )
                }
                TransactionType.EXPENSE.value -> {
                    if (transaction.assetId.isBlank()) {
                        return@withContext Result.failure(IllegalArgumentException("Aset sumber pengeluaran wajib dipilih"))
                    }
                    // Update asset balance: balance = balance - amount
                    db.execSQL(
                        "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance - ?, updated_at = ? WHERE id = ?",
                        arrayOf(transaction.amount, System.currentTimeMillis(), transaction.assetId)
                    )
                }
                TransactionType.TRANSFER.value -> {
                    if (transaction.assetId.isBlank()) {
                        return@withContext Result.failure(IllegalArgumentException("Aset sumber transfer wajib dipilih"))
                    }
                    if (transaction.destinationAssetId.isNullOrBlank()) {
                        return@withContext Result.failure(IllegalArgumentException("Aset tujuan transfer wajib dipilih"))
                    }
                    if (transaction.assetId == transaction.destinationAssetId) {
                        return@withContext Result.failure(IllegalArgumentException("Aset sumber dan aset tujuan tidak boleh sama"))
                    }
                    // Deduct from source
                    db.execSQL(
                        "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance - ?, updated_at = ? WHERE id = ?",
                        arrayOf(transaction.amount, System.currentTimeMillis(), transaction.assetId)
                    )
                    // Add to destination
                    db.execSQL(
                        "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance + ?, updated_at = ? WHERE id = ?",
                        arrayOf(transaction.amount, System.currentTimeMillis(), transaction.destinationAssetId)
                    )
                }
                else -> return@withContext Result.failure(IllegalArgumentException("Jenis transaksi tidak valid"))
            }

            // Insert transaction
            val cv = ContentValues().apply {
                put("id", transaction.id.ifBlank { UUID.randomUUID().toString() })
                put("type", transaction.type)
                put("amount", transaction.amount)
                put("description", transaction.description.ifBlank { "Transaksi ${transaction.type}" })
                put("category_id", transaction.categoryId)
                put("asset_id", transaction.assetId)
                put("destination_asset_id", transaction.destinationAssetId)
                put("transaction_date", transaction.transactionDate)
                put("transaction_time", transaction.transactionTime)
                put("note", transaction.note)
                put("attachment", transaction.attachment)
                put("input_method", transaction.inputMethod)
                put("created_at", transaction.createdAt)
                put("updated_at", transaction.updatedAt)
            }
            db.insertOrThrow(DatabaseHelper.TABLE_TRANSACTIONS, null, cv)

            db.setTransactionSuccessful()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            db.endTransaction()
            loadFromDb()
        }
    }

    suspend fun updateTransaction(newTx: Transaction): Result<Unit> = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            // Find existing transaction to revert
            val oldTxCursor = db.query(
                DatabaseHelper.TABLE_TRANSACTIONS,
                null, "id = ?", arrayOf(newTx.id),
                null, null, null
            )
            val oldTx = oldTxCursor.use {
                if (it.moveToNext()) cursorToTransaction(it) else null
            } ?: return@withContext Result.failure(IllegalStateException("Transaksi lama tidak ditemukan"))

            // 1. Revert effect of old transaction
            when (oldTx.type) {
                TransactionType.INCOME.value -> {
                    db.execSQL(
                        "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance - ?, updated_at = ? WHERE id = ?",
                        arrayOf(oldTx.amount, System.currentTimeMillis(), oldTx.assetId)
                    )
                }
                TransactionType.EXPENSE.value -> {
                    db.execSQL(
                        "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance + ?, updated_at = ? WHERE id = ?",
                        arrayOf(oldTx.amount, System.currentTimeMillis(), oldTx.assetId)
                    )
                }
                TransactionType.TRANSFER.value -> {
                    db.execSQL(
                        "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance + ?, updated_at = ? WHERE id = ?",
                        arrayOf(oldTx.amount, System.currentTimeMillis(), oldTx.assetId)
                    )
                    if (!oldTx.destinationAssetId.isNullOrBlank()) {
                        db.execSQL(
                            "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance - ?, updated_at = ? WHERE id = ?",
                            arrayOf(oldTx.amount, System.currentTimeMillis(), oldTx.destinationAssetId)
                        )
                    }
                }
            }

            // 2. Apply effect of new transaction
            when (newTx.type) {
                TransactionType.INCOME.value -> {
                    db.execSQL(
                        "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance + ?, updated_at = ? WHERE id = ?",
                        arrayOf(newTx.amount, System.currentTimeMillis(), newTx.assetId)
                    )
                }
                TransactionType.EXPENSE.value -> {
                    db.execSQL(
                        "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance - ?, updated_at = ? WHERE id = ?",
                        arrayOf(newTx.amount, System.currentTimeMillis(), newTx.assetId)
                    )
                }
                TransactionType.TRANSFER.value -> {
                    db.execSQL(
                        "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance - ?, updated_at = ? WHERE id = ?",
                        arrayOf(newTx.amount, System.currentTimeMillis(), newTx.assetId)
                    )
                    db.execSQL(
                        "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance + ?, updated_at = ? WHERE id = ?",
                        arrayOf(newTx.amount, System.currentTimeMillis(), newTx.destinationAssetId)
                    )
                }
            }

            // 3. Update transaction row
            val cv = ContentValues().apply {
                put("type", newTx.type)
                put("amount", newTx.amount)
                put("description", newTx.description)
                put("category_id", newTx.categoryId)
                put("asset_id", newTx.assetId)
                put("destination_asset_id", newTx.destinationAssetId)
                put("transaction_date", newTx.transactionDate)
                put("transaction_time", newTx.transactionTime)
                put("note", newTx.note)
                put("attachment", newTx.attachment)
                put("updated_at", System.currentTimeMillis())
            }
            db.update(DatabaseHelper.TABLE_TRANSACTIONS, cv, "id = ?", arrayOf(newTx.id))

            db.setTransactionSuccessful()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            db.endTransaction()
            loadFromDb()
        }
    }

    suspend fun deleteTransaction(txId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val cursor = db.query(
                DatabaseHelper.TABLE_TRANSACTIONS,
                null, "id = ?", arrayOf(txId),
                null, null, null
            )
            val tx = cursor.use {
                if (it.moveToNext()) cursorToTransaction(it) else null
            } ?: return@withContext Result.failure(IllegalStateException("Transaksi tidak ditemukan"))

            // Revert effect on asset
            when (tx.type) {
                TransactionType.INCOME.value -> {
                    db.execSQL(
                        "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance - ?, updated_at = ? WHERE id = ?",
                        arrayOf(tx.amount, System.currentTimeMillis(), tx.assetId)
                    )
                }
                TransactionType.EXPENSE.value -> {
                    db.execSQL(
                        "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance + ?, updated_at = ? WHERE id = ?",
                        arrayOf(tx.amount, System.currentTimeMillis(), tx.assetId)
                    )
                }
                TransactionType.TRANSFER.value -> {
                    db.execSQL(
                        "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance + ?, updated_at = ? WHERE id = ?",
                        arrayOf(tx.amount, System.currentTimeMillis(), tx.assetId)
                    )
                    if (!tx.destinationAssetId.isNullOrBlank()) {
                        db.execSQL(
                            "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance - ?, updated_at = ? WHERE id = ?",
                            arrayOf(tx.amount, System.currentTimeMillis(), tx.destinationAssetId)
                        )
                    }
                }
            }

            db.delete(DatabaseHelper.TABLE_TRANSACTIONS, "id = ?", arrayOf(txId))
            db.setTransactionSuccessful()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            db.endTransaction()
            loadFromDb()
        }
    }

    // --- ASSET OPERATIONS ---

    suspend fun addAsset(asset: Asset): Result<Unit> = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        try {
            if (asset.isDefault) {
                // Remove default flag from all other assets
                val cvDef = ContentValues().apply { put("is_default", 0) }
                db.update(DatabaseHelper.TABLE_ASSETS, cvDef, null, null)
            }

            val cv = ContentValues().apply {
                put("id", asset.id.ifBlank { UUID.randomUUID().toString() })
                put("name", asset.name)
                put("type", asset.type)
                put("opening_balance", asset.openingBalance)
                put("current_balance", asset.openingBalance) // starts at opening balance
                put("currency", asset.currency)
                put("is_default", if (asset.isDefault) 1 else 0)
                put("is_active", if (asset.isActive) 1 else 0)
                put("note", asset.note)
                put("created_at", asset.createdAt)
                put("updated_at", asset.updatedAt)
            }
            db.insertOrThrow(DatabaseHelper.TABLE_ASSETS, null, cv)
            loadFromDb()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateAsset(asset: Asset): Result<Unit> = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        try {
            if (asset.isDefault) {
                val cvDef = ContentValues().apply { put("is_default", 0) }
                db.update(DatabaseHelper.TABLE_ASSETS, cvDef, null, null)
            }

            val cv = ContentValues().apply {
                put("name", asset.name)
                put("type", asset.type)
                put("is_default", if (asset.isDefault) 1 else 0)
                put("note", asset.note)
                put("updated_at", System.currentTimeMillis())
            }
            db.update(DatabaseHelper.TABLE_ASSETS, cv, "id = ?", arrayOf(asset.id))
            loadFromDb()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setDefaultAsset(assetId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val cvDef = ContentValues().apply { put("is_default", 0) }
            db.update(DatabaseHelper.TABLE_ASSETS, cvDef, null, null)

            val cvSet = ContentValues().apply { put("is_default", 1) }
            db.update(DatabaseHelper.TABLE_ASSETS, cvSet, "id = ?", arrayOf(assetId))

            db.setTransactionSuccessful()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            db.endTransaction()
            loadFromDb()
        }
    }

    suspend fun archiveAsset(assetId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        try {
            val cv = ContentValues().apply {
                put("is_active", 0)
                put("is_default", 0)
                put("updated_at", System.currentTimeMillis())
            }
            db.update(DatabaseHelper.TABLE_ASSETS, cv, "id = ?", arrayOf(assetId))
            loadFromDb()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAsset(assetId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        try {
            // Check if there are transactions associated with this asset
            val cursor = db.rawQuery(
                "SELECT COUNT(*) FROM ${DatabaseHelper.TABLE_TRANSACTIONS} WHERE asset_id = ? OR destination_asset_id = ?",
                arrayOf(assetId, assetId)
            )
            val txCount = cursor.use { if (it.moveToNext()) it.getInt(0) else 0 }

            if (txCount > 0) {
                // Must archive instead of hard delete to preserve historical integrity!
                archiveAsset(assetId)
                return@withContext Result.success(Unit)
            } else {
                db.delete(DatabaseHelper.TABLE_ASSETS, "id = ?", arrayOf(assetId))
                loadFromDb()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- CATEGORY OPERATIONS ---

    suspend fun addCategory(category: Category): Result<Unit> = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        try {
            val cv = ContentValues().apply {
                put("id", category.id.ifBlank { UUID.randomUUID().toString() })
                put("name", category.name)
                put("type", category.type)
                put("icon", category.icon)
                put("color", category.color)
                put("is_default", if (category.isDefault) 1 else 0)
                put("is_active", if (category.isActive) 1 else 0)
            }
            db.insertOrThrow(DatabaseHelper.TABLE_CATEGORIES, null, cv)
            loadFromDb()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- BUDGET OPERATIONS ---

    suspend fun addBudget(budget: Budget): Result<Unit> = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        try {
            val cv = ContentValues().apply {
                put("id", budget.id.ifBlank { UUID.randomUUID().toString() })
                put("category_id", budget.categoryId)
                put("period_type", budget.periodType)
                put("target_amount", budget.targetAmount)
                put("created_at", budget.createdAt)
            }
            db.insertWithOnConflict(DatabaseHelper.TABLE_BUDGETS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            loadFromDb()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateBudget(budget: Budget): Result<Unit> = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        try {
            val cv = ContentValues().apply {
                put("category_id", budget.categoryId)
                put("period_type", budget.periodType)
                put("target_amount", budget.targetAmount)
            }
            val rows = db.update(DatabaseHelper.TABLE_BUDGETS, cv, "id = ?", arrayOf(budget.id))
            if (rows == 0) {
                cv.put("id", budget.id.ifBlank { UUID.randomUUID().toString() })
                cv.put("created_at", budget.createdAt)
                db.insertWithOnConflict(DatabaseHelper.TABLE_BUDGETS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            loadFromDb()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteBudget(budgetId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        try {
            db.delete(DatabaseHelper.TABLE_BUDGETS, "id = ?", arrayOf(budgetId))
            loadFromDb()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- FINANCIAL GOAL OPERATIONS ---

    suspend fun addGoal(goal: FinancialGoal): Result<Unit> = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        try {
            val cv = ContentValues().apply {
                put("id", goal.id.ifBlank { UUID.randomUUID().toString() })
                put("name", goal.name)
                put("target_amount", goal.targetAmount)
                put("current_amount", goal.currentAmount)
                put("deadline", goal.deadline)
                put("asset_id", goal.assetId)
                put("note", goal.note)
                put("created_at", goal.createdAt)
                put("updated_at", goal.updatedAt)
            }
            db.insertOrThrow(DatabaseHelper.TABLE_GOALS, null, cv)
            loadFromDb()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateGoal(goal: FinancialGoal): Result<Unit> = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        try {
            val cv = ContentValues().apply {
                put("name", goal.name)
                put("target_amount", goal.targetAmount)
                put("current_amount", goal.currentAmount)
                put("deadline", goal.deadline)
                put("asset_id", goal.assetId)
                put("note", goal.note)
                put("updated_at", System.currentTimeMillis())
            }
            db.update(DatabaseHelper.TABLE_GOALS, cv, "id = ?", arrayOf(goal.id))
            loadFromDb()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteGoal(goalId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        try {
            db.delete(DatabaseHelper.TABLE_GOALS, "id = ?", arrayOf(goalId))
            loadFromDb()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- DEBT OPERATIONS (STANDALONE TRACKER) ---

    suspend fun addDebt(
        title: String,
        lenderName: String,
        totalAmount: Long,
        dueDate: String,
        note: String = "",
        receiveAssetId: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            if (totalAmount <= 0) {
                return@withContext Result.failure(IllegalArgumentException("Nominal pinjaman harus lebih dari 0"))
            }
            if (title.isBlank() || lenderName.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Nama pinjaman dan pemberi pinjaman wajib diisi"))
            }

            val now = System.currentTimeMillis()
            val debtId = UUID.randomUUID().toString()
            val debtCv = ContentValues().apply {
                put("id", debtId)
                put("title", title.trim())
                put("lender_name", lenderName.trim())
                put("total_amount", totalAmount)
                put("paid_amount", 0L)
                put("due_date", dueDate.trim())
                put("status", DebtStatus.UNPAID.key)
                put("note", note.trim())
                put("created_at", now)
                put("updated_at", now)
            }
            db.insertOrThrow(DatabaseHelper.TABLE_DEBTS, null, debtCv)

            // If user selected an asset to receive borrowed funds, increment asset balance & record INCOME transaction
            if (!receiveAssetId.isNullOrBlank()) {
                db.execSQL(
                    "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance + ?, updated_at = ? WHERE id = ?",
                    arrayOf(totalAmount, now, receiveAssetId)
                )

                val txId = "tx_${UUID.randomUUID()}"
                val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val timeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                val txCv = ContentValues().apply {
                    put("id", txId)
                    put("type", TransactionType.INCOME.value)
                    put("amount", totalAmount)
                    put("description", "Pinjaman Diterima: ${title.trim()} (${lenderName.trim()})")
                    put("category_id", "cat_lainnya_inc")
                    put("asset_id", receiveAssetId)
                    put("destination_asset_id", null as String?)
                    put("transaction_date", todayStr)
                    put("transaction_time", timeStr)
                    put("note", if (note.isNotBlank()) note.trim() else "Penerimaan dana pinjaman dari ${lenderName.trim()}")
                    put("attachment", null as String?)
                    put("input_method", "manual")
                    put("created_at", now)
                    put("updated_at", now)
                }
                db.insertOrThrow(DatabaseHelper.TABLE_TRANSACTIONS, null, txCv)
            }

            db.setTransactionSuccessful()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            db.endTransaction()
            loadFromDb()
        }
    }

    suspend fun recordDebtPayment(
        debtId: String,
        paymentAmount: Long,
        sourceAssetId: String,
        paymentDate: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
        note: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            if (paymentAmount <= 0) {
                return@withContext Result.failure(IllegalArgumentException("Nominal pembayaran harus lebih dari 0"))
            }
            if (sourceAssetId.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Pilih aset sumber pembayaran cicilan"))
            }

            // 1. Fetch Debt
            val cursor = db.query(
                DatabaseHelper.TABLE_DEBTS,
                null, "id = ?", arrayOf(debtId),
                null, null, null
            )
            val existingDebt = cursor.use {
                if (it.moveToNext()) cursorToDebt(it) else null
            } ?: return@withContext Result.failure(IllegalStateException("Data hutang tidak ditemukan"))

            // 2. Fetch Asset
            val assetCursor = db.query(
                DatabaseHelper.TABLE_ASSETS,
                null, "id = ?", arrayOf(sourceAssetId),
                null, null, null
            )
            val existingAsset = assetCursor.use {
                if (it.moveToNext()) cursorToAsset(it) else null
            } ?: return@withContext Result.failure(IllegalStateException("Aset sumber pembayaran tidak ditemukan"))

            val newPaidAmount = (existingDebt.paidAmount + paymentAmount).coerceAtMost(existingDebt.totalAmount)
            val newStatus = if (newPaidAmount >= existingDebt.totalAmount) DebtStatus.PAID.key else DebtStatus.PARTIAL.key
            val now = System.currentTimeMillis()

            // 3. Update Debt
            val debtCv = ContentValues().apply {
                put("paid_amount", newPaidAmount)
                put("status", newStatus)
                put("updated_at", now)
            }
            db.update(DatabaseHelper.TABLE_DEBTS, debtCv, "id = ?", arrayOf(debtId))

            // 4. Deduct Asset Balance
            db.execSQL(
                "UPDATE ${DatabaseHelper.TABLE_ASSETS} SET current_balance = current_balance - ?, updated_at = ? WHERE id = ?",
                arrayOf(paymentAmount, now, sourceAssetId)
            )

            // 5. Insert EXPENSE Transaction
            val txId = "tx_${UUID.randomUUID()}"
            val debtCategory = _categories.value.firstOrNull {
                it.type == "expense" && (it.name.contains("Hutang", ignoreCase = true) || it.name.contains("Tagihan", ignoreCase = true))
            }?.id ?: "cat_tagihan"
            val timeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

            val txCv = ContentValues().apply {
                put("id", txId)
                put("type", TransactionType.EXPENSE.value)
                put("amount", paymentAmount)
                put("description", "Pembayaran Hutang: ${existingDebt.title} (${existingDebt.lenderName})")
                put("category_id", debtCategory)
                put("asset_id", sourceAssetId)
                put("destination_asset_id", null as String?)
                put("transaction_date", paymentDate)
                put("transaction_time", timeStr)
                put("note", if (note.isNotBlank()) note.trim() else "Cicilan/pelunasan pinjaman kepada ${existingDebt.lenderName}")
                put("attachment", null as String?)
                put("input_method", "manual")
                put("created_at", now)
                put("updated_at", now)
            }
            db.insertOrThrow(DatabaseHelper.TABLE_TRANSACTIONS, null, txCv)

            db.setTransactionSuccessful()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            db.endTransaction()
            loadFromDb()
        }
    }

    suspend fun updateDebt(debt: Debt): Result<Unit> = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        try {
            if (debt.totalAmount <= 0) {
                return@withContext Result.failure(IllegalArgumentException("Nominal pinjaman harus lebih dari 0"))
            }
            val boundedPaid = debt.paidAmount.coerceIn(0L, debt.totalAmount)
            val computedStatus = if (boundedPaid >= debt.totalAmount) DebtStatus.PAID.key
            else if (boundedPaid > 0) DebtStatus.PARTIAL.key
            else DebtStatus.UNPAID.key

            val cv = ContentValues().apply {
                put("title", debt.title.trim())
                put("lender_name", debt.lenderName.trim())
                put("total_amount", debt.totalAmount)
                put("paid_amount", boundedPaid)
                put("due_date", debt.dueDate)
                put("status", computedStatus)
                put("note", debt.note.trim())
                put("updated_at", System.currentTimeMillis())
            }
            db.update(DatabaseHelper.TABLE_DEBTS, cv, "id = ?", arrayOf(debt.id))
            loadFromDb()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteDebt(debtId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        try {
            db.delete(DatabaseHelper.TABLE_DEBTS, "id = ?", arrayOf(debtId))
            loadFromDb()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetToDemo(): Result<Unit> = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        try {
            dbHelper.resetToDemo(db)
            loadFromDb()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- CSV EXPORT GENERATOR ---
    suspend fun generateCsvContent(transactionsList: List<Transaction>? = null): String = withContext(Dispatchers.IO) {
        val list = transactionsList ?: _transactions.value
        val sb = StringBuilder()
        // Prepend UTF-8 BOM so Microsoft Excel & spreadsheet applications decode Indonesian characters properly
        sb.append("\uFEFF")
        sb.append("Tanggal,Waktu,Deskripsi,Jenis,Kategori,Aset,Aset Tujuan,Nominal,Catatan,Metode Input\n")

        val catMap = _categories.value.associateBy { it.id }
        val assetMap = _assets.value.associateBy { it.id }

        for (tx in list) {
            val typeName = when (tx.type) {
                TransactionType.INCOME.value -> "Pemasukan"
                TransactionType.EXPENSE.value -> "Pengeluaran"
                TransactionType.TRANSFER.value -> "Transfer"
                else -> tx.type
            }
            val catName = tx.categoryId?.let { catMap[it]?.name } ?: "-"
            val assetName = assetMap[tx.assetId]?.name ?: tx.assetId
            val destAssetName = tx.destinationAssetId?.let { assetMap[it]?.name ?: it } ?: "-"

            sb.append("\"${tx.transactionDate}\",")
            sb.append("\"${tx.transactionTime}\",")
            sb.append("\"${escapeCsv(tx.description)}\",")
            sb.append("\"$typeName\",")
            sb.append("\"$catName\",")
            sb.append("\"$assetName\",")
            sb.append("\"$destAssetName\",")
            sb.append("${tx.amount},")
            sb.append("\"${escapeCsv(tx.note)}\",")
            sb.append("\"${escapeCsv(tx.inputMethod)}\"\n")
        }
        sb.toString()
    }

    private fun escapeCsv(str: String): String {
        return str.replace("\"", "\"\"")
    }

    // --- CURSOR MAPPERS ---

    private fun cursorToAsset(cursor: Cursor): Asset {
        return Asset(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
            type = cursor.getString(cursor.getColumnIndexOrThrow("type")),
            openingBalance = cursor.getLong(cursor.getColumnIndexOrThrow("opening_balance")),
            currentBalance = cursor.getLong(cursor.getColumnIndexOrThrow("current_balance")),
            currency = cursor.getString(cursor.getColumnIndexOrThrow("currency")),
            isDefault = cursor.getInt(cursor.getColumnIndexOrThrow("is_default")) == 1,
            isActive = cursor.getInt(cursor.getColumnIndexOrThrow("is_active")) == 1,
            note = cursor.getString(cursor.getColumnIndexOrThrow("note")) ?: "",
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))
        )
    }

    private fun cursorToCategory(cursor: Cursor): Category {
        return Category(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
            type = cursor.getString(cursor.getColumnIndexOrThrow("type")),
            icon = cursor.getString(cursor.getColumnIndexOrThrow("icon")),
            color = cursor.getString(cursor.getColumnIndexOrThrow("color")),
            isDefault = cursor.getInt(cursor.getColumnIndexOrThrow("is_default")) == 1,
            isActive = cursor.getInt(cursor.getColumnIndexOrThrow("is_active")) == 1
        )
    }

    private fun cursorToTransaction(cursor: Cursor): Transaction {
        return Transaction(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            type = cursor.getString(cursor.getColumnIndexOrThrow("type")),
            amount = cursor.getLong(cursor.getColumnIndexOrThrow("amount")),
            description = cursor.getString(cursor.getColumnIndexOrThrow("description")),
            categoryId = cursor.getString(cursor.getColumnIndexOrThrow("category_id")),
            assetId = cursor.getString(cursor.getColumnIndexOrThrow("asset_id")),
            destinationAssetId = cursor.getString(cursor.getColumnIndexOrThrow("destination_asset_id")),
            transactionDate = cursor.getString(cursor.getColumnIndexOrThrow("transaction_date")),
            transactionTime = cursor.getString(cursor.getColumnIndexOrThrow("transaction_time")),
            note = cursor.getString(cursor.getColumnIndexOrThrow("note")) ?: "",
            attachment = cursor.getString(cursor.getColumnIndexOrThrow("attachment")),
            inputMethod = cursor.getString(cursor.getColumnIndexOrThrow("input_method")),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))
        )
    }

    private fun cursorToBudget(cursor: Cursor): Budget {
        return Budget(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            categoryId = cursor.getString(cursor.getColumnIndexOrThrow("category_id")),
            periodType = cursor.getString(cursor.getColumnIndexOrThrow("period_type")),
            targetAmount = cursor.getLong(cursor.getColumnIndexOrThrow("target_amount")),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
        )
    }

    private fun cursorToGoal(cursor: Cursor): FinancialGoal {
        return FinancialGoal(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
            targetAmount = cursor.getLong(cursor.getColumnIndexOrThrow("target_amount")),
            currentAmount = cursor.getLong(cursor.getColumnIndexOrThrow("current_amount")),
            deadline = cursor.getString(cursor.getColumnIndexOrThrow("deadline")),
            assetId = cursor.getString(cursor.getColumnIndexOrThrow("asset_id")),
            note = cursor.getString(cursor.getColumnIndexOrThrow("note")) ?: "",
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))
        )
    }

    private fun cursorToDebt(cursor: Cursor): Debt {
        return Debt(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            lenderName = cursor.getString(cursor.getColumnIndexOrThrow("lender_name")),
            totalAmount = cursor.getLong(cursor.getColumnIndexOrThrow("total_amount")),
            paidAmount = cursor.getLong(cursor.getColumnIndexOrThrow("paid_amount")),
            dueDate = cursor.getString(cursor.getColumnIndexOrThrow("due_date")),
            status = cursor.getString(cursor.getColumnIndexOrThrow("status")),
            note = cursor.getString(cursor.getColumnIndexOrThrow("note")) ?: "",
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))
        )
    }
}
