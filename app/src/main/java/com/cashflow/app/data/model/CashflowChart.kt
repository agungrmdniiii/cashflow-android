package com.cashflow.app.data.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.DayOfWeek

data class CashflowBucket(val label: String, val income: Long = 0L, val expense: Long = 0L)

fun buildCashflowChart(transactions: List<Transaction>, period: PeriodFilter, today: LocalDate = LocalDate.now(), customRange: ReportDateRange? = null): List<CashflowBucket> {
    val dated = transactions.filter { it.type == "income" || it.type == "expense" }
        .mapNotNull { tx -> runCatching { tx to LocalDate.parse(tx.transactionDate) }.getOrNull() }
    val ranges: List<Triple<String, LocalDate, LocalDate>> = if (customRange != null) {
        val days = java.time.temporal.ChronoUnit.DAYS.between(customRange.start, customRange.end) + 1
        val step = ((days + 11) / 12).coerceAtLeast(1)
        val formatter = java.time.format.DateTimeFormatter.ofPattern("d/M")
        generateSequence(customRange.start) { it.plusDays(step) }.takeWhile { it <= customRange.end }.map { start ->
            val endExclusive = minOf(start.plusDays(step), customRange.end.plusDays(1))
            val last = endExclusive.minusDays(1)
            val label = when {
                start == last -> start.format(formatter)
                YearMonth.from(start) == YearMonth.from(last) -> "${start.dayOfMonth}–${last.format(formatter)}"
                else -> "${start.format(formatter)}–${last.format(formatter)}"
            }
            Triple(label, start, endExclusive)
        }.toList()
    } else when (period) {
        PeriodFilter.HARI_INI -> (0..5).map { Triple("%02d".format(it * 4), today, today.plusDays(1)) }
        PeriodFilter.MINGGU_INI -> {
            val start = today.with(DayOfWeek.MONDAY)
            listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min").mapIndexed { i, label -> Triple(label, start.plusDays(i.toLong()), start.plusDays(i + 1L)) }
        }
        PeriodFilter.BULAN_INI -> {
            val month = YearMonth.from(today)
            (1..month.lengthOfMonth() step 7).map { day ->
                val last = (day + 6).coerceAtMost(month.lengthOfMonth())
                Triple("$day–$last", month.atDay(day), month.atDay(last).plusDays(1))
            }
        }
        PeriodFilter.TAHUN_INI -> listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des").mapIndexed { i, label ->
            val start = today.withMonth(i + 1).withDayOfMonth(1)
            Triple(label, start, start.plusMonths(1))
        }
        PeriodFilter.SEMUA -> {
            val firstYear = dated.minOfOrNull { it.second.year } ?: today.year
            val lastYear = dated.maxOfOrNull { it.second.year } ?: today.year
            val step = ((lastYear - firstYear + 12) / 12).coerceAtLeast(1)
            (firstYear..lastYear step step).map { year ->
                val last = (year + step - 1).coerceAtMost(lastYear)
                Triple(if (year == last) "$year" else "$year–$last", LocalDate.of(year, 1, 1), LocalDate.of(last + 1, 1, 1))
            }
        }
    }
    val income = LongArray(ranges.size)
    val expense = LongArray(ranges.size)
    dated.forEach { (tx, date) ->
        val index = if (period == PeriodFilter.HARI_INI && customRange == null) {
            if (date != today) -1 else tx.transactionTime.substringBefore(':').toIntOrNull()?.takeIf { it in 0..23 }?.div(4) ?: -1
        } else ranges.indexOfFirst { date >= it.second && date < it.third }
        if (index >= 0) {
            if (tx.type == "income") income[index] += tx.amount else expense[index] += tx.amount
        }
    }
    return ranges.mapIndexed { index, range -> CashflowBucket(range.first, income[index], expense[index]) }
}
