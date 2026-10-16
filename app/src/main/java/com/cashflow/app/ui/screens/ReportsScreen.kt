package com.cashflow.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.data.model.TransactionType
import com.cashflow.app.data.model.PeriodFilter
import com.cashflow.app.data.model.buildCashflowChart
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.components.*
import com.cashflow.app.ui.theme.*

/**
 * Editorial Reports Screen for "Duit Aing"
 * Open layout without excessive card nesting, emphasizing data clarity and insights.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: CashflowViewModel,
    modifier: Modifier = Modifier,
    onTourCalendarPositioned: (Rect) -> Unit = {}
) {
    val periodTransactions by viewModel.reportTransactions.collectAsState()
    val reportRange by viewModel.reportDateRange.collectAsState()
    val periodIncome = remember(periodTransactions) { periodTransactions.filter { it.type == "income" }.sumOf { it.amount } }
    val periodExpense = remember(periodTransactions) { periodTransactions.filter { it.type == "expense" }.sumOf { it.amount } }
    val netCashflow = periodIncome - periodExpense
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val allAssets by viewModel.assets.collectAsState()
    val monthStats by viewModel.monthComparisonStats.collectAsState()
    val geminiApiKey by viewModel.geminiApiKey.collectAsState()

    val catMap = remember(categories) { categories.associateBy { it.id } }
    val assetMap = remember(allAssets) { allAssets.associateBy { it.id } }

    val isBalanceHidden by viewModel.isBalanceHidden.collectAsState()
    val duit = MaterialTheme.duit
    val isDark = LocalIsDarkTheme.current
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    var reportChartType by remember { mutableStateOf("expense") } // "expense" or "income"
    var showPeriodSheet by remember { mutableStateOf(false) }
    var showReportCalendar by remember { mutableStateOf(false) }
    val periodLabel = reportRange?.let { "${Formatters.formatDateReadable(it.start.toString())} – ${Formatters.formatDateReadable(it.end.toString())}" } ?: selectedPeriod.displayName

    var showPayloadDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var tempApiKey by remember { mutableStateOf(geminiApiKey ?: "") }

    // Expense Category breakdown
    val categoryBreakdown = remember(periodTransactions, categories) {
        val expenseTx = periodTransactions.filter { it.type == TransactionType.EXPENSE.value && it.categoryId != null }
        val totalExp = expenseTx.sumOf { it.amount }
        if (totalExp == 0L) emptyList()
        else {
            expenseTx.groupBy { it.categoryId!! }
                .map { (catId, txs) ->
                    val sum = txs.sumOf { it.amount }
                    val cat = catMap[catId]
                    val percent = (sum.toFloat() / totalExp.toFloat()) * 100f
                    Triple(cat, sum, percent)
                }
                .sortedByDescending { it.second }
        }
    }

    // Income Category breakdown
    val incomeCategoryBreakdown = remember(periodTransactions, categories) {
        val incomeTx = periodTransactions.filter { it.type == TransactionType.INCOME.value && it.categoryId != null }
        val totalInc = incomeTx.sumOf { it.amount }
        if (totalInc == 0L) emptyList()
        else {
            incomeTx.groupBy { it.categoryId!! }
                .map { (catId, txs) ->
                    val sum = txs.sumOf { it.amount }
                    val cat = catMap[catId]
                    val percent = (sum.toFloat() / totalInc.toFloat()) * 100f
                    Triple(cat, sum, percent)
                }
                .sortedByDescending { it.second }
        }
    }

    // Asset spending breakdown
    val assetBreakdown = remember(periodTransactions, allAssets) {
        val expenseTx = periodTransactions.filter { it.type == TransactionType.EXPENSE.value }
        val totalExp = expenseTx.sumOf { it.amount }
        if (totalExp == 0L) emptyList()
        else {
            expenseTx.groupBy { it.assetId }
                .map { (assetId, txs) ->
                    val sum = txs.sumOf { it.amount }
                    val ast = assetMap[assetId]
                    val percent = (sum.toFloat() / totalExp.toFloat()) * 100f
                    Triple(ast, sum, percent)
                }
                .sortedByDescending { it.second }
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
            .background(duit.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
        // 1. Screen Header: Left-aligned title + subtitle, Calendar button & Eye toggle on right
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Text(
                        text = "Laporan Keuangan",
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
                        text = periodLabel,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp
                        ),
                        color = TextSecondary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.toggleBalanceVisibility() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(duit.surface)
                            .border(1.dp, duit.border, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isBalanceHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (isBalanceHidden) "Tampilkan saldo" else "Sembunyikan saldo",
                            tint = duit.textPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    IconButton(
                        onClick = { showReportCalendar = true },
                        modifier = Modifier
                            .onGloballyPositioned { onTourCalendarPositioned(it.boundsInRoot()) }
                            .minimumInteractiveComponentSize()
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(duit.surface)
                            .border(1.dp, duit.border, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = "Pilih rentang tanggal laporan",
                            tint = duit.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 2. Segmented Pill Tabs: Pengeluaran | Pemasukan with unified tactile control
        item {
            val selectedReportTab = if (reportChartType == "income") 1 else 0
            val reportTabs = listOf(
                SegmentItem(value = 0, label = "Pengeluaran"),
                SegmentItem(value = 1, label = "Pemasukan")
            )

            DuitAingSegmentedControl(
                items = reportTabs,
                selectedValue = selectedReportTab,
                onValueChange = { index ->
                    reportChartType = if (index == 1) "income" else "expense"
                },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        val isExpense = reportChartType == "expense"
        val currentBreakdown = if (isExpense) categoryBreakdown else incomeCategoryBreakdown
        val currentTotal = if (isExpense) periodExpense else periodIncome

        // 3. Section Title "Pengeluaran Anda" / "Pemasukan Anda" + Month Dropdown Pill
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isExpense) "Pengeluaran Anda" else "Pemasukan Anda",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = duit.textPrimary
                )

                // Month Dropdown Pill (e.g. "Agu 2026 ▾")
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(duit.surface)
                        .border(1.dp, duit.border, RoundedCornerShape(999.dp))
                        .clickable(role = Role.Button) { showPeriodSheet = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (reportRange != null) "Kustom" else selectedPeriod.displayName,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = duit.textPrimary
                        )
                        Icon(
                            imageVector = Icons.Rounded.ArrowDropDown,
                            contentDescription = null,
                            tint = duit.textPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (currentTotal <= 0L || currentBreakdown.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedEmptyState(
                        icon = Icons.Rounded.Assessment,
                        title = if (isExpense) "BELUM ADA PENGELUARAN" else "BELUM ADA PEMASUKAN",
                        description = if (isExpense)
                            "Belum ada catatan pengeluaran pada periode ${periodLabel}. Catat pengeluaran untuk melihat analisis laporan."
                        else
                            "Belum ada catatan pemasukan pada periode ${periodLabel}. Catat pemasukan untuk melihat analisis laporan.",
                        lottieRawRes = com.cashflow.app.R.raw.lottie_no_transactions,
                        actionText = if (isExpense) "Catat Pengeluaran" else "Catat Pemasukan",
                        actionIcon = Icons.Rounded.Add,
                        onActionClick = {
                            viewModel.addTransactionInitialType.value = if (isExpense) TransactionType.EXPENSE else TransactionType.INCOME
                            viewModel.editingTransaction.value = null
                            viewModel.isAddTransactionOpen.value = true
                        }
                    )
                }
            }
        } else {
        // 4. Donut Chart with Center Total and 2-Column Legend
        item {
            DonutExpenseChart(
                breakdownList = currentBreakdown,
                totalExpense = currentTotal,
                isExpense = isExpense,
                isBalanceHidden = isBalanceHidden
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Trend and breakdowns use exactly the same report transaction set.
        item {
            val today = java.time.LocalDate.now()
            val buckets = remember(periodTransactions, selectedPeriod, reportRange, today) {
                buildCashflowChart(periodTransactions, selectedPeriod, today, reportRange)
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text(if (isExpense) "Tren Pengeluaran" else "Tren Pemasukan", style = MaterialTheme.typography.titleLarge, color = duit.textPrimary)
                Text(periodLabel, style = MaterialTheme.typography.bodySmall, color = duit.textSecondary)
                Spacer(Modifier.height(16.dp))
                ReportTrendChart(buckets, isExpense, isBalanceHidden, Modifier.fillMaxWidth())
                Spacer(Modifier.height(20.dp))
            }
        }
        // 4b. Month Comparison & Financial Metrics Card (4D)
        if (monthStats != null && reportRange == null && selectedPeriod == PeriodFilter.BULAN_INI) {
            val stats = monthStats!!
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = duit.surface),
                    border = BorderStroke(1.dp, duit.border)
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
                            Text(
                                text = "METRIK & PERBANDINGAN",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp,
                                    fontSize = 12.sp
                                ),
                                color = duit.textSecondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val expDeltaPct = stats.expensePercentChange
                            val expDeltaLabel = if (expDeltaPct > 0) "+${String.format(java.util.Locale.US, "%.1f", expDeltaPct)}%"
                                               else "${String.format(java.util.Locale.US, "%.1f", expDeltaPct)}%"
                            val expBadgeColor = if (expDeltaPct > 0) duit.expense else duit.income
                            val expBadgeBg = expBadgeColor.copy(alpha = 0.14f)

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(expBadgeBg)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "vs Bulan lalu $expDeltaLabel",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    color = expBadgeColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "RATA-RATA HARIAN",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    color = duit.textSecondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isBalanceHidden) "••••••" else Formatters.displayRupiah(stats.averageDailyExpense),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.5.sp
                                    ),
                                    color = duit.textPrimary
                                )
                            }

                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "SAVINGS RATE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    color = duit.textSecondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val srPct = (stats.savingsRate * 100).toInt()
                                Text(
                                    text = "$srPct%",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.5.sp
                                    ),
                                    color = if (srPct >= 20) duit.income else if (srPct > 0) duit.textPrimary else duit.expense
                                )
                            }
                        }

                        if (stats.highestExpenseDay != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = duit.border, thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Puncak Belanja: ${Formatters.formatDateReadable(stats.highestExpenseDay!!)}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = duit.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isBalanceHidden) "••••••" else Formatters.displayRupiah(stats.highestExpenseDayAmount),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    color = duit.expense,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        // 4c. Gemini AI Financial Insight Card (4D)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = duit.surface),
                border = BorderStroke(1.dp, duit.border)
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(duit.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = duit.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "Konsultasi Finansial Bersama Vina",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                ),
                                color = duit.textPrimary
                            )
                        }

                        // Lihat data yang dikirim button (Transparansi Privasi)
                        TextButton(
                            onClick = { showPayloadDialog = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Icon(imageVector = Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(14.dp), tint = duit.textSecondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Data Dikirim",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = duit.textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tanyakan apa saja seputar arus kas, tren bulanan, evaluasi anggaran, atau simulasi pembelian kepada Vina, asisten finansial pribadi Anda.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 15.sp
                        ),
                        color = duit.textSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.openChat() },
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = duit.primary,
                                contentColor = duit.onPrimary
                            )
                        ) {
                            Icon(imageVector = Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "TANYA VINA SEKARANG",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                tempApiKey = geminiApiKey ?: ""
                                showApiKeyDialog = true
                            },
                            modifier = Modifier.height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, duit.border)
                        ) {
                            Icon(imageVector = Icons.Rounded.Key, contentDescription = "API Key", modifier = Modifier.size(16.dp), tint = duit.textPrimary)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 4d. Top Spending Ranking (01, 02, 03)
        if (categoryBreakdown.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PENGELUARAN TERBESAR",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            ),
                            color = duit.textPrimary
                        )
                        EyebrowTag(
                            text = "TOP 3",
                            variant = EyebrowVariant.NEGATIVE
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    val top3 = categoryBreakdown.take(3)
                    DoubleBezelCard(
                        modifier = Modifier.fillMaxWidth(),
                        outerPadding = 4.dp,
                        outerRadius = 14.dp,
                        innerRadius = 10.dp,
                        innerColor = duit.surface
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            top3.forEachIndexed { idx, (cat, amount, pct) ->
                                val rankNumber = String.format(java.util.Locale.US, "%02d", idx + 1)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = rankNumber,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            color = if (idx == 0) duit.expense else TextSecondary
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        CategoryIcon(
                                            iconName = cat?.icon ?: "category",
                                            size = 28.dp,
                                            iconSize = 14.dp,
                                            backgroundColor = duit.surfaceVariant,
                                            tintColor = duit.textPrimary
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = cat?.name ?: "Lainnya",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = duit.textPrimary
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "−" + Formatters.displayRupiah(amount),
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                letterSpacing = (-0.3).sp
                                            ),
                                            color = duit.expense
                                        )
                                        Text(
                                            text = "${String.format(java.util.Locale.US, "%.1f", pct)}%",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp
                                            ),
                                            color = TextSecondary
                                        )
                                    }
                                }
                                if (idx < top3.size - 1) {
                                    HorizontalDivider(color = duit.border, thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }
        }


        // Section: Breakdown Kategori (only shown when categories exist for the period to prevent stacked empty blocks)
        val isExpenseMode = reportChartType == "expense"
        val activeBreakdown = if (isExpenseMode) categoryBreakdown else incomeCategoryBreakdown

        if (activeBreakdown.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isExpenseMode) "PENGELUARAN PER KATEGORI" else "PEMASUKAN PER KATEGORI",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        ),
                        color = duit.textPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    EyebrowTag(
                        text = "${activeBreakdown.size} KATEGORI",
                        variant = if (isExpenseMode) EyebrowVariant.NEGATIVE else EyebrowVariant.PRIMARY
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            itemsIndexed(activeBreakdown) { idx, (cat, amount, percent) ->
                val chartColors = duit.chart
                val sliceColor = if (cat != null) getCategoryColor(cat.icon, isDark) else if (idx < 6 && idx < chartColors.size) chartColors[idx] else duit.chartOther

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(sliceColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            CategoryIcon(
                                iconName = cat?.icon ?: "category",
                                size = 36.dp,
                                iconSize = 18.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = cat?.name ?: "Lainnya",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                color = duit.textPrimary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            val sign = if (isExpenseMode) "−" else "+"
                            val amountColor = if (isExpenseMode) duit.expense else duit.income
                            Text(
                                text = if (isBalanceHidden) "$sign••••••" else sign + Formatters.displayRupiah(amount),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.3).sp
                                ),
                                color = amountColor
                            )
                            Text(
                                text = "${String.format(java.util.Locale.US, "%.1f", percent)}%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = duit.textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(2.5.dp)),
                        color = sliceColor,
                        trackColor = duit.track
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = duit.border, thickness = 0.5.dp)
                }
            }
        }

        // Section: Breakdown Aset (tampil hanya bila ada data penggunaan aset)
        if (assetBreakdown.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PENGELUARAN PER ASET",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        ),
                        color = duit.textPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    EyebrowTag(
                        text = "${assetBreakdown.size} SUMBER",
                        variant = EyebrowVariant.NEGATIVE
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(
                items = assetBreakdown,
                key = { it.first?.id ?: "unknown_${it.second}" }
            ) { (ast, amount, percent) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (ast != null) (if (ast.isActive) ast.name else "${ast.name} (Arsip)") else "Aset",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            color = duit.textPrimary
                        )

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (isBalanceHidden) "−••••••" else "−" + Formatters.displayRupiah(amount),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.3).sp
                                ),
                                color = duit.expense
                            )
                            Text(
                                text = "${String.format(java.util.Locale.US, "%.1f", percent)}%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(2.5.dp)),
                        color = duit.expense,
                        trackColor = duit.track
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = duit.border, thickness = 0.8.dp)
                }
            }
        }
        }
    }
    }

    PeriodSelectionSheet(
        visible = showPeriodSheet,
        selectedPeriod = selectedPeriod.takeIf { reportRange == null },
        onPeriodSelected = {
            viewModel.setReportPeriod(it)
            showPeriodSheet = false
        },
        onDismissRequest = { showPeriodSheet = false }
    )

    if (showReportCalendar) {
        ReportDateRangeDialog(initialRange = reportRange, onDismiss = { showReportCalendar = false }, onApply = {
            viewModel.reportDateRange.value = it
            showReportCalendar = false
        })
    }
    // Transparansi Privasi: Data yang Dikirim ke Gemini Dialog
    if (showPayloadDialog) {
        val payloadJson = remember {
            try {
                viewModel.getGeminiPayloadPreview().toString(2)
            } catch (e: Exception) {
                "{ \"error\": \"${e.message}\" }"
            }
        }
        AlertDialog(
            onDismissRequest = { showPayloadDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Rounded.Security, contentDescription = null, tint = duit.textPrimary)
                    Text("Transparansi Data", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = duit.textPrimary)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Berikut adalah data JSON persis yang dikirim ke Gemini AI. Perhatikan bahwa hanya total angka kategori dan tanggal bulan yang dikirim. Tidak ada nama merchant, lokasi, atau catatan pribadi.",
                        style = MaterialTheme.typography.bodySmall.copy(color = duit.textSecondary, fontSize = 12.sp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(duit.surfaceVariant)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = payloadJson,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            color = duit.textPrimary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPayloadDialog = false }) {
                    Text("TUTUP", fontWeight = FontWeight.Bold, color = duit.textPrimary)
                }
            },
            containerColor = duit.surface
        )
    }

    // Set Gemini API Key Dialog
    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = {
                Text("Gemini 3.8 Flash API Key", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = duit.textPrimary)
            },
            text = {
                Column {
                    Text(
                        text = "Gunakan model Gemini 3.8 Flash gratis dari Google AI Studio (aistudio.google.com). Pilih Free Tier tanpa biaya/kartu kredit. Kunci disimpan terenkripsi di HP Anda.\n\nJika dikosongkan, aplikasi otomatis menggunakan Penasihat Finansial Cerdas lokal secara offline dan 100% gratis.",
                        style = MaterialTheme.typography.bodySmall.copy(color = duit.textSecondary, fontSize = 12.sp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempApiKey,
                        onValueChange = { tempApiKey = it },
                        label = { Text("API Key (Opsional - Free Tier)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setGeminiApiKey(tempApiKey.trim())
                        showApiKeyDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = duit.primary,
                        contentColor = duit.onPrimary
                    )
                ) {
                    Text("SIMPAN", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("BATAL", color = duit.textSecondary)
                }
            },
            containerColor = duit.surface
        )
    }
}
