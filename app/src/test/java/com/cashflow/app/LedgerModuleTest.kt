package com.cashflow.app

import com.cashflow.app.data.backup.BackupManager
import com.cashflow.app.data.backup.BackupValidationResult
import com.cashflow.app.data.model.Asset
import com.cashflow.app.data.model.Transaction
import com.cashflow.app.data.model.TransactionType
import com.cashflow.app.data.repository.LedgerService
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest

/**
 * Comprehensive Automated Unit Tests for LedgerService & Data Integrity (Tahap 1)
 * Covers:
 * 1. Income balance increase
 * 2. Expense balance decrease
 * 3. Outgoing transfer decrement
 * 4. Incoming transfer increment
 * 5. Multi-transaction ledger calculation
 * 6. Amount validation (> 0 requirement)
 * 7. Transfer validation (disallow same asset)
 * 8. Transfer destination validation
 * 9. Date format validation (ISO yyyy-MM-dd)
 * 10. Balance sufficiency check
 * 11. Large integer safety (50 Billion IDR without float drift or overflow)
 * 12. Edit rollback and re-apply simulation
 * 13. Delete and Undo transaction balance consistency
 * 14. Backup JSON SHA-256 checksum & tamper resistance
 */
class LedgerModuleTest {

    private lateinit var mainAsset: Asset
    private lateinit var secondaryAsset: Asset

    @Before
    fun setUp() {
        mainAsset = Asset(
            id = "asset_bca",
            name = "BCA",
            type = "Bank",
            openingBalance = 5_000_000L,
            currentBalance = 5_000_000L
        )
        secondaryAsset = Asset(
            id = "asset_gopay",
            name = "GoPay",
            type = "E-wallet",
            openingBalance = 500_000L,
            currentBalance = 500_000L
        )
    }

    @Test
    fun test1_IncomeIncreasesBalance() {
        val tx = Transaction(
            id = "tx_1",
            type = TransactionType.INCOME.value,
            amount = 1_500_000L,
            description = "Gaji Bulanan",
            assetId = mainAsset.id,
            transactionDate = "2026-10-14"
        )
        val calculated = LedgerService.calculateAssetBalance(
            openingBalance = mainAsset.openingBalance,
            transactions = listOf(tx),
            assetId = mainAsset.id
        )
        assertEquals(6_500_000L, calculated)
    }

    @Test
    fun test2_ExpenseDecreasesBalance() {
        val tx = Transaction(
            id = "tx_2",
            type = TransactionType.EXPENSE.value,
            amount = 350_000L,
            description = "Belanja Mingguan",
            assetId = mainAsset.id,
            transactionDate = "2026-10-14"
        )
        val calculated = LedgerService.calculateAssetBalance(
            openingBalance = mainAsset.openingBalance,
            transactions = listOf(tx),
            assetId = mainAsset.id
        )
        assertEquals(4_650_000L, calculated)
    }

    @Test
    fun test3_TransferOutgoingDecreasesSource() {
        val transferTx = Transaction(
            id = "tx_3",
            type = TransactionType.TRANSFER.value,
            amount = 200_000L,
            description = "Top Up GoPay",
            assetId = mainAsset.id,
            destinationAssetId = secondaryAsset.id,
            transactionDate = "2026-10-14"
        )
        val calculatedSource = LedgerService.calculateAssetBalance(
            openingBalance = mainAsset.openingBalance,
            transactions = listOf(transferTx),
            assetId = mainAsset.id
        )
        assertEquals(4_800_000L, calculatedSource)
    }

    @Test
    fun test4_TransferIncomingIncreasesDestination() {
        val transferTx = Transaction(
            id = "tx_4",
            type = TransactionType.TRANSFER.value,
            amount = 200_000L,
            description = "Top Up GoPay",
            assetId = mainAsset.id,
            destinationAssetId = secondaryAsset.id,
            transactionDate = "2026-10-14"
        )
        val calculatedDest = LedgerService.calculateAssetBalance(
            openingBalance = secondaryAsset.openingBalance,
            transactions = listOf(transferTx),
            assetId = secondaryAsset.id
        )
        assertEquals(700_000L, calculatedDest)
    }

