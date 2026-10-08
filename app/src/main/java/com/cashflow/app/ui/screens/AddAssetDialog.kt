package com.cashflow.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    assetToEdit: Asset? = null,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            AddAssetContent(
                viewModel = viewModel,
                assetToEdit = assetToEdit,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
fun AddAssetContent(
    viewModel: CashflowViewModel,
    assetToEdit: Asset? = null,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val isEditing = assetToEdit != null
    var nameInput by remember(assetToEdit) { mutableStateOf(assetToEdit?.name ?: "") }
    var selectedType by remember(assetToEdit) { mutableStateOf(assetToEdit?.type ?: AssetType.BANK.displayName) }
    var openingBalanceInput by remember(assetToEdit) { mutableStateOf(assetToEdit?.let { it.currentBalance.toString() } ?: "") }
    var noteInput by remember(assetToEdit) { mutableStateOf(assetToEdit?.note ?: "") }
    var isDefaultChecked by remember(assetToEdit) { mutableStateOf(assetToEdit?.isDefault ?: false) }

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

    Card(
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.92f)
            .padding(vertical = 12.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, DarkBorder),
        colors = CardDefaults.cardColors(containerColor = PebbleSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isEditing) "EDIT ASET" else "TAMBAH ASET BARU",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                            fontSize = 18.sp
                        ),
                        color = DarkSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isEditing) "Perbarui informasi rekening atau instrumen simpanan" else "Kelola rekening, e-wallet, atau instrumen simpanan",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(
                            role = Role.Button,
                            onClick = { onBack?.invoke() ?: onDismiss() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(PebbleSurfaceVariant)
                            .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Tutup",
                            tint = DarkSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Scrollable Form Fields
            Column(
                modifier = Modifier
                    .weight(1f)
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
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(PebbleSurfaceVariant)
                                    .border(1.dp, DarkBorder.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                    .clickable(role = Role.Button) { nameInput = suggestion }
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
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

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DuitAingButton(
                    text = "BATAL",
                    onClick = { onBack?.invoke() ?: onDismiss() },
                    variant = DuitAingButtonVariant.OUTLINE,
                    modifier = Modifier.weight(1f),
                    height = 44.dp,
                    shadowOffset = 2.dp
                )

                DuitAingButton(
                    text = if (isEditing) "SIMPAN PERUBAHAN" else "SIMPAN ASET",
                    enabled = nameInput.isNotBlank(),
                    onClick = {
                        if (nameInput.isNotBlank()) {
                            if (isEditing) {
                                val updatedAsset = assetToEdit!!.copy(
                                    name = nameInput.trim(),
                                    type = selectedType,
                                    openingBalance = balance,
                                    currentBalance = balance,
                                    isDefault = isDefaultChecked,
                                    note = noteInput.trim(),
                                    updatedAt = System.currentTimeMillis()
                                )
                                viewModel.saveAsset(updatedAsset, isNew = false) {
                                    onDismiss()
                                }
                            } else {
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
                        }
                    },
                    trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                    modifier = Modifier.weight(1.5f),
                    variant = DuitAingButtonVariant.PRIMARY,
                    height = 44.dp,
                    shadowOffset = 2.dp
                )
            }
        }
    }
}
