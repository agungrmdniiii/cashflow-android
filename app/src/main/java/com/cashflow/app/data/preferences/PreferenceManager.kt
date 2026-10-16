package com.cashflow.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.cashflow.app.data.model.BiometricTimeout
import com.cashflow.app.data.model.ThemeMode

class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val credentials = context.getSharedPreferences("duit_aing_credentials", Context.MODE_PRIVATE)

    private fun credentialKey(): javax.crypto.SecretKey {
        val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("duit_aing_api_key", null) as? javax.crypto.SecretKey)?.let { return it }
        return javax.crypto.KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(android.security.keystore.KeyGenParameterSpec.Builder("duit_aing_api_key",
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes("GCM").setEncryptionPaddings("NoPadding").build())
        }.generateKey()
    }

    fun getThemeMode(): ThemeMode {
        val value = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.value) ?: ThemeMode.SYSTEM.value
        return ThemeMode.fromValue(value)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.value).apply()
    }

    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun getBiometricTimeout(): BiometricTimeout {
        val value = prefs.getString(KEY_BIOMETRIC_TIMEOUT, BiometricTimeout.IMMEDIATE.value) ?: BiometricTimeout.IMMEDIATE.value
        return BiometricTimeout.fromValue(value)
    }

    fun setBiometricTimeout(timeout: BiometricTimeout) {
        prefs.edit().putString(KEY_BIOMETRIC_TIMEOUT, timeout.value).apply()
    }

    fun getLastBackupTime(): Long {
        return prefs.getLong(KEY_LAST_BACKUP_TIME, 0L)
    }

    fun getBudgetStartDay(): Int = prefs.getInt("budget_start_day", 1).coerceIn(1, 31)
    fun setBudgetStartDay(day: Int) { require(day in 1..31); prefs.edit().putInt("budget_start_day", day).apply() }
    fun getLastBackupFormat(): String = prefs.getString("backup_format", "Format lama tidak tercatat")!!
    fun setBackupSaved(format: String, time: Long) { prefs.edit().putLong(KEY_LAST_BACKUP_TIME, time).putString("backup_format", format).apply() }
    fun getLastRestoreTime(): Long = prefs.getLong("last_restore_time", 0L)
    fun setLastRestoreTime(time: Long) { prefs.edit().putLong("last_restore_time", time).apply() }

    fun setLastBackupTime(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_BACKUP_TIME, timestamp).apply()
    }

    fun isBackupReminderEnabled(): Boolean {
        return prefs.getBoolean(KEY_BACKUP_REMINDER_ENABLED, true)
    }

    fun setBackupReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BACKUP_REMINDER_ENABLED, enabled).apply()
    }

    fun isInitialSetupCompleted(): Boolean {
        return prefs.getBoolean(KEY_INITIAL_SETUP_COMPLETED, false)
    }

    fun setInitialSetupCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_INITIAL_SETUP_COMPLETED, completed).apply()
    }

    fun isAppTourPending(): Boolean = prefs.getBoolean("app_tour_pending", false)
    fun setAppTourPending(pending: Boolean) {
        prefs.edit().putBoolean("app_tour_pending", pending).apply()
    }

    fun isBalanceHidden(): Boolean {
        return prefs.getBoolean(KEY_BALANCE_HIDDEN, false)
    }

    fun setBalanceHidden(hidden: Boolean) {
        prefs.edit().putBoolean(KEY_BALANCE_HIDDEN, hidden).apply()
    }

    fun isFavoriteDirectSave(): Boolean {
        return prefs.getBoolean(KEY_FAVORITE_DIRECT_SAVE, true)
    }

    fun setFavoriteDirectSave(directSave: Boolean) {
        prefs.edit().putBoolean(KEY_FAVORITE_DIRECT_SAVE, directSave).apply()
    }

    // Last used category & asset per transaction type
    fun getLastExpenseCategoryId(): String? = prefs.getString(KEY_LAST_EXPENSE_CATEGORY, null)
    fun setLastExpenseCategoryId(id: String?) {
        prefs.edit().putString(KEY_LAST_EXPENSE_CATEGORY, id).apply()
    }

    fun getLastExpenseAssetId(): String? = prefs.getString(KEY_LAST_EXPENSE_ASSET, null)
    fun setLastExpenseAssetId(id: String?) {
        prefs.edit().putString(KEY_LAST_EXPENSE_ASSET, id).apply()
    }

    fun getLastIncomeCategoryId(): String? = prefs.getString(KEY_LAST_INCOME_CATEGORY, null)
    fun setLastIncomeCategoryId(id: String?) {
        prefs.edit().putString(KEY_LAST_INCOME_CATEGORY, id).apply()
    }

    fun getLastIncomeAssetId(): String? = prefs.getString(KEY_LAST_INCOME_ASSET, null)
    fun setLastIncomeAssetId(id: String?) {
        prefs.edit().putString(KEY_LAST_INCOME_ASSET, id).apply()
    }

    fun getLastTransferSourceAssetId(): String? = prefs.getString(KEY_LAST_TRANSFER_SOURCE, null)
    fun setLastTransferSourceAssetId(id: String?) {
        prefs.edit().putString(KEY_LAST_TRANSFER_SOURCE, id).apply()
    }

    fun getLastTransferTargetAssetId(): String? = prefs.getString(KEY_LAST_TRANSFER_TARGET, null)
    fun setLastTransferTargetAssetId(id: String?) {
        prefs.edit().putString(KEY_LAST_TRANSFER_TARGET, id).apply()
    }

    fun getLastUsedCategory(type: String): String? {
        return when (type) {
            "income" -> getLastIncomeCategoryId()
            else -> getLastExpenseCategoryId()
        }
    }

    fun setLastUsedCategory(type: String, categoryId: String?) {
        when (type) {
            "income" -> setLastIncomeCategoryId(categoryId)
            else -> setLastExpenseCategoryId(categoryId)
        }
    }

    fun getLastUsedAsset(type: String): String? {
        return when (type) {
            "income" -> getLastIncomeAssetId()
            "transfer" -> getLastTransferSourceAssetId()
            else -> getLastExpenseAssetId()
        }
    }

    fun setLastUsedAsset(type: String, assetId: String?) {
        when (type) {
            "income" -> setLastIncomeAssetId(assetId)
            "transfer" -> setLastTransferSourceAssetId(assetId)
            else -> setLastExpenseAssetId(assetId)
        }
    }

    fun getLastUsedDestinationAsset(): String? = getLastTransferTargetAssetId()
    fun setLastUsedDestinationAsset(assetId: String?) = setLastTransferTargetAssetId(assetId)

    // Notifikasi & Anggaran
    fun isBudgetAlertsEnabled(): Boolean = prefs.getBoolean(KEY_BUDGET_ALERTS_ENABLED, true)
    fun setBudgetAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BUDGET_ALERTS_ENABLED, enabled).apply()
    }

    fun isDailyReminderEnabled(): Boolean = prefs.getBoolean(KEY_DAILY_REMINDER_ENABLED, false)
    fun setDailyReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DAILY_REMINDER_ENABLED, enabled).apply()
    }

    fun getDailyReminderTime(): String = prefs.getString(KEY_DAILY_REMINDER_TIME, "20:00") ?: "20:00"
    fun setDailyReminderTime(time: String) {
        prefs.edit().putString(KEY_DAILY_REMINDER_TIME, time).apply()
    }

    fun isBudgetRolloverEnabled(): Boolean = prefs.getBoolean(KEY_BUDGET_ROLLOVER_ENABLED, false)
    fun setBudgetRolloverEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BUDGET_ROLLOVER_ENABLED, enabled).apply()
    }

    // AI & Cloud Backup
    fun getGeminiApiKey(): String? {
        prefs.getString(KEY_GEMINI_API_KEY, null)?.let { old ->
            return runCatching { setGeminiApiKey(old); old }.getOrNull()
        }
        val encoded = credentials.getString("api_key", null) ?: return null
        return runCatching {
            val bytes = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, credentialKey(), javax.crypto.spec.GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
        }.getOrNull()
    }
    fun setGeminiApiKey(key: String?) {
        if (key.isNullOrBlank()) {
            check(credentials.edit().remove("api_key").commit())
        } else {
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, credentialKey())
            val encoded = android.util.Base64.encodeToString(cipher.iv + cipher.doFinal(key.toByteArray(Charsets.UTF_8)), android.util.Base64.NO_WRAP)
            check(credentials.edit().putString("api_key", encoded).commit())
        }
        check(prefs.edit().remove(KEY_GEMINI_API_KEY).commit())
    }

    fun getCloudBackupEmail(): String? = prefs.getString(KEY_CLOUD_BACKUP_EMAIL, null)
    fun setCloudBackupEmail(email: String?) {
        prefs.edit().putString(KEY_CLOUD_BACKUP_EMAIL, email).apply()
    }

    // Generic key-value helpers for internal state / flags
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean = prefs.getBoolean(key, defaultValue)
    fun setBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    // Security: Screenshot & Recents obscuring
    fun isScreenshotBlocked(): Boolean = prefs.getBoolean(KEY_SCREENSHOT_BLOCKED, false)
    fun setScreenshotBlocked(blocked: Boolean) {
        prefs.edit().putBoolean(KEY_SCREENSHOT_BLOCKED, blocked).apply()
    }

    // Online Privacy Hard Toggles (Kill Switches)
    fun isOnlineVoiceEnabled(): Boolean = prefs.getBoolean(KEY_ONLINE_VOICE_ENABLED, false)
    fun setOnlineVoiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ONLINE_VOICE_ENABLED, enabled).apply()
    }

    fun isOnlineOcrEnabled(): Boolean = prefs.getBoolean(KEY_ONLINE_OCR_ENABLED, true)
    fun setOnlineOcrEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ONLINE_OCR_ENABLED, enabled).apply()
    }

    fun isAiInsightsEnabled(): Boolean = prefs.getBoolean(KEY_AI_INSIGHTS_ENABLED, false)
    fun setAiInsightsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AI_INSIGHTS_ENABLED, enabled).apply()
    }

    fun isCloudBackupEnabled(): Boolean = prefs.getBoolean(KEY_CLOUD_BACKUP_ENABLED, true)
    fun setCloudBackupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CLOUD_BACKUP_ENABLED, enabled).apply()
    }

    // User Profile / Nickname
    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "") ?: ""
    fun setUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name.trim()).apply()
    }

    companion object {
        private const val PREFS_NAME = "duit_aing_preferences"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_BIOMETRIC_TIMEOUT = "biometric_timeout"
        private const val KEY_SCREENSHOT_BLOCKED = "screenshot_blocked"
        private const val KEY_LAST_BACKUP_TIME = "last_backup_time"
        private const val KEY_BACKUP_REMINDER_ENABLED = "backup_reminder_enabled"
        private const val KEY_INITIAL_SETUP_COMPLETED = "initial_setup_completed"
        private const val KEY_BALANCE_HIDDEN = "balance_hidden"
        private const val KEY_FAVORITE_DIRECT_SAVE = "favorite_direct_save"
        private const val KEY_LAST_EXPENSE_CATEGORY = "last_expense_cat"
        private const val KEY_LAST_EXPENSE_ASSET = "last_expense_asset"
        private const val KEY_LAST_INCOME_CATEGORY = "last_income_cat"
        private const val KEY_LAST_INCOME_ASSET = "last_income_asset"
        private const val KEY_LAST_TRANSFER_SOURCE = "last_transfer_src"
        private const val KEY_LAST_TRANSFER_TARGET = "last_transfer_tgt"
        private const val KEY_BUDGET_ALERTS_ENABLED = "budget_alerts_enabled"
        private const val KEY_DAILY_REMINDER_ENABLED = "daily_reminder_enabled"
        private const val KEY_DAILY_REMINDER_TIME = "daily_reminder_time"
        private const val KEY_BUDGET_ROLLOVER_ENABLED = "budget_rollover_enabled"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_CLOUD_BACKUP_EMAIL = "cloud_backup_email"
        private const val KEY_ONLINE_VOICE_ENABLED = "online_voice_enabled"
        private const val KEY_ONLINE_OCR_ENABLED = "online_ocr_enabled"
        private const val KEY_AI_INSIGHTS_ENABLED = "ai_insights_enabled"
        private const val KEY_CLOUD_BACKUP_ENABLED = "cloud_backup_enabled"
    }
}
