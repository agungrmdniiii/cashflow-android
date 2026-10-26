package com.cashflow.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cashflow.app.ui.theme.*

data class SegmentItem<T>(
    val value: T,
    val label: String,
    val badgeCount: Int = 0,
    val icon: (@Composable () -> Unit)? = null
)

/**
 * Standardized Unified Segmented Control for Duit Aing
 * Emulates precision hardware toggles with smooth animated transitions,
 * concentric pill container, and tactile tokens.
 */
@Composable
fun <T> DuitAingSegmentedControl(
    items: List<SegmentItem<T>>,
    selectedValue: T,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = AppTouchTarget.comfortable
) {
    val duit = MaterialTheme.duit
    val pillShape = RoundedCornerShape(AppRadius.pill)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(pillShape)
            .background(duit.surfaceVariant)
            .border(1.dp, duit.border, pillShape)
            .padding(AppSpacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val isSelected = item.value == selectedValue
            val activeBg by animateColorAsState(
                targetValue = if (isSelected) duit.primary else Color.Transparent,
                animationSpec = tween(durationMillis = 180, easing = DuitAingMotion.SubtleEasing),
                label = "SegmentBg_${item.label}"
            )
            val activeText by animateColorAsState(
                targetValue = if (isSelected) duit.onPrimary else duit.textSecondary,
                animationSpec = tween(durationMillis = 180, easing = DuitAingMotion.SubtleEasing),
                label = "SegmentText_${item.label}"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = maxOf(height - AppSpacing.xs, 48.dp))
                    .clip(pillShape)
                    .background(activeBg)
                    .clickable(
                        role = Role.Tab,
                        onClick = { onValueChange(item.value) }
                    )
                    .padding(horizontal = AppSpacing.xs, vertical = AppSpacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
                ) {
                    item.icon?.invoke()
                    Text(
                        text = item.label,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = activeText,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (item.badgeCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(pillShape)
                                .background(if (isSelected) duit.onPrimary.copy(alpha = 0.2f) else duit.expense)
                                .padding(horizontal = AppSpacing.xs, vertical = AppSpacing.xxs)
                        ) {
                            Text(
                                text = "${item.badgeCount}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (isSelected) duit.onPrimary else Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}
