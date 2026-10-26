package com.cashflow.app.data.backup

import android.content.Context
import android.net.Uri

/** Device-only settings: account selection, folder grants and keys never enter exported backups. */
class BackupSettingsStore(context: Context) {
    val preferences = context.getSharedPreferences("duitaing_backup_device", Context.MODE_PRIVATE)
    fun automation(): BackupAutomation {
        val values = preferences.all
        val start = (values["start"] as? Int ?: 2).coerceIn(0, 23)
        val end = (values["end"] as? Int ?: 6).coerceIn(0, 23).let { if (it == start) (it + 4) % 24 else it }
        return BackupAutomation(values["enabled"] as? Boolean ?: false,
            enumValue(values["frequency"] as? String, BackupFrequency.DAILY),
            enumValue(values["destination"] as? String, BackupDestination.DRIVE),
            enumValue(values["network"] as? String, BackupNetwork.WIFI), start, end,
            (values["month_day"] as? Int ?: java.time.LocalDate.now().dayOfMonth).coerceIn(1, 31))
    }
    fun save(settings: BackupAutomation) {
        preferences.edit().putBoolean("enabled", settings.enabled).putString("frequency", settings.frequency.name)
            .putString("destination", settings.destination.name).putString("network", settings.network.name)
            .putInt("start", settings.startHour).putInt("end", settings.endHour).putInt("month_day", settings.monthDay).apply()
    }
    var driveAccount: String?
        get() = preferences.getString("account", null)
        set(value) { preferences.edit().putString("account", value).apply() }
    var localFolder: Uri?
        get() = preferences.getString("folder", null)?.let(Uri::parse)
        set(value) { preferences.edit().putString("folder", value?.toString()).apply() }
    fun success(destination: BackupDestination): Long = preferences.getLong("success_${destination.name}", 0)
    fun automaticSuccess(destination: BackupDestination): Long = preferences.getLong("automatic_success_${destination.name}", 0)
    fun error(destination: BackupDestination): String? = preferences.getString("error_${destination.name}", null)
    fun recordSuccess(destination: BackupDestination, automatic: Boolean, time: Long = System.currentTimeMillis()) {
        preferences.edit().putLong("success_${destination.name}", time).remove("error_${destination.name}")
            .apply { if (automatic) putLong("automatic_success_${destination.name}", time) }.apply()
    }
    fun recordError(destination: BackupDestination, message: String) {
        preferences.edit().putString("error_${destination.name}", message).apply()
    }
    private inline fun <reified T : Enum<T>> enumValue(value: String?, fallback: T): T =
        enumValues<T>().find { it.name == value } ?: fallback
}