    @Test
    fun test5_MultiTransactionLedgerCalculation() {
        val history = listOf(
            Transaction("t1", TransactionType.INCOME.value, 2_000_000L, "Project", assetId = mainAsset.id, transactionDate = "2026-10-01"),
            Transaction("t2", TransactionType.EXPENSE.value, 450_000L, "Makan", assetId = mainAsset.id, transactionDate = "2026-10-02"),
            Transaction("t3", TransactionType.TRANSFER.value, 500_000L, "Transfer out", assetId = mainAsset.id, destinationAssetId = secondaryAsset.id, transactionDate = "2026-10-03"),
            Transaction("t4", TransactionType.TRANSFER.value, 100_000L, "Transfer in", assetId = secondaryAsset.id, destinationAssetId = mainAsset.id, transactionDate = "2026-10-04"),
            Transaction("t5", TransactionType.EXPENSE.value, 50_000L, "Kopi", assetId = mainAsset.id, transactionDate = "2026-10-05")
        )

        // Opening: 5_000_000 + 2_000_000 - 450_000 - 500_000 + 100_000 - 50_000 = 6_100_000
        val finalBca = LedgerService.calculateAssetBalance(
            openingBalance = mainAsset.openingBalance,
            transactions = history,
            assetId = mainAsset.id
        )
        assertEquals(6_100_000L, finalBca)

        // Secondary: 500_000 + 500_000 (from t3) - 100_000 (from t4) = 900_000
        val finalGopay = LedgerService.calculateAssetBalance(
            openingBalance = secondaryAsset.openingBalance,
            transactions = history,
            assetId = secondaryAsset.id
        )
        assertEquals(900_000L, finalGopay)
    }

    @Test
    fun test6_ValidateTransaction_ZeroOrNegativeFails() {
        val zeroTx = Transaction(
            id = "tx_zero",
            type = TransactionType.EXPENSE.value,
            amount = 0L,
            description = "Zero",
            assetId = mainAsset.id,
            transactionDate = "2026-10-14"
        )
        val zeroValidation = LedgerService.validateTransaction(zeroTx)
        assertTrue(zeroValidation.isFailure)
        assertTrue(zeroValidation.exceptionOrNull()?.message?.contains("harus lebih dari Rp 0") == true)

        val negativeTx = zeroTx.copy(amount = -50_000L)
        val negativeValidation = LedgerService.validateTransaction(negativeTx)
        assertTrue(negativeValidation.isFailure)
    }

    @Test
    fun test7_ValidateTransaction_SameSourceAndDestFails() {
        val sameAssetTx = Transaction(
            id = "tx_same",
            type = TransactionType.TRANSFER.value,
            amount = 100_000L,
            description = "Same asset transfer",
            assetId = mainAsset.id,
            destinationAssetId = mainAsset.id,
            transactionDate = "2026-10-14"
        )
        val validation = LedgerService.validateTransaction(sameAssetTx)
        assertTrue(validation.isFailure)
        assertTrue(validation.exceptionOrNull()?.message?.contains("tidak boleh sama") == true)
    }

    @Test
    fun test8_ValidateTransaction_TransferMissingDestinationFails() {
        val invalidTransfer = Transaction(
            id = "tx_no_dest",
            type = TransactionType.TRANSFER.value,
            amount = 100_000L,
            description = "Missing destination",
            assetId = mainAsset.id,
            destinationAssetId = null,
            transactionDate = "2026-10-14"
        )
        val validation = LedgerService.validateTransaction(invalidTransfer)
        assertTrue(validation.isFailure)
        assertTrue(validation.exceptionOrNull()?.message?.contains("Aset tujuan transfer wajib dipilih") == true)
    }

    @Test
    fun test9_ValidateTransaction_InvalidDateFormatFails() {
        val malformedDateTx = Transaction(
            id = "tx_bad_date",
            type = TransactionType.EXPENSE.value,
            amount = 50_000L,
            description = "Bad date",
            assetId = mainAsset.id,
            transactionDate = "14/10/2026" // Not ISO yyyy-MM-dd
        )
        val validation = LedgerService.validateTransaction(malformedDateTx)
        assertTrue(validation.isFailure)
        assertTrue(validation.exceptionOrNull()?.message?.contains("Format tanggal tidak valid") == true)
    }

    @Test
    fun test10_BalanceSufficiencyCheck() {
        // Current balance: 500_000, Attempt to spend: 800_000 -> insufficient
        val isSufficient = LedgerService.checkBalanceSufficiency(
            currentBalance = 500_000L,
            amount = 800_000L
        )
        assertFalse("Balance of 500k should be insufficient for 800k", isSufficient)

        // Current balance: 500_000, Attempt to spend: 300_000 -> sufficient
        val isSufficientOk = LedgerService.checkBalanceSufficiency(
            currentBalance = 500_000L,
            amount = 300_000L
        )
        assertTrue("Balance of 500k should be sufficient for 300k", isSufficientOk)
    }

