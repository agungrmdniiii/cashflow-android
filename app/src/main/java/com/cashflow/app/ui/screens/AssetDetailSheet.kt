package com.cashflow.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cashflow.app.data.model.Asset
import com.cashflow.app.data.model.Transaction
import com.cashflow.app.data.model.TransactionType
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.components.*
import com.cashflow.app.ui.theme.*
import java.time.LocalDate

@Composable
fun AssetDetailSheet(
    viewModel: CashflowViewModel,
    asset: Asset,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    DuitAingModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        AssetDetailContent(
            viewModel = viewModel,
            asset = asset,
            onDismiss = onDismiss,
            modifier = modifier
        )
    }
}

@Composable
fun AssetDetailContent(
    viewModel: CashflowViewModel,
    asset: Asset,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val transactions by viewModel.transactions.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val activeAssets by viewModel.activeAssets.collectAsState()

    val catMap = remember(categories) { categories.associateBy { it.id } }
    val assetMap = remember(activeAssets) { activeAssets.associateBy { it.id } }
    val currentAsset = activeAssets.find { it.id == asset.id } ?: asset

    // Filter transactions involving this asset
    val assetTransactions = remember(transactions, currentAsset) {
        transactions.filter { it.assetId == currentAsset.id || it.destinationAssetId == currentAsset.id }
    }

    // Calculate Inflow, Outflow, Transfer In, Transfer Out
    val inflow = remember(assetTransactions, currentAsset) {
        assetTransactions.filter { it.type == TransactionType.INCOME.value && it.assetId == currentAsset.id }
            .sumOf { it.amount }
    }

    val outflow = remember(assetTransactions, currentAsset) {
        assetTransactions.filter { it.type == TransactionType.EXPENSE.value && it.assetId == currentAsset.id }
            .sumOf { it.amount }
    }

    val transferIn = remember(assetTransactions, currentAsset) {
        assetTransactions.filter { it.type == TransactionType.TRANSFER.value && it.destinationAssetId == currentAsset.id }
            .sumOf { it.amount }
    }

    val transferOut = remember(assetTransactions, currentAsset) {
        assetTransactions.filter { it.type == TransactionType.TRANSFER.value && it.assetId == currentAsset.id }
            .sumOf { it.amount }
    }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showBalanceCheck by remember { mutableStateOf(false) }
    val duit = MaterialTheme.duit

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .navigationBarsPadding()
    ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        AssetIcon(
                            assetType = asset.type,
                            size = 44.dp,
                            iconSize = 22.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = asset.name,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp
                                    ),
                                    color = duit.textPrimary
                                )
                                if (asset.isDefault) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    AssetBadge(text = "Utama")
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = asset.type.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = duit.textSecondary
                            )
                        }
                    }

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
                                .border(1.dp, duit.border, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Rounded.Close, contentDescription = "Tutup", tint = duit.textPrimary, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Balance Card (Neo-Brutalist Frame)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, duit.border),
                    colors = CardDefaults.cardColors(containerColor = duit.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "SALDO SAAT INI",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.2.sp
                            ),
                            color = duit.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        AnimatedNumberText(
                            value = currentAsset.currentBalance,
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-1).sp
                            ),
                            color = duit.textPrimary,
                            maxLines = 1,
                            softWrap = false
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = duit.border, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "PEMASUKAN",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.8.sp
                                    ),
                                    color = duit.textSecondary
                                )
                                Text(
                                    text = "+" + Formatters.displayRupiah(inflow),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = duit.income,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Column {
                                Text(
                                    text = "PENGELUARAN",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.8.sp
                                    ),
                                    color = duit.textSecondary
                                )
                                Text(
                                    text = "−" + Formatters.displayRupiah(outflow),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = duit.expense,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "TRANSFER IN / OUT",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.8.sp
                                    ),
                                    color = duit.textSecondary
                                )
                                Text(
                                    text = "+${Formatters.displayRupiah(transferIn, false)} / -${Formatters.displayRupiah(transferOut, false)}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = duit.transfer,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = duit.border, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        AssetSparkline(
                            transactions = assetTransactions,
                            currentBalance = currentAsset.currentBalance
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons: Edit, Delete & Default
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        DuitAingButton(
                            text = "EDIT ASET",
                            onClick = {
                                if (onEdit != null) {
                                    onEdit()
                                } else {
                                    viewModel.editingAsset.value = asset
                                    viewModel.isAddAssetOpen.value = true
                                    onDismiss()
                                }
                            },
                            leadingIcon = Icons.Rounded.Edit,
                            modifier = Modifier.weight(1f),
                            variant = DuitAingButtonVariant.SECONDARY,
                            height = 42.dp,
                            shadowOffset = 2.dp
                        )

                        DuitAingButton(
                            text = "HAPUS ASET",
                            onClick = { showDeleteConfirmDialog = true },
                            leadingIcon = Icons.Rounded.Delete,
                            modifier = Modifier.weight(1f),
                            variant = DuitAingButtonVariant.DANGER,
                            height = 42.dp,
                            shadowOffset = 2.dp
                        )
                    }

                    DuitAingButton(
                        text = "CEK SALDO",
                        onClick = { showBalanceCheck = true },
                        leadingIcon = Icons.Rounded.FactCheck,
                        modifier = Modifier.fillMaxWidth(),
                        variant = DuitAingButtonVariant.SECONDARY
                    )
                    if (!asset.isDefault) {
                        DuitAingButton(
                            text = "JADIKAN ASET UTAMA (DEFAULT)",
                            onClick = { viewModel.setDefaultAsset(asset.id) },
                            leadingIcon = Icons.Rounded.StarBorder,
                            modifier = Modifier.fillMaxWidth(),
                            variant = DuitAingButtonVariant.OUTLINE,
                            height = 40.dp,
                            shadowOffset = 2.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Transaction History for this asset
                Text(
                    text = "RIWAYAT TRANSAKSI (${assetTransactions.size})",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    ),
                    color = duit.textPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (assetTransactions.isEmpty()) {
                    AnimatedEmptyState(
                        icon = Icons.Rounded.ReceiptLong,
                        title = "BELUM ADA TRANSAKSI",
                        description = "Belum ada transaksi tercatat pada rekening / aset ini.",
                        lottieRawRes = com.cashflow.app.R.raw.lottie_no_transactions,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        items(assetTransactions, key = { it.id }, contentType = { "transaction" }) { tx ->
                            TransactionItemCard(
                                transaction = tx,
                                category = catMap[tx.categoryId],
                                sourceAsset = assetMap[tx.assetId],
                                destinationAsset = tx.destinationAssetId?.let { assetMap[it] },
                                onClick = {
                                    viewModel.selectedTransactionForDetail.value = tx
                                }
                            )
                        }
                    }
                }
            }

    if (showBalanceCheck) {
        BalanceCheckDialog(currentAsset, onDismiss = { showBalanceCheck = false },
            onAdjust = { target, note, done -> viewModel.adjustAssetBalance(currentAsset.id, target, note, done) })
    }
    // Delete Confirmation Dialog (Standardized Neo-Brutalist Frame)
    DuitAingConfirmDialog(
        isOpen = showDeleteConfirmDialog,
        title = "HAPUS ASET \"${asset.name.uppercase()}\"?",
        message = "Apakah Anda yakin ingin menghapus aset ini? Jika memiliki riwayat transaksi, aset akan diarsipkan dengan aman agar catatan historis tetap utuh.",
        confirmText = "HAPUS ASET",
        confirmVariant = DuitAingButtonVariant.DANGER,
        onConfirm = {
            viewModel.deleteAsset(asset.id, confirmed = true) {
                showDeleteConfirmDialog = false
                onDismiss()
            }
        },
        onDismiss = { showDeleteConfirmDialog = false }
    )
}

