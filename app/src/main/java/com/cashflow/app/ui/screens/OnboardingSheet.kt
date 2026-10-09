package com.cashflow.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cashflow.app.ui.components.*
import com.cashflow.app.ui.theme.*

/**
 * Editorial Onboarding for "Duit Aing"
 * Follows Section 19 of the design spec:
 * "CATAT DUIT. PAHAMI ARUSNYA."
 */
@Composable
fun OnboardingSheet(
    onDismiss: () -> Unit
) {
    DuitAingModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.Start
        ) {
            DuitAingLogo(size = 56.dp, variant = LogoVariant.COLOR)

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "CATAT DUIT.\nPAHAMI ARUSNYA.\nNGOMONG AJA.",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                    lineHeight = 32.sp
                ),
                color = DarkSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Kelola pemasukan, pengeluaran, aset, dan target keuangan harian tanpa ribet. Cukup tekan mikrofon dan sebutkan transaksi secara natural.",
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 22.sp
                ),
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Feature Highlights in clean typography
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FeatureItem(text = "Input suara instan bahasa Indonesia")
                FeatureItem(text = "Total kekayaan & saldo aset real-time")
                FeatureItem(text = "100% offline-first & aman di perangkat Anda")
            }

            Spacer(modifier = Modifier.height(24.dp))

            DuitAingButton(
                text = "MULAI",
                onClick = onDismiss,
                trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                modifier = Modifier.fillMaxWidth(),
                variant = DuitAingButtonVariant.PRIMARY,
                height = 52.dp,
                shadowOffset = 3.dp
            )
        }
    }
}

@Composable
private fun FeatureItem(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(JadePrimary, RoundedCornerShape(2.dp))
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = TextPrimary
        )
    }
}
