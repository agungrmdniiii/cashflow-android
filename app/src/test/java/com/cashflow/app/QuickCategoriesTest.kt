package com.cashflow.app

import com.cashflow.app.data.model.*
import org.junit.Assert.*
import org.junit.Test

class QuickCategoriesTest {
    private val categories = (1..12).map { Category("c$it", "Category $it", "expense", "category", "#111111") }
    @Test fun tappingVisibleCategoryDoesNotReorderTiles() {
        assertEquals(categories.take(7), quickCategories(categories, "c4"))
    }
    @Test fun categoryChosenFromFullListReplacesOnlyLastQuickSlot() {
        assertEquals(categories.take(6) + categories[10], quickCategories(categories, "c11"))
    }

    private fun tx(id: String, category: String, date: String = "2026-10-19", type: String = "expense") =
        Transaction(id, type, 10_000, id, category, "cash", transactionDate = date)

    @Test fun frequentCategoriesRiseAboveAlphabeticalOrderAndLargeAmountsDoNotMatter() {
        val rows = listOf(tx("1", "c11"), tx("2", "c11"), tx("3", "c3").copy(amount = 9_000_000))
        val ranked = rankCategoriesByUsage(categories, rows)
        assertEquals(listOf("c11", "c3"), ranked.take(2).map { it.id })
        assertEquals("c11", quickCategories(ranked, "c1").first().id)
    }
    @Test fun equalCountsUseMostRecentDateThenKeepOriginalOrder() {
        val rows = listOf(tx("a", "c2", "2026-10-17"), tx("b", "c9", "2026-10-19"))
        assertEquals(listOf("c9", "c2", "c1", "c3"), rankCategoriesByUsage(categories, rows).take(4).map { it.id })
        assertEquals(categories, rankCategoriesByUsage(categories, emptyList()))
    }
    @Test fun rankingIgnoresCorrectionsTransfersMismatchedTypesAndInactiveCategories() {
        val cats = categories + Category("salary", "Gaji", "income") + Category("hidden", "Arsip", "expense", isActive = false)
        val rows = listOf(tx("a", "c10"), tx("b", "c1").copy(isAdjustment = true), tx("c", "c1", type = "transfer"),
            tx("d", "c1", type = "income"), tx("e", "salary", type = "income"), tx("f", "hidden"))
        val ranked = rankCategoriesByUsage(cats, rows)
        assertEquals(listOf("c10", "salary"), ranked.take(2).map { it.id })
        assertFalse(ranked.any { it.id == "hidden" })
        assertEquals(listOf("c10", "salary"), rankCategoriesByUsage(cats, rows.toList()).take(2).map { it.id })
    }

    @Test fun favoriteCategoriesComeFirstAndUnfavoritingRestoresUsageOrder() {
        val rows = listOf(tx("a", "c11"), tx("b", "c11"), tx("c", "c3"))
        val favorites = setOf("c9", "c3", "missing")
        val ranked = rankCategoriesByUsage(categories, rows, favorites)
        assertEquals(listOf("c3", "c9", "c11"), ranked.take(3).map { it.id })
        assertEquals(ranked.size, ranked.map { it.id }.distinct().size)
        assertEquals(listOf("c11", "c3"), rankCategoriesByUsage(categories, rows, emptySet()).take(2).map { it.id })
        assertFalse(rankCategoriesByUsage(categories + Category("hidden", "Arsip", "expense", isActive = false), rows,
            setOf("hidden")).any { it.id == "hidden" })
    }
}
