package com.cashflow.app.data.service

import android.content.Context
import com.cashflow.app.data.model.RecurringFrequency
import com.cashflow.app.data.model.RecurringMode
import com.cashflow.app.data.model.RecurringTransaction
import com.cashflow.app.data.model.RecurringExecution
import com.cashflow.app.data.model.Transaction
import com.cashflow.app.data.repository.CashflowRepository
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID

data class UpcomingRecurringItem(
    val recurring: RecurringTransaction,
    val scheduledDate: LocalDate,
    val isOverdue: Boolean
) {
    val dueDate: String get() = scheduledDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val daysRemaining: Int get() = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), scheduledDate).toInt()
}

typealias UpcomingRecurringBill = UpcomingRecurringItem

object RecurringSchedulerService {

    private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Menghitung tanggal yang disesuaikan untuk akhir bulan pendek (misal: tgl 31 di Februari -> 28/29).
     */
    fun clampDayOfMonth(year: Int, month: Int, targetDay: Int): LocalDate {
        val ym = YearMonth.of(year, month)
        val maxDay = ym.lengthOfMonth()
        val clampedDay = targetDay.coerceAtMost(maxDay)
        return ym.atDay(clampedDay)
    }

    /**
     * Menghitung daftar jadwal yang jatuh tempo hingga tanggal `targetDate` (inklusif).
     */
    fun generateScheduledDates(
        recurring: RecurringTransaction,
        upToDate: LocalDate
    ): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        require(recurring.interval > 0) { "Interval jadwal harus lebih dari 0." }
        val start = try {
            LocalDate.parse(recurring.startDate, DATE_FORMATTER)
        } catch (_: Exception) {
            return emptyList()
        }

        val end = recurring.endDate?.let {
            try { LocalDate.parse(it, DATE_FORMATTER) } catch (_: Exception) { null }
        }

        val limitDate = if (end != null && end.isBefore(upToDate)) end else upToDate
        val initialDayOfMonth = start.dayOfMonth

        var current = start
        var count = 0
        val maxOccurrences = recurring.totalOccurrences ?: Int.MAX_VALUE

        while (!current.isAfter(limitDate) && count < maxOccurrences) {
            dates.add(current)
            count++

            current = when (RecurringFrequency.fromValue(recurring.frequency)) {
                RecurringFrequency.DAILY -> current.plusDays(recurring.interval.toLong())
                RecurringFrequency.WEEKLY -> current.plusWeeks(recurring.interval.toLong())
                RecurringFrequency.MONTHLY -> {
                    val nextMonthDate = current.plusMonths(recurring.interval.toLong())
                    clampDayOfMonth(nextMonthDate.year, nextMonthDate.monthValue, initialDayOfMonth)
                }
                RecurringFrequency.YEARLY -> {
                    val nextYear = current.year + recurring.interval
                    clampDayOfMonth(nextYear, start.monthValue, initialDayOfMonth)
                }
            }
        }

        return dates
    }

    /**
     * Memproses transaksi berulang yang jatuh tempo secara idempotent (tidak menggandakan).
     * Jika mode = "auto", langsung membuat transaksi di ledger.
     */
    suspend fun processDueRecurringTransactions(
        repository: CashflowRepository,
        today: LocalDate = LocalDate.now()
    ): List<Transaction> {
        val activeRecurring = repository.recurringTransactions.value.filter { it.isActive }
        val generatedTransactions = mutableListOf<Transaction>()

        for (rec in activeRecurring) {
            val scheduledDates = generateScheduledDates(rec, today)
            for (date in scheduledDates) {
                val dateStr = date.format(DATE_FORMATTER)
                if (rec.mode == RecurringMode.AUTO.value) {
                    repository.executeRecurringOccurrence(rec.id, dateStr).getOrThrow()?.let {
                        generatedTransactions.add(it)
                    }
                }
            }
        }
        return generatedTransactions
    }

    /**
     * Mengambil daftar tagihan / transaksi berulang yang akan datang dalam X hari ke depan (default 7 hari).
     */
    fun getUpcomingOccurrences(
        recurringList: List<RecurringTransaction>,
        isExecuted: (String, String) -> Boolean,
        daysAhead: Int = 7,
        today: LocalDate = LocalDate.now()
    ): List<UpcomingRecurringItem> {
        val maxTargetDate = today.plusDays(daysAhead.toLong())
        val result = mutableListOf<UpcomingRecurringItem>()

        for (rec in recurringList.filter { it.isActive }) {
            val allDates = generateScheduledDates(rec, maxTargetDate)
            for (d in allDates) {
                val dateStr = d.format(DATE_FORMATTER)
                // Hanya sertakan jika belum dieksekusi/dilewati
                if (!isExecuted(rec.id, dateStr)) {
                    val isOverdue = d.isBefore(today)
                    // Tampilkan jika overdue atau dalam rentang [today..maxTargetDate]
                    if (isOverdue || (!d.isBefore(today) && !d.isAfter(maxTargetDate))) {
                        result.add(
                            UpcomingRecurringItem(
                                recurring = rec,
                                scheduledDate = d,
                                isOverdue = isOverdue
                            )
                        )
                    }
                }
            }
        }

        return result.sortedBy { it.scheduledDate }
    }

    fun getUpcomingBills(
        repo: CashflowRepository,
        recurringList: List<RecurringTransaction>,
        daysAhead: Int = 7
    ): List<UpcomingRecurringItem> {
        val executed = repo.executedOccurrenceKeys()
        return getUpcomingOccurrences(
            recurringList = recurringList,
            isExecuted = { id, date -> (id to date) in executed },
            daysAhead = daysAhead
        )
    }

    fun getUpcomingBills(
        context: Context,
        recurringList: List<RecurringTransaction>,
        daysAhead: Int = 7
    ): List<UpcomingRecurringItem> {
        val repo = CashflowRepository(context)
        return getUpcomingBills(repo, recurringList, daysAhead)
    }

    suspend fun processDueRecurringSchedules(
        repo: CashflowRepository
    ): Int {
        val created = processDueRecurringTransactions(repo)
        return created.size
    }

    suspend fun processDueRecurringSchedules(
        context: Context,
        recurringList: List<RecurringTransaction>
    ): Int {
        val repo = CashflowRepository(context)
        return processDueRecurringSchedules(repo)
    }

    suspend fun skipRecurringOccurrence(
        repo: CashflowRepository,
        recurringId: String,
        occurrenceDate: String
    ) {
        val exec = RecurringExecution(
            id = UUID.randomUUID().toString(),
            recurringId = recurringId,
            scheduledDate = occurrenceDate,
            transactionId = null,
            status = "skipped"
        )
        repo.recordRecurringExecution(exec).getOrThrow()
    }

    suspend fun skipRecurringOccurrence(
        context: Context,
        recurringId: String,
        occurrenceDate: String
    ) {
        val repo = CashflowRepository(context)
        skipRecurringOccurrence(repo, recurringId, occurrenceDate)
    }
}
