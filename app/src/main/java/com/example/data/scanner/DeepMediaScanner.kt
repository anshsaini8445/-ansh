package com.example.data.scanner

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
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
 * - Detects partially downloaded / in-progress Chrome, Samsung Internet, Opera, Firefox, 1DM, ADM,
 *   Telegram & DownloadManager videos (.crdownload, .part, .partial, .download, .tmp, .opdownload,
 *   .sdownload, .pending-*, IS_PENDING=1) via MediaStore.Video, MediaStore.Downloads, MediaStore.Files,
 *   Direct Directories, and optional SAF Downloads folder.
 * - Scans WhatsApp & WhatsApp Business .Statuses across Android 6-17 using both Direct Filesystem
 *   and persisted Storage Access Framework (SAF) folder permissions.
 */
object DeepMediaScanner {

    enum class SafFolderTarget(
        val prefKey: String,
        val modernRelativePath: String,
        val legacyRelativePath: String
    ) {
        WHATSAPP_STATUS(
            prefKey = "saf_wa_status_tree_uri_v1",
            modernRelativePath = "Android/media/com.whatsapp/WhatsApp/Media/.Statuses",
            legacyRelativePath = "WhatsApp/Media/.Statuses"
        ),
        WHATSAPP_BUSINESS_STATUS(
            prefKey = "saf_wa_biz_status_tree_uri_v1",
            modernRelativePath = "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses",
            legacyRelativePath = "WhatsApp Business/Media/.Statuses"
        ),
        DOWNLOADS_PENDING(
            prefKey = "saf_downloads_pending_tree_uri_v1",
            modernRelativePath = "Download",
            legacyRelativePath = "Download"
        )
    }

    private const val LEGACY_DUMMY_PREFS = "HYBRID_INSTANT_VIDEO_SNAPSHOT"
    private const val SNAPSHOT_PREFS = "HYBRID_REAL_MEDIA_SNAPSHOT_V4"
    private const val KEY_VIDEO_SNAPSHOT = "real_video_rows_v4"
    private const val KEY_AUDIO_SNAPSHOT = "real_audio_rows_v4"
    private const val KEY_PICKED_VIDEOS = "user_picked_video_uris_v4"
    private const val KEY_PICKED_AUDIOS = "user_picked_audio_uris_v4"

    val PARTIAL_DOWNLOAD_EXTENSIONS = setOf(
        "crdownload", "part", "partial", "temp", "tmp", "download", "dl", "dltemp", "incomplete",
        "1dm", "ucd", "idm", "adm", "td", "bin", "opdownload", "sdownload", "fbdownload", "aria2", "bc!", "!ut"
    )

    private val NON_VIDEO_BASE_EXTENSIONS = setOf(
        "apk", "apks", "xapk", "zip", "rar", "7z", "tar", "gz", "bz2", "iso", "img", "exe", "msi", "dmg",
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "rtf", "epub", "mobi",
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "ico", "heic",
        "mp3", "wav", "flac", "aac", "m4a", "ogg", "opus", "wma", "amr",
        "json", "xml", "html", "htm", "css", "js", "ttf", "otf", "woff", "db", "sqlite", "log"
    )

