package com.cashflow.app.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.cashflow.app.data.backup.BackupManager
import com.cashflow.app.data.backup.BackupSummary
import com.cashflow.app.data.backup.BackupValidationResult
import com.cashflow.app.data.db.DatabaseHelper
import com.cashflow.app.data.diagnostic.DiagnosticLogManager
import com.cashflow.app.data.model.*
import com.cashflow.app.data.preferences.PreferenceManager
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class CashflowRepository(private val context: Context) {

    private val dbHelper = DatabaseHelper(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val balanceWriteMutex = Mutex()

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

    private val _favoriteTemplates = MutableStateFlow<List<FavoriteTemplate>>(emptyList())
    val favoriteTemplates: StateFlow<List<FavoriteTemplate>> = _favoriteTemplates.asStateFlow()

    private val _recurringTransactions = MutableStateFlow<List<RecurringTransaction>>(emptyList())
    val recurringTransactions: StateFlow<List<RecurringTransaction>> = _recurringTransactions.asStateFlow()

    private val _integrityDiscrepancies = MutableStateFlow<List<BalanceDiscrepancy>>(emptyList())
    val integrityDiscrepancies: StateFlow<List<BalanceDiscrepancy>> = _integrityDiscrepancies.asStateFlow()

    private val _integrityReportItems = MutableStateFlow<List<BalanceDiscrepancy>>(emptyList())
    val integrityReportItems: StateFlow<List<BalanceDiscrepancy>> = _integrityReportItems.asStateFlow()

    private val _initError = MutableStateFlow<String?>(null)
    val initError: StateFlow<String?> = _initError.asStateFlow()

    fun clearInitError() {
        _initError.value = null
    }

    init {
        scope.launch {
            balanceWriteMutex.withLock {
                try {
                    val db = dbHelper.writableDatabase
                    val result = LedgerService.recalculateAllBalances(db, autoFix = false)
                    _integrityReportItems.value = result.allItems
                    if (result.discrepanciesFound > 0) {
                        _integrityDiscrepancies.value = result.details
                        DiagnosticLogManager.logDiscrepancy(context, result.details)
                    }
                } catch (e: Exception) {
                    DiagnosticLogManager.logError(context, "INIT_RECALCULATE", e)
                    _initError.value = "Gagal memverifikasi integritas saldo awal: ${e.message ?: "Kesalahan database"}"
                }
            }
            loadFromDb()
        }
    }

    fun refreshAll() {
        scope.launch {
            loadFromDb()
        }
    }

    suspend fun reloadForBackgroundCheck() = balanceWriteMutex.withLock { loadFromDb() }

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

        val prefManager = PreferenceManager(context)
        val budgetWithProgressList = calculateBudgetProgress(budgetList, categoryList, txList,
            prefManager.isBudgetRolloverEnabled(), periodStartDay = prefManager.getBudgetStartDay())
        _budgets.value = budgetWithProgressList

        // Trigger budget notification alerts if threshold (80% or 100%) reached
        try {
            com.cashflow.app.data.service.NotificationHelper.checkAndNotifyBudgets(
                context = context,
                budgets = budgetWithProgressList,
                pref = prefManager
            )
        } catch (_: Exception) {}

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

        // 7. Load Favorite Templates
        val favList = mutableListOf<FavoriteTemplate>()
        try {
            val fCursor = db.query(
                DatabaseHelper.TABLE_FAVORITES,
                null, null, null, null, null,
                "created_at DESC"
            )
            fCursor.use { cursor ->
                while (cursor.moveToNext()) {
                    favList.add(cursorToFavoriteTemplate(cursor))
                }
            }
        } catch (_: Exception) {}
        _favoriteTemplates.value = favList

        // 8. Load Recurring Transactions
        val recList = mutableListOf<RecurringTransaction>()
        try {
            val rCursor = db.query(
                DatabaseHelper.TABLE_RECURRING,
                null, null, null, null, null,
                "created_at DESC"
            )
            rCursor.use { cursor ->
                while (cursor.moveToNext()) {
                    recList.add(cursorToRecurringTransaction(cursor))
                }
            }
        } catch (_: Exception) {}
        _recurringTransactions.value = recList

        try {
            com.cashflow.app.widget.DuitAingQuickWidget.updateAllWidgets(context)
        } catch (_: Exception) {
        }
    }

    // --- TRANSACTION OPERATIONS (WITH ATOMIC BALANCE UPDATE & VALIDATION) ---

    suspend fun addTransaction(transaction: Transaction): Result<Unit> = balanceWriteMutex.withLock {
        withContext(Dispatchers.IO) {
            val validation = LedgerService.validateTransaction(transaction)
            if (validation.isFailure) return@withContext validation

            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                val now = System.currentTimeMillis()
                LedgerService.applyTransactionEffect(db, transaction, now)
                insertTransactionRow(db, transaction, now)

                db.setTransactionSuccessful()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                db.endTransaction()
                loadFromDb()
            }
        }
    }

    private fun insertTransactionRow(db: SQLiteDatabase, transaction: Transaction, now: Long) {
        val cv = ContentValues().apply {
                    put("id", transaction.id.ifBlank { UUID.randomUUID().toString() })
                    put("type", transaction.type)
                    put("amount", transaction.amount)
                    put("description", transaction.description.ifBlank { "Transaksi ${transaction.type}" })
                    put("category_id", if (transaction.type != TransactionType.TRANSFER.value) transaction.categoryId else null)
                    put("asset_id", transaction.assetId)
                    put("destination_asset_id", if (transaction.type == TransactionType.TRANSFER.value) transaction.destinationAssetId else null)
                    put("transaction_date", transaction.transactionDate)
                    put("transaction_time", transaction.transactionTime)
                    put("note", transaction.note)
                    put("attachment", transaction.attachment)
                    put("input_method", transaction.inputMethod)
                    put("debt_id", if (transaction.type == TransactionType.EXPENSE.value) transaction.debtId else null)
                    put("goal_id", transaction.goalId)
                    put("is_sample", if (transaction.isSample) 1 else 0)
                    put("tags", transaction.tags)
                    put("is_adjustment", if (transaction.type == TransactionType.ADJUSTMENT.value) (if (transaction.isAdjustment) 1 else 0) else 0)
                    put("adjustment_direction", if (transaction.type == TransactionType.ADJUSTMENT.value) transaction.adjustmentDirection else 0)
                    put("created_at", transaction.createdAt)
                    put("updated_at", now)
                }
        db.insertOrThrow(DatabaseHelper.TABLE_TRANSACTIONS, null, cv)
    }

    suspend fun updateTransaction(newTx: Transaction): Result<Unit> = balanceWriteMutex.withLock {
        withContext(Dispatchers.IO) {
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

                val now = System.currentTimeMillis()
                if ((!oldTx.debtId.isNullOrBlank() || !oldTx.goalId.isNullOrBlank()) && newTx.type != oldTx.type) {
                    return@withContext Result.failure(IllegalArgumentException("Jenis transaksi cicilan atau alokasi tidak dapat diubah."))
                }
                val linkedTx = newTx.copy(debtId = oldTx.debtId ?: newTx.debtId, goalId = oldTx.goalId ?: newTx.goalId)
                val validation = LedgerService.validateTransaction(linkedTx)
                if (validation.isFailure) return@withContext validation

                // 1. Revert effect of old transaction
                LedgerService.revertTransactionEffect(db, oldTx, now)

                // 2. Apply effect of new transaction
                LedgerService.applyTransactionEffect(db, linkedTx, now)

                // 3. Update transaction row
                val cv = ContentValues().apply {
                    put("type", newTx.type)
                    put("amount", newTx.amount)
                    put("description", newTx.description)
                    put("category_id", if (newTx.type != TransactionType.TRANSFER.value) newTx.categoryId else null)
                    put("asset_id", newTx.assetId)
                    put("destination_asset_id", if (newTx.type == TransactionType.TRANSFER.value) newTx.destinationAssetId else null)
                    put("transaction_date", newTx.transactionDate)
                    put("transaction_time", newTx.transactionTime)
                    put("note", newTx.note)
                    put("attachment", newTx.attachment)
                    put("debt_id", linkedTx.debtId)
                    put("goal_id", linkedTx.goalId)
                    put("is_sample", if (newTx.isSample) 1 else 0)
                    put("tags", newTx.tags)
                    put("is_adjustment", if (newTx.type == TransactionType.ADJUSTMENT.value) (if (newTx.isAdjustment) 1 else 0) else 0)
                    put("adjustment_direction", if (newTx.type == TransactionType.ADJUSTMENT.value) newTx.adjustmentDirection else 0)
                    put("updated_at", now)
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
    }

    suspend fun deleteTransaction(txId: String): Result<Unit> = balanceWriteMutex.withLock {
        withContext(Dispatchers.IO) {
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

                val now = System.currentTimeMillis()
                LedgerService.revertTransactionEffect(db, tx, now)

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
    }

    suspend fun recalculateBalances(autoFix: Boolean = true): Result<LedgerRecalculateResult> = balanceWriteMutex.withLock {
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                val result = LedgerService.recalculateAllBalances(db, autoFix = autoFix)
                db.setTransactionSuccessful()
                _integrityReportItems.value = result.allItems
                if (autoFix) {
                    _integrityDiscrepancies.value = emptyList()
                } else {
                    _integrityDiscrepancies.value = result.details
                }
                Result.success(result)
            } catch (e: Exception) {
                DiagnosticLogManager.logError(context, "RECALCULATE_BALANCES", e)
                Result.failure(e)
            } finally {
                db.endTransaction()
                loadFromDb()
            }
        }
    }

    suspend fun repairBalanceDiscrepancies(): Result<LedgerRecalculateResult> {
        // Buat backup pengaman otomatis sebelum perbaikan saldo
        try {
            val db = dbHelper.readableDatabase
            val json = BackupManager.createBackupJson(db)
            val backupDir = File(context.filesDir, "backups/safety")
            if (!backupDir.exists()) backupDir.mkdirs()
            val safetyFile = File(backupDir, "safety_repair_${System.currentTimeMillis()}.json")
            safetyFile.writeText(json)
        } catch (_: Exception) {}

        return recalculateBalances(autoFix = true)
    }

    fun checkBalanceSufficiency(assetId: String, amount: Long): Boolean {
        val asset = _assets.value.find { it.id == assetId } ?: return true
        return LedgerService.checkBalanceSufficiency(asset.currentBalance, amount)
    }

    suspend fun deleteSampleData(): Result<Unit> = balanceWriteMutex.withLock {
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            try {
                dbHelper.deleteSampleData(db)
                LedgerService.recalculateAllBalances(db, autoFix = true)
                loadFromDb()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun deleteAllUserData(): Result<Unit> = balanceWriteMutex.withLock {
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            try {
                dbHelper.deleteAllUserData(db)
                loadFromDb()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // --- ASSET OPERATIONS ---

    suspend fun addAsset(asset: Asset): Result<Unit> = balanceWriteMutex.withLock {
        withContext(Dispatchers.IO) {
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
                    put("is_sample", if (asset.isSample) 1 else 0)
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
    }

    suspend fun updateAsset(asset: Asset): Result<Unit> = balanceWriteMutex.withLock {
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            db.beginTransaction()
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
                db.setTransactionSuccessful()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                db.endTransaction()
                loadFromDb()
            }
        }
    }

    suspend fun hasTransactions(assetId: String): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM ${DatabaseHelper.TABLE_TRANSACTIONS} WHERE asset_id = ? OR destination_asset_id = ?",
            arrayOf(assetId, assetId)
        )
        cursor.use {
            if (it.moveToNext()) it.getInt(0) > 0 else false
        }
    }

    suspend fun updateAssetOpeningBalance(assetId: String, newOpeningBalance: Long): Result<Unit> = balanceWriteMutex.withLock {
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                val cursor = db.rawQuery(
                    "SELECT COUNT(*) FROM ${DatabaseHelper.TABLE_TRANSACTIONS} WHERE asset_id = ? OR destination_asset_id = ?",
                    arrayOf(assetId, assetId)
                )
                val txCount = cursor.use { if (it.moveToNext()) it.getInt(0) else 0 }
                if (txCount > 0) {
                    return@withContext Result.failure(IllegalStateException("Saldo awal tidak dapat diubah karena aset sudah memiliki riwayat transaksi."))
                }

                val cv = ContentValues().apply {
                    put("opening_balance", newOpeningBalance)
                    put("updated_at", System.currentTimeMillis())
                }
                val rows = db.update(DatabaseHelper.TABLE_ASSETS, cv, "id = ?", arrayOf(assetId))
                if (rows > 0) {
                    // Jalur sah (b): hitung ulang saldo via LedgerService
                    LedgerService.recalculateAllBalances(db, autoFix = true)
                    db.setTransactionSuccessful()
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalArgumentException("Aset tidak ditemukan."))
                }
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                db.endTransaction()
                loadFromDb()
            }
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

    suspend fun depositToGoal(
        goalId: String,
        amount: Long,
        sourceAssetId: String?,
        note: String = ""
    ): Result<Unit> = balanceWriteMutex.withLock {
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                require(amount > 0L) { "Nominal alokasi harus lebih dari 0." }
                val goal = _goals.value.find { it.id == goalId }
                    ?: return@withContext Result.failure(IllegalArgumentException("Target tidak ditemukan"))

                val now = System.currentTimeMillis()
                if (!sourceAssetId.isNullOrBlank()) {
                    val todayStr = LocalDate.now().toString()
                    val timeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                    val savingsCatId = _categories.value.find { it.name.contains("tabung", ignoreCase = true) }?.id
                        ?: _categories.value.firstOrNull { it.type == "expense" }?.id
                        ?: "cat_lainnya_exp"

                    val tx = Transaction(
                        id = "tx_goal_${System.currentTimeMillis()}",
                        type = TransactionType.ALLOCATION.value,
                        amount = amount,
                        description = if (note.isNotBlank()) note else "Tabungan: ${goal.name}",
                        categoryId = savingsCatId,
                        assetId = sourceAssetId,
                        destinationAssetId = null,
                        transactionDate = todayStr,
                        transactionTime = timeStr,
                        note = "Alokasi target: ${goal.name}",
                        inputMethod = "manual",
                        goalId = goalId,
                        createdAt = now,
                        updatedAt = now
                    )

                    val cvTx = ContentValues().apply {
                        put("id", tx.id)
                        put("type", tx.type)
                        put("amount", tx.amount)
                        put("description", tx.description)
                        put("category_id", tx.categoryId)
                        put("asset_id", tx.assetId)
                        put("destination_asset_id", tx.destinationAssetId)
                        put("transaction_date", tx.transactionDate)
                        put("transaction_time", tx.transactionTime)
                        put("note", tx.note)
                        put("input_method", tx.inputMethod)
                        put("goal_id", tx.goalId)
                        put("created_at", tx.createdAt)
                        put("updated_at", tx.updatedAt)
                    }
                    db.insertOrThrow(DatabaseHelper.TABLE_TRANSACTIONS, null, cvTx)

                    // Jalur sah (a): perbarui saldo aset dan saldo target lewat LedgerService
                    LedgerService.applyTransactionEffect(db, tx, now)
                } else {
                    val newCurrent = goal.currentAmount + amount
                    val cvGoal = ContentValues().apply {
                        put("current_amount", newCurrent)
                        put("updated_at", now)
                    }
                    db.update(DatabaseHelper.TABLE_GOALS, cvGoal, "id = ?", arrayOf(goalId))
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
    }

    suspend fun withdrawFromGoal(
        goalId: String,
        amount: Long,
        destinationAssetId: String?,
        note: String = ""
    ): Result<Unit> = balanceWriteMutex.withLock {
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                require(amount > 0L) { "Nominal pelepasan harus lebih dari 0." }
                val goal = _goals.value.find { it.id == goalId }
                    ?: return@withContext Result.failure(IllegalArgumentException("Target tidak ditemukan"))
                require(amount <= goal.currentAmount) { "Nominal melebihi dana yang dialokasikan." }

                val now = System.currentTimeMillis()
                if (!destinationAssetId.isNullOrBlank()) {
                    val todayStr = LocalDate.now().toString()
                    val timeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                    val incCatId = _categories.value.firstOrNull { it.type == "income" }?.id ?: "cat_lainnya_inc"

                    val tx = Transaction(
                        id = "tx_goal_wd_${System.currentTimeMillis()}",
                        type = TransactionType.RELEASE.value,
                        amount = amount,
                        description = if (note.isNotBlank()) note else "Pencairan: ${goal.name}",
                        categoryId = incCatId,
                        assetId = destinationAssetId,
                        destinationAssetId = null,
                        transactionDate = todayStr,
                        transactionTime = timeStr,
                        note = "Pencairan target: ${goal.name}",
                        inputMethod = "manual",
                        goalId = goalId,
                        createdAt = now,
                        updatedAt = now
                    )

                    val cvTx = ContentValues().apply {
                        put("id", tx.id)
                        put("type", tx.type)
                        put("amount", tx.amount)
                        put("description", tx.description)
                        put("category_id", tx.categoryId)
                        put("asset_id", tx.assetId)
                        put("destination_asset_id", tx.destinationAssetId)
                        put("transaction_date", tx.transactionDate)
                        put("transaction_time", tx.transactionTime)
                        put("note", tx.note)
                        put("input_method", tx.inputMethod)
                        put("goal_id", tx.goalId)
                        put("created_at", tx.createdAt)
                        put("updated_at", tx.updatedAt)
                    }
                    db.insertOrThrow(DatabaseHelper.TABLE_TRANSACTIONS, null, cvTx)

                    // Jalur sah (a): perbarui saldo aset dan saldo target lewat LedgerService
                    LedgerService.applyTransactionEffect(db, tx, now)
                } else {
                    val newCurrent = (goal.currentAmount - amount).coerceAtLeast(0L)
                    val cvGoal = ContentValues().apply {
                        put("current_amount", newCurrent)
                        put("updated_at", now)
                    }
                    db.update(DatabaseHelper.TABLE_GOALS, cvGoal, "id = ?", arrayOf(goalId))
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
    }

    // --- DEBT OPERATIONS (STANDALONE TRACKER) ---

    suspend fun addDebt(
        title: String,
        lenderName: String,
        totalAmount: Long,
        dueDate: String,
        note: String = "",
        receiveAssetId: String? = null,
        direction: String = "debt"
    ): Result<Unit> = balanceWriteMutex.withLock {
        withContext(Dispatchers.IO) {
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
                require(direction in listOf("debt", "receivable"))
                LocalDate.parse(dueDate)
                val debtId = UUID.randomUUID().toString()
                val debtCv = ContentValues().apply {
                    put("id", debtId)
                    put("title", title.trim())
                    put("lender_name", lenderName.trim())
                    put("direction", direction)
                    put("total_amount", totalAmount)
                    put("paid_amount", 0L)
                    put("due_date", dueDate.trim())
                    put("status", DebtStatus.UNPAID.key)
                    put("note", note.trim())
                    put("created_at", now)
                    put("updated_at", now)
                }
                db.insertOrThrow(DatabaseHelper.TABLE_DEBTS, null, debtCv)

                // If user selected an asset to receive borrowed funds, increment asset balance & record INCOME transaction via LedgerService
                if (!receiveAssetId.isNullOrBlank()) {
                    val txId = "tx_${UUID.randomUUID()}"
                    val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val timeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                    val txCv = ContentValues().apply {
                        put("id", txId)
                        put("type", if (direction == "receivable") TransactionType.LOAN_GIVEN.value else TransactionType.LOAN_RECEIVED.value)
                        put("amount", totalAmount)
                        put("description", "Dana Pinjaman: ${title.trim()} (${lenderName.trim()})")
                        put("category_id", "cat_lainnya_inc")
                        put("asset_id", receiveAssetId)
                        put("destination_asset_id", null as String?)
                        put("transaction_date", todayStr)
                        put("transaction_time", timeStr)
                        put("note", if (note.isNotBlank()) note.trim() else "Penerimaan dana pinjaman dari ${lenderName.trim()}")
                        put("attachment", null as String?)
                        put("input_method", "manual")
                        put("debt_id", debtId)
                        put("created_at", now)
                        put("updated_at", now)
                    }
                    db.insertOrThrow(DatabaseHelper.TABLE_TRANSACTIONS, null, txCv)

                    val tx = Transaction(
                        id = txId,
                        type = if (direction == "receivable") TransactionType.LOAN_GIVEN.value else TransactionType.LOAN_RECEIVED.value,
                        amount = totalAmount,
                        description = "Dana Pinjaman: ${title.trim()} (${lenderName.trim()})",
                        categoryId = "cat_lainnya_inc",
                        assetId = receiveAssetId,
                        destinationAssetId = null,
                        transactionDate = todayStr,
                        transactionTime = timeStr,
                        debtId = debtId,
                        createdAt = now,
                        updatedAt = now
                    )
                    // Jalur sah (a): perbarui saldo aset lewat LedgerService
                    LedgerService.applyTransactionEffect(db, tx, now)
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
    }

    suspend fun recordDebtPayment(
        debtId: String,
        paymentAmount: Long,
        sourceAssetId: String,
        paymentDate: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
        note: String = ""
    ): Result<Unit> = balanceWriteMutex.withLock {
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                if (paymentAmount <= 0) {
                    return@withContext Result.failure(IllegalArgumentException("Nominal pembayaran harus lebih dari 0"))
                }
                if (sourceAssetId.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Pilih aset sumber pembayaran cicilan"))
                }

                // Fetch Debt details for transaction description
                val cursor = db.query(
                    DatabaseHelper.TABLE_DEBTS,
                    null, "id = ?", arrayOf(debtId),
                    null, null, null
                )
                val existingDebt = cursor.use {
                    if (it.moveToNext()) cursorToDebt(it) else null
                } ?: return@withContext Result.failure(IllegalStateException("Data hutang tidak ditemukan"))

                val now = System.currentTimeMillis()
                val txId = "tx_${UUID.randomUUID()}"
                require(paymentAmount <= existingDebt.remainingAmount) { "Nominal melebihi sisa hutang/piutang." }
                val paymentType = if (existingDebt.direction == "receivable") TransactionType.RECEIVABLE_PAYMENT.value else TransactionType.DEBT_PAYMENT.value
                val debtCategory = _categories.value.firstOrNull {
                    it.type == "expense" && (it.name.contains("Hutang", ignoreCase = true) || it.name.contains("Tagihan", ignoreCase = true))
                }?.id ?: "cat_tagihan"
                val timeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

                val txCv = ContentValues().apply {
                    put("id", txId)
                    put("type", paymentType)
                    put("amount", paymentAmount)
                    put("description", "Pelunasan Pinjaman: ${existingDebt.title} (${existingDebt.lenderName})")
                    put("category_id", debtCategory)
                    put("asset_id", sourceAssetId)
                    put("destination_asset_id", null as String?)
                    put("transaction_date", paymentDate)
                    put("transaction_time", timeStr)
                    put("note", if (note.isNotBlank()) note.trim() else "Cicilan/pelunasan pinjaman kepada ${existingDebt.lenderName}")
                    put("attachment", null as String?)
                    put("input_method", "manual")
                    put("debt_id", debtId)
                    put("created_at", now)
                    put("updated_at", now)
                }
                db.insertOrThrow(DatabaseHelper.TABLE_TRANSACTIONS, null, txCv)

                val tx = Transaction(
                    id = txId,
                    type = paymentType,
                    amount = paymentAmount,
                    description = "Pelunasan Pinjaman: ${existingDebt.title} (${existingDebt.lenderName})",
                    categoryId = debtCategory,
                    assetId = sourceAssetId,
                    destinationAssetId = null,
                    transactionDate = paymentDate,
                    transactionTime = timeStr,
                    debtId = debtId,
                    createdAt = now,
                    updatedAt = now
                )
                // Jalur sah (a): potong saldo aset DAN sinkronkan cicilan hutang lewat LedgerService secara atomik
                LedgerService.applyTransactionEffect(db, tx, now)

                db.setTransactionSuccessful()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                db.endTransaction()
                loadFromDb()
            }
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

    // --- JSON BACKUP & RESTORE ---

    suspend fun createBackupJson(): Result<String> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        try {
            val json = BackupManager.createBackupJson(db)
            Result.success(json)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun validateBackupJson(jsonString: String): BackupValidationResult {
        return BackupManager.validateBackupJson(jsonString)
    }

    suspend fun restoreFromJson(data: JSONObject): Result<Unit> = balanceWriteMutex.withLock {
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            val res = runCatching {
                BackupManager.writeSafetyBackup(context, db)
                BackupManager.restoreBackup(db, data).getOrThrow()
            }
            if (res.isSuccess) {
                loadFromDb()
            }
            res
        }
    }

    suspend fun createBackupZip(destZipFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        BackupManager.createBackupZipWithReceipts(context, db, destZipFile)
    }

    suspend fun restoreFromZip(zipFile: File): Result<BackupSummary> = balanceWriteMutex.withLock {
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            val res = BackupManager.restoreBackupFromZip(context, db, zipFile)
            if (res.isSuccess) {
                loadFromDb()
            }
            res
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
                TransactionType.ALLOCATION.value -> "Alokasi Tabungan"
                TransactionType.RELEASE.value -> "Pelepasan Alokasi"
                else -> tx.type
            }
            val catName = tx.categoryId?.let { catMap[it]?.name } ?: "-"
            val assetName = assetMap[tx.assetId]?.name ?: tx.assetId
            val destAssetName = tx.destinationAssetId?.let { assetMap[it]?.name ?: it } ?: "-"

            sb.append("\"${tx.transactionDate}\",")
            sb.append("\"${tx.transactionTime}\",")
            sb.append("\"${escapeCsv(tx.description)}\",")
            sb.append("\"$typeName\",")
            sb.append("\"${escapeCsv(catName)}\",")
            sb.append("\"${escapeCsv(assetName)}\",")
            sb.append("\"${escapeCsv(destAssetName)}\",")
            sb.append("${tx.amount},")
            sb.append("\"${escapeCsv(tx.note)}\",")
            sb.append("\"${escapeCsv(tx.inputMethod)}\"\n")
        }
        sb.toString()
    }

    private fun escapeCsv(str: String): String {
        return str.replace("\"", "\"\"")
    }

    suspend fun importCsv(content: String): Result<Int> = balanceWriteMutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                loadFromDb()
                val preview = com.cashflow.app.data.importer.CsvImport.preview(content, _assets.value, _categories.value, _transactions.value)
                require(preview.errors.isEmpty()) { preview.errors.take(5).joinToString("\n") }
                val db = dbHelper.writableDatabase
                db.beginTransaction()
                try {
                    val now = System.currentTimeMillis()
                    preview.transactions.forEach { tx ->
                        LedgerService.applyTransactionEffect(db, tx, now)
                        insertTransactionRow(db, tx, now)
                    }
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
                loadFromDb()
                preview.transactions.size
            }
        }
    }

    // --- CURSOR MAPPERS ---

    private fun cursorToAsset(cursor: Cursor): Asset {
        val sampleIdx = cursor.getColumnIndex("is_sample")
        return Asset(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
            type = cursor.getString(cursor.getColumnIndexOrThrow("type")),
            openingBalance = cursor.getLong(cursor.getColumnIndexOrThrow("opening_balance")),
            currentBalance = cursor.getLong(cursor.getColumnIndexOrThrow("current_balance")),
            currency = cursor.getString(cursor.getColumnIndexOrThrow("currency")),
            isDefault = cursor.getInt(cursor.getColumnIndexOrThrow("is_default")) == 1,
            isActive = cursor.getInt(cursor.getColumnIndexOrThrow("is_active")) == 1,
            isSample = if (sampleIdx >= 0) cursor.getInt(sampleIdx) == 1 else false,
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
        val debtIdx = cursor.getColumnIndex("debt_id")
        val goalIdx = cursor.getColumnIndex("goal_id")
        val sampleIdx = cursor.getColumnIndex("is_sample")
        val tagsIdx = cursor.getColumnIndex("tags")
        val adjIdx = cursor.getColumnIndex("is_adjustment")
        val dirIdx = cursor.getColumnIndex("adjustment_direction")
        val isAdj = if (adjIdx >= 0) cursor.getInt(adjIdx) == 1 else false
        val rawDir = if (dirIdx >= 0) cursor.getInt(dirIdx) else 0
        val resolvedDir = if (rawDir != 0) {
            rawDir
        } else if (cursor.getString(cursor.getColumnIndexOrThrow("type")) == TransactionType.ADJUSTMENT.value || isAdj) {
            val noteVal = cursor.getString(cursor.getColumnIndexOrThrow("note")) ?: ""
            if (noteVal.contains("(-)")) -1 else 1
        } else {
            0
        }
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
            debtId = if (debtIdx >= 0) cursor.getString(debtIdx) else null,
            goalId = if (goalIdx >= 0) cursor.getString(goalIdx) else null,
            isSample = if (sampleIdx >= 0) cursor.getInt(sampleIdx) == 1 else false,
            tags = if (tagsIdx >= 0) cursor.getString(tagsIdx) ?: "" else "",
            isAdjustment = isAdj,
            adjustmentDirection = resolvedDir,
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
            direction = cursor.getString(cursor.getColumnIndexOrThrow("direction")),
            totalAmount = cursor.getLong(cursor.getColumnIndexOrThrow("total_amount")),
            paidAmount = cursor.getLong(cursor.getColumnIndexOrThrow("paid_amount")),
            dueDate = cursor.getString(cursor.getColumnIndexOrThrow("due_date")),
            status = cursor.getString(cursor.getColumnIndexOrThrow("status")),
            note = cursor.getString(cursor.getColumnIndexOrThrow("note")) ?: "",
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))
        )
    }

    suspend fun saveFavoriteTemplate(template: FavoriteTemplate): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            val cv = ContentValues().apply {
                put("id", template.id)
                put("name", template.name)
                put("type", template.type)
                put("amount", template.amount)
                put("category_id", template.categoryId)
                put("asset_id", template.assetId)
                put("destination_asset_id", template.destinationAssetId)
                put("note", template.note)
                put("created_at", template.createdAt)
            }
            db.insertWithOnConflict(DatabaseHelper.TABLE_FAVORITES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            loadFromDb()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFavoriteTemplate(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            db.delete(DatabaseHelper.TABLE_FAVORITES, "id = ?", arrayOf(id))
            loadFromDb()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun cursorToFavoriteTemplate(cursor: Cursor): FavoriteTemplate {
        return FavoriteTemplate(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
            type = cursor.getString(cursor.getColumnIndexOrThrow("type")),
            amount = cursor.getLong(cursor.getColumnIndexOrThrow("amount")),
            categoryId = cursor.getString(cursor.getColumnIndexOrThrow("category_id")),
            assetId = cursor.getString(cursor.getColumnIndexOrThrow("asset_id")),
            destinationAssetId = cursor.getString(cursor.getColumnIndexOrThrow("destination_asset_id")),
            note = cursor.getString(cursor.getColumnIndexOrThrow("note")) ?: "",
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
        )
    }

    suspend fun saveRecurringTransaction(rec: RecurringTransaction): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            require(rec.name.isNotBlank() && rec.amount > 0L) { "Nama dan nominal jadwal wajib diisi." }
            require(rec.interval in 1..365) { "Interval jadwal harus antara 1 dan 365." }
            require(RecurringFrequency.entries.any { it.value == rec.frequency }) { "Frekuensi jadwal tidak valid." }
            require(RecurringMode.entries.any { it.value == rec.mode }) { "Mode jadwal tidak valid." }
            val startDate = LocalDate.parse(rec.startDate)
            require(rec.endDate == null || !LocalDate.parse(rec.endDate).isBefore(startDate)) { "Tanggal akhir mendahului tanggal mulai." }
            require(rec.totalOccurrences == null || rec.totalOccurrences > 0) { "Jumlah pengulangan harus lebih dari 0." }
            LedgerService.validateTransaction(Transaction(rec.id, rec.type, rec.amount, rec.name,
                assetId = rec.assetId, destinationAssetId = rec.destinationAssetId, transactionDate = rec.startDate)).getOrThrow()
            val db = dbHelper.writableDatabase
            val cv = ContentValues().apply {
                put("id", rec.id)
                put("name", rec.name)
                put("type", rec.type)
                put("amount", rec.amount)
                put("category_id", rec.categoryId)
                put("asset_id", rec.assetId)
                put("destination_asset_id", rec.destinationAssetId)
                put("frequency", rec.frequency)
                put("interval", rec.interval)
                put("start_date", rec.startDate)
                put("end_date", rec.endDate)
                put("total_occurrences", rec.totalOccurrences)
                put("occurrences_done", rec.occurrencesDone)
                put("mode", rec.mode)
                put("linked_debt_id", rec.linkedDebtId)
                put("last_executed_date", rec.lastExecutedDate)
                put("is_active", if (rec.isActive) 1 else 0)
                put("note", rec.note)
                put("created_at", rec.createdAt)
            }
            // UPDATE preserves occurrence history; REPLACE would cascade-delete it.
            val editableValues = ContentValues(cv).apply {
                remove("occurrences_done")
                remove("last_executed_date")
                remove("created_at")
            }
            if (db.update(DatabaseHelper.TABLE_RECURRING, editableValues, "id = ?", arrayOf(rec.id)) == 0) {
                db.insertOrThrow(DatabaseHelper.TABLE_RECURRING, null, cv)
            }
            loadFromDb()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRecurringTransaction(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            db.delete(DatabaseHelper.TABLE_RECURRING, "id = ?", arrayOf(id))
            db.delete(DatabaseHelper.TABLE_RECURRING_EXECUTIONS, "recurring_id = ?", arrayOf(id))
            loadFromDb()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun executeRecurringOccurrence(recurringId: String, scheduledDate: String, confirmed: Boolean = false): Result<Transaction?> = balanceWriteMutex.withLock {
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                val rec = db.query(DatabaseHelper.TABLE_RECURRING, null, "id = ?", arrayOf(recurringId),
                    null, null, null).use { if (it.moveToFirst()) cursorToRecurringTransaction(it) else null }
                    ?: throw IllegalArgumentException("Jadwal tidak ditemukan.")
                require(rec.isActive) { "Jadwal tidak aktif." }
                if (isOccurrenceExecuted(rec.id, scheduledDate)) return@withContext Result.success(null)
                require(rec.mode == RecurringMode.AUTO.value || confirmed) { "Jadwal memerlukan konfirmasi." }
                require(rec.interval > 0) { "Interval jadwal tidak valid." }
                val date = LocalDate.parse(scheduledDate)
                require(com.cashflow.app.data.service.RecurringSchedulerService.generateScheduledDates(rec, date).lastOrNull() == date) {
                    "Tanggal bukan bagian dari jadwal."
                }
                val tx = Transaction(id = "tx_rec_${rec.id}_$scheduledDate", type = rec.type, amount = rec.amount,
                    description = rec.name, categoryId = rec.categoryId, assetId = rec.assetId,
                    destinationAssetId = rec.destinationAssetId, transactionDate = if (confirmed) LocalDate.now().toString() else scheduledDate,
                    transactionTime = "08:00", note = "[Otomatis Berulang] ${rec.note.orEmpty()}".trim(), debtId = rec.linkedDebtId)
                LedgerService.validateTransaction(tx).getOrThrow()
                val now = System.currentTimeMillis()
                LedgerService.applyTransactionEffect(db, tx, now)
                insertTransactionRow(db, tx, now)
                insertRecurringExecution(db, RecurringExecution(recurringId = rec.id, scheduledDate = scheduledDate,
                    transactionId = tx.id, status = "completed"))
                db.setTransactionSuccessful()
                Result.success(tx)
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                db.endTransaction()
                loadFromDb()
            }
        }
    }

    private fun insertRecurringExecution(db: SQLiteDatabase, execution: RecurringExecution) {
        val cv = ContentValues().apply {
            put("id", execution.id)
            put("recurring_id", execution.recurringId)
            put("scheduled_date", execution.scheduledDate)
            put("transaction_id", execution.transactionId)
            put("executed_at", execution.executedAt)
            put("status", execution.status)
        }
        if (db.insertWithOnConflict(DatabaseHelper.TABLE_RECURRING_EXECUTIONS, null, cv, SQLiteDatabase.CONFLICT_IGNORE) != -1L) {
            db.execSQL("UPDATE ${DatabaseHelper.TABLE_RECURRING} SET last_executed_date = MAX(COALESCE(last_executed_date, ''), ?), occurrences_done = occurrences_done + 1 WHERE id = ?",
                arrayOf(execution.scheduledDate, execution.recurringId))
        }
    }

    suspend fun recordRecurringExecution(execution: RecurringExecution): Result<Unit> = balanceWriteMutex.withLock {
        withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            LocalDate.parse(execution.scheduledDate)
            require(execution.status == "skipped") { "Eksekusi transaksi harus dicatat bersama transaksi keuangannya." }
            insertRecurringExecution(db, execution)
            db.setTransactionSuccessful()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            db.endTransaction()
            loadFromDb()
        }
        }
    }

    fun isOccurrenceExecuted(recurringId: String, scheduledDate: String): Boolean {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT 1 FROM ${DatabaseHelper.TABLE_RECURRING_EXECUTIONS} WHERE recurring_id = ? AND scheduled_date = ?",
            arrayOf(recurringId, scheduledDate)
        )
        return cursor.use { it.moveToFirst() }
    }

    fun executedOccurrenceKeys(): Set<Pair<String, String>> {
        val keys = mutableSetOf<Pair<String, String>>()
        dbHelper.readableDatabase.rawQuery("SELECT recurring_id, scheduled_date FROM ${DatabaseHelper.TABLE_RECURRING_EXECUTIONS}", null).use {
            while (it.moveToNext()) keys.add(it.getString(0) to it.getString(1))
        }
        return keys
    }

    private fun cursorToRecurringTransaction(cursor: Cursor): RecurringTransaction {
        val totalOccIdx = cursor.getColumnIndex("total_occurrences")
        return RecurringTransaction(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
            type = cursor.getString(cursor.getColumnIndexOrThrow("type")),
            amount = cursor.getLong(cursor.getColumnIndexOrThrow("amount")),
            categoryId = cursor.getString(cursor.getColumnIndexOrThrow("category_id")),
            assetId = cursor.getString(cursor.getColumnIndexOrThrow("asset_id")),
            destinationAssetId = cursor.getString(cursor.getColumnIndexOrThrow("destination_asset_id")),
            frequency = cursor.getString(cursor.getColumnIndexOrThrow("frequency")),
            interval = cursor.getInt(cursor.getColumnIndexOrThrow("interval")),
            startDate = cursor.getString(cursor.getColumnIndexOrThrow("start_date")),
            endDate = cursor.getString(cursor.getColumnIndexOrThrow("end_date")),
            totalOccurrences = if (totalOccIdx >= 0 && !cursor.isNull(totalOccIdx)) cursor.getInt(totalOccIdx) else null,
            occurrencesDone = cursor.getInt(cursor.getColumnIndexOrThrow("occurrences_done")),
            mode = cursor.getString(cursor.getColumnIndexOrThrow("mode")),
            linkedDebtId = cursor.getString(cursor.getColumnIndexOrThrow("linked_debt_id")),
            lastExecutedDate = cursor.getString(cursor.getColumnIndexOrThrow("last_executed_date")),
            isActive = cursor.getInt(cursor.getColumnIndexOrThrow("is_active")) == 1,
            note = cursor.getString(cursor.getColumnIndexOrThrow("note")),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
        )
    }

    suspend fun seedSyntheticTransactions(count: Int = 10_000): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            val currentAssets = _assets.value.map { it.id }
            val assetIds = if (currentAssets.isNotEmpty()) currentAssets else listOf("asset_tunai", "asset_bank", "asset_ewallet")

            val syntheticList = com.cashflow.app.data.benchmark.PerformanceBenchmarker.generateSyntheticTransactions(
                count = count,
                years = 3,
                referenceDate = LocalDate.now(),
                assetIds = assetIds
            )
            val insertDuration = com.cashflow.app.data.benchmark.PerformanceBenchmarker.insertBatchFast(db, syntheticList)
            LedgerService.recalculateAllBalances(db)
            loadFromDb()
            Result.success(insertDuration)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearSyntheticTransactions(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            val deletedCount = db.delete(DatabaseHelper.TABLE_TRANSACTIONS, "is_sample = 1", null)
            LedgerService.recalculateAllBalances(db)
            loadFromDb()
            Result.success(deletedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
