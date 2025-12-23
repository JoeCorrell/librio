package com.librio.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.librio.data.MigrationManager
import com.librio.data.MigrationResult
import com.librio.data.PlaylistFolderManager
import com.librio.data.ProfileFileManager
import com.librio.data.repository.SettingsRepository
import com.librio.ui.screens.UserProfile
import com.librio.ui.theme.AppTheme
import com.librio.ui.theme.BackgroundTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel for app settings
 */
class SettingsViewModel : ViewModel() {

    private var repository: SettingsRepository? = null

    val appTheme: StateFlow<AppTheme>?
        get() = repository?.appTheme

    val accentTheme: StateFlow<AppTheme>?
        get() = repository?.accentTheme

    val skipForwardDuration: StateFlow<Int>?
        get() = repository?.skipForwardDuration

    val skipBackDuration: StateFlow<Int>?
        get() = repository?.skipBackDuration

    val autoBookmark: StateFlow<Boolean>?
        get() = repository?.autoBookmark

    val keepScreenOn: StateFlow<Boolean>?
        get() = repository?.keepScreenOn

    val volumeBoostEnabled: StateFlow<Boolean>?
        get() = repository?.volumeBoostEnabled

    val volumeBoostLevel: StateFlow<Float>?
        get() = repository?.volumeBoostLevel

    val libraryOwnerName: StateFlow<String>?
        get() = repository?.libraryOwnerName

    val profiles: StateFlow<List<UserProfile>>?
        get() = repository?.profiles

    val darkMode: StateFlow<Boolean>?
        get() = repository?.darkMode

    val backgroundTheme: StateFlow<BackgroundTheme>?
        get() = repository?.backgroundTheme

    // Additional global settings
    val playbackSpeed: StateFlow<Float>?
        get() = repository?.playbackSpeed

    val sleepTimerMinutes: StateFlow<Int>?
        get() = repository?.sleepTimerMinutes

    val autoPlayNext: StateFlow<Boolean>?
        get() = repository?.autoPlayNext

    val defaultLibraryView: StateFlow<String>?
        get() = repository?.defaultLibraryView

    val defaultSortOrder: StateFlow<String>?
        get() = repository?.defaultSortOrder

    val resumePlayback: StateFlow<Boolean>?
        get() = repository?.resumePlayback

    val showPlaybackNotification: StateFlow<Boolean>?
        get() = repository?.showPlaybackNotification

    // E-Reader settings
    val readerFontSize: StateFlow<Int>?
        get() = repository?.readerFontSize

    val readerLineSpacing: StateFlow<Float>?
        get() = repository?.readerLineSpacing

    val readerTheme: StateFlow<String>?
        get() = repository?.readerTheme

    val readerFont: StateFlow<String>?
        get() = repository?.readerFont

    val readerTextAlign: StateFlow<String>?
        get() = repository?.readerTextAlign

    val readerMargins: StateFlow<Int>?
        get() = repository?.readerMargins

    val readerKeepScreenOn: StateFlow<Boolean>?
        get() = repository?.readerKeepScreenOn

    val readerParagraphSpacing: StateFlow<Int>?
        get() = repository?.readerParagraphSpacing

    val readerBrightness: StateFlow<Float>?
        get() = repository?.readerBrightness

    val readerBoldText: StateFlow<Boolean>?
        get() = repository?.readerBoldText

    val readerWordSpacing: StateFlow<Int>?
        get() = repository?.readerWordSpacing

    val readerPageFitMode: StateFlow<String>?
        get() = repository?.readerPageFitMode

    val readerPageGap: StateFlow<Int>?
        get() = repository?.readerPageGap

    val readerForceTwoPage: StateFlow<Boolean>?
        get() = repository?.readerForceTwoPage

    val readerForceSinglePage: StateFlow<Boolean>?
        get() = repository?.readerForceSinglePage

    // Comic Reader settings
    val comicForceTwoPage: StateFlow<Boolean>?
        get() = repository?.comicForceTwoPage

    val comicForceSinglePage: StateFlow<Boolean>?
        get() = repository?.comicForceSinglePage

    val comicReadingDirection: StateFlow<String>?
        get() = repository?.comicReadingDirection

    val comicPageFitMode: StateFlow<String>?
        get() = repository?.comicPageFitMode

    val comicPageGap: StateFlow<Int>?
        get() = repository?.comicPageGap

