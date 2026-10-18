package com.cashflow.app

import com.cashflow.app.data.model.*
import com.cashflow.app.data.service.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class FinancialInsightVarietyTest {
    private val today = LocalDate.of(2026, 10, 17)
    private fun stats(income: Long = 0, expense: Long = 0) = FinancialInsightService.computeMonthComparison(
        listOf(Transaction("in", "income", income, "Gaji", null, "cash", transactionDate = "2026-10-01"),
            Transaction("out", "expense", expense, "Makan", "food", "cash", transactionDate = "2026-10-16")),
        listOf(Category("food", "Makan", "expense")), today)

    @Test fun emptyLedgerIsNotCalledDeficitOrHealthy() {
        val narrative = FinancialInsightService.generateLocalNarrativeInsight(stats())
        assertFalse(narrative, narrative.contains("defisit", true))
        assertTrue(narrative, narrative.contains("Belum ada"))
        assertTrue(FinancialInsightService.generateLocalRuleInsights(stats()).isEmpty())
    }
    @Test fun deficitAndConcentrationAreDistinctFacts() {
        val insights = FinancialInsightService.generateLocalRuleInsights(stats(100_000, 200_000))
        assertTrue(insights.toString(), insights.any { it.title == "Pengeluaran melebihi pemasukan" && it.type == "warning" })
        assertTrue(insights.toString(), insights.any { it.title == "Pos pengeluaran terbesar" && it.message.contains("Makan") })
        assertFalse(insights.toString(), insights.any { it.message.contains("ideal") || it.message.contains("tabungan prima", true) })
    }
    @Test fun contextualInsightsRespectRolloverDebtDirectionDatesAndGoalProgress() {
        val budget = BudgetWithProgress(Budget("b", "food", targetAmount = 100_000), Category("food", "Makan", "expense"),
            180_000, 20_000, .9f, BudgetStatus.NEAR_LIMIT, rolloverAmount = 100_000)
        val debts = listOf(Debt("d", "Cicilan", "Doni", 90_000, dueDate = "2026-10-20"),
            Debt("r", "Piutang", "Sari", 900_000, dueDate = "2026-10-10", direction = "receivable"),
            Debt("paid", "Lunas", "Budi", 70_000, dueDate = "2026-10-10", status = "paid"),
            Debt("bad", "Rusak", "X", 80_000, dueDate = "invalid"))
        val insights = FinancialInsightService.generateLocalRuleInsights(stats(500_000, 200_000), listOf(budget), debts,
            listOf(FinancialGoal("g", "Laptop", 1_000_000, 900_000, "2026-12-01")), today)
        val text = insights.joinToString { it.message }
        assertTrue(text, text.contains("20.000") && text.contains("Doni") && text.contains("Laptop"))
        assertFalse(text, text.contains("Sari") || text.contains("Budi") || text.contains("Rusak"))
        assertFalse(insights.toString(), insights.any { it.title == "Anggaran terlampaui" })
        assertEquals("warning", insights.first().type)
    }

    @Test fun monthlyWrapperUsesAllCalendarDaysForACompletedMonth() {
        val past = java.time.YearMonth.now().minusMonths(1)
        val tx = Transaction("past", "expense", 300_000, "Makan", "food", "cash", transactionDate = past.atDay(1).toString())
        val stats = FinancialInsightService.calculateMonthComparison(listOf(tx), past.toString(), past.minusMonths(1).toString())
        assertEquals(300_000L / past.lengthOfMonth(), stats.averageDailyExpense)
    }
}
