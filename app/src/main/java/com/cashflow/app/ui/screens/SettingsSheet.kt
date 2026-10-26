package com.cashflow.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.data.backup.BackupManager
import com.cashflow.app.data.backup.BackupValidationResult
import com.cashflow.app.data.model.BiometricTimeout
import com.cashflow.app.data.model.filterTransactionsByPeriod
import com.cashflow.app.data.repository.LedgerRecalculateResult
import com.cashflow.app.security.BiometricHelper
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.components.*
import com.cashflow.app.ui.screens.settings.*
import com.cashflow.app.ui.theme.*
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

private enum class SettingsPage(val title: String, val description: String) {
    APPEARANCE("Profil & Tampilan", "Nama panggilan dan tema aplikasi"),
    SECURITY("Keamanan & Privasi", "Kunci aplikasi dan tangkapan layar"),
    NOTIFICATIONS("Notifikasi & Anggaran", "Pengingat, batas anggaran, dan tanggal gajian"),
    BACKUP("Data & Cadangan", "Backup, pemulihan, impor, dan ekspor"),
    ONLINE("Vina & Suara", "Analisis lokal dan pengaturan suara"),
    ABOUT("Tentang Aplikasi", "Panduan, versi, dan pemeriksaan sistem"),
    HISTORY("Riwayat pembaruan", "Catatan perubahan setiap versi")
}
/**
 * Clean, Modular Neo-Brutalist Settings Sheet for "Duit Aing"
 * Composed of dedicated section components:
 * - AppearanceSection (Tampilan)
 * - SecuritySection (Keamanan)
 * - NotificationSection (Notifikasi & Anggaran)
 * - FavoritesSettingsSection (Pintasan & Berulang)
 * - DataBackupSection (Data & Cadangan)
 * - OnlineFeaturesSection (Fitur Online & AI)
 * - AboutSection (Tentang Aplikasi)
 */
