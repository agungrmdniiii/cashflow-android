package com.cashflow.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.cashflow.app.security.BiometricHelper
import com.cashflow.app.ui.components.DuitAingButton
import com.cashflow.app.ui.components.DuitAingButtonVariant
import com.cashflow.app.ui.components.DuitAingLogo
import com.cashflow.app.ui.components.LogoVariant
import com.cashflow.app.ui.theme.*

@Composable
fun BiometricLockScreen(
    onUnlockSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun triggerPrompt() {
        if (activity != null) {
            errorMessage = null
            BiometricHelper.showBiometricPrompt(
                activity = activity,
                title = "Buka Kunci Duit Aing",
                subtitle = "Verifikasi identitas Anda untuk mengakses data kas",
                onSuccess = {
                    errorMessage = null
                    onUnlockSuccess()
                },
                onError = { err ->
                    errorMessage = err
                }
            )
        } else {
            // Fallback if activity is not FragmentActivity
            onUnlockSuccess()
        }
    }

    // Auto-launch biometric prompt when screen appears
    LaunchedEffect(Unit) {
        triggerPrompt()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = PebbleBackground
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, DarkBorder),
                colors = CardDefaults.cardColors(containerColor = PebbleSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Logo Header
                    DuitAingLogo(size = 48.dp, variant = LogoVariant.COLOR)

                    Spacer(modifier = Modifier.height(28.dp))

                    // Tactile Fingerprint Island
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(JadePrimaryContainer)
                            .border(2.dp, DarkBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Fingerprint,
                            contentDescription = "Biometrik",
                            modifier = Modifier.size(48.dp),
                            tint = JadePrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Title
                    Text(
                        text = "APLIKASI TERKUNCI",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        ),
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Subtitle
                    Text(
                        text = "Data keuangan pribadi Anda terlindungi. Tempelkan sidik jari atau gunakan PIN/Pola untuk membuka.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    // Error text if prompt cancelled or failed
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StatusNegativeContainer,
                            border = androidx.compose.foundation.BorderStroke(1.dp, StatusNegative.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                color = StatusNegative,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Unlock Button
                    DuitAingButton(
                        text = "BUKA KUNCI SEKARANG",
                        onClick = { triggerPrompt() },
                        variant = DuitAingButtonVariant.PRIMARY,
                        leadingIcon = Icons.Rounded.Fingerprint,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Enkripsi lokal aman di perangkat",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}
