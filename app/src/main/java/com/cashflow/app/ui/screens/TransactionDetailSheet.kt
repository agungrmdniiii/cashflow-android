package com.cashflow.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.data.model.Transaction
import com.cashflow.app.data.model.TransactionType
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.components.*
import com.cashflow.app.ui.theme.*

@Composable
fun TransactionDetailSheet(
    viewModel: CashflowViewModel,
    transaction: Transaction,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    DuitAingModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        TransactionDetailContent(
            viewModel = viewModel,
            transaction = transaction,
            onDismiss = onDismiss,
            modifier = modifier
        )
    }
}

@Composable
fun TransactionDetailContent(
    viewModel: CashflowViewModel,
    transaction: Transaction,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val categories by viewModel.categories.collectAsState()
    val assets by viewModel.activeAssets.collectAsState()

    val cat = remember(transaction, categories) {
        categories.firstOrNull { it.id == transaction.categoryId }
    }
    val sourceAsset = remember(transaction, assets) {
        assets.firstOrNull { it.id == transaction.assetId }
    }
    val destAsset = remember(transaction, assets) {
        assets.firstOrNull { it.id == transaction.destinationAssetId }
    }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val typeColor = when (transaction.type) {
        TransactionType.INCOME.value -> StatusPositive
        TransactionType.EXPENSE.value -> StatusNegative
        else -> StatusTransfer
    }
    val typeContainerColor = when (transaction.type) {
        TransactionType.INCOME.value -> StatusPositiveContainer
        TransactionType.EXPENSE.value -> StatusNegativeContainer
        else -> StatusTransferContainer
    }
    val typeLabel = when (transaction.type) {
        TransactionType.INCOME.value -> "PEMASUKAN"
        TransactionType.EXPENSE.value -> "PENGELUARAN"
        TransactionType.TRANSFER.value -> "TRANSFER ANTAR ASET"
        else -> transaction.type.uppercase()
    }
    val prefix = when (transaction.type) {
        TransactionType.INCOME.value -> "+"
        TransactionType.EXPENSE.value -> "−"
        else -> ""
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .navigationBarsPadding()
    ) {
        // 1. Header (Standardized across all bottom sheets)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DETAIL TRANSAKSI",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                    fontSize = 18.sp
                ),
                color = DarkSurface
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
                        .background(PebbleSurfaceVariant)
                        .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Tutup",
                        tint = DarkSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. HERO CARD (Nominal, Deskripsi, Kategori & Jenis)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.20f)),
            colors = CardDefaults.cardColors(containerColor = PebbleSurfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (transaction.type == TransactionType.TRANSFER.value) {
                            CategoryIcon(
                                iconName = "swap_horiz",
                                size = 40.dp,
                                iconSize = 20.dp,
                                backgroundColor = typeContainerColor,
                                tintColor = typeColor
                            )
                        } else {
                            CategoryIcon(
                                iconName = cat?.icon ?: "category",
                                size = 40.dp,
                                iconSize = 20.dp,
                                backgroundColor = typeContainerColor,
                                tintColor = typeColor
                            )
                        }

                        Text(
                            text = if (transaction.type == TransactionType.TRANSFER.value) "Transfer Antar Aset" else (cat?.name ?: "Lainnya"),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            ),
                            color = TextPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(typeColor.copy(alpha = 0.12f))
                            .border(1.dp, typeColor.copy(alpha = 0.30f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = typeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 9.5.sp,
                                letterSpacing = 0.6.sp
                            ),
                            color = typeColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = prefix + Formatters.formatRupiah(transaction.amount),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1).sp,
                        fontSize = 32.sp
                    ),
                    color = typeColor
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = transaction.description.ifBlank { "(Tanpa deskripsi)" },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = DarkSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. SPECIFICATION CARD (Aset, Tanggal, Waktu, Metode Input, Catatan)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.20f)),
            colors = CardDefaults.cardColors(containerColor = PebbleSurfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DetailRowItem(
                    icon = Icons.Rounded.AccountBalanceWallet,
                    label = if (transaction.type == TransactionType.TRANSFER.value) "ASET SUMBER" else "ASET PEMBAYARAN",
                    value = sourceAsset?.name ?: "Aset (${transaction.assetId})"
                )

                if (transaction.type == TransactionType.TRANSFER.value) {
                    HorizontalDivider(color = DarkBorder.copy(alpha = 0.10f), thickness = 1.dp)
                    DetailRowItem(
                        icon = Icons.AutoMirrored.Rounded.ArrowForward,
                        label = "ASET TUJUAN",
                        value = destAsset?.name ?: "Aset Tujuan (${transaction.destinationAssetId})"
                    )
                }

                HorizontalDivider(color = DarkBorder.copy(alpha = 0.10f), thickness = 1.dp)

                DetailRowItem(
                    icon = Icons.Rounded.Schedule,
                    label = "WAKTU & TANGGAL",
                    value = "${Formatters.formatDateReadable(transaction.transactionDate)}, ${transaction.transactionTime}"
                )

                HorizontalDivider(color = DarkBorder.copy(alpha = 0.10f), thickness = 1.dp)

                DetailRowItem(
                    icon = if (transaction.inputMethod == "voice") Icons.Rounded.Mic else Icons.Rounded.EditNote,
                    label = "METODE INPUT",
                    value = if (transaction.inputMethod == "voice") "Input Suara (Voice AI)" else "Input Manual"
                )

                if (transaction.note.isNotBlank()) {
                    HorizontalDivider(color = DarkBorder.copy(alpha = 0.10f), thickness = 1.dp)
                    DetailRowItem(
                        icon = Icons.Rounded.Description,
                        label = "CATATAN",
                        value = transaction.note
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 4. ACTION BUTTONS: Hapus & Edit
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DuitAingButton(
                text = "HAPUS",
                onClick = { showDeleteConfirmDialog = true },
                leadingIcon = Icons.Rounded.DeleteOutline,
                modifier = Modifier.weight(1f),
                variant = DuitAingButtonVariant.DANGER,
                height = 46.dp,
                shadowOffset = 2.dp
            )

            DuitAingButton(
                text = "EDIT",
                onClick = {
                    if (onEdit != null) {
                        onEdit()
                    } else {
                        viewModel.editingTransaction.value = transaction
                        viewModel.isAddTransactionOpen.value = true
                        onDismiss()
                    }
                },
                leadingIcon = Icons.Rounded.Edit,
                trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                modifier = Modifier.weight(1.3f),
                variant = DuitAingButtonVariant.PRIMARY,
                height = 46.dp,
                shadowOffset = 2.dp
            )
        }
    }

    // Delete Confirmation Dialog (Standardized Neo-Brutalist Frame)
    DuitAingConfirmDialog(
        isOpen = showDeleteConfirmDialog,
        title = "HAPUS TRANSAKSI INI?",
        message = "Transaksi yang dihapus akan memengaruhi saldo aset dan laporan keuangan Anda.",
        confirmText = "HAPUS",
        confirmVariant = DuitAingButtonVariant.DANGER,
        onConfirm = {
            viewModel.deleteTransactionWithUndo(transaction) {
                showDeleteConfirmDialog = false
                onDismiss()
            }
        },
        onDismiss = { showDeleteConfirmDialog = false }
    )
}

@Composable
private fun DetailRowItem(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                    fontSize = 10.5.sp
                ),
                color = TextSecondary
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = DarkSurface
        )
    }
}