    val comicBackgroundColor: StateFlow<String>?
        get() = repository?.comicBackgroundColor

    val comicShowPageIndicators: StateFlow<Boolean>?
        get() = repository?.comicShowPageIndicators

    val comicEnableDoubleTapZoom: StateFlow<Boolean>?
        get() = repository?.comicEnableDoubleTapZoom

    val comicShowControlsOnTap: StateFlow<Boolean>?
        get() = repository?.comicShowControlsOnTap

    // Audio Enhancement settings
    val normalizeAudio: StateFlow<Boolean>?
        get() = repository?.normalizeAudio

    val bassBoostLevel: StateFlow<Int>?
        get() = repository?.bassBoostLevel

    val equalizerPreset: StateFlow<String>?
        get() = repository?.equalizerPreset

    val crossfadeDuration: StateFlow<Int>?
        get() = repository?.crossfadeDuration

    // Playback Control settings
    val autoRewindSeconds: StateFlow<Int>?
        get() = repository?.autoRewindSeconds

    val headsetControls: StateFlow<Boolean>?
        get() = repository?.headsetControls

    val pauseOnDisconnect: StateFlow<Boolean>?
        get() = repository?.pauseOnDisconnect

    // Display settings
    val showPlaceholderIcons: StateFlow<Boolean>?
        get() = repository?.showPlaceholderIcons

    val showFileSize: StateFlow<Boolean>?
        get() = repository?.showFileSize

    val showDuration: StateFlow<Boolean>?
        get() = repository?.showDuration

    val animationSpeed: StateFlow<String>?
        get() = repository?.animationSpeed

    val hapticFeedback: StateFlow<Boolean>?
        get() = repository?.hapticFeedback

    val confirmBeforeDelete: StateFlow<Boolean>?
        get() = repository?.confirmBeforeDelete

    val useSquareCorners: StateFlow<Boolean>?
        get() = repository?.useSquareCorners

    val rememberLastPosition: StateFlow<Boolean>?
        get() = repository?.rememberLastPosition

    // Filter persistence
    val selectedContentType: StateFlow<String>?
        get() = repository?.selectedContentType

    val selectedCategoryId: StateFlow<String?>?
        get() = repository?.selectedCategoryId

    val collapsedSeries: StateFlow<Set<String>>?
        get() = repository?.collapsedSeries

    // UI Visibility settings
    val showBackButton: StateFlow<Boolean>?
        get() = repository?.showBackButton

    val showSearchBar: StateFlow<Boolean>?
        get() = repository?.showSearchBar

    // Custom theme colors
    val customPrimaryColor: StateFlow<Int>?
        get() = repository?.customPrimaryColor

    val customAccentColor: StateFlow<Int>?
        get() = repository?.customAccentColor

    val customBackgroundColor: StateFlow<Int>?
        get() = repository?.customBackgroundColor

    val appScale: StateFlow<Float>?
        get() = repository?.appScale

    val uiFontScale: StateFlow<Float>?
        get() = repository?.uiFontScale

    val uiFontFamily: StateFlow<String>?
        get() = repository?.uiFontFamily

    val lastMusicId: StateFlow<String?>?
        get() = repository?.lastMusicId

    val lastMusicPosition: StateFlow<Long>?
        get() = repository?.lastMusicPosition

    val lastMusicPlaying: StateFlow<Boolean>?
        get() = repository?.lastMusicPlaying

    val lastAudiobookId: StateFlow<String?>?
        get() = repository?.lastAudiobookId

    val lastAudiobookPosition: StateFlow<Long>?
        get() = repository?.lastAudiobookPosition

    val lastAudiobookPlaying: StateFlow<Boolean>?
        get() = repository?.lastAudiobookPlaying

    val lastActiveType: StateFlow<String?>?
        get() = repository?.lastActiveType

    // Music playback mode settings
    val musicShuffleEnabled: StateFlow<Boolean>?
        get() = repository?.musicShuffleEnabled

    val musicRepeatMode: StateFlow<Int>?
        get() = repository?.musicRepeatMode

    // Migration state
    private val _migrationComplete = MutableStateFlow(false)
    val migrationComplete: StateFlow<Boolean> = _migrationComplete.asStateFlow()

    private var migrationManager: MigrationManager? = null

