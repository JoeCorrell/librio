package com.librio.data.repository

import android.content.Context
import android.net.Uri
import com.librio.data.LibraryFileManager
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
 * Repository for persisting library data using JSON files
 * Supports per-profile library storage for content isolation
 * Data is stored in library.json file per profile in Librio/Profiles/{ProfileName}/
 */
class LibraryRepository(private val context: Context) {

    // Legacy SharedPreferences for migration only
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // File-based library storage (replaces SharedPreferences)
    private val libraryFileManager = LibraryFileManager()

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
     * Get the library file manager for external access
     */
    fun getLibraryFileManager(): LibraryFileManager = libraryFileManager

    /**
     * Get profile-specific key for storage (legacy, for migration)
     */
    private fun getProfileKey(baseKey: String): String {
        return "${baseKey}_profile_${currentProfileName.replace(Regex("[^a-zA-Z0-9]"), "_")}"
    }

    /**
     * Check if migration from SharedPreferences to JSON file is needed
     */
    fun needsMigration(): Boolean {
        val hasSharedPrefsData = prefs.getString(getProfileKey(KEY_LIBRARY), null) != null ||
                prefs.getString(getProfileKey(KEY_BOOKS), null) != null ||
                prefs.getString(getProfileKey(KEY_MUSIC), null) != null
        val hasJsonFile = libraryFileManager.libraryFileExists(currentProfileName)
        return hasSharedPrefsData && !hasJsonFile
    }

