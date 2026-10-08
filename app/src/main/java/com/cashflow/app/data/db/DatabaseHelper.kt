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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "cashflow.db"
        const val DATABASE_VERSION = 3

        const val TABLE_ASSETS = "assets"
        const val TABLE_CATEGORIES = "categories"
        const val TABLE_TRANSACTIONS = "transactions"
        const val TABLE_BUDGETS = "budgets"
        const val TABLE_GOALS = "financial_goals"
        const val TABLE_DEBTS = "debts"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
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
                color TEXT NOT NULL DEFAULT '#0F6B56',
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
        createIndexes(db)
        seedInitialData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createDebtsTable(db)
        }
        if (oldVersion < 3) {
            createIndexes(db)
        }
    }

    fun createIndexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_date ON $TABLE_TRANSACTIONS (transaction_date DESC);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_asset ON $TABLE_TRANSACTIONS (asset_id);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_category ON $TABLE_TRANSACTIONS (category_id);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_budgets_category ON $TABLE_BUDGETS (category_id);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_debts_status ON $TABLE_DEBTS (status);")
    }

    fun createDebtsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_DEBTS (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                lender_name TEXT NOT NULL,
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

    private fun seedInitialData(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val yesterdayStr = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)

        // Seed Categories
        val defaultCategories = listOf(
            // Expense Categories
            Category("cat_makanan", "Makanan", "expense", "restaurant", "#E57373", isDefault = true),
            Category("cat_transportasi", "Transportasi", "expense", "directions_car", "#64B5F6", isDefault = true),
            Category("cat_tagihan", "Tagihan", "expense", "receipt_long", "#FFB74D", isDefault = true),
            Category("cat_belanja", "Belanja", "expense", "shopping_bag", "#81C784", isDefault = true),
            Category("cat_hiburan", "Hiburan", "expense", "movie", "#BA68C8", isDefault = true),
            Category("cat_kesehatan", "Kesehatan", "expense", "local_hospital", "#4DB6AC", isDefault = true),
            Category("cat_pendidikan", "Pendidikan", "expense", "school", "#7986CB", isDefault = true),
            Category("cat_rumah", "Kebutuhan rumah", "expense", "home", "#A1887F", isDefault = true),
            Category("cat_lainnya_exp", "Lainnya", "expense", "more_horiz", "#90A4AE", isDefault = true),

            // Income Categories
            Category("cat_gaji", "Gaji", "income", "payments", "#1B8A5A", isDefault = true),
            Category("cat_freelance", "Freelance", "income", "work", "#00897B", isDefault = true),
            Category("cat_bonus", "Bonus", "income", "card_giftcard", "#0288D1", isDefault = true),
            Category("cat_hadiah", "Hadiah", "income", "redeem", "#7B1FA2", isDefault = true),
            Category("cat_penjualan", "Penjualan", "income", "storefront", "#F57C00", isDefault = true),
            Category("cat_lainnya_inc", "Lainnya", "income", "attach_money", "#455A64", isDefault = true)
        )

        for (cat in defaultCategories) {
            val cv = ContentValues().apply {
                put("id", cat.id)
                put("name", cat.name)
                put("type", cat.type)
                put("icon", cat.icon)
                put("color", cat.color)
                put("is_default", if (cat.isDefault) 1 else 0)
                put("is_active", if (cat.isActive) 1 else 0)
            }
            db.insert(TABLE_CATEGORIES, null, cv)
        }

        // Seed Assets
        val defaultAssets = listOf(
            Asset("asset_bca", "BCA", "Bank", 4500000L, 4500000L, "IDR", isDefault = true, isActive = true, "Akun utama perbankan"),
            Asset("asset_cash", "Cash", "Tunai", 750000L, 750000L, "IDR", isDefault = false, isActive = true, "Uang tunai di dompet"),
            Asset("asset_gopay", "GoPay", "E-wallet", 325000L, 325000L, "IDR", isDefault = false, isActive = true, "Dompet digital harian"),
            Asset("asset_mandiri", "Mandiri", "Bank", 1200000L, 1200000L, "IDR", isDefault = false, isActive = true, "Rekening tabungan bisnis"),
            Asset("asset_tabungan", "Tabungan", "Tabungan", 10000000L, 10000000L, "IDR", isDefault = false, isActive = true, "Simpanan masa depan")
        )

        for (asset in defaultAssets) {
            val cv = ContentValues().apply {
                put("id", asset.id)
                put("name", asset.name)
                put("type", asset.type)
                put("opening_balance", asset.openingBalance)
                put("current_balance", asset.currentBalance)
                put("currency", asset.currency)
                put("is_default", if (asset.isDefault) 1 else 0)
                put("is_active", if (asset.isActive) 1 else 0)
                put("note", asset.note)
                put("created_at", asset.createdAt)
                put("updated_at", asset.updatedAt)
            }
            db.insert(TABLE_ASSETS, null, cv)
        }

        // Seed Sample Transactions (matching specification examples)
        val initialTransactions = listOf(
            Transaction(
                id = "tx_1",
                type = "expense",
                amount = 20000L,
                description = "Beli kopi",
                categoryId = "cat_makanan",
                assetId = "asset_gopay",
                destinationAssetId = null,
                transactionDate = todayStr,
                transactionTime = "09:15",
                note = "Kopi pagi sebelum kerja",
                inputMethod = "voice",
                createdAt = now - 14400000
            ),
            Transaction(
                id = "tx_2",
                type = "expense",
                amount = 35000L,
                description = "Makan siang",
                categoryId = "cat_makanan",
                assetId = "asset_bca",
                destinationAssetId = null,
                transactionDate = todayStr,
                transactionTime = "12:30",
                note = "Nasi padang lengkap",
                inputMethod = "voice",
                createdAt = now - 7200000
            ),
            Transaction(
                id = "tx_3",
                type = "expense",
                amount = 50000L,
                description = "Bayar bensin",
                categoryId = "cat_transportasi",
                assetId = "asset_bca",
                destinationAssetId = null,
                transactionDate = yesterdayStr,
                transactionTime = "16:45",
                note = "Isi pertamax",
                inputMethod = "manual",
                createdAt = now - 86400000
            ),
            Transaction(
                id = "tx_4",
                type = "income",
                amount = 5000000L,
                description = "Gaji bulanan",
                categoryId = "cat_gaji",
                assetId = "asset_bca",
                destinationAssetId = null,
                transactionDate = yesterdayStr,
                transactionTime = "08:00",
                note = "Payroll kantor",
                inputMethod = "voice",
                createdAt = now - 90000000
            ),
            Transaction(
                id = "tx_5",
                type = "transfer",
                amount = 1000000L,
                description = "Transfer tabungan bulanan",
                categoryId = null,
                assetId = "asset_bca",
                destinationAssetId = "asset_tabungan",
                transactionDate = todayStr,
                transactionTime = "10:00",
                note = "Pindahan dana dingin",
                inputMethod = "manual",
                createdAt = now - 3600000
            )
        )

        for (tx in initialTransactions) {
            val cv = ContentValues().apply {
                put("id", tx.id)
                put("type", tx.type)
                put("amount", tx.amount)
                put("description", tx.description)
                put("category_id", tx.categoryId)
                put("asset_id", tx.assetId)
                put("destination_asset_id", tx.destinationAssetId)
                put("transaction_date", tx.transactionDate)
                put("transaction_time", tx.transactionTime)
                put("note", tx.note)
                put("attachment", tx.attachment)
                put("input_method", tx.inputMethod)
                put("created_at", tx.createdAt)
                put("updated_at", tx.updatedAt)
            }
            db.insert(TABLE_TRANSACTIONS, null, cv)
        }

        // Seed Budgets
        val defaultBudgets = listOf(
            Budget("b_makanan", "cat_makanan", "monthly", 1500000L, now),
            Budget("b_transport", "cat_transportasi", "monthly", 500000L, now),
            Budget("b_tagihan", "cat_tagihan", "monthly", 1000000L, now)
        )
        for (b in defaultBudgets) {
            val cv = ContentValues().apply {
                put("id", b.id)
                put("category_id", b.categoryId)
                put("period_type", b.periodType)
                put("target_amount", b.targetAmount)
                put("created_at", b.createdAt)
            }
            db.insert(TABLE_BUDGETS, null, cv)
        }

        // Seed Financial Goals
        val defaultGoals = listOf(
            FinancialGoal(
                id = "goal_darurat",
                name = "Dana Darurat",
                targetAmount = 15000000L,
                currentAmount = 5000000L,
                deadline = LocalDate.now().plusMonths(6).format(DateTimeFormatter.ISO_LOCAL_DATE),
                assetId = "asset_tabungan",
                note = "Minimal 3 bulan pengeluaran rutin",
                createdAt = now,
                updatedAt = now
            ),
            FinancialGoal(
                id = "goal_liburan",
                name = "Liburan Akhir Tahun",
                targetAmount = 6000000L,
                currentAmount = 2500000L,
                deadline = LocalDate.now().plusMonths(3).format(DateTimeFormatter.ISO_LOCAL_DATE),
                assetId = "asset_mandiri",
                note = "Trip ke Bali / Jogja",
                createdAt = now,
                updatedAt = now
            )
        )
        for (g in defaultGoals) {
            val cv = ContentValues().apply {
                put("id", g.id)
                put("name", g.name)
                put("target_amount", g.targetAmount)
                put("current_amount", g.currentAmount)
                put("deadline", g.deadline)
                put("asset_id", g.assetId)
                put("note", g.note)
                put("created_at", g.createdAt)
                put("updated_at", g.updatedAt)
            }
            db.insert(TABLE_GOALS, null, cv)
        }

        // Seed Debts
        val defaultDebts = listOf(
            Debt(
                id = "debt_demo_1",
                title = "Cicilan Laptop",
                lenderName = "Toko Elektronik",
                totalAmount = 6000000L,
                paidAmount = 2000000L,
                dueDate = LocalDate.now().plusMonths(2).format(DateTimeFormatter.ISO_LOCAL_DATE),
                status = DebtStatus.PARTIAL.key,
                note = "Cicilan 2 dari 6 bulan",
                createdAt = now,
                updatedAt = now
            ),
            Debt(
                id = "debt_demo_2",
                title = "Pinjam Renovasi",
                lenderName = "Budi",
                totalAmount = 3000000L,
                paidAmount = 0L,
                dueDate = LocalDate.now().plusMonths(1).format(DateTimeFormatter.ISO_LOCAL_DATE),
                status = DebtStatus.UNPAID.key,
                note = "Pinjaman tanpa bunga",
                createdAt = now,
                updatedAt = now
            )
        )
        for (d in defaultDebts) {
            val cv = ContentValues().apply {
                put("id", d.id)
                put("title", d.title)
                put("lender_name", d.lenderName)
                put("total_amount", d.totalAmount)
                put("paid_amount", d.paidAmount)
                put("due_date", d.dueDate)
                put("status", d.status)
                put("note", d.note)
                put("created_at", d.createdAt)
                put("updated_at", d.updatedAt)
            }
            db.insert(TABLE_DEBTS, null, cv)
        }
    }

    fun resetToDemo(db: SQLiteDatabase) {
        db.execSQL("DELETE FROM $TABLE_TRANSACTIONS")
        db.execSQL("DELETE FROM $TABLE_BUDGETS")
        db.execSQL("DELETE FROM $TABLE_GOALS")
        db.execSQL("DELETE FROM $TABLE_DEBTS")
        db.execSQL("DELETE FROM $TABLE_ASSETS")
        db.execSQL("DELETE FROM $TABLE_CATEGORIES")
        seedInitialData(db)
    }
}
