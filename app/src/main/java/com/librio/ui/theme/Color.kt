package com.librio.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

/**
 * App Theme Color Palettes
 * 8 curated themes (Parchment, Rosewood, Sage, Sand, Lavender, Sky, Peach, Mint)
 * Each theme has hand-tuned light and dark variants
 */
enum class AppTheme(val displayName: String) {
    PARCHMENT("Parchment"),
    ROSEWOOD("Rosewood"),
    SAGE("Sage"),
    SAND("Sand"),
    LAVENDER("Lavender"),
    SKY("Sky"),
    PEACH("Peach"),
    MINT("Mint"),
    CUSTOM("Custom");

    /**
     * Returns true if this is a dark-themed theme (none are inherently dark now; dark mode is separate)
     */
    fun isDark(): Boolean = false

    /**
     * Get the corresponding light theme (identity - dark mode is now a separate toggle)
     */
    fun toLightTheme(): AppTheme = this

    /**
     * Get the corresponding dark theme (identity - dark mode is now a separate toggle)
     */
    fun toDarkTheme(): AppTheme = this
}

// Global custom color storage (set from SettingsRepository)
object CustomThemeColors {
    var baseColor: Color = Color(0xFF4A7C59)
    var primaryColor: Color
        get() = baseColor
        set(value) { baseColor = value }
    var accentColor: Color
        get() = baseColor
        set(value) { baseColor = value }
    var backgroundColor: Color = Color(0xFFFFFFFF)
}

/**
 * Color palette for each theme
 * Maintains backward compatibility with shade1-12 system
 *
 * Shade levels:
 * - shade1: Deepest
 * - shade2-3: Dark
 * - shade4: Base accent color
 * - shade5-8: Mid tones
 * - shade9-12: Light backgrounds
 */
data class ThemePalette(
    val primary: Color,
    val primaryLight: Color,
    val primaryDark: Color,
    val accent: Color,
    val accentGradientStart: Color,
    val accentGradientEnd: Color,
    val surfaceDark: Color,
    val surfaceMedium: Color,
    val surfaceLight: Color,
    val surfaceCard: Color,
    val shade1: Color,
    val shade2: Color,
    val shade3: Color,
    val shade4: Color,
    val shade5: Color,
    val shade6: Color,
    val shade7: Color,
    val shade8: Color,
    val shade9: Color,
    val shade10: Color,
    val shade11: Color,
    val shade12: Color,
    val background: Color,
    val surface: Color,
    val contentBackground: Color = background,
    val onPrimary: Color = Color.White,
    val onBackground: Color,
    val onSurface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val divider: Color,
    val headerBackground: Color
)

// ═══════════════════════════════════════════════
// LIGHT THEME PALETTES (from JSX design)
// ═══════════════════════════════════════════════

private fun createLightParchmentPalette(): ThemePalette {
    val accent = Color(0xFF4A7C59)
    val accent2 = Color(0xFFC97C2A)
    val shades = createShadesFromColor(accent)
    return ThemePalette(
        primary = accent, primaryLight = shades[5], primaryDark = shades[0],
        accent = accent, accentGradientStart = shades[0], accentGradientEnd = accent2,
        surfaceDark = Color(0xFF2A2015), surfaceMedium = Color(0xFFEDE7D9),
        surfaceLight = Color(0xFFF5F0E8), surfaceCard = Color(0xFFEEE8D8),
        shade1 = shades[0], shade2 = shades[1], shade3 = shades[2], shade4 = shades[3],
        shade5 = shades[4], shade6 = shades[5], shade7 = shades[6], shade8 = shades[7],
        shade9 = Color(0xFFF5F0E8), shade10 = Color(0xFFF8F4ED),
        shade11 = Color(0xFFFAF7F2), shade12 = Color(0xFFFCFAF7),
        background = Color(0xFFF5F0E8), surface = Color(0xFFEDE7D9),
        onPrimary = Color.White, onBackground = Color(0xFF2C1F0F), onSurface = Color(0xFF2C1F0F),
        textPrimary = Color(0xFF2C1F0F), textSecondary = Color(0xFF6B5240),
        textMuted = Color(0xFF9C7D62), divider = Color(0xFFD4C4A8),
        headerBackground = Color(0xFF2A2015)
    )
}

private fun createLightRosewoodPalette(): ThemePalette {
    val accent = Color(0xFFB85A6A)
    val accent2 = Color(0xFF8A6AB8)
    val shades = createShadesFromColor(accent)
    return ThemePalette(
        primary = accent, primaryLight = shades[5], primaryDark = shades[0],
        accent = accent, accentGradientStart = shades[0], accentGradientEnd = accent2,
        surfaceDark = Color(0xFF201515), surfaceMedium = Color(0xFFF0E4E4),
        surfaceLight = Color(0xFFF8F0F0), surfaceCard = Color(0xFFF2E8E8),
        shade1 = shades[0], shade2 = shades[1], shade3 = shades[2], shade4 = shades[3],
        shade5 = shades[4], shade6 = shades[5], shade7 = shades[6], shade8 = shades[7],
        shade9 = Color(0xFFF8F0F0), shade10 = Color(0xFFFAF4F4),
        shade11 = Color(0xFFFCF7F7), shade12 = Color(0xFFFDF9F9),
        background = Color(0xFFF8F0F0), surface = Color(0xFFF0E4E4),
        onPrimary = Color.White, onBackground = Color(0xFF2C1A1A), onSurface = Color(0xFF2C1A1A),
        textPrimary = Color(0xFF2C1A1A), textSecondary = Color(0xFF6B4A4A),
        textMuted = Color(0xFF9C7272), divider = Color(0xFFD8C0C0),
        headerBackground = Color(0xFF201515)
    )
}

