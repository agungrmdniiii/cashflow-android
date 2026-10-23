package com.cashflow.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.data.model.TransactionType
import com.cashflow.app.data.model.ReportDateRange
import com.cashflow.app.data.model.transactionFilterError
import com.cashflow.app.ui.components.ReportDateRangeDialog
import com.cashflow.app.ui.components.ThousandSeparatorVisualTransformation
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.DateFilterPreset
import com.cashflow.app.ui.components.CategoryIcon
import com.cashflow.app.ui.components.DuitAingButton
import com.cashflow.app.ui.components.DuitAingButtonVariant
import com.cashflow.app.ui.components.FadingHorizontalScrollRow
import com.cashflow.app.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionFilterSheet(
    viewModel: CashflowViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    val currentType by viewModel.filterType.collectAsState()
    val currentCatId by viewModel.filterCategoryId.collectAsState()
    val currentAssetId by viewModel.filterAssetId.collectAsState()
    val currentDatePreset by viewModel.filterDatePreset.collectAsState()
    val currentStartDate by viewModel.filterStartDate.collectAsState()
    val currentEndDate by viewModel.filterEndDate.collectAsState()
    val currentMinAmount by viewModel.filterMinAmount.collectAsState()
    val currentMaxAmount by viewModel.filterMaxAmount.collectAsState()
    val activeCount by viewModel.activeFilterCount.collectAsState()

    // Temporary local state for editing within the sheet
    var tempType by remember { mutableStateOf(currentType) }
    var tempCatId by remember { mutableStateOf(currentCatId) }
    var tempAssetId by remember { mutableStateOf(currentAssetId) }
    var tempDatePreset by remember { mutableStateOf(currentDatePreset) }
    var tempStartDate by remember { mutableStateOf(currentStartDate) }
    var tempEndDate by remember { mutableStateOf(currentEndDate) }
    var tempMinAmountText by remember { mutableStateOf(currentMinAmount?.toString() ?: "") }
    var tempMaxAmountText by remember { mutableStateOf(currentMaxAmount?.toString() ?: "") }

    var showDateRangePicker by remember { mutableStateOf(false) }
    val filterError = transactionFilterError(tempMinAmountText, tempMaxAmountText,
        tempDatePreset == DateFilterPreset.CUSTOM, tempStartDate, tempEndDate)
    val amountTransformation = remember { ThousandSeparatorVisualTransformation() }

    val categories by viewModel.categories.collectAsState()
    val assets by viewModel.assets.collectAsState()

    val duit = MaterialTheme.duit
    val surfaceColor = duit.surface
    val onSurfaceColor = duit.textPrimary
    val onSurfaceVariantColor = duit.textSecondary
    val borderColor = duit.border

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = surfaceColor,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(duit.borderStrong)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = duit.textPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Filter Transaksi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onSurfaceColor
                    )
                    if (activeCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = duit.primary,
                            contentColor = duit.onPrimary
                        ) {
                            Text(
                                text = "$activeCount",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                TextButton(
                    onClick = {
                        tempType = "all"
                        tempCatId = null
                        tempAssetId = null
                        tempDatePreset = DateFilterPreset.ALL
                        tempStartDate = null
                        tempEndDate = null
                        tempMinAmountText = ""
                        tempMaxAmountText = ""
                    }
                ) {
                    Text(
                        text = "Reset",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = duit.expense
                    )
                }
            }

            HorizontalDivider(color = borderColor)

            // Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // 1. Rentang Tanggal
                Text(
                    text = "RENTANG TANGGAL",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = onSurfaceVariantColor,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateFilterPreset.values().forEach { preset ->
                        val isSelected = tempDatePreset == preset
                        FilterChip(
                            selected = isSelected,
                            onClick = { tempDatePreset = preset },
                            label = { Text(preset.label, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = duit.primary,
                                selectedLabelColor = duit.onPrimary,
                                containerColor = duit.surfaceVariant,
                                labelColor = duit.textSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) Color.Transparent else borderColor,
                                selectedBorderColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(999.dp)
                        )
                    }
                }

                AnimatedVisibility(visible = tempDatePreset == DateFilterPreset.CUSTOM) {
                    OutlinedCard(
                        onClick = { showDateRangePicker = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = surfaceColor),
                        border = BorderStroke(1.dp, borderColor)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Rounded.DateRange, contentDescription = null, tint = onSurfaceColor)
                            Column(Modifier.weight(1f)) {
                                Text("Rentang transaksi", style = MaterialTheme.typography.labelMedium, color = onSurfaceVariantColor)
                                val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.forLanguageTag("id-ID"))
                                Text(if (tempStartDate != null && tempEndDate != null)
                                    "${tempStartDate!!.format(formatter)} – ${tempEndDate!!.format(formatter)}"
                                    else "Pilih tanggal awal dan akhir",
                                    style = MaterialTheme.typography.bodyMedium, color = onSurfaceColor)
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = onSurfaceVariantColor)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Jenis Transaksi
                Text(
                    text = "JENIS TRANSAKSI",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = onSurfaceVariantColor,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                val typeOptions = listOf(
                    "all" to "Semua",
                    TransactionType.EXPENSE.value to "Pengeluaran",
                    TransactionType.INCOME.value to "Pemasukan",
                    TransactionType.TRANSFER.value to "Transfer"
                )

                FadingHorizontalScrollRow(modifier = Modifier.fillMaxWidth(), fadeColor = surfaceColor) {
                    typeOptions.forEach { (typeVal, label) ->
                        val isSelected = tempType == typeVal || (tempType == null && typeVal == "all")
                        FilterChip(
                            selected = isSelected,
                            onClick = { tempType = if (typeVal == "all") "all" else typeVal },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = duit.primary,
                                selectedLabelColor = duit.onPrimary,
                                containerColor = duit.surfaceVariant,
                                labelColor = duit.textSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) Color.Transparent else borderColor,
                                selectedBorderColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(999.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Rekening / Sumber Dana
                Text(
                    text = "REKENING / SUMBER DANA",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = onSurfaceVariantColor,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                FadingHorizontalScrollRow(modifier = Modifier.fillMaxWidth(), fadeColor = surfaceColor) {
                    val isAllAssets = tempAssetId == null
                    FilterChip(
                        selected = isAllAssets,
                        onClick = { tempAssetId = null },
                        label = { Text("Semua Rekening", style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = duit.primary,
                            selectedLabelColor = duit.onPrimary,
                            containerColor = duit.surfaceVariant,
                            labelColor = duit.textSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isAllAssets,
                            borderColor = if (isAllAssets) Color.Transparent else borderColor,
                            selectedBorderColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(999.dp)
                    )

                    assets.forEach { asset ->
                        val isSelected = tempAssetId == asset.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { tempAssetId = asset.id },
                            label = { Text(if (asset.isActive) asset.name else "${asset.name} (arsip)", style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = if (isSelected) duit.onPrimary else duit.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = duit.primary,
                                selectedLabelColor = duit.onPrimary,
                                containerColor = duit.surfaceVariant,
                                labelColor = duit.textSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) Color.Transparent else borderColor,
                                selectedBorderColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(999.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 4. Kategori
                Text(
                    text = "KATEGORI",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = onSurfaceVariantColor,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                FadingHorizontalScrollRow(modifier = Modifier.fillMaxWidth(), fadeColor = surfaceColor) {
                    val isAllCats = tempCatId == null
                    FilterChip(
                        selected = isAllCats,
                        onClick = { tempCatId = null },
                        label = { Text("Semua Kategori", style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = duit.primary,
                            selectedLabelColor = duit.onPrimary,
                            containerColor = duit.surfaceVariant,
                            labelColor = duit.textSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isAllCats,
                            borderColor = if (isAllCats) Color.Transparent else borderColor,
                            selectedBorderColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(999.dp)
                    )

                    categories.forEach { cat ->
                        val isSelected = tempCatId == cat.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { tempCatId = cat.id },
                            label = { Text(cat.name, style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = {
                                CategoryIcon(
                                    iconName = cat.icon,
                                    size = 18.dp,
                                    iconSize = 14.dp,
                                    tintColor = if (isSelected) duit.onPrimary else duit.textPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = duit.primary,
                                selectedLabelColor = duit.onPrimary,
                                containerColor = duit.surfaceVariant,
                                labelColor = duit.textSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) Color.Transparent else borderColor,
                                selectedBorderColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(999.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 5. Rentang Nominal
                Text(
                    text = "RENTANG NOMINAL (RP)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = onSurfaceVariantColor,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = tempMinAmountText,
                        onValueChange = { tempMinAmountText = it.filter { ch -> ch in '0'..'9' } },
                        modifier = Modifier.weight(1f),
                        label = { Text("Minimum") },
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = amountTransformation,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = surfaceColor,
                            unfocusedContainerColor = surfaceColor,
                            focusedBorderColor = duit.primary,
                            unfocusedBorderColor = borderColor
                        )
                    )

                    OutlinedTextField(
                        value = tempMaxAmountText,
                        onValueChange = { tempMaxAmountText = it.filter { ch -> ch in '0'..'9' } },
                        modifier = Modifier.weight(1f),
                        label = { Text("Maksimum") },
                        placeholder = { Text("Tak terbatas") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = amountTransformation,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = surfaceColor,
                            unfocusedContainerColor = surfaceColor,
                            focusedBorderColor = duit.primary,
                            unfocusedBorderColor = borderColor
                        )
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            if (filterError != null) {
                Text(filterError, color = duit.expense, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            }

            // Bottom Action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DuitAingButton(
                    text = "Batal",
                    onClick = onDismiss,
                    variant = DuitAingButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )

                DuitAingButton(
                    text = "Terapkan",
                    enabled = filterError == null,
                    onClick = {
                        if (filterError != null) return@DuitAingButton
                        viewModel.filterType.value = tempType
                        viewModel.filterCategoryId.value = tempCatId
                        viewModel.filterAssetId.value = tempAssetId
                        viewModel.filterDatePreset.value = tempDatePreset
                        viewModel.filterStartDate.value = tempStartDate
                        viewModel.filterEndDate.value = tempEndDate
                        viewModel.filterMinAmount.value = tempMinAmountText.toLongOrNull()
                        viewModel.filterMaxAmount.value = tempMaxAmountText.toLongOrNull()
                        onDismiss()
                    },
                    variant = DuitAingButtonVariant.PRIMARY,
                    modifier = Modifier.weight(2f)
                )
            }
        }
    }

    if (showDateRangePicker) {
        val initialRange = if (tempStartDate != null && tempEndDate != null && !tempEndDate!!.isBefore(tempStartDate))
            ReportDateRange(tempStartDate!!, tempEndDate!!) else null
        ReportDateRangeDialog(initialRange = initialRange, title = "Rentang transaksi",
            onDismiss = { showDateRangePicker = false }, onApply = {
                tempStartDate = it.start
                tempEndDate = it.end
                showDateRangePicker = false
            })
    }
}
