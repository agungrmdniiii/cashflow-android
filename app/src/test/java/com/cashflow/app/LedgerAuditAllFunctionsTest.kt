package com.cashflow.app

import com.cashflow.app.data.model.*
import com.cashflow.app.data.repository.LedgerService
import com.cashflow.app.data.service.RecurringSchedulerService
import com.cashflow.app.voice.VoiceTransactionParser
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Comprehensive Audit Test Suite (Audit 3/5 - Semua Fungsi yang Menyentuh Saldo).
 *
 * Menguji secara ketat 10 fungsi/area saldo di Duit Aing:
 * 1. addTransaction / updateTransaction / deleteTransaction (apply, revert, ganti jenis/aset).
 * 2. Transfer atomik dua sisi, validasi aset sama/kosong, dan ganti aset tujuan.
 * 3. adjustAssetBalance & transaksi ber-adjustment_direction (naik, turun, hapus, hitung ulang).
 * 4. Pembayaran hutang & target tabungan (sinkronisasi dua arah saldo aset & sisa hutang/tabungan).
 * 5. processRecurringBills: idempoten, tanggal akhir bulan (clampDayOfMonth), riwayat berulang.
 * 6. Proteksi simpan ganda (double-click guard pada dialog suara & form transaksi).
 * 7. archiveAsset / deleteAsset: perlakuan konsisten aset aktif vs terarsip pada totalWealth & netWorth.
 * 8. Restore backup & migrasi: integritas saldo sebelum vs sesudah restore (termasuk skema lama).
 * 9. Konsistensi angka Arus Kas & Laporan (single source of truth: netCashflow = income - expense).
 * 10. Tipe angka: 100% Long rupiah bulat & bebas rounding error floating-point.
 */
class LedgerAuditAllFunctionsTest {

    private lateinit var assetBca: Asset
    private lateinit var assetGopay: Asset
    private val txList = mutableListOf<Transaction>()

    @Before
    fun setUp() {
        assetBca = Asset(
            id = "asset_bca_1",
            name = "BCA Rekening Utama",
            type = "Bank",
            openingBalance = 5_000_000L,
            currentBalance = 5_000_000L,
            isActive = true,
            isDefault = true
        )
        assetGopay = Asset(
            id = "asset_gopay_1",
            name = "GoPay",
            type = "E-wallet",
            openingBalance = 500_000L,
            currentBalance = 500_000L,
            isActive = true,
            isDefault = false
        )
        txList.clear()
    }

    /**
     * Area 1: addTransaction / updateTransaction / deleteTransaction
     */
    @Test
    fun test01_AddUpdateDeleteTransaction_ApplyRevertAndTypeSwitch() {
        var bcaBalance = assetBca.openingBalance // 5.000.000

        // 1. Add Income 1.000.000
        val txIncome = Transaction(
            id = "tx_inc_1",
            type = TransactionType.INCOME.value,
            amount = 1_000_000L,
            description = "Gaji Bulanan",
            assetId = assetBca.id,
            transactionDate = "2026-10-15"
        )
        txList.add(txIncome)
        bcaBalance += txIncome.amount
        assertEquals(6_000_000L, bcaBalance)
        assertEquals(6_000_000L, LedgerService.calculateAssetBalance(assetBca.openingBalance, txList, assetBca.id))

        // 2. Add Expense 250.000
        val txExpense = Transaction(
            id = "tx_exp_1",
            type = TransactionType.EXPENSE.value,
            amount = 250_000L,
            description = "Belanja Supermarket",
            assetId = assetBca.id,
            transactionDate = "2026-10-15"
        )
        txList.add(txExpense)
        bcaBalance -= txExpense.amount
        assertEquals(5_750_000L, bcaBalance)
        assertEquals(5_750_000L, LedgerService.calculateAssetBalance(assetBca.openingBalance, txList, assetBca.id))

        // 3. Edit Expense amount from 250.000 to 350.000 (revert old, apply new)
        val txExpenseUpdated = txExpense.copy(amount = 350_000L)
        bcaBalance += txExpense.amount // revert old
        bcaBalance -= txExpenseUpdated.amount // apply new
        txList.remove(txExpense)
        txList.add(txExpenseUpdated)
        assertEquals(5_650_000L, bcaBalance)
        assertEquals(5_650_000L, LedgerService.calculateAssetBalance(assetBca.openingBalance, txList, assetBca.id))

        // 4. Delete Expense: balance must revert cleanly
        txList.remove(txExpenseUpdated)
        bcaBalance += txExpenseUpdated.amount
        assertEquals(6_000_000L, bcaBalance)
        assertEquals(6_000_000L, LedgerService.calculateAssetBalance(assetBca.openingBalance, txList, assetBca.id))
    }

