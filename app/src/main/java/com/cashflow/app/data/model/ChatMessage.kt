package com.cashflow.app.data.model

import java.util.UUID

enum class ChatSender {
    USER,
    ASSISTANT
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: ChatSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false
)
