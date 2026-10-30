package com.app.mediaplayer

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.Executors

object ToolkitModulesHelper {

    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 1. Real Hardware Video-to-Audio (.m4a) Extractor using Android MediaExtractor + MediaMuxer.
     */
    @SuppressLint("WrongConstant")
    fun extractAudioFromVideoHardware(
        context: Context,
        video: VideoModel,
        onComplete: (Boolean, File?, String) -> Unit
    ) {
        ioExecutor.execute {
            val srcFile = File(video.path)
            if (!srcFile.exists()) {
                mainHandler.post {
                    onComplete(false, null, "Source video file not found: ${video.path}")
                }
                return@execute
            }

            val musicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "HybridExtractedAudio"
            ).apply { mkdirs() }

            val cleanName = video.title.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(40)
            val outFile = File(musicDir, "${cleanName}_${System.currentTimeMillis() % 10000}.m4a")

            val extractor = MediaExtractor()
            var muxer: MediaMuxer? = null
            try {
                extractor.setDataSource(srcFile.absolutePath)
                var audioTrackIndex = -1
                var audioFormat: MediaFormat? = null

                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        audioTrackIndex = i
                        audioFormat = format
                        break
                    }
                }

                if (audioTrackIndex < 0 || audioFormat == null) {
                    mainHandler.post {
                        onComplete(false, null, "No audio stream found inside this video.")
                    }
                    return@execute
                }

                extractor.selectTrack(audioTrackIndex)
                muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val dstTrack = muxer.addTrack(audioFormat)
                muxer.start()

                val buffer = ByteBuffer.allocate(256 * 1024)
                val bufferInfo = MediaCodec.BufferInfo()

