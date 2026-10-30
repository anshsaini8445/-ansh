package com.app.mediaplayer

import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Rational
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

@OptIn(UnstableApi::class)
class VideoPlayerActivity : AppCompatActivity(), MediaNotificationHelper.ControlDelegate {

    private var exoPlayer: ExoPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var lastNotificationSec: Long = -1L

    private lateinit var rootContainer: FrameLayout
    private lateinit var playerView: PlayerView
    private lateinit var nightDimOverlay: View
    private lateinit var gestureSurfaceView: View
    private lateinit var hudOverlayView: VideoGestureHelper.GestureHudOverlayView
    private lateinit var controlsOverlay: FrameLayout
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var centerControlsRow: LinearLayout
    private lateinit var unlockShieldButton: TextView

    private lateinit var titleTextView: TextView
    private lateinit var badgeTextView: TextView
    private lateinit var playPauseButton: TextView
    private lateinit var positionTextView: TextView
    private lateinit var durationTextView: TextView
    private lateinit var seekBar: SeekBar

    private lateinit var speedChip: TextView
    private lateinit var ratioChip: TextView
    private lateinit var abLoopChip: TextView
    private lateinit var boostChip: TextView
    private lateinit var bgAudioChip: TextView

    private lateinit var gestureHelper: PlayerGestureHelper

    private var videoId: Long = -1L
    private var videoTitle: String = "Video"
    private var videoPath: String = ""
    private var videoSize: Long = 0L
    private var startPositionMs: Long = 0L

    private var brightnessPercent: Int = 70
    private var volumePercent: Int = 80
    private var currentSpeed: Float = 1.0f
    private var savedBaseSpeed: Float = 1.0f
    private var currentResizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT
    private var isScreenLocked: Boolean = false
    private var isControlsVisible: Boolean = true
    private var isUserScrubbingSeekBar: Boolean = false
    private var backgroundAudioMode: Boolean = false
    private var nightFilterAlpha: Float = 0f

    private var loopPointAMs: Long = -1L
    private var loopPointBMs: Long = -1L

    private val mainHandler = Handler(Looper.getMainLooper())

    private val hideHudRunnable = Runnable {
        hudOverlayView.renderState(null)
    }

    private val autoHideControlsRunnable = Runnable {
        if (exoPlayer?.isPlaying == true && !isUserScrubbingSeekBar && !isScreenLocked) {
            setControlsVisibility(false)
        }
    }

