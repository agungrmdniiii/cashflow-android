package com.cashflow.app

import androidx.test.platform.app.InstrumentationRegistry
import com.cashflow.app.data.model.*
import com.cashflow.app.data.service.FinancialInsightService
import com.cashflow.app.data.service.FinancialChatContext
import com.cashflow.app.ui.components.Formatters
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Read-only checks for the upgraded emulator fixture; never resets its ledger. */
class UpgradeFeaturesTest {
    @Test fun upgradedFixtureBalancesReportsAndOfflineVinaAgree() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("upgradeAudit") == "approved")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(File(context.filesDir, "upgrade-audit-fixture.json").isFile)
        val repo = (context.applicationContext as CashflowApplication).repository
        repo.awaitInitialLoad()
        repo.reloadForBackgroundCheck()
        assertNull(repo.initError.value)
        assertEquals(10, repo.transactions.value.size)
        assertEquals(1_160_000L, repo.assets.value.first { it.id == "upgrade_cash" }.currentBalance)
        assertEquals(580_000L, repo.assets.value.first { it.id == "upgrade_bank" }.currentBalance)
        assertEquals(0, repo.recalculateBalances(autoFix = false).getOrThrow().discrepanciesFound)
        assertEquals(220_000L, repo.debts.value.first { it.direction == "debt" }.remainingAmount)
        assertEquals(110_000L, repo.debts.value.first { it.direction == "receivable" }.remainingAmount)
        assertEquals(30_000L, repo.goals.value.single().currentAmount)
        val summary = summarizeTransactions(repo.transactions.value)
        assertEquals(200_000L, summary.income)
        assertEquals(50_000L, summary.expense)
        val today = LocalDate.now()
        val stats = FinancialInsightService.computeMonthComparison(repo.transactions.value, repo.categories.value, today)
        val chat = FinancialChatContext("Penguji", today.toString(), 1_740_000L, repo.assets.value, stats,
            today.minusMonths(1).toString().take(7), today.toString().take(7), stats.categoryDeltas,
            repo.budgets.value, repo.debts.value, repo.goals.value, repo.transactions.value, repo.categories.value)
        val answer = FinancialInsightService.sendChatMessage(listOf(ChatMessage(
            text = "berapa total pengeluaran saya bulan ini", sender = ChatSender.USER)), chat).getOrThrow()
        assertTrue("Vina must read the actual nonzero upgraded ledger", answer.contains(Formatters.formatRupiah(50_000L)))
    }
}
