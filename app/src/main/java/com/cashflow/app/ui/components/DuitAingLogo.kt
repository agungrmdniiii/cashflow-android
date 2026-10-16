package com.cashflow.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import com.cashflow.app.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.ui.theme.*

enum class LogoVariant {
    COLOR,
    MONOCHROME,
    ON_DARK
}

/**
 * Geometric Brand Logo for "Duit Aing"
 * Combines concepts of:
 * - Currency coin circle
 * - Dynamic flow loop & letter "D" curve
 * - Interlocking geometric angles representing cash flow and financial growth
 */
@Composable
fun DuitAingLogo(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    variant: LogoVariant = LogoVariant.COLOR
) {
    Image(
        painter = painterResource(id = R.drawable.ic_duit_aing_logo),
        contentDescription = "Duit Aing Logo",
        modifier = modifier.size(size)
    )
}

/**
 * Top App Brand Header for Duit Aing
 */
@Composable
fun DuitAingBrandHeader(
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit
) {
    val duit = MaterialTheme.duit
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            DuitAingLogo(size = 38.dp)

            Column {
                Text(
                    text = "DUIT AING",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    ),
                    color = duit.textPrimary,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "MANAJEMEN KAS PRIBADI",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = duit.textSecondary,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(duit.surface)
                .border(1.dp, duit.border, RoundedCornerShape(8.dp))
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onSettingsClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = "Pengaturan",
                tint = duit.textPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