    private val progressTickRunnable = object : Runnable {
        override fun run() {
            val player = exoPlayer
            if (player != null) {
                val pos = player.currentPosition.coerceAtLeast(0L)
                val dur = player.duration.coerceAtLeast(0L)

                if (loopPointAMs >= 0L && loopPointBMs > loopPointAMs && pos >= loopPointBMs) {
                    player.seekTo(loopPointAMs)
                }

                if (!isUserScrubbingSeekBar) {
                    positionTextView.text = VideoGestureHelper.formatTimestamp(pos)
                    if (dur > 0L) {
                        durationTextView.text = VideoGestureHelper.formatTimestamp(dur)
                        seekBar.max = (dur / 100L).toInt().coerceAtLeast(1)
                        seekBar.progress = (pos / 100L).toInt().coerceIn(0, seekBar.max)
                    } else {
                        durationTextView.text = "PARTIAL/LIVE"
                    }
                }

                val sec = pos / 1000L
                if (player.isPlaying && sec != lastNotificationSec) {
                    lastNotificationSec = sec
                    postVideoMediaNotification()
                }
            }
            mainHandler.postDelayed(this, 250L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        videoId = intent.getLongExtra("video_id", -1L)
        videoTitle = intent.getStringExtra("video_title") ?: "Video Playback"
        videoPath = intent.getStringExtra("video_path") ?: ""
        videoSize = intent.getLongExtra("video_size", 0L)
        startPositionMs = intent.getLongExtra("start_position_ms", 0L)
        backgroundAudioMode = intent.getBooleanExtra("background_audio_mode", false)
        val enterPipImmediately = intent.getBooleanExtra("enter_pip_immediately", false)

        if (videoPath.isBlank() && intent.data != null) {
            videoPath = intent.data?.path ?: intent.data.toString()
            videoTitle = File(videoPath).name
        }

        brightnessPercent = VideoGestureHelper.readCurrentBrightnessPercent(this)
        volumePercent = VideoGestureHelper.readCurrentVolumePercent(this)

        buildPlayerUiProgrammatically()
        initializeExoPlayer()

        if (enterPipImmediately) {
            mainHandler.postDelayed({ enterFloatingPipMode() }, 350L)
        }
    }

    private fun buildPlayerUiProgrammatically() {
        rootContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        playerView = PlayerView(this).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        }
        rootContainer.addView(playerView)

        nightDimOverlay = View(this).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0f
            isClickable = false
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        rootContainer.addView(nightDimOverlay)

        gestureSurfaceView = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        gestureHelper = PlayerGestureHelper(
            context = this,
            hostView = gestureSurfaceView,
            getBrightnessPercent = { brightnessPercent },
            getVolumePercent = { volumePercent },
            getCurrentPositionMs = { exoPlayer?.currentPosition ?: 0L },
            getDurationMs = {
                val dur = exoPlayer?.duration ?: 0L
                if (dur > 0L) dur else 60_000L
            },
            getPlaybackSpeed = { currentSpeed },
            onSingleTapConfirmed = {
                if (isScreenLocked) {
                    unlockShieldButton.visibility =
                        if (unlockShieldButton.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                } else {
                    setControlsVisibility(!isControlsVisible)
                }
            },
            onDoubleTapSeek = { _, targetMs, hudState ->
                exoPlayer?.seekTo(targetMs)
                showHudWithAutoHide(hudState)
            },
            onBrightnessChanged = { newPercent, hudState ->
                brightnessPercent = newPercent
                VideoGestureHelper.applyHardwareBrightness(this, newPercent)
                showHudWithAutoHide(hudState)
            },
            onVolumeChanged = { newPercent, hudState ->
                volumePercent = newPercent
                VideoGestureHelper.applyHardwareVolumeAndBoost(this, newPercent, loudnessEnhancer)
                updateBoostChipLabel()
                showHudWithAutoHide(hudState)
            },
            onSeekChanged = { newPositionMs, hudState ->
                exoPlayer?.seekTo(newPositionMs)
                positionTextView.text = VideoGestureHelper.formatTimestamp(newPositionMs)
                showHudWithAutoHide(hudState)
            },
            onDynamicSpeedChanged = { newSpeed, active, hudState ->
                if (active) {
                    currentSpeed = newSpeed
                    exoPlayer?.setPlaybackSpeed(newSpeed)
                    if (hudState != null) {
                        hudOverlayView.renderState(hudState)
                    }
                } else {
                    currentSpeed = savedBaseSpeed
                    exoPlayer?.setPlaybackSpeed(savedBaseSpeed)
                    hudOverlayView.renderState(null)
                }
                updateSpeedChipLabel()
            },
            onGestureEnded = {
                mainHandler.removeCallbacks(hideHudRunnable)
                mainHandler.postDelayed(hideHudRunnable, 650L)
            }
        )
        gestureSurfaceView.setOnTouchListener(gestureHelper)
        rootContainer.addView(gestureSurfaceView)

        hudOverlayView = VideoGestureHelper.GestureHudOverlayView(this)
        val hudLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        rootContainer.addView(hudOverlayView, hudLp)

        controlsOverlay = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Top Bar
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#CC000000"), Color.TRANSPARENT)
            )
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        }

        val backBtn = createHeaderActionText("← Back", Color.WHITE) {
            saveWatchProgressNow()
            finish()
        }
        topBar.addView(backBtn)

        titleTextView = TextView(this).apply {
            text = videoTitle
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(12), 0, dp(8), 0)
        }
        topBar.addView(titleTextView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        badgeTextView = TextView(this).apply {
            val badge = VideoAdapter.resolveQualityBadgeText(videoPath, videoSize)
            text = badge
            setTextColor(Color.parseColor("#0B0D0E"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(7), dp(2), dp(7), dp(2))
            background = GradientDrawable().apply {
                setColor(
                    if (badge == "PARTIAL") Color.parseColor("#FF5252") else Color.parseColor("#FFD54F")
                )
                cornerRadius = dp(5).toFloat()
            }
        }
        topBar.addView(badgeTextView)

        val tracksBtn = createHeaderActionText("Tracks/Sub", Color.parseColor("#2ECC71")) {
            showTracksSelectionDialog()
        }
        val snapBtn = createHeaderActionText("Snap", Color.parseColor("#00FFFF")) {
            captureRealVideoFrameToGallery()
        }
        val pipBtn = createHeaderActionText("PiP", Color.WHITE) {
            enterFloatingPipMode()
        }
        topBar.addView(tracksBtn)
        topBar.addView(snapBtn)
        topBar.addView(pipBtn)
        controlsOverlay.addView(topBar)

        // Center Transport Controls
        centerControlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        val rewindBtn = createTransportCircleButton("⏪ 10s", 52) {
            val player = exoPlayer ?: return@createTransportCircleButton
            player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
            scheduleAutoHideControls()
        }

        playPauseButton = createTransportCircleButton("⏸", 66) {
            val player = exoPlayer ?: return@createTransportCircleButton
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
            updatePlayPauseLabel()
            scheduleAutoHideControls()
        }
        val playLp = LinearLayout.LayoutParams(dp(66), dp(66)).apply {
            leftMargin = dp(24)
            rightMargin = dp(24)
        }

        val forwardBtn = createTransportCircleButton("10s ⏩", 52) {
            val player = exoPlayer ?: return@createTransportCircleButton
            val dur = player.duration.coerceAtLeast(1000L)
            player.seekTo((player.currentPosition + 10_000L).coerceAtMost(dur))
            scheduleAutoHideControls()
        }

        centerControlsRow.addView(rewindBtn, LinearLayout.LayoutParams(dp(52), dp(52)))
        centerControlsRow.addView(playPauseButton, playLp)
        centerControlsRow.addView(forwardBtn, LinearLayout.LayoutParams(dp(52), dp(52)))
        controlsOverlay.addView(centerControlsRow)

        // Bottom Bar (SeekBar + Pro Tool Chips)
        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(16))
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.parseColor("#DD000000"), Color.TRANSPARENT)
            )
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }

        val seekRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        positionTextView = TextView(this).apply {
            text = "00:00"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
        }

        seekBar = SeekBar(this).apply {
            max = 1000
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val targetMs = progress.toLong() * 100L
                        positionTextView.text = VideoGestureHelper.formatTimestamp(targetMs)
                    }
                }

                override fun onStartTrackingTouch(bar: SeekBar?) {
                    isUserScrubbingSeekBar = true
                    mainHandler.removeCallbacks(autoHideControlsRunnable)
                }

                override fun onStopTrackingTouch(bar: SeekBar?) {
                    isUserScrubbingSeekBar = false
                    val targetMs = (bar?.progress ?: 0).toLong() * 100L
                    exoPlayer?.seekTo(targetMs)
                    scheduleAutoHideControls()
                }
            })
        }

        durationTextView = TextView(this).apply {
            text = "00:00"
            setTextColor(Color.parseColor("#B0BAC5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }

        seekRow.addView(positionTextView)
        seekRow.addView(seekBar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        seekRow.addView(durationTextView)
        bottomBar.addView(seekRow)

        val chipsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(8)
            layoutParams = lp
        }
        val chipsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val lockChip = createBottomToolChip("🔒 Lock") {
            toggleScreenLock(true)
        }
        speedChip = createBottomToolChip("1.0x Speed") {
            showPlaybackSpeedDialog()
        }
        ratioChip = createBottomToolChip("Aspect: Fit") {
            cycleAspectRatio()
        }
        val rotateChip = createBottomToolChip("⟳ Rotate") {
            toggleScreenOrientation()
        }
        abLoopChip = createBottomToolChip("A-B Loop") {
            cycleAbLoopPoint()
        }
        boostChip = createBottomToolChip("🔊 Boost ${volumePercent}%") {
            val nextVol = if (volumePercent <= 100) 200 else 100
            volumePercent = nextVol
            VideoGestureHelper.applyHardwareVolumeAndBoost(this, volumePercent, loudnessEnhancer)
            updateBoostChipLabel()
            Toast.makeText(
                this,
                if (nextVol > 100) "200% Hardware LoudnessEnhancer Active!" else "Standard 100% Volume",
                Toast.LENGTH_SHORT
            ).show()
        }
        bgAudioChip = createBottomToolChip(if (backgroundAudioMode) "🎧 BG Audio: ON" else "🎧 BG Audio: OFF") {
            backgroundAudioMode = !backgroundAudioMode
            bgAudioChip.text = if (backgroundAudioMode) "🎧 BG Audio: ON" else "🎧 BG Audio: OFF"
            Toast.makeText(
                this,
                if (backgroundAudioMode) "Video will keep playing audio in background" else "Background audio disabled",
                Toast.LENGTH_SHORT
            ).show()
        }
        val nightChip = createBottomToolChip("🌙 Night Dim") {
            nightFilterAlpha = when {
                nightFilterAlpha < 0.2f -> 0.35f
                nightFilterAlpha < 0.5f -> 0.60f
                else -> 0f
            }
            nightDimOverlay.alpha = nightFilterAlpha
        }
        val puppyChip = createBottomToolChip("🐶 3D Puppy") {
            MascotCompanionHelper.showBreedSelectorDialog(this) { _, _ ->
                MascotCompanionHelper.attachCompanionOverlay(this, rootContainer, { exoPlayer?.isPlaying == true })
            }
        }

        chipsRow.addView(lockChip)
        chipsRow.addView(speedChip)
        chipsRow.addView(ratioChip)
        chipsRow.addView(rotateChip)
        chipsRow.addView(abLoopChip)
        chipsRow.addView(boostChip)
        chipsRow.addView(bgAudioChip)
        chipsRow.addView(nightChip)
        chipsRow.addView(puppyChip)
        chipsScroll.addView(chipsRow)
        bottomBar.addView(chipsScroll)

        controlsOverlay.addView(bottomBar)
        rootContainer.addView(controlsOverlay)

        // Floating Unlock Button (Visible only when Screen Lock is enabled)
        unlockShieldButton = TextView(this).apply {
            text = "🔓 Tap to Unlock Controls"
            setTextColor(Color.parseColor("#0B0D0E"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            visibility = View.GONE
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2ECC71"))
                cornerRadius = dp(20).toFloat()
            }
            val lp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            ).apply {
                leftMargin = dp(20)
                topMargin = dp(24)
            }
            layoutParams = lp
            setOnClickListener {
                toggleScreenLock(false)
            }
        }
        rootContainer.addView(unlockShieldButton)

        // Optional Muso 3D DJ Headphone Puppy Companion
        MascotCompanionHelper.attachCompanionOverlay(
            context = this,
            parentFrame = rootContainer,
            isMusicPlayingProvider = { exoPlayer?.isPlaying == true },
            onDoubleTapQuickPlay = {
                val player = exoPlayer ?: return@attachCompanionOverlay
                if (player.isPlaying) player.pause() else player.play()
                updatePlayPauseLabel()
            }
        )

        setContentView(rootContainer)
    }

    private fun initializeExoPlayer() {
        val player = ExoPlayer.Builder(this).build()
        exoPlayer = player
        playerView.player = player

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                setupLoudnessEnhancer(audioSessionId)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayPauseLabel()
                if (isPlaying) {
                    MediaNotificationHelper.setControlDelegate(this@VideoPlayerActivity)
                    scheduleAutoHideControls()
                }
                postVideoMediaNotification()
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(
                    this@VideoPlayerActivity,
                    "Playback notice: ${error.errorCodeName} (Partial/Corrupt stream recovery)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        MediaNotificationHelper.setControlDelegate(this)

        val mediaUri = when {
            videoPath.startsWith("content://") || videoPath.startsWith("http") -> Uri.parse(videoPath)
            videoPath.isNotBlank() -> Uri.fromFile(File(videoPath))
            intent.data != null -> intent.data!!
            else -> null
        }

        if (mediaUri != null) {
            val item = MediaItem.fromUri(mediaUri)
            player.setMediaItem(item)
            player.prepare()
            if (startPositionMs > 0L) {
                player.seekTo(startPositionMs)
            }
            player.playWhenReady = true
        } else {
            Toast.makeText(this, "No valid video path provided", Toast.LENGTH_SHORT).show()
        }

        mainHandler.post(progressTickRunnable)
        scheduleAutoHideControls()
    }

    private fun setupLoudnessEnhancer(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) return
        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = LoudnessEnhancer(audioSessionId)
            VideoGestureHelper.applyHardwareVolumeAndBoost(this, volumePercent, loudnessEnhancer)
        } catch (_: Exception) {
        }
    }

    private fun showHudWithAutoHide(state: GestureHudState) {
        hudOverlayView.renderState(state)
        mainHandler.removeCallbacks(hideHudRunnable)
        mainHandler.postDelayed(hideHudRunnable, 900L)
    }

    private fun setControlsVisibility(visible: Boolean) {
        isControlsVisible = visible
        controlsOverlay.visibility = if (visible && !isScreenLocked) View.VISIBLE else View.GONE
        if (visible && !isScreenLocked) {
            scheduleAutoHideControls()
        }
    }

    private fun scheduleAutoHideControls() {
        mainHandler.removeCallbacks(autoHideControlsRunnable)
        mainHandler.postDelayed(autoHideControlsRunnable, 4000L)
    }

    private fun toggleScreenLock(locked: Boolean) {
        isScreenLocked = locked
        gestureHelper.setScreenLocked(locked)
        if (locked) {
            controlsOverlay.visibility = View.GONE
            unlockShieldButton.visibility = View.VISIBLE
            Toast.makeText(this, "Screen controls locked", Toast.LENGTH_SHORT).show()
        } else {
            unlockShieldButton.visibility = View.GONE
            setControlsVisibility(true)
            Toast.makeText(this, "Screen unlocked", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePlayPauseLabel() {
        val playing = exoPlayer?.isPlaying == true
        playPauseButton.text = if (playing) "⏸" else "▶"
    }

    private fun updateSpeedChipLabel() {
        speedChip.text = String.format(Locale.US, "%.2fx Speed", currentSpeed)
    }

    private fun updateBoostChipLabel() {
        boostChip.text = if (volumePercent > 100) {
            "🔥 Boost ${volumePercent}%"
        } else {
            "🔊 Vol ${volumePercent}%"
        }
    }

    private fun showPlaybackSpeedDialog() {
        val speeds = floatArrayOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f, 8.0f)
        val labels = Array(speeds.size) { i -> String.format(Locale.US, "%.2fx", speeds[i]) }

        AlertDialog.Builder(this)
            .setTitle("Playback Speed (0.25x - 8.0x)")
            .setItems(labels) { _, which ->
                val chosen = speeds[which]
                savedBaseSpeed = chosen
                currentSpeed = chosen
                exoPlayer?.setPlaybackSpeed(chosen)
                updateSpeedChipLabel()
            }
            .show()
    }

    private fun cycleAspectRatio() {
        currentResizeMode = when (currentResizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        playerView.resizeMode = currentResizeMode
        val label = when (currentResizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Aspect: Stretch Fill"
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Aspect: Crop Zoom"
            else -> "Aspect: Fit"
        }
        ratioChip.text = label
    }

    private fun toggleScreenOrientation() {
        requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    private fun cycleAbLoopPoint() {
        val pos = exoPlayer?.currentPosition ?: 0L
        when {
            loopPointAMs < 0L -> {
                loopPointAMs = pos
                abLoopChip.text = "Loop A: ${VideoGestureHelper.formatTimestamp(loopPointAMs)}"
                Toast.makeText(this, "Loop Point A set. Tap again to set Point B.", Toast.LENGTH_SHORT).show()
            }
            loopPointBMs < 0L -> {
                if (pos > loopPointAMs + 1000L) {
                    loopPointBMs = pos
                    abLoopChip.text =
                        "A-B (${VideoGestureHelper.formatTimestamp(loopPointAMs)}-${VideoGestureHelper.formatTimestamp(loopPointBMs)})"
                    Toast.makeText(this, "A-B Repeat Loop Active!", Toast.LENGTH_SHORT).show()
                } else {
                    loopPointAMs = -1L
                    abLoopChip.text = "A-B Loop"
                }
            }
            else -> {
                loopPointAMs = -1L
                loopPointBMs = -1L
                abLoopChip.text = "A-B Loop"
                Toast.makeText(this, "A-B Loop cleared", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showTracksSelectionDialog() {
        val player = exoPlayer ?: return
        val groups = player.currentTracks.groups
        val entries = ArrayList<Pair<String, () -> Unit>>()

        entries.add("Disable Subtitles" to {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        })

        for (group in groups) {
            val trackType = group.type
            if (trackType == C.TRACK_TYPE_AUDIO || trackType == C.TRACK_TYPE_TEXT) {
                val typeName = if (trackType == C.TRACK_TYPE_AUDIO) "Audio" else "Subtitle"
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val lang = format.language ?: "und"
                    val label = format.label ?: "Track ${i + 1}"
                    val selected = group.isTrackSelected(i)
                    val prefix = if (selected) "✓ " else "   "
                    entries.add("$prefix[$typeName] $label ($lang)" to {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(trackType, false)
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                            .build()
                    })
                }
            }
        }

        val labels = entries.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Audio & Subtitle Tracks")
            .setItems(labels) { _, which ->
                entries[which].second.invoke()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun captureRealVideoFrameToGallery() {
        if (videoPath.isBlank()) return
        val currentPosUs = (exoPlayer?.currentPosition ?: 0L).coerceAtLeast(0L) * 1000L
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoPath)
            val bitmap = retriever.getFrameAtTime(currentPosUs, MediaMetadataRetriever.OPTION_CLOSEST)
            if (bitmap != null) {
                val picturesDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "HybridPlayer"
                )
                if (!picturesDir.exists()) {
                    picturesDir.mkdirs()
                }
                val outFile = File(picturesDir, "HybridSnap_${System.currentTimeMillis()}.jpg")
                FileOutputStream(outFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 94, fos)
                }
                MediaScannerConnection.scanFile(this, arrayOf(outFile.absolutePath), arrayOf("image/jpeg"), null)
                Toast.makeText(this, "Saved frame to ${outFile.absolutePath}", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Unable to decode video frame at current timestamp", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Frame capture failed: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            try {
                retriever?.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun enterFloatingPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (_: Exception) {
                Toast.makeText(this, "Picture-in-Picture is not supported on this device", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            controlsOverlay.visibility = View.GONE
            unlockShieldButton.visibility = View.GONE
        } else if (!isScreenLocked) {
            setControlsVisibility(true)
        }
    }

    private fun saveWatchProgressNow() {
        val player = exoPlayer ?: return
        if (videoPath.isBlank()) return
        val model = VideoModel(
            id = videoId,
            title = videoTitle,
            path = videoPath,
            size = videoSize.coerceAtLeast(File(videoPath).length()),
            duration = player.duration.coerceAtLeast(0L)
        )
        HybridVideoSectionHelper.recordWatchHistory(
            context = this,
            video = model,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.coerceAtLeast(0L)
        )
    }

    private fun postVideoMediaNotification() {
        val player = exoPlayer ?: return
        val pos = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration.coerceAtLeast(0L)
        val modeSubtitle = if (backgroundAudioMode) "BG Video Audio • Hybrid 8K Engine" else "Hybrid 8K Video Player"
        MediaNotificationHelper.showOrUpdateNotification(
            context = this,
            title = videoTitle,
            subtitle = modeSubtitle,
            mediaPath = videoPath,
            isVideo = true,
            isPlaying = player.isPlaying,
            positionMs = pos,
            durationMs = dur,
            playbackSpeed = currentSpeed,
            contentIntentActivity = VideoPlayerActivity::class.java
        )
    }

    private fun switchVideoByOffset(offset: Int) {
        val list = DeepMediaScanner.getCachedVideosInstant(this)
        if (list.isEmpty()) return
        val idx = list.indexOfFirst { it.path == videoPath }
        val targetIdx = if (idx >= 0) {
            (idx + offset + list.size) % list.size
        } else {
            0
        }
        val nextVid = list[targetIdx]
        saveWatchProgressNow()
        videoId = nextVid.id
        videoTitle = nextVid.title
        videoPath = nextVid.path
        videoSize = nextVid.size
        titleTextView.text = videoTitle
        badgeTextView.text = VideoAdapter.resolveQualityBadgeText(videoPath, videoSize)
        val player = exoPlayer ?: return
        val uri = if (File(videoPath).exists()) Uri.fromFile(File(videoPath)) else Uri.parse(videoPath)
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = true
        postVideoMediaNotification()
    }

    override fun onNotificationPlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) player.pause() else player.play()
        updatePlayPauseLabel()
        postVideoMediaNotification()
    }

    override fun onNotificationRewind10s() {
        val player = exoPlayer ?: return
        val target = (player.currentPosition - 10_000L).coerceAtLeast(0L)
        player.seekTo(target)
        postVideoMediaNotification()
    }

    override fun onNotificationFastForward10s() {
        val player = exoPlayer ?: return
        val dur = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
        val target = (player.currentPosition + 10_000L).coerceAtMost(dur)
        player.seekTo(target)
        postVideoMediaNotification()
    }

    override fun onNotificationNext() {
        switchVideoByOffset(1)
    }

    override fun onNotificationPrevious() {
        switchVideoByOffset(-1)
    }

    override fun onNotificationSeekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs.coerceAtLeast(0L))
        postVideoMediaNotification()
    }

    override fun onNotificationStop() {
        exoPlayer?.pause()
        updatePlayPauseLabel()
        MediaNotificationHelper.cancelNotification(this)
    }

    override fun onPause() {
        super.onPause()
        saveWatchProgressNow()
        val inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode
        if (!inPip && !backgroundAudioMode) {
            exoPlayer?.pause()
        }
        postVideoMediaNotification()
    }

    override fun onDestroy() {
        saveWatchProgressNow()
        mainHandler.removeCallbacksAndMessages(null)
        if (MediaNotificationHelper.getControlDelegate() === this) {
            MediaNotificationHelper.setControlDelegate(null)
        }
        MediaNotificationHelper.cancelNotification(this)
        try {
            loudnessEnhancer?.release()
        } catch (_: Exception) {
        }
        loudnessEnhancer = null
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }

    private fun createHeaderActionText(label: String, textColor: Int, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { onClick() }
        }
    }

    private fun createTransportCircleButton(label: String, sizeDp: Int, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (sizeDp > 60) 22f else 13f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#B3141E30"))
                cornerRadius = dp(sizeDp / 2).toFloat()
                setStroke(dp(1), Color.parseColor("#2ECC71"))
            }
            setOnClickListener { onClick() }
        }
    }

    private fun createBottomToolChip(label: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#CC161A1D"))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.parseColor("#26323E"))
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.rightMargin = dp(8)
            layoutParams = lp
            setOnClickListener { onClick() }
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
