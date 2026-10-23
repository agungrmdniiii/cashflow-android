package com.cashflow.app

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.cashflow.app.data.db.DatabaseHelper
import com.cashflow.app.data.preferences.PreferenceManager
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Separate preferences and in-memory SQLite: no changes to the installed ledger or preferences. */
class CategoryFavoritesStorageTest {
    @Test fun favoriteIdsSurvivePreferenceManagerRecreationWithoutSharingAMutableSet() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "category-favorite-test-${UUID.randomUUID()}-"
        val isolated = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int) =
                context.getSharedPreferences(prefix + name, mode)
        }
        try {
            val manager = PreferenceManager(isolated)
            assertTrue(manager.getFavoriteCategoryIds().isEmpty())
            manager.setFavoriteCategoryIds(setOf("cat_game", "cat_afiliasi"))
            val reopened = PreferenceManager(isolated)
            assertEquals(setOf("cat_game", "cat_afiliasi"), reopened.getFavoriteCategoryIds())
            val copy = reopened.getFavoriteCategoryIds().toMutableSet()
            copy.clear()
            assertEquals(2, manager.getFavoriteCategoryIds().size)
            reopened.setFavoriteCategoryIds(emptySet())
            assertTrue(PreferenceManager(isolated).getFavoriteCategoryIds().isEmpty())
        } finally {
            listOf("duit_aing_preferences", "duit_aing_credentials").forEach {
                context.getSharedPreferences(prefix + it, Context.MODE_PRIVATE).edit().clear().commit()
            }
        }
    }

    @Test fun newCatalogSeedsOnceAndPreservesRenamedOrArchivedCategories() {
        val helper = DatabaseHelper(InstrumentationRegistry.getInstrumentation().targetContext)
        SQLiteDatabase.create(null).use { db ->
            helper.onCreate(db)
            db.execSQL("UPDATE categories SET name = 'Game pribadi', is_active = 0 WHERE id = 'cat_game'")
            db.execSQL("DELETE FROM categories WHERE id = 'cat_rokok'")
            repeat(2) { helper.seedMissingDefaultCategories(db) }
            db.rawQuery("SELECT name, is_active FROM categories WHERE id = 'cat_game'", null).use {
                assertTrue(it.moveToFirst()); assertEquals("Game pribadi", it.getString(0)); assertEquals(0, it.getInt(1))
            }
            db.rawQuery("SELECT COUNT(*) FROM categories", null).use {
                assertTrue(it.moveToFirst()); assertEquals(helper.getDefaultCategoriesList().size, it.getInt(0))
            }
            db.rawQuery("SELECT COUNT(*) FROM categories WHERE id = 'cat_rokok'", null).use {
                assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0))
            }
        }
        helper.close()
    }
}
