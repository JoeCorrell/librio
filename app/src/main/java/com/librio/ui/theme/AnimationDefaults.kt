package com.librio.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition

/**
 * Centralized animation configuration for consistent feel across the app.
 * All animations should use these values instead of hardcoded specs.
 */
object AnimationDefaults {

    // ========== DURATIONS ==========

    /** Quick interactions like button feedback (100ms) */
    const val DurationFast = 100

    /** Standard transitions (150ms) */
    const val DurationNormal = 150

    /** Slower, more deliberate animations (250ms) */
    const val DurationSlow = 250

    // ========== SCALE VALUES ==========

    /** Standard button press scale */
    const val ButtonPressScale = 0.95f

    /** Subtle press scale for larger items */
    const val ItemPressScale = 0.98f

    // ========== SPRING SPECS ==========

    /** Snappy, no bounce - for quick button feedback */
    fun <T> snappySpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )

    /** Standard spring - smooth with slight bounce */
    fun <T> standardSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    /** Smooth spring - for sliders and progress */
    fun <T> smoothSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    // ========== TWEEN SPECS ==========

    /** Fast tween for quick transitions */
    fun <T> fastTween() = tween<T>(DurationFast)

    /** Standard tween for normal transitions */
    fun <T> normalTween() = tween<T>(DurationNormal)

    /** Slow tween for deliberate transitions */
    fun <T> slowTween() = tween<T>(DurationSlow)

    // ========== VISIBILITY TRANSITIONS ==========

    /** Standard fade in */
    fun fadeInTransition(): EnterTransition = fadeIn(animationSpec = tween(DurationNormal))

    /** Standard fade out */
    fun fadeOutTransition(): ExitTransition = fadeOut(animationSpec = tween(DurationFast))

    /** Expand vertically with fade */
    fun expandFadeIn(): EnterTransition =
        expandVertically(animationSpec = tween(DurationNormal)) + fadeIn(animationSpec = tween(DurationNormal))

    /** Shrink vertically with fade */
    fun shrinkFadeOut(): ExitTransition =
        shrinkVertically(animationSpec = tween(DurationNormal)) + fadeOut(animationSpec = tween(DurationFast))

    /** Slide in from right with fade */
    fun slideInRight(): EnterTransition =
        slideInHorizontally(animationSpec = tween(DurationNormal)) { it / 4 } + fadeIn(animationSpec = tween(DurationNormal))

    /** Slide out to left with fade */
    fun slideOutLeft(): ExitTransition =
        slideOutHorizontally(animationSpec = tween(DurationNormal)) { -it / 4 } + fadeOut(animationSpec = tween(DurationFast))

    /** Slide in from left with fade */
    fun slideInLeft(): EnterTransition =
        slideInHorizontally(animationSpec = tween(DurationNormal)) { -it / 4 } + fadeIn(animationSpec = tween(DurationNormal))

    /** Slide out to right with fade */
    fun slideOutRight(): ExitTransition =
        slideOutHorizontally(animationSpec = tween(DurationNormal)) { it / 4 } + fadeOut(animationSpec = tween(DurationFast))
}
