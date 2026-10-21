package com.cashflow.app.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.cashflow.app.data.model.Asset
import com.cashflow.app.data.model.Budget
import com.cashflow.app.data.model.Category
import com.cashflow.app.data.model.Debt
import com.cashflow.app.data.model.DebtStatus
import com.cashflow.app.data.model.FinancialGoal
import com.cashflow.app.data.model.Transaction
import com.cashflow.app.data.model.TransactionType
import com.cashflow.app.data.repository.LedgerService
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    private val appContext: Context = context.applicationContext

    companion object {
        const val DATABASE_NAME = "cashflow.db"
        const val DATABASE_VERSION = 12

        const val TABLE_ASSETS = "assets"
        const val TABLE_CATEGORIES = "categories"
        const val TABLE_TRANSACTIONS = "transactions"
        const val TABLE_BUDGETS = "budgets"
        const val TABLE_GOALS = "financial_goals"
        const val TABLE_DEBTS = "debts"
        const val TABLE_FAVORITES = "favorite_templates"
        const val TABLE_RECURRING = "recurring_transactions"
        const val TABLE_RECURRING_EXECUTIONS = "recurring_executions"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        seedMissingDefaultCategories(db)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_ASSETS (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                opening_balance INTEGER NOT NULL,
                current_balance INTEGER NOT NULL,
                currency TEXT NOT NULL DEFAULT 'IDR',
                is_default INTEGER NOT NULL DEFAULT 0,
                is_active INTEGER NOT NULL DEFAULT 1,
                is_sample INTEGER NOT NULL DEFAULT 0,
                note TEXT DEFAULT '',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_CATEGORIES (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                icon TEXT NOT NULL DEFAULT 'category',
                color TEXT NOT NULL DEFAULT '#111111',
                is_default INTEGER NOT NULL DEFAULT 0,
                is_active INTEGER NOT NULL DEFAULT 1
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_TRANSACTIONS (
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL,
                amount INTEGER NOT NULL,
                description TEXT NOT NULL,
                category_id TEXT,
                asset_id TEXT NOT NULL,
                destination_asset_id TEXT,
                transaction_date TEXT NOT NULL,
                transaction_time TEXT NOT NULL,
                note TEXT DEFAULT '',
                attachment TEXT,
                input_method TEXT NOT NULL DEFAULT 'manual',
                debt_id TEXT,
                goal_id TEXT,
                is_sample INTEGER NOT NULL DEFAULT 0,
                tags TEXT NOT NULL DEFAULT '',
                is_adjustment INTEGER NOT NULL DEFAULT 0,
                adjustment_direction INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_BUDGETS (
                id TEXT PRIMARY KEY,
                category_id TEXT NOT NULL,
                period_type TEXT NOT NULL DEFAULT 'monthly',
                target_amount INTEGER NOT NULL,
                created_at INTEGER NOT NULL
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_GOALS (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                target_amount INTEGER NOT NULL,
                current_amount INTEGER NOT NULL DEFAULT 0,
                deadline TEXT NOT NULL,
                asset_id TEXT,
                note TEXT DEFAULT '',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );
            """.trimIndent()
        )

        createDebtsTable(db)
        createFavoritesTable(db)
        createRecurringTables(db)
        createIndexes(db)
        seedInitialData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        backupBeforeMigration(oldVersion)
        if (oldVersion < 2) {
            createDebtsTable(db)
        }
        if (oldVersion < 3) {
            createIndexes(db)
        }
        if (oldVersion < 4) {
            seedMissingDefaultCategories(db)
        }
        if (oldVersion < 5) {
            try { db.execSQL("ALTER TABLE $TABLE_TRANSACTIONS ADD COLUMN debt_id TEXT;") } catch (_: Exception) {}
            try { db.execSQL("ALTER TABLE $TABLE_TRANSACTIONS ADD COLUMN goal_id TEXT;") } catch (_: Exception) {}
            try { db.execSQL("ALTER TABLE $TABLE_TRANSACTIONS ADD COLUMN is_sample INTEGER NOT NULL DEFAULT 0;") } catch (_: Exception) {}
            try { db.execSQL("ALTER TABLE $TABLE_ASSETS ADD COLUMN is_sample INTEGER NOT NULL DEFAULT 0;") } catch (_: Exception) {}
        }
        if (oldVersion < 6) {
            createFavoritesTable(db)
        }
        if (oldVersion < 7) {
            createRecurringTables(db)
            try { db.execSQL("ALTER TABLE $TABLE_TRANSACTIONS ADD COLUMN tags TEXT NOT NULL DEFAULT '';") } catch (_: Exception) {}
            try { db.execSQL("ALTER TABLE $TABLE_TRANSACTIONS ADD COLUMN is_adjustment INTEGER NOT NULL DEFAULT 0;") } catch (_: Exception) {}
        }
        if (oldVersion < 8) {
            createIndexes(db)
        }
        if (oldVersion < 9) {
            try {
                db.execSQL("ALTER TABLE $TABLE_TRANSACTIONS ADD COLUMN adjustment_direction INTEGER NOT NULL DEFAULT 0;")
                db.execSQL("UPDATE $TABLE_TRANSACTIONS SET adjustment_direction = -1 WHERE (type = '${TransactionType.ADJUSTMENT.value}' OR is_adjustment = 1) AND note LIKE '%(-)%';")
                db.execSQL("UPDATE $TABLE_TRANSACTIONS SET adjustment_direction = 1 WHERE (type = '${TransactionType.ADJUSTMENT.value}' OR is_adjustment = 1) AND (note NOT LIKE '%(-)%' OR note IS NULL);")
            } catch (_: Exception) {}
        }
        if (oldVersion < 10) {
            LedgerService.migrateLegacyGoalTransactions(db)
        }
        if (oldVersion < 11) {
            db.execSQL("DELETE FROM $TABLE_RECURRING_EXECUTIONS WHERE rowid NOT IN (SELECT MIN(rowid) FROM $TABLE_RECURRING_EXECUTIONS GROUP BY recurring_id, scheduled_date)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_recurring_occurrence_unique ON $TABLE_RECURRING_EXECUTIONS (recurring_id, scheduled_date)")
        }
        if (oldVersion < 12) {
            db.execSQL("ALTER TABLE $TABLE_DEBTS ADD COLUMN direction TEXT NOT NULL DEFAULT 'debt'")
            LedgerService.migrateLegacyDebtTransactions(db)
        }
    }

    private fun backupBeforeMigration(oldVersion: Int) {
        try {
            val dbFile = appContext.getDatabasePath(DATABASE_NAME)
            if (dbFile != null && dbFile.exists() && dbFile.length() > 0) {
                val backupFile = java.io.File(dbFile.parentFile, "$DATABASE_NAME.bak_v$oldVersion")
                dbFile.copyTo(backupFile, overwrite = true)
            }
        } catch (_: Exception) {
            // Ignore in restricted environments
        }
    }

    fun createIndexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_date ON $TABLE_TRANSACTIONS (transaction_date DESC);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_asset ON $TABLE_TRANSACTIONS (asset_id);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_category ON $TABLE_TRANSACTIONS (category_id);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_budgets_category ON $TABLE_BUDGETS (category_id);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_debts_status ON $TABLE_DEBTS (status);")
        // High-performance composite indexes for 10.000+ records
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_date_type ON $TABLE_TRANSACTIONS (transaction_date DESC, type);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_category_date ON $TABLE_TRANSACTIONS (category_id, transaction_date DESC);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_asset_date ON $TABLE_TRANSACTIONS (asset_id, transaction_date DESC);")
    }

    fun createDebtsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_DEBTS (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                lender_name TEXT NOT NULL,
                direction TEXT NOT NULL DEFAULT 'debt',
                total_amount INTEGER NOT NULL,
                paid_amount INTEGER NOT NULL DEFAULT 0,
                due_date TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'unpaid',
                note TEXT DEFAULT '',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );
            """.trimIndent()
        )
    }

    fun createFavoritesTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_FAVORITES (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                amount INTEGER NOT NULL,
                category_id TEXT,
                asset_id TEXT NOT NULL,
                destination_asset_id TEXT,
                note TEXT DEFAULT '',
                created_at INTEGER NOT NULL
            );
            """.trimIndent()
        )
    }

    fun createRecurringTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_RECURRING (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                amount INTEGER NOT NULL,
                category_id TEXT,
                asset_id TEXT NOT NULL,
                destination_asset_id TEXT,
                frequency TEXT NOT NULL,
                interval INTEGER NOT NULL DEFAULT 1,
                start_date TEXT NOT NULL,
                end_date TEXT,
                total_occurrences INTEGER,
                occurrences_done INTEGER NOT NULL DEFAULT 0,
                mode TEXT NOT NULL DEFAULT 'auto',
                linked_debt_id TEXT,
                last_executed_date TEXT,
                is_active INTEGER NOT NULL DEFAULT 1,
                note TEXT DEFAULT '',
                created_at INTEGER NOT NULL
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_RECURRING_EXECUTIONS (
                id TEXT PRIMARY KEY,
                recurring_id TEXT NOT NULL,
                scheduled_date TEXT NOT NULL,
                transaction_id TEXT,
                executed_at INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'completed',
                FOREIGN KEY (recurring_id) REFERENCES $TABLE_RECURRING(id) ON DELETE CASCADE
            );
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_recurring_executions ON $TABLE_RECURRING_EXECUTIONS (recurring_id, scheduled_date);")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_recurring_occurrence_unique ON $TABLE_RECURRING_EXECUTIONS (recurring_id, scheduled_date);")
    }

    fun getDefaultCategoriesList(): List<Category> = listOf(
        // Expense categories: broad groups and optional everyday detail.
        Category("cat_makanan", "Makanan & Minuman", "expense", "restaurant", "#8D3B3B", isDefault = true),
        Category("cat_transportasi", "Transportasi & Ojol", "expense", "directions_car", "#B57C1E", isDefault = true),
        Category("cat_tagihan", "Tagihan & Utilitas", "expense", "receipt_long", "#2E6F89", isDefault = true),
        Category("cat_belanja", "Belanja & Pasar", "expense", "shopping_bag", "#2D5D48", isDefault = true),
        Category("cat_hiburan", "Hiburan & Hobi", "expense", "movie", "#6B3E7A", isDefault = true),
        Category("cat_kesehatan", "Kesehatan & Obat", "expense", "medical_services", "#1E7063", isDefault = true),
        Category("cat_pendidikan", "Pendidikan & Buku", "expense", "school", "#3B487A", isDefault = true),
        Category("cat_rumah", "Kebutuhan Rumah", "expense", "home", "#704E38", isDefault = true),
        Category("cat_keluarga", "Keluarga & Anak", "expense", "family_restroom", "#9E4B3E", isDefault = true),
        Category("cat_perawatan", "Perawatan Diri", "expense", "spa", "#8F5845", isDefault = true),
        Category("cat_olahraga", "Olahraga & Gym", "expense", "fitness_center", "#386641", isDefault = true),
        Category("cat_peliharaan", "Peliharaan (Anabul)", "expense", "pets", "#8C5835", isDefault = true),
        Category("cat_donasi", "Zakat & Sedekah", "expense", "volunteer_activism", "#13503C", isDefault = true),
        Category("cat_cicilan", "Cicilan & Asuransi", "expense", "credit_card", "#7A2E35", isDefault = true),
        Category("cat_investasi_exp", "Investasi & Emas", "expense", "savings", "#1C5A46", isDefault = true),
        Category("cat_lainnya_exp", "Lainnya", "expense", "more_horiz", "#4A5550", isDefault = true),
        Category("cat_kopi_jajan", "Kopi & Jajan", "expense", "restaurant", "#8D3B3B", isDefault = true),
        Category("cat_bahan_makanan", "Bahan Makanan", "expense", "shopping_bag", "#2D5D48", isDefault = true),
        Category("cat_bensin", "Bensin & BBM", "expense", "directions_car", "#B57C1E", isDefault = true),
        Category("cat_parkir_tol", "Parkir & Tol", "expense", "directions_car", "#B57C1E", isDefault = true),
        Category("cat_servis_kendaraan", "Servis Kendaraan", "expense", "directions_car", "#B57C1E", isDefault = true),
        Category("cat_pulsa_data", "Pulsa & Paket Data", "expense", "receipt_long", "#2E6F89", isDefault = true),
        Category("cat_listrik", "Listrik", "expense", "receipt_long", "#2E6F89", isDefault = true),
        Category("cat_air_pdam", "Air & PDAM", "expense", "receipt_long", "#2E6F89", isDefault = true),
        Category("cat_internet_rumah", "Internet Rumah", "expense", "receipt_long", "#2E6F89", isDefault = true),
        Category("cat_sewa_kos", "Sewa & Kos", "expense", "home", "#704E38", isDefault = true),
        Category("cat_perbaikan_rumah", "Perbaikan Rumah", "expense", "home", "#704E38", isDefault = true),
        Category("cat_obat_vitamin", "Obat & Vitamin", "expense", "medical_services", "#1E7063", isDefault = true),
        Category("cat_liburan", "Liburan & Wisata", "expense", "movie", "#6B3E7A", isDefault = true),
        Category("cat_langganan_digital", "Langganan Digital", "expense", "movie", "#6B3E7A", isDefault = true),
        Category("cat_biaya_bank", "Biaya Admin Bank", "expense", "credit_card", "#7A2E35", isDefault = true),
        Category("cat_pajak", "Pajak & Retribusi", "expense", "receipt_long", "#7A2E35", isDefault = true),
        Category("cat_perlengkapan_anak", "Perlengkapan Anak", "expense", "family_restroom", "#9E4B3E", isDefault = true),
        Category("cat_acara", "Pernikahan & Acara", "expense", "celebration", "#9E4B3E", isDefault = true),
        Category("cat_laundry", "Laundry", "expense", "spa", "#8F5845", isDefault = true),
        Category("cat_perlengkapan_kerja", "Perlengkapan Kerja", "expense", "laptop", "#1A586E", isDefault = true),

        Category("cat_pakaian", "Pakaian", "expense", "shopping_bag", "#2D5D48", isDefault = true),
        Category("cat_sepatu", "Sepatu", "expense", "shopping_bag", "#2D5D48", isDefault = true),
        Category("cat_skincare", "Skincare", "expense", "spa", "#2D5D48", isDefault = true),
        Category("cat_potong_rambut", "Potong Rambut", "expense", "face", "#2D5D48", isDefault = true),
        Category("cat_elektronik", "Elektronik", "expense", "laptop", "#2D5D48", isDefault = true),
        Category("cat_gadget", "Gadget", "expense", "laptop", "#2D5D48", isDefault = true),
        Category("cat_perabot", "Perabot", "expense", "home", "#2D5D48", isDefault = true),
        Category("cat_keamanan", "Keamanan", "expense", "home", "#2D5D48", isDefault = true),
        Category("cat_angkutan_umum", "Bus/Kereta", "expense", "directions_bus", "#2D5D48", isDefault = true),
        Category("cat_kursus", "Les/Kursus", "expense", "school", "#2D5D48", isDefault = true),
        Category("cat_atk", "ATK", "expense", "school", "#2D5D48", isDefault = true),
        Category("cat_kado", "Kado", "expense", "redeem", "#2D5D48", isDefault = true),
        Category("cat_asisten_rumah", "Asisten Rumah", "expense", "home", "#2D5D48", isDefault = true),
        Category("cat_kurir", "Paket/Kurir", "expense", "directions_car", "#2D5D48", isDefault = true),

        // Income categories.
        Category("cat_gaji", "Gaji Pokok", "income", "payments", "#0A7559", isDefault = true),
        Category("cat_freelance", "Freelance & Jasa", "income", "laptop", "#1A586E", isDefault = true),
        Category("cat_bonus", "Bonus & THR", "income", "celebration", "#1D5A85", isDefault = true),
        Category("cat_penjualan", "Hasil Usaha & Toko", "income", "storefront", "#945219", isDefault = true),
        Category("cat_investasi_inc", "Dividen & Investasi", "income", "trending_up", "#165B33", isDefault = true),
        Category("cat_cashback", "Cashback & Reward", "income", "currency_exchange", "#8F6B23", isDefault = true),
        Category("cat_hadiah", "Hadiah & Uang Saku", "income", "redeem", "#5E2B7A", isDefault = true),
        Category("cat_sewa", "Sewa Properti / Aset", "income", "home_work", "#2E5339", isDefault = true),
        Category("cat_lainnya_inc", "Lainnya", "income", "attach_money", "#4A5550", isDefault = true),
        Category("cat_komisi", "Komisi Penjualan", "income", "storefront", "#945219", isDefault = true),
        Category("cat_lembur", "Upah Lembur", "income", "payments", "#0A7559", isDefault = true),
        Category("cat_insentif", "Insentif Kerja", "income", "celebration", "#1D5A85", isDefault = true),
        Category("cat_royalti", "Royalti", "income", "laptop", "#1A586E", isDefault = true),
        Category("cat_bunga_tabungan", "Bunga Tabungan", "income", "trending_up", "#165B33", isDefault = true),
        Category("cat_pensiun", "Dana Pensiun", "income", "payments", "#0A7559", isDefault = true),
        Category("cat_hasil_panen", "Hasil Panen", "income", "storefront", "#165B33", isDefault = true),
        Category("cat_barang_bekas", "Jual Barang Bekas", "income", "storefront", "#945219", isDefault = true),
        Category("cat_refund", "Pengembalian Dana", "income", "currency_exchange", "#8F6B23", isDefault = true),
        Category("cat_bantuan", "Bantuan & Subsidi", "income", "redeem", "#5E2B7A", isDefault = true),
        Category("cat_proyek_sampingan", "Proyek Sampingan", "income", "laptop", "#1A586E", isDefault = true),
        Category("cat_upah_harian", "Upah Harian", "income", "payments", "#0A7559", isDefault = true),
        Category("cat_honor", "Honor", "income", "payments", "#0A7559", isDefault = true),
        Category("cat_bisnis_online", "Bisnis Online", "income", "storefront", "#0A7559", isDefault = true),
        Category("cat_tip", "Tip", "income", "payments", "#0A7559", isDefault = true),
        Category("cat_beasiswa", "Beasiswa", "income", "school", "#0A7559", isDefault = true),
        Category("cat_pesangon", "Pesangon", "income", "payments", "#0A7559", isDefault = true),
        Category("cat_uang_saku", "Uang Saku", "income", "redeem", "#0A7559", isDefault = true),
        Category("cat_sewa_kendaraan", "Sewa Kend.", "income", "directions_car", "#0A7559", isDefault = true),
        Category("cat_jasa_desain", "Jasa Desain", "income", "laptop", "#0A7559", isDefault = true),
        Category("cat_jasa_mengajar", "Jasa Mengajar", "income", "school", "#0A7559", isDefault = true)
    ).map { it.copy(name = com.cashflow.app.data.model.shortDefaultCategoryLabels[it.name] ?: it.name) }

    fun seedMissingDefaultCategories(db: SQLiteDatabase) {
        val defaultCategories = getDefaultCategoriesList()
        val existing = mutableMapOf<String, Pair<String, Boolean>>()
        db.query(TABLE_CATEGORIES, arrayOf("id", "name", "is_default"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) existing[cursor.getString(0)] = cursor.getString(1) to (cursor.getInt(2) == 1)
        }
        val missing = defaultCategories.filter { it.id !in existing }
        val legacy = defaultCategories.filter { cat ->
            val stored = existing[cat.id]
            stored?.second == true && stored.first != cat.name &&
                com.cashflow.app.data.model.shortDefaultCategoryLabels[stored.first] == cat.name
        }
        if (missing.isEmpty() && legacy.isEmpty()) return

        val ownsTransaction = !db.inTransaction()
        if (ownsTransaction) db.beginTransaction()
        try {
            for (cat in missing) {
                val cv = ContentValues().apply {
                    put("id", cat.id)
                    put("name", cat.name)
                    put("type", cat.type)
                    put("icon", cat.icon)
                    put("color", cat.color)
                    put("is_default", if (cat.isDefault) 1 else 0)
                    put("is_active", if (cat.isActive) 1 else 0)
                }
                db.insertWithOnConflict(TABLE_CATEGORIES, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
            }
            for (cat in legacy) {
                db.execSQL("UPDATE $TABLE_CATEGORIES SET name = ? WHERE id = ? AND is_default = 1 AND name = ?",
                    arrayOf(cat.name, cat.id, existing.getValue(cat.id).first))
            }
            if (ownsTransaction) db.setTransactionSuccessful()
        } finally {
            if (ownsTransaction) db.endTransaction()
        }
    }

    fun seedInitialData(db: SQLiteDatabase) {
        // Seed Default Categories (Essential for categorization)
        seedMissingDefaultCategories(db)
    }

    /**
     * Menghapus hanya data yang bertanda sample/contoh (is_sample = 1).
     * Data pribadi pengguna yang dicatat manual tidak akan terhapus.
     */
    fun deleteSampleData(db: SQLiteDatabase) {
        db.execSQL("DELETE FROM $TABLE_TRANSACTIONS WHERE is_sample = 1")
        db.execSQL("DELETE FROM $TABLE_ASSETS WHERE is_sample = 1")
    }

    /**
     * Menghapus seluruh data transaksi, aset, anggaran, target, dan hutang (Hapus Semua Data).
     * Kategori bawaan tetap dipertahankan.
     */
    fun deleteAllUserData(db: SQLiteDatabase) {
        db.beginTransaction()
        try {
        db.execSQL("DELETE FROM $TABLE_RECURRING_EXECUTIONS")
        db.execSQL("DELETE FROM $TABLE_RECURRING")
        db.execSQL("DELETE FROM $TABLE_TRANSACTIONS")
        db.execSQL("DELETE FROM $TABLE_BUDGETS")
        db.execSQL("DELETE FROM $TABLE_GOALS")
        db.execSQL("DELETE FROM $TABLE_DEBTS")
        db.execSQL("DELETE FROM $TABLE_ASSETS")
        db.execSQL("DELETE FROM $TABLE_FAVORITES")
        db.execSQL("DELETE FROM $TABLE_CATEGORIES")
        seedMissingDefaultCategories(db)
        db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

}
