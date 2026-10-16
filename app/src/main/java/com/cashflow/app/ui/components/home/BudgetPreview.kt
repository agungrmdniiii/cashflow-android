package com.cashflow.app.ui.components.home

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.data.model.Budget
import com.cashflow.app.data.model.BudgetStatus
import com.cashflow.app.data.model.BudgetWithProgress
import com.cashflow.app.ui.components.CategoryIcon
import com.cashflow.app.ui.components.DoubleBezelCard
import com.cashflow.app.ui.components.Formatters
import com.cashflow.app.ui.theme.duit

/**
 * Pratinjau Anggaran untuk Beranda (Tahap 2):
 * - Maksimal 3 anggaran teratas.
 * - Progress bar tebal (8dp) dengan status warna jelas:
 *   - Hijau (< 80%)
 *   - Amber (80-99%)
 *   - Merah (>= 100%)
 * - Opsi "Lihat semua" & tombol "Tambah" (target sentuh >= 48dp).
 * - Empty state ringkas (ikon kecil + 1 kalimat + 1 tombol).
 */
@Composable
fun BudgetPreview(
    budgets: List<BudgetWithProgress>,
    isBalanceHidden: Boolean,
    onViewAllClick: () -> Unit,
    onAddBudgetClick: () -> Unit,
    onEditBudget: (Budget) -> Unit,
    onDeleteBudget: (BudgetWithProgress) -> Unit,
    modifier: Modifier = Modifier
) {
    val duit = MaterialTheme.duit
    val topBudgets = remember(budgets) { budgets.take(3) }
    val context = LocalContext.current
    val reduceMotion = remember {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Header Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onViewAllClick)
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Anggaran Belanja",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = duit.textPrimary
                )
                if (budgets.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(${budgets.size})",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        color = duit.textSecondary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = "Buka Anggaran",
                    tint = duit.textSecondary,
                    modifier = Modifier.size(15.dp)
                )
            }

            // Tambah Anggaran Button (Target sentuh >= 48dp)
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .defaultMinSize(minHeight = 44.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(duit.surfaceVariant)
                    .border(1.dp, duit.border, RoundedCornerShape(999.dp))
                    .clickable(role = Role.Button, onClick = onAddBudgetClick)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Tambah Anggaran",
                        tint = duit.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Tambah",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = duit.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (budgets.isEmpty()) {
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
                            imageVector = Icons.Rounded.PieChartOutline,
                            contentDescription = null,
                            tint = duit.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Belum ada anggaran belanja yang dibuat.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp
                        ),
                        color = duit.textSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onAddBudgetClick,
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
                                text = "Buat Anggaran",
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
            // Top 3 Budgets List (Spacing antar item 12dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                topBudgets.forEach { item ->
                    var menuExpanded by remember { mutableStateOf(false) }

                    DoubleBezelCard(
                        modifier = Modifier.fillMaxWidth(),
                        innerRadius = 18.dp,
                        innerColor = duit.surface,
                        onClick = onViewAllClick
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp)
                        ) {
                            val progressColor = when {
                                item.progressPercentage >= 1.0f -> duit.expense
                                item.progressPercentage >= 0.80f -> duit.warning
                                else -> duit.primary
                            }

                            val animatedProgress by animateFloatAsState(
                                targetValue = item.progressPercentage.coerceIn(0f, 1f),
                                animationSpec = if (reduceMotion) snap() else tween(durationMillis = 500, easing = FastOutSlowInEasing),
                                label = "HomeBudgetProgress_${item.budget.id}"
                            )

                            // Row 1: Icon, Kategori, Sisa & Menu 3 Titik
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    CategoryIcon(
                                        iconName = item.category?.icon ?: "category",
                                        size = 36.dp,
                                        iconSize = 18.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = item.category?.name ?: "Kategori",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            ),
                                            color = duit.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (isBalanceHidden) "••••••" else {
                                                if (item.remainingAmount >= 0) "Sisa ${Formatters.formatRupiah(item.remainingAmount)}"
                                                else "Defisit ${Formatters.formatRupiah(-item.remainingAmount)}"
                                            },
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = progressColor
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${(item.progressPercentage * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp
                                        ),
                                        color = if (item.status == BudgetStatus.EXCEEDED) duit.expense else duit.textPrimary
                                    )

                                    // Menu 3 Titik (Target sentuh >= 48dp)
                                    Box {
                                        IconButton(
                                            onClick = { menuExpanded = true },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.MoreVert,
                                                contentDescription = "Opsi anggaran",
                                                tint = duit.textSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = menuExpanded,
                                            onDismissRequest = { menuExpanded = false },
                                            modifier = Modifier.background(duit.surface)
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Edit Anggaran", color = duit.textPrimary) },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Rounded.Edit,
                                                        contentDescription = null,
                                                        tint = duit.textPrimary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                },
                                                onClick = {
                                                    menuExpanded = false
                                                    onEditBudget(item.budget)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Hapus", color = duit.expense) },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Rounded.DeleteOutline,
                                                        contentDescription = null,
                                                        tint = duit.expense,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                },
                                                onClick = {
                                                    menuExpanded = false
                                                    onDeleteBudget(item)
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Row 2: Progress Bar Tebal (8dp)
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(999.dp)),
                                color = progressColor,
                                trackColor = duit.track
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Row 3: Terpakai vs Plafon
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isBalanceHidden) "Terpakai: ••••••" else "Terpakai: " + Formatters.formatRupiah(item.spentAmount),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 11.5.sp
                                    ),
                                    color = duit.textSecondary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = if (isBalanceHidden) "Plafon: ••••••" else "Plafon: " + Formatters.formatRupiah(item.effectiveTargetAmount),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 11.5.sp
                                    ),
                                    color = duit.textSecondary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            // Tombol "Lihat semua (N) anggaran" jika total lebih dari 3
            if (budgets.size > 3) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(
                        onClick = onViewAllClick,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Lihat semua (${budgets.size}) anggaran",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = duit.primary
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = duit.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
