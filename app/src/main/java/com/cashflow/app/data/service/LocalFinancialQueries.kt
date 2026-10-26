package com.cashflow.app.data.service

import com.cashflow.app.data.model.*
import com.cashflow.app.ui.components.Formatters
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale

private fun String.has(vararg terms: String) = terms.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(this) }
private val monthNames = listOf("januari", "februari", "maret", "april", "mei", "juni", "juli", "agustus", "september", "oktober", "november", "desember")
private val abbreviations = listOf("jan", "feb", "mar", "apr", "mei", "jun", "jul", "agu", "sep", "okt", "nov", "des")
private val conversationDate = "(?:\\d{4}-\\d{2}-\\d{2}|\\d{1,2}/\\d{1,2}/\\d{4})"
private val conversationPeriod = "(?:" + conversationDate + "(?:\\s+(?:sampai|hingga|s/d|sd|-)\\s+" + conversationDate + ")?" +
    "|hari ini|kemarin|sehari yang lalu|\\d+ (?:minggu|bulan|tahun) lalu|minggu (?:ini|lalu)|bulan (?:ini|lalu)|tahun (?:ini|lalu|\\d{4})" +
    "|\\d+ hari terakhir|seminggu terakhir|sebulan terakhir|semua(?: waktu)?|sepanjang waktu|(?:" +
    (monthNames + abbreviations).distinct().joinToString("|") + ")(?: \\d{4})?)"
private val conversationPeriods = Regex("\\b$conversationPeriod\\b")
private val periodReply = Regex("^(?:(?:kalau|kalo|bagaimana|gimana|yang|untuk|terus|lalu|dan|maksud saya|maksudnya)\\s+)?$conversationPeriod[?!., ]*$")
// Standalone "vs" is a follow-up; inline "A vs B" retains the existing chronological comparison.
private val explicitComparison = Regex("\\b(?:dibanding(?:kan)?(?: dengan)?|bandingkan dengan|^vs|^versus)\\s+($conversationPeriod)\\b")

private val financialWords = listOf("pengeluaran", "pemasukan", "pendapatan", "penghasilan", "anggaran", "transaksi", "piutang", "tabungan")
private val affirmativeReply = Regex("(?:ya|iya|boleh|silakan|lanjut)(?:[ ,]+(?:boleh|tolong|vina|dong|lanjut|silakan|saja|mau))*[!?. ]*")
private val decliningReply = Regex("(?:(?:tidak|nggak|enggak|ga|gak)(?: dulu| usah| perlu)?|cukup|nanti(?: saja| dulu)?|sudah cukup|stop|jangan dulu)[!?. ]*")
private val socialReply = Regex("(?:(?:kamu )?apa kabar|siapa (?:kamu|anda)|kamu siapa|terima kasih|makasih|makasi|thanks|ok|oke|baik|sip)(?: vina)?[!?. ,]*")

private fun followUpAction(q: String): String? {
    val text = q.trimEnd('?', '!', '.', ',', ' ').removePrefix("tolong ")
        .replace(Regex("^(?:kalau|kalo|dan|terus|lalu)\\s+"), "")
        .replace(Regex("(?:\\s+(?:dong|ya|sih|vina))+$"), "")
    return when {
        text in listOf("rincikan", "jelaskan", "lebih detail", "detailnya", "rincian", "tampilkan rinciannya") -> "rincikan"
        text in listOf("bandingkan", "banding", "bandingin") -> "bandingkan"
        text in listOf("ringkas", "lebih singkat") -> "ringkas"
        text.matches(Regex("(?:kenapa|mengapa|kok)(?: (?:bisa(?: begitu| gitu| naik| turun)?|begitu|demikian|naik|turun|gitu|seperti itu))?")) -> "kenapa"
        text.matches(Regex("(?:(?:yang|apa|mana|transaksi|kategori)(?: yang| mana)? )?(?:paling besar|terbesar|tertinggi|paling tinggi|paling mahal|paling boros)(?: (?:apa|mana|yang mana))?")) ->
            if (text.has("kategori")) "kategori terbesar" else "terbesar"
        text.matches(Regex("(?:kategori (?:lain|lainnya)(?: apa(?: saja)?)?|(?:bagaimana|gimana) kategori (?:lain|lainnya))")) -> "kategori lain"
        else -> null
    }
}

