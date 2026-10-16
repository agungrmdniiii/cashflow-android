package com.cashflow.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cashflow.app.data.model.Budget
import com.cashflow.app.data.model.Category
import com.cashflow.app.ui.theme.*

@Composable
fun BudgetFormDialog(
    visible: Boolean = false,
    isOpen: Boolean = visible,
    editingBudget: Budget? = null,
    categories: List<Category> = emptyList(),
    expenseCategories: List<Category> = categories,
    onDismiss: () -> Unit,
    onSave: (Budget) -> Unit = {},
    onUpdate: (Budget) -> Unit = {}
) {
    val show = visible || isOpen
    if (!show) return

    val isEditing = editingBudget != null
    val targetExpenseCategories = remember(categories, expenseCategories) {
        if (expenseCategories.isNotEmpty()) expenseCategories else categories.filter { it.type == "expense" && it.isActive }
    }

    var selectedCategoryId by remember(editingBudget) {
        mutableStateOf(editingBudget?.categoryId ?: targetExpenseCategories.firstOrNull()?.id ?: "")
    }
    var targetAmountInput by remember(editingBudget) {
        mutableStateOf(editingBudget?.targetAmount?.toString() ?: "")
    }
    var periodTypeInput by remember(editingBudget) {
        mutableStateOf(editingBudget?.periodType ?: "monthly")
    }

    val amount = targetAmountInput.toLongOrNull() ?: 0L
    val focusManager = LocalFocusManager.current
    val duit = MaterialTheme.duit
    val isDark = LocalIsDarkTheme.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    DuitAingModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
                    // Header Row with Title and Close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEditing) "Edit Anggaran" else "Buat Anggaran Baru",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = duit.textPrimary
                        )

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable(
                                    role = Role.Button,
                                    onClick = onDismiss
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(duit.surfaceVariant)
                                    .border(1.dp, duit.border, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Tutup",
                                    tint = duit.textPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Form Body
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
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
                                    .background(duit.surfaceVariant)
                                    .border(1.dp, duit.border, RoundedCornerShape(8.dp))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("monthly" to "BULANAN", "weekly" to "MINGGUAN").forEach { (typeKey, label) ->
                                    val isSelected = periodTypeInput == typeKey
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (isSelected) {
                                                    if (isDark) duit.accentLime else Color(0xFF111111)
                                                } else Color.Transparent
                                            )
                                            .clickable(role = Role.Tab) { periodTypeInput = typeKey }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                letterSpacing = 0.8.sp
                                            ),
                                            color = if (isSelected) {
                                                if (isDark) Color(0xFF111111) else Color.White
                                            } else duit.textSecondary
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Category Selector Dropdown
                        Column {
                            Text(
                                text = "KATEGORI PENGELUARAN",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp
                                ),
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            CategoryDropdownField(
                                categories = targetExpenseCategories,
                                selectedCategoryId = selectedCategoryId,
                                onCategorySelected = { selectedCategoryId = it.id },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // 3. Target Plafond Amount Field
                        Column {
                            Text(
                                text = "BATAS MAKSIMAL (PLAFON)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp
                                ),
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = targetAmountInput,
                                onValueChange = { targetAmountInput = it.filter { c -> c.isDigit() } },
                                prefix = {
                                    Text(
                                        text = "Rp ",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = duit.primary,
                                        fontSize = 18.sp
                                    )
                                },
                                placeholder = { Text("0") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { focusManager.clearFocus() }
                                ),
                                visualTransformation = ThousandSeparatorVisualTransformation(),
                                shape = RoundedCornerShape(10.dp),
                                textStyle = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = duit.primary,
                                    unfocusedBorderColor = duit.border,
                                    focusedContainerColor = duit.surface,
                                    unfocusedContainerColor = duit.surface,
                                    focusedTextColor = duit.textPrimary,
                                    unfocusedTextColor = duit.textPrimary,
                                    cursorColor = duit.primary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (amount > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Terbaca: ${Formatters.formatRupiah(amount)}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    color = duit.primary
                                )
                            }

                            // Quick preset chips
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                            ) {
                                listOf(
                                    250_000L to "+250rb",
                                    500_000L to "+500rb",
                                    1_000_000L to "+1jt",
                                    2_000_000L to "+2jt",
                                    5_000_000L to "+5jt"
                                ).forEach { (presetVal, chipText) ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(duit.surfaceVariant)
                                            .border(1.dp, duit.border, RoundedCornerShape(6.dp))
                                        .clickable(role = Role.Button) {
                                            val cur = targetAmountInput.toLongOrNull() ?: 0L
                                            targetAmountInput = (cur + presetVal).toString()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = chipText,
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
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons
                val isFormValid = selectedCategoryId.isNotBlank() && amount > 0L
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    DuitAingButton(
                        text = "BATAL",
                        onClick = onDismiss,
                        variant = DuitAingButtonVariant.OUTLINE,
                        modifier = Modifier.weight(1f),
                        height = 44.dp,
                        shadowOffset = 0.dp
                    )

                    DuitAingButton(
                        text = if (isEditing) "SIMPAN PERUBAHAN" else "SIMPAN ANGGARAN",
                        enabled = isFormValid,
                        onClick = {
                            if (isFormValid) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
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
                        trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                        modifier = Modifier.weight(1.5f),
                        variant = DuitAingButtonVariant.PRIMARY,
                        height = 44.dp,
                        shadowOffset = 0.dp
                    )
                }
            }
    }
}