    val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "flv", "ts", "m4v", "3gp", "3gpp", "wmv",
        "hevc", "h265", "av1", "divx", "vob", "ogv", "m2ts", "mts", "rmvb", "mpeg", "mpg"
    )

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
                                try {
                                    retriever.setDataSource(file.absolutePath)
                                } catch (_: Throwable) {
                                    java.io.FileInputStream(file).use { fis ->
                                        retriever.setDataSource(fis.fd, 0L, file.length())
                                    }
                                }
                                if (isVideo) {
                                    extracted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                        retriever.getScaledFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 240, 136)
                                            ?: retriever.getScaledFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 240, 136)
                                            ?: retriever.getFrameAtTime(0L)
                                    } else {
                                        retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                            ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                            ?: retriever.getFrameAtTime(0L)
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
                                // Final fallback via raw FileDescriptor if setDataSource(path) accepted path but failed during extract
                                if (isVideo && extracted == null) {
                                    val fdRetriever = MediaMetadataRetriever()
                                    try {
                                        java.io.FileInputStream(file).use { fis ->
                                            fdRetriever.setDataSource(fis.fd, 0L, file.length())
                                        }
                                        extracted = fdRetriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                            ?: fdRetriever.getFrameAtTime(0L)
                                    } catch (_: Throwable) {
                                    } finally {
                                        try { fdRetriever.release() } catch (_: Throwable) {}
                                    }
                                }
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
     * Sorts Pending/Partial downloads at the VERY TOP, followed by newest videos.
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
                val existing = discoveredMap[item.path]
                if (existing == null || !existing.isPartialDownload) {
                    discoveredMap[item.path] = item
                }
            }
        }

        // 3. Fast Direct Directory & Partial File Scanner
        val directFiles = scanRealDeviceDirectories(context, isVideo)
        for (item in directFiles) {
            if (PermanentDeleteHelper.isHiddenByTrash(context, item.id, item.path)) continue
            val existing = discoveredMap[item.path]
            if (existing == null || (item.isPartialDownload && !existing.isPartialDownload)) {
                discoveredMap[item.path] = item
            }
        }

        // 4. Persisted SAF Folders (WhatsApp .Statuses, WhatsApp Business .Statuses, Pending Downloads)
        if (isVideo) {
            val safItems = scanAllPersistedSafVideos(context)
            for (item in safItems) {
                if (PermanentDeleteHelper.isHiddenByTrash(context, item.id, item.path)) continue
                val existing = discoveredMap[item.path]
                if (existing == null || (item.isPartialDownload && !existing.isPartialDownload)) {
                    discoveredMap[item.path] = item
                }
            }
        }

        // 5. User-Picked Persistent Document URIs
        val pickedItems = loadUserPickedUris(context, isVideo)
        for (item in pickedItems) {
            if (PermanentDeleteHelper.isHiddenByTrash(context, item.id, item.path)) continue
            if (!discoveredMap.containsKey(item.path)) {
                discoveredMap[item.path] = item
            }
        }

        // Sort: All Pending/Partial downloads at the top (newest first), then by dateAdded descending
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

    /**
     * Builds an Android SAF OpenDocumentTree intent pre-opened directly to the exact
     * WhatsApp .Statuses, WhatsApp Business .Statuses, or Download folder (1-tap "Use this folder").
     */
    fun buildSafFolderPickerIntent(context: Context, target: SafFolderTarget): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chosenRelPath = try {
                val root = Environment.getExternalStorageDirectory()
                val modernDir = if (root != null) File(root, target.modernRelativePath) else null
                val legacyDir = if (root != null) File(root, target.legacyRelativePath) else null
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> target.modernRelativePath
                    modernDir != null && modernDir.exists() -> target.modernRelativePath
                    legacyDir != null && legacyDir.exists() -> target.legacyRelativePath
                    else -> target.modernRelativePath
                }
            } catch (_: Exception) {
                target.modernRelativePath
            }
            val encodedPath = Uri.encode(chosenRelPath)
            val initialUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3A$encodedPath")
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
        }
        return intent
    }

    fun saveSafFolderTreeUri(context: Context, target: SafFolderTarget, treeUri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        context.getSharedPreferences(SNAPSHOT_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(target.prefKey, treeUri.toString())
            .apply()
        invalidateMemoryCache()
    }

    fun getSavedSafTreeUri(context: Context, target: SafFolderTarget): Uri? {
        val raw = context.getSharedPreferences(SNAPSHOT_PREFS, Context.MODE_PRIVATE)
            .getString(target.prefKey, null)
            ?.takeIf { it.isNotBlank() } ?: return null
        return try {
            val uri = Uri.parse(raw)
            val hasPerm = context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission
            }
            if (hasPerm) uri else null
        } catch (_: Exception) {
            null
        }
    }

    fun hasSafFolderPermission(context: Context, target: SafFolderTarget): Boolean {
        return getSavedSafTreeUri(context, target) != null
    }

    /**
     * Exact status source badge label ("WhatsApp Business" or "WhatsApp") or null if regular video.
     */
    fun statusSourceLabel(item: MediaItem): String? {
        val combined = "${item.id} ${item.folderName} ${item.artist} ${Uri.decode(item.path)}".lowercase(Locale.US)
        return when {
            combined.contains("wa_biz") ||
                combined.contains("com.whatsapp.w4b") ||
                combined.contains("whatsapp business") ||
                combined.contains("wa business") -> "WhatsApp Business"
            combined.contains("wa_status") ||
                combined.contains("wa_saf") ||
                combined.contains("com.whatsapp") ||
                combined.contains("whatsapp") ||
                combined.contains(".statuses") -> "WhatsApp"
            else -> null
        }
    }

    fun isWhatsAppStatusVideo(item: MediaItem): Boolean {
        val combined = "${item.id} ${item.folderName} ${item.artist} ${Uri.decode(item.path)}".lowercase(Locale.US)
        return combined.contains(".statuses") ||
            combined.contains("wa_status") ||
            combined.contains("wa_biz_status") ||
            combined.contains("wa_saf") ||
            combined.contains("wa_biz_saf") ||
            combined.contains("whatsapp status") ||
            combined.contains("whatsapp business status")
    }

    /**
     * Dedicated real-time WhatsApp & WhatsApp Business Status scanner:
     * 1. Persisted SAF trees (works on Android 11 - Android 17 Scoped Storage)
     * 2. Direct filesystem .Statuses folders (Android 6-10 + direct-access OEMs + Dual/Clone)
     * 3. MediaStore .Statuses / WhatsApp Status records
     */
    suspend fun scanWhatsAppStatusesNow(context: Context): List<MediaItem> = withContext(Dispatchers.IO) {
        val collected = LinkedHashMap<String, MediaItem>()
        val dedupSignature = HashSet<String>()

        fun addUnique(item: MediaItem) {
            if (PermanentDeleteHelper.isHiddenByTrash(context, item.id, item.path)) return
            val sig = "${item.cleanTitle.lowercase(Locale.US)}_${item.size}"
            if (!collected.containsKey(item.path) && dedupSignature.add(sig)) {
                collected[item.path] = item
            }
        }

        // 1. Persisted SAF Trees for WhatsApp & WhatsApp Business
        getSavedSafTreeUri(context, SafFolderTarget.WHATSAPP_STATUS)?.let { uri ->
            querySafTreeVideos(context, uri, SafFolderTarget.WHATSAPP_STATUS, depth = 0).forEach(::addUnique)
        }
        getSavedSafTreeUri(context, SafFolderTarget.WHATSAPP_BUSINESS_STATUS)?.let { uri ->
            querySafTreeVideos(context, uri, SafFolderTarget.WHATSAPP_BUSINESS_STATUS, depth = 0).forEach(::addUnique)
        }

        // 2. Direct Filesystem .Statuses folders (Android 6-10 & Dual/Parallel apps)
        try {
            val extRoot = Environment.getExternalStorageDirectory()
            if (extRoot != null && extRoot.exists()) {
                val waDirs = listOf(
                    File(extRoot, "Android/media/com.whatsapp/WhatsApp/Media/.Statuses") to "WhatsApp",
                    File(extRoot, "WhatsApp/Media/.Statuses") to "WhatsApp",
                    File(extRoot, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses") to "WhatsApp Business",
                    File(extRoot, "WhatsApp Business/Media/.Statuses") to "WhatsApp Business",
                    File(extRoot, "DualApp/Android/media/com.whatsapp/WhatsApp/Media/.Statuses") to "WhatsApp",
                    File(extRoot, "DualApp/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses") to "WhatsApp Business"
                )
                for ((dir, sourceLabel) in waDirs) {
                    if (!dir.exists() || !dir.isDirectory || !dir.canRead()) continue
                    val files = try { dir.listFiles() } catch (_: Exception) { null } ?: continue
                    val isBiz = sourceLabel == "WhatsApp Business"
                    for (file in files) {
                        if (!file.isFile || file.length() < 1024L) continue
                        val ext = file.extension.lowercase(Locale.US)
                        if (ext !in VIDEO_EXTENSIONS) continue
                        addUnique(
                            MediaItem(
                                id = "${if (isBiz) "wa_biz_status" else "wa_status"}_${file.absolutePath.hashCode()}",
                                title = file.nameWithoutExtension.ifBlank { "$sourceLabel Status" },
                                artist = "$sourceLabel Status",
                                duration = estimatePartialDurationMs(file.length()).coerceIn(5_000L, 60_000L),
                                size = file.length(),
                                path = file.absolutePath,
                                isVideo = true,
                                folderName = sourceLabel,
                                albumName = "$sourceLabel Status",
                                resolution = estimateResolutionLabel(0, 0, file.length()),
                                format = ext.uppercase(Locale.US),
                                isPartialDownload = false,
                                dateAdded = file.lastModified()
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }

        // 3. MediaStore fallback for any indexed .Statuses clips
        try {
            val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val proj = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DURATION
            )
            val sel = "${MediaStore.Video.Media.DATA} LIKE '%.Statuses%'"
            context.contentResolver.query(uri, proj, sel, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { c ->
                val idCol = c.getColumnIndex(MediaStore.Video.Media._ID)
                val nameCol = c.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = c.getColumnIndex(MediaStore.Video.Media.SIZE)
                val dataCol = c.getColumnIndex(MediaStore.Video.Media.DATA)
                val dateCol = c.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                val durCol = c.getColumnIndex(MediaStore.Video.Media.DURATION)
                while (c.moveToNext()) {
                    val id = if (idCol >= 0) c.getLong(idCol) else continue
                    val name = if (nameCol >= 0) c.getString(nameCol) ?: "Status" else "Status"
                    val rawPath = if (dataCol >= 0) c.getString(dataCol) ?: "" else ""
                    val size = if (sizeCol >= 0) c.getLong(sizeCol).coerceAtLeast(1024L) else 1024L
                    val dur = if (durCol >= 0) c.getLong(durCol).coerceAtLeast(5_000L) else 15_000L
                    val dateMs = (if (dateCol >= 0) c.getLong(dateCol) else 0L) * 1000L
                    val isBiz = rawPath.contains("w4b", ignoreCase = true) || rawPath.contains("WhatsApp Business", ignoreCase = true)
                    val sourceLabel = if (isBiz) "WhatsApp Business" else "WhatsApp"
                    val contentUri = ContentUris.withAppendedId(uri, id)
                    val file = if (rawPath.isNotBlank()) File(rawPath) else null
                    val playablePath = if (file != null && file.exists() && file.canRead()) file.absolutePath else contentUri.toString()
                    addUnique(
                        MediaItem(
                            id = "${if (isBiz) "wa_biz_status" else "wa_status"}_ms_$id",
                            title = name.substringBeforeLast('.').ifBlank { "$sourceLabel Status" },
                            artist = "$sourceLabel Status",
                            duration = dur,
                            size = size,
                            path = playablePath,
                            isVideo = true,
                            folderName = sourceLabel,
                            albumName = "$sourceLabel Status",
                            resolution = estimateResolutionLabel(0, 0, size),
                            format = name.substringAfterLast('.', "MP4").uppercase(Locale.US),
                            isPartialDownload = false,
                            dateAdded = if (dateMs > 0L) dateMs else System.currentTimeMillis()
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }

        collected.values.sortedByDescending { it.dateAdded }
    }

    private fun scanAllPersistedSafVideos(context: Context): List<MediaItem> {
        val out = mutableListOf<MediaItem>()
        for (target in SafFolderTarget.entries) {
            val treeUri = getSavedSafTreeUri(context, target) ?: continue
            out.addAll(querySafTreeVideos(context, treeUri, target, depth = 0))
        }
        return out
    }

    private fun querySafTreeVideos(
        context: Context,
        treeUri: Uri,
        target: SafFolderTarget,
        depth: Int,
        parentDocId: String? = null
    ): List<MediaItem> {
        if (depth > 2) return emptyList()
        val results = mutableListOf<MediaItem>()
        try {
            val docId = parentDocId ?: DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
                val idCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (c.moveToNext()) {
                    val childId = if (idCol >= 0) c.getString(idCol) ?: continue else continue
                    val name = if (nameCol >= 0) c.getString(nameCol) ?: "" else ""
                    val mime = if (mimeCol >= 0) c.getString(mimeCol) ?: "" else ""
                    val size = if (sizeCol >= 0) c.getLong(sizeCol).coerceAtLeast(0L) else 0L
                    val lastMod = if (modCol >= 0) c.getLong(modCol).coerceAtLeast(0L) else System.currentTimeMillis()

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (target == SafFolderTarget.DOWNLOADS_PENDING && depth < 1 && !name.startsWith(".trashed")) {
                            results.addAll(querySafTreeVideos(context, treeUri, target, depth + 1, childId))
                        }
                        continue
                    }

                    if (name.isBlank() || name == ".nomedia") continue
                    val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
                    val isPartial = isFileNamePartialDownload(name, null)
                    val isVideoFile = mime.startsWith("video/", ignoreCase = true) || ext in VIDEO_EXTENSIONS
                    if (!isVideoFile && !isPartial) continue
                    if (size < 512L && !isPartial) continue

                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                    val (idPrefix, folderLabel, artistLabel) = when (target) {
                        SafFolderTarget.WHATSAPP_STATUS -> Triple("wa_saf", "WhatsApp", "WhatsApp Status")
                        SafFolderTarget.WHATSAPP_BUSINESS_STATUS -> Triple("wa_biz_saf", "WhatsApp Business", "WhatsApp Business Status")
                        SafFolderTarget.DOWNLOADS_PENDING -> Triple(
                            if (isPartial) "pending_saf" else "dl_saf",
                            "Download",
                            if (isPartial) "Pending Download" else "Download"
                        )
                    }

                    results.add(
                        MediaItem(
                            id = "${idPrefix}_${childId.hashCode()}",
                            title = if (isPartial) cleanPartialFileName(name) else name.substringBeforeLast('.').ifBlank { name },
                            artist = artistLabel,
                            duration = if (isPartial) estimatePartialDurationMs(size) else estimatePartialDurationMs(size).coerceIn(5_000L, 60_000L),
                            size = size.coerceAtLeast(512L),
                            path = docUri.toString(),
                            isVideo = true,
                            folderName = folderLabel,
                            albumName = artistLabel,
                            resolution = estimateResolutionLabel(0, 0, size),
                            format = if (isPartial) "PENDING" else ext.ifBlank { "MP4" }.uppercase(Locale.US),
                            isPartialDownload = isPartial,
                            dateAdded = if (lastMod > 0L) lastMod else System.currentTimeMillis()
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return results
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

    private fun isLikelyNonVideoMagicHeader(file: File): Boolean {
        return try {
            if (!file.exists() || !file.canRead() || file.length() < 8L) return false
            val header = ByteArray(12)
            val read = java.io.FileInputStream(file).use { it.read(header) }
            if (read < 4) return false
            // PK\x03\x04 (ZIP / APK / DOCX / JAR)
            if (header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() && header[2] == 0x03.toByte() && header[3] == 0x04.toByte()) return true
            // %PDF
            if (header[0] == 0x25.toByte() && header[1] == 0x50.toByte() && header[2] == 0x44.toByte() && header[3] == 0x46.toByte()) return true
            // \x89PNG
            if (header[0] == 0x89.toByte() && header[1] == 0x50.toByte() && header[2] == 0x4E.toByte() && header[3] == 0x47.toByte()) return true
            // \xFF\xD8\xFF (JPEG)
            if (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()) return true
            // \x7FELF (Binary)
            if (header[0] == 0x7F.toByte() && header[1] == 0x45.toByte() && header[2] == 0x4C.toByte() && header[3] == 0x46.toByte()) return true
            // Rar!
            if (header[0] == 0x52.toByte() && header[1] == 0x61.toByte() && header[2] == 0x72.toByte() && header[3] == 0x21.toByte()) return true
            // ID3 (MP3 audio)
            if (header[0] == 0x49.toByte() && header[1] == 0x44.toByte() && header[2] == 0x33.toByte()) return true
            false
        } catch (_: Exception) {
            false
        }
    }

    fun isFileNamePartialDownload(nameOrPath: String, file: File? = null): Boolean {
        val decoded = try { Uri.decode(nameOrPath) } catch (_: Exception) { nameOrPath }
        val fileName = decoded.substringAfterLast('/').substringAfterLast(':').lowercase(Locale.US)
        if (fileName.isBlank()) return false
        val ext = fileName.substringAfterLast('.', "")
        val isPartialSuffix = ext in PARTIAL_DOWNLOAD_EXTENSIONS ||
            fileName.endsWith(".crdownload") ||
            fileName.endsWith(".part") ||
            fileName.endsWith(".partial") ||
            fileName.endsWith(".download") ||
            fileName.endsWith(".opdownload") ||
            fileName.endsWith(".sdownload") ||
            fileName.endsWith(".fbdownload") ||
            fileName.endsWith(".dltemp") ||
            fileName.endsWith(".incomplete") ||
            fileName.endsWith(".1dm") ||
            fileName.endsWith(".adm") ||
            fileName.endsWith(".idm") ||
            fileName.endsWith(".ucd") ||
            fileName.startsWith(".pending-") ||
            fileName.startsWith("unconfirmed ")
        if (!isPartialSuffix) return false

        // Strip partial download suffixes and prefixes to inspect base extension if present
        val stripped = fileName
            .removePrefix(".pending-")
            .replace(Regex("^\\d+-"), "")
            .replace(Regex("\\.(crdownload|part|partial|download|opdownload|sdownload|fbdownload|dltemp|incomplete|tmp|temp|1dm|ucd|idm|adm|td|dl|bin|aria2|bc!|!ut)$"), "")
            .replace(Regex("\\s*\\(\\d+\\)$"), "")

        if (stripped.contains('.')) {
            val baseExt = stripped.substringAfterLast('.', "")
            if (baseExt in NON_VIDEO_BASE_EXTENSIONS) return false
            if (baseExt in VIDEO_EXTENSIONS) return true
        }

        // For single-extension partial files (e.g. "movie.crdownload", "Unconfirmed 12345.crdownload", "videoplayback.part"):
        // Reject only if magic bytes belong to a non-video archive/image/PDF/MP3
        if (file != null && isLikelyNonVideoMagicHeader(file)) {
            return false
        }
        return true
    }

    private fun cleanPartialFileName(rawName: String): String {
        val decoded = try { Uri.decode(rawName) } catch (_: Exception) { rawName }
        var cleaned = decoded
            .substringAfterLast('/')
            .substringAfterLast(':')
            .removePrefix(".pending-")
            .replace(Regex("^\\d+-"), "")
            .replace(Regex("(?i)\\.(crdownload|part|partial|download|opdownload|sdownload|fbdownload|dltemp|incomplete|tmp|temp|1dm|ucd|idm|adm|td|dl|bin|aria2|bc!|!ut)$"), "")
            .substringBeforeLast('.')
            .trim()
        if (cleaned.isBlank()) cleaned = decoded
        return cleaned
    }

    private fun estimatePartialDurationMs(sizeBytes: Long): Long {
        if (sizeBytes <= 0L) return 1000L
        // Approximate ~200 KB/sec for typical mobile video stream, minimum 1000ms (1 second)
        return ((sizeBytes / (200 * 1024f)) * 1000L).toLong().coerceIn(1000L, 7_200_000L)
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
                // On Android 11+ (API 30+), use QUERY_ARG_MATCH_PENDING = MATCH_INCLUDE so half-downloaded videos are ALWAYS returned!
                val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val queryArgs = Bundle().apply {
                        putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
                        putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.MediaColumns.DATE_ADDED))
                        putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                    }
                    resolver.query(baseUri, baseProjection.toTypedArray(), queryArgs, null)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val queryUri = try {
                        @Suppress("DEPRECATION")
                        MediaStore.setIncludePending(baseUri)
                    } catch (_: Exception) {
                        baseUri
                    }
                    resolver.query(
                        queryUri,
                        baseProjection.toTypedArray(),
                        null,
                        null,
                        "${MediaStore.MediaColumns.DATE_ADDED} DESC"
                    )
                } else {
                    resolver.query(
                        baseUri,
                        baseProjection.toTypedArray(),
                        null,
                        null,
                        "${MediaStore.MediaColumns.DATE_ADDED} DESC"
                    )
                }

                cursor?.use { c ->
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
                        val duration = if (durCol >= 0) c.getLong(durCol).coerceAtLeast(0L) else 0L
                        val size = if (sizeCol >= 0) c.getLong(sizeCol).coerceAtLeast(0L) else (dataFile?.length() ?: 0L)
                        val isPendingFlag = (if (pendingCol >= 0) c.getInt(pendingCol) else 0) == 1
                        val isUnfinalizedDownloadVideo = isVideo && duration == 0L && (
                            bucketName.contains("Download", ignoreCase = true) ||
                                bucketName.contains("1DM", ignoreCase = true) ||
                                bucketName.contains("ADM", ignoreCase = true) ||
                                rawDataPath.contains("/Download", ignoreCase = true) ||
                                rawDataPath.contains("/1DM", ignoreCase = true) ||
                                rawDataPath.contains("/ADM", ignoreCase = true)
                            )
                        val isPartial = isVideo && (
                            isPendingFlag ||
                                isUnfinalizedDownloadVideo ||
                                isFileNamePartialDownload(displayName, dataFile) ||
                                isFileNamePartialDownload(rawDataPath, dataFile)
                            )
                        if (!isPartial && size <= 0L && dataFile != null && !dataFile.exists()) continue

                        val rawTitle = if (isPartial) {
                            cleanPartialFileName(displayName.ifBlank { dataFile?.name ?: "Pending Video" })
                        } else {
                            (if (titleCol >= 0) c.getString(titleCol) else null)
                                ?.takeIf { it.isNotBlank() }
                                ?: displayName.substringBeforeLast('.').ifBlank {
                                    dataFile?.nameWithoutExtension ?: "Media $numericId"
                                }
                        }

                        // Skip tiny audio sound effects (< 5 seconds or < 24KB)
                        if (!isVideo && ((duration in 1..4999L) || size < 24_000L)) continue

                        val width = if (widthCol >= 0) c.getInt(widthCol) else 0
                        val height = if (heightCol >= 0) c.getInt(heightCol) else 0

                        val dateSec = if (dateCol >= 0) c.getLong(dateCol) else 0L
                        val dateAddedMs = if (dataFile != null && dataFile.exists() && isPartial) {
                            dataFile.lastModified()
                        } else if (dateSec > 0L) {
                            dateSec * 1000L
                        } else {
                            dataFile?.lastModified() ?: System.currentTimeMillis()
                        }

                        val folderName = bucketName.takeIf { it.isNotBlank() }
                            ?: dataFile?.parentFile?.name
                            ?: if (isVideo) "Videos" else "Music"

                        val extSource = displayName.ifBlank { rawDataPath }
                        val ext = extSource.substringAfterLast('.', if (isVideo) "MP4" else "MP3").uppercase(Locale.US)
                        val effectiveDuration = if (duration > 0L) duration else if (isPartial) estimatePartialDurationMs(size) else 1000L

                        list.add(
                            MediaItem(
                                id = "${if (isVideo) "vid" else "aud"}_$numericId",
                                title = rawTitle,
                                artist = if (isPartial) "Pending Download" else folderName,
                                duration = effectiveDuration.coerceAtLeast(1000L),
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
     * Queries BOTH MediaStore.Files AND MediaStore.Downloads (Android 10+) for all partially downloaded
     * or pending browser/downloader video files (.crdownload, .part, .partial, .download, .opdownload,
     * .sdownload, .tmp, .1dm, .adm, .idm, .ucd, .pending-*, IS_PENDING=1) even if only 1 second is downloaded.
     */
    private fun queryPendingAndPartialVideoFiles(context: Context): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        val seenIds = HashSet<String>()

        val targetUris = mutableListOf<Uri>()
        targetUris.add(MediaStore.Files.getContentUri("external"))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            targetUris.add(MediaStore.Downloads.EXTERNAL_CONTENT_URI)
        }

        val baseProjection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.MIME_TYPE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            baseProjection.add(MediaStore.MediaColumns.IS_PENDING)
        }

        val nameSelection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.crdownload' OR " +
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.part' OR " +
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.partial' OR " +
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.download' OR " +
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.opdownload' OR " +
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.sdownload' OR " +
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.dltemp' OR " +
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.incomplete' OR " +
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.tmp' OR " +
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.1dm' OR " +
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.adm' OR " +
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.idm' OR " +
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.ucd' OR " +
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '.pending-%' OR " +
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE 'Unconfirmed %'"

        val fullSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "($nameSelection) OR ${MediaStore.MediaColumns.IS_PENDING} = 1"
        } else {
            nameSelection
        }

        for (baseUri in targetUris) {
            try {
                val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val args = Bundle().apply {
                        putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
                        putString(ContentResolver.QUERY_ARG_SQL_SELECTION, fullSelection)
                        putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.MediaColumns.DATE_ADDED))
                        putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                    }
                    context.contentResolver.query(baseUri, baseProjection.toTypedArray(), args, null)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val queryUri = try {
                        @Suppress("DEPRECATION")
                        MediaStore.setIncludePending(baseUri)
                    } catch (_: Exception) {
                        baseUri
                    }
                    context.contentResolver.query(
                        queryUri,
                        baseProjection.toTypedArray(),
                        fullSelection,
                        null,
                        "${MediaStore.MediaColumns.DATE_ADDED} DESC"
                    )
                } else {
                    context.contentResolver.query(
                        baseUri,
                        baseProjection.toTypedArray(),
                        fullSelection,
                        null,
                        "${MediaStore.MediaColumns.DATE_ADDED} DESC"
                    )
                }

                cursor?.use { c ->
                    val idCol = c.getColumnIndex(MediaStore.MediaColumns._ID)
                    val nameCol = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeCol = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    val dataCol = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                    val dateCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                    val mimeCol = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                    val pendingCol = c.getColumnIndex("is_pending")

                    while (c.moveToNext()) {
                        val id = if (idCol >= 0) c.getLong(idCol) else continue
                        val name = if (nameCol >= 0) c.getString(nameCol) ?: "" else ""
                        val rawPath = if (dataCol >= 0) c.getString(dataCol) ?: "" else ""
                        val mime = if (mimeCol >= 0) c.getString(mimeCol) ?: "" else ""
                        val isPendingFlag = (if (pendingCol >= 0) c.getInt(pendingCol) else 0) == 1
                        val file = if (rawPath.isNotBlank()) File(rawPath) else null

                        val size = maxOf(
                            if (sizeCol >= 0) c.getLong(sizeCol) else 0L,
                            if (file != null && file.exists()) file.length() else 0L
                        )
                        // Allow even brand-new pending downloads (size == 0 while actively downloading) or 512B+
                        if (size < 512L && !isPendingFlag) continue

                        val isVideoPartial = mime.startsWith("video/", ignoreCase = true) ||
                            isFileNamePartialDownload(name.ifBlank { rawPath }, file)
                        if (!isVideoPartial) continue

                        val dateSec = if (dateCol >= 0) c.getLong(dateCol) else 0L
                        val dateMs = if (file != null && file.exists()) {
                            file.lastModified()
                        } else if (dateSec > 0L) {
                            dateSec * 1000L
                        } else {
                            System.currentTimeMillis()
                        }

                        val contentUri = ContentUris.withAppendedId(baseUri, id)
                        val playablePath = if (file != null && file.exists() && file.canRead()) {
                            file.absolutePath
                        } else {
                            contentUri.toString()
                        }
                        if (!seenIds.add(playablePath)) continue

                        val isWaBiz = rawPath.contains("WhatsApp Business", ignoreCase = true) || rawPath.contains("w4b", ignoreCase = true)
                        val isWa = !isWaBiz && rawPath.contains("WhatsApp", ignoreCase = true)
                        val folderLabel = when {
                            isWaBiz -> "WhatsApp Business"
                            isWa -> "WhatsApp"
                            else -> file?.parentFile?.name?.takeIf { it.isNotBlank() } ?: "Download"
                        }

                        list.add(
                            MediaItem(
                                id = "pending_file_${baseUri.hashCode()}_$id",
                                title = cleanPartialFileName(name.ifBlank { file?.name ?: "Downloading Video" }),
                                artist = "Pending Download",
                                duration = estimatePartialDurationMs(size.coerceAtLeast(1024L)),
                                size = size.coerceAtLeast(512L),
                                path = playablePath,
                                isVideo = true,
                                folderName = folderLabel,
                                albumName = folderLabel,
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
                targetDirs.add(File(externalRoot, "Downloads"))
                targetDirs.add(File(externalRoot, "Download/Chrome"))
                targetDirs.add(File(externalRoot, "Download/1DM"))
                targetDirs.add(File(externalRoot, "Download/ADM"))
                targetDirs.add(File(externalRoot, "Download/Telegram"))
                targetDirs.add(File(externalRoot, "1DM"))
                targetDirs.add(File(externalRoot, "ADM"))
                targetDirs.add(File(externalRoot, "UCDownloads"))
                targetDirs.add(File(externalRoot, "Opera"))
                targetDirs.add(File(externalRoot, "Browser"))
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
                targetDirs.add(File(externalRoot, "WhatsApp Business/Media/WhatsApp Business Video"))
                targetDirs.add(File(externalRoot, "WhatsApp Business/Media/.Statuses"))
                targetDirs.add(File(externalRoot, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video"))
                targetDirs.add(File(externalRoot, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio"))
                targetDirs.add(File(externalRoot, "Android/media/com.whatsapp/WhatsApp/Media/.Statuses"))
                targetDirs.add(File(externalRoot, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Video"))
                targetDirs.add(File(externalRoot, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"))
                targetDirs.add(File(externalRoot, "DualApp/Android/media/com.whatsapp/WhatsApp/Media/.Statuses"))
                targetDirs.add(File(externalRoot, "DualApp/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"))
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
                val isPartial = isVideo && isFileNamePartialDownload(file.name, file)
                if (ext in validExtensions || isPartial) {
                    val pathLower = dir.absolutePath.lowercase(Locale.US)
                    val isStatusDir = dir.name == ".Statuses" || pathLower.contains(".statuses")
                    val isWaBizDir = pathLower.contains("com.whatsapp.w4b") || pathLower.contains("whatsapp business")
                    val isWaDir = !isWaBizDir && (pathLower.contains("com.whatsapp") || pathLower.contains("/whatsapp/"))

                    val folderLabel = when {
                        isStatusDir && isWaBizDir -> "WhatsApp Business"
                        isStatusDir -> "WhatsApp"
                        isWaBizDir -> "WhatsApp Business"
                        isWaDir -> "WhatsApp"
                        else -> dir.name.ifBlank { "Storage" }
                    }
                    val artistLabel = when {
                        isPartial -> "Pending Download"
                        isStatusDir && isWaBizDir -> "WhatsApp Business Status"
                        isStatusDir -> "WhatsApp Status"
                        else -> folderLabel
                    }
                    val idPrefix = when {
                        isStatusDir && isWaBizDir -> "wa_biz_status"
                        isStatusDir -> "wa_status"
                        else -> "file"
                    }
                    // Estimate duration lightweightly or set to <=100s for Status clips
                    val estimatedDuration = when {
                        isPartial -> estimatePartialDurationMs(file.length())
                        isStatusDir -> 30_000L
                        file.length() < 8_000_000L -> 45_000L
                        else -> 120_000L
                    }
                    out.add(
                        MediaItem(
                            id = "${idPrefix}_${file.absolutePath.hashCode()}",
                            title = if (isPartial) cleanPartialFileName(file.name) else file.nameWithoutExtension,
                            artist = artistLabel,
                            duration = estimatedDuration,
                            size = file.length(),
                            path = file.absolutePath,
                            isVideo = isVideo,
                            folderName = folderLabel,
                            albumName = artistLabel,
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
