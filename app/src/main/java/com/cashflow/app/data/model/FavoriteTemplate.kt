package com.cashflow.app.data.model

import java.util.UUID

/**
 * Template transaksi favorit untuk mencatat transaksi rutin dengan cepat (<= 2 tap).
 */
data class FavoriteTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: String = TransactionType.EXPENSE.value,
    val amount: Long,
    val categoryId: String? = null,
    val assetId: String,
    val destinationAssetId: String? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
