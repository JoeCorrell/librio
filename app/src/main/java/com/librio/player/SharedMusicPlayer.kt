package com.librio.player

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer

/**
 * Shared ExoPlayer instance for music playback.
 * Keeps a single player alive across screens and the playback service,
 * with simple reference counting to avoid premature release.
 *
 * Uses AudioSettingsManager for advanced audio processing:
 * - Trim silence (skip silent parts)
 * - Fade on pause/resume
 * - Mono audio mixing
 * - Channel balance (L/R)
 * - Gapless playback
 * - Equalizer presets
 * - Bass boost
 * - Volume boost
 * - Audio normalization
 */
object SharedMusicPlayer {
    private var player: ExoPlayer? = null
    private var refCount = 0
    private var audioSettingsManager: AudioSettingsManager? = null

    /**
     * Get the audio settings manager (creates one if needed)
     */
    @Synchronized
    fun getAudioSettingsManager(context: Context): AudioSettingsManager {
        return audioSettingsManager ?: AudioSettingsManager(context.applicationContext).also {
            audioSettingsManager = it
        }
    }

    @Synchronized
    fun acquire(context: Context): ExoPlayer {
        val currentPlayer = player
        if (currentPlayer != null) {
            refCount++
            return currentPlayer
        }
        // Create new player
        val manager = getAudioSettingsManager(context)
        val newPlayer = manager.createConfiguredPlayer()
        player = newPlayer
        refCount = 1
        return newPlayer
    }

    /**
     * Update audio settings on the fly
     */
    @Synchronized
    fun updateAudioSettings(
        context: Context,
        trimSilence: Boolean? = null,
        fadeOnPauseResume: Boolean? = null,
        gaplessPlayback: Boolean? = null,
        equalizerPreset: String? = null,
        volumeBoostEnabled: Boolean? = null,
        volumeBoostLevel: Float? = null,
        normalizeAudio: Boolean? = null,
        bassBoostLevel: Float? = null
    ) {
        val manager = getAudioSettingsManager(context)
        trimSilence?.let { manager.setTrimSilence(it) }
        fadeOnPauseResume?.let { manager.setFadeOnPauseResume(it) }
        gaplessPlayback?.let { manager.setGaplessPlayback(it) }
        equalizerPreset?.let { manager.setEqualizerPreset(it) }
        if (volumeBoostEnabled != null && volumeBoostLevel != null) {
            manager.setVolumeBoost(volumeBoostEnabled, volumeBoostLevel)
        }
        normalizeAudio?.let { manager.setNormalizeAudio(it) }
        bassBoostLevel?.let { manager.setBassBoostLevel(it) }
    }

    /**
     * Set equalizer preset
     */
    @Synchronized
    fun setEqualizerPreset(context: Context, preset: String) {
        getAudioSettingsManager(context).setEqualizerPreset(preset)
    }

    /**
     * Set volume boost
     */
    @Synchronized
    fun setVolumeBoost(context: Context, enabled: Boolean, level: Float) {
        getAudioSettingsManager(context).setVolumeBoost(enabled, level)
    }

    /**
     * Set audio normalization
     */
    @Synchronized
    fun setNormalizeAudio(context: Context, enabled: Boolean) {
        getAudioSettingsManager(context).setNormalizeAudio(enabled)
    }

    /**
     * Set bass boost level
     */
    @Synchronized
    fun setBassBoostLevel(context: Context, level: Float) {
        getAudioSettingsManager(context).setBassBoostLevel(level)
    }

    /**
     * Refresh audio effects (call when resuming playback)
     */
    @Synchronized
    fun refreshAudioEffects(context: Context) {
        getAudioSettingsManager(context).refreshAudioEffects()
    }

    /**
     * Play with fade effect if enabled
     */
    @Synchronized
    fun playWithFade(context: Context) {
        val p = player ?: return
        val manager = getAudioSettingsManager(context)
        manager.playWithFade(p)
    }

    /**
     * Pause with fade effect if enabled
     */
    @Synchronized
    fun pauseWithFade(context: Context, onComplete: (() -> Unit)? = null) {
        val p = player ?: return
        val manager = getAudioSettingsManager(context)
        manager.pauseWithFade(p, onComplete)
    }

    /**
     * Check if fade on pause/resume is enabled
     */
    @Synchronized
    fun isFadeEnabled(context: Context): Boolean {
        return getAudioSettingsManager(context).fadeOnPauseResume
    }

    @Synchronized
    fun release() {
        if (refCount <= 0) {
            // Already released or never acquired - prevent double-release
            return
        }
        refCount--
        if (refCount == 0) {
            player?.release()
            player = null
            audioSettingsManager?.release()
            audioSettingsManager = null
        }
    }
}
