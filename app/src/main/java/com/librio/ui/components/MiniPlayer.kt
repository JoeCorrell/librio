package com.librio.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.librio.ui.theme.AppIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import com.librio.ui.theme.*
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * Represents what is currently playing - either music or audiobook
 */
sealed class NowPlaying {
    abstract val id: String
    abstract val title: String
    abstract val subtitle: String
    abstract val coverArt: Bitmap?
    abstract val duration: Long
    abstract val currentPosition: Long
    abstract val isPlaying: Boolean

    data class Music(
        override val id: String,
        override val title: String,
        override val subtitle: String, // Artist name
        override val coverArt: Bitmap?,
        override val duration: Long,
        override val currentPosition: Long,
        override val isPlaying: Boolean,
        val hasNext: Boolean,
        val hasPrevious: Boolean
    ) : NowPlaying()

    data class Audiobook(
        override val id: String,
        override val title: String,
        override val subtitle: String, // Author name
        override val coverArt: Bitmap?,
        override val duration: Long,
        override val currentPosition: Long,
        override val isPlaying: Boolean,
        val chapterInfo: String? = null
    ) : NowPlaying()
}

/**
 * Slim mini player that shows above the navbar
 * Uses light and dark shades of the palette to blend in nicely
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(
    nowPlaying: NowPlaying?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onClick: () -> Unit,
    onDismiss: () -> Unit = {},
    showPlaceholderIcons: Boolean = true,
    modifier: Modifier = Modifier
) {
    val palette = currentPalette()
    val shape8 = cornerRadius(8.dp)
    val density = LocalDensity.current

    // Swipe state - track horizontal offset
    var offsetX by remember { mutableFloatStateOf(0f) }
    var isDismissing by remember { mutableStateOf(false) }
    val dismissThreshold = with(density) { 150.dp.toPx() }

    // Animate offset when not dragging or dismissing
    val animatedOffset by animateFloatAsState(
        targetValue = if (isDismissing) {
            // Animate off screen in the direction of swipe
            if (offsetX > 0) with(density) { 500.dp.toPx() } else with(density) { -500.dp.toPx() }
        } else {
            offsetX
        },
        animationSpec = if (isDismissing) {
            tween(durationMillis = 200, easing = FastOutSlowInEasing)
        } else {
            spring(stiffness = Spring.StiffnessHigh)
        },
        finishedListener = {
            if (isDismissing) {
                onDismiss()
                // Reset state after dismiss
                offsetX = 0f
                isDismissing = false
            }
        },
        label = "swipe_offset"
    )

    // Calculate opacity based on swipe distance
    val swipeAlpha = 1f - (animatedOffset.absoluteValue / dismissThreshold).coerceIn(0f, 0.5f)

    AnimatedVisibility(
        visible = nowPlaying != null && !isDismissing,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        nowPlaying?.let { playing ->
            // Progress as fraction
            val progress = if (playing.duration > 0) {
                playing.currentPosition.toFloat() / playing.duration
            } else 0f

            // Track if we're currently dragging
            var isDragging by remember { mutableStateOf(false) }

            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                    .graphicsLayer { alpha = swipeAlpha }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                // Wait for initial press
                                var event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.none { it.pressed }) continue

                                var totalDragX = 0f
                                var totalDragY = 0f
                                var dragStarted = false

                                while (true) {
                                    event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull() ?: break

                                    if (!change.pressed) {
                                        // Finger lifted
                                        if (dragStarted) {
                                            if (offsetX.absoluteValue > dismissThreshold) {
                                                isDismissing = true
                                            } else {
                                                offsetX = 0f
                                            }
                                            isDragging = false
                                        }
                                        break
                                    }

                                    val dragAmount = change.positionChange()
                                    totalDragX += dragAmount.x
                                    totalDragY += dragAmount.y

                                    // Check if horizontal drag is dominant (start after 10px movement)
                                    if (!dragStarted && (totalDragX.absoluteValue > 10f || totalDragY.absoluteValue > 10f)) {
                                        if (totalDragX.absoluteValue > totalDragY.absoluteValue * 1.5f) {
                                            // Horizontal swipe detected - start dragging
                                            dragStarted = true
                                            isDragging = true
                                        } else {
                                            // Vertical or diagonal - don't intercept
                                            break
                                        }
                                    }

                                    if (dragStarted) {
                                        // Consume the event and update offset
                                        change.consume()
                                        offsetX += dragAmount.x
                                    }
                                }
                            }
                        }
                    }
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                palette.shade8.copy(alpha = 0.95f),
                                palette.shade10.copy(alpha = 0.95f),
                                palette.shade8.copy(alpha = 0.95f)
                            )
                        )
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
            ) {
                Column {
                    // Progress bar at top - thin line
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(palette.shade4.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(3.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            palette.shade2,
                                            palette.shade3,
                                            palette.shade4
                                        )
                                    )
                                )
                        )
                    }

                    // Main content row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cover art thumbnail
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(shape8)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            palette.shade3,
                                            palette.shade4,
                                            palette.shade5
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            val coverArt = playing.coverArt
                            if (coverArt == null || showPlaceholderIcons) {
                                Icon(
                                    imageVector = when (playing) {
                                        is NowPlaying.Music -> AppIcons.Music
                                        is NowPlaying.Audiobook -> AppIcons.Audiobook
                                    },
                                    contentDescription = null,
                                    tint = palette.shade1,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                val imageBitmap = remember(coverArt) { coverArt.asImageBitmap() }
                                Image(
                                    bitmap = imageBitmap,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Title and subtitle
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = playing.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.shade1,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    delayMillis = 2000,
                                    initialDelayMillis = 1500,
                                    velocity = 30.dp
                                )
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = playing.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.shade2.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = formatMiniPlayerTime(playing.duration),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.shade3.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Playback controls
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Seek back for audiobooks, previous track for music
                            if (playing is NowPlaying.Music) {
                                IconButton(
                                    onClick = onPrevious,
                                    enabled = playing.hasPrevious,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        AppIcons.SkipPrevious,
                                        contentDescription = "Previous",
                                        tint = if (playing.hasPrevious) palette.shade2 else palette.shade4,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = onSeekBack,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        AppIcons.Replay10,
                                        contentDescription = "Skip Back",
                                        tint = palette.shade2,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Play/Pause button with gradient background
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                            .background(palette.accent)
                                    .clickable(onClick = onPlayPause),
                                contentAlignment = Alignment.Center
                            ) {
                                // Animated play/pause icon (lighter for better contrast)
                                Icon(
                                    imageVector = if (playing.isPlaying) AppIcons.Pause else AppIcons.Play,
                                    contentDescription = if (playing.isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Next/forward button (music or audiobook)
                            if (playing is NowPlaying.Music) {
                                IconButton(
                                    onClick = onNext,
                                    enabled = playing.hasNext,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        AppIcons.SkipNext,
                                        contentDescription = "Next",
                                        tint = if (playing.hasNext) palette.shade2 else palette.shade4,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = onSeekForward,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        AppIcons.Forward30,
                                        contentDescription = "Skip Forward",
                                        tint = palette.shade2,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Format duration for mini player display (compact format)
 */
private fun formatMiniPlayerTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
