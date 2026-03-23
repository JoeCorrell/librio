package com.librio.data

import android.net.Uri
import com.librio.LibrioApplication
import com.librio.model.Category
import com.librio.model.ContentType
import com.librio.model.LibraryAudiobook
import com.librio.model.LibraryBook
import com.librio.model.LibraryComic
import com.librio.model.LibraryMovie
import com.librio.model.LibraryMusic
import com.librio.model.LibrarySeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manages file-based storage of library data per profile
 * Replaces SharedPreferences-based storage for library items
 * Stores all library data in library.json file per profile
 */
class LibraryFileManager {

    companion object {
        const val LIBRARY_FILE = "library.json"
        const val LIBRARY_VERSION = 1
    }

    private val librioRoot: File get() = LibrioApplication.getLibrioRoot()
    private val profilesRoot = File(librioRoot, "Profiles")

    /**
     * Get the profile folder for a given profile name
     */
    fun getProfileFolder(profileName: String): File {
        return File(profilesRoot, sanitizeFolderName(profileName))
    }

    /**
     * Get the library.json file for a given profile
     */
    fun getLibraryFile(profileName: String): File {
        val folder = getProfileFolder(profileName)
        if (!folder.exists()) {
            folder.mkdirs()
        }
        return File(folder, LIBRARY_FILE)
    }

    /**
     * Check if library file exists for a profile
     */
    fun libraryFileExists(profileName: String): Boolean {
        return getLibraryFile(profileName).exists()
    }

