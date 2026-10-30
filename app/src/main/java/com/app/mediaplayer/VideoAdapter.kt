package com.app.mediaplayer

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
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
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.TextUtils
import android.util.LruCache
import android.util.Size
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class VideoAdapter(
    private val context: Context,
    private var videos: MutableList<VideoModel>,
    private var isGridMode: Boolean = false,
    private val onVideoClick: (VideoModel) -> Unit,
    private val onPlayFloatingPip: ((VideoModel) -> Unit)? = null,
    private val onPlayBackgroundAudio: ((VideoModel) -> Unit)? = null,
    private val onVideoListMutated: (() -> Unit)? = null,
    private val onSelectionCountChanged: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    companion object {
        private val maxMemKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
        private val cacheSizeKb = (maxMemKb / 8).coerceAtLeast(4096)

        val thumbnailCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(cacheSizeKb) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return (value.byteCount / 1024).coerceAtLeast(1)
            }
        }

        private val thumbPool = Executors.newFixedThreadPool(4)
        private val mainHandler = Handler(Looper.getMainLooper())

        fun loadThumbnailAsync(path: String, targetImageView: ImageView) {
            targetImageView.tag = path
            val cached = thumbnailCache.get(path)
            if (cached != null && !cached.isRecycled) {
                targetImageView.setImageBitmap(cached)
                return
            }

            targetImageView.setImageDrawable(CinemaClapperPlaceholderDrawable())

            thumbPool.execute {
                val bmp = extractBestVideoFrame(path)
                if (bmp != null) {
                    thumbnailCache.put(path, bmp)
                    mainHandler.post {
                        if (targetImageView.tag == path) {
                            targetImageView.setImageBitmap(bmp)
                        }
                    }
                }
            }
        }

        private fun extractBestVideoFrame(path: String): Bitmap? {
            val file = File(path)
            if (!file.exists() || file.length() <= 1024L) return null

            try {
                val thumb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ThumbnailUtils.createVideoThumbnail(file, Size(320, 180), null)
                } else {
                    @Suppress("DEPRECATION")
                    ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND)
                }
                if (thumb != null) return thumb
            } catch (_: Exception) {
            }

            var retriever: MediaMetadataRetriever? = null
            return try {
                retriever = MediaMetadataRetriever()
                retriever.setDataSource(path)
                retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(0L)
            } catch (_: Exception) {
                null
            } finally {
                try {
                    retriever?.release()
                } catch (_: Exception) {
                }
            }
        }

        fun formatDuration(durationMs: Long): String {
            if (durationMs <= 0L) return "LIVE/PART"
            val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L
            return if (hours > 0L) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        }

        fun resolveQualityBadgeText(path: String, sizeBytes: Long): String {
            val lower = path.lowercase(Locale.US)
            if (lower.endsWith(".part") ||
                lower.endsWith(".crdownload") ||
                lower.endsWith(".download") ||
                lower.endsWith(".tmp") ||
                lower.contains("[partial]")
            ) {
                return "PARTIAL"
            }
            if (lower.contains("8k") || lower.contains("4320p") || sizeBytes >= 1800L * 1024L * 1024L) {
                return "8K"
            }
            if (lower.contains("4k") || lower.contains("2160p") || sizeBytes >= 650L * 1024L * 1024L) {
                return "4K"
            }
            if (lower.contains("1080p") || lower.contains("fhd") || sizeBytes >= 140L * 1024L * 1024L) {
                return "FHD"
            }
            return "HD"
        }
    }

    var isSelectionMode: Boolean = false
        private set

    private val selectedPaths = LinkedHashSet<String>()

    fun updateData(newVideos: List<VideoModel>) {
        videos.clear()
        videos.addAll(newVideos)
        val validPaths = newVideos.map { it.path }.toHashSet()
        selectedPaths.retainAll(validPaths)
        notifyDataSetChanged()
        onSelectionCountChanged?.invoke(selectedPaths.size)
    }

    fun setGridViewMode(grid: Boolean) {
        if (isGridMode != grid) {
            isGridMode = grid
            notifyDataSetChanged()
        }
    }

    fun isGridViewMode(): Boolean = isGridMode

    fun enableSelectionMode(enable: Boolean) {
        isSelectionMode = enable
        if (!enable) {
            selectedPaths.clear()
        }
        notifyDataSetChanged()
        onSelectionCountChanged?.invoke(selectedPaths.size)
    }

    fun selectAll() {
        isSelectionMode = true
        for (v in videos) {
            selectedPaths.add(v.path)
        }
        notifyDataSetChanged()
        onSelectionCountChanged?.invoke(selectedPaths.size)
    }

    fun getSelectedVideos(): List<VideoModel> {
        return videos.filter { selectedPaths.contains(it.path) }
    }

    override fun getItemViewType(position: Int): Int {
        return if (isGridMode) 1 else 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val ctx = parent.context
        val isGrid = (viewType == 1)

        val cardContainer = LinearLayout(ctx).apply {
            orientation = if (isGrid) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = if (isGrid) Gravity.TOP else Gravity.CENTER_VERTICAL
            val pad = dp(ctx, 10)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#161A1D"))
                cornerRadius = dp(ctx, 14).toFloat()
                setStroke(dp(ctx, 1), Color.parseColor("#232B33"))
            }
            val lp = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            val margin = dp(ctx, 5)
            lp.setMargins(margin, margin, margin, margin)
            layoutParams = lp
        }

        val checkBox = CheckBox(ctx).apply {
            visibility = View.GONE
        }

        val thumbWidth = if (isGrid) ViewGroup.LayoutParams.MATCH_PARENT else dp(ctx, 132)
        val thumbHeight = if (isGrid) dp(ctx, 112) else dp(ctx, 76)

        val thumbFrame = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(thumbWidth, thumbHeight)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F1317"))
                cornerRadius = dp(ctx, 10).toFloat()
            }
            clipToOutline = true
        }

        val thumbImage = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageDrawable(CinemaClapperPlaceholderDrawable())
        }
        thumbFrame.addView(
            thumbImage,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        val qualityBadge = TextView(ctx).apply {
            setTextColor(Color.parseColor("#0B0D0E"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(ctx, 5), dp(ctx, 1), dp(ctx, 5), dp(ctx, 1))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FFD54F"))
                cornerRadius = dp(ctx, 4).toFloat()
            }
        }
        val qLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.START
        ).apply {
            leftMargin = dp(ctx, 6)
            bottomMargin = dp(ctx, 6)
        }
        thumbFrame.addView(qualityBadge, qLp)

        val durationBadge = TextView(ctx).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(ctx, 6), dp(ctx, 2), dp(ctx, 6), dp(ctx, 2))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#BF000000"))
                cornerRadius = dp(ctx, 5).toFloat()
            }
        }
        val dLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.END
        ).apply {
            rightMargin = dp(ctx, 6)
            bottomMargin = dp(ctx, 6)
        }
        thumbFrame.addView(durationBadge, dLp)

        val textColumn = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            if (isGrid) {
                setPadding(dp(ctx, 2), dp(ctx, 8), dp(ctx, 2), 0)
            } else {
                setPadding(dp(ctx, 12), 0, dp(ctx, 6), 0)
            }
        }

        val titleTv = TextView(ctx).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }

        val subtitleTv = TextView(ctx).apply {
            setTextColor(Color.parseColor("#9AA5B1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
            setPadding(0, dp(ctx, 4), 0, 0)
        }

        val folderTagTv = TextView(ctx).apply {
            setTextColor(Color.parseColor("#2ECC71"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(ctx, 2), 0, 0)
        }

        textColumn.addView(titleTv)
        textColumn.addView(subtitleTv)
        textColumn.addView(folderTagTv)

        val moreBtn = ImageView(ctx).apply {
            setImageDrawable(ThreeDotsVectorDrawable())
            setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8))
        }

        if (isGrid) {
            cardContainer.addView(thumbFrame)
            val bottomRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            bottomRow.addView(checkBox)
            bottomRow.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            bottomRow.addView(moreBtn, LinearLayout.LayoutParams(dp(ctx, 34), dp(ctx, 34)))
            cardContainer.addView(bottomRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        } else {
            cardContainer.addView(checkBox)
            cardContainer.addView(thumbFrame)
            cardContainer.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            cardContainer.addView(moreBtn, LinearLayout.LayoutParams(dp(ctx, 36), dp(ctx, 36)))
        }

        return VideoViewHolder(
            itemView = cardContainer,
            checkBox = checkBox,
            thumbImage = thumbImage,
            qualityBadge = qualityBadge,
            durationBadge = durationBadge,
            titleTv = titleTv,
            subtitleTv = subtitleTv,
            folderTagTv = folderTagTv,
            moreBtn = moreBtn
        )
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = videos[position]
        val cleanTitle = video.title.removePrefix("[PARTIAL] ")
        holder.titleTv.text = cleanTitle

        val sizeText = PermanentDeleteHelper.formatFileSize(video.size)
        val fileObj = File(video.path)
        val dateStr = if (fileObj.exists() && fileObj.lastModified() > 0L) {
            SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(fileObj.lastModified()))
        } else {
            "Storage"
        }
        holder.subtitleTv.text = "$sizeText • $dateStr"

        val parentFolderName = fileObj.parentFile?.name ?: "Internal"
        holder.folderTagTv.text = "Folder: $parentFolderName"

        val badgeLabel = resolveQualityBadgeText(video.path, video.size)
        holder.qualityBadge.text = badgeLabel
        val badgeColor = when (badgeLabel) {
            "PARTIAL" -> Color.parseColor("#FF5252")
            "8K", "4K" -> Color.parseColor("#FFD54F")
            else -> Color.parseColor("#2ECC71")
        }
        holder.qualityBadge.background = GradientDrawable().apply {
            setColor(badgeColor)
            cornerRadius = dp(context, 4).toFloat()
        }
        holder.qualityBadge.setTextColor(
            if (badgeLabel == "PARTIAL") Color.WHITE else Color.parseColor("#0B0D0E")
        )

        holder.durationBadge.text = formatDuration(video.duration)

        loadThumbnailAsync(video.path, holder.thumbImage)

        holder.checkBox.setOnCheckedChangeListener(null)
        if (isSelectionMode) {
            holder.checkBox.visibility = View.VISIBLE
            holder.checkBox.isChecked = selectedPaths.contains(video.path)
        } else {
            holder.checkBox.visibility = View.GONE
            holder.checkBox.isChecked = false
        }

        holder.checkBox.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                selectedPaths.add(video.path)
            } else {
                selectedPaths.remove(video.path)
            }
            onSelectionCountChanged?.invoke(selectedPaths.size)
        }

        holder.itemView.setOnClickListener {
            if (isSelectionMode) {
                val nowSelected = !selectedPaths.contains(video.path)
                if (nowSelected) selectedPaths.add(video.path) else selectedPaths.remove(video.path)
                holder.checkBox.isChecked = nowSelected
                onSelectionCountChanged?.invoke(selectedPaths.size)
            } else {
                onVideoClick(video)
            }
        }

        holder.itemView.setOnLongClickListener {
            if (!isSelectionMode) {
                isSelectionMode = true
                selectedPaths.add(video.path)
                notifyDataSetChanged()
                onSelectionCountChanged?.invoke(selectedPaths.size)
            }
            true
        }

        holder.moreBtn.setOnClickListener { anchor ->
            showVideoItemMenu(anchor, video, holder.bindingAdapterPosition)
        }
    }

    override fun getItemCount(): Int = videos.size

    private fun showVideoItemMenu(anchor: View, video: VideoModel, position: Int) {
        val popup = PopupMenu(context, anchor)
        popup.menu.add(0, 1, 0, "Play Video")
        popup.menu.add(0, 2, 1, "Play in Floating PiP Window")
        popup.menu.add(0, 3, 2, "Play as Background Audio")
        popup.menu.add(0, 4, 3, "Rename Video")
        popup.menu.add(0, 5, 4, "Move to 30-Day Recycle Bin")
        popup.menu.add(0, 6, 5, "Delete Permanently")
        popup.menu.add(0, 7, 6, "File Properties")
        popup.menu.add(0, 8, 7, "Share Video")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> onVideoClick(video)
                2 -> {
                    if (onPlayFloatingPip != null) {
                        onPlayFloatingPip.invoke(video)
                    } else {
                        onVideoClick(video)
                    }
                }
                3 -> {
                    if (onPlayBackgroundAudio != null) {
                        onPlayBackgroundAudio.invoke(video)
                    } else {
                        onVideoClick(video)
                    }
                }
                4 -> showRenameVideoDialog(video, position)
                5 -> {
                    PermanentDeleteHelper.moveToRecycleBin(
                        context = context,
                        path = video.path,
                        mediaId = video.id,
                        title = video.title,
                        isVideo = true
                    )
                    if (position in videos.indices) {
                        videos.removeAt(position)
                        notifyItemRemoved(position)
                    } else {
                        notifyDataSetChanged()
                    }
                    Toast.makeText(context, "Moved to 30-Day Recycle Bin", Toast.LENGTH_SHORT).show()
                    onVideoListMutated?.invoke()
                }
                6 -> {
                    PermanentDeleteHelper.confirmAndDeletePermanently(
                        context = context,
                        video = video
                    ) {
                        if (position in videos.indices && videos[position].path == video.path) {
                            videos.removeAt(position)
                            notifyItemRemoved(position)
                        } else {
                            videos.removeAll { it.path == video.path }
                            notifyDataSetChanged()
                        }
                        onVideoListMutated?.invoke()
                    }
                }
                7 -> showVideoPropertiesDialog(video)
                8 -> shareVideoFile(video)
            }
            true
        }
        popup.show()
    }

    private fun showRenameVideoDialog(video: VideoModel, position: Int) {
        val currentFile = File(video.path)
        val ext = currentFile.extension
        val baseName = if (ext.isNotEmpty()) currentFile.nameWithoutExtension else currentFile.name

        val input = EditText(context).apply {
            setText(baseName)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#7B8794"))
            setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14))
        }

        AlertDialog.Builder(context)
            .setTitle("Rename Video")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Rename") { _, _ ->
                val newBase = input.text?.toString()?.trim().orEmpty()
                if (newBase.isEmpty()) return@setPositiveButton
                val newFileName = if (ext.isNotEmpty()) "$newBase.$ext" else newBase
                val destFile = File(currentFile.parentFile, newFileName)
                if (currentFile.exists() && currentFile.renameTo(destFile)) {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(currentFile.absolutePath, destFile.absolutePath),
                        null,
                        null
                    )
                    val updated = VideoModel(
                        id = video.id,
                        title = newBase,
                        path = destFile.absolutePath,
                        size = destFile.length(),
                        duration = video.duration
                    )
                    if (position in videos.indices) {
                        videos[position] = updated
                        notifyItemChanged(position)
                    }
                    Toast.makeText(context, "Renamed to $newFileName", Toast.LENGTH_SHORT).show()
                    onVideoListMutated?.invoke()
                } else {
                    Toast.makeText(context, "Rename failed (storage permission required)", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showVideoPropertiesDialog(video: VideoModel) {
        val file = File(video.path)
        var resolution = "Unknown"
        var bitrate = "Unknown"
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(video.path)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val br = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
            if (!w.isNullOrBlank() && !h.isNullOrBlank()) {
                resolution = "${w}x${h}"
            }
            if (br != null && br > 0L) {
                bitrate = "${br / 1000L} kbps"
            }
        } catch (_: Exception) {
        } finally {
            try {
                retriever?.release()
            } catch (_: Exception) {
            }
        }

        val modifiedStr = if (file.exists() && file.lastModified() > 0L) {
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(file.lastModified()))
        } else {
            "Unknown"
        }

        val details = buildString {
            append("Title: ").append(video.title).append("\n\n")
            append("File Path: ").append(video.path).append("\n\n")
            append("Size: ").append(PermanentDeleteHelper.formatFileSize(video.size))
                .append(" (").append(video.size).append(" bytes)\n\n")
            append("Duration: ").append(formatDuration(video.duration)).append("\n\n")
            append("Resolution: ").append(resolution).append("\n\n")
            append("Bitrate: ").append(bitrate).append("\n\n")
            append("Last Modified: ").append(modifiedStr)
        }

        AlertDialog.Builder(context)
            .setTitle("Video Properties")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun shareVideoFile(video: VideoModel) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_STREAM, Uri.parse("file://${video.path}"))
                putExtra(Intent.EXTRA_TITLE, video.title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to share this file", Toast.LENGTH_SHORT).show()
        }
    }

    class VideoViewHolder(
        itemView: View,
        val checkBox: CheckBox,
        val thumbImage: ImageView,
        val qualityBadge: TextView,
        val durationBadge: TextView,
        val titleTv: TextView,
        val subtitleTv: TextView,
        val folderTagTv: TextView,
        val moreBtn: ImageView
    ) : RecyclerView.ViewHolder(itemView)

    class CinemaClapperPlaceholderDrawable : Drawable() {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#11161C")
            style = Paint.Style.FILL
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2ECC71")
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val playPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2ECC71")
            style = Paint.Style.FILL
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return

            canvas.drawRect(b, bgPaint)
            val cx = w * 0.5f
            val cy = h * 0.5f
            val boxW = (w * 0.34f).coerceAtMost(h * 0.52f)
            val boxH = boxW * 0.78f
            val rect = RectF(cx - boxW / 2f, cy - boxH / 2f, cx + boxW / 2f, cy + boxH / 2f)
            canvas.drawRoundRect(rect, 8f, 8f, strokePaint)

            val tri = Path().apply {
                moveTo(cx - boxW * 0.12f, cy - boxH * 0.22f)
                lineTo(cx + boxW * 0.22f, cy)
                lineTo(cx - boxW * 0.12f, cy + boxH * 0.22f)
                close()
            }
            canvas.drawPath(tri, playPaint)
        }

        override fun setAlpha(alpha: Int) {
            bgPaint.alpha = alpha
            strokePaint.alpha = alpha
            playPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            strokePaint.colorFilter = colorFilter
            playPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.OPAQUE
    }

    class ThreeDotsVectorDrawable(
        tintColor: Int = Color.parseColor("#B0BAC5")
    ) : Drawable() {
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tintColor
            style = Paint.Style.FILL
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val h = b.height().toFloat()
            val r = (b.width().coerceAtMost(b.height()) * 0.08f).coerceAtLeast(2.4f)
            canvas.drawCircle(cx, h * 0.26f, r, dotPaint)
            canvas.drawCircle(cx, h * 0.50f, r, dotPaint)
            canvas.drawCircle(cx, h * 0.74f, r, dotPaint)
        }

        override fun setAlpha(alpha: Int) {
            dotPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            dotPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private fun dp(context: Context, value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
