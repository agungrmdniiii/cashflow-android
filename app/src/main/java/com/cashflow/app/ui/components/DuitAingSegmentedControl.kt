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
import androidx.compose.ui.unit.sp
import com.cashflow.app.ui.theme.*

data class SegmentItem<T>(
    val value: T,
    val label: String,
    val badgeCount: Int = 0,
    val icon: (@Composable () -> Unit)? = null
)

/**
 * High-End Unified Segmented Control for Duit Aing
 * Emulates precision hardware toggles with smooth animated transitions,
 * concentric pill container, and tactile spring feedback.
 */
@Composable
fun <T> DuitAingSegmentedControl(
    items: List<SegmentItem<T>>,
    selectedValue: T,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp
) {
    val isDark = LocalIsDarkTheme.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(PebbleSurface)
            .border(1.dp, PebbleBorder, RoundedCornerShape(999.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val isSelected = item.value == selectedValue
            val activeBg by animateColorAsState(
                targetValue = if (isSelected) (if (isDark) FintechAccentLight else FintechPrimary) else Color.Transparent,
                animationSpec = tween(durationMillis = 180, easing = DuitAingMotion.SubtleEasing),
                label = "SegmentBg_${item.label}"
            )
            val activeText by animateColorAsState(
                targetValue = if (isSelected) (if (isDark) PebbleBackgroundDark else TextOnDark) else DarkSurface,
                animationSpec = tween(durationMillis = 180, easing = DuitAingMotion.SubtleEasing),
                label = "SegmentText_${item.label}"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(height - 8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(activeBg)
                    .clickable(
                        role = Role.Tab,
                        onClick = { onValueChange(item.value) }
                    )
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    item.icon?.invoke()
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            fontSize = 11.5.sp,
                            letterSpacing = 0.3.sp
                        ),
                        color = activeText,
                        maxLines = 1
                    )
                    if (item.badgeCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(if (isSelected) (if (isDark) FintechPrimaryDark else FintechAccentLight) else StatusNegative)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${item.badgeCount}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                color = if (isSelected) (if (isDark) FintechAccentLight else Color.White) else Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
