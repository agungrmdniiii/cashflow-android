package com.cashflow.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PieChartOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.data.model.Category
import com.cashflow.app.ui.theme.*
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class CategorySliceData(
    val category: Category?,
    val name: String,
    val amount: Long,
    val percentage: Float,
    val color: Color
)

// Curated palette mapped to theme chart tokens
val DonutChartPalette: List<Color>
    get() = LightColors.chart

/**
 * Immersive Interactive Fintech Donut Chart for Expense Distribution
 */
@Composable
fun DonutExpenseChart(
    breakdownList: List<Triple<Category?, Long, Float>>,
    totalExpense: Long,
    modifier: Modifier = Modifier,
    chartSize: Dp = 220.dp,
    isExpense: Boolean = true,
    isBalanceHidden: Boolean = false
) {
    val duit = MaterialTheme.duit
    val isDark = LocalIsDarkTheme.current
    val chartPalette = duit.chart
    val otherColor = duit.chartOther

    // Map data to slice models with colors: Top 6 (consistent with category colors) + aggregate "Lainnya" (chartOther)
    val slices = remember(breakdownList, chartPalette, otherColor, isDark) {
        val resolveColor: (Category?, Int) -> Color = { cat, idx ->
            if (cat != null) {
                getCategoryColor(cat.icon, isDark)
            } else if (idx < chartPalette.size) {
                chartPalette[idx]
            } else {
                otherColor
            }
        }
        if (breakdownList.size <= 6) {
            breakdownList.mapIndexed { idx, (cat, amount, pct) ->
                CategorySliceData(
                    category = cat,
                    name = cat?.name ?: "Lainnya",
                    amount = amount,
                    percentage = pct,
                    color = resolveColor(cat, idx)
                )
            }
        } else {
            val top6 = breakdownList.take(6).mapIndexed { idx, (cat, amount, pct) ->
                CategorySliceData(
                    category = cat,
                    name = cat?.name ?: "Lainnya",
                    amount = amount,
                    percentage = pct,
                    color = resolveColor(cat, idx)
                )
            }
            val remaining = breakdownList.drop(6)
            val othersAmount = remaining.sumOf { it.second }
            val othersPct = remaining.sumOf { it.third.toDouble() }.toFloat()
            top6 + CategorySliceData(
                category = null,
                name = "Lainnya",
                amount = othersAmount,
                percentage = othersPct,
                color = otherColor
            )
        }
    }

    var selectedIndex by remember(breakdownList) { mutableStateOf<Int?>(null) }

    // Smooth entrance sweep animation
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(breakdownList) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 650)
        )
    }

    if (totalExpense <= 0L || slices.isEmpty()) {
        // Empty State: Clean geometric dashed outline
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(duit.surfaceVariant)
                        .border(1.dp, duit.border, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PieChartOutline,
                        contentDescription = null,
                        tint = duit.textSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (isExpense) "BELUM ADA PENGELUARAN" else "BELUM ADA PEMASUKAN",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    ),
                    color = duit.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isExpense)
                        "Tidak ada transaksi pengeluaran pada periode ini."
                    else
                        "Tidak ada transaksi pemasukan pada periode ini.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = duit.textSecondary
                )
            }
        }
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Interactive Donut Canvas with Center Text
        Box(
            modifier = Modifier
                .size(chartSize)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(slices) {
                        detectTapGestures { tapOffset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val dx = tapOffset.x - center.x
                            val dy = tapOffset.y - center.y
                            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                            val outerRadius = size.width / 2f
                            val innerRadius = outerRadius - 38.dp.toPx()

                            // Check if tap is within the donut ring
                            if (distance in innerRadius..outerRadius) {
                                var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                if (angle < 0) angle += 360f
                                // Align with -90 degree starting offset
                                val normalizedAngle = (angle + 90f) % 360f

                                var currentAngle = 0f
                                var tappedIdx: Int? = null
                                for (i in slices.indices) {
                                    val sweep = (slices[i].percentage / 100f) * 360f
                                    if (normalizedAngle >= currentAngle && normalizedAngle < currentAngle + sweep) {
                                        tappedIdx = i
                                        break
                                    }
                                    currentAngle += sweep
                                }

                                selectedIndex = if (selectedIndex == tappedIdx) null else tappedIdx
                            } else if (distance < innerRadius) {
                                // Tap in center resets selection
                                selectedIndex = null
                            }
                        }
                    }
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val strokeWidth = 32.dp.toPx()
                val activeStrokeWidth = 38.dp.toPx()

                val arcSize = Size(canvasWidth - activeStrokeWidth, canvasHeight - activeStrokeWidth)
                val topLeft = Offset(activeStrokeWidth / 2f, activeStrokeWidth / 2f)

                var startAngle = -90f

                // Draw subtle track ring for visual depth
                drawArc(
                    color = duit.chartInactive,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )

                slices.forEachIndexed { index, slice ->
                    val sweepAngle = (slice.percentage / 100f) * 360f * animationProgress.value
                    val isSelected = selectedIndex == index

                    val currentStroke = if (isSelected) activeStrokeWidth else strokeWidth
                    val sliceColor = when {
                        selectedIndex == null -> slice.color
                        isSelected -> slice.color
                        else -> slice.color.copy(alpha = 0.25f)
                    }

                    drawArc(
                        color = sliceColor,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle.coerceAtLeast(0.5f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = currentStroke, cap = if (slices.size == 1) StrokeCap.Round else StrokeCap.Butt)
                    )

                    startAngle += sweepAngle
                }

                // 2dp gap between segments in surface color
                if (slices.size > 1 && animationProgress.value > 0.05f) {
                    val center = Offset(canvasWidth / 2f, canvasHeight / 2f)
                    val outerR = canvasWidth / 2f
                    val innerR = outerR - activeStrokeWidth
                    var divAngle = -90f
                    slices.forEach { slice ->
                        val rad = Math.toRadians(divAngle.toDouble())
                        val cosVal = kotlin.math.cos(rad).toFloat()
                        val sinVal = kotlin.math.sin(rad).toFloat()
                        val p1 = Offset(center.x + innerR * cosVal, center.y + innerR * sinVal)
                        val p2 = Offset(center.x + outerR * cosVal, center.y + outerR * sinVal)
                        drawLine(
                            color = duit.surface,
                            start = p1,
                            end = p2,
                            strokeWidth = 2.dp.toPx()
                        )
                        val sweepAngle = (slice.percentage / 100f) * 360f * animationProgress.value
                        divAngle += sweepAngle
                    }
                }
            }

            // 2. DONUT CENTER DISPLAY (Constrained strictly to inner donut hole diameter)
            Column(
                modifier = Modifier
                    .widthIn(max = (chartSize - 84.dp).coerceAtLeast(110.dp))
                    .padding(horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val selectedSlice = selectedIndex?.let { slices.getOrNull(it) }

                if (slices.size == 1) {
                    val single = slices[0]
                    Text(
                        text = if (isBalanceHidden) "Rp ••••••" else Formatters.formatRupiah(single.amount),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            letterSpacing = (-0.4).sp
                        ),
                        color = duit.textPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = single.name,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = single.color,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "100% Porsi",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        ),
                        color = duit.textSecondary,
                        textAlign = TextAlign.Center
                    )
                } else if (selectedSlice != null) {
                    Text(
                        text = if (isBalanceHidden) "Rp ••••••" else Formatters.formatRupiah(selectedSlice.amount),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            letterSpacing = (-0.4).sp
                        ),
                        color = duit.textPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = selectedSlice.name,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = selectedSlice.color,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = String.format(java.util.Locale.US, "%.0f%% Porsi", selectedSlice.percentage),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        ),
                        color = duit.textSecondary,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = if (isBalanceHidden) "Rp ••••••" else Formatters.formatRupiah(totalExpense),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            letterSpacing = (-0.5).sp
                        ),
                        color = duit.textPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isExpense) "Total Pengeluaran" else "Total Pemasukan",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 11.sp
                        ),
                        color = duit.textSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 3. Highlight Card for 1 Category OR 2-Column Legend for Multiple
        if (slices.size == 1) {
            val single = slices[0]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(duit.surface)
                    .border(1.dp, duit.border, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        CategoryIcon(
                            iconName = single.category?.icon ?: "category",
                            size = 38.dp,
                            iconSize = 19.dp,
                            backgroundColor = single.color.copy(alpha = 0.20f),
                            tintColor = single.color
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = single.name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp
                                ),
                                color = duit.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isExpense) "100% dari seluruh pengeluaran" else "100% dari seluruh pemasukan",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = duit.textSecondary
                            )
                        }
                    }
                    Text(
                        text = if (isBalanceHidden) "Rp ••••••" else Formatters.formatRupiah(single.amount),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.5.sp
                        ),
                        color = single.color
                    )
                }
            }
        } else {
            val chunkedSlices = remember(slices) { slices.chunked(2) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chunkedSlices.forEach { rowSlices ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        rowSlices.forEach { slice ->
                            val index = slices.indexOf(slice)
                            val isSelected = selectedIndex == index
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(role = Role.Button) {
                                        selectedIndex = if (isSelected) null else index
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(slice.color)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = slice.name,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp
                                    ),
                                    color = if (isSelected) duit.textPrimary else duit.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = String.format(java.util.Locale.US, "%.0f%%", slice.percentage),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    color = duit.textPrimary
                                )
                            }
                        }
                        if (rowSlices.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
