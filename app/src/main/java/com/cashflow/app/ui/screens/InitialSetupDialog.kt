package com.cashflow.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.data.model.AssetType
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.components.*
import com.cashflow.app.ui.theme.*

/**
 * Clean First-Launch Setup Screen for Fresh / Empty Installations
 * Allows user to set up their primary asset & starting balance before entering Home.
 */
@Composable
fun InitialSetupScreen(
    viewModel: CashflowViewModel
) {
    var nameInput by remember { mutableStateOf("Tunai (Dompet)") }
    var selectedType by remember { mutableStateOf(AssetType.TUNAI.displayName) }
    var openingBalanceInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val balance = openingBalanceInput.toLongOrNull() ?: 0L
    val duit = MaterialTheme.duit

    val nameSuggestions = remember(selectedType) {
        when (selectedType.lowercase()) {
            "tunai" -> listOf("Dompet Utama", "Uang Tunai", "Kas Kecil")
            "bank" -> listOf("BCA", "Mandiri", "BRI", "BNI", "Bank Jago", "SeaBank")
            "e-wallet" -> listOf("GoPay", "OVO", "DANA", "ShopeePay")
            else -> listOf("Rekening Utama", "Tabungan")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(duit.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon Badge
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(duit.primaryContainer)
                    .border(2.dp, duit.primary, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccountBalanceWallet,
                    contentDescription = null,
                    tint = duit.onPrimaryContainer,
                    modifier = Modifier.size(32.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "DUIT AING",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    ),
                    color = duit.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Setup Rekening / Aset Pertama Anda",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = duit.textSecondary
                )
            }

            // Card Form
            DoubleBezelCard(
                modifier = Modifier.fillMaxWidth(),
                outerPadding = 0.dp,
                outerRadius = 16.dp,
                innerRadius = 14.dp,
                shadowOffset = 0.dp,
                innerBorderColor = duit.border,
                innerColor = duit.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
                ) {
                    // 1. Tipe Aset
                    Column {
                        Text(
                            text = "TIPE ASET",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            ),
                            color = duit.textSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                        ) {
                            val types = listOf(AssetType.TUNAI, AssetType.BANK, AssetType.EWALLET)
                            types.forEach { type ->
                                val isSelected = selectedType == type.displayName
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) duit.primary else duit.surfaceVariant)
                                        .border(
                                            1.dp,
                                            if (isSelected) duit.primary else duit.border,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable(role = Role.RadioButton) {
                                            selectedType = type.displayName
                                            if (nameInput.isBlank() || nameInput == "Tunai (Dompet)" || nameInput == "BCA" || nameInput == "GoPay") {
                                                nameInput = when (type) {
                                                    AssetType.TUNAI -> "Dompet Utama"
                                                    AssetType.BANK -> "BCA"
                                                    AssetType.EWALLET -> "GoPay"
                                                    else -> "Aset Baru"
                                                }
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = type.displayName.uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                            fontSize = 12.sp
                                        ),
                                        color = if (isSelected) duit.onPrimary else duit.textPrimary
                                    )
                                }
                            }
                        }
                    }

                    // 2. Nama Aset
                    Column {
                        Text(
                            text = "NAMA ASET / REKENING",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            ),
                            color = duit.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            placeholder = { Text("Contoh: Dompet Utama, BCA, GoPay") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                        ) {
                            nameSuggestions.forEach { suggestion ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(duit.surfaceVariant)
                                        .border(1.dp, duit.border, RoundedCornerShape(6.dp))
                                        .clickable(role = Role.Button) { nameInput = suggestion }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = suggestion,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        ),
                                        color = duit.textPrimary
                                    )
                                }
                            }
                        }
                    }

                    // 3. Saldo Awal
                    Column {
                        Text(
                            text = "SALDO AWAL (RUPIAH)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            ),
                            color = duit.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = openingBalanceInput,
                            onValueChange = { input ->
                                val digitsOnly = input.filter { it.isDigit() }
                                openingBalanceInput = digitsOnly
                            },
                            placeholder = { Text("0") },
                            leadingIcon = {
                                Text(
                                    text = "Rp",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = duit.textSecondary,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Tombol Submit
                    DuitAingButton(
                        text = "MULAI MENCATAT",
                        onClick = {
                            viewModel.completeInitialSetup(
                                firstAssetName = nameInput.trim(),
                                assetType = selectedType,
                                initialBalance = balance
                            ) {}
                        },
                        enabled = nameInput.isNotBlank(),
                        trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                        modifier = Modifier.fillMaxWidth(),
                        variant = DuitAingButtonVariant.PRIMARY,
                        height = 48.dp,
                        shadowOffset = 0.dp
                    )
                }
            }

            Text(
                text = "Data disimpan 100% lokal & privat di perangkat Anda.",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = duit.textTertiary
            )
        }
    }
}
