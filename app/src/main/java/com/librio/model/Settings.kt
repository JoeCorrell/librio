package com.librio.model

/**
 * Profile settings data class for JSON file storage
 * Stores UI preferences, theme, and display settings per profile
 */
data class ProfileSettings(
    val version: Int = 1,
    val id: String,
    val name: String,
    val profilePicture: String? = null,
    val theme: String = "DARK_TEAL",
    val darkMode: Boolean = false,
    val accentTheme: String = "DARK_TEAL",
    val backgroundTheme: String = "WHITE",
    val customPrimaryColor: Int = 0x00897B,
    val customAccentColor: Int = 0x26A69A,
    val customBackgroundColor: Int = 0x121212,
    val appScale: Float = 1.0f,
    val uiFontScale: Float = 1.0f,
    val uiFontFamily: String = "Default",
    val libraryOwnerName: String = "",
    val defaultLibraryView: String = "LIST", // "LIST" or "GRID_2"
    val defaultSortOrder: String = "TITLE",
    val useSquareCorners: Boolean = false,
    val showPlaceholderIcons: Boolean = true,
    val showFileSize: Boolean = true,
    val showDuration: Boolean = true,
    val showBackButton: Boolean = true,
    val showSearchBar: Boolean = true,
    val animationSpeed: String = "Normal",
    val hapticFeedback: Boolean = true,
    val confirmBeforeDelete: Boolean = true,
    val collapsedSeries: List<String> = emptyList(),
    val collapsedPlaylists: List<String> = emptyList(),
    val selectedContentType: String = "AUDIOBOOK",
    val selectedAudiobookCategoryId: String? = null,
    val selectedBookCategoryId: String? = null,
    val selectedMusicCategoryId: String? = null,
    val selectedComicCategoryId: String? = null,
    val selectedMovieCategoryId: String? = null,
    val lastModified: Long = System.currentTimeMillis()
)

/**
 * Audio settings data class for JSON file storage
 * Stores playback, volume, equalizer, and audio processing settings per profile
 */
data class AudioSettings(
    val version: Int = 1,
    val playbackSpeed: Float = 1.0f,
    val skipForwardDuration: Int = 30,
    val skipBackDuration: Int = 10,
    val sleepTimerMinutes: Int = 0,
    val autoBookmark: Boolean = true,
    val keepScreenOn: Boolean = false,
    val autoPlayNext: Boolean = true,
    val resumePlayback: Boolean = true,
    val rememberLastPosition: Boolean = true,
    val autoRewindSeconds: Int = 0,
    val volumeBoostEnabled: Boolean = false,
    val volumeBoostLevel: Float = 1.5f,
    val normalizeAudio: Boolean = false,
    val bassBoostLevel: Float = 0f,
    val equalizerPreset: String = "DEFAULT",
    val headsetControls: Boolean = true,
    val pauseOnDisconnect: Boolean = true,
    val showPlaybackNotification: Boolean = true,
    // Undo seek feature
    val showUndoSeekButton: Boolean = true,
    val lastSeekPosition: Long = 0L,
    // Headphone/Bluetooth controls
    val headphoneUnplugAction: String = "PAUSE", // PAUSE, STOP, CONTINUE
    // Audio transition effects
    val fadeOnPauseResume: Boolean = false,
    val fadeOnSeek: Boolean = false,
    val fadeDurationMs: Int = 300,
    // Gapless playback
    val gaplessPlayback: Boolean = true,
    // Silence trimming
    val trimSilence: Boolean = false,
    // Last playback state for music
    val lastMusicId: String? = null,
    val lastMusicPosition: Long = 0L,
    val lastMusicPlaying: Boolean = false,
    // Last playback state for audiobooks
    val lastAudiobookId: String? = null,
    val lastAudiobookPosition: Long = 0L,
    val lastAudiobookPlaying: Boolean = false,
    // Track which media type was most recently active ("MUSIC" or "AUDIOBOOK")
    val lastActiveType: String? = null,
    // Music playback mode settings
    val musicShuffleEnabled: Boolean = false,
    val musicRepeatMode: Int = 0, // 0 = OFF, 1 = ONE, 2 = ALL (matches Player.REPEAT_MODE_*)
    val lastModified: Long = System.currentTimeMillis()
)

/**
 * E-Reader settings data class for JSON file storage
 * Stores font, layout, and display settings for e-book reading per profile
 */
data class ReaderSettings(
    val version: Int = 1,
    val fontSize: Int = 18,
    val lineSpacing: Float = 1.4f,
    val readerTheme: String = "light",
    val fontFamily: String = "serif",
    val textAlignment: String = "left",
    val margins: Int = 16,
    val paragraphSpacing: Int = 12,
    val brightness: Float = 1.0f,
    val boldText: Boolean = false,
    val wordSpacing: Int = 0,
    val pageFitMode: String = "fit",
    val pageGap: Int = 4,
    val forceTwoPage: Boolean = false,
    val forceSinglePage: Boolean = false,
    val keepScreenOn: Boolean = true,
    val lastModified: Long = System.currentTimeMillis()
)

