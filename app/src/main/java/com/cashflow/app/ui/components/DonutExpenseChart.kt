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

// Curated harmonious Jade Pebble Morning chart palette (no rainbow slop)
val DonutChartPalette = listOf(
    LightJadePrimary,           // #0C6B55 (Signature Jade)
    LightDarkSurface,           // #141A17 (Deep Charcoal)
    Color(0xFF108569),     // Jade Medium
    AccentAmber,           // #E66A23 (Warm Editorial Accent)
    Color(0xFF2B8A3E),     // Forest Green
    StatusTransfer,        // #1864AB (High-contrast Slate Blue)
    Color(0xFF495057),     // Deep Mineral Slate
    Color(0xFF084839),     // Deep Jade
    Color(0xFF868E96),     // Pebble Slate Neutral
    Color(0xFFD9480F)      // Terracotta
)

/**
 * Immersive Interactive Neo-Brutalist Donut Chart for Expense Distribution
 *
 * Implements Sections 27-32 of the Master Product Specification:
 * - Uses ACTUAL expense transactions only (transfers & incomes excluded)
 * - Jade Pebble Morning harmonious palette
 * - Center of Donut:
 *     Default: "TOTAL PENGELUARAN" + formatted total
 *     On Tap: Category Name (uppercase) + formatted amount + percentage (e.g. 26,2%)
 * - Interactive slice selection with smooth entrance animation
 * - Empty state handling
 */
@Composable
fun DonutExpenseChart(
    breakdownList: List<Triple<Category?, Long, Float>>,
    totalExpense: Long,
    modifier: Modifier = Modifier,
    chartSize: Dp = 220.dp,
    isExpense: Boolean = true
) {
    // Map data to slice models with colors
    val slices = remember(breakdownList) {
        breakdownList.mapIndexed { index, (cat, amount, pct) ->
            CategorySliceData(
                category = cat,
                name = cat?.name ?: "Lainnya",
                amount = amount,
                percentage = pct,
                color = DonutChartPalette[index % DonutChartPalette.size]
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
                        .background(PebbleSurfaceVariant)
                        .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PieChartOutline,
                        contentDescription = null,
                        tint = TextSecondary,
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
                    color = DarkSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isExpense)
                        "Tidak ada transaksi pengeluaran pada periode ini."
                    else
                        "Tidak ada transaksi pemasukan pada periode ini.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = TextSecondary
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
                        style = Stroke(width = currentStroke, cap = StrokeCap.Butt)
                    )

                    startAngle += sweepAngle
                }
            }

            // 2. DONUT CENTER DISPLAY (Section 29)
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val selectedSlice = selectedIndex?.let { slices.getOrNull(it) }

                if (selectedSlice != null) {
                    Text(
                        text = selectedSlice.name.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                            fontSize = 11.sp
                        ),
                        color = selectedSlice.color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val prefix = if (isExpense) "−" else "+"
                    val amountColor = if (isExpense) StatusNegative else StatusPositive
                    Text(
                        text = prefix + Formatters.formatRupiah(selectedSlice.amount),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = amountColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f%%", selectedSlice.percentage),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = if (isExpense) "TOTAL PENGELUARAN" else "TOTAL PEMASUKAN",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 10.sp
                        ),
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    val prefix = if (isExpense) "−" else "+"
                    val amountColor = if (isExpense) StatusNegative else StatusPositive
                    Text(
                        text = prefix + Formatters.formatRupiah(totalExpense),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = amountColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${slices.size} KATEGORI",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            fontSize = 10.sp
                        ),
                        color = TextTertiary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Interactive Category Legend Items (Section 30)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            slices.forEachIndexed { index, slice ->
                val isSelected = selectedIndex == index

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) PebbleSurfaceVariant else Color.Transparent)
                        .border(
                            width = if (isSelected) 1.5.dp else 0.dp,
                            color = if (isSelected) DarkBorder else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable {
                            selectedIndex = if (isSelected) null else index
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(slice.color)
                                .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(3.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = slice.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                            ),
                            color = DarkSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = String.format(java.util.Locale.US, "%.1f%%", slice.percentage),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        val prefix = if (isExpense) "−" else "+"
                        val amountColor = if (isExpense) StatusNegative else StatusPositive
                        Text(
                            text = prefix + Formatters.formatRupiah(slice.amount),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = amountColor
                        )
                    }
                }
            }
        }
    }
}
