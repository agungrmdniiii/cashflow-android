package com.cashflow.app

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.cashflow.app.data.backup.*
import com.cashflow.app.data.model.*
import com.cashflow.app.data.preferences.PreferenceManager
import com.cashflow.app.data.repository.CashflowRepository
import com.cashflow.app.data.service.CloudBackupService
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.time.LocalDate
import kotlin.random.Random

/** Opt-in cloud round trip: only temporary example databases are exported or restored. */
class DeviceRestoreAuditTest {
    // Public fixture password, never the user's device-protected recovery password.
    private val fixturePassword = "Migration-example-only-2026"
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun canonical(value: Any?): String = when (value) {
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") {
            JSONObject.quote(it) + ":" + canonical(value.get(it))
        }
        is JSONArray -> (0 until value.length()).map { canonical(value.get(it)) }.sorted().joinToString(",", "[", "]")
        null, JSONObject.NULL -> "null"
        is String -> JSONObject.quote(value)
        else -> value.toString()
    }

    private suspend fun snapshot(repo: CashflowRepository, receipts: Boolean = true): String {
        val data = JSONObject(repo.createBackupJson().getOrThrow()).getJSONObject("data")
        if (receipts) {
            val transactions = data.getJSONArray("transactions")
            for (i in 0 until transactions.length()) {
                val row = transactions.getJSONObject(i)
                val path = row.optString("attachment").takeUnless { it.isBlank() || it == "null" } ?: continue
                val photo = File(path)
                check(photo.isFile) { "A linked receipt is missing" }
                val bitmap = BitmapFactory.decodeFile(path)
                assertNotNull("Linked JPEG must be decodable", bitmap)
                bitmap?.recycle()
                row.put("attachment", "sha256:" + digest(photo.readBytes()))
            }
        }
        return canonical(data)
    }

    private fun isolated(base: Context, root: File, preferences: MutableSet<String>): Context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir() = File(root, "files").apply { mkdirs() }
        override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
            val isolatedName = root.name + "_" + name
            preferences.add(isolatedName)
            return base.getSharedPreferences(isolatedName, mode)
        }
        override fun getDatabasePath(name: String) = File(root, "databases/$name").apply { parentFile!!.mkdirs() }
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?) =
            SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?) =
            SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path, factory, errorHandler)
    }

    private suspend fun verifyMoney(repo: CashflowRepository) {
        assertEquals(5, repo.transactions.value.size)
        assertEquals(2, repo.assets.value.size)
        assertEquals(1_100_000L, repo.assets.value.single { it.id == "migration_cash" }.currentBalance)
        assertEquals(582_500L, repo.assets.value.single { it.id == "migration_bank" }.currentBalance)
        assertEquals(0, repo.recalculateBalances(autoFix = false).getOrThrow().discrepanciesFound)
        val today = LocalDate.of(2026, 10, 15)
        val month = filterTransactionsByPeriod(repo.transactions.value, PeriodFilter.BULAN_INI, today)
        val summary = summarizeTransactions(month)
        assertEquals(250_000L, summary.income)
        assertEquals(50_000L, summary.expense)
        val chart = buildCashflowChart(month, PeriodFilter.BULAN_INI, today)
        assertEquals(250_000L, chart.sumOf { it.income })
        assertEquals(50_000L, chart.sumOf { it.expense })
        assertEquals(70_000L, summarizeTransactions(repo.transactions.value).expense)
    }

    @Test fun control() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Explicit synthetic migration audit required", args.getString("deviceRestoreAudit") == "approved")
        check(Build.HARDWARE in listOf("ranchu", "goldfish")) { "Never run on a personal phone" }
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val live = (base.applicationContext as CashflowApplication).repository
        live.awaitInitialLoad()
        val before = digest(snapshot(live, receipts = false).toByteArray())
        val deviceOptions = BackupSettingsStore(base).preferences.all.toMap()
        val root = Files.createTempDirectory(base.cacheDir.toPath(), "migration-audit-").toFile()
        val preferences = mutableSetOf<String>()
        val context = isolated(base, root, preferences)
        val repo = CashflowRepository(context).also { it.awaitInitialLoad() }
        assertNull(repo.initError.value)
        val artifacts = File(checkNotNull(base.getExternalFilesDir(null)), "migration-audit").apply { mkdirs() }
        val result = JSONObject().put("stage", args.getString("stage")).put("sdk", Build.VERSION.SDK_INT)
        try {
            when (args.getString("stage")) {
                "prepare" -> {
                    check(BackupSettingsStore(base).driveAccount == null)
                    check(!BackupPasswordStore(base).isConfigured())
                    result.put("fresh_destination", true)
                }
                "source" -> {
                    repo.addAsset(Asset("migration_cash", "Kas Contoh", "Tunai", 1_000_000L, 1_000_000L)).getOrThrow()
                    repo.addAsset(Asset("migration_bank", "Bank Contoh", "Bank", 500_000L, 500_000L)).getOrThrow()
                    repo.addCategory(Category("migration_food", "Makan Uji", "expense")).getOrThrow()
                    val photo = File(context.filesDir, "receipts/example.jpg").apply { parentFile!!.mkdirs() }
                    val random = Random(2026)
                    val pixels = IntArray(512 * 512) { random.nextInt() or (0xff shl 24) }
                    Bitmap.createBitmap(pixels, 512, 512, Bitmap.Config.ARGB_8888).let { bitmap ->
                        photo.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 94, it)) }
                        bitmap.recycle()
                    }
                    val date = "2026-10-15"
                    for (tx in listOf(
                        Transaction("migration_income", "income", 250_000L, "Gaji contoh", assetId = "migration_cash", transactionDate = date),
                        Transaction("migration_expense", "expense", 50_000L, "Makan contoh", "migration_food", "migration_cash", transactionDate = date, attachment = photo.absolutePath),
                        Transaction("migration_transfer", "transfer", 100_000L, "Pindah rekening contoh", assetId = "migration_cash", destinationAssetId = "migration_bank", transactionDate = date),
                        Transaction("migration_previous", "expense", 20_000L, "Pengeluaran bulan lalu", "migration_food", "migration_bank", transactionDate = "2026-09-15"),
                        Transaction("migration_adjustment", "adjustment", 2_500L, "Koreksi contoh", assetId = "migration_bank", transactionDate = date, isAdjustment = true, adjustmentDirection = 1)
                    )) repo.addTransaction(tx).getOrThrow()
                    repo.addBudget(Budget("migration_budget", "migration_food", targetAmount = 500_000L)).getOrThrow()
                    PreferenceManager(context).setUserName("Penguji Pindah HP")
                    PreferenceManager(context).setThemeMode(ThemeMode.DARK)
                    verifyMoney(repo)
                    val expected = snapshot(repo)
                    val zip = File(root, "sample.zip")
                    repo.createBackupZip(zip).getOrThrow()
                    val encrypted = File(root, "duitaing-backup-migration-${System.currentTimeMillis()}.dab")
                    BackupArchive.encrypt(zip, encrypted, fixturePassword)
                    encrypted.copyTo(File(artifacts, "offline-source.dab"), overwrite = true)
                    File(artifacts, "expected.json").writeText(JSONObject().put("data", JSONObject(expected))
                        .put("encrypted_sha256", digest(encrypted.readBytes())).toString(2))
                    File(artifacts, "source-prepared.json").writeText(JSONObject()
                        .put("portable_sha256", digest(expected.toByteArray())).put("receipt_sha256", digest(photo.readBytes()))
                        .put("encrypted_sha256", digest(encrypted.readBytes())).put("bytes", encrypted.length()).toString(2))
                    val account = BackupSettingsStore(base).driveAccount ?: error("Connect the emulator's Google Drive first")
                    val coordinator = BackupCoordinator(base)
                    println("MIGRATION_PROGRESS authorizing_existing_consent")
                    val auth = coordinator.authorize(account)
                    check(!auth.hasResolution() && BackupCoordinator.DRIVE_SCOPE in auth.grantedScopes) { "Google consent must be completed by the user" }
                    val token = auth.accessToken ?: error("No Drive session available")
                    val network = coordinator.driveNetwork()
                    println("MIGRATION_PROGRESS checking_drive_access")
                    CloudBackupService.listDriveAppDataBackups(token, network = network).getOrThrow()
                    println("MIGRATION_PROGRESS uploading_synthetic_archive bytes=${encrypted.length()}")
                    val id = CloudBackupService.uploadToDriveAppData(token, encrypted, network).getOrThrow()
                    println("MIGRATION_PROGRESS verifying_uploaded_archive")
                    val listing = CloudBackupService.listDriveAppDataBackups(token, network = network).getOrThrow()
                    assertTrue("Uploaded example archive must appear in Drive", listing.files.any { it.id == id && it.sizeBytes == encrypted.length() })
                    val interrupted = File(root, "interrupted.dab")
                    coroutineScope {
                        println("MIGRATION_PROGRESS testing_partial_download_cancellation")
                        val job = async(Dispatchers.IO) { CloudBackupService.downloadDriveBackup(token, id, interrupted, network).getOrThrow() }
                        withTimeout(60_000) { while (job.isActive && interrupted.length() == 0L) delay(1) }
                        check(job.isActive && interrupted.length() > 0) { "Download finished too quickly to exercise partial-transfer cancellation" }
                        result.put("partial_download_bytes", interrupted.length())
                        job.cancelAndJoin()
                    }
                    assertFalse("Cancelled Google download must remove the partial file", interrupted.exists())
                    val downloaded = File(artifacts, "transfer.dab")
                    check(!downloaded.exists()) { "Use a new audit artifact directory before rerunning" }
                    println("MIGRATION_PROGRESS downloading_complete_archive")
                    CloudBackupService.downloadDriveBackup(token, id, downloaded, network).getOrThrow()
                    assertEquals(digest(encrypted.readBytes()), digest(downloaded.readBytes()))
                    result.put("drive_upload_list_download", true).put("partial_download_cleaned", true)
                        .put("encrypted_sha256", digest(downloaded.readBytes())).put("bytes", downloaded.length())
                        .put("portable_sha256", digest(expected.toByteArray())).put("receipt_sha256", digest(photo.readBytes()))
                }
                "destination" -> {
                    check(BackupSettingsStore(base).driveAccount == null) { "Destination must have its own fresh device state" }
                    check(!BackupPasswordStore(base).isConfigured()) { "Do not copy source Keystore/password state" }
                    repo.addAsset(Asset("destination_guard", "Saldo Tujuan Contoh", "Tunai", 12_345L, 12_345L)).getOrThrow()
                    repo.addTransaction(Transaction("destination_guard_expense", "expense", 345L, "Data sebelum pemulihan", assetId = "destination_guard", transactionDate = "2026-10-15")).getOrThrow()
                    val guard = snapshot(repo)
                    val encrypted = File(artifacts, "transfer.dab")
                    val manifest = JSONObject(File(artifacts, "expected.json").readText())
                    assertEquals(manifest.getString("encrypted_sha256"), digest(encrypted.readBytes()))
                    val badOutput = File(root, "bad.zip")
                    assertTrue(runCatching { BackupArchive.decrypt(encrypted, badOutput, "wrong-example-password") }.isFailure)
                    assertFalse(badOutput.exists())
                    assertEquals(guard, snapshot(repo))
                    val truncated = File(root, "truncated.dab").apply { writeBytes(encrypted.readBytes().copyOf((encrypted.length() / 2).toInt())) }
                    assertTrue(runCatching { BackupArchive.decrypt(truncated, badOutput, fixturePassword) }.isFailure)
                    assertFalse(badOutput.exists())
                    assertEquals(guard, snapshot(repo))
                    val brokenInput = object : InputStream() {
                        var remaining = 65_537
                        override fun read(): Int { if (remaining-- <= 0) throw IOException("Synthetic disconnected stream"); return 1 }
                        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                            if (remaining <= 0) throw IOException("Synthetic disconnected stream")
                            val count = minOf(length, remaining); buffer.fill(1, offset, offset + count); remaining -= count; return count
                        }
                    }
                    val incomplete = File(root, "interrupted-copy.dab")
                    assertTrue(runCatching { BackupArchive.copyBounded(brokenInput, incomplete) }.isFailure)
                    assertFalse(incomplete.exists())
                    assertEquals(guard, snapshot(repo))
                    val plain = File(root, "restored.zip")
                    BackupArchive.decrypt(encrypted, plain, fixturePassword)
                    val cutZip = File(root, "truncated.zip").apply { writeBytes(plain.readBytes().copyOf((plain.length() / 2).toInt())) }
                    assertTrue(repo.restoreFromZip(cutZip).isFailure)
                    assertEquals(guard, snapshot(repo))
                    val summary = repo.restoreFromZip(plain).getOrThrow()
                    assertEquals(5, summary.transactionCount)
                    assertEquals(2, summary.assetCount)
                    assertEquals(canonical(manifest.getJSONObject("data")), snapshot(repo))
                    verifyMoney(repo)
                    assertFalse(BackupPasswordStore(base).isConfigured())
                    assertNull(BackupSettingsStore(base).driveAccount)
                    result.put("all_portable_records_equal", true).put("wrong_password_preserved_destination", true)
                        .put("truncated_encrypted_preserved_destination", true).put("truncated_zip_preserved_destination", true)
                        .put("interrupted_copy_cleaned", true).put("receipt_decoded_and_hashed", true)
                        .put("balances_reports_charts_reconciled", true).put("device_credentials_not_transferred", true)
                        .put("portable_sha256", digest(snapshot(repo).toByteArray())).put("transactions", 5).put("assets", 2)
                }
                else -> error("Choose source or destination stage")
            }
        } finally {
            live.reloadForBackgroundCheck()
            assertTrue("The live ledger must remain unchanged", before == digest(snapshot(live, receipts = false).toByteArray()))
            assertTrue("Real account, encrypted password and automatic-backup settings must remain unchanged", deviceOptions == BackupSettingsStore(base).preferences.all)
            println("MIGRATION_PROGRESS live_ledger_and_device_options_unchanged")
            root.deleteRecursively()
            preferences.forEach { base.deleteSharedPreferences(it) }
        }
        result.put("live_ledger_unchanged", true).put("device_options_unchanged", true).put("passed", true)
        File(artifacts, "${args.getString("stage")}-result.json").writeText(result.toString(2))
        println("DEVICE_RESTORE_AUDIT " + result)
    }
}