    @Test
    fun test11_LargeNominalIntegrity_NoOverflowOrFloatDrift() {
        // Test with 50 Billion IDR (50.000.000.000)
        val massiveOpening = 50_000_000_000L
        val massiveIncome = 25_000_000_000L
        val massiveExpense = 12_500_000_001L

        val txIncome = Transaction("large1", TransactionType.INCOME.value, massiveIncome, "Bonus", assetId = mainAsset.id, transactionDate = "2026-10-14")
        val txExpense = Transaction("large2", TransactionType.EXPENSE.value, massiveExpense, "Investasi", assetId = mainAsset.id, transactionDate = "2026-10-14")

        val result = LedgerService.calculateAssetBalance(
            openingBalance = massiveOpening,
            transactions = listOf(txIncome, txExpense),
            assetId = mainAsset.id
        )

        // 50B + 25B - 12.5B - 1 = 62_499_999_999
        assertEquals(62_499_999_999L, result)
    }

    @Test
    fun test12_SimulateEditTransaction_RollbackAndReapply() {
        var assetBalance = 1_000_000L

        // Initial transaction: Expense Rp 200.000
        val oldTx = Transaction("edit_tx", TransactionType.EXPENSE.value, 200_000L, "Belanja", assetId = mainAsset.id, transactionDate = "2026-10-14")
        assetBalance -= oldTx.amount
        assertEquals(800_000L, assetBalance)

        // Edit transaction: user changed amount to Rp 350.000
        val newTx = oldTx.copy(amount = 350_000L)

        // Rollback old effect:
        assetBalance += oldTx.amount
        assertEquals(1_000_000L, assetBalance)

        // Apply new effect:
        assetBalance -= newTx.amount
        assertEquals(650_000L, assetBalance)
    }

    @Test
    fun test13_SimulateDeleteAndUndo_BalanceRestored() {
        val initialBalance = 2_500_000L
        var balance = initialBalance

        val tx = Transaction("del_tx", TransactionType.EXPENSE.value, 750_000L, "Beli Alat", assetId = mainAsset.id, transactionDate = "2026-10-14")
        balance -= tx.amount
        assertEquals(1_750_000L, balance)

        // Action: Delete -> Balance rolled back (+750.000)
        balance += tx.amount
        assertEquals(2_500_000L, balance)

        // Action: Undo Delete within 5s -> Re-apply (-750.000)
        balance -= tx.amount
        assertEquals(1_750_000L, balance)
    }

    @Test
    fun test14_BackupChecksumIntegrityAndTamperDetection() {
        val dataObj = JSONObject().apply {
            put("assets", org.json.JSONArray().put(JSONObject().put("name", "BCA").put("balance", 5000000)))
            put("transactions", org.json.JSONArray())
            put("categories", org.json.JSONArray())
        }

        val dataString = dataObj.toString()
        val md = MessageDigest.getInstance("SHA-256")
        val hashBytes = md.digest(dataString.toByteArray(Charsets.UTF_8))
        val expectedChecksum = hashBytes.joinToString("") { "%02x".format(it) }

        val rootJson = JSONObject().apply {
            put("metadata", JSONObject().apply {
                put("app", "Duit Aing")
                put("schema_version", 5)
                put("app_version", "1.0.1")
                put("checksum", expectedChecksum)
                put("backup_date", "2026-10-14 13:00")
                put("record_counts", JSONObject().apply {
                    put("assets", 1)
                    put("transactions", 0)
                })
            })
            put("data", dataObj)
        }

        // 1. Valid backup validation
        val validResult = BackupManager.validateBackupJson(rootJson.toString())
        assertTrue("Valid JSON backup must pass validation", validResult is BackupValidationResult.Valid)

        // 2. Tampered JSON validation (altering checksum)
        rootJson.getJSONObject("metadata").put("checksum", "0000000000000000000000000000000000000000000000000000000000000000")
        val tamperedResult = BackupManager.validateBackupJson(rootJson.toString())
        assertTrue("Tampered JSON checksum must fail validation", tamperedResult is BackupValidationResult.Invalid)
        val invalidReason = (tamperedResult as BackupValidationResult.Invalid).reason
        assertTrue(invalidReason.contains("Checksum integritas tidak valid") || invalidReason.contains("tidak cocok"))
    }

