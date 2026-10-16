package com.cashflow.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.ui.theme.*

enum class DuitAingButtonVariant {
    PRIMARY,     // duit.primary, duit.onPrimary
    SECONDARY,   // duit.surface, duit.border, duit.textPrimary
    DARK,        // duit.hero, duit.onHero
    OUTLINE,     // duit.surface, duit.borderStrong, duit.textPrimary
    DANGER,      // duit.expense, duit.onPrimary
    DESTRUCTIVE  // duit.expense, duit.onPrimary
}

/**
 * Standardized Neo-Brutalist Button for "Duit Aing"
 * Strictly uses design tokens and 4-8-12-16-20-24-32dp scale.
 */
@Composable
fun DuitAingButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: DuitAingButtonVariant = DuitAingButtonVariant.PRIMARY,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    height: Dp = AppTouchTarget.comfortable, // 48dp standard touch target
    shadowOffset: Dp = 0.dp,
    shapeRadius: Dp = AppRadius.pill,
    containerColor: Color? = null,
    contentColorOverride: Color? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed && enabled && !isLoading) 0.97f else 1.0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f),
        label = "ButtonPressScale"
    )

    val shape = RoundedCornerShape(shapeRadius)
    val duit = MaterialTheme.duit

    val surfaceColor = when {
        !enabled -> duit.track
        containerColor != null -> containerColor
        variant == DuitAingButtonVariant.PRIMARY -> duit.primary
        variant == DuitAingButtonVariant.DARK -> duit.hero
        variant == DuitAingButtonVariant.SECONDARY -> duit.surfaceVariant
        variant == DuitAingButtonVariant.OUTLINE -> duit.surface
        variant == DuitAingButtonVariant.DESTRUCTIVE || variant == DuitAingButtonVariant.DANGER -> duit.expense
        else -> duit.primary
    }

    val contentColor = when {
        !enabled -> duit.textTertiary
        contentColorOverride != null -> contentColorOverride
        containerColor != null -> {
            val luminance = (0.299 * containerColor.red + 0.587 * containerColor.green + 0.114 * containerColor.blue)
            if (luminance > 0.55) LightColors.primary else Color.White
        }
        variant == DuitAingButtonVariant.SECONDARY || variant == DuitAingButtonVariant.OUTLINE -> duit.textPrimary
        variant == DuitAingButtonVariant.PRIMARY -> duit.onPrimary
        variant == DuitAingButtonVariant.DARK -> duit.onHero
        variant == DuitAingButtonVariant.DESTRUCTIVE || variant == DuitAingButtonVariant.DANGER -> Color.White
        else -> duit.onPrimary
    }

    val borderColor = when {
        !enabled -> Color.Transparent
        variant == DuitAingButtonVariant.PRIMARY -> Color.Transparent
        variant == DuitAingButtonVariant.OUTLINE -> duit.borderStrong
        variant == DuitAingButtonVariant.SECONDARY -> Color.Transparent
        variant == DuitAingButtonVariant.DARK -> Color.Transparent
        variant == DuitAingButtonVariant.DESTRUCTIVE || variant == DuitAingButtonVariant.DANGER -> Color.Transparent
        else -> Color.Transparent
    }

    val isCompact = height <= AppTouchTarget.comfortable
    val actualIconSize = if (isCompact) 16.dp else 20.dp
    val actualIconSpacing = AppSpacing.xs // 8dp
    val actualPadding = if (isCompact) AppSpacing.md else AppSpacing.lg // 16dp / 20dp
    val actualFontSize = if (isCompact) 13.sp else 14.sp

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = AppTouchTarget.comfortable, minHeight = AppTouchTarget.comfortable)
            .heightIn(min = maxOf(height, 48.dp))
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(shape)
            .background(surfaceColor)
            .then(if (borderColor != Color.Transparent) Modifier.border(1.dp, borderColor, shape) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !isLoading,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = actualPadding),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(if (isCompact) 16.dp else 20.dp),
                color = contentColor,
                strokeWidth = 2.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(actualIconSize)
                    )
                    Spacer(modifier = Modifier.width(actualIconSpacing))
                }

                Text(
                    text = text,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp,
                        fontSize = actualFontSize
                    ),
                    color = contentColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )

                if (trailingIcon != null) {
                    Spacer(modifier = Modifier.width(AppSpacing.xs))
                    val badgeBg = when (variant) {
                        DuitAingButtonVariant.PRIMARY -> duit.onPrimary.copy(alpha = 0.18f)
                        DuitAingButtonVariant.DARK -> duit.onHero.copy(alpha = 0.15f)
                        DuitAingButtonVariant.DESTRUCTIVE, DuitAingButtonVariant.DANGER -> Color.White.copy(alpha = 0.2f)
                        else -> duit.borderStrong.copy(alpha = 0.2f)
                    }
                    val badgeSize = if (isCompact) 20.dp else 24.dp
                    val badgeIconSize = 12.dp
                    Box(
                        modifier = Modifier
                            .size(badgeSize)
                            .clip(CircleShape)
                            .background(badgeBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = trailingIcon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(badgeIconSize)
                        )
                    }
                }
            }
        }
    }
}