private fun createLightSagePalette(): ThemePalette {
    val accent = Color(0xFF5A9A50)
    val accent2 = Color(0xFFC4883A)
    val shades = createShadesFromColor(accent)
    return ThemePalette(
        primary = accent, primaryLight = shades[5], primaryDark = shades[0],
        accent = accent, accentGradientStart = shades[0], accentGradientEnd = accent2,
        surfaceDark = Color(0xFF121A10), surfaceMedium = Color(0xFFE4ECE0),
        surfaceLight = Color(0xFFF0F4EE), surfaceCard = Color(0xFFE8F0E4),
        shade1 = shades[0], shade2 = shades[1], shade3 = shades[2], shade4 = shades[3],
        shade5 = shades[4], shade6 = shades[5], shade7 = shades[6], shade8 = shades[7],
        shade9 = Color(0xFFF0F4EE), shade10 = Color(0xFFF4F7F2),
        shade11 = Color(0xFFF7FAF5), shade12 = Color(0xFFFAFCF9),
        background = Color(0xFFF0F4EE), surface = Color(0xFFE4ECE0),
        onPrimary = Color.White, onBackground = Color(0xFF1A2C14), onSurface = Color(0xFF1A2C14),
        textPrimary = Color(0xFF1A2C14), textSecondary = Color(0xFF4A6B40),
        textMuted = Color(0xFF72A068), divider = Color(0xFFC0D0B8),
        headerBackground = Color(0xFF121A10)
    )
}

private fun createLightSandPalette(): ThemePalette {
    val accent = Color(0xFFC48830)
    val accent2 = Color(0xFF5A7C8B)
    val shades = createShadesFromColor(accent)
    return ThemePalette(
        primary = accent, primaryLight = shades[5], primaryDark = shades[0],
        accent = accent, accentGradientStart = shades[0], accentGradientEnd = accent2,
        surfaceDark = Color(0xFF1A1508), surfaceMedium = Color(0xFFEDE6D8),
        surfaceLight = Color(0xFFF6F2EA), surfaceCard = Color(0xFFF0EAD8),
        shade1 = shades[0], shade2 = shades[1], shade3 = shades[2], shade4 = shades[3],
        shade5 = shades[4], shade6 = shades[5], shade7 = shades[6], shade8 = shades[7],
        shade9 = Color(0xFFF6F2EA), shade10 = Color(0xFFF8F5EF),
        shade11 = Color(0xFFFAF8F4), shade12 = Color(0xFFFCFAF7),
        background = Color(0xFFF6F2EA), surface = Color(0xFFEDE6D8),
        onPrimary = Color.White, onBackground = Color(0xFF2A2010), onSurface = Color(0xFF2A2010),
        textPrimary = Color(0xFF2A2010), textSecondary = Color(0xFF6B5830),
        textMuted = Color(0xFFA08C64), divider = Color(0xFFD8C8A8),
        headerBackground = Color(0xFF1A1508)
    )
}

private fun createLightLavenderPalette(): ThemePalette {
    val accent = Color(0xFF7A5AB8)
    val accent2 = Color(0xFFC87A4A)
    val shades = createShadesFromColor(accent)
    return ThemePalette(
        primary = accent, primaryLight = shades[5], primaryDark = shades[0],
        accent = accent, accentGradientStart = shades[0], accentGradientEnd = accent2,
        surfaceDark = Color(0xFF12101A), surfaceMedium = Color(0xFFECE4F2),
        surfaceLight = Color(0xFFF4F0F8), surfaceCard = Color(0xFFF0EAF6),
        shade1 = shades[0], shade2 = shades[1], shade3 = shades[2], shade4 = shades[3],
        shade5 = shades[4], shade6 = shades[5], shade7 = shades[6], shade8 = shades[7],
        shade9 = Color(0xFFF4F0F8), shade10 = Color(0xFFF7F4FA),
        shade11 = Color(0xFFF9F7FC), shade12 = Color(0xFFFBF9FD),
        background = Color(0xFFF4F0F8), surface = Color(0xFFECE4F2),
        onPrimary = Color.White, onBackground = Color(0xFF1A142C), onSurface = Color(0xFF1A142C),
        textPrimary = Color(0xFF1A142C), textSecondary = Color(0xFF5A4A7A),
        textMuted = Color(0xFF8878A8), divider = Color(0xFFC8B8D8),
        headerBackground = Color(0xFF12101A)
    )
}

private fun createLightSkyPalette(): ThemePalette {
    val accent = Color(0xFF3A8AC0)
    val accent2 = Color(0xFFD4884A)
    val shades = createShadesFromColor(accent)
    return ThemePalette(
        primary = accent, primaryLight = shades[5], primaryDark = shades[0],
        accent = accent, accentGradientStart = shades[0], accentGradientEnd = accent2,
        surfaceDark = Color(0xFF080E14), surfaceMedium = Color(0xFFE0ECF4),
        surfaceLight = Color(0xFFEEF4F8), surfaceCard = Color(0xFFE6F0F6),
        shade1 = shades[0], shade2 = shades[1], shade3 = shades[2], shade4 = shades[3],
        shade5 = shades[4], shade6 = shades[5], shade7 = shades[6], shade8 = shades[7],
        shade9 = Color(0xFFEEF4F8), shade10 = Color(0xFFF2F7FA),
        shade11 = Color(0xFFF6F9FC), shade12 = Color(0xFFF9FBFD),
        background = Color(0xFFEEF4F8), surface = Color(0xFFE0ECF4),
        onPrimary = Color.White, onBackground = Color(0xFF0E1A24), onSurface = Color(0xFF0E1A24),
        textPrimary = Color(0xFF0E1A24), textSecondary = Color(0xFF3A5A72),
        textMuted = Color(0xFF6890A8), divider = Color(0xFFB0C8D8),
        headerBackground = Color(0xFF080E14)
    )
}

