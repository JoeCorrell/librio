package com.librio.data.repository

import android.content.Context
import android.net.Uri
import com.librio.data.PlaylistFolderManager
import com.librio.data.ProgressFileManager
import com.librio.model.Category
import com.librio.model.ContentType
import com.librio.model.DiscoveredPlaylist
import com.librio.model.LibraryAudiobook
import com.librio.model.LibraryBook
import com.librio.model.LibraryComic
import com.librio.model.LibraryMusic
import com.librio.model.LibrarySeries
import com.librio.model.LibraryMovie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Repository for persisting library data using SharedPreferences
 * Supports per-profile library storage for content isolation
 */
class LibraryRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Playlist folder manager for folder-based playlists
    private val playlistFolderManager = PlaylistFolderManager()

    // Progress file manager for progress.json
    private val progressFileManager = ProgressFileManager()

    // Current profile for per-profile library storage
    private var currentProfileName: String = "Default"

    /**
     * Set the current profile for library operations
     * This allows each profile to have its own library state with progress
     */
    fun setCurrentProfile(profileName: String) {
        currentProfileName = profileName
    }

    /**
     * Get the playlist folder manager for external access
     */
    fun getPlaylistFolderManager(): PlaylistFolderManager = playlistFolderManager

    /**
     * Get profile-specific key for storage
     */
    private fun getProfileKey(baseKey: String): String {
        return "${baseKey}_profile_${currentProfileName.replace(Regex("[^a-zA-Z0-9]"), "_")}"
    }

    /**
     * Save the library to persistent storage (per-profile)
     */
    suspend fun saveLibrary(audiobooks: List<LibraryAudiobook>) = withContext(Dispatchers.IO) {
        val jsonArray = JSONArray()
        audiobooks.forEach { audiobook ->
            val jsonObject = JSONObject().apply {
                put("id", audiobook.id)
                put("uri", audiobook.uri.toString())
                put("title", audiobook.title)
                put("author", audiobook.author)
                put("narrator", audiobook.narrator ?: "")
                put("coverArtUri", audiobook.coverArtUri?.toString() ?: "")
                put("duration", audiobook.duration)
                put("lastPosition", audiobook.lastPosition)
                put("lastPlayed", audiobook.lastPlayed)
                put("dateAdded", audiobook.dateAdded)
                put("isCompleted", audiobook.isCompleted)
                put("categoryId", audiobook.categoryId ?: "")
                put("seriesId", audiobook.seriesId ?: "")
                put("seriesOrder", audiobook.seriesOrder)
                put("fileType", audiobook.fileType)
            }
            jsonArray.put(jsonObject)
        }
        prefs.edit().putString(getProfileKey(KEY_LIBRARY), jsonArray.toString()).commit()
    }

    /**
     * Load the library from persistent storage (per-profile)
     */
    suspend fun loadLibrary(): List<LibraryAudiobook> = withContext(Dispatchers.IO) {
        val jsonString = prefs.getString(getProfileKey(KEY_LIBRARY), null) ?: return@withContext emptyList()

        try {
            val jsonArray = JSONArray(jsonString)
            val audiobooks = mutableListOf<LibraryAudiobook>()

            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val audiobook = LibraryAudiobook(
                    id = jsonObject.getString("id"),
                    uri = Uri.parse(jsonObject.getString("uri")),
                    title = jsonObject.getString("title"),
                    author = jsonObject.getString("author"),
                    narrator = jsonObject.getString("narrator").takeIf { it.isNotEmpty() },
                    coverArtUri = jsonObject.optString("coverArtUri").takeIf { it.isNotEmpty() }?.let { Uri.parse(it) },
                    duration = jsonObject.getLong("duration"),
                    lastPosition = jsonObject.getLong("lastPosition"),
                    lastPlayed = jsonObject.getLong("lastPlayed"),
                    dateAdded = jsonObject.getLong("dateAdded"),
                    isCompleted = jsonObject.getBoolean("isCompleted"),
                    categoryId = jsonObject.optString("categoryId").takeIf { it.isNotEmpty() },
                    seriesId = jsonObject.optString("seriesId").takeIf { it.isNotEmpty() },
                    seriesOrder = jsonObject.optInt("seriesOrder", 0),
                    fileType = jsonObject.optString("fileType", "mp3"),
                    coverArt = null // Cover art will be reloaded
                )
                audiobooks.add(audiobook)
            }
            audiobooks
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Save the last played audiobook ID (per-profile)
     */
    fun saveLastPlayedId(audiobookId: String?) {
        prefs.edit().putString(getProfileKey(KEY_LAST_PLAYED), audiobookId).apply()
    }

    /**
     * Get the last played audiobook ID (per-profile)
     */
    fun getLastPlayedId(): String? {
        return prefs.getString(getProfileKey(KEY_LAST_PLAYED), null)
    }

    /**
     * Save playback settings (per-profile)
     */
    fun savePlaybackSpeed(speed: Float) {
        prefs.edit().putFloat(getProfileKey(KEY_PLAYBACK_SPEED), speed).apply()
    }

    fun getPlaybackSpeed(): Float {
        return prefs.getFloat(getProfileKey(KEY_PLAYBACK_SPEED), 1.0f)
    }

    /**
     * Save categories to persistent storage (per-profile)
     */
    suspend fun saveCategories(categories: List<Category>) = withContext(Dispatchers.IO) {
        val jsonArray = JSONArray()
        categories.forEach { category ->
            val jsonObject = JSONObject().apply {
                put("id", category.id)
                put("name", category.name)
                put("dateCreated", category.dateCreated)
            }
            jsonArray.put(jsonObject)
        }
        prefs.edit().putString(getProfileKey(KEY_CATEGORIES), jsonArray.toString()).commit()
    }

    /**
     * Load categories from persistent storage (per-profile)
     */
    suspend fun loadCategories(): List<Category> = withContext(Dispatchers.IO) {
        val jsonString = prefs.getString(getProfileKey(KEY_CATEGORIES), null) ?: return@withContext emptyList()

        try {
            val jsonArray = JSONArray(jsonString)
            val categories = mutableListOf<Category>()

            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val category = Category(
                    id = jsonObject.getString("id"),
                    name = jsonObject.getString("name"),
                    dateCreated = jsonObject.optLong("dateCreated", System.currentTimeMillis())
                )
                categories.add(category)
            }
            categories
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Save books to persistent storage
     */
    suspend fun saveBooks(books: List<LibraryBook>) = withContext(Dispatchers.IO) {
        val jsonArray = JSONArray()
        books.forEach { book ->
            val jsonObject = JSONObject().apply {
                put("id", book.id)
                put("uri", book.uri.toString())
                put("title", book.title)
                put("author", book.author)
                put("coverArtUri", book.coverArtUri ?: "")
                put("totalPages", book.totalPages)
                put("currentPage", book.currentPage)
                put("lastRead", book.lastRead)
                put("dateAdded", book.dateAdded)
                put("isCompleted", book.isCompleted)
                put("categoryId", book.categoryId ?: "")
                put("seriesId", book.seriesId ?: "")
                put("seriesOrder", book.seriesOrder)
                put("fileType", book.fileType)
            }
            jsonArray.put(jsonObject)
        }
        prefs.edit().putString(getProfileKey(KEY_BOOKS), jsonArray.toString()).commit()
    }

    /**
     * Load books from persistent storage (per-profile)
     */
    suspend fun loadBooks(): List<LibraryBook> = withContext(Dispatchers.IO) {
        val jsonString = prefs.getString(getProfileKey(KEY_BOOKS), null) ?: return@withContext emptyList()

        try {
            val jsonArray = JSONArray(jsonString)
            val books = mutableListOf<LibraryBook>()

            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val book = LibraryBook(
                    id = jsonObject.getString("id"),
                    uri = Uri.parse(jsonObject.getString("uri")),
                    title = jsonObject.getString("title"),
                    author = jsonObject.optString("author", "Unknown Author"),
                    coverArtUri = jsonObject.optString("coverArtUri").takeIf { it.isNotEmpty() },
                    totalPages = jsonObject.optInt("totalPages", 0),
                    currentPage = jsonObject.optInt("currentPage", 0),
                    lastRead = jsonObject.optLong("lastRead", 0L),
                    dateAdded = jsonObject.optLong("dateAdded", System.currentTimeMillis()),
                    isCompleted = jsonObject.optBoolean("isCompleted", false),
                    categoryId = jsonObject.optString("categoryId").takeIf { it.isNotEmpty() },
                    seriesId = jsonObject.optString("seriesId").takeIf { it.isNotEmpty() },
                    seriesOrder = jsonObject.optInt("seriesOrder", 0),
                    fileType = jsonObject.optString("fileType", "txt")
                )
                books.add(book)
            }
            books
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Save music to persistent storage
     * Deduplicates by URI before saving to prevent duplicate entries
     */
    suspend fun saveMusic(music: List<LibraryMusic>) = withContext(Dispatchers.IO) {
        val jsonArray = JSONArray()
        val seenUris = mutableSetOf<String>()

        // Deduplicate by URI before saving
        music.forEach { track ->
            val uriString = track.uri.toString()
            if (uriString in seenUris) return@forEach
            seenUris.add(uriString)

            val jsonObject = JSONObject().apply {
                put("id", track.id)
                put("uri", uriString)
                put("title", track.title)
                put("artist", track.artist)
                put("album", track.album ?: "")
                put("coverArtUri", track.coverArtUri ?: "")
                put("duration", track.duration)
                put("lastPosition", track.lastPosition)
                put("lastPlayed", track.lastPlayed)
                put("dateAdded", track.dateAdded)
                put("isCompleted", track.isCompleted)
                put("categoryId", track.categoryId ?: "")
                put("seriesId", track.seriesId ?: "")
                put("seriesOrder", track.seriesOrder)
                put("fileType", track.fileType)
                put("timesListened", track.timesListened)
                put("contentType", track.contentType.name)
            }
            jsonArray.put(jsonObject)
        }
        prefs.edit().putString(getProfileKey(KEY_MUSIC), jsonArray.toString()).commit()
    }

    /**
     * Load music from persistent storage (per-profile)
     * Deduplicates by URI to prevent duplicate entries
     */
    suspend fun loadMusic(): List<LibraryMusic> = withContext(Dispatchers.IO) {
        val jsonString = prefs.getString(getProfileKey(KEY_MUSIC), null) ?: return@withContext emptyList()

        try {
            val jsonArray = JSONArray(jsonString)
            val music = mutableListOf<LibraryMusic>()
            val seenUris = mutableSetOf<String>()

            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val uriString = jsonObject.getString("uri")

                // Skip duplicates by URI
                if (uriString in seenUris) continue
                seenUris.add(uriString)

                val track = LibraryMusic(
                    id = jsonObject.getString("id"),
                    uri = Uri.parse(uriString),
                    title = jsonObject.getString("title"),
                    artist = jsonObject.optString("artist", "Unknown Artist"),
                    album = jsonObject.optString("album").takeIf { it.isNotEmpty() },
                    coverArtUri = jsonObject.optString("coverArtUri").takeIf { it.isNotEmpty() },
                    duration = jsonObject.optLong("duration", 0L),
                    lastPosition = jsonObject.optLong("lastPosition", 0L),
                    lastPlayed = jsonObject.optLong("lastPlayed", 0L),
                    dateAdded = jsonObject.optLong("dateAdded", System.currentTimeMillis()),
                    isCompleted = jsonObject.optBoolean("isCompleted", false),
                    categoryId = jsonObject.optString("categoryId").takeIf { it.isNotEmpty() },
                    seriesId = jsonObject.optString("seriesId").takeIf { it.isNotEmpty() },
                    seriesOrder = jsonObject.optInt("seriesOrder", 0),
                    fileType = jsonObject.optString("fileType", "mp3"),
                    timesListened = jsonObject.optInt("timesListened", 0),
                    contentType = runCatching {
                        ContentType.valueOf(jsonObject.optString("contentType", ContentType.MUSIC.name))
                    }.getOrDefault(ContentType.MUSIC)
                )
                music.add(track)
            }
            music
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Save comics to persistent storage
     */
    suspend fun saveComics(comics: List<LibraryComic>) = withContext(Dispatchers.IO) {
        val jsonArray = JSONArray()
        comics.forEach { comic ->
            val jsonObject = JSONObject().apply {
                put("id", comic.id)
                put("uri", comic.uri.toString())
                put("title", comic.title)
                put("author", comic.author)
                put("series", comic.series ?: "")
                put("coverArtUri", comic.coverArtUri ?: "")
                put("totalPages", comic.totalPages)
                put("currentPage", comic.currentPage)
                put("lastRead", comic.lastRead)
                put("dateAdded", comic.dateAdded)
                put("isCompleted", comic.isCompleted)
                put("categoryId", comic.categoryId ?: "")
                put("seriesId", comic.seriesId ?: "")
                put("seriesOrder", comic.seriesOrder)
                put("fileType", comic.fileType)
            }
            jsonArray.put(jsonObject)
        }
        prefs.edit().putString(getProfileKey(KEY_COMICS), jsonArray.toString()).commit()
    }

    /**
     * Load comics from persistent storage (per-profile)
     */
    suspend fun loadComics(): List<LibraryComic> = withContext(Dispatchers.IO) {
        val jsonString = prefs.getString(getProfileKey(KEY_COMICS), null) ?: return@withContext emptyList()

        try {
            val jsonArray = JSONArray(jsonString)
            val comics = mutableListOf<LibraryComic>()

            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val comic = LibraryComic(
                    id = jsonObject.getString("id"),
                    uri = Uri.parse(jsonObject.getString("uri")),
                    title = jsonObject.getString("title"),
                    author = jsonObject.optString("author", "Unknown Author"),
                    series = jsonObject.optString("series").takeIf { it.isNotEmpty() },
                    coverArtUri = jsonObject.optString("coverArtUri").takeIf { it.isNotEmpty() },
                    totalPages = jsonObject.optInt("totalPages", 0),
                    currentPage = jsonObject.optInt("currentPage", 0),
                    lastRead = jsonObject.optLong("lastRead", 0L),
                    dateAdded = jsonObject.optLong("dateAdded", System.currentTimeMillis()),
                    isCompleted = jsonObject.optBoolean("isCompleted", false),
                    categoryId = jsonObject.optString("categoryId").takeIf { it.isNotEmpty() },
                    seriesId = jsonObject.optString("seriesId").takeIf { it.isNotEmpty() },
                    seriesOrder = jsonObject.optInt("seriesOrder", 0),
                    fileType = jsonObject.optString("fileType", "cbz")
                )
                comics.add(comic)
            }
            comics
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Save movies to persistent storage
     */
    suspend fun saveMovies(movies: List<LibraryMovie>) = withContext(Dispatchers.IO) {
        val jsonArray = JSONArray()
        movies.forEach { movie ->
            val jsonObject = JSONObject().apply {
                put("id", movie.id)
                put("uri", movie.uri.toString())
                put("title", movie.title)
                put("duration", movie.duration)
                put("lastPosition", movie.lastPosition)
                put("lastPlayed", movie.lastPlayed)
                put("dateAdded", movie.dateAdded)
                put("isCompleted", movie.isCompleted)
                put("categoryId", movie.categoryId ?: "")
                put("seriesId", movie.seriesId ?: "")
                put("seriesOrder", movie.seriesOrder)
                put("thumbnailUri", movie.thumbnailUri?.toString() ?: "")
                put("coverArtUri", movie.coverArtUri ?: "")
                put("fileType", movie.fileType)
            }
            jsonArray.put(jsonObject)
        }
        prefs.edit().putString(getProfileKey(KEY_MOVIES), jsonArray.toString()).commit()
    }

    /**
     * Load movies from persistent storage (per-profile)
     */
    suspend fun loadMovies(): List<LibraryMovie> = withContext(Dispatchers.IO) {
        val jsonString = prefs.getString(getProfileKey(KEY_MOVIES), null) ?: return@withContext emptyList()

        try {
            val jsonArray = JSONArray(jsonString)
            val movies = mutableListOf<LibraryMovie>()

            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val movie = LibraryMovie(
                    id = jsonObject.getString("id"),
                    uri = Uri.parse(jsonObject.getString("uri")),
                    title = jsonObject.getString("title"),
                    duration = jsonObject.optLong("duration", 0L),
                    lastPosition = jsonObject.optLong("lastPosition", 0L),
                    lastPlayed = jsonObject.optLong("lastPlayed", 0L),
                    dateAdded = jsonObject.optLong("dateAdded", System.currentTimeMillis()),
                    isCompleted = jsonObject.optBoolean("isCompleted", false),
                    categoryId = jsonObject.optString("categoryId").takeIf { it.isNotEmpty() },
                    seriesId = jsonObject.optString("seriesId").takeIf { it.isNotEmpty() },
                    seriesOrder = jsonObject.optInt("seriesOrder", 0),
                    thumbnailUri = jsonObject.optString("thumbnailUri").takeIf { it.isNotEmpty() }?.let { Uri.parse(it) },
                    coverArtUri = jsonObject.optString("coverArtUri").takeIf { it.isNotEmpty() },
                    fileType = jsonObject.optString("fileType", "mp4")
                )
                movies.add(movie)
            }
            movies
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Save series to persistent storage
     */
    suspend fun saveSeries(series: List<LibrarySeries>) = withContext(Dispatchers.IO) {
        val jsonArray = JSONArray()
        series.forEach { s ->
            val jsonObject = JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("contentType", s.contentType.name)
                put("categoryId", s.categoryId ?: "")
                put("order", s.order)
                put("dateCreated", s.dateCreated)
                put("coverArtUri", s.coverArtUri ?: "")
            }
            jsonArray.put(jsonObject)
        }
        prefs.edit().putString(getProfileKey(KEY_SERIES), jsonArray.toString()).commit()
    }

    /**
     * Load series from persistent storage (per-profile)
     */
    suspend fun loadSeries(): List<LibrarySeries> = withContext(Dispatchers.IO) {
        val jsonString = prefs.getString(getProfileKey(KEY_SERIES), null) ?: return@withContext emptyList()

        try {
            val jsonArray = JSONArray(jsonString)
            val series = mutableListOf<LibrarySeries>()

            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val s = LibrarySeries(
                    id = jsonObject.getString("id"),
                    name = jsonObject.getString("name"),
                    contentType = try {
                        ContentType.valueOf(jsonObject.getString("contentType"))
                    } catch (e: Exception) {
                        ContentType.EBOOK
                    },
                    categoryId = jsonObject.optString("categoryId").takeIf { it.isNotEmpty() },
                    order = jsonObject.optInt("order", 0),
                    dateCreated = jsonObject.optLong("dateCreated", System.currentTimeMillis()),
                    coverArtUri = jsonObject.optString("coverArtUri").takeIf { it.isNotEmpty() }
                )
                series.add(s)
            }
            series
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // ==================== Folder-Based Playlist Operations ====================

    /**
     * Discover playlist folders and sync them with the series list
     * This creates LibrarySeries entries for any new folders found
     * AND creates folders for any existing series that don't have folders
     */
    suspend fun syncPlaylistFoldersWithSeries(existingSeries: List<LibrarySeries>): List<LibrarySeries> = withContext(Dispatchers.IO) {
        val allDiscovered: Map<ContentType, List<DiscoveredPlaylist>> = playlistFolderManager.discoverAllPlaylistFolders(currentProfileName)
        val updatedSeries = existingSeries.toMutableList()
        var maxOrder = existingSeries.maxOfOrNull { it.order } ?: -1

        // First: Create folders for any existing series that don't have folders yet
        for (series in existingSeries) {
            val discoveredForType = allDiscovered[series.contentType] ?: emptyList()
            val hasFolder = discoveredForType.any { it.folderName.equals(series.name, ignoreCase = true) }
            if (!hasFolder) {
                // Create the folder for this series
                playlistFolderManager.createPlaylistFolder(currentProfileName, series.contentType, series.name)
            }
        }

        // Second: For each content type, check for new folders and create series entries
        for ((contentType, discoveredPlaylists) in allDiscovered) {
            for (discovered in discoveredPlaylists) {
                // Check if a series with this name and content type already exists
                val existingMatch = updatedSeries.find {
                    it.name.equals(discovered.folderName, ignoreCase = true) &&
                    it.contentType == contentType
                }

                if (existingMatch == null) {
                    // Create a new series for this folder
                    maxOrder++
                    val newSeries = LibrarySeries(
                        id = UUID.randomUUID().toString(),
                        name = discovered.folderName,
                        contentType = contentType,
                        categoryId = null,
                        order = maxOrder,
                        dateCreated = discovered.dateCreated
                    )
                    updatedSeries.add(newSeries)
                }
            }
        }

        updatedSeries
    }

    /**
     * Discover playlist folders for a specific content type
     */
    suspend fun discoverPlaylistFolders(contentType: ContentType): List<DiscoveredPlaylist> {
        return playlistFolderManager.discoverPlaylistFolders(currentProfileName, contentType)
    }

    /**
     * Create a playlist folder when a new series is added
     */
    suspend fun createPlaylistFolder(contentType: ContentType, playlistName: String): Boolean = withContext(Dispatchers.IO) {
        playlistFolderManager.createPlaylistFolder(currentProfileName, contentType, playlistName) != null
    }

    /**
     * Rename a playlist folder when a series is renamed
     */
    suspend fun renamePlaylistFolder(contentType: ContentType, oldName: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        playlistFolderManager.renamePlaylistFolder(currentProfileName, contentType, oldName, newName) != null
    }

    /**
     * Delete a playlist folder when a series is deleted
     */
    suspend fun deletePlaylistFolder(contentType: ContentType, playlistName: String, deleteContents: Boolean = false): Boolean {
        return playlistFolderManager.deletePlaylistFolder(currentProfileName, contentType, playlistName, deleteContents)
    }

    /**
     * Get the file path for a playlist folder
     */
    fun getPlaylistFolderPath(contentType: ContentType, playlistName: String): String {
        return playlistFolderManager.getPlaylistFolderPath(currentProfileName, contentType, playlistName)
    }

    /**
     * Check if a playlist folder exists
     */
    fun playlistFolderExists(contentType: ContentType, playlistName: String): Boolean {
        return playlistFolderManager.playlistFolderExists(currentProfileName, contentType, playlistName)
    }

    /**
     * Ensure all content folders exist for the current profile
     */
    suspend fun ensureContentFoldersExist() {
        playlistFolderManager.ensureContentFoldersExist(currentProfileName)
    }

    /**
     * Move a file to a playlist folder
     * @param filePath The full path to the source file
     * @param contentType The type of content (AUDIOBOOK, EBOOK, MUSIC, COMICS, MOVIE)
     * @param playlistName The name of the target playlist folder
     * @return The new file path if successful, null otherwise
     */
    suspend fun moveFileToPlaylist(filePath: String, contentType: ContentType, playlistName: String): String? {
        val sourceFile = java.io.File(filePath)
        if (!sourceFile.exists()) return null

        val result = playlistFolderManager.moveFileToPlaylist(
            sourceFile = sourceFile,
            profileName = currentProfileName,
            contentType = contentType,
            targetPlaylistName = playlistName
        )
        return result?.absolutePath
    }

    /**
     * Move a file back to the root content folder (out of a playlist)
     * @param filePath The full path to the source file
     * @param contentType The type of content
     * @return The new file path if successful, null otherwise
     */
    suspend fun moveFileToRoot(filePath: String, contentType: ContentType): String? {
        val sourceFile = java.io.File(filePath)
        if (!sourceFile.exists()) return null

        val result = playlistFolderManager.moveFileToRoot(
            sourceFile = sourceFile,
            profileName = currentProfileName,
            contentType = contentType
        )
        return result?.absolutePath
    }

    // ==================== Progress JSON File Operations ====================

    /**
     * Save all progress to progress.json file
     * Called when app exits or when explicitly saving
     */
    suspend fun saveProgressToFile(
        audiobooks: List<LibraryAudiobook>,
        books: List<LibraryBook>,
        music: List<LibraryMusic>,
        comics: List<LibraryComic>,
        movies: List<LibraryMovie>
    ): Boolean {
        return progressFileManager.saveProgress(
            profileName = currentProfileName,
            audiobooks = audiobooks,
            books = books,
            music = music,
            comics = comics,
            movies = movies
        )
    }

    /**
     * Load progress from progress.json file
     * Returns a map of URI string to ProgressEntry
     */
    suspend fun loadProgressFromFile(): Map<String, ProgressFileManager.ProgressEntry> {
        return progressFileManager.loadProgress(currentProfileName)
    }

    /**
     * Update a single item's progress in the JSON file
     * Used for real-time progress updates
     */
    suspend fun updateProgressInFile(
        uri: String,
        type: String,
        position: Long,
        total: Long
    ): Boolean {
        return progressFileManager.updateSingleProgress(
            profileName = currentProfileName,
            uri = uri,
            type = type,
            position = position,
            total = total
        )
    }

    // ==================== Backup/Restore Functions ====================

    /**
     * Export all library data for the current profile as a JSON object
     * This includes audiobooks, books, music, comics, movies, series, and categories
     */
    suspend fun exportLibraryData(): JSONObject = withContext(Dispatchers.IO) {
        JSONObject().apply {
            prefs.getString(getProfileKey(KEY_LIBRARY), null)?.let { put("audiobooks", JSONArray(it)) }
            prefs.getString(getProfileKey(KEY_BOOKS), null)?.let { put("books", JSONArray(it)) }
            prefs.getString(getProfileKey(KEY_MUSIC), null)?.let { put("music", JSONArray(it)) }
            prefs.getString(getProfileKey(KEY_COMICS), null)?.let { put("comics", JSONArray(it)) }
            prefs.getString(getProfileKey(KEY_MOVIES), null)?.let { put("movies", JSONArray(it)) }
            prefs.getString(getProfileKey(KEY_SERIES), null)?.let { put("series", JSONArray(it)) }
            prefs.getString(getProfileKey(KEY_CATEGORIES), null)?.let { put("categories", JSONArray(it)) }
            prefs.getString(getProfileKey(KEY_LAST_PLAYED), null)?.let { put("lastPlayed", it) }
            put("playbackSpeed", prefs.getFloat(getProfileKey(KEY_PLAYBACK_SPEED), 1.0f).toDouble())
        }
    }

    /**
     * Import library data from a JSON object into the current profile
     * This restores audiobooks, books, music, comics, movies, series, and categories
     */
    suspend fun importLibraryData(data: JSONObject) = withContext(Dispatchers.IO) {
        prefs.edit().apply {
            data.optJSONArray("audiobooks")?.let { putString(getProfileKey(KEY_LIBRARY), it.toString()) }
            data.optJSONArray("books")?.let { putString(getProfileKey(KEY_BOOKS), it.toString()) }
            data.optJSONArray("music")?.let { putString(getProfileKey(KEY_MUSIC), it.toString()) }
            data.optJSONArray("comics")?.let { putString(getProfileKey(KEY_COMICS), it.toString()) }
            data.optJSONArray("movies")?.let { putString(getProfileKey(KEY_MOVIES), it.toString()) }
            data.optJSONArray("series")?.let { putString(getProfileKey(KEY_SERIES), it.toString()) }
            data.optJSONArray("categories")?.let { putString(getProfileKey(KEY_CATEGORIES), it.toString()) }
            data.optString("lastPlayed").takeIf { it.isNotEmpty() }?.let { putString(getProfileKey(KEY_LAST_PLAYED), it) }
            if (data.has("playbackSpeed")) {
                putFloat(getProfileKey(KEY_PLAYBACK_SPEED), data.optDouble("playbackSpeed", 1.0).toFloat())
            }
            commit()
        }
    }

    /**
     * Export library data for a specific profile name (not necessarily the current one)
     */
    suspend fun exportLibraryDataForProfile(profileName: String): JSONObject = withContext(Dispatchers.IO) {
        val profileKey = { baseKey: String -> "${baseKey}_profile_${profileName.replace(Regex("[^a-zA-Z0-9]"), "_")}" }
        JSONObject().apply {
            prefs.getString(profileKey(KEY_LIBRARY), null)?.let { put("audiobooks", JSONArray(it)) }
            prefs.getString(profileKey(KEY_BOOKS), null)?.let { put("books", JSONArray(it)) }
            prefs.getString(profileKey(KEY_MUSIC), null)?.let { put("music", JSONArray(it)) }
            prefs.getString(profileKey(KEY_COMICS), null)?.let { put("comics", JSONArray(it)) }
            prefs.getString(profileKey(KEY_MOVIES), null)?.let { put("movies", JSONArray(it)) }
            prefs.getString(profileKey(KEY_SERIES), null)?.let { put("series", JSONArray(it)) }
            prefs.getString(profileKey(KEY_CATEGORIES), null)?.let { put("categories", JSONArray(it)) }
            prefs.getString(profileKey(KEY_LAST_PLAYED), null)?.let { put("lastPlayed", it) }
            put("playbackSpeed", prefs.getFloat(profileKey(KEY_PLAYBACK_SPEED), 1.0f).toDouble())
        }
    }

    /**
     * Import library data for a specific profile name (not necessarily the current one)
     */
    suspend fun importLibraryDataForProfile(profileName: String, data: JSONObject) = withContext(Dispatchers.IO) {
        val profileKey = { baseKey: String -> "${baseKey}_profile_${profileName.replace(Regex("[^a-zA-Z0-9]"), "_")}" }
        prefs.edit().apply {
            data.optJSONArray("audiobooks")?.let { putString(profileKey(KEY_LIBRARY), it.toString()) }
            data.optJSONArray("books")?.let { putString(profileKey(KEY_BOOKS), it.toString()) }
            data.optJSONArray("music")?.let { putString(profileKey(KEY_MUSIC), it.toString()) }
            data.optJSONArray("comics")?.let { putString(profileKey(KEY_COMICS), it.toString()) }
            data.optJSONArray("movies")?.let { putString(profileKey(KEY_MOVIES), it.toString()) }
            data.optJSONArray("series")?.let { putString(profileKey(KEY_SERIES), it.toString()) }
            data.optJSONArray("categories")?.let { putString(profileKey(KEY_CATEGORIES), it.toString()) }
            data.optString("lastPlayed").takeIf { it.isNotEmpty() }?.let { putString(profileKey(KEY_LAST_PLAYED), it) }
            if (data.has("playbackSpeed")) {
                putFloat(profileKey(KEY_PLAYBACK_SPEED), data.optDouble("playbackSpeed", 1.0).toFloat())
            }
            commit()
        }
    }

    companion object {
        private const val PREFS_NAME = "audible_library_prefs"
        private const val KEY_LIBRARY = "library_data"
        private const val KEY_BOOKS = "books_data"
        private const val KEY_MUSIC = "music_data"
        private const val KEY_COMICS = "comics_data"
        private const val KEY_MOVIES = "movies_data"
        private const val KEY_SERIES = "series_data"
        private const val KEY_LAST_PLAYED = "last_played_id"
        private const val KEY_PLAYBACK_SPEED = "playback_speed"
        private const val KEY_CATEGORIES = "categories_data"
    }
}
