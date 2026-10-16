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

    /**
     * Menghasilkan 1-3 insight berbasis aturan lokal tanpa koneksi jaringan.
     * Hanya dimunculkan jika data mencukupi.
     */
    fun generateLocalRuleInsights(
        stats: MonthComparisonResult
    ): List<RuleBasedInsight> {
        val insights = mutableListOf<RuleBasedInsight>()

        // 1. Kenaikan pengeluaran signifikan vs bulan lalu
        if (stats.prevMonthTotalExpense > 0 && stats.currentMonthTotalExpense > 0) {
            if (stats.expensePercentChange >= 15.0) {
                insights.add(
                    RuleBasedInsight(
                        title = "Pengeluaran Meningkat",
                        message = "Total pengeluaran naik ${stats.expensePercentChange.toInt()}% dibanding bulan lalu (${Formatters.formatCompact(stats.expenseDifference)} lebih banyak).",
                        type = "warning"
                    )
                )
            } else if (stats.expensePercentChange <= -10.0) {
                insights.add(
                    RuleBasedInsight(
                        title = "Pengeluaran Terkendali",
                        message = "Bagus! Pengeluaran Anda hemat ${Math.abs(stats.expensePercentChange).toInt()}% dibanding periode bulan lalu.",
                        type = "positive"
                    )
                )
            }
        }

        // 2. Kategori dengan lonjakan tertinggi
        val surgeCategory = stats.categoryDeltas.filter { it.prevAmount > 0 && it.diff > 100_000L }
            .maxByOrNull { it.percentChange }

        if (surgeCategory != null && surgeCategory.percentChange >= 20.0) {
            insights.add(
                RuleBasedInsight(
                    title = "Lonjakan ${surgeCategory.categoryName}",
                    message = "Kategori ${surgeCategory.categoryName} naik ${surgeCategory.percentChange.toInt()}% (bertambah ${Formatters.formatCompact(surgeCategory.diff)}).",
                    type = "warning"
                )
            )
        }

        // 3. Savings Rate
        if (stats.currentMonthTotalIncome > 0) {
            val ratePercent = (stats.savingsRate * 100).toInt()
            if (ratePercent >= 30) {
                insights.add(
                    RuleBasedInsight(
                        title = "Tingkat Tabungan Prima",
                        message = "Savings rate Anda berada di $ratePercent%, melampaui target rekomendasi 20%.",
                        type = "positive"
                    )
                )
            } else if (ratePercent < 10 && stats.currentMonthTotalExpense > 0) {
                insights.add(
                    RuleBasedInsight(
                        title = "Arus Kas Ketat",
                        message = "Savings rate bulan ini $ratePercent%. Pertimbangkan menahan pos pengeluaran sekunder.",
                        type = "info"
                    )
                )
            }
        }

        return insights
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
        val sb = StringBuilder()
        val srPct = (stats.savingsRate * 100).toInt()
        val expDiff = stats.expenseDifference
        val expPct = stats.expensePercentChange.toInt()

        sb.append("📊 Evaluasi Arus Kas Bulan Ini\n")
        if (srPct >= 20) {
            sb.append("Kondisi keuangan Anda bulan ini sangat sehat dengan tingkat tabungan (savings rate) mencapai $srPct%, berada di atas batas rekomendasi 20%.\n\n")
        } else if (srPct > 0) {
            sb.append("Arus kas Anda masih positif dengan tingkat tabungan $srPct%, namun berada di bawah rekomendasi ideal 20%. Pertahankan disiplin anggaran.\n\n")
        } else {
            sb.append("Perhatian: Arus kas bulan ini mengalami defisit (pengeluaran melampaui pemasukan). Disarankan untuk meninjau kembali pos belanja non-primer.\n\n")
        }

        if (stats.prevMonthTotalExpense > 0) {
            if (expPct > 0) {
                sb.append("Total pengeluaran meningkat $expPct% (+${Formatters.formatCompact(expDiff)}) dibanding bulan lalu.\n\n")
            } else {
                sb.append("Total pengeluaran berhasil ditekan hemat ${Math.abs(expPct)}% dibanding periode bulan lalu.\n\n")
            }
        }

        sb.append("💡 Rekomendasi Penghematan\n")
        val topCat = stats.categoryDeltas.firstOrNull()
        if (topCat != null) {
            sb.append("Pos pengeluaran terbesar saat ini adalah ${topCat.categoryName} sebesar ${Formatters.formatRupiah(topCat.currentAmount)}.")
            if (topCat.percentChange > 15) {
                sb.append(" Kategori ini melonjak ${topCat.percentChange.toInt()}% dibanding bulan lalu. Fokuskan pengendalian pada pos ini untuk hasil penghematan paling cepat.")
            } else {
                sb.append(" Jaga stabilitas pos pengeluaran ini agar rasio arus kas tetap optimal.")
            }
        } else {
            sb.append("Terus catat transaksi harian Anda secara konsisten agar AI dapat memetakan pola keuangan secara lebih akurat.")
        }

        return sb.toString()
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

        val primaryRes = executeGeminiCall(
            model = "gemini-3.8-flash",
            apiKey = cleanKey,
            systemInstruction = prompt,
            userQuery = "Berikan analisis naratif evaluasi keuangan bulanan saya."
        )
        if (primaryRes.isSuccess) {
            return@withContext primaryRes
        }

        val fallbackRes = executeGeminiCall(
            model = "gemini-3-flash",
            apiKey = cleanKey,
            systemInstruction = prompt,
            userQuery = "Berikan analisis naratif evaluasi keuangan bulanan saya."
        )
        if (fallbackRes.isSuccess) {
            return@withContext fallbackRes
        }

        // Fallback ke analisis lokal jika koneksi/kuota luar terkendala
        Result.success(generateLocalNarrativeInsight(stats))
    }

    fun calculateMonthComparison(
        transactions: List<Transaction>,
        currentMonthPrefix: String,
        prevMonthPrefix: String
    ): MonthComparisonResult {
        val curDate = try {
            LocalDate.parse("$currentMonthPrefix-01")
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
        return generateLocalRuleInsights(stats)
    }

    fun buildGeminiAggregatePayload(
        transactions: List<Transaction>,
        categories: List<Category>,
        currentMonthPrefix: String,
        previousMonthPrefix: String
    ): JSONObject {
        val curDate = try {
            LocalDate.parse("$currentMonthPrefix-01")
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
            LocalDate.parse("$currentMonthPrefix-01")
        } catch (_: Exception) {
            LocalDate.now()
        }
        val stats = computeMonthComparison(transactions, categories, curDate)
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
        sb.append("Anda bertindak layaknya seorang penasihat keuangan pribadi yang cerdas, jujur, solutif, empati, dan bersahabat. ")
        sb.append("Anda berbicara langsung kepada $userLabel dan selalu menyebut diri Anda sebagai 'Vina'.\n\n")

        sb.append("=== DATA KEUANGAN NYATA PENGGUNA (SNAPSHOT HARI INI: ${context.currentDate}) ===\n")
        sb.append("1. Total Saldo Likuid / Kekayaan Bersih: ${Formatters.formatRupiah(context.totalLiquidBalance)}\n")
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
                val sb = StringBuilder()
                val greeting = if (context.userName.isNotBlank()) "Halo ${context.userName.trim()}!" else "Halo!"
                sb.append("👋 **$greeting Saya Vina, Asisten Finansial Pribadi Anda.**\n\n")
                sb.append("Vina hadir untuk membantu Anda membaca data keuangan secara cerdas, jujur, dan mudah dipahami. Kondisi ringkas Anda saat ini:\n")
                sb.append("• **Total Saldo Likuid:** **${Formatters.formatRupiah(context.totalLiquidBalance)}**\n")
                val statusCash = if (netCashflow >= 0) "Surplus **${Formatters.formatRupiah(netCashflow)}**" else "Defisit **${Formatters.formatRupiah(Math.abs(netCashflow))}**"
                sb.append("• **Arus Kas Bulan Ini:** $statusCash (Savings Rate: **$srPct%**)\n\n")
                sb.append("💬 **Anda bisa menanyakan berbagai hal kepada Vina, contohnya:**\n")
                sb.append("1. *\"Bagaimana tren arus kas saya bulan lalu?\"*\n")
                sb.append("2. *\"Kategori apa pengeluaran terbesar saya?\"*\n")
                sb.append("3. *\"Berapa rata-rata belanja harian saya?\"*\n")
                sb.append("4. *\"Apakah kondisi keuangan saya sehat?\"*\n")
                sb.append("5. *\"Apakah anggaran belanja masih aman?\"*\n")
                sb.append("6. *\"Bisa beli barang seharga Rp 2.000.000?\"*\n")
                sb.append("7. *\"Berapa sisa utang dan cicilan saya?\"*\n")
                sb.append("8. *\"Berikan tips hemat untuk bulan ini.\"*")
                sb.toString()
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
                var healthScore = 0
                val notes = mutableListOf<String>()

                // Pilar 1: Arus Kas Bersih (30 poin)
                if (netCashflow > 0) {
                    healthScore += 30
                    notes.add("✅ Arus kas positif (+${Formatters.formatRupiah(netCashflow)})")
                } else if (netCashflow == 0L) {
                    healthScore += 15
                    notes.add("⚡ Arus kas impas (Pemasukan = Pengeluaran)")
                } else {
                    notes.add("⚠️ Arus kas defisit (${Formatters.formatRupiah(netCashflow)})")
                }

                // Pilar 2: Savings Rate (30 poin)
                if (srPct >= 20) {
                    healthScore += 30
                    notes.add("✅ Rasio Tabungan prima ($srPct% >= target 20%)")
                } else if (srPct >= 10) {
                    healthScore += 18
                    notes.add("⚡ Rasio Tabungan cukup ($srPct%), targetkan naik ke 20%")
                } else if (srPct > 0) {
                    healthScore += 8
                    notes.add("⚠️ Rasio Tabungan tipis ($srPct%)")
                } else {
                    notes.add("⚠️ Belum ada tabungan yang berhasil disisihkan")
                }

                // Pilar 3: Beban Utang (20 poin)
                if (activeDebts.isEmpty()) {
                    healthScore += 20
                    notes.add("✅ Bebas Beban Cicilan konsumtif / cicilan aktif")
                } else {
                    val debtRatio = if (context.totalLiquidBalance > 0) (totalDebt.toDouble() / context.totalLiquidBalance) else 1.0
                    if (debtRatio <= 0.3) {
                        healthScore += 15
                        notes.add("✅ Beban Cicilan aman (<30% dari saldo likuid)")
                    } else if (debtRatio <= 0.7) {
                        healthScore += 10
                        notes.add("⚡ Beban Cicilan aktif ${Formatters.formatRupiah(totalDebt)} perlu dicicil teratur")
                    } else {
                        notes.add("⚠️ Beban Cicilan (${Formatters.formatRupiah(totalDebt)}) mendekati/melebihi aset likuid")
                    }
                }

                // Pilar 4: Disiplin Anggaran (20 poin)
                val exceededBudgets = context.budgets.filter { it.status == com.cashflow.app.data.model.BudgetStatus.EXCEEDED }
                if (context.budgets.isEmpty()) {
                    healthScore += 12
                    notes.add("ℹ️ Belum mengatur limit anggaran bulanan")
                } else if (exceededBudgets.isEmpty()) {
                    healthScore += 20
                    notes.add("✅ Seluruh pos anggaran masih dalam batas aman")
                } else {
                    notes.add("⚠️ Terdapat ${exceededBudgets.size} pos anggaran yang overbudget")
                }

                val verdictTitle = when {
                    healthScore >= 80 -> "⭐ **Kondisi Finansial: SANGAT PRIMA ($healthScore/100)**"
                    healthScore >= 60 -> "🟢 **Kondisi Finansial: SEHAT & TERKENDALI ($healthScore/100)**"
                    healthScore >= 40 -> "⚡ **Kondisi Finansial: CUKUP BAIK - PERLU WASPADA ($healthScore/100)**"
                    else -> "⚠️ **Kondisi Finansial: PERLU PENGETATAN SEGERA ($healthScore/100)**"
                }

                val sb = StringBuilder()
                sb.append("🩺 **Evaluasi Kesehatan Finansial (Diagnosis Vina)**\n\n")
                sb.append("$verdictTitle\n\n")
                sb.append("**Catatan Evaluasi 4 Pilar:**\n")
                notes.forEach { n -> sb.append("• $n\n") }
                sb.append("\n💡 **Rekomendasi Vina:** ")
                if (healthScore >= 70) {
                    sb.append("Pertahankan kebiasaan mencatat dan sisihkan surplus ke target tabungan/dana darurat.")
                } else {
                    sb.append("Fokuskan pengetatan pada pos belanja fleksibel agar arus kas kembali menghasilkan surplus.")
                }
                sb.toString()
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

                val topCat = context.topCategories.firstOrNull()
                val surgeCat = context.topCategories.filter { it.prevAmount > 0 && it.diff > 50_000L }.maxByOrNull { it.percentChange }

                sb.append("1. **Kendalikan Pos Belanja Terbesar:**\n")
                if (topCat != null) {
                    val pct = if (cur.currentMonthTotalExpense > 0) ((topCat.currentAmount.toDouble() / cur.currentMonthTotalExpense) * 100).toInt() else 0
                    sb.append("   Pengeluaran terbesar Anda tertuju pada **${topCat.categoryName}** (${Formatters.formatRupiah(topCat.currentAmount)} atau $pct% dari total belanja). Kurangi frekuensi atau cari substitusi lebih hemat di pos ini.\n\n")
                } else {
                    sb.append("   Catat transaksi harian secara konsisten agar pos pengeluaran terbesar teridentifikasi.\n\n")
                }

                sb.append("2. **Waspadai Kenaikan Cepat:**\n")
                if (surgeCat != null) {
                    sb.append("   Kategori **${surgeCat.categoryName}** melonjak **${surgeCat.percentChange.toInt()}%** (+${Formatters.formatRupiah(surgeCat.diff)}) dibanding bulan lalu. Tahan belanja tambahan di pos ini hingga awal bulan depan.\n\n")
                } else {
                    sb.append("   Laju pengeluaran antar kategori relatif stabil tanpa lonjakan tajam.\n\n")
                }

                sb.append("3. **Terapkan Aturan 50-30-20 (50/30/20):**\n")
                sb.append("   Alokasikan 50% untuk kebutuhan pokok, 30% untuk gaya hidup, dan minimal 20% langsung disisihkan ke tabungan di awal bulan, bukan dari sisa akhir bulan.")
                sb.toString()
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
    fun executeGeminiCall(
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
                connectTimeout = 15000
                readTimeout = 20000
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

            // 1. Coba Gemini 3.8 Flash (Model utama terkini)
            val primaryResult = executeGeminiCall("gemini-3.8-flash", cleanKey, systemInstruction, lastUserMessage)
            if (primaryResult.isSuccess) {
                return@withContext primaryResult
            }

            val primaryError = primaryResult.exceptionOrNull()

            // 2. Coba Gemini 3 Flash / Fallback model
            val fallbackResult = executeGeminiCall("gemini-3-flash", cleanKey, systemInstruction, lastUserMessage)
            if (fallbackResult.isSuccess) {
                return@withContext fallbackResult
            }

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
