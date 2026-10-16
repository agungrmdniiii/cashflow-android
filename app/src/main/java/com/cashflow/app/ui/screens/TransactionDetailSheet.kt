package com.cashflow.app.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

    val duit = MaterialTheme.duit

    val typeColor = when (transaction.type) {
        TransactionType.INCOME.value -> duit.income
        TransactionType.EXPENSE.value -> duit.expense
        else -> duit.transfer
    }
    val typeContainerColor = when (transaction.type) {
        TransactionType.INCOME.value -> duit.incomeContainer
        TransactionType.EXPENSE.value -> duit.expenseContainer
        else -> duit.transferContainer
    }
    val typeLabel = when (transaction.type) {
        TransactionType.INCOME.value -> "PEMASUKAN"
        TransactionType.EXPENSE.value -> "PENGELUARAN"
        TransactionType.TRANSFER.value -> "TRANSFER ANTAR ASET"
        TransactionType.ALLOCATION.value -> "ALOKASI TABUNGAN"
        TransactionType.RELEASE.value -> "PELEPASAN ALOKASI"
        else -> TransactionType.fromValue(transaction.type).displayName.uppercase()
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
                color = duit.textPrimary
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
                        .background(duit.surfaceVariant)
                        .border(1.dp, duit.border, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Tutup",
                        tint = duit.textPrimary,
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
            border = BorderStroke(1.dp, duit.border),
            colors = CardDefaults.cardColors(containerColor = duit.surfaceVariant)
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
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
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
                            color = duit.textPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(typeColor.copy(alpha = 0.12f))
                            .border(1.dp, typeColor.copy(alpha = 0.30f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = typeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                letterSpacing = 0.6.sp
                            ),
                            color = typeColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = prefix + Formatters.displayRupiah(transaction.amount),
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
                    color = duit.textPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. SPECIFICATION CARD (Aset, Tanggal, Waktu, Metode Input, Catatan)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, duit.border),
            colors = CardDefaults.cardColors(containerColor = duit.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                DetailRowItem(
                    icon = Icons.Rounded.AccountBalanceWallet,
                    label = if (transaction.type == TransactionType.TRANSFER.value) "ASET SUMBER" else "ASET PEMBAYARAN",
                    value = sourceAsset?.name ?: "Aset (${transaction.assetId})"
                )

                if (transaction.type == TransactionType.TRANSFER.value) {
                    HorizontalDivider(color = duit.border, thickness = 1.dp)
                    DetailRowItem(
                        icon = Icons.AutoMirrored.Rounded.ArrowForward,
                        label = "ASET TUJUAN",
                        value = destAsset?.name ?: "Aset Tujuan (${transaction.destinationAssetId})"
                    )
                }

                HorizontalDivider(color = duit.border, thickness = 1.dp)

                DetailRowItem(
                    icon = Icons.Rounded.Schedule,
                    label = "WAKTU & TANGGAL",
                    value = "${Formatters.formatDateReadable(transaction.transactionDate)}, ${transaction.transactionTime}"
                )

                HorizontalDivider(color = duit.border, thickness = 1.dp)

                DetailRowItem(
                    icon = when (transaction.inputMethod) {
                        "voice" -> Icons.Rounded.Mic
                        "ocr" -> Icons.Rounded.DocumentScanner
                        else -> Icons.Rounded.EditNote
                    },
                    label = "METODE INPUT",
                    value = when (transaction.inputMethod) {
                        "voice" -> "Input Suara (Voice AI)"
                        "ocr" -> "Pemindai Struk (ML Kit OCR)"
                        else -> "Input Manual"
                    }
                )

                if (transaction.tags.isNotBlank()) {
                    HorizontalDivider(color = duit.border, thickness = 1.dp)
                    DetailRowItem(
                        icon = Icons.Rounded.Label,
                        label = "TAG / LABEL",
                        value = transaction.tags
                    )
                }

                if (transaction.note.isNotBlank()) {
                    HorizontalDivider(color = duit.border, thickness = 1.dp)
                    DetailRowItem(
                        icon = Icons.Rounded.Description,
                        label = "CATATAN",
                        value = transaction.note
                    )
                }

                if (!transaction.attachment.isNullOrBlank()) {
                    val bitmap = remember(transaction.attachment) {
                        try {
                            BitmapFactory.decodeFile(transaction.attachment)
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (bitmap != null) {
                        HorizontalDivider(color = duit.border, thickness = 1.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(duit.background)
                                        .border(1.dp, duit.border, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Receipt,
                                        contentDescription = null,
                                        tint = duit.textPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = "FOTO STRUK",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = duit.textSecondary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, duit.border, RoundedCornerShape(8.dp))
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Foto Struk",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 4. ACTION BUTTONS: Hapus, Duplikat & Edit
        DuitAingButton(
            text = "SIMPAN SEBAGAI FAVORIT",
            onClick = {
                val template = com.cashflow.app.data.model.FavoriteTemplate(
                    id = java.util.UUID.randomUUID().toString(),
                    name = transaction.description,
                    type = transaction.type,
                    amount = transaction.amount,
                    categoryId = transaction.categoryId,
                    assetId = transaction.assetId,
                    destinationAssetId = transaction.destinationAssetId,
                    note = transaction.note
                )
                viewModel.saveFavoriteTemplate(template)
            },
            leadingIcon = Icons.Rounded.Star,
            modifier = Modifier.fillMaxWidth(),
            variant = DuitAingButtonVariant.OUTLINE,
            height = 44.dp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DuitAingButton(
                text = "HAPUS",
                onClick = { showDeleteConfirmDialog = true },
                leadingIcon = Icons.Rounded.DeleteOutline,
                modifier = Modifier.weight(1f),
                variant = DuitAingButtonVariant.DANGER,
                height = 46.dp,
                shadowOffset = 0.dp
            )

            DuitAingButton(
                text = "SALIN",
                onClick = {
                    viewModel.editingTransaction.value = transaction.copy(
                        id = java.util.UUID.randomUUID().toString(),
                        transactionDate = java.time.LocalDate.now().toString(),
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    viewModel.isAddTransactionOpen.value = true
                    onDismiss()
                },
                leadingIcon = Icons.Rounded.ContentCopy,
                modifier = Modifier.weight(1f),
                variant = DuitAingButtonVariant.SECONDARY,
                height = 46.dp,
                shadowOffset = 0.dp
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
                modifier = Modifier.weight(1.1f),
                variant = DuitAingButtonVariant.PRIMARY,
                height = 46.dp,
                shadowOffset = 0.dp
            )
        }
    }

    // Delete Confirmation Dialog (Standardized Neo-Brutalist Frame)
    val amountStr = Formatters.displayRupiah(transaction.amount)
    val deleteConfirmMessage = remember(transaction, sourceAsset, destAsset, amountStr) {
        when (transaction.type) {
            TransactionType.TRANSFER.value ->
                "Hapus transfer $amountStr dari '${sourceAsset?.name ?: "Aset Asal"}' ke '${destAsset?.name ?: "Aset Tujuan"}'? Saldo kedua aset akan dikembalikan otomatis."
            TransactionType.EXPENSE.value ->
                "Hapus pengeluaran $amountStr dari '${sourceAsset?.name ?: "Aset"}'? Saldo aset akan dikembalikan otomatis."
            TransactionType.INCOME.value ->
                "Hapus pemasukan $amountStr ke '${sourceAsset?.name ?: "Aset"}'? Saldo aset akan dikurangi otomatis."
            else ->
                "Hapus transaksi $amountStr dari '${sourceAsset?.name ?: "Aset"}'? Saldo aset akan disesuaikan otomatis."
        }
    }

    DuitAingConfirmDialog(
        isOpen = showDeleteConfirmDialog,
        title = "HAPUS TRANSAKSI INI?",
        message = deleteConfirmMessage,
        confirmText = "HAPUS TRANSAKSI",
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
    val duit = MaterialTheme.duit
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Row(
            modifier = Modifier.widthIn(min = 100.dp, max = 140.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = duit.textTertiary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                    fontSize = 11.sp
                ),
                color = duit.textSecondary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            ),
            color = duit.textPrimary,
            textAlign = TextAlign.End
        )
    }
}
