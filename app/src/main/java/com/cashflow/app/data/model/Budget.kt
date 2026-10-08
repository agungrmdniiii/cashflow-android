package com.cashflow.app.data.model

data class Budget(
    val id: String,
    val categoryId: String,
    val periodType: String = "monthly", // "weekly", "monthly"
    val targetAmount: Long,
    val createdAt: Long = System.currentTimeMillis()
)

data class BudgetWithProgress(
    val budget: Budget,
    val category: Category?,
    val spentAmount: Long,
    val remainingAmount: Long,
    val progressPercentage: Float,
    val status: BudgetStatus
)
