package com.cashflow.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.ui.components.DuitAingButton
import com.cashflow.app.ui.components.DuitAingButtonVariant
import com.cashflow.app.ui.components.DuitAingLogo
import com.cashflow.app.ui.theme.duit

/**
 * Section Tentang: Identitas Duit Aing, panduan onboarding,
 * diagnostik sistem, dan preview design system.
 */
@Composable
fun AboutSection(
    onOpenChangelog: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onOpenDiagnostic: () -> Unit,
    onOpenIntegrityCheck: () -> Unit,
    onOpenDesignSystem: () -> Unit,
    discrepancyCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val duit = MaterialTheme.duit

    SettingsSectionCard(
        title = "Tentang Aplikasi",
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Info Aplikasi
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onOpenChangelog)
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DuitAingLogo(size = 48.dp)

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "DUIT AING",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                            fontSize = 17.sp
                        ),
                        color = duit.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Versi ${com.cashflow.app.BuildConfig.VERSION_NAME} · Build ${com.cashflow.app.BuildConfig.VERSION_CODE}\nLihat riwayat pembaruan",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = duit.textSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(duit.surfaceVariant)
                        .border(1.dp, duit.border, RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "CATAT KEUANGAN • DATA DI PERANGKAT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            fontSize = 11.sp
                        ),
                        color = duit.textPrimary
                    )
                }
            }

            HorizontalDivider(color = duit.border, thickness = 1.dp)

            if (com.cashflow.app.BuildConfig.DIRECT_APK_UPDATES) {
                AppUpdateSection()
                HorizontalDivider(color = duit.border, thickness = 1.dp)
            }

            SettingsItemRow(
                title = "Riwayat pembaruan",
                description = "Catatan versi terbaru dan sebelumnya",
                icon = Icons.Rounded.History,
                iconTint = duit.textPrimary,
                iconBackground = duit.surfaceVariant,
                onClick = onOpenChangelog,
                control = { DuitAingButton(text = "LIHAT", onClick = onOpenChangelog,
                    variant = DuitAingButtonVariant.SECONDARY, height = 36.dp) }
            )
            HorizontalDivider(color = duit.border, thickness = 1.dp)

            // Row 1: Panduan Penggunaan
            SettingsItemRow(
                title = "Panduan Aplikasi",
                description = "Tur pencatatan dan petunjuk fitur lainnya",
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                iconTint = duit.textPrimary,
                iconBackground = duit.surfaceVariant,
                control = {
                    DuitAingButton(
                        text = "BUKA",
                        onClick = onOpenOnboarding,
                        variant = DuitAingButtonVariant.SECONDARY,
                        height = 36.dp
                    )
                }
            )

            HorizontalDivider(color = duit.border, thickness = 1.dp)

            // Row 2: Diagnostik & Log Sistem
            SettingsItemRow(
                title = "Diagnostik Sistem",
                description = "Inspeksi kesehatan memori & log error yang aman tersanitasi",
                icon = Icons.Rounded.BugReport,
                iconTint = duit.textPrimary,
                iconBackground = duit.surfaceVariant,
                control = {
                    DuitAingButton(
                        text = "LOG",
                        onClick = onOpenDiagnostic,
                        variant = DuitAingButtonVariant.SECONDARY,
                        height = 36.dp
                    )
                }
            )

            HorizontalDivider(color = duit.border, thickness = 1.dp)

            // Row 2b: Cek Integritas Saldo
            SettingsItemRow(
                title = "Integritas Saldo",
                description = if (discrepancyCount > 0) {
                    "Terdeteksi $discrepancyCount selisih saldo aset"
                } else {
                    "Semua saldo aset sinkron dengan riwayat transaksi"
                },
                icon = if (discrepancyCount > 0) Icons.Rounded.WarningAmber else Icons.Rounded.VerifiedUser,
                iconTint = if (discrepancyCount > 0) duit.expense else duit.income,
                iconBackground = if (discrepancyCount > 0) duit.expense.copy(alpha = 0.12f) else duit.income.copy(alpha = 0.12f),
                onClick = onOpenIntegrityCheck,
                control = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (discrepancyCount > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(duit.expense)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$discrepancyCount",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = duit.onPrimary
                                )
                            }
                        }
                        DuitAingButton(
                            text = "CEK",
                            onClick = onOpenIntegrityCheck,
                            variant = if (discrepancyCount > 0) DuitAingButtonVariant.PRIMARY else DuitAingButtonVariant.SECONDARY,
                            height = 36.dp
                        )
                    }
                }
            )

            HorizontalDivider(color = duit.border, thickness = 1.dp)

            // Row 3: Fondasi Design System
            if (androidx.compose.ui.platform.LocalContext.current.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) SettingsItemRow(
                title = "Design System",
                description = "Pratinjau tipografi Plus Jakarta Sans & token warna",
                icon = Icons.Rounded.Palette,
                iconTint = duit.textPrimary,
                iconBackground = duit.surfaceVariant,
                control = {
                    DuitAingButton(
                        text = "PREVIEW",
                        onClick = onOpenDesignSystem,
                        variant = DuitAingButtonVariant.SECONDARY,
                        height = 36.dp
                    )
                }
            )
        }
    }
}
