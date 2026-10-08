package com.cashflow.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalIsDarkTheme = compositionLocalOf { false }

// ============================================================================
// MATCHA OAT & CALM SAGE PALETTE (Soft, Gentle, Eye-Friendly Design System)
// ============================================================================

// Light Palette Constants (Warm Oat Milk Cream & Soft Muted Matcha Sage)
val LightJadePrimary = Color(0xFF386652)          // Soft Matcha Sage (Restful & AA compliant)
val LightJadePrimaryDark = Color(0xFF264839)      // Deeper Matcha for contrast
val LightJadePrimaryLight = Color(0xFFE4EEE8)     // Gentle matcha milk wash
val LightJadePrimaryContainer = Color(0xFFD6E7DE) // Soothing sage container
val LightJadeOnPrimaryContainer = Color(0xFF13271E)

val LightPebbleBackground = Color(0xFFF7F5F0)     // Warm Oat Milk (Anti-glare, replaces harsh white)
val LightPebbleSurface = Color(0xFFFFFFFF)        // Clean silky surface
val LightPebbleSurfaceVariant = Color(0xFFEFECE4) // Soft oatmeal card variant
val LightPebbleBorder = Color(0xFFDAD6CA)         // Gentle tactile hairline border
val LightDarkBorder = Color(0xFF1C2721)
val LightDarkSurface = Color(0xFF19231E)
val LightDarkSurfaceVariant = Color(0xFF23302A)

val LightTextPrimary = Color(0xFF1B2620)          // Deep warm charcoal (Gentle on retinas)
val LightTextSecondary = Color(0xFF485850)        // Soft muted sage charcoal
val LightTextTertiary = Color(0xFF6E7E76)         // Subtle contextual metadata
val LightTextOnDark = Color(0xFFF7F5F0)           // Warm oat milk text on dark cards
val LightTextOnDarkSecondary = Color(0xFFAAB8B0)

// Dark Palette Constants (Deep Velvety Olive-Charcoal & Glowing Muted Sage)
val JadePrimaryDarkTheme = Color(0xFF7EA693)      // Soft glowing sage (Gentle, zero harsh neon glare)
val PebbleBackgroundDark = Color(0xFF141917)      // Deep olive-charcoal (Eye-comfort dark mode)
val PebbleSurfaceDark = Color(0xFF1C2320)         // Elevated soft dark pebble
val PebbleSurfaceVariantDark = Color(0xFF232D29)  // Secondary elevated card surface
val PebbleBorderDark = Color(0xFF2D3933)          // Soft muted hairline border
val DarkBorderDarkTheme = Color(0xFF35443D)
val DarkSurfaceDarkTheme = Color(0xFFE8ECE9)
val TextPrimaryDark = Color(0xFFE8ECE9)           // Soft oat white
val TextSecondaryDark = Color(0xFFA2B2AA)         // Calming misty sage
val TextTertiaryDark = Color(0xFF74857D)          // Subtle dark metadata

// Shared Editorial Accents (Softened for Eye Comfort)
val AccentAmber = Color(0xFFD97736)               // Warm soft caramel
val AccentAmberLight = Color(0xFFFDF0E6)
val StatusPositive = Color(0xFF387B58)            // Soft eucalyptus green
val StatusPositiveContainer = Color(0xFFE3F3EB)
val StatusNegative = Color(0xFFBF4842)            // Soft terracotta rose (Comforting, not angry red)
val StatusNegativeContainer = Color(0xFFFCEEED)
val CoralPrimary = StatusNegative
val CoralLight = StatusNegativeContainer
val StatusTransfer = Color(0xFF476C8E)            // Soft dusk slate blue
val StatusTransferContainer = Color(0xFFECF2F8)

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
    get() = if (LocalIsDarkTheme.current) DarkSurfaceDarkTheme else LightDarkSurface

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
