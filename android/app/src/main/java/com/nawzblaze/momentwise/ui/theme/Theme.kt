package com.nawzblaze.momentwise.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.nawzblaze.momentwise.data.model.ThemeMode

/*
 * Momentwise theme.
 *
 * The brand palette is fixed (no Material You / dynamic color) so the app looks
 * the same calm forest-green-and-gold on every device. SYSTEM is the default
 * theme mode; LIGHT and DARK are user overrides selected in Settings.
 */

private val LightColors: ColorScheme = lightColorScheme(
    primary = LightTextPrimary,          // deep forest green — primary buttons
    onPrimary = LightTextInverse,
    primaryContainer = LightSurfaceSubtle,
    onPrimaryContainer = LightTextPrimary,
    secondary = LightStatusGreen,        // calm forest emerald — "active/guarded" states
    onSecondary = LightTextInverse,
    secondaryContainer = LightStatusGreenBackground,
    onSecondaryContainer = LightStatusGreen,
    tertiary = LightAccentText,          // sunrise amber — restrained accent text
    onTertiary = LightTextInverse,
    tertiaryContainer = LightAccentSubtle,
    onTertiaryContainer = LightAccentText,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceSubtle,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = LightBorderSubtle,
    error = LightError,
    onError = LightTextInverse,
    errorContainer = LightErrorBackground,
    onErrorContainer = LightError,
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = DarkTextPrimary,           // off-white primary on charcoal, per prototype
    onPrimary = DarkTextInverse,
    primaryContainer = DarkSurfaceSubtle,
    onPrimaryContainer = DarkTextPrimary,
    secondary = DarkStatusGreen,
    onSecondary = DarkTextInverse,
    secondaryContainer = DarkStatusGreenBackground,
    onSecondaryContainer = DarkStatusGreen,
    tertiary = DarkAccent,
    onTertiary = DarkTextInverse,
    tertiaryContainer = DarkAccentSubtle,
    onTertiaryContainer = DarkAccent,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceSubtle,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorderSubtle,
    error = DarkError,
    onError = DarkTextInverse,
    errorContainer = DarkErrorBackground,
    onErrorContainer = DarkError,
)

/**
 * Resolves the effective dark/light decision for a [ThemeMode].
 * SYSTEM follows the OS setting; LIGHT and DARK are absolute.
 */
@Composable
fun resolveIsDark(themeMode: ThemeMode): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun MomentwiseTheme(
    themeMode: ThemeMode = ThemeMode.DEFAULT,
    content: @Composable () -> Unit,
) {
    val dark = resolveIsDark(themeMode)
    val colorScheme = if (dark) DarkColors else LightColors
    val momentwiseColors = if (dark) DarkMomentwiseColors else LightMomentwiseColors

    CompositionLocalProvider(LocalMomentwiseColors provides momentwiseColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MomentwiseTypography,
            shapes = MomentwiseShapes,
            content = content,
        )
    }
}

/** Convenience accessor for the extended brand tokens. */
object MomentwiseThemeTokens {
    val colors: MomentwiseColors
        @Composable get() = LocalMomentwiseColors.current
}
