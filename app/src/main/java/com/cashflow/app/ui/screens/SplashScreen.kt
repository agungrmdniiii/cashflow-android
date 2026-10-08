package com.cashflow.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.ui.components.DuitAingLogo
import com.cashflow.app.ui.components.LogoVariant
import com.cashflow.app.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Minimalist, elegant Splash Screen for "Duit Aing"
 */
@Composable
fun SplashScreen(
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(1200)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PebbleBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            DuitAingLogo(size = 84.dp, variant = LogoVariant.COLOR)

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "DUIT AING",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp
                ),
                color = DarkSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "PERSONAL CASHFLOW",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontSize = 11.sp
                ),
                color = TextSecondary
            )
        }
    }
}
