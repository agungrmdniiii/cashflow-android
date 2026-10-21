package com.cashflow.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
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
 * - Native touch ripple, with no per-row press animation coroutine
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
    val duit = MaterialTheme.duit
    var showMenu by remember { mutableStateOf(false) }
    var menuWasOpened by remember { mutableStateOf(false) }
    val formattedAmount = remember(transaction.amount, transaction.type) {
        val prefix = when (transaction.type) {
            TransactionType.INCOME.value -> "+"
            TransactionType.EXPENSE.value -> "−"
            else -> ""
        }
        prefix + Formatters.formatRupiah(transaction.amount)
    }

    // Rows request an action and stay in place; a dismiss layout/anchor state is unnecessary.
    val currentEdit by rememberUpdatedState(onEdit)
    val currentDelete by rememberUpdatedState(onDelete)
    val directionMultiplier = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }
    var rowWidth by remember { mutableIntStateOf(0) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var swipeWasStarted by remember { mutableStateOf(false) }
    // Vertical flings should not create an animation state and coroutine for every new row.
    val swipeOffset = if (swipeWasStarted) {
        val offset by animateFloatAsState(
            targetValue = if (dragging) dragOffset else 0f,
            animationSpec = if (dragging) snap() else spring(stiffness = 500f),
            label = "TransactionSwipeOffset"
        )
        offset
    } else 0f
    val swipeModifier = if (onEdit == null && onDelete == null) Modifier else Modifier.pointerInput(
        onEdit != null, onDelete != null, directionMultiplier, threshold
    ) {
        detectHorizontalDragGestures(
            onDragStart = { swipeWasStarted = true; dragOffset = 0f; dragging = true },
            onDragCancel = { dragging = false },
            onDragEnd = {
                val logicalOffset = dragOffset * directionMultiplier
                dragging = false
                if (abs(logicalOffset) >= minOf(threshold, rowWidth * .45f) && rowWidth > 0) {
                    val action = if (logicalOffset > 0) currentEdit else currentDelete
                    if (action != null) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        action()
                    }
                }
            }
        ) { change, amount ->
            change.consume()
            val next = dragOffset + amount
            val logicalOffset = next * directionMultiplier
            dragOffset = if ((logicalOffset > 0 && currentEdit == null) || (logicalOffset < 0 && currentDelete == null)) 0f
                else next.coerceIn(-rowWidth.toFloat(), rowWidth.toFloat())
        }
    }
    Box(modifier.fillMaxWidth().onSizeChanged { rowWidth = it.width }.then(swipeModifier)) {
        if (dragging || swipeOffset != 0f) {
            val editing = (if (dragging) dragOffset else swipeOffset) * directionMultiplier > 0
            Box(Modifier.matchParentSize().background(if (editing) duit.primary else duit.expense)
                .padding(horizontal = 24.dp),
                contentAlignment = if (editing) Alignment.CenterStart else Alignment.CenterEnd) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(if (editing) Icons.Rounded.Edit else Icons.Rounded.Delete, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(24.dp))
                    Text(if (editing) "Edit" else "Hapus", color = Color.White,
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = swipeOffset
                }
                .background(MaterialTheme.colorScheme.surface)
                .clickable(
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
                        backgroundColor = MaterialTheme.duit.transfer,
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
                                    .background(duit.primaryContainer)
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Mic,
                                        contentDescription = "Suara",
                                        tint = duit.onPrimaryContainer,
                                        modifier = Modifier.size(9.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "SUARA",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 0.4.sp
                                        ),
                                        color = duit.onPrimaryContainer
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
                            "$src - $dst"
                        }
                        else -> {
                            val cat = category?.name ?: "Umum"
                            val ast = sourceAsset?.name ?: "Aset"
                            "$cat - $ast"
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
                val amountColor = when (transaction.type) {
                    TransactionType.INCOME.value -> duit.income
                    TransactionType.EXPENSE.value -> duit.expense
                    else -> duit.transfer
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                    text = if (isBalanceHidden) "Rp ••••••" else formattedAmount,
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
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                // 3-dots accessibility menu
                Box {
                    IconButton(
                        onClick = { menuWasOpened = true; showMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Opsi transaksi",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (menuWasOpened) DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        shape = RoundedCornerShape(12.dp),
                        containerColor = duit.surface
                    ) {
                        DropdownMenuItem(
                            text = { Text("Lihat Detail") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Visibility, contentDescription = null, tint = duit.textPrimary)
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
                                    Icon(Icons.Rounded.Edit, contentDescription = null, tint = duit.textPrimary)
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
                                    Icon(Icons.Rounded.Star, contentDescription = null, tint = duit.warning)
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
                                text = { Text("Hapus Transaksi", color = duit.expense, fontWeight = FontWeight.SemiBold) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = duit.expense)
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
