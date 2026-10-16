package com.cashflow.app.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.data.model.ThemeMode
import com.cashflow.app.ui.theme.AppSpacing
import com.cashflow.app.ui.theme.duit

/**
 * Section Tampilan: Mengatur tema aplikasi (Sistem, Terang, Gelap)
 */
@Composable
fun AppearanceSection(
    currentThemeMode: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val duit = MaterialTheme.duit

    SettingsSectionCard(
        title = "Tampilan",
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(duit.surfaceVariant)
                        .border(1.dp, duit.border, RoundedCornerShape(999.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = null,
                        tint = duit.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = "Mode Tema",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = duit.textPrimary
                    )
                    Text(
                        text = when (currentThemeMode) {
                            ThemeMode.SYSTEM -> "Mengikuti tema bawaan sistem Android"
                            ThemeMode.LIGHT -> "Mode Terang (Mono & Lime)"
                            ThemeMode.DARK -> "Mode Gelap (OLED Black & Lime)"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            color = duit.textSecondary
                        )
                    )
                }
            }

            // Segmented 3-button Theme Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(duit.surfaceVariant)
                    .border(1.dp, duit.border, RoundedCornerShape(999.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val themes = listOf(
                    Triple(ThemeMode.SYSTEM, "Sistem", Icons.Rounded.BrightnessAuto),
                    Triple(ThemeMode.LIGHT, "Terang", Icons.Rounded.LightMode),
                    Triple(ThemeMode.DARK, "Gelap", Icons.Rounded.DarkMode)
                )

                themes.forEach { (mode, title, icon) ->
                    val isSelected = currentThemeMode == mode
                    val itemBg by animateColorAsState(
                        targetValue = if (isSelected) duit.primary else Color.Transparent,
                        animationSpec = tween(180),
                        label = "theme_bg"
                    )
                    val itemTextCol by animateColorAsState(
                        targetValue = if (isSelected) duit.onPrimary else duit.textSecondary,
                        animationSpec = tween(180),
                        label = "theme_text"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 40.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(itemBg)
                            .clickable(role = Role.RadioButton) {
                                onThemeSelected(mode)
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = itemTextCol
                            )
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                color = itemTextCol
                            )
                        }
                    }
                }
            }
        }
    }
}
