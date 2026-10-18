package com.cashflow.app

import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.test.platform.app.InstrumentationRegistry
import com.cashflow.app.data.model.*
import com.cashflow.app.data.service.*
import com.cashflow.app.ui.components.Formatters
import com.cashflow.app.ui.components.home.HomeInsightBanner
import com.cashflow.app.ui.theme.CashflowTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** These checks never create, edit, or delete financial records. */
class FinancialInsightUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun insightNavigationChangesContentWrapsAndCanBeDismissed() {
        val index = mutableIntStateOf(0)
        var dismissed = false
        val insights = listOf(RuleBasedInsight("Pos terbesar", "Makan: Rp 200.000", "info"),
            RuleBasedInsight("Anggaran mendekati batas", "Sisa Rp 20.000", "warning"))
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, ComponentActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
        instrumentation.runOnMainSync {
            activity.setContent { CashflowTheme {
                HomeInsightBanner(insights[index.intValue], onDismiss = { dismissed = true },
                    position = index.intValue + 1, total = insights.size,
                    onNext = { index.intValue = (index.intValue + 1) % insights.size })
            } }
        }
        try {
            node("1 dari 2")
            click("Insight berikutnya")
            node("Anggaran mendekati batas")
            node("2 dari 2")
            click("Insight berikutnya")
            node("Pos terbesar")
            click("Tutup insight")
            instrumentation.runOnMainSync { assertTrue(dismissed) }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun localVinaReadsInstalledLedgerAndResolvesPreviousMonthFollowUp() = runBlocking {
        val app = instrumentation.targetContext.applicationContext as CashflowApplication
        val repo = app.repository
        repo.awaitInitialLoad()
        assertNull(repo.initError.value)
        val today = LocalDate.now()
        val transactions = repo.transactions.value.toList()
        val stats = FinancialInsightService.computeMonthComparison(transactions, repo.categories.value, today)
        val context = FinancialChatContext("", today.toString(), repo.assets.value.sumOf { it.currentBalance },
            repo.assets.value, stats, today.minusMonths(1).toString().take(7), today.toString().take(7),
            stats.categoryDeltas, repo.budgets.value, repo.debts.value, repo.goals.value, transactions, repo.categories.value)
        val history = listOf("pengeluaran bulan ini", "terima kasih", "kalau bulan lalu?")
            .map { ChatMessage(text = it, sender = ChatSender.USER) }
        val answer = FinancialInsightService.sendChatMessage("", history, context).getOrThrow()
        val expected = transactions.filter { !it.isAdjustment && it.type == "expense" &&
            it.transactionDate.startsWith(context.prevMonthPrefix) }.sumOf { it.amount }
        assertTrue("Must use installed ledger for last month", answer.contains(Formatters.formatRupiah(expected)))
        assertTrue("Must preserve requested month", answer.contains(context.prevMonthPrefix + "-01"))
        assertEquals("Chat must not alter transactions", transactions, repo.transactions.value)
    }

    private fun node(text: String): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < deadline) {
            find(instrumentation.uiAutomation.rootInActiveWindow, text)?.let { return it }
            SystemClock.sleep(50)
        }
        error("Control not visible: $text")
    }
    private fun find(root: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.text?.toString() == text || root.contentDescription?.toString() == text) return root
        for (i in 0 until root.childCount) find(root.getChild(i), text)?.let { return it }
        return null
    }
    private fun click(text: String) {
        var target: AccessibilityNodeInfo? = node(text)
        while (target != null && !target.isClickable) target = target.parent
        assertTrue("Control not clickable: $text", target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        instrumentation.waitForIdleSync()
    }
}
