package com.cashflow.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import com.cashflow.app.ui.theme.duit

@Composable
fun CategoryIcon(
    iconName: String,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    iconSize: Dp = 20.dp,
    backgroundColor: Color? = null,
    tintColor: Color? = null
) {
    val key = iconName.lowercase().trim()
    val vector: ImageVector = when {
        // Makanan & Minuman
        key in listOf("restaurant", "makanan", "food") -> Icons.Rounded.Restaurant
        key in listOf("coffee", "kopi", "cafe", "local_cafe") -> Icons.Rounded.Coffee
        key in listOf("fastfood", "snack", "cemilan") -> Icons.Rounded.Fastfood

        // Transportasi
        key in listOf("directions_car", "transportasi", "mobil") -> Icons.Rounded.DirectionsCar
        key in listOf("two_wheeler", "motor", "ojol") -> Icons.Rounded.TwoWheeler
        key in listOf("local_gas_station", "bensin", "spbu") -> Icons.Rounded.LocalGasStation
        key in listOf("directions_bus", "bus", "kereta") -> Icons.Rounded.DirectionsBus

        // Tagihan & Utilitas
        key in listOf("receipt_long", "tagihan", "bill") -> Icons.AutoMirrored.Rounded.ReceiptLong
        key in listOf("bolt", "listrik", "pln") -> Icons.Rounded.Bolt
        key in listOf("wifi", "internet", "pulsa") -> Icons.Rounded.Wifi

        // Belanja
        key in listOf("shopping_bag", "belanja", "mall") -> Icons.Rounded.ShoppingBag
        key in listOf("shopping_cart", "supermarket", "pasar") -> Icons.Rounded.ShoppingCart

        // Hiburan & Rekreasi
        key in listOf("movie", "hiburan", "bioskop", "film") -> Icons.Rounded.Movie
        key in listOf("sports_esports", "game", "gaming") -> Icons.Rounded.SportsEsports
        key in listOf("flight", "flight_takeoff", "liburan", "travel") -> Icons.Rounded.FlightTakeoff
        key in listOf("headphones", "musik") -> Icons.Rounded.Headphones

        // Kesehatan & Medis
        key in listOf("local_hospital", "kesehatan", "medis") -> Icons.Rounded.LocalHospital
        key in listOf("medical_services", "obat", "dokter") -> Icons.Rounded.MedicalServices

        // Olahraga & Kebugaran
        key in listOf("fitness_center", "olahraga", "gym", "fitness") -> Icons.Rounded.FitnessCenter

        // Pendidikan
        key in listOf("school", "pendidikan", "kursus", "kuliah") -> Icons.Rounded.School
        key in listOf("menu_book", "buku", "literasi") -> Icons.AutoMirrored.Rounded.MenuBook

        // Rumah & Keluarga
        key in listOf("home", "rumah", "kebutuhan rumah") -> Icons.Rounded.Home
        key in listOf("family_restroom", "keluarga", "anak") -> Icons.Rounded.FamilyRestroom

        // Perawatan & Gaya Diri
        key in listOf("spa", "perawatan", "salon", "skincare") -> Icons.Rounded.Spa
        key in listOf("face", "barbershop") -> Icons.Rounded.Face

        // Peliharaan
        key in listOf("pets", "peliharaan", "anabul", "kucing", "anjing") -> Icons.Rounded.Pets

        // Zakat, Sedekah & Donasi
        key in listOf("volunteer_activism", "zakat", "sedekah", "donasi", "infaq") -> Icons.Rounded.VolunteerActivism
        key in listOf("favorite", "amal") -> Icons.Rounded.Favorite

        // Cicilan & Finansial
        key in listOf("credit_card", "cicilan", "kartu_kredit", "paylater") -> Icons.Rounded.CreditCard
        key in listOf("savings", "investasi_exp", "tabungan", "emas") -> Icons.Rounded.Savings

        // Pemasukan: Gaji, Freelance, Bisnis
        key in listOf("payments", "gaji", "payroll") -> Icons.Rounded.Payments
        key in listOf("laptop", "work", "freelance", "proyek") -> Icons.Rounded.Laptop
        key in listOf("celebration", "bonus", "thr", "insentif") -> Icons.Rounded.Celebration
        key in listOf("card_giftcard") -> Icons.Rounded.CardGiftcard
        key in listOf("storefront", "penjualan", "bisnis", "toko", "dagang") -> Icons.Rounded.Storefront
        key in listOf("trending_up", "investasi", "dividen", "investasi_inc") -> Icons.AutoMirrored.Rounded.TrendingUp
        key in listOf("currency_exchange", "cashback", "reward", "refund") -> Icons.Rounded.CurrencyExchange
        key in listOf("redeem", "hadiah", "angpao") -> Icons.Rounded.Redeem
        key in listOf("home_work", "sewa", "properti", "kosan") -> Icons.Rounded.HomeWork

        // Transfer & Lainnya
        key in listOf("swap_horiz", "transfer", "pindah_saldo") -> Icons.Rounded.SwapHoriz
        key in listOf("more_horiz", "lainnya", "lain-lain") -> Icons.Rounded.MoreHoriz
        key in listOf("attach_money") -> Icons.Rounded.AttachMoney

        else -> Icons.Rounded.Category
    }

    val duit = MaterialTheme.duit
    val actualBg = backgroundColor ?: duit.surfaceVariant
    val actualTint = tintColor ?: duit.textPrimary

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(actualBg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = vector,
            contentDescription = null,
            tint = actualTint,
            modifier = Modifier.size(iconSize)
        )
    }
}
