package com.librio.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.librio.data.repository.LibraryRepository
import com.librio.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Comprehensive backup manager for Librio profiles.
 *
 * Features:
 * - ZIP-based backups with compression
 * - Includes all settings, progress, playlists, and library data
 * - Embeds cover art images (no broken URIs on restore)
 * - Simple drop-to-restore: just place .librio-backup.zip in Profiles folder
 * - Backward compatible with old .librio-profile.json backups
 * - Versioned backup format for future compatibility
 *
 * Backup Structure:
 * ```
 * ProfileName.librio-backup.zip
 * ├── manifest.json             # Backup metadata and version
 * ├── settings/
 * │   ├── profile_settings.json
 * │   ├── audio_settings.json
 * │   ├── reader_settings.json
 * │   ├── comic_settings.json
 * │   └── movie_settings.json
 * ├── data/
 * │   ├── library.json
 * │   ├── progress.json
 * │   └── playlists/
 * │       └── *.json
 * └── cover_art/
 *     └── [item_id].jpg
 * ```
 */
class BackupManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val libraryRepository: LibraryRepository
) {
    companion object {
        const val BACKUP_VERSION = 3 // Version 3: ZIP format with cover art
        const val BACKUP_EXTENSION = ".librio-backup.zip"
        const val LEGACY_EXTENSION = ".librio-profile.json"
        private const val MANIFEST_FILE = "manifest.json"
        private const val MAX_COVER_ART_SIZE = 1024 // Max dimension for cover art in backup
    }

    /**
     * Create a comprehensive backup of a profile.
     * Returns the backup file or null if failed.
     */
    suspend fun createBackup(
        profileId: String,
        includeTimestamp: Boolean = true
    ): File? = withContext(Dispatchers.IO) {
        try {
            val profile = settingsRepository.profiles.value.find { it.id == profileId }
                ?: return@withContext null

            val profileName = profile.name
            val timestamp = if (includeTimestamp) {
                "_${System.currentTimeMillis()}"
            } else ""

            val backupDir = File(settingsRepository.getLibrioRoot(), "Profiles/Backups")
            backupDir.mkdirs()

            val backupFile = File(backupDir, "${profileName}${timestamp}${BACKUP_EXTENSION}")

            // Create ZIP backup
            ZipOutputStream(FileOutputStream(backupFile)).use { zipOut ->
                // 1. Add manifest
                addManifest(zipOut, profileId, profileName)

                // 2. Add all settings files
                addSettings(zipOut, profileId)

                // 3. Add library and progress data
                addLibraryData(zipOut, profileId)

                // 4. Add playlists
                addPlaylists(zipOut, profileId)

                // 5. Add cover art images
                addCoverArt(zipOut, profileId)
            }

            backupFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Import a backup file (supports both new ZIP and legacy JSON formats).
     * Returns true if successful.
     */
    suspend fun importBackup(backupFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            when {
                backupFile.name.endsWith(BACKUP_EXTENSION) -> importZipBackup(backupFile)
                backupFile.name.endsWith(LEGACY_EXTENSION) -> importLegacyBackup(backupFile)
                else -> false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Auto-detect and import all backup files in the Profiles folder.
     * This enables the "drop backup file to restore" functionality.
     */
    suspend fun autoImportPendingBackups() = withContext(Dispatchers.IO) {
        try {
            val profilesDir = File(settingsRepository.getLibrioRoot(), "Profiles")
            if (!profilesDir.exists()) return@withContext

            // Find all backup files in root Profiles folder
            val backupFiles = profilesDir.listFiles()?.filter { file ->
                file.isFile && (
                    file.name.endsWith(BACKUP_EXTENSION) ||
                    file.name.endsWith(LEGACY_EXTENSION)
                )
            } ?: emptyList()

            // Import each backup
            backupFiles.forEach { backupFile ->
                val success = importBackup(backupFile)

                if (success) {
                    // Move to Imported folder after successful import
                    val importedDir = File(profilesDir, "Backups/Imported")
                    importedDir.mkdirs()
                    backupFile.renameTo(File(importedDir, backupFile.name))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ========== Private Helper Methods ==========

    private fun addManifest(zipOut: ZipOutputStream, profileId: String, profileName: String) {
        val manifest = JSONObject().apply {
            put("version", BACKUP_VERSION)
            put("profileId", profileId)
            put("profileName", profileName)
            put("timestamp", System.currentTimeMillis())
            put("app", "Librio")
            put("format", "zip")
        }

        zipOut.putNextEntry(ZipEntry(MANIFEST_FILE))
        zipOut.write(manifest.toString(2).toByteArray())
        zipOut.closeEntry()
    }

    private suspend fun addSettings(zipOut: ZipOutputStream, profileId: String) {
        val settingsFiles = listOf(
            "profile_settings.json",
            "audio_settings.json",
            "reader_settings.json",
            "comic_settings.json",
            "movie_settings.json"
        )

        val profileDir = File(settingsRepository.getLibrioRoot(), "Profiles/$profileId")

        settingsFiles.forEach { filename ->
            val file = File(profileDir, filename)
            if (file.exists()) {
                zipOut.putNextEntry(ZipEntry("settings/$filename"))
                FileInputStream(file).use { input ->
                    input.copyTo(zipOut)
                }
                zipOut.closeEntry()
            }
        }
    }

    private suspend fun addLibraryData(zipOut: ZipOutputStream, profileId: String) {
        val profileDir = File(settingsRepository.getLibrioRoot(), "Profiles/$profileId")

        // Add library.json
        val libraryFile = File(profileDir, "library.json")
        if (libraryFile.exists()) {
            zipOut.putNextEntry(ZipEntry("data/library.json"))
            FileInputStream(libraryFile).use { it.copyTo(zipOut) }
            zipOut.closeEntry()
        }

        // Add progress.json
        val progressFile = File(profileDir, "progress.json")
        if (progressFile.exists()) {
            zipOut.putNextEntry(ZipEntry("data/progress.json"))
            FileInputStream(progressFile).use { it.copyTo(zipOut) }
            zipOut.closeEntry()
        }
    }

    private suspend fun addPlaylists(zipOut: ZipOutputStream, profileId: String) {
        val playlistsDir = File(settingsRepository.getLibrioRoot(), "Profiles/$profileId/Playlists")
        if (!playlistsDir.exists()) return

        playlistsDir.listFiles()?.filter { it.isFile && it.extension == "json" }?.forEach { file ->
            zipOut.putNextEntry(ZipEntry("data/playlists/${file.name}"))
            FileInputStream(file).use { it.copyTo(zipOut) }
            zipOut.closeEntry()
        }
    }

    private suspend fun addCoverArt(zipOut: ZipOutputStream, profileId: String) {
        // Load library to get all cover art URIs
        libraryRepository.setCurrentProfile(profileId)

        val audiobooks = libraryRepository.loadLibrary()
        val books = libraryRepository.loadBooks()
        val music = libraryRepository.loadMusic()
        val comics = libraryRepository.loadComics()
        val movies = libraryRepository.loadMovies()
        val series = libraryRepository.loadSeries()

        // Helper to extract and add cover art
        suspend fun addCoverArtFromUri(uri: Uri?, itemId: String, type: String) {
            if (uri == null) return

            try {
                val bitmap = when {
                    uri.scheme == "file" -> {
                        val file = File(uri.path ?: return)
                        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                    }
                    uri.scheme == "content" -> {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            BitmapFactory.decodeStream(input)
                        }
                    }
                    else -> null
                }

                if (bitmap != null) {
                    // Resize if needed to keep backup size reasonable
                    val resized = if (bitmap.width > MAX_COVER_ART_SIZE || bitmap.height > MAX_COVER_ART_SIZE) {
                        val scale = MAX_COVER_ART_SIZE.toFloat() / maxOf(bitmap.width, bitmap.height)
                        Bitmap.createScaledBitmap(
                            bitmap,
                            (bitmap.width * scale).toInt(),
                            (bitmap.height * scale).toInt(),
                            true
                        )
                    } else bitmap

                    // Save as JPEG in ZIP
                    zipOut.putNextEntry(ZipEntry("cover_art/${type}_${itemId}.jpg"))
                    resized.compress(Bitmap.CompressFormat.JPEG, 85, zipOut)
                    zipOut.closeEntry()

                    if (resized != bitmap) resized.recycle()
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                // Skip this cover art if extraction fails
                e.printStackTrace()
            }
        }

        // Add cover art for all content types
        audiobooks.forEach { addCoverArtFromUri(it.coverArtUri, it.id, "audiobook") }
        books.forEach { addCoverArtFromUri(it.coverArtUri?.let { Uri.parse(it) }, it.id, "book") }
        music.forEach { addCoverArtFromUri(it.coverArtUri?.let { Uri.parse(it) }, it.id, "music") }
        comics.forEach { addCoverArtFromUri(it.coverArtUri?.let { Uri.parse(it) }, it.id, "comic") }
        movies.forEach { addCoverArtFromUri(it.coverArtUri?.let { Uri.parse(it) }, it.id, "movie") }
        series.forEach { addCoverArtFromUri(it.coverArtUri?.let { Uri.parse(it) }, it.id, "series") }
    }

    private suspend fun importZipBackup(backupFile: File): Boolean {
        try {
            // Parse manifest first
            var profileId: String? = null
            var profileName: String? = null

            ZipInputStream(FileInputStream(backupFile)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    if (entry.name == MANIFEST_FILE) {
                        val manifestJson = zipIn.readBytes().decodeToString()
                        val manifest = JSONObject(manifestJson)
                        profileId = manifest.optString("profileId").takeIf { it.isNotEmpty() }
                        profileName = manifest.optString("profileName").takeIf { it.isNotEmpty() }
                        break
                    }
                    entry = zipIn.nextEntry
                }
            }

            // Validate manifest data (optString can return empty string, not null)
            if (profileId.isNullOrEmpty() || profileName.isNullOrEmpty()) {
                return false
            }

            // Check if profile exists or create new one
            val existingProfile = settingsRepository.profiles.value.find {
                it.id == profileId || it.name == profileName
            }

            val targetProfileId = existingProfile?.id ?: profileId
            val profileDir = File(settingsRepository.getLibrioRoot(), "Profiles/$targetProfileId")
            profileDir.mkdirs()

            // Extract all files from ZIP
            ZipInputStream(FileInputStream(backupFile)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name != MANIFEST_FILE) {
                        val targetFile = when {
                            entry.name.startsWith("settings/") -> {
                                File(profileDir, entry.name.removePrefix("settings/"))
                            }
                            entry.name.startsWith("data/playlists/") -> {
                                val playlistsDir = File(profileDir, "Playlists")
                                playlistsDir.mkdirs()
                                File(playlistsDir, entry.name.removePrefix("data/playlists/"))
                            }
                            entry.name.startsWith("data/") -> {
                                File(profileDir, entry.name.removePrefix("data/"))
                            }
                            entry.name.startsWith("cover_art/") -> {
                                // Extract cover art to profile's cover art cache
                                val coverArtDir = File(profileDir, ".cover_art_cache")
                                coverArtDir.mkdirs()
                                File(coverArtDir, entry.name.removePrefix("cover_art/"))
                            }
                            else -> null
                        }

                        targetFile?.let { file ->
                            file.parentFile?.mkdirs()
                            FileOutputStream(file).use { output ->
                                zipIn.copyTo(output)
                            }
                        }
                    }
                    entry = zipIn.nextEntry
                }
            }

            // Update cover art URIs in library.json to point to extracted images
            updateCoverArtUris(profileDir)

            // Reload settings if this is the active profile
            settingsRepository.reloadProfiles()

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private suspend fun updateCoverArtUris(profileDir: File) {
        try {
            val libraryFile = File(profileDir, "library.json")
            if (!libraryFile.exists()) return

            val coverArtDir = File(profileDir, ".cover_art_cache")
            if (!coverArtDir.exists()) return

            // Parse library.json as JSON to safely update URIs
            val libraryJson = libraryFile.readText()
            val library = JSONObject(libraryJson)

            // Create mapping of item IDs to new cover art file paths
            val coverArtMap = mutableMapOf<String, String>()
            coverArtDir.listFiles()?.forEach { coverFile ->
                val fileName = coverFile.nameWithoutExtension // e.g., "audiobook_123"
                val parts = fileName.split("_")
                if (parts.size == 2) {
                    val itemId = parts[1]
                    coverArtMap[itemId] = "file://${coverFile.absolutePath}"
                }
            }

            // Update cover art URIs in all content type arrays
            listOf("audiobooks", "books", "music", "comics", "movies", "series").forEach { contentType ->
                if (library.has(contentType)) {
                    val items = library.getJSONArray(contentType)
                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val itemId = item.optString("id")
                        if (itemId.isNotEmpty() && coverArtMap.containsKey(itemId)) {
                            item.put("coverArtUri", coverArtMap[itemId])
                        }
                    }
                }
            }

            libraryFile.writeText(library.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun importLegacyBackup(backupFile: File): Boolean {
        // Delegate to existing legacy import in SettingsRepository
        return try {
            settingsRepository.importLegacyBackup(backupFile)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
