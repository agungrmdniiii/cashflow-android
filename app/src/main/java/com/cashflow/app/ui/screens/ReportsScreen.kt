package com.cashflow.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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

    val catMap = remember(categories) { categories.associateBy { it.id } }
    val assetMap = remember(assets) { assets.associateBy { it.id } }

    var reportChartType by remember { mutableStateOf("expense") } // "expense" or "income"
    var showPeriodSheet by remember { mutableStateOf(false) }

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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PebbleBackground),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        // 1. Screen Header with Editorial Typography
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)
            ) {
                Text(
                    text = "LAPORAN",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.3).sp,
                        fontSize = 22.sp
                    ),
                    color = DarkSurface,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.height(3.dp))
                EyebrowTag(
                    text = "${periodTransactions.size} TRANSAKSI TERCATAT",
                    variant = EyebrowVariant.DEFAULT
                )
            }
        }

        // 2. Cashflow Summary with Concentric Double-Bezel Card Architecture
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EyebrowTag(
                                text = "ARUS KAS BERSIH",
                                variant = if (netCashflow >= 0) EyebrowVariant.JADE else EyebrowVariant.NEGATIVE
                            )

                            // Integrated Contextual Period Selector Pill (Accessible touch target)
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PebbleSurfaceVariant)
                                    .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
                                    .clickable(role = Role.Button) { showPeriodSheet = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
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
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 10.5.sp,
                                            letterSpacing = 0.5.sp
                                        ),
                                        color = DarkSurface
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowDropDown,
                                        contentDescription = null,
                                        tint = DarkSurface,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

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

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = PebbleBorder, thickness = 0.8.dp)
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "TOTAL PEMASUKAN",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.6.sp,
                                        fontSize = 10.sp
                                    ),
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                AnimatedNumberText(
                                    value = periodIncome,
                                    prefix = "+",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = (-0.4).sp
                                    ),
                                    color = StatusPositive,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "TOTAL PENGELUARAN",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.6.sp,
                                        fontSize = 10.sp
                                    ),
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                AnimatedNumberText(
                                    value = periodExpense,
                                    prefix = "−",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = (-0.4).sp
                                    ),
                                    color = StatusNegative,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }

        // 4. Distribution Donut Chart (Pengeluaran & Pemasukan)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    text = if (reportChartType == "expense") "DISTRIBUSI PENGELUARAN" else "DISTRIBUSI PEMASUKAN",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    ),
                    color = DarkSurface
                )
                Text(
                    text = if (reportChartType == "expense")
                        "KOMPOSISI BELANJA AKTUAL (${selectedPeriod.displayName.uppercase()})"
                    else
                        "SUMBER PEMASUKAN AKTUAL (${selectedPeriod.displayName.uppercase()})",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(14.dp))

                // High-End Segmented Selector: Pengeluaran / Pemasukan
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(PebbleSurface)
                        .border(1.dp, PebbleBorder, RoundedCornerShape(8.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isExp = reportChartType == "expense"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isExp) DarkSurface else Color.Transparent)
                            .then(if (isExp) Modifier.border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(6.dp)) else Modifier)
                            .clickable(role = Role.Tab) { reportChartType = "expense" }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(StatusNegative)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "PENGELUARAN",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.6.sp
                                ),
                                color = if (isExp) PebbleSurface else TextSecondary
                            )
                        }
                    }

                    val isInc = reportChartType == "income"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isInc) JadePrimary else Color.Transparent)
                            .then(if (isInc) Modifier.border(1.dp, JadePrimaryDark.copy(alpha = 0.30f), RoundedCornerShape(6.dp)) else Modifier)
                            .clickable(role = Role.Tab) { reportChartType = "income" }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(StatusPositive)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "PEMASUKAN",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.6.sp
                                ),
                                color = if (isInc) PebbleSurface else TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val currentBreakdown = if (reportChartType == "expense") categoryBreakdown else incomeCategoryBreakdown
                val currentTotal = if (reportChartType == "expense") periodExpense else periodIncome

                DonutExpenseChart(
                    breakdownList = currentBreakdown,
                    totalExpense = currentTotal,
                    isExpense = (reportChartType == "expense")
                )

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = PebbleBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(18.dp))
            }
        }


        // 4c. Top Spending Ranking (01, 02, 03)
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


        // Section: Breakdown Kategori
        val isExpenseMode = reportChartType == "expense"
        val activeBreakdown = if (isExpenseMode) categoryBreakdown else incomeCategoryBreakdown

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

        if (activeBreakdown.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PebbleSurface)
                        .border(1.dp, PebbleBorder, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isExpenseMode) "BELUM ADA PENGELUARAN" else "BELUM ADA PEMASUKAN",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            ),
                            color = DarkSurface
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (isExpenseMode)
                                "Tidak ada catatan pengeluaran pada periode ${selectedPeriod.displayName.lowercase()}."
                            else
                                "Tidak ada catatan pemasukan pada periode ${selectedPeriod.displayName.lowercase()}.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            itemsIndexed(activeBreakdown) { index, (cat, amount, percent) ->
                val sliceColor = DonutChartPalette[index % DonutChartPalette.size]

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
                                text = sign + Formatters.formatRupiah(amount),
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

        // Section: Breakdown Aset
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

        if (assetBreakdown.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PebbleSurface)
                        .border(1.dp, PebbleBorder, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "BELUM ADA PENGELUARAN ASET",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            ),
                            color = DarkSurface
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Belum ada transaksi pengeluaran tercatat untuk aset aktif.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
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
                                text = "−" + Formatters.formatRupiah(amount),
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

    PeriodSelectionSheet(
        visible = showPeriodSheet,
        selectedPeriod = selectedPeriod,
        onPeriodSelected = {
            viewModel.setPeriodFilter(it)
            showPeriodSheet = false
        },
        onDismissRequest = { showPeriodSheet = false }
    )
}