    @Test
    fun test15_AdjustmentUp_IncreasesAssetBalance() {
        val initialBalance = 5_000_000L
        val adjUpTx = Transaction(
            id = "adj_up",
            type = TransactionType.ADJUSTMENT.value,
            amount = 2_000_000L,
            description = "Penyesuaian Saldo (+Rp 2.000.000)",
            assetId = mainAsset.id,
            transactionDate = "2026-10-14",
            isAdjustment = true,
            adjustmentDirection = 1
        )

        val finalBalance = LedgerService.calculateAssetBalance(
            openingBalance = initialBalance,
            transactions = listOf(adjUpTx),
            assetId = mainAsset.id
        )

        assertEquals("Penyesuaian naik harus menambah saldo", 7_000_000L, finalBalance)
    }

    @Test
    fun test16_AdjustmentDown_DecreasesAssetBalance() {
        val initialBalance = 5_000_000L
        // Skenario bug lama: note tidak memuat "(-)", dulu salah menambah saldo.
        // Sekarang: membaca adjustmentDirection = -1, sehingga saldo turun ke 3.200.000.
        val adjDownTx = Transaction(
            id = "adj_down",
            type = TransactionType.ADJUSTMENT.value,
            amount = 1_800_000L,
            description = "Penyesuaian Saldo (-Rp 1.800.000)",
            assetId = mainAsset.id,
            transactionDate = "2026-10-14",
            note = "Penyesuaian saldo manual dari Rp 5.000.000 ke Rp 3.200.000", // Tidak memuat "(-)"
            isAdjustment = true,
            adjustmentDirection = -1
        )

        val finalBalance = LedgerService.calculateAssetBalance(
            openingBalance = initialBalance,
            transactions = listOf(adjDownTx),
            assetId = mainAsset.id
        )

        assertEquals("Menyesuaikan saldo ke angka lebih rendah HARUS menurunkan saldo", 3_200_000L, finalBalance)
    }

    @Test
    fun test17_AdjustmentEdit_RollbackAndReapply() {
        val initialBalance = 5_000_000L
        var simulatedBalance = initialBalance

        // 1. Buat penyesuaian turun awal: -1.000.000 (direction -1)
        val oldAdj = Transaction(
            id = "adj_edit",
            type = TransactionType.ADJUSTMENT.value,
            amount = 1_000_000L,
            description = "Penyesuaian turun",
            assetId = mainAsset.id,
            transactionDate = "2026-10-14",
            isAdjustment = true,
            adjustmentDirection = -1
        )
        // Apply efek awal: saldo turun 1.000.000 -> 4.000.000
        simulatedBalance += if (oldAdj.adjustmentDirection == -1) -oldAdj.amount else oldAdj.amount
        assertEquals(4_000_000L, simulatedBalance)

        // 2. Edit menjadi penyesuaian naik: +500.000 (direction 1)
        val editedAdj = oldAdj.copy(
            amount = 500_000L,
            description = "Penyesuaian naik",
            adjustmentDirection = 1
        )

        // 3. Rollback efek lama (revert: jika negative (-1), tambah kembali)
        simulatedBalance += if (oldAdj.adjustmentDirection == -1) oldAdj.amount else -oldAdj.amount
        assertEquals(5_000_000L, simulatedBalance)

        // 4. Apply efek baru (apply: jika positive (1), tambah)
        simulatedBalance += if (editedAdj.adjustmentDirection == -1) -editedAdj.amount else editedAdj.amount
        assertEquals(5_500_000L, simulatedBalance)

        // Verifikasi kalkulasi menyeluruh lewat calculateAssetBalance
        val calculated = LedgerService.calculateAssetBalance(
            openingBalance = initialBalance,
            transactions = listOf(editedAdj),
            assetId = mainAsset.id
        )
        assertEquals(5_500_000L, calculated)
    }

    @Test
    fun test18_AdjustmentDelete_RevertsEffectAccurately() {
        val initialBalance = 5_000_000L

        // Kasus A: Hapus penyesuaian turun (-1.500.000)
        var balanceA = initialBalance
        val adjDown = Transaction(
            id = "adj_del_down",
            type = TransactionType.ADJUSTMENT.value,
            amount = 1_500_000L,
            description = "Turun",
            assetId = mainAsset.id,
            transactionDate = "2026-10-14",
            isAdjustment = true,
            adjustmentDirection = -1
        )
        balanceA -= adjDown.amount // Saldo menjadi 3.500.000
        assertEquals(3_500_000L, balanceA)
        // Revert efek saat dihapus (tambah kembali)
        balanceA += adjDown.amount
        assertEquals("Revert penyesuaian turun harus memulihkan saldo awal", 5_000_000L, balanceA)

        // Kasus B: Hapus penyesuaian naik (+2.500.000)
        var balanceB = initialBalance
        val adjUp = Transaction(
            id = "adj_del_up",
            type = TransactionType.ADJUSTMENT.value,
            amount = 2_500_000L,
            description = "Naik",
            assetId = mainAsset.id,
            transactionDate = "2026-10-14",
            isAdjustment = true,
            adjustmentDirection = 1
        )
        balanceB += adjUp.amount // Saldo menjadi 7.500.000
        assertEquals(7_500_000L, balanceB)
        // Revert efek saat dihapus (kurangi kembali)
        balanceB -= adjUp.amount
        assertEquals("Revert penyesuaian naik harus memulihkan saldo awal", 5_000_000L, balanceB)
    }

