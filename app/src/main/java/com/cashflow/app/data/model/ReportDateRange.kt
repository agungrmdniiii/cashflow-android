package com.cashflow.app.data.model

import java.time.LocalDate

data class ReportDateRange(val start: LocalDate, val end: LocalDate) {
    init { require(!end.isBefore(start)) { "Tanggal akhir harus sama atau setelah tanggal awal." } }
    fun contains(value: String): Boolean = runCatching {
        val date = LocalDate.parse(value)
        !date.isBefore(start) && !date.isAfter(end)
    }.getOrDefault(false)
}
