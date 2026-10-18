package com.cashflow.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em

/** One text layout per amount; animate value changes without a layout and layer per digit. */
@Composable
fun AnimatedNumberText(
    value: Long,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    prefix: String = "",
    withPrefix: Boolean = true,
    dimThousands: Boolean = false,
    maxLines: Int = 1,
    softWrap: Boolean = false
) {
    val tabularStyle = remember(style) { style.copy(fontFeatureSettings = "tnum") }
    val formatted = remember(value, prefix, withPrefix) {
        prefix + Formatters.formatRupiah(value, withPrefix)
    }
    AnimatedContent(
        targetState = formatted,
        modifier = modifier.clearAndSetSemantics { contentDescription = formatted },
        transitionSpec = {
            (fadeIn(tween(180)) togetherWith fadeOut(tween(120))).using(null)
        },
        label = "AmountChange"
    ) { target ->
        val text = remember(target, dimThousands, color) {
            buildAnnotatedString {
                if (dimThousands) {
                    val suffixStart = target.indexOfLast { !it.isDigit() } + 1
                    append(target.substring(0, suffixStart))
                    withStyle(SpanStyle(fontSize = 0.72.em, color = color.copy(alpha = 0.65f))) {
                        append(target.substring(suffixStart))
                    }
                } else append(target)
            }
        }
        Text(text = text, style = tabularStyle, color = color, maxLines = maxLines, softWrap = softWrap)
    }
}
