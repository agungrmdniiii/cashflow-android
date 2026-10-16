package com.cashflow.app.ui.components.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.data.model.Asset
import com.cashflow.app.data.model.Category
import com.cashflow.app.data.model.Transaction
import com.cashflow.app.ui.components.TransactionItemCard
import com.cashflow.app.ui.theme.duit

/**
 * Komponen Transaksi Terbaru untuk Beranda (Tahap 2):
 * - Header dengan "Lihat semua" (target sentuh >= 48dp)
 * - 5 item transaksi dengan ikon kategori bulat & nominal rata kanan
 * - Empty state ringkas (ikon kecil + 1 kalimat + 1 tombol)
 * - Jarak antar item 12dp
 */
@Composable
fun RecentTransactions(
    transactions: List<Transaction>,
    categoryMap: Map<String, Category>,
    assetMap: Map<String, Asset>,
    isBalanceHidden: Boolean,
    onViewAllClick: () -> Unit,
    onTransactionClick: (Transaction) -> Unit,
    onEditTransaction: (Transaction) -> Unit,
    onDeleteTransaction: (Transaction) -> Unit,
    onSaveAsFavorite: (Transaction) -> Unit,
    onAddTransactionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val duit = MaterialTheme.duit
    val displayedTransactions = transactions.take(5)

    Column(modifier = modifier.fillMaxWidth()) {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Transaksi Terbaru",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                color = duit.textPrimary
            )

            // "Lihat semua" button (target sentuh >= 48dp)
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onViewAllClick)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Lihat semua",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp
                        ),
                        color = duit.textSecondary
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = duit.textSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (displayedTransactions.isEmpty()) {
            // Empty State Ringkas (Ikon Kecil + 1 Kalimat + 1 Tombol)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = duit.surface),
                border = BorderStroke(1.dp, duit.border)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(duit.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
                            contentDescription = null,
                            tint = duit.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Belum ada transaksi dicatat bulan ini.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp
                        ),
                        color = duit.textSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onAddTransactionClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .defaultMinSize(minHeight = 44.dp),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = duit.primary,
                            contentColor = duit.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = duit.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Catat Transaksi",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = duit.surface),
                border = BorderStroke(1.dp, duit.border)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    displayedTransactions.forEachIndexed { index, tx ->
                        TransactionItemCard(
                            transaction = tx,
                            category = categoryMap[tx.categoryId],
                            sourceAsset = assetMap[tx.assetId],
                            destinationAsset = tx.destinationAssetId?.let { assetMap[it] },
                            isBalanceHidden = isBalanceHidden,
                            onClick = { onTransactionClick(tx) },
                            onEdit = { onEditTransaction(tx) },
                            onDelete = { onDeleteTransaction(tx) },
                            onSaveAsFavorite = { onSaveAsFavorite(tx) },
                            showDivider = index < displayedTransactions.size - 1
                        )
                    }
                }
            }
        }
    }
}