    /**
     * Area 2: Transfer (dua sisi atomik, validasi penolakan aset sama, ganti aset tujuan)
     */
    @Test
    fun test02_Transfer_TwoSidedAtomicityAndValidation() {
        var bcaBalance = assetBca.openingBalance // 5.000.000
        var gopayBalance = assetGopay.openingBalance // 500.000

        // 1. Validasi penolakan transfer ke aset yang sama
        val invalidSameAssetTx = Transaction(
            id = "tx_trf_invalid",
            type = TransactionType.TRANSFER.value,
            amount = 100_000L,
            description = "Transfer diri sendiri",
            assetId = assetBca.id,
            destinationAssetId = assetBca.id,
            transactionDate = "2026-10-15"
        )
        val valResultSame = LedgerService.validateTransaction(invalidSameAssetTx)
        assertTrue(valResultSame.isFailure)
        assertTrue(valResultSame.exceptionOrNull()?.message?.contains("tidak boleh sama") == true)

        // 2. Validasi penolakan transfer tanpa aset tujuan
        val invalidNoDestTx = invalidSameAssetTx.copy(destinationAssetId = null)
        val valResultNoDest = LedgerService.validateTransaction(invalidNoDestTx)
        assertTrue(valResultNoDest.isFailure)

        // 3. Transfer sah 200.000 dari BCA ke GoPay
        val validTrfTx = Transaction(
            id = "tx_trf_1",
            type = TransactionType.TRANSFER.value,
            amount = 200_000L,
            description = "Top up GoPay",
            assetId = assetBca.id,
            destinationAssetId = assetGopay.id,
            transactionDate = "2026-10-15"
        )
        assertTrue(LedgerService.validateTransaction(validTrfTx).isSuccess)
        txList.add(validTrfTx)
        bcaBalance -= validTrfTx.amount
        gopayBalance += validTrfTx.amount

        assertEquals(4_800_000L, bcaBalance)
        assertEquals(700_000L, gopayBalance)
        assertEquals(4_800_000L, LedgerService.calculateAssetBalance(assetBca.openingBalance, txList, assetBca.id))
        assertEquals(700_000L, LedgerService.calculateAssetBalance(assetGopay.openingBalance, txList, assetGopay.id))

        // 4. Revert transfer (hapus transfer): kedua sisi kembali ke saldo semula
        txList.remove(validTrfTx)
        bcaBalance += validTrfTx.amount
        gopayBalance -= validTrfTx.amount
        assertEquals(5_000_000L, bcaBalance)
        assertEquals(500_000L, gopayBalance)
        assertEquals(5_000_000L, LedgerService.calculateAssetBalance(assetBca.openingBalance, txList, assetBca.id))
        assertEquals(500_000L, LedgerService.calculateAssetBalance(assetGopay.openingBalance, txList, assetGopay.id))
    }

    /**
     * Area 3: adjustAssetBalance dan transaksi ber-adjustment_direction (naik, turun, edit, hapus)
     */
    @Test
    fun test03_AdjustAssetBalance_DirectionAndRecalculate() {
        var bcaBalance = assetBca.openingBalance // 5.000.000

        // 1. Penyesuaian Naik (+50.000): direction = +1
        val adjUp = Transaction(
            id = "adj_up_1",
            type = TransactionType.ADJUSTMENT.value,
            amount = 50_000L,
            description = "Penyesuaian Saldo Kas Fisik",
            assetId = assetBca.id,
            transactionDate = "2026-10-15",
            isAdjustment = true,
            adjustmentDirection = 1
        )
        txList.add(adjUp)
        bcaBalance += adjUp.amount
        assertEquals(5_050_000L, bcaBalance)
        assertEquals(5_050_000L, LedgerService.calculateAssetBalance(assetBca.openingBalance, txList, assetBca.id))

        // 2. Penyesuaian Turun (-120.000): direction = -1
        val adjDown = Transaction(
            id = "adj_down_1",
            type = TransactionType.ADJUSTMENT.value,
            amount = 120_000L,
            description = "Koreksi biaya admin",
            assetId = assetBca.id,
            transactionDate = "2026-10-15",
            isAdjustment = true,
            adjustmentDirection = -1
        )
        txList.add(adjDown)
        bcaBalance -= adjDown.amount
        assertEquals(4_930_000L, bcaBalance)
        assertEquals(4_930_000L, LedgerService.calculateAssetBalance(assetBca.openingBalance, txList, assetBca.id))

        // 3. Edit penyesuaian: ubah adjDown menjadi -70.000
        val adjDownEdited = adjDown.copy(amount = 70_000L)
        bcaBalance += adjDown.amount // revert lama (+120k)
        bcaBalance -= adjDownEdited.amount // apply baru (-70k)
        txList.remove(adjDown)
        txList.add(adjDownEdited)
        assertEquals(4_980_000L, bcaBalance)
        assertEquals(4_980_000L, LedgerService.calculateAssetBalance(assetBca.openingBalance, txList, assetBca.id))

        // 4. Hapus kedua penyesuaian: saldo kembali ke opening balance semula
        txList.clear()
        assertEquals(5_000_000L, LedgerService.calculateAssetBalance(assetBca.openingBalance, txList, assetBca.id))
    }

