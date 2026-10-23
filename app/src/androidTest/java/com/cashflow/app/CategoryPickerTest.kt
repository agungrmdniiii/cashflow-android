package com.cashflow.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.test.platform.app.InstrumentationRegistry
import com.cashflow.app.data.db.DatabaseHelper
import com.cashflow.app.data.model.Category
import com.cashflow.app.ui.screens.AllCategoriesDialog
import com.cashflow.app.ui.theme.CashflowTheme
import org.junit.Assert.*
import org.junit.Test
import java.io.FileInputStream

/** Isolated UI data only: never inserts categories or transactions into the user's database. */
class CategoryPickerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val categories = (1..300).map { Category("test$it", "Kategori $it", "expense", "restaurant") }

    @Test fun openingThePickerDoesNotRecomposeItsParentForm() {
        val visible = mutableStateOf(false)
        var parentCompositions = 0
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, ComponentActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
        instrumentation.runOnMainSync { activity.setContent { CashflowTheme {
            SideEffect { parentCompositions++ }
            AllCategoriesDialog(categories, "test1", onCategorySelected = {}, onDismiss = {}, visible = { visible.value })
        } } }
        try {
            instrumentation.waitForIdleSync()
            var before = 0
            instrumentation.runOnMainSync { before = parentCompositions; visible.value = true }
            node { it.text?.toString() == "Semua Kategori" }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertEquals("Opening a picker must not rebuild the form behind it", before, parentCompositions)
                visible.value = false
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun defaultCatalogIncludesEverydayCategoriesWithShortUniqueNames() {
        val cats = DatabaseHelper(instrumentation.targetContext).getDefaultCategoriesList()
        assertEquals(62, cats.count { it.type == "expense" })
        assertEquals(36, cats.count { it.type == "income" })
        assertTrue(cats.any { it.id == "cat_game" && it.name == "Game" && it.type == "expense" })
        assertEquals(cats.size, cats.map { it.id }.distinct().size)
        assertEquals(cats.size, cats.map { it.type to it.name }.distinct().size)
        assertTrue(cats.all { it.name.length <= 14 })
    }

    @Test fun favoritingDoesNotSelectACategoryOrCloseThePicker() {
        val favorites = mutableStateOf(emptySet<String>())
        var selected: String? = null
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, ComponentActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
        try {
            instrumentation.runOnMainSync { activity.setContent { CashflowTheme {
                AllCategoriesDialog(categories.take(3), "test1", onCategorySelected = { selected = it.id }, onDismiss = {},
                    favoriteCategoryIds = favorites.value, onToggleFavorite = { id ->
                        favorites.value = if (id in favorites.value) favorites.value - id else favorites.value + id
                    })
            } } }
            click(node { it.contentDescription?.toString() == "Jadikan Kategori 2 favorit" })
            node { it.contentDescription?.toString() == "Hapus Kategori 2 dari favorit" }
            assertNull(selected)
            assertEquals(setOf("test2"), favorites.value)
            click(node { it.contentDescription?.toString() == "Hapus Kategori 2 dari favorit" })
            node { it.contentDescription?.toString() == "Jadikan Kategori 2 favorit" }
            assertTrue(favorites.value.isEmpty())
            assertNull(selected)
            click(node { it.text?.toString() == "Kategori 2" && it.className?.toString() == "android.widget.TextView" })
            instrumentation.runOnMainSync { assertEquals("test2", selected) }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun searchFiltersALargeCatalogAndSelectingDoesNotChangeUntilTapped() {
        var selected: String? = null
        val activity = showPicker { selected = it.id }
        try {
            node { it.text?.toString() == "Semua Kategori" }
            val field = node { it.className?.toString() == "android.widget.EditText" }
            field.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Kategori 179")
            }))
            val result = node { it.text?.toString() == "Kategori 179" && it.className?.toString() == "android.widget.TextView" }
            assertNull(selected)
            click(result)
            instrumentation.runOnMainSync { assertEquals("test179", selected) }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun fullCatalogScrollsToLastCategoryWithoutSelectingWhileDragging() {
        var selected: String? = null
        val activity = showPicker { selected = it.id }
        try {
            node { it.text?.toString() == "Semua Kategori" }
            var last: AccessibilityNodeInfo? = null
            for (i in 0..30) {
                last = find(instrumentation.uiAutomation.rootInActiveWindow) { it.text?.toString() == "Kategori 300" }
                if (last != null) break
                node { it.isScrollable }.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                instrumentation.waitForIdleSync()
                SystemClock.sleep(100)
            }
            assertNotNull("Last category must remain reachable", last)
            assertNull("Scrolling must not choose a category", selected)
            click(last!!)
            instrumentation.runOnMainSync { assertEquals("test300", selected) }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun hundredsOfFavoritesAreLoadedLazilyAndScrollDoesNotDelete() {
        val favorites = (1..300).map { com.cashflow.app.data.model.FavoriteTemplate(
            id = "favorite$it", name = "Favorit $it", amount = 1000, assetId = "test-asset"
        ) }
        var deleteRequests = 0
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, ComponentActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
        instrumentation.runOnMainSync { activity.setContent { CashflowTheme {
            com.cashflow.app.ui.screens.settings.FavoritesSettingsSection(
                favoriteTemplates = favorites, isFavoriteDirectSave = false,
                currentAssets = emptyList(), categories = emptyList(), recurringCount = 0,
                onToggleFavoriteDirectSave = {}, onDeleteFavorite = { deleteRequests++ }, onOpenRecurring = {}
            )
        } } }
        try {
            node { it.text?.toString() == "Favorit 1" }
            assertNull("Off-screen favorites must not all be laid out", find(instrumentation.uiAutomation.rootInActiveWindow) {
                it.text?.toString() == "Favorit 300"
            })
            var last: AccessibilityNodeInfo? = null
            for (i in 0..80) {
                last = find(instrumentation.uiAutomation.rootInActiveWindow) { it.text?.toString() == "Favorit 300" }
                if (last != null) break
                node { it.isScrollable }.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                instrumentation.waitForIdleSync()
                SystemClock.sleep(50)
            }
            assertNotNull("Last favorite must remain reachable", last)
            assertEquals("Scrolling must never request deletion", 0, deleteRequests)
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun reversingCategoryScrollKeepsSelectionAndSearchUsable() {
        var selected: String? = null
        val activity = showPicker { selected = it.id }
        try {
            node { it.text?.toString() == "Kategori 1" }
            val bounds = Rect().also { instrumentation.uiAutomation.rootInActiveWindow.getBoundsInScreen(it) }
            fun drag(forward: Boolean) {
                val top = (bounds.height() * .55f).toInt()
                val bottom = (bounds.height() * .85f).toInt()
                val start = if (forward) bottom else top
                val end = if (forward) top else bottom
                instrumentation.uiAutomation.executeShellCommand(
                    "input swipe ${bounds.centerX()} $start ${bounds.centerX()} $end 500"
                ).use { FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() } }
                SystemClock.sleep(250)
            }
            repeat(4) { drag(true) }
            assertNull("Scrolling must not select a category", selected)
            assertNull(find(instrumentation.uiAutomation.rootInActiveWindow) { it.text?.toString() == "Kategori 1" })
            for (attempt in 0 until 8) {
                if (find(instrumentation.uiAutomation.rootInActiveWindow) { it.text?.toString() == "Kategori 1" } != null) break
                drag(false)
            }
            node { it.text?.toString() == "Kategori 1" }
            val field = node { it.className?.toString() == "android.widget.EditText" }
            assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Kategori 300")
            }))
            click(node { it.text?.toString() == "Kategori 300" && it.className?.toString() == "android.widget.TextView" })
            instrumentation.runOnMainSync { assertEquals("test300", selected) }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    private fun showPicker(onSelect: (Category) -> Unit): ComponentActivity {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, ComponentActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
        instrumentation.runOnMainSync { activity.setContent { CashflowTheme {
            AllCategoriesDialog(categories, "test1", onCategorySelected = onSelect, onDismiss = {})
        } } }
        return activity
    }
    private fun find(root: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (root == null) return null
        if (predicate(root)) return root
        for (i in 0 until root.childCount) find(root.getChild(i), predicate)?.let { return it }
        return null
    }
    private fun node(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < deadline) {
            find(instrumentation.uiAutomation.rootInActiveWindow, predicate)?.let { return it }
            SystemClock.sleep(50)
        }
        error("Category control not found")
    }
    private fun click(node: AccessibilityNodeInfo) {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        assertTrue(target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        instrumentation.waitForIdleSync()
    }
}
