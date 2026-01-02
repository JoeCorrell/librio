package com.librio.player

import android.animation.ValueAnimator
import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.DecelerateInterpolator
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.common.AudioAttributes
import com.librio.player.applyEqualizerPreset
import com.librio.player.normalizeEqPresetName

/**
 * Manages audio settings for ExoPlayer including:
 * - Trim silence (skip silent parts)
 * - Fade on pause/resume
 * - Mono audio mixing
 * - Channel balance (L/R)
 * - Gapless playback
 * - Equalizer presets
 * - Bass boost
 * - Volume boost (loudness enhancer)
 * - Audio normalization
 */
@OptIn(UnstableApi::class)
class AudioSettingsManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioSettingsManager"
        private const val MAX_EFFECT_RETRY_ATTEMPTS = 3
        private const val EFFECT_RETRY_DELAY_MS = 500L // Delay between retries
    }

    // Current settings state
    var trimSilence: Boolean = false
        private set
    var monoAudio: Boolean = false
        private set
    var channelBalance: Float = 0f  // -1 = left, 0 = center, 1 = right
        private set
    var fadeOnPauseResume: Boolean = false
        private set
    var fadeDurationMs: Long = 300L
        private set
    var gaplessPlayback: Boolean = true
        private set

    // Audio effects settings
    private var volumeBoostEnabled: Boolean = false
    private var volumeBoostLevel: Float = 1.0f
    private var normalizeAudio: Boolean = false
    private var bassBoostLevel: Float = 0f
    private var equalizerPreset: String = "DEFAULT"

    private var currentPlayer: ExoPlayer? = null
    private var fadeAnimator: ValueAnimator? = null
    private var playerListener: Player.Listener? = null

    // Audio processors
    private var silenceProcessor: SilenceSkippingAudioProcessor? = null
    private var channelProcessor: ChannelMixingAudioProcessor? = null

    // Audio effects (hardware)
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var bassBoost: BassBoost? = null
    private var equalizer: Equalizer? = null

    // Effect retry mechanism for newer Android versions
    private var effectRetryCount = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    /**
     * Create an ExoPlayer configured with current audio settings
     */
    fun createConfiguredPlayer(): ExoPlayer {
        // Create silence skipping processor
        val newSilenceProcessor = SilenceSkippingAudioProcessor(
            /* minimumSilenceDurationUs = */ 150_000L,  // 150ms minimum silence
            /* paddingSilenceUs = */ 20_000L,           // 20ms padding
            /* silenceThresholdLevel = */ 1024.toShort()  // Threshold level
        )
        silenceProcessor = newSilenceProcessor

        // Create channel mixing processor for mono/balance
        val newChannelProcessor = ChannelMixingAudioProcessor()
        channelProcessor = newChannelProcessor

        // Create custom audio sink with our processors
        val audioSink = DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf(newChannelProcessor, newSilenceProcessor))
            .build()

        // Create renderers factory with custom audio sink
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink {
                return audioSink
            }
        }

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setPauseAtEndOfMediaItems(!gaplessPlayback)
            .build()

        currentPlayer = player

        // Apply initial settings
        updateSilenceSkipping()
        updateChannelMixing()

        // Add listener to apply audio effects when player is ready
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val sessionId = player.audioSessionId
                    if (sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId != 0) {
                        // Only apply if effects aren't already initialized or session changed
                        if (equalizer == null || bassBoost == null || loudnessEnhancer == null) {
                            applyAudioEffects(sessionId)
                        }
                    }
                }
            }
        }
        player.addListener(listener)
        playerListener = listener

        return player
    }

    /**
     * Apply hardware audio effects (equalizer, bass boost, volume boost)
     * Includes retry mechanism for newer Android versions where effects may fail initially.
     */
    private fun applyAudioEffects(audioSessionId: Int, retryAttempt: Int = 0) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId == 0) {
            Log.w(TAG, "Invalid audio session ID: $audioSessionId")
            return
        }

        lastAudioSessionId = audioSessionId
        Log.d(TAG, "Applying audio effects with session ID: $audioSessionId (Android ${Build.VERSION.SDK_INT}, attempt ${retryAttempt + 1})")

        try {
            // Release old effects
            loudnessEnhancer?.release()
            bassBoost?.release()
            equalizer?.release()

            var successCount = 0
            var failureCount = 0

            // Create LoudnessEnhancer
            loudnessEnhancer = try {
                LoudnessEnhancer(audioSessionId).also {
                    Log.d(TAG, "✓ LoudnessEnhancer created successfully")
                    successCount++
                }
            } catch (e: Exception) {
                Log.e(TAG, "✗ Failed to create LoudnessEnhancer: ${e.message}")
                failureCount++
                null
            }

            // Create BassBoost
            val useHardwareBassBoost = bassBoostLevel > 0f && equalizerPreset != "BASS_INCREASED"
            bassBoost = try {
                BassBoost(0, audioSessionId).apply {
                    enabled = useHardwareBassBoost
                    Log.d(TAG, "✓ BassBoost created successfully (enabled: $useHardwareBassBoost)")
                    successCount++
                }
            } catch (e: Exception) {
                Log.e(TAG, "✗ Failed to create BassBoost: ${e.message}")
                failureCount++
                null
            }

            // Create Equalizer
            equalizer = try {
                Equalizer(0, audioSessionId).apply {
                    enabled = true
                    val bandCount = numberOfBands
                    val levelRange = bandLevelRange
                    Log.d(TAG, "✓ Equalizer created successfully (bands: $bandCount, range: ${levelRange[0]}..${levelRange[1]} mB)")
                    successCount++
                }
            } catch (e: Exception) {
                Log.e(TAG, "✗ Failed to create Equalizer: ${e.message}")
                failureCount++
                null
            }

            // Log summary
            Log.i(TAG, "Audio effects: $successCount created, $failureCount failed")

            // If all effects failed and we haven't exceeded retry limit, try again
            if (successCount == 0 && failureCount > 0 && retryAttempt < MAX_EFFECT_RETRY_ATTEMPTS) {
                Log.w(TAG, "All effects failed, retrying in ${EFFECT_RETRY_DELAY_MS}ms...")
                mainHandler.postDelayed({
                    applyAudioEffects(audioSessionId, retryAttempt + 1)
                }, EFFECT_RETRY_DELAY_MS)
                return
            }

            // Apply current settings to successfully created effects
            if (equalizer != null) {
                val normalizedPreset = normalizeEqPresetName(equalizerPreset)
                try {
                    applyEqualizerPreset(equalizer!!, normalizedPreset)
                    Log.d(TAG, "✓ Equalizer preset applied: $normalizedPreset")
                } catch (e: Exception) {
                    Log.e(TAG, "✗ Failed to apply equalizer preset: ${e.message}")
                }
            }

            applyLoudness()
            applyBassBoost()

            // Reset retry counter on success
            if (successCount > 0) {
                effectRetryCount = 0
            }

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error applying audio effects: ${e.message}", e)
            e.printStackTrace()
        }
    }

    private fun applyLoudness() {
        loudnessEnhancer?.let { enhancer ->
            runCatching {
                val baseGain = when {
                    volumeBoostEnabled -> ((volumeBoostLevel - 1f) * 1500).toInt().coerceAtLeast(0)
                    normalizeAudio -> 600  // Normalize audio with moderate gain boost
                    else -> 0
                }
                enhancer.setTargetGain(baseGain)
                enhancer.enabled = volumeBoostEnabled || normalizeAudio
            }
        }
    }

    private fun applyBassBoost() {
        bassBoost?.let { boost ->
            runCatching {
                // Only apply hardware bass boost if not using equalizer bass preset
                val useHardwareBassBoost = bassBoostLevel > 0f && equalizerPreset != "BASS_INCREASED"
                if (useHardwareBassBoost) {
                    val strength = (bassBoostLevel * 1000).toInt().coerceIn(0, 1000).toShort()
                    boost.setStrength(strength)
                    boost.enabled = true
                } else {
                    boost.enabled = false
                }
            }
        }
    }

    /**
     * Update trim silence setting
     */
    fun setTrimSilence(enabled: Boolean) {
        trimSilence = enabled
        updateSilenceSkipping()
    }

    private fun updateSilenceSkipping() {
        silenceProcessor?.setEnabled(trimSilence)
    }

    /**
     * Update mono audio setting
     */
    fun setMonoAudio(enabled: Boolean) {
        monoAudio = enabled
        updateChannelMixing()
    }

    /**
     * Update channel balance (-1 = left, 0 = center, 1 = right)
     */
    fun setChannelBalance(balance: Float) {
        channelBalance = balance.coerceIn(-1f, 1f)
        updateChannelMixing()
    }

    private fun updateChannelMixing() {
        channelProcessor?.setMono(monoAudio)
        channelProcessor?.setBalance(channelBalance)
    }

    /**
     * Update fade on pause/resume setting
     */
    fun setFadeOnPauseResume(enabled: Boolean, durationMs: Long = 300L) {
        fadeOnPauseResume = enabled
        fadeDurationMs = durationMs
    }

    /**
     * Play with optional fade in
     */
    fun playWithFade(player: ExoPlayer) {
        fadeAnimator?.cancel()

        if (fadeOnPauseResume) {
            player.volume = 0f
            player.play()

            fadeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = fadeDurationMs
                interpolator = DecelerateInterpolator()
                addUpdateListener { animator ->
                    val value = animator.animatedValue as Float
                    player.volume = value
                }
                start()
            }
        } else {
            player.volume = 1f
            player.play()
        }
    }

    /**
     * Pause with optional fade out
     */
    fun pauseWithFade(player: ExoPlayer, onComplete: (() -> Unit)? = null) {
        fadeAnimator?.cancel()

        if (fadeOnPauseResume) {
            val startVolume = player.volume

            fadeAnimator = ValueAnimator.ofFloat(startVolume, 0f).apply {
                duration = fadeDurationMs
                interpolator = DecelerateInterpolator()
                addUpdateListener { animator ->
                    val value = animator.animatedValue as Float
                    player.volume = value
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        player.pause()
                        player.volume = 1f  // Reset volume for next play
                        onComplete?.invoke()
                    }
                })
                start()
            }
        } else {
            player.pause()
            onComplete?.invoke()
        }
    }

    /**
     * Update gapless playback setting
     */
    fun setGaplessPlayback(enabled: Boolean) {
        gaplessPlayback = enabled
        currentPlayer?.pauseAtEndOfMediaItems = !enabled
    }

    /**
     * Update all settings at once
     */
    fun updateAllSettings(
        trimSilence: Boolean,
        monoAudio: Boolean,
        channelBalance: Float,
        fadeOnPauseResume: Boolean,
        gaplessPlayback: Boolean
    ) {
        this.trimSilence = trimSilence
        this.monoAudio = monoAudio
        this.channelBalance = channelBalance.coerceIn(-1f, 1f)
        this.fadeOnPauseResume = fadeOnPauseResume
        this.gaplessPlayback = gaplessPlayback

        updateSilenceSkipping()
        updateChannelMixing()
        currentPlayer?.pauseAtEndOfMediaItems = !gaplessPlayback
    }

    /**
     * Set equalizer preset
     */
    fun setEqualizerPreset(preset: String) {
        equalizerPreset = normalizeEqPresetName(preset)
        equalizer?.let { runCatching { applyEqualizerPreset(it, equalizerPreset) } }
        applyBassBoost()  // Update bass boost based on new preset
        applyLoudness()
    }

    /**
     * Set volume boost
     */
    fun setVolumeBoost(enabled: Boolean, level: Float) {
        volumeBoostEnabled = enabled
        volumeBoostLevel = level.coerceIn(1f, 3f)
        applyLoudness()
    }

    /**
     * Set audio normalization
     */
    fun setNormalizeAudio(enabled: Boolean) {
        normalizeAudio = enabled
        applyLoudness()
    }

    /**
     * Set bass boost level (0.0 to 1.0)
     */
    fun setBassBoostLevel(level: Float) {
        bassBoostLevel = level.coerceIn(0f, 1f)
        applyBassBoost()
    }

    /**
     * Refresh audio effects - call when audio session changes
     */
    fun refreshAudioEffects() {
        val player = currentPlayer ?: return
        val sessionId = player.audioSessionId
        if (sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId != 0) {
            applyAudioEffects(sessionId)
        }
    }

    /**
     * Manually retry audio effects initialization.
     * Useful if effects failed on initial creation.
     */
    fun retryAudioEffects() {
        val sessionId = lastAudioSessionId
        if (sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId != 0) {
            Log.i(TAG, "Manually retrying audio effects...")
            effectRetryCount = 0
            applyAudioEffects(sessionId, 0)
        } else {
            Log.w(TAG, "Cannot retry: no valid audio session ID available")
        }
    }

    /**
     * Release resources
     */
    fun release() {
        fadeAnimator?.cancel()
        fadeAnimator = null

        // Cancel any pending effect retry operations
        mainHandler.removeCallbacksAndMessages(null)

        // Remove player listener
        playerListener?.let { listener ->
            currentPlayer?.removeListener(listener)
        }
        playerListener = null
        currentPlayer = null
        silenceProcessor = null
        channelProcessor = null

        // Release audio effects
        try {
            loudnessEnhancer?.release()
            Log.d(TAG, "LoudnessEnhancer released")
        } catch (_: Exception) { }
        try {
            bassBoost?.release()
            Log.d(TAG, "BassBoost released")
        } catch (_: Exception) { }
        try {
            equalizer?.release()
            Log.d(TAG, "Equalizer released")
        } catch (_: Exception) { }

        loudnessEnhancer = null
        bassBoost = null
        equalizer = null

        Log.i(TAG, "AudioSettingsManager released")
    }
}
