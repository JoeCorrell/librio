package com.librio.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.scale
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
import com.librio.model.*
import com.librio.navigation.BottomNavItem
import com.librio.ui.theme.AppIcons
import com.librio.ui.theme.cornerRadius
import com.librio.ui.theme.coverArtGradient
import com.librio.ui.theme.currentPalette
import com.librio.ui.theme.headerGradient

/**
 * Generic series/playlist detail screen that works for all content types.
 * Shows header, content list, and navbar.
 * Only shows playback controls for audio/video content (music, audiobooks, movies).
 */
@Composable
fun SeriesDetailScreen(
    series: LibrarySeries,
    contentType: ContentType,
    // Content lists - only one will be non-empty based on contentType
    musicTracks: List<LibraryMusic> = emptyList(),
    audiobooks: List<LibraryAudiobook> = emptyList(),
    books: List<LibraryBook> = emptyList(),
    comics: List<LibraryComic> = emptyList(),
    movies: List<LibraryMovie> = emptyList(),
    // Cover art
    coverArtBitmap: Bitmap? = null,
    // Playback state (for audio/video content)
    currentlyPlayingId: String? = null,
    isPlaying: Boolean = false,
    isShuffleEnabled: Boolean = false,
    isRepeatEnabled: Boolean = false,
    // UI settings
    showPlaceholderIcons: Boolean = true,
    showBackButton: Boolean = true,
    // Miniplayer content
    miniplayerContent: @Composable (() -> Unit)? = null,
    // Callbacks
    onMusicClick: (LibraryMusic, List<LibraryMusic>) -> Unit = { _, _ -> },
    onAudiobookClick: (LibraryAudiobook) -> Unit = {},
    onBookClick: (LibraryBook) -> Unit = {},
    onComicClick: (LibraryComic) -> Unit = {},
    onMovieClick: (LibraryMovie) -> Unit = {},
    onShuffleClick: () -> Unit = {},
    onRepeatClick: () -> Unit = {},
    onPlayAllClick: () -> Unit = {},
    onPlayPause: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onBack: () -> Unit = {},
    // Edit and remove callbacks
    onEditMusicMetadata: (LibraryMusic) -> Unit = {},
    onRemoveMusicFromPlaylist: (LibraryMusic) -> Unit = {},
    // Navigation
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val palette = currentPalette()
    val shape16 = cornerRadius(16.dp)
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val context = LocalContext.current

    // Determine if this content type supports playback controls
    val hasPlaybackControls = contentType == ContentType.MUSIC ||
        contentType == ContentType.CREEPYPASTA ||
        contentType == ContentType.AUDIOBOOK ||
        contentType == ContentType.MOVIE

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

    // Use custom cover art if available, otherwise fall back to provided cover art
    val displayCoverArt = customCoverArt ?: coverArtBitmap

    // Calculate item count based on content type
    val itemCount = when (contentType) {
        ContentType.MUSIC, ContentType.CREEPYPASTA -> musicTracks.size
        ContentType.AUDIOBOOK -> audiobooks.size
        ContentType.EBOOK -> books.size
        ContentType.COMICS -> comics.size
        ContentType.MOVIE -> movies.size
    }

    // Get the appropriate icon for the content type
    val contentIcon = when (contentType) {
        ContentType.MUSIC, ContentType.CREEPYPASTA -> AppIcons.Playlist
        ContentType.AUDIOBOOK -> AppIcons.Audiobook
        ContentType.EBOOK -> AppIcons.Book
        ContentType.COMICS -> AppIcons.Comic
        ContentType.MOVIE -> AppIcons.Movie
    }

    // Get item label
    val itemLabel = when (contentType) {
        ContentType.MUSIC, ContentType.CREEPYPASTA -> if (itemCount == 1) "track" else "tracks"
        ContentType.AUDIOBOOK -> if (itemCount == 1) "audiobook" else "audiobooks"
        ContentType.EBOOK -> if (itemCount == 1) "book" else "books"
        ContentType.COMICS -> if (itemCount == 1) "comic" else "comics"
        ContentType.MOVIE -> if (itemCount == 1) "movie" else "movies"
    }

    // Calculate total duration for audio/video content
    val totalDuration = remember(musicTracks, audiobooks, movies) {
        when (contentType) {
            ContentType.MUSIC, ContentType.CREEPYPASTA -> musicTracks.sumOf { it.duration }
            ContentType.AUDIOBOOK -> audiobooks.sumOf { it.duration }
            ContentType.MOVIE -> movies.sumOf { it.duration }
            else -> 0L
        }
    }

    // Format total duration
    val formattedTotalDuration = remember(totalDuration) {
        if (totalDuration <= 0) return@remember ""
        val totalSeconds = totalDuration / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        if (hours > 0) {
            "${hours}h ${minutes}m"
        } else {
            "${minutes} min"
        }
    }

    // Responsive cover art size
    val coverArtSize = when {
        screenWidth < 360.dp -> 220.dp
        screenWidth < 400.dp -> 240.dp
        screenWidth < 600.dp -> 280.dp
        else -> 320.dp
    }

    // Responsive playback button sizes
    val playButtonSize = when {
        screenWidth < 360.dp -> 52.dp
        screenWidth < 400.dp -> 56.dp
        screenWidth < 600.dp -> 64.dp
        else -> 72.dp
    }
    val controlButtonSize = when {
        screenWidth < 360.dp -> 40.dp
        screenWidth < 400.dp -> 44.dp
        screenWidth < 600.dp -> 48.dp
        else -> 52.dp
    }
    val shuffleRepeatButtonSize = when {
        screenWidth < 360.dp -> 36.dp
        screenWidth < 400.dp -> 40.dp
        screenWidth < 600.dp -> 44.dp
        else -> 48.dp
    }

    // Responsive icon sizes
    val playIconSize = when {
        screenWidth < 360.dp -> 24.dp
        screenWidth < 400.dp -> 28.dp
        screenWidth < 600.dp -> 32.dp
        else -> 36.dp
    }
    val controlIconSize = when {
        screenWidth < 360.dp -> 20.dp
        screenWidth < 400.dp -> 22.dp
        screenWidth < 600.dp -> 26.dp
        else -> 30.dp
    }

    // Responsive spacing
    val buttonSpacing = when {
        screenWidth < 360.dp -> 8.dp
        screenWidth < 400.dp -> 10.dp
        else -> 12.dp
    }

    // Handle back button/gesture
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            // Header with back button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.headerGradient())
            ) {
                val headerContentHeight = 40.dp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .height(headerContentHeight),
                    contentAlignment = Alignment.Center
                ) {
                    // Back button on the left
                    if (showBackButton) {
                        val backInteractionSource = remember { MutableInteractionSource() }
                        val backIsPressed by backInteractionSource.collectIsPressedAsState()
                        val backScale by animateFloatAsState(
                            targetValue = if (backIsPressed) 0.9f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 10000f),
                            label = "backScale"
                        )
                        IconButton(
                            onClick = { onBack() },
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

                    // Series name as title
                    Text(
                        text = series.name,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            lineHeight = 24.sp
                        ),
                        color = palette.shade11,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 56.dp) // Make room for back button
                    )
                }
            }
        },
        bottomBar = {
            // Bottom navigation bar with optional miniplayer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                // Miniplayer if provided
                if (miniplayerContent != null) {
                    miniplayerContent()
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.headerGradient())
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BottomNavItem.entries.forEach { item ->
                            // In series detail, nothing is selected (we're in a sub-screen)
                            val isSelected = false
                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()

                            val scale by animateFloatAsState(
                                targetValue = when {
                                    isPressed -> 0.9f
                                    isSelected -> 1.05f
                                    else -> 1f
                                },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = 10000f
                                ),
                                label = "navScale"
                            )

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .scale(scale)
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null
                                    ) {
                                        when (item) {
                                            BottomNavItem.LIBRARY -> onNavigateToLibrary()
                                            BottomNavItem.PROFILE -> onNavigateToProfile()
                                            BottomNavItem.SETTINGS -> onNavigateToSettings()
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = item.unselectedIcon,
                                    contentDescription = item.title,
                                    modifier = Modifier.size(24.dp),
                                    tint = palette.shade11.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.title,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 11.sp,
                                    color = palette.shade11.copy(alpha = 0.7f)
                                )
                            }
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
                .background(palette.background)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
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
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Cover art
                        Box(
                            modifier = Modifier
                                .size(coverArtSize)
                                .shadow(16.dp, shape16)
                                .clip(shape16)
                                .background(palette.coverArtGradient()),
                            contentAlignment = Alignment.Center
                        ) {
                            if (displayCoverArt != null) {
                                Image(
                                    bitmap = displayCoverArt.asImageBitmap(),
                                    contentDescription = "Cover art",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = contentIcon,
                                    contentDescription = null,
                                    tint = palette.shade7.copy(alpha = 0.95f),
                                    modifier = Modifier.size(72.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Stats row
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$itemCount $itemLabel",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textMuted
                            )
                            if (formattedTotalDuration.isNotEmpty()) {
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
                        }

                        // Playback controls only for audio/video content
                        if (hasPlaybackControls && itemCount > 0) {
                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Shuffle button
                                Box(
                                    modifier = Modifier
                                        .size(shuffleRepeatButtonSize)
                                        .clip(CircleShape)
                                        .background(
                                            if (isShuffleEnabled) palette.accent.copy(alpha = 0.2f)
                                            else palette.surface
                                        )
                                        .clickable { onShuffleClick() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Shuffle,
                                        contentDescription = "Shuffle",
                                        tint = if (isShuffleEnabled) palette.accent else palette.primary,
                                        modifier = Modifier.size(controlIconSize)
                                    )
                                }

                                Spacer(modifier = Modifier.width(buttonSpacing))

                                // Skip Previous button
                                Box(
                                    modifier = Modifier
                                        .size(controlButtonSize)
                                        .clip(CircleShape)
                                        .background(palette.surface)
                                        .clickable { onSkipPrevious() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = AppIcons.SkipPrevious,
                                        contentDescription = "Previous",
                                        tint = palette.primary,
                                        modifier = Modifier.size(controlIconSize)
                                    )
                                }

                                Spacer(modifier = Modifier.width(buttonSpacing))

                                // Play/Pause button
                                Box(
                                    modifier = Modifier
                                        .size(playButtonSize)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(palette.accent, palette.shade6)
                                            )
                                        )
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
                                        modifier = Modifier.size(playIconSize)
                                    )
                                }

                                Spacer(modifier = Modifier.width(buttonSpacing))

                                // Skip Next button
                                Box(
                                    modifier = Modifier
                                        .size(controlButtonSize)
                                        .clip(CircleShape)
                                        .background(palette.surface)
                                        .clickable { onSkipNext() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = AppIcons.SkipNext,
                                        contentDescription = "Next",
                                        tint = palette.primary,
                                        modifier = Modifier.size(controlIconSize)
                                    )
                                }

                                Spacer(modifier = Modifier.width(buttonSpacing))

                                // Repeat button
                                Box(
                                    modifier = Modifier
                                        .size(shuffleRepeatButtonSize)
                                        .clip(CircleShape)
                                        .background(
                                            if (isRepeatEnabled) palette.accent.copy(alpha = 0.2f)
                                            else palette.surface
                                        )
                                        .clickable { onRepeatClick() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Repeat,
                                        contentDescription = "Repeat",
                                        tint = if (isRepeatEnabled) palette.accent else palette.primary,
                                        modifier = Modifier.size(controlIconSize)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // Content list based on type
            when (contentType) {
                ContentType.MUSIC, ContentType.CREEPYPASTA -> {
                    itemsIndexed(musicTracks, key = { _, track -> track.id }) { _, track ->
                        SeriesTrackItem(
                            title = track.title,
                            subtitle = track.artist,
                            duration = track.formattedDuration,
                            coverArt = track.coverArt,
                            isCurrentlyPlaying = track.id == currentlyPlayingId,
                            isPlaying = track.id == currentlyPlayingId && isPlaying,
                            progress = if (track.duration > 0) track.lastPosition.toFloat() / track.duration else null,
                            showProgress = track.lastPosition > 0,
                            icon = AppIcons.Music,
                            onClick = { onMusicClick(track, musicTracks) },
                            onRemoveFromPlaylist = { onRemoveMusicFromPlaylist(track) },
                            onEditMetadata = { onEditMusicMetadata(track) }
                        )
                    }
                }
                ContentType.AUDIOBOOK -> {
                    itemsIndexed(audiobooks, key = { _, book -> book.id }) { _, audiobook ->
                        SeriesTrackItem(
                            title = audiobook.title,
                            subtitle = audiobook.author,
                            duration = audiobook.formattedDuration,
                            coverArt = audiobook.coverArt,
                            isCurrentlyPlaying = audiobook.id == currentlyPlayingId,
                            isPlaying = audiobook.id == currentlyPlayingId && isPlaying,
                            progress = if (audiobook.duration > 0) audiobook.lastPosition.toFloat() / audiobook.duration else null,
                            showProgress = audiobook.lastPosition > 0,
                            icon = AppIcons.Audiobook,
                            onClick = { onAudiobookClick(audiobook) }
                        )
                    }
                }
                ContentType.EBOOK -> {
                    itemsIndexed(books, key = { _, book -> book.id }) { _, book ->
                        SeriesTrackItem(
                            title = book.title,
                            subtitle = book.author,
                            duration = null,
                            coverArt = book.coverArt,
                            isCurrentlyPlaying = false,
                            isPlaying = false,
                            progress = if (book.totalPages > 0) book.currentPage.toFloat() / book.totalPages else null,
                            showProgress = book.currentPage > 0,
                            icon = AppIcons.Book,
                            onClick = { onBookClick(book) }
                        )
                    }
                }
                ContentType.COMICS -> {
                    itemsIndexed(comics, key = { _, comic -> comic.id }) { _, comic ->
                        SeriesTrackItem(
                            title = comic.title,
                            subtitle = comic.author,
                            duration = "${comic.totalPages} pages",
                            coverArt = comic.coverArt,
                            isCurrentlyPlaying = false,
                            isPlaying = false,
                            progress = if (comic.totalPages > 0) comic.currentPage.toFloat() / comic.totalPages else null,
                            showProgress = comic.currentPage > 0,
                            icon = AppIcons.Comic,
                            onClick = { onComicClick(comic) }
                        )
                    }
                }
                ContentType.MOVIE -> {
                    itemsIndexed(movies, key = { _, movie -> movie.id }) { _, movie ->
                        SeriesTrackItem(
                            title = movie.title,
                            subtitle = null,
                            duration = movie.formattedDuration,
                            coverArt = null, // Movies use thumbnailUri, loaded separately
                            isCurrentlyPlaying = movie.id == currentlyPlayingId,
                            isPlaying = movie.id == currentlyPlayingId && isPlaying,
                            progress = if (movie.duration > 0) movie.lastPosition.toFloat() / movie.duration else null,
                            showProgress = movie.lastPosition > 0,
                            icon = AppIcons.Movie,
                            onClick = { onMovieClick(movie) }
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun SeriesTrackItem(
    title: String,
    subtitle: String?,
    duration: String?,
    coverArt: Bitmap?,
    isCurrentlyPlaying: Boolean,
    isPlaying: Boolean,
    progress: Float?,
    showProgress: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    onRemoveFromPlaylist: () -> Unit = {},
    onEditMetadata: () -> Unit = {}
) {
    val palette = currentPalette()
    val shape2 = cornerRadius(2.dp)
    val shape8 = cornerRadius(8.dp)
    val shape12 = cornerRadius(12.dp)
    var showMenu by remember { mutableStateOf(false) }

    // Highlight animation for currently playing
    val highlightAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 0.2f else if (isCurrentlyPlaying) 0.15f else 0f,
        animationSpec = tween(300),
        label = "playing_alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(shape12)
            .background(
                if (isCurrentlyPlaying) palette.accent.copy(alpha = highlightAlpha)
                else palette.surfaceDark.copy(alpha = 0.08f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cover art thumbnail
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(4.dp, shape8)
                .clip(shape8)
                .background(palette.coverArtGradient()),
            contentAlignment = Alignment.Center
        ) {
            if (coverArt != null) {
                Image(
                    bitmap = coverArt.asImageBitmap(),
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = palette.shade7.copy(alpha = 0.95f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Item info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrentlyPlaying) FontWeight.SemiBold else FontWeight.SemiBold,
                color = if (isCurrentlyPlaying) palette.accent else palette.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.primary.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                if (duration != null && subtitle != null) {
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.primary.copy(alpha = 0.5f)
                    )
                }
                if (duration != null) {
                    Text(
                        text = duration,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.primary.copy(alpha = 0.5f)
                    )
                }
            }
            // Progress indicator
            if (showProgress && progress != null && progress > 0f) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = progress.coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(shape2),
                    color = palette.accent,
                    trackColor = palette.shade3
                )
            }
        }

        // 3-dots menu button
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = AppIcons.MoreVert,
                    contentDescription = "More options",
                    tint = palette.primary.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = {
                        showMenu = false
                        onEditMetadata()
                    },
                    leadingIcon = {
                        Icon(
                            AppIcons.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("Remove from playlist") },
                    onClick = {
                        showMenu = false
                        onRemoveFromPlaylist()
                    },
                    leadingIcon = {
                        Icon(
                            AppIcons.Remove,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
            }
        }
    }
}
