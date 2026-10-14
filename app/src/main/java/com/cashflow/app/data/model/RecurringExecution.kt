package com.cashflow.app.data.model

import java.util.UUID

data class RecurringExecution(
    val id: String = UUID.randomUUID().toString(),
    val recurringId: String,
    val scheduledDate: String, // YYYY-MM-DD
    val transactionId: String? = null, // ID of generated transaction if auto
    val executedAt: Long = System.currentTimeMillis(),
    val status: String = "completed" // completed, skipped, snoozed
)
