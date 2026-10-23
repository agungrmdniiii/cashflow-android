package com.cashflow.app.data.model

import java.util.UUID

enum class ChatSender {
    USER,
    ASSISTANT
}

data class ChatTransactionEvidence(
    val label: String,
    val range: ReportDateRange,
    val transactions: List<Transaction>,
    val isComparison: Boolean = false,
    val isCashflow: Boolean = false
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: ChatSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val transactionEvidence: List<ChatTransactionEvidence> = emptyList()
)