    fun initialize(context: Context) {
        if (repository == null) {
            repository = SettingsRepository(context)

            // Initialize migration manager
            val profileFileManager = ProfileFileManager()
            val playlistFolderManager = PlaylistFolderManager()
            migrationManager = MigrationManager(context, profileFileManager, playlistFolderManager)

            // Check and perform migration if needed
            performMigrationIfNeeded()
        }
    }

    /**
     * Check if migration is needed and perform it
     */
    private fun performMigrationIfNeeded() {
        val manager = migrationManager ?: return
        val repo = repository

        // Check if settings files exist - if not, reset migration so it runs again
        if (repo != null) {
            val profileFileManager = repo.getProfileFileManager()
            val profileName = repo.getActiveProfileName()
            if (!profileFileManager.profileSettingsExist(profileName)) {
                // Settings files don't exist, reset migration to force re-run
                manager.resetMigration()
            }
        }

        if (manager.isMigrationNeeded()) {
            viewModelScope.launch {
                // Delete legacy global folders first
                manager.deleteLegacyGlobalFolders()

                when (manager.performMigration()) {
                    is MigrationResult.Success -> {
                        // After migration, save current settings to files
                        repository?.saveAllSettingsToFiles()
                        _migrationComplete.value = true
                    }
                    is MigrationResult.Error -> {
                        // Still try to save settings even on error
                        repository?.saveAllSettingsToFiles()
                        _migrationComplete.value = true
                    }
                }
            }
        } else {
            // Even if migration is complete, ensure settings files exist and clean up legacy folders
            viewModelScope.launch {
                manager.deleteLegacyGlobalFolders()
                ensureSettingsFilesExist()
            }
            _migrationComplete.value = true
        }
    }

    /**
     * Ensure settings files exist for the current profile
     */
    private fun ensureSettingsFilesExist() {
        val repo = repository ?: return
        val profileFileManager = repo.getProfileFileManager()
        val profileName = repo.getActiveProfileName()

        // If profile settings file doesn't exist, create it
        if (!profileFileManager.profileSettingsExist(profileName)) {
            repo.saveAllSettingsToFiles()
        }
    }

    /**
     * Save all current settings to files
     */
    fun saveAllSettingsToFiles() {
        repository?.saveAllSettingsToFiles()
    }

    /**
     * Reload settings from JSON files
     * Call this when resuming the app to pick up external changes to JSON files
     */
    fun reloadSettingsFromFiles() {
        repository?.reloadSettingsFromFiles()
    }

    fun setTheme(theme: AppTheme) {
        repository?.setThemeForProfile(theme)
    }

    fun setAccentTheme(theme: AppTheme) {
        repository?.setAccentThemeForProfile(theme)
    }

    fun setSkipForwardDuration(seconds: Int) {
        repository?.setSkipForwardDuration(seconds)
    }

    fun setSkipBackDuration(seconds: Int) {
        repository?.setSkipBackDuration(seconds)
    }

    fun setAutoBookmark(enabled: Boolean) {
        repository?.setAutoBookmark(enabled)
    }

    fun setKeepScreenOn(enabled: Boolean) {
        repository?.setKeepScreenOn(enabled)
    }

    fun setVolumeBoostEnabled(enabled: Boolean) {
        repository?.setVolumeBoostEnabled(enabled)
    }

    fun setVolumeBoostLevel(level: Float) {
        repository?.setVolumeBoostLevel(level)
    }

    fun setLibraryOwnerName(name: String) {
        repository?.setLibraryOwnerName(name)
    }

    fun setDarkMode(enabled: Boolean) {
        repository?.setDarkModeForProfile(enabled)
    }

    fun setBackgroundTheme(theme: BackgroundTheme) {
        repository?.setBackgroundTheme(theme)
    }

    fun addProfile(name: String) {
        repository?.addProfile(name)
    }

    fun deleteProfile(profile: UserProfile) {
        repository?.deleteProfile(profile.id)
    }

    fun renameProfile(profile: UserProfile, newName: String) {
        repository?.renameProfile(profile.id, newName)
    }

    fun setProfilePicture(profile: UserProfile, pictureUri: String?) {
        repository?.setProfilePicture(profile.id, pictureUri)
    }

    fun selectProfile(profile: UserProfile) {
        repository?.setActiveProfile(profile.id)
    }

    fun exportProfileBackup(profile: UserProfile, onComplete: (File?) -> Unit = {}) {
        viewModelScope.launch {
            val backupFile = repository?.exportProfileBackup(profile.id)
            onComplete(backupFile)
        }
    }

