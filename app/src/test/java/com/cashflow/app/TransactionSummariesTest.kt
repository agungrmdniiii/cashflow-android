package com.cashflow.app

import com.cashflow.app.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TransactionSummariesTest {
    private fun tx(id: String, type: String, amount: Long, date: String, category: String? = "food") =
        Transaction(id, type, amount, id, category, "bank", transactionDate = date)

    @Test fun dayGroupsPreserveOrderAndExcludeInternalMovementsFromTotals() {
        val list = listOf(tx("a", "expense", 20, "2026-10-17"), tx("b", "transfer", 80, "2026-10-17"),
            tx("c", "income", 100, "2026-10-16"), tx("d", "adjustment", 500, "2026-10-17"))
        val groups = groupTransactionDays(list)
        assertEquals(listOf("2026-10-17", "2026-10-16"), groups.map { it.date })
        assertEquals(listOf("a", "b", "d"), groups[0].transactions.map { it.id })
        assertEquals(20L, groups[0].expense)
        assertEquals(0L, groups[0].income)
        assertEquals(100L, groups[1].income)
        assertTrue(groupTransactionDays(emptyList()).isEmpty())
    }

    @Test fun reportCategoryBreakdownAccountsForEveryRupiah() {
        val summary = summarizeTransactions(listOf(tx("a", "expense", 20, "2026-10-17"),
            tx("b", "expense", 30, "2026-10-17", null), tx("c", "income", 100, "2026-10-17", "salary"),
            tx("d", "transfer", 800, "2026-10-17"), tx("e", "allocation", 900, "2026-10-17")))
        assertEquals(50L, summary.expense)
        assertEquals(100L, summary.income)
        assertEquals(30L, summary.expenseByCategory[""])
        assertEquals(summary.expense, summary.expenseByCategory.values.sum())
        assertEquals(mapOf("salary" to 100L), summary.incomeByCategory)
        assertEquals(mapOf("bank" to 50L), summary.expenseByAsset)
        assertEquals(0L, summarizeTransactions(emptyList()).expense)
    }

    @Test fun periodFilterHandlesLeapDaysInvalidDatesAndInclusiveCustomBounds() {
        val list = listOf(tx("bad", "expense", 1, "2024-02-30"), tx("a", "expense", 2, "2024-02-28"),
            tx("b", "transfer", 3, "2024-02-29"), tx("c", "income", 4, "2024-03-01"))
        val today = LocalDate.of(2024, 2, 29)
        assertEquals(listOf("a", "b"), filterTransactionsByPeriod(list, PeriodFilter.BULAN_INI, today).map { it.id })
        assertEquals(listOf("b"), filterTransactionsByPeriod(list, PeriodFilter.HARI_INI, today).map { it.id })
        assertEquals(listOf("a", "b", "c"), filterTransactionsByPeriod(list, PeriodFilter.MINGGU_INI, today).map { it.id })
        assertEquals(listOf("a", "b", "c"), filterTransactionsByPeriod(list, PeriodFilter.TAHUN_INI, today).map { it.id })
        assertEquals(listOf("a", "b", "c"), filterTransactionsByPeriod(list, PeriodFilter.SEMUA, today).map { it.id })
        assertEquals(listOf("b", "c"), filterTransactionsByPeriod(list, PeriodFilter.SEMUA, today,
            ReportDateRange(today, today.plusDays(1))).map { it.id })
    }

    @Test fun legacyAdjustmentsNeverInflateReportOrDailyTotals() {
        val list = listOf(tx("real", "expense", 20, "2026-10-17"),
            tx("legacy", "expense", 900, "2026-10-17").copy(isAdjustment = true),
            tx("legacy-income", "income", 800, "2026-10-17").copy(isAdjustment = true))
        val summary = summarizeTransactions(list)
        assertEquals(20L, summary.expense)
        assertEquals(0L, summary.income)
        assertEquals(20L, groupTransactionDays(list).single().expense)
        assertEquals(0L, groupTransactionDays(list).single().income)
        val chart = buildCashflowChart(list, PeriodFilter.BULAN_INI, LocalDate.of(2026, 10, 17))
        assertEquals(20L, chart.sumOf { it.expense })
        assertEquals(0L, chart.sumOf { it.income })
    }

    @Test fun reportTotalsAndChartsAgreeAcrossCalendarBoundaries() {
        val today = LocalDate.of(2024, 2, 29)
        val list = listOf(
            tx("last-year", "expense", 10, "2023-12-31"),
            tx("january", "expense", 20, "2024-01-31"),
            tx("february", "expense", 30, "2024-02-01"),
            tx("monday", "expense", 40, "2024-02-26"),
            tx("leap", "expense", 50, "2024-02-29", null),
            tx("march", "expense", 60, "2024-03-01"),
            tx("invalid", "expense", 900, "2024-02-30"),
            tx("salary", "income", 5_000_000_000L, "2024-02-29", null),
            tx("transfer", "transfer", 800, "2024-02-29"),
            tx("adjustment", "income", 700, "2024-02-29").copy(isAdjustment = true))
        val cases = listOf(PeriodFilter.HARI_INI to 50L, PeriodFilter.MINGGU_INI to 150L,
            PeriodFilter.BULAN_INI to 120L, PeriodFilter.TAHUN_INI to 200L, PeriodFilter.SEMUA to 210L)
        for ((period, expectedExpense) in cases) {
            val filtered = filterTransactionsByPeriod(list, period, today)
            val summary = summarizeTransactions(filtered)
            val chart = buildCashflowChart(filtered, period, today)
            assertEquals(period.name, expectedExpense, summary.expense)
            assertEquals(period.name, expectedExpense, chart.sumOf { it.expense })
            assertEquals(period.name, expectedExpense, summary.expenseByCategory.values.sum())
            assertEquals(period.name, 5_000_000_000L, summary.income)
            assertEquals(period.name, 5_000_000_000L, chart.sumOf { it.income })
            assertEquals(period.name, 5_000_000_000L, summary.incomeByCategory.values.sum())
        }
        val range = ReportDateRange(LocalDate.of(2023, 12, 31), LocalDate.of(2024, 2, 1))
        val filtered = filterTransactionsByPeriod(list, PeriodFilter.BULAN_INI, today, range)
        assertEquals(60L, summarizeTransactions(filtered).expense)
        assertEquals(60L, buildCashflowChart(filtered, PeriodFilter.BULAN_INI, today, range).sumOf { it.expense })
    }
}
