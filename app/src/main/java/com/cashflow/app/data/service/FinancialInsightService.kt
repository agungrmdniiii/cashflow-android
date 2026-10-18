package com.cashflow.app.data.service

import com.cashflow.app.data.model.Asset
import com.cashflow.app.data.model.BudgetWithProgress
import com.cashflow.app.data.model.Category
import com.cashflow.app.data.model.ChatMessage
import com.cashflow.app.data.model.ChatSender
import com.cashflow.app.data.model.Debt
import com.cashflow.app.data.model.FinancialGoal
import com.cashflow.app.data.model.Transaction
import com.cashflow.app.data.model.TransactionType
import com.cashflow.app.ui.components.Formatters
import com.cashflow.app.data.network.ResilientNetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class FinancialChatContext(
    val userName: String,
    val currentDate: String,
    val totalLiquidBalance: Long,
    val assets: List<Asset>,
    val currentMonthStats: MonthComparisonResult,
    val prevMonthPrefix: String,
    val currentMonthPrefix: String,
    val topCategories: List<CategoryDelta>,
    val budgets: List<BudgetWithProgress>,
    val debts: List<Debt>,
    val goals: List<FinancialGoal>,
    val transactions: List<Transaction>? = null,
    val categories: List<Category> = emptyList()
)

data class RuleBasedInsight(
    val title: String,
    val message: String,
    val type: String // "warning", "info", "positive"
)
typealias FinancialInsight = RuleBasedInsight

data class MonthComparisonResult(
    val currentMonthTotalExpense: Long,
    val prevMonthTotalExpense: Long,
    val expenseDifference: Long,
    val expensePercentChange: Double,
    val currentMonthTotalIncome: Long,
    val prevMonthTotalIncome: Long,
    val incomeDifference: Long,
    val incomePercentChange: Double,
    val savingsRate: Double, // (Income - Expense) / Income
    val categoryDeltas: List<CategoryDelta>,
    val averageDailyExpense: Long,
    val highestExpenseDay: String? = null,
    val highestExpenseDayAmount: Long = 0L
)
typealias MonthComparisonStats = MonthComparisonResult

data class CategoryDelta(
    val categoryId: String,
    val categoryName: String,
    val currentAmount: Long,
    val prevAmount: Long,
    val diff: Long,
    val percentChange: Double
)

object FinancialInsightService {

    internal suspend fun withGeminiModelFallback(call: suspend (String) -> Result<String>): Result<String> {
        val primary = call("gemini-3.8-flash")
        currentCoroutineContext().ensureActive()
        if (primary.isSuccess || primary.exceptionOrNull()?.message?.startsWith("HTTP 404:") != true) return primary
        return call("gemini-3-flash")
    }

    /**
     * Menghitung statistik mendalam dan perbandingan antara bulan berjalan dan bulan sebelumnya.
     */
    fun computeMonthComparison(
        transactions: List<Transaction>,
        categories: List<Category>,
        currentMonthDate: LocalDate = LocalDate.now()
    ): MonthComparisonResult {
        val currentMonthPrefix = currentMonthDate.toString().substring(0, 7) // "YYYY-MM"
        val prevMonthDate = currentMonthDate.minusMonths(1)
        val prevMonthPrefix = prevMonthDate.toString().substring(0, 7)

        val catMap = categories.associateBy { it.id }

        // Filter transaksi aktual (abaikan adjustment dan sample)
        val validTxs = transactions.filter { !it.isAdjustment && it.type != TransactionType.ADJUSTMENT.value }

        val currentMonthTxs = validTxs.filter { it.transactionDate.startsWith(currentMonthPrefix) }
        val prevMonthTxs = validTxs.filter { it.transactionDate.startsWith(prevMonthPrefix) }

        val curExpense = currentMonthTxs.filter { it.type == TransactionType.EXPENSE.value }.sumOf { it.amount }
        val prevExpense = prevMonthTxs.filter { it.type == TransactionType.EXPENSE.value }.sumOf { it.amount }

        val curIncome = currentMonthTxs.filter { it.type == TransactionType.INCOME.value }.sumOf { it.amount }
        val prevIncome = prevMonthTxs.filter { it.type == TransactionType.INCOME.value }.sumOf { it.amount }

        val expenseDiff = curExpense - prevExpense
        val expensePct = if (prevExpense > 0) (expenseDiff.toDouble() / prevExpense) * 100.0 else 0.0

        val incomeDiff = curIncome - prevIncome
        val incomePct = if (prevIncome > 0) (incomeDiff.toDouble() / prevIncome) * 100.0 else 0.0

        val savingsRate = if (curIncome > 0) ((curIncome - curExpense).toDouble() / curIncome) else 0.0

        // Per kategori delta
        val curCatSpent = currentMonthTxs.filter { it.type == TransactionType.EXPENSE.value && it.categoryId != null }
            .groupBy { it.categoryId!! }
            .mapValues { (_, txs) -> txs.sumOf { it.amount } }

        val prevCatSpent = prevMonthTxs.filter { it.type == TransactionType.EXPENSE.value && it.categoryId != null }
            .groupBy { it.categoryId!! }
            .mapValues { (_, txs) -> txs.sumOf { it.amount } }

        val allCategoryIds = (curCatSpent.keys + prevCatSpent.keys).toSet()
        val categoryDeltas = allCategoryIds.map { catId ->
            val cSpent = curCatSpent[catId] ?: 0L
            val pSpent = prevCatSpent[catId] ?: 0L
            val diff = cSpent - pSpent
            val pct = if (pSpent > 0) (diff.toDouble() / pSpent) * 100.0 else if (cSpent > 0) 100.0 else 0.0
            CategoryDelta(
                categoryId = catId,
                categoryName = catMap[catId]?.name ?: "Lainnya",
                currentAmount = cSpent,
                prevAmount = pSpent,
                diff = diff,
                percentChange = pct
            )
        }.sortedByDescending { it.currentAmount }

        // Rata-rata harian bulan ini
        val dayOfMonth = currentMonthDate.dayOfMonth.coerceAtLeast(1)
        val avgDailyExpense = curExpense / dayOfMonth

        // Hari paling boros
        val expenseByDate = currentMonthTxs.filter { it.type == TransactionType.EXPENSE.value }
            .groupBy { it.transactionDate }
            .mapValues { (_, txs) -> txs.sumOf { it.amount } }

        val highestDay = expenseByDate.maxByOrNull { it.value }

        return MonthComparisonResult(
            currentMonthTotalExpense = curExpense,
            prevMonthTotalExpense = prevExpense,
            expenseDifference = expenseDiff,
            expensePercentChange = expensePct,
            currentMonthTotalIncome = curIncome,
            prevMonthTotalIncome = prevIncome,
            incomeDifference = incomeDiff,
            incomePercentChange = incomePct,
            savingsRate = savingsRate,
            categoryDeltas = categoryDeltas,
            averageDailyExpense = avgDailyExpense,
            highestExpenseDay = highestDay?.key,
            highestExpenseDayAmount = highestDay?.value ?: 0L
        )
    }

