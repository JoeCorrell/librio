package com.librio.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import android.media.audiofx.Equalizer
import com.librio.player.applyEqualizerPreset
import com.librio.player.normalizeEqPresetName
import com.librio.player.SharedMusicPlayer
import com.librio.navigation.BottomNavItem
import com.librio.R
import com.librio.model.LibraryMusic
import com.librio.ui.components.CoverArt
import com.librio.ui.components.CoverArtContentType
import com.librio.ui.components.MinimalProgressSlider
import com.librio.ui.screens.MusicSettingsScreen
import com.librio.ui.theme.*
import com.librio.ui.theme.AppIcons
import kotlinx.coroutines.delay
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.BassBoost
import kotlin.math.abs

/**
 * Music Player Screen - Uses ExoPlayer directly for reliable playback
 */
@Composable
fun MusicPlayerScreen(
    music: LibraryMusic,
    playlist: List<LibraryMusic> = emptyList(),
    currentIndex: Int = 0,
    onBack: () -> Unit,
    onPositionChange: (Long) -> Unit,
    onTrackChange: (LibraryMusic) -> Unit = {},
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    skipForwardSeconds: Int = 30,
    skipBackSeconds: Int = 10,
    sleepTimerMinutes: Int = 0,
    playbackSpeed: Float = 1.0f,
    equalizerPreset: String = "DEFAULT",
    volumeBoostEnabled: Boolean = false,
    volumeBoostLevel: Float = 1.0f,
    normalizeAudio: Boolean = false,
    bassBoostLevel: Float = 0f,
    autoRewind: Int = 0,
    autoPlayNext: Boolean = true,
    resumePlayback: Boolean = true,
    onPlaybackSpeedChange: (Float) -> Unit = {},
    onSkipForwardChange: (Int) -> Unit = {},
    onSkipBackChange: (Int) -> Unit = {},
    onAutoRewindChange: (Int) -> Unit = {},
    onAutoPlayNextChange: (Boolean) -> Unit = {},
    onResumePlaybackChange: (Boolean) -> Unit = {},
    onSleepTimerChange: (Int) -> Unit = {},
    onVolumeBoostEnabledChange: (Boolean) -> Unit = {},
    onVolumeBoostLevelChange: (Float) -> Unit = {},
    onNormalizeAudioChange: (Boolean) -> Unit = {},
    onBassBoostLevelChange: (Float) -> Unit = {},
    onEqualizerPresetChange: (String) -> Unit = {},
    // New audio settings
    showUndoSeekButton: Boolean = true,
    onShowUndoSeekButtonChange: (Boolean) -> Unit = {},
    fadeOnPauseResume: Boolean = false,
    onFadeOnPauseResumeChange: (Boolean) -> Unit = {},
    gaplessPlayback: Boolean = true,
    onGaplessPlaybackChange: (Boolean) -> Unit = {},
    trimSilence: Boolean = false,
    onTrimSilenceChange: (Boolean) -> Unit = {},
    initialShuffleEnabled: Boolean = false,
    initialRepeatMode: Int = Player.REPEAT_MODE_OFF,
    onShuffleEnabledChange: (Boolean) -> Unit = {},
    onRepeatModeChange: (Int) -> Unit = {},
    showBackButton: Boolean = true,
    showSearchBar: Boolean = true,
    showPlaceholderIcons: Boolean = true,
    headerTitle: String = "Librio",
    externalExoPlayer: ExoPlayer? = null, // Shared player from MainActivity
    modifier: Modifier = Modifier
) {
    val palette = currentPalette()
    val headerContentHeight = 40.dp
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    var selectedNavItem by remember { mutableStateOf<BottomNavItem?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    // Responsive sizing
    val maxCoverSize = when {
        screenWidth < 400.dp -> 220.dp
        screenWidth < 600.dp -> 280.dp
        screenWidth < 840.dp -> 320.dp
        else -> 360.dp
    }

    val horizontalPadding = when {
        screenWidth < 400.dp -> 24.dp
        screenWidth < 600.dp -> 32.dp
        else -> 48.dp
    }

    // Player state placeholders (initialized after player selection below)
    var isLoading by remember { mutableStateOf(false) }

    // Shuffle and loop state - initialized from persisted values
    var isShuffleEnabled by remember { mutableStateOf(initialShuffleEnabled) }
    var repeatMode by remember { mutableIntStateOf(initialRepeatMode) }

    // Sleep timer state
    var sleepTimerEndTime by remember { mutableStateOf<Long?>(
        if (sleepTimerMinutes > 0) System.currentTimeMillis() + (sleepTimerMinutes * 60 * 1000L) else null
    ) }
    var sleepTimerActive by remember { mutableStateOf(sleepTimerMinutes > 0) }

    // Playlist navigation - when shuffle is enabled, there's always a next track if playlist has >1 items
    val hasPrevious = playlist.isNotEmpty() && currentIndex > 0
    val hasNext = playlist.isNotEmpty() && (currentIndex < playlist.size - 1 || (isShuffleEnabled && playlist.size > 1))

    // Playback speed state
    var currentSpeed by remember { mutableFloatStateOf(playbackSpeed.coerceIn(0.5f, 2f)) }

    // Undo seek tracking - stores position before last seek
    var lastSeekPositionLocal by remember { mutableStateOf(0L) }
    fun buildMusicMediaItem(track: LibraryMusic): MediaItem {
        return MediaItem.Builder()
            .setUri(track.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album ?: "")
                    .build()
            )
            .build()
    }

    // Use external player if provided, otherwise create local one
    val isExternalPlayer = externalExoPlayer != null
    val exoPlayer = externalExoPlayer ?: remember {
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
    }
    val manageEffectsLocally = !isExternalPlayer

    // Equalizer state
    var equalizer by remember { mutableStateOf<Equalizer?>(null) }
    var loudnessEnhancer by remember { mutableStateOf<LoudnessEnhancer?>(null) }
    var bassBoost by remember { mutableStateOf<BassBoost?>(null) }
    var currentAudioSessionId by remember { mutableIntStateOf(C.AUDIO_SESSION_ID_UNSET) }

    // Determine if we're showing the same track the shared player already has loaded
    val currentItemUri = exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
    val sameTrack = currentItemUri == music.uri.toString()

    // Sync initial state with existing player when applicable
    var isPlaying by remember(music.uri, currentItemUri) { mutableStateOf(if (sameTrack) exoPlayer.isPlaying else false) }
    var currentPosition by remember(music.uri, currentItemUri) {
        mutableLongStateOf(
            if (sameTrack && exoPlayer.currentPosition > 0) exoPlayer.currentPosition else music.lastPosition
        )
    }
    var duration by remember(music.uri, currentItemUri) {
        mutableLongStateOf(
            if (sameTrack && exoPlayer.duration > 0) exoPlayer.duration else music.duration
        )
    }

    // Track if we need to load this track (only if different from what's playing)
    val needsLoad = remember(music.uri, currentItemUri) { !sameTrack || exoPlayer.currentMediaItem == null }

    // Set up player and load media
    DisposableEffect(music.uri) {
        // Sync initial loading state with existing player (important when returning to the screen)
        isLoading = exoPlayer.playbackState == Player.STATE_BUFFERING

        // Only load if this is a different track or player was reset
        if (needsLoad) {
            isLoading = true
            val mediaItem = buildMusicMediaItem(music)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            val resumePos = if (sameTrack && exoPlayer.currentPosition > 0) exoPlayer.currentPosition else music.lastPosition
            exoPlayer.seekTo(resumePos)
            // Preserve current play/pause state (handled by shared player)
            exoPlayer.playWhenReady = exoPlayer.playWhenReady
        } else {
            // Sync duration when re-entering without reload
            if (exoPlayer.duration > 0) {
                duration = exoPlayer.duration
            }
        }

        // Add listener
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isLoading = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY && exoPlayer.duration > 0) {
                    duration = exoPlayer.duration
                    if (manageEffectsLocally) {
                        // Set up effects after player is ready
                        try {
                            val audioSessionId = exoPlayer.audioSessionId
                            if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId != 0 && audioSessionId != currentAudioSessionId) {
                                currentAudioSessionId = audioSessionId
                                try { equalizer?.release() } catch (_: Exception) { }
                                equalizer = runCatching { Equalizer(0, audioSessionId) }.getOrNull()
                                try { loudnessEnhancer?.release() } catch (_: Exception) { }
                                loudnessEnhancer = runCatching { LoudnessEnhancer(audioSessionId) }.getOrNull()
                                try { bassBoost?.release() } catch (_: Exception) { }
                                bassBoost = runCatching { BassBoost(0, audioSessionId) }.getOrNull()
                                equalizer?.let { applyEqualizerPreset(it, equalizerPreset) }
                            }
                            applyAudioEffects(
                                loudnessEnhancer,
                                bassBoost,
                                volumeBoostEnabled,
                                volumeBoostLevel,
                                normalizeAudio,
                                bassBoostLevel,
                                equalizerPreset
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                // Handle track ended - auto-play next based on shuffle/repeat mode
                if (playbackState == Player.STATE_ENDED && !isExternalPlayer) {
                    when {
                        repeatMode == Player.REPEAT_MODE_ONE -> {
                            exoPlayer.seekTo(0)
                            exoPlayer.play()
                        }
                        isShuffleEnabled && playlist.size > 1 -> {
                            val availableIndices = playlist.indices.filter { it != currentIndex }
                            if (availableIndices.isNotEmpty()) {
                                val randomIndex = availableIndices.random()
                                onTrackChange(playlist[randomIndex])
                            } else if (repeatMode == Player.REPEAT_MODE_ALL) {
                                exoPlayer.seekTo(0)
                                exoPlayer.play()
                            }
                        }
                        currentIndex < playlist.size - 1 -> {
                            onTrackChange(playlist[currentIndex + 1])
                        }
                        repeatMode == Player.REPEAT_MODE_ALL && playlist.isNotEmpty() -> {
                            onTrackChange(playlist[0])
                        }
                        else -> { }
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)

            onDispose {
                // Save position before leaving
                onPositionChange(exoPlayer.currentPosition)
                exoPlayer.removeListener(listener)
                // Only release if we own the player/effects locally
                if (manageEffectsLocally) {
                    try {
                        equalizer?.release()
                        loudnessEnhancer?.release()
                        bassBoost?.release()
                    } catch (e: Exception) { }
                    exoPlayer.release()
                }
            }
    }

    // Apply playback speed when it changes
    LaunchedEffect(currentSpeed) {
        val safeSpeed = currentSpeed.coerceIn(0.5f, 2f)
        if (safeSpeed != currentSpeed) {
            currentSpeed = safeSpeed
        }
        exoPlayer.playbackParameters = PlaybackParameters(safeSpeed, 1f)
    }

    // Keep local speed state in sync with upstream changes
    LaunchedEffect(playbackSpeed) {
        val safeSpeed = playbackSpeed.coerceIn(0.5f, 2f)
        if (abs(safeSpeed - currentSpeed) > 0.01f) {
            currentSpeed = safeSpeed
        }
    }

    // Sync player's shuffle and repeat mode with persisted state on screen load
    LaunchedEffect(Unit) {
        exoPlayer.shuffleModeEnabled = isShuffleEnabled
        exoPlayer.repeatMode = repeatMode
    }

    if (manageEffectsLocally) {
        // Apply equalizer preset when it changes
        LaunchedEffect(equalizer, equalizerPreset) {
            equalizer?.let { eq ->
                try {
                    applyEqualizerPreset(eq, equalizerPreset)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Apply audio effects when toggles change
        LaunchedEffect(volumeBoostEnabled, volumeBoostLevel, normalizeAudio, bassBoostLevel, equalizerPreset) {
            applyAudioEffects(
                loudnessEnhancer,
                bassBoost,
                volumeBoostEnabled,
                volumeBoostLevel,
                normalizeAudio,
                bassBoostLevel,
                equalizerPreset
            )
        }
    }

    // Sleep timer countdown
    LaunchedEffect(sleepTimerEndTime) {
        sleepTimerEndTime?.let { endTime ->
            while (System.currentTimeMillis() < endTime && sleepTimerActive) {
                delay(1000)
            }
            if (sleepTimerActive) {
                // Timer finished - pause playback with fade if enabled
                if (fadeOnPauseResume) {
                    SharedMusicPlayer.pauseWithFade(context)
                } else {
                    exoPlayer.pause()
                }
                sleepTimerActive = false
                sleepTimerEndTime = null
            }
        }
    }

    // Update position periodically - only runs while playing to save battery
    // Note: while(true) is safe here - LaunchedEffect cancels when isPlaying changes, and delay() is a cancellation point
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        while (true) {
            currentPosition = exoPlayer.currentPosition
            if (exoPlayer.duration > 0) {
                duration = exoPlayer.duration
            }
            delay(500)
        }
    }

    // Save position periodically while playing
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        while (true) {
            delay(5000)
            onPositionChange(exoPlayer.currentPosition)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.backgroundGradient())
    ) {

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.headerGradient())
                    .statusBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .height(headerContentHeight),
                    contentAlignment = Alignment.Center
                ) {
                    if (showBackButton) {
                        val backInteractionSource = remember { MutableInteractionSource() }
                        val backIsPressed by backInteractionSource.collectIsPressedAsState()
                        val backScale by animateFloatAsState(
                            targetValue = if (backIsPressed) 0.85f else 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessHigh),
                            label = "musicBackScale"
                        )
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .scale(backScale),
                            interactionSource = backInteractionSource
                        ) {
                            Icon(AppIcons.Back, "Back", tint = palette.shade11)
                        }
                    }

                    Text(
                        text = headerTitle,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = palette.shade11,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    if (showSearchBar) {
                        val searchInteractionSource = remember { MutableInteractionSource() }
                        val searchIsPressed by searchInteractionSource.collectIsPressedAsState()
                        val searchScale by animateFloatAsState(
                            targetValue = if (searchIsPressed) 0.85f else 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessHigh),
                            label = "musicSearchScale"
                        )
                        IconButton(
                            onClick = { },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .scale(searchScale),
                            interactionSource = searchInteractionSource
                        ) {
                            Icon(AppIcons.Search, "Search", tint = palette.shade11)
                        }
                    } else {
                        Spacer(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(24.dp)
                        )
                    }
                }
            }

            // Main content - adaptive layout for different screen sizes
            val screenHeight = configuration.screenHeightDp
            val isLargeScreen = screenHeight > 700

            // Calculate max cover size based on available space (accounting for header ~80dp, controls ~200dp)
            val availableHeight = screenHeight - 280
            val maxAllowedCoverSize = (availableHeight * 0.5f).dp.coerceIn(120.dp, maxCoverSize)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = horizontalPadding)
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                // Cover art - adaptive size based on available space
                val adaptiveCoverSize = when {
                    screenHeight > 800 -> maxAllowedCoverSize.coerceAtMost(maxCoverSize)
                    screenHeight > 600 -> (maxAllowedCoverSize.value * 0.85f).dp
                    else -> (maxAllowedCoverSize.value * 0.75f).dp
                }

                // Cover art using same component as audiobook player
                val fileExtension = music.uri.lastPathSegment?.substringAfterLast(".", "MP3")?.uppercase() ?: "MP3"
                CoverArt(
                    bitmap = music.coverArt,
                    contentDescription = "Album art for ${music.title}",
                    modifier = Modifier.size(adaptiveCoverSize),
                    showPlaceholderAlways = showPlaceholderIcons,
                    fileExtension = fileExtension,
                    contentType = CoverArtContentType.MUSIC
                )

                // Title and artist
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = music.title,
                        style = if (isLargeScreen) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = music.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textMuted,
                        textAlign = TextAlign.Center
                    )
                    music.album?.let { album ->
                        Text(
                            text = album,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textMuted.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Progress slider
                val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f

                Column(modifier = Modifier.fillMaxWidth()) {
                    MinimalProgressSlider(
                        value = progress,
                        onValueChange = { newProgress ->
                            val newPosition = (newProgress * duration).toLong()
                            exoPlayer.seekTo(newPosition)
                            currentPosition = newPosition
                        },
                        activeColor = palette.accent,
                        inactiveColor = palette.surfaceMedium.copy(alpha = 0.4f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(currentPosition), style = MaterialTheme.typography.bodySmall, color = palette.accent)
                        Text(formatTime(duration), style = MaterialTheme.typography.bodySmall, color = palette.accent)
                    }
                }

                // Responsive control button sizes
                val smallButtonSize = if (screenWidth < 400.dp) 36.dp else 40.dp
                val mediumButtonSize = if (screenWidth < 400.dp) 44.dp else 52.dp
                val playButtonSize = if (screenWidth < 400.dp) 64.dp else if (isLargeScreen) 76.dp else 68.dp
                val smallIconSize = if (screenWidth < 400.dp) 18.dp else 20.dp
                val mediumIconSize = if (screenWidth < 400.dp) 24.dp else 28.dp
                val playIconSize = if (screenWidth < 400.dp) 32.dp else if (isLargeScreen) 40.dp else 36.dp

                // Main playback controls row - 5 buttons: prev, skip back, play, skip forward, next
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous track button
                    Box(
                        modifier = Modifier
                            .size(smallButtonSize)
                            .clip(CircleShape)
                            .background(palette.accent.copy(alpha = 0.1f))
                            .clickable(enabled = hasPrevious || playlist.isEmpty()) {
                                if (hasPrevious) {
                                    exoPlayer.stop()
                                    onTrackChange(playlist[currentIndex - 1])
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            AppIcons.SkipPrevious,
                            "Previous",
                            tint = if (hasPrevious || playlist.isEmpty()) palette.accent else palette.accent.copy(alpha = 0.3f),
                            modifier = Modifier.size(smallIconSize + 4.dp)
                        )
                    }

                    // Rewind button
                    Box(
                        modifier = Modifier
                            .size(mediumButtonSize)
                            .clip(CircleShape)
                            .background(palette.accent.copy(alpha = 0.1f))
                            .clickable {
                                lastSeekPositionLocal = currentPosition
                                exoPlayer.seekTo((currentPosition - skipBackSeconds * 1000L).coerceAtLeast(0))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            AppIcons.Replay10,
                            "Rewind",
                            tint = palette.accent,
                            modifier = Modifier.size(mediumIconSize)
                        )
                        Text(
                            text = "$skipBackSeconds",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.accent,
                            fontSize = if (screenWidth < 400.dp) 8.sp else 9.sp
                        )
                    }

                    // Play/Pause button with gradient
                    Box(
                        modifier = Modifier
                            .size(playButtonSize)
                            .shadow(8.dp, CircleShape)
                            .clip(CircleShape)
                            .background(palette.buttonGradient())
                            .clickable {
                                if (isPlaying) {
                                    if (fadeOnPauseResume) {
                                        SharedMusicPlayer.pauseWithFade(context)
                                    } else {
                                        exoPlayer.pause()
                                    }
                                } else {
                                    if (fadeOnPauseResume) {
                                        SharedMusicPlayer.playWithFade(context)
                                    } else {
                                        exoPlayer.play()
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(playIconSize * 0.6f),
                                color = palette.onPrimary,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) AppIcons.Pause else AppIcons.Play,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = palette.onPrimary,
                                modifier = Modifier.size(playIconSize)
                            )
                        }
                    }

                    // Forward button (mirrored)
                    Box(
                        modifier = Modifier
                            .size(mediumButtonSize)
                            .clip(CircleShape)
                            .background(palette.accent.copy(alpha = 0.1f))
                            .clickable {
                                lastSeekPositionLocal = currentPosition
                                val maxPos = if (duration > 0) duration else Long.MAX_VALUE
                                exoPlayer.seekTo((currentPosition + skipForwardSeconds * 1000L).coerceAtMost(maxPos))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            AppIcons.Forward10,
                            "Forward",
                            tint = palette.accent,
                            modifier = Modifier
                                .size(mediumIconSize)
                        )
                        Text(
                            text = "$skipForwardSeconds",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.accent,
                            fontSize = if (screenWidth < 400.dp) 8.sp else 9.sp
                        )
                    }

                    // Next track button
                    Box(
                        modifier = Modifier
                            .size(smallButtonSize)
                            .clip(CircleShape)
                            .background(palette.accent.copy(alpha = 0.1f))
                            .clickable(enabled = hasNext || playlist.isEmpty()) {
                                if (hasNext && playlist.isNotEmpty()) {
                                    exoPlayer.stop()
                                    val nextTrack = if (isShuffleEnabled && playlist.size > 1) {
                                        // Pick a random track that's not the current one
                                        val availableIndices = playlist.indices.filter { it != currentIndex }
                                        playlist[availableIndices.random()]
                                    } else {
                                        // Sequential: just go to next track
                                        playlist[currentIndex + 1]
                                    }
                                    onTrackChange(nextTrack)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            AppIcons.SkipNext,
                            "Next",
                            tint = if (hasNext || playlist.isEmpty()) palette.accent else palette.accent.copy(alpha = 0.3f),
                            modifier = Modifier.size(smallIconSize + 4.dp)
                        )
                    }
                }

                // Undo seek button
                val canUndo = showUndoSeekButton && lastSeekPositionLocal > 0L &&
                    kotlin.math.abs(currentPosition - lastSeekPositionLocal) > 1000L
                AnimatedVisibility(
                    visible = canUndo,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(palette.accent.copy(alpha = 0.15f))
                                .clickable {
                                    exoPlayer.seekTo(lastSeekPositionLocal)
                                    lastSeekPositionLocal = 0L
                                }
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

                Spacer(modifier = Modifier.height(12.dp))

                // Shuffle and Repeat controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ControlPill(
                        modifier = Modifier.weight(1f),
                        text = if (isShuffleEnabled) "On" else "Off",
                        icon = AppIcons.Shuffle,
                        isActive = isShuffleEnabled,
                        onClick = {
                            isShuffleEnabled = !isShuffleEnabled
                            exoPlayer.shuffleModeEnabled = isShuffleEnabled
                            onShuffleEnabledChange(isShuffleEnabled)
                        }
                    )

                    ControlPill(
                        modifier = Modifier.weight(1f),
                        text = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> "One"
                            Player.REPEAT_MODE_ALL -> "All"
                            else -> "Off"
                        },
                        icon = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> AppIcons.RepeatOne
                            else -> AppIcons.Repeat
                        },
                        isActive = repeatMode != Player.REPEAT_MODE_OFF,
                        onClick = {
                            repeatMode = when (repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                                else -> Player.REPEAT_MODE_OFF
                            }
                            exoPlayer.repeatMode = repeatMode
                            onRepeatModeChange(repeatMode)
                        }
                    )
                }
            }

            // Bottom navigation bar matching header color with light icons
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .background(palette.headerGradient())
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        BottomNavItem.LIBRARY to {
                            // Don't highlight - navigating away
                            onNavigateToLibrary()
                        },
                        BottomNavItem.PROFILE to {
                            // Don't highlight - navigating away
                            onNavigateToProfile()
                        },
                        BottomNavItem.SETTINGS to {
                            selectedNavItem = BottomNavItem.SETTINGS
                            showSettings = true
                        }
                    ).forEach { (item, action) ->
                        val isSelected = selectedNavItem == item
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()

                        // Subtle press scale animation only
                        val scale by animateFloatAsState(
                            targetValue = if (isPressed) 0.92f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessHigh
                            ),
                            label = "navScale"
                        )

                        // Icon size based on selection
                        val iconSize = if (isSelected) 26.dp else 24.dp

                        // Animate color alpha for smooth transition
                        val iconAlpha by animateFloatAsState(
                            targetValue = if (isSelected) 1f else 0.6f,
                            animationSpec = tween(200),
                            label = "iconAlpha"
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .scale(scale)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null
                                ) { action() }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                            ) {
                                // Crossfade between filled and outlined icons
                                Crossfade(
                                    targetState = isSelected,
                                    animationSpec = tween(200),
                                    label = "iconCrossfade"
                                ) { selected ->
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.title,
                                        modifier = Modifier.size(iconSize),
                                        tint = palette.shade12.copy(alpha = iconAlpha)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.title,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 11.sp,
                                    color = palette.shade12.copy(alpha = iconAlpha)
                                )
                            }
                        }
                    }
            }
        }

        if (showSettings) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showSettings = false; selectedNavItem = null }
            ) {
                MusicSettingsScreen(
                    title = "Music settings",
                    icon = AppIcons.Music,
                    onBack = { showSettings = false; selectedNavItem = null },
                    playbackSpeed = playbackSpeed,
                    onPlaybackSpeedChange = onPlaybackSpeedChange,
                    skipForward = skipForwardSeconds,
                    onSkipForwardChange = onSkipForwardChange,
                    skipBack = skipBackSeconds,
                    onSkipBackChange = onSkipBackChange,
                    autoRewind = autoRewind,
                    onAutoRewindChange = onAutoRewindChange,
                    autoPlayNext = autoPlayNext,
                    onAutoPlayNextChange = onAutoPlayNextChange,
                    resumePlayback = resumePlayback,
                    onResumePlaybackChange = onResumePlaybackChange,
                    sleepTimerMinutes = sleepTimerMinutes,
                    onSleepTimerChange = onSleepTimerChange,
                    volumeBoostEnabled = volumeBoostEnabled,
                    onVolumeBoostEnabledChange = onVolumeBoostEnabledChange,
                    volumeBoostLevel = volumeBoostLevel,
                    onVolumeBoostLevelChange = onVolumeBoostLevelChange,
                    normalizeAudio = normalizeAudio,
                    onNormalizeAudioChange = onNormalizeAudioChange,
                    bassBoostLevel = bassBoostLevel,
                    onBassBoostLevelChange = onBassBoostLevelChange,
                    equalizerPreset = equalizerPreset,
                    onEqualizerPresetChange = onEqualizerPresetChange,
                    // New audio settings
                    showUndoSeekButton = showUndoSeekButton,
                    onShowUndoSeekButtonChange = onShowUndoSeekButtonChange,
                    fadeOnPauseResume = fadeOnPauseResume,
                    onFadeOnPauseResumeChange = onFadeOnPauseResumeChange,
                    gaplessPlayback = gaplessPlayback,
                    onGaplessPlaybackChange = onGaplessPlaybackChange,
                    trimSilence = trimSilence,
                    onTrimSilenceChange = onTrimSilenceChange,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* consume */ }
                )
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

