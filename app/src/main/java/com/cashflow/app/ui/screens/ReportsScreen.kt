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
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.data.model.TransactionType
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
    modifier: Modifier = Modifier
) {
    val periodTransactions by viewModel.periodTransactions.collectAsState()
    val periodIncome by viewModel.periodIncome.collectAsState()
    val periodExpense by viewModel.periodExpense.collectAsState()
    val netCashflow by viewModel.netCashflow.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val assets by viewModel.activeAssets.collectAsState()
    val monthStats by viewModel.monthComparisonStats.collectAsState()
    val geminiApiKey by viewModel.geminiApiKey.collectAsState()

    val catMap = remember(categories) { categories.associateBy { it.id } }
    val assetMap = remember(assets) { assets.associateBy { it.id } }

    val isBalanceHidden by viewModel.isBalanceHidden.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    var reportChartType by remember { mutableStateOf("expense") } // "expense" or "income"
    var showPeriodSheet by remember { mutableStateOf(false) }

    var aiInsightText by remember { mutableStateOf<String?>(null) }
    var isLoadingAi by remember { mutableStateOf(false) }
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
    val assetBreakdown = remember(periodTransactions, assets) {
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
            .background(PebbleBackground)
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
                        color = DarkSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Analisis arus kas & perputaran dana",
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
                            .background(PebbleSurface)
                            .border(1.dp, PebbleBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isBalanceHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (isBalanceHidden) "Tampilkan saldo" else "Sembunyikan saldo",
                            tint = DarkSurface,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    IconButton(
                        onClick = { showPeriodSheet = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(PebbleSurface)
                            .border(1.dp, PebbleBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = "Pilih Periode",
                            tint = DarkSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 2. Segmented Pill Tabs: Pengeluaran | Pemasukan | Tabungan | Target with unified tactile control
        item {
            var selectedReportTab by remember { mutableStateOf(0) }
            val reportTabs = listOf(
                SegmentItem(value = 0, label = "Pengeluaran"),
                SegmentItem(value = 1, label = "Pemasukan")
            )

            DuitAingSegmentedControl(
                items = reportTabs,
                selectedValue = selectedReportTab,
                onValueChange = { index ->
                    selectedReportTab = index
                    reportChartType = if (index == 1) "income" else "expense"
                },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        // 3. Section Title "Pengeluaran Anda" + Month Dropdown Pill
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (reportChartType == "expense") "Pengeluaran Anda" else "Pemasukan Anda",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = DarkSurface
                )

                // Month Dropdown Pill (e.g. "Agu 2026 ▾")
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(PebbleSurface)
                        .border(1.dp, PebbleBorder, RoundedCornerShape(999.dp))
                        .clickable(role = Role.Button) { showPeriodSheet = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = selectedPeriod.displayName,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = DarkSurface
                        )
                        Icon(
                            imageVector = Icons.Rounded.ArrowDropDown,
                            contentDescription = null,
                            tint = DarkSurface,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 4. Donut Chart with Center Total and 2-Column Legend
        item {
            val isExpense = reportChartType == "expense"
            val currentBreakdown = if (isExpense) categoryBreakdown else incomeCategoryBreakdown
            val currentTotal = if (isExpense) periodExpense else periodIncome

            DonutExpenseChart(
                breakdownList = currentBreakdown,
                totalExpense = currentTotal,
                isExpense = isExpense,
                isBalanceHidden = isBalanceHidden
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // 5. Tren Pengeluaran (Spending Trend) with 6-Month Bar Chart & Floating Tooltip
        item {
            val allTransactions by viewModel.transactions.collectAsState()
            val isDark = LocalIsDarkTheme.current
            val monthTrend = remember(allTransactions) {
                val now = java.time.YearMonth.now()
                (5 downTo 0).map { offset ->
                    val ym = now.minusMonths(offset.toLong())
                    val ymStr = ym.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))
                    val exp = allTransactions
                        .filter { it.type == "expense" && it.transactionDate.startsWith(ymStr) }
                        .sumOf { it.amount }
                    Triple(ym, ym.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale("id")), exp)
                }
            }
            val maxExp = remember(monthTrend) { monthTrend.maxOfOrNull { it.third }?.coerceAtLeast(100_000L) ?: 100_000L }
            var selectedMonthIdx by remember { mutableStateOf<Int?>(5) } // Default to current month

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    text = "Tren Pengeluaran",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = DarkSurface
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Chart row with Y-axis markers on right (Matching Reference!)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // 6 Bars Area
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        monthTrend.forEachIndexed { idx, (_, monthName, exp) ->
                            val isCurrent = idx == 5
                            val isSelected = selectedMonthIdx == idx
                            val barHeightFraction = (exp.toFloat() / maxExp.toFloat()).coerceIn(0.12f, 1f)

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        role = Role.Tab
                                    ) { selectedMonthIdx = idx }
                            ) {
                                // Floating Tooltip Pill for Active / Selected bar (Matching Reference!)
                                if (isSelected || isCurrent) {
                                    Box(
                                        modifier = Modifier
                                            .wrapContentWidth(unbounded = true)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isDark) FintechAccentLight else FintechPrimary)
                                            .padding(horizontal = 7.dp, vertical = 3.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isBalanceHidden) "Rp ••••••" else if (exp > 0) Formatters.formatRupiah(exp) else "Rp 0",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.5.sp
                                            ),
                                            color = if (isDark) PebbleBackgroundDark else Color.White,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                }

                                // Rounded Bar or Thin Zero Line
                                if (exp <= 0L) {
                                    Box(
                                        modifier = Modifier
                                            .width(18.dp)
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(1.5.dp))
                                            .background(if (isDark) FintechActionSquareDark else FintechBarMutedLight)
                                    )
                                } else {
                                    val barHeightFraction = (exp.toFloat() / maxExp.toFloat()).coerceIn(0.08f, 1f)
                                    Box(
                                        modifier = Modifier
                                            .width(26.dp)
                                            .fillMaxHeight(barHeightFraction * 0.70f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                when {
                                                    isSelected || isCurrent -> if (isDark) FintechAccentLight else FintechPrimary
                                                    else -> if (isDark) FintechActionSquareDark else FintechBarMutedLight
                                                }
                                            )
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = monthName,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected || isCurrent) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected || isCurrent) DarkSurface else TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Y-axis Markers on right (Indonesian abbreviated scale: rb/jt)
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End
                    ) {
                        val formatScale: (Long) -> String = { v ->
                            when {
                                v >= 1_000_000L -> {
                                    val jt = v.toDouble() / 1_000_000.0
                                    if (jt % 1.0 == 0.0) "${jt.toInt()}jt" else String.format(java.util.Locale.US, "%.1fjt", jt)
                                }
                                v >= 1_000L -> "${v / 1_000L}rb"
                                else -> v.toString()
                            }
                        }
                        val maxLabel = formatScale(maxExp)
                        val midLabel = formatScale(maxExp / 2)
                        Text(text = maxLabel, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = TextSecondary)
                        Text(text = midLabel, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = TextSecondary)
                        Text(text = "0", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // 4b. Month Comparison & Financial Metrics Card (4D)
        if (monthStats != null) {
            val stats = monthStats!!
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
                                color = TextSecondary
                            )
                            val expDeltaPct = stats.expensePercentChange
                            val expDeltaLabel = if (expDeltaPct > 0) "+${String.format(java.util.Locale.US, "%.1f", expDeltaPct)}%"
                                               else "${String.format(java.util.Locale.US, "%.1f", expDeltaPct)}%"
                            val expBadgeColor = if (expDeltaPct > 0) StatusNegative else StatusPositive
                            Text(
                                text = "Pengeluaran vs Bulan Lalu: $expDeltaLabel",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp
                                ),
                                color = expBadgeColor
                            )
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
                                        fontSize = 10.sp
                                    ),
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isBalanceHidden) "••••••" else Formatters.formatRupiah(stats.averageDailyExpense),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.5.sp
                                    ),
                                    color = DarkSurface
                                )
                            }

                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "SAVINGS RATE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val srPct = (stats.savingsRate * 100).toInt()
                                Text(
                                    text = "$srPct%",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.5.sp
                                    ),
                                    color = if (srPct >= 20) StatusPositive else if (srPct > 0) DarkSurface else StatusNegative
                                )
                            }
                        }

                        if (stats.highestExpenseDay != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = PebbleBorder, thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Puncak Belanja: ${Formatters.formatDateReadable(stats.highestExpenseDay!!)}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = TextSecondary
                                )
                                Text(
                                    text = if (isBalanceHidden) "••••••" else Formatters.formatRupiah(stats.highestExpenseDayAmount),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    color = StatusNegative
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
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PebbleSurface),
                border = BorderStroke(1.dp, PebbleBorder)
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
                                    .background(JadePrimary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = JadePrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "Insight AI Gemini",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                ),
                                color = DarkSurface
                            )
                        }

                        // Lihat data yang dikirim button (Transparansi Privasi)
                        TextButton(
                            onClick = { showPayloadDialog = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Icon(imageVector = Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextSecondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Data Dikirim",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Analisis pola pengeluaran bulanan menggunakan Gemini AI secara privat (hanya mengirim ringkasan angka per kategori, tanpa teks deskripsi atau nama toko).",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp
                        ),
                        color = TextSecondary
                    )

                    if (aiInsightText != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(PebbleSurfaceVariant.copy(alpha = 0.5f))
                                .border(1.dp, PebbleBorder, RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = aiInsightText!!,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = DarkSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isLoadingAi) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = JadePrimary, strokeWidth = 2.dp)
                            }
                        } else {
                            Button(
                                onClick = {
                                    val key = geminiApiKey
                                    if (key.isNullOrBlank()) {
                                        showApiKeyDialog = true
                                    } else {
                                        isLoadingAi = true
                                        viewModel.requestGeminiInsight(key) { res ->
                                            isLoadingAi = false
                                            res.onSuccess { text ->
                                                aiInsightText = text
                                            }.onFailure { err ->
                                                aiInsightText = "Gagal memproses insight: ${err.message}"
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f).height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = JadePrimary)
                            ) {
                                Icon(imageVector = Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (aiInsightText != null) "Perbarui Analisis AI" else "Analisis Sekarang",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    tempApiKey = geminiApiKey ?: ""
                                    showApiKeyDialog = true
                                },
                                modifier = Modifier.height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, PebbleBorder)
                            ) {
                                Icon(imageVector = Icons.Rounded.Key, contentDescription = "API Key", modifier = Modifier.size(16.dp), tint = DarkSurface)
                            }
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
                            color = DarkSurface
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
                        innerColor = PebbleSurface
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            top3.forEachIndexed { idx, (cat, amount, pct) ->
                                val rankNumber = String.format(java.util.Locale.US, "%02d", idx + 1)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
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
                                            color = if (idx == 0) StatusNegative else TextSecondary
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        CategoryIcon(
                                            iconName = cat?.icon ?: "category",
                                            size = 28.dp,
                                            iconSize = 14.dp,
                                            backgroundColor = PebbleSurfaceVariant,
                                            tintColor = DarkSurface
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = cat?.name ?: "Lainnya",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = DarkSurface
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "−" + Formatters.formatRupiah(amount),
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                letterSpacing = (-0.3).sp
                                            ),
                                            color = StatusNegative
                                        )
                                        Text(
                                            text = "${String.format(java.util.Locale.US, "%.1f", pct)}%",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp
                                            ),
                                            color = TextSecondary
                                        )
                                    }
                                }
                                if (idx < top3.size - 1) {
                                    HorizontalDivider(color = PebbleBorder, thickness = 0.5.dp)
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
                        color = DarkSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    EyebrowTag(
                        text = "${activeBreakdown.size} KATEGORI",
                        variant = if (isExpenseMode) EyebrowVariant.NEGATIVE else EyebrowVariant.JADE
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            itemsIndexed(activeBreakdown) { _, (cat, amount, percent) ->
                val isDark = LocalIsDarkTheme.current
                val sliceColor = cat?.icon?.let { getCategoryColor(it, isDark) }
                    ?: (if (isDark) Color(0xFF90A4AE) else Color(0xFF4A5550))

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
                                size = 30.dp,
                                iconSize = 15.dp,
                                backgroundColor = sliceColor.copy(alpha = 0.12f),
                                tintColor = sliceColor
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = cat?.name ?: "Lainnya",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                color = DarkSurface
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            val sign = if (isExpenseMode) "−" else "+"
                            val amountColor = if (isExpenseMode) StatusNegative else StatusPositive
                            Text(
                                text = if (isBalanceHidden) "$sign••••••" else sign + Formatters.formatRupiah(amount),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.3).sp
                                ),
                                color = amountColor
                            )
                            Text(
                                text = "${String.format(java.util.Locale.US, "%.1f", percent)}%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
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
                        color = sliceColor,
                        trackColor = PebbleSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = PebbleBorder, thickness = 0.8.dp)
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
                        color = DarkSurface
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
                            text = ast?.name ?: "Aset",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            color = DarkSurface
                        )

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (isBalanceHidden) "−••••••" else "−" + Formatters.formatRupiah(amount),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.3).sp
                                ),
                                color = StatusNegative
                            )
                            Text(
                                text = "${String.format(java.util.Locale.US, "%.1f", percent)}%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
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
                        color = StatusNegative,
                        trackColor = PebbleSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = PebbleBorder, thickness = 0.8.dp)
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
                    Icon(imageVector = Icons.Rounded.Security, contentDescription = null, tint = JadePrimary)
                    Text("Transparansi Data", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            },
            text = {
                Column {
                    Text(
                        text = "Berikut adalah data JSON persis yang dikirim ke Gemini AI. Perhatikan bahwa hanya total angka kategori dan tanggal bulan yang dikirim. Tidak ada nama merchant, lokasi, atau catatan pribadi.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.5.sp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(PebbleSurfaceVariant)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = payloadJson,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                            color = DarkSurface
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPayloadDialog = false }) {
                    Text("TUTUP", fontWeight = FontWeight.Bold, color = JadePrimary)
                }
            }
        )
    }

    // Set Gemini API Key Dialog
    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = {
                Text("Gemini API Key", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            },
            text = {
                Column {
                    Text(
                        text = "Masukkan API Key Google Gemini Anda. Kunci disimpan terenkripsi di perangkat lokal dan tidak pernah dibagikan atau dicadangkan.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.5.sp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempApiKey,
                        onValueChange = { tempApiKey = it },
                        label = { Text("API Key (AIzaSy...)") },
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
                    colors = ButtonDefaults.buttonColors(containerColor = JadePrimary)
                ) {
                    Text("SIMPAN", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("BATAL", color = TextSecondary)
                }
            }
        )
    }
}