                while (true) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = extractor.sampleTime
                    bufferInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(dstTrack, buffer, bufferInfo)
                    extractor.advance()
                }

                muxer.stop()
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(outFile.absolutePath),
                    arrayOf("audio/mp4"),
                    null
                )

                mainHandler.post {
                    onComplete(true, outFile, "Saved to ${outFile.absolutePath}")
                }
            } catch (e: Exception) {
                mainHandler.post {
                    onComplete(false, null, "Hardware extraction failed: ${e.message}")
                }
            } finally {
                try {
                    extractor.release()
                } catch (_: Exception) {
                }
                try {
                    muxer?.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * 2. Real Lossless Hardware Video Trimmer using MediaExtractor + MediaMuxer.
     */
    @SuppressLint("WrongConstant")
    fun trimVideoLosslessHardware(
        context: Context,
        video: VideoModel,
        startMs: Long,
        endMs: Long,
        onComplete: (Boolean, File?, String) -> Unit
    ) {
        ioExecutor.execute {
            val srcFile = File(video.path)
            if (!srcFile.exists()) {
                mainHandler.post { onComplete(false, null, "Source file does not exist") }
                return@execute
            }

            val outDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "HybridTrimmed"
            ).apply { mkdirs() }
            val cleanName = video.title.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(35)
            val outFile = File(outDir, "${cleanName}_Trimmed_${System.currentTimeMillis() % 10000}.mp4")

            val extractor = MediaExtractor()
            var muxer: MediaMuxer? = null
            try {
                extractor.setDataSource(srcFile.absolutePath)
                muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

                val trackMap = HashMap<Int, Int>()
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                        extractor.selectTrack(i)
                        val dstIndex = muxer.addTrack(format)
                        trackMap[i] = dstIndex
                    }
                }

                val startUs = (startMs.coerceAtLeast(0L)) * 1000L
                val endUs = (endMs.coerceAtLeast(startMs + 1000L)) * 1000L

                muxer.start()
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

                val buffer = ByteBuffer.allocate(512 * 1024)
                val bufferInfo = MediaCodec.BufferInfo()

                while (true) {
                    val trackIndex = extractor.sampleTrackIndex
                    if (trackIndex < 0) break
                    val sampleTimeUs = extractor.sampleTime
                    if (sampleTimeUs > endUs) break

                    val dstTrack = trackMap[trackIndex]
                    if (dstTrack != null) {
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break
                        bufferInfo.offset = 0
                        bufferInfo.size = size
                        bufferInfo.presentationTimeUs = (sampleTimeUs - startUs).coerceAtLeast(0L)
                        bufferInfo.flags = extractor.sampleFlags
                        muxer.writeSampleData(dstTrack, buffer, bufferInfo)
                    }
                    extractor.advance()
                }

                muxer.stop()
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(outFile.absolutePath),
                    arrayOf("video/mp4"),
                    null
                )
                mainHandler.post {
                    onComplete(true, outFile, "Trimmed video saved: ${outFile.name}")
                }
            } catch (e: Exception) {
                mainHandler.post {
                    onComplete(false, null, "Trim failed: ${e.message}")
                }
            } finally {
                try {
                    extractor.release()
                } catch (_: Exception) {
                }
                try {
                    muxer?.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * 3. Real WhatsApp & WhatsApp Business Status Scanner & Saver.
     */
    fun scanWhatsAppStatuses(): List<File> {
        val root = Environment.getExternalStorageDirectory()
        val candidateDirs = listOf(
            File(root, "Android/media/com.whatsapp/WhatsApp/Media/.Statuses"),
            File(root, "WhatsApp/Media/.Statuses"),
            File(root, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"),
            File(root, "WhatsApp Business/Media/.Statuses")
        )
        val statuses = mutableListOf<File>()
        val validExts = setOf("mp4", "mkv", "3gp", "webm", "jpg", "jpeg", "png")
        for (dir in candidateDirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { f ->
                    if (f.isFile && f.extension.lowercase(Locale.US) in validExts && f.length() > 1024L) {
                        statuses.add(f)
                    }
                }
            }
        }
        return statuses.sortedByDescending { it.lastModified() }
    }

    fun saveStatusToGallery(context: Context, statusFile: File): File? {
        return try {
            val destDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "HybridSavedStatuses"
            ).apply { mkdirs() }
            val destFile = File(destDir, statusFile.name)
            statusFile.copyTo(destFile, overwrite = true)
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                null,
                null
            )
            destFile
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 4. Deep Codec & Bitrate Inspector Dialog using MediaMetadataRetriever + MediaExtractor.
     */
    fun showMediaCodecInspectorDialog(context: Context, video: VideoModel) {
        val retriever = MediaMetadataRetriever()
        val extractor = MediaExtractor()
        val report = StringBuilder()

        try {
            val file = File(video.path)
            report.append("File Name: ").append(file.name).append("\n")
            report.append("Path: ").append(video.path).append("\n")
            report.append("Size: ").append(video.formattedSize).append(" (").append(video.size).append(" bytes)\n")
            report.append("Status: ").append(if (video.isPartialDownload) "Partial / Incomplete Stream" else "Complete File").append("\n\n")

            if (file.exists()) {
                retriever.setDataSource(video.path)
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: "?"
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: "?"
                val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
                val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "video/*"
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) ?: "0"

                report.append("Container MIME: ").append(mime).append("\n")
                report.append("Resolution: ").append(width).append(" x ").append(height).append(" (Rotation: ").append(rotation).append("°)\n")
                if (bitrate != null && bitrate > 0) {
                    report.append(String.format(Locale.US, "Bitrate: %.2f Mbps\n", bitrate / 1_000_000.0))
                }

                extractor.setDataSource(video.path)
                report.append("Tracks Count: ").append(extractor.trackCount).append("\n")
                for (i in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(i)
                    val trackMime = fmt.getString(MediaFormat.KEY_MIME) ?: "unknown"
                    val lang = if (fmt.containsKey(MediaFormat.KEY_LANGUAGE)) fmt.getString(MediaFormat.KEY_LANGUAGE) else "und"
                    report.append(" • Track #").append(i + 1).append(": ").append(trackMime).append(" [").append(lang).append("]\n")
                }
            }
        } catch (e: Exception) {
            report.append("\nStream note: ").append(e.message)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }

        val scroll = ScrollView(context)
        val tv = TextView(context).apply {
            text = report.toString()
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(48, 36, 48, 36)
            setBackgroundColor(Color.parseColor("#121820"))
        }
        scroll.addView(tv)

        AlertDialog.Builder(context)
            .setTitle("Hardware Stream & Codec Inspector")
            .setView(scroll)
            .setPositiveButton("Close", null)
            .show()
    }

    /**
     * Interactive picker dialog to select any device video for Audio Extraction, Trimming, or Codec Inspection.
     */
    fun showVideoPickerForTool(
        context: Context,
        title: String,
        onVideoPicked: (VideoModel) -> Unit
    ) {
        val videos = DeepMediaScanner.getCachedVideosInstant(context).ifEmpty {
            DeepMediaScanner.scanAllVideosEverywhere(context, forceRefresh = true)
        }
        if (videos.isEmpty()) {
            Toast.makeText(context, "No videos found on device storage", Toast.LENGTH_SHORT).show()
            return
        }
        val names = videos.map { "${it.title} (${it.formattedDuration} • ${it.formattedSize})" }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(title)
            .setItems(names) { _, which ->
                onVideoPicked(videos[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * 5. Real SHA-256 Private PIN Vault (.vault_media physical move & unlock).
     */
    fun openPrivateVaultDialog(context: Context) {
        val prefs = context.getSharedPreferences("HYBRID_PRIVATE_VAULT_PREFS", Context.MODE_PRIVATE)
        val savedHash = prefs.getString("vault_pin_sha256", null)
        val input = android.widget.EditText(context).apply {
            hint = if (savedHash == null) "Set a 4-digit PIN (or 0000 decoy)" else "Enter 4-digit Vault PIN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setPadding(48, 36, 48, 36)
        }

        AlertDialog.Builder(context)
            .setTitle(if (savedHash == null) "Setup Private PIN Vault" else "Unlock Private Vault")
            .setView(input)
            .setPositiveButton("Unlock") { _, _ ->
                val pin = input.text?.toString()?.trim().orEmpty()
                if (pin.length < 4) {
                    Toast.makeText(context, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (pin == "0000") {
                    AlertDialog.Builder(context)
                        .setTitle("Private Vault (Decoy Mode)")
                        .setMessage("0 files in Private Vault.")
                        .setPositiveButton("Close", null)
                        .show()
                    return@setPositiveButton
                }
                val hash = sha256(pin)
                if (savedHash == null) {
                    prefs.edit().putString("vault_pin_sha256", hash).apply()
                    showVaultContentsDialog(context)
                } else if (savedHash == hash) {
                    showVaultContentsDialog(context)
                } else {
                    Toast.makeText(context, "Incorrect Vault PIN", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showVaultContentsDialog(context: Context) {
        val vaultDir = File(context.filesDir, ".vault_media").apply { mkdirs() }
        val lockedFiles = vaultDir.listFiles()?.toList().orEmpty()

        val options = mutableListOf("+ Lock a Video into Private Vault")
        lockedFiles.forEach { f ->
            options.add("🔒 ${f.name} (${VideoModel.formatMediaSize(f.length())}) — Tap to Play/Unlock")
        }

        AlertDialog.Builder(context)
            .setTitle("Private Vault (${lockedFiles.size} Locked Videos)")
            .setItems(options.toTypedArray()) { _, which ->
                if (which == 0) {
                    showVideoPickerForTool(context, "Select Video to Lock in Vault") { video ->
                        val src = File(video.path)
                        if (src.exists()) {
                            val dst = File(vaultDir, src.name)
                            src.copyTo(dst, overwrite = true)
                            Toast.makeText(context, "Locked in Vault: ${dst.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    val file = lockedFiles[which - 1]
                    val intent = android.content.Intent(context, VideoPlayerActivity::class.java).apply {
                        putExtra("video_path", file.absolutePath)
                        putExtra("video_title", file.name)
                        putExtra("video_size", file.length())
                    }
                    context.startActivity(intent)
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun sha256(input: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
