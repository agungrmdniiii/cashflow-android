package com.cashflow.app

import com.cashflow.app.data.model.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class TransactionSearchMatchingTest {
    private val row = Transaction("sample", "expense", 50_000, "Sarapan", "food", "bank",
        transactionDate = "2026-10-23", note = "Rapat tim", tags = "kantor")

    private fun matches(query: String, tx: Transaction = row) = transactionMatchesSearch(
        tx, transactionSearchTerms(query), "Makan", "BCA", "Dompet")

    @Test fun wordsMatchAcrossFieldsInAnyOrderIgnoringCaseAndWhitespace() {
        assertTrue(matches("  BcA\tMAKAN   rapat  "))
        assertTrue(matches("sarapan kantor"))
        assertTrue(matches("dompet 50000"))
        assertFalse(matches("makan mandiri"))
    }

    @Test fun formattedRupiahFindsTheExactAmount() {
        for (query in listOf("50.000", "50,000", "Rp50.000", "Rp. 50.000", "makan Rp 50.000")) {
            assertTrue(query, matches(query))
            assertFalse(query, matches(query, row.copy(amount = 500_000)))
        }
        assertTrue(matches("Rp 1.250.000", row.copy(amount = 1_250_000)))
        assertFalse(matches("999999999999999999999999"))
    }

    @Test fun blankSearchAndLiteralSpecialCharactersAreSafe() {
        assertTrue(matches(" \n\t"))
        assertFalse(matches(".*"))
        assertTrue(matches("c++", row.copy(description = "Buku C++")))
    }

    @Test fun formattedNumbersInsideNotesRemainSearchableAsWritten() {
        assertTrue(matches("edisi 1.000", row.copy(note = "Buku edisi 1.000")))
        val adjustment = row.copy(description = "Penyesuaian Saldo (Rp -1.050.000)")
        assertTrue(matches("Penyesuaian Saldo (Rp -1.050.000)", adjustment))
    }

    @Test fun largeHistoryKeepsOnlyRowsMatchingEveryWordInOriginalOrder() {
        val rows = List(20_000) { row.copy(id = it.toString(), amount = if (it % 2 == 0) 50_000 else 51_000) }
        val terms = transactionSearchTerms("bca kantor Rp50.000")
        val found = rows.filter { transactionMatchesSearch(it, terms, "Makan", "BCA", "") }
        assertEquals(10_000, found.size)
        assertEquals("0", found.first().id)
        assertEquals("19998", found.last().id)
    }

    @Test fun amountLimitsMustBeValidInclusiveAndNotReversed() {
        fun error(min: String, max: String) = transactionFilterError(min, max, false, null, null)
        assertNull(error("", ""))
        assertNull(error("0", "0"))
        assertNull(error("50000", "50000"))
        assertNull(error("", Long.MAX_VALUE.toString()))
        assertNotNull(error("50001", "50000"))
        assertNotNull(error("9999999999999999999999", ""))
        assertNotNull(error("", "9999999999999999999999"))
        assertNotNull(error("-1", ""))
        assertNotNull(error("", "1.5"))
    }

    @Test fun customDatesRequireBothBoundariesInOrder() {
        val day = LocalDate.of(2026, 10, 23)
        fun error(start: LocalDate?, end: LocalDate?, custom: Boolean = true) =
            transactionFilterError("", "", custom, start, end)
        assertNotNull(error(null, null))
        assertNotNull(error(day, null))
        assertNotNull(error(null, day))
        assertNotNull(error(day, day.minusDays(1)))
        assertNull(error(day, day))
        assertNull(error(day, day.plusDays(1)))
        assertNull(error(null, null, false))
    }
}
