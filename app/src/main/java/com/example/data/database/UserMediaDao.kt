package com.example.data.database

import androidx.room.*
import com.example.data.model.UserMediaEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for managing [UserMediaEntity] records.
 * Provides reactive queries for public/private media, folder-level filtering,
 * search, and operations for locking/protecting user media files into private safe folders.
 */
@Dao
interface UserMediaDao {

    // --- Reactive Observables (Flows) ---

    @Query("SELECT * FROM user_media ORDER BY dateAdded DESC")
    fun getAllMedia(): Flow<List<UserMediaEntity>>

    @Query("SELECT * FROM user_media WHERE isPrivate = 0 ORDER BY dateAdded DESC")
    fun getPublicMedia(): Flow<List<UserMediaEntity>>

    @Query("SELECT * FROM user_media WHERE isPrivate = 1 ORDER BY dateAdded DESC")
    fun getPrivateMedia(): Flow<List<UserMediaEntity>>

    @Query("SELECT * FROM user_media WHERE isPrivate = 0 AND isVideo = 1 ORDER BY dateAdded DESC")
    fun getPublicVideos(): Flow<List<UserMediaEntity>>

    @Query("SELECT * FROM user_media WHERE isPrivate = 0 AND isVideo = 0 ORDER BY dateAdded DESC")
    fun getPublicAudio(): Flow<List<UserMediaEntity>>

    @Query("SELECT * FROM user_media WHERE id = :id LIMIT 1")
    fun getMediaByIdFlow(id: String): Flow<UserMediaEntity?>

    @Query("SELECT * FROM user_media WHERE path = :path LIMIT 1")
    fun getMediaByPathFlow(path: String): Flow<UserMediaEntity?>

    @Query("SELECT COUNT(*) FROM user_media WHERE isPrivate = 1")
    fun getPrivateCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM user_media WHERE isPrivate = 0")
    fun getPublicCountFlow(): Flow<Int>

    @Query("SELECT DISTINCT folderName FROM user_media WHERE isPrivate = :isPrivate ORDER BY folderName ASC")
    fun getFoldersFlow(isPrivate: Boolean = false): Flow<List<String>>

    @Query("SELECT * FROM user_media WHERE folderName = :folderName AND isPrivate = :isPrivate ORDER BY dateAdded DESC")
    fun getMediaByFolderFlow(folderName: String, isPrivate: Boolean = false): Flow<List<UserMediaEntity>>

    @Query("SELECT * FROM user_media WHERE (title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%') AND isPrivate = :isPrivate ORDER BY dateAdded DESC")
    fun searchMediaFlow(query: String, isPrivate: Boolean = false): Flow<List<UserMediaEntity>>

    // --- Synchronous / Suspend Queries ---

    @Query("SELECT * FROM user_media WHERE id = :id LIMIT 1")
    suspend fun getMediaById(id: String): UserMediaEntity?

    @Query("SELECT * FROM user_media WHERE path = :path LIMIT 1")
    suspend fun getMediaByPath(path: String): UserMediaEntity?

    @Query("SELECT isPrivate FROM user_media WHERE id = :id LIMIT 1")
    suspend fun isMediaPrivate(id: String): Boolean?

    @Query("SELECT EXISTS(SELECT 1 FROM user_media WHERE id = :id AND isPrivate = 1)")
    suspend fun isItemInPrivateVault(id: String): Boolean

    @Query("SELECT * FROM user_media WHERE isPrivate = 0")
    suspend fun getPublicMediaList(): List<UserMediaEntity>

    @Query("SELECT * FROM user_media WHERE isPrivate = 1")
    suspend fun getPrivateMediaList(): List<UserMediaEntity>

    // --- Insertions & Updates ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: UserMediaEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mediaList: List<UserMediaEntity>)

    @Update
    suspend fun updateMedia(media: UserMediaEntity)

    /**
     * Updates the private protection flag for an individual media item.
     */
    @Query("UPDATE user_media SET isPrivate = :isPrivate WHERE id = :id")
    suspend fun setPrivateStatus(id: String, isPrivate: Boolean)

    /**
     * Batch updates the private protection flag for a list of media items.
     */
    @Query("UPDATE user_media SET isPrivate = :isPrivate WHERE id IN (:ids)")
    suspend fun setPrivateStatusBatch(ids: List<String>, isPrivate: Boolean)

    /**
     * Toggles the private protection flag for an individual media file.
     */
    @Query("UPDATE user_media SET isPrivate = CASE WHEN isPrivate = 1 THEN 0 ELSE 1 END WHERE id = :id")
    suspend fun togglePrivateStatus(id: String)

    /**
     * Updates playback progress for resume functionality.
     */
    @Query("UPDATE user_media SET dateModified = :timestamp WHERE id = :id")
    suspend fun updateLastPlayedTime(id: String, timestamp: Long = System.currentTimeMillis())

    // --- Deletions ---

    @Delete
    suspend fun deleteMedia(media: UserMediaEntity)

    @Query("DELETE FROM user_media WHERE id = :id")
    suspend fun deleteMediaById(id: String)

    @Query("DELETE FROM user_media WHERE id IN (:ids)")
    suspend fun deleteMediaByIds(ids: List<String>)

    @Query("DELETE FROM user_media WHERE isPrivate = 1")
    suspend fun clearPrivateMedia()

    @Query("DELETE FROM user_media")
    suspend fun clearAllMedia()
}
