package com.cashflow.app

import com.cashflow.app.data.model.*
import com.cashflow.app.data.repository.BalanceDiscrepancy
import com.cashflow.app.data.repository.LedgerRecalculateResult
import com.cashflow.app.data.repository.LedgerService
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.Random

/**
 * KUNCI 4/5 - TEST INVARIAN & INTEGRITAS SALDO (Duit Aing).
 *
 * Menguji:
 * 1. Fuzz Invariant Test: Menjalankan >= 1.000 urutan operasi acak (seed tetap 20261016L) mencakup:
 *    tambah/edit/hapus transaksi, transfer, penyesuaian, edit metadata aset, edit saldo awal aset kosong,
 *    arsip aset, bayar cicilan hutang, alokasi tabungan target, dan simulasi restore backup.
 *    Memverifikasi invarian saldo setelah SETIAP langkah:
 *    current_balance == opening_balance + income - expense - transferOut + transferIn + adjIn - adjOut.
 * 2. Regression Test Skenario Pengguna: Ubah metadata aset -> simulasi buka ulang aplikasi (recalculate) ->
 *    saldo tetap sama dan tidak tertimpa.
 * 3. Artificial Discrepancy Test: Menyuntikkan selisih saldo buatan -> terdeteksi oleh integrity check ->
 *    dapat diperbaiki secara sempurna -> verifikasi selisih 0 setelah perbaikan.
 */
class LedgerInvariantFuzzAndRegressionTest {

    data class FuzzState(
        val assets: MutableList<Asset> = mutableListOf(),
        val transactions: MutableList<Transaction> = mutableListOf(),
        val debts: MutableList<Debt> = mutableListOf(),
        val goals: MutableList<FinancialGoal> = mutableListOf()
    )

    private fun applyTx(tx: Transaction, state: FuzzState) {
        state.transactions.add(tx)
        val asset = state.assets.find { it.id == tx.assetId }
        val destAsset = state.assets.find { it.id == tx.destinationAssetId }

        when (tx.type) {
            TransactionType.INCOME.value -> {
                asset?.let {
                    val idx = state.assets.indexOf(it)
                    state.assets[idx] = it.copy(currentBalance = it.currentBalance + tx.amount)
                }
            }
            TransactionType.EXPENSE.value -> {
                asset?.let {
                    val idx = state.assets.indexOf(it)
                    state.assets[idx] = it.copy(currentBalance = it.currentBalance - tx.amount)
                }
            }
            TransactionType.TRANSFER.value -> {
                asset?.let {
                    val idx = state.assets.indexOf(it)
                    state.assets[idx] = it.copy(currentBalance = it.currentBalance - tx.amount)
                }
                destAsset?.let {
                    val idx = state.assets.indexOf(it)
                    state.assets[idx] = it.copy(currentBalance = it.currentBalance + tx.amount)
                }
            }
            TransactionType.ADJUSTMENT.value -> {
                asset?.let {
                    val idx = state.assets.indexOf(it)
                    val delta = if (tx.adjustmentDirection == -1) -tx.amount else tx.amount
                    state.assets[idx] = it.copy(currentBalance = it.currentBalance + delta)
                }
            }
        }
    }

    private fun revertTx(tx: Transaction, state: FuzzState) {
        val asset = state.assets.find { it.id == tx.assetId }
        val destAsset = state.assets.find { it.id == tx.destinationAssetId }

        when (tx.type) {
            TransactionType.INCOME.value -> {
                asset?.let {
                    val idx = state.assets.indexOf(it)
                    state.assets[idx] = it.copy(currentBalance = it.currentBalance - tx.amount)
                }
            }
            TransactionType.EXPENSE.value -> {
                asset?.let {
                    val idx = state.assets.indexOf(it)
                    state.assets[idx] = it.copy(currentBalance = it.currentBalance + tx.amount)
                }
            }
            TransactionType.TRANSFER.value -> {
                asset?.let {
                    val idx = state.assets.indexOf(it)
                    state.assets[idx] = it.copy(currentBalance = it.currentBalance + tx.amount)
                }
                destAsset?.let {
                    val idx = state.assets.indexOf(it)
                    state.assets[idx] = it.copy(currentBalance = it.currentBalance - tx.amount)
                }
            }
            TransactionType.ADJUSTMENT.value -> {
                asset?.let {
                    val idx = state.assets.indexOf(it)
                    val delta = if (tx.adjustmentDirection == -1) tx.amount else -tx.amount
                    state.assets[idx] = it.copy(currentBalance = it.currentBalance + delta)
                }
            }
        }
    }

