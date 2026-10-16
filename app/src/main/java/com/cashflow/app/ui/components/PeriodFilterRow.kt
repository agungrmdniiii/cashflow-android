package com.cashflow.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cashflow.app.data.model.PeriodFilter
import com.cashflow.app.ui.theme.*

@Composable
fun PeriodFilterRow(
    selectedPeriod: PeriodFilter,
    onPeriodSelected: (PeriodFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSheet by remember { mutableStateOf(false) }
    val duit = MaterialTheme.duit

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val chipShape = RoundedCornerShape(AppRadius.xs)

        PeriodFilter.entries.forEach { period ->
            val isSelected = period == selectedPeriod
            val bg by animateColorAsState(
                targetValue = if (isSelected) duit.primary else duit.surface,
                animationSpec = tween(durationMillis = 180),
                label = "chip_bg"
            )
            val textCol by animateColorAsState(
                targetValue = if (isSelected) duit.onPrimary else duit.textSecondary,
                animationSpec = tween(durationMillis = 180),
                label = "chip_text"
            )
            val borderCol by animateColorAsState(
                targetValue = if (isSelected) duit.primary else duit.border,
                animationSpec = tween(durationMillis = 180),
                label = "chip_border"
            )

            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(chipShape)
                    .background(bg)
                    .border(1.dp, borderCol, chipShape)
                    .clickable(role = Role.Button) { onPeriodSelected(period) }
                    .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = period.displayName,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = textCol
                )
            }
        }

        // Quick Bottom Sheet selector trigger (Accessible >= 48dp touch target)
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(chipShape)
                .background(duit.surfaceVariant)
                .border(1.dp, duit.border, chipShape)
                .clickable(role = Role.Button) { showSheet = true }
                .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = "Pilih Periode Lengkap",
                    tint = duit.textPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "PILIH",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = duit.textPrimary
                )
            }
        }
    }

    if (showSheet) {
        PeriodSelectionSheet(
            visible = showSheet,
            selectedPeriod = selectedPeriod,
            onPeriodSelected = { period ->
                onPeriodSelected(period)
                showSheet = false
            },
            onDismissRequest = { showSheet = false }
        )
    }
}
