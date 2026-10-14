package com.cashflow.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.DateFilterPreset
import com.cashflow.app.ui.components.CategoryIcon
import com.cashflow.app.ui.components.DuitAingButton
import com.cashflow.app.ui.components.DuitAingButtonVariant
import com.cashflow.app.ui.components.FadingHorizontalScrollRow
import com.cashflow.app.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
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

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    val categories by viewModel.categories.collectAsState()
    val assets by viewModel.activeAssets.collectAsState()

    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = MaterialTheme.colorScheme.outlineVariant

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
                    .background(borderColor)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
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
                        tint = JadePrimary,
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
                            color = JadePrimary,
                            contentColor = Color.White
                        ) {
                            Text(
                                text = "$activeCount",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
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
                        color = StatusNegative
                    )
                }
            }

            HorizontalDivider(color = borderColor.copy(alpha = 0.5f))

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

                FadingHorizontalScrollRow(modifier = Modifier.fillMaxWidth()) {
                    DateFilterPreset.values().forEach { preset ->
                        val isSelected = tempDatePreset == preset
                        FilterChip(
                            selected = isSelected,
                            onClick = { tempDatePreset = preset },
                            label = { Text(preset.label, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = JadePrimary,
                                selectedLabelColor = Color.White,
                                containerColor = surfaceColor,
                                labelColor = onSurfaceColor
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) JadePrimary else borderColor
                            ),
                            shape = RoundedCornerShape(999.dp)
                        )
                    }
                }

                // Custom date pickers when CUSTOM is selected
                AnimatedVisibility(visible = tempDatePreset == DateFilterPreset.CUSTOM) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedCard(
                                onClick = { showStartDatePicker = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.outlinedCardColors(containerColor = surfaceColor),
                                border = BorderStroke(1.dp, borderColor)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "Dari Tanggal",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = onSurfaceVariantColor
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = tempStartDate?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) ?: "Pilih...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = onSurfaceColor
                                    )
                                }
                            }

                            OutlinedCard(
                                onClick = { showEndDatePicker = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.outlinedCardColors(containerColor = surfaceColor),
                                border = BorderStroke(1.dp, borderColor)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "Sampai Tanggal",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = onSurfaceVariantColor
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = tempEndDate?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) ?: "Pilih...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = onSurfaceColor
                                    )
                                }
                            }
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

                FadingHorizontalScrollRow(modifier = Modifier.fillMaxWidth()) {
                    typeOptions.forEach { (typeVal, label) ->
                        val isSelected = tempType == typeVal || (tempType == null && typeVal == "all")
                        FilterChip(
                            selected = isSelected,
                            onClick = { tempType = if (typeVal == "all") "all" else typeVal },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = JadePrimary,
                                selectedLabelColor = Color.White,
                                containerColor = surfaceColor,
                                labelColor = onSurfaceColor
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) JadePrimary else borderColor
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

                FadingHorizontalScrollRow(modifier = Modifier.fillMaxWidth()) {
                    val isAllAssets = tempAssetId == null
                    FilterChip(
                        selected = isAllAssets,
                        onClick = { tempAssetId = null },
                        label = { Text("Semua Rekening", style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = JadePrimary,
                            selectedLabelColor = Color.White,
                            containerColor = surfaceColor,
                            labelColor = onSurfaceColor
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isAllAssets,
                            borderColor = if (isAllAssets) JadePrimary else borderColor
                        ),
                        shape = RoundedCornerShape(999.dp)
                    )

                    assets.forEach { asset ->
                        val isSelected = tempAssetId == asset.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { tempAssetId = asset.id },
                            label = { Text(asset.name, style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.AccountBalanceWallet,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = JadePrimary,
                                selectedLabelColor = Color.White,
                                containerColor = surfaceColor,
                                labelColor = onSurfaceColor
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) JadePrimary else borderColor
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

                FadingHorizontalScrollRow(modifier = Modifier.fillMaxWidth()) {
                    val isAllCats = tempCatId == null
                    FilterChip(
                        selected = isAllCats,
                        onClick = { tempCatId = null },
                        label = { Text("Semua Kategori", style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = JadePrimary,
                            selectedLabelColor = Color.White,
                            containerColor = surfaceColor,
                            labelColor = onSurfaceColor
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isAllCats,
                            borderColor = if (isAllCats) JadePrimary else borderColor
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
                                    tintColor = if (isSelected) Color.White else JadePrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = JadePrimary,
                                selectedLabelColor = Color.White,
                                containerColor = surfaceColor,
                                labelColor = onSurfaceColor
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) JadePrimary else borderColor
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
                        onValueChange = { tempMinAmountText = it.filter { ch -> ch.isDigit() } },
                        modifier = Modifier.weight(1f),
                        label = { Text("Min. Nominal") },
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JadePrimary,
                            unfocusedBorderColor = borderColor
                        )
                    )

                    OutlinedTextField(
                        value = tempMaxAmountText,
                        onValueChange = { tempMaxAmountText = it.filter { ch -> ch.isDigit() } },
                        modifier = Modifier.weight(1f),
                        label = { Text("Maks. Nominal") },
                        placeholder = { Text("Tak terbatas") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JadePrimary,
                            unfocusedBorderColor = borderColor
                        )
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
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
                    variant = DuitAingButtonVariant.OUTLINE,
                    modifier = Modifier.weight(1f)
                )

                DuitAingButton(
                    text = "Terapkan Filter",
                    onClick = {
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

    // Start Date Picker Dialog
    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = tempStartDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            tempStartDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        }
                        showStartDatePicker = false
                    }
                ) { Text("Pilih") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("Batal") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // End Date Picker Dialog
    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = tempEndDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            tempEndDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        }
                        showEndDatePicker = false
                    }
                ) { Text("Pilih") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("Batal") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