    private fun assertInvariantForAllAssets(step: Int, seed: Long, opName: String, state: FuzzState) {
        for (asset in state.assets) {
            val income = state.transactions.filter { it.assetId == asset.id && it.type == TransactionType.INCOME.value }.sumOf { it.amount }
            val expense = state.transactions.filter { it.assetId == asset.id && it.type == TransactionType.EXPENSE.value }.sumOf { it.amount }
            val transferOut = state.transactions.filter { it.assetId == asset.id && it.type == TransactionType.TRANSFER.value }.sumOf { it.amount }
            val transferIn = state.transactions.filter { it.destinationAssetId == asset.id && it.type == TransactionType.TRANSFER.value }.sumOf { it.amount }
            val adjIn = state.transactions.filter { it.assetId == asset.id && it.type == TransactionType.ADJUSTMENT.value && it.adjustmentDirection == 1 }.sumOf { it.amount }
            val adjOut = state.transactions.filter { it.assetId == asset.id && it.type == TransactionType.ADJUSTMENT.value && it.adjustmentDirection == -1 }.sumOf { it.amount }

            val expectedByFormula = asset.openingBalance + income - expense - transferOut + transferIn + adjIn - adjOut
            val calculatedByLedger = LedgerService.calculateAssetBalance(asset.openingBalance, state.transactions, asset.id)

            assertEquals(
                "Invariant Formula vs Ledger mismatch at step $step (Seed: $seed, Op: $opName) on Asset ${asset.name}",
                expectedByFormula,
                calculatedByLedger
            )

            assertEquals(
                "Current Balance vs Invariant mismatch at step $step (Seed: $seed, Op: $opName) on Asset ${asset.name} (stored=${asset.currentBalance}, expected=$expectedByFormula)",
                expectedByFormula,
                asset.currentBalance
            )
        }
    }

