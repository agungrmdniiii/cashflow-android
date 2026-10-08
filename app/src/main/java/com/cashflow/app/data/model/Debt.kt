package com.cashflow.app.data.model

import java.time.LocalDate

enum class DebtStatus(val key: String, val label: String) {
    UNPAID("unpaid", "Belum Lunas"),
    PARTIAL("partial", "Sebagian"),
    PAID("paid", "Lunas")
}

data class Debt(
    val id: String,
    val title: String,               // Contoh: "Pinjam Renovasi", "Cicilan Laptop"
    val lenderName: String,          // Contoh: "Budi", "Bank Mandiri"
    val totalAmount: Long,           // Total nominal pinjaman awal
    val paidAmount: Long = 0L,       // Total nominal yang telah dicicil/dibayar
    val dueDate: String,             // Format ISO: "YYYY-MM-DD"
    val status: String = "unpaid",   // "unpaid" | "partial" | "paid"
    val note: String = "",           // Catatan opsional
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val remainingAmount: Long
        get() = (totalAmount - paidAmount).coerceAtLeast(0L)

    val progressPercentage: Float
        get() = if (totalAmount > 0) {
            (paidAmount.toFloat() / totalAmount.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val isOverdue: Boolean
        get() = try {
            if (status == DebtStatus.PAID.key) false
            else LocalDate.parse(dueDate).isBefore(LocalDate.now())
        } catch (_: Exception) {
            false
        }
}
