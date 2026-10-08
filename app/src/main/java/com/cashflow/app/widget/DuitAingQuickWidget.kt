package com.cashflow.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.cashflow.app.MainActivity
import com.cashflow.app.R
import com.cashflow.app.data.db.DatabaseHelper
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.components.Formatters

class DuitAingQuickWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_duit_aing_quick)

            // 1. Calculate live balance (Total Wealth & Net Worth)
            var totalWealth = 0L
            var totalDebt = 0L
            try {
                val db = DatabaseHelper(context).readableDatabase
                // Query active assets total
                db.rawQuery(
                    "SELECT COALESCE(SUM(current_balance), 0) FROM ${DatabaseHelper.TABLE_ASSETS} WHERE is_active = 1",
                    null
                ).use { cursor ->
                    if (cursor.moveToFirst()) {
                        totalWealth = cursor.getLong(0)
                    }
                }
                // Query remaining debt
                db.rawQuery(
                    "SELECT COALESCE(SUM(total_amount - paid_amount), 0) FROM ${DatabaseHelper.TABLE_DEBTS} WHERE status != 'paid'",
                    null
                ).use { cursor ->
                    if (cursor.moveToFirst()) {
                        totalDebt = cursor.getLong(0)
                    }
                }
            } catch (e: Exception) {
                // Fallback to 0 if database read fails
            }

            views.setTextViewText(R.id.tv_widget_balance, Formatters.formatRupiah(totalWealth))

            val subtitleText = if (totalDebt > 0) {
                val net = totalWealth - totalDebt
                "Bersih: ${Formatters.formatRupiah(net)}"
            } else {
                "Kas Bersih Aktif"
            }
            views.setTextViewText(R.id.tv_widget_subtitle, subtitleText)

            // 2. PendingIntent to open MainActivity when tapping info or widget card
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                100,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.layout_widget_info, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_container, openAppPendingIntent)

            // 3. PendingIntent for Voice Record
            val voiceIntent = Intent(context, MainActivity::class.java).apply {
                action = CashflowViewModel.ACTION_RECORD_VOICE
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val voicePendingIntent = PendingIntent.getActivity(
                context,
                101,
                voiceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_voice, voicePendingIntent)

            // 4. PendingIntent for Manual Add Transaction
            val addIntent = Intent(context, MainActivity::class.java).apply {
                action = CashflowViewModel.ACTION_ADD_TRANSACTION
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val addPendingIntent = PendingIntent.getActivity(
                context,
                102,
                addIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_add, addPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, DuitAingQuickWidget::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                for (id in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, id)
                }
            } catch (e: Exception) {
                // Ignore widget update errors
            }
        }
    }
}
