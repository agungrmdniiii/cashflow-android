package com.cashflow.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cashflow.app.ui.theme.FintechAccentLight
import com.cashflow.app.ui.theme.FintechAccentMint
import com.cashflow.app.ui.theme.FintechPrimary
import com.cashflow.app.ui.theme.JadePrimary
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Native Jetpack Compose Motion Suite for "Duit Aing"
 * 100% Zero-Dependency Vector Choreography (Lottie-Grade Polish).
 */

/**
 * 1. Onboarding Motion: Flowing Cash & Floating Coins Animation
 * Used on Slide 1 ("Catat Cepat, Kelola Nyaman").
 */
@Composable
fun FlowingCashMotion(
    modifier: Modifier = Modifier,
    size: Dp = 220.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "flowingCashTransition")

    // Vertical floating bounce of main wallet/vault pedestal
    val walletFloat by infiniteTransition.animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "walletFloat"
    )

    // Pulsing aura scale
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraScale"
    )

    // Orbiting coin 1 (top-right)
    val coin1Offset by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "coin1Offset"
    )

    // Orbiting coin 2 (bottom-left)
    val coin2Offset by infiniteTransition.animateFloat(
        initialValue = 10f,
        targetValue = -12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "coin2Offset"
    )

    // Shimmer sweep across pedestal
    val shimmerSweep by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerSweep"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Background breathing aura
        Box(
            modifier = Modifier
                .size(size * 0.85f)
                .scale(auraScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF2E7557).copy(alpha = 0.45f),
                            Color(0xFF0F382A).copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Floating Center Pedestal
        Box(
            modifier = Modifier
                .size(size * 0.62f)
                .offset(y = walletFloat.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF348663),
                            Color(0xFF133E2B)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Rotating particle rim
            Canvas(modifier = Modifier.fillMaxSize().rotate(shimmerSweep)) {
                val strokeWidth = 2.dp.toPx()
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            FintechAccentMint.copy(alpha = 0.8f),
                            Color.Transparent,
                            FintechAccentLight.copy(alpha = 0.6f),
                            Color.Transparent
                        )
                    ),
                    radius = (this.size.minDimension / 2f) - strokeWidth,
                    style = Stroke(width = strokeWidth)
                )
            }

            // Wallet/Vault Geometric Graphic
            Canvas(modifier = Modifier.size(size * 0.32f)) {
                val w = this.size.width
                val h = this.size.height

                // Wallet body
                drawRoundRect(
                    color = Color.White,
                    size = Size(w, h * 0.75f),
                    topLeft = Offset(0f, h * 0.12f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx())
                )

                // Flap
                drawRoundRect(
                    color = FintechAccentMint,
                    size = Size(w * 0.45f, h * 0.36f),
                    topLeft = Offset(w * 0.55f, h * 0.32f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )

                // Coin dot clasp
                drawCircle(
                    color = Color(0xFF0F382A),
                    radius = 4.dp.toPx(),
                    center = Offset(w * 0.8f, h * 0.5f)
                )
            }
        }

        // Floating Coin 1 (Gold/Mint Top-Right)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-16).dp, y = (24 + coin1Offset).dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            FintechAccentMint,
                            Color(0xFF65A786)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF133E2B)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(FintechAccentMint)
                )
            }
        }

        // Floating Coin 2 (Soft Mint Bottom-Left)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 18.dp, y = (-26 + coin2Offset).dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            FintechAccentLight,
                            Color(0xFF86BC9F)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F382A)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(FintechAccentLight)
                )
            }
        }
    }
}

/**
 * 2. Onboarding Motion: Revolving Donut Budget Chart Animation
 * Used on Slide 2 ("Pantau Anggaran, Disiplin Finansial").
 */
