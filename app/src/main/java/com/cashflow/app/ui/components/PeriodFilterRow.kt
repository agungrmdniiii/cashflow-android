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
import androidx.compose.ui.unit.sp
import com.cashflow.app.data.model.PeriodFilter
import com.cashflow.app.ui.theme.*

@Composable
fun PeriodFilterRow(
    selectedPeriod: PeriodFilter,
    onPeriodSelected: (PeriodFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSheet by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PeriodFilter.entries.forEach { period ->
            val isSelected = period == selectedPeriod
            val bg by animateColorAsState(
                targetValue = if (isSelected) DarkSurface else PebbleSurface,
                animationSpec = tween(durationMillis = 180),
                label = "chip_bg"
            )
            val textCol by animateColorAsState(
                targetValue = if (isSelected) TextOnDark else TextSecondary,
                animationSpec = tween(durationMillis = 180),
                label = "chip_text"
            )
            val borderCol by animateColorAsState(
                targetValue = if (isSelected) DarkBorder.copy(alpha = 0.35f) else PebbleBorder,
                animationSpec = tween(durationMillis = 180),
                label = "chip_border"
            )

            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(6.dp))
                    .background(bg)
                    .border(1.dp, borderCol, RoundedCornerShape(6.dp))
                    .clickable(role = Role.Button) { onPeriodSelected(period) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = period.displayName.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    ),
                    color = textCol
                )
            }
        }

        // Quick Bottom Sheet selector trigger (Accessible >= 48dp touch target)
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(6.dp))
                .background(PebbleSurfaceVariant)
                .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(6.dp))
                .clickable(role = Role.Button) { showSheet = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = "Pilih Periode Lengkap",
                    tint = DarkSurface,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "PILIH",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        letterSpacing = 0.5.sp
                    ),
                    color = DarkSurface
                )
            }
        }
    }

    PeriodSelectionSheet(
        visible = showSheet,
        selectedPeriod = selectedPeriod,
        onPeriodSelected = onPeriodSelected,
        onDismissRequest = { showSheet = false }
    )
}