    fun getThemeSync(): AppTheme {
        return repository?.appTheme?.value ?: AppTheme.TEAL
    }

    // E-Reader settings setters
    fun setReaderFontSize(size: Int) {
        repository?.setReaderFontSize(size)
    }

    fun setReaderLineSpacing(spacing: Float) {
        repository?.setReaderLineSpacing(spacing)
    }

    fun setReaderTheme(theme: String) {
        repository?.setReaderTheme(theme)
    }

    fun setReaderFont(font: String) {
        repository?.setReaderFont(font)
    }

    fun setReaderTextAlign(align: String) {
        repository?.setReaderTextAlign(align)
    }

    fun setReaderMargins(margins: Int) {
        repository?.setReaderMargins(margins)
    }

    fun setReaderKeepScreenOn(enabled: Boolean) {
        repository?.setReaderKeepScreenOn(enabled)
    }

    fun setReaderParagraphSpacing(spacing: Int) {
        repository?.setReaderParagraphSpacing(spacing)
    }

    fun setReaderBrightness(brightness: Float) {
        repository?.setReaderBrightness(brightness)
    }

    fun setReaderBoldText(enabled: Boolean) {
        repository?.setReaderBoldText(enabled)
    }

    fun setReaderWordSpacing(spacing: Int) {
        repository?.setReaderWordSpacing(spacing)
    }

    fun setReaderPageFitMode(mode: String) {
        repository?.setReaderPageFitMode(mode)
    }

    fun setReaderPageGap(gap: Int) {
        repository?.setReaderPageGap(gap)
    }

    fun setReaderForceTwoPage(enabled: Boolean) {
        repository?.setReaderForceTwoPage(enabled)
    }

    fun setReaderForceSinglePage(enabled: Boolean) {
        repository?.setReaderForceSinglePage(enabled)
    }

    // Comic Reader setters
    fun setComicForceTwoPage(enabled: Boolean) {
        repository?.setComicForceTwoPage(enabled)
    }

    fun setComicForceSinglePage(enabled: Boolean) {
        repository?.setComicForceSinglePage(enabled)
    }

    fun setComicReadingDirection(direction: String) {
        repository?.setComicReadingDirection(direction)
    }

    fun setComicPageFitMode(mode: String) {
        repository?.setComicPageFitMode(mode)
    }

    fun setComicPageGap(gap: Int) {
        repository?.setComicPageGap(gap)
    }

    fun setComicBackgroundColor(color: String) {
        repository?.setComicBackgroundColor(color)
    }

    fun setComicShowPageIndicators(enabled: Boolean) {
        repository?.setComicShowPageIndicators(enabled)
    }

    fun setComicEnableDoubleTapZoom(enabled: Boolean) {
        repository?.setComicEnableDoubleTapZoom(enabled)
    }

    fun setComicShowControlsOnTap(enabled: Boolean) {
        repository?.setComicShowControlsOnTap(enabled)
    }

    // Additional global settings setters
    fun setPlaybackSpeed(speed: Float) {
        repository?.setPlaybackSpeed(speed)
    }

    fun setSleepTimerMinutes(minutes: Int) {
        repository?.setSleepTimerMinutes(minutes)
    }

    fun setAutoPlayNext(enabled: Boolean) {
        repository?.setAutoPlayNext(enabled)
    }

    fun setDefaultLibraryView(view: String) {
        repository?.setDefaultLibraryView(view)
    }

    fun setDefaultSortOrder(order: String) {
        repository?.setDefaultSortOrder(order)
    }

    fun setResumePlayback(enabled: Boolean) {
        repository?.setResumePlayback(enabled)
    }

    fun setShowPlaybackNotification(enabled: Boolean) {
        repository?.setShowPlaybackNotification(enabled)
    }

    // Audio Enhancement setters
    fun setNormalizeAudio(enabled: Boolean) {
        repository?.setNormalizeAudio(enabled)
    }

    fun setBassBoostLevel(level: Int) {
        repository?.setBassBoostLevel(level)
    }

    fun setEqualizerPreset(preset: String) {
        repository?.setEqualizerPreset(preset)
    }

    fun setCrossfadeDuration(seconds: Int) {
        repository?.setCrossfadeDuration(seconds)
    }