@Composable
fun RevolvingDonutMotion(
    modifier: Modifier = Modifier,
    size: Dp = 220.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "revolvingDonutTransition")

    // Slow organic rotation
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "donutRotation"
    )

    // Pulse breathing of center percentage
    val centerScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "centerScale"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Outer glow
        Box(
            modifier = Modifier
                .size(size * 0.85f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF2E7557).copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Revolving Donut Canvas
        Canvas(
            modifier = Modifier
                .size(size * 0.65f)
                .rotate(rotation)
        ) {
            val strokeW = 16.dp.toPx()
            val canvasSize = this.size
            val arcSize = Size(canvasSize.width - strokeW, canvasSize.height - strokeW)
            val topLeft = Offset(strokeW / 2f, strokeW / 2f)

            // Segment 1: Mint Accent (40% - 144 deg)
            drawArc(
                color = FintechAccentMint,
                startAngle = 0f,
                sweepAngle = 138f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // Segment 2: Light Green (30% - 108 deg)
            drawArc(
                color = FintechAccentLight,
                startAngle = 146f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // Segment 3: Deep Emerald (20% - 72 deg)
            drawArc(
                color = Color(0xFF65A786),
                startAngle = 254f,
                sweepAngle = 66f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // Segment 4: White Highlight (10% - 36 deg)
            drawArc(
                color = Color.White,
                startAngle = 328f,
                sweepAngle = 26f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
        }

        // Center Target Core
        Box(
            modifier = Modifier
                .size(size * 0.36f)
                .scale(centerScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1B553D),
                            Color(0xFF0D2D1F)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(size * 0.18f)) {
                // Mini bar chart inside center core
                val w = this.size.width
                val h = this.size.height
                val barW = w * 0.2f

                drawRoundRect(
                    color = FintechAccentLight.copy(alpha = 0.7f),
                    topLeft = Offset(0f, h * 0.45f),
                    size = Size(barW, h * 0.55f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(w * 0.35f, h * 0.15f),
                    size = Size(barW, h * 0.85f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
                drawRoundRect(
                    color = FintechAccentMint,
                    topLeft = Offset(w * 0.7f, h * 0.3f),
                    size = Size(barW, h * 0.7f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }
        }
    }
}

/**
 * 3. Onboarding Motion: Shield Lock & Offline Security Vault
 * Used on Slide 3 ("100% Offline, Privasi Terjaga").
 */
@Composable
fun SecurityVaultMotion(
    modifier: Modifier = Modifier,
    size: Dp = 220.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "securityVaultTransition")

    // Shield vertical floating
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shieldFloat"
    )

    // Scanning radar line
    val radarAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarAngle"
    )

    // Shield pulse ring
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringScale"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Radar circular ring
        Box(
            modifier = Modifier
                .size(size * 0.85f)
                .scale(ringScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF2E7557).copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Radar sweep scanner
        Canvas(modifier = Modifier.size(size * 0.75f).rotate(radarAngle)) {
            val r = this.size.minDimension / 2f
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        FintechAccentMint.copy(alpha = 0.6f),
                        Color.Transparent
                    )
                ),
                radius = r,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        // Vault Core Pedestal
        Box(
            modifier = Modifier
                .size(size * 0.62f)
                .offset(y = floatOffset.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF2E7557),
                            Color(0xFF0F382A)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.3f)
            )

            // Inner Padlock Shackle Accent
            Canvas(modifier = Modifier.size(size * 0.14f)) {
                val w = this.size.width
                val h = this.size.height

                // Shackle loop
                drawArc(
                    color = Color(0xFF0F382A),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(w * 0.2f, 0f),
                    size = Size(w * 0.6f, h * 0.7f),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // Padlock base
                drawRoundRect(
                    color = Color(0xFF0F382A),
                    topLeft = Offset(w * 0.1f, h * 0.35f),
                    size = Size(w * 0.8f, h * 0.65f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )

                // Keyhole
                drawCircle(
                    color = FintechAccentMint,
                    radius = 2.dp.toPx(),
                    center = Offset(w * 0.5f, h * 0.6f)
                )
            }
        }
    }
}

/**
 * 4. Transaction Success Burst Motion
 * Choreographed draw-in checkmark + radiating ring particles on transaction save.
 */
@Composable
fun SuccessBurstMotion(
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
    onAnimationEnd: () -> Unit = {}
) {
    // Circle bloom animation
    val scaleAnim = remember { Animatable(0f) }
    // Checkmark draw-in progress (0f to 1f)
    val checkmarkAnim = remember { Animatable(0f) }
    // Particle ring expansion
    val particlesAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // 1. Pop scale the success circle
        scaleAnim.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        // 2. Draw in the checkmark path
        checkmarkAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        )
        // 3. Radiate outer burst
        particlesAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 350, easing = LinearOutSlowInEasing)
        )
        delay(250)
        onAnimationEnd()
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Outer particle burst
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pProgress = particlesAnim.value
            if (pProgress > 0f) {
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val baseRadius = this.size.minDimension * 0.38f
                val distance = baseRadius + (pProgress * 18.dp.toPx())
                val alpha = (1f - pProgress).coerceIn(0f, 1f)

                // 8 radiating burst dots
                for (i in 0 until 8) {
                    val angle = (i * 45f) * (Math.PI / 180f)
                    val x = center.x + (cos(angle) * distance).toFloat()
                    val y = center.y + (sin(angle) * distance).toFloat()
                    drawCircle(
                        color = FintechAccentMint.copy(alpha = alpha),
                        radius = (3.dp.toPx() * (1f - pProgress * 0.5f)).coerceAtLeast(1f),
                        center = Offset(x, y)
                    )
                }
            }
        }

        // Blooming Green Circle
        Box(
            modifier = Modifier
                .size(size * 0.72f)
                .scale(scaleAnim.value)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF246C4F),
                            FintechPrimary
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Draw-in Checkmark Vector
            Canvas(modifier = Modifier.size(size * 0.38f)) {
                val w = this.size.width
                val h = this.size.height

                val fullPath = Path().apply {
                    moveTo(w * 0.18f, h * 0.52f)
                    lineTo(w * 0.42f, h * 0.76f)
                    lineTo(w * 0.84f, h * 0.26f)
                }

                val pathMeasure = PathMeasure()
                pathMeasure.setPath(fullPath, false)
                val totalLength = pathMeasure.length

                val animatedPath = Path()
                pathMeasure.getSegment(
                    startDistance = 0f,
                    stopDistance = totalLength * checkmarkAnim.value,
                    destination = animatedPath,
                    startWithMoveTo = true
                )

                drawPath(
                    path = animatedPath,
                    color = Color.White,
                    style = Stroke(
                        width = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )
            }
        }
    }
}
