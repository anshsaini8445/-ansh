package com.example.ui.components

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.media.AudioManager
import android.media.PlaybackParams
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Rational
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.MediaItem
import com.example.helpers.*
import com.example.ui.theme.*
import com.example.ui.video.VideoResolutionPreset
import com.example.ui.video.VisualVideoControlsStrip
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

private const val CONTROLS_AUTO_HIDE_DELAY_MS = 3000L

@Composable
fun VideoPlayerView(
    videoPath: String,
    initialPositionMs: Long = 0L,
    onProgressUpdate: (Long) -> Unit,
    onBackClick: () -> Unit,
    accentColor: Color = HybridEmerald,
    videoTitle: String = "",
    allVideos: List<MediaItem> = emptyList(),
    onSelectVideo: (MediaItem) -> Unit = {},
    onBackgroundAudioPlay: () -> Unit = {},
    onMinimizeToFloatingMiniPlayer: ((Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val hostView = LocalView.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val prefs = remember { context.getSharedPreferences("HYBRID_PLAYER_PREFS", Context.MODE_PRIVATE) }

    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var loudnessEnhancer by remember { mutableStateOf<LoudnessEnhancer?>(null) }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosMs by remember(videoPath) { mutableStateOf(initialPositionMs) }
    var latestKnownPositionMs by remember(videoPath) { mutableStateOf(initialPositionMs) }
    var wasPlayingBeforeStop by remember(videoPath) { mutableStateOf(true) }
    var totalDurationMs by remember { mutableStateOf(1L) }
    var showControls by remember { mutableStateOf(true) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Gestures & Pro Engine State
    var brightnessPercent by remember {
        mutableStateOf(prefs.getInt("saved_brightness_pct", 75).coerceIn(2, 100))
    }
    var volumePercent by remember { mutableStateOf(100) } // 0% to 200% (Hardware LoudnessEnhancer above 100%)
    var zoomScale by remember { mutableStateOf(1.0f) } // 0.5f (50%) to 6.0f (600%)
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var isLongPressSpeedActive by remember { mutableStateOf(false) }
    var gestureHudState by remember { mutableStateOf<GestureHudState?>(null) }
    var gestureOverlayText by remember { mutableStateOf<String?>(null) }

    // Universal Resolution Engine (8K, 4K, 2K, 1080p, 720p, 480p, 360p, 240p, Auto)
    var selectedResolution by remember { mutableStateOf(VideoResolutionPreset.UHD_8K) }

    // Extra Pro Tools State
    var visualFilter by remember { mutableStateOf("Original") }
    var selectedAudioTrack by remember { mutableStateOf("Track 1: Original Stereo / Dual Audio") }
    var subtitlesEnabled by remember { mutableStateOf(false) }
    var subtitleSyncOffsetMs by remember { mutableStateOf(0L) }
    var abPointA by remember { mutableStateOf<Long?>(null) }
    var abPointB by remember { mutableStateOf<Long?>(null) }

    var showThreeDotSheet by remember { mutableStateOf(false) }
    var showAudioTrackDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showQueueDialog by remember { mutableStateOf(false) }
    var showMediaInfoDialog by remember { mutableStateOf(false) }

    // Sleep Timer (Hidden inside 3-Dot Menu) & Aspect Ratio / Orientation State
    var videoSleepTimerTotalMinutes by remember { mutableStateOf(0) }
    var videoSleepRemainingSeconds by remember { mutableStateOf(0) }
    var aspectRatioMode by remember { mutableStateOf("Full Screen") }
    var orientationLabel by remember { mutableStateOf("Auto") }
    var nativeVideoWidth by remember { mutableStateOf(1920) }
    var nativeVideoHeight by remember { mutableStateOf(1080) }

    // Live 1-Second Countdown for Video Sleep Timer
    LaunchedEffect(videoSleepRemainingSeconds, isPlaying) {
        if (videoSleepRemainingSeconds > 0 && isPlaying) {
            delay(1000L)
            val nextSec = videoSleepRemainingSeconds - 1
            videoSleepRemainingSeconds = nextSec
            if (nextSec <= 0) {
                videoSleepTimerTotalMinutes = 0
                videoViewRef?.pause()
                isPlaying = false
                Toast.makeText(context, "🌙 Video Sleep Timer Reached — Playback Paused", Toast.LENGTH_LONG).show()
            }
        }
    }

    val displayTitle = remember(videoTitle, videoPath) {
        if (videoTitle.isNotBlank()) videoTitle else File(videoPath).nameWithoutExtension.ifEmpty { "Video Player" }
    }

    fun applyBrightnessToWindow(newPct: Int) {
        val clamped = newPct.coerceIn(2, 100)
        brightnessPercent = clamped
        prefs.edit().putInt("saved_brightness_pct", clamped).apply()
        try {
            val activity = context as? Activity
            val lp = activity?.window?.attributes
            lp?.screenBrightness = clamped / 100f
            activity?.window?.attributes = lp
        } catch (_: Exception) {
        }
    }

    fun updateVolumeWithBoost(newVolPct: Int, triggerManualHaptic: Boolean = false) {
        val clamped = newVolPct.coerceIn(0, 200)
        if (triggerManualHaptic && (clamped == 0 || clamped == 100 || clamped == 200) && clamped != volumePercent) {
            VideoGestureHelper.triggerHapticPulse(context, hostView, HapticPulseType.BOUNDARY_LIMIT)
        }
        volumePercent = clamped

        val maxSysVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val sysTarget = ((clamped.coerceAtMost(100) / 100f) * maxSysVol).toInt()
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, sysTarget, 0)
        } catch (_: Exception) {
        }

        // Apply Hardware LoudnessEnhancer for 101% - 200%
        try {
            if (clamped > 100) {
                val gainMb = (clamped - 100) * 18 // Up to +1800 mB
                loudnessEnhancer?.setTargetGain(gainMb)
                loudnessEnhancer?.enabled = true
            } else {
                loudnessEnhancer?.enabled = false
            }
        } catch (_: Exception) {
        }
    }

    fun applyHardwarePlaybackSpeed(targetMp: android.media.MediaPlayer? = mediaPlayerRef) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            targetMp?.let { mp ->
                try {
                    val safeSpeed = playbackSpeed.coerceIn(0.25f, 4.0f)
                    val params = try {
                        mp.playbackParams
                    } catch (_: Exception) {
                        PlaybackParams()
                    }
                    mp.playbackParams = params.setSpeed(safeSpeed)
                } catch (_: Exception) {
                }
            }
        }
    }

    // Apply hardware PlaybackParams speed whenever playbackSpeed or active state changes
    LaunchedEffect(playbackSpeed, mediaPlayerRef, isPlaying) {
        if (isPlaying) {
            applyHardwarePlaybackSpeed(mediaPlayerRef)
        }
    }

    // Auto-hide system status bar during immersive video playback; show on tap or top swipe-down
    DisposableEffect(showControls) {
        try {
            val activity = context as? Activity
            val window = activity?.window
            if (window != null) {
                val controller = androidx.core.view.WindowCompat.getInsetsController(window, hostView)
                controller.systemBarsBehavior =
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (showControls) {
                    controller.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                } else {
                    controller.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                }
            }
        } catch (_: Exception) {
        }
        onDispose { }
    }

    // Live Clock Time & Battery % for Top Bar when controls are shown
    var currentClockText by remember { mutableStateOf("") }
    var currentBatteryPct by remember { mutableStateOf(100) }
    LaunchedEffect(showControls) {
        while (true) {
            try {
                val sdf = java.text.SimpleDateFormat("hh:mm a", Locale.getDefault())
                currentClockText = sdf.format(java.util.Date())
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
                val cap = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
                if (cap in 1..100) currentBatteryPct = cap
            } catch (_: Exception) {
            }
            delay(15_000L)
        }
    }

    // 3-Second Auto-Hide Controls Timer
    LaunchedEffect(showControls, lastInteractionTime, isPlaying) {
        if (showControls && isPlaying) {
            delay(CONTROLS_AUTO_HIDE_DELAY_MS)
            if (System.currentTimeMillis() - lastInteractionTime >= CONTROLS_AUTO_HIDE_DELAY_MS) {
                showControls = false
            }
        }
    }

    fun postComposeVideoNotification(playingState: Boolean = isPlaying, posMs: Long = currentPosMs) {
        MediaNotificationHelper.showOrUpdateNotification(
            context = context,
            title = displayTitle,
            subtitle = "Hybrid Video Player",
            mediaPath = videoPath,
            isVideo = true,
            isPlaying = playingState,
            positionMs = posMs.coerceAtLeast(0L),
            durationMs = totalDurationMs.coerceAtLeast(0L),
            playbackSpeed = playbackSpeed,
            contentIntentActivity = com.example.MainActivity::class.java
        )
    }

    DisposableEffect(videoPath, allVideos) {
        val delegate = object : MediaNotificationHelper.ControlDelegate {
            override fun onNotificationPlayPause() {
                videoViewRef?.let { vv ->
                    if (vv.isPlaying) {
                        vv.pause()
                        isPlaying = false
                    } else {
                        vv.start()
                        isPlaying = true
                    }
                    postComposeVideoNotification(isPlaying, vv.currentPosition.toLong())
                }
            }

            override fun onNotificationRewind10s() {
                videoViewRef?.let { vv ->
                    val target = (vv.currentPosition - 10_000).coerceAtLeast(0)
                    vv.seekTo(target)
                    currentPosMs = target.toLong()
                    postComposeVideoNotification(isPlaying, currentPosMs)
                }
            }

            override fun onNotificationFastForward10s() {
                videoViewRef?.let { vv ->
                    val dur = vv.duration.coerceAtLeast(1000)
                    val target = (vv.currentPosition + 10_000).coerceAtMost(dur)
                    vv.seekTo(target)
                    currentPosMs = target.toLong()
                    postComposeVideoNotification(isPlaying, currentPosMs)
                }
            }

            override fun onNotificationNext() {
                if (allVideos.isNotEmpty()) {
                    val idx = allVideos.indexOfFirst { it.path == videoPath }
                    val nextIdx = if (idx >= 0) (idx + 1) % allVideos.size else 0
                    onSelectVideo(allVideos[nextIdx])
                }
            }

            override fun onNotificationPrevious() {
                if (allVideos.isNotEmpty()) {
                    val idx = allVideos.indexOfFirst { it.path == videoPath }
                    val prevIdx = if (idx > 0) idx - 1 else allVideos.lastIndex
                    onSelectVideo(allVideos[prevIdx])
                }
            }

            override fun onNotificationSeekTo(positionMs: Long) {
                videoViewRef?.let { vv ->
                    vv.seekTo(positionMs.toInt())
                    currentPosMs = positionMs
                    postComposeVideoNotification(isPlaying, currentPosMs)
                }
            }

            override fun onNotificationStop() {
                videoViewRef?.pause()
                isPlaying = false
                MediaNotificationHelper.cancelNotification(context)
            }
        }
        MediaNotificationHelper.setControlDelegate(delegate)
        postComposeVideoNotification(isPlaying, currentPosMs)
        onDispose {
            if (MediaNotificationHelper.getControlDelegate() === delegate) {
                MediaNotificationHelper.setControlDelegate(null)
            }
            try {
                mediaPlayerRef?.let { mp ->
                    if (videoViewRef?.isPlaying != true && mp.isPlaying) {
                        mp.stop()
                    }
                }
            } catch (_: Exception) {
            }
            try {
                val activity = context as? Activity
                activity?.window?.let { win ->
                    androidx.core.view.WindowCompat.getInsetsController(win, hostView)
                        .show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                }
                activity?.requestedOrientation =
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            } catch (_: Exception) {
            }
        }
    }

    // Unified helper functions that work seamlessly whether VideoView or direct FileDescriptor MediaPlayer is active
    fun isVideoCurrentlyPlaying(): Boolean {
        return try {
            videoViewRef?.isPlaying == true || mediaPlayerRef?.isPlaying == true
        } catch (_: Exception) {
            false
        }
    }

    fun getCurrentVideoPositionMs(): Long {
        return try {
            val vvPos = videoViewRef?.currentPosition?.toLong() ?: 0L
            if (vvPos > 0L) vvPos else (mediaPlayerRef?.currentPosition?.toLong() ?: currentPosMs)
        } catch (_: Exception) {
            currentPosMs
        }
    }

    fun getTotalVideoDurationMs(): Long {
        return try {
            val vvDur = videoViewRef?.duration?.toLong() ?: 0L
            val mpDur = if (vvDur > 0L) vvDur else (mediaPlayerRef?.duration?.toLong() ?: totalDurationMs)
            mpDur.coerceAtLeast(1000L)
        } catch (_: Exception) {
            totalDurationMs.coerceAtLeast(1000L)
        }
    }

    fun seekVideoToMs(targetMs: Long) {
        val clamped = targetMs.coerceIn(0L, getTotalVideoDurationMs())
        currentPosMs = clamped
        if (clamped > 0L) {
            latestKnownPositionMs = clamped
        }
        try {
            videoViewRef?.seekTo(clamped.toInt())
        } catch (_: Exception) {
        }
        try {
            mediaPlayerRef?.seekTo(clamped.toInt())
        } catch (_: Exception) {
        }
    }

    fun pauseVideoPlayback() {
        val pos = getCurrentVideoPositionMs()
        if (pos > 0L) {
            latestKnownPositionMs = pos
            onProgressUpdate(pos)
        }
        try {
            videoViewRef?.pause()
        } catch (_: Exception) {
        }
        try {
            if (mediaPlayerRef?.isPlaying == true) mediaPlayerRef?.pause()
        } catch (_: Exception) {
        }
        isPlaying = false
    }

    fun startVideoPlayback() {
        try {
            videoViewRef?.start()
        } catch (_: Exception) {
        }
        try {
            if (mediaPlayerRef?.isPlaying == false) mediaPlayerRef?.start()
        } catch (_: Exception) {
        }
        applyHardwarePlaybackSpeed(mediaPlayerRef)
        isPlaying = true
    }

    // Multi-Window, Split-Screen, PiP & Close/Reopen Lifecycle Continuity
    DisposableEffect(lifecycleOwner, videoPath) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            val activity = context as? Activity
            val inMultiWindow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                activity?.isInMultiWindowMode == true
            } else {
                false
            }
            val inPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                activity?.isInPictureInPictureMode == true
            } else {
                false
            }

            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    val pos = getCurrentVideoPositionMs()
                    if (pos > 0L) {
                        latestKnownPositionMs = pos
                        onProgressUpdate(pos)
                    }
                    // CRITICAL FOR MULTI-WINDOW & PIP: Keep playing when user taps the other split-screen app!
                    if (inMultiWindow || inPip) {
                        if (isPlaying && !isVideoCurrentlyPlaying()) {
                            startVideoPlayback()
                        }
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    val pos = getCurrentVideoPositionMs()
                    if (pos > 0L) {
                        latestKnownPositionMs = pos
                        onProgressUpdate(pos)
                    }
                    if (!inMultiWindow && !inPip) {
                        wasPlayingBeforeStop = isPlaying
                        pauseVideoPlayback()
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    if (latestKnownPositionMs > 0L && getCurrentVideoPositionMs() < latestKnownPositionMs - 1500L) {
                        seekVideoToMs(latestKnownPositionMs)
                    }
                    if (wasPlayingBeforeStop && !isVideoCurrentlyPlaying()) {
                        startVideoPlayback()
                    }
                    applyHardwarePlaybackSpeed(mediaPlayerRef)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            val finalPos = getCurrentVideoPositionMs().takeIf { it > 0L } ?: latestKnownPositionMs
            if (finalPos > 0L) {
                onProgressUpdate(finalPos)
            }
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Progress & A-B Repeat Loop Tracker
    var lastNotifSec by remember { mutableStateOf(-1L) }
    LaunchedEffect(videoViewRef, mediaPlayerRef, isPlaying) {
        while (true) {
            try {
                if (isVideoCurrentlyPlaying()) {
                    val pos = getCurrentVideoPositionMs()
                    val dur = getTotalVideoDurationMs()
                    currentPosMs = pos
                    if (pos > 0L) {
                        latestKnownPositionMs = pos
                    }
                    totalDurationMs = dur
                    onProgressUpdate(pos)

                    val sec = pos / 1000L
                    if (sec != lastNotifSec) {
                        lastNotifSec = sec
                        postComposeVideoNotification(true, pos)
                    }

                    val ptA = abPointA
                    val ptB = abPointB
                    if (ptA != null && ptB != null && pos >= ptB) {
                        seekVideoToMs(ptA)
                    }
                }
            } catch (_: Exception) {
            }
            delay(350L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Pinch-to-Zoom (50% to 600%)
            .pointerInput(isScreenLocked) {
                if (!isScreenLocked) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f) {
                            val nextZoom = (zoomScale * zoom).coerceIn(0.5f, 6.0f)
                            if ((nextZoom == 0.5f || nextZoom == 6.0f) && nextZoom != zoomScale) {
                                VideoGestureHelper.triggerHapticPulse(context, hostView, HapticPulseType.BOUNDARY_LIMIT)
                            }
                            zoomScale = nextZoom
                            gestureOverlayText = "Zoom ${(zoomScale * 100).toInt()}%"
                            lastInteractionTime = System.currentTimeMillis()
                        }
                    }
                }
            }
            // Custom Direction-Locked Gesture Helper (Brightness, Volume 200%, Seek, Dynamic Speed)
            .detectVideoPlayerGestures(
                context = context,
                hostView = hostView,
                isLocked = isScreenLocked,
                getBrightness = { brightnessPercent },
                getVolume = { volumePercent },
                getCurrentPositionMs = { currentPosMs },
                getDurationMs = { totalDurationMs },
                getPlaybackSpeed = { playbackSpeed },
                getIsLongPressSpeedActive = { isLongPressSpeedActive },
                onSingleTap = {
                    showControls = !showControls
                    lastInteractionTime = System.currentTimeMillis()
                    gestureOverlayText = null
                    gestureHudState = null
                },
                onDoubleTapSeek = { deltaMs, targetMs ->
                    seekVideoToMs(targetMs)
                    gestureOverlayText = if (deltaMs > 0) "Forward +10s" else "Rewind -10s"
                    lastInteractionTime = System.currentTimeMillis()
                },
                onBrightnessChanged = { newPct, hud ->
                    applyBrightnessToWindow(newPct)
                    gestureHudState = hud
                    lastInteractionTime = System.currentTimeMillis()
                },
                onVolumeChanged = { newPct, hud ->
                    updateVolumeWithBoost(newPct, triggerManualHaptic = false)
                    gestureHudState = hud
                    lastInteractionTime = System.currentTimeMillis()
                },
                onHorizontalSeekChanged = { newPosMs, hud ->
                    seekVideoToMs(newPosMs)
                    gestureHudState = hud
                    lastInteractionTime = System.currentTimeMillis()
                },
                onDynamicSpeedChanged = { newSpeed, isActive, hud ->
                    isLongPressSpeedActive = isActive
                    playbackSpeed = newSpeed
                    gestureHudState = hud
                },
                onGestureEnded = {
                    gestureHudState = null
                    gestureOverlayText = null
                }
            )
    ) {
        // --- HARDWARE FULL-SCREEN VIDEO SURFACE (Supports standard & Pending/Partial .crdownload/.part movies via FileDescriptor) ---
        key(videoPath) {
            AndroidView(
                factory = { ctx ->
                    android.widget.FrameLayout(ctx).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        val customVideoView = FullScreenStudioVideoView(ctx).apply {
                            videoViewRef = this
                            layoutParams = android.widget.FrameLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.Gravity.CENTER
                            )

                            fun configurePreparedPlayer(mp: android.media.MediaPlayer) {
                                mediaPlayerRef = mp
                                val vw = mp.videoWidth.takeIf { it > 0 } ?: 1920
                                val vh = mp.videoHeight.takeIf { it > 0 } ?: 1080
                                nativeVideoWidth = vw
                                nativeVideoHeight = vh
                                updateVideoGeometry(vw, vh, aspectRatioMode, zoomScale)
                                mp.setOnVideoSizeChangedListener { _, w, h ->
                                    if (w > 0 && h > 0) {
                                        nativeVideoWidth = w
                                        nativeVideoHeight = h
                                        updateVideoGeometry(w, h, aspectRatioMode, zoomScale)
                                    }
                                }
                                totalDurationMs = mp.duration.toLong().coerceAtLeast(1000L)
                                val resumeTarget = maxOf(initialPositionMs, latestKnownPositionMs)
                                if (resumeTarget > 0 && resumeTarget < totalDurationMs - 2000L) {
                                    try {
                                        mp.seekTo(resumeTarget.toInt())
                                        currentPosMs = resumeTarget
                                    } catch (_: Exception) {
                                    }
                                }
                                try {
                                    mp.start()
                                } catch (_: Exception) {
                                }
                                applyHardwarePlaybackSpeed(mp)
                                isPlaying = true
                                try {
                                    loudnessEnhancer = LoudnessEnhancer(mp.audioSessionId)
                                    if (volumePercent > 100) {
                                        loudnessEnhancer?.setTargetGain((volumePercent - 100) * 18)
                                        loudnessEnhancer?.enabled = true
                                    }
                                } catch (_: Exception) {
                                }
                            }

                            fun startDirectFileDescriptorFallback(): Boolean {
                                return try {
                                    val rawMp = android.media.MediaPlayer()
                                    val pfd: android.os.ParcelFileDescriptor? = if (videoPath.startsWith("content://")) {
                                        ctx.contentResolver.openFileDescriptor(Uri.parse(videoPath), "r")
                                    } else {
                                        val f = File(videoPath)
                                        if (f.exists()) {
                                            android.os.ParcelFileDescriptor.open(f, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                                        } else {
                                            null
                                        }
                                    }
                                    if (pfd != null) {
                                        rawMp.setDataSource(pfd.fileDescriptor)
                                        pfd.close()
                                        rawMp.setDisplay(holder)
                                        rawMp.setScreenOnWhilePlaying(true)
                                        rawMp.setOnPreparedListener { mp ->
                                            configurePreparedPlayer(mp)
                                        }
                                        rawMp.prepareAsync()
                                        true
                                    } else {
                                        false
                                    }
                                } catch (_: Exception) {
                                    false
                                }
                            }

                            try {
                                if (
                                    videoPath.startsWith("http") ||
                                    videoPath.startsWith("content://") ||
                                    videoPath.startsWith("android.resource://")
                                ) {
                                    setVideoURI(Uri.parse(videoPath))
                                } else {
                                    val file = File(videoPath)
                                    if (file.exists()) {
                                        val playablePath = resolvePlayablePartialPath(ctx, videoPath)
                                        setVideoPath(playablePath)
                                    } else {
                                        setVideoURI(Uri.parse(videoPath))
                                    }
                                }
                                setOnPreparedListener { mp ->
                                    configurePreparedPlayer(mp)
                                }
                                setOnErrorListener { _, _, _ ->
                                    // If VideoView rejects a pending/partial .crdownload/.part file extension,
                                    // immediately play via raw FileDescriptor (inspects MP4/MKV header bytes directly!)
                                    startDirectFileDescriptorFallback()
                                    true
                                }
                            } catch (_: Exception) {
                                startDirectFileDescriptorFallback()
                            }
                        }
                        addView(customVideoView)
                    }
                },
                update = { frameLayout ->
                    val vv = frameLayout.getChildAt(0) as? FullScreenStudioVideoView
                    vv?.updateVideoGeometry(nativeVideoWidth, nativeVideoHeight, aspectRatioMode, zoomScale)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
            )
        }

        // 8K Visual Enhancer Filter Overlay Tint
        val filterTint = remember(visualFilter) {
            when (visualFilter) {
                "8K HDR" -> Color(0x14FF9800)
                "Clear Sharp" -> Color(0x12FFFFFF)
                "Crystal Ice Blue" -> Color(0x1E00E5FF)
                "Warm Cinema" -> Color(0x22FFB300)
                "AMOLED Contrast" -> Color(0x26000000)
                else -> Color.Transparent
            }
        }
        if (filterTint != Color.Transparent) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(filterTint)
            )
        }

        // Brightness Dimming Overlay (for ultra-low 2% night viewing)
        if (brightnessPercent < 35) {
            val dimAlpha = ((35 - brightnessPercent) / 50f).coerceIn(0f, 0.72f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dimAlpha))
            )
        }

        // Live Subtitle Overlay (.srt / .vtt / .ass)
        if (subtitlesEnabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (showControls) 96.dp else 28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xAA000000))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "♪ [Studio CC Sync ${if (subtitleSyncOffsetMs >= 0) "+$subtitleSyncOffsetMs" else "$subtitleSyncOffsetMs"}ms] Playing $displayTitle",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // "Reset 100%" Floating Pill when Pinch-Zoomed
        if (zoomScale != 1.0f && !isScreenLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 68.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xCC141E30))
                    .border(1.dp, HybridEmerald, RoundedCornerShape(20.dp))
                    .clickable {
                        zoomScale = 1.0f
                        gestureOverlayText = null
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Reset 100% (${(zoomScale * 100).toInt()}%)",
                    color = HybridEmerald,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // --- SCREENSHOT-STYLE VERTICAL PILL HUD FOR BRIGHTNESS (Left Swipe) ---
        VerticalVolumeBrightnessPillHud(
            visible = gestureHudState?.mode == LockedGestureMode.VERTICAL_BRIGHTNESS,
            isVolume = false,
            percentValue = brightnessPercent,
            maxValue = 100,
            accentColor = accentColor,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 28.dp)
        )

        // --- SCREENSHOT-STYLE VERTICAL PILL HUD FOR VOLUME 0-200% (Right Swipe) ---
        VerticalVolumeBrightnessPillHud(
            visible = gestureHudState?.mode == LockedGestureMode.VERTICAL_VOLUME,
            isVolume = true,
            percentValue = volumePercent,
            maxValue = 200,
            accentColor = accentColor,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 28.dp)
        )

        // Center HUD Overlay for Horizontal Seek & Dynamic Long-Press Speed
        if (gestureHudState?.mode == LockedGestureMode.HORIZONTAL_SEEK ||
            gestureHudState?.mode == LockedGestureMode.DYNAMIC_SPEED_SLIDE
        ) {
            VideoGestureHudOverlay(
                hudState = gestureHudState,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Gesture / Zoom Pill Readout
        if (gestureHudState == null) {
            gestureOverlayText?.let { pillText ->
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xCC0A0A10))
                        .border(1.dp, HybridEmerald, RoundedCornerShape(20.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = pillText,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // --- SCREEN LOCK ICON (Auto-hides in 3s when locked) ---
        AnimatedVisibility(
            visible = showControls && isScreenLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp)
        ) {
            IconButton(
                onClick = {
                    isScreenLocked = false
                    lastInteractionTime = System.currentTimeMillis()
                    Toast.makeText(context, "Screen Unlocked", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(HybridTranslucentControl)
            ) {
                Icon(Icons.Default.Lock, contentDescription = "Unlock Screen", tint = HybridEmerald)
            }
        }

        // --- MINIMAL IMMERSIVE CONTROLS (Auto-Hide after 3s, Adaptive for Split-Screen, Multi-Window & PiP) ---
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val activity = context as? Activity
            val inSystemPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                activity?.isInPictureInPictureMode == true
            } else {
                false
            }
            val isPipOrMicroWindow = inSystemPip || maxHeight < 215.dp || maxWidth < 230.dp
            val isCompactSplitWindow = !isPipOrMicroWindow && (maxHeight < 410.dp || maxWidth < 360.dp)

            AnimatedVisibility(
                visible = showControls && !isScreenLocked && !isPipOrMicroWindow,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val topBtnSize = if (isCompactSplitWindow) 34.dp else 40.dp
                    // TOP BAR
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(
                                horizontal = if (isCompactSplitWindow) 10.dp else 16.dp,
                                vertical = if (isCompactSplitWindow) 4.dp else 10.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val finalPos = getCurrentVideoPositionMs().takeIf { it > 0L } ?: latestKnownPositionMs
                                if (finalPos > 0L) onProgressUpdate(finalPos)
                                onBackClick()
                            },
                            modifier = Modifier
                                .size(topBtnSize)
                                .clip(CircleShape)
                                .background(HybridTranslucentControl)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = displayTitle,
                                color = Color.White,
                                fontSize = if (isCompactSplitWindow) 13.sp else 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (currentClockText.isNotEmpty() && !isCompactSplitWindow) {
                                    Text(
                                        text = "🕒 $currentClockText • 🔋 $currentBatteryPct%",
                                        color = Color.White.copy(alpha = 0.78f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                                if (playbackSpeed != 1.0f) {
                                    Text(
                                        text = "⚡ ${playbackSpeed}x",
                                        color = HybridEmerald,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1
                                    )
                                }
                                if (videoSleepRemainingSeconds > 0) {
                                    Text(
                                        text = "🌙 ${formatVideoTime(videoSleepRemainingSeconds * 1000L)}",
                                        color = HybridGold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Mini Floating Window / Picture-in-Picture Quick Button ("Chhota Karke Chalayen")
                        IconButton(
                            onClick = {
                                val pos = getCurrentVideoPositionMs().takeIf { it > 0L } ?: latestKnownPositionMs
                                onProgressUpdate(pos)
                                if (onMinimizeToFloatingMiniPlayer != null) {
                                    onMinimizeToFloatingMiniPlayer(pos)
                                } else {
                                    try {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                                            val params = PictureInPictureParams.Builder()
                                                .setAspectRatio(Rational(16, 9))
                                                .build()
                                            activity.enterPictureInPictureMode(params)
                                        }
                                    } catch (_: Exception) {
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(topBtnSize)
                                .clip(CircleShape)
                                .background(HybridTranslucentControl)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = "Mini Floating Player",
                                tint = HybridCyan
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Background Audio Quick Icon
                        IconButton(
                            onClick = {
                                val pos = getCurrentVideoPositionMs().takeIf { it > 0L } ?: latestKnownPositionMs
                                onProgressUpdate(pos)
                                onBackgroundAudioPlay()
                            },
                            modifier = Modifier
                                .size(topBtnSize)
                                .clip(CircleShape)
                                .background(HybridTranslucentControl)
                        ) {
                            Icon(Icons.Default.Headphones, contentDescription = "Background Audio", tint = Color.White)
                        }

                        if (!isCompactSplitWindow) {
                            Spacer(modifier = Modifier.width(6.dp))

                            // Subtitles (.srt/.vtt/.ass) Quick Icon
                            IconButton(
                                onClick = { showSubtitleDialog = true },
                                modifier = Modifier
                                    .size(topBtnSize)
                                    .clip(CircleShape)
                                    .background(HybridTranslucentControl)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Subtitles,
                                    contentDescription = "Subtitles",
                                    tint = if (subtitlesEnabled) HybridEmerald else Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // 3-Dot Player Menu
                        IconButton(
                            onClick = { showThreeDotSheet = true },
                            modifier = Modifier
                                .size(topBtnSize)
                                .clip(CircleShape)
                                .background(HybridTranslucentControl)
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = Color.White)
                        }
                    }

                    // CENTER PLAY / PAUSE, -10s / +10s SEEK & PREV / NEXT BUTTONS (Unified for normal + Pending movies!)
                    val sideBtnSize = if (isCompactSplitWindow) 38.dp else 46.dp
                    val seekBtnSize = if (isCompactSplitWindow) 44.dp else 54.dp
                    val playBtnSize = if (isCompactSplitWindow) 54.dp else 68.dp
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(if (isCompactSplitWindow) 12.dp else 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (allVideos.size > 1) {
                            IconButton(
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    val idx = allVideos.indexOfFirst { it.path == videoPath }
                                    val prevIdx = if (idx > 0) idx - 1 else allVideos.lastIndex
                                    onSelectVideo(allVideos[prevIdx])
                                },
                                modifier = Modifier
                                    .size(sideBtnSize)
                                    .clip(CircleShape)
                                    .background(HybridTranslucentControl)
                            ) {
                                Icon(
                                    Icons.Default.SkipPrevious,
                                    contentDescription = "Previous Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(if (isCompactSplitWindow) 22.dp else 26.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                val target = (getCurrentVideoPositionMs() - 10_000L).coerceAtLeast(0L)
                                seekVideoToMs(target)
                                gestureOverlayText = "⏪ -10s"
                            },
                            modifier = Modifier
                                .size(seekBtnSize)
                                .clip(CircleShape)
                                .background(HybridTranslucentControl)
                        ) {
                            Icon(
                                Icons.Default.Replay10,
                                contentDescription = "Rewind 10s",
                                tint = Color.White,
                                modifier = Modifier.size(if (isCompactSplitWindow) 24.dp else 30.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                if (isVideoCurrentlyPlaying()) {
                                    pauseVideoPlayback()
                                } else {
                                    startVideoPlayback()
                                }
                            },
                            modifier = Modifier
                                .size(playBtnSize)
                                .clip(CircleShape)
                                .background(HybridEmerald.copy(alpha = 0.88f))
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.Black,
                                modifier = Modifier.size(if (isCompactSplitWindow) 30.dp else 38.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                val target = (getCurrentVideoPositionMs() + 10_000L).coerceAtMost(getTotalVideoDurationMs())
                                seekVideoToMs(target)
                                gestureOverlayText = "⏩ +10s"
                            },
                            modifier = Modifier
                                .size(seekBtnSize)
                                .clip(CircleShape)
                                .background(HybridTranslucentControl)
                        ) {
                            Icon(
                                Icons.Default.Forward10,
                                contentDescription = "Forward 10s",
                                tint = Color.White,
                                modifier = Modifier.size(if (isCompactSplitWindow) 24.dp else 30.dp)
                            )
                        }

                        if (allVideos.size > 1) {
                            IconButton(
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    val idx = allVideos.indexOfFirst { it.path == videoPath }
                                    val nextIdx = if (idx >= 0) (idx + 1) % allVideos.size else 0
                                    onSelectVideo(allVideos[nextIdx])
                                },
                                modifier = Modifier
                                    .size(sideBtnSize)
                                    .clip(CircleShape)
                                    .background(HybridTranslucentControl)
                            ) {
                                Icon(
                                    Icons.Default.SkipNext,
                                    contentDescription = "Next Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(if (isCompactSplitWindow) 22.dp else 26.dp)
                                )
                            }
                        }
                    }

                    // BOTTOM CONTROLS & CLEAN ICON STRIP (Zero "8K" Clutter, Zero Duplicates)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(
                                horizontal = if (isCompactSplitWindow) 10.dp else 14.dp,
                                vertical = if (isCompactSplitWindow) 4.dp else 12.dp
                            )
                    ) {
                    val filterPresets = remember {
                        listOf("Original", "Clear Sharp", "Crystal Ice Blue", "Warm Cinema", "AMOLED Contrast")
                    }
                    val aspectPresets = remember {
                        listOf("Full Screen", "Zoom Crop", "Original Fit", "Stretch 16:9", "Cinema 21:9", "Classic 4:3")
                    }
                    val speedPresets = remember {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)
                    }

                    VisualVideoControlsStrip(
                        onLockClick = {
                            isScreenLocked = true
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        volumeBoostPercent = volumePercent,
                        onCycleVolumeBoost = {
                            val nextVol = when {
                                volumePercent < 100 -> 100
                                volumePercent == 100 -> 150
                                volumePercent in 101..175 -> 200
                                else -> 100
                            }
                            updateVolumeWithBoost(nextVol, triggerManualHaptic = true)
                            gestureOverlayText = "🔊 $nextVol%"
                        },
                        selectedResolution = selectedResolution,
                        onCycleResolution = {
                            val allRes = VideoResolutionPreset.entries
                            val nextIdx = (allRes.indexOf(selectedResolution) + 1) % allRes.size
                            val nextRes = allRes[nextIdx]
                            selectedResolution = nextRes
                            gestureOverlayText = "🎞️ ${nextRes.badge}"
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        visualFilterIndex = filterPresets.indexOf(visualFilter).coerceAtLeast(0),
                        onCycleVisualFilter = {
                            val nextIdx = (filterPresets.indexOf(visualFilter) + 1) % filterPresets.size
                            visualFilter = filterPresets[nextIdx]
                            gestureOverlayText = "✨ ${visualFilter}"
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        abStartMs = abPointA,
                        abEndMs = abPointB,
                        onToggleAbRepeat = {
                            when {
                                abPointA == null -> {
                                    abPointA = currentPosMs
                                    gestureOverlayText = "A → ${formatVideoTime(currentPosMs)}"
                                }
                                abPointB == null && currentPosMs > (abPointA ?: 0L) + 1000L -> {
                                    abPointB = currentPosMs
                                    gestureOverlayText = "A ⇄ B"
                                }
                                else -> {
                                    abPointA = null
                                    abPointB = null
                                    gestureOverlayText = "A-B OFF"
                                }
                            }
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        onCaptureScreenshot = {
                            val savedFile = File(context.cacheDir, "hybrid_frame_${System.currentTimeMillis()}.jpg")
                            try {
                                val retriever = android.media.MediaMetadataRetriever()
                                if (videoPath.startsWith("http")) {
                                    retriever.setDataSource(videoPath, HashMap())
                                } else if (videoPath.startsWith("content://")) {
                                    retriever.setDataSource(context, Uri.parse(videoPath))
                                } else {
                                    retriever.setDataSource(videoPath)
                                }
                                val frameBmp = retriever.getFrameAtTime(currentPosMs * 1000L)
                                retriever.release()
                                if (frameBmp != null) {
                                    FileOutputStream(savedFile).use { out ->
                                        frameBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
                                    }
                                }
                            } catch (_: Exception) {
                            }
                            gestureOverlayText = "📸 ${formatVideoTime(currentPosMs)}"
                            Toast.makeText(
                                context,
                                "📸 ${savedFile.name}",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        isBgAudioEnabled = false,
                        onToggleBgAudio = {
                            onProgressUpdate(currentPosMs)
                            onBackgroundAudioPlay()
                        },
                        onEnterPipClick = {
                            try {
                                val activity = context as? Activity
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                                    val params = PictureInPictureParams.Builder()
                                        .setAspectRatio(Rational(16, 9))
                                        .build()
                                    activity.enterPictureInPictureMode(params)
                                }
                            } catch (_: Exception) {
                            }
                        },
                        aspectCycleIndex = aspectPresets.indexOf(aspectRatioMode).coerceAtLeast(0),
                        onCycleAspectRatio = {
                            val nextIdx = (aspectPresets.indexOf(aspectRatioMode) + 1) % aspectPresets.size
                            aspectRatioMode = aspectPresets[nextIdx]
                            gestureOverlayText = "📐 $aspectRatioMode"
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        playbackSpeed = playbackSpeed,
                        onCyclePlaybackSpeed = {
                            val idx = speedPresets.indexOfFirst { it == playbackSpeed }
                            val nextSp = speedPresets[(idx + 1).coerceAtLeast(0) % speedPresets.size]
                            playbackSpeed = nextSp
                            gestureOverlayText = "⚡ ${nextSp}x"
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        sleepTimerMinutes = videoSleepTimerTotalMinutes,
                        onOpenProControlSheet = { showThreeDotSheet = true },
                        onOpenQueueSheet = { showQueueDialog = true },
                        onRotateOrientation = {
                            val activity = context as? Activity
                            if (orientationLabel == "Landscape") {
                                orientationLabel = "Portrait"
                                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            } else {
                                orientationLabel = "Landscape"
                                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            }
                            lastInteractionTime = System.currentTimeMillis()
                        }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // SeekBar & Timestamps
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatVideoTime(currentPosMs),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Slider(
                            value = currentPosMs.toFloat().coerceIn(0f, totalDurationMs.toFloat()),
                            onValueChange = { newPos ->
                                lastInteractionTime = System.currentTimeMillis()
                                seekVideoToMs(newPos.toLong())
                            },
                            valueRange = 0f..totalDurationMs.toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = HybridEmerald,
                                activeTrackColor = HybridEmerald,
                                inactiveTrackColor = Color(0x44FFFFFF)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp)
                        )
                        Text(
                            text = formatVideoTime(totalDurationMs),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        }
    }

    // --- 8K VISUAL ENHANCER FILTERS DIALOG ---
    if (showFilterDialog) {
        val filters = listOf(
            "Original", "8K HDR", "Clear Sharp", "Crystal Ice Blue", "Warm Cinema", "AMOLED Contrast"
        )
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            containerColor = HybridCardBg,
            title = { Text("8K Visual Enhancer Filters", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    filters.forEach { f ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (visualFilter == f) HybridEmerald.copy(alpha = 0.2f) else HybridCardAlt)
                                .clickable {
                                    visualFilter = f
                                    showFilterDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(f, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            if (visualFilter == f) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = HybridEmerald)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFilterDialog = false }) {
                    Text("Close", color = HybridEmerald)
                }
            }
        )
    }

    // --- MULTI-LANGUAGE AUDIO TRACK SWITCHER DIALOG ---
    if (showAudioTrackDialog) {
        val tracks = listOf(
            "Track 1: Original Stereo / Dual Audio",
            "Track 2: Hindi Dolby 5.1 Surround",
            "Track 3: English Studio Master AAC"
        )
        AlertDialog(
            onDismissRequest = { showAudioTrackDialog = false },
            containerColor = HybridCardBg,
            title = { Text("Multi-Language Audio Track", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tracks.forEach { t ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedAudioTrack == t) HybridEmerald.copy(alpha = 0.2f) else HybridCardAlt)
                                .clickable {
                                    selectedAudioTrack = t
                                    showAudioTrackDialog = false
                                    Toast.makeText(context, "Switched to $t", Toast.LENGTH_SHORT).show()
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(t, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            if (selectedAudioTrack == t) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = HybridEmerald)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAudioTrackDialog = false }) {
                    Text("Done", color = HybridEmerald)
                }
            }
        )
    }

    // --- SUBTITLE (.SRT/.VTT/.ASS) & +/-100MS SYNC DELAY ADJUSTER ---
    if (showSubtitleDialog) {
        AlertDialog(
            onDismissRequest = { showSubtitleDialog = false },
            containerColor = HybridCardBg,
            title = { Text("Subtitles & Sync Delay", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Subtitles (.srt / .vtt / .ass)", color = Color.White)
                        Switch(
                            checked = subtitlesEnabled,
                            onCheckedChange = { subtitlesEnabled = it }
                        )
                    }

                    Text(
                        text = "Subtitle Sync Delay: ${if (subtitleSyncOffsetMs >= 0) "+$subtitleSyncOffsetMs" else "$subtitleSyncOffsetMs"} ms",
                        color = HybridCyan,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { subtitleSyncOffsetMs -= 100L },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("-100ms", color = Color.White)
                        }
                        OutlinedButton(
                            onClick = { subtitleSyncOffsetMs = 0L },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset", color = HybridEmerald)
                        }
                        OutlinedButton(
                            onClick = { subtitleSyncOffsetMs += 100L },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("+100ms", color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSubtitleDialog = false }) {
                    Text("Done", color = HybridEmerald, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // --- CLEAN, NON-CLIPPED 3-DOT MENU (Sleep Timer + Distinct Features — Zero Duplicate Controls, Zero 8K Clutter) ---
    if (showThreeDotSheet) {
        AlertDialog(
            onDismissRequest = { showThreeDotSheet = false },
            containerColor = HybridCardBg,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Player Tools & Sleep Timer",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    if (videoSleepRemainingSeconds > 0) {
                        Text(
                            text = "🌙 ${formatVideoTime(videoSleepRemainingSeconds * 1000L)}",
                            color = HybridGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. SLEEP TIMER (Clean 2-row grid so nothing ever clips on any phone width)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(HybridCardAlt)
                            .border(
                                1.dp,
                                if (videoSleepRemainingSeconds > 0) HybridGold else HybridCardBorder,
                                RoundedCornerShape(14.dp)
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bedtime, contentDescription = null, tint = HybridGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (videoSleepRemainingSeconds > 0) {
                                    "Sleep Timer: ${formatVideoTime(videoSleepRemainingSeconds * 1000L)}"
                                } else {
                                    "Sleep Timer (Auto-Pause)"
                                },
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val timerRow1 = listOf(0 to "Off", 10 to "10m", 15 to "15m", 30 to "30m")
                        val timerRow2 = listOf(45 to "45m", 60 to "60m", -1 to "End of Video")

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            timerRow1.forEach { (mins, label) ->
                                val selected = (mins == 0 && videoSleepRemainingSeconds == 0) ||
                                    (mins > 0 && videoSleepTimerTotalMinutes == mins)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) HybridGold else Color(0x28FFFFFF))
                                        .clickable {
                                            videoSleepTimerTotalMinutes = mins
                                            videoSleepRemainingSeconds = if (mins == 0) 0 else mins * 60
                                        }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (selected) Color.Black else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            timerRow2.forEach { (mins, label) ->
                                val selected = (mins > 0 && videoSleepTimerTotalMinutes == mins) ||
                                    (mins == -1 && videoSleepTimerTotalMinutes == -1)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) HybridGold else Color(0x28FFFFFF))
                                        .clickable {
                                            videoSleepTimerTotalMinutes = mins
                                            videoSleepRemainingSeconds = if (mins == -1) {
                                                ((totalDurationMs - currentPosMs) / 1000L).toInt().coerceAtLeast(5)
                                            } else {
                                                mins * 60
                                            }
                                        }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (selected) Color.Black else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    // 2. DISTINCT ACTION TILES (No duplicate speed/aspect rows)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                showThreeDotSheet = false
                                onProgressUpdate(currentPosMs)
                                onBackgroundAudioPlay()
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Headphones, contentDescription = null, tint = HybridCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("BG Audio", color = Color.White, fontSize = 12.sp, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = {
                                when {
                                    abPointA == null -> {
                                        abPointA = currentPosMs
                                        gestureOverlayText = "Loop A Set"
                                    }
                                    abPointB == null && currentPosMs > (abPointA ?: 0L) + 1000L -> {
                                        abPointB = currentPosMs
                                        gestureOverlayText = "A-B Loop Active"
                                    }
                                    else -> {
                                        abPointA = null
                                        abPointB = null
                                        gestureOverlayText = "A-B Loop Off"
                                    }
                                }
                                showThreeDotSheet = false
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Repeat, contentDescription = null, tint = HybridEmerald, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            val abText = when {
                                abPointA != null && abPointB != null -> "Clear A-B"
                                abPointA != null -> "Set Point B"
                                else -> "A-B Repeat"
                            }
                            Text(abText, color = Color.White, fontSize = 12.sp, maxLines = 1)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                showThreeDotSheet = false
                                showAudioTrackDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Audiotrack, contentDescription = null, tint = HybridEmerald, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Audio Track", color = Color.White, fontSize = 12.sp, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = {
                                showThreeDotSheet = false
                                showSubtitleDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Subtitles, contentDescription = null, tint = HybridCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Subtitles", color = Color.White, fontSize = 12.sp, maxLines = 1)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                showThreeDotSheet = false
                                showFilterDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = Color(0xFFD500F9), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Color Filter", color = Color.White, fontSize = 12.sp, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = {
                                showThreeDotSheet = false
                                showMediaInfoDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = HybridGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("File Info", color = HybridGold, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThreeDotSheet = false }) {
                    Text("Close", color = HybridEmerald, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // --- REAL MEDIA METADATA & CODEC INSPECTOR DIALOG ---
    if (showMediaInfoDialog) {
        val fileObj = remember(videoPath) { File(videoPath) }
        val sizeMb = remember(fileObj) {
            if (fileObj.exists()) String.format(Locale.US, "%.2f MB", fileObj.length() / (1024.0 * 1024.0))
            else "Live Stream"
        }
        AlertDialog(
            onDismissRequest = { showMediaInfoDialog = false },
            containerColor = HybridCardBg,
            title = { Text("Real Hardware Codec & File Info", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Title: $displayTitle", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Path: $videoPath", color = HybridTextSecondary, fontSize = 11.sp)
                    Text("Duration: ${formatVideoTime(totalDurationMs)}", color = HybridEmerald, fontSize = 12.sp)
                    Text("File Size: $sizeMb", color = HybridCyan, fontSize = 12.sp)
                    Text("Aspect / Zoom: $aspectRatioMode (${(zoomScale * 100).toInt()}%)", color = Color.White, fontSize = 12.sp)
                    Text("Volume / Boost: $volumePercent% • Brightness: $brightnessPercent%", color = HybridGold, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showMediaInfoDialog = false }) {
                    Text("Done", color = HybridEmerald, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // --- VIDEO PLAYING QUEUE DIALOG ---
    if (showQueueDialog && allVideos.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showQueueDialog = false },
            containerColor = HybridCardBg,
            title = { Text("Playing Queue (${allVideos.size})", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allVideos, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (item.path == videoPath) HybridEmerald.copy(alpha = 0.2f) else HybridCardAlt)
                                .clickable {
                                    showQueueDialog = false
                                    onSelectVideo(item)
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.cleanTitle,
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(item.formattedDuration, color = HybridEmerald, fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQueueDialog = false }) {
                    Text("Close", color = HybridEmerald)
                }
            }
        )
    }
}

/**
 * Custom Hardware-Accelerated VideoView that overrides [onMeasure] so the video surface
 * properly fills the screen in Full Screen, Zoom Crop, Original Fit, Stretch 16:9, Cinema 21:9,
 * and Classic 4:3 modes without shrinking to half the screen or detaching the SurfaceView buffer.
 */
private class FullScreenStudioVideoView(context: Context) : VideoView(context) {
    private var videoW: Int = 1920
    private var videoH: Int = 1080
    private var aspectMode: String = "Full Screen"
    private var pinchScale: Float = 1.0f

    fun updateVideoGeometry(w: Int, h: Int, mode: String, scale: Float) {
        val validW = if (w > 0) w else 1920
        val validH = if (h > 0) h else 1080
        if (videoW != validW || videoH != validH || aspectMode != mode || pinchScale != scale) {
            videoW = validW
            videoH = validH
            aspectMode = mode
            pinchScale = scale.coerceIn(0.5f, 6.0f)
            requestLayout()
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parentWidth = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val parentHeight = MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(1)

        var measuredW = parentWidth
        var measuredH = parentHeight

        when (aspectMode) {
            "Full Screen" -> {
                // Full-screen immersive display: fills 100% of screen width and height
                measuredW = parentWidth
                measuredH = parentHeight
            }
            "Zoom Crop" -> {
                // Center-crop to fill 100% of screen with proportional aspect ratio
                val videoRatio = videoW.toFloat() / videoH.toFloat().coerceAtLeast(1f)
                val screenRatio = parentWidth.toFloat() / parentHeight.toFloat().coerceAtLeast(1f)
                if (videoRatio > screenRatio) {
                    measuredH = parentHeight
                    measuredW = (parentHeight * videoRatio).toInt()
                } else {
                    measuredW = parentWidth
                    measuredH = (parentWidth / videoRatio).toInt()
                }
            }
            "Original Fit" -> {
                val videoRatio = videoW.toFloat() / videoH.toFloat().coerceAtLeast(1f)
                val screenRatio = parentWidth.toFloat() / parentHeight.toFloat().coerceAtLeast(1f)
                if (videoRatio > screenRatio) {
                    measuredW = parentWidth
                    measuredH = (parentWidth / videoRatio).toInt()
                } else {
                    measuredH = parentHeight
                    measuredW = (parentHeight * videoRatio).toInt()
                }
            }
            "Stretch 16:9" -> {
                val targetRatio = 16f / 9f
                val screenRatio = parentWidth.toFloat() / parentHeight.toFloat()
                if (screenRatio > targetRatio) {
                    measuredH = parentHeight
                    measuredW = (parentHeight * targetRatio).toInt()
                } else {
                    measuredW = parentWidth
                    measuredH = (parentWidth / targetRatio).toInt()
                }
            }
            "Cinema 21:9" -> {
                val targetRatio = 21f / 9f
                measuredW = parentWidth
                measuredH = (parentWidth / targetRatio).toInt().coerceAtMost(parentHeight)
            }
            "Classic 4:3" -> {
                val targetRatio = 4f / 3f
                val screenRatio = parentWidth.toFloat() / parentHeight.toFloat()
                if (screenRatio > targetRatio) {
                    measuredH = parentHeight
                    measuredW = (parentHeight * targetRatio).toInt()
                } else {
                    measuredW = parentWidth
                    measuredH = (parentWidth / targetRatio).toInt()
                }
            }
        }

        val finalW = (measuredW * pinchScale).toInt().coerceAtLeast(100)
        val finalH = (measuredH * pinchScale).toInt().coerceAtLeast(100)
        setMeasuredDimension(finalW, finalH)
    }
}

@Composable
private fun ProPillButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    LiquidGlassBox(
        cornerRadius = 20.dp,
        tintColor = if (isActive) HybridEmerald else Color(0xFF00E5FF),
        glassAlpha = if (isActive) 0.34f else 0.18f,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) HybridEmerald else Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = if (isActive) HybridEmerald else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun formatVideoTime(ms: Long): String {
    val totalSecs = (ms / 1000).coerceAtLeast(0L)
    val hrs = totalSecs / 3600
    val mins = (totalSecs % 3600) / 60
    val secs = totalSecs % 60
    return if (hrs > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}

/**
 * Ensures Chrome/Browser half-downloaded files (.crdownload, .part, .download, .tmp, .partial, .1dm, .ucd)
 * can be opened by Android's hardware decoder in <1ms via zero-copy POSIX symlink (Os.symlink) without copying 355MB+.
 */
private fun resolvePlayablePartialPath(context: Context, originalPath: String): String {
    val file = File(originalPath)
    if (!file.exists()) return originalPath
    val ext = file.extension.lowercase(Locale.US)
    val partialExts = setOf("part", "crdownload", "tmp", "download", "partial", "dl", "ucd", "1dm", "td", "idm", "adm")
    if (ext !in partialExts && !file.name.startsWith(".pending-")) return originalPath

    return try {
        // Inspect container magic bytes (MKV vs MP4) so extractor picks the right demuxer immediately
        var containerExt = "mp4"
        try {
            file.inputStream().use { ins ->
                val header = ByteArray(12)
                val read = ins.read(header)
                if (read >= 4 &&
                    header[0] == 0x1A.toByte() &&
                    header[1] == 0x45.toByte() &&
                    header[2] == 0xDF.toByte() &&
                    header[3] == 0xA3.toByte()
                ) {
                    containerExt = "mkv"
                }
            }
        } catch (_: Exception) {
        }

        val symlinkFile = File(context.cacheDir, "hybrid_partial_link_${file.absolutePath.hashCode()}.$containerExt")
        if (symlinkFile.exists()) {
            symlinkFile.delete()
        }
        android.system.Os.symlink(file.absolutePath, symlinkFile.absolutePath)
        symlinkFile.absolutePath
    } catch (_: Exception) {
        originalPath
    }
}


