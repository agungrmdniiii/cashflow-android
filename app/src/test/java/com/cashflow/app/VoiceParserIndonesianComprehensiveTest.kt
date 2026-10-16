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

/**
 * Tahap 5G: Comprehensive Indonesian Voice Test Suite (35+ Colloquial Real-World Phrases)
 * Tests parser accuracy across daily transactions, slang numbers, modern e-wallets,
 * and Indonesian colloquialisms.
 */
class VoiceParserIndonesianComprehensiveTest {

    private lateinit var assets: List<Asset>
    private lateinit var categories: List<Category>
    private lateinit var defaultAsset: Asset

    @Before
    fun setUp() {
        assets = listOf(
            Asset("bca", "BCA", "Bank", 5000000L, 5000000L, isDefault = true),
            Asset("mandiri", "Mandiri", "Bank", 2000000L, 2000000L),
            Asset("gopay", "GoPay", "E-wallet", 250000L, 250000L),
            Asset("ovo", "OVO", "E-wallet", 150000L, 150000L),
            Asset("dana", "DANA", "E-wallet", 100000L, 100000L),
            Asset("shopeepay", "ShopeePay", "E-wallet", 75000L, 75000L),
            Asset("cash", "Cash", "Tunai", 500000L, 500000L),
            Asset("tabungan", "Tabungan", "Bank", 15000000L, 15000000L)
        )
        defaultAsset = assets.first { it.isDefault }

        categories = listOf(
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
            Category("cat_penjualan", "Penjualan", "income"),
            Category("cat_investasi", "Investasi", "expense")
        )
    }

    // --- KULINER & MAKANAN (10 Frasa) ---

    @Test
    fun test01_SeblakPedas() {
        val res = VoiceTransactionParser.parse("Beli seblak ceker 18 ribu bayar pakai GoPay", assets, categories, defaultAsset)
        assertEquals(TransactionType.EXPENSE, res.type)
        assertEquals(18000L, res.amount)
        assertEquals("Makanan", res.detectedCategory?.name)
        assertEquals("GoPay", res.matchedAsset?.name)
    }

    @Test
    fun test02_GorenganWarkop() {
        val res = VoiceTransactionParser.parse("Nongkrong warkop makan gorengan ceban pakai cash", assets, categories, defaultAsset)
        assertEquals(TransactionType.EXPENSE, res.type)
        assertEquals(10000L, res.amount)
        assertEquals("Makanan", res.detectedCategory?.name)
        assertEquals("Cash", res.matchedAsset?.name)
    }

    @Test
    fun test03_PecelLele() {
        val res = VoiceTransactionParser.parse("Makan malam pecel lele 22 ribu dari cash", assets, categories, defaultAsset)
        assertEquals(TransactionType.EXPENSE, res.type)
        assertEquals(22000L, res.amount)
        assertEquals("Makanan", res.detectedCategory?.name)
        assertEquals("Cash", res.matchedAsset?.name)
    }

    @Test
    fun test04_KopiSusuKekinian() {
        val res = VoiceTransactionParser.parse("Beli es kopi susu kenangan 24k pakai BCA", assets, categories, defaultAsset)
        assertEquals(TransactionType.EXPENSE, res.type)
        assertEquals(24000L, res.amount)
        assertEquals("Makanan", res.detectedCategory?.name)
        assertEquals("BCA", res.matchedAsset?.name)
    }

    @Test
    fun test05_MartabakManis() {
        val res = VoiceTransactionParser.parse("Beli martabak manis goban pakai GoPay", assets, categories, defaultAsset)
        assertEquals(TransactionType.EXPENSE, res.type)
        assertEquals(50000L, res.amount)
        assertEquals("Makanan", res.detectedCategory?.name)
        assertEquals("GoPay", res.matchedAsset?.name)
    }

    @Test
    fun test06_BaksoUrat() {
        val res = VoiceTransactionParser.parse("Makan bakso urat 15 ribu pakai DANA", assets, categories, defaultAsset)
        assertEquals(TransactionType.EXPENSE, res.type)
        assertEquals(15000L, res.amount)
        assertEquals("Makanan", res.detectedCategory?.name)
        assertEquals("DANA", res.matchedAsset?.name)
    }

    @Test
    fun test07_MieAyamPangsit() {
        val res = VoiceTransactionParser.parse("Beli mie ayam pangsit 14 ribu pakai cash", assets, categories, defaultAsset)
        assertEquals(TransactionType.EXPENSE, res.type)
        assertEquals(14000L, res.amount)
        assertEquals("Makanan", res.detectedCategory?.name)
        assertEquals("Cash", res.matchedAsset?.name)
    }

