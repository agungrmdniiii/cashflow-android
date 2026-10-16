package com.cashflow.app.ui.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.ui.theme.duit

/**
 * Standardized Settings Row Component
 * - Left: icon in rounded/circle container (size 38dp)
 * - Middle: Title (15sp, Bold) and Description (13sp, Secondary)
 * - Right: Action control (Switch, Button, Dropdown, etc.)
 * - Strict minimum 48dp touch target and accessibility semantics
 */
@Composable
fun SettingsItemRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    iconBackground: Color? = null,
    onClick: (() -> Unit)? = null,
    control: (@Composable () -> Unit)? = null
) {
    val duit = MaterialTheme.duit
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.98f else 1.0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f),
        label = "SettingsRowScale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClick()
                        }
                    )
                } else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = if (control != null) 12.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon != null) {
                val bg = iconBackground ?: duit.surfaceVariant
                val tint = iconTint ?: duit.textPrimary
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(bg)
                        .border(1.dp, duit.border, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 20.sp
                    ),
                    color = duit.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = duit.textSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (control != null) {
            Box(
                contentAlignment = Alignment.CenterEnd
            ) {
                control()
            }
        }
    }
}

/**
 * Standardized Section Container with Clean Header
 */
@Composable
fun SettingsSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val duit = MaterialTheme.duit
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                fontSize = 12.sp
            ),
            color = duit.textSecondary,
            modifier = Modifier.padding(start = 4.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(duit.surface)
                .border(1.dp, duit.border, RoundedCornerShape(12.dp)),
            content = content
        )
    }
}
