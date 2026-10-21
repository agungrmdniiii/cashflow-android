package com.cashflow.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.ui.theme.duit

/**
 * Section Vina, Pindai Struk OCR, dan Pintasan Widget Android.
 */
@Composable
fun OnlineFeaturesSection(
    modifier: Modifier = Modifier
) {
    val duit = MaterialTheme.duit

    SettingsSectionCard(
        title = "Vina & Fitur Perangkat",
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SettingsItemRow(title = "Vina offline", description = "Analisis catatan di perangkat, tanpa internet atau kunci API.",
                icon = Icons.Rounded.AutoAwesome, iconTint = duit.textPrimary, iconBackground = duit.surfaceVariant)

            HorizontalDivider(color = duit.border, thickness = 1.dp)

            // Row 2: Scan Struk OCR
            SettingsItemRow(
                title = "Pindai Struk OCR",
                description = "Pembacaan teks di perangkat dengan ML Kit. Foto diproses di perangkat; periksa hasil sebelum menyimpan.",
                icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                iconTint = duit.textPrimary,
                iconBackground = duit.surfaceVariant,
                control = {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(duit.surfaceVariant)
                            .border(1.dp, duit.border, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "LOKAL",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp
                            ),
                            color = duit.textSecondary
                        )
                    }
                }
            )

            HorizontalDivider(color = duit.border, thickness = 1.dp)

            // Row 3: Widget & Pintasan Android
            SettingsItemRow(
                title = "Pintasan & Widget",
                description = "Widget layar beranda & pintasan rekam suara instan",
                icon = Icons.Rounded.Widgets,
                iconTint = duit.textPrimary,
                iconBackground = duit.surfaceVariant,
                control = {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(duit.surfaceVariant)
                            .border(1.dp, duit.border, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "TERSEDIA",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = duit.textSecondary
                        )
                    }
                }
            )
        }
    }
}
