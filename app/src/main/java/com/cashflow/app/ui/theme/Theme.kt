package com.cashflow.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.cashflow.app.data.model.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = LightColors.primary,
    onPrimary = LightColors.onPrimary,
    primaryContainer = LightColors.primaryContainer,
    onPrimaryContainer = LightColors.onPrimaryContainer,
    secondary = LightColors.textSecondary,
    onSecondary = LightColors.surface,
    tertiary = LightColors.accentLime,
    onTertiary = LightColors.onAccentLime,
    background = LightColors.background,
    onBackground = LightColors.textPrimary,
    surface = LightColors.surface,
    onSurface = LightColors.textPrimary,
    surfaceVariant = LightColors.surfaceVariant,
    onSurfaceVariant = LightColors.textSecondary,
    outline = LightColors.border,
    outlineVariant = LightColors.borderStrong,
    error = LightColors.expense,
    onError = LightColors.onPrimary,
    errorContainer = LightColors.expenseContainer,
    onErrorContainer = LightColors.expense
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkColors.primary,
    onPrimary = DarkColors.onPrimary,
    primaryContainer = DarkColors.primaryContainer,
    onPrimaryContainer = DarkColors.onPrimaryContainer,
    secondary = DarkColors.textSecondary,
    onSecondary = DarkColors.surface,
    tertiary = DarkColors.accentLime,
    onTertiary = DarkColors.onAccentLime,
    background = DarkColors.background,
    onBackground = DarkColors.textPrimary,
    surface = DarkColors.surface,
    onSurface = DarkColors.textPrimary,
    surfaceVariant = DarkColors.surfaceVariant,
    onSurfaceVariant = DarkColors.textSecondary,
    outline = DarkColors.border,
    outlineVariant = DarkColors.borderStrong,
    error = DarkColors.expense,
    onError = DarkColors.onPrimary,
    errorContainer = DarkColors.expenseContainer,
    onErrorContainer = DarkColors.expense
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
    val duitColors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                // Edge-to-edge: window decor manages background, only set insets controller appearance
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalIsDarkTheme provides darkTheme,
        LocalDuitAingColors provides duitColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
