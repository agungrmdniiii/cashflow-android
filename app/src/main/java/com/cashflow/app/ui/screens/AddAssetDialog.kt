package com.cashflow.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import com.cashflow.app.data.model.Asset
import com.cashflow.app.data.model.AssetType
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.components.AssetTypeDropdownField
import com.cashflow.app.ui.components.DuitAingButton
import com.cashflow.app.ui.components.DuitAingButtonVariant
import com.cashflow.app.ui.components.Formatters
import com.cashflow.app.ui.theme.*
import java.util.UUID

@Composable
fun AddAssetDialog(
    viewModel: CashflowViewModel,
    onDismiss: () -> Unit
) {
    var nameInput by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(AssetType.BANK.displayName) }
    var openingBalanceInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }
    var isDefaultChecked by remember { mutableStateOf(false) }

    val balance = openingBalanceInput.toLongOrNull() ?: 0L

    val nameSuggestions = remember(selectedType) {
        when (selectedType.lowercase()) {
            "bank" -> listOf("BCA", "Mandiri", "BRI", "BNI", "BSI", "Bank Jago", "SeaBank")
            "e-wallet" -> listOf("GoPay", "OVO", "DANA", "ShopeePay", "LinkAja")
            "tunai" -> listOf("Dompet Utama", "Uang Tunai", "Kas Kecil")
            "tabungan" -> listOf("Tabungan Utama", "Deposito", "Bibit", "Emas")
            "kartu kredit" -> listOf("BCA Card", "Mandiri Card", "Mega Card")
            else -> listOf("Akun Baru")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .imePadding()
            .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(14.dp)),
        containerColor = PebbleSurface,
        title = {
            Column {
                Text(
                    text = "TAMBAH ASET BARU",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    ),
                    color = DarkSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Kelola rekening, e-wallet, atau instrumen simpanan",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Asset Type Selector
                Column {
                    Text(
                        text = "TIPE ASET",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        ),
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    AssetTypeDropdownField(
                        selectedType = selectedType,
                        onTypeSelected = { selectedType = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 2. Asset Name with quick suggestions
                Column {
                    Text(
                        text = "NAMA ASET",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        ),
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        placeholder = { Text("Contoh: BCA, GoPay, Dompet") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        nameSuggestions.forEach { suggestion ->
                            Box(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(PebbleSurfaceVariant)
                                    .border(1.dp, DarkBorder.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                    .clickable(role = Role.Button) { nameInput = suggestion }
                                    .padding(horizontal = 9.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = suggestion,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.5.sp
                                    ),
                                    color = DarkSurface
                                )
                            }
                        }
                    }
                }

                // 3. Opening Balance with live Rupiah preview and preset chips
                Column {
                    Text(
                        text = "SALDO AWAL",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        ),
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = openingBalanceInput,
                        onValueChange = { openingBalanceInput = it.filter { c -> c.isDigit() } },
                        prefix = { Text("Rp ", fontWeight = FontWeight.Bold, color = JadePrimary) },
                        placeholder = { Text("0") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (balance > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Terbaca: ${Formatters.formatRupiah(balance)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = JadePrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            100_000L to "+100rb",
                            500_000L to "+500rb",
                            1_000_000L to "+1jt",
                            5_000_000L to "+5jt",
                            10_000_000L to "+10jt"
                        ).forEach { (presetVal, chipText) ->
                            Box(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(PebbleSurfaceVariant)
                                    .border(1.dp, DarkBorder.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                    .clickable(role = Role.Button) {
                                        val cur = openingBalanceInput.toLongOrNull() ?: 0L
                                        openingBalanceInput = (cur + presetVal).toString()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = chipText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.5.sp
                                    ),
                                    color = DarkSurface
                                )
                            }
                        }
                    }
                }

                // 4. Notes
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Catatan (Opsional)") },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // 5. Default checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Checkbox) { isDefaultChecked = !isDefaultChecked }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = isDefaultChecked,
                        onCheckedChange = { isDefaultChecked = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Jadikan Aset Utama (Default)",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = DarkSurface
                        )
                        Text(
                            text = "Dipilih otomatis saat mencatat transaksi baru",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = TextSecondary
                        )
                    }
                }
            }
        },
        confirmButton = {
            DuitAingButton(
                text = "SIMPAN ASET",
                enabled = nameInput.isNotBlank(),
                onClick = {
                    if (nameInput.isNotBlank()) {
                        val newAsset = Asset(
                            id = "asset_${UUID.randomUUID()}",
                            name = nameInput.trim(),
                            type = selectedType,
                            openingBalance = balance,
                            currentBalance = balance,
                            isDefault = isDefaultChecked,
                            note = noteInput.trim()
                        )
                        viewModel.saveAsset(newAsset, isNew = true) {
                            onDismiss()
                        }
                    }
                },
                trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                variant = DuitAingButtonVariant.PRIMARY,
                height = 42.dp,
                shadowOffset = 2.dp
            )
        },
        dismissButton = {
            DuitAingButton(
                text = "BATAL",
                onClick = onDismiss,
                variant = DuitAingButtonVariant.OUTLINE,
                height = 42.dp,
                shadowOffset = 2.dp
            )
        }
    )
}
