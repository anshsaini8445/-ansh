package com.example.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import com.example.data.database.AppDatabase
import com.example.data.database.MediaDao
import com.example.data.database.MediaFileDao
import com.example.data.database.UserMediaDao
import com.example.data.model.MediaFile
import com.example.data.model.MediaItem
import com.example.data.model.UserMediaEntity
import com.example.data.scanner.DeepMediaScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class MediaRepository(
    private val context: Context,
    private val mediaDao: MediaDao,
    val userMediaDao: UserMediaDao = AppDatabase.getDatabase(context).userMediaDao(),
    val mediaFileDao: MediaFileDao = AppDatabase.getDatabase(context).mediaFileDao()
) {
    // --- Query Local Storage Media Files (100% Real Device Storage, Zero Dummy Data) ---
    suspend fun getLocalMedia(isVideo: Boolean): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()
        val resolver: ContentResolver = context.contentResolver
        val uri = if (isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = if (isVideo) {
            arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.ARTIST,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATA
            )
        } else {
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATA
            )
        }

        try {
            val cursor: Cursor? = resolver.query(
                uri,
                projection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )

            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media._ID else MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.TITLE else MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.ARTIST else MediaStore.Audio.Media.ARTIST)
                val durationCol = c.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.DURATION else MediaStore.Audio.Media.DURATION)
                val sizeCol = c.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.SIZE else MediaStore.Audio.Media.SIZE)
                val dataCol = c.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.DATA else MediaStore.Audio.Media.DATA)

                while (c.moveToNext()) {
                    val numericId = c.getLong(idCol)
                    val id = numericId.toString()
                    val title = c.getString(titleCol) ?: "Local Media"
                    val artist = c.getString(artistCol) ?: "Local Media"
                    val duration = c.getLong(durationCol)
                    val size = c.getLong(sizeCol)
                    val rawPath = c.getString(dataCol) ?: ""
                    val file = if (rawPath.isNotBlank()) File(rawPath) else null
                    val playablePath = if (file != null && file.exists() && file.canRead()) {
                        file.absolutePath
                    } else {
                        ContentUris.withAppendedId(uri, numericId).toString()
                    }
                    val parentFolder = file?.parentFile?.name ?: "Local Storage"

                    list.add(
                        MediaItem(
                            id = id,
                            title = title,
                            artist = artist,
                            duration = duration,
                            size = size,
                            path = playablePath,
                            isVideo = isVideo,
                            folderName = parentFolder
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext list
    }

    // Instant (<1ms) cold-start snapshot from DeepMediaScanner (100% real device files only)
    fun getInstantMedia(isVideo: Boolean): List<MediaItem> {
        return DeepMediaScanner.getInstantSnapshot(context, isVideo)
    }

    // Full background scan combining MediaStore, Direct Folder Scanner, and User Picked URIs
    suspend fun getAvailableMedia(isVideo: Boolean): List<MediaItem> {
        return DeepMediaScanner.scanAndSyncMedia(context, isVideo)
    }

    // --- Favorites ---
    fun getFavoritesFlow(allMediaList: List<MediaItem>): Flow<List<MediaItem>> {
        return mediaDao.getFavorites().map { favEntities ->
            val favIds = favEntities.map { it.mediaId }.toSet()
            allMediaList.filter { it.id in favIds }
        }
    }

    suspend fun addFavorite(mediaId: String, isVideo: Boolean) {
        mediaDao.addFavorite(com.example.data.model.FavoriteEntity(mediaId, isVideo))
    }

    suspend fun removeFavorite(mediaId: String) {
        mediaDao.removeFavorite(mediaId)
    }

    suspend fun isFavorite(mediaId: String): Boolean {
        return mediaDao.isFavorite(mediaId)
    }

    // --- History & Resume Playback ---
    fun getHistoryFlow(allMediaList: List<MediaItem>): Flow<List<Pair<MediaItem, Long>>> {
        return mediaDao.getHistory().map { historyEntities ->
            val historyMap = historyEntities.associateBy { it.mediaId }
            allMediaList
                .filter { it.id in historyMap.keys }
                .map { item ->
                    val lastPos = historyMap[item.id]?.lastPositionMs ?: 0L
                    item to lastPos
                }
        }
    }

    suspend fun saveProgress(mediaId: String, positionMs: Long) {
        mediaDao.saveProgress(com.example.data.model.HistoryEntity(mediaId, positionMs))
    }

    suspend fun getProgress(mediaId: String): Long {
        return mediaDao.getProgress(mediaId) ?: 0L
    }

    suspend fun deleteHistoryItem(mediaId: String) {
        mediaDao.deleteHistoryItem(mediaId)
    }

    suspend fun clearHistory() {
        mediaDao.clearHistory()
    }

    // --- Private Vault (Safe Folder) & UserMedia / MediaFile Database Sync ---
    val publicUserMediaFlow: Flow<List<UserMediaEntity>> = userMediaDao.getPublicMedia()
    val privateUserMediaFlow: Flow<List<UserMediaEntity>> = userMediaDao.getPrivateMedia()
    val allMediaFilesFlow: Flow<List<MediaFile>> = mediaFileDao.getAllMediaFiles()
    val privateMediaFilesFlow: Flow<List<MediaFile>> = mediaFileDao.getPrivateMediaFiles()

    suspend fun syncMediaLibrary(items: List<MediaItem>) = withContext(Dispatchers.IO) {
        val entities = mutableListOf<UserMediaEntity>()
        val mediaFiles = mutableListOf<MediaFile>()
        for (item in items) {
            val isPriv = userMediaDao.isItemInPrivateVault(item.id) || mediaDao.isPrivateItem(item.id) || item.isPrivate
            entities.add(UserMediaEntity.fromMediaItem(item, isPrivate = isPriv))
            mediaFiles.add(MediaFile.fromMediaItem(item, isPrivate = isPriv))
        }
        userMediaDao.insertAll(entities)
        mediaFileDao.insertAll(mediaFiles)
    }

    fun getPrivateMediaFlow(allMediaList: List<MediaItem>): Flow<List<MediaItem>> {
        return mediaDao.getPrivateItems().map { vaultEntities ->
            val vaultIds = vaultEntities.map { it.mediaId }.toSet()
            allMediaList.filter { it.id in vaultIds }
        }
    }

    suspend fun encryptToVault(mediaId: String) {
        mediaDao.encryptToVault(com.example.data.model.PrivateItemEntity(mediaId))
        userMediaDao.setPrivateStatus(mediaId, true)
        mediaFileDao.setPrivateStatus(mediaId, true)
    }

    suspend fun decryptFromVault(mediaId: String) {
        mediaDao.decryptFromVault(mediaId)
        userMediaDao.setPrivateStatus(mediaId, false)
        mediaFileDao.setPrivateStatus(mediaId, false)
    }

    suspend fun setMediaPrivateStatus(mediaId: String, isPrivate: Boolean) {
        if (isPrivate) {
            encryptToVault(mediaId)
        } else {
            decryptFromVault(mediaId)
        }
    }

    suspend fun toggleMediaPrivateStatus(mediaId: String) {
        val isPrivate = isPrivateItem(mediaId)
        setMediaPrivateStatus(mediaId, !isPrivate)
    }

    suspend fun isPrivateItem(mediaId: String): Boolean {
        val mediaFilePrivate = mediaFileDao.getMediaFileById(mediaId)?.isPrivate ?: false
        return mediaFilePrivate || userMediaDao.isItemInPrivateVault(mediaId) || mediaDao.isPrivateItem(mediaId)
    }

    // Secure PIN setup and verify
    suspend fun getVaultPinHash(): String? {
        return mediaDao.getVaultPinHash()
    }

    suspend fun setVaultPin(pinHash: String) {
        mediaDao.setVaultPin(com.example.data.model.VaultPinEntity(pinHash = pinHash))
    }

    // --- Custom Playlists ---
    fun getPlaylists(): Flow<List<com.example.data.model.PlaylistEntity>> {
        return mediaDao.getPlaylists()
    }

    suspend fun createPlaylist(name: String): Long {
        return mediaDao.createPlaylist(com.example.data.model.PlaylistEntity(name = name))
    }

    suspend fun deletePlaylist(playlistId: Long) {
        mediaDao.deletePlaylist(playlistId)
    }

    fun getPlaylistItemsFlow(playlistId: Long, allMediaList: List<MediaItem>): Flow<List<MediaItem>> {
        return mediaDao.getPlaylistItems(playlistId).map { playlistItemEntities ->
            val mediaIds = playlistItemEntities.map { it.mediaId }.toSet()
            allMediaList.filter { it.id in mediaIds }
        }
    }

    suspend fun addItemToPlaylist(playlistId: Long, mediaId: String) {
        mediaDao.addItemToPlaylist(com.example.data.model.PlaylistItemEntity(playlistId = playlistId, mediaId = mediaId))
    }

    suspend fun removeItemFromPlaylist(playlistId: Long, mediaId: String) {
        mediaDao.removeItemFromPlaylist(playlistId, mediaId)
    }
}
