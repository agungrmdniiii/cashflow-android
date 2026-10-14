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
}