private fun createLightPeachPalette(): ThemePalette {
    val accent = Color(0xFFD07040)
    val accent2 = Color(0xFF508A7A)
    val shades = createShadesFromColor(accent)
    return ThemePalette(
        primary = accent, primaryLight = shades[5], primaryDark = shades[0],
        accent = accent, accentGradientStart = shades[0], accentGradientEnd = accent2,
        surfaceDark = Color(0xFF1A100A), surfaceMedium = Color(0xFFF4E8E0),
        surfaceLight = Color(0xFFFAF2EE), surfaceCard = Color(0xFFF6ECE4),
        shade1 = shades[0], shade2 = shades[1], shade3 = shades[2], shade4 = shades[3],
        shade5 = shades[4], shade6 = shades[5], shade7 = shades[6], shade8 = shades[7],
        shade9 = Color(0xFFFAF2EE), shade10 = Color(0xFFFCF6F2),
        shade11 = Color(0xFFFDF9F6), shade12 = Color(0xFFFEFBF9),
        background = Color(0xFFFAF2EE), surface = Color(0xFFF4E8E0),
        onPrimary = Color.White, onBackground = Color(0xFF2C1810), onSurface = Color(0xFF2C1810),
        textPrimary = Color(0xFF2C1810), textSecondary = Color(0xFF7A5040),
        textMuted = Color(0xFFA87860), divider = Color(0xFFE0C8B8),
        headerBackground = Color(0xFF1A100A)
    )
}

private fun createLightMintPalette(): ThemePalette {
    val accent = Color(0xFF3AAA98)
    val accent2 = Color(0xFFC87A5A)
    val shades = createShadesFromColor(accent)
    return ThemePalette(
        primary = accent, primaryLight = shades[5], primaryDark = shades[0],
        accent = accent, accentGradientStart = shades[0], accentGradientEnd = accent2,
        surfaceDark = Color(0xFF081210), surfaceMedium = Color(0xFFE0F0EC),
        surfaceLight = Color(0xFFEEF6F4), surfaceCard = Color(0xFFE4F2EE),
        shade1 = shades[0], shade2 = shades[1], shade3 = shades[2], shade4 = shades[3],
        shade5 = shades[4], shade6 = shades[5], shade7 = shades[6], shade8 = shades[7],
        shade9 = Color(0xFFEEF6F4), shade10 = Color(0xFFF2F9F7),
        shade11 = Color(0xFFF6FBFA), shade12 = Color(0xFFF9FCFB),
        background = Color(0xFFEEF6F4), surface = Color(0xFFE0F0EC),
        onPrimary = Color.White, onBackground = Color(0xFF0E1C1A), onSurface = Color(0xFF0E1C1A),
        textPrimary = Color(0xFF0E1C1A), textSecondary = Color(0xFF3A6860),
        textMuted = Color(0xFF68A098), divider = Color(0xFFB0D0C8),
        headerBackground = Color(0xFF081210)
    )
}

// ═══════════════════════════════════════════════
// DARK THEME PALETTES (from JSX design)
// ═══════════════════════════════════════════════

private fun createDarkParchmentPalette(): ThemePalette {
    val accent = Color(0xFF5A9A68)
    val accent2 = Color(0xFFD89040)
    val shades = createShadesFromColor(accent)
    return ThemePalette(
        primary = accent, primaryLight = shades[5], primaryDark = shades[0],
        accent = accent, accentGradientStart = shades[0], accentGradientEnd = accent2,
        surfaceDark = Color(0xFF0E0C08), surfaceMedium = Color(0xFF1E1A12),
        surfaceLight = Color(0xFF242018), surfaceCard = Color(0xFF242018),
        shade1 = shades[0], shade2 = shades[1], shade3 = shades[2], shade4 = shades[3],
        shade5 = Color(0xFF242018), shade6 = Color(0xFF1E1A12),
        shade7 = Color(0xFF18150E), shade8 = Color(0xFF0E0C08),
        shade9 = Color(0xFF18150E), shade10 = Color(0xFF1E1A12),
        shade11 = Color(0xFF242018), shade12 = Color(0xFF18150E),
        background = Color(0xFF18150E), surface = Color(0xFF1E1A12),
        onPrimary = Color.White, onBackground = Color(0xFFE8E0D0), onSurface = Color(0xFFE8E0D0),
        textPrimary = Color(0xFFE8E0D0), textSecondary = Color(0xFFB0A080),
        textMuted = Color(0xFF7A6A4A), divider = Color(0xFF3A3020),
        headerBackground = Color(0xFF0E0C08)
    )
}

private fun createDarkRosewoodPalette(): ThemePalette {
    val accent = Color(0xFFD06878)
    val accent2 = Color(0xFF9A78C8)
    val shades = createShadesFromColor(accent)
    return ThemePalette(
        primary = accent, primaryLight = shades[5], primaryDark = shades[0],
        accent = accent, accentGradientStart = shades[0], accentGradientEnd = accent2,
        surfaceDark = Color(0xFF100C0E), surfaceMedium = Color(0xFF241A1E),
        surfaceLight = Color(0xFF2A2024), surfaceCard = Color(0xFF2A2024),
        shade1 = shades[0], shade2 = shades[1], shade3 = shades[2], shade4 = shades[3],
        shade5 = Color(0xFF2A2024), shade6 = Color(0xFF241A1E),
        shade7 = Color(0xFF1C1416), shade8 = Color(0xFF100C0E),
        shade9 = Color(0xFF1C1416), shade10 = Color(0xFF241A1E),
        shade11 = Color(0xFF2A2024), shade12 = Color(0xFF1C1416),
        background = Color(0xFF1C1416), surface = Color(0xFF241A1E),
        onPrimary = Color.White, onBackground = Color(0xFFF0E0E4), onSurface = Color(0xFFF0E0E4),
        textPrimary = Color(0xFFF0E0E4), textSecondary = Color(0xFFB88A90),
        textMuted = Color(0xFF7A5A60), divider = Color(0xFF3E2E34),
        headerBackground = Color(0xFF100C0E)
    )
}

