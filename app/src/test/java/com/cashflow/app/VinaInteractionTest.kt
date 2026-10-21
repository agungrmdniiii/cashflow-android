package com.cashflow.app

import com.cashflow.app.data.model.*
import com.cashflow.app.data.service.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class VinaInteractionTest {
    private val transactions = listOf(
        Transaction("september", "expense", 75_000, "Bekal rapat", assetId = "bca", transactionDate = "2026-09-10"),
        Transaction("other", "expense", 900_000, "Rekening lain", assetId = "cash", transactionDate = "2026-09-10"),
        Transaction("october", "expense", 125_000, "Belanja Oktober", assetId = "bca", transactionDate = "2026-10-10"))
    private val stats = FinancialInsightService.computeMonthComparison(transactions, emptyList(), LocalDate.of(2026, 10, 21))
    private val context = FinancialChatContext("Agung", "2026-10-21", 0,
        listOf(Asset("bca", "BCA", "Bank", 0, 0), Asset("cash", "Tunai", "Tunai", 0, 0)), stats,
        "2026-09", "2026-10", stats.categoryDeltas, emptyList(), emptyList(), emptyList(), transactions, emptyList())
    private fun user(text: String) = ChatMessage(sender = ChatSender.USER, text = text)
    private suspend fun answer(history: List<ChatMessage>) = FinancialInsightService.sendChatMessage(history, context).getOrThrow()

    @Test fun answerOffersARelevantNextStepAndYesKeepsItsAccountAndPeriod() = runBlocking {
        val question = user("pengeluaran dari BCA bulan lalu")
        val reply = answer(listOf(question))
        assertTrue(reply, reply.contains("75.000") && reply.trimEnd().endsWith("?"))
        val followUp = answer(listOf(question, ChatMessage(sender = ChatSender.ASSISTANT, text = reply), user("ya boleh")))
        assertTrue(followUp, followUp.contains("Bekal rapat") && followUp.contains("2026-09-10"))
        assertFalse(followUp, followUp.contains("900.000") || followUp.contains("Belanja Oktober"))
    }
    @Test fun smallTalkDoesNotReplaceTheFinancialTopic() = runBlocking {
        val reply = answer(listOf(user("pengeluaran dari BCA bulan lalu"), user("apa kabar"), user("rincikan")))
        assertTrue(reply, reply.contains("Bekal rapat"))
        assertFalse(reply, reply.contains("900.000"))
    }
    @Test fun decliningIsAcknowledgedWithoutAnotherFinancialAnswer() = runBlocking {
        val question = user("pengeluaran bulan lalu")
        val reply = answer(listOf(question))
        val declined = answer(listOf(question, ChatMessage(sender = ChatSender.ASSISTANT, text = reply), user("tidak dulu")))
        assertTrue(declined, declined.contains("Baik", true))
        assertFalse(declined, declined.contains("Rp ") || declined.endsWith("?"))
    }
    @Test fun yesWithoutAnOfferAsksWhatToDiscuss() = runBlocking {
        val reply = answer(listOf(user("ya")))
        assertTrue(reply, reply.endsWith("?"))
        assertFalse(reply, reply.contains("Rp "))
    }
    @Test fun everydayGreetingsStayPersonalAndDoNotClaimFinancialHealth() = runBlocking {
        for (greeting in listOf("halo vina", "selamat pagi vina", "hai vina kamu apa kabar?")) {
            val reply = answer(listOf(user(greeting)))
            assertTrue(reply, reply.contains("Agung") && reply.contains("Vina"))
            assertFalse(reply, reply.contains("Rp "))
        }
    }

    @Test fun selectingComparisonKeepsTheOriginalAccountAndRange() = runBlocking {
        val question = user("pengeluaran BCA 01/09/2026 sampai 30/09/2026")
        val reply = answer(listOf(question))
        val history = listOf(question, ChatMessage(sender = ChatSender.ASSISTANT, text = reply))
        assertEquals(listOf("Rincikan", "Bandingkan"), FinancialInsightService.suggestedQuestions(history))
        val comparison = answer(history + user("Bandingkan"))
        assertTrue(comparison, comparison.contains("75.000") && comparison.contains("Periode pembanding"))
        assertFalse(comparison, comparison.contains("900.000"))
    }
    @Test fun switchingPeriodAfterANumericDateQuestionReplacesTheOldDate() = runBlocking {
        val result = answer(listOf(user("pengeluaran BCA 10/09/2026"), user("kalau bulan ini?")))
        assertTrue(result, result.contains("125.000"))
        assertFalse(result, result.contains("75.000"))
    }
    @Test fun emptyLedgerOffersHelpRatherThanPretendingThereAreDetails() = runBlocking {
        val empty = context.copy(transactions = emptyList())
        val question = user("pengeluaran bulan lalu")
        val reply = FinancialInsightService.sendChatMessage(listOf(question), empty).getOrThrow()
        val suggestions = FinancialInsightService.suggestedQuestions(listOf(question, ChatMessage(sender = ChatSender.ASSISTANT, text = reply)))
        assertFalse(suggestions.contains("Rincikan"))
        assertTrue(reply, reply.endsWith("?"))
        assertEquals(listOf("Panduan mencatat transaksi"), suggestions)
        val help = FinancialInsightService.sendChatMessage(listOf(question,
            ChatMessage(sender = ChatSender.ASSISTANT, text = reply), user("ya")), empty).getOrThrow()
        assertTrue(help, help.contains("(+)"))
        assertFalse(help, help.contains("Belum ada transaksi tercatat"))
    }
}
