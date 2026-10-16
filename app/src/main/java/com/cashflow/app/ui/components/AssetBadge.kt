package com.cashflow.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cashflow.app.ui.theme.AppRadius
import com.cashflow.app.ui.theme.AppSpacing
import com.cashflow.app.ui.theme.duit

@Composable
fun AssetBadge(
    text: String = "Utama",
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.duit.primaryContainer,
    textColor: Color = MaterialTheme.duit.onPrimaryContainer
) {
    val shape = RoundedCornerShape(AppRadius.xs)
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, MaterialTheme.duit.borderStrong.copy(alpha = 0.5f), shape)
            .padding(horizontal = AppSpacing.xs, vertical = AppSpacing.xxs)
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

/**
 * Standardized High-Contrast Asset Icon
 * Distinctly visible in both Light and Dark mode using semantic surfaceVariant/border and textPrimary tokens.
 */
@Composable
fun AssetIcon(
    assetType: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    shape: Shape = RoundedCornerShape(AppRadius.sm),
    backgroundColor: Color = MaterialTheme.duit.surfaceVariant,
    tintColor: Color = MaterialTheme.duit.textPrimary
) {
    val vector: ImageVector = when (assetType.lowercase().trim()) {
        "tunai", "cash" -> Icons.Rounded.LocalAtm
        "bank" -> Icons.Rounded.AccountBalance
        "e-wallet", "ewallet" -> Icons.Rounded.PhoneAndroid
        "tabungan", "savings" -> Icons.Rounded.Savings
        "kartu kredit", "credit card", "credit" -> Icons.Rounded.CreditCard
        "investasi", "investment" -> Icons.AutoMirrored.Rounded.TrendingUp
        else -> Icons.Rounded.AccountBalanceWallet
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, MaterialTheme.duit.border, shape),
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
