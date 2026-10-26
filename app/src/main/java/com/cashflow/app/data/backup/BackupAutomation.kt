package com.cashflow.app.data.backup

import java.time.*

enum class BackupFrequency(val label: String) { DAILY("Harian"), WEEKLY("Mingguan"), MONTHLY("Bulanan") }
enum class BackupDestination(val label: String) { LOCAL("Di HP"), DRIVE("Google Drive") }
enum class BackupNetwork(val label: String) {
    WIFI("Wi-Fi saja"), ANY("Wi-Fi dan seluler");
    fun allows(validated: Boolean, wifi: Boolean, cellular: Boolean, metered: Boolean): Boolean =
        validated && if (this == WIFI) wifi && !cellular && !metered else wifi || cellular
}

data class BackupWindow(val start: ZonedDateTime, val end: ZonedDateTime)

data class BackupAutomation(
    val enabled: Boolean = false,
    val frequency: BackupFrequency = BackupFrequency.DAILY,
    val destination: BackupDestination = BackupDestination.DRIVE,
    val network: BackupNetwork = BackupNetwork.WIFI,
    val startHour: Int = 2,
    val endHour: Int = 6,
    val monthDay: Int = LocalDate.now().dayOfMonth
) {
    init { require(startHour in 0..23 && endHour in 0..23 && startHour != endHour && monthDay in 1..31) }

    fun nextWindow(now: ZonedDateTime, lastSuccess: Instant?): BackupWindow? {
        if (!enabled) return null
        fun windowDay(time: ZonedDateTime) = if (endHour < startHour && time.hour < endHour)
            time.toLocalDate().minusDays(1) else time.toLocalDate()
        fun window(day: LocalDate) = BackupWindow(day.atTime(startHour, 0).atZone(now.zone),
            day.plusDays(if (endHour < startHour) 1 else 0).atTime(endHour, 0).atZone(now.zone))
        val currentDay = windowDay(now)
        val dueDay = lastSuccess?.let { success ->
            val day = windowDay(minOf(success, now.toInstant()).atZone(now.zone))
            when (frequency) {
                BackupFrequency.DAILY -> day.plusDays(1)
                BackupFrequency.WEEKLY -> day.plusDays(7)
                BackupFrequency.MONTHLY -> YearMonth.from(day).plusMonths(1).let { it.atDay(minOf(monthDay, it.lengthOfMonth())) }
            }
        } ?: currentDay
        val candidate = window(maxOf(currentDay, dueDay))
        return if (now.isBefore(candidate.end)) candidate else window(candidate.start.toLocalDate().plusDays(1))
    }
}
