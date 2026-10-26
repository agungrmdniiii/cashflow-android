package com.cashflow.app

import androidx.test.platform.app.InstrumentationRegistry
import com.cashflow.app.data.db.DatabaseHelper
import com.cashflow.app.data.model.Asset
import com.cashflow.app.data.model.FinancialGoal
import com.cashflow.app.data.model.Budget
import com.cashflow.app.data.model.Transaction
import com.cashflow.app.data.model.RecurringTransaction
import com.cashflow.app.data.backup.BackupValidationResult
import com.cashflow.app.data.backup.BackupManager
import com.cashflow.app.data.service.RecurringSchedulerService
import com.cashflow.app.data.repository.CashflowRepository
import com.cashflow.app.data.repository.LedgerService
import com.cashflow.app.data.preferences.PreferenceManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

/** Runs against SQLite on the isolated audit emulator, never the user's device. */
class AuditDatabaseRegressionTest {
    private lateinit var repository: CashflowRepository

    @Test fun undoneDebtPaymentRetainsItsLinkThroughAnotherEditAndDelete() = runBlocking {
        for (direction in listOf("debt", "receivable")) {
            repository.addDebt("Undo $direction", "Audit", 400_000L, "2026-12-31", direction = direction).getOrThrow()
            val debt = repository.debts.value.first { it.title == "Undo $direction" }
            val before = repository.assets.value.single().currentBalance
            repository.recordDebtPayment(debt.id, 100_000L, "audit_cash").getOrThrow()
            val payment = repository.transactions.value.first { it.debtId == debt.id }
            repository.deleteTransaction(payment.id).getOrThrow()
            repository.addTransaction(payment).getOrThrow() // Actual delete snackbar undo path.
            val restored = repository.transactions.value.first { it.id == payment.id }
            assertEquals("Undo must retain the loan association", debt.id, restored.debtId)
            repository.updateTransaction(restored.copy(amount = 150_000L)).getOrThrow()
            assertEquals(150_000L, repository.debts.value.first { it.id == debt.id }.paidAmount)
            assertEquals(before + if (direction == "debt") -150_000L else 150_000L, repository.assets.value.single().currentBalance)
            repository.deleteTransaction(payment.id).getOrThrow()
            assertEquals(0L, repository.debts.value.first { it.id == debt.id }.paidAmount)
            assertEquals(before, repository.assets.value.single().currentBalance)
        }
    }

    @Test fun duplicateConcurrentSubmissionsCommitExactlyOneRealLedgerEntry() = runBlocking {
        val tx = Transaction("audit_same_submit", "income", 100_000L, "Income", assetId = "audit_cash", transactionDate = LocalDate.now().toString())
        val start = CompletableDeferred<Unit>()
        val results = (1..12).map { async(Dispatchers.Default) { start.await(); repository.addTransaction(tx) } }
        start.complete(Unit)
        assertEquals(1, results.awaitAll().count { it.isSuccess })
        assertEquals(1, repository.transactions.value.size)
        assertEquals(1_100_000L, repository.assets.value.single().currentBalance)
        val invalid = tx.copy(assetId = "missing_account", amount = 300_000L)
        assertTrue(repository.updateTransaction(invalid).isFailure)
        assertEquals(1_100_000L, repository.assets.value.single().currentBalance)
        assertEquals(tx.amount, repository.transactions.value.single().amount)
        assertEquals("audit_cash", repository.transactions.value.single().assetId)
    }

