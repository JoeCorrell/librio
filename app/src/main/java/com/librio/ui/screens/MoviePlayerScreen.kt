package com.librio.ui.screens

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.librio.ui.theme.cornerRadius
import com.librio.ui.components.MinimalSlider
import androidx.compose.material3.*
import com.librio.ui.theme.AppIcons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.librio.R
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.librio.MainActivity
import com.librio.navigation.BottomNavItem
import com.librio.model.LibraryMovie
import com.librio.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Movie Player Screen using ExoPlayer for video playback
 * Features: Fullscreen mode, immersive playback, settings panel with persistence
 */
@Suppress("UNUSED_PARAMETER")
@OptIn(UnstableApi::class)
@Composable
fun MoviePlayerScreen(
    movie: LibraryMovie,
    onBack: () -> Unit,
    onPositionChange: (Long) -> Unit,
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    showBackButton: Boolean = true,
    showSearchBar: Boolean = true,
    headerTitle: String = "Librio",
    // Persisted movie player settings
    playbackSpeed: Float = 1.0f,
    onPlaybackSpeedChange: (Float) -> Unit = {},
    keepScreenOn: Boolean = true,
    onKeepScreenOnChange: (Boolean) -> Unit = {},
    resizeMode: String = "fit",
    onResizeModeChange: (String) -> Unit = {},
    brightness: Float = 1.0f,
    onBrightnessChange: (Float) -> Unit = {},
    autoFullscreenLandscape: Boolean = true,
    onAutoFullscreenLandscapeChange: (Boolean) -> Unit = {},
    showControlsOnTap: Boolean = true,
    onShowControlsOnTapChange: (Boolean) -> Unit = {},
    controlsTimeout: Int = 4000,
    onControlsTimeoutChange: (Int) -> Unit = {},
    doubleTapSeekDuration: Int = 10,
    onDoubleTapSeekDurationChange: (Int) -> Unit = {},
    swipeGesturesEnabled: Boolean = true,
    onSwipeGesturesEnabledChange: (Boolean) -> Unit = {},
    rememberPosition: Boolean = true,
    onRememberPositionChange: (Boolean) -> Unit = {},
    subtitlesEnabled: Boolean = false,
    onSubtitlesEnabledChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = currentPalette()
    val shape2 = cornerRadius(2.dp)
    val shape3 = cornerRadius(3.dp)
    val shape4 = cornerRadius(4.dp)
    val shape8 = cornerRadius(8.dp)
    val shape10 = cornerRadius(10.dp)
    val headerContentHeight = 40.dp
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Convert resizeMode string to AspectRatioFrameLayout constant
    val currentResizeMode = when (resizeMode) {
        "fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        "zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    // Fullscreen mode state
    var isFullscreen by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var selectedNavItem by remember { mutableStateOf<BottomNavItem?>(null) }

    // Track manual fullscreen toggle to prevent auto-fullscreen from overriding user choice
    var manualFullscreenOverride by remember { mutableStateOf(false) }

    // Auto-fullscreen on landscape rotation and auto-exit on portrait
    LaunchedEffect(isLandscape, autoFullscreenLandscape) {
        if (autoFullscreenLandscape && !manualFullscreenOverride) {
            if (isLandscape) {
                isFullscreen = true
            } else {
                // Auto-exit fullscreen when rotating back to portrait
                isFullscreen = false
            }
        }
    }

    // Reset manual override when orientation changes
    LaunchedEffect(isLandscape) {
        manualFullscreenOverride = false
    }

    // Handle fullscreen mode - hide system UI
    DisposableEffect(isFullscreen) {
        val activity = context as? Activity
        val window = activity?.window
        val decorView = window?.decorView

        if (isFullscreen) {
            // Hide system UI for immersive mode
            @Suppress("DEPRECATION")
            decorView?.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        } else {
            // Show system UI
            @Suppress("DEPRECATION")
            decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }

        onDispose {
            // Restore system UI on dispose
            @Suppress("DEPRECATION")
            decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    // Keep screen on functionality
    DisposableEffect(keepScreenOn) {
        val window = (context as? Activity)?.window
        if (keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Brightness control
    // Store original brightness on entry and restore on exit
    val originalBrightness = remember {
        val window = (context as? Activity)?.window
        window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    }

    LaunchedEffect(brightness) {
        val window = (context as? Activity)?.window
        window?.let {
            val layoutParams = it.attributes
            layoutParams.screenBrightness = brightness
            it.attributes = layoutParams
        }
    }

    // Restore original brightness on dispose
    DisposableEffect(Unit) {
        onDispose {
            val window = (context as? Activity)?.window
            window?.let {
                val layoutParams = it.attributes
                layoutParams.screenBrightness = originalBrightness
                it.attributes = layoutParams
            }
        }
    }

    // Responsive sizing for controls
    val playButtonSize = when {
        screenWidth < 400.dp -> 48.dp
        screenWidth < 600.dp -> 56.dp
        else -> 64.dp
    }
    val seekButtonSize = when {
        screenWidth < 400.dp -> 40.dp
        screenWidth < 600.dp -> 44.dp
        else -> 48.dp
    }
    val iconSize = when {
        screenWidth < 400.dp -> 24.dp
        screenWidth < 600.dp -> 28.dp
        else -> 32.dp
    }
    val seekIconSize = when {
        screenWidth < 400.dp -> 20.dp
        screenWidth < 600.dp -> 24.dp
        else -> 28.dp
    }
    val buttonSpacing = when {
        screenWidth < 400.dp -> 16.dp
        screenWidth < 600.dp -> 24.dp
        else -> 28.dp
    }

    // Player state
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(movie.lastPosition) }
    var duration by remember { mutableLongStateOf(movie.duration) }
    var isBuffering by remember { mutableStateOf(true) }

    // Create ExoPlayer with MediaSession for notification controls
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    var mediaSession by remember { mutableStateOf<MediaSession?>(null) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }

    // Set up player and media session
    DisposableEffect(exoPlayer) {
        // Create media item with metadata for notification
        val mediaItem = MediaItem.Builder()
            .setUri(movie.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(movie.title)
                    .build()
            )
            .build()

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.seekTo(movie.lastPosition)
        exoPlayer.playWhenReady = true

        // Create pending intent for notification tap
        val sessionActivityIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create MediaSession for notification controls with unique ID
        val session = MediaSession.Builder(context, exoPlayer)
            .setId("movie_player_${movie.id}")
            .setSessionActivity(sessionActivityIntent)
            .build()
        mediaSession = session

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            onPositionChange(exoPlayer.currentPosition)
            exoPlayer.removeListener(listener)
            mediaSession?.release()
            mediaSession = null
            exoPlayer.release()
        }
    }

    // Update playback speed when changed
    LaunchedEffect(playbackSpeed) {
        val safeSpeed = playbackSpeed.coerceIn(0.5f, 2f)
        exoPlayer.playbackParameters = PlaybackParameters(safeSpeed, 1f)
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

    // Auto-hide controls when playing (uses configurable timeout)
    LaunchedEffect(showControls, isPlaying, controlsTimeout) {
        if (showControls && isPlaying) {
            delay(controlsTimeout.toLong())
            showControls = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header bar - hidden in fullscreen mode
            AnimatedVisibility(
                visible = !isFullscreen,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Surface(
                    color = Color.Transparent,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .statusBarsPadding()
                        .background(palette.headerGradient())
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .height(headerContentHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        // Back button on the left
                        if (showBackButton) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.align(Alignment.CenterStart)
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

                        // Search icon on the right - only shown when showSearchBar is enabled
                        if (showSearchBar) {
                            IconButton(
                                onClick = { /* Search functionality placeholder */ },
                                modifier = Modifier.align(Alignment.CenterEnd)
                            ) {
                                Icon(
                                    AppIcons.Search,
                                    contentDescription = "Search",
                                    tint = palette.shade11
                                )
                            }
                        }
                    }
                }
            }

            // Movie container - scaled to fit in available space
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = if (!isFullscreen) 80.dp else 0.dp) // Add padding to prevent navbar clipping
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showControls = !showControls },
                contentAlignment = Alignment.Center
            ) {
                // Movie player view with proper scaling
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            setResizeMode(currentResizeMode)
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            playerView = this
                        }
                    },
                    update = { view ->
                        view.resizeMode = currentResizeMode
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.95f) // Scale to 95% width for better fit
                        .aspectRatio(16f / 9f) // Maintain 16:9 aspect ratio
                )

                // Loading indicator
                if (isBuffering) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = palette.primary,
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp
                        )
                    }
                }

                // Center play/pause controls overlay
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showControls,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Main playback controls row
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(buttonSpacing),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Rewind 30 seconds
                                IconButton(
                                    onClick = {
                                        exoPlayer.seekTo((currentPosition - 30000).coerceAtLeast(0))
                                    },
                                    modifier = Modifier
                                        .size(seekButtonSize * 0.85f)
                                        .clip(CircleShape)
                                        .background(palette.accent.copy(alpha = 0.25f))
                                ) {
                                    Icon(
                                        AppIcons.Replay30,
                                        contentDescription = "Rewind 30 seconds",
                                        tint = palette.accent.copy(alpha = 0.9f),
                                        modifier = Modifier.size(seekIconSize * 0.9f)
                                    )
                                }

                                // Rewind 10 seconds
                                IconButton(
                                    onClick = {
                                        exoPlayer.seekTo((currentPosition - 10000).coerceAtLeast(0))
                                    },
                                    modifier = Modifier
                                        .size(seekButtonSize)
                                        .clip(CircleShape)
                                        .background(palette.accent.copy(alpha = 0.35f))
                                ) {
                                    Icon(
                                        AppIcons.Replay10,
                                        contentDescription = "Rewind 10 seconds",
                                        tint = palette.accent,
                                        modifier = Modifier.size(seekIconSize)
                                    )
                                }

                                // Play/Pause
                                IconButton(
                                    onClick = {
                                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    },
                                    modifier = Modifier
                                        .size(playButtonSize)
                                        .clip(CircleShape)
                                        .background(palette.accentGradient())
                                ) {
                                    Icon(
                                        if (isPlaying) AppIcons.Pause else AppIcons.Play,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(iconSize)
                                    )
                                }

                                // Forward 10 seconds
                                IconButton(
                                    onClick = {
                                        val maxPos = if (duration > 0) duration else Long.MAX_VALUE
                                        exoPlayer.seekTo((currentPosition + 10000).coerceAtMost(maxPos))
                                    },
                                    modifier = Modifier
                                        .size(seekButtonSize)
                                        .clip(CircleShape)
                                        .background(palette.accent.copy(alpha = 0.35f))
                                ) {
                                    Icon(
                                        AppIcons.Forward10,
                                        contentDescription = "Forward 10 seconds",
                                        tint = palette.accent,
                                        modifier = Modifier.size(seekIconSize)
                                    )
                                }

                                // Forward 30 seconds
                                IconButton(
                                    onClick = {
                                        val maxPos = if (duration > 0) duration else Long.MAX_VALUE
                                        exoPlayer.seekTo((currentPosition + 30000).coerceAtMost(maxPos))
                                    },
                                    modifier = Modifier
                                        .size(seekButtonSize * 0.85f)
                                        .clip(CircleShape)
                                        .background(palette.accent.copy(alpha = 0.25f))
                                ) {
                                    Icon(
                                        AppIcons.Forward30,
                                        contentDescription = "Forward 30 seconds",
                                        tint = palette.accent.copy(alpha = 0.9f),
                                        modifier = Modifier.size(seekIconSize * 0.9f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Progress bar and time display - hidden in fullscreen mode
            AnimatedVisibility(
                visible = !isFullscreen,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f)
                                )
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Movie title and fullscreen button row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = movie.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            // Fullscreen toggle button
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f))
                                    .clickable {
                                        isFullscreen = !isFullscreen
                                        manualFullscreenOverride = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isFullscreen) AppIcons.FullscreenExit else AppIcons.Fullscreen,
                                    contentDescription = if (isFullscreen) "Exit Fullscreen" else "Fullscreen",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Custom progress bar with gradient
                        val progress = if (duration > 0) {
                            currentPosition.toFloat() / duration
                        } else 0f

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(shape3)
                                .background(Color.White.copy(alpha = 0.3f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .height(3.dp)
                                    .clip(shape3)
                                    .background(Color.White)
                            )
                        }

                        // Clickable seek area over progress bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .offset(y = (-12).dp)
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        val seekProgress = (offset.x / size.width).coerceIn(0f, 1f)
                                        val newPosition = (seekProgress * duration).toLong()
                                        exoPlayer.seekTo(newPosition)
                                        currentPosition = newPosition
                                    }
                                }
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Current time
                            Text(
                                text = formatMovieTime(currentPosition),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.shade2
                            )

                            // Playback speed indicator with accent background
                            Box(
                                modifier = Modifier
                                    .clip(shape4)
                                    .background(palette.accent.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${playbackSpeed}x",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.accent
                                )
                            }

                            // Duration
                            Text(
                                text = formatMovieTime(duration),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.shade3
                            )
                        }
                    }
                }
            }

            // Bottom navigation bar - hidden in fullscreen mode
            AnimatedVisibility(
                visible = !isFullscreen,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
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
                                showSettings = false
                                // Don't highlight - navigating away
                                onNavigateToLibrary()
                            },
                            BottomNavItem.PROFILE to {
                                showSettings = false
                                // Don't highlight - navigating away
                                onNavigateToProfile()
                            },
                            BottomNavItem.SETTINGS to {
                                val toggled = !showSettings
                                showSettings = toggled
                                selectedNavItem = if (toggled) BottomNavItem.SETTINGS else null
                            }
                        ).forEach { (item, action) ->
                            val isSelected = selectedNavItem == item
                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()

                            val scale by animateFloatAsState(
                                targetValue = when {
                                    isPressed -> 0.85f
                                    isSelected -> 1.1f
                                    else -> 1f
                                },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessHigh
                                ),
                                label = "navScale"
                            )

                            val offsetY by animateFloatAsState(
                                targetValue = if (isSelected) -4f else 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "navOffset"
                            )

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .scale(scale)
                                    .offset(y = offsetY.dp)
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null
                                    ) { action() }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.title,
                                        modifier = Modifier.size(24.dp),
                                        tint = if (isSelected) palette.shade12 else palette.shade11.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.title,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 11.sp,
                                        color = if (isSelected) palette.shade12 else palette.shade11.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
        }

        // Settings panel overlay - positioned directly above navbar
        // Adaptive sizing based on screen
        val settingsPadding = if (screenWidth < 400.dp) 12.dp else 16.dp
        val settingsIconSize = if (screenWidth < 400.dp) 14.dp else 16.dp
        val settingsButtonHeight = if (screenWidth < 400.dp) 32.dp else 36.dp
        val settingsFontSize = if (screenWidth < 400.dp) 10.sp else 12.sp
        val settingsMaxHeight = (configuration.screenHeightDp * 0.6f).dp

        AnimatedVisibility(
            visible = showSettings,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = palette.surface,
                shape = cornerRadiusTop(16.dp),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = settingsMaxHeight)
                    .padding(bottom = 80.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = settingsPadding, vertical = 10.dp)
                ) {
                    // Header with drag handle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(shape2)
                                .background(palette.textMuted.copy(alpha = 0.3f))
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                AppIcons.Movie,
                                contentDescription = null,
                                tint = palette.accent,
                                modifier = Modifier.size(if (screenWidth < 400.dp) 18.dp else 22.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Movie Settings",
                                style = if (screenWidth < 400.dp) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary
                            )
                        }
                        IconButton(
                            onClick = { showSettings = false; selectedNavItem = null },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                AppIcons.Close,
                                contentDescription = "Close",
                                tint = palette.textMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Display Settings Card
                    Surface(
                        color = palette.surfaceMedium,
                        shape = shape10,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            // Section header
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    AppIcons.Visibility,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier.size(settingsIconSize)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Display",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.textPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Brightness row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    AppIcons.BrightnessLow,
                                    contentDescription = null,
                                    tint = palette.textMuted,
                                    modifier = Modifier.size(settingsIconSize)
                                )
                                MinimalSlider(
                                    value = brightness,
                                    onValueChange = { onBrightnessChange(it) },
                                    valueRange = 0.1f..1f,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp)
                                )
                                Icon(
                                    AppIcons.BrightnessHigh,
                                    contentDescription = null,
                                    tint = palette.textMuted,
                                    modifier = Modifier.size(settingsIconSize)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Scale mode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(settingsButtonHeight)
                                    .clip(shape8)
                                    .background(palette.background),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                listOf("fit" to "Fit", "fill" to "Fill", "zoom" to "Zoom").forEach { (mode, label) ->
                                    val isSelected = resizeMode == mode
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(shape8)
                                            .background(if (isSelected) palette.accent else Color.Transparent)
                                            .clickable { onResizeModeChange(mode) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = settingsFontSize,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isSelected) palette.onPrimary else palette.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Playback Settings Card
                    Surface(
                        color = palette.surfaceMedium,
                        shape = shape10,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    AppIcons.Play,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier.size(settingsIconSize)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Playback",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.textPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Speed selector
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(settingsButtonHeight)
                                    .clip(shape8)
                                    .background(palette.background),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                    val isSelected = playbackSpeed == speed
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(shape8)
                                            .background(if (isSelected) palette.accent else Color.Transparent)
                                            .clickable { onPlaybackSpeedChange(speed) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${speed}x",
                                            fontSize = settingsFontSize,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isSelected) palette.onPrimary else palette.textPrimary
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Subtitles toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape8)
                                    .background(palette.background)
                                    .clickable { onSubtitlesEnabledChange(!subtitlesEnabled) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        AppIcons.TextFields,
                                        contentDescription = null,
                                        tint = palette.textPrimary,
                                        modifier = Modifier.size(settingsIconSize)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Subtitles",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.textPrimary
                                    )
                                }
                                Switch(
                                    checked = subtitlesEnabled,
                                    onCheckedChange = { onSubtitlesEnabledChange(it) },
                                    modifier = Modifier.scale(0.8f),
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = palette.onPrimary,
                                        checkedTrackColor = palette.accent,
                                        uncheckedThumbColor = palette.textMuted,
                                        uncheckedTrackColor = palette.textMuted.copy(alpha = 0.2f)
                                    )
                                )
                            }

                            // Remember position toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape8)
                                    .background(palette.background)
                                    .clickable { onRememberPositionChange(!rememberPosition) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        AppIcons.Bookmark,
                                        contentDescription = null,
                                        tint = palette.textPrimary,
                                        modifier = Modifier.size(settingsIconSize)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Remember Position",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.textPrimary
                                    )
                                }
                                Switch(
                                    checked = rememberPosition,
                                    onCheckedChange = { onRememberPositionChange(it) },
                                    modifier = Modifier.scale(0.8f),
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = palette.onPrimary,
                                        checkedTrackColor = palette.accent,
                                        uncheckedThumbColor = palette.textMuted,
                                        uncheckedTrackColor = palette.textMuted.copy(alpha = 0.2f)
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Controls Settings Card
                    Surface(
                        color = palette.surfaceMedium,
                        shape = shape10,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    AppIcons.TouchApp,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier.size(settingsIconSize)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Controls",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.textPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Fullscreen toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape8)
                                    .background(if (isFullscreen) palette.accent.copy(alpha = 0.15f) else palette.background)
                                    .clickable {
                                        isFullscreen = !isFullscreen
                                        manualFullscreenOverride = true
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (isFullscreen) AppIcons.FullscreenExit else AppIcons.Fullscreen,
                                        contentDescription = null,
                                        tint = if (isFullscreen) palette.accent else palette.textPrimary,
                                        modifier = Modifier.size(settingsIconSize)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (isFullscreen) "Exit Fullscreen" else "Fullscreen",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isFullscreen) palette.accent else palette.textPrimary
                                    )
                                }
                                Icon(
                                    AppIcons.ChevronRight,
                                    contentDescription = null,
                                    tint = palette.textMuted,
                                    modifier = Modifier.size(settingsIconSize)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Auto-fullscreen on landscape
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape8)
                                    .background(palette.background)
                                    .clickable { onAutoFullscreenLandscapeChange(!autoFullscreenLandscape) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        AppIcons.ScreenRotation,
                                        contentDescription = null,
                                        tint = palette.textPrimary,
                                        modifier = Modifier.size(settingsIconSize)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Auto-fullscreen on Rotate",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.textPrimary
                                    )
                                }
                                Switch(
                                    checked = autoFullscreenLandscape,
                                    onCheckedChange = { onAutoFullscreenLandscapeChange(it) },
                                    modifier = Modifier.scale(0.8f),
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = palette.onPrimary,
                                        checkedTrackColor = palette.accent,
                                        uncheckedThumbColor = palette.textMuted,
                                        uncheckedTrackColor = palette.textMuted.copy(alpha = 0.2f)
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Keep Screen On
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape8)
                                    .background(palette.background)
                                    .clickable { onKeepScreenOnChange(!keepScreenOn) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (keepScreenOn) AppIcons.Visibility else AppIcons.VisibilityOff,
                                        contentDescription = null,
                                        tint = palette.textPrimary,
                                        modifier = Modifier.size(settingsIconSize)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Keep Screen On",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.textPrimary
                                    )
                                }
                                Switch(
                                    checked = keepScreenOn,
                                    onCheckedChange = { onKeepScreenOnChange(it) },
                                    modifier = Modifier.scale(0.8f),
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = palette.onPrimary,
                                        checkedTrackColor = palette.accent,
                                        uncheckedThumbColor = palette.textMuted,
                                        uncheckedTrackColor = palette.textMuted.copy(alpha = 0.2f)
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Swipe Gestures
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape8)
                                    .background(palette.background)
                                    .clickable { onSwipeGesturesEnabledChange(!swipeGesturesEnabled) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        AppIcons.Vibration,
                                        contentDescription = null,
                                        tint = palette.textPrimary,
                                        modifier = Modifier.size(settingsIconSize)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Swipe Gestures",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.textPrimary
                                    )
                                }
                                Switch(
                                    checked = swipeGesturesEnabled,
                                    onCheckedChange = { onSwipeGesturesEnabledChange(it) },
                                    modifier = Modifier.scale(0.8f),
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = palette.onPrimary,
                                        checkedTrackColor = palette.accent,
                                        uncheckedThumbColor = palette.textMuted,
                                        uncheckedTrackColor = palette.textMuted.copy(alpha = 0.2f)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Format milliseconds to HH:MM:SS or MM:SS format
 */
private fun formatMovieTime(millis: Long): String {
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
