package com.librio.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.librio.data.repository.LibraryRepository
import com.librio.data.repository.SettingsRepository
import com.librio.model.Category
import com.librio.model.ContentType
import com.librio.model.LibraryAudiobook
import com.librio.model.LibraryBook
import com.librio.model.LibraryComic
import com.librio.model.LibraryMusic
import com.librio.model.LibrarySeries
import com.librio.model.LibraryState
import com.librio.model.LibraryMovie
import com.librio.model.SortOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * ViewModel for managing the audiobook library with persistence
 */
@Suppress("UNUSED_PARAMETER")
class LibraryViewModel : ViewModel() {

    companion object {
        // Dedicated Librio folder structure - only root and Profiles folder
        // Content folders are now per-profile inside Librio/Profiles/{ProfileName}/
        val LIBRIO_ROOT = File(Environment.getExternalStorageDirectory(), "Librio")
        val LIBRIO_PROFILES = File(LIBRIO_ROOT, "Profiles")

        // Cover art fallback filenames to look for in the same directory
        private val COVER_ART_FILENAMES = listOf(
            "cover.jpg", "cover.jpeg", "cover.png",
            "folder.jpg", "folder.jpeg", "folder.png",
            "album.jpg", "album.jpeg", "album.png",
            "front.jpg", "front.jpeg", "front.png"
        )

        /**
         * Extract cover art from a media file with robust fallback handling.
         * Uses multiple approaches to handle device-specific codec issues:
         * 1. Try embedded picture with RGB_565 config (most compatible)
         * 2. Try embedded picture with ARGB_8888 config
         * 3. Try getFrameAtTime for video files
         * 4. Look for cover.jpg/folder.jpg in the same directory
         */
        fun extractCoverArtRobust(
            context: Context,
            uri: Uri,
            file: File? = null,
            targetSize: Int = 512
        ): Bitmap? {
            var coverArt: Bitmap? = null
            var retriever: MediaMetadataRetriever? = null

            try {
                retriever = MediaMetadataRetriever()

                // Set data source - prefer file path if available for better compatibility
                if (file != null && file.exists()) {
                    retriever.setDataSource(file.absolutePath)
                } else {
                    retriever.setDataSource(context, uri)
                }

                // Try 1: Extract embedded picture with ARGB_8888 (better color/alpha support)
                val artBytes = retriever.embeddedPicture
                if (artBytes != null && artBytes.isNotEmpty()) {
                    coverArt = decodeBitmapSafely(artBytes, targetSize, Bitmap.Config.ARGB_8888)

                    // Try 2: If ARGB_8888 failed, try RGB_565 (uses less memory)
                    if (coverArt == null) {
                        coverArt = decodeBitmapSafely(artBytes, targetSize, Bitmap.Config.RGB_565)
                    }
                }

                // Try 3: For video files or if embedded picture failed, try getting a frame
                if (coverArt == null) {
                    try {
                        val frame = retriever.getFrameAtTime(1000000L) // 1 second in
                        if (frame != null) {
                            coverArt = scaleBitmapIfNeeded(frame, targetSize)
                        }
                    } catch (_: Exception) {
                        // Not a video file or frame extraction failed
                    }
                }
            } catch (e: Exception) {
                // MediaMetadataRetriever failed entirely
                e.printStackTrace()
            } finally {
                try {
                    retriever?.release()
                } catch (_: Exception) {}
            }

            // Try 4: Look for cover art file in the same directory
            if (coverArt == null && file != null) {
                coverArt = findCoverArtInDirectory(file.parentFile, targetSize)
            }

            return coverArt
        }

        /**
         * Decode bitmap bytes safely with proper error handling and memory management
         */
        private fun decodeBitmapSafely(
            bytes: ByteArray,
            targetSize: Int,
            config: Bitmap.Config
        ): Bitmap? {
            return try {
                // First pass: get dimensions only
                val boundsOptions = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

                // Calculate sample size
                val sampleSize = calculateInSampleSizeStatic(boundsOptions, targetSize, targetSize)

                // Second pass: decode with calculated sample size
                val decodeOptions = BitmapFactory.Options().apply {
                    inJustDecodeBounds = false
                    inSampleSize = sampleSize
                    inPreferredConfig = config
                    // Don't scale based on density
                    inScaled = false
                }

                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            } catch (e: OutOfMemoryError) {
                // Try with a larger sample size if we ran out of memory
                try {
                    val fallbackOptions = BitmapFactory.Options().apply {
                        inSampleSize = 4
                        inPreferredConfig = Bitmap.Config.RGB_565
                        inScaled = false
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, fallbackOptions)
                } catch (_: Exception) {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }

        /**
         * Scale bitmap if larger than target size
         */
        private fun scaleBitmapIfNeeded(bitmap: Bitmap, targetSize: Int): Bitmap {
            if (bitmap.width <= targetSize && bitmap.height <= targetSize) {
                return bitmap
            }
            val scale = targetSize.toFloat() / maxOf(bitmap.width, bitmap.height)
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            return try {
                Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            } catch (_: Exception) {
                bitmap
            }
        }

        /**
         * Look for cover art image files in the given directory
         */
        private fun findCoverArtInDirectory(directory: File?, targetSize: Int): Bitmap? {
            if (directory == null || !directory.exists() || !directory.isDirectory) {
                return null
            }

            for (filename in COVER_ART_FILENAMES) {
                val coverFile = File(directory, filename)
                if (coverFile.exists() && coverFile.isFile) {
                    try {
                        // Get dimensions first
                        val boundsOptions = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        BitmapFactory.decodeFile(coverFile.absolutePath, boundsOptions)

                        // Decode with sample size
                        val decodeOptions = BitmapFactory.Options().apply {
                            inSampleSize = calculateInSampleSizeStatic(boundsOptions, targetSize, targetSize)
                            inPreferredConfig = Bitmap.Config.RGB_565
                            inScaled = false
                        }
                        val bitmap = BitmapFactory.decodeFile(coverFile.absolutePath, decodeOptions)
                        if (bitmap != null) {
                            return bitmap
                        }
                    } catch (_: Exception) {
                        // Continue to next file
                    }
                }
            }
            return null
        }

        private fun calculateInSampleSizeStatic(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
            val (height, width) = options.run { outHeight to outWidth }
            var inSampleSize = 1
            if (height > reqHeight || width > reqWidth) {
                val halfHeight = height / 2
                val halfWidth = width / 2
                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize
        }
    }

    private var repository: LibraryRepository? = null
    private var settingsRepository: SettingsRepository? = null
    private var autoRefreshJob: Job? = null
    private var appContext: Context? = null
    private var isInitialLoadComplete = false // Flag to prevent race condition
    private var currentProfileName: String = "Default" // Current active profile for folder scanning
    private val musicScanMutex = Mutex()

    private val _libraryState = MutableStateFlow(LibraryState())
    val libraryState: StateFlow<LibraryState> = _libraryState.asStateFlow()

    private val _selectedAudiobook = MutableStateFlow<LibraryAudiobook?>(null)
    val selectedAudiobook: StateFlow<LibraryAudiobook?> = _selectedAudiobook.asStateFlow()

    private val _selectedBook = MutableStateFlow<LibraryBook?>(null)
    val selectedBook: StateFlow<LibraryBook?> = _selectedBook.asStateFlow()

    private val _selectedMusic = MutableStateFlow<LibraryMusic?>(null)
    val selectedMusic: StateFlow<LibraryMusic?> = _selectedMusic.asStateFlow()

    private val _selectedComic = MutableStateFlow<LibraryComic?>(null)
    val selectedComic: StateFlow<LibraryComic?> = _selectedComic.asStateFlow()

    private val _selectedMovie = MutableStateFlow<LibraryMovie?>(null)
    val selectedMovie: StateFlow<LibraryMovie?> = _selectedMovie.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Per-content-type sort options
    private val _audiobookSortOption = MutableStateFlow(SortOption.RECENTLY_ADDED)
    val audiobookSortOption: StateFlow<SortOption> = _audiobookSortOption.asStateFlow()

    private val _bookSortOption = MutableStateFlow(SortOption.RECENTLY_ADDED)
    val bookSortOption: StateFlow<SortOption> = _bookSortOption.asStateFlow()

    private val _musicSortOption = MutableStateFlow(SortOption.RECENTLY_ADDED)
    val musicSortOption: StateFlow<SortOption> = _musicSortOption.asStateFlow()

    private val _comicSortOption = MutableStateFlow(SortOption.RECENTLY_ADDED)
    val comicSortOption: StateFlow<SortOption> = _comicSortOption.asStateFlow()

    private val _movieSortOption = MutableStateFlow(SortOption.RECENTLY_ADDED)
    val movieSortOption: StateFlow<SortOption> = _movieSortOption.asStateFlow()

    private val _isSearchVisible = MutableStateFlow(false)
    val isSearchVisible: StateFlow<Boolean> = _isSearchVisible.asStateFlow()

    /**
     * Initialize the repository and load saved library, then auto-scan for new audiobooks
     */
    fun initialize(context: Context) {
        if (repository == null) {
            settingsRepository = SettingsRepository(context)

            // Get the active profile name from settings BEFORE creating the library repository
            val activeProfile = settingsRepository?.profiles?.value?.find { it.isActive }
            currentProfileName = activeProfile?.name ?: "Default"

            repository = LibraryRepository(context)
            // Sync the repository with the active profile
            repository?.setCurrentProfile(currentProfileName)

            appContext = context.applicationContext
            isInitialLoadComplete = false // Reset flag for fresh initialization

            // Create Librio folder structure if it doesn't exist
            createLibrioFolders()

            // Ensure default profile folder exists
            settingsRepository?.ensureDefaultProfileFolder()

            loadLibraryAndScan(context)
            startAutoRefresh()
        } else {
            // Repository already exists, but we may need to reload if called again
            // This handles cases where user returns to app after profile change
            appContext?.let { ctx ->
                viewModelScope.launch {
                    loadLibraryAndScan(ctx)
                }
            }
        }
    }

    /**
     * Set the active profile for content scanning
     * This should be called when the user switches profiles
     */
    fun setActiveProfile(profileName: String) {
        // Save current profile's library before switching
        saveLibrary()

        currentProfileName = profileName
        // Update repository to use new profile for storage
        repository?.setCurrentProfile(profileName)

        // Clear current library state and reload for new profile
        _libraryState.value = LibraryState()

        // Load and scan for new profile's content
        appContext?.let { context ->
            viewModelScope.launch {
                loadLibraryAndScan(context)
            }
        }
    }

    /**
     * Get profile-specific content folder
     */
    private fun getProfileContentFolder(contentType: String): File? {
        return settingsRepository?.getProfileContentFolder(currentProfileName, contentType)
    }

    /**
     * Get the playlist folder name from a file if it's in a playlist subfolder
     * Returns null if the file is in the root content folder
     */
    private fun getPlaylistFolderName(file: File, contentType: String): String? {
        val contentFolder = getProfileContentFolder(contentType) ?: return null
        val contentPath = contentFolder.absolutePath
        val filePath = file.absolutePath

        // Check if file is within the content folder
        if (!filePath.startsWith(contentPath)) return null

        // Get relative path from content folder
        val relativePath = filePath.removePrefix(contentPath).removePrefix(File.separator)

        // If there's a folder separator, the first part is the playlist folder
        val separatorIndex = relativePath.indexOf(File.separator)
        return if (separatorIndex > 0) {
            relativePath.substring(0, separatorIndex)
        } else {
            null // File is in root, not in a playlist folder
        }
    }

    /**
     * Find or create a series for a playlist folder
     * Returns the series ID or null if not in a playlist folder
     */
    private fun findOrCreateSeriesForPlaylist(playlistName: String, contentType: ContentType): String? {
        if (playlistName.isBlank()) return null

        // Look for existing series with this name and content type
        val existingSeries = _libraryState.value.series.find {
            it.name.equals(playlistName, ignoreCase = true) && it.contentType == contentType
        }

        if (existingSeries != null) {
            return existingSeries.id
        }

        // Create new series for this playlist folder
        val maxOrder = _libraryState.value.series
            .asSequence()
            .filter { it.contentType == contentType }
            .maxOfOrNull { it.order } ?: -1

        val newSeries = LibrarySeries(
            id = UUID.randomUUID().toString(),
            name = playlistName,
            contentType = contentType,
            order = maxOrder + 1
        )

        val currentSeries = _libraryState.value.series.toMutableList()
        currentSeries.add(newSeries)
        _libraryState.value = _libraryState.value.copy(series = currentSeries)
        saveSeries()

        return newSeries.id
    }

    /**
     * Create the Librio folder structure
     * Only creates root and Profiles folder - content folders are per-profile
     */
    private fun createLibrioFolders() {
        try {
            listOf(LIBRIO_ROOT, LIBRIO_PROFILES).forEach { folder ->
                if (!folder.exists()) {
                    folder.mkdirs()
                }
            }
            // Ensure content folders exist for the current profile
            viewModelScope.launch {
                repository?.ensureContentFoldersExist()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Start auto-refresh job that periodically scans for new files
     * Waits for initial library load to complete to prevent race conditions
     */
    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            // Wait for initial library load to complete before scanning
            // This prevents overwriting saved progress with new items
            while (!isInitialLoadComplete) {
                delay(500)
            }
            // Additional delay after load completes
            delay(2_000)
            while (isActive) {
                appContext?.let { context ->
                    // Clean up deleted files first
                    cleanupDeletedFiles(context)
                    // Then scan for new files
                    scanAudiobooksFolderSilent(context)
                    scanBooksFolderSilent(context)
                    scanForMusicFiles(context)
                    scanForCreepypastaFiles(context)
                    scanForComicFiles(context)
                    scanForMovieFiles(context)
                }
                delay(5_000) // Scan every 5 seconds
            }
        }
    }

    /**
     * Stop auto-refresh job
     */
    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    /**
     * Load library from storage, then auto-scan for new audiobooks
     */
    private fun loadLibraryAndScan(context: Context) {
        viewModelScope.launch {
            _libraryState.value = _libraryState.value.copy(isLoading = true)
            try {
                val repo = repository
                if (repo != null) {
                    // Load all library data in parallel on IO dispatcher
                    val results = withContext(Dispatchers.IO) {
                        // Run all loads concurrently using launch and collecting results
                        var audiobooks: List<LibraryAudiobook> = emptyList()
                        var categories: List<Category> = emptyList()
                        var books: List<LibraryBook> = emptyList()
                        var music: List<LibraryMusic> = emptyList()
                        var comics: List<LibraryComic> = emptyList()
                        var movies: List<LibraryMovie> = emptyList()
                        var series: List<LibrarySeries> = emptyList()

                        kotlinx.coroutines.coroutineScope {
                            launch { audiobooks = repo.loadLibrary() }
                            launch { categories = repo.loadCategories() }
                            launch { books = repo.loadBooks() }
                            launch { music = repo.loadMusic() }
                            launch { comics = repo.loadComics() }
                            launch { movies = repo.loadMovies() }
                            launch { series = repo.loadSeries() }
                        }

                        // Return all results as a list for type safety
                        listOf(audiobooks, categories, books, music, comics, movies, series)
                    }

                    @Suppress("UNCHECKED_CAST")
                    val savedAudiobooks = results[0] as List<LibraryAudiobook>
                    @Suppress("UNCHECKED_CAST")
                    val savedCategories = results[1] as List<Category>
                    @Suppress("UNCHECKED_CAST")
                    val savedBooks = results[2] as List<LibraryBook>
                    @Suppress("UNCHECKED_CAST")
                    val savedMusic = results[3] as List<LibraryMusic>
                    @Suppress("UNCHECKED_CAST")
                    val savedComics = results[4] as List<LibraryComic>
                    @Suppress("UNCHECKED_CAST")
                    val savedMovies = results[5] as List<LibraryMovie>
                    @Suppress("UNCHECKED_CAST")
                    var savedSeries = results[6] as List<LibrarySeries>

                    // Sync playlist folders with series - discover new folders and create series entries
                    val originalSeriesCount = savedSeries.size
                    savedSeries = repo.syncPlaylistFoldersWithSeries(savedSeries)

                    // Save series if new ones were discovered from folder sync
                    if (savedSeries.size > originalSeriesCount) {
                        repo.saveSeries(savedSeries)
                    }

                    // Load progress from progress.json file and merge with loaded data
                    val progressMap = withContext(Dispatchers.IO) {
                        repo.loadProgressFromFile()
                    }

                    // Apply progress from JSON file to items (for recovery/sync purposes)
                    val mergedAudiobooks = if (progressMap.isNotEmpty()) {
                        savedAudiobooks.map { audiobook ->
                            val progress = progressMap[audiobook.uri.toString()]
                            if (progress != null && progress.position > audiobook.lastPosition) {
                                audiobook.copy(
                                    lastPosition = progress.position,
                                    duration = if (progress.total > 0) progress.total else audiobook.duration,
                                    lastPlayed = progress.lastUpdated
                                )
                            } else audiobook
                        }
                    } else savedAudiobooks

                    val mergedBooks = if (progressMap.isNotEmpty()) {
                        savedBooks.map { book ->
                            val progress = progressMap[book.uri.toString()]
                            if (progress != null && progress.position > book.currentPage) {
                                book.copy(
                                    currentPage = progress.position.toInt(),
                                    totalPages = if (progress.total > 0) progress.total.toInt() else book.totalPages,
                                    lastRead = progress.lastUpdated
                                )
                            } else book
                        }
                    } else savedBooks

                    val mergedMusic = if (progressMap.isNotEmpty()) {
                        savedMusic.map { track ->
                            val progress = progressMap[track.uri.toString()]
                            if (progress != null && progress.position > track.lastPosition) {
                                track.copy(
                                    lastPosition = progress.position,
                                    duration = if (progress.total > 0) progress.total else track.duration,
                                    lastPlayed = progress.lastUpdated
                                )
                            } else track
                        }
                    } else savedMusic

                    val mergedComics = if (progressMap.isNotEmpty()) {
                        savedComics.map { comic ->
                            val progress = progressMap[comic.uri.toString()]
                            if (progress != null && progress.position > comic.currentPage) {
                                comic.copy(
                                    currentPage = progress.position.toInt(),
                                    totalPages = if (progress.total > 0) progress.total.toInt() else comic.totalPages,
                                    lastRead = progress.lastUpdated
                                )
                            } else comic
                        }
                    } else savedComics

                    val mergedMovies = if (progressMap.isNotEmpty()) {
                        savedMovies.map { movie ->
                            val progress = progressMap[movie.uri.toString()]
                            if (progress != null && progress.position > movie.lastPosition) {
                                movie.copy(
                                    lastPosition = progress.position,
                                    duration = if (progress.total > 0) progress.total else movie.duration,
                                    lastPlayed = progress.lastUpdated
                                )
                            } else movie
                        }
                    } else savedMovies

                    // Show library immediately without cover art (fast initial load)
                    _libraryState.value = _libraryState.value.copy(
                        audiobooks = mergedAudiobooks,
                        categories = savedCategories,
                        books = mergedBooks,
                        music = mergedMusic,
                        comics = mergedComics,
                        movies = mergedMovies,
                        series = savedSeries,
                        isLoading = false
                    )

                    // Mark initial load as complete - CRITICAL: this must happen BEFORE auto-refresh starts scanning
                    isInitialLoadComplete = true

                    // Clean up files that no longer exist on disk
                    launch { cleanupDeletedFiles(context) }

                    // Then load cover art in background (non-blocking) for all content types
                    // Use synchronized updates to prevent race conditions
                    launch(Dispatchers.IO) {
                        // Process all content types in parallel for faster loading
                        val audiobooksDeferred = mergedAudiobooks.map { audiobook ->
                            async { reloadCoverArt(context, audiobook) }
                        }
                        val booksDeferred = mergedBooks.map { book ->
                            async { reloadBookCoverArt(context, book) }
                        }
                        val musicDeferred = mergedMusic.map { music ->
                            async { reloadMusicCoverArt(context, music) }
                        }
                        val comicsDeferred = mergedComics.map { comic ->
                            async { reloadComicCoverArt(context, comic) }
                        }
                        val moviesDeferred = mergedMovies.map { movie ->
                            async { reloadMovieCoverArt(context, movie) }
                        }

                        // Wait for all parallel operations to complete
                        val audiobooksWithCovers = audiobooksDeferred.awaitAll()
                        val booksWithCovers = booksDeferred.awaitAll()
                        val musicWithCovers = musicDeferred.awaitAll()
                        val comicsWithCovers = comicsDeferred.awaitAll()
                        val moviesWithCovers = moviesDeferred.awaitAll()

                        // Update all at once to avoid race conditions
                        withContext(Dispatchers.Main) {
                            _libraryState.value = _libraryState.value.copy(
                                audiobooks = audiobooksWithCovers,
                                books = booksWithCovers,
                                music = musicWithCovers,
                                comics = comicsWithCovers,
                                movies = moviesWithCovers
                            )
                        }
                    }

                    // Auto-scan for new content in parallel (non-blocking)
                    launch { scanAudiobooksFolderSilent(context) }
                    launch { scanBooksFolderSilent(context) }
                    launch { scanForMusicFiles(context) }
                    launch { scanForCreepypastaFiles(context) }
                    launch { scanForComicFiles(context) }
                    launch { scanForMovieFiles(context) }
                } else {
                    _libraryState.value = _libraryState.value.copy(isLoading = false)
                    isInitialLoadComplete = true
                }
            } catch (e: Exception) {
                _libraryState.value = _libraryState.value.copy(
                    isLoading = false,
                    error = "Failed to load library: ${e.message}"
                )
                isInitialLoadComplete = true
            }
        }
    }

    /**
     * Clean up library items whose files no longer exist on disk
     * Called during library load to remove stale entries
     */
    private suspend fun cleanupDeletedFiles(context: Context) {
        withContext(Dispatchers.IO) {
            val currentState = _libraryState.value
            var hasChanges = false

            // Check audiobooks
            val validAudiobooks = currentState.audiobooks.filter { item ->
                fileExists(context, item.uri)
            }
            if (validAudiobooks.size != currentState.audiobooks.size) {
                hasChanges = true
            }

            // Check books
            val validBooks = currentState.books.filter { item ->
                fileExists(context, item.uri)
            }
            if (validBooks.size != currentState.books.size) {
                hasChanges = true
            }

            // Check music
            val validMusic = currentState.music.filter { item ->
                fileExists(context, item.uri)
            }
            if (validMusic.size != currentState.music.size) {
                hasChanges = true
            }

            // Check comics
            val validComics = currentState.comics.filter { item ->
                fileExists(context, item.uri)
            }
            if (validComics.size != currentState.comics.size) {
                hasChanges = true
            }

            // Check movies
            val validMovies = currentState.movies.filter { item ->
                fileExists(context, item.uri)
            }
            if (validMovies.size != currentState.movies.size) {
                hasChanges = true
            }

            // Update state if any items were removed
            if (hasChanges) {
                withContext(Dispatchers.Main) {
                    _libraryState.value = _libraryState.value.copy(
                        audiobooks = validAudiobooks,
                        books = validBooks,
                        music = validMusic,
                        comics = validComics,
                        movies = validMovies
                    )
                }
                // Save the cleaned up library
                repository?.saveLibrary(validAudiobooks)
                repository?.saveBooks(validBooks)
                repository?.saveMusic(validMusic)
                repository?.saveComics(validComics)
                repository?.saveMovies(validMovies)
            }
        }
    }

    /**
     * Check if a file exists for a given URI
     */
    private fun fileExists(context: Context, uri: Uri): Boolean {
        return try {
            // Try file:// URI first (most common for local files)
            val path = uri.path
            if (path != null) {
                val file = File(path)
                if (file.exists()) return true
            }

            // Try content:// URI
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Silent scan that doesn't show loading state or error messages for "no new audiobooks"
     */
    private fun scanAudiobooksFolderSilent(context: Context) {
        viewModelScope.launch {
            try {
                val audioFiles = withContext(Dispatchers.IO) {
                    findAudioFilesInAudiobooksFolder()
                }

                if (audioFiles.isEmpty()) return@launch

                val currentList = _libraryState.value.audiobooks.toMutableList()
                // Use both URI and filename for duplicate detection to prevent duplicates
                // when same file is accessed via different URI schemes (content:// vs file://)
                val existingUris = currentList.map { it.uri.toString() }.toSet()
                val existingFilenames = currentList.mapNotNull { audiobook ->
                    audiobook.uri.lastPathSegment?.substringAfterLast("/")?.lowercase()
                }.toSet()

                var addedCount = 0

                for (file in audioFiles) {
                    val uri = file.toUri()
                    val filename = file.name.lowercase()
                    // Check if this exact URI OR filename is already in the library
                    if (uri.toString() !in existingUris && filename !in existingFilenames) {
                        try {
                            // Detect playlist folder and assign series
                            val playlistName = getPlaylistFolderName(file, "Audiobooks")
                            val seriesId = if (playlistName != null) {
                                findOrCreateSeriesForPlaylist(playlistName, ContentType.AUDIOBOOK)
                            } else null

                            val audiobook = withContext(Dispatchers.IO) {
                                extractAudiobookMetadataFromFile(context, file)
                            }.copy(seriesId = seriesId)

                            currentList.add(0, audiobook)
                            addedCount++
                        } catch (e: Exception) {
                            // Skip files that can't be read
                        }
                    }
                }

                if (addedCount > 0) {
                    _libraryState.value = _libraryState.value.copy(audiobooks = currentList)
                    saveLibrary()
                    // Save series if any were created
                    saveSeries()
                }
            } catch (e: Exception) {
                // Silently fail - don't show error for background scan
            }
        }
    }


    /**
     * Reload cover art for an audiobook from its URI
     * Uses robust extraction with fallback handling for device compatibility
     */
    private fun reloadCoverArt(context: Context, audiobook: LibraryAudiobook): LibraryAudiobook {
        // First check if there's a custom cover art URI
        val coverArt = if (audiobook.coverArtUri != null) {
            loadCustomCoverArt(audiobook.coverArtUri.toString(), 512)
        } else {
            // Otherwise, try to get file from URI for better compatibility and fallback options
            // For file:// URIs, we can extract the path; for content:// URIs, pass null
            val file = try {
                if (audiobook.uri.scheme == "file") {
                    audiobook.uri.path?.let { File(it) }?.takeIf { it.exists() }
                } else {
                    null
                }
            } catch (_: Exception) { null }

            extractCoverArtRobust(context, audiobook.uri, file, 512)
        }
        return audiobook.copy(coverArt = coverArt)
    }

    /**
     * Reload cover art for a music track from its URI
     * Uses robust extraction with fallback handling for device compatibility
     */
    private fun reloadMusicCoverArt(context: Context, music: LibraryMusic): LibraryMusic {
        // First check if there's a custom cover art URI
        val coverArt = if (music.coverArtUri != null) {
            loadCustomCoverArt(music.coverArtUri, 512)
        } else {
            // Otherwise, try to get file from URI for better compatibility and fallback options
            val file = try {
                if (music.uri.scheme == "file") {
                    music.uri.path?.let { File(it) }?.takeIf { it.exists() }
                } else {
                    null
                }
            } catch (_: Exception) { null }

            extractCoverArtRobust(context, music.uri, file, 512)
        }
        return music.copy(coverArt = coverArt)
    }

    /**
     * Reload cover art for a comic from its archive
     * Extracts the first image from the comic archive (CBZ/CBR/etc)
     */
    private fun reloadComicCoverArt(context: Context, comic: LibraryComic): LibraryComic {
        // First check if there's a custom cover art URI
        val coverArt = if (comic.coverArtUri != null) {
            loadCustomCoverArt(comic.coverArtUri, 512)
        } else {
            extractComicCoverArt(context, comic.uri, 512)
        }
        return comic.copy(coverArt = coverArt)
    }

    /**
     * Reload cover art for a book
     * Uses custom cover art if available, otherwise returns null (books don't have embedded art)
     */
    private fun reloadBookCoverArt(context: Context, book: LibraryBook): LibraryBook {
        // Books typically don't have embedded cover art, so check for custom URI
        val coverArt = if (book.coverArtUri != null) {
            loadCustomCoverArt(book.coverArtUri, 512)
        } else {
            null
        }
        return book.copy(coverArt = coverArt)
    }

    /**
     * Reload cover art for a movie
     * Uses custom cover art if available, otherwise extracts a frame from the video
     */
    private fun reloadMovieCoverArt(context: Context, movie: LibraryMovie): LibraryMovie {
        // First check if there's a custom cover art URI
        val coverArt = if (movie.coverArtUri != null) {
            loadCustomCoverArt(movie.coverArtUri, 512)
        } else {
            // Try to extract a frame from the video file
            try {
                val file = if (movie.uri.scheme == "file") {
                    movie.uri.path?.let { File(it) }?.takeIf { it.exists() }
                } else {
                    null
                }
                extractCoverArtRobust(context, movie.uri, file, 512)
            } catch (_: Exception) {
                null
            }
        }
        return movie.copy(coverArt = coverArt)
    }

    /**
     * Extract cover art from a comic archive (CBZ, CBR, etc)
     * CBZ files are ZIP archives, we extract the first image file as cover
     */
    private fun extractComicCoverArt(context: Context, uri: Uri, targetSize: Int): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val zipInputStream = java.util.zip.ZipInputStream(inputStream)
                var entry = zipInputStream.nextEntry

                // Image extensions to look for
                val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

                // Collect all image entries and sort them to get the first one
                val imageEntries = mutableListOf<String>()
                while (entry != null) {
                    val name = entry.name.lowercase()
                    val extension = name.substringAfterLast(".", "")
                    if (!entry.isDirectory && extension in imageExtensions) {
                        imageEntries.add(entry.name)
                    }
                    zipInputStream.closeEntry()
                    entry = zipInputStream.nextEntry
                }
                zipInputStream.close()

                // Sort and get the first image (typically cover)
                if (imageEntries.isNotEmpty()) {
                    val firstImage = imageEntries.sorted().first()

                    // Re-open to extract the first image
                    context.contentResolver.openInputStream(uri)?.use { is2 ->
                        val zis2 = java.util.zip.ZipInputStream(is2)
                        var e2 = zis2.nextEntry
                        while (e2 != null) {
                            if (e2.name == firstImage) {
                                val bytes = zis2.readBytes()
                                zis2.close()
                                return@use decodeBitmapSafelyStatic(bytes, targetSize, Bitmap.Config.RGB_565)
                            }
                            zis2.closeEntry()
                            e2 = zis2.nextEntry
                        }
                        zis2.close()
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Decode bitmap bytes safely (static version for use in extractComicCoverArt)
     */
    private fun decodeBitmapSafelyStatic(bytes: ByteArray, targetSize: Int, config: Bitmap.Config): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

            val sampleSize = calculateInSampleSize(boundsOptions, targetSize, targetSize)

            val decodeOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inSampleSize = sampleSize
                inPreferredConfig = config
                inScaled = false
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        } catch (e: OutOfMemoryError) {
            try {
                val fallbackOptions = BitmapFactory.Options().apply {
                    inSampleSize = 4
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inScaled = false
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, fallbackOptions)
            } catch (_: Exception) { null }
        } catch (_: Exception) { null }
    }

    /**
     * Save all library data to persistent storage (synchronous to ensure data is persisted)
     */
    fun saveLibrary() {
        kotlinx.coroutines.runBlocking {
            repository?.saveLibrary(_libraryState.value.audiobooks)
            repository?.saveBooks(_libraryState.value.books)
            repository?.saveMusic(_libraryState.value.music)
            repository?.saveComics(_libraryState.value.comics)
            repository?.saveMovies(_libraryState.value.movies)
            repository?.saveCategories(_libraryState.value.categories)
            repository?.saveSeries(_libraryState.value.series)
        }
    }

    /**
     * Add an audiobook to the library from a URI
     */
    fun addAudiobook(context: Context, uri: Uri) {
        viewModelScope.launch {
            _libraryState.value = _libraryState.value.copy(isLoading = true)

            try {
                // Take persistable permission for the URI
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Permission might not be persistable, continue anyway
                }

                val audiobook = withContext(Dispatchers.IO) {
                    extractAudiobookMetadata(context, uri)
                }

                val currentList = _libraryState.value.audiobooks.toMutableList()

                // Check if already exists
                if (currentList.none { it.uri == uri }) {
                    currentList.add(0, audiobook)
                    _libraryState.value = _libraryState.value.copy(
                        audiobooks = currentList,
                        isLoading = false
                    )
                    // Save after adding
                    saveLibrary()
                } else {
                    _libraryState.value = _libraryState.value.copy(
                        isLoading = false,
                        error = "This audiobook is already in your library"
                    )
                }
            } catch (e: Exception) {
                _libraryState.value = _libraryState.value.copy(
                    isLoading = false,
                    error = "Failed to add audiobook: ${e.message}"
                )
            }
        }
    }

    /**
     * Extract metadata from audio file
     * Uses robust cover art extraction with fallback handling
     */
    private fun extractAudiobookMetadata(context: Context, uri: Uri): LibraryAudiobook {
        val retriever = MediaMetadataRetriever()
        var title = "Unknown Title"
        var author = "Unknown Author"
        var narrator: String? = null
        var duration = 0L

        try {
            retriever.setDataSource(context, uri)

            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?: uri.lastPathSegment?.substringBeforeLast(".") ?: "Unknown Title"

            author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
                ?: "Unknown Author"

            narrator = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)

            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }

        // Extract cover art using robust method with fallback handling
        val coverArt = extractCoverArtRobust(context, uri, null, 512)

        // Extract file extension
        val fileExtension = uri.lastPathSegment?.substringAfterLast(".", "mp3")?.lowercase() ?: "mp3"

        return LibraryAudiobook(
            id = UUID.randomUUID().toString(),
            uri = uri,
            title = title,
            author = author,
            narrator = narrator,
            coverArt = coverArt,
            duration = duration,
            dateAdded = System.currentTimeMillis(),
            fileType = fileExtension
        )
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Decode a bitmap from a persisted URI string with safe sampling to avoid OOM
     */
    private fun loadCustomCoverArt(uriString: String?, targetSize: Int = 512): Bitmap? {
        if (uriString.isNullOrEmpty()) return null
        val context = appContext ?: return null
        return try {
            val uri = Uri.parse(uriString)
            // First pass: bounds only
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, boundsOptions)
            }
            val sampleSize = calculateInSampleSize(boundsOptions, targetSize, targetSize)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
                inScaled = false
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get filtered and sorted audiobooks
     */
    fun getFilteredAudiobooks(): List<LibraryAudiobook> {
        val query = _searchQuery.value.lowercase()
        val selectedCategoryId = _libraryState.value.selectedAudiobookCategoryId

        // First filter by category
        val categoryFiltered = if (selectedCategoryId == null) {
            _libraryState.value.audiobooks
        } else {
            _libraryState.value.audiobooks.filter { it.categoryId == selectedCategoryId }
        }

        // Then filter by search query
        val filtered = if (query.isEmpty()) {
            categoryFiltered
        } else {
            categoryFiltered.filter {
                it.title.lowercase().contains(query) ||
                it.author.lowercase().contains(query) ||
                (it.narrator?.lowercase()?.contains(query) == true)
            }
        }

        return when (_audiobookSortOption.value) {
            SortOption.RECENTLY_ADDED -> filtered.sortedByDescending { it.dateAdded }
            SortOption.RECENTLY_PLAYED -> filtered.sortedByDescending { it.lastPlayed }
            SortOption.TITLE_AZ -> filtered.sortedBy { it.title.lowercase() }
            SortOption.TITLE_ZA -> filtered.sortedByDescending { it.title.lowercase() }
            SortOption.AUTHOR_AZ -> filtered.sortedBy { it.author.lowercase() }
            SortOption.PROGRESS -> filtered.sortedByDescending { it.progress }
            SortOption.BOOK_NUMBER -> filtered.sortedBy { extractBookNumber(it.title) }
        }
    }

    /**
     * Extract book number from title for sorting
     * Matches patterns like "book #1", "#2", "Book 3", etc.
     */
    private fun extractBookNumber(title: String): Int {
        // Pattern to match "book #X", "#X", "book X" where X is a number
        val patterns = listOf(
            Regex("""book\s*#?\s*(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""#(\d+)"""),
            Regex("""(?:part|chapter|vol|volume)\s*#?\s*(\d+)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            val match = pattern.find(title)
            if (match != null) {
                return match.groupValues[1].toIntOrNull() ?: Int.MAX_VALUE
            }
        }

        // If no book number found, return max value so it sorts to the end
        return Int.MAX_VALUE
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOption(option: SortOption) {
        // Set sort option for the current content type
        when (_libraryState.value.selectedContentType) {
            ContentType.AUDIOBOOK -> _audiobookSortOption.value = option
            ContentType.EBOOK -> _bookSortOption.value = option
            ContentType.MUSIC -> _musicSortOption.value = option
            ContentType.CREEPYPASTA -> _musicSortOption.value = option
            ContentType.COMICS -> _comicSortOption.value = option
            ContentType.MOVIE -> _movieSortOption.value = option
        }
    }

    fun getCurrentSortOption(): SortOption {
        return when (_libraryState.value.selectedContentType) {
            ContentType.AUDIOBOOK -> _audiobookSortOption.value
            ContentType.EBOOK -> _bookSortOption.value
            ContentType.MUSIC -> _musicSortOption.value
            ContentType.CREEPYPASTA -> _musicSortOption.value
            ContentType.COMICS -> _comicSortOption.value
            ContentType.MOVIE -> _movieSortOption.value
        }
    }

    fun toggleSearch() {
        _isSearchVisible.value = !_isSearchVisible.value
        if (!_isSearchVisible.value) {
            _searchQuery.value = ""
        }
    }

    fun selectAudiobook(audiobook: LibraryAudiobook) {
        _selectedAudiobook.value = audiobook
        repository?.saveLastPlayedId(audiobook.id)
    }

    fun clearSelection() {
        _selectedAudiobook.value = null
    }

    fun updateProgress(audiobookId: String, position: Long, duration: Long) {
        val currentList = _libraryState.value.audiobooks.toMutableList()
        val index = currentList.indexOfFirst { it.id == audiobookId }

        if (index >= 0) {
            val audiobook = currentList[index]
            currentList[index] = audiobook.copy(
                lastPosition = position,
                duration = if (duration > 0) duration else audiobook.duration,
                lastPlayed = System.currentTimeMillis(),
                isCompleted = position >= duration - 1000
            )
            _libraryState.value = _libraryState.value.copy(audiobooks = currentList)

            // Auto-save progress periodically (every ~5 seconds)
            if (position % 5000 < 1000) {
                saveLibrary()
                // Also save to progress.json file for persistence
                viewModelScope.launch {
                    repository?.updateProgressInFile(
                        uri = audiobook.uri.toString(),
                        type = "AUDIOBOOK",
                        position = position,
                        total = if (duration > 0) duration else audiobook.duration
                    )
                }
            }
        }
    }

    fun removeAudiobook(audiobook: LibraryAudiobook, deleteFile: Boolean = true) {
        // Delete the file from device if requested
        if (deleteFile) {
            deleteFileFromUri(audiobook.uri)
        }

        val currentList = _libraryState.value.audiobooks.toMutableList()
        currentList.removeAll { it.id == audiobook.id }
        _libraryState.value = _libraryState.value.copy(audiobooks = currentList)

        if (_selectedAudiobook.value?.id == audiobook.id) {
            _selectedAudiobook.value = null
        }

        // Save after removing
        saveLibrary()
    }

    /**
     * Update audiobook metadata (title and author) manually
     */
    fun updateAudiobookMetadata(audiobookId: String, newTitle: String, newAuthor: String) {
        val currentList = _libraryState.value.audiobooks.toMutableList()
        val index = currentList.indexOfFirst { it.id == audiobookId }

        if (index >= 0) {
            val audiobook = currentList[index]
            currentList[index] = audiobook.copy(
                title = newTitle.ifBlank { audiobook.title },
                author = newAuthor.ifBlank { audiobook.author }
            )
            _libraryState.value = _libraryState.value.copy(audiobooks = currentList)
            saveLibrary()
        }
    }

    fun clearError() {
        _libraryState.value = _libraryState.value.copy(error = null)
    }

    // ==================== Category Management ====================

    /**
     * Add a new category
     */
    fun addCategory(name: String) {
        if (name.isBlank()) return

        val newCategory = Category(
            id = UUID.randomUUID().toString(),
            name = name.trim()
        )

        val currentCategories = _libraryState.value.categories.toMutableList()
        currentCategories.add(newCategory)
        _libraryState.value = _libraryState.value.copy(categories = currentCategories)
        saveCategories()
    }

    /**
     * Delete a category (audiobooks in it will become uncategorized)
     */
    fun deleteCategory(categoryId: String) {
        val currentCategories = _libraryState.value.categories.toMutableList()
        currentCategories.removeAll { it.id == categoryId }
        _libraryState.value = _libraryState.value.copy(categories = currentCategories)

        // Clear category from audiobooks that had this category
        val currentAudiobooks = _libraryState.value.audiobooks.map {
            if (it.categoryId == categoryId) it.copy(categoryId = null) else it
        }
        _libraryState.value = _libraryState.value.copy(audiobooks = currentAudiobooks)

        // Clear selection if this category was selected for any content type
        var newState = _libraryState.value
        if (newState.selectedAudiobookCategoryId == categoryId) {
            newState = newState.copy(selectedAudiobookCategoryId = null)
        }
        if (newState.selectedBookCategoryId == categoryId) {
            newState = newState.copy(selectedBookCategoryId = null)
        }
        if (newState.selectedMusicCategoryId == categoryId) {
            newState = newState.copy(selectedMusicCategoryId = null)
        }
        if (newState.selectedCreepypastaCategoryId == categoryId) {
            newState = newState.copy(selectedCreepypastaCategoryId = null)
        }
        if (newState.selectedComicCategoryId == categoryId) {
            newState = newState.copy(selectedComicCategoryId = null)
        }
        if (newState.selectedMovieCategoryId == categoryId) {
            newState = newState.copy(selectedMovieCategoryId = null)
        }
        _libraryState.value = newState

        saveLibrary()
        saveCategories()
    }

    /**
     * Rename a category
     */
    fun renameCategory(categoryId: String, newName: String) {
        if (newName.isBlank()) return

        val currentCategories = _libraryState.value.categories.map {
            if (it.id == categoryId) it.copy(name = newName.trim()) else it
        }
        _libraryState.value = _libraryState.value.copy(categories = currentCategories)
        saveCategories()
    }

    /**
     * Assign an audiobook to a category (null to remove from category)
     */
    fun setAudiobookCategory(audiobookId: String, categoryId: String?) {
        val currentAudiobooks = _libraryState.value.audiobooks.map {
            if (it.id == audiobookId) it.copy(categoryId = categoryId) else it
        }
        _libraryState.value = _libraryState.value.copy(audiobooks = currentAudiobooks)
        saveLibrary()
    }

    /**
     * Select a category for filtering (null shows all)
     * Each content type has its own category filter
     */
    fun selectCategory(categoryId: String?) {
        val currentState = _libraryState.value
        _libraryState.value = when (currentState.selectedContentType) {
            ContentType.AUDIOBOOK -> currentState.copy(selectedAudiobookCategoryId = categoryId)
            ContentType.EBOOK -> currentState.copy(selectedBookCategoryId = categoryId)
            ContentType.MUSIC -> currentState.copy(selectedMusicCategoryId = categoryId)
            ContentType.CREEPYPASTA -> currentState.copy(selectedCreepypastaCategoryId = categoryId)
            ContentType.COMICS -> currentState.copy(selectedComicCategoryId = categoryId)
            ContentType.MOVIE -> currentState.copy(selectedMovieCategoryId = categoryId)
        }
    }

    /**
     * Save categories to repository
     */
    private fun saveCategories() {
        viewModelScope.launch {
            repository?.saveCategories(_libraryState.value.categories)
        }
    }

    /**
     * Scan the Audiobooks folder for audio files and add them to the library
     * Avoids adding duplicates by checking existing URIs and filenames
     */
    fun scanAudiobooksFolder(context: Context) {
        viewModelScope.launch {
            _libraryState.value = _libraryState.value.copy(isLoading = true)

            try {
                val audioFiles = withContext(Dispatchers.IO) {
                    findAudioFilesInAudiobooksFolder()
                }

                if (audioFiles.isEmpty()) {
                    _libraryState.value = _libraryState.value.copy(
                        isLoading = false,
                        error = "No audiobooks found in the Audiobooks folder"
                    )
                    return@launch
                }

                val currentList = _libraryState.value.audiobooks.toMutableList()
                // Use both URI and filename for duplicate detection to prevent duplicates
                // when same file is accessed via different URI schemes (content:// vs file://)
                val existingUris = currentList.map { it.uri.toString() }.toSet()
                val existingFilenames = currentList.mapNotNull { audiobook ->
                    audiobook.uri.lastPathSegment?.substringAfterLast("/")?.lowercase()
                }.toSet()

                var addedCount = 0

                for (file in audioFiles) {
                    val uri = file.toUri()
                    val filename = file.name.lowercase()
                    // Check if this exact URI OR filename is already in the library
                    if (uri.toString() !in existingUris && filename !in existingFilenames) {
                        try {
                            // Detect playlist folder and assign series
                            val playlistName = getPlaylistFolderName(file, "Audiobooks")
                            val seriesId = if (playlistName != null) {
                                findOrCreateSeriesForPlaylist(playlistName, ContentType.AUDIOBOOK)
                            } else null

                            val audiobook = withContext(Dispatchers.IO) {
                                extractAudiobookMetadataFromFile(context, file)
                            }.copy(seriesId = seriesId)

                            currentList.add(0, audiobook)
                            addedCount++
                        } catch (e: Exception) {
                            // Skip files that can't be read
                        }
                    }
                }

                _libraryState.value = _libraryState.value.copy(
                    audiobooks = currentList,
                    isLoading = false,
                    error = if (addedCount > 0) "Added $addedCount audiobook(s) to library" else "No new audiobooks found"
                )

                if (addedCount > 0) {
                    saveLibrary()
                    // Save series if any were created
                    saveSeries()
                }
            } catch (e: Exception) {
                _libraryState.value = _libraryState.value.copy(
                    isLoading = false,
                    error = "Failed to scan folder: ${e.message}"
                )
            }
        }
    }

    /**
     * Find all audio files in the profile-specific Audiobooks folder only
     * Each profile only sees its own content for complete content isolation
     */
    private fun findAudioFilesInAudiobooksFolder(): List<File> {
        // Comprehensive audiobook format support
        val audioExtensions = setOf(
            // Common audio formats
            "mp3", "m4a", "m4b", "aac", "ogg", "flac", "wav", "wma", "opus",
            // Audible formats
            "aax", "aa", "aaxc",
            // Other formats
            "oga", "mka", "webm", "3gp", "amr", "ape", "mpc", "shn",
            // Lossless
            "alac", "aiff", "aif", "dsd", "dsf", "dff",
            // Spoken word / podcast
            "spx", "ra", "rm"
        )
        val audioFiles = mutableListOf<File>()

        // Scan the profile-specific Audiobooks folder only (complete content isolation)
        getProfileContentFolder("Audiobooks")?.let { profileFolder ->
            if (profileFolder.exists() && profileFolder.isDirectory) {
                scanFolderRecursively(profileFolder, audioExtensions, audioFiles)
            }
        }

        return audioFiles.distinctBy { it.absolutePath }
    }

    /**
     * Recursively scan a folder for audio files
     */
    private fun scanFolderRecursively(folder: File, extensions: Set<String>, results: MutableList<File>) {
        folder.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                scanFolderRecursively(file, extensions, results)
            } else {
                val extension = file.extension.lowercase()
                if (extension in extensions) {
                    results.add(file)
                }
            }
        }
    }

    /**
     * Extract metadata from a File (instead of URI)
     * Uses robust cover art extraction with fallback handling
     */
    private fun extractAudiobookMetadataFromFile(context: Context, file: File): LibraryAudiobook {
        val retriever = MediaMetadataRetriever()
        var title = "Unknown Title"
        var author = "Unknown Author"
        var narrator: String? = null
        var duration = 0L

        try {
            retriever.setDataSource(file.absolutePath)

            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?: file.nameWithoutExtension

            author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
                ?: "Unknown Author"

            narrator = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)

            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }

        // Extract cover art using robust method with fallback handling (including folder art lookup)
        val coverArt = extractCoverArtRobust(context, file.toUri(), file, 512)

        return LibraryAudiobook(
            id = UUID.randomUUID().toString(),
            uri = file.toUri(),
            title = title,
            author = author,
            narrator = narrator,
            coverArt = coverArt,
            duration = duration,
            dateAdded = System.currentTimeMillis(),
            fileType = file.extension.lowercase().ifEmpty { "mp3" }
        )
    }

    override fun onCleared() {
        super.onCleared()
        // Stop auto-refresh and save all library data when ViewModel is cleared
        stopAutoRefresh()
        // Use runBlocking to ensure saves complete before ViewModel is destroyed
        // viewModelScope gets cancelled when ViewModel is cleared, so we can't use it here
        kotlinx.coroutines.runBlocking {
            repository?.saveLibrary(_libraryState.value.audiobooks)
            repository?.saveBooks(_libraryState.value.books)
            repository?.saveMusic(_libraryState.value.music)
            repository?.saveComics(_libraryState.value.comics)
            repository?.saveMovies(_libraryState.value.movies)
            repository?.saveCategories(_libraryState.value.categories)
            repository?.saveSeries(_libraryState.value.series)
        }
    }

    // ==================== E-Book Management ====================

    /**
     * Switch between audiobook and ebook content type
     */
    fun setContentType(type: ContentType) {
        _libraryState.value = _libraryState.value.copy(selectedContentType = type)
    }

    /**
     * Add an e-book to the library from a URI
     */
    fun addBook(context: Context, uri: Uri) {
        viewModelScope.launch {
            _libraryState.value = _libraryState.value.copy(isLoading = true)

            try {
                // Take persistable permission for the URI
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Permission might not be persistable, continue anyway
                }

                val book = withContext(Dispatchers.IO) {
                    extractBookMetadata(context, uri)
                }

                val currentBooks = _libraryState.value.books.toMutableList()

                // Check if already exists by URI or filename to prevent duplicates
                val newFilename = uri.lastPathSegment?.substringAfterLast("/")?.lowercase()
                val existsByUri = currentBooks.any { it.uri == uri }
                val existsByFilename = newFilename != null && currentBooks.any { existingBook ->
                    existingBook.uri.lastPathSegment?.substringAfterLast("/")?.lowercase() == newFilename
                }

                if (!existsByUri && !existsByFilename) {
                    currentBooks.add(0, book)
                    _libraryState.value = _libraryState.value.copy(
                        books = currentBooks,
                        isLoading = false
                    )
                    // Save after adding
                    saveBooks()
                } else {
                    _libraryState.value = _libraryState.value.copy(
                        isLoading = false,
                        error = "This book is already in your library"
                    )
                }
            } catch (e: Exception) {
                _libraryState.value = _libraryState.value.copy(
                    isLoading = false,
                    error = "Failed to add book: ${e.message}"
                )
            }
        }
    }

    /**
     * Extract metadata from book file
     */
    private fun extractBookMetadata(context: Context, uri: Uri): LibraryBook {
        val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "Unknown"
        val extension = fileName.substringAfterLast(".", "txt").lowercase()
        val title = fileName.substringBeforeLast(".")

        return LibraryBook(
            id = UUID.randomUUID().toString(),
            uri = uri,
            title = title,
            author = "Unknown Author",
            totalPages = 1,
            dateAdded = System.currentTimeMillis(),
            fileType = extension
        )
    }

    /**
     * Select a book for reading
     */
    fun selectBook(book: LibraryBook) {
        // Get the book from library state to ensure we have the latest saved progress
        val bookFromState = _libraryState.value.books.find { it.id == book.id } ?: book
        _selectedBook.value = bookFromState
        // Update last read timestamp
        val currentBooks = _libraryState.value.books.map {
            if (it.id == book.id) it.copy(lastRead = System.currentTimeMillis()) else it
        }
        _libraryState.value = _libraryState.value.copy(books = currentBooks)
        saveBooks()
    }

    /**
     * Update book reading progress
     */
    fun updateBookProgress(bookId: String, currentPage: Int, totalPages: Int) {
        // Use at least 1 page to ensure progress is saved
        // Text-based books may have dynamic page counts, but we should never block saving
        val effectiveTotalPages = totalPages.coerceAtLeast(1)
        val effectiveCurrentPage = currentPage.coerceIn(0, effectiveTotalPages - 1)

        // Get the book's URI for progress.json saving
        val book = _libraryState.value.books.find { it.id == bookId }

        val currentBooks = _libraryState.value.books.map {
            if (it.id == bookId) it.copy(
                currentPage = effectiveCurrentPage,
                totalPages = effectiveTotalPages,
                lastRead = System.currentTimeMillis(),
                isCompleted = effectiveCurrentPage >= effectiveTotalPages - 1
            ) else it
        }
        _libraryState.value = _libraryState.value.copy(books = currentBooks)

        // Also update selected book if it matches
        _selectedBook.value?.let { selected ->
            if (selected.id == bookId) {
                _selectedBook.value = selected.copy(
                    currentPage = effectiveCurrentPage,
                    totalPages = effectiveTotalPages,
                    lastRead = System.currentTimeMillis(),
                    isCompleted = effectiveCurrentPage >= effectiveTotalPages - 1
                )
            }
        }

        // Save books immediately with the updated list (pass directly to avoid state timing issues)
        saveBooksImmediately(currentBooks)

        // Also save to progress.json file for persistence
        book?.let {
            viewModelScope.launch {
                repository?.updateProgressInFile(
                    uri = it.uri.toString(),
                    type = "EBOOK",
                    position = effectiveCurrentPage.toLong(),
                    total = effectiveTotalPages.toLong()
                )
            }
        }
    }

    /**
     * Save books immediately with a specific list (to avoid state timing issues)
     */
    private fun saveBooksImmediately(books: List<LibraryBook>) {
        val repo = repository
        if (repo != null) {
            kotlinx.coroutines.runBlocking {
                repo.saveBooks(books)
            }
        } else {
            // Fallback: Save via state if repository isn't initialized yet
            _libraryState.value = _libraryState.value.copy(books = books)
        }
    }

    /**
     * Update book metadata (title and author) manually
     */
    fun updateBookMetadata(bookId: String, newTitle: String, newAuthor: String) {
        val currentBooks = _libraryState.value.books.toMutableList()
        val index = currentBooks.indexOfFirst { it.id == bookId }

        if (index >= 0) {
            val book = currentBooks[index]
            currentBooks[index] = book.copy(
                title = newTitle.ifBlank { book.title },
                author = newAuthor.ifBlank { book.author }
            )
            _libraryState.value = _libraryState.value.copy(books = currentBooks)
            // Persist immediately so edits survive app restarts
            saveBooksImmediately(currentBooks)
        }
    }

    /**
     * Remove a book from the library
     */
    fun removeBook(book: LibraryBook, deleteFile: Boolean = true) {
        // Delete the file from device if requested
        if (deleteFile) {
            deleteFileFromUri(book.uri)
        }

        val currentBooks = _libraryState.value.books.toMutableList()
        currentBooks.removeAll { it.id == book.id }
        _libraryState.value = _libraryState.value.copy(books = currentBooks)

        if (_selectedBook.value?.id == book.id) {
            _selectedBook.value = null
        }

        saveBooks()
    }

    /**
     * Get filtered and sorted books
     */
    fun getFilteredBooks(): List<LibraryBook> {
        val query = _searchQuery.value.lowercase()
        val selectedCategoryId = _libraryState.value.selectedBookCategoryId

        // First filter by category
        val categoryFiltered = if (selectedCategoryId == null) {
            _libraryState.value.books
        } else {
            _libraryState.value.books.filter { it.categoryId == selectedCategoryId }
        }

        // Then filter by search query
        val filtered = if (query.isEmpty()) {
            categoryFiltered
        } else {
            categoryFiltered.filter {
                it.title.lowercase().contains(query) ||
                it.author.lowercase().contains(query)
            }
        }

        return when (_bookSortOption.value) {
            SortOption.RECENTLY_ADDED -> filtered.sortedByDescending { it.dateAdded }
            SortOption.RECENTLY_PLAYED -> filtered.sortedByDescending { it.lastRead }
            SortOption.TITLE_AZ -> filtered.sortedBy { it.title.lowercase() }
            SortOption.TITLE_ZA -> filtered.sortedByDescending { it.title.lowercase() }
            SortOption.AUTHOR_AZ -> filtered.sortedBy { it.author.lowercase() }
            SortOption.PROGRESS -> filtered.sortedByDescending { it.progress }
            SortOption.BOOK_NUMBER -> filtered.sortedBy { extractBookNumber(it.title) }
        }
    }

    /**
     * Save books to repository (synchronous to ensure data is persisted)
     */
    private fun saveBooks() {
        kotlinx.coroutines.runBlocking {
            repository?.saveBooks(_libraryState.value.books)
        }
    }

    /**
     * Save music to repository (synchronous to ensure data is persisted)
     */
    private fun saveMusic() {
        kotlinx.coroutines.runBlocking {
            repository?.saveMusic(_libraryState.value.music)
        }
    }

    /**
     * Save music immediately with a specific list (to avoid state timing issues)
     */
    private fun saveMusicImmediately(music: List<LibraryMusic>) {
        kotlinx.coroutines.runBlocking {
            repository?.saveMusic(music)
        }
    }

    /**
     * Save comics to repository (synchronous to ensure data is persisted)
     */
    private fun saveComics() {
        kotlinx.coroutines.runBlocking {
            repository?.saveComics(_libraryState.value.comics)
        }
    }

    /**
     * Save comics immediately with a specific list (to avoid state timing issues)
     */
    private fun saveComicsImmediately(comics: List<LibraryComic>) {
        kotlinx.coroutines.runBlocking {
            repository?.saveComics(comics)
        }
    }

    /**
     * Save movies to repository (synchronous to ensure data is persisted)
     */
    private fun saveMovies() {
        kotlinx.coroutines.runBlocking {
            repository?.saveMovies(_libraryState.value.movies)
        }
    }

    /**
     * Save movies immediately with a specific list (to avoid state timing issues)
     */
    private fun saveMoviesImmediately(movies: List<LibraryMovie>) {
        kotlinx.coroutines.runBlocking {
            repository?.saveMovies(movies)
        }
    }

    /**
     * Scan for e-book files in common folders
     */
    fun scanBooksFolder(context: Context) {
        viewModelScope.launch {
            try {
                val bookFiles = withContext(Dispatchers.IO) {
                    findBookFilesInFolders()
                }

                if (bookFiles.isEmpty()) return@launch

                val currentBooks = _libraryState.value.books.toMutableList()
                // Use both URI and filename for duplicate detection to prevent duplicates
                // when same file is accessed via different URI schemes (content:// vs file://)
                val existingUris = currentBooks.map { it.uri.toString() }.toSet()
                val existingFilenames = currentBooks.mapNotNull { book ->
                    book.uri.lastPathSegment?.substringAfterLast("/")?.lowercase()
                }.toSet()

                var addedCount = 0

                for (file in bookFiles) {
                    val uri = file.toUri()
                    val filename = file.name.lowercase()
                    // Check if this exact URI OR filename is already in the library
                    if (uri.toString() !in existingUris && filename !in existingFilenames) {
                        try {
                            // Detect playlist folder and assign series
                            val playlistName = getPlaylistFolderName(file, "Books")
                            val seriesId = if (playlistName != null) {
                                findOrCreateSeriesForPlaylist(playlistName, ContentType.EBOOK)
                            } else null

                            val book = LibraryBook(
                                id = UUID.randomUUID().toString(),
                                uri = uri,
                                title = file.nameWithoutExtension,
                                author = "Unknown Author",
                                totalPages = 1, // Default to 1 page - will be updated when opened
                                fileType = file.extension.lowercase(),
                                dateAdded = System.currentTimeMillis(),
                                seriesId = seriesId
                            )
                            currentBooks.add(0, book)
                            addedCount++
                        } catch (e: Exception) {
                            // Skip files that can't be read
                        }
                    }
                }

                if (addedCount > 0) {
                    _libraryState.value = _libraryState.value.copy(books = currentBooks)
                    saveBooks()
                    // Save series if any were created
                    saveSeries()
                }
            } catch (e: Exception) {
                // Silently fail
            }
        }
    }

    /**
     * Silent scan for e-book files (used by auto-refresh)
     */
    private fun scanBooksFolderSilent(context: Context) {
        viewModelScope.launch {
            try {
                val bookFiles = withContext(Dispatchers.IO) {
                    findBookFilesInFolders()
                }

                if (bookFiles.isEmpty()) return@launch

                val currentBooks = _libraryState.value.books.toMutableList()
                // Use both URI and filename for duplicate detection to prevent duplicates
                // when same file is accessed via different URI schemes (content:// vs file://)
                val existingUris = currentBooks.map { it.uri.toString() }.toSet()
                val existingFilenames = currentBooks.mapNotNull { book ->
                    book.uri.lastPathSegment?.substringAfterLast("/")?.lowercase()
                }.toSet()

                var addedCount = 0

                for (file in bookFiles) {
                    val uri = file.toUri()
                    val filename = file.name.lowercase()
                    // Check if this exact URI OR filename is already in the library
                    if (uri.toString() !in existingUris && filename !in existingFilenames) {
                        try {
                            // Detect playlist folder and assign series
                            val playlistName = getPlaylistFolderName(file, "Books")
                            val seriesId = if (playlistName != null) {
                                findOrCreateSeriesForPlaylist(playlistName, ContentType.EBOOK)
                            } else null

                            val book = LibraryBook(
                                id = UUID.randomUUID().toString(),
                                uri = uri,
                                title = file.nameWithoutExtension,
                                author = "Unknown Author",
                                totalPages = 1, // Default to 1 page - will be updated when opened
                                fileType = file.extension.lowercase(),
                                dateAdded = System.currentTimeMillis(),
                                seriesId = seriesId
                            )
                            currentBooks.add(0, book)
                            addedCount++
                        } catch (e: Exception) {
                            // Skip files that can't be read
                        }
                    }
                }

                if (addedCount > 0) {
                    _libraryState.value = _libraryState.value.copy(books = currentBooks)
                    saveBooks()
                    // Save series if any were created
                    saveSeries()
                }
            } catch (e: Exception) {
                // Silently fail
            }
        }
    }

    /**
     * Find book files in the profile-specific Books folder only (excluding comics)
     * Each profile only sees its own content for complete content isolation
     */
    private fun findBookFilesInFolders(): List<File> {
        // Standard ebook format support (comics handled separately)
        val bookExtensions = setOf(
            // Standard ebook formats
            "epub", "mobi", "pdf", "fb2", "fb2zip",
            // Amazon Kindle formats
            "azw", "azw3", "azw4", "kfx", "prc",
            // Text formats
            "txt", "rtf", "doc", "docx", "odt",
            // Web formats
            "html", "htm", "xhtml", "mhtml",
            // Image-based documents
            "djvu", "djv",
            // Other formats
            "chm", "lit", "lrf", "pdb", "tcr", "opf"
        )
        val bookFiles = mutableListOf<File>()

        // Scan the profile-specific Books folder only (complete content isolation)
        getProfileContentFolder("Books")?.let { profileFolder ->
            if (profileFolder.exists() && profileFolder.isDirectory) {
                scanFolderRecursively(profileFolder, bookExtensions, bookFiles)
            }
        }

        return bookFiles.distinctBy { it.absolutePath }
    }

    /**
     * Find music files in the profile-specific Music folder only
     * Each profile only sees its own content for complete content isolation
     */
    private fun findMusicFilesInFolders(): List<File> {
        val musicExtensions = setOf(
            "mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "wma", "alac"
        )
        val musicFiles = mutableListOf<File>()

        // Scan the profile-specific Music folder only (complete content isolation)
        getProfileContentFolder("Music")?.let { profileFolder ->
            if (profileFolder.exists() && profileFolder.isDirectory) {
                scanFolderRecursively(profileFolder, musicExtensions, musicFiles)
            }
        }

        return musicFiles.distinctBy { it.absolutePath }
    }

    /**
     * Find creepypasta audio files in the profile-specific Creepypasta folder only
     */
    private fun findCreepypastaFilesInFolders(): List<File> {
        val audioExtensions = setOf(
            "mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "wma", "alac",
            "webm", "mp4", "mkv", "m4v"
        )
        val files = mutableListOf<File>()

        getProfileContentFolder("Creepypasta")?.let { profileFolder ->
            if (profileFolder.exists() && profileFolder.isDirectory) {
                scanFolderRecursively(profileFolder, audioExtensions, files)
            }
        }

        return files.distinctBy { it.absolutePath }
    }

    /**
     * Find comic files in the profile-specific Comics folder only
     * Each profile only sees its own content for complete content isolation
     */
    private fun findComicFilesInFolders(): List<File> {
        val comicExtensions = setOf(
            "cbz", "cbr", "cb7", "cbt", "pdf"
        )
        val comicFiles = mutableListOf<File>()

        // Scan the profile-specific Comics folder only (complete content isolation)
        getProfileContentFolder("Comics")?.let { profileFolder ->
            if (profileFolder.exists() && profileFolder.isDirectory) {
                scanFolderRecursively(profileFolder, comicExtensions, comicFiles)
            }
        }

        return comicFiles.distinctBy { it.absolutePath }
    }

    /**
     * Find movie files in the profile-specific Movies folder only
     * Each profile only sees its own content for complete content isolation
     */
    private fun findMovieFilesInFolders(): List<File> {
        val movieExtensions = setOf(
            "mp4", "mkv", "avi", "mov", "wmv", "webm", "m4v", "flv", "3gp"
        )
        val movieFiles = mutableListOf<File>()

        // Scan the profile-specific Movies folder only (complete content isolation)
        getProfileContentFolder("Movies")?.let { profileFolder ->
            if (profileFolder.exists() && profileFolder.isDirectory) {
                scanFolderRecursively(profileFolder, movieExtensions, movieFiles)
            }
        }

        return movieFiles.distinctBy { it.absolutePath }
    }

    /**
     * Scan for and add music files from the profile's Music folder
     */
    fun scanForMusicFiles(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            musicScanMutex.withLock {
                try {
                    // Deduplicate any existing entries first to clean up previous duplicate scans
                    val otherTypes = _libraryState.value.music.filter { it.contentType != ContentType.MUSIC }
                    var currentMusic = _libraryState.value.music.filter { it.contentType == ContentType.MUSIC }
                    val dedupedMusic = currentMusic.distinctBy { it.uri.toString() }
                    if (dedupedMusic.size != currentMusic.size) {
                        withContext(Dispatchers.Main) {
                            _libraryState.value = _libraryState.value.copy(music = otherTypes + dedupedMusic)
                            saveMusic()
                        }
                        currentMusic = dedupedMusic
                    }

                    val musicFiles = findMusicFilesInFolders()
                    // Use both URI and filename for duplicate detection
                    val existingUris = currentMusic.map { it.uri.toString() }.toSet()
                    val existingFilenames = currentMusic.mapNotNull { music ->
                        music.uri.lastPathSegment?.substringAfterLast("/")?.lowercase()
                    }.toSet()
                    val newMusic = mutableListOf<LibraryMusic>()

                    musicFiles.forEach { file ->
                        val uri = file.toUri()
                        val filename = file.name.lowercase()
                        if (uri.toString() !in existingUris && filename !in existingFilenames) {
                            // Check if file is in a playlist folder
                            val playlistName = getPlaylistFolderName(file, "Music")
                            val seriesId = if (playlistName != null) {
                                withContext(Dispatchers.Main) {
                                    findOrCreateSeriesForPlaylist(playlistName, ContentType.MUSIC)
                                }
                            } else null

                            val music = createMusicFromFile(context, file, seriesId, ContentType.MUSIC)
                            newMusic.add(music)
                        }
                    }

                    if (newMusic.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            val updatedMusic = (_libraryState.value.music + newMusic)
                                .distinctBy { it.uri.toString() }
                            _libraryState.value = _libraryState.value.copy(music = updatedMusic)
                            saveMusic()
                            saveSeries()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Scan for and add creepypasta files from the profile's Creepypasta folder
     */
    fun scanForCreepypastaFiles(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            musicScanMutex.withLock {
                try {
                    val otherTypes = _libraryState.value.music.filter { it.contentType != ContentType.CREEPYPASTA }
                    var current = _libraryState.value.music.filter { it.contentType == ContentType.CREEPYPASTA }
                    val deduped = current.distinctBy { it.uri.toString() }
                    if (deduped.size != current.size) {
                        withContext(Dispatchers.Main) {
                            _libraryState.value = _libraryState.value.copy(music = otherTypes + deduped)
                            saveMusic()
                        }
                        current = deduped
                    }

                    val files = findCreepypastaFilesInFolders()
                    val existingUris = current.map { it.uri.toString() }.toSet()
                    val existingFilenames = current.mapNotNull { item ->
                        item.uri.lastPathSegment?.substringAfterLast("/")?.lowercase()
                    }.toSet()
                    val newItems = mutableListOf<LibraryMusic>()

                    files.forEach { file ->
                        val uri = file.toUri()
                        val filename = file.name.lowercase()
                        if (uri.toString() !in existingUris && filename !in existingFilenames) {
                            val playlistName = getPlaylistFolderName(file, "Creepypasta")
                            val seriesId = if (playlistName != null) {
                                withContext(Dispatchers.Main) {
                                    findOrCreateSeriesForPlaylist(playlistName, ContentType.CREEPYPASTA)
                                }
                            } else null

                            val item = createMusicFromFile(context, file, seriesId, ContentType.CREEPYPASTA)
                            newItems.add(item)
                        }
                    }

                    if (newItems.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            val updated = (_libraryState.value.music + newItems)
                                .distinctBy { it.uri.toString() }
                            _libraryState.value = _libraryState.value.copy(music = updated)
                            saveMusic()
                            saveSeries()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Scan for and add comic files from the profile's Comics folder
     */
    fun scanForComicFiles(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val comicFiles = findComicFilesInFolders()
                // Use both URI and filename for duplicate detection
                val existingUris = _libraryState.value.comics.map { it.uri.toString() }.toSet()
                val existingFilenames = _libraryState.value.comics.mapNotNull { comic ->
                    comic.uri.lastPathSegment?.substringAfterLast("/")?.lowercase()
                }.toSet()
                val newComics = mutableListOf<LibraryComic>()

                comicFiles.forEach { file ->
                    val uri = file.toUri()
                    val filename = file.name.lowercase()
                    if (uri.toString() !in existingUris && filename !in existingFilenames) {
                        // Check if file is in a playlist folder
                        val playlistName = getPlaylistFolderName(file, "Comics")
                        val seriesId = if (playlistName != null) {
                            withContext(Dispatchers.Main) {
                                findOrCreateSeriesForPlaylist(playlistName, ContentType.COMICS)
                            }
                        } else null

                        val comic = createComicFromFile(file, seriesId)
                        newComics.add(comic)
                    }
                }

                if (newComics.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        _libraryState.value = _libraryState.value.copy(
                            comics = _libraryState.value.comics + newComics
                        )
                        saveComics()
                        saveSeries()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Scan for and add movie files from the profile's Movies folder
     */
    fun scanForMovieFiles(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val movieFiles = findMovieFilesInFolders()
                // Use both URI and filename for duplicate detection
                val existingUris = _libraryState.value.movies.map { it.uri.toString() }.toSet()
                val existingFilenames = _libraryState.value.movies.mapNotNull { movie ->
                    movie.uri.lastPathSegment?.substringAfterLast("/")?.lowercase()
                }.toSet()
                val newMovies = mutableListOf<LibraryMovie>()

                movieFiles.forEach { file ->
                    val uri = file.toUri()
                    val filename = file.name.lowercase()
                    if (uri.toString() !in existingUris && filename !in existingFilenames) {
                        // Check if file is in a playlist folder
                        val playlistName = getPlaylistFolderName(file, "Movies")
                        val seriesId = if (playlistName != null) {
                            withContext(Dispatchers.Main) {
                                findOrCreateSeriesForPlaylist(playlistName, ContentType.MOVIE)
                            }
                        } else null

                        val movie = createMovieFromFile(context, file, seriesId)
                        newMovies.add(movie)
                    }
                }

                if (newMovies.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        _libraryState.value = _libraryState.value.copy(
                            movies = _libraryState.value.movies + newMovies
                        )
                        saveMovies()
                        saveSeries()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Create a LibraryMusic from a file
     * Uses robust cover art extraction with fallback handling
     */
    private fun createMusicFromFile(
        context: Context,
        file: File,
        seriesId: String? = null,
        contentType: ContentType = ContentType.MUSIC
    ): LibraryMusic {
        val uri = file.toUri()
        var title = file.nameWithoutExtension
        var artist = "Unknown Artist"
        var album: String? = null
        var duration = 0L

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)

            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { title = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { artist = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { album = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.let {
                duration = it.toLongOrNull() ?: 0L
            }
            retriever.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Extract cover art using robust method with fallback handling (including folder art lookup)
        val coverArt = extractCoverArtRobust(context, uri, file, 512)

        return LibraryMusic(
            id = UUID.randomUUID().toString(),
            uri = uri,
            title = title,
            artist = artist,
            album = album,
            coverArt = coverArt,
            duration = duration,
            fileType = file.extension.lowercase(),
            timesListened = 0,
            seriesId = seriesId,
            contentType = contentType
        )
    }

    /**
     * Create a LibraryComic from a file
     */
    private fun createComicFromFile(file: File, seriesId: String? = null): LibraryComic {
        val uri = file.toUri()
        val title = file.nameWithoutExtension

        return LibraryComic(
            id = UUID.randomUUID().toString(),
            uri = uri,
            title = title,
            fileType = file.extension.lowercase(),
            seriesId = seriesId
        )
    }

    /**
     * Create a LibraryMovie from a file
     */
    private fun createMovieFromFile(context: Context, file: File, seriesId: String? = null): LibraryMovie {
        val uri = file.toUri()
        var title = file.nameWithoutExtension
        var duration = 0L

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)

            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { title = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.let {
                duration = it.toLongOrNull() ?: 0L
            }
            retriever.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return LibraryMovie(
            id = UUID.randomUUID().toString(),
            uri = uri,
            title = title,
            duration = duration,
            fileType = file.extension.lowercase(),
            seriesId = seriesId
        )
    }

    // Selection functions for new content types
    fun selectMusic(music: LibraryMusic) {
        _selectedMusic.value = music
    }

    fun selectComic(comic: LibraryComic) {
        _selectedComic.value = comic
    }

    fun selectMovie(movie: LibraryMovie) {
        _selectedMovie.value = movie
    }

    fun clearMusicSelection() {
        _selectedMusic.value = null
    }

    fun clearComicSelection() {
        _selectedComic.value = null
    }

    fun clearMovieSelection() {
        _selectedMovie.value = null
    }

    /**
     * Add a music file to the library from a URI
     */
    fun addMusic(context: Context, uri: Uri, contentType: ContentType = ContentType.MUSIC) {
        viewModelScope.launch {
            try {
                // Take persistable permission for the URI
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Permission might not be persistable, continue anyway
                }

                val music = withContext(Dispatchers.IO) {
                    extractMusicMetadata(context, uri, contentType)
                }

                val currentMusic = _libraryState.value.music.toMutableList()

                // Check if already exists
                if (currentMusic.none { it.uri == uri }) {
                    currentMusic.add(0, music)
                    _libraryState.value = _libraryState.value.copy(music = currentMusic)
                    // Save after adding
                    saveMusic()
                }
            } catch (e: Exception) {
                _libraryState.value = _libraryState.value.copy(
                    error = "Failed to add music: ${e.message}"
                )
            }
        }
    }

    private fun extractMusicMetadata(context: Context, uri: Uri, contentType: ContentType): LibraryMusic {
        val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "Unknown"
        val extension = fileName.substringAfterLast(".", "mp3").lowercase()
        val title = fileName.substringBeforeLast(".")

        return LibraryMusic(
            id = UUID.randomUUID().toString(),
            uri = uri,
            title = title,
            artist = "Unknown Artist",
            duration = 0L,
            dateAdded = System.currentTimeMillis(),
            fileType = extension,
            timesListened = 0,
            contentType = contentType
        )
    }

    /**
     * Add a comic file to the library from a URI
     */
    fun addComic(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                // Take persistable permission for the URI
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Permission might not be persistable, continue anyway
                }

                val comic = withContext(Dispatchers.IO) {
                    extractComicMetadata(context, uri)
                }

                val currentComics = _libraryState.value.comics.toMutableList()

                // Check if already exists
                if (currentComics.none { it.uri == uri }) {
                    currentComics.add(0, comic)
                    _libraryState.value = _libraryState.value.copy(comics = currentComics)
                    // Save after adding
                    saveComics()
                }
            } catch (e: Exception) {
                _libraryState.value = _libraryState.value.copy(
                    error = "Failed to add comic: ${e.message}"
                )
            }
        }
    }

    private fun extractComicMetadata(context: Context, uri: Uri): LibraryComic {
        val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "Unknown"
        val extension = fileName.substringAfterLast(".", "cbz").lowercase()
        val title = fileName.substringBeforeLast(".")

        return LibraryComic(
            id = UUID.randomUUID().toString(),
            uri = uri,
            title = title,
            totalPages = 0,
            dateAdded = System.currentTimeMillis(),
            fileType = extension
        )
    }

    /**
     * Add a movie file to the library from a URI
     */
    fun addMovie(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                // Take persistable permission for the URI
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Permission might not be persistable, continue anyway
                }

                val movie = withContext(Dispatchers.IO) {
                    extractMovieMetadata(context, uri)
                }

                val currentMovies = _libraryState.value.movies.toMutableList()

                // Check if already exists
                if (currentMovies.none { it.uri == uri }) {
                    currentMovies.add(0, movie)
                    _libraryState.value = _libraryState.value.copy(movies = currentMovies)
                    // Save after adding
                    saveMovies()
                }
            } catch (e: Exception) {
                _libraryState.value = _libraryState.value.copy(
                    error = "Failed to add movie: ${e.message}"
                )
            }
        }
    }

    private fun extractMovieMetadata(context: Context, uri: Uri): LibraryMovie {
        val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "Unknown"
        val extension = fileName.substringAfterLast(".", "mp4").lowercase()
        val title = fileName.substringBeforeLast(".")

        return LibraryMovie(
            id = UUID.randomUUID().toString(),
            uri = uri,
            title = title,
            duration = 0L,
            dateAdded = System.currentTimeMillis(),
            fileType = extension
        )
    }

    fun updateMusicProgress(musicId: String, position: Long) {
        val currentState = _libraryState.value
        // Get the music item for progress.json saving
        val musicItem = currentState.music.find { it.id == musicId }

        val updatedMusic = currentState.music.map { music ->
            if (music.id == musicId) {
                music.copy(
                    lastPosition = position,
                    lastPlayed = System.currentTimeMillis()
                )
            } else music
        }
        _libraryState.value = currentState.copy(music = updatedMusic)

        // Also update selected music if it matches
        _selectedMusic.value?.let { selected ->
            if (selected.id == musicId) {
                _selectedMusic.value = selected.copy(
                    lastPosition = position,
                    lastPlayed = System.currentTimeMillis()
                )
            }
        }

        // Save progress periodically (every ~5 seconds)
        if (position % 5000 < 1000) {
            saveMusic()
            // Also save to progress.json file for persistence
            musicItem?.let {
                viewModelScope.launch {
                    repository?.updateProgressInFile(
                        uri = it.uri.toString(),
                        type = "MUSIC",
                        position = position,
                        total = it.duration
                    )
                }
            }
        }
    }

    fun incrementMusicPlayCount(musicId: String) {
        val currentState = _libraryState.value
        val updatedMusic = currentState.music.map { music ->
            if (music.id == musicId) {
                music.copy(
                    timesListened = music.timesListened + 1,
                    lastPlayed = System.currentTimeMillis(),
                    lastPosition = 0L
                )
            } else music
        }
        _libraryState.value = currentState.copy(music = updatedMusic)

        _selectedMusic.value?.let { selected ->
            if (selected.id == musicId) {
                _selectedMusic.value = selected.copy(
                    timesListened = selected.timesListened + 1,
                    lastPlayed = System.currentTimeMillis(),
                    lastPosition = 0L
                )
            }
        }

        saveMusicImmediately(updatedMusic)
    }

    /**
     * Update music metadata (title and artist) manually
     */
    fun updateMusicMetadata(musicId: String, newTitle: String, newArtist: String) {
        val currentMusic = _libraryState.value.music.toMutableList()
        val index = currentMusic.indexOfFirst { it.id == musicId }

        if (index >= 0) {
            val music = currentMusic[index]
            currentMusic[index] = music.copy(
                title = newTitle.ifBlank { music.title },
                artist = newArtist.ifBlank { music.artist }
            )
            _libraryState.value = _libraryState.value.copy(music = currentMusic)
            // Persist immediately so edits survive app restarts
            saveMusicImmediately(currentMusic)
        }
    }

    /**
     * Remove a music track from the library
     */
    fun removeMusic(music: LibraryMusic, deleteFile: Boolean = true) {
        // Delete the file from device if requested
        if (deleteFile) {
            deleteFileFromUri(music.uri)
        }

        val currentMusic = _libraryState.value.music.toMutableList()
        currentMusic.removeAll { it.id == music.id }
        _libraryState.value = _libraryState.value.copy(music = currentMusic)

        if (_selectedMusic.value?.id == music.id) {
            _selectedMusic.value = null
        }

        saveMusic()
    }

    fun updateMovieProgress(movieId: String, position: Long) {
        val currentState = _libraryState.value
        // Get the movie item for progress.json saving
        val movieItem = currentState.movies.find { it.id == movieId }

        val updatedMovies = currentState.movies.map { movie ->
            if (movie.id == movieId) {
                movie.copy(
                    lastPosition = position,
                    lastPlayed = System.currentTimeMillis()
                )
            } else movie
        }
        _libraryState.value = currentState.copy(movies = updatedMovies)

        // Also update selected movie if it matches
        _selectedMovie.value?.let { selected ->
            if (selected.id == movieId) {
                _selectedMovie.value = selected.copy(
                    lastPosition = position,
                    lastPlayed = System.currentTimeMillis()
                )
            }
        }

        // Save progress periodically (every ~5 seconds)
        if (position % 5000 < 1000) {
            saveMovies()
            // Also save to progress.json file for persistence
            movieItem?.let {
                viewModelScope.launch {
                    repository?.updateProgressInFile(
                        uri = it.uri.toString(),
                        type = "MOVIE",
                        position = position,
                        total = it.duration
                    )
                }
            }
        }
    }

    /**
     * Update movie metadata (title) manually
     */
    fun updateMovieMetadata(movieId: String, newTitle: String) {
        val currentMovies = _libraryState.value.movies.toMutableList()
        val index = currentMovies.indexOfFirst { it.id == movieId }

        if (index >= 0) {
            val movie = currentMovies[index]
            currentMovies[index] = movie.copy(
                title = newTitle.ifBlank { movie.title }
            )
            _libraryState.value = _libraryState.value.copy(movies = currentMovies)
            // Persist immediately so edits survive app restarts
            saveMoviesImmediately(currentMovies)
        }
    }

    /**
     * Remove a movie from the library
     */
    fun removeMovie(movie: LibraryMovie, deleteFile: Boolean = true) {
        // Delete the file from device if requested
        if (deleteFile) {
            deleteFileFromUri(movie.uri)
        }

        val currentMovies = _libraryState.value.movies.toMutableList()
        currentMovies.removeAll { it.id == movie.id }
        _libraryState.value = _libraryState.value.copy(movies = currentMovies)

        if (_selectedMovie.value?.id == movie.id) {
            _selectedMovie.value = null
        }

        saveMovies()
    }

    fun updateComicProgress(comicId: String, currentPage: Int, totalPages: Int = 0) {
        val currentState = _libraryState.value
        // Get the comic item for progress.json saving
        val comicItem = currentState.comics.find { it.id == comicId }

        val updatedComics = currentState.comics.map { comic ->
            if (comic.id == comicId) {
                // Use provided totalPages if valid, otherwise keep existing
                val effectiveTotalPages = if (totalPages > 0) totalPages else comic.totalPages
                comic.copy(
                    currentPage = currentPage,
                    totalPages = effectiveTotalPages,
                    lastRead = System.currentTimeMillis(),
                    isCompleted = effectiveTotalPages > 0 && currentPage >= effectiveTotalPages - 1
                )
            } else comic
        }
        _libraryState.value = currentState.copy(comics = updatedComics)

        // Also update selected comic if it matches
        _selectedComic.value?.let { selected ->
            if (selected.id == comicId) {
                val effectiveTotalPages = if (totalPages > 0) totalPages else selected.totalPages
                _selectedComic.value = selected.copy(
                    currentPage = currentPage,
                    totalPages = effectiveTotalPages,
                    lastRead = System.currentTimeMillis(),
                    isCompleted = effectiveTotalPages > 0 && currentPage >= effectiveTotalPages - 1
                )
            }
        }

        // Save comic progress immediately with the updated list (to avoid state timing issues)
        saveComicsImmediately(updatedComics)

        // Also save to progress.json file for persistence
        comicItem?.let {
            val effectiveTotalPages = if (totalPages > 0) totalPages else it.totalPages
            viewModelScope.launch {
                repository?.updateProgressInFile(
                    uri = it.uri.toString(),
                    type = "COMICS",
                    position = currentPage.toLong(),
                    total = effectiveTotalPages.toLong()
                )
            }
        }
    }

    /**
     * Update comic metadata (title and author) manually
     */
    fun updateComicMetadata(comicId: String, newTitle: String, newAuthor: String) {
        val currentComics = _libraryState.value.comics.toMutableList()
        val index = currentComics.indexOfFirst { it.id == comicId }

        if (index >= 0) {
            val comic = currentComics[index]
            currentComics[index] = comic.copy(
                title = newTitle.ifBlank { comic.title },
                author = newAuthor.ifBlank { comic.author }
            )
            _libraryState.value = _libraryState.value.copy(comics = currentComics)
            // Persist immediately so edits survive app restarts
            saveComicsImmediately(currentComics)
        }
    }

    /**
     * Remove a comic from the library
     */
    fun removeComic(comic: LibraryComic, deleteFile: Boolean = true) {
        // Delete the file from device if requested
        if (deleteFile) {
            deleteFileFromUri(comic.uri)
        }

        val currentComics = _libraryState.value.comics.toMutableList()
        currentComics.removeAll { it.id == comic.id }
        _libraryState.value = _libraryState.value.copy(comics = currentComics)

        if (_selectedComic.value?.id == comic.id) {
            _selectedComic.value = null
        }

        saveComics()
    }

    /**
     * Get filtered and sorted music
     */
    fun getFilteredMusic(): List<LibraryMusic> {
        val query = _searchQuery.value.lowercase()
        val currentType = _libraryState.value.selectedContentType
        if (currentType != ContentType.MUSIC && currentType != ContentType.CREEPYPASTA) {
            return emptyList()
        }
        val selectedCategoryId = when (currentType) {
            ContentType.CREEPYPASTA -> _libraryState.value.selectedCreepypastaCategoryId
            else -> _libraryState.value.selectedMusicCategoryId
        }

        // First filter by category
        val categoryFiltered = _libraryState.value.music
            .filter { it.contentType == currentType }
            .filter { selectedCategoryId == null || it.categoryId == selectedCategoryId }

        // Then filter by search query
        val filtered = if (query.isEmpty()) {
            categoryFiltered
        } else {
            categoryFiltered.filter {
                it.title.lowercase().contains(query) ||
                it.artist.lowercase().contains(query) ||
                (it.album?.lowercase()?.contains(query) == true)
            }
        }

        return when (_musicSortOption.value) {
            SortOption.RECENTLY_ADDED -> filtered.sortedByDescending { it.dateAdded }
            SortOption.RECENTLY_PLAYED -> filtered.sortedByDescending { it.lastPlayed }
            SortOption.TITLE_AZ -> filtered.sortedBy { it.title.lowercase() }
            SortOption.TITLE_ZA -> filtered.sortedByDescending { it.title.lowercase() }
            SortOption.AUTHOR_AZ -> filtered.sortedBy { it.artist.lowercase() }
            SortOption.PROGRESS -> filtered.sortedByDescending { it.progress }
            SortOption.BOOK_NUMBER -> filtered.sortedBy { extractBookNumber(it.title) }
        }
    }

    /**
     * Get filtered and sorted comics
     */
    fun getFilteredComics(): List<LibraryComic> {
        val query = _searchQuery.value.lowercase()
        val selectedCategoryId = _libraryState.value.selectedComicCategoryId

        // First filter by category
        val categoryFiltered = if (selectedCategoryId == null) {
            _libraryState.value.comics
        } else {
            _libraryState.value.comics.filter { it.categoryId == selectedCategoryId }
        }

        // Then filter by search query
        val filtered = if (query.isEmpty()) {
            categoryFiltered
        } else {
            categoryFiltered.filter {
                it.title.lowercase().contains(query) ||
                it.author.lowercase().contains(query) ||
                (it.series?.lowercase()?.contains(query) == true)
            }
        }

        return when (_comicSortOption.value) {
            SortOption.RECENTLY_ADDED -> filtered.sortedByDescending { it.dateAdded }
            SortOption.RECENTLY_PLAYED -> filtered.sortedByDescending { it.lastRead }
            SortOption.TITLE_AZ -> filtered.sortedBy { it.title.lowercase() }
            SortOption.TITLE_ZA -> filtered.sortedByDescending { it.title.lowercase() }
            SortOption.AUTHOR_AZ -> filtered.sortedBy { it.author.lowercase() }
            SortOption.PROGRESS -> filtered.sortedByDescending { it.progress }
            SortOption.BOOK_NUMBER -> filtered.sortedBy { extractBookNumber(it.title) }
        }
    }

    /**
     * Delete a file from device by URI
     * Works with file:// URIs from the Librio folder
     */
    private fun deleteFileFromUri(uri: Uri) {
        try {
            // Try to get the file path from the URI
            val path = uri.path
            if (path != null) {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            // Silently fail - file might be a content:// URI or permission denied
            e.printStackTrace()
        }
    }

    /**
     * Get filtered and sorted movies
     */
    fun getFilteredMovies(): List<LibraryMovie> {
        val query = _searchQuery.value.lowercase()
        val selectedCategoryId = _libraryState.value.selectedMovieCategoryId

        // First filter by category
        val categoryFiltered = if (selectedCategoryId == null) {
            _libraryState.value.movies
        } else {
            _libraryState.value.movies.filter { it.categoryId == selectedCategoryId }
        }

        // Then filter by search query
        val filtered = if (query.isEmpty()) {
            categoryFiltered
        } else {
            categoryFiltered.filter {
                it.title.lowercase().contains(query)
            }
        }

        return when (_movieSortOption.value) {
            SortOption.RECENTLY_ADDED -> filtered.sortedByDescending { it.dateAdded }
            SortOption.RECENTLY_PLAYED -> filtered.sortedByDescending { it.lastPlayed }
            SortOption.TITLE_AZ -> filtered.sortedBy { it.title.lowercase() }
            SortOption.TITLE_ZA -> filtered.sortedByDescending { it.title.lowercase() }
            SortOption.AUTHOR_AZ -> filtered.sortedBy { it.title.lowercase() } // Movies don't have author
            SortOption.PROGRESS -> filtered.sortedByDescending { it.progress }
            SortOption.BOOK_NUMBER -> filtered.sortedBy { extractBookNumber(it.title) }
        }
    }

    // ==================== Series Management ====================

    /**
     * Add a new series for the current content type
     */
    fun addSeries(name: String, categoryId: String? = null) {
        if (name.isBlank()) return

        val contentType = _libraryState.value.selectedContentType
        val currentSeries = _libraryState.value.series.toMutableList()

        // Get max order for this content type
        val maxOrder = currentSeries
            .asSequence()
            .filter { it.contentType == contentType }
            .maxOfOrNull { it.order } ?: -1

        val newSeries = LibrarySeries(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            contentType = contentType,
            categoryId = categoryId ?: _libraryState.value.selectedCategoryId,
            order = maxOrder + 1
        )

        currentSeries.add(newSeries)
        _libraryState.value = _libraryState.value.copy(series = currentSeries)
        saveSeries()

        // Create playlist folder for the new series
        viewModelScope.launch {
            repository?.createPlaylistFolder(contentType, name.trim())
        }
    }

    /**
     * Delete a series (items in it will become unassigned)
     * @param deleteFolder If true, also deletes the playlist folder (only if empty)
     */
    fun deleteSeries(seriesId: String, deleteFolder: Boolean = false) {
        val seriesToDelete = _libraryState.value.series.find { it.id == seriesId }
        val currentSeries = _libraryState.value.series.toMutableList()
        currentSeries.removeAll { it.id == seriesId }
        _libraryState.value = _libraryState.value.copy(series = currentSeries)

        // Clear seriesId from all items that had this series
        clearSeriesFromItems(seriesId)
        saveSeries()

        // Optionally delete the playlist folder (only if empty by default)
        if (deleteFolder && seriesToDelete != null) {
            viewModelScope.launch {
                repository?.deletePlaylistFolder(
                    seriesToDelete.contentType,
                    seriesToDelete.name,
                    deleteContents = false // Only delete if empty
                )
            }
        }
    }

    /**
     * Rename a series
     */
    fun renameSeries(seriesId: String, newName: String) {
        if (newName.isBlank()) return

        val oldSeries = _libraryState.value.series.find { it.id == seriesId }
        val currentSeries = _libraryState.value.series.map {
            if (it.id == seriesId) it.copy(name = newName.trim()) else it
        }
        _libraryState.value = _libraryState.value.copy(series = currentSeries)
        saveSeries()

        // Rename the playlist folder
        if (oldSeries != null) {
            viewModelScope.launch {
                repository?.renamePlaylistFolder(
                    oldSeries.contentType,
                    oldSeries.name,
                    newName.trim()
                )
            }
        }
    }

    /**
     * Set series order (for drag-and-drop reordering)
     */
    fun reorderSeries(seriesId: String, newOrder: Int) {
        val currentSeries = _libraryState.value.series.map {
            if (it.id == seriesId) it.copy(order = newOrder) else it
        }
        _libraryState.value = _libraryState.value.copy(series = currentSeries)
        saveSeries()
    }

    fun setAudiobookCoverArt(audiobookId: String, coverArtUri: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val cover = loadCustomCoverArt(coverArtUri)
            val parsedUri = coverArtUri?.let { Uri.parse(it) }
            withContext(Dispatchers.Main) {
                val updated = _libraryState.value.audiobooks.map {
                    if (it.id == audiobookId) it.copy(coverArtUri = parsedUri, coverArt = cover) else it
                }
                _libraryState.value = _libraryState.value.copy(audiobooks = updated)
                saveLibrary()
            }
        }
    }

    fun setBookCoverArt(bookId: String, coverArtUri: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val cover = loadCustomCoverArt(coverArtUri)
            withContext(Dispatchers.Main) {
                val updated = _libraryState.value.books.map {
                    if (it.id == bookId) it.copy(coverArtUri = coverArtUri, coverArt = cover) else it
                }
                _libraryState.value = _libraryState.value.copy(books = updated)
                saveBooks()
            }
        }
    }

    fun setMusicCoverArt(musicId: String, coverArtUri: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val cover = loadCustomCoverArt(coverArtUri)
            withContext(Dispatchers.Main) {
                val updated = _libraryState.value.music.map {
                    if (it.id == musicId) it.copy(coverArtUri = coverArtUri, coverArt = cover) else it
                }
                _libraryState.value = _libraryState.value.copy(music = updated)
                saveMusic()
            }
        }
    }

    fun setComicCoverArt(comicId: String, coverArtUri: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val cover = loadCustomCoverArt(coverArtUri)
            withContext(Dispatchers.Main) {
                val updated = _libraryState.value.comics.map {
                    if (it.id == comicId) it.copy(coverArtUri = coverArtUri, coverArt = cover) else it
                }
                _libraryState.value = _libraryState.value.copy(comics = updated)
                saveComics()
            }
        }
    }

    fun setMovieCoverArt(movieId: String, coverArtUri: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                val updated = _libraryState.value.movies.map {
                    if (it.id == movieId) it.copy(coverArtUri = coverArtUri) else it
                }
                _libraryState.value = _libraryState.value.copy(movies = updated)
                saveMovies()
            }
        }
    }

    /**
     * Set custom cover art for a series/playlist
     */
    fun setSeriesCoverArt(seriesId: String, coverArtUri: String?) {
        val currentSeries = _libraryState.value.series.map {
            if (it.id == seriesId) it.copy(coverArtUri = coverArtUri) else it
        }
        _libraryState.value = _libraryState.value.copy(series = currentSeries)
        saveSeries()
    }

    /**
     * Refresh playlist folders - scan for new folders and add them as series
     */
    fun refreshPlaylistFolders() {
        viewModelScope.launch {
            val repo = repository ?: return@launch
            val currentSeries = _libraryState.value.series
            val updatedSeries = repo.syncPlaylistFoldersWithSeries(currentSeries)

            if (updatedSeries.size != currentSeries.size) {
                _libraryState.value = _libraryState.value.copy(series = updatedSeries)
                saveSeries()
            }
        }
    }

    /**
     * Update library - comprehensive update that refreshes playlists, reassigns items, and scans for new files.
     * This is the main function called by the "Update Library" button.
     */
    fun updateLibrary(context: Context, onComplete: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                _libraryState.value = _libraryState.value.copy(isLoading = true)

                // Step 1: Refresh playlist folders
                withContext(Dispatchers.IO) {
                    val repo = repository ?: return@withContext
                    val currentSeries = _libraryState.value.series
                    val updatedSeries = repo.syncPlaylistFoldersWithSeries(currentSeries)
                    if (updatedSeries.size != currentSeries.size) {
                        withContext(Dispatchers.Main) {
                            _libraryState.value = _libraryState.value.copy(series = updatedSeries)
                            saveSeries()
                        }
                    }
                }

                // Step 2: Reassign existing items to playlists based on their current locations
                reassignPlaylistMembershipInternal()

                // Step 3: Scan for new files (silent versions to avoid duplicate error messages)
                scanAudiobooksFolderSilent(context)
                scanBooksFolderSilent(context)
                // Music, comics, and movies don't have silent versions, scan normally
                scanForMusicFiles(context)
                scanForCreepypastaFiles(context)
                scanForComicFiles(context)
                scanForMovieFiles(context)

                _libraryState.value = _libraryState.value.copy(isLoading = false)
                withContext(Dispatchers.Main) {
                    onComplete("Library updated successfully")
                }
            } catch (e: Exception) {
                _libraryState.value = _libraryState.value.copy(
                    isLoading = false,
                    error = "Failed to update library: ${e.message}"
                )
                withContext(Dispatchers.Main) {
                    onComplete("Update failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Reassign seriesId to existing items based on their current file locations in playlist folders.
     * Also removes items whose files no longer exist on the device.
     * This handles cases where users manually create playlist folders and move files into them.
     */
    private suspend fun reassignPlaylistMembershipInternal() {
        withContext(Dispatchers.IO) {
            var audiobooksUpdated = false
            var booksUpdated = false
            var musicUpdated = false
            var comicsUpdated = false
            var moviesUpdated = false

            // Update audiobooks - filter out missing files and update seriesId for existing ones
            val updatedAudiobooks = _libraryState.value.audiobooks.mapNotNull { audiobook ->
                // Convert URI to File - handle both file:// and content:// URIs
                val file = try {
                    java.io.File(java.net.URI(audiobook.uri.toString()))
                } catch (e: Exception) {
                    // Fallback to path if URI parsing fails
                    audiobook.uri.path?.let { java.io.File(it) }
                }

                if (file != null && file.exists()) {
                    val playlistName = getPlaylistFolderName(file, "Audiobooks")
                    val newSeriesId = if (playlistName != null) {
                        findOrCreateSeriesForPlaylist(playlistName, ContentType.AUDIOBOOK)
                    } else null

                    if (newSeriesId != audiobook.seriesId) {
                        audiobooksUpdated = true
                        audiobook.copy(seriesId = newSeriesId)
                    } else audiobook
                } else {
                    // File no longer exists, remove from library
                    audiobooksUpdated = true
                    null
                }
            }

            // Update books - filter out missing files and update seriesId for existing ones
            val updatedBooks = _libraryState.value.books.mapNotNull { book ->
                val file = try {
                    java.io.File(java.net.URI(book.uri.toString()))
                } catch (e: Exception) {
                    book.uri.path?.let { java.io.File(it) }
                }

                if (file != null && file.exists()) {
                    val playlistName = getPlaylistFolderName(file, "Books")
                    val newSeriesId = if (playlistName != null) {
                        findOrCreateSeriesForPlaylist(playlistName, ContentType.EBOOK)
                    } else null

                    if (newSeriesId != book.seriesId) {
                        booksUpdated = true
                        book.copy(seriesId = newSeriesId)
                    } else book
                } else {
                    // File no longer exists, remove from library
                    booksUpdated = true
                    null
                }
            }

            // Update music - filter out missing files and update seriesId for existing ones
            val updatedMusic = _libraryState.value.music.mapNotNull { music ->
                val file = try {
                    java.io.File(java.net.URI(music.uri.toString()))
                } catch (e: Exception) {
                    music.uri.path?.let { java.io.File(it) }
                }

                if (file != null && file.exists()) {
                    val folderName = if (music.contentType == ContentType.CREEPYPASTA) "Creepypasta" else "Music"
                    val playlistName = getPlaylistFolderName(file, folderName)
                    val newSeriesId = if (playlistName != null) {
                        findOrCreateSeriesForPlaylist(playlistName, music.contentType)
                    } else null

                    if (newSeriesId != music.seriesId) {
                        musicUpdated = true
                        music.copy(seriesId = newSeriesId)
                    } else music
                } else {
                    // File no longer exists, remove from library
                    musicUpdated = true
                    null
                }
            }

            // Update comics - filter out missing files and update seriesId for existing ones
            val updatedComics = _libraryState.value.comics.mapNotNull { comic ->
                val file = try {
                    java.io.File(java.net.URI(comic.uri.toString()))
                } catch (e: Exception) {
                    comic.uri.path?.let { java.io.File(it) }
                }

                if (file != null && file.exists()) {
                    val playlistName = getPlaylistFolderName(file, "Comics")
                    val newSeriesId = if (playlistName != null) {
                        findOrCreateSeriesForPlaylist(playlistName, ContentType.COMICS)
                    } else null

                    if (newSeriesId != comic.seriesId) {
                        comicsUpdated = true
                        comic.copy(seriesId = newSeriesId)
                    } else comic
                } else {
                    // File no longer exists, remove from library
                    comicsUpdated = true
                    null
                }
            }

            // Update movies - filter out missing files and update seriesId for existing ones
            val updatedMovies = _libraryState.value.movies.mapNotNull { movie ->
                val file = try {
                    java.io.File(java.net.URI(movie.uri.toString()))
                } catch (e: Exception) {
                    movie.uri.path?.let { java.io.File(it) }
                }

                if (file != null && file.exists()) {
                    val playlistName = getPlaylistFolderName(file, "Movies")
                    val newSeriesId = if (playlistName != null) {
                        findOrCreateSeriesForPlaylist(playlistName, ContentType.MOVIE)
                    } else null

                    if (newSeriesId != movie.seriesId) {
                        moviesUpdated = true
                        movie.copy(seriesId = newSeriesId)
                    } else movie
                } else {
                    // File no longer exists, remove from library
                    moviesUpdated = true
                    null
                }
            }

            // Update state and save if any changes were made
            withContext(Dispatchers.Main) {
                if (audiobooksUpdated || booksUpdated || musicUpdated || comicsUpdated || moviesUpdated) {
                    _libraryState.value = _libraryState.value.copy(
                        audiobooks = updatedAudiobooks,
                        books = updatedBooks,
                        music = updatedMusic,
                        comics = updatedComics,
                        movies = updatedMovies
                    )
                    saveLibrary()
                    if (audiobooksUpdated || booksUpdated || musicUpdated || comicsUpdated || moviesUpdated) {
                        saveSeries()
                    }
                }
            }
        }
    }

    /**
     * Get series for the current content type and category
     */
    fun getSeriesForCurrentContentType(): List<LibrarySeries> {
        val contentType = _libraryState.value.selectedContentType
        val categoryId = _libraryState.value.selectedCategoryId

        return _libraryState.value.series
            .asSequence()
            .filter { it.contentType == contentType && (categoryId == null || it.categoryId == categoryId || it.categoryId == null) }
            .sortedBy { it.order }
            .toList()
    }

    /**
     * Assign an audiobook to a series and move file to playlist folder
     */
    fun setAudiobookSeries(audiobookId: String, seriesId: String?, seriesOrder: Int = 0) {
        viewModelScope.launch {
            val audiobook = _libraryState.value.audiobooks.find { it.id == audiobookId } ?: return@launch
            val series = if (seriesId != null) _libraryState.value.series.find { it.id == seriesId } else null

            var newUri = audiobook.uri

            // Move file to playlist folder or back to root
            val filePath = audiobook.uri.path
            if (filePath != null) {
                val newPath = if (series != null) {
                    repository?.moveFileToPlaylist(filePath, ContentType.AUDIOBOOK, series.name)
                } else {
                    repository?.moveFileToRoot(filePath, ContentType.AUDIOBOOK)
                }
                if (newPath != null) {
                    newUri = java.io.File(newPath).toUri()
                }
            }

            val currentAudiobooks = _libraryState.value.audiobooks.map {
                if (it.id == audiobookId) it.copy(uri = newUri, seriesId = seriesId, seriesOrder = seriesOrder) else it
            }
            _libraryState.value = _libraryState.value.copy(audiobooks = currentAudiobooks)
            saveLibrary()
        }
    }

    /**
     * Assign a book to a series and move file to playlist folder
     */
    fun setBookSeries(bookId: String, seriesId: String?, seriesOrder: Int = 0) {
        viewModelScope.launch {
            val book = _libraryState.value.books.find { it.id == bookId } ?: return@launch
            val series = if (seriesId != null) _libraryState.value.series.find { it.id == seriesId } else null

            var newUri = book.uri

            // Move file to playlist folder or back to root
            val filePath = book.uri.path
            if (filePath != null) {
                val newPath = if (series != null) {
                    repository?.moveFileToPlaylist(filePath, ContentType.EBOOK, series.name)
                } else {
                    repository?.moveFileToRoot(filePath, ContentType.EBOOK)
                }
                if (newPath != null) {
                    newUri = java.io.File(newPath).toUri()
                }
            }

            val currentBooks = _libraryState.value.books.map {
                if (it.id == bookId) it.copy(uri = newUri, seriesId = seriesId, seriesOrder = seriesOrder) else it
            }
            _libraryState.value = _libraryState.value.copy(books = currentBooks)
            saveBooks()
        }
    }

    /**
     * Assign a music track to a series and move file to playlist folder
     */
    fun setMusicSeries(musicId: String, seriesId: String?, seriesOrder: Int = 0) {
        viewModelScope.launch {
            val music = _libraryState.value.music.find { it.id == musicId } ?: return@launch
            val series = if (seriesId != null) _libraryState.value.series.find { it.id == seriesId } else null
            val targetType = music.contentType

            var newUri = music.uri

            // Move file to playlist folder or back to root
            val filePath = music.uri.path
            if (filePath != null) {
                val newPath = if (series != null) {
                    repository?.moveFileToPlaylist(filePath, targetType, series.name)
                } else {
                    repository?.moveFileToRoot(filePath, targetType)
                }
                if (newPath != null) {
                    newUri = java.io.File(newPath).toUri()
                }
            }

            val currentMusic = _libraryState.value.music.map {
                if (it.id == musicId) it.copy(uri = newUri, seriesId = seriesId, seriesOrder = seriesOrder) else it
            }
            _libraryState.value = _libraryState.value.copy(music = currentMusic)
            saveMusic()
        }
    }

    /**
     * Assign a comic to a series and move file to playlist folder
     */
    fun setComicSeries(comicId: String, seriesId: String?, seriesOrder: Int = 0) {
        viewModelScope.launch {
            val comic = _libraryState.value.comics.find { it.id == comicId } ?: return@launch
            val series = if (seriesId != null) _libraryState.value.series.find { it.id == seriesId } else null

            var newUri = comic.uri

            // Move file to playlist folder or back to root
            val filePath = comic.uri.path
            if (filePath != null) {
                val newPath = if (series != null) {
                    repository?.moveFileToPlaylist(filePath, ContentType.COMICS, series.name)
                } else {
                    repository?.moveFileToRoot(filePath, ContentType.COMICS)
                }
                if (newPath != null) {
                    newUri = java.io.File(newPath).toUri()
                }
            }

            val currentComics = _libraryState.value.comics.map {
                if (it.id == comicId) it.copy(uri = newUri, seriesId = seriesId, seriesOrder = seriesOrder) else it
            }
            _libraryState.value = _libraryState.value.copy(comics = currentComics)
            saveComics()
        }
    }

    /**
     * Assign a movie to a series and move file to playlist folder
     */
    fun setMovieSeries(movieId: String, seriesId: String?, seriesOrder: Int = 0) {
        viewModelScope.launch {
            val movie = _libraryState.value.movies.find { it.id == movieId } ?: return@launch
            val series = if (seriesId != null) _libraryState.value.series.find { it.id == seriesId } else null

            var newUri = movie.uri

            // Move file to playlist folder or back to root
            val filePath = movie.uri.path
            if (filePath != null) {
                val newPath = if (series != null) {
                    repository?.moveFileToPlaylist(filePath, ContentType.MOVIE, series.name)
                } else {
                    repository?.moveFileToRoot(filePath, ContentType.MOVIE)
                }
                if (newPath != null) {
                    newUri = java.io.File(newPath).toUri()
                }
            }

            val currentMovies = _libraryState.value.movies.map {
                if (it.id == movieId) it.copy(uri = newUri, seriesId = seriesId, seriesOrder = seriesOrder) else it
            }
            _libraryState.value = _libraryState.value.copy(movies = currentMovies)
            saveMovies()
        }
    }

    /**
     * Clear series from all items (used when deleting a series)
     */
    private fun clearSeriesFromItems(seriesId: String) {
        // Clear from audiobooks
        val currentAudiobooks = _libraryState.value.audiobooks.map {
            if (it.seriesId == seriesId) it.copy(seriesId = null, seriesOrder = 0) else it
        }
        // Clear from books
        val currentBooks = _libraryState.value.books.map {
            if (it.seriesId == seriesId) it.copy(seriesId = null, seriesOrder = 0) else it
        }
        // Clear from music
        val currentMusic = _libraryState.value.music.map {
            if (it.seriesId == seriesId) it.copy(seriesId = null, seriesOrder = 0) else it
        }
        // Clear from comics
        val currentComics = _libraryState.value.comics.map {
            if (it.seriesId == seriesId) it.copy(seriesId = null, seriesOrder = 0) else it
        }
        // Clear from movies
        val currentMovies = _libraryState.value.movies.map {
            if (it.seriesId == seriesId) it.copy(seriesId = null, seriesOrder = 0) else it
        }

        _libraryState.value = _libraryState.value.copy(
            audiobooks = currentAudiobooks,
            books = currentBooks,
            music = currentMusic,
            comics = currentComics,
            movies = currentMovies
        )
        saveLibrary()
    }

    /**
     * Save series to repository
     */
    private fun saveSeries() {
        kotlinx.coroutines.runBlocking {
            repository?.saveSeries(_libraryState.value.series)
        }
    }

    /**
     * Get items grouped by series for the current content type
     * Returns a map of series (or null for unassigned) to list of items
     */
    fun getItemsGroupedBySeries(): Map<LibrarySeries?, List<Any>> {
        val contentType = _libraryState.value.selectedContentType
        val series = getSeriesForCurrentContentType()

        return when (contentType) {
            ContentType.AUDIOBOOK -> {
                val filtered = getFilteredAudiobooks()
                val result = mutableMapOf<LibrarySeries?, List<Any>>()

                // Items without series (unassigned)
                val unassigned = filtered.filter { it.seriesId == null }
                if (unassigned.isNotEmpty()) {
                    result[null] = unassigned
                }

                // Items by series
                series.forEach { s ->
                    val items = filtered.filter { it.seriesId == s.id }.sortedBy { it.seriesOrder }
                    if (items.isNotEmpty()) {
                        result[s] = items
                    }
                }
                result
            }
            ContentType.EBOOK -> {
                val filtered = getFilteredBooks()
                val result = mutableMapOf<LibrarySeries?, List<Any>>()

                val unassigned = filtered.filter { it.seriesId == null }
                if (unassigned.isNotEmpty()) {
                    result[null] = unassigned
                }

                series.forEach { s ->
                    val items = filtered.filter { it.seriesId == s.id }.sortedBy { it.seriesOrder }
                    if (items.isNotEmpty()) {
                        result[s] = items
                    }
                }
                result
            }
            ContentType.MUSIC -> {
                val filtered = getFilteredMusic()
                val result = mutableMapOf<LibrarySeries?, List<Any>>()

                val unassigned = filtered.filter { it.seriesId == null }
                if (unassigned.isNotEmpty()) {
                    result[null] = unassigned
                }

                series.forEach { s ->
                    val items = filtered.filter { it.seriesId == s.id }.sortedBy { it.seriesOrder }
                    if (items.isNotEmpty()) {
                        result[s] = items
                    }
                }
                result
            }
            ContentType.CREEPYPASTA -> {
                val filtered = getFilteredMusic()
                val result = mutableMapOf<LibrarySeries?, List<Any>>()

                val unassigned = filtered.filter { it.seriesId == null }
                if (unassigned.isNotEmpty()) {
                    result[null] = unassigned
                }

                series.forEach { s ->
                    val items = filtered.filter { it.seriesId == s.id }.sortedBy { it.seriesOrder }
                    if (items.isNotEmpty()) {
                        result[s] = items
                    }
                }
                result
            }
            ContentType.COMICS -> {
                val filtered = getFilteredComics()
                val result = mutableMapOf<LibrarySeries?, List<Any>>()

                val unassigned = filtered.filter { it.seriesId == null }
                if (unassigned.isNotEmpty()) {
                    result[null] = unassigned
                }

                series.forEach { s ->
                    val items = filtered.filter { it.seriesId == s.id }.sortedBy { it.seriesOrder }
                    if (items.isNotEmpty()) {
                        result[s] = items
                    }
                }
                result
            }
            ContentType.MOVIE -> {
                val filtered = getFilteredMovies()
                val result = mutableMapOf<LibrarySeries?, List<Any>>()

                val unassigned = filtered.filter { it.seriesId == null }
                if (unassigned.isNotEmpty()) {
                    result[null] = unassigned
                }

                series.forEach { s ->
                    val items = filtered.filter { it.seriesId == s.id }.sortedBy { it.seriesOrder }
                    if (items.isNotEmpty()) {
                        result[s] = items
                    }
                }
                result
            }
        }
    }
}
