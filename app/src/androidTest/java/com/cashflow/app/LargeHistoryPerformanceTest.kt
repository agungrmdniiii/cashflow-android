package com.cashflow.app

import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.cashflow.app.data.db.DatabaseHelper
import com.cashflow.app.data.model.calculateBudgetProgress
import com.cashflow.app.data.preferences.PreferenceManager
import com.cashflow.app.data.repository.CashflowRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Read-only measurement; provision synthetic history on the audit emulator before running. */
class LargeHistoryPerformanceTest {
    @Test fun timeInitialSnapshotAndBudgetCalculation() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val expected = DatabaseHelper(context).use { helper ->
            helper.readableDatabase.rawQuery("SELECT COUNT(*), COALESCE(SUM(CASE WHEN type='expense' THEN amount ELSE 0 END),0) FROM transactions", null).use {
                it.moveToFirst(); it.getInt(0) to it.getLong(1)
            }
        }
        val count = expected.first
        assumeTrue("Requires a provisioned large synthetic history", count >= 10_000)
        val started = SystemClock.elapsedRealtime()
        val repository = CashflowRepository(context)
        withTimeout(60_000) { repository.transactions.first { it.size == count } }
        val rowsMs = SystemClock.elapsedRealtime() - started
        withTimeout(60_000) { repository.awaitInitialLoad() }
        val readyMs = SystemClock.elapsedRealtime() - started
        assertEquals(count, repository.transactions.value.size)
        assertEquals(expected.second, repository.transactions.value.filter { it.type == "expense" }.sumOf { it.amount })
        val prefs = PreferenceManager(context)
        val budgetStarted = SystemClock.elapsedRealtime()
        calculateBudgetProgress(repository.budgets.value.map { it.budget }, repository.categories.value,
            repository.transactions.value, prefs.isBudgetRolloverEnabled(), periodStartDay = prefs.getBudgetStartDay())
        val budgetMs = SystemClock.elapsedRealtime() - budgetStarted
        instrumentation.sendStatus(0, Bundle().apply {
            putString("stream", "\nPERF transactions=$count rows_ms=$rowsMs ready_ms=$readyMs budget_ms=$budgetMs\n")
        })
    }
}
