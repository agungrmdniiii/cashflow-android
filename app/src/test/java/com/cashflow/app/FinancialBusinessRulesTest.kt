package com.cashflow.app

import com.cashflow.app.data.model.Asset
import com.cashflow.app.data.model.Transaction
import com.cashflow.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FinancialBusinessRulesTest {

    private lateinit var bca: Asset
    private lateinit var gopay: Asset
    private lateinit var tabungan: Asset

    @Before
    fun setUp() {
        bca = Asset("bca", "BCA", "Bank", openingBalance = 4500000L, currentBalance = 4500000L)
        gopay = Asset("gopay", "GoPay", "E-wallet", openingBalance = 325000L, currentBalance = 325000L)
        tabungan = Asset("tabungan", "Tabungan", "Tabungan", openingBalance = 10000000L, currentBalance = 10000000L)
    }

    private fun calculateTotalWealth(assets: List<Asset>): Long {
        return assets.filter { it.isActive }.sumOf { it.currentBalance }
    }

    private fun calculateNetCashflow(transactions: List<Transaction>): Long {
        val income = transactions.filter { it.type == TransactionType.INCOME.value }.sumOf { it.amount }
        val expense = transactions.filter { it.type == TransactionType.EXPENSE.value }.sumOf { it.amount }
        return income - expense
    }

    @Test
    fun testIncomeFormula() {
        // Gaji Rp 5.000.000 masuk ke BCA
        val previousBalance = bca.currentBalance
        val incomeAmount = 5000000L
        val updatedBca = bca.copy(currentBalance = previousBalance + incomeAmount)

        assertEquals(9500000L, updatedBca.currentBalance)
    }

    @Test
    fun testExpenseFormula() {
        // Makan Rp 50.000 menggunakan BCA
        val previousBalance = bca.currentBalance
        val expenseAmount = 50000L
        val updatedBca = bca.copy(currentBalance = previousBalance - expenseAmount)

        assertEquals(4450000L, updatedBca.currentBalance)
    }

    @Test
    fun testTransferFormula_PreservesTotalWealth() {
        val initialWealth = calculateTotalWealth(listOf(bca, gopay, tabungan))
        assertEquals(14825000L, initialWealth)

        // Transfer Rp 1.000.000 dari BCA ke Tabungan
        val transferAmount = 1000000L
        val updatedBca = bca.copy(currentBalance = bca.currentBalance - transferAmount)
        val updatedTabungan = tabungan.copy(currentBalance = tabungan.currentBalance + transferAmount)

        assertEquals(3500000L, updatedBca.currentBalance)
        assertEquals(11000000L, updatedTabungan.currentBalance)

        val newWealth = calculateTotalWealth(listOf(updatedBca, gopay, updatedTabungan))
        assertEquals(initialWealth, newWealth)
    }

    @Test
    fun testTransfer_DoesNotAffectNetCashflow() {
        val transactions = mutableListOf<Transaction>()

        // 1. Income: Gaji Rp 5.000.000
        transactions.add(Transaction("tx1", TransactionType.INCOME.value, 5000000L, "Gaji", assetId = "bca", transactionDate = "2026-10-06"))

        // 2. Expense: Makan Rp 100.000
        transactions.add(Transaction("tx2", TransactionType.EXPENSE.value, 100000L, "Makan", assetId = "bca", transactionDate = "2026-10-06"))

        val netBeforeTransfer = calculateNetCashflow(transactions)
        assertEquals(4900000L, netBeforeTransfer)

        // 3. Transfer: Rp 1.000.000 dari BCA ke Tabungan
        transactions.add(Transaction("tx3", TransactionType.TRANSFER.value, 1000000L, "Transfer", assetId = "bca", destinationAssetId = "tabungan", transactionDate = "2026-10-06"))

        val netAfterTransfer = calculateNetCashflow(transactions)
        // Net Cashflow must NOT change due to transfer!
        assertEquals(netBeforeTransfer, netAfterTransfer)
    }

    @Test
    fun testEditTransaction_RevertAndApply() {
        // Initially: Expense Rp 100.000 dari BCA
        var currentBca = bca.copy(currentBalance = bca.currentBalance - 100000L)
        var currentGopay = gopay

        assertEquals(4400000L, currentBca.currentBalance)
        assertEquals(325000L, currentGopay.currentBalance)

        // Then edited: Expense Rp 100.000 dari GoPay
        // Step 1: Revert on BCA (+100.000)
        currentBca = currentBca.copy(currentBalance = currentBca.currentBalance + 100000L)
        // Step 2: Apply on GoPay (-100.000)
        currentGopay = currentGopay.copy(currentBalance = currentGopay.currentBalance - 100000L)

        assertEquals(4500000L, currentBca.currentBalance)
        assertEquals(225000L, currentGopay.currentBalance)
    }

    @Test
    fun testDeleteTransaction_RestoresBalance() {
        // Expense Rp 100.000 dari BCA
        val afterExpenseBca = bca.copy(currentBalance = bca.currentBalance - 100000L)
        assertEquals(4400000L, afterExpenseBca.currentBalance)

        // When deleted: revert effect (+100.000)
        val restoredBca = afterExpenseBca.copy(currentBalance = afterExpenseBca.currentBalance + 100000L)
        assertEquals(4500000L, restoredBca.currentBalance)
    }
}
