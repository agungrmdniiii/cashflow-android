package com.cashflow.app

import com.cashflow.app.data.model.Asset
import com.cashflow.app.data.model.Category
import com.cashflow.app.data.model.TransactionType
import com.cashflow.app.voice.VoiceTransactionParser
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class VoiceTransactionParserTest {

    private lateinit var mockAssets: List<Asset>
    private lateinit var mockCategories: List<Category>
    private lateinit var defaultAsset: Asset

    @Before
    fun setUp() {
        mockAssets = listOf(
            Asset("bca", "BCA", "Bank", 4500000L, 4500000L, isDefault = true),
            Asset("mandiri", "Mandiri", "Bank", 1200000L, 1200000L),
            Asset("gopay", "GoPay", "E-wallet", 325000L, 325000L),
            Asset("cash", "Cash", "Tunai", 750000L, 750000L),
            Asset("tabungan", "Tabungan", "Tabungan", 10000000L, 10000000L),
            Asset("cc", "Kartu Kredit", "Kartu kredit", 0L, 0L)
        )
        defaultAsset = mockAssets.first { it.isDefault }

        mockCategories = listOf(
            Category("cat_makanan", "Makanan", "expense"),
            Category("cat_trans", "Transportasi", "expense"),
            Category("cat_tagihan", "Tagihan", "expense"),
            Category("cat_belanja", "Belanja", "expense"),
            Category("cat_hiburan", "Hiburan", "expense"),
            Category("cat_kesehatan", "Kesehatan", "expense"),
            Category("cat_pendidikan", "Pendidikan", "expense"),
            Category("cat_rumah", "Kebutuhan rumah", "expense"),
            Category("cat_gaji", "Gaji", "income"),
            Category("cat_freelance", "Freelance", "income"),
            Category("cat_bonus", "Bonus", "income"),
            Category("cat_penjualan", "Penjualan", "income")
        )
    }

    @Test
    fun testNominalParsingStandard() {
        assertEquals(10000L, VoiceTransactionParser.parseNominal("10 ribu"))
        assertEquals(10000L, VoiceTransactionParser.parseNominal("10k"))
        assertEquals(10000L, VoiceTransactionParser.parseNominal("10 K"))
        assertEquals(10000L, VoiceTransactionParser.parseNominal("sepuluh ribu"))
        assertEquals(50000L, VoiceTransactionParser.parseNominal("50k"))
        assertEquals(1000000L, VoiceTransactionParser.parseNominal("1 juta"))
        assertEquals(1000000L, VoiceTransactionParser.parseNominal("1 jt"))
        assertEquals(1000000L, VoiceTransactionParser.parseNominal("satu juta"))
        assertEquals(1500000L, VoiceTransactionParser.parseNominal("1,5 juta"))
        assertEquals(1500000L, VoiceTransactionParser.parseNominal("satu setengah juta"))
        assertEquals(2500000L, VoiceTransactionParser.parseNominal("2 juta 500 ribu"))
        assertEquals(500000L, VoiceTransactionParser.parseNominal("setengah juta"))
        assertEquals(750000L, VoiceTransactionParser.parseNominal("750 ribu"))
    }

    @Test
    fun testNominalParsingSlangAndCompounds() {
        // Slang numbers (ceban, goban, goceng, seceng, pego, etc.)
        assertEquals(10000L, VoiceTransactionParser.parseNominal("ceban"))
        assertEquals(50000L, VoiceTransactionParser.parseNominal("goban"))
        assertEquals(5000L, VoiceTransactionParser.parseNominal("goceng"))
        assertEquals(1000L, VoiceTransactionParser.parseNominal("seceng"))
        assertEquals(150000L, VoiceTransactionParser.parseNominal("pego"))
        assertEquals(250000L, VoiceTransactionParser.parseNominal("seperempat juta"))
        assertEquals(2500000L, VoiceTransactionParser.parseNominal("dua koma lima juta"))
        assertEquals(350000L, VoiceTransactionParser.parseNominal("tiga ratus lima puluh ribu"))
        assertEquals(25000L, VoiceTransactionParser.parseNominal("dua puluh lima ribu"))
        assertEquals(150000L, VoiceTransactionParser.parseNominal("seratus lima puluh ribu"))
    }

    @Test
    fun testExample1_BeliKopiGopay() {
        val result = VoiceTransactionParser.parse(
            spokenText = "Beli kopi dua puluh ribu pakai GoPay",
            availableAssets = mockAssets,
            availableCategories = mockCategories,
            defaultAsset = defaultAsset
        )

        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals(20000L, result.amount)
        assertEquals("Beli kopi", result.description)
        assertEquals("Makanan", result.detectedCategory?.name)
        assertEquals("GoPay", result.matchedAsset?.name)
        assertFalse(result.isAmountMissing)
        assertFalse(result.isAssetMissing)
    }

    @Test
    fun testExample2_GajiBCA() {
        val result = VoiceTransactionParser.parse(
            spokenText = "Gaji masuk lima juta ke BCA",
            availableAssets = mockAssets,
            availableCategories = mockCategories,
            defaultAsset = defaultAsset
        )

        assertEquals(TransactionType.INCOME, result.type)
        assertEquals(5000000L, result.amount)
        assertEquals("Gaji", result.detectedCategory?.name)
        assertEquals("BCA", result.matchedAsset?.name)
    }

    @Test
    fun testExample3_TransferBCATabungan() {
        val result = VoiceTransactionParser.parse(
            spokenText = "Transfer satu juta dari BCA ke tabungan",
            availableAssets = mockAssets,
            availableCategories = mockCategories,
            defaultAsset = defaultAsset
        )

        assertEquals(TransactionType.TRANSFER, result.type)
        assertEquals(1000000L, result.amount)
        assertEquals("BCA", result.matchedAsset?.name)
        assertEquals("Tabungan", result.destinationAsset?.name)
    }

    @Test
    fun testExample4_BayarBensinBCA() {
        val result = VoiceTransactionParser.parse(
            spokenText = "Bayar bensin 50 ribu dari BCA",
            availableAssets = mockAssets,
            availableCategories = mockCategories,
            defaultAsset = defaultAsset
        )

        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals(50000L, result.amount)
        assertEquals("Transportasi", result.detectedCategory?.name)
        assertEquals("BCA", result.matchedAsset?.name)
    }

    @Test
    fun testExample5_FreelanceMandiri() {
        val result = VoiceTransactionParser.parse(
            spokenText = "Dapat freelance 750 ribu masuk Mandiri",
            availableAssets = mockAssets,
            availableCategories = mockCategories,
            defaultAsset = defaultAsset
        )

        assertEquals(TransactionType.INCOME, result.type)
        assertEquals(750000L, result.amount)
        assertEquals("Freelance", result.detectedCategory?.name)
        assertEquals("Mandiri", result.matchedAsset?.name)
    }

    @Test
    fun testExample6_KemarinMakanMalamCash() {
        val result = VoiceTransactionParser.parse(
            spokenText = "Kemarin makan malam 85 ribu pakai cash",
            availableAssets = mockAssets,
            availableCategories = mockCategories,
            defaultAsset = defaultAsset
        )

        val yesterdayStr = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)

        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals(85000L, result.amount)
        assertEquals("Cash", result.matchedAsset?.name)
        assertEquals(yesterdayStr, result.transactionDate)
    }

    @Test
    fun testSpecialTransfer_TarikTunai() {
        val result = VoiceTransactionParser.parse(
            spokenText = "Tarik tunai 500 ribu dari BCA",
            availableAssets = mockAssets,
            availableCategories = mockCategories,
            defaultAsset = defaultAsset
        )

        assertEquals(TransactionType.TRANSFER, result.type)
        assertEquals(500000L, result.amount)
        assertEquals("BCA", result.matchedAsset?.name)
        assertEquals("Cash", result.destinationAsset?.name)
    }

    @Test
    fun testSpecialTransfer_TopUpGoPay() {
        val result = VoiceTransactionParser.parse(
            spokenText = "Top up GoPay 100 ribu dari BCA",
            availableAssets = mockAssets,
            availableCategories = mockCategories,
            defaultAsset = defaultAsset
        )

        assertEquals(TransactionType.TRANSFER, result.type)
        assertEquals(100000L, result.amount)
        assertEquals("BCA", result.matchedAsset?.name)
        assertEquals("GoPay", result.destinationAsset?.name)
    }

    @Test
    fun testEverydayCulinary_MaksiPadang() {
        val result = VoiceTransactionParser.parse(
            spokenText = "Maksi nasi padang 25 ribu pakai cash",
            availableAssets = mockAssets,
            availableCategories = mockCategories,
            defaultAsset = defaultAsset
        )

        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals(25000L, result.amount)
        assertEquals("Makanan", result.detectedCategory?.name)
        assertEquals("Cash", result.matchedAsset?.name)
    }

    @Test
    fun testEverydayUtility_TokenListrik() {
        val result = VoiceTransactionParser.parse(
            spokenText = "Bayar token listrik 200 ribu pakai Mandiri",
            availableAssets = mockAssets,
            availableCategories = mockCategories,
            defaultAsset = defaultAsset
        )

        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals(200000L, result.amount)
        assertEquals("Tagihan", result.detectedCategory?.name)
        assertEquals("Mandiri", result.matchedAsset?.name)
    }

    @Test
    fun testEverydayHealth_Apotek() {
        val result = VoiceTransactionParser.parse(
            spokenText = "Beli obat di apotek 75 ribu pakai BCA",
            availableAssets = mockAssets,
            availableCategories = mockCategories,
            defaultAsset = defaultAsset
        )

        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals(75000L, result.amount)
        assertEquals("Kesehatan", result.detectedCategory?.name)
        assertEquals("BCA", result.matchedAsset?.name)
    }

    @Test
    fun testIncome_BonusTHR() {
        val result = VoiceTransactionParser.parse(
            spokenText = "Dapat bonus THR 5 juta masuk BCA",
            availableAssets = mockAssets,
            availableCategories = mockCategories,
            defaultAsset = defaultAsset
        )

        assertEquals(TransactionType.INCOME, result.type)
        assertEquals(5000000L, result.amount)
        assertEquals("Bonus", result.detectedCategory?.name)
        assertEquals("BCA", result.matchedAsset?.name)
    }

    @Test
    fun testMissingAmount_RequiresUserInput() {
        val result = VoiceTransactionParser.parse(
            spokenText = "Beli makan pakai GoPay",
            availableAssets = mockAssets,
            availableCategories = mockCategories,
            defaultAsset = defaultAsset
        )

        assertTrue(result.isAmountMissing)
        assertNull(result.amount)
        assertEquals("GoPay", result.matchedAsset?.name)
    }

    @Test
    fun testDefaultAssetSuggestion_WhenAssetNotMentioned() {
        val result = VoiceTransactionParser.parse(
            spokenText = "Beli makan 30 ribu",
            availableAssets = mockAssets,
            availableCategories = mockCategories,
            defaultAsset = defaultAsset
        )

        assertEquals(30000L, result.amount)
        assertNotNull(result.suggestedDefaultAsset)
        assertEquals("BCA", result.suggestedDefaultAsset?.name)
    }

    @Test
    fun testAssetMismatch_WhenMentionedAssetDoesNotExist() {
        val result = VoiceTransactionParser.parse(
            spokenText = "Beli makan 30 ribu pakai CIMB",
            availableAssets = mockAssets,
            availableCategories = mockCategories,
            defaultAsset = defaultAsset
        )

        assertEquals("CIMB", result.assetMismatchName)
    }

    @Test
    fun testExpandedVocabulary_SlangNumbers() {
        val result1 = VoiceTransactionParser.parse(
            spokenText = "Jajan ceban pakai GoPay",
            availableAssets = mockAssets,
            availableCategories = mockCategories,
            defaultAsset = defaultAsset
        )
        assertEquals(TransactionType.EXPENSE, result1.type)
        assertEquals(10000L, result1.amount)
        assertEquals("Makanan", result1.detectedCategory?.name)
        assertEquals("GoPay", result1.matchedAsset?.name)

        val result2 = VoiceTransactionParser.parse(
            spokenText = "Beli bensin goban pakai cash",
            availableAssets = mockAssets,
            availableCategories = mockCategories,
            defaultAsset = defaultAsset
        )
        assertEquals(50000L, result2.amount)
        assertEquals("Transportasi", result2.detectedCategory?.name)
        assertEquals("Cash", result2.matchedAsset?.name)

        val result3 = VoiceTransactionParser.parse(
            spokenText = "Bayar pego pakai Mandiri",
            availableAssets = mockAssets,
            availableCategories = mockCategories,
            defaultAsset = defaultAsset
        )
        assertEquals(150000L, result3.amount)
        assertEquals("Mandiri", result3.matchedAsset?.name)
    }

    @Test
    fun testExpandedVocabulary_CulinaryAndDrinks() {
        val result1 = VoiceTransactionParser.parse(
            spokenText = "Ngopi starbucks 58 ribu pakai BCA",
            availableAssets = mockAssets,
            availableCategories = mockCategories,
            defaultAsset = defaultAsset
        )
        assertEquals(58000L, result1.amount)
        assertEquals("Makanan", result1.detectedCategory?.name)
        assertEquals("BCA", result1.matchedAsset?.name)

        val result2 = VoiceTransactionParser.parse(
            spokenText = "Pesan gofood martabak 65 ribu dari GoPay",
            availableAssets = mockAssets,
            availableCategories = mockCategories,
            defaultAsset = defaultAsset
        )
        assertEquals(65000L, result2.amount)
        assertEquals("Makanan", result2.detectedCategory?.name)
        assertEquals("GoPay", result2.matchedAsset?.name)
    }

    @Test
    fun testExpandedVocabulary_BillsAndUtilities() {
        val result1 = VoiceTransactionParser.parse(
            spokenText = "Bayar wifi indihome 385 ribu pakai BCA",
            availableAssets = mockAssets,
            availableCategories = mockCategories,
            defaultAsset = defaultAsset
        )
        assertEquals(TransactionType.EXPENSE, result1.type)
        assertEquals(385000L, result1.amount)
        assertEquals("Tagihan", result1.detectedCategory?.name)
        assertEquals("BCA", result1.matchedAsset?.name)

        val result2 = VoiceTransactionParser.parse(
            spokenText = "Bayar bpjs 150 ribu dari BCA",
            availableAssets = mockAssets,
            availableCategories = mockCategories,
            defaultAsset = defaultAsset
        )
        assertEquals(150000L, result2.amount)
        assertEquals("Tagihan", result2.detectedCategory?.name)
        assertEquals("BCA", result2.matchedAsset?.name)
    }

    @Test
    fun testExpandedVocabulary_ShoppingAndLeisure() {
        val result1 = VoiceTransactionParser.parse(
            spokenText = "Checkout shopee kemeja 120 ribu pakai BCA",
            availableAssets = mockAssets,
            availableCategories = mockCategories,
            defaultAsset = defaultAsset
        )
        assertEquals(120000L, result1.amount)
        assertEquals("Belanja", result1.detectedCategory?.name)
        assertEquals("BCA", result1.matchedAsset?.name)

        val result2 = VoiceTransactionParser.parse(
            spokenText = "Nonton bioskop xxi 50 ribu pakai BCA",
            availableAssets = mockAssets,
            availableCategories = mockCategories,
            defaultAsset = defaultAsset
        )
        assertEquals(50000L, result2.amount)
        assertEquals("Hiburan", result2.detectedCategory?.name)
        assertEquals("BCA", result2.matchedAsset?.name)
    }

    @Test
    fun testExpandedVocabulary_IncomeTypes() {
        val result1 = VoiceTransactionParser.parse(
            spokenText = "Dapat uang lembur 400 ribu masuk Mandiri",
            availableAssets = mockAssets,
            availableCategories = mockCategories,
            defaultAsset = defaultAsset
        )
        assertEquals(TransactionType.INCOME, result1.type)
        assertEquals(400000L, result1.amount)
        assertEquals("Bonus", result1.detectedCategory?.name)
        assertEquals("Mandiri", result1.matchedAsset?.name)

        val result2 = VoiceTransactionParser.parse(
            spokenText = "Closing orderan olshop 1 juta 200 ribu masuk BCA",
            availableAssets = mockAssets,
            availableCategories = mockCategories,
            defaultAsset = defaultAsset
        )
        assertEquals(TransactionType.INCOME, result2.type)
        assertEquals(1200000L, result2.amount)
        assertEquals("Penjualan", result2.detectedCategory?.name)
        assertEquals("BCA", result2.matchedAsset?.name)
    }

    @Test
    fun testExpandedVocabulary_RelativeDates() {
        val result1 = VoiceTransactionParser.parse(
            spokenText = "Kemarin lusa beli sepatu 250 ribu pakai BCA",
            availableAssets = mockAssets,
            availableCategories = mockCategories,
            defaultAsset = defaultAsset
        )
        val twoDaysAgo = LocalDate.now().minusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE)
        assertEquals(250000L, result1.amount)
        assertEquals("Belanja", result1.detectedCategory?.name)
        assertEquals(twoDaysAgo, result1.transactionDate)

        val result2 = VoiceTransactionParser.parse(
            spokenText = "Tadi pagi sarapan bubur ayam 15 ribu pakai cash",
            availableAssets = mockAssets,
            availableCategories = mockCategories,
            defaultAsset = defaultAsset
        )
        val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        assertEquals(15000L, result2.amount)
        assertEquals("Makanan", result2.detectedCategory?.name)
        assertEquals(todayStr, result2.transactionDate)
    }

    @Test
    fun testFormattedCurrencyDotsAndCommas() {
        assertEquals(25000L, VoiceTransactionParser.parseNominal("25.000"))
        assertEquals(25000L, VoiceTransactionParser.parseNominal("Rp 25.000"))
        assertEquals(25000L, VoiceTransactionParser.parseNominal("Rp25.000"))
        assertEquals(25000L, VoiceTransactionParser.parseNominal("Rp. 25.000"))
        assertEquals(25000L, VoiceTransactionParser.parseNominal("Rp.25.000"))
        assertEquals(25000L, VoiceTransactionParser.parseNominal("25,000"))
        assertEquals(50000000L, VoiceTransactionParser.parseNominal("50.000.000"))
        assertEquals(5000000L, VoiceTransactionParser.parseNominal("5.000.000"))
        assertEquals(1500000L, VoiceTransactionParser.parseNominal("1.500.000"))
        assertEquals(2000L, VoiceTransactionParser.parseNominal("2.000"))
        assertEquals(25000L, VoiceTransactionParser.parseNominal("25.000,00"))
        assertEquals(100000L, VoiceTransactionParser.parseNominal("IDR 100.000"))
    }

    @Test
    fun testCompoundThousandsPlusHundreds() {
        assertEquals(12500L, VoiceTransactionParser.parseNominal("12 ribu 500"))
        assertEquals(12500L, VoiceTransactionParser.parseNominal("12rb 500"))
        assertEquals(25500L, VoiceTransactionParser.parseNominal("25 ribu 500"))
        assertEquals(15500L, VoiceTransactionParser.parseNominal("15k 500"))
    }

    @Test
    fun testColloquialShortenedNominalsWithoutRibu() {
        // Users saying numbers between 10..999 without the word "ribu"
        val res1 = VoiceTransactionParser.parse("Beli kopi 25 pakai GoPay", mockAssets, mockCategories, defaultAsset)
        assertEquals(25000L, res1.amount)
        assertFalse(res1.isAmountMissing)

        val res2 = VoiceTransactionParser.parse("Beli makan 35 pakai BCA", mockAssets, mockCategories, defaultAsset)
        assertEquals(35000L, res2.amount)
        assertFalse(res2.isAmountMissing)

        val res3 = VoiceTransactionParser.parse("Beli bensin 50 pakai cash", mockAssets, mockCategories, defaultAsset)
        assertEquals(50000L, res3.amount)
        assertFalse(res3.isAmountMissing)

        val res4 = VoiceTransactionParser.parse("Beli pulsa 100 pakai Mandiri", mockAssets, mockCategories, defaultAsset)
        assertEquals(100000L, res4.amount)
        assertFalse(res4.isAmountMissing)

        val res5 = VoiceTransactionParser.parse("Token listrik 200 pakai BCA", mockAssets, mockCategories, defaultAsset)
        assertEquals(200000L, res5.amount)
        assertFalse(res5.isAmountMissing)

        val res6 = VoiceTransactionParser.parse("Parkir 2 pakai cash", mockAssets, mockCategories, defaultAsset)
        assertEquals(2000L, res6.amount)
        assertFalse(res6.isAmountMissing)

        val res7 = VoiceTransactionParser.parse("Beli makan seharga 35", mockAssets, mockCategories, defaultAsset)
        assertEquals(35000L, res7.amount)
        assertFalse(res7.isAmountMissing)

        val res8 = VoiceTransactionParser.parse("Makan siang habis 42", mockAssets, mockCategories, defaultAsset)
        assertEquals(42000L, res8.amount)
        assertFalse(res8.isAmountMissing)

        val res9 = VoiceTransactionParser.parse("Kopi 20", mockAssets, mockCategories, defaultAsset)
        assertEquals(20000L, res9.amount)
        assertFalse(res9.isAmountMissing)

        val res10 = VoiceTransactionParser.parse("Beli makan dua puluh lima pakai GoPay", mockAssets, mockCategories, defaultAsset)
        assertEquals(25000L, res10.amount)
        assertFalse(res10.isAmountMissing)
    }

    @Test
    fun testConcatenatedSpeechArtifactsAndSuffixes() {
        val res1 = VoiceTransactionParser.parse("Beli kopi duapuluh lima ribu pakai GoPay", mockAssets, mockCategories, defaultAsset)
        assertEquals(25000L, res1.amount)

        val res2 = VoiceTransactionParser.parse("Beli bensin limapuluh ribu pakai cash", mockAssets, mockCategories, defaultAsset)
        assertEquals(50000L, res2.amount)

        val res3 = VoiceTransactionParser.parse("Transfer duaratus ribu dari BCA ke Tabungan", mockAssets, mockCategories, defaultAsset)
        assertEquals(200000L, res3.amount)

        val res4 = VoiceTransactionParser.parse("Beli makan 30 ribuan pakai GoPay", mockAssets, mockCategories, defaultAsset)
        assertEquals(30000L, res4.amount)

        val res5 = VoiceTransactionParser.parse("Jajan 20 rebuan", mockAssets, mockCategories, defaultAsset)
        assertEquals(20000L, res5.amount)

        val res6 = VoiceTransactionParser.parse("Beli makan 50 ribu rupiah pakai BCA", mockAssets, mockCategories, defaultAsset)
        assertEquals(50000L, res6.amount)

        val res7 = VoiceTransactionParser.parse("Beli makan 50rb rupiah", mockAssets, mockCategories, defaultAsset)
        assertEquals(50000L, res7.amount)
    }
}
