package com.example.helpers

import android.content.Context
import com.example.data.model.MediaItem
import com.example.data.model.RecycledMediaItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 30-Day Recycle Bin & Permanent Delete Engine.
 * Prevents accidental loss of family videos or favorite tracks by offering a 30-day
 * safe holding vault before permanent removal from MediaStore and the file system.
 */
object PermanentDeleteHelper {
    private const val PREFS_NAME = "HYBRID_RECYCLE_BIN_PREFS"
    private const val KEY_RECYCLED_ITEMS = "recycled_items_json"
    private const val KEY_PERMANENTLY_DELETED_IDS = "permanently_deleted_ids"
    private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000L

    fun moveToRecycleBin(context: Context, item: MediaItem) {
        val current = getRecycledItems(context).toMutableList()
        if (current.none { it.mediaItem.id == item.id }) {
            current.add(0, RecycledMediaItem(mediaItem = item, deletedAt = System.currentTimeMillis()))
            saveRecycledItems(context, current)
        }
    }

    fun moveBatchToRecycleBin(context: Context, items: List<MediaItem>) {
        val current = getRecycledItems(context).toMutableList()
        val now = System.currentTimeMillis()
        items.forEach { item ->
            if (current.none { it.mediaItem.id == item.id }) {
                current.add(0, RecycledMediaItem(mediaItem = item, deletedAt = now))
            }
        }
        saveRecycledItems(context, current)
    }

    fun restoreFromRecycleBin(context: Context, mediaId: String) {
        val current = getRecycledItems(context).filterNot { it.mediaItem.id == mediaId }
        saveRecycledItems(context, current)
    }

    fun restoreAll(context: Context) {
        saveRecycledItems(context, emptyList())
    }

    fun deletePermanently(context: Context, item: MediaItem) {
        // Remove from recycle bin if present
        val current = getRecycledItems(context).filterNot { it.mediaItem.id == item.id }
        saveRecycledItems(context, current)

        // Record ID as permanently deleted so scanner skips it
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val deletedSet = prefs.getStringSet(KEY_PERMANENTLY_DELETED_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        deletedSet.add(item.id)
        prefs.edit().putStringSet(KEY_PERMANENTLY_DELETED_IDS, deletedSet).apply()

        // Attempt physical file & MediaStore deletion if it is a local writable file
        try {
            if (!item.path.startsWith("http")) {
                val file = File(item.path)
                if (file.exists()) {
                    file.delete()
                }
                val uri = if (item.isVideo) {
                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                context.contentResolver.delete(
                    uri,
                    "${android.provider.MediaStore.MediaColumns.DATA}=?",
                    arrayOf(item.path)
                )
            }
        } catch (_: Exception) {
        }
        com.example.data.scanner.DeepMediaScanner.invalidateMemoryCache()
    }

    fun emptyRecycleBinPermanently(context: Context) {
        val current = getRecycledItems(context)
        current.forEach { recycled ->
            deletePermanently(context, recycled.mediaItem)
        }
        saveRecycledItems(context, emptyList())
    }

    fun getRecycledItems(context: Context): List<RecycledMediaItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_RECYCLED_ITEMS, null) ?: return emptyList()
        val now = System.currentTimeMillis()
        val result = mutableListOf<RecycledMediaItem>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val deletedAt = obj.optLong("deletedAt", now)
                if (now - deletedAt <= THIRTY_DAYS_MS) {
                    val item = MediaItem(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        artist = obj.optString("artist", "Unknown"),
                        duration = obj.optLong("duration", 0L),
                        size = obj.optLong("size", 0L),
                        path = obj.getString("path"),
                        isVideo = obj.optBoolean("isVideo", true),
                        folderName = obj.optString("folderName", "Internal Storage"),
                        coverUrl = obj.optString("coverUrl", "").takeIf { it.isNotEmpty() },
                        resolution = obj.optString("resolution", "4k"),
                        format = obj.optString("format", "MP4"),
                        isPartialDownload = obj.optBoolean("isPartialDownload", false)
                    )
                    result.add(RecycledMediaItem(item, deletedAt))
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    fun isHiddenByTrash(context: Context, mediaId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val permDeleted = prefs.getStringSet(KEY_PERMANENTLY_DELETED_IDS, emptySet()) ?: emptySet()
        if (mediaId in permDeleted) return true
        return getRecycledItems(context).any { it.mediaItem.id == mediaId }
    }

    private fun saveRecycledItems(context: Context, items: List<RecycledMediaItem>) {
        val array = JSONArray()
        items.forEach { recycled ->
            val item = recycled.mediaItem
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("artist", item.artist)
                put("duration", item.duration)
                put("size", item.size)
                put("path", item.path)
                put("isVideo", item.isVideo)
                put("folderName", item.folderName)
                put("coverUrl", item.coverUrl ?: "")
                put("resolution", item.resolution)
                put("format", item.format)
                put("isPartialDownload", item.isPartialDownload)
                put("deletedAt", recycled.deletedAt)
            }
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECYCLED_ITEMS, array.toString())
            .apply()
    }
}
