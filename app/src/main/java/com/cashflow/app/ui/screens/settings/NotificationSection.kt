package com.cashflow.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.data.model.Asset
import com.cashflow.app.ui.components.AssetDropdownField
import com.cashflow.app.ui.theme.duit

/**
 * Section Notifikasi: Pengingat harian, peringatan batas anggaran, rollover, dan preferensi aset default.
 */
@Composable
fun NotificationSection(
    isDailyReminderEnabled: Boolean,
    dailyReminderTime: String,
    isBudgetAlertsEnabled: Boolean,
    isBudgetRolloverEnabled: Boolean,
    activeAssets: List<Asset>,
    defaultAsset: Asset?,
    onDailyReminderToggled: (Boolean) -> Unit,
    onBudgetAlertsToggled: (Boolean) -> Unit,
    onBudgetRolloverToggled: (Boolean) -> Unit,
    onDefaultAssetSelected: (Asset) -> Unit,
    modifier: Modifier = Modifier
) {
    val duit = MaterialTheme.duit

    SettingsSectionCard(
        title = "Notifikasi & Anggaran",
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Row 1: Pengingat Catat Harian
            SettingsItemRow(
                title = "Pengingat Harian",
                description = "Notifikasi setiap pukul $dailyReminderTime agar rutin mencatat mutasi kas",
                icon = Icons.Rounded.NotificationsActive,
                iconTint = if (isDailyReminderEnabled) duit.primary else duit.textSecondary,
                iconBackground = if (isDailyReminderEnabled) duit.primaryContainer else duit.surfaceVariant,
                control = {
                    Switch(
                        checked = isDailyReminderEnabled,
                        onCheckedChange = { onDailyReminderToggled(it) },
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

            // Row 2: Peringatan Batas Anggaran
            SettingsItemRow(
                title = "Peringatan Batas Anggaran",
                description = "Notifikasi cerdas saat pengeluaran menyentuh 80% dan 100% dari batas",
                icon = Icons.Rounded.WarningAmber,
                iconTint = if (isBudgetAlertsEnabled) duit.warning else duit.textSecondary,
                iconBackground = duit.surfaceVariant,
                control = {
                    Switch(
                        checked = isBudgetAlertsEnabled,
                        onCheckedChange = { onBudgetAlertsToggled(it) },
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

            // Row 3: Rollover Sisa Anggaran
            SettingsItemRow(
                title = "Rollover Sisa Anggaran",
                description = "Alihkan sisa surplus anggaran bulan lalu ke batas bulan ini",
                icon = Icons.Rounded.Update,
                iconTint = if (isBudgetRolloverEnabled) duit.income else duit.textSecondary,
                iconBackground = duit.surfaceVariant,
                control = {
                    Switch(
                        checked = isBudgetRolloverEnabled,
                        onCheckedChange = { onBudgetRolloverToggled(it) },
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

            // Row 4: Rekening Kas Utama Default
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsItemRow(
                    title = "Rekening Utama Default",
                    description = "Rekening sumber otomatis saat membuka form transaksi baru",
                    icon = Icons.Rounded.AccountBalanceWallet,
                    iconTint = duit.textPrimary,
                    iconBackground = duit.surfaceVariant,
                    modifier = Modifier.padding(horizontal = 0.dp, vertical = 0.dp)
                )

                AssetDropdownField(
                    assets = activeAssets,
                    selectedAssetId = defaultAsset?.id ?: "",
                    onAssetSelected = onDefaultAssetSelected,
                    placeholder = "PILIH REKENING DEFAULT",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
