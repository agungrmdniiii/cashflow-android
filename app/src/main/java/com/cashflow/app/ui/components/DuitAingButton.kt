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
    shadowOffset: Dp = 0.dp,
    shapeRadius: Dp = 999.dp,
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

    val isDark = LocalIsDarkTheme.current
    val surfaceColor = when {
        !enabled -> PebbleBorder
        containerColor != null -> containerColor
        variant == DuitAingButtonVariant.PRIMARY -> if (isDark) FintechAccentLight else FintechPrimary
        variant == DuitAingButtonVariant.DARK -> if (isDark) PebbleSurfaceDark else FintechPrimary
        variant == DuitAingButtonVariant.OUTLINE || variant == DuitAingButtonVariant.SECONDARY -> PebbleSurface
        variant == DuitAingButtonVariant.DESTRUCTIVE || variant == DuitAingButtonVariant.DANGER -> StatusNegative
        else -> if (isDark) FintechAccentLight else FintechPrimary
    }

    val contentColor = when {
        !enabled -> TextTertiary
        contentColorOverride != null -> contentColorOverride
        containerColor != null -> {
            val luminance = (0.299 * containerColor.red + 0.587 * containerColor.green + 0.114 * containerColor.blue)
            if (luminance > 0.55) PebbleBackgroundDark else Color.White
        }
        variant == DuitAingButtonVariant.OUTLINE || variant == DuitAingButtonVariant.SECONDARY -> TextPrimary
        variant == DuitAingButtonVariant.PRIMARY && isDark -> PebbleBackgroundDark
        else -> TextOnDark
    }

    val borderColor = when {
        !enabled -> Color.Transparent
        variant == DuitAingButtonVariant.PRIMARY -> Color.Transparent
        variant == DuitAingButtonVariant.OUTLINE || variant == DuitAingButtonVariant.SECONDARY -> PebbleBorder
        variant == DuitAingButtonVariant.DARK -> Color.Transparent
        variant == DuitAingButtonVariant.DESTRUCTIVE || variant == DuitAingButtonVariant.DANGER -> Color.Transparent
        else -> Color.Transparent
    }

    val isCompact = height <= 48.dp
    val actualIconSize = if (isCompact) 16.dp else 18.dp
    val actualIconSpacing = if (isCompact) 6.dp else 8.dp
    val actualPadding = if (isCompact) 14.dp else 20.dp
    val actualFontSize = if (isCompact) 12.sp else 13.5.sp

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .height(height)
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
