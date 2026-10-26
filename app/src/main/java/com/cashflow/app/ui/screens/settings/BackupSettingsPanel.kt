package com.cashflow.app.ui.screens.settings

import android.accounts.AccountManager
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.DocumentsContract
import android.view.ContextThemeWrapper
import android.widget.NumberPicker
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cashflow.app.data.backup.*
import com.cashflow.app.data.service.AutomaticBackupJob
import com.cashflow.app.data.service.CloudBackupMetadata
import com.cashflow.app.ui.components.DuitAingButton
import com.cashflow.app.ui.components.DuitAingButtonVariant
import com.cashflow.app.ui.components.DuitAingSelectionSheet
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import com.cashflow.app.ui.theme.duit
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.*
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BackupSettingsPanel(onRestoreReady: (PreparedRestore) -> Unit, onAdvanced: () -> Unit) {
    val context = LocalContext.current
    val duit = MaterialTheme.duit
    val coordinator = remember { BackupCoordinator(context) }
    val store = coordinator.store
    val passwords = remember { BackupPasswordStore(context) }
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(store) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { store.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val options = remember(revision) { store.automation() }
    val account = remember(revision) { store.driveAccount }
    val needsAuthorization = remember(revision) { store.preferences.getBoolean("drive_reauth", false) }
    val progress by BackupCoordinator.status.collectAsState()
    var working by remember { mutableStateOf(false) }
    val busy = working || progress != null
    var actionJob by remember { mutableStateOf<Job?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var setupPassword by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var connectAfterPassword by remember { mutableStateOf(false) }
    var pendingAccount by remember { mutableStateOf<String?>(null) }
    var pendingResolution by remember { mutableStateOf<PendingIntent?>(null) }
    var pendingLocal by remember { mutableStateOf<File?>(null) }
    var pendingRestore by remember { mutableStateOf<File?>(null) }
    var restorePassword by remember { mutableStateOf("") }
    var restoreError by remember { mutableStateOf<String?>(null) }
    var chooseWindow by remember { mutableStateOf(false) }
    var choice by remember { mutableStateOf<String?>(null) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var showDriveFiles by remember { mutableStateOf(false) }
    var driveFiles by remember { mutableStateOf<List<CloudBackupMetadata>>(emptyList()) }
    var nextPage by remember { mutableStateOf<String?>(null) }
    var enableAfterSetup by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { pendingLocal?.delete(); pendingRestore?.delete() } }

    fun update(settings: BackupAutomation) {
        try {
            store.save(settings)
            AutomaticBackupJob.schedule(context, replace = true)
        } catch (e: Exception) { message = e.message ?: "Jadwal belum dapat disimpan. Coba lagi." }
    }
    fun runAction(action: suspend () -> Unit) {
        actionJob = scope.launch {
            working = true
            message = null
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: DriveAuthorizationRequired) {
                pendingAccount = store.driveAccount
                pendingResolution = e.resolution
                message = e.message
            } catch (e: Exception) {
                message = e.message ?: "Proses belum berhasil. Coba lagi."
                if (setupPassword) passwordError = message
                if (pendingRestore != null) restoreError = message
            } finally { working = false }
        }
    }
    fun connected(name: String, result: com.google.android.gms.auth.api.identity.AuthorizationResult) {
        coordinator.acceptAuthorization(name, result)
        if (enableAfterSetup) update(store.automation().copy(enabled = true))
        enableAfterSetup = false
        pendingAccount = null
        message = "Google Drive terhubung. Anda dapat mencadangkan sekarang atau mengaktifkan jadwal otomatis."
    }

    val authorizationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val name = pendingAccount
        if (result.resultCode != Activity.RESULT_OK || name == null) {
            pendingAccount = null; enableAfterSetup = false; message = "Koneksi Google dibatalkan. Data tidak berubah."
        } else runAction { connected(name, Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result.data)) }
    }
    LaunchedEffect(pendingResolution) { pendingResolution?.let {
        pendingResolution = null
        authorizationLauncher.launch(IntentSenderRequest.Builder(it).build())
    } }
    val accountLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val name = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (result.resultCode != Activity.RESULT_OK || name.isNullOrBlank()) {
            enableAfterSetup = false; message = "Pemilihan akun dibatalkan."
        } else runAction {
            pendingAccount = name
            val authorization = coordinator.authorize(name)
            if (authorization.hasResolution()) pendingResolution = authorization.pendingIntent else connected(name, authorization)
        }
    }
    fun connect() {
        if (!passwords.isConfigured()) {
            newPassword = ""; confirmPassword = ""; passwordError = null
            connectAfterPassword = true; setupPassword = true
        } else if (account != null && needsAuthorization) runAction {
            pendingAccount = account
            val authorization = coordinator.authorize(account)
            if (authorization.hasResolution()) pendingResolution = authorization.pendingIntent else connected(account, authorization)
        } else accountLauncher.launch(AccountManager.newChooseAccountIntent(null, null, arrayOf("com.google"), null, null, null, null))
    }

    suspend fun openRestore(file: File) {
        try {
            val kind = withContext(Dispatchers.IO) { BackupArchive.kind(file) }
            if (kind == BackupArchive.Kind.ENCRYPTED_JSON || kind == BackupArchive.Kind.ENCRYPTED_ZIP) {
                restorePassword = ""; restoreError = null; pendingRestore = file
            } else {
                val prepared = coordinator.inspect(file)
                onRestoreReady(prepared)
                if (prepared.zip != file) file.delete()
            }
        } catch (e: Exception) { file.delete(); throw e }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runAction {
            val file = withContext(Dispatchers.IO) {
                val target = File.createTempFile("restore-", ".backup", context.cacheDir)
                try { requireNotNull(context.contentResolver.openInputStream(uri)).use { BackupArchive.copyBounded(it, target) }; target }
                catch (e: Exception) { target.delete(); throw e }
            }
            openRestore(file)
        }
    }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val file = pendingLocal
        pendingLocal = null
        if (uri == null) { file?.delete(); message = "Penyimpanan dibatalkan. Cadangan sebelumnya tetap tersedia." }
        else runAction {
            if (file == null) {
                withContext(Dispatchers.IO) { runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) } }
                error("Proses cadangan terputus. Silakan cadangkan kembali.")
            }
            try { coordinator.saveLocal(file, uri); message = "Cadangan data dan foto struk berhasil disimpan." }
            finally { file.delete() }
        }
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) enableAfterSetup = false
        else runAction {
            withContext(Dispatchers.IO) {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                val document = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
                val flags = context.contentResolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use {
                    if (it.moveToFirst()) it.getInt(0) else 0
                } ?: 0
                check(flags and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0) { "Folder ini tidak dapat ditulis. Pilih folder khusus cadangan di HP." }
                val previous = store.localFolder
                store.localFolder = uri
                if (previous != null && previous != uri) runCatching {
                    context.contentResolver.releasePersistableUriPermission(previous, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
            }
            if (enableAfterSetup) update(store.automation().copy(enabled = true))
            else AutomaticBackupJob.schedule(context, replace = true)
            enableAfterSetup = false
            message = "Folder cadangan otomatis tersimpan."
        }
    }

    fun enableAutomatic(enabled: Boolean, destination: BackupDestination? = null) {
        val target = destination ?: options.destination
        val needsDrive = target == BackupDestination.DRIVE && (account == null || needsAuthorization || !passwords.isConfigured())
        val needsFolder = target == BackupDestination.LOCAL && store.localFolder == null
        if (enabled && destination == null && (needsDrive || needsFolder)) {
            choice = "Tujuan"
            return
        }
        val next = options.copy(enabled = enabled, destination = target)
        when {
            enabled && needsDrive -> {
                update(next.copy(enabled = false)); enableAfterSetup = true; connect()
            }
            enabled && needsFolder -> {
                update(next.copy(enabled = false)); enableAfterSetup = true; folderLauncher.launch(null)
            }
            else -> update(next)
        }
    }

    fun savedText(destination: BackupDestination): String {
        val time = store.success(destination)
        return if (time == 0L) "Belum dicadangkan" else
            Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH.mm", Locale.forLanguageTag("id-ID")))
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SettingsSectionCard(title = "Di HP") {
            SettingsItemRow(title = "Cadangkan data", description = savedText(BackupDestination.LOCAL), icon = Icons.Rounded.Backup,
                control = { DuitAingButton("SIMPAN", enabled = !busy, variant = DuitAingButtonVariant.SECONDARY, height = 36.dp, onClick = {
                    runAction { pendingLocal = coordinator.prepareLocal(); saveLauncher.launch(pendingLocal!!.name) }
                }) })
            store.error(BackupDestination.LOCAL)?.let { Text(it, Modifier.padding(horizontal = 14.dp), color = duit.warning, style = MaterialTheme.typography.bodySmall) }
            HorizontalDivider(color = duit.border)
            SettingsItemRow(title = "Pulihkan data", icon = Icons.Rounded.Restore,
                control = { DuitAingButton("BUKA", enabled = !busy, variant = DuitAingButtonVariant.SECONDARY, height = 36.dp,
                    onClick = { restoreLauncher.launch(arrayOf("*/*")) }) })
        }
        SettingsSectionCard(title = "Google Drive") {
            SettingsItemRow(title = "Akun Google", description = account ?: "Belum terhubung", icon = Icons.Rounded.CloudQueue,
                control = { TextButton(enabled = !busy, onClick = {
                    if (account == null) connect() else confirmDisconnect = true
                }) { Text(if (account == null) "Hubungkan" else "Putuskan") } })
            if (account != null && needsAuthorization) {
                HorizontalDivider(color = duit.border)
                SettingsItemRow(title = "Hubungkan ulang", icon = Icons.Rounded.Sync,
                    onClick = if (busy) null else ({ connect() }),
                    control = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = duit.textSecondary) })
            }
            if (account != null && !needsAuthorization) {
                HorizontalDivider(color = duit.border)
                SettingsItemRow(title = "Cadangkan data", description = savedText(BackupDestination.DRIVE), icon = Icons.Rounded.CloudUpload,
                    control = { DuitAingButton("SIMPAN", enabled = !busy, variant = DuitAingButtonVariant.SECONDARY, height = 36.dp, onClick = {
                        runAction { coordinator.runAutomaticOrDrive(BackupDestination.DRIVE, false); message = "Cadangan terenkripsi tersimpan di Drive." }
                    }) })
                store.error(BackupDestination.DRIVE)?.let { Text(it, Modifier.padding(horizontal = 14.dp), color = duit.warning, style = MaterialTheme.typography.bodySmall) }
                HorizontalDivider(color = duit.border)
                SettingsItemRow(title = "Pulihkan data", icon = Icons.Rounded.CloudDownload,
                    control = { DuitAingButton("BUKA", enabled = !busy, variant = DuitAingButtonVariant.SECONDARY, height = 36.dp, onClick = {
                        showDriveFiles = true; driveFiles = emptyList(); nextPage = null
                        runAction { val page = coordinator.listDrive(); driveFiles = page.files; nextPage = page.nextPageToken }
                    }) })
            }
        }
        if (progress != null) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(progress!!, color = duit.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        message?.let { Text(it, color = duit.textPrimary, style = MaterialTheme.typography.bodySmall) }
        SettingsSectionCard(title = "Cadangan otomatis") {
            SettingsItemRow(title = "Aktifkan", icon = Icons.Rounded.Backup,
                iconTint = if (options.enabled) duit.primary else duit.textSecondary,
                iconBackground = if (options.enabled) duit.primaryContainer else duit.surfaceVariant,
                control = {
                    Switch(modifier = Modifier.semantics { contentDescription = "Cadangan otomatis" }, checked = options.enabled, onCheckedChange = { enabled ->
                        enableAutomatic(enabled)
                    }, colors = SwitchDefaults.colors(checkedThumbColor = duit.onPrimary, checkedTrackColor = duit.primary,
                        uncheckedThumbColor = duit.textSecondary, uncheckedTrackColor = duit.surfaceVariant))
                })
            if (options.enabled) {
                HorizontalDivider(color = duit.border)
                SettingsItemRow(title = "Tujuan", description = options.destination.label, icon = Icons.Rounded.SaveAlt,
                    onClick = if (busy) null else ({ choice = "Tujuan" }),
                    control = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = duit.textSecondary) })
                if (options.destination == BackupDestination.LOCAL) {
                    HorizontalDivider(color = duit.border)
                    SettingsItemRow(title = "Folder cadangan", description = if (store.localFolder == null) "Belum dipilih" else "Folder tersimpan",
                        icon = Icons.Rounded.FolderOpen, onClick = if (busy) null else ({ folderLauncher.launch(store.localFolder) }),
                        control = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = duit.textSecondary) })
                }
                HorizontalDivider(color = duit.border)
                SettingsItemRow(title = "Frekuensi", description = options.frequency.label, icon = Icons.Rounded.Repeat,
                    onClick = if (busy) null else ({ choice = "Frekuensi" }),
                    control = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = duit.textSecondary) })
                HorizontalDivider(color = duit.border)
                SettingsItemRow(title = "Jendela waktu", description = "%02d.00–%02d.00".format(options.startHour, options.endHour),
                    icon = Icons.Rounded.Schedule, onClick = if (busy) null else ({ chooseWindow = true }),
                    control = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = duit.textSecondary) })
                if (options.destination == BackupDestination.DRIVE) {
                    HorizontalDivider(color = duit.border)
                    SettingsItemRow(title = "Jaringan", description = options.network.label, icon = Icons.Rounded.Wifi,
                        onClick = if (busy) null else ({ choice = "Jaringan" }),
                        control = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = duit.textSecondary) })
                }
            }
        }
        SettingsSectionCard(title = "Pilihan lanjutan") {
            SettingsItemRow(title = "Format cadangan", icon = Icons.Rounded.Tune, onClick = if (busy) null else onAdvanced,
                control = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = duit.textSecondary) })
            if (passwords.isConfigured()) {
                HorizontalDivider(color = duit.border)
                SettingsItemRow(title = "Kata sandi pemulihan", icon = Icons.Rounded.VpnKey, onClick = if (busy) null else ({
                    connectAfterPassword = false; passwordError = null; newPassword = ""; confirmPassword = ""; setupPassword = true
                }), control = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = duit.textSecondary) })
            }
        }
    }

    val activeChoice = choice
    if (activeChoice != null) {
        val labels = when (activeChoice) {
            "Tujuan" -> BackupDestination.entries.map { it.label }
            "Frekuensi" -> BackupFrequency.entries.map { it.label }
            else -> BackupNetwork.entries.map { it.label }
        }
        val selected = when (activeChoice) {
            "Tujuan" -> options.destination.ordinal
            "Frekuensi" -> options.frequency.ordinal
            else -> options.network.ordinal
        }
        DuitAingSelectionSheet(visible = true, title = activeChoice, onDismissRequest = { choice = null },
            subtitle = if (activeChoice == "Jaringan") "Wi-Fi berkuota tidak digunakan, termasuk pada cadangan manual." else null) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                labels.forEachIndexed { index, label ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = selected == index, enabled = !busy,
                        role = Role.RadioButton, onClick = {
                            when (activeChoice) {
                                "Tujuan" -> if (!options.enabled || BackupDestination.entries[index] != options.destination) enableAutomatic(true, BackupDestination.entries[index])
                                "Frekuensi" -> update(options.copy(frequency = BackupFrequency.entries[index], monthDay = LocalDate.now().dayOfMonth))
                                else -> update(options.copy(network = BackupNetwork.entries[index]))
                            }
                            choice = null
                        }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadioButton(selected = selected == index, onClick = null, enabled = !busy,
                            colors = RadioButtonDefaults.colors(selectedColor = duit.primary, unselectedColor = duit.textSecondary))
                        Text(label, modifier = Modifier.weight(1f), color = duit.textPrimary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    if (setupPassword) AlertDialog(onDismissRequest = { if (!busy) { setupPassword = false; newPassword = ""; confirmPassword = ""; enableAfterSetup = false } },
        containerColor = duit.surface, titleContentColor = duit.textPrimary, textContentColor = duit.textSecondary,
        title = { Text("Kata sandi cadangan") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Buat kata sandi untuk membuka cadangan saat memulihkan data, termasuk di HP baru. Simpan kata sandi ini; perubahan hanya berlaku untuk cadangan baru.")
                OutlinedTextField(value = newPassword, onValueChange = { newPassword = it }, label = { Text("Kata sandi, minimal 8 karakter") },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, enabled = !busy)
                OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it }, label = { Text("Ulangi kata sandi") },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, enabled = !busy)
                passwordError?.let { Text(it, color = duit.expense) }
            }
        }, confirmButton = { TextButton(enabled = !busy && newPassword.length >= 8 && newPassword == confirmPassword, onClick = {
            runAction {
                withContext(Dispatchers.IO) { passwords.save(newPassword) }
                newPassword = ""; confirmPassword = ""; setupPassword = false
                if (connectAfterPassword) accountLauncher.launch(AccountManager.newChooseAccountIntent(null, null, arrayOf("com.google"), null, null, null, null))
                else message = "Kata sandi untuk cadangan baru sudah diperbarui."
            }
        }) { Text("Simpan") } }, dismissButton = { TextButton(enabled = !busy, onClick = {
            setupPassword = false; newPassword = ""; confirmPassword = ""; enableAfterSetup = false
        }) { Text("Batal") } })

    if (pendingRestore != null) AlertDialog(onDismissRequest = { if (!busy) { pendingRestore?.delete(); pendingRestore = null; restorePassword = "" } },
        containerColor = duit.surface, titleContentColor = duit.textPrimary, textContentColor = duit.textSecondary,
        title = { Text("Buka cadangan") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Masukkan kata sandi yang dipakai saat membuat cadangan ini.")
            OutlinedTextField(value = restorePassword, onValueChange = { restorePassword = it }, label = { Text("Kata sandi cadangan") },
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, enabled = !busy)
            restoreError?.let { Text(it, color = duit.expense) }
        } }, confirmButton = { TextButton(enabled = !busy && restorePassword.isNotEmpty(), onClick = {
            val file = pendingRestore ?: return@TextButton
            runAction {
                val prepared = coordinator.inspect(file, restorePassword)
                onRestoreReady(prepared)
                if (prepared.zip != file) file.delete()
                pendingRestore = null; restorePassword = ""; restoreError = null
            }
        }) { Text("Lanjutkan") } }, dismissButton = { TextButton(enabled = !busy, onClick = {
            pendingRestore?.delete(); pendingRestore = null; restorePassword = ""; restoreError = null
        }) { Text("Batal") } })

    if (chooseWindow) BackupWindowDialog(options, onDismiss = { chooseWindow = false }, onSave = { start, end ->
        update(options.copy(startHour = start, endHour = end)); chooseWindow = false
    })

    if (confirmDisconnect) AlertDialog(onDismissRequest = { confirmDisconnect = false }, title = { Text("Putuskan Google Drive?") },
        containerColor = duit.surface, titleContentColor = duit.textPrimary, textContentColor = duit.textSecondary,
        text = { Text("Cadangan yang sudah tersimpan tetap ada. Cadangan otomatis ke Drive akan dinonaktifkan.") },
        confirmButton = { TextButton(onClick = { confirmDisconnect = false; runAction {
            val revoked = coordinator.disconnectDrive()
            message = if (revoked) "Koneksi Google Drive diputuskan." else "Akun sudah dilepas dari perangkat ini dan jadwal Drive dihentikan. Pencabutan izin di Google belum berhasil; Anda dapat mencabutnya melalui pengaturan akun Google."
        } }) { Text("Putuskan") } },
        dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Batal") } })

    if (showDriveFiles) AlertDialog(onDismissRequest = { actionJob?.cancel(); showDriveFiles = false }, title = { Text("Cadangan Google Drive") },
        containerColor = duit.surface, titleContentColor = duit.textPrimary, textContentColor = duit.textSecondary,
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!busy && driveFiles.isEmpty()) Text(message ?: "Belum ada cadangan pada akun ini.")
            LazyColumn(Modifier.heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(driveFiles, key = { it.id }) { item ->
                    OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                        runAction { val file = coordinator.downloadDrive(item.id); showDriveFiles = false; openRestore(file) }
                    }) { Column(Modifier.fillMaxWidth()) {
                        Text(runCatching { Instant.parse(item.createdTime).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH.mm", Locale.forLanguageTag("id-ID"))) }
                            .getOrDefault(item.name.take(80)), fontWeight = FontWeight.SemiBold)
                        Text("${(item.sizeBytes / 1024).coerceAtLeast(1)} KB • ${item.name.substringAfterLast('.').uppercase()}", style = MaterialTheme.typography.bodySmall)
                    } }
                }
            }
            if (nextPage != null) TextButton(enabled = !busy, onClick = { runAction {
                val page = coordinator.listDrive(nextPage); driveFiles = (driveFiles + page.files).distinctBy { it.id }; nextPage = page.nextPageToken
            } }) { Text("Muat lainnya") }
        } }, confirmButton = { TextButton(onClick = { actionJob?.cancel(); showDriveFiles = false }) { Text(if (busy) "Batalkan" else "Tutup") } })
}

