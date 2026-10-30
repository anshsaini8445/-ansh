package com.app.mediaplayer

import android.app.AlertDialog
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
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

object HybridVideoSectionHelper {

    private const val PREF_HISTORY = "HYBRID_VIDEO_WATCH_HISTORY"
    private const val KEY_HISTORY_ROWS = "history_rows_v1"
    private const val PREF_UI_STATE = "HYBRID_VIDEO_UI_PREFS"
    private const val KEY_SORT_FIELD = "sort_field"
    private const val KEY_SORT_DESC = "sort_desc"
    private const val KEY_GRID_MODE = "grid_mode"

    const val FILTER_ALL = "ALL"
    const val FILTER_FOLDERS = "FOLDERS"
    const val FILTER_RECENT = "RECENT"
    const val FILTER_LARGE = "LARGE_100MB"
    const val FILTER_RECYCLE_BIN = "RECYCLE_BIN"

    const val SORT_DATE = 0
    const val SORT_NAME = 1
    const val SORT_SIZE = 2
    const val SORT_LENGTH = 3

    private val thumbExecutor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())

    data class WatchHistoryEntry(
        val id: Long,
        val title: String,
        val path: String,
        val durationMs: Long,
        val positionMs: Long,
        val sizeBytes: Long,
        val timestampMs: Long
    )

    data class SortConfig(
        val sortField: Int,
        val descending: Boolean
    )

    fun loadSortConfig(context: Context): SortConfig {
        val prefs = context.getSharedPreferences(PREF_UI_STATE, Context.MODE_PRIVATE)
        return SortConfig(
            sortField = prefs.getInt(KEY_SORT_FIELD, SORT_DATE),
            descending = prefs.getBoolean(KEY_SORT_DESC, true)
        )
    }

    fun saveSortConfig(context: Context, config: SortConfig) {
        context.getSharedPreferences(PREF_UI_STATE, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SORT_FIELD, config.sortField)
            .putBoolean(KEY_SORT_DESC, config.descending)
            .apply()
    }

    fun isGridMode(context: Context): Boolean {
        return context.getSharedPreferences(PREF_UI_STATE, Context.MODE_PRIVATE)
            .getBoolean(KEY_GRID_MODE, false)
    }

    fun setGridMode(context: Context, isGrid: Boolean) {
        context.getSharedPreferences(PREF_UI_STATE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_GRID_MODE, isGrid)
            .apply()
    }

    fun recordWatchHistory(
        context: Context,
        video: VideoModel,
        positionMs: Long,
        durationMs: Long = video.duration
    ) {
        if (video.path.isBlank()) return
        val current = getWatchHistory(context).toMutableList()
        current.removeAll { it.path == video.path }
        current.add(
            0,
            WatchHistoryEntry(
                id = video.id,
                title = video.title,
                path = video.path,
                durationMs = if (durationMs > 0L) durationMs else video.duration,
                positionMs = positionMs.coerceAtLeast(0L),
                sizeBytes = video.size,
                timestampMs = System.currentTimeMillis()
            )
        )
        val trimmed = current.take(30)
        saveWatchHistoryList(context, trimmed)
    }

    fun getWatchHistory(context: Context): List<WatchHistoryEntry> {
        val raw = context.getSharedPreferences(PREF_HISTORY, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY_ROWS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        val result = ArrayList<WatchHistoryEntry>()
        for (line in raw.split("\n")) {
            if (line.isBlank()) continue
            val parts = line.split("||")
            if (parts.size < 7) continue
            val id = parts[0].toLongOrNull() ?: continue
            val title = parts[1]
            val path = parts[2]
            val durationMs = parts[3].toLongOrNull() ?: 0L
            val positionMs = parts[4].toLongOrNull() ?: 0L
            val sizeBytes = parts[5].toLongOrNull() ?: 0L
            val timestampMs = parts[6].toLongOrNull() ?: 0L
            if (!PermanentDeleteHelper.isMarkedDeleted(context, path) && File(path).exists()) {
                result.add(
                    WatchHistoryEntry(
                        id = id,
                        title = title,
                        path = path,
                        durationMs = durationMs,
                        positionMs = positionMs,
                        sizeBytes = sizeBytes,
                        timestampMs = timestampMs
                    )
                )
            }
        }
        return result
    }

    fun clearWatchHistory(context: Context) {
        context.getSharedPreferences(PREF_HISTORY, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_HISTORY_ROWS)
            .apply()
    }

    private fun saveWatchHistoryList(context: Context, entries: List<WatchHistoryEntry>) {
        val sb = StringBuilder()
        for (entry in entries) {
            val cleanTitle = entry.title.replace("||", " ").replace("\n", " ")
            val cleanPath = entry.path.replace("||", "").replace("\n", "")
            sb.append(entry.id).append("||")
                .append(cleanTitle).append("||")
                .append(cleanPath).append("||")
                .append(entry.durationMs).append("||")
                .append(entry.positionMs).append("||")
                .append(entry.sizeBytes).append("||")
                .append(entry.timestampMs).append("\n")
        }
        context.getSharedPreferences(PREF_HISTORY, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY_ROWS, sb.toString())
            .apply()
    }

    fun applyFilterAndSort(
        videos: List<VideoModel>,
        activeFilter: String,
        selectedFolderBucketId: String?,
        searchQuery: String,
        sortConfig: SortConfig
    ): List<VideoModel> {
        val now = System.currentTimeMillis()
        val recentCutoff = now - (7L * 24L * 3600L * 1000L)
        val largeThreshold = 100L * 1024L * 1024L

        val filtered = videos.filter { video ->
            val matchesQuery = if (searchQuery.isBlank()) {
                true
            } else {
                video.title.contains(searchQuery, ignoreCase = true) ||
                        video.path.contains(searchQuery, ignoreCase = true)
            }
            if (!matchesQuery) return@filter false

            if (!selectedFolderBucketId.isNullOrBlank()) {
                val parentPath = File(video.path).parentFile?.absolutePath ?: ""
                if (parentPath != selectedFolderBucketId) return@filter false
            }

            when (activeFilter) {
                FILTER_RECENT -> {
                    val lastMod = File(video.path).lastModified()
                    lastMod >= recentCutoff
                }
                FILTER_LARGE -> video.size >= largeThreshold
                else -> true
            }
        }

        val comparator = when (sortConfig.sortField) {
            SORT_NAME -> compareBy<VideoModel> { it.title.lowercase(Locale.US) }
            SORT_SIZE -> compareBy<VideoModel> { it.size }
            SORT_LENGTH -> compareBy<VideoModel> { it.duration }
            else -> compareBy<VideoModel> { File(it.path).lastModified() }
        }

        return if (sortConfig.descending) {
            filtered.sortedWith(comparator.reversed())
        } else {
            filtered.sortedWith(comparator)
        }
    }

    fun showTopRightMoreMenu(
        context: Context,
        anchor: View,
        onToggleCleanerHub: () -> Unit,
        onOpenRecycleBin: () -> Unit,
        onForceDeepScan: () -> Unit,
        onStartMultiSelect: () -> Unit,
        onOpenSortDialog: () -> Unit
    ) {
        val popup = PopupMenu(context, anchor)
        popup.menu.add(0, 1, 0, "Cleaner & Storage Hub")
        popup.menu.add(0, 2, 1, "Recycle Bin (30 Days Safe)")
        popup.menu.add(0, 3, 2, "Deep Scan All Videos")
        popup.menu.add(0, 4, 3, "Select Multiple Videos")
        popup.menu.add(0, 5, 4, "Sort By")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> onToggleCleanerHub()
                2 -> onOpenRecycleBin()
                3 -> onForceDeepScan()
                4 -> onStartMultiSelect()
                5 -> onOpenSortDialog()
            }
            true
        }
        popup.show()
    }

    fun showPlayItStyleSortDialog(
        context: Context,
        currentConfig: SortConfig,
        onApplied: (SortConfig) -> Unit
    ) {
        var chosenField = currentConfig.sortField
        var chosenDesc = currentConfig.descending

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 20), dp(context, 18), dp(context, 20), dp(context, 14))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#14191F"))
                cornerRadius = dp(context, 18).toFloat()
                setStroke(dp(context, 1), Color.parseColor("#26323E"))
            }
        }

        val titleTv = TextView(context).apply {
            text = "Sort by"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(context, 10))
        }
        root.addView(titleTv)

        val fieldOptions = listOf(
            SORT_DATE to "Date",
            SORT_NAME to "Name",
            SORT_SIZE to "Size",
            SORT_LENGTH to "Length"
        )

        val fieldIndicators = ArrayList<ImageView>()
        val orderIndicators = ArrayList<ImageView>()
        val orderLabels = ArrayList<TextView>()

        fun updateOrderLabels(field: Int) {
            val (descText, ascText) = when (field) {
                SORT_NAME -> "From Z to A" to "From A to Z"
                SORT_SIZE -> "From large to small" to "From small to large"
                SORT_LENGTH -> "From long to short" to "From short to long"
                else -> "From new to old" to "From old to new"
            }
            if (orderLabels.size >= 2) {
                orderLabels[0].text = descText
                orderLabels[1].text = ascText
            }
        }

        fun refreshRadioDrawables() {
            for (i in fieldIndicators.indices) {
                val active = (fieldOptions[i].first == chosenField)
                fieldIndicators[i].setImageDrawable(SectionVectorDrawable(SectionVectorDrawable.ICON_RADIO, active))
            }
            if (orderIndicators.size >= 2) {
                orderIndicators[0].setImageDrawable(
                    SectionVectorDrawable(SectionVectorDrawable.ICON_RADIO, chosenDesc)
                )
                orderIndicators[1].setImageDrawable(
                    SectionVectorDrawable(SectionVectorDrawable.ICON_RADIO, !chosenDesc)
                )
            }
            updateOrderLabels(chosenField)
        }

        for ((fieldConst, label) in fieldOptions) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(context, 4), dp(context, 10), dp(context, 4), dp(context, 10))
            }
            val radioImg = ImageView(context)
            fieldIndicators.add(radioImg)
            row.addView(radioImg, LinearLayout.LayoutParams(dp(context, 22), dp(context, 22)))

            val tv = TextView(context).apply {
                text = label
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(dp(context, 14), 0, 0, 0)
            }
            row.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.setOnClickListener {
                chosenField = fieldConst
                refreshRadioDrawables()
            }
            root.addView(row)
        }

        val divider = View(context).apply {
            setBackgroundColor(Color.parseColor("#26323E"))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1))
            lp.topMargin = dp(context, 8)
            lp.bottomMargin = dp(context, 8)
            layoutParams = lp
        }
        root.addView(divider)

        val orderPairs = listOf(true to "From new to old", false to "From old to new")
        for ((isDesc, defaultText) in orderPairs) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(context, 4), dp(context, 10), dp(context, 4), dp(context, 10))
            }
            val radioImg = ImageView(context)
            orderIndicators.add(radioImg)
            row.addView(radioImg, LinearLayout.LayoutParams(dp(context, 22), dp(context, 22)))

            val tv = TextView(context).apply {
                text = defaultText
                setTextColor(Color.parseColor("#D5DCE2"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                setPadding(dp(context, 14), 0, 0, 0)
            }
            orderLabels.add(tv)
            row.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.setOnClickListener {
                chosenDesc = isDesc
                refreshRadioDrawables()
            }
            root.addView(row)
        }

        refreshRadioDrawables()

        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(context, 12), 0, 0)
        }

        val dialog = AlertDialog.Builder(context)
            .setView(root)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val cancelBtn = TextView(context).apply {
            text = "CANCEL"
            setTextColor(Color.parseColor("#9AA5B1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 8))
            setOnClickListener { dialog.dismiss() }
        }
        val okBtn = TextView(context).apply {
            text = "OK"
            setTextColor(Color.parseColor("#2ECC71"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(context, 18), dp(context, 8), dp(context, 12), dp(context, 8))
            setOnClickListener {
                val updated = SortConfig(chosenField, chosenDesc)
                saveSortConfig(context, updated)
                onApplied(updated)
                dialog.dismiss()
            }
        }
        buttonRow.addView(cancelBtn)
        buttonRow.addView(okBtn)
        root.addView(buttonRow)

        dialog.show()
    }

    fun renderHistoryStrip(
        context: Context,
        container: ViewGroup,
        onPlayHistoryItem: (VideoModel, Long) -> Unit,
        onHistoryCleared: () -> Unit
    ) {
        container.removeAllViews()
        val historyList = getWatchHistory(context)
        if (historyList.isEmpty()) {
            container.visibility = View.GONE
            return
        }
        container.visibility = View.VISIBLE

        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(context, 4), 0, dp(context, 8))
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 4), 0, dp(context, 4), dp(context, 6))
        }
        val titleTv = TextView(context).apply {
            text = "History"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
        }
        headerRow.addView(titleTv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val clearBtn = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4))
            setOnClickListener {
                clearWatchHistory(context)
                container.removeAllViews()
                container.visibility = View.GONE
                Toast.makeText(context, "Watch history cleared", Toast.LENGTH_SHORT).show()
                onHistoryCleared()
            }
        }
        val trashIcon = ImageView(context).apply {
            setImageDrawable(PermanentDeleteHelper.TrashVectorDrawable(Color.parseColor("#9AA5B1")))
        }
        clearBtn.addView(trashIcon, LinearLayout.LayoutParams(dp(context, 16), dp(context, 16)))
        val clearTv = TextView(context).apply {
            text = "Clear"
            setTextColor(Color.parseColor("#9AA5B1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(context, 4), 0, 0, 0)
        }
        clearBtn.addView(clearTv)
        headerRow.addView(clearBtn)
        wrapper.addView(headerRow)

        val scrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
        }
        val stripRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        for (entry in historyList) {
            val cardWidth = dp(context, 152)
            val cardHeight = dp(context, 86)

            val cardCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val lp = LinearLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.rightMargin = dp(context, 10)
                layoutParams = lp
            }

            val thumbFrame = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(cardWidth, cardHeight)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#161A1D"))
                    cornerRadius = dp(context, 10).toFloat()
                }
                clipToOutline = true
            }

            val thumbImage = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageDrawable(VideoAdapter.CinemaClapperPlaceholderDrawable())
            }
            thumbFrame.addView(
                thumbImage,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )

            VideoAdapter.loadThumbnailAsync(entry.path, thumbImage)

            val qualityBadge = TextView(context).apply {
                text = VideoAdapter.resolveQualityBadgeText(entry.path, entry.sizeBytes)
                setTextColor(Color.parseColor("#101418"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(context, 5), dp(context, 1), dp(context, 5), dp(context, 1))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#FFD54F"))
                    cornerRadius = dp(context, 4).toFloat()
                }
            }
            val badgeLp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.START
            ).apply {
                leftMargin = dp(context, 6)
                bottomMargin = dp(context, 8)
            }
            thumbFrame.addView(qualityBadge, badgeLp)

            val durationBadge = TextView(context).apply {
                text = "▶ ${VideoAdapter.formatDuration(entry.durationMs)}"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
                setPadding(dp(context, 5), dp(context, 1), dp(context, 5), dp(context, 1))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#B3000000"))
                    cornerRadius = dp(context, 4).toFloat()
                }
            }
            val durLp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            ).apply {
                rightMargin = dp(context, 6)
                bottomMargin = dp(context, 8)
            }
            thumbFrame.addView(durationBadge, durLp)

            val progressRatio = if (entry.durationMs > 0L) {
                (entry.positionMs.toFloat() / entry.durationMs.toFloat()).coerceIn(0.05f, 1f)
            } else {
                0.2f
            }

            val progressTrack = FrameLayout(context).apply {
                setBackgroundColor(Color.parseColor("#33FFFFFF"))
            }
            val progressFill = View(context).apply {
                setBackgroundColor(Color.parseColor("#2ECC71"))
            }
            progressTrack.addView(
                progressFill,
                FrameLayout.LayoutParams((cardWidth * progressRatio).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
            )
            thumbFrame.addView(
                progressTrack,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(context, 3),
                    Gravity.BOTTOM
                )
            )

            cardCol.addView(thumbFrame)

            val nameTv = TextView(context).apply {
                text = entry.title
                setTextColor(Color.parseColor("#E1E6EB"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(context, 2), dp(context, 4), dp(context, 2), 0)
            }
            cardCol.addView(nameTv)

            cardCol.setOnClickListener {
                val model = VideoModel(
                    id = entry.id,
                    title = entry.title,
                    path = entry.path,
                    size = entry.sizeBytes,
                    duration = entry.durationMs
                )
                onPlayHistoryItem(model, entry.positionMs)
            }

            stripRow.addView(cardCol)
        }

        scrollView.addView(stripRow)
        wrapper.addView(scrollView)
        container.addView(wrapper)
    }

    fun showFolderBrowserDialog(
        context: Context,
        folders: List<FolderModel>,
        onFolderChosen: (FolderModel?) -> Unit
    ) {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16))
            setBackgroundColor(Color.parseColor("#0B0D0E"))
        }

        val titleTv = TextView(context).apply {
            text = "Video Folders (${folders.size})"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(context, 10))
        }
        root.addView(titleTv)

        val scroll = ScrollView(context)
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val dialog = AlertDialog.Builder(context)
            .setView(root)
            .setNegativeButton("Show All Videos") { _, _ ->
                onFolderChosen(null)
            }
            .create()

        for (folder in folders) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#161A1D"))
                    cornerRadius = dp(context, 10).toFloat()
                }
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(context, 8)
                layoutParams = lp
            }

            val folderIcon = ImageView(context).apply {
                setImageDrawable(SectionVectorDrawable(SectionVectorDrawable.ICON_FOLDER, true))
            }
            row.addView(folderIcon, LinearLayout.LayoutParams(dp(context, 28), dp(context, 28)))

            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(context, 12), 0, 0, 0)
            }
            val nameTv = TextView(context).apply {
                text = folder.folderName
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTypeface(typeface, Typeface.BOLD)
            }
            val subTv = TextView(context).apply {
                text = "${folder.videoCount} videos • ${PermanentDeleteHelper.formatFileSize(folder.totalSize)}"
                setTextColor(Color.parseColor("#2ECC71"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            }
            textCol.addView(nameTv)
            textCol.addView(subTv)
            row.addView(textCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            row.setOnClickListener {
                onFolderChosen(folder)
                dialog.dismiss()
            }
            col.addView(row)
        }

        scroll.addView(col)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 320)))
        dialog.show()
    }

    class SectionVectorDrawable(
        private val iconType: Int,
        private val active: Boolean = false
    ) : Drawable() {

        companion object {
            const val ICON_SORT = 1
            const val ICON_VIEW_LIST = 2
            const val ICON_VIEW_GRID = 3
            const val ICON_RADIO = 4
            const val ICON_FOLDER = 5
            const val ICON_CLEANER_BUCKET = 6
        }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (active) Color.parseColor("#2ECC71") else Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2ECC71")
            style = Paint.Style.FILL
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return
            paint.strokeWidth = (w * 0.085f).coerceAtLeast(2.2f)

            when (iconType) {
                ICON_SORT -> {
                    canvas.drawLine(w * 0.32f, h * 0.22f, w * 0.32f, h * 0.78f, paint)
                    canvas.drawLine(w * 0.18f, h * 0.38f, w * 0.32f, h * 0.22f, paint)
                    canvas.drawLine(w * 0.46f, h * 0.38f, w * 0.32f, h * 0.22f, paint)

                    canvas.drawLine(w * 0.68f, h * 0.22f, w * 0.68f, h * 0.78f, paint)
                    canvas.drawLine(w * 0.54f, h * 0.62f, w * 0.68f, h * 0.78f, paint)
                    canvas.drawLine(w * 0.82f, h * 0.62f, w * 0.68f, h * 0.78f, paint)
                }
                ICON_VIEW_LIST -> {
                    canvas.drawLine(w * 0.20f, h * 0.28f, w * 0.82f, h * 0.28f, paint)
                    canvas.drawLine(w * 0.20f, h * 0.50f, w * 0.82f, h * 0.50f, paint)
                    canvas.drawLine(w * 0.20f, h * 0.72f, w * 0.82f, h * 0.72f, paint)
                }
                ICON_VIEW_GRID -> {
                    val r1 = RectF(w * 0.18f, h * 0.18f, w * 0.45f, h * 0.45f)
                    val r2 = RectF(w * 0.55f, h * 0.18f, w * 0.82f, h * 0.45f)
                    val r3 = RectF(w * 0.18f, h * 0.55f, w * 0.45f, h * 0.82f)
                    val r4 = RectF(w * 0.55f, h * 0.55f, w * 0.82f, h * 0.82f)
                    canvas.drawRoundRect(r1, 4f, 4f, paint)
                    canvas.drawRoundRect(r2, 4f, 4f, paint)
                    canvas.drawRoundRect(r3, 4f, 4f, paint)
                    canvas.drawRoundRect(r4, 4f, 4f, paint)
                }
                ICON_RADIO -> {
                    paint.color = if (active) Color.parseColor("#2ECC71") else Color.parseColor("#7B8794")
                    canvas.drawCircle(w * 0.5f, h * 0.5f, w * 0.38f, paint)
                    if (active) {
                        canvas.drawCircle(w * 0.5f, h * 0.5f, w * 0.20f, fillPaint)
                    }
                }
                ICON_FOLDER -> {
                    val path = Path().apply {
                        moveTo(w * 0.14f, h * 0.28f)
                        lineTo(w * 0.42f, h * 0.28f)
                        lineTo(w * 0.50f, h * 0.38f)
                        lineTo(w * 0.86f, h * 0.38f)
                        lineTo(w * 0.86f, h * 0.78f)
                        lineTo(w * 0.14f, h * 0.78f)
                        close()
                    }
                    canvas.drawPath(path, paint)
                }
                ICON_CLEANER_BUCKET -> {
                    paint.color = Color.parseColor("#2ECC71")
                    val body = RectF(w * 0.22f, h * 0.28f, w * 0.78f, h * 0.84f)
                    canvas.drawRoundRect(body, w * 0.10f, w * 0.10f, paint)
                    canvas.drawLine(w * 0.16f, h * 0.28f, w * 0.84f, h * 0.28f, paint)
                    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#FF5252")
                        style = Paint.Style.FILL
                    }
                    canvas.drawCircle(w * 0.78f, h * 0.22f, w * 0.13f, dotPaint)
                }
            }
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
            fillPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
            fillPaint.colorFilter = colorFilter
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
