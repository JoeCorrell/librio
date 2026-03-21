package com.librio.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
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
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.DefaultAudioSink
import android.content.Context
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
        val audioSink = DefaultAudioSink.Builder(context)
            // Disable audio offload so AudioEffect APIs (equalizer, bass, loudness) can attach reliably.
            .setAudioOffloadSupportProvider { _, _ -> AudioOffloadSupport.DEFAULT_UNSUPPORTED }
            .build()
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink {
                return audioSink
            }
        }
        ExoPlayer.Builder(context, renderersFactory)
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

        fun setupAudioEffects(audioSessionId: Int) {
            if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId == 0 || audioSessionId == currentAudioSessionId) return
            currentAudioSessionId = audioSessionId
            try { equalizer?.release() } catch (_: Exception) { }
            equalizer = runCatching { Equalizer(0, audioSessionId) }.getOrNull()
            try { loudnessEnhancer?.release() } catch (_: Exception) { }
            loudnessEnhancer = runCatching { LoudnessEnhancer(audioSessionId) }.getOrNull()
            try { bassBoost?.release() } catch (_: Exception) { }
            bassBoost = runCatching { BassBoost(0, audioSessionId) }.getOrNull()
            equalizer?.let { applyEqualizerPreset(it, equalizerPreset) }
        }

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
                            setupAudioEffects(audioSessionId)
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

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (!manageEffectsLocally) return
                setupAudioEffects(audioSessionId)
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
            // Header - clean theme background with card-style buttons
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.background)
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
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(34.dp)
                                .scale(backScale)
                                .clip(RoundedCornerShape(11.dp))
                                .background(palette.surfaceCard)
                                .clickable(
                                    interactionSource = backInteractionSource,
                                    indication = null
                                ) { onBack() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(AppIcons.Back, "Back", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                        }
                    }

                    // Center: librio branding
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "lib",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = palette.textPrimary
                        )
                        Text(
                            text = "rio",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = palette.accent
                        )
                    }

                    Spacer(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(34.dp)
                    )
                }
            }

            // Main content - Librio FX layout
            val screenHeight = configuration.screenHeightDp

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                // Album art — 220dp with shadow and rounded corners
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .align(Alignment.CenterHorizontally)
                        .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = palette.textPrimary.copy(alpha = 0.25f))
                        .clip(RoundedCornerShape(24.dp))
                        .background(palette.thumbnailGradient()),
                    contentAlignment = Alignment.Center,
                ) {
                    val fileExtension = music.uri.lastPathSegment?.substringAfterLast(".", "MP3")?.uppercase() ?: "MP3"
                    if (music.coverArt != null) {
                        Image(
                            bitmap = music.coverArt!!.asImageBitmap(),
                            contentDescription = "Album art for ${music.title}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            AppIcons.Music, null,
                            tint = Color.White.copy(0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Title + Like button row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = music.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${music.artist}${music.album?.let { " · $it" } ?: ""}",
                            fontSize = 11.sp,
                            color = palette.textMuted,
                        )
                    }
                    // Like button placeholder (visual only — matches Librio FX)
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(palette.accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            AppIcons.Heart, "Like",
                            tint = palette.accent,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Progress slider
                val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f
                MinimalProgressSlider(
                    value = progress,
                    onValueChange = { newProgress ->
                        val newPosition = (newProgress * duration).toLong()
                        exoPlayer.seekTo(newPosition)
                        currentPosition = newPosition
                    },
                    activeColor = palette.accent,
                    inactiveColor = palette.divider
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime(currentPosition), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = palette.textMuted.copy(alpha = 0.5f))
                    Text(formatTime(duration), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = palette.textMuted.copy(alpha = 0.5f))
                }

                Spacer(Modifier.height(6.dp))

                // 7-button control row: shuffle, rw, prev, play, next, ff, repeat
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        AppIcons.Shuffle, "Shuffle",
                        tint = if (isShuffleEnabled) palette.accent else palette.textMuted.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp).clickable {
                            isShuffleEnabled = !isShuffleEnabled
                            exoPlayer.shuffleModeEnabled = isShuffleEnabled
                            onShuffleEnabledChange(isShuffleEnabled)
                        }
                    )
                    Icon(
                        AppIcons.Replay10, "Rewind",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(20.dp).clickable {
                            lastSeekPositionLocal = currentPosition
                            exoPlayer.seekTo((currentPosition - skipBackSeconds * 1000L).coerceAtLeast(0))
                        }
                    )
                    Icon(
                        AppIcons.SkipPrevious, "Previous",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(22.dp).clickable {
                            if (hasPrevious) { exoPlayer.stop(); onTrackChange(playlist[currentIndex - 1]) }
                        }
                    )

                    // Play/Pause — 54dp accent circle with shadow
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .shadow(6.dp, CircleShape, ambientColor = palette.accent.copy(alpha = 0.4f))
                            .clip(CircleShape)
                            .background(palette.accent)
                            .clickable {
                                if (isPlaying) {
                                    if (fadeOnPauseResume) SharedMusicPlayer.pauseWithFade(context) else exoPlayer.pause()
                                } else {
                                    if (fadeOnPauseResume) SharedMusicPlayer.playWithFade(context) else exoPlayer.play()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 3.dp)
                        } else {
                            Icon(
                                imageVector = if (isPlaying) AppIcons.Pause else AppIcons.Play,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    Icon(
                        AppIcons.SkipNext, "Next",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(22.dp).clickable {
                            if (hasNext && playlist.isNotEmpty()) {
                                exoPlayer.stop()
                                val nextTrack = if (isShuffleEnabled && playlist.size > 1) {
                                    val availableIndices = playlist.indices.filter { it != currentIndex }
                                    playlist[availableIndices.random()]
                                } else { playlist[currentIndex + 1] }
                                onTrackChange(nextTrack)
                            }
                        }
                    )
                    Icon(
                        AppIcons.Forward10, "Forward",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(20.dp).clickable {
                            lastSeekPositionLocal = currentPosition
                            val maxPos = if (duration > 0) duration else Long.MAX_VALUE
                            exoPlayer.seekTo((currentPosition + skipForwardSeconds * 1000L).coerceAtMost(maxPos))
                        }
                    )
                    Icon(
                        imageVector = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> AppIcons.RepeatOne
                            else -> AppIcons.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) palette.accent else palette.textMuted.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp).clickable {
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

                Spacer(Modifier.height(8.dp))

                // Up Next — shows ~5 items, scrollable
                if (playlist.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 312.dp) // ~8 rows at ~39dp each
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.surfaceCard)
                            .border(1.dp, palette.divider, RoundedCornerShape(14.dp)),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Up Next", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                            Text("${playlist.size} tracks", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.accent)
                        }
                        HorizontalDivider(color = palette.divider, thickness = 1.dp)
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            itemsIndexed(playlist) { idx, track ->
                                val isCurrent = idx == currentIndex
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .background(if (isCurrent) palette.accent.copy(alpha = 0.15f) else Color.Transparent)
                                        .clickable { exoPlayer.stop(); onTrackChange(track) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = if (isCurrent) "\u25B6" else "${idx + 1}",
                                        fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                        color = if (isCurrent) palette.accent else palette.textMuted.copy(alpha = 0.5f),
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Box(
                                        modifier = Modifier.size(26.dp).clip(RoundedCornerShape(6.dp))
                                            .background(palette.thumbnailGradient()),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (track.coverArt != null) {
                                            Image(
                                                bitmap = track.coverArt!!.asImageBitmap(),
                                                contentDescription = track.title,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                    Text(track.title, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                        color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f))
                                    Text(formatTime(track.duration), fontSize = 9.sp, color = palette.textMuted.copy(alpha = 0.5f))
                                }
                                if (idx < playlist.size - 1) {
                                    HorizontalDivider(color = palette.divider, thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Audio Visualizer Bar
                AudioVisualizerBar(isPlaying = isPlaying, audioSessionId = exoPlayer.audioSessionId, accentColor = palette.accent, cardBg = palette.surfaceCard, borderColor = palette.divider)
            }

            // Bottom navigation bar - clean theme background
            // Swipe up to open player settings
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .background(palette.background)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount < -20 && !showSettings) {
                                showSettings = true
                                selectedNavItem = BottomNavItem.SETTINGS
                            }
                        }
                    }
            ) {
                HorizontalDivider(color = palette.divider, thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        BottomNavItem.LIBRARY to { onNavigateToLibrary() },
                        BottomNavItem.PROFILE to { onNavigateToProfile() },
                        BottomNavItem.SETTINGS to {
                            if (showSettings) {
                                showSettings = false
                                selectedNavItem = null
                            } else {
                                selectedNavItem = BottomNavItem.SETTINGS
                                showSettings = true
                            }
                        }
                    ).forEach { (item, action) ->
                        val isSelected = selectedNavItem == item
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()

                        val scale by animateFloatAsState(
                            targetValue = if (isPressed) 0.92f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessHigh
                            ),
                            label = "navScale"
                        )

                        val iconSize = if (isSelected) 22.dp else 20.dp

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .scale(scale)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null
                                ) { action() }
                                .padding(vertical = 6.dp, horizontal = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Crossfade(
                                targetState = isSelected,
                                animationSpec = tween(200),
                                label = "iconCrossfade"
                            ) { selected ->
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title,
                                    modifier = Modifier.size(iconSize),
                                    tint = if (selected) palette.accent else palette.textMuted
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                letterSpacing = 0.2.sp,
                                color = if (isSelected) palette.accent else palette.textMuted
                            )
                        }
                    }
                }
            }
        }

        // Settings panel with slide animation
        AnimatedVisibility(
            visible = showSettings,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
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

/**
 * Real-time audio visualizer bar using Android's Visualizer API.
 * Captures waveform data from the active audio session and renders
 * smoothly animated bars with accent color gradient.
 */
@Composable
internal fun AudioVisualizerBar(
    isPlaying: Boolean,
    audioSessionId: Int,
    accentColor: androidx.compose.ui.graphics.Color,
    cardBg: androidx.compose.ui.graphics.Color,
    borderColor: androidx.compose.ui.graphics.Color,
) {
    val barCount = 24
    val smoothing = 0.3f
    val context = androidx.compose.ui.platform.LocalContext.current
    val hasPermission = remember {
        mutableStateOf(
            android.content.pm.PackageManager.PERMISSION_GRANTED ==
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
        )
    }

    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission.value = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission.value) permLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    val rawTargets = remember { FloatArray(barCount) { 0.05f } }
    val displayBars = remember { mutableStateOf(FloatArray(barCount) { 0.05f }) }

    val sessionId = audioSessionId

    DisposableEffect(hasPermission.value, sessionId) {
        var vis: android.media.audiofx.Visualizer? = null
        if (hasPermission.value && sessionId != 0) {
            try {
                vis = android.media.audiofx.Visualizer(sessionId).apply {
                    captureSize = android.media.audiofx.Visualizer.getCaptureSizeRange()[1].coerceAtMost(256)
                    setDataCaptureListener(
                        object : android.media.audiofx.Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(v: android.media.audiofx.Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                                if (waveform == null) return
                                val step = (waveform.size / barCount).coerceAtLeast(1)
                                for (i in 0 until barCount) {
                                    var sum = 0f
                                    val start = i * step
                                    val end = (start + step).coerceAtMost(waveform.size)
                                    for (j in start until end) {
                                        val sample = (waveform[j].toInt() and 0xFF) - 128
                                        sum += kotlin.math.abs(sample)
                                    }
                                    val avg = sum / (end - start) / 128f
                                    rawTargets[i] = avg.coerceIn(0.03f, 1f)
                                }
                            }
                            override fun onFftDataCapture(v: android.media.audiofx.Visualizer?, fft: ByteArray?, samplingRate: Int) {}
                        },
                        android.media.audiofx.Visualizer.getMaxCaptureRate() / 3,
                        true, false,
                    )
                    enabled = true
                }
            } catch (_: Exception) {}
        }
        onDispose {
            try { vis?.enabled = false; vis?.release() } catch (_: Exception) {}
        }
    }

    // Smooth animation loop
    LaunchedEffect(isPlaying) {
        while (true) {
            val current = displayBars.value.copyOf()
            for (i in current.indices) {
                val target = if (isPlaying) rawTargets[i] else 0.03f
                val speed = if (target > current[i]) smoothing * 1.5f else smoothing * 0.6f
                current[i] = current[i] + (target - current[i]) * speed
            }
            displayBars.value = current
            kotlinx.coroutines.delay(32) // ~30fps
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        val bars = displayBars.value
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val gap = 3f
            val barWidth = (size.width - (barCount - 1) * gap) / barCount
            for (i in 0 until barCount) {
                val magnitude = bars[i]
                val barHeight = (magnitude * size.height).coerceAtLeast(2f)
                val x = i * (barWidth + gap)
                val centerDist = kotlin.math.abs(i - barCount / 2f) / (barCount / 2f)
                val alpha = 0.5f + magnitude * 0.5f - centerDist * 0.1f
                drawRoundRect(
                    color = accentColor.copy(alpha = alpha.coerceIn(0.2f, 1f)),
                    topLeft = androidx.compose.ui.geometry.Offset(x, size.height - barHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f),
                )
            }
        }
    }
}