/**
 * Comic reader settings data class for JSON file storage
 * Stores display and navigation settings for comic reading per profile
 */
data class ComicSettings(
    val version: Int = 1,
    val forceTwoPage: Boolean = false,
    val forceSinglePage: Boolean = false,
    val readingDirection: String = "ltr",
    val pageFitMode: String = "fit",
    val pageGap: Int = 4,
    val backgroundColor: String = "theme",
    val showPageIndicators: Boolean = true,
    val enableDoubleTapZoom: Boolean = true,
    val showControlsOnTap: Boolean = true,
    val keepScreenOn: Boolean = true,
    val lastModified: Long = System.currentTimeMillis()
)

/**
 * Movie player settings data class for JSON file storage
 * Stores playback, display, and control settings for video playback per profile
 */
data class MovieSettings(
    val version: Int = 1,
    val playbackSpeed: Float = 1.0f,
    val keepScreenOn: Boolean = true,
    val resizeMode: String = "fit",  // fit, fill, zoom
    val brightness: Float = 1.0f,
    val autoFullscreenLandscape: Boolean = true,
    val showControlsOnTap: Boolean = true,
    val controlsTimeout: Int = 4000,  // milliseconds
    val doubleTapSeekDuration: Int = 10,  // seconds
    val swipeGesturesEnabled: Boolean = true,
    val swipeBrightnessEnabled: Boolean = true,
    val swipeVolumeEnabled: Boolean = true,
    val swipeSeekEnabled: Boolean = true,
    val rememberPosition: Boolean = true,
    val autoPlayNext: Boolean = false,
    val skipIntroEnabled: Boolean = false,
    val skipIntroSeconds: Int = 0,
    val defaultAudioTrack: String = "auto",
    val subtitlesEnabled: Boolean = false,
    val subtitleSize: Float = 1.0f,
    val subtitleBackground: Boolean = false,
    val lastModified: Long = System.currentTimeMillis()
)

/**
 * Discovered playlist folder information
 * Used when scanning profile content folders for playlist subfolders
 */
data class DiscoveredPlaylist(
    val folderName: String,
    val folderPath: String,
    val contentType: ContentType,
    val mediaFileCount: Int,
    val dateCreated: Long
)

/**
 * Master profiles registry stored in /Librio/profiles.json
 * Contains list of all profiles and active profile tracking
 */
data class ProfileRegistry(
    val version: Int = 1,
    val lastModified: Long = System.currentTimeMillis(),
    val activeProfileId: String,
    val profiles: List<ProfileEntry>
)

/**
 * Individual profile entry in master profiles registry
 * References profile folder and avatar file
 */
data class ProfileEntry(
    val id: String,
    val name: String,
    val folderName: String,
    val avatarFile: String? = null,
    val dateCreated: Long = System.currentTimeMillis(),
    val lastAccessed: Long = System.currentTimeMillis()
)

/**
 * Playlist metadata stored as individual JSON files
 * Each playlist gets own file in Profiles/{ProfileName}/Playlists/{id}.json
 */
data class PlaylistMetadata(
    val version: Int = 1,
    val id: String,
    val name: String,
    val contentType: ContentType,
    val categoryId: String? = null,
    val order: Int = 0,
    val dateCreated: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis(),
    val folderPath: String,
    val items: List<PlaylistItem> = emptyList(),
    val metadata: PlaylistStats = PlaylistStats()
)

/**
 * Individual item within a playlist
 * Tracks media file URI, title, and order
 */
data class PlaylistItem(
    val uri: String,
    val title: String,
    val order: Int = 0,
    val dateAdded: Long = System.currentTimeMillis()
)

/**
 * Playlist statistics and metadata
 * Tracks item count, total duration, and last scan time
 */
data class PlaylistStats(
    val itemCount: Int = 0,
    val totalDuration: Long = 0,
    val lastScanned: Long = System.currentTimeMillis()
)

/**
 * Result of two-way sync between playlist JSON files and folder structure
 * Tracks added, removed, and modified playlists
 */
data class SyncResult(
    val added: List<PlaylistMetadata> = emptyList(),
    val removed: List<String> = emptyList(),
    val modified: List<PlaylistMetadata> = emptyList()
)

/**
 * File change detection result for tracking external modifications
 */
data class ChangedFile(
    val path: String,
    val type: FileType,
    val lastModified: Long
)

/**
 * Types of JSON files that can be reloaded
 * Used for file change detection and reload routing
 */
enum class FileType {
    PROFILE_SETTINGS,
    AUDIO_SETTINGS,
    READER_SETTINGS,
    COMIC_SETTINGS,
    MOVIE_SETTINGS,
    PROGRESS,
    PLAYLIST,
    MASTER_PROFILES
}
