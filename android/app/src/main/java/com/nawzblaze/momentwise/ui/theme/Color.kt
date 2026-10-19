package com.nawzblaze.momentwise.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Momentwise brand palette — a 1:1 port of the approved design tokens in
 * prototype/styles.css (:root light values and [data-theme="dark"] values).
 *
 * Light: warm cream, deep forest green, restrained sunrise gold.
 * Dark:  soft charcoal, comfortable off-white, muted sunrise gold.
 */

// --- Light mode tokens -------------------------------------------------------
val LightBackground = Color(0xFFFAF7F2)        // --color-bg (warm cream)
val LightSurface = Color(0xFFFFFFFF)           // --color-surface
val LightSurfaceHover = Color(0xFFF6F3EC)      // --color-surface-hover
val LightSurfaceSubtle = Color(0xFFF0EBE0)     // --color-surface-subtle
val LightBorder = Color(0xFFE2DBD0)            // --color-border
val LightBorderSubtle = Color(0xFFEDE7DC)      // --color-border-subtle
val LightBorderHover = Color(0xFFCFC5B6)       // --color-border-hover
val LightTextPrimary = Color(0xFF14291E)       // deep forest green
val LightTextSecondary = Color(0xFF365343)     // balanced forest green
val LightTextTertiary = Color(0xFF5D7568)      // muted green
val LightTextMuted = Color(0xFF7A9184)
val LightTextInverse = Color(0xFFFAF7F2)
val LightAccent = Color(0xFFC28B38)            // restrained sunrise gold
val LightAccentText = Color(0xFF8F5D15)        // high-contrast sunrise amber
val LightAccentHover = Color(0xFFA87428)
val LightAccentSubtle = Color(0x1FC28B38)      // rgba(194,139,56,0.12)
val LightStatusGreen = Color(0xFF1B7340)       // calm forest emerald
val LightStatusGreenBackground = Color(0xFFE8F5E9)
val LightError = Color(0xFFB3261E)
val LightErrorBackground = Color(0xFFFDF2F2)

// --- Dark mode tokens --------------------------------------------------------
val DarkBackground = Color(0xFF0F1412)         // soft charcoal
val DarkSurface = Color(0xFF171D1A)
val DarkSurfaceHover = Color(0xFF1E2521)
val DarkSurfaceSubtle = Color(0xFF141A17)
val DarkSurfaceCard = Color(0xFF19201D)
val DarkBorder = Color(0xFF26322C)
val DarkBorderSubtle = Color(0xFF1E2822)
val DarkBorderHover = Color(0xFF37473E)
val DarkTextPrimary = Color(0xFFEDF2EE)        // comfortable off-white
val DarkTextSecondary = Color(0xFF9EAEA3)      // soft muted sage
val DarkTextTertiary = Color(0xFF6D7F74)
val DarkTextMuted = Color(0xFF71847A)
val DarkTextInverse = Color(0xFF0F1412)
val DarkAccent = Color(0xFFE2AC54)             // muted sunrise gold
val DarkAccentHover = Color(0xFFECC073)
val DarkAccentSubtle = Color(0x29E2AC54)       // rgba(226,172,84,0.16)
val DarkStatusGreen = Color(0xFF34D399)        // luminous emerald
val DarkStatusGreenBackground = Color(0xFF11281E)
val DarkError = Color(0xFFF2B8B5)
val DarkErrorBackground = Color(0xFF371414)

/**
 * Extended Momentwise tokens that Material 3's ColorScheme does not model.
 * Exposed through [LocalMomentwiseColors] so composables never hardcode hex values.
 */
@Immutable
data class MomentwiseColors(
    val surfaceHover: Color,
    val surfaceSubtle: Color,
    val surfaceCard: Color,
    val border: Color,
    val borderSubtle: Color,
    val borderHover: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentText: Color,
    val accentSubtle: Color,
    val statusGreen: Color,
    val statusGreenBackground: Color,
    val errorBackground: Color,
)

val LightMomentwiseColors = MomentwiseColors(
    surfaceHover = LightSurfaceHover,
    surfaceSubtle = LightSurfaceSubtle,
    surfaceCard = LightSurface,
    border = LightBorder,
    borderSubtle = LightBorderSubtle,
    borderHover = LightBorderHover,
    textSecondary = LightTextSecondary,
    textTertiary = LightTextTertiary,
    textMuted = LightTextMuted,
    accent = LightAccent,
    accentText = LightAccentText,
    accentSubtle = LightAccentSubtle,
    statusGreen = LightStatusGreen,
    statusGreenBackground = LightStatusGreenBackground,
    errorBackground = LightErrorBackground,
)

val DarkMomentwiseColors = MomentwiseColors(
    surfaceHover = DarkSurfaceHover,
    surfaceSubtle = DarkSurfaceSubtle,
    surfaceCard = DarkSurfaceCard,
    border = DarkBorder,
    borderSubtle = DarkBorderSubtle,
    borderHover = DarkBorderHover,
    textSecondary = DarkTextSecondary,
    textTertiary = DarkTextTertiary,
    textMuted = DarkTextMuted,
    accent = DarkAccent,
    accentText = DarkAccent,
    accentSubtle = DarkAccentSubtle,
    statusGreen = DarkStatusGreen,
    statusGreenBackground = DarkStatusGreenBackground,
    errorBackground = DarkErrorBackground,
)

val LocalMomentwiseColors = staticCompositionLocalOf { LightMomentwiseColors }
