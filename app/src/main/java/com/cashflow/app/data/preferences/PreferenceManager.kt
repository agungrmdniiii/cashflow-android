package com.cashflow.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.cashflow.app.data.model.BiometricTimeout
import com.cashflow.app.data.model.ThemeMode

class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
    fun getGeminiApiKey(): String? = prefs.getString(KEY_GEMINI_API_KEY, null)
    fun setGeminiApiKey(key: String?) {
        prefs.edit().putString(KEY_GEMINI_API_KEY, key).apply()
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

    companion object {
        private const val PREFS_NAME = "duit_aing_preferences"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_BIOMETRIC_TIMEOUT = "biometric_timeout"
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
    }
}
