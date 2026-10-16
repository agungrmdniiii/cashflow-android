package com.cashflow.app

import com.cashflow.app.data.model.*
import com.cashflow.app.data.service.CategoryDelta
import com.cashflow.app.data.service.FinancialChatContext
import com.cashflow.app.data.service.FinancialInsightService
import com.cashflow.app.data.service.MonthComparisonResult
import org.junit.Assert.*
import org.junit.Test

class FinancialChatAssistantTest {

    @Test fun previousMonthExpenseUnderstandsTyposAndEverydayPhrases() {
        val context = createSampleContext()
        val phrases = listOf(
            "bagaimana pengeleruan saya bulan lagu",
            "berapa pengeluran saya bln lalu?",
            "pengluaran bulan kemarin berapa",
            "Halo Vina, bagaimana pengeluaran saya bulan lalu?",
            "Vina pengeluaran bulan lalu berapa?",
            "BAGAIMANA PENGELUARAN SAYA BULAN LALU?"
        )
        phrases.forEach { query ->
            val answer = FinancialInsightService.evaluateOfflineQuery(query, context)
            assertTrue(query, answer.contains("Bulan Lalu (2026-09)"))
            assertTrue(query, answer.contains("6.000.000"))
            assertTrue(query, answer.contains("2.500.000"))
            assertFalse("Jangan menjawab bulan berjalan: $query", answer.contains("4.500.000"))
            assertFalse("Jangan menyapa ulang: $query", answer.contains("Saya Vina"))
        }
    }

    @Test fun previousMonthCategoriesUseTheirOwnValuesAndRanking() {
        val context = createSampleContext()
        val categories = context.topCategories.map {
            if (it.categoryId == "cat_transport") it.copy(prevAmount = 3_000_000L) else it
        }
        val answer = FinancialInsightService.evaluateOfflineQuery("Pengeluaran terbesar bulan lalu?", context.copy(topCategories = categories))
        assertTrue(answer, answer.indexOf("Transportasi") < answer.indexOf("Makanan & Minuman"))
        assertTrue(answer, answer.contains("3.000.000"))
        assertFalse(answer, answer.contains("1.200.000"))
    }

    @Test fun previousMonthIncomeDoesNotAnswerWithCurrentIncome() {
        val answer = FinancialInsightService.evaluateOfflineQuery("Vina pemasukkan bln lalu berapa?", createSampleContext())
        assertTrue(answer, answer.contains("Bulan Lalu (2026-09)"))
        assertTrue(answer, answer.contains("9.000.000"))
        assertFalse(answer, answer.contains("10.000.000"))
    }

    @Test fun greetingAndTyposStillAllowExplicitMonthComparison() {
        listOf("Halo Vina, bandingkan pengeluran bulan lalu dan bulan ini",
            "pengeluaran bulan lalu vs bulan ini").forEach { query ->
            val answer = FinancialInsightService.evaluateOfflineQuery(query, createSampleContext())
            assertTrue(query, answer.contains("Analisis Tren Arus Kas"))
            assertTrue(query, answer.contains("6.000.000") && answer.contains("4.500.000"))
        }
    }

    @Test fun emptyPreviousMonthDoesNotBorrowCurrentMonthCategories() {
        val context = createSampleContext()
        val answer = FinancialInsightService.evaluateOfflineQuery("pengeluaran bulan lalu", context.copy(
            currentMonthStats = context.currentMonthStats.copy(prevMonthTotalExpense = 0),
            topCategories = context.topCategories.map { it.copy(prevAmount = 0) }
        ))
        assertTrue(answer, answer.contains("Belum ada pengeluaran tercatat"))
        assertFalse(answer, answer.contains("2.000.000"))
    }

    @Test fun greetingDoesNotOverrideCurrentMonthExpenseQuestion() {
        val answer = FinancialInsightService.evaluateOfflineQuery("Halo Vina, berapa pengeluaran bulan ini?", createSampleContext())
        assertTrue(answer, answer.contains("4.500.000"))
        assertFalse(answer, answer.contains("Saya Vina"))
    }

