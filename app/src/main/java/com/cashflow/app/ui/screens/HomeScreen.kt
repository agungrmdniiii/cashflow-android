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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.cashflow.app.data.model.FavoriteTemplate
import com.cashflow.app.data.model.Transaction
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.cashflow.app.data.service.FinancialInsight
import com.cashflow.app.data.service.UpcomingRecurringBill
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.components.*
import com.cashflow.app.ui.theme.*

/**
 * Editorial Financial Dashboard for "Duit Aing"
 * Open layout without excessive card containers.
 * Strong typographic hierarchy, generous whitespace, and focused signature actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val favoriteTemplates by viewModel.favoriteTemplates.collectAsState()
    val isFavoriteDirectSave by viewModel.isFavoriteDirectSave.collectAsState()
    val upcomingBills by viewModel.upcomingRecurringBills.collectAsState()
    val ruleBasedInsights by viewModel.ruleBasedInsights.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshUpcomingBills()
        viewModel.processRecurringBills()
    }

    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

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
    val displayedBudgets = remember(budgets) {
        budgets.take(2)
    }
    val isBalanceHidden by viewModel.isBalanceHidden.collectAsState()

    val monthDeltaPct = remember(transactions) {
        val now = java.time.YearMonth.now()
        val lastMonth = now.minusMonths(1)
        val curMonthStr = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))
        val lastMonthStr = lastMonth.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))

        val curExp = transactions.filter { it.type == "expense" && it.transactionDate.startsWith(curMonthStr) }.sumOf { it.amount }
        val lastExp = transactions.filter { it.type == "expense" && it.transactionDate.startsWith(lastMonthStr) }.sumOf { it.amount }

        if (lastExp > 0L) {
            val pct = ((curExp - lastExp).toDouble() / lastExp * 100).toInt()
            pct
        } else {
            null
        }
    }

    val greeting = remember {
        val hour = java.time.LocalTime.now().hour
        when (hour) {
            in 5..10 -> "Selamat pagi"
            in 11..14 -> "Selamat siang"
            in 15..18 -> "Selamat sore"
            else -> "Selamat malam"
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            coroutineScope.launch {
                viewModel.recalculateBalances()
                kotlinx.coroutines.delay(400)
                isRefreshing = false
            }
        },
        modifier = modifier
            .fillMaxSize()
            .background(PebbleBackground)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
        // 1. Header: Sapaan, Subteks, Ikon Pencarian & Pengaturan
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = DarkSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Kelola keuanganmu hari ini",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp
                        ),
                        color = TextSecondary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Search button
                    IconButton(
                        onClick = onNavigateToTransactions,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(PebbleSurfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Cari transaksi",
                            tint = DarkSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Settings button
                    IconButton(
                        onClick = { viewModel.isSettingsOpen.value = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(PebbleSurfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Pengaturan",
                            tint = DarkSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 2. Hero Card Hijau Tua "Total Kekayaan"
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                DarkDoubleBezelCard(
                    modifier = Modifier.fillMaxWidth(),
                    outerRadius = 24.dp,
                    innerRadius = 22.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp)
                    ) {
                        // Top row: Wallet badge & Title on Left, Eye Toggle Button on Right
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(FintechAccentMint.copy(alpha = 0.22f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = FintechAccentMint,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Text(
                                    text = "Total Kekayaan",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    ),
                                    color = FintechAccentMint
                                )
                            }

                            val eyeRotation by animateFloatAsState(
                                targetValue = if (isBalanceHidden) 180f else 0f,
                                animationSpec = tween(durationMillis = 240, easing = DuitAingMotion.SubtleEasing),
                                label = "EyeRotation"
                            )

                            // Prominent tactile toggle on top-right corner
                            Box(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.10f))
                                    .clickable(role = Role.Button) { viewModel.toggleBalanceVisibility() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isBalanceHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (isBalanceHidden) "Tampilkan saldo" else "Sembunyikan saldo",
                                    tint = FintechAccentMint,
                                    modifier = Modifier
                                        .size(17.dp)
                                        .graphicsLayer { rotationZ = eyeRotation }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Main Balance Row
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
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 32.sp,
                                        letterSpacing = 2.sp
                                    ),
                                    color = TextOnDark,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    AnimatedNumberText(
                                        value = totalWealth,
                                        style = MaterialTheme.typography.displayLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 35.sp,
                                            letterSpacing = (-1.0).sp
                                        ),
                                        color = TextOnDark,
                                        dimThousands = true,
                                        maxLines = 1,
                                        softWrap = false
                                    )

                                    if (monthDeltaPct != null) {
                                        val isLower = monthDeltaPct <= 0
                                        val deltaText = if (isLower) "${monthDeltaPct}%" else "+${monthDeltaPct}%"
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(if (isLower) FintechAccentMint.copy(alpha = 0.22f) else Color(0xFFFF7E67).copy(alpha = 0.22f))
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isLower) Icons.Rounded.TrendingDown else Icons.Rounded.TrendingUp,
                                                    contentDescription = null,
                                                    tint = if (isLower) FintechAccentMint else Color(0xFFFF8E8E),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = deltaText,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp
                                                    ),
                                                    color = if (isLower) FintechAccentMint else Color(0xFFFF8E8E)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Bottom Row: Asset/Debt breakdown on Left, "Lihat Aset →" on Right
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isBalanceHidden) {
                                Text(
                                    text = "${activeAssets.size} Akun Aktif",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp
                                    ),
                                    color = FintechAccentMint.copy(alpha = 0.82f)
                                )
                            } else if (totalRemainingDebt > 0L) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Bersih: ${Formatters.formatRupiah(netWorth)}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.sp
                                        ),
                                        color = FintechAccentMint.copy(alpha = 0.85f)
                                    )
                                    Text(
                                        text = "·",
                                        color = FintechAccentMint.copy(alpha = 0.5f),
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Hutang: ${Formatters.formatRupiah(totalRemainingDebt)}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.sp
                                        ),
                                        color = Color(0xFFFF8E8E)
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(FintechAccentMint)
                                    )
                                    Text(
                                        text = "${activeAssets.size} Akun Aktif · Bebas Hutang",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.sp
                                        ),
                                        color = FintechAccentMint.copy(alpha = 0.85f)
                                    )
                                }
                            }

                            // Shortcut to Assets Screen
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable(role = Role.Button) { onNavigateToAssets() }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Lihat Aset",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.5.sp
                                    ),
                                    color = FintechAccentMint.copy(alpha = 0.9f)
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    tint = FintechAccentMint.copy(alpha = 0.9f),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Quick Actions (4 Tombol: Pengeluaran, Pemasukan, Transfer, Suara)
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HomeQuickActionButton(
                    icon = Icons.Rounded.TrendingDown,
                    label = "Pengeluaran",
                    onClick = { viewModel.openAddTransaction(TransactionType.EXPENSE) },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                HomeQuickActionButton(
                    icon = Icons.Rounded.TrendingUp,
                    label = "Pemasukan",
                    onClick = { viewModel.openAddTransaction(TransactionType.INCOME) },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                HomeQuickActionButton(
                    icon = Icons.Rounded.SwapHoriz,
                    label = "Transfer",
                    onClick = { viewModel.openAddTransaction(TransactionType.TRANSFER) },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                HomeQuickActionButton(
                    icon = Icons.Rounded.Mic,
                    label = "Suara",
                    onClick = { viewModel.isRecordVoiceOpen.value = true },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // 3B. Transaksi Favorit (3-5 Tombol Cepat)
        if (favoriteTemplates.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Transaksi Favorit",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = DarkSurface
                        )
                        Text(
                            text = if (isFavoriteDirectSave) "1-Tap Simpan" else "Isi Form",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    FadingHorizontalScrollRow(
                        fadeColor = PebbleBackground,
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        favoriteTemplates.take(5).forEach { fav ->
                            OutlinedCard(
                                onClick = {
                                    if (isFavoriteDirectSave) {
                                        val newTx = Transaction(
                                            id = "tx_${UUID.randomUUID()}",
                                            type = fav.type,
                                            amount = fav.amount,
                                            description = fav.name,
                                            categoryId = fav.categoryId,
                                            assetId = fav.assetId,
                                            destinationAssetId = fav.destinationAssetId,
                                            transactionDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                                            transactionTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                                            note = fav.note,
                                            inputMethod = "manual"
                                        )
                                        viewModel.saveTransaction(newTx)
                                    } else {
                                        viewModel.editingTransaction.value = Transaction(
                                            id = "tx_${UUID.randomUUID()}",
                                            type = fav.type,
                                            amount = fav.amount,
                                            description = fav.name,
                                            categoryId = fav.categoryId,
                                            assetId = fav.assetId,
                                            destinationAssetId = fav.destinationAssetId,
                                            transactionDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                                            transactionTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                                            note = fav.note,
                                            inputMethod = "manual"
                                        )
                                        viewModel.isAddTransactionOpen.value = true
                                    }
                                },
                                shape = RoundedCornerShape(999.dp),
                                colors = CardDefaults.outlinedCardColors(containerColor = PebbleSurface),
                                border = BorderStroke(1.dp, PebbleBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFE89A3C),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = fav.name,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        ),
                                        color = DarkSurface
                                    )
                                    Text(
                                        text = "•",
                                        color = TextSecondary,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = Formatters.formatCompact(fav.amount),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.5.sp
                                        ),
                                        color = JadePrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3C. Financial Insight Banner (Local Rule-Based)
        if (ruleBasedInsights.isNotEmpty()) {
            item {
                val topInsight = ruleBasedInsights.first()
                val isDark = LocalIsDarkTheme.current
                val badgeBg = when (topInsight.type.lowercase()) {
                    "positive" -> if (isDark) Color(0xFF1E382B) else Color(0xFFE8F5E9)
                    "warning" -> if (isDark) Color(0xFF3E2D17) else Color(0xFFFFF3E0)
                    else -> if (isDark) Color(0xFF1D2B36) else Color(0xFFE1F5FE)
                }
                val badgeTint = when (topInsight.type.lowercase()) {
                    "positive" -> if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                    "warning" -> if (isDark) Color(0xFFFFB74D) else Color(0xFFEF6C00)
                    else -> if (isDark) Color(0xFF4FC3F7) else Color(0xFF0277BD)
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = PebbleSurface),
                    border = BorderStroke(1.dp, PebbleBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(badgeBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (topInsight.type.lowercase()) {
                                    "positive" -> Icons.Rounded.ThumbUp
                                    "warning" -> Icons.Rounded.WarningAmber
                                    else -> Icons.Rounded.Lightbulb
                                },
                                contentDescription = null,
                                tint = badgeTint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "INSIGHT KEUANGAN",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp,
                                    fontSize = 10.sp
                                ),
                                color = badgeTint
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = topInsight.title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                ),
                                color = DarkSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = topInsight.message,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                ),
                                color = TextSecondary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // 3D. Upcoming Recurring Bills Card (7 Hari ke Depan)
        if (upcomingBills.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = PebbleSurface),
                    border = BorderStroke(1.dp, PebbleBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(JadePrimary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.EventRepeat,
                                        contentDescription = null,
                                        tint = JadePrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = "Akan Datang (7 Hari)",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    ),
                                    color = DarkSurface
                                )
                            }
                            Text(
                                text = "${upcomingBills.size} tagihan",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                ),
                                color = TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        upcomingBills.take(3).forEachIndexed { idx, bill ->
                            val dueLabel = when (bill.daysRemaining) {
                                0 -> "Hari ini"
                                1 -> "Besok"
                                else -> "${bill.daysRemaining} hari lagi"
                            }
                            val dueBadgeColor = if (bill.daysRemaining <= 1) StatusNegative else JadePrimary

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = bill.recurring.name,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp
                                        ),
                                        color = DarkSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = dueLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.5.sp
                                            ),
                                            color = dueBadgeColor
                                        )
                                        Text(text = "•", color = TextSecondary, fontSize = 10.sp)
                                        Text(
                                            text = Formatters.formatRupiah(bill.recurring.amount),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp
                                            ),
                                            color = DarkSurface
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Lewati button
                                    OutlinedButton(
                                        onClick = { viewModel.skipUpcomingBill(bill.recurring.id, bill.dueDate) },
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, PebbleBorder)
                                    ) {
                                        Text(
                                            text = "Lewati",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = TextSecondary
                                        )
                                    }

                                    // Catat Sekarang button
                                    Button(
                                        onClick = {
                                            val newTx = Transaction(
                                                id = "tx_${UUID.randomUUID()}",
                                                type = bill.recurring.type,
                                                amount = bill.recurring.amount,
                                                description = bill.recurring.name,
                                                categoryId = bill.recurring.categoryId,
                                                assetId = bill.recurring.assetId,
                                                destinationAssetId = bill.recurring.destinationAssetId,
                                                transactionDate = bill.dueDate,
                                                transactionTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                                                note = "Pencatatan tagihan berulang",
                                                inputMethod = "manual"
                                            )
                                            viewModel.saveTransaction(newTx)
                                            viewModel.skipUpcomingBill(bill.recurring.id, bill.dueDate)
                                        },
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = JadePrimary)
                                    ) {
                                        Text(
                                            text = "Catat",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            if (idx < upcomingBills.take(3).size - 1) {
                                HorizontalDivider(color = PebbleBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.isRecurringScreenOpen.value = true }
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Kelola Semua Transaksi Berulang →",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5.sp
                                ),
                                color = JadePrimary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // 4. Section: Transaksi Terbaru
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Transaksi Terbaru",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = DarkSurface
                )
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 44.dp)
                        .clickable(role = Role.Button) { onNavigateToTransactions() }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Lihat semua",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            ),
                            color = TextSecondary
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (recentTransactions.isEmpty()) {
            item {
                AnimatedEmptyState(
                    icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                    title = "Belum Ada Transaksi",
                    description = "Mulai catat transaksi pertama Anda dengan mudah.",
                    actionText = "Tambah Transaksi",
                    actionIcon = Icons.Rounded.Add,
                    onActionClick = { viewModel.openAddTransaction() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        } else {
            items(recentTransactions, key = { it.id }) { tx ->
                TransactionItemCard(
                    transaction = tx,
                    category = catMap[tx.categoryId],
                    sourceAsset = assetMap[tx.assetId],
                    destinationAsset = tx.destinationAssetId?.let { assetMap[it] },
                    isBalanceHidden = isBalanceHidden,
                    onClick = {
                        viewModel.selectedTransactionForDetail.value = tx
                    },
                    onEdit = {
                        viewModel.editingTransaction.value = tx
                        viewModel.isAddTransactionOpen.value = true
                    },
                    onDelete = {
                        viewModel.deleteTransactionWithUndo(tx)
                    },
                    onSaveAsFavorite = {
                        val template = FavoriteTemplate(
                            id = UUID.randomUUID().toString(),
                            name = tx.description,
                            type = tx.type,
                            amount = tx.amount,
                            categoryId = tx.categoryId,
                            assetId = tx.assetId,
                            destinationAssetId = tx.destinationAssetId,
                            note = tx.note
                        )
                        viewModel.saveFavoriteTemplate(template)
                    }
                )
            }
        }

        // 5. Kartu Ringkasan Arus Kas Bulan Ini dengan Mini Bar Chart
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                DoubleBezelCard(
                    modifier = Modifier.fillMaxWidth(),
                    innerRadius = 22.dp,
                    innerColor = PebbleSurface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        // Header row with Period Pill
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Arus Kas",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                ),
                                color = DarkSurface
                            )

                            // Period selector pill
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 34.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(PebbleSurfaceVariant)
                                    .border(1.dp, PebbleBorder, RoundedCornerShape(999.dp))
                                    .clickable(role = Role.Button) { showPeriodSheet = true }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
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
                                        text = selectedPeriod.displayName,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        color = DarkSurface
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowDropDown,
                                        contentDescription = "Pilih periode",
                                        tint = DarkSurface,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Large net cashflow
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
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (netCashflow >= 0) FintechSecondary else StatusNegative
                                )
                            } else {
                                val netPrefix = if (netCashflow > 0) "+" else ""
                                AnimatedNumberText(
                                    value = netCashflow,
                                    prefix = netPrefix,
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 24.sp,
                                        letterSpacing = (-0.5).sp
                                    ),
                                    color = if (netCashflow >= 0) FintechSecondary else StatusNegative
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Mini daily activity bar chart
                        MiniDailyBarChart(
                            transactions = transactions,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = PebbleBorder, thickness = 0.8.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // 2 Columns: Pemasukan vs Pengeluaran
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(StatusPositive)
                                    )
                                    Text(
                                        text = "Pemasukan",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = TextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isBalanceHidden) "+••••••" else "+" + Formatters.formatRupiah(periodIncome),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    ),
                                    color = StatusPositive
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .height(28.dp)
                                    .width(1.dp)
                                    .background(PebbleBorder)
                            )

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
                                        text = "Pengeluaran",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = TextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isBalanceHidden) "−••••••" else "−" + Formatters.formatRupiah(periodExpense),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    ),
                                    color = StatusNegative
                                )
                            }
                        }
                    }
                }
            }
        }

        // 7. Section: Anggaran Belanja (Thick Progress Bar + 3-Dot Overflow Menu)
        item {
            Spacer(modifier = Modifier.height(22.dp))
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
                        .clickable(role = Role.Button) { viewModel.openBudgetsScreen() }
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "Anggaran Belanja",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = DarkSurface
                    )
                    if (budgets.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(${budgets.size})",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            ),
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "Buka Anggaran",
                        tint = TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }

                // Add Budget Button
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .defaultMinSize(minHeight = 36.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(PebbleSurfaceVariant)
                        .border(1.dp, PebbleBorder, RoundedCornerShape(999.dp))
                        .clickable(role = Role.Button) {
                            editingBudget = null
                            showBudgetDialog = true
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Tambah Anggaran",
                            tint = FintechPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Tambah",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = FintechPrimary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (budgets.isEmpty()) {
            item {
                AnimatedEmptyState(
                    icon = Icons.Rounded.PieChartOutline,
                    title = "Belum Ada Anggaran",
                    description = "Tetapkan batas belanja disiplin per kategori untuk menjaga cashflow.",
                    actionText = "Buat Anggaran",
                    actionIcon = Icons.Rounded.Add,
                    onActionClick = {
                        editingBudget = null
                        showBudgetDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }
        } else {
            items(displayedBudgets, key = { it.budget.id }) { item ->
                var menuExpanded by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    DoubleBezelCard(
                        modifier = Modifier.fillMaxWidth(),
                        innerRadius = 18.dp,
                        innerColor = PebbleSurface,
                        onClick = {
                            viewModel.openBudgetsScreen()
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            val progressColor = when (item.status) {
                                BudgetStatus.SAFE -> FintechSecondary
                                BudgetStatus.WARNING -> Color(0xFFD97706)
                                BudgetStatus.NEAR_LIMIT -> Color(0xFFEA580C)
                                BudgetStatus.EXCEEDED -> StatusNegative
                            }

                            val animatedProgress by animateFloatAsState(
                                targetValue = item.progressPercentage.coerceIn(0f, 1f),
                                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                                label = "HomeBudgetProgress_${item.budget.id}"
                            )

                            // Row 1: Icon, Category Name, Sisa Plafon & 3-Dot Menu
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
                                                fontSize = 11.sp,
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
                                            fontSize = 12.sp
                                        ),
                                        color = if (item.status == BudgetStatus.EXCEEDED) StatusNegative else DarkSurface
                                    )

                                    // 3-dot overflow menu
                                    Box {
                                        IconButton(
                                            onClick = { menuExpanded = true },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.MoreVert,
                                                contentDescription = "Opsi anggaran",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = menuExpanded,
                                            onDismissRequest = { menuExpanded = false },
                                            modifier = Modifier.background(PebbleSurface)
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Edit Anggaran", color = TextPrimary) },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Rounded.Edit,
                                                        contentDescription = null,
                                                        tint = TextPrimary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                },
                                                onClick = {
                                                    menuExpanded = false
                                                    editingBudget = item.budget
                                                    showBudgetDialog = true
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Hapus", color = StatusNegative) },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Rounded.DeleteOutline,
                                                        contentDescription = null,
                                                        tint = StatusNegative,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                },
                                                onClick = {
                                                    menuExpanded = false
                                                    budgetToDelete = item
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Row 2: Thick Rounded Progress Bar (8.dp)
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(999.dp)),
                                color = progressColor,
                                trackColor = PebbleSurfaceVariant
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
                                        fontSize = 11.sp
                                    ),
                                    color = TextSecondary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = if (isBalanceHidden) "Plafon: ••••••" else "Plafon: " + Formatters.formatRupiah(item.budget.targetAmount),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Normal,
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

            if (budgets.size > 2) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        TextButton(
                            onClick = { viewModel.openBudgetsScreen() },
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
                                    color = FintechSecondary
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    tint = FintechSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
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

@Composable
private fun HomeQuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "QuickActionScale"
    )

    val isDark = LocalIsDarkTheme.current
    val actualBg = if (isDark) FintechActionSquareDark else FintechActionSquareLight
    val actualIconTint = if (isDark) TextPrimaryDark else LightTextPrimary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(actualBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = actualIconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            ),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MiniSparkline(
    modifier: Modifier = Modifier,
    color: Color = FintechAccentLight
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val path = Path().apply {
            moveTo(0f, height * 0.8f)
            cubicTo(width * 0.25f, height * 0.9f, width * 0.4f, height * 0.3f, width * 0.65f, height * 0.45f)
            cubicTo(width * 0.8f, height * 0.55f, width * 0.9f, height * 0.1f, width, height * 0.15f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        )
    }
}

@Composable
private fun MiniDailyBarChart(
    transactions: List<com.cashflow.app.data.model.Transaction>,
    modifier: Modifier = Modifier
) {
    val today = remember { java.time.LocalDate.now() }
    val dayAmounts = remember(transactions) {
        (6 downTo 0).map { daysAgo ->
            val d = today.minusDays(daysAgo.toLong())
            val dStr = d.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
            val dayTxs = transactions.filter { it.transactionDate == dStr }
            val inc = dayTxs.filter { it.type == TransactionType.INCOME.value }.sumOf { it.amount }
            val exp = dayTxs.filter { it.type == TransactionType.EXPENSE.value }.sumOf { it.amount }
            val net = inc - exp
            val total = inc + exp
            val dayName = when (d.dayOfWeek.value) {
                1 -> "Sen"
                2 -> "Sel"
                3 -> "Rab"
                4 -> "Kam"
                5 -> "Jum"
                6 -> "Sab"
                else -> "Min"
            }
            Triple(dayName, net, total)
        }
    }
    val maxTotal = remember(dayAmounts) {
        dayAmounts.maxOfOrNull { it.third }?.coerceAtLeast(10_000L) ?: 10_000L
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        dayAmounts.forEach { (label, net, total) ->
            val barRatio = if (total > 0) (total.toFloat() / maxTotal.toFloat()).coerceIn(0.18f, 1.0f) else 0.12f
            val barColor = when {
                total == 0L -> PebbleBorder
                net >= 0 -> FintechSecondary
                else -> StatusNegative
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .width(14.dp)
                        .fillMaxHeight(barRatio)
                        .clip(RoundedCornerShape(4.dp))
                        .background(barColor)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = TextSecondary
                )
            }
        }
    }
}

