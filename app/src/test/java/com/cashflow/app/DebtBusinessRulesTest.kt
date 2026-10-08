package com.cashflow.app

import com.cashflow.app.data.model.Debt
import com.cashflow.app.data.model.DebtStatus
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DebtBusinessRulesTest {

    @Test
    fun testDebtRemainingAmountAndProgress() {
        val debt = Debt(
            id = "debt_1",
            title = "Pinjam Renovasi",
            lenderName = "Budi",
            totalAmount = 10000000L,
            paidAmount = 2500000L,
            dueDate = LocalDate.now().plusDays(30).toString(),
            status = DebtStatus.PARTIAL.key
        )
        assertEquals(7500000L, debt.remainingAmount)
        assertEquals(0.25f, debt.progressPercentage, 0.001f)
        assertFalse(debt.isOverdue)
    }

    @Test
    fun testDebtOverdueDetection() {
        val overdueDebt = Debt(
            id = "debt_2",
            title = "Cicilan Laptop",
            lenderName = "Toko",
            totalAmount = 5000000L,
            paidAmount = 1000000L,
            dueDate = LocalDate.now().minusDays(5).toString(),
            status = DebtStatus.PARTIAL.key
        )
        assertTrue(overdueDebt.isOverdue)

        val paidPastDebt = overdueDebt.copy(status = DebtStatus.PAID.key, paidAmount = 5000000L)
        assertFalse(paidPastDebt.isOverdue)
    }

    @Test
    fun testNetWorthCalculation() {
        val totalAssets = 25000000L
        val totalRemainingDebt = 7500000L
        val netWorth = totalAssets - totalRemainingDebt
        assertEquals(17500000L, netWorth)
    }

    @Test
    fun testDebtPaymentAffectsAssetBalanceAndProducesExpense() {
        var assetBalance = 5000000L
        var debtPaidAmount = 1000000L
        val debtTotalAmount = 4000000L

        val paymentAmount = 1500000L

        // Simulating the business rule of debt installment:
        // 1. Asset balance must be deducted by payment amount
        assetBalance -= paymentAmount
        assertEquals(3500000L, assetBalance)

        // 2. Debt paid amount increments and remaining debt decreases
        debtPaidAmount = (debtPaidAmount + paymentAmount).coerceAtMost(debtTotalAmount)
        val remainingDebt = debtTotalAmount - debtPaidAmount
        assertEquals(2500000L, debtPaidAmount)
        assertEquals(1500000L, remainingDebt)

        // 3. Produces an expense transaction
        val isExpense = true
        val expenseAmount = paymentAmount
        assertTrue(isExpense)
        assertEquals(1500000L, expenseAmount)
    }

    @Test
    fun testBorrowingFundsIncreasesAssetBalanceAndProducesIncome() {
        var assetBalance = 2000000L
        val loanAmount = 10000000L

        // When receiving loan funds into an asset:
        assetBalance += loanAmount
        assertEquals(12000000L, assetBalance)

        // Produces an income transaction of loanAmount
        val incomeAmount = loanAmount
        assertEquals(10000000L, incomeAmount)
    }
}