@Composable
private fun BackupWindowDialog(options: BackupAutomation, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit) {
    val duit = MaterialTheme.duit
    var start by remember { mutableIntStateOf(options.startHour) }
    var end by remember { mutableIntStateOf(options.endHour) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = duit.surface,
        titleContentColor = duit.textPrimary, textContentColor = duit.textSecondary,
        title = { Text("Waktu cadangan otomatis") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf("Mulai" to start, "Sampai" to end).forEachIndexed { index, (label, value) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, color = duit.textSecondary, style = MaterialTheme.typography.bodySmall)
                        Box(Modifier.fillMaxWidth().height(144.dp)) {
                            Box(Modifier.align(Alignment.Center).fillMaxWidth().height(48.dp)
                                .clip(RoundedCornerShape(12.dp)).background(duit.surfaceVariant))
                            AndroidView(modifier = Modifier.matchParentSize(), factory = { context ->
                            NumberPicker(ContextThemeWrapper(context, if (duit.surface.luminance() < 0.5f) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)).apply {
                                minValue = 0; maxValue = 23; displayedValues = Array(24) { "%02d.00".format(it) }; wrapSelectorWheel = true
                                descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                                contentDescription = if (index == 0) "Pilih jam mulai" else "Pilih jam akhir"
                                if (Build.VERSION.SDK_INT >= 29) {
                                    textColor = duit.textPrimary.toArgb()
                                    textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 24f, resources.displayMetrics)
                                    selectionDividerHeight = 0
                                }
                                setOnValueChangedListener { _, _, hour -> if (index == 0) start = hour else end = hour }
                            }
                            }, update = { it.value = value })
                        }
                    }
                }
            }
            Text("Android dapat menunda cadangan saat baterai atau jaringan belum siap.", style = MaterialTheme.typography.bodySmall, color = duit.textSecondary)
            Text(if (start == end) "Pilih jam mulai dan akhir yang berbeda." else if (end < start) "Jendela berakhir pada hari berikutnya." else "Waktu mengikuti zona waktu HP.",
                style = MaterialTheme.typography.bodySmall, color = duit.textSecondary)
        }
    }, confirmButton = { TextButton(enabled = start != end, onClick = { onSave(start, end) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}
