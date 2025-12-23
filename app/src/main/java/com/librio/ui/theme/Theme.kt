package com.librio.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * Local composition for accessing theme colors throughout the app
 */
val LocalThemePalette = compositionLocalOf { TealPalette }

/**
 * Local composition for accessing corner radius setting
 */
val LocalUseSquareCorners = compositionLocalOf { false }

/**
 * Get the current theme palette
 */
@Composable
fun currentPalette(): ThemePalette {
    return LocalThemePalette.current
}

/**
 * Get corner radius based on square corners setting
 */
@Composable
fun cornerRadius(dp: Dp): Shape {
    val useSquare = LocalUseSquareCorners.current
    return if (useSquare) RectangleShape else RoundedCornerShape(dp)
}

/**
 * Get dp value for corner radius (0dp if square, otherwise the provided value)
 */
@Composable
fun cornerRadiusDp(dp: Dp): Dp {
    val useSquare = LocalUseSquareCorners.current
    return if (useSquare) 0.dp else dp
}

private fun createDarkColorScheme(palette: ThemePalette) = darkColorScheme(
    primary = palette.primary,
    onPrimary = Color.White,
    primaryContainer = palette.primaryDark,
    onPrimaryContainer = palette.primaryLight,
    secondary = palette.accent,
    onSecondary = Color.Black,
    secondaryContainer = palette.surfaceLight,
    onSecondaryContainer = palette.primaryLight,
    tertiary = AccentGold,
    onTertiary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = palette.surfaceCard,
    onSurfaceVariant = TextSecondary,
    outline = TextMuted,
    inverseSurface = TextPrimary,
    inverseOnSurface = palette.surfaceDark,
)

private fun createLightColorScheme(palette: ThemePalette) = lightColorScheme(
    primary = palette.primary,
    onPrimary = Color.White,
    primaryContainer = palette.primaryLight,
    onPrimaryContainer = palette.primaryDark,
    secondary = palette.accent,
    onSecondary = Color.Black,
    secondaryContainer = palette.primaryLight.copy(alpha = 0.3f),
    onSecondaryContainer = palette.primaryDark,
    tertiary = AccentCoral,
    onTertiary = Color.White,
    background = palette.background,
    onBackground = palette.onBackground,
    surface = palette.surface,
    onSurface = palette.onSurface,
    surfaceVariant = palette.primaryLight.copy(alpha = 0.1f),
    onSurfaceVariant = palette.textSecondary,
    outline = palette.textSecondary,
)

@Composable
fun AudiobookPlayerTheme(
    appTheme: AppTheme = AppTheme.TEAL,
    accentTheme: AppTheme = appTheme,
    darkTheme: Boolean = false,
    customPrimaryColor: Int = 0x00897B, // Used to trigger recomposition for custom theme
    useSquareCorners: Boolean = false,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    content: @Composable () -> Unit
) {
    // Update CustomThemeColors when custom color changes
    if (appTheme == AppTheme.CUSTOM) {
        CustomThemeColors.baseColor = Color(customPrimaryColor.toLong() or 0xFF000000L)
    }

    // Cache palette/colorScheme per inputs to avoid repeated heavy recomputation during quick theme switches
    val palette = remember(appTheme, accentTheme, darkTheme, customPrimaryColor) {
        if (accentTheme != appTheme) {
            getMixedThemePalette(appTheme, accentTheme, darkTheme)
        } else {
            getThemePalette(appTheme, darkTheme)
        }
    }
    val colorScheme = remember(palette, darkTheme) {
        if (darkTheme) createDarkColorScheme(palette) else createLightColorScheme(palette)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        // Use SideEffect for immediate synchronous updates during composition
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = if (darkTheme) OneDarkBackground.toArgb() else palette.shade2.toArgb()
            window.navigationBarColor = if (darkTheme) OneDarkBackground.toArgb() else palette.shade9.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    val typography = remember(fontFamily) {
        if (fontFamily == null) {
            Typography
        } else {
            androidx.compose.material3.Typography(
                displayLarge = Typography.displayLarge.copy(fontFamily = fontFamily),
                displayMedium = Typography.displayMedium.copy(fontFamily = fontFamily),
                displaySmall = Typography.displaySmall.copy(fontFamily = fontFamily),
                headlineLarge = Typography.headlineLarge.copy(fontFamily = fontFamily),
                headlineMedium = Typography.headlineMedium.copy(fontFamily = fontFamily),
                headlineSmall = Typography.headlineSmall.copy(fontFamily = fontFamily),
                titleLarge = Typography.titleLarge.copy(fontFamily = fontFamily),
                titleMedium = Typography.titleMedium.copy(fontFamily = fontFamily),
                titleSmall = Typography.titleSmall.copy(fontFamily = fontFamily),
                bodyLarge = Typography.bodyLarge.copy(fontFamily = fontFamily),
                bodyMedium = Typography.bodyMedium.copy(fontFamily = fontFamily),
                bodySmall = Typography.bodySmall.copy(fontFamily = fontFamily),
                labelLarge = Typography.labelLarge.copy(fontFamily = fontFamily),
                labelMedium = Typography.labelMedium.copy(fontFamily = fontFamily),
                labelSmall = Typography.labelSmall.copy(fontFamily = fontFamily)
            )
        }
    }

    CompositionLocalProvider(
        LocalThemePalette provides palette,
        LocalUseSquareCorners provides useSquareCorners
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
