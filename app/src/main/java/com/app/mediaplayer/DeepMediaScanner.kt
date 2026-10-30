package com.app.mediaplayer

import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object DeepMediaScanner {

    private const val PREF_SNAPSHOT_NAME = "HYBRID_INSTANT_VIDEO_SNAPSHOT"
    private const val KEY_INSTANT_VIDEOS_V4 = "instant_video_rows_v4"
    private const val KEY_INSTANT_AUDIOS_V1 = "instant_audio_rows_v1"
    private const val MIN_VALID_FILE_BYTES = 1024L

    @Volatile
    var memoryCache: List<VideoModel> = emptyList()
        private set

    @Volatile
    var audioMemoryCache: List<AudioModel> = emptyList()
        private set

    private val durationCache = ConcurrentHashMap<String, Long>()

    private val SUPPORTED_VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "avi", "mov", "flv", "wmv", "3gp",
        "webm", "ts", "m4v", "mpeg", "mpg", "part", "crdownload", "download", "tmp"
    )

    private val SUPPORTED_AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma", "amr"
    )

    fun getCachedVideosInstant(context: Context?): List<VideoModel> {
        val currentRam = memoryCache
        if (currentRam.isNotEmpty()) {
            return currentRam.filterNot { PermanentDeleteHelper.isMarkedDeleted(context, it.path) }
        }
        if (context == null) return emptyList()

        val prefs = context.getSharedPreferences(PREF_SNAPSHOT_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_INSTANT_VIDEOS_V4, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()

        val parsed = ArrayList<VideoModel>()
        val lines = raw.split("\n")
        for (line in lines) {
            if (line.isBlank()) continue
            val parts = line.split("|", limit = 5)
            if (parts.size < 5) continue
            val id = parts[0].toLongOrNull() ?: continue
            val title = parts[1]
            val duration = parts[2].toLongOrNull() ?: 0L
            val size = parts[3].toLongOrNull() ?: 0L
            val path = parts[4]
            if (path.isBlank()) continue
            if (PermanentDeleteHelper.isMarkedDeleted(context, path)) continue
            val file = File(path)
            if (file.exists() && file.length() > MIN_VALID_FILE_BYTES) {
                parsed.add(
                    VideoModel(
                        id = id,
                        title = title,
                        path = path,
                        size = size,
                        duration = duration
                    )
                )
            }
        }
        if (parsed.isNotEmpty()) {
            memoryCache = parsed
        }
        return parsed
    }

    fun getCachedAudiosInstant(context: Context?): List<AudioModel> {
        val currentRam = audioMemoryCache
        if (currentRam.isNotEmpty()) {
            return currentRam.filterNot { PermanentDeleteHelper.isMarkedDeleted(context, it.path) }
        }
        if (context == null) return emptyList()

        val prefs = context.getSharedPreferences(PREF_SNAPSHOT_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_INSTANT_AUDIOS_V1, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()

        val parsed = ArrayList<AudioModel>()
        val lines = raw.split("\n")
        for (line in lines) {
            if (line.isBlank()) continue
            val parts = line.split("|", limit = 6)
            if (parts.size < 6) continue
            val id = parts[0].toLongOrNull() ?: continue
            val title = parts[1]
            val artist = parts[2]
            val duration = parts[3].toLongOrNull() ?: 0L
            val size = parts[4].toLongOrNull() ?: 0L
            val path = parts[5]
            if (path.isBlank()) continue
            if (PermanentDeleteHelper.isMarkedDeleted(context, path)) continue
            val file = File(path)
            if (file.exists() && file.length() > MIN_VALID_FILE_BYTES) {
                parsed.add(
                    AudioModel(
                        id = id,
                        title = title,
                        path = path,
                        artist = artist,
                        duration = duration,
                        size = size
                    )
                )
            }
        }
        if (parsed.isNotEmpty()) {
            audioMemoryCache = parsed
        }
        return parsed
    }

    fun scanAllVideosEverywhere(context: Context, forceRefresh: Boolean = false): List<VideoModel> {
        if (!forceRefresh && memoryCache.isNotEmpty()) {
            val filtered = memoryCache.filterNot {
                PermanentDeleteHelper.isMarkedDeleted(context, it.path) || !File(it.path).exists()
            }
            if (filtered.isNotEmpty()) {
                memoryCache = filtered
                return filtered
            }
        }

        val discoveredByPath = LinkedHashMap<String, VideoModel>()

        // Step A: Query Android MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            val cursor: Cursor? = context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )
            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val displayCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val durationCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val rawPath = c.getString(dataCol) ?: continue
                    if (rawPath.isBlank()) continue
                    if (PermanentDeleteHelper.isMarkedDeleted(context, rawPath)) continue

                    val file = File(rawPath)
                    if (!file.exists() || file.length() <= MIN_VALID_FILE_BYTES) continue

                    val displayName = c.getString(displayCol)
                    val rawTitle = c.getString(titleCol)
                    val cleanTitle = when {
                        !displayName.isNullOrBlank() -> displayName
                        !rawTitle.isNullOrBlank() -> rawTitle
                        else -> file.name
                    }
                    val duration = c.getLong(durationCol).coerceAtLeast(0L)
                    val size = c.getLong(sizeCol).let { if (it > 0L) it else file.length() }

                    val canonicalKey = normalizePathKey(file)
                    discoveredByPath[canonicalKey] = VideoModel(
                        id = id,
                        title = cleanTitle,
                        path = file.absolutePath,
                        size = size,
                        duration = duration
                    )
                }
            }
        } catch (_: Throwable) {
            // Continues to direct filesystem scan if MediaStore query fails or lacks permission
        }

        // Step B: Direct Directory & Partial/Incomplete File Scanner (.part, .crdownload, unindexed 8K/MKV/TS)
        val candidateDirs = collectCandidateDirectories()
        for (rootDir in candidateDirs) {
            scanDirectoryForVideosRecursive(
                context = context,
                dir = rootDir,
                depth = 0,
                maxDepth = 4,
                outMap = discoveredByPath
            )
        }

        // Step C: Final Recycle Bin & Tombstone Filter
        val finalVideos = discoveredByPath.values
            .filter { video ->
                val f = File(video.path)
                f.exists() &&
                    f.length() > MIN_VALID_FILE_BYTES &&
                    !PermanentDeleteHelper.isMarkedDeleted(context, video.path)
            }
            .sortedByDescending { File(it.path).lastModified() }

        memoryCache = finalVideos
        saveVideoSnapshot(context, finalVideos)
        return finalVideos
    }

    fun scanAllAudioEverywhere(context: Context, forceRefresh: Boolean = false): List<AudioModel> {
        if (!forceRefresh && audioMemoryCache.isNotEmpty()) {
            val filtered = audioMemoryCache.filterNot {
                PermanentDeleteHelper.isMarkedDeleted(context, it.path) || !File(it.path).exists()
            }
            if (filtered.isNotEmpty()) {
                audioMemoryCache = filtered
                return filtered
            }
        }

        val discoveredByPath = LinkedHashMap<String, AudioModel>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE
        )
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        try {
            val cursor: Cursor? = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )
            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val displayCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val rawPath = c.getString(dataCol) ?: continue
                    if (rawPath.isBlank()) continue
                    if (PermanentDeleteHelper.isMarkedDeleted(context, rawPath)) continue

                    val file = File(rawPath)
                    if (!file.exists() || file.length() <= MIN_VALID_FILE_BYTES) continue

                    val title = c.getString(titleCol)?.takeIf { it.isNotBlank() }
                        ?: c.getString(displayCol)?.takeIf { it.isNotBlank() }
                        ?: file.nameWithoutExtension
                    val rawArtist = c.getString(artistCol)
                    val artist = if (rawArtist.isNullOrBlank() || rawArtist == "<unknown>") {
                        "Unknown Artist"
                    } else {
                        rawArtist
                    }
                    val album = c.getString(albumCol)?.takeIf { it.isNotBlank() } ?: "Unknown Album"
                    val duration = c.getLong(durationCol).coerceAtLeast(0L)
                    val size = c.getLong(sizeCol).let { if (it > 0L) it else file.length() }

                    val canonicalKey = normalizePathKey(file)
                    discoveredByPath[canonicalKey] = AudioModel(
                        id = id,
                        title = title,
                        path = file.absolutePath,
                        artist = artist,
                        duration = duration,
                        size = size,
                        album = album
                    )
                }
            }
        } catch (_: Throwable) {
        }

        val candidateDirs = collectCandidateAudioDirectories()
        for (rootDir in candidateDirs) {
            scanDirectoryForAudioRecursive(
                context = context,
                dir = rootDir,
                depth = 0,
                maxDepth = 3,
                outMap = discoveredByPath
            )
        }

        val finalAudios = discoveredByPath.values
            .filter { audio ->
                val f = File(audio.path)
                f.exists() &&
                    f.length() > MIN_VALID_FILE_BYTES &&
                    !PermanentDeleteHelper.isMarkedDeleted(context, audio.path)
            }
            .sortedByDescending { File(it.path).lastModified() }

        audioMemoryCache = finalAudios
        saveAudioSnapshot(context, finalAudios)
        return finalAudios
    }

    fun groupVideosByFolder(videos: List<VideoModel>): List<FolderModel> {
        if (videos.isEmpty()) return emptyList()
        val grouped = LinkedHashMap<String, MutableList<VideoModel>>()
        for (video in videos) {
            val parent = File(video.path).parentFile
            val folderPath = parent?.absolutePath ?: "Internal Storage"
            grouped.getOrPut(folderPath) { ArrayList() }.add(video)
        }
        return grouped.entries.map { (folderPath, list) ->
            val folderName = File(folderPath).name.takeIf { it.isNotBlank() } ?: "Internal Storage"
            val totalBytes = list.sumOf { it.size }
            FolderModel(
                bucketId = folderPath.hashCode().toString(),
                folderName = folderName,
                videoCount = list.size,
                folderPath = folderPath,
                totalSize = totalBytes
            )
        }.sortedByDescending { it.videoCount }
    }

    fun evictPathFromMemoryAndSnapshot(context: Context?, path: String) {
        if (path.isBlank()) return
        val targetKey = normalizePathKey(File(path))
        memoryCache = memoryCache.filterNot { normalizePathKey(File(it.path)) == targetKey }
        audioMemoryCache = audioMemoryCache.filterNot { normalizePathKey(File(it.path)) == targetKey }
        if (context != null) {
            saveVideoSnapshot(context, memoryCache)
            saveAudioSnapshot(context, audioMemoryCache)
        }
    }

    private fun collectCandidateDirectories(): List<File> {
        val dirs = LinkedHashSet<File>()
        try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { dirs.add(it) }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)?.let { dirs.add(it) }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)?.let { dirs.add(it) }
            val extRoot = Environment.getExternalStorageDirectory()
            if (extRoot != null && extRoot.exists()) {
                dirs.add(File(extRoot, "Download"))
                dirs.add(File(extRoot, "Downloads"))
                dirs.add(File(extRoot, "Movies"))
                dirs.add(File(extRoot, "DCIM"))
                dirs.add(File(extRoot, "Video"))
                dirs.add(File(extRoot, "Videos"))
                dirs.add(File(extRoot, "WhatsApp/Media/WhatsApp Video"))
                dirs.add(File(extRoot, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video"))
                dirs.add(File(extRoot, "Telegram/Telegram Video"))
                dirs.add(File(extRoot, "Snaptube/download"))
            }
        } catch (_: Throwable) {
        }
        return dirs.filter { it.exists() && it.isDirectory && it.canRead() }
    }

    private fun collectCandidateAudioDirectories(): List<File> {
        val dirs = LinkedHashSet<File>()
        try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.let { dirs.add(it) }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { dirs.add(it) }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS)?.let { dirs.add(it) }
            val extRoot = Environment.getExternalStorageDirectory()
            if (extRoot != null && extRoot.exists()) {
                dirs.add(File(extRoot, "Music"))
                dirs.add(File(extRoot, "Download"))
                dirs.add(File(extRoot, "Audio"))
                dirs.add(File(extRoot, "WhatsApp/Media/WhatsApp Audio"))
                dirs.add(File(extRoot, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio"))
            }
        } catch (_: Throwable) {
        }
        return dirs.filter { it.exists() && it.isDirectory && it.canRead() }
    }

    private fun scanDirectoryForVideosRecursive(
        context: Context,
        dir: File,
        depth: Int,
        maxDepth: Int,
        outMap: MutableMap<String, VideoModel>
    ) {
        if (depth > maxDepth || !dir.exists() || !dir.canRead()) return
        if (dir.name.startsWith(".")) return

        val children = try {
            dir.listFiles()
        } catch (_: Throwable) {
            null
        } ?: return

        for (child in children) {
            if (child.isDirectory) {
                scanDirectoryForVideosRecursive(context, child, depth + 1, maxDepth, outMap)
            } else if (child.isFile && child.length() > MIN_VALID_FILE_BYTES) {
                if (isCandidateVideoOrPartialFile(child)) {
                    val path = child.absolutePath
                    if (PermanentDeleteHelper.isMarkedDeleted(context, path)) continue
                    val key = normalizePathKey(child)
                    if (!outMap.containsKey(key)) {
                        val syntheticId = (child.absolutePath.hashCode().toLong() and 0x7FFFFFFFL) + 100_000_000L
                        val durationMs = extractDurationFastOrZero(child)
                        outMap[key] = VideoModel(
                            id = syntheticId,
                            title = child.name,
                            path = path,
                            size = child.length(),
                            duration = durationMs
                        )
                    }
                }
            }
        }
    }

    private fun scanDirectoryForAudioRecursive(
        context: Context,
        dir: File,
        depth: Int,
        maxDepth: Int,
        outMap: MutableMap<String, AudioModel>
    ) {
        if (depth > maxDepth || !dir.exists() || !dir.canRead()) return
        if (dir.name.startsWith(".")) return

        val children = try {
            dir.listFiles()
        } catch (_: Throwable) {
            null
        } ?: return

        for (child in children) {
            if (child.isDirectory) {
                scanDirectoryForAudioRecursive(context, child, depth + 1, maxDepth, outMap)
            } else if (child.isFile && child.length() > MIN_VALID_FILE_BYTES) {
                val ext = child.extension.lowercase(Locale.US)
                if (ext in SUPPORTED_AUDIO_EXTENSIONS) {
                    val path = child.absolutePath
                    if (PermanentDeleteHelper.isMarkedDeleted(context, path)) continue
                    val key = normalizePathKey(child)
                    if (!outMap.containsKey(key)) {
                        val syntheticId = (child.absolutePath.hashCode().toLong() and 0x7FFFFFFFL) + 200_000_000L
                        val durationMs = extractDurationFastOrZero(child)
                        outMap[key] = AudioModel(
                            id = syntheticId,
                            title = child.nameWithoutExtension,
                            path = path,
                            artist = "Local Storage",
                            duration = durationMs,
                            size = child.length(),
                            album = child.parentFile?.name ?: "Folder Audio"
                        )
                    }
                }
            }
        }
    }

    private fun isCandidateVideoOrPartialFile(file: File): Boolean {
        val nameLower = file.name.lowercase(Locale.US)
        val ext = file.extension.lowercase(Locale.US)
        if (ext in SUPPORTED_VIDEO_EXTENSIONS) {
            if (ext == "part" || ext == "crdownload" || ext == "download" || ext == "tmp") {
                return SUPPORTED_VIDEO_EXTENSIONS.any { videoExt ->
                    videoExt != ext && nameLower.contains(".$videoExt")
                } || file.length() > 256 * 1024L
            }
            return true
        }
        return false
    }

    private fun extractDurationFastOrZero(file: File): Long {
        val path = file.absolutePath
        durationCache[path]?.let { return it }
        val ext = file.extension.lowercase(Locale.US)
        if (ext == "part" || ext == "crdownload" || ext == "download" || ext == "tmp") {
            durationCache[path] = 0L
            return 0L
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val dur = durStr?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            durationCache[path] = dur
            dur
        } catch (_: Throwable) {
            durationCache[path] = 0L
            0L
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {
            }
        }
    }

    private fun saveVideoSnapshot(context: Context, videos: List<VideoModel>) {
        try {
            val sb = StringBuilder()
            val limit = videos.size.coerceAtMost(450)
            for (i in 0 until limit) {
                val v = videos[i]
                val safeTitle = v.title.replace("|", " ").replace("\n", " ")
                val safePath = v.path.replace("\n", "")
                sb.append(v.id).append('|')
                    .append(safeTitle).append('|')
                    .append(v.duration).append('|')
                    .append(v.size).append('|')
                    .append(safePath).append('\n')
            }
            context.getSharedPreferences(PREF_SNAPSHOT_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_INSTANT_VIDEOS_V4, sb.toString())
                .apply()
        } catch (_: Throwable) {
        }
    }

    private fun saveAudioSnapshot(context: Context, audios: List<AudioModel>) {
        try {
            val sb = StringBuilder()
            val limit = audios.size.coerceAtMost(500)
            for (i in 0 until limit) {
                val a = audios[i]
                val safeTitle = a.title.replace("|", " ").replace("\n", " ")
                val safeArtist = a.artist.replace("|", " ").replace("\n", " ")
                val safePath = a.path.replace("\n", "")
                sb.append(a.id).append('|')
                    .append(safeTitle).append('|')
                    .append(safeArtist).append('|')
                    .append(a.duration).append('|')
                    .append(a.size).append('|')
                    .append(safePath).append('\n')
            }
            context.getSharedPreferences(PREF_SNAPSHOT_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_INSTANT_AUDIOS_V1, sb.toString())
                .apply()
        } catch (_: Throwable) {
        }
    }

    private fun normalizePathKey(file: File): String {
        return try {
            file.canonicalPath.lowercase(Locale.US)
        } catch (_: Throwable) {
            file.absolutePath.lowercase(Locale.US)
        }
    }
}
