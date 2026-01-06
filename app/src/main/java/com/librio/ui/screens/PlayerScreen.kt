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
import com.librio.utils.formatTime
import kotlinx.coroutines.launch
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import com.librio.navigation.BottomNavItem

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
    headerTitle: String = "Librio",
) {
    val palette = currentPalette()
    val headerContentHeight = 40.dp
    val audiobook by player.currentAudiobook.collectAsState()
    val playbackState by player.playbackState.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
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

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
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
                            label = "playerBackScale"
                        )

                        IconButton(
                            onClick = { onBackToLibrary?.invoke() },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .scale(backScale),
                            interactionSource = backInteractionSource
                        ) {
                            Icon(
                                AppIcons.Back,
                                contentDescription = "Back",
                                tint = palette.shade11
                            )
                        }
                    }

                    Text(
                        text = headerTitle,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            lineHeight = 24.sp
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
                            label = "playerSearchScale"
                        )
                        IconButton(
                            onClick = { onToggleSearch?.invoke() },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .scale(searchScale),
                            interactionSource = searchInteractionSource
                        ) {
                            Icon(
                                AppIcons.Search,
                                contentDescription = "Search",
                                tint = palette.shade11
                            )
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
            // Full-width navigation bar matching header color with light icons
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
                    BottomNavItem.entries.forEach { item ->
                        val isSelected = selectedTab == item

                        // Animation for selection
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
                                    // Only highlight settings button - library/profile navigate away
                                    when (item) {
                                        BottomNavItem.LIBRARY -> onNavigateToLibrary?.invoke()
                                        BottomNavItem.PROFILE -> onNavigateToProfile?.invoke()
                                        BottomNavItem.SETTINGS -> {
                                            selectedTab = item
                                            showSettings = true
                                        }
                                    }
                                }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                                Icon(
                                    imageVector = item.unselectedIcon, // Always show unselected icon in player
                                    contentDescription = item.title,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (isSelected) palette.shade12 else palette.shade11.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.title,
                                    fontWeight = FontWeight.Normal, // Always show as unselected
                                    fontSize = 11.sp,
                                    color = if (isSelected) palette.shade12 else palette.shade11.copy(alpha = 0.7f)
                                )
                            }
                        }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
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
                            strokeWidth = 4.dp
                        )
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
 * Main player content when an audiobook is loaded
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
) {
    val palette = currentPalette()
    val dimens = com.librio.ui.components.rememberResponsiveDimens()

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

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val availableHeight = maxHeight
        // Use responsive cover art size, capped based on available space
        val coverArtSize = minOf(
            dimens.playerCoverArtSize,
            availableHeight * 0.42f,
            dimens.screenWidthDp * 0.7f
        )

        // Estimate content height for scroll decision
        val controlsHeight = if (dimens.isCompactHeight) 180.dp else 220.dp
        val estimatedContentHeight = coverArtSize + dimens.spacing * 4 + controlsHeight
        val needsScroll = estimatedContentHeight > (availableHeight - dimens.verticalPadding * 2)
        val scrollModifier = if (needsScroll) Modifier.verticalScroll(rememberScrollState()) else Modifier

        Column(
            modifier = scrollModifier
                .fillMaxHeight()
                .padding(horizontal = dimens.horizontalPadding)
                .padding(vertical = dimens.verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (needsScroll) Arrangement.Top else Arrangement.SpaceEvenly
        ) {
            // Cover art with responsive sizing and entrance animation
            val fileExtension = audiobook.fileName.substringAfterLast('.', "").ifEmpty { "AUDIO" }
            CoverArt(
                bitmap = audiobook.coverArt,
                contentDescription = "Cover art for ${audiobook.title}",
                modifier = Modifier
                    .size(coverArtSize)
                    .graphicsLayer {
                        scaleX = coverScale
                        scaleY = coverScale
                        alpha = coverAlpha
                    },
                showPlaceholderAlways = showPlaceholderIcons,
                fileExtension = fileExtension,
                contentType = CoverArtContentType.AUDIOBOOK
            )

            Spacer(modifier = Modifier.height(dimens.spacing))

            // Title and author with responsive text
            Text(
                text = audiobook.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = palette.primary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(dimens.spacingSmall))

            Text(
                text = audiobook.author,
                style = MaterialTheme.typography.bodyLarge,
                color = palette.primary.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            // Chapter info
            if (audiobook.chapters.size > 1) {
                Spacer(modifier = Modifier.height(dimens.spacingSmall))
                val currentChapter = audiobook.chapters.getOrNull(playbackState.currentChapterIndex)
                Text(
                    text = "Chapter ${playbackState.currentChapterIndex + 1} of ${audiobook.chapters.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.primary,
                    textAlign = TextAlign.Center
                )
                currentChapter?.let {
                    Text(
                        text = it.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.primary.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(dimens.spacing))

            // Playback controls
            PlaybackControls(
                playbackState = playbackState,
                onPlayPause = onPlayPause,
                onSeekForward = onSeekForward,
                onSeekBackward = onSeekBackward,
                onNextChapter = onNextChapter,
                onPreviousChapter = onPreviousChapter,
                onSeekTo = onSeekTo,
                skipForwardSeconds = skipForwardSeconds,
                skipBackSeconds = skipBackSeconds,
                showUndoSeekButton = showUndoSeekButton,
                lastSeekPosition = lastSeekPosition,
                onUndoSeek = onUndoSeek,
                modifier = Modifier.fillMaxWidth()
            )
        }
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
