package com.app.mediaplayer

import android.app.AlertDialog
import android.content.Context
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
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

object HybridCleanerHubHelper {

    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    data class StorageMetrics(
        val totalBytes: Long,
        val freeBytes: Long,
        val usedBytes: Long,
        val usedPercent: Int
    )

    data class CleanerSnapshot(
        val storage: StorageMetrics,
        val recycleBinCount: Int,
        val recycleBinBytes: Long,
        val largeFiles: List<VideoModel>,
        val largeFilesBytes: Long,
        val duplicateFiles: List<VideoModel>,
        val duplicateWastedBytes: Long,
        val junkFiles: List<File>,
        val junkBytes: Long
    )

    fun readRealStorageMetrics(): StorageMetrics {
        return try {
            val targetPath = try {
                val ext = Environment.getExternalStorageDirectory()
                if (ext != null && ext.exists()) ext.path else Environment.getDataDirectory().path
            } catch (_: Exception) {
                Environment.getDataDirectory().path
            }
            val stat = StatFs(targetPath)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val total = (totalBlocks * blockSize).coerceAtLeast(1L)
            val free = (availableBlocks * blockSize).coerceIn(0L, total)
            val used = (total - free).coerceAtLeast(0L)
            val percent = ((used.toDouble() / total.toDouble()) * 100.0).toInt().coerceIn(0, 100)
            StorageMetrics(total, free, used, percent)
        } catch (_: Exception) {
            StorageMetrics(1L, 0L, 0L, 0)
        }
    }

    fun scanRealCleanerSnapshot(
        context: Context,
        cachedVideos: List<VideoModel>? = null
    ): CleanerSnapshot {
        val appContext = context.applicationContext
        val storage = readRealStorageMetrics()

        val recycleEntries = PermanentDeleteHelper.getRecycleBinEntries(appContext)
        var recycleBytes = 0L
        for (entry in recycleEntries) {
            val f = File(entry.path)
            if (f.exists() && f.isFile) {
                recycleBytes += f.length().coerceAtLeast(0L)
            }
        }

        val videos = cachedVideos ?: DeepMediaScanner.scanAllVideosEverywhere(appContext, false)

        val largeThreshold = 50L * 1024L * 1024L
        val largeList = videos.filter { it.size >= largeThreshold }.sortedByDescending { it.size }
        val largeBytes = largeList.fold(0L) { acc, v -> acc + v.size.coerceAtLeast(0L) }

        val groupedDuplicates = videos
            .filter { it.size > 0L && File(it.path).exists() }
            .groupBy { video ->
                val cleanName = File(video.path).name
                    .lowercase(Locale.US)
                    .replace(Regex("\\(\\d+\\)"), "")
                    .trim()
                "${video.size}_$cleanName"
            }

        val redundantDuplicates = ArrayList<VideoModel>()
        var duplicateBytes = 0L
        for ((_, group) in groupedDuplicates) {
            if (group.size > 1) {
                val sorted = group.sortedBy { File(it.path).lastModified() }
                for (i in 1 until sorted.size) {
                    redundantDuplicates.add(sorted[i])
                    duplicateBytes += sorted[i].size.coerceAtLeast(0L)
                }
            }
        }

        val junkFiles = scanJunkAndTempFiles(appContext)
        val junkBytes = junkFiles.fold(0L) { acc, f -> acc + f.length().coerceAtLeast(0L) }

        return CleanerSnapshot(
            storage = storage,
            recycleBinCount = recycleEntries.size,
            recycleBinBytes = recycleBytes,
            largeFiles = largeList,
            largeFilesBytes = largeBytes,
            duplicateFiles = redundantDuplicates,
            duplicateWastedBytes = duplicateBytes,
            junkFiles = junkFiles,
            junkBytes = junkBytes
        )
    }