private fun createDarkSagePalette(): ThemePalette {
    val accent = Color(0xFF68B860)
    val accent2 = Color(0xFFD0A050)
    val shades = createShadesFromColor(accent)
    return ThemePalette(
        primary = accent, primaryLight = shades[5], primaryDark = shades[0],
        accent = accent, accentGradientStart = shades[0], accentGradientEnd = accent2,
        surfaceDark = Color(0xFF0A100A), surfaceMedium = Color(0xFF1A2218),
        surfaceLight = Color(0xFF202A1E), surfaceCard = Color(0xFF202A1E),
        shade1 = shades[0], shade2 = shades[1], shade3 = shades[2], shade4 = shades[3],
        shade5 = Color(0xFF202A1E), shade6 = Color(0xFF1A2218),
        shade7 = Color(0xFF141A12), shade8 = Color(0xFF0A100A),
        shade9 = Color(0xFF141A12), shade10 = Color(0xFF1A2218),
        shade11 = Color(0xFF202A1E), shade12 = Color(0xFF141A12),
        background = Color(0xFF141A12), surface = Color(0xFF1A2218),
        onPrimary = Color.White, onBackground = Color(0xFFD8E8D0), onSurface = Color(0xFFD8E8D0),
        textPrimary = Color(0xFFD8E8D0), textSecondary = Color(0xFF8AB080),
        textMuted = Color(0xFF5A7A50), divider = Color(0xFF2E3E2A),
        headerBackground = Color(0xFF0A100A)
    )
}

private fun createDarkSandPalette(): ThemePalette {
    val accent = Color(0xFFD89830)
    val accent2 = Color(0xFF6890A0)
    val shades = createShadesFromColor(accent)
    return ThemePalette(
        primary = accent, primaryLight = shades[5], primaryDark = shades[0],
        accent = accent, accentGradientStart = shades[0], accentGradientEnd = accent2,
        surfaceDark = Color(0xFF0E0A06), surfaceMedium = Color(0xFF201A14),
        surfaceLight = Color(0xFF28221A), surfaceCard = Color(0xFF28221A),
        shade1 = shades[0], shade2 = shades[1], shade3 = shades[2], shade4 = shades[3],
        shade5 = Color(0xFF28221A), shade6 = Color(0xFF201A14),
        shade7 = Color(0xFF181410), shade8 = Color(0xFF0E0A06),
        shade9 = Color(0xFF181410), shade10 = Color(0xFF201A14),
        shade11 = Color(0xFF28221A), shade12 = Color(0xFF181410),
        background = Color(0xFF181410), surface = Color(0xFF201A14),
        onPrimary = Color.White, onBackground = Color(0xFFE8E0D0), onSurface = Color(0xFFE8E0D0),
        textPrimary = Color(0xFFE8E0D0), textSecondary = Color(0xFFB09868),
        textMuted = Color(0xFF7A6A40), divider = Color(0xFF3E3420),
        headerBackground = Color(0xFF0E0A06)
    )
}

private fun createDarkLavenderPalette(): ThemePalette {
    val accent = Color(0xFF8A68C8)
    val accent2 = Color(0xFFD88A58)
    val shades = createShadesFromColor(accent)
    return ThemePalette(
        primary = accent, primaryLight = shades[5], primaryDark = shades[0],
        accent = accent, accentGradientStart = shades[0], accentGradientEnd = accent2,
        surfaceDark = Color(0xFF0C0A12), surfaceMedium = Color(0xFF1C1A26),
        surfaceLight = Color(0xFF22202E), surfaceCard = Color(0xFF22202E),
        shade1 = shades[0], shade2 = shades[1], shade3 = shades[2], shade4 = shades[3],
        shade5 = Color(0xFF22202E), shade6 = Color(0xFF1C1A26),
        shade7 = Color(0xFF16141E), shade8 = Color(0xFF0C0A12),
        shade9 = Color(0xFF16141E), shade10 = Color(0xFF1C1A26),
        shade11 = Color(0xFF22202E), shade12 = Color(0xFF16141E),
        background = Color(0xFF16141E), surface = Color(0xFF1C1A26),
        onPrimary = Color.White, onBackground = Color(0xFFE4E0EE), onSurface = Color(0xFFE4E0EE),
        textPrimary = Color(0xFFE4E0EE), textSecondary = Color(0xFFA098C0),
        textMuted = Color(0xFF6A5E8A), divider = Color(0xFF34303E),
        headerBackground = Color(0xFF0C0A12)
    )
}

private fun createDarkSkyPalette(): ThemePalette {
    val accent = Color(0xFF48A0D8)
    val accent2 = Color(0xFFE09850)
    val shades = createShadesFromColor(accent)
    return ThemePalette(
        primary = accent, primaryLight = shades[5], primaryDark = shades[0],
        accent = accent, accentGradientStart = shades[0], accentGradientEnd = accent2,
        surfaceDark = Color(0xFF080E14), surfaceMedium = Color(0xFF142028),
        surfaceLight = Color(0xFF1A2830), surfaceCard = Color(0xFF1A2830),
        shade1 = shades[0], shade2 = shades[1], shade3 = shades[2], shade4 = shades[3],
        shade5 = Color(0xFF1A2830), shade6 = Color(0xFF142028),
        shade7 = Color(0xFF101820), shade8 = Color(0xFF080E14),
        shade9 = Color(0xFF101820), shade10 = Color(0xFF142028),
        shade11 = Color(0xFF1A2830), shade12 = Color(0xFF101820),
        background = Color(0xFF101820), surface = Color(0xFF142028),
        onPrimary = Color.White, onBackground = Color(0xFFD4E4F0), onSurface = Color(0xFFD4E4F0),
        textPrimary = Color(0xFFD4E4F0), textSecondary = Color(0xFF80A0B8),
        textMuted = Color(0xFF507088), divider = Color(0xFF283848),
        headerBackground = Color(0xFF080E14)
    )
}

