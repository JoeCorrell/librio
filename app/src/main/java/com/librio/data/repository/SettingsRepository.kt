package com.librio.data.repository

import android.content.Context
import android.os.Environment
import com.librio.data.ProfileFileManager
import com.librio.model.AudioSettings
import com.librio.model.ComicSettings
import com.librio.model.MovieSettings
import com.librio.model.ProfileSettings
import com.librio.model.ReaderSettings
import com.librio.ui.screens.UserProfile
import com.librio.ui.theme.AppTheme
import com.librio.ui.theme.BackgroundTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Repository for app settings persistence
 */
@Suppress("UNUSED_PARAMETER")
class SettingsRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // File-based settings manager for per-profile JSON storage
    private val profileFileManager = ProfileFileManager()

    // Library repository for backup/restore of library data (categories, items, etc.)
    private val libraryRepository by lazy { LibraryRepository(context) }

    // Coroutine scope for async file operations
    private val fileScope = CoroutineScope(Dispatchers.IO)

    // Current profile name for per-profile settings - must be initialized early for profile-specific loading
    private var currentProfileName: String = run {
        val activeId = prefs.getString(KEY_ACTIVE_PROFILE_ID, "default") ?: "default"
        val profilesJson = prefs.getString(KEY_PROFILES, null)
        if (profilesJson.isNullOrEmpty()) {
            "Default"
        } else {
            try {
                profilesJson.split("|").mapNotNull { entry ->
                    val parts = if (entry.contains(";")) entry.split(";", limit = 14) else entry.split(":")
                    if (parts.size >= 2 && parts[0] == activeId) parts[1] else null
                }.firstOrNull() ?: "Default"
            } catch (e: Exception) {
                "Default"
            }
        }
    }

    /**
     * Generate a profile-specific key for settings storage
     */
    private fun getProfileKey(baseKey: String): String {
        val sanitized = currentProfileName.replace(Regex("[^a-zA-Z0-9]"), "_")
        return "${baseKey}_profile_${sanitized}"
    }

    /**
     * Pull the active profile's theme + dark mode straight from shared preferences.
     * Used at startup so the splash screen immediately reflects the correct theme.
     */
    private fun loadActiveProfileThemeFromPrefs(): Pair<AppTheme, Boolean>? {
        val profilesJson = prefs.getString(KEY_PROFILES, null) ?: return null
        val activeId = prefs.getString(KEY_ACTIVE_PROFILE_ID, "default") ?: "default"
        return profilesJson.split("|").firstNotNullOfOrNull { entry ->
            val parts = if (entry.contains(";")) entry.split(";", limit = 15) else entry.split(":")
            if (parts.size >= 4 && parts[0] == activeId) {
                val theme = try {
                    AppTheme.valueOf(parts[2])
                } catch (e: Exception) {
                    null
                }
                val dark = parts[3].toBooleanStrictOrNull() ?: false
                if (theme != null) theme to dark else null
            } else {
                null
            }
        }
    }

    // Profile folder structure
    private val librioRoot = File(Environment.getExternalStorageDirectory(), "Librio")
    private val profilesRoot = File(librioRoot, "Profiles")
    private val contentFolders = listOf("Audiobooks", "Books", "Music", "Creepypasta", "Comics", "Movies")

    /**
     * Get the profile folder for a given profile name
     */
    fun getProfileFolder(profileName: String): File {
        return File(profilesRoot, sanitizeFolderName(profileName))
    }

    /**
     * Get the content folder for a specific profile and content type
     */
    fun getProfileContentFolder(profileName: String, contentType: String): File {
        return File(getProfileFolder(profileName), contentType)
    }

    /**
     * Create folder structure for a profile
     */
    private fun createProfileFolders(profileName: String) {
        try {
            val profileFolder = getProfileFolder(profileName)
            if (!profileFolder.exists()) {
                profileFolder.mkdirs()
            }
            // Create content subfolders
            contentFolders.forEach { contentType ->
                val contentFolder = File(profileFolder, contentType)
                if (!contentFolder.exists()) {
                    contentFolder.mkdirs()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Rename profile folder when profile is renamed
     */
    private fun renameProfileFolder(oldName: String, newName: String) {
        try {
            val oldFolder = getProfileFolder(oldName)
            val newFolder = getProfileFolder(newName)
            if (oldFolder.exists() && !newFolder.exists()) {
                oldFolder.renameTo(newFolder)
            } else if (!oldFolder.exists()) {
                // Old folder doesn't exist, create new one
                createProfileFolders(newName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Delete profile folder when profile is deleted
     */
    private fun deleteProfileFolder(profileName: String) {
        try {
            val profileFolder = getProfileFolder(profileName)
            if (profileFolder.exists()) {
                profileFolder.deleteRecursively()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Sanitize folder name to be filesystem safe
     */
    private fun sanitizeFolderName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }

    /**
     * Normalize equalizer preset names to a small, predictable set so we can apply
     * consistent behavior even if legacy values are stored in preferences.
     */
    private fun normalizeEqualizerPreset(raw: String?): String {
        val cleaned = raw?.trim()?.uppercase().orEmpty()
        return when {
            cleaned in listOf("BASS_INCREASED", "BASSBOOST", "BASS BOOST", "BASS_INCRESSED", "BASS BOOSTED") -> "BASS_INCREASED"
            cleaned.contains("BASS") && cleaned.contains("INCREAS") -> "BASS_INCREASED"
            cleaned in listOf("BASS_REDUCED", "BASS_REDUCER") -> "BASS_REDUCED"
            cleaned.contains("BASS") && cleaned.contains("REDUC") -> "BASS_REDUCED"
            cleaned in listOf("TREBLE_INCREASED", "TREBLE BOOST", "TREBLEBOOST") -> "TREBLE_INCREASED"
            cleaned.contains("TREBLE") && cleaned.contains("INCREAS") -> "TREBLE_INCREASED"
            cleaned in listOf("TREBLE_REDUCED", "TREBLE_REDUCER") -> "TREBLE_REDUCED"
            cleaned.contains("TREBLE") && cleaned.contains("REDUC") -> "TREBLE_REDUCED"
            cleaned == "FLAT" -> "FLAT"
            cleaned in listOf("DEFAULT", "OFF", "NONE") -> "DEFAULT"
            else -> "DEFAULT"
        }
    }

    /**
     * Ensure default profile folder exists
     */
    fun ensureDefaultProfileFolder() {
        createProfileFolders("Default")
    }

    private val _appTheme = MutableStateFlow(loadTheme())
    val appTheme: StateFlow<AppTheme> = _appTheme.asStateFlow()

    private val _accentTheme = MutableStateFlow(loadAccentTheme())
    val accentTheme: StateFlow<AppTheme> = _accentTheme.asStateFlow()

    private val _profiles = MutableStateFlow(loadProfiles())
    val profiles: StateFlow<List<UserProfile>> = _profiles.asStateFlow()

    private val _activeProfileId = MutableStateFlow(loadActiveProfileId())
    val activeProfileId: StateFlow<String> = _activeProfileId.asStateFlow()

    private val _skipForwardDuration = MutableStateFlow(loadSkipForward())
    val skipForwardDuration: StateFlow<Int> = _skipForwardDuration.asStateFlow()

    private val _skipBackDuration = MutableStateFlow(loadSkipBack())
    val skipBackDuration: StateFlow<Int> = _skipBackDuration.asStateFlow()

    private val _autoBookmark = MutableStateFlow(loadAutoBookmark())
    val autoBookmark: StateFlow<Boolean> = _autoBookmark.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(loadKeepScreenOn())
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _volumeBoostEnabled = MutableStateFlow(loadVolumeBoost())
    val volumeBoostEnabled: StateFlow<Boolean> = _volumeBoostEnabled.asStateFlow()

    private val _volumeBoostLevel = MutableStateFlow(loadVolumeBoostLevel())
    val volumeBoostLevel: StateFlow<Float> = _volumeBoostLevel.asStateFlow()

    private val _libraryOwnerName = MutableStateFlow(loadLibraryOwnerName())
    val libraryOwnerName: StateFlow<String> = _libraryOwnerName.asStateFlow()

    private val _darkMode = MutableStateFlow(loadDarkMode())
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    private val _backgroundTheme = MutableStateFlow(loadBackgroundTheme())
    val backgroundTheme: StateFlow<BackgroundTheme> = _backgroundTheme.asStateFlow()

    // Additional global settings
    private val _playbackSpeed = MutableStateFlow(loadPlaybackSpeed())
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _sleepTimerMinutes = MutableStateFlow(loadSleepTimerMinutes())
    val sleepTimerMinutes: StateFlow<Int> = _sleepTimerMinutes.asStateFlow()

    private val _autoPlayNext = MutableStateFlow(loadAutoPlayNext())
    val autoPlayNext: StateFlow<Boolean> = _autoPlayNext.asStateFlow()

    private val _defaultLibraryView = MutableStateFlow(loadDefaultLibraryView())
    val defaultLibraryView: StateFlow<String> = _defaultLibraryView.asStateFlow()

    private val _defaultSortOrder = MutableStateFlow(loadDefaultSortOrder())
    val defaultSortOrder: StateFlow<String> = _defaultSortOrder.asStateFlow()

    private val _resumePlayback = MutableStateFlow(loadResumePlayback())
    val resumePlayback: StateFlow<Boolean> = _resumePlayback.asStateFlow()

    private val _showPlaybackNotification = MutableStateFlow(loadShowPlaybackNotification())
    val showPlaybackNotification: StateFlow<Boolean> = _showPlaybackNotification.asStateFlow()

    // E-Reader settings
    private val _readerFontSize = MutableStateFlow(loadReaderFontSize())
    val readerFontSize: StateFlow<Int> = _readerFontSize.asStateFlow()

    private val _readerLineSpacing = MutableStateFlow(loadReaderLineSpacing())
    val readerLineSpacing: StateFlow<Float> = _readerLineSpacing.asStateFlow()

    private val _readerTheme = MutableStateFlow(loadReaderTheme())
    val readerTheme: StateFlow<String> = _readerTheme.asStateFlow()

    private val _readerFont = MutableStateFlow(loadReaderFont())
    val readerFont: StateFlow<String> = _readerFont.asStateFlow()

    private val _readerTextAlign = MutableStateFlow(loadReaderTextAlign())
    val readerTextAlign: StateFlow<String> = _readerTextAlign.asStateFlow()

    private val _readerMargins = MutableStateFlow(loadReaderMargins())
    val readerMargins: StateFlow<Int> = _readerMargins.asStateFlow()

    private val _readerKeepScreenOn = MutableStateFlow(loadReaderKeepScreenOn())
    val readerKeepScreenOn: StateFlow<Boolean> = _readerKeepScreenOn.asStateFlow()

    // Additional e-reader settings
    private val _readerParagraphSpacing = MutableStateFlow(loadReaderParagraphSpacing())
    val readerParagraphSpacing: StateFlow<Int> = _readerParagraphSpacing.asStateFlow()

    private val _readerBrightness = MutableStateFlow(loadReaderBrightness())
    val readerBrightness: StateFlow<Float> = _readerBrightness.asStateFlow()

    private val _readerBoldText = MutableStateFlow(loadReaderBoldText())
    val readerBoldText: StateFlow<Boolean> = _readerBoldText.asStateFlow()

    private val _readerWordSpacing = MutableStateFlow(loadReaderWordSpacing())
    val readerWordSpacing: StateFlow<Int> = _readerWordSpacing.asStateFlow()

    private val _readerPageFitMode = MutableStateFlow(loadReaderPageFitMode())
    val readerPageFitMode: StateFlow<String> = _readerPageFitMode.asStateFlow()

    private val _readerPageGap = MutableStateFlow(loadReaderPageGap())
    val readerPageGap: StateFlow<Int> = _readerPageGap.asStateFlow()

    private val _readerForceTwoPage = MutableStateFlow(loadReaderForceTwoPage())
    val readerForceTwoPage: StateFlow<Boolean> = _readerForceTwoPage.asStateFlow()

    private val _readerForceSinglePage = MutableStateFlow(loadReaderForceSinglePage())
    val readerForceSinglePage: StateFlow<Boolean> = _readerForceSinglePage.asStateFlow()

    // Comic Reader settings (per-profile)
    private val _comicForceTwoPage = MutableStateFlow(loadComicForceTwoPage())
    val comicForceTwoPage: StateFlow<Boolean> = _comicForceTwoPage.asStateFlow()

    private val _comicForceSinglePage = MutableStateFlow(loadComicForceSinglePage())
    val comicForceSinglePage: StateFlow<Boolean> = _comicForceSinglePage.asStateFlow()

    private val _comicReadingDirection = MutableStateFlow(loadComicReadingDirection())
    val comicReadingDirection: StateFlow<String> = _comicReadingDirection.asStateFlow()

    private val _comicPageFitMode = MutableStateFlow(loadComicPageFitMode())
    val comicPageFitMode: StateFlow<String> = _comicPageFitMode.asStateFlow()

    private val _comicPageGap = MutableStateFlow(loadComicPageGap())
    val comicPageGap: StateFlow<Int> = _comicPageGap.asStateFlow()

    private val _comicBackgroundColor = MutableStateFlow(loadComicBackgroundColor())
    val comicBackgroundColor: StateFlow<String> = _comicBackgroundColor.asStateFlow()

    private val _comicShowPageIndicators = MutableStateFlow(loadComicShowPageIndicators())
    val comicShowPageIndicators: StateFlow<Boolean> = _comicShowPageIndicators.asStateFlow()

    private val _comicEnableDoubleTapZoom = MutableStateFlow(loadComicEnableDoubleTapZoom())
    val comicEnableDoubleTapZoom: StateFlow<Boolean> = _comicEnableDoubleTapZoom.asStateFlow()

    private val _comicShowControlsOnTap = MutableStateFlow(loadComicShowControlsOnTap())
    val comicShowControlsOnTap: StateFlow<Boolean> = _comicShowControlsOnTap.asStateFlow()

    // Movie Player settings (per-profile)
    private val _moviePlaybackSpeed = MutableStateFlow(loadMoviePlaybackSpeed())
    val moviePlaybackSpeed: StateFlow<Float> = _moviePlaybackSpeed.asStateFlow()

    private val _movieKeepScreenOn = MutableStateFlow(loadMovieKeepScreenOn())
    val movieKeepScreenOn: StateFlow<Boolean> = _movieKeepScreenOn.asStateFlow()

    private val _movieResizeMode = MutableStateFlow(loadMovieResizeMode())
    val movieResizeMode: StateFlow<String> = _movieResizeMode.asStateFlow()

    private val _movieBrightness = MutableStateFlow(loadMovieBrightness())
    val movieBrightness: StateFlow<Float> = _movieBrightness.asStateFlow()

    private val _movieAutoFullscreenLandscape = MutableStateFlow(loadMovieAutoFullscreenLandscape())
    val movieAutoFullscreenLandscape: StateFlow<Boolean> = _movieAutoFullscreenLandscape.asStateFlow()

    private val _movieShowControlsOnTap = MutableStateFlow(loadMovieShowControlsOnTap())
    val movieShowControlsOnTap: StateFlow<Boolean> = _movieShowControlsOnTap.asStateFlow()

    private val _movieControlsTimeout = MutableStateFlow(loadMovieControlsTimeout())
    val movieControlsTimeout: StateFlow<Int> = _movieControlsTimeout.asStateFlow()

    private val _movieDoubleTapSeekDuration = MutableStateFlow(loadMovieDoubleTapSeekDuration())
    val movieDoubleTapSeekDuration: StateFlow<Int> = _movieDoubleTapSeekDuration.asStateFlow()

    private val _movieSwipeGesturesEnabled = MutableStateFlow(loadMovieSwipeGesturesEnabled())
    val movieSwipeGesturesEnabled: StateFlow<Boolean> = _movieSwipeGesturesEnabled.asStateFlow()

    private val _movieRememberPosition = MutableStateFlow(loadMovieRememberPosition())
    val movieRememberPosition: StateFlow<Boolean> = _movieRememberPosition.asStateFlow()

    // Audio Enhancement settings
    private val _normalizeAudio = MutableStateFlow(loadNormalizeAudio())
    val normalizeAudio: StateFlow<Boolean> = _normalizeAudio.asStateFlow()

    private val _bassBoostLevel = MutableStateFlow(loadBassBoostLevel())
    val bassBoostLevel: StateFlow<Int> = _bassBoostLevel.asStateFlow()

    private val _equalizerPreset = MutableStateFlow(loadEqualizerPreset())
    val equalizerPreset: StateFlow<String> = _equalizerPreset.asStateFlow()

    private val _crossfadeDuration = MutableStateFlow(loadCrossfadeDuration())
    val crossfadeDuration: StateFlow<Int> = _crossfadeDuration.asStateFlow()

    // Playback Control settings
    private val _autoRewindSeconds = MutableStateFlow(loadAutoRewindSeconds())
    val autoRewindSeconds: StateFlow<Int> = _autoRewindSeconds.asStateFlow()

    private val _headsetControls = MutableStateFlow(loadHeadsetControls())
    val headsetControls: StateFlow<Boolean> = _headsetControls.asStateFlow()

    private val _pauseOnDisconnect = MutableStateFlow(loadPauseOnDisconnect())
    val pauseOnDisconnect: StateFlow<Boolean> = _pauseOnDisconnect.asStateFlow()

    // Display settings
    private val _showPlaceholderIcons = MutableStateFlow(loadShowPlaceholderIcons())
    val showPlaceholderIcons: StateFlow<Boolean> = _showPlaceholderIcons.asStateFlow()

    private val _showFileSize = MutableStateFlow(loadShowFileSize())
    val showFileSize: StateFlow<Boolean> = _showFileSize.asStateFlow()

    private val _showDuration = MutableStateFlow(loadShowDuration())
    val showDuration: StateFlow<Boolean> = _showDuration.asStateFlow()

    private val _animationSpeed = MutableStateFlow(loadAnimationSpeed())
    val animationSpeed: StateFlow<String> = _animationSpeed.asStateFlow()

    private val _hapticFeedback = MutableStateFlow(loadHapticFeedback())
    val hapticFeedback: StateFlow<Boolean> = _hapticFeedback.asStateFlow()

    private val _confirmBeforeDelete = MutableStateFlow(loadConfirmBeforeDelete())
    val confirmBeforeDelete: StateFlow<Boolean> = _confirmBeforeDelete.asStateFlow()

    private val _useSquareCorners = MutableStateFlow(loadUseSquareCorners())
    val useSquareCorners: StateFlow<Boolean> = _useSquareCorners.asStateFlow()

    private val _rememberLastPosition = MutableStateFlow(loadRememberLastPosition())
    val rememberLastPosition: StateFlow<Boolean> = _rememberLastPosition.asStateFlow()

    // Filter persistence
    private val _selectedContentType = MutableStateFlow(loadSelectedContentType())
    val selectedContentType: StateFlow<String> = _selectedContentType.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow(loadSelectedCategoryId())
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    // Collapsed playlist/series persistence (per-profile)
    private val _collapsedSeries = MutableStateFlow(loadCollapsedSeries())
    val collapsedSeries: StateFlow<Set<String>> = _collapsedSeries.asStateFlow()

    // UI Visibility settings
    private val _showBackButton = MutableStateFlow(loadShowBackButton())
    val showBackButton: StateFlow<Boolean> = _showBackButton.asStateFlow()

    private val _showSearchBar = MutableStateFlow(loadShowSearchBar())
    val showSearchBar: StateFlow<Boolean> = _showSearchBar.asStateFlow()

    // Custom theme colors
    private val _customPrimaryColor = MutableStateFlow(loadCustomPrimaryColor())
    val customPrimaryColor: StateFlow<Int> = _customPrimaryColor.asStateFlow()

    private val _customAccentColor = MutableStateFlow(loadCustomAccentColor())
    val customAccentColor: StateFlow<Int> = _customAccentColor.asStateFlow()

    private val _customBackgroundColor = MutableStateFlow(loadCustomBackgroundColor())
    val customBackgroundColor: StateFlow<Int> = _customBackgroundColor.asStateFlow()

    // Global app scale (UI zoom)
    private val _appScale = MutableStateFlow(loadAppScale())
    val appScale: StateFlow<Float> = _appScale.asStateFlow()

    // UI font settings (per profile)
    private val _uiFontScale = MutableStateFlow(loadUiFontScale())
    val uiFontScale: StateFlow<Float> = _uiFontScale.asStateFlow()

    private val _uiFontFamily = MutableStateFlow(loadUiFontFamily())
    val uiFontFamily: StateFlow<String> = _uiFontFamily.asStateFlow()

    // Persisted playback state (per profile)
    private val _lastMusicId = MutableStateFlow(loadString(KEY_LAST_MUSIC_ID))
    val lastMusicId: StateFlow<String?> = _lastMusicId.asStateFlow()

    private val _lastMusicPosition = MutableStateFlow(loadLong(KEY_LAST_MUSIC_POSITION, 0L))
    val lastMusicPosition: StateFlow<Long> = _lastMusicPosition.asStateFlow()

    private val _lastMusicPlaying = MutableStateFlow(loadBoolean(KEY_LAST_MUSIC_PLAYING, false))
    val lastMusicPlaying: StateFlow<Boolean> = _lastMusicPlaying.asStateFlow()

    private val _lastAudiobookId = MutableStateFlow(loadString(KEY_LAST_AUDIOBOOK_ID))
    val lastAudiobookId: StateFlow<String?> = _lastAudiobookId.asStateFlow()

    private val _lastAudiobookPosition = MutableStateFlow(loadLong(KEY_LAST_AUDIOBOOK_POSITION, 0L))
    val lastAudiobookPosition: StateFlow<Long> = _lastAudiobookPosition.asStateFlow()

    private val _lastAudiobookPlaying = MutableStateFlow(loadBoolean(KEY_LAST_AUDIOBOOK_PLAYING, false))
    val lastAudiobookPlaying: StateFlow<Boolean> = _lastAudiobookPlaying.asStateFlow()

    private val _lastActiveType = MutableStateFlow(loadString(KEY_LAST_ACTIVE_TYPE))
    val lastActiveType: StateFlow<String?> = _lastActiveType.asStateFlow()

    // Music playback mode settings
    private val _musicShuffleEnabled = MutableStateFlow(loadBoolean(KEY_MUSIC_SHUFFLE_ENABLED, false))
    val musicShuffleEnabled: StateFlow<Boolean> = _musicShuffleEnabled.asStateFlow()

    private val _musicRepeatMode = MutableStateFlow(loadInt(KEY_MUSIC_REPEAT_MODE, 0))
    val musicRepeatMode: StateFlow<Int> = _musicRepeatMode.asStateFlow()

    init {
        // Initialize CustomThemeColors from saved values
        updateCustomThemeColors()
        // Load settings from JSON files (they take priority over SharedPreferences)
        loadAllSettingsFromFiles()
        // Import any profile backups dropped into Librio/Profiles
        importProfileBackupsAsync()
    }

    private data class ProfileBackupData(
        val profileId: String?,
        val profileName: String,
        val profileSettings: JSONObject?,
        val audioSettings: JSONObject?,
        val readerSettings: JSONObject?,
        val comicSettings: JSONObject?,
        val movieSettings: JSONObject?,
        val progressSettings: JSONObject?,
        val playlistFiles: List<PlaylistBackupFile>,
        val libraryData: JSONObject? = null  // Library data (categories, audiobooks, books, etc.)
    )

    private data class PlaylistBackupFile(
        val fileName: String,
        val content: JSONObject
    )

    private data class ProfileBackupImportResult(
        val profiles: List<UserProfile>,
        val activeProfileUpdated: Boolean,
        val importedCount: Int
    )

    private fun importProfileBackupsAsync() {
        fileScope.launch {
            val result = importProfileBackups(_profiles.value)
            if (result.importedCount > 0) {
                _profiles.value = result.profiles
                saveProfiles(result.profiles)
                if (result.activeProfileUpdated) {
                    loadAllSettingsFromFiles()
                }
            }
        }
    }

    private suspend fun importProfileBackups(existingProfiles: List<UserProfile>): ProfileBackupImportResult {
        val pendingBackups = profileFileManager.listPendingBackupFiles()
        if (pendingBackups.isEmpty()) {
            return ProfileBackupImportResult(existingProfiles, false, 0)
        }

        val updatedProfiles = existingProfiles.toMutableList()
        var activeProfileUpdated = false
        var importedCount = 0
        val activeProfileId = loadActiveProfileId()

        pendingBackups.forEach { backupFile ->
            val backup = parseProfileBackup(backupFile) ?: return@forEach
            val existingIndex = updatedProfiles.indexOfFirst { profile ->
                val idMatches = backup.profileId != null && profile.id == backup.profileId
                val nameMatches = profile.name.equals(backup.profileName, ignoreCase = true)
                idMatches || nameMatches
            }
            val existingProfile = updatedProfiles.getOrNull(existingIndex)
            val resolvedId = existingProfile?.id ?: backup.profileId ?: UUID.randomUUID().toString()
            val resolvedName = existingProfile?.name ?: backup.profileName
            val isActive = existingProfile?.isActive ?: false

            createProfileFolders(resolvedName)

            backup.profileSettings?.apply {
                put("id", resolvedId)
                put("name", resolvedName)
            }

            backup.profileSettings?.let {
                profileFileManager.writeSettingsJson(resolvedName, ProfileFileManager.PROFILE_SETTINGS_FILE, it)
            }
            backup.audioSettings?.let {
                profileFileManager.writeSettingsJson(resolvedName, ProfileFileManager.AUDIO_SETTINGS_FILE, it)
            }
            backup.readerSettings?.let {
                profileFileManager.writeSettingsJson(resolvedName, ProfileFileManager.READER_SETTINGS_FILE, it)
            }
            backup.comicSettings?.let {
                profileFileManager.writeSettingsJson(resolvedName, ProfileFileManager.COMIC_SETTINGS_FILE, it)
            }
            backup.movieSettings?.let {
                profileFileManager.writeSettingsJson(resolvedName, ProfileFileManager.MOVIE_SETTINGS_FILE, it)
            }

            if (backup.progressSettings != null) {
                backup.progressSettings.put("profile", resolvedName)
                profileFileManager.writeSettingsJson(resolvedName, ProfileFileManager.PROGRESS_FILE, backup.progressSettings)
            }

            if (backup.playlistFiles.isNotEmpty()) {
                val playlistsFolder = profileFileManager.getPlaylistsFolder(resolvedName)
                playlistsFolder.mkdirs()
                backup.playlistFiles.forEach { playlistFile ->
                    val safeFileName = File(playlistFile.fileName).name
                    val file = File(playlistsFolder, safeFileName)
                    profileFileManager.writeJsonFile(file, playlistFile.content)
                }
            }

            // Restore library data (categories, audiobooks, books, music, comics, movies, series)
            backup.libraryData?.let { libraryData ->
                libraryRepository.importLibraryDataForProfile(resolvedName, libraryData)
            }

            if (existingProfile == null) {
                if (backup.profileSettings == null) {
                    profileFileManager.saveProfileSettings(
                        resolvedName,
                        ProfileSettings(id = resolvedId, name = resolvedName, libraryOwnerName = resolvedName)
                    )
                }
                if (backup.audioSettings == null) {
                    profileFileManager.saveAudioSettings(resolvedName, AudioSettings())
                }
                if (backup.readerSettings == null) {
                    profileFileManager.saveReaderSettings(resolvedName, ReaderSettings())
                }
                if (backup.comicSettings == null) {
                    profileFileManager.saveComicSettings(resolvedName, ComicSettings())
                }
                if (backup.movieSettings == null) {
                    profileFileManager.saveMovieSettings(resolvedName, MovieSettings())
                }
            }

            val mergedProfile = buildUserProfileFromBackup(
                baseProfile = existingProfile,
                profileId = resolvedId,
                profileName = resolvedName,
                profileSettings = backup.profileSettings,
                audioSettings = backup.audioSettings,
                isActive = isActive
            )

            if (existingIndex >= 0) {
                updatedProfiles[existingIndex] = mergedProfile
            } else {
                updatedProfiles.add(mergedProfile)
            }

            if (mergedProfile.isActive || resolvedId == activeProfileId) {
                activeProfileUpdated = true
            }

            if (profileFileManager.moveBackupToImported(backupFile) != null) {
                importedCount += 1
            }
        }

        return ProfileBackupImportResult(updatedProfiles, activeProfileUpdated, importedCount)
    }

    private fun parseProfileBackup(backupFile: File): ProfileBackupData? {
        val root = profileFileManager.readJsonFile(backupFile) ?: return null
        val schemaVersion = root.optInt("schemaVersion", 0)
        if (schemaVersion <= 0) return null

        val settingsContainer = root.optJSONObject("settings") ?: root
        val dataContainer = root.optJSONObject("data") ?: root
        val profileSettings = settingsContainer.optJSONObject("profileSettings")
            ?: settingsContainer.optJSONObject("profile")
            ?: root.optJSONObject("profileSettings")
        val audioSettings = settingsContainer.optJSONObject("audioSettings")
            ?: root.optJSONObject("audioSettings")
        val readerSettings = settingsContainer.optJSONObject("readerSettings")
            ?: root.optJSONObject("readerSettings")
        val comicSettings = settingsContainer.optJSONObject("comicSettings")
            ?: root.optJSONObject("comicSettings")
        val movieSettings = settingsContainer.optJSONObject("movieSettings")
            ?: root.optJSONObject("movieSettings")
        val progressSettings = dataContainer.optJSONObject("progress")
            ?: root.optJSONObject("progress")

        val playlistFiles = mutableListOf<PlaylistBackupFile>()
        val playlistsArray = dataContainer.optJSONArray("playlists")
            ?: root.optJSONArray("playlists")
        if (playlistsArray != null) {
            for (index in 0 until playlistsArray.length()) {
                val entry = playlistsArray.optJSONObject(index) ?: continue
                val fileName = entry.optString("fileName").takeIf { it.isNotBlank() } ?: continue
                val content = entry.optJSONObject("content") ?: continue
                playlistFiles.add(PlaylistBackupFile(fileName = fileName, content = content))
            }
        }

        // Extract library data (categories, audiobooks, books, music, comics, movies, series)
        val libraryData = dataContainer.optJSONObject("library")
            ?: root.optJSONObject("library")

        val profileName = root.optString("profileName").ifBlank {
            profileSettings?.optString("name").orEmpty()
        }.ifBlank {
            backupFile.name.removeSuffix(ProfileFileManager.PROFILE_BACKUP_EXTENSION)
        }.ifBlank {
            "Profile"
        }
        val profileId = root.optString("profileId").ifBlank {
            profileSettings?.optString("id").orEmpty()
        }.ifBlank {
            null
        }

        return ProfileBackupData(
            profileId = profileId,
            profileName = profileName,
            profileSettings = profileSettings,
            audioSettings = audioSettings,
            readerSettings = readerSettings,
            comicSettings = comicSettings,
            movieSettings = movieSettings,
            progressSettings = progressSettings,
            playlistFiles = playlistFiles,
            libraryData = libraryData
        )
    }

    private fun buildUserProfileFromBackup(
        baseProfile: UserProfile?,
        profileId: String,
        profileName: String,
        profileSettings: JSONObject?,
        audioSettings: JSONObject?,
        isActive: Boolean
    ): UserProfile {
        val theme = profileSettings?.optString("theme").takeIf { !it.isNullOrBlank() }
            ?: baseProfile?.theme
            ?: AppTheme.DARK_TEAL.name
        val darkMode = profileSettings?.optBoolean("darkMode", baseProfile?.darkMode ?: false)
            ?: baseProfile?.darkMode
            ?: false
        val profilePicture = profileSettings?.optString("profilePicture").takeIf { !it.isNullOrBlank() }
            ?: baseProfile?.profilePicture

        val playbackSpeed = audioSettings?.optDouble("playbackSpeed", baseProfile?.playbackSpeed?.toDouble() ?: 1.0)?.toFloat()
            ?: baseProfile?.playbackSpeed
            ?: 1.0f
        val skipForwardDuration = audioSettings?.optInt("skipForwardDuration", baseProfile?.skipForwardDuration ?: 30)
            ?: baseProfile?.skipForwardDuration
            ?: 30
        val skipBackDuration = audioSettings?.optInt("skipBackDuration", baseProfile?.skipBackDuration ?: 10)
            ?: baseProfile?.skipBackDuration
            ?: 10
        val volumeBoostEnabled = audioSettings?.optBoolean("volumeBoostEnabled", baseProfile?.volumeBoostEnabled ?: false)
            ?: baseProfile?.volumeBoostEnabled
            ?: false
        val volumeBoostLevel = audioSettings?.optDouble("volumeBoostLevel", baseProfile?.volumeBoostLevel?.toDouble() ?: 1.0)?.toFloat()
            ?: baseProfile?.volumeBoostLevel
            ?: 1.0f
        val normalizeAudio = audioSettings?.optBoolean("normalizeAudio", baseProfile?.normalizeAudio ?: false)
            ?: baseProfile?.normalizeAudio
            ?: false
        val bassBoostLevel = audioSettings?.optDouble("bassBoostLevel", baseProfile?.bassBoostLevel?.toDouble() ?: 0.0)?.toFloat()
            ?: baseProfile?.bassBoostLevel
            ?: 0f
        val equalizerPreset = normalizeEqualizerPreset(
            audioSettings?.optString("equalizerPreset", baseProfile?.equalizerPreset ?: "DEFAULT")
        )
        val sleepTimerMinutes = audioSettings?.optInt("sleepTimerMinutes", baseProfile?.sleepTimerMinutes ?: 0)
            ?: baseProfile?.sleepTimerMinutes
            ?: 0

        return UserProfile(
            id = profileId,
            name = profileName,
            isActive = isActive,
            theme = theme,
            darkMode = darkMode,
            profilePicture = profilePicture,
            playbackSpeed = playbackSpeed,
            skipForwardDuration = skipForwardDuration,
            skipBackDuration = skipBackDuration,
            volumeBoostEnabled = volumeBoostEnabled,
            volumeBoostLevel = volumeBoostLevel,
            normalizeAudio = normalizeAudio,
            bassBoostLevel = bassBoostLevel,
            equalizerPreset = equalizerPreset,
            sleepTimerMinutes = sleepTimerMinutes
        )
    }

    suspend fun exportProfileBackup(profileId: String): File? = withContext(Dispatchers.IO) {
        val profile = _profiles.value.firstOrNull { it.id == profileId } ?: return@withContext null
        val profileName = profile.name
        val backupsFolder = profileFileManager.getBackupsFolder()
        backupsFolder.mkdirs()

        val baseName = profileFileManager.sanitizeFolderName(profileName).ifBlank { "Profile" }
        var backupFile = File(backupsFolder, "$baseName${ProfileFileManager.PROFILE_BACKUP_EXTENSION}")
        if (backupFile.exists()) {
            val suffix = System.currentTimeMillis().toString()
            backupFile = File(backupsFolder, "${baseName}_$suffix${ProfileFileManager.PROFILE_BACKUP_EXTENSION}")
        }

        val profileSettings = profileFileManager.readSettingsJson(profileName, ProfileFileManager.PROFILE_SETTINGS_FILE)
        val audioSettings = profileFileManager.readSettingsJson(profileName, ProfileFileManager.AUDIO_SETTINGS_FILE)
        val readerSettings = profileFileManager.readSettingsJson(profileName, ProfileFileManager.READER_SETTINGS_FILE)
        val comicSettings = profileFileManager.readSettingsJson(profileName, ProfileFileManager.COMIC_SETTINGS_FILE)
        val movieSettings = profileFileManager.readSettingsJson(profileName, ProfileFileManager.MOVIE_SETTINGS_FILE)

        val settingsObject = JSONObject().apply {
            profileSettings?.let { put("profileSettings", it) }
            audioSettings?.let { put("audioSettings", it) }
            readerSettings?.let { put("readerSettings", it) }
            comicSettings?.let { put("comicSettings", it) }
            movieSettings?.let { put("movieSettings", it) }
        }

        val progressFile = profileFileManager.getProgressFile(profileName)
        val progressJson = if (progressFile.exists()) {
            profileFileManager.readJsonFile(progressFile)
        } else {
            null
        }

        val playlistsFolder = profileFileManager.getPlaylistsFolder(profileName)
        val playlistsArray = org.json.JSONArray().apply {
            if (playlistsFolder.exists()) {
                playlistsFolder.listFiles { file -> file.extension == "json" }?.forEach { file ->
                    val playlistJson = profileFileManager.readJsonFile(file) ?: return@forEach
                    val entry = JSONObject().apply {
                        put("fileName", file.name)
                        put("content", playlistJson)
                    }
                    put(entry)
                }
            }
        }

        // Export library data (categories, audiobooks, books, music, comics, movies, series)
        val libraryData = libraryRepository.exportLibraryDataForProfile(profileName)

        val dataObject = JSONObject().apply {
            progressJson?.let { put("progress", it) }
            if (playlistsArray.length() > 0) {
                put("playlists", playlistsArray)
            }
            if (libraryData.length() > 0) {
                put("library", libraryData)
            }
        }

        val backupJson = JSONObject().apply {
            put("schemaVersion", 2) // Bumped version to indicate library data support
            put("createdAt", System.currentTimeMillis())
            put("profileName", profileName)
            put("profileId", profile.id)
            put("settings", settingsObject)
            if (dataObject.length() > 0) {
                put("data", dataObject)
            }
        }

        profileFileManager.writeJsonFile(backupFile, backupJson)
        if (backupFile.exists()) backupFile else null
    }

    private fun updateCustomThemeColors() {
        com.librio.ui.theme.CustomThemeColors.primaryColor = androidx.compose.ui.graphics.Color(_customPrimaryColor.value.toLong() or 0xFF000000L)
        com.librio.ui.theme.CustomThemeColors.accentColor = androidx.compose.ui.graphics.Color(_customAccentColor.value.toLong() or 0xFF000000L)
        com.librio.ui.theme.CustomThemeColors.backgroundColor = androidx.compose.ui.graphics.Color(_customBackgroundColor.value.toLong() or 0xFF000000L)
    }

    // ==================== File-Based Settings Loading ====================

    /**
     * Load all settings from JSON files - JSON files are the source of truth
     * This allows users to edit the JSON files externally and have the app respect those changes
     */
    fun loadAllSettingsFromFiles() {
        fileScope.launch {
            loadProfileSettingsFromFile()
            loadAudioSettingsFromFile()
            loadReaderSettingsFromFile()
            loadComicSettingsFromFile()
            loadMovieSettingsFromFile()
        }
    }

    /**
     * Load profile settings from JSON file and update StateFlows
     */
    suspend fun loadProfileSettingsFromFile() {
        val settings = profileFileManager.loadProfileSettings(currentProfileName) ?: return

        // Update StateFlows with loaded values
        _appTheme.value = try { AppTheme.valueOf(settings.theme) } catch (e: Exception) { AppTheme.DARK_TEAL }
        _darkMode.value = settings.darkMode
        _accentTheme.value = try { AppTheme.valueOf(settings.accentTheme) } catch (e: Exception) { AppTheme.DARK_TEAL }
        _backgroundTheme.value = try { BackgroundTheme.valueOf(settings.backgroundTheme) } catch (e: Exception) { BackgroundTheme.WHITE }
        _customPrimaryColor.value = settings.customPrimaryColor
        _customAccentColor.value = settings.customAccentColor
        _customBackgroundColor.value = settings.customBackgroundColor
        _appScale.value = settings.appScale
        _uiFontScale.value = settings.uiFontScale
        _uiFontFamily.value = settings.uiFontFamily
        _libraryOwnerName.value = settings.libraryOwnerName
        _defaultLibraryView.value = settings.defaultLibraryView
        _defaultSortOrder.value = settings.defaultSortOrder
        _showPlaceholderIcons.value = settings.showPlaceholderIcons
        _showFileSize.value = settings.showFileSize
        _showDuration.value = settings.showDuration
        _showBackButton.value = settings.showBackButton
        _showSearchBar.value = settings.showSearchBar
        _animationSpeed.value = settings.animationSpeed
        _hapticFeedback.value = settings.hapticFeedback
        _confirmBeforeDelete.value = settings.confirmBeforeDelete
        _useSquareCorners.value = settings.useSquareCorners
        _collapsedSeries.value = settings.collapsedSeries.toSet()
        _selectedContentType.value = settings.selectedContentType

        // Update custom theme colors
        updateCustomThemeColors()

        // Also update SharedPreferences to keep them in sync
        prefs.edit().apply {
            putString(getProfileKey(KEY_THEME), settings.theme)
            putBoolean(getProfileKey(KEY_DARK_MODE), settings.darkMode)
            putString(getProfileKey(KEY_ACCENT_THEME), settings.accentTheme)
            putString(getProfileKey(KEY_BACKGROUND_THEME), settings.backgroundTheme)
            putInt(getProfileKey(KEY_CUSTOM_PRIMARY_COLOR), settings.customPrimaryColor)
            putInt(getProfileKey(KEY_CUSTOM_ACCENT_COLOR), settings.customAccentColor)
            putInt(getProfileKey(KEY_CUSTOM_BACKGROUND_COLOR), settings.customBackgroundColor)
            putFloat(getProfileKey(KEY_APP_SCALE), settings.appScale)
            putFloat(getProfileKey(KEY_UI_FONT_SCALE), settings.uiFontScale)
            putString(getProfileKey(KEY_UI_FONT_FAMILY), settings.uiFontFamily)
            putString(getProfileKey(KEY_LIBRARY_OWNER_NAME), settings.libraryOwnerName)
            putString(getProfileKey(KEY_DEFAULT_LIBRARY_VIEW), settings.defaultLibraryView)
            putString(getProfileKey(KEY_DEFAULT_SORT_ORDER), settings.defaultSortOrder)
            putBoolean(getProfileKey(KEY_SHOW_PLACEHOLDER_ICONS), settings.showPlaceholderIcons)
            putBoolean(getProfileKey(KEY_SHOW_FILE_SIZE), settings.showFileSize)
            putBoolean(getProfileKey(KEY_SHOW_DURATION), settings.showDuration)
            putBoolean(getProfileKey(KEY_SHOW_BACK_BUTTON), settings.showBackButton)
            putBoolean(getProfileKey(KEY_SHOW_SEARCH_BAR), settings.showSearchBar)
            putString(getProfileKey(KEY_ANIMATION_SPEED), settings.animationSpeed)
            putBoolean(getProfileKey(KEY_HAPTIC_FEEDBACK), settings.hapticFeedback)
            putBoolean(getProfileKey(KEY_CONFIRM_BEFORE_DELETE), settings.confirmBeforeDelete)
            putBoolean(getProfileKey(KEY_USE_SQUARE_CORNERS), settings.useSquareCorners)
            putString(getProfileKey(KEY_SELECTED_CONTENT_TYPE), settings.selectedContentType)
            apply()
        }
    }

    /**
     * Load audio settings from JSON file and update StateFlows
     */
    suspend fun loadAudioSettingsFromFile() {
        val settings = profileFileManager.loadAudioSettings(currentProfileName) ?: return

        // Update StateFlows with loaded values
        _playbackSpeed.value = settings.playbackSpeed
        _skipForwardDuration.value = settings.skipForwardDuration
        _skipBackDuration.value = settings.skipBackDuration
        _sleepTimerMinutes.value = settings.sleepTimerMinutes
        _autoBookmark.value = settings.autoBookmark
        _keepScreenOn.value = settings.keepScreenOn
        _autoPlayNext.value = settings.autoPlayNext
        _resumePlayback.value = settings.resumePlayback
        _rememberLastPosition.value = settings.rememberLastPosition
        _autoRewindSeconds.value = settings.autoRewindSeconds
        _volumeBoostEnabled.value = settings.volumeBoostEnabled
        _volumeBoostLevel.value = settings.volumeBoostLevel
        _normalizeAudio.value = settings.normalizeAudio
        _bassBoostLevel.value = settings.bassBoostLevel.toInt()
        _equalizerPreset.value = normalizeEqualizerPreset(settings.equalizerPreset)
        _crossfadeDuration.value = settings.crossfadeDuration
        _headsetControls.value = settings.headsetControls
        _pauseOnDisconnect.value = settings.pauseOnDisconnect
        _showPlaybackNotification.value = settings.showPlaybackNotification
        val prefsLastMusicId = _lastMusicId.value
        val prefsLastAudiobookId = _lastAudiobookId.value
        val prefsLastActiveType = _lastActiveType.value
        val resolvedLastMusicId = prefsLastMusicId ?: settings.lastMusicId
        val resolvedLastAudiobookId = prefsLastAudiobookId ?: settings.lastAudiobookId
        val resolvedLastMusicPosition = if (prefsLastMusicId != null) {
            _lastMusicPosition.value
        } else {
            settings.lastMusicPosition
        }
        val resolvedLastAudiobookPosition = if (prefsLastAudiobookId != null) {
            _lastAudiobookPosition.value
        } else {
            settings.lastAudiobookPosition
        }
        val resolvedLastMusicPlaying = if (prefsLastMusicId != null) {
            _lastMusicPlaying.value
        } else {
            settings.lastMusicPlaying
        }
        val resolvedLastAudiobookPlaying = if (prefsLastAudiobookId != null) {
            _lastAudiobookPlaying.value
        } else {
            settings.lastAudiobookPlaying
        }
        val resolvedLastActiveType = prefsLastActiveType ?: settings.lastActiveType

        _lastMusicId.value = resolvedLastMusicId
        _lastMusicPosition.value = resolvedLastMusicPosition
        _lastMusicPlaying.value = resolvedLastMusicPlaying
        _lastAudiobookId.value = resolvedLastAudiobookId
        _lastAudiobookPosition.value = resolvedLastAudiobookPosition
        _lastAudiobookPlaying.value = resolvedLastAudiobookPlaying
        _lastActiveType.value = resolvedLastActiveType
        // Prefer SharedPreferences value for shuffle/repeat since it's committed synchronously
        // and more likely to be up-to-date than async JSON file writes
        val prefsShuffleEnabled = prefs.getBoolean(getProfileKey(KEY_MUSIC_SHUFFLE_ENABLED), settings.musicShuffleEnabled)
        val prefsRepeatMode = prefs.getInt(getProfileKey(KEY_MUSIC_REPEAT_MODE), settings.musicRepeatMode)
        _musicShuffleEnabled.value = prefsShuffleEnabled
        _musicRepeatMode.value = prefsRepeatMode

        // Also update SharedPreferences to keep them in sync
        prefs.edit().apply {
            putFloat(getProfileKey(KEY_PLAYBACK_SPEED), settings.playbackSpeed)
            putInt(getProfileKey(KEY_SKIP_FORWARD), settings.skipForwardDuration)
            putInt(getProfileKey(KEY_SKIP_BACK), settings.skipBackDuration)
            putInt(getProfileKey(KEY_SLEEP_TIMER_MINUTES), settings.sleepTimerMinutes)
            putBoolean(getProfileKey(KEY_AUTO_BOOKMARK), settings.autoBookmark)
            putBoolean(getProfileKey(KEY_KEEP_SCREEN_ON), settings.keepScreenOn)
            putBoolean(getProfileKey(KEY_AUTO_PLAY_NEXT), settings.autoPlayNext)
            putBoolean(getProfileKey(KEY_RESUME_PLAYBACK), settings.resumePlayback)
            putBoolean(getProfileKey(KEY_REMEMBER_LAST_POSITION), settings.rememberLastPosition)
            putInt(getProfileKey(KEY_AUTO_REWIND_SECONDS), settings.autoRewindSeconds)
            putBoolean(KEY_VOLUME_BOOST_ENABLED, settings.volumeBoostEnabled)
            putFloat(KEY_VOLUME_BOOST_LEVEL, settings.volumeBoostLevel)
            putBoolean(KEY_NORMALIZE_AUDIO, settings.normalizeAudio)
            putInt(KEY_BASS_BOOST_LEVEL, settings.bassBoostLevel.toInt())
            putString(KEY_EQUALIZER_PRESET, normalizeEqualizerPreset(settings.equalizerPreset))
            putInt(KEY_CROSSFADE_DURATION, settings.crossfadeDuration)
            putBoolean(getProfileKey(KEY_HEADSET_CONTROLS), settings.headsetControls)
            putBoolean(getProfileKey(KEY_PAUSE_ON_DISCONNECT), settings.pauseOnDisconnect)
            putBoolean(getProfileKey(KEY_SHOW_PLAYBACK_NOTIFICATION), settings.showPlaybackNotification)
            putString(getProfileKey(KEY_LAST_MUSIC_ID), resolvedLastMusicId)
            putLong(getProfileKey(KEY_LAST_MUSIC_POSITION), resolvedLastMusicPosition)
            putBoolean(getProfileKey(KEY_LAST_MUSIC_PLAYING), resolvedLastMusicPlaying)
            putString(getProfileKey(KEY_LAST_AUDIOBOOK_ID), resolvedLastAudiobookId)
            putLong(getProfileKey(KEY_LAST_AUDIOBOOK_POSITION), resolvedLastAudiobookPosition)
            putBoolean(getProfileKey(KEY_LAST_AUDIOBOOK_PLAYING), resolvedLastAudiobookPlaying)
            if (resolvedLastActiveType != null) {
                putString(getProfileKey(KEY_LAST_ACTIVE_TYPE), resolvedLastActiveType)
            } else {
                remove(getProfileKey(KEY_LAST_ACTIVE_TYPE))
            }
            putBoolean(getProfileKey(KEY_MUSIC_SHUFFLE_ENABLED), prefsShuffleEnabled)
            putInt(getProfileKey(KEY_MUSIC_REPEAT_MODE), prefsRepeatMode)
            apply()
        }
    }

    /**
     * Load reader settings from JSON file and update StateFlows
     */
    suspend fun loadReaderSettingsFromFile() {
        val settings = profileFileManager.loadReaderSettings(currentProfileName) ?: return

        // Update StateFlows with loaded values
        _readerFontSize.value = settings.fontSize
        _readerLineSpacing.value = settings.lineSpacing
        _readerTheme.value = settings.readerTheme
        _readerFont.value = settings.fontFamily
        _readerTextAlign.value = settings.textAlignment
        _readerMargins.value = settings.margins
        _readerParagraphSpacing.value = settings.paragraphSpacing
        _readerBrightness.value = settings.brightness
        _readerBoldText.value = settings.boldText
        _readerWordSpacing.value = settings.wordSpacing
        _readerPageFitMode.value = settings.pageFitMode
        _readerPageGap.value = settings.pageGap
        _readerForceTwoPage.value = settings.forceTwoPage
        _readerForceSinglePage.value = settings.forceSinglePage
        _readerKeepScreenOn.value = settings.keepScreenOn

        // Also update SharedPreferences to keep them in sync
        prefs.edit().apply {
            putInt(getProfileKey(KEY_READER_FONT_SIZE), settings.fontSize)
            putFloat(getProfileKey(KEY_READER_LINE_SPACING), settings.lineSpacing)
            putString(getProfileKey(KEY_READER_THEME), settings.readerTheme)
            putString(getProfileKey(KEY_READER_FONT), settings.fontFamily)
            putString(getProfileKey(KEY_READER_TEXT_ALIGN), settings.textAlignment)
            putInt(getProfileKey(KEY_READER_MARGINS), settings.margins)
            putInt(getProfileKey(KEY_READER_PARAGRAPH_SPACING), settings.paragraphSpacing)
            putFloat(getProfileKey(KEY_READER_BRIGHTNESS), settings.brightness)
            putBoolean(getProfileKey(KEY_READER_BOLD_TEXT), settings.boldText)
            putInt(getProfileKey(KEY_READER_WORD_SPACING), settings.wordSpacing)
            putString(getProfileKey(KEY_READER_PAGE_FIT_MODE), settings.pageFitMode)
            putInt(getProfileKey(KEY_READER_PAGE_GAP), settings.pageGap)
            putBoolean(getProfileKey(KEY_READER_FORCE_TWO_PAGE), settings.forceTwoPage)
            putBoolean(getProfileKey(KEY_READER_FORCE_SINGLE_PAGE), settings.forceSinglePage)
            putBoolean(getProfileKey(KEY_READER_KEEP_SCREEN_ON), settings.keepScreenOn)
            apply()
        }
    }

    /**
     * Load comic settings from JSON file and update StateFlows
     */
    suspend fun loadComicSettingsFromFile() {
        val settings = profileFileManager.loadComicSettings(currentProfileName) ?: return

        // Update StateFlows with loaded values
        _comicForceTwoPage.value = settings.forceTwoPage
        _comicForceSinglePage.value = settings.forceSinglePage
        _comicReadingDirection.value = settings.readingDirection
        _comicPageFitMode.value = settings.pageFitMode
        _comicPageGap.value = settings.pageGap
        _comicBackgroundColor.value = settings.backgroundColor
        _comicShowPageIndicators.value = settings.showPageIndicators
        _comicEnableDoubleTapZoom.value = settings.enableDoubleTapZoom
        _comicShowControlsOnTap.value = settings.showControlsOnTap

        // Also update SharedPreferences to keep them in sync
        prefs.edit().apply {
            putBoolean(getProfileKey(KEY_COMIC_FORCE_TWO_PAGE), settings.forceTwoPage)
            putBoolean(getProfileKey(KEY_COMIC_FORCE_SINGLE_PAGE), settings.forceSinglePage)
            putString(getProfileKey(KEY_COMIC_READING_DIRECTION), settings.readingDirection)
            putString(getProfileKey(KEY_COMIC_PAGE_FIT_MODE), settings.pageFitMode)
            putInt(getProfileKey(KEY_COMIC_PAGE_GAP), settings.pageGap)
            putString(getProfileKey(KEY_COMIC_BACKGROUND_COLOR), settings.backgroundColor)
            putBoolean(getProfileKey(KEY_COMIC_SHOW_PAGE_INDICATORS), settings.showPageIndicators)
            putBoolean(getProfileKey(KEY_COMIC_ENABLE_DOUBLE_TAP_ZOOM), settings.enableDoubleTapZoom)
            putBoolean(getProfileKey(KEY_COMIC_SHOW_CONTROLS_ON_TAP), settings.showControlsOnTap)
            apply()
        }
    }

    /**
     * Load movie settings from JSON file and update StateFlows
     */
    suspend fun loadMovieSettingsFromFile() {
        val settings = profileFileManager.loadMovieSettings(currentProfileName) ?: return

        // Update StateFlows with loaded values
        _moviePlaybackSpeed.value = settings.playbackSpeed
        _movieKeepScreenOn.value = settings.keepScreenOn
        _movieResizeMode.value = settings.resizeMode
        _movieBrightness.value = settings.brightness
        _movieAutoFullscreenLandscape.value = settings.autoFullscreenLandscape
        _movieShowControlsOnTap.value = settings.showControlsOnTap
        _movieControlsTimeout.value = settings.controlsTimeout
        _movieDoubleTapSeekDuration.value = settings.doubleTapSeekDuration
        _movieSwipeGesturesEnabled.value = settings.swipeGesturesEnabled
        _movieRememberPosition.value = settings.rememberPosition

        // Also update SharedPreferences to keep them in sync
        prefs.edit().apply {
            putFloat(getProfileKey(KEY_MOVIE_PLAYBACK_SPEED), settings.playbackSpeed)
            putBoolean(getProfileKey(KEY_MOVIE_KEEP_SCREEN_ON), settings.keepScreenOn)
            putString(getProfileKey(KEY_MOVIE_RESIZE_MODE), settings.resizeMode)
            putFloat(getProfileKey(KEY_MOVIE_BRIGHTNESS), settings.brightness)
            putBoolean(getProfileKey(KEY_MOVIE_AUTO_FULLSCREEN_LANDSCAPE), settings.autoFullscreenLandscape)
            putBoolean(getProfileKey(KEY_MOVIE_SHOW_CONTROLS_ON_TAP), settings.showControlsOnTap)
            putInt(getProfileKey(KEY_MOVIE_CONTROLS_TIMEOUT), settings.controlsTimeout)
            putInt(getProfileKey(KEY_MOVIE_DOUBLE_TAP_SEEK_DURATION), settings.doubleTapSeekDuration)
            putBoolean(getProfileKey(KEY_MOVIE_SWIPE_GESTURES_ENABLED), settings.swipeGesturesEnabled)
            putBoolean(getProfileKey(KEY_MOVIE_REMEMBER_POSITION), settings.rememberPosition)
            apply()
        }
    }

    /**
     * Reload settings from JSON files (for when files are modified externally)
     * Call this when resuming the app or when you want to refresh settings from disk
     */
    fun reloadSettingsFromFiles() {
        loadAllSettingsFromFiles()
    }

    // ==================== File-Based Settings Persistence ====================

    /**
     * Save all profile settings to JSON file
     */
    fun saveProfileSettingsToFile() {
        fileScope.launch {
            val activeProfile = _profiles.value.find { it.isActive } ?: return@launch
            val profileSettings = ProfileSettings(
                version = 1,
                id = activeProfile.id,
                name = activeProfile.name,
                profilePicture = activeProfile.profilePicture,
                theme = _appTheme.value.name,
                darkMode = _darkMode.value,
                accentTheme = _accentTheme.value.name,
                backgroundTheme = _backgroundTheme.value.name,
                customPrimaryColor = _customPrimaryColor.value,
                customAccentColor = _customAccentColor.value,
                customBackgroundColor = _customBackgroundColor.value,
                appScale = _appScale.value,
                uiFontScale = _uiFontScale.value,
                uiFontFamily = _uiFontFamily.value,
                libraryOwnerName = _libraryOwnerName.value,
                defaultLibraryView = _defaultLibraryView.value,
                defaultSortOrder = _defaultSortOrder.value,
                showPlaceholderIcons = _showPlaceholderIcons.value,
                showFileSize = _showFileSize.value,
                showDuration = _showDuration.value,
                showBackButton = _showBackButton.value,
                showSearchBar = _showSearchBar.value,
                animationSpeed = _animationSpeed.value,
                hapticFeedback = _hapticFeedback.value,
                confirmBeforeDelete = _confirmBeforeDelete.value,
                useSquareCorners = _useSquareCorners.value,
                collapsedSeries = _collapsedSeries.value.toList(),
                collapsedPlaylists = emptyList(),
                selectedContentType = _selectedContentType.value,
                selectedAudiobookCategoryId = null,
                selectedBookCategoryId = null,
                selectedMusicCategoryId = null,
                selectedCreepypastaCategoryId = null,
                selectedComicCategoryId = null,
                selectedMovieCategoryId = null
            )
            profileFileManager.saveProfileSettings(currentProfileName, profileSettings)
        }
    }

    /**
     * Save audio settings to JSON file
     */
    fun saveAudioSettingsToFile() {
        fileScope.launch {
            val audioSettings = AudioSettings(
                version = 1,
                playbackSpeed = _playbackSpeed.value,
                skipForwardDuration = _skipForwardDuration.value,
                skipBackDuration = _skipBackDuration.value,
                sleepTimerMinutes = _sleepTimerMinutes.value,
                autoBookmark = _autoBookmark.value,
                keepScreenOn = _keepScreenOn.value,
                autoPlayNext = _autoPlayNext.value,
                resumePlayback = _resumePlayback.value,
                rememberLastPosition = _rememberLastPosition.value,
                autoRewindSeconds = _autoRewindSeconds.value,
                volumeBoostEnabled = _volumeBoostEnabled.value,
                volumeBoostLevel = _volumeBoostLevel.value,
                normalizeAudio = _normalizeAudio.value,
                bassBoostLevel = _bassBoostLevel.value.toFloat(),
                equalizerPreset = _equalizerPreset.value,
                crossfadeDuration = _crossfadeDuration.value,
                headsetControls = _headsetControls.value,
                pauseOnDisconnect = _pauseOnDisconnect.value,
                showPlaybackNotification = _showPlaybackNotification.value,
                lastMusicId = _lastMusicId.value,
                lastMusicPosition = _lastMusicPosition.value,
                lastMusicPlaying = _lastMusicPlaying.value,
                lastAudiobookId = _lastAudiobookId.value,
                lastAudiobookPosition = _lastAudiobookPosition.value,
                lastAudiobookPlaying = _lastAudiobookPlaying.value,
                lastActiveType = _lastActiveType.value,
                musicShuffleEnabled = _musicShuffleEnabled.value,
                musicRepeatMode = _musicRepeatMode.value
            )
            profileFileManager.saveAudioSettings(currentProfileName, audioSettings)
        }
    }

    /**
     * Save reader settings to JSON file
     */
    fun saveReaderSettingsToFile() {
        fileScope.launch {
            val readerSettings = ReaderSettings(
                version = 1,
                fontSize = _readerFontSize.value,
                lineSpacing = _readerLineSpacing.value,
                readerTheme = _readerTheme.value,
                fontFamily = _readerFont.value,
                textAlignment = _readerTextAlign.value,
                margins = _readerMargins.value,
                paragraphSpacing = _readerParagraphSpacing.value,
                brightness = _readerBrightness.value,
                boldText = _readerBoldText.value,
                wordSpacing = _readerWordSpacing.value,
                pageFitMode = _readerPageFitMode.value,
                pageGap = _readerPageGap.value,
                forceTwoPage = _readerForceTwoPage.value,
                forceSinglePage = _readerForceSinglePage.value,
                keepScreenOn = _readerKeepScreenOn.value
            )
            profileFileManager.saveReaderSettings(currentProfileName, readerSettings)
        }
    }

    /**
     * Save comic settings to JSON file
     */
    fun saveComicSettingsToFile() {
        fileScope.launch {
            val comicSettings = ComicSettings(
                version = 1,
                forceTwoPage = _comicForceTwoPage.value,
                forceSinglePage = _comicForceSinglePage.value,
                readingDirection = _comicReadingDirection.value,
                pageFitMode = _comicPageFitMode.value,
                pageGap = _comicPageGap.value,
                backgroundColor = _comicBackgroundColor.value,
                showPageIndicators = _comicShowPageIndicators.value,
                enableDoubleTapZoom = _comicEnableDoubleTapZoom.value,
                showControlsOnTap = _comicShowControlsOnTap.value,
                keepScreenOn = true
            )
            profileFileManager.saveComicSettings(currentProfileName, comicSettings)
        }
    }

    /**
     * Save movie settings to JSON file
     */
    fun saveMovieSettingsToFile() {
        fileScope.launch {
            val movieSettings = MovieSettings(
                version = 1,
                playbackSpeed = _moviePlaybackSpeed.value,
                keepScreenOn = _movieKeepScreenOn.value,
                resizeMode = _movieResizeMode.value,
                brightness = _movieBrightness.value,
                autoFullscreenLandscape = _movieAutoFullscreenLandscape.value,
                showControlsOnTap = _movieShowControlsOnTap.value,
                controlsTimeout = _movieControlsTimeout.value,
                doubleTapSeekDuration = _movieDoubleTapSeekDuration.value,
                swipeGesturesEnabled = _movieSwipeGesturesEnabled.value,
                rememberPosition = _movieRememberPosition.value
            )
            profileFileManager.saveMovieSettings(currentProfileName, movieSettings)
        }
    }

    /**
     * Save all settings to files (called when switching profiles or on significant changes)
     */
    fun saveAllSettingsToFiles() {
        saveProfileSettingsToFile()
        saveAudioSettingsToFile()
        saveReaderSettingsToFile()
        saveComicSettingsToFile()
        saveMovieSettingsToFile()
    }

    /**
     * Get the profile file manager for external access (e.g., migration)
     */
    fun getProfileFileManager(): ProfileFileManager = profileFileManager

    /**
     * Get the current active profile name
     */
    fun getActiveProfileName(): String = currentProfileName

    fun setCustomPrimaryColor(color: Int) {
        _customPrimaryColor.value = color
        prefs.edit().putInt(getProfileKey(KEY_CUSTOM_PRIMARY_COLOR), color).apply()
        updateCustomThemeColors()
    }

    fun setCustomAccentColor(color: Int) {
        _customAccentColor.value = color
        prefs.edit().putInt(getProfileKey(KEY_CUSTOM_ACCENT_COLOR), color).apply()
        updateCustomThemeColors()
    }

    fun setCustomBackgroundColor(color: Int) {
        _customBackgroundColor.value = color
        prefs.edit().putInt(getProfileKey(KEY_CUSTOM_BACKGROUND_COLOR), color).apply()
        updateCustomThemeColors()
    }

    fun setUiFontScale(scale: Float) {
        val clamped = scale.coerceIn(0.85f, 1.3f)
        _uiFontScale.value = clamped
        prefs.edit().putFloat(getProfileKey(KEY_UI_FONT_SCALE), clamped).apply()
    }

    fun setUiFontFamily(family: String) {
        _uiFontFamily.value = family
        prefs.edit().putString(getProfileKey(KEY_UI_FONT_FAMILY), family).apply()
    }

    fun setAppScale(scale: Float) {
        val clamped = scale.coerceIn(0.85f, 1.3f)
        _appScale.value = clamped
        prefs.edit().putFloat(getProfileKey(KEY_APP_SCALE), clamped).apply()
    }

    fun setLastMusicState(id: String?, position: Long, isPlaying: Boolean) {
        _lastMusicId.value = id
        _lastMusicPosition.value = position
        _lastMusicPlaying.value = isPlaying
        prefs.edit()
            .putString(getProfileKey(KEY_LAST_MUSIC_ID), id)
            .putLong(getProfileKey(KEY_LAST_MUSIC_POSITION), position)
            .putBoolean(getProfileKey(KEY_LAST_MUSIC_PLAYING), isPlaying)
            .apply()
    }

    fun setLastAudiobookState(id: String?, position: Long, isPlaying: Boolean) {
        _lastAudiobookId.value = id
        _lastAudiobookPosition.value = position
        _lastAudiobookPlaying.value = isPlaying
        prefs.edit()
            .putString(getProfileKey(KEY_LAST_AUDIOBOOK_ID), id)
            .putLong(getProfileKey(KEY_LAST_AUDIOBOOK_POSITION), position)
            .putBoolean(getProfileKey(KEY_LAST_AUDIOBOOK_PLAYING), isPlaying)
            .apply()
    }

    private fun loadCustomPrimaryColor(): Int {
        return prefs.getInt(getProfileKey(KEY_CUSTOM_PRIMARY_COLOR), 0x00897B) // Default teal
    }

    private fun loadCustomAccentColor(): Int {
        return prefs.getInt(getProfileKey(KEY_CUSTOM_ACCENT_COLOR), 0x26A69A) // Default teal accent
    }

    private fun loadCustomBackgroundColor(): Int {
        return prefs.getInt(getProfileKey(KEY_CUSTOM_BACKGROUND_COLOR), 0x121212) // Default dark background
    }

    private fun loadAppScale(): Float {
        return prefs.getFloat(getProfileKey(KEY_APP_SCALE), 1.0f).coerceIn(0.85f, 1.3f)
    }

    private fun loadUiFontScale(): Float {
        return prefs.getFloat(getProfileKey(KEY_UI_FONT_SCALE), 1.0f).coerceIn(0.85f, 1.3f)
    }

    private fun loadUiFontFamily(): String {
        return prefs.getString(getProfileKey(KEY_UI_FONT_FAMILY), "Default") ?: "Default"
    }

    private fun loadString(key: String): String? = prefs.getString(getProfileKey(key), null)
    private fun loadLong(key: String, default: Long): Long = prefs.getLong(getProfileKey(key), default)
    private fun loadBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(getProfileKey(key), default)
    private fun loadInt(key: String, default: Int): Int = prefs.getInt(getProfileKey(key), default)

    fun setReaderFontSize(size: Int) {
        _readerFontSize.value = size
        prefs.edit().putInt(getProfileKey(KEY_READER_FONT_SIZE), size).apply()
        saveReaderSettingsToFile()
    }

    fun setReaderLineSpacing(spacing: Float) {
        _readerLineSpacing.value = spacing
        prefs.edit().putFloat(getProfileKey(KEY_READER_LINE_SPACING), spacing).apply()
        saveReaderSettingsToFile()
    }

    fun setReaderTheme(theme: String) {
        _readerTheme.value = theme
        prefs.edit().putString(getProfileKey(KEY_READER_THEME), theme).apply()
        saveReaderSettingsToFile()
    }

    fun setReaderFont(font: String) {
        _readerFont.value = font
        prefs.edit().putString(getProfileKey(KEY_READER_FONT), font).apply()
        saveReaderSettingsToFile()
    }

    fun setReaderTextAlign(align: String) {
        _readerTextAlign.value = align
        prefs.edit().putString(getProfileKey(KEY_READER_TEXT_ALIGN), align).apply()
        saveReaderSettingsToFile()
    }

    fun setReaderMargins(margins: Int) {
        _readerMargins.value = margins
        prefs.edit().putInt(getProfileKey(KEY_READER_MARGINS), margins).apply()
        saveReaderSettingsToFile()
    }

    fun setReaderKeepScreenOn(enabled: Boolean) {
        _readerKeepScreenOn.value = enabled
        prefs.edit().putBoolean(getProfileKey(KEY_READER_KEEP_SCREEN_ON), enabled).apply()
        saveReaderSettingsToFile()
    }

    fun setReaderParagraphSpacing(spacing: Int) {
        _readerParagraphSpacing.value = spacing
        prefs.edit().putInt(getProfileKey(KEY_READER_PARAGRAPH_SPACING), spacing).apply()
        saveReaderSettingsToFile()
    }

    fun setReaderBrightness(brightness: Float) {
        _readerBrightness.value = brightness
        prefs.edit().putFloat(getProfileKey(KEY_READER_BRIGHTNESS), brightness).apply()
        saveReaderSettingsToFile()
    }

    fun setReaderBoldText(enabled: Boolean) {
        _readerBoldText.value = enabled
        prefs.edit().putBoolean(getProfileKey(KEY_READER_BOLD_TEXT), enabled).apply()
        saveReaderSettingsToFile()
    }

    fun setReaderWordSpacing(spacing: Int) {
        _readerWordSpacing.value = spacing
        prefs.edit().putInt(getProfileKey(KEY_READER_WORD_SPACING), spacing).apply()
        saveReaderSettingsToFile()
    }

    fun setReaderPageFitMode(mode: String) {
        _readerPageFitMode.value = mode
        prefs.edit().putString(getProfileKey(KEY_READER_PAGE_FIT_MODE), mode).apply()
        saveReaderSettingsToFile()
    }

    fun setReaderPageGap(gap: Int) {
        _readerPageGap.value = gap
        prefs.edit().putInt(getProfileKey(KEY_READER_PAGE_GAP), gap).apply()
        saveReaderSettingsToFile()
    }

    fun setReaderForceTwoPage(enabled: Boolean) {
        _readerForceTwoPage.value = enabled
        prefs.edit().putBoolean(getProfileKey(KEY_READER_FORCE_TWO_PAGE), enabled).apply()
        saveReaderSettingsToFile()
    }

    fun setReaderForceSinglePage(enabled: Boolean) {
        _readerForceSinglePage.value = enabled
        prefs.edit().putBoolean(getProfileKey(KEY_READER_FORCE_SINGLE_PAGE), enabled).apply()
        saveReaderSettingsToFile()
    }

    // Comic Reader setters (per-profile)
    fun setComicForceTwoPage(enabled: Boolean) {
        _comicForceTwoPage.value = enabled
        prefs.edit().putBoolean(getProfileKey(KEY_COMIC_FORCE_TWO_PAGE), enabled).apply()
        saveComicSettingsToFile()
    }

    fun setComicForceSinglePage(enabled: Boolean) {
        _comicForceSinglePage.value = enabled
        prefs.edit().putBoolean(getProfileKey(KEY_COMIC_FORCE_SINGLE_PAGE), enabled).apply()
        saveComicSettingsToFile()
    }

    fun setComicReadingDirection(direction: String) {
        _comicReadingDirection.value = direction
        prefs.edit().putString(getProfileKey(KEY_COMIC_READING_DIRECTION), direction).apply()
        saveComicSettingsToFile()
    }

    fun setComicPageFitMode(mode: String) {
        _comicPageFitMode.value = mode
        prefs.edit().putString(getProfileKey(KEY_COMIC_PAGE_FIT_MODE), mode).apply()
        saveComicSettingsToFile()
    }

    fun setComicPageGap(gap: Int) {
        _comicPageGap.value = gap
        prefs.edit().putInt(getProfileKey(KEY_COMIC_PAGE_GAP), gap).apply()
        saveComicSettingsToFile()
    }

    fun setComicBackgroundColor(color: String) {
        _comicBackgroundColor.value = color
        prefs.edit().putString(getProfileKey(KEY_COMIC_BACKGROUND_COLOR), color).apply()
        saveComicSettingsToFile()
    }

    fun setComicShowPageIndicators(enabled: Boolean) {
        _comicShowPageIndicators.value = enabled
        prefs.edit().putBoolean(getProfileKey(KEY_COMIC_SHOW_PAGE_INDICATORS), enabled).apply()
        saveComicSettingsToFile()
    }

    fun setComicEnableDoubleTapZoom(enabled: Boolean) {
        _comicEnableDoubleTapZoom.value = enabled
        prefs.edit().putBoolean(getProfileKey(KEY_COMIC_ENABLE_DOUBLE_TAP_ZOOM), enabled).apply()
        saveComicSettingsToFile()
    }

    fun setComicShowControlsOnTap(enabled: Boolean) {
        _comicShowControlsOnTap.value = enabled
        prefs.edit().putBoolean(getProfileKey(KEY_COMIC_SHOW_CONTROLS_ON_TAP), enabled).apply()
        saveComicSettingsToFile()
    }

    // Movie Player setters (per-profile)
    fun setMoviePlaybackSpeed(speed: Float) {
        val safeSpeed = speed.coerceIn(0.25f, 3f)
        _moviePlaybackSpeed.value = safeSpeed
        prefs.edit().putFloat(getProfileKey(KEY_MOVIE_PLAYBACK_SPEED), safeSpeed).apply()
        saveMovieSettingsToFile()
    }

    fun setMovieKeepScreenOn(enabled: Boolean) {
        _movieKeepScreenOn.value = enabled
        prefs.edit().putBoolean(getProfileKey(KEY_MOVIE_KEEP_SCREEN_ON), enabled).apply()
        saveMovieSettingsToFile()
    }

    fun setMovieResizeMode(mode: String) {
        _movieResizeMode.value = mode
        prefs.edit().putString(getProfileKey(KEY_MOVIE_RESIZE_MODE), mode).apply()
        saveMovieSettingsToFile()
    }

    fun setMovieBrightness(brightness: Float) {
        val safeBrightness = brightness.coerceIn(0f, 1f)
        _movieBrightness.value = safeBrightness
        prefs.edit().putFloat(getProfileKey(KEY_MOVIE_BRIGHTNESS), safeBrightness).apply()
        saveMovieSettingsToFile()
    }

    fun setMovieAutoFullscreenLandscape(enabled: Boolean) {
        _movieAutoFullscreenLandscape.value = enabled
        prefs.edit().putBoolean(getProfileKey(KEY_MOVIE_AUTO_FULLSCREEN_LANDSCAPE), enabled).apply()
        saveMovieSettingsToFile()
    }

    fun setMovieShowControlsOnTap(enabled: Boolean) {
        _movieShowControlsOnTap.value = enabled
        prefs.edit().putBoolean(getProfileKey(KEY_MOVIE_SHOW_CONTROLS_ON_TAP), enabled).apply()
        saveMovieSettingsToFile()
    }

    fun setMovieControlsTimeout(seconds: Int) {
        val safeTimeout = seconds.coerceIn(1, 30)
        _movieControlsTimeout.value = safeTimeout
        prefs.edit().putInt(getProfileKey(KEY_MOVIE_CONTROLS_TIMEOUT), safeTimeout).apply()
        saveMovieSettingsToFile()
    }

    fun setMovieDoubleTapSeekDuration(seconds: Int) {
        val safeDuration = seconds.coerceIn(5, 60)
        _movieDoubleTapSeekDuration.value = safeDuration
        prefs.edit().putInt(getProfileKey(KEY_MOVIE_DOUBLE_TAP_SEEK_DURATION), safeDuration).apply()
        saveMovieSettingsToFile()
    }

    fun setMovieSwipeGesturesEnabled(enabled: Boolean) {
        _movieSwipeGesturesEnabled.value = enabled
        prefs.edit().putBoolean(getProfileKey(KEY_MOVIE_SWIPE_GESTURES_ENABLED), enabled).apply()
        saveMovieSettingsToFile()
    }

    fun setMovieRememberPosition(enabled: Boolean) {
        _movieRememberPosition.value = enabled
        prefs.edit().putBoolean(getProfileKey(KEY_MOVIE_REMEMBER_POSITION), enabled).apply()
        saveMovieSettingsToFile()
    }

    fun setTheme(theme: AppTheme) {
        _appTheme.value = theme
        prefs.edit().putString(KEY_THEME, theme.name).apply()
    }

    fun setSkipForwardDuration(seconds: Int) {
        _skipForwardDuration.value = seconds
        prefs.edit().putInt(getProfileKey(KEY_SKIP_FORWARD), seconds).apply()
        saveAudioSettingsToFile()
    }

    fun setSkipBackDuration(seconds: Int) {
        _skipBackDuration.value = seconds
        prefs.edit().putInt(getProfileKey(KEY_SKIP_BACK), seconds).apply()
        saveAudioSettingsToFile()
    }

    fun setAutoBookmark(enabled: Boolean) {
        _autoBookmark.value = enabled
        prefs.edit().putBoolean(getProfileKey(KEY_AUTO_BOOKMARK), enabled).apply()
        saveAudioSettingsToFile()
    }

    fun setKeepScreenOn(enabled: Boolean) {
        _keepScreenOn.value = enabled
        prefs.edit().putBoolean(getProfileKey(KEY_KEEP_SCREEN_ON), enabled).apply()
        saveAudioSettingsToFile()
    }

    fun setVolumeBoostEnabled(enabled: Boolean) {
        _volumeBoostEnabled.value = enabled
        prefs.edit().putBoolean(KEY_VOLUME_BOOST_ENABLED, enabled).apply()
        saveAudioSettingsToFile()
    }

    fun setVolumeBoostLevel(level: Float) {
        _volumeBoostLevel.value = level
        prefs.edit().putFloat(KEY_VOLUME_BOOST_LEVEL, level).apply()
        saveAudioSettingsToFile()
    }

    fun setLibraryOwnerName(name: String) {
        _libraryOwnerName.value = name
        prefs.edit().putString(getProfileKey(KEY_LIBRARY_OWNER_NAME), name).apply()
    }

    fun setDarkMode(enabled: Boolean) {
        _darkMode.value = enabled
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }

    fun setBackgroundTheme(theme: BackgroundTheme) {
        _backgroundTheme.value = theme
        prefs.edit().putString(getProfileKey(KEY_BACKGROUND_THEME), theme.name).apply()
        saveProfileSettingsToFile()
    }

    fun setPlaybackSpeed(speed: Float) {
        val safeSpeed = speed.coerceIn(0.5f, 2f)
        _playbackSpeed.value = safeSpeed
        prefs.edit().putFloat(KEY_PLAYBACK_SPEED, safeSpeed).apply()
        saveAudioSettingsToFile()
    }

    fun setSleepTimerMinutes(minutes: Int) {
        _sleepTimerMinutes.value = minutes
        prefs.edit().putInt(KEY_SLEEP_TIMER_MINUTES, minutes).apply()
        saveAudioSettingsToFile()
    }

    fun setAutoPlayNext(enabled: Boolean) {
        _autoPlayNext.value = enabled
        prefs.edit().putBoolean(getProfileKey(KEY_AUTO_PLAY_NEXT), enabled).apply()
        saveAudioSettingsToFile()
    }

    fun setLastActiveType(type: String?) {
        _lastActiveType.value = type
        if (type != null) {
            prefs.edit().putString(getProfileKey(KEY_LAST_ACTIVE_TYPE), type).apply()
        } else {
            prefs.edit().remove(getProfileKey(KEY_LAST_ACTIVE_TYPE)).apply()
        }
        saveAudioSettingsToFile()
    }

    fun setMusicShuffleEnabled(enabled: Boolean) {
        _musicShuffleEnabled.value = enabled
        // Use commit() for synchronous write to ensure state is persisted before app close
        prefs.edit().putBoolean(getProfileKey(KEY_MUSIC_SHUFFLE_ENABLED), enabled).commit()
        saveAudioSettingsToFile()
    }

    fun setMusicRepeatMode(mode: Int) {
        _musicRepeatMode.value = mode
        // Use commit() for synchronous write to ensure state is persisted before app close
        prefs.edit().putInt(getProfileKey(KEY_MUSIC_REPEAT_MODE), mode).commit()
        saveAudioSettingsToFile()
    }

    fun setDefaultLibraryView(view: String) {
        _defaultLibraryView.value = view
        prefs.edit().putString(getProfileKey(KEY_DEFAULT_LIBRARY_VIEW), view).apply()
    }

    fun setDefaultSortOrder(order: String) {
        _defaultSortOrder.value = order
        prefs.edit().putString(getProfileKey(KEY_DEFAULT_SORT_ORDER), order).apply()
    }

    fun setResumePlayback(enabled: Boolean) {
        _resumePlayback.value = enabled
        prefs.edit().putBoolean(getProfileKey(KEY_RESUME_PLAYBACK), enabled).apply()
        saveAudioSettingsToFile()
    }

    fun setShowPlaybackNotification(enabled: Boolean) {
        _showPlaybackNotification.value = enabled
        prefs.edit().putBoolean(getProfileKey(KEY_SHOW_PLAYBACK_NOTIFICATION), enabled).apply()
        saveAudioSettingsToFile()
    }

    fun addProfile(name: String) {
        val newProfile = UserProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            isActive = false,
            theme = AppTheme.DARK_TEAL.name,
            darkMode = false,
            profilePicture = null
        )
        val currentProfiles = _profiles.value.toMutableList()
        currentProfiles.add(newProfile)
        _profiles.value = currentProfiles
        saveProfiles(currentProfiles)

        // Create profile-specific folder structure
        createProfileFolders(name)

        // Create initial settings JSON files for the new profile
        fileScope.launch {
            val profileSettings = ProfileSettings(
                version = 1,
                id = newProfile.id,
                name = name,
                theme = AppTheme.DARK_TEAL.name,
                darkMode = false,
                libraryOwnerName = name
            )
            profileFileManager.saveProfileSettings(name, profileSettings)
            profileFileManager.saveAudioSettings(name, AudioSettings())
            profileFileManager.saveReaderSettings(name, ReaderSettings())
            profileFileManager.saveComicSettings(name, ComicSettings())
        }
    }

    fun deleteProfile(profileId: String) {
        // Don't delete if it's the only profile
        if (_profiles.value.size <= 1) return

        val currentProfiles = _profiles.value.toMutableList()
        val deletedProfile = currentProfiles.find { it.id == profileId }
        currentProfiles.removeAll { it.id == profileId }

        // If we deleted the active profile, switch to default or first available
        if (deletedProfile?.isActive == true && currentProfiles.isNotEmpty()) {
            val newActiveId = currentProfiles.find { it.id == "default" }?.id
                ?: currentProfiles.first().id
            setActiveProfile(newActiveId)
        }

        _profiles.value = currentProfiles
        saveProfiles(currentProfiles)

        // Delete the profile's folder structure and settings files
        deletedProfile?.let { profile ->
            deleteProfileFolder(profile.name)
            fileScope.launch {
                profileFileManager.deleteProfileSettings(profile.name)
            }
        }
    }

    fun renameProfile(profileId: String, newName: String) {
        val currentProfiles = _profiles.value.toMutableList()
        val index = currentProfiles.indexOfFirst { it.id == profileId }
        if (index >= 0) {
            val oldName = currentProfiles[index].name
            currentProfiles[index] = currentProfiles[index].copy(name = newName)
            _profiles.value = currentProfiles
            saveProfiles(currentProfiles)

            // Rename the profile's folder structure (including settings files)
            renameProfileFolder(oldName, newName)
            fileScope.launch {
                profileFileManager.renameProfile(oldName, newName)
            }

            // If this is the active profile, update the library owner name and current profile name
            if (currentProfiles[index].isActive) {
                currentProfileName = newName
                setLibraryOwnerName(newName)
            }
        }
    }

    fun setProfilePicture(profileId: String, pictureUri: String?) {
        val currentProfiles = _profiles.value.toMutableList()
        val index = currentProfiles.indexOfFirst { it.id == profileId }
        if (index >= 0) {
            currentProfiles[index] = currentProfiles[index].copy(profilePicture = pictureUri)
            _profiles.value = currentProfiles
            saveProfiles(currentProfiles)
        }
    }

    fun setActiveProfile(profileId: String) {
        // Save current profile settings to files before switching
        saveAllSettingsToFiles()

        _activeProfileId.value = profileId
        prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, profileId).apply()

        // Update the active flag in profiles list
        val updatedProfiles = _profiles.value.map { profile ->
            profile.copy(isActive = profile.id == profileId)
        }
        _profiles.value = updatedProfiles

        // Update library owner name based on selected profile
        val activeProfile = updatedProfiles.find { it.id == profileId }
        if (activeProfile != null) {
            // Update current profile name for per-profile settings
            currentProfileName = activeProfile.name
            val storedName = loadLibraryOwnerName()
            if (storedName.isBlank()) {
                setLibraryOwnerName(activeProfile.name)
            } else {
                _libraryOwnerName.value = storedName
            }
            // Sync skip controls from the profile snapshot into per-profile prefs/flows
            setSkipForwardDuration(activeProfile.skipForwardDuration)
            setSkipBackDuration(activeProfile.skipBackDuration)

            // Load theme and dark mode from profile
            _appTheme.value = try {
                AppTheme.valueOf(activeProfile.theme)
            } catch (e: Exception) {
                AppTheme.DARK_TEAL
            }
            _darkMode.value = activeProfile.darkMode
            prefs.edit()
                .putString(KEY_THEME, _appTheme.value.name)
                .putBoolean(KEY_DARK_MODE, _darkMode.value)
                .apply()

            // Load all profile-specific settings
            reloadProfileSettings()
            // Ensure JSON settings files remain the source of truth for the new profile
            loadAllSettingsFromFiles()
        }
    }

    /**
     * Reload all profile-specific settings for the current profile
     */
    private fun reloadProfileSettings() {
        // Core playback + general toggles
        _skipForwardDuration.value = loadSkipForward()
        _skipBackDuration.value = loadSkipBack()
        _autoBookmark.value = loadAutoBookmark()
        _keepScreenOn.value = loadKeepScreenOn()
        _autoPlayNext.value = loadAutoPlayNext()
        _resumePlayback.value = loadResumePlayback()
        _showPlaybackNotification.value = loadShowPlaybackNotification()
        _autoRewindSeconds.value = loadAutoRewindSeconds()
        _headsetControls.value = loadHeadsetControls()
        _pauseOnDisconnect.value = loadPauseOnDisconnect()
        _libraryOwnerName.value = loadLibraryOwnerName()
        _rememberLastPosition.value = loadRememberLastPosition()

        // Theme settings
        _accentTheme.value = loadAccentTheme()
        _backgroundTheme.value = loadBackgroundTheme()
        _customPrimaryColor.value = loadCustomPrimaryColor()
        _customAccentColor.value = loadCustomAccentColor()
        _customBackgroundColor.value = loadCustomBackgroundColor()
        updateCustomThemeColors()

        // Layout + typography
        _appScale.value = loadAppScale()
        _uiFontScale.value = loadUiFontScale()
        _uiFontFamily.value = loadUiFontFamily()

        // Reader settings
        _readerFontSize.value = loadReaderFontSize()
        _readerLineSpacing.value = loadReaderLineSpacing()
        _readerTheme.value = loadReaderTheme()
        _readerFont.value = loadReaderFont()
        _readerTextAlign.value = loadReaderTextAlign()
        _readerMargins.value = loadReaderMargins()
        _readerKeepScreenOn.value = loadReaderKeepScreenOn()
        _readerParagraphSpacing.value = loadReaderParagraphSpacing()
        _readerBrightness.value = loadReaderBrightness()
        _readerBoldText.value = loadReaderBoldText()
        _readerWordSpacing.value = loadReaderWordSpacing()
        _readerPageFitMode.value = loadReaderPageFitMode()
        _readerPageGap.value = loadReaderPageGap()
        _readerForceTwoPage.value = loadReaderForceTwoPage()
        _readerForceSinglePage.value = loadReaderForceSinglePage()

        // Comic Reader settings
        _comicForceTwoPage.value = loadComicForceTwoPage()
        _comicForceSinglePage.value = loadComicForceSinglePage()
        _comicReadingDirection.value = loadComicReadingDirection()
        _comicPageFitMode.value = loadComicPageFitMode()
        _comicPageGap.value = loadComicPageGap()
        _comicBackgroundColor.value = loadComicBackgroundColor()
        _comicShowPageIndicators.value = loadComicShowPageIndicators()
        _comicEnableDoubleTapZoom.value = loadComicEnableDoubleTapZoom()
        _comicShowControlsOnTap.value = loadComicShowControlsOnTap()

        // Display settings
        _showPlaceholderIcons.value = loadShowPlaceholderIcons()
        _showFileSize.value = loadShowFileSize()
        _showDuration.value = loadShowDuration()
        _animationSpeed.value = loadAnimationSpeed()
        _hapticFeedback.value = loadHapticFeedback()
        _confirmBeforeDelete.value = loadConfirmBeforeDelete()
        _useSquareCorners.value = loadUseSquareCorners()

        // Filter persistence
        _selectedContentType.value = loadSelectedContentType()
        _selectedCategoryId.value = loadSelectedCategoryId()
        _collapsedSeries.value = loadCollapsedSeries()

        // UI Visibility settings
        _showBackButton.value = loadShowBackButton()
        _showSearchBar.value = loadShowSearchBar()

        // Library view settings
        _defaultLibraryView.value = loadDefaultLibraryView()
        _defaultSortOrder.value = loadDefaultSortOrder()
    }

    // Update theme for current profile
    fun setThemeForProfile(theme: AppTheme) {
        _appTheme.value = theme
        prefs.edit().putString(KEY_THEME, theme.name).apply()

        // Update the current profile's theme
        val activeId = _activeProfileId.value
        val updatedProfiles = _profiles.value.map { profile ->
            if (profile.id == activeId) {
                profile.copy(theme = theme.name)
            } else profile
        }
        _profiles.value = updatedProfiles
        saveProfiles(updatedProfiles)
    }

    // Update accent theme for current profile (independent from primary theme)
    fun setAccentThemeForProfile(theme: AppTheme) {
        _accentTheme.value = theme
        prefs.edit().putString(getProfileKey(KEY_ACCENT_THEME), theme.name).apply()
    }

    // Update dark mode for current profile
    fun setDarkModeForProfile(enabled: Boolean) {
        _darkMode.value = enabled
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()

        // Update the current profile's dark mode
        val activeId = _activeProfileId.value
        val updatedProfiles = _profiles.value.map { profile ->
            if (profile.id == activeId) {
                profile.copy(darkMode = enabled)
            } else profile
        }
        _profiles.value = updatedProfiles
        saveProfiles(updatedProfiles)
    }

    // Update profile audio settings
    fun setProfilePlaybackSpeed(speed: Float) {
        val safeSpeed = speed.coerceIn(0.5f, 2f)
        val activeId = _activeProfileId.value
        val updatedProfiles = _profiles.value.map { profile ->
            if (profile.id == activeId) profile.copy(playbackSpeed = safeSpeed) else profile
        }
        _profiles.value = updatedProfiles
        saveProfiles(updatedProfiles)
    }

    fun setProfileSkipForward(seconds: Int) {
        val activeId = _activeProfileId.value
        val updatedProfiles = _profiles.value.map { profile ->
            if (profile.id == activeId) profile.copy(skipForwardDuration = seconds) else profile
        }
        _profiles.value = updatedProfiles
        saveProfiles(updatedProfiles)
        // Keep persisted per-profile skip duration in sync for the active profile
        if (activeId == _activeProfileId.value) {
            setSkipForwardDuration(seconds)
        }
    }

    fun setProfileSkipBack(seconds: Int) {
        val activeId = _activeProfileId.value
        val updatedProfiles = _profiles.value.map { profile ->
            if (profile.id == activeId) profile.copy(skipBackDuration = seconds) else profile
        }
        _profiles.value = updatedProfiles
        saveProfiles(updatedProfiles)
        if (activeId == _activeProfileId.value) {
            setSkipBackDuration(seconds)
        }
    }

    fun setProfileVolumeBoostEnabled(enabled: Boolean) {
        val activeId = _activeProfileId.value
        val updatedProfiles = _profiles.value.map { profile ->
            if (profile.id == activeId) profile.copy(volumeBoostEnabled = enabled) else profile
        }
        _profiles.value = updatedProfiles
        saveProfiles(updatedProfiles)
    }

    fun setProfileVolumeBoostLevel(level: Float) {
        val activeId = _activeProfileId.value
        val updatedProfiles = _profiles.value.map { profile ->
            if (profile.id == activeId) profile.copy(volumeBoostLevel = level) else profile
        }
        _profiles.value = updatedProfiles
        saveProfiles(updatedProfiles)
    }

    fun setProfileNormalizeAudio(enabled: Boolean) {
        val activeId = _activeProfileId.value
        val updatedProfiles = _profiles.value.map { profile ->
            if (profile.id == activeId) profile.copy(normalizeAudio = enabled) else profile
        }
        _profiles.value = updatedProfiles
        saveProfiles(updatedProfiles)
    }

    fun setProfileBassBoostLevel(level: Float) {
        val activeId = _activeProfileId.value
        val updatedProfiles = _profiles.value.map { profile ->
            if (profile.id == activeId) profile.copy(bassBoostLevel = level) else profile
        }
        _profiles.value = updatedProfiles
        saveProfiles(updatedProfiles)
    }

    fun setProfileEqualizerPreset(preset: String) {
        val activeId = _activeProfileId.value
        val normalized = normalizeEqualizerPreset(preset)
        val updatedProfiles = _profiles.value.map { profile ->
            if (profile.id == activeId) profile.copy(equalizerPreset = normalized) else profile
        }
        _profiles.value = updatedProfiles
        saveProfiles(updatedProfiles)
    }

    fun setProfileSleepTimer(minutes: Int) {
        val activeId = _activeProfileId.value
        val updatedProfiles = _profiles.value.map { profile ->
            if (profile.id == activeId) profile.copy(sleepTimerMinutes = minutes) else profile
        }
        _profiles.value = updatedProfiles
        saveProfiles(updatedProfiles)
    }

    private fun loadProfiles(): List<UserProfile> {
        val profilesJson = prefs.getString(KEY_PROFILES, null)
        if (profilesJson.isNullOrEmpty()) {
            // Create default profile with current theme settings
            val currentTheme = loadTheme()
            val currentDarkMode = loadDarkMode()
            val defaultProfile = UserProfile(
                id = "default",
                name = "Default",
                isActive = true,
                theme = currentTheme.name,
                darkMode = currentDarkMode
            )
            saveProfiles(listOf(defaultProfile))
            return listOf(defaultProfile)
        }

        return try {
            val activeId = loadActiveProfileId()
            val profiles = profilesJson.split("|").mapNotNull { entry ->
                // Try new format with ; delimiter first (handles URIs with colons)
                val parts = if (entry.contains(";")) {
                    entry.split(";", limit = 15) // Limit to 15 for all fields including audio settings and sleep timer
                } else {
                    // Legacy format with : delimiter
                    entry.split(":")
                }
                when {
                    parts.size >= 15 -> {
                        // Full format with all audio settings including sleep timer
                        UserProfile(
                            id = parts[0],
                            name = parts[1],
                            isActive = parts[0] == activeId,
                            theme = parts[2],
                            darkMode = parts[3].toBooleanStrictOrNull() ?: false,
                            profilePicture = parts[4].ifEmpty { null },
                            playbackSpeed = parts[5].toFloatOrNull() ?: 1.0f,
                            skipForwardDuration = parts[6].toIntOrNull() ?: 30,
                            skipBackDuration = parts[7].toIntOrNull() ?: 10,
                            volumeBoostEnabled = parts[8].toBooleanStrictOrNull() ?: false,
                            volumeBoostLevel = parts[9].toFloatOrNull() ?: 1.0f,
                            normalizeAudio = parts[11].toBooleanStrictOrNull() ?: false,
                            bassBoostLevel = parts[12].toFloatOrNull() ?: 0f,
                            equalizerPreset = normalizeEqualizerPreset(parts[13]),
                            sleepTimerMinutes = parts[14].toIntOrNull() ?: 0
                        )
                    }
                    parts.size >= 14 -> {
                        // Format with audio settings but no sleep timer (migrate)
                        UserProfile(
                            id = parts[0],
                            name = parts[1],
                            isActive = parts[0] == activeId,
                            theme = parts[2],
                            darkMode = parts[3].toBooleanStrictOrNull() ?: false,
                            profilePicture = parts[4].ifEmpty { null },
                            playbackSpeed = parts[5].toFloatOrNull() ?: 1.0f,
                            skipForwardDuration = parts[6].toIntOrNull() ?: 30,
                            skipBackDuration = parts[7].toIntOrNull() ?: 10,
                            volumeBoostEnabled = parts[8].toBooleanStrictOrNull() ?: false,
                            volumeBoostLevel = parts[9].toFloatOrNull() ?: 1.0f,
                            normalizeAudio = parts[11].toBooleanStrictOrNull() ?: false,
                            bassBoostLevel = parts[12].toFloatOrNull() ?: 0f,
                            equalizerPreset = normalizeEqualizerPreset(parts[13])
                        )
                    }
                    parts.size >= 5 -> {
                        // Format with profilePicture (migrate - add default audio settings)
                        UserProfile(
                            id = parts[0],
                            name = parts[1],
                            isActive = parts[0] == activeId,
                            theme = parts[2],
                            darkMode = parts[3].toBooleanStrictOrNull() ?: false,
                            profilePicture = parts[4].ifEmpty { null }
                        )
                    }
                    parts.size >= 4 -> {
                        // Format with theme and darkMode (migrate)
                        UserProfile(
                            id = parts[0],
                            name = parts[1],
                            isActive = parts[0] == activeId,
                            theme = parts[2].ifEmpty { AppTheme.DARK_TEAL.name },
                            darkMode = parts[3].toBooleanStrictOrNull() ?: false,
                            profilePicture = null
                        )
                    }
                    parts.size == 2 -> {
                        // Legacy format without theme - migrate
                        UserProfile(
                            id = parts[0],
                            name = parts[1],
                            isActive = parts[0] == activeId,
                            theme = AppTheme.DARK_TEAL.name,
                            darkMode = false,
                            profilePicture = null
                        )
                    }
                    else -> null
                }
            }
            // Re-save in new format to migrate old data
            if (profiles.isNotEmpty() && (profiles.any { it.playbackSpeed != 1.0f } || !profilesJson.contains(";"))) {
                saveProfiles(profiles)
            }
            profiles
        } catch (e: Exception) {
            val defaultProfile = UserProfile(
                id = "default",
                name = "Default",
                isActive = true,
                theme = AppTheme.DARK_TEAL.name,
                darkMode = false
            )
            listOf(defaultProfile)
        }
    }

    private fun saveProfiles(profiles: List<UserProfile>) {
        // Format: id;name;theme;darkMode;profilePicture;playbackSpeed;skipForward;skipBack;volumeBoostEnabled;volumeBoostLevel;normalizeAudio;bassBoostLevel;equalizerPreset;sleepTimerMinutes
        val profilesJson = profiles.joinToString("|") {
            "${it.id};${it.name};${it.theme};${it.darkMode};${it.profilePicture ?: ""};" +
            "${it.playbackSpeed};${it.skipForwardDuration};${it.skipBackDuration};" +
            "${it.volumeBoostEnabled};${it.volumeBoostLevel};" +
            "${it.normalizeAudio};${it.bassBoostLevel};${it.equalizerPreset};${it.sleepTimerMinutes}"
        }
        // Use commit() to ensure data is saved before app exits
        prefs.edit().putString(KEY_PROFILES, profilesJson).commit()
    }

    private fun loadActiveProfileId(): String {
        return prefs.getString(KEY_ACTIVE_PROFILE_ID, "default") ?: "default"
    }

    private fun loadTheme(): AppTheme {
        loadActiveProfileThemeFromPrefs()?.first?.let { return it }
        val themeName = prefs.getString(KEY_THEME, AppTheme.DARK_TEAL.name)
        return try {
            AppTheme.valueOf(themeName ?: AppTheme.DARK_TEAL.name)
        } catch (e: Exception) {
            AppTheme.DARK_TEAL
        }
    }

    private fun loadAccentTheme(): AppTheme {
        val themeName = prefs.getString(getProfileKey(KEY_ACCENT_THEME), null)
        return try {
            if (themeName != null) {
                AppTheme.valueOf(themeName)
            } else {
                // Default to primary theme if no accent theme set
                loadTheme()
            }
        } catch (e: Exception) {
            loadTheme()
        }
    }

    private fun loadSkipForward(): Int = prefs.getInt(getProfileKey(KEY_SKIP_FORWARD), 30)
    private fun loadSkipBack(): Int = prefs.getInt(getProfileKey(KEY_SKIP_BACK), 10)
    private fun loadAutoBookmark(): Boolean = prefs.getBoolean(getProfileKey(KEY_AUTO_BOOKMARK), true)
    private fun loadKeepScreenOn(): Boolean = prefs.getBoolean(getProfileKey(KEY_KEEP_SCREEN_ON), false)
    private fun loadVolumeBoost(): Boolean = prefs.getBoolean(KEY_VOLUME_BOOST_ENABLED, false)
    private fun loadVolumeBoostLevel(): Float = prefs.getFloat(KEY_VOLUME_BOOST_LEVEL, 1.5f)
    private fun loadLibraryOwnerName(): String = prefs.getString(getProfileKey(KEY_LIBRARY_OWNER_NAME), "") ?: ""
    private fun loadDarkMode(): Boolean {
        loadActiveProfileThemeFromPrefs()?.second?.let { return it }
        return prefs.getBoolean(KEY_DARK_MODE, false)
    }
    private fun loadBackgroundTheme(): BackgroundTheme {
        val themeName = prefs.getString(getProfileKey(KEY_BACKGROUND_THEME), BackgroundTheme.WHITE.name)
        return try {
            BackgroundTheme.valueOf(themeName ?: BackgroundTheme.WHITE.name)
        } catch (e: Exception) {
            BackgroundTheme.WHITE
        }
    }

    // Additional global settings loaders
    private fun loadPlaybackSpeed(): Float = prefs.getFloat(KEY_PLAYBACK_SPEED, 1.0f)
    private fun loadSleepTimerMinutes(): Int = prefs.getInt(KEY_SLEEP_TIMER_MINUTES, 0)
    private fun loadAutoPlayNext(): Boolean = prefs.getBoolean(getProfileKey(KEY_AUTO_PLAY_NEXT), true)
    private fun loadDefaultLibraryView(): String = prefs.getString(getProfileKey(KEY_DEFAULT_LIBRARY_VIEW), "GRID") ?: "GRID"
    private fun loadDefaultSortOrder(): String = prefs.getString(getProfileKey(KEY_DEFAULT_SORT_ORDER), "TITLE") ?: "TITLE"
    private fun loadResumePlayback(): Boolean = prefs.getBoolean(getProfileKey(KEY_RESUME_PLAYBACK), true)
    private fun loadShowPlaybackNotification(): Boolean = prefs.getBoolean(getProfileKey(KEY_SHOW_PLAYBACK_NOTIFICATION), true)

    // E-Reader settings loaders (per-profile)
    private fun loadReaderFontSize(): Int = prefs.getInt(getProfileKey(KEY_READER_FONT_SIZE), 18)
    private fun loadReaderLineSpacing(): Float = prefs.getFloat(getProfileKey(KEY_READER_LINE_SPACING), 1.4f)
    private fun loadReaderTheme(): String = prefs.getString(getProfileKey(KEY_READER_THEME), "light") ?: "light"
    private fun loadReaderFont(): String = prefs.getString(getProfileKey(KEY_READER_FONT), "serif") ?: "serif"
    private fun loadReaderTextAlign(): String = prefs.getString(getProfileKey(KEY_READER_TEXT_ALIGN), "left") ?: "left"
    private fun loadReaderMargins(): Int = prefs.getInt(getProfileKey(KEY_READER_MARGINS), 16)
    private fun loadReaderKeepScreenOn(): Boolean = prefs.getBoolean(getProfileKey(KEY_READER_KEEP_SCREEN_ON), true)
    private fun loadReaderParagraphSpacing(): Int = prefs.getInt(getProfileKey(KEY_READER_PARAGRAPH_SPACING), 12)
    private fun loadReaderBrightness(): Float = prefs.getFloat(getProfileKey(KEY_READER_BRIGHTNESS), 1f)
    private fun loadReaderBoldText(): Boolean = prefs.getBoolean(getProfileKey(KEY_READER_BOLD_TEXT), false)
    private fun loadReaderWordSpacing(): Int = prefs.getInt(getProfileKey(KEY_READER_WORD_SPACING), 0)
    private fun loadReaderPageFitMode(): String = prefs.getString(getProfileKey(KEY_READER_PAGE_FIT_MODE), "fit") ?: "fit"
    private fun loadReaderPageGap(): Int = prefs.getInt(getProfileKey(KEY_READER_PAGE_GAP), 4)
    private fun loadReaderForceTwoPage(): Boolean = prefs.getBoolean(getProfileKey(KEY_READER_FORCE_TWO_PAGE), false)
    private fun loadReaderForceSinglePage(): Boolean = prefs.getBoolean(getProfileKey(KEY_READER_FORCE_SINGLE_PAGE), false)

    // Comic Reader loaders (per-profile)
    private fun loadComicForceTwoPage(): Boolean = prefs.getBoolean(getProfileKey(KEY_COMIC_FORCE_TWO_PAGE), false)
    private fun loadComicForceSinglePage(): Boolean = prefs.getBoolean(getProfileKey(KEY_COMIC_FORCE_SINGLE_PAGE), false)
    private fun loadComicReadingDirection(): String = prefs.getString(getProfileKey(KEY_COMIC_READING_DIRECTION), "ltr") ?: "ltr"
    private fun loadComicPageFitMode(): String = prefs.getString(getProfileKey(KEY_COMIC_PAGE_FIT_MODE), "fit") ?: "fit"
    private fun loadComicPageGap(): Int = prefs.getInt(getProfileKey(KEY_COMIC_PAGE_GAP), 4)
    private fun loadComicBackgroundColor(): String = prefs.getString(getProfileKey(KEY_COMIC_BACKGROUND_COLOR), "theme") ?: "theme"
    private fun loadComicShowPageIndicators(): Boolean = prefs.getBoolean(getProfileKey(KEY_COMIC_SHOW_PAGE_INDICATORS), true)
    private fun loadComicEnableDoubleTapZoom(): Boolean = prefs.getBoolean(getProfileKey(KEY_COMIC_ENABLE_DOUBLE_TAP_ZOOM), true)
    private fun loadComicShowControlsOnTap(): Boolean = prefs.getBoolean(getProfileKey(KEY_COMIC_SHOW_CONTROLS_ON_TAP), true)

    // Movie Player loaders (per-profile)
    private fun loadMoviePlaybackSpeed(): Float = prefs.getFloat(getProfileKey(KEY_MOVIE_PLAYBACK_SPEED), 1.0f)
    private fun loadMovieKeepScreenOn(): Boolean = prefs.getBoolean(getProfileKey(KEY_MOVIE_KEEP_SCREEN_ON), true)
    private fun loadMovieResizeMode(): String = prefs.getString(getProfileKey(KEY_MOVIE_RESIZE_MODE), "fit") ?: "fit"
    private fun loadMovieBrightness(): Float = prefs.getFloat(getProfileKey(KEY_MOVIE_BRIGHTNESS), 1.0f)
    private fun loadMovieAutoFullscreenLandscape(): Boolean = prefs.getBoolean(getProfileKey(KEY_MOVIE_AUTO_FULLSCREEN_LANDSCAPE), true)
    private fun loadMovieShowControlsOnTap(): Boolean = prefs.getBoolean(getProfileKey(KEY_MOVIE_SHOW_CONTROLS_ON_TAP), true)
    private fun loadMovieControlsTimeout(): Int = prefs.getInt(getProfileKey(KEY_MOVIE_CONTROLS_TIMEOUT), 4000)
    private fun loadMovieDoubleTapSeekDuration(): Int = prefs.getInt(getProfileKey(KEY_MOVIE_DOUBLE_TAP_SEEK_DURATION), 10)
    private fun loadMovieSwipeGesturesEnabled(): Boolean = prefs.getBoolean(getProfileKey(KEY_MOVIE_SWIPE_GESTURES_ENABLED), true)
    private fun loadMovieRememberPosition(): Boolean = prefs.getBoolean(getProfileKey(KEY_MOVIE_REMEMBER_POSITION), true)

    // Audio Enhancement loaders
    private fun loadNormalizeAudio(): Boolean = prefs.getBoolean(KEY_NORMALIZE_AUDIO, false)
    private fun loadBassBoostLevel(): Int = prefs.getInt(KEY_BASS_BOOST_LEVEL, 0)
    private fun loadEqualizerPreset(): String = normalizeEqualizerPreset(prefs.getString(KEY_EQUALIZER_PRESET, "DEFAULT"))
    private fun loadCrossfadeDuration(): Int = prefs.getInt(KEY_CROSSFADE_DURATION, 0)

    // Playback Control loaders
    private fun loadAutoRewindSeconds(): Int = prefs.getInt(getProfileKey(KEY_AUTO_REWIND_SECONDS), 0)
    private fun loadHeadsetControls(): Boolean = prefs.getBoolean(getProfileKey(KEY_HEADSET_CONTROLS), true)
    private fun loadPauseOnDisconnect(): Boolean = prefs.getBoolean(getProfileKey(KEY_PAUSE_ON_DISCONNECT), true)

    // Display settings loaders (per-profile)
    private fun loadShowPlaceholderIcons(): Boolean = prefs.getBoolean(getProfileKey(KEY_SHOW_PLACEHOLDER_ICONS), true)
    private fun loadShowFileSize(): Boolean = prefs.getBoolean(getProfileKey(KEY_SHOW_FILE_SIZE), true)
    private fun loadShowDuration(): Boolean = prefs.getBoolean(getProfileKey(KEY_SHOW_DURATION), true)
    private fun loadAnimationSpeed(): String = prefs.getString(getProfileKey(KEY_ANIMATION_SPEED), "Normal") ?: "Normal"
    private fun loadHapticFeedback(): Boolean = prefs.getBoolean(getProfileKey(KEY_HAPTIC_FEEDBACK), true)
    private fun loadConfirmBeforeDelete(): Boolean = prefs.getBoolean(getProfileKey(KEY_CONFIRM_BEFORE_DELETE), true)
    private fun loadUseSquareCorners(): Boolean = prefs.getBoolean(getProfileKey(KEY_USE_SQUARE_CORNERS), false)
    private fun loadRememberLastPosition(): Boolean = prefs.getBoolean(getProfileKey(KEY_REMEMBER_LAST_POSITION), true)

    // Filter persistence loaders (per-profile)
    private fun loadSelectedContentType(): String = prefs.getString(getProfileKey(KEY_SELECTED_CONTENT_TYPE), "AUDIOBOOK") ?: "AUDIOBOOK"
    private fun loadSelectedCategoryId(): String? = prefs.getString(getProfileKey(KEY_SELECTED_CATEGORY_ID), null)
    private fun loadCollapsedSeries(): Set<String> {
        val raw = prefs.getString(getProfileKey(KEY_COLLAPSED_SERIES), "") ?: ""
        return raw.split("|").mapNotNull { it.ifBlank { null } }.toSet()
    }

    // UI Visibility loaders (per-profile)
    private fun loadShowBackButton(): Boolean = prefs.getBoolean(getProfileKey(KEY_SHOW_BACK_BUTTON), true)
    private fun loadShowSearchBar(): Boolean = prefs.getBoolean(getProfileKey(KEY_SHOW_SEARCH_BAR), true)

    // Audio Enhancement setters
    fun setNormalizeAudio(enabled: Boolean) {
        _normalizeAudio.value = enabled
        prefs.edit().putBoolean(KEY_NORMALIZE_AUDIO, enabled).apply()
        saveAudioSettingsToFile()
    }

    fun setBassBoostLevel(level: Int) {
        _bassBoostLevel.value = level
        prefs.edit().putInt(KEY_BASS_BOOST_LEVEL, level).apply()
        saveAudioSettingsToFile()
    }

    fun setEqualizerPreset(preset: String) {
        val normalized = normalizeEqualizerPreset(preset)
        _equalizerPreset.value = normalized
        prefs.edit().putString(KEY_EQUALIZER_PRESET, normalized).apply()
        saveAudioSettingsToFile()
    }

    fun setCrossfadeDuration(seconds: Int) {
        _crossfadeDuration.value = seconds
        prefs.edit().putInt(KEY_CROSSFADE_DURATION, seconds).apply()
        saveAudioSettingsToFile()
    }

    // Playback Control setters
    fun setAutoRewindSeconds(seconds: Int) {
        _autoRewindSeconds.value = seconds
        prefs.edit().putInt(getProfileKey(KEY_AUTO_REWIND_SECONDS), seconds).apply()
        saveAudioSettingsToFile()
    }

    fun setHeadsetControls(enabled: Boolean) {
        _headsetControls.value = enabled
        prefs.edit().putBoolean(getProfileKey(KEY_HEADSET_CONTROLS), enabled).apply()
        saveAudioSettingsToFile()
    }

    fun setPauseOnDisconnect(enabled: Boolean) {
        _pauseOnDisconnect.value = enabled
        prefs.edit().putBoolean(getProfileKey(KEY_PAUSE_ON_DISCONNECT), enabled).apply()
        saveAudioSettingsToFile()
    }

    // Display settings setters (per-profile)
    fun setShowPlaceholderIcons(enabled: Boolean) {
        _showPlaceholderIcons.value = enabled
        prefs.edit().putBoolean(getProfileKey(KEY_SHOW_PLACEHOLDER_ICONS), enabled).apply()
        saveProfileSettingsToFile()
    }

    fun setShowFileSize(enabled: Boolean) {
        _showFileSize.value = enabled
        prefs.edit().putBoolean(getProfileKey(KEY_SHOW_FILE_SIZE), enabled).apply()
        saveProfileSettingsToFile()
    }

    fun setShowDuration(enabled: Boolean) {
        _showDuration.value = enabled
        prefs.edit().putBoolean(getProfileKey(KEY_SHOW_DURATION), enabled).apply()
        saveProfileSettingsToFile()
    }

    fun setAnimationSpeed(speed: String) {
        _animationSpeed.value = speed
        prefs.edit().putString(getProfileKey(KEY_ANIMATION_SPEED), speed).apply()
        saveProfileSettingsToFile()
    }

    fun setHapticFeedback(enabled: Boolean) {
        _hapticFeedback.value = enabled
        prefs.edit().putBoolean(getProfileKey(KEY_HAPTIC_FEEDBACK), enabled).apply()
        saveProfileSettingsToFile()
    }

    fun setConfirmBeforeDelete(enabled: Boolean) {
        _confirmBeforeDelete.value = enabled
        prefs.edit().putBoolean(getProfileKey(KEY_CONFIRM_BEFORE_DELETE), enabled).apply()
        saveProfileSettingsToFile()
    }

    fun setUseSquareCorners(enabled: Boolean) {
        _useSquareCorners.value = enabled
        prefs.edit().putBoolean(getProfileKey(KEY_USE_SQUARE_CORNERS), enabled).apply()
        saveProfileSettingsToFile()
    }

    fun setRememberLastPosition(enabled: Boolean) {
        _rememberLastPosition.value = enabled
        prefs.edit().putBoolean(getProfileKey(KEY_REMEMBER_LAST_POSITION), enabled).apply()
        saveAudioSettingsToFile()
    }

    // Filter persistence setters (per-profile)
    fun setSelectedContentType(contentType: String) {
        _selectedContentType.value = contentType
        prefs.edit().putString(getProfileKey(KEY_SELECTED_CONTENT_TYPE), contentType).apply()
    }

    fun setSelectedCategoryId(categoryId: String?) {
        _selectedCategoryId.value = categoryId
        if (categoryId != null) {
            prefs.edit().putString(getProfileKey(KEY_SELECTED_CATEGORY_ID), categoryId).apply()
        } else {
            prefs.edit().remove(getProfileKey(KEY_SELECTED_CATEGORY_ID)).apply()
        }
    }

    fun setCollapsedSeries(seriesIds: Set<String>) {
        _collapsedSeries.value = seriesIds
        val serialized = seriesIds.joinToString("|")
        prefs.edit().putString(getProfileKey(KEY_COLLAPSED_SERIES), serialized).apply()
    }

    // UI Visibility setters (per-profile)
    fun setShowBackButton(enabled: Boolean) {
        _showBackButton.value = enabled
        prefs.edit().putBoolean(getProfileKey(KEY_SHOW_BACK_BUTTON), enabled).apply()
    }

    fun setShowSearchBar(enabled: Boolean) {
        _showSearchBar.value = enabled
        prefs.edit().putBoolean(getProfileKey(KEY_SHOW_SEARCH_BAR), enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "audible_library_settings"
        private const val KEY_THEME = "app_theme"
        private const val KEY_ACCENT_THEME = "accent_theme"
        private const val KEY_SKIP_FORWARD = "skip_forward_duration"
        private const val KEY_SKIP_BACK = "skip_back_duration"
        private const val KEY_AUTO_BOOKMARK = "auto_bookmark"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_VOLUME_BOOST_ENABLED = "volume_boost_enabled"
        private const val KEY_VOLUME_BOOST_LEVEL = "volume_boost_level"
        private const val KEY_LIBRARY_OWNER_NAME = "library_owner_name"
        private const val KEY_PROFILES = "user_profiles"
        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_BACKGROUND_THEME = "background_theme"
        private const val KEY_READER_FONT_SIZE = "reader_font_size"
        private const val KEY_READER_LINE_SPACING = "reader_line_spacing"
        private const val KEY_READER_THEME = "reader_theme"
        private const val KEY_READER_FONT = "reader_font"
        private const val KEY_READER_TEXT_ALIGN = "reader_text_align"
        private const val KEY_READER_MARGINS = "reader_margins"
        private const val KEY_READER_KEEP_SCREEN_ON = "reader_keep_screen_on"
        private const val KEY_READER_PARAGRAPH_SPACING = "reader_paragraph_spacing"
        private const val KEY_READER_BRIGHTNESS = "reader_brightness"
        private const val KEY_READER_BOLD_TEXT = "reader_bold_text"
        private const val KEY_READER_WORD_SPACING = "reader_word_spacing"
        private const val KEY_READER_PAGE_FIT_MODE = "reader_page_fit_mode"
        private const val KEY_READER_PAGE_GAP = "reader_page_gap"
        private const val KEY_READER_FORCE_TWO_PAGE = "reader_force_two_page"
        private const val KEY_READER_FORCE_SINGLE_PAGE = "reader_force_single_page"

        // Comic Reader keys
        private const val KEY_COMIC_FORCE_TWO_PAGE = "comic_force_two_page"
        private const val KEY_COMIC_FORCE_SINGLE_PAGE = "comic_force_single_page"
        private const val KEY_COMIC_READING_DIRECTION = "comic_reading_direction"
        private const val KEY_COMIC_PAGE_FIT_MODE = "comic_page_fit_mode"
        private const val KEY_COMIC_PAGE_GAP = "comic_page_gap"
        private const val KEY_COMIC_BACKGROUND_COLOR = "comic_background_color"
        private const val KEY_COMIC_SHOW_PAGE_INDICATORS = "comic_show_page_indicators"
        private const val KEY_COMIC_ENABLE_DOUBLE_TAP_ZOOM = "comic_enable_double_tap_zoom"
        private const val KEY_COMIC_SHOW_CONTROLS_ON_TAP = "comic_show_controls_on_tap"

        // Movie Player keys
        private const val KEY_MOVIE_PLAYBACK_SPEED = "movie_playback_speed"
        private const val KEY_MOVIE_KEEP_SCREEN_ON = "movie_keep_screen_on"
        private const val KEY_MOVIE_RESIZE_MODE = "movie_resize_mode"
        private const val KEY_MOVIE_BRIGHTNESS = "movie_brightness"
        private const val KEY_MOVIE_AUTO_FULLSCREEN_LANDSCAPE = "movie_auto_fullscreen_landscape"
        private const val KEY_MOVIE_SHOW_CONTROLS_ON_TAP = "movie_show_controls_on_tap"
        private const val KEY_MOVIE_CONTROLS_TIMEOUT = "movie_controls_timeout"
        private const val KEY_MOVIE_DOUBLE_TAP_SEEK_DURATION = "movie_double_tap_seek_duration"
        private const val KEY_MOVIE_SWIPE_GESTURES_ENABLED = "movie_swipe_gestures_enabled"
        private const val KEY_MOVIE_REMEMBER_POSITION = "movie_remember_position"

        private const val KEY_PLAYBACK_SPEED = "playback_speed"
        private const val KEY_SLEEP_TIMER_MINUTES = "sleep_timer_minutes"
        private const val KEY_AUTO_PLAY_NEXT = "auto_play_next"
        private const val KEY_DEFAULT_LIBRARY_VIEW = "default_library_view"
        private const val KEY_DEFAULT_SORT_ORDER = "default_sort_order"
        private const val KEY_RESUME_PLAYBACK = "resume_playback"
        private const val KEY_SHOW_PLAYBACK_NOTIFICATION = "show_playback_notification"

        // Audio Enhancement keys
        private const val KEY_NORMALIZE_AUDIO = "normalize_audio"
        private const val KEY_BASS_BOOST_LEVEL = "bass_boost_level"
        private const val KEY_EQUALIZER_PRESET = "equalizer_preset"
        private const val KEY_CROSSFADE_DURATION = "crossfade_duration"

        // Playback Control keys
        private const val KEY_AUTO_REWIND_SECONDS = "auto_rewind_seconds"
        private const val KEY_HEADSET_CONTROLS = "headset_controls"
        private const val KEY_PAUSE_ON_DISCONNECT = "pause_on_disconnect"

        // Display settings keys
        private const val KEY_SHOW_PLACEHOLDER_ICONS = "show_placeholder_icons"
        private const val KEY_SHOW_FILE_SIZE = "show_file_size"
        private const val KEY_SHOW_DURATION = "show_duration"
        private const val KEY_ANIMATION_SPEED = "animation_speed"
        private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
        private const val KEY_CONFIRM_BEFORE_DELETE = "confirm_before_delete"
        private const val KEY_REMEMBER_LAST_POSITION = "remember_last_position"
        private const val KEY_USE_SQUARE_CORNERS = "use_square_corners"

        // Filter persistence keys
        private const val KEY_SELECTED_CONTENT_TYPE = "selected_content_type"
        private const val KEY_SELECTED_CATEGORY_ID = "selected_category_id"
        private const val KEY_COLLAPSED_SERIES = "collapsed_series"

        // UI Visibility keys
        private const val KEY_SHOW_BACK_BUTTON = "show_back_button"
        private const val KEY_SHOW_SEARCH_BAR = "show_search_bar"

        // Custom theme color keys
        private const val KEY_CUSTOM_PRIMARY_COLOR = "custom_primary_color"
        private const val KEY_CUSTOM_ACCENT_COLOR = "custom_accent_color"
        private const val KEY_CUSTOM_BACKGROUND_COLOR = "custom_background_color"
        private const val KEY_APP_SCALE = "app_scale"
        private const val KEY_UI_FONT_SCALE = "ui_font_scale"
        private const val KEY_UI_FONT_FAMILY = "ui_font_family"
        private const val KEY_LAST_MUSIC_ID = "last_music_id"
        private const val KEY_LAST_MUSIC_POSITION = "last_music_position"
        private const val KEY_LAST_MUSIC_PLAYING = "last_music_playing"
        private const val KEY_LAST_AUDIOBOOK_ID = "last_audiobook_id"
        private const val KEY_LAST_AUDIOBOOK_POSITION = "last_audiobook_position"
        private const val KEY_LAST_AUDIOBOOK_PLAYING = "last_audiobook_playing"
        private const val KEY_LAST_ACTIVE_TYPE = "last_active_type"
        private const val KEY_MUSIC_SHUFFLE_ENABLED = "music_shuffle_enabled"
        private const val KEY_MUSIC_REPEAT_MODE = "music_repeat_mode"
    }
}