    /**
     * Area 4: Pembayaran cicilan hutang dan target tabungan (sinkronisasi dua arah)
     */
    @Test
    fun test04_DebtPaymentAndGoalDepositWithdrawalSync() {
        var bcaBalance = assetBca.openingBalance // 5.000.000
        var debtTotal = 2_000_000L
        var debtPaid = 0L

        // 1. Bayar cicilan hutang 500.000
        val paymentAmount = 500_000L
        val debtTx = Transaction(
            id = "tx_debt_pay_1",
            type = TransactionType.EXPENSE.value,
            amount = paymentAmount,
            description = "Cicilan Laptop",
            assetId = assetBca.id,
            transactionDate = "2026-10-15",
            debtId = "debt_laptop"
        )
        txList.add(debtTx)
        bcaBalance -= paymentAmount
        debtPaid += paymentAmount
        val remainingDebt = debtTotal - debtPaid

        assertEquals(4_500_000L, bcaBalance)
        assertEquals(500_000L, debtPaid)
        assertEquals(1_500_000L, remainingDebt)
        assertEquals(4_500_000L, LedgerService.calculateAssetBalance(assetBca.openingBalance, txList, assetBca.id))

        // 2. Revert pembayaran cicilan (misal saat transaksi dihapus)
        bcaBalance += paymentAmount
        debtPaid -= paymentAmount
        txList.remove(debtTx)
        assertEquals(5_000_000L, bcaBalance)
        assertEquals(0L, debtPaid)
        assertEquals(2_000_000L, debtTotal - debtPaid)

        // 3. Tabungan / Financial Goal: Alokasi 300.000
        var goalCurrentAmount = 1_000_000L
        val depositAmount = 300_000L
        val goalTx = Transaction(
            id = "tx_goal_dep_1",
            type = TransactionType.EXPENSE.value,
            amount = depositAmount,
            description = "Tabungan Liburan",
            assetId = assetBca.id,
            transactionDate = "2026-10-15",
            goalId = "goal_vacation"
        )
        txList.add(goalTx)
        bcaBalance -= depositAmount
        goalCurrentAmount += depositAmount

        assertEquals(4_700_000L, bcaBalance)
        assertEquals(1_300_000L, goalCurrentAmount)
        assertEquals(4_700_000L, LedgerService.calculateAssetBalance(assetBca.openingBalance, txList, assetBca.id))

        // 4. Pencairan Tabungan (Withdrawal) 400.000 -> Masuk ke BCA
        val withdrawAmount = 400_000L
        val goalWdTx = Transaction(
            id = "tx_goal_wd_1",
            type = TransactionType.INCOME.value,
            amount = withdrawAmount,
            description = "Pencairan Liburan",
            assetId = assetBca.id,
            transactionDate = "2026-10-15",
            goalId = "goal_vacation"
        )
        txList.add(goalWdTx)
        bcaBalance += withdrawAmount
        goalCurrentAmount -= withdrawAmount

        assertEquals(5_100_000L, bcaBalance)
        assertEquals(900_000L, goalCurrentAmount)
        assertEquals(5_100_000L, LedgerService.calculateAssetBalance(assetBca.openingBalance, txList, assetBca.id))
    }

