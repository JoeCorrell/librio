package com.librio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit

/**
 * Window size classes for responsive design
 */
enum class WindowSizeClass {
    COMPACT,    // Phone portrait (< 600dp)
    MEDIUM,     // Phone landscape / small tablet (600-840dp)
    EXPANDED    // Tablet / desktop (> 840dp)
}

/**
 * Height size classes for responsive design
 */
enum class HeightSizeClass {
    COMPACT,    // Short screens (< 500dp)
    NORMAL,     // Normal screens (500-700dp)
    EXPANDED    // Tall screens (> 700dp)
}

/**
 * Get the current window size class based on screen width
 */
@Composable
fun rememberWindowSizeClass(): WindowSizeClass {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp

    return when {
        screenWidthDp < 600 -> WindowSizeClass.COMPACT
        screenWidthDp < 840 -> WindowSizeClass.MEDIUM
        else -> WindowSizeClass.EXPANDED
    }
}

/**
 * Get the current height size class based on screen height
 */
@Composable
fun rememberHeightSizeClass(): HeightSizeClass {
    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp

    return when {
        screenHeightDp < 500 -> HeightSizeClass.COMPACT
        screenHeightDp < 700 -> HeightSizeClass.NORMAL
        else -> HeightSizeClass.EXPANDED
    }
}

/**
 * Responsive dimensions that scale based on screen size
 */
data class ResponsiveDimens(
    val cardWidth: Dp,
    val cardHeight: Dp,
    val coverArtSize: Dp,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val titleTextSize: Int,
    val bodyTextSize: Int,
    val labelTextSize: Int,
    val iconSize: Dp,
    val iconSizeSmall: Dp,
    val iconSizeLarge: Dp,
    val buttonHeight: Dp,
    val buttonHeightSmall: Dp,
    val carouselHeight: Dp,
    val splashLogoSize: Dp,
    val columns: Int,
    val spacing: Dp,
    val spacingSmall: Dp,
    val spacingLarge: Dp,
    val cornerRadius: Dp,
    val cornerRadiusSmall: Dp,
    val cornerRadiusLarge: Dp,
    val dialogWidth: Dp,
    val dialogMaxHeight: Dp,
    val playerCoverArtSize: Dp,
    val miniPlayerHeight: Dp,
    val navBarHeight: Dp,
    val headerHeight: Dp,
    val tabHeight: Dp,
    val chipHeight: Dp,
    val sliderHeight: Dp,
    val progressBarHeight: Dp,
    val dividerThickness: Dp,
    val touchTargetSize: Dp,
    val screenWidthDp: Dp,
    val screenHeightDp: Dp,
    val isCompactHeight: Boolean,
    val isWideScreen: Boolean
)

/**
 * Get responsive dimensions based on window size class
 * @param denseGrid When true, use more columns for a denser grid (useful for square mode)
 */
