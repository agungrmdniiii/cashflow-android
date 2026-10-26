package com.cashflow.app

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.test.platform.app.InstrumentationRegistry
import com.cashflow.app.data.backup.BackupManager
import com.cashflow.app.data.backup.BackupValidationResult
import com.cashflow.app.data.db.DatabaseHelper
import com.cashflow.app.data.model.Asset
import com.cashflow.app.data.model.Transaction
import com.cashflow.app.data.model.ThemeMode
import com.cashflow.app.data.preferences.PreferenceManager
import com.cashflow.app.data.repository.CashflowRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** All destructive operations use a temporary database and private test directories. */
class BackupDeletionTest {
    private lateinit var root: File
    private lateinit var context: Context
    private lateinit var repository: CashflowRepository
    private val preferenceNames = mutableSetOf<String>()
    private var viewModelStore: androidx.lifecycle.ViewModelStore? = null
    private val ledgerReads = java.util.concurrent.atomic.AtomicInteger()
    private val queryReads = java.util.concurrent.atomic.AtomicInteger()
    private val countingCursors = SQLiteDatabase.CursorFactory { _, driver, table, query ->
        queryReads.incrementAndGet()
        if (query.toString().contains("SELECT * FROM transactions", ignoreCase = true)) ledgerReads.incrementAndGet()
        android.database.sqlite.SQLiteCursor(driver, table, query)
    }

