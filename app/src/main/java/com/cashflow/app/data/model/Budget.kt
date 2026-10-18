package com.cashflow.app.data.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class Budget(
    val id: String,
    val categoryId: String,
    val periodType: String = "monthly", // "weekly", "monthly"
    val targetAmount: Long,
    val createdAt: Long = System.currentTimeMillis()
)

data class BudgetWithProgress(
    val budget: Budget,
    val category: Category?,
    val spentAmount: Long,
    val remainingAmount: Long,
    val progressPercentage: Float,
    val status: BudgetStatus,
    val rolloverAmount: Long = 0L,
    val daysRemaining: Int = 1
) {
    val effectiveTargetAmount: Long get() = budget.targetAmount + rolloverAmount
}

fun calculateBudgetProgress(
    budgets: List<Budget>, categories: List<Category>, transactions: List<Transaction>,
    rolloverEnabled: Boolean, today: LocalDate = LocalDate.now(), periodStartDay: Int = 1
): List<BudgetWithProgress> {
    val categoriesById = categories.associateBy { it.id }
    val starts = mapOf(
        "monthly" to monthlyBudgetStart(today, periodStartDay),
        "weekly" to today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    )
    val windows = starts.mapValues { (period, start) ->
        val end = if (period == "weekly") start.plusWeeks(1) else nextMonthlyBudgetStart(start, periodStartDay)
        val previousStart = if (period == "weekly") start.minusWeeks(1) else java.time.YearMonth.from(start).minusMonths(1).let { it.atDay(periodStartDay.coerceIn(1, it.lengthOfMonth())) }
        Triple(start.toString(), end.toString(), previousStart.toString())
    }
    val current = mutableMapOf<Pair<String, String>, Long>()
    val previous = mutableMapOf<Pair<String, String>, Long>()
    // Two calendar periods, one traversal of transactions; work does not grow with the budget count.
    for (tx in transactions) {
        if (tx.type != TransactionType.EXPENSE.value || tx.categoryId == null) continue
        for ((period, window) in windows) {
            val (start, end, previousStart) = window
            val key = period to tx.categoryId
            when {
                tx.transactionDate >= start && tx.transactionDate < end ->
                    current[key] = (current[key] ?: 0L) + tx.amount
                tx.transactionDate >= previousStart && tx.transactionDate < start ->
                    previous[key] = (previous[key] ?: 0L) + tx.amount
            }
        }
    }
    return budgets.map { budget ->
        val period = if (budget.periodType == "weekly") "weekly" else "monthly"
        val start = starts.getValue(period)
        val end = if (period == "weekly") start.plusWeeks(1) else nextMonthlyBudgetStart(start, periodStartDay)
        val key = period to budget.categoryId
        val existedBeforePeriod = java.time.Instant.ofEpochMilli(budget.createdAt)
            .atZone(ZoneId.systemDefault()).toLocalDate().isBefore(start)
        val rollover = if (rolloverEnabled && existedBeforePeriod)
            (budget.targetAmount - (previous[key] ?: 0L)).coerceAtLeast(0L) else 0L
        val spent = current[key] ?: 0L
        val limit = budget.targetAmount + rollover
        val percentage = if (limit > 0L) spent.toFloat() / limit.toFloat() else 0f
        val status = when {
            spent > limit -> BudgetStatus.EXCEEDED
            percentage >= 0.85f -> BudgetStatus.NEAR_LIMIT
            percentage >= 0.60f -> BudgetStatus.WARNING
            else -> BudgetStatus.SAFE
        }
        BudgetWithProgress(budget, categoriesById[budget.categoryId], spent, limit - spent,
            percentage.coerceIn(0f, 2f), status, rollover,
            ChronoUnit.DAYS.between(today, end).toInt().coerceAtLeast(1))
    }
}

fun monthlyBudgetStart(today: LocalDate, day: Int): LocalDate {
    require(day in 1..31)
    val month = java.time.YearMonth.from(today)
    val candidate = month.atDay(day.coerceAtMost(month.lengthOfMonth()))
    return if (!today.isBefore(candidate)) candidate else month.minusMonths(1).let { it.atDay(day.coerceAtMost(it.lengthOfMonth())) }
}

fun nextMonthlyBudgetStart(start: LocalDate, day: Int): LocalDate =
    java.time.YearMonth.from(start).plusMonths(1).let { it.atDay(day.coerceIn(1, it.lengthOfMonth())) }
