package com.librio.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import com.librio.ui.theme.AnimationDefaults
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.librio.ui.theme.currentPalette
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A minimal, custom slider that handles scroll gestures properly.
 * Only activates when user taps and holds, not during vertical scrolling.
 *
 * Uses Canvas for full control over appearance - no Material3 dependencies.
 */
@Composable
fun MinimalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    thumbSize: Dp = 14.dp,
    trackHeight: Dp = 4.dp,
    activeColor: Color? = null,
    inactiveColor: Color? = null
) {
    val palette = currentPalette()
    val density = LocalDensity.current

    val thumbColor = activeColor ?: palette.accent
    val activeTrackColor = activeColor ?: palette.accent
    val inactiveTrackColor = inactiveColor ?: palette.surfaceLight.copy(alpha = 0.4f)

    var isDragging by remember { mutableStateOf(false) }
    var sliderWidth by remember { mutableFloatStateOf(0f) }

    // Snap to steps if defined
    fun snapValue(rawProgress: Float): Float {
        val progress = rawProgress.coerceIn(0f, 1f)
        if (steps <= 0) {
            return valueRange.start + progress * (valueRange.endInclusive - valueRange.start)
        }
        val stepProgress = (progress * steps).roundToInt().toFloat() / steps
        return valueRange.start + stepProgress * (valueRange.endInclusive - valueRange.start)
    }

    // Normalize value to 0-1
    val normalizedValue = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start))
        .coerceIn(0f, 1f)

    // Animate the progress
    val animatedProgress by animateFloatAsState(
        targetValue = normalizedValue,
        animationSpec = AnimationDefaults.smoothSpring(),
        label = "sliderProgress"
    )

    // Animate thumb size when dragging
    val animatedThumbSize by animateDpAsState(
        targetValue = if (isDragging) thumbSize + 4.dp else thumbSize,
        animationSpec = AnimationDefaults.standardSpring(),
        label = "thumbSize"
    )

    // Animate track height when dragging
    val animatedTrackHeight by animateDpAsState(
        targetValue = if (isDragging) trackHeight + 1.dp else trackHeight,
        animationSpec = AnimationDefaults.standardSpring(),
        label = "trackHeight"
    )

    val thumbSizePx = with(density) { animatedThumbSize.toPx() }
    val trackHeightPx = with(density) { animatedTrackHeight.toPx() }
    val touchSlop = with(density) { 8.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thumbSize + 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(thumbSize + 16.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput

                    awaitEachGesture {
                        // Don't consume the down event - let parent scroll handle it first
                        val down = awaitFirstDown(requireUnconsumed = false)
                        sliderWidth = size.width.toFloat()

                        var totalDragX = 0f
                        var totalDragY = 0f
                        var isSliderActive = false
                        var hasMoved = false
                        var gestureDecided = false

                        // Check if initial touch is directly on the thumb
                        val thumbX = animatedProgress * (sliderWidth - thumbSizePx) + thumbSizePx / 2
                        val touchX = down.position.x
                        val isOnThumb = abs(touchX - thumbX) < thumbSizePx * 1.5f

                        // DON'T set isDragging = true yet - wait for gesture decision

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break

                            if (change.pressed) {
                                val dragDelta = change.positionChange()
                                totalDragX += abs(dragDelta.x)
                                totalDragY += abs(dragDelta.y)

                                // Wait for enough movement to decide gesture type
                                if (!gestureDecided && (totalDragX > touchSlop || totalDragY > touchSlop)) {
                                    gestureDecided = true
                                    // Only activate slider if:
                                    // 1. Horizontal movement is significantly more than vertical, OR
                                    // 2. User is dragging directly on the thumb
                                    isSliderActive = totalDragX > totalDragY * 2f || (isOnThumb && totalDragX > totalDragY * 0.5f)

                                    if (!isSliderActive) {
                                        // This is a vertical scroll gesture - don't interfere
                                        break
                                    }
                                    // Now we know it's a slider gesture
                                    isDragging = true
                                }

                                if (isSliderActive) {
                                    // Update slider value and consume the event
                                    val progress = (change.position.x / sliderWidth).coerceIn(0f, 1f)
                                    val newValue = snapValue(progress)
                                    onValueChange(newValue)
                                    change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        // Handle tap (no movement) - only if touch was on the track area
                        if (!hasMoved && !gestureDecided) {
                            val progress = (down.position.x / sliderWidth).coerceIn(0f, 1f)
                            val newValue = snapValue(progress)
                            onValueChange(newValue)
                            onValueChangeFinished?.invoke()
                        } else if (isDragging) {
                            onValueChangeFinished?.invoke()
                        }
                        isDragging = false
                    }
                }
        ) {
            sliderWidth = size.width
            val centerY = size.height / 2
            val trackRadius = trackHeightPx / 2
            val thumbRadius = thumbSizePx / 2

            // Track padding to account for thumb
            val trackStartX = thumbRadius
            val trackEndX = size.width - thumbRadius
            val trackWidth = trackEndX - trackStartX

            // Draw inactive track (full width)
            drawRoundRect(
                color = inactiveTrackColor,
                topLeft = Offset(trackStartX, centerY - trackHeightPx / 2),
                size = Size(trackWidth, trackHeightPx),
                cornerRadius = CornerRadius(trackRadius, trackRadius)
            )

            // Draw active track
            val activeWidth = animatedProgress * trackWidth
            if (activeWidth > 0) {
                drawRoundRect(
                    color = activeTrackColor,
                    topLeft = Offset(trackStartX, centerY - trackHeightPx / 2),
                    size = Size(activeWidth, trackHeightPx),
                    cornerRadius = CornerRadius(trackRadius, trackRadius)
                )
            }

            // Draw thumb
            val thumbX = trackStartX + animatedProgress * trackWidth

            // Thumb shadow
            drawCircle(
                color = Color.Black.copy(alpha = 0.15f),
                radius = thumbRadius + 1.dp.toPx(),
                center = Offset(thumbX, centerY + 1.dp.toPx())
            )

            // Thumb
            drawCircle(
                color = thumbColor,
                radius = thumbRadius,
                center = Offset(thumbX, centerY)
            )

            // Thumb highlight
            drawCircle(
                color = Color.White.copy(alpha = 0.25f),
                radius = thumbRadius * 0.4f,
                center = Offset(thumbX - thumbRadius * 0.15f, centerY - thumbRadius * 0.15f)
            )
        }
    }
}