    @Before fun prepare() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        // FileProvider caches its roots process-wide; initialize with the real cache, which contains every isolated test directory.
        androidx.core.content.FileProvider.getUriForFile(base, "${base.packageName}.fileprovider", File(base.cacheDir, "export-root-probe.csv"))
        root = Files.createTempDirectory(base.cacheDir.toPath(), "backup-audit-").toFile()
        context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                val isolatedName = root.name + "_" + name
                preferenceNames.add(isolatedName)
                return base.getSharedPreferences(isolatedName, mode)
            }
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
            override fun getDatabasePath(name: String) = File(root, "databases/$name").apply { parentFile!!.mkdirs() }
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?) =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory ?: countingCursors)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?) =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path, factory ?: countingCursors, errorHandler)
        }
        repository = CashflowRepository(context, countingCursors)
        repository.awaitInitialLoad()
        assertNull(repository.initError.value)
        repository.addAsset(Asset("backup-cash", "Test", "Tunai", openingBalance = 1000, currentBalance = 1000)).getOrThrow()
        repository.addTransaction(Transaction("backup-tx", "expense", 100, "Test", assetId = "backup-cash",
            transactionDate = "2026-10-21")).getOrThrow()
    }

    @After fun clean() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { viewModelStore?.clear() }
        root.deleteRecursively()
        preferenceNames.forEach { InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(it) }
    }

    private suspend fun isolatedViewModel(): com.cashflow.app.ui.CashflowViewModel {
        val app = CashflowApplication()
        ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java).apply { isAccessible = true }.invoke(app, context)
        app.repository.awaitInitialLoad()
        lateinit var vm: com.cashflow.app.ui.CashflowViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val store = androidx.lifecycle.ViewModelStore().also { viewModelStore = it }
            vm = androidx.lifecycle.ViewModelProvider(store, androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(app))[com.cashflow.app.ui.CashflowViewModel::class.java]
        }
        return vm
    }

    @Test fun csvExportUsesVisibleSearchResultsAndEmptyFiltersNeverFallBackToAllRows() = runBlocking {
        verifyTransactionScreenExport("CSV")
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 35)
    @Test fun pdfExportUsesTheSameVisibleSearchResultsAsCsv() = runBlocking {
        verifyTransactionScreenExport("PDF")
    }

    private suspend fun verifyTransactionScreenExport(format: String) {
        val today = java.time.LocalDate.now().toString()
        repository.updateTransaction(repository.transactions.value.single().copy(description = "Makan", transactionDate = today)).getOrThrow()
        repository.addTransaction(Transaction("export-game", "expense", 50, "Game", assetId = "backup-cash", transactionDate = today)).getOrThrow()
        val vm = isolatedViewModel()
        val before = vm.transactions.value.toList()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val chooser = java.util.concurrent.atomic.AtomicReference<android.content.Intent?>()
        val exportContext = object : ContextWrapper(context) {
            override fun startActivity(intent: android.content.Intent) { chooser.set(intent) }
        }
        val activity = instrumentation.startActivitySync(android.content.Intent(instrumentation.targetContext, androidx.activity.ComponentActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) as androidx.activity.ComponentActivity
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val originalClip = clipboard.primaryClip
        try {
            instrumentation.runOnMainSync {
                vm.searchQuery.value = "Makan"
                activity.setContent { com.cashflow.app.ui.theme.CashflowTheme {
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalContext provides exportContext) {
                        com.cashflow.app.ui.screens.TransactionsScreen(vm, androidx.compose.ui.Modifier.statusBarsPadding())
                    }
                } }
            }
            exportAuditNode("1 transaksi ditemukan")
            suspend fun export(allRows: Boolean = false): String {
                chooser.set(null)
                vm.clearSnackbar()
                exportAuditClick("Menu transaksi")
                exportAuditClick("Ekspor laporan")
                exportAuditClick(if (format == "CSV") "Spreadsheet CSV (.csv)" else "Dokumen PDF (.pdf)")
                exportAuditClick(if (allRows) "SEMUA RIWAYAT" else "DATA TERFILTER")
                exportAuditClick("EKSPOR $format")
                val completed = kotlinx.coroutines.withTimeoutOrNull(10000) {
                    while (chooser.get() == null && vm.snackbarMessage.value == null) kotlinx.coroutines.delay(20)
                    chooser.get() != null
                }
                assertTrue("$format export did not open the share sheet (allRows=$allRows): ${vm.snackbarMessage.value}", completed == true)
                val send = chooser.get()!!.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)!!
                val uri = send.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)!!
                return if (format == "CSV") context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                else if (android.os.Build.VERSION.SDK_INT >= 35) context.contentResolver.openFileDescriptor(uri, "r")!!.use { descriptor ->
                    android.graphics.pdf.PdfRenderer(descriptor).use { pdf ->
                        (0 until pdf.pageCount).joinToString(" ") { index ->
                            pdf.openPage(index).use { page -> page.textContents.joinToString(" ") { it.text } }
                        }
                    }
                }
                else error("PDF text verification requires Android 15 or newer")
            }
            val filtered = export()
            assertTrue("The selected Makan record must be exported", filtered.contains("Makan"))
            assertFalse("Game is not visible in the search results and must not be exported", filtered.contains("Game"))
            if (format == "CSV") {
                instrumentation.runOnMainSync { vm.searchQuery.value = "no-such-record" }
                exportAuditNode("0 transaksi ditemukan")
                val empty = export()
                assertFalse("An empty filtered export must not expose hidden records", empty.contains("Makan") || empty.contains("Game"))
                val all = export(allRows = true)
                assertTrue(all.contains("Makan") && all.contains("Game"))
            }
            assertEquals(before, vm.transactions.value)
        } finally {
            instrumentation.runOnMainSync {
                if (originalClip != null) clipboard.setPrimaryClip(originalClip)
                else if (android.os.Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip()
                activity.finish()
            }
        }
    }

    private fun exportAuditFind(node: android.view.accessibility.AccessibilityNodeInfo?, label: String): android.view.accessibility.AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString() == label || node.contentDescription?.toString() == label) return node
        for (index in 0 until node.childCount) exportAuditFind(node.getChild(index), label)?.let { return it }
        return null
    }

    private fun exportAuditNode(label: String): android.view.accessibility.AccessibilityNodeInfo {
        val end = android.os.SystemClock.uptimeMillis() + 5000
        while (android.os.SystemClock.uptimeMillis() < end) {
            exportAuditFind(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow, label)?.let { return it }
            android.os.SystemClock.sleep(30)
        }
        error("Export control missing: $label")
    }

    private fun exportAuditClick(label: String) {
        var node: android.view.accessibility.AccessibilityNodeInfo? = exportAuditNode(label)
        while (node != null && !node.isClickable) node = node.parent
        assertTrue("Export control is not clickable: $label", node?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) == true)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @Test fun similarManualTransactionWaitsForConsentAndCancellationKeepsTheLedger() = runBlocking {
        val vm = isolatedViewModel()
        val before = vm.transactions.value.toList()
        var completed: Boolean? = null
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            vm.saveTransaction(before.single().copy(id = "similar-new", description = "OCR berbeda", inputMethod = "ocr")) { completed = it }
        }
        kotlinx.coroutines.withTimeout(5000) {
            while (vm.dataChangeConfirmation.value == null && completed == null) kotlinx.coroutines.delay(20)
        }
        assertNotNull("A similar record must not be saved without consent", vm.dataChangeConfirmation.value)
        assertNull(completed)
        instrumentation.runOnMainSync { vm.cancelPendingDataChange() }
        assertEquals(false, completed)
        assertEquals(before, vm.transactions.value)
    }

    @Test fun similarCsvRowsWaitForConsentAndCancellationKeepsTheLedger() = runBlocking {
        val vm = isolatedViewModel()
        val before = vm.transactions.value.toList()
        val csv = "Tanggal,Waktu,Deskripsi,Jenis,Kategori,Aset,Aset Tujuan,Nominal,Catatan,Metode Input\n2026-10-21,14:00,Impor ulang,Pengeluaran,-,Test,-,100,,manual"
        var completed: Boolean? = null
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { vm.importCsv(csv) { completed = it } }
        kotlinx.coroutines.withTimeout(5000) {
            while (vm.dataChangeConfirmation.value == null && completed == null) kotlinx.coroutines.delay(20)
        }
        assertNotNull("Similar CSV rows must be reviewed before import", vm.dataChangeConfirmation.value)
        instrumentation.runOnMainSync { vm.cancelPendingDataChange() }
        assertEquals(false, completed)
        assertEquals(before, vm.transactions.value)
    }

    @Test fun confirmingSimilarTransactionSavesExactlyOnce() = runBlocking {
        val vm = isolatedViewModel()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var completed: Boolean? = null
        instrumentation.runOnMainSync { vm.saveTransaction(vm.transactions.value.single().copy(id = "accepted-similar")) { completed = it } }
        kotlinx.coroutines.withTimeout(5000) { while (vm.dataChangeConfirmation.value == null) kotlinx.coroutines.delay(20) }
        instrumentation.runOnMainSync { vm.confirmPendingDataChange(); vm.confirmPendingDataChange() }
        kotlinx.coroutines.withTimeout(5000) { while (completed == null) kotlinx.coroutines.delay(20) }
        assertEquals(true, completed)
        assertEquals(2, vm.transactions.value.size)
        assertEquals(800L, vm.assets.value.single().currentBalance)
    }

    @Test fun confirmingSimilarCsvImportsTheReviewedRow() = runBlocking {
        val vm = isolatedViewModel()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val csv = "Tanggal,Waktu,Deskripsi,Jenis,Kategori,Aset,Aset Tujuan,Nominal,Catatan,Metode Input\n2026-10-21,14:00,Impor ulang,Pengeluaran,-,Test,-,100,,manual"
        var completed: Boolean? = null
        instrumentation.runOnMainSync { vm.importCsv(csv) { completed = it } }
        kotlinx.coroutines.withTimeout(5000) { while (vm.dataChangeConfirmation.value == null) kotlinx.coroutines.delay(20) }
        instrumentation.runOnMainSync { vm.confirmPendingDataChange(); vm.confirmPendingDataChange() }
        kotlinx.coroutines.withTimeout(5000) { while (completed == null) kotlinx.coroutines.delay(20) }
        assertEquals(true, completed)
        assertEquals(2, vm.transactions.value.size)
        assertEquals(800L, vm.assets.value.single().currentBalance)
    }

    @Test fun malformedRestoreCannotEraseExistingLedger() = runBlocking {
        val before = repository.transactions.value.toList()
        assertTrue(repository.restoreFromJson(JSONObject()).isFailure)
        assertEquals(before, repository.transactions.value)
        assertEquals(900L, repository.assets.value.single().currentBalance)
    }

    @Test fun assetCardUsesTheSameNetWorthAsDebtDetailsAndWidget() = runBlocking {
        repository.addAsset(Asset("archived-wealth", "Arsip", "Tunai", 9000, 9000, isActive = false)).getOrThrow()
        val helper = DatabaseHelper(context)
        try {
            helper.writableDatabase.execSQL("INSERT INTO debts(id,title,lender_name,total_amount,paid_amount,due_date,status,direction,created_at,updated_at) VALUES ('card-credit','Piutang','Test',500,100,'2026-12-01','partial','receivable',1,1), ('card-debt','Utang','Test',300,100,'2026-12-01','partial','debt',1,1), ('paid-debt','Lunas','Test',999,999,'2026-12-01','paid','debt',1,1)")
        } finally { helper.close() }
        val vm = isolatedViewModel()
        kotlinx.coroutines.withTimeout(5000) { vm.netWorth.first { it == 1100L } }
        val before = vm.transactions.value.toList()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(android.content.Intent(instrumentation.targetContext, androidx.activity.ComponentActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) as androidx.activity.ComponentActivity
        try {
            instrumentation.runOnMainSync {
                activity.setContent { com.cashflow.app.ui.theme.CashflowTheme {
                    com.cashflow.app.ui.screens.AssetsScreen(vm, androidx.compose.ui.Modifier.statusBarsPadding())
                } }
            }
            exportAuditNode("DAFTAR ASET")
            assertNotNull("The net-worth card must show active assets 900 + remaining receivables 400 - remaining debt 200, not the account balance",
                exportAuditFind(instrumentation.uiAutomation.rootInActiveWindow, "Rp 1.100"))
            instrumentation.runOnMainSync { vm.setAssetScreenSubTab(1) }
            exportAuditNode("Kekayaan Bersih: Rp 1.100")
            val remote = com.cashflow.app.widget.DuitAingQuickWidget.buildWidgetViews(context)
            instrumentation.runOnMainSync {
                assertEquals("Bersih: Rp 1.100", remote.apply(context, null).findViewById<android.widget.TextView>(R.id.tv_widget_subtitle).text.toString())
            }
            assertEquals(before, vm.transactions.value)
            assertEquals(900L, vm.totalWealth.value)
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun widgetNetWorthAddsReceivablesAndSubtractsDebt() = runBlocking {
        val helper = DatabaseHelper(context)
        try {
            val db = helper.writableDatabase
            db.execSQL("INSERT INTO debts(id,title,lender_name,total_amount,paid_amount,due_date,status,direction,created_at,updated_at) VALUES ('widget-credit','Piutang','Test',500,0,'2026-12-01','unpaid','receivable',1,1)")
            PreferenceManager(context).setBalanceHidden(false)
            for (layout in listOf(R.layout.widget_duit_aing_quick, R.layout.widget_duit_aing_balance, R.layout.widget_duit_aing_summary)) {
                val remote = com.cashflow.app.widget.DuitAingQuickWidget.buildWidgetViews(context, layout)
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    assertEquals("Bersih: Rp 1.400", remote.apply(context, null).findViewById<android.widget.TextView>(R.id.tv_widget_subtitle).text.toString())
                }
            }
            db.execSQL("INSERT INTO debts(id,title,lender_name,total_amount,paid_amount,due_date,status,direction,created_at,updated_at) VALUES ('widget-debt','Utang','Test',300,100,'2026-12-01','partial','debt',1,1)")
            val remote = com.cashflow.app.widget.DuitAingQuickWidget.buildWidgetViews(context)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                assertEquals("Bersih: Rp 1.200", remote.apply(context, null).findViewById<android.widget.TextView>(R.id.tv_widget_subtitle).text.toString())
            }
        } finally { helper.close() }
    }

    @Test fun zipExportMustRejectMissingReceiptsWithoutPublishingAPartialArchive() = runBlocking {
        repository.updateTransaction(repository.transactions.value.single().copy(attachment = "/old-phone/receipts/missing.jpg")).getOrThrow()
        val archive = File(root, "missing-export.zip")
        val before = repository.transactions.value.toList()
        val result = repository.createBackupZip(archive)
        assertTrue("A complete ZIP must not be reported as saved when a receipt is missing", result.isFailure)
        assertFalse(archive.exists())
        assertEquals(before, repository.transactions.value)
    }

    @Test fun repeatedOccurrenceDoesNotReadTheEntireLedgerAgain() = runBlocking {
        val date = java.time.LocalDate.now().toString()
        val recurring = com.cashflow.app.data.model.RecurringTransaction(id = "rec-noop", name = "Test", type = "expense", amount = 1,
            assetId = "backup-cash", frequency = "daily", startDate = date, mode = "auto")
        repository.saveRecurringTransaction(recurring).getOrThrow()
        repository.executeRecurringOccurrence(recurring.id, date).getOrThrow()
        val reads = ledgerReads.get()
        assertNull(repository.executeRecurringOccurrence(recurring.id, date).getOrThrow())
        assertEquals("An already processed occurrence must not reload all transactions", reads, ledgerReads.get())
    }

    @Test fun recurringCatchUpReloadsTheLedgerOnceAndSkipsCompletedHistory() = runBlocking {
        val today = java.time.LocalDate.now()
        val recurring = com.cashflow.app.data.model.RecurringTransaction(id = "rec-batch", name = "Test", type = "income", amount = 1,
            assetId = "backup-cash", frequency = "daily", startDate = today.minusDays(2).toString(), mode = "auto")
        repository.saveRecurringTransaction(recurring).getOrThrow()
        ledgerReads.set(0)
        val created = com.cashflow.app.data.service.RecurringSchedulerService.processDueRecurringTransactions(repository, today)
        assertEquals(3, created.size)
        assertEquals("Catch-up must publish one refreshed snapshot", 1, ledgerReads.get())
        queryReads.set(0)
        assertTrue(com.cashflow.app.data.service.RecurringSchedulerService.processDueRecurringTransactions(repository, today).isEmpty())
        assertTrue("Completed history should need only one execution-key query", queryReads.get() <= 2)
    }

    @Test fun foregroundCalendarChangeUpdatesRelativeFiltersWithoutANewTransaction() = runBlocking {
        org.junit.Assume.assumeTrue(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val zone = java.util.TimeZone.getDefault()
        var vm: com.cashflow.app.ui.CashflowViewModel? = null
        try {
            java.util.TimeZone.setDefault(java.util.SimpleTimeZone(-12 * 60 * 60 * 1000, "GMT-12:00"))
            val oldDay = java.time.LocalDate.now()
            java.util.TimeZone.setDefault(java.util.SimpleTimeZone(14 * 60 * 60 * 1000, "GMT+14:00"))
            val newDay = java.time.LocalDate.now()
            assertNotEquals(oldDay, newDay)
            java.util.TimeZone.setDefault(java.util.SimpleTimeZone(-12 * 60 * 60 * 1000, "GMT-12:00"))
            repository.addTransaction(Transaction("day-old", "income", 10, "Old day", assetId = "backup-cash", transactionDate = oldDay.toString())).getOrThrow()
            repository.addTransaction(Transaction("day-new", "income", 20, "New day", assetId = "backup-cash", transactionDate = newDay.toString())).getOrThrow()
            val model = isolatedViewModel().also { vm = it }
            model.filterDatePreset.value = com.cashflow.app.ui.DateFilterPreset.TODAY
            kotlinx.coroutines.withTimeout(4000) { model.filteredTransactionsTab.first { rows -> rows.any { it.id == "day-old" } } }
            java.util.TimeZone.setDefault(java.util.SimpleTimeZone(14 * 60 * 60 * 1000, "GMT+14:00"))
            InstrumentationRegistry.getInstrumentation().runOnMainSync { model.onAppBackgrounded(); model.onAppForegrounded() }
            val updated = kotlinx.coroutines.withTimeoutOrNull(4000) { model.filteredTransactionsTab.first { rows -> rows.any { it.id == "day-new" } } }
            assertNotNull("Foregrounding on a new calendar day must invalidate relative date filters", updated)
            assertFalse(updated!!.any { it.id == "day-old" })
        } finally {
            java.util.TimeZone.setDefault(zone)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { vm?.onAppBackgrounded() }
        }
    }


    @Test fun failedZipExportPreservesAnExistingArchive() = runBlocking {
        repository.updateTransaction(repository.transactions.value.single().copy(attachment = "/missing/photo.jpg")).getOrThrow()
        val archive = File(root, "previous.zip").apply { writeText("previous verified archive") }
        assertTrue(repository.createBackupZip(archive).isFailure)
        assertEquals("previous verified archive", archive.readText())
        assertFalse(root.listFiles().orEmpty().any { it.name.startsWith("backup-") && it.extension == "zip" })
    }

    @Test fun calendarRefreshRecalculatesMonthlyBudgetOnceWithoutChangingTheLedger() = runBlocking {
        val category = repository.categories.value.first { it.type == "expense" }.id
        repository.updateTransaction(repository.transactions.value.single().copy(categoryId = category, transactionDate = "2026-10-31")).getOrThrow()
        repository.addBudget(com.cashflow.app.data.model.Budget("calendar-budget", category, "monthly", 1000)).getOrThrow()
        repository.refreshCalendar(java.time.LocalDate.of(2026, 10, 31))
        assertEquals(100L, repository.budgets.value.single().spentAmount)
        val before = repository.transactions.value.toList()
        repository.refreshCalendar(java.time.LocalDate.of(2026, 11, 1))
        assertEquals(0L, repository.budgets.value.single().spentAmount)
        assertEquals(java.time.LocalDate.of(2026, 11, 1), repository.calendarDate.value)
        assertEquals(before, repository.transactions.value)
        ledgerReads.set(0)
        repository.refreshCalendar(java.time.LocalDate.of(2026, 11, 1))
        assertEquals("Same-day checks must not read the ledger", 0, ledgerReads.get())
    }

    @Test fun monthlyWidgetUsesTheSameFinancialPeriodAndExcludesAdjustments() = runBlocking {
        val month = java.time.LocalDate.now().withDayOfMonth(1)
        repository.updateTransaction(repository.transactions.value.single().copy(transactionDate = month.toString())).getOrThrow()
        repository.addTransaction(Transaction("widget-income", "income", 500, "Income", assetId = "backup-cash", transactionDate = month.toString())).getOrThrow()
        repository.addTransaction(Transaction("widget-old-income", "income", 900, "Old income", assetId = "backup-cash", transactionDate = month.minusDays(1).toString())).getOrThrow()
        repository.addTransaction(Transaction("widget-adjustment", "adjustment", 800, "Adjustment", assetId = "backup-cash", transactionDate = month.toString(), isAdjustment = true, adjustmentDirection = 1)).getOrThrow()
        val preferences = PreferenceManager(context)
        preferences.setBalanceHidden(false)
        val remote = com.cashflow.app.widget.DuitAingQuickWidget.buildWidgetViews(context, R.layout.widget_duit_aing_summary)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = remote.apply(context, null)
            assertEquals("Rp 500", view.findViewById<android.widget.TextView>(R.id.tv_widget_income).text.toString())
            assertEquals("Rp 100", view.findViewById<android.widget.TextView>(R.id.tv_widget_expense).text.toString())
            assertEquals(com.cashflow.app.ui.components.Formatters.formatRupiah(repository.assets.value.single().currentBalance),
                view.findViewById<android.widget.TextView>(R.id.tv_widget_balance).text.toString())
        }
    }

    @Test fun jsonBackupRestoresPortableSettingsAndCategoryFavorites() = runBlocking {
        val prefs = PreferenceManager(context)
        val category = repository.categories.value.first { it.isActive }.id
        prefs.setFavoriteCategoryIds(setOf(category))
        prefs.setThemeMode(ThemeMode.DARK)
        prefs.setUserName("Penguji")
        prefs.setBalanceHidden(true)
        prefs.setFavoriteDirectSave(false)
        prefs.setBudgetStartDay(25)
        prefs.setDailyReminderEnabled(true)
        prefs.setDailyReminderTime("18:45")
        prefs.setBudgetAlertsEnabled(false)
        prefs.setBudgetRolloverEnabled(true)
        prefs.setBackupReminderEnabled(false)
        val json = repository.createBackupJson().getOrThrow()
        val settings = JSONObject(json).getJSONObject("data").optJSONObject("app_settings")
        assertNotNull("Portable settings must be part of the checksummed backup", settings)
        assertFalse(settings!!.has("biometric_enabled"))
        assertFalse(settings.has("cloud_backup_email"))
        prefs.setThemeMode(ThemeMode.LIGHT)
        prefs.setFavoriteCategoryIds(emptySet())
        prefs.setBudgetStartDay(1)
        prefs.setDailyReminderTime("20:00")
        prefs.setBiometricEnabled(true)
        prefs.setCloudBackupEmail("device@example.test")
        val valid = repository.validateBackupJson(json) as BackupValidationResult.Valid
        repository.restoreFromJson(valid.parsedData).getOrThrow()
        assertEquals(setOf(category), prefs.getFavoriteCategoryIds())
        assertEquals(ThemeMode.DARK, prefs.getThemeMode())
        assertEquals("Penguji", prefs.getUserName())
        assertEquals(25, prefs.getBudgetStartDay())
        assertEquals("18:45", prefs.getDailyReminderTime())
        assertTrue(prefs.isDailyReminderEnabled())
        assertTrue(prefs.isBalanceHidden())
        assertTrue(prefs.isBudgetRolloverEnabled())
        assertFalse(prefs.isFavoriteDirectSave())
        assertFalse(prefs.isBackupReminderEnabled())
        assertFalse(prefs.isBudgetAlertsEnabled())
        assertTrue("Device security must not be replaced", prefs.isBiometricEnabled())
        assertEquals("device@example.test", prefs.getCloudBackupEmail())
        val safety = JSONObject(File(context.filesDir, "backups/before-restore.json").readText())
        assertEquals("light", safety.getJSONObject("data").getJSONObject("app_settings").getString("theme_mode"))
        assertEquals(900L, repository.assets.value.single().currentBalance)
    }

    @Test fun legacyBackupKeepsExistingPreferences() = runBlocking {
        val data = (repository.validateBackupJson(repository.createBackupJson().getOrThrow()) as BackupValidationResult.Valid).parsedData
        data.remove("app_settings")
        val prefs = PreferenceManager(context)
        prefs.setBudgetStartDay(17)
        prefs.setFavoriteCategoryIds(setOf(repository.categories.value.first { it.isActive }.id))
        val favorites = prefs.getFavoriteCategoryIds()
        repository.restoreFromJson(data).getOrThrow()
        assertEquals(17, prefs.getBudgetStartDay())
        assertEquals(favorites, prefs.getFavoriteCategoryIds())
    }

    @Test fun invalidSettingsCannotReplaceLedgerOrPreferences() = runBlocking {
        val data = (repository.validateBackupJson(repository.createBackupJson().getOrThrow()) as BackupValidationResult.Valid).parsedData
        data.put("app_settings", JSONObject().put("daily_reminder_time", "25:99"))
        data.getJSONArray("transactions").getJSONObject(0).put("amount", 700)
        val before = repository.transactions.value.toList()
        val prefs = PreferenceManager(context)
        val time = prefs.getDailyReminderTime()
        assertTrue(repository.restoreFromJson(data).isFailure)
        assertEquals(before, repository.transactions.value)
        assertEquals(time, prefs.getDailyReminderTime())
        assertEquals(900L, repository.assets.value.single().currentBalance)
    }

    @Test fun zipBackupAlsoRestoresCategoryFavorites() = runBlocking {
        val prefs = PreferenceManager(context)
        val category = repository.categories.value.first { it.isActive }.id
        prefs.setFavoriteCategoryIds(setOf(category))
        prefs.setBudgetStartDay(23)
        val archive = File(root, "settings.zip")
        repository.createBackupZip(archive).getOrThrow()
        prefs.setFavoriteCategoryIds(emptySet())
        prefs.setBudgetStartDay(1)
        repository.restoreFromZip(archive).getOrThrow()
        assertEquals(setOf(category), prefs.getFavoriteCategoryIds())
        assertEquals(23, prefs.getBudgetStartDay())
    }

    @Test fun encryptedBackupRestoresPreferencesWithoutImportingDeviceConsent() = runBlocking {
        val prefs = PreferenceManager(context)
        val category = repository.categories.value.first { it.isActive }.id
        prefs.setFavoriteCategoryIds(setOf(category, "category-no-longer-exists"))
        prefs.setDailyReminderTime("07:15")
        val payload = com.cashflow.app.data.service.CloudBackupService.encryptBackupPayload(repository.createBackupJson().getOrThrow(), "test-password-123")
        val json = com.cashflow.app.data.service.CloudBackupService.decryptBackupPayload(payload, "test-password-123")
        val valid = repository.validateBackupJson(json) as BackupValidationResult.Valid
        val settings = valid.parsedData.getJSONObject("app_settings")
        settings.put("online_voice_enabled", true).put("cloud_backup_enabled", true).put("biometric_enabled", false)
        prefs.setOnlineVoiceEnabled(false)
        prefs.setCloudBackupEnabled(false)
        prefs.setBiometricEnabled(true)
        prefs.setFavoriteCategoryIds(emptySet())
        prefs.setDailyReminderTime("20:00")
        repository.restoreFromJson(valid.parsedData).getOrThrow()
        assertEquals(setOf(category), prefs.getFavoriteCategoryIds())
        assertEquals("07:15", prefs.getDailyReminderTime())
        assertFalse(prefs.isOnlineVoiceEnabled())
        assertFalse(prefs.isCloudBackupEnabled())
        assertTrue(prefs.isBiometricEnabled())
    }

    @Test fun jsonAndZipRestoreUpdateVisiblePreferencesWithoutRestart() = runBlocking {
        val vm = isolatedViewModel()
        val category = vm.categories.value.first { it.isActive }.id
        val data = (repository.validateBackupJson(repository.createBackupJson().getOrThrow()) as BackupValidationResult.Valid).parsedData
        data.getJSONObject("app_settings").put("favorite_category_ids", org.json.JSONArray().put(category))
            .put("theme_mode", "dark").put("budget_start_day", 19).put("daily_reminder_time", "06:30").put("balance_hidden", true)
        var result: Result<Unit>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { vm.restoreBackup(data) { result = it } }
        kotlinx.coroutines.withTimeout(5000) { while (result == null) kotlinx.coroutines.delay(20) }
        result!!.getOrThrow()
        assertEquals(setOf(category), vm.favoriteCategoryIds.value)
        assertEquals(ThemeMode.DARK, vm.themeMode.value)
        assertEquals(19, vm.budgetStartDay.value)
        assertEquals("06:30", vm.dailyReminderTime.value)
        assertTrue(vm.isBalanceHidden.value)
        val archive = File(root, "visible-settings.zip")
        repository.createBackupZip(archive).getOrThrow()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            vm.setThemeMode(ThemeMode.LIGHT)
            vm.toggleCategoryFavorite(category)
            vm.setDailyReminderTime("20:00")
        }
        var zipResult: Result<com.cashflow.app.data.backup.BackupSummary>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { vm.restoreZipBackupFile(archive) { zipResult = it } }
        kotlinx.coroutines.withTimeout(5000) { while (zipResult == null) kotlinx.coroutines.delay(20) }
        zipResult!!.getOrThrow()
        assertEquals(setOf(category), vm.favoriteCategoryIds.value)
        assertEquals(ThemeMode.DARK, vm.themeMode.value)
        assertEquals("06:30", vm.dailyReminderTime.value)
    }

    @Test fun resetRemovesOwnedFinancialCopiesButKeepsUnrelatedFiles() = runBlocking {
        val paths = listOf(File(context.filesDir, "receipts/photo.jpg"),
            File(context.filesDir, "backups/before-restore.json"), File(context.filesDir, "backups/safety/repair.json"),
            File(context.filesDir, "logs/crash_latest.log"), File(context.cacheDir, "exports/data.csv"),
            File(context.cacheDir, "backups/data.json"), File(context.cacheDir, "camera/photo.jpg"),
            File(context.cacheDir, "temp_restore.enc"), File(context.cacheDir, "temp_restore.zip"),
            File(context.cacheDir, "duitaing-backup-20261021-1200.enc"),
            File(context.cacheDir, "restore-staging/backup.json"),
            context.getDatabasePath(DatabaseHelper.DATABASE_NAME + ".bak_v9"))
        paths.forEach { it.parentFile!!.mkdirs(); it.writeText("private fixture") }
        val unrelated = File(context.filesDir, "keep.txt").apply { writeText("keep") }
        repository.deleteAllUserData().getOrThrow()
        assertTrue(repository.transactions.value.isEmpty())
        assertTrue(repository.assets.value.isEmpty())
        paths.forEach { assertFalse("Leftover: ${it.name}", it.exists()) }
        assertEquals("keep", unrelated.readText())
    }

    @Test fun jsonAndZipRoundTripRestoreAmountsAndReceiptBytes() = runBlocking {
        val photo = File(context.filesDir, "receipts/test.jpg").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        val tx = repository.transactions.value.single().copy(attachment = photo.absolutePath)
        repository.updateTransaction(tx).getOrThrow()
        val saved = repository.transactions.value.single()
        val archive = File(root, "backup.zip")
        repository.createBackupZip(archive).getOrThrow()
        val valid = repository.validateBackupJson(repository.createBackupJson().getOrThrow()) as BackupValidationResult.Valid
        repository.restoreFromJson(valid.parsedData).getOrThrow()
        assertEquals(saved, repository.transactions.value.single())
        repository.restoreFromZip(archive).getOrThrow()
        val restored = repository.transactions.value.single()
        assertEquals(tx.amount, restored.amount)
        assertEquals(900L, repository.assets.value.single().currentBalance)
        assertArrayEquals(byteArrayOf(1, 2, 3), File(restored.attachment!!).readBytes())
    }

    @Test fun zipWithMissingReceiptMustNotReplaceExistingData() = runBlocking {
        repository.updateTransaction(repository.transactions.value.single().copy(attachment = "/old/receipts/missing.jpg")).getOrThrow()
        val before = repository.transactions.value.toList()
        val archive = File(root, "missing-receipt.zip")
        java.util.zip.ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("backup.json"))
            zip.write(repository.createBackupJson().getOrThrow().toByteArray())
            zip.closeEntry()
        }
        assertTrue(repository.restoreFromZip(archive).isFailure)
        assertEquals(before, repository.transactions.value)
    }

    @Test fun resetAndRestoreDoNotLeaveOldCustomCategories() = runBlocking {
        val snapshot = repository.validateBackupJson(repository.createBackupJson().getOrThrow()) as BackupValidationResult.Valid
        val helper = DatabaseHelper(context)
        try {
            helper.writableDatabase.execSQL("INSERT INTO categories (id,name,type,is_default,is_active) VALUES ('private-cat','Private category','expense',0,1)")
            repository.restoreFromJson(snapshot.parsedData).getOrThrow()
            assertFalse(repository.categories.value.any { it.id == "private-cat" })
            helper.writableDatabase.execSQL("INSERT INTO categories (id,name,type,is_default,is_active) VALUES ('private-cat','Private category','expense',0,1)")
            repository.deleteAllUserData().getOrThrow()
            assertFalse(repository.categories.value.any { it.id == "private-cat" })
            assertEquals(helper.getDefaultCategoriesList().size, repository.categories.value.size)
        } finally { helper.close() }
    }

    @Test fun invalidTransactionRollsBackAllRestoredTables() = runBlocking {
        val before = repository.transactions.value.toList()
        val valid = repository.validateBackupJson(repository.createBackupJson().getOrThrow()) as BackupValidationResult.Valid
        val prefs = PreferenceManager(context)
        prefs.setThemeMode(ThemeMode.LIGHT)
        valid.parsedData.getJSONObject("app_settings").put("theme_mode", "dark")
        valid.parsedData.getJSONArray("transactions").getJSONObject(0).put("amount", -100)
        assertTrue(repository.restoreFromJson(valid.parsedData).isFailure)
        assertEquals(ThemeMode.LIGHT, prefs.getThemeMode())
        assertEquals(before, repository.transactions.value)
        assertEquals(900L, repository.assets.value.single().currentBalance)
    }

    @Test fun offlineMigrationRemovesBothLegacyAiKeysAndOnlineOptIn() {
        val raw = context.getSharedPreferences("duit_aing_preferences", 0)
        val credentials = context.getSharedPreferences("duit_aing_credentials", 0)
        raw.edit().putString("gemini_api_key", "test-key").putBoolean("ai_insights_enabled", true).commit()
        credentials.edit().putString("api_key", "old-encrypted-value").commit()
        com.cashflow.app.data.preferences.PreferenceManager(context)
        assertFalse(raw.contains("gemini_api_key"))
        assertFalse(raw.contains("ai_insights_enabled"))
        assertFalse(credentials.contains("api_key"))
    }

    @Test fun databaseInsertFailureCannotBeReportedAsSuccessfulRestore() = runBlocking {
        val before = repository.transactions.value.toList()
        val valid = repository.validateBackupJson(repository.createBackupJson().getOrThrow()) as BackupValidationResult.Valid
        valid.parsedData.getJSONArray("categories").put(JSONObject().put("id", "broken-cat").put("name", "Test").put("type", "expense"))
        val helper = DatabaseHelper(context)
        try {
            helper.writableDatabase.execSQL("CREATE TRIGGER reject_test_category BEFORE INSERT ON categories WHEN NEW.id = 'broken-cat' BEGIN SELECT RAISE(FAIL, 'test insert failure'); END")
            assertTrue(repository.restoreFromJson(valid.parsedData).isFailure)
            assertEquals(before, repository.transactions.value)
            assertEquals(900L, repository.assets.value.single().currentBalance)
        } finally { helper.close() }
    }
}
