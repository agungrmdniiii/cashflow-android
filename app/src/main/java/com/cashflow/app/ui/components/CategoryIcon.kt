package com.cashflow.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
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
import com.cashflow.app.ui.theme.DarkBorder
import com.cashflow.app.ui.theme.JadePrimary
import com.cashflow.app.ui.theme.JadePrimaryContainer

@Composable
fun CategoryIcon(
    iconName: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    backgroundColor: Color = JadePrimaryContainer,
    tintColor: Color = JadePrimary
) {
    val vector: ImageVector = when (iconName.lowercase()) {
        "restaurant", "makanan" -> Icons.Rounded.Restaurant
        "directions_car", "transportasi" -> Icons.Rounded.DirectionsCar
        "receipt_long", "tagihan" -> Icons.AutoMirrored.Rounded.ReceiptLong
        "shopping_bag", "belanja" -> Icons.Rounded.ShoppingBag
        "movie", "hiburan" -> Icons.Rounded.Movie
        "local_hospital", "kesehatan" -> Icons.Rounded.LocalHospital
        "school", "pendidikan" -> Icons.Rounded.School
        "home", "rumah" -> Icons.Rounded.Home
        "payments", "gaji" -> Icons.Rounded.Payments
        "work", "freelance" -> Icons.Rounded.Work
        "card_giftcard", "bonus" -> Icons.Rounded.CardGiftcard
        "redeem", "hadiah" -> Icons.Rounded.Redeem
        "storefront", "penjualan" -> Icons.Rounded.Storefront
        "swap_horiz", "transfer" -> Icons.Rounded.SwapHoriz
        else -> Icons.Rounded.Category
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(1.dp, DarkBorder.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = vector,
            contentDescription = null,
            tint = tintColor,
            modifier = Modifier.size(iconSize)
        )
    }
}