/**
 * A minimal progress slider specifically for media playback.
 * Thinner and cleaner than the settings slider.
 */
@Composable
fun MinimalProgressSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    thumbSize: Dp = 12.dp,
    trackHeight: Dp = 3.dp,
    activeColor: Color? = null,
    inactiveColor: Color? = null
) {
    MinimalSlider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = 0f..1f,
        steps = 0,
        onValueChangeFinished = onValueChangeFinished,
        thumbSize = thumbSize,
        trackHeight = trackHeight,
        activeColor = activeColor,
        inactiveColor = inactiveColor
    )
}

/**
 * Custom linear progress indicator to replace Material3 LinearProgressIndicator.
 * Minimal design that matches the custom slider aesthetic.
 */
@Composable
fun LibrioProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 3.dp,
    activeColor: Color? = null,
    trackColor: Color? = null,
    cornerRadius: Dp = 2.dp
) {
    val palette = currentPalette()
    val fillColor = activeColor ?: palette.accent
    val backgroundColor = trackColor ?: palette.surfaceLight.copy(alpha = 0.3f)

    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = AnimationDefaults.smoothSpring(),
        label = "progressBar"
    )

    val cornerRadiusPx = with(LocalDensity.current) { cornerRadius.toPx() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val trackHeight = size.height
        val radius = CornerRadius(cornerRadiusPx, cornerRadiusPx)

        // Draw track background
        drawRoundRect(
            color = backgroundColor,
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = radius
        )

        // Draw progress fill
        if (animatedProgress > 0) {
            drawRoundRect(
                color = fillColor,
                topLeft = Offset.Zero,
                size = Size(size.width * animatedProgress, trackHeight),
                cornerRadius = radius
            )
        }
    }
}
