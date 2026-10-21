package com.cashflow.app.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import java.time.LocalDate
import java.time.YearMonth

/**
 * Editorial Monthly Budgeting for "Duit Aing"
 * Open layout without excessive containers.
 */
@Composable
fun BudgetsScreen(
    viewModel: CashflowViewModel,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (onBack != null) {
        BackHandler(enabled = true) { onBack() }
    }

    val duit = MaterialTheme.duit
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
            .background(duit.background)
    ) {
        // Header with spacious, elegant layout
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                if (onBack != null) {
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(duit.surface)
                            .border(1.dp, duit.border, RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button, onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Kembali ke Beranda",
                            tint = duit.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "Anggaran",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            letterSpacing = (-0.5).sp
                        ),
                        color = duit.textPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${budgets.size} pos belanja aktif",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp
                        ),
                        color = duit.textSecondary
                    )
                }
            }

            DuitAingButton(
                text = "Tambah",
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
                height = 44.dp
            )
        }

        // Rollover Toggle & Info Row
        val isRolloverEnabled by viewModel.isBudgetRolloverEnabled.collectAsState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(duit.surface)
                .border(1.dp, duit.border, RoundedCornerShape(12.dp))
                .clickable(role = Role.Switch) {
                    viewModel.setBudgetRolloverEnabled(!isRolloverEnabled)
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Autorenew,
                    contentDescription = null,
                    tint = if (isRolloverEnabled) duit.primary else duit.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "Alihkan Sisa Periode Lalu",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        ),
                        color = duit.textPrimary
                    )
                    Text(
                        text = if (isRolloverEnabled) "Aktif • Sisa periode lalu menambah batas" else "Nonaktif • Batas tetap setiap periode",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp
                        ),
                        color = duit.textSecondary
                    )
                }
            }
            Switch(
                checked = isRolloverEnabled,
                onCheckedChange = { viewModel.setBudgetRolloverEnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = duit.onPrimary,
                    checkedTrackColor = duit.primary,
                    uncheckedThumbColor = duit.textSecondary,
                    uncheckedTrackColor = duit.track
                )
            )
        }

        if (budgets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedEmptyState(
                    icon = Icons.Rounded.PieChartOutline,
                    lottieRawRes = com.cashflow.app.R.raw.lottie_shopping_cart,
                    title = "KENDALIKAN ARUS KAS",
                    description = "Tetapkan batas per pos belanja agar pengeluaran terukur.",
                    actionText = "Buat Anggaran Pertama",
                    actionIcon = Icons.Rounded.Add,
                    onActionClick = {
                        editingBudget = null
                        if (expenseCategories.isNotEmpty()) {
                            selectedCategoryId = expenseCategories.first().id
                        }
                        targetAmountInput = ""
                        periodTypeInput = "monthly"
                        showBudgetDialog = true
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)
            ) {
                items(budgets, key = { it.budget.id }, contentType = { "budget" }) { item ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        DoubleBezelCard(
                            modifier = Modifier.fillMaxWidth(),
                            outerPadding = 3.dp,
                            outerRadius = 14.dp,
                            innerRadius = 11.dp,
                            shadowOffset = 2.dp,
                            innerColor = duit.surface,
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
                                val statusLabel = when (item.status) {
                                    BudgetStatus.SAFE -> "AMAN"
                                    BudgetStatus.WARNING -> "WASPADA"
                                    BudgetStatus.NEAR_LIMIT -> "HAMPIR PENUH"
                                    BudgetStatus.EXCEEDED -> "MELEBIHI"
                                }

                                val duit = MaterialTheme.duit
                                val progressColor = when {
                                    item.progressPercentage >= 1.0f -> duit.expense
                                    item.progressPercentage >= 0.80f -> duit.warning
                                    else -> duit.primary
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
                                    BudgetStatus.SAFE -> duit.income
                                    BudgetStatus.WARNING -> duit.warning
                                    BudgetStatus.NEAR_LIMIT -> duit.warning
                                    BudgetStatus.EXCEEDED -> duit.expense
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
                                        Column(modifier = Modifier.weight(1f, fill = false)) {
                                            Text(
                                                text = (item.category?.name ?: "Kategori").uppercase(),
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 14.sp,
                                                    letterSpacing = 0.5.sp
                                                ),
                                                color = duit.textPrimary,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(1.dp))
                                            Text(
                                                text = if (item.budget.periodType == "monthly") "PLAFON BULANAN" else "PLAFON MINGGUAN",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 0.5.sp
                                                ),
                                                color = duit.textSecondary,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
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
                                                tint = duit.textSecondary.copy(alpha = 0.6f),
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
                                                fontSize = 12.sp
                                            ),
                                            color = duit.textSecondary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (item.remainingAmount >= 0)
                                                Formatters.displayRupiah(item.remainingAmount)
                                            else
                                                Formatters.displayRupiah(-item.remainingAmount),
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
                                            fontSize = 12.sp,
                                            letterSpacing = (-0.2).sp
                                        ),
                                        color = if (item.status == BudgetStatus.EXCEEDED) duit.expense else duit.textPrimary
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
                                    trackColor = duit.track
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                val daysRemaining = item.daysRemaining
                                val dailyRemaining = (item.remainingAmount / daysRemaining).coerceAtLeast(0L)

                                // Row 4: Subtle Contextual Footer (Terpakai & Sisa Harian)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Terpakai: " + Formatters.displayRupiah(item.spentAmount),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp
                                        ),
                                        color = duit.textSecondary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Text(
                                        text = "Sisa ${Formatters.displayCompact(dailyRemaining)}/hari ($daysRemaining hari lagi)",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        ),
                                        color = progressColor,
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
