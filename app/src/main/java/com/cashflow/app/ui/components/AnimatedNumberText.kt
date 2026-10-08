package com.cashflow.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * Section 36: Tactile Mechanical Rolling Odometer Engine (Impeccable Polish)
 * Provides authentic, buttery-smooth rolling digit transitions.
 * - Zero horizontal jitter: Tabular figures ('tnum') ensure identical digit widths.
 * - Complete coverage: Every single digit (including '0') animates reliably.
 * - Unified vertical momentum: All digits slide in the same direction.
 * - Cascading stagger: Place values settle sequentially with mechanical precision.
 */
private val OdometerEasing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)

@Composable
fun AnimatedNumberText(
    value: Long,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    prefix: String = "",
    withPrefix: Boolean = true,
    maxLines: Int = 1,
    softWrap: Boolean = false
) {
    val tabularStyle = remember(style) {
        style.copy(fontFeatureSettings = "tnum")
    }

    val formatted = remember(value, prefix, withPrefix) {
        prefix + Formatters.formatRupiah(value, withPrefix)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        var currentDigitFromLeft = 0
        val len = formatted.length
        formatted.forEachIndexed { index, char ->
            if (char.isDigit()) {
                var digitPlaceFromRight = 0
                for (i in index + 1 until len) {
                    if (formatted[i].isDigit()) digitPlaceFromRight++
                }
                val indexFromLeft = currentDigitFromLeft
                currentDigitFromLeft++

                key("odometer_digit_$digitPlaceFromRight") {
                    OdometerDigitSlot(
                        digit = char,
                        style = tabularStyle,
                        color = color,
                        indexFromLeft = indexFromLeft
                    )
                }
            } else if (char == '.') {
                var digitsToRight = 0
                for (i in index + 1 until len) {
                    if (formatted[i].isDigit()) digitsToRight++
                }
                key("odometer_dot_$digitsToRight") {
                    Text(
                        text = ".",
                        style = tabularStyle,
                        color = color,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            } else {
                key("odometer_char_${index}_$char") {
                    Text(
                        text = char.toString(),
                        style = tabularStyle,
                        color = color,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

/**
 * Single aperture slot for an individual digit.
 * Slides vertically with mechanical easing and place-based cascading delay.
 */
@Composable
private fun OdometerDigitSlot(
    digit: Char,
    style: TextStyle,
    color: Color,
    indexFromLeft: Int,
    modifier: Modifier = Modifier
) {
    var displayedDigit by remember { mutableStateOf<Char?>(digit) }

    LaunchedEffect(digit) {
        displayedDigit = digit
    }

    val staggerDelay = (indexFromLeft.coerceAtMost(8) * 32)

    Box(
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = displayedDigit,
            transitionSpec = {
                (slideInVertically(
                    animationSpec = tween(
                        durationMillis = 460,
                        delayMillis = staggerDelay,
                        easing = OdometerEasing
                    )
                ) { height -> height } + fadeIn(
                    animationSpec = tween(180, delayMillis = staggerDelay)
                )).togetherWith(
                    slideOutVertically(
                        animationSpec = tween(
                            durationMillis = 460,
                            delayMillis = staggerDelay,
                            easing = OdometerEasing
                        )
                    ) { height -> -height } + fadeOut(
                        animationSpec = tween(180, delayMillis = staggerDelay)
                    )
                )
            },
            label = "OdometerDigit"
        ) { targetChar ->
            Text(
                text = (targetChar ?: ' ').toString(),
                style = style,
                color = if (targetChar != null) color else Color.Transparent,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
