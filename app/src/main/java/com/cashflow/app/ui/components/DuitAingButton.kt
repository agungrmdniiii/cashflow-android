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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.ui.theme.*

enum class DuitAingButtonVariant {
    PRIMARY,     // JadePrimary surface, TextOnDark
    SECONDARY,   // PebbleSurface, DarkBorder, TextPrimary
    DARK,        // DarkSurface, TextOnDark
    OUTLINE,     // PebbleSurface, DarkBorder, TextPrimary
    DANGER,      // StatusNegative, TextOnDark
    DESTRUCTIVE  // StatusNegative, TextOnDark
}

/**
 * Reusable Neo-Brutalist Button for "Duit Aing"
 *
 * Specifications from High-End Visual Design:
 * - Single border outline (1.5dp DarkBorder)
 * - Hard offset shadow (solid 3dp dark background block)
 * - Tactile mechanical press effect: on press, surface shifts with Spring Physics
 * - Button-in-Button Trailing Icon: enclosed in a concentric island badge wrapper
 * - Compact, substantial height (48-52dp)
 * - Guarantees Icon + Text + Trailing Icon stay on a SINGLE horizontal line
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
    height: Dp = 50.dp,
    shadowOffset: Dp = 3.dp,
    shapeRadius: Dp = 8.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Tactile press offset animation: Spring physics (dampingRatio = 0.7f, stiffness = 450f)
    val pressShift by animateDpAsState(
        targetValue = if (isPressed && enabled && !isLoading) shadowOffset else 0.dp,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f),
        label = "ButtonPressShift"
    )

    val shape = RoundedCornerShape(shapeRadius)

    val surfaceColor = when {
        !enabled -> PebbleBorder
        variant == DuitAingButtonVariant.PRIMARY -> JadePrimary
        variant == DuitAingButtonVariant.DARK -> DarkSurface
        variant == DuitAingButtonVariant.OUTLINE || variant == DuitAingButtonVariant.SECONDARY -> PebbleSurface
        variant == DuitAingButtonVariant.DESTRUCTIVE || variant == DuitAingButtonVariant.DANGER -> StatusNegative
        else -> JadePrimary
    }

    val contentColor = when {
        !enabled -> TextTertiary
        variant == DuitAingButtonVariant.OUTLINE || variant == DuitAingButtonVariant.SECONDARY -> TextPrimary
        else -> TextOnDark
    }

    val borderColor = when {
        !enabled -> DarkBorder.copy(alpha = 0.15f)
        variant == DuitAingButtonVariant.PRIMARY -> JadePrimaryDark.copy(alpha = 0.40f)
        variant == DuitAingButtonVariant.OUTLINE || variant == DuitAingButtonVariant.SECONDARY -> DarkBorder.copy(alpha = 0.22f)
        variant == DuitAingButtonVariant.DARK -> DarkBorder.copy(alpha = 0.35f)
        variant == DuitAingButtonVariant.DESTRUCTIVE || variant == DuitAingButtonVariant.DANGER -> StatusNegative.copy(alpha = 0.40f)
        else -> DarkBorder.copy(alpha = 0.22f)
    }

    val isCompact = height <= 48.dp
    val actualIconSize = if (isCompact) 16.dp else 18.dp
    val actualIconSpacing = if (isCompact) 6.dp else 8.dp
    val actualPadding = if (isCompact) 10.dp else 16.dp
    val actualFontSize = if (isCompact) 11.5.sp else 13.sp

    // Outer container with padding allocated for the tactile offset shadow
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .height(height + shadowOffset)
            .padding(end = shadowOffset, bottom = shadowOffset),
        propagateMinConstraints = true
    ) {
        // 1. TACTILE OFFSET SHADOW LAYER (Soft blended shadow)
        if (enabled && shadowOffset > 0.dp) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = shadowOffset, y = shadowOffset)
                    .clip(shape)
                    .background(DarkBorder.copy(alpha = 0.18f))
            )
        }

        // 2. MAIN BUTTON SURFACE LAYER
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .offset(x = pressShift, y = pressShift)
                .clip(shape)
                .background(surfaceColor)
                .border(1.dp, borderColor, shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null, // Custom tactile mechanical shift replaces generic ripple
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
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp,
                            fontSize = actualFontSize
                        ),
                        color = contentColor,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (trailingIcon != null) {
                        Spacer(modifier = Modifier.width(if (isCompact) 8.dp else 10.dp))
                        val badgeBg = when (variant) {
                            DuitAingButtonVariant.PRIMARY -> Color.White.copy(alpha = 0.18f)
                            DuitAingButtonVariant.DARK -> Color.White.copy(alpha = 0.15f)
                            DuitAingButtonVariant.DESTRUCTIVE, DuitAingButtonVariant.DANGER -> Color.White.copy(alpha = 0.2f)
                            else -> DarkBorder.copy(alpha = 0.08f)
                        }
                        val badgeSize = if (isCompact) 20.dp else 24.dp
                        val badgeIconSize = if (isCompact) 11.dp else 13.dp
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
}
