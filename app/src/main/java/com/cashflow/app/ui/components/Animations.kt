package com.cashflow.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.ui.theme.DarkBorder
import com.cashflow.app.ui.theme.JadePrimary
import com.cashflow.app.ui.theme.PebbleSurface
import com.cashflow.app.ui.theme.PebbleSurfaceVariant
import com.cashflow.app.ui.theme.TextPrimary
import com.cashflow.app.ui.theme.TextSecondary

/**
 * Shimmer gradient modifier for skeleton loading states.
 * 100% native Jetpack Compose code-driven animation with zero external dependencies.
 */
@Composable
fun Modifier.tactileShimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "tactileShimmerTransition")
    val translateAnim by transition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "tactileShimmerTranslate"
    )

    val shimmerColors = listOf(
        PebbleSurfaceVariant.copy(alpha = 0.45f),
        PebbleSurfaceVariant.copy(alpha = 0.9f),
        PebbleSurfaceVariant.copy(alpha = 0.45f)
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim, translateAnim),
        end = Offset(translateAnim + 250f, translateAnim + 250f)
    )

    return this.background(brush)
}

/**
 * Animated Empty State with tactile Neo-Brutalist floating icon and breathing pulse rings.
 * Provides interactive visual feedback when no data is available.
 */
@Composable
fun AnimatedEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    actionIcon: ImageVector? = null,
    onActionClick: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "emptyStateTransition")

    // Smooth vertical floating animation (simulating buoyancy)
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emptyStateFloat"
    )

    // Breathing pulse scale for background halo
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emptyStatePulse"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emptyStateAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Floating Tactile Icon Container with Breathing Aura
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(96.dp)
                .offset(y = floatOffset.dp)
        ) {
            // Concentric Breathing Glow Ring
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(JadePrimary.copy(alpha = pulseAlpha))
            )

            // Inner Accent Ring
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(PebbleSurfaceVariant.copy(alpha = 0.5f))
            )

            // Main Neo-Brutalist Icon Container with Solid Border
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PebbleSurface)
                    .border(1.5.dp, DarkBorder, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = JadePrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Title
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                fontSize = 15.sp
            ),
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Description
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 12.5.sp,
                lineHeight = 17.sp
            ),
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.85f)
        )

        // Action Button
        if (actionText != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(14.dp))
            DuitAingButton(
                text = actionText,
                onClick = onActionClick,
                leadingIcon = actionIcon,
                variant = DuitAingButtonVariant.SECONDARY,
                height = 42.dp,
                shadowOffset = 2.dp
            )
        }
    }
}

/**
 * Skeleton Card Loading Placeholder with tactile shimmer effect.
 */
@Composable
fun ShimmerCardPlaceholder(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 76.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, DarkBorder.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .tactileShimmer()
    )
}