private fun createDarkPeachPalette(): ThemePalette {
    val accent = Color(0xFFE08050)
    val accent2 = Color(0xFF58A090)
    val shades = createShadesFromColor(accent)
    return ThemePalette(
        primary = accent, primaryLight = shades[5], primaryDark = shades[0],
        accent = accent, accentGradientStart = shades[0], accentGradientEnd = accent2,
        surfaceDark = Color(0xFF100A08), surfaceMedium = Color(0xFF221A14),
        surfaceLight = Color(0xFF2A201A), surfaceCard = Color(0xFF2A201A),
        shade1 = shades[0], shade2 = shades[1], shade3 = shades[2], shade4 = shades[3],
        shade5 = Color(0xFF2A201A), shade6 = Color(0xFF221A14),
        shade7 = Color(0xFF1A1410), shade8 = Color(0xFF100A08),
        shade9 = Color(0xFF1A1410), shade10 = Color(0xFF221A14),
        shade11 = Color(0xFF2A201A), shade12 = Color(0xFF1A1410),
        background = Color(0xFF1A1410), surface = Color(0xFF221A14),
        onPrimary = Color.White, onBackground = Color(0xFFF0E0D4), onSurface = Color(0xFFF0E0D4),
        textPrimary = Color(0xFFF0E0D4), textSecondary = Color(0xFFB88870),
        textMuted = Color(0xFF7A5A48), divider = Color(0xFF3E3028),
        headerBackground = Color(0xFF100A08)
    )
}

private fun createDarkMintPalette(): ThemePalette {
    val accent = Color(0xFF48BAA8)
    val accent2 = Color(0xFFD08868)
    val shades = createShadesFromColor(accent)
    return ThemePalette(
        primary = accent, primaryLight = shades[5], primaryDark = shades[0],
        accent = accent, accentGradientStart = shades[0], accentGradientEnd = accent2,
        surfaceDark = Color(0xFF080E0E), surfaceMedium = Color(0xFF142220),
        surfaceLight = Color(0xFF1A2A28), surfaceCard = Color(0xFF1A2A28),
        shade1 = shades[0], shade2 = shades[1], shade3 = shades[2], shade4 = shades[3],
        shade5 = Color(0xFF1A2A28), shade6 = Color(0xFF142220),
        shade7 = Color(0xFF101A18), shade8 = Color(0xFF080E0E),
        shade9 = Color(0xFF101A18), shade10 = Color(0xFF142220),
        shade11 = Color(0xFF1A2A28), shade12 = Color(0xFF101A18),
        background = Color(0xFF101A18), surface = Color(0xFF142220),
        onPrimary = Color.White, onBackground = Color(0xFFD4F0EA), onSurface = Color(0xFFD4F0EA),
        textPrimary = Color(0xFFD4F0EA), textSecondary = Color(0xFF80B8B0),
        textMuted = Color(0xFF508A80), divider = Color(0xFF283A38),
        headerBackground = Color(0xFF080E0E)
    )
}

// ═══════════════════════════════════════════════
// DEFAULT PALETTE
// ═══════════════════════════════════════════════

val TealPalette = createLightParchmentPalette()

// ═══════════════════════════════════════════════
// CUSTOM THEME SUPPORT
// ═══════════════════════════════════════════════

/**
 * Creates a custom palette from a single base color
 */
fun createCustomPalette(
    baseColor: Color = CustomThemeColors.baseColor
): ThemePalette {
    val shades = createShadesFromColor(baseColor)
    val shade1 = shades[0]
    val shade2 = shades[1]
    val shade5 = shades[4]
    val shade7 = shades[6]
    val shade9 = shades[8]
    val shade10 = shades[9]

    val divider = Color(
        red = (shade5.red * 0.8f + 0.2f).coerceIn(0f, 1f),
        green = (shade5.green * 0.8f + 0.2f).coerceIn(0f, 1f),
        blue = (shade5.blue * 0.8f + 0.2f).coerceIn(0f, 1f)
    )

    val hasCustomBackground = CustomThemeColors.backgroundColor.alpha > 0f && CustomThemeColors.backgroundColor != Color.White
    val bg = if (hasCustomBackground) CustomThemeColors.backgroundColor else shade9
    val surfaceColor = if (hasCustomBackground) bg else shade10
    val bgLuminance = (bg.red * 0.299f + bg.green * 0.587f + bg.blue * 0.114f)
    val contrastTextPrimary = if (bgLuminance < 0.5f) Color.White else Color(0xFF111111)
    val contrastSecondary = if (bgLuminance < 0.5f) Color(0xFFD0D0D0) else Color(0xFF444444)
    val contrastMuted = if (bgLuminance < 0.5f) Color(0xFFB0B0B0) else Color(0xFF666666)

    return ThemePalette(
        primary = baseColor, primaryLight = shade5, primaryDark = shade2,
        accent = baseColor, accentGradientStart = shade2, accentGradientEnd = shade7,
        background = bg, surface = surfaceColor,
        surfaceDark = shade2, surfaceMedium = shade7, surfaceLight = shade10,
        surfaceCard = baseColor, headerBackground = shade2,
        textPrimary = contrastTextPrimary, textSecondary = contrastSecondary,
        textMuted = contrastMuted,
        onPrimary = Color.White, onBackground = contrastTextPrimary, onSurface = contrastTextPrimary,
        divider = divider,
        shade1 = shades[0], shade2 = shades[1], shade3 = shades[2], shade4 = shades[3],
        shade5 = shades[4], shade6 = shades[5], shade7 = shades[6], shade8 = shades[7],
        shade9 = shades[8], shade10 = shades[9], shade11 = shades[10], shade12 = shades[11]
    )
}

