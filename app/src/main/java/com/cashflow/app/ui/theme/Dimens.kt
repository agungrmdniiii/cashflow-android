package com.cashflow.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standardized Design Tokens for Duit Aing
 * Spacing, Radius, Elevation, Touch Targets
 */
object AppSpacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
}

object AppRadius {
    val xs: Dp = 6.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val input: Dp = 14.dp     // Standard input field radius (14-16dp)
    val inputLg: Dp = 16.dp
    val card: Dp = 20.dp      // Standard card radius (20-24dp)
    val cardLg: Dp = 24.dp    // Double-bezel outer card radius
    val pill: Dp = 999.dp     // Fully rounded pill / chip
}

object AppElevation {
    val none: Dp = 0.dp
    val subtle: Dp = 1.dp
    val low: Dp = 2.dp
    val card: Dp = 3.dp
    val floating: Dp = 6.dp
}

object AppTouchTarget {
    val minSize: Dp = 44.dp   // WCAG AA recommended minimum touch target
    val comfortable: Dp = 48.dp
}
