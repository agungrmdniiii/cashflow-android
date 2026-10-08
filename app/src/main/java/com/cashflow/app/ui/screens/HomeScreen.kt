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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.data.model.Budget
import com.cashflow.app.data.model.BudgetStatus
import com.cashflow.app.data.model.BudgetWithProgress
import com.cashflow.app.data.model.TransactionType
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.components.*
import com.cashflow.app.ui.theme.*

/**
 * Editorial Financial Dashboard for "Duit Aing"
 * Open layout without excessive card containers.
 * Strong typographic hierarchy, generous whitespace, and focused signature actions.
 */
@Composable
fun HomeScreen(
    viewModel: CashflowViewModel,
    onNavigateToTransactions: () -> Unit,
    onNavigateToAssets: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalWealth by viewModel.totalWealth.collectAsState()
    val totalRemainingDebt by viewModel.totalRemainingDebt.collectAsState()
    val netWorth by viewModel.netWorth.collectAsState()
    val periodIncome by viewModel.periodIncome.collectAsState()
    val periodExpense by viewModel.periodExpense.collectAsState()
    val netCashflow by viewModel.netCashflow.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val activeAssets by viewModel.activeAssets.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val largestExpense by viewModel.largestExpense.collectAsState()
    val budgets by viewModel.budgets.collectAsState()

    val catMap = remember(categories) { categories.associateBy { it.id } }
    val assetMap = remember(activeAssets) { activeAssets.associateBy { it.id } }
    val expenseCategories = remember(categories) {
        categories.filter { it.type == "expense" && it.isActive }
    }



    val recentTransactions = remember(transactions) {
        transactions.take(5)
    }

    var showPeriodSheet by remember { mutableStateOf(false) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<Budget?>(null) }
    var budgetToDelete by remember { mutableStateOf<BudgetWithProgress?>(null) }
    var showAllBudgets by remember { mutableStateOf(false) }
    val displayedBudgets = remember(budgets, showAllBudgets) {
        if (showAllBudgets) budgets else budgets.take(2)
    }
    val isBalanceHidden by viewModel.isBalanceHidden.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PebbleBackground),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        // 1. Top Brand Header with Logo & Settings
        item {
            DuitAingBrandHeader(
                onSettingsClick = { viewModel.isSettingsOpen.value = true }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // 2. Primary Focal Point: Hero Financial Command (Total Wealth, Privacy Mode & Net Worth)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                DarkDoubleBezelCard(
                    modifier = Modifier.fillMaxWidth(),
                    outerPadding = 4.dp,
                    outerRadius = 20.dp,
                    innerRadius = 16.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 22.dp)
                    ) {
                        // Top row: Section Label + Currency & Privacy Mask Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TOTAL KEKAYAAN",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    fontSize = 11.sp
                                ),
                                color = TextOnDarkSecondary
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val eyeRotation by animateFloatAsState(
                                    targetValue = if (isBalanceHidden) 180f else 0f,
                                    animationSpec = tween(durationMillis = 240, easing = DuitAingMotion.SubtleEasing),
                                    label = "EyeRotation"
                                )

                                // Balance Privacy Toggle (Accessible ≥48dp touch target via minimumInteractiveComponentSize)
                                Box(
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .clickable(role = Role.Button) { viewModel.toggleBalanceVisibility() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isBalanceHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (isBalanceHidden) "Tampilkan nominal saldo" else "Sembunyikan nominal saldo",
                                        tint = JadePrimaryLight,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .graphicsLayer { rotationZ = eyeRotation }
                                    )
                                }

                                Text(
                                    text = "IDR",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.sp,
                                        fontSize = 10.sp
                                    ),
                                    color = JadePrimaryLight
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Hero Wealth Amount (Masked or Live with AnimatedContent Transition)
                        AnimatedContent(
                            targetState = isBalanceHidden,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.96f)) togetherWith
                                (fadeOut(animationSpec = tween(120)) + scaleOut(targetScale = 1.04f))
                            },
                            label = "WealthPrivacyTransition"
                        ) { hidden ->
                            if (hidden) {
                                Text(
                                    text = "Rp ••••••••",
                                    style = MaterialTheme.typography.displayLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 2.sp
                                    ),
                                    color = TextOnDark,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            } else {
                                AnimatedNumberText(
                                    value = totalWealth,
                                    style = MaterialTheme.typography.displayLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 34.sp,
                                        letterSpacing = (-1.0).sp
                                    ),
                                    color = TextOnDark,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.12f), thickness = 0.8.dp)
                        Spacer(modifier = Modifier.height(14.dp))

                        // Wealth Breakdown (Adaptive layout for varied font scales and screen widths)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(JadePrimaryLight)
                                )
                                Text(
                                    text = if (isBalanceHidden) "Bersih: ••••••" else "Bersih: ${Formatters.formatRupiah(netWorth)}",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    ),
                                    color = Color(0xFFD6E4DE),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (totalRemainingDebt > 0L) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable(role = Role.Button) {
                                            viewModel.selectTab(3)
                                            viewModel.setAssetScreenSubTab(1)
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFF8E8E))
                                    )
                                    Text(
                                        text = if (isBalanceHidden) "Hutang: ••••••" else "Hutang: ${Formatters.formatRupiah(totalRemainingDebt)}",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp
                                        ),
                                        color = Color(0xFFFF8E8E),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                        contentDescription = "Buka Hutang",
                                        tint = Color(0xFFFF8E8E),
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Unified Primary Action: RECORD CASHFLOW (Voice default + Manual fallback in popup)
        item {
            Spacer(modifier = Modifier.height(18.dp))
            DuitAingButton(
                text = "RECORD CASHFLOW",
                onClick = { viewModel.isRecordVoiceOpen.value = true },
                leadingIcon = Icons.Rounded.Mic,
                trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                variant = DuitAingButtonVariant.PRIMARY,
                height = 52.dp,
                shadowOffset = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // 4. Dedicated Cashflow Section (Clean Header, Period Selector integrated inside Card)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                // Section Header: Simple & Uncluttered
                Text(
                    text = "RINGKASAN ARUS KAS",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp,
                        fontSize = 15.sp
                    ),
                    color = DarkSurface
                )

                Spacer(modifier = Modifier.height(10.dp))

                // High-End Cashflow Card with Integrated Period Control
                DoubleBezelCard(
                    modifier = Modifier.fillMaxWidth(),
                    outerPadding = 4.dp,
                    outerRadius = 18.dp,
                    innerRadius = 14.dp,
                    shadowOffset = 3.dp,
                    innerColor = PebbleSurface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        // Net cashflow header row with Integrated "Atur Periode"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Living financial heartbeat & label
                            val infinitePulse = rememberInfiniteTransition(label = "HeroCashflowPulse")
                            val pulseAlpha by infinitePulse.animateFloat(
                                initialValue = 0.35f,
                                targetValue = 1.0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1200, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "PulseAlpha"
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .graphicsLayer { alpha = pulseAlpha }
                                        .background(if (netCashflow >= 0) JadePrimary else StatusNegative, CircleShape)
                                )
                                Text(
                                    text = "ARUS KAS BERSIH",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp,
                                        fontSize = 10.5.sp
                                    ),
                                    color = TextSecondary
                                )
                            }

                            // Simple "Atur Periode" pill inside the card
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(PebbleSurfaceVariant)
                                    .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(6.dp))
                                    .clickable(role = Role.Button) { showPeriodSheet = true }
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CalendarMonth,
                                        contentDescription = null,
                                        tint = DarkSurface,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = selectedPeriod.displayName.uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.5.sp,
                                            letterSpacing = 0.4.sp
                                        ),
                                        color = DarkSurface
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowDropDown,
                                        contentDescription = "Atur Periode",
                                        tint = DarkSurface,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Large net cashflow value (Respects Privacy Mode with AnimatedContent)
                        AnimatedContent(
                            targetState = isBalanceHidden,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.96f)) togetherWith
                                (fadeOut(animationSpec = tween(120)) + scaleOut(targetScale = 1.04f))
                            },
                            label = "NetCashflowPrivacyTransition"
                        ) { hidden ->
                            if (hidden) {
                                Text(
                                    text = "Rp ••••••••",
                                    style = MaterialTheme.typography.displayMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.sp
                                    ),
                                    color = if (netCashflow >= 0) JadePrimary else StatusNegative,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            } else {
                                val netPrefix = if (netCashflow > 0) "+" else ""
                                AnimatedNumberText(
                                    value = netCashflow,
                                    prefix = netPrefix,
                                    style = MaterialTheme.typography.displayMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = (-1.0).sp
                                    ),
                                    color = if (netCashflow >= 0) JadePrimary else StatusNegative,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = PebbleBorder, thickness = 0.8.dp)
                        Spacer(modifier = Modifier.height(14.dp))

                        // Open 2-Column Metrics (Pemasukan vs Pengeluaran - No Nested Boxes)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Pemasukan
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(JadePrimary)
                                    )
                                    Text(
                                        text = "PEMASUKAN",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.6.sp,
                                            fontSize = 10.sp
                                        ),
                                        color = TextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                if (isBalanceHidden) {
                                    Text(
                                        text = "+••••••",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = JadePrimary
                                    )
                                } else {
                                    AnimatedNumberText(
                                        value = periodIncome,
                                        prefix = "+",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = (-0.3).sp,
                                            fontSize = 15.sp
                                        ),
                                        color = JadePrimary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            // Hairline vertical divider between columns
                            Box(
                                modifier = Modifier
                                    .height(32.dp)
                                    .width(1.dp)
                                    .background(PebbleBorder)
                            )

                            // Pengeluaran
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(StatusNegative)
                                    )
                                    Text(
                                        text = "PENGELUARAN",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.6.sp,
                                            fontSize = 10.sp
                                        ),
                                        color = TextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                if (isBalanceHidden) {
                                    Text(
                                        text = "−••••••",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = StatusNegative
                                    )
                                } else {
                                    AnimatedNumberText(
                                        value = periodExpense,
                                        prefix = "−",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = (-0.3).sp,
                                            fontSize = 15.sp
                                        ),
                                        color = StatusNegative,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        // Grounded contextual insight footnote (Clean open text, no nested card)
                        if (largestExpense != null) {
                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = PebbleBorder.copy(alpha = 0.6f), thickness = 0.6.dp)
                            Spacer(modifier = Modifier.height(10.dp))
                            val tx = largestExpense!!
                            val cat = tx.categoryId?.let { catMap[it] }
                            val catName = cat?.name ?: tx.description
                            val sum = tx.amount
                            val percent = if (periodExpense > 0) ((sum.toFloat() / periodExpense.toFloat()) * 100).toInt() else 0

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Insights,
                                        contentDescription = null,
                                        tint = AccentAmber,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Terbesar: $catName ($percent%)",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = if (isBalanceHidden) "••••••" else Formatters.formatRupiah(sum),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    ),
                                    color = DarkSurface
                                )
                            }
                        } else if (periodIncome > 0L && periodExpense == 0L) {
                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = PebbleBorder.copy(alpha = 0.6f), thickness = 0.6.dp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Verified,
                                    contentDescription = null,
                                    tint = JadePrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Belum ada pengeluaran periode ini · Kas utuh 100%",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = JadePrimaryDark
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Section: Anggaran Belanja (Replaces Aset Saya per Impeccable Layout)
        item {
            Spacer(modifier = Modifier.height(28.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "ANGGARAN BELANJA",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        ),
                        color = DarkSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(${budgets.size})",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextSecondary
                    )
                }

                // Add Budget Button with accessible touch target
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) {
                            editingBudget = null
                            showBudgetDialog = true
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Tambah Anggaran",
                            tint = JadePrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "TAMBAH",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = JadePrimary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Empty state when 0 budgets exist
        if (budgets.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.20f)),
                    colors = CardDefaults.cardColors(containerColor = PebbleSurface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(PebbleSurfaceVariant)
                                .border(1.dp, DarkBorder.copy(alpha = 0.20f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Savings,
                                contentDescription = null,
                                tint = DarkSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Belum Ada Anggaran Ditetapkan",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = DarkSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tetapkan batas belanja disiplin per kategori agar keuangan Anda terencana dengan tenang.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        DuitAingButton(
                            text = "BUAT ANGGARAN PERTAMA",
                            onClick = {
                                editingBudget = null
                                showBudgetDialog = true
                            },
                            leadingIcon = Icons.Rounded.Add,
                            variant = DuitAingButtonVariant.PRIMARY,
                            height = 44.dp
                        )
                    }
                }
            }
        } else {
            // List of Category Budgets (Capped to max 2 items by default)
            items(displayedBudgets, key = { it.budget.id }) { item ->
                Box(
                    modifier = Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 3.dp)
                ) {
                    DoubleBezelCard(
                        modifier = Modifier.fillMaxWidth(),
                        outerPadding = 2.dp,
                        outerRadius = 12.dp,
                        innerRadius = 10.dp,
                        shadowOffset = 2.dp,
                        innerColor = PebbleSurface,
                        onClick = {
                            editingBudget = item.budget
                            showBudgetDialog = true
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            val progressColor = when (item.status) {
                                BudgetStatus.SAFE -> JadePrimary
                                BudgetStatus.WARNING -> Color(0xFFD97706)
                                BudgetStatus.NEAR_LIMIT -> Color(0xFFEA580C)
                                BudgetStatus.EXCEEDED -> StatusNegative
                            }

                            val animatedProgress by animateFloatAsState(
                                targetValue = item.progressPercentage.coerceIn(0f, 1f),
                                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                                label = "HomeBudgetProgress_${item.budget.id}"
                            )

                            // Row 1: Icon, Category Name, Sisa Plafon & Delete
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
                                        size = 30.dp,
                                        iconSize = 16.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = item.category?.name ?: "Kategori",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            ),
                                            color = DarkSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (isBalanceHidden) "••••••" else {
                                                if (item.remainingAmount >= 0) "Sisa ${Formatters.formatRupiah(item.remainingAmount)}"
                                                else "Defisit ${Formatters.formatRupiah(-item.remainingAmount)}"
                                            },
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold
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
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp
                                        ),
                                        color = if (item.status == BudgetStatus.EXCEEDED) StatusNegative else DarkSurface
                                    )
                                    IconButton(
                                        onClick = { budgetToDelete = item },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Hapus Anggaran",
                                            tint = TextSecondary.copy(alpha = 0.5f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Row 2: Progress Bar
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = progressColor,
                                trackColor = PebbleSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Row 3: Detail Terpakai & Batas Plafon
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isBalanceHidden) "Terpakai: ••••••" else "Terpakai: " + Formatters.formatRupiah(item.spentAmount),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 9.5.sp
                                    ),
                                    color = TextSecondary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = if (isBalanceHidden) "Plafon: ••••••" else "Plafon: " + Formatters.formatRupiah(item.budget.targetAmount),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 9.5.sp
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

            // Compact overflow indicator / toggle when more than 2 budgets exist
            if (budgets.size > 2) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        TextButton(
                            onClick = { showAllBudgets = !showAllBudgets },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (showAllBudgets) "TAMPILKAN 2 ANGGARAN SAJA" else "+ LIHAT SEMUA (${budgets.size}) ANGGARAN",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    fontSize = 11.sp
                                ),
                                color = JadePrimary
                            )
                        }
                    }
                }
            }
        }

        // 6. Section: Transaksi Terbaru
        item {
            Spacer(modifier = Modifier.height(28.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TRANSAKSI TERBARU",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    ),
                    color = DarkSurface
                )
                // Accessible Touch Target for "LIHAT SEMUA"
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .clickable(role = Role.Button) { onNavigateToTransactions() }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "LIHAT SEMUA",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = JadePrimary
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = JadePrimary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (recentTransactions.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "BELUM ADA TRANSAKSI",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        ),
                        color = DarkSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Mulai catat transaksi pertama Anda dengan suara atau formulir manual.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    DuitAingButton(
                        text = "RECORD CASHFLOW",
                        onClick = { viewModel.isRecordVoiceOpen.value = true },
                        leadingIcon = Icons.Rounded.Mic,
                        trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                        variant = DuitAingButtonVariant.PRIMARY,
                        height = 48.dp,
                        shadowOffset = 2.dp
                    )
                }
            }
        } else {
            items(recentTransactions, key = { it.id }) { tx ->
                TransactionItemCard(
                    transaction = tx,
                    category = catMap[tx.categoryId],
                    sourceAsset = assetMap[tx.assetId],
                    destinationAsset = tx.destinationAssetId?.let { assetMap[it] },
                    onClick = {
                        viewModel.selectedTransactionForDetail.value = tx
                    }
                )
            }
        }
    }

    PeriodSelectionSheet(
        visible = showPeriodSheet,
        selectedPeriod = selectedPeriod,
        onPeriodSelected = {
            viewModel.setPeriodFilter(it)
            showPeriodSheet = false
        },
        onDismissRequest = { showPeriodSheet = false }
    )

    BudgetFormDialog(
        visible = showBudgetDialog,
        editingBudget = editingBudget,
        expenseCategories = expenseCategories,
        onSave = { newBudget ->
            viewModel.saveBudget(newBudget) {
                showBudgetDialog = false
            }
        },
        onUpdate = { updatedBudget ->
            viewModel.updateBudget(updatedBudget) {
                showBudgetDialog = false
            }
        },
        onDismiss = { showBudgetDialog = false }
    )

    val catNameToDelete = budgetToDelete?.category?.name ?: "ini"
    DuitAingConfirmDialog(
        isOpen = budgetToDelete != null,
        title = "HAPUS ANGGARAN?",
        message = "Apakah Anda yakin ingin menghapus batas anggaran kategori \"$catNameToDelete\"? Catatan pengeluaran Anda tetap aman.",
        confirmText = "HAPUS",
        confirmVariant = DuitAingButtonVariant.DANGER,
        onConfirm = {
            budgetToDelete?.let { viewModel.deleteBudget(it.budget.id) }
            budgetToDelete = null
        },
        onDismiss = { budgetToDelete = null }
    )
}
