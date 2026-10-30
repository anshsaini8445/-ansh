package com.app.mediaplayer

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class VideoFragment : Fragment() {

    private val scannerExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var allScannedVideos: List<VideoModel> = emptyList()
    private var activeFilter: String = HybridVideoSectionHelper.FILTER_ALL
    private var selectedFolder: FolderModel? = null
    private var currentSearchQuery: String = ""
    private var currentSortConfig = HybridVideoSectionHelper.SortConfig(
        HybridVideoSectionHelper.SORT_DATE,
        true
    )
    private var isGridViewMode: Boolean = false
    private var isCleanerVisible: Boolean = false

    private lateinit var recyclerView: RecyclerView
    private lateinit var videoAdapter: VideoAdapter
    private lateinit var cleanerHubContainer: FrameLayout
    private lateinit var historyContainer: FrameLayout
    private lateinit var folderBannerContainer: LinearLayout
    private lateinit var folderBannerText: TextView
    private lateinit var batchBarContainer: LinearLayout
    private lateinit var batchCountText: TextView
    private lateinit var emptyStateText: TextView
    private lateinit var cleanerBadgeText: TextView
    private lateinit var viewModeToggleIcon: ImageView
    private val pillButtons = LinkedHashMap<String, TextView>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        currentSortConfig = HybridVideoSectionHelper.loadSortConfig(ctx)
        isGridViewMode = HybridVideoSectionHelper.isGridMode(ctx)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A10"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            val padH = dp(ctx, 12)
            setPadding(padH, dp(ctx, 10), padH, 0)
        }

        // 1. Top PLAYit-style Header Bar ("Hybrid Player" + Cleaner Bucket Badge + 3-Dot Menu)
        val topHeader = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 4), dp(ctx, 4), dp(ctx, 4), dp(ctx, 8))
        }

        val brandTitle = TextView(ctx).apply {
            text = "Hybrid Player"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
            setTypeface(typeface, Typeface.BOLD)
        }
        topHeader.addView(brandTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val cleanerBucketBtn = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 10), dp(ctx, 5), dp(ctx, 10), dp(ctx, 5))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#182ECC71"))
                cornerRadius = dp(ctx, 16).toFloat()
                setStroke(dp(ctx, 1), Color.parseColor("#2ECC71"))
            }
            setOnClickListener {
                toggleCleanerHubVisibility()
            }
        }

        val bucketIcon = ImageView(ctx).apply {
            setImageDrawable(
                HybridVideoSectionHelper.SectionVectorDrawable(
                    HybridVideoSectionHelper.SectionVectorDrawable.ICON_CLEANER_BUCKET,
                    true
                )
            )
        }
        cleanerBucketBtn.addView(bucketIcon, LinearLayout.LayoutParams(dp(ctx, 18), dp(ctx, 18)))

        cleanerBadgeText = TextView(ctx).apply {
            val metrics = HybridCleanerHubHelper.readRealStorageMetrics()
            text = PermanentDeleteHelper.formatFileSize(metrics.freeBytes) + " Free"
            setTextColor(Color.parseColor("#2ECC71"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(ctx, 6), 0, 0, 0)
        }
        cleanerBucketBtn.addView(cleanerBadgeText)
        topHeader.addView(cleanerBucketBtn)

        val moreMenuBtn = ImageView(ctx).apply {
            setImageDrawable(VideoAdapter.ThreeDotsVectorDrawable(Color.WHITE))
            setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 6), dp(ctx, 6))
            setOnClickListener { anchor ->
                HybridVideoSectionHelper.showTopRightMoreMenu(
                    context = ctx,
                    anchor = anchor,
                    onToggleCleanerHub = { toggleCleanerHubVisibility() },
                    onOpenRecycleBin = {
                        PermanentDeleteHelper.openRecycleBinDialog(ctx) {
                            triggerBackgroundVideoSync(forceRefresh = true)
                        }
                    },
                    onForceDeepScan = {
                        Toast.makeText(ctx, "Running deep storage scan...", Toast.LENGTH_SHORT).show()
                        triggerBackgroundVideoSync(forceRefresh = true)
                    },
                    onStartMultiSelect = {
                        videoAdapter.enableSelectionMode(true)
                        updateBatchSelectionBar(videoAdapter.getSelectedVideos().size)
                    },
                    onOpenSortDialog = {
                        openSortDialog()
                    }
                )
            }
        }
        topHeader.addView(moreMenuBtn, LinearLayout.LayoutParams(dp(ctx, 36), dp(ctx, 36)))
        root.addView(topHeader)

        // 2. Expandable Cleaner Hub Container
        cleanerHubContainer = FrameLayout(ctx).apply {
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(ctx, 8)
            layoutParams = lp
        }
        root.addView(cleanerHubContainer)

        // 3. Instant Search Bar
        val searchBox = EditText(ctx).apply {
            hint = "Search videos, 8K/4K, or partial downloads..."
            setHintTextColor(Color.parseColor("#6E7A86"))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setSingleLine(true)
            setPadding(dp(ctx, 14), dp(ctx, 9), dp(ctx, 14), dp(ctx, 9))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#14191F"))
                cornerRadius = dp(ctx, 12).toFloat()
                setStroke(dp(ctx, 1), Color.parseColor("#232C35"))
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(ctx, 8)
            layoutParams = lp

            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    currentSearchQuery = s?.toString()?.trim().orEmpty()
                    refreshDisplayedVideoList()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        root.addView(searchBox)

        // 4. Watch History Carousel Strip
        historyContainer = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(historyContainer)

        // 5. Sub-Tabs / Filter Pills + Sort + List/Grid Toggle Row
        val controlsRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(ctx, 6)
            layoutParams = lp
        }

        val pillsScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
        }
        val pillsRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val filterDefs = listOf(
            HybridVideoSectionHelper.FILTER_ALL to "All Videos",
            HybridVideoSectionHelper.FILTER_FOLDERS to "Folders",
            HybridVideoSectionHelper.FILTER_RECENT to "Recent",
            HybridVideoSectionHelper.FILTER_LARGE to "Large >100MB",
            HybridVideoSectionHelper.FILTER_RECYCLE_BIN to "Recycle Bin"
        )

        pillButtons.clear()
        for ((filterKey, label) in filterDefs) {
            val pill = TextView(ctx).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(ctx, 12), dp(ctx, 6), dp(ctx, 12), dp(ctx, 6))
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.rightMargin = dp(ctx, 6)
                layoutParams = lp
                setOnClickListener {
                    handleFilterPillClick(filterKey)
                }
            }
            pillButtons[filterKey] = pill
            pillsRow.addView(pill)
        }
        pillsScroll.addView(pillsRow)
        controlsRow.addView(pillsScroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val sortBtn = ImageView(ctx).apply {
            setImageDrawable(
                HybridVideoSectionHelper.SectionVectorDrawable(
                    HybridVideoSectionHelper.SectionVectorDrawable.ICON_SORT,
                    false
                )
            )
            setPadding(dp(ctx, 7), dp(ctx, 7), dp(ctx, 7), dp(ctx, 7))
            setOnClickListener { openSortDialog() }
        }
        controlsRow.addView(sortBtn, LinearLayout.LayoutParams(dp(ctx, 34), dp(ctx, 34)))

        viewModeToggleIcon = ImageView(ctx).apply {
            setPadding(dp(ctx, 7), dp(ctx, 7), dp(ctx, 7), dp(ctx, 7))
            setOnClickListener {
                isGridViewMode = !isGridViewMode
                HybridVideoSectionHelper.setGridMode(ctx, isGridViewMode)
                applyLayoutManagerAndViewToggleIcon()
            }
        }
        controlsRow.addView(viewModeToggleIcon, LinearLayout.LayoutParams(dp(ctx, 34), dp(ctx, 34)))
        root.addView(controlsRow)

        // 6. Active Folder Drill-Down Banner
        folderBannerContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#182ECC71"))
                cornerRadius = dp(ctx, 10).toFloat()
                setStroke(dp(ctx, 1), Color.parseColor("#2ECC71"))
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(ctx, 6)
            layoutParams = lp
        }
        folderBannerText = TextView(ctx).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
        }
        val clearFolderBtn = TextView(ctx).apply {
            text = "All Folders ✕"
            setTextColor(Color.parseColor("#2ECC71"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(ctx, 10), dp(ctx, 4), dp(ctx, 6), dp(ctx, 4))
            setOnClickListener {
                selectedFolder = null
                activeFilter = HybridVideoSectionHelper.FILTER_ALL
                updateFilterPillStyles()
                refreshDisplayedVideoList()
            }
        }
        folderBannerContainer.addView(folderBannerText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        folderBannerContainer.addView(clearFolderBtn)
        root.addView(folderBannerContainer)

        // 7. Batch Selection Action Bar
        batchBarContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A242D"))
                cornerRadius = dp(ctx, 12).toFloat()
                setStroke(dp(ctx, 1), Color.parseColor("#2ECC71"))
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(ctx, 6)
            layoutParams = lp
        }
        batchCountText = TextView(ctx).apply {
            text = "0 selected"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
        }
        batchBarContainer.addView(batchCountText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val selectAllBtn = createBatchActionChip(ctx, "All", Color.parseColor("#2ECC71")) {
            videoAdapter.selectAll()
        }
        val batchRecycleBtn = createBatchActionChip(ctx, "Recycle", Color.parseColor("#FFB300")) {
            val selected = videoAdapter.getSelectedVideos()
            if (selected.isNotEmpty()) {
                for (v in selected) {
                    PermanentDeleteHelper.moveToRecycleBin(ctx, v.path, v.id, v.title, true)
                }
                Toast.makeText(ctx, "Moved ${selected.size} videos to Recycle Bin", Toast.LENGTH_SHORT).show()
                videoAdapter.enableSelectionMode(false)
                triggerBackgroundVideoSync(forceRefresh = true)
            }
        }
        val batchDeleteBtn = createBatchActionChip(ctx, "Delete", Color.parseColor("#FF5252")) {
            val selected = videoAdapter.getSelectedVideos()
            if (selected.isNotEmpty()) {
                PermanentDeleteHelper.confirmAndDeleteMultipleVideosPermanently(ctx, selected) {
                    videoAdapter.enableSelectionMode(false)
                    triggerBackgroundVideoSync(forceRefresh = true)
                }
            }
        }
        val batchCloseBtn = createBatchActionChip(ctx, "Done", Color.parseColor("#9AA5B1")) {
            videoAdapter.enableSelectionMode(false)
        }
        batchBarContainer.addView(selectAllBtn)
        batchBarContainer.addView(batchRecycleBtn)
        batchBarContainer.addView(batchDeleteBtn)
        batchBarContainer.addView(batchCloseBtn)
        root.addView(batchBarContainer)

        // 8. Empty State Readout + Video RecyclerView
        emptyStateText = TextView(ctx).apply {
            text = "No videos found in this filter. Tap 'Deep Scan All Videos' in the menu to rescan storage."
            setTextColor(Color.parseColor("#7B8794"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 24), dp(ctx, 40), dp(ctx, 24), dp(ctx, 40))
            visibility = View.GONE
        }
        root.addView(emptyStateText)

        recyclerView = RecyclerView(ctx).apply {
            clipToPadding = false
            setPadding(0, 0, 0, dp(ctx, 84))
        }

        videoAdapter = VideoAdapter(
            context = ctx,
            videos = ArrayList(),
            isGridMode = isGridViewMode,
            onVideoClick = { video ->
                launchVideoPlayer(video, startPositionMs = 0L, enterPip = false, audioOnly = false)
            },
            onPlayFloatingPip = { video ->
                launchVideoPlayer(video, startPositionMs = 0L, enterPip = true, audioOnly = false)
            },
            onPlayBackgroundAudio = { video ->
                launchVideoPlayer(video, startPositionMs = 0L, enterPip = false, audioOnly = true)
            },
            onVideoListMutated = {
                triggerBackgroundVideoSync(forceRefresh = true)
            },
            onSelectionCountChanged = { count ->
                updateBatchSelectionBar(count)
            }
        )
        recyclerView.adapter = videoAdapter
        applyLayoutManagerAndViewToggleIcon()
        updateFilterPillStyles()

        root.addView(
            recyclerView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()

        // Stage 1: <1ms Cold-Start Instant Snapshot Load (Zero Spinner)
        val cachedInstant = DeepMediaScanner.getCachedVideosInstant(ctx)
        if (cachedInstant.isNotEmpty()) {
            allScannedVideos = cachedInstant
            refreshDisplayedVideoList()
        }

        refreshWatchHistoryCarousel()

        // Stage 2: Background Full Storage & Partial-Download Sync
        triggerBackgroundVideoSync(forceRefresh = true)
    }

    override fun onResume() {
        super.onResume()
        refreshWatchHistoryCarousel()
        updateCleanerBadgeMetrics()
    }

    private fun triggerBackgroundVideoSync(forceRefresh: Boolean) {
        val ctx = context?.applicationContext ?: return
        scannerExecutor.execute {
            val freshVideos = DeepMediaScanner.scanAllVideosEverywhere(ctx, forceRefresh = forceRefresh)
            mainHandler.post {
                if (!isAdded) return@post
                allScannedVideos = freshVideos
                refreshDisplayedVideoList()
                updateCleanerBadgeMetrics()
            }
        }
    }

    private fun updateCleanerBadgeMetrics() {
        val metrics = HybridCleanerHubHelper.readRealStorageMetrics()
        cleanerBadgeText.text = PermanentDeleteHelper.formatFileSize(metrics.freeBytes) + " Free"
    }

    private fun toggleCleanerHubVisibility() {
        val ctx = context ?: return
        isCleanerVisible = !isCleanerVisible
        if (isCleanerVisible) {
            cleanerHubContainer.visibility = View.VISIBLE
            HybridCleanerHubHelper.attachCleanerHubView(ctx, cleanerHubContainer) {
                triggerBackgroundVideoSync(forceRefresh = true)
            }
        } else {
            cleanerHubContainer.removeAllViews()
            cleanerHubContainer.visibility = View.GONE
        }
    }

    private fun handleFilterPillClick(filterKey: String) {
        val ctx = context ?: return
        when (filterKey) {
            HybridVideoSectionHelper.FILTER_RECYCLE_BIN -> {
                PermanentDeleteHelper.openRecycleBinDialog(ctx) {
                    triggerBackgroundVideoSync(forceRefresh = true)
                }
            }
            HybridVideoSectionHelper.FILTER_FOLDERS -> {
                val folders = DeepMediaScanner.groupVideosByFolder(allScannedVideos)
                if (folders.isEmpty()) {
                    Toast.makeText(ctx, "No video folders found on storage", Toast.LENGTH_SHORT).show()
                    return
                }
                HybridVideoSectionHelper.showFolderBrowserDialog(ctx, folders) { chosenFolder ->
                    selectedFolder = chosenFolder
                    activeFilter = if (chosenFolder != null) {
                        HybridVideoSectionHelper.FILTER_FOLDERS
                    } else {
                        HybridVideoSectionHelper.FILTER_ALL
                    }
                    updateFilterPillStyles()
                    refreshDisplayedVideoList()
                }
            }
            else -> {
                selectedFolder = null
                activeFilter = filterKey
                updateFilterPillStyles()
                refreshDisplayedVideoList()
            }
        }
    }

    private fun updateFilterPillStyles() {
        val ctx = context ?: return
        for ((key, tv) in pillButtons) {
            val isActive = (key == activeFilter)
            tv.setTextColor(if (isActive) Color.parseColor("#0B0D0E") else Color.parseColor("#D5DCE2"))
            tv.background = GradientDrawable().apply {
                setColor(if (isActive) Color.parseColor("#2ECC71") else Color.parseColor("#161C22"))
                cornerRadius = dp(ctx, 16).toFloat()
                setStroke(
                    dp(ctx, 1),
                    if (isActive) Color.parseColor("#2ECC71") else Color.parseColor("#26323E")
                )
            }
        }
    }

    private fun applyLayoutManagerAndViewToggleIcon() {
        val ctx = context ?: return
        if (isGridViewMode) {
            recyclerView.layoutManager = GridLayoutManager(ctx, 2)
            viewModeToggleIcon.setImageDrawable(
                HybridVideoSectionHelper.SectionVectorDrawable(
                    HybridVideoSectionHelper.SectionVectorDrawable.ICON_VIEW_LIST,
                    true
                )
            )
        } else {
            recyclerView.layoutManager = LinearLayoutManager(ctx)
            viewModeToggleIcon.setImageDrawable(
                HybridVideoSectionHelper.SectionVectorDrawable(
                    HybridVideoSectionHelper.SectionVectorDrawable.ICON_VIEW_GRID,
                    false
                )
            )
        }
        videoAdapter.setGridViewMode(isGridViewMode)
    }

    private fun openSortDialog() {
        val ctx = context ?: return
        HybridVideoSectionHelper.showPlayItStyleSortDialog(ctx, currentSortConfig) { newSort ->
            currentSortConfig = newSort
            refreshDisplayedVideoList()
        }
    }

    private fun refreshDisplayedVideoList() {
        val folderBucket = selectedFolder?.bucketId
        if (selectedFolder != null) {
            folderBannerContainer.visibility = View.VISIBLE
            folderBannerText.text =
                "Folder: ${selectedFolder?.folderName} (${selectedFolder?.videoCount} videos)"
        } else {
            folderBannerContainer.visibility = View.GONE
        }

        val filteredList = HybridVideoSectionHelper.applyFilterAndSort(
            videos = allScannedVideos,
            activeFilter = activeFilter,
            selectedFolderBucketId = folderBucket,
            searchQuery = currentSearchQuery,
            sortConfig = currentSortConfig
        )

        videoAdapter.updateData(filteredList)
        emptyStateText.visibility = if (filteredList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun refreshWatchHistoryCarousel() {
        val ctx = context ?: return
        HybridVideoSectionHelper.renderHistoryStrip(
            context = ctx,
            container = historyContainer,
            onPlayHistoryItem = { video, resumePosMs ->
                launchVideoPlayer(
                    video = video,
                    startPositionMs = resumePosMs,
                    enterPip = false,
                    audioOnly = false
                )
            },
            onHistoryCleared = {
                historyContainer.visibility = View.GONE
            }
        )
    }

    private fun updateBatchSelectionBar(selectedCount: Int) {
        if (videoAdapter.isSelectionMode) {
            batchBarContainer.visibility = View.VISIBLE
            batchCountText.text = "$selectedCount selected"
        } else {
            batchBarContainer.visibility = View.GONE
        }
    }

    private fun launchVideoPlayer(
        video: VideoModel,
        startPositionMs: Long,
        enterPip: Boolean,
        audioOnly: Boolean
    ) {
        val ctx = context ?: return
        HybridVideoSectionHelper.recordWatchHistory(
            context = ctx,
            video = video,
            positionMs = startPositionMs,
            durationMs = video.duration
        )
        try {
            val intent = Intent().apply {
                setClassName(ctx.packageName, "com.app.mediaplayer.VideoPlayerActivity")
                putExtra("video_id", video.id)
                putExtra("video_title", video.title)
                putExtra("video_path", video.path)
                putExtra("video_size", video.size)
                putExtra("video_duration", video.duration)
                putExtra("start_position_ms", startPositionMs)
                putExtra("enter_pip_immediately", enterPip)
                putExtra("background_audio_mode", audioOnly)
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(ctx, "Opening player for: ${video.title}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createBatchActionChip(
        context: Context,
        label: String,
        color: Int,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4))
            setOnClickListener { onClick() }
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