    @Test
    fun test08_AyamGeprek() {
        val res = VoiceTransactionParser.parse("Beli ayam geprek sambal korek 20 ribu bayar pakai OVO", assets, categories, defaultAsset)
        assertEquals(TransactionType.EXPENSE, res.type)
        assertEquals(20000L, res.amount)
        assertEquals("Makanan", res.detectedCategory?.name)
        assertEquals("OVO", res.matchedAsset?.name)
    }

    @Test
    fun test09_SarapanLontongSayur() {
        val res = VoiceTransactionParser.parse("Sarapan lontong sayur 12 ribu pakai cash", assets, categories, defaultAsset)
        assertEquals(TransactionType.EXPENSE, res.type)
        assertEquals(12000L, res.amount)
        assertEquals("Makanan", res.detectedCategory?.name)
        assertEquals("Cash", res.matchedAsset?.name)
    }

    @Test
    fun test10_BobaMilkTea() {
        val res = VoiceTransactionParser.parse("Beli boba milk tea 28 ribu pakai ShopeePay", assets, categories, defaultAsset)
        assertEquals(TransactionType.EXPENSE, res.type)
        assertEquals(28000L, res.amount)
        assertEquals("Makanan", res.detectedCategory?.name)
        assertEquals("ShopeePay", res.matchedAsset?.name)
    }

    // --- TRANSPORTASI & KENDARAAN (6 Frasa) ---

    @Test
    fun test11_BensinPertalite() {
        val res = VoiceTransactionParser.parse("Isi bensin pertalite 50 ribu pakai cash", assets, categories, defaultAsset)
        assertEquals(TransactionType.EXPENSE, res.type)
        assertEquals(50000L, res.amount)
        assertEquals("Transportasi", res.detectedCategory?.name)
        assertEquals("Cash", res.matchedAsset?.name)
    }

    @Test
    fun test12_GojekKeKantor() {
        val res = VoiceTransactionParser.parse("Naik gojek ke stasiun 16 ribu pakai GoPay", assets, categories, defaultAsset)
        assertEquals(TransactionType.EXPENSE, res.type)
        assertEquals(16000L, res.amount)
        assertEquals("Transportasi", res.detectedCategory?.name)
        assertEquals("GoPay", res.matchedAsset?.name)
    }

    @Test
    fun test13_GrabCarHujan() {
        val res = VoiceTransactionParser.parse("Ongkos grabcar pulang kantor 65 ribu dari OVO", assets, categories, defaultAsset)
        assertEquals(TransactionType.EXPENSE, res.type)
        assertEquals(65000L, res.amount)
        assertEquals("Transportasi", res.detectedCategory?.name)
        assertEquals("OVO", res.matchedAsset?.name)
    }

    @Test
    fun test14_ParkirMall() {
        val res = VoiceTransactionParser.parse("Bayar parkir mobil mall goceng pakai cash", assets, categories, defaultAsset)
        assertEquals(TransactionType.EXPENSE, res.type)
        assertEquals(5000L, res.amount)
        assertEquals("Transportasi", res.detectedCategory?.name)
        assertEquals("Cash", res.matchedAsset?.name)
    }

    @Test
    fun test15_TambalBanMotor() {
        val res = VoiceTransactionParser.parse("Tambal ban motor 15 ribu pakai cash", assets, categories, defaultAsset)
        assertEquals(TransactionType.EXPENSE, res.type)
        assertEquals(15000L, res.amount)
        assertEquals("Transportasi", res.detectedCategory?.name)
        assertEquals("Cash", res.matchedAsset?.name)
    }

    @Test
    fun test16_ServiceGantiOli() {
        val res = VoiceTransactionParser.parse("Service ganti oli motor 85 ribu bayar pakai BCA", assets, categories, defaultAsset)
        assertEquals(TransactionType.EXPENSE, res.type)
        assertEquals(85000L, res.amount)
        assertEquals("Transportasi", res.detectedCategory?.name)
        assertEquals("BCA", res.matchedAsset?.name)
    }

    // --- BELANJA & KEBUTUHAN RUMAH (6 Frasa) ---

