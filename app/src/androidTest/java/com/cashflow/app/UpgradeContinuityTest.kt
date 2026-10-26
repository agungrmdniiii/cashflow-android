package com.cashflow.app

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate

/** Opt-in example data on the owned emulator. Platform APIs intentionally work on both APKs. */
class UpgradeContinuityTest {
    @Test fun control() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Explicit upgrade audit required", args.getString("upgradeAudit") == "approved")
        check(Build.HARDWARE in listOf("ranchu", "goldfish")) { "Never seed a personal phone." }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val code = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        val expected = File(context.filesDir, "upgrade-audit-fixture.json")
        val photo = File(context.filesDir, "receipts/upgrade-audit.jpg")
        val keys = listOf("user_name", "theme_mode", "balance_hidden", "favorite_category_ids", "daily_reminder_enabled",
            "daily_reminder_time", "initial_setup_completed", "favorite_direct_save", "last_expense_cat", "last_expense_asset")
        val prefs = context.getSharedPreferences("duit_aing_preferences", 0)
        fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        fun snapshot(): JSONObject {
            val tables = JSONObject()
            SQLiteDatabase.openDatabase(context.getDatabasePath("cashflow.db").path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("PRAGMA integrity_check", null).use { it.moveToFirst(); assertEquals("ok", it.getString(0)) }
                db.rawQuery("PRAGMA foreign_key_check", null).use { assertEquals(0, it.count) }
                db.rawQuery("SELECT attachment FROM transactions WHERE id = 'upgrade_expense'", null).use {
                    assertTrue(it.moveToFirst())
                    assertEquals("The transaction must point to its actual receipt", photo.absolutePath, it.getString(0))
                    val decoded = android.graphics.BitmapFactory.decodeFile(it.getString(0))
                    assertNotNull("The app must be able to decode the linked receipt", decoded)
                    decoded?.recycle()
                }
                db.rawQuery("SELECT DISTINCT type FROM transactions ORDER BY type", null).use { cursor ->
                    val kinds = mutableSetOf<String>()
                    while (cursor.moveToNext()) kinds += cursor.getString(0)
                    assertEquals(setOf("income", "expense", "transfer", "adjustment", "allocation", "release",
                        "loan_received", "loan_given", "debt_payment", "receivable_payment"), kinds)
                }
                for (table in listOf("assets", "transactions", "budgets", "financial_goals", "debts", "favorite_templates", "recurring_transactions", "recurring_executions", "categories")) {
                    val columns = mutableListOf<String>()
                    db.rawQuery("PRAGMA table_info($table)", null).use { while (it.moveToNext()) columns += it.getString(1) }
                    val query = columns.joinToString(",") { "quote(\"$it\")" }
                    val rows = JSONArray()
                    db.rawQuery("SELECT $query FROM $table" + (if (table == "categories") " WHERE id = 'upgrade_custom'" else "") + " ORDER BY $query", null).use { cursor ->
                        while (cursor.moveToNext()) rows.put(JSONArray().apply { for (i in columns.indices) put(cursor.getString(i)) })
                    }
                    tables.put(table, rows)
                }
            }
            val preferences = JSONObject()
            keys.forEach { key ->
                val value = prefs.all[key]
                preferences.put(key, if (value is Set<*>) JSONArray(value.map { it.toString() }.sorted()) else value)
            }
            assertTrue("Receipt must remain readable", photo.isFile)
            return JSONObject().put("tables", tables).put("preferences", preferences).put("receipt_sha256", digest(photo.readBytes()))
        }
        when (args.getString("stage")) {
            "seed" -> {
                check(code == 132L && !expected.exists()) { "Seed only a fresh public132 audit installation." }
                val now = 1790000000000L
                val date = LocalDate.now().toString()
                SQLiteDatabase.openDatabase(context.getDatabasePath("cashflow.db").path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                    fun insert(table: String, vararg values: Pair<String, Any?>) {
                        val row = ContentValues()
                        values.forEach { (key, value) -> when (value) {
                            null -> row.putNull(key)
                            is Long -> row.put(key, value)
                            is Int -> row.put(key, value)
                            else -> row.put(key, value.toString())
                        } }
                        db.insertOrThrow(table, null, row)
                    }
                    db.beginTransaction()
                    try {
                        listOf("recurring_executions", "recurring_transactions", "transactions", "budgets", "financial_goals", "debts", "favorite_templates", "assets").forEach { db.delete(it, null, null) }
                        insert("categories", "id" to "upgrade_custom", "name" to "Makan Uji", "type" to "expense", "icon" to "restaurant", "color" to "#8D3B3B", "is_default" to 0, "is_active" to 1)
                        for ((id, name, opening, balance) in listOf(listOf("upgrade_cash", "Kas Uji", 1_000_000L, 1_160_000L), listOf("upgrade_bank", "Bank Uji", 500_000L, 580_000L))) {
                            insert("assets", "id" to id, "name" to name, "type" to "Tunai", "opening_balance" to opening, "current_balance" to balance,
                                "is_default" to if (id == "upgrade_cash") 1 else 0, "created_at" to now, "updated_at" to now)
                        }
                        fun tx(id: String, type: String, amount: Long, asset: String = "upgrade_cash", destination: String? = null,
                            debt: String? = null, goal: String? = null, receipt: String? = null, adjustment: Int = 0) = insert("transactions",
                            "id" to id, "type" to type, "amount" to amount, "description" to "Contoh $id", "asset_id" to asset,
                            "category_id" to if (type == "expense") "upgrade_custom" else if (type == "income") "cat_gaji" else null,
                            "destination_asset_id" to destination, "transaction_date" to date, "transaction_time" to "12:00", "attachment" to receipt,
                            "debt_id" to debt, "goal_id" to goal, "is_adjustment" to if (adjustment != 0) 1 else 0, "adjustment_direction" to adjustment,
                            "created_at" to now, "updated_at" to now)
                        tx("upgrade_income", "income", 200_000L)
                        tx("upgrade_expense", "expense", 50_000L, receipt = photo.absolutePath)
                        tx("upgrade_transfer", "transfer", 100_000L, destination = "upgrade_bank")
                        tx("upgrade_adjustment", "adjustment", 20_000L, asset = "upgrade_bank", adjustment = -1)
                        tx("upgrade_loan", "loan_received", 300_000L, debt = "upgrade_debt")
                        tx("upgrade_payment", "debt_payment", 80_000L, debt = "upgrade_debt")
                        tx("upgrade_allocation", "allocation", 40_000L, goal = "upgrade_goal")
                        tx("upgrade_release", "release", 10_000L, goal = "upgrade_goal")
                        tx("upgrade_lent", "loan_given", 150_000L, debt = "upgrade_receivable")
                        tx("upgrade_repaid", "receivable_payment", 40_000L, debt = "upgrade_receivable")
                        insert("debts", "id" to "upgrade_debt", "title" to "Pinjaman Uji", "lender_name" to "Pemberi Uji", "direction" to "debt",
                            "total_amount" to 300_000L, "paid_amount" to 80_000L, "due_date" to "2050-12-31", "status" to "partial", "created_at" to now, "updated_at" to now)
                        insert("debts", "id" to "upgrade_receivable", "title" to "Piutang Uji", "lender_name" to "Peminjam Uji", "direction" to "receivable",
                            "total_amount" to 150_000L, "paid_amount" to 40_000L, "due_date" to "2050-12-31", "status" to "partial", "created_at" to now, "updated_at" to now)
                        insert("financial_goals", "id" to "upgrade_goal", "name" to "Target Uji", "target_amount" to 500_000L, "current_amount" to 30_000L,
                            "deadline" to "2050-12-31", "asset_id" to "upgrade_cash", "created_at" to now, "updated_at" to now)
                        insert("budgets", "id" to "upgrade_budget", "category_id" to "upgrade_custom", "period_type" to "monthly", "target_amount" to 300_000L, "created_at" to now)
                        insert("favorite_templates", "id" to "upgrade_favorite", "name" to "Makan Uji", "type" to "expense", "amount" to 25_000L,
                            "category_id" to "upgrade_custom", "asset_id" to "upgrade_cash", "created_at" to now)
                        insert("recurring_transactions", "id" to "upgrade_recurring", "name" to "Tagihan Uji", "type" to "expense", "amount" to 50_000L,
                            "category_id" to "upgrade_custom", "asset_id" to "upgrade_cash", "frequency" to "monthly", "interval" to 1, "start_date" to date,
                            "occurrences_done" to 1, "mode" to "reminder", "is_active" to 0, "last_executed_date" to date, "created_at" to now)
                        insert("recurring_executions", "id" to "upgrade_execution", "recurring_id" to "upgrade_recurring", "scheduled_date" to date,
                            "transaction_id" to "upgrade_expense", "executed_at" to now, "status" to "completed")
                        db.setTransactionSuccessful()
                    } finally { db.endTransaction() }
                }
                photo.parentFile!!.mkdirs()
                val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.GREEN)
                photo.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
                bitmap.recycle()
                check(prefs.edit().putString("user_name", "Penguji Pembaruan").putString("theme_mode", "dark").putBoolean("balance_hidden", true)
                    .putStringSet("favorite_category_ids", setOf("upgrade_custom", "cat_makanan")).putBoolean("daily_reminder_enabled", true)
                    .putString("daily_reminder_time", "23:59").putBoolean("initial_setup_completed", true).putBoolean("favorite_direct_save", false)
                    .putString("last_expense_cat", "upgrade_custom").putString("last_expense_asset", "upgrade_cash").putBoolean("budget_alerts_enabled", false).commit())
                expected.writeText(snapshot().toString())
            }
            "check" -> {
                check(expected.isFile) { "Missing old-version fixture; do not seed after upgrading." }
                val old = JSONObject(expected.readText())
                val current = snapshot()
                assertEquals("Every financial row must remain identical", old.getJSONObject("tables").toString(), current.getJSONObject("tables").toString())
                assertEquals("User preferences must survive", old.getJSONObject("preferences").toString(), current.getJSONObject("preferences").toString())
                assertEquals(old.getString("receipt_sha256"), current.getString("receipt_sha256"))
            }
            else -> error("Choose seed or check.")
        }
        instrumentation.addResults(Bundle().apply {
            putLong("installed_build", code)
            putString("fixture_sha256", digest(expected.readBytes()))
            putString("receipt_sha256", digest(photo.readBytes()))
            putString("verified_tables", "9")
        })
    }
}
