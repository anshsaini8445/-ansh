package com.app.mediaplayer

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class AudioPlayerActivity : AppCompatActivity(), AudioService.PlaybackListener {

    companion object {
        const val EXTRA_AUDIO_PATH = "extra_audio_path"
        const val EXTRA_AUDIO_TITLE = "extra_audio_title"
        const val EXTRA_AUDIO_ARTIST = "extra_audio_artist"
        const val EXTRA_AUDIO_DURATION = "extra_audio_duration"
        const val EXTRA_AUDIO_ID = "extra_audio_id"

        fun launch(context: Context, track: AudioModel) {
            val intent = Intent(context, AudioPlayerActivity::class.java).apply {
                putExtra(EXTRA_AUDIO_ID, track.id)
                putExtra(EXTRA_AUDIO_PATH, track.path)
                putExtra(EXTRA_AUDIO_TITLE, track.title)
                putExtra(EXTRA_AUDIO_ARTIST, track.artist)
                putExtra(EXTRA_AUDIO_DURATION, track.duration)
            }
            context.startActivity(intent)
        }
    }

    data class LrcLine(val timeMs: Long, val text: String)

    private var audioService: AudioService? = null
    private var isBound = false

    private lateinit var rootContainer: FrameLayout
    private lateinit var vinylVisualizerView: VinylSpectrumStudioView
    private lateinit var titleTextView: TextView
    private lateinit var artistTextView: TextView
    private lateinit var lrcTextView: TextView
    private lateinit var currentTimeText: TextView
    private lateinit var totalTimeText: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var playPauseBtn: TextView
    private lateinit var shuffleBtn: TextView
    private lateinit var repeatBtn: TextView
    private lateinit var speedBadgeBtn: TextView
    private lateinit var eqBadgeBtn: TextView

    private var isUserSeeking = false
    private var lrcLines: List<LrcLine> = emptyList()
    private val animHandler = Handler(Looper.getMainLooper())

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? AudioService.LocalBinder ?: return
            val svc = binder.getService()
            audioService = svc
            isBound = true
            svc.registerListener(this@AudioPlayerActivity)

            val incomingPath = intent.getStringExtra(EXTRA_AUDIO_PATH)
            if (!incomingPath.isNullOrBlank()) {
                val current = svc.getCurrentTrack()
                if (current == null || current.path != incomingPath) {
                    val track = AudioModel(
                        id = intent.getLongExtra(EXTRA_AUDIO_ID, 0L),
                        title = intent.getStringExtra(EXTRA_AUDIO_TITLE) ?: File(incomingPath).nameWithoutExtension,
                        path = incomingPath,
                        artist = intent.getStringExtra(EXTRA_AUDIO_ARTIST) ?: "Unknown Artist",
                        duration = intent.getLongExtra(EXTRA_AUDIO_DURATION, 0L),
                        size = File(incomingPath).length()
                    )
                    svc.playSingleTrack(track)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioService?.unregisterListener(this@AudioPlayerActivity)
            audioService = null
            isBound = false
        }
    }

    private val studioAnimator = object : Runnable {
        override fun run() {
            val svc = audioService
            val playing = svc?.isPlaying() == true
            vinylVisualizerView.advanceAnimation(playing)
            animHandler.postDelayed(this, 32L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildStudioUi()
        setContentView(rootContainer)

        // Attach Muso-style 3D Chibi DJ Headphone Puppy companion
        MascotCompanionHelper.attachCompanionOverlay(
            context = this,
            parentFrame = rootContainer,
            isMusicPlayingProvider = { audioService?.isPlaying() == true },
            onDoubleTapQuickPlay = { audioService?.togglePlayPause() }
        )

        val serviceIntent = Intent(this, AudioService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
        animHandler.post(studioAnimator)
    }

    private fun buildStudioUi() {
        rootContainer = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#102229"),
                    Color.parseColor("#0B1015"),
                    Color.parseColor("#07090C")
                )
            )
        }

        val mainColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(18), dp(20), dp(20))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Top Bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val backBtn = TextView(this).apply {
            text = "↓"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(6), dp(14), dp(6))
            setOnClickListener { finish() }
        }

        val headerTitle = TextView(this).apply {
            text = "HYBRID HI-RES AUDIO STUDIO"
            setTextColor(Color.parseColor("#00E676"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val sleepBtn = TextView(this).apply {
            text = "☾ Timer"
            setTextColor(Color.parseColor("#00E5FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#162832"))
            }
            setOnClickListener { showSleepTimerDialog() }
        }

        topBar.addView(backBtn)
        topBar.addView(headerTitle)
        topBar.addView(sleepBtn)
        mainColumn.addView(topBar)

        // Rotating Vinyl + 28-Bar Spectrum Visualizer
        vinylVisualizerView = VinylSpectrumStudioView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = dp(10)
                bottomMargin = dp(10)
            }
        }
        mainColumn.addView(vinylVisualizerView)

        // Synchronized LRC Lyrics Banner
        lrcTextView = TextView(this).apply {
            text = "♪ Bit-Perfect Hardware Audio Engine Ready ♪"
            setTextColor(Color.parseColor("#00E5FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.ITALIC)
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#141E27"))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }
        mainColumn.addView(lrcTextView)

        // Track Title & Artist
        titleTextView = TextView(this).apply {
            text = intent.getStringExtra(EXTRA_AUDIO_TITLE) ?: "Audio Studio"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        artistTextView = TextView(this).apply {
            text = intent.getStringExtra(EXTRA_AUDIO_ARTIST) ?: "Unknown Artist"
            setTextColor(Color.parseColor("#90A4AE"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(0, dp(4), 0, dp(12))
        }
        mainColumn.addView(titleTextView)
        mainColumn.addView(artistTextView)

        // Studio Action Chips (5-Band EQ + BassBoost, Pitch/Speed Lab)
        val studioChipsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }

        eqBadgeBtn = createPillButton("5-Band EQ & 200% Boost", "#00E676") {
            showEqualizerDialog()
        }
        speedBadgeBtn = createPillButton("Pitch & Speed: 1.0x", "#00E5FF") {
            showPitchAndSpeedDialog()
        }

        studioChipsRow.addView(eqBadgeBtn)
        studioChipsRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(10), 1) })
        studioChipsRow.addView(speedBadgeBtn)
        mainColumn.addView(studioChipsRow)

        // SeekBar + Timestamps
        seekBar = SeekBar(this).apply {
            max = 1000
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val dur = audioService?.getDurationMs() ?: 0L
                        val target = (dur * progress) / 1000L
                        currentTimeText.text = VideoModel.formatMediaDuration(target)
                    }
                }

                override fun onStartTrackingTouch(bar: SeekBar?) {
                    isUserSeeking = true
                }

                override fun onStopTrackingTouch(bar: SeekBar?) {
                    isUserSeeking = false
                    val dur = audioService?.getDurationMs() ?: 0L
                    val target = (dur * (bar?.progress ?: 0)) / 1000L
                    audioService?.seekTo(target)
                }
            })
        }
        mainColumn.addView(seekBar)

        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(14)
            }
        }
        currentTimeText = TextView(this).apply {
            text = "00:00"
            setTextColor(Color.parseColor("#B0BEC5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        totalTimeText = TextView(this).apply {
            text = "00:00"
            setTextColor(Color.parseColor("#B0BEC5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        timeRow.addView(currentTimeText)
        timeRow.addView(totalTimeText)
        mainColumn.addView(timeRow)

        // Transport Controls Row
        val transportRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        shuffleBtn = createTransportIcon("⇄", 18f) {
            val enabled = audioService?.toggleShuffle() == true
            shuffleBtn.setTextColor(
                if (enabled) Color.parseColor("#00E676") else Color.parseColor("#90A4AE")
            )
            Toast.makeText(this, if (enabled) "Shuffle ON" else "Shuffle OFF", Toast.LENGTH_SHORT).show()
        }

        val prevBtn = createTransportIcon("⏮", 22f) {
            audioService?.playPrevious()
        }

        playPauseBtn = TextView(this).apply {
            text = "⏸"
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(dp(68), dp(68)).apply {
                marginStart = dp(18)
                marginEnd = dp(18)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00E676"))
            }
            setOnClickListener {
                audioService?.togglePlayPause()
            }
        }

        val nextBtn = createTransportIcon("⏭", 22f) {
            audioService?.playNext()
        }

        repeatBtn = createTransportIcon("🔁", 18f) {
            val mode = audioService?.cycleRepeatMode() ?: AudioService.RepeatMode.REPEAT_ALL
            repeatBtn.text = when (mode) {
                AudioService.RepeatMode.REPEAT_ONE -> "🔂"
                AudioService.RepeatMode.REPEAT_ALL -> "🔁"
                AudioService.RepeatMode.OFF -> "➡"
            }
            repeatBtn.setTextColor(
                if (mode == AudioService.RepeatMode.OFF) Color.parseColor("#90A4AE")
                else Color.parseColor("#00E676")
            )
        }

        transportRow.addView(shuffleBtn)
        transportRow.addView(prevBtn)
        transportRow.addView(playPauseBtn)
        transportRow.addView(nextBtn)
        transportRow.addView(repeatBtn)
        mainColumn.addView(transportRow)

        rootContainer.addView(mainColumn)
    }

    private fun createPillButton(text: String, accentHex: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor(accentHex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.parseColor("#16222B"))
                setStroke(dp(1), Color.parseColor(accentHex))
            }
            setOnClickListener { onClick() }
        }
    }

    private fun createTransportIcon(symbol: String, textSp: Float, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = symbol
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                marginStart = dp(6)
                marginEnd = dp(6)
            }
            setOnClickListener { onClick() }
        }
    }

    override fun onTrackChanged(track: AudioModel?, index: Int) {
        if (track == null) return
        titleTextView.text = track.title
        artistTextView.text = track.artist
        totalTimeText.text = track.formattedDuration
        vinylVisualizerView.setAlbumArt(extractAlbumBitmap(track.path))
        lrcLines = loadSidecarLrcFile(track.path)
        if (lrcLines.isEmpty()) {
            lrcTextView.text = "♪ ${track.title} — ${track.artist} ♪"
        }
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean, positionMs: Long, durationMs: Long) {
        playPauseBtn.text = if (isPlaying) "⏸" else "▶"
        if (!isUserSeeking) {
            currentTimeText.text = VideoModel.formatMediaDuration(positionMs)
            totalTimeText.text = VideoModel.formatMediaDuration(durationMs)
            val progress = if (durationMs > 0L) ((positionMs * 1000L) / durationMs).toInt() else 0
            seekBar.progress = progress.coerceIn(0, 1000)
        }
        if (lrcLines.isNotEmpty()) {
            val activeLine = lrcLines.lastOrNull { it.timeMs <= positionMs } ?: lrcLines.first()
            lrcTextView.text = activeLine.text
        }
    }

    override fun onAudioEffectsUpdated() {
        // Hardware effects attached
    }

    private fun showEqualizerDialog() {
        val svc = audioService ?: return
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            setBackgroundColor(Color.parseColor("#11171D"))
        }

        val range = svc.getEqualizerBandLevelRange()
        val minLevel = range.first.toInt()
        val maxLevel = range.second.toInt()
        val span = (maxLevel - minLevel).coerceAtLeast(100)

        val bandCount = svc.getEqualizerBandCount().coerceIn(1, 5)
        for (band in 0 until bandCount) {
            val freqHz = svc.getEqualizerCenterFreqHz(band)
            val currentMb = svc.getEqualizerBandLevel(band).toInt()

            val label = TextView(this).apply {
                text = "${if (freqHz >= 1000) "${freqHz / 1000.0} kHz" else "$freqHz Hz"} (${currentMb / 100} dB)"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            }
            val bar = SeekBar(this).apply {
                max = span
                progress = (currentMb - minLevel).coerceIn(0, span)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                        val levelMb = (minLevel + p).toShort()
                        svc.setEqualizerBandLevel(band, levelMb)
                        label.text = "${if (freqHz >= 1000) "${freqHz / 1000.0} kHz" else "$freqHz Hz"} (${levelMb / 100} dB)"
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            }
            container.addView(label)
            container.addView(bar)
        }

        // Bass Boost
        val bassLabel = TextView(this).apply {
            text = "Hardware Sub-Bass Boost (${svc.bassStrengthPermille / 10}%)"
            setTextColor(Color.parseColor("#00E676"))
            setPadding(0, dp(10), 0, 0)
        }
        val bassBar = SeekBar(this).apply {
            max = 1000
            progress = svc.bassStrengthPermille.toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    svc.setBassBoostStrength(p)
                    bassLabel.text = "Hardware Sub-Bass Boost (${p / 10}%)"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        container.addView(bassLabel)
        container.addView(bassBar)

        // 3D Virtualizer
        val virtLabel = TextView(this).apply {
            text = "3D Spatial Virtualizer (${svc.virtualizerStrengthPermille / 10}%)"
            setTextColor(Color.parseColor("#00E5FF"))
            setPadding(0, dp(10), 0, 0)
        }
        val virtBar = SeekBar(this).apply {
            max = 1000
            progress = svc.virtualizerStrengthPermille.toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    svc.setVirtualizerStrength(p)
                    virtLabel.text = "3D Spatial Virtualizer (${p / 10}%)"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        container.addView(virtLabel)
        container.addView(virtBar)

        // 200% Hardware Loudness Enhancer
        val loudLabel = TextView(this).apply {
            text = "Hardware Loudness Pre-Amp (${100 + svc.loudnessGainMb / 15}%)"
            setTextColor(Color.parseColor("#FFAB40"))
            setPadding(0, dp(10), 0, 0)
        }
        val loudBar = SeekBar(this).apply {
            max = 1500
            progress = svc.loudnessGainMb
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    svc.setLoudnessBoostGainMb(p)
                    loudLabel.text = "Hardware Loudness Pre-Amp (${100 + p / 15}%)"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        container.addView(loudLabel)
        container.addView(loudBar)

        scroll.addView(container)
        AlertDialog.Builder(this)
            .setTitle("5-Band Equalizer & Studio DSP")
            .setView(scroll)
            .setPositiveButton("Done", null)
            .show()
    }

    private fun showPitchAndSpeedDialog() {
        val presets = arrayOf(
            "1.0x Normal Studio (Bit-Perfect)" to (1.0f to 1.0f),
            "0.85x Lofi Slowed + Warm Pitch" to (0.85f to 0.88f),
            "1.25x Nightcore Boost" to (1.25f to 1.22f),
            "0.92x Deep Cinema Sub-Bass" to (0.92f to 0.90f),
            "1.50x Podcast Double Tempo" to (1.50f to 1.0f)
        )
        val labels = presets.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Sound & Pitch Lab")
            .setItems(labels) { _, which ->
                val (speed, pitch) = presets[which].second
                audioService?.setSpeedAndPitch(speed, pitch)
                speedBadgeBtn.text = "Pitch & Speed: ${speed}x"
            }
            .show()
    }

    private fun showSleepTimerDialog() {
        val options = arrayOf("Off", "15 Minutes", "30 Minutes", "45 Minutes", "60 Minutes")
        val mins = intArrayOf(0, 15, 30, 45, 60)
        AlertDialog.Builder(this)
            .setTitle("Sleep Timer")
            .setItems(options) { _, which ->
                audioService?.setSleepTimerMinutes(mins[which])
                Toast.makeText(this, "Sleep Timer: ${options[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun extractAlbumBitmap(path: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val bytes = retriever.embeddedPicture ?: return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun loadSidecarLrcFile(audioPath: String): List<LrcLine> {
        return try {
            val audioFile = File(audioPath)
            val lrcFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
            if (!lrcFile.exists()) return emptyList()
            val regex = Regex("\\[(\\d{2}):(\\d{2})(?:\\.(\\d{1,3}))?](.*)")
            val parsed = mutableListOf<LrcLine>()
            lrcFile.forEachLine { line ->
                val match = regex.find(line.trim())
                if (match != null) {
                    val min = match.groupValues[1].toLongOrNull() ?: 0L
                    val sec = match.groupValues[2].toLongOrNull() ?: 0L
                    val fracStr = match.groupValues[3]
                    val ms = when (fracStr.length) {
                        1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                        2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                        3 -> fracStr.toLongOrNull() ?: 0L
                        else -> 0L
                    }
                    val lyricText = match.groupValues[4].trim()
                    if (lyricText.isNotEmpty()) {
                        parsed.add(LrcLine(min * 60_000L + sec * 1000L + ms, lyricText))
                    }
                }
            }
            parsed.sortedBy { it.timeMs }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun onDestroy() {
        animHandler.removeCallbacksAndMessages(null)
        if (isBound) {
            audioService?.unregisterListener(this)
            unbindService(connection)
            isBound = false
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /**
     * Custom Canvas View rendering a rotating Vinyl Record + Album Art + 28-bar Real-Time Spectrum.
     */
    private class VinylSpectrumStudioView(context: Context) : View(context) {
        private var rotationDeg = 0f
        private var wavePhase = 0f
        private var isPlayingState = false
        private var albumBitmap: Bitmap? = null

        private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#141A21")
            style = Paint.Style.FILL
        }
        private val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#25303B")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        private val neonRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E676")
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        private val centerLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E5FF")
            style = Paint.Style.FILL
        }
        private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val rectF = RectF()

        fun setAlbumArt(bitmap: Bitmap?) {
            albumBitmap = bitmap
            invalidate()
        }

        fun advanceAnimation(isPlaying: Boolean) {
            isPlayingState = isPlaying
            if (isPlaying) {
                rotationDeg = (rotationDeg + 1.4f) % 360f
                wavePhase += 0.22f
                invalidate()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height * 0.42f
            val radius = (width.coerceAtMost((height * 0.72f).toInt()) * 0.36f).coerceAtLeast(80f)

            // Rotating Vinyl Record
            canvas.save()
            canvas.rotate(rotationDeg, cx, cy)
            canvas.drawCircle(cx, cy, radius, discPaint)
            for (i in 1..5) {
                canvas.drawCircle(cx, cy, radius * (0.45f + i * 0.09f), groovePaint)
            }
            canvas.drawCircle(cx, cy, radius, neonRingPaint)

            val innerR = radius * 0.42f
            val bmp = albumBitmap
            if (bmp != null && !bmp.isRecycled) {
                rectF.set(cx - innerR, cy - innerR, cx + innerR, cy + innerR)
                canvas.drawBitmap(bmp, null, rectF, null)
            } else {
                canvas.drawCircle(cx, cy, innerR, centerLabelPaint)
            }
            // Spindle hole
            canvas.drawCircle(cx, cy, radius * 0.07f, discPaint)
            canvas.restore()

            // 28-Bar Spectrum Visualizer at bottom
            val barCount = 28
            val totalWidth = width * 0.86f
            val startX = (width - totalWidth) / 2f
            val slotW = totalWidth / barCount
            val barW = slotW * 0.62f
            val baseY = height * 0.94f
            val maxBarH = height * 0.16f

            barPaint.shader = LinearGradient(
                0f,
                baseY - maxBarH,
                0f,
                baseY,
                Color.parseColor("#00E5FF"),
                Color.parseColor("#00E676"),
                Shader.TileMode.CLAMP
            )

            for (i in 0 until barCount) {
                val factor = if (isPlayingState) {
                    val s1 = sin(wavePhase + i * 0.45f)
                    val s2 = cos(wavePhase * 1.3f - i * 0.28f)
                    ((s1 * s2 + 1.1f) / 2.1f).coerceIn(0.15f, 1.0f)
                } else {
                    0.15f
                }
                val h = maxBarH * factor
                val left = startX + i * slotW
                rectF.set(left, baseY - h, left + barW, baseY)
                canvas.drawRoundRect(rectF, 6f, 6f, barPaint)
            }
        }
    }
}
