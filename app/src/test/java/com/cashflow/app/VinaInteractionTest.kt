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
        assertTrue(FinancialInsightService.suggestedQuestions(history).containsAll(listOf("Rincikan", "Bandingkan")))
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
        assertTrue(suggestions.contains("Panduan mencatat transaksi"))
        val help = FinancialInsightService.sendChatMessage(listOf(question,
            ChatMessage(sender = ChatSender.ASSISTANT, text = reply), user("ya")), empty).getOrThrow()
        assertTrue(help, help.contains("(+)"))
        assertFalse(help, help.contains("Belum ada transaksi tercatat"))
    }

    @Test fun recommendationsContinueAfterDetailsRankingAndComparisonWithTheSameAccount() = runBlocking {
        val history = mutableListOf(user("pengeluaran BCA bulan lalu"))
        for ((selection, required) in listOf(
            "Rincikan" to "Bekal rapat",
            "Yang paling besar?" to "Bekal rapat",
            "Bandingkan" to "Periode pembanding",
            "Kenapa?" to "Perubahan per kategori"
        )) {
            history.add(ChatMessage(sender = ChatSender.ASSISTANT, text = answer(history)))
            val options = FinancialInsightService.suggestedQuestions(history)
            assertTrue("Missing $selection after ${history.last().text}: $options", selection in options)
            assertTrue(options.size in 1..3)
            history.add(user(selection))
            val result = answer(history)
            assertTrue(result, result.contains(required) && result.contains("75.000"))
            assertFalse(result, result.contains("900.000") || result.contains("125.000"))
        }
    }

    @Test fun completedDetailsOfferAnotherPeriodAndRetainTheAccountWhenSelected() = runBlocking {
        val history = mutableListOf(user("pengeluaran BCA bulan lalu"), user("rincikan"), user("yang paling besar?"), user("bandingkan"), user("kenapa?"))
        history.add(ChatMessage(sender = ChatSender.ASSISTANT, text = answer(history)))
        val suggestions = FinancialInsightService.suggestedQuestions(history)
        assertTrue(suggestions.toString(), "Kalau bulan ini?" in suggestions)
        val next = answer(history + user("Kalau bulan ini?"))
        assertTrue(next, next.contains("125.000"))
        assertFalse(next, next.contains("900.000") || next.contains("2026-09-10"))
    }

    @Test fun recommendationsRemainAvailableForEmptyUnknownGreetingAndErrorReplies() = runBlocking {
        val empty = context.copy(transactions = emptyList())
        val cases = listOf("halo vina", "terima kasih", "tidak dulu", "pertanyaan yang tidak dikenali", "saldo saya", "pengeluaran bulan ini", "kategori lainnya?")
        assertTrue(FinancialInsightService.suggestedQuestions(emptyList()).isNotEmpty())
        for (question in cases) {
            val history = listOf(user(question), ChatMessage(sender = ChatSender.ASSISTANT,
                text = FinancialInsightService.sendChatMessage(listOf(user(question)), empty).getOrThrow()))
            val suggestions = FinancialInsightService.suggestedQuestions(history)
            assertTrue("No recommendations for $question", suggestions.size in 1..3)
            assertEquals(suggestions.distinct(), suggestions)
            for (selection in suggestions) {
                val next = FinancialInsightService.sendChatMessage(history + user(selection), empty).getOrThrow()
                assertFalse("$question -> $selection: $next", next.contains("Saya belum memahami"))
                if (question == "pengeluaran bulan ini") assertFalse(selection in listOf("Rincikan", "Yang paling besar?"))
            }
        }
        assertTrue(FinancialInsightService.suggestedQuestions(listOf(ChatMessage(sender = ChatSender.ASSISTANT, text = "Gagal", isError = true))).isNotEmpty())
    }

    @Test fun pendingQuestionKeepsPreviousRecommendationsUntilItsReplyArrives() = runBlocking {
        val history = listOf(user("pengeluaran BCA bulan lalu"))
        val complete = history + ChatMessage(sender = ChatSender.ASSISTANT, text = answer(history))
        val suggestions = FinancialInsightService.suggestedQuestions(complete)
        assertTrue(suggestions.isNotEmpty())
        assertEquals(suggestions, FinancialInsightService.suggestedQuestions(complete + user("Rincikan")))
    }

    @Test fun periodRecommendationChangesThePrimaryPeriodRatherThanRepeatingTheComparator() = runBlocking {
        val history = listOf(user("pengeluaran BCA bulan ini"), user("dibanding bulan lalu?"), user("kenapa?"))
        val complete = history + ChatMessage(sender = ChatSender.ASSISTANT, text = answer(history))
        val recommendations = FinancialInsightService.suggestedQuestions(complete)
        assertTrue(recommendations.toString(), "Kalau bulan lalu?" in recommendations)
        assertFalse(recommendations.toString(), "Kalau bulan ini?" in recommendations)
    }

    @Test fun allTimeRecommendationsCanContinueThroughComparison() = runBlocking {
        val history = mutableListOf(user("pengeluaran semua"))
        for (selection in listOf("Rincikan", "Yang paling besar?", "Bandingkan", "Kenapa?")) {
            val reply = answer(history)
            history.add(ChatMessage(sender = ChatSender.ASSISTANT, text = reply))
            val suggestions = FinancialInsightService.suggestedQuestions(history)
            assertTrue("$selection missing from $suggestions after $reply", selection in suggestions)
            history.add(user(selection))
        }
    }
}
