package com.cashflow.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Global Unified Design Tokens for "Duit Aing"
 * Strict 4-8-12-16-20-24-32dp scale.
 */
object AppSpacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 20.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp

    // Semantic Spacing
    val screenHorizontalPadding: Dp = 20.dp
    val sectionSpacing: Dp = 20.dp
    val itemSpacing: Dp = 12.dp
    val cardInternalPadding: Dp = 16.dp
    val rowContentSpacing: Dp = 12.dp
}

typealias DuitAingSpacing = AppSpacing

object AppRadius {
    val xs: Dp = 4.dp         // Normalized from 6dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val input: Dp = 16.dp     // Normalized from 14dp
    val inputLg: Dp = 16.dp
    val card: Dp = 20.dp      // Standard card radius
    val cardLg: Dp = 24.dp    // Normalized from 22dp
    val pill: Dp = 999.dp     // Fully rounded pill / chip
}

object AppElevation {
    val none: Dp = 0.dp
    val subtle: Dp = 1.dp
    val low: Dp = 2.dp
    val card: Dp = 4.dp       // Normalized from 3dp
    val floating: Dp = 8.dp   // Normalized from 6dp
}

object AppTouchTarget {
    val minSize: Dp = 44.dp   // WCAG AA recommended minimum touch target
    val comfortable: Dp = 48.dp
}
