package com.cashflow.app

import com.cashflow.app.data.model.*
import com.cashflow.app.data.service.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class VinaQuestionCoverageTest {
    private val today = LocalDate.of(2026, 10, 17)
    private val categories = listOf(Category("cat_makanan", "Makan", "expense"), Category("cat_transportasi", "Transport", "expense"))
    private fun tx(id: String, amount: Long, date: String, cat: String? = "cat_makanan", asset: String = "cash", type: String = "expense") =
        Transaction(id, type, amount, id, cat, asset, transactionDate = date)
    private val ledger = listOf(
        tx("Bakso", 20_000, "2026-10-17"), tx("Sarapan", 10_000, "2026-10-16", asset = "bca"),
        tx("Ojek", 30_000, "2026-10-12", "cat_transportasi", "bca"), tx("Belanja dapur", 40_000, "2026-10-01"),
        tx("Makan September", 50_000, "2026-09-30"), tx("Taksi September", 60_000, "2026-09-01", "cat_transportasi", "bca"),
        tx("Gaji Januari", 1_000_000, "2026-01-01", type = "income"), tx("Gaji Oktober", 200_000, "2026-10-16", type = "income"),
        tx("Pindah uang", 500_000, "2026-10-17", type = "transfer"),
        tx("Koreksi", 999_000, "2026-10-17").copy(isAdjustment = true)
    )
    private fun context(): FinancialChatContext {
        val stats = FinancialInsightService.computeMonthComparison(ledger, categories, today)
        return FinancialChatContext("Budi", today.toString(), 2_300_000,
            listOf(Asset("cash", "Dompet", "Tunai", 300_000, 300_000), Asset("bca", "BCA", "Bank", 2_000_000, 2_000_000)),
            stats, "2026-09", "2026-10", stats.categoryDeltas,
            listOf(BudgetWithProgress(Budget("b1", "cat_transportasi", targetAmount = 100_000), categories[1], 30_000, 70_000, .3f, BudgetStatus.SAFE)),
            listOf(Debt("d1", "Utang Doni", "Doni", 40_000, dueDate = "2026-10-01"),
                Debt("d2", "Piutang Sari", "Sari", 90_000, dueDate = "2026-12-01", direction = "receivable")),
            listOf(FinancialGoal("g1", "Laptop", 2_000_000, 500_000, "2026-12-31", "cash")),
            transactions = ledger, categories = categories)
    }
    private fun check(phrases: List<String>, vararg expected: String) {
        phrases.forEach { query ->
            val answer = FinancialInsightService.evaluateOfflineQuery(query, context())
            expected.forEach { assertTrue("$query\n$answer\nExpected $it", answer.contains(it)) }
        }
    }

    @Test fun relativeDatesAndSlangUseActualTransactions() {
        check(listOf("pengeluaran hari ini", "hari ini keluar duit berapa", "duit keluar hari ini berapa", "Vina, belanja hari ini brp?", "habis berapa hari ini", "spending hari ini", "pengeluran hr ini berapa"), "20.000", "2026-10-17")
        check(listOf("pengeluaran kemarin", "kemaren habis berapa", "belanja kemarin", "uang keluar kemarin", "pengeluaran sehari yang lalu"), "10.000", "2026-10-16")
        check(listOf("pengeluaran minggu ini", "belanja pekan ini", "duit keluar 7 hari terakhir", "pengeluaran seminggu terakhir"), "60.000")
        check(listOf("belanja minggu lalu", "pengeluaran pekan lalu"), "Rp 0", "Belum ada")
        check(listOf("pengeluaran bulan ini", "belanja bln ini", "bulan ini keluar duit berapa", "total spending bulan ini"), "100.000")
        check(listOf("pengeluaran tahun ini", "belanja thn ini"), "210.000")
    }

    @Test fun namedAndExplicitDateRangesAreInclusive() {
        check(listOf("belanja september 2026", "pengeluaran bulan sep 2026", "pengeluaran bulan lalu", "pengeleruan bulan lagu"), "110.000")
        check(listOf("pengeluaran dari 2026-10-12 sampai 2026-10-16", "belanja 2026-10-12 s/d 2026-10-16"), "40.000")
        check(listOf("pengeluaran tanggal 2026-10-16", "belanja 2026-10-16"), "10.000")
        check(listOf("pemasukan januari 2026", "uang masuk jan 2026"), "1.000.000")
        check(listOf("pengeluaran tahun lalu", "belanja thn lalu"), "Rp 0", "2025")
    }

    @Test fun categoriesAndAccountsCanBeCombinedWithPeriods() {
        check(listOf("pengeluaran makan bulan ini", "berapa biaya makanan bulan ini", "habis berapa untuk makan bulan ini"), "70.000", "Makan")
        check(listOf("belanja transport bulan ini", "pengeluaran transportasi bulan ini", "ongkos transport bulan ini"), "30.000", "Transport")
        check(listOf("pengeluaran dari BCA bulan ini", "belanja pakai BCA bulan ini"), "40.000", "BCA")
        check(listOf("pengeluaran makan dari BCA bulan ini", "belanja makanan pakai BCA bulan ini"), "10.000", "Makan", "BCA")
        check(listOf("saldo BCA berapa", "uang di BCA berapa", "BCA masih berapa", "cek rekening BCA"), "2.000.000", "BCA")
    }

    @Test fun countsLargestLatestAndAveragesAnswerDifferentQuestions() {
        check(listOf("berapa kali belanja bulan ini", "jumlah transaksi pengeluaran bulan ini", "ada berapa transaksi pengeluaran bulan ini"), "4 transaksi")
        check(listOf("transaksi pengeluaran terbesar bulan ini", "belanja paling mahal bulan ini", "pengeluaran tertinggi bulan ini"), "Belanja dapur", "40.000")
        check(listOf("pengeluaran terakhir", "belanja terbaru", "transaksi keluar paling baru"), "Bakso", "20.000")
        check(listOf("rata-rata pengeluaran bulan lalu per hari", "rerata belanja september 2026"), "3.666", "30 hari")
        check(listOf("total transfer hari ini", "berapa uang dipindahkan hari ini"), "500.000")
        check(listOf("pemasukan bulan ini", "duit masuk bln ini", "penghasilan bulan ini", "income bulan ini"), "200.000")
    }

    @Test fun debtsReceivablesGoalsAndBudgetsAreDistinct() {
        check(listOf("utang saya berapa", "sisa hutang", "berapa uang yang harus saya bayar", "cicilan belum lunas"), "40.000")
        check(listOf("piutang saya berapa", "siapa yang belum bayar saya", "uang yang dipinjam Sari berapa"), "90.000", "Sari")
        check(listOf("utang yang telat bayar", "hutang lewat jatuh tempo"), "Doni", "40.000")
        check(listOf("target Laptop kurang berapa", "berapa lagi untuk Laptop", "sisa target laptop"), "1.500.000")
        check(listOf("budget transport sisa berapa", "sisa anggaran transport", "limit transport masih berapa"), "70.000", "Transport")
    }

    @Test fun shortFollowUpsReuseOnlyThePreviousTopic() = runBlocking {
        val history = listOf(ChatMessage(sender = ChatSender.USER, text = "pengeluaran makan dari BCA bulan ini"),
            ChatMessage(sender = ChatSender.ASSISTANT, text = "Rp10.000"), ChatMessage(sender = ChatSender.USER, text = "kalau kemarin?"))
        val answer = FinancialInsightService.sendChatMessage(null, history, context()).getOrThrow()
        assertTrue(answer, answer.contains("10.000") && answer.contains("2026-10-16") && answer.contains("BCA"))
        val income = FinancialInsightService.sendChatMessage(null, history + ChatMessage(sender = ChatSender.USER, text = "pemasukan januari 2026"), context()).getOrThrow()
        assertTrue(income, income.contains("1.000.000"))
        val named = listOf(ChatMessage(sender = ChatSender.USER, text = "pengeluaran september 2026"),
            ChatMessage(sender = ChatSender.USER, text = "kalau kemarin?"))
        val yesterday = FinancialInsightService.sendChatMessage(null, named, context()).getOrThrow()
        assertTrue(yesterday, yesterday.contains("10.000") && yesterday.contains("2026-10-16"))
        val september = FinancialInsightService.sendChatMessage(null, history + ChatMessage(sender = ChatSender.USER, text = "kalau september 2026?"), context()).getOrThrow()
        assertTrue(september, september.contains("Rp 0") && september.contains("BCA") && september.contains("2026-09-01"))
    }

    @Test fun invalidOrUnknownRequestsDoNotSilentlyUseThisMonth() {
        listOf("pengeluaran 2026-02-30", "belanja dari 2026-10-17 sampai 2026-10-01", "pengeluaran abad lalu").forEach {
            val answer = FinancialInsightService.evaluateOfflineQuery(it, context())
            assertTrue("$it\n$answer", answer.contains("tanggal", true) || answer.contains("periode", true))
            assertFalse(answer, answer.contains("100.000"))
        }
        val unknown = FinancialInsightService.evaluateOfflineQuery("berapa suhu matahari", context())
        assertTrue(unknown, unknown.contains("belum memahami", true) || unknown.contains("belum bisa", true))
    }

    @Test fun missingCategoriesAndEmptyLedgerStillGiveHonestTotals() {
        val ctx = context()
        val answer = FinancialInsightService.evaluateOfflineQuery("pengeluaran hari ini", ctx.copy(transactions = ledger + tx("Tanpa kategori", 7_000, "2026-10-17", null)))
        assertTrue(answer, answer.contains("27.000"))
        val empty = FinancialInsightService.evaluateOfflineQuery("pengeluaran hari ini", ctx.copy(transactions = emptyList()))
        assertTrue(empty, empty.contains("Rp 0") && empty.contains("Belum ada"))
    }

    @Test fun cashflowAndComparisonsUseNetAmountsAndEqualPeriods() {
        check(listOf("arus kas kemarin", "sisa pemasukan kemarin", "cashflow kemarin"), "190.000", "200.000", "10.000")
        check(listOf("bandingkan pengeluaran bulan ini dengan bulan lalu", "pengeluaran bulan lalu vs bulan ini"), "100.000", "110.000", "-10.000")
        check(listOf("bandingkan arus kas bulan ini dengan bulan lalu"), "100.000", "-110.000", "210.000")
        check(listOf("bandingkan belanja minggu ini dengan minggu lalu"), "60.000", "2026-10-05 sampai 2026-10-11")
    }

    @Test fun unknownAccountAndUnsupportedTimeAskForClarification() {
        listOf("saldo Mandiri berapa", "pengeluaran hari Senin", "belanja 12/10/2026", "pengeluaran 2 bulan lalu").forEach {
            val answer = FinancialInsightService.evaluateOfflineQuery(it, context())
            assertTrue("$it\n$answer", answer.contains("belum", true))
            assertFalse(answer, answer.contains("100.000"))
        }
    }

    @Test fun chatCannotPretendToMutateTheLedger() {
        listOf("hapus pengeluaran hari ini", "catat beli kopi 20 ribu", "ubah saldo BCA").forEach {
            val answer = FinancialInsightService.evaluateOfflineQuery(it, context())
            assertTrue("$it\n$answer", answer.contains("belum bisa mengubah"))
        }
    }

    @Test fun missingRawLedgerDoesNotPretendItKnowsDailySpending() {
        val answer = FinancialInsightService.evaluateOfflineQuery("pengeluaran kemarin", context().copy(transactions = null))
        assertTrue(answer, answer.contains("data transaksi", true))
        assertFalse(answer, answer.contains("100.000"))
    }

    @Test fun genericBelanjaDoesNotAccidentallySelectTheBelanjaCategory() {
        val ctx = context().copy(categories = categories + Category("cat_belanja", "Belanja", "expense"),
            transactions = ledger + tx("Baju", 50_000, "2026-10-17", "cat_belanja"))
        val total = FinancialInsightService.evaluateOfflineQuery("belanja bulan ini", ctx)
        assertTrue(total, total.contains("150.000"))
        val category = FinancialInsightService.evaluateOfflineQuery("pengeluaran kategori belanja bulan ini", ctx)
        assertTrue(category, category.contains("50.000"))
        assertFalse(category, category.contains("150.000"))
    }

    @Test fun historicalSnapshotsDoNotUseCurrentBalancesOrCurrentBudgetProgress() {
        listOf("saldo BCA bulan lalu", "budget transport minggu lalu", "target laptop september 2026").forEach {
            val answer = FinancialInsightService.evaluateOfflineQuery(it, context())
            assertTrue("$it\n$answer", answer.contains("saat ini") && answer.contains("belum"))
            assertFalse(answer, answer.contains("2.000.000") || answer.contains("70.000"))
        }
    }

    @Test fun shortAndCompositeCategoryLabelsHaveEverydayAliases() {
        val extra = listOf(Category("cat_kopi_jajan", "Kopi/Jajan", "expense"), Category("cat_bensin", "BBM", "expense"),
            Category("cat_bahan_makanan", "Dapur", "expense"), Category("cat_bonus", "Bonus/THR", "income"))
        val rows = listOf(tx("Kopi", 5_000, "2026-10-17", extra[0].id), tx("Bensin", 15_000, "2026-10-17", extra[1].id),
            tx("Bahan makanan", 25_000, "2026-10-17", extra[2].id), tx("Bonus kerja", 80_000, "2026-10-17", extra[3].id, type = "income"))
        val ctx = context().copy(categories = categories + extra, transactions = ledger + rows)
        listOf("pengeluaran kopi bulan ini" to "5.000", "belanja bensin bulan ini" to "15.000",
            "pengeluaran bahan makanan bulan ini" to "25.000", "bonus bulan ini berapa" to "80.000").forEach { (query, amount) ->
            val answer = FinancialInsightService.evaluateOfflineQuery(query, ctx)
            assertTrue("$query\n$answer", answer.contains("total **Rp $amount**"))
        }
    }

    @Test fun accountNamesDoNotOverrideTheQuestionIntent() {
        val ctx = context().copy(assets = context().assets.map { if (it.id == "bca") it.copy(name = "Audit Cash") else it })
        val answer = FinancialInsightService.evaluateOfflineQuery("pengeluaran makan dari Audit Cash bulan ini", ctx)
        assertTrue(answer, answer.contains("10.000") && answer.contains("Makan · Audit Cash"))
        assertFalse(answer, answer.contains("Diagnosis Vina"))
    }

    @Test fun lastTransactionDoesNotUseTheEntireHistoryAsItsAmount() {
        val answer = FinancialInsightService.evaluateOfflineQuery("pengeluaran terakhir", context())
        assertTrue(answer, answer.contains("1 transaksi**, total **Rp 20.000"))
        assertFalse(answer, answer.contains("210.000"))
    }

    @Test fun ordinaryTypingErrorsStillReadTheRequestedDay() {
        listOf("Bagaimana penegeluaran saya kemarin", "pengeluarran kemarin", "pengeluarn kemarin", "pemasuukan kemarin").forEach { query ->
            val answer = FinancialInsightService.evaluateOfflineQuery(query, context())
            val expected = if (query.startsWith("pemasuukan")) "200.000" else "10.000"
            assertTrue("$query\n$answer", answer.contains("2026-10-16") && answer.contains(expected))
            assertFalse(answer, answer.contains("Analisis Tren Arus Kas"))
        }
    }

    @Test fun unknownDailyQuestionNeverFallsBackToMonthlyComparison() {
        val answer = FinancialInsightService.evaluateOfflineQuery("bagaimana catatanxyz saya kemarin", context())
        assertTrue(answer, answer.contains("belum memahami"))
        assertFalse(answer, answer.contains("Analisis Tren Arus Kas") || answer.contains("100.000"))
    }

    @Test fun typoCorrectionPreservesAccountNamesInOfflineChat() = runBlocking {
        val ctx = context().copy(assets = context().assets.map { if (it.id == "bca") it.copy(name = "Transaksii") else it })
        val answer = FinancialInsightService.sendChatMessage(null, listOf(ChatMessage(sender = ChatSender.USER,
            text = "penegeluaran dari Transaksii kemarin")), ctx).getOrThrow()
        assertTrue(answer, answer.contains("Transaksii") && answer.contains("10.000") && answer.contains("2026-10-16"))
    }
}
