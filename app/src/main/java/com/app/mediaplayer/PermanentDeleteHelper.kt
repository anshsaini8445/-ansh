package com.app.mediaplayer

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

object PermanentDeleteHelper {

    private const val PREF_REGISTRY_NAME = "HYBRID_PERMANENT_DELETED_REGISTRY"
    private const val KEY_RECYCLE_BIN_ENTRIES = "recycle_bin_entries_v1"
    private const val KEY_PERMANENT_TOMBSTONES = "deleted_paths_v1"
    private const val RECYCLE_RETENTION_MS = 30L * 24L * 3600L * 1000L
    const val REQUEST_CODE_MEDIASTORE_DELETE = 9421

    private val thumbnailExecutor = Executors.newFixedThreadPool(2)

    data class RecycleBinEntry(
        val path: String,
        val title: String,
        val mediaId: Long,
        val isVideo: Boolean,
        val deletedAtMs: Long
    ) {
        val daysRemaining: Int
            get() {
                val elapsed = (System.currentTimeMillis() - deletedAtMs).coerceAtLeast(0L)
                val remainingMs = (RECYCLE_RETENTION_MS - elapsed).coerceAtLeast(0L)
                return ((remainingMs / (24L * 3600L * 1000L)) + 1L).toInt().coerceIn(1, 30)
            }
    }

    fun isMarkedDeleted(context: Context?, path: String?): Boolean {
        if (context == null || path.isNullOrBlank()) return false
        val normalized = normalizePath(path)
        val prefs = context.getSharedPreferences(PREF_REGISTRY_NAME, Context.MODE_PRIVATE)

        val tombstones = prefs.getStringSet(KEY_PERMANENT_TOMBSTONES, emptySet()) ?: emptySet()
        if (normalized in tombstones) return true

        val entries = loadAndPurgeExpiredEntries(context)
        return entries.any { normalizePath(it.path) == normalized }
    }

    fun getRecycleBinEntries(context: Context): List<RecycleBinEntry> {
        return loadAndPurgeExpiredEntries(context)
    }

    fun moveToRecycleBin(
        context: Context,
        path: String,
        title: String,
        mediaId: Long,
        isVideo: Boolean = true
    ) {
        if (path.isBlank()) return
        val current = loadAndPurgeExpiredEntries(context).toMutableList()
        val normalized = normalizePath(path)
        current.removeAll { normalizePath(it.path) == normalized }
        current.add(
            0,
            RecycleBinEntry(
                path = path,
                title = title.ifBlank { File(path).name },
                mediaId = mediaId,
                isVideo = isVideo,
                deletedAtMs = System.currentTimeMillis()
            )
        )
        saveRecycleBinEntries(context, current)
        DeepMediaScanner.evictPathFromMemoryAndSnapshot(context, path)
    }

    fun moveToRecycleBin(
        context: Context,
        filePath: String,
        itemMediaId: Long,
        itemTitle: String,
        isVideoItem: Boolean = true
    ) {
        moveToRecycleBin(context, filePath, itemTitle, itemMediaId, isVideoItem)
    }

    fun restoreFromRecycleBin(context: Context, path: String): Boolean {
        if (path.isBlank()) return false
        val normalized = normalizePath(path)
        val current = loadAndPurgeExpiredEntries(context).toMutableList()
        val removed = current.removeAll { normalizePath(it.path) == normalized }
        saveRecycleBinEntries(context, current)

        val prefs = context.getSharedPreferences(PREF_REGISTRY_NAME, Context.MODE_PRIVATE)
        val tombstones = (prefs.getStringSet(KEY_PERMANENT_TOMBSTONES, emptySet()) ?: emptySet()).toMutableSet()
        if (tombstones.remove(normalized)) {
            prefs.edit().putStringSet(KEY_PERMANENT_TOMBSTONES, tombstones).apply()
        }
        return removed
    }

