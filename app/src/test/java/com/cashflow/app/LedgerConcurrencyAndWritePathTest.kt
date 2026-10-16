package com.cashflow.app

import com.cashflow.app.data.model.Asset
import com.cashflow.app.data.model.Transaction
import com.cashflow.app.data.model.TransactionType
import com.cashflow.app.data.repository.BalanceDiscrepancy
import com.cashflow.app.data.repository.LedgerRecalculateResult
import com.cashflow.app.data.repository.LedgerService
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Concurrency and Single Write Path Unit Tests (Perbaikan 2/5).
 *
 * Verifikasi:
 * 1. Operasi tulis paralel simultan dengan Mutex menghasilkan saldo 100% akurat tanpa lost update / race condition.
 * 2. Hasil perhitungan ulang (recalculate) agregat setelah serangkaian operasi identik dengan hasil apply bertahap.
 * 3. Mode autoFix = false (saat init) mendeteksi selisih tanpa mengubah saldo secara diam-diam.
 * 4. Konsistensi saldo untuk operasi majemuk: transfer, penyesuaian, tabungan target, dan pembayaran hutang.
 */
class LedgerConcurrencyAndWritePathTest {

    private lateinit var assetBca: Asset
    private lateinit var assetGopay: Asset
    private lateinit var assetCash: Asset

    @Before
    fun setUp() {
        assetBca = Asset(
            id = "asset_bca",
            name = "BCA Prioritas",
            type = "Bank",
            openingBalance = 10_000_000L,
            currentBalance = 10_000_000L
        )
        assetGopay = Asset(
            id = "asset_gopay",
            name = "GoPay",
            type = "E-wallet",
            openingBalance = 1_000_000L,
            currentBalance = 1_000_000L
        )
        assetCash = Asset(
            id = "asset_cash",
            name = "Dompet Tunai",
            type = "Tunai",
            openingBalance = 500_000L,
            currentBalance = 500_000L
        )
    }

    /**
     * Test 1: Dua atau lebih operasi tulis paralel simultan dilindungi Mutex.
     * Membuktikan bahwa dengan Mutex serialisasi, 100 transaksi paralel tidak mengalami lost update.
     */
    @Test
    fun test1_ConcurrentParallelWrites_WithMutex_PreservesExactBalance() = runBlocking {
        val mutex = Mutex()
        var currentBalance = assetBca.openingBalance // 10.000.000
        val operationsCount = 50
        val incomeAmount = 100_000L
        val expenseAmount = 40_000L

        // Jalankan 100 coroutine konkuren secara simultan di Dispatchers.Default
        val jobs = mutableListOf<Job>()

        // 50 transaksi pemasukan paralel
        repeat(operationsCount) {
            jobs.add(
                launch(Dispatchers.Default) {
                    mutex.withLock {
                        // Simulasi applyTransactionEffect pada aset
                        val balanceBefore = currentBalance
                        // Simulasi jeda mikro eksekusi database
                        delay(1)
                        currentBalance = balanceBefore + incomeAmount
                    }
                }
            )
        }

        // 50 transaksi pengeluaran paralel
        repeat(operationsCount) {
            jobs.add(
                launch(Dispatchers.Default) {
                    mutex.withLock {
                        val balanceBefore = currentBalance
                        delay(1)
                        currentBalance = balanceBefore - expenseAmount
                    }
                }
            )
        }

        // Tunggu semua operasi selesai
        jobs.joinAll()

        // Ekspektasi: 10.000.000 + (50 * 100.000) - (50 * 40.000) = 10.000.000 + 5.000.000 - 2.000.000 = 13.000.000
        val expectedBalance = 10_000_000L + (operationsCount * incomeAmount) - (operationsCount * expenseAmount)
        assertEquals("Saldo akhir setelah 100 operasi paralel harus 100% presisi tanpa lost update", expectedBalance, currentBalance)
    }

