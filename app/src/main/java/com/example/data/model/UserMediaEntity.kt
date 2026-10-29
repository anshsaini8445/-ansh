package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Database entity representing a user's media file (video or audio track).
 * Includes comprehensive metadata along with folder protection via the [isPrivate] flag.
 */
@Entity(
    tableName = "user_media",
    indices = [
        Index(value = ["isPrivate"]),
        Index(value = ["path"], unique = true),
        Index(value = ["folderName"])
    ]
)
data class UserMediaEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val path: String,
    val isPrivate: Boolean = false,
    val artist: String = "Unknown Artist",
    val duration: Long = 0L, // in milliseconds
    val size: Long = 0L,     // in bytes
    val isVideo: Boolean = false,
    val folderName: String = "Internal Storage",
    val coverUrl: String? = null,
    val mimeType: String? = null,
    val dateAdded: Long = System.currentTimeMillis(),
    val dateModified: Long = System.currentTimeMillis()
) {
    /**
     * Converts Room database entity into UI domain model [MediaItem].
     */
    fun toMediaItem(): MediaItem {
        return MediaItem(
            id = id,
            title = title,
            artist = artist,
            duration = duration,
            size = size,
            path = path,
            isVideo = isVideo,
            folderName = folderName,
            isPrivate = isPrivate,
            coverUrl = coverUrl
        )
    }

    companion object {
        /**
         * Creates an entity from domain [MediaItem] preserving or overriding private status.
         */
        fun fromMediaItem(item: MediaItem, isPrivate: Boolean = item.isPrivate): UserMediaEntity {
            return UserMediaEntity(
                id = item.id,
                title = item.title,
                path = item.path,
                isPrivate = isPrivate,
                artist = item.artist,
                duration = item.duration,
                size = item.size,
                isVideo = item.isVideo,
                folderName = item.folderName,
                coverUrl = item.coverUrl
            )
        }
    }
}