    @Test fun offlineChatPathUsesTheSameTypoAndPeriodUnderstanding() = kotlinx.coroutines.runBlocking {
        val result = FinancialInsightService.sendChatMessage(null, listOf(
            ChatMessage(sender = ChatSender.USER, text = "bagaimana pengeleruan saya bulan lagu")
        ), createSampleContext()).getOrThrow()
        assertTrue(result, result.contains("6.000.000"))
        assertTrue(result, result.contains("Bulan Lalu (2026-09)"))
        assertFalse(result, result.contains("4.500.000"))
    }

    private fun createSampleContext(): FinancialChatContext {
        val sampleStats = MonthComparisonResult(
            currentMonthTotalExpense = 4_500_000L,
            prevMonthTotalExpense = 6_000_000L,
            expenseDifference = -1_500_000L,
            expensePercentChange = -25.0,
            currentMonthTotalIncome = 10_000_000L,
            prevMonthTotalIncome = 9_000_000L,
            incomeDifference = 1_000_000L,
            incomePercentChange = 11.1,
            savingsRate = 0.55,
            categoryDeltas = listOf(
                CategoryDelta(
                    categoryId = "cat_food",
                    categoryName = "Makanan & Minuman",
                    currentAmount = 2_000_000L,
                    prevAmount = 2_500_000L,
                    diff = -500_000L,
                    percentChange = -20.0
                ),
                CategoryDelta(
                    categoryId = "cat_transport",
                    categoryName = "Transportasi",
                    currentAmount = 1_200_000L,
                    prevAmount = 800_000L,
                    diff = 400_000L,
                    percentChange = 50.0
                )
            ),
            averageDailyExpense = 281_250L,
            highestExpenseDay = "2026-10-05",
            highestExpenseDayAmount = 1_200_000L
        )

        return FinancialChatContext(
            userName = "Budi",
            currentDate = "2026-10-16",
            totalLiquidBalance = 15_500_000L,
            assets = listOf(
                Asset(id = "a1", name = "BCA", type = "Bank", openingBalance = 10_000_000L, currentBalance = 12_500_000L),
                Asset(id = "a2", name = "Dompet Tunai", type = "Tunai", openingBalance = 1_000_000L, currentBalance = 3_000_000L)
            ),
            currentMonthStats = sampleStats,
            prevMonthPrefix = "2026-09",
            currentMonthPrefix = "2026-10",
            topCategories = sampleStats.categoryDeltas,
            budgets = listOf(
                BudgetWithProgress(
                    budget = Budget(id = "b1", categoryId = "cat_food", targetAmount = 2_500_000L),
                    category = Category(id = "cat_food", name = "Makanan & Minuman", icon = "food", color = "#FF0000", type = "expense"),
                    spentAmount = 2_000_000L,
                    remainingAmount = 500_000L,
                    progressPercentage = 0.80f,
                    status = BudgetStatus.WARNING
                )
            ),
            debts = listOf(
                Debt(
                    id = "d1",
                    title = "Cicilan Laptop",
                    lenderName = "Bank Mandiri",
                    totalAmount = 10_000_000L,
                    paidAmount = 6_000_000L,
                    dueDate = "2026-12-31"
                )
            ),
            goals = listOf(
                FinancialGoal(
                    id = "g1",
                    name = "Dana Darurat",
                    targetAmount = 30_000_000L,
                    currentAmount = 15_000_000L,
                    deadline = "2026-12-31"
                )
            )
        )
    }

    @Test
    fun testBuildFinancialSystemInstruction_includesComprehensiveRealData() {
        val context = createSampleContext()
        val instruction = FinancialInsightService.buildFinancialSystemInstruction(context)

        // Verifikasi instruksi sistem memuat data riil secara akurat tanpa template kosong
        assertTrue("Harus menyapa nama pengguna", instruction.contains("Budi"))
        assertTrue("Harus memuat saldo likuid riil", instruction.contains("15.500.000"))
        assertTrue("Harus memuat rincian aset BCA", instruction.contains("BCA"))
        assertTrue("Harus memuat pengeluaran bulan berjalan", instruction.contains("4.500.000"))
        assertTrue("Harus memuat pengeluaran bulan lalu", instruction.contains("6.000.000"))
        assertTrue("Harus memuat persentase perubahan pengeluaran", instruction.contains("-25.0%"))
        assertTrue("Harus memuat savings rate riil", instruction.contains("55%"))
        assertTrue("Harus memuat kategori makanan dan transportasi", instruction.contains("Makanan & Minuman") && instruction.contains("Transportasi"))
        assertTrue("Harus memuat anggaran dan statusnya", instruction.contains("Makanan & Minuman") && instruction.contains("2.500.000"))
        assertTrue("Harus memuat utang aktif", instruction.contains("Cicilan Laptop") && instruction.contains("Bank Mandiri"))
    }

