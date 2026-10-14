package com.cashflow.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.style.TextOverflow
import com.cashflow.app.data.backup.BackupSummary
import com.cashflow.app.data.backup.BackupValidationResult
import com.cashflow.app.data.model.BiometricTimeout
import com.cashflow.app.data.model.FavoriteTemplate
import com.cashflow.app.data.model.ThemeMode
import com.cashflow.app.data.model.TransactionType
import com.cashflow.app.security.BiometricHelper
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.components.*
import com.cashflow.app.ui.theme.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    // Backup & Data management states
    val lastBackupTime by viewModel.lastBackupTime.collectAsState()
    val isBackupReminderEnabled by viewModel.isBackupReminderEnabled.collectAsState()
    val isBackupOverdue by viewModel.isBackupOverdue.collectAsState()
    val hasSampleData by viewModel.hasSampleData.collectAsState()

    val currentTransactions by viewModel.transactions.collectAsState()
    val currentAssets by viewModel.assets.collectAsState()
    val currentDebts by viewModel.debts.collectAsState()
    val currentGoals by viewModel.goals.collectAsState()
    val currentBudgets by viewModel.budgets.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val favoriteTemplates by viewModel.favoriteTemplates.collectAsState()
    val isFavoriteDirectSave by viewModel.isFavoriteDirectSave.collectAsState()
    val recurringTransactions by viewModel.recurringTransactions.collectAsState()
    val isBudgetAlertsEnabled by viewModel.isBudgetAlertsEnabled.collectAsState()
    val isDailyReminderEnabled by viewModel.isDailyReminderEnabled.collectAsState()
    val dailyReminderTime by viewModel.dailyReminderTime.collectAsState()
    val isBudgetRolloverEnabled by viewModel.isBudgetRolloverEnabled.collectAsState()
    val geminiApiKey by viewModel.geminiApiKey.collectAsState()

    var showExportDialog by remember { mutableStateOf(false) }
    var showEncryptExportDialog by remember { mutableStateOf(false) }
    var encryptPasswordInput by remember { mutableStateOf("") }
    var showEncryptRestoreDialog by remember { mutableStateOf(false) }
    var decryptPasswordInput by remember { mutableStateOf("") }
    var selectedEncryptedFile by remember { mutableStateOf<java.io.File?>(null) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var tempApiKeyInput by remember { mutableStateOf(geminiApiKey ?: "") }

    // Restore States
    var validatedBackup by remember { mutableStateOf<BackupValidationResult.Valid?>(null) }
    var restoreErrorReason by remember { mutableStateOf<String?>(null) }
    var restoreInputConfirmation by remember { mutableStateOf("") }

    // Delete All Data Flow States (Step 1: Offer backup, Step 2: Confirmation input)
    var showDeleteAllStep1Dialog by remember { mutableStateOf(false) }
    var showDeleteAllStep2Dialog by remember { mutableStateOf(false) }
    var deleteInputConfirmation by remember { mutableStateOf("") }
    var showDeleteSampleDialog by remember { mutableStateOf(false) }

    val restoreFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                val jsonContent = reader.readText()
                reader.close()

                val validation = viewModel.validateBackupJson(jsonContent)
                when (validation) {
                    is BackupValidationResult.Valid -> {
                        validatedBackup = validation
                        restoreInputConfirmation = ""
                    }
                    is BackupValidationResult.Invalid -> {
                        restoreErrorReason = validation.reason
                    }
                }
            } catch (e: Exception) {
                restoreErrorReason = "Gagal membaca berkas: ${e.message}"
            }
        }
    }

    val restoreEncryptedLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val cacheFile = java.io.File(context.cacheDir, "temp_restore.enc")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                }
                selectedEncryptedFile = cacheFile
                decryptPasswordInput = ""
                showEncryptRestoreDialog = true
            } catch (e: Exception) {
                viewModel.showSnackbar("Gagal membuka berkas: ${e.message}")
            }
        }
    }

    val restoreZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val cacheFile = java.io.File(context.cacheDir, "temp_restore.zip")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                }
                viewModel.restoreZipBackupFile(cacheFile) { res ->
                    res.onSuccess { summary ->
                        viewModel.showSnackbar("Berhasil memulihkan cadangan ZIP (${summary.transactionsCount} transaksi)")
                    }.onFailure { err ->
                        viewModel.showSnackbar("Gagal memulihkan ZIP: ${err.message}")
                    }
                }
            } catch (e: Exception) {
                viewModel.showSnackbar("Gagal membuka berkas ZIP: ${e.message}")
            }
        }
    }

    DuitAingModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .navigationBarsPadding()
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

                            Spacer(modifier = Modifier.height(14.dp))

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
                        title = "TEMA TAMPILAN"
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(999.dp))
                                .background(PebbleSurfaceVariant)
                                .border(1.dp, PebbleBorder, RoundedCornerShape(999.dp))
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
                                    targetValue = if (isSelected) (if (isDark) FintechAccentLight else FintechPrimary) else Color.Transparent,
                                    animationSpec = tween(180),
                                    label = "theme_bg"
                                )
                                val itemTextCol by animateColorAsState(
                                    targetValue = if (isSelected) (if (isDark) PebbleBackgroundDark else TextOnDark) else TextPrimary,
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
                                            viewModel.setThemeMode(mode)
                                            viewModel.showSnackbar("Tema diubah ke mode $title")
                                        }
                                        .padding(vertical = 9.dp),
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
                        title = "KEAMANAN BIOMETRIK"
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
                        title = "PREFERENSI KEUANGAN"
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

                    // Section: TRANSAKSI FAVORIT
                    SettingsSection(
                        title = "TRANSAKSI FAVORIT",
                        subtitle = "Templat transaksi cepat dan preferensi pencatatan"
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(PebbleSurface)
                                .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Toggle 1-Tap Save vs Prefilled Form
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        text = "Simpan Langsung (1-Tap)",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isFavoriteDirectSave) "Tap favorit di Beranda langsung mencatat transaksi (dapat diurungkan 5 detik)."
                                               else "Tap favorit di Beranda membuka form dengan data terisi untuk diedit sebelum disimpan.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = TextSecondary
                                    )
                                }
                                Switch(
                                    checked = isFavoriteDirectSave,
                                    onCheckedChange = { checked ->
                                        viewModel.setFavoriteDirectSave(checked)
                                        viewModel.showSnackbar(
                                            if (checked) "Mode favorit: Simpan langsung aktif" else "Mode favorit: Buka form aktif"
                                        )
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = PebbleSurface,
                                        checkedTrackColor = JadePrimary,
                                        uncheckedThumbColor = TextSecondary,
                                        uncheckedTrackColor = PebbleSurfaceVariant
                                    )
                                )
                            }

                            HorizontalDivider(color = DarkBorder.copy(alpha = 0.12f), thickness = 1.dp)

                            // List of Saved Favorites
                            Text(
                                text = "DAFTAR TEMPLAT FAVORIT (${favoriteTemplates.size})",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp,
                                    fontSize = 10.5.sp
                                ),
                                color = JadePrimary
                            )

                            if (favoriteTemplates.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(PebbleSurfaceVariant.copy(alpha = 0.5f))
                                        .padding(14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Belum ada templat favorit.\nBuka detail transaksi apa saja lalu tap \"Simpan Sebagai Favorit\".",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = TextSecondary,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            lineHeight = 16.sp,
                                            fontSize = 11.5.sp
                                        )
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    favoriteTemplates.forEach { fav ->
                                        val asset = currentAssets.find { it.id == fav.assetId }
                                        val cat = categories.find { it.id == fav.categoryId }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(PebbleSurfaceVariant)
                                                .border(1.dp, DarkBorder.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    val (badgeBg, badgeColor, badgeText) = when (fav.type) {
                                                        TransactionType.INCOME.value -> Triple(StatusPositive.copy(alpha = 0.18f), StatusPositive, "MASUK")
                                                        TransactionType.TRANSFER.value -> Triple(StatusTransfer.copy(alpha = 0.18f), StatusTransfer, "TRANSFER")
                                                        else -> Triple(StatusNegative.copy(alpha = 0.18f), StatusNegative, "KELUAR")
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(badgeBg)
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = badgeText,
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.ExtraBold
                                                            ),
                                                            color = badgeColor
                                                        )
                                                    }
                                                    Text(
                                                        text = fav.name,
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = TextPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Text(
                                                    text = "${cat?.name ?: "Tanpa Kategori"} • ${asset?.name ?: "Aset"} • ${Formatters.formatRupiah(fav.amount)}",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                    color = TextSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            IconButton(
                                                onClick = { viewModel.deleteFavoriteTemplate(fav.id) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DeleteOutline,
                                                    contentDescription = "Hapus Favorit",
                                                    tint = StatusNegative.copy(alpha = 0.85f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section: TRANSAKSI BERULANG (4A)
                    SettingsSection(
                        title = "TRANSAKSI BERULANG",
                        subtitle = "Jadwal tagihan otomatis, pengingat & cicilan"
                    ) {
                        DuitAingButton(
                            text = "KELOLA TRANSAKSI BERULANG (${recurringTransactions.size})",
                            onClick = {
                                onDismiss()
                                viewModel.isRecurringScreenOpen.value = true
                            },
                            leadingIcon = Icons.Rounded.EventRepeat,
                            trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                            modifier = Modifier.fillMaxWidth(),
                            variant = DuitAingButtonVariant.SECONDARY,
                            height = 44.dp
                        )
                    }

                    // Section: NOTIFIKASI & ANGGARAN (4B)
                    SettingsSection(
                        title = "NOTIFIKASI & ANGGARAN",
                        subtitle = "Pengingat limit anggaran 80%/100% dan pencatatan harian"
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(PebbleSurface)
                                .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Budget Alert 80% & 100%
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        text = "Peringatan Batas Anggaran",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Notifikasi saat pengeluaran mencapai 80% dan 100% dari limit anggaran bulanan.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = TextSecondary
                                    )
                                }
                                Switch(
                                    checked = isBudgetAlertsEnabled,
                                    onCheckedChange = { viewModel.setBudgetAlertsEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = JadePrimary,
                                        checkedTrackColor = JadePrimaryLight
                                    )
                                )
                            }

                            HorizontalDivider(color = DarkBorder.copy(alpha = 0.12f), thickness = 1.dp)

                            // Rollover sisa anggaran
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        text = "Rollover Sisa Anggaran",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Sisa anggaran yang belum terpakai di bulan lalu otomatis ditambahkan ke batas anggaran bulan ini.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = TextSecondary
                                    )
                                }
                                Switch(
                                    checked = isBudgetRolloverEnabled,
                                    onCheckedChange = { viewModel.setBudgetRolloverEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = JadePrimary,
                                        checkedTrackColor = JadePrimaryLight
                                    )
                                )
                            }

                            HorizontalDivider(color = DarkBorder.copy(alpha = 0.12f), thickness = 1.dp)

                            // Pengingat Catat Harian
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        text = "Pengingat Catat Harian ($dailyReminderTime)",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Kirim notifikasi setiap malam agar tidak lupa mencatat pengeluaran hari ini.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = TextSecondary
                                    )
                                }
                                Switch(
                                    checked = isDailyReminderEnabled,
                                    onCheckedChange = { viewModel.setDailyReminderEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = JadePrimary,
                                        checkedTrackColor = JadePrimaryLight
                                    )
                                )
                            }
                        }
                    }

                    // 5. Section: DATA & CADANGAN
                    SettingsSection(
                        title = "DATA & CADANGAN",
                        subtitle = "Cadangan, pemulihan, rekonsiliasi saldo & manajemen data offline"
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(PebbleSurface)
                                .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Row Cadangan Terakhir
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(JadePrimaryLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Backup,
                                        contentDescription = null,
                                        tint = JadePrimaryDark,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "STATUS CADANGAN LOKAL",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 0.8.sp,
                                            fontSize = 10.sp
                                        ),
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = if (lastBackupTime > 0L) {
                                            "Terakhir: " + SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(lastBackupTime))
                                        } else {
                                            "Belum pernah dicadangkan"
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        ),
                                        color = TextPrimary
                                    )
                                }
                            }

                            // Overdue Notice jika > 7 hari
                            if (isBackupOverdue) {
                                val warnBg = if (isDark) Color(0xFF331A18) else StatusNegativeContainer.copy(alpha = 0.5f)
                                val warnText = if (isDark) Color(0xFFFFB4AB) else StatusNegative
                                val warnBorder = if (isDark) Color(0xFF5D2E2B) else StatusNegative.copy(alpha = 0.35f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(warnBg)
                                        .border(1.dp, warnBorder, RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Warning,
                                            contentDescription = null,
                                            tint = warnText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Sudah lebih dari 7 hari belum ada cadangan! Sangat disarankan untuk mencadangkan data sekarang.",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = warnText
                                        )
                                    }
                                }
                            }

                            // Tombol Cadangkan Data (JSON)
                            DuitAingButton(
                                text = "CADANGKAN DATA (JSON)",
                                onClick = {
                                    viewModel.exportBackupFile(
                                        context = context,
                                        onSuccess = { file, intent, _ ->
                                            val chooser = Intent.createChooser(intent, "Bagikan / Simpan Berkas Cadangan")
                                            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(chooser)
                                            viewModel.showSnackbar("Cadangan berhasil dibuat: ${file.name}")
                                        },
                                        onError = { err -> viewModel.showSnackbar(err) }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = Icons.Rounded.CloudUpload,
                                variant = DuitAingButtonVariant.PRIMARY,
                                height = 44.dp,
                                shadowOffset = 2.dp
                            )

                            // Tombol Pulihkan Data (JSON)
                            DuitAingButton(
                                text = "PULIHKAN DATA (JSON)",
                                onClick = {
                                    restoreFileLauncher.launch("*/*")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = Icons.Rounded.CloudDownload,
                                variant = DuitAingButtonVariant.SECONDARY,
                                height = 44.dp,
                                shadowOffset = 2.dp
                            )

                            HorizontalDivider(color = DarkBorder.copy(alpha = 0.12f), thickness = 1.dp)

                            // Cadangan Terenkripsi (.enc AES-256)
                            Text(
                                text = "CADANGAN TERENKRIPSI (AES-256-GCM)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp,
                                    fontSize = 10.sp
                                ),
                                color = JadePrimary
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DuitAingButton(
                                    text = "EKSPOR .ENC",
                                    onClick = {
                                        encryptPasswordInput = ""
                                        showEncryptExportDialog = true
                                    },
                                    leadingIcon = Icons.Rounded.Lock,
                                    variant = DuitAingButtonVariant.SECONDARY,
                                    height = 40.dp,
                                    modifier = Modifier.weight(1f)
                                )

                                DuitAingButton(
                                    text = "PULIHKAN .ENC",
                                    onClick = {
                                        restoreEncryptedLauncher.launch("*/*")
                                    },
                                    leadingIcon = Icons.Rounded.Key,
                                    variant = DuitAingButtonVariant.SECONDARY,
                                    height = 40.dp,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Cadangan ZIP Lengkap (dengan Foto Struk)
                            Text(
                                text = "CADANGAN LENGKAP ZIP (TERMASUK STRUK)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp,
                                    fontSize = 10.sp
                                ),
                                color = JadePrimary
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DuitAingButton(
                                    text = "EKSPOR ZIP",
                                    onClick = {
                                        viewModel.exportZipBackupFile { res ->
                                            res.onSuccess { file ->
                                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    file
                                                )
                                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "application/zip"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                val chooser = Intent.createChooser(sendIntent, "Bagikan Cadangan ZIP")
                                                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(chooser)
                                                viewModel.showSnackbar("Cadangan ZIP berhasil dibuat: ${file.name}")
                                            }.onFailure { err ->
                                                viewModel.showSnackbar("Gagal membuat ZIP: ${err.message}")
                                            }
                                        }
                                    },
                                    leadingIcon = Icons.Rounded.FolderZip,
                                    variant = DuitAingButtonVariant.SECONDARY,
                                    height = 40.dp,
                                    modifier = Modifier.weight(1f)
                                )

                                DuitAingButton(
                                    text = "PULIHKAN ZIP",
                                    onClick = {
                                        restoreZipLauncher.launch("application/zip")
                                    },
                                    leadingIcon = Icons.Rounded.Unarchive,
                                    variant = DuitAingButtonVariant.SECONDARY,
                                    height = 40.dp,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            HorizontalDivider(color = DarkBorder.copy(alpha = 0.12f), thickness = 1.dp)

                            // Pengingat Cadangan Mingguan Switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        text = "PENGINGAT CADANGAN MINGGUAN",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 0.8.sp,
                                            fontSize = 10.5.sp
                                        ),
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Ingatkan jika >7 hari belum mencadangkan data.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = TextSecondary
                                    )
                                }
                                Switch(
                                    checked = isBackupReminderEnabled,
                                    onCheckedChange = { viewModel.setBackupReminderEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = JadePrimary,
                                        checkedTrackColor = JadePrimaryLight
                                    )
                                )
                            }

                            HorizontalDivider(color = DarkBorder.copy(alpha = 0.12f), thickness = 1.dp)

                            // Hitung Ulang & Rekonsiliasi Saldo (Ledger)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                    Text(
                                        text = "REKONSILIASI SALDO (LEDGER)",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 0.8.sp,
                                            fontSize = 10.5.sp
                                        ),
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Hitung ulang saldo dari riwayat mutasi dan perbaiki selisih.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = TextSecondary
                                    )
                                }
                                DuitAingButton(
                                    text = "REKONSILIASI",
                                    onClick = { viewModel.recalculateBalances() },
                                    variant = DuitAingButtonVariant.SECONDARY,
                                    leadingIcon = Icons.Rounded.Sync,
                                    height = 38.dp,
                                    shadowOffset = 2.dp
                                )
                            }

                            HorizontalDivider(color = DarkBorder.copy(alpha = 0.12f), thickness = 1.dp)

                            // Ekspor Laporan PDF/CSV
                            DuitAingButton(
                                text = "EKSPOR LAPORAN (PDF / CSV)",
                                onClick = { showExportDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = Icons.Rounded.Download,
                                variant = DuitAingButtonVariant.SECONDARY,
                                height = 40.dp,
                                shadowOffset = 2.dp
                            )

                            // Hapus Data Contoh (Jika ada)
                            if (hasSampleData) {
                                HorizontalDivider(color = DarkBorder.copy(alpha = 0.12f), thickness = 1.dp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                        Text(
                                            text = "HAPUS DATA CONTOH",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                letterSpacing = 0.8.sp,
                                                fontSize = 10.5.sp
                                            ),
                                            color = StatusNegative
                                        )
                                        Text(
                                            text = "Hapus aset & transaksi contoh awal saja.",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = TextSecondary
                                        )
                                    }
                                    DuitAingButton(
                                        text = "HAPUS CONTOH",
                                        onClick = { showDeleteSampleDialog = true },
                                        variant = DuitAingButtonVariant.DANGER,
                                        height = 36.dp,
                                        shadowOffset = 2.dp
                                    )
                                }
                            }

                            HorizontalDivider(color = DarkBorder.copy(alpha = 0.12f), thickness = 1.dp)

                            // Hapus Semua Data
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                    Text(
                                        text = "HAPUS SEMUA DATA",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 0.8.sp,
                                            fontSize = 10.5.sp
                                        ),
                                        color = StatusNegative
                                    )
                                    Text(
                                        text = "Hapus permanen seluruh aset dan transaksi.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = TextSecondary
                                    )
                                }
                                DuitAingButton(
                                    text = "HAPUS DATA",
                                    onClick = { showDeleteAllStep1Dialog = true },
                                    variant = DuitAingButtonVariant.DANGER,
                                    height = 38.dp,
                                    shadowOffset = 2.dp
                                )
                            }
                        }
                    }

                    // Section: INTEGRASI AI (4D)
                    SettingsSection(
                        title = "INTEGRASI GEMINI AI",
                        subtitle = "Insight finansial cerdas dengan Google Gemini (Privasi: Hanya Agregat)"
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                    Text(
                                        text = "Google Gemini API Key",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (geminiApiKey.isNullOrBlank()) "Belum disetel (hanya insight lokal aturan baku)"
                                               else "Tersimpan aman & terenkripsi di perangkat",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = if (geminiApiKey.isNullOrBlank()) TextSecondary else JadePrimary
                                    )
                                }
                                DuitAingButton(
                                    text = if (geminiApiKey.isNullOrBlank()) "SETEL" else "UBAH",
                                    onClick = {
                                        tempApiKeyInput = geminiApiKey ?: ""
                                        showApiKeyDialog = true
                                    },
                                    variant = DuitAingButtonVariant.SECONDARY,
                                    height = 36.dp
                                )
                            }
                        }
                    }

                    // 6. Section: PINTASAN & WIDGET ANDROID
                    SettingsSection(
                        title = "PINTASAN & WIDGET ANDROID"
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
                                            text = "Widget Layar Utama",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "Pintasan Rekam Suara & Catat Kas di beranda.",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
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
                                            text = "Akses cepat saat menekan lama ikon aplikasi.",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
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
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "DUIT AING v1.0.1 · 100% OFFLINE-FIRST",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = TextTertiary
                        )
                    }
                }
            }
        }

    // 1. Restore Data Preview Dialog
    validatedBackup?.let { valid ->
        DuitAingModalBottomSheet(
            onDismissRequest = {
                validatedBackup = null
                restoreInputConfirmation = ""
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "PULIHKAN DATA CADANGAN",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp,
                        fontSize = 20.sp
                    ),
                    color = TextPrimary
                )

                val restoreWarnBg = if (isDark) Color(0xFF331A18) else StatusNegativeContainer.copy(alpha = 0.5f)
                val restoreWarnText = if (isDark) Color(0xFFFFB4AB) else StatusNegative
                val restoreWarnBorder = if (isDark) Color(0xFF5D2E2B) else StatusNegative.copy(alpha = 0.4f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(restoreWarnBg)
                        .border(1.dp, restoreWarnBorder, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "PERINGATAN: Memulihkan cadangan akan menimpa data yang ada saat ini. Cadangan pengaman otomatis dari data saat ini akan dibuat sebelum pemulihan.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = restoreWarnText
                    )
                }

                // Metadata Details
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(PebbleSurface)
                        .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SettingItemRow("WAKTU CADANGAN", valid.summary.backupDate)
                    HorizontalDivider(color = DarkBorder.copy(alpha = 0.10f), thickness = 1.dp)
                    SettingItemRow("TRANSAKSI", "${valid.summary.transactionCount} data (saat ini: ${currentTransactions.size})")
                    HorizontalDivider(color = DarkBorder.copy(alpha = 0.10f), thickness = 1.dp)
                    SettingItemRow("ASET & REKENING", "${valid.summary.assetCount} aset (saat ini: ${currentAssets.size})")
                    HorizontalDivider(color = DarkBorder.copy(alpha = 0.10f), thickness = 1.dp)
                    SettingItemRow("TARGET KEUANGAN", "${valid.summary.goalCount} target (saat ini: ${currentGoals.size})")
                    HorizontalDivider(color = DarkBorder.copy(alpha = 0.10f), thickness = 1.dp)
                    SettingItemRow("CATATAN HUTANG", "${valid.summary.debtCount} hutang (saat ini: ${currentDebts.size})")
                    HorizontalDivider(color = DarkBorder.copy(alpha = 0.10f), thickness = 1.dp)
                    SettingItemRow("CHECKSUM SHA-256", "Terverifikasi ✓")
                }

                Text(
                    text = "Ketik 'PULIHKAN' untuk melanjutkan proses:",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = restoreInputConfirmation,
                    onValueChange = { restoreInputConfirmation = it },
                    placeholder = { Text("Ketik PULIHKAN") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DuitAingButton(
                        text = "BATAL",
                        onClick = {
                            validatedBackup = null
                            restoreInputConfirmation = ""
                        },
                        modifier = Modifier.weight(1f),
                        variant = DuitAingButtonVariant.SECONDARY
                    )

                    val isRestoreEnabled = restoreInputConfirmation.trim() == "PULIHKAN"
                    DuitAingButton(
                        text = "PULIHKAN DATA",
                        onClick = {
                            viewModel.restoreBackup(valid.parsedData) {
                                validatedBackup = null
                                restoreInputConfirmation = ""
                            }
                        },
                        modifier = Modifier.weight(1.3f),
                        enabled = isRestoreEnabled,
                        variant = DuitAingButtonVariant.DANGER
                    )
                }
            }
        }
    }

    // 2. Restore Error Dialog
    if (restoreErrorReason != null) {
        DuitAingConfirmDialog(
            isOpen = true,
            title = "BERKAS TIDAK VALID",
            message = restoreErrorReason ?: "Berkas cadangan rusak atau format tidak dikenali.",
            confirmText = "MENGERTI",
            confirmVariant = DuitAingButtonVariant.PRIMARY,
            dismissText = "",
            onConfirm = { restoreErrorReason = null },
            onDismiss = { restoreErrorReason = null }
        )
    }

    // 3. Delete All Step 1: Offer Backup Dialog
    DuitAingConfirmDialog(
        isOpen = showDeleteAllStep1Dialog,
        title = "CADANGKAN SEBELUM MENGHAPUS?",
        message = "Seluruh transaksi, aset, dan riwayat akan dihapus secara permanen. Apakah Anda ingin membuat cadangan data terlebih dahulu?",
        confirmText = "CADANGKAN DULU",
        confirmVariant = DuitAingButtonVariant.PRIMARY,
        dismissText = "LEWATI & LANJUT",
        onConfirm = {
            viewModel.exportBackupFile(
                context = context,
                onSuccess = { _, intent, _ ->
                    val chooser = Intent.createChooser(intent, "Simpan Berkas Cadangan")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                    showDeleteAllStep1Dialog = false
                    showDeleteAllStep2Dialog = true
                },
                onError = { err ->
                    viewModel.showSnackbar(err)
                    showDeleteAllStep1Dialog = false
                    showDeleteAllStep2Dialog = true
                }
            )
        },
        onDismiss = {
            showDeleteAllStep1Dialog = false
            showDeleteAllStep2Dialog = true
        }
    )

    // 4. Delete All Step 2: Confirm by typing "HAPUS"
    if (showDeleteAllStep2Dialog) {
        DuitAingModalBottomSheet(
            onDismissRequest = {
                showDeleteAllStep2Dialog = false
                deleteInputConfirmation = ""
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "KONFIRMASI HAPUS SEMUA DATA",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp,
                        fontSize = 20.sp
                    ),
                    color = StatusNegative
                )

                Text(
                    text = "Tindakan ini permanen dan tidak dapat dibatalkan. Semua transaksi, aset, target, hutang, dan anggaran Anda akan dihapus bersih.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ),
                    color = TextSecondary
                )

                Text(
                    text = "Ketik 'HAPUS' dengan huruf kapital di bawah untuk konfirmasi:",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )

                OutlinedTextField(
                    value = deleteInputConfirmation,
                    onValueChange = { deleteInputConfirmation = it },
                    placeholder = { Text("Ketik HAPUS") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DuitAingButton(
                        text = "BATAL",
                        onClick = {
                            showDeleteAllStep2Dialog = false
                            deleteInputConfirmation = ""
                        },
                        modifier = Modifier.weight(1f),
                        variant = DuitAingButtonVariant.SECONDARY
                    )

                    val isDeleteEnabled = deleteInputConfirmation.trim() == "HAPUS"
                    DuitAingButton(
                        text = "HAPUS SEMUA",
                        onClick = {
                            viewModel.deleteAllUserData {
                                showDeleteAllStep2Dialog = false
                                deleteInputConfirmation = ""
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1.3f),
                        enabled = isDeleteEnabled,
                        variant = DuitAingButtonVariant.DANGER
                    )
                }
            }
        }
    }

    // 5. Delete Sample Data Dialog
    DuitAingConfirmDialog(
        isOpen = showDeleteSampleDialog,
        title = "HAPUS DATA CONTOH?",
        message = "Aset dan transaksi bertanda sampel awal akan dihapus. Data dan transaksi pribadi yang Anda buat sendiri tetap aman.",
        confirmText = "HAPUS CONTOH",
        confirmVariant = DuitAingButtonVariant.DANGER,
        onConfirm = {
            viewModel.deleteSampleData()
            showDeleteSampleDialog = false
        },
        onDismiss = { showDeleteSampleDialog = false }
    )

    // Standardized Export Dialog (PDF & CSV)
    ExportDataDialog(
        isOpen = showExportDialog,
        viewModel = viewModel,
        hasActiveFilter = false,
        onDismiss = { showExportDialog = false }
    )

    // Export Encrypted Backup Dialog (4F)
    if (showEncryptExportDialog) {
        AlertDialog(
            onDismissRequest = { showEncryptExportDialog = false },
            title = {
                Text("Ekspor Cadangan Terenkripsi", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            },
            text = {
                Column {
                    Text(
                        text = "Data cadangan akan dienkripsi dengan standar militer AES-256-GCM dan PBKDF2 (100.000 iterasi). Masukkan kata sandi pengunci:",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.5.sp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = encryptPasswordInput,
                        onValueChange = { encryptPasswordInput = it },
                        label = { Text("Kata Sandi Enkripsi") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pwd = encryptPasswordInput.trim()
                        if (pwd.length < 4) {
                            viewModel.showSnackbar("Kata sandi minimal 4 karakter")
                            return@Button
                        }
                        showEncryptExportDialog = false
                        viewModel.exportEncryptedBackupFile(pwd) { res ->
                            res.onSuccess { file ->
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/octet-stream"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                val chooser = Intent.createChooser(sendIntent, "Simpan / Kirim Cadangan Terenkripsi (.enc)")
                                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(chooser)
                                viewModel.showSnackbar("Berkas terenkripsi dibuat: ${file.name}")
                            }.onFailure { err ->
                                viewModel.showSnackbar("Gagal membuat berkas terenkripsi: ${err.message}")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JadePrimary)
                ) {
                    Text("EKSPOR .ENC", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEncryptExportDialog = false }) {
                    Text("BATAL", color = TextSecondary)
                }
            }
        )
    }

    // Restore Encrypted Backup Dialog (4F)
    if (showEncryptRestoreDialog && selectedEncryptedFile != null) {
        AlertDialog(
            onDismissRequest = { showEncryptRestoreDialog = false },
            title = {
                Text("Buka Kunci Cadangan .enc", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            },
            text = {
                Column {
                    Text(
                        text = "Berkas: ${selectedEncryptedFile?.name}\nMasukkan kata sandi yang digunakan saat mengenkripsi berkas cadangan ini:",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.5.sp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = decryptPasswordInput,
                        onValueChange = { decryptPasswordInput = it },
                        label = { Text("Kata Sandi") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pwd = decryptPasswordInput.trim()
                        if (pwd.isEmpty()) {
                            viewModel.showSnackbar("Masukkan kata sandi")
                            return@Button
                        }
                        showEncryptRestoreDialog = false
                        viewModel.restoreEncryptedBackupFile(selectedEncryptedFile!!, pwd) { res ->
                            res.onSuccess { summary ->
                                viewModel.showSnackbar("Berhasil memulihkan ${summary.transactionsCount} transaksi dari cadangan terenkripsi!")
                            }.onFailure { err ->
                                viewModel.showSnackbar("Gagal: Kata sandi salah atau berkas rusak (${err.message})")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JadePrimary)
                ) {
                    Text("PULIHKAN", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEncryptRestoreDialog = false }) {
                    Text("BATAL", color = TextSecondary)
                }
            }
        )
    }

    // Set Gemini API Key Dialog
    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = {
                Text("Gemini API Key", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            },
            text = {
                Column {
                    Text(
                        text = "Masukkan Google Gemini API Key Anda. Kunci disimpan terenkripsi di EncryptedSharedPreferences lokal dan tidak pernah dibagikan atau dicadangkan ke berkas.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.5.sp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempApiKeyInput,
                        onValueChange = { tempApiKeyInput = it },
                        label = { Text("API Key (AIzaSy...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setGeminiApiKey(tempApiKeyInput.trim())
                        showApiKeyDialog = false
                        viewModel.showSnackbar("API Key Gemini berhasil disimpan")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JadePrimary)
                ) {
                    Text("SIMPAN", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("BATAL", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = TextSecondary
                )
            }
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