@Composable
fun SettingsSheet(
    viewModel: CashflowViewModel,
    onOpenOnboarding: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val fileScope = rememberCoroutineScope()
    var page by remember { mutableStateOf<SettingsPage?>(null) }
    fun back() { page = if (page == SettingsPage.HISTORY) SettingsPage.ABOUT else null }
    val requestedPage by viewModel.settingsInitialPage.collectAsState()
    LaunchedEffect(requestedPage) {
        if (requestedPage == "BACKUP") {
            page = SettingsPage.BACKUP
            viewModel.settingsInitialPage.value = null
        }
    }
    val duit = MaterialTheme.duit
    val permissionActivity = remember(context) {
        generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<android.app.Activity>().firstOrNull()
    }
    val permissionPrefs = remember(context) { com.cashflow.app.data.preferences.PreferenceManager(context) }
    var notificationsAllowed by remember { mutableStateOf(com.cashflow.app.data.service.NotificationHelper.canSendNotifications(context)) }
    var blockedNotificationChannels by remember { mutableStateOf(com.cashflow.app.data.service.NotificationHelper.blockedChannels(context)) }
    var showNotificationDenied by remember { mutableStateOf(false) }
    var pendingNotificationAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var returningFromNotificationSettings by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = com.cashflow.app.data.service.NotificationHelper.canSendNotifications(context)
        if (granted && notificationsAllowed) {
            val action = pendingNotificationAction
            pendingNotificationAction = null
            action?.invoke()
        } else {
            pendingNotificationAction = null
            showNotificationDenied = true
        }
    }
    fun requestNotifications(action: () -> Unit = {}) {
        notificationsAllowed = com.cashflow.app.data.service.NotificationHelper.canSendNotifications(context)
        if (notificationsAllowed) {
            action()
        } else if (android.os.Build.VERSION.SDK_INT >= 33 &&
            !com.cashflow.app.data.service.NotificationHelper.hasNotificationPermission(context) &&
            (!permissionPrefs.getBoolean("requested_notifications") ||
                permissionActivity?.let { androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(it, android.Manifest.permission.POST_NOTIFICATIONS) } == true)) {
            pendingNotificationAction = action
            permissionPrefs.setBoolean("requested_notifications", true)
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            pendingNotificationAction = action
            showNotificationDenied = true
        }
    }
    fun openNotificationSettings() {
        showNotificationDenied = false
        returningFromNotificationSettings = true
        try {
            context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
        } catch (_: android.content.ActivityNotFoundException) {
            pendingNotificationAction = null
            returningFromNotificationSettings = false
            viewModel.showSnackbar("Buka Pengaturan Android > Aplikasi > Duit Aing > Notifikasi.")
        }
    }
    val permissionLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(permissionLifecycle, context) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notificationsAllowed = com.cashflow.app.data.service.NotificationHelper.canSendNotifications(context)
                blockedNotificationChannels = com.cashflow.app.data.service.NotificationHelper.blockedChannels(context)
                if (returningFromNotificationSettings) {
                    returningFromNotificationSettings = false
                    val action = pendingNotificationAction
                    pendingNotificationAction = null
                    if (notificationsAllowed) action?.invoke()
                    else viewModel.showSnackbar("Notifikasi masih dinonaktifkan di Android. Pencatatan tetap dapat digunakan.")
                }
            }
        }
        permissionLifecycle.lifecycle.addObserver(observer)
        onDispose { permissionLifecycle.lifecycle.removeObserver(observer) }
    }

    val activeAssets by viewModel.activeAssets.collectAsState()
    val defaultAsset by viewModel.defaultAsset.collectAsState()
    val currentThemeMode by viewModel.themeMode.collectAsState()
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val biometricTimeout by viewModel.biometricTimeout.collectAsState()
    val isBiometricAvailable = remember(context) { BiometricHelper.isBiometricAvailable(context) }

    // Backup & Data management states
    val lastBackupTime by viewModel.lastBackupTime.collectAsState()
    val backupFormat by viewModel.lastBackupFormat.collectAsState()
    val lastRestoreTime by viewModel.lastRestoreTime.collectAsState()
    val budgetStartDay by viewModel.budgetStartDay.collectAsState()
    val isBackupReminderEnabled by viewModel.isBackupReminderEnabled.collectAsState()
    val isBackupOverdue by viewModel.isBackupOverdue.collectAsState()
    val hasSampleData by viewModel.hasSampleData.collectAsState()

    val currentTransactions by viewModel.transactions.collectAsState()
    val currentAssets by viewModel.assets.collectAsState()
    val currentDebts by viewModel.debts.collectAsState()
    val currentGoals by viewModel.goals.collectAsState()
    val currentBudgets by viewModel.budgets.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val recurringTransactions by viewModel.recurringTransactions.collectAsState()
    val isBudgetAlertsEnabled by viewModel.isBudgetAlertsEnabled.collectAsState()
    val isDailyReminderEnabled by viewModel.isDailyReminderEnabled.collectAsState()
    val dailyReminderTime by viewModel.dailyReminderTime.collectAsState()
    val isBudgetRolloverEnabled by viewModel.isBudgetRolloverEnabled.collectAsState()
    val userName by viewModel.userName.collectAsState()

    var showEditNameDialog by remember { mutableStateOf(false) }
    var tempNameInput by remember(userName) { mutableStateOf(userName) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showEncryptExportDialog by remember { mutableStateOf(false) }
    var showBackupFormatDialog by remember { mutableStateOf(false) }
    var preparedBackupFile by remember { mutableStateOf<java.io.File?>(null) }
    var selectedZipFile by remember { mutableStateOf<java.io.File?>(null) }
    var csvContent by remember { mutableStateOf("") }
    var csvPreview by remember { mutableStateOf<com.cashflow.app.data.importer.CsvPreview?>(null) }
    var importingCsv by remember { mutableStateOf(false) }
    var restoringBackup by remember { mutableStateOf(false) }
    var encryptPasswordInput by remember { mutableStateOf("") }
    var showDiagnosticDialog by remember { mutableStateOf(false) }
    var diagnosticReportText by remember { mutableStateOf("") }
    var showDesignSystemPreview by remember { mutableStateOf(false) }
    var showIntegritySheet by remember { mutableStateOf(false) }
    val integrityDiscrepancies by viewModel.integrityDiscrepancies.collectAsState()

    // Restore & Backup States
    var validatedBackup by remember { mutableStateOf<BackupValidationResult.Valid?>(null) }
    var restoreErrorReason by remember { mutableStateOf<String?>(null) }
    var restoreInputConfirmation by remember { mutableStateOf("") }
    var backupSuccessFileName by remember { mutableStateOf<String?>(null) }
    var backupSuccessUri by remember { mutableStateOf<Uri?>(null) }

    // Reconciliation States
    var reconciliationResult by remember { mutableStateOf<LedgerRecalculateResult?>(null) }

    // Delete All Data Flow States
    var showDeleteAllStep1Dialog by remember { mutableStateOf(false) }
    var showDeleteAllStep2Dialog by remember { mutableStateOf(false) }
    var deleteInputConfirmation by remember { mutableStateOf("") }
    var showDeleteSampleDialog by remember { mutableStateOf(false) }

    val savePreparedBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri == null) viewModel.showSnackbar("Pemilihan berkas dibatalkan. Data tidak berubah.")
        val file = preparedBackupFile
        if (uri != null && file != null) {
            viewModel.savePreparedBackupToUri(file, uri) { result ->
                result.onSuccess { backupSuccessFileName = file.name; backupSuccessUri = uri }
                    .onFailure { viewModel.showSnackbar(it.message ?: "Gagal menyimpan cadangan") }
            }
        }
        preparedBackupFile = null
    }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) viewModel.showSnackbar("Pemilihan berkas dibatalkan. Data tidak berubah.")
        if (uri != null) {
            viewModel.exportBackupToUri(
                uri = uri,
                context = context,
                onSuccess = { _ ->
                    backupSuccessFileName = BackupManager.generateBackupFileName()
                    backupSuccessUri = uri
                },
                onError = { err ->
                    viewModel.showSnackbar(err)
                }
            )
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) viewModel.showSnackbar("Pemilihan berkas dibatalkan. Data tidak berubah.")
        if (uri != null) fileScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openInputStream(uri)).use {
                        BackupManager.readBackupBytes(it).also { bytes -> require(bytes.size <= 4 * 1024 * 1024) { "CSV maksimal 4 MB." } }.toString(Charsets.UTF_8)
                    }
                }
            }.onSuccess { content ->
                viewModel.previewCsv(content) { result ->
                    result.onSuccess { csvContent = content; csvPreview = it }
                        .onFailure { viewModel.showSnackbar(it.message ?: "CSV tidak valid") }
                }
            }.onFailure { viewModel.showSnackbar(it.message ?: "Gagal membaca CSV") }
        }
    }

    csvPreview?.let { preview ->
        AlertDialog(onDismissRequest = { if (!importingCsv) csvPreview = null },
            title = { Text("Pratinjau impor CSV") },
            text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                Text("${preview.transactions.size} transaksi baru • ${preview.duplicateCount} duplikat identik dilewati • ${preview.errors.size} kesalahan")
                if (preview.similarTransactions.isNotEmpty()) Text("${preview.similarTransactions.size} baris mirip perlu ditinjau. Tidak ada baris mirip yang dihapus otomatis.", color = duit.expense)
                Text("Hanya CSV ekspor Duit Aing untuk pemasukan, pengeluaran, dan transfer. Rekening serta kategori harus sudah ada. Cadangan lengkap memakai JSON/ZIP.")
                preview.errors.take(5).forEach { Text(it, color = duit.expense) }
                preview.transactions.take(10).forEach { tx ->
                    Text("${tx.transactionDate} • ${tx.description} • ${Formatters.displayRupiah(tx.amount)}")
                    preview.similarTransactions[tx.id]?.let { prior ->
                        Text("Mirip: ${prior.transactionDate} ${prior.transactionTime} • ${prior.description}", color = duit.expense)
                    }
                }
                if (preview.transactions.size > 10) Text("Dan ${preview.transactions.size - 10} transaksi lainnya.")
            } },
            confirmButton = { TextButton(enabled = !importingCsv && preview.errors.isEmpty() && preview.transactions.isNotEmpty(), onClick = {
                importingCsv = true
                viewModel.importCsv(csvContent) { success -> importingCsv = false; if (success) { csvPreview = null; csvContent = "" } }
            }) { Text(if (importingCsv) "Mengimpor…" else if (preview.similarTransactions.isNotEmpty()) "Tinjau dan impor" else "Impor transaksi") } },
            dismissButton = { TextButton(enabled = !importingCsv, onClick = { csvPreview = null; csvContent = "" }) { Text("Batal") } })
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = { if (page != null) back() else onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = duit.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (page != null) IconButton(onClick = ::back) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali ke menu pengaturan", tint = duit.textPrimary)
                }
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = page?.title ?: "Pengaturan",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp,
                            fontSize = 20.sp
                        ),
                        color = duit.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (page == null) "Pilih bagian yang ingin diatur" else "Pengaturan",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            fontSize = 12.sp
                        ),
                        color = duit.textSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(duit.surfaceVariant)
                        .border(1.dp, duit.border, RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Tutup Pengaturan",
                        tint = duit.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedContent(
                targetState = page,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val forward = targetState != null && (initialState == null || targetState!!.ordinal > initialState!!.ordinal)
                    (slideInHorizontally(tween(220)) { if (forward) it / 8 else -it / 8 } + fadeIn(tween(180))) togetherWith
                        (slideOutHorizontally(tween(180)) { if (forward) -it / 8 else it / 8 } + fadeOut(tween(120)))
                },
                label = "SettingsNavigation"
            ) { displayedPage ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (displayedPage == SettingsPage.HISTORY) Modifier else Modifier.verticalScroll(rememberScrollState())),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                when (displayedPage) {
                    null -> SettingsPage.entries.filter { it != SettingsPage.HISTORY }.forEach { destination ->
                        val icon = when (destination) {
                            SettingsPage.APPEARANCE -> Icons.Rounded.Palette
                            SettingsPage.SECURITY -> Icons.Rounded.Security
                            SettingsPage.NOTIFICATIONS -> Icons.Rounded.NotificationsActive
                            SettingsPage.BACKUP -> Icons.Rounded.Backup
                            SettingsPage.ONLINE -> Icons.Rounded.AutoAwesome
                            SettingsPage.ABOUT -> Icons.Rounded.Info
                            SettingsPage.HISTORY -> Icons.Rounded.History
                        }
                        SettingsItemRow(title = destination.title, description = destination.description, icon = icon,
                            onClick = { page = destination }, control = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = duit.textSecondary) })
                        if (destination != SettingsPage.ABOUT) HorizontalDivider(color = duit.border)
                    }
                    SettingsPage.APPEARANCE -> {
                // 0. Profil Pengguna (Nama Panggilan)
                ProfileSection(
                    userName = userName,
                    onEditName = {
                        tempNameInput = userName
                        showEditNameDialog = true
                    }
                )

                // 1. Tampilan (Appearance)
                AppearanceSection(
                    currentThemeMode = currentThemeMode,
                    onThemeSelected = { mode ->
                        viewModel.setThemeMode(mode)
                        viewModel.showSnackbar("Tema diubah ke mode ${mode.name.lowercase().replaceFirstChar { it.uppercase() }}")
                    }
                )


                    }

                    SettingsPage.SECURITY -> {
                // 2. Keamanan (Security & Biometrics)
                SecuritySection(
                    isBiometricEnabled = isBiometricEnabled,
                    isBiometricAvailable = isBiometricAvailable,
                    onBiometricToggled = { checked ->
                        if (isBiometricAvailable) {
                            viewModel.setBiometricEnabled(checked)
                            viewModel.setBiometricTimeout(BiometricTimeout.IMMEDIATE)
                            viewModel.showSnackbar(
                                if (checked) "Kunci biometrik diaktifkan" else "Kunci biometrik dinonaktifkan"
                            )
                        } else {
                            viewModel.showSnackbar("Biometrik atau PIN belum disetel di pengaturan perangkat")
                        }
                    }
                )

                val blockedScreenshots by viewModel.isScreenshotBlocked.collectAsState()
                SettingsItemRow(title = "Blokir tangkapan layar", description = "Lindungi tampilan aplikasi dan pratinjau aplikasi terbaru.", icon = Icons.Rounded.Security,
                    control = { Switch(checked = blockedScreenshots, onCheckedChange = viewModel::setScreenshotBlocked) })


                    }

                    SettingsPage.NOTIFICATIONS -> {
                // Permissions reflect Android's actual status, separately from reminder preferences.
                NotificationAccessSection(
                    allowed = notificationsAllowed, blockedChannels = blockedNotificationChannels,
                    onRequest = { requestNotifications() }, onOpenSettings = { openNotificationSettings() },
                    onTest = { requestNotifications {
                        if (com.cashflow.app.data.service.NotificationHelper.showLocalNotification(context, 3099,
                            "Pengingat Anda siap", "Notifikasi Duit Aing berhasil ditampilkan. Pengingat harian mengikuti jam yang Anda pilih.",
                            com.cashflow.app.data.service.NotificationHelper.CHANNEL_DAILY_REMINDER)) {
                            viewModel.showSnackbar("Notifikasi uji dikirim. Periksa panel notifikasi.")
                        } else {
                            blockedNotificationChannels = com.cashflow.app.data.service.NotificationHelper.blockedChannels(context)
                            showNotificationDenied = true
                        }
                    } }
                )
                Spacer(Modifier.height(12.dp))
                NotificationSection(
                    isDailyReminderEnabled = isDailyReminderEnabled,
                    dailyReminderTime = dailyReminderTime,
                    isBudgetAlertsEnabled = isBudgetAlertsEnabled,
                    isBudgetRolloverEnabled = isBudgetRolloverEnabled,
                    activeAssets = activeAssets,
                    defaultAsset = defaultAsset,
                    onDailyReminderToggled = { enabled -> if (enabled) requestNotifications { viewModel.setDailyReminderEnabled(true) } else viewModel.setDailyReminderEnabled(false) },
                    onDailyReminderTimeChanged = { viewModel.setDailyReminderTime(it) },
                    onBudgetAlertsToggled = { enabled -> if (enabled) requestNotifications { viewModel.setBudgetAlertsEnabled(true) } else viewModel.setBudgetAlertsEnabled(false) },
                    onBudgetRolloverToggled = { viewModel.setBudgetRolloverEnabled(it) },
                    onDefaultAssetSelected = { viewModel.setDefaultAsset(it.id) }
                )

                // 4. Pintasan Transaksi (Favorit & Berulang)
                var startDayInput by remember(budgetStartDay) { mutableStateOf(budgetStartDay.toString()) }
                OutlinedTextField(value = startDayInput, onValueChange = { startDayInput = it }, label = { Text("Tanggal mulai anggaran bulanan (1–31)") },
                    supportingText = { Text("Sesuaikan tanggal gajian. Bulan pendek memakai hari terakhir. Laporan bulanan tetap mengikuti kalender.") },
                    trailingIcon = { TextButton(onClick = {
                        val day = startDayInput.toIntOrNull()
                        if (day != null && day in 1..31) viewModel.setBudgetStartDay(day) else viewModel.showSnackbar("Pilih tanggal 1–31")
                    }) { Text("Simpan") } }, modifier = Modifier.fillMaxWidth())

                    }

                    SettingsPage.BACKUP -> {
                com.cashflow.app.ui.screens.settings.BackupSettingsPanel(
                    onRestoreReady = { prepared ->
                        selectedZipFile = prepared.zip
                        validatedBackup = prepared.validation
                        restoreInputConfirmation = ""
                    },
                    onAdvanced = { showBackupFormatDialog = true }
                )
                // 5. Data & Cadangan (Backup, Restore, Sync, Reset)
                DataBackupSection(
                    showBackupControls = false,
                    lastBackupTime = lastBackupTime,
                    backupFormat = backupFormat,
                    isBackupOverdue = isBackupOverdue,
                    isBackupReminderEnabled = isBackupReminderEnabled,
                    hasSampleData = hasSampleData,
                    onExportJson = { showBackupFormatDialog = true },
                    onRestoreJson = {},
                    onBackupReminderToggled = { enabled -> if (enabled) requestNotifications { viewModel.setBackupReminderEnabled(true) } else viewModel.setBackupReminderEnabled(false) },
                    onRecalculateBalances = {
                        viewModel.recalculateBalances { result ->
                            reconciliationResult = result
                        }
                    },
                    onExportReport = { showExportDialog = true },
                    onImportCsv = { csvLauncher.launch("*/*") },
                    onDeleteSampleData = { showDeleteSampleDialog = true },
                    onDeleteAllData = { showDeleteAllStep1Dialog = true }
                )
                if (lastRestoreTime > 0) Text("Pemulihan terakhir berhasil: " + java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.forLanguageTag("id-ID")).format(java.util.Date(lastRestoreTime)), style = MaterialTheme.typography.bodySmall)


                    }

                    SettingsPage.ONLINE -> {
                // 6. Fitur Online & AI
                OnlineFeaturesSection()
                val onlineVoice by viewModel.isOnlineVoiceEnabled.collectAsState()
                SettingsItemRow(title = "Layanan suara online", description = "Pengenalan ucapan memakai layanan suara Android dan dapat memerlukan internet. Nonaktifkan untuk hanya memakai pengenal offline jika tersedia.", icon = Icons.Rounded.Mic,
                    control = { Switch(checked = onlineVoice, onCheckedChange = viewModel::setOnlineVoiceEnabled) })

                    }

                    SettingsPage.ABOUT -> {
                // 7. Tentang Aplikasi (About, Changelog, Diagnostics, Design System)
                AboutSection(
                    onOpenChangelog = { page = SettingsPage.HISTORY },
                    onOpenOnboarding = {
                        onDismiss()
                        onOpenOnboarding()
                    },
                    onOpenDiagnostic = {
                        diagnosticReportText = com.cashflow.app.data.diagnostic.DiagnosticLogManager.generateSanitizedReport(
                            context = context,
                            transactionCount = currentTransactions.size,
                            assetCount = currentAssets.size,
                            categoryCount = categories.size,
                            budgetCount = currentBudgets.size,
                            debtCount = currentDebts.size,
                            goalCount = currentGoals.size,
                            recurringCount = recurringTransactions.size
                        )
                        showDiagnosticDialog = true
                    },
                    onOpenIntegrityCheck = { showIntegritySheet = true },
                    onOpenDesignSystem = { showDesignSystemPreview = true },
                    discrepancyCount = integrityDiscrepancies.size
                )


                    }
                    SettingsPage.HISTORY -> ReleaseHistorySection()
                }
                if (displayedPage != SettingsPage.HISTORY) Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    }

        }

    // Modal Overlays and Confirmations

    // 1. Restore Data Preview Dialog
    validatedBackup?.let { valid ->
        DuitAingModalBottomSheet(
            onDismissRequest = {
                if (!restoringBackup) {
                    selectedZipFile?.delete(); selectedZipFile = null
                    validatedBackup = null
                    restoreInputConfirmation = ""
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
            ) {
                Text(
                    text = "PULIHKAN DATA CADANGAN",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp,
                        fontSize = 20.sp
                    ),
                    color = duit.textPrimary
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(duit.expenseContainer)
                        .border(1.dp, duit.expense.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Pemulihan akan mengganti data keuangan serta pengaturan yang tercantum dalam cadangan. Salinan pengaman data dan pengaturan saat ini akan dibuat terlebih dahulu.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = duit.expense
                    )
                }

                // Metadata Details
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(duit.surface)
                        .border(1.dp, duit.border, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    RestoreItemRow("WAKTU CADANGAN", valid.summary.backupDate)
                    HorizontalDivider(color = duit.border, thickness = 1.dp)
                    RestoreItemRow("TRANSAKSI", "${valid.summary.transactionCount} data (saat ini: ${currentTransactions.size})")
                    HorizontalDivider(color = duit.border, thickness = 1.dp)
                    RestoreItemRow("ASET & REKENING", "${valid.summary.assetCount} aset (saat ini: ${currentAssets.size})")
                    HorizontalDivider(color = duit.border, thickness = 1.dp)
                    RestoreItemRow("TARGET KEUANGAN", "${valid.summary.goalCount} target (saat ini: ${currentGoals.size})")
                    HorizontalDivider(color = duit.border, thickness = 1.dp)
                    RestoreItemRow("CATATAN HUTANG", "${valid.summary.debtCount} hutang (saat ini: ${currentDebts.size})")
                    HorizontalDivider(color = duit.border, thickness = 1.dp)
                    RestoreItemRow("PENGATURAN", if (valid.parsedData.has("app_settings"))
                        "Termasuk favorit kategori dan preferensi penggunaan" else "Tidak disertakan; pengaturan saat ini dipertahankan")
                    HorizontalDivider(color = duit.border, thickness = 1.dp)
                    RestoreItemRow("CHECKSUM SHA-256", "Terverifikasi ✓")
                }

                Text(
                    text = "Ketik 'PULIHKAN' untuk melanjutkan proses:",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = duit.textSecondary
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
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    DuitAingButton(
                        text = "BATAL",
                        onClick = {
                            selectedZipFile?.delete(); selectedZipFile = null
                            validatedBackup = null
                            restoreInputConfirmation = ""
                        },
                        enabled = !restoringBackup,
                        modifier = Modifier.weight(1f),
                        variant = DuitAingButtonVariant.SECONDARY
                    )

                    val isRestoreEnabled = !restoringBackup && restoreInputConfirmation.trim() == "PULIHKAN"
                    DuitAingButton(
                        text = if (restoringBackup) "MEMULIHKAN…" else "PULIHKAN DATA",
                        onClick = {
                            restoringBackup = true
                            val zip = selectedZipFile
                            if (zip != null) {
                                viewModel.restoreZipBackupFile(zip) { result ->
                                    zip.delete()
                                    restoringBackup = false
                                    result.onFailure { restoreErrorReason = it.message ?: "Gagal memulihkan ZIP" }
                                    validatedBackup = null
                                    selectedZipFile = null
                                    restoreInputConfirmation = ""
                                }
                            } else {
                                viewModel.restoreBackup(valid.parsedData) { result ->
                                    restoringBackup = false
                                    result.onFailure { restoreErrorReason = it.message ?: "Pemulihan gagal" }
                                    validatedBackup = null
                                    restoreInputConfirmation = ""
                                }
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

    // 2b. Backup Success Dialog
    if (backupSuccessFileName != null) {
        DuitAingConfirmDialog(
            isOpen = true,
            title = "CADANGAN BERHASIL DISIMPAN",
            message = buildString {
                append("Berkas cadangan data keuangan Anda telah berhasil disimpan.\n\n")
                append("• Nama berkas: ${backupSuccessFileName}\n")
                append("• Transaksi dicadangkan: ${currentTransactions.size}\n")
                append("• Rekening aset: ${currentAssets.size}\n\n")
                append("Berkas ini tersimpan aman di penyimpanan perangkat Anda dan dapat dipulihkan kapan saja melalui menu 'Pulihkan Data'.")
            },
            confirmText = "MENGERTI",
            confirmVariant = DuitAingButtonVariant.PRIMARY,
            dismissText = "BAGIKAN",
            onConfirm = {
                backupSuccessFileName = null
                backupSuccessUri = null
            },
            onDismiss = {
                backupSuccessFileName = null
                backupSuccessUri = null
                viewModel.exportBackupFile(
                    context = context,
                    onSuccess = { _, intent, _ ->
                        val chooser = Intent.createChooser(intent, "Bagikan Berkas Cadangan")
                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(chooser)
                    },
                    onError = { err -> viewModel.showSnackbar(err) }
                )
            }
        )
    }

    // 2c. Reconciliation Result Dialog
    if (reconciliationResult != null) {
        val result = reconciliationResult!!
        val isConsistent = result.discrepanciesFound == 0
        DuitAingConfirmDialog(
            isOpen = true,
            title = "REKONSILIASI SALDO BUKU BESAR",
            message = buildString {
                append("Apa itu Rekonsiliasi Saldo?\n")
                append("Rekonsiliasi adalah audit otomatis untuk mencocokkan saldo tercatat di setiap rekening aset Anda dengan total kalkulasi akumulasi mutasi pemasukan, pengeluaran, dan transfer.\n\n")
                append("Hasil Pemeriksaan:\n")
                append("• Rekening diperiksa: ${result.totalAssetsChecked} aset\n")
                if (isConsistent) {
                    append("• Status: 100% Cocok & Akurat ✓\n")
                    append("Seluruh saldo rekening konsisten sempurna dengan riwayat transaksi Anda.")
                } else {
                    append("• Selisih ditemukan: ${result.discrepanciesFound} aset\n")
                    append("Selisih saldo telah diperbaiki dan diselaraskan otomatis dengan buku besar:")
                    result.details.forEach { d ->
                        append("\n- ${d.assetName}: disesuaikan ${Formatters.formatRupiah(d.difference)}")
                    }
                }
            },
            confirmText = "MENGERTI",
            confirmVariant = DuitAingButtonVariant.PRIMARY,
            dismissText = "",
            onConfirm = { reconciliationResult = null },
            onDismiss = { reconciliationResult = null }
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
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
            ) {
                Text(
                    text = "KONFIRMASI HAPUS SEMUA DATA",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp,
                        fontSize = 20.sp
                    ),
                    color = duit.expense
                )

                Text(
                    text = "Transaksi, rekening, anggaran, target, hutang, kategori pribadi, struk, percakapan Vina, serta cadangan dan ekspor internal akan dihapus. Berkas yang sudah Anda simpan ke Downloads atau Drive tetap ada dan perlu dihapus sendiri. Pengaturan aplikasi dipertahankan.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ),
                    color = duit.textSecondary
                )

                Text(
                    text = "Ketik 'HAPUS' dengan huruf kapital di bawah untuk konfirmasi:",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = duit.textPrimary
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
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
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

    if (showNotificationDenied) {
        AlertDialog(
            onDismissRequest = { showNotificationDenied = false; pendingNotificationAction = null },
            title = { Text(if (notificationsAllowed) "Kanal notifikasi dinonaktifkan" else "Izin notifikasi belum diberikan") },
            text = { Text(if (notificationsAllowed)
                "Aktifkan kanal pengingat di Pengaturan Android: ${blockedNotificationChannels.joinToString().ifBlank { "Pengingat Catat Harian" }}."
                else "Pengingat tidak dapat muncul karena notifikasi belum diizinkan atau dinonaktifkan di Android. Anda tetap dapat memakai pencatatan manual.") },
            confirmButton = { TextButton(onClick = { openNotificationSettings() }) { Text("Buka Pengaturan") } },
            dismissButton = { TextButton(onClick = { showNotificationDenied = false; pendingNotificationAction = null }) { Text("Nanti") } }
        )
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

    // 6. Export Data Dialog (PDF & CSV)
    if (showExportDialog) {
        val period by viewModel.selectedPeriod.collectAsState()
        val range by viewModel.reportDateRange.collectAsState()
        val today by viewModel.calendarDate.collectAsState()
        val filtered = remember(currentTransactions, period, range, today) {
            filterTransactionsByPeriod(currentTransactions, period, today, range)
        }
        val scopeName = range?.let {
            "${Formatters.formatDateReadable(it.start.toString())} – ${Formatters.formatDateReadable(it.end.toString())}"
        } ?: period.displayName
        ExportDataDialog(
            isOpen = true,
            viewModel = viewModel,
            filteredTransactions = filtered,
            filterName = scopeName,
            onDismiss = { showExportDialog = false }
        )
    }

    if (showBackupFormatDialog) {
        AlertDialog(onDismissRequest = { showBackupFormatDialog = false },
            title = { Text("Pilih Cadangan") },
            text = { Column {
                Text("Pilih data yang ingin disimpan dan perlindungannya.")
                TextButton(onClick = {
                    showBackupFormatDialog = false
                    encryptPasswordInput = ""
                    showEncryptExportDialog = true
                }) { Text("Data terenkripsi (.enc) — tanpa foto struk") }
                TextButton(onClick = {
                    showBackupFormatDialog = false
                    viewModel.exportZipBackupFile { result ->
                        result.onSuccess { file -> preparedBackupFile = file; savePreparedBackupLauncher.launch(file.name) }
                            .onFailure { viewModel.showSnackbar(it.message ?: "Gagal membuat ZIP") }
                    }
                }) { Text("Data + foto struk (.zip) — tanpa kata sandi") }
                TextButton(onClick = {
                    showBackupFormatDialog = false
                    createBackupLauncher.launch(BackupManager.generateBackupFileName())
                }) { Text("Data JSON (.json) — tanpa kata sandi") }
            } }, confirmButton = { TextButton(onClick = { showBackupFormatDialog = false }) { Text("Batal") } })
    }
    // 7. Export Encrypted Backup Dialog
    if (showEncryptExportDialog) {
        AlertDialog(
            onDismissRequest = { showEncryptExportDialog = false },
            title = {
                Text("Ekspor Cadangan Terenkripsi", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            },
            text = {
                Column {
                    Text(
                        text = "Data cadangan akan dienkripsi dengan standar AES-256-GCM. Masukkan kata sandi pengunci:",
                        style = MaterialTheme.typography.bodySmall.copy(color = duit.textSecondary, fontSize = 12.sp)
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
                        val pwd = encryptPasswordInput
                        if (pwd.length < 12 || pwd.isBlank()) {
                            viewModel.showSnackbar("Gunakan kata sandi minimal 12 karakter")
                            return@Button
                        }
                        showEncryptExportDialog = false
                        viewModel.exportEncryptedBackupFile(pwd) { res ->
                            res.onSuccess { file ->
                                preparedBackupFile = file
                                savePreparedBackupLauncher.launch(file.name)
                            }.onFailure { err ->
                                viewModel.showSnackbar("Gagal membuat berkas terenkripsi: ${err.message}")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = duit.primary,
                        contentColor = duit.onPrimary
                    )
                ) {
                    Text("EKSPOR .ENC", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEncryptExportDialog = false }) {
                    Text("BATAL", color = duit.textSecondary)
                }
            }
        )
    }

    // Edit User Nickname Dialog
    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = {
                Text("Nama Panggilan", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Nama panggilan ini akan ditampilkan pada sapaan beranda (mis: Selamat pagi, Agung).",
                        style = MaterialTheme.typography.bodySmall.copy(color = duit.textSecondary, fontSize = 12.5.sp)
                    )
                    OutlinedTextField(
                        value = tempNameInput,
                        onValueChange = { if (it.length <= 25) tempNameInput = it },
                        placeholder = { Text("Contoh: Agung") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setUserName(tempNameInput) {
                            showEditNameDialog = false
                            viewModel.showSnackbar("Nama panggilan diperbarui.")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = duit.primary,
                        contentColor = duit.onPrimary
                    )
                ) {
                    Text("SIMPAN", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("BATAL", color = duit.textSecondary)
                }
            }
        )
    }

    // 10. Diagnostik & Log Sistem Dialog
    if (showDiagnosticDialog) {
        AlertDialog(
            onDismissRequest = { showDiagnosticDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Assessment,
                        contentDescription = null,
                        tint = duit.textPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        "Diagnostik Sistem",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    Text(
                        text = "Laporan ini mencakup metrik perangkat & ukuran basis data tanpa menyertakan data sensitif.",
                        style = MaterialTheme.typography.bodySmall.copy(color = duit.textSecondary, fontSize = 12.sp)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(duit.surfaceVariant)
                            .border(1.dp, duit.border, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = diagnosticReportText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 14.sp
                            ),
                            color = duit.textPrimary
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            showDiagnosticDialog = false
                            showIntegritySheet = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = duit.surfaceVariant,
                            contentColor = duit.textPrimary
                        )
                    ) {
                        Icon(Icons.Rounded.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("INTEGRITAS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = ClipData.newPlainText("Duit Aing Diagnostic Log", diagnosticReportText)
                            clipboard?.setPrimaryClip(clip)
                            Toast.makeText(context, "Log diagnostik disalin ke clipboard", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = duit.primary,
                            contentColor = duit.onPrimary
                        )
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SALIN", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiagnosticDialog = false }) {
                    Text("TUTUP", color = duit.textSecondary)
                }
            }
        )
    }

    if (showDesignSystemPreview) {
        DesignSystemPreviewDialog(onDismiss = { showDesignSystemPreview = false })
    }

    if (showIntegritySheet) {
        BalanceIntegritySheet(
            viewModel = viewModel,
            onDismiss = { showIntegritySheet = false }
        )
    }
}

@Composable
private fun ChangelogItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    val duit = MaterialTheme.duit
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = duit.primaryContainer,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = duit.onPrimaryContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = duit.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 15.sp
                ),
                color = duit.textSecondary
            )
        }
    }
}

@Composable
private fun RestoreItemRow(label: String, value: String) {
    val duit = MaterialTheme.duit
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            ),
            color = duit.textSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = duit.textPrimary
        )
    }
}