@Suppress("UNUSED_VARIABLE")
@Composable
fun rememberResponsiveDimens(denseGrid: Boolean = false): ResponsiveDimens {
    val windowSize = rememberWindowSizeClass()
    val heightSize = rememberHeightSizeClass()
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp
    val isCompactHeight = heightSize == HeightSizeClass.COMPACT
    val isWideScreen = windowSize != WindowSizeClass.COMPACT

    return remember(windowSize, heightSize, screenWidthDp, screenHeightDp, denseGrid) {
        when (windowSize) {
            WindowSizeClass.COMPACT -> {
                // Always 2 columns on phones, scaled to fit titles properly
                val columns = 2
                val cardWidth = (screenWidthDp - 48.dp) / 2  // 2 cards with 16dp padding on sides + 16dp gap
                ResponsiveDimens(
                    cardWidth = cardWidth,
                    cardHeight = if (isCompactHeight) 220.dp else 260.dp,
                    coverArtSize = cardWidth - 16.dp,
                    horizontalPadding = 16.dp,
                    verticalPadding = if (isCompactHeight) 8.dp else 12.dp,
                    titleTextSize = 14,
                    bodyTextSize = 12,
                    labelTextSize = 10,
                    iconSize = 24.dp,
                    iconSizeSmall = 18.dp,
                    iconSizeLarge = 32.dp,
                    buttonHeight = if (isCompactHeight) 44.dp else 52.dp,
                    buttonHeightSmall = if (isCompactHeight) 32.dp else 40.dp,
                    carouselHeight = minOf(if (isCompactHeight) 260.dp else 320.dp, screenHeightDp * 0.45f),
                    splashLogoSize = minOf(if (isCompactHeight) 120.dp else 160.dp, screenWidthDp * 0.4f),
                    columns = columns,
                    spacing = if (isCompactHeight) 8.dp else 12.dp,
                    spacingSmall = if (isCompactHeight) 4.dp else 6.dp,
                    spacingLarge = if (isCompactHeight) 12.dp else 16.dp,
                    cornerRadius = 12.dp,
                    cornerRadiusSmall = 8.dp,
                    cornerRadiusLarge = 16.dp,
                    dialogWidth = screenWidthDp * 0.9f,
                    dialogMaxHeight = screenHeightDp * 0.85f,
                    playerCoverArtSize = minOf(screenWidthDp * 0.7f, screenHeightDp * 0.35f, 280.dp),
                    miniPlayerHeight = if (isCompactHeight) 56.dp else 64.dp,
                    navBarHeight = if (isCompactHeight) 52.dp else 56.dp,
                    headerHeight = if (isCompactHeight) 44.dp else 48.dp,
                    tabHeight = if (isCompactHeight) 40.dp else 48.dp,
                    chipHeight = if (isCompactHeight) 28.dp else 32.dp,
                    sliderHeight = 24.dp,
                    progressBarHeight = 4.dp,
                    dividerThickness = 1.dp,
                    touchTargetSize = 48.dp,
                    screenWidthDp = screenWidthDp,
                    screenHeightDp = screenHeightDp,
                    isCompactHeight = isCompactHeight,
                    isWideScreen = false
                )
            }
            WindowSizeClass.MEDIUM -> {
                // 3 columns on medium tablets, properly scaled
                val columns = 3
                val cardWidth = (screenWidthDp - 64.dp) / 3  // 3 cards with padding and gaps
                ResponsiveDimens(
                    cardWidth = cardWidth,
                    cardHeight = 280.dp,
                    coverArtSize = cardWidth - 16.dp,
                    horizontalPadding = 20.dp,
                    verticalPadding = 12.dp,
                    titleTextSize = 15,
                    bodyTextSize = 13,
                    labelTextSize = 11,
                    iconSize = 26.dp,
                    iconSizeSmall = 20.dp,
                    iconSizeLarge = 36.dp,
                    buttonHeight = 56.dp,
                    buttonHeightSmall = 44.dp,
                    carouselHeight = 320.dp,
                    splashLogoSize = 180.dp,
                    columns = columns,
                    spacing = 12.dp,
                    spacingSmall = 6.dp,
                    spacingLarge = 20.dp,
                    cornerRadius = 14.dp,
                    cornerRadiusSmall = 10.dp,
                    cornerRadiusLarge = 20.dp,
                    dialogWidth = minOf(screenWidthDp * 0.8f, 480.dp),
                    dialogMaxHeight = screenHeightDp * 0.8f,
                    playerCoverArtSize = minOf(screenWidthDp * 0.5f, screenHeightDp * 0.4f, 320.dp),
                    miniPlayerHeight = 68.dp,
                    navBarHeight = 60.dp,
                    headerHeight = 52.dp,
                    tabHeight = 52.dp,
                    chipHeight = 34.dp,
                    sliderHeight = 28.dp,
                    progressBarHeight = 4.dp,
                    dividerThickness = 1.dp,
                    touchTargetSize = 48.dp,
                    screenWidthDp = screenWidthDp,
                    screenHeightDp = screenHeightDp,
                    isCompactHeight = isCompactHeight,
                    isWideScreen = true
                )
            }
            WindowSizeClass.EXPANDED -> {
                // 3 columns on large tablets, properly scaled with larger cards
                val columns = 3
                val cardWidth = (screenWidthDp - 72.dp) / 3  // 3 cards with padding and gaps
                ResponsiveDimens(
                    cardWidth = cardWidth,
                    cardHeight = 320.dp,
                    coverArtSize = cardWidth - 20.dp,
                    horizontalPadding = 24.dp,
                    verticalPadding = 16.dp,
                    titleTextSize = 16,
                    bodyTextSize = 14,
                    labelTextSize = 12,
                    iconSize = 28.dp,
                    iconSizeSmall = 22.dp,
                    iconSizeLarge = 40.dp,
                    buttonHeight = 60.dp,
                    buttonHeightSmall = 48.dp,
                    carouselHeight = 340.dp,
                    splashLogoSize = 200.dp,
                    columns = columns,
                    spacing = 16.dp,
                    spacingSmall = 8.dp,
                    spacingLarge = 24.dp,
                    cornerRadius = 16.dp,
                    cornerRadiusSmall = 12.dp,
                    cornerRadiusLarge = 24.dp,
                    dialogWidth = minOf(screenWidthDp * 0.6f, 560.dp),
                    dialogMaxHeight = screenHeightDp * 0.75f,
                    playerCoverArtSize = minOf(screenWidthDp * 0.4f, screenHeightDp * 0.45f, 360.dp),
                    miniPlayerHeight = 72.dp,
                    navBarHeight = 64.dp,
                    headerHeight = 56.dp,
                    tabHeight = 56.dp,
                    chipHeight = 36.dp,
                    sliderHeight = 32.dp,
                    progressBarHeight = 5.dp,
                    dividerThickness = 1.dp,
                    touchTargetSize = 52.dp,
                    screenWidthDp = screenWidthDp,
                    screenHeightDp = screenHeightDp,
                    isCompactHeight = isCompactHeight,
                    isWideScreen = true
                )
            }
        }
    }
}

