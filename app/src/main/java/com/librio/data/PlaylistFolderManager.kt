package com.librio.data

import android.os.Environment
import com.librio.model.ContentType
import com.librio.model.DiscoveredPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages playlist folders within profile content directories
 * Handles discovery, creation, renaming, and deletion of playlist folders
 */
class PlaylistFolderManager {

    private val librioRoot = File(Environment.getExternalStorageDirectory(), "Librio")
    private val profilesRoot = File(librioRoot, "Profiles")

    /**
     * Get the profile folder for a given profile name
     */
    fun getProfileFolder(profileName: String): File {
        return File(profilesRoot, sanitizeFolderName(profileName))
    }

    /**
     * Get the content type folder for a profile
     */
    fun getContentFolder(profileName: String, contentType: ContentType): File {
        return File(getProfileFolder(profileName), contentType.folderName)
    }

    /**
     * Sanitize folder name to be filesystem safe
     */
    fun sanitizeFolderName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }

    /**
     * Discover all playlist folders within a content type directory
     * A playlist folder is any subdirectory within the content type folder
     */
    suspend fun discoverPlaylistFolders(
        profileName: String,
        contentType: ContentType
    ): List<DiscoveredPlaylist> = withContext(Dispatchers.IO) {
        val contentFolder = getContentFolder(profileName, contentType)

        if (!contentFolder.exists()) {
            contentFolder.mkdirs()
            return@withContext emptyList()
        }

        contentFolder.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.map { folder ->
                DiscoveredPlaylist(
                    folderName = folder.name,
                    folderPath = folder.absolutePath,
                    contentType = contentType,
                    mediaFileCount = countMediaFiles(folder, contentType),
                    dateCreated = folder.lastModified()
                )
            }
            ?.sortedBy { it.folderName.lowercase() }
            ?.toList()
            ?: emptyList()
    }

    /**
     * Discover all playlist folders across all content types for a profile
     */
    suspend fun discoverAllPlaylistFolders(profileName: String): Map<ContentType, List<DiscoveredPlaylist>> =
        withContext(Dispatchers.IO) {
            ContentType.entries.associateWith { contentType ->
                discoverPlaylistFolders(profileName, contentType)
            }
        }

    /**
     * Create a new playlist folder
     */
    suspend fun createPlaylistFolder(
        profileName: String,
        contentType: ContentType,
        playlistName: String
    ): File? = withContext(Dispatchers.IO) {
        val sanitizedName = sanitizeFolderName(playlistName)
        if (sanitizedName.isEmpty()) return@withContext null

        val folder = File(getContentFolder(profileName, contentType), sanitizedName)

        try {
            if (folder.exists()) {
                // Folder already exists
                return@withContext folder
            }
            if (folder.mkdirs()) {
                folder
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Rename a playlist folder
     */
    suspend fun renamePlaylistFolder(
        profileName: String,
        contentType: ContentType,
        oldName: String,
        newName: String
    ): File? = withContext(Dispatchers.IO) {
        val sanitizedOldName = sanitizeFolderName(oldName)
        val sanitizedNewName = sanitizeFolderName(newName)

        if (sanitizedNewName.isEmpty()) return@withContext null

        val oldFolder = File(getContentFolder(profileName, contentType), sanitizedOldName)
        val newFolder = File(getContentFolder(profileName, contentType), sanitizedNewName)

        try {
            if (!oldFolder.exists()) {
                // Old folder doesn't exist, just create new one
                if (newFolder.mkdirs()) newFolder else null
            } else if (newFolder.exists()) {
                // Target folder already exists
                null
            } else if (oldFolder.renameTo(newFolder)) {
                newFolder
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Delete a playlist folder
     * @param deleteContents If true, recursively deletes all contents. If false, only deletes if empty.
     */
    suspend fun deletePlaylistFolder(
        profileName: String,
        contentType: ContentType,
        playlistName: String,
        deleteContents: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val sanitizedName = sanitizeFolderName(playlistName)
        val folder = File(getContentFolder(profileName, contentType), sanitizedName)

        try {
            if (!folder.exists()) return@withContext true

            if (deleteContents) {
                folder.deleteRecursively()
            } else {
                folder.delete() // Only succeeds if empty
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Get all media files within a playlist folder
     */
    suspend fun getPlaylistMediaFiles(
        profileName: String,
        contentType: ContentType,
        playlistName: String
    ): List<File> = withContext(Dispatchers.IO) {
        val sanitizedName = sanitizeFolderName(playlistName)
        val folder = File(getContentFolder(profileName, contentType), sanitizedName)
        getMediaFilesInFolder(folder, contentType)
    }

    /**
     * Get all media files directly in a folder (not in subfolders)
     */
    private fun getMediaFilesInFolder(folder: File, contentType: ContentType): List<File> {
        if (!folder.exists()) return emptyList()

        val extensions = getMediaExtensions(contentType)
        return folder.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && extensions.contains(it.extension.lowercase()) }
            ?.sortedBy { it.name.lowercase() }
            ?.toList()
            ?: emptyList()
    }

    /**
     * Move a file to a different playlist folder
     */
    suspend fun moveFileToPlaylist(
        sourceFile: File,
        profileName: String,
        contentType: ContentType,
        targetPlaylistName: String
    ): File? = withContext(Dispatchers.IO) {
        if (!sourceFile.exists()) return@withContext null

        val targetFolder = File(getContentFolder(profileName, contentType), sanitizeFolderName(targetPlaylistName))
        if (!targetFolder.exists()) {
            targetFolder.mkdirs()
        }

        val targetFile = File(targetFolder, sourceFile.name)

        try {
            // If target file already exists, generate a unique name
            val finalTarget = if (targetFile.exists()) {
                generateUniqueFileName(targetFolder, sourceFile.name)
            } else {
                targetFile
            }

            if (sourceFile.renameTo(finalTarget)) {
                finalTarget
            } else {
                // If rename fails (cross-filesystem), try copy and delete
                sourceFile.copyTo(finalTarget, overwrite = false)
                sourceFile.delete()
                finalTarget
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Copy a file to a playlist folder
     */
    suspend fun copyFileToPlaylist(
        sourceFile: File,
        profileName: String,
        contentType: ContentType,
        targetPlaylistName: String
    ): File? = withContext(Dispatchers.IO) {
        if (!sourceFile.exists()) return@withContext null

        val targetFolder = File(getContentFolder(profileName, contentType), sanitizeFolderName(targetPlaylistName))
        if (!targetFolder.exists()) {
            targetFolder.mkdirs()
        }

        val targetFile = File(targetFolder, sourceFile.name)

        try {
            val finalTarget = if (targetFile.exists()) {
                generateUniqueFileName(targetFolder, sourceFile.name)
            } else {
                targetFile
            }

            sourceFile.copyTo(finalTarget, overwrite = false)
            finalTarget
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get files in the root content folder (not in any playlist subfolder)
     */
    suspend fun getRootMediaFiles(
        profileName: String,
        contentType: ContentType
    ): List<File> = withContext(Dispatchers.IO) {
        val contentFolder = getContentFolder(profileName, contentType)
        getMediaFilesInFolder(contentFolder, contentType)
    }

    /**
     * Check if a playlist folder exists
     */
    fun playlistFolderExists(
        profileName: String,
        contentType: ContentType,
        playlistName: String
    ): Boolean {
        val folder = File(getContentFolder(profileName, contentType), sanitizeFolderName(playlistName))
        return folder.exists() && folder.isDirectory
    }

    /**
     * Get the path to a playlist folder
     */
    fun getPlaylistFolderPath(
        profileName: String,
        contentType: ContentType,
        playlistName: String
    ): String {
        return File(getContentFolder(profileName, contentType), sanitizeFolderName(playlistName)).absolutePath
    }

    /**
     * Count media files in a folder
     */
    private fun countMediaFiles(folder: File, contentType: ContentType): Int {
        val extensions = getMediaExtensions(contentType)
        return folder.listFiles()?.count {
            it.isFile && extensions.contains(it.extension.lowercase())
        } ?: 0
    }

    /**
     * Generate a unique filename if file already exists.
     * Strips existing _N suffixes to prevent _1_1 patterns.
     */
    private fun generateUniqueFileName(folder: File, originalName: String): File {
        val rawBaseName = originalName.substringBeforeLast(".")
        val extension = originalName.substringAfterLast(".", "")

        // Strip existing _N suffix pattern to get clean base name
        val suffixPattern = Regex("_\\d+$")
        val cleanBaseName = rawBaseName.replace(suffixPattern, "")

        // First try the clean name without any suffix
        val cleanFileName = if (extension.isNotEmpty()) "$cleanBaseName.$extension" else cleanBaseName
        var newFile = File(folder, cleanFileName)

        if (!newFile.exists()) {
            return newFile
        }

        // Find the highest existing counter for this base name
        val existingPattern = Regex("${Regex.escape(cleanBaseName)}(?:_(\\d+))?\\.${Regex.escape(extension)}$")
        val existingCounters = folder.listFiles()?.mapNotNull { file ->
            existingPattern.matchEntire(file.name)?.let { match ->
                match.groupValues[1].toIntOrNull() ?: 0
            }
        } ?: emptyList()

        var counter = (existingCounters.maxOrNull() ?: 0) + 1

        val newName = if (extension.isNotEmpty()) {
            "${cleanBaseName}_$counter.$extension"
        } else {
            "${cleanBaseName}_$counter"
        }
        newFile = File(folder, newName)

        // Safety fallback in case of edge cases
        while (newFile.exists()) {
            counter++
            val fallbackName = if (extension.isNotEmpty()) {
                "${cleanBaseName}_$counter.$extension"
            } else {
                "${cleanBaseName}_$counter"
            }
            newFile = File(folder, fallbackName)
        }

        return newFile
    }

    /**
     * Get supported media extensions for a content type
     */
    private fun getMediaExtensions(contentType: ContentType): Set<String> {
        return when (contentType) {
            ContentType.AUDIOBOOK -> setOf("mp3", "m4a", "m4b", "ogg", "flac", "wav", "aac", "wma", "opus")
            ContentType.EBOOK -> setOf("pdf", "epub", "txt", "mobi", "azw", "azw3", "djvu", "fb2")
            ContentType.MUSIC -> setOf("mp3", "m4a", "flac", "ogg", "wav", "aac", "wma", "opus", "aiff")
            ContentType.CREEPYPASTA -> setOf("mp3", "m4a", "flac", "ogg", "wav", "aac", "wma", "opus", "aiff", "webm", "mp4", "mkv", "m4v")
            ContentType.COMICS -> setOf("cbz", "cbr", "cb7", "cbt", "pdf")
            ContentType.MOVIE -> setOf("mp4", "mkv", "avi", "webm", "mov", "wmv", "flv", "m4v", "3gp")
        }
    }

    /**
     * Ensure content folders exist for a profile
     */
    suspend fun ensureContentFoldersExist(profileName: String) = withContext(Dispatchers.IO) {
        ContentType.entries.forEach { contentType ->
            val folder = getContentFolder(profileName, contentType)
            if (!folder.exists()) {
                folder.mkdirs()
            }
        }
    }

    /**
     * Remove a file from a playlist folder (delete from disk)
     */
    suspend fun removeFileFromPlaylist(
        file: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (file.exists()) {
                file.delete()
            } else {
                true // File already doesn't exist
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Move file back to root content folder (out of playlist)
     */
    suspend fun moveFileToRoot(
        sourceFile: File,
        profileName: String,
        contentType: ContentType
    ): File? = withContext(Dispatchers.IO) {
        if (!sourceFile.exists()) return@withContext null

        val rootFolder = getContentFolder(profileName, contentType)
        if (!rootFolder.exists()) {
            rootFolder.mkdirs()
        }

        val targetFile = File(rootFolder, sourceFile.name)

        try {
            val finalTarget = if (targetFile.exists()) {
                generateUniqueFileName(rootFolder, sourceFile.name)
            } else {
                targetFile
            }

            if (sourceFile.renameTo(finalTarget)) {
                finalTarget
            } else {
                // If rename fails (cross-filesystem), try copy and delete
                sourceFile.copyTo(finalTarget, overwrite = false)
                sourceFile.delete()
                finalTarget
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Sync files: Move all files from a source folder to a playlist folder
     * Used when assigning items to a playlist
     */
    suspend fun syncFilesToPlaylist(
        sourceFile: File,
        profileName: String,
        contentType: ContentType,
        targetPlaylistName: String
    ): File? {
        return moveFileToPlaylist(sourceFile, profileName, contentType, targetPlaylistName)
    }

    /**
     * Get all files in a folder (for syncing)
     */
    suspend fun getAllFilesInFolder(folder: File): List<File> = withContext(Dispatchers.IO) {
        if (!folder.exists() || !folder.isDirectory) return@withContext emptyList()

        folder.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && !it.name.startsWith(".") }
            ?.sortedBy { it.name.lowercase() }
            ?.toList()
            ?: emptyList()
    }

    /**
     * Check if a file is in a playlist folder
     */
    fun isFileInPlaylist(
        filePath: String,
        profileName: String,
        contentType: ContentType,
        playlistName: String
    ): Boolean {
        val playlistFolder = File(getContentFolder(profileName, contentType), sanitizeFolderName(playlistName))
        return filePath.startsWith(playlistFolder.absolutePath)
    }

    /**
     * Get the playlist name from a file path, if any
     */
    fun getPlaylistNameFromPath(
        filePath: String,
        profileName: String,
        contentType: ContentType
    ): String? {
        val contentFolder = getContentFolder(profileName, contentType)
        if (!filePath.startsWith(contentFolder.absolutePath)) return null

        val relativePath = filePath.removePrefix(contentFolder.absolutePath).removePrefix("/").removePrefix("\\")
        val firstSlash = relativePath.indexOfFirst { it == '/' || it == '\\' }

        return if (firstSlash > 0) {
            relativePath.substring(0, firstSlash)
        } else {
            null // File is in root folder, not a playlist
        }
    }

    /**
     * Detect folder renames by comparing known series names with current folders
     * Returns a map of oldName -> newName for any detected renames
     */
    suspend fun detectFolderRenames(
        profileName: String,
        contentType: ContentType,
        knownSeriesNames: List<String>
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val contentFolder = getContentFolder(profileName, contentType)
        if (!contentFolder.exists()) return@withContext emptyMap()

        val currentFolders = contentFolder.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.map { it.name }
            ?.toSet()
            ?: return@withContext emptyMap()

        // Series names that don't have matching folders
        val missingSeries = knownSeriesNames.filter { seriesName ->
            !currentFolders.any { it.equals(seriesName, ignoreCase = true) }
        }

        // Folders that don't match any known series
        val unknownFolders = currentFolders.filter { folderName ->
            !knownSeriesNames.any { it.equals(folderName, ignoreCase = true) }
        }

        // Try to match missing series to unknown folders by content
        val renames = mutableMapOf<String, String>()

        // Simple heuristic: if there's exactly one missing series and one unknown folder
        // with the same number of files, assume it's a rename
        // For more complex cases, we rely on the sync process to create new series
        if (missingSeries.size == 1 && unknownFolders.size == 1) {
            val oldName = missingSeries.first()
            val newName = unknownFolders.first()
            renames[oldName] = newName
        }

        renames
    }
}
