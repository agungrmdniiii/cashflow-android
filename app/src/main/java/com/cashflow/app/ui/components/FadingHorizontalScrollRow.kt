package com.cashflow.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.cashflow.app.ui.theme.duit

/**
 * Reusable horizontal scroll container with edge-fading gradients and generous trailing padding.
 * Prevents abrupt chip clipping at the viewport edge and visually indicates scrollability.
 */
@Composable
fun FadingHorizontalScrollRow(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    fadeColor: Color = MaterialTheme.duit.background,
    fadeWidth: Dp = 20.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(contentPadding),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment
        ) {
            content()
            // Extra safety spacer at the end to ensure the last chip never snaps against the screen border
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Left edge fade gradient when scrolled forward
        if (scrollState.canScrollBackward) {
            Box(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(fadeWidth)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(fadeColor, Color.Transparent)
                            )
                        )
                )
            }
        }

        // Right edge fade gradient when more content can be scrolled
        if (scrollState.canScrollForward) {
            Box(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(fadeWidth)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, fadeColor)
                            )
                        )
                )
            }
        }
    }
}
