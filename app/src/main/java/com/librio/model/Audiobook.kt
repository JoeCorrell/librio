package com.librio.model

import android.graphics.Bitmap
import android.net.Uri

/**
 * Represents an audiobook with its metadata
 */
data class Audiobook(
    val uri: Uri,
    val title: String,
    val author: String = "Unknown Author",
    val narrator: String? = null,
    val coverArt: Bitmap? = null,
    val coverArtUri: Uri? = null,
    val duration: Long = 0L, // in milliseconds
    val chapters: List<Chapter> = emptyList(),
    val fileName: String = "",
    val fileSize: Long = 0L,
)

/**
 * Represents a chapter within an audiobook
 */
data class Chapter(
    val index: Int,
    val title: String,
    val startTime: Long, // in milliseconds
    val endTime: Long,   // in milliseconds
) {
    val duration: Long get() = endTime - startTime
}

/**
 * Playback state for the player
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val currentChapterIndex: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * Available playback speeds
 */
val PlaybackSpeeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