    fun deletePermanentlyNow(
        context: Context,
        path: String,
        mediaId: Long = -1L,
        isVideo: Boolean = true
    ): Boolean {
        if (path.isBlank()) return false
        val normalized = normalizePath(path)

        // Remove from Recycle Bin list and record permanent tombstone
        val current = loadAndPurgeExpiredEntries(context).toMutableList()
        current.removeAll { normalizePath(it.path) == normalized }
        saveRecycleBinEntries(context, current)
        recordPermanentTombstone(context, normalized)
        DeepMediaScanner.evictPathFromMemoryAndSnapshot(context, path)

        var deletedFromStorage = false
        val resolver = context.contentResolver
        val baseUri: Uri = if (isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val pendingDeleteUris = ArrayList<Uri>()

        try {
            if (mediaId > 0L && mediaId < 100_000_000L) {
                val itemUri = ContentUris.withAppendedId(baseUri, mediaId)
                try {
                    val rows = resolver.delete(itemUri, null, null)
                    if (rows > 0) deletedFromStorage = true
                } catch (sec: SecurityException) {
                    pendingDeleteUris.add(itemUri)
                }
            }

            if (!deletedFromStorage) {
                val where = "${MediaStore.MediaColumns.DATA} = ?"
                val args = arrayOf(path)
                try {
                    val rows = resolver.delete(baseUri, where, args)
                    if (rows > 0) deletedFromStorage = true
                } catch (sec: SecurityException) {
                    queryMediaUriByPath(context, baseUri, path)?.let { pendingDeleteUris.add(it) }
                }
            }
        } catch (_: Throwable) {
        }

        try {
            val file = File(path)
            if (file.exists() && file.delete()) {
                deletedFromStorage = true
            }
        } catch (_: Throwable) {
        }

        // Android 11+ (API 30+) Scoped Storage createDeleteRequest fallback when SecurityException occurs
        if (!deletedFromStorage && pendingDeleteUris.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val activity = context as? Activity
            if (activity != null) {
                try {
                    val pendingIntent = MediaStore.createDeleteRequest(resolver, pendingDeleteUris)
                    activity.startIntentSenderForResult(
                        pendingIntent.intentSender,
                        REQUEST_CODE_MEDIASTORE_DELETE,
                        null,
                        0,
                        0,
                        0
                    )
                } catch (_: Throwable) {
                }
            }
        }

        try {
            MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
        } catch (_: Throwable) {
        }

        return deletedFromStorage
    }

    fun emptyRecycleBinNow(context: Context): Int {
        val entries = loadAndPurgeExpiredEntries(context)
        var count = 0
        for (entry in entries) {
            deletePermanentlyNow(
                context = context,
                path = entry.path,
                mediaId = entry.mediaId,
                isVideo = entry.isVideo
            )
            count++
        }
        saveRecycleBinEntries(context, emptyList())
        return count
    }

    fun confirmAndDeletePermanently(
        context: Context,
        video: VideoModel,
        onCompleted: () -> Unit
    ) {
        confirmAndDeleteMultipleVideosPermanently(
            context = context,
            videos = listOf(video),
            onCompleted = onCompleted
        )
    }

    fun confirmAndDeleteMultipleVideosPermanently(
        context: Context,
        videos: List<VideoModel>,
        onCompleted: () -> Unit
    ) {
        if (videos.isEmpty()) return

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 22), dp(context, 22), dp(context, 22), dp(context, 18))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#161A1D"))
                cornerRadius = dp(context, 20).toFloat()
                setStroke(dp(context, 1), Color.parseColor("#263238"))
            }
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(context, 42), dp(context, 42))
            setImageDrawable(createTrashVectorDrawable(Color.parseColor("#2ECC71")))
        }
        headerRow.addView(iconView)

        val titleText = TextView(context).apply {
            text = if (videos.size == 1) {
                "Delete Media File?"
            } else {
                "Delete ${videos.size} Media Files?"
            }
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(context, 12), 0, 0, 0)
        }
        headerRow.addView(titleText)
        root.addView(headerRow)

        val subtitleText = TextView(context).apply {
            val totalBytes = videos.sumOf { it.size }
            val namePreview = if (videos.size == 1) {
                videos.first().title
            } else {
                "${videos.first().title} + ${videos.size - 1} more (${VideoModel.formatMediaSize(totalBytes)})"
            }
            text = "$namePreview\n\nChoose whether to keep a 30-day recovery backup in the Recycle Bin or erase permanently from device storage."
            setTextColor(Color.parseColor("#B0BEC5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(context, 14), 0, dp(context, 18))
        }
        root.addView(subtitleText)

        val dialog = AlertDialog.Builder(context)
            .setView(root)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val recycleBtn = Button(context).apply {
            text = "MOVE TO RECYCLE BIN (30 DAYS SAFE)"
            isAllCaps = false
            setTextColor(Color.parseColor("#0A0A10"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2ECC71"))
                cornerRadius = dp(context, 12).toFloat()
            }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 48)
            )
            params.bottomMargin = dp(context, 10)
            layoutParams = params
            setOnClickListener {
                for (v in videos) {
                    moveToRecycleBin(
                        context = context,
                        path = v.path,
                        title = v.title,
                        mediaId = v.id,
                        isVideo = true
                    )
                }
                Toast.makeText(
                    context,
                    "Moved ${videos.size} item(s) to Recycle Bin (30 days safe)",
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
                onCompleted()
            }
        }
        root.addView(recycleBtn)

        val bottomActionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val cancelBtn = Button(context).apply {
            text = "Cancel"
            isAllCaps = false
            setTextColor(Color.parseColor("#B0BEC5"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#20262B"))
                cornerRadius = dp(context, 12).toFloat()
            }
            val params = LinearLayout.LayoutParams(
                0,
                dp(context, 46),
                1f
            )
            params.rightMargin = dp(context, 8)
            layoutParams = params
            setOnClickListener { dialog.dismiss() }
        }
        bottomActionsRow.addView(cancelBtn)

        val permanentBtn = Button(context).apply {
            text = "Delete Permanently"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E53935"))
                cornerRadius = dp(context, 12).toFloat()
            }
            val params = LinearLayout.LayoutParams(
                0,
                dp(context, 46),
                1.2f
            )
            layoutParams = params
            setOnClickListener {
                for (v in videos) {
                    deletePermanentlyNow(
                        context = context,
                        path = v.path,
                        mediaId = v.id,
                        isVideo = true
                    )
                }
                Toast.makeText(
                    context,
                    "Permanently deleted ${videos.size} file(s)",
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
                onCompleted()
            }
        }
        bottomActionsRow.addView(permanentBtn)

        root.addView(bottomActionsRow)
        dialog.show()
    }

    fun confirmAndDeleteAudioPermanently(
        context: Context,
        audio: AudioModel,
        onCompleted: () -> Unit
    ) {
        val fakeVideoWrapper = VideoModel(
            id = audio.id,
            title = audio.title,
            path = audio.path,
            size = audio.size,
            duration = audio.duration
        )
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 22), dp(context, 22), dp(context, 22), dp(context, 18))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#161A1D"))
                cornerRadius = dp(context, 20).toFloat()
                setStroke(dp(context, 1), Color.parseColor("#263238"))
            }
        }

        val titleView = TextView(context).apply {
            text = "Delete Audio Track?"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
        }
        root.addView(titleView)

        val msgView = TextView(context).apply {
            text = "${fakeVideoWrapper.title}\n\nMove to Recycle Bin (30 Days Safe) or delete permanently from storage?"
            setTextColor(Color.parseColor("#B0BEC5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(context, 12), 0, dp(context, 16))
        }
        root.addView(msgView)

        val dialog = AlertDialog.Builder(context).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val recycleBtn = Button(context).apply {
            text = "MOVE TO RECYCLE BIN (30 DAYS SAFE)"
            isAllCaps = false
            setTextColor(Color.parseColor("#0A0A10"))
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2ECC71"))
                cornerRadius = dp(context, 12).toFloat()
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 48))
            lp.bottomMargin = dp(context, 10)
            layoutParams = lp
            setOnClickListener {
                moveToRecycleBin(context, audio.path, audio.title, audio.id, isVideo = false)
                Toast.makeText(context, "Moved to Recycle Bin", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                onCompleted()
            }
        }
        root.addView(recycleBtn)

        val permBtn = Button(context).apply {
            text = "Delete Permanently"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E53935"))
                cornerRadius = dp(context, 12).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 46))
            setOnClickListener {
                deletePermanentlyNow(context, audio.path, audio.id, isVideo = false)
                Toast.makeText(context, "Audio deleted permanently", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                onCompleted()
            }
        }
        root.addView(permBtn)
        dialog.show()
    }

    fun openRecycleBinDialog(context: Context, onChanged: () -> Unit) {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 16))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0B0D0E"))
                cornerRadius = dp(context, 22).toFloat()
                setStroke(dp(context, 1), Color.parseColor("#1F2933"))
            }
        }

        val dialog = AlertDialog.Builder(context)
            .setView(root)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        fun renderBinContent() {
            root.removeAllViews()
            val entries = loadAndPurgeExpiredEntries(context)

            val topBar = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(context, 12))
            }

            val iconView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(context, 34), dp(context, 34))
                setImageDrawable(createTrashVectorDrawable(Color.parseColor("#2ECC71")))
            }
            topBar.addView(iconView)

            val titleCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(dp(context, 10), 0, 0, 0)
            }
            val headerTitle = TextView(context).apply {
                text = "Recycle Bin (${entries.size})"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTypeface(typeface, Typeface.BOLD)
            }
            val headerSub = TextView(context).apply {
                text = "Items are automatically purged after 30 days"
                setTextColor(Color.parseColor("#78909C"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            }
            titleCol.addView(headerTitle)
            titleCol.addView(headerSub)
            topBar.addView(titleCol)

            if (entries.isNotEmpty()) {
                val emptyBinBtn = Button(context).apply {
                    text = "Empty Bin"
                    isAllCaps = false
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#C62828"))
                        cornerRadius = dp(context, 10).toFloat()
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        dp(context, 36)
                    )
                    setOnClickListener {
                        val removedCount = emptyRecycleBinNow(context)
                        Toast.makeText(
                            context,
                            "Emptied $removedCount item(s) from Recycle Bin",
                            Toast.LENGTH_SHORT
                        ).show()
                        onChanged()
                        renderBinContent()
                    }
                }
                topBar.addView(emptyBinBtn)
            }

            root.addView(topBar)

            if (entries.isEmpty()) {
                val emptyBox = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(context, 20), dp(context, 40), dp(context, 20), dp(context, 40))
                }
                val emptyTitle = TextView(context).apply {
                    text = "Recycle Bin is Empty"
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                }
                val emptyHint = TextView(context).apply {
                    text = "Deleted videos and songs moved to the Recycle Bin stay safe here for 30 days."
                    setTextColor(Color.parseColor("#78909C"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(context, 8), 0, 0)
                }
                emptyBox.addView(emptyTitle)
                emptyBox.addView(emptyHint)
                root.addView(emptyBox)
            } else {
                val scrollView = ScrollView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(context, 360)
                    )
                }
                val listContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                }

                for (entry in entries) {
                    val card = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10))
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#161A1D"))
                            cornerRadius = dp(context, 14).toFloat()
                        }
                        val lp = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        lp.bottomMargin = dp(context, 8)
                        layoutParams = lp
                    }

                    val thumbFrame = FrameLayout(context).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(context, 64), dp(context, 48))
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#141E30"))
                            cornerRadius = dp(context, 8).toFloat()
                        }
                    }

                    val thumbImage = ImageView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                    thumbFrame.addView(thumbImage)

                    val badgeView = TextView(context).apply {
                        text = "${entry.daysRemaining}d left"
                        setTextColor(Color.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                        setPadding(dp(context, 4), dp(context, 1), dp(context, 4), dp(context, 1))
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#B3000000"))
                            cornerRadius = dp(context, 4).toFloat()
                        }
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            Gravity.BOTTOM or Gravity.START
                        )
                    }
                    thumbFrame.addView(badgeView)
                    card.addView(thumbFrame)

                    if (entry.isVideo) {
                        loadVideoThumbnailAsync(entry.path, thumbImage)
                    }

                    val metaCol = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        setPadding(dp(context, 10), 0, dp(context, 8), 0)
                    }

                    val itemTitle = TextView(context).apply {
                        text = entry.title
                        setTextColor(Color.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        setTypeface(typeface, Typeface.BOLD)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    }
                    val itemSub = TextView(context).apply {
                        val sizeBytes = File(entry.path).let { if (it.exists()) it.length() else 0L }
                        text = "${if (entry.isVideo) "Video" else "Audio"} • ${VideoModel.formatMediaSize(sizeBytes)} • ${entry.daysRemaining} days remaining"
                        setTextColor(Color.parseColor("#90A4AE"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    }
                    metaCol.addView(itemTitle)
                    metaCol.addView(itemSub)
                    card.addView(metaCol)

                    val restoreBtn = Button(context).apply {
                        text = "Restore"
                        isAllCaps = false
                        setTextColor(Color.parseColor("#0A0A10"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        setTypeface(typeface, Typeface.BOLD)
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#2ECC71"))
                            cornerRadius = dp(context, 8).toFloat()
                        }
                        val lp = LinearLayout.LayoutParams(dp(context, 68), dp(context, 34))
                        lp.rightMargin = dp(context, 6)
                        layoutParams = lp
                        setPadding(0, 0, 0, 0)
                        setOnClickListener {
                            restoreFromRecycleBin(context, entry.path)
                            Toast.makeText(context, "Restored ${entry.title}", Toast.LENGTH_SHORT).show()
                            onChanged()
                            renderBinContent()
                        }
                    }
                    card.addView(restoreBtn)

                    val deleteBtn = Button(context).apply {
                        text = "Delete"
                        isAllCaps = false
                        setTextColor(Color.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#E53935"))
                            cornerRadius = dp(context, 8).toFloat()
                        }
                        layoutParams = LinearLayout.LayoutParams(dp(context, 62), dp(context, 34))
                        setPadding(0, 0, 0, 0)
                        setOnClickListener {
                            deletePermanentlyNow(
                                context = context,
                                path = entry.path,
                                mediaId = entry.mediaId,
                                isVideo = entry.isVideo
                            )
                            Toast.makeText(context, "Deleted permanently", Toast.LENGTH_SHORT).show()
                            onChanged()
                            renderBinContent()
                        }
                    }
                    card.addView(deleteBtn)

                    listContainer.addView(card)
                }

                scrollView.addView(listContainer)
                root.addView(scrollView)
            }

            val closeBtn = Button(context).apply {
                text = "Close"
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1E272C"))
                    cornerRadius = dp(context, 12).toFloat()
                }
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(context, 44)
                )
                lp.topMargin = dp(context, 12)
                layoutParams = lp
                setOnClickListener { dialog.dismiss() }
            }
            root.addView(closeBtn)
        }

        renderBinContent()
        dialog.show()
    }

    private fun loadVideoThumbnailAsync(path: String, targetView: ImageView) {
        targetView.tag = path
        thumbnailExecutor.execute {
            var bmp: Bitmap? = null
            try {
                val file = File(path)
                if (file.exists()) {
                    @Suppress("DEPRECATION")
                    bmp = ThumbnailUtils.createVideoThumbnail(
                        path,
                        MediaStore.Images.Thumbnails.MINI_KIND
                    )
                    if (bmp == null) {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(path)
                            bmp = retriever.getFrameAtTime(1_000_000L)
                        } finally {
                            retriever.release()
                        }
                    }
                }
            } catch (_: Throwable) {
            }
            if (bmp != null) {
                targetView.post {
                    if (targetView.tag == path) {
                        targetView.setImageBitmap(bmp)
                    }
                }
            }
        }
    }

    private fun loadAndPurgeExpiredEntries(context: Context): List<RecycleBinEntry> {
        val prefs = context.getSharedPreferences(PREF_REGISTRY_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_RECYCLE_BIN_ENTRIES, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()

        val now = System.currentTimeMillis()
        val valid = ArrayList<RecycleBinEntry>()
        var modified = false

        val lines = raw.split("\n")
        for (line in lines) {
            if (line.isBlank()) continue
            val parts = line.split("||", limit = 5)
            if (parts.size < 5) continue
            val path = parts[0]
            val title = parts[1]
            val mediaId = parts[2].toLongOrNull() ?: -1L
            val isVideo = parts[3].toBooleanStrictOrNull() ?: true
            val deletedAtMs = parts[4].toLongOrNull() ?: now

            if (now - deletedAtMs > RECYCLE_RETENTION_MS) {
                modified = true
                try {
                    File(path).delete()
                } catch (_: Throwable) {
                }
                recordPermanentTombstone(context, normalizePath(path))
            } else {
                valid.add(
                    RecycleBinEntry(
                        path = path,
                        title = title,
                        mediaId = mediaId,
                        isVideo = isVideo,
                        deletedAtMs = deletedAtMs
                    )
                )
            }
        }

        if (modified) {
            saveRecycleBinEntries(context, valid)
        }
        return valid
    }

    private fun saveRecycleBinEntries(context: Context, entries: List<RecycleBinEntry>) {
        val sb = StringBuilder()
        for (entry in entries) {
            val cleanTitle = entry.title.replace("||", " ").replace("\n", " ")
            val cleanPath = entry.path.replace("\n", "")
            sb.append(cleanPath).append("||")
                .append(cleanTitle).append("||")
                .append(entry.mediaId).append("||")
                .append(entry.isVideo).append("||")
                .append(entry.deletedAtMs).append('\n')
        }
        context.getSharedPreferences(PREF_REGISTRY_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECYCLE_BIN_ENTRIES, sb.toString())
            .apply()
    }

    private fun recordPermanentTombstone(context: Context, normalizedPath: String) {
        if (normalizedPath.isBlank()) return
        val prefs = context.getSharedPreferences(PREF_REGISTRY_NAME, Context.MODE_PRIVATE)
        val existing = (prefs.getStringSet(KEY_PERMANENT_TOMBSTONES, emptySet()) ?: emptySet()).toMutableSet()
        existing.add(normalizedPath)
        prefs.edit().putStringSet(KEY_PERMANENT_TOMBSTONES, existing).apply()
    }

    private fun queryMediaUriByPath(context: Context, baseUri: Uri, path: String): Uri? {
        return try {
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val sel = "${MediaStore.MediaColumns.DATA} = ?"
            context.contentResolver.query(baseUri, projection, sel, arrayOf(path), null)?.use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(0)
                    ContentUris.withAppendedId(baseUri, id)
                } else {
                    null
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun normalizePath(path: String): String {
        return try {
            File(path).canonicalPath.lowercase(Locale.US)
        } catch (_: Throwable) {
            path.trim().lowercase(Locale.US)
        }
    }

    fun formatFileSize(bytes: Long): String {
        val safe = bytes.coerceAtLeast(0L)
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            safe >= gb -> String.format(Locale.US, "%.2f GB", safe / gb)
            safe >= mb -> String.format(Locale.US, "%.1f MB", safe / mb)
            safe >= kb -> String.format(Locale.US, "%.0f KB", safe / kb)
            else -> "$safe B"
        }
    }

    fun TrashVectorDrawable(accentColor: Int = Color.parseColor("#FF5252")): Drawable {
        return createTrashVectorDrawable(accentColor)
    }

    fun createTrashVectorDrawable(accentColor: Int): Drawable {
        return object : Drawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentColor
                style = Paint.Style.STROKE
                strokeWidth = 5f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            override fun draw(canvas: Canvas) {
                val b = bounds
                val w = b.width().toFloat()
                val h = b.height().toFloat()
                if (w <= 0f || h <= 0f) return

                val bodyRect = RectF(w * 0.24f, h * 0.28f, w * 0.76f, h * 0.84f)
                canvas.drawRoundRect(bodyRect, w * 0.08f, w * 0.08f, paint)
                canvas.drawLine(w * 0.16f, h * 0.22f, w * 0.84f, h * 0.22f, paint)

                val handlePath = Path().apply {
                    moveTo(w * 0.38f, h * 0.22f)
                    lineTo(w * 0.42f, h * 0.13f)
                    lineTo(w * 0.58f, h * 0.13f)
                    lineTo(w * 0.62f, h * 0.22f)
                }
                canvas.drawPath(handlePath, paint)
                canvas.drawLine(w * 0.42f, h * 0.40f, w * 0.42f, h * 0.72f, paint)
                canvas.drawLine(w * 0.58f, h * 0.40f, w * 0.58f, h * 0.72f, paint)
            }

            override fun setAlpha(alpha: Int) {
                paint.alpha = alpha
            }

            override fun setColorFilter(colorFilter: ColorFilter?) {
                paint.colorFilter = colorFilter
            }

            @Deprecated("Deprecated in Java")
            override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        }
    }

    private fun dp(context: Context, value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
