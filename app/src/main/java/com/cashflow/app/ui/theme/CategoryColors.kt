package com.cashflow.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Global single-source-of-truth category color palette inside the theme package.
 * Ensures Color(0x...) definitions reside exclusively within the theme layer.
 * In Dark Mode, luminous tones are selected to ensure WCAG AA contrast (> 4.5:1).
 */
fun getCategoryColor(iconName: String, isDark: Boolean = false): Color {
    val key = iconName.lowercase().trim()
    return if (isDark) {
        when {
            key in listOf("restaurant", "makanan", "food", "fastfood", "coffee", "kopi") -> Color(0xFFE57373)
            key in listOf("directions_car", "transportasi", "mobil", "motor", "bensin", "local_gas_station") -> Color(0xFFFFB74D)
            key in listOf("receipt_long", "tagihan", "bolt", "wifi", "listrik") -> Color(0xFF4FC3F7)
            key in listOf("shopping_bag", "belanja", "shopping_cart", "supermarket") -> Color(0xFF81C784)
            key in listOf("movie", "hiburan", "sports_esports", "flight", "game") -> Color(0xFFBA68C8)
            key in listOf("local_hospital", "medical_services", "kesehatan", "obat") -> Color(0xFF4DB6AC)
            key in listOf("school", "pendidikan", "menu_book", "buku") -> Color(0xFF7986CB)
            key in listOf("home", "rumah", "kebutuhan rumah") -> Color(0xFFA1887F)
            key in listOf("family_restroom", "keluarga", "anak") -> Color(0xFFFF8A65)
            key in listOf("spa", "perawatan", "face", "salon") -> Color(0xFFF06292)
            key in listOf("fitness_center", "olahraga", "gym", "fitness") -> Color(0xFF66BB6A)
            key in listOf("pets", "peliharaan", "anabul") -> Color(0xFFFFB300)
            key in listOf("volunteer_activism", "zakat", "sedekah", "donasi", "infaq", "favorite") -> Color(0xFF26A69A)
            key in listOf("credit_card", "cicilan", "paylater") -> Color(0xFFEF5350)
            key in listOf("savings", "tabungan", "investasi_exp", "emas") -> Color(0xFF80CBC4)
            key in listOf("payments", "gaji", "payroll") -> Color(0xFF66BB6A)
            key in listOf("laptop", "work", "freelance", "proyek") -> Color(0xFF29B6F6)
            key in listOf("celebration", "bonus", "thr", "card_giftcard") -> Color(0xFF42A5F5)
            key in listOf("storefront", "penjualan", "bisnis", "toko") -> Color(0xFFFFA726)
            key in listOf("trending_up", "investasi", "dividen", "investasi_inc") -> Color(0xFF4CAF50)
            key in listOf("currency_exchange", "cashback", "reward", "refund") -> Color(0xFFFFCA28)
            key in listOf("redeem", "hadiah", "angpao") -> Color(0xFFAB47BC)
            key in listOf("home_work", "sewa", "properti", "kosan") -> Color(0xFF81C784)
            key in listOf("swap_horiz", "transfer", "pindah_saldo") -> Color(0xFF4DB6AC)
            key in listOf("more_horiz", "lainnya", "lain-lain", "attach_money") -> Color(0xFF90A4AE)
            else -> Color(0xFF78909C)
        }
    } else {
        when {
            key in listOf("restaurant", "makanan", "food", "fastfood", "coffee", "kopi") -> Color(0xFF8D3B3B)
            key in listOf("directions_car", "transportasi", "mobil", "motor", "bensin", "local_gas_station") -> Color(0xFFB57C1E)
            key in listOf("receipt_long", "tagihan", "bolt", "wifi", "listrik") -> Color(0xFF2E6F89)
            key in listOf("shopping_bag", "belanja", "shopping_cart", "supermarket") -> Color(0xFF2D5D48)
            key in listOf("movie", "hiburan", "sports_esports", "flight", "game") -> Color(0xFF6B3E7A)
            key in listOf("local_hospital", "medical_services", "kesehatan", "obat") -> Color(0xFF1E7063)
            key in listOf("school", "pendidikan", "menu_book", "buku") -> Color(0xFF3B487A)
            key in listOf("home", "rumah", "kebutuhan rumah") -> Color(0xFF704E38)
            key in listOf("family_restroom", "keluarga", "anak") -> Color(0xFF9E4B3E)
            key in listOf("spa", "perawatan", "face", "salon") -> Color(0xFF8F5845)
            key in listOf("fitness_center", "olahraga", "gym", "fitness") -> Color(0xFF386641)
            key in listOf("pets", "peliharaan", "anabul") -> Color(0xFF8C5835)
            key in listOf("volunteer_activism", "zakat", "sedekah", "donasi", "infaq", "favorite") -> Color(0xFF13503C)
            key in listOf("credit_card", "cicilan", "paylater") -> Color(0xFF7A2E35)
            key in listOf("savings", "tabungan", "investasi_exp", "emas") -> Color(0xFF1C5A46)
            key in listOf("payments", "gaji", "payroll") -> Color(0xFF0A7559)
            key in listOf("laptop", "work", "freelance", "proyek") -> Color(0xFF1A586E)
            key in listOf("celebration", "bonus", "thr", "card_giftcard") -> Color(0xFF1D5A85)
            key in listOf("storefront", "penjualan", "bisnis", "toko") -> Color(0xFF945219)
            key in listOf("trending_up", "investasi", "dividen", "investasi_inc") -> Color(0xFF165B33)
            key in listOf("currency_exchange", "cashback", "reward", "refund") -> Color(0xFF8F6B23)
            key in listOf("redeem", "hadiah", "angpao") -> Color(0xFF5E2B7A)
            key in listOf("home_work", "sewa", "properti", "kosan") -> Color(0xFF2E5339)
            key in listOf("swap_horiz", "transfer", "pindah_saldo") -> Color(0xFF224E3D)
            key in listOf("more_horiz", "lainnya", "lain-lain", "attach_money") -> Color(0xFF4A5550)
            else -> Color(0xFF2C3E35)
        }
    }
}
