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
import com.cashflow.app.data.model.Category
import com.cashflow.app.data.model.reportTransactionDetails
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.components.*
import com.cashflow.app.ui.theme.*

/**
 * Editorial Reports Screen for "Duit Aing"
 * Open layout without excessive card nesting, emphasizing data clarity and insights.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportsScreen(
    viewModel: CashflowViewModel,
    modifier: Modifier = Modifier,
    onTourCalendarPositioned: (Rect) -> Unit = {}
) {
    val report by viewModel.reportSnapshot.collectAsState()
    val summary = report.summary
    val chartBuckets = report.chart
    val reportRange = report.range
    val periodIncome = summary.income
    val periodExpense = summary.expense
    val netCashflow = periodIncome - periodExpense
    val selectedPeriod = report.period
    val categories by viewModel.categories.collectAsState()
    val allAssets by viewModel.assets.collectAsState()
    val monthStats by viewModel.monthComparisonStats.collectAsState()

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
    var detailCategory by remember { mutableStateOf<String?>(null) }
    var detailType by remember { mutableStateOf<String?>(null) }

    val categoryBreakdown = remember(summary.expenseByCategory, catMap) {
        val total = summary.expenseByCategory.values.sum()
        if (total == 0L) emptyList() else summary.expenseByCategory.map { (id, amount) ->
            Triple(catMap[id] ?: Category(id, "Lainnya", "expense"), amount, amount.toFloat() / total * 100f)
        }.sortedByDescending { it.second }
    }
    val incomeCategoryBreakdown = remember(summary.incomeByCategory, catMap) {
        val total = summary.incomeByCategory.values.sum()
        if (total == 0L) emptyList() else summary.incomeByCategory.map { (id, amount) ->
            Triple(catMap[id] ?: Category(id, "Lainnya", "income"), amount, amount.toFloat() / total * 100f)
        }.sortedByDescending { it.second }
    }
    val assetBreakdown = remember(summary.expenseByAsset, assetMap) {
        val total = summary.expenseByAsset.values.sum()
        if (total == 0L) emptyList() else summary.expenseByAsset.map { (id, amount) ->
            Triple(assetMap[id], amount, amount.toFloat() / total * 100f)
        }.sortedByDescending { it.second }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            coroutineScope.launch {
                viewModel.refreshData()
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
                        softWrap = true
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
            Column(Modifier.fillMaxWidth().clickable(role = Role.Button) {
                detailCategory = null
                detailType = reportChartType
            }) {
            DonutExpenseChart(
                breakdownList = currentBreakdown,
                totalExpense = currentTotal,
                isExpense = isExpense,
                isBalanceHidden = isBalanceHidden
            )
            Text("Lihat transaksi pembentuk total", style = MaterialTheme.typography.labelLarge,
                color = duit.primary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Trend and breakdowns use exactly the same report transaction set.
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text(if (isExpense) "Tren Pengeluaran" else "Tren Pemasukan", style = MaterialTheme.typography.titleLarge, color = duit.textPrimary)
                Text(periodLabel, style = MaterialTheme.typography.bodySmall, color = duit.textSecondary)
                Spacer(Modifier.height(16.dp))
                ReportTrendChart(chartBuckets, isExpense, isBalanceHidden, Modifier.fillMaxWidth())
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
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Metrik & perbandingan",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp,
                                    fontSize = 12.sp
                                ),
                                color = duit.textSecondary
                            )
                            val comparison = report.comparison
                            val previousTotal = if (isExpense) comparison?.previous?.expense ?: 0L else comparison?.previous?.income ?: 0L
                            val comparisonTotal = if (isExpense) comparison?.current?.expense ?: 0L else comparison?.current?.income ?: 0L
                            val expDeltaPct = if (previousTotal > 0) (comparisonTotal - previousTotal).toDouble() / previousTotal * 100.0 else 0.0
                            val expDeltaLabel = if (expDeltaPct > 0) "+${String.format(java.util.Locale.US, "%.1f", expDeltaPct)}%"
                                               else "${String.format(java.util.Locale.US, "%.1f", expDeltaPct)}%"
                            val expBadgeColor = if (previousTotal <= 0) duit.textSecondary
                                else if ((isExpense && expDeltaPct > 0) || (!isExpense && expDeltaPct < 0)) duit.expense else duit.income
                            val expBadgeBg = expBadgeColor.copy(alpha = 0.14f)

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(expBadgeBg)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isBalanceHidden) "••••••" else if (previousTotal > 0) expDeltaLabel else "Belum ada pembanding",
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

                        val comparison = report.comparison
                        Spacer(Modifier.height(12.dp))
                        comparison?.let {
                            Text("Bulan ini: ${Formatters.formatDateReadable(it.currentRange.start.toString())} – ${Formatters.formatDateReadable(it.currentRange.end.toString())}",
                                style = MaterialTheme.typography.bodySmall, color = duit.textSecondary)
                            Spacer(Modifier.height(4.dp))
                            Text("Bulan lalu: ${Formatters.formatDateReadable(it.previousRange.start.toString())} – ${Formatters.formatDateReadable(it.previousRange.end.toString())}",
                                style = MaterialTheme.typography.bodySmall, color = duit.textSecondary)
                        }
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "BELANJA / HARI",
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
                                    text = "SISA PEMASUKAN",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    color = duit.textSecondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val srPct = (stats.savingsRate * 100).toInt()
                                Text(
                                    text = if (isBalanceHidden) "••••••" else if (stats.currentMonthTotalIncome > 0) "$srPct%" else "—",
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
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Puncak Belanja: ${Formatters.formatDateReadable(stats.highestExpenseDay!!)}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = duit.textSecondary,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = if (isBalanceHidden) "••••••" else Formatters.displayRupiah(stats.highestExpenseDayAmount),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    color = duit.expense
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        // Vina offline
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
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                                text = "Konsultasi bersama Vina",
                                modifier = Modifier.weight(1f),
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
                                text = "Privasi Vina",
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
                        text = "Baca transaksi, arus kas, anggaran, dan target bersama Vina. Analisis berjalan di perangkat tanpa internet atau kunci API.",
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
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = duit.primary,
                                contentColor = duit.onPrimary
                            )
                        ) {
                            Icon(imageVector = Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tanya Vina",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                            )
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
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                        .clickable(role = Role.Button) { detailCategory = cat?.id.orEmpty(); detailType = "expense" }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(modifier = Modifier.weight(1f).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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
                                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
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
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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

            itemsIndexed(activeBreakdown, key = { index, row -> row.first?.id?.let { "category_$it" } ?: "missing_category_$index" }, contentType = { _, _ -> "category_breakdown" }) { idx, (cat, amount, percent) ->
                val chartColors = duit.chart
                val sliceColor = if (cat != null) getCategoryColor(cat.icon, isDark) else if (idx < 6 && idx < chartColors.size) chartColors[idx] else duit.chartOther

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { detailCategory = cat?.id.orEmpty(); detailType = reportChartType }
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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

                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
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
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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

    detailType?.let { type ->
        val details = remember(report.transactions, type, detailCategory) {
            reportTransactionDetails(report.transactions, type, detailCategory)
        }
        ReportTransactionsSheet(
            title = detailCategory?.let { catMap[it]?.name ?: "Lainnya" }
                ?: if (type == "expense") "Rincian pengeluaran" else "Rincian pemasukan",
            periodLabel = periodLabel, transactions = details,
            categories = catMap, assets = assetMap, isBalanceHidden = isBalanceHidden,
            onDismiss = { detailType = null }, onOpen = { tx ->
                detailType = null
                viewModel.selectedTransactionForDetail.value = tx
            })
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
    if (showPayloadDialog) {
        AlertDialog(onDismissRequest = { showPayloadDialog = false },
            title = { Text("Vina bekerja di perangkat") },
            text = { Text("Vina membaca transaksi, rekening, kategori, anggaran, hutang, dan target yang tersimpan di aplikasi. Percakapan dan data keuangan tidak dikirim ke penyedia AI. Jawaban bergantung pada kelengkapan catatan dan bukan jaminan hasil keuangan.") },
            confirmButton = { TextButton(onClick = { showPayloadDialog = false }) { Text("Tutup") } })
    }
}
