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
}
