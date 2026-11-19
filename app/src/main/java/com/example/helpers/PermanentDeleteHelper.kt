package com.example.helpers

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.data.model.MediaItem
import com.example.data.model.RecycledMediaItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Permanent Phone Storage Delete & 30-Day Recycle Bin Engine.
 * Supports:
 * 1. Direct Permanent Delete from the entire phone (File + MediaStore + Android 11+ createDeleteRequest)
 * 2. Optional 30-Day Recycle Bin holding vault
 */
object PermanentDeleteHelper {
    private const val PREFS_NAME = "HYBRID_RECYCLE_BIN_PREFS"
    private const val KEY_RECYCLED_ITEMS = "recycled_items_json"
    private const val KEY_PERMANENTLY_DELETED_IDS = "permanently_deleted_ids"
    private const val KEY_PERMANENTLY_DELETED_PATHS = "permanently_deleted_paths"
    private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000L

    @Volatile
    private var cachedRecycledIds: Set<String>? = null
    @Volatile
    private var cachedPermDeletedIds: Set<String>? = null
    @Volatile
    private var cachedPermDeletedPaths: Set<String>? = null

    fun moveToRecycleBin(context: Context, item: MediaItem) {
        val current = getRecycledItems(context).toMutableList()
        if (current.none { it.mediaItem.id == item.id }) {
            current.add(0, RecycledMediaItem(mediaItem = item, deletedAt = System.currentTimeMillis()))
            saveRecycledItems(context, current)
        }
        com.example.data.scanner.DeepMediaScanner.invalidateMemoryCache()
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
        com.example.data.scanner.DeepMediaScanner.invalidateMemoryCache()
    }

    fun restoreFromRecycleBin(context: Context, mediaId: String) {
        val current = getRecycledItems(context).filterNot { it.mediaItem.id == mediaId }
        saveRecycledItems(context, current)
        com.example.data.scanner.DeepMediaScanner.invalidateMemoryCache()
    }

    fun restoreAll(context: Context) {
        saveRecycledItems(context, emptyList())
        com.example.data.scanner.DeepMediaScanner.invalidateMemoryCache()
    }

