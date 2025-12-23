package com.librio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.librio.model.ContentType
import com.librio.model.LibraryBook
import com.librio.navigation.BottomNavItem
import com.librio.navigation.Screen
import com.librio.player.AudiobookPlayer
import com.librio.ui.components.MiniPlayer
import com.librio.ui.components.NowPlaying
import com.librio.ui.screens.EbookReaderScreen
import com.librio.ui.screens.MainScreen
import com.librio.ui.screens.MusicPlayerScreen
import com.librio.ui.screens.PlayerScreen
import com.librio.ui.screens.MoviePlayerScreen
import com.librio.ui.screens.ComicReaderScreen
import com.librio.ui.screens.PlaylistDetailScreen
import com.librio.ui.screens.SeriesDetailScreen
import com.librio.ui.screens.SettingsScreen
import com.librio.ui.screens.SplashScreen
import com.librio.ui.screens.UserProfile
import com.librio.player.applyEqualizerPreset
import com.librio.player.normalizeEqPresetName
import com.librio.ui.theme.AppTheme
import com.librio.ui.theme.AudiobookPlayerTheme
import com.librio.ui.theme.BackgroundTheme
import com.librio.viewmodel.LibraryViewModel
import com.librio.viewmodel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import kotlinx.coroutines.delay
import com.librio.player.PlaybackService
import com.librio.player.SharedMusicPlayer

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            // Could show a message explaining why permissions are needed
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPermissions()

        setContent {
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            val navController = rememberNavController()
            val libraryViewModel: LibraryViewModel = viewModel()
            val settingsViewModel: SettingsViewModel = viewModel()
            // Initialize settings immediately so theme/custom colors are available before first frame
            settingsViewModel.initialize(context)
            val player = remember { AudiobookPlayer(context) }
            var introComplete by remember { mutableStateOf(false) }

            // Initialize library from persistent storage and scan books folder
            LaunchedEffect(Unit) {
                libraryViewModel.initialize(context)
                // Also scan Books folder for e-books
                libraryViewModel.scanBooksFolder(context)
            }

            // Observe settings
            val appTheme by settingsViewModel.appTheme?.collectAsState() ?: remember { mutableStateOf(AppTheme.TEAL) }
            val accentTheme by settingsViewModel.accentTheme?.collectAsState() ?: remember { mutableStateOf(AppTheme.TEAL) }
            val darkMode by settingsViewModel.darkMode?.collectAsState() ?: remember { mutableStateOf(false) }
            val backgroundTheme by settingsViewModel.backgroundTheme?.collectAsState() ?: remember { mutableStateOf(BackgroundTheme.WHITE) }
            val skipForward by settingsViewModel.skipForwardDuration?.collectAsState() ?: remember { mutableStateOf(30) }
            val skipBack by settingsViewModel.skipBackDuration?.collectAsState() ?: remember { mutableStateOf(10) }
            val autoBookmark by settingsViewModel.autoBookmark?.collectAsState() ?: remember { mutableStateOf(true) }
            val keepScreenOn by settingsViewModel.keepScreenOn?.collectAsState() ?: remember { mutableStateOf(false) }
            val volumeBoostEnabled by settingsViewModel.volumeBoostEnabled?.collectAsState() ?: remember { mutableStateOf(false) }
            val volumeBoostLevel by settingsViewModel.volumeBoostLevel?.collectAsState() ?: remember { mutableStateOf(1.5f) }
            val libraryOwnerName by settingsViewModel.libraryOwnerName?.collectAsState() ?: remember { mutableStateOf("") }
            val playbackSpeed by settingsViewModel.playbackSpeed?.collectAsState() ?: remember { mutableStateOf(1.0f) }
            val sleepTimerMinutes by settingsViewModel.sleepTimerMinutes?.collectAsState() ?: remember { mutableStateOf(0) }
            val autoPlayNext by settingsViewModel.autoPlayNext?.collectAsState() ?: remember { mutableStateOf(true) }
            val defaultLibraryView by settingsViewModel.defaultLibraryView?.collectAsState() ?: remember { mutableStateOf("GRID") }
            val defaultSortOrder by settingsViewModel.defaultSortOrder?.collectAsState() ?: remember { mutableStateOf("TITLE") }
            val resumePlayback by settingsViewModel.resumePlayback?.collectAsState() ?: remember { mutableStateOf(true) }
            val showPlaybackNotification by settingsViewModel.showPlaybackNotification?.collectAsState() ?: remember { mutableStateOf(true) }
            val profiles by settingsViewModel.profiles?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
            val appScale by settingsViewModel.appScale?.collectAsState() ?: remember { mutableStateOf(1.0f) }
            val uiFontScale by settingsViewModel.uiFontScale?.collectAsState() ?: remember { mutableStateOf(1.0f) }
            val uiFontFamilyName by settingsViewModel.uiFontFamily?.collectAsState() ?: remember { mutableStateOf("Default") }
            val lastMusicId by settingsViewModel.lastMusicId?.collectAsState() ?: remember { mutableStateOf<String?>(null) }
            val lastMusicPosition by settingsViewModel.lastMusicPosition?.collectAsState() ?: remember { mutableStateOf(0L) }
            val lastMusicPlaying by settingsViewModel.lastMusicPlaying?.collectAsState() ?: remember { mutableStateOf(false) }
            val lastAudiobookId by settingsViewModel.lastAudiobookId?.collectAsState() ?: remember { mutableStateOf<String?>(null) }
            val lastAudiobookPosition by settingsViewModel.lastAudiobookPosition?.collectAsState() ?: remember { mutableStateOf(0L) }
            val lastAudiobookPlaying by settingsViewModel.lastAudiobookPlaying?.collectAsState() ?: remember { mutableStateOf(false) }
            val lastActiveType by settingsViewModel.lastActiveType?.collectAsState() ?: remember { mutableStateOf<String?>(null) }
            val musicShuffleEnabled by settingsViewModel.musicShuffleEnabled?.collectAsState() ?: remember { mutableStateOf(false) }
            val musicRepeatMode by settingsViewModel.musicRepeatMode?.collectAsState() ?: remember { mutableStateOf(0) }

            // Get active profile for audio settings
            val activeProfile = profiles.find { it.isActive }
            val profilePlaybackSpeed = activeProfile?.playbackSpeed ?: 1.0f
            val profileSkipForward = activeProfile?.skipForwardDuration ?: 30
            val profileSkipBack = activeProfile?.skipBackDuration ?: 10
            val profileVolumeBoost = activeProfile?.volumeBoostEnabled ?: false
            val profileVolumeBoostLevel = activeProfile?.volumeBoostLevel ?: 1.0f
            val profileNormalizeAudio = activeProfile?.normalizeAudio ?: false
            val profileBassBoost = activeProfile?.bassBoostLevel ?: 0f
            val profileEqualizer = activeProfile?.equalizerPreset ?: "DEFAULT"
            val profileSleepTimer = activeProfile?.sleepTimerMinutes ?: 0
            val profileHeaderTitle = formatProfileHeader(activeProfile?.name)

            // New settings
            val normalizeAudio by settingsViewModel.normalizeAudio?.collectAsState() ?: remember { mutableStateOf(false) }
            val bassBoostLevel by settingsViewModel.bassBoostLevel?.collectAsState() ?: remember { mutableStateOf(0) }
            val equalizerPreset by settingsViewModel.equalizerPreset?.collectAsState() ?: remember { mutableStateOf("DEFAULT") }
            val crossfadeDuration by settingsViewModel.crossfadeDuration?.collectAsState() ?: remember { mutableStateOf(0) }
            val autoRewindSeconds by settingsViewModel.autoRewindSeconds?.collectAsState() ?: remember { mutableStateOf(0) }
            val headsetControls by settingsViewModel.headsetControls?.collectAsState() ?: remember { mutableStateOf(true) }
            val pauseOnDisconnect by settingsViewModel.pauseOnDisconnect?.collectAsState() ?: remember { mutableStateOf(true) }
            val showPlaceholderIcons by settingsViewModel.showPlaceholderIcons?.collectAsState() ?: remember { mutableStateOf(true) }
            val showFileSize by settingsViewModel.showFileSize?.collectAsState() ?: remember { mutableStateOf(true) }
            val showDuration by settingsViewModel.showDuration?.collectAsState() ?: remember { mutableStateOf(true) }
            val animationSpeed by settingsViewModel.animationSpeed?.collectAsState() ?: remember { mutableStateOf("Normal") }
            val hapticFeedback by settingsViewModel.hapticFeedback?.collectAsState() ?: remember { mutableStateOf(true) }
            val confirmBeforeDelete by settingsViewModel.confirmBeforeDelete?.collectAsState() ?: remember { mutableStateOf(true) }
            val useSquareCorners by settingsViewModel.useSquareCorners?.collectAsState() ?: remember { mutableStateOf(false) }
            val rememberLastPosition by settingsViewModel.rememberLastPosition?.collectAsState() ?: remember { mutableStateOf(true) }
            val showBackButton by settingsViewModel.showBackButton?.collectAsState() ?: remember { mutableStateOf(true) }
            val showSearchBar by settingsViewModel.showSearchBar?.collectAsState() ?: remember { mutableStateOf(true) }
            val customPrimaryColor by settingsViewModel.customPrimaryColor?.collectAsState() ?: remember { mutableStateOf(0x00897B) }
            val customAccentColor by settingsViewModel.customAccentColor?.collectAsState() ?: remember { mutableStateOf(0x26A69A) }
            val customBackgroundColor by settingsViewModel.customBackgroundColor?.collectAsState() ?: remember { mutableStateOf(0x121212) }

            // E-Reader settings
            val readerFontSize by settingsViewModel.readerFontSize?.collectAsState() ?: remember { mutableStateOf(18) }
            val readerLineSpacing by settingsViewModel.readerLineSpacing?.collectAsState() ?: remember { mutableStateOf(1.4f) }
            val readerFont by settingsViewModel.readerFont?.collectAsState() ?: remember { mutableStateOf("serif") }
            val readerTextAlign by settingsViewModel.readerTextAlign?.collectAsState() ?: remember { mutableStateOf("left") }
            val readerMargins by settingsViewModel.readerMargins?.collectAsState() ?: remember { mutableStateOf(16) }
            val readerParagraphSpacing by settingsViewModel.readerParagraphSpacing?.collectAsState() ?: remember { mutableStateOf(12) }
            val readerBrightness by settingsViewModel.readerBrightness?.collectAsState() ?: remember { mutableStateOf(1f) }
            val readerBoldText by settingsViewModel.readerBoldText?.collectAsState() ?: remember { mutableStateOf(false) }
            val readerWordSpacing by settingsViewModel.readerWordSpacing?.collectAsState() ?: remember { mutableStateOf(0) }
            val readerTheme by settingsViewModel.readerTheme?.collectAsState() ?: remember { mutableStateOf("light") }
            val readerPageFitMode by settingsViewModel.readerPageFitMode?.collectAsState() ?: remember { mutableStateOf("fit") }
            val readerPageGap by settingsViewModel.readerPageGap?.collectAsState() ?: remember { mutableStateOf(4) }
            val readerForceTwoPage by settingsViewModel.readerForceTwoPage?.collectAsState() ?: remember { mutableStateOf(false) }
            val readerForceSinglePage by settingsViewModel.readerForceSinglePage?.collectAsState() ?: remember { mutableStateOf(false) }

            // Comic reader settings
            val comicForceTwoPage by settingsViewModel.comicForceTwoPage?.collectAsState() ?: remember { mutableStateOf(false) }
            val comicForceSinglePage by settingsViewModel.comicForceSinglePage?.collectAsState() ?: remember { mutableStateOf(false) }
            val comicReadingDirection by settingsViewModel.comicReadingDirection?.collectAsState() ?: remember { mutableStateOf("ltr") }
            val comicPageFitMode by settingsViewModel.comicPageFitMode?.collectAsState() ?: remember { mutableStateOf("fit") }
            val comicPageGap by settingsViewModel.comicPageGap?.collectAsState() ?: remember { mutableStateOf(4) }
            val comicBackgroundColor by settingsViewModel.comicBackgroundColor?.collectAsState() ?: remember { mutableStateOf("theme") }
            val comicShowPageIndicators by settingsViewModel.comicShowPageIndicators?.collectAsState() ?: remember { mutableStateOf(true) }
            val comicEnableDoubleTapZoom by settingsViewModel.comicEnableDoubleTapZoom?.collectAsState() ?: remember { mutableStateOf(true) }
            val comicShowControlsOnTap by settingsViewModel.comicShowControlsOnTap?.collectAsState() ?: remember { mutableStateOf(true) }

            // Clean up player when activity is destroyed
            DisposableEffect(Unit) {
                onDispose {
                    libraryViewModel.saveLibrary()
                    player.release()
                }
            }

            // Apply profile audio effects to audiobook player
            LaunchedEffect(profileNormalizeAudio, profileBassBoost, profileVolumeBoost, profileVolumeBoostLevel, profileEqualizer) {
                player.setNormalizeAudio(profileNormalizeAudio)
                player.setBassBoostLevel(profileBassBoost)
                player.setVolumeBoost(profileVolumeBoost, profileVolumeBoostLevel)
                player.setEqualizerPreset(profileEqualizer)
            }

            // Observe library state
            val libraryState by libraryViewModel.libraryState.collectAsState()
            val selectedAudiobook by libraryViewModel.selectedAudiobook.collectAsState()
            val selectedBook by libraryViewModel.selectedBook.collectAsState()
            val selectedMusic by libraryViewModel.selectedMusic.collectAsState()
            val selectedMovie by libraryViewModel.selectedMovie.collectAsState()
            val selectedComic by libraryViewModel.selectedComic.collectAsState()
            val searchQuery by libraryViewModel.searchQuery.collectAsState()
            val isSearchVisible by libraryViewModel.isSearchVisible.collectAsState()

            // Mini player state - tracks what was last played
            var lastPlayedMusic by remember { mutableStateOf<com.librio.model.LibraryMusic?>(null) }
            var lastPlayedAudiobook by remember { mutableStateOf<com.librio.model.LibraryAudiobook?>(null) }
            var isMusicPlaying by remember { mutableStateOf(false) }
            var isAudiobookPlaying by remember { mutableStateOf(false) }
            var musicPlaylistIndex by remember { mutableStateOf(0) }
            var musicCurrentPosition by remember { mutableStateOf(0L) }
            // Store the playlist when playback starts - used for skip navigation
            var currentMusicPlaylist by remember { mutableStateOf<List<com.librio.model.LibraryMusic>>(emptyList()) }

            // Shared music ExoPlayer - persists across screen navigation and background playback
            val musicExoPlayer = remember { SharedMusicPlayer.acquire(context) }
            val startPlaybackService = remember {
                {
                    PlaybackService.start(context.applicationContext)
                }
            }

            // Apply profile audio effects to music player (external ExoPlayer)
            var musicLoudnessEnhancer by remember { mutableStateOf<android.media.audiofx.LoudnessEnhancer?>(null) }
            var musicBassBoost by remember { mutableStateOf<android.media.audiofx.BassBoost?>(null) }
            var musicEqualizer by remember { mutableStateOf<android.media.audiofx.Equalizer?>(null) }
            var musicAudioSessionId by remember { mutableIntStateOf(androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) }

            LaunchedEffect(profileNormalizeAudio, profileBassBoost, profileVolumeBoost, profileVolumeBoostLevel, profileEqualizer, musicExoPlayer.audioSessionId) {
                val audioSessionId = musicExoPlayer.audioSessionId
                if (audioSessionId == androidx.media3.common.C.AUDIO_SESSION_ID_UNSET || audioSessionId == 0) return@LaunchedEffect

                if (audioSessionId != musicAudioSessionId) {
                    musicAudioSessionId = audioSessionId
                    // Release existing effects
                    try { musicLoudnessEnhancer?.release() } catch (_: Exception) { }
                    try { musicBassBoost?.release() } catch (_: Exception) { }
                    try { musicEqualizer?.release() } catch (_: Exception) { }

                    // Create new audio effects for this session
                    musicLoudnessEnhancer = runCatching { android.media.audiofx.LoudnessEnhancer(audioSessionId) }.getOrNull()
                    musicBassBoost = runCatching { android.media.audiofx.BassBoost(0, audioSessionId) }.getOrNull()
                    musicEqualizer = runCatching { android.media.audiofx.Equalizer(0, audioSessionId) }.getOrNull()
                }

                val normalizedPreset = normalizeEqPresetName(profileEqualizer)

                // Apply loudness (volume boost or normalization)
                musicLoudnessEnhancer?.let { enhancer ->
                    runCatching {
                        val baseGain = when {
                            profileVolumeBoost -> ((profileVolumeBoostLevel - 1f) * 1500).toInt().coerceAtLeast(0)
                            profileNormalizeAudio -> 0
                            else -> 0
                        }
                        val gainMb = baseGain.coerceIn(0, 2000)
                        enhancer.setTargetGain(gainMb)
                        enhancer.enabled = profileVolumeBoost || profileNormalizeAudio
                    }
                }

                // Apply bass boost (avoid stacking with bass EQ preset)
                musicBassBoost?.let { boost ->
                    runCatching {
                        val shouldApply = profileBassBoost > 0f && normalizedPreset != "BASS_INCREASED"
                        val strength = if (shouldApply) {
                            (profileBassBoost * 700).toInt().coerceIn(0, 700)
                        } else {
                            0
                        }
                        boost.setStrength(strength.toShort())
                        boost.enabled = strength > 0
                    }
                }

                // Apply equalizer preset
                musicEqualizer?.let { eq ->
                    runCatching {
                        applyEqualizerPreset(eq, normalizedPreset)
                    }
                }
            }

            // Clean up audio effects when composable is disposed
            DisposableEffect(Unit) {
                onDispose {
                    try { musicLoudnessEnhancer?.release() } catch (_: Exception) { }
                    try { musicBassBoost?.release() } catch (_: Exception) { }
                    try { musicEqualizer?.release() } catch (_: Exception) { }
                }
            }

            // Sync music player shuffle and repeat mode with persisted settings
            LaunchedEffect(musicShuffleEnabled, musicRepeatMode) {
                musicExoPlayer.shuffleModeEnabled = musicShuffleEnabled
                musicExoPlayer.repeatMode = musicRepeatMode
            }

            fun persistAudiobookStateOnBackground() {
                val currentUri = player.currentAudiobook.value?.uri
                    ?: musicExoPlayer.currentMediaItem?.localConfiguration?.uri
                val playbackSnapshot = player.playbackState.value
                if (lastAudiobookId == null && lastPlayedAudiobook == null && !isAudiobookPlaying && !playbackSnapshot.isPlaying) {
                    return
                }
                val book = selectedAudiobook
                    ?: lastPlayedAudiobook
                    ?: currentUri?.let { uri -> libraryState.audiobooks.find { it.uri == uri } }
                    ?: return
                val audiobookActive = playbackSnapshot.isPlaying || isAudiobookPlaying
                if (isMusicPlaying && !audiobookActive) return
                val position = playbackSnapshot.currentPosition.takeIf { it > 0L } ?: book.lastPosition
                val duration = if (playbackSnapshot.duration > 0) playbackSnapshot.duration else book.duration
                libraryViewModel.updateProgress(book.id, position, duration)
                lastPlayedAudiobook = book.copy(lastPosition = position, duration = duration)
                settingsViewModel.setLastAudiobookState(book.id, position, audiobookActive)
                if (!isMusicPlaying) {
                    settingsViewModel.setLastActiveType("AUDIOBOOK")
                }
            }

            // Save library and settings, manage playback when lifecycle changes
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> {
                            // Reload settings from JSON files when resuming
                            // This allows external edits to JSON files to take effect
                            settingsViewModel.reloadSettingsFromFiles()
                        }
                        Lifecycle.Event.ON_PAUSE -> {
                            persistAudiobookStateOnBackground()
                            libraryViewModel.saveLibrary()
                            settingsViewModel.saveAllSettingsToFiles()
                        }
                        Lifecycle.Event.ON_STOP -> {
                            persistAudiobookStateOnBackground()
                            libraryViewModel.saveLibrary()
                            settingsViewModel.saveAllSettingsToFiles()
                        }
                        Lifecycle.Event.ON_DESTROY -> {
                            // Save all state before destroying (don't pause players to preserve playing state)
                            persistAudiobookStateOnBackground()
                            libraryViewModel.saveLibrary()
                            settingsViewModel.saveAllSettingsToFiles()
                            // Don't pause players here - pausing triggers onIsPlayingChanged which overwrites saved state
                            // The system will clean up resources automatically
                            try {
                                stopService(Intent(this@MainActivity, PlaybackService::class.java))
                            } catch (_: Exception) {
                            }
                        }
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            fun buildMusicMediaItem(music: com.librio.model.LibraryMusic): MediaItem {
                return MediaItem.Builder()
                    .setUri(music.uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(music.title)
                            .setArtist(music.artist)
                            .setAlbumTitle(music.album ?: "")
                            .build()
                    )
                    .build()
            }

            // Per-content-type sort options
            val audiobookSortOption by libraryViewModel.audiobookSortOption.collectAsState()
            val bookSortOption by libraryViewModel.bookSortOption.collectAsState()
            val musicSortOption by libraryViewModel.musicSortOption.collectAsState()
            val comicSortOption by libraryViewModel.comicSortOption.collectAsState()
            val movieSortOption by libraryViewModel.movieSortOption.collectAsState()

            // Load persisted filter state
            val savedContentType by settingsViewModel.selectedContentType?.collectAsState() ?: remember { mutableStateOf("AUDIOBOOK") }
            val savedCategoryId by settingsViewModel.selectedCategoryId?.collectAsState() ?: remember { mutableStateOf<String?>(null) }
            val collapsedSeries by settingsViewModel.collapsedSeries?.collectAsState() ?: remember { mutableStateOf(emptySet<String>()) }

            // Restore filter state on startup
            LaunchedEffect(Unit) {
                try {
                    val contentType = ContentType.valueOf(savedContentType)
                    libraryViewModel.setContentType(contentType)
                } catch (e: Exception) {
                    // Default to AUDIOBOOK if invalid
                }
                savedCategoryId?.let { categoryId ->
                    libraryViewModel.selectCategory(categoryId)
                }
            }

            // Get filtered audiobooks (also depends on selectedCategoryId)
            val filteredAudiobooks = remember(libraryState.audiobooks, searchQuery, audiobookSortOption, libraryState.selectedAudiobookCategoryId) {
                libraryViewModel.getFilteredAudiobooks()
            }

            // Get filtered books
            val filteredBooks = remember(libraryState.books, searchQuery, bookSortOption, libraryState.selectedBookCategoryId) {
                libraryViewModel.getFilteredBooks()
            }

            // Get filtered music
            val filteredMusic = remember(libraryState.music, searchQuery, musicSortOption, libraryState.selectedMusicCategoryId, libraryState.selectedCreepypastaCategoryId, libraryState.selectedContentType) {
                libraryViewModel.getFilteredMusic()
            }

            // Get filtered comics
            val filteredComics = remember(libraryState.comics, searchQuery, comicSortOption, libraryState.selectedComicCategoryId) {
                libraryViewModel.getFilteredComics()
            }

            // Get filtered movies
            val filteredMovies = remember(libraryState.movies, searchQuery, movieSortOption, libraryState.selectedMovieCategoryId) {
                libraryViewModel.getFilteredMovies()
            }

            // Keep playlist snapshot for playback transitions
            LaunchedEffect(filteredMusic, selectedMusic) {
                if (currentMusicPlaylist.isEmpty() && filteredMusic.isNotEmpty()) {
                    currentMusicPlaylist = filteredMusic
                }
            }

            // Restore last played music from persisted state (only when music was last active)
            LaunchedEffect(libraryState.music, lastMusicId, introComplete, lastActiveType) {
                if (!introComplete) return@LaunchedEffect
                // Use lastActiveType for priority if available, otherwise fall back to playing state
                val shouldRestoreMusic = lastMusicId != null && (
                    lastActiveType == "MUSIC" ||
                    (lastActiveType == null && (lastMusicPlaying || (!lastAudiobookPlaying && lastAudiobookId == null)))
                )
                if (!shouldRestoreMusic) return@LaunchedEffect
                if (lastPlayedMusic == null && lastMusicId != null) {
                    val music = libraryState.music.find { it.id == lastMusicId }
                    music?.let {
                        lastPlayedMusic = it
                        lastPlayedAudiobook = null
                        // Filter playlist to only include items of the same content type (MUSIC or CREEPYPASTA)
                        currentMusicPlaylist = libraryState.music.filter { musicItem ->
                            musicItem.contentType == it.contentType
                        }
                        musicCurrentPosition = lastMusicPosition
                        isMusicPlaying = lastMusicPlaying
                        val activeType = if (it.contentType == ContentType.CREEPYPASTA) "CREEPYPASTA" else "MUSIC"
                        settingsViewModel.setLastActiveType(activeType)
                        musicExoPlayer.setMediaItem(buildMusicMediaItem(it))
                        musicExoPlayer.prepare()
                        musicExoPlayer.seekTo(lastMusicPosition)
                        musicExoPlayer.playWhenReady = lastMusicPlaying
                        if (lastMusicPlaying) {
                            startPlaybackService()
                        } else {
                            musicExoPlayer.pause()
                        }
                    }
                }
            }

            // Restore last played audiobook from persisted state (only when audiobook was last active)
            LaunchedEffect(libraryState.audiobooks, lastAudiobookId, introComplete, lastActiveType) {
                if (!introComplete) return@LaunchedEffect
                // Use lastActiveType for priority if available, otherwise fall back to playing state
                val shouldRestoreBook = lastAudiobookId != null && (
                    lastActiveType == "AUDIOBOOK" ||
                    (lastActiveType == null && !lastMusicPlaying)
                )
                if (!shouldRestoreBook) return@LaunchedEffect
                if (lastPlayedAudiobook == null && lastAudiobookId != null) {
                    val audiobook = libraryState.audiobooks.find { it.id == lastAudiobookId }
                    audiobook?.let { book ->
                        lastPlayedAudiobook = book.copy(lastPosition = lastAudiobookPosition)
                        lastPlayedMusic = null
                        isAudiobookPlaying = lastAudiobookPlaying
                        settingsViewModel.setLastActiveType("AUDIOBOOK")
                        CoroutineScope(Dispatchers.Main).launch {
                            player.loadAudiobook(book.uri, book.title, book.author)
                            player.seekTo(lastAudiobookPosition)
                            if (lastAudiobookPlaying) {
                                player.play()
                            } else {
                                player.pause()
                            }
                        }
                    }
                }
            }

            LaunchedEffect(libraryState.audiobooks, introComplete) {
                if (!introComplete) return@LaunchedEffect
                val playbackSnapshot = player.playbackState.value
                if (lastAudiobookId == null && lastPlayedAudiobook == null && !isAudiobookPlaying && !playbackSnapshot.isPlaying) {
                    return@LaunchedEffect
                }
                if (lastPlayedAudiobook != null) return@LaunchedEffect
                val currentUri = musicExoPlayer.currentMediaItem?.localConfiguration?.uri ?: return@LaunchedEffect
                val match = libraryState.audiobooks.find { it.uri == currentUri } ?: return@LaunchedEffect
                val position = musicExoPlayer.currentPosition
                val duration = if (musicExoPlayer.duration > 0) musicExoPlayer.duration else match.duration
                lastPlayedAudiobook = match.copy(lastPosition = position, duration = duration)
                isAudiobookPlaying = musicExoPlayer.isPlaying
                settingsViewModel.setLastAudiobookState(match.id, position, musicExoPlayer.isPlaying)
                if (musicExoPlayer.isPlaying) {
                    settingsViewModel.setLastActiveType("AUDIOBOOK")
                }
            }

            val latestFilteredMusic by rememberUpdatedState(filteredMusic)
            val latestCurrentMusicPlaylist by rememberUpdatedState(currentMusicPlaylist)
            val latestLastPlayedMusic by rememberUpdatedState(lastPlayedMusic)
            val latestSelectedAudiobook by rememberUpdatedState(selectedAudiobook)
            val latestLastPlayedAudiobook by rememberUpdatedState(lastPlayedAudiobook)

            fun isCurrentMediaMusic(currentUri: Uri?): Boolean {
                if (currentUri == null) return false
                val lastMusicUri = latestLastPlayedMusic?.uri
                if (lastMusicUri != null && lastMusicUri == currentUri) return true
                if (latestCurrentMusicPlaylist.any { it.uri == currentUri }) return true
                return latestFilteredMusic.any { it.uri == currentUri }
            }

            fun isCurrentMediaAudiobook(currentUri: Uri?): Boolean {
                if (currentUri == null) return false
                return latestSelectedAudiobook?.uri == currentUri ||
                    latestLastPlayedAudiobook?.uri == currentUri
            }

            // Track music player state - must be after filteredMusic is defined
            DisposableEffect(musicExoPlayer) {
                val listener = object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        val currentUri = musicExoPlayer.currentMediaItem?.localConfiguration?.uri
                        if (isCurrentMediaAudiobook(currentUri)) {
                            if (isMusicPlaying) {
                                isMusicPlaying = false
                            }
                            return
                        }
                        if (!isCurrentMediaMusic(currentUri)) return
                        isMusicPlaying = playing
                        if (playing) {
                            startPlaybackService()
                        }
                        settingsViewModel.setLastMusicState(lastPlayedMusic?.id, musicExoPlayer.currentPosition, playing)
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val currentUri = musicExoPlayer.currentMediaItem?.localConfiguration?.uri
                        if (!isCurrentMediaMusic(currentUri)) return
                        if (playbackState == Player.STATE_ENDED) {
                            // Auto-play next track if available
                            val playlist = if (currentMusicPlaylist.isNotEmpty()) currentMusicPlaylist else filteredMusic
                            val currentIdx = playlist.indexOfFirst { it.id == lastPlayedMusic?.id }

                            if (playlist.isEmpty() || currentIdx == -1) return

                            // Repeat-one handled manually since we manage the queue ourselves
                            if (musicExoPlayer.repeatMode == Player.REPEAT_MODE_ONE) {
                                musicExoPlayer.seekTo(0)
                                musicExoPlayer.play()
                                return
                            }

                            val nextIndex = when {
                                musicExoPlayer.shuffleModeEnabled && playlist.size > 1 -> {
                                    val available = playlist.indices.filter { it != currentIdx }
                                    available.randomOrNull() ?: currentIdx
                                }
                                currentIdx < playlist.size - 1 -> currentIdx + 1
                                musicExoPlayer.repeatMode == Player.REPEAT_MODE_ALL && playlist.isNotEmpty() -> 0
                                else -> null
                            }

                            nextIndex?.let { idx ->
                                val nextTrack = playlist[idx]
                                lastPlayedMusic = nextTrack
                                settingsViewModel.setLastActiveType("MUSIC")
                                libraryViewModel.selectMusic(nextTrack)
                                libraryViewModel.incrementMusicPlayCount(nextTrack.id)
                                musicCurrentPosition = 0L
                                musicPlaylistIndex = idx
                                musicExoPlayer.setMediaItem(buildMusicMediaItem(nextTrack))
                                musicExoPlayer.prepare()
                                musicExoPlayer.seekTo(0)
                                musicExoPlayer.play()
                                startPlaybackService()
                            }
                        }
                        settingsViewModel.setLastMusicState(lastPlayedMusic?.id, musicExoPlayer.currentPosition, isMusicPlaying)
                    }
                }
                musicExoPlayer.addListener(listener)
                onDispose {
                    musicExoPlayer.removeListener(listener)
                    SharedMusicPlayer.release()
                }
            }

            // Update current position periodically for mini player
            LaunchedEffect(isMusicPlaying) {
                while (isMusicPlaying) {
                    musicCurrentPosition = musicExoPlayer.currentPosition
                    delay(500)
                }
            }

            // Persist music playback position frequently for mini player restore
            LaunchedEffect(musicCurrentPosition, isMusicPlaying, lastPlayedMusic) {
                lastPlayedMusic?.let { music ->
                    settingsViewModel.setLastMusicState(music.id, musicCurrentPosition, isMusicPlaying)
                }
            }

            val playbackState by player.playbackState.collectAsState()

            // Helper to build audiobook NowPlaying
            fun buildAudiobookNowPlaying(audiobook: com.librio.model.LibraryAudiobook): NowPlaying.Audiobook {
                return NowPlaying.Audiobook(
                    id = audiobook.id,
                    title = audiobook.title,
                    subtitle = audiobook.author,
                    coverArt = audiobook.coverArt,
                    duration = if (playbackState.duration > 0) playbackState.duration else audiobook.duration,
                    currentPosition = playbackState.currentPosition,
                    isPlaying = playbackState.isPlaying || isAudiobookPlaying,
                    chapterInfo = null
                )
            }

            // Helper to build music NowPlaying
            fun buildMusicNowPlaying(music: com.librio.model.LibraryMusic): NowPlaying.Music {
                val currentIndex = filteredMusic.indexOfFirst { it.id == music.id }
                // If not found in filtered list (-1), disable next/previous navigation
                val validIndex = currentIndex >= 0
                return NowPlaying.Music(
                    id = music.id,
                    title = music.title,
                    subtitle = music.artist,
                    coverArt = music.coverArt,
                    duration = if (musicExoPlayer.duration > 0) musicExoPlayer.duration else music.duration,
                    currentPosition = musicCurrentPosition,
                    isPlaying = isMusicPlaying,
                    hasNext = validIndex && currentIndex < filteredMusic.size - 1,
                    hasPrevious = validIndex && currentIndex > 0
                )
            }

            // Build NowPlaying object - prioritize by lastActiveType, then playing state
            val nowPlaying: NowPlaying? = when {
                // Priority 1: Use lastActiveType to determine which player to show
                lastActiveType == "AUDIOBOOK" && lastPlayedAudiobook != null -> {
                    buildAudiobookNowPlaying(lastPlayedAudiobook!!)
                }
                lastActiveType == "MUSIC" && lastPlayedMusic != null -> {
                    buildMusicNowPlaying(lastPlayedMusic!!)
                }
                // Priority 2: If audiobook is actively playing (service running)
                (isAudiobookPlaying || playbackState.isPlaying) && lastPlayedAudiobook != null -> {
                    buildAudiobookNowPlaying(lastPlayedAudiobook!!)
                }
                // Priority 3: If music is actively playing
                isMusicPlaying && lastPlayedMusic != null -> {
                    buildMusicNowPlaying(lastPlayedMusic!!)
                }
                // Priority 4: Show paused music if available
                lastPlayedMusic != null -> {
                    buildMusicNowPlaying(lastPlayedMusic!!)
                }
                // Priority 5: Show paused audiobook if available
                lastPlayedAudiobook != null -> {
                    buildAudiobookNowPlaying(lastPlayedAudiobook!!)
                }
                else -> null
            }

            // Get the current sort option based on content type
            val currentSortOption = when (libraryState.selectedContentType) {
                ContentType.AUDIOBOOK -> audiobookSortOption
                ContentType.EBOOK -> bookSortOption
                ContentType.MUSIC -> musicSortOption
                ContentType.CREEPYPASTA -> musicSortOption
                ContentType.COMICS -> comicSortOption
                ContentType.MOVIE -> movieSortOption
            }

            // Track playback state to update library progress
            LaunchedEffect(playbackState.currentPosition, selectedAudiobook) {
                selectedAudiobook?.let { audiobook ->
                    if (playbackState.duration > 0) {
                        // Only update progress when the playback duration is for the current audiobook
                        if (audiobook.uri == player.currentAudiobook.value?.uri) {
                            libraryViewModel.updateProgress(
                                audiobook.id,
                                playbackState.currentPosition,
                                playbackState.duration
                            )
                        }
                    } else if (audiobook.uri == player.currentAudiobook.value?.uri && playbackState.currentPosition > 0) {
                        // Fallback: update when duration not yet known but we have position
                        libraryViewModel.updateProgress(
                            audiobook.id,
                            playbackState.currentPosition,
                            audiobook.duration
                        )
                    }
                }
            }

            // Keep mini player audiobook position in sync
            LaunchedEffect(playbackState.currentPosition, playbackState.duration) {
                lastPlayedAudiobook?.let { book ->
                    lastPlayedAudiobook = book.copy(
                        lastPosition = playbackState.currentPosition,
                        duration = if (playbackState.duration > 0) playbackState.duration else book.duration
                    )
                }
            }

            val baseDensity = LocalDensity.current
            val uiScale = appScale.coerceIn(0.85f, 1.3f)
            val fontScale = uiFontScale.coerceIn(0.85f, 1.3f)
            val selectedFontFamily = when (uiFontFamilyName.lowercase()) {
                "serif" -> FontFamily.Serif
                "monospace" -> FontFamily.Monospace
                "sans" -> FontFamily.SansSerif
                "default" -> FontFamily.Default
                else -> FontFamily.Default
            }

            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = baseDensity.density * uiScale,
                    fontScale = baseDensity.fontScale * uiScale * fontScale
                )
            ) {
                AudiobookPlayerTheme(
                    appTheme = appTheme,
                    accentTheme = accentTheme,
                    darkTheme = darkMode,
                    customPrimaryColor = customPrimaryColor,
                    useSquareCorners = useSquareCorners,
                    fontFamily = selectedFontFamily
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Splash.route,
                            enterTransition = {
                                fadeIn(animationSpec = tween(300)) +
                                        slideInHorizontally(
                                            initialOffsetX = { it },
                                            animationSpec = tween(400)
                                        )
                            },
                            exitTransition = {
                                fadeOut(animationSpec = tween(300)) +
                                        slideOutHorizontally(
                                            targetOffsetX = { -it / 3 },
                                            animationSpec = tween(400)
                                        )
                            },
                            popEnterTransition = {
                                fadeIn(animationSpec = tween(300)) +
                                        slideInHorizontally(
                                            initialOffsetX = { -it / 3 },
                                            animationSpec = tween(400)
                                        )
                            },
                            popExitTransition = {
                                fadeOut(animationSpec = tween(300)) +
                                        slideOutHorizontally(
                                            targetOffsetX = { it },
                                            animationSpec = tween(400)
                                        )
                            }
                        ) {
                        // Splash screen
                        composable(Screen.Splash.route) {
                            SplashScreen(
                                onSplashComplete = {
                                    introComplete = true
                                    navController.navigate(Screen.Main.createRoute("library")) {
                                        popUpTo(Screen.Splash.route) { inclusive = true }
                                    }
                                }
                            )
                        }

                        // Main screen with bottom navigation
                        composable(
                            route = Screen.Main.route,
                            arguments = listOf(
                                androidx.navigation.navArgument("initialTab") {
                                    type = androidx.navigation.NavType.StringType
                                    defaultValue = "library"
                                }
                            )
                        ) { backStackEntry ->
                            val initialTabArg = backStackEntry.arguments?.getString("initialTab") ?: "library"
                            val initialTab = when (initialTabArg.lowercase()) {
                                "profile" -> BottomNavItem.PROFILE
                                "settings" -> BottomNavItem.SETTINGS
                                else -> BottomNavItem.LIBRARY
                            }
                            MainScreen(
                                initialTab = initialTab,
                                audiobooks = filteredAudiobooks,
                                books = filteredBooks,
                                music = filteredMusic,
                                comics = filteredComics,
                                movies = filteredMovies,
                                selectedContentType = libraryState.selectedContentType,
                                onContentTypeChange = { contentType ->
                                    libraryViewModel.setContentType(contentType)
                                    settingsViewModel.setSelectedContentType(contentType.name)
                                },
                                onAddAudiobook = { uri ->
                                    libraryViewModel.addAudiobook(context, uri)
                                },
                                onAddBook = { uri ->
                                    libraryViewModel.addBook(context, uri)
                                },
                                onAddMusic = { uri ->
                                    libraryViewModel.addMusic(context, uri)
                                },
                                onAddComic = { uri ->
                                    libraryViewModel.addComic(context, uri)
                                },
                                onAddMovie = { uri ->
                                    libraryViewModel.addMovie(context, uri)
                                },
                                onSelectAudiobook = { audiobook ->
                                    // Stop music if playing and start audiobook
                                    if (isMusicPlaying) {
                                        isMusicPlaying = false
                                        musicExoPlayer.pause()
                                    }
                                    lastPlayedMusic = null
                                    lastPlayedAudiobook = audiobook
                                    isAudiobookPlaying = true
                                    settingsViewModel.setLastActiveType("AUDIOBOOK")
                                    libraryViewModel.selectAudiobook(audiobook)
                                    CoroutineScope(Dispatchers.Main).launch {
                                        player.loadAudiobook(audiobook.uri, audiobook.title, audiobook.author)
                                        if (audiobook.lastPosition > 0) {
                                            player.seekTo(audiobook.lastPosition)
                                        }
                                    }
                                    navController.navigate(Screen.Player.route)
                                },
                                onSelectBook = { book ->
                                    libraryViewModel.selectBook(book)
                                    navController.navigate(Screen.EbookReader.route)
                                },
                                onSelectMusic = { musicItem ->
                                    // Stop audiobook if playing and start music
                                    if (isAudiobookPlaying) {
                                        isAudiobookPlaying = false
                                        // Save audiobook progress before switching players
                                        selectedAudiobook?.let { audiobook ->
                                            val position = playbackState.currentPosition
                                            val duration = if (playbackState.duration > 0) playbackState.duration else audiobook.duration
                                            libraryViewModel.updateProgress(audiobook.id, position, duration)
                                            lastPlayedAudiobook = audiobook.copy(lastPosition = position)
                                        }
                                        player.pause()
                                    }
                                    lastPlayedMusic = musicItem
                                    settingsViewModel.setLastActiveType("MUSIC")
                                    val playlistForSelection = if (musicItem.seriesId != null) {
                                        filteredMusic.filter { it.seriesId == musicItem.seriesId }
                                    } else {
                                        filteredMusic
                                    }
                                    val foundIndex = playlistForSelection.indexOfFirst { it.id == musicItem.id }
                                    musicPlaylistIndex = if (foundIndex >= 0) foundIndex else 0
                                    currentMusicPlaylist = playlistForSelection
                                    libraryViewModel.incrementMusicPlayCount(musicItem.id)
                                    // Start playing on shared player
                                    musicExoPlayer.setMediaItem(buildMusicMediaItem(musicItem))
                                    musicExoPlayer.prepare()
                                    musicExoPlayer.seekTo(0)
                                    musicCurrentPosition = 0L
                                    musicExoPlayer.play()
                                    startPlaybackService()
                                    libraryViewModel.selectMusic(musicItem)
                                    navController.navigate(Screen.MusicPlayer.route)
                                },
                                onSelectComic = { comic ->
                                    libraryViewModel.selectComic(comic)
                                    navController.navigate(Screen.ComicReader.route)
                                },
                                onSelectMovie = { movie ->
                                    // Pause music if playing
                                    if (isMusicPlaying) {
                                        isMusicPlaying = false
                                        musicExoPlayer.pause()
                                    }
                                    // Pause audiobook if playing
                                    if (isAudiobookPlaying) {
                                        isAudiobookPlaying = false
                                        // Save audiobook progress before switching
                                        selectedAudiobook?.let { audiobook ->
                                            val position = playbackState.currentPosition
                                            val duration = if (playbackState.duration > 0) playbackState.duration else audiobook.duration
                                            libraryViewModel.updateProgress(audiobook.id, position, duration)
                                            lastPlayedAudiobook = audiobook.copy(lastPosition = position)
                                        }
                                        player.pause()
                                    }
                                    libraryViewModel.selectMovie(movie)
                                    navController.navigate(Screen.MoviePlayer.route)
                                },
                                onDeleteAudiobook = { audiobook ->
                                    libraryViewModel.removeAudiobook(audiobook)
                                },
                                onDeleteBook = { book ->
                                    libraryViewModel.removeBook(book)
                                },
                                onEditAudiobook = { audiobook, title, author ->
                                    libraryViewModel.updateAudiobookMetadata(audiobook.id, title, author)
                                },
                                onEditBook = { book, title, author ->
                                    libraryViewModel.updateBookMetadata(book.id, title, author)
                                },
                                onEditMusic = { music, title, artist ->
                                    libraryViewModel.updateMusicMetadata(music.id, title, artist)
                                },
                                onDeleteMusic = { music ->
                                    libraryViewModel.removeMusic(music)
                                },
                                onEditComic = { comic, title, author ->
                                    libraryViewModel.updateComicMetadata(comic.id, title, author)
                                },
                                onDeleteComic = { comic ->
                                    libraryViewModel.removeComic(comic)
                                },
                                onEditMovie = { movie, title ->
                                    libraryViewModel.updateMovieMetadata(movie.id, title)
                                },
                                onDeleteMovie = { movie ->
                                    libraryViewModel.removeMovie(movie)
                                },
                                searchQuery = searchQuery,
                                onSearchQueryChange = { libraryViewModel.setSearchQuery(it) },
                                headerTitle = profileHeaderTitle,
                                showBackButton = showBackButton,
                                onShowBackButtonChange = { settingsViewModel.setShowBackButton(it) },
                                showSearchBar = showSearchBar,
                                onShowSearchBarChange = { settingsViewModel.setShowSearchBar(it) },
                                isSearchVisible = isSearchVisible,
                                onToggleSearch = { libraryViewModel.toggleSearch() },
                                sortOption = currentSortOption,
                                onSortOptionChange = { libraryViewModel.setSortOption(it) },
                                categories = libraryState.categories,
                                selectedCategoryId = libraryState.selectedCategoryId,
                                onSelectCategory = { categoryId ->
                                    libraryViewModel.selectCategory(categoryId)
                                    settingsViewModel.setSelectedCategoryId(categoryId)
                                },
                                onAddCategory = { libraryViewModel.addCategory(it) },
                                onDeleteCategory = { libraryViewModel.deleteCategory(it) },
                                onRenameCategory = { id, name -> libraryViewModel.renameCategory(id, name) },
                                onSetAudiobookCategory = { audiobookId, categoryId ->
                                    libraryViewModel.setAudiobookCategory(audiobookId, categoryId)
                                },
                                // Series management
                                seriesList = libraryState.series,
                                onAddSeries = { name -> libraryViewModel.addSeries(name) },
                                onDeleteSeries = { id ->
                                    libraryViewModel.deleteSeries(id)
                                    // Drop any stale collapsed entry for the deleted series/playlist
                                    settingsViewModel.setCollapsedSeries(collapsedSeries - id)
                                },
                                onRenameSeries = { id, name -> libraryViewModel.renameSeries(id, name) },
                                onSetAudiobookSeries = { audiobookId, seriesId ->
                                    libraryViewModel.setAudiobookSeries(audiobookId, seriesId)
                                },
                                onSetBookSeries = { bookId, seriesId ->
                                    libraryViewModel.setBookSeries(bookId, seriesId)
                                },
                                onSetMusicSeries = { musicId, seriesId ->
                                    libraryViewModel.setMusicSeries(musicId, seriesId)
                                },
                                onSetComicSeries = { comicId, seriesId ->
                                    libraryViewModel.setComicSeries(comicId, seriesId)
                                },
                                onSetMovieSeries = { movieId, seriesId ->
                                    libraryViewModel.setMovieSeries(movieId, seriesId)
                                },
                                onPlaylistClick = { series ->
                                    navController.navigate(Screen.PlaylistDetail.createRoute(series.id))
                                },
                                onSetSeriesCoverArt = { seriesId, coverArtUri ->
                                    libraryViewModel.setSeriesCoverArt(seriesId, coverArtUri)
                                },
                                onSetAudiobookCoverArt = { audiobookId, coverArtUri ->
                                    libraryViewModel.setAudiobookCoverArt(audiobookId, coverArtUri)
                                },
                                onSetBookCoverArt = { bookId, coverArtUri ->
                                    libraryViewModel.setBookCoverArt(bookId, coverArtUri)
                                },
                                onSetMusicCoverArt = { musicId, coverArtUri ->
                                    libraryViewModel.setMusicCoverArt(musicId, coverArtUri)
                                },
                                onSetComicCoverArt = { comicId, coverArtUri ->
                                    libraryViewModel.setComicCoverArt(comicId, coverArtUri)
                                },
                                onSetMovieCoverArt = { movieId, coverArtUri ->
                                    libraryViewModel.setMovieCoverArt(movieId, coverArtUri)
                                },
                                collapsedSeries = collapsedSeries,
                                onCollapsedSeriesChange = { updated ->
                                    settingsViewModel.setCollapsedSeries(updated)
                                },
                                libraryOwnerName = libraryOwnerName,
                                profiles = profiles,
                                onProfileSelect = { profile ->
                                    // Persist current playback positions before switching profiles
                                    lastPlayedMusic?.let { music ->
                                        libraryViewModel.updateMusicProgress(music.id, musicExoPlayer.currentPosition)
                                        musicExoPlayer.pause()
                                    }
                                    lastPlayedAudiobook?.let { audiobook ->
                                        val position = playbackState.currentPosition
                                        val duration = if (playbackState.duration > 0) playbackState.duration else audiobook.duration
                                        libraryViewModel.updateProgress(audiobook.id, position, duration)
                                        player.pause()
                                    }
                                    // Clear mini player state so it doesn't bleed into the new profile
                                    lastPlayedMusic = null
                                    lastPlayedAudiobook = null
                                    isMusicPlaying = false
                                    isAudiobookPlaying = false
                                    musicCurrentPosition = 0L
                                    currentMusicPlaylist = emptyList()

                                    settingsViewModel.selectProfile(profile)
                                    // Update library to scan profile-specific content
                                    libraryViewModel.setActiveProfile(profile.name)
                                },
                                onAddProfile = { settingsViewModel.addProfile(it) },
                                onDeleteProfile = { settingsViewModel.deleteProfile(it) },
                                onRenameProfile = { profile, newName ->
                                    settingsViewModel.renameProfile(profile, newName)
                                },
                                onSetProfilePicture = { profile, pictureUri ->
                                    settingsViewModel.setProfilePicture(profile, pictureUri)
                                },
                                onBackupProfile = { profile ->
                                    settingsViewModel.exportProfileBackup(profile) { file ->
                                        val message = if (file != null) {
                                            "Backup saved to ${file.absolutePath}"
                                        } else {
                                            "Unable to create profile backup"
                                        }
                                        android.widget.Toast.makeText(
                                            context,
                                            message,
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                onRescanLibrary = {
                                    libraryViewModel.initialize(context)
                                    libraryViewModel.scanBooksFolder(context)
                                },
                                onUpdateLibrary = {
                                    libraryViewModel.updateLibrary(context) { message ->
                                        // Show toast message with result
                                        android.widget.Toast.makeText(
                                            context,
                                            message,
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                currentTheme = appTheme,
                                onThemeChange = { settingsViewModel.setTheme(it) },
                                accentTheme = accentTheme,
                                onAccentThemeChange = { settingsViewModel.setAccentTheme(it) },
                                backgroundTheme = backgroundTheme,
                                onBackgroundThemeChange = { settingsViewModel.setBackgroundTheme(it) },
                                darkMode = darkMode,
                                onDarkModeChange = { settingsViewModel.setDarkMode(it) },
                                // Settings parameters
                                skipForward = skipForward,
                                onSkipForwardChange = { settingsViewModel.setSkipForwardDuration(it) },
                                skipBack = skipBack,
                                onSkipBackChange = { settingsViewModel.setSkipBackDuration(it) },
                                autoBookmark = autoBookmark,
                                onAutoBookmarkChange = { settingsViewModel.setAutoBookmark(it) },
                                keepScreenOn = keepScreenOn,
                                onKeepScreenOnChange = { settingsViewModel.setKeepScreenOn(it) },
                                volumeBoostEnabled = volumeBoostEnabled,
                                onVolumeBoostEnabledChange = { settingsViewModel.setVolumeBoostEnabled(it) },
                                volumeBoostLevel = volumeBoostLevel,
                                onVolumeBoostLevelChange = { settingsViewModel.setVolumeBoostLevel(it) },
                                onLibraryOwnerNameChange = { settingsViewModel.setLibraryOwnerName(it) },
                                playbackSpeed = playbackSpeed,
                                onPlaybackSpeedChange = { settingsViewModel.setPlaybackSpeed(it.coerceIn(0.5f, 2f)) },
                                sleepTimerMinutes = sleepTimerMinutes,
                                onSleepTimerChange = { settingsViewModel.setSleepTimerMinutes(it) },
                                autoPlayNext = autoPlayNext,
                                onAutoPlayNextChange = { settingsViewModel.setAutoPlayNext(it) },
                                defaultLibraryView = defaultLibraryView,
                                onDefaultLibraryViewChange = { settingsViewModel.setDefaultLibraryView(it) },
                                defaultSortOrder = defaultSortOrder,
                                onDefaultSortOrderChange = { settingsViewModel.setDefaultSortOrder(it) },
                                resumePlayback = resumePlayback,
                                onResumePlaybackChange = { settingsViewModel.setResumePlayback(it) },
                                showPlaybackNotification = showPlaybackNotification,
                                onShowPlaybackNotificationChange = { settingsViewModel.setShowPlaybackNotification(it) },
                                // New settings
                                showPlaceholderIcons = showPlaceholderIcons,
                                onShowPlaceholderIconsChange = { settingsViewModel.setShowPlaceholderIcons(it) },
                                normalizeAudio = normalizeAudio,
                                onNormalizeAudioChange = { settingsViewModel.setNormalizeAudio(it) },
                                bassBoostLevel = bassBoostLevel,
                                onBassBoostLevelChange = { settingsViewModel.setBassBoostLevel(it) },
                                equalizerPreset = equalizerPreset,
                                onEqualizerPresetChange = { settingsViewModel.setEqualizerPreset(it) },
                                crossfadeDuration = crossfadeDuration,
                                onCrossfadeDurationChange = { settingsViewModel.setCrossfadeDuration(it) },
                                showFileSize = showFileSize,
                                onShowFileSizeChange = { settingsViewModel.setShowFileSize(it) },
                                showDuration = showDuration,
                                onShowDurationChange = { settingsViewModel.setShowDuration(it) },
                                animationSpeed = animationSpeed,
                                onAnimationSpeedChange = { settingsViewModel.setAnimationSpeed(it) },
                                hapticFeedback = hapticFeedback,
                                onHapticFeedbackChange = { settingsViewModel.setHapticFeedback(it) },
                                confirmBeforeDelete = confirmBeforeDelete,
                                onConfirmBeforeDeleteChange = { settingsViewModel.setConfirmBeforeDelete(it) },
                                useSquareCorners = useSquareCorners,
                                onUseSquareCornersChange = { settingsViewModel.setUseSquareCorners(it) },
                                rememberLastPosition = rememberLastPosition,
                                onRememberLastPositionChange = { settingsViewModel.setRememberLastPosition(it) },
                                autoRewind = autoRewindSeconds,
                                onAutoRewindChange = { settingsViewModel.setAutoRewindSeconds(it) },
                                headsetControls = headsetControls,
                                onHeadsetControlsChange = { settingsViewModel.setHeadsetControls(it) },
                                pauseOnDisconnect = pauseOnDisconnect,
                                onPauseOnDisconnectChange = { settingsViewModel.setPauseOnDisconnect(it) },
                                customPrimaryColor = customPrimaryColor,
                                onCustomPrimaryColorChange = { settingsViewModel.setCustomPrimaryColor(it) },
                                customAccentColor = customAccentColor,
                                onCustomAccentColorChange = { settingsViewModel.setCustomAccentColor(it) },
                                customBackgroundColor = customBackgroundColor,
                                onCustomBackgroundColorChange = { settingsViewModel.setCustomBackgroundColor(it) },
                                appScale = appScale,
                                onAppScaleChange = { settingsViewModel.setAppScale(it) },
                                uiFontScale = uiFontScale,
                                onUiFontScaleChange = { settingsViewModel.setUiFontScale(it) },
                                uiFontFamily = uiFontFamilyName,
                                onUiFontFamilyChange = { settingsViewModel.setUiFontFamily(it) },
                                // Profile-specific audio settings callbacks
                                onProfilePlaybackSpeedChange = { settingsViewModel.setProfilePlaybackSpeed(it) },
                                onProfileSkipForwardChange = { settingsViewModel.setProfileSkipForward(it) },
                                onProfileSkipBackChange = { settingsViewModel.setProfileSkipBack(it) },
                                onProfileVolumeBoostEnabledChange = { settingsViewModel.setProfileVolumeBoostEnabled(it) },
                                onProfileVolumeBoostLevelChange = { settingsViewModel.setProfileVolumeBoostLevel(it) },
                                onProfileNormalizeAudioChange = { settingsViewModel.setProfileNormalizeAudio(it) },
                                onProfileBassBoostLevelChange = { settingsViewModel.setProfileBassBoostLevel(it) },
                                onProfileEqualizerPresetChange = { settingsViewModel.setProfileEqualizerPreset(it) },
                                onProfileSleepTimerChange = { settingsViewModel.setProfileSleepTimer(it) },
                                onBackPressed = {
                                    finish()
                                },
                                // Mini player parameters
                                nowPlaying = nowPlaying,
                                onMiniPlayerPlayPause = {
                                    if (lastPlayedMusic != null) {
                                        if (musicExoPlayer.isPlaying) {
                                            musicExoPlayer.pause()
                                        } else {
                                            musicExoPlayer.play()
                                        }
                                    } else if (lastPlayedAudiobook != null) {
                                        if (isAudiobookPlaying) {
                                            isAudiobookPlaying = false
                                            player.pause()
                                        } else {
                                            isAudiobookPlaying = true
                                            player.play()
                                        }
                                    }
                                },
                                onMiniPlayerNext = {
                                    if (lastPlayedMusic != null) {
                                        val musicList = if (currentMusicPlaylist.isNotEmpty()) currentMusicPlaylist else filteredMusic
                                        currentMusicPlaylist = musicList
                                        val currentIdx = musicList.indexOfFirst { it.id == lastPlayedMusic?.id }
                                        if (currentIdx >= 0 && musicList.size > 1) {
                                            // Determine next track based on shuffle mode
                                            val nextIdx = if (musicShuffleEnabled) {
                                                // Pick a random track that's not the current one
                                                val available = musicList.indices.filter { it != currentIdx }
                                                available.randomOrNull() ?: currentIdx
                                            } else {
                                                // Sequential: next track or wrap to first if repeat all
                                                if (currentIdx < musicList.size - 1) {
                                                    currentIdx + 1
                                                } else if (musicRepeatMode == 2) { // REPEAT_MODE_ALL
                                                    0
                                                } else {
                                                    -1 // No next track
                                                }
                                            }
                                            if (nextIdx >= 0 && nextIdx < musicList.size) {
                                                val nextMusic = musicList[nextIdx]
                                                lastPlayedMusic = nextMusic
                                                settingsViewModel.setLastActiveType("MUSIC")
                                                libraryViewModel.selectMusic(nextMusic)
                                                libraryViewModel.incrementMusicPlayCount(nextMusic.id)
                                                // Play on shared player without navigating
                                                musicExoPlayer.setMediaItem(buildMusicMediaItem(nextMusic))
                                                musicExoPlayer.prepare()
                                                musicExoPlayer.seekTo(0)
                                                musicExoPlayer.play()
                                                startPlaybackService()
                                            }
                                        }
                                    }
                                },
                                onMiniPlayerPrevious = {
                                    if (lastPlayedMusic != null) {
                                        val musicList = if (currentMusicPlaylist.isNotEmpty()) currentMusicPlaylist else filteredMusic
                                        currentMusicPlaylist = musicList
                                        val currentIdx = musicList.indexOfFirst { it.id == lastPlayedMusic?.id }
                                        if (currentIdx > 0) {
                                            val prevMusic = musicList[currentIdx - 1]
                                            lastPlayedMusic = prevMusic
                                            settingsViewModel.setLastActiveType("MUSIC")
                                            libraryViewModel.selectMusic(prevMusic)
                                            libraryViewModel.incrementMusicPlayCount(prevMusic.id)
                                            // Play on shared player without navigating
                                            musicExoPlayer.setMediaItem(buildMusicMediaItem(prevMusic))
                                            musicExoPlayer.prepare()
                                            musicExoPlayer.seekTo(0)
                                            musicExoPlayer.play()
                                            startPlaybackService()
                                        }
                                    }
                                },
                                onMiniPlayerSeekBack = {
                                    if (lastPlayedAudiobook != null) {
                                        player.seekBackward(profileSkipBack)
                                    } else if (lastPlayedMusic != null) {
                                        val newPos = (musicExoPlayer.currentPosition - profileSkipBack * 1000L).coerceAtLeast(0)
                                        musicExoPlayer.seekTo(newPos)
                                    }
                                },
                                onMiniPlayerSeekForward = {
                                    if (lastPlayedAudiobook != null) {
                                        player.seekForward(profileSkipForward)
                                    } else if (lastPlayedMusic != null) {
                                        val duration = if (musicExoPlayer.duration > 0) musicExoPlayer.duration else Long.MAX_VALUE
                                        val newPos = (musicExoPlayer.currentPosition + profileSkipForward * 1000L).coerceAtMost(duration)
                                        musicExoPlayer.seekTo(newPos)
                                    }
                                },
                                onMiniPlayerClick = {
                                    // Navigate to player screen (works even when paused)
                                    if (lastPlayedMusic != null) {
                                        lastPlayedMusic = lastPlayedMusic?.copy(lastPosition = musicCurrentPosition)
                                        lastPlayedMusic?.let { track ->
                                            libraryViewModel.selectMusic(track)
                                        }
                                        // Ensure media item is loaded if returning after process death
                                        if (musicExoPlayer.currentMediaItem == null) {
                                            lastPlayedMusic?.let { track ->
                                                musicExoPlayer.setMediaItem(buildMusicMediaItem(track))
                                                musicExoPlayer.prepare()
                                                musicExoPlayer.seekTo(musicCurrentPosition)
                                                musicExoPlayer.playWhenReady = isMusicPlaying
                                            }
                                        }
                                        navController.navigate(Screen.MusicPlayer.route)
                                    } else if (lastPlayedAudiobook != null) {
                                        lastPlayedAudiobook?.let { book ->
                                            libraryViewModel.selectAudiobook(book)
                                            // Reload if needed
                                            CoroutineScope(Dispatchers.Main).launch {
                                                if (player.currentAudiobook.value?.uri != book.uri) {
                                                    player.loadAudiobook(book.uri, book.title, book.author)
                                                    player.seekTo(book.lastPosition)
                                                    if (isAudiobookPlaying) player.play() else player.pause()
                                                }
                                            }
                                        }
                                        navController.navigate(Screen.Player.route)
                                    }
                                },
                                onMiniPlayerDismiss = {
                                    // Pause and clear the active player on swipe dismiss
                                    if (lastPlayedMusic != null) {
                                        // Pause music and save position
                                        musicExoPlayer.pause()
                                        isMusicPlaying = false
                                        lastPlayedMusic?.let { track ->
                                            libraryViewModel.updateProgress(track.id, musicCurrentPosition, track.duration)
                                        }
                                        lastPlayedMusic = null
                                        settingsViewModel.setLastMusicState(null, 0L, false)
                                    }
                                    if (lastPlayedAudiobook != null) {
                                        // Pause audiobook and save position
                                        player.pause()
                                        isAudiobookPlaying = false
                                        lastPlayedAudiobook?.let { book ->
                                            val position = playbackState.currentPosition
                                            val duration = if (playbackState.duration > 0) playbackState.duration else book.duration
                                            libraryViewModel.updateProgress(book.id, position, duration)
                                        }
                                        lastPlayedAudiobook = null
                                        settingsViewModel.setLastAudiobookState(null, 0L, false)
                                    }
                                    settingsViewModel.setLastActiveType(null)
                                }
                            )
                        }

                        // Player screen
                        composable(Screen.Player.route) {
                            PlayerScreen(
                                player = player,
                                onBackToLibrary = {
                                    libraryViewModel.saveLibrary()
                                    navController.popBackStack()
                                },
                                onNavigateToLibrary = {
                                    navController.navigate(Screen.Main.route) {
                                        popUpTo(Screen.Main.route) { inclusive = true }
                                    }
                                },
                                onNavigateToProfile = {
                                    navController.navigate(Screen.Main.createRoute("profile")) {
                                        popUpTo(Screen.Main.route) { inclusive = true }
                                    }
                                },
                                onToggleSearch = {
                                    libraryViewModel.toggleSearch()
                                    navController.navigate(Screen.Main.route) {
                                        popUpTo(Screen.Main.route) { inclusive = true }
                                    }
                                },
                                skipForwardSeconds = profileSkipForward,
                                skipBackSeconds = profileSkipBack,
                                sleepTimerMinutes = profileSleepTimer,
                                playbackSpeed = profilePlaybackSpeed,
                                onPlaybackSpeedChange = {
                                    val safeSpeed = it.coerceIn(0.5f, 2f)
                                    settingsViewModel.setProfilePlaybackSpeed(safeSpeed)
                                    player.setPlaybackSpeed(safeSpeed)
                                },
                                equalizerPreset = profileEqualizer,
                                volumeBoostEnabled = profileVolumeBoost,
                                volumeBoostLevel = profileVolumeBoostLevel,
                                normalizeAudio = profileNormalizeAudio,
                                bassBoostLevel = profileBassBoost,
                                crossfadeDuration = crossfadeDuration,
                                autoRewind = autoRewindSeconds,
                                autoPlayNext = autoPlayNext,
                                resumePlayback = resumePlayback,
                                onSkipForwardChange = { settingsViewModel.setProfileSkipForward(it) },
                                onSkipBackChange = { settingsViewModel.setProfileSkipBack(it) },
                                onAutoRewindChange = { settingsViewModel.setAutoRewindSeconds(it) },
                                onAutoPlayNextChange = { settingsViewModel.setAutoPlayNext(it) },
                                onResumePlaybackChange = { settingsViewModel.setResumePlayback(it) },
                                onSleepTimerChange = { settingsViewModel.setProfileSleepTimer(it) },
                                onCrossfadeDurationChange = { settingsViewModel.setCrossfadeDuration(it) },
                                onVolumeBoostEnabledChange = { settingsViewModel.setProfileVolumeBoostEnabled(it) },
                                onVolumeBoostLevelChange = { settingsViewModel.setProfileVolumeBoostLevel(it) },
                                onNormalizeAudioChange = { settingsViewModel.setProfileNormalizeAudio(it) },
                                onBassBoostLevelChange = { settingsViewModel.setProfileBassBoostLevel(it) },
                                onEqualizerPresetChange = { settingsViewModel.setProfileEqualizerPreset(it) },
                                showBackButton = showBackButton,
                                showSearchBar = showSearchBar,
                                showPlaceholderIcons = showPlaceholderIcons,
                                headerTitle = profileHeaderTitle,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // E-book reader screen
                        composable(Screen.EbookReader.route) {
                            selectedBook?.let { book ->
                                EbookReaderScreen(
                                    book = book,
                                    onBack = {
                                        libraryViewModel.saveLibrary()
                                        navController.popBackStack()
                                    },
                                    onPageChange = { page, totalPages ->
                                        libraryViewModel.updateBookProgress(
                                            book.id,
                                            page,
                                            totalPages
                                        )
                                    },
                                    appDarkMode = darkMode,
                                    onNavigateToLibrary = {
                                        libraryViewModel.saveLibrary()
                                        navController.navigate(Screen.Main.route) {
                                            popUpTo(Screen.Main.route) { inclusive = true }
                                        }
                                    },
                                    onNavigateToProfile = {
                                        libraryViewModel.saveLibrary()
                                        navController.navigate(Screen.Main.createRoute("profile")) {
                                            popUpTo(Screen.Main.route) { inclusive = true }
                                        }
                                    },
                                    showBackButton = showBackButton,
                                    showSearchBar = showSearchBar,
                                    headerTitle = profileHeaderTitle,
                                    // Reader settings from repository (persisted)
                                    initialFontSize = readerFontSize,
                                    initialLineSpacing = readerLineSpacing,
                                    initialFontFamily = readerFont,
                                    initialTextAlign = readerTextAlign,
                                    initialMargins = readerMargins,
                                    initialParagraphSpacing = readerParagraphSpacing,
                                    initialBrightness = readerBrightness,
                                    initialBoldText = readerBoldText,
                                    initialWordSpacing = readerWordSpacing,
                                    initialBackgroundColor = readerTheme,
                                    initialPageFitMode = readerPageFitMode,
                                    initialPageGap = readerPageGap,
                                    initialForceTwoPage = readerForceTwoPage,
                                    initialForceSinglePage = readerForceSinglePage,
                                    // Callbacks to save settings when changed
                                    onFontSizeChange = { settingsViewModel.setReaderFontSize(it) },
                                    onLineSpacingChange = { settingsViewModel.setReaderLineSpacing(it) },
                                    onFontFamilyChange = { settingsViewModel.setReaderFont(it) },
                                    onTextAlignChange = { settingsViewModel.setReaderTextAlign(it) },
                                    onMarginsChange = { settingsViewModel.setReaderMargins(it) },
                                    onParagraphSpacingChange = { settingsViewModel.setReaderParagraphSpacing(it) },
                                    onBrightnessChange = { settingsViewModel.setReaderBrightness(it) },
                                    onBoldTextChange = { settingsViewModel.setReaderBoldText(it) },
                                    onWordSpacingChange = { settingsViewModel.setReaderWordSpacing(it) },
                                    onBackgroundColorChange = { settingsViewModel.setReaderTheme(it) },
                                    onPageFitModeChange = { settingsViewModel.setReaderPageFitMode(it) },
                                    onPageGapChange = { settingsViewModel.setReaderPageGap(it) },
                                    onForceTwoPageChange = { settingsViewModel.setReaderForceTwoPage(it) },
                                    onForceSinglePageChange = { settingsViewModel.setReaderForceSinglePage(it) }
                                )
                            }
                        }

                        // Music player screen
                        composable(Screen.MusicPlayer.route) {
                            selectedMusic?.let { music ->
                                // Calculate current index in playlist
                                val activePlaylist = if (currentMusicPlaylist.isNotEmpty()) currentMusicPlaylist else filteredMusic
                                if (currentMusicPlaylist.isEmpty() && activePlaylist.isNotEmpty()) {
                                    currentMusicPlaylist = activePlaylist
                                }
                                val currentMusicIndex = activePlaylist.indexOfFirst { it.id == music.id }
                                MusicPlayerScreen(
                                    music = music,
                                    playlist = activePlaylist,
                                    currentIndex = if (currentMusicIndex >= 0) currentMusicIndex else 0,
                                    onBack = {
                                        libraryViewModel.saveLibrary()
                                        navController.popBackStack()
                                    },
                                    onPositionChange = { position ->
                                        libraryViewModel.updateMusicProgress(music.id, position)
                                        musicCurrentPosition = position
                                        settingsViewModel.setLastMusicState(music.id, position, musicExoPlayer.isPlaying)
                                    },
                                    onTrackChange = { newTrack ->
                                        lastPlayedMusic = newTrack
                                        settingsViewModel.setLastActiveType("MUSIC")
                                        if (currentMusicPlaylist.isEmpty()) {
                                            currentMusicPlaylist = activePlaylist
                                        }
                                        libraryViewModel.selectMusic(newTrack)
                                        libraryViewModel.incrementMusicPlayCount(newTrack.id)
                                        // Load new track into shared player
                                        musicExoPlayer.setMediaItem(buildMusicMediaItem(newTrack))
                                        musicExoPlayer.prepare()
                                        musicExoPlayer.seekTo(0)
                                        musicExoPlayer.play()
                                        startPlaybackService()
                                    },
                                    onNavigateToLibrary = {
                                        navController.navigate(Screen.Main.route) {
                                            popUpTo(Screen.Main.route) { inclusive = true }
                                        }
                                    },
                                    onNavigateToProfile = {
                                        navController.navigate(Screen.Main.createRoute("profile")) {
                                            popUpTo(Screen.Main.route) { inclusive = true }
                                        }
                                    },
                                    skipForwardSeconds = profileSkipForward,
                                    skipBackSeconds = profileSkipBack,
                                    sleepTimerMinutes = profileSleepTimer,
                                    playbackSpeed = profilePlaybackSpeed,
                                    equalizerPreset = profileEqualizer,
                                    volumeBoostEnabled = profileVolumeBoost,
                                    volumeBoostLevel = profileVolumeBoostLevel,
                                    normalizeAudio = profileNormalizeAudio,
                                    bassBoostLevel = profileBassBoost,
                                    crossfadeDuration = crossfadeDuration,
                                    autoRewind = autoRewindSeconds,
                                    autoPlayNext = autoPlayNext,
                                    resumePlayback = resumePlayback,
                                    onPlaybackSpeedChange = {
                                        val safeSpeed = it.coerceIn(0.5f, 2f)
                                        settingsViewModel.setProfilePlaybackSpeed(safeSpeed)
                                        musicExoPlayer.playbackParameters = PlaybackParameters(safeSpeed, 1f)
                                    },
                                    onSkipForwardChange = { settingsViewModel.setProfileSkipForward(it) },
                                    onSkipBackChange = { settingsViewModel.setProfileSkipBack(it) },
                                    onAutoRewindChange = { settingsViewModel.setAutoRewindSeconds(it) },
                                    onAutoPlayNextChange = { settingsViewModel.setAutoPlayNext(it) },
                                    onResumePlaybackChange = { settingsViewModel.setResumePlayback(it) },
                                    onSleepTimerChange = { settingsViewModel.setProfileSleepTimer(it) },
                                    onCrossfadeDurationChange = { settingsViewModel.setCrossfadeDuration(it) },
                                    onVolumeBoostEnabledChange = { settingsViewModel.setProfileVolumeBoostEnabled(it) },
                                    onVolumeBoostLevelChange = { settingsViewModel.setProfileVolumeBoostLevel(it) },
                                    onNormalizeAudioChange = { settingsViewModel.setProfileNormalizeAudio(it) },
                                    onBassBoostLevelChange = { settingsViewModel.setProfileBassBoostLevel(it) },
                                    onEqualizerPresetChange = { settingsViewModel.setProfileEqualizerPreset(it) },
                                    initialShuffleEnabled = musicShuffleEnabled,
                                    initialRepeatMode = musicRepeatMode,
                                    onShuffleEnabledChange = { settingsViewModel.setMusicShuffleEnabled(it) },
                                    onRepeatModeChange = { settingsViewModel.setMusicRepeatMode(it) },
                                    showBackButton = showBackButton,
                                    showSearchBar = showSearchBar,
                                    showPlaceholderIcons = showPlaceholderIcons,
                                    headerTitle = profileHeaderTitle,
                                    externalExoPlayer = musicExoPlayer
                                )
                            }
                        }

                        // Movie player screen
                        composable(Screen.MoviePlayer.route) {
                            selectedMovie?.let { movie ->
                                MoviePlayerScreen(
                                    movie = movie,
                                    onBack = {
                                        libraryViewModel.saveLibrary()
                                        navController.popBackStack()
                                    },
                                    onPositionChange = { position ->
                                        libraryViewModel.updateMovieProgress(movie.id, position)
                                    },
                                    onNavigateToLibrary = {
                                        navController.navigate(Screen.Main.createRoute("library")) {
                                            popUpTo(Screen.Main.route) { inclusive = true }
                                        }
                                    },
                                    onNavigateToProfile = {
                                        navController.navigate(Screen.Main.createRoute("profile")) {
                                            popUpTo(Screen.Main.route) { inclusive = true }
                                        }
                                    },
                                    showBackButton = showBackButton,
                                    showSearchBar = showSearchBar,
                                    headerTitle = profileHeaderTitle
                                )
                            }
                        }

                        // Comic reader screen - uses dedicated ComicReaderScreen with 2-page support
                        composable(Screen.ComicReader.route) {
                            selectedComic?.let { comic ->
                                ComicReaderScreen(
                                    comic = comic,
                                    onBack = {
                                        libraryViewModel.saveLibrary()
                                        navController.popBackStack()
                                    },
                                    onPageChange = { page, total ->
                                        libraryViewModel.updateComicProgress(comic.id, page, total)
                                    },
                                    onNavigateToLibrary = {
                                        libraryViewModel.saveLibrary()
                                        navController.navigate(Screen.Main.createRoute("library")) {
                                            popUpTo(Screen.Main.route) { inclusive = true }
                                        }
                                    },
                                onNavigateToProfile = {
                                    libraryViewModel.saveLibrary()
                                    navController.navigate(Screen.Main.createRoute("profile")) {
                                        popUpTo(Screen.Main.route) { inclusive = true }
                                    }
                                },
                                showBackButton = showBackButton,
                                showSearchBar = showSearchBar,
                                headerTitle = profileHeaderTitle,
                                // Persisted comic reader settings
                                    darkMode = darkMode,
                                    forceTwoPageMode = comicForceTwoPage,
                                    onForceTwoPageModeChange = { settingsViewModel.setComicForceTwoPage(it) },
                                    forceSinglePageMode = comicForceSinglePage,
                                    onForceSinglePageModeChange = { settingsViewModel.setComicForceSinglePage(it) },
                                    readingDirection = comicReadingDirection,
                                    onReadingDirectionChange = { settingsViewModel.setComicReadingDirection(it) },
                                    pageFitMode = comicPageFitMode,
                                    onPageFitModeChange = { settingsViewModel.setComicPageFitMode(it) },
                                    pageGap = comicPageGap,
                                    onPageGapChange = { settingsViewModel.setComicPageGap(it) },
                                    backgroundColor = comicBackgroundColor,
                                    onBackgroundColorChange = { settingsViewModel.setComicBackgroundColor(it) },
                                    showPageIndicators = comicShowPageIndicators,
                                    onShowPageIndicatorsChange = { settingsViewModel.setComicShowPageIndicators(it) },
                                    enableDoubleTapZoom = comicEnableDoubleTapZoom,
                                    onEnableDoubleTapZoomChange = { settingsViewModel.setComicEnableDoubleTapZoom(it) },
                                    showControlsOnTap = comicShowControlsOnTap,
                                    onShowControlsOnTapChange = { settingsViewModel.setComicShowControlsOnTap(it) }
                                )
                            }
                        }

                        // Series/Playlist detail screen - works for all content types
                        composable(
                            route = Screen.PlaylistDetail.route,
                            arguments = listOf(
                                androidx.navigation.navArgument("seriesId") {
                                    type = androidx.navigation.NavType.StringType
                                }
                            )
                        ) { backStackEntry ->
                            val seriesId = backStackEntry.arguments?.getString("seriesId") ?: return@composable
                            val series = libraryState.series.find { it.id == seriesId }
                            series?.let { seriesItem ->
                                // Get content for this series based on content type
                                val seriesMusic = filteredMusic.filter { it.seriesId == seriesId }.sortedBy { it.seriesOrder }
                                val seriesAudiobooks = filteredAudiobooks.filter { it.seriesId == seriesId }.sortedBy { it.seriesOrder }
                                val seriesBooks = filteredBooks.filter { it.seriesId == seriesId }.sortedBy { it.seriesOrder }
                                val seriesComics = filteredComics.filter { it.seriesId == seriesId }.sortedBy { it.seriesOrder }
                                val seriesMovies = filteredMovies.filter { it.seriesId == seriesId }.sortedBy { it.seriesOrder }

                                // Get cover art from first item if available
                                val coverArt = when (seriesItem.contentType) {
                                    ContentType.MUSIC -> seriesMusic.firstOrNull()?.coverArt
                                    ContentType.CREEPYPASTA -> seriesMusic.firstOrNull()?.coverArt
                                    ContentType.AUDIOBOOK -> seriesAudiobooks.firstOrNull()?.coverArt
                                    ContentType.EBOOK -> seriesBooks.firstOrNull()?.coverArt
                                    ContentType.COMICS -> seriesComics.firstOrNull()?.coverArt
                                    ContentType.MOVIE -> null
                                }

                                // Determine currently playing ID based on content type
                                val currentlyPlayingId = when (seriesItem.contentType) {
                                    ContentType.MUSIC -> if (lastPlayedMusic?.seriesId == seriesId) lastPlayedMusic?.id else null
                                    ContentType.CREEPYPASTA -> if (lastPlayedMusic?.seriesId == seriesId) lastPlayedMusic?.id else null
                                    ContentType.AUDIOBOOK -> if (lastPlayedAudiobook?.seriesId == seriesId) lastPlayedAudiobook?.id else null
                                    else -> null
                                }

                                val isCurrentlyPlaying = when (seriesItem.contentType) {
                                    ContentType.MUSIC -> isMusicPlaying && currentlyPlayingId != null
                                    ContentType.CREEPYPASTA -> isMusicPlaying && currentlyPlayingId != null
                                    ContentType.AUDIOBOOK -> isAudiobookPlaying && currentlyPlayingId != null
                                    else -> false
                                }

                                SeriesDetailScreen(
                                    series = seriesItem,
                                    contentType = seriesItem.contentType,
                                    musicTracks = seriesMusic,
                                    audiobooks = seriesAudiobooks,
                                    books = seriesBooks,
                                    comics = seriesComics,
                                    movies = seriesMovies,
                                    coverArtBitmap = coverArt,
                                    currentlyPlayingId = currentlyPlayingId,
                                    isPlaying = isCurrentlyPlaying,
                                    isShuffleEnabled = musicShuffleEnabled,
                                    isRepeatEnabled = musicRepeatMode != 0,
                                    showPlaceholderIcons = showPlaceholderIcons,
                                    onMusicClick = { track, tracks ->
                                        // Stop audiobook if playing
                                        if (isAudiobookPlaying) {
                                            isAudiobookPlaying = false
                                            selectedAudiobook?.let { audiobook ->
                                                val position = playbackState.currentPosition
                                                val duration = if (playbackState.duration > 0) playbackState.duration else audiobook.duration
                                                libraryViewModel.updateProgress(audiobook.id, position, duration)
                                                lastPlayedAudiobook = audiobook.copy(lastPosition = position)
                                            }
                                            player.pause()
                                        }
                                        lastPlayedMusic = track
                                        settingsViewModel.setLastActiveType("MUSIC")
                                        currentMusicPlaylist = tracks
                                        libraryViewModel.selectMusic(track)
                                        libraryViewModel.incrementMusicPlayCount(track.id)
                                        musicExoPlayer.setMediaItem(buildMusicMediaItem(track))
                                        musicExoPlayer.prepare()
                                        musicExoPlayer.seekTo(0)
                                        musicCurrentPosition = 0L
                                        musicExoPlayer.play()
                                        startPlaybackService()
                                        navController.navigate(Screen.MusicPlayer.route)
                                    },
                                    onAudiobookClick = { audiobook ->
                                        // Stop music if playing
                                        if (isMusicPlaying) {
                                            isMusicPlaying = false
                                            musicExoPlayer.pause()
                                        }
                                        lastPlayedMusic = null
                                        lastPlayedAudiobook = audiobook
                                        isAudiobookPlaying = true
                                        settingsViewModel.setLastActiveType("AUDIOBOOK")
                                        libraryViewModel.selectAudiobook(audiobook)
                                        CoroutineScope(Dispatchers.Main).launch {
                                            player.loadAudiobook(audiobook.uri, audiobook.title, audiobook.author)
                                            if (audiobook.lastPosition > 0) {
                                                player.seekTo(audiobook.lastPosition)
                                            }
                                        }
                                        navController.navigate(Screen.Player.route)
                                    },
                                    onBookClick = { book ->
                                        libraryViewModel.selectBook(book)
                                        navController.navigate(Screen.EbookReader.route)
                                    },
                                    onComicClick = { comic ->
                                        libraryViewModel.selectComic(comic)
                                        navController.navigate(Screen.ComicReader.route)
                                    },
                                    onMovieClick = { movie ->
                                        // Pause music if playing
                                        if (isMusicPlaying) {
                                            isMusicPlaying = false
                                            musicExoPlayer.pause()
                                        }
                                        // Pause audiobook if playing
                                        if (isAudiobookPlaying) {
                                            isAudiobookPlaying = false
                                            selectedAudiobook?.let { audiobook ->
                                                val position = playbackState.currentPosition
                                                val duration = if (playbackState.duration > 0) playbackState.duration else audiobook.duration
                                                libraryViewModel.updateProgress(audiobook.id, position, duration)
                                                lastPlayedAudiobook = audiobook.copy(lastPosition = position)
                                            }
                                            player.pause()
                                        }
                                        libraryViewModel.selectMovie(movie)
                                        navController.navigate(Screen.MoviePlayer.route)
                                    },
                                    onShuffleClick = {
                                        val newShuffleState = !musicShuffleEnabled
                                        settingsViewModel.setMusicShuffleEnabled(newShuffleState)
                                        musicExoPlayer.shuffleModeEnabled = newShuffleState
                                    },
                                    onRepeatClick = {
                                        val newRepeatMode = when (musicRepeatMode) {
                                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                                            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                                            else -> Player.REPEAT_MODE_OFF
                                        }
                                        settingsViewModel.setMusicRepeatMode(newRepeatMode)
                                        musicExoPlayer.repeatMode = newRepeatMode
                                    },
                                    onPlayAllClick = {
                                        when (seriesItem.contentType) {
                                            ContentType.MUSIC, ContentType.CREEPYPASTA -> {
                                                if (seriesMusic.isNotEmpty()) {
                                                    val firstTrack = if (musicShuffleEnabled) seriesMusic.random() else seriesMusic.first()
                                                    if (isAudiobookPlaying) {
                                                        isAudiobookPlaying = false
                                                        player.pause()
                                                    }
                                                    lastPlayedMusic = firstTrack
                                                    settingsViewModel.setLastActiveType("MUSIC")
                                                    currentMusicPlaylist = seriesMusic
                                                    libraryViewModel.selectMusic(firstTrack)
                                                    libraryViewModel.incrementMusicPlayCount(firstTrack.id)
                                                    musicExoPlayer.setMediaItem(buildMusicMediaItem(firstTrack))
                                                    musicExoPlayer.prepare()
                                                    musicExoPlayer.seekTo(0)
                                                    musicCurrentPosition = 0L
                                                    musicExoPlayer.play()
                                                    startPlaybackService()
                                                }
                                            }
                                            ContentType.AUDIOBOOK -> {
                                                if (seriesAudiobooks.isNotEmpty()) {
                                                    val firstAudiobook = seriesAudiobooks.first()
                                                    if (isMusicPlaying) {
                                                        isMusicPlaying = false
                                                        musicExoPlayer.pause()
                                                    }
                                                    lastPlayedMusic = null
                                                    lastPlayedAudiobook = firstAudiobook
                                                    isAudiobookPlaying = true
                                                    settingsViewModel.setLastActiveType("AUDIOBOOK")
                                                    libraryViewModel.selectAudiobook(firstAudiobook)
                                                    CoroutineScope(Dispatchers.Main).launch {
                                                        player.loadAudiobook(firstAudiobook.uri, firstAudiobook.title, firstAudiobook.author)
                                                        if (firstAudiobook.lastPosition > 0) {
                                                            player.seekTo(firstAudiobook.lastPosition)
                                                        }
                                                        player.play()
                                                    }
                                                }
                                            }
                                            else -> { /* No play all for ebooks/comics */ }
                                        }
                                    },
                                    onPlayPause = {
                                        when (seriesItem.contentType) {
                                            ContentType.MUSIC, ContentType.CREEPYPASTA -> {
                                                if (musicExoPlayer.isPlaying) {
                                                    musicExoPlayer.pause()
                                                } else {
                                                    musicExoPlayer.play()
                                                    startPlaybackService()
                                                }
                                            }
                                            ContentType.AUDIOBOOK -> {
                                                if (isAudiobookPlaying) {
                                                    isAudiobookPlaying = false
                                                    player.pause()
                                                } else {
                                                    isAudiobookPlaying = true
                                                    player.play()
                                                }
                                            }
                                            else -> { }
                                        }
                                    },
                                    onSkipPrevious = {
                                        when (seriesItem.contentType) {
                                            ContentType.MUSIC, ContentType.CREEPYPASTA -> {
                                                val currentIdx = seriesMusic.indexOfFirst { it.id == lastPlayedMusic?.id }
                                                if (currentIdx > 0) {
                                                    val prevTrack = seriesMusic[currentIdx - 1]
                                                    lastPlayedMusic = prevTrack
                                                    settingsViewModel.setLastActiveType("MUSIC")
                                                    libraryViewModel.selectMusic(prevTrack)
                                                    libraryViewModel.incrementMusicPlayCount(prevTrack.id)
                                                    musicExoPlayer.setMediaItem(buildMusicMediaItem(prevTrack))
                                                    musicExoPlayer.prepare()
                                                    musicExoPlayer.seekTo(0)
                                                    musicExoPlayer.play()
                                                    startPlaybackService()
                                                }
                                            }
                                            else -> { }
                                        }
                                    },
                                    onSkipNext = {
                                        when (seriesItem.contentType) {
                                            ContentType.MUSIC, ContentType.CREEPYPASTA -> {
                                                val currentIdx = seriesMusic.indexOfFirst { it.id == lastPlayedMusic?.id }
                                                val nextIdx = if (musicShuffleEnabled && seriesMusic.size > 1) {
                                                    val available = seriesMusic.indices.filter { it != currentIdx }
                                                    available.randomOrNull() ?: currentIdx
                                                } else if (currentIdx < seriesMusic.size - 1) {
                                                    currentIdx + 1
                                                } else if (musicRepeatMode == Player.REPEAT_MODE_ALL) {
                                                    0
                                                } else {
                                                    -1
                                                }
                                                if (nextIdx >= 0 && nextIdx < seriesMusic.size) {
                                                    val nextTrack = seriesMusic[nextIdx]
                                                    lastPlayedMusic = nextTrack
                                                    settingsViewModel.setLastActiveType("MUSIC")
                                                    libraryViewModel.selectMusic(nextTrack)
                                                    libraryViewModel.incrementMusicPlayCount(nextTrack.id)
                                                    musicExoPlayer.setMediaItem(buildMusicMediaItem(nextTrack))
                                                    musicExoPlayer.prepare()
                                                    musicExoPlayer.seekTo(0)
                                                    musicExoPlayer.play()
                                                    startPlaybackService()
                                                }
                                            }
                                            else -> { }
                                        }
                                    },
                                    onBack = {
                                        navController.navigate(Screen.Main.createRoute("library")) {
                                            popUpTo(Screen.Main.route) { inclusive = true }
                                        }
                                    },
                                    onNavigateToLibrary = {
                                        navController.navigate(Screen.Main.createRoute("library")) {
                                            popUpTo(Screen.Main.route) { inclusive = true }
                                        }
                                    },
                                    onNavigateToProfile = {
                                        navController.navigate(Screen.Main.createRoute("profile")) {
                                            popUpTo(Screen.Main.route) { inclusive = true }
                                        }
                                    },
                                    onNavigateToSettings = {
                                        navController.navigate(Screen.Main.createRoute("settings")) {
                                            popUpTo(Screen.Main.route) { inclusive = true }
                                        }
                                    },
                                    miniplayerContent = {
                                        MiniPlayer(
                                            nowPlaying = nowPlaying,
                                            onPlayPause = {
                                                if (lastPlayedMusic != null) {
                                                    if (musicExoPlayer.isPlaying) {
                                                        musicExoPlayer.pause()
                                                    } else {
                                                        musicExoPlayer.play()
                                                        startPlaybackService()
                                                    }
                                                } else if (lastPlayedAudiobook != null) {
                                                    if (isAudiobookPlaying) {
                                                        isAudiobookPlaying = false
                                                        player.pause()
                                                    } else {
                                                        isAudiobookPlaying = true
                                                        player.play()
                                                    }
                                                }
                                            },
                                            onNext = {
                                                if (lastPlayedMusic != null && currentMusicPlaylist.isNotEmpty()) {
                                                    val currentIndex = currentMusicPlaylist.indexOfFirst { it.id == lastPlayedMusic?.id }
                                                    val nextIndex = if (currentIndex < currentMusicPlaylist.size - 1) currentIndex + 1 else 0
                                                    val nextTrack = currentMusicPlaylist[nextIndex]
                                                    lastPlayedMusic = nextTrack
                                                    settingsViewModel.setLastActiveType("MUSIC")
                                                    libraryViewModel.selectMusic(nextTrack)
                                                    libraryViewModel.incrementMusicPlayCount(nextTrack.id)
                                                    musicExoPlayer.setMediaItem(buildMusicMediaItem(nextTrack))
                                                    musicExoPlayer.prepare()
                                                    musicExoPlayer.seekTo(0)
                                                    musicExoPlayer.play()
                                                    startPlaybackService()
                                                }
                                            },
                                            onPrevious = {
                                                if (lastPlayedMusic != null && currentMusicPlaylist.isNotEmpty()) {
                                                    val currentIndex = currentMusicPlaylist.indexOfFirst { it.id == lastPlayedMusic?.id }
                                                    val prevIndex = if (currentIndex > 0) currentIndex - 1 else currentMusicPlaylist.size - 1
                                                    val prevTrack = currentMusicPlaylist[prevIndex]
                                                    lastPlayedMusic = prevTrack
                                                    settingsViewModel.setLastActiveType("MUSIC")
                                                    libraryViewModel.selectMusic(prevTrack)
                                                    libraryViewModel.incrementMusicPlayCount(prevTrack.id)
                                                    musicExoPlayer.setMediaItem(buildMusicMediaItem(prevTrack))
                                                    musicExoPlayer.prepare()
                                                    musicExoPlayer.seekTo(0)
                                                    musicExoPlayer.play()
                                                    startPlaybackService()
                                                }
                                            },
                                            onSeekBack = {
                                                if (lastPlayedAudiobook != null) {
                                                    player.seekBackward(profileSkipBack)
                                                } else if (lastPlayedMusic != null) {
                                                    val newPos = (musicExoPlayer.currentPosition - profileSkipBack * 1000L).coerceAtLeast(0)
                                                    musicExoPlayer.seekTo(newPos)
                                                }
                                            },
                                            onSeekForward = {
                                                if (lastPlayedAudiobook != null) {
                                                    player.seekForward(profileSkipForward)
                                                } else if (lastPlayedMusic != null) {
                                                    val newPos = (musicExoPlayer.currentPosition + profileSkipForward * 1000L).coerceAtMost(musicExoPlayer.duration)
                                                    musicExoPlayer.seekTo(newPos)
                                                }
                                            },
                                            onClick = {
                                                if (lastPlayedMusic != null) {
                                                    navController.navigate(Screen.MusicPlayer.route)
                                                } else if (lastPlayedAudiobook != null) {
                                                    navController.navigate(Screen.Player.route)
                                                }
                                            },
                                            onDismiss = {
                                                if (lastPlayedMusic != null) {
                                                    musicExoPlayer.pause()
                                                    isMusicPlaying = false
                                                    lastPlayedMusic?.let { track ->
                                                        libraryViewModel.updateProgress(track.id, musicCurrentPosition, track.duration)
                                                    }
                                                    lastPlayedMusic = null
                                                    settingsViewModel.setLastMusicState(null, 0L, false)
                                                }
                                                if (lastPlayedAudiobook != null) {
                                                    player.pause()
                                                    isAudiobookPlaying = false
                                                    lastPlayedAudiobook?.let { book ->
                                                        val position = playbackState.currentPosition
                                                        val duration = if (playbackState.duration > 0) playbackState.duration else book.duration
                                                        libraryViewModel.updateProgress(book.id, position, duration)
                                                    }
                                                    lastPlayedAudiobook = null
                                                    settingsViewModel.setLastAudiobookState(null, 0L, false)
                                                }
                                                settingsViewModel.setLastActiveType(null)
                                            },
                                            showPlaceholderIcons = showPlaceholderIcons
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Persist audiobook playback state for mini player restore
            // Track both playbackState.isPlaying and local isAudiobookPlaying to catch all state changes
            LaunchedEffect(playbackState.currentPosition, playbackState.isPlaying, isAudiobookPlaying, player.currentAudiobook.value) {
                val actuallyPlaying = isAudiobookPlaying || playbackState.isPlaying
                if (lastAudiobookId == null && lastPlayedAudiobook == null && !actuallyPlaying) {
                    return@LaunchedEffect
                }
                val currentUri = player.currentAudiobook.value?.uri
                    ?: musicExoPlayer.currentMediaItem?.localConfiguration?.uri
                val resolvedBook = selectedAudiobook
                    ?: lastPlayedAudiobook
                    ?: currentUri?.let { uri -> libraryState.audiobooks.find { it.uri == uri } }

                resolvedBook?.let { book ->
                    val position = playbackState.currentPosition.takeIf { it > 0L } ?: book.lastPosition
                    val duration = if (playbackState.duration > 0) playbackState.duration else book.duration
                    lastPlayedAudiobook = book.copy(lastPosition = position, duration = duration)
                    // Use isAudiobookPlaying as primary indicator since it's more reliable for local state
                    settingsViewModel.setLastAudiobookState(book.id, position, actuallyPlaying)
                    if (actuallyPlaying && lastActiveType != "AUDIOBOOK") {
                        settingsViewModel.setLastActiveType("AUDIOBOOK")
                    }
                }
            }
        }
    }

    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Request "All Files Access" for Android 11+ to scan device storage
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_MEDIA_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}

private fun formatProfileHeader(name: String?): String {
    val trimmed = name?.trim()?.takeIf { it.isNotEmpty() } ?: return "Librio"
    val suffix = if (trimmed.lastOrNull()?.lowercaseChar() == 's') "' Librio" else "'s Librio"
    return "$trimmed$suffix"
}
