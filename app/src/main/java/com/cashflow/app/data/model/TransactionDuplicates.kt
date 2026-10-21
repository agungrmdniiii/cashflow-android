package com.cashflow.app.data.model

/** Descriptions, categories, and times can differ when a payment is scanned or imported again. */
internal fun Transaction.similarityKey(): List<Any?>? =
    if (isAdjustment || type !in listOf("income", "expense", "transfer")) null
    else listOf(type, amount, assetId, if (type == "transfer") destinationAssetId else null, transactionDate)

fun findSimilarTransactions(candidate: Transaction, existing: List<Transaction>): List<Transaction> {
    val key = candidate.similarityKey() ?: return emptyList()
    return existing.asSequence().filter { it.id != candidate.id && it.similarityKey() == key }.take(3).toList()
}
