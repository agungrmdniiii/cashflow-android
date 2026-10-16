package com.cashflow.app

import com.cashflow.app.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ReportDateRangeTest {
    @Test fun endpointsAreInclusiveAndInvalidDatesExcluded() {
        val range = ReportDateRange(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 11, 2))
        assertTrue(range.contains("2026-10-05"))
        assertTrue(range.contains("2026-11-02"))
        assertFalse(range.contains("2026-10-04"))
        assertFalse(range.contains("2026-11-03"))
        assertFalse(range.contains("bad-date"))
    }
    @Test fun invertedRangesAreRejected() {
        assertTrue(runCatching { ReportDateRange(LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 1)) }.isFailure)
    }
    @Test fun groupedCustomLabelsShowBothBoundaries() {
        val range = ReportDateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 16))
        val buckets = buildCashflowChart(emptyList(), PeriodFilter.BULAN_INI, customRange = range)
        assertEquals("1–2/10", buckets.first().label)
        assertEquals("15–16/10", buckets.last().label)
    }
    @Test fun customChartDoesNotFallBackToCurrentMonthOrLoseFinalDay() {
        val range = ReportDateRange(LocalDate.of(2025, 12, 28), LocalDate.of(2026, 1, 3))
        fun tx(id: String, date: String) = Transaction(id, "expense", 100L, id, assetId = "cash", transactionDate = date)
        val buckets = buildCashflowChart(listOf(tx("start", "2025-12-28"), tx("end", "2026-01-03"), tx("outside", "2026-01-04")),
            PeriodFilter.BULAN_INI, LocalDate.of(2026, 10, 17), range)
        assertEquals(200L, buckets.sumOf { it.expense })
        assertEquals(7, buckets.size)
        assertEquals(100L, buckets.last().expense)
    }
}
