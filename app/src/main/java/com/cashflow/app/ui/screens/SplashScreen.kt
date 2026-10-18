package com.cashflow.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cashflow.app.ui.theme.DarkColors

/** Shown only while the first database snapshot loads; no decorative startup delay. */
@Composable
fun SplashScreen() {
    Column(
        Modifier.fillMaxSize().background(DarkColors.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Duit Aing", style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator(Modifier.size(28.dp), color = Color.White)
    }
}