    /** Local facts only: partial-month totals are explicitly compared with the full previous month. */
    fun generateLocalRuleInsights(
        stats: MonthComparisonResult,
        budgets: List<BudgetWithProgress> = emptyList(),
        debts: List<Debt> = emptyList(),
        goals: List<FinancialGoal> = emptyList(),
        today: LocalDate = LocalDate.now()
    ): List<RuleBasedInsight> {
        val insights = mutableListOf<RuleBasedInsight>()
        fun add(title: String, message: String, type: String = "info") {
            insights.add(RuleBasedInsight(title, message, type))
        }
        fun money(amount: Long) = Formatters.formatRupiah(amount)
        val expense = stats.currentMonthTotalExpense
        val income = stats.currentMonthTotalIncome
        when {
            expense > income && income > 0 -> add("Pengeluaran melebihi pemasukan",
                "Bulan ini tercatat defisit ${money(expense - income)}. Tinjau pengeluaran sebelum menambah belanja.", "warning")
            expense > 0 && income == 0L -> add("Pemasukan belum tercatat",
                "Pengeluaran bulan ini ${money(expense)} tanpa pemasukan tercatat. Pastikan catatan pemasukan sudah lengkap.", "warning")
            income > expense -> add("Arus kas positif",
                "Selisih pemasukan dan pengeluaran bulan ini ${money(income - expense)}. Selisih ini belum tentu menjadi tabungan.", "positive")
        }
        if (stats.prevMonthTotalExpense > 0 && expense > 0 && kotlin.math.abs(stats.expensePercentChange) >= 10) {
            val direction = if (stats.expenseDifference > 0) "lebih tinggi" else "lebih rendah"
            add("Perbandingan pengeluaran", "Pengeluaran bulan berjalan $direction ${money(kotlin.math.abs(stats.expenseDifference))} " +
                "(${kotlin.math.abs(stats.expensePercentChange).toInt()}%) dibanding total sebulan penuh sebelumnya. Bulan berjalan belum tentu selesai.",
                if (stats.expenseDifference > 0) "warning" else "info")
        }
        if (stats.prevMonthTotalIncome > 0 && income != stats.prevMonthTotalIncome) {
            add("Perbandingan pemasukan", "Pemasukan bulan berjalan ${money(income)}; total sebulan penuh sebelumnya ${money(stats.prevMonthTotalIncome)}. Periksa apakah seluruh pemasukan sudah dicatat.")
        }
        stats.categoryDeltas.maxByOrNull { it.currentAmount }?.takeIf { it.currentAmount > 0 && expense > 0 }?.let {
            add("Pos pengeluaran terbesar", "${it.categoryName} sebesar ${money(it.currentAmount)}, sekitar ${(it.currentAmount.toDouble() / expense * 100).toInt()}% dari pengeluaran bulan ini.")
        }
        stats.categoryDeltas.filter { it.diff > 0 && it.prevAmount > 0 }.maxByOrNull { it.diff }?.let {
            add("Kenaikan kategori terbesar", "${it.categoryName} bertambah ${money(it.diff)} dibanding total bulan sebelumnya. Periksa rinciannya untuk mengetahui transaksi yang berkontribusi.")
        }
        if (expense > 0 && stats.highestExpenseDay != null && stats.highestExpenseDayAmount.toDouble() / expense >= .35) {
            add("Pengeluaran terkonsentrasi", "Tanggal ${stats.highestExpenseDay} mencatat ${money(stats.highestExpenseDayAmount)} dari total ${money(expense)} bulan ini. Periksa apakah termasuk kebutuhan sekali bayar.")
        }
        if (stats.averageDailyExpense > 0) add("Rata-rata pengeluaran", "Bulan berjalan mencatat rata-rata ${money(stats.averageDailyExpense)} per hari kalender. Ini ringkasan catatan, bukan perkiraan tagihan berikutnya.")
        budgets.filter { it.effectiveTargetAmount > 0 && it.spentAmount.toDouble() / it.effectiveTargetAmount >= .85 }
            .sortedBy { it.remainingAmount }.firstOrNull()?.let {
                val category = it.category?.name ?: "Kategori tanpa nama"
                if (it.remainingAmount < 0) add("Anggaran terlampaui", "$category melewati batas sebesar ${money(-it.remainingAmount)} pada periode anggaran aktif.", "warning")
                else add("Anggaran mendekati batas", "$category menyisakan ${money(it.remainingAmount)} dari batas ${money(it.effectiveTargetAmount)} pada periode aktif.", "warning")
            }
        debts.filter { it.direction != "receivable" && it.status != "paid" && it.remainingAmount > 0 }
            .mapNotNull { debt -> runCatching { LocalDate.parse(debt.dueDate) }.getOrNull()?.let { debt to it } }
            .filter { (_, date) -> !date.isAfter(today.plusDays(7)) }.minByOrNull { it.second }?.let { (debt, date) ->
                add(if (date.isBefore(today)) "Utang lewat jatuh tempo" else "Jatuh tempo terdekat",
                    "${debt.lenderName}: sisa ${money(debt.remainingAmount)}, jatuh tempo $date. Periksa status pembayaran.", "warning")
            }
        goals.filter { it.targetAmount > 0 && it.currentAmount > 0 }
            .maxByOrNull { it.progressPercentage }?.let {
                if (it.remainingAmount == 0L) add("Target tercapai", "Catatan target ${it.name} telah mencapai ${money(it.targetAmount)}. Pastikan nominalnya masih sesuai.", "positive")
                else add("Progres target", "${it.name} terkumpul ${money(it.currentAmount)} dari ${money(it.targetAmount)}. Sisa target ${money(it.remainingAmount)}.", "info")
            }
        return insights.sortedBy { when (it.type) { "warning" -> 0; "info" -> 1; else -> 2 } }
    }

    /**
     * Mempersiapkan payload JSON agregat transparan untuk Gemini API.
     * HANYA berisi agregat angka kategori, persentase, dan total — TANPA catatan, nama rekening, atau rincian mentah.
     */
    fun buildAggregatedAiPayload(stats: MonthComparisonResult): String {
        val root = JSONObject()
        root.put("bulan_ini_total_pengeluaran", stats.currentMonthTotalExpense)
        root.put("bulan_ini_total_pemasukan", stats.currentMonthTotalIncome)
        root.put("perubahan_pengeluaran_persen", stats.expensePercentChange.toInt())
        root.put("savings_rate_persen", (stats.savingsRate * 100).toInt())
        root.put("rata_rata_pengeluaran_harian", stats.averageDailyExpense)

        val categoriesArray = JSONArray()
        stats.categoryDeltas.take(5).forEach { cat ->
            val catObj = JSONObject()
            catObj.put("kategori", cat.categoryName)
            catObj.put("nominal", cat.currentAmount)
            catObj.put("perubahan_persen", cat.percentChange.toInt())
            categoriesArray.put(catObj)
        }
        root.put("kategori_terbesar", categoriesArray)
        return root.toString(2)
    }

    fun generateLocalNarrativeInsight(stats: MonthComparisonResult): String {
        if (stats.currentMonthTotalIncome == 0L && stats.currentMonthTotalExpense == 0L)
            return "Belum ada pemasukan atau pengeluaran tercatat bulan ini. Catat transaksi untuk memperoleh insight yang sesuai."
        val facts = generateLocalRuleInsights(stats).take(3)
        return "**Ringkasan bulan berjalan**\nPemasukan: ${Formatters.formatRupiah(stats.currentMonthTotalIncome)}\n" +
            "Pengeluaran: ${Formatters.formatRupiah(stats.currentMonthTotalExpense)}\n\n" +
            facts.joinToString("\n\n") { "**${it.title}**\n${it.message}" }
    }