/**
 * Get the number of visible items in carousel based on screen width
 */
@Composable
fun rememberCarouselItemCount(): Int {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp

    return when {
        screenWidthDp < 400 -> 2
        screenWidthDp < 600 -> 2
        screenWidthDp < 840 -> 3
        else -> 4
    }
}

/**
 * Calculate number of grid columns based on available width and minimum card width
 */
@Composable
fun rememberGridColumns(minCardWidth: Dp = 140.dp): Int {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val padding = 32.dp // Total horizontal padding
    val availableWidth = screenWidthDp - padding
    val spacing = 12.dp

    // Calculate how many cards fit with spacing
    val columns = ((availableWidth + spacing) / (minCardWidth + spacing)).toInt()
    return columns.coerceIn(2, 6)
}

/**
 * Get responsive text sizes
 */
@Composable
fun rememberResponsiveTextSizes(): ResponsiveTextSizes {
    val windowSize = rememberWindowSizeClass()
    val heightSize = rememberHeightSizeClass()
    val isCompactHeight = heightSize == HeightSizeClass.COMPACT

    return remember(windowSize, isCompactHeight) {
        when (windowSize) {
            WindowSizeClass.COMPACT -> ResponsiveTextSizes(
                headline = if (isCompactHeight) 20.sp else 24.sp,
                title = if (isCompactHeight) 16.sp else 18.sp,
                body = if (isCompactHeight) 13.sp else 14.sp,
                label = if (isCompactHeight) 10.sp else 11.sp,
                caption = if (isCompactHeight) 9.sp else 10.sp
            )
            WindowSizeClass.MEDIUM -> ResponsiveTextSizes(
                headline = 26.sp,
                title = 20.sp,
                body = 15.sp,
                label = 12.sp,
                caption = 11.sp
            )
            WindowSizeClass.EXPANDED -> ResponsiveTextSizes(
                headline = 28.sp,
                title = 22.sp,
                body = 16.sp,
                label = 13.sp,
                caption = 12.sp
            )
        }
    }
}

/**
 * Responsive text size values
 */
data class ResponsiveTextSizes(
    val headline: TextUnit,
    val title: TextUnit,
    val body: TextUnit,
    val label: TextUnit,
    val caption: TextUnit
)
