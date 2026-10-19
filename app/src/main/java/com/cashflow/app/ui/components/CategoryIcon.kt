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
    val vector: ImageVector = when (key) {
        // Makanan & Minuman
        "restaurant", "makanan", "food" -> Icons.Rounded.Restaurant
        "coffee", "kopi", "cafe", "local_cafe" -> Icons.Rounded.Coffee
        "fastfood", "snack", "cemilan" -> Icons.Rounded.Fastfood

        // Transportasi
        "directions_car", "transportasi", "mobil" -> Icons.Rounded.DirectionsCar
        "two_wheeler", "motor", "ojol" -> Icons.Rounded.TwoWheeler
        "local_gas_station", "bensin", "spbu" -> Icons.Rounded.LocalGasStation
        "directions_bus", "bus", "kereta" -> Icons.Rounded.DirectionsBus

        // Tagihan & Utilitas
        "receipt_long", "tagihan", "bill" -> Icons.AutoMirrored.Rounded.ReceiptLong
        "bolt", "listrik", "pln" -> Icons.Rounded.Bolt
        "wifi", "internet", "pulsa" -> Icons.Rounded.Wifi

        // Belanja
        "shopping_bag", "belanja", "mall" -> Icons.Rounded.ShoppingBag
        "shopping_cart", "supermarket", "pasar" -> Icons.Rounded.ShoppingCart

        // Hiburan & Rekreasi
        "movie", "hiburan", "bioskop", "film" -> Icons.Rounded.Movie
        "sports_esports", "game", "gaming" -> Icons.Rounded.SportsEsports
        "flight", "flight_takeoff", "liburan", "travel" -> Icons.Rounded.FlightTakeoff
        "headphones", "musik" -> Icons.Rounded.Headphones

        // Kesehatan & Medis
        "local_hospital", "kesehatan", "medis" -> Icons.Rounded.LocalHospital
        "medical_services", "obat", "dokter" -> Icons.Rounded.MedicalServices

        // Olahraga & Kebugaran
        "fitness_center", "olahraga", "gym", "fitness" -> Icons.Rounded.FitnessCenter

        // Pendidikan
        "school", "pendidikan", "kursus", "kuliah" -> Icons.Rounded.School
        "menu_book", "buku", "literasi" -> Icons.AutoMirrored.Rounded.MenuBook

        // Rumah & Keluarga
        "home", "rumah", "kebutuhan rumah" -> Icons.Rounded.Home
        "family_restroom", "keluarga", "anak" -> Icons.Rounded.FamilyRestroom

        // Perawatan & Gaya Diri
        "spa", "perawatan", "salon", "skincare" -> Icons.Rounded.Spa
        "face", "barbershop" -> Icons.Rounded.Face

        // Peliharaan
        "pets", "peliharaan", "anabul", "kucing", "anjing" -> Icons.Rounded.Pets

        // Zakat, Sedekah & Donasi
        "volunteer_activism", "zakat", "sedekah", "donasi", "infaq" -> Icons.Rounded.VolunteerActivism
        "favorite", "amal" -> Icons.Rounded.Favorite

        // Cicilan & Finansial
        "credit_card", "cicilan", "kartu_kredit", "paylater" -> Icons.Rounded.CreditCard
        "savings", "investasi_exp", "tabungan", "emas" -> Icons.Rounded.Savings

        // Pemasukan: Gaji, Freelance, Bisnis
        "payments", "gaji", "payroll" -> Icons.Rounded.Payments
        "laptop", "work", "freelance", "proyek" -> Icons.Rounded.Laptop
        "celebration", "bonus", "thr", "insentif" -> Icons.Rounded.Celebration
        "card_giftcard" -> Icons.Rounded.CardGiftcard
        "storefront", "penjualan", "bisnis", "toko", "dagang" -> Icons.Rounded.Storefront
        "trending_up", "investasi", "dividen", "investasi_inc" -> Icons.AutoMirrored.Rounded.TrendingUp
        "currency_exchange", "cashback", "reward", "refund" -> Icons.Rounded.CurrencyExchange
        "redeem", "hadiah", "angpao" -> Icons.Rounded.Redeem
        "home_work", "sewa", "properti", "kosan" -> Icons.Rounded.HomeWork

        // Transfer & Lainnya
        "swap_horiz", "transfer", "pindah_saldo" -> Icons.Rounded.SwapHoriz
        "more_horiz", "lainnya", "lain-lain" -> Icons.Rounded.MoreHoriz
        "attach_money" -> Icons.Rounded.AttachMoney

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
