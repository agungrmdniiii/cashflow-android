package com.cashflow.app

import com.cashflow.app.data.model.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ReportComparisonDetailsTest {
    private fun tx(id: String, date: String, type: String = "expense", category: String? = null) =
        Transaction(id, type, 100, id, category, "cash", transactionDate = date)

    @Test fun comparisonUsesMatchingDatesAndExcludesFutureAndAdjustments() {
        val rows = listOf(tx("current", "2026-10-23"), tx("future", "2026-10-24"),
            tx("previous", "2026-09-23"), tx("after-cutoff", "2026-09-24"),
            tx("salary", "2026-10-01", "income"), tx("transfer", "2026-10-01", "transfer"),
            tx("adjustment", "2026-10-01").copy(isAdjustment = true))
        val result = compareMonthToDate(rows, LocalDate.of(2026, 10, 23))
        assertEquals(100L, result.current.expense)
        assertEquals(100L, result.previous.expense)
        assertEquals(100L, result.current.income)
        assertEquals(LocalDate.of(2026, 9, 23), result.previousRange.end)
    }

    @Test fun comparisonHandlesYearStartLeapYearAndShortMonths() {
        for ((today, expectedEnd) in listOf("2026-01-01" to "2025-12-01",
                "2024-03-31" to "2024-02-29", "2025-03-31" to "2025-02-28",
                "2024-02-29" to "2024-01-29")) {
            val result = compareMonthToDate(emptyList(), LocalDate.parse(today))
            assertEquals(LocalDate.parse(today), result.currentRange.end)
            assertEquals(LocalDate.parse(expectedEnd), result.previousRange.end)
            assertEquals(0L, result.previous.expense)
        }
    }

    @Test fun detailTotalsMatchSummaryIncludingMissingAndEmptyCategories() {
        val range = ReportDateRange(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3))
        val rows = filterTransactionsByPeriod(listOf(tx("null", "2026-10-02"),
            tx("blank", "2026-10-03", category = ""), tx("missing", "2026-10-03", category = "deleted"),
            tx("outside", "2026-10-01"), tx("income", "2026-10-02", "income"),
            tx("transfer", "2026-10-02", "transfer"), tx("adjustment", "2026-10-02").copy(isAdjustment = true)),
            PeriodFilter.SEMUA, LocalDate.of(2026, 10, 23), range)
        val summary = summarizeTransactions(rows)
        for ((category, total) in summary.expenseByCategory) {
            assertEquals(total, reportTransactionDetails(rows, "expense", category).sumOf { it.amount })
        }
        assertEquals(summary.expense, reportTransactionDetails(rows, "expense").sumOf { it.amount })
        assertEquals(summary.income, reportTransactionDetails(rows, "income").sumOf { it.amount })
        assertEquals(listOf("blank", "null"), reportTransactionDetails(rows, "expense", "").map { it.id })
    }
}
