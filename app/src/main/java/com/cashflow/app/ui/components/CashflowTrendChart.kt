package com.cashflow.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.data.model.PeriodFilter
import com.cashflow.app.data.model.Transaction
import com.cashflow.app.data.model.TransactionType
import com.cashflow.app.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class TrendBucket(
    val label: String,
    val income: Long,
    val expense: Long,
    val net: Long
)

/**
 * Section 22: Cashflow Trend Chart (Overdrive Engine)
 * Tactile, interactive 60 FPS scrubber with smooth cubic spline Bezier curves,
 * dynamic gradient fill, haptic feedback per bucket, and instant daily inspection.
 */
@Composable
fun CashflowTrendChart(
    transactions: List<Transaction>,
    period: PeriodFilter,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var selectedBucketIndex by remember { mutableStateOf<Int?>(null) }
    var lastHapticIndex by remember { mutableStateOf<Int?>(null) }

    // Group actual transactions into chronological buckets
    val buckets: List<TrendBucket> = remember(transactions, period) {
        if (transactions.isEmpty()) {
            emptyList()
        } else {
            val sorted = transactions.sortedBy { it.transactionDate }
            val grouped = sorted.groupBy { it.transactionDate }
            // Take up to 7 distinct days/intervals to fit cleanly without crowding
            val allKeys = grouped.keys.toList()
            val selectedDates = if (allKeys.size > 7) allKeys.takeLast(7) else allKeys
            selectedDates.map { dateStr ->
                val dayTxs = grouped[dateStr].orEmpty()
                val inc = dayTxs.filter { it.type == TransactionType.INCOME.value }.sumOf { it.amount }
                val exp = dayTxs.filter { it.type == TransactionType.EXPENSE.value }.sumOf { it.amount }
                val net = inc - exp
                val label = try {
                    val parsed = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
                    "${parsed.dayOfMonth}/${parsed.monthValue}"
                } catch (e: Exception) {
                    if (dateStr.length >= 5) dateStr.substring(dateStr.length - 5) else dateStr
                }
                TrendBucket(label = label, income = inc, expense = exp, net = net)
            }
        }
    }

    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(buckets) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = DuitAingMotion.DURATION_DELIBERATE, easing = DuitAingMotion.SubtleEasing)
        )
    }

    val duit = MaterialTheme.duit
    val isDark = LocalIsDarkTheme.current

    if (buckets.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(duit.surface)
                .border(1.dp, duit.border, RoundedCornerShape(8.dp))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ShowChart,
                    contentDescription = null,
                    tint = duit.textSecondary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "BELUM ADA TREN CASHFLOW",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    ),
                    color = duit.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Transaksi belum mencukupi untuk membentuk grafik tren.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = duit.textSecondary
                )
            }
        }
        return
    }

    // Main Card
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(duit.surface)
            .border(1.dp, duit.border, RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Column {
            // Header with legend & tactile scrubber state
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "TREN CASHFLOW",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        ),
                        color = duit.textPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(duit.primaryContainer)
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "INTERAKTIF 👆",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.4.sp
                            ),
                            color = duit.onPrimaryContainer
                        )
                    }
                }

                // Legend
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendIndicator(color = duit.income, text = "MASUK")
                    LegendIndicator(color = duit.expense, text = "KELUAR")
                    LegendIndicator(color = duit.primary, text = "NET")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dynamic Daily Inspection Banner (Shown when a bucket is actively scrubbed/tapped)
            val activeBucket = selectedBucketIndex?.let { idx -> buckets.getOrNull(idx) }
            if (activeBucket != null) {
                val tooltipBg = if (isDark) duit.accentLime else Color(0xFF111111)
                val tooltipFg = if (isDark) Color(0xFF111111) else Color.White
                val tooltipFgSec = if (isDark) Color(0xFF333333) else Color(0xFFCCCCCC)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(tooltipBg)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "RINCIAN: ${activeBucket.label}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = tooltipFg
                                )
                                IconButton(
                                    onClick = {
                                        selectedBucketIndex = null
                                        lastHapticIndex = null
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .size(32.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(tooltipFg.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Tutup inspeksi",
                                            tint = tooltipFg,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Masuk: +${Formatters.formatRupiah(activeBucket.income)}  •  Keluar: −${Formatters.formatRupiah(activeBucket.expense)}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = tooltipFgSec
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "NET HARIAN",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.4.sp
                                ),
                                color = tooltipFgSec
                            )
                            val netSign = if (activeBucket.net >= 0) "+" else "−"
                            Text(
                                text = netSign + Formatters.formatRupiah(abs(activeBucket.net)),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.5.sp
                                ),
                                color = tooltipFg
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💡 Sentuh atau geser grafik untuk melihat rincian tanggal",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = TextSecondary
                    )
                }
            }

            // Canvas Chart with Tactile Gesture Scrubber
            val maxVal = remember(buckets) {
                val maxInc = buckets.maxOfOrNull { b: TrendBucket -> b.income } ?: 0L
                val maxExp = buckets.maxOfOrNull { b: TrendBucket -> b.expense } ?: 0L
                maxOf(maxInc, maxExp, 1000L).toFloat()
            }

            val chartBorderColor = duit.border
            val chartMidBorderColor = duit.border.copy(alpha = 0.5f)
            val chartPointColor = duit.textPrimary
            val chartActiveColor = duit.primary
            val chartInactiveColor = duit.chartInactive
            val statusPosColor = duit.income
            val statusNegColor = duit.expense
            val activeIndex = selectedBucketIndex

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .pointerInput(buckets) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val stepX = size.width / buckets.size.coerceAtLeast(1)
                                val idx = ((offset.x / stepX).toInt()).coerceIn(0, buckets.lastIndex)
                                selectedBucketIndex = idx
                                if (lastHapticIndex != idx) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    lastHapticIndex = idx
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val stepX = size.width / buckets.size.coerceAtLeast(1)
                                val idx = ((change.position.x / stepX).toInt()).coerceIn(0, buckets.lastIndex)
                                selectedBucketIndex = idx
                                if (lastHapticIndex != idx) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    lastHapticIndex = idx
                                }
                            },
                            onDragEnd = { /* keep inspected bucket active */ },
                            onDragCancel = { /* keep current selection */ }
                        )
                    }
                    .pointerInput(buckets) {
                        detectTapGestures { offset ->
                            val stepX = size.width / buckets.size.coerceAtLeast(1)
                            val idx = ((offset.x / stepX).toInt()).coerceIn(0, buckets.lastIndex)
                            if (selectedBucketIndex == idx) {
                                selectedBucketIndex = null
                                lastHapticIndex = null
                            } else {
                                selectedBucketIndex = idx
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastHapticIndex = idx
                            }
                        }
                    }
            ) {
                val canvasW = size.width
                val canvasH = size.height
                val bottomY = canvasH - 18.dp.toPx()
                val chartH = bottomY - 10.dp.toPx()
                val count = buckets.count()
                val stepX = canvasW / count.coerceAtLeast(1)

                // Baseline
                drawLine(
                    color = chartBorderColor,
                    start = Offset(0f, bottomY),
                    end = Offset(canvasW, bottomY),
                    strokeWidth = 1.dp.toPx()
                )

                // Mid reference line
                drawLine(
                    color = chartMidBorderColor,
                    start = Offset(0f, bottomY - (chartH * 0.5f)),
                    end = Offset(canvasW, bottomY - (chartH * 0.5f)),
                    strokeWidth = 0.8.dp.toPx()
                )

                val netPoints = mutableListOf<Offset>()

                // 1. Draw Bars (Income & Expense)
                buckets.forEachIndexed { i: Int, b: TrendBucket ->
                    val centerX = (i * stepX) + (stepX / 2f)
                    val barWidth = (stepX * 0.28f).coerceIn(6f, 16f)
                    val isCurrentSelected = (activeIndex != null && activeIndex == i)
                    val isAnySelected = activeIndex != null

                    // Income Bar
                    val incRatio = (b.income.toFloat() / maxVal).coerceIn(0f, 1f) * animProgress.value
                    val incH = incRatio * chartH
                    val incColor = if (isAnySelected && !isCurrentSelected) chartInactiveColor else statusPosColor
                    if (incH > 1f) {
                        drawRect(
                            color = incColor,
                            topLeft = Offset(centerX - barWidth - 1f, bottomY - incH),
                            size = Size(barWidth, incH)
                        )
                    }

                    // Expense Bar
                    val expRatio = (b.expense.toFloat() / maxVal).coerceIn(0f, 1f) * animProgress.value
                    val expH = expRatio * chartH
                    val expColor = if (isAnySelected && !isCurrentSelected) chartInactiveColor else statusNegColor
                    if (expH > 1f) {
                        drawRect(
                            color = expColor,
                            topLeft = Offset(centerX + 1f, bottomY - expH),
                            size = Size(barWidth, expH)
                        )
                    }

                    // Net point coordinate
                    val netRatio = (b.net.toFloat() / maxVal).coerceIn(-1f, 1f) * animProgress.value
                    val netY = bottomY - (chartH * 0.5f) - (netRatio * chartH * 0.45f)
                    netPoints.add(Offset(centerX, netY))
                }

                // 2. Draw Smooth Spline Curve for Net Line & Area Gradient
                if (netPoints.isNotEmpty()) {
                    val netPath = Path()
                    netPath.moveTo(netPoints[0].x, netPoints[0].y)

                    if (netPoints.size == 1) {
                        // Single point
                    } else if (netPoints.size == 2) {
                        netPath.lineTo(netPoints[1].x, netPoints[1].y)
                    } else {
                        // Catmull-Rom to Cubic Bezier curve
                        for (i in 0 until netPoints.size - 1) {
                            val p0 = netPoints[max(0, i - 1)]
                            val p1 = netPoints[i]
                            val p2 = netPoints[i + 1]
                            val p3 = netPoints[min(netPoints.lastIndex, i + 2)]

                            val cp1X = p1.x + (p2.x - p0.x) / 6f
                            val cp1Y = p1.y + (p2.y - p0.y) / 6f
                            val cp2X = p2.x - (p3.x - p1.x) / 6f
                            val cp2Y = p2.y - (p3.y - p1.y) / 6f

                            netPath.cubicTo(cp1X, cp1Y, cp2X, cp2Y, p2.x, p2.y)
                        }
                    }

                    // Soft Area Gradient underneath Net Curve
                    val fillPath = Path().apply {
                        addPath(netPath)
                        lineTo(netPoints.last().x, bottomY)
                        lineTo(netPoints.first().x, bottomY)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                chartActiveColor.copy(alpha = 0.20f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = bottomY
                        )
                    )

                    // Draw Smooth Line
                    drawPath(
                        path = netPath,
                        color = chartActiveColor,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw Point Dots
                    netPoints.forEachIndexed { i, pt ->
                        val isSel = (activeIndex != null && activeIndex == i)
                        if (!isSel) {
                            drawCircle(
                                color = chartPointColor,
                                radius = 2.8.dp.toPx(),
                                center = pt
                            )
                        }
                    }
                }

                // 3. Draw Active Scrubber Vertical Guide & Glowing Halo
                if (activeIndex != null && activeIndex in netPoints.indices) {
                    val selPoint = netPoints[activeIndex]
                    val selCenterX = selPoint.x

                    // Vertical Dashed Scrubber Guide Line
                    drawLine(
                        color = chartActiveColor.copy(alpha = 0.65f),
                        start = Offset(selCenterX, 0f),
                        end = Offset(selCenterX, bottomY),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                    )

                    // Concentric Glowing Rings on Selected Net Point
                    drawCircle(
                        color = chartActiveColor.copy(alpha = 0.22f),
                        radius = 11.dp.toPx(),
                        center = selPoint
                    )
                    drawCircle(
                        color = chartActiveColor,
                        radius = 5.dp.toPx(),
                        center = selPoint
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.dp.toPx(),
                        center = selPoint
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // X-axis date labels with active selection highlight
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                buckets.forEachIndexed { idx, b ->
                    val isSel = (activeIndex != null && activeIndex == idx)
                    Text(
                        text = b.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = if (isSel) 10.sp else 9.5.sp,
                            fontWeight = if (isSel) FontWeight.Black else FontWeight.Bold
                        ),
                        color = if (isSel) duit.primary else duit.textTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendIndicator(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.4.sp
            ),
            color = TextSecondary
        )
    }
}
