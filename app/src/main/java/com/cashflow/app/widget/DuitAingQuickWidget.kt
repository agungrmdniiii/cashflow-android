package com.cashflow.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.cashflow.app.MainActivity
import com.cashflow.app.R
import com.cashflow.app.data.db.DatabaseHelper
import com.cashflow.app.data.preferences.PreferenceManager
import java.util.concurrent.Executors
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.components.Formatters
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

open class DuitAingQuickWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pending = goAsync()
        updateAllWidgets(context) { pending?.finish() }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE_BALANCE_VISIBILITY) {
            val preferences = PreferenceManager(context)
            preferences.setBalanceHidden(!preferences.isBalanceHidden())
            val pending = goAsync()
            updateAllWidgets(context) { pending?.finish() }
        } else {
            super.onReceive(context, intent)
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        val pending = goAsync()
        updateAllWidgets(context) { pending?.finish() }
    }

    companion object {
        const val ACTION_TOGGLE_BALANCE_VISIBILITY = "com.cashflow.app.widget.TOGGLE_BALANCE_VISIBILITY"
        private val updates = Executors.newSingleThreadExecutor()
        internal val providers = listOf(DuitAingQuickWidget::class.java, DuitAingBalanceWidget::class.java,
            DuitAingEntryWidget::class.java, DuitAingSummaryWidget::class.java)
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val layout = when (appWidgetManager.getAppWidgetInfo(appWidgetId)?.provider?.className) {
                DuitAingBalanceWidget::class.java.name -> R.layout.widget_duit_aing_balance
                DuitAingEntryWidget::class.java.name -> R.layout.widget_duit_aing_entry
                DuitAingSummaryWidget::class.java.name -> R.layout.widget_duit_aing_summary
                else -> R.layout.widget_duit_aing_quick
            }
            appWidgetManager.updateAppWidget(appWidgetId, buildWidgetViews(context, layout))
        }

        internal fun buildWidgetViews(context: Context, layout: Int = R.layout.widget_duit_aing_quick): RemoteViews {
            val views = RemoteViews(context.packageName, layout)
            fun openApp(action: String?, request: Int): PendingIntent = PendingIntent.getActivity(context, request,
                Intent(context, MainActivity::class.java).apply {
                    this.action = action
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val open = openApp(null, 100)
            views.setOnClickPendingIntent(R.id.widget_brand, open)
            views.setOnClickPendingIntent(R.id.widget_container, open)
            if (layout == R.layout.widget_duit_aing_quick || layout == R.layout.widget_duit_aing_entry) {
                views.setOnClickPendingIntent(R.id.btn_widget_voice, openApp(CashflowViewModel.ACTION_RECORD_VOICE, 101))
                views.setOnClickPendingIntent(R.id.btn_widget_add, openApp(CashflowViewModel.ACTION_ADD_EXPENSE, 102))
                views.setContentDescription(R.id.btn_widget_voice, "Catat transaksi dengan suara")
                views.setContentDescription(R.id.btn_widget_add, "Catat pengeluaran")
            }
            if (layout == R.layout.widget_duit_aing_entry) {
                views.setOnClickPendingIntent(R.id.btn_widget_income, openApp(CashflowViewModel.ACTION_ADD_INCOME, 104))
                views.setContentDescription(R.id.btn_widget_income, "Catat pemasukan")
                return views
            }

            // 1. Calculate live balance (Total Wealth & Net Worth)
            var totalWealth = 0L
            var totalDebt = 0L
            var totalReceivable = 0L
            var income = 0L
            var expense = 0L
            val monthStart = LocalDate.now().withDayOfMonth(1)
            var available = true
            try {
                DatabaseHelper(context).use { helper ->
                    val db = helper.readableDatabase
                    // Query active assets total
                    db.rawQuery(
                        "SELECT COALESCE(SUM(current_balance), 0) FROM ${DatabaseHelper.TABLE_ASSETS} WHERE is_active = 1",
                        null
                    ).use { cursor ->
                        if (cursor.moveToFirst()) {
                            totalWealth = cursor.getLong(0)
                        }
                    }
                    // Match the app: receivables add to net worth; remaining amounts cannot be negative.
                    db.rawQuery(
                        """SELECT
                            COALESCE(SUM(CASE WHEN direction = 'debt' THEN MAX(total_amount - paid_amount, 0) ELSE 0 END), 0),
                            COALESCE(SUM(CASE WHEN direction = 'receivable' THEN MAX(total_amount - paid_amount, 0) ELSE 0 END), 0)
                            FROM ${DatabaseHelper.TABLE_DEBTS}""",
                        null
                    ).use { cursor ->
                        if (cursor.moveToFirst()) {
                            totalDebt = cursor.getLong(0)
                            totalReceivable = cursor.getLong(1)
                        }
                    }
                    if (layout == R.layout.widget_duit_aing_summary) {
                        // Match report totals: exclude transfers, allocations, and balance adjustments.
                        db.rawQuery("""SELECT
                            COALESCE(SUM(CASE WHEN type = 'income' THEN amount ELSE 0 END), 0),
                            COALESCE(SUM(CASE WHEN type = 'expense' THEN amount ELSE 0 END), 0)
                            FROM ${DatabaseHelper.TABLE_TRANSACTIONS}
                            WHERE transaction_date >= ? AND transaction_date < ? AND is_adjustment = 0""",
                            arrayOf(monthStart.toString(), monthStart.plusMonths(1).toString())).use { cursor ->
                            if (cursor.moveToFirst()) { income = cursor.getLong(0); expense = cursor.getLong(1) }
                        }
                    }
                }
            } catch (_: Exception) {
                available = false
            }

            // Read privacy after the database work, so queued refreshes use the latest choice.
            val hidden = PreferenceManager(context).isBalanceHidden()
            val balanceText = if (hidden) "Rp ••••••" else if (available) Formatters.formatRupiah(totalWealth) else "Saldo belum tersedia"
            views.setTextViewText(R.id.tv_widget_balance, balanceText)
            val eye = if (hidden) R.drawable.ic_widget_visibility_off else R.drawable.ic_widget_visibility
            if (layout == R.layout.widget_duit_aing_quick) views.setTextViewCompoundDrawablesRelative(R.id.tv_widget_balance, 0, 0, eye, 0)
            else views.setImageViewResource(R.id.btn_widget_visibility, eye)

            val subtitleText = if (hidden) {
                "Saldo disembunyikan"
            } else if (!available) {
                "Buka Duit Aing"
            } else if (totalDebt > 0 || totalReceivable > 0) {
                val net = totalWealth + totalReceivable - totalDebt
                "Bersih: ${Formatters.formatRupiah(net)}"
            } else {
                "Saldo aset aktif"
            }
            views.setTextViewText(R.id.tv_widget_subtitle, subtitleText)

            val toggleIntent = Intent(context, DuitAingQuickWidget::class.java).setAction(ACTION_TOGGLE_BALANCE_VISIBILITY)
            val togglePendingIntent = PendingIntent.getBroadcast(context, 103, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.layout_widget_info, togglePendingIntent)
            views.setContentDescription(R.id.layout_widget_info, if (hidden) "Saldo aset disembunyikan. Tampilkan saldo aset"
                else "$balanceText. $subtitleText. Sembunyikan saldo aset")
            if (layout != R.layout.widget_duit_aing_quick) {
                views.setOnClickPendingIntent(R.id.btn_widget_visibility, togglePendingIntent)
                views.setContentDescription(R.id.btn_widget_visibility, if (hidden) "Tampilkan saldo" else "Sembunyikan saldo")
            }
            if (layout == R.layout.widget_duit_aing_summary) {
                views.setTextViewText(R.id.tv_widget_period, monthStart.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("id-ID"))))
                views.setTextViewText(R.id.tv_widget_income, if (hidden) "Rp ••••••" else if (available) Formatters.formatRupiah(income) else "Belum tersedia")
                views.setTextViewText(R.id.tv_widget_expense, if (hidden) "Rp ••••••" else if (available) Formatters.formatRupiah(expense) else "Belum tersedia")
            }

            return views
        }

        fun updateAllWidgets(context: Context, onComplete: (() -> Unit)? = null) {
            val appContext = context.applicationContext
            updates.execute {
                try {
                    val manager = AppWidgetManager.getInstance(appContext)
                    for (provider in providers) {
                        for (id in manager.getAppWidgetIds(ComponentName(appContext, provider))) updateAppWidget(appContext, manager, id)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("DuitAingWidget", "Widget belum dapat diperbarui", e)
                } finally {
                    onComplete?.invoke()
                }
            }
        }
    }
}

class DuitAingBalanceWidget : DuitAingQuickWidget()
class DuitAingEntryWidget : DuitAingQuickWidget()
class DuitAingSummaryWidget : DuitAingQuickWidget()