    fun scanJunkAndTempFiles(context: Context): List<File> {
        val found = LinkedHashMap<String, File>()
        val junkExtensions = setOf(
            "tmp", "temp", "log", "bak", "old", "part", "crdownload", "download", "thumb", "exo"
        )

        fun collectJunkRecursive(dir: File?, depth: Int, maxDepth: Int, AlwaysIncludeCache: Boolean) {
            if (dir == null || depth > maxDepth || !dir.exists() || !dir.canRead()) return
            val children = dir.listFiles() ?: return
            for (child in children) {
                try {
                    if (child.isDirectory) {
                        val lowerDir = child.name.lowercase(Locale.US)
                        if (lowerDir == "android" && depth == 0) continue
                        val isCacheFolder = AlwaysIncludeCache ||
                                lowerDir == ".thumbnails" ||
                                lowerDir == "cache" ||
                                lowerDir == ".cache" ||
                                lowerDir == "temp"
                        collectJunkRecursive(child, depth + 1, maxDepth, isCacheFolder)
                    } else if (child.isFile) {
                        val nameLower = child.name.lowercase(Locale.US)
                        val ext = nameLower.substringAfterLast('.', "")
                        val isZeroByteFile = child.length() == 0L
                        val isJunkExt = junkExtensions.contains(ext) ||
                                nameLower.endsWith(".tmp") ||
                                nameLower.startsWith(".trashed-") ||
                                nameLower == "thumbs.db"
                        if (AlwaysIncludeCache || isJunkExt || isZeroByteFile) {
                            found[child.absolutePath] = child
                        }
                    }
                } catch (_: SecurityException) {
                } catch (_: Exception) {
                }
            }
        }

        collectJunkRecursive(context.cacheDir, 0, 4, true)
        collectJunkRecursive(context.externalCacheDir, 0, 4, true)

        val storageRoots = linkedSetOf<File>()
        try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { storageRoots.add(it) }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)?.let { storageRoots.add(it) }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)?.let { storageRoots.add(it) }
            Environment.getExternalStorageDirectory()?.let { storageRoots.add(it) }
        } catch (_: Exception) {
        }

        for (root in storageRoots) {
            collectJunkRecursive(root, 0, 2, false)
        }

        return found.values.toList()
    }

    fun attachCleanerHubView(
        context: Context,
        container: ViewGroup,
        onStorageChanged: () -> Unit
    ) {
        container.removeAllViews()
        val rootCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 14))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#12171C"))
                cornerRadius = dp(context, 16).toFloat()
                setStroke(dp(context, 1), Color.parseColor("#24303C"))
            }
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val gaugeIcon = ImageView(context).apply {
            setImageDrawable(CleanerVectorDrawable(CleanerVectorDrawable.TYPE_GAUGE))
        }
        headerRow.addView(gaugeIcon, LinearLayout.LayoutParams(dp(context, 28), dp(context, 28)))

        val headerTextCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 10), 0, dp(context, 8), 0)
        }

        val titleView = TextView(context).apply {
            text = "Device Storage & Cleaner Hub"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
        }

        val storageReadoutView = TextView(context).apply {
            val initialMetrics = readRealStorageMetrics()
            text = "Used ${PermanentDeleteHelper.formatFileSize(initialMetrics.usedBytes)} / ${
                PermanentDeleteHelper.formatFileSize(initialMetrics.totalBytes)
            } (${initialMetrics.usedPercent}%)"
            setTextColor(Color.parseColor("#9AA5B1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }

        headerTextCol.addView(titleView)
        headerTextCol.addView(storageReadoutView)
        headerRow.addView(headerTextCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val refreshBtn = TextView(context).apply {
            text = "RESCAN"
            setTextColor(Color.parseColor("#2ECC71"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#182ECC71"))
                cornerRadius = dp(context, 12).toFloat()
                setStroke(dp(context, 1), Color.parseColor("#2ECC71"))
            }
        }
        headerRow.addView(refreshBtn)
        rootCard.addView(headerRow)

        val storageBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = readRealStorageMetrics().usedPercent
            progressDrawable = createStorageProgressDrawable(context)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 8))
            lp.topMargin = dp(context, 10)
            lp.bottomMargin = dp(context, 12)
            layoutParams = lp
        }
        rootCard.addView(storageBar)

        val gridRow1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }
        val gridRow2 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(context, 8)
            layoutParams = lp
        }

        val cardRecycleSub = TextView(context)
        val cardLargeSub = TextView(context)
        val cardDuplicateSub = TextView(context)
        val cardJunkSub = TextView(context)

        var latestSnapshot: CleanerSnapshot? = null

        fun refreshHubData() {
            storageReadoutView.text = "Analyzing real device storage..."
            backgroundExecutor.execute {
                val snap = scanRealCleanerSnapshot(context)
                mainHandler.post {
                    latestSnapshot = snap
                    storageBar.progress = snap.storage.usedPercent
                    storageReadoutView.text =
                        "Used ${PermanentDeleteHelper.formatFileSize(snap.storage.usedBytes)} / ${
                            PermanentDeleteHelper.formatFileSize(snap.storage.totalBytes)
                        } (${snap.storage.usedPercent}%) • Free ${
                            PermanentDeleteHelper.formatFileSize(snap.storage.freeBytes)
                        }"

                    cardRecycleSub.text =
                        "${snap.recycleBinCount} items • ${PermanentDeleteHelper.formatFileSize(snap.recycleBinBytes)}"
                    cardLargeSub.text =
                        "${snap.largeFiles.size} files • ${PermanentDeleteHelper.formatFileSize(snap.largeFilesBytes)}"
                    cardDuplicateSub.text =
                        "${snap.duplicateFiles.size} copies • ${PermanentDeleteHelper.formatFileSize(snap.duplicateWastedBytes)}"
                    cardJunkSub.text =
                        "${snap.junkFiles.size} files • ${PermanentDeleteHelper.formatFileSize(snap.junkBytes)}"
                }
            }
        }

        val cardRecycle = buildToolGridCard(
            context = context,
            title = "Recycle Bin",
            subtitleView = cardRecycleSub,
            accentColor = Color.parseColor("#2ECC71"),
            iconType = CleanerVectorDrawable.TYPE_RECYCLE
        ) {
            PermanentDeleteHelper.openRecycleBinDialog(context) {
                refreshHubData()
                onStorageChanged()
            }
        }

        val cardLarge = buildToolGridCard(
            context = context,
            title = "Large Files (>50MB)",
            subtitleView = cardLargeSub,
            accentColor = Color.parseColor("#00E5FF"),
            iconType = CleanerVectorDrawable.TYPE_LARGE_FILE
        ) {
            val snap = latestSnapshot
            if (snap == null) {
                Toast.makeText(context, "Scanning storage, please wait...", Toast.LENGTH_SHORT).show()
            } else {
                showVideoCleanupDialog(
                    context = context,
                    dialogTitle = "Large Videos (> 50 MB)",
                    emptyMessage = "No video files larger than 50 MB were found on storage.",
                    videos = snap.largeFiles
                ) {
                    refreshHubData()
                    onStorageChanged()
                }
            }
        }

        val cardDuplicates = buildToolGridCard(
            context = context,
            title = "Duplicate Files",
            subtitleView = cardDuplicateSub,
            accentColor = Color.parseColor("#FFB300"),
            iconType = CleanerVectorDrawable.TYPE_DUPLICATE
        ) {
            val snap = latestSnapshot
            if (snap == null) {
                Toast.makeText(context, "Scanning duplicates, please wait...", Toast.LENGTH_SHORT).show()
            } else {
                showVideoCleanupDialog(
                    context = context,
                    dialogTitle = "Duplicate Media Copies",
                    emptyMessage = "Zero duplicate media files found. Your library is clean!",
                    videos = snap.duplicateFiles
                ) {
                    refreshHubData()
                    onStorageChanged()
                }
            }
        }

        val cardJunk = buildToolGridCard(
            context = context,
            title = "Junk & Temp Cleaner",
            subtitleView = cardJunkSub,
            accentColor = Color.parseColor("#FF5252"),
            iconType = CleanerVectorDrawable.TYPE_BROOM
        ) {
            val snap = latestSnapshot
            if (snap == null) {
                Toast.makeText(context, "Scanning temp & cache files...", Toast.LENGTH_SHORT).show()
            } else {
                showJunkCleanupDialog(context, snap.junkFiles) {
                    refreshHubData()
                    onStorageChanged()
                }
            }
        }

        val leftLp1 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = dp(context, 4)
        }
        val rightLp1 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = dp(context, 4)
        }
        gridRow1.addView(cardRecycle, leftLp1)
        gridRow1.addView(cardLarge, rightLp1)

        val leftLp2 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = dp(context, 4)
        }
        val rightLp2 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = dp(context, 4)
        }
        gridRow2.addView(cardDuplicates, leftLp2)
        gridRow2.addView(cardJunk, rightLp2)

        rootCard.addView(gridRow1)
        rootCard.addView(gridRow2)

        refreshBtn.setOnClickListener {
            refreshHubData()
            onStorageChanged()
        }

        container.addView(
            rootCard,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        refreshHubData()
    }

    fun openStandaloneCleanerDialog(
        context: Context,
        onStorageChanged: () -> Unit
    ) {
        val wrapper = FrameLayout(context).apply {
            setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12))
            setBackgroundColor(Color.parseColor("#0B0D0E"))
        }
        val dialog = AlertDialog.Builder(context)
            .setView(wrapper)
            .create()

        attachCleanerHubView(context, wrapper) {
            onStorageChanged()
        }
        dialog.show()
    }

    private fun buildToolGridCard(
        context: Context,
        title: String,
        subtitleView: TextView,
        accentColor: Int,
        iconType: Int,
        onClick: () -> Unit
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#182028"))
                cornerRadius = dp(context, 12).toFloat()
                setStroke(dp(context, 1), Color.parseColor("#26323E"))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }

            val iconBox = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    setColor(Color.argb(36, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)))
                    cornerRadius = dp(context, 10).toFloat()
                }
            }
            val iconView = ImageView(context).apply {
                setImageDrawable(CleanerVectorDrawable(iconType, accentColor))
            }
            iconBox.addView(
                iconView,
                FrameLayout.LayoutParams(dp(context, 22), dp(context, 22), Gravity.CENTER)
            )
            addView(iconBox, LinearLayout.LayoutParams(dp(context, 36), dp(context, 36)))

            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(context, 8), 0, 0, 0)
            }
            val titleTv = TextView(context).apply {
                text = title
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            subtitleView.apply {
                text = "Scanning..."
                setTextColor(accentColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            textCol.addView(titleTv)
            textCol.addView(subtitleView)
            addView(textCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun showVideoCleanupDialog(
        context: Context,
        dialogTitle: String,
        emptyMessage: String,
        videos: List<VideoModel>,
        onCleaned: () -> Unit
    ) {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16))
            setBackgroundColor(Color.parseColor("#0B0D0E"))
        }

        val titleView = TextView(context).apply {
            text = dialogTitle
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(typeface, Typeface.BOLD)
        }
        root.addView(titleView)

        if (videos.isEmpty()) {
            val emptyTv = TextView(context).apply {
                text = emptyMessage
                setTextColor(Color.parseColor("#9AA5B1"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, dp(context, 20), 0, dp(context, 20))
            }
            root.addView(emptyTv)
            AlertDialog.Builder(context)
                .setView(root)
                .setPositiveButton("Close", null)
                .show()
            return
        }

        val selectedSet = LinkedHashSet<VideoModel>(videos)
        val summaryTv = TextView(context).apply {
            setTextColor(Color.parseColor("#2ECC71"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(0, dp(context, 4), 0, dp(context, 10))
        }

        fun updateSummary() {
            val totalSelectedBytes = selectedSet.fold(0L) { acc, v -> acc + v.size.coerceAtLeast(0L) }
            summaryTv.text =
                "Selected ${selectedSet.size} / ${videos.size} files (${PermanentDeleteHelper.formatFileSize(totalSelectedBytes)})"
        }
        updateSummary()
        root.addView(summaryTv)

        val scrollView = ScrollView(context)
        val listLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        for (video in videos) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#161A1D"))
                    cornerRadius = dp(context, 10).toFloat()
                }
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(context, 6)
                layoutParams = lp
            }

            val checkBox = CheckBox(context).apply {
                isChecked = true
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedSet.add(video) else selectedSet.remove(video)
                    updateSummary()
                }
            }
            row.addView(checkBox)

            val infoCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(context, 8), 0, 0, 0)
            }
            val nameTv = TextView(context).apply {
                text = video.title
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            val metaTv = TextView(context).apply {
                text = "${PermanentDeleteHelper.formatFileSize(video.size)} • ${video.path}"
                setTextColor(Color.parseColor("#9AA5B1"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.START
            }
            infoCol.addView(nameTv)
            infoCol.addView(metaTv)
            row.addView(infoCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.setOnClickListener { checkBox.isChecked = !checkBox.isChecked }
            listLayout.addView(row)
        }

        scrollView.addView(listLayout)
        root.addView(scrollView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 300)))

        val dialog = AlertDialog.Builder(context)
            .setView(root)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Move to Recycle Bin") { _, _ ->
                if (selectedSet.isEmpty()) return@setNeutralButton
                for (video in selectedSet) {
                    PermanentDeleteHelper.moveToRecycleBin(
                        context = context,
                        path = video.path,
                        mediaId = video.id,
                        title = video.title,
                        isVideo = true
                    )
                }
                Toast.makeText(
                    context,
                    "Moved ${selectedSet.size} files to 30-Day Recycle Bin",
                    Toast.LENGTH_SHORT
                ).show()
                onCleaned()
            }
            .setPositiveButton("Delete Permanently") { _, _ ->
                if (selectedSet.isEmpty()) return@setPositiveButton
                var freedBytes = 0L
                for (video in selectedSet) {
                    freedBytes += video.size.coerceAtLeast(0L)
                    PermanentDeleteHelper.deletePermanentlyNow(
                        context = context,
                        path = video.path,
                        mediaId = video.id,
                        isVideo = true
                    )
                }
                Toast.makeText(
                    context,
                    "Freed ${PermanentDeleteHelper.formatFileSize(freedBytes)} permanently",
                    Toast.LENGTH_SHORT
                ).show()
                onCleaned()
            }
            .create()

        dialog.show()
    }

    private fun showJunkCleanupDialog(
        context: Context,
        junkFiles: List<File>,
        onCleaned: () -> Unit
    ) {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16))
            setBackgroundColor(Color.parseColor("#0B0D0E"))
        }

        val titleView = TextView(context).apply {
            text = "Junk, Cache & Incomplete Temp Files"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(typeface, Typeface.BOLD)
        }
        root.addView(titleView)

        if (junkFiles.isEmpty()) {
            val emptyTv = TextView(context).apply {
                text = "Zero junk, cache, or temporary residual files found!"
                setTextColor(Color.parseColor("#2ECC71"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, dp(context, 20), 0, dp(context, 20))
            }
            root.addView(emptyTv)
            AlertDialog.Builder(context)
                .setView(root)
                .setPositiveButton("Awesome", null)
                .show()
            return
        }

        val selectedFiles = LinkedHashSet<File>(junkFiles)
        val summaryTv = TextView(context).apply {
            setTextColor(Color.parseColor("#FF5252"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(0, dp(context, 4), 0, dp(context, 10))
        }

        fun updateSummary() {
            val bytes = selectedFiles.fold(0L) { acc, f -> acc + f.length().coerceAtLeast(0L) }
            summaryTv.text =
                "Selected ${selectedFiles.size} junk items (${PermanentDeleteHelper.formatFileSize(bytes)})"
        }
        updateSummary()
        root.addView(summaryTv)

        val scrollView = ScrollView(context)
        val listLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        for (file in junkFiles) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#161A1D"))
                    cornerRadius = dp(context, 10).toFloat()
                }
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(context, 6)
                layoutParams = lp
            }

            val cb = CheckBox(context).apply {
                isChecked = true
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedFiles.add(file) else selectedFiles.remove(file)
                    updateSummary()
                }
            }
            row.addView(cb)

            val col = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(context, 8), 0, 0, 0)
            }
            val nameTv = TextView(context).apply {
                text = file.name
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            val pathTv = TextView(context).apply {
                text = "${PermanentDeleteHelper.formatFileSize(file.length())} • ${file.absolutePath}"
                setTextColor(Color.parseColor("#9AA5B1"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.START
            }
            col.addView(nameTv)
            col.addView(pathTv)
            row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.setOnClickListener { cb.isChecked = !cb.isChecked }
            listLayout.addView(row)
        }

        scrollView.addView(listLayout)
        root.addView(scrollView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 280)))

        AlertDialog.Builder(context)
            .setView(root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clean Now") { _, _ ->
                var cleanedCount = 0
                var freedBytes = 0L
                for (file in selectedFiles) {
                    try {
                        val len = file.length().coerceAtLeast(0L)
                        if (file.exists() && file.delete()) {
                            cleanedCount++
                            freedBytes += len
                        }
                    } catch (_: Exception) {
                    }
                }
                Toast.makeText(
                    context,
                    "Cleaned $cleanedCount junk files (${PermanentDeleteHelper.formatFileSize(freedBytes)} freed)",
                    Toast.LENGTH_SHORT
                ).show()
                onCleaned()
            }
            .show()
    }

    private fun createStorageProgressDrawable(context: Context): Drawable {
        return object : Drawable() {
            private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#24303C")
                style = Paint.Style.FILL
            }
            private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#2ECC71")
                style = Paint.Style.FILL
            }
            private val rect = RectF()

            override fun draw(canvas: Canvas) {
                val b = bounds
                val radius = b.height() / 2f
                rect.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
                canvas.drawRoundRect(rect, radius, radius, trackPaint)

                val fraction = (level / 10000f).coerceIn(0f, 1f)
                progressPaint.color = if (fraction > 0.85f) {
                    Color.parseColor("#FF5252")
                } else {
                    Color.parseColor("#2ECC71")
                }
                val progRight = b.left + b.width() * fraction
                if (progRight > b.left) {
                    rect.set(b.left.toFloat(), b.top.toFloat(), progRight, b.bottom.toFloat())
                    canvas.drawRoundRect(rect, radius, radius, progressPaint)
                }
            }

            override fun onLevelChange(level: Int): Boolean {
                invalidateSelf()
                return true
            }

            override fun setAlpha(alpha: Int) {
                trackPaint.alpha = alpha
                progressPaint.alpha = alpha
            }

            override fun setColorFilter(colorFilter: ColorFilter?) {
                trackPaint.colorFilter = colorFilter
                progressPaint.colorFilter = colorFilter
            }

            @Deprecated("Deprecated in Java")
            override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        }
    }

    class CleanerVectorDrawable(
        private val type: Int,
        private val tintColor: Int = Color.parseColor("#2ECC71")
    ) : Drawable() {

        companion object {
            const val TYPE_GAUGE = 1
            const val TYPE_RECYCLE = 2
            const val TYPE_LARGE_FILE = 3
            const val TYPE_DUPLICATE = 4
            const val TYPE_BROOM = 5
        }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tintColor
            style = Paint.Style.STROKE
            strokeWidth = 4.2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return
            paint.strokeWidth = (w * 0.09f).coerceAtLeast(2.2f)

            when (type) {
                TYPE_GAUGE -> {
                    val oval = RectF(w * 0.14f, h * 0.16f, w * 0.86f, h * 0.88f)
                    canvas.drawArc(oval, 150f, 240f, false, paint)
                    canvas.drawLine(w * 0.5f, h * 0.54f, w * 0.72f, h * 0.32f, paint)
                }
                TYPE_RECYCLE -> {
                    val oval = RectF(w * 0.18f, h * 0.18f, w * 0.82f, h * 0.82f)
                    canvas.drawArc(oval, 20f, 280f, false, paint)
                    val arrow = Path().apply {
                        moveTo(w * 0.68f, h * 0.16f)
                        lineTo(w * 0.84f, h * 0.24f)
                        lineTo(w * 0.70f, h * 0.36f)
                    }
                    canvas.drawPath(arrow, paint)
                }
                TYPE_LARGE_FILE -> {
                    val rect = RectF(w * 0.22f, h * 0.14f, w * 0.78f, h * 0.86f)
                    canvas.drawRoundRect(rect, w * 0.08f, w * 0.08f, paint)
                    canvas.drawLine(w * 0.36f, h * 0.42f, w * 0.64f, h * 0.42f, paint)
                    canvas.drawLine(w * 0.36f, h * 0.58f, w * 0.64f, h * 0.58f, paint)
                    canvas.drawLine(w * 0.36f, h * 0.72f, w * 0.54f, h * 0.72f, paint)
                }
                TYPE_DUPLICATE -> {
                    val backRect = RectF(w * 0.16f, h * 0.16f, w * 0.68f, h * 0.68f)
                    val frontRect = RectF(w * 0.32f, h * 0.32f, w * 0.84f, h * 0.84f)
                    canvas.drawRoundRect(backRect, w * 0.07f, w * 0.07f, paint)
                    canvas.drawRoundRect(frontRect, w * 0.07f, w * 0.07f, paint)
                }
                TYPE_BROOM -> {
                    canvas.drawLine(w * 0.76f, h * 0.16f, w * 0.42f, h * 0.52f, paint)
                    val head = Path().apply {
                        moveTo(w * 0.32f, h * 0.46f)
                        lineTo(w * 0.54f, h * 0.66f)
                        lineTo(w * 0.34f, h * 0.86f)
                        lineTo(w * 0.16f, h * 0.68f)
                        close()
                    }
                    canvas.drawPath(head, paint)
                }
            }
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

    private fun dp(context: Context, value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
