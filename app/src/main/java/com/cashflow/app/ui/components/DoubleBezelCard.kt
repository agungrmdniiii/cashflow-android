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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.ui.theme.*

/**
 * High-End Visual Design: "Double-Bezel" (Doppelrand) Card Architecture
 *
 * Implements concentric nested enclosures that look like machined physical hardware:
 * - Outer Shell: Subtle background tint, hairline outer border (1dp), outer radius (18dp)
 * - Inner Core: Concentric inner surface with distinct background, 1.5dp DarkBorder, calculated radius (14dp)
 */
@Composable
fun DoubleBezelCard(
    modifier: Modifier = Modifier,
    outerPadding: Dp = 4.dp,
    outerRadius: Dp = 18.dp,
    innerRadius: Dp = 14.dp,
    shadowOffset: Dp = 0.dp,
    outerColor: Color = PebbleSurfaceVariant,
    outerBorderColor: Color = DarkBorder.copy(alpha = 0.20f),
    innerColor: Color = PebbleSurface,
    innerBorderColor: Color = DarkBorder.copy(alpha = 0.18f),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = remember(innerRadius) { RoundedCornerShape(innerRadius) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Tactile press mechanical physics with spring damping
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
            // Tactile soft offset shadow layer
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = shadowOffset, y = shadowOffset)
                    .clip(shape)
                    .background(DarkBorder.copy(alpha = 0.18f))
            )
            cardContent()
        }
    } else {
        Box(modifier = modifier) {
            cardContent()
        }
    }
}

/**
 * High-Contrast Dark Double-Bezel Card for Hero Focal Points
 */
@Composable
fun DarkDoubleBezelCard(
    modifier: Modifier = Modifier,
    outerPadding: Dp = 4.dp,
    outerRadius: Dp = 20.dp,
    innerRadius: Dp = 16.dp,
    shadowOffset: Dp = 3.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    DoubleBezelCard(
        modifier = modifier,
        outerPadding = outerPadding,
        outerRadius = outerRadius,
        innerRadius = innerRadius,
        shadowOffset = shadowOffset,
        outerColor = DarkBorder.copy(alpha = 0.12f),
        outerBorderColor = DarkBorder.copy(alpha = 0.22f),
        innerColor = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF161C19) else LightDarkSurface,
        innerBorderColor = DarkBorder.copy(alpha = 0.20f),
        onClick = onClick,
        content = content
    )
}

/**
 * Signature Jade Double-Bezel Card for Highlights
 */
@Composable
fun JadeDoubleBezelCard(
    modifier: Modifier = Modifier,
    outerPadding: Dp = 4.dp,
    outerRadius: Dp = 18.dp,
    innerRadius: Dp = 14.dp,
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
        outerColor = JadePrimaryLight,
        outerBorderColor = JadePrimary.copy(alpha = 0.30f),
        innerColor = PebbleSurface,
        innerBorderColor = DarkBorder.copy(alpha = 0.18f),
        onClick = onClick,
        content = content
    )
}

enum class EyebrowVariant {
    DEFAULT,
    JADE,
    POSITIVE,
    NEGATIVE,
    DARK
}

/**
 * Microscopic Eyebrow Tag for High-End Spatial Hierarchy
 */
@Composable
fun EyebrowTag(
    text: String,
    modifier: Modifier = Modifier,
    variant: EyebrowVariant = EyebrowVariant.DEFAULT
) {
    val bg = when (variant) {
        EyebrowVariant.DEFAULT -> PebbleSurfaceVariant
        EyebrowVariant.JADE -> JadePrimaryLight
        EyebrowVariant.POSITIVE -> StatusPositiveContainer
        EyebrowVariant.NEGATIVE -> StatusNegativeContainer
        EyebrowVariant.DARK -> DarkSurface
    }

    val textCol = when (variant) {
        EyebrowVariant.DEFAULT -> TextPrimary
        EyebrowVariant.JADE -> JadePrimaryDark
        EyebrowVariant.POSITIVE -> StatusPositive
        EyebrowVariant.NEGATIVE -> StatusNegative
        EyebrowVariant.DARK -> TextOnDark
    }

    val borderCol = when (variant) {
        EyebrowVariant.DEFAULT -> DarkBorder.copy(alpha = 0.20f)
        EyebrowVariant.JADE -> JadePrimary.copy(alpha = 0.35f)
        EyebrowVariant.POSITIVE -> StatusPositive.copy(alpha = 0.35f)
        EyebrowVariant.NEGATIVE -> StatusNegative.copy(alpha = 0.35f)
        EyebrowVariant.DARK -> DarkBorder.copy(alpha = 0.30f)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(bg)
            .border(1.dp, borderCol, RoundedCornerShape(100.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 9.5.sp,
                letterSpacing = 1.2.sp
            ),
            color = textCol,
            maxLines = 1,
            softWrap = false
        )
    }
}
