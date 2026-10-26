package com.cashflow.app.ui.components.home

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.ui.theme.duit

/**
 * 4 Tombol Cepat Beranda (Tahap 2):
 * - Pengeluaran, Pemasukan, Transfer, Suara
 * - Ukuran sama besar (weight 1f)
 * - Label >= 12sp
 * - Target sentuh >= 48dp
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun QuickActions(
    onExpenseClick: () -> Unit,
    onIncomeClick: () -> Unit,
    onTransferClick: () -> Unit,
    onVoiceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        maxItemsInEachRow = if (LocalDensity.current.fontScale > 1.15f) 2 else 4
    ) {
        QuickActionButton(
            icon = Icons.AutoMirrored.Rounded.TrendingDown,
            label = "Pengeluaran",
            onClick = onExpenseClick,
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            icon = Icons.AutoMirrored.Rounded.TrendingUp,
            label = "Pemasukan",
            onClick = onIncomeClick,
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            icon = Icons.Rounded.SwapHoriz,
            label = "Transfer",
            onClick = onTransferClick,
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            icon = Icons.Rounded.Mic,
            label = "Suara",
            onClick = onVoiceClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val context = LocalContext.current
    val reduceMotion = remember {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reduceMotion) 0.94f else 1.0f,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "QuickActionScale_$label"
    )

    val duit = MaterialTheme.duit

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(duit.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = duit.textPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                letterSpacing = (-0.4).sp
            ),
            color = duit.textPrimary,
            textAlign = TextAlign.Center
        )
    }
}
