package com.example.data.scanner

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.LruCache
import android.util.Size
import com.example.data.model.MediaItem
import com.example.helpers.PermanentDeleteHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * High-Speed Real Android Device Media Scanner.
 * - Scans all videos from 144p tiny clips up to 2K / 4K / 8K large files.
 * - Detects partially downloaded / in-progress Chrome & browser videos (.crdownload, .part, .partial,
 *   .download, .tmp, IS_PENDING=1) and pins them at the top with `isPartialDownload = true` ("Pending").
 * - Uses non-blocking metadata scanning for instant list loading and semaphore-throttled thumbnail extraction.
 */
object DeepMediaScanner {

    private const val LEGACY_DUMMY_PREFS = "HYBRID_INSTANT_VIDEO_SNAPSHOT"
    private const val SNAPSHOT_PREFS = "HYBRID_REAL_MEDIA_SNAPSHOT_V4"
    private const val KEY_VIDEO_SNAPSHOT = "real_video_rows_v4"
    private const val KEY_AUDIO_SNAPSHOT = "real_audio_rows_v4"
    private const val KEY_PICKED_VIDEOS = "user_picked_video_uris_v4"
    private const val KEY_PICKED_AUDIOS = "user_picked_audio_uris_v4"

    val PARTIAL_DOWNLOAD_EXTENSIONS = setOf(
        "crdownload", "part", "partial", "temp", "tmp", "download", "dl", "1dm", "ucd", "idm", "adm"
    )

