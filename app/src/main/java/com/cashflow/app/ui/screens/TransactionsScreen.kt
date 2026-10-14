package com.cashflow.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.data.model.FavoriteTemplate
import com.cashflow.app.data.model.Transaction
import com.cashflow.app.data.model.TransactionType
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.DateFilterPreset
import com.cashflow.app.ui.components.*
import com.cashflow.app.ui.theme.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: CashflowViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val isDark = LocalIsDarkTheme.current

    var showExportDialog by remember { mutableStateOf(false) }
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }
    val isFilterSheetOpen by viewModel.isFilterSheetOpen.collectAsState()

    val transactions by viewModel.filteredTransactionsTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterType by viewModel.filterType.collectAsState()
    val filterInputMethod by viewModel.filterInputMethod.collectAsState()
    val filterCatId by viewModel.filterCategoryId.collectAsState()
    val filterAssetId by viewModel.filterAssetId.collectAsState()
    val filterDatePreset by viewModel.filterDatePreset.collectAsState()
    val filterMinAmt by viewModel.filterMinAmount.collectAsState()
    val filterMaxAmt by viewModel.filterMaxAmount.collectAsState()
    val activeFilterCount by viewModel.activeFilterCount.collectAsState()
    val hasActiveFilters by viewModel.hasActiveFilters.collectAsState()

    val categories by viewModel.categories.collectAsState()
    val assets by viewModel.activeAssets.collectAsState()
    val isBalanceHidden by viewModel.isBalanceHidden.collectAsState()

    val catMap = remember(categories) { categories.associateBy { it.id } }
    val assetMap = remember(assets) { assets.associateBy { it.id } }

    // Group transactions by date
    val groupedTransactions = remember(transactions) {
        transactions.groupBy { it.transactionDate }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PebbleBackground)
    ) {
        // Screen Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = "Transaksi",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        letterSpacing = (-0.5).sp
                    ),
                    color = DarkSurface,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${transactions.size} transaksi tercatat",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = TextSecondary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Filter Bottom Sheet Button with Badge
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (activeFilterCount > 0) JadePrimary else PebbleSurface)
                        .border(1.dp, if (activeFilterCount > 0) Color.Transparent else PebbleBorder, CircleShape)
                        .clickable(role = Role.Button) {
                            viewModel.isFilterSheetOpen.value = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Filter Transaksi",
                        tint = if (activeFilterCount > 0) Color.White else DarkSurface,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Export Dialog Trigger Button
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(PebbleSurface)
                        .border(1.dp, PebbleBorder, CircleShape)
                        .clickable(role = Role.Button) {
                            showExportDialog = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = "Pilihan Ekspor (PDF / CSV)",
                        tint = DarkSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Catat Button
                Button(
                    onClick = {
                        viewModel.addTransactionInitialType.value = TransactionType.EXPENSE
                        viewModel.editingTransaction.value = null
                        viewModel.isAddTransactionOpen.value = true
                    },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FintechPrimary,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Catat",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp
                        )
                    )
                }
            }
        }

        // Search Bar (Multi-field: description, notes, category, amount)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.searchQuery.value = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            placeholder = { Text("Cari transaksi, nominal, catatan...", fontSize = 13.sp, color = TextSecondary) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = DarkSurface,
                    modifier = Modifier.size(19.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Hapus",
                            tint = DarkSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = PebbleSurface,
                unfocusedContainerColor = PebbleSurface,
                unfocusedBorderColor = DarkBorder.copy(alpha = 0.20f),
                focusedBorderColor = JadePrimary
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Active Filter Chips Row (if any filter is applied)
        AnimatedVisibility(visible = hasActiveFilters) {
            FadingHorizontalScrollRow(
                fadeColor = PebbleBackground,
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                // Reset all chip
                InputChip(
                    selected = true,
                    onClick = { viewModel.resetFilters() },
                    label = { Text("Reset Filter", style = MaterialTheme.typography.labelSmall) },
                    trailingIcon = {
                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    colors = InputChipDefaults.inputChipColors(
                        selectedContainerColor = StatusNegative.copy(alpha = 0.15f),
                        selectedLabelColor = StatusNegative
                    ),
                    shape = RoundedCornerShape(999.dp)
                )

                // Date filter chip
                if (filterDatePreset != DateFilterPreset.ALL) {
                    InputChip(
                        selected = true,
                        onClick = { viewModel.filterDatePreset.value = DateFilterPreset.ALL },
                        label = { Text(filterDatePreset.label, style = MaterialTheme.typography.labelSmall) },
                        trailingIcon = {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        colors = InputChipDefaults.inputChipColors(
                            selectedContainerColor = JadePrimary.copy(alpha = 0.15f),
                            selectedLabelColor = JadePrimary
                        ),
                        shape = RoundedCornerShape(999.dp)
                    )
                }

                // Category filter chip
                if (filterCatId != null) {
                    val catName = catMap[filterCatId]?.name ?: "Kategori"
                    InputChip(
                        selected = true,
                        onClick = { viewModel.filterCategoryId.value = null },
                        label = { Text(catName, style = MaterialTheme.typography.labelSmall) },
                        trailingIcon = {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        colors = InputChipDefaults.inputChipColors(
                            selectedContainerColor = JadePrimary.copy(alpha = 0.15f),
                            selectedLabelColor = JadePrimary
                        ),
                        shape = RoundedCornerShape(999.dp)
                    )
                }

                // Asset filter chip
                if (filterAssetId != null) {
                    val astName = assetMap[filterAssetId]?.name ?: "Aset"
                    InputChip(
                        selected = true,
                        onClick = { viewModel.filterAssetId.value = null },
                        label = { Text(astName, style = MaterialTheme.typography.labelSmall) },
                        trailingIcon = {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        colors = InputChipDefaults.inputChipColors(
                            selectedContainerColor = JadePrimary.copy(alpha = 0.15f),
                            selectedLabelColor = JadePrimary
                        ),
                        shape = RoundedCornerShape(999.dp)
                    )
                }

                // Amount filter chip
                if (filterMinAmt != null || filterMaxAmt != null) {
                    val label = when {
                        filterMinAmt != null && filterMaxAmt != null -> "${Formatters.formatCompact(filterMinAmt!!)} - ${Formatters.formatCompact(filterMaxAmt!!)}"
                        filterMinAmt != null -> "≥ ${Formatters.formatCompact(filterMinAmt!!)}"
                        else -> "≤ ${Formatters.formatCompact(filterMaxAmt!!)}"
                    }
                    InputChip(
                        selected = true,
                        onClick = {
                            viewModel.filterMinAmount.value = null
                            viewModel.filterMaxAmount.value = null
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        trailingIcon = {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        colors = InputChipDefaults.inputChipColors(
                            selectedContainerColor = JadePrimary.copy(alpha = 0.15f),
                            selectedLabelColor = JadePrimary
                        ),
                        shape = RoundedCornerShape(999.dp)
                    )
                }
            }
        }

        // Quick Type Filter Row
        FadingHorizontalScrollRow(
            fadeColor = PebbleBackground,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val typeOptions = listOf(
                "all" to "Semua",
                "expense" to "Pengeluaran",
                "income" to "Pemasukan",
                "transfer" to "Transfer"
            )

            typeOptions.forEach { (key, label) ->
                val isSelected = filterType == key
                val activeBg = when (key) {
                    "expense" -> StatusNegative
                    "income" -> FintechPrimary
                    "transfer" -> StatusTransfer
                    else -> if (isDark) FintechAccentLight else FintechPrimary
                }
                val activeText = when (key) {
                    "all" -> if (isDark) PebbleBackgroundDark else TextOnDark
                    else -> TextOnDark
                }
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 36.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isSelected) activeBg else PebbleSurface)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) Color.Transparent else PebbleBorder,
                            shape = RoundedCornerShape(999.dp)
                        )
                        .clickable(role = Role.Tab) { viewModel.filterType.value = key }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (key != "all") {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) activeText else activeBg)
                            )
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                letterSpacing = 0.4.sp
                            ),
                            color = if (isSelected) activeText else TextPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Transaction List grouped by date
        if (groupedTransactions.isEmpty()) {
            val hasActiveFilter = searchQuery.isNotBlank() || filterType != "all" || hasActiveFilters
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedEmptyState(
                    icon = if (hasActiveFilter) Icons.Rounded.SearchOff else Icons.AutoMirrored.Rounded.ReceiptLong,
                    title = if (hasActiveFilter) "TIDAK ADA TRANSAKSI COCOK" else "BELUM ADA TRANSAKSI",
                    description = if (hasActiveFilter)
                        "Coba ubah kata kunci atau sesuaikan filter transaksi."
                    else
                        "Mulai rekam pengeluaran atau pemasukan untuk memantau arus kas harian Anda.",
                    actionText = if (hasActiveFilter) "Reset Filter" else "Catat Transaksi",
                    actionIcon = if (hasActiveFilter) Icons.Rounded.FilterAltOff else Icons.Rounded.Add,
                    onActionClick = {
                        if (hasActiveFilter) {
                            viewModel.resetFilters()
                        } else {
                            viewModel.addTransactionInitialType.value = TransactionType.EXPENSE
                            viewModel.editingTransaction.value = null
                            viewModel.isAddTransactionOpen.value = true
                        }
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp, top = 4.dp)
            ) {
                groupedTransactions.forEach { (dateStr, txGroup) ->
                    // Daily subtotal calculation
                    val dayExpense = txGroup.filter { it.type == "expense" }.sumOf { it.amount }
                    val dayIncome = txGroup.filter { it.type == "income" }.sumOf { it.amount }

                    // Date Header Row with count and daily subtotal
                    item(key = "header_$dateStr") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = Formatters.formatDateGroup(dateStr),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                ),
                                color = DarkSurface
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (dayExpense > 0) {
                                    Text(
                                        text = if (isBalanceHidden) "−Rp ••••" else "−${Formatters.formatRupiah(dayExpense)}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        color = StatusNegative
                                    )
                                }
                                if (dayIncome > 0) {
                                    Text(
                                        text = if (isBalanceHidden) "+Rp ••••" else "+${Formatters.formatRupiah(dayIncome)}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        color = FintechSecondary
                                    )
                                }
                                Text(
                                    text = "(${txGroup.size})",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                    color = TextTertiary
                                )
                            }
                        }
                    }

                    // Transactions for that date (Swipe left=delete, Swipe right=edit, 3-dots accessibility menu)
                    items(txGroup, key = { it.id }) { tx ->
                        TransactionItemCard(
                            transaction = tx,
                            category = catMap[tx.categoryId],
                            sourceAsset = assetMap[tx.assetId],
                            destinationAsset = tx.destinationAssetId?.let { assetMap[it] },
                            isBalanceHidden = isBalanceHidden,
                            onClick = { viewModel.selectedTransactionForDetail.value = tx },
                            onEdit = {
                                viewModel.editingTransaction.value = tx
                                viewModel.isAddTransactionOpen.value = true
                            },
                            onDelete = {
                                transactionToDelete = tx
                            },
                            onSaveAsFavorite = {
                                val template = FavoriteTemplate(
                                    id = UUID.randomUUID().toString(),
                                    name = tx.description,
                                    type = tx.type,
                                    amount = tx.amount,
                                    categoryId = tx.categoryId,
                                    assetId = tx.assetId,
                                    destinationAssetId = tx.destinationAssetId,
                                    note = tx.note
                                )
                                viewModel.saveFavoriteTemplate(template)
                            }
                        )
                    }
                }
            }
        }
    }

    // Filter Bottom Sheet
    if (isFilterSheetOpen) {
        TransactionFilterSheet(
            viewModel = viewModel,
            onDismiss = { viewModel.isFilterSheetOpen.value = false }
        )
    }

    // Export Data Dialog
    ExportDataDialog(
        isOpen = showExportDialog,
        viewModel = viewModel,
        hasActiveFilter = searchQuery.isNotBlank() || filterType != "all" || hasActiveFilters,
        filterName = "Data Terfilter",
        onDismiss = { showExportDialog = false }
    )

    // Delete Confirmation Dialog (explicit confirmation naming amount and affected asset)
    transactionToDelete?.let { tx ->
        val assetName = assetMap[tx.assetId]?.name ?: "Aset Terpilih"
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    tint = StatusNegative,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Hapus Transaksi?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Transaksi \"${tx.description}\" senilai ${Formatters.formatRupiah(tx.amount)} akan dihapus.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Saldo rekening $assetName akan disesuaikan kembali secara otomatis.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val deletedTx = tx
                        transactionToDelete = null
                        viewModel.deleteTransactionWithUndo(deletedTx)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusNegative)
                ) {
                    Text("Hapus Transaksi", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { transactionToDelete = null }) {
                    Text("Batal")
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
