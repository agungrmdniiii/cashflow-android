package com.cashflow.app

import com.cashflow.app.data.model.*
import com.cashflow.app.data.service.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class VinaTransactionEvidenceTest {
    private val categories = listOf(Category("cat_makanan", "Makanan", "expense"), Category("transport", "Transportasi", "expense"))
    private fun tx(id: String, amount: Long, date: String = "2026-10-10", category: String? = "cat_makanan", asset: String = "bca", type: String = "expense") =
        Transaction(id, type, amount, id, category, asset, transactionDate = date)
    private val rows = listOf(tx("food", 1000), tx("other-account", 2000, asset = "cash"),
        tx("transport", 4000, "2026-10-20", category = "transport"),
        tx("previous", 3000, "2026-09-10"), tx("income", 9000, type = "income", category = null),
        tx("adjustment", 99999).copy(isAdjustment = true), tx("transfer", 8000, type = "transfer"))
    private fun context(source: List<Transaction> = rows): FinancialChatContext {
        val stats = FinancialInsightService.computeMonthComparison(source, categories, LocalDate.of(2026, 10, 23))
        return FinancialChatContext("Agung", "2026-10-23", 0,
            listOf(Asset("bca", "BCA", "Bank", 0, 0), Asset("cash", "Cash", "Tunai", 0, 0)),
            stats, "2026-09", "2026-10", stats.categoryDeltas, emptyList(), emptyList(), emptyList(), source, categories)
    }
    private fun query(question: String, context: FinancialChatContext = context()): Pair<String, List<ChatTransactionEvidence>> {
        val evidence = mutableListOf<ChatTransactionEvidence>()
        val answer = FinancialInsightService.evaluateOfflineQuery(question, context) { evidence.add(it) }
        return answer to evidence
    }

    @Test fun categoryAccountAndPeriodUseExactlyTheAnsweredTransactions() {
        val (answer, data) = query("pengeluaran makan BCA bulan ini")
        assertTrue(answer, answer.contains("1.000"))
        assertEquals(listOf("food"), data.single().transactions.map { it.id })
        assertEquals(ReportDateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)), data.single().range)
    }
    @Test fun comparisonKeepsBothDatasetsSeparate() {
        val (_, data) = query("bandingkan pengeluaran makan BCA bulan ini")
        assertEquals(2, data.size)
        assertFalse(data[0].isComparison)
        assertTrue(data[1].isComparison)
        assertEquals(1000L, data[0].transactions.sumOf { it.amount })
        assertEquals(3000L, data[1].transactions.sumOf { it.amount })
    }
    @Test fun latestAndCustomRangeNeverShowUnrelatedRows() {
        assertEquals(listOf("transport"), query("pengeluaran terbaru BCA").second.single().transactions.map { it.id })
        assertEquals(listOf("food"), query("pengeluaran makan BCA 01/10/2026 sampai 15/10/2026").second.single().transactions.map { it.id })
    }
    @Test fun cashflowKeepsIncomeAndExpenseAndExcludesTransfersAndAdjustments() {
        val data = query("arus kas BCA bulan ini").second.single()
        assertTrue(data.isCashflow)
        val totals = summarizeTransactions(data.transactions)
        assertEquals(9000L, totals.income)
        assertEquals(5000L, totals.expense)
        assertEquals(setOf("food", "transport", "income"), data.transactions.map { it.id }.toSet())
    }
    @Test fun emptyResultsAndNonTransactionAnswersDoNotInventEvidence() {
        assertTrue(query("pengeluaran tahun 2020").second.single().transactions.isEmpty())
        for (question in listOf("halo vina", "saldo BCA berapa", "bagaimana cara hemat", "pengeluaran tanggal salah")) {
            assertTrue(question, query(question).second.isEmpty())
        }
    }
    @Test fun snapshotSurvivesSourceChangesAndFollowUpKeepsFilters() = runBlocking {
        val mutable = rows.toMutableList()
        val evidence = query("pengeluaran makan BCA bulan ini", context(mutable)).second.single()
        mutable.clear()
        assertEquals(1000L, evidence.transactions.sumOf { it.amount })
        val first = query("pengeluaran makan BCA bulan ini").first
        val followUp = mutableListOf<ChatTransactionEvidence>()
        FinancialInsightService.sendChatMessage(listOf(ChatMessage(sender = ChatSender.USER, text = "pengeluaran makan BCA bulan ini"),
            ChatMessage(sender = ChatSender.ASSISTANT, text = first), ChatMessage(sender = ChatSender.USER, text = "kalau bulan lalu?")),
            context()) { followUp.add(it) }.getOrThrow()
        assertEquals(listOf("previous"), followUp.single().transactions.map { it.id })
    }
}
