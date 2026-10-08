package com.cashflow.app.voice

import com.cashflow.app.data.model.Asset
import com.cashflow.app.data.model.Category
import com.cashflow.app.data.model.TransactionType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.regex.Pattern

data class ParsedVoiceResult(
    val rawText: String,
    val type: TransactionType?,
    val amount: Long?,
    val description: String,
    val detectedCategory: Category?,
    val matchedAsset: Asset?,
    val destinationAsset: Asset? = null,
    val transactionDate: String, // "YYYY-MM-DD"
    val transactionTime: String, // "HH:mm"
    val isAssetMissing: Boolean = false,
    val assetMismatchName: String? = null,
    val suggestedDefaultAsset: Asset? = null,
    val isAmountMissing: Boolean = false,
    val note: String = ""
)

object VoiceTransactionParser {

    fun parse(
        spokenText: String,
        availableAssets: List<Asset>,
        availableCategories: List<Category>,
        defaultAsset: Asset? = null
    ): ParsedVoiceResult {
        val originalText = spokenText.trim()
        val normalized = normalizeText(originalText)

        // 1. Detect Transaction Type
        val type = detectType(normalized)

        // 2. Parse Amount
        val amount = parseNominal(normalized)
        val isAmountMissing = (amount == null || amount <= 0)

        // 3. Parse Date & Time
        val date = parseDate(normalized)
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

        // 4. Asset Matching & Resolution
        val (sourceAsset, destAsset, assetMismatch, isAssetMissing) = resolveAssets(
            normalizedText = normalized,
            type = type,
            availableAssets = availableAssets,
            defaultAsset = defaultAsset
        )

        // 5. Category Detection
        val detectedCategory = detectCategory(normalized, type, availableCategories)

        // 6. Clean Description Extraction
        val description = extractDescription(originalText, type, detectedCategory, sourceAsset, destAsset)

        return ParsedVoiceResult(
            rawText = originalText,
            type = type,
            amount = amount,
            description = description,
            detectedCategory = detectedCategory,
            matchedAsset = sourceAsset ?: defaultAsset,
            destinationAsset = destAsset,
            transactionDate = date.format(DateTimeFormatter.ISO_LOCAL_DATE),
            transactionTime = time,
            isAssetMissing = (sourceAsset == null && defaultAsset == null),
            assetMismatchName = assetMismatch,
            suggestedDefaultAsset = if (sourceAsset == null) defaultAsset else null,
            isAmountMissing = isAmountMissing,
            note = if (originalText.isNotBlank()) "Input suara: \"$originalText\"" else ""
        )
    }

    private fun normalizeText(text: String): String {
        var res = text.lowercase(Locale.ROOT)
        // Normalize common Indonesian spoken variations
        val replacements = mapOf(
            "kemaren" to "kemarin",
            "dapet" to "dapat",
            "nerima" to "terima",
            "rebu" to "ribu",
            "rb" to "ribu",
            "kilo" to "ribu",
            "ngopi" to "kopi",
            "ngebakso" to "bakso",
            "maksi" to "makan siang",
            "dimandiin" to "di mandiri",
            "topup" to "top up",
            "trf" to "transfer",
            "tf" to "transfer",
            "sejuteng" to "satu juta",
            "sejeti" to "satu juta",
            "dujeti" to "dua juta",
            "cepek ceng" to "seratus ribu",
            "cepek ribu" to "seratus ribu"
        )
        for ((from, to) in replacements) {
            res = res.replace(Regex("\\b$from\\b"), to)
        }
        return res
    }

    private fun detectType(text: String): TransactionType {
        // Special Transfer intents:
        val specialTransferPatterns = listOf(
            "tarik tunai", "ambil cash", "ambil uang di atm", "tarik duit", "tarik uang",
            "setor tunai", "setor uang", "setor cdm",
            "top up", "isi saldo", "transfer", "pindah", "pindahkan", "pindahin",
            "oper dana", "oper uang", "kirim uang ke", "kirim ke", "kirim dana ke",
            "sisihkan ke", "tabung ke", "simpan ke tabungan"
        )
        if (specialTransferPatterns.any { text.contains(it) }) {
            return TransactionType.TRANSFER
        }

        // Income intent:
        val incomeKeywords = listOf(
            "gaji", "gajian", "payroll", "upah", "honor", "honorarium", "insentif", "uang saku", "sangu",
            "tunjangan", "thr", "freelance", "proyek", "side hustle", "komisi", "fee",
            "dapat uang", "dapat duit", "terima uang", "terima transferan", "dapat transferan", "dapat freelance", "dapat kiriman",
            "pemasukan", "masuk ke", "masuk mandiri", "masuk bca", "masuk gopay", "masuk tabungan", "masuk rekening",
            "cair", "bonus", "hadiah", "angpao", "cashback", "refund", "penjualan", "jualan",
            "laku", "untung", "profit", "omset", "omzet", "dividen", "bunga tabungan", "bagi hasil",
            "reimburse", "klaim kantor", "doorprize", "giveaway"
        )
        if (incomeKeywords.any { text.contains(it) }) {
            return TransactionType.INCOME
        }

        // Expense intent:
        val expenseKeywords = listOf(
            "beli", "membeli", "belanja", "jajan", "pesan", "order", "checkout",
            "makan", "minum", "kopi", "sarapan", "makan siang", "dinner", "makan malam",
            "nyemil", "ngemil", "bakso", "bayar", "membayar", "lunasi", "cicil", "angsur",
            "tagihan", "iuran", "patungan", "denda", "sumbangan", "infaq", "sedekah",
            "zakat", "donasi", "tip", "isi bensin", "isi pulsa", "isi token", "isi kuota",
            "ongkos", "parkir", "tarif", "bensin", "pertamax", "pertalite", "solar", "tol", "ojol",
            "taksi", "angkot", "tiket", "keluar duit", "habis", "kepakai", "pengeluaran",
            "servis", "service", "bengkel", "ganti oli", "cuci motor", "cuci mobil", "laundry",
            "obat", "apotek", "apotik", "dokter", "klinik", "spp", "ukt", "sewa", "kos", "kontrakan"
        )
        if (expenseKeywords.any { text.contains(it) }) {
            return TransactionType.EXPENSE
        }

        return TransactionType.EXPENSE // standard fallback
    }

