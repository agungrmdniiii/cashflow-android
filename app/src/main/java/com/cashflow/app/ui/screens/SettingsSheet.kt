package com.cashflow.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cashflow.app.data.model.ThemeMode
import com.cashflow.app.security.BiometricHelper
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.components.AssetDropdownField
import com.cashflow.app.ui.components.DuitAingButton
import com.cashflow.app.ui.components.DuitAingButtonVariant
import com.cashflow.app.ui.components.DuitAingLogo
import com.cashflow.app.ui.components.LogoVariant
import com.cashflow.app.ui.theme.*

@Composable
fun SettingsSheet(
    viewModel: CashflowViewModel,
    onOpenOnboarding: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activeAssets by viewModel.activeAssets.collectAsState()
    val defaultAsset by viewModel.defaultAsset.collectAsState()
    val currentThemeMode by viewModel.themeMode.collectAsState()
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val isBiometricAvailable = remember(context) { BiometricHelper.isBiometricAvailable(context) }

    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var exportedCsvText by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.20f)),
            colors = CardDefaults.cardColors(containerColor = PebbleSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "PENGATURAN",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            ),
                            color = TextPrimary
                        )
                        Text(
                            text = "KONFIGURASI SISTEM & PREFERENSI",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = TextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable(
                                role = Role.Button,
                                onClick = onDismiss
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Rounded.Close, contentDescription = "Tutup", tint = TextPrimary, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Duit Aing Brand Card with Onboarding launcher
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurface)
                            .border(1.dp, DarkBorder.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                DuitAingLogo(size = 40.dp, variant = LogoVariant.ON_DARK)
                                Column {
                                    Text(
                                        text = "DUIT AING",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 1.2.sp
                                        ),
                                        color = TextOnDark
                                    )
                                    Text(
                                        text = "v1.0.0 · JADE PEBBLE MORNING",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp,
                                            fontSize = 10.sp
                                        ),
                                        color = JadePrimaryLight
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Aplikasi personal cashflow Android-first dengan rekaman suara cepat, arsitektur offline-first, dan kontrol privasi 100% di perangkat Anda.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                ),
                                color = PebbleSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            DuitAingButton(
                                text = "BACA PANDUAN APLIKASI",
                                onClick = {
                                    onDismiss()
                                    onOpenOnboarding()
                                },
                                trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                                modifier = Modifier.fillMaxWidth(),
                                variant = DuitAingButtonVariant.SECONDARY,
                                height = 40.dp,
                                shadowOffset = 2.dp
                            )
                        }
                    }

                    // 2. Tema Tampilan (Dark Mode, Light Mode, System)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "TEMA TAMPILAN",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            ),
                            color = JadePrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Sesuaikan kontras dan tema visual sesuai kenyamanan mata.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(PebbleSurfaceVariant)
                                .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
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
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) JadePrimary else Color.Transparent)
                                        .then(
                                            if (isSelected) Modifier.border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                                            else Modifier
                                        )
                                        .clickable(role = Role.RadioButton) {
                                            viewModel.setThemeMode(mode)
                                            viewModel.showSnackbar("Tema diubah ke mode $title")
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (isSelected) PebbleSurface else TextPrimary
                                        )
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                            ),
                                            color = if (isSelected) PebbleSurface else TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Keamanan Biometrik
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "KEAMANAN APLIKASI",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            ),
                            color = JadePrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(PebbleSurface)
                                .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (isBiometricEnabled) JadePrimaryContainer else PebbleSurfaceVariant)
                                        .border(1.dp, DarkBorder.copy(alpha = 0.25f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Fingerprint,
                                        contentDescription = null,
                                        tint = if (isBiometricEnabled) JadePrimary else TextSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Kunci Biometrik",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = if (isBiometricAvailable) {
                                            if (isBiometricEnabled) "Aktif: Wajib sidik jari/PIN saat buka"
                                            else "Nonaktif: Masuk langsung tanpa kunci"
                                        } else "Biometrik/PIN belum didaftarkan di HP",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = if (!isBiometricAvailable) AccentAmber else TextSecondary
                                    )
                                }
                            }

                            Switch(
                                checked = isBiometricEnabled,
                                onCheckedChange = { checked ->
                                    if (isBiometricAvailable) {
                                        viewModel.setBiometricEnabled(checked)
                                        viewModel.showSnackbar(
                                            if (checked) "Kunci biometrik diaktifkan" else "Kunci biometrik dinonaktifkan"
                                        )
                                    } else {
                                        viewModel.showSnackbar("Biometrik atau PIN belum disetel di pengaturan perangkat Anda")
                                    }
                                },
                                enabled = isBiometricAvailable,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = PebbleSurface,
                                    checkedTrackColor = JadePrimary,
                                    uncheckedThumbColor = TextSecondary,
                                    uncheckedTrackColor = PebbleSurfaceVariant
                                )
                            )
                        }
                    }

                    // 4. Widget Sat-Set & Pintasan
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "PINTASAN & WIDGET SAT-SET",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            ),
                            color = JadePrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Catat kas secepat kilat langsung dari layar utama ponsel Anda.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(PebbleSurfaceVariant.copy(alpha = 0.5f))
                                .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Widgets,
                                        contentDescription = null,
                                        tint = JadePrimary,
                                        modifier = Modifier.size(20.dp).padding(top = 2.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Widget Layar Utama (Home Screen)",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "Tahan layar beranda Android Anda -> pilih Widget -> 'Duit Aing' untuk tombol cepat Rekam Suara & Catat Kas.",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                                            color = TextSecondary
                                        )
                                    }
                                }

                                HorizontalDivider(color = DarkBorder.copy(alpha = 0.12f), thickness = 1.dp)

                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.TouchApp,
                                        contentDescription = null,
                                        tint = JadePrimary,
                                        modifier = Modifier.size(20.dp).padding(top = 2.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Pintasan Cepat Ikon Aplikasi",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "Tekan lama ikon aplikasi Duit Aing untuk memunculkan pintasan langsung tanpa membuka aplikasi dulu.",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 5. Financial Preferences Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "PREFERENSI FINANSIAL",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            ),
                            color = JadePrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        SettingItemRow(label = "MATA UANG", value = "IDR (Rupiah)")
                        HorizontalDivider(color = DarkBorder.copy(alpha = 0.12f), thickness = 1.dp)
                        SettingItemRow(label = "FORMAT TANGGAL", value = "dd MMMM yyyy")
                        HorizontalDivider(color = DarkBorder.copy(alpha = 0.12f), thickness = 1.dp)
                        SettingItemRow(
                            label = "ASET UTAMA",
                            value = defaultAsset?.name ?: "Belum dipilih"
                        )
                    }

                    // 3. Default Asset Selector
                    Column {
                        Text(
                            text = "PILIH ASET UTAMA DEFAULT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            ),
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        AssetDropdownField(
                            assets = activeAssets,
                            selectedAssetId = defaultAsset?.id ?: "",
                            onAssetSelected = { viewModel.setDefaultAsset(it.id) },
                            placeholder = "PILIH ASET DEFAULT",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 4. Export Data Section (Section 45)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "EKSPOR & CADANGAN DATA",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            ),
                            color = DarkSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Ekspor seluruh transaksi Anda ke format CSV standar untuk analisis spreadsheet atau backup mandiri.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        var isExportingAll by remember { mutableStateOf(false) }
                        var isExportingFiltered by remember { mutableStateOf(false) }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DuitAingButton(
                                text = "EKSPOR SEMUA DATA (CSV)",
                                onClick = {
                                    isExportingAll = true
                                    viewModel.exportCsvFile(
                                        context = context,
                                        filteredOnly = false,
                                        onSuccess = { file, sendIntent, content ->
                                            isExportingAll = false
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Cashflow Export CSV", content)
                                            clipboard.setPrimaryClip(clip)
                                            try {
                                                context.startActivity(Intent.createChooser(sendIntent, "Bagikan / Simpan File CSV"))
                                            } catch (_: Exception) {
                                                // Fallback
                                            }
                                            viewModel.showSnackbar("CSV berhasil dibuat: ${file.name}")
                                        },
                                        onError = {
                                            isExportingAll = false
                                            viewModel.showSnackbar("CSV gagal dibuat. Coba lagi.")
                                        }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = Icons.Rounded.Download,
                                variant = DuitAingButtonVariant.PRIMARY,
                                isLoading = isExportingAll,
                                height = 46.dp,
                                shadowOffset = 2.dp
                            )

                            DuitAingButton(
                                text = "EKSPOR DATA TERFILTER (CSV)",
                                onClick = {
                                    isExportingFiltered = true
                                    viewModel.exportCsvFile(
                                        context = context,
                                        filteredOnly = true,
                                        onSuccess = { file, sendIntent, content ->
                                            isExportingFiltered = false
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Cashflow Export CSV", content)
                                            clipboard.setPrimaryClip(clip)
                                            try {
                                                context.startActivity(Intent.createChooser(sendIntent, "Bagikan / Simpan File CSV"))
                                            } catch (_: Exception) {
                                                // Fallback
                                            }
                                            viewModel.showSnackbar("CSV berhasil dibuat: ${file.name}")
                                        },
                                        onError = {
                                            isExportingFiltered = false
                                            viewModel.showSnackbar("CSV gagal dibuat. Coba lagi.")
                                        }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = Icons.Rounded.FilterList,
                                variant = DuitAingButtonVariant.OUTLINE,
                                isLoading = isExportingFiltered,
                                height = 46.dp,
                                shadowOffset = 2.dp
                            )
                        }
                    }

                    // 5. Reset Data Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "RESET DATA APLIKASI",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            ),
                            color = StatusNegative
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Kembalikan database aplikasi ke sampel demo awal.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        DuitAingButton(
                            text = "RESET KE DATA DEMO",
                            onClick = { showResetConfirmDialog = true },
                            variant = DuitAingButtonVariant.DANGER,
                            height = 40.dp,
                            shadowOffset = 2.dp
                        )
                    }

                    // 6. App Information Footer
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "DUIT AING v1.0.0",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = TextTertiary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Dirancang untuk Android Native dengan arsitektur Offline-First",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                            color = TextTertiary
                        )
                    }
                }
            }
        }
    }

    // Reset Confirm Dialog (Neo-Brutalist Frame)
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(12.dp)),
            containerColor = PebbleSurface,
            title = {
                Text(
                    text = "HAPUS SEMUA DATA?",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    ),
                    color = StatusNegative
                )
            },
            text = {
                Text(
                    text = "Data transaksi, aset, dan anggaran yang telah Anda catat akan dihapus dan dikembalikan ke sampel awal. Tindakan ini tidak dapat dibatalkan.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = DarkSurface
                )
            },
            confirmButton = {
                DuitAingButton(
                    text = "HAPUS DATA",
                    onClick = {
                        viewModel.resetData()
                        showResetConfirmDialog = false
                    },
                    variant = DuitAingButtonVariant.DESTRUCTIVE,
                    height = 42.dp,
                    shadowOffset = 2.dp
                )
            },
            dismissButton = {
                DuitAingButton(
                    text = "BATAL",
                    onClick = { showResetConfirmDialog = false },
                    variant = DuitAingButtonVariant.OUTLINE,
                    height = 42.dp,
                    shadowOffset = 2.dp
                )
            }
        )
    }
}

@Composable
private fun SettingItemRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            ),
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
        )
    }
}
