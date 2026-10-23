package com.cashflow.app.data.model

import java.time.DayOfWeek
import java.time.LocalDate

data class TransactionDay(
    val date: String,
    val transactions: List<Transaction>,
    val income: Long,
    val expense: Long
)

fun groupTransactionDays(transactions: List<Transaction>): List<TransactionDay> =
    transactions.groupBy { it.transactionDate }.map { (date, list) ->
        var income = 0L
        var expense = 0L
        for (tx in list) {
            if (tx.isAdjustment) continue
            when (tx.type) {
                "income" -> income += tx.amount
                "expense" -> expense += tx.amount
            }
        }
        TransactionDay(date, list, income, expense)
    }

data class TransactionSummary(
    val income: Long = 0L,
    val expense: Long = 0L,
    val expenseByCategory: Map<String, Long> = emptyMap(),
    val incomeByCategory: Map<String, Long> = emptyMap(),
    val expenseByAsset: Map<String, Long> = emptyMap()
)

// One emission keeps the period label, totals, and graph on the same dataset.
data class ReportSnapshot(
    val period: PeriodFilter = PeriodFilter.BULAN_INI,
    val range: ReportDateRange? = null,
    val summary: TransactionSummary = TransactionSummary(),
    val chart: List<CashflowBucket> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val comparison: MonthToDateComparison? = null
)

data class MonthToDateComparison(
    val currentRange: ReportDateRange,
    val previousRange: ReportDateRange,
    val current: TransactionSummary,
    val previous: TransactionSummary
)

fun compareMonthToDate(transactions: List<Transaction>, today: LocalDate): MonthToDateComparison {
    val previousMonth = today.minusMonths(1)
    val current = ReportDateRange(today.withDayOfMonth(1), today)
    val previous = ReportDateRange(previousMonth.withDayOfMonth(1), previousMonth.withDayOfMonth(minOf(today.dayOfMonth, previousMonth.lengthOfMonth())))
    return MonthToDateComparison(current, previous,
        summarizeTransactions(filterTransactionsByPeriod(transactions, PeriodFilter.BULAN_INI, today, current)),
        summarizeTransactions(filterTransactionsByPeriod(transactions, PeriodFilter.BULAN_INI, today, previous)))
}

fun reportTransactionDetails(transactions: List<Transaction>, type: String, categoryId: String? = null): List<Transaction> =
    transactions.filter { !it.isAdjustment && it.type == type && (categoryId == null || it.categoryId.orEmpty() == categoryId) }
        .sortedWith(compareByDescending<Transaction> { it.transactionDate }.thenByDescending { it.transactionTime }.thenBy { it.id })

fun summarizeTransactions(transactions: List<Transaction>): TransactionSummary {
    var income = 0L
    var expense = 0L
    val expenseByCategory = mutableMapOf<String, Long>()
    val incomeByCategory = mutableMapOf<String, Long>()
    val expenseByAsset = mutableMapOf<String, Long>()
    for (tx in transactions) {
        if (tx.isAdjustment) continue
        when (tx.type) {
            "income" -> {
                income += tx.amount
                val category = tx.categoryId.orEmpty()
                incomeByCategory[category] = (incomeByCategory[category] ?: 0L) + tx.amount
            }
            "expense" -> {
                expense += tx.amount
                val category = tx.categoryId.orEmpty()
                expenseByCategory[category] = (expenseByCategory[category] ?: 0L) + tx.amount
                expenseByAsset[tx.assetId] = (expenseByAsset[tx.assetId] ?: 0L) + tx.amount
            }
        }
    }
    return TransactionSummary(income, expense, expenseByCategory, incomeByCategory, expenseByAsset)
}

fun filterTransactionsByPeriod(
    transactions: List<Transaction>,
    period: PeriodFilter,
    today: LocalDate = LocalDate.now(),
    range: ReportDateRange? = null
): List<Transaction> {
    val allTime = range == null && period == PeriodFilter.SEMUA
    val start = range?.start ?: when (period) {
        PeriodFilter.HARI_INI -> today
        PeriodFilter.MINGGU_INI -> today.with(DayOfWeek.MONDAY)
        PeriodFilter.BULAN_INI -> today.withDayOfMonth(1)
        PeriodFilter.TAHUN_INI -> today.withDayOfYear(1)
        PeriodFilter.SEMUA -> today
    }
    val end = range?.end ?: when (period) {
        PeriodFilter.HARI_INI -> today
        PeriodFilter.MINGGU_INI -> start.plusDays(6)
        PeriodFilter.BULAN_INI -> start.plusMonths(1).minusDays(1)
        PeriodFilter.TAHUN_INI -> start.plusYears(1).minusDays(1)
        PeriodFilter.SEMUA -> today
    }
    // Many rows share one date; validate/parse each distinct date only once per pass.
    val matches = mutableMapOf<String, Boolean>()
    return transactions.filter { tx ->
        matches.getOrPut(tx.transactionDate) {
            runCatching { LocalDate.parse(tx.transactionDate).let { allTime || (it >= start && it <= end) } }.getOrDefault(false)
        }
    }
}