    @Test
    fun testEvaluateOfflineQuery_trendQuery_calculatesAccurately() {
        val context = createSampleContext()
        val response = FinancialInsightService.evaluateOfflineQuery("Bagaimana tren arus kas saya bulan lalu?", context)

        // Respons harus menghitung perbedaan riil antara bulan lalu dan bulan ini
        assertTrue("Harus menyertakan header tren", response.contains("Analisis Tren Arus Kas"))
        assertTrue("Harus memuat nominal pengeluaran bulan lalu", response.contains("6.000.000"))
        assertTrue("Harus memuat nominal pengeluaran bulan ini", response.contains("4.500.000"))
        assertTrue("Harus mencatat penghematan 1.500.000", response.contains("1.500.000") || response.contains("25.0%"))
        assertTrue("Harus menyebutkan savings rate 55%", response.contains("55%"))
    }

    @Test
    fun testEvaluateOfflineQuery_topSpendingQuery_listsCategories() {
        val context = createSampleContext()
        val response = FinancialInsightService.evaluateOfflineQuery("Kategori apa pengeluaran terbesar saya?", context)

        assertTrue("Harus memuat judul rincian pengeluaran", response.contains("Pengeluaran Terbesar"))
        assertTrue("Harus menyebutkan Makanan & Minuman sebesar 2.000.000", response.contains("Makanan & Minuman") && response.contains("2.000.000"))
    }

    @Test
    fun testEvaluateOfflineQuery_balanceQuery_listsAssets() {
        val context = createSampleContext()
        val response = FinancialInsightService.evaluateOfflineQuery("Berapa total saldo dan kondisi aset saya?", context)

        assertTrue("Harus memuat total saldo", response.contains("15.500.000"))
        assertTrue("Harus merinci rekening BCA", response.contains("BCA"))
        assertTrue("Harus merinci Dompet Tunai", response.contains("Dompet Tunai"))
    }

    @Test
    fun testEvaluateOfflineQuery_budgetQuery_reportsBudgetStatus() {
        val context = createSampleContext()
        val response = FinancialInsightService.evaluateOfflineQuery("Apakah anggaran belanja saya masih aman?", context)

        assertTrue("Harus memuat status anggaran", response.contains("Status Anggaran Belanja"))
        assertTrue("Harus memuat kategori makanan terpakai 2.000.000", response.contains("Makanan & Minuman") && response.contains("2.000.000"))
    }

    @Test
    fun testEvaluateOfflineQuery_debtQuery_reportsActiveDebts() {
        val context = createSampleContext()
        val response = FinancialInsightService.evaluateOfflineQuery("Berapa sisa utang yang harus saya bayar?", context)

        assertTrue("Harus memuat ringkasan catatan utang", response.contains("Catatan Utang"))
        assertTrue("Harus memuat Cicilan Laptop sebesar 4.000.000 sisa", response.contains("Cicilan Laptop") && response.contains("4.000.000"))
    }

    @Test
    fun testExtractEstimatedAmount_parsesIndonesianFormatsAccurately() {
        assertEquals(3_000_000L, FinancialInsightService.extractEstimatedAmount("Bolehkah saya beli hp seharga 3 juta?"))
        assertEquals(2_500_000L, FinancialInsightService.extractEstimatedAmount("Mau beli meja 2.5 juta"))
        assertEquals(500_000L, FinancialInsightService.extractEstimatedAmount("Apakah aman beli sepatu 500rb?"))
        assertEquals(250_000L, FinancialInsightService.extractEstimatedAmount("Beli tas 250k"))
        assertEquals(15_000_000L, FinancialInsightService.extractEstimatedAmount("Beli laptop 15.000.000"))
        assertEquals(75_000L, FinancialInsightService.extractEstimatedAmount("Beli makan 75000"))
        assertNull(FinancialInsightService.extractEstimatedAmount("Bagaimana tren pengeluaran saya?"))
    }

