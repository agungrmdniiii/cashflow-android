package com.cashflow.app

import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Same-signed read-only audit also works when the reviewed app has no debugger attached. */
class LedgerFingerprintTest {
    @Test fun financialTablesFingerprint() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val path = instrumentation.targetContext.getDatabasePath("cashflow.db").path
        val digest = MessageDigest.getInstance("SHA-256")
        fun feed(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
            digest.update(bytes)
        }
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("PRAGMA integrity_check", null).use { it.moveToFirst(); assertEquals("ok", it.getString(0)) }
            db.rawQuery("PRAGMA foreign_key_check", null).use { assertEquals(0, it.count) }
            for (table in listOf("assets", "transactions", "budgets", "financial_goals", "debts", "recurring_transactions", "recurring_executions", "favorite_templates")) {
                val columns = mutableListOf<String>()
                db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
                    while (cursor.moveToNext()) columns += cursor.getString(1)
                }
                val expressions = columns.joinToString(",") { "quote(\"${it.replace("\"", "\"\"")}\")" }
                db.rawQuery("SELECT $expressions FROM $table ORDER BY $expressions", null).use { cursor ->
                    feed(table); feed(columns.size.toString()); feed(cursor.count.toString())
                    while (cursor.moveToNext()) for (index in columns.indices) feed(cursor.getString(index))
                }
            }
        }
        val fingerprint = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        instrumentation.addResults(Bundle().apply { putString("ledger_sha256", fingerprint) })
    }
}
