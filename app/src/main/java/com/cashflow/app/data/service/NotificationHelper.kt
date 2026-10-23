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
import androidx.core.graphics.drawable.toBitmap
import com.cashflow.app.MainActivity
import com.cashflow.app.R
import com.cashflow.app.ui.CashflowViewModel
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
        pref: PreferenceManager,
        today: LocalDate = LocalDate.now()
    ) {
        if (!hasNotificationPermission(context)) return

        val isBudgetAlertsEnabled = pref.isBudgetAlertsEnabled()
        if (!isBudgetAlertsEnabled) return

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
                val sent = showBudgetNotification(
                    context = context,
                    notificationId = budget.id.hashCode() + 100,
                    title = "Anggaran $catName mencapai batas",
                    message = "Terpakai ${Formatters.formatRupiah(item.spentAmount)} dari ${Formatters.formatRupiah(item.effectiveTargetAmount)}. Periksa pengeluaran Anda."
                )
                if (sent) {
                    pref.setBoolean(notified100Key, true)
                    pref.setBoolean(notified80Key, true)
                }
            } else if (percentage >= 0.80f && percentage < 1.0f && !hasNotified80) {
                // Ambang 80% tercapai
                val sent = showBudgetNotification(
                    context = context,
                    notificationId = budget.id.hashCode() + 80,
                    title = "$catName mencapai 80% anggaran",
                    message = "Sisa ${Formatters.formatRupiah(item.remainingAmount)} dari ${Formatters.formatRupiah(item.effectiveTargetAmount)}. Pantau pengeluaran berikutnya agar tetap sesuai anggaran."
                )
                if (sent) pref.setBoolean(notified80Key, true)
            }
        }
    }

    private fun showBudgetNotification(context: Context, notificationId: Int, title: String, message: String): Boolean =
        showLocalNotification(context, notificationId, title, message, CHANNEL_BUDGET_ALERTS)

    fun canSendNotifications(context: Context): Boolean = hasNotificationPermission(context) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun blockedChannels(context: Context): List<String> =
        context.getSystemService(NotificationManager::class.java).notificationChannels
            .filter { it.id in listOf(CHANNEL_BUDGET_ALERTS, CHANNEL_DAILY_REMINDER, CHANNEL_BACKUP_REMINDER) &&
                it.importance == NotificationManager.IMPORTANCE_NONE }
            .map { it.name.toString() }

    fun showLocalNotification(context: Context, id: Int, title: String, message: String, channel: String): Boolean {
        initNotificationChannels(context)
        if (!canSendNotifications(context)) return false
        val notificationChannel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(channel)
        if (notificationChannel == null || notificationChannel.importance == NotificationManager.IMPORTANCE_NONE) return false
        return try {
            val (label, button, intentAction) = when (channel) {
                CHANNEL_BUDGET_ALERTS -> Triple("Anggaran", "Lihat anggaran", CashflowViewModel.ACTION_OPEN_BUDGETS)
                CHANNEL_BACKUP_REMINDER -> Triple("Cadangan data", "Kelola cadangan", CashflowViewModel.ACTION_OPEN_BACKUP)
                CHANNEL_DAILY_REMINDER -> if (id == 3002) Triple("Tagihan", "Lihat jadwal", CashflowViewModel.ACTION_OPEN_RECURRING)
                    else Triple("Catatan harian", "Catat transaksi", CashflowViewModel.ACTION_ADD_TRANSACTION)
                else -> Triple("Pengingat", "Buka aplikasi", null)
            }
            val intent = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java).apply {
                action = intentAction
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_stat_notification)
                .setLargeIcon(context.packageManager.getApplicationIcon(context.applicationInfo).toBitmap(128, 128))
                .setContentTitle(title).setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(intent).setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER).setSubText(label)
                .addAction(0, button, intent)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
            NotificationManagerCompat.from(context).notify(id, notification)
            true
        } catch (_: SecurityException) { false }
    }
}
