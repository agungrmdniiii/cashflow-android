package com.cashflow.app.data.model

fun quickCategories(categories: List<Category>, selectedId: String): List<Category> {
    if (categories.size <= 8) return categories
    val visible = categories.take(7)
    val selected = categories.firstOrNull { it.id == selectedId }
    return if (selected == null || selected in visible) visible else visible.take(6) + selected
}

// Saved transactions are the source of truth, so restored backups need no separate usage counters.
fun rankCategoriesByUsage(categories: List<Category>, transactions: List<Transaction>, favoriteIds: Set<String> = emptySet()): List<Category> {
    val active = categories.filter { it.isActive }
    val types = active.associate { it.id to it.type }
    val counts = mutableMapOf<String, Int>()
    val latest = mutableMapOf<String, String>()
    for (tx in transactions) {
        val id = tx.categoryId ?: continue
        if (tx.isAdjustment || (tx.type != "expense" && tx.type != "income") || types[id] != tx.type) continue
        counts[id] = (counts[id] ?: 0) + 1
        val date = tx.transactionDate + " " + tx.transactionTime
        if (date > (latest[id] ?: "")) latest[id] = date
    }
    return active.sortedWith(compareByDescending<Category> { it.id in favoriteIds }
        .thenByDescending { counts[it.id] ?: 0 }.thenByDescending { latest[it.id] ?: "" })
}
