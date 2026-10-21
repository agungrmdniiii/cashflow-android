package com.cashflow.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DataChangeConfirmationTest {
    @Test fun requestingDoesNotMutateAndConfirmationRunsOnlyOnce() {
        var changes = 0
        val request = DataChangeConfirmation("Simpan perubahan?", "Data akan diperbarui.", false) { changes++ }
        assertEquals(0, changes)
        request.confirm()
        request.confirm()
        assertEquals(1, changes)
    }

    @Test fun cancellationDiscardsTheActionEvenIfAnOldCallbackRuns() {
        var changes = 0
        var cancelled = 0
        val request = DataChangeConfirmation("Hapus?", "Data akan dihapus.", true, { cancelled++ }) { changes++ }
        request.cancel()
        request.cancel()
        request.confirm()
        assertEquals(0, changes)
        assertEquals(1, cancelled)
    }
}