private fun oneTypingError(word: String, target: String): Boolean {
    if (word.length == target.length) {
        val different = word.indices.filter { word[it] != target[it] }
        return different.size == 1 || (different.size == 2 && different[1] == different[0] + 1 &&
            word[different[0]] == target[different[1]] && word[different[1]] == target[different[0]])
    }
    if (kotlin.math.abs(word.length - target.length) != 1) return false
    val longer = if (word.length > target.length) word else target
    val shorter = if (word.length > target.length) target else word
    return longer.indices.any { longer.removeRange(it, it + 1) == shorter }
}

internal fun normalizeLocalQuestion(text: String, protectedNames: List<String> = emptyList()): String {
    val protectedWords = protectedNames.flatMap { Regex("[a-z]+").findAll(it.lowercase(Locale.ROOT)).map { token -> token.value }.toList() }.toSet()
    return text.lowercase(Locale.ROOT).trim()
    .replace(Regex("\\s+"), " ")
    .replace(Regex("\\b(?:pengeleruan|pengeluran|pengluaran|pengeluarann|spending)\\b"), "pengeluaran")
    .replace(Regex("\\bpemasukkan\\b"), "pemasukan")
    .replace(Regex("\\b(?:duit|duid)\\b"), "uang")
    .replace(Regex("\\b(?:kemaren|kmrn)\\b"), "kemarin")
    .replace(Regex("\\bbln\\b"), "bulan").replace(Regex("\\bthn\\b"), "tahun")
    .replace(Regex("\\bhr\\b"), "hari").replace(Regex("\\bpekan\\b"), "minggu")
    .replace(Regex("\\b(minggu|tahun) kemarin\\b"), "$1 lalu")
    .replace(Regex("\\bbulan (?:lagu|kemarin)\\b"), "bulan lalu")
    .replace(Regex("^(?:(?:halo|hai|hei|hi|vina|assalamualaikum|assalamu alaikum|selamat (?:pagi|siang|sore|malam))\\b[\\s,!.?:]*)+"), "")
    .replace(Regex("\\b[a-z]{6,}\\b")) { token ->
        // ponytail: one edit over eight finance terms only; use a lexicon if the vocabulary grows.
        if (token.value in protectedWords || token.value in financialWords) token.value
        else financialWords.filter { oneTypingError(token.value, it) }.singleOrNull() ?: token.value
    }
}

/** Small talk stays separate from the last financial topic; explicit new questions reset filters. */
internal fun localConversationReply(q: String, context: FinancialChatContext): String? = when {
    q.isBlank() || q.matches(Regex("(?:assalamualaikum|assalamu alaikum|siapa (?:kamu|anda)|kamu siapa|(?:kamu )?apa kabar)[?!., ]*")) ->
        "Halo${context.userName.trim().takeIf { it.isNotBlank() }?.let { ", $it" }.orEmpty()}. Saya Vina. Saya membantu membaca transaksi, anggaran, utang, dan target dari data yang tercatat. Apa yang ingin Anda periksa?"
    q.matches(Regex("(?:terima kasih|makasih|makasi|thanks|ok|oke|baik|sip)(?: vina)?[!., ]*")) ->
        "Sama-sama. Anda dapat melanjutkan dengan periode lain atau meminta rincian."
    decliningReply.matches(q) -> "Baik, kita berhenti di sini dulu. Anda bisa melanjutkan kapan saja."
    affirmativeReply.matches(q) -> "Boleh. Apa yang ingin Anda periksa: pengeluaran, pemasukan, atau anggaran?"
    q.matches(Regex("(?:bisa apa|bantu apa|bantuan|contoh pertanyaan|cara pakai vina)[?!., ]*")) ->
        "Coba: ‘Pengeluaran bulan lalu’, ‘Sisa anggaran transport’, atau ‘Insight keuangan’. Setelah itu, Anda bisa bertanya ‘rincikan’ atau ‘kalau bulan ini?’."
    followUpAction(q) != null || explicitComparison.matches(q.trimEnd('?', '!', '.', ',', ' ')) ->
        "Pengeluaran atau pemasukan pada periode mana yang ingin Anda periksa?"
    periodReply.matches(q) -> "Pada periode itu, ingin memeriksa pengeluaran atau pemasukan?"
    else -> null
}