    @Test
    fun testEvaluateOfflineQuery_greetingQuery_introducesVina() {
        val context = createSampleContext()
        val response = FinancialInsightService.evaluateOfflineQuery("Halo Vina, kamu siapa?", context)

        assertTrue("Harus memperkenalkan diri sebagai Vina", response.contains("Vina"))
        assertTrue("Harus menyapa nama pengguna Budi", response.contains("Budi"))
        assertTrue("Harus memuat ringkasan saldo likuid", response.contains("15.500.000"))
    }

    @Test
    fun testEvaluateOfflineQuery_healthAuditQuery_calculatesScoreAndCondition() {
        val context = createSampleContext()
        val response = FinancialInsightService.evaluateOfflineQuery("Bagaimana audit kesehatan keuangan saya?", context)

        assertTrue("Harus memuat judul Evaluasi Kesehatan Finansial", response.contains("Evaluasi Kesehatan Finansial"))
        assertTrue("Harus memuat skor kesehatan /100", response.contains("/100"))
        assertTrue("Harus memuat rasio tabungan", response.contains("Rasio Tabungan"))
        assertTrue("Harus memuat beban cicilan", response.contains("Beban Cicilan"))
    }

    @Test
    fun testEvaluateOfflineQuery_purchaseSimulation_evaluatesAffordability() {
        val context = createSampleContext()
        // Tes dengan barang 3 juta
        val response = FinancialInsightService.evaluateOfflineQuery("Bolehkah saya beli gadget 3 juta?", context)

        assertTrue("Harus memuat judul Simulasi Kelayakan Pembelian", response.contains("Simulasi Kelayakan Pembelian"))
        assertTrue("Harus mencatat estimasi harga 3.000.000", response.contains("3.000.000"))
        assertTrue("Harus menyebutkan cadangan aman atau saldo likuid", response.contains("15.500.000"))
    }

    @Test
    fun testEvaluateOfflineQuery_tipsQuery_providesActionableAdvice() {
        val context = createSampleContext()
        val response = FinancialInsightService.evaluateOfflineQuery("Berikan saya tips berhemat bulan ini", context)

        assertTrue("Harus memuat judul Tips & Solusi Penghematan", response.contains("Tips & Solusi Penghematan"))
        assertTrue("Harus menyoroti kategori pengeluaran terbesar", response.contains("Makanan & Minuman"))
        assertTrue("Harus mereferensikan aturan 50-30-20", response.contains("50/30/20"))
    }

    @Test
    fun testEvaluateOfflineQuery_incomeQuery_detailsEarningsAndSavings() {
        val context = createSampleContext()
        val response = FinancialInsightService.evaluateOfflineQuery("Berapa total pemasukan saya bulan ini?", context)

        assertTrue("Harus memuat judul Analisis Pemasukan", response.contains("Analisis Pemasukan & Penghasilan"))
        assertTrue("Harus memuat pemasukan bulan ini 10.000.000", response.contains("10.000.000"))
        assertTrue("Harus memuat pemasukan bulan lalu 9.000.000", response.contains("9.000.000"))
        assertTrue("Harus memuat kenaikan pemasukan", response.contains("1.000.000") || response.contains("11.1%"))
    }

    @Test
    fun testEvaluateOfflineQuery_dailyBurnRateQuery_calculatesProjection() {
        val context = createSampleContext()
        val response = FinancialInsightService.evaluateOfflineQuery("Berapa rata-rata pengeluaran harian dan burn rate saya?", context)

        assertTrue("Harus memuat judul Rata-rata Harian & Burn Rate", response.contains("Rata-rata Pengeluaran Harian"))
        assertTrue("Harus memuat rata-rata harian 281.250", response.contains("281.250"))
        assertTrue("Harus memuat hari belanja puncak", response.contains("2026-10-05") || response.contains("1.200.000"))
    }
}
