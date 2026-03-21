package com.librio.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.librio.ui.theme.AppIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.coroutines.launch

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
 * Slim mini player matching Librio FX design — vertical swipe to dismiss,
 * animated progress bar, shuffle/prev/play/next/repeat controls
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
    onShuffle: () -> Unit = {},
    onRepeat: () -> Unit = {},
    shuffleOn: Boolean = false,
    repeatOn: Boolean = false,
    showPlaceholderIcons: Boolean = true,
    modifier: Modifier = Modifier
) {
    val palette = currentPalette()
    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    var dismissed by remember(nowPlaying?.id) { mutableStateOf(false) }

    if (dismissed || nowPlaying == null) return

    val swipeAlpha = (1f - (offsetY.value.absoluteValue / 300f)).coerceIn(0f, 1f)

    val progress = if (nowPlaying.duration > 0) {
        nowPlaying.currentPosition.toFloat() / nowPlaying.duration
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 500),
        label = "mini_progress",
    )

    val playScale by animateFloatAsState(
        targetValue = if (nowPlaying.isPlaying) 1f else 1.1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "play_scale",
    )

    val shuffleColor by animateColorAsState(
        targetValue = if (shuffleOn) palette.accent else palette.textMuted,
        animationSpec = tween(250), label = "shuffle_color",
    )
    val repeatColor by animateColorAsState(
        targetValue = if (repeatOn) palette.accent else palette.textMuted,
        animationSpec = tween(250), label = "repeat_color",
    )

    Column(
        modifier = modifier.fillMaxWidth()
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .graphicsLayer(alpha = swipeAlpha)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (offsetY.value.absoluteValue > 100f) {
                                offsetY.animateTo(
                                    if (offsetY.value > 0) 500f else -500f,
                                    tween(200),
                                )
                                dismissed = true
                                onDismiss()
                            } else {
                                offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            }
                        }
                    },
                    onVerticalDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Float ->
                        scope.launch { offsetY.snapTo(offsetY.value + dragAmount) }
                    },
                )
            }
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .background(palette.surfaceCard)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
    ) {
        // Animated progress bar at top
        Box(Modifier.fillMaxWidth().height(2.dp).background(palette.divider)) {
            Box(Modifier.fillMaxWidth(animatedProgress).height(2.dp).background(palette.accent))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Cover art with crossfade
            AnimatedContent(
                targetState = nowPlaying.id,
                transitionSpec = {
                    (fadeIn(tween(300)) + slideInVertically(tween(300)) { -it / 4 })
                        .togetherWith(fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 4 })
                },
                label = "mini_art",
            ) { _ ->
                Box(
                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp))
                        .background(palette.thumbnailGradient()),
                    contentAlignment = Alignment.Center,
                ) {
                    val coverArt = nowPlaying.coverArt
                    if (coverArt != null && !showPlaceholderIcons) {
                        Image(
                            bitmap = coverArt.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = when (nowPlaying) {
                                is NowPlaying.Music -> AppIcons.Music
                                is NowPlaying.Audiobook -> AppIcons.Audiobook
                            },
                            contentDescription = null,
                            tint = Color.White.copy(0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Title + subtitle with crossfade
            AnimatedContent(
                targetState = nowPlaying.id,
                transitionSpec = {
                    (fadeIn(tween(300)) + slideInVertically(tween(250)) { it / 3 })
                        .togetherWith(fadeOut(tween(150)))
                },
                label = "mini_text",
                modifier = Modifier.weight(1f),
            ) { _ ->
                Column {
                    Text(
                        nowPlaying.title, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = palette.textPrimary, maxLines = 1,
                        modifier = Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            repeatDelayMillis = 2000,
                            initialDelayMillis = 1500,
                            velocity = 30.dp
                        )
                    )
                    Text(
                        nowPlaying.subtitle, fontSize = 9.sp, color = palette.textSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Shuffle (music only)
            if (nowPlaying is NowPlaying.Music) {
                Icon(AppIcons.Shuffle, null, tint = shuffleColor,
                    modifier = Modifier.size(16.dp).clickable(onClick = onShuffle))
            }

            // Previous / Seek back
            if (nowPlaying is NowPlaying.Music) {
                Icon(AppIcons.SkipPrevious, null, tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp).clickable(onClick = onPrevious))
            } else {
                Icon(AppIcons.Replay10, null, tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp).clickable(onClick = onSeekBack))
            }

            // Play/Pause — bouncy accent circle
            Box(
                modifier = Modifier.size(30.dp).scale(playScale).clip(CircleShape)
                    .background(palette.accent)
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = nowPlaying.isPlaying,
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) },
                    label = "mini_play_icon",
                ) { playing ->
                    Icon(
                        if (playing) AppIcons.Pause else AppIcons.Play,
                        null, tint = Color.White, modifier = Modifier.size(14.dp),
                    )
                }
            }

            // Next / Seek forward
            if (nowPlaying is NowPlaying.Music) {
                Icon(AppIcons.SkipNext, null, tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp).clickable(onClick = onNext))
            } else {
                Icon(AppIcons.Forward10, null, tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp).clickable(onClick = onSeekForward))
            }

            // Repeat (music only)
            if (nowPlaying is NowPlaying.Music) {
                Icon(AppIcons.Repeat, null, tint = repeatColor,
                    modifier = Modifier.size(16.dp).clickable(onClick = onRepeat))
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
