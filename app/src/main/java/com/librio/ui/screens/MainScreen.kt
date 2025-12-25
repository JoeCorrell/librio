package com.librio.ui.screens

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.librio.R
import com.librio.model.Category
import com.librio.model.ContentType
import com.librio.model.LibraryAudiobook
import com.librio.model.LibraryBook
import com.librio.model.LibraryComic
import com.librio.model.LibraryMusic
import com.librio.model.LibrarySeries
import com.librio.model.LibraryMovie
import com.librio.model.SortOption
import com.librio.navigation.BottomNavItem
import com.librio.ui.components.MiniPlayer
import com.librio.ui.components.NowPlaying
import com.librio.ui.theme.*
import com.librio.ui.theme.AppIcons

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    audiobooks: List<LibraryAudiobook>,
    books: List<LibraryBook> = emptyList(),
    music: List<LibraryMusic> = emptyList(),
    comics: List<LibraryComic> = emptyList(),
    movies: List<LibraryMovie> = emptyList(),
    initialTab: BottomNavItem = BottomNavItem.LIBRARY,
    selectedContentType: ContentType = ContentType.AUDIOBOOK,
    onContentTypeChange: (ContentType) -> Unit = {},
    onAddAudiobook: (Uri) -> Unit,
    onAddBook: (Uri) -> Unit = {},
    onAddMusic: (Uri) -> Unit = {},
    onAddComic: (Uri) -> Unit = {},
    onAddMovie: (Uri) -> Unit = {},
    onSelectAudiobook: (LibraryAudiobook) -> Unit,
    onSelectBook: (LibraryBook) -> Unit = {},
    onSelectMusic: (LibraryMusic) -> Unit = {},
    onSelectComic: (LibraryComic) -> Unit = {},
    onSelectMovie: (LibraryMovie) -> Unit = {},
    onDeleteAudiobook: (LibraryAudiobook) -> Unit,
    onDeleteBook: (LibraryBook) -> Unit = {},
    onEditAudiobook: (LibraryAudiobook, String, String) -> Unit,
    onEditBook: (LibraryBook, String, String) -> Unit = { _, _, _ -> },
    onEditMusic: (LibraryMusic, String, String) -> Unit = { _, _, _ -> },
    onDeleteMusic: (LibraryMusic) -> Unit = {},
    onEditComic: (LibraryComic, String, String) -> Unit = { _, _, _ -> },
    onDeleteComic: (LibraryComic) -> Unit = {},
    onEditMovie: (LibraryMovie, String) -> Unit = { _, _ -> },
    onDeleteMovie: (LibraryMovie) -> Unit = {},
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    headerTitle: String = "Librio",
    isSearchVisible: Boolean,
    onToggleSearch: () -> Unit,
    sortOption: SortOption,
    onSortOptionChange: (SortOption) -> Unit,
    categories: List<Category> = emptyList(),
    selectedCategoryId: String? = null,
    onSelectCategory: (String?) -> Unit = {},
    onAddCategory: (String) -> Unit = {},
    onDeleteCategory: (String) -> Unit = {},
    onRenameCategory: (String, String) -> Unit = { _, _ -> },
    onSetAudiobookCategory: (String, String?) -> Unit = { _, _ -> },
    // Series management
    seriesList: List<LibrarySeries> = emptyList(),
    onAddSeries: (String) -> Unit = {},
    onDeleteSeries: (String) -> Unit = {},
    onRenameSeries: (String, String) -> Unit = { _, _ -> },
    onSetAudiobookSeries: (String, String?) -> Unit = { _, _ -> },
    onSetBookSeries: (String, String?) -> Unit = { _, _ -> },
    onSetMusicSeries: (String, String?) -> Unit = { _, _ -> },
    onSetComicSeries: (String, String?) -> Unit = { _, _ -> },
    onSetMovieSeries: (String, String?) -> Unit = { _, _ -> },
    onPlaylistClick: (LibrarySeries) -> Unit = {},
    onSetSeriesCoverArt: (String, String?) -> Unit = { _, _ -> },
    onSetAudiobookCoverArt: (String, String?) -> Unit = { _, _ -> },
    onSetBookCoverArt: (String, String?) -> Unit = { _, _ -> },
    onSetMusicCoverArt: (String, String?) -> Unit = { _, _ -> },
    onSetComicCoverArt: (String, String?) -> Unit = { _, _ -> },
    onSetMovieCoverArt: (String, String?) -> Unit = { _, _ -> },
    collapsedSeries: Set<String> = emptySet(),
    onCollapsedSeriesChange: (Set<String>) -> Unit = {},
    libraryOwnerName: String,
    profiles: List<UserProfile>,
    onProfileSelect: (UserProfile) -> Unit,
    onAddProfile: (String) -> Unit,
    onDeleteProfile: (UserProfile) -> Unit,
    onRenameProfile: (UserProfile, String) -> Unit,
    onSetProfilePicture: (UserProfile, String?) -> Unit = { _, _ -> },
    onBackupProfile: (UserProfile) -> Unit = {},
    onRescanLibrary: () -> Unit,
    onUpdateLibrary: () -> Unit = {},
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    accentTheme: AppTheme = currentTheme,
    onAccentThemeChange: (AppTheme) -> Unit = {},
    darkMode: Boolean = false,
    onDarkModeChange: (Boolean) -> Unit = {},
    // Settings parameters
    skipForward: Int = 30,
    onSkipForwardChange: (Int) -> Unit = {},
    skipBack: Int = 10,
    onSkipBackChange: (Int) -> Unit = {},
    autoBookmark: Boolean = true,
    onAutoBookmarkChange: (Boolean) -> Unit = {},
    keepScreenOn: Boolean = false,
    onKeepScreenOnChange: (Boolean) -> Unit = {},
    volumeBoostEnabled: Boolean = false,
    onVolumeBoostEnabledChange: (Boolean) -> Unit = {},
    volumeBoostLevel: Float = 1.5f,
    onVolumeBoostLevelChange: (Float) -> Unit = {},
    onLibraryOwnerNameChange: (String) -> Unit = {},
    playbackSpeed: Float = 1.0f,
    onPlaybackSpeedChange: (Float) -> Unit = {},
    sleepTimerMinutes: Int = 0,
    onSleepTimerChange: (Int) -> Unit = {},
    autoPlayNext: Boolean = true,
    onAutoPlayNextChange: (Boolean) -> Unit = {},
    defaultLibraryView: String = "GRID",
    onDefaultLibraryViewChange: (String) -> Unit = {},
    defaultSortOrder: String = "TITLE",
    onDefaultSortOrderChange: (String) -> Unit = {},
    resumePlayback: Boolean = true,
    onResumePlaybackChange: (Boolean) -> Unit = {},
    showPlaybackNotification: Boolean = true,
    onShowPlaybackNotificationChange: (Boolean) -> Unit = {},
    // New settings
    showPlaceholderIcons: Boolean = true,
    onShowPlaceholderIconsChange: (Boolean) -> Unit = {},
    normalizeAudio: Boolean = false,
    onNormalizeAudioChange: (Boolean) -> Unit = {},
    bassBoostLevel: Int = 0,
    onBassBoostLevelChange: (Int) -> Unit = {},
    equalizerPreset: String = "DEFAULT",
    onEqualizerPresetChange: (String) -> Unit = {},
    showFileSize: Boolean = true,
    onShowFileSizeChange: (Boolean) -> Unit = {},
    showDuration: Boolean = true,
    onShowDurationChange: (Boolean) -> Unit = {},
    animationSpeed: String = "Normal",
    onAnimationSpeedChange: (String) -> Unit = {},
    hapticFeedback: Boolean = true,
    onHapticFeedbackChange: (Boolean) -> Unit = {},
    confirmBeforeDelete: Boolean = true,
    onConfirmBeforeDeleteChange: (Boolean) -> Unit = {},
    rememberLastPosition: Boolean = true,
    onRememberLastPositionChange: (Boolean) -> Unit = {},
    autoRewind: Int = 0,
    onAutoRewindChange: (Int) -> Unit = {},
    headsetControls: Boolean = true,
    onHeadsetControlsChange: (Boolean) -> Unit = {},
    pauseOnDisconnect: Boolean = true,
    onPauseOnDisconnectChange: (Boolean) -> Unit = {},
    showBackButton: Boolean = true,
    onShowBackButtonChange: (Boolean) -> Unit = {},
    showSearchBar: Boolean = true,
    onShowSearchBarChange: (Boolean) -> Unit = {},
    useSquareCorners: Boolean = false,
    onUseSquareCornersChange: (Boolean) -> Unit = {},
    customPrimaryColor: Int = 0x00897B,
    onCustomPrimaryColorChange: (Int) -> Unit = {},
    customAccentColor: Int = 0x26A69A,
    onCustomAccentColorChange: (Int) -> Unit = {},
    customBackgroundColor: Int = 0x121212,
    onCustomBackgroundColorChange: (Int) -> Unit = {},
    appScale: Float = 1.0f,
    onAppScaleChange: (Float) -> Unit = {},
    uiFontScale: Float = 1.0f,
    onUiFontScaleChange: (Float) -> Unit = {},
    uiFontFamily: String = "Default",
    onUiFontFamilyChange: (String) -> Unit = {},
    // Profile-specific audio settings callbacks
    onProfilePlaybackSpeedChange: (Float) -> Unit = {},
    onProfileSkipForwardChange: (Int) -> Unit = {},
    onProfileSkipBackChange: (Int) -> Unit = {},
    onProfileVolumeBoostEnabledChange: (Boolean) -> Unit = {},
    onProfileVolumeBoostLevelChange: (Float) -> Unit = {},
    onProfileNormalizeAudioChange: (Boolean) -> Unit = {},
    onProfileBassBoostLevelChange: (Float) -> Unit = {},
    onProfileEqualizerPresetChange: (String) -> Unit = {},
    onProfileSleepTimerChange: (Int) -> Unit = {},
    onBackPressed: () -> Unit = {},
    // Mini player parameters
    nowPlaying: NowPlaying? = null,
    onMiniPlayerPlayPause: () -> Unit = {},
    onMiniPlayerNext: () -> Unit = {},
    onMiniPlayerPrevious: () -> Unit = {},
    onMiniPlayerSeekBack: () -> Unit = {},
    onMiniPlayerSeekForward: () -> Unit = {},
    onMiniPlayerClick: () -> Unit = {},
    onMiniPlayerDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = currentPalette()
    var selectedTab by remember { mutableStateOf(initialTab) }


    Scaffold(
        topBar = {
            // Fixed Header with gradient animation
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
                    // Back button on the left - exits app if on LIBRARY, otherwise goes to LIBRARY
                    if (showBackButton) {
                        val backInteractionSource = remember { MutableInteractionSource() }
                        val backIsPressed by backInteractionSource.collectIsPressedAsState()
                        val backScale by animateFloatAsState(
                            targetValue = if (backIsPressed) 0.9f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 10000f),
                            label = "backScale"
                        )
                        IconButton(
                            onClick = {
                                if (selectedTab == BottomNavItem.LIBRARY) {
                                    onBackPressed()
                                } else {
                                    selectedTab = BottomNavItem.LIBRARY
                                }
                            },
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

                    // Search icon on the right
                    if (showSearchBar) {
                        val searchInteractionSource = remember { MutableInteractionSource() }
                        val searchIsPressed by searchInteractionSource.collectIsPressedAsState()
                        val searchScale by animateFloatAsState(
                            targetValue = if (searchIsPressed) 0.9f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 10000f),
                            label = "searchScale"
                        )
                        IconButton(
                            onClick = onToggleSearch,
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
                    }
                }
            }
        },
        bottomBar = {
            // Column with mini player above navbar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                // Mini player - shows above navbar when something is playing
                MiniPlayer(
                    nowPlaying = nowPlaying,
                    onPlayPause = onMiniPlayerPlayPause,
                    onNext = onMiniPlayerNext,
                    onPrevious = onMiniPlayerPrevious,
                    onSeekBack = onMiniPlayerSeekBack,
                    onSeekForward = onMiniPlayerSeekForward,
                    onClick = { onMiniPlayerClick() },
                    onDismiss = onMiniPlayerDismiss,
                    showPlaceholderIcons = showPlaceholderIcons
                )

                // Full-width navigation bar matching header color with light icons
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
                            val isSelected = selectedTab == item
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

                            // Smooth icon size transition
                            val iconSize by animateDpAsState(
                                targetValue = if (isSelected) 26.dp else 24.dp,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "iconSize"
                            )

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
                                    ) { selectedTab = item }
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
        },
        containerColor = palette.background
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(palette.backgroundGradient())
        ) {
            // Content with smooth transition animation
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    // Determine direction based on tab order
                    val targetIndex = targetState.ordinal
                    val initialIndex = initialState.ordinal

                    if (targetIndex > initialIndex) {
                        // Moving right/forward
                        (fadeIn(animationSpec = tween(150)) +
                            slideInHorizontally(animationSpec = tween(200)) { it / 8 }) togetherWith
                        (fadeOut(animationSpec = tween(100)) +
                            slideOutHorizontally(animationSpec = tween(200)) { -it / 8 })
                    } else {
                        // Moving left/backward
                        (fadeIn(animationSpec = tween(150)) +
                            slideInHorizontally(animationSpec = tween(200)) { -it / 8 }) togetherWith
                        (fadeOut(animationSpec = tween(100)) +
                            slideOutHorizontally(animationSpec = tween(200)) { it / 8 })
                    }
                },
                label = "tabContent"
            ) { tab ->
                when (tab) {
                    BottomNavItem.LIBRARY -> {
                        LibraryListScreen(
                            audiobooks = audiobooks,
                            books = books,
                            music = music,
                            comics = comics,
                            movies = movies,
                            selectedContentType = selectedContentType,
                            onContentTypeChange = onContentTypeChange,
                            onAddAudiobook = onAddAudiobook,
                            onAddBook = onAddBook,
                            onAddMusic = onAddMusic,
                            onAddComic = onAddComic,
                            onAddMovie = onAddMovie,
                            onSelectAudiobook = { audiobook ->
                                onSelectAudiobook(audiobook)
                            },
                            onSelectBook = { book ->
                                onSelectBook(book)
                            },
                            onSelectMusic = { music ->
                                onSelectMusic(music)
                            },
                            onSelectComic = { comic ->
                                onSelectComic(comic)
                            },
                            onSelectMovie = { movie ->
                                onSelectMovie(movie)
                            },
                            onDeleteAudiobook = onDeleteAudiobook,
                            onDeleteBook = onDeleteBook,
                            onEditAudiobook = onEditAudiobook,
                            onEditBook = onEditBook,
                            onEditMusic = onEditMusic,
                            onDeleteMusic = onDeleteMusic,
                            onEditComic = onEditComic,
                            onDeleteComic = onDeleteComic,
                            onEditMovie = onEditMovie,
                            onDeleteMovie = onDeleteMovie,
                            showPlaceholderIcons = showPlaceholderIcons,
                            defaultLibraryView = defaultLibraryView,
                            searchQuery = searchQuery,
                            onSearchQueryChange = onSearchQueryChange,
                            isSearchVisible = isSearchVisible,
                            sortOption = sortOption,
                            onSortOptionChange = onSortOptionChange,
                            categories = categories,
                            selectedCategoryId = selectedCategoryId,
                            onSelectCategory = onSelectCategory,
                            onAddCategory = onAddCategory,
                            onDeleteCategory = onDeleteCategory,
                            onRenameCategory = onRenameCategory,
                            onSetAudiobookCategory = onSetAudiobookCategory,
                            // Series management
                            seriesList = seriesList,
                            onAddSeries = onAddSeries,
                            onDeleteSeries = onDeleteSeries,
                            onRenameSeries = onRenameSeries,
                            onSetAudiobookSeries = onSetAudiobookSeries,
                            onSetBookSeries = onSetBookSeries,
                            onSetMusicSeries = onSetMusicSeries,
                            onSetComicSeries = onSetComicSeries,
                            onSetMovieSeries = onSetMovieSeries,
                            onPlaylistClick = onPlaylistClick,
                            onSetSeriesCoverArt = onSetSeriesCoverArt,
                            onSetAudiobookCoverArt = onSetAudiobookCoverArt,
                            onSetBookCoverArt = onSetBookCoverArt,
                            onSetMusicCoverArt = onSetMusicCoverArt,
                            onSetComicCoverArt = onSetComicCoverArt,
                            onSetMovieCoverArt = onSetMovieCoverArt,
                            collapsedSeries = collapsedSeries,
                            onCollapsedSeriesChange = onCollapsedSeriesChange
                        )
                    }
                    BottomNavItem.PROFILE -> {
                        val activeProfile = profiles.find { it.isActive }
                        ProfileScreen(
                            currentProfileName = activeProfile?.name ?: "Default",
                            profiles = profiles,
                            currentTheme = currentTheme,
                            onThemeChange = onThemeChange,
                            accentTheme = accentTheme,
                            onAccentThemeChange = onAccentThemeChange,
                            darkMode = darkMode,
                            onDarkModeChange = onDarkModeChange,
                            customPrimaryColor = customPrimaryColor,
                            onCustomPrimaryColorChange = onCustomPrimaryColorChange,
                            customAccentColor = customAccentColor,
                            onCustomAccentColorChange = onCustomAccentColorChange,
                            customBackgroundColor = customBackgroundColor,
                            onCustomBackgroundColorChange = onCustomBackgroundColorChange,
                            appScale = appScale,
                            onAppScaleChange = onAppScaleChange,
                            uiFontScale = uiFontScale,
                            onUiFontScaleChange = onUiFontScaleChange,
                            uiFontFamily = uiFontFamily,
                            onUiFontFamilyChange = onUiFontFamilyChange,
                            onProfileSelect = onProfileSelect,
                            onAddProfile = onAddProfile,
                            onDeleteProfile = onDeleteProfile,
                            onRenameProfile = onRenameProfile,
                            onSetProfilePicture = onSetProfilePicture
                        )
                    }
                    BottomNavItem.SETTINGS -> {
                        val activeProfile = profiles.find { it.isActive }
                        SettingsScreen(
                            // Playback Controls
                            headsetControls = headsetControls,
                            onHeadsetControlsChange = onHeadsetControlsChange,
                            pauseOnDisconnect = pauseOnDisconnect,
                            onPauseOnDisconnectChange = onPauseOnDisconnectChange,
                            hapticFeedback = hapticFeedback,
                            onHapticFeedbackChange = onHapticFeedbackChange,
                            showPlaybackNotification = showPlaybackNotification,
                            onShowPlaybackNotificationChange = onShowPlaybackNotificationChange,
                            // Playback Behavior
                            autoPlayNext = autoPlayNext,
                            onAutoPlayNextChange = onAutoPlayNextChange,
                            resumePlayback = resumePlayback,
                            onResumePlaybackChange = onResumePlaybackChange,
                            rememberLastPosition = rememberLastPosition,
                            onRememberLastPositionChange = onRememberLastPositionChange,
                            autoRewind = autoRewind,
                            onAutoRewindChange = onAutoRewindChange,
                            // Library & Display
                            showPlaceholderIcons = showPlaceholderIcons,
                            onShowPlaceholderIconsChange = onShowPlaceholderIconsChange,
                            showFileSize = showFileSize,
                            onShowFileSizeChange = onShowFileSizeChange,
                            showDuration = showDuration,
                            onShowDurationChange = onShowDurationChange,
                            showBackButton = showBackButton,
                            onShowBackButtonChange = onShowBackButtonChange,
                            showSearchBar = showSearchBar,
                            onShowSearchBarChange = onShowSearchBarChange,
                            useSquareCorners = useSquareCorners,
                            onUseSquareCornersChange = onUseSquareCornersChange,
                            defaultLibraryView = defaultLibraryView,
                            onDefaultLibraryViewChange = onDefaultLibraryViewChange,
                            // General
                            autoBookmark = autoBookmark,
                            onAutoBookmarkChange = onAutoBookmarkChange,
                            keepScreenOn = keepScreenOn,
                            onKeepScreenOnChange = onKeepScreenOnChange,
                            confirmBeforeDelete = confirmBeforeDelete,
                            onConfirmBeforeDeleteChange = onConfirmBeforeDeleteChange,
                            // Library actions
                            onUpdateLibrary = onUpdateLibrary,
                            onRescanLibrary = onRescanLibrary,
                            onBackupProfile = { activeProfile?.let { onBackupProfile(it) } },
                            // Current profile name
                            currentProfileName = activeProfile?.name ?: "Default"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    audiobooks: List<LibraryAudiobook>,
    books: List<LibraryBook> = emptyList(),
    onSelectAudiobook: (LibraryAudiobook) -> Unit,
    onSelectBook: (LibraryBook) -> Unit = {},
    libraryOwnerName: String,
    showPlaceholderIcons: Boolean = true,
    modifier: Modifier = Modifier
) {
    val palette = currentPalette()
    // Remember shapes for performance
    val shape20 = cornerRadius(20.dp)

    // Data for sections - memoized to avoid recalculation on every recomposition
    val recentlyPlayed = remember(audiobooks) {
        audiobooks
            .filter { it.lastPlayed > 0 && !it.isCompleted }
            .sortedByDescending { it.lastPlayed }
            .take(6)
    }

    val recentlyRead = remember(books) {
        books
            .filter { it.lastRead > 0 && !it.isCompleted }
            .sortedByDescending { it.lastRead }
            .take(6)
    }

    val recentlyAdded: List<Any> = remember(audiobooks, books) {
        (audiobooks.sortedByDescending { it.dateAdded }.take(3) +
                books.sortedByDescending { it.dateAdded }.take(3))
            .sortedByDescending {
                when (it) {
                    is LibraryAudiobook -> it.dateAdded
                    is LibraryBook -> it.dateAdded
                    else -> 0L
                }
            }
            .take(5)
    }

    // Stats - memoized
    val totalAudiobooks = audiobooks.size
    val totalBooks = books.size
    val completedAudiobooks = remember(audiobooks) { audiobooks.count { it.isCompleted } }
    val completedBooks = remember(books) { books.count { it.isCompleted } }
    val inProgressAudiobooks = remember(audiobooks) { audiobooks.count { it.progress > 0 && !it.isCompleted } }
    val inProgressBooks = remember(books) { books.count { it.currentPage > 0 && !it.isCompleted } }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        // Welcome Header with gradient card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(shape20)
                    .background(palette.accentGradient())
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = if (libraryOwnerName.isNotBlank()) "Welcome back," else "Welcome to",
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.onPrimary.copy(alpha = 0.8f)
                    )
                    Text(
                        text = if (libraryOwnerName.isNotBlank()) libraryOwnerName else "Librio",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.onPrimary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Quick Stats Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(
                            value = totalAudiobooks.toString(),
                            label = "Audiobooks",
                            color = palette.onPrimary
                        )
                        StatItem(
                            value = totalBooks.toString(),
                            label = "Books",
                            color = palette.onPrimary
                        )
                        StatItem(
                            value = (inProgressAudiobooks + inProgressBooks).toString(),
                            label = "In Progress",
                            color = palette.onPrimary
                        )
                        StatItem(
                            value = (completedAudiobooks + completedBooks).toString(),
                            label = "Completed",
                            color = palette.onPrimary
                        )
                    }
                }
            }
        }

        // Continue Listening Section (Horizontal Scroll)
        if (recentlyPlayed.isNotEmpty()) {
            item {
                SectionHeader(title = "Continue Listening", palette = palette)
            }
            item {
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recentlyPlayed, key = { it.id }) { audiobook ->
                        AudiobookCard(
                            audiobook = audiobook,
                            onClick = { onSelectAudiobook(audiobook) },
                            palette = palette,
                            showPlaceholderIcons = showPlaceholderIcons
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // Continue Reading Section (Horizontal Scroll)
        if (recentlyRead.isNotEmpty()) {
            item {
                SectionHeader(title = "Continue Reading", palette = palette)
            }
            item {
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recentlyRead, key = { it.id }) { book ->
                        BookCard(
                            book = book,
                            onClick = { onSelectBook(book) },
                            palette = palette,
                            showPlaceholderIcons = showPlaceholderIcons
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // Recently Added Section
        if (recentlyAdded.isNotEmpty()) {
            item {
                SectionHeader(title = "Recently Added", palette = palette)
            }
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    recentlyAdded.forEach { item ->
                        when (item) {
                            is LibraryAudiobook -> {
                                RecentlyAddedItem(
                                    title = item.title,
                                    subtitle = item.author,
                                    type = "Audiobook",
                                    onClick = { onSelectAudiobook(item) },
                                    palette = palette
                                )
                            }
                            is LibraryBook -> {
                                RecentlyAddedItem(
                                    title = item.title,
                                    subtitle = item.author,
                                    type = "Book",
                                    onClick = { onSelectBook(item) },
                                    palette = palette
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // Empty state with folder info
        if (audiobooks.isEmpty() && books.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = null,
                            modifier = Modifier
                                .size(100.dp)
                                .graphicsLayer { alpha = 0.5f }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Your library is empty",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = palette.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Add files to your profile folder:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.primary.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "📁 Librio/Profiles/{YourProfile}/Audiobooks\n📁 Librio/Profiles/{YourProfile}/Books",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = palette.primary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Files will be automatically detected",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.primary.copy(alpha = 0.5f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun StatItem(
    value: String,
    label: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun SectionHeader(title: String, palette: ThemePalette, showDivider: Boolean = true) {
    Column {
        if (showDivider) {
            Divider(
                color = palette.divider,
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = palette.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun AudiobookCard(
    audiobook: LibraryAudiobook,
    onClick: () -> Unit,
    palette: ThemePalette,
    showPlaceholderIcons: Boolean
) {
    // Remember shape for performance
    val shape12 = cornerRadius(12.dp)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "cardScale"
    )

    Card(
        modifier = Modifier
            .width(140.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = shape12,
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            // Placeholder with gradient and icon
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(palette.coverArtGradient()),
                contentAlignment = Alignment.Center
            ) {
                val usePlaceholder = showPlaceholderIcons || audiobook.coverArt == null
                if (usePlaceholder) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = AppIcons.Audiobook,
                            contentDescription = null,
                            tint = palette.shade7.copy(alpha = 0.95f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = audiobook.fileType.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.shade7.copy(alpha = 0.95f)
                        )
                    }
                } else {
                    Image(
                        bitmap = audiobook.coverArt!!.asImageBitmap(),
                        contentDescription = audiobook.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // Progress indicator overlay with gradient
                if (audiobook.progress > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter)
                            .background(palette.shade6)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(audiobook.progress.coerceIn(0f, 1f))
                                .background(palette.progressGradient())
                        )
                    }
                }
            }

            // Title and author
            Column(
                modifier = Modifier.padding(10.dp)
            ) {
                Text(
                    text = audiobook.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.primary,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = audiobook.author,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.primary.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BookCard(
    book: LibraryBook,
    onClick: () -> Unit,
    palette: ThemePalette,
    showPlaceholderIcons: Boolean
) {
    // Remember shape for performance
    val shape12 = cornerRadius(12.dp)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "bookCardScale"
    )

    Card(
        modifier = Modifier
            .width(140.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = shape12,
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            // Cover placeholder with gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(palette.coverArtGradient()),
                contentAlignment = Alignment.Center
            ) {
                val usePlaceholder = showPlaceholderIcons || book.coverArt == null
                if (usePlaceholder) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = AppIcons.Book,
                            contentDescription = null,
                            tint = palette.shade7.copy(alpha = 0.95f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = book.fileType.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.shade7.copy(alpha = 0.95f)
                        )
                    }
                } else {
                    Image(
                        bitmap = book.coverArt!!.asImageBitmap(),
                        contentDescription = book.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // Progress indicator overlay with gradient
                val progress = if (book.totalPages > 0) book.currentPage.toFloat() / book.totalPages else 0f
                if (progress > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter)
                            .background(palette.shade6)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .background(palette.progressGradient())
                        )
                    }
                }
            }

            // Title and author
            Column(
                modifier = Modifier.padding(10.dp)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.primary,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.primary.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RecentlyAddedItem(
    title: String,
    subtitle: String,
    type: String,
    onClick: () -> Unit,
    palette: ThemePalette
) {
    // Remember shapes for performance
    val shape10 = cornerRadius(10.dp)
    val shape12 = cornerRadius(12.dp)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "recentItemScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(shape12)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Type badge
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(shape10)
                .background(palette.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (type == "Audiobook")
                    AppIcons.Audiobook
                else
                    AppIcons.Book,
                contentDescription = null,
                tint = palette.primary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = palette.primary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = palette.primary.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }

        // Type label
        Text(
            text = type,
            style = MaterialTheme.typography.labelSmall,
            color = palette.primary.copy(alpha = 0.5f)
        )
    }
}