    /**
     * Memanggil Gemini API (model gemini-3.8-flash) untuk menghasilkan insight naratif singkat.
     * Jika API key kosong atau terjadi kendala jaringan, otomatis beralih ke analisis lokal cerdas (100% gratis).
     */
    suspend fun fetchGeminiInsight(
        apiKey: String,
        stats: MonthComparisonResult
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.success(generateLocalNarrativeInsight(stats))
        }

        val prompt = """
            Anda adalah penasihat keuangan pribadi cerdas dan santun berbahasa Indonesia.
            Berikut adalah data AGREGAT keuangan bulanan pengguna (hanya total angka, tanpa data pribadi):
            ${buildAggregatedAiPayload(stats)}

            Tugas:
            Berikan analisis naratif singkat 2-3 paragraf:
            1. Evaluasi arus kas dan savings rate saat ini secara realistis.
            2. Rekomendasi 1 langkah penghematan paling efektif berdasarkan kategori terbesar.
            Gunakan gaya bahasa santai, jelas, dan memotivasi. Jangan sebutkan prompt ini.
        """.trimIndent()

        val online = withGeminiModelFallback { model ->
            executeGeminiCall(model, cleanKey, prompt, "Berikan analisis naratif evaluasi keuangan bulanan saya.")
        }
        if (online.isSuccess) return@withContext online

