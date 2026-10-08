package com.cashflow.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.data.model.Budget
import com.cashflow.app.data.model.Category
import com.cashflow.app.ui.theme.*

/**
 * Reusable Dialog for Creating and Editing Category Budgets (Anggaran)
 * Integrated with tactile keyboard input, preset amount chips, and period selector.
 */
@Composable
fun BudgetFormDialog(
    visible: Boolean,
    editingBudget: Budget?,
    expenseCategories: List<Category>,
    onSave: (Budget) -> Unit,
    onUpdate: (Budget) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val isEditing = editingBudget != null
    var selectedCategoryId by remember(editingBudget) {
        mutableStateOf(editingBudget?.categoryId ?: expenseCategories.firstOrNull()?.id ?: "")
    }
    var targetAmountInput by remember(editingBudget) {
        mutableStateOf(editingBudget?.targetAmount?.toString() ?: "")
    }
    var periodTypeInput by remember(editingBudget) {
        mutableStateOf(editingBudget?.periodType ?: "monthly")
    }

    val amount = targetAmountInput.toLongOrNull() ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .imePadding()
            .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(16.dp)),
        containerColor = PebbleSurface,
        title = {
            Column {
                Text(
                    text = if (isEditing) "EDIT ANGGARAN" else "BUAT ANGGARAN BARU",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    ),
                    color = DarkSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isEditing) "Perbarui batas belanja kategori terpilih" else "Tetapkan batas belanja disiplin per pos pengeluaran",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Period Selector (BULANAN vs MINGGUAN)
                Column {
                    Text(
                        text = "PERIODE ANGGARAN",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        ),
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(PebbleSurfaceVariant)
                            .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("monthly" to "BULANAN", "weekly" to "MINGGUAN").forEach { (typeKey, label) ->
                            val isSelected = periodTypeInput == typeKey
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isSelected) {
                                            if (isSystemInDarkTheme()) JadePrimary else LightDarkSurface
                                        } else Color.Transparent
                                    )
                                    .clickable(role = Role.Tab) { periodTypeInput = typeKey }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        fontSize = 11.sp
                                    ),
                                    color = if (isSelected) {
                                        if (isSystemInDarkTheme()) Color(0xFF101413) else TextOnDark
                                    } else TextPrimary
                                )
                            }
                        }
                    }
                }

                // 2. Category Selector
                Column {
                    Text(
                        text = "PILIH KATEGORI",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        ),
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    CategoryDropdownField(
                        categories = expenseCategories,
                        selectedCategoryId = selectedCategoryId,
                        onCategorySelected = { selectedCategoryId = it.id },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 3. Target Amount with live Rupiah preview and quick chips
                Column {
                    Text(
                        text = "BATAS MAKSIMAL (PLAFON)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        ),
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = targetAmountInput,
                        onValueChange = { targetAmountInput = it.filter { c -> c.isDigit() } },
                        prefix = { Text("Rp ", fontWeight = FontWeight.Bold, color = JadePrimary) },
                        placeholder = { Text("0") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (amount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Terbaca: ${Formatters.formatRupiah(amount)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = JadePrimary
                        )
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tentukan batas belanja disiplin (minimal Rp 10.000)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Normal,
                                fontSize = 10.5.sp
                            ),
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick preset amount chips (+100rb, +500rb, +1jt, +2jt)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            100_000L to "+100rb",
                            500_000L to "+500rb",
                            1_000_000L to "+1jt",
                            2_000_000L to "+2jt"
                        ).forEach { (presetAmount, chipLabel) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(PebbleSurfaceVariant)
                                    .border(1.dp, DarkBorder.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                    .clickable(role = Role.Button) {
                                        val current = targetAmountInput.toLongOrNull() ?: 0L
                                        targetAmountInput = (current + presetAmount).toString()
                                    }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = chipLabel,
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
            }
        },
        confirmButton = {
            val isFormValid = selectedCategoryId.isNotBlank() && amount > 0L
            DuitAingButton(
                text = if (isEditing) "SIMPAN PERUBAHAN" else "SIMPAN ANGGARAN",
                enabled = isFormValid,
                onClick = {
                    if (isFormValid) {
                        if (isEditing) {
                            val updated = editingBudget!!.copy(
                                categoryId = selectedCategoryId,
                                targetAmount = amount,
                                periodType = periodTypeInput
                            )
                            onUpdate(updated)
                        } else {
                            val newBudget = Budget(
                                id = "bgt_${selectedCategoryId}_${System.currentTimeMillis()}",
                                categoryId = selectedCategoryId,
                                targetAmount = amount,
                                periodType = periodTypeInput
                            )
                            onSave(newBudget)
                        }
                    }
                },
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
