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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
                // Quick CSV Export Button (Accessible >= 48dp touch target)
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PebbleSurface)
                        .border(1.dp, DarkBorder.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) {
                            viewModel.exportCsvFile(
                                context = context,
                                filteredOnly = true,
                                onSuccess = { file, sendIntent, content ->
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Cashflow Export CSV", content)
                                    clipboard.setPrimaryClip(clip)
                                    try {
                                        context.startActivity(Intent.createChooser(sendIntent, "Bagikan CSV Transaksi"))
                                    } catch (_: Exception) {}
                                    viewModel.showSnackbar("CSV berhasil dibuat: ${file.name}")
                                },
                                onError = {
                                    viewModel.showSnackbar("CSV gagal dibuat. Coba lagi.")
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = "Ekspor CSV",
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
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = PebbleSurface,
                unfocusedContainerColor = PebbleSurface,
                unfocusedBorderColor = DarkBorder.copy(alpha = 0.20f),
                focusedBorderColor = JadePrimary
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Filter Chips Row
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
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.filterType.value = key },
                    label = {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when (key) {
                            "expense" -> StatusNegative
                            "income" -> JadePrimary
                            "transfer" -> StatusTransfer
                            else -> DarkSurface
                        },
                        selectedLabelColor = PebbleSurface,
                        containerColor = PebbleSurface,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = if (isSelected) DarkBorder.copy(alpha = 0.4f) else PebbleBorder,
                        borderWidth = 1.dp,
                        enabled = true,
                        selected = isSelected
                    )
                )
            }

            // Input Method Filter (Suara vs Manual)
            val voiceFilterOptions = listOf(
                "all" to "SEMUA METODE",
                "voice" to "SUARA",
                "manual" to "MANUAL"
            )
            voiceFilterOptions.forEach { (key, label) ->
                val isSelected = filterInputMethod == key
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.filterInputMethod.value = key },
                    label = {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = JadePrimary,
                        selectedLabelColor = PebbleSurface,
                        containerColor = PebbleSurface,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = if (isSelected) DarkBorder.copy(alpha = 0.4f) else PebbleBorder,
                        borderWidth = 1.dp,
                        enabled = true,
                        selected = isSelected
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Transaction List grouped by date
        if (groupedTransactions.isEmpty()) {
            val hasActiveFilter = searchQuery.isNotBlank() || filterType != "all" || filterInputMethod != "all"
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                DoubleBezelCard(
                    modifier = Modifier.fillMaxWidth(),
                    outerPadding = 3.dp,
                    outerRadius = 16.dp,
                    innerRadius = 13.dp,
                    innerColor = PebbleSurface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(PebbleSurfaceVariant)
                                .border(1.dp, DarkBorder.copy(alpha = 0.20f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (hasActiveFilter) Icons.Rounded.SearchOff else Icons.AutoMirrored.Rounded.ReceiptLong,
                                contentDescription = null,
                                tint = DarkSurface,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = if (hasActiveFilter) "TIDAK ADA TRANSAKSI COCOK" else "BELUM ADA TRANSAKSI",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            ),
                            color = DarkSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (hasActiveFilter)
                                "Coba ubah kata kunci pencarian atau sesuaikan filter jenis transaksi Anda."
                            else
                                "Mulai rekam catatan pengeluaran atau pemasukan untuk memantau arus kas harian Anda.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        if (hasActiveFilter) {
                            DuitAingButton(
                                text = "RESET FILTER",
                                onClick = {
                                    viewModel.searchQuery.value = ""
                                    viewModel.filterType.value = "all"
                                    viewModel.filterInputMethod.value = "all"
                                },
                                leadingIcon = Icons.Rounded.FilterAltOff,
                                variant = DuitAingButtonVariant.SECONDARY,
                                height = 44.dp,
                                shadowOffset = 2.dp
                            )
                        } else {
                            DuitAingButton(
                                text = "CATAT TRANSAKSI",
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
                }
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
}
