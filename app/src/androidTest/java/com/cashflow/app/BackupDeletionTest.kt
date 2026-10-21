package com.cashflow.app

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.cashflow.app.data.backup.BackupManager
import com.cashflow.app.data.backup.BackupValidationResult
import com.cashflow.app.data.db.DatabaseHelper
import com.cashflow.app.data.model.Asset
import com.cashflow.app.data.model.Transaction
import com.cashflow.app.data.repository.CashflowRepository
import kotlinx.coroutines.runBlocking
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

    @Before fun prepare() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
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
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?) =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path, factory, errorHandler)
        }
        repository = CashflowRepository(context)
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
        valid.parsedData.getJSONArray("transactions").getJSONObject(0).put("amount", -100)
        assertTrue(repository.restoreFromJson(valid.parsedData).isFailure)
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
