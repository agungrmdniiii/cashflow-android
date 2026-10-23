package com.cashflow.app

import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.DateFilterPreset
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test

/** Emulator only: ViewModel startup also runs the existing recurring scheduler. */
class TransactionSearchTest {
    @Test fun searchWordsCanMatchDifferentFieldsOfTheSameTransaction() = runBlocking {
        org.junit.Assume.assumeTrue(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as CashflowApplication
        app.repository.awaitInitialLoad()
        val before = app.repository.transactions.value.toList()
        val assets = app.repository.assets.value.associateBy { it.id }
        val row = before.first { it.description.isNotBlank() && it.categoryId != null && assets[it.assetId] != null }
        val query = "${row.description} ${assets.getValue(row.assetId).name}"
        val store = ViewModelStore()
        lateinit var model: CashflowViewModel
        instrumentation.runOnMainSync {
            model = CashflowViewModel(app)
            store.put("search", model)
        }
        try {
            model.searchQuery.value = query
            val found = withTimeoutOrNull(4_000) {
                model.filteredTransactionsTab.first { rows -> rows.any { it.id == row.id } }
            }
            assertNotNull("Description and account words should match across fields: $query", found)
            model.filterType.value = row.type
            model.filterCategoryId.value = row.categoryId
            model.filterAssetId.value = row.assetId
            model.filterDatePreset.value = DateFilterPreset.CUSTOM
            model.filterStartDate.value = LocalDate.parse(row.transactionDate)
            model.filterEndDate.value = LocalDate.parse(row.transactionDate)
            model.filterMinAmount.value = row.amount
            model.filterMaxAmount.value = row.amount
            val expected = before.filter {
                it.type == row.type && it.categoryId == row.categoryId &&
                    (it.assetId == row.assetId || it.destinationAssetId == row.assetId) &&
                    it.transactionDate == row.transactionDate && it.amount == row.amount &&
                    it.description == row.description
            }
            assertNotNull("All five filter groups must combine, with inclusive date and amount boundaries",
                withTimeoutOrNull(4_000) {
                    model.filteredTransactionsTab.first { rows -> rows.map { it.id } == expected.map { it.id } }
                })
            assertNotNull(withTimeoutOrNull(4_000) { model.activeFilterCount.first { it == 5 } })
            model.resetFilters()
            assertNotNull("Reset should restore the complete history", withTimeoutOrNull(4_000) {
                model.filteredTransactionsTab.first { it == before }
            })
            assertEquals("", model.searchQuery.value)
            assertEquals(before, app.repository.transactions.value)
        } finally { instrumentation.runOnMainSync { store.clear() } }
    }
}
