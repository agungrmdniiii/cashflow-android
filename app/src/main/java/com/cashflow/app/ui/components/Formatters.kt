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

class ThousandSeparatorVisualTransformation : androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val raw = text.text
        if (raw.isEmpty()) {
            return androidx.compose.ui.text.input.TransformedText(text, androidx.compose.ui.text.input.OffsetMapping.Identity)
        }

        val formatted = StringBuilder()
        val origToTrans = IntArray(raw.length + 1)
        val transToOrig = ArrayList<Int>()

        val length = raw.length
        for (i in 0 until length) {
            origToTrans[i] = formatted.length
            val digitsRemaining = length - i
            if (i > 0 && digitsRemaining % 3 == 0) {
                formatted.append('.')
                transToOrig.add(i)
                origToTrans[i] = formatted.length
            }
            formatted.append(raw[i])
            transToOrig.add(i)
        }
        origToTrans[length] = formatted.length
        transToOrig.add(length)

        val offsetMapping = object : androidx.compose.ui.text.input.OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val clamped = offset.coerceIn(0, raw.length)
                return origToTrans[clamped]
            }

            override fun transformedToOriginal(offset: Int): Int {
                val clamped = offset.coerceIn(0, formatted.length)
                return if (clamped < transToOrig.size) transToOrig[clamped] else raw.length
            }
        }

        return androidx.compose.ui.text.input.TransformedText(androidx.compose.ui.text.AnnotatedString(formatted.toString()), offsetMapping)
    }
}
