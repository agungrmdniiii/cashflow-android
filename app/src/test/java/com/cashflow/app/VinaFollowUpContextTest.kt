package com.cashflow.app

import com.cashflow.app.data.model.*
import com.cashflow.app.data.service.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class VinaFollowUpContextTest {
    private val categories = listOf(Category("cat_makanan", "Makan", "expense"), Category("game", "Game", "expense"),
        Category("cat_transportasi", "Transport", "expense"), Category("other", "Lainnya", "expense"), Category("salary", "Gaji", "income"))
    private fun tx(id: String, amount: Long, date: String, category: String, asset: String = "bca", type: String = "expense") =
        Transaction(id, type, amount, id, categoryId = category, assetId = asset, transactionDate = date)
    private val rows = listOf(tx("Makan BCA", 10_000, "2026-10-10", "cat_makanan"), tx("Game BCA", 30_000, "2026-10-12", "game"),
        tx("Transport BCA", 5_000, "2026-10-13", "cat_transportasi"), tx("Lainnya BCA", 2_000, "2026-10-14", "other"),
        tx("September makan", 3_000, "2026-09-10", "cat_makanan"), tx("September game", 5_000, "2026-09-12", "game"),
        tx("Agustus makan", 1_000, "2026-08-10", "cat_makanan"), tx("Kas pribadi", 999_000, "2026-10-10", "game", "cash"),
        tx("Gaji BCA", 100_000, "2026-10-10", "salary", type = "income"), tx("Gaji kas", 50_000, "2026-10-10", "salary", "cash", "income"))
    private val stats = FinancialInsightService.computeMonthComparison(rows, categories, LocalDate.of(2026, 10, 24))
    private val context = FinancialChatContext("Agung", "2026-10-24", 0,
        listOf(Asset("bca", "BCA", "Bank", 0, 0), Asset("cash", "Tunai", "Tunai", 0, 0)), stats,
        "2026-09", "2026-10", stats.categoryDeltas, emptyList(), emptyList(), emptyList(), rows, categories)

    private suspend fun conversation(vararg questions: String, ctx: FinancialChatContext = context): Pair<String, List<ChatTransactionEvidence>> {
        val history = mutableListOf<ChatMessage>()
        var reply = ""
        val evidence = mutableListOf<ChatTransactionEvidence>()
        for (question in questions) {
            history.add(ChatMessage(sender = ChatSender.USER, text = question))
            evidence.clear()
            reply = FinancialInsightService.sendChatMessage(history, ctx) { evidence.add(it) }.getOrThrow()
            history.add(ChatMessage(sender = ChatSender.ASSISTANT, text = reply))
        }
        return reply to evidence.toList()
    }

    @Test fun everydayLargestQuestionsKeepTheAccountAndPeriod() = runBlocking {
        for (question in listOf("yang paling besar apa?", "yang mana paling besar?", "paling boros yang mana?", "transaksi terbesar apa?", "kalau yang paling besar apa ya?")) {
            val (reply, evidence) = conversation("pengeluaran BCA bulan ini", question)
            assertTrue("$question\n$reply", reply.contains("Game BCA") && reply.indexOf("Game BCA") < reply.indexOf("Makan BCA"))
            assertFalse(reply, reply.contains("999.000"))
            assertEquals(LocalDate.of(2026, 10, 1), evidence.single().range.start)
            assertTrue(evidence.single().transactions.all { it.assetId == "bca" })
        }
    }

    @Test fun smallTalkAndPeriodChangesKeepTheLastFinancialAction() = runBlocking {
        val (reply, evidence) = conversation("pengeluaran BCA bulan lalu", "apa kabar", "yang paling besar apa?", "kalau bulan ini?")
        assertTrue(reply, reply.contains("Game BCA"))
        assertFalse(reply, reply.contains("September game") || reply.contains("Kas pribadi"))
        assertEquals(LocalDate.of(2026, 10, 1), evidence.single().range.start)
    }

    @Test fun explicitComparisonKeepsTheOriginalScopeAndUsesTheRequestedMonth() = runBlocking {
        for ((question, month, amount) in listOf(Triple("dibanding bulan lalu?", 9, "8.000"), Triple("bandingkan dengan Agustus 2026", 8, "1.000"))) {
            val (reply, evidence) = conversation("pengeluaran BCA bulan ini", question)
            assertTrue(reply, reply.contains("47.000") && reply.contains(amount))
            assertEquals(listOf(10, month), evidence.map { it.range.start.monthValue })
            assertTrue(evidence[1].isComparison)
            assertTrue(evidence.flatMap { it.transactions }.all { it.assetId == "bca" })
        }
    }

    @Test fun whyAfterComparisonKeepsTheComparatorAndExplainsRecordedChanges() = runBlocking {
        for (question in listOf("kenapa?", "kok bisa begitu?", "mengapa demikian?")) {
            val (reply, evidence) = conversation("pengeluaran BCA bulan ini", "dibanding Agustus 2026", question)
            assertEquals(listOf(10, 8), evidence.map { it.range.start.monthValue })
            assertTrue(reply, reply.contains("Perubahan per kategori") && reply.contains("Game") && reply.contains("30.000"))
            assertTrue(reply, reply.contains("catatan"))
            assertEquals("The explanation must not repeat its category table", 1, Regex("• Game:").findAll(reply).count())
            assertFalse(reply, reply.contains("Kas pribadi"))
        }
    }

    @Test fun otherCategoriesExcludeTheSelectedCategoryWithoutSelectingTheLiteralLainnyaCategory() = runBlocking {
        val (reply, evidence) = conversation("pengeluaran makan BCA bulan ini", "kategori lainnya?")
        assertEquals(setOf("Game BCA", "Transport BCA", "Lainnya BCA"), evidence.single().transactions.map { it.id }.toSet())
        assertTrue(reply, reply.contains("37.000") && reply.contains("Selain Makan"))
        assertFalse(reply, reply.contains("Makan BCA") || reply.contains("999.000"))
    }

    @Test fun otherCategoriesCanShowCategoriesBeyondTheInitialFive() = runBlocking {
        val cats = (0..5).map { Category("extra$it", "Kategori $it", "expense") }
        val extraRows = cats.mapIndexed { i, cat -> tx("extra$i", (6L - i) * 1000, "2026-10-10", cat.id) }
        val (reply, evidence) = conversation("pengeluaran BCA bulan ini", "kategori lainnya?", ctx = context.copy(categories = cats, transactions = extraRows))
        assertEquals(listOf("extra5"), evidence.single().transactions.map { it.id })
        assertTrue(reply, reply.contains("Kategori 5") && reply.contains("1.000"))
        assertFalse(reply, reply.contains("Kategori 0"))
    }

    @Test fun categoryExclusionsSurviveAnotherActionAndAPeriodChange() = runBlocking {
        val (ranked, first) = conversation("pengeluaran makan BCA bulan ini", "kalau kategori lainnya?", "yang paling besar apa?")
        assertTrue(ranked, ranked.contains("Game BCA"))
        assertFalse(ranked, ranked.contains("Makan BCA"))
        assertFalse(first.single().transactions.any { it.categoryId == "cat_makanan" })
        val (previous, second) = conversation("pengeluaran makan BCA bulan ini", "kategori lainnya?", "yang paling besar apa?", "kalau bulan lalu?")
        assertTrue(previous, previous.contains("September game"))
        assertFalse(previous, previous.contains("September makan"))
        assertEquals(listOf("September game"), second.single().transactions.map { it.id })
    }

    @Test fun ambiguousFollowUpsAskForContextWithoutInventingAmounts() = runBlocking {
        for (question in listOf("yang paling besar apa?", "kenapa?", "dibanding bulan lalu?", "kategori lainnya?")) {
            val (reply, evidence) = conversation("halo vina", question)
            assertTrue("$question\n$reply", reply.trimEnd().endsWith("?"))
            assertFalse(reply, reply.contains("Rp ") || reply.contains("belum memahami"))
            assertTrue(evidence.isEmpty())
        }
    }

    @Test fun aNewExplicitQuestionResetsTheOldAccountCategoryAndPeriod() = runBlocking {
        val (reply, evidence) = conversation("pengeluaran makan BCA bulan lalu", "pemasukan bulan ini", "yang paling besar apa?")
        assertTrue(reply, reply.contains("Gaji BCA") && reply.contains("Gaji kas"))
        assertEquals(setOf("bca", "cash"), evidence.single().transactions.map { it.assetId }.toSet())
        assertEquals(LocalDate.of(2026, 10, 1), evidence.single().range.start)
        assertTrue(evidence.single().transactions.all { it.type == "income" })
    }

    @Test fun aLongerConversationDoesNotForgetItsInitialScope() = runBlocking {
        val questions = listOf("pengeluaran BCA bulan ini") + List(10) { "rincikan" } + "kalau bulan lalu?"
        val (reply, evidence) = conversation(*questions.toTypedArray())
        assertTrue(reply, reply.contains("September game") && reply.contains("September makan"))
        assertEquals(LocalDate.of(2026, 9, 1), evidence.single().range.start)
        assertTrue(evidence.single().transactions.all { it.assetId == "bca" })
    }

    @Test fun noAdditionalCategoriesDoesNotOfferDetailsForAnEmptyResult() = runBlocking {
        val (reply, evidence) = conversation("pengeluaran BCA bulan ini", "kategori lainnya?")
        assertTrue(reply, reply.contains("Belum ada kategori tambahan"))
        assertFalse(reply, reply.trimEnd().endsWith("?"))
        assertTrue(evidence.single().transactions.isEmpty())
    }

    @Test fun recommendedOtherCategoriesCanBeSelectedWithoutLosingTheScope() = runBlocking {
        val cats = (0..5).map { Category("extra$it", "Kategori $it", "expense") }
        val ctx = context.copy(categories = cats, transactions = cats.mapIndexed { i, cat -> tx("extra$i", (6L - i) * 1000, "2026-10-10", cat.id) })
        for (topic in listOf("pengeluaran", "pengeluaran dan pemasukan")) {
            val history = mutableListOf(ChatMessage(sender = ChatSender.USER, text = "$topic BCA bulan ini"),
                ChatMessage(sender = ChatSender.USER, text = "rincikan"))
            val evidence = mutableListOf<ChatTransactionEvidence>()
            val reply = FinancialInsightService.sendChatMessage(history, ctx) { evidence.add(it) }.getOrThrow()
            history.add(ChatMessage(sender = ChatSender.ASSISTANT, text = reply, transactionEvidence = evidence))
            val recommendations = FinancialInsightService.suggestedQuestions(history)
            if (topic == "pengeluaran") {
                assertTrue(recommendations.toString(), "Kategori lainnya?" in recommendations)
                evidence.clear()
                val next = FinancialInsightService.sendChatMessage(history + ChatMessage(sender = ChatSender.USER, text = "Kategori lainnya?"), ctx) { evidence.add(it) }.getOrThrow()
                assertTrue(next, next.contains("Kategori 5") && next.contains("1.000"))
                assertEquals(listOf("extra5"), evidence.single().transactions.map { it.id })
            } else assertFalse("Mixed cash flow needs an explicit category type", "Kategori lainnya?" in recommendations)
        }
    }
}
