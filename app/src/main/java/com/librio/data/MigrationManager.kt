package com.librio.data

import android.content.Context
import android.content.SharedPreferences
import com.librio.model.AudioSettings
import com.librio.model.ComicSettings
import com.librio.model.ContentType
import com.librio.model.ProfileSettings
import com.librio.model.ReaderSettings
import com.librio.ui.screens.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Handles migration from SharedPreferences-based storage to file-based storage.
 * This includes:
 * - Profile settings stored in profile_settings.json
 * - Audio settings stored in audio_settings.json
 * - Reader settings stored in reader_settings.json
 * - Comic settings stored in comic_settings.json
 * - Series/playlists migrated to folders
 */
class MigrationManager(
    private val context: Context,
    private val profileFileManager: ProfileFileManager,
    private val playlistFolderManager: PlaylistFolderManager
) {

    companion object {
        private const val MIGRATION_PREFS_NAME = "migration_prefs"
        private const val KEY_MIGRATION_VERSION = "migration_version"
        private const val CURRENT_MIGRATION_VERSION = 1

        // Legacy SharedPreferences keys
        private const val LEGACY_PREFS_NAME = "audible_library_settings"
        private const val KEY_PROFILES = "user_profiles"
        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
    }

    private val migrationPrefs: SharedPreferences = context.getSharedPreferences(MIGRATION_PREFS_NAME, Context.MODE_PRIVATE)
    private val legacyPrefs: SharedPreferences = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
    private val libraryPrefs: SharedPreferences = context.getSharedPreferences("library_prefs", Context.MODE_PRIVATE)

    /**
     * Check if migration is needed
     */
    fun isMigrationNeeded(): Boolean {
        val currentVersion = migrationPrefs.getInt(KEY_MIGRATION_VERSION, 0)
        return currentVersion < CURRENT_MIGRATION_VERSION
    }

    /**
     * Perform migration from SharedPreferences to file-based storage
     */
    suspend fun performMigration(): MigrationResult = withContext(Dispatchers.IO) {
        try {
            // Get list of profiles from legacy storage
            val profiles = loadLegacyProfiles()

            if (profiles.isEmpty()) {
                // No profiles to migrate, mark as complete
                markMigrationComplete()
                return@withContext MigrationResult.Success(0)
            }

            var migratedCount = 0

            profiles.forEach { profile ->
                // Skip if already migrated (file exists)
                if (profileFileManager.profileSettingsExist(profile.name)) {
                    migratedCount++
                    return@forEach
                }

                // Migrate profile settings
                migrateProfileSettings(profile)

                // Migrate audio settings
                migrateAudioSettings(profile)

                // Migrate reader settings
                migrateReaderSettings(profile.name)

                // Migrate comic settings
                migrateComicSettings(profile.name)

                // Migrate series to playlist folders
                migrateSeriesAsPlaylistFolders(profile.name)

                // Ensure content folders exist
                playlistFolderManager.ensureContentFoldersExist(profile.name)

                migratedCount++
            }

            markMigrationComplete()
            MigrationResult.Success(migratedCount)
        } catch (e: Exception) {
            e.printStackTrace()
            MigrationResult.Error(e.message ?: "Unknown error during migration")
        }
    }

    /**
     * Load profiles from legacy SharedPreferences storage
     */
    private fun loadLegacyProfiles(): List<UserProfile> {
        val profilesJson = legacyPrefs.getString(KEY_PROFILES, null) ?: return listOf(
            UserProfile(id = "default", name = "Default", isActive = true)
        )

        val activeId = legacyPrefs.getString(KEY_ACTIVE_PROFILE_ID, "default") ?: "default"

        return try {
            profilesJson.split("|").mapNotNull { entry ->
                val parts = if (entry.contains(";")) entry.split(";") else entry.split(":")
                when {
                    parts.size >= 15 -> UserProfile(
                        id = parts[0],
                        name = parts[1],
                        isActive = parts[0] == activeId,
                        theme = parts[2],
                        darkMode = parts[3].toBooleanStrictOrNull() ?: false,
                        profilePicture = parts[4].takeIf { it.isNotEmpty() },
                        playbackSpeed = parts[5].toFloatOrNull() ?: 1.0f,
                        skipForwardDuration = parts[6].toIntOrNull() ?: 30,
                        skipBackDuration = parts[7].toIntOrNull() ?: 10,
                        volumeBoostEnabled = parts[8].toBooleanStrictOrNull() ?: false,
                        volumeBoostLevel = parts[9].toFloatOrNull() ?: 1.0f,
                        normalizeAudio = parts[11].toBooleanStrictOrNull() ?: false,
                        bassBoostLevel = parts[12].toFloatOrNull() ?: 0f,
                        equalizerPreset = parts[13],
                        sleepTimerMinutes = parts[14].toIntOrNull() ?: 0
                    )
                    parts.size >= 5 -> UserProfile(
                        id = parts[0],
                        name = parts[1],
                        isActive = parts[0] == activeId,
                        theme = parts[2],
                        darkMode = parts[3].toBooleanStrictOrNull() ?: false,
                        profilePicture = parts[4].takeIf { it.isNotEmpty() }
                    )
                    parts.size >= 2 -> UserProfile(
                        id = parts[0],
                        name = parts[1],
                        isActive = parts[0] == activeId
                    )
                    else -> null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            listOf(UserProfile(id = "default", name = "Default", isActive = true))
        }
    }

    /**
     * Migrate profile settings to JSON file
     */
    private suspend fun migrateProfileSettings(profile: UserProfile) {
        val sanitizedName = profile.name.replace(Regex("[^a-zA-Z0-9]"), "_")

        fun getProfileKey(baseKey: String): String = "${baseKey}_profile_${sanitizedName}"

        val settings = ProfileSettings(
            version = 1,
            id = profile.id,
            name = profile.name,
            profilePicture = profile.profilePicture,
            theme = profile.theme,
            darkMode = profile.darkMode,
            accentTheme = legacyPrefs.getString(getProfileKey("accent_theme"), profile.theme) ?: profile.theme,
            backgroundTheme = legacyPrefs.getString(getProfileKey("background_theme"), "WHITE") ?: "WHITE",
            customPrimaryColor = legacyPrefs.getInt(getProfileKey("custom_primary_color"), 0x00897B),
            customAccentColor = legacyPrefs.getInt(getProfileKey("custom_accent_color"), 0x26A69A),
            customBackgroundColor = legacyPrefs.getInt(getProfileKey("custom_background_color"), 0x121212),
            appScale = legacyPrefs.getFloat(getProfileKey("app_scale"), 1.0f),
            uiFontScale = legacyPrefs.getFloat(getProfileKey("ui_font_scale"), 1.0f),
            uiFontFamily = legacyPrefs.getString(getProfileKey("ui_font_family"), "Default") ?: "Default",
            libraryOwnerName = legacyPrefs.getString(getProfileKey("library_owner_name"), profile.name) ?: profile.name,
            defaultLibraryView = legacyPrefs.getString(getProfileKey("default_library_view"), "GRID") ?: "GRID",
            defaultSortOrder = legacyPrefs.getString(getProfileKey("default_sort_order"), "TITLE") ?: "TITLE",
            showPlaceholderIcons = legacyPrefs.getBoolean(getProfileKey("show_placeholder_icons"), true),
            showFileSize = legacyPrefs.getBoolean(getProfileKey("show_file_size"), true),
            showDuration = legacyPrefs.getBoolean(getProfileKey("show_duration"), true),
            showBackButton = legacyPrefs.getBoolean(getProfileKey("show_back_button"), true),
            showSearchBar = legacyPrefs.getBoolean(getProfileKey("show_search_bar"), true),
            animationSpeed = legacyPrefs.getString(getProfileKey("animation_speed"), "Normal") ?: "Normal",
            hapticFeedback = legacyPrefs.getBoolean(getProfileKey("haptic_feedback"), true),
            confirmBeforeDelete = legacyPrefs.getBoolean(getProfileKey("confirm_before_delete"), true),
            collapsedSeries = legacyPrefs.getString(getProfileKey("collapsed_series"), "")
                ?.split("|")?.filter { it.isNotBlank() } ?: emptyList(),
            collapsedPlaylists = emptyList(),
            selectedContentType = legacyPrefs.getString(getProfileKey("selected_content_type"), "AUDIOBOOK") ?: "AUDIOBOOK",
            selectedAudiobookCategoryId = legacyPrefs.getString(getProfileKey("selected_audiobook_category_id"), null),
            selectedBookCategoryId = legacyPrefs.getString(getProfileKey("selected_book_category_id"), null),
            selectedMusicCategoryId = legacyPrefs.getString(getProfileKey("selected_music_category_id"), null),
            selectedComicCategoryId = legacyPrefs.getString(getProfileKey("selected_comic_category_id"), null),
            selectedMovieCategoryId = legacyPrefs.getString(getProfileKey("selected_video_category_id"), null)
        )

        profileFileManager.saveProfileSettings(profile.name, settings)
    }

    /**
     * Migrate audio settings to JSON file
     */
    private suspend fun migrateAudioSettings(profile: UserProfile) {
        val sanitizedName = profile.name.replace(Regex("[^a-zA-Z0-9]"), "_")

        fun getProfileKey(baseKey: String): String = "${baseKey}_profile_${sanitizedName}"

        val settings = AudioSettings(
            version = 1,
            playbackSpeed = profile.playbackSpeed,
            skipForwardDuration = profile.skipForwardDuration,
            skipBackDuration = profile.skipBackDuration,
            sleepTimerMinutes = profile.sleepTimerMinutes,
            autoBookmark = legacyPrefs.getBoolean(getProfileKey("auto_bookmark"), true),
            keepScreenOn = legacyPrefs.getBoolean(getProfileKey("keep_screen_on"), false),
            autoPlayNext = legacyPrefs.getBoolean(getProfileKey("auto_play_next"), true),
            resumePlayback = legacyPrefs.getBoolean(getProfileKey("resume_playback"), true),
            rememberLastPosition = legacyPrefs.getBoolean(getProfileKey("remember_last_position"), true),
            autoRewindSeconds = legacyPrefs.getInt(getProfileKey("auto_rewind_seconds"), 0),
            volumeBoostEnabled = profile.volumeBoostEnabled,
            volumeBoostLevel = profile.volumeBoostLevel,
            normalizeAudio = profile.normalizeAudio,
            bassBoostLevel = profile.bassBoostLevel,
            equalizerPreset = profile.equalizerPreset,
            headsetControls = legacyPrefs.getBoolean(getProfileKey("headset_controls"), true),
            pauseOnDisconnect = legacyPrefs.getBoolean(getProfileKey("pause_on_disconnect"), true),
            showPlaybackNotification = legacyPrefs.getBoolean(getProfileKey("show_playback_notification"), true),
            lastMusicId = legacyPrefs.getString(getProfileKey("last_music_id"), null),
            lastMusicPosition = legacyPrefs.getLong(getProfileKey("last_music_position"), 0L),
            lastMusicPlaying = legacyPrefs.getBoolean(getProfileKey("last_music_playing"), false),
            lastAudiobookId = legacyPrefs.getString(getProfileKey("last_audiobook_id"), null),
            lastAudiobookPosition = legacyPrefs.getLong(getProfileKey("last_audiobook_position"), 0L),
            lastAudiobookPlaying = legacyPrefs.getBoolean(getProfileKey("last_audiobook_playing"), false)
        )

        profileFileManager.saveAudioSettings(profile.name, settings)
    }

    /**
     * Migrate reader settings to JSON file
     */
    private suspend fun migrateReaderSettings(profileName: String) {
        val sanitizedName = profileName.replace(Regex("[^a-zA-Z0-9]"), "_")

        fun getProfileKey(baseKey: String): String = "${baseKey}_profile_${sanitizedName}"

        val settings = ReaderSettings(
            version = 1,
            fontSize = legacyPrefs.getInt(getProfileKey("reader_font_size"), 18),
            lineSpacing = legacyPrefs.getFloat(getProfileKey("reader_line_spacing"), 1.4f),
            readerTheme = legacyPrefs.getString(getProfileKey("reader_theme"), "light") ?: "light",
            fontFamily = legacyPrefs.getString(getProfileKey("reader_font"), "serif") ?: "serif",
            textAlignment = legacyPrefs.getString(getProfileKey("reader_text_align"), "left") ?: "left",
            margins = legacyPrefs.getInt(getProfileKey("reader_margins"), 16),
            paragraphSpacing = legacyPrefs.getInt(getProfileKey("reader_paragraph_spacing"), 12),
            brightness = legacyPrefs.getFloat(getProfileKey("reader_brightness"), 1.0f),
            boldText = legacyPrefs.getBoolean(getProfileKey("reader_bold_text"), false),
            wordSpacing = legacyPrefs.getInt(getProfileKey("reader_word_spacing"), 0),
            pageFitMode = legacyPrefs.getString(getProfileKey("reader_page_fit_mode"), "fit") ?: "fit",
            pageGap = legacyPrefs.getInt(getProfileKey("reader_page_gap"), 4),
            forceTwoPage = legacyPrefs.getBoolean(getProfileKey("reader_force_two_page"), false),
            forceSinglePage = legacyPrefs.getBoolean(getProfileKey("reader_force_single_page"), false),
            keepScreenOn = legacyPrefs.getBoolean(getProfileKey("reader_keep_screen_on"), true)
        )

        profileFileManager.saveReaderSettings(profileName, settings)
    }

    /**
     * Migrate comic settings to JSON file
     */
    private suspend fun migrateComicSettings(profileName: String) {
        val sanitizedName = profileName.replace(Regex("[^a-zA-Z0-9]"), "_")

        fun getProfileKey(baseKey: String): String = "${baseKey}_profile_${sanitizedName}"

        val settings = ComicSettings(
            version = 1,
            forceTwoPage = legacyPrefs.getBoolean(getProfileKey("comic_force_two_page"), false),
            forceSinglePage = legacyPrefs.getBoolean(getProfileKey("comic_force_single_page"), false),
            readingDirection = legacyPrefs.getString(getProfileKey("comic_reading_direction"), "ltr") ?: "ltr",
            pageFitMode = legacyPrefs.getString(getProfileKey("comic_page_fit_mode"), "fit") ?: "fit",
            keepScreenOn = true
        )

        profileFileManager.saveComicSettings(profileName, settings)
    }

    /**
     * Migrate series to playlist folders
     */
    private suspend fun migrateSeriesAsPlaylistFolders(profileName: String) {
        val sanitizedName = profileName.replace(Regex("[^a-zA-Z0-9]"), "_")
        val seriesKey = "series_data_profile_${sanitizedName}"

        // Try to load series from library prefs
        val jsonString = libraryPrefs.getString(seriesKey, null) ?: return

        try {
            val jsonArray = JSONArray(jsonString)

            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val name = jsonObject.getString("name")
                val contentTypeStr = jsonObject.getString("contentType")
                val contentType = try {
                    ContentType.valueOf(contentTypeStr)
                } catch (e: Exception) {
                    ContentType.AUDIOBOOK
                }

                // Create folder for this series/playlist
                playlistFolderManager.createPlaylistFolder(profileName, contentType, name)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Mark migration as complete
     */
    private fun markMigrationComplete() {
        migrationPrefs.edit().putInt(KEY_MIGRATION_VERSION, CURRENT_MIGRATION_VERSION).apply()
    }

    /**
     * Reset migration status (for testing or forced re-migration)
     */
    fun resetMigration() {
        migrationPrefs.edit().putInt(KEY_MIGRATION_VERSION, 0).apply()
    }

    /**
     * Get current migration version
     */
    fun getMigrationVersion(): Int {
        return migrationPrefs.getInt(KEY_MIGRATION_VERSION, 0)
    }

    /**
     * Delete legacy global content folders (Librio/Audiobooks, Librio/Books, etc.)
     * These are no longer used since content is now per-profile
     */
    suspend fun deleteLegacyGlobalFolders(): Boolean = withContext(Dispatchers.IO) {
        val librioRoot = java.io.File(android.os.Environment.getExternalStorageDirectory(), "Librio")
        val legacyFolders = listOf("Audiobooks", "Books", "Music", "Comics", "Videos")
        var allDeleted = true

        legacyFolders.forEach { folderName ->
            val folder = java.io.File(librioRoot, folderName)
            if (folder.exists() && folder.isDirectory) {
                try {
                    // Only delete if empty or contains no files (safety check)
                    val hasFiles = folder.walkTopDown().any { it.isFile }
                    if (!hasFiles) {
                        folder.deleteRecursively()
                    } else {
                        // Folder has files - don't delete, but report
                        allDeleted = false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    allDeleted = false
                }
            }
        }
        allDeleted
    }
}

/**
 * Result of migration operation
 */
sealed class MigrationResult {
    data class Success(val profilesCount: Int) : MigrationResult()
    data class Error(val message: String) : MigrationResult()
}