/**
 * Section 23: Asset Sparkline Trend Component
 * Renders a crisp 30-day balance activity curve using actual historical transactions.
 */
@Composable
fun AssetSparkline(
    transactions: List<Transaction>,
    currentBalance: Long,
    modifier: Modifier = Modifier
) {
    val dailyBalances = remember(transactions, currentBalance) {
        val today = LocalDate.now()
        val days = 30
        val deltaByDay = mutableMapOf<LocalDate, Long>()

        for (tx in transactions) {
            val d = try { LocalDate.parse(tx.transactionDate) } catch (e: Exception) { null } ?: continue
            val delta = when (tx.type) {
                TransactionType.INCOME.value -> tx.amount
                TransactionType.EXPENSE.value -> -tx.amount
                else -> 0L
            }
            deltaByDay[d] = (deltaByDay[d] ?: 0L) + delta
        }

        var running = currentBalance
        val reversePoints = mutableListOf<Long>()
        reversePoints.add(running)
        for (i in 1 until days) {
            val date = today.minusDays(i.toLong())
            val deltaOnDate = deltaByDay[date] ?: 0L
            running -= deltaOnDate
            reversePoints.add(running)
        }
        reversePoints.reversed()
    }

    val duit = MaterialTheme.duit

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TREN SALDO 30 HARI",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    letterSpacing = 0.8.sp
                ),
                color = duit.textSecondary
            )
            val firstVal = dailyBalances.firstOrNull() ?: currentBalance
            val isUp = currentBalance >= firstVal
            Text(
                text = if (isUp) "STABIL / NAIK" else "MENURUN",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ),
                color = if (isUp) duit.income else duit.expense
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        val chartColor = duit.primary

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
        ) {
            if (dailyBalances.size < 2) return@Canvas
            val minVal = dailyBalances.minOrNull()!!.toFloat()
            val maxVal = dailyBalances.maxOrNull()!!.toFloat()
            val range = if (maxVal == minVal) 1f else (maxVal - minVal)

            val width = size.width
            val height = size.height
            val stepX = width / (dailyBalances.size - 1)

            val path = Path()
            dailyBalances.forEachIndexed { index, b ->
                val x = index * stepX
                val normalizedY = (b - minVal) / range
                val y = height - (normalizedY * (height - 8f) + 4f)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(
                path = path,
                color = chartColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // Highlight current end point
            val lastY = height - (((dailyBalances.last() - minVal) / range) * (height - 8f) + 4f)
            drawCircle(
                color = chartColor,
                radius = 3.5.dp.toPx(),
                center = Offset(width, lastY)
            )
        }
    }
}
