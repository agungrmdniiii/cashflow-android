package com.cashflow.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalIsDarkTheme = compositionLocalOf { false }

// ============================================================================
// FINTECH DEEP GREEN & OFF-WHITE DESIGN SYSTEM
// ============================================================================

// Brand Colors matching Reference Fintech Design
val FintechPrimary = Color(0xFF133E2B)           // Reference Deep Forest Green
val FintechPrimaryDark = Color(0xFF0C261B)
val FintechSecondary = Color(0xFF2D6A4F)         // Secondary Forest Green
val FintechAccentMint = Color(0xFFA3D9A5)        // Reference Luminous Mint ("Better Days" & Delta)
val FintechAccentLight = Color(0xFFA3D9A5)
val FintechAccentSoft = Color(0xFFDDEBD8)        // Soft Mint Wash
val FintechBorderDark = Color(0xFF1C3A2D)

// Reference Canvas & Card Colors
val FintechCanvasLight = Color(0xFFF7F6F0)       // Reference Warm Creamy Off-White Canvas
val FintechActionSquareLight = Color(0xFFEBEBE3) // Reference Quick Action Square background
val FintechActionSquareDark = Color(0xFF182E23)
val FintechInsightCardLight = Color(0xFFEBF5ED)  // Reference Pale Mint Sprout Banner
val FintechInsightCardDark = Color(0xFF132A1F)
val FintechBarMutedLight = Color(0xFFCCD7D0)     // Reference Inactive Trend Bars
val FintechBarMutedDark = Color(0xFF243B30)

// Light Palette Constants
val LightJadePrimary = FintechPrimary
val LightJadePrimaryDark = FintechPrimaryDark
val LightJadePrimaryLight = Color(0xFFDDEBD8)
val LightJadePrimaryContainer = Color(0xFFDDEBD8)
val LightJadeOnPrimaryContainer = FintechPrimary

val LightPebbleBackground = Color(0xFFF6F5EF)   // Mode Terang Background #F6F5EF
val LightPebbleSurface = Color(0xFFFFFFFF)      // Surface Putih #FFFFFF
val LightPebbleSurfaceVariant = Color(0xFFF0EFE7)
val LightPebbleBorder = Color(0xFFECEAE0)       // Border #ECEAE0
val LightDarkBorder = FintechPrimary
val LightDarkSurface = Color(0xFF121A16)        // Text #121A16
val LightDarkSurfaceVariant = Color(0xFF2B3A33)

val LightTextPrimary = Color(0xFF121A16)        // Contrast 15.2:1 (AAA)
val LightTextSecondary = Color(0xFF4A5C54)      // Contrast 6.1:1 (AA)
val LightTextTertiary = Color(0xFF62746C)       // Contrast 4.6:1 (AA)
val LightTextOnDark = Color(0xFFFFFFFF)         // Crisp white on dark cards
val LightTextOnDarkSecondary = Color(0xFFA3D9A5)

// Dark Palette Constants
val JadePrimaryDarkTheme = Color(0xFFA3D9A5)
val PebbleBackgroundDark = Color(0xFF0B1512)    // Mode Gelap Background #0B1512
val PebbleSurfaceDark = Color(0xFF12201B)       // Mode Gelap Surface #12201B
val PebbleSurfaceVariantDark = Color(0xFF162822)// Mode Gelap Surface Variant #162822
val PebbleBorderDark = Color(0xFF223730)       // Mode Gelap Border #223730
val DarkBorderDarkTheme = Color(0xFF223730)
val DarkSurfaceDarkTheme = Color(0xFFF2F5F1)
val TextPrimaryDark = Color(0xFFF2F5F1)        // Mode Gelap Teks #F2F5F1 (Contrast 16.5:1)
val TextSecondaryDark = Color(0xFF9DB2A8)      // Mode Gelap Teks Sekunder #9DB2A8 (Contrast 8.5:1)
val TextTertiaryDark = Color(0xFF7A9187)       // Metadata text (Contrast 4.8:1)

// Semantic Colors
val AccentAmber = Color(0xFFE5A83B)               // Amber for Transport/Gold
val AccentAmberLight = Color(0xFFFDF4E5)
val StatusPositive = Color(0xFF2D6A4F)            // Forest Emerald
val StatusPositiveContainer = Color(0xFFDDEBD8)
val StatusNegative = Color(0xFFDF5B55)            // Soft Coral Red
val StatusNegativeContainer = Color(0xFFFDEEED)
val CoralPrimary = StatusNegative
val CoralLight = StatusNegativeContainer
val StatusTransfer = Color(0xFF3D7E9A)            // Cyan Slate Teal
val StatusTransferContainer = Color(0xFFEEF5F8)

// Reactive Theme Tokens (Seamlessly adapts between Light and Dark mode)
val JadePrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) JadePrimaryDarkTheme else LightJadePrimary

val JadePrimaryDark: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) Color(0xFF5A8472) else LightJadePrimaryDark

val JadePrimaryLight: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) Color(0xFFAFD3C2) else LightJadePrimaryLight

val JadePrimaryContainer: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) Color(0xFF24332B) else LightJadePrimaryContainer

val JadeOnPrimaryContainer: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) Color(0xFFC8E6D8) else LightJadeOnPrimaryContainer

val PebbleBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) PebbleBackgroundDark else LightPebbleBackground

val PebbleSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) PebbleSurfaceDark else LightPebbleSurface

val PebbleSurfaceVariant: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) PebbleSurfaceVariantDark else LightPebbleSurfaceVariant

val PebbleBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) PebbleBorderDark else LightPebbleBorder

val DarkBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) DarkBorderDarkTheme else LightDarkBorder

val DarkSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) TextPrimaryDark else LightDarkSurface

val DarkSurfaceVariant: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) Color(0xFF27332D) else LightDarkSurfaceVariant

val TextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) TextPrimaryDark else LightTextPrimary

val TextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) TextSecondaryDark else LightTextSecondary

val TextTertiary: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) TextTertiaryDark else LightTextTertiary

val TextOnDark: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) Color(0xFFF7F5F0) else LightTextOnDark

val TextOnDarkSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) Color(0xFFAAB8B0) else LightTextOnDarkSecondary
