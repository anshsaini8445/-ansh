package com.example.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.data.database.AppDatabase
import com.example.data.database.MediaDao
import com.example.data.database.MediaFileDao
import com.example.data.database.UserMediaDao
import com.example.data.model.LyricLine
import com.example.data.model.MediaFile
import com.example.data.model.MediaItem
import com.example.data.model.UserMediaEntity
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
    // Curated Premium online files for instant playability & streaming preview
    private val premiumCuratedMedia = listOf(
        // --- Video Section ---
        MediaItem(
            id = "video_bbb",
            title = "Big Buck Bunny (HD Studio Master)",
            artist = "Blender Foundation",
            duration = 596000, // ~10m
            size = 276100000,
            path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            isVideo = true,
            folderName = "Featured Streams",
            coverUrl = "https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=500&auto=format&fit=crop"
        ),
        MediaItem(
            id = "video_sintel",
            title = "Sintel (HD Cinematic CGI)",
            artist = "Durian Open Movie Project",
            duration = 888000, // ~14m
            size = 420000000,
            path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
            isVideo = true,
            folderName = "Cinema",
            coverUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=500&auto=format&fit=crop"
        ),
        MediaItem(
            id = "video_tears",
            title = "Tears of Steel (VFX Sci-Fi Showcase)",
            artist = "Mango Open Movie",
            duration = 734000, // ~12m
            size = 360000000,
            path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
            isVideo = true,
            folderName = "Sci-Fi",
            coverUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=500&auto=format&fit=crop"
        ),
        MediaItem(
            id = "video_sub_loop",
            title = "Neon Ambient Visual Loop",
            artist = "MAX Visuals",
            duration = 30000, // 30s loop
            size = 12000000,
            path = "https://assets.mixkit.co/videos/preview/mixkit-abstract-laser-lights-background-loop-41712-large.mp4",
            isVideo = true,
            folderName = "Featured Streams",
            coverUrl = "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?w=500&auto=format&fit=crop"
        ),

        // --- Music Section ---
        MediaItem(
            id = "music_helix_1",
            title = "Retro Synthwave Drive",
            artist = "Aether Glitch",
            duration = 372000, // ~6.2m
            size = 8900000,
            path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            isVideo = false,
            folderName = "Synthwave",
            coverUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=500&auto=format&fit=crop",
            lyrics = listOf(
                LyricLine(0, "[Instrumental Intro - Feel the Bass]"),
                LyricLine(8000, "Driving down the neon light highway"),
                LyricLine(16000, "Engine humming, stars fading to gray"),
                LyricLine(24000, "Unlocking the Maximus premium play"),
                LyricLine(32000, "Perfect acoustics guiding our way"),
                LyricLine(40000, "Equalizer waves start to rise and fall"),
                LyricLine(48000, "Feel the custom frequencies touch the soul"),
                LyricLine(56000, "[Pre-Chorus Drop - Pure Analog Bliss]"),
                LyricLine(72000, "This is the sound of absolute control!"),
                LyricLine(80000, "Through the night, we cruise so fast"),
                LyricLine(88000, "Leaving shadows in the glowing past"),
                LyricLine(96000, "[Synthesizer Solo Interlude]")
            )
        ),
        MediaItem(
            id = "music_helix_2",
            title = "Acoustic Horizon Sunset",
            artist = "Fable Woods",
            duration = 423000,
            size = 10100000,
            path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            isVideo = false,
            folderName = "Acoustic",
            coverUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=500&auto=format&fit=crop",
            lyrics = listOf(
                LyricLine(0, "[Soft Guitar Strumbing Intro]"),
                LyricLine(12000, "Sun sinks down below the golden ridge"),
                LyricLine(24000, "Meet me halfway, standing on the bridge"),
                LyricLine(36000, "Whispers of the summer breeze so sweet"),
                LyricLine(48000, "Felt the earth beneath our bare feet"),
                LyricLine(60000, "[Chorus - Acoustic Echoes]"),
                LyricLine(72000, "Time won't wait, but we can slow it down"),
                LyricLine(84000, "Far away from the busy, noisy town")
            )
        ),
        MediaItem(
            id = "music_helix_4",
            title = "Chill Lofi Afternoon Beats",
            artist = "Sleepy Head",
            duration = 302000,
            size = 7200000,
            path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
            isVideo = false,
            folderName = "Chill Lofi",
            coverUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&auto=format&fit=crop",
            lyrics = listOf(
                LyricLine(0, "[Vinyl Crackle - Relaxation Mode On]"),
                LyricLine(10000, "Raindrops hitting the window pane"),
                LyricLine(20000, "Washing away the stress and pain"),
                LyricLine(30000, "Close your eyes, feel the cozy rain"),
                LyricLine(40000, "Max Player premium stream is here again")
            )
        ),
        MediaItem(
            id = "music_helix_8",
            title = "Electro Cyber Beats",
            artist = "Neuro Linker",
            duration = 318000,
            size = 7600000,
            path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
            isVideo = false,
            folderName = "Cyberpunk",
            coverUrl = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=500&auto=format&fit=crop",
            lyrics = listOf(
                LyricLine(0, "[High Energy Cyber Beat Intro]"),
                LyricLine(15000, "Into the matrix, neon grid lines fuse"),
                LyricLine(30000, "Overclock the frequency, nothing left to lose")
            )
        )
    )

    // --- Query Local Storage Media Files ---
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
                null
            )

            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media._ID else MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.TITLE else MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.ARTIST else MediaStore.Audio.Media.ARTIST)
                val durationCol = c.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.DURATION else MediaStore.Audio.Media.DURATION)
                val sizeCol = c.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.SIZE else MediaStore.Audio.Media.SIZE)
                val dataCol = c.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.DATA else MediaStore.Audio.Media.DATA)

                while (c.moveToNext()) {
                    val id = c.getLong(idCol).toString()
                    val title = c.getString(titleCol) ?: "Unknown File"
                    val artist = c.getString(artistCol) ?: "Local Media"
                    val duration = c.getLong(durationCol)
                    val size = c.getLong(sizeCol)
                    val path = c.getString(dataCol) ?: ""
                    
                    val file = File(path)
                    val parentFolder = file.parentFile?.name ?: "Local Storage"

                    list.add(
                        MediaItem(
                            id = id,
                            title = title,
                            artist = artist,
                            duration = duration,
                            size = size,
                            path = path,
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

    // Instant (<150ms) cold-start snapshot from DeepMediaScanner
    fun getInstantMedia(isVideo: Boolean): List<MediaItem> {
        return com.example.data.scanner.DeepMediaScanner.getInstantSnapshot(context, isVideo)
    }

    // Full background scan combining MediaStore, Direct Folder Scanner (>1KB partial files), and Studio Showcase
    suspend fun getAvailableMedia(isVideo: Boolean): List<MediaItem> {
        return com.example.data.scanner.DeepMediaScanner.scanAndSyncMedia(context, isVideo)
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
