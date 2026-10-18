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

    @Test fun reportTotalsKeepUncategorizedMoneyButCategoryChartsOnlyUseCategorizedMoney() {
        val summary = summarizeTransactions(listOf(tx("a", "expense", 20, "2026-10-17"),
            tx("b", "expense", 30, "2026-10-17", null), tx("c", "income", 100, "2026-10-17", "salary"),
            tx("d", "transfer", 800, "2026-10-17"), tx("e", "allocation", 900, "2026-10-17")))
        assertEquals(50L, summary.expense)
        assertEquals(100L, summary.income)
        assertEquals(mapOf("food" to 20L), summary.expenseByCategory)
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
        assertSame(list, filterTransactionsByPeriod(list, PeriodFilter.SEMUA, today))
        assertEquals(listOf("b", "c"), filterTransactionsByPeriod(list, PeriodFilter.SEMUA, today,
            ReportDateRange(today, today.plusDays(1))).map { it.id })
    }
}