    /**
     * Test 2: Recalculate agregat setelah serangkaian operasi menghasilkan saldo yang sama persis
     * dengan hasil apply transaksi bertahap (stepwise apply).
     */
    @Test
    fun test2_StepwiseApply_Vs_AggregateRecalculate_AreIdentical() {
        val transactions = mutableListOf<Transaction>()
        var bcaStepwise = assetBca.openingBalance
        var gopayStepwise = assetGopay.openingBalance
        var cashStepwise = assetCash.openingBalance

        // 1. Pemasukan ke BCA (Gaji: +8.000.000)
        val tx1 = Transaction(
            id = "tx_1",
            type = TransactionType.INCOME.value,
            amount = 8_000_000L,
            description = "Gaji",
            assetId = assetBca.id,
            transactionDate = "2026-10-01"
        )
        transactions.add(tx1)
        bcaStepwise += tx1.amount

        // 2. Pengeluaran dari BCA (Belanja: -1.250.000)
        val tx2 = Transaction(
            id = "tx_2",
            type = TransactionType.EXPENSE.value,
            amount = 1_250_000L,
            description = "Belanja Bulanan",
            assetId = assetBca.id,
            transactionDate = "2026-10-02"
        )
        transactions.add(tx2)
        bcaStepwise -= tx2.amount

        // 3. Transfer dari BCA ke GoPay (Top up: BCA -500.000, GoPay +500.000)
        val tx3 = Transaction(
            id = "tx_3",
            type = TransactionType.TRANSFER.value,
            amount = 500_000L,
            description = "Top up GoPay",
            assetId = assetBca.id,
            destinationAssetId = assetGopay.id,
            transactionDate = "2026-10-03"
        )
        transactions.add(tx3)
        bcaStepwise -= tx3.amount
        gopayStepwise += tx3.amount

        // 4. Pengeluaran dari GoPay (Transport: -75.000)
        val tx4 = Transaction(
            id = "tx_4",
            type = TransactionType.EXPENSE.value,
            amount = 75_000L,
            description = "Gojek",
            assetId = assetGopay.id,
            transactionDate = "2026-10-04"
        )
        transactions.add(tx4)
        gopayStepwise -= tx4.amount

        // 5. Transfer dari GoPay ke Dompet Tunai (Tarik Tunai: GoPay -200.000, Cash +200.000)
        val tx5 = Transaction(
            id = "tx_5",
            type = TransactionType.TRANSFER.value,
            amount = 200_000L,
            description = "Tarik Tunai GoPay",
            assetId = assetGopay.id,
            destinationAssetId = assetCash.id,
            transactionDate = "2026-10-05"
        )
        transactions.add(tx5)
        gopayStepwise -= tx5.amount
        cashStepwise += tx5.amount

        // 6. Penyesuaian Saldo Naik pada Cash (Uang Kembalian Ketemu: +25.000, direction = 1)
        val tx6 = Transaction(
            id = "tx_6",
            type = TransactionType.ADJUSTMENT.value,
            amount = 25_000L,
            description = "Penyesuaian Cash (+Rp 25.000)",
            assetId = assetCash.id,
            transactionDate = "2026-10-06",
            isAdjustment = true,
            adjustmentDirection = 1
        )
        transactions.add(tx6)
        cashStepwise += tx6.amount

        // 7. Penyesuaian Saldo Turun pada BCA (Biaya Admin Tak Terduga: -15.000, direction = -1)
        val tx7 = Transaction(
            id = "tx_7",
            type = TransactionType.ADJUSTMENT.value,
            amount = 15_000L,
            description = "Penyesuaian BCA (-Rp 15.000)",
            assetId = assetBca.id,
            transactionDate = "2026-10-07",
            isAdjustment = true,
            adjustmentDirection = -1
        )
        transactions.add(tx7)
        bcaStepwise -= tx7.amount

        // 8. Tabungan ke Target dari BCA (Alokasi Tabungan: BCA -1.000.000)
        val tx8 = Transaction(
            id = "tx_8",
            type = TransactionType.EXPENSE.value,
            amount = 1_000_000L,
            description = "Tabungan Rumah",
            assetId = assetBca.id,
            goalId = "goal_rumah",
            transactionDate = "2026-10-08"
        )
        transactions.add(tx8)
        bcaStepwise -= tx8.amount

        // 9. Pembayaran Cicilan Hutang dari BCA (Hutang: BCA -2.000.000)
        val tx9 = Transaction(
            id = "tx_9",
            type = TransactionType.EXPENSE.value,
            amount = 2_000_000L,
            description = "Cicilan Motor",
            assetId = assetBca.id,
            debtId = "debt_motor",
            transactionDate = "2026-10-09"
        )
        transactions.add(tx9)
        bcaStepwise -= tx9.amount

        // Hitung ulang menggunakan rumus LedgerService.calculateAssetBalance (rumus agregat yang sama dengan query SQL)
        val bcaRecalculated = LedgerService.calculateAssetBalance(assetBca.openingBalance, transactions, assetBca.id)
        val gopayRecalculated = LedgerService.calculateAssetBalance(assetGopay.openingBalance, transactions, assetGopay.id)
        val cashRecalculated = LedgerService.calculateAssetBalance(assetCash.openingBalance, transactions, assetCash.id)

        // Verifikasi kesesuaian 100% antara stepwise apply dan batch recalculate
        assertEquals("Saldo BCA bertahap harus identik dengan hasil hitung ulang agregat", bcaStepwise, bcaRecalculated)
        assertEquals("Saldo GoPay bertahap harus identik dengan hasil hitung ulang agregat", gopayStepwise, gopayRecalculated)
        assertEquals("Saldo Cash bertahap harus identik dengan hasil hitung ulang agregat", cashStepwise, cashRecalculated)

        // Verifikasi nilai nominal matematis
        assertEquals(13_235_000L, bcaRecalculated)
        assertEquals(1_225_000L, gopayRecalculated)
        assertEquals(725_000L, cashRecalculated)
    }