    // Playback Control setters
    fun setAutoRewindSeconds(seconds: Int) {
        repository?.setAutoRewindSeconds(seconds)
    }

    fun setHeadsetControls(enabled: Boolean) {
        repository?.setHeadsetControls(enabled)
    }

    fun setPauseOnDisconnect(enabled: Boolean) {
        repository?.setPauseOnDisconnect(enabled)
    }

    // Display settings setters
    fun setShowPlaceholderIcons(enabled: Boolean) {
        repository?.setShowPlaceholderIcons(enabled)
    }

    fun setShowFileSize(enabled: Boolean) {
        repository?.setShowFileSize(enabled)
    }

    fun setShowDuration(enabled: Boolean) {
        repository?.setShowDuration(enabled)
    }

    fun setAnimationSpeed(speed: String) {
        repository?.setAnimationSpeed(speed)
    }

    fun setHapticFeedback(enabled: Boolean) {
        repository?.setHapticFeedback(enabled)
    }

    fun setConfirmBeforeDelete(enabled: Boolean) {
        repository?.setConfirmBeforeDelete(enabled)
    }

    fun setUseSquareCorners(enabled: Boolean) {
        repository?.setUseSquareCorners(enabled)
    }

    fun setRememberLastPosition(enabled: Boolean) {
        repository?.setRememberLastPosition(enabled)
    }

    // Filter persistence setters
    fun setSelectedContentType(contentType: String) {
        repository?.setSelectedContentType(contentType)
    }

    fun setSelectedCategoryId(categoryId: String?) {
        repository?.setSelectedCategoryId(categoryId)
    }

    fun setCollapsedSeries(seriesIds: Set<String>) {
        repository?.setCollapsedSeries(seriesIds)
    }

    // UI Visibility setters
    fun setShowBackButton(enabled: Boolean) {
        repository?.setShowBackButton(enabled)
    }

    fun setShowSearchBar(enabled: Boolean) {
        repository?.setShowSearchBar(enabled)
    }

    // Custom theme color setters
    fun setCustomPrimaryColor(color: Int) {
        repository?.setCustomPrimaryColor(color)
    }

    fun setCustomAccentColor(color: Int) {
        repository?.setCustomAccentColor(color)
    }

    fun setCustomBackgroundColor(color: Int) {
        repository?.setCustomBackgroundColor(color)
    }

    fun setUiFontScale(scale: Float) {
        repository?.setUiFontScale(scale)
    }

    fun setUiFontFamily(family: String) {
        repository?.setUiFontFamily(family)
    }

    fun setAppScale(scale: Float) {
        repository?.setAppScale(scale)
    }

    fun setLastMusicState(id: String?, position: Long, isPlaying: Boolean) {
        repository?.setLastMusicState(id, position, isPlaying)
    }

    fun setLastAudiobookState(id: String?, position: Long, isPlaying: Boolean) {
        repository?.setLastAudiobookState(id, position, isPlaying)
    }

    fun setLastActiveType(type: String?) {
        repository?.setLastActiveType(type)
    }

    fun setMusicShuffleEnabled(enabled: Boolean) {
        repository?.setMusicShuffleEnabled(enabled)
    }

    fun setMusicRepeatMode(mode: Int) {
        repository?.setMusicRepeatMode(mode)
    }

    // Profile-specific audio settings setters
    fun setProfilePlaybackSpeed(speed: Float) {
        repository?.setProfilePlaybackSpeed(speed)
    }

    fun setProfileSkipForward(seconds: Int) {
        repository?.setProfileSkipForward(seconds)
    }

    fun setProfileSkipBack(seconds: Int) {
        repository?.setProfileSkipBack(seconds)
    }

    fun setProfileVolumeBoostEnabled(enabled: Boolean) {
        repository?.setProfileVolumeBoostEnabled(enabled)
    }

    fun setProfileVolumeBoostLevel(level: Float) {
        repository?.setProfileVolumeBoostLevel(level)
    }

    fun setProfileNormalizeAudio(enabled: Boolean) {
        repository?.setProfileNormalizeAudio(enabled)
    }

    fun setProfileBassBoostLevel(level: Float) {
        repository?.setProfileBassBoostLevel(level)
    }

    fun setProfileEqualizerPreset(preset: String) {
        repository?.setProfileEqualizerPreset(preset)
    }

    fun setProfileSleepTimer(minutes: Int) {
        repository?.setProfileSleepTimer(minutes)
    }
}
