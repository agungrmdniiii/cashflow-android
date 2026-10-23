package com.cashflow.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import java.util.Locale
import com.cashflow.app.data.model.Asset
import com.cashflow.app.ui.components.AssetDropdownField
import com.cashflow.app.ui.components.DuitAingButton
import com.cashflow.app.ui.components.DuitAingButtonVariant
import com.cashflow.app.ui.theme.duit

@Composable
fun NotificationAccessSection(
    allowed: Boolean,
    blockedChannels: List<String>,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onTest: () -> Unit
) {
    SettingsSectionCard(title = "Izin Android") {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (allowed) "Notifikasi diizinkan" else "Notifikasi belum diizinkan",
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.duit.textPrimary)
            Text(when {
                !allowed -> "Izinkan agar pengingat harian, anggaran, dan cadangan dapat muncul. Penolakan tidak membatasi pencatatan."
                blockedChannels.isNotEmpty() -> "Kanal yang dinonaktifkan: ${blockedChannels.joinToString()}. Aktifkan melalui Pengaturan Android."
                else -> "Izin dan kanal aktif. Pengingat mengikuti jadwal Anda dan dapat ditunda Android saat hemat baterai."
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.duit.textSecondary)
            DuitAingButton(text = if (allowed) "PENGATURAN ANDROID" else "IZINKAN NOTIFIKASI",
                onClick = if (allowed) onOpenSettings else onRequest,
                variant = DuitAingButtonVariant.SECONDARY, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = onTest) { Text("Kirim notifikasi uji") }
        }
    }
}

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
    onDailyReminderTimeChanged: (String) -> Unit,
    onBudgetAlertsToggled: (Boolean) -> Unit,
    onBudgetRolloverToggled: (Boolean) -> Unit,
    onDefaultAssetSelected: (Asset) -> Unit,
    modifier: Modifier = Modifier
) {
    val duit = MaterialTheme.duit
    var showTimePicker by remember { mutableStateOf(false) }
    if (showTimePicker) {
        val initial = runCatching { LocalTime.parse(dailyReminderTime) }.getOrDefault(LocalTime.of(20, 0))
        var hour by remember { mutableIntStateOf(initial.hour) }
        var minute by remember { mutableIntStateOf(initial.minute) }
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = duit.surface,
            titleContentColor = duit.textPrimary,
            title = { Text("Jam pengingat") },
            text = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("Jam" to 24, "Menit" to 60).forEachIndexed { index, (label, count) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(label, style = MaterialTheme.typography.bodySmall, color = duit.textSecondary)
                            Box(Modifier.fillMaxWidth().height(144.dp)) {
                                Box(Modifier.align(Alignment.Center).fillMaxWidth().height(48.dp)
                                    .clip(RoundedCornerShape(12.dp)).background(duit.surfaceVariant))
                                AndroidView(modifier = Modifier.matchParentSize(), factory = { context ->
                                    val theme = if (duit.surface.luminance() < 0.5f) android.R.style.Theme_Material_NoActionBar
                                        else android.R.style.Theme_Material_Light_NoActionBar
                                    NumberPicker(ContextThemeWrapper(context, theme)).apply {
                                        minValue = 0
                                        maxValue = count - 1
                                        displayedValues = Array(count) { String.format(Locale.ROOT, "%02d", it) }
                                        value = if (index == 0) hour else minute
                                        wrapSelectorWheel = true
                                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                                        contentDescription = if (index == 0) "Pilih jam" else "Pilih menit"
                                        if (Build.VERSION.SDK_INT >= 29) {
                                            setTextColor(duit.textPrimary.toArgb())
                                            setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 26f, resources.displayMetrics))
                                            selectionDividerHeight = 0
                                        }
                                        setOnValueChangedListener { _, _, selected ->
                                            if (index == 0) hour = selected else minute = selected
                                        }
                                    }
                                })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onDailyReminderTimeChanged(String.format(Locale.ROOT, "%02d:%02d", hour, minute))
                    showTimePicker = false
                }) { Text("Simpan jam") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Batal") } }
        )
    }

    SettingsSectionCard(
        title = "Notifikasi & Anggaran",
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Row 1: Pengingat Catat Harian
            SettingsItemRow(
                title = "Pengingat Harian",
                description = if (isDailyReminderEnabled) "Ingatkan untuk mencatat transaksi setiap hari" else "Aktifkan untuk mengingat pencatatan harian",
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

            SettingsItemRow(
                title = "Jam pengingat",
                description = "$dailyReminderTime · setiap hari",
                icon = Icons.Rounded.Schedule,
                iconTint = duit.textPrimary,
                iconBackground = duit.surfaceVariant,
                onClick = { showTimePicker = true },
                control = { TextButton(onClick = { showTimePicker = true }) { Text("Ubah jam") } }
            )
            Text("Waktu perkiraan; dapat ditunda Android saat hemat baterai.",
                style = MaterialTheme.typography.bodySmall, color = duit.textSecondary,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp))

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