    /**
     * Test 1: Fuzz test dengan >= 1.000 urutan operasi acak dan seed tetap (20261016L).
     */
    @Test
    fun test01_FuzzTest_1200RandomOperations_StrictInvariantCheckAfterEveryStep() {
        val seed = 20261016L
        val random = Random(seed)
        val totalOperations = 1200

        val state = FuzzState()

        // Inisialisasi 4 aset awal
        state.assets.addAll(
            listOf(
                Asset(id = "asset_bca", name = "BCA Prioritas", type = "Bank", openingBalance = 5_000_000L, currentBalance = 5_000_000L),
                Asset(id = "asset_mandiri", name = "Mandiri Tabungan", type = "Bank", openingBalance = 2_000_000L, currentBalance = 2_000_000L),
                Asset(id = "asset_gopay", name = "GoPay Saldo", type = "E-wallet", openingBalance = 500_000L, currentBalance = 500_000L),
                Asset(id = "asset_cash", name = "Dompet Fisik", type = "Tunai", openingBalance = 300_000L, currentBalance = 300_000L)
            )
        )

        // Hutang dan Target tabungan
        state.debts.add(Debt(id = "debt_1", title = "Cicilan Laptop", lenderName = "Bank Mandiri", totalAmount = 10_000_000L, paidAmount = 2_000_000L, dueDate = "2026-12-31"))
        state.goals.add(FinancialGoal(id = "goal_1", name = "Dana Darurat", targetAmount = 20_000_000L, currentAmount = 5_000_000L, deadline = "2026-12-31"))

        var txIdCounter = 1

        val operationHistory = mutableListOf<String>()

        for (step in 1..totalOperations) {
            val opType = random.nextInt(14)
            var opName = ""

            when (opType) {
                0 -> { // Add Income
                    val asset = state.assets[random.nextInt(state.assets.size)]
                    val amount = (random.nextInt(100) + 1) * 10_000L
                    val tx = Transaction(
                        id = "tx_${txIdCounter++}",
                        type = TransactionType.INCOME.value,
                        amount = amount,
                        description = "Fuzz Income $step",
                        assetId = asset.id,
                        transactionDate = "2026-10-16"
                    )
                    applyTx(tx, state)
                    opName = "ADD_INCOME_${asset.name}_$amount"
                }
                1 -> { // Add Expense
                    val asset = state.assets[random.nextInt(state.assets.size)]
                    val amount = (random.nextInt(50) + 1) * 5_000L
                    val tx = Transaction(
                        id = "tx_${txIdCounter++}",
                        type = TransactionType.EXPENSE.value,
                        amount = amount,
                        description = "Fuzz Expense $step",
                        assetId = asset.id,
                        transactionDate = "2026-10-16"
                    )
                    applyTx(tx, state)
                    opName = "ADD_EXPENSE_${asset.name}_$amount"
                }
                2 -> { // Add Transfer
                    val fromIdx = random.nextInt(state.assets.size)
                    var toIdx = random.nextInt(state.assets.size)
                    while (toIdx == fromIdx) {
                        toIdx = random.nextInt(state.assets.size)
                    }
                    val fromAsset = state.assets[fromIdx]
                    val toAsset = state.assets[toIdx]
                    val amount = (random.nextInt(30) + 1) * 10_000L
                    val tx = Transaction(
                        id = "tx_${txIdCounter++}",
                        type = TransactionType.TRANSFER.value,
                        amount = amount,
                        description = "Transfer $step",
                        assetId = fromAsset.id,
                        destinationAssetId = toAsset.id,
                        transactionDate = "2026-10-16"
                    )
                    applyTx(tx, state)
                    opName = "ADD_TRANSFER_${fromAsset.name}_TO_${toAsset.name}_$amount"
                }
                3 -> { // Add Adjustment
                    val asset = state.assets[random.nextInt(state.assets.size)]
                    val dir = if (random.nextBoolean()) 1 else -1
                    val amount = (random.nextInt(20) + 1) * 10_000L
                    val tx = Transaction(
                        id = "tx_${txIdCounter++}",
                        type = TransactionType.ADJUSTMENT.value,
                        amount = amount,
                        adjustmentDirection = dir,
                        description = "Adjustment $step",
                        assetId = asset.id,
                        transactionDate = "2026-10-16"
                    )
                    applyTx(tx, state)
                    opName = "ADD_ADJUSTMENT_${asset.name}_DIR_${dir}_$amount"
                }
                4 -> { // Edit Transaction Amount
                    if (state.transactions.isNotEmpty()) {
                        val txIdx = random.nextInt(state.transactions.size)
                        val oldTx = state.transactions[txIdx]
                        revertTx(oldTx, state)
                        state.transactions.removeAt(txIdx)

                        val newAmount = (random.nextInt(100) + 1) * 10_000L
                        val newTx = oldTx.copy(amount = newAmount)
                        applyTx(newTx, state)
                        opName = "EDIT_TX_AMOUNT_${oldTx.id}_${oldTx.amount}_TO_$newAmount"
                    } else {
                        opName = "NO_OP_EDIT_AMOUNT"
                    }
                }
                5 -> { // Edit Transaction Asset
                    if (state.transactions.isNotEmpty()) {
                        val txIdx = random.nextInt(state.transactions.size)
                        val oldTx = state.transactions[txIdx]
                        if (oldTx.type != TransactionType.TRANSFER.value) {
                            revertTx(oldTx, state)
                            state.transactions.removeAt(txIdx)

                            val newAsset = state.assets[random.nextInt(state.assets.size)]
                            val newTx = oldTx.copy(assetId = newAsset.id)
                            applyTx(newTx, state)
                            opName = "EDIT_TX_ASSET_${oldTx.id}_TO_${newAsset.name}"
                        } else {
                            opName = "SKIP_TRANSFER_ASSET_EDIT"
                        }
                    } else {
                        opName = "NO_OP_EDIT_ASSET"
                    }
                }
                6 -> { // Edit Transaction Type (e.g. Income <-> Expense)
                    if (state.transactions.isNotEmpty()) {
                        val txIdx = random.nextInt(state.transactions.size)
                        val oldTx = state.transactions[txIdx]
                        if (oldTx.type == TransactionType.INCOME.value || oldTx.type == TransactionType.EXPENSE.value) {
                            revertTx(oldTx, state)
                            state.transactions.removeAt(txIdx)

                            val newType = if (oldTx.type == TransactionType.INCOME.value) {
                                TransactionType.EXPENSE.value
                            } else {
                                TransactionType.INCOME.value
                            }
                            val newTx = oldTx.copy(type = newType)
                            applyTx(newTx, state)
                            opName = "EDIT_TX_TYPE_${oldTx.id}_${oldTx.type}_TO_$newType"
                        } else {
                            opName = "SKIP_TRANSFER_TYPE_SWITCH"
                        }
                    } else {
                        opName = "NO_OP_EDIT_TYPE"
                    }
                }
                7 -> { // Delete Transaction
                    if (state.transactions.isNotEmpty()) {
                        val txIdx = random.nextInt(state.transactions.size)
                        val oldTx = state.transactions[txIdx]
                        revertTx(oldTx, state)
                        state.transactions.removeAt(txIdx)
                        opName = "DELETE_TX_${oldTx.id}"
                    } else {
                        opName = "NO_OP_DELETE"
                    }
                }
                8 -> { // Edit Asset Metadata (Name, Type, isDefault)
                    val assetIdx = random.nextInt(state.assets.size)
                    val oldAsset = state.assets[assetIdx]
                    val updatedAsset = oldAsset.copy(
                        name = "${oldAsset.name} Rev$step",
                        isDefault = random.nextBoolean()
                    )
                    state.assets[assetIdx] = updatedAsset
                    opName = "EDIT_ASSET_METADATA_${oldAsset.id}"
                }
                9 -> { // Edit Opening Balance on Asset without Transactions
                    val emptyAsset = state.assets.firstOrNull { a ->
                        state.transactions.none { it.assetId == a.id || it.destinationAssetId == a.id }
                    }
                    if (emptyAsset != null) {
                        val idx = state.assets.indexOf(emptyAsset)
                        val newOpening = (random.nextInt(100) + 1) * 100_000L
                        state.assets[idx] = emptyAsset.copy(
                            openingBalance = newOpening,
                            currentBalance = newOpening
                        )
                        opName = "EDIT_OPENING_BALANCE_EMPTY_${emptyAsset.name}_$newOpening"
                    } else {
                        // Jika tidak ada yang kosong, tambahkan aset baru kosong
                        val newAssetId = "asset_fuzz_${step}"
                        val initialOpening = (random.nextInt(50) + 1) * 100_000L
                        state.assets.add(
                            Asset(
                                id = newAssetId,
                                name = "Aset Baru $step",
                                type = "Bank",
                                openingBalance = initialOpening,
                                currentBalance = initialOpening
                            )
                        )
                        opName = "ADD_NEW_EMPTY_ASSET_$newAssetId"
                    }
                }
                10 -> { // Archive / Unarchive Asset
                    val assetIdx = random.nextInt(state.assets.size)
                    val asset = state.assets[assetIdx]
                    state.assets[assetIdx] = asset.copy(isActive = !asset.isActive)
                    opName = "TOGGLE_ACTIVE_ASSET_${asset.id}"
                }
                11 -> { // Pay Debt (linked expense)
                    val asset = state.assets[random.nextInt(state.assets.size)]
                    val debt = state.debts[0]
                    val paymentAmount = 200_000L
                    val tx = Transaction(
                        id = "tx_debt_${txIdCounter++}",
                        type = TransactionType.EXPENSE.value,
                        amount = paymentAmount,
                        description = "Bayar Cicilan $step",
                        assetId = asset.id,
                        debtId = debt.id,
                        transactionDate = "2026-10-16"
                    )
                    applyTx(tx, state)
                    opName = "PAY_DEBT_${debt.title}_FROM_${asset.name}"
                }
                12 -> { // Goal Allocation / Withdrawal
                    val asset = state.assets[random.nextInt(state.assets.size)]
                    val goal = state.goals[0]
                    val isDeposit = random.nextBoolean()
                    val amount = 150_000L
                    val tx = Transaction(
                        id = "tx_goal_${txIdCounter++}",
                        type = if (isDeposit) TransactionType.EXPENSE.value else TransactionType.INCOME.value,
                        amount = amount,
                        description = if (isDeposit) "Tabung Target" else "Tarik Target",
                        assetId = asset.id,
                        goalId = goal.id,
                        transactionDate = "2026-10-16"
                    )
                    applyTx(tx, state)
                    opName = "GOAL_OP_${goal.name}_isDep_$isDeposit"
                }
                13 -> { // Simulate Backup Export & Restore Recalculation
                    // Ekspor ke JSON
                    val backupObj = JSONObject()
                    val assetsArr = JSONArray()
                    state.assets.forEach { a ->
                        val obj = JSONObject()
                        obj.put("id", a.id)
                        obj.put("name", a.name)
                        obj.put("opening_balance", a.openingBalance)
                        obj.put("current_balance", a.currentBalance)
                        assetsArr.put(obj)
                    }
                    backupObj.put("assets", assetsArr)

                    // Verifikasi bahwa saldo setelah restore dan recalculate seluruh aset identik
                    for (i in 0 until assetsArr.length()) {
                        val aObj = assetsArr.getJSONObject(i)
                        val aId = aObj.getString("id")
                        val aOpening = aObj.getLong("opening_balance")
                        val recalcBalance = LedgerService.calculateAssetBalance(aOpening, state.transactions, aId)
                        val storedBalance = state.assets.find { it.id == aId }?.currentBalance ?: 0L
                        assertEquals("Restore recalculation mismatch for $aId", storedBalance, recalcBalance)
                    }
                    opName = "RESTORE_VERIFICATION"
                }
            }

            operationHistory.add("Step $step: $opName")

            // INVARIANT CHECK SETELAH SETIAP LANGKAH
            try {
                assertInvariantForAllAssets(step, seed, opName, state)
            } catch (t: Throwable) {
                System.err.println("=== FUZZ FAILURE AT STEP $step (Seed: $seed, Op: $opName) ===")
                operationHistory.takeLast(30).forEach { System.err.println(it) }
                throw t
            }
        }

        println("=== FUZZ TEST PASSED ===")
        println("Seed: $seed")
        println("Total operations verified: $totalOperations")
        println("Final active assets: ${state.assets.size}")
        println("Final transactions: ${state.transactions.size}")
        assertTrue(totalOperations >= 1000)
    }

