package com.cashflow.app.data.model

data class FinancialGoal(
    val id: String,
    val name: String,
    val targetAmount: Long,
    val currentAmount: Long,
    val deadline: String, // "YYYY-MM-DD"
    val assetId: String? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val progressPercentage: Float
        get() = if (targetAmount > 0) {
            (currentAmount.toFloat() / targetAmount.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val remainingAmount: Long
        get() = (targetAmount - currentAmount).coerceAtLeast(0L)
}
