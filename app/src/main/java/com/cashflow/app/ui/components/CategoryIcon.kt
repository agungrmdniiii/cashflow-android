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
import androidx.compose.ui.unit.dp

@Composable
fun CategoryIcon(
    iconName: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
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

    val isDark = com.cashflow.app.ui.theme.LocalIsDarkTheme.current
    val defaultBg = getCategoryColor(iconName, isDark)
    val actualBg = backgroundColor ?: defaultBg
    val actualTint = tintColor ?: Color.White

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

/**
 * Global single-source-of-truth category color palette.
 * Ensures identical category colors across Donut charts, Transactions lists, and Budgets.
 * In Dark Mode, luminous tones are selected to ensure WCAG AA contrast (> 4.5:1).
 */
fun getCategoryColor(iconName: String, isDark: Boolean = false): Color {
    val key = iconName.lowercase().trim()
    return if (isDark) {
        when {
            key in listOf("restaurant", "makanan", "food", "fastfood", "coffee", "kopi") -> Color(0xFFE57373) // Coral Salmon
            key in listOf("directions_car", "transportasi", "mobil", "motor", "bensin", "local_gas_station") -> Color(0xFFFFB74D) // Bright Amber
            key in listOf("receipt_long", "tagihan", "bolt", "wifi", "listrik") -> Color(0xFF4FC3F7) // Sky Blue
            key in listOf("shopping_bag", "belanja", "shopping_cart", "supermarket") -> Color(0xFF81C784) // Mint Green
            key in listOf("movie", "hiburan", "sports_esports", "flight", "game") -> Color(0xFFBA68C8) // Orchid Purple
            key in listOf("local_hospital", "medical_services", "kesehatan", "obat") -> Color(0xFF4DB6AC) // Mint Teal
            key in listOf("school", "pendidikan", "menu_book", "buku") -> Color(0xFF7986CB) // Periwinkle Blue
            key in listOf("home", "rumah", "kebutuhan rumah") -> Color(0xFFA1887F) // Warm Taupe
            key in listOf("family_restroom", "keluarga", "anak") -> Color(0xFFFF8A65) // Peach Coral
            key in listOf("spa", "perawatan", "face", "salon") -> Color(0xFFF06292) // Rose Pink
            key in listOf("fitness_center", "olahraga", "gym", "fitness") -> Color(0xFF66BB6A) // Meadow Pine
            key in listOf("pets", "peliharaan", "anabul") -> Color(0xFFFFB300) // Amber Ochre
            key in listOf("volunteer_activism", "zakat", "sedekah", "donasi", "infaq", "favorite") -> Color(0xFF26A69A) // Radiant Jade
            key in listOf("credit_card", "cicilan", "paylater") -> Color(0xFFEF5350) // Crimson
            key in listOf("savings", "tabungan", "investasi_exp", "emas") -> Color(0xFF80CBC4) // Soft Aquamarine
            key in listOf("payments", "gaji", "payroll") -> Color(0xFF66BB6A) // Vibrant Emerald
            key in listOf("laptop", "work", "freelance", "proyek") -> Color(0xFF29B6F6) // Tech Cyan
            key in listOf("celebration", "bonus", "thr", "card_giftcard") -> Color(0xFF42A5F5) // Electric Azure
            key in listOf("storefront", "penjualan", "bisnis", "toko") -> Color(0xFFFFA726) // Orange Gold
            key in listOf("trending_up", "investasi", "dividen", "investasi_inc") -> Color(0xFF4CAF50) // Emerald
            key in listOf("currency_exchange", "cashback", "reward", "refund") -> Color(0xFFFFCA28) // Honey Gold
            key in listOf("redeem", "hadiah", "angpao") -> Color(0xFFAB47BC) // Violet
            key in listOf("home_work", "sewa", "properti", "kosan") -> Color(0xFF81C784) // Sage
            key in listOf("swap_horiz", "transfer", "pindah_saldo") -> Color(0xFF4DB6AC) // Teal
            key in listOf("more_horiz", "lainnya", "lain-lain", "attach_money") -> Color(0xFF90A4AE) // Slate Blue Gray
            else -> Color(0xFF78909C)
        }
    } else {
        when {
            key in listOf("restaurant", "makanan", "food", "fastfood", "coffee", "kopi") -> Color(0xFF8D3B3B) // Terracotta Wine
            key in listOf("directions_car", "transportasi", "mobil", "motor", "bensin", "local_gas_station") -> Color(0xFFB57C1E) // Amber Gold
            key in listOf("receipt_long", "tagihan", "bolt", "wifi", "listrik") -> Color(0xFF2E6F89) // Slate Teal
            key in listOf("shopping_bag", "belanja", "shopping_cart", "supermarket") -> Color(0xFF2D5D48) // Forest Olive
            key in listOf("movie", "hiburan", "sports_esports", "flight", "game") -> Color(0xFF6B3E7A) // Royal Plum
            key in listOf("local_hospital", "medical_services", "kesehatan", "obat") -> Color(0xFF1E7063) // Medical Mint Teal
            key in listOf("school", "pendidikan", "menu_book", "buku") -> Color(0xFF3B487A) // Royal Indigo
            key in listOf("home", "rumah", "kebutuhan rumah") -> Color(0xFF704E38) // Earthy Wood
            key in listOf("family_restroom", "keluarga", "anak") -> Color(0xFF9E4B3E) // Warm Clay Coral
            key in listOf("spa", "perawatan", "face", "salon") -> Color(0xFF8F5845) // Warm Rosewood
            key in listOf("fitness_center", "olahraga", "gym", "fitness") -> Color(0xFF386641) // Forest Pine
            key in listOf("pets", "peliharaan", "anabul") -> Color(0xFF8C5835) // Bronze Umber
            key in listOf("volunteer_activism", "zakat", "sedekah", "donasi", "infaq", "favorite") -> Color(0xFF13503C) // Deep Jade
            key in listOf("credit_card", "cicilan", "paylater") -> Color(0xFF7A2E35) // Deep Crimson
            key in listOf("savings", "tabungan", "investasi_exp", "emas") -> Color(0xFF1C5A46) // Mint Emerald
            key in listOf("payments", "gaji", "payroll") -> Color(0xFF0F3D2E) // Deepest Forest
            key in listOf("laptop", "work", "freelance", "proyek") -> Color(0xFF1A586E) // Tech Blue Teal
            key in listOf("celebration", "bonus", "thr", "card_giftcard") -> Color(0xFF1D5A85) // Electric Sapphire
            key in listOf("storefront", "penjualan", "bisnis", "toko") -> Color(0xFF945219) // Burnt Ochre
            key in listOf("trending_up", "investasi", "dividen", "investasi_inc") -> Color(0xFF165B33) // Emerald Green
            key in listOf("currency_exchange", "cashback", "reward", "refund") -> Color(0xFF8F6B23) // Warm Honey Bronze
            key in listOf("redeem", "hadiah", "angpao") -> Color(0xFF5E2B7A) // Velvet Orchid
            key in listOf("home_work", "sewa", "properti", "kosan") -> Color(0xFF2E5339) // Moss Spruce
            key in listOf("swap_horiz", "transfer", "pindah_saldo") -> Color(0xFF224E3D) // Fintech Green
            key in listOf("more_horiz", "lainnya", "lain-lain", "attach_money") -> Color(0xFF4A5550) // Slate Charcoal
            else -> Color(0xFF2C3E35)
        }
    }
}