    fun parseNominal(text: String): Long? {
        if (text.isBlank()) return null

        // --- STEP 1: Pre-processing & Normalization of text ---
        var clean = text.lowercase(Locale.ROOT).trim()

        // 1.1 Strip currency symbols and prefixes so digits don't stick to letters
        // Handles: "rp 50.000", "rp. 50.000", "rp50.000", "rp.50.000", "idr 50000"
        clean = clean.replace(Regex("(?:rp\\.?|idr)\\s*"), " ")

        // 1.2 Strip trailing currency markers: "rupiah", "perak"
        clean = clean.replace(Regex("\\b(?:rupiah|perak)\\b"), "")

        // 1.3 Normalize concatenated Indonesian number words (Google SpeechRecognizer artifact)
        val concatenatedNumbers = mapOf(
            "duapuluh" to "dua puluh", "tigapuluh" to "tiga puluh", "empatpuluh" to "empat puluh",
            "limapuluh" to "lima puluh", "enampuluh" to "enam puluh", "tujuhpuluh" to "tujuh puluh",
            "delapanpuluh" to "delapan puluh", "sembilanpuluh" to "sembilan puluh",
            "duaratus" to "dua ratus", "tigaratus" to "tiga ratus", "empatratus" to "empat ratus",
            "limaratus" to "lima ratus", "enamratus" to "enam ratus", "tujuhratus" to "tujuh ratus",
            "delapanratus" to "delapan ratus", "sembilanratus" to "sembilan ratus",
            "duaribu" to "dua ribu", "tigaribu" to "tiga ribu", "empatribu" to "empat ribu",
            "limaribu" to "lima ribu", "sepuluhribu" to "sepuluh ribu", "duapuluhribu" to "dua puluh ribu",
            "duajuta" to "dua juta", "tigajuta" to "tiga juta", "limajuta" to "lima juta",
            "duabelas" to "dua belas", "tigabelas" to "tiga belas", "empatbelas" to "empat belas",
            "limabelas" to "lima belas", "enambelas" to "enam belas", "tujuhbelas" to "tujuh belas",
            "delapanbelas" to "delapan belas", "sembilanbelas" to "sembilan belas"
        )
        for ((concat, separated) in concatenatedNumbers) {
            clean = clean.replace(Regex("\\b$concat\\b"), separated)
        }

        // 1.4 Normalize colloquial suffixes: "ribuan", "rebuan", "rebu", "rebo" -> "ribu"
        clean = clean.replace(Regex("\\b(?:ribuan|rebuan|rebu|rebo)\\b"), "ribu")
        clean = clean.replace(Regex("\\b(?:jutaan)\\b"), "juta")

        // 1.5 Strip trailing cents/sen: e.g. ",00" or ".00" after 3+ digits (e.g. 50.000,00 -> 50.000)
        clean = clean.replace(Regex("(?<=\\d{3})[,.]00\\b"), "")

        // --- STEP 2: Indonesian Slang Numbers (Peranakan / Pasar terms) ---
        val slangMap = listOf(
            "goban" to 50000L,
            "noban" to 20000L,
            "ceban" to 10000L,
            "goceng" to 5000L,
            "noceng" to 2000L,
            "seceng" to 1000L,
            "pego" to 150000L,
            "gopek" to 500L,
            "cepek" to 100L
        )
        for ((slang, value) in slangMap) {
            if (Pattern.compile("\\b$slang\\b", Pattern.CASE_INSENSITIVE).matcher(clean).find()) {
                if (slang == "cepek" && clean.contains("cepek ribu")) return 100000L
                return value
            }
        }

        // --- STEP 3: Spoken Fractions & Spoken Millions ---
        if (clean.contains("satu setengah juta") || clean.contains("1 setengah juta")) return 1500000L
        if (clean.contains("dua setengah juta") || clean.contains("2 setengah juta")) return 2500000L
        if (clean.contains("tiga setengah juta") || clean.contains("3 setengah juta")) return 3500000L
        if (clean.contains("empat setengah juta") || clean.contains("4 setengah juta")) return 4500000L
        if (clean.contains("lima setengah juta") || clean.contains("5 setengah juta")) return 5500000L
        if (clean.contains("tiga perempat juta")) return 750000L
        if (clean.contains("seperempat juta")) return 250000L
        if (clean.contains("setengah juta") || clean.contains("1/2 juta") || clean.contains("setengah jt")) return 500000L

        // "satu koma lima juta", "dua koma lima juta"
        val komaJutaRegex = Pattern.compile("(\\w+)\\s+koma\\s+(\\w+)\\s+(?:juta|jt)", Pattern.CASE_INSENSITIVE).matcher(clean)
        if (komaJutaRegex.find()) {
            val d1 = wordToDigit(komaJutaRegex.group(1) ?: "")
            val d2 = wordToDigit(komaJutaRegex.group(2) ?: "")
            if (d1 != null && d2 != null) {
                return (d1 * 1000000L) + (d2 * 100000L)
            }
        }

        // --- STEP 4: Compound Millions + Thousands (e.g. "2 juta 500 ribu", "1jt 200rb", "1 juta 200 ribu") ---
        val compoundJutaRibuRegex = Pattern.compile("(\\d+)\\s*(?:juta|jt)\\s*(\\d+)\\s*(?:ribu|rb|k)\\b", Pattern.CASE_INSENSITIVE).matcher(clean)
        if (compoundJutaRibuRegex.find()) {
            val millions = compoundJutaRibuRegex.group(1)?.toLongOrNull() ?: 0L
            val thousands = compoundJutaRibuRegex.group(2)?.toLongOrNull() ?: 0L
            return (millions * 1000000L) + (thousands * 1000L)
        }

        // --- STEP 5: Compound Thousands + Hundreds (e.g. "12 ribu 500", "12rb 500", "25 ribu 500") ---
        val compoundRibuRatusRegex = Pattern.compile("(\\d+)\\s*(?:ribu|rb|k)\\s*(\\d{1,3})\\b", Pattern.CASE_INSENSITIVE).matcher(clean)
        if (compoundRibuRatusRegex.find()) {
            val thousands = compoundRibuRatusRegex.group(1)?.toLongOrNull() ?: 0L
            val hundreds = compoundRibuRatusRegex.group(2)?.toLongOrNull() ?: 0L
            if (thousands > 0) {
                val actualHundreds = if (hundreds in 1..9) hundreds * 100L else hundreds
                return (thousands * 1000L) + actualHundreds
            }
        }

        // --- STEP 6: Formatted Currency Numbers with Thousands Separator ('.' or ',') ---
        // Matches: "50.000.000", "5.000.000", "1.500.000", "350.000", "50.000", "25.000", "12.500", "2.000", "50,000"
        val formattedThousandsRegex = Pattern.compile("\\b(\\d{1,3}(?:[.,]\\d{3})+)\\b").matcher(clean)
        if (formattedThousandsRegex.find()) {
            val raw = formattedThousandsRegex.group(1)!!.replace(".", "").replace(",", "")
            val num = raw.toLongOrNull()
            if (num != null && num > 0) return num
        }

        // --- STEP 7: Decimal Millions with Digits (e.g. "1.5 juta", "2.75 juta", "1,5jt") ---
        val decimalJutaRegex = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:juta|jt)\\b", Pattern.CASE_INSENSITIVE).matcher(clean)
        if (decimalJutaRegex.find()) {
            val normalizedNumStr = decimalJutaRegex.group(1)!!.replace(",", ".")
            val num = normalizedNumStr.toDoubleOrNull()
            if (num != null) return (num * 1000000.0).toLong()
        }

