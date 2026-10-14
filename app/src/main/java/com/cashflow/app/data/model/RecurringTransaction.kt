package com.cashflow.app.data.model

import java.util.UUID

enum class RecurringFrequency(val value: String, val label: String) {
    DAILY("daily", "Harian"),
    WEEKLY("weekly", "Mingguan"),
    MONTHLY("monthly", "Bulanan"),
    YEARLY("yearly", "Tahunan");

    companion object {
        fun fromValue(value: String): RecurringFrequency {
            return entries.find { it.value.equals(value, ignoreCase = true) } ?: MONTHLY
        }
    }
}

enum class RecurringMode(val value: String, val label: String) {
    AUTO("auto", "Otomatis Catat"),
    REMIND("remind", "Ingatkan Dulu");

    companion object {
        fun fromValue(value: String): RecurringMode {
            return entries.find { it.value.equals(value, ignoreCase = true) } ?: AUTO
        }
    }
}

data class RecurringTransaction(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: String, // expense, income, transfer
    val amount: Long,
    val categoryId: String? = null,
    val assetId: String,
    val destinationAssetId: String? = null,
    val frequency: String = RecurringFrequency.MONTHLY.value,
    val interval: Int = 1, // e.g. every 1 month
    val startDate: String, // YYYY-MM-DD
    val endDate: String? = null, // YYYY-MM-DD (optional)
    val totalOccurrences: Int? = null, // max execution count (optional)
    val occurrencesDone: Int = 0,
    val mode: String = RecurringMode.AUTO.value,
    val linkedDebtId: String? = null,
    val lastExecutedDate: String? = null,
    val isActive: Boolean = true,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val frequencyDisplayName: String get() = RecurringFrequency.fromValue(frequency).label
}
