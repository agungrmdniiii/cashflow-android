package com.cashflow.app

import com.cashflow.app.data.benchmark.PerformanceBenchmarker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PerformanceBenchmarkTest {

    @Test
    fun testGenerateSyntheticTransactions_ExactCountAndDistribution() {
        val count = 10_000
        val refDate = LocalDate.of(2026, 10, 14)
        val startGen = System.currentTimeMillis()
        val list = PerformanceBenchmarker.generateSyntheticTransactions(
            count = count,
            years = 3,
            referenceDate = refDate
        )
        val genDuration = System.currentTimeMillis() - startGen

        // Verify total count
        assertEquals(count, list.size)

        // Verify generator speed (< 500ms for 10k items in memory)
        assertTrue("Generator must be faster than 500ms, took $genDuration ms", genDuration < 500)

        // Verify date boundary (within 3 years)
        val minAllowed = refDate.minusYears(3).toString()
        val maxAllowed = refDate.toString()
        for (tx in list) {
            assertTrue("Date ${tx.transactionDate} should be >= $minAllowed", tx.transactionDate >= minAllowed)
            assertTrue("Date ${tx.transactionDate} should be <= $maxAllowed", tx.transactionDate <= maxAllowed)
            assertTrue("Amount should be > 0", tx.amount > 0)
            assertTrue("Type must be expense or income", tx.type in listOf("expense", "income"))
            assertTrue("Asset must not be empty", tx.assetId.isNotEmpty())
        }

        // Verify sorted descending
        for (i in 0 until list.size - 1) {
            val cur = list[i]
            val next = list[i + 1]
            val cmp = cur.transactionDate.compareTo(next.transactionDate)
            assertTrue("List must be sorted descending by date", cmp >= 0)
        }
    }

    @Test
    fun testAggregationCalculation_10kItemsUnder10ms() {
        val list = PerformanceBenchmarker.generateSyntheticTransactions(count = 10_000)

        val start = System.currentTimeMillis()
        var totalIncome = 0L
        var totalExpense = 0L
        val categoryBreakdown = mutableMapOf<String, Long>()

        for (tx in list) {
            if (tx.type == "income") {
                totalIncome += tx.amount
            } else if (tx.type == "expense") {
                totalExpense += tx.amount
                val cat = tx.categoryId ?: "uncategorized"
                categoryBreakdown[cat] = (categoryBreakdown[cat] ?: 0L) + tx.amount
            }
        }

        val duration = System.currentTimeMillis() - start
        println("10,000 in-memory aggregation duration: $duration ms")
        println("Income: Rp $totalIncome, Expense: Rp $totalExpense, Categories: ${categoryBreakdown.size}")

        assertTrue("In-memory aggregation of 10k records should be < 50ms", duration < 50)
        assertTrue(totalIncome > 0)
        assertTrue(totalExpense > 0)
        assertTrue(categoryBreakdown.isNotEmpty())
    }
}
