package com.cashflow.app.data.model

fun quickCategories(categories: List<Category>, selectedId: String): List<Category> {
    if (categories.size <= 8) return categories
    val visible = categories.take(7)
    val selected = categories.firstOrNull { it.id == selectedId }
    return if (selected == null || selected in visible) visible else visible.take(6) + selected
}
