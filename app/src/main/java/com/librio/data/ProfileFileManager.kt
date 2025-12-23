package com.librio.data

import android.os.Environment
import com.librio.model.AudioSettings
import com.librio.model.ComicSettings
import com.librio.model.MovieSettings
import com.librio.model.ProfileSettings
import com.librio.model.ReaderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manages file-based storage of profile settings
 * Handles reading and writing JSON configuration files for each profile
 */
class ProfileFileManager {

    companion object {
        const val PROFILE_SETTINGS_FILE = "profile_settings.json"
        const val AUDIO_SETTINGS_FILE = "audio_settings.json"
        const val READER_SETTINGS_FILE = "reader_settings.json"
        const val COMIC_SETTINGS_FILE = "comic_settings.json"
        const val MOVIE_SETTINGS_FILE = "movie_settings.json"
        const val PROGRESS_FILE = "progress.json"
        const val PLAYLISTS_FOLDER = "Playlists"
        const val PROFILE_BACKUP_EXTENSION = ".librio-profile.json"
        private const val PROFILE_BACKUP_FOLDER = "Backups"
        private const val PROFILE_BACKUP_IMPORTED_FOLDER = "Imported"
    }

    private val librioRoot = File(Environment.getExternalStorageDirectory(), "Librio")
    private val profilesRoot = File(librioRoot, "Profiles")

    /**
     * Get the profile folder for a given profile name
     */
    fun getProfileFolder(profileName: String): File {
        return File(profilesRoot, sanitizeFolderName(profileName))
    }

    fun getProgressFile(profileName: String): File {
        return File(getProfileFolder(profileName), PROGRESS_FILE)
    }

    fun getPlaylistsFolder(profileName: String): File {
        return File(getProfileFolder(profileName), PLAYLISTS_FOLDER)
    }

    fun getProfilesRoot(): File {
        return profilesRoot
    }

    fun getBackupsFolder(): File {
        return File(profilesRoot, PROFILE_BACKUP_FOLDER)
    }

    fun getImportedBackupsFolder(): File {
        return File(getBackupsFolder(), PROFILE_BACKUP_IMPORTED_FOLDER)
    }

    fun getBackupFile(profileName: String, suffix: String? = null): File {
        val safeName = sanitizeFolderName(profileName).ifBlank { "Profile" }
        val fileName = if (suffix.isNullOrBlank()) safeName else "${safeName}_$suffix"
        return File(getBackupsFolder(), "$fileName$PROFILE_BACKUP_EXTENSION")
    }

    fun listPendingBackupFiles(): List<File> {
        if (!profilesRoot.exists()) return emptyList()
        return profilesRoot.listFiles()?.filter { file ->
            file.isFile && file.name.endsWith(PROFILE_BACKUP_EXTENSION, ignoreCase = true)
        } ?: emptyList()
    }