/** Each pair is an actionable question and the invitation shown in the answer. */
internal fun localFollowUps(question: String, answer: String): List<Pair<String, String>> {
    if (question.isBlank()) return listOf("Pengeluaran bulan ini" to "Ingin mulai dari pengeluaran bulan ini?",
        "Insight keuangan" to "Ingin melihat ringkasan keuangan?")
    if (question.has("rincikan", "detail", "ringkas", "lebih singkat")) return emptyList()
    if (answer.contains("Belum ada kategori tambahan")) return emptyList()
    if (answer.startsWith("**") && question.has("pengeluaran", "pemasukan", "transaksi", "belanja", "arus kas", "transfer")) {
        if (answer.contains("Belum ada transaksi tercatat")) return listOf(
            "Panduan mencatat transaksi" to "Ingin saya jelaskan cara mencatat transaksi?")
        val detail = "Rincikan" to "Ingin saya tampilkan rincian transaksi pada periode ini?"
        return if (question.has("bandingkan", "banding", "tren", "vs", "kenapa", "mengapa", "terbaru", "terakhir", "terbesar")) listOf(detail)
        else listOf(detail, "Bandingkan" to "Ingin saya bandingkan dengan periode sebelumnya?")
    }
    if (answer.startsWith("**") && question.has("saldo", "anggaran", "budget", "utang", "hutang", "piutang", "target"))
        return listOf("Insight keuangan" to "Ingin melihat ringkasan hal yang perlu diperhatikan?")
    return emptyList()
}

/** Keep recommendations stable while replying; invitations inside answers remain optional. */
internal fun localSuggestedQuestions(history: List<ChatMessage>): List<String> {
    val completed = history.take(history.indexOfLast { it.sender == ChatSender.ASSISTANT } + 1)
    val reply = completed.lastOrNull()
    val question = localQuestionWithHistory(completed)
    val answer = reply?.text.orEmpty()
    fun normalized(text: String) = normalizeLocalQuestion(text).trimEnd('?', '!', '.', ',', ' ')
    val recent = completed.filter { it.sender == ChatSender.USER }.takeLast(3).map { normalized(it.text) }
    val candidates = buildList {
        if (reply?.isError != true) {
            val transaction = answer.startsWith("**") && question.has("pengeluaran", "pemasukan", "transaksi", "belanja", "arus kas", "transfer")
            val empty = answer.contains("Belum ada transaksi tercatat") || answer.contains("Belum ada kategori tambahan")
            val comparison = question.has("bandingkan", "banding", "vs", "tren", "kenapa", "mengapa")
            if (transaction && !empty && comparison && !question.has("kenapa", "mengapa")) add("Kenapa?")
            addAll(localFollowUps(question, answer).map { it.first })
            if (transaction) {
                if (!empty) {
                    if (!question.has("terbesar", "tertinggi", "paling mahal")) add("Yang paling besar?")
                    if (!comparison) add("Bandingkan")
                    if (!question.has("kategori lain") && reply?.transactionEvidence.orEmpty().any {
                        !it.isComparison && it.transactions.map { tx -> tx.categoryId }.distinct().size > 5
                    } && (question.has("pengeluaran", "belanja") != question.has("pemasukan", "pendapatan", "penghasilan")) &&
                        !question.has("arus kas", "transfer")) add("Kategori lainnya?")
                }
                add(if (explicitComparison.replace(question, "").has("bulan lalu")) "Kalau bulan ini?" else "Kalau bulan lalu?")
            }
        }
        addAll(listOf("Pengeluaran bulan ini", "Anggaran saya", "Saldo saya", "Panduan mencatat transaksi", "Insight keuangan"))
    }
    return candidates.distinct().filter { normalized(it) !in recent && normalized(it) != normalized(question) }.take(3)
}

