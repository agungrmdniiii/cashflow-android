package com.cashflow.app.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cashflow.app.data.export.PdfReportGenerator
import com.cashflow.app.data.model.*
import com.cashflow.app.data.preferences.PreferenceManager
import com.cashflow.app.data.repository.CashflowRepository
import com.cashflow.app.ui.components.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

data class SnackbarEvent(
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

class CashflowViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CashflowRepository(application.applicationContext)
    private val prefs = PreferenceManager(application.applicationContext)

    // Theme Mode State
    val themeMode = MutableStateFlow(prefs.getThemeMode())

    fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
        prefs.setThemeMode(mode)
    }

    // Biometric Security State & Session
    val isBiometricEnabled = MutableStateFlow(prefs.isBiometricEnabled())
    val biometricTimeout = MutableStateFlow(prefs.getBiometricTimeout())

    fun setBiometricEnabled(enabled: Boolean) {
        isBiometricEnabled.value = enabled
        prefs.setBiometricEnabled(enabled)
        if (!enabled) {
            isAppLocked.value = false
        }
    }

    fun setBiometricTimeout(timeout: BiometricTimeout) {
        biometricTimeout.value = timeout
        prefs.setBiometricTimeout(timeout)
    }

    val isAppLocked = MutableStateFlow(prefs.isBiometricEnabled())
    private var lastBackgroundTimestamp: Long = 0L

    fun unlockApp() {
        isAppLocked.value = false
        lastBackgroundTimestamp = 0L
        pendingAction.value?.let { action ->
            pendingAction.value = null
            handleIntentAction(action)
        }
    }

    fun lockApp() {
        if (isBiometricEnabled.value) {
            isAppLocked.value = true
        }
    }

    fun onAppBackgrounded() {
        if (isBiometricEnabled.value && !isAppLocked.value) {
            lastBackgroundTimestamp = System.currentTimeMillis()
        }
    }

    fun onAppForegrounded() {
        if (isBiometricEnabled.value) {
            if (lastBackgroundTimestamp == 0L) {
                return
            }
            val elapsed = System.currentTimeMillis() - lastBackgroundTimestamp
            val timeoutMillis = biometricTimeout.value.durationMillis
            if (elapsed >= timeoutMillis) {
                isAppLocked.value = true
            }
            lastBackgroundTimestamp = 0L
        }
    }

    // Sat-Set Quick Actions (Widget & App Shortcuts)
    val pendingAction = MutableStateFlow<String?>(null)

    fun handleIntentAction(action: String) {
        if (isAppLocked.value && isBiometricEnabled.value) {
            pendingAction.value = action
            return
        }
        when (action) {
            ACTION_RECORD_VOICE -> openRecordVoice()
            ACTION_ADD_TRANSACTION -> openAddTransaction(TransactionType.EXPENSE)
        }
    }

    fun openRecordVoice() {
        isRecordVoiceOpen.value = true
    }

    fun openAddTransaction(type: TransactionType = TransactionType.EXPENSE) {
        addTransactionInitialType.value = type
        editingTransaction.value = null
        isAddTransactionOpen.value = true
    }

    // Data streams from repository
    val assets: StateFlow<List<Asset>> = repository.assets
    val categories: StateFlow<List<Category>> = repository.categories
    val transactions: StateFlow<List<Transaction>> = repository.transactions
    val budgets: StateFlow<List<BudgetWithProgress>> = repository.budgets
    val goals: StateFlow<List<FinancialGoal>> = repository.goals
    val debts: StateFlow<List<Debt>> = repository.debts

    // UI Navigation State (0: Beranda, 1: Transaksi, 2: Anggaran, 3: Laporan, 4: Aset)
    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    fun selectTab(tab: Int) {
        _currentTab.value = tab
    }

    // Period Filter State (default: BULAN_INI)
    private val _selectedPeriod = MutableStateFlow(PeriodFilter.BULAN_INI)
    val selectedPeriod: StateFlow<PeriodFilter> = _selectedPeriod.asStateFlow()

    fun setPeriodFilter(filter: PeriodFilter) {
        _selectedPeriod.value = filter
    }

    // Transaction Search & Filter State
    val searchQuery = MutableStateFlow("")
    val filterType = MutableStateFlow<String?>("all") // "all", "expense", "income", "transfer"
    val filterCategoryId = MutableStateFlow<String?>(null)
    val filterAssetId = MutableStateFlow<String?>(null)
    val filterInputMethod = MutableStateFlow<String?>("all") // "all", "manual", "voice"

    // Snackbar notification state & event
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _snackbarEvent = MutableSharedFlow<SnackbarEvent>(extraBufferCapacity = 4)
    val snackbarEvent = _snackbarEvent.asSharedFlow()

    fun showSnackbar(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        _snackbarMessage.value = message
        _snackbarEvent.tryEmit(SnackbarEvent(message, actionLabel, onAction))
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    // Navigation state for dedicated Budgets Screen
    val isBudgetsScreenOpen = MutableStateFlow(false)
    fun openBudgetsScreen() { isBudgetsScreenOpen.value = true }
    fun closeBudgetsScreen() { isBudgetsScreenOpen.value = false }

    // Modal / Sheet States
    val isRecordVoiceOpen = MutableStateFlow(false)
    val isAddTransactionOpen = MutableStateFlow(false)
    val addTransactionInitialType = MutableStateFlow(TransactionType.EXPENSE)
    val selectedTransactionForDetail = MutableStateFlow<Transaction?>(null)
    val selectedAssetForDetail = MutableStateFlow<Asset?>(null)
    val editingTransaction = MutableStateFlow<Transaction?>(null)
    val editingAsset = MutableStateFlow<Asset?>(null)
    val isAddAssetOpen = MutableStateFlow(false)
    val isAddBudgetOpen = MutableStateFlow(false)
    val isAddGoalOpen = MutableStateFlow(false)
    val isSettingsOpen = MutableStateFlow(false)

    // Debt UI & Dialog States
    val isAddDebtOpen = MutableStateFlow(false)
    val payingDebt = MutableStateFlow<Debt?>(null)
    val assetScreenSubTab = MutableStateFlow(0) // 0: ASET SAYA, 1: HUTANG SAYA
    val selectedDebtFilter = MutableStateFlow("ALL") // "ALL", "ACTIVE", "PAID"

    // Global Privacy State: Mask sensitive financial balances
    val isBalanceHidden = MutableStateFlow(false)

    fun toggleBalanceVisibility() {
        isBalanceHidden.value = !isBalanceHidden.value
    }

    // Computed: Active Assets
    val activeAssets: StateFlow<List<Asset>> = assets.map { list ->
        list.filter { it.isActive }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Computed: Default Asset
    val defaultAsset: StateFlow<Asset?> = activeAssets.map { list ->
        list.firstOrNull { it.isDefault } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Computed: Total Wealth = Sum of active assets
    val totalWealth: StateFlow<Long> = activeAssets.map { list ->
        list.sumOf { it.currentBalance }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    // Computed: Total Remaining Debt (sisa kewajiban hutang yang belum terbayar)
    val totalRemainingDebt: StateFlow<Long> = debts.map { list ->
        list.sumOf { it.remainingAmount }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    // Computed: Total Paid Debt (total cicilan hutang yang telah dibayarkan)
    val totalPaidDebt: StateFlow<Long> = debts.map { list ->
        list.sumOf { it.paidAmount }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    // Computed: Active Debts Count
    val activeDebtsCount: StateFlow<Int> = debts.map { list ->
        list.count { it.status != DebtStatus.PAID.key }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // Computed: Overdue Debts Count
    val overdueDebtsCount: StateFlow<Int> = debts.map { list ->
        list.count { it.isOverdue }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // Computed: Real Net Worth = Total Wealth - Total Remaining Debt
    val netWorth: StateFlow<Long> = combine(totalWealth, totalRemainingDebt) { wealth, debt ->
        wealth - debt
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    // Helper: Checks if a date string matches the current period filter
    fun isDateInPeriod(dateStr: String, filter: PeriodFilter): Boolean {
        if (filter == PeriodFilter.SEMUA) return true
        val date = try {
            LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: Exception) {
            return false
        }
        val today = LocalDate.now()

        return when (filter) {
            PeriodFilter.HARI_INI -> date == today
            PeriodFilter.MINGGU_INI -> {
                val startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val endOfWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                !date.isBefore(startOfWeek) && !date.isAfter(endOfWeek)
            }
            PeriodFilter.BULAN_INI -> {
                date.year == today.year && date.monthValue == today.monthValue
            }
            PeriodFilter.TAHUN_INI -> {
                date.year == today.year
            }
            PeriodFilter.SEMUA -> true
        }
    }

    // Computed: Period-filtered transactions
    val periodTransactions: StateFlow<List<Transaction>> = combine(
        transactions,
        selectedPeriod
    ) { txList, period ->
        txList.filter { isDateInPeriod(it.transactionDate, period) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Computed: Period Income
    val periodIncome: StateFlow<Long> = periodTransactions.map { list ->
        list.filter { it.type == TransactionType.INCOME.value }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    // Computed: Period Expense
    val periodExpense: StateFlow<Long> = periodTransactions.map { list ->
        list.filter { it.type == TransactionType.EXPENSE.value }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    // Computed: Net Cashflow = Total Income - Total Expense (Transfer excluded!)
    val netCashflow: StateFlow<Long> = combine(periodIncome, periodExpense) { inc, exp ->
        inc - exp
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    // Computed: Largest Expense Transaction
    val largestExpense: StateFlow<Transaction?> = periodTransactions.map { list ->
        list.filter { it.type == TransactionType.EXPENSE.value }.maxByOrNull { it.amount }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Computed: Largest Expense Category
    val largestCategory: StateFlow<Pair<Category?, Long>?> = combine(
        periodTransactions,
        categories
    ) { txList, catList ->
        val expenseTx = txList.filter { it.type == TransactionType.EXPENSE.value && it.categoryId != null }
        if (expenseTx.isEmpty()) return@combine null

        val grouped = expenseTx.groupBy { it.categoryId!! }
        val maxEntry = grouped.maxByOrNull { entry -> entry.value.sumOf { it.amount } } ?: return@combine null
        val cat = catList.firstOrNull { it.id == maxEntry.key }
        val sum = maxEntry.value.sumOf { it.amount }
        Pair(cat, sum)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Computed: Recently Used Categories (Section 15) from actual user transactions
    val recentCategories: StateFlow<List<Category>> = combine(
        transactions,
        categories
    ) { txList, catList ->
        val catMap = catList.associateBy { it.id }
        txList.mapNotNull { it.categoryId }
            .distinct()
            .mapNotNull { catMap[it] }
            .take(5)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Computed: Recently Used Assets (Section 15) from actual user transactions
    val recentAssets: StateFlow<List<Asset>> = combine(
        transactions,
        activeAssets
    ) { txList, assetList ->
        val assetMap = assetList.associateBy { it.id }
        txList.mapNotNull { it.assetId }
            .distinct()
            .mapNotNull { assetMap[it] }
            .take(4)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Computed: Filtered Transactions for Transactions Tab (Search + Filters)
    val filteredTransactionsTab: StateFlow<List<Transaction>> = combine(
        transactions,
        categories,
        assets,
        searchQuery,
        filterType,
        filterCategoryId,
        filterAssetId,
        filterInputMethod
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val txList = args[0] as List<Transaction>
        @Suppress("UNCHECKED_CAST")
        val catList = args[1] as List<Category>
        @Suppress("UNCHECKED_CAST")
        val assetList = args[2] as List<Asset>
        val query = args[3] as String
        val type = args[4] as String?
        val catId = args[5] as String?
        val assetId = args[6] as String?
        val inputMethod = args[7] as String?

        val catMap = catList.associateBy { it.id }
        val assetMap = assetList.associateBy { it.id }

        txList.filter { tx ->
            val catName = catMap[tx.categoryId]?.name ?: ""
            val assetName = assetMap[tx.assetId]?.name ?: ""
            val destAssetName = tx.destinationAssetId?.let { assetMap[it]?.name } ?: ""

            // Search match across description, notes, category name, asset name, and amount (Section 19)
            val matchesQuery = query.isBlank() ||
                    tx.description.contains(query, ignoreCase = true) ||
                    tx.note.contains(query, ignoreCase = true) ||
                    catName.contains(query, ignoreCase = true) ||
                    assetName.contains(query, ignoreCase = true) ||
                    destAssetName.contains(query, ignoreCase = true) ||
                    tx.amount.toString().contains(query)

            // Type match
            val matchesType = type == "all" || type == null || tx.type == type

            // Category match
            val matchesCat = catId == null || tx.categoryId == catId

            // Asset match
            val matchesAsset = assetId == null || tx.assetId == assetId || tx.destinationAssetId == assetId

            // Input Method match
            val matchesInput = inputMethod == "all" || inputMethod == null || tx.inputMethod == inputMethod

            matchesQuery && matchesType && matchesCat && matchesAsset && matchesInput
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // --- ACTIONS ---

    // Prevent duplicate rapid submissions
    private var isSubmitting = false

    fun saveTransaction(tx: Transaction, onComplete: ((Boolean) -> Unit)? = null) {
        if (isSubmitting) return
        isSubmitting = true

        viewModelScope.launch {
            val res = repository.addTransaction(tx)
            isSubmitting = false
            if (res.isSuccess) {
                showSnackbar("Transaksi berhasil disimpan.")
                onComplete?.invoke(true)
            } else {
                showSnackbar(res.exceptionOrNull()?.message ?: "Gagal menyimpan transaksi")
                onComplete?.invoke(false)
            }
        }
    }

    fun updateTransaction(tx: Transaction, onComplete: ((Boolean) -> Unit)? = null) {
        if (isSubmitting) return
        isSubmitting = true

        viewModelScope.launch {
            val res = repository.updateTransaction(tx)
            isSubmitting = false
            if (res.isSuccess) {
                showSnackbar("Transaksi berhasil diperbarui.")
                selectedTransactionForDetail.value = null
                editingTransaction.value = null
                onComplete?.invoke(true)
            } else {
                showSnackbar(res.exceptionOrNull()?.message ?: "Gagal memperbarui transaksi")
                onComplete?.invoke(false)
            }
        }
    }

    fun deleteTransaction(txId: String, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val res = repository.deleteTransaction(txId)
            if (res.isSuccess) {
                showSnackbar("Transaksi berhasil dihapus.")
                selectedTransactionForDetail.value = null
                onComplete?.invoke()
            } else {
                showSnackbar(res.exceptionOrNull()?.message ?: "Gagal menghapus transaksi")
            }
        }
    }

    fun deleteTransactionWithUndo(transaction: Transaction, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val res = repository.deleteTransaction(transaction.id)
            if (res.isSuccess) {
                selectedTransactionForDetail.value = null
                onComplete?.invoke()
                showSnackbar(
                    message = "Transaksi dihapus",
                    actionLabel = "BATALKAN",
                    onAction = {
                        viewModelScope.launch {
                            repository.addTransaction(transaction)
                        }
                    }
                )
            } else {
                showSnackbar(res.exceptionOrNull()?.message ?: "Gagal menghapus transaksi")
            }
        }
    }

    fun saveAsset(asset: Asset, isNew: Boolean, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val res = if (isNew) repository.addAsset(asset) else repository.updateAsset(asset)
            if (res.isSuccess) {
                showSnackbar(if (isNew) "Aset berhasil ditambahkan." else "Aset berhasil diperbarui.")
                isAddAssetOpen.value = false
                editingAsset.value = null
                onComplete?.invoke()
            } else {
                showSnackbar(res.exceptionOrNull()?.message ?: "Gagal menyimpan aset")
            }
        }
    }

    fun setDefaultAsset(assetId: String) {
        viewModelScope.launch {
            val res = repository.setDefaultAsset(assetId)
            if (res.isSuccess) {
                showSnackbar("Aset default berhasil diubah.")
            } else {
                showSnackbar(res.exceptionOrNull()?.message ?: "Gagal mengubah aset default")
            }
        }
    }

    fun archiveAsset(assetId: String, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val res = repository.archiveAsset(assetId)
            if (res.isSuccess) {
                showSnackbar("Aset berhasil diarsipkan. Riwayat transaksi tetap aman.")
                selectedAssetForDetail.value = null
                onComplete?.invoke()
            } else {
                showSnackbar(res.exceptionOrNull()?.message ?: "Gagal mengarsipkan aset")
            }
        }
    }

    fun deleteAsset(assetId: String, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val res = repository.deleteAsset(assetId)
            if (res.isSuccess) {
                showSnackbar("Aset berhasil dihapus.")
                selectedAssetForDetail.value = null
                onComplete?.invoke()
            } else {
                showSnackbar(res.exceptionOrNull()?.message ?: "Gagal menghapus aset")
            }
        }
    }

    fun saveBudget(budget: Budget, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val res = repository.addBudget(budget)
            if (res.isSuccess) {
                showSnackbar("Anggaran berhasil disimpan.")
                isAddBudgetOpen.value = false
                onComplete?.invoke()
            } else {
                showSnackbar(res.exceptionOrNull()?.message ?: "Gagal menyimpan anggaran")
            }
        }
    }

    fun updateBudget(budget: Budget, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val res = repository.updateBudget(budget)
            if (res.isSuccess) {
                showSnackbar("Anggaran berhasil diperbarui.")
                onComplete?.invoke()
            } else {
                showSnackbar(res.exceptionOrNull()?.message ?: "Gagal memperbarui anggaran")
            }
        }
    }

    fun deleteBudget(budgetId: String) {
        viewModelScope.launch {
            val res = repository.deleteBudget(budgetId)
            if (res.isSuccess) {
                showSnackbar("Anggaran berhasil dihapus.")
            } else {
                showSnackbar(res.exceptionOrNull()?.message ?: "Gagal menghapus anggaran")
            }
        }
    }

    fun saveGoal(goal: FinancialGoal, isNew: Boolean, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val res = if (isNew) repository.addGoal(goal) else repository.updateGoal(goal)
            if (res.isSuccess) {
                showSnackbar(if (isNew) "Target keuangan berhasil dibuat." else "Target diperbarui.")
                isAddGoalOpen.value = false
                onComplete?.invoke()
            } else {
                showSnackbar(res.exceptionOrNull()?.message ?: "Gagal menyimpan target")
            }
        }
    }

    fun deleteGoal(goalId: String) {
        viewModelScope.launch {
            val res = repository.deleteGoal(goalId)
            if (res.isSuccess) {
                showSnackbar("Target keuangan berhasil dihapus.")
            } else {
                showSnackbar(res.exceptionOrNull()?.message ?: "Gagal menghapus target")
            }
        }
    }

    fun depositToGoal(
        goalId: String,
        amount: Long,
        sourceAssetId: String?,
        note: String = "",
        onComplete: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            val res = repository.depositToGoal(goalId, amount, sourceAssetId, note)
            if (res.isSuccess) {
                showSnackbar("Tabungan sebesar ${Formatters.formatRupiah(amount)} berhasil disimpan!")
                onComplete?.invoke()
            } else {
                showSnackbar(res.exceptionOrNull()?.message ?: "Gagal menabung ke target")
            }
        }
    }

    fun withdrawFromGoal(
        goalId: String,
        amount: Long,
        destinationAssetId: String?,
        note: String = "",
        onComplete: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            val res = repository.withdrawFromGoal(goalId, amount, destinationAssetId, note)
            if (res.isSuccess) {
                showSnackbar("Pencairan dana target sebesar ${Formatters.formatRupiah(amount)} berhasil.")
                onComplete?.invoke()
            } else {
                showSnackbar(res.exceptionOrNull()?.message ?: "Gagal mencairkan dana target")
            }
        }
    }

    fun exportCsv(filteredOnly: Boolean = false, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val list = if (filteredOnly) periodTransactions.value else null
            val content = repository.generateCsvContent(list)
            onReady(content)
        }
    }

    fun exportCsvFile(
        context: Context,
        filteredOnly: Boolean = false,
        onSuccess: (File, Intent, String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val list = if (filteredOnly) periodTransactions.value else null
                val content = repository.generateCsvContent(list)
                val exportsDir = File(context.cacheDir, "exports")
                if (!exportsDir.exists()) {
                    exportsDir.mkdirs()
                }

                val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val fileName = if (filteredOnly) {
                    "DuitAing_Transaksi_${selectedPeriod.value.name.lowercase()}_$todayStr.csv"
                } else {
                    "DuitAing_Transaksi_$todayStr.csv"
                }
                val file = File(exportsDir, fileName)
                file.writeText(content, Charsets.UTF_8)

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Ekspor Transaksi Duit Aing")
                    putExtra(Intent.EXTRA_TEXT, "File ekspor transaksi Duit Aing ($todayStr).")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                onSuccess(file, sendIntent, content)
            } catch (e: Exception) {
                onError(e.message ?: "Gagal membuat file CSV")
            }
        }
    }

    fun exportPdfFile(
        context: Context,
        filteredOnly: Boolean = false,
        onSuccess: (File, Intent) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val list = if (filteredOnly) periodTransactions.value else transactions.value
                val catList = categories.value
                val assetList = assets.value
                val periodName = if (filteredOnly) selectedPeriod.value.displayName else "Semua Waktu"

                val exportsDir = File(context.cacheDir, "exports")
                if (!exportsDir.exists()) {
                    exportsDir.mkdirs()
                }

                val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val fileName = if (filteredOnly) {
                    "DuitAing_Laporan_${selectedPeriod.value.name.lowercase()}_$todayStr.pdf"
                } else {
                    "DuitAing_Laporan_Lengkap_$todayStr.pdf"
                }
                val file = File(exportsDir, fileName)

                val result = withContext(Dispatchers.IO) {
                    PdfReportGenerator.generatePdfReport(
                        context = context,
                        file = file,
                        transactions = list,
                        categories = catList,
                        assets = assetList,
                        periodTitle = periodName
                    )
                }

                if (result.isSuccess) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )

                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "Laporan Keuangan Duit Aing")
                        putExtra(Intent.EXTRA_TEXT, "File laporan keuangan Duit Aing ($periodName).")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    onSuccess(file, sendIntent)
                } else {
                    onError(result.exceptionOrNull()?.message ?: "Gagal membuat dokumen PDF")
                }
            } catch (e: Exception) {
                onError(e.message ?: "Terjadi kesalahan saat membuat PDF")
            }
        }
    }

    fun resetData() {
        viewModelScope.launch {
            val res = repository.resetToDemo()
            if (res.isSuccess) {
                showSnackbar("Data berhasil direset ke data awal.")
                isSettingsOpen.value = false
            } else {
                showSnackbar("Gagal mereset data.")
            }
        }
    }

    // --- DEBT TRACKER ACTIONS ---

    fun setAssetScreenSubTab(tab: Int) {
        assetScreenSubTab.value = tab
    }

    fun setDebtFilter(filter: String) {
        selectedDebtFilter.value = filter
    }

    fun addDebt(
        title: String,
        lenderName: String,
        totalAmount: Long,
        dueDate: String,
        note: String = "",
        receiveAssetId: String? = null
    ) {
        if (totalAmount <= 0) {
            showSnackbar("Nominal pinjaman harus lebih dari 0")
            return
        }
        if (title.isBlank() || lenderName.isBlank()) {
            showSnackbar("Nama pinjaman dan pemberi pinjaman wajib diisi")
            return
        }

        viewModelScope.launch {
            val result = repository.addDebt(
                title = title,
                lenderName = lenderName,
                totalAmount = totalAmount,
                dueDate = dueDate,
                note = note,
                receiveAssetId = receiveAssetId
            )
            result.onSuccess {
                isAddDebtOpen.value = false
                showSnackbar(
                    if (!receiveAssetId.isNullOrBlank())
                        "Catatan hutang & pencairan dana ke aset berhasil disimpan"
                    else
                        "Catatan hutang berhasil ditambahkan"
                )
            }.onFailure { e ->
                showSnackbar(e.message ?: "Gagal menambahkan catatan hutang")
            }
        }
    }

    fun recordDebtPayment(
        debtId: String,
        paymentAmount: Long,
        sourceAssetId: String,
        paymentDate: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
        note: String = ""
    ) {
        if (paymentAmount <= 0) {
            showSnackbar("Nominal pembayaran harus lebih dari 0")
            return
        }
        if (sourceAssetId.isBlank()) {
            showSnackbar("Pilih aset/rekening sumber pembayaran cicilan")
            return
        }

        viewModelScope.launch {
            val result = repository.recordDebtPayment(
                debtId = debtId,
                paymentAmount = paymentAmount,
                sourceAssetId = sourceAssetId,
                paymentDate = paymentDate,
                note = note
            )
            result.onSuccess {
                payingDebt.value = null
                showSnackbar("Pembayaran cicilan berhasil dicatat & saldo aset diperbarui")
            }.onFailure { e ->
                showSnackbar(e.message ?: "Gagal mencatat pembayaran cicilan")
            }
        }
    }

    fun deleteDebt(debtId: String) {
        viewModelScope.launch {
            val result = repository.deleteDebt(debtId)
            result.onSuccess {
                showSnackbar("Catatan hutang berhasil dihapus")
            }.onFailure { e ->
                showSnackbar(e.message ?: "Gagal menghapus hutang")
            }
        }
    }

    companion object {
        const val ACTION_RECORD_VOICE = "com.cashflow.app.ACTION_RECORD_VOICE"
        const val ACTION_ADD_TRANSACTION = "com.cashflow.app.ACTION_ADD_TRANSACTION"
    }
}
