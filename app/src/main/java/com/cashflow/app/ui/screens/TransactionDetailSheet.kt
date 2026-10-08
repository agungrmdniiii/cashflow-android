package com.cashflow.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import com.cashflow.app.data.model.Transaction
import com.cashflow.app.data.model.TransactionType
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.components.CategoryIcon
import com.cashflow.app.ui.components.DuitAingButton
import com.cashflow.app.ui.components.DuitAingButtonVariant
import com.cashflow.app.ui.components.DuitAingConfirmDialog
import com.cashflow.app.ui.components.Formatters
import com.cashflow.app.ui.theme.*

@Composable
fun TransactionDetailSheet(
    viewModel: CashflowViewModel,
    transaction: Transaction,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        TransactionDetailContent(
            viewModel = viewModel,
            transaction = transaction,
            onDismiss = onDismiss
        )
    }
}

@Composable
fun TransactionDetailContent(
    viewModel: CashflowViewModel,
    transaction: Transaction,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null
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

    Card(
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .wrapContentHeight()
            .padding(vertical = 16.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.20f)),
            colors = CardDefaults.cardColors(containerColor = PebbleSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "DETAIL TRANSAKSI",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            ),
                            color = TextPrimary
                        )
                        Text(
                            text = "INFORMASI CATATAN KEUANGAN",
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

                Spacer(modifier = Modifier.height(18.dp))

                // Type Icon & Banner
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (transaction.type == TransactionType.TRANSFER.value) {
                        CategoryIcon(
                            iconName = "swap_horiz",
                            size = 46.dp,
                            iconSize = 22.dp,
                            backgroundColor = StatusTransferContainer,
                            tintColor = StatusTransfer
                        )
                    } else {
                        CategoryIcon(
                            iconName = cat?.icon ?: "category",
                            size = 46.dp,
                            iconSize = 22.dp,
                            backgroundColor = if (transaction.type == TransactionType.INCOME.value) StatusPositiveContainer else JadePrimaryContainer,
                            tintColor = if (transaction.type == TransactionType.INCOME.value) StatusPositive else JadePrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        val typeLabel = when (transaction.type) {
                            TransactionType.INCOME.value -> "PEMASUKAN"
                            TransactionType.EXPENSE.value -> "PENGELUARAN"
                            TransactionType.TRANSFER.value -> "TRANSFER ANTAR ASET"
                            else -> transaction.type.uppercase()
                        }
                        Text(
                            text = typeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            ),
                            color = when (transaction.type) {
                                TransactionType.INCOME.value -> StatusPositive
                                TransactionType.EXPENSE.value -> StatusNegative
                                else -> StatusTransfer
                            }
                        )
                        Text(
                            text = transaction.description,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Nominal Highlight
                val prefix = when (transaction.type) {
                    TransactionType.INCOME.value -> "+"
                    TransactionType.EXPENSE.value -> "−"
                    else -> ""
                }
                Text(
                    text = prefix + Formatters.formatRupiah(transaction.amount),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1).sp
                    ),
                    color = when (transaction.type) {
                        TransactionType.INCOME.value -> StatusPositive
                        TransactionType.EXPENSE.value -> StatusNegative
                        else -> StatusTransfer
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = DarkBorder.copy(alpha = 0.15f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))

                // Detailed Rows
                if (transaction.type != TransactionType.TRANSFER.value) {
                    DetailRow(label = "KATEGORI", value = cat?.name ?: "Lainnya")
                }

                DetailRow(
                    label = if (transaction.type == TransactionType.TRANSFER.value) "ASET SUMBER" else "ASET",
                    value = sourceAsset?.name ?: "Aset (${transaction.assetId})"
                )

                if (transaction.type == TransactionType.TRANSFER.value) {
                    DetailRow(
                        label = "ASET TUJUAN",
                        value = destAsset?.name ?: "Aset Tujuan (${transaction.destinationAssetId})"
                    )
                }

                DetailRow(
                    label = "WAKTU & TANGGAL",
                    value = "${Formatters.formatDateReadable(transaction.transactionDate)}, ${transaction.transactionTime}"
                )

                DetailRow(
                    label = "METODE INPUT",
                    value = if (transaction.inputMethod == "voice") "Suara (Voice)" else "Manual"
                )

                if (transaction.note.isNotBlank()) {
                    DetailRow(label = "CATATAN", value = transaction.note)
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions: Edit and Delete
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
        }

    // Delete Confirmation Dialog (Standardized Neo-Brutalist Frame)
    DuitAingConfirmDialog(
        isOpen = showDeleteConfirmDialog,
        title = "HAPUS TRANSAKSI INI?",
        message = "Transaksi yang dihapus akan memengaruhi saldo aset dan laporan keuangan Anda.",
        confirmText = "HAPUS",
        confirmVariant = DuitAingButtonVariant.DANGER,
        onConfirm = {
            viewModel.deleteTransaction(transaction.id) {
                showDeleteConfirmDialog = false
                onDismiss()
            }
        },
        onDismiss = { showDeleteConfirmDialog = false }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            ),
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
        )
    }
}