/**
 * Creates a complete ThemePalette from a single accent color
 */
fun createPaletteFromAccent(accent: Color): ThemePalette {
    val shades = createShadesFromColor(accent)
    val luminance = accent.red * 0.299f + accent.green * 0.587f + accent.blue * 0.114f
    val isPastel = luminance > 0.65f
    val accentForUI = if (isPastel) shades[0] else accent

    return ThemePalette(
        primary = accentForUI, primaryLight = shades[5], primaryDark = shades[0],
        accent = accentForUI, accentGradientStart = shades[0], accentGradientEnd = shades[3],
        surfaceDark = shades[0], surfaceMedium = shades[7], surfaceLight = shades[9],
        surfaceCard = accentForUI,
        shade1 = shades[0], shade2 = shades[1], shade3 = shades[2], shade4 = shades[3],
        shade5 = shades[4], shade6 = shades[5], shade7 = shades[6], shade8 = shades[7],
        shade9 = shades[8], shade10 = shades[9], shade11 = shades[10], shade12 = shades[11],
        background = shades[11], surface = Color.White,
        onPrimary = Color.White, onBackground = Color(0xFF1A1A1A), onSurface = Color(0xFF1A1A1A),
        textPrimary = Color(0xFF1A1A1A), textSecondary = Color(0xFF37474F),
        textMuted = Color(0xFF607D8B), divider = Color(0xFFB0BEC5),
        headerBackground = shades[1]
    )
}

/**
 * Adds shades to a palette based on its primary/accent color
 */
fun ThemePalette.withGeneratedShades(): ThemePalette {
    val shades = createShadesFromColor(accent)
    return copy(
        shade1 = shades[0], shade2 = shades[1], shade3 = shades[2], shade4 = shades[3],
        shade5 = shades[4], shade6 = shades[5], shade7 = shades[6], shade8 = shades[7],
        shade9 = shades[8], shade10 = shades[9], shade11 = shades[10], shade12 = shades[11]
    )
}

// ═══════════════════════════════════════════════
// THEME PALETTE RESOLUTION
// ═══════════════════════════════════════════════

fun getThemePalette(
    theme: AppTheme,
    darkMode: Boolean = false
): ThemePalette {
    if (theme == AppTheme.CUSTOM) {
        val customPalette = createCustomPalette().withGeneratedShades()
        return if (darkMode) {
            val bgLuminance = (CustomThemeColors.backgroundColor.red * 0.299f +
                    CustomThemeColors.backgroundColor.green * 0.587f +
                    CustomThemeColors.backgroundColor.blue * 0.114f)
            if (bgLuminance < 0.4f) customPalette else customPalette.toDarkMode()
        } else {
            customPalette
        }
    }

    return if (darkMode) {
        when (theme) {
            AppTheme.PARCHMENT -> createDarkParchmentPalette()
            AppTheme.ROSEWOOD -> createDarkRosewoodPalette()
            AppTheme.SAGE -> createDarkSagePalette()
            AppTheme.SAND -> createDarkSandPalette()
            AppTheme.LAVENDER -> createDarkLavenderPalette()
            AppTheme.SKY -> createDarkSkyPalette()
            AppTheme.PEACH -> createDarkPeachPalette()
            AppTheme.MINT -> createDarkMintPalette()
            AppTheme.CUSTOM -> createCustomPalette() // handled above
        }
    } else {
        when (theme) {
            AppTheme.PARCHMENT -> createLightParchmentPalette()
            AppTheme.ROSEWOOD -> createLightRosewoodPalette()
            AppTheme.SAGE -> createLightSagePalette()
            AppTheme.SAND -> createLightSandPalette()
            AppTheme.LAVENDER -> createLightLavenderPalette()
            AppTheme.SKY -> createLightSkyPalette()
            AppTheme.PEACH -> createLightPeachPalette()
            AppTheme.MINT -> createLightMintPalette()
            AppTheme.CUSTOM -> createCustomPalette() // handled above
        }
    }
}

/**
 * Creates a mixed theme palette that combines primary colors from one theme
 * with accent colors from another theme
 */
fun getMixedThemePalette(
    primaryTheme: AppTheme,
    accentTheme: AppTheme,
    darkMode: Boolean = false
): ThemePalette {
    val primaryPalette = getThemePalette(primaryTheme, darkMode)
    if (primaryTheme == accentTheme) return primaryPalette
    val accentPalette = getThemePalette(accentTheme, darkMode)

    return primaryPalette.copy(
        accent = accentPalette.accent,
        accentGradientStart = accentPalette.accentGradientStart,
        accentGradientEnd = accentPalette.accentGradientEnd,
        shade1 = accentPalette.shade1, shade2 = accentPalette.shade2,
        shade3 = accentPalette.shade3, shade4 = accentPalette.shade4,
        shade5 = accentPalette.shade5, shade6 = accentPalette.shade6
    )
}

// One Dark Pro reference colors (used in dark mode fallback)
val OneDarkBackground = Color(0xFF282C34)
val OneDarkSurface = Color(0xFF21252B)
val OneDarkSurfaceMedium = Color(0xFF3E4451)
val OneDarkSurfaceLight = Color(0xFF4B5263)
val OneDarkTextPrimary = Color(0xFFABB2BF)
val OneDarkTextSecondary = Color(0xFF848B98)
val OneDarkTextMuted = Color(0xFF5C6370)
val OneDarkDivider = Color(0xFF3E4451)

