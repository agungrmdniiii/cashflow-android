package com.cashflow.app.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cashflow.app.data.backup.BackupManager
import com.cashflow.app.data.backup.BackupSummary
import com.cashflow.app.data.backup.BackupValidationResult
import com.cashflow.app.data.export.PdfReportGenerator
import com.cashflow.app.data.model.*
import com.cashflow.app.data.preferences.PreferenceManager
import com.cashflow.app.data.repository.BalanceDiscrepancy
import com.cashflow.app.data.repository.CashflowRepository
import com.cashflow.app.data.repository.LedgerRecalculateResult
import com.cashflow.app.data.service.*
import com.cashflow.app.ui.components.Formatters
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

data class SnackbarEvent(
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

enum class DateFilterPreset(val label: String) {
    ALL("Semua"),
    TODAY("Hari Ini"),
    LAST_7_DAYS("7 Hari"),
    THIS_MONTH("Bulan Ini"),
    LAST_MONTH("Bulan Lalu"),
    CUSTOM("Kustom")
}

class CashflowViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as com.cashflow.app.CashflowApplication).repository
    val initialLoadComplete = repository.initialLoadComplete
    private val prefs = PreferenceManager(application.applicationContext)
    val budgetStartDay = MutableStateFlow(prefs.getBudgetStartDay())
    val lastBackupFormat = MutableStateFlow(prefs.getLastBackupFormat())
    val lastRestoreTime = MutableStateFlow(prefs.getLastRestoreTime())
    fun setBudgetStartDay(day: Int) {
        prefs.setBudgetStartDay(day)
        budgetStartDay.value = day
        repository.refreshAll()
    }

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

    // User Profile / Nickname
    val userName = MutableStateFlow(prefs.getUserName())
    fun setUserName(name: String) {
        val trimmed = name.trim()
        userName.value = trimmed
        prefs.setUserName(trimmed)
    }

    // Screenshot & Recents privacy
    val isScreenshotBlocked = MutableStateFlow(prefs.isScreenshotBlocked())
    fun setScreenshotBlocked(blocked: Boolean) {
        isScreenshotBlocked.value = blocked
        prefs.setScreenshotBlocked(blocked)
    }

    // Online Feature Kill Switches
    val isOnlineVoiceEnabled = MutableStateFlow(prefs.isOnlineVoiceEnabled())
    fun setOnlineVoiceEnabled(enabled: Boolean) {
        isOnlineVoiceEnabled.value = enabled
        prefs.setOnlineVoiceEnabled(enabled)
    }

    val isOnlineOcrEnabled = MutableStateFlow(prefs.isOnlineOcrEnabled())
    fun setOnlineOcrEnabled(enabled: Boolean) {
        isOnlineOcrEnabled.value = enabled
        prefs.setOnlineOcrEnabled(enabled)
    }

    val isAiInsightsEnabled = MutableStateFlow(prefs.isAiInsightsEnabled())
    fun setAiInsightsEnabled(enabled: Boolean) {
        isAiInsightsEnabled.value = enabled
        prefs.setAiInsightsEnabled(enabled)
    }

    val isCloudBackupEnabled = MutableStateFlow(prefs.isCloudBackupEnabled())
    fun setCloudBackupEnabled(enabled: Boolean) {
        isCloudBackupEnabled.value = enabled
        prefs.setCloudBackupEnabled(enabled)
    }

    // Backup & Restore Preferences
    val lastBackupTime = MutableStateFlow(prefs.getLastBackupTime())
    val isBackupReminderEnabled = MutableStateFlow(prefs.isBackupReminderEnabled())

    fun setBackupReminderEnabled(enabled: Boolean) {
        isBackupReminderEnabled.value = enabled
        prefs.setBackupReminderEnabled(enabled)
    }

    // Has backup been pending for > 7 days?
    val isBackupOverdue: StateFlow<Boolean> = combine(
        lastBackupTime,
        isBackupReminderEnabled
    ) { lastTime, enabled ->
        if (!enabled) false
        else if (lastTime == 0L) true
        else (System.currentTimeMillis() - lastTime) > (7 * 24 * 60 * 60 * 1000L)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, false)

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
            ACTION_ADD_TRANSACTION, ACTION_ADD_EXPENSE -> openAddTransaction(TransactionType.EXPENSE)
            ACTION_ADD_INCOME -> openAddTransaction(TransactionType.INCOME)
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
    val categoriesByUsage: StateFlow<List<Category>> = combine(categories, transactions) { cats, rows ->
        rankCategoriesByUsage(cats, rows)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val budgets: StateFlow<List<BudgetWithProgress>> = repository.budgets
    val goals: StateFlow<List<FinancialGoal>> = repository.goals
    val debts: StateFlow<List<Debt>> = repository.debts
    val favoriteTemplates: StateFlow<List<FavoriteTemplate>> = repository.favoriteTemplates
    val recurringTransactions: StateFlow<List<RecurringTransaction>> = repository.recurringTransactions
    val upcomingRecurringBills = MutableStateFlow<List<UpcomingRecurringItem>>(emptyList())

    val integrityDiscrepancies: StateFlow<List<BalanceDiscrepancy>> = repository.integrityDiscrepancies
    val integrityReportItems: StateFlow<List<BalanceDiscrepancy>> = repository.integrityReportItems
    val isCheckingIntegrity = MutableStateFlow(false)
    val isIntegritySheetOpen = MutableStateFlow(false)
    val initError: StateFlow<String?> = repository.initError

    fun clearInitError() {
        repository.clearInitError()
    }

    fun refreshIntegrityCheck(onComplete: ((LedgerRecalculateResult) -> Unit)? = null) {
        viewModelScope.launch {
            isCheckingIntegrity.value = true
            val result = repository.recalculateBalances(autoFix = false)
            isCheckingIntegrity.value = false
            if (result.isSuccess) {
                val res = result.getOrNull()
                if (res != null) {
                    onComplete?.invoke(res)
                }
            } else {
                showSnackbar("Gagal memeriksa integritas saldo: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun repairBalanceDiscrepancies(onComplete: ((LedgerRecalculateResult) -> Unit)? = null) {
        viewModelScope.launch {
            isCheckingIntegrity.value = true
            val result = repository.repairBalanceDiscrepancies()
            isCheckingIntegrity.value = false
            if (result.isSuccess) {
                val r = result.getOrNull()
                showSnackbar("Saldo berhasil diperbaiki (${r?.discrepanciesFound ?: 0} aset disesuaikan).")
                if (r != null) {
                    onComplete?.invoke(r)
                }
            } else {
                showSnackbar("Gagal memperbaiki saldo: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    val isRecurringScreenOpen = MutableStateFlow(false)
    val editingRecurring = MutableStateFlow<RecurringTransaction?>(null)

    init {
        viewModelScope.launch {
            try {
                repository.awaitInitialLoad()
                withContext(Dispatchers.IO) { RecurringSchedulerService.processDueRecurringTransactions(repository) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            repository.recurringTransactions.collect { recList ->
                upcomingRecurringBills.value = withContext(Dispatchers.IO) {
                    RecurringSchedulerService.getUpcomingBills(repository, recList, daysAhead = 7)
                }
            }
        }
        viewModelScope.launch {
            repository.initError.collect { error ->
                if (!error.isNullOrBlank()) {
                    showSnackbar(error)
                }
            }
        }
        viewModelScope.launch {
            repository.integrityDiscrepancies.collect { discrepancies ->
                if (discrepancies.isNotEmpty()) {
                    showSnackbar(
                        message = "Terdeteksi selisih saldo pada ${discrepancies.size} aset.",
                        actionLabel = "Perbaiki",
                        onAction = {
                            repairBalanceDiscrepancies()
                        }
                    )
                }
            }
        }
    }

    // Notification & Rollover Settings
    val isBudgetAlertsEnabled = MutableStateFlow(prefs.isBudgetAlertsEnabled())
    fun setBudgetAlertsEnabled(enabled: Boolean) {
        isBudgetAlertsEnabled.value = enabled
        prefs.setBudgetAlertsEnabled(enabled)
    }

    val isDailyReminderEnabled = MutableStateFlow(prefs.isDailyReminderEnabled())
    fun setDailyReminderEnabled(enabled: Boolean) {
        isDailyReminderEnabled.value = enabled
        prefs.setDailyReminderEnabled(enabled)
    }

    val dailyReminderTime = MutableStateFlow(prefs.getDailyReminderTime())
    fun setDailyReminderTime(time: String) {
        dailyReminderTime.value = time
        prefs.setDailyReminderTime(time)
    }

    val isBudgetRolloverEnabled = MutableStateFlow(prefs.isBudgetRolloverEnabled())
    fun setBudgetRolloverEnabled(enabled: Boolean) {
        isBudgetRolloverEnabled.value = enabled
        prefs.setBudgetRolloverEnabled(enabled)
        repository.refreshAll()
    }

    // AI & Local Insights
    val geminiApiKey = MutableStateFlow(prefs.getGeminiApiKey())
    fun setGeminiApiKey(key: String?) {
        runCatching { prefs.setGeminiApiKey(key) }
            .onSuccess { geminiApiKey.value = key }
            .onFailure { showSnackbar("Kunci gagal disimpan dengan aman: ${it.message}") }
    }

    val monthComparisonStats: StateFlow<MonthComparisonStats?> = combine(transactions, categories) { txList, catList ->
        FinancialInsightService.computeMonthComparison(txList, catList, LocalDate.now())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val ruleBasedInsights: StateFlow<List<FinancialInsight>> = combine(monthComparisonStats, budgets, debts, goals) { stats, budgetList, debtList, goalList ->
        stats?.let { FinancialInsightService.generateLocalRuleInsights(it, budgetList, debtList, goalList) } ?: emptyList()
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isInitialSetupCompleted = MutableStateFlow(prefs.isInitialSetupCompleted())
    val isAppTourPending = MutableStateFlow(prefs.isAppTourPending())

    fun dismissAppTour() {
        prefs.setAppTourPending(false)
        isAppTourPending.value = false
    }

    val hasSampleData: StateFlow<Boolean> = combine(assets, transactions) { aList, txList ->
        aList.any { it.isSample } || txList.any { it.isSample }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // UI Navigation State (0: Beranda, 1: Transaksi, 2: Anggaran, 3: Laporan, 4: Aset)
    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    fun selectTab(tab: Int) {
        _currentTab.value = tab
    }

    // Period Filter State (default: BULAN_INI)
    private val _selectedPeriod = MutableStateFlow(PeriodFilter.BULAN_INI)
    val selectedPeriod: StateFlow<PeriodFilter> = _selectedPeriod.asStateFlow()
    val reportDateRange = MutableStateFlow<ReportDateRange?>(null)
    val reportTransactions = combine(transactions, selectedPeriod, reportDateRange) { list, period, range ->
        filterTransactionsByPeriod(list, period, range = range)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val reportSummary = reportTransactions.map(::summarizeTransactions)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionSummary())

    val reportChart = combine(reportTransactions, selectedPeriod, reportDateRange) { list, period, range ->
        buildCashflowChart(list, period, customRange = range)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setReportPeriod(period: PeriodFilter) {
        reportDateRange.value = null
        setPeriodFilter(period)
    }

    fun setPeriodFilter(filter: PeriodFilter) {
        _selectedPeriod.value = filter
    }

    // Transaction Search & Filter State
    val searchQuery = MutableStateFlow("")
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private val settledSearchQuery = searchQuery.debounce { if (it.isBlank()) 0L else 150L }
    val filterType = MutableStateFlow<String?>("all") // "all", "expense", "income", "transfer"
    val filterCategoryId = MutableStateFlow<String?>(null)
    val filterAssetId = MutableStateFlow<String?>(null)
    val filterInputMethod = MutableStateFlow<String?>("all") // "all", "manual", "voice"
    val filterDatePreset = MutableStateFlow(DateFilterPreset.ALL)
    val filterStartDate = MutableStateFlow<LocalDate?>(null)
    val filterEndDate = MutableStateFlow<LocalDate?>(null)
    val filterMinAmount = MutableStateFlow<Long?>(null)
    val filterMaxAmount = MutableStateFlow<Long?>(null)
    val isFilterSheetOpen = MutableStateFlow(false)

    val activeFilterCount: StateFlow<Int> = combine(
        filterType,
        filterCategoryId,
        filterAssetId,
        filterDatePreset,
        filterMinAmount,
        filterMaxAmount
    ) { args: Array<Any?> ->
        val type = args[0] as? String
        val catId = args[1] as? String
        val assetId = args[2] as? String
        val datePreset = args[3] as? DateFilterPreset ?: DateFilterPreset.ALL
        val minAmt = args[4] as? Long
        val maxAmt = args[5] as? Long

        var count = 0
        if (type != null && type != "all") count++
        if (catId != null) count++
        if (assetId != null) count++
        if (datePreset != DateFilterPreset.ALL) count++
        if (minAmt != null) count++
        if (maxAmt != null) count++
        count
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val hasActiveFilters: StateFlow<Boolean> = activeFilterCount.map { it > 0 }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun resetFilters() {
        searchQuery.value = ""
        filterType.value = "all"
        filterCategoryId.value = null
        filterAssetId.value = null
        filterInputMethod.value = "all"
        filterDatePreset.value = DateFilterPreset.ALL
        filterStartDate.value = null
        filterEndDate.value = null
        filterMinAmount.value = null
        filterMaxAmount.value = null
    }

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
    val isBalanceHidden = MutableStateFlow(prefs.isBalanceHidden())

    fun toggleBalanceVisibility() {
        val next = !isBalanceHidden.value
        isBalanceHidden.value = next
        prefs.setBalanceHidden(next)
    }

    // Favorite Templates Preference (Direct Save vs Open Prefilled Form)
    val isFavoriteDirectSave = MutableStateFlow(prefs.isFavoriteDirectSave())

    fun setFavoriteDirectSave(direct: Boolean) {
        isFavoriteDirectSave.value = direct
        prefs.setFavoriteDirectSave(direct)
    }

    fun saveFavoriteTemplate(template: FavoriteTemplate, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val res = repository.saveFavoriteTemplate(template)
            if (res.isSuccess) {
                showSnackbar("Favorit \"${template.name}\" berhasil disimpan.")
                onComplete?.invoke(true)
            } else {
                showSnackbar("Gagal menyimpan favorit: ${res.exceptionOrNull()?.message}")
                onComplete?.invoke(false)
            }
        }
    }

    fun deleteFavoriteTemplate(id: String) {
        viewModelScope.launch {
            val res = repository.deleteFavoriteTemplate(id)
            if (res.isSuccess) {
                showSnackbar("Favorit berhasil dihapus.")
            }
        }
    }

    // Remember last used category/asset per transaction type
    fun getLastUsedCategory(type: String): String? = prefs.getLastUsedCategory(type)
    fun getLastUsedAsset(type: String): String? = prefs.getLastUsedAsset(type)
    fun getLastUsedDestinationAsset(): String? = prefs.getLastUsedDestinationAsset()

    fun rememberLastUsed(type: String, categoryId: String?, assetId: String, destAssetId: String? = null) {
        prefs.setLastUsedCategory(type, categoryId)
        prefs.setLastUsedAsset(type, assetId)
        if (type == TransactionType.TRANSFER.value && destAssetId != null) {
            prefs.setLastUsedDestinationAsset(destAssetId)
        }
    }

    // Dynamic frequent descriptions per category
    fun getFrequentDescriptions(categoryId: String?): List<String> {
        if (categoryId.isNullOrBlank()) return emptyList()
        return transactions.value
            .filter { it.categoryId == categoryId && it.description.isNotBlank() }
            .groupingBy { it.description.trim() }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(5)
            .map { it.first }
    }

    // Computed: Active Assets
    val activeAssets: StateFlow<List<Asset>> = assets.map { list ->
        list.filter { it.isActive }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Computed: Default Asset
    val defaultAsset: StateFlow<Asset?> = activeAssets.map { list ->
        list.firstOrNull { it.isDefault } ?: list.firstOrNull()
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Computed: Total Wealth = Sum of active assets
    val totalWealth: StateFlow<Long> = activeAssets.map { list ->
        list.sumOf { it.currentBalance }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    // Computed: Total Remaining Debt (sisa kewajiban hutang yang belum terbayar)
    val totalRemainingDebt: StateFlow<Long> = debts.map { list ->
        list.filter { it.direction == "debt" }.sumOf { it.remainingAmount }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    // Computed: Total Paid Debt (total cicilan hutang yang telah dibayarkan)
    val totalPaidDebt: StateFlow<Long> = debts.map { list ->
        list.filter { it.direction == "debt" }.sumOf { it.paidAmount }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    // Computed: Active Debts Count
    val activeDebtsCount: StateFlow<Int> = debts.map { list ->
        list.count { it.status != DebtStatus.PAID.key }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // Computed: Overdue Debts Count
    val overdueDebtsCount: StateFlow<Int> = debts.map { list ->
        list.count { it.isOverdue }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // Computed: Real Net Worth = Total Wealth - Total Remaining Debt
    val netWorth: StateFlow<Long> = combine(totalWealth, debts) { wealth, list ->
        wealth + list.filter { it.direction == "receivable" }.sumOf { it.remainingAmount } - list.filter { it.direction == "debt" }.sumOf { it.remainingAmount }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    // Computed: Period-filtered transactions
    val periodTransactions: StateFlow<List<Transaction>> = combine(
        transactions,
        selectedPeriod
    ) { txList, period ->
        filterTransactionsByPeriod(txList, period)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val homeChart = combine(periodTransactions, selectedPeriod) { list, period ->
        buildCashflowChart(list, period)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Computed: Period Income
    val periodIncome: StateFlow<Long> = periodTransactions.map { list ->
        list.filter { it.type == TransactionType.INCOME.value }.sumOf { it.amount }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    // Computed: Period Expense
    val periodExpense: StateFlow<Long> = periodTransactions.map { list ->
        list.filter { it.type == TransactionType.EXPENSE.value }.sumOf { it.amount }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    // Computed: Net Cashflow = Total Income - Total Expense (Transfer excluded!)
    val netCashflow: StateFlow<Long> = combine(periodIncome, periodExpense) { inc, exp ->
        inc - exp
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    // Computed: Largest Expense Transaction
    val largestExpense: StateFlow<Transaction?> = periodTransactions.map { list ->
        list.filter { it.type == TransactionType.EXPENSE.value }.maxByOrNull { it.amount }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

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
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Computed: Recently Used Categories (Section 15) from actual user transactions
    val recentCategories: StateFlow<List<Category>> = combine(
        transactions,
        categories
    ) { txList, catList ->
        val catMap = catList.associateBy { it.id }
        txList.asSequence().mapNotNull { it.categoryId }
            .distinct()
            .mapNotNull { catMap[it] }
            .take(5)
            .toList()
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Computed: Recently Used Assets (Section 15) from actual user transactions
    val recentAssets: StateFlow<List<Asset>> = combine(
        transactions,
        activeAssets
    ) { txList, assetList ->
        val assetMap = assetList.associateBy { it.id }
        txList.asSequence().mapNotNull { it.assetId }
            .distinct()
            .mapNotNull { assetMap[it] }
            .take(4)
            .toList()
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Computed: Filtered Transactions for Transactions Tab (Search + Advanced Filters)
    val filteredTransactionsTab: StateFlow<List<Transaction>> = combine(
        listOf(
            transactions,
            categories,
            assets,
            settledSearchQuery,
            filterType,
            filterCategoryId,
            filterAssetId,
            filterInputMethod,
            filterDatePreset,
            filterStartDate,
            filterEndDate,
            filterMinAmount,
            filterMaxAmount
        )
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
        val datePreset = args[8] as DateFilterPreset
        val startDate = args[9] as LocalDate?
        val endDate = args[10] as LocalDate?
        val minAmt = args[11] as Long?
        val maxAmt = args[12] as Long?

        val catMap = catList.associateBy { it.id }
        val assetMap = assetList.associateBy { it.id }
        val today = LocalDate.now()

        val dateMatches = mutableMapOf<String, Boolean>()

        txList.filter { tx ->
            val catName = catMap[tx.categoryId]?.name ?: ""
            val assetName = assetMap[tx.assetId]?.name ?: ""
            val destAssetName = tx.destinationAssetId?.let { assetMap[it]?.name } ?: ""

            // Search match across description, notes, category name, asset name, and amount
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

            // Amount range match
            val matchesMinAmt = minAmt == null || tx.amount >= minAmt
            val matchesMaxAmt = maxAmt == null || tx.amount <= maxAmt

            // Date preset / custom range match
            val matchesDate = dateMatches.getOrPut(tx.transactionDate) { when (datePreset) {
                DateFilterPreset.ALL -> true
                DateFilterPreset.TODAY -> tx.transactionDate == today.format(DateTimeFormatter.ISO_LOCAL_DATE)
                DateFilterPreset.LAST_7_DAYS -> {
                    val txDate = try { LocalDate.parse(tx.transactionDate) } catch (_: Exception) { null }
                    txDate != null && !txDate.isBefore(today.minusDays(6)) && !txDate.isAfter(today)
                }
                DateFilterPreset.THIS_MONTH -> {
                    val txDate = try { LocalDate.parse(tx.transactionDate) } catch (_: Exception) { null }
                    txDate != null && txDate.year == today.year && txDate.monthValue == today.monthValue
                }
                DateFilterPreset.LAST_MONTH -> {
                    val txDate = try { LocalDate.parse(tx.transactionDate) } catch (_: Exception) { null }
                    val lastM = today.minusMonths(1)
                    txDate != null && txDate.year == lastM.year && txDate.monthValue == lastM.monthValue
                }
                DateFilterPreset.CUSTOM -> {
                    val txDate = try { LocalDate.parse(tx.transactionDate) } catch (_: Exception) { null }
                    if (txDate == null) false
                    else {
                        val afterStart = startDate == null || !txDate.isBefore(startDate)
                        val beforeEnd = endDate == null || !txDate.isAfter(endDate)
                        afterStart && beforeEnd
                    }
                }
            } }

            matchesQuery && matchesType && matchesCat && matchesAsset && matchesInput && matchesMinAmt && matchesMaxAmt && matchesDate
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val transactionDays = filteredTransactionsTab.map(::groupTransactionDays)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- ACTIONS ---

    // Prevent duplicate rapid submissions
    private var isSubmitting = false

    fun checkBalanceSufficiency(assetId: String, amount: Long): Boolean {
        return repository.checkBalanceSufficiency(assetId, amount)
    }

    fun saveTransaction(tx: Transaction, onComplete: ((Boolean) -> Unit)? = null) {
        if (isSubmitting) return
        isSubmitting = true

        rememberLastUsed(tx.type, tx.categoryId, tx.assetId, tx.destinationAssetId)

        viewModelScope.launch {
            val res = repository.addTransaction(tx)
            isSubmitting = false
            if (res.isSuccess) {
                onComplete?.invoke(true)
                showSnackbar(
                    message = "Transaksi disimpan.",
                    actionLabel = "BATALKAN",
                    onAction = {
                        viewModelScope.launch {
                            repository.deleteTransaction(tx.id)
                        }
                    }
                )
            } else {
                showSnackbar(res.exceptionOrNull()?.message ?: "Gagal menyimpan transaksi")
                onComplete?.invoke(false)
            }
        }
    }

    fun updateTransaction(tx: Transaction, previousTx: Transaction? = null, onComplete: ((Boolean) -> Unit)? = null) {
        if (isSubmitting) return
        isSubmitting = true

        rememberLastUsed(tx.type, tx.categoryId, tx.assetId, tx.destinationAssetId)

        viewModelScope.launch {
            val res = repository.updateTransaction(tx)
            isSubmitting = false
            if (res.isSuccess) {
                selectedTransactionForDetail.value = null
                editingTransaction.value = null
                onComplete?.invoke(true)
                if (previousTx != null) {
                    showSnackbar(
                        message = "Transaksi diperbarui.",
                        actionLabel = "BATALKAN",
                        onAction = {
                            viewModelScope.launch {
                                repository.updateTransaction(previousTx)
                            }
                        }
                    )
                } else {
                    showSnackbar("Transaksi diperbarui.")
                }
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
                    actionLabel = "URUNGKAN",
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

    // --- RECALCULATE, BACKUP & DATA RESET METHODS ---

    fun recalculateBalances(onComplete: ((LedgerRecalculateResult) -> Unit)? = null) {
        viewModelScope.launch {
            val res = repository.recalculateBalances()
            if (res.isSuccess) {
                val report = res.getOrThrow()
                if (report.discrepanciesFound > 0) {
                    showSnackbar("Pemeriksaan selesai: ${report.discrepanciesFound} selisih saldo aset diperbaiki otomatis.")
                } else {
                    showSnackbar("Seluruh saldo aset sudah konsisten 100% dengan riwayat transaksi.")
                }
                onComplete?.invoke(report)
            } else {
                showSnackbar("Gagal menghitung ulang saldo: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    suspend fun createBackupJson(): Result<String> {
        return repository.createBackupJson()
    }

    fun validateBackupJson(jsonString: String): BackupValidationResult {
        return repository.validateBackupJson(jsonString)
    }

    fun restoreBackup(data: JSONObject, onComplete: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val res = repository.restoreFromJson(data)
            if (res.isSuccess) {
                val now = System.currentTimeMillis()
                prefs.setLastRestoreTime(now)
                lastRestoreTime.value = now
                showSnackbar("Data cadangan berhasil dipulihkan.")
            } else {
                showSnackbar(res.exceptionOrNull()?.message ?: "Gagal memulihkan cadangan.")
            }
            onComplete(res)
        }
    }

    fun deleteSampleData() {
        viewModelScope.launch {
            val res = repository.deleteSampleData()
            if (res.isSuccess) {
                showSnackbar("Data contoh berhasil dihapus.")
            } else {
                showSnackbar(res.exceptionOrNull()?.message ?: "Gagal menghapus data contoh")
            }
        }
    }

    fun deleteAllUserData(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val res = repository.deleteAllUserData()
            if (res.isSuccess) {
                prefs.setInitialSetupCompleted(false)
                isInitialSetupCompleted.value = false
                dismissAppTour()
                showSnackbar("Semua data transaksi dan aset berhasil dihapus.")
                onComplete?.invoke()
            } else {
                showSnackbar(res.exceptionOrNull()?.message ?: "Gagal menghapus data")
            }
        }
    }

    fun completeInitialSetup(firstAssetName: String, assetType: String, initialBalance: Long, onComplete: () -> Unit) {
        viewModelScope.launch {
            val newAsset = Asset(
                id = "asset_${UUID.randomUUID()}",
                name = firstAssetName.ifBlank { "Tunai (Dompet)" },
                type = assetType,
                openingBalance = initialBalance,
                currentBalance = initialBalance,
                isDefault = true,
                isActive = true
            )
            val res = repository.addAsset(newAsset)
            if (res.isSuccess) {
                prefs.setInitialSetupCompleted(true)
                prefs.setAppTourPending(true)
                isAppTourPending.value = true
                isInitialSetupCompleted.value = true
                showSnackbar("Aset pertama berhasil dibuat!")
                onComplete()
            } else {
                showSnackbar(res.exceptionOrNull()?.message ?: "Gagal membuat aset")
            }
        }
    }

    fun saveAsset(
        asset: Asset,
        isNew: Boolean,
        newOpeningBalance: Long? = null,
        onComplete: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            if (!isNew && newOpeningBalance != null && newOpeningBalance != asset.openingBalance) {
                val openBalRes = repository.updateAssetOpeningBalance(asset.id, newOpeningBalance)
                if (openBalRes.isFailure) {
                    showSnackbar(openBalRes.exceptionOrNull()?.message ?: "Gagal mengubah saldo awal aset")
                    return@launch
                }
            }

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
            val content = withContext(Dispatchers.IO) { repository.generateCsvContent(list) }
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
                val content = withContext(Dispatchers.IO) { repository.generateCsvContent(list) }
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
                withContext(Dispatchers.IO) { file.writeText(content, Charsets.UTF_8) }

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

    fun exportBackupFile(
        context: Context,
        onSuccess: (File, Intent, String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val res = createBackupJson()
                if (res.isFailure) {
                    onError(res.exceptionOrNull()?.message ?: "Gagal membuat cadangan JSON")
                    return@launch
                }
                val jsonString = res.getOrThrow()
                val backupsDir = File(context.cacheDir, "backups")
                if (!backupsDir.exists()) {
                    backupsDir.mkdirs()
                }

                val fileName = BackupManager.generateBackupFileName()
                val file = File(backupsDir, fileName)
                withContext(Dispatchers.IO) {
                    file.writeText(jsonString, Charsets.UTF_8)
                }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Cadangan Data Duit Aing")
                    putExtra(Intent.EXTRA_TEXT, "File cadangan data keuangan Duit Aing ($fileName).")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                onSuccess(file, sendIntent, jsonString)
            } catch (e: Exception) {
                onError(e.message ?: "Gagal membuat berkas cadangan")
            }
        }
    }

    fun exportBackupToUri(
        uri: Uri,
        context: Context,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val res = createBackupJson()
                if (res.isFailure) {
                    onError(res.exceptionOrNull()?.message ?: "Gagal membuat cadangan JSON")
                    return@launch
                }
                val jsonString = res.getOrThrow()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(jsonString.toByteArray(Charsets.UTF_8))
                        output.flush()
                    } ?: throw IllegalStateException("Tidak dapat membuka berkas untuk penulisan")
                }
                val now = System.currentTimeMillis()
                prefs.setBackupSaved("JSON", now)
                lastBackupFormat.value = "JSON"
                lastBackupTime.value = now
                onSuccess(jsonString)
            } catch (e: Exception) {
                onError(e.message ?: "Gagal menyimpan cadangan ke penyimpanan")
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
        receiveAssetId: String? = null,
        direction: String = "debt"
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
                receiveAssetId = receiveAssetId,
                direction = direction
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

    // --- TRANSAKSI BERULANG (4A) ---
    fun refreshUpcomingBills() {
        upcomingRecurringBills.value = RecurringSchedulerService.getUpcomingBills(
            repository, recurringTransactions.value, daysAhead = 7
        )
    }

    fun previewCsv(content: String, callback: (Result<com.cashflow.app.data.importer.CsvPreview>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { com.cashflow.app.data.importer.CsvImport.preview(content, assets.value, categories.value, transactions.value) } }
            callback(result)
        }
    }

    fun importCsv(content: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            repository.importCsv(content).onSuccess { showSnackbar("$it transaksi diimpor. Duplikat dilewati."); callback(true) }
                .onFailure { showSnackbar(it.message ?: "Impor gagal"); callback(false) }
        }
    }

    fun processRecurringBills() {
        viewModelScope.launch {
            val newlyCreated = RecurringSchedulerService.processDueRecurringSchedules(repository)
            if (newlyCreated > 0) {
                showSnackbar("$newlyCreated tagihan berulang berhasil dicatat otomatis.")
                repository.refreshAll()
                refreshUpcomingBills()
            }
        }
    }

    fun skipUpcomingBill(recurringId: String, occurrenceDate: String) {
        viewModelScope.launch {
            runCatching { RecurringSchedulerService.skipRecurringOccurrence(repository, recurringId, occurrenceDate) }
                .onSuccess { showSnackbar("Jadwal $occurrenceDate berhasil dilewati.") }
                .onFailure { showSnackbar(it.message ?: "Gagal melewati jadwal") }
            refreshUpcomingBills()
        }
    }

    fun recordUpcomingBill(recurringId: String, occurrenceDate: String) {
        viewModelScope.launch {
            repository.executeRecurringOccurrence(recurringId, occurrenceDate, confirmed = true)
                .onSuccess { showSnackbar(if (it == null) "Jadwal sudah diproses." else "Transaksi berhasil dicatat.") }
                .onFailure { showSnackbar(it.message ?: "Gagal mencatat jadwal") }
            refreshUpcomingBills()
        }
    }

    fun saveRecurringTransaction(recurring: RecurringTransaction, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.saveRecurringTransaction(recurring)
            if (res.isSuccess) {
                showSnackbar("Jadwal berulang \"${recurring.name}\" berhasil disimpan.")
                refreshUpcomingBills()
                onResult(true)
            } else {
                showSnackbar("Gagal: ${res.exceptionOrNull()?.message}")
                onResult(false)
            }
        }
    }

    fun deleteRecurringTransaction(id: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.deleteRecurringTransaction(id)
            if (res.isSuccess) {
                showSnackbar("Jadwal berulang berhasil dihapus.")
                refreshUpcomingBills()
                onResult(true)
            } else {
                showSnackbar("Gagal menghapus.")
                onResult(false)
            }
        }
    }

    // --- INSIGHT & AI (4D) ---
    fun getGeminiPayloadPreview(): JSONObject {
        val now = LocalDate.now()
        val currentPrefix = now.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        val prevPrefix = now.minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"))
        return FinancialInsightService.buildGeminiAggregatePayload(
            transactions = transactions.value,
            categories = categories.value,
            currentMonthPrefix = currentPrefix,
            previousMonthPrefix = prevPrefix
        )
    }

    fun requestGeminiInsight(apiKey: String, onResult: (Result<String>) -> Unit) {
        if (!isAiInsightsEnabled.value) {
            onResult(Result.failure(IllegalStateException("Aktifkan analisis online di Pengaturan sebelum mengirim ringkasan ke Gemini.")))
            return
        }
        viewModelScope.launch {
            val now = LocalDate.now()
            val currentPrefix = now.format(DateTimeFormatter.ofPattern("yyyy-MM"))
            val prevPrefix = now.minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"))
            val res = FinancialInsightService.fetchGeminiInsight(
                apiKey = apiKey,
                transactions = transactions.value,
                categories = categories.value,
                currentMonthPrefix = currentPrefix,
                previousMonthPrefix = prevPrefix
            )
            onResult(res)
        }
    }

    // --- FINANCIAL AI CHAT ASSISTANT ---
    val isChatOpen = MutableStateFlow(false)
    val isChatLoading = MutableStateFlow(false)
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = ChatSender.ASSISTANT,
                text = "Halo! Saya Vina, asisten finansial pribadi Anda di Duit Aing. Tanyakan apa saja seputar kondisi keuangan Anda, misalnya tren arus kas bulan lalu, evaluasi anggaran, pos belanja terbesar, atau simulasi kelayakan pembelian barang."
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    fun openChat() {
        isChatOpen.value = true
    }

    fun closeChat() {
        isChatOpen.value = false
    }

    fun clearChat() {
        _chatMessages.value = listOf(
            ChatMessage(
                sender = ChatSender.ASSISTANT,
                text = "Riwayat percakapan telah dibersihkan. Halo, saya Vina! Ada hal lain yang ingin Anda konsultasikan seputar keuangan Anda?"
            )
        )
    }

    fun sendChatMessage(userText: String) {
        val cleanText = userText.trim()
        if (cleanText.isBlank() || isChatLoading.value) return

        val userMsg = ChatMessage(sender = ChatSender.USER, text = cleanText)
        _chatMessages.value = _chatMessages.value + userMsg
        isChatLoading.value = true

        viewModelScope.launch {
            try {
                val txSnapshot = transactions.value
                val catSnapshot = categories.value
                val chatContext = withContext(Dispatchers.Default) {
                    val now = LocalDate.now()
                    val curMonthPrefix = now.format(DateTimeFormatter.ofPattern("yyyy-MM"))
                    val prevMonthPrefix = now.minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"))
                    val stats = FinancialInsightService.computeMonthComparison(
                        transactions = txSnapshot,
                        categories = catSnapshot,
                        currentMonthDate = now
                    )

                    FinancialChatContext(
                        userName = userName.value,
                        currentDate = now.toString(),
                        totalLiquidBalance = activeAssets.value.sumOf { it.currentBalance },
                        assets = activeAssets.value,
                        currentMonthStats = stats,
                        prevMonthPrefix = prevMonthPrefix,
                        currentMonthPrefix = curMonthPrefix,
                        topCategories = stats.categoryDeltas,
                        budgets = budgets.value,
                        debts = debts.value,
                        goals = goals.value,
                        transactions = txSnapshot,
                        categories = catSnapshot
                    )
                }

                val responseRes = FinancialInsightService.sendChatMessage(
                    apiKey = geminiApiKey.value.takeIf { isAiInsightsEnabled.value },
                    conversationHistory = _chatMessages.value,
                    context = chatContext
                )

                val replyText = responseRes.getOrDefault("Maaf, terjadi kendala saat memproses jawaban. Silakan coba sesaat lagi.")
                _chatMessages.value = _chatMessages.value + ChatMessage(sender = ChatSender.ASSISTANT, text = replyText)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    sender = ChatSender.ASSISTANT,
                    text = "Terjadi kesalahan: ${e.message}",
                    isError = true
                )
            } finally {
                isChatLoading.value = false
            }
        }
    }

    // --- SESUAIKAN SALDO ASET (4E) ---
    fun adjustAssetBalance(assetId: String, targetBalance: Long, note: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val asset = assets.value.firstOrNull { it.id == assetId }
            if (asset == null) {
                showSnackbar("Aset tidak ditemukan")
                onResult(false)
                return@launch
            }
            val diff = targetBalance - asset.currentBalance
            if (diff == 0L) {
                showSnackbar("Saldo sudah sesuai")
                onResult(true)
                return@launch
            }
            val isPositive = diff > 0
            val adjustmentTx = Transaction(
                id = "adj_${UUID.randomUUID()}",
                type = TransactionType.ADJUSTMENT.value,
                amount = if (isPositive) diff else -diff,
                description = if (note.isNotBlank()) note else "Penyesuaian Saldo (${if (isPositive) "+${Formatters.formatRupiah(diff)}" else Formatters.formatRupiah(diff)})",
                assetId = assetId,
                transactionDate = LocalDate.now().toString(),
                transactionTime = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                note = if (note.isNotBlank()) note else "Penyesuaian saldo manual dari ${Formatters.formatRupiah(asset.currentBalance)} ke ${Formatters.formatRupiah(targetBalance)}",
                isAdjustment = true,
                adjustmentDirection = if (isPositive) 1 else -1
            )
            val saveRes = repository.addTransaction(adjustmentTx)
            if (saveRes.isSuccess) {
                showSnackbar("Saldo ${asset.name} disesuaikan ke ${Formatters.formatRupiah(targetBalance)}")
                onResult(true)
            } else {
                showSnackbar("Gagal: ${saveRes.exceptionOrNull()?.message}")
                onResult(false)
            }
        }
    }

    // --- ENCRYPTED & ZIP BACKUP (4F) ---
    fun savePreparedBackupToUri(file: File, uri: Uri, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val output = getApplication<Application>().contentResolver.openOutputStream(uri)
                        ?: throw java.io.IOException("Penyimpanan tujuan tidak dapat dibuka.")
                    output.use { out -> file.inputStream().use { it.copyTo(out) } }
                    Unit
                }
            }
            if (result.isSuccess) {
                val now = System.currentTimeMillis()
                val format = if (file.extension == "enc") "Terenkripsi (.enc)" else "ZIP + struk"
                prefs.setBackupSaved(format, now)
                lastBackupFormat.value = format
                lastBackupTime.value = now
            }
            onResult(result)
        }
    }

    fun exportEncryptedBackupFile(password: String, onResult: (Result<File>) -> Unit) {
        viewModelScope.launch {
            try {
                val jsonRes = repository.createBackupJson()
                if (jsonRes.isFailure) {
                    onResult(Result.failure(jsonRes.exceptionOrNull() ?: Exception("Gagal membuat JSON")))
                    return@launch
                }
                val outFile = withContext(Dispatchers.IO) {
                    val encryptedBytes = CloudBackupService.encryptBackupPayload(jsonRes.getOrThrow(), password)
                    File(getApplication<Application>().cacheDir, BackupManager.generateBackupFileName().replace(".json", ".enc"))
                        .apply { writeBytes(encryptedBytes) }
                }
                onResult(Result.success(outFile))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    fun readEncryptedBackupFile(file: File, password: String, onResult: (Result<BackupValidationResult.Valid>) -> Unit) {
        viewModelScope.launch {
            try {
                val validation = withContext(Dispatchers.IO) {
                    val encryptedBytes = file.inputStream().use { BackupManager.readBackupBytes(it) }
                    BackupManager.validateBackupJson(CloudBackupService.decryptBackupPayload(encryptedBytes, password))
                }
                if (validation !is BackupValidationResult.Valid) {
                    onResult(Result.failure(IllegalArgumentException((validation as BackupValidationResult.Invalid).reason)))
                    return@launch
                }
                onResult(Result.success(validation))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    fun exportZipBackupFile(onResult: (Result<File>) -> Unit) {
        viewModelScope.launch {
            try {
                val fileName = BackupManager.generateBackupFileName().replace(".json", ".zip")
                val outFile = File(getApplication<Application>().cacheDir, fileName)
                val res = repository.createBackupZip(outFile)
                if (res.isSuccess) {
                    onResult(Result.success(outFile))
                } else {
                    onResult(Result.failure(res.exceptionOrNull() ?: Exception("Gagal membuat ZIP")))
                }
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    fun restoreZipBackupFile(file: File, onResult: (Result<BackupSummary>) -> Unit) {
        viewModelScope.launch {
            try {
                val res = repository.restoreFromZip(file)
                if (res.isSuccess) {
                    val now = System.currentTimeMillis()
                    prefs.setLastRestoreTime(now)
                    lastRestoreTime.value = now
                }
                onResult(res)
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    fun seedSyntheticTransactions(count: Int = 10_000, onComplete: (Result<Long>) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.seedSyntheticTransactions(count)
            onComplete(res)
        }
    }

    fun clearSyntheticTransactions(onComplete: (Result<Int>) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.clearSyntheticTransactions()
            onComplete(res)
        }
    }

    companion object {
        const val ACTION_RECORD_VOICE = "com.cashflow.app.ACTION_RECORD_VOICE"
        const val ACTION_ADD_TRANSACTION = "com.cashflow.app.ACTION_ADD_TRANSACTION"
        const val ACTION_ADD_EXPENSE = "com.cashflow.app.ACTION_ADD_EXPENSE"
        const val ACTION_ADD_INCOME = "com.cashflow.app.ACTION_ADD_INCOME"
    }
}
