package com.cashflow.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cashflow.app.ui.theme.duit

/**
 * Section Keamanan: Kunci biometrik standar aplikasi perbankan konvensional.
 * Otomatis mengunci saat aplikasi ditinggalkan/ditutup tanpa konfigurasi durasi yang membingungkan.
 */
@Composable
fun SecuritySection(
    isBiometricEnabled: Boolean,
    isBiometricAvailable: Boolean,
    onBiometricToggled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val duit = MaterialTheme.duit

    SettingsSectionCard(
        title = "Keamanan",
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SettingsItemRow(
                title = "Kunci Biometrik",
                description = if (isBiometricAvailable) {
                    if (isBiometricEnabled) "Otomatis mengunci saat aplikasi ditutup atau diminimalkan"
                    else "Nonaktif (masuk langsung tanpa kunci)"
                } else "Biometrik atau PIN belum disetel di pengaturan perangkat",
                icon = Icons.Rounded.Fingerprint,
                iconTint = if (isBiometricEnabled) duit.primary else duit.textSecondary,
                iconBackground = if (isBiometricEnabled) duit.primaryContainer else duit.surfaceVariant,
                control = {
                    Switch(
                        checked = isBiometricEnabled,
                        onCheckedChange = { onBiometricToggled(it) },
                        enabled = isBiometricAvailable,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = duit.onPrimary,
                            checkedTrackColor = duit.primary,
                            uncheckedThumbColor = duit.textSecondary,
                            uncheckedTrackColor = duit.surfaceVariant
                        )
                    )
                }
            )
        }
    }
}