// Legacy color constants (referenced by Theme.kt)
val TextPrimary = Color(0xFFABB2BF)
val TextSecondary = Color(0xFF848B98)
val TextMuted = Color(0xFF5C6370)
val AccentGold = Color(0xFFE5C07B)
val AccentCoral = Color(0xFFE06C75)

/**
 * Converts a light theme palette to dark mode
 */
fun ThemePalette.toDarkMode(): ThemePalette {
    val baseColor = primary
    val darkBg = Color(
        red = (0.16f + baseColor.red * 0.04f).coerceIn(0f, 1f),
        green = (0.17f + baseColor.green * 0.04f).coerceIn(0f, 1f),
        blue = (0.20f + baseColor.blue * 0.04f).coerceIn(0f, 1f)
    )
    val darkSurface = Color(
        red = (0.13f + baseColor.red * 0.03f).coerceIn(0f, 1f),
        green = (0.14f + baseColor.green * 0.03f).coerceIn(0f, 1f),
        blue = (0.17f + baseColor.blue * 0.03f).coerceIn(0f, 1f)
    )
    val darkSurfaceMedium = Color(
        red = (0.24f + baseColor.red * 0.05f).coerceIn(0f, 1f),
        green = (0.26f + baseColor.green * 0.05f).coerceIn(0f, 1f),
        blue = (0.31f + baseColor.blue * 0.05f).coerceIn(0f, 1f)
    )
    val darkSurfaceLight = Color(
        red = (0.29f + baseColor.red * 0.06f).coerceIn(0f, 1f),
        green = (0.32f + baseColor.green * 0.06f).coerceIn(0f, 1f),
        blue = (0.38f + baseColor.blue * 0.06f).coerceIn(0f, 1f)
    )
    val darkDivider = Color(
        red = (0.24f + baseColor.red * 0.08f).coerceIn(0f, 1f),
        green = (0.26f + baseColor.green * 0.08f).coerceIn(0f, 1f),
        blue = (0.31f + baseColor.blue * 0.08f).coerceIn(0f, 1f)
    )
    val tp = Color(
        red = (0.67f + baseColor.red * 0.1f).coerceIn(0f, 1f),
        green = (0.70f + baseColor.green * 0.1f).coerceIn(0f, 1f),
        blue = (0.75f + baseColor.blue * 0.1f).coerceIn(0f, 1f)
    )
    val ts = Color(
        red = (0.52f + baseColor.red * 0.08f).coerceIn(0f, 1f),
        green = (0.54f + baseColor.green * 0.08f).coerceIn(0f, 1f),
        blue = (0.59f + baseColor.blue * 0.08f).coerceIn(0f, 1f)
    )
    val tm = Color(
        red = (0.36f + baseColor.red * 0.06f).coerceIn(0f, 1f),
        green = (0.38f + baseColor.green * 0.06f).coerceIn(0f, 1f),
        blue = (0.44f + baseColor.blue * 0.06f).coerceIn(0f, 1f)
    )

    return copy(
        background = darkBg, surface = darkSurface,
        surfaceMedium = darkSurfaceMedium, surfaceLight = darkSurfaceLight,
        surfaceDark = darkSurface,
        onBackground = tp, onSurface = tp,
        textPrimary = tp, textSecondary = ts, textMuted = tm,
        divider = darkDivider, headerBackground = primaryDark,
        shade5 = darkSurfaceLight, shade6 = darkSurfaceMedium,
        shade7 = darkSurface, shade8 = darkBg,
        shade9 = darkSurface, shade10 = darkSurfaceMedium,
        shade11 = darkSurfaceLight, shade12 = darkBg,
        primaryLight = primaryLight, primaryDark = primaryDark
    )
}

/**
 * Creates shades from a base color for the palette
 */