    fun readJsonFile(file: File): JSONObject? {
        if (!file.exists()) return null
        return try {
            JSONObject(file.readText())
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun writeJsonFile(file: File, json: JSONObject): Boolean {
        return try {
            file.parentFile?.mkdirs()
            file.writeText(json.toString(2))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun readSettingsJson(profileName: String, fileName: String): JSONObject? {
        val file = File(getProfileFolder(profileName), fileName)
        return readJsonFile(file)
    }

    fun writeSettingsJson(profileName: String, fileName: String, json: JSONObject): Boolean {
        val file = File(getProfileFolder(profileName), fileName)
        return writeJsonFile(file, json)
    }

    fun moveBackupToImported(backupFile: File): File? {
        if (!backupFile.exists()) return null
        val importedFolder = getImportedBackupsFolder()
        importedFolder.mkdirs()
        var destination = File(importedFolder, backupFile.name)
        if (destination.exists()) {
            val uniqueSuffix = System.currentTimeMillis().toString()
            val baseName = backupFile.name.removeSuffix(PROFILE_BACKUP_EXTENSION)
            destination = File(importedFolder, "${baseName}_$uniqueSuffix$PROFILE_BACKUP_EXTENSION")
        }
        return try {
            if (backupFile.renameTo(destination)) {
                destination
            } else {
                backupFile.copyTo(destination, overwrite = true)
                backupFile.delete()
                destination
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Sanitize folder name to be filesystem safe
     */
    fun sanitizeFolderName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }

    // ==================== Profile Settings ====================

    /**
     * Load profile settings from JSON file
     */
    suspend fun loadProfileSettings(profileName: String): ProfileSettings? = withContext(Dispatchers.IO) {
        val file = File(getProfileFolder(profileName), PROFILE_SETTINGS_FILE)
        if (!file.exists()) return@withContext null

        try {
            val json = JSONObject(file.readText())
            ProfileSettings(
                version = json.optInt("version", 1),
                id = json.optString("id", ""),
                name = json.optString("name", profileName),
                profilePicture = json.optString("profilePicture").takeIf { it.isNotEmpty() },
                theme = json.optString("theme", "DARK_TEAL"),
                darkMode = json.optBoolean("darkMode", false),
                accentTheme = json.optString("accentTheme", "DARK_TEAL"),
                backgroundTheme = json.optString("backgroundTheme", "WHITE"),
                customPrimaryColor = json.optInt("customPrimaryColor", 0x00897B),
                customAccentColor = json.optInt("customAccentColor", 0x26A69A),
                customBackgroundColor = json.optInt("customBackgroundColor", 0x121212),
                appScale = json.optDouble("appScale", 1.0).toFloat(),
                uiFontScale = json.optDouble("uiFontScale", 1.0).toFloat(),
                uiFontFamily = json.optString("uiFontFamily", "Default"),
                libraryOwnerName = json.optString("libraryOwnerName", profileName),
                defaultLibraryView = json.optString("defaultLibraryView", "GRID"),
                defaultSortOrder = json.optString("defaultSortOrder", "TITLE"),
                showPlaceholderIcons = json.optBoolean("showPlaceholderIcons", true),
                showFileSize = json.optBoolean("showFileSize", true),
                showDuration = json.optBoolean("showDuration", true),
                showBackButton = json.optBoolean("showBackButton", true),
                showSearchBar = json.optBoolean("showSearchBar", true),
                animationSpeed = json.optString("animationSpeed", "Normal"),
                hapticFeedback = json.optBoolean("hapticFeedback", true),
                confirmBeforeDelete = json.optBoolean("confirmBeforeDelete", true),
                collapsedSeries = json.optJSONArray("collapsedSeries")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                collapsedPlaylists = json.optJSONArray("collapsedPlaylists")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                selectedContentType = json.optString("selectedContentType", "AUDIOBOOK"),
                selectedAudiobookCategoryId = json.optString("selectedAudiobookCategoryId").takeIf { it.isNotEmpty() },
                selectedBookCategoryId = json.optString("selectedBookCategoryId").takeIf { it.isNotEmpty() },
                selectedMusicCategoryId = json.optString("selectedMusicCategoryId").takeIf { it.isNotEmpty() },
                selectedComicCategoryId = json.optString("selectedComicCategoryId").takeIf { it.isNotEmpty() },
                selectedMovieCategoryId = json.optString("selectedMovieCategoryId").takeIf { it.isNotEmpty() },
                lastModified = json.optLong("lastModified", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Save profile settings to JSON file
     */
    suspend fun saveProfileSettings(profileName: String, settings: ProfileSettings) = withContext(Dispatchers.IO) {
        val folder = getProfileFolder(profileName)
        folder.mkdirs()
        val file = File(folder, PROFILE_SETTINGS_FILE)

        try {
            val json = JSONObject().apply {
                put("version", settings.version)
                put("id", settings.id)
                put("name", settings.name)
                put("profilePicture", settings.profilePicture ?: "")
                put("theme", settings.theme)
                put("darkMode", settings.darkMode)
                put("accentTheme", settings.accentTheme)
                put("backgroundTheme", settings.backgroundTheme)
                put("customPrimaryColor", settings.customPrimaryColor)
                put("customAccentColor", settings.customAccentColor)
                put("customBackgroundColor", settings.customBackgroundColor)
                put("appScale", settings.appScale.toDouble())
                put("uiFontScale", settings.uiFontScale.toDouble())
                put("uiFontFamily", settings.uiFontFamily)
                put("libraryOwnerName", settings.libraryOwnerName)
                put("defaultLibraryView", settings.defaultLibraryView)
                put("defaultSortOrder", settings.defaultSortOrder)
                put("showPlaceholderIcons", settings.showPlaceholderIcons)
                put("showFileSize", settings.showFileSize)
                put("showDuration", settings.showDuration)
                put("showBackButton", settings.showBackButton)
                put("showSearchBar", settings.showSearchBar)
                put("animationSpeed", settings.animationSpeed)
                put("hapticFeedback", settings.hapticFeedback)
                put("confirmBeforeDelete", settings.confirmBeforeDelete)
                put("collapsedSeries", JSONArray(settings.collapsedSeries))
                put("collapsedPlaylists", JSONArray(settings.collapsedPlaylists))
                put("selectedContentType", settings.selectedContentType)
                put("selectedAudiobookCategoryId", settings.selectedAudiobookCategoryId ?: "")
                put("selectedBookCategoryId", settings.selectedBookCategoryId ?: "")
                put("selectedMusicCategoryId", settings.selectedMusicCategoryId ?: "")
                put("selectedComicCategoryId", settings.selectedComicCategoryId ?: "")
                put("selectedMovieCategoryId", settings.selectedMovieCategoryId ?: "")
                put("lastModified", System.currentTimeMillis())
            }
            file.writeText(json.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==================== Audio Settings ====================

    /**
     * Load audio settings from JSON file
     */
    suspend fun loadAudioSettings(profileName: String): AudioSettings? = withContext(Dispatchers.IO) {
        val file = File(getProfileFolder(profileName), AUDIO_SETTINGS_FILE)
        if (!file.exists()) return@withContext null

        try {
            val json = JSONObject(file.readText())
            AudioSettings(
                version = json.optInt("version", 1),
                playbackSpeed = json.optDouble("playbackSpeed", 1.0).toFloat(),
                skipForwardDuration = json.optInt("skipForwardDuration", 30),
                skipBackDuration = json.optInt("skipBackDuration", 10),
                sleepTimerMinutes = json.optInt("sleepTimerMinutes", 0),
                autoBookmark = json.optBoolean("autoBookmark", true),
                keepScreenOn = json.optBoolean("keepScreenOn", false),
                autoPlayNext = json.optBoolean("autoPlayNext", true),
                resumePlayback = json.optBoolean("resumePlayback", true),
                rememberLastPosition = json.optBoolean("rememberLastPosition", true),
                autoRewindSeconds = json.optInt("autoRewindSeconds", 0),
                volumeBoostEnabled = json.optBoolean("volumeBoostEnabled", false),
                volumeBoostLevel = json.optDouble("volumeBoostLevel", 1.5).toFloat(),
                normalizeAudio = json.optBoolean("normalizeAudio", false),
                bassBoostLevel = json.optDouble("bassBoostLevel", 0.0).toFloat(),
                equalizerPreset = json.optString("equalizerPreset", "DEFAULT"),
                headsetControls = json.optBoolean("headsetControls", true),
                pauseOnDisconnect = json.optBoolean("pauseOnDisconnect", true),
                showPlaybackNotification = json.optBoolean("showPlaybackNotification", true),
                lastMusicId = json.optString("lastMusicId").takeIf { it.isNotEmpty() },
                lastMusicPosition = json.optLong("lastMusicPosition", 0L),
                lastMusicPlaying = json.optBoolean("lastMusicPlaying", false),
                lastAudiobookId = json.optString("lastAudiobookId").takeIf { it.isNotEmpty() },
                lastAudiobookPosition = json.optLong("lastAudiobookPosition", 0L),
                lastAudiobookPlaying = json.optBoolean("lastAudiobookPlaying", false),
                lastActiveType = json.optString("lastActiveType").takeIf { it.isNotEmpty() },
                musicShuffleEnabled = json.optBoolean("musicShuffleEnabled", false),
                musicRepeatMode = json.optInt("musicRepeatMode", 0),
                lastModified = json.optLong("lastModified", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Save audio settings to JSON file
     */
    suspend fun saveAudioSettings(profileName: String, settings: AudioSettings) = withContext(Dispatchers.IO) {
        val folder = getProfileFolder(profileName)
        folder.mkdirs()
        val file = File(folder, AUDIO_SETTINGS_FILE)

        try {
            val json = JSONObject().apply {
                put("version", settings.version)
                put("playbackSpeed", settings.playbackSpeed.toDouble())
                put("skipForwardDuration", settings.skipForwardDuration)
                put("skipBackDuration", settings.skipBackDuration)
                put("sleepTimerMinutes", settings.sleepTimerMinutes)
                put("autoBookmark", settings.autoBookmark)
                put("keepScreenOn", settings.keepScreenOn)
                put("autoPlayNext", settings.autoPlayNext)
                put("resumePlayback", settings.resumePlayback)
                put("rememberLastPosition", settings.rememberLastPosition)
                put("autoRewindSeconds", settings.autoRewindSeconds)
                put("volumeBoostEnabled", settings.volumeBoostEnabled)
                put("volumeBoostLevel", settings.volumeBoostLevel.toDouble())
                put("normalizeAudio", settings.normalizeAudio)
                put("bassBoostLevel", settings.bassBoostLevel.toDouble())
                put("equalizerPreset", settings.equalizerPreset)
                put("headsetControls", settings.headsetControls)
                put("pauseOnDisconnect", settings.pauseOnDisconnect)
                put("showPlaybackNotification", settings.showPlaybackNotification)
                put("lastMusicId", settings.lastMusicId ?: "")
                put("lastMusicPosition", settings.lastMusicPosition)
                put("lastMusicPlaying", settings.lastMusicPlaying)
                put("lastAudiobookId", settings.lastAudiobookId ?: "")
                put("lastAudiobookPosition", settings.lastAudiobookPosition)
                put("lastAudiobookPlaying", settings.lastAudiobookPlaying)
                put("lastActiveType", settings.lastActiveType ?: "")
                put("musicShuffleEnabled", settings.musicShuffleEnabled)
                put("musicRepeatMode", settings.musicRepeatMode)
                put("lastModified", System.currentTimeMillis())
            }
            file.writeText(json.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==================== Reader Settings ====================

    /**
     * Load reader settings from JSON file
     */
    suspend fun loadReaderSettings(profileName: String): ReaderSettings? = withContext(Dispatchers.IO) {
        val file = File(getProfileFolder(profileName), READER_SETTINGS_FILE)
        if (!file.exists()) return@withContext null

        try {
            val json = JSONObject(file.readText())
            ReaderSettings(
                version = json.optInt("version", 1),
                fontSize = json.optInt("fontSize", 18),
                lineSpacing = json.optDouble("lineSpacing", 1.4).toFloat(),
                readerTheme = json.optString("readerTheme", "light"),
                fontFamily = json.optString("fontFamily", "serif"),
                textAlignment = json.optString("textAlignment", "left"),
                margins = json.optInt("margins", 16),
                paragraphSpacing = json.optInt("paragraphSpacing", 12),
                brightness = json.optDouble("brightness", 1.0).toFloat(),
                boldText = json.optBoolean("boldText", false),
                wordSpacing = json.optInt("wordSpacing", 0),
                pageFitMode = json.optString("pageFitMode", "fit"),
                pageGap = json.optInt("pageGap", 4),
                forceTwoPage = json.optBoolean("forceTwoPage", false),
                forceSinglePage = json.optBoolean("forceSinglePage", false),
                keepScreenOn = json.optBoolean("keepScreenOn", true),
                lastModified = json.optLong("lastModified", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Save reader settings to JSON file
     */
    suspend fun saveReaderSettings(profileName: String, settings: ReaderSettings) = withContext(Dispatchers.IO) {
        val folder = getProfileFolder(profileName)
        folder.mkdirs()
        val file = File(folder, READER_SETTINGS_FILE)

        try {
            val json = JSONObject().apply {
                put("version", settings.version)
                put("fontSize", settings.fontSize)
                put("lineSpacing", settings.lineSpacing.toDouble())
                put("readerTheme", settings.readerTheme)
                put("fontFamily", settings.fontFamily)
                put("textAlignment", settings.textAlignment)
                put("margins", settings.margins)
                put("paragraphSpacing", settings.paragraphSpacing)
                put("brightness", settings.brightness.toDouble())
                put("boldText", settings.boldText)
                put("wordSpacing", settings.wordSpacing)
                put("pageFitMode", settings.pageFitMode)
                put("pageGap", settings.pageGap)
                put("forceTwoPage", settings.forceTwoPage)
                put("forceSinglePage", settings.forceSinglePage)
                put("keepScreenOn", settings.keepScreenOn)
                put("lastModified", System.currentTimeMillis())
            }
            file.writeText(json.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==================== Comic Settings ====================

    /**
     * Load comic settings from JSON file
     */
    suspend fun loadComicSettings(profileName: String): ComicSettings? = withContext(Dispatchers.IO) {
        val file = File(getProfileFolder(profileName), COMIC_SETTINGS_FILE)
        if (!file.exists()) return@withContext null

        try {
            val json = JSONObject(file.readText())
            ComicSettings(
                version = json.optInt("version", 1),
                forceTwoPage = json.optBoolean("forceTwoPage", false),
                forceSinglePage = json.optBoolean("forceSinglePage", false),
                readingDirection = json.optString("readingDirection", "ltr"),
                pageFitMode = json.optString("pageFitMode", "fit"),
                pageGap = json.optInt("pageGap", 4),
                backgroundColor = json.optString("backgroundColor", "theme"),
                showPageIndicators = json.optBoolean("showPageIndicators", true),
                enableDoubleTapZoom = json.optBoolean("enableDoubleTapZoom", true),
                showControlsOnTap = json.optBoolean("showControlsOnTap", true),
                keepScreenOn = json.optBoolean("keepScreenOn", true),
                lastModified = json.optLong("lastModified", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Save comic settings to JSON file
     */
    suspend fun saveComicSettings(profileName: String, settings: ComicSettings) = withContext(Dispatchers.IO) {
        val folder = getProfileFolder(profileName)
        folder.mkdirs()
        val file = File(folder, COMIC_SETTINGS_FILE)

        try {
            val json = JSONObject().apply {
                put("version", settings.version)
                put("forceTwoPage", settings.forceTwoPage)
                put("forceSinglePage", settings.forceSinglePage)
                put("readingDirection", settings.readingDirection)
                put("pageFitMode", settings.pageFitMode)
                put("pageGap", settings.pageGap)
                put("backgroundColor", settings.backgroundColor)
                put("showPageIndicators", settings.showPageIndicators)
                put("enableDoubleTapZoom", settings.enableDoubleTapZoom)
                put("showControlsOnTap", settings.showControlsOnTap)
                put("keepScreenOn", settings.keepScreenOn)
                put("lastModified", System.currentTimeMillis())
            }
            file.writeText(json.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==================== Movie Settings ====================

    /**
     * Load movie settings from JSON file
     */
    suspend fun loadMovieSettings(profileName: String): MovieSettings? = withContext(Dispatchers.IO) {
        val file = File(getProfileFolder(profileName), MOVIE_SETTINGS_FILE)
        if (!file.exists()) return@withContext null

        try {
            val json = JSONObject(file.readText())
            MovieSettings(
                version = json.optInt("version", 1),
                playbackSpeed = json.optDouble("playbackSpeed", 1.0).toFloat(),
                keepScreenOn = json.optBoolean("keepScreenOn", true),
                resizeMode = json.optString("resizeMode", "fit"),
                brightness = json.optDouble("brightness", 1.0).toFloat(),
                autoFullscreenLandscape = json.optBoolean("autoFullscreenLandscape", true),
                showControlsOnTap = json.optBoolean("showControlsOnTap", true),
                controlsTimeout = json.optInt("controlsTimeout", 4000),
                doubleTapSeekDuration = json.optInt("doubleTapSeekDuration", 10),
                swipeGesturesEnabled = json.optBoolean("swipeGesturesEnabled", true),
                swipeBrightnessEnabled = json.optBoolean("swipeBrightnessEnabled", true),
                swipeVolumeEnabled = json.optBoolean("swipeVolumeEnabled", true),
                swipeSeekEnabled = json.optBoolean("swipeSeekEnabled", true),
                rememberPosition = json.optBoolean("rememberPosition", true),
                autoPlayNext = json.optBoolean("autoPlayNext", false),
                skipIntroEnabled = json.optBoolean("skipIntroEnabled", false),
                skipIntroSeconds = json.optInt("skipIntroSeconds", 0),
                defaultAudioTrack = json.optString("defaultAudioTrack", "auto"),
                defaultSubtitleTrack = json.optString("defaultSubtitleTrack", "off"),
                subtitleSize = json.optDouble("subtitleSize", 1.0).toFloat(),
                subtitleBackground = json.optBoolean("subtitleBackground", false),
                lastModified = json.optLong("lastModified", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Save movie settings to JSON file
     */
    suspend fun saveMovieSettings(profileName: String, settings: MovieSettings) = withContext(Dispatchers.IO) {
        val folder = getProfileFolder(profileName)
        folder.mkdirs()
        val file = File(folder, MOVIE_SETTINGS_FILE)

        try {
            val json = JSONObject().apply {
                put("version", settings.version)
                put("playbackSpeed", settings.playbackSpeed.toDouble())
                put("keepScreenOn", settings.keepScreenOn)
                put("resizeMode", settings.resizeMode)
                put("brightness", settings.brightness.toDouble())
                put("autoFullscreenLandscape", settings.autoFullscreenLandscape)
                put("showControlsOnTap", settings.showControlsOnTap)
                put("controlsTimeout", settings.controlsTimeout)
                put("doubleTapSeekDuration", settings.doubleTapSeekDuration)
                put("swipeGesturesEnabled", settings.swipeGesturesEnabled)
                put("swipeBrightnessEnabled", settings.swipeBrightnessEnabled)
                put("swipeVolumeEnabled", settings.swipeVolumeEnabled)
                put("swipeSeekEnabled", settings.swipeSeekEnabled)
                put("rememberPosition", settings.rememberPosition)
                put("autoPlayNext", settings.autoPlayNext)
                put("skipIntroEnabled", settings.skipIntroEnabled)
                put("skipIntroSeconds", settings.skipIntroSeconds)
                put("defaultAudioTrack", settings.defaultAudioTrack)
                put("defaultSubtitleTrack", settings.defaultSubtitleTrack)
                put("subtitleSize", settings.subtitleSize.toDouble())
                put("subtitleBackground", settings.subtitleBackground)
                put("lastModified", System.currentTimeMillis())
            }
            file.writeText(json.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Check if profile settings file exists
     */
    fun profileSettingsExist(profileName: String): Boolean {
        return File(getProfileFolder(profileName), PROFILE_SETTINGS_FILE).exists()
    }

    /**
     * Delete all settings files for a profile
     */
    suspend fun deleteProfileSettings(profileName: String) = withContext(Dispatchers.IO) {
        val folder = getProfileFolder(profileName)
        listOf(PROFILE_SETTINGS_FILE, AUDIO_SETTINGS_FILE, READER_SETTINGS_FILE, COMIC_SETTINGS_FILE, MOVIE_SETTINGS_FILE).forEach {
            File(folder, it).delete()
        }
    }

    /**
     * Rename profile folder (when profile is renamed)
     */
    suspend fun renameProfile(oldName: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val oldFolder = getProfileFolder(oldName)
        val newFolder = getProfileFolder(newName)

        if (!oldFolder.exists()) {
            // Old folder doesn't exist, create new one
            newFolder.mkdirs()
            return@withContext true
        }

        if (newFolder.exists()) {
            return@withContext false
        }

        oldFolder.renameTo(newFolder)
    }

    /**
     * Get list of all profile folders
     */
    fun getAllProfileFolders(): List<File> {
        if (!profilesRoot.exists()) return emptyList()
        return profilesRoot.listFiles()?.filter { it.isDirectory } ?: emptyList()
    }
}
