package com.cashflow.app.data.model

data class Category(
    val id: String,
    val name: String,
    val type: String, // "income" or "expense"
    val icon: String = "category",
    val color: String = "#0F6B56",
    val isDefault: Boolean = false,
    val isActive: Boolean = true
)
