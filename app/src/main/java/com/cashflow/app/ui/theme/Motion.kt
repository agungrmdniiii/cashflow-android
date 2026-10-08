package com.cashflow.app.ui.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.*

/**
 * Global Motion System for "Duit Aing"
 * Section 6: FAST (~150-250ms), SMOOTH, SUBTLE, TACTILE.
 * No sluggish or bouncy slideshow animations.
 */
object DuitAingMotion {
    const val DURATION_FAST = 150
    const val DURATION_STANDARD = 220
    const val DURATION_DELIBERATE = 300

    val SubtleEasing = FastOutSlowInEasing
    val DecelEasing = FastOutLinearInEasing

    fun <T> fastTween(): TweenSpec<T> = tween(
        durationMillis = DURATION_FAST,
        easing = SubtleEasing
    )

    fun <T> standardTween(): TweenSpec<T> = tween(
        durationMillis = DURATION_STANDARD,
        easing = SubtleEasing
    )

    fun <T> springTactile(): SpringSpec<T> = spring(
        dampingRatio = 0.75f,
        stiffness = 500f
    )

    // Screen Transition: subtle fade + slight vertical shift (Section 7)
    val ScreenEnterTransition: EnterTransition = fadeIn(
        animationSpec = tween(durationMillis = DURATION_STANDARD, easing = SubtleEasing)
    ) + slideInVertically(
        animationSpec = tween(durationMillis = DURATION_STANDARD, easing = SubtleEasing),
        initialOffsetY = { fullHeight -> (fullHeight * 0.03f).toInt() }
    )

    val ScreenExitTransition: ExitTransition = fadeOut(
        animationSpec = tween(durationMillis = DURATION_FAST, easing = SubtleEasing)
    )
}
