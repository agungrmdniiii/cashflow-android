package com.cashflow.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cashflow.app.ui.theme.*

/**
 * Standardized "Double-Bezel" Card Architecture for Duit Aing
 * All paddings and radii adhere to the strict 4-8-12-16-20-24-32dp scale.
 */
@Composable
fun DoubleBezelCard(
    modifier: Modifier = Modifier,
    outerPadding: Dp = AppSpacing.noneOrZero(),
    outerRadius: Dp = AppRadius.cardLg,
    innerRadius: Dp = AppRadius.card,
    shadowOffset: Dp = 0.dp,
    outerColor: Color = Color.Transparent,
    outerBorderColor: Color = Color.Transparent,
    innerColor: Color = MaterialTheme.duit.surface,
    innerBorderColor: Color = MaterialTheme.duit.border,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = remember(innerRadius) { RoundedCornerShape(innerRadius) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressShift by animateDpAsState(
        targetValue = if (isPressed && onClick != null && shadowOffset > 0.dp) shadowOffset else 0.dp,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f),
        label = "CardPressShift"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null && shadowOffset == 0.dp) 0.985f else 1.0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f),
        label = "CardPressScale"
    )

    val cardContent = @Composable {
        val baseModifier = if (onClick != null) {
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = pressShift.toPx()
                    translationY = pressShift.toPx()
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .clip(shape)
                .background(innerColor)
                .border(1.dp, innerBorderColor, shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                )
        } else {
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(innerColor)
                .border(1.dp, innerBorderColor, shape)
        }

        Column(
            modifier = baseModifier
        ) {
            content()
        }
    }

    if (shadowOffset > 0.dp) {
        Box(
            modifier = modifier
                .padding(end = shadowOffset, bottom = shadowOffset)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = shadowOffset, y = shadowOffset)
                    .clip(shape)
                    .background(MaterialTheme.duit.borderStrong.copy(alpha = 0.25f))
            )
            cardContent()
        }
    } else {
        Box(modifier = modifier) {
            cardContent()
        }
    }
}

private fun AppSpacing.noneOrZero(): Dp = 0.dp

/**
 * High-Contrast Dark Double-Bezel Card for Hero Focal Points
 */
@Composable
fun DarkDoubleBezelCard(
    modifier: Modifier = Modifier,
    outerPadding: Dp = 0.dp,
    outerRadius: Dp = AppRadius.cardLg,
    innerRadius: Dp = AppRadius.card,
    shadowOffset: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    DoubleBezelCard(
        modifier = modifier,
        outerPadding = outerPadding,
        outerRadius = outerRadius,
        innerRadius = innerRadius,
        shadowOffset = shadowOffset,
        outerColor = Color.Transparent,
        outerBorderColor = Color.Transparent,
        innerColor = MaterialTheme.duit.hero,
        innerBorderColor = MaterialTheme.duit.borderStrong,
        onClick = onClick,
        content = content
    )
}

/**
 * Signature Accent Double-Bezel Card for Highlights
 */
@Composable
fun AccentDoubleBezelCard(
    modifier: Modifier = Modifier,
    outerPadding: Dp = AppSpacing.xxs,
    outerRadius: Dp = AppRadius.card,
    innerRadius: Dp = AppRadius.input,
    shadowOffset: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    DoubleBezelCard(
        modifier = modifier,
        outerPadding = outerPadding,
        outerRadius = outerRadius,
        innerRadius = innerRadius,
        shadowOffset = shadowOffset,
        outerColor = MaterialTheme.duit.primaryContainer,
        outerBorderColor = MaterialTheme.duit.border,
        innerColor = MaterialTheme.duit.surface,
        innerBorderColor = MaterialTheme.duit.border,
        onClick = onClick,
        content = content
    )
}

enum class EyebrowVariant {
    DEFAULT,
    PRIMARY,
    POSITIVE,
    NEGATIVE,
    DARK
}

/**
 * Standardized Eyebrow Tag for High-End Spatial Hierarchy
 * Minimum font size 12sp, standard 4-8-12-16 padding.
 */
@Composable
fun EyebrowTag(
    text: String,
    modifier: Modifier = Modifier,
    variant: EyebrowVariant = EyebrowVariant.DEFAULT
) {
    val bg = when (variant) {
        EyebrowVariant.DEFAULT -> MaterialTheme.duit.surfaceVariant
        EyebrowVariant.PRIMARY -> MaterialTheme.duit.primaryContainer
        EyebrowVariant.POSITIVE -> MaterialTheme.duit.incomeContainer
        EyebrowVariant.NEGATIVE -> MaterialTheme.duit.expenseContainer
        EyebrowVariant.DARK -> MaterialTheme.duit.hero
    }

    val textCol = when (variant) {
        EyebrowVariant.DEFAULT -> MaterialTheme.duit.textPrimary
        EyebrowVariant.PRIMARY -> MaterialTheme.duit.onPrimaryContainer
        EyebrowVariant.POSITIVE -> MaterialTheme.duit.income
        EyebrowVariant.NEGATIVE -> MaterialTheme.duit.expense
        EyebrowVariant.DARK -> MaterialTheme.duit.onHero
    }

    val borderCol = when (variant) {
        EyebrowVariant.DEFAULT -> MaterialTheme.duit.border
        EyebrowVariant.PRIMARY -> MaterialTheme.duit.borderStrong
        EyebrowVariant.POSITIVE -> MaterialTheme.duit.income.copy(alpha = 0.35f)
        EyebrowVariant.NEGATIVE -> MaterialTheme.duit.expense.copy(alpha = 0.35f)
        EyebrowVariant.DARK -> MaterialTheme.duit.borderStrong
    }

    val pillShape = RoundedCornerShape(AppRadius.pill)
    Box(
        modifier = modifier
            .clip(pillShape)
            .background(bg)
            .border(1.dp, borderCol, pillShape)
            .padding(horizontal = AppSpacing.xs, vertical = AppSpacing.xxs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = textCol,
            maxLines = 1,
            softWrap = false
        )
    }
}
