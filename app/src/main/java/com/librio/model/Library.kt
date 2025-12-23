package com.librio.model

import android.graphics.Bitmap
import android.net.Uri

/**
 * Types of content in the library
 * Order: Audiobooks, E Books, Comics, Music, Movies
 */
enum class ContentType(val displayName: String, val folderName: String) {
    AUDIOBOOK("Audiobooks", "Audiobooks"),
    EBOOK("E Books", "Books"),
    COMICS("Comics", "Comics"),
    MUSIC("Music", "Music"),
    CREEPYPASTA("Creepypasta", "Creepypasta"),
    MOVIE("Movies", "Movies")
}

/**
 * Represents a category/folder for organizing content
 */
data class Category(
    val id: String,
    val name: String,
    val dateCreated: Long = System.currentTimeMillis()
)

/**
 * Represents a series divider for grouping related content (e.g., book series)
 */
data class LibrarySeries(
    val id: String,
    val name: String,
    val contentType: ContentType,
    val categoryId: String? = null, // Optional: associate series with a category
    val order: Int = 0, // Display order
    val dateCreated: Long = System.currentTimeMillis(),
    val coverArtUri: String? = null // Custom cover art URI for the series/playlist
)

/**
 * Represents an e-book in the library
 */
data class LibraryBook(
    val id: String,
    val uri: Uri,
    val title: String,
    val author: String = "Unknown Author",
    val coverArt: Bitmap? = null,
    val coverArtUri: String? = null, // Custom cover art URI
    val totalPages: Int = 0,
    val currentPage: Int = 0,
    val lastRead: Long = 0L,
    val dateAdded: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false,
    val categoryId: String? = null,
    val seriesId: String? = null, // Series this book belongs to
    val seriesOrder: Int = 0, // Order within the series
    val fileType: String = "pdf" // pdf, epub, txt
) {
    val progress: Float
        get() = if (totalPages > 0) (currentPage.toFloat() / totalPages).coerceIn(0f, 1f) else 0f

    val remainingPages: Int
        get() = (totalPages - currentPage).coerceAtLeast(0)
}

/**
 * Represents an audiobook in the library with persistent metadata
 */
data class LibraryAudiobook(
    val id: String,
    val uri: Uri,
    val title: String,
    val author: String = "Unknown Author",
    val narrator: String? = null,
    val coverArtUri: Uri? = null,
    val coverArt: Bitmap? = null,
    val duration: Long = 0L,
    val lastPosition: Long = 0L,
    val lastPlayed: Long = 0L,
    val dateAdded: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false,
    val categoryId: String? = null,
    val seriesId: String? = null, // Series this audiobook belongs to
    val seriesOrder: Int = 0, // Order within the series
    val fileType: String = "mp3" // mp3, m4a, m4b, ogg, flac, etc.
) {
    val progress: Float
        get() = if (duration > 0) (lastPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f

    val remainingTime: Long
        get() = (duration - lastPosition).coerceAtLeast(0)

    val formattedDuration: String
        get() = formatDuration(duration)

    val formattedRemaining: String
        get() = formatDuration(remainingTime)

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return if (hours > 0) {
            "${hours}h ${minutes}m"
        } else {
            "${minutes}m"
        }
    }
}

/**
 * Represents a music track in the library
 */
data class LibraryMusic(
    val id: String,
    val uri: Uri,
    val title: String,
    val artist: String = "Unknown Artist",
    val album: String? = null,
    val coverArt: Bitmap? = null,
    val coverArtUri: String? = null, // Custom cover art URI
    val duration: Long = 0L,
    val lastPosition: Long = 0L,
    val lastPlayed: Long = 0L,
    val dateAdded: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false,
    val categoryId: String? = null,
    val seriesId: String? = null, // Series/playlist this track belongs to
    val seriesOrder: Int = 0, // Order within the series
    val fileType: String = "mp3",
    val timesListened: Int = 0,
    val contentType: ContentType = ContentType.MUSIC
) {
    val progress: Float
        get() = if (duration > 0) (lastPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f

    val remainingTime: Long
        get() = (duration - lastPosition).coerceAtLeast(0)

    val formattedDuration: String
        get() = formatDuration(duration)

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "${minutes}:${seconds.toString().padStart(2, '0')}"
    }
}