        // Fallback ke analisis lokal jika koneksi/kuota luar terkendala
        Result.success(generateLocalNarrativeInsight(stats))
    }

    fun calculateMonthComparison(
        transactions: List<Transaction>,
        currentMonthPrefix: String,
        prevMonthPrefix: String
    ): MonthComparisonResult {
        val curDate = try {
            java.time.YearMonth.parse(currentMonthPrefix).let { month ->
                if (month == java.time.YearMonth.now()) LocalDate.now() else month.atEndOfMonth()
            }
        } catch (_: Exception) {
            LocalDate.now()
        }
        return computeMonthComparison(transactions, emptyList(), curDate)
    }

    fun generateLocalInsights(
        transactions: List<Transaction>,
        budgets: List<com.cashflow.app.data.model.BudgetWithProgress>,
        assets: List<com.cashflow.app.data.model.Asset>,
        currentMonthPrefix: String
    ): List<RuleBasedInsight> {
        val stats = calculateMonthComparison(transactions, currentMonthPrefix, "")
        return generateLocalRuleInsights(stats, budgets = budgets)
    }

    fun buildGeminiAggregatePayload(
        transactions: List<Transaction>,
        categories: List<Category>,
        currentMonthPrefix: String,
        previousMonthPrefix: String
    ): JSONObject {
        val curDate = try {
            java.time.YearMonth.parse(currentMonthPrefix).let { month ->
                if (month == java.time.YearMonth.now()) LocalDate.now() else month.atEndOfMonth()
            }
        } catch (_: Exception) {
            LocalDate.now()
        }
        val stats = computeMonthComparison(transactions, categories, curDate)
        return JSONObject(buildAggregatedAiPayload(stats))
    }

    suspend fun fetchGeminiInsight(
        apiKey: String,
        transactions: List<Transaction>,
        categories: List<Category>,
        currentMonthPrefix: String,
        previousMonthPrefix: String
    ): Result<String> {
        val curDate = try {
            java.time.YearMonth.parse(currentMonthPrefix).let { month ->
                if (month == java.time.YearMonth.now()) LocalDate.now() else month.atEndOfMonth()
            }
        } catch (_: Exception) {
            LocalDate.now()
        }
        val stats = withContext(Dispatchers.Default) { computeMonthComparison(transactions, categories, curDate) }
        return fetchGeminiInsight(apiKey, stats)
    }

    /**
     * Membangun prompt sistem komprehensif berisi seluruh snapshot data keuangan nyata pengguna.
     */
    fun buildFinancialSystemInstruction(
        context: FinancialChatContext,
        conversationHistory: List<ChatMessage> = emptyList()
    ): String {
        val sb = StringBuilder()
        val userLabel = if (context.userName.isNotBlank()) context.userName.trim() else "Pengguna"
        sb.append("Anda adalah 'Vina', asisten dan penasihat keuangan pribadi cerdas dari aplikasi Duit Aing. ")
        sb.append("Gunakan bahasa Indonesia yang ringkas dan profesional: jawab inti dalam 2–4 kalimat atau maksimal 3 poin. Jangan mengulang perkenalan, memakai emoji berlebihan, atau menggurui. Bedakan fakta tercatat dari dugaan; jangan menyebut surplus sebagai tabungan atau saldo likuid sebagai kekayaan bersih. ")
        sb.append("Anda berbicara langsung kepada $userLabel dengan nada profesional dan ramah.\n\n")

        sb.append("=== DATA KEUANGAN NYATA PENGGUNA (SNAPSHOT HARI INI: ${context.currentDate}) ===\n")
        sb.append("1. Total Saldo Likuid: ${Formatters.formatRupiah(context.totalLiquidBalance)}\n")
        sb.append("   Rincian Aset:\n")
        if (context.assets.isEmpty()) {
            sb.append("   - Tidak ada aset terdaftar\n")
        } else {
            context.assets.forEach { a ->
                sb.append("   - ${a.name} (${a.type}): ${Formatters.formatRupiah(a.currentBalance)}\n")
            }
        }

        val cur = context.currentMonthStats
        sb.append("\n2. Arus Kas Bulan Berjalan (${context.currentMonthPrefix}):\n")
        sb.append("   - Total Pemasukan: ${Formatters.formatRupiah(cur.currentMonthTotalIncome)}\n")
        sb.append("   - Total Pengeluaran: ${Formatters.formatRupiah(cur.currentMonthTotalExpense)}\n")
        val netCashflow = cur.currentMonthTotalIncome - cur.currentMonthTotalExpense
        sb.append("   - Arus Kas Bersih (Surplus/Defisit): ${Formatters.formatRupiah(netCashflow)}\n")
        sb.append("   - Tingkat Tabungan (Savings Rate): ${(cur.savingsRate * 100).toInt()}%\n")
        sb.append("   - Rata-rata Pengeluaran Harian: ${Formatters.formatRupiah(cur.averageDailyExpense)}\n")
        if (cur.highestExpenseDay != null) {
            sb.append("   - Hari Pengeluaran Tertinggi: ${cur.highestExpenseDay} (${Formatters.formatRupiah(cur.highestExpenseDayAmount)})\n")
        }

        sb.append("\n3. Arus Kas Bulan Lalu (${context.prevMonthPrefix}):\n")
        sb.append("   - Total Pemasukan: ${Formatters.formatRupiah(cur.prevMonthTotalIncome)}\n")
        sb.append("   - Total Pengeluaran: ${Formatters.formatRupiah(cur.prevMonthTotalExpense)}\n")
        val prevNet = cur.prevMonthTotalIncome - cur.prevMonthTotalExpense
        sb.append("   - Arus Kas Bersih Bulan Lalu: ${Formatters.formatRupiah(prevNet)}\n")

        sb.append("\n4. Tren & Perbandingan (Bulan Ini vs Bulan Lalu):\n")
        val expDiffSign = if (cur.expenseDifference >= 0) "+" else ""
        sb.append("   - Perubahan Pengeluaran: $expDiffSign${Formatters.formatRupiah(cur.expenseDifference)} (${String.format(Locale.US, "%.1f", cur.expensePercentChange)}%)\n")
        val incDiffSign = if (cur.incomeDifference >= 0) "+" else ""
        sb.append("   - Perubahan Pemasukan: $incDiffSign${Formatters.formatRupiah(cur.incomeDifference)} (${String.format(Locale.US, "%.1f", cur.incomePercentChange)}%)\n")

        sb.append("\n5. Rincian Pengeluaran per Kategori (Bulan Ini vs Bulan Lalu):\n")
        if (context.topCategories.isEmpty()) {
            sb.append("   (Belum ada data pengeluaran)\n")
        } else {
            context.topCategories.forEach { cat ->
                val sign = if (cat.diff >= 0) "+" else ""
                sb.append("   - ${cat.categoryName}: Bulan ini ${Formatters.formatRupiah(cat.currentAmount)}, Bulan lalu ${Formatters.formatRupiah(cat.prevAmount)} (Selisih: $sign${Formatters.formatRupiah(cat.diff)}, ${String.format(Locale.US, "%.1f", cat.percentChange)}%)\n")
            }
        }

        sb.append("\n6. Status Anggaran Belanja (Budgets):\n")
        if (context.budgets.isEmpty()) {
            sb.append("   (Belum ada anggaran belanja yang disetel)\n")
        } else {
            context.budgets.forEach { b ->
                val statusStr = when (b.status) {
                    com.cashflow.app.data.model.BudgetStatus.EXCEEDED -> "⚠️ MELEBIHI BATAS"
                    com.cashflow.app.data.model.BudgetStatus.NEAR_LIMIT,
                    com.cashflow.app.data.model.BudgetStatus.WARNING -> "⚡ HAMPIR HABIS"
                    else -> "✅ AMAN"
                }
                val catName = b.category?.name ?: "Kategori"
                sb.append("   - $catName: Batas ${Formatters.formatRupiah(b.effectiveTargetAmount)}, Terpakai ${Formatters.formatRupiah(b.spentAmount)} (${(b.progressPercentage * 100).toInt()}%), Sisa ${Formatters.formatRupiah(b.remainingAmount)} [$statusStr]\n")
            }
        }

        sb.append("\n7. Catatan Utang & Cicilan:\n")
        val activeDebts = context.debts.filter { it.status != com.cashflow.app.data.model.DebtStatus.PAID.key }
        if (activeDebts.isEmpty()) {
            sb.append("   (Tidak ada utang atau cicilan aktif tercatat)\n")
        } else {
            val totalRemainingDebt = activeDebts.sumOf { it.remainingAmount }
            sb.append("   - Total Utang Belum Lunas: ${Formatters.formatRupiah(totalRemainingDebt)}\n")
            activeDebts.forEach { d ->
                sb.append("   - ${d.title} (${d.lenderName}): Sisa ${Formatters.formatRupiah(d.remainingAmount)} dari ${Formatters.formatRupiah(d.totalAmount)} (Jatuh tempo: ${d.dueDate})\n")
            }
        }

        sb.append("\n8. Target Finansial (Goals):\n")
        if (context.goals.isEmpty()) {
            sb.append("   (Belum ada target keuangan aktif)\n")
        } else {
            context.goals.forEach { g ->
                val pct = if (g.targetAmount > 0) ((g.currentAmount.toDouble() / g.targetAmount) * 100).toInt() else 0
                sb.append("   - ${g.name}: Terkumpul ${Formatters.formatRupiah(g.currentAmount)} dari target ${Formatters.formatRupiah(g.targetAmount)} ($pct%)\n")
            }
        }

        // Cantumkan riwayat percakapan sebelumnya (jika ada) sebelum pertanyaan terakhir
        val priorTurns = conversationHistory.dropLast(1).takeLast(8)
        if (priorTurns.isNotEmpty()) {
            sb.append("\n=== RIWAYAT PERCAKAPAN SEBELUMNYA ===\n")
            priorTurns.forEach { msg ->
                val speaker = if (msg.sender == ChatSender.USER) "Pengguna" else "Vina"
                sb.append("$speaker: ${msg.text}\n")
            }
        }

        sb.append("\n=== PANDUAN MENJAWAB ===\n")
        sb.append("1. Jawablah langsung, spesifik, dan cerdas menggunakan angka-angka nyata di atas. Jangan mengarang data.\n")
        sb.append("2. Bersikap ramah, cerdas, dan suportif layaknya seorang penasihat keuangan pribadi bernama Vina.\n")
        sb.append("3. Jika pengguna bertanya tentang tren arus kas bulan lalu, jelaskan perbandingan nominal pemasukan & pengeluaran antara bulan lalu dan bulan ini, hitung apakah naik/turun berapa persen, jelaskan kategori mana yang paling melonjak/berhemat, dan berikan evaluasi arus kas.\n")
        sb.append("4. Jika pengguna bertanya apakah bisa membeli sesuatu (misal gadget, kendaraan, liburan), analisis berdasarkan saldo likuid saat ini, sisa anggaran bulan ini, dan laju tabungan mereka.\n")
        sb.append("5. Gunakan bahasa Indonesia yang santun, luwes, dan bersahabat. Berikan format angka dalam format Rupiah (cth: Rp 1.500.000) dan teks tebal (**angka**) agar mudah dibaca.\n")
        sb.append("6. Jangan memberikan jawaban template yang kaku atau basa-basi berlebihan. Jawab tepat sasaran sesuai pertanyaan pengguna.")
        return sb.toString()
    }

    /**
     * Mengekstrak estimasi nominal angka dari teks pertanyaan (misal: "3 juta", "2.5 juta", "500rb", "15.000.000", "250k").
     */
    fun extractEstimatedAmount(query: String): Long? {
        val q = query.lowercase().replace("rp", " ")

        // 1. Deteksi pola juta/jt dengan desimal opsional (misal: 2.5 juta, 1,5 jt, 3 juta)
        val jtRegex = Regex("(\\d+(?:[.,]\\d+)?)\\s*(?:juta|jt)")
        val jtMatch = jtRegex.find(q)
        if (jtMatch != null) {
            val numStr = jtMatch.groupValues[1].replace(',', '.')
            val num = numStr.toDoubleOrNull() ?: 0.0
            if (num > 0) return (num * 1_000_000).toLong()
        }

        // 2. Deteksi pola ribu/rb/k dengan desimal opsional (misal: 2.5k, 500rb, 250 ribu)
        val rbRegex = Regex("(\\d+(?:[.,]\\d+)?)\\s*(?:ribu|rb|k)")
        val rbMatch = rbRegex.find(q)
        if (rbMatch != null) {
            val numStr = rbMatch.groupValues[1].replace(',', '.')
            val num = numStr.toDoubleOrNull() ?: 0.0
            if (num > 0) return (num * 1_000).toLong()
        }

        // 3. Deteksi angka bertitik ribuan (misal: 15.000.000) atau angka mentah
        val cleanQ = q.replace(".", "").replace(",", "")
        val numRegex = Regex("\\b(\\d{4,12})\\b")
        val numMatch = numRegex.find(cleanQ)
        if (numMatch != null) {
            val num = numMatch.groupValues[1].toLongOrNull()
            if (num != null && num > 0) return num
        }
        return null
    }

    /**
     * Mesin evaluasi pertanyaan analitik lokal Vina yang cerdas, luwes, dan multi-domain.
     * Mampu menjawab beragam variasi pertanyaan keuangan tanpa ketergantungan koneksi internet.
     */
    fun evaluateOfflineQuery(userQuery: String, context: FinancialChatContext): String {
        val q = normalizeLocalQuestion(userQuery, context.assets.map { it.name } + context.categories.map { it.name })
        localConversationReply(q, context)?.let { return it }
        if (q in listOf("insight", "insight keuangan", "ringkasan keuangan", "evaluasi keuangan", "beri insight", "analisis keuangan saya", "beri saya insight keuangan", "apa yang perlu diperhatikan", "ringkasan kondisi keuangan", "cek keuangan saya", "kasih insight dong")) {
            val today = runCatching { LocalDate.parse(context.currentDate) }.getOrElse { LocalDate.now() }
            val insights = generateLocalRuleInsights(context.currentMonthStats, context.budgets, context.debts, context.goals, today)
            return insights.take(3).joinToString("\n\n") { "**${it.title}**\n${it.message}" }
                .ifBlank { "Belum ada data yang cukup untuk insight. Mulai dengan mencatat pemasukan dan pengeluaran." }
        }
        answerLocalDataQuestion(q, context)?.let { return it }
        val cur = context.currentMonthStats
        val netCashflow = cur.currentMonthTotalIncome - cur.currentMonthTotalExpense
        val srPct = (cur.savingsRate * 100).toInt()
        val activeDebts = context.debts.filter { it.status != com.cashflow.app.data.model.DebtStatus.PAID.key && it.direction != "receivable" }
        val totalDebt = activeDebts.sumOf { it.remainingAmount }

        return when {
            // 1. Salam, Perkenalan, Identitas Vina & Apa yang Bisa Dibantu
            q.isBlank() || q.contains("assalamu") ||
            q.contains("siapa kamu") || q.contains("siapa anda") || q.contains("kamu siapa") ||
            q.contains("namamu") || q.contains("kenalan") ||
            q.contains("bisa apa") || q.contains("bantu apa") || q.contains("fungsi") -> {
                "Saya Vina, asisten keuangan Duit Aing. Tanyakan pengeluaran, pemasukan, anggaran, utang, atau target. Contoh: ‘Pengeluaran bulan lalu’, lalu ‘rincikan’."
            }

            // A named month must use that month's amounts, not the current ranking.
            q.contains("bulan lalu") && listOf("tren", "banding", "arus kas", "cashflow", "bulan ini", " vs ").none { q.contains(it) } &&
                listOf("pengeluaran", "belanja", "biaya", "boros", "pemasukan", "pendapatan", "penghasilan", "gaji", "income").any { q.contains(it) } -> {
                val expenseRequested = listOf("pengeluaran", "belanja", "biaya", "boros").any { q.contains(it) }
                val incomeRequested = listOf("pemasukan", "pendapatan", "penghasilan", "gaji", "income").any { q.contains(it) }
                buildString {
                    append("📅 **Ringkasan Bulan Lalu (${context.prevMonthPrefix})**\n\n")
                    if (expenseRequested) {
                        append("• **Total Pengeluaran:** **${Formatters.formatRupiah(cur.prevMonthTotalExpense)}**\n")
                        if (cur.prevMonthTotalExpense == 0L) {
                            append("Belum ada pengeluaran tercatat pada bulan lalu.\n")
                        } else {
                            context.topCategories.filter { it.prevAmount > 0 }.sortedByDescending { it.prevAmount }.take(5).forEachIndexed { index, cat ->
                                append("${index + 1}. **${cat.categoryName}**: **${Formatters.formatRupiah(cat.prevAmount)}**\n")
                            }
                        }
                    }
                    if (incomeRequested) {
                        append("• **Total Pemasukan:** **${Formatters.formatRupiah(cur.prevMonthTotalIncome)}**\n")
                        if (cur.prevMonthTotalIncome == 0L) append("Belum ada pemasukan tercatat pada bulan lalu.\n")
                    }
                }.trimEnd()
            }

            // 2. Evaluasi Kesehatan Finansial Menyeluruh (Financial Health Audit)
            q.contains("sehat") || q.contains("kesehatan") || q.contains("kondisi keuangan") ||
            q.contains("gimana keuangan") || q.contains("bagus gak") || q.contains("skor") ||
            q.contains("review") || q.contains("audit") -> {
                val exceeded = context.budgets.count { it.remainingAmount < 0 }
                buildString {
                    append("**Evaluasi Kesehatan Finansial**\n")
                    append("Arus kas bulan berjalan: **${Formatters.formatRupiah(netCashflow)}**.\n")
                    append("Beban Cicilan: sisa utang tercatat **${Formatters.formatRupiah(totalDebt)}**.\n")
                    append(if (context.budgets.isEmpty()) "Belum ada anggaran tercatat.\n" else "$exceeded dari ${context.budgets.size} anggaran melewati batas.\n")
                    append("Ringkasan ini mengikuti kelengkapan catatan dan belum mencakup kebutuhan atau kewajiban yang belum dicatat.")
                }
            }

            // 3. Simulasi & Kelayakan Pembelian ("Bisa beli HP?", "Mampu gak beli X?", "Boleh belanja?")
            q.contains("bisa beli") || q.contains("boleh beli") || q.contains("mampu beli") ||
            q.contains("cukup gak") || q.contains("mau beli") || q.contains("pengen beli") ||
            q.contains("bolehkah saya beli") || q.contains("beli hp") || q.contains("beli motor") ||
            q.contains("beli mobil") || q.contains("beli laptop") || q.contains("beli gadget") ||
            q.contains("beli baju") || q.contains("checkout") || q.contains("belanja lagi") ||
            q.contains("simulasi") -> {
                val detectedAmount = extractEstimatedAmount(userQuery)
                val safetyReserve = (cur.currentMonthTotalExpense * 2).coerceAtLeast(1_000_000L) // Cadangan darurat 2 bulan pengeluaran
                val safeDiscretionaryBudget = (context.totalLiquidBalance - safetyReserve).coerceAtLeast(0L)

                val sb = StringBuilder()
                sb.append("🛍️ **Simulasi Kelayakan Pembelian & Analisis Belanja oleh Vina**\n\n")

                if (detectedAmount != null) {
                    sb.append("Anda menanyakan pembelian senilai **${Formatters.formatRupiah(detectedAmount)}**.\n\n")
                    if (detectedAmount <= netCashflow && detectedAmount <= safeDiscretionaryBudget) {
                        sb.append("✅ **Vina Menilai: SANGAT AMAN DIBELI!**\n")
                        sb.append("Nominal ini dapat ditutup sepenuhnya dari surplus kas bulan ini (**${Formatters.formatRupiah(netCashflow)}**) tanpa menyentuh cadangan dana darurat aman Anda (**${Formatters.formatRupiah(context.totalLiquidBalance)}**).")
                    } else if (detectedAmount <= context.totalLiquidBalance && detectedAmount <= safeDiscretionaryBudget) {
                        sb.append("🟢 **Vina Menilai: BISA DIBELI.**\n")
                        sb.append("Saldo likuid bebas Anda (**${Formatters.formatRupiah(safeDiscretionaryBudget)}**) mencukupi dan dana cadangan darurat tetap aman dari total saldo **${Formatters.formatRupiah(context.totalLiquidBalance)}**.")
                    } else if (detectedAmount <= context.totalLiquidBalance) {
                        sb.append("⚡ **Vina Menilai: PERTIMBANGKAN ULANG.**\n")
                        sb.append("Total saldo Anda (**${Formatters.formatRupiah(context.totalLiquidBalance)}**) secara teknis cukup, namun pembelian ini akan memakan dana cadangan darurat aman Anda (minimal disarankan **${Formatters.formatRupiah(safetyReserve)}**). Jika bukan barang primer mendesak, Vina sarankan tunda hingga tabungan bertambah.")
                    } else {
                        val shortage = detectedAmount - context.totalLiquidBalance
                        sb.append("⚠️ **Vina Menilai: BELUM DISARANKAN.**\n")
                        sb.append("Total saldo likuid Anda saat ini (**${Formatters.formatRupiah(context.totalLiquidBalance)}**) masih kurang sebesar **${Formatters.formatRupiah(shortage)}**. Hindari memaksakan pembelian ini dengan utang konsumtif.")
                    }
                } else {
                    sb.append("Berdasarkan posisi keuangan Anda saat ini:\n")
                    sb.append("• **Batas Belanja Bebas Maksimal:** **${Formatters.formatRupiah(safeDiscretionaryBudget)}** (setelah cadangan aman)\n")
                    sb.append("• **Surplus Arus Kas Bulan Ini:** **${Formatters.formatRupiah(netCashflow)}**\n")
                    sb.append("• **Total Saldo Likuid:** **${Formatters.formatRupiah(context.totalLiquidBalance)}**\n\n")
                    sb.append("💡 **Panduan Vina:** Jika barang yang ingin dibeli berada di bawah **${Formatters.formatRupiah(safeDiscretionaryBudget)}**, Anda aman membelinya tanpa mengganggu kestabilan keuangan.")
                }
                sb.toString()
            }

            // 4. Tips Penghematan & Rekomendasi Solutif
            q.contains("tips") || q.contains("saran") || q.contains("rekomendasi") ||
            q.contains("hemat") || q.contains("cara hemat") || q.contains("kurangi") ||
            q.contains("solusi") || q.contains("nasehat") -> {
                val sb = StringBuilder()
                sb.append("💡 **Tips & Solusi Penghematan dari Vina**\n\n")

                val topCat = context.topCategories.maxByOrNull { it.currentAmount }?.takeIf { it.currentAmount > 0 }
                buildString {
                    append("**Tips & Solusi Penghematan**\n")
                    if (topCat != null) append("Tinjau ${topCat.categoryName}: tercatat **${Formatters.formatRupiah(topCat.currentAmount)}** bulan ini. Pilih transaksi yang bisa dikurangi tanpa mengganggu kebutuhan utama.\n")
                    else append("Catat pengeluaran terlebih dahulu agar pos yang bisa ditinjau terlihat.\n")
                    val tight = context.budgets.filter { it.remainingAmount >= 0 && it.effectiveTargetAmount > 0 }.minByOrNull { it.remainingAmount }
                    if (tight != null) append("Sisa anggaran ${tight.category?.name ?: "aktif"}: **${Formatters.formatRupiah(tight.remainingAmount)}**.\n")
                    append("Tetapkan batas sesuai kebutuhan Anda. Saya bisa merinci pengeluaran jika Anda menyebutkan kategori dan periode.")
                }
            }

            // 5. Analisis Pemasukan & Penghasilan (Income)
            q.contains("pemasukan") || q.contains("income") || q.contains("pendapatan") ||
            q.contains("gaji") || q.contains("penghasilan") || q.contains("cuan") -> {
                val sb = StringBuilder()
                sb.append("💵 **Analisis Pemasukan & Penghasilan dari Vina**\n\n")
                sb.append("• **Total Pemasukan Bulan Ini (${context.currentMonthPrefix}):** **${Formatters.formatRupiah(cur.currentMonthTotalIncome)}**\n")
                if (cur.prevMonthTotalIncome > 0) {
                    val incSign = if (cur.incomeDifference >= 0) "naik" else "turun"
                    sb.append("• **Bulan Lalu (${context.prevMonthPrefix}):** **${Formatters.formatRupiah(cur.prevMonthTotalIncome)}** ($incSign **${String.format(Locale.US, "%.1f", Math.abs(cur.incomePercentChange))}%**)\n")
                }
                sb.append("• **Total Pengeluaran Bulan Ini:** **${Formatters.formatRupiah(cur.currentMonthTotalExpense)}**\n")
                sb.append("• **Sisa Bersih (Surplus):** **${Formatters.formatRupiah(netCashflow)}**\n\n")
                if (netCashflow > 0) {
                    sb.append("✅ Pemasukan Anda berhasil menutup seluruh kebutuhan bulan ini dengan surplus **$srPct%**.")
                } else {
                    sb.append("⚠️ Pemasukan saat ini belum mencukupi total pengeluaran yang tercatat.")
                }
                sb.toString()
            }

            // 6. Rata-rata Harian & Burn Rate
            q.contains("harian") || q.contains("per hari") || q.contains("rata-rata") ||
            q.contains("hari apa") || q.contains("hari paling") || q.contains("burn rate") -> {
                val sb = StringBuilder()
                sb.append("📅 **Rata-rata Pengeluaran Harian & Burn Rate dari Vina**\n\n")
                sb.append("• **Rata-rata Belanja Harian:** **${Formatters.formatRupiah(cur.averageDailyExpense)}/hari**\n")
                val estimatedMonthly = cur.averageDailyExpense * 30
                sb.append("• **Proyeksi Belanja Akhir Bulan:** ± **${Formatters.formatRupiah(estimatedMonthly)}**\n")
                if (cur.highestExpenseDay != null) {
                    sb.append("• **Hari Pengeluaran Tertinggi:** ${cur.highestExpenseDay} sebesar **${Formatters.formatRupiah(cur.highestExpenseDayAmount)}**\n\n")
                }
                sb.append("💡 Menjaga rata-rata belanja harian tetap konsisten adalah kunci utama agar target anggaran bulanan Anda tidak jebol.")
                sb.toString()
            }

            // 7. Tren Arus Kas / Perbandingan Bulan Lalu
            q.contains("tren") || q.contains("bulan lalu") || q.contains("arus kas") ||
            q.contains("banding") || q.contains("cashflow") -> {
                val sb = StringBuilder()
                sb.append("📊 **Analisis Tren Arus Kas dari Vina (${context.prevMonthPrefix} vs ${context.currentMonthPrefix})**\n\n")

                if (cur.prevMonthTotalExpense > 0 || cur.prevMonthTotalIncome > 0) {
                    val expDiffSign = if (cur.expenseDifference >= 0) "meningkat" else "berhemat"
                    val incDiffSign = if (cur.incomeDifference >= 0) "naik" else "turun"

                    sb.append("• **Pemasukan:** Dari **${Formatters.formatRupiah(cur.prevMonthTotalIncome)}** di bulan lalu menjadi **${Formatters.formatRupiah(cur.currentMonthTotalIncome)}** di bulan ini ($incDiffSign **${String.format(Locale.US, "%.1f", Math.abs(cur.incomePercentChange))}%**).\n")
                    sb.append("• **Pengeluaran:** Dari **${Formatters.formatRupiah(cur.prevMonthTotalExpense)}** di bulan lalu menjadi **${Formatters.formatRupiah(cur.currentMonthTotalExpense)}** di bulan ini ($expDiffSign **${Formatters.formatRupiah(Math.abs(cur.expenseDifference))}** atau **${String.format(Locale.US, "%.1f", Math.abs(cur.expensePercentChange))}%**).\n")
                    sb.append("• **Arus Kas Bersih:** Bulan ini Anda membukukan surplus sebesar **${Formatters.formatRupiah(netCashflow)}** dengan tingkat tabungan (savings rate) **$srPct%**.\n\n")

                    val surge = context.topCategories.filter { it.prevAmount > 0 && it.diff > 0 }.maxByOrNull { it.percentChange }
                    if (surge != null) {
                        sb.append("💡 **Sorotan Kategori:** Lonjakan pengeluaran terbesar terjadi pada **${surge.categoryName}** yang naik **${surge.percentChange.toInt()}%** (bertambah **${Formatters.formatRupiah(surge.diff)}**).\n\n")
                    }

                    if (srPct >= 20) {
                        sb.append("✅ **Kesimpulan Vina:** Arus kas Anda berada dalam kondisi sehat di atas target rekomendasi 20%. Pertahankan disiplin alokasi tabungan ini!")
                    } else if (netCashflow >= 0) {
                        sb.append("⚡ **Kesimpulan Vina:** Arus kas Anda masih surplus positif, namun savings rate ($srPct%) masih di bawah rasio aman 20%. Pertimbangkan menahan pos belanja sekunder.")
                    } else {
                        sb.append("⚠️ **Peringatan Defisit dari Vina:** Pengeluaran bulan ini melampaui pemasukan Anda. Disarankan segera membatasi pos pengeluaran fleksibel hingga awal bulan depan.")
                    }
                } else {
                    sb.append("Bulan lalu belum memiliki riwayat transaksi tercatat yang cukup untuk perbandingan langsung. Namun di bulan ini (${context.currentMonthPrefix}), total pengeluaran Anda adalah **${Formatters.formatRupiah(cur.currentMonthTotalExpense)}** dengan pemasukan **${Formatters.formatRupiah(cur.currentMonthTotalIncome)}** (Surplus: **${Formatters.formatRupiah(netCashflow)}**).")
                }
                sb.toString()
            }

            // 8. Status Anggaran Belanja (Budgets - Diprioritaskan sebelum pencarian umum pos belanja)
            q.contains("anggaran") || q.contains("budget") || q.contains("limit") ||
            q.contains("jebol") || q.contains("batas") || q.contains("kuota") -> {
                val sb = StringBuilder()
                sb.append("🎯 **Status Anggaran Belanja Bulan Ini**\n\n")
                if (context.budgets.isNotEmpty()) {
                    context.budgets.forEach { b ->
                        val pct = (b.progressPercentage * 100).toInt()
                        val icon = when (b.status) {
                            com.cashflow.app.data.model.BudgetStatus.EXCEEDED -> "⚠️"
                            com.cashflow.app.data.model.BudgetStatus.NEAR_LIMIT,
                            com.cashflow.app.data.model.BudgetStatus.WARNING -> "⚡"
                            else -> "✅"
                        }
                        val catName = b.category?.name ?: "Kategori"
                        sb.append("$icon **$catName**: Terpakai **${Formatters.formatRupiah(b.spentAmount)}** dari limit **${Formatters.formatRupiah(b.effectiveTargetAmount)}** ($pct%). Sisa: **${Formatters.formatRupiah(b.remainingAmount)}**\n")
                    }
                } else {
                    sb.append("Anda belum mengatur batas anggaran di menu Anggaran. Anda dapat membuatnya untuk membatasi pos belanja tertentu.")
                }
                sb.toString()
            }

            // 9. Kategori Pengeluaran Terbesar / Pos Boros
            q.contains("terbesar") || q.contains("boros") || q.contains("kategori") ||
            q.contains("banyak") || q.contains("habis") || q.contains("belanja") ||
            q.contains("biaya") || q.contains("pengeluaran") -> {
                val sb = StringBuilder()
                sb.append("📈 **Rincian Pengeluaran Terbesar Bulan Ini**\n\n")
                sb.append("• **Total Pengeluaran:** **${Formatters.formatRupiah(cur.currentMonthTotalExpense)}**\n\n")
                if (context.topCategories.isNotEmpty()) {
                    context.topCategories.take(5).forEachIndexed { idx, cat ->
                        val pctOfTotal = if (cur.currentMonthTotalExpense > 0) ((cat.currentAmount.toDouble() / cur.currentMonthTotalExpense) * 100).toInt() else 0
                        sb.append("${idx + 1}. **${cat.categoryName}**: **${Formatters.formatRupiah(cat.currentAmount)}** ($pctOfTotal% dari total belanja)\n")
                    }
                    sb.append("\n💡 Rata-rata belanja harian Anda bulan ini adalah **${Formatters.formatRupiah(cur.averageDailyExpense)}/hari**.")
                } else {
                    sb.append("Belum ada transaksi pengeluaran berkategori pada periode bulan ini.")
                }
                sb.toString()
            }

            // 10. Saldo & Rincian Aset
            q.contains("saldo") || q.contains("aset") || q.contains("uang") ||
            q.contains("rekening") || q.contains("kekayaan") || q.contains("dompet") ||
            q.contains("kas") || q.contains("atm") || q.contains("bank") || q.contains("e-wallet") -> {
                val sb = StringBuilder()
                sb.append("💰 **Kondisi Saldo & Aset Anda**\n\n")
                sb.append("Total seluruh saldo likuid Anda saat ini adalah **${Formatters.formatRupiah(context.totalLiquidBalance)}**.\n\n")
                sb.append("**Rincian per Akun/Rekening:**\n")
                context.assets.forEach { a ->
                    sb.append("• **${a.name}** (${a.type}): **${Formatters.formatRupiah(a.currentBalance)}**\n")
                }
                sb.toString()
            }

            // 11. Catatan Utang & Cicilan
            q.contains("utang") || q.contains("hutang") || q.contains("piutang") ||
            q.contains("cicilan") || q.contains("kredit") || q.contains("pinjol") -> {
                val sb = StringBuilder()
                sb.append("📋 **Ringkasan Catatan Utang & Cicilan**\n\n")
                if (activeDebts.isNotEmpty()) {
                    sb.append("• **Total Utang Belum Lunas:** **${Formatters.formatRupiah(totalDebt)}** (${activeDebts.size} catatan aktif).\n\n")
                    activeDebts.forEach { d ->
                        sb.append("• **${d.title}** (${d.lenderName}): Sisa **${Formatters.formatRupiah(d.remainingAmount)}** (Jatuh tempo: ${d.dueDate})\n")
                    }
                } else {
                    sb.append("Selamat! Tidak ada catatan utang yang belum lunas dalam pencatatan Anda.")
                }
                sb.toString()
            }

            // 12. Target Finansial (Goals)
            q.contains("target") || q.contains("goal") || q.contains("impian") ||
            q.contains("tabungan") || q.contains("menabung") || q.contains("rencana") -> {
                val sb = StringBuilder()
                sb.append("🎯 **Target Finansial & Tabungan Anda**\n\n")
                if (context.goals.isNotEmpty()) {
                    context.goals.forEach { g ->
                        val pct = if (g.targetAmount > 0) ((g.currentAmount.toDouble() / g.targetAmount) * 100).toInt() else 0
                        sb.append("• **${g.name}**: Terkumpul **${Formatters.formatRupiah(g.currentAmount)}** dari target **${Formatters.formatRupiah(g.targetAmount)}** ($pct%). Deadline: ${g.deadline}\n")
                    }
                    if (netCashflow > 0) {
                        sb.append("\n💡 Dengan surplus bulanan saat ini (**${Formatters.formatRupiah(netCashflow)}**), target Anda berada di jalur yang sangat baik.")
                    }
                } else {
                    sb.append("Belum ada target keuangan aktif yang dicatat. Anda dapat membuat target tabungan untuk memotivasi pengelolaan dana.")
                }
                sb.toString()
            }

            // 13. Panduan Penggunaan Fitur Duit Aing
            q.contains("cara catat") || q.contains("cara buat") || q.contains("cara tambah") ||
            q.contains("bikin anggaran") || q.contains("tambah aset") || q.contains("ekspor") ||
            q.contains("cadang") || q.contains("panduan") || q.contains("bantuan") -> {
                val sb = StringBuilder()
                sb.append("📖 **Panduan Cepat Aplikasi Duit Aing dari Vina**\n\n")
                sb.append("• **Catat Transaksi:** Tekan tombol **(+)** hijau di tengah navigation bar bawah.\n")
                sb.append("• **Atur Anggaran:** Buka menu Beranda ➔ scroll ke kartu Anggaran ➔ tekan **KELOLA**.\n")
                sb.append("• **Kelola Aset & Rekening:** Masuk ke tab **Aset** di pojok kanan bawah untuk menambah dompet atau menyesuaikan saldo.\n")
                sb.append("• **Cadangkan Data:** Buka menu **Pengaturan (⚙️)** di pojok kanan atas Beranda ➔ pilih **Cadangkan Data**.\n\n")
                sb.append("Ada fitur tertentu yang ingin Anda pelajari lebih dalam?")
                sb.toString()
            }

            // 14. Executive Dashboard Summary (Default Fallback Cerdas)
            else -> {
                "Saya belum memahami pertanyaan itu secara spesifik. Saya bisa membantu membaca transaksi, saldo, anggaran, utang, piutang, dan target tabungan.\n\n" +
                    "Coba: **Pengeluaran kemarin berapa?**, **Belanja makan bulan ini**, **Transaksi terbesar minggu ini**, atau **Saldo BCA berapa?** Gunakan nama rekening dan kategori yang tercatat di aplikasi Anda."
            }
        }
    }

    /**
     * Menjalankan pemanggilan REST API ke model Gemini (mendukung Gemini 3.8 Flash).
     * Memfilter bagian internal thinking dan mengekstrak pesan final secara aman.
     */
    suspend fun executeGeminiCall(
        model: String,
        apiKey: String,
        systemInstruction: String,
        userQuery: String
    ): Result<String> {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return Result.failure(IllegalArgumentException("API Key kosong"))
        }

        return try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$cleanKey"
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connectTimeout = 5000
                readTimeout = 8000
                doOutput = true
            }

            val requestBody = JSONObject().apply {
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
                })

                val contents = JSONArray().apply {
                    val userContent = JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().put(JSONObject().put("text", userQuery)))
                    }
                    put(userContent)
                }
                put("contents", contents)

                val genConfig = JSONObject().apply {
                    put("temperature", 1.0)
                    put("maxOutputTokens", 2048)
                    val thinkingConfig = JSONObject().apply {
                        put("thinkingLevel", "low")
                        put("includeThoughts", false)
                    }
                    put("thinkingConfig", thinkingConfig)
                }
                put("generationConfig", genConfig)
            }

            ResilientNetworkClient.withConnectionTimeout(conn, timeoutMs = 12_000L) {
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(requestBody.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val responseText = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                    val json = JSONObject(responseText)
                    val candidates = json.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val candidate = candidates.getJSONObject(0)
                        val content = candidate.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val textBuilder = StringBuilder()
                            for (i in 0 until parts.length()) {
                                val part = parts.getJSONObject(i)
                                // Abaikan bagian internal thinking Gemini 3
                                if (!part.optBoolean("thought", false)) {
                                    val t = part.optString("text", "")
                                    if (t.isNotBlank()) textBuilder.append(t)
                                }
                            }
                            val textResult = textBuilder.toString().trim()
                            if (textResult.isNotEmpty()) {
                                Result.success(textResult)
                            } else {
                                val fallbackText = parts.getJSONObject(parts.length() - 1).optString("text", "").trim()
                                if (fallbackText.isNotEmpty()) Result.success(fallbackText)
                                else Result.failure(java.io.IOException("Respons kosong dari Gemini"))
                            }
                        } else {
                            Result.failure(java.io.IOException("Kandidat respons tidak memiliki bagian teks"))
                        }
                    } else {
                        Result.failure(java.io.IOException("Tidak ada kandidat jawaban dari Gemini"))
                    }
                } else {
                    val errorStream = conn.errorStream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() } } ?: ""
                    val errorMsg = try {
                        val errJson = JSONObject(errorStream)
                        errJson.optJSONObject("error")?.optString("message") ?: errorStream
                    } catch (_: Exception) {
                        errorStream
                    }
                    Result.failure(java.io.IOException("HTTP $responseCode: $errorMsg"))
                }
            }
        } catch (e: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            Result.failure(java.net.SocketTimeoutException("AI online melewati batas waktu 12 detik"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mengirim pesan chat percakapan ke Gemini 3.8 Flash dengan fallback cerdas ke mesin lokal bila offline/gagal.
     */
    suspend fun sendChatMessage(
        apiKey: String?,
        conversationHistory: List<ChatMessage>,
        context: FinancialChatContext
    ): Result<String> {
        val lastUserMessage = conversationHistory.lastOrNull { it.sender == ChatSender.USER }?.text ?: ""
        val cleanKey = apiKey?.trim() ?: ""

        if (cleanKey.isBlank()) {
            return withContext(Dispatchers.Default) {
                Result.success(evaluateOfflineQuery(localQuestionWithHistory(conversationHistory, context.assets.map { it.name } + context.categories.map { it.name }), context))
            }
        }

        return withContext(Dispatchers.IO) {
            val systemInstruction = buildFinancialSystemInstruction(context, conversationHistory)

            val online = withGeminiModelFallback { model ->
                executeGeminiCall(model, cleanKey, systemInstruction, lastUserMessage)
            }
            if (online.isSuccess) return@withContext online
            val primaryError = online.exceptionOrNull()

            // 3. Fallback cerdas ke Mesin Analisis Finansial Lokal jika terjadi kendala pada API key / kuota
            val localAnswer = evaluateOfflineQuery(localQuestionWithHistory(conversationHistory, context.assets.map { it.name } + context.categories.map { it.name }), context)
            val errorNote = primaryError?.message?.let {
                val cleanErrMsg = if (it.contains("API_KEY_INVALID", ignoreCase = true) || it.contains("400")) {
                    "API Key tidak valid atau kedaluwarsa"
                } else if (it.contains("RESOURCE_EXHAUSTED", ignoreCase = true) || it.contains("429")) {
                    "Batas kuota gratis Gemini tercapai"
                } else {
                    it.take(80)
                }
                "\n\n*(Catatan: Mode dialihkan otomatis ke Analisis Cerdas Lokal karena kendala AI: $cleanErrMsg. Periksa API Key di Pengaturan).* "
            } ?: ""
            Result.success(localAnswer + errorNote)
        }
    }
}
