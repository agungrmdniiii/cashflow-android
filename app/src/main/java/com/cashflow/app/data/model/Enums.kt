package com.cashflow.app.data.model

enum class TransactionType(val value: String, val displayName: String) {
    INCOME("income", "Pemasukan"),
    EXPENSE("expense", "Pengeluaran"),
    TRANSFER("transfer", "Transfer");

    companion object {
        fun fromValue(value: String): TransactionType {
            return entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: EXPENSE
        }
    }
}

enum class AssetType(val value: String, val displayName: String) {
    TUNAI("Tunai", "Tunai"),
    BANK("Bank", "Bank"),
    EWALLET("E-wallet", "E-Wallet"),
    TABUNGAN("Tabungan", "Tabungan"),
    KARTU_KREDIT("Kartu kredit", "Kartu Kredit"),
    LAINNYA("Lainnya", "Lainnya");

    companion object {
        fun fromValue(value: String): AssetType {
            return entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: BANK
        }
    }
}

enum class InputMethod(val value: String, val displayName: String) {
    MANUAL("manual", "Manual"),
    VOICE("voice", "Suara");

    companion object {
        fun fromValue(value: String): InputMethod {
            return entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: MANUAL
        }
    }
}

enum class PeriodFilter(val displayName: String) {
    HARI_INI("Hari ini"),
    MINGGU_INI("Minggu ini"),
    BULAN_INI("Bulan ini"),
    TAHUN_INI("Tahun ini"),
    SEMUA("Semua")
}

enum class BudgetStatus(val displayName: String) {
    SAFE("Aman"),
    WARNING("Perhatian"),
    NEAR_LIMIT("Hampir Habis"),
    EXCEEDED("Melebihi")
}

enum class ThemeMode(val value: String, val displayName: String) {
    SYSTEM("system", "Sistem"),
    LIGHT("light", "Terang"),
    DARK("dark", "Gelap");

    companion object {
        fun fromValue(value: String): ThemeMode {
            return entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: SYSTEM
        }
    }
}
