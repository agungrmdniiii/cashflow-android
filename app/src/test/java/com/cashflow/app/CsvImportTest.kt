package com.cashflow.app

import com.cashflow.app.data.importer.CsvImport
import org.junit.Assert.*
import org.junit.Test

class CsvImportTest {
    @Test fun quotedNewlinesCommasAndEscapedQuotesAreOneField() {
        assertEquals(listOf(listOf("a", "b,c\n\"d\"")), CsvImport.parse("\uFEFFa,\"b,c\n\"\"d\"\"\"\r\n"))
    }
    @Test fun rejectsUnclosedQuote() {
        assertTrue(runCatching { CsvImport.parse("a,\"b") }.isFailure)
    }
    @Test fun rejectsCharactersAfterClosingQuote() {
        assertTrue(runCatching { CsvImport.parse("a,\"b\"x") }.isFailure)
    }
}
