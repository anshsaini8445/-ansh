package com.app.mediaplayer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

class AudioFragment : Fragment(), AudioService.PlaybackListener {

    enum class AudioFilter {
        ALL_SONGS, FAVORITES, HI_RES_LOSSLESS
    }

    private lateinit var rootLayout: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var countBadge: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var miniPlayerBar: LinearLayout
    private lateinit var miniPlayerTitle: TextView
    private lateinit var miniPlayerSubtitle: TextView
    private lateinit var miniPlayPauseBtn: TextView
    private lateinit var audioAdapter: AudioAdapter

    private val allTracks = mutableListOf<AudioModel>()
    private var activeFilter: AudioFilter = AudioFilter.ALL_SONGS
    private var hideShortClipsUnder30s: Boolean = true
    private var searchQuery: String = ""

    private var audioService: AudioService? = null
    private var isServiceBound = false
    private val scanExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? AudioService.LocalBinder ?: return
            val svc = binder.getService()
            audioService = svc
            isServiceBound = true
            svc.registerListener(this@AudioFragment)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioService?.unregisterListener(this@AudioFragment)
            audioService = null
            isServiceBound = false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        rootLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0F14"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        buildTopHeader(ctx)
        buildSearchAndFilterChips(ctx)
        buildActionBanner(ctx)
        buildTrackListSection(ctx)
        buildMiniPlayerBar(ctx)