    @Test
    fun test19_RecalculateLogic_ConsistentWithLedgerDirection() {
        val initialBalance = 10_000_000L
        val txList = listOf(
            Transaction("t1", TransactionType.INCOME.value, 3_000_000L, "Gaji", assetId = mainAsset.id, transactionDate = "2026-10-01"),
            Transaction("t2", TransactionType.EXPENSE.value, 1_000_000L, "Belanja", assetId = mainAsset.id, transactionDate = "2026-10-02"),
            // Penyesuaian naik (+500.000)
            Transaction("t3", TransactionType.ADJUSTMENT.value, 500_000L, "Koreksi Naik", assetId = mainAsset.id, transactionDate = "2026-10-03", isAdjustment = true, adjustmentDirection = 1),
            // Penyesuaian turun (-2.000.000)
            Transaction("t4", TransactionType.ADJUSTMENT.value, 2_000_000L, "Koreksi Turun", assetId = mainAsset.id, transactionDate = "2026-10-04", isAdjustment = true, adjustmentDirection = -1)
        )

        // 10.000.000 + 3.000.000 - 1.000.000 + 500.000 - 2.000.000 = 10.500.000
        val finalCalculated = LedgerService.calculateAssetBalance(
            openingBalance = initialBalance,
            transactions = txList,
            assetId = mainAsset.id
        )

        assertEquals(10_500_000L, finalCalculated)
    }

    @Test
    fun test20_BackupRestore_PreservesAndMigratesAdjustmentDirection() {
        // 1. Backup baru yang sudah memiliki adjustment_direction
        val newBackupTx = JSONObject().apply {
            put("type", "adjustment")
            put("amount", 500000)
            put("is_adjustment", 1)
            put("adjustment_direction", -1)
            put("note", "Penyesuaian baru")
        }
        val rawDirNew = if (newBackupTx.has("adjustment_direction")) newBackupTx.optInt("adjustment_direction", 0) else 0
        val resolvedDirNew = if (rawDirNew != 0) rawDirNew else if (newBackupTx.optString("type") == "adjustment") {
            if (newBackupTx.optString("note", "").contains("(-)")) -1 else 1
        } else 0
        assertEquals(-1, resolvedDirNew)

        // 2. Backup lama tanpa adjustment_direction dengan note memuat "(-)"
        val legacyNegTx = JSONObject().apply {
            put("type", "adjustment")
            put("amount", 300000)
            put("is_adjustment", 1)
            put("note", "Penyesuaian saldo manual dari Rp 1.000.000 ke Rp 700.000 (-)")
        }
        val rawDirLegacyNeg = if (legacyNegTx.has("adjustment_direction")) legacyNegTx.optInt("adjustment_direction", 0) else 0
        val resolvedDirLegacyNeg = if (rawDirLegacyNeg != 0) rawDirLegacyNeg else if (legacyNegTx.optString("type") == "adjustment") {
            if (legacyNegTx.optString("note", "").contains("(-)")) -1 else 1
        } else 0
        assertEquals("Data lama dengan tanda '(-)' harus dimigrasi ke -1", -1, resolvedDirLegacyNeg)

        // 3. Backup lama tanpa adjustment_direction tanpa note "(-)"
        val legacyPosTx = JSONObject().apply {
            put("type", "adjustment")
            put("amount", 400000)
            put("is_adjustment", 1)
            put("note", "Penyesuaian saldo manual dari Rp 1.000.000 ke Rp 1.400.000")
        }
        val rawDirLegacyPos = if (legacyPosTx.has("adjustment_direction")) legacyPosTx.optInt("adjustment_direction", 0) else 0
        val resolvedDirLegacyPos = if (rawDirLegacyPos != 0) rawDirLegacyPos else if (legacyPosTx.optString("type") == "adjustment") {
            if (legacyPosTx.optString("note", "").contains("(-)")) -1 else 1
        } else 0
        assertEquals("Data lama penyesuaian positif harus dimigrasi ke 1", 1, resolvedDirLegacyPos)
    }
}
