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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.cashflow.app.ui.theme.AppSpacing
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
import androidx.compose.ui.graphics.Color
import com.cashflow.app.data.model.Asset
import com.cashflow.app.data.model.FinancialGoal
import com.cashflow.app.ui.theme.LocalIsDarkTheme
import com.cashflow.app.ui.theme.duit
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
    val focusManager = LocalFocusManager.current
    val duit = MaterialTheme.duit

    DuitAingModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = if (isEditing) "EDIT TARGET KEUANGAN" else "BUAT TARGET KEUANGAN",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                ),
                color = duit.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Tetapkan sasaran tabungan, nominal target, dan tenggat waktu",
                style = MaterialTheme.typography.bodySmall,
                color = duit.textSecondary
            )
            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it; errorMessage = null },
                    label = { Text("Nama Target (misal: Dana Darurat)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = targetInput,
                    onValueChange = { targetInput = it.filter { c -> c.isDigit() }; errorMessage = null },
                    label = { Text("Nominal Sasaran Target (Rp)") },
                    prefix = { Text("Rp ", fontWeight = FontWeight.Bold, color = duit.primary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    visualTransformation = ThousandSeparatorVisualTransformation(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = currentInput,
                    onValueChange = { currentInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Sudah Terkumpul Saat Ini (Rp)") },
                    prefix = { Text("Rp ", fontWeight = FontWeight.Bold, color = duit.primary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = duit.expense,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                DuitAingButton(
                    text = "BATAL",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    variant = DuitAingButtonVariant.OUTLINE,
                    height = 44.dp,
                    shadowOffset = 0.dp
                )

                DuitAingButton(
                    text = if (isEditing) "SIMPAN PERUBAHAN" else "BUAT TARGET",
                    variant = DuitAingButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1.3f),
                    height = 44.dp,
                    shadowOffset = 0.dp,
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
    var selectedSourceAssetId by remember { mutableStateOf(goal.assetId ?: assets.firstOrNull()?.id ?: "") }
    var noteInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val depositAmount = depositAmountText.toLongOrNull() ?: 0L
    val remainingToTarget = goal.remainingAmount
    val focusManager = LocalFocusManager.current

    val quickAmounts = listOf(50000L, 100000L, 250000L, 500000L, 1000000L)

    val duit = MaterialTheme.duit
    val isDark = LocalIsDarkTheme.current

    DuitAingModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "TABUNG KE TARGET",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                ),
                color = duit.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${goal.name} • Sisa sasaran: ${Formatters.formatRupiah(remainingToTarget)}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = duit.textSecondary
            )
            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                // Info Box: Progres Terkini
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(duit.surfaceVariant.copy(alpha = 0.5f))
                        .border(1.dp, duit.border, RoundedCornerShape(10.dp))
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
                                color = duit.primary
                            )
                            Text(
                                "Target: ${Formatters.formatRupiah(goal.targetAmount)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = duit.textSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { goal.progressPercentage.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = duit.primary,
                            trackColor = duit.surface
                        )
                    }
                }

                // Input Nominal
                OutlinedTextField(
                    value = depositAmountText,
                    onValueChange = { depositAmountText = it.filter { c -> c.isDigit() }; errorMessage = null },
                    label = { Text("Nominal Tabungan (Rp)") },
                    prefix = { Text("Rp ", fontWeight = FontWeight.Bold, color = duit.primary) },
                    placeholder = { Text("0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    visualTransformation = ThousandSeparatorVisualTransformation(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick nominal chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    quickAmounts.forEach { chipAmt ->
                        val isSelected = depositAmount == chipAmt
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) (if (isDark) duit.accentLime else Color(0xFF111111)) else duit.surfaceVariant)
                                .border(1.dp, duit.border, RoundedCornerShape(6.dp))
                                .clickable(role = Role.Button) {
                                    depositAmountText = chipAmt.toString()
                                    errorMessage = null
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "+${Formatters.formatRupiah(chipAmt)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = if (isSelected) (if (isDark) Color(0xFF111111) else Color.White) else duit.textPrimary
                            )
                        }
                    }
                }

                Text(
                    "Dana tetap berada di rekening. Alokasi tabungan bukan pengeluaran belanja.",
                    style = MaterialTheme.typography.bodySmall,
                    color = duit.textSecondary
                )
                if (assets.isNotEmpty()) {
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = duit.expense,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                DuitAingButton(
                    text = "BATAL",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    variant = DuitAingButtonVariant.OUTLINE,
                    height = 44.dp,
                    shadowOffset = 0.dp
                )

                DuitAingButton(
                    text = "SIMPAN TABUNGAN",
                    variant = DuitAingButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1.3f),
                    height = 44.dp,
                    shadowOffset = 0.dp,
                    onClick = {
                        if (depositAmount <= 0) {
                            errorMessage = "Nominal tabungan harus lebih dari 0"
                            return@DuitAingButton
                        }
                        val sourceAsset = selectedSourceAssetId.ifBlank { null }
                        onConfirmDeposit(goal.id, depositAmount, sourceAsset, noteInput.trim())
                    }
                )
            }
        }
    }
}
