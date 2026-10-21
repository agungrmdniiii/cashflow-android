package com.cashflow.app.data.importer

import com.cashflow.app.data.model.*
import com.cashflow.app.data.repository.LedgerService
import java.util.UUID

data class CsvPreview(val transactions: List<Transaction>, val duplicateCount: Int, val errors: List<String>,
    val similarTransactions: Map<String, Transaction> = emptyMap())

object CsvImport {
    private val header = listOf("Tanggal", "Waktu", "Deskripsi", "Jenis", "Kategori", "Aset", "Aset Tujuan", "Nominal", "Catatan", "Metode Input")

    fun parse(content: String): List<List<String>> {
        require(content.length <= 4 * 1024 * 1024) { "CSV maksimal 4 MB." }
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var closed = false
        val source = content.removePrefix("\uFEFF")
        var index = 0
        fun endField() { row.add(field.toString()); field.setLength(0); closed = false }
        fun endRow() {
            endField()
            if (row.any { it.isNotEmpty() }) rows.add(row.toList())
            row.clear()
            require(rows.size <= 10001) { "CSV maksimal 10.000 transaksi." }
        }
        while (index < source.length) {
            val char = source[index++]
            if (quoted) {
                if (char == '"') {
                    if (index < source.length && source[index] == '"') { field.append('"'); index++ }
                    else { quoted = false; closed = true }
                } else field.append(char)
            } else when (char) {
                '"' -> { require(field.isEmpty() && !closed) { "Tanda kutip CSV tidak valid." }; quoted = true }
                ',' -> endField()
                '\n', '\r' -> { endRow(); if (char == '\r' && index < source.length && source[index] == '\n') index++ }
                else -> { require(!closed) { "Karakter setelah tanda kutip CSV tidak valid." }; field.append(char) }
            }
        }
        require(!quoted) { "Tanda kutip CSV belum ditutup." }
        if (field.isNotEmpty() || row.isNotEmpty() || closed) endRow()
        return rows
    }

    fun fingerprint(tx: Transaction): List<Any?> = listOf(tx.type, tx.amount, tx.description, tx.categoryId, tx.assetId,
        tx.destinationAssetId, tx.transactionDate, tx.transactionTime, tx.note)

    fun preview(content: String, assets: List<Asset>, categories: List<Category>, existing: List<Transaction>): CsvPreview {
        val rows = parse(content)
        require(rows.firstOrNull() == header) { "Gunakan CSV hasil ekspor Duit Aing dengan 10 kolom asli." }
        val seen = existing.map(::fingerprint).toMutableSet()
        val similarIndex = mutableMapOf<List<Any?>, Transaction>()
        existing.forEach { tx -> tx.similarityKey()?.let { similarIndex.putIfAbsent(it, tx) } }
        val similar = mutableMapOf<String, Transaction>()
        val transactions = mutableListOf<Transaction>()
        val errors = mutableListOf<String>()
        var duplicates = 0
        rows.drop(1).forEachIndexed { index, row ->
            runCatching {
                require(row.size == header.size) { "Jumlah kolom harus 10." }
                val type = when (row[3]) {
                    "Pemasukan", "income" -> "income"
                    "Pengeluaran", "expense" -> "expense"
                    "Transfer", "transfer" -> "transfer"
                    else -> error("Jenis ${row[3]} memerlukan cadangan JSON/ZIP untuk menjaga hubungan data.")
                }
                fun account(name: String) = assets.filter { it.isActive && it.name == name }.singleOrNull()?.id
                    ?: error("Rekening '$name' tidak ditemukan atau namanya ganda.")
                val category = if (type == "transfer" || row[4] == "-") null else
                    categories.filter { it.isActive && it.type == type && it.name == row[4] }.singleOrNull()?.id
                        ?: error("Kategori '${row[4]}' tidak ditemukan atau namanya ganda.")
                val tx = Transaction("csv_${UUID.randomUUID()}", type, row[7].toLongOrNull() ?: error("Nominal harus bilangan bulat."),
                    row[2], category, account(row[5]), if (type == "transfer") account(row[6]) else null,
                    row[0], row[1], row[8], inputMethod = row[9].ifBlank { "manual" })
                LedgerService.validateTransaction(tx).getOrThrow()
                if (seen.add(fingerprint(tx))) {
                    tx.similarityKey()?.let { key -> similarIndex.putIfAbsent(key, tx)?.let { similar[tx.id] = it } }
                    transactions.add(tx)
                } else duplicates++
            }.onFailure { errors.add("Baris ${index + 2}: ${it.message}") }
        }
        return CsvPreview(transactions, duplicates, errors, similar)
    }
}
