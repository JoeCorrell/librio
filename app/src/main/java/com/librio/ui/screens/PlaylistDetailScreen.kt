package com.librio.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.librio.model.LibraryMusic
import com.librio.model.LibrarySeries
import com.librio.ui.theme.AppIcons
import com.librio.ui.theme.cornerRadius
import com.librio.ui.theme.currentPalette

/**
 * Playlist detail screen showing:
 * - Large cover art at top
 * - Playlist title and stats
 * - Scrollable list of tracks
 * - Shuffle and play controls
 */
@Composable
fun PlaylistDetailScreen(
    series: LibrarySeries,
    tracks: List<LibraryMusic>,
    coverArtBitmap: Bitmap?,
    currentlyPlayingId: String? = null,
    isPlaying: Boolean = false,
    isShuffleEnabled: Boolean = false,
    isRepeatEnabled: Boolean = false,
    showPlaceholderIcons: Boolean = true,
    onTrackClick: (LibraryMusic, List<LibraryMusic>) -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onPlayAllClick: () -> Unit,
    onPlayPause: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val palette = currentPalette()
    val shape16 = cornerRadius(16.dp)
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val context = LocalContext.current

    // Load custom cover art from series if available
    val customCoverArt = remember(series.coverArtUri) {
        series.coverArtUri?.let { uriString ->
            try {
                val uri = Uri.parse(uriString)
                BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))
            } catch (e: Exception) {
                null
            }
        }
    }

    // Use custom cover art if available, otherwise fall back to track cover art
    val displayCoverArt = customCoverArt ?: coverArtBitmap

    // Calculate total duration
    val totalDuration = remember(tracks) {
        tracks.sumOf { it.duration }
    }

    // Format total duration
    val formattedTotalDuration = remember(totalDuration) {
        val totalSeconds = totalDuration / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        if (hours > 0) {
            "${hours}h ${minutes}m"
        } else {
            "${minutes} min"
        }
    }

    // Responsive cover art size - larger for immersive experience
    val coverArtSize = when {
        screenWidth < 360.dp -> 260.dp
        screenWidth < 400.dp -> 280.dp
        screenWidth < 600.dp -> 300.dp
        else -> 340.dp
    }

    // Handle back button/gesture to dismiss overlay
    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp) // Space for mini player
        ) {
            // Cover art and info section
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.background)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Large cover art
                        Box(
                            modifier = Modifier
                                .size(coverArtSize)
                                .shadow(12.dp, shape16)
                                .clip(shape16)
                                .background(palette.surfaceCard),
                            contentAlignment = Alignment.Center
                        ) {
                            // Show cover art if available, otherwise show placeholder
                            if (displayCoverArt != null) {
                                Image(
                                    bitmap = displayCoverArt.asImageBitmap(),
                                    contentDescription = "Playlist cover",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Playlist,
                                        contentDescription = null,
                                        tint = palette.shade7.copy(alpha = 0.95f),
                                        modifier = Modifier.size(72.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Playlist title
                        Text(
                            text = series.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Stats row
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${tracks.size} ${if (tracks.size == 1) "track" else "tracks"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textMuted
                            )
                            Text(
                                text = " • ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textMuted
                            )
                            Text(
                                text = formattedTotalDuration,
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // All playback controls on one line: Shuffle, Prev, Play/Pause, Next, Repeat
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Shuffle button (small)
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isShuffleEnabled) palette.accent.copy(alpha = 0.2f)
                                        else palette.surfaceCard
                                    )
                                    .clickable { onShuffleClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AppIcons.Shuffle,
                                    contentDescription = "Shuffle",
                                    tint = if (isShuffleEnabled) palette.accent else palette.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Skip Previous button
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(palette.surfaceCard)
                                    .clickable { onSkipPrevious() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AppIcons.SkipPrevious,
                                    contentDescription = "Previous",
                                    tint = palette.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Play/Pause button - large and prominent
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(palette.accent)
                                    .clickable {
                                        if (currentlyPlayingId != null) {
                                            onPlayPause()
                                        } else {
                                            onPlayAllClick()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) AppIcons.Pause else AppIcons.Play,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = palette.onPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Skip Next button
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(palette.surfaceCard)
                                    .clickable { onSkipNext() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AppIcons.SkipNext,
                                    contentDescription = "Next",
                                    tint = palette.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Repeat button (small)
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isRepeatEnabled) palette.accent.copy(alpha = 0.2f)
                                        else palette.surfaceCard
                                    )
                                    .clickable { onRepeatClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AppIcons.Repeat,
                                    contentDescription = "Repeat",
                                    tint = if (isRepeatEnabled) palette.accent else palette.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }

            // Track list
            itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                PlaylistTrackItem(
                    track = track,
                    index = index + 1,
                    isCurrentlyPlaying = track.id == currentlyPlayingId,
                    isPlaying = track.id == currentlyPlayingId && isPlaying,
                    onClick = { onTrackClick(track, tracks) }
                )
            }
        }
    }
}

@Composable
private fun PlaylistTrackItem(
    track: LibraryMusic,
    index: Int,
    isCurrentlyPlaying: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val palette = currentPalette()
    val shape8 = cornerRadius(8.dp)
    val shape12 = cornerRadius(12.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = shape12,
        colors = CardDefaults.cardColors(containerColor = palette.surfaceCard),
        border = BorderStroke(
            1.dp,
            if (isCurrentlyPlaying) palette.accent.copy(alpha = 0.5f) else palette.surfaceDark.copy(alpha = 0.12f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
        // Track number or playing indicator
        Box(
            modifier = Modifier.width(32.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isCurrentlyPlaying) {
                Icon(
                    imageVector = if (isPlaying) AppIcons.VolumeUp else AppIcons.Pause,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = index.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textMuted
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Cover art thumbnail
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(4.dp, shape8)
                .clip(shape8)
                .background(palette.surfaceCard),
            contentAlignment = Alignment.Center
        ) {
            // Show cover art if available, otherwise placeholder
            if (track.coverArt != null) {
                Image(
                    bitmap = track.coverArt.asImageBitmap(),
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = AppIcons.Music,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Track info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrentlyPlaying) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrentlyPlaying) palette.accent else palette.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = palette.primary.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Duration
        Text(
            text = track.formattedDuration,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textMuted
        )
    }
    }
}
