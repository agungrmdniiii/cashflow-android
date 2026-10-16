package com.cashflow.app

import com.cashflow.app.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BudgetPeriodRegressionTest {
    @org.junit.Test fun paydayPeriodsClampShortMonthsWithoutDrifting() {
        val start = com.cashflow.app.data.model.monthlyBudgetStart(java.time.LocalDate.of(2026, 3, 15), 31)
        org.junit.Assert.assertEquals(java.time.LocalDate.of(2026, 2, 28), start)
        org.junit.Assert.assertEquals(java.time.LocalDate.of(2026, 3, 31), com.cashflow.app.data.model.nextMonthlyBudgetStart(start, 31))
    }
    private fun expense(id: String, date: String, amount: Long) = Transaction(
        id, "expense", amount, id, categoryId = "food", assetId = "cash", transactionDate = date)

    @Test
    fun weekCanCrossYearBoundaryAndEndsOnSunday() {
        val result = calculateBudgetProgress(listOf(Budget("w", "food", "weekly", 100L, 0L)),
            emptyList(), listOf(expense("old", "2026-12-27", 50L), expense("current", "2026-12-28", 20L),
                expense("future", "2027-01-04", 70L)), false, LocalDate.of(2027, 1, 1)).single()
        assertEquals(20L, result.spentAmount)
        assertEquals(3, result.daysRemaining)
    }

    @Test
    fun leapMonthRolloverDoesNotMutateBaseBudget() {
        val base = Budget("m", "food", "monthly", 100L, 0L)
        val result = calculateBudgetProgress(listOf(base), emptyList(),
            listOf(expense("previous", "2024-01-31", 40L), expense("current", "2024-02-29", 20L)),
            true, LocalDate.of(2024, 2, 29)).single()
        assertEquals(base, result.budget)
        assertEquals(60L, result.rolloverAmount)
        assertEquals(160L, result.effectiveTargetAmount)
        assertEquals(140L, result.remainingAmount)
        assertEquals(1, result.daysRemaining)
    }

    @Test
    fun overspendingRemainsVisibleAsDeficit() {
        val result = calculateBudgetProgress(listOf(Budget("m", "food", "monthly", 100L, 0L)),
            emptyList(), listOf(expense("over", "2026-10-16", 150L)), false,
            LocalDate.of(2026, 10, 16)).single()
        assertEquals(-50L, result.remainingAmount)
        assertEquals(BudgetStatus.EXCEEDED, result.status)
    }
}