    @Test fun realTransactionLifecycleMatchesHomeReportsWidgetAndOfflineVina() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as CashflowApplication
        val holder = androidx.lifecycle.ViewModelStore()
        lateinit var model: com.cashflow.app.ui.CashflowViewModel
        instrumentation.runOnMainSync {
            model = androidx.lifecycle.ViewModelProvider(holder,
                androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(app))[com.cashflow.app.ui.CashflowViewModel::class.java]
            model.setReportPeriod(com.cashflow.app.data.model.PeriodFilter.BULAN_INI)
        }
        val today = LocalDate.now()
        repository.addAsset(Asset("audit_bank", "Audit Bank", "Bank", 200_000L, 200_000L)).getOrThrow()
        repository.addAsset(Asset("audit_archived", "Archived", "Bank", 900_000L, 900_000L, isActive = false)).getOrThrow()
        suspend fun verify(cash: Long, bank: Long, income: Long, expense: Long) {
            assertEquals(cash, repository.assets.value.first { it.id == "audit_cash" }.currentBalance)
            assertEquals(bank, repository.assets.value.first { it.id == "audit_bank" }.currentBalance)
            val db = DatabaseHelper(context)
            try { assertEquals(0, LedgerService.recalculateAllBalances(db.readableDatabase, false).discrepanciesFound) }
            finally { db.close() }
            app.repository.reloadForBackgroundCheck()
            kotlinx.coroutines.withTimeout(5000) {
                model.totalWealth.first { it == cash + bank }
                model.periodIncome.first { it == income }
                model.periodExpense.first { it == expense }
                val snapshot = model.reportSnapshot.first {
                    it.summary.income == income && it.summary.expense == expense &&
                        it.transactions.map { row -> row.id }.toSet() == repository.transactions.value.map { row -> row.id }.toSet()
                }
                assertEquals(income, snapshot.chart.sumOf { it.income })
                assertEquals(expense, snapshot.chart.sumOf { it.expense })
            }
            val remote = com.cashflow.app.widget.DuitAingQuickWidget.buildWidgetViews(context, R.layout.widget_duit_aing_summary)
            instrumentation.runOnMainSync {
                val view = remote.apply(context, null)
                assertEquals(com.cashflow.app.ui.components.Formatters.formatRupiah(cash + bank), view.findViewById<android.widget.TextView>(R.id.tv_widget_balance).text.toString())
                assertEquals(com.cashflow.app.ui.components.Formatters.formatRupiah(income), view.findViewById<android.widget.TextView>(R.id.tv_widget_income).text.toString())
                assertEquals(com.cashflow.app.ui.components.Formatters.formatRupiah(expense), view.findViewById<android.widget.TextView>(R.id.tv_widget_expense).text.toString())
            }
        }
        val incoming = Transaction("audit_income", "income", 100_000L, "Income", assetId = "audit_cash", transactionDate = today.toString())
        val spending = incoming.copy(id = "audit_expense", type = "expense", amount = 40_000L)
        val transfer = incoming.copy(id = "audit_transfer", type = "transfer", amount = 200_000L, destinationAssetId = "audit_bank")
        val adjustment = incoming.copy(id = "audit_adjustment", type = "adjustment", amount = 10_000L, isAdjustment = true, adjustmentDirection = -1)
        try {
            listOf(incoming, spending, transfer, adjustment).forEach { repository.addTransaction(it).getOrThrow() }
            verify(850_000L, 400_000L, 100_000L, 40_000L)
            repository.updateTransaction(spending.copy(amount = 70_000L, assetId = "audit_bank")).getOrThrow()
            verify(890_000L, 330_000L, 100_000L, 70_000L)
            val stats = com.cashflow.app.data.service.FinancialInsightService.computeMonthComparison(repository.transactions.value, repository.categories.value, today)
            val chatContext = com.cashflow.app.data.service.FinancialChatContext("", today.toString(), 1_220_000L,
                repository.assets.value.filter { it.isActive }, stats, today.minusMonths(1).toString().take(7), today.toString().take(7),
                stats.categoryDeltas, repository.budgets.value, repository.debts.value, repository.goals.value,
                repository.transactions.value, repository.categories.value)
            val answer = com.cashflow.app.data.service.FinancialInsightService.sendChatMessage(listOf(
                com.cashflow.app.data.model.ChatMessage(text = "berapa total pengeluaran saya bulan ini", sender = com.cashflow.app.data.model.ChatSender.USER)), chatContext).getOrThrow()
            assertTrue(answer.contains(com.cashflow.app.ui.components.Formatters.formatRupiah(70_000L)))
            repository.updateTransaction(transfer.copy(amount = 100_000L)).getOrThrow()
            verify(990_000L, 230_000L, 100_000L, 70_000L)
            repository.deleteTransaction(incoming.id).getOrThrow()
            verify(890_000L, 230_000L, 0L, 70_000L)
            repository.deleteTransaction(adjustment.id).getOrThrow()
            verify(900_000L, 230_000L, 0L, 70_000L)
            repository.deleteTransaction(spending.id).getOrThrow()
            repository.deleteTransaction(transfer.id).getOrThrow()
            verify(1_000_000L, 200_000L, 0L, 0L)
            assertTrue(repository.transactions.value.isEmpty())
        } finally { instrumentation.runOnMainSync { holder.clear() } }
    }

    @Test fun fullySeededCategoriesCanBeCheckedWithoutWriting() {
        val helper = DatabaseHelper(InstrumentationRegistry.getInstrumentation().targetContext)
        val db = helper.writableDatabase
        db.execSQL("PRAGMA query_only = ON")
        try {
            helper.seedMissingDefaultCategories(db)
        } finally {
            db.execSQL("PRAGMA query_only = OFF")
            helper.close()
        }
    }

    @Test fun defaultLabelsBecomeShortWithoutRenamingCustomCategories() {
        val helper = DatabaseHelper(InstrumentationRegistry.getInstrumentation().targetContext)
        val db = helper.writableDatabase
        db.execSQL("UPDATE categories SET name = 'Makanan & Minuman' WHERE id = 'cat_makanan'")
        db.execSQL("UPDATE categories SET name = 'Nama saya' WHERE id = 'cat_transportasi'")
        helper.seedMissingDefaultCategories(db)
        db.rawQuery("SELECT name FROM categories WHERE id = 'cat_makanan'", null).use { it.moveToFirst(); assertEquals("Makan", it.getString(0)) }
        db.rawQuery("SELECT name FROM categories WHERE id = 'cat_transportasi'", null).use { it.moveToFirst(); assertEquals("Nama saya", it.getString(0)) }
        assertTrue(helper.getDefaultCategoriesList().all { it.name.length <= 14 })
    }

    @Test fun expandedCategoriesSeedOnceWithoutOverwritingExistingNames() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val helper = DatabaseHelper(context)
        val db = helper.writableDatabase
        db.execSQL("UPDATE categories SET name = 'Nama pribadi' WHERE id = 'cat_makanan'")
        helper.seedMissingDefaultCategories(db)
        helper.seedMissingDefaultCategories(db)
        db.rawQuery("SELECT type, COUNT(*) FROM categories WHERE is_active = 1 GROUP BY type", null).use {
            while (it.moveToNext()) assertTrue("Kategori ${it.getString(0)} belum diperbanyak", it.getInt(1) >= if (it.getString(0) == "expense") 36 else 20)
        }
        db.rawQuery("SELECT name FROM categories WHERE id = 'cat_makanan'", null).use { it.moveToFirst(); assertEquals("Nama pribadi", it.getString(0)) }
        db.execSQL("UPDATE categories SET name = 'Makanan & Minuman' WHERE id = 'cat_makanan'")
    }

    @Test fun zipImportStreamsPhotoArchivesLargerThanTheJsonLimit() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "large-archive-input")
        val target = File(context.cacheDir, "large-archive-output")
        try {
            java.io.RandomAccessFile(source, "rw").use { it.setLength(33L * 1024 * 1024) }
            source.inputStream().use { BackupManager.copyBackupZip(it, target) }
            assertEquals(source.length(), target.length())
        } finally { source.delete(); target.delete() }
    }

    @Test fun reminderOccurrencesCanBeConfirmedOrSkippedOnlyOnce() = runBlocking {
        val today = LocalDate.now()
        val rec = RecurringTransaction(id = "remind_test", name = "Bill", type = "expense", amount = 10_000L,
            assetId = "audit_cash", frequency = "daily", startDate = today.toString(), mode = "remind")
        repository.saveRecurringTransaction(rec).getOrThrow()
        assertTrue(repository.executeRecurringOccurrence(rec.id, today.toString()).isFailure)
        assertNotNull(repository.executeRecurringOccurrence(rec.id, today.toString(), true).getOrThrow())
        assertNull(repository.executeRecurringOccurrence(rec.id, today.toString(), true).getOrThrow())
        RecurringSchedulerService.skipRecurringOccurrence(repository, rec.id, today.plusDays(1).toString())
        RecurringSchedulerService.skipRecurringOccurrence(repository, rec.id, today.plusDays(1).toString())
        assertEquals(2, repository.recurringTransactions.value.single().occurrencesDone)
        assertEquals(1, repository.transactions.value.size)
        assertEquals(990_000L, repository.assets.value.single().currentBalance)
        repository.saveRecurringTransaction(rec.copy(name = "Edited after execution")).getOrThrow()
        assertEquals(2, repository.recurringTransactions.value.single().occurrencesDone)
    }

    @Test fun zipRoundTripRestoresReferencedReceiptUnderANewName() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val receipts = File(context.filesDir, "receipts").apply { mkdirs() }
        val source = File(receipts, "referenced-test.jpg").apply { writeText("receipt-data") }
        File(receipts, "orphan-test.jpg").writeText("orphan")
        repository.addTransaction(Transaction("receipt_tx", "expense", 50L, "Receipt", assetId = "audit_cash",
            transactionDate = LocalDate.now().toString(), attachment = source.path)).getOrThrow()
        val zip = File(context.cacheDir, "roundtrip-test.zip")
        repository.createBackupZip(zip).getOrThrow()
        java.util.zip.ZipFile(zip).use { assertNull(it.getEntry("receipts/orphan-test.jpg")) }
        repository.restoreFromZip(zip).getOrThrow()
        val restored = File(repository.transactions.value.single().attachment!!)
        assertNotEquals(source.path, restored.path)
        assertEquals("receipt-data", restored.readText())
        assertEquals("receipt-data", source.readText())
        assertEquals(999_950L, repository.assets.value.single().currentBalance)
    }

    @Test fun editingPaymentCannotExceedRemainingPrincipal() = runBlocking {
        repository.addDebt("Audit", "Lender", 100_000L, "2026-12-31").getOrThrow()
        repository.recordDebtPayment(repository.debts.value.single().id, 40_000L, "audit_cash").getOrThrow()
        val payment = repository.transactions.value.single()
        assertTrue(repository.updateTransaction(payment.copy(amount = 120_000L)).isFailure)
        assertEquals(40_000L, repository.debts.value.single().paidAmount)
        assertEquals(960_000L, repository.assets.value.single().currentBalance)
    }

    @Test fun spentAllocationCannotBeDeletedAndSilentlyClamped() = runBlocking {
        repository.addGoal(FinancialGoal("audit_goal", "Goal", 500_000L, 0L, "2026-12-31", "audit_cash")).getOrThrow()
        repository.depositToGoal("audit_goal", 100_000L, "audit_cash").getOrThrow()
        val allocation = repository.transactions.value.single()
        repository.withdrawFromGoal("audit_goal", 80_000L, "audit_cash").getOrThrow()
        assertTrue(repository.deleteTransaction(allocation.id).isFailure)
        assertEquals(20_000L, repository.goals.value.single().currentAmount)
        assertEquals(2, repository.transactions.value.size)
    }

    @Test fun csvImportIsAtomicAndIdempotent() = runBlocking {
        val tx = Transaction("csv_source", "expense", 30_000L, "Kopi, \"pagi\"\nKedua", assetId = "audit_cash", transactionDate = "2026-10-16")
        val csv = repository.generateCsvContent(listOf(tx))
        assertEquals(1, repository.importCsv(csv).getOrThrow())
        assertEquals(970_000L, repository.assets.value.single().currentBalance)
        assertEquals(0, repository.importCsv(csv).getOrThrow())
        val bad = csv + "2026-10-16,12:00,Bad,Pengeluaran,-,Missing,-,50,,manual\n"
        assertTrue(repository.importCsv(bad).isFailure)
        assertEquals(1, repository.transactions.value.size)
        assertEquals(970_000L, repository.assets.value.single().currentBalance)
    }

    @Test fun receivablePaymentsPreserveNetWorthAndUndoCorrectly() = runBlocking {
        repository.addDebt("Piutang", "Audit borrower", 400_000L, "2026-12-31", receiveAssetId = "audit_cash", direction = "receivable").getOrThrow()
        val debt = repository.debts.value.single()
        assertEquals("receivable", debt.direction)
        assertEquals(600_000L, repository.assets.value.single().currentBalance)
        assertEquals("loan_given", repository.transactions.value.single().type)
        repository.recordDebtPayment(debt.id, 100_000L, "audit_cash").getOrThrow()
        assertEquals(700_000L, repository.assets.value.single().currentBalance)
        assertEquals(300_000L, repository.debts.value.single().remainingAmount)
        val payment = repository.transactions.value.first { it.type == "receivable_payment" }
        repository.deleteTransaction(payment.id).getOrThrow()
        assertEquals(600_000L, repository.assets.value.single().currentBalance)
        assertEquals(400_000L, repository.debts.value.single().remainingAmount)
        assertTrue(repository.recordDebtPayment(debt.id, 500_000L, "audit_cash").isFailure)
        val db = DatabaseHelper(InstrumentationRegistry.getInstrumentation().targetContext).writableDatabase
        assertEquals(0, LedgerService.recalculateAllBalances(db, false).discrepanciesFound)
        val backup = BackupManager.createBackupJson(db)
        BackupManager.restoreBackup(db, JSONObject(backup).getJSONObject("data")).getOrThrow()
        repository.reloadForBackgroundCheck()
        assertEquals("receivable", repository.debts.value.single().direction)
    }

    @Test
    fun retiredAiCredentialsAreRemovedWithoutChangingTheVoicePreference() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val raw = context.getSharedPreferences("duit_aing_preferences", 0)
        raw.edit().putString("gemini_api_key", "audit-secret-value").commit()
        raw.edit().putBoolean("online_voice_enabled", true).commit()
        val prefs = PreferenceManager(context)
        assertFalse(raw.contains("gemini_api_key"))
        assertFalse(context.getSharedPreferences("duit_aing_credentials", 0).all.values.any { it.toString().contains("audit-secret-value") })
        assertTrue(prefs.isOnlineVoiceEnabled())
        assertFalse(raw.contains("ai_insights_enabled"))
    }

    @Test
    fun localRemindersAreScheduledWithoutExactAlarmPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        com.cashflow.app.data.service.LocalReminderJob.schedule(context)
        val scheduler = context.getSystemService(android.app.job.JobScheduler::class.java)
        val job = scheduler.getPendingJob(com.cashflow.app.data.service.LocalReminderJob.JOB_ID)
        assertNotNull(job)
        assertTrue(job!!.isPersisted)
        assertTrue(job.isPeriodic)
        assertTrue(context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().contains(android.Manifest.permission.POST_NOTIFICATIONS))
    }

    @Before
    fun resetDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("duit_aing_preferences", 0).edit().clear().commit()
        val helper = DatabaseHelper(context)
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            listOf("recurring_executions", "recurring_transactions", "transactions", "budgets",
                "financial_goals", "debts", "favorite_templates", "assets").forEach {
                db.delete(it, null, null)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            helper.close()
        }
        repository = CashflowRepository(context)
        runBlocking {
            repository.awaitInitialLoad()
            repository.addAsset(Asset(id = "audit_cash", name = "Audit Cash", type = "Tunai",
                openingBalance = 1_000_000L, currentBalance = 1_000_000L)).getOrThrow()
        }
    }

    @Test
    fun editingDebtPaymentKeepsItsLinkAndPaidAmount() = runBlocking {
        repository.addDebt("Audit Loan", "Audit lender", 500_000L, "2026-12-31").getOrThrow()
        val debt = repository.debts.value.single()
        repository.recordDebtPayment(debt.id, 100_000L, "audit_cash").getOrThrow()
        val payment = repository.transactions.value.single()
        // Reproduce the old edit form, which omitted the association when rebuilding the transaction.
        repository.updateTransaction(payment.copy(note = "Edited note", debtId = null)).getOrThrow()
        assertEquals(debt.id, repository.transactions.value.single().debtId)
        assertEquals(100_000L, repository.debts.value.single().paidAmount)
        assertEquals(900_000L, repository.assets.value.single().currentBalance)
    }

    @Test
    fun goalAllocationKeepsMoneyInItsAccountAndIsReversible() = runBlocking {
        repository.addGoal(FinancialGoal("audit_goal", "Emergency fund", 500_000L, 0L,
            "2026-12-31", "audit_cash")).getOrThrow()
        repository.depositToGoal("audit_goal", 100_000L, "audit_cash").getOrThrow()
        assertEquals(1_000_000L, repository.assets.value.single().currentBalance)
        assertEquals(100_000L, repository.goals.value.single().currentAmount)
        assertNotEquals("expense", repository.transactions.value.single().type)
        val allocation = repository.transactions.value.single()
        repository.updateTransaction(allocation.copy(note = "Edited allocation", goalId = null)).getOrThrow()
        assertEquals("audit_goal", repository.transactions.value.single().goalId)
        assertEquals(100_000L, repository.goals.value.single().currentAmount)
        repository.deleteTransaction(allocation.id).getOrThrow()
        assertEquals(0L, repository.goals.value.single().currentAmount)
        assertEquals(1_000_000L, repository.assets.value.single().currentBalance)
    }

    @Test
    fun goalAllocationRejectsNegativeAmounts() = runBlocking {
        repository.addGoal(FinancialGoal("audit_goal", "Emergency fund", 500_000L, 0L,
            "2026-12-31", "audit_cash")).getOrThrow()
        assertTrue(repository.depositToGoal("audit_goal", -1L, "audit_cash").isFailure)
        assertEquals(0L, repository.goals.value.single().currentAmount)
        assertEquals(1_000_000L, repository.assets.value.single().currentBalance)
    }

    @Test
    fun legacyGoalMigrationPreservesHistoryAndDoesNotRunTwice() = runBlocking {
        repository.addGoal(FinancialGoal("audit_goal", "Emergency fund", 500_000L, 0L,
            "2026-12-31", "audit_cash")).getOrThrow()
        repository.addTransaction(Transaction("legacy_allocation", "expense", 100_000L, "Legacy saving",
            assetId = "audit_cash", transactionDate = "2026-10-16", goalId = "audit_goal")).getOrThrow()
        val helper = DatabaseHelper(InstrumentationRegistry.getInstrumentation().targetContext)
        val db = helper.writableDatabase
        LedgerService.migrateLegacyGoalTransactions(db)
        LedgerService.migrateLegacyGoalTransactions(db)
        db.rawQuery("SELECT current_balance FROM assets WHERE id = 'audit_cash'", null).use {
            assertTrue(it.moveToFirst())
            assertEquals(1_000_000L, it.getLong(0))
        }
        db.rawQuery("SELECT type, amount, goal_id FROM transactions WHERE id = 'legacy_allocation'", null).use {
            assertTrue(it.moveToFirst())
            assertEquals("allocation", it.getString(0))
            assertEquals(100_000L, it.getLong(1))
            assertEquals("audit_goal", it.getString(2))
        }
        assertEquals(0, LedgerService.recalculateAllBalances(db, autoFix = false).discrepanciesFound)
        helper.close()
    }

    @Test
    fun weeklyBudgetExcludesPreviousWeek() = runBlocking {
        val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        repository.addTransaction(Transaction("previous_week", "expense", 10_000L, "Previous week",
            categoryId = "cat_belanja", assetId = "audit_cash", transactionDate = monday.minusDays(1).toString())).getOrThrow()
        repository.addTransaction(Transaction("this_week", "expense", 20_000L, "This week",
            categoryId = "cat_belanja", assetId = "audit_cash", transactionDate = monday.toString())).getOrThrow()
        repository.addBudget(Budget("weekly", "cat_belanja", "weekly", 100_000L)).getOrThrow()
        assertEquals(20_000L, repository.budgets.value.single().spentAmount)
        assertEquals(80_000L, repository.budgets.value.single().remainingAmount)
    }

    @Test
    fun editingBudgetDoesNotPromoteRolloverToBaseLimit() = runBlocking {
        PreferenceManager(InstrumentationRegistry.getInstrumentation().targetContext).setBudgetRolloverEnabled(true)
        repository.addBudget(Budget("monthly", "cat_belanja", "monthly", 2_000_000L,
            LocalDate.now().minusMonths(1).withDayOfMonth(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())).getOrThrow()
        val progress = repository.budgets.value.single()
        assertEquals(2_000_000L, progress.budget.targetAmount)
        assertEquals(4_000_000L, progress.remainingAmount)
        repository.updateBudget(progress.budget).getOrThrow()
        assertEquals(2_000_000L, repository.budgets.value.single().budget.targetAmount)
        assertEquals(4_000_000L, repository.budgets.value.single().remainingAmount)
    }

    @Test
    fun restoreKeepsCompletedRecurringOccurrences() = runBlocking {
        val today = LocalDate.now()
        repository.saveRecurringTransaction(RecurringTransaction(id = "audit_rec", name = "Audit subscription",
            type = "expense", amount = 10_000L, assetId = "audit_cash", startDate = today.toString())).getOrThrow()
        RecurringSchedulerService.processDueRecurringTransactions(repository, today)
        assertEquals(1, repository.transactions.value.size)
        val backup = repository.createBackupJson().getOrThrow()
        val valid = repository.validateBackupJson(backup) as BackupValidationResult.Valid
        repository.restoreFromJson(valid.parsedData).getOrThrow()
        RecurringSchedulerService.processDueRecurringTransactions(repository, today)
        assertEquals(1, repository.transactions.value.size)
        assertEquals(990_000L, repository.assets.value.single().currentBalance)
    }

    @Test
    fun concurrentRecurringProcessingCreatesOneTransaction() = runBlocking {
        val today = LocalDate.now()
        repository.saveRecurringTransaction(RecurringTransaction(id = "audit_rec", name = "Audit subscription",
            type = "expense", amount = 10_000L, assetId = "audit_cash", startDate = today.toString())).getOrThrow()
        val start = CompletableDeferred<Unit>()
        val workers = (1..8).map {
            async(Dispatchers.Default) {
                start.await()
                RecurringSchedulerService.processDueRecurringTransactions(repository, today)
            }
        }
        start.complete(Unit)
        workers.awaitAll()
        assertEquals(1, repository.transactions.value.size)
        assertEquals(990_000L, repository.assets.value.single().currentBalance)
    }

    @Test
    fun resetDeletesRecurringSchedulesAndExecutions() = runBlocking {
        repository.saveRecurringTransaction(RecurringTransaction(id = "audit_rec", name = "Audit subscription",
            type = "expense", amount = 10_000L, assetId = "audit_cash", startDate = LocalDate.now().toString())).getOrThrow()
        RecurringSchedulerService.processDueRecurringTransactions(repository)
        repository.deleteAllUserData().getOrThrow()
        assertTrue(repository.recurringTransactions.value.isEmpty())
        assertFalse(repository.isOccurrenceExecuted("audit_rec", LocalDate.now().toString()))
        assertTrue(repository.transactions.value.isEmpty())
        assertTrue(repository.assets.value.isEmpty())
    }

    @Test
    fun recurringIntervalMustAdvanceTime() = runBlocking {
        val invalid = RecurringTransaction(id = "audit_rec", name = "Invalid interval", type = "expense",
            amount = 10_000L, assetId = "audit_cash", startDate = LocalDate.now().toString(), interval = 0)
        assertTrue(repository.saveRecurringTransaction(invalid).isFailure)
    }

    @Test
    fun zipTraversalCannotOverwriteExistingApplicationFiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sentinel = File(context.filesDir, "audit_escape.txt").apply { writeText("original") }
        val archive = File(context.cacheDir, "audit_traversal.zip")
        ZipOutputStream(archive.outputStream()).use {
            it.putNextEntry(ZipEntry("receipts/../audit_escape.txt"))
            it.write("overwritten".toByteArray())
            it.closeEntry()
            it.putNextEntry(ZipEntry("backup.json"))
            it.write("{}".toByteArray())
            it.closeEntry()
        }
        val helper = DatabaseHelper(context)
        assertTrue(BackupManager.restoreBackupFromZip(context, helper.writableDatabase, archive).isFailure)
        assertEquals("original", sentinel.readText())
        helper.close()
    }

    @Test
    fun csvEscapesQuotedAccountNames() = runBlocking {
        repository.updateAsset(repository.assets.value.single().copy(name = "Bank \"Utama\", IDR")).getOrThrow()
        repository.addTransaction(Transaction("csv", "income", 100L, "Export check", assetId = "audit_cash",
            transactionDate = LocalDate.now().toString())).getOrThrow()
        assertTrue(repository.generateCsvContent().contains("\"Bank \"\"Utama\"\", IDR\""))
    }

    @Test
    fun malformedBackupStructureIsRejectedWithoutThrowing() {
        val result = runCatching { BackupManager.validateBackupJson("{\"metadata\":[],\"data\":{}}") }.getOrNull()
        assertTrue(result is BackupValidationResult.Invalid)
    }

    @Test
    fun transactionsCannotReferToMissingAccounts() = runBlocking {
        val result = repository.addTransaction(Transaction("missing", "income", 100L, "Missing account",
            assetId = "does_not_exist", transactionDate = LocalDate.now().toString()))
        assertTrue(result.isFailure)
        assertTrue(repository.transactions.value.isEmpty())
        assertEquals(1_000_000L, repository.assets.value.single().currentBalance)
    }

    @Test
    fun invalidZipDoesNotOverwriteExistingReceipts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val receipt = File(context.filesDir, "receipts/audit-receipt.jpg").apply {
            parentFile!!.mkdirs()
            writeText("original receipt")
        }
        val archive = File(context.cacheDir, "audit_invalid.zip")
        ZipOutputStream(archive.outputStream()).use {
            it.putNextEntry(ZipEntry("receipts/audit-receipt.jpg"))
            it.write("overwritten".toByteArray())
            it.closeEntry()
            it.putNextEntry(ZipEntry("backup.json"))
            it.write("{}".toByteArray())
            it.closeEntry()
        }
        val helper = DatabaseHelper(context)
        assertTrue(BackupManager.restoreBackupFromZip(context, helper.writableDatabase, archive).isFailure)
        assertEquals("original receipt", receipt.readText())
        helper.close()
    }

    @Test
    fun restoreRejectsNegativeTransactionsAndKeepsCurrentData() = runBlocking {
        val valid = repository.validateBackupJson(repository.createBackupJson().getOrThrow()) as BackupValidationResult.Valid
        valid.parsedData.put("transactions", JSONArray().put(JSONObject().apply {
            put("id", "invalid_tx"); put("type", "expense"); put("amount", -100L)
            put("description", "Invalid amount"); put("asset_id", "audit_cash")
            put("transaction_date", LocalDate.now().toString())
        }))
        assertTrue(repository.restoreFromJson(valid.parsedData).isFailure)
        assertEquals(1_000_000L, repository.assets.value.single().currentBalance)
        assertTrue(repository.transactions.value.isEmpty())
    }
}
