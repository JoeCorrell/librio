package com.librio.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import com.librio.ui.theme.AnimationDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.librio.ui.theme.AppIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.librio.model.PlaybackState
import com.librio.ui.theme.*
import com.librio.utils.formatTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Main playback controls including play/pause, skip, seek, speed, and sleep timer
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun PlaybackControls(
    playbackState: PlaybackState,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onNextChapter: () -> Unit,
    onPreviousChapter: () -> Unit,
    onSeekTo: (Long) -> Unit,
    skipForwardSeconds: Int = 30,
    skipBackSeconds: Int = 30,
    showUndoSeekButton: Boolean = false,
    lastSeekPosition: Long = 0L,
    onUndoSeek: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = currentPalette()
    // Show undo button if enabled and there's a valid position to return to
    val canUndo = showUndoSeekButton && lastSeekPosition > 0L &&
        kotlin.math.abs(playbackState.currentPosition - lastSeekPosition) > 1000L

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Progress slider
        ProgressSlider(
            currentPosition = playbackState.currentPosition,
            duration = playbackState.duration,
            onSeekTo = onSeekTo,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Main controls row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlButton(
                icon = AppIcons.SkipPrevious,
                contentDescription = "Previous chapter",
                onClick = onPreviousChapter,
                size = ControlButtonSize.Small
            )

            SkipButton(
                seconds = skipBackSeconds,
                isForward = false,
                contentDescription = "Rewind $skipBackSeconds seconds",
                onClick = onSeekBackward
            )

            PlayPauseButton(
                isPlaying = playbackState.isPlaying,
                onClick = onPlayPause
            )

            SkipButton(
                seconds = skipForwardSeconds,
                isForward = true,
                contentDescription = "Forward $skipForwardSeconds seconds",
                onClick = onSeekForward
            )

            ControlButton(
                icon = AppIcons.SkipNext,
                contentDescription = "Next chapter",
                onClick = onNextChapter,
                size = ControlButtonSize.Small
            )
        }

        // Undo seek button row
        AnimatedVisibility(
            visible = canUndo,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(palette.accent.copy(alpha = 0.15f))
                        .clickable(onClick = onUndoSeek)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Replay,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = palette.accent
                        )
                        Text(
                            text = "Undo seek",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.accent,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Progress slider with time labels
 */
@Composable
fun ProgressSlider(
    currentPosition: Long,
    duration: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = currentPalette()
    var sliderPosition by remember { mutableStateOf<Float?>(null) }
    val displayPosition = sliderPosition ?: (currentPosition.toFloat() / duration.coerceAtLeast(1).toFloat())

    Column(modifier = modifier) {
        MinimalProgressSlider(
            value = displayPosition.coerceIn(0f, 1f),
            onValueChange = { sliderPosition = it },
            onValueChangeFinished = {
                sliderPosition?.let { position ->
                    onSeekTo((position * duration).toLong())
                }
                sliderPosition = null
            },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(
                    if (sliderPosition != null) (sliderPosition!! * duration).toLong()
                    else currentPosition
                ),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = palette.accent
            )
            Text(
                text = "-${formatTime(duration - currentPosition)}",
                style = MaterialTheme.typography.bodySmall,
                color = palette.accent.copy(alpha = 0.5f)
            )
        }
    }
}

enum class ControlButtonSize { Small, Medium, Large }

/**
 * Skip button showing the seconds value overlaid on the icon
 */
@Composable
fun SkipButton(
    seconds: Int,
    isForward: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = currentPalette()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) AnimationDefaults.ButtonPressScale else 1f,
        animationSpec = AnimationDefaults.snappySpring(),
        label = "scale"
    )

    Box(
        modifier = modifier
            .size(56.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(palette.accent.copy(alpha = 0.1f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Use Replay icon for both, but flip horizontally for forward
        Icon(
            imageVector = AppIcons.Replay,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(34.dp)
                .graphicsLayer(scaleX = if (isForward) -1f else 1f),
            tint = palette.accent
        )
        Text(
            text = "$seconds",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = palette.accent,
            fontSize = 10.sp
        )
    }
}

/**
 * Reusable control button with press animation
 */
@Composable
fun ControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: ControlButtonSize = ControlButtonSize.Medium,
    modifier: Modifier = Modifier,
) {
    val palette = currentPalette()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) AnimationDefaults.ButtonPressScale else 1f,
        animationSpec = AnimationDefaults.snappySpring(),
        label = "scale"
    )

    val buttonSize = when (size) {
        ControlButtonSize.Small -> 44.dp
        ControlButtonSize.Medium -> 56.dp
        ControlButtonSize.Large -> 72.dp
    }

    val iconSize = when (size) {
        ControlButtonSize.Small -> 26.dp
        ControlButtonSize.Medium -> 34.dp
        ControlButtonSize.Large -> 42.dp
    }

    Box(
        modifier = modifier
            .size(buttonSize)
            .scale(scale)
            .clip(CircleShape)
            .background(palette.accent.copy(alpha = 0.1f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = palette.accent
        )
    }
}

/**
 * Large play/pause button with animated state
 */
@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = currentPalette()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) AnimationDefaults.ButtonPressScale else 1f,
        animationSpec = AnimationDefaults.snappySpring(),
        label = "scale"
    )

    Box(
        modifier = modifier
            .size(84.dp)
            .scale(scale)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(palette.accentGradient())
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) AppIcons.Pause else AppIcons.Play,
            contentDescription = if (isPlaying) "Pause" else "Play",
            modifier = Modifier.size(48.dp),
            tint = palette.onPrimary
        )
    }
}

/**
 * Legacy speed control for backwards compatibility
 */
@Composable
fun SpeedControl(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // This is now replaced by the new SpeedButton + SpeedSelectorSheet
    // Keeping for backwards compatibility
}