    /**
     * Sanitize folder name to be filesystem safe
     */
    private fun sanitizeFolderName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }

    // ==================== Complete Library Save/Load ====================

    /**
     * Save all library data to library.json
     */
    suspend fun saveLibrary(
        profileName: String,
        audiobooks: List<LibraryAudiobook>,
        books: List<LibraryBook>,
        music: List<LibraryMusic>,
        comics: List<LibraryComic>,
        movies: List<LibraryMovie>,
        series: List<LibrarySeries>,
        categories: List<Category>,
        lastPlayedId: String?,
        playbackSpeed: Float
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val libraryFile = getLibraryFile(profileName)
            val json = JSONObject().apply {
                put("version", LIBRARY_VERSION)
                put("lastModified", System.currentTimeMillis())
                put("audiobooks", audiobooksToJson(audiobooks))
                put("books", booksToJson(books))
                put("music", musicToJson(music))
                put("comics", comicsToJson(comics))
                put("movies", moviesToJson(movies))
                put("series", seriesToJson(series))
                put("categories", categoriesToJson(categories))
                put("lastPlayedId", lastPlayedId ?: "")
                put("playbackSpeed", playbackSpeed.toDouble())
            }
            // Atomic write: write to temp file then rename to prevent corruption
            val tempFile = java.io.File(libraryFile.parent, "${libraryFile.name}.tmp")
            tempFile.writeText(json.toString(2))
            if (!tempFile.renameTo(libraryFile)) {
                        tempFile.copyTo(libraryFile, overwrite = true)
                        tempFile.delete()
                    }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Load all library data from library.json
     */
    suspend fun loadLibrary(profileName: String): LibraryData? = withContext(Dispatchers.IO) {
        val libraryFile = getLibraryFile(profileName)
        // Try main file first, then fall back to temp file (from interrupted atomic write)
        val filesToTry = listOfNotNull(
            libraryFile.takeIf { it.exists() },
            java.io.File(libraryFile.parent, "${libraryFile.name}.tmp").takeIf { it.exists() }
        )
        for (file in filesToTry) {
            try {
                val json = JSONObject(file.readText())
                return@withContext LibraryData(
                    audiobooks = jsonToAudiobooks(json.optJSONArray("audiobooks")),
                    books = jsonToBooks(json.optJSONArray("books")),
                    music = jsonToMusic(json.optJSONArray("music")),
                    comics = jsonToComics(json.optJSONArray("comics")),
                    movies = jsonToMovies(json.optJSONArray("movies")),
                    series = jsonToSeries(json.optJSONArray("series")),
                    categories = jsonToCategories(json.optJSONArray("categories")),
                    lastPlayedId = json.optString("lastPlayedId").takeIf { it.isNotEmpty() },
                    playbackSpeed = json.optDouble("playbackSpeed", 1.0).toFloat()
                )
            } catch (e: Exception) {
                e.printStackTrace()
                // Try next file
            }
        }
        null
    }

    // ==================== Individual Save Methods ====================

    /**
     * Save only audiobooks to library.json (updates existing file)
     */
    suspend fun saveAudiobooks(profileName: String, audiobooks: List<LibraryAudiobook>): Boolean =
        updateLibraryField(profileName, "audiobooks", audiobooksToJson(audiobooks))

    /**
     * Save only books to library.json (updates existing file)
     */
    suspend fun saveBooks(profileName: String, books: List<LibraryBook>): Boolean =
        updateLibraryField(profileName, "books", booksToJson(books))

    /**
     * Save only music to library.json (updates existing file)
     */
    suspend fun saveMusic(profileName: String, music: List<LibraryMusic>): Boolean =
        updateLibraryField(profileName, "music", musicToJson(music))

    /**
     * Save only comics to library.json (updates existing file)
     */
    suspend fun saveComics(profileName: String, comics: List<LibraryComic>): Boolean =
        updateLibraryField(profileName, "comics", comicsToJson(comics))

    /**
     * Save only movies to library.json (updates existing file)
     */
    suspend fun saveMovies(profileName: String, movies: List<LibraryMovie>): Boolean =
        updateLibraryField(profileName, "movies", moviesToJson(movies))

    /**
     * Save only series to library.json (updates existing file)
     */
    suspend fun saveSeries(profileName: String, series: List<LibrarySeries>): Boolean =
        updateLibraryField(profileName, "series", seriesToJson(series))

    /**
     * Save only categories to library.json (updates existing file)
     */
    suspend fun saveCategories(profileName: String, categories: List<Category>): Boolean =
        updateLibraryField(profileName, "categories", categoriesToJson(categories))

    /**
     * Save last played ID
     */
    suspend fun saveLastPlayedId(profileName: String, lastPlayedId: String?): Boolean =
        updateLibraryStringField(profileName, "lastPlayedId", lastPlayedId ?: "")

    /**
     * Save playback speed
     */
    suspend fun savePlaybackSpeed(profileName: String, speed: Float): Boolean =
        updateLibraryDoubleField(profileName, "playbackSpeed", speed.toDouble())

    // ==================== Individual Load Methods ====================

    /**
     * Load only audiobooks from library.json
     */
    suspend fun loadAudiobooks(profileName: String): List<LibraryAudiobook> = withContext(Dispatchers.IO) {
        try {
            val libraryFile = getLibraryFile(profileName)
            if (!libraryFile.exists()) return@withContext emptyList()
            val json = JSONObject(libraryFile.readText())
            jsonToAudiobooks(json.optJSONArray("audiobooks"))
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Load only books from library.json
     */
    suspend fun loadBooks(profileName: String): List<LibraryBook> = withContext(Dispatchers.IO) {
        try {
            val libraryFile = getLibraryFile(profileName)
            if (!libraryFile.exists()) return@withContext emptyList()
            val json = JSONObject(libraryFile.readText())
            jsonToBooks(json.optJSONArray("books"))
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Load only music from library.json
     */
    suspend fun loadMusic(profileName: String): List<LibraryMusic> = withContext(Dispatchers.IO) {
        try {
            val libraryFile = getLibraryFile(profileName)
            if (!libraryFile.exists()) return@withContext emptyList()
            val json = JSONObject(libraryFile.readText())
            jsonToMusic(json.optJSONArray("music"))
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Load only comics from library.json
     */
    suspend fun loadComics(profileName: String): List<LibraryComic> = withContext(Dispatchers.IO) {
        try {
            val libraryFile = getLibraryFile(profileName)
            if (!libraryFile.exists()) return@withContext emptyList()
            val json = JSONObject(libraryFile.readText())
            jsonToComics(json.optJSONArray("comics"))
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Load only movies from library.json
     */
    suspend fun loadMovies(profileName: String): List<LibraryMovie> = withContext(Dispatchers.IO) {
        try {
            val libraryFile = getLibraryFile(profileName)
            if (!libraryFile.exists()) return@withContext emptyList()
            val json = JSONObject(libraryFile.readText())
            jsonToMovies(json.optJSONArray("movies"))
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Load only series from library.json
     */
    suspend fun loadSeries(profileName: String): List<LibrarySeries> = withContext(Dispatchers.IO) {
        try {
            val libraryFile = getLibraryFile(profileName)
            if (!libraryFile.exists()) return@withContext emptyList()
            val json = JSONObject(libraryFile.readText())
            jsonToSeries(json.optJSONArray("series"))
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Load only categories from library.json
     */
    suspend fun loadCategories(profileName: String): List<Category> = withContext(Dispatchers.IO) {
        try {
            val libraryFile = getLibraryFile(profileName)
            if (!libraryFile.exists()) return@withContext emptyList()
            val json = JSONObject(libraryFile.readText())
            jsonToCategories(json.optJSONArray("categories"))
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Load last played ID
     */
    suspend fun loadLastPlayedId(profileName: String): String? = withContext(Dispatchers.IO) {
        try {
            val libraryFile = getLibraryFile(profileName)
            if (!libraryFile.exists()) return@withContext null
            val json = JSONObject(libraryFile.readText())
            json.optString("lastPlayedId").takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Load playback speed
     */
    suspend fun loadPlaybackSpeed(profileName: String): Float = withContext(Dispatchers.IO) {
        try {
            val libraryFile = getLibraryFile(profileName)
            if (!libraryFile.exists()) return@withContext 1.0f
            val json = JSONObject(libraryFile.readText())
            json.optDouble("playbackSpeed", 1.0).toFloat()
        } catch (e: Exception) {
            e.printStackTrace()
            1.0f
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Update a single field in the library JSON file
     */
    private suspend fun updateLibraryField(profileName: String, fieldName: String, value: JSONArray): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val libraryFile = getLibraryFile(profileName)
                val json = if (libraryFile.exists()) {
                    try {
                        JSONObject(libraryFile.readText())
                    } catch (_: Exception) {
                        // Corrupted JSON file — start fresh
                        JSONObject().apply { put("version", LIBRARY_VERSION) }
                    }
                } else {
                    JSONObject().apply {
                        put("version", LIBRARY_VERSION)
                    }
                }
                json.put(fieldName, value)
                json.put("lastModified", System.currentTimeMillis())
                // Atomic write: write to temp file then rename to prevent corruption
                val tempFile = java.io.File(libraryFile.parent, "${libraryFile.name}.tmp")
                tempFile.writeText(json.toString(2))
                tempFile.renameTo(libraryFile)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

    private suspend fun updateLibraryStringField(profileName: String, fieldName: String, value: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val libraryFile = getLibraryFile(profileName)
                val json = if (libraryFile.exists()) {
                    try {
                        JSONObject(libraryFile.readText())
                    } catch (_: Exception) {
                        JSONObject().apply { put("version", LIBRARY_VERSION) }
                    }
                } else {
                    JSONObject().apply {
                        put("version", LIBRARY_VERSION)
                    }
                }
                json.put(fieldName, value)
                json.put("lastModified", System.currentTimeMillis())
                val tempFile = java.io.File(libraryFile.parent, "${libraryFile.name}.tmp")
                tempFile.writeText(json.toString(2))
                if (!tempFile.renameTo(libraryFile)) {
                    tempFile.copyTo(libraryFile, overwrite = true)
                    tempFile.delete()
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

    private suspend fun updateLibraryDoubleField(profileName: String, fieldName: String, value: Double): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val libraryFile = getLibraryFile(profileName)
                val json = if (libraryFile.exists()) {
                    try {
                        JSONObject(libraryFile.readText())
                    } catch (_: Exception) {
                        JSONObject().apply { put("version", LIBRARY_VERSION) }
                    }
                } else {
                    JSONObject().apply {
                        put("version", LIBRARY_VERSION)
                    }
                }
                json.put(fieldName, value)
                json.put("lastModified", System.currentTimeMillis())
                val tempFile = java.io.File(libraryFile.parent, "${libraryFile.name}.tmp")
                tempFile.writeText(json.toString(2))
                if (!tempFile.renameTo(libraryFile)) {
                    tempFile.copyTo(libraryFile, overwrite = true)
                    tempFile.delete()
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

    // ==================== JSON Serialization ====================

    private fun audiobooksToJson(audiobooks: List<LibraryAudiobook>): JSONArray {
        val jsonArray = JSONArray()
        audiobooks.forEach { audiobook ->
            val jsonObject = JSONObject().apply {
                put("id", audiobook.id)
                put("uri", audiobook.uri.toString())
                put("title", audiobook.title)
                put("author", audiobook.author)
                put("narrator", audiobook.narrator ?: "")
                put("track", audiobook.track ?: JSONObject.NULL)
                put("album", audiobook.album ?: "")
                put("coverArtUri", audiobook.coverArtUri?.toString() ?: "")
                put("duration", audiobook.duration)
                put("lastPosition", audiobook.lastPosition)
                put("lastPlayed", audiobook.lastPlayed)
                put("dateAdded", audiobook.dateAdded)
                put("isCompleted", audiobook.isCompleted)
                put("isFavorite", audiobook.isFavorite)
                put("categoryId", audiobook.categoryId ?: "")
                put("seriesId", audiobook.seriesId ?: "")
                put("seriesOrder", audiobook.seriesOrder)
                put("fileType", audiobook.fileType)
            }
            jsonArray.put(jsonObject)
        }
        return jsonArray
    }

    private fun jsonToAudiobooks(jsonArray: JSONArray?): List<LibraryAudiobook> {
        if (jsonArray == null) return emptyList()
        val audiobooks = mutableListOf<LibraryAudiobook>()
        for (i in 0 until jsonArray.length()) {
            try {
                val jsonObject = jsonArray.getJSONObject(i)
                val audiobook = LibraryAudiobook(
                    id = jsonObject.getString("id"),
                    uri = Uri.parse(jsonObject.getString("uri")),
                    title = jsonObject.getString("title"),
                    author = jsonObject.optString("author", "Unknown Author"),
                    narrator = jsonObject.optString("narrator").takeIf { it.isNotEmpty() },
                    track = jsonObject.opt("track")?.let { if (it == JSONObject.NULL) null else (it as? Int) ?: jsonObject.optInt("track", -1).takeIf { t -> t >= 0 } },
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return audiobooks
    }

    private fun booksToJson(books: List<LibraryBook>): JSONArray {
        val jsonArray = JSONArray()
        books.forEach { book ->
            val jsonObject = JSONObject().apply {
                put("id", book.id)
                put("uri", book.uri.toString())
                put("title", book.title)
                put("author", book.author)
                put("narrator", book.narrator ?: "")
                put("coverArtUri", book.coverArtUri ?: "")
                put("totalPages", book.totalPages)
                put("currentPage", book.currentPage)
                put("lastRead", book.lastRead)
                put("dateAdded", book.dateAdded)
                put("isCompleted", book.isCompleted)
                put("isFavorite", book.isFavorite)
                put("categoryId", book.categoryId ?: "")
                put("seriesId", book.seriesId ?: "")
                put("seriesOrder", book.seriesOrder)
                put("fileType", book.fileType)
            }
            jsonArray.put(jsonObject)
        }
        return jsonArray
    }

    private fun jsonToBooks(jsonArray: JSONArray?): List<LibraryBook> {
        if (jsonArray == null) return emptyList()
        val books = mutableListOf<LibraryBook>()
        for (i in 0 until jsonArray.length()) {
            try {
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return books
    }

    private fun musicToJson(music: List<LibraryMusic>): JSONArray {
        val jsonArray = JSONArray()
        val seenUris = mutableSetOf<String>()
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
                put("track", track.track ?: JSONObject.NULL)
                put("coverArtUri", track.coverArtUri ?: "")
                put("duration", track.duration)
                put("lastPosition", track.lastPosition)
                put("lastPlayed", track.lastPlayed)
                put("dateAdded", track.dateAdded)
                put("isCompleted", track.isCompleted)
                put("isFavorite", track.isFavorite)
                put("categoryId", track.categoryId ?: "")
                put("seriesId", track.seriesId ?: "")
                put("seriesOrder", track.seriesOrder)
                put("fileType", track.fileType)
                put("timesListened", track.timesListened)
                put("contentType", track.contentType.name)
            }
            jsonArray.put(jsonObject)
        }
        return jsonArray
    }

    private fun jsonToMusic(jsonArray: JSONArray?): List<LibraryMusic> {
        if (jsonArray == null) return emptyList()
        val music = mutableListOf<LibraryMusic>()
        val seenUris = mutableSetOf<String>()
        for (i in 0 until jsonArray.length()) {
            try {
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
                    track = jsonObject.opt("track")?.let { if (it == JSONObject.NULL) null else (it as? Int) ?: jsonObject.optInt("track", -1).takeIf { t -> t >= 0 } },
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return music
    }

    private fun comicsToJson(comics: List<LibraryComic>): JSONArray {
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
                put("isFavorite", comic.isFavorite)
                put("categoryId", comic.categoryId ?: "")
                put("seriesId", comic.seriesId ?: "")
                put("seriesOrder", comic.seriesOrder)
                put("fileType", comic.fileType)
            }
            jsonArray.put(jsonObject)
        }
        return jsonArray
    }

    private fun jsonToComics(jsonArray: JSONArray?): List<LibraryComic> {
        if (jsonArray == null) return emptyList()
        val comics = mutableListOf<LibraryComic>()
        for (i in 0 until jsonArray.length()) {
            try {
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return comics
    }

    private fun moviesToJson(movies: List<LibraryMovie>): JSONArray {
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
                put("isFavorite", movie.isFavorite)
                put("categoryId", movie.categoryId ?: "")
                put("seriesId", movie.seriesId ?: "")
                put("seriesOrder", movie.seriesOrder)
                put("thumbnailUri", movie.thumbnailUri?.toString() ?: "")
                put("coverArtUri", movie.coverArtUri ?: "")
                put("fileType", movie.fileType)
            }
            jsonArray.put(jsonObject)
        }
        return jsonArray
    }

    private fun jsonToMovies(jsonArray: JSONArray?): List<LibraryMovie> {
        if (jsonArray == null) return emptyList()
        val movies = mutableListOf<LibraryMovie>()
        for (i in 0 until jsonArray.length()) {
            try {
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return movies
    }

    private fun seriesToJson(series: List<LibrarySeries>): JSONArray {
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
        return jsonArray
    }

    private fun jsonToSeries(jsonArray: JSONArray?): List<LibrarySeries> {
        if (jsonArray == null) return emptyList()
        val series = mutableListOf<LibrarySeries>()
        for (i in 0 until jsonArray.length()) {
            try {
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return series
    }

    private fun categoriesToJson(categories: List<Category>): JSONArray {
        val jsonArray = JSONArray()
        categories.forEach { category ->
            val jsonObject = JSONObject().apply {
                put("id", category.id)
                put("name", category.name)
                put("dateCreated", category.dateCreated)
            }
            jsonArray.put(jsonObject)
        }
        return jsonArray
    }

    private fun jsonToCategories(jsonArray: JSONArray?): List<Category> {
        if (jsonArray == null) return emptyList()
        val categories = mutableListOf<Category>()
        for (i in 0 until jsonArray.length()) {
            try {
                val jsonObject = jsonArray.getJSONObject(i)
                val category = Category(
                    id = jsonObject.getString("id"),
                    name = jsonObject.getString("name"),
                    dateCreated = jsonObject.optLong("dateCreated", System.currentTimeMillis())
                )
                categories.add(category)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return categories
    }

    // ==================== Export/Import for Backup ====================

    /**
     * Export all library data as a JSON object (for backup)
     */
    suspend fun exportLibraryData(profileName: String): JSONObject = withContext(Dispatchers.IO) {
        try {
            val libraryFile = getLibraryFile(profileName)
            if (libraryFile.exists()) {
                JSONObject(libraryFile.readText())
            } else {
                JSONObject()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            JSONObject()
        }
    }

    /**
     * Import library data from a JSON object (for restore)
     */
    suspend fun importLibraryData(profileName: String, data: JSONObject): Boolean = withContext(Dispatchers.IO) {
        try {
            val libraryFile = getLibraryFile(profileName)
            data.put("lastModified", System.currentTimeMillis())
            libraryFile.parentFile?.mkdirs()
            libraryFile.writeText(data.toString(2))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Data class to hold all library data
     */
    data class LibraryData(
        val audiobooks: List<LibraryAudiobook>,
        val books: List<LibraryBook>,
        val music: List<LibraryMusic>,
        val comics: List<LibraryComic>,
        val movies: List<LibraryMovie>,
        val series: List<LibrarySeries>,
        val categories: List<Category>,
        val lastPlayedId: String?,
        val playbackSpeed: Float
    )
}
