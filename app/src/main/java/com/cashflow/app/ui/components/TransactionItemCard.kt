package com.cashflow.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.data.model.Asset
import com.cashflow.app.data.model.Category
import com.cashflow.app.data.model.Transaction
import com.cashflow.app.data.model.TransactionType
import com.cashflow.app.ui.theme.*

/**
 * Editorial Transaction Row for "Duit Aing"
 * Supports:
 * - Swipe right to edit, swipe left to delete (with haptics)
 * - 3-dots accessibility menu for non-gesture control (Detail, Edit, Favorit, Hapus)
 * - Global balance hiding (Rp ••••••)
 * - Press micro-scale (0.97f)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionItemCard(
    transaction: Transaction,
    category: Category?,
    sourceAsset: Asset?,
    destinationAsset: Asset?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onSaveAsFavorite: (() -> Unit)? = null,
    isBalanceHidden: Boolean = false,
    showDivider: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f),
        label = "TxItemPressScale"
    )

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    // Swipe right -> Edit
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEdit?.invoke()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    // Swipe left -> Delete
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete?.invoke()
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.fillMaxWidth(),
        enableDismissFromStartToEnd = onEdit != null,
        enableDismissFromEndToStart = onDelete != null,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color by animateColorAsState(
                targetValue = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.StartToEnd -> JadePrimary
                    SwipeToDismissBoxValue.EndToStart -> StatusNegative
                    SwipeToDismissBoxValue.Settled -> Color.Transparent
                },
                label = "SwipeDismissColor"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Edit",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                } else if (direction == SwipeToDismissBoxValue.EndToStart) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Hapus",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Hapus",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category or Transfer Icon
                if (transaction.type == TransactionType.TRANSFER.value) {
                    CategoryIcon(
                        iconName = "swap_horiz",
                        size = 44.dp,
                        iconSize = 22.dp,
                        backgroundColor = Color(0xFF2D5D48),
                        tintColor = Color.White
                    )
                } else {
                    CategoryIcon(
                        iconName = category?.icon ?: "category",
                        size = 44.dp,
                        iconSize = 22.dp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Details: Description & Subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = transaction.description,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (transaction.inputMethod == "voice") {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(JadePrimaryLight)
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Mic,
                                        contentDescription = "Suara",
                                        tint = JadePrimary,
                                        modifier = Modifier.size(9.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "SUARA",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 0.4.sp
                                        ),
                                        color = JadePrimary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    val subtitleText = when (transaction.type) {
                        TransactionType.TRANSFER.value -> {
                            val src = sourceAsset?.name ?: "Aset"
                            val dst = destinationAsset?.name ?: "Tujuan"
                            "$src ke $dst"
                        }
                        else -> {
                            val cat = category?.name ?: "Umum"
                            val ast = sourceAsset?.name ?: "Aset"
                            "$cat · $ast"
                        }
                    }
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right column: Amount & Time
                val prefix = when (transaction.type) {
                    TransactionType.INCOME.value -> "+"
                    TransactionType.EXPENSE.value -> "−"
                    else -> ""
                }
                val amountColor = when (transaction.type) {
                    TransactionType.INCOME.value -> StatusPositive
                    TransactionType.EXPENSE.value -> MaterialTheme.colorScheme.onSurface
                    else -> StatusTransfer
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (isBalanceHidden) "Rp ••••••" else prefix + Formatters.formatRupiah(transaction.amount),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        color = if (isBalanceHidden) MaterialTheme.colorScheme.onSurfaceVariant else amountColor,
                        softWrap = false,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = transaction.transactionTime.ifBlank { "12:00" },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                // 3-dots accessibility menu
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Opsi transaksi",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        shape = RoundedCornerShape(12.dp),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        DropdownMenuItem(
                            text = { Text("Lihat Detail") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Visibility, contentDescription = null, tint = JadePrimary)
                            },
                            onClick = {
                                showMenu = false
                                onClick()
                            }
                        )

                        if (onEdit != null) {
                            DropdownMenuItem(
                                text = { Text("Edit Transaksi") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Edit, contentDescription = null, tint = JadePrimary)
                                },
                                onClick = {
                                    showMenu = false
                                    onEdit()
                                }
                            )
                        }

                        if (onSaveAsFavorite != null) {
                            DropdownMenuItem(
                                text = { Text("Jadikan Favorit") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Star, contentDescription = null, tint = Color(0xFFE89A3C))
                                },
                                onClick = {
                                    showMenu = false
                                    onSaveAsFavorite()
                                }
                            )
                        }

                        if (onDelete != null) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(
                                text = { Text("Hapus Transaksi", color = StatusNegative, fontWeight = FontWeight.SemiBold) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = StatusNegative)
                                },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            if (showDivider) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 0.8.dp,
                    modifier = Modifier.padding(start = 76.dp, end = 20.dp)
                )
            }
        }
    }
}