private fun applyAudioEffects(
    loudnessEnhancer: LoudnessEnhancer?,
    bassBoost: BassBoost?,
    volumeBoostEnabled: Boolean,
    volumeBoostLevel: Float,
    normalizeAudio: Boolean,
    bassBoostLevel: Float,
    equalizerPreset: String
) {
    loudnessEnhancer?.let { enhancer ->
        runCatching {
            val gainMb = when {
                volumeBoostEnabled -> ((volumeBoostLevel - 1f) * 1500).toInt().coerceAtLeast(0)
                normalizeAudio -> 600  // Normalize audio with moderate gain boost
                else -> 0
            }
            enhancer.setTargetGain(gainMb)
            enhancer.enabled = volumeBoostEnabled || normalizeAudio
        }
    }
    bassBoost?.let { boost ->
        val normalizedPreset = normalizeEqPresetName(equalizerPreset)
        val shouldApply = bassBoostLevel > 0f && normalizedPreset != "BASS_INCREASED"
        val strength = if (shouldApply) {
            (bassBoostLevel * 700f).toInt().coerceIn(0, 700)
        } else {
            0
        }
        boost.setStrength(strength.toShort())
        boost.enabled = strength > 0
    }
}

@Composable
private fun ControlPill(
    text: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = currentPalette()
    val shape12 = cornerRadius(12.dp)
    // Material You tonal surfaces - solid colors
    val backgroundColor = if (isActive) palette.shade4 else palette.shade9
    val contentColor = if (isActive) palette.shade11 else palette.shade2
    val iconColor = if (isActive) palette.shade11 else palette.shade3

    Box(
        modifier = modifier
            .clip(shape12)
            .background(backgroundColor)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}
