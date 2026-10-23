package com.cashflow.app

import com.cashflow.app.data.backup.BackupManager
import com.cashflow.app.data.backup.BackupValidationResult
import com.cashflow.app.data.preferences.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class BackupSettingsTest {
    private fun backup(settings: Any? = null, schema: Int = BackupManager.CURRENT_SCHEMA_VERSION): JSONObject {
        val data = JSONObject().put("assets", JSONArray()).put("categories", JSONArray()).put("transactions", JSONArray())
        if (settings != null) data.put("app_settings", settings)
        val checksum = MessageDigest.getInstance("SHA-256").digest(data.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return JSONObject().put("metadata", JSONObject().put("schema_version", schema).put("checksum", checksum)).put("data", data)
    }

    @Test fun validatesSettingTypesAndBoundsBeforeRestore() {
        val invalid = listOf(
            "theme_mode" to "unknown", "user_name" to 123,
            "budget_start_day" to 0, "budget_start_day" to 32, "budget_start_day" to 1.5,
            "daily_reminder_time" to "24:00", "daily_reminder_time" to "12:60", "daily_reminder_time" to "8:30",
            "favorite_category_ids" to "category", "favorite_category_ids" to JSONArray().put(123),
            "favorite_category_ids" to JSONArray().put(" "), "balance_hidden" to "true", "daily_reminder_enabled" to 1
        )
        for ((key, value) in invalid) {
            assertTrue("Invalid setting: $key=$value", BackupManager.validateBackupJson(backup(JSONObject().put(key, value)).toString()) is BackupValidationResult.Invalid)
        }
        assertTrue(BackupManager.validateBackupJson(backup(JSONArray()).toString()) is BackupValidationResult.Invalid)
        assertTrue(BackupManager.validateBackupJson(backup(JSONObject.NULL).toString()) is BackupValidationResult.Invalid)
    }

    @Test fun acceptsLegacyAndPartialSettingsWithoutInventingMissingValues() {
        assertTrue(BackupManager.validateBackupJson(backup(schema = 10).toString()) is BackupValidationResult.Valid)
        val settings = JSONObject().put("budget_start_day", 31).put("daily_reminder_time", "23:59").put("favorite_category_ids", JSONArray())
        PreferenceManager.validateBackupSettings(settings)
        assertTrue(BackupManager.validateBackupJson(backup(settings).toString()) is BackupValidationResult.Valid)
        assertFalse(settings.has("theme_mode"))
    }

    @Test fun checksumProtectsSettingsAsWellAsFinancialRecords() {
        val json = backup(JSONObject().put("balance_hidden", true))
        assertTrue(BackupManager.validateBackupJson(json.toString()) is BackupValidationResult.Valid)
        json.getJSONObject("data").getJSONObject("app_settings").put("balance_hidden", false)
        val result = BackupManager.validateBackupJson(json.toString())
        assertTrue(result is BackupValidationResult.Invalid)
        assertTrue((result as BackupValidationResult.Invalid).reason.contains("Checksum"))
    }
}
