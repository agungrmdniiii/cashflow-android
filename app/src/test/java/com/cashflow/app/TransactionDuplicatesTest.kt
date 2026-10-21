package com.cashflow.app

import com.cashflow.app.data.model.*
import com.cashflow.app.data.importer.CsvImport
import org.junit.Assert.*
import org.junit.Test

class TransactionDuplicatesTest {
    private val original = Transaction("old", "expense", 50_000, "Makan", "food", "cash", transactionDate = "2026-10-21")
    private val header = "Tanggal,Waktu,Deskripsi,Jenis,Kategori,Aset,Aset Tujuan,Nominal,Catatan,Metode Input\n"
    private val assets = listOf(Asset("cash", "Tunai", "Tunai", 0, 0))
    private fun row(description: String) = "2026-10-21,12:00,$description,Pengeluaran,-,Tunai,-,50000,,manual"

    @Test fun differentOcrTextAndTimeStillWarnWithoutChangingEitherRecord() {
        val scanned = original.copy(id = "new", description = "Merchant OCR", categoryId = null, transactionTime = "17:30", inputMethod = "ocr")
        assertEquals(listOf(original), findSimilarTransactions(scanned, listOf(original)))
        assertEquals("Makan", original.description)
        assertEquals("Merchant OCR", scanned.description)
    }
    @Test fun differentAmountsDatesAccountsAndTypesDoNotWarn() {
        listOf(original.copy(id = "new", amount = 50_001), original.copy(id = "new", assetId = "bank"),
            original.copy(id = "new", transactionDate = "2026-10-22"), original.copy(id = "new", type = "income"),
            original.copy(id = "new", isAdjustment = true)).forEach { assertTrue(findSimilarTransactions(it, listOf(original)).isEmpty()) }
        assertTrue(findSimilarTransactions(original, listOf(original)).isEmpty())
        assertTrue(findSimilarTransactions(original.copy(id = "new"), listOf(original.copy(isAdjustment = true))).isEmpty())
    }
    @Test fun transfersMustHaveTheSameDirectionAndDestination() {
        val transfer = original.copy(type = "transfer", destinationAssetId = "bank")
        assertEquals(listOf(transfer), findSimilarTransactions(transfer.copy(id = "new"), listOf(transfer)))
        assertTrue(findSimilarTransactions(transfer.copy(id = "new", destinationAssetId = "other"), listOf(transfer)).isEmpty())
        assertTrue(findSimilarTransactions(transfer.copy(id = "new", assetId = "bank", destinationAssetId = "cash"), listOf(transfer)).isEmpty())
    }
    @Test fun csvDistinguishesIdenticalRowsFromSimilarRowsWithinTheSameBatch() {
        val preview = CsvImport.preview(header + listOf(row("A"), row("A"), row("B")).joinToString("\n"), assets, emptyList(), emptyList())
        assertTrue(preview.errors.toString(), preview.errors.isEmpty())
        assertEquals(2, preview.transactions.size)
        assertEquals(1, preview.duplicateCount)
        assertEquals(1, preview.similarTransactions.size)
        assertEquals("A", preview.similarTransactions.values.single().description)
    }
    @Test fun csvFindsExistingRecordsWithoutDroppingSimilarRows() {
        val existing = original.copy(categoryId = null)
        val preview = CsvImport.preview(header + row("OCR berbeda"), assets, emptyList(), listOf(existing))
        assertEquals(1, preview.transactions.size)
        assertEquals(0, preview.duplicateCount)
        assertEquals(listOf(existing), preview.similarTransactions.values.toList())
    }
    @Test fun tenThousandCsvRowsAreIndexedAsOneBatch() {
        val csv = header + (1..10_000).joinToString("\n") { row("Pembayaran $it") }
        val preview = CsvImport.preview(csv, assets, emptyList(), emptyList())
        assertTrue(preview.errors.toString(), preview.errors.isEmpty())
        assertEquals(10_000, preview.transactions.size)
        assertEquals(9_999, preview.similarTransactions.size)
    }
}