    /**
     * Resolves the MediaStore content:// Uri for a given MediaItem so Android 11+ (API 30+)
     * can permanently delete it from system storage via MediaStore.createDeleteRequest.
     */
    fun resolveMediaStoreUri(context: Context, item: MediaItem): Uri? {
        try {
            if (item.path.startsWith("content://")) {
                return Uri.parse(item.path)
            }
            val numericId = item.id
                .removePrefix("vid_")
                .removePrefix("aud_")
                .removePrefix("ms_v_")
                .removePrefix("ms_a_")
                .removePrefix("pending_file_")
                .toLongOrNull()
            val baseUri = if (item.isVideo) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            if (numericId != null && numericId > 0L) {
                return ContentUris.withAppendedId(baseUri, numericId)
            }
            // Query MediaStore by file path
            context.contentResolver.query(
                baseUri,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DATA}=?",
                arrayOf(item.path),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    return ContentUris.withAppendedId(baseUri, id)
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    /**
     * Creates an OS-level PendingIntent on Android 11+ (API 30+) for permanent phone-wide file deletion
     * when direct file deletion requires scoped-storage confirmation.
     */
    fun createSystemPermanentDeleteRequest(context: Context, items: List<MediaItem>): PendingIntent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val uris = items.mapNotNull { resolveMediaStoreUri(context, it) }
                if (uris.isNotEmpty()) {
                    return MediaStore.createDeleteRequest(context.contentResolver, uris)
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    /**
     * Permanently deletes a single file from the entire phone storage (File system + ContentResolver + Cache).
     */
    fun deletePermanently(context: Context, item: MediaItem): Boolean {
        return deleteBatchPermanently(context, listOf(item)) > 0
    }

    fun markItemsPermanentlyDeleted(context: Context, items: List<MediaItem>) {
        if (items.isEmpty()) return
        val targetIdSet = items.map { it.id }.toSet()
        val current = getRecycledItems(context).filterNot { it.mediaItem.id in targetIdSet }
        saveRecycledItems(context, current)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val deletedIds = prefs.getStringSet(KEY_PERMANENTLY_DELETED_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val deletedPaths = prefs.getStringSet(KEY_PERMANENTLY_DELETED_PATHS, emptySet())?.toMutableSet() ?: mutableSetOf()
        for (item in items) {
            deletedIds.add(item.id)
            if (item.path.isNotBlank()) {
                deletedPaths.add(item.path)
            }
        }
        prefs.edit()
            .putStringSet(KEY_PERMANENTLY_DELETED_IDS, deletedIds)
            .putStringSet(KEY_PERMANENTLY_DELETED_PATHS, deletedPaths)
            .apply()
        cachedPermDeletedIds = deletedIds.toSet()
        cachedPermDeletedPaths = deletedPaths.toSet()
        com.example.data.scanner.DeepMediaScanner.invalidateMemoryCache()
    }

    /**
     * Permanently deletes 1 to 1000+ selected media items at once from phone storage + MediaStore + Cache.
     * Records an item as permanently deleted ONLY if storage/MediaStore deletion actually succeeded.
     */
    fun deleteBatchPermanently(context: Context, items: List<MediaItem>): Int {
        if (items.isEmpty()) return 0
        val actuallyDeleted = mutableListOf<MediaItem>()

        for (item in items) {
            var itemDeleted = false
            // 1. Delete physical file from phone storage
            try {
                if (!item.path.startsWith("http") && !item.path.startsWith("content://")) {
                    val file = File(item.path)
                    if (!file.exists()) {
                        itemDeleted = true
                    } else if (file.delete()) {
                        itemDeleted = true
                    }
                }
            } catch (_: Exception) {
            }

            // 2. Delete from Android MediaStore ContentResolver
            try {
                val mediaStoreUri = resolveMediaStoreUri(context, item)
                if (mediaStoreUri != null) {
                    val rows = context.contentResolver.delete(mediaStoreUri, null, null)
                    if (rows > 0) itemDeleted = true
                }
                if (!item.path.startsWith("content://")) {
                    val baseUri = if (item.isVideo) {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    } else {
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }
                    val rows = context.contentResolver.delete(
                        baseUri,
                        "${MediaStore.MediaColumns.DATA}=?",
                        arrayOf(item.path)
                    )
                    if (rows > 0) itemDeleted = true
                }
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
            if (itemDeleted) {
                actuallyDeleted.add(item)
            }
        }

        if (actuallyDeleted.isNotEmpty()) {
            markItemsPermanentlyDeleted(context, actuallyDeleted)
        }
        return actuallyDeleted.size
    }

    /**
     * Attempts to permanently delete all Recycle Bin items from device storage.
     * Retains any items that require Android 11+ system delete confirmation and returns them.
     */
    fun emptyRecycleBinPermanently(context: Context): List<MediaItem> {
        val current = getRecycledItems(context)
        val remaining = mutableListOf<RecycledMediaItem>()
        current.forEach { recycled ->
            if (!deletePermanently(context, recycled.mediaItem)) {
                remaining.add(recycled)
            }
        }
        saveRecycledItems(context, remaining)
        return remaining.map { it.mediaItem }
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

    fun isHiddenByTrash(context: Context, mediaId: String, mediaPath: String? = null): Boolean {
        val permIds = cachedPermDeletedIds ?: run {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            (prefs.getStringSet(KEY_PERMANENTLY_DELETED_IDS, emptySet()) ?: emptySet()).toSet().also {
                cachedPermDeletedIds = it
            }
        }
        if (mediaId in permIds) return true
        if (!mediaPath.isNullOrBlank()) {
            val permPaths = cachedPermDeletedPaths ?: run {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                (prefs.getStringSet(KEY_PERMANENTLY_DELETED_PATHS, emptySet()) ?: emptySet()).toSet().also {
                    cachedPermDeletedPaths = it
                }
            }
            if (mediaPath in permPaths) return true
        }
        val recycledIds = cachedRecycledIds ?: getRecycledItems(context)
            .map { it.mediaItem.id }
            .toSet()
            .also { cachedRecycledIds = it }
        return mediaId in recycledIds
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
        cachedRecycledIds = items.map { it.mediaItem.id }.toSet()
    }
}
