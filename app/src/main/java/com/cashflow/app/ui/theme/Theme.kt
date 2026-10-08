package com.cashflow.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.cashflow.app.data.model.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = LightJadePrimary,
    onPrimary = LightPebbleSurface,
    primaryContainer = LightJadePrimaryContainer,
    onPrimaryContainer = LightJadeOnPrimaryContainer,
    secondary = LightJadePrimaryDark,
    onSecondary = LightPebbleSurface,
    background = LightPebbleBackground,
    onBackground = LightTextPrimary,
    surface = LightPebbleSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightPebbleSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightPebbleBorder
)

private val DarkColorScheme = darkColorScheme(
    primary = JadePrimaryDarkTheme,
    onPrimary = PebbleBackgroundDark,
    primaryContainer = Color(0xFF24332B),
    onPrimaryContainer = Color(0xFFC8E6D8),
    secondary = Color(0xFF5A8472),
    onSecondary = PebbleBackgroundDark,
    background = PebbleBackgroundDark,
    onBackground = TextPrimaryDark,
    surface = PebbleSurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = PebbleSurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = PebbleBorderDark
)

@Composable
fun CashflowTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    darkTheme: Boolean = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    },
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
