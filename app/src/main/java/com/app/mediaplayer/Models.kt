package com.app.mediaplayer

import java.io.File
import java.util.Locale

data class VideoModel(
    val id: Long,
    val title: String,
    val path: String,
    val size: Long,
    val duration: Long
) {
    constructor(
        id: Long,
        title: String,
        durationMs: Long,
        sizeBytes: Long,
        filePath: String
    ) : this(id, title, filePath, sizeBytes, durationMs)

    val isPartialDownload: Boolean
        get() {
            val lower = path.lowercase(Locale.US)
            return lower.endsWith(".part") ||
                lower.endsWith(".crdownload") ||
                lower.endsWith(".download") ||
                lower.endsWith(".tmp") ||
                duration <= 0L
        }

    val folderPath: String
        get() = File(path).parentFile?.absolutePath ?: ""

    val folderName: String
        get() = File(path).parentFile?.name?.takeIf { it.isNotBlank() } ?: "Internal Storage"

    val formattedDuration: String
        get() = formatMediaDuration(duration)

    val formattedSize: String
        get() = formatMediaSize(size)

    val resolutionBadge: String
        get() {
            val lowerTitle = title.lowercase(Locale.US)
            return when {
                lowerTitle.contains("8k") || lowerTitle.contains("4320p") || size > 2_500_000_000L -> "8K"
                lowerTitle.contains("4k") || lowerTitle.contains("2160p") || size > 900_000_000L -> "4K"
                lowerTitle.contains("1080p") || lowerTitle.contains("fhd") || size > 250_000_000L -> "FHD"
                lowerTitle.contains("720p") || lowerTitle.contains("hd") || size > 80_000_000L -> "HD"
                isPartialDownload -> "PARTIAL"
                else -> "HD"
            }
        }

    companion object {
        fun formatMediaDuration(durationMs: Long): String {
            if (durationMs <= 0L) return "LIVE / PARTIAL"
            val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L
            return if (hours > 0L) {
                String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        }

        fun formatMediaSize(bytes: Long): String {
            if (bytes <= 0L) return "0 KB"
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
                mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
                else -> String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
            }
        }
    }
}

data class AudioModel(
    val id: Long,
    val title: String,
    val path: String,
    val artist: String,
    val duration: Long,
    val size: Long = 0L,
    val album: String = "Unknown Album"
) {
    val formattedDuration: String
        get() = VideoModel.formatMediaDuration(duration)

    val formattedSize: String
        get() = VideoModel.formatMediaSize(size)
}

data class FolderModel(
    val bucketId: String,
    val folderName: String,
    val videoCount: Int,
    val folderPath: String = "",
    val totalSize: Long = 0L
)
