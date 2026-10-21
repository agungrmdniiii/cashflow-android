package com.cashflow.app

import com.cashflow.app.data.model.*
import com.cashflow.app.data.service.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class VinaOfflineTest {
    private val ledger = listOf(
        Transaction("aug", "expense", 123_000, "Agustus", assetId = "cash", transactionDate = "2026-08-10"),
        Transaction("oct", "expense", 234_000, "Oktober", assetId = "cash", transactionDate = "2026-10-02"),
        Transaction("week", "expense", 345_000, "Mingguan", assetId = "cash", transactionDate = "2026-10-05"),
        Transaction("old", "income", 456_000, "Lama", assetId = "cash", transactionDate = "2024-01-01"))
    private fun context(txs: List<Transaction> = ledger): FinancialChatContext {
        val stats = FinancialInsightService.computeMonthComparison(txs, emptyList(), LocalDate.of(2026, 10, 21))
        return FinancialChatContext("", "2026-10-21", 0, listOf(Asset("cash", "Tunai", "Tunai", 0, 0)), stats,
            "2026-09", "2026-10", stats.categoryDeltas, emptyList(), emptyList(), emptyList(), txs, emptyList())
    }
    private fun answer(q: String) = FinancialInsightService.evaluateOfflineQuery(q, context())

    @Test fun relativeCalendarPeriodsUseTheRequestedMonthWeekAndYear() {
        for ((question, value) in listOf("pengeluaran 2 bulan lalu" to "123.000",
            "pengeluaran 2 minggu lalu" to "345.000", "pemasukan 2 tahun lalu" to "456.000")) {
            val result = answer(question)
            assertTrue("$question: $result", result.contains(value))
            assertFalse(result, result.contains("579.000"))
        }
    }
    @Test fun indonesianNumericDateRangeIsInclusive() {
        val result = answer("pengeluaran 01/10/2026 sampai 02/10/2026")
        assertTrue(result, result.contains("234.000"))
        assertTrue(result, result.contains("2026-10-01") && result.contains("2026-10-02"))
        assertFalse(result, result.contains("345.000"))
    }
    @Test fun ambiguousMonthsAskForOneRangeRatherThanSilentlyChoosingOne() {
        val result = answer("pengeluaran september dan oktober 2026")
        assertFalse(result, result.contains("Rp "))
        assertTrue(result, result.contains("periode", true) || result.contains("rentang", true))
    }
    @Test fun unknownAccountCannotReturnAllAccountsAsIfItMatched() {
        val result = answer("pengeluaran dari rekening TidakAda bulan ini")
        assertFalse(result, result.contains("579.000"))
        assertTrue(result, result.contains("rekening", true))
    }
    @Test fun everyConversationReadsTheLatestProvidedLedger() = runBlocking {
        val question = listOf(ChatMessage(sender = ChatSender.USER, text = "pengeluaran oktober 2026"))
        val before = FinancialInsightService.sendChatMessage(question, context()).getOrThrow()
        val after = FinancialInsightService.sendChatMessage(question, context(ledger.filterNot { it.id == "week" })).getOrThrow()
        assertTrue(before, before.contains("579.000"))
        assertTrue(after, after.contains("234.000"))
        assertFalse(after, after.contains("579.000"))
    }
}