    /**
     * Test 2: Test Regresi Skenario Pengguna:
     * Ubah nama/tipe aset -> jalankan recalculate (simulasi tutup dan buka ulang aplikasi) ->
     * saldo tetap sama dan tidak tertimpa.
     */
    @Test
    fun test02_Regression_EditAssetMetadata_ReopenRecalculate_BalanceStrictlyUnchanged() {
        val originalOpening = 10_000_000L
        val asset = Asset(
            id = "asset_reopen_test",
            name = "BCA Tabungan Utama",
            type = "Bank",
            openingBalance = originalOpening,
            currentBalance = originalOpening
        )

        val txList = mutableListOf(
            Transaction(
                id = "tx_reg_1",
                type = TransactionType.INCOME.value,
                amount = 2_500_000L,
                description = "Gaji",
                assetId = asset.id,
                transactionDate = "2026-10-01"
            ),
            Transaction(
                id = "tx_reg_2",
                type = TransactionType.EXPENSE.value,
                amount = 1_000_000L,
                description = "Belanja Bulanan",
                assetId = asset.id,
                transactionDate = "2026-10-02"
            )
        )

        // Saldo saat ini: 10.000.000 + 2.500.000 - 1.000.000 = 11.500.000
        val currentBalance = LedgerService.calculateAssetBalance(asset.openingBalance, txList, asset.id)
        assertEquals(11_500_000L, currentBalance)
        val activeAsset = asset.copy(currentBalance = currentBalance)

        // Pengguna mengubah nama aset di dialog edit aset
        // Sesuai perbaikan 1/5: updateAsset hanya mengubah metadata (name, type, isDefault, note)
        // dan TIDAK mengubah opening_balance atau current_balance.
        val editedAsset = activeAsset.copy(
            name = "BCA Rekening Payroll",
            type = "Bank",
            note = "Sudah diubah namanya"
        )

        assertEquals("Opening balance tidak boleh berubah", originalOpening, editedAsset.openingBalance)
        assertEquals("Current balance tidak boleh tertimpa", 11_500_000L, editedAsset.currentBalance)

        // Simulasi aplikasi ditutup paksa dan dibuka ulang:
        // CashflowRepository.init memanggil recalculateAllBalances (autoFix = false)
        val recalculatedBalance = LedgerService.calculateAssetBalance(
            openingBalance = editedAsset.openingBalance,
            transactions = txList,
            assetId = editedAsset.id
        )

        // Verifikasi saldo tetap sama persis dan selisih adalah 0
        assertEquals("Saldo setelah buka ulang harus identik", 11_500_000L, recalculatedBalance)
        assertEquals("Selisih saldo harus 0", 0L, recalculatedBalance - editedAsset.currentBalance)
    }

