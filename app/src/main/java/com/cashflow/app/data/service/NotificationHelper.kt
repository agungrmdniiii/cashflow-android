package com.cashflow.app.data.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cashflow.app.MainActivity
import com.cashflow.app.R
import com.cashflow.app.data.model.BudgetWithProgress
import com.cashflow.app.data.preferences.PreferenceManager
import com.cashflow.app.ui.components.Formatters
import java.time.LocalDate

object NotificationHelper {

    const val CHANNEL_BUDGET_ALERTS = "budget_alerts_channel"
    const val CHANNEL_DAILY_REMINDER = "daily_reminder_channel"
    const val CHANNEL_BACKUP_REMINDER = "backup_reminder_channel"

    fun initNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val budgetChannel = NotificationChannel(
                CHANNEL_BUDGET_ALERTS,
                "Peringatan Anggaran",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi saat pengeluaran kategori mencapai 80% dan 100%"
                enableVibration(true)
            }

            val dailyChannel = NotificationChannel(
                CHANNEL_DAILY_REMINDER,
                "Pengingat Catat Harian",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Pengingat rutin untuk mencatat pengeluaran hari ini"
            }

            val backupChannel = NotificationChannel(
                CHANNEL_BACKUP_REMINDER,
                "Pengingat Cadangan Data",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Pengingat untuk mencadangkan data keuangan Anda"
            }

            manager.createNotificationChannels(listOf(budgetChannel, dailyChannel, backupChannel))
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Memeriksa ambang batas anggaran (80% dan 100%) dan mengirimkan notifikasi tepat satu kali per ambang per bulan.
     */
    fun checkAndNotifyBudgets(
        context: Context,
        budgets: List<BudgetWithProgress>,
        pref: PreferenceManager
    ) {
        if (!hasNotificationPermission(context)) return

        val isBudgetAlertsEnabled = pref.isBudgetAlertsEnabled()
        if (!isBudgetAlertsEnabled) return

        val today = LocalDate.now()

        for (item in budgets) {
            val budget = item.budget
            val catName = item.category?.name ?: "Kategori"
            val percentage = item.progressPercentage
            val currentMonthKey = if (budget.periodType == "weekly") today.with(java.time.DayOfWeek.MONDAY).toString() else com.cashflow.app.data.model.monthlyBudgetStart(today, pref.getBudgetStartDay()).toString()

            val notified80Key = "notif_budget_80_${budget.id}_$currentMonthKey"
            val notified100Key = "notif_budget_100_${budget.id}_$currentMonthKey"

            val hasNotified80 = pref.getBoolean(notified80Key, false)
            val hasNotified100 = pref.getBoolean(notified100Key, false)

            if (percentage >= 1.0f && !hasNotified100) {
                // Ambang 100% tercapai
                showBudgetNotification(
                    context = context,
                    notificationId = budget.id.hashCode() + 100,
                    title = "⚠️ Anggaran $catName Habis!",
                    message = "Pengeluaran ${Formatters.formatRupiah(item.spentAmount)} telah mencapai limit ${Formatters.formatRupiah(item.effectiveTargetAmount)}."
                )
                pref.setBoolean(notified100Key, true)
                pref.setBoolean(notified80Key, true) // Otomatis tandai 80% sudah lewat
            } else if (percentage >= 0.80f && percentage < 1.0f && !hasNotified80) {
                // Ambang 80% tercapai
                showBudgetNotification(
                    context = context,
                    notificationId = budget.id.hashCode() + 80,
                    title = "⚡ Anggaran $catName Mencapai 80%",
                    message = "Sisa anggaran Anda: ${Formatters.formatRupiah(item.remainingAmount)} dari total ${Formatters.formatRupiah(item.effectiveTargetAmount)}."
                )
                pref.setBoolean(notified80Key, true)
            }
        }
    }

    private fun showBudgetNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String
    ) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_BUDGET_ALERTS)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission rejected by user
        }
    }

    fun showLocalNotification(context: Context, id: Int, title: String, message: String, channel: String): Boolean {
        if (!hasNotificationPermission(context) || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return try {
            val intent = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(context, channel).setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title).setContentText(message).setContentIntent(intent).setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
            NotificationManagerCompat.from(context).notify(id, notification)
            true
        } catch (_: SecurityException) { false }
    }
}