internal fun localQuestionWithHistory(history: List<ChatMessage>, protectedNames: List<String> = emptyList()): String {
    val users = history.withIndex().filter { it.value.sender == ChatSender.USER }
    var question = ""
    var action = ""
    var comparison = ""
    var otherCategories = false
    var output = ""
    fun scopedQuestion() = listOf(question, if (otherCategories) "kategori lain" else "", comparison.takeIf { it.isNotBlank() }?.let { "bandingkan dengan $it" }.orEmpty(),
        action.takeUnless { it == "bandingkan" && comparison.isNotBlank() }.orEmpty()).filter { it.isNotBlank() }.joinToString(" ")
    fun hasTopic() = question.has("pengeluaran", "pemasukan", "pendapatan", "penghasilan", "belanja", "transaksi", "arus kas", "transfer",
        "saldo", "utang", "hutang", "piutang", "anggaran", "budget", "target", "tabungan") ||
        protectedNames.any { question.has(it.lowercase(Locale.ROOT)) }
    users.forEach { (index, message) ->
        var current = normalizeLocalQuestion(message.text, protectedNames)
        if (affirmativeReply.matches(current)) {
            val previous = history.subList(0, index).lastOrNull { it.sender == ChatSender.ASSISTANT }
            val offered = previous?.takeUnless { it.isError }?.let { reply ->
                localFollowUps(scopedQuestion(), reply.text).firstOrNull { reply.text.trimEnd().endsWith(it.second) }
            }
            current = offered?.first?.let { normalizeLocalQuestion(it, protectedNames) } ?: current
        }
        val nextAction = followUpAction(current)
        val comparisonMatch = explicitComparison.find(current)
        output = when {
            current.isBlank() || socialReply.matches(current) || affirmativeReply.matches(current) || decliningReply.matches(current) -> current
            periodReply.matches(current) -> {
                if (hasTopic()) {
                    question = conversationPeriods.replace(question, "").trim() + " " + conversationPeriods.find(current)!!.value
                    scopedQuestion()
                } else {
                    question = current
                    action = ""
                    comparison = ""
                    otherCategories = false
                    current
                }
            }
            comparisonMatch != null && comparisonMatch.value == current.trimEnd('?', '!', '.', ',', ' ') && hasTopic() -> {
                comparison = comparisonMatch.groupValues[1]
                action = "bandingkan"
                scopedQuestion()
            }
            nextAction != null && hasTopic() -> {
                if (nextAction == "kategori lain") {
                    otherCategories = true
                    action = ""
                } else action = nextAction
                if (action == "bandingkan") comparison = ""
                scopedQuestion()
            }
            current.matches(Regex("(?:kalau |kalo |dan )?(?:pemasukan|pengeluaran)(?:nya)?[?!., ]*")) &&
                (hasTopic() || conversationPeriods.containsMatchIn(question)) -> {
                question = (if (current.contains("pemasukan")) "pemasukan" else "pengeluaran") + " " +
                    conversationPeriods.findAll(question).joinToString(" ") { it.value }
                action = ""
                comparison = ""
                otherCategories = false
                question.trim()
            }
            else -> {
                question = current
                action = ""
                comparison = ""
                otherCategories = false
                if (comparisonMatch != null && hasTopic()) {
                    question = explicitComparison.replace(current, "").trim()
                    comparison = comparisonMatch.groupValues[1]
                    action = "bandingkan"
                }
                scopedQuestion()
            }
        }
    }
    return output
}

private fun queryPeriod(q: String, today: LocalDate, transactions: List<Transaction>): ReportDateRange {
    require(!Regex("\\bhari (?:senin|selasa|rabu|kamis|jumat|sabtu|minggu)\\b").containsMatchIn(q))
    val numericDate = java.time.format.DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT)
    val dates = Regex("\\b(?:\\d{4}-\\d{2}-\\d{2}|\\d{1,2}/\\d{1,2}/\\d{4})\\b").findAll(q).map {
        if ('/' in it.value) LocalDate.parse(it.value, numericDate) else LocalDate.parse(it.value)
    }.toList()
    if (dates.isNotEmpty()) {
        require(dates.size <= 2)
        return ReportDateRange(dates.first(), dates.last())
    }
    val namedMonths = Regex("\\b(?:${(monthNames + abbreviations).distinct().joinToString("|")})\\b").findAll(q).count()
    require(namedMonths <= 1) { "Sebutkan satu periode atau rentang tanggal lengkap." }
    val relative = Regex("\\b(\\d+) (minggu|bulan|tahun) lalu\\b").find(q)
    if (relative != null) {
        val count = relative.groupValues[1].toLong()
        require(count in 1..120)
        return when (relative.groupValues[2]) {
            "minggu" -> today.with(DayOfWeek.MONDAY).minusWeeks(count).let { ReportDateRange(it, it.plusDays(6)) }
            "bulan" -> YearMonth.from(today).minusMonths(count).let { ReportDateRange(it.atDay(1), it.atEndOfMonth()) }
            else -> (today.year - count.toInt()).let { ReportDateRange(LocalDate.of(it, 1, 1), LocalDate.of(it, 12, 31)) }
        }
    }
    monthNames.indices.forEach { i ->
        val match = Regex("\\b(?:${monthNames[i]}|${abbreviations[i]})(?: (\\d{4}))?\\b").find(q)
        if (match != null) {
            val month = YearMonth.of(match.groupValues[1].toIntOrNull() ?: today.year, i + 1)
            return ReportDateRange(month.atDay(1), month.atEndOfMonth())
        }
    }
    val days = Regex("\\b(\\d+) hari terakhir\\b").find(q)?.groupValues?.get(1)?.toLongOrNull()
    if (days != null) { require(days in 1..3660); return ReportDateRange(today.minusDays(days - 1), today) }
    val month = YearMonth.from(today)
    val monday = today.with(DayOfWeek.MONDAY)
    val year = Regex("\\btahun (\\d{4})\\b").find(q)?.groupValues?.get(1)?.toIntOrNull()
    return when {
        q.has("hari ini") -> ReportDateRange(today, today)
        q.has("kemarin", "sehari yang lalu") -> ReportDateRange(today.minusDays(1), today.minusDays(1))
        q.has("seminggu terakhir") -> ReportDateRange(today.minusDays(6), today)
        q.has("sebulan terakhir") -> ReportDateRange(today.minusDays(29), today)
        q.has("minggu lalu") && !q.has("minggu ini") -> ReportDateRange(monday.minusWeeks(1), monday.minusDays(1))
        q.has("minggu ini") -> ReportDateRange(monday, monday.plusDays(6))
        q.has("bulan lalu") && !q.has("bulan ini") -> month.minusMonths(1).let { ReportDateRange(it.atDay(1), it.atEndOfMonth()) }
        q.has("tahun lalu") && !q.has("tahun ini") -> ReportDateRange(LocalDate.of(today.year - 1, 1, 1), LocalDate.of(today.year - 1, 12, 31))
        q.has("tahun ini") || year != null -> (year ?: today.year).let { ReportDateRange(LocalDate.of(it, 1, 1), LocalDate.of(it, 12, 31)) }
        q.has("semua", "sepanjang waktu", "terakhir", "terbaru", "paling baru") -> {
            val recordedDates = transactions.mapNotNull { runCatching { LocalDate.parse(it.transactionDate) }.getOrNull() }
            ReportDateRange(recordedDates.minOrNull() ?: today, recordedDates.maxOrNull() ?: today)
        }
        q.has("abad", "depan", "tanggal", "periode") -> error("Periode belum dikenali")
        else -> ReportDateRange(month.atDay(1), month.atEndOfMonth())
    }
}

