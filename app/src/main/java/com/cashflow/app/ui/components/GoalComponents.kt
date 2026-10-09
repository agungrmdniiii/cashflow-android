package com.cashflow.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.data.model.Asset
import com.cashflow.app.data.model.FinancialGoal
import com.cashflow.app.ui.theme.DarkBorder
import com.cashflow.app.ui.theme.DarkSurface
import com.cashflow.app.ui.theme.JadePrimary
import com.cashflow.app.ui.theme.PebbleSurface
import com.cashflow.app.ui.theme.PebbleSurfaceVariant
import com.cashflow.app.ui.theme.StatusNegative
import com.cashflow.app.ui.theme.TextOnDark
import com.cashflow.app.ui.theme.TextPrimary
import com.cashflow.app.ui.theme.TextSecondary
import java.time.LocalDate

@Composable
fun GoalFormSheet(
    isOpen: Boolean,
    goalToEdit: FinancialGoal? = null,
    assets: List<Asset> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (name: String, targetAmount: Long, currentAmount: Long, deadline: String, assetId: String?, note: String) -> Unit
) {
    if (!isOpen) return

    val isEditing = goalToEdit != null
    var nameInput by remember(goalToEdit) { mutableStateOf(goalToEdit?.name ?: "") }
    var targetInput by remember(goalToEdit) { mutableStateOf(goalToEdit?.targetAmount?.toString() ?: "") }
    var currentInput by remember(goalToEdit) { mutableStateOf(goalToEdit?.currentAmount?.toString() ?: "0") }
    var deadlineInput by remember(goalToEdit) {
        mutableStateOf(goalToEdit?.deadline ?: LocalDate.now().plusMonths(6).toString())
    }
    var selectedAssetId by remember(goalToEdit) { mutableStateOf(goalToEdit?.assetId ?: "") }
    var noteInput by remember(goalToEdit) { mutableStateOf(goalToEdit?.note ?: "") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val targetAmount = targetInput.toLongOrNull() ?: 0L
    val currentAmount = currentInput.toLongOrNull() ?: 0L

    DuitAingModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 10.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = if (isEditing) "EDIT TARGET KEUANGAN" else "BUAT TARGET KEUANGAN",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                ),
                color = DarkSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Tetapkan sasaran tabungan, nominal target, dan tenggat waktu",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it; errorMessage = null },
                    label = { Text("Nama Target (misal: Dana Darurat)") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = targetInput,
                    onValueChange = { targetInput = it.filter { c -> c.isDigit() }; errorMessage = null },
                    label = { Text("Nominal Sasaran Target (Rp)") },
                    prefix = { Text("Rp ", fontWeight = FontWeight.Bold, color = JadePrimary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = ThousandSeparatorVisualTransformation(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = currentInput,
                    onValueChange = { currentInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Sudah Terkumpul Saat Ini (Rp)") },
                    prefix = { Text("Rp ", fontWeight = FontWeight.Bold, color = JadePrimary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = ThousandSeparatorVisualTransformation(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                DuitAingDatePickerField(
                    dateIsoString = deadlineInput.ifBlank { LocalDate.now().plusMonths(3).toString() },
                    onDateSelected = { deadlineInput = it },
                    label = "Tenggat Waktu / Deadline",
                    modifier = Modifier.fillMaxWidth()
                )

                if (assets.isNotEmpty()) {
                    AssetDropdownField(
                        assets = assets,
                        selectedAssetId = selectedAssetId,
                        onAssetSelected = { selectedAssetId = it.id },
                        placeholder = "PILIH REKENING TERKAIT (OPSIONAL)",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Catatan / Keterangan (Opsional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = StatusNegative,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DuitAingButton(
                    text = "BATAL",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    variant = DuitAingButtonVariant.OUTLINE,
                    height = 44.dp,
                    shadowOffset = 2.dp
                )

                DuitAingButton(
                    text = if (isEditing) "SIMPAN PERUBAHAN" else "BUAT TARGET",
                    variant = DuitAingButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1.3f),
                    height = 44.dp,
                    shadowOffset = 2.dp,
                    onClick = {
                        if (nameInput.isBlank()) {
                            errorMessage = "Nama target wajib diisi"
                            return@DuitAingButton
                        }
                        if (targetAmount <= 0) {
                            errorMessage = "Nominal target harus lebih dari 0"
                            return@DuitAingButton
                        }
                        onSave(
                            nameInput.trim(),
                            targetAmount,
                            currentAmount,
                            deadlineInput,
                            selectedAssetId.ifBlank { null },
                            noteInput.trim()
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun DepositGoalSheet(
    goal: FinancialGoal?,
    assets: List<Asset> = emptyList(),
    onDismiss: () -> Unit,
    onConfirmDeposit: (goalId: String, amount: Long, sourceAssetId: String?, note: String) -> Unit
) {
    if (goal == null) return

    var depositAmountText by remember { mutableStateOf("") }
    var deductFromAsset by remember { mutableStateOf(false) }
    var selectedSourceAssetId by remember { mutableStateOf(assets.firstOrNull()?.id ?: "") }
    var noteInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val depositAmount = depositAmountText.toLongOrNull() ?: 0L
    val remainingToTarget = goal.remainingAmount

    val quickAmounts = listOf(50000L, 100000L, 250000L, 500000L, 1000000L)

    DuitAingModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 10.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "TABUNG KE TARGET",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                ),
                color = DarkSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${goal.name} • Sisa sasaran: ${Formatters.formatRupiah(remainingToTarget)}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Info Box: Progres Terkini
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(PebbleSurfaceVariant.copy(alpha = 0.5f))
                        .border(1.dp, DarkBorder.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Terkumpul: ${Formatters.formatRupiah(goal.currentAmount)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = JadePrimary
                            )
                            Text(
                                "Target: ${Formatters.formatRupiah(goal.targetAmount)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { goal.progressPercentage.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = JadePrimary,
                            trackColor = PebbleSurface
                        )
                    }
                }

                // Input Nominal
                OutlinedTextField(
                    value = depositAmountText,
                    onValueChange = { depositAmountText = it.filter { c -> c.isDigit() }; errorMessage = null },
                    label = { Text("Nominal Tabungan (Rp)") },
                    prefix = { Text("Rp ", fontWeight = FontWeight.Bold, color = JadePrimary) },
                    placeholder = { Text("0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = ThousandSeparatorVisualTransformation(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick nominal chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickAmounts.forEach { chipAmt ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (depositAmount == chipAmt) JadePrimary else PebbleSurfaceVariant.copy(alpha = 0.6f))
                                .border(1.dp, DarkBorder.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                .clickable(role = Role.Button) {
                                    depositAmountText = chipAmt.toString()
                                    errorMessage = null
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "+${Formatters.formatRupiah(chipAmt)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = if (depositAmount == chipAmt) TextOnDark else TextPrimary
                            )
                        }
                    }
                }

                // Deduct from Asset Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Checkbox) { deductFromAsset = !deductFromAsset }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = deductFromAsset,
                        onCheckedChange = { deductFromAsset = it },
                        colors = CheckboxDefaults.colors(checkedColor = JadePrimary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            "Kurangi Saldo Rekening / Dompet",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            "Mencatat otomatis pengeluaran tabungan di arus kas",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                if (deductFromAsset && assets.isNotEmpty()) {
                    AssetDropdownField(
                        assets = assets,
                        selectedAssetId = selectedSourceAssetId,
                        onAssetSelected = { selectedSourceAssetId = it.id },
                        placeholder = "PILIH REKENING SUMBER DANA",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Catatan / Keterangan (Opsional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = StatusNegative,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DuitAingButton(
                    text = "BATAL",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    variant = DuitAingButtonVariant.OUTLINE,
                    height = 44.dp,
                    shadowOffset = 2.dp
                )

                DuitAingButton(
                    text = "SIMPAN TABUNGAN",
                    variant = DuitAingButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1.3f),
                    height = 44.dp,
                    shadowOffset = 2.dp,
                    onClick = {
                        if (depositAmount <= 0) {
                            errorMessage = "Nominal tabungan harus lebih dari 0"
                            return@DuitAingButton
                        }
                        val sourceAsset = if (deductFromAsset) selectedSourceAssetId.ifBlank { null } else null
                        onConfirmDeposit(goal.id, depositAmount, sourceAsset, noteInput.trim())
                    }
                )
            }
        }
    }
}
