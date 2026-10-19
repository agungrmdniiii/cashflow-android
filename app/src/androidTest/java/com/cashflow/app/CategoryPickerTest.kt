package com.cashflow.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.platform.app.InstrumentationRegistry
import com.cashflow.app.data.db.DatabaseHelper
import com.cashflow.app.data.model.Category
import com.cashflow.app.ui.screens.AllCategoriesDialog
import com.cashflow.app.ui.theme.CashflowTheme
import org.junit.Assert.*
import org.junit.Test

/** Isolated UI data only: never inserts categories or transactions into the user's database. */
class CategoryPickerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val categories = (1..300).map { Category("test$it", "Kategori $it", "expense", "restaurant") }

    @Test fun defaultCatalogHasFiftyExpensesThirtyIncomeAndShortUniqueNames() {
        val cats = DatabaseHelper(instrumentation.targetContext).getDefaultCategoriesList()
        assertEquals(50, cats.count { it.type == "expense" })
        assertEquals(30, cats.count { it.type == "income" })
        assertEquals(cats.size, cats.map { it.id }.distinct().size)
        assertEquals(cats.size, cats.map { it.type to it.name }.distinct().size)
        assertTrue(cats.all { it.name.length <= 14 })
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
