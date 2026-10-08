package com.cashflow.app.ui.screens

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cashflow.app.data.model.BiometricTimeout
import com.cashflow.app.data.model.ThemeMode
import com.cashflow.app.security.BiometricHelper
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.components.AssetDropdownField
import com.cashflow.app.ui.components.DuitAingButton
import com.cashflow.app.ui.components.DuitAingButtonVariant
import com.cashflow.app.ui.components.DuitAingConfirmDialog
import com.cashflow.app.ui.components.DuitAingLogo
import com.cashflow.app.ui.components.ExportDataDialog
import com.cashflow.app.ui.components.LogoVariant
import com.cashflow.app.ui.theme.*

/**
 * Clean, Unified Neo-Brutalist Settings Sheet for "Duit Aing"
 * Rhythmic grouping, dark-mode adaptive contrast, and session security configuration.
 */
@Composable
fun SettingsSheet(
    viewModel: CashflowViewModel,
    onOpenOnboarding: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current
    val activeAssets by viewModel.activeAssets.collectAsState()
    val defaultAsset by viewModel.defaultAsset.collectAsState()
    val currentThemeMode by viewModel.themeMode.collectAsState()
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val biometricTimeout by viewModel.biometricTimeout.collectAsState()
    val isBiometricAvailable = remember(context) { BiometricHelper.isBiometricAvailable(context) }

    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, DarkBorder),
            colors = CardDefaults.cardColors(containerColor = PebbleSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "PENGATURAN",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp,
                                fontSize = 20.sp
                            ),
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "KONFIGURASI SISTEM & PREFERENSI",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                fontSize = 10.sp
                            ),
                            color = TextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(PebbleSurfaceVariant)
                            .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button, onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Tutup Pengaturan",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // 1. Duit Aing Hero Brand Card (Always high-contrast dark surface in both light & dark mode)
                    val brandCardBg = if (isDark) Color(0xFF141A17) else LightDarkSurface
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(brandCardBg)
                            .border(1.5.dp, DarkBorder.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
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
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "v1.0.1 · JADE PEBBLE MORNING",
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
                                text = "Aplikasi personal cashflow Android-first dengan arsitektur offline-first dan privasi 100% lokal di perangkat Anda.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                ),
                                color = TextOnDarkSecondary
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

                    // 2. Section: TEMA TAMPILAN
                    SettingsSection(
                        title = "TEMA TAMPILAN",
                        subtitle = "Pilih skema visual sesuai kenyamanan mata."
                    ) {
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
                                val itemBg by animateColorAsState(
                                    targetValue = if (isSelected) JadePrimary else Color.Transparent,
                                    animationSpec = tween(180),
                                    label = "theme_bg"
                                )
                                val itemTextCol by animateColorAsState(
                                    targetValue = if (isSelected) (if (isDark) Color(0xFF13271E) else TextOnDark) else TextPrimary,
                                    animationSpec = tween(180),
                                    label = "theme_text"
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(itemBg)
                                        .then(
                                            if (isSelected) Modifier.border(1.dp, DarkBorder.copy(alpha = 0.30f), RoundedCornerShape(8.dp))
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
                                            tint = itemTextCol
                                        )
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                            ),
                                            color = itemTextCol
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Section: KEAMANAN & PRIVASI (With Biometric Session Handling)
                    SettingsSection(
                        title = "KEAMANAN BIOMETRIK",
                        subtitle = "Kunci aplikasi dengan sidik jari atau PIN perangkat."
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(PebbleSurface)
                                .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
                                .padding(14.dp)
                        ) {
                            // Main Biometric Toggle Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                                                if (isBiometricEnabled) "Aktif: Wajib sidik jari/PIN"
                                                else "Nonaktif: Masuk langsung tanpa kunci"
                                            } else "Biometrik belum disetel di HP",
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

                            // Biometric Session Timeout Selector (Shown only when Biometric is enabled)
                            if (isBiometricEnabled) {
                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = DarkBorder.copy(alpha = 0.12f), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "BATAS WAKTU SESI KUNCI",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.8.sp,
                                        fontSize = 10.5.sp
                                    ),
                                    color = JadePrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Kunci otomatis saat aplikasi ditinggalkan lebih dari durasi ini:",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val timeouts = listOf(
                                        BiometricTimeout.IMMEDIATE,
                                        BiometricTimeout.ONE_MINUTE,
                                        BiometricTimeout.FIVE_MINUTES,
                                        BiometricTimeout.FIFTEEN_MINUTES
                                    )

                                    timeouts.forEach { timeout ->
                                        val isTimeoutSelected = biometricTimeout == timeout
                                        val chipBg by animateColorAsState(
                                            targetValue = if (isTimeoutSelected) JadePrimary else PebbleSurfaceVariant,
                                            animationSpec = tween(180),
                                            label = "timeout_bg"
                                        )
                                        val chipTextCol by animateColorAsState(
                                            targetValue = if (isTimeoutSelected) (if (isDark) Color(0xFF13271E) else TextOnDark) else TextPrimary,
                                            animationSpec = tween(180),
                                            label = "timeout_text"
                                        )

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .defaultMinSize(minHeight = 38.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(chipBg)
                                                .border(
                                                    1.dp,
                                                    if (isTimeoutSelected) (if (isDark) JadePrimaryDark else DarkBorder.copy(alpha = 0.35f))
                                                    else DarkBorder.copy(alpha = 0.18f),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable(role = Role.RadioButton) {
                                                    viewModel.setBiometricTimeout(timeout)
                                                    viewModel.showSnackbar("Sesi kunci diubah ke: ${timeout.displayName}")
                                                }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = timeout.displayName.uppercase(),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = if (isTimeoutSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                                    fontSize = 10.5.sp
                                                ),
                                                color = chipTextCol,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Section: PREFERENSI KEUANGAN
                    SettingsSection(
                        title = "PREFERENSI KEUANGAN",
                        subtitle = "Rekening utama dan format tampilan nilai kas."
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(PebbleSurface)
                                .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "ASET UTAMA DEFAULT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp,
                                    fontSize = 10.5.sp
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

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = DarkBorder.copy(alpha = 0.12f), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(6.dp))

                            SettingItemRow(label = "MATA UANG", value = "IDR (Rupiah)")
                            HorizontalDivider(color = DarkBorder.copy(alpha = 0.12f), thickness = 1.dp)
                            SettingItemRow(label = "FORMAT TANGGAL", value = "dd MMMM yyyy")
                        }
                    }

                    // 5. Section: DATA & CADANGAN
                    SettingsSection(
                        title = "DATA & CADANGAN",
                        subtitle = "Ekspor berkas kas atau bersihkan basis data."
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(PebbleSurface)
                                .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Ekspor seluruh catatan kas ke Dokumen PDF resmi (tabel rapi & ringkasan) atau Spreadsheet CSV untuk Excel/Sheets.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
                                color = TextSecondary
                            )

                            DuitAingButton(
                                text = "EKSPOR LAPORAN (PDF / CSV)",
                                onClick = { showExportDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = Icons.Rounded.Download,
                                variant = DuitAingButtonVariant.PRIMARY,
                                height = 44.dp,
                                shadowOffset = 2.dp
                            )

                            HorizontalDivider(color = DarkBorder.copy(alpha = 0.12f), thickness = 1.dp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                    Text(
                                        text = "RESET KE DATA DEMO",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 0.8.sp,
                                            fontSize = 10.5.sp
                                        ),
                                        color = StatusNegative
                                    )
                                    Text(
                                        text = "Kembalikan saldo dan transaksi ke sampel awal.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = TextSecondary
                                    )
                                }

                                DuitAingButton(
                                    text = "RESET DATA",
                                    onClick = { showResetConfirmDialog = true },
                                    variant = DuitAingButtonVariant.DANGER,
                                    height = 38.dp,
                                    shadowOffset = 2.dp
                                )
                            }
                        }
                    }

                    // 6. Section: PINTASAN & WIDGET ANDROID
                    SettingsSection(
                        title = "PINTASAN & WIDGET ANDROID",
                        subtitle = "Catat kas cepat tanpa membuka aplikasi utama."
                    ) {
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
                                            text = "Tahan layar beranda Android -> pilih Widget -> 'Duit Aing' untuk tombol cepat Rekam Suara & Catat Kas.",
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
                                            text = "Pintasan Ikon Aplikasi",
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

                    // 7. App Info Footer
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "DUIT AING v1.0.1 · OFFLINE-FIRST",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = TextTertiary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Privasi 100% terjaga. Data tersimpan aman di ponsel Anda.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                            color = TextTertiary
                        )
                    }
                }
            }
        }
    }

    // Reset Confirmation Dialog
    DuitAingConfirmDialog(
        isOpen = showResetConfirmDialog,
        title = "HAPUS SEMUA DATA?",
        message = "Data transaksi, aset, dan anggaran yang telah Anda catat akan dihapus dan dikembalikan ke sampel awal. Tindakan ini tidak dapat dibatalkan.",
        confirmText = "HAPUS DATA",
        confirmVariant = DuitAingButtonVariant.DANGER,
        onConfirm = {
            viewModel.resetData()
            showResetConfirmDialog = false
        },
        onDismiss = { showResetConfirmDialog = false }
    )

    // Standardized Export Dialog (PDF & CSV)
    ExportDataDialog(
        isOpen = showExportDialog,
        viewModel = viewModel,
        hasActiveFilter = false,
        onDismiss = { showExportDialog = false }
    )
}

@Composable
private fun SettingsSection(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    fontSize = 11.sp
                ),
                color = JadePrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = TextSecondary
            )
        }
        content()
    }
}

@Composable
private fun SettingItemRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
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