    val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "flv", "ts", "m4v", "3gp", "3gpp", "wmv",
        "hevc", "h265", "av1", "divx", "vob", "ogv", "m2ts", "mts", "rmvb", "mpeg", "mpg"
    ) + PARTIAL_DOWNLOAD_EXTENSIONS

    val AUDIO_EXTENSIONS = setOf(
        "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma", "amr", "alac", "aiff"
    )

    @Volatile
    private var appContextRef: Context? = null

    @Volatile
    private var ramVideoCache: List<MediaItem> = emptyList()

    @Volatile
    private var ramAudioCache: List<MediaItem> = emptyList()

    private val bitmapCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(160) {}
    private val unextractablePaths = java.util.Collections.synchronizedSet(HashSet<String>())

    // Limits concurrent heavy frame decodes so scrolling stays 120fps smooth
    private val thumbnailDecodeSemaphore = Semaphore(permits = 2)

    fun getCachedBitmap(path: String): Bitmap? = bitmapCache.get(path)

    fun putCachedBitmap(path: String, bitmap: Bitmap) {
        bitmapCache.put(path, bitmap)
    }

    fun invalidateMemoryCache() {
        ramVideoCache = emptyList()
        ramAudioCache = emptyList()
    }

    private fun downscaleForSmoothUi(src: Bitmap?, isVideo: Boolean): Bitmap? {
        if (src == null) return null
        val maxW = if (isVideo) 240 else 180
        val maxH = if (isVideo) 136 else 180
        if (src.width <= 0 || src.height <= 0 || (src.width <= maxW && src.height <= maxH)) return src
        return try {
            val ratio = minOf(maxW.toFloat() / src.width.toFloat(), maxH.toFloat() / src.height.toFloat())
            val targetW = (src.width * ratio).toInt().coerceAtLeast(1)
            val targetH = (src.height * ratio).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(src, targetW, targetH, true)
            if (scaled !== src) src.recycle()
            scaled
        } catch (_: Throwable) {
            src
        }
    }

    suspend fun loadOrExtractBitmap(path: String, isVideo: Boolean, context: Context? = appContextRef): Bitmap? =
        withContext(Dispatchers.IO) {
            if (path.isBlank() || path.startsWith("http") || unextractablePaths.contains(path)) return@withContext null
            bitmapCache.get(path)?.let { return@withContext it }

            thumbnailDecodeSemaphore.withPermit {
                bitmapCache.get(path)?.let { return@withPermit it }
                if (unextractablePaths.contains(path)) return@withPermit null

                val ctx = context ?: appContextRef
                var extracted: Bitmap? = null

                if (ctx != null && path.startsWith("content://")) {
                    val uri = Uri.parse(path)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            extracted = ctx.contentResolver.loadThumbnail(
                                uri,
                                if (isVideo) Size(240, 136) else Size(180, 180),
                                null
                            )
                        } catch (_: Throwable) {
                        }
                    }
                    if (extracted == null) {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(ctx, uri)
                            if (isVideo) {
                                extracted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                    retriever.getScaledFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 240, 136)
                                } else {
                                    retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                }
                            } else {
                                val pic = retriever.embeddedPicture
                                if (pic != null && pic.isNotEmpty()) {
                                    val opts = BitmapFactory.Options().apply {
                                        inSampleSize = 2
                                        inPreferredConfig = Bitmap.Config.RGB_565
                                    }
                                    extracted = BitmapFactory.decodeByteArray(pic, 0, pic.size, opts)
                                }
                            }
                        } catch (_: Throwable) {
                        } finally {
                            try {
                                retriever.release()
                            } catch (_: Throwable) {
                            }
                        }
                    }
                } else {
                    val file = File(path)
                    if (file.exists() && file.length() > 256L) {
                        if (isVideo) {
                            try {
                                @Suppress("DEPRECATION")
                                extracted = ThumbnailUtils.createVideoThumbnail(
                                    file.absolutePath,
                                    MediaStore.Images.Thumbnails.MINI_KIND
                                )
                            } catch (_: Throwable) {
                            }
                        }
                        if (extracted == null) {
                            val retriever = MediaMetadataRetriever()
                            try {
                                retriever.setDataSource(file.absolutePath)
                                if (isVideo) {
                                    extracted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                        retriever.getScaledFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 240, 136)
                                    } else {
                                        retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                    }
                                } else {
                                    val pic = retriever.embeddedPicture
                                    if (pic != null && pic.isNotEmpty()) {
                                        val opts = BitmapFactory.Options().apply {
                                            inSampleSize = 2
                                            inPreferredConfig = Bitmap.Config.RGB_565
                                        }
                                        extracted = BitmapFactory.decodeByteArray(pic, 0, pic.size, opts)
                                    }
                                }
                            } catch (_: Throwable) {
                            } finally {
                                try {
                                    retriever.release()
                                } catch (_: Throwable) {
                                }
                            }
                        }
                    }
                }

                val scaled = downscaleForSmoothUi(extracted, isVideo)
                if (scaled != null) {
                    try {
                        bitmapCache.put(path, scaled)
                    } catch (_: Throwable) {
                    }
                } else {
                    if (unextractablePaths.size > 500) unextractablePaths.clear()
                    unextractablePaths.add(path)
                }
                scaled
            }
        }

    private fun purgeLegacyDummySnapshot(context: Context) {
        try {
            context.getSharedPreferences(LEGACY_DUMMY_PREFS, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        } catch (_: Exception) {
        }
    }

    fun getInstantSnapshot(context: Context, isVideo: Boolean): List<MediaItem> {
        appContextRef = context.applicationContext
        purgeLegacyDummySnapshot(context)

        val currentRam = if (isVideo) ramVideoCache else ramAudioCache
        if (currentRam.isNotEmpty()) {
            return currentRam.filterNot {
                it.path.startsWith("http", ignoreCase = true) ||
                    PermanentDeleteHelper.isHiddenByTrash(context, it.id, it.path)
            }.distinctBy { it.path }.distinctBy { it.id }
        }

        val prefs = context.getSharedPreferences(SNAPSHOT_PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(if (isVideo) KEY_VIDEO_SNAPSHOT else KEY_AUDIO_SNAPSHOT, null)
        if (!raw.isNullOrBlank()) {
            val parsed = deserializeSnapshot(raw, isVideo).filterNot {
                it.path.startsWith("http", ignoreCase = true) ||
                    PermanentDeleteHelper.isHiddenByTrash(context, it.id, it.path)
            }.distinctBy { it.path }.distinctBy { it.id }
            if (parsed.isNotEmpty()) {
                if (isVideo) ramVideoCache = parsed else ramAudioCache = parsed
                return parsed
            }
        }

        return emptyList()
    }

    /**
     * Ultra-Fast (<80ms) First-Pass MediaStore Query:
     * Immediately returns indexed MediaStore videos/songs + pending downloads right after app launch
     * or permission grant so the UI populates with zero delay before the deep folder walk finishes.
     */
    suspend fun scanFastMediaStoreFirst(context: Context, isVideo: Boolean): List<MediaItem> = withContext(Dispatchers.IO) {
        appContextRef = context.applicationContext
        purgeLegacyDummySnapshot(context)

        val discoveredMap = LinkedHashMap<String, MediaItem>()
        val mediaStoreList = queryMediaStore(context, isVideo)
        for (item in mediaStoreList) {
            if (PermanentDeleteHelper.isHiddenByTrash(context, item.id, item.path)) continue
            discoveredMap[item.path] = item
        }
        if (isVideo) {
            val pendingFiles = queryPendingAndPartialVideoFiles(context)
            for (item in pendingFiles) {
                if (PermanentDeleteHelper.isHiddenByTrash(context, item.id, item.path)) continue
                if (!discoveredMap.containsKey(item.path)) {
                    discoveredMap[item.path] = item
                }
            }
        }
        val fastList = discoveredMap.values
            .distinctBy { it.path }
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<MediaItem> { it.isPartialDownload }
                    .thenByDescending { it.dateAdded }
            )
        if (fastList.isNotEmpty()) {
            if (isVideo) ramVideoCache = fastList else ramAudioCache = fastList
        }
        return@withContext fastList
    }

    /**
     * Full High-Speed Asynchronous Storage Scan:
     * 1. MediaStore Video/Audio tables (with safe universal columns across all Android versions)
     * 2. MediaStore.Files query for pending/partial browser downloads (.crdownload, .part, IS_PENDING)
     * 3. Direct File System scan across Download, DCIM, Camera, Movies, WhatsApp, Statuses, Telegram
     * 4. User-Picked Document URIs
     * Sorts Pending/Partial downloads at the VERY TOP (like XPlayer), followed by newest videos.
     */
    suspend fun scanAndSyncMedia(context: Context, isVideo: Boolean): List<MediaItem> = withContext(Dispatchers.IO) {
        appContextRef = context.applicationContext
        purgeLegacyDummySnapshot(context)

        val discoveredMap = LinkedHashMap<String, MediaItem>()

        // 1. Query MediaStore (External + Internal volumes)
        val mediaStoreList = queryMediaStore(context, isVideo)
        for (item in mediaStoreList) {
            if (PermanentDeleteHelper.isHiddenByTrash(context, item.id, item.path)) continue
            discoveredMap[item.path] = item
        }

        // 2. Query MediaStore.Files for partial / pending browser downloads if scanning videos
        if (isVideo) {
            val pendingFiles = queryPendingAndPartialVideoFiles(context)
            for (item in pendingFiles) {
                if (PermanentDeleteHelper.isHiddenByTrash(context, item.id, item.path)) continue
                if (!discoveredMap.containsKey(item.path)) {
                    discoveredMap[item.path] = item
                }
            }
        }

        // 3. Fast Direct Directory & Partial File Scanner
        val directFiles = scanRealDeviceDirectories(context, isVideo)
        for (item in directFiles) {
            if (PermanentDeleteHelper.isHiddenByTrash(context, item.id, item.path)) continue
            if (!discoveredMap.containsKey(item.path)) {
                discoveredMap[item.path] = item
            }
        }

        // 4. User-Picked Persistent Document URIs
        val pickedItems = loadUserPickedUris(context, isVideo)
        for (item in pickedItems) {
            if (PermanentDeleteHelper.isHiddenByTrash(context, item.id, item.path)) continue
            if (!discoveredMap.containsKey(item.path)) {
                discoveredMap[item.path] = item
            }
        }

        // Sort: Pending/Partial downloads ALWAYS at the top, then by dateAdded descending
        val finalList = discoveredMap.values
            .distinctBy { it.path }
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<MediaItem> { it.isPartialDownload }
                    .thenByDescending { it.dateAdded }
            )

        if (isVideo) {
            ramVideoCache = finalList
        } else {
            ramAudioCache = finalList
        }
        saveSnapshot(context, isVideo, finalList)
        return@withContext finalList
    }

    fun addPickedMediaUris(context: Context, uris: List<Uri>, isVideo: Boolean) {
        if (uris.isEmpty()) return
        appContextRef = context.applicationContext
        val prefs = context.getSharedPreferences(SNAPSHOT_PREFS, Context.MODE_PRIVATE)
        val key = if (isVideo) KEY_PICKED_VIDEOS else KEY_PICKED_AUDIOS
        val existing = (prefs.getStringSet(key, emptySet()) ?: emptySet()).toMutableSet()
        for (uri in uris) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            existing.add(uri.toString())
        }
        prefs.edit().putStringSet(key, existing).apply()
    }

    private fun isFileNamePartialDownload(nameOrPath: String): Boolean {
        val lower = nameOrPath.lowercase(Locale.US)
        val ext = lower.substringAfterLast('.', "")
        if (ext in PARTIAL_DOWNLOAD_EXTENSIONS) return true
        return lower.contains(".crdownload") ||
            lower.contains(".part") ||
            lower.contains(".partial") ||
            lower.contains(".download") ||
            lower.startsWith(".pending-")
    }

    private fun cleanPartialFileName(rawName: String): String {
        var cleaned = rawName
            .removePrefix(".pending-")
            .replace(Regex("(?i)\\.(crdownload|part|partial|download|tmp|temp|1dm|ucd|dl)$"), "")
            .substringBeforeLast('.')
            .trim()
        if (cleaned.isBlank()) cleaned = rawName
        return cleaned
    }

    private fun estimateResolutionLabel(width: Int, height: Int, sizeBytes: Long): String {
        val maxDim = maxOf(width, height)
        return when {
            maxDim >= 7000 || sizeBytes > 600_000_000L -> "8K"
            maxDim >= 3500 || sizeBytes > 180_000_000L -> "4K"
            maxDim >= 2400 || sizeBytes > 90_000_000L -> "2K"
            maxDim >= 1800 || sizeBytes > 25_000_000L -> "1080P"
            maxDim >= 1200 || sizeBytes > 8_000_000L -> "720P"
            maxDim >= 800 || sizeBytes > 3_000_000L -> "480P"
            maxDim >= 400 -> "360P"
            else -> "144P"
        }
    }

    private fun loadUserPickedUris(context: Context, isVideo: Boolean): List<MediaItem> {
        val prefs = context.getSharedPreferences(SNAPSHOT_PREFS, Context.MODE_PRIVATE)
        val key = if (isVideo) KEY_PICKED_VIDEOS else KEY_PICKED_AUDIOS
        val uriStrings = prefs.getStringSet(key, emptySet()) ?: emptySet()
        if (uriStrings.isEmpty()) return emptyList()

        val list = mutableListOf<MediaItem>()
        for (uriStr in uriStrings) {
            try {
                val uri = Uri.parse(uriStr)
                var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: if (isVideo) "Video" else "Audio"
                var size = 0L
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0) {
                            displayName = c.getString(nameIdx) ?: displayName
                        }
                        if (sizeIdx >= 0) {
                            size = c.getLong(sizeIdx).coerceAtLeast(0L)
                        }
                    }
                }
                var duration = 0L
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } catch (_: Exception) {
                } finally {
                    try {
                        retriever.release()
                    } catch (_: Exception) {
                    }
                }
                val isPartial = isFileNamePartialDownload(displayName)
                val ext = displayName.substringAfterLast('.', if (isVideo) "MP4" else "MP3").uppercase(Locale.US)
                list.add(
                    MediaItem(
                        id = "picked_${uriStr.hashCode()}",
                        title = if (isPartial) cleanPartialFileName(displayName) else displayName.substringBeforeLast('.'),
                        artist = if (isPartial) "Pending Download" else if (isVideo) "Local Video" else "Local Artist",
                        duration = duration.coerceAtLeast(1000L),
                        size = size,
                        path = uriStr,
                        isVideo = isVideo,
                        folderName = "Picked Files",
                        albumName = "Local Device",
                        resolution = if (isVideo) estimateResolutionLabel(0, 0, size) else "Hi-Res",
                        format = if (isPartial) "PENDING" else ext,
                        isPartialDownload = isPartial,
                        dateAdded = System.currentTimeMillis()
                    )
                )
            } catch (_: Exception) {
            }
        }
        return list
    }

    private fun isRingtoneOrSystemSoundPath(pathOrFolder: String): Boolean {
        val lower = pathOrFolder.lowercase(Locale.US)
        return lower.contains("/ringtones") ||
            lower.contains("/notifications") ||
            lower.contains("/alarms") ||
            lower.contains("/system/media") ||
            lower.contains("/ui/") ||
            lower == "ringtones" ||
            lower == "notifications" ||
            lower == "alarms"
    }

    private fun queryMediaStore(context: Context, isVideo: Boolean): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        val seenIds = HashSet<Long>()
        val resolver: ContentResolver = context.contentResolver

        val urisToQuery = mutableListOf<Uri>()
        if (isVideo) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                urisToQuery.add(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL))
                urisToQuery.add(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY))
            }
            urisToQuery.add(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        } else {
            // NEVER query INTERNAL_CONTENT_URI for Audio — that contains Android system ringtones, alarms & UI clicks!
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                urisToQuery.add(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL))
                urisToQuery.add(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY))
            }
            urisToQuery.add(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        }

        // Use strictly universal columns so no OEM ContentProvider ever throws IllegalArgumentException
        val baseProjection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.TITLE,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT
        )
        if (!isVideo) {
            baseProjection.add(MediaStore.Audio.AudioColumns.IS_RINGTONE)
            baseProjection.add(MediaStore.Audio.AudioColumns.IS_NOTIFICATION)
            baseProjection.add(MediaStore.Audio.AudioColumns.IS_ALARM)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            baseProjection.add(MediaStore.MediaColumns.DURATION)
            baseProjection.add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            baseProjection.add(MediaStore.MediaColumns.IS_PENDING)
        } else {
            @Suppress("DEPRECATION")
            baseProjection.add(if (isVideo) MediaStore.Video.VideoColumns.DURATION else MediaStore.Audio.AudioColumns.DURATION)
            @Suppress("DEPRECATION")
            if (isVideo) baseProjection.add(MediaStore.Video.VideoColumns.BUCKET_DISPLAY_NAME)
        }

        for (baseUri in urisToQuery.distinct()) {
            try {
                // On Android 10+, also include pending files if possible
                val queryUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        MediaStore.setIncludePending(baseUri)
                    } catch (_: Exception) {
                        baseUri
                    }
                } else {
                    baseUri
                }

                resolver.query(
                    queryUri,
                    baseProjection.toTypedArray(),
                    null,
                    null,
                    "${MediaStore.MediaColumns.DATE_ADDED} DESC"
                )?.use { c ->
                    val idCol = c.getColumnIndex(MediaStore.MediaColumns._ID)
                    val titleCol = c.getColumnIndex(MediaStore.MediaColumns.TITLE)
                    val displayCol = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val durCol = c.getColumnIndex("duration")
                    val sizeCol = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    val dataCol = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                    val dateCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                    val bucketCol = c.getColumnIndex("bucket_display_name")
                    val widthCol = c.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                    val heightCol = c.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                    val pendingCol = c.getColumnIndex("is_pending")
                    val ringtoneCol = c.getColumnIndex(MediaStore.Audio.AudioColumns.IS_RINGTONE)
                    val notifCol = c.getColumnIndex(MediaStore.Audio.AudioColumns.IS_NOTIFICATION)
                    val alarmCol = c.getColumnIndex(MediaStore.Audio.AudioColumns.IS_ALARM)

                    while (c.moveToNext()) {
                        val numericId = if (idCol >= 0) c.getLong(idCol) else continue
                        if (!seenIds.add(numericId)) continue

                        val contentUri = ContentUris.withAppendedId(baseUri, numericId)
                        val rawDataPath = if (dataCol >= 0) c.getString(dataCol) ?: "" else ""
                        val bucketName = if (bucketCol >= 0) c.getString(bucketCol) ?: "" else ""

                        // Exclude system ringtones, notifications, and alarms from the Audio section
                        if (!isVideo) {
                            val isRingtone = if (ringtoneCol >= 0) c.getInt(ringtoneCol) != 0 else false
                            val isNotif = if (notifCol >= 0) c.getInt(notifCol) != 0 else false
                            val isAlarm = if (alarmCol >= 0) c.getInt(alarmCol) != 0 else false
                            if (isRingtone || isNotif || isAlarm) continue
                            if (isRingtoneOrSystemSoundPath(rawDataPath) || isRingtoneOrSystemSoundPath(bucketName)) continue
                        }

                        val dataFile = if (rawDataPath.isNotBlank()) File(rawDataPath) else null

                        val playablePath = if (dataFile != null && dataFile.exists() && dataFile.canRead()) {
                            dataFile.absolutePath
                        } else {
                            contentUri.toString()
                        }

                        val displayName = (if (displayCol >= 0) c.getString(displayCol) else null) ?: ""
                        val isPendingFlag = (if (pendingCol >= 0) c.getInt(pendingCol) else 0) == 1
                        val isPartial = isVideo && (isPendingFlag || isFileNamePartialDownload(displayName) || isFileNamePartialDownload(rawDataPath))

                        val rawTitle = if (isPartial) {
                            cleanPartialFileName(displayName.ifBlank { dataFile?.name ?: "Pending Video" })
                        } else {
                            (if (titleCol >= 0) c.getString(titleCol) else null)
                                ?.takeIf { it.isNotBlank() }
                                ?: displayName.substringBeforeLast('.').ifBlank {
                                    dataFile?.nameWithoutExtension ?: "Media $numericId"
                                }
                        }

                        val duration = if (durCol >= 0) c.getLong(durCol).coerceAtLeast(0L) else 0L
                        val size = if (sizeCol >= 0) c.getLong(sizeCol).coerceAtLeast(0L) else (dataFile?.length() ?: 0L)
                        if (size <= 0L && dataFile != null && !dataFile.exists()) continue

                        // Skip tiny audio sound effects (< 5 seconds or < 24KB)
                        if (!isVideo && ((duration in 1..4999L) || size < 24_000L)) continue

                        val width = if (widthCol >= 0) c.getInt(widthCol) else 0
                        val height = if (heightCol >= 0) c.getInt(heightCol) else 0

                        val dateSec = if (dateCol >= 0) c.getLong(dateCol) else 0L
                        val dateAddedMs = if (dateSec > 0L) dateSec * 1000L else (dataFile?.lastModified() ?: System.currentTimeMillis())

                        val folderName = bucketName.takeIf { it.isNotBlank() }
                            ?: dataFile?.parentFile?.name
                            ?: if (isVideo) "Videos" else "Music"

                        val extSource = displayName.ifBlank { rawDataPath }
                        val ext = extSource.substringAfterLast('.', if (isVideo) "MP4" else "MP3").uppercase(Locale.US)

                        list.add(
                            MediaItem(
                                id = "${if (isVideo) "vid" else "aud"}_$numericId",
                                title = rawTitle,
                                artist = if (isPartial) "Pending Download" else folderName,
                                duration = duration.coerceAtLeast(1000L),
                                size = size.coerceAtLeast(512L),
                                path = playablePath,
                                isVideo = isVideo,
                                folderName = folderName,
                                albumName = folderName,
                                resolution = if (isVideo) estimateResolutionLabel(width, height, size) else "Hi-Res",
                                format = if (isPartial) "PENDING" else ext,
                                isPartialDownload = isPartial,
                                dateAdded = dateAddedMs
                            )
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }
        return list
    }

    /**
     * Queries MediaStore.Files for partially downloaded Chrome / browser video files
     * (.crdownload, .part, .partial, .download) in Download/ that aren't indexed in MediaStore.Video yet.
     */
    private fun queryPendingAndPartialVideoFiles(context: Context): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        try {
            val filesUri = MediaStore.Files.getContentUri("external")
            val queryUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    MediaStore.setIncludePending(filesUri)
                } catch (_: Exception) {
                    filesUri
                }
            } else {
                filesUri
            }
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.DATE_ADDED,
                MediaStore.Files.FileColumns.MIME_TYPE
            )
            val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.crdownload' OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.part' OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.partial' OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.download' OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '.pending-%'"

            context.contentResolver.query(
                queryUri,
                projection,
                selection,
                null,
                "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
            )?.use { c ->
                val idCol = c.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val nameCol = c.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = c.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val dataCol = c.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                val dateCol = c.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)

                while (c.moveToNext()) {
                    val id = if (idCol >= 0) c.getLong(idCol) else continue
                    val name = if (nameCol >= 0) c.getString(nameCol) ?: "" else ""
                    val rawPath = if (dataCol >= 0) c.getString(dataCol) ?: "" else ""
                    val size = if (sizeCol >= 0) c.getLong(sizeCol) else 0L
                    if (size < 512L) continue

                    val contentUri = ContentUris.withAppendedId(filesUri, id)
                    val file = if (rawPath.isNotBlank()) File(rawPath) else null
                    val playablePath = if (file != null && file.exists() && file.canRead()) {
                        file.absolutePath
                    } else {
                        contentUri.toString()
                    }
                    val dateSec = if (dateCol >= 0) c.getLong(dateCol) else 0L
                    val dateMs = if (dateSec > 0L) dateSec * 1000L else System.currentTimeMillis()

                    list.add(
                        MediaItem(
                            id = "pending_file_$id",
                            title = cleanPartialFileName(name.ifBlank { file?.name ?: "Downloading Video" }),
                            artist = "Pending Download",
                            duration = 1000L,
                            size = size,
                            path = playablePath,
                            isVideo = true,
                            folderName = file?.parentFile?.name ?: "Download",
                            albumName = "Download",
                            resolution = estimateResolutionLabel(0, 0, size),
                            format = "PENDING",
                            isPartialDownload = true,
                            dateAdded = dateMs
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return list
    }

    /**
     * Ultra-Fast Direct File & Partial Download Scanner (>512B threshold, zero blocking Retriever calls).
     */
    private fun scanRealDeviceDirectories(context: Context, isVideo: Boolean): List<MediaItem> {
        val results = mutableListOf<MediaItem>()
        val targetDirs = mutableSetOf<File>()

        try {
            val externalRoot = Environment.getExternalStorageDirectory()
            if (externalRoot != null && externalRoot.exists()) {
                targetDirs.add(File(externalRoot, "Download"))
                targetDirs.add(File(externalRoot, "Movies"))
                targetDirs.add(File(externalRoot, "DCIM"))
                targetDirs.add(File(externalRoot, "DCIM/Camera"))
                targetDirs.add(File(externalRoot, "DCIM/Screenshots"))
                targetDirs.add(File(externalRoot, "Pictures"))
                targetDirs.add(File(externalRoot, "Video"))
                targetDirs.add(File(externalRoot, "Videos"))
                targetDirs.add(File(externalRoot, "Music"))
                targetDirs.add(File(externalRoot, "Recordings"))
                targetDirs.add(File(externalRoot, "WhatsApp/Media/WhatsApp Video"))
                targetDirs.add(File(externalRoot, "WhatsApp/Media/WhatsApp Audio"))
                targetDirs.add(File(externalRoot, "WhatsApp/Media/.Statuses"))
                targetDirs.add(File(externalRoot, "WhatsApp Business/Media/.Statuses"))
                targetDirs.add(File(externalRoot, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video"))
                targetDirs.add(File(externalRoot, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio"))
                targetDirs.add(File(externalRoot, "Android/media/com.whatsapp/WhatsApp/Media/.Statuses"))
                targetDirs.add(File(externalRoot, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"))
                targetDirs.add(File(externalRoot, "Telegram/Telegram Video"))
                targetDirs.add(File(externalRoot, "Telegram/Telegram Audio"))
                targetDirs.add(File(externalRoot, "Snaptube"))
                targetDirs.add(File(externalRoot, "VidMate"))
            }
            context.getExternalFilesDir(null)?.let { targetDirs.add(it) }
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.let { targetDirs.add(it) }
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { targetDirs.add(it) }
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let { targetDirs.add(it) }
        } catch (_: Exception) {
        }

        val validExtensions = if (isVideo) VIDEO_EXTENSIONS else AUDIO_EXTENSIONS

        for (dir in targetDirs) {
            if (!dir.exists() || !dir.canRead()) continue
            scanDirectoryRecursive(dir, depth = 0, maxDepth = 3, isVideo = isVideo, validExtensions = validExtensions, out = results)
        }
        return results
    }

    private fun scanDirectoryRecursive(
        dir: File,
        depth: Int,
        maxDepth: Int,
        isVideo: Boolean,
        validExtensions: Set<String>,
        out: MutableList<MediaItem>
    ) {
        if (depth > maxDepth) return
        if (!isVideo && isRingtoneOrSystemSoundPath(dir.absolutePath)) return

        val children = try {
            dir.listFiles()
        } catch (_: Exception) {
            null
        } ?: return

        for (file in children) {
            if (file.isDirectory) {
                if (!file.name.startsWith(".trashed") && (isVideo || !isRingtoneOrSystemSoundPath(file.name))) {
                    scanDirectoryRecursive(file, depth + 1, maxDepth, isVideo, validExtensions, out)
                }
            } else if (file.isFile && file.length() >= if (isVideo) 512L else 24_000L) {
                val ext = file.extension.lowercase(Locale.US)
                val isPartial = isVideo && isFileNamePartialDownload(file.name)
                if (ext in validExtensions || isPartial) {
                    val folderLabel = if (dir.name == ".Statuses" || dir.absolutePath.contains(".Statuses")) {
                        "Status"
                    } else {
                        dir.name.ifBlank { "Storage" }
                    }
                    // Estimate duration lightweightly or set to <=100s for Status clips
                    val estimatedDuration = when {
                        folderLabel == "Status" -> 30_000L
                        file.length() < 8_000_000L -> 45_000L
                        else -> 120_000L
                    }
                    out.add(
                        MediaItem(
                            id = "file_${file.absolutePath.hashCode()}",
                            title = if (isPartial) cleanPartialFileName(file.name) else file.nameWithoutExtension,
                            artist = if (isPartial) "Pending Download" else folderLabel,
                            duration = estimatedDuration,
                            size = file.length(),
                            path = file.absolutePath,
                            isVideo = isVideo,
                            folderName = folderLabel,
                            albumName = folderLabel,
                            resolution = if (isVideo) estimateResolutionLabel(0, 0, file.length()) else "Hi-Res",
                            format = if (isPartial) "PENDING" else ext.uppercase(Locale.US),
                            isPartialDownload = isPartial,
                            dateAdded = file.lastModified()
                        )
                    )
                }
            }
        }
    }

    private fun saveSnapshot(context: Context, isVideo: Boolean, items: List<MediaItem>) {
        val realOnly = items.filterNot { it.path.startsWith("http", ignoreCase = true) }
        val serialized = realOnly.take(250).joinToString("\n") { item ->
            listOf(
                item.id.replace("|", ""),
                item.title.replace("|", " "),
                item.artist.replace("|", " "),
                item.duration.toString(),
                item.size.toString(),
                item.path.replace("|", ""),
                item.folderName.replace("|", " "),
                item.albumName.replace("|", " "),
                item.resolution.replace("|", ""),
                item.isPartialDownload.toString(),
                item.format.replace("|", ""),
                item.dateAdded.toString()
            ).joinToString("|")
        }
        context.getSharedPreferences(SNAPSHOT_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(if (isVideo) KEY_VIDEO_SNAPSHOT else KEY_AUDIO_SNAPSHOT, serialized)
            .apply()
    }

    private fun deserializeSnapshot(raw: String, isVideo: Boolean): List<MediaItem> {
        return raw.lines().mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size < 12) return@mapNotNull null
            val path = parts[5]
            if (path.startsWith("http", ignoreCase = true)) return@mapNotNull null
            try {
                MediaItem(
                    id = parts[0],
                    title = parts[1],
                    artist = parts[2],
                    duration = parts[3].toLongOrNull() ?: 0L,
                    size = parts[4].toLongOrNull() ?: 0L,
                    path = path,
                    isVideo = isVideo,
                    folderName = parts[6],
                    albumName = parts[7],
                    resolution = parts[8],
                    isPartialDownload = parts[9].toBooleanStrictOrNull() ?: (parts[10] == "PENDING"),
                    format = parts[10],
                    dateAdded = parts[11].toLongOrNull() ?: 0L
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
