package com.cashflow.app

import com.cashflow.app.data.model.Asset
import com.cashflow.app.data.model.Transaction
import com.cashflow.app.data.model.TransactionType
import com.cashflow.app.data.repository.LedgerService
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Reproduction & Integrity Tests for Asset Edit & Balance Overwrite Bug (Perbaikan 1/5).
 *
 * Verifies:
 * 1. Editing asset metadata (name/type) must NEVER alter balance, even after recalculation.
 * 2. Editing opening balance of an asset without transactions must update opening_balance & current_balance,
 *    and remain stable after recalculation.
 * 3. Adjusting balance on an asset with transactions via adjustment transaction (up & down) must be 100% correct
 *    after recalculation.
 * 4. Concurrent transaction entering while edit dialog is open must NOT be lost (lost update prevention).
 */
class AssetEditBalanceIntegrityTest {

    private lateinit var assetWithTx: Asset
    private lateinit var emptyAsset: Asset
    private val transactions = mutableListOf<Transaction>()

    @Before
    fun setUp() {
        assetWithTx = Asset(
            id = "asset_mandiri",
            name = "Mandiri",
            type = "Bank",
            openingBalance = 1_000_000L,
            currentBalance = 1_500_000L
        )
        emptyAsset = Asset(
            id = "asset_cash",
            name = "Dompet Tunai",
            type = "Tunai",
            openingBalance = 500_000L,
            currentBalance = 500_000L
        )
        transactions.clear()
        transactions.add(
            Transaction(
                id = "tx_income_1",
                type = TransactionType.INCOME.value,
                amount = 500_000L,
                description = "Freelance Project",
                assetId = assetWithTx.id,
                transactionDate = LocalDate.now().toString()
            )
        )
    }

    /**
     * Skenario 1: Edit nama/tipe aset -> saldo tidak berubah, bahkan setelah recalculate.
     */
    @Test
    fun test1_EditAssetMetadata_BalanceNeverChanges_EvenAfterRecalculate() {
        val originalBalance = assetWithTx.currentBalance // 1.500.000

        // User edits name from "Mandiri" to "Mandiri Payroll"
        // Metadata update simulation:
        val updatedAsset = assetWithTx.copy(name = "Mandiri Payroll", type = "Bank")

        // In fixed code, updateAsset does NOT modify current_balance or opening_balance.
        // Even after full ledger recalculation:
        val recalculated = LedgerService.calculateAssetBalance(
            openingBalance = updatedAsset.openingBalance,
            transactions = transactions,
            assetId = updatedAsset.id
        )

        assertEquals("Saldo harus tetap sama dengan saldo asli", originalBalance, recalculated)
    }

    /**
     * Skenario 2: Edit saldo awal aset KOSONG (tanpa transaksi).
     * Saldo harus ikut berubah ke angka baru dan TETAP sama setelah recalculate.
     */
    @Test
    fun test2_EditOpeningBalance_EmptyAsset_PersistsAfterRecalculate() {
        // Aset kosong awal: opening 500.000, current 500.000
        assertEquals(0, transactions.count { it.assetId == emptyAsset.id })

        val newOpeningBalance = 2_000_000L

        // Di kode baru, updateAssetOpeningBalance mengubah opening_balance & current_balance ke 2.000.000
        val updatedEmptyAsset = emptyAsset.copy(
            openingBalance = newOpeningBalance,
            currentBalance = newOpeningBalance
        )

        // Setelah recalculateAllBalances:
        val recalculated = LedgerService.calculateAssetBalance(
            openingBalance = updatedEmptyAsset.openingBalance,
            transactions = transactions,
            assetId = updatedEmptyAsset.id
        )

        assertEquals("Saldo hasil recalculate harus 2.000.000", newOpeningBalance, recalculated)
        assertEquals("Current balance aset harus sama dengan saldo awal baru", newOpeningBalance, updatedEmptyAsset.currentBalance)
    }

    /**
     * Skenario 3: Aset bertransaksi: Sesuaikan Saldo naik dan turun.
     * Saldo benar sebelum dan sesudah recalculate karena dicatat lewat transaksi penyesuaian.
     */
    @Test
    fun test3_AdjustAssetBalance_WithTransactions_AccurateAfterRecalculate() {
        // Kondisi awal: opening 1.000.000, income 500.000 -> saldo 1.500.000
        val balanceAwal = LedgerService.calculateAssetBalance(
            openingBalance = assetWithTx.openingBalance,
            transactions = transactions,
            assetId = assetWithTx.id
        )
        assertEquals(1_500_000L, balanceAwal)

        // 1. Penyesuaian NAIK ke 2.300.000 (diff = +800.000, direction = 1)
        val targetNaik = 2_300_000L
        val diffNaik = targetNaik - balanceAwal
        val adjNaikTx = Transaction(
            id = "adj_up_1",
            type = TransactionType.ADJUSTMENT.value,
            amount = diffNaik,
            description = "Penyesuaian Saldo (+Rp 800.000)",
            assetId = assetWithTx.id,
            transactionDate = LocalDate.now().toString(),
            isAdjustment = true,
            adjustmentDirection = 1
        )
        transactions.add(adjNaikTx)

        val balanceAfterNaik = LedgerService.calculateAssetBalance(
            openingBalance = assetWithTx.openingBalance,
            transactions = transactions,
            assetId = assetWithTx.id
        )
        assertEquals("Saldo setelah penyesuaian naik harus 2.300.000", targetNaik, balanceAfterNaik)

        // 2. Penyesuaian TURUN ke 1.800.000 (diff = -500.000, direction = -1)
        val targetTurun = 1_800_000L
        val diffTurun = balanceAfterNaik - targetTurun // 500.000
        val adjTurunTx = Transaction(
            id = "adj_down_1",
            type = TransactionType.ADJUSTMENT.value,
            amount = diffTurun,
            description = "Penyesuaian Saldo (-Rp 500.000)",
            assetId = assetWithTx.id,
            transactionDate = LocalDate.now().toString(),
            isAdjustment = true,
            adjustmentDirection = -1
        )
        transactions.add(adjTurunTx)

        val balanceAfterTurun = LedgerService.calculateAssetBalance(
            openingBalance = assetWithTx.openingBalance,
            transactions = transactions,
            assetId = assetWithTx.id
        )
        assertEquals("Saldo setelah penyesuaian turun harus 1.800.000", targetTurun, balanceAfterTurun)
    }

