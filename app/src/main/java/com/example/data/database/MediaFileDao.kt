package com.example.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.MediaFile
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for managing [MediaFile] records, including insertion,
 * deletion, and toggling the private folder protection status.
 */
@Dao
interface MediaFileDao {

    @Query("SELECT * FROM media_files ORDER BY timestamp DESC")
    fun getAllMediaFiles(): Flow<List<MediaFile>>

    @Query("SELECT * FROM media_files WHERE is_private = 0 ORDER BY timestamp DESC")
    fun getPublicMediaFiles(): Flow<List<MediaFile>>

    @Query("SELECT * FROM media_files WHERE is_private = 1 ORDER BY timestamp DESC")
    fun getPrivateMediaFiles(): Flow<List<MediaFile>>

    @Query("SELECT * FROM media_files WHERE id = :id LIMIT 1")
    suspend fun getMediaFileById(id: String): MediaFile?

    @Query("SELECT * FROM media_files WHERE path = :path LIMIT 1")
    suspend fun getMediaFileByPath(path: String): MediaFile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mediaFile: MediaFile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mediaFiles: List<MediaFile>)

    @Update
    suspend fun update(mediaFile: MediaFile)

    @Delete
    suspend fun delete(mediaFile: MediaFile)

    @Query("DELETE FROM media_files WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE media_files SET is_private = NOT is_private, timestamp = :updatedTimestamp WHERE id = :id")
    suspend fun togglePrivateStatus(id: String, updatedTimestamp: Long = System.currentTimeMillis())

    @Query("UPDATE media_files SET is_private = :isPrivate, timestamp = :updatedTimestamp WHERE id = :id")
    suspend fun setPrivateStatus(id: String, isPrivate: Boolean, updatedTimestamp: Long = System.currentTimeMillis())

    @Transaction
    suspend fun toggleAndGetPrivateStatus(id: String): Boolean {
        togglePrivateStatus(id, System.currentTimeMillis())
        return getMediaFileById(id)?.isPrivate ?: false
    }
}
