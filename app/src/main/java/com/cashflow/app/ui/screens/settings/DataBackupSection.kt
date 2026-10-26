package com.cashflow.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.ui.components.DuitAingButton
import com.cashflow.app.ui.components.DuitAingButtonVariant
import com.cashflow.app.ui.theme.AppSpacing
import com.cashflow.app.ui.theme.duit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Section Data & Cadangan: Ekspor/impor JSON, enkripsi AES-256, cadangan ZIP,
 * rekonsiliasi saldo buku besar, laporan, serta reset data.
 */
@Composable
fun DataBackupSection(
    lastBackupTime: Long,
    isBackupOverdue: Boolean,
    isBackupReminderEnabled: Boolean,
    hasSampleData: Boolean,
    onExportJson: () -> Unit,
    onRestoreJson: () -> Unit,
    onBackupReminderToggled: (Boolean) -> Unit,
    onRecalculateBalances: () -> Unit,
    onExportReport: () -> Unit,
    onImportCsv: () -> Unit,
    onDeleteSampleData: () -> Unit,
    onDeleteAllData: () -> Unit,
    modifier: Modifier = Modifier,
    backupFormat: String = "Format tidak tercatat",
    showBackupControls: Boolean = true
) {
    val duit = MaterialTheme.duit

    SettingsSectionCard(
        title = if (showBackupControls) "Data & Cadangan" else "Alat data",
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (showBackupControls) {
            // Status Cadangan Card / Banner
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(duit.primaryContainer)
                            .border(1.dp, duit.border, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Backup,
                            contentDescription = null,
                            tint = duit.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Status Cadangan Lokal",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = duit.textPrimary
                        )
                        Text(
                            text = if (lastBackupTime > 0L) {
                                "$backupFormat tersimpan\n" + SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID")).format(Date(lastBackupTime))
                            } else {
                                "Belum pernah dicadangkan"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (lastBackupTime > 0L) duit.textSecondary else duit.warning
                        )
                    }
                }

                if (isBackupOverdue) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(duit.expenseContainer)
                            .border(1.dp, duit.expense.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = duit.expense,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Sudah >7 hari belum mencadangkan data. Cadangkan sekarang untuk mencegah kehilangan data.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = duit.expense
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = duit.border, thickness = 1.dp)

            // Row 1: Cadangkan Data
            SettingsItemRow(
                title = "Cadangkan Data",
                description = "Simpan data keuangan, favorit kategori, dan pengaturan aplikasi",
                icon = Icons.Rounded.CloudUpload,
                iconTint = duit.textPrimary,
                iconBackground = duit.surfaceVariant,
                control = {
                    DuitAingButton(
                        text = "CADANGKAN",
                        onClick = onExportJson,
                        variant = DuitAingButtonVariant.SECONDARY,
                        height = 36.dp
                    )
                }
            )

            HorizontalDivider(color = duit.border, thickness = 1.dp)

            // Row 2: Pulihkan Data
            SettingsItemRow(
                title = "Pulihkan Data",
                description = "Pulihkan data dari berkas cadangan di perangkat Anda",
                icon = Icons.Rounded.CloudDownload,
                iconTint = duit.textPrimary,
                iconBackground = duit.surfaceVariant,
                control = {
                    DuitAingButton(
                        text = "PULIHKAN",
                        onClick = onRestoreJson,
                        variant = DuitAingButtonVariant.SECONDARY,
                        height = 36.dp
                    )
                }
            )

            HorizontalDivider(color = duit.border, thickness = 1.dp)

            // Row 4: Pengingat Mingguan
            SettingsItemRow(
                title = "Pengingat Mingguan",
                description = "Ingatkan jika >7 hari belum mencadangkan",
                icon = Icons.Rounded.Alarm,
                iconTint = duit.textPrimary,
                iconBackground = duit.surfaceVariant,
                control = {
                    Switch(
                        checked = isBackupReminderEnabled,
                        onCheckedChange = onBackupReminderToggled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = duit.onPrimary,
                            checkedTrackColor = duit.primary,
                            uncheckedThumbColor = duit.textSecondary,
                            uncheckedTrackColor = duit.surfaceVariant
                        )
                    )
                }
            )

            HorizontalDivider(color = duit.border, thickness = 1.dp)

            }

            // Row 5: Rekonsiliasi Saldo Buku Besar
            SettingsItemRow(
                title = "Rekonsiliasi Saldo",
                description = "Hitung ulang saldo transaksi.",
                icon = Icons.Rounded.Sync,
                iconTint = duit.textPrimary,
                iconBackground = duit.surfaceVariant,
                control = {
                    DuitAingButton(
                        text = "HITUNG",
                        onClick = onRecalculateBalances,
                        variant = DuitAingButtonVariant.SECONDARY,
                        height = 36.dp
                    )
                }
            )

            HorizontalDivider(color = duit.border, thickness = 1.dp)

            // Row 6: Ekspor Laporan PDF/CSV
            SettingsItemRow(
                title = "Ekspor Laporan",
                description = "Berkas PDF atau CSV.",
                icon = Icons.Rounded.Download,
                iconTint = duit.textPrimary,
                iconBackground = duit.surfaceVariant,
                control = {
                    DuitAingButton(
                        text = "EKSPOR",
                        onClick = onExportReport,
                        variant = DuitAingButtonVariant.SECONDARY,
                        height = 36.dp
                    )
                }
            )

            HorizontalDivider(color = duit.border, thickness = 1.dp)
            SettingsItemRow(title = "Impor CSV", icon = Icons.Rounded.UploadFile,
                control = { DuitAingButton(text = "IMPOR", onClick = onImportCsv,
                    variant = DuitAingButtonVariant.SECONDARY, height = 36.dp) })

            // Row 7: Hapus Data Sampel (Jika ada)
            if (hasSampleData) {
                HorizontalDivider(color = duit.border, thickness = 1.dp)
                SettingsItemRow(
                    title = "Hapus Data Contoh",
                    description = "Bersihkan aset dan transaksi contoh bawaan",
                    icon = Icons.Rounded.DeleteSweep,
                    iconTint = duit.expense,
                    iconBackground = duit.expenseContainer,
                    control = {
                        DuitAingButton(
                            text = "HAPUS",
                            onClick = onDeleteSampleData,
                            variant = DuitAingButtonVariant.DANGER,
                            height = 36.dp
                        )
                    }
                )
            }

            HorizontalDivider(color = duit.border, thickness = 1.dp)

            // Row 8: Hapus Semua Data
            SettingsItemRow(
                title = "Hapus Semua Data",
                description = "Hapus permanen data keuangan.",
                icon = Icons.Rounded.DeleteForever,
                iconTint = duit.expense,
                iconBackground = duit.expenseContainer,
                control = {
                    DuitAingButton(
                        text = "RESET",
                        onClick = onDeleteAllData,
                        variant = DuitAingButtonVariant.DANGER,
                        height = 36.dp
                    )
                }
            )
        }
    }
}