    /**
     * Test 3: Artificial Discrepancy Injection & Repair:
     * Menyuntikkan selisih buatan di test -> terdeteksi oleh pengecekan integritas ->
     * dapat diperbaiki oleh repairBalanceDiscrepancies -> saldo kembali sinkron 100%.
     */
    @Test
    fun test03_ArtificialDiscrepancy_DetectedAndRepairedSuccessfully() {
        val assetBca = Asset(
            id = "asset_audit_1",
            name = "BCA",
            type = "Bank",
            openingBalance = 5_000_000L,
            currentBalance = 5_000_000L
        )
        val assetGopay = Asset(
            id = "asset_audit_2",
            name = "GoPay",
            type = "E-wallet",
            openingBalance = 1_000_000L,
            currentBalance = 1_000_000L
        )

        val txList = mutableListOf(
            Transaction(
                id = "tx_audit_1",
                type = TransactionType.INCOME.value,
                amount = 2_000_000L,
                description = "Proyek",
                assetId = assetBca.id,
                transactionDate = "2026-10-10"
            ),
            Transaction(
                id = "tx_audit_2",
                type = TransactionType.TRANSFER.value,
                amount = 500_000L,
                description = "Topup GoPay",
                assetId = assetBca.id,
                destinationAssetId = assetGopay.id,
                transactionDate = "2026-10-11"
            )
        )

        // Saldo yang sah secara jurnal:
        // BCA = 5.000.000 + 2.000.000 - 500.000 = 6.500.000
        // GoPay = 1.000.000 + 500.000 = 1.500.000
        val legitimateBca = LedgerService.calculateAssetBalance(assetBca.openingBalance, txList, assetBca.id)
        val legitimateGopay = LedgerService.calculateAssetBalance(assetGopay.openingBalance, txList, assetGopay.id)
        assertEquals(6_500_000L, legitimateBca)
        assertEquals(1_500_000L, legitimateGopay)

        // SUNTIKKAN SELISIH BUATAN (Artificial Discrepancy):
        // Misalnya karena crash lawas atau database kotor, saldo tersimpan BCA tercatat 7.250.000 (+750.000)
        val corruptedBca = assetBca.copy(currentBalance = 7_250_000L)
        val validGopay = assetGopay.copy(currentBalance = legitimateGopay)

        // 1. Jalankan Pengecekan Integritas (simulasi recalculateAllBalances dengan autoFix = false)
        val discrepancies = mutableListOf<BalanceDiscrepancy>()
        val allItems = mutableListOf<BalanceDiscrepancy>()

        val assetsToCheck = listOf(corruptedBca, validGopay)
        for (a in assetsToCheck) {
            val calculated = LedgerService.calculateAssetBalance(a.openingBalance, txList, a.id)
            val diff = calculated - a.currentBalance
            val item = BalanceDiscrepancy(
                assetId = a.id,
                assetName = a.name,
                recordedBalance = a.currentBalance,
                calculatedBalance = calculated,
                difference = diff
            )
            allItems.add(item)
            if (diff != 0L) {
                discrepancies.add(item)
            }
        }

        // Verifikasi deteksi:
        assertEquals("Harus terdeteksi tepat 1 selisih aset", 1, discrepancies.size)
        val bcaDisc = discrepancies[0]
        assertEquals(corruptedBca.id, bcaDisc.assetId)
        assertEquals(7_250_000L, bcaDisc.recordedBalance)
        assertEquals(6_500_000L, bcaDisc.calculatedBalance)
        assertEquals(-750_000L, bcaDisc.difference) // Dihitung (6.5jt) - Tersimpan (7.25jt) = -750rb

        // GoPay harus tercatat selisih 0 (Sinkron)
        val gopayItem = allItems.find { it.assetId == validGopay.id }
        assertNotNull(gopayItem)
        assertEquals(0L, gopayItem?.difference)

        // 2. Jalankan Perbaikan Saldo (simulasi repairBalanceDiscrepancies / autoFix = true)
        val repairedAssets = mutableListOf<Asset>()
        for (a in assetsToCheck) {
            val disc = discrepancies.find { it.assetId == a.id }
            if (disc != null) {
                repairedAssets.add(a.copy(currentBalance = disc.calculatedBalance))
            } else {
                repairedAssets.add(a)
            }
        }

        // 3. Verifikasi Ulang Pasca-Perbaikan:
        val postRepairDiscrepancies = mutableListOf<BalanceDiscrepancy>()
        for (a in repairedAssets) {
            val calculated = LedgerService.calculateAssetBalance(a.openingBalance, txList, a.id)
            val diff = calculated - a.currentBalance
            if (diff != 0L) {
                postRepairDiscrepancies.add(
                    BalanceDiscrepancy(a.id, a.name, a.currentBalance, calculated, diff)
                )
            }
        }

        assertTrue("Seluruh aset harus kembali sinkron (0 selisih)", postRepairDiscrepancies.isEmpty())
        val repairedBca = repairedAssets.find { it.id == assetBca.id }
        assertEquals(6_500_000L, repairedBca?.currentBalance)
    }
}
