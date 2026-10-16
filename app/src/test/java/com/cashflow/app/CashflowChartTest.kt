package com.cashflow.app

import com.cashflow.app.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CashflowChartTest {
    private val today = LocalDate.of(2026, 10, 16)
    private fun tx(id: String, date: String, type: String = "expense", amount: Long = 100L, time: String = "12:00") =
        Transaction(id, type, amount, id, assetId = "cash", transactionDate = date, transactionTime = time)

    @Test fun monthlyChartIncludesEarlierWeeksAndExcludesOtherMonths() {
        val buckets = buildCashflowChart(listOf(tx("early", "2026-10-02"), tx("now", "2026-10-16", "income", 200), tx("old", "2026-09-30")), PeriodFilter.BULAN_INI, today)
        assertEquals(listOf("1–7", "8–14", "15–21", "22–28", "29–31"), buckets.map { it.label })
        assertEquals(100L, buckets.sumOf { it.expense })
        assertEquals(200L, buckets.sumOf { it.income })
    }
    @Test fun weeklyChartStartsOnMondayRatherThanTheLastSevenDays() {
        val buckets = buildCashflowChart(listOf(tx("sun", "2026-10-11"), tx("mon", "2026-10-12")), PeriodFilter.MINGGU_INI, today)
        assertEquals(listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min"), buckets.map { it.label })
        assertEquals(100L, buckets.first().expense)
        assertEquals(100L, buckets.sumOf { it.expense })
    }
    @Test fun dailyChartGroupsHoursAndYearlyChartGroupsMonths() {
        val txs = listOf(tx("a", "2026-10-16", time = "03:59"), tx("b", "2026-10-16", time = "04:00"), tx("c", "2026-01-01"))
        val day = buildCashflowChart(txs, PeriodFilter.HARI_INI, today)
        assertEquals(6, day.size)
        assertEquals(100L, day[0].expense)
        assertEquals(100L, day[1].expense)
        val year = buildCashflowChart(txs, PeriodFilter.TAHUN_INI, today)
        assertEquals(12, year.size)
        assertEquals(300L, year.sumOf { it.expense })
    }
    @Test fun allTimeKeepsOldHistoryAndNeverIncludesAllocations() {
        val buckets = buildCashflowChart(listOf(tx("old", "2020-01-01"), tx("new", "2026-10-16", "income"), tx("goal", "2026-10-16", "allocation")), PeriodFilter.SEMUA, today)
        assertEquals(100L, buckets.sumOf { it.expense })
        assertEquals(100L, buckets.sumOf { it.income })
        assertTrue(buckets.size <= 12)
    }
}
