package com.cashflow.app.data.model

data class Transaction(
    val id: String,
    val type: String, // "income", "expense", "transfer"
    val amount: Long,
    val description: String,
    val categoryId: String? = null,
    val assetId: String, // Source asset
    val destinationAssetId: String? = null, // Destination asset for transfer
    val transactionDate: String, // "YYYY-MM-DD"
    val transactionTime: String = "12:00", // "HH:mm"
    val note: String = "",
    val attachment: String? = null,
    val inputMethod: String = "manual", // "manual", "voice"
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