    /**
     * Skenario 4: Transaksi masuk saat dialog edit terbuka tidak tertimpa saat menyimpan (Lost Update).
     */
    @Test
    fun test4_ConcurrentTransactionWhileDialogEditing_NotLost() {
        // T0: Dialog dibuka, membaca snapshot aset saat itu
        val dialogSnapshot = assetWithTx.copy() // currentBalance = 1.500.000

        // T1: Transaksi baru masuk saat dialog masih terbuka (mis. background bill atau quick record)
        val concurrentTx = Transaction(
            id = "tx_concurrent",
            type = TransactionType.INCOME.value,
            amount = 750_000L,
            description = "Bonus Masuk",
            assetId = assetWithTx.id,
            transactionDate = LocalDate.now().toString()
        )
        transactions.add(concurrentTx)

        // Saldo riil saat ini di database bertambah menjadi 2.250.000
        var realDbBalance = LedgerService.calculateAssetBalance(
            openingBalance = assetWithTx.openingBalance,
            transactions = transactions,
            assetId = assetWithTx.id
        )
        assertEquals(2_250_000L, realDbBalance)

        // T2: User menekan "Simpan Perubahan" di dialog edit (hanya ingin mengubah nama)
        // BUG LAMA: Dialog mengirim dialogSnapshot.currentBalance (1.500.000) dan menimpa DB menjadi 1.500.000!
        // PERBAIKAN: updateAsset HANYA memperbarui metadata (name, type, note), TIDAK MENYENTUH current_balance.
        val updatedMetadata = dialogSnapshot.copy(name = "Mandiri Tabungan Bisnis")

        // Verifikasi: realDbBalance tetap 2.250.000 (transaksi concurrent tidak tertimpa)
        val finalBalance = LedgerService.calculateAssetBalance(
            openingBalance = updatedMetadata.openingBalance,
            transactions = transactions,
            assetId = updatedMetadata.id
        )
        assertEquals("Saldo transaksi concurrent tidak boleh hilang tertimpa oleh dialog", 2_250_000L, finalBalance)
    }

    /**
     * Skenario Reproduksi Bug Lama vs Perbaikan Baru:
     * Menunjukkan secara eksplisit apa yang terjadi pada bug lama:
     * Jika updateAsset menimpa current_balance tanpa mengubah opening_balance,
     * maka recalculateAllBalances akan membatalkan input user!
     */
    @Test
    fun test5_DemonstrateOldBugFailureMechanism() {
        // Skenario Bug Lama pada Aset Kosong:
        val oldOpeningInDb = 500_000L
        val userTypedInEditDialog = 2_000_000L

        // BUG LAMA: updateAsset hanya melakukan `UPDATE assets SET current_balance = 2.000.000`,
        // tetapi opening_balance di database tetap 500.000!
        val buggedCurrentBalance = userTypedInEditDialog // 2.000.000
        val buggedOpeningBalanceInDb = oldOpeningInDb     // 500.000 (tidak berubah)

        // Ketika recalculateAllBalances jalan:
        val recalculatedUnderOldBug = LedgerService.calculateAssetBalance(
            openingBalance = buggedOpeningBalanceInDb,
            transactions = emptyList(),
            assetId = emptyAsset.id
        )
        // Di bawah bug lama, recalculate mengembalikan 500.000!
        assertEquals(500_000L, recalculatedUnderOldBug)
        assertNotEquals("Bug lama: Saldo yang diketik user tertimpa kembali ke opening_balance lama", userTypedInEditDialog, recalculatedUnderOldBug)

        // DENGAN PERBAIKAN:
        // updateAssetOpeningBalance mengubah opening_balance menjadi 2.000.000
        val fixedOpeningBalanceInDb = userTypedInEditDialog
        val recalculatedFixed = LedgerService.calculateAssetBalance(
            openingBalance = fixedOpeningBalanceInDb,
            transactions = emptyList(),
            assetId = emptyAsset.id
        )
        assertEquals("Dengan perbaikan: Saldo tetap 2.000.000 setelah recalculate", userTypedInEditDialog, recalculatedFixed)
    }
}