    @Test
    fun test17_BelanjaIndomaret() {
        val res = VoiceTransactionParser.parse("Belanja bulanan di Indomaret 45 ribu pakai BCA", assets, categories, defaultAsset)
        assertEquals(TransactionType.EXPENSE, res.type)
        assertEquals(45000L, res.amount)
        assertEquals("Belanja", res.detectedCategory?.name)
        assertEquals("BCA", res.matchedAsset?.name)
    }

    @Test
    fun test18_BelanjaAlfamart() {
        val res = VoiceTransactionParser.parse("Beli sabun cuci di Alfamart 32 ribu pakai ShopeePay", assets, categories, defaultAsset)
        assertEquals(TransactionType.EXPENSE, res.type)
        assertEquals(32000L, res.amount)
        assertEquals("Belanja", res.detectedCategory?.name)
        assertEquals("ShopeePay", res.matchedAsset?.name)
    }

    @Test
    fun test19_GalonAirMinum() {
        val res = VoiceTransactionParser.parse("Beli air galon isi ulang 20 ribu pakai cash", assets, categories, defaultAsset)
        assertEquals(TransactionType.EXPENSE, res.type)
        assertEquals(20000L, res.amount)
        assertEquals("Kebutuhan rumah", res.detectedCategory?.name)
        assertEquals("Cash", res.matchedAsset?.name)
    }

    @Test
    fun test20_GasElpiji() {
        val res = VoiceTransactionParser.parse("Beli tabung gas elpiji 22 ribu pakai cash", assets, categories, defaultAsset)
        assertEquals(TransactionType.EXPENSE, res.type)
        assertEquals(22000L, res.amount)
        assertEquals("Kebutuhan rumah", res.detectedCategory?.name)
        assertEquals("Cash", res.matchedAsset?.name)
    }

    @Test
    fun test21_LaundryKiloan() {
        val res = VoiceTransactionParser.parse("Bayar laundry kiloan 35 ribu pakai DANA", assets, categories, defaultAsset)
        assertEquals(TransactionType.EXPENSE, res.type)
        assertEquals(35000L, res.amount)
        assertEquals("Kebutuhan rumah", res.detectedCategory?.name)
        assertEquals("DANA", res.matchedAsset?.name)
    }

    @Test
    fun test22_CheckoutShopee() {
        val res = VoiceTransactionParser.parse("Checkout shopee casing hp 50 ribu pakai ShopeePay", assets, categories, defaultAsset)
        assertEquals(TransactionType.EXPENSE, res.type)
        assertEquals(50000L, res.amount)
        assertEquals("Belanja", res.detectedCategory?.name)
        assertEquals("ShopeePay", res.matchedAsset?.name)
    }

    // --- TAGIHAN & KESEHATAN (5 Frasa) ---

    @Test
    fun test23_TokenListrikPLN() {
        val res = VoiceTransactionParser.parse("Beli token listrik PLN 100 ribu dari BCA", assets, categories, defaultAsset)
        assertEquals(TransactionType.EXPENSE, res.type)
        assertEquals(100000L, res.amount)
        assertEquals("Tagihan", res.detectedCategory?.name)
        assertEquals("BCA", res.matchedAsset?.name)
    }

    @Test
    fun test24_TagihanWifiIndihome() {
        val res = VoiceTransactionParser.parse("Bayar wifi indihome rumah 350 ribu dari Mandiri", assets, categories, defaultAsset)
        assertEquals(TransactionType.EXPENSE, res.type)
        assertEquals(350000L, res.amount)
        assertEquals("Tagihan", res.detectedCategory?.name)
        assertEquals("Mandiri", res.matchedAsset?.name)
    }

    @Test
    fun test25_PulsaPaketData() {
        val res = VoiceTransactionParser.parse("Beli pulsa dan kuota internet 75 ribu pakai GoPay", assets, categories, defaultAsset)
        assertEquals(TransactionType.EXPENSE, res.type)
        assertEquals(75000L, res.amount)
        assertEquals("Tagihan", res.detectedCategory?.name)
        assertEquals("GoPay", res.matchedAsset?.name)
    }

    @Test
    fun test26_ObatApotekKimiaFarma() {
        val res = VoiceTransactionParser.parse("Beli obat batuk apotek 40 ribu pakai cash", assets, categories, defaultAsset)
        assertEquals(TransactionType.EXPENSE, res.type)
        assertEquals(40000L, res.amount)
        assertEquals("Kesehatan", res.detectedCategory?.name)
        assertEquals("Cash", res.matchedAsset?.name)
    }

