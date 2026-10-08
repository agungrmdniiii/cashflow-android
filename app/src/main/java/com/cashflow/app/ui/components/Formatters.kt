package com.cashflow.app.ui.components

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatters {

    private val indonesianLocale = Locale.forLanguageTag("id-ID")
    private val numberFormat = NumberFormat.getNumberInstance(indonesianLocale)
    private val dateGroupFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", indonesianLocale)
    private val dateReadableFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", indonesianLocale)

    fun formatRupiah(amount: Long, includePrefix: Boolean = true): String {
        val formatted = synchronized(numberFormat) {
            numberFormat.format(amount)
        }
        return if (includePrefix) "Rp $formatted" else formatted
    }

    fun formatTransactionAmount(type: String, amount: Long): String {
        val formatted = formatRupiah(amount)
        return when (type) {
            "income" -> "+$formatted"
            "expense" -> "-$formatted"
            else -> formatted
        }
    }

    fun formatDateGroup(dateStr: String): String {
        return try {
            val date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
            val today = LocalDate.now()
            when (date) {
                today -> "Hari ini"
                today.minusDays(1) -> "Kemarin"
                else -> date.format(dateGroupFormatter)
            }
        } catch (e: Exception) {
            dateStr
        }
    }

    fun formatDateReadable(dateStr: String): String {
        return try {
            val date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
            date.format(dateReadableFormatter)
        } catch (e: Exception) {
            dateStr
        }
    }
}
