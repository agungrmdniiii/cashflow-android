package com.cashflow.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import com.cashflow.app.data.model.Transaction
import com.cashflow.app.data.model.TransactionType
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.components.*
import com.cashflow.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: CashflowViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showExportDialog by remember { mutableStateOf(false) }
    val transactions by viewModel.filteredTransactionsTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterType by viewModel.filterType.collectAsState()
    val filterInputMethod by viewModel.filterInputMethod.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val assets by viewModel.activeAssets.collectAsState()

    val catMap = remember(categories) { categories.associateBy { it.id } }
    val assetMap = remember(assets) { assets.associateBy { it.id } }

    // Group transactions by date
    val groupedTransactions = remember(transactions) {
        transactions.groupBy { it.transactionDate }
    }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PebbleBackground)
    ) {
        // Screen Header with spacious layout
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = "TRANSAKSI",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.3).sp
                    ),
                    color = DarkSurface,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.height(3.dp))
                EyebrowTag(
                    text = "${transactions.size} CATATAN TERCATAT",
                    variant = EyebrowVariant.DEFAULT
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Export Dialog Trigger Button (Accessible >= 48dp touch target)
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PebbleSurface)
                        .border(1.dp, DarkBorder.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
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

                DuitAingButton(
                    text = "CATAT MANUAL",
                    onClick = {
                        viewModel.addTransactionInitialType.value = TransactionType.EXPENSE
                        viewModel.editingTransaction.value = null
                        viewModel.isAddTransactionOpen.value = true
                    },
                    leadingIcon = Icons.Rounded.Add,
                    variant = DuitAingButtonVariant.PRIMARY,
                    height = 44.dp,
                    shadowOffset = 2.dp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search Bar Card (Clean, Spacious & Focused)
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

        Spacer(modifier = Modifier.height(12.dp))

        // 1. Transaction Type Filter Row (Jade Pebble Morning Tactile Pills)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val typeOptions = listOf(
                "all" to "SEMUA",
                "expense" to "PENGELUARAN",
                "income" to "PEMASUKAN",
                "transfer" to "TRANSFER"
            )

            typeOptions.forEach { (key, label) ->
                val isSelected = filterType == key
                val activeBg = when (key) {
                    "expense" -> StatusNegative
                    "income" -> JadePrimary
                    "transfer" -> StatusTransfer
                    else -> DarkSurface
                }
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) activeBg else PebbleSurface)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) DarkBorder.copy(alpha = 0.35f) else PebbleBorder,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable(role = Role.Tab) { viewModel.filterType.value = key }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (key != "all") {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) TextOnDark else activeBg)
                            )
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                letterSpacing = 0.4.sp
                            ),
                            color = if (isSelected) TextOnDark else DarkSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. Input Method Filter Row (SEMUA METODE | SUARA | MANUAL)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val voiceFilterOptions = listOf(
                "all" to "SEMUA METODE",
                "voice" to "SUARA",
                "manual" to "MANUAL"
            )
            voiceFilterOptions.forEach { (key, label) ->
                val isSelected = filterInputMethod == key
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) JadePrimaryContainer else PebbleSurfaceVariant.copy(alpha = 0.6f))
                        .border(
                            width = 1.dp,
                            color = if (isSelected) JadePrimary.copy(alpha = 0.5f) else DarkBorder.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable(role = Role.Tab) { viewModel.filterInputMethod.value = key }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (key == "voice") {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = null,
                                tint = if (isSelected) JadePrimary else TextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                        } else if (key == "manual") {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                                tint = if (isSelected) JadePrimary else TextSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                letterSpacing = 0.4.sp
                            ),
                            color = if (isSelected) JadePrimary else TextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Transaction List grouped by date
        if (groupedTransactions.isEmpty()) {
            val hasActiveFilter = searchQuery.isNotBlank() || filterType != "all" || filterInputMethod != "all"
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
                        "Coba ubah kata kunci pencarian atau sesuaikan filter jenis transaksi Anda."
                    else
                        "Mulai rekam catatan pengeluaran atau pemasukan untuk memantau arus kas harian Anda.",
                    actionText = if (hasActiveFilter) "RESET FILTER" else "CATAT TRANSAKSI",
                    actionIcon = if (hasActiveFilter) Icons.Rounded.FilterAltOff else Icons.Rounded.Add,
                    onActionClick = {
                        if (hasActiveFilter) {
                            viewModel.searchQuery.value = ""
                            viewModel.filterType.value = "all"
                            viewModel.filterInputMethod.value = "all"
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
                contentPadding = PaddingValues(bottom = 90.dp, top = 6.dp)
            ) {
                groupedTransactions.forEach { (dateStr, txGroup) ->
                    // Editorial Date Header Row with count
                    item(key = "header_$dateStr") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = Formatters.formatDateGroup(dateStr).uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.2.sp,
                                    fontSize = 11.5.sp
                                ),
                                color = DarkSurface
                            )
                            Text(
                                text = "${txGroup.size} CATATAN",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp,
                                    fontSize = 10.sp
                                ),
                                color = TextSecondary
                            )
                        }
                    }

                    // Transactions for that date (Rendered as clean open rows with dividers)
                    items(txGroup, key = { it.id }) { tx ->
                        TransactionItemCard(
                            transaction = tx,
                            category = catMap[tx.categoryId],
                            sourceAsset = assetMap[tx.assetId],
                            destinationAsset = tx.destinationAssetId?.let { assetMap[it] },
                            onClick = { viewModel.selectedTransactionForDetail.value = tx }
                        )
                    }
                }
            }
        }
    }

    ExportDataDialog(
        isOpen = showExportDialog,
        viewModel = viewModel,
        hasActiveFilter = searchQuery.isNotBlank() || filterType != null || filterInputMethod != null,
        filterName = "Data Terfilter",
        onDismiss = { showExportDialog = false }
    )
}
