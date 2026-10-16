package com.cashflow.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme

val LocalIsDarkTheme = compositionLocalOf { false }

// ============================================================================
// DUIT AING "MONO DAN LIME" DESIGN TOKENS
// ============================================================================

data class DuitAingColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val border: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val accentLime: Color,
    val onAccentLime: Color,
    val hero: Color,
    val onHero: Color,
    val onHeroSecondary: Color,
    val heroAccent: Color,
    val income: Color,
    val incomeContainer: Color,
    val expense: Color,
    val expenseContainer: Color,
    val transfer: Color,
    val transferContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val track: Color,
    val chartInactive: Color,
    val chartOther: Color,
    val chart: List<Color>
)

val LightColors = DuitAingColors(
    background = Color(0xFFF4F4F1),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEDEDE8),
    border = Color(0xFFE6E6E1),
    borderStrong = Color(0xFFD6D6D0),
    textPrimary = Color(0xFF111111),
    textSecondary = Color(0xFF5A5A55),
    textTertiary = Color(0xFF666661),
    primary = Color(0xFF111111),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEDEDE8),
    onPrimaryContainer = Color(0xFF111111),
    accentLime = Color(0xFFC8F25A),
    onAccentLime = Color(0xFF111111),
    hero = Color(0xFF111111),
    onHero = Color(0xFFFFFFFF),
    onHeroSecondary = Color(0xFFB8B8B2),
    heroAccent = Color(0xFFC8F25A),
    income = Color(0xFF0A7559),
    incomeContainer = Color(0xFFE3F3EC),
    expense = Color(0xFFC42B30),
    expenseContainer = Color(0xFFFBE9E9),
    transfer = Color(0xFF2563EB),
    transferContainer = Color(0xFFE8EFFD),
    warning = Color(0xFFA64A07),
    warningContainer = Color(0xFFFBEEDD),
    track = Color(0xFFE4E4DE),
    chartInactive = Color(0xFFD6D6D0),
    chartOther = Color(0xFF8A8A82),
    chart = listOf(
        Color(0xFF111111),
        Color(0xFF6F8F00),
        Color(0xFF2563EB),
        Color(0xFFC9731A),
        Color(0xFF7C4DDB),
        Color(0xFF0E8F80)
    )
)

val DarkColors = DuitAingColors(
    background = Color(0xFF0C0C0C),
    surface = Color(0xFF161616),
    surfaceVariant = Color(0xFF1F1F1F),
    border = Color(0xFF2B2B2B),
    borderStrong = Color(0xFF3A3A3A),
    textPrimary = Color(0xFFF2F2EE),
    textSecondary = Color(0xFF9C9C96),
    textTertiary = Color(0xFF8C8C86),
    primary = Color(0xFFC8F25A),
    onPrimary = Color(0xFF111111),
    primaryContainer = Color(0xFF232B10),
    onPrimaryContainer = Color(0xFFC8F25A),
    accentLime = Color(0xFFC8F25A),
    onAccentLime = Color(0xFF111111),
    hero = Color(0xFFC8F25A),
    onHero = Color(0xFF111111),
    onHeroSecondary = Color(0xFF3A4A0A),
    heroAccent = Color(0xFF111111),
    income = Color(0xFF3DD6A5),
    incomeContainer = Color(0xFF11291F),
    expense = Color(0xFFFF7A80),
    expenseContainer = Color(0xFF2E1517),
    transfer = Color(0xFF6EA8FF),
    transferContainer = Color(0xFF14223A),
    warning = Color(0xFFF5B84B),
    warningContainer = Color(0xFF2E2410),
    track = Color(0xFF2B2B2B),
    chartInactive = Color(0xFF2B2B2B),
    chartOther = Color(0xFF8E8E88),
    chart = listOf(
        Color(0xFFC8F25A),
        Color(0xFF6EA8FF),
        Color(0xFFFFB454),
        Color(0xFFB69CFF),
        Color(0xFF4FD1C5),
        Color(0xFFFF8FB1)
    )
)

val LocalDuitAingColors = staticCompositionLocalOf { LightColors }

val MaterialTheme.duit: DuitAingColors
    @Composable
    @ReadOnlyComposable
    get() = LocalDuitAingColors.current

// Category Palette (Centralized in theme so Color(0x...) only exists here)
object CategoryPalette {
    val Orange = Color(0xFFFF9800)
    val Amber = Color(0xFFFFC107)
    val Red = Color(0xFFE53935)
    val Green = Color(0xFF4CAF50)
    val Blue = Color(0xFF2196F3)
    val Cyan = Color(0xFF00BCD4)
    val Purple = Color(0xFF9C27B0)
    val Pink = Color(0xFFE91E63)
    val Teal = Color(0xFF009688)
    val Indigo = Color(0xFF3F51B5)
    val Brown = Color(0xFF795548)
    val Grey = Color(0xFF757575)
    val LightBlue = Color(0xFF03A9F4)
    val DeepPurple = Color(0xFF673AB7)
    val BlueGrey = Color(0xFF607D8B)
    val DeepOrange = Color(0xFFFF5722)
    val LightGreen = Color(0xFF8BC34A)
    val Yellow = Color(0xFFFFEB3B)
}

val TextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.duit.textPrimary

val TextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.duit.textSecondary

val TextTertiary: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.duit.textTertiary

val TextOnDark: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.duit.onPrimary

val TextOnDarkSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.duit.onHeroSecondary
