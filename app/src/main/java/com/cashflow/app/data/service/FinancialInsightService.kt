package com.cashflow.app.data.service

import com.cashflow.app.data.model.Category
import com.cashflow.app.data.model.Transaction
import com.cashflow.app.data.model.TransactionType
import com.cashflow.app.ui.components.Formatters
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

    /**
     * Memanggil Gemini API untuk menghasilkan insight naratif singkat berdasarkan agregat angka.
     */
    suspend fun fetchGeminiInsight(
        apiKey: String,
        stats: MonthComparisonResult
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("API Key Gemini belum disetel."))
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

        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
            }

            val requestBody = JSONObject().apply {
                val contents = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        }
                        put("parts", parts)
                    }
                    put(contentObj)
                }
                put("contents", contents)
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val json = JSONObject(responseText)
                val text = json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                Result.success(text.trim())
            } else {
                val errorStream = conn.errorStream?.let { BufferedReader(InputStreamReader(it)).use { r -> r.readText() } }
                Result.failure(Exception("HTTP $responseCode: $errorStream"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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
}