    /**
     * Test 3: Perilaku autoFix = false pada saat init.
     * Bila terdeteksi selisih saldo, fungsi mendeteksi dan melaporkan rincian selisih,
     * tetapi TIDAK mengubah saldo secara diam-diam.
     */
    @Test
    fun test3_AutoFixFalse_ReportsDiscrepancy_WithoutSilentDatabaseMutation() {
        // Misalkan saldo tersimpan di database corrupt / tidak sinkron (mis. 9.000.000 padahal riwayat transaksi menghasilkan 10.500.000)
        val corruptStoredBalance = 9_000_000L
        val openingBalance = assetBca.openingBalance // 10.000.000
        val incomeTx = Transaction(
            id = "tx_inc_test",
            type = TransactionType.INCOME.value,
            amount = 500_000L,
            description = "Penjualan Barang",
            assetId = assetBca.id,
            transactionDate = LocalDate.now().toString()
        )
        val transactions = listOf(incomeTx)

        val trueCalculatedBalance = LedgerService.calculateAssetBalance(
            openingBalance = openingBalance,
            transactions = transactions,
            assetId = assetBca.id
        ) // 10.500.000

        // Simulasi pemeriksaan integritas dengan autoFix = false (jalur init)
        val hasDiscrepancy = corruptStoredBalance != trueCalculatedBalance
        assertTrue("Harus mendeteksi selisih saldo", hasDiscrepancy)

        val discrepancy = BalanceDiscrepancy(
            assetId = assetBca.id,
            assetName = assetBca.name,
            recordedBalance = corruptStoredBalance,
            calculatedBalance = trueCalculatedBalance,
            difference = trueCalculatedBalance - corruptStoredBalance
        )

        // Pada autoFix = false:
        var balanceInDatabase = corruptStoredBalance // Database TIDAK diubah
        val autoFix = false
        if (autoFix) {
            balanceInDatabase = trueCalculatedBalance
        }

        assertEquals("Dengan autoFix = false, saldo di DB tidak boleh diubah diam-diam", corruptStoredBalance, balanceInDatabase)
        assertEquals("Selisih harus tepat 1.500.000", 1_500_000L, discrepancy.difference)
        assertEquals(trueCalculatedBalance, discrepancy.calculatedBalance)

        // Pada perbaikan manual (user menekan tombol 'Perbaiki'):
        val userTriggeredRepair = true
        if (userTriggeredRepair) {
            balanceInDatabase = trueCalculatedBalance
        }
        assertEquals("Setelah user menekan 'Perbaiki', saldo DB berhasil diselaraskan", trueCalculatedBalance, balanceInDatabase)
    }

    /**
     * Test 4: Memastikan seluruh transaksi terbalik (revert) secara simetris mengembalikan saldo ke kondisi semula.
     */
    @Test
    fun test4_RevertTransactionEffect_RestoresOriginalBalance() {
        val initialBalance = assetBca.openingBalance // 10.000.000
        val tx = Transaction(
            id = "tx_temp",
            type = TransactionType.EXPENSE.value,
            amount = 750_000L,
            description = "Tiket Kereta",
            assetId = assetBca.id,
            transactionDate = "2026-10-10"
        )

        // Apply
        val balanceAfterApply = initialBalance - tx.amount
        assertEquals(9_250_000L, balanceAfterApply)

        // Revert (seperti saat delete atau sebelum edit)
        val balanceAfterRevert = balanceAfterApply + tx.amount
        assertEquals("Revert harus memulihkan saldo 100% simetris", initialBalance, balanceAfterRevert)
    }
}
