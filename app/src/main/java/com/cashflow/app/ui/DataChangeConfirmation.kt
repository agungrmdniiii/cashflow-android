package com.cashflow.app.ui

/** A pending mutation is consumed once; cancellation also invalidates stale UI callbacks. */
class DataChangeConfirmation(
    val title: String,
    val message: String,
    val destructive: Boolean,
    private val onCancel: () -> Unit = {},
    val confirmLabel: String? = null,
    val reviewTransactions: List<com.cashflow.app.data.model.Transaction> = emptyList(),
    action: () -> Unit
) {
    private var action: (() -> Unit)? = action

    fun confirm() {
        val pending = action ?: return
        action = null
        pending()
    }

    fun cancel() {
        if (action == null) return
        action = null
        onCancel()
    }
}