        // --- STEP 8: Thousands with Suffix (e.g. "500 ribu", "50rb", "50k", "50 k", "25ribu") ---
        val thousandsRegex = Pattern.compile("(\\d+)\\s*(?:ribu|rb|k)\\b", Pattern.CASE_INSENSITIVE).matcher(clean)
        if (thousandsRegex.find()) {
            val num = thousandsRegex.group(1)?.toLongOrNull()
            if (num != null && num > 0) return num * 1000L
        }

        // --- STEP 9: Millions with Suffix (e.g. "5 juta", "5jt") ---
        val jutaRegex = Pattern.compile("(\\d+)\\s*(?:juta|jt)\\b", Pattern.CASE_INSENSITIVE).matcher(clean)
        if (jutaRegex.find()) {
            val num = jutaRegex.group(1)?.toLongOrNull()
            if (num != null && num > 0) return num * 1000000L
        }

        // --- STEP 10: Standalone Direct Digits >= 1000 (e.g. "50000", "25000", "2000", "12500") ---
        val directDigits = Pattern.compile("\\b(\\d{4,10})\\b").matcher(clean)
        if (directDigits.find()) {
            val num = directDigits.group(1)?.toLongOrNull()
            if (num != null && num > 0) return num
        }

        // --- STEP 11: Spelled-Out Indonesian Numbers (e.g. "dua puluh lima ribu", "tiga ratus lima puluh ribu") ---
        val wordAmount = parseSpelledOutIndonesian(clean)
        if (wordAmount != null && wordAmount >= 1000L) {
            return wordAmount
        }

        // --- STEP 12: Colloquial Indonesian Shortened Numbers (Users saying 10..999 without "ribu") ---
        // Examples: "beli kopi 25 pakai gopay", "makan siang 35 pakai bca", "beli bensin 50 pakai cash",
        //           "beli pulsa 100 pakai mandiri", "token 200 pakai bca", "seharga 35", "habis 50"

        // 12.1 Parkir small amounts (e.g. "parkir 2", "parkir 3", "parkir 5 pakai cash") -> 2000, 3000, 5000
        if (clean.contains("parkir")) {
            val parkirRegex = Pattern.compile("(?:parkir.*?|bayar\\s+)?\\b([1-9])\\b(?:\\s+(?:pakai|pake|cash|tunai))?", Pattern.CASE_INSENSITIVE).matcher(clean)
            if (parkirRegex.find()) {
                val pNum = parkirRegex.group(1)?.toLongOrNull()
                if (pNum != null) return pNum * 1000L
            }
        }

        // 12.2 Digits 10..999 preceded by price/spending cues: "seharga 35", "sebesar 50", "senilai 100", "habis 45", "total 60", "kena 25", "bayar 30"
        val precededCueRegex = Pattern.compile("(?:seharga|sebesar|senilai|habis|total|totalnya|kena|bayar|beli|makan|minum|kopi|bensin|pulsa|token|kuota|jajan|belanja|ongkos|tarif)\\s+(?:sekitar|kira-kira|cuma|hanya)?\\s*(\\d{2,3})\\b", Pattern.CASE_INSENSITIVE).matcher(clean)
        if (precededCueRegex.find()) {
            val num = precededCueRegex.group(1)?.toLongOrNull()
            if (num != null && num in 10..999) return num * 1000L
        }

        // 12.3 Digits 10..999 followed by payment/asset prepositions: "25 pakai gopay", "50 dari bca", "35 lewat mandiri", "100 via bca", "20 ke dana"
        val followedPrepRegex = Pattern.compile("\\b(\\d{2,3})\\s+(?:pakai|pake|dari|lewat|via|ke|tujuan|buat|untuk|di)\\b", Pattern.CASE_INSENSITIVE).matcher(clean)
        if (followedPrepRegex.find()) {
            val num = followedPrepRegex.group(1)?.toLongOrNull()
            if (num != null && num in 10..999) return num * 1000L
        }

        // 12.4 Digits 10..999 at the end of spending context: "kopi 20", "nasi padang 25", "isi bensin 50"
        val spendingEndRegex = Pattern.compile("(?:beli|makan|minum|kopi|bensin|pulsa|token|kuota|jajan)\\s+.*?(\\d{2,3})\\s*$", Pattern.CASE_INSENSITIVE).matcher(clean)
        if (spendingEndRegex.find()) {
            val num = spendingEndRegex.group(1)?.toLongOrNull()
            if (num != null && num in 10..999) return num * 1000L
        }

