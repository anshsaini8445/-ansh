package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room database Entity representing a managed media file with privacy vault support.
 */
@Entity(
    tableName = "media_files",
    indices = [
        Index(value = ["path"], unique = false),
        Index(value = ["is_private"])
    ]
)
data class MediaFile(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "path")
    val path: String,

    @ColumnInfo(name = "duration")
    val duration: Long = 0L,

    @ColumnInfo(name = "is_private")
    val isPrivate: Boolean = false,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromMediaItem(
            item: MediaItem,
            isPrivate: Boolean = item.isPrivate,
            timestamp: Long = System.currentTimeMillis()
        ): MediaFile {
            return MediaFile(
                id = item.id,
                title = item.title,
                path = item.path,
                duration = item.duration,
                isPrivate = isPrivate,
                timestamp = timestamp
            )
        }
    }
}
