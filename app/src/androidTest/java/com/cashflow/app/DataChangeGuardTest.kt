package com.cashflow.app

import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.cashflow.app.data.model.*
import com.cashflow.app.ui.CashflowViewModel
import org.junit.Assert.*
import org.junit.Test

/** Requests are always cancelled: these tests never commit changes to the user's ledger. */
class DataChangeGuardTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val tx = Transaction("confirmation-test", "expense", 1, "Uji", assetId = "none", transactionDate = "2026-10-21")

    @Test fun similarTransactionCanBeReviewedAndCancelledWithoutWriting() {
        withApp { vm ->
            val before = vm.transactions.value.toList()
            val existing = before.firstOrNull { !it.isAdjustment && it.type in listOf("income", "expense", "transfer") }
            org.junit.Assume.assumeNotNull(existing)
            var completed: Boolean? = null
            instrumentation.runOnMainSync { vm.saveTransaction(existing!!.copy(id = "duplicate-review-test", description = "Draft belum disimpan")) { completed = it } }
            val deadline = SystemClock.uptimeMillis() + 5000
            while (vm.dataChangeConfirmation.value == null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(30)
            val reviewDeadline = SystemClock.uptimeMillis() + 5000
            var review: AccessibilityNodeInfo? = null
            while (review == null && SystemClock.uptimeMillis() < reviewDeadline) {
                review = find(instrumentation.uiAutomation.rootInActiveWindow, "Periksa transaksi sebelumnya")
                SystemClock.sleep(30)
            }
            assertNotNull("Review action must be visible", review)
            var button = review
            while (button != null && !button.isClickable) button = button.parent
            assertTrue(button?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
            val expandedDeadline = SystemClock.uptimeMillis() + 5000
            while (find(instrumentation.uiAutomation.rootInActiveWindow, existing!!.description) == null &&
                SystemClock.uptimeMillis() < expandedDeadline) SystemClock.sleep(30)
            assertNotNull(find(instrumentation.uiAutomation.rootInActiveWindow, existing!!.description))
            instrumentation.runOnMainSync { vm.cancelPendingDataChange() }
            assertEquals(false, completed)
            assertEquals(before, vm.transactions.value)
        }
    }

    @Test fun clearingChatStopsPendingAnswerAndResetsLoading() {
        withApp { vm ->
            instrumentation.runOnMainSync {
                vm.sendChatMessage("berapa saldo saya")
                vm.clearChat()
                vm.confirmPendingDataChange()
                assertFalse(vm.isChatLoading.value)
                assertEquals(1, vm.chatMessages.value.size)
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(300)
            instrumentation.runOnMainSync { assertEquals(1, vm.chatMessages.value.size) }
        }
    }

    @Test fun everyDeleteEntryPointWaitsForConfirmation() {
        withApp { vm ->
            val before = vm.transactions.value
            val actions: List<() -> Unit> = listOf(
                { vm.deleteTransaction("confirmation-test") },
                { vm.deleteTransactionWithUndo(tx) },
                { vm.deleteAsset("confirmation-test") },
                { vm.deleteBudget("confirmation-test") },
                { vm.deleteGoal("confirmation-test") },
                { vm.deleteDebt("confirmation-test") },
                { vm.deleteFavoriteTemplate("confirmation-test") },
                { vm.deleteRecurringTransaction("confirmation-test") },
                { vm.clearChat() }
            )
            actions.forEach { action -> instrumentation.runOnMainSync {
                action()
                assertTrue(vm.dataChangeConfirmation.value?.destructive == true)
                vm.cancelPendingDataChange()
                assertNull(vm.dataChangeConfirmation.value)
            } }
            assertEquals(before, vm.transactions.value)
        }
    }

    @Test fun cancellingEditAboveTheFormKeepsTheDraftAndAllowsAnotherSave() {
        withApp { vm ->
            val original = vm.transactions.value.firstOrNull() ?: tx
            var result: Boolean? = null
            instrumentation.runOnMainSync {
                vm.editingTransaction.value = original
                vm.isAddTransactionOpen.value = true
            }
            SystemClock.sleep(800)
            instrumentation.runOnMainSync {
                vm.updateTransaction(original.copy(description = "Draft belum disimpan"), original) { result = it }
                assertNull(result)
            }
            val deadline = SystemClock.uptimeMillis() + 5000
            var cancel: AccessibilityNodeInfo? = null
            while (cancel == null && SystemClock.uptimeMillis() < deadline) {
                cancel = find(instrumentation.uiAutomation.rootInActiveWindow, "Batal")
                SystemClock.sleep(50)
            }
            assertNotNull("Confirmation must appear above the edit sheet", cancel)
            var button = cancel
            while (button != null && !button.isClickable) button = button.parent
            assertTrue(button?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertEquals(false, result)
                assertNull(vm.dataChangeConfirmation.value)
                assertEquals(original, vm.editingTransaction.value)
                assertTrue(vm.isAddTransactionOpen.value)
                vm.updateTransaction(original) { result = it }
                assertNotNull(vm.dataChangeConfirmation.value)
                vm.cancelPendingDataChange()
                vm.isAddTransactionOpen.value = false
                vm.editingTransaction.value = null
            }
        }
    }

    @Test fun otherRecordEditsAlsoWaitBeforeWriting() {
        withApp { vm ->
            val name = vm.userName.value
            val actions: List<() -> Unit> = listOf(
                { vm.saveAsset(Asset("confirmation-test", "Uji", "Tunai", 0, 0), false) },
                { vm.updateBudget(Budget("confirmation-test", "none", targetAmount = 1)) },
                { vm.saveGoal(FinancialGoal("confirmation-test", "Uji", 1, 0, "2027-01-01"), false) },
                { vm.setUserName("Draft belum disimpan") },
                { vm.setDefaultAsset("confirmation-test") },
                { vm.skipUpcomingBill("confirmation-test", "2026-10-21") },
                { vm.recalculateBalances() },
                { vm.repairBalanceDiscrepancies() }
            )
            actions.forEach { action -> instrumentation.runOnMainSync {
                action()
                assertNotNull(vm.dataChangeConfirmation.value)
                vm.cancelPendingDataChange()
            } }
            assertEquals(name, vm.userName.value)
        }
    }

    @Test fun refreshingReloadsRecordsWithoutRepairingOrEditingThem() {
        withApp { vm ->
            val transactions = vm.transactions.value
            val assets = vm.assets.value
            kotlinx.coroutines.runBlocking { vm.refreshData() }
            assertEquals(transactions, vm.transactions.value)
            assertEquals(assets, vm.assets.value)
            assertNull(vm.dataChangeConfirmation.value)
        }
    }

    @Test fun assetActionsStayBelowTheForm() {
        withApp { vm ->
            val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, ComponentActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
            instrumentation.runOnMainSync { activity.setContent { com.cashflow.app.ui.theme.CashflowTheme {
                Box(Modifier.fillMaxSize()) {
                    com.cashflow.app.ui.screens.AddAssetContent(vm, onDismiss = {}, modifier = Modifier.fillMaxSize())
                }
            } } }
            try {
                val deadline = SystemClock.uptimeMillis() + 5000
                while (find(instrumentation.uiAutomation.rootInActiveWindow, "NAMA ASET") == null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
                val root = instrumentation.uiAutomation.rootInActiveWindow
                val name = find(root, "NAMA ASET")
                val save = find(root, "SIMPAN ASET")
                assertNotNull(name)
                assertNotNull(save)
                val nameBounds = android.graphics.Rect().also { name!!.getBoundsInScreen(it) }
                val saveBounds = android.graphics.Rect().also { save!!.getBoundsInScreen(it) }
                assertTrue("Asset actions must stay below the form instead of overlapping its header", saveBounds.top > nameBounds.bottom)
            } finally { instrumentation.runOnMainSync { activity.finish() } }
        }
    }

    private fun withApp(test: (CashflowViewModel) -> Unit) {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        lateinit var vm: CashflowViewModel
        instrumentation.runOnMainSync { vm = ViewModelProvider(activity)[CashflowViewModel::class.java] }
        try {
            val deadline = SystemClock.uptimeMillis() + 10000
            while (!vm.initialLoadComplete.value && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
            assertTrue(vm.initialLoadComplete.value)
            test(vm)
        } finally { instrumentation.runOnMainSync { vm.cancelPendingDataChange(); activity.finish() } }
    }

    private fun find(root: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.text?.toString() == text) return root
        for (i in 0 until root.childCount) find(root.getChild(i), text)?.let { return it }
        return null
    }
}
