package com.cashflow.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
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
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.cashflow.app.data.model.Asset
import com.cashflow.app.data.model.Debt
import com.cashflow.app.data.model.DebtStatus
import com.cashflow.app.data.model.FinancialGoal
import com.cashflow.app.data.model.TransactionType
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.components.*
import com.cashflow.app.ui.theme.*
import java.time.LocalDate

/**
 * Editorial Asset & Savings Management for "Duit Aing"
 * Open layout emphasizing balances and clean lists rather than bulky containers.
 */
@Composable
fun AssetsScreen(
    viewModel: CashflowViewModel,
    modifier: Modifier = Modifier
) {
    val totalWealth by viewModel.totalWealth.collectAsState()
    val activeAssets by viewModel.activeAssets.collectAsState()
    val defaultAsset by viewModel.defaultAsset.collectAsState()
    val goals by viewModel.goals.collectAsState()
    val isBalanceHidden by viewModel.isBalanceHidden.collectAsState()

    val debts by viewModel.debts.collectAsState()
    val totalRemainingDebt by viewModel.totalRemainingDebt.collectAsState()
    val totalPaidDebt by viewModel.totalPaidDebt.collectAsState()
    val activeDebtsCount by viewModel.activeDebtsCount.collectAsState()
    val overdueDebtsCount by viewModel.overdueDebtsCount.collectAsState()
    val netWorth by viewModel.netWorth.collectAsState()
    val subTab by viewModel.assetScreenSubTab.collectAsState()
    val selectedDebtFilter by viewModel.selectedDebtFilter.collectAsState()
    val isAddDebtOpen by viewModel.isAddDebtOpen.collectAsState()
    val payingDebt by viewModel.payingDebt.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    var debtToDelete by remember { mutableStateOf<Debt?>(null) }
    var goalToDelete by remember { mutableStateOf<FinancialGoal?>(null) }

    val avg3MonthSavings = remember(transactions) {
        val now = java.time.YearMonth.now()
        var totalNet = 0L
        for (i in 0..2) {
            val ym = now.minusMonths(i.toLong()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))
            val inc = transactions.filter { it.type == "income" && it.transactionDate.startsWith(ym) }.sumOf { it.amount }
            val exp = transactions.filter { it.type == "expense" && it.transactionDate.startsWith(ym) }.sumOf { it.amount }
            totalNet += (inc - exp)
        }
        (totalNet / 3).coerceAtLeast(0L)
    }

    val filteredDebts = remember(debts, selectedDebtFilter) {
        when (selectedDebtFilter) {
            "ACTIVE" -> debts.filter { it.status != DebtStatus.PAID.key }
            "PAID" -> debts.filter { it.status == DebtStatus.PAID.key }
            else -> debts
        }
    }

    val isDark = LocalIsDarkTheme.current
    val duit = MaterialTheme.duit
    var showGoalFormSheet by remember { mutableStateOf(false) }
    var goalToEdit by remember { mutableStateOf<FinancialGoal?>(null) }
    var goalToDeposit by remember { mutableStateOf<FinancialGoal?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(duit.background)
    ) {
        // 1. Top Bar Header with spacious layout
        Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Text(
                        text = "Aset & Hutang",
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
                        text = if (subTab == 0) "${activeAssets.size} akun aktif" else "$activeDebtsCount kewajiban aktif",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp
                        ),
                        color = TextSecondary
                    )
                }

                DuitAingButton(
                    text = if (subTab == 0) "Tambah Aset" else "Tambah Hutang",
                    onClick = {
                        if (subTab == 0) {
                            viewModel.editingAsset.value = null
                            viewModel.isAddAssetOpen.value = true
                        } else {
                            viewModel.isAddDebtOpen.value = true
                        }
                    },
                    leadingIcon = Icons.Rounded.Add,
                    variant = DuitAingButtonVariant.PRIMARY,
                    height = 44.dp,
                    shadowOffset = 2.dp
                )
            }

        // 2. Segmented Switcher (ASET SAYA | HUTANG SAYA) with unified tactile control
        DuitAingSegmentedControl(
            items = listOf(
                SegmentItem(value = 0, label = "ASET SAYA"),
                SegmentItem(value = 1, label = "HUTANG / PIUTANG", badgeCount = activeDebtsCount)
            ),
            selectedValue = subTab,
            onValueChange = { viewModel.setAssetScreenSubTab(it) },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))

        AnimatedContent(
            targetState = subTab,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally(
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        initialOffsetX = { fullWidth -> (fullWidth * 0.15f).toInt() }
                    ) + fadeIn(tween(180))).togetherWith(
                        slideOutHorizontally(
                            animationSpec = tween(180, easing = FastOutSlowInEasing),
                            targetOffsetX = { fullWidth -> (-fullWidth * 0.15f).toInt() }
                        ) + fadeOut(tween(140))
                    )
                } else {
                    (slideInHorizontally(
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        initialOffsetX = { fullWidth -> (-fullWidth * 0.15f).toInt() }
                    ) + fadeIn(tween(180))).togetherWith(
                        slideOutHorizontally(
                            animationSpec = tween(180, easing = FastOutSlowInEasing),
                            targetOffsetX = { fullWidth -> (fullWidth * 0.15f).toInt() }
                        ) + fadeOut(tween(140))
                    )
                }
            },
            label = "AssetsSubTabTransition",
            modifier = Modifier.weight(1f)
        ) { currentSubTab ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                if (currentSubTab == 0) {
            // 2. Total Wealth Focal Point with Concentric DarkDoubleBezelCard Architecture
            item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                val duit = MaterialTheme.duit
                DarkDoubleBezelCard(
                    modifier = Modifier.fillMaxWidth(),
                    outerPadding = 5.dp,
                    outerRadius = 20.dp,
                    innerRadius = 15.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EyebrowTag(
                                text = "TOTAL KEKAYAAN BERSIH",
                                variant = EyebrowVariant.DARK
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Balance Privacy Toggle (Accessible >= 48dp touch target)
                                Box(
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(duit.onHero.copy(alpha = 0.12f))
                                        .clickable(role = Role.Button) { viewModel.toggleBalanceVisibility() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isBalanceHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (isBalanceHidden) "Tampilkan nominal saldo" else "Sembunyikan nominal saldo",
                                        tint = duit.onHeroSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                EyebrowTag(
                                    text = "${activeAssets.size} ASET AKTIF",
                                    variant = EyebrowVariant.DARK
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (isBalanceHidden) {
                            Text(
                                text = "Rp ••••••••",
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 2.sp
                                ),
                                color = duit.onHero,
                                maxLines = 1,
                                softWrap = false
                            )
                        } else {
                            AnimatedNumberText(
                                value = totalWealth,
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-1.2).sp
                                ),
                                color = duit.onHero,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(26.dp))
        }

        // 3. Assets Section Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "DAFTAR ASET",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        ),
                        color = duit.textPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    EyebrowTag(
                        text = "${activeAssets.size} TERDAFTAR",
                        variant = EyebrowVariant.DEFAULT
                    )
                }

                DuitAingButton(
                    text = "TRANSFER",
                    onClick = {
                        viewModel.addTransactionInitialType.value = TransactionType.TRANSFER
                        viewModel.editingTransaction.value = null
                        viewModel.isAddTransactionOpen.value = true
                    },
                    leadingIcon = Icons.Rounded.SwapHoriz,
                    variant = DuitAingButtonVariant.SECONDARY,
                    height = 40.dp,
                    shadowOffset = 2.dp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Render Active Assets as Open List Items
        items(activeAssets, key = { it.id }, contentType = { "asset" }) { asset ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { viewModel.selectedAssetForDetail.value = asset }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    AssetIcon(
                        assetType = asset.type,
                        size = 40.dp,
                        iconSize = 20.dp
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = asset.name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                ),
                                color = duit.textPrimary
                            )
                            if (asset.isDefault) {
                                Spacer(modifier = Modifier.width(6.dp))
                                AssetBadge(text = "Utama")
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = asset.type.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                letterSpacing = 0.4.sp
                            ),
                            color = TextSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isBalanceHidden) "••••••" else Formatters.formatRupiah(asset.currentBalance),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.5.sp,
                            letterSpacing = (-0.4).sp
                        ),
                        color = duit.textPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            HorizontalDivider(
                color = duit.border,
                thickness = 0.8.dp,
                modifier = Modifier.padding(start = 72.dp, end = 20.dp)
            )
        }

        // 4. Section: Target Keuangan (Financial Goals)
        item {
            Spacer(modifier = Modifier.height(32.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        Text(
                            text = "TARGET KEUANGAN",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp,
                                fontSize = 15.sp
                            ),
                            color = duit.textPrimary,
                            maxLines = 1
                        )
                        EyebrowTag(
                            text = "${goals.size} TARGET",
                            variant = EyebrowVariant.DEFAULT
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "RENCANA & IMPIAN",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        ),
                        color = TextSecondary
                    )
                }

                DuitAingButton(
                    text = "BUAT TARGET",
                    onClick = {
                        goalToEdit = null
                        showGoalFormSheet = true
                    },
                    leadingIcon = Icons.Rounded.Add,
                    variant = DuitAingButtonVariant.SECONDARY,
                    height = 38.dp,
                    shadowOffset = 2.dp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (goals.isEmpty()) {
            item {
                AnimatedEmptyState(
                    icon = Icons.Rounded.Savings,
                    title = "BELUM ADA TARGET KEUANGAN",
                    description = "Tetapkan sasaran tabungan, dana darurat, atau impian penting dan pantau progresnya setiap hari.",
                    lottieRawRes = com.cashflow.app.R.raw.lottie_calm_zen,
                    actionText = "BUAT TARGET PERTAMA",
                    actionIcon = Icons.Rounded.Add,
                    onActionClick = {
                        goalToEdit = null
                        showGoalFormSheet = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        } else {
            items(goals, key = { it.id }, contentType = { "goal" }) { goal ->
                DoubleBezelCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    shadowOffset = 2.dp,
                    outerPadding = 3.dp,
                    outerRadius = 14.dp,
                    innerRadius = 11.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (goal.progressPercentage >= 1.0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(duit.primaryContainer)
                                    .border(1.dp, duit.border, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Celebration,
                                        contentDescription = null,
                                        tint = duit.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "🎉 TARGET TERCAPAI 100%!",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp
                                        ),
                                        color = duit.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Text(
                                    text = goal.name,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp
                                    ),
                                    color = duit.textPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "TARGET: ${Formatters.formatDateReadable(goal.deadline)}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 0.4.sp
                                    ),
                                    color = TextSecondary
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        goalToEdit = goal
                                        showGoalFormSheet = true
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Edit Target Keuangan",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { goalToDelete = goal },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Hapus Target Keuangan",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        LinearProgressIndicator(
                            progress = { goal.progressPercentage.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp)),
                            color = duit.primary,
                            trackColor = duit.track
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "TERKUMPUL",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = TextTertiary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isBalanceHidden) "••••••" else Formatters.formatRupiah(goal.currentAmount),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.5.sp
                                    ),
                                    color = duit.primary
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = "TARGET (${(goal.progressPercentage * 100).toInt()}%)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = TextTertiary,
                                    textAlign = TextAlign.End
                                )
                                 Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isBalanceHidden) "••••••" else Formatters.formatRupiah(goal.targetAmount),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    ),
                                    color = duit.textPrimary,
                                    textAlign = TextAlign.End
                                )
                            }
                        }

                        // Savings Projection / Estimated Completion
                        val remainingToSave = (goal.targetAmount - goal.currentAmount).coerceAtLeast(0L)
                        if (goal.progressPercentage < 1.0f && remainingToSave > 0L) {
                            val estMonths = if (avg3MonthSavings > 0L) {
                                kotlin.math.ceil(remainingToSave.toDouble() / avg3MonthSavings.toDouble()).toInt()
                            } else null

                            val deadlineMonths = try {
                                val deadlineDate = LocalDate.parse(goal.deadline)
                                val now = LocalDate.now()
                                val period = java.time.Period.between(now, deadlineDate)
                                (period.years * 12 + period.months).coerceAtLeast(1)
                            } catch (e: Exception) { null }

                            val projectionText = when {
                                estMonths != null -> "⏱ Estimasi tercapai: ~$estMonths bulan lagi (surplus rata-rata: ${Formatters.formatCompact(avg3MonthSavings)}/bln)"
                                deadlineMonths != null -> "⏱ Target tenggat: ~$deadlineMonths bulan lagi (${Formatters.formatDateReadable(goal.deadline)})"
                                else -> "⏱ Tenggat target: ${Formatters.formatDateReadable(goal.deadline)}"
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(duit.surfaceVariant.copy(alpha = 0.6f))
                                    .border(0.5.dp, duit.border, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = projectionText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        DuitAingButton(
                            text = if (goal.progressPercentage >= 1.0f) "+ TAMBAH DANA LAGI" else "+ NABUNG SEKARANG",
                            onClick = { goalToDeposit = goal },
                            leadingIcon = Icons.Rounded.Savings,
                            variant = DuitAingButtonVariant.SECONDARY,
                            height = 38.dp,
                            shadowOffset = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

        if (currentSubTab == 1) {
        // 1. Debt Summary Card
        item {
            DebtSummaryCard(
                totalRemaining = totalRemainingDebt,
                totalPaid = totalPaidDebt,
                activeCount = activeDebtsCount,
                overdueCount = overdueDebtsCount,
                netWorth = netWorth,
                isBalanceHidden = isBalanceHidden
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        // 2. Filter Chips Row (Accessible >= 48dp touch targets)
        item {
            val receivables = debts.filter { it.direction == "receivable" }.sumOf { it.remainingAmount }
            Text("Piutang belum diterima: ${Formatters.displayRupiah(receivables)}", modifier = Modifier.padding(horizontal = 20.dp), color = duit.textPrimary)
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val filters = listOf(
                    "ALL" to "SEMUA (${debts.size})",
                    "ACTIVE" to "BELUM LUNAS ($activeDebtsCount)",
                    "PAID" to "LUNAS (${debts.count { it.status == DebtStatus.PAID.key }})"
                )

                val duit = MaterialTheme.duit
                filters.forEach { (key, label) ->
                    val isSelected = selectedDebtFilter == key
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isSelected) duit.primary else duit.surfaceVariant)
                            .border(
                                1.dp,
                                if (isSelected) Color.Transparent else duit.border,
                                RoundedCornerShape(999.dp)
                            )
                            .clickable(role = Role.Button) { viewModel.setDebtFilter(key) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            ),
                            color = if (isSelected) duit.onPrimary else duit.textSecondary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // 3. Debt List or Celebratory Empty State
        if (filteredDebts.isEmpty()) {
            item {
                DoubleBezelCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    outerPadding = 3.dp,
                    outerRadius = 18.dp,
                    innerRadius = 14.dp,
                    shadowOffset = 2.dp,
                    innerColor = duit.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Lottie Calm Zen Motion
                        val zenComposition by rememberLottieComposition(LottieCompositionSpec.RawRes(com.cashflow.app.R.raw.lottie_calm_zen))
                        val zenProgress by animateLottieCompositionAsState(
                            composition = zenComposition,
                            iterations = LottieConstants.IterateForever,
                            speed = 1.0f
                        )

                        Box(
                            modifier = Modifier
                                .size(130.dp)
                                .padding(bottom = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (zenComposition != null) {
                                LottieAnimation(
                                    composition = zenComposition,
                                    progress = { zenProgress },
                                    enableMergePaths = true,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(duit.primaryContainer)
                                        .border(1.dp, duit.border, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Verified,
                                        contentDescription = null,
                                        tint = duit.primary,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Status Tag
                        EyebrowTag(
                            text = if (selectedDebtFilter == "LUNAS") "SEMUA TERCATAT LUNAS" else "100% BEBAS HUTANG",
                            variant = EyebrowVariant.PRIMARY
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (selectedDebtFilter == "LUNAS")
                                "Semua Cicilan Telah Tuntas"
                            else
                                "Merdeka Finansial Tanpa Cicilan",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.5.sp,
                                letterSpacing = (-0.2).sp
                            ),
                            color = duit.textPrimary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (selectedDebtFilter == "LUNAS")
                                "Catatan pinjaman yang sudah Anda selesaikan tersimpan aman sebagai rekam jejak kedisiplinan keuangan."
                            else
                                "Tidak ada kewajiban hutang yang menggantung. Setiap rupiah arus kasmu 100% milik ketenangan masa depanmu.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp
                            ),
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filteredDebts, key = { it.id }, contentType = { "debt" }) { debt ->
                DebtItemCard(
                    debt = debt,
                    onPayClick = { viewModel.payingDebt.value = debt },
                    onDeleteClick = { debtToDelete = debt },
                    isBalanceHidden = isBalanceHidden
                )
            }
        }
    }
}
}
}

    // Goal Form Sheet (Create / Edit Financial Goal)
    GoalFormSheet(
        isOpen = showGoalFormSheet,
        goalToEdit = goalToEdit,
        assets = activeAssets,
        onDismiss = {
            showGoalFormSheet = false
            goalToEdit = null
        },
        onSave = { name, targetAmount, currentAmount, deadline, assetId, note ->
            val goal = if (goalToEdit != null) {
                goalToEdit!!.copy(
                    name = name,
                    targetAmount = targetAmount,
                    currentAmount = currentAmount,
                    deadline = deadline,
                    assetId = assetId,
                    note = note
                )
            } else {
                FinancialGoal(
                    id = "goal_${System.currentTimeMillis()}",
                    name = name,
                    targetAmount = targetAmount,
                    currentAmount = currentAmount,
                    deadline = deadline,
                    assetId = assetId,
                    note = note
                )
            }
            viewModel.saveGoal(goal, isNew = goalToEdit == null) {
                showGoalFormSheet = false
                goalToEdit = null
            }
        }
    )

    // Deposit Goal Sheet (Save money towards goal, optional asset balance deduction)
    DepositGoalSheet(
        goal = goalToDeposit,
        assets = activeAssets,
        onDismiss = { goalToDeposit = null },
        onConfirmDeposit = { goalId, amount, sourceAssetId, note ->
            viewModel.depositToGoal(goalId, amount, sourceAssetId, note) {
                goalToDeposit = null
            }
        }
    )

    // Add Debt Dialog
    AddDebtDialog(
        isOpen = isAddDebtOpen,
        assets = activeAssets,
        defaultAssetId = defaultAsset?.id,
        onDismiss = { viewModel.isAddDebtOpen.value = false },
        onSave = { title, lender, amount, dueDate, note, receiveAssetId, direction ->
            viewModel.addDebt(title, lender, amount, dueDate, note, receiveAssetId, direction)
        }
    )

    // Pay Debt Dialog
    PayDebtDialog(
        debt = payingDebt,
        assets = activeAssets,
        defaultAssetId = defaultAsset?.id,
        onDismiss = { viewModel.payingDebt.value = null },
        onConfirmPay = { debtId, amount, sourceAssetId, paymentDate, note ->
            viewModel.recordDebtPayment(debtId, amount, sourceAssetId, paymentDate, note)
        }
    )

    // Delete Debt Confirmation Dialog (Standardized Neo-Brutalist Frame)
    val debtTitleToDelete = debtToDelete?.title ?: ""
    DuitAingConfirmDialog(
        isOpen = debtToDelete != null,
        title = "HAPUS CATATAN HUTANG?",
        message = "Apakah Anda yakin ingin menghapus catatan hutang \"$debtTitleToDelete\"? Tindakan ini tidak dapat dibatalkan.",
        confirmText = "HAPUS",
        confirmVariant = DuitAingButtonVariant.DANGER,
        onConfirm = {
            debtToDelete?.let { viewModel.deleteDebt(it.id, confirmed = true) }
            debtToDelete = null
        },
        onDismiss = { debtToDelete = null }
    )

    // Delete Financial Goal Confirmation Dialog (Standardized Neo-Brutalist Frame)
    val goalNameToDelete = goalToDelete?.name ?: ""
    DuitAingConfirmDialog(
        isOpen = goalToDelete != null,
        title = "HAPUS TARGET KEUANGAN?",
        message = "Apakah Anda yakin ingin menghapus target \"$goalNameToDelete\"? Progres tabungan Anda akan dihapus.",
        confirmText = "HAPUS",
        confirmVariant = DuitAingButtonVariant.DANGER,
        onConfirm = {
            goalToDelete?.let { viewModel.deleteGoal(it.id, confirmed = true) }
            goalToDelete = null
        },
        onDismiss = { goalToDelete = null }
    )
}
