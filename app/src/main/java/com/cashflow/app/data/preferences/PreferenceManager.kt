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

    companion object {
        private const val PREFS_NAME = "duit_aing_preferences"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_BIOMETRIC_TIMEOUT = "biometric_timeout"
    }
}
