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
    onDismiss: () -> Unit
) {
    val transactions by viewModel.transactions.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val activeAssets by viewModel.activeAssets.collectAsState()

    val catMap = remember(categories) { categories.associateBy { it.id } }
    val assetMap = remember(activeAssets) { activeAssets.associateBy { it.id } }

    // Filter transactions involving this asset
    val assetTransactions = remember(transactions, asset) {
        transactions.filter { it.assetId == asset.id || it.destinationAssetId == asset.id }
    }

    // Calculate Inflow, Outflow, Transfer In, Transfer Out
    val inflow = remember(assetTransactions, asset) {
        assetTransactions.filter { it.type == TransactionType.INCOME.value && it.assetId == asset.id }
            .sumOf { it.amount }
    }

    val outflow = remember(assetTransactions, asset) {
        assetTransactions.filter { it.type == TransactionType.EXPENSE.value && it.assetId == asset.id }
            .sumOf { it.amount }
    }

    val transferIn = remember(assetTransactions, asset) {
        assetTransactions.filter { it.type == TransactionType.TRANSFER.value && it.destinationAssetId == asset.id }
            .sumOf { it.amount }
    }

    val transferOut = remember(assetTransactions, asset) {
        assetTransactions.filter { it.type == TransactionType.TRANSFER.value && it.assetId == asset.id }
            .sumOf { it.amount }
    }

    var showArchiveConfirmDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.20f)),
            colors = CardDefaults.cardColors(containerColor = PebbleSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = asset.name.uppercase(),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                ),
                                color = TextPrimary
                            )
                            if (asset.isDefault) {
                                Spacer(modifier = Modifier.width(6.dp))
                                AssetBadge(text = "UTAMA")
                            }
                        }
                        Text(
                            text = asset.type.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = TextSecondary
                        )
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
                                .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Rounded.Close, contentDescription = "Tutup", tint = TextPrimary, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Balance Card (Neo-Brutalist Frame)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.20f)),
                    colors = CardDefaults.cardColors(containerColor = PebbleSurfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "SALDO SAAT INI",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.2.sp
                            ),
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        AnimatedNumberText(
                            value = asset.currentBalance,
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-1).sp
                            ),
                            color = JadePrimary,
                            maxLines = 1,
                            softWrap = false
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = DarkBorder.copy(alpha = 0.15f), thickness = 1.dp)
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
                                    color = TextSecondary
                                )
                                Text(
                                    text = "+" + Formatters.formatRupiah(inflow),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = StatusPositive,
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
                                    color = TextSecondary
                                )
                                Text(
                                    text = "−" + Formatters.formatRupiah(outflow),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = StatusNegative,
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
                                    color = TextSecondary
                                )
                                Text(
                                    text = "+${Formatters.formatRupiah(transferIn, false)} / -${Formatters.formatRupiah(transferOut, false)}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = StatusTransfer,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = DarkBorder.copy(alpha = 0.15f), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        AssetSparkline(
                            transactions = assetTransactions,
                            currentBalance = asset.currentBalance
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons: Set Default & Archive
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!asset.isDefault) {
                        DuitAingButton(
                            text = "JADIKAN DEFAULT",
                            onClick = { viewModel.setDefaultAsset(asset.id) },
                            leadingIcon = Icons.Rounded.StarBorder,
                            modifier = Modifier.weight(1f),
                            variant = DuitAingButtonVariant.SECONDARY,
                            height = 42.dp,
                            shadowOffset = 2.dp
                        )
                    }

                    DuitAingButton(
                        text = "ARSIPKAN ASET",
                        onClick = { showArchiveConfirmDialog = true },
                        leadingIcon = Icons.Rounded.Archive,
                        modifier = Modifier.weight(1f),
                        variant = DuitAingButtonVariant.DANGER,
                        height = 42.dp,
                        shadowOffset = 2.dp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Transaction History for this asset
                Text(
                    text = "RIWAYAT TRANSAKSI (${assetTransactions.size})",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    ),
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (assetTransactions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada transaksi pada aset ini.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(assetTransactions, key = { it.id }) { tx ->
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
        }
    }

    // Archive Confirmation Dialog (Neo-Brutalist Frame)
    if (showArchiveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirmDialog = false },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(12.dp)),
            containerColor = PebbleSurface,
            title = {
                Text(
                    text = "ARSIPKAN ASET \"${asset.name.uppercase()}\"?",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    ),
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Aset ini tidak akan muncul lagi di daftar aktif, namun semua transaksi historis yang pernah dicatat tetap aman dan terjaga.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                DuitAingButton(
                    text = "ARSIPKAN",
                    onClick = {
                        viewModel.archiveAsset(asset.id) {
                            showArchiveConfirmDialog = false
                            onDismiss()
                        }
                    },
                    variant = DuitAingButtonVariant.DANGER,
                    height = 40.dp,
                    shadowOffset = 2.dp
                )
            },
            dismissButton = {
                DuitAingButton(
                    text = "BATAL",
                    onClick = { showArchiveConfirmDialog = false },
                    variant = DuitAingButtonVariant.OUTLINE,
                    height = 40.dp,
                    shadowOffset = 2.dp
                )
            }
        )
    }
}

/**
 * Section 23: Asset Sparkline Trend Component
 * Renders a crisp 14-day balance activity curve using actual historical transactions.
 */
@Composable
fun AssetSparkline(
    transactions: List<Transaction>,
    currentBalance: Long,
    modifier: Modifier = Modifier
) {
    val dailyBalances = remember(transactions, currentBalance) {
        val today = LocalDate.now()
        val days = 14
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

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TREN SALDO 14 HARI",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 9.sp,
                    letterSpacing = 0.8.sp
                ),
                color = TextSecondary
            )
            val firstVal = dailyBalances.firstOrNull() ?: currentBalance
            val isUp = currentBalance >= firstVal
            Text(
                text = if (isUp) "STABIL / NAIK" else "MENURUN",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp
                ),
                color = if (isUp) JadePrimary else StatusNegative
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        val chartJadePrimary = JadePrimary

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
                color = chartJadePrimary,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // Highlight current end point
            val lastY = height - (((dailyBalances.last() - minVal) / range) * (height - 8f) + 4f)
            drawCircle(
                color = chartJadePrimary,
                radius = 3.5.dp.toPx(),
                center = Offset(width, lastY)
            )
        }
    }
}