/**
 * Represents a comic in the library
 */
data class LibraryComic(
    val id: String,
    val uri: Uri,
    val title: String,
    val author: String = "Unknown Author",
    val series: String? = null,
    val coverArt: Bitmap? = null,
    val coverArtUri: String? = null, // Custom cover art URI
    val totalPages: Int = 0,
    val currentPage: Int = 0,
    val lastRead: Long = 0L,
    val dateAdded: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false,
    val categoryId: String? = null,
    val seriesId: String? = null, // Series this comic belongs to
    val seriesOrder: Int = 0, // Order within the series
    val fileType: String = "cbz" // cbz, cbr, cb7, pdf
) {
    val progress: Float
        get() = if (totalPages > 0) (currentPage.toFloat() / totalPages).coerceIn(0f, 1f) else 0f

    val remainingPages: Int
        get() = (totalPages - currentPage).coerceAtLeast(0)
}

/**
 * Represents a movie in the library
 */
data class LibraryMovie(
    val id: String,
    val uri: Uri,
    val title: String,
    val duration: Long = 0L,
    val lastPosition: Long = 0L,
    val lastPlayed: Long = 0L,
    val dateAdded: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false,
    val categoryId: String? = null,
    val seriesId: String? = null, // Series this movie belongs to
    val seriesOrder: Int = 0, // Order within the series
    val thumbnailUri: Uri? = null,
    val coverArt: Bitmap? = null,
    val coverArtUri: String? = null, // Custom cover art URI
    val fileType: String = "mp4" // mp4, mkv, avi, webm
) {
    val progress: Float
        get() = if (duration > 0) (lastPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f

    val remainingTime: Long
        get() = (duration - lastPosition).coerceAtLeast(0)

    val formattedDuration: String
        get() = formatDuration(duration)

    val formattedRemaining: String
        get() = formatDuration(remainingTime)

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        } else {
            "${minutes}:${seconds.toString().padStart(2, '0')}"
        }
    }
}

/**
 * Library state
 */
data class LibraryState(
    val audiobooks: List<LibraryAudiobook> = emptyList(),
    val books: List<LibraryBook> = emptyList(),
    val music: List<LibraryMusic> = emptyList(),
    val comics: List<LibraryComic> = emptyList(),
    val movies: List<LibraryMovie> = emptyList(),
    val categories: List<Category> = emptyList(),
    val series: List<LibrarySeries> = emptyList(), // Series dividers
    // Per-content-type category filters
    val selectedAudiobookCategoryId: String? = null,
    val selectedBookCategoryId: String? = null,
    val selectedMusicCategoryId: String? = null,
    val selectedCreepypastaCategoryId: String? = null,
    val selectedComicCategoryId: String? = null,
    val selectedMovieCategoryId: String? = null,
    val selectedContentType: ContentType = ContentType.AUDIOBOOK,
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    // Helper to get the selected category ID for the current content type
    val selectedCategoryId: String?
        get() = when (selectedContentType) {
            ContentType.AUDIOBOOK -> selectedAudiobookCategoryId
            ContentType.EBOOK -> selectedBookCategoryId
            ContentType.MUSIC -> selectedMusicCategoryId
            ContentType.CREEPYPASTA -> selectedCreepypastaCategoryId
            ContentType.COMICS -> selectedComicCategoryId
            ContentType.MOVIE -> selectedMovieCategoryId
        }
}

/**
 * Sort options for the library
 */
enum class SortOption(val displayName: String) {
    RECENTLY_ADDED("Recently Added"),
    RECENTLY_PLAYED("Recently Played"),
    TITLE_AZ("Title A-Z"),
    TITLE_ZA("Title Z-A"),
    AUTHOR_AZ("Author A-Z"),
    PROGRESS("Progress"),
    BOOK_NUMBER("Book Number")
}
