package com.cashflow.app

import androidx.test.platform.app.InstrumentationRegistry
import com.cashflow.app.data.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** Reconciles the installed ledger without creating, editing, or deleting any records. */
class ReportAccuracyTest {
    @Test fun installedLedgerTotalsMatchEveryReportAndCustomRange() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CashflowApplication
        val repo = app.repository
        repo.awaitInitialLoad()
        assertNull(repo.initError.value)
        val original = repo.transactions.value.toList()
        val today = LocalDate.now()
        val ranges = PeriodFilter.entries.map { it to null } + listOf(
            PeriodFilter.BULAN_INI to ReportDateRange(today.minusYears(1), today),
            PeriodFilter.SEMUA to ReportDateRange(today, today))
        for ((period, custom) in ranges) {
            val start = custom?.start ?: when (period) {
                PeriodFilter.HARI_INI -> today
                PeriodFilter.MINGGU_INI -> today.minusDays(today.dayOfWeek.value - 1L)
                PeriodFilter.BULAN_INI -> today.withDayOfMonth(1)
                PeriodFilter.TAHUN_INI -> LocalDate.of(today.year, 1, 1)
                PeriodFilter.SEMUA -> LocalDate.MIN
            }
            val end = custom?.end ?: when (period) {
                PeriodFilter.HARI_INI -> today
                PeriodFilter.MINGGU_INI -> start.plusDays(6)
                PeriodFilter.BULAN_INI -> today.withDayOfMonth(today.lengthOfMonth())
                PeriodFilter.TAHUN_INI -> LocalDate.of(today.year, 12, 31)
                PeriodFilter.SEMUA -> LocalDate.MAX
            }
            val expected = original.filter { tx ->
                val date = runCatching { LocalDate.parse(tx.transactionDate) }.getOrNull()
                date != null && date >= start && date <= end && !tx.isAdjustment
            }
            val expectedIncome = expected.filter { it.type == "income" }.sumOf { it.amount }
            val expectedExpense = expected.filter { it.type == "expense" }.sumOf { it.amount }
            val filtered = filterTransactionsByPeriod(original, period, today, custom)
            val summary = summarizeTransactions(filtered)
            val chart = buildCashflowChart(filtered, period, today, custom)
            assertEquals(expectedIncome, summary.income)
            assertEquals(expectedExpense, summary.expense)
            assertEquals(expectedIncome, summary.incomeByCategory.values.sum())
            assertEquals(expectedExpense, summary.expenseByCategory.values.sum())
            assertEquals(expectedExpense, summary.expenseByAsset.values.sum())
            assertEquals(expectedIncome, chart.sumOf { it.income })
            assertEquals(expectedExpense, chart.sumOf { it.expense })
        }
        assertEquals("Report checks must preserve the ledger", original, repo.transactions.value)
    }
}
