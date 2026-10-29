package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

data class MediaItem(
    val id: String,
    val title: String,
    val artist: String,
    val duration: Long, // in milliseconds
    val size: Long, // in bytes
    val path: String, // file path, resource name, or stream URL
    val isVideo: Boolean,
    val folderName: String = "Internal Storage",
    val isPrivate: Boolean = false,
    val coverUrl: String? = null,
    val lyrics: List<LyricLine> = emptyList()
) {
    val formattedDuration: String
        get() {
            val totalSecs = duration / 1000
            val hours = totalSecs / 3600
            val minutes = (totalSecs % 3600) / 60
            val seconds = totalSecs % 60
            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

    val formattedSize: String
        get() {
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
        }
}

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val mediaId: String,
    val isVideo: Boolean,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playback_history")
data class HistoryEntity(
    @PrimaryKey val mediaId: String,
    val lastPositionMs: Long,
    val lastPlayedAt: Long = System.currentTimeMillis(),
    val playCount: Int = 1
)

@Entity(tableName = "private_vault")
data class PrivateItemEntity(
    @PrimaryKey val mediaId: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "vault_pin")
data class VaultPinEntity(
    @PrimaryKey val id: Int = 1, // Only 1 PIN supported
    val pinHash: String
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_items")
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val mediaId: String,
    val addedAt: Long = System.currentTimeMillis()
)
