package com.cashflow.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cashflow.app.BuildConfig
import com.cashflow.app.data.update.AppUpdateViewModel
import com.cashflow.app.data.update.UpdatePhase
import com.cashflow.app.data.update.updateInstallIntent
import com.cashflow.app.ui.theme.duit
import java.util.Locale

@Composable
fun AppUpdateSection() {
    val manager: AppUpdateViewModel = viewModel()
    val status by manager.status.collectAsState()
    val context = LocalContext.current
    val duit = MaterialTheme.duit
    var show by rememberSaveable { mutableStateOf(false) }
    var installError by remember { mutableStateOf<String?>(null) }
    val install = {
        try {
            val apk = status.apk ?: error("Berkas pembaruan tidak tersedia. Unduh kembali.")
            context.startActivity(updateInstallIntent(context, apk))
            installError = null
        } catch (_: Exception) {
            installError = "Pemasangan belum dapat dibuka. Pastikan berkas tersedia dan coba lagi."
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) install()
        else installError = "Izin pemasangan belum diberikan. Izinkan sumber ini melalui Pengaturan Android, lalu tekan Pasang pembaruan."
    }
    SettingsItemRow(
        title = "Pembaruan aplikasi",
        description = when (status.phase) {
            UpdatePhase.DOWNLOADING -> "Mengunduh pembaruan · ${status.progress}%"
            UpdatePhase.READY -> "Pembaruan siap dipasang"
            else -> "Versi ${BuildConfig.VERSION_NAME} · Periksa versi terbaru"
        },
        icon = Icons.Rounded.SystemUpdate,
        iconTint = duit.textPrimary,
        iconBackground = duit.surfaceVariant,
        onClick = { show = true; manager.check() },
        control = {
            TextButton(onClick = { show = true; manager.check() }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("CEK", color = duit.textPrimary)
            }
        }
    )
    if (!show) return
    AlertDialog(
        onDismissRequest = { show = false },
        title = { Text("Pembaruan aplikasi") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Versi terpasang: ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})", color = duit.textSecondary)
                if (status.phase == UpdatePhase.CHECKING) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Memeriksa pembaruan...")
                }
                if (status.message.isNotBlank()) Text(status.message, color = if (status.phase == UpdatePhase.ERROR) duit.expense else duit.textPrimary)
                status.release?.let { release ->
                    Text("Versi terbaru: ${release.versionName} (Build ${release.versionCode})", fontWeight = FontWeight.Bold)
                    Text(String.format(Locale.forLanguageTag("id-ID"), "Ukuran unduhan: %.1f MB", release.size / 1048576.0), color = duit.textSecondary)
                    Text("Catatan perubahan", fontWeight = FontWeight.Bold)
                    Text(release.notes, color = duit.textSecondary)
                }
                if (status.phase == UpdatePhase.DOWNLOADING) {
                    LinearProgressIndicator(progress = { status.progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("Mengunduh pembaruan · ${status.progress}%")
                    TextButton(onClick = manager::cancelDownload) { Text("Batalkan unduhan") }
                }
                if (status.phase == UpdatePhase.READY) Text("Unduhan telah diverifikasi. Android akan meminta konfirmasi pemasangan.")
                installError?.let { Text(it, color = duit.expense) }
            }
        },
        confirmButton = {
            when (status.phase) {
                UpdatePhase.AVAILABLE -> TextButton(onClick = manager::download) { Text("Unduh pembaruan") }
                UpdatePhase.READY -> TextButton(onClick = {
                    if (status.apk?.isFile != true) {
                        installError = null
                        manager.download()
                    } else if (context.packageManager.canRequestPackageInstalls()) install()
                    else try {
                        permissionLauncher.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                    } catch (_: Exception) { installError = "Buka Pengaturan Android untuk mengizinkan pemasangan pembaruan." }
                }) { Text(if (status.apk?.isFile == true) "Pasang pembaruan" else "Unduh ulang") }
                UpdatePhase.ERROR -> TextButton(onClick = { installError = null; if (status.release == null) manager.check() else manager.download() }) {
                    Text(if (status.release == null) "Coba lagi" else "Unduh ulang")
                }
                else -> TextButton(onClick = { show = false }) { Text("Tutup") }
            }
        },
        dismissButton = {
            if (status.phase in listOf(UpdatePhase.AVAILABLE, UpdatePhase.DOWNLOADING, UpdatePhase.READY, UpdatePhase.ERROR)) {
                TextButton(onClick = { show = false }) { Text("Nanti") }
            }
        }
    )
}