    /**
     * Migrate data from SharedPreferences to JSON file
     */
    suspend fun migrateToJsonFile(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Load all data from SharedPreferences using legacy methods
            val audiobooks = loadLibraryFromPrefs()
            val books = loadBooksFromPrefs()
            val music = loadMusicFromPrefs()
            val comics = loadComicsFromPrefs()
            val movies = loadMoviesFromPrefs()
            val series = loadSeriesFromPrefs()
            val categories = loadCategoriesFromPrefs()
            val lastPlayedId = prefs.getString(getProfileKey(KEY_LAST_PLAYED), null)
            val playbackSpeed = prefs.getFloat(getProfileKey(KEY_PLAYBACK_SPEED), 1.0f)

            // Save to JSON file
            val success = libraryFileManager.saveLibrary(
                profileName = currentProfileName,
                audiobooks = audiobooks,
                books = books,
                music = music,
                comics = comics,
                movies = movies,
                series = series,
                categories = categories,
                lastPlayedId = lastPlayedId,
                playbackSpeed = playbackSpeed
            )

            if (success) {
                // Clear SharedPreferences after successful migration
                prefs.edit().apply {
                    remove(getProfileKey(KEY_LIBRARY))
                    remove(getProfileKey(KEY_BOOKS))
                    remove(getProfileKey(KEY_MUSIC))
                    remove(getProfileKey(KEY_COMICS))
                    remove(getProfileKey(KEY_MOVIES))
                    remove(getProfileKey(KEY_SERIES))
                    remove(getProfileKey(KEY_CATEGORIES))
                    remove(getProfileKey(KEY_LAST_PLAYED))
                    remove(getProfileKey(KEY_PLAYBACK_SPEED))
                    apply()
                }
            }

            success
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ==================== Legacy SharedPreferences Load Methods (for migration) ====================

    private fun loadLibraryFromPrefs(): List<LibraryAudiobook> {
        val jsonString = prefs.getString(getProfileKey(KEY_LIBRARY), null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            val audiobooks = mutableListOf<LibraryAudiobook>()
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val audiobook = LibraryAudiobook(
                    id = jsonObject.getString("id"),
                    uri = Uri.parse(jsonObject.getString("uri")),
                    title = jsonObject.getString("title"),
                    author = jsonObject.optString("author", "Unknown Author"),
                    narrator = jsonObject.optString("narrator").takeIf { it.isNotEmpty() },
                    track = jsonObject.optInt("track", -1).takeIf { it >= 0 },
                    album = jsonObject.optString("album").takeIf { it.isNotEmpty() },
                    coverArtUri = jsonObject.optString("coverArtUri").takeIf { it.isNotEmpty() }?.let { Uri.parse(it) },
                    duration = jsonObject.optLong("duration", 0L),
                    lastPosition = jsonObject.optLong("lastPosition", 0L),
                    lastPlayed = jsonObject.optLong("lastPlayed", 0L),
                    dateAdded = jsonObject.optLong("dateAdded", System.currentTimeMillis()),
                    isCompleted = jsonObject.optBoolean("isCompleted", false),
                    isFavorite = jsonObject.optBoolean("isFavorite", false),
                    categoryId = jsonObject.optString("categoryId").takeIf { it.isNotEmpty() },
                    seriesId = jsonObject.optString("seriesId").takeIf { it.isNotEmpty() },
                    seriesOrder = jsonObject.optInt("seriesOrder", 0),
                    fileType = jsonObject.optString("fileType", "mp3"),
                    coverArt = null
                )
                audiobooks.add(audiobook)
            }
            audiobooks
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun loadBooksFromPrefs(): List<LibraryBook> {
        val jsonString = prefs.getString(getProfileKey(KEY_BOOKS), null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            val books = mutableListOf<LibraryBook>()
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val book = LibraryBook(
                    id = jsonObject.getString("id"),
                    uri = Uri.parse(jsonObject.getString("uri")),
                    title = jsonObject.getString("title"),
                    author = jsonObject.optString("author", "Unknown Author"),
                    narrator = jsonObject.optString("narrator").takeIf { it.isNotEmpty() },
                    coverArtUri = jsonObject.optString("coverArtUri").takeIf { it.isNotEmpty() },
                    totalPages = jsonObject.optInt("totalPages", 0),
                    currentPage = jsonObject.optInt("currentPage", 0),
                    lastRead = jsonObject.optLong("lastRead", 0L),
                    dateAdded = jsonObject.optLong("dateAdded", System.currentTimeMillis()),
                    isCompleted = jsonObject.optBoolean("isCompleted", false),
                    isFavorite = jsonObject.optBoolean("isFavorite", false),
                    categoryId = jsonObject.optString("categoryId").takeIf { it.isNotEmpty() },
                    seriesId = jsonObject.optString("seriesId").takeIf { it.isNotEmpty() },
                    seriesOrder = jsonObject.optInt("seriesOrder", 0),
                    fileType = jsonObject.optString("fileType", "pdf")
                )
                books.add(book)
            }
            books
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun loadMusicFromPrefs(): List<LibraryMusic> {
        val jsonString = prefs.getString(getProfileKey(KEY_MUSIC), null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            val music = mutableListOf<LibraryMusic>()
            val seenUris = mutableSetOf<String>()
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val uriString = jsonObject.getString("uri")
                if (uriString in seenUris) continue
                seenUris.add(uriString)
                val track = LibraryMusic(
                    id = jsonObject.getString("id"),
                    uri = Uri.parse(uriString),
                    title = jsonObject.getString("title"),
                    artist = jsonObject.optString("artist", "Unknown Artist"),
                    album = jsonObject.optString("album").takeIf { it.isNotEmpty() },
                    track = jsonObject.optInt("track", -1).takeIf { it >= 0 },
                    coverArtUri = jsonObject.optString("coverArtUri").takeIf { it.isNotEmpty() },
                    duration = jsonObject.optLong("duration", 0L),
                    lastPosition = jsonObject.optLong("lastPosition", 0L),
                    lastPlayed = jsonObject.optLong("lastPlayed", 0L),
                    dateAdded = jsonObject.optLong("dateAdded", System.currentTimeMillis()),
                    isCompleted = jsonObject.optBoolean("isCompleted", false),
                    isFavorite = jsonObject.optBoolean("isFavorite", false),
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

    private fun loadComicsFromPrefs(): List<LibraryComic> {
        val jsonString = prefs.getString(getProfileKey(KEY_COMICS), null) ?: return emptyList()
        return try {
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
                    isFavorite = jsonObject.optBoolean("isFavorite", false),
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

    private fun loadMoviesFromPrefs(): List<LibraryMovie> {
        val jsonString = prefs.getString(getProfileKey(KEY_MOVIES), null) ?: return emptyList()
        return try {
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
                    isFavorite = jsonObject.optBoolean("isFavorite", false),
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

    private fun loadSeriesFromPrefs(): List<LibrarySeries> {
        val jsonString = prefs.getString(getProfileKey(KEY_SERIES), null) ?: return emptyList()
        return try {
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

    private fun loadCategoriesFromPrefs(): List<Category> {
        val jsonString = prefs.getString(getProfileKey(KEY_CATEGORIES), null) ?: return emptyList()
        return try {
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

    // ==================== Main Save/Load Methods (using JSON files) ====================

    /**
     * Save the library to persistent storage (per-profile)
     * Now uses JSON file instead of SharedPreferences
     */
    suspend fun saveLibrary(audiobooks: List<LibraryAudiobook>) = withContext(Dispatchers.IO) {
        libraryFileManager.saveAudiobooks(currentProfileName, audiobooks)
    }

    /**
     * Load the library from persistent storage (per-profile)
     * Now uses JSON file instead of SharedPreferences
     */
    suspend fun loadLibrary(): List<LibraryAudiobook> = withContext(Dispatchers.IO) {
        libraryFileManager.loadAudiobooks(currentProfileName)
    }

    /**
     * Save the last played audiobook ID (per-profile)
     * Now uses JSON file instead of SharedPreferences
     */
    fun saveLastPlayedId(audiobookId: String?) {
        kotlinx.coroutines.runBlocking {
            libraryFileManager.saveLastPlayedId(currentProfileName, audiobookId)
        }
    }

    /**
     * Get the last played audiobook ID (per-profile)
     * Now uses JSON file instead of SharedPreferences
     */
    fun getLastPlayedId(): String? {
        return kotlinx.coroutines.runBlocking {
            libraryFileManager.loadLastPlayedId(currentProfileName)
        }
    }

    /**
     * Save playback settings (per-profile)
     * Now uses JSON file instead of SharedPreferences
     */
    fun savePlaybackSpeed(speed: Float) {
        kotlinx.coroutines.runBlocking {
            libraryFileManager.savePlaybackSpeed(currentProfileName, speed)
        }
    }

    /**
     * Get playback speed (per-profile)
     * Now uses JSON file instead of SharedPreferences
     */
    fun getPlaybackSpeed(): Float {
        return kotlinx.coroutines.runBlocking {
            libraryFileManager.loadPlaybackSpeed(currentProfileName)
        }
    }

    /**
     * Save categories to persistent storage (per-profile)
     * Now uses JSON file instead of SharedPreferences
     */
    suspend fun saveCategories(categories: List<Category>) = withContext(Dispatchers.IO) {
        libraryFileManager.saveCategories(currentProfileName, categories)
    }

    /**
     * Load categories from persistent storage (per-profile)
     * Now uses JSON file instead of SharedPreferences
     */
    suspend fun loadCategories(): List<Category> = withContext(Dispatchers.IO) {
        libraryFileManager.loadCategories(currentProfileName)
    }

    /**
     * Save books to persistent storage
     * Now uses JSON file instead of SharedPreferences
     */
    suspend fun saveBooks(books: List<LibraryBook>) = withContext(Dispatchers.IO) {
        libraryFileManager.saveBooks(currentProfileName, books)
    }

    /**
     * Load books from persistent storage (per-profile)
     * Now uses JSON file instead of SharedPreferences
     */
    suspend fun loadBooks(): List<LibraryBook> = withContext(Dispatchers.IO) {
        libraryFileManager.loadBooks(currentProfileName)
    }

    /**
     * Save music to persistent storage
     * Now uses JSON file instead of SharedPreferences
     */
    suspend fun saveMusic(music: List<LibraryMusic>) = withContext(Dispatchers.IO) {
        libraryFileManager.saveMusic(currentProfileName, music)
    }

    /**
     * Load music from persistent storage (per-profile)
     * Now uses JSON file instead of SharedPreferences
     */
    suspend fun loadMusic(): List<LibraryMusic> = withContext(Dispatchers.IO) {
        libraryFileManager.loadMusic(currentProfileName)
    }

    /**
     * Save comics to persistent storage
     * Now uses JSON file instead of SharedPreferences
     */
    suspend fun saveComics(comics: List<LibraryComic>) = withContext(Dispatchers.IO) {
        libraryFileManager.saveComics(currentProfileName, comics)
    }

    /**
     * Load comics from persistent storage (per-profile)
     * Now uses JSON file instead of SharedPreferences
     */
    suspend fun loadComics(): List<LibraryComic> = withContext(Dispatchers.IO) {
        libraryFileManager.loadComics(currentProfileName)
    }

    /**
     * Save movies to persistent storage
     * Now uses JSON file instead of SharedPreferences
     */
    suspend fun saveMovies(movies: List<LibraryMovie>) = withContext(Dispatchers.IO) {
        libraryFileManager.saveMovies(currentProfileName, movies)
    }

    /**
     * Load movies from persistent storage (per-profile)
     * Now uses JSON file instead of SharedPreferences
     */
    suspend fun loadMovies(): List<LibraryMovie> = withContext(Dispatchers.IO) {
        libraryFileManager.loadMovies(currentProfileName)
    }

    /**
     * Save series to persistent storage
     * Now uses JSON file instead of SharedPreferences
     */
    suspend fun saveSeries(series: List<LibrarySeries>) = withContext(Dispatchers.IO) {
        libraryFileManager.saveSeries(currentProfileName, series)
    }

    /**
     * Load series from persistent storage (per-profile)
     * Now uses JSON file instead of SharedPreferences
     */
    suspend fun loadSeries(): List<LibrarySeries> = withContext(Dispatchers.IO) {
        libraryFileManager.loadSeries(currentProfileName)
    }

    // ==================== Folder-Based Playlist Operations ====================

    /**
     * Discover playlist folders and sync them with the series list
     * This creates LibrarySeries entries for any new folders found
     * AND creates folders for any existing series that don't have folders
     * @param deletedSeriesNames Map of content type name to set of deleted series names to exclude
     */
    suspend fun syncPlaylistFoldersWithSeries(
        existingSeries: List<LibrarySeries>,
        deletedSeriesNames: Map<String, Set<String>> = emptyMap()
    ): List<LibrarySeries> = withContext(Dispatchers.IO) {
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
            // Get the deleted names for this content type
            val deletedNames = deletedSeriesNames[contentType.name] ?: emptySet()

            for (discovered in discoveredPlaylists) {
                // Skip if this folder name is in the deleted list (case-insensitive check)
                if (deletedNames.any { it.equals(discovered.folderName, ignoreCase = true) }) {
                    continue
                }

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
     * Now uses JSON file instead of SharedPreferences
     */
    suspend fun exportLibraryData(): JSONObject = withContext(Dispatchers.IO) {
        libraryFileManager.exportLibraryData(currentProfileName)
    }

    /**
     * Import library data from a JSON object into the current profile
     * This restores audiobooks, books, music, comics, movies, series, and categories
     * Now uses JSON file instead of SharedPreferences
     */
    suspend fun importLibraryData(data: JSONObject) = withContext(Dispatchers.IO) {
        libraryFileManager.importLibraryData(currentProfileName, data)
    }

    /**
     * Export library data for a specific profile name (not necessarily the current one)
     * Now uses JSON file instead of SharedPreferences
     */
    suspend fun exportLibraryDataForProfile(profileName: String): JSONObject = withContext(Dispatchers.IO) {
        libraryFileManager.exportLibraryData(profileName)
    }

    /**
     * Import library data for a specific profile name (not necessarily the current one)
     * Now uses JSON file instead of SharedPreferences
     */
    suspend fun importLibraryDataForProfile(profileName: String, data: JSONObject) = withContext(Dispatchers.IO) {
        libraryFileManager.importLibraryData(profileName, data)
    }

    /**
     * Migrate all library data from old profile to new profile
     * This should be called when a profile is renamed to preserve library data
     * Now uses file-based migration (rename library.json location)
     */
    suspend fun migrateLibraryDataForRename(oldName: String, newName: String) = withContext(Dispatchers.IO) {
        // Export from old profile, import to new profile
        val data = libraryFileManager.exportLibraryData(oldName)
        if (data.length() > 0) {
            libraryFileManager.importLibraryData(newName, data)
            // Delete old library file
            val oldFile = libraryFileManager.getLibraryFile(oldName)
            if (oldFile.exists()) {
                oldFile.delete()
            }
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