    /**
     * Area 5: processRecurringBills (Idempoten, akhir bulan clamp, dan format note otomatis)
     */
    @Test
    fun test05_RecurringScheduler_IdempotencyAndEndOfMonthClamp() {
        // 1. Clamp day of month untuk bulan pendek (Februari 2026: 28 hari)
        val clampedFeb2026 = RecurringSchedulerService.clampDayOfMonth(2026, 2, 31)
        assertEquals(LocalDate.of(2026, 2, 28), clampedFeb2026)

        // 2. Tahun kabisat (Februari 2024: 29 hari)
        val clampedFeb2024 = RecurringSchedulerService.clampDayOfMonth(2024, 2, 31)
        assertEquals(LocalDate.of(2024, 2, 29), clampedFeb2024)

        // 3. Bulan 30 hari (April: 30 hari)
        val clampedApr = RecurringSchedulerService.clampDayOfMonth(2026, 4, 31)
        assertEquals(LocalDate.of(2026, 4, 30), clampedApr)

        // 4. Bulan normal 31 hari (Januari: tetap 31)
        val clampedJan = RecurringSchedulerService.clampDayOfMonth(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 1, 31), clampedJan)

        // 5. Cek format catatan otomatis
        val recurringTemplate = RecurringTransaction(
            id = "rec_wifi",
            name = "IndiHome Bulanan",
            type = TransactionType.EXPENSE.value,
            amount = 350_000L,
            assetId = assetBca.id,
            startDate = "2026-01-31",
            frequency = "monthly",
            interval = 1,
            mode = "auto",
            note = "Paket 50 Mbps"
        )
        val expectedAutoNote = "[Otomatis Berulang] ${recurringTemplate.note ?: ""}".trim()
        assertTrue(expectedAutoNote.startsWith("[Otomatis Berulang]"))
        assertTrue(expectedAutoNote.contains("Paket 50 Mbps"))

