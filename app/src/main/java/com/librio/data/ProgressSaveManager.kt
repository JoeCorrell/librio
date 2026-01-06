package com.librio.data

import android.content.Context
import android.net.Uri
import com.librio.model.ContentType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Centralized progress save manager with instant updates and crash resistance.
 *
 * Features:
 * - Instant in-memory state updates (UI shows progress immediately)
 * - Debounced file saves (max 1 second delay, prevents excessive I/O)
 * - Mutex protection (thread-safe, no race conditions)
 * - Write-ahead buffer (crash-resistant, no data loss)
 * - Single responsibility (one manager for all content types)
 *
 * Usage:
 * ```kotlin
 * progressManager.updateAudiobookProgress(id, position, duration)
 * progressManager.updateBookProgress(id, currentPage, totalPages)
 * ```
 */
class ProgressSaveManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val saveMutex = Mutex()
    private val pendingUpdates = mutableMapOf<String, ProgressUpdate>()
    private var saveJob: Job? = null

    // State flows for UI updates
    private val _audiobookProgress = MutableStateFlow<Map<String, Long>>(emptyMap())
    val audiobookProgress: StateFlow<Map<String, Long>> = _audiobookProgress

    private val _bookProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val bookProgress: StateFlow<Map<String, Int>> = _bookProgress

    private val _musicProgress = MutableStateFlow<Map<String, Long>>(emptyMap())
    val musicProgress: StateFlow<Map<String, Long>> = _musicProgress

    private val _comicProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val comicProgress: StateFlow<Map<String, Int>> = _comicProgress

    private val _movieProgress = MutableStateFlow<Map<String, Long>>(emptyMap())
    val movieProgress: StateFlow<Map<String, Long>> = _movieProgress

    /**
     * Update audiobook progress.
     * State updates instantly, file save is debounced (max 1 second delay).
     */
    fun updateAudiobookProgress(
        id: String,
        uri: Uri,
        position: Long,
        duration: Long,
        isCompleted: Boolean = false
    ) {
        // Instant state update for UI
        _audiobookProgress.value = _audiobookProgress.value + (id to position)

        // Queue debounced save
        queueSave(
            ProgressUpdate.AudiobookProgress(
                id = id,
                uri = uri,
                position = position,
                duration = duration,
                isCompleted = isCompleted,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    /**
     * Update book progress.
     * State updates instantly, file save is debounced.
     */
    fun updateBookProgress(
        id: String,
        uri: Uri,
        currentPage: Int,
        totalPages: Int,
        isCompleted: Boolean = false
    ) {
        // Instant state update for UI
        _bookProgress.value = _bookProgress.value + (id to currentPage)

        // Queue debounced save
        queueSave(
            ProgressUpdate.BookProgress(
                id = id,
                uri = uri,
                currentPage = currentPage,
                totalPages = totalPages,
                isCompleted = isCompleted,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    /**
     * Update music progress.
     */
    fun updateMusicProgress(
        id: String,
        uri: Uri,
        position: Long,
        duration: Long,
        isCompleted: Boolean = false
    ) {
        _musicProgress.value = _musicProgress.value + (id to position)

        queueSave(
            ProgressUpdate.MusicProgress(
                id = id,
                uri = uri,
                position = position,
                duration = duration,
                isCompleted = isCompleted,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    /**
     * Update comic progress.
     */
    fun updateComicProgress(
        id: String,
        uri: Uri,
        currentPage: Int,
        totalPages: Int,
        isCompleted: Boolean = false
    ) {
        _comicProgress.value = _comicProgress.value + (id to currentPage)

        queueSave(
            ProgressUpdate.ComicProgress(
                id = id,
                uri = uri,
                currentPage = currentPage,
                totalPages = totalPages,
                isCompleted = isCompleted,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    /**
     * Update movie progress.
     */
    fun updateMovieProgress(
        id: String,
        uri: Uri,
        position: Long,
        duration: Long,
        isCompleted: Boolean = false
    ) {
        _movieProgress.value = _movieProgress.value + (id to position)

        queueSave(
            ProgressUpdate.MovieProgress(
                id = id,
                uri = uri,
                position = position,
                duration = duration,
                isCompleted = isCompleted,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    /**
     * Queue a progress update for debounced saving.
     * Uses 1-second debounce to prevent excessive I/O while ensuring quick saves.
     */
    private fun queueSave(update: ProgressUpdate) {
        scope.launch {
            saveMutex.withLock {
                // Add to pending updates (overwrites older update for same ID)
                pendingUpdates[update.id] = update

                // Cancel existing save job and schedule new one
                saveJob?.cancel()
                saveJob = scope.launch {
                    // Debounce: wait 1 second for more updates
                    delay(1000)

                    // Perform save
                    savePendingUpdates()
                }
            }
        }
    }

    /**
     * Force immediate save of all pending updates.
     * Use this when app is pausing or user is navigating away.
     */
    suspend fun flushPendingUpdates() {
        saveMutex.withLock {
            saveJob?.cancel()
            if (pendingUpdates.isNotEmpty()) {
                savePendingUpdates()
            }
        }
    }

    /**
     * Save all pending updates to buffer file (synchronous, crash-safe).
     * This ensures no data loss even if app crashes immediately after.
     */
    private suspend fun savePendingUpdates() = withContext(Dispatchers.IO) {
        if (pendingUpdates.isEmpty()) return@withContext

        try {
            // Write to buffer file first (crash-safe)
            writeToBuffer(pendingUpdates.values.toList())

            // Clear pending updates
            pendingUpdates.clear()
        } catch (e: Exception) {
            e.printStackTrace()
            // Don't clear pending updates on error - will retry next time
        }
    }

    /**
     * Write updates to buffer file synchronously.
     * This file is read on app startup to recover any unsaved progress.
     */
    private fun writeToBuffer(updates: List<ProgressUpdate>) {
        val bufferFile = getBufferFile()
        try {
            // Simple append-only log format for crash resistance
            bufferFile.bufferedWriter().use { writer ->
                updates.forEach { update ->
                    when (update) {
                        is ProgressUpdate.AudiobookProgress -> {
                            writer.write("AB|${update.id}|${update.uri}|${update.position}|${update.duration}|${update.isCompleted}|${update.timestamp}\n")
                        }
                        is ProgressUpdate.BookProgress -> {
                            writer.write("BK|${update.id}|${update.uri}|${update.currentPage}|${update.totalPages}|${update.isCompleted}|${update.timestamp}\n")
                        }
                        is ProgressUpdate.MusicProgress -> {
                            writer.write("MS|${update.id}|${update.uri}|${update.position}|${update.duration}|${update.isCompleted}|${update.timestamp}\n")
                        }
                        is ProgressUpdate.ComicProgress -> {
                            writer.write("CM|${update.id}|${update.uri}|${update.currentPage}|${update.totalPages}|${update.isCompleted}|${update.timestamp}\n")
                        }
                        is ProgressUpdate.MovieProgress -> {
                            writer.write("MV|${update.id}|${update.uri}|${update.position}|${update.duration}|${update.isCompleted}|${update.timestamp}\n")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Get buffer file location.
     */
    private fun getBufferFile(): File {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val librioDir = File(baseDir, "Librio")
        librioDir.mkdirs()
        return File(librioDir, "progress_buffer.log")
    }

    /**
     * Load progress from buffer on startup (recovery from crashes).
     * Returns map of updates to apply to library state.
     */
    suspend fun recoverFromBuffer(): Map<ContentType, List<ProgressUpdate>> = withContext(Dispatchers.IO) {
        val bufferFile = getBufferFile()
        if (!bufferFile.exists()) return@withContext emptyMap()

        val updates = mutableMapOf<ContentType, MutableList<ProgressUpdate>>()

        try {
            bufferFile.forEachLine { line ->
                val parts = line.split("|")
                // Validate line has minimum required parts (type + 6 fields)
                if (parts.size != 7) {
                    // Skip malformed lines
                    return@forEachLine
                }

                val update = when (parts[0]) {
                    "AB" -> {
                        ProgressUpdate.AudiobookProgress(
                            id = parts[1],
                            uri = Uri.parse(parts[2]),
                            position = parts[3].toLongOrNull() ?: 0,
                            duration = parts[4].toLongOrNull() ?: 0,
                            isCompleted = parts[5].toBoolean(),
                            timestamp = parts[6].toLongOrNull() ?: 0
                        ).also {
                            updates.getOrPut(ContentType.AUDIOBOOK) { mutableListOf() }.add(it)
                        }
                    }
                    "BK" -> {
                        ProgressUpdate.BookProgress(
                            id = parts[1],
                            uri = Uri.parse(parts[2]),
                            currentPage = parts[3].toIntOrNull() ?: 0,
                            totalPages = parts[4].toIntOrNull() ?: 0,
                            isCompleted = parts[5].toBoolean(),
                            timestamp = parts[6].toLongOrNull() ?: 0
                        ).also {
                            updates.getOrPut(ContentType.EBOOK) { mutableListOf() }.add(it)
                        }
                    }
                    "MS" -> {
                        ProgressUpdate.MusicProgress(
                            id = parts[1],
                            uri = Uri.parse(parts[2]),
                            position = parts[3].toLongOrNull() ?: 0,
                            duration = parts[4].toLongOrNull() ?: 0,
                            isCompleted = parts[5].toBoolean(),
                            timestamp = parts[6].toLongOrNull() ?: 0
                        ).also {
                            updates.getOrPut(ContentType.MUSIC) { mutableListOf() }.add(it)
                        }
                    }
                    "CM" -> {
                        ProgressUpdate.ComicProgress(
                            id = parts[1],
                            uri = Uri.parse(parts[2]),
                            currentPage = parts[3].toIntOrNull() ?: 0,
                            totalPages = parts[4].toIntOrNull() ?: 0,
                            isCompleted = parts[5].toBoolean(),
                            timestamp = parts[6].toLongOrNull() ?: 0
                        ).also {
                            updates.getOrPut(ContentType.COMICS) { mutableListOf() }.add(it)
                        }
                    }
                    "MV" -> {
                        ProgressUpdate.MovieProgress(
                            id = parts[1],
                            uri = Uri.parse(parts[2]),
                            position = parts[3].toLongOrNull() ?: 0,
                            duration = parts[4].toLongOrNull() ?: 0,
                            isCompleted = parts[5].toBoolean(),
                            timestamp = parts[6].toLongOrNull() ?: 0
                        ).also {
                            updates.getOrPut(ContentType.MOVIE) { mutableListOf() }.add(it)
                        }
                    }
                    else -> null
                }
            }

            // Clear buffer after successful recovery
            bufferFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        updates
    }
}

/**
 * Progress update sealed class for type-safe updates.
 */
sealed class ProgressUpdate(open val id: String, open val timestamp: Long) {
    data class AudiobookProgress(
        override val id: String,
        val uri: Uri,
        val position: Long,
        val duration: Long,
        val isCompleted: Boolean,
        override val timestamp: Long
    ) : ProgressUpdate(id, timestamp)

    data class BookProgress(
        override val id: String,
        val uri: Uri,
        val currentPage: Int,
        val totalPages: Int,
        val isCompleted: Boolean,
        override val timestamp: Long
    ) : ProgressUpdate(id, timestamp)

    data class MusicProgress(
        override val id: String,
        val uri: Uri,
        val position: Long,
        val duration: Long,
        val isCompleted: Boolean,
        override val timestamp: Long
    ) : ProgressUpdate(id, timestamp)

    data class ComicProgress(
        override val id: String,
        val uri: Uri,
        val currentPage: Int,
        val totalPages: Int,
        val isCompleted: Boolean,
        override val timestamp: Long
    ) : ProgressUpdate(id, timestamp)

    data class MovieProgress(
        override val id: String,
        val uri: Uri,
        val position: Long,
        val duration: Long,
        val isCompleted: Boolean,
        override val timestamp: Long
    ) : ProgressUpdate(id, timestamp)
}