    @Test
    fun test27_IuranBPJSKesehatan() {
        val res = VoiceTransactionParser.parse("Bayar bpjs kesehatan sekeluarga 150 ribu dari BCA", assets, categories, defaultAsset)
        assertEquals(TransactionType.EXPENSE, res.type)
        assertEquals(150000L, res.amount)
        assertEquals("Tagihan", res.detectedCategory?.name)
        assertEquals("BCA", res.matchedAsset?.name)
    }

    // --- PEMASUKAN & REZEKI (5 Frasa) ---

    @Test
    fun test28_GajiBulanan() {
        val res = VoiceTransactionParser.parse("Gaji bulanan masuk 7 juta ke BCA", assets, categories, defaultAsset)
        assertEquals(TransactionType.INCOME, res.type)
        assertEquals(7000000L, res.amount)
        assertEquals("Gaji", res.detectedCategory?.name)
        assertEquals("BCA", res.matchedAsset?.name)
    }

    @Test
    fun test29_ProyekFreelanceDesign() {
        val res = VoiceTransactionParser.parse("Dapat fee freelance logo 1,5 juta masuk Mandiri", assets, categories, defaultAsset)
        assertEquals(TransactionType.INCOME, res.type)
        assertEquals(1500000L, res.amount)
        assertEquals("Freelance", res.detectedCategory?.name)
        assertEquals("Mandiri", res.matchedAsset?.name)
    }

    @Test
    fun test30_BonusPenjualanOlshop() {
        val res = VoiceTransactionParser.parse("Hasil jualan olshop laris 500 ribu masuk BCA", assets, categories, defaultAsset)
        assertEquals(TransactionType.INCOME, res.type)
        assertEquals(500000L, res.amount)
        assertEquals("Penjualan", res.detectedCategory?.name)
        assertEquals("BCA", res.matchedAsset?.name)
    }

    @Test
    fun test31_UangBonusLembur() {
        val res = VoiceTransactionParser.parse("Uang bonus lembur 300 ribu masuk Mandiri", assets, categories, defaultAsset)
        assertEquals(TransactionType.INCOME, res.type)
        assertEquals(300000L, res.amount)
        assertEquals("Bonus", res.detectedCategory?.name)
        assertEquals("Mandiri", res.matchedAsset?.name)
    }

    @Test
    fun test32_HadiahUangCash() {
        val res = VoiceTransactionParser.parse("Dapat hadiah uang tunai 200 ribu masuk cash", assets, categories, defaultAsset)
        assertEquals(TransactionType.INCOME, res.type)
        assertEquals(200000L, res.amount)
        assertEquals("Cash", res.matchedAsset?.name)
    }

    // --- TRANSFER & TOP-UP (4 Frasa) ---

    @Test
    fun test33_TopUpDANA() {
        val res = VoiceTransactionParser.parse("Top up DANA 150 ribu dari BCA", assets, categories, defaultAsset)
        assertEquals(TransactionType.TRANSFER, res.type)
        assertEquals(150000L, res.amount)
        assertEquals("BCA", res.matchedAsset?.name)
        assertEquals("DANA", res.destinationAsset?.name)
    }

    @Test
    fun test34_TopUpShopeePay() {
        val res = VoiceTransactionParser.parse("Top up ShopeePay 100 ribu dari Mandiri", assets, categories, defaultAsset)
        assertEquals(TransactionType.TRANSFER, res.type)
        assertEquals(100000L, res.amount)
        assertEquals("Mandiri", res.matchedAsset?.name)
        assertEquals("ShopeePay", res.destinationAsset?.name)
    }

    @Test
    fun test35_TarikTunaiATM() {
        val res = VoiceTransactionParser.parse("Tarik tunai uang 400 ribu dari BCA", assets, categories, defaultAsset)
        assertEquals(TransactionType.TRANSFER, res.type)
        assertEquals(400000L, res.amount)
        assertEquals("BCA", res.matchedAsset?.name)
        assertEquals("Cash", res.destinationAsset?.name)
    }

    @Test
    fun test36_NabungKeRekeningTabungan() {
        val res = VoiceTransactionParser.parse("Transfer 2 juta dari BCA ke tabungan", assets, categories, defaultAsset)
        assertEquals(TransactionType.TRANSFER, res.type)
        assertEquals(2000000L, res.amount)
        assertEquals("BCA", res.matchedAsset?.name)
        assertEquals("Tabungan", res.destinationAsset?.name)
    }
}