        // 12.5 If spelled-out Indonesian produced 10..999 (e.g. "dua puluh lima" -> 25) in spending context:
        if (wordAmount != null && wordAmount in 10..999) {
            val hasSpendingContext = listOf("beli", "makan", "minum", "kopi", "bensin", "pulsa", "token", "jajan", "bayar", "seharga", "sebesar", "habis", "pakai", "pake", "dari").any { clean.contains(it) }
            if (hasSpendingContext) {
                return wordAmount * 1000L
            }
        }

        return null
    }

    private fun wordToDigit(w: String): Long? {
        return when (w.lowercase()) {
            "nol" -> 0L
            "satu", "se" -> 1L
            "dua" -> 2L
            "tiga" -> 3L
            "empat" -> 4L
            "lima" -> 5L
            "enam" -> 6L
            "tujuh" -> 7L
            "delapan" -> 8L
            "sembilan" -> 9L
            else -> w.toLongOrNull()
        }
    }

    private fun parseSpelledOutIndonesian(text: String): Long? {
        val singleDigits = mapOf(
            "nol" to 0L, "satu" to 1L, "se" to 1L, "dua" to 2L, "tiga" to 3L, "empat" to 4L,
            "lima" to 5L, "enam" to 6L, "tujuh" to 7L, "delapan" to 8L, "sembilan" to 9L
        )

        var total = 0L
        var subtotal = 0L
        var temp = 0L
        var foundNumber = false

        val tokens = text.split(" ", "-", ",")

        for (token in tokens) {
            val t = token.trim()
            if (t.isEmpty()) continue

            when {
                t in singleDigits -> {
                    foundNumber = true
                    temp = singleDigits[t]!!
                }
                t == "sepuluh" -> {
                    foundNumber = true
                    subtotal += 10L
                    temp = 0L
                }
                t == "sebelas" -> {
                    foundNumber = true
                    subtotal += 11L
                    temp = 0L
                }
                t == "seratus" -> {
                    foundNumber = true
                    subtotal += 100L
                    temp = 0L
                }
                t == "seribu" -> {
                    foundNumber = true
                    total += 1000L
                    temp = 0L
                    subtotal = 0L
                }
                t == "sejuta" -> {
                    foundNumber = true
                    total += 1000000L
                    temp = 0L
                    subtotal = 0L
                }
                t == "belas" -> {
                    foundNumber = true
                    subtotal += temp + 10L
                    temp = 0L
                }
                t == "puluh" -> {
                    foundNumber = true
                    subtotal += (if (temp == 0L) 1L else temp) * 10L
                    temp = 0L
                }
                t == "ratus" -> {
                    foundNumber = true
                    subtotal += (if (temp == 0L) 1L else temp) * 100L
                    temp = 0L
                }
                t == "ribu" || t == "rb" -> {
                    foundNumber = true
                    subtotal += temp
                    total += (if (subtotal == 0L) 1L else subtotal) * 1000L
                    subtotal = 0L
                    temp = 0L
                }
                t == "juta" || t == "jt" -> {
                    foundNumber = true
                    subtotal += temp
                    total += (if (subtotal == 0L) 1L else subtotal) * 1000000L
                    subtotal = 0L
                    temp = 0L
                }
                t.toLongOrNull() != null -> {
                    foundNumber = true
                    temp = t.toLong()
                }
                else -> {
                    // Non-number word: if we haven't locked in subtotal or total yet, reset stray temp
                    if (total == 0L && subtotal == 0L) {
                        temp = 0L
                    }
                }
            }
        }
        subtotal += temp
        total += subtotal

        return if (foundNumber && total > 0) total else null
    }

    private fun parseDate(text: String): LocalDate {
        val today = LocalDate.now()

        // 1. Relative day offsets
        when {
            text.contains("tiga hari lalu") || text.contains("3 hari lalu") -> return today.minusDays(3)
            text.contains("kemarin lusa") || text.contains("dua hari lalu") || text.contains("2 hari lalu") -> return today.minusDays(2)
            text.contains("kemarin") || text.contains("semalam") -> return today.minusDays(1)
            text.contains("hari ini") || text.contains("barusan") || text.contains("tadi") -> return today
        }

        // 2. Named days of the week: "senin", "selasa", "rabu", "kamis", "jumat", "sabtu", "minggu"
        val dayOfWeekMap = mapOf(
            "senin" to DayOfWeek.MONDAY,
            "selasa" to DayOfWeek.TUESDAY,
            "rabu" to DayOfWeek.WEDNESDAY,
            "kamis" to DayOfWeek.THURSDAY,
            "jumat" to DayOfWeek.FRIDAY,
            "sabtu" to DayOfWeek.SATURDAY,
            "minggu" to DayOfWeek.SUNDAY
        )
        for ((name, dow) in dayOfWeekMap) {
            if (Pattern.compile("\\b$name\\b", Pattern.CASE_INSENSITIVE).matcher(text).find()) {
                val past = today.with(TemporalAdjusters.previousOrSame(dow))
                return past
            }
        }

        // 3. Named month matching: e.g. "5 oktober", "12 agustus"
        val monthMap = mapOf(
            "januari" to 1, "februari" to 2, "maret" to 3, "april" to 4, "mei" to 5, "juni" to 6,
            "juli" to 7, "agustus" to 8, "september" to 9, "oktober" to 10, "november" to 11, "desember" to 12
        )
        for ((mName, mNum) in monthMap) {
            val dateMonthRegex = Pattern.compile("(?:tanggal|tgl)?\\s*(\\d{1,2})\\s+$mName(?:\\s+(\\d{4}))?", Pattern.CASE_INSENSITIVE).matcher(text)
            if (dateMonthRegex.find()) {
                val day = dateMonthRegex.group(1)?.toIntOrNull() ?: 1
                val year = dateMonthRegex.group(2)?.toIntOrNull() ?: today.year
                try {
                    return LocalDate.of(year, mNum, day)
                } catch (e: Exception) {
                    // ignore invalid date overflow
                }
            }
        }

        // 4. "tanggal 5" / "tgl 5"
        val tanggalRegex = Pattern.compile("(?:tanggal|tgl)\\s+(\\d{1,2})\\b", Pattern.CASE_INSENSITIVE).matcher(text)
        if (tanggalRegex.find()) {
            val day = tanggalRegex.group(1)?.toIntOrNull()
            if (day != null && day in 1..31) {
                try {
                    return today.withDayOfMonth(day)
                } catch (e: Exception) {
                    // ignore
                }
            }
        }

        return today
    }

    private data class AssetResolution(
        val sourceAsset: Asset?,
        val destinationAsset: Asset?,
        val assetMismatch: String?,
        val isAssetMissing: Boolean
    )

    private fun resolveAssets(
        normalizedText: String,
        type: TransactionType,
        availableAssets: List<Asset>,
        defaultAsset: Asset?
    ): AssetResolution {
        val knownAssetKeywords = listOf(
            "bca", "mandiri", "bri", "bni", "cimb", "bsi", "jago", "jenius", "seabank",
            "gopay", "ovo", "dana", "shopeepay", "spay", "linkaja", "cash", "tunai",
            "tabungan", "kartu kredit", "rekening", "livin", "brimo"
        )

        fun findAssetByToken(token: String): Asset? {
            val t = token.lowercase(Locale.ROOT)
            return availableAssets.firstOrNull { asset ->
                val name = asset.name.lowercase(Locale.ROOT)
                val typeName = asset.type.lowercase(Locale.ROOT)

                t.contains(name) ||
                        (name == "bca" && (t.contains("bca") || t.contains("klikbca") || t.contains("mbca"))) ||
                        (name == "mandiri" && (t.contains("mandiri") || t.contains("livin"))) ||
                        (name == "bri" && (t.contains("bri") || t.contains("brimo"))) ||
                        (name == "gopay" && (t.contains("gopay") || t.contains("gojek"))) ||
                        (name == "shopeepay" && (t.contains("shopeepay") || t.contains("spay"))) ||
                        ((name == "cash" || typeName == "tunai") && (t.contains("cash") || t.contains("tunai") || t.contains("dompet") || t.contains("kontan"))) ||
                        (name == "tabungan" && (t.contains("tabungan") || t.contains("simpanan") || t.contains("celengan")))
            }
        }

        // SPECIAL CASE 1: "Tarik Tunai" / "Ambil Cash di ATM" -> Transfer from Bank to Cash
        if (normalizedText.contains("tarik tunai") || normalizedText.contains("ambil cash") || normalizedText.contains("ambil uang di atm")) {
            val cashAsset = availableAssets.firstOrNull { it.name.equals("Cash", true) || it.type.equals("Tunai", true) }
            val bankAsset = availableAssets.firstOrNull { asset ->
                val name = asset.name.lowercase(Locale.ROOT)
                name != "cash" && asset.type.lowercase() != "tunai" && normalizedText.contains(name)
            } ?: defaultAsset

            return AssetResolution(
                sourceAsset = bankAsset,
                destinationAsset = cashAsset,
                assetMismatch = null,
                isAssetMissing = bankAsset == null || cashAsset == null
            )
        }

        // SPECIAL CASE 2: "Setor Tunai" -> Transfer from Cash to Bank
        if (normalizedText.contains("setor tunai")) {
            val cashAsset = availableAssets.firstOrNull { it.name.equals("Cash", true) || it.type.equals("Tunai", true) }
            val bankAsset = availableAssets.firstOrNull { asset ->
                val name = asset.name.lowercase(Locale.ROOT)
                name != "cash" && asset.type.lowercase() != "tunai" && normalizedText.contains(name)
            } ?: defaultAsset

            return AssetResolution(
                sourceAsset = cashAsset,
                destinationAsset = bankAsset,
                assetMismatch = null,
                isAssetMissing = bankAsset == null || cashAsset == null
            )
        }

        // SPECIAL CASE 3: "Top Up [EWALLET] ... dari/pakai [BANK]" -> Transfer from Bank to EWALLET
        if (normalizedText.contains("top up") || normalizedText.contains("isi saldo")) {
            // Find destination (e-wallet / recipient)
            val destMatch = Pattern.compile("(?:top up|isi saldo)\\s+([a-zA-Z0-9]+)", Pattern.CASE_INSENSITIVE).matcher(normalizedText)
            val targetToken = if (destMatch.find()) destMatch.group(1)?.trim() else ""
            var dest = findAssetByToken(targetToken ?: "")
            if (dest == null) {
                // Fallback: search availableAssets for any e-wallet or non-cash asset mentioned in text
                dest = availableAssets.firstOrNull { asset ->
                    val n = asset.name.lowercase(Locale.ROOT)
                    normalizedText.contains(n) && n != "cash" && !asset.type.equals("Tunai", true)
                }
            }

            // Find source (bank or default)
            val sourceMatch = Pattern.compile("(?:dari|pakai|lewat|via)\\s+([a-zA-Z0-9]+)", Pattern.CASE_INSENSITIVE).matcher(normalizedText)
            val sourceToken = if (sourceMatch.find()) sourceMatch.group(1)?.trim() else ""
            var source = findAssetByToken(sourceToken ?: "")
            if (source == null) {
                // Look for bank mentioned in text that is not the dest
                source = availableAssets.firstOrNull { asset ->
                    asset.id != dest?.id && normalizedText.contains(asset.name.lowercase(Locale.ROOT))
                } ?: defaultAsset
            }

            return AssetResolution(
                sourceAsset = source,
                destinationAsset = dest,
                assetMismatch = if (dest == null && !targetToken.isNullOrBlank()) targetToken.uppercase() else null,
                isAssetMissing = source == null && defaultAsset == null
            )
        }

        // GENERAL TRANSFER: "... dari [ASET_A] ke [ASET_B]"
        if (type == TransactionType.TRANSFER) {
            val dariKeRegex = Pattern.compile("(?:dari|lewat)\\s+([a-zA-Z0-9\\s]+?)\\s+(?:ke|tujuan)\\s+([a-zA-Z0-9\\s]+)", Pattern.CASE_INSENSITIVE).matcher(normalizedText)
            if (dariKeRegex.find()) {
                val sourceText = dariKeRegex.group(1)!!.trim()
                val destText = dariKeRegex.group(2)!!.trim()

                val source = findAssetByToken(sourceText)
                val dest = findAssetByToken(destText)

                var mismatch: String? = null
                if (source == null) {
                    val candidate = knownAssetKeywords.firstOrNull { sourceText.contains(it) }
                    if (candidate != null) mismatch = candidate.uppercase()
                }
                if (dest == null && mismatch == null) {
                    val candidate = knownAssetKeywords.firstOrNull { destText.contains(it) }
                    if (candidate != null) mismatch = candidate.uppercase()
                }

                return AssetResolution(
                    sourceAsset = source ?: defaultAsset,
                    destinationAsset = dest,
                    assetMismatch = mismatch,
                    isAssetMissing = source == null && defaultAsset == null
                )
            }
        }

        // For Income / Expense: find if any asset is explicitly referenced
        val matched = availableAssets.firstOrNull { asset ->
            val name = asset.name.lowercase(Locale.ROOT)
            val typeName = asset.type.lowercase(Locale.ROOT)
            normalizedText.contains(name) ||
                    (name == "bca" && (normalizedText.contains("bca") || normalizedText.contains("klikbca") || normalizedText.contains("mbca"))) ||
                    (name == "mandiri" && (normalizedText.contains("mandiri") || normalizedText.contains("livin"))) ||
                    (name == "bri" && (normalizedText.contains("bri") || normalizedText.contains("brimo"))) ||
                    (name == "gopay" && (normalizedText.contains("gopay") || normalizedText.contains("gojek"))) ||
                    (name == "shopeepay" && (normalizedText.contains("shopeepay") || normalizedText.contains("spay"))) ||
                    ((name == "cash" || typeName == "tunai") && (normalizedText.contains("cash") || normalizedText.contains("tunai") || normalizedText.contains("dompet") || normalizedText.contains("kontan"))) ||
                    (name == "tabungan" && (normalizedText.contains("tabungan") || normalizedText.contains("simpanan") || normalizedText.contains("celengan")))
        }

        if (matched != null) {
            return AssetResolution(
                sourceAsset = matched,
                destinationAsset = null,
                assetMismatch = null,
                isAssetMissing = false
            )
        }

        // Check if an unowned asset was mentioned (e.g. CIMB, OVO, ShopeePay)
        val mentionedUnknown = knownAssetKeywords.firstOrNull { normalizedText.contains(it) }
        if (mentionedUnknown != null) {
            return AssetResolution(
                sourceAsset = null,
                destinationAsset = null,
                assetMismatch = mentionedUnknown.uppercase(),
                isAssetMissing = false
            )
        }

        // No asset mentioned at all: user relies on default suggestion
        return AssetResolution(
            sourceAsset = null,
            destinationAsset = null,
            assetMismatch = null,
            isAssetMissing = true
        )
    }

    private fun detectCategory(
        text: String,
        type: TransactionType,
        categories: List<Category>
    ): Category? {
        val activeCategories = categories.filter { it.isActive }

        if (type == TransactionType.INCOME) {
            val incomeCategories = activeCategories.filter { it.type == "income" }
            return when {
                // Bonus & Rewards (prioritized before broad payroll terms)
                text.contains("bonus") || text.contains("reward") || text.contains("insentif") ||
                        text.contains("uang lembur") || text.contains("lemburan") || text.contains("tip") ->
                    incomeCategories.firstOrNull { it.name.contains("Bonus", true) }

                // Gaji / Regular Payroll
                text.contains("gaji") || text.contains("payroll") || text.contains("tunjangan") ||
                        text.contains("upah") || text.contains("honor") || text.contains("thr") ||
                        text.contains("sangu") || text.contains("uang saku") || text.contains("jatah bulanan") ||
                        text.contains("kiriman ortu") ->
                    incomeCategories.firstOrNull { it.name.contains("Gaji", true) }

                // Freelance / Side Projects
                text.contains("freelance") || text.contains("proyek") || text.contains("project") ||
                        text.contains("side hustle") || text.contains("klien") || text.contains("client") ||
                        text.contains("desain") || text.contains("coding") || text.contains("jasa") ||
                        text.contains("komisi") || text.contains("fee") ->
                    incomeCategories.firstOrNull { it.name.contains("Freelance", true) }

                // Hadiah / Giveaway
                text.contains("hadiah") || text.contains("angpao") || text.contains("giveaway") ||
                        text.contains("saweran") || text.contains("menang") || text.contains("doorprize") ||
                        text.contains("undian") ->
                    incomeCategories.firstOrNull { it.name.contains("Hadiah", true) }

                // Penjualan / Bisnis
                text.contains("jual") || text.contains("penjualan") || text.contains("laku") ||
                        text.contains("omset") || text.contains("omzet") || text.contains("dagangan") ||
                        text.contains("orderan") || text.contains("toko laku") || text.contains("closing") ||
                        text.contains("preorder") || text.contains("po") ->
                    incomeCategories.firstOrNull { it.name.contains("Penjualan", true) }

                // Investasi & Passive Income
                text.contains("dividen") || text.contains("bagi hasil") || text.contains("profit sharing") ||
                        text.contains("bunga") || text.contains("imbal hasil") || text.contains("reksa dana") ||
                        text.contains("saham") || text.contains("kripto") || text.contains("crypto") ||
                        text.contains("deposito") ->
                    incomeCategories.firstOrNull { it.name.contains("Investasi", true) } ?: incomeCategories.firstOrNull { it.name.contains("Lainnya", true) }

                else -> incomeCategories.firstOrNull { it.name.contains("Lainnya", true) } ?: incomeCategories.firstOrNull()
            }
        }

        if (type == TransactionType.EXPENSE) {
            val expenseCategories = activeCategories.filter { it.type == "expense" }
            return when {
                // Makanan & Minuman
                text.contains("kopi") || text.contains("ngopi") || text.contains("makan") || text.contains("minum") ||
                        text.contains("sarapan") || text.contains("maksi") || text.contains("dinner") ||
                        text.contains("bakso") || text.contains("soto") || text.contains("sate") ||
                        text.contains("ayam") || text.contains("bebek") || text.contains("seblak") ||
                        text.contains("martabak") || text.contains("pizza") || text.contains("burger") ||
                        text.contains("boba") || text.contains("es teh") || text.contains("jus") ||
                        text.contains("resto") || text.contains("restoran") || text.contains("kafe") ||
                        text.contains("cafe") || text.contains("warung") || text.contains("warteg") ||
                        text.contains("nasi") || text.contains("nasgor") || text.contains("mie") ||
                        text.contains("indomie") || text.contains("snack") || text.contains("cemilan") ||
                        text.contains("gofood") || text.contains("grabfood") || text.contains("shopeefood") ||
                        text.contains("mcd") || text.contains("kfc") || text.contains("starbucks") ||
                        text.contains("roti") || text.contains("dimsum") || text.contains("steak") ||
                        text.contains("ramen") || text.contains("sushi") || text.contains("bubur") ||
                        text.contains("ketoprak") || text.contains("pecel") || text.contains("padang") ||
                        text.contains("gulai") || text.contains("rendang") || text.contains("rawon") ||
                        text.contains("tongseng") || text.contains("batagor") || text.contains("siomay") ||
                        text.contains("gorengan") || text.contains("cilok") || text.contains("pempek") ||
                        text.contains("pecel lele") || text.contains("seafood") || text.contains("geprek") ||
                        text.contains("kue") || text.contains("donat") || text.contains("chatime") ||
                        text.contains("mixue") || text.contains("janji jiwa") || text.contains("kopi kenangan") ->
                    expenseCategories.firstOrNull { it.name.contains("Makanan", true) }

                // Transportasi
                text.contains("bensin") || text.contains("pertamax") || text.contains("pertalite") ||
                        text.contains("solar") || text.contains("shell") || text.contains("spbu") ||
                        text.contains("parkir") || text.contains("tol") || text.contains("etoll") ||
                        text.contains("e-toll") || text.contains("ojol") || text.contains("ojek") ||
                        text.contains("gojek") || text.contains("goride") || text.contains("gocar") ||
                        text.contains("grab") || text.contains("grabcar") || text.contains("grabbike") ||
                        text.contains("maxim") || text.contains("indrive") || text.contains("taksi") ||
                        text.contains("bluebird") || text.contains("angkot") || text.contains("busway") ||
                        text.contains("transjakarta") || text.contains("krl") || text.contains("commuterline") ||
                        text.contains("mrt") || text.contains("lrt") || text.contains("kereta") ||
                        text.contains("whoosh") || text.contains("pesawat") || text.contains("bengkel") ||
                        text.contains("servis motor") || text.contains("servis mobil") || text.contains("cuci motor") ||
                        text.contains("cuci mobil") || text.contains("oli") || text.contains("ganti oli") ||
                        text.contains("ongkos") || text.contains("tambal ban") ->
                    expenseCategories.firstOrNull { it.name.contains("Transportasi", true) }

                // Tagihan & Utilitas
                text.contains("listrik") || text.contains("token listrik") || text.contains("pln") ||
                        text.contains("air") || text.contains("pdam") || text.contains("wifi") ||
                        text.contains("indihome") || text.contains("biznet") || text.contains("first media") ||
                        text.contains("internet") || text.contains("pulsa") || text.contains("paket data") ||
                        text.contains("kuota") || text.contains("bpjs") || text.contains("asuransi") ||
                        text.contains("pajak") || text.contains("pbb") || text.contains("kontrakan") ||
                        text.contains("sewa kos") || text.contains("kosan") || text.contains("uang kos") ||
                        text.contains("cicilan") || text.contains("kpr") || text.contains("tagihan") ||
                        text.contains("iuran") || text.contains("ipl") || text.contains("paylater") ||
                        text.contains("spaylater") || text.contains("kredivo") || text.contains("akulaku") ->
                    expenseCategories.firstOrNull { it.name.contains("Tagihan", true) }

                // Belanja & Shopping
                text.contains("belanja") || text.contains("shopping") || text.contains("supermarket") ||
                        text.contains("minimarket") || text.contains("indomaret") || text.contains("alfamart") ||
                        text.contains("alfamidi") || text.contains("superindo") || text.contains("hypermart") ||
                        text.contains("mall") || text.contains("pasar") || text.contains("tokopedia") ||
                        text.contains("shopee") || text.contains("lazada") || text.contains("tiktok shop") ||
                        text.contains("baju") || text.contains("celana") || text.contains("sepatu") ||
                        text.contains("tas") || text.contains("skincare") || text.contains("makeup") ||
                        text.contains("sabun") || text.contains("shampo") || text.contains("parfum") ||
                        text.contains("belanja bulanan") || text.contains("sayur") || text.contains("buah") ||
                        text.contains("beras") || text.contains("minyak") || text.contains("telur") ||
                        text.contains("potong rambut") || text.contains("barbershop") || text.contains("salon") ->
                    expenseCategories.firstOrNull { it.name.contains("Belanja", true) }

                // Hiburan & Rekreasi
                text.contains("bioskop") || text.contains("nonton") || text.contains("film") ||
                        text.contains("cinema") || text.contains("xxi") || text.contains("cgv") ||
                        text.contains("cinepolis") || text.contains("game") || text.contains("top up game") ||
                        text.contains("steam") || text.contains("playstation") || text.contains("mobile legends") ||
                        text.contains("free fire") || text.contains("pubg") || text.contains("netflix") ||
                        text.contains("spotify") || text.contains("youtube premium") || text.contains("disney") ||
                        text.contains("karaoke") || text.contains("liburan") || text.contains("wisata") ||
                        text.contains("staycation") || text.contains("hotel") || text.contains("villa") ||
                        text.contains("jalan-jalan") || text.contains("rekreasi") || text.contains("hiburan") ||
                        text.contains("konser") ->
                    expenseCategories.firstOrNull { it.name.contains("Hiburan", true) }

                // Kesehatan & Medis
                text.contains("obat") || text.contains("apotek") || text.contains("apotik") ||
                        text.contains("kimia farma") || text.contains("k24") || text.contains("century") ||
                        text.contains("guardian") || text.contains("watsons") || text.contains("dokter") ||
                        text.contains("klinik") || text.contains("rumah sakit") || text.contains("puskesmas") ||
                        text.contains("vitamin") || text.contains("suplemen") || text.contains("periksa") ||
                        text.contains("gigi") || text.contains("rawat") || text.contains("kesehatan") ||
                        text.contains("panadol") || text.contains("paracetamol") || text.contains("tolak angin") ||
                        text.contains("optik") || text.contains("kacamata") ->
                    expenseCategories.firstOrNull { it.name.contains("Kesehatan", true) }

                // Pendidikan & Pengembangan Diri
                text.contains("buku") || text.contains("kursus") || text.contains("les") ||
                        text.contains("bimbel") || text.contains("sekolah") || text.contains("kuliah") ||
                        text.contains("spp") || text.contains("ukt") || text.contains("wisuda") ||
                        text.contains("skripsi") || text.contains("pelatihan") || text.contains("webinar") ||
                        text.contains("seminar") || text.contains("pendidikan") || text.contains("fotokopi") ||
                        text.contains("print") || text.contains("atk") ->
                    expenseCategories.firstOrNull { it.name.contains("Pendidikan", true) }

                // Kebutuhan Rumah
                text.contains("perabot") || text.contains("alat dapur") || text.contains("sapu") ||
                        text.contains("pel") || text.contains("deterjen") || text.contains("gas") ||
                        text.contains("elpiji") || text.contains("galon") || text.contains("aqua galon") ||
                        text.contains("renovasi") || text.contains("cat rumah") || text.contains("lampu") ||
                        text.contains("rumah") || text.contains("kebersihan") || text.contains("kasur") ||
                        text.contains("sprei") ->
                    expenseCategories.firstOrNull { it.name.contains("Kebutuhan rumah", true) }

                // Donasi & Sosial
                text.contains("infaq") || text.contains("sedekah") || text.contains("zakat") ||
                        text.contains("donasi") || text.contains("sumbangan") || text.contains("kotak amal") ||
                        text.contains("wakaf") || text.contains("kolekte") || text.contains("persepuluhan") ||
                        text.contains("kondangan") || text.contains("kurban") || text.contains("aqiqah") ->
                    expenseCategories.firstOrNull { it.name.contains("Sosial", true) } ?: expenseCategories.firstOrNull { it.name.contains("Lainnya", true) }

                else -> expenseCategories.firstOrNull { it.name.contains("Lainnya", true) } ?: expenseCategories.firstOrNull()
            }
        }

        return null
    }

    private fun extractDescription(
        originalText: String,
        type: TransactionType,
        category: Category?,
        sourceAsset: Asset?,
        destAsset: Asset?
    ): String {
        var desc = originalText.trim()

        // Transfer naming
        if (type == TransactionType.TRANSFER) {
            val lower = desc.lowercase()
            return when {
                lower.contains("tarik tunai") -> "Tarik Tunai ${sourceAsset?.name ?: ""}".trim()
                lower.contains("setor tunai") -> "Setor Tunai ${destAsset?.name ?: ""}".trim()
                lower.contains("top up") || lower.contains("isi saldo") -> "Top Up ${destAsset?.name ?: "E-wallet"}"
                sourceAsset != null && destAsset != null -> "Transfer ${sourceAsset.name} ke ${destAsset.name}"
                else -> "Transfer Antar Aset"
            }
        }

        // Clean trailing prepositions, amounts, currency cues, and dates
        val cleanPatterns = listOf(
            "(?i)\\s*(?:seharga|sebesar|senilai|habis|total|totalnya|kena|sejumlah)\\s+.*$",
            "(?i)\\s*(?:pakai|pake|dari|ke|lewat|via|dengan|di)\\s+.*$",
            "(?i)\\s*(?:rp\\.?|idr)?\\s*\\d+(?:[.,]\\d+)*\\s*(?:ribu|rb|k|juta|jt|miliar|rupiah|perak)?.*$",
            "(?i)\\s*\\b(?:satu|dua|tiga|empat|lima|enam|tujuh|delapan|sembilan|sepuluh|sebelas|seratus|seribu|sejuta|semiliar|setengah|seperempat|belas|puluh|ratus|ribu|rb|juta|jt|goban|noban|ceban|goceng|noceng|seceng|pego|gopek|cepek)\\b(?:\\s+\\b(?:satu|dua|tiga|empat|lima|enam|tujuh|delapan|sembilan|sepuluh|sebelas|seratus|seribu|sejuta|semiliar|setengah|seperempat|belas|puluh|ratus|ribu|rb|juta|jt|goban|noban|ceban|goceng|noceng|seceng|pego|gopek|cepek)\\b)*.*$",
            "(?i)^(?:kemarin\\s+lusa\\s+|kemarin\\s+|kemaren\\s+|hari\\s+ini\\s+|tadi\\s+pagi\\s+|tadi\\s+siang\\s+|tadi\\s+sore\\s+|tadi\\s+malam\\s+|tadi\\s+|barusan\\s+)"
        )

        for (pattern in cleanPatterns) {
            desc = desc.replace(Regex(pattern), "").trim()
        }

        // Capitalize first letter
        desc = desc.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }

        if (desc.isBlank()) {
            return category?.name ?: (if (type == TransactionType.INCOME) "Pemasukan" else "Pengeluaran")
        }

        return desc
    }
}
