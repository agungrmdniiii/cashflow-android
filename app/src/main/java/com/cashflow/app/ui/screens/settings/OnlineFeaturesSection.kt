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
import com.cashflow.app.ui.components.DuitAingButton
import com.cashflow.app.ui.components.DuitAingButtonVariant
import com.cashflow.app.ui.theme.duit

/**
 * Section Fitur Online: Integrasi Gemini AI, Pindai Struk OCR, dan Pintasan Widget Android.
 */
@Composable
fun OnlineFeaturesSection(
    geminiApiKey: String?,
    onConfigureApiKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    val duit = MaterialTheme.duit
    val hasApiKey = !geminiApiKey.isNullOrBlank()

    SettingsSectionCard(
        title = "Fitur Online & AI",
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Row 1: Google Gemini API Key
            SettingsItemRow(
                title = "Gemini AI (opsional)",
                description = if (hasApiKey) "Kunci tersimpan di perangkat. Penggunaan API mengikuti kuota penyedia."
                else "Analisis lokal tersedia tanpa kunci API.",
                icon = Icons.Rounded.AutoAwesome,
                iconTint = if (hasApiKey) duit.primary else duit.textSecondary,
                iconBackground = if (hasApiKey) duit.primaryContainer else duit.surfaceVariant,
                control = {
                    DuitAingButton(
                        text = if (hasApiKey) "UBAH" else "SETEL",
                        onClick = onConfigureApiKey,
                        variant = DuitAingButtonVariant.SECONDARY,
                        height = 36.dp
                    )
                }
            )

            HorizontalDivider(color = duit.border, thickness = 1.dp)

            // Row 2: Scan Struk OCR
            SettingsItemRow(
                title = "Pindai Struk OCR",
                description = "Pembacaan teks di perangkat dengan ML Kit. Foto tidak dikirim ke Gemini; periksa hasil sebelum menyimpan.",
                icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                iconTint = duit.textPrimary,
                iconBackground = duit.surfaceVariant,
                control = {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (hasApiKey) duit.primaryContainer else duit.surfaceVariant)
                            .border(1.dp, duit.border, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "LOKAL",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp
                            ),
                            color = if (hasApiKey) duit.onPrimaryContainer else duit.textSecondary
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
