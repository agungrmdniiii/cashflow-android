package com.cashflow.app.data.backup

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.cashflow.app.data.db.DatabaseHelper
import com.cashflow.app.data.model.TransactionType
import com.cashflow.app.data.model.RecurringTransaction
import com.cashflow.app.data.service.RecurringSchedulerService
import java.time.LocalDate
import com.cashflow.app.data.repository.LedgerService
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupSummary(
    val assetCount: Int,
    val transactionCount: Int,
    val categoryCount: Int,
    val budgetCount: Int,
    val goalCount: Int,
    val debtCount: Int,
    val favoriteCount: Int = 0,
    val recurringCount: Int = 0,
    val schemaVersion: Int,
    val appVersion: String,
    val backupDate: String
) {
    val transactionsCount: Int get() = transactionCount
}

sealed class BackupValidationResult {
    data class Valid(val summary: BackupSummary, val parsedData: JSONObject) : BackupValidationResult()
    data class Invalid(val reason: String) : BackupValidationResult()
}

object BackupManager {
    const val MAX_BACKUP_BYTES = 32 * 1024 * 1024

    const val CURRENT_SCHEMA_VERSION = 10
    const val APP_VERSION = com.cashflow.app.BuildConfig.VERSION_NAME

    fun readBackupBytes(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            require(output.size().toLong() + count <= MAX_BACKUP_BYTES.toLong() + 128L) { "Berkas cadangan melebihi 32 MB." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    fun validateBackupZip(file: File): BackupValidationResult = try {
        java.util.zip.ZipFile(file).use { zip ->
            val entry = zip.getEntry("backup.json") ?: return BackupValidationResult.Invalid("backup.json tidak ditemukan.")
            zip.getInputStream(entry).use { validateBackupJson(readBackupBytes(it).toString(Charsets.UTF_8)) }
        }
    } catch (e: Exception) {
        BackupValidationResult.Invalid(e.message ?: "Berkas ZIP tidak valid.")
    }

    fun copyBackupZip(input: InputStream, destination: File) {
        try {
            destination.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    total += count
                    require(total <= 272L * 1024 * 1024) { "Arsip ZIP melebihi 272 MB." }
                    output.write(buffer, 0, count)
                }
            }
        } catch (e: Exception) { destination.delete(); throw e }
    }

