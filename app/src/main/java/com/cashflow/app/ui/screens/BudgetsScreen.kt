package com.cashflow.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.cashflow.app.data.model.Budget
import com.cashflow.app.data.model.BudgetStatus
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.components.*
import com.cashflow.app.ui.theme.*

/**
 * Editorial Monthly Budgeting for "Duit Aing"
 * Open layout without excessive containers.
 */
@Composable
fun BudgetsScreen(
    viewModel: CashflowViewModel,
    modifier: Modifier = Modifier
) {
    val budgets by viewModel.budgets.collectAsState()
    val categories by viewModel.categories.collectAsState()

    var showBudgetDialog by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<Budget?>(null) }
    var selectedCategoryId by remember { mutableStateOf("") }
    var targetAmountInput by remember { mutableStateOf("") }
    var periodTypeInput by remember { mutableStateOf("monthly") }

    val expenseCategories = remember(categories) {
        categories.filter { it.type == "expense" && it.isActive }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PebbleBackground)
    ) {
        // Header with spacious, elegant layout
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = "ANGGARAN",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.3).sp
                    ),
                    color = DarkSurface,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.height(3.dp))
                EyebrowTag(
                    text = "${budgets.size} KATEGORI DITETAPKAN",
                    variant = EyebrowVariant.DEFAULT
                )
            }

            DuitAingButton(
                text = "TAMBAH",
                onClick = {
                    editingBudget = null
                    if (expenseCategories.isNotEmpty()) {
                        selectedCategoryId = expenseCategories.first().id
                    }
                    targetAmountInput = ""
                    periodTypeInput = "monthly"
                    showBudgetDialog = true
                },
                leadingIcon = Icons.Rounded.Add,
                variant = DuitAingButtonVariant.PRIMARY,
                height = 44.dp,
                shadowOffset = 2.dp
            )
        }

        if (budgets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                DoubleBezelCard(
                    modifier = Modifier.fillMaxWidth(),
                    outerPadding = 3.dp,
                    outerRadius = 16.dp,
                    innerRadius = 13.dp,
                    innerColor = PebbleSurface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(PebbleSurfaceVariant)
                                .border(1.dp, DarkBorder.copy(alpha = 0.20f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Savings,
                                contentDescription = null,
                                tint = DarkSurface,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "KENDALIKAN ARUS KAS",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            ),
                            color = DarkSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Anggaran adalah kompas belanja Anda. Tetapkan batas per pos agar pengeluaran terukur dan tabungan bertumbuh tenang.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        DuitAingButton(
                            text = "BUAT ANGGARAN PERTAMA",
                            onClick = {
                                editingBudget = null
                                if (expenseCategories.isNotEmpty()) {
                                    selectedCategoryId = expenseCategories.first().id
                                }
                                targetAmountInput = ""
                                periodTypeInput = "monthly"
                                showBudgetDialog = true
                            },
                            leadingIcon = Icons.Rounded.Add,
                            variant = DuitAingButtonVariant.PRIMARY,
                            height = 44.dp,
                            shadowOffset = 2.dp
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 90.dp, top = 12.dp)
            ) {
                items(budgets, key = { it.budget.id }) { item ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                    ) {
                        DoubleBezelCard(
                            modifier = Modifier.fillMaxWidth(),
                            outerPadding = 3.dp,
                            outerRadius = 14.dp,
                            innerRadius = 11.dp,
                            shadowOffset = 2.dp,
                            innerColor = PebbleSurface,
                            onClick = {
                                editingBudget = item.budget
                                selectedCategoryId = item.budget.categoryId
                                targetAmountInput = item.budget.targetAmount.toString()
                                periodTypeInput = item.budget.periodType
                                showBudgetDialog = true
                            }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // Status nuance & styling
                                val eyebrowVariant = when (item.status) {
                                    BudgetStatus.SAFE -> EyebrowVariant.POSITIVE
                                    BudgetStatus.WARNING -> EyebrowVariant.DEFAULT
                                    BudgetStatus.NEAR_LIMIT -> EyebrowVariant.DEFAULT
                                    BudgetStatus.EXCEEDED -> EyebrowVariant.NEGATIVE
                                }
                                val statusLabel = if (item.status == BudgetStatus.SAFE && item.progressPercentage <= 0.35f) {
                                    "TERKENDALI · DISIPLIN"
                                } else if (item.status == BudgetStatus.SAFE) {
                                    "PLAFON AMAN"
                                } else {
                                    item.status.displayName
                                }

                                val progressColor = when (item.status) {
                                    BudgetStatus.SAFE -> JadePrimary
                                    BudgetStatus.WARNING -> Color(0xFFD97706)
                                    BudgetStatus.NEAR_LIMIT -> Color(0xFFEA580C)
                                    BudgetStatus.EXCEEDED -> StatusNegative
                                }

                                val animatedProgress by animateFloatAsState(
                                    targetValue = item.progressPercentage.coerceIn(0f, 1f),
                                    animationSpec = tween(
                                        durationMillis = 550,
                                        easing = FastOutSlowInEasing
                                    ),
                                    label = "BudgetProgress_${item.budget.id}"
                                )

                                val remainingColor = when (item.status) {
                                    BudgetStatus.SAFE -> JadePrimary
                                    BudgetStatus.WARNING -> Color(0xFFD97706)
                                    BudgetStatus.NEAR_LIMIT -> Color(0xFFEA580C)
                                    BudgetStatus.EXCEEDED -> StatusNegative
                                }

                                // Row 1: Category Info & Status Badge + Delete Action
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
                                    ) {
                                        CategoryIcon(
                                            iconName = item.category?.icon ?: "category",
                                            size = 38.dp,
                                            iconSize = 20.dp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = (item.category?.name ?: "Kategori").uppercase(),
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 14.sp,
                                                    letterSpacing = 0.5.sp
                                                ),
                                                color = DarkSurface,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(1.dp))
                                            Text(
                                                text = if (item.budget.periodType == "monthly") "PLAFON BULANAN" else "PLAFON MINGGUAN",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 0.5.sp
                                                ),
                                                color = TextSecondary
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        EyebrowTag(
                                            text = statusLabel,
                                            variant = eyebrowVariant
                                        )
                                        IconButton(
                                            onClick = { viewModel.deleteBudget(item.budget.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = "Hapus Anggaran",
                                                tint = TextSecondary.copy(alpha = 0.6f),
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Row 2: Hero Metric (Sisa Anggaran + Tabular Figure)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Column {
                                        Text(
                                            text = if (item.remainingAmount >= 0) "SISA ANGGARAN" else "DEFISIT PLAFON",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.8.sp,
                                                fontSize = 9.5.sp
                                            ),
                                            color = TextSecondary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (item.remainingAmount >= 0)
                                                Formatters.formatRupiah(item.remainingAmount)
                                            else
                                                Formatters.formatRupiah(-item.remainingAmount),
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Black,
                                                fontSize = 19.sp,
                                                letterSpacing = (-0.6).sp
                                            ),
                                            color = remainingColor
                                        )
                                    }

                                    Text(
                                        text = "${(item.progressPercentage * 100).toInt()}% terpakai",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.5.sp,
                                            letterSpacing = (-0.2).sp
                                        ),
                                        color = if (item.status == BudgetStatus.EXCEEDED) StatusNegative else DarkSurface
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Row 3: Progress Bar (Sleek 6dp)
                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = progressColor,
                                    trackColor = PebbleSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Row 4: Subtle Contextual Footer (Terpakai & Plafon)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Terpakai: " + Formatters.formatRupiah(item.spentAmount),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp
                                        ),
                                        color = TextSecondary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Text(
                                        text = "Batas: " + Formatters.formatRupiah(item.budget.targetAmount),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp
                                        ),
                                        color = TextSecondary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Budget Dialog (Unified & Animated)
    BudgetFormDialog(
        isOpen = showBudgetDialog,
        editingBudget = editingBudget,
        categories = categories,
        onDismiss = {
            showBudgetDialog = false
            editingBudget = null
        },
        onSave = { newBudget ->
            viewModel.saveBudget(newBudget) {
                showBudgetDialog = false
                editingBudget = null
            }
        },
        onUpdate = { updatedBudget ->
            viewModel.updateBudget(updatedBudget) {
                showBudgetDialog = false
                editingBudget = null
            }
        }
    )
}
