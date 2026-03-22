package com.librio.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.librio.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.librio.model.Audiobook
import com.librio.model.PlaybackState
import com.librio.player.AudiobookPlayer
import com.librio.ui.components.CoverArt
import com.librio.ui.components.CoverArtContentType
import com.librio.ui.components.PlaybackControls
import com.librio.ui.theme.*
import com.librio.ui.theme.AppIcons
import com.librio.ui.screens.MusicSettingsScreen
import com.librio.ui.screens.AudioVisualizerBar
import com.librio.utils.formatTime
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.launch
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import android.view.WindowManager
import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import com.librio.navigation.BottomNavItem

// Helper extension to find Activity from Context
private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

/**
 * Main player screen showing cover art, playback controls, and audiobook info
 */
@Composable
fun PlayerScreen(
    player: AudiobookPlayer,
    modifier: Modifier = Modifier,
    onBackToLibrary: (() -> Unit)? = null,
    onNavigateToLibrary: (() -> Unit)? = null,
    onNavigateToProfile: (() -> Unit)? = null,
    onToggleSearch: (() -> Unit)? = null,
    skipForwardSeconds: Int = 30,
    skipBackSeconds: Int = 10,
    sleepTimerMinutes: Int = 0,
    playbackSpeed: Float = 1.0f,
    onPlaybackSpeedChange: (Float) -> Unit = {},
    equalizerPreset: String = "DEFAULT",
    volumeBoostEnabled: Boolean = false,
    volumeBoostLevel: Float = 1.0f,
    normalizeAudio: Boolean = false,
    bassBoostLevel: Float = 0f,
    autoRewind: Int = 0,
    autoPlayNext: Boolean = true,
    resumePlayback: Boolean = true,
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
    showBackButton: Boolean = true,
    showSearchBar: Boolean = true,
    showPlaceholderIcons: Boolean = true,
    keepScreenOn: Boolean = false,
    headerTitle: String = "Librio",
) {
    val palette = currentPalette()
    val headerContentHeight = 40.dp
    val audiobook by player.currentAudiobook.collectAsState()
    val playbackState by player.playbackState.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val view = LocalView.current
    // No tab selected when in player screen - user navigates FROM library
    var selectedTab by remember { mutableStateOf<BottomNavItem?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    // Undo seek tracking - stores position before last seek
    var lastSeekPositionLocal by remember { mutableStateOf(0L) }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                player.loadAudiobook(it)
            }
        }
    }


    // Show error messages
    LaunchedEffect(playbackState.error) {
        playbackState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            player.clearError()
        }
    }

    // Apply persisted playback speed on entry
    LaunchedEffect(playbackSpeed) {
        player.setPlaybackSpeed(playbackSpeed)
    }

    // Keep screen on when playing if setting is enabled
    DisposableEffect(keepScreenOn, playbackState.isPlaying) {
        val window = view.context.findActivity()?.window
        if (keepScreenOn && playbackState.isPlaying) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
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
                                label = "playerBackScale"
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
                                    ) { onBackToLibrary?.invoke() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    AppIcons.Back,
                                    contentDescription = "Back",
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Center: librio branding
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "lib",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary
                            )
                            Text(
                                text = "rio",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
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
            },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        bottomBar = {
            // Clean bottom navigation bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .background(palette.background)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount < -20 && !showSettings) {
                                showSettings = true
                                selectedTab = BottomNavItem.SETTINGS
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
                    BottomNavItem.entries.forEach { item ->
                        val isSelected = selectedTab == item

                        val scale by animateFloatAsState(
                            targetValue = if (isSelected) 1.03f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessHigh
                            ),
                            label = "navScale"
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .scale(scale)
                                .clickable {
                                    when (item) {
                                        BottomNavItem.LIBRARY -> onNavigateToLibrary?.invoke()
                                        BottomNavItem.PROFILE -> onNavigateToProfile?.invoke()
                                        BottomNavItem.SETTINGS -> {
                                            if (showSettings) {
                                                showSettings = false
                                                selectedTab = null
                                            } else {
                                                selectedTab = item
                                                showSettings = true
                                            }
                                        }
                                    }
                                }
                                .padding(vertical = 6.dp, horizontal = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = item.unselectedIcon,
                                contentDescription = item.title,
                                modifier = Modifier.size(if (isSelected) 22.dp else 20.dp),
                                tint = if (isSelected) palette.accent else palette.textMuted
                            )
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
        },
        containerColor = palette.background
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Background with gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(palette.backgroundGradient())
                )

                if (audiobook != null) {
                    // Player content
                    PlayerContent(
                        audiobook = audiobook!!,
                        playbackState = playbackState,
                        onPlayPause = { player.togglePlayPause() },
                        onSeekForward = {
                            lastSeekPositionLocal = playbackState.currentPosition
                            player.seekForward(skipForwardSeconds)
                        },
                        onSeekBackward = {
                            lastSeekPositionLocal = playbackState.currentPosition
                            player.seekBackward(skipBackSeconds)
                        },
                        onNextChapter = {
                            lastSeekPositionLocal = playbackState.currentPosition
                            player.nextChapter()
                        },
                        onPreviousChapter = {
                            lastSeekPositionLocal = playbackState.currentPosition
                            player.previousChapter()
                        },
                        onSeekTo = {
                            lastSeekPositionLocal = playbackState.currentPosition
                            player.seekTo(it)
                        },
                        skipForwardSeconds = skipForwardSeconds,
                        skipBackSeconds = skipBackSeconds,
                        showPlaceholderIcons = showPlaceholderIcons,
                        showUndoSeekButton = showUndoSeekButton,
                        lastSeekPosition = lastSeekPositionLocal,
                        onUndoSeek = {
                            player.seekTo(lastSeekPositionLocal)
                            lastSeekPositionLocal = 0L
                        },
                        player = player,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Empty state - no audiobook loaded
                    EmptyState(
                        onOpenFile = {
                            filePickerLauncher.launch(arrayOf("audio/*"))
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Loading overlay
                AnimatedVisibility(
                    visible = playbackState.isLoading,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(palette.background.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = palette.primary,
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 5.dp
                        )
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
                    ) { showSettings = false; selectedTab = null }
            ) {
                MusicSettingsScreen(
                    title = "Audiobook settings",
                    icon = AppIcons.Audiobook,
                    onBack = { showSettings = false; selectedTab = null },
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

/**
 * Main player content when an audiobook is loaded — matches music player UI
 */
@Composable
private fun PlayerContent(
    audiobook: Audiobook,
    playbackState: PlaybackState,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onNextChapter: () -> Unit,
    onPreviousChapter: () -> Unit,
    onSeekTo: (Long) -> Unit,
    skipForwardSeconds: Int = 30,
    skipBackSeconds: Int = 10,
    showPlaceholderIcons: Boolean,
    showUndoSeekButton: Boolean = false,
    lastSeekPosition: Long = 0L,
    onUndoSeek: () -> Unit = {},
    modifier: Modifier = Modifier,
    player: AudiobookPlayer? = null,
) {
    val palette = currentPalette()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

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

    // Animation for cover art appearance
    var coverVisible by remember { mutableStateOf(false) }
    LaunchedEffect(audiobook) { coverVisible = true }

    val coverScale by animateFloatAsState(
        targetValue = if (coverVisible) 1f else 0.85f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "coverScale"
    )

    val coverAlpha by animateFloatAsState(
        targetValue = if (coverVisible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "coverAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
    ) {
        // Cover art — responsive size with shadow
        val fileExtension = audiobook.fileName.substringAfterLast('.', "").ifEmpty { "AUDIO" }
        Box(
            modifier = Modifier
                .size(maxCoverSize)
                .align(Alignment.CenterHorizontally)
                .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = palette.textPrimary.copy(alpha = 0.25f))
                .clip(RoundedCornerShape(24.dp))
                .background(palette.thumbnailGradient())
                .graphicsLayer {
                    scaleX = coverScale
                    scaleY = coverScale
                    alpha = coverAlpha
                },
            contentAlignment = Alignment.Center,
        ) {
            if (audiobook.coverArt != null) {
                Image(
                    bitmap = audiobook.coverArt!!.asImageBitmap(),
                    contentDescription = "Cover art for ${audiobook.title}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    AppIcons.Audiobook, null,
                    tint = Color.White.copy(0.4f),
                    modifier = Modifier.size(64.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Title + chapter info row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = audiobook.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val currentChapter = audiobook.chapters.getOrNull(playbackState.currentChapterIndex)
                Text(
                    text = buildString {
                        append(audiobook.author)
                        if (currentChapter != null) {
                            append(" · ")
                            append(currentChapter.title)
                        }
                    },
                    fontSize = 11.sp,
                    color = palette.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Chapter badge
            if (audiobook.chapters.size > 1) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(palette.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${playbackState.currentChapterIndex + 1}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accent,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Progress slider
        val progress = if (playbackState.duration > 0) playbackState.currentPosition.toFloat() / playbackState.duration else 0f
        var sliderPosition by remember { mutableStateOf<Float?>(null) }
        val displayProgress = sliderPosition ?: progress

        com.librio.ui.components.MinimalProgressSlider(
            value = displayProgress.coerceIn(0f, 1f),
            onValueChange = { sliderPosition = it },
            onValueChangeFinished = {
                sliderPosition?.let { pos ->
                    onSeekTo((pos * playbackState.duration).toLong())
                }
                sliderPosition = null
            },
            activeColor = palette.accent,
            inactiveColor = palette.divider
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatTime(
                    if (sliderPosition != null) (sliderPosition!! * playbackState.duration).toLong()
                    else playbackState.currentPosition
                ),
                fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = palette.textMuted.copy(alpha = 0.5f)
            )
            Text(formatTime(playbackState.duration), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = palette.textMuted.copy(alpha = 0.5f))
        }

        Spacer(Modifier.height(6.dp))

        // 5-button control row: rw, prev, play, next, ff (audiobook doesn't need shuffle/repeat)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable { onSeekBackward() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Replay10, "Rewind $skipBackSeconds seconds", tint = palette.textSecondary, modifier = Modifier.size(22.dp))
            }
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable { onPreviousChapter() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.SkipPrevious, "Previous chapter", tint = palette.textSecondary, modifier = Modifier.size(24.dp))
            }

            // Play/Pause — 54dp accent circle with shadow
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .shadow(6.dp, CircleShape, ambientColor = palette.accent.copy(alpha = 0.4f))
                    .clip(CircleShape)
                    .background(palette.accent)
                    .clickable { onPlayPause() },
                contentAlignment = Alignment.Center,
            ) {
                if (playbackState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 3.dp)
                } else {
                    Icon(
                        imageVector = if (playbackState.isPlaying) AppIcons.Pause else AppIcons.Play,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable { onNextChapter() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.SkipNext, "Next chapter", tint = palette.textSecondary, modifier = Modifier.size(24.dp))
            }
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable { onSeekForward() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Forward10, "Forward $skipForwardSeconds seconds", tint = palette.textSecondary, modifier = Modifier.size(22.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        // Chapters section — matches music player "Up Next"
        if (audiobook.chapters.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 330.dp)
                    .clip(cornerRadius(14.dp))
                    .background(palette.surfaceCard)
                    .border(1.dp, palette.divider, cornerRadius(14.dp)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Chapters", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    Text("${audiobook.chapters.size} chapters", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.accent)
                }
                HorizontalDivider(color = palette.divider, thickness = 1.dp)
                val chapters = audiobook.chapters
                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(chapters) { idx, chapter ->
                        val isCurrent = idx == playbackState.currentChapterIndex
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .background(if (isCurrent) palette.accent.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { onSeekTo(chapter.startTime) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = if (isCurrent) "\u25B6" else "${idx + 1}",
                                fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                color = if (isCurrent) palette.accent else palette.textMuted.copy(alpha = 0.5f),
                                modifier = Modifier.width(14.dp),
                            )
                            Text(chapter.title, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f))
                            Text(formatTime(chapter.duration), fontSize = 9.sp, color = palette.textMuted.copy(alpha = 0.5f))
                        }
                        if (idx < chapters.size - 1) {
                            HorizontalDivider(color = palette.divider, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Audio Visualizer Bar
        val sessionId = player?.audioSessionId ?: 0
        AudioVisualizerBar(
            isPlaying = playbackState.isPlaying,
            audioSessionId = sessionId,
            accentColor = palette.accent,
            cardBg = palette.surfaceCard,
            borderColor = palette.divider
        )
    }
}

/**
 * Empty state when no audiobook is loaded
 */
@Composable
private fun EmptyState(
    onOpenFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = currentPalette()
    val dimens = com.librio.ui.components.rememberResponsiveDimens()
    val shapeLarge = cornerRadius(dimens.cornerRadiusLarge)
    val shapeRegular = cornerRadius(dimens.cornerRadius)

    // Responsive sizes using dimens
    val iconBoxSize = if (dimens.isCompactHeight) 100.dp else 140.dp
    val iconSize = if (dimens.isCompactHeight) 50.dp else 70.dp

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(dimens.horizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Large headphones icon
        Box(
            modifier = Modifier
                .size(iconBoxSize)
                .background(
                    color = palette.primary.copy(alpha = 0.1f),
                    shape = shapeLarge
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.Audiobook,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = palette.primary
            )
        }

        Spacer(modifier = Modifier.height(dimens.spacingLarge))

        Text(
            text = "Welcome to Librio",
            style = if (dimens.isCompactHeight) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = palette.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(dimens.spacing))

        Text(
            text = "Open an audiobook to start listening",
            style = MaterialTheme.typography.bodyLarge,
            color = palette.primary.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(dimens.spacingSmall))

        Text(
            text = "Supports M4B, MP3, M4A, AAC, OGG, FLAC and more",
            style = MaterialTheme.typography.bodySmall,
            color = palette.primary.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(dimens.spacingLarge * 2))

        Button(
            onClick = onOpenFile,
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.primary,
                contentColor = palette.onPrimary
            ),
            shape = shapeRegular,
            modifier = Modifier.height(dimens.buttonHeight)
        ) {
            Icon(
                imageVector = AppIcons.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(dimens.iconSize)
            )
            Spacer(modifier = Modifier.width(dimens.spacing))
            Text(
                text = "Open Audiobook",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
