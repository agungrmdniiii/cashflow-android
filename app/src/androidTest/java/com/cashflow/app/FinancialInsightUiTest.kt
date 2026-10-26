package com.cashflow.app

import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

    @Test fun hiddenAmountsNeverRemainInInsightTextOrAccessibilityAndCanBeRevealedAgain() {
        val hidden = androidx.compose.runtime.mutableStateOf(true)
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, ComponentActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
        try {
            instrumentation.runOnMainSync {
                activity.setContent { CashflowTheme {
                    androidx.compose.runtime.CompositionLocalProvider(com.cashflow.app.ui.components.LocalAmountsHidden provides hidden.value) {
                        HomeInsightBanner(RuleBasedInsight("Target pribadi Rp 99.000", "Pengeluaran bulan ini Rp 70.000", "warning"),
                            onDismiss = {}, modifier = Modifier.statusBarsPadding())
                    }
                } }
            }
            node("INSIGHT KEUANGAN")
            assertNull("The hidden insight must not expose its amount through text or accessibility",
                find(instrumentation.uiAutomation.rootInActiveWindow, "Pengeluaran bulan ini Rp 70.000"))
            assertNull(find(instrumentation.uiAutomation.rootInActiveWindow, "Target pribadi Rp 99.000"))
            node("Insight disembunyikan")
            instrumentation.runOnMainSync { hidden.value = false }
            node("Pengeluaran bulan ini Rp 70.000")
            node("Target pribadi Rp 99.000")
            instrumentation.runOnMainSync { hidden.value = true }
            node("Insight disembunyikan")
            assertNull(find(instrumentation.uiAutomation.rootInActiveWindow, "Pengeluaran bulan ini Rp 70.000"))
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun suggestedFollowUpButtonsContinueTheConversationWithoutChangingTheLedger() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        lateinit var vm: com.cashflow.app.ui.CashflowViewModel
        instrumentation.runOnMainSync {
            vm = androidx.lifecycle.ViewModelProvider(activity)[com.cashflow.app.ui.CashflowViewModel::class.java]
            activity.setContent { CashflowTheme {
                com.cashflow.app.ui.screens.FinancialChatContent(vm, onDismiss = {})
            } }
        }
        try {
            fun awaitReply() {
                val deadline = SystemClock.uptimeMillis() + 5000
                while (vm.isChatLoading.value && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
                assertFalse("Vina must finish processing", vm.isChatLoading.value)
                instrumentation.waitForIdleSync()
            }
            fun selectRecommendation(question: String) {
                click(question)
                val deadline = SystemClock.uptimeMillis() + 5000
                while (vm.chatMessages.value.lastOrNull { it.sender == ChatSender.USER }?.text != question &&
                    SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
                assertEquals("Recommendation must submit before waiting for its reply", question,
                    vm.chatMessages.value.lastOrNull { it.sender == ChatSender.USER }?.text)
                awaitReply()
            }
            node("Pengeluaran bulan ini")
            val deadline = SystemClock.uptimeMillis() + 5000
            while (!vm.initialLoadComplete.value && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
            val before = vm.transactions.value.toList()
            org.junit.Assume.assumeTrue("Needs an installed ledger with expenses", before.any { it.type == "expense" && !it.isAdjustment })
            instrumentation.runOnMainSync { vm.sendChatMessage("pengeluaran semua") }
            awaitReply()
            node("Rincikan")
            node("Bandingkan")
            selectRecommendation("Rincikan")
            node("Yang paling besar?")
            selectRecommendation("Yang paling besar?")
            node("Bandingkan")
            selectRecommendation("Bandingkan")
            assertTrue("Expected contextual recommendation; last reply: ${vm.chatMessages.value.last().text}",
                FinancialInsightService.suggestedQuestions(vm.chatMessages.value).contains("Kenapa?"))
            selectRecommendation("Kenapa?")
            node("Rincikan")
            instrumentation.runOnMainSync {
                assertEquals(listOf("pengeluaran semua", "Rincikan", "Yang paling besar?", "Bandingkan", "Kenapa?"),
                    vm.chatMessages.value.filter { it.sender == ChatSender.USER }.map { it.text })
                assertFalse(vm.isChatLoading.value)
                assertTrue(vm.chatMessages.value.last().text.contains("Perubahan per kategori"))
                assertEquals(before, vm.transactions.value)
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun dismissedHomeInsightStaysHiddenForTheSessionAndReturnsInANewOne() {
        androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            node("Tutup insight")
            click("Tutup insight")
            scenario.onActivity {
                androidx.lifecycle.ViewModelProvider(it)[com.cashflow.app.ui.CashflowViewModel::class.java].selectTab(1)
            }
            node("Menu transaksi")
            scenario.onActivity {
                androidx.lifecycle.ViewModelProvider(it)[com.cashflow.app.ui.CashflowViewModel::class.java].selectTab(0)
            }
            node("Total Saldo Rekening")
            assertNull("Tab changes must not restore a dismissed insight", find(instrumentation.uiAutomation.rootInActiveWindow, "Tutup insight"))
            scenario.recreate()
            node("Total Saldo Rekening")
            assertNull("Activity recreation must preserve the dismissal", find(instrumentation.uiAutomation.rootInActiveWindow, "Tutup insight"))
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            node("Total Saldo Rekening")
            assertNull("Brief backgrounding must not restore the insight", find(instrumentation.uiAutomation.rootInActiveWindow, "Tutup insight"))
        }
        androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use {
            node("Tutup insight")
        }
    }

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

    @Test fun insightRemainsReadableAtNarrowWidthAndLargerTextInBothThemes() {
        val title = "Anggaran pengeluaran hampir mencapai batas bulan ini"
        val message = "Pengeluaran makan dan transportasi mencapai Rp 1.250.000. Periksa sisa anggaran sebelum mencatat pengeluaran berikutnya."
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, ComponentActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
        try {
            for (dark in listOf(false, true)) {
                instrumentation.runOnMainSync {
                    activity.setContent {
                        val density = androidx.compose.ui.platform.LocalDensity.current.density
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density, 1.3f)
                        ) {
                            CashflowTheme(darkTheme = dark) {
                                androidx.compose.foundation.layout.Box(
                                    Modifier.width(320.dp).statusBarsPadding().semantics {
                                        contentDescription = if (dark) "Pratinjau gelap" else "Pratinjau terang"
                                    }
                                ) {
                                    HomeInsightBanner(RuleBasedInsight(title, message, "warning"), onDismiss = {},
                                        total = 12, onNext = {})
                                }
                            }
                        }
                    }
                }
                node(if (dark) "Pratinjau gelap" else "Pratinjau terang")
                val heading = android.graphics.Rect().also { node(title).getBoundsInScreen(it) }
                val body = android.graphics.Rect().also { node(message).getBoundsInScreen(it) }
                val close = android.graphics.Rect().also { node("Tutup insight").getBoundsInScreen(it) }
                val next = android.graphics.Rect().also { node("Insight berikutnya").getBoundsInScreen(it) }
                assertTrue("Body must use the same full width as title", body.width() == heading.width())
                assertTrue("Title must sit below the close control", heading.top >= close.bottom)
                assertTrue("Body must not overlap the title", body.top >= heading.bottom)
                assertTrue("Navigation must sit below the body", next.top >= body.bottom)
                node("1 dari 12")
                instrumentation.waitForIdleSync()
                SystemClock.sleep(150) // Allow the new theme to reach the screenshot surface.
                val screenshot = instrumentation.uiAutomation.takeScreenshot()
                val output = java.io.File(instrumentation.targetContext.externalCacheDir,
                    if (dark) "insight-dark.png" else "insight-light.png")
                output.outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                screenshot.recycle()
            }
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
        val answer = FinancialInsightService.sendChatMessage(history, context).getOrThrow()
        val expected = transactions.filter { !it.isAdjustment && it.type == "expense" &&
            it.transactionDate.startsWith(context.prevMonthPrefix) }.sumOf { it.amount }
        assertTrue("Must use installed ledger for last month", answer.contains(Formatters.formatRupiah(expected)))
        assertTrue("Must preserve requested month", answer.contains(context.prevMonthPrefix + "-01"))
        assertEquals("Chat must not alter transactions", transactions, repo.transactions.value)
    }

    @Test fun automaticBackupChoicesStayHiddenUntilEnabledAndRetainTheirValues() {
        val context = instrumentation.targetContext
        val store = com.cashflow.app.data.backup.BackupSettingsStore(context)
        val original = store.automation()
        org.junit.Assume.assumeFalse("Use an audit device with automatic backups disabled", original.enabled)
        org.junit.Assume.assumeTrue("Use an audit device without a connected Google account", store.driveAccount == null)
        store.save(original.copy(destination = com.cashflow.app.data.backup.BackupDestination.DRIVE,
            frequency = com.cashflow.app.data.backup.BackupFrequency.DAILY, network = com.cashflow.app.data.backup.BackupNetwork.WIFI))
        val activity = instrumentation.startActivitySync(Intent(context, ComponentActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
        try {
            instrumentation.runOnMainSync { activity.setContent { CashflowTheme {
                androidx.compose.foundation.layout.Column(Modifier.statusBarsPadding().verticalScroll(rememberScrollState())) {
                    com.cashflow.app.ui.screens.settings.BackupSettingsPanel({}, {})
                }
            } } }
            fun scrollable(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (root == null || root.isScrollable) return root
                for (i in 0 until root.childCount) scrollable(root.getChild(i))?.let { return it }
                return null
            }
            fun reveal(label: String) {
                repeat(6) {
                    if (find(instrumentation.uiAutomation.rootInActiveWindow, label) != null) return
                    scrollable(instrumentation.uiAutomation.rootInActiveWindow)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                    SystemClock.sleep(50)
                }
                repeat(8) {
                    if (find(instrumentation.uiAutomation.rootInActiveWindow, label) != null) return
                    scrollable(instrumentation.uiAutomation.rootInActiveWindow)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    SystemClock.sleep(100)
                }
                node(label)
            }
            fun absent(label: String) {
                val deadline = SystemClock.uptimeMillis() + 3000
                while (find(instrumentation.uiAutomation.rootInActiveWindow, label) != null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
                assertNull(find(instrumentation.uiAutomation.rootInActiveWindow, label))
            }
            node("Aktifkan")
            assertNull(find(instrumentation.uiAutomation.rootInActiveWindow, "Tujuan"))
            assertNull(find(instrumentation.uiAutomation.rootInActiveWindow, "Frekuensi"))
            assertNull(find(instrumentation.uiAutomation.rootInActiveWindow, "Jaringan"))
            click("Cadangan otomatis")
            node("TUJUAN")
            click("Tutup")
            assertFalse(store.automation().enabled)
            assertNull(find(instrumentation.uiAutomation.rootInActiveWindow, "Tujuan"))
            instrumentation.runOnMainSync { store.save(store.automation().copy(enabled = true)) }
            reveal("Tujuan"); click("Tujuan"); click("Tutup")
            assertEquals(com.cashflow.app.data.backup.BackupDestination.DRIVE, store.automation().destination)
            reveal("Frekuensi"); click("Frekuensi"); click("Mingguan")
            assertEquals(com.cashflow.app.data.backup.BackupFrequency.WEEKLY, store.automation().frequency)
            reveal("Jaringan"); click("Jaringan"); click("Wi-Fi dan seluler")
            assertEquals(com.cashflow.app.data.backup.BackupNetwork.ANY, store.automation().network)
            click("Jaringan"); click("Tutup")
            assertEquals(com.cashflow.app.data.backup.BackupNetwork.ANY, store.automation().network)
            reveal("Cadangan otomatis"); click("Cadangan otomatis")
            assertFalse(store.automation().enabled)
            absent("Tujuan")
            absent("Frekuensi")
            absent("Jaringan")
            assertEquals(com.cashflow.app.data.backup.BackupFrequency.WEEKLY, store.automation().frequency)
            assertEquals(com.cashflow.app.data.backup.BackupNetwork.ANY, store.automation().network)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            store.save(original)
            com.cashflow.app.data.service.AutomaticBackupJob.schedule(context, replace = true)
        }
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
