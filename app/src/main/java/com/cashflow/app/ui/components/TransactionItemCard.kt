package com.cashflow.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
 * Open Editorial Transaction Row for "Duit Aing"
 * Eliminates heavy individual card containers in favor of high-scanability rows with dividers.
 */
@Composable
fun TransactionItemCard(
    transaction: Transaction,
    category: Category?,
    sourceAsset: Asset?,
    destinationAsset: Asset?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1.0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f),
        label = "TxItemPressScale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
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
            // Category or Transfer Icon (Clean, lightweight squircle)
            if (transaction.type == TransactionType.TRANSFER.value) {
                CategoryIcon(
                    iconName = "swap_horiz",
                    size = 40.dp,
                    iconSize = 20.dp,
                    backgroundColor = StatusTransferContainer,
                    tintColor = StatusTransfer
                )
            } else {
                CategoryIcon(
                    iconName = category?.icon ?: "category",
                    size = 40.dp,
                    iconSize = 20.dp,
                    backgroundColor = if (transaction.type == TransactionType.INCOME.value)
                        StatusPositiveContainer else JadePrimaryLight,
                    tintColor = if (transaction.type == TransactionType.INCOME.value)
                        StatusPositive else JadePrimary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details: Description & Subtitle (Category · Asset)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = transaction.description,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp
                        ),
                        color = TextPrimary,
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

                // Subtitle: Category · Asset or Source -> Destination
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
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Amount Highlight (Clear typography, no container needed)
            val prefix = when (transaction.type) {
                TransactionType.INCOME.value -> "+"
                TransactionType.EXPENSE.value -> "−"
                else -> ""
            }
            val amountColor = when (transaction.type) {
                TransactionType.INCOME.value -> StatusPositive
                TransactionType.EXPENSE.value -> StatusNegative
                else -> StatusTransfer
            }

            Text(
                text = prefix + Formatters.formatRupiah(transaction.amount),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    letterSpacing = (-0.4).sp
                ),
                color = amountColor,
                softWrap = false,
                maxLines = 1
            )
        }

        if (showDivider) {
            HorizontalDivider(
                color = PebbleBorder,
                thickness = 0.8.dp,
                modifier = Modifier.padding(start = 72.dp, end = 20.dp)
            )
        }
    }
}