        return rootLayout
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        val intent = Intent(ctx, AudioService::class.java)
        ctx.startService(intent)
        ctx.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        scanDeviceAudioLibrary()
    }

    override fun onResume() {
        super.onResume()
        scanDeviceAudioLibrary()
    }

    private fun buildTopHeader(ctx: Context) {
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 8))
        }

        val title = TextView(ctx).apply {
            text = "Hi-Res Music Studio"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        countBadge = TextView(ctx).apply {
            text = "0 Tracks"
            setTextColor(Color.parseColor("#00E676"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(ctx, 10), dp(ctx, 5), dp(ctx, 10), dp(ctx, 5))
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 12).toFloat()
                setColor(Color.parseColor("#132A23"))
            }
        }

        val binBtn = TextView(ctx).apply {
            text = "🗑 Bin"
            setTextColor(Color.parseColor("#FFAB40"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(ctx, 10), dp(ctx, 5), dp(ctx, 10), dp(ctx, 5))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(ctx, 8)
            }
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 12).toFloat()
                setColor(Color.parseColor("#282015"))
            }
            setOnClickListener {
                PermanentDeleteHelper.openRecycleBinDialog(ctx) {
                    scanDeviceAudioLibrary()
                }
            }
        }

        header.addView(title)
        header.addView(countBadge)
        header.addView(binBtn)
        rootLayout.addView(header)
    }

    private fun buildSearchAndFilterChips(ctx: Context) {
        searchInput = EditText(ctx).apply {
            hint = "Search songs, artists, FLAC, MP3..."
            setHintTextColor(Color.parseColor("#6E7E8B"))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setSingleLine(true)
            setPadding(dp(ctx, 14), dp(ctx, 10), dp(ctx, 14), dp(ctx, 10))
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 14).toFloat()
                setColor(Color.parseColor("#151C24"))
                setStroke(dp(ctx, 1), Color.parseColor("#25313D"))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(ctx, 16), dp(ctx, 4), dp(ctx, 16), dp(ctx, 8))
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    searchQuery = s?.toString()?.trim() ?: ""
                    applyFiltersAndRefresh()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        rootLayout.addView(searchInput)

        val chipScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(dp(ctx, 16), 0, dp(ctx, 16), dp(ctx, 8))
        }
        val chipRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        fun addChip(label: String, onClick: (TextView) -> Unit): TextView {
            val tv = TextView(ctx).apply {
                text = label
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(ctx, 14), dp(ctx, 7), dp(ctx, 14), dp(ctx, 7))
                background = GradientDrawable().apply {
                    cornerRadius = dp(ctx, 16).toFloat()
                    setColor(Color.parseColor("#18222C"))
                    setStroke(dp(ctx, 1), Color.parseColor("#2B3B4B"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dp(ctx, 8)
                }
            }
            tv.setOnClickListener { onClick(tv) }
            chipRow.addView(tv)
            return tv
        }

        addChip("All Songs") {
            activeFilter = AudioFilter.ALL_SONGS
            applyFiltersAndRefresh()
        }
        addChip("♥ Favorites") {
            activeFilter = AudioFilter.FAVORITES
            applyFiltersAndRefresh()
        }
        addChip("Hi-Res / FLAC") {
            activeFilter = AudioFilter.HI_RES_LOSSLESS
            applyFiltersAndRefresh()
        }
        addChip("Hide <30s: ON") { chip ->
            hideShortClipsUnder30s = !hideShortClipsUnder30s
            chip.text = if (hideShortClipsUnder30s) "Hide <30s: ON" else "Hide <30s: OFF"
            applyFiltersAndRefresh()
        }

        chipScroll.addView(chipRow)
        rootLayout.addView(chipScroll)
    }

    private fun buildActionBanner(ctx: Context) {
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 16), dp(ctx, 4), dp(ctx, 16), dp(ctx, 8))
        }

        val shuffleAllBtn = TextView(ctx).apply {
            text = "▶ Shuffle Play All"
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(ctx, 16), dp(ctx, 9), dp(ctx, 16), dp(ctx, 9))
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 18).toFloat()
                setColor(Color.parseColor("#00E676"))
            }
            setOnClickListener {
                val visible = audioAdapter.getItems()
                if (visible.isEmpty()) {
                    Toast.makeText(ctx, "No audio tracks found on device", Toast.LENGTH_SHORT).show()
                } else {
                    val shuffled = visible.shuffled()
                    audioService?.setQueueAndPlay(shuffled, 0)
                    AudioPlayerActivity.launch(ctx, shuffled.first())
                }
            }
        }

        val rescanBtn = TextView(ctx).apply {
            text = "↻ Deep Scan"
            setTextColor(Color.parseColor("#00E5FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(ctx, 14), dp(ctx, 9), dp(ctx, 14), dp(ctx, 9))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(ctx, 10)
            }
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 18).toFloat()
                setColor(Color.parseColor("#132530"))
            }
            setOnClickListener {
                scanDeviceAudioLibrary()
                Toast.makeText(ctx, "Scanning device storage for audio...", Toast.LENGTH_SHORT).show()
            }
        }

        bar.addView(shuffleAllBtn)
        bar.addView(rescanBtn)
        rootLayout.addView(bar)
    }

    private fun buildTrackListSection(ctx: Context) {
        val listFrame = android.widget.FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        audioAdapter = AudioAdapter(
            context = ctx,
            items = mutableListOf(),
            onTrackClick = { track, index ->
                val currentList = audioAdapter.getItems()
                audioService?.setQueueAndPlay(currentList, index)
                AudioPlayerActivity.launch(ctx, track)
            },
            onLibraryMutated = {
                scanDeviceAudioLibrary()
            }
        )

        recyclerView = RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
            adapter = audioAdapter
            setPadding(dp(ctx, 16), dp(ctx, 4), dp(ctx, 16), dp(ctx, 16))
            clipToPadding = false
        }

        emptyStateText = TextView(ctx).apply {
            text = "No audio tracks matched your filter.\nTap '↻ Deep Scan' or disable '<30s' filter."
            setTextColor(Color.parseColor("#78909C"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(dp(ctx, 24), dp(ctx, 24), dp(ctx, 24), dp(ctx, 24))
        }

        listFrame.addView(recyclerView)
        listFrame.addView(emptyStateText)
        rootLayout.addView(listFrame)
    }

    private fun buildMiniPlayerBar(ctx: Context) {
        miniPlayerBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dp(ctx, 14), dp(ctx, 10), dp(ctx, 14), dp(ctx, 10))
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 16).toFloat()
                setColor(Color.parseColor("#15242C"))
                setStroke(dp(ctx, 1), Color.parseColor("#00E676"))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(ctx, 12), dp(ctx, 4), dp(ctx, 12), dp(ctx, 10))
            }
            setOnClickListener {
                val track = audioService?.getCurrentTrack()
                if (track != null) {
                    AudioPlayerActivity.launch(ctx, track)
                }
            }
        }

        val infoCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        miniPlayerTitle = TextView(ctx).apply {
            text = "Not Playing"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        miniPlayerSubtitle = TextView(ctx).apply {
            text = "Tap to open Hi-Res Studio"
            setTextColor(Color.parseColor("#00E676"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            maxLines = 1
        }

        infoCol.addView(miniPlayerTitle)
        infoCol.addView(miniPlayerSubtitle)

        miniPlayPauseBtn = TextView(ctx).apply {
            text = "▶"
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 40), dp(ctx, 40)).apply {
                marginStart = dp(ctx, 8)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00E676"))
            }
            setOnClickListener {
                audioService?.togglePlayPause()
            }
        }

        val nextBtn = TextView(ctx).apply {
            text = "⏭"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 40), dp(ctx, 40)).apply {
                marginStart = dp(ctx, 4)
            }
            setOnClickListener {
                audioService?.playNext()
            }
        }

        miniPlayerBar.addView(infoCol)
        miniPlayerBar.addView(miniPlayPauseBtn)
        miniPlayerBar.addView(nextBtn)
        rootLayout.addView(miniPlayerBar)
    }

    private fun scanDeviceAudioLibrary() {
        val ctx = context ?: return
        scanExecutor.execute {
            val discovered = LinkedHashMap<String, AudioModel>()

            // Step 1: Query MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            try {
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.SIZE
                )
                val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
                ctx.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

                    while (cursor.moveToNext()) {
                        val path = cursor.getString(dataCol) ?: continue
                        if (PermanentDeleteHelper.isMarkedDeleted(ctx, path)) continue
                        val file = File(path)
                        if (!file.exists() || file.length() <= 1024L) continue

                        val id = cursor.getLong(idCol)
                        val rawTitle = cursor.getString(titleCol)
                            ?: cursor.getString(nameCol)
                            ?: file.nameWithoutExtension
                        val rawArtist = cursor.getString(artistCol)
                            ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                            ?: "Unknown Artist"
                        val rawAlbum = cursor.getString(albumCol)
                            ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                            ?: "Unknown Album"
                        val duration = cursor.getLong(durCol).coerceAtLeast(0L)
                        val size = cursor.getLong(sizeCol).takeIf { it > 0L } ?: file.length()

                        discovered[path] = AudioModel(
                            id = id,
                            title = rawTitle,
                            path = path,
                            artist = rawArtist,
                            duration = duration,
                            size = size,
                            album = rawAlbum
                        )
                    }
                }
            } catch (_: Exception) {
            }

            // Step 2: Direct folder scan for Music & Downloads (catches unindexed FLAC/WAV/MP3/M4A)
            val audioExtensions = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "alac")
            val roots = listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES)
            )
            for (root in roots) {
                scanDirectoryForAudio(ctx, root, audioExtensions, discovered, 0)
            }

            val resultList = discovered.values.toList()
            mainHandler.post {
                if (!isAdded) return@post
                allTracks.clear()
                allTracks.addAll(resultList)
                applyFiltersAndRefresh()
            }
        }
    }

    private fun scanDirectoryForAudio(
        ctx: Context,
        dir: File?,
        extensions: Set<String>,
        out: LinkedHashMap<String, AudioModel>,
        depth: Int
    ) {
        if (dir == null || !dir.exists() || !dir.isDirectory || depth > 3) return
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                scanDirectoryForAudio(ctx, f, extensions, out, depth + 1)
            } else {
                val ext = f.extension.lowercase(Locale.US)
                if (ext in extensions && f.length() > 8192L && !out.containsKey(f.absolutePath)) {
                    if (!PermanentDeleteHelper.isMarkedDeleted(ctx, f.absolutePath)) {
                        out[f.absolutePath] = AudioModel(
                            id = f.absolutePath.hashCode().toLong(),
                            title = f.nameWithoutExtension,
                            path = f.absolutePath,
                            artist = f.parentFile?.name ?: "Local Storage",
                            duration = 0L,
                            size = f.length()
                        )
                    }
                }
            }
        }
    }

    private fun applyFiltersAndRefresh() {
        val ctx = context ?: return
        val filtered = allTracks.filter { track ->
            if (hideShortClipsUnder30s && track.duration in 1L..29_999L) {
                return@filter false
            }
            when (activeFilter) {
                AudioFilter.ALL_SONGS -> true
                AudioFilter.FAVORITES -> AudioAdapter.isFavorite(ctx, track.path)
                AudioFilter.HI_RES_LOSSLESS -> {
                    val ext = File(track.path).extension.lowercase(Locale.US)
                    ext in setOf("flac", "wav", "alac", "aiff", "m4a") || track.size > 12_000_000L
                }
            } && (searchQuery.isEmpty() ||
                track.title.contains(searchQuery, ignoreCase = true) ||
                track.artist.contains(searchQuery, ignoreCase = true))
        }

        countBadge.text = "${filtered.size} Tracks"
        audioAdapter.updateData(filtered)
        emptyStateText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onTrackChanged(track: AudioModel?, index: Int) {
        if (track == null) {
            miniPlayerBar.visibility = View.GONE
        } else {
            miniPlayerBar.visibility = View.VISIBLE
            miniPlayerTitle.text = track.title
            miniPlayerSubtitle.text = "${track.artist} • Tap for Studio"
            audioAdapter.setActivePlayingPath(track.path)
        }
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean, positionMs: Long, durationMs: Long) {
        miniPlayPauseBtn.text = if (isPlaying) "⏸" else "▶"
    }

    override fun onAudioEffectsUpdated() {}

    override fun onDestroyView() {
        if (isServiceBound) {
            audioService?.unregisterListener(this)
            requireContext().unbindService(serviceConnection)
            isServiceBound = false
        }
        super.onDestroyView()
    }

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}