fun createShadesFromColor(baseColor: Color): List<Color> {
    val luminance = baseColor.red * 0.299f + baseColor.green * 0.587f + baseColor.blue * 0.114f
    val isPastel = luminance > 0.65f

    val darkAnchor = if (isPastel) {
        val maxChannel = maxOf(baseColor.red, baseColor.green, baseColor.blue)
        val minChannel = minOf(baseColor.red, baseColor.green, baseColor.blue)
        val saturationBoost = if (maxChannel > 0f) 1f - (minChannel / maxChannel) else 0f
        Color(
            red = (baseColor.red * 0.3f * (1f + saturationBoost)).coerceIn(0f, 0.4f),
            green = (baseColor.green * 0.3f * (1f + saturationBoost)).coerceIn(0f, 0.4f),
            blue = (baseColor.blue * 0.3f * (1f + saturationBoost)).coerceIn(0f, 0.4f)
        )
    } else {
        Color(
            red = (baseColor.red * 0.35f).coerceIn(0f, 1f),
            green = (baseColor.green * 0.35f).coerceIn(0f, 1f),
            blue = (baseColor.blue * 0.35f).coerceIn(0f, 1f)
        )
    }

    val lightTint = Color(
        red = (baseColor.red * 0.2f + 0.80f).coerceIn(0f, 1f),
        green = (baseColor.green * 0.2f + 0.80f).coerceIn(0f, 1f),
        blue = (baseColor.blue * 0.2f + 0.80f).coerceIn(0f, 1f)
    )

    return listOf(
        darkAnchor,
        Color(
            red = (darkAnchor.red * 0.6f + baseColor.red * 0.4f).coerceIn(0f, 1f),
            green = (darkAnchor.green * 0.6f + baseColor.green * 0.4f).coerceIn(0f, 1f),
            blue = (darkAnchor.blue * 0.6f + baseColor.blue * 0.4f).coerceIn(0f, 1f)
        ),
        Color(
            red = (darkAnchor.red * 0.4f + baseColor.red * 0.6f).coerceIn(0f, 1f),
            green = (darkAnchor.green * 0.4f + baseColor.green * 0.6f).coerceIn(0f, 1f),
            blue = (darkAnchor.blue * 0.4f + baseColor.blue * 0.6f).coerceIn(0f, 1f)
        ),
        baseColor,
        Color(
            red = (baseColor.red * 0.85f + lightTint.red * 0.15f).coerceIn(0f, 1f),
            green = (baseColor.green * 0.85f + lightTint.green * 0.15f).coerceIn(0f, 1f),
            blue = (baseColor.blue * 0.85f + lightTint.blue * 0.15f).coerceIn(0f, 1f)
        ),
        Color(
            red = (baseColor.red * 0.7f + lightTint.red * 0.3f).coerceIn(0f, 1f),
            green = (baseColor.green * 0.7f + lightTint.green * 0.3f).coerceIn(0f, 1f),
            blue = (baseColor.blue * 0.7f + lightTint.blue * 0.3f).coerceIn(0f, 1f)
        ),
        Color(
            red = (baseColor.red * 0.55f + lightTint.red * 0.45f).coerceIn(0f, 1f),
            green = (baseColor.green * 0.55f + lightTint.green * 0.45f).coerceIn(0f, 1f),
            blue = (baseColor.blue * 0.55f + lightTint.blue * 0.45f).coerceIn(0f, 1f)
        ),
        Color(
            red = (baseColor.red * 0.4f + lightTint.red * 0.6f).coerceIn(0f, 1f),
            green = (baseColor.green * 0.4f + lightTint.green * 0.6f).coerceIn(0f, 1f),
            blue = (baseColor.blue * 0.4f + lightTint.blue * 0.6f).coerceIn(0f, 1f)
        ),
        Color(
            red = (baseColor.red * 0.25f + lightTint.red * 0.75f).coerceIn(0f, 1f),
            green = (baseColor.green * 0.25f + lightTint.green * 0.75f).coerceIn(0f, 1f),
            blue = (baseColor.blue * 0.25f + lightTint.blue * 0.75f).coerceIn(0f, 1f)
        ),
        Color(
            red = (baseColor.red * 0.15f + lightTint.red * 0.85f).coerceIn(0f, 1f),
            green = (baseColor.green * 0.15f + lightTint.green * 0.85f).coerceIn(0f, 1f),
            blue = (baseColor.blue * 0.15f + lightTint.blue * 0.85f).coerceIn(0f, 1f)
        ),
        Color(
            red = (baseColor.red * 0.08f + lightTint.red * 0.92f).coerceIn(0f, 1f),
            green = (baseColor.green * 0.08f + lightTint.green * 0.92f).coerceIn(0f, 1f),
            blue = (baseColor.blue * 0.08f + lightTint.blue * 0.92f).coerceIn(0f, 1f)
        ),
        lightTint
    )
}

// ═══════════════════════════════════════════════
// GRADIENT HELPERS
// ═══════════════════════════════════════════════

fun ThemePalette.accentGradient(): Brush = SolidColor(shade3)
fun ThemePalette.accentGradientVertical(): Brush = SolidColor(shade3)
fun ThemePalette.accentGradientRadial(): Brush = SolidColor(shade4)
fun ThemePalette.backgroundGradient(): Brush = SolidColor(shade11)
fun ThemePalette.headerGradient(): Brush = SolidColor(shade4)
fun ThemePalette.navBarGradient(): Brush = SolidColor(shade5)
fun ThemePalette.cardGradient(): Brush = SolidColor(shade9)
fun ThemePalette.coverArtGradient(): Brush = SolidColor(shade6)
fun ThemePalette.thumbnailGradient(): Brush = SolidColor(shade6)
fun ThemePalette.buttonGradient(): Brush = SolidColor(shade3)
fun ThemePalette.progressGradient(): Brush = Brush.horizontalGradient(listOf(shade2, shade3, shade4))

// ═══════════════════════════════════════════════
// BACKGROUND THEME (simplified)
// ═══════════════════════════════════════════════

enum class BackgroundTheme(val displayName: String, val baseTheme: AppTheme? = null) {
    DEFAULT("Default (Theme)", null),
    PARCHMENT("Parchment", AppTheme.PARCHMENT),
    ROSEWOOD("Rosewood", AppTheme.ROSEWOOD),
    SAGE("Sage", AppTheme.SAGE),
    SAND("Sand", AppTheme.SAND),
    LAVENDER("Lavender", AppTheme.LAVENDER),
    SKY("Sky", AppTheme.SKY),
    PEACH("Peach", AppTheme.PEACH),
    MINT("Mint", AppTheme.MINT)
}

fun getBackgroundColors(theme: BackgroundTheme, darkMode: Boolean = false): Pair<Color, Color> {
    if (theme == BackgroundTheme.DEFAULT || theme.baseTheme == null) {
        return Pair(Color.Transparent, Color.Transparent)
    }
    val palette = getThemePalette(theme.baseTheme, false)
    return if (darkMode) {
        val darkBg = Color(
            red = (0.10f + palette.accent.red * 0.06f).coerceIn(0f, 0.2f),
            green = (0.10f + palette.accent.green * 0.06f).coerceIn(0f, 0.2f),
            blue = (0.12f + palette.accent.blue * 0.06f).coerceIn(0f, 0.22f)
        )
        val darkSurface = Color(
            red = (0.08f + palette.accent.red * 0.04f).coerceIn(0f, 0.15f),
            green = (0.08f + palette.accent.green * 0.04f).coerceIn(0f, 0.15f),
            blue = (0.10f + palette.accent.blue * 0.04f).coerceIn(0f, 0.18f)
        )
        Pair(darkBg, darkSurface)
    } else {
        Pair(palette.shade11, palette.shade12)
    }
}