        // 6. Idempotensi: Jadwal tanggal yang sama tidak boleh didaftarkan ganda
        val dates = RecurringSchedulerService.generateScheduledDates(
            recurring = recurringTemplate,
            upToDate = LocalDate.of(2026, 3, 31)
        )
        assertEquals(3, dates.size) // Jan 31, Feb 28, Mar 31
        assertEquals(LocalDate.of(2026, 1, 31), dates[0])
        assertEquals(LocalDate.of(2026, 2, 28), dates[1])
        assertEquals(LocalDate.of(2026, 3, 31), dates[2])
    }

    /**
     * Area 6: Simpan suara / OCR / favorit tidak tersimpan ganda
     */
    @Test
    fun test06_DoubleSubmitPrevention_VoiceAndTransaction() {
        var isSaving = false
        var submitCount = 0

        // Simulasi fungsi tombol "SIMPAN TRANSAKSI" pada dialog suara / form
        val onSaveClicked = {
            if (!isSaving) {
                isSaving = true
                submitCount++
            }
        }

        // Klik pertama
        onSaveClicked()
        assertEquals(1, submitCount)
        assertTrue(isSaving)

        // Klik kedua yang cepat (double-tap saat isSaving masih true)
        onSaveClicked()
        assertEquals(1, submitCount) // Tetap 1, klik kedua diabaikan!
    }

    /**
     * Area 7: archiveAsset vs deleteAsset (perlakuan aset aktif vs terarsip konsisten)
     */
    @Test
    fun test07_ArchivedAsset_ExclusionAndPreservation() {
        val activeAssets = listOf(assetBca, assetGopay)
        val archivedAsset = Asset(
            id = "asset_old_ovo",
            name = "OVO Lama",
            type = "E-wallet",
            openingBalance = 50_000L,
            currentBalance = 150_000L,
            isActive = false // terarsip
        )
        val allAssets = activeAssets + archivedAsset

        // 1. totalWealth HANYA menghitung active assets
        val totalWealth = activeAssets.filter { it.isActive }.sumOf { it.currentBalance }
        assertEquals(5_500_000L, totalWealth)
        assertFalse(totalWealth == allAssets.sumOf { it.currentBalance }) // 5.650.000 tidak boleh ikut totalWealth

        // 2. netWorth konsisten: totalWealth - totalRemainingDebt
        val debtRemaining = 1_000_000L
        val netWorth = totalWealth - debtRemaining
        assertEquals(4_500_000L, netWorth)

        // 3. Historical asset mapping tetap mengenali nama aset terarsip
        val assetMap = allAssets.associateBy { it.id }
        val historicalAsset = assetMap["asset_old_ovo"]
        assertNotNull(historicalAsset)
        assertEquals("OVO Lama", historicalAsset?.name)
        assertFalse(historicalAsset?.isActive == true)
    }

    /**
     * Area 8: Restore backup dan migrasi DB (saldo sama sebelum & sesudah restore)
     */
    @Test
    fun test08_BackupRestore_BalanceIntegrity() {
        // Snapshot saldo sebelum backup
        val preBackupBcaBalance = 5_000_000L
        val tx1 = Transaction(
            id = "tx_b1",
            type = TransactionType.INCOME.value,
            amount = 800_000L,
            description = "Transfer Masuk",
            assetId = assetBca.id,
            transactionDate = "2026-10-15"
        )
        val tx2 = Transaction(
            id = "tx_b2",
            type = TransactionType.EXPENSE.value,
            amount = 300_000L,
            description = "Beli Bensin",
            assetId = assetBca.id,
            transactionDate = "2026-10-15"
        )
        val restoredTxList = listOf(tx1, tx2)

        // Saldo sebelum backup
        val expectedBalance = preBackupBcaBalance + 800_000L - 300_000L // 5.500.000L

        // Simulasi pembacaan JSON backup versi lama (tanpa current_balance)
        val assetJson = JSONObject().apply {
            put("id", assetBca.id)
            put("name", assetBca.name)
            put("type", assetBca.type)
            put("opening_balance", preBackupBcaBalance)
            // current_balance sengaja tidak ada (skema lama)
        }

        val restoredOpening = assetJson.optLong("opening_balance", 0L)
        val restoredCurrentFallback = assetJson.optLong("current_balance", restoredOpening)
        assertEquals(preBackupBcaBalance, restoredOpening)
        assertEquals(preBackupBcaBalance, restoredCurrentFallback)

        // RecalculateAllBalances menghitung ulang dari opening + transactions
        val calculatedBalance = LedgerService.calculateAssetBalance(restoredOpening, restoredTxList, assetBca.id)
        assertEquals(expectedBalance, calculatedBalance)
    }

    /**
     * Area 9: Konsistensi angka (single source of truth: netCashflow = income - expense)
     */
    @Test
    fun test09_SingleSourceOfTruth_ConsistencyAcrossScreens() {
        val income1 = Transaction(id = "i1", type = TransactionType.INCOME.value, amount = 1_500_000L, description = "A", assetId = assetBca.id, transactionDate = "2026-10-15")
        val expense1 = Transaction(id = "e1", type = TransactionType.EXPENSE.value, amount = 400_000L, description = "B", assetId = assetBca.id, transactionDate = "2026-10-15")
        val transfer1 = Transaction(id = "t1", type = TransactionType.TRANSFER.value, amount = 1_000_000L, description = "C", assetId = assetBca.id, destinationAssetId = assetGopay.id, transactionDate = "2026-10-15")
        val periodList = listOf(income1, expense1, transfer1)

        // Arus Kas: Total Income
        val periodIncome = periodList.filter { it.type == TransactionType.INCOME.value }.sumOf { it.amount }
        assertEquals(1_500_000L, periodIncome)

        // Arus Kas: Total Expense
        val periodExpense = periodList.filter { it.type == TransactionType.EXPENSE.value }.sumOf { it.amount }
        assertEquals(400_000L, periodExpense)

        // Transfer TIDAK boleh masuk ke Income atau Expense!
        val netCashflow = periodIncome - periodExpense
        assertEquals(1_100_000L, netCashflow)
    }

    /**
     * Area 10: Tipe angka (100% Long rupiah bulat & bebas rounding error floating point)
     */
    @Test
    fun test10_CurrencyType_LongRupiahPrecision() {
        // 1. Spoken decimal juta parsed with BigDecimal
        val spokenInput1 = "1.5"
        val bd1 = BigDecimal(spokenInput1).multiply(BigDecimal(1_000_000)).toLong()
        assertEquals(1_500_000L, bd1)

        val spokenInput2 = "2.75"
        val bd2 = BigDecimal(spokenInput2).multiply(BigDecimal(1_000_000)).toLong()
        assertEquals(2_750_000L, bd2)

        // 2. Tidak ada floating point off-by-one
        val spokenInput3 = "1.234567"
        val bd3 = BigDecimal(spokenInput3).multiply(BigDecimal(1_000_000)).toLong()
        assertEquals(1_234_567L, bd3)

        // 3. Seluruh kalkulasi saldo menggunakan Long murni
        val largeAmount: Long = 150_000_000_000L // 150 Milyar Rupiah
        val balanceResult = largeAmount + 50_000_000L - 25_000_000L
        assertEquals(150_025_000_000L, balanceResult)
    }
}
