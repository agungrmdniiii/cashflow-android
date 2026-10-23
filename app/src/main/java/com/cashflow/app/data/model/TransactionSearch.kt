package com.cashflow.app.data.model

import java.time.LocalDate

private val rupiahPrefix = Regex("(?i)\\brp\\.?\\s*(?=\\d)")
private val groupedAmount = Regex("(?<![\\p{L}\\d])\\d{1,3}(?:[.,]\\d{3})+(?!\\d)")
private val searchWhitespace = Regex("\\s+")

fun transactionSearchTerms(query: String): Map<String, Long?> {
    val normalized = rupiahPrefix.replace(query, "").trim()
    return if (normalized.isEmpty()) emptyMap() else normalized.split(searchWhitespace).associateWith { word ->
        // Preserve literal text while parsing formatted amounts once per query, not once per row.
        (if (groupedAmount.matches(word)) word.filter { it in '0'..'9' } else word).toLongOrNull()
    }
}

fun transactionMatchesSearch(
    transaction: Transaction,
    terms: Map<String, Long?>,
    categoryName: String,
    assetName: String,
    destinationAssetName: String
): Boolean = terms.all { (word, amount) ->
    transaction.description.contains(word, ignoreCase = true) ||
        transaction.note.contains(word, ignoreCase = true) ||
        transaction.tags.contains(word, ignoreCase = true) ||
        categoryName.contains(word, ignoreCase = true) ||
        assetName.contains(word, ignoreCase = true) ||
        destinationAssetName.contains(word, ignoreCase = true) ||
        amount == transaction.amount
}

fun transactionFilterError(
    minimum: String, maximum: String, customDate: Boolean,
    start: LocalDate?, end: LocalDate?
): String? {
    if (customDate) {
        if (start == null || end == null) return "Pilih tanggal awal dan akhir."
        if (end.isBefore(start)) return "Tanggal akhir harus sama atau setelah tanggal awal."
    }
    if (minimum.isNotEmpty() && (minimum.any { it !in '0'..'9' } || minimum.toLongOrNull() == null))
        return "Nominal minimum tidak valid atau terlalu besar."
    if (maximum.isNotEmpty() && (maximum.any { it !in '0'..'9' } || maximum.toLongOrNull() == null))
        return "Nominal maksimum tidak valid atau terlalu besar."
    val min = minimum.toLongOrNull()
    val max = maximum.toLongOrNull()
    return if (min != null && max != null && min > max) "Nominal maksimum harus sama atau lebih besar dari minimum." else null
}
