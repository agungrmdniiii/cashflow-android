package com.cashflow.app.data.model

data class Asset(
    val id: String,
    val name: String,
    val type: String, // Tunai, Bank, E-wallet, Tabungan, Kartu kredit, Lainnya
    val openingBalance: Long,
    val currentBalance: Long,
    val currency: String = "IDR",
    val isDefault: Boolean = false,
    val isActive: Boolean = true,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