private fun categoryMentioned(q: String, category: Category): Boolean {
    if (category.id == "cat_makanan" && q.has("bahan makanan")) return false
    if (category.name.lowercase(Locale.ROOT) in listOf("belanja", "pengeluaran", "pemasukan", "transfer", "tabungan"))
        return q.has("kategori ${category.name.lowercase(Locale.ROOT)}", "untuk ${category.name.lowercase(Locale.ROOT)}")
    val aliases = when (category.id) {
        "cat_makanan" -> arrayOf("makan", "makanan", "minuman")
        "cat_transportasi" -> arrayOf("transport", "transportasi", "ojek", "ojol", "ongkos")
        "cat_bensin" -> arrayOf("bensin", "bbm")
        "cat_bahan_makanan" -> arrayOf("bahan makanan", "dapur")
        "cat_freelance" -> arrayOf("freelance", "jasa")
        "cat_penjualan" -> arrayOf("usaha", "penjualan", "toko")
        else -> emptyArray()
    }
    return q.has(category.name.lowercase(Locale.ROOT), *aliases) ||
        (category.name.contains('/') && category.name.split('/').any { q.has(it.trim().lowercase(Locale.ROOT)) })
}

/** Returns null when an existing conversational answer should handle the question. */
internal fun answerLocalDataQuestion(q: String, context: FinancialChatContext,
    onEvidence: (com.cashflow.app.data.model.ChatTransactionEvidence) -> Unit = {}): String? {
    val today = runCatching { LocalDate.parse(context.currentDate) }.getOrNull() ?: return null
    fun money(amount: Long) = Formatters.formatRupiah(amount)
    val otherCategories = q.has("kategori lain")
    val topicQuestion = if (otherCategories) q.replace("kategori lain", "") else q
    val mentionedCategories = context.categories.filter { categoryMentioned(topicQuestion, it) }
    val accounts = context.assets.filter { q.has(it.name.lowercase(Locale.ROOT)) &&
        !(it.name.equals("kas", true) && q.has("arus kas") && !q.has("dari kas", "di kas", "pakai kas")) }
    val intent = (accounts.map { it.name } + mentionedCategories.map { it.name }).fold(q) { text, name ->
        text.replace(Regex("\\b${Regex.escape(name.lowercase(Locale.ROOT))}\\b"), " ")
    }
    val cashflow = q.has("arus kas", "cashflow", "surplus", "defisit", "sisa pemasukan")
    val expense = cashflow || q.has("pengeluaran", "belanja", "biaya", "ongkos", "uang keluar", "keluar uang", "habis berapa", "habis buat", "habis untuk", "keluar", "boros") ||
        (mentionedCategories.any { it.type == "expense" } && !q.has("saldo", "anggaran", "budget", "limit", "target"))
    val income = cashflow || q.has("pemasukan", "penghasilan", "pendapatan", "income", "uang masuk", "masuk uang", "gaji") || mentionedCategories.any { it.type == "income" }
    val transfer = q.has("transfer", "dipindahkan", "pindah uang")
    val transactionQuestion = expense || income || transfer || q.has("transaksi")
    if (otherCategories && expense == income)
        return "Ingin melihat kategori pengeluaran atau pemasukan?"

    if (intent.has("hapus", "catat", "tambahkan", "ubah") && !intent.has("cara", "bagaimana", "gimana"))
        return "Saya belum bisa mengubah catatan lewat chat. Gunakan tombol (+) untuk mencatat transaksi, atau buka detail transaksi untuk mengubah atau menghapusnya."
    if (q.has("saldo", "anggaran", "budget", "limit", "target", "goal") &&
        (q.has("kemarin", "minggu lalu", "bulan lalu", "tahun lalu", *(monthNames + abbreviations).toTypedArray()) || Regex("\\d{4}-\\d{2}-\\d{2}").containsMatchIn(q)))
        return "Saya hanya memiliki status saldo, anggaran, dan target saat ini. Riwayat status pada tanggal atau periode itu belum tersedia. Untuk riwayat uang masuk/keluar, tanyakan pemasukan atau pengeluaran pada periode tersebut."

    if (intent.has("piutang", "belum bayar saya", "uang yang dipinjam") || intent.has("utang", "hutang", "cicilan", "pinjol", "harus saya bayar")) {
        val receivable = intent.has("piutang", "belum bayar saya", "uang yang dipinjam")
        var debts = context.debts.filter { it.status != DebtStatus.PAID.key && it.remainingAmount > 0 && (it.direction == "receivable") == receivable }
        val named = debts.filter { q.has(it.lenderName.lowercase(Locale.ROOT), it.title.lowercase(Locale.ROOT)) }
        if (named.isNotEmpty()) debts = named
        if (q.has("telat", "terlambat", "lewat jatuh tempo", "menunggak")) debts = debts.filter { runCatching { LocalDate.parse(it.dueDate).isBefore(today) }.getOrDefault(false) }
        val title = if (receivable) "Piutang" else "Utang & Cicilan"
        return buildString {
            append("**Ringkasan Catatan $title**\nTotal sisa: **${money(debts.sumOf { it.remainingAmount })}**\n")
            if (debts.isEmpty()) append("Belum ada catatan yang cocok.")
            debts.forEach { append("• ${it.title} — ${it.lenderName}: ${money(it.remainingAmount)}, jatuh tempo ${it.dueDate}\n") }
        }.trimEnd()
    }
    val goals = context.goals.filter { q.has(it.name.lowercase(Locale.ROOT)) }
    if (q.has("target", "goal", "impian", "berapa lagi") && (goals.isNotEmpty() || !transactionQuestion)) {
        val chosen = goals.ifEmpty { context.goals }
        return "**Target Tabungan**\n" + if (chosen.isEmpty()) "Belum ada target tercatat." else chosen.joinToString("\n") {
            "• ${it.name}: terkumpul ${money(it.currentAmount)} dari ${money(it.targetAmount)}, kurang **${money((it.targetAmount - it.currentAmount).coerceAtLeast(0))}**; tenggat ${it.deadline}"
        }
    }
    if (q.has("anggaran", "budget", "limit", "jebol")) {
        val budgets = context.budgets.filter { mentionedCategories.isEmpty() || it.budget.categoryId in mentionedCategories.map { cat -> cat.id } }
        return "**Status Anggaran Belanja**\n" + if (budgets.isEmpty()) "Belum ada anggaran yang cocok." else budgets.joinToString("\n") {
            "• ${it.category?.name ?: "Kategori"}: terpakai ${money(it.spentAmount)} dari ${money(it.effectiveTargetAmount)}; " +
                if (it.remainingAmount >= 0) "sisa **${money(it.remainingAmount)}**" else "melewati batas **${money(-it.remainingAmount)}**"
        }
    }
    if (!transactionQuestion && accounts.isNotEmpty()) {
        return "**Saldo Rekening Saat Ini**\n" + accounts.joinToString("\n") { "• ${it.name}: **${money(it.currentBalance)}**" }
    }
    val requestedAccount = Regex("\\b(?:saldo|rekening|uang di) ([\\p{L}\\d_-]+)").find(q)?.groupValues?.get(1)
    if (accounts.isEmpty() && requestedAccount != null && requestedAccount !in listOf("saya", "aku", "total", "semua", "dan", "saat", "sekarang", "berapa", "brp", "rekening", "aset", "bank", "bulan", "minggu", "tahun", "hari", "terbesar", "terkecil"))
        return "Rekening itu belum ditemukan. Rekening yang tercatat: ${context.assets.joinToString { it.name }.ifBlank { "belum ada" }}. Sebutkan nama rekening yang ingin diperiksa."
    val source = context.transactions ?: run {
        if (transactionQuestion && (q.has("hari ini", "kemarin", "minggu ini", "minggu lalu", "tahun ini", "tahun lalu", "tanggal") || Regex("\\d{4}-\\d{2}-\\d{2}").containsMatchIn(q)))
            return "Data transaksi rinci belum tersedia untuk periode itu. Saat ini hanya ringkasan bulan berjalan dan bulan lalu yang tersedia."
        return null
    }
    if (!transactionQuestion) return null
    // Existing health/tips/purchase answers remain responsible for advice rather than a transaction sum.
    if (intent.has("tips", "saran", "hemat", "sehat", "audit", "bisa beli", "boleh beli", "mau beli", "simulasi", "cara", "panduan", "bantuan")) return null
    val comparisonPeriod = explicitComparison.find(intent)?.groupValues?.get(1)
    val range = runCatching { queryPeriod(explicitComparison.replace(intent, ""), today, source) }.getOrElse {
        return "Rentang tanggalnya belum jelas. Sebutkan satu periode, misalnya 2 bulan lalu atau September 2026, atau tulis tanggal awal sampai akhir (DD/MM/YYYY atau YYYY-MM-DD)."
    }
    val comparisonRange = comparisonPeriod?.let { period ->
        runCatching { queryPeriod(period, today, source) }.getOrElse {
            return "Periode pembandingnya belum jelas. Sebutkan periode atau rentang tanggal yang ingin dibandingkan."
        }
    }
    val types = if (transfer) listOf("transfer") else if (expense && income) listOf("expense", "income") else if (expense) listOf("expense") else if (income) listOf("income") else listOf("expense", "income", "transfer")
    var matching = source.filter { !it.isAdjustment && it.type in types &&
        (mentionedCategories.isEmpty() || (it.categoryId in mentionedCategories.map { cat -> cat.id }) != otherCategories) &&
        (accounts.isEmpty() || it.assetId in accounts.map { account -> account.id } || (it.type == "transfer" && it.destinationAssetId in accounts.map { account -> account.id })) }
    if (otherCategories && mentionedCategories.isEmpty()) {
        val shown = matching.filter { range.contains(it.transactionDate) }.groupBy { it.categoryId }
            .mapValues { (_, rows) -> rows.sumOf { it.amount } }.entries.sortedByDescending { it.value }.take(5).map { it.key }.toSet()
        matching = matching.filterNot { it.categoryId in shown }
    }
    val inRange = matching.filter { range.contains(it.transactionDate) }
    val latest = q.has("terbaru", "paling baru") || (q.has("terakhir") &&
        !Regex("\\b(?:\\d+ hari|seminggu|sebulan) terakhir\\b").containsMatchIn(q))
    val transactions = if (latest)
        inRange.sortedWith(compareByDescending<Transaction> { it.transactionDate }.thenByDescending { it.transactionTime }.thenByDescending { it.createdAt }).take(1)
    else inRange
    val label = when { transfer -> "Transfer"; expense && income -> "Arus Kas"; expense -> "Pengeluaran"; income -> "Pemasukan"; else -> "Transaksi" }
    val explainingChange = q.has("kenapa", "mengapa") && expense != income
    val period = if (range.start == range.end) range.start.toString() else "${range.start} sampai ${range.end}"
    val categoryLabel = when {
        otherCategories && mentionedCategories.isNotEmpty() -> "Selain " + mentionedCategories.joinToString { it.name }
        otherCategories -> "Kategori tambahan di luar lima kategori teratas"
        else -> mentionedCategories.joinToString(" · ") { it.name }
    }
    val filters = (listOf(categoryLabel) + accounts.map { it.name }).filter { it.isNotBlank() }.joinToString(" · ")
    onEvidence(com.cashflow.app.data.model.ChatTransactionEvidence(
        listOf(label, filters).filter { it.isNotBlank() }.joinToString(" · "), range, transactions,
        isCashflow = expense && income))
    return buildString {
        append("**$label — $period**\n")
        if (filters.isNotBlank()) append("$filters\n")
        if (expense && income) {
            val totalIncome = transactions.filter { it.type == "income" }.sumOf { it.amount }
            val totalExpense = transactions.filter { it.type == "expense" }.sumOf { it.amount }
            append("Pemasukan: **${money(totalIncome)}**\nPengeluaran: **${money(totalExpense)}**\n")
            append("Arus kas bersih: **${money(totalIncome - totalExpense)}**\n")
        } else append("**${transactions.size} transaksi**, total **${money(transactions.sumOf { it.amount })}**\n")
        if (q.has("bandingkan", "banding", "vs", "tren", "naik", "turun", "kenapa", "mengapa")) {
            val previous = comparisonRange ?: if (range.start.dayOfYear == 1 && range.end == LocalDate.of(range.start.year, 12, 31))
                ReportDateRange(range.start.minusYears(1), range.end.minusYears(1))
            else if (range.start.dayOfMonth == 1 && range.end == YearMonth.from(range.start).atEndOfMonth())
                YearMonth.from(range.start).minusMonths(1).let { ReportDateRange(it.atDay(1), it.atEndOfMonth()) }
            else (ChronoUnit.DAYS.between(range.start, range.end) + 1).let { ReportDateRange(range.start.minusDays(it), range.start.minusDays(1)) }
            fun value(txs: List<Transaction>) = txs.sumOf { if (expense && income && it.type == "expense") -it.amount else it.amount }
            val before = value(matching.filter { previous.contains(it.transactionDate) })
            onEvidence(com.cashflow.app.data.model.ChatTransactionEvidence(
                listOf(label, filters).filter { it.isNotBlank() }.joinToString(" · "), previous,
                matching.filter { previous.contains(it.transactionDate) }, isComparison = true, isCashflow = expense && income))
            val difference = value(transactions) - before
            append("Periode pembanding ${previous.start} sampai ${previous.end}: **${money(before)}**\nSelisih: **${money(difference)}**\n")
            if (explainingChange) {
                val prior = matching.filter { previous.contains(it.transactionDate) }
                val currentByCategory = transactions.groupBy { it.categoryId }.mapValues { (_, txs) -> txs.sumOf { it.amount } }
                val previousByCategory = prior.groupBy { it.categoryId }.mapValues { (_, txs) -> txs.sumOf { it.amount } }
                val categoryIds = currentByCategory.keys + previousByCategory.keys
                append("Perubahan per kategori (periode terpilih dibanding pembanding):\n")
                categoryIds.map { id -> id to ((currentByCategory[id] ?: 0L) - (previousByCategory[id] ?: 0L)) }
                    .filter { it.second != 0L }.sortedByDescending { kotlin.math.abs(it.second) }.take(3).forEach { (id, delta) ->
                        val name = context.categories.find { it.id == id }?.name ?: "Tanpa kategori"
                        append("• $name: ${if (delta > 0) "naik" else "turun"} ${money(kotlin.math.abs(delta))}\n")
                    }
                append("Angka ini menjelaskan perubahan catatan, bukan penyebab pribadi di balik transaksi.\n")
            }
        }
        if (transactions.isEmpty()) {
            append(if (otherCategories) "Belum ada kategori tambahan pada rentang dan filter ini." else "Belum ada transaksi tercatat pada rentang dan filter ini.")
            return@buildString
        }
        if (q.has("rata-rata", "rerata", "per hari", "harian")) {
            val days = (ChronoUnit.DAYS.between(range.start, minOf(range.end, today)) + 1).coerceAtLeast(1)
            append("Rata-rata: **${money(transactions.sumOf { it.amount } / days)}/hari** ($days hari kalender).\n")
        }
        val details = when {
            q.has("terbesar", "tertinggi", "paling mahal") && !topicQuestion.has("kategori") -> transactions.sortedByDescending { it.amount }.take(5)
            latest -> transactions
            q.has("rincikan", "detail") -> transactions.sortedWith(compareByDescending<Transaction> { it.transactionDate }.thenByDescending { it.transactionTime }).take(5)
            else -> emptyList()
        }
        details.forEach { append("• ${it.transactionDate} ${it.transactionTime} — ${it.description}: ${money(it.amount)}\n") }
        if (q.has("rincikan", "detail") && transactions.size > details.size) append("Menampilkan ${details.size} dari ${transactions.size} transaksi.\n")
        if (details.isEmpty() && (expense || income) && !q.has("ringkas") && !explainingChange) transactions.filter { it.type == if (expense) "expense" else "income" }.groupBy { it.categoryId }.map { (id, txs) ->
            (context.categories.find { it.id == id }?.name ?: "Tanpa kategori") to txs.sumOf { it.amount }
        }.sortedByDescending { it.second }.take(5).forEach { append("• ${it.first}: ${money(it.second)}\n") }
    }.trimEnd()
}