    fun writeSafetyBackup(context: Context, db: SQLiteDatabase) {
        val directory = File(context.filesDir, "backups").apply { mkdirs() }
        val temporary = File(directory, "before-restore.tmp")
        FileOutputStream(temporary).use {
            it.write(createBackupJson(db).toByteArray(Charsets.UTF_8))
            it.fd.sync()
        }
        java.nio.file.Files.move(temporary.toPath(), File(directory, "before-restore.json").toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    }

    fun generateBackupFileName(): String {
        val now = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
        return "duitaing-backup-${now.format(formatter)}.json"
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Mengekstrak seluruh data lokal dari database menjadi JSON string terstruktur dan ber-checksum.
     */
    fun createBackupJson(db: SQLiteDatabase): String {
        val ownsTransaction = !db.inTransaction()
        if (ownsTransaction) db.beginTransaction()
        try {
        val root = JSONObject()
        val data = JSONObject()

        // 1. Assets
        val assetsArray = JSONArray()
        val assetCursor = db.query(DatabaseHelper.TABLE_ASSETS, null, null, null, null, null, null)
        assetCursor.use { cursor ->
            while (cursor.moveToNext()) {
                val item = JSONObject().apply {
                    put("id", cursor.getString(cursor.getColumnIndexOrThrow("id")))
                    put("name", cursor.getString(cursor.getColumnIndexOrThrow("name")))
                    put("type", cursor.getString(cursor.getColumnIndexOrThrow("type")))
                    put("opening_balance", cursor.getLong(cursor.getColumnIndexOrThrow("opening_balance")))
                    put("current_balance", cursor.getLong(cursor.getColumnIndexOrThrow("current_balance")))
                    put("currency", cursor.getString(cursor.getColumnIndexOrThrow("currency")))
                    put("is_default", cursor.getInt(cursor.getColumnIndexOrThrow("is_default")))
                    put("is_active", cursor.getInt(cursor.getColumnIndexOrThrow("is_active")))
                    val sIdx = cursor.getColumnIndex("is_sample")
                    put("is_sample", if (sIdx >= 0) cursor.getInt(sIdx) else 0)
                    put("note", cursor.getString(cursor.getColumnIndexOrThrow("note")) ?: "")
                    put("created_at", cursor.getLong(cursor.getColumnIndexOrThrow("created_at")))
                    put("updated_at", cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")))
                }
                assetsArray.put(item)
            }
        }
        data.put("assets", assetsArray)

        // 2. Categories
        val categoriesArray = JSONArray()
        val catCursor = db.query(DatabaseHelper.TABLE_CATEGORIES, null, null, null, null, null, null)
        catCursor.use { cursor ->
            while (cursor.moveToNext()) {
                val item = JSONObject().apply {
                    put("id", cursor.getString(cursor.getColumnIndexOrThrow("id")))
                    put("name", cursor.getString(cursor.getColumnIndexOrThrow("name")))
                    put("type", cursor.getString(cursor.getColumnIndexOrThrow("type")))
                    put("icon", cursor.getString(cursor.getColumnIndexOrThrow("icon")))
                    put("color", cursor.getString(cursor.getColumnIndexOrThrow("color")))
                    put("is_default", cursor.getInt(cursor.getColumnIndexOrThrow("is_default")))
                    put("is_active", cursor.getInt(cursor.getColumnIndexOrThrow("is_active")))
                }
                categoriesArray.put(item)
            }
        }
        data.put("categories", categoriesArray)

        // 3. Transactions
        val txArray = JSONArray()
        val txCursor = db.query(DatabaseHelper.TABLE_TRANSACTIONS, null, null, null, null, null, null)
        txCursor.use { cursor ->
            while (cursor.moveToNext()) {
                val item = JSONObject().apply {
                    put("id", cursor.getString(cursor.getColumnIndexOrThrow("id")))
                    put("type", cursor.getString(cursor.getColumnIndexOrThrow("type")))
                    put("amount", cursor.getLong(cursor.getColumnIndexOrThrow("amount")))
                    put("description", cursor.getString(cursor.getColumnIndexOrThrow("description")))
                    put("category_id", cursor.getString(cursor.getColumnIndexOrThrow("category_id")))
                    put("asset_id", cursor.getString(cursor.getColumnIndexOrThrow("asset_id")))
                    put("destination_asset_id", cursor.getString(cursor.getColumnIndexOrThrow("destination_asset_id")))
                    put("transaction_date", cursor.getString(cursor.getColumnIndexOrThrow("transaction_date")))
                    put("transaction_time", cursor.getString(cursor.getColumnIndexOrThrow("transaction_time")))
                    put("note", cursor.getString(cursor.getColumnIndexOrThrow("note")) ?: "")
                    put("attachment", cursor.getString(cursor.getColumnIndexOrThrow("attachment")))
                    put("input_method", cursor.getString(cursor.getColumnIndexOrThrow("input_method")))
                    val dIdx = cursor.getColumnIndex("debt_id")
                    put("debt_id", if (dIdx >= 0) cursor.getString(dIdx) else null)
                    val gIdx = cursor.getColumnIndex("goal_id")
                    put("goal_id", if (gIdx >= 0) cursor.getString(gIdx) else null)
                    val sIdx = cursor.getColumnIndex("is_sample")
                    put("is_sample", if (sIdx >= 0) cursor.getInt(sIdx) else 0)
                    val tIdx = cursor.getColumnIndex("tags")
                    put("tags", if (tIdx >= 0) cursor.getString(tIdx) else "")
                    val adjIdx = cursor.getColumnIndex("is_adjustment")
                    put("is_adjustment", if (adjIdx >= 0) cursor.getInt(adjIdx) else 0)
                    val dirIdx = cursor.getColumnIndex("adjustment_direction")
                    put("adjustment_direction", if (dirIdx >= 0) cursor.getInt(dirIdx) else 0)
                    put("created_at", cursor.getLong(cursor.getColumnIndexOrThrow("created_at")))
                    put("updated_at", cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")))
                }
                txArray.put(item)
            }
        }
        data.put("transactions", txArray)

        // 4. Budgets
        val budgetsArray = JSONArray()
        val bCursor = db.query(DatabaseHelper.TABLE_BUDGETS, null, null, null, null, null, null)
        bCursor.use { cursor ->
            while (cursor.moveToNext()) {
                val item = JSONObject().apply {
                    put("id", cursor.getString(cursor.getColumnIndexOrThrow("id")))
                    put("category_id", cursor.getString(cursor.getColumnIndexOrThrow("category_id")))
                    put("period_type", cursor.getString(cursor.getColumnIndexOrThrow("period_type")))
                    put("target_amount", cursor.getLong(cursor.getColumnIndexOrThrow("target_amount")))
                    put("created_at", cursor.getLong(cursor.getColumnIndexOrThrow("created_at")))
                }
                budgetsArray.put(item)
            }
        }
        data.put("budgets", budgetsArray)

        // 5. Financial Goals
        val goalsArray = JSONArray()
        val gCursor = db.query(DatabaseHelper.TABLE_GOALS, null, null, null, null, null, null)
        gCursor.use { cursor ->
            while (cursor.moveToNext()) {
                val item = JSONObject().apply {
                    put("id", cursor.getString(cursor.getColumnIndexOrThrow("id")))
                    put("name", cursor.getString(cursor.getColumnIndexOrThrow("name")))
                    put("target_amount", cursor.getLong(cursor.getColumnIndexOrThrow("target_amount")))
                    put("current_amount", cursor.getLong(cursor.getColumnIndexOrThrow("current_amount")))
                    put("deadline", cursor.getString(cursor.getColumnIndexOrThrow("deadline")))
                    put("asset_id", cursor.getString(cursor.getColumnIndexOrThrow("asset_id")))
                    put("note", cursor.getString(cursor.getColumnIndexOrThrow("note")) ?: "")
                    put("created_at", cursor.getLong(cursor.getColumnIndexOrThrow("created_at")))
                    put("updated_at", cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")))
                }
                goalsArray.put(item)
            }
        }
        data.put("goals", goalsArray)

        // 6. Debts
        val debtsArray = JSONArray()
        val dCursor = db.query(DatabaseHelper.TABLE_DEBTS, null, null, null, null, null, null)
        dCursor.use { cursor ->
            while (cursor.moveToNext()) {
                val item = JSONObject().apply {
                    put("id", cursor.getString(cursor.getColumnIndexOrThrow("id")))
                    put("title", cursor.getString(cursor.getColumnIndexOrThrow("title")))
                    put("lender_name", cursor.getString(cursor.getColumnIndexOrThrow("lender_name")))
                    put("direction", cursor.getString(cursor.getColumnIndexOrThrow("direction")))
                    put("total_amount", cursor.getLong(cursor.getColumnIndexOrThrow("total_amount")))
                    put("paid_amount", cursor.getLong(cursor.getColumnIndexOrThrow("paid_amount")))
                    put("due_date", cursor.getString(cursor.getColumnIndexOrThrow("due_date")))
                    put("status", cursor.getString(cursor.getColumnIndexOrThrow("status")))
                    put("note", cursor.getString(cursor.getColumnIndexOrThrow("note")) ?: "")
                    put("created_at", cursor.getLong(cursor.getColumnIndexOrThrow("created_at")))
                    put("updated_at", cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")))
                }
                debtsArray.put(item)
            }
        }
        data.put("debts", debtsArray)

        // 7. Favorite Templates
        val favsArray = JSONArray()
        val fCursor = db.query(DatabaseHelper.TABLE_FAVORITES, null, null, null, null, null, null)
        fCursor.use { cursor ->
            while (cursor.moveToNext()) {
                val item = JSONObject().apply {
                    put("id", cursor.getString(cursor.getColumnIndexOrThrow("id")))
                    put("name", cursor.getString(cursor.getColumnIndexOrThrow("name")))
                    put("type", cursor.getString(cursor.getColumnIndexOrThrow("type")))
                    put("amount", cursor.getLong(cursor.getColumnIndexOrThrow("amount")))
                    put("category_id", if (cursor.isNull(cursor.getColumnIndexOrThrow("category_id"))) null else cursor.getString(cursor.getColumnIndexOrThrow("category_id")))
                    put("asset_id", cursor.getString(cursor.getColumnIndexOrThrow("asset_id")))
                    put("destination_asset_id", if (cursor.isNull(cursor.getColumnIndexOrThrow("destination_asset_id"))) null else cursor.getString(cursor.getColumnIndexOrThrow("destination_asset_id")))
                    put("note", cursor.getString(cursor.getColumnIndexOrThrow("note")) ?: "")
                    put("created_at", cursor.getLong(cursor.getColumnIndexOrThrow("created_at")))
                }
                favsArray.put(item)
            }
        }
        data.put("favorite_templates", favsArray)

        // 8. Recurring Transactions
        val recArray = JSONArray()
        try {
            val rCursor = db.query(DatabaseHelper.TABLE_RECURRING, null, null, null, null, null, null)
            rCursor.use { cursor ->
                while (cursor.moveToNext()) {
                    val item = JSONObject().apply {
                        put("id", cursor.getString(cursor.getColumnIndexOrThrow("id")))
                        put("name", cursor.getString(cursor.getColumnIndexOrThrow("name")))
                        put("type", cursor.getString(cursor.getColumnIndexOrThrow("type")))
                        put("amount", cursor.getLong(cursor.getColumnIndexOrThrow("amount")))
                        put("category_id", if (cursor.isNull(cursor.getColumnIndexOrThrow("category_id"))) null else cursor.getString(cursor.getColumnIndexOrThrow("category_id")))
                        put("asset_id", cursor.getString(cursor.getColumnIndexOrThrow("asset_id")))
                        put("destination_asset_id", if (cursor.isNull(cursor.getColumnIndexOrThrow("destination_asset_id"))) null else cursor.getString(cursor.getColumnIndexOrThrow("destination_asset_id")))
                        put("frequency", cursor.getString(cursor.getColumnIndexOrThrow("frequency")))
                        put("interval", cursor.getInt(cursor.getColumnIndexOrThrow("interval")))
                        put("start_date", cursor.getString(cursor.getColumnIndexOrThrow("start_date")))
                        put("end_date", if (cursor.isNull(cursor.getColumnIndexOrThrow("end_date"))) null else cursor.getString(cursor.getColumnIndexOrThrow("end_date")))
                        val totalOccIdx = cursor.getColumnIndex("total_occurrences")
                        if (totalOccIdx >= 0 && !cursor.isNull(totalOccIdx)) put("total_occurrences", cursor.getInt(totalOccIdx))
                        put("occurrences_done", cursor.getInt(cursor.getColumnIndexOrThrow("occurrences_done")))
                        put("mode", cursor.getString(cursor.getColumnIndexOrThrow("mode")))
                        put("linked_debt_id", if (cursor.isNull(cursor.getColumnIndexOrThrow("linked_debt_id"))) null else cursor.getString(cursor.getColumnIndexOrThrow("linked_debt_id")))
                        put("last_executed_date", if (cursor.isNull(cursor.getColumnIndexOrThrow("last_executed_date"))) null else cursor.getString(cursor.getColumnIndexOrThrow("last_executed_date")))
                        put("is_active", cursor.getInt(cursor.getColumnIndexOrThrow("is_active")))
                        put("note", cursor.getString(cursor.getColumnIndexOrThrow("note")) ?: "")
                        put("created_at", cursor.getLong(cursor.getColumnIndexOrThrow("created_at")))
                    }
                    recArray.put(item)
                }
            }
        } catch (_: Exception) {}
        data.put("recurring_transactions", recArray)
        val executions = JSONArray()
        db.query(DatabaseHelper.TABLE_RECURRING_EXECUTIONS, null, null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                executions.put(JSONObject().apply {
                    put("id", cursor.getString(cursor.getColumnIndexOrThrow("id")))
                    put("recurring_id", cursor.getString(cursor.getColumnIndexOrThrow("recurring_id")))
                    put("scheduled_date", cursor.getString(cursor.getColumnIndexOrThrow("scheduled_date")))
                    put("transaction_id", cursor.getString(cursor.getColumnIndexOrThrow("transaction_id")))
                    put("executed_at", cursor.getLong(cursor.getColumnIndexOrThrow("executed_at")))
                    put("status", cursor.getString(cursor.getColumnIndexOrThrow("status")))
                })
            }
        }
        data.put("recurring_executions", executions)

        val dataString = data.toString()
        val checksum = sha256(dataString)

        val metadata = JSONObject().apply {
            put("schema_version", CURRENT_SCHEMA_VERSION)
            put("app_version", APP_VERSION)
            put("backup_date", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            put("checksum", checksum)
            val counts = JSONObject().apply {
                put("assets", assetsArray.length())
                put("categories", categoriesArray.length())
                put("transactions", txArray.length())
                put("budgets", budgetsArray.length())
                put("goals", goalsArray.length())
                put("debts", debtsArray.length())
                put("favorite_templates", favsArray.length())
                put("recurring_transactions", recArray.length())
            }
            put("record_counts", counts)
        }

        root.put("metadata", metadata)
        root.put("data", data)

        return root.toString(2)
        } finally {
            if (ownsTransaction) db.endTransaction()
        }
    }

    /**
     * Memvalidasi file JSON cadangan sebelum diimpor/dipulihkan.
     */
    fun validateBackupJson(jsonString: String): BackupValidationResult {
        if (jsonString.length > MAX_BACKUP_BYTES) return BackupValidationResult.Invalid("Berkas cadangan terlalu besar (maksimal 32 MB).")
        if (jsonString.isBlank()) {
            return BackupValidationResult.Invalid("File cadangan kosong.")
        }

        val root = try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            return BackupValidationResult.Invalid("Format berkas bukan JSON yang valid.")
        }

        if (!root.has("metadata") || !root.has("data")) {
            return BackupValidationResult.Invalid("Struktur cadangan tidak valid (metadata atau data tidak ditemukan).")
        }

        val metadata = root.optJSONObject("metadata")
            ?: return BackupValidationResult.Invalid("Metadata cadangan harus berupa objek.")
        val data = root.optJSONObject("data")
            ?: return BackupValidationResult.Invalid("Data cadangan harus berupa objek.")
        for (name in listOf("assets", "categories", "transactions")) {
            if (data.optJSONArray(name) == null) return BackupValidationResult.Invalid("Daftar $name tidak ditemukan atau tidak valid.")
        }
        for (name in listOf("budgets", "goals", "debts", "favorite_templates", "recurring_transactions", "recurring_executions")) {
            if (data.has(name) && data.optJSONArray(name) == null) return BackupValidationResult.Invalid("Daftar $name tidak valid.")
        }

        val schemaVersion = metadata.optInt("schema_version", 0)
        if (schemaVersion < 1 || schemaVersion > CURRENT_SCHEMA_VERSION) {
            return BackupValidationResult.Invalid("Versi skema ($schemaVersion) tidak didukung aplikasi ini.")
        }

        val expectedChecksum = metadata.optString("checksum", "")
        if (expectedChecksum.isBlank()) {
            return BackupValidationResult.Invalid("Checksum integritas tidak ditemukan.")
        }

        val actualChecksum = sha256(data.toString())
        if (expectedChecksum != actualChecksum) {
            return BackupValidationResult.Invalid("Integritas data rusak: Checksum tidak cocok.")
        }

        val summary = BackupSummary(
            assetCount = data.getJSONArray("assets").length(),
            transactionCount = data.getJSONArray("transactions").length(),
            categoryCount = data.getJSONArray("categories").length(),
            budgetCount = data.optJSONArray("budgets")?.length() ?: 0,
            goalCount = data.optJSONArray("goals")?.length() ?: 0,
            debtCount = data.optJSONArray("debts")?.length() ?: 0,
            favoriteCount = data.optJSONArray("favorite_templates")?.length() ?: 0,
            recurringCount = data.optJSONArray("recurring_transactions")?.length() ?: 0,
            schemaVersion = schemaVersion,
            appVersion = metadata.optString("app_version", "1.0.0"),
            backupDate = metadata.optString("backup_date", "Tidak diketahui")
        )

        return BackupValidationResult.Valid(summary, data)
    }

    /**
     * Memulihkan database dari JSON secara atomik dengan backup pengaman pra-restore (safety backup).
     */
    fun restoreBackup(db: SQLiteDatabase, data: JSONObject): Result<Unit> {
        if (listOf("assets", "categories", "transactions").any { data.optJSONArray(it) == null }) {
            return Result.failure(IllegalArgumentException("Struktur cadangan tidak lengkap. Data lama tidak diubah."))
        }
        db.beginTransaction()
        return try {
            // Bersihkan data lama
            db.execSQL("DELETE FROM ${DatabaseHelper.TABLE_TRANSACTIONS}")
            db.execSQL("DELETE FROM ${DatabaseHelper.TABLE_BUDGETS}")
            db.execSQL("DELETE FROM ${DatabaseHelper.TABLE_GOALS}")
            db.execSQL("DELETE FROM ${DatabaseHelper.TABLE_DEBTS}")
            db.execSQL("DELETE FROM ${DatabaseHelper.TABLE_FAVORITES}")
            db.execSQL("DELETE FROM ${DatabaseHelper.TABLE_RECURRING_EXECUTIONS}")
            db.execSQL("DELETE FROM ${DatabaseHelper.TABLE_RECURRING}")
            db.execSQL("DELETE FROM ${DatabaseHelper.TABLE_ASSETS}")
            db.execSQL("DELETE FROM ${DatabaseHelper.TABLE_CATEGORIES} WHERE is_default = 0")

            // Pulihkan Kategori jika ada kustom
            val categories = data.optJSONArray("categories")
            if (categories != null) {
                for (i in 0 until categories.length()) {
                    val obj = categories.getJSONObject(i)
                    val cv = ContentValues().apply {
                        put("id", obj.getString("id"))
                        put("name", obj.getString("name"))
                        put("type", obj.getString("type"))
                        put("icon", obj.optString("icon", "category"))
                        put("color", obj.optString("color", "#111111"))
                        put("is_default", obj.optInt("is_default", 0))
                        put("is_active", obj.optInt("is_active", 1))
                    }
                    db.insertWithOnConflict(DatabaseHelper.TABLE_CATEGORIES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }

            // Pulihkan Aset
            val assets = data.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val obj = assets.getJSONObject(i)
                    val cv = ContentValues().apply {
                        put("id", obj.getString("id"))
                        put("name", obj.getString("name"))
                        put("type", obj.getString("type"))
                        put("opening_balance", obj.optLong("opening_balance", 0L))
                        put("current_balance", obj.optLong("current_balance", obj.optLong("opening_balance", 0L)))
                        put("currency", obj.optString("currency", "IDR"))
                        put("is_default", obj.optInt("is_default", 0))
                        put("is_active", obj.optInt("is_active", 1))
                        put("is_sample", obj.optInt("is_sample", 0))
                        put("note", obj.optString("note", ""))
                        put("created_at", obj.optLong("created_at", System.currentTimeMillis()))
                        put("updated_at", obj.optLong("updated_at", System.currentTimeMillis()))
                    }
                    db.insertOrThrow(DatabaseHelper.TABLE_ASSETS, null, cv)
                }
            }

            // Pulihkan Transaksi
            val transactions = data.optJSONArray("transactions")
            if (transactions != null) {
                for (i in 0 until transactions.length()) {
                    val obj = transactions.getJSONObject(i)
                    val restoredTx = com.cashflow.app.data.model.Transaction(
                        id = obj.getString("id"), type = obj.getString("type"), amount = obj.getLong("amount"),
                        description = obj.getString("description"), assetId = obj.getString("asset_id"),
                        destinationAssetId = if (obj.isNull("destination_asset_id")) null else obj.optString("destination_asset_id"),
                        transactionDate = obj.getString("transaction_date"), transactionTime = obj.optString("transaction_time", "12:00"),
                        debtId = if (obj.isNull("debt_id")) null else obj.optString("debt_id"),
                        goalId = if (obj.isNull("goal_id")) null else obj.optString("goal_id"))
                    LedgerService.validateTransaction(restoredTx).getOrThrow()
                    require(restoredTx.id.isNotBlank()) { "ID transaksi kosong." }
                    for (account in listOfNotNull(restoredTx.assetId, restoredTx.destinationAssetId)) {
                        db.rawQuery("SELECT 1 FROM ${DatabaseHelper.TABLE_ASSETS} WHERE id = ?", arrayOf(account)).use {
                            require(it.moveToFirst()) { "Rekening transaksi cadangan tidak ditemukan." }
                        }
                    }
                    val cv = ContentValues().apply {
                        put("id", obj.getString("id"))
                        put("type", obj.getString("type"))
                        put("amount", obj.getLong("amount"))
                        put("description", obj.getString("description"))
                        put("category_id", if (obj.isNull("category_id")) null else obj.optString("category_id"))
                        put("asset_id", obj.getString("asset_id"))
                        put("destination_asset_id", if (obj.isNull("destination_asset_id")) null else obj.optString("destination_asset_id"))
                        put("transaction_date", obj.getString("transaction_date"))
                        put("transaction_time", obj.optString("transaction_time", "12:00"))
                        put("note", obj.optString("note", ""))
                        put("attachment", if (obj.isNull("attachment")) null else obj.optString("attachment"))
                        put("input_method", obj.optString("input_method", "manual"))
                        put("debt_id", if (obj.isNull("debt_id")) null else obj.optString("debt_id"))
                        put("goal_id", if (obj.isNull("goal_id")) null else obj.optString("goal_id"))
                        put("tags", obj.optString("tags", ""))
                        put("is_adjustment", obj.optInt("is_adjustment", 0))
                        val rawDir = if (obj.has("adjustment_direction")) obj.optInt("adjustment_direction", 0) else 0
                        val dir = if (rawDir != 0) {
                            rawDir
                        } else if (obj.optString("type") == TransactionType.ADJUSTMENT.value || obj.optInt("is_adjustment", 0) == 1) {
                            if (obj.optString("note", "").contains("(-)")) -1 else 1
                        } else {
                            0
                        }
                        put("adjustment_direction", dir)
                        put("is_sample", obj.optInt("is_sample", 0))
                        put("created_at", obj.optLong("created_at", System.currentTimeMillis()))
                        put("updated_at", obj.optLong("updated_at", System.currentTimeMillis()))
                    }
                    db.insertOrThrow(DatabaseHelper.TABLE_TRANSACTIONS, null, cv)
                }
            }

            // Pulihkan Anggaran
            val budgets = data.optJSONArray("budgets")
            if (budgets != null) {
                for (i in 0 until budgets.length()) {
                    val obj = budgets.getJSONObject(i)
                    val cv = ContentValues().apply {
                        put("id", obj.getString("id"))
                        put("category_id", obj.getString("category_id"))
                        put("period_type", obj.optString("period_type", "monthly"))
                        put("target_amount", obj.getLong("target_amount"))
                        put("created_at", obj.optLong("created_at", System.currentTimeMillis()))
                    }
                    db.insertWithOnConflict(DatabaseHelper.TABLE_BUDGETS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }

            // Pulihkan Target Keuangan
            val goals = data.optJSONArray("goals")
            if (goals != null) {
                for (i in 0 until goals.length()) {
                    val obj = goals.getJSONObject(i)
                    val cv = ContentValues().apply {
                        put("id", obj.getString("id"))
                        put("name", obj.getString("name"))
                        put("target_amount", obj.getLong("target_amount"))
                        put("current_amount", obj.optLong("current_amount", 0L))
                        put("deadline", obj.getString("deadline"))
                        put("asset_id", if (obj.isNull("asset_id")) null else obj.optString("asset_id"))
                        put("note", obj.optString("note", ""))
                        put("created_at", obj.optLong("created_at", System.currentTimeMillis()))
                        put("updated_at", obj.optLong("updated_at", System.currentTimeMillis()))
                    }
                    db.insertWithOnConflict(DatabaseHelper.TABLE_GOALS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }

            // Pulihkan Hutang
            val debts = data.optJSONArray("debts")
            if (debts != null) {
                for (i in 0 until debts.length()) {
                    val obj = debts.getJSONObject(i)
                    val cv = ContentValues().apply {
                        put("id", obj.getString("id"))
                        put("title", obj.getString("title"))
                        put("lender_name", obj.getString("lender_name"))
                        put("direction", obj.optString("direction", "debt").also { require(it in listOf("debt", "receivable")) })
                        put("total_amount", obj.getLong("total_amount"))
                        put("paid_amount", obj.optLong("paid_amount", 0L))
                        put("due_date", obj.getString("due_date"))
                        put("status", obj.optString("status", "unpaid"))
                        put("note", obj.optString("note", ""))
                        put("created_at", obj.optLong("created_at", System.currentTimeMillis()))
                        put("updated_at", obj.optLong("updated_at", System.currentTimeMillis()))
                    }
                    db.insertWithOnConflict(DatabaseHelper.TABLE_DEBTS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }

            // Pulihkan Transaksi Favorit
            val favs = data.optJSONArray("favorite_templates")
            if (favs != null) {
                for (i in 0 until favs.length()) {
                    val obj = favs.getJSONObject(i)
                    val cv = ContentValues().apply {
                        put("id", obj.getString("id"))
                        put("name", obj.getString("name"))
                        put("type", obj.getString("type"))
                        put("amount", obj.getLong("amount"))
                        put("category_id", if (obj.isNull("category_id")) null else obj.optString("category_id"))
                        put("asset_id", obj.getString("asset_id"))
                        put("destination_asset_id", if (obj.isNull("destination_asset_id")) null else obj.optString("destination_asset_id"))
                        put("note", obj.optString("note", ""))
                        put("created_at", obj.optLong("created_at", System.currentTimeMillis()))
                    }
                    db.insertWithOnConflict(DatabaseHelper.TABLE_FAVORITES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }

            // Pulihkan Transaksi Berulang
            val recurringList = data.optJSONArray("recurring_transactions")
            if (recurringList != null) {
                for (i in 0 until recurringList.length()) {
                    val obj = recurringList.getJSONObject(i)
                    val cv = ContentValues().apply {
                        put("id", obj.getString("id"))
                        put("name", obj.getString("name"))
                        put("type", obj.getString("type"))
                        put("amount", obj.getLong("amount"))
                        put("category_id", if (obj.isNull("category_id")) null else obj.optString("category_id"))
                        put("asset_id", obj.getString("asset_id"))
                        put("destination_asset_id", if (obj.isNull("destination_asset_id")) null else obj.optString("destination_asset_id"))
                        put("frequency", obj.optString("frequency", "MONTHLY"))
                        put("interval", obj.optInt("interval", 1))
                        put("start_date", obj.getString("start_date"))
                        put("end_date", if (obj.isNull("end_date")) null else obj.optString("end_date"))
                        if (obj.has("total_occurrences") && !obj.isNull("total_occurrences")) {
                            put("total_occurrences", obj.getInt("total_occurrences"))
                        }
                        put("occurrences_done", obj.optInt("occurrences_done", 0))
                        put("mode", obj.optString("mode", "AUTO"))
                        put("linked_debt_id", if (obj.isNull("linked_debt_id")) null else obj.optString("linked_debt_id"))
                        put("last_executed_date", if (obj.isNull("last_executed_date")) null else obj.optString("last_executed_date"))
                        put("is_active", obj.optInt("is_active", 1))
                        put("note", obj.optString("note", ""))
                        put("created_at", obj.optLong("created_at", System.currentTimeMillis()))
                    }
                    db.insertWithOnConflict(DatabaseHelper.TABLE_RECURRING, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }

            val executions = data.optJSONArray("recurring_executions")
            if (executions != null) {
                for (i in 0 until executions.length()) {
                    val obj = executions.getJSONObject(i)
                    LocalDate.parse(obj.getString("scheduled_date"))
                    val status = obj.getString("status")
                    require(status == "completed" || status == "skipped") { "Status eksekusi jadwal tidak valid." }
                    val cv = ContentValues().apply {
                        put("id", obj.getString("id"))
                        put("recurring_id", obj.getString("recurring_id"))
                        put("scheduled_date", obj.getString("scheduled_date"))
                        put("transaction_id", if (obj.isNull("transaction_id")) null else obj.optString("transaction_id"))
                        put("executed_at", obj.getLong("executed_at"))
                        put("status", status)
                    }
                    db.insertOrThrow(DatabaseHelper.TABLE_RECURRING_EXECUTIONS, null, cv)
                }
            } else {
                // Older backups retained the automatic transaction marker and last processed date.
                db.execSQL("""
                    INSERT OR IGNORE INTO ${DatabaseHelper.TABLE_RECURRING_EXECUTIONS}
                    (id, recurring_id, scheduled_date, transaction_id, executed_at, status)
                    SELECT 'legacy_' || r.id || '_' || t.transaction_date, r.id, t.transaction_date, t.id, t.created_at, 'completed'
                    FROM ${DatabaseHelper.TABLE_TRANSACTIONS} t JOIN ${DatabaseHelper.TABLE_RECURRING} r
                    ON t.description = r.name AND t.asset_id = r.asset_id AND t.amount = r.amount AND t.type = r.type
                    WHERE t.note LIKE '[Otomatis Berulang]%'
                """.trimIndent())
                if (recurringList != null) for (i in 0 until recurringList.length()) {
                    val obj = recurringList.getJSONObject(i)
                    val lastDate = obj.optString("last_executed_date", "")
                    if (lastDate.isBlank()) continue
                    val rec = RecurringTransaction(id = obj.getString("id"), name = obj.getString("name"),
                        type = obj.getString("type"), amount = obj.getLong("amount"), assetId = obj.getString("asset_id"),
                        startDate = obj.getString("start_date"), interval = obj.optInt("interval", 1),
                        frequency = obj.optString("frequency", "monthly"),
                        endDate = if (obj.isNull("end_date")) null else obj.optString("end_date"),
                        totalOccurrences = if (obj.isNull("total_occurrences")) null else obj.optInt("total_occurrences"))
                    for (date in RecurringSchedulerService.generateScheduledDates(rec, LocalDate.parse(lastDate))) {
                        db.execSQL("INSERT OR IGNORE INTO ${DatabaseHelper.TABLE_RECURRING_EXECUTIONS} (id, recurring_id, scheduled_date, executed_at, status) VALUES (?, ?, ?, ?, 'skipped')",
                            arrayOf<Any>("legacy_${rec.id}_$date", rec.id, date.toString(), System.currentTimeMillis()))
                    }
                }
            }
            db.execSQL("""
                UPDATE ${DatabaseHelper.TABLE_RECURRING}
                SET occurrences_done = (SELECT COUNT(*) FROM ${DatabaseHelper.TABLE_RECURRING_EXECUTIONS} e WHERE e.recurring_id = ${DatabaseHelper.TABLE_RECURRING}.id),
                    last_executed_date = (SELECT MAX(scheduled_date) FROM ${DatabaseHelper.TABLE_RECURRING_EXECUTIONS} e WHERE e.recurring_id = ${DatabaseHelper.TABLE_RECURRING}.id)
            """.trimIndent())

            LedgerService.migrateLegacyGoalTransactions(db)
            LedgerService.migrateLegacyDebtTransactions(db)
            // Jalankan recalculate saldo untuk memverifikasi konsistensi saldo aset
            LedgerService.recalculateAllBalances(db)

            db.setTransactionSuccessful()
            Result.success(Unit)
        } catch (e: Exception) {
            // endTransaction rolls the entire restore back; never recurse into partial data.
            Result.failure(e)
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Membuat file ZIP cadangan yang menyertakan database JSON dan berkas foto struk lokal.
     */
    fun createBackupZipWithReceipts(context: Context, db: SQLiteDatabase, destZipFile: File): Result<Unit> {
        return try {
            val jsonContent = createBackupJson(db)
            val receiptsDir = context.filesDir.resolve("receipts")
            val jsonBytes = jsonContent.toByteArray(Charsets.UTF_8)
            require(jsonBytes.size <= MAX_BACKUP_BYTES) { "Data JSON melebihi 32 MB." }
            val referenced = mutableSetOf<String>()
            val transactions = JSONObject(jsonContent).getJSONObject("data").getJSONArray("transactions")
            for (index in 0 until transactions.length()) {
                val path = transactions.getJSONObject(index).optString("attachment", "")
                if (path.isNotBlank()) referenced.add(File(path).name)
            }
            val receiptFiles = receiptsDir.listFiles().orEmpty().filter { it.isFile && it.name in referenced }
            require(receiptFiles.size <= 1000 && receiptFiles.all { it.length() <= MAX_BACKUP_BYTES } &&
                jsonBytes.size.toLong() + receiptFiles.sumOf { it.length() } <= 256L * 1024 * 1024) { "Jumlah atau ukuran struk melebihi batas cadangan ZIP (1000 foto, 32 MB/foto, total 256 MB)." }

            ZipOutputStream(FileOutputStream(destZipFile)).use { zos ->
                // 1. Tambah backup.json
                val jsonEntry = ZipEntry("backup.json")
                zos.putNextEntry(jsonEntry)
                zos.write(jsonBytes)
                zos.closeEntry()

                // 2. Tambah file foto struk jika direktori ada
                if (receiptsDir.exists() && receiptsDir.isDirectory) {
                    val files = receiptsDir.listFiles() ?: emptyArray()
                    for (file in files) {
                        if (file.isFile && file.name in referenced) {
                            val entry = ZipEntry("receipts/${file.name}")
                            zos.putNextEntry(entry)
                            FileInputStream(file).use { fis ->
                                fis.copyTo(zos)
                            }
                            zos.closeEntry()
                        }
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Memulihkan data dari berkas ZIP cadangan (JSON + foto struk).
     */
    fun restoreBackupFromZip(context: Context, db: SQLiteDatabase, zipFile: File): Result<BackupSummary> {
        val staging = java.nio.file.Files.createTempDirectory(context.cacheDir.toPath(), "restore-").toFile()
        val copiedReceipts = mutableListOf<File>()
        var committed = false
        return try {
            val seen = mutableSetOf<String>()
            var totalBytes = 0L
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    require(seen.add(entry.name) && seen.size <= 1001) { "ZIP berisi entri ganda atau terlalu banyak berkas." }
                    if (entry.isDirectory) {
                        require(entry.name == "receipts/") { "Folder ZIP tidak diizinkan." }
                    } else {
                        val receiptName = entry.name.removePrefix("receipts/")
                        require(entry.name == "backup.json" || (entry.name.startsWith("receipts/") &&
                            receiptName.isNotBlank() && receiptName != "." && receiptName != ".." &&
                            '/' !in receiptName && '\\' !in receiptName)) { "Jalur berkas ZIP tidak aman." }
                        val target = File(staging, entry.name).canonicalFile
                        require(target.path.startsWith(staging.canonicalPath + File.separator)) { "Jalur berkas ZIP tidak aman." }
                        target.parentFile!!.mkdirs()
                        var entryBytes = 0L
                        FileOutputStream(target).use { out ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = zis.read(buffer)
                                if (count == -1) break
                                entryBytes += count
                                totalBytes += count
                                require(entryBytes <= MAX_BACKUP_BYTES && totalBytes <= 256L * 1024 * 1024) { "Ukuran hasil ekstraksi ZIP melebihi batas." }
                                out.write(buffer, 0, count)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val jsonFile = File(staging, "backup.json")
            require(jsonFile.isFile) { "File backup.json tidak ditemukan di dalam ZIP." }
            val validation = validateBackupJson(jsonFile.readText(Charsets.UTF_8))
            require(validation is BackupValidationResult.Valid) {
                (validation as BackupValidationResult.Invalid).reason
            }
            writeSafetyBackup(context, db)
            val receiptsDir = context.filesDir.resolve("receipts").apply { mkdirs() }
            val restoredPaths = mutableMapOf<String, String>()
            val prefix = java.util.UUID.randomUUID().toString()
            for (source in File(staging, "receipts").listFiles().orEmpty()) {
                // Unique names preserve old receipts even if the process stops before the DB commits.
                val target = File(receiptsDir, "${prefix}_${source.name}")
                copiedReceipts.add(target)
                source.inputStream().use { input -> FileOutputStream(target).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                } }
                restoredPaths[source.name] = target.absolutePath
            }
            val transactions = validation.parsedData.optJSONArray("transactions")
            if (transactions != null) for (i in 0 until transactions.length()) {
                val tx = transactions.getJSONObject(i)
                val name = File(tx.optString("attachment", "")).name
                if (name.isNotBlank()) {
                    val path = restoredPaths[name]
                        ?: throw IllegalArgumentException("Foto struk $name tidak ditemukan di ZIP. Data lama tidak diubah. Gunakan cadangan ZIP lengkap atau pulihkan JSON tanpa foto.")
                    tx.put("attachment", path)
                }
            }
            restoreBackup(db, validation.parsedData).getOrThrow()
            committed = true
            Result.success(validation.summary)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            if (!committed) copiedReceipts.forEach { it.delete() }
            staging.deleteRecursively()
        }
    }
}
