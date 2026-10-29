package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    // --- Favorites ---
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getFavorites(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(fav: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE mediaId = :mediaId")
    suspend fun removeFavorite(mediaId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE mediaId = :mediaId)")
    suspend fun isFavorite(mediaId: String): Boolean

    // --- Playback History & Resume Progress ---
    @Query("SELECT * FROM playback_history ORDER BY lastPlayedAt DESC")
    fun getHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(history: HistoryEntity)

    @Query("SELECT lastPositionMs FROM playback_history WHERE mediaId = :mediaId")
    suspend fun getProgress(mediaId: String): Long?

    @Query("DELETE FROM playback_history WHERE mediaId = :mediaId")
    suspend fun deleteHistoryItem(mediaId: String)

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory()

    // --- Private Safe Folder (Vault) ---
    @Query("SELECT * FROM private_vault ORDER BY addedAt DESC")
    fun getPrivateItems(): Flow<List<PrivateItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun encryptToVault(vaultItem: PrivateItemEntity)

    @Query("DELETE FROM private_vault WHERE mediaId = :mediaId")
    suspend fun decryptFromVault(mediaId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM private_vault WHERE mediaId = :mediaId)")
    suspend fun isPrivateItem(mediaId: String): Boolean

    // --- Secure Vault PIN ---
    @Query("SELECT pinHash FROM vault_pin WHERE id = 1")
    suspend fun getVaultPinHash(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setVaultPin(pin: VaultPinEntity)

    // --- Playlists ---
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun createPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY addedAt DESC")
    fun getPlaylistItems(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addItemToPlaylist(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND mediaId = :mediaId")
    suspend fun removeItemFromPlaylist(playlistId: Long, mediaId: String)
}
