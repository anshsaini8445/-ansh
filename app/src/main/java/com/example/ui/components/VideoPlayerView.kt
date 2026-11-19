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
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

private const val CONTROLS_AUTO_HIDE_DELAY_MS = 10_000L

private data class VideoSubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

private fun parseSrtOrVttSubtitles(raw: String): List<VideoSubtitleCue> {
    val cues = mutableListOf<VideoSubtitleCue>()
    val timeRegex = Regex("""(?:(\d{1,2}):)?(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(?:(\d{1,2}):)?(\d{2}):(\d{2})[,.](\d{3})""")
    fun toMs(h: String?, m: String, s: String, ms: String): Long {
        val hh = h?.toLongOrNull() ?: 0L
        val mm = m.toLongOrNull() ?: 0L
        val ss = s.toLongOrNull() ?: 0L
        val milli = ms.toLongOrNull() ?: 0L
        return ((hh * 3600L + mm * 60L + ss) * 1000L) + milli
    }
    val blocks = raw.replace("\r\n", "\n").split(Regex("""\n\s*\n"""))
    for (block in blocks) {
        val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val timeLineIdx = lines.indexOfFirst { timeRegex.containsMatchIn(it) }
        if (timeLineIdx < 0) continue
        val match = timeRegex.find(lines[timeLineIdx]) ?: continue
        val startMs = toMs(match.groupValues[1].ifEmpty { null }, match.groupValues[2], match.groupValues[3], match.groupValues[4])
        val endMs = toMs(match.groupValues[5].ifEmpty { null }, match.groupValues[6], match.groupValues[7], match.groupValues[8])
        val textLines = lines.drop(timeLineIdx + 1)
            .joinToString("\n")
            .replace(Regex("""<[^>]+>"""), "")
            .trim()
        if (textLines.isNotEmpty() && endMs >= startMs) {
            cues.add(VideoSubtitleCue(startMs, endMs, textLines))
        }
    }
    return cues
}

private fun saveSnapshotBitmapToGallery(
    context: Context,
    videoPath: String,
    posMs: Long,
    title: String
): Boolean {
    return try {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            when {
                videoPath.startsWith("http://", true) || videoPath.startsWith("https://", true) ->
                    retriever.setDataSource(videoPath, HashMap())
                videoPath.startsWith("content://") ->
                    retriever.setDataSource(context, Uri.parse(videoPath))
                else -> {
                    try {
                        retriever.setDataSource(videoPath)
                    } catch (_: Exception) {
                        val f = File(videoPath)
                        java.io.FileInputStream(f).use { fis ->
                            retriever.setDataSource(fis.fd, 0L, f.length())
                        }
                    }
                }
            }
            val frameBmp = retriever.getFrameAtTime(posMs.coerceAtLeast(0L) * 1000L)
                ?: retriever.getFrameAtTime(0L)
                ?: return false
            val safeName = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(24).ifBlank { "HybridFrame" }
            val fileName = "${safeName}_${System.currentTimeMillis()}.jpg"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_PICTURES}/HybridPlayer")
                    put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    frameBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
                } ?: return false
                values.clear()
                values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                true
            } else {
                val picDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
                val appDir = File(picDir, "HybridPlayer").apply { if (!exists()) mkdirs() }
                val savedFile = File(appDir, fileName)
                FileOutputStream(savedFile).use { out ->
                    frameBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
                }
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(savedFile.absolutePath),
                    arrayOf("image/jpeg"),
                    null
                )
                savedFile.exists() && savedFile.length() > 0L
            }
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    } catch (_: Exception) {
        false
    }
}

@Composable
fun VideoPlayerView(
    videoPath: String,
    initialPositionMs: Long = 0L,
    initialSpeed: Float = 1.0f,
    onProgressUpdate: (Long) -> Unit,
    onBackClick: () -> Unit,
    accentColor: Color = HybridEmerald,
    videoTitle: String = "",
    allVideos: List<MediaItem> = emptyList(),
    onSelectVideo: (MediaItem) -> Unit = {},
    onBackgroundAudioPlay: () -> Unit = {},
    onBackgroundAudioPlayWithPosition: ((Long, Float) -> Unit)? = null,
    onMinimizeToFloatingMiniPlayer: ((Long) -> Unit)? = null,
    onLockInVault: (() -> Unit)? = null,
    onPlayingStateChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val hostView = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val prefs = remember { context.getSharedPreferences("HYBRID_PLAYER_PREFS", Context.MODE_PRIVATE) }

    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var rawFallbackMediaPlayerRef by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var loudnessEnhancer by remember { mutableStateOf<LoudnessEnhancer?>(null) }

    var isPlaying by remember { mutableStateOf(true) }
    LaunchedEffect(isPlaying) {
        onPlayingStateChanged(isPlaying)
    }
    DisposableEffect(Unit) {
        onDispose { onPlayingStateChanged(false) }
    }
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
    var volumePercent by remember {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        mutableStateOf(((curVol * 100f) / maxVol).toInt().coerceIn(0, 100))
    } // 0% to 200% (Hardware LoudnessEnhancer above 100%)
    var zoomScale by remember { mutableStateOf(1.0f) } // 0.5f (50%) to 6.0f (600%)
    var playbackSpeed by remember(videoPath) { mutableStateOf(initialSpeed.coerceIn(0.25f, 4.0f)) }
    var isLongPressSpeedActive by remember { mutableStateOf(false) }
    var gestureHudState by remember { mutableStateOf<GestureHudState?>(null) }
    var gestureOverlayText by remember { mutableStateOf<String?>(null) }

    // Universal Resolution Engine (8K, 4K, 2K, 1080p, 720p, 480p, 360p, 240p, Auto)
    var selectedResolution by remember { mutableStateOf(VideoResolutionPreset.UHD_8K) }

    // Extra Pro Tools State
    var visualFilter by remember { mutableStateOf("Original") }
    var selectedAudioTrackIndex by remember(videoPath) { mutableStateOf(-1) }
    var subtitlesEnabled by remember { mutableStateOf(false) }
    var subtitleSyncOffsetMs by remember { mutableStateOf(0L) }
    var loadedSubtitleCues by remember(videoPath) { mutableStateOf<List<VideoSubtitleCue>>(emptyList()) }
    var loadedSubtitleFileName by remember(videoPath) { mutableStateOf<String?>(null) }
    val subtitlePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                val parsed = try {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                        parseSrtOrVttSubtitles(reader.readText())
                    } ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
                withContext(Dispatchers.Main) {
                    if (parsed.isNotEmpty()) {
                        loadedSubtitleCues = parsed
                        loadedSubtitleFileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Subtitle File"
                        subtitlesEnabled = true
                        Toast.makeText(context, "Loaded ${parsed.size} subtitle lines", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "No valid .SRT / .VTT cues found in file", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
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
    var aspectRatioMode by remember { mutableStateOf("Original Fit") }
    val currentConfig = androidx.compose.ui.platform.LocalConfiguration.current
    val isCurrentlyLandscape = currentConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var orientationLabel by remember(isCurrentlyLandscape) {
        mutableStateOf(if (isCurrentlyLandscape) "Landscape" else "Portrait")
    }
    var nativeVideoWidth by remember { mutableStateOf(1920) }
    var nativeVideoHeight by remember { mutableStateOf(1080) }
    var isSoftwareFallbackActive by remember(videoPath) { mutableStateOf(false) }
    var softwareFallbackBitmap by remember(videoPath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    val isPartialDownloadStream = remember(videoPath) {
        val f = if (!videoPath.startsWith("content://") && !videoPath.startsWith("http", true)) File(videoPath) else null
        com.example.data.scanner.DeepMediaScanner.isFileNamePartialDownload(videoPath, f)
    }
    var partialPlayableDurationMs by remember(videoPath) { mutableStateOf(0L) }
    var partialLiveBytes by remember(videoPath) { mutableStateOf(0L) }
    LaunchedEffect(videoPath) {
        if (!videoPath.startsWith("content://") && !videoPath.startsWith("http", true)) {
            partialLiveBytes = withContext(Dispatchers.IO) {
                runCatching { File(videoPath).length() }.getOrDefault(0L)
            }
        }
    }
    val isNetworkStream = remember(videoPath) {
        videoPath.startsWith("http://", ignoreCase = true) ||
            videoPath.startsWith("https://", ignoreCase = true) ||
            videoPath.startsWith("rtsp://", ignoreCase = true) ||
            videoPath.startsWith("rtmp://", ignoreCase = true)
    }
    var isStreamBuffering by remember(videoPath) { mutableStateOf(isNetworkStream) }
    var streamBufferPercent by remember(videoPath) { mutableStateOf(0) }
    val streamNetworkHeaders = remember {
        mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36",
            "Accept" to "*/*",
            "Connection" to "keep-alive"
        )
    }

    // Pro Feature Toggles (Auto-Play Next, Shuffle, Loop 1, Night Eye, Mirror Flip, Mute, Favorite, Speed Dialog)
    var isAutoPlayNextEnabled by remember {
        mutableStateOf(prefs.getBoolean("auto_play_next_video", true))
    }
    var isShuffleActive by remember { mutableStateOf(false) }
    var isLoopSingleActive by remember { mutableStateOf(false) }
    var isNightEyeActive by remember { mutableStateOf(false) }
    var isMirrorFlipped by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var volumeBeforeMute by remember { mutableStateOf(100) }
    var isFavorite by remember(videoPath) {
        mutableStateOf(prefs.getBoolean("fav_video_${videoPath.hashCode()}", false))
    }
    var isHardwareDecoderActive by remember { mutableStateOf(true) }
    var showSpeedDialog by remember { mutableStateOf(false) }

    fun handleVideoCompletion() {
        if (isLoopSingleActive) {
            videoViewRef?.seekTo(0)
            mediaPlayerRef?.seekTo(0)
            currentPosMs = 0L
            videoViewRef?.start()
            mediaPlayerRef?.start()
            isPlaying = true
        } else if (allVideos.isNotEmpty() && isAutoPlayNextEnabled) {
            val idx = allVideos.indexOfFirst { it.path == videoPath }
            val nextIdx = if (isShuffleActive) {
                val candidates = (0 until allVideos.size).filter { it != idx }
                if (candidates.isNotEmpty()) candidates.random() else 0
            } else {
                if (idx >= 0) (idx + 1) % allVideos.size else 0
            }
            onSelectVideo(allVideos[nextIdx])
        } else {
            isPlaying = false
        }
    }

    fun toggleFavorite() {
        val next = !isFavorite
        isFavorite = next
        prefs.edit().putBoolean("fav_video_${videoPath.hashCode()}", next).apply()
        gestureOverlayText = if (next) "❤️ Added to Favorites" else "💔 Removed from Favorites"
        Toast.makeText(context, gestureOverlayText, Toast.LENGTH_SHORT).show()
    }

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

    fun toggleMute() {
        if (isMuted) {
            isMuted = false
            updateVolumeWithBoost(volumeBeforeMute)
            gestureOverlayText = "🔊 $volumeBeforeMute%"
        } else {
            volumeBeforeMute = volumePercent
            isMuted = true
            updateVolumeWithBoost(0)
            gestureOverlayText = "🔇 Muted"
        }
    }

    fun applyHardwarePlaybackSpeed(targetMp: android.media.MediaPlayer? = mediaPlayerRef) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            targetMp?.let { mp ->
                try {
                    val safeSpeed = playbackSpeed.coerceIn(0.25f, 8.0f)
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

    // Block accidental Back button exit when Screen Lock is enabled
    androidx.activity.compose.BackHandler(enabled = isScreenLocked) {
        showControls = true
        lastInteractionTime = System.currentTimeMillis()
        Toast.makeText(context, "🔒 Screen Locked — Tap the Lock icon to unlock", Toast.LENGTH_SHORT).show()
    }

    // Auto-hide BOTH system status bar AND 3-button navigation bar during video playback & Screen Lock
    DisposableEffect(showControls, isScreenLocked, isCurrentlyLandscape) {
        fun applyImmersiveSystemBars(hideAllBars: Boolean) {
            try {
                val activity = context as? Activity
                val window = activity?.window ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                            } else {
                                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                            }
                    }
                }
                val controller = androidx.core.view.WindowCompat.getInsetsController(window, hostView)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
                controller.systemBarsBehavior =
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (hideAllBars) {
                    controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        @Suppress("DEPRECATION")
                        window.decorView.systemUiVisibility = (
                            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                            )
                    }
                } else {
                    // Keep bottom 3-button system navigation bar hidden even while controls show
                    controller.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        @Suppress("DEPRECATION")
                        window.decorView.systemUiVisibility = (
                            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            )
                    }
                }
            } catch (_: Exception) {
            }
        }

        applyImmersiveSystemBars(hideAllBars = !showControls || isScreenLocked)

        onDispose { }
    }

    // Keep screen awake while video is actively playing (covers software fallback & standard mode)
    DisposableEffect(isPlaying) {
        val activity = context as? Activity
        try {
            if (isPlaying) {
                activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        } catch (_: Exception) {
        }
        onDispose {
            try {
                activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } catch (_: Exception) {
            }
        }
    }

    // Save original window brightness and restore system bars & brightness when exiting VideoPlayerView
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val originalWindowBrightness = try {
            activity?.window?.attributes?.screenBrightness
                ?: android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        } catch (_: Exception) {
            android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        onDispose {
            try {
                val window = activity?.window
                if (window != null) {
                    val lp = window.attributes
                    lp.screenBrightness = originalWindowBrightness
                    window.attributes = lp
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    val controller = androidx.core.view.WindowCompat.getInsetsController(window, hostView)
                    controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        @Suppress("DEPRECATION")
                        window.decorView.systemUiVisibility = (
                            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            )
                    }
                    window.decorView.requestApplyInsets()
                }
            } catch (_: Exception) {
            }
        }
    }

    val isAnyOverlayDialogOpen = showThreeDotSheet ||
        showAudioTrackDialog ||
        showSubtitleDialog ||
        showFilterDialog ||
        showQueueDialog ||
        showMediaInfoDialog ||
        showSpeedDialog

    // 10-Second Auto-Hide Controls Timer (pauses while 3-Dot Pro Tools or any tool dialog is open)
    LaunchedEffect(showControls, lastInteractionTime, isPlaying, isScreenLocked, isAnyOverlayDialogOpen) {
        if (showControls && (isPlaying || isScreenLocked) && !isAnyOverlayDialogOpen) {
            delay(CONTROLS_AUTO_HIDE_DELAY_MS)
            if (System.currentTimeMillis() - lastInteractionTime >= CONTROLS_AUTO_HIDE_DELAY_MS && !isAnyOverlayDialogOpen) {
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
                if (isPlaying) onNotificationPause() else onNotificationPlay()
            }

            override fun onNotificationPlay() {
                val vv = videoViewRef
                val raw = rawFallbackMediaPlayerRef
                try {
                    if (isSoftwareFallbackActive) {
                        isPlaying = true
                    } else if (raw != null) {
                        if (!raw.isPlaying) raw.start()
                        isPlaying = true
                    } else if (vv != null) {
                        if (!vv.isPlaying) vv.start()
                        isPlaying = true
                    }
                    postComposeVideoNotification(isPlaying, currentPosMs)
                } catch (_: Exception) {
                }
            }

            override fun onNotificationPause() {
                val vv = videoViewRef
                val raw = rawFallbackMediaPlayerRef
                try {
                    if (isSoftwareFallbackActive) {
                        isPlaying = false
                    } else if (raw != null) {
                        if (raw.isPlaying) raw.pause()
                        isPlaying = false
                    } else if (vv != null) {
                        if (vv.isPlaying) vv.pause()
                        isPlaying = false
                    }
                    postComposeVideoNotification(isPlaying, currentPosMs)
                } catch (_: Exception) {
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
            // If we are still the active delegate (not handed off to Background Audio), dismiss the orphan video notification!
            if (MediaNotificationHelper.getControlDelegate() === delegate) {
                MediaNotificationHelper.setControlDelegate(null)
                MediaNotificationHelper.cancelNotification(context)
            }
            if (currentPosMs > 0L) {
                onProgressUpdate(currentPosMs)
            }
            try {
                loudnessEnhancer?.release()
            } catch (_: Exception) {
            }
            loudnessEnhancer = null
            try {
                rawFallbackMediaPlayerRef?.let { raw ->
                    if (raw.isPlaying) raw.stop()
                    raw.release()
                }
            } catch (_: Exception) {
            }
            rawFallbackMediaPlayerRef = null
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
        if (isSoftwareFallbackActive) return isPlaying
        return try {
            videoViewRef?.isPlaying == true || mediaPlayerRef?.isPlaying == true
        } catch (_: Exception) {
            false
        }
    }

    fun getCurrentVideoPositionMs(): Long {
        if (isSoftwareFallbackActive) return currentPosMs
        return try {
            val vvPos = videoViewRef?.currentPosition?.toLong() ?: 0L
            if (vvPos > 0L) vvPos else (mediaPlayerRef?.currentPosition?.toLong() ?: currentPosMs)
        } catch (_: Exception) {
            currentPosMs
        }
    }

    fun getTotalVideoDurationMs(): Long {
        if (isPartialDownloadStream && partialPlayableDurationMs >= 1000L) {
            return partialPlayableDurationMs
        }
        if (isSoftwareFallbackActive) return totalDurationMs.coerceAtLeast(1000L)
        return try {
            val vvDur = videoViewRef?.duration?.toLong() ?: 0L
            val mpDur = if (vvDur > 0L) vvDur else (mediaPlayerRef?.duration?.toLong() ?: totalDurationMs)
            mpDur.coerceAtLeast(1000L)
        } catch (_: Exception) {
            totalDurationMs.coerceAtLeast(1000L)
        }
    }

    fun seekVideoToMs(targetMs: Long) {
        val maxDur = getTotalVideoDurationMs()
        val safeMax = if (isPartialDownloadStream && maxDur > 1200L) maxDur - 250L else maxDur
        val clamped = targetMs.coerceIn(0L, safeMax.coerceAtLeast(0L))
        currentPosMs = clamped
        if (clamped > 0L) {
            latestKnownPositionMs = clamped
        }
        if (isSoftwareFallbackActive) return
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
        if (!isSoftwareFallbackActive) {
            try {
                videoViewRef?.pause()
            } catch (_: Exception) {
            }
            try {
                if (mediaPlayerRef?.isPlaying == true) mediaPlayerRef?.pause()
            } catch (_: Exception) {
            }
        }
        isPlaying = false
    }

    fun startVideoPlayback() {
        if (!isSoftwareFallbackActive) {
            try {
                videoViewRef?.start()
            } catch (_: Exception) {
            }
            try {
                if (mediaPlayerRef?.isPlaying == false) mediaPlayerRef?.start()
            } catch (_: Exception) {
            }
            applyHardwarePlaybackSpeed(mediaPlayerRef)
        }
        isPlaying = true
    }

    // Live Partial / Pending Download Byte & Playable Duration Watcher:
    // Probes the actual downloaded packets on disk via MediaExtractor + FileDescriptor and extends
    // playable duration live as Chrome / 1DM / ADM / Browser writes more bytes!
    LaunchedEffect(videoPath, isPartialDownloadStream) {
        if (!isNetworkStream) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                var lastSeenBytes = -1L
                while (true) {
                    try {
                        val file = if (!videoPath.startsWith("content://")) File(videoPath) else null
                        val currentBytes = file?.length() ?: 0L
                        if (currentBytes > 0L) {
                            partialLiveBytes = currentBytes
                        }
                        if (currentBytes != lastSeenBytes) {
                            lastSeenBytes = currentBytes
                            val probedMs = probePlayableDurationFromFd(context, videoPath)
                            if (probedMs >= 1000L) {
                                if (isPartialDownloadStream || totalDurationMs <= 1000L || probedMs < totalDurationMs - 3000L) {
                                    partialPlayableDurationMs = probedMs
                                    totalDurationMs = probedMs
                                }
                            } else if (isPartialDownloadStream && partialPlayableDurationMs <= 0L) {
                                val fallbackMs = ((currentBytes / (180 * 1024f)) * 1000L).toLong().coerceAtLeast(1000L)
                                partialPlayableDurationMs = fallbackMs
                                if (totalDurationMs <= 1000L) totalDurationMs = fallbackMs
                            }
                        }
                    } catch (_: Exception) {
                    }
                    if (!isPartialDownloadStream) break
                    delay(1500L)
                }
            }
        }
    }

    // Universal Software Frame-Stream Fallback for Partial/Incomplete Downloads & 8K/4K/2K codecs
    LaunchedEffect(isSoftwareFallbackActive, videoPath) {
        if (isSoftwareFallbackActive) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val retriever = android.media.MediaMetadataRetriever()
                var pfd: android.os.ParcelFileDescriptor? = null
                try {
                    if (videoPath.startsWith("content://")) {
                        pfd = context.contentResolver.openFileDescriptor(Uri.parse(videoPath), "r")
                        if (pfd != null) {
                            retriever.setDataSource(pfd.fileDescriptor)
                        } else {
                            retriever.setDataSource(context, Uri.parse(videoPath))
                        }
                    } else if (isNetworkStream) {
                        retriever.setDataSource(videoPath, streamNetworkHeaders)
                    } else {
                        val rawFile = File(videoPath)
                        if (rawFile.exists()) {
                            pfd = android.os.ParcelFileDescriptor.open(rawFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                            retriever.setDataSource(pfd.fileDescriptor, 0L, rawFile.length().coerceAtLeast(1L))
                        } else {
                            retriever.setDataSource(videoPath)
                        }
                    }
                    val probedPlayable = if (partialPlayableDurationMs >= 1000L) {
                        partialPlayableDurationMs
                    } else {
                        probePlayableDurationFromFd(context, videoPath)
                    }
                    val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val metaDur = durStr?.toLongOrNull() ?: 0L
                    val dur = when {
                        isPartialDownloadStream && probedPlayable >= 1000L -> probedPlayable
                        probedPlayable in 1000L until (metaDur - 3000L) -> probedPlayable
                        metaDur >= 1000L -> metaDur
                        probedPlayable >= 1000L -> probedPlayable
                        else -> totalDurationMs.coerceAtLeast(2000L)
                    }
                    totalDurationMs = dur
                    val vw = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
                    val vh = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
                    nativeVideoWidth = vw
                    nativeVideoHeight = vh

                    val scaleRatio = minOf(854f / vw.coerceAtLeast(1), 480f / vh.coerceAtLeast(1), 1f)
                    val targetW = (vw * scaleRatio).toInt().coerceAtLeast(320)
                    val targetH = (vh * scaleRatio).toInt().coerceAtLeast(180)

                    // Prime initial frame at 0ms immediately so even a 1-second video displays right away
                    val firstBmp = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            retriever.getScaledFrameAtTime(0L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, targetW, targetH)
                                ?: retriever.getFrameAtTime(0L)
                        } else {
                            retriever.getFrameAtTime(0L)
                        }
                    } catch (_: Exception) {
                        null
                    }
                    if (firstBmp != null) {
                        softwareFallbackBitmap = firstBmp
                    }

                    var consecutiveNullFrames = 0
                    var lastTickMs = System.currentTimeMillis()
                    while (isSoftwareFallbackActive) {
                        if (isPlaying) {
                            val now = System.currentTimeMillis()
                            val elapsed = ((now - lastTickMs) * playbackSpeed).toLong().coerceIn(20L, 250L)
                            lastTickMs = now
                            val effectiveMax = if (partialPlayableDurationMs >= 1000L) partialPlayableDurationMs else totalDurationMs
                            val nextPos = currentPosMs + elapsed
                            if (nextPos >= effectiveMax) {
                                currentPosMs = 0L
                                consecutiveNullFrames = 0
                            } else {
                                currentPosMs = nextPos
                                latestKnownPositionMs = nextPos
                            }
                            val frameBmp = try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                    retriever.getScaledFrameAtTime(
                                        currentPosMs * 1000L,
                                        android.media.MediaMetadataRetriever.OPTION_CLOSEST,
                                        targetW,
                                        targetH
                                    ) ?: retriever.getScaledFrameAtTime(
                                        currentPosMs * 1000L,
                                        android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                        targetW,
                                        targetH
                                    ) ?: retriever.getFrameAtTime(currentPosMs * 1000L)
                                } else {
                                    retriever.getFrameAtTime(
                                        currentPosMs * 1000L,
                                        android.media.MediaMetadataRetriever.OPTION_CLOSEST
                                    ) ?: retriever.getFrameAtTime(currentPosMs * 1000L)
                                }
                            } catch (_: Exception) {
                                null
                            }
                            if (frameBmp != null) {
                                softwareFallbackBitmap = frameBmp
                                consecutiveNullFrames = 0
                            } else {
                                consecutiveNullFrames++
                                // If we reached the end of the downloaded bytes in a partial file, clamp duration and loop back to 0s
                                if (consecutiveNullFrames >= 4 && currentPosMs >= 800L) {
                                    partialPlayableDurationMs = currentPosMs.coerceAtLeast(1000L)
                                    totalDurationMs = partialPlayableDurationMs
                                    currentPosMs = 0L
                                    consecutiveNullFrames = 0
                                }
                            }
                            delay(65L)
                        } else {
                            lastTickMs = System.currentTimeMillis()
                            delay(150L)
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    try {
                        retriever.release()
                    } catch (_: Exception) {
                    }
                    try {
                        pfd?.close()
                    } catch (_: Exception) {
                    }
                }
            }
        }
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

    // Progress & A-B Repeat Loop Tracker (with Seamless Partial Download Boundary Handling)
    var lastNotifSec by remember { mutableStateOf(-1L) }
    LaunchedEffect(videoViewRef, mediaPlayerRef, isPlaying, partialPlayableDurationMs) {
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

                    // If playing a partial/pending download and we reach the end of the downloaded bytes,
                    // smoothly loop back to 0:00 before the hardware decoder hits truncated EOF bytes!
                    if (isPartialDownloadStream && partialPlayableDurationMs >= 1000L && pos >= partialPlayableDurationMs - 220L) {
                        seekVideoToMs(0L)
                        startVideoPlayback()
                    }

                    val notifBucket = pos / 5000L
                    if (notifBucket != lastNotifSec) {
                        lastNotifSec = notifBucket
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
            delay(250L)
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
        // --- HARDWARE FULL-SCREEN VIDEO SURFACE (Supports standard & Pending/Partial .crdownload/.part movies via direct FileDescriptor) ---
        val configOrientation = currentConfig.orientation
        val configWidthDp = currentConfig.screenWidthDp
        val configHeightDp = currentConfig.screenHeightDp
        key(videoPath) {
            AndroidView(
                factory = { ctx ->
                    object : android.widget.FrameLayout(ctx) {
                        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                            super.onSizeChanged(w, h, oldw, oldh)
                            if (w > 0 && h > 0 && (w != oldw || h != oldh)) {
                                val child = getChildAt(0) as? FullScreenStudioVideoView
                                child?.onParentBoundsChanged(w, h)
                                post {
                                    child?.onParentBoundsChanged(width, height)
                                    requestLayout()
                                }
                            }
                        }
                    }.apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        val customVideoView = FullScreenStudioVideoView(ctx).apply {
                            videoViewRef = this
                            layoutParams = android.widget.FrameLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.Gravity.CENTER
                            )

                            fun applySafeSurfaceBufferCap(vw: Int, vh: Int) {
                                try {
                                    val maxDim = 1920f
                                    if (vw > 1920 || vh > 1920) {
                                        val ratio = minOf(maxDim / vw.coerceAtLeast(1), maxDim / vh.coerceAtLeast(1))
                                        val safeW = ((vw * ratio).toInt() / 2) * 2
                                        val safeH = ((vh * ratio).toInt() / 2) * 2
                                        if (safeW >= 320 && safeH >= 180) {
                                            holder.setFixedSize(safeW, safeH)
                                        }
                                    } else {
                                        // Allow SurfaceView buffer to follow the rotated layout cleanly (fixes half-screen cut on rotation)
                                        holder.setSizeFromLayout()
                                    }
                                } catch (_: Exception) {
                                }
                            }

                            fun configurePreparedPlayer(mp: android.media.MediaPlayer) {
                                isSoftwareFallbackActive = false
                                isStreamBuffering = false
                                mediaPlayerRef = mp
                                try {
                                    mp.setVideoScalingMode(android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                                } catch (_: Exception) {
                                }
                                mp.setOnBufferingUpdateListener { _, pct ->
                                    streamBufferPercent = pct.coerceIn(0, 100)
                                }
                                mp.setOnInfoListener { _, what, _ ->
                                    when (what) {
                                        android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START -> isStreamBuffering = true
                                        android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END,
                                        android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> isStreamBuffering = false
                                    }
                                    false
                                }
                                val vw = mp.videoWidth.takeIf { it > 0 } ?: 1920
                                val vh = mp.videoHeight.takeIf { it > 0 } ?: 1080
                                nativeVideoWidth = vw
                                nativeVideoHeight = vh
                                applySafeSurfaceBufferCap(vw, vh)
                                updateVideoGeometry(vw, vh, aspectRatioMode, zoomScale)
                                mp.setOnVideoSizeChangedListener { _, w, h ->
                                    if (w > 0 && h > 0) {
                                        nativeVideoWidth = w
                                        nativeVideoHeight = h
                                        applySafeSurfaceBufferCap(w, h)
                                        updateVideoGeometry(w, h, aspectRatioMode, zoomScale)
                                    }
                                }
                                mp.setOnCompletionListener {
                                    if (isPartialDownloadStream) {
                                        seekVideoToMs(0L)
                                        startVideoPlayback()
                                    } else {
                                        handleVideoCompletion()
                                    }
                                }
                                val reportedDur = mp.duration.toLong().coerceAtLeast(1000L)
                                totalDurationMs = if (isPartialDownloadStream && partialPlayableDurationMs >= 1000L) {
                                    partialPlayableDurationMs
                                } else {
                                    reportedDur
                                }
                                val resumeTarget = maxOf(initialPositionMs, latestKnownPositionMs)
                                if (resumeTarget > 0 && resumeTarget < totalDurationMs - 1500L) {
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
                                    loudnessEnhancer?.release()
                                    loudnessEnhancer = LoudnessEnhancer(mp.audioSessionId)
                                    if (volumePercent > 100) {
                                        loudnessEnhancer?.setTargetGain((volumePercent - 100) * 18)
                                        loudnessEnhancer?.enabled = true
                                    }
                                } catch (_: Exception) {
                                }
                            }

                            fun attachAndPrepareRawPlayer(rawMp: android.media.MediaPlayer) {
                                try {
                                    rawFallbackMediaPlayerRef?.release()
                                } catch (_: Exception) {
                                }
                                rawFallbackMediaPlayerRef = rawMp
                                fun bindAndPrepare() {
                                    try {
                                        applySafeSurfaceBufferCap(1920, 1080)
                                        rawMp.setDisplay(holder)
                                        rawMp.setScreenOnWhilePlaying(true)
                                        rawMp.setOnPreparedListener { mp ->
                                            configurePreparedPlayer(mp)
                                        }
                                        rawMp.setOnCompletionListener {
                                            if (isPartialDownloadStream) {
                                                seekVideoToMs(0L)
                                                startVideoPlayback()
                                            } else {
                                                handleVideoCompletion()
                                            }
                                        }
                                        rawMp.setOnErrorListener { mp, _, _ ->
                                            isStreamBuffering = false
                                            // If we already played up to the end of the downloaded partial stream, loop back to 0:00 cleanly!
                                            if (currentPosMs >= 900L || latestKnownPositionMs >= 900L) {
                                                val reachedMs = maxOf(currentPosMs, latestKnownPositionMs)
                                                if (partialPlayableDurationMs <= 0L || reachedMs < partialPlayableDurationMs) {
                                                    partialPlayableDurationMs = reachedMs.coerceAtLeast(1000L)
                                                    totalDurationMs = partialPlayableDurationMs
                                                }
                                                try {
                                                    mp.seekTo(0)
                                                    mp.start()
                                                    currentPosMs = 0L
                                                    isPlaying = true
                                                    return@setOnErrorListener true
                                                } catch (_: Exception) {
                                                }
                                            }
                                            // Otherwise activate Software Frame-Stream Engine (plays even 1-second or unfinalized chunks)
                                            isSoftwareFallbackActive = true
                                            isPlaying = true
                                            true
                                        }
                                        rawMp.prepareAsync()
                                    } catch (_: Exception) {
                                        isSoftwareFallbackActive = true
                                        isPlaying = true
                                    }
                                }

                                if (holder.surface != null && holder.surface.isValid) {
                                    bindAndPrepare()
                                } else {
                                    holder.addCallback(object : android.view.SurfaceHolder.Callback {
                                        override fun surfaceCreated(sh: android.view.SurfaceHolder) {
                                            holder.removeCallback(this)
                                            bindAndPrepare()
                                        }
                                        override fun surfaceChanged(sh: android.view.SurfaceHolder, format: Int, w: Int, h: Int) {}
                                        override fun surfaceDestroyed(sh: android.view.SurfaceHolder) {}
                                    })
                                }
                            }

                            fun startDirectFileDescriptorFallback(): Boolean {
                                return try {
                                    try {
                                        stopPlayback()
                                    } catch (_: Exception) {
                                    }
                                    val rawMp = android.media.MediaPlayer()
                                    if (isNetworkStream) {
                                        rawMp.setDataSource(ctx, Uri.parse(videoPath), streamNetworkHeaders)
                                        attachAndPrepareRawPlayer(rawMp)
                                        return true
                                    }
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
                                        try {
                                            val len = pfd.statSize
                                            if (len > 0L) {
                                                rawMp.setDataSource(pfd.fileDescriptor, 0L, len)
                                            } else {
                                                rawMp.setDataSource(pfd.fileDescriptor)
                                            }
                                        } finally {
                                            try { pfd.close() } catch (_: Exception) {}
                                        }
                                        attachAndPrepareRawPlayer(rawMp)
                                        true
                                    } else {
                                        try { rawMp.release() } catch (_: Exception) {}
                                        isSoftwareFallbackActive = true
                                        isPlaying = true
                                        false
                                    }
                                } catch (_: Exception) {
                                    if (!isNetworkStream) {
                                        isSoftwareFallbackActive = true
                                    }
                                    isPlaying = true
                                    false
                                }
                            }

                            // For Pending / Partially Downloaded files (.crdownload, .part, .1dm, etc.),
                            // open directly via raw FileDescriptor so Android never rejects the partial extension!
                            if (isPartialDownloadStream && !isNetworkStream) {
                                if (!startDirectFileDescriptorFallback()) {
                                    isSoftwareFallbackActive = true
                                    isPlaying = true
                                }
                            } else {
                                try {
                                    if (isNetworkStream) {
                                        isStreamBuffering = true
                                        setVideoURI(Uri.parse(videoPath), streamNetworkHeaders)
                                    } else if (
                                        videoPath.startsWith("content://") ||
                                        videoPath.startsWith("android.resource://")
                                    ) {
                                        setVideoURI(Uri.parse(videoPath))
                                    } else {
                                        val file = File(videoPath)
                                        if (file.exists()) {
                                            setVideoPath(file.absolutePath)
                                        } else {
                                            setVideoURI(Uri.parse(videoPath))
                                        }
                                    }
                                    setOnPreparedListener { mp ->
                                        configurePreparedPlayer(mp)
                                    }
                                    setOnCompletionListener {
                                        handleVideoCompletion()
                                    }
                                    setOnErrorListener { _, _, _ ->
                                        if (!startDirectFileDescriptorFallback() && !isNetworkStream) {
                                            isSoftwareFallbackActive = true
                                            isPlaying = true
                                        }
                                        true
                                    }
                                } catch (_: Exception) {
                                    if (!startDirectFileDescriptorFallback() && !isNetworkStream) {
                                        isSoftwareFallbackActive = true
                                        isPlaying = true
                                    }
                                }
                            }
                        }
                        addView(customVideoView)
                    }
                },
                update = { frameLayout ->
                    val vv = frameLayout.getChildAt(0) as? FullScreenStudioVideoView
                    val orientationKey = configOrientation * 100000 + configWidthDp * 100 + configHeightDp
                    vv?.updateVideoGeometry(
                        w = nativeVideoWidth,
                        h = nativeVideoHeight,
                        mode = aspectRatioMode,
                        scale = zoomScale,
                        orientationSignature = orientationKey
                    )
                    vv?.scaleX = if (isMirrorFlipped) -1f else 1f
                    frameLayout.post {
                        vv?.onParentBoundsChanged(frameLayout.width, frameLayout.height)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
            )
        }

        // Live Network Stream Buffering & Telemetry HUD
        if (isNetworkStream && isStreamBuffering) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xDD0B1322))
                    .border(1.5.dp, accentColor.copy(alpha = 0.7f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 22.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = accentColor,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (streamBufferPercent > 0) "⚡ Buffering HD Stream ($streamBufferPercent%)" else "⚡ Connecting Network Stream...",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = displayTitle,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Live Pending / Partially Downloaded Stream Badge (shows live downloaded MB & playable duration)
        if (isPartialDownloadStream) {
            val mbStr = remember(partialLiveBytes) {
                if (partialLiveBytes >= 1024 * 1024L) {
                    String.format(Locale.US, "%.1f MB", partialLiveBytes / (1024f * 1024f))
                } else {
                    String.format(Locale.US, "%d KB", (partialLiveBytes / 1024L).coerceAtLeast(1L))
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (showControls) 66.dp else 18.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xDD101828))
                    .border(1.2.dp, Color(0xFFFF9100), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "⚡ Pending Stream • Playing Downloaded Portion ($mbStr • ${formatVideoTime(totalDurationMs)})",
                    color = Color(0xFFFFD180),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Night Eye Comfort Mode Scrim (2% Ultra-Dim for dark room watching)
        if (isNightEyeActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
            )
        }

        // Software Adaptive 8K/4K/2K Frame Renderer Overlay (activates automatically if phone hardware MediaCodec rejects >1080p)
        if (isSoftwareFallbackActive && softwareFallbackBitmap != null) {
            val bmp = softwareFallbackBitmap!!
            androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = displayTitle,
                contentScale = when (aspectRatioMode) {
                    "Zoom Crop" -> androidx.compose.ui.layout.ContentScale.Crop
                    "Original Fit" -> androidx.compose.ui.layout.ContentScale.Fit
                    else -> androidx.compose.ui.layout.ContentScale.FillBounds
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoomScale
                        scaleY = zoomScale
                    }
            )
        }

        // Visual Tone Filter Overlay Tint
        val filterTint = remember(visualFilter) {
            when (visualFilter) {
                "Warm Cinema" -> Color(0x22FFB300)
                "Cool Ice" -> Color(0x1E00E5FF)
                "High Contrast" -> Color(0x26000000)
                "Vivid Sunset" -> Color(0x1CFF5252)
                "Night Soft" -> Color(0x36050A14)
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

        // Live Subtitle Overlay (.srt / .vtt)
        if (subtitlesEnabled) {
            val adjustedPos = (currentPosMs + subtitleSyncOffsetMs).coerceAtLeast(0L)
            val activeCue = remember(loadedSubtitleCues, adjustedPos) {
                loadedSubtitleCues.firstOrNull { adjustedPos in it.startMs..it.endMs }
            }
            val captionText = when {
                loadedSubtitleCues.isEmpty() -> "No subtitle file loaded — tap Subtitles to load .SRT / .VTT"
                activeCue != null -> activeCue.text
                else -> null
            }
            if (captionText != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 20.dp)
                        .padding(bottom = if (showControls) 96.dp else 28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xBB000000))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = captionText,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
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
                            if (isSoftwareFallbackActive || playbackSpeed != 1.0f || videoSleepRemainingSeconds > 0) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (isSoftwareFallbackActive) {
                                        Text(
                                            text = "⚡ Smart HD Fit",
                                            color = HybridCyan,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
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
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Background Audio Quick Icon
                        IconButton(
                            onClick = {
                                val pos = getCurrentVideoPositionMs().takeIf { it > 0L } ?: latestKnownPositionMs
                                onProgressUpdate(pos)
                                Toast.makeText(
                                    context,
                                    "🎧 Background Video Audio Active • Continues with screen off!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                if (onBackgroundAudioPlayWithPosition != null) {
                                    onBackgroundAudioPlayWithPosition(pos, playbackSpeed)
                                } else {
                                    onBackgroundAudioPlay()
                                }
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
                                    tint = if (subtitlesEnabled) Color.White else Color.White.copy(alpha = 0.7f)
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

                    // CENTER PLAY / PAUSE, -10s / +10s SEEK, PREV / NEXT (Clean Spacious 5-Button Layout)
                    val sideBtnSize = if (isCompactSplitWindow) 38.dp else 46.dp
                    val seekBtnSize = if (isCompactSplitWindow) 44.dp else 54.dp
                    val playBtnSize = if (isCompactSplitWindow) 56.dp else 68.dp
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(if (isCompactSplitWindow) 12.dp else 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Previous Video
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
                                    .background(Color(0x28FFFFFF))
                                    .border(1.dp, Color(0x33FFFFFF), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.SkipPrevious,
                                    contentDescription = "Previous Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(if (isCompactSplitWindow) 20.dp else 24.dp)
                                )
                            }
                        }

                        // 2. Rewind 10s
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
                                .background(Color(0x28FFFFFF))
                                .border(1.dp, Color(0x33FFFFFF), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Replay10,
                                contentDescription = "Rewind 10s",
                                tint = Color.White,
                                modifier = Modifier.size(if (isCompactSplitWindow) 22.dp else 28.dp)
                            )
                        }

                        // 3. Center Play / Pause in Pure High-Contrast White
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
                                .background(Color.White)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.Black,
                                modifier = Modifier.size(if (isCompactSplitWindow) 30.dp else 38.dp)
                            )
                        }

                        // 4. Forward 10s
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
                                .background(Color(0x28FFFFFF))
                                .border(1.dp, Color(0x33FFFFFF), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Forward10,
                                contentDescription = "Forward 10s",
                                tint = Color.White,
                                modifier = Modifier.size(if (isCompactSplitWindow) 22.dp else 28.dp)
                            )
                        }

                        // 5. Next Video
                        if (allVideos.size > 1) {
                            IconButton(
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    val idx = allVideos.indexOfFirst { it.path == videoPath }
                                    val nextIdx = if (isShuffleActive) {
                                        val candidates = (0 until allVideos.size).filter { it != idx }
                                        if (candidates.isNotEmpty()) candidates.random() else 0
                                    } else {
                                        if (idx >= 0) (idx + 1) % allVideos.size else 0
                                    }
                                    onSelectVideo(allVideos[nextIdx])
                                },
                                modifier = Modifier
                                    .size(sideBtnSize)
                                    .clip(CircleShape)
                                    .background(Color(0x28FFFFFF))
                                    .border(1.dp, Color(0x33FFFFFF), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.SkipNext,
                                    contentDescription = "Next Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(if (isCompactSplitWindow) 20.dp else 24.dp)
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
                        listOf("Original Fit", "Full Screen", "Zoom Crop", "Stretch 16:9", "Cinema 21:9", "Classic 4:3")
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
                        playbackSpeed = playbackSpeed,
                        onCyclePlaybackSpeed = {
                            showSpeedDialog = true
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        aspectCycleIndex = aspectPresets.indexOf(aspectRatioMode).coerceAtLeast(0),
                        onCycleAspectRatio = {
                            val nextIdx = (aspectPresets.indexOf(aspectRatioMode) + 1) % aspectPresets.size
                            aspectRatioMode = aspectPresets[nextIdx]
                            gestureOverlayText = "📐 $aspectRatioMode"
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        onRotateOrientation = {
                            val activity = context as? Activity
                            if (isCurrentlyLandscape) {
                                orientationLabel = "Portrait"
                                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                            } else {
                                orientationLabel = "Landscape"
                                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            }
                            lastInteractionTime = System.currentTimeMillis()
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
                        onCaptureScreenshot = {
                            val snapPos = currentPosMs
                            coroutineScope.launch(Dispatchers.IO) {
                                val savedOk = saveSnapshotBitmapToGallery(context, videoPath, snapPos, displayTitle)
                                withContext(Dispatchers.Main) {
                                    gestureOverlayText = if (savedOk) "📸 Snapshot Saved" else "⚠️ Snapshot Failed"
                                    Toast.makeText(
                                        context,
                                        if (savedOk) "📸 Frame saved to Pictures/HybridPlayer" else "Unable to extract video frame",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        isBgAudioEnabled = false,
                        onToggleBgAudio = {
                            val pos = getCurrentVideoPositionMs().takeIf { it > 0L } ?: currentPosMs
                            onProgressUpdate(pos)
                            Toast.makeText(
                                context,
                                "🎧 Background Video Audio Active • Continues with screen off!",
                                Toast.LENGTH_SHORT
                            ).show()
                            if (onBackgroundAudioPlayWithPosition != null) {
                                onBackgroundAudioPlayWithPosition(pos, playbackSpeed)
                            } else {
                                onBackgroundAudioPlay()
                            }
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
                                    gestureOverlayText = "A ⇄ B Loop"
                                }
                                else -> {
                                    abPointA = null
                                    abPointB = null
                                    gestureOverlayText = "A-B Loop OFF"
                                }
                            }
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        subtitlesEnabled = subtitlesEnabled,
                        onOpenSubtitles = { showSubtitleDialog = true },
                        onOpenAudioTrack = { showAudioTrackDialog = true },
                        visualFilterName = visualFilter,
                        onOpenVisualFilters = { showFilterDialog = true },
                        isNightEyeActive = isNightEyeActive,
                        onToggleNightEye = {
                            isNightEyeActive = !isNightEyeActive
                            gestureOverlayText = if (isNightEyeActive) "🌙 Night Eye 2% ON" else "☀️ Standard Eye"
                        },
                        isMirrorFlipped = isMirrorFlipped,
                        onToggleMirrorFlip = {
                            isMirrorFlipped = !isMirrorFlipped
                            gestureOverlayText = if (isMirrorFlipped) "🪞 Mirror Flip ON" else "Standard View"
                        },
                        isShuffleActive = isShuffleActive,
                        onToggleShuffle = {
                            isShuffleActive = !isShuffleActive
                            gestureOverlayText = if (isShuffleActive) "🔀 Shuffle ON" else "➡️ Order Play"
                        },
                        isLoopSingleActive = isLoopSingleActive,
                        onToggleLoopSingle = {
                            isLoopSingleActive = !isLoopSingleActive
                            gestureOverlayText = if (isLoopSingleActive) "🔂 Loop 1 ON" else "🔁 Loop All"
                        },
                        onOpenQueueSheet = { showQueueDialog = true },
                        onOpenProControlSheet = { showThreeDotSheet = true }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // SeekBar & Timestamps (Clean White Theme)
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
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
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

    // --- VISUAL COLOR TONE FILTERS DIALOG ---
    if (showFilterDialog) {
        val filters = listOf(
            "Original", "Warm Cinema", "Cool Ice", "High Contrast", "Vivid Sunset", "Night Soft"
        )
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            containerColor = HybridCardBg,
            title = { Text("Color Tone Filters", color = Color.White, fontWeight = FontWeight.Bold) },
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

    // --- MULTI-TRACK AUDIO SELECTOR (Inspects Real MediaPlayer TrackInfo) ---
    if (showAudioTrackDialog) {
        val realAudioTracks = remember(mediaPlayerRef, showAudioTrackDialog) {
            try {
                mediaPlayerRef?.trackInfo?.mapIndexedNotNull { index, info ->
                    if (info.trackType == android.media.MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO) {
                        val lang = info.language?.takeIf { it.isNotBlank() && it != "und" }?.uppercase(Locale.US) ?: "Default"
                        index to "Audio Track ${index + 1} ($lang)"
                    } else null
                } ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
        AlertDialog(
            onDismissRequest = { showAudioTrackDialog = false },
            containerColor = HybridCardBg,
            title = { Text("Audio Track Switcher", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (realAudioTracks.size <= 1) {
                        val label = realAudioTracks.firstOrNull()?.second ?: "Track 1: Default Audio"
                        Text(
                            text = "$label — This video contains a single embedded audio stream.",
                            color = HybridTextSecondary,
                            fontSize = 13.sp
                        )
                    } else {
                        realAudioTracks.forEach { (idx, label) ->
                            val isSelectedTrack = selectedAudioTrackIndex == idx || (selectedAudioTrackIndex == -1 && idx == realAudioTracks.first().first)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelectedTrack) HybridEmerald.copy(alpha = 0.2f) else HybridCardAlt)
                                    .clickable {
                                        try {
                                            mediaPlayerRef?.selectTrack(idx)
                                            selectedAudioTrackIndex = idx
                                            Toast.makeText(context, "Switched to $label", Toast.LENGTH_SHORT).show()
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Audio track could not be switched on this stream", Toast.LENGTH_SHORT).show()
                                        }
                                        showAudioTrackDialog = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                if (isSelectedTrack) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = HybridEmerald)
                                }
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

    // --- REAL SUBTITLE (.SRT/.VTT) PICKER & +/-100MS SYNC DELAY ADJUSTER ---
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
                        Text("Show Subtitles", color = Color.White)
                        Switch(
                            checked = subtitlesEnabled,
                            onCheckedChange = { subtitlesEnabled = it }
                        )
                    }

                    Button(
                        onClick = { subtitlePickerLauncher.launch(arrayOf("*/*")) },
                        colors = ButtonDefaults.buttonColors(containerColor = HybridCyan),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Subtitles, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (loadedSubtitleFileName != null) "Change File ($loadedSubtitleFileName)" else "Load .SRT / .VTT File",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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

    // --- 25 PRO TOOLS & SLEEP TIMER SUITE DIALOG (VIBRANT COLORFUL STUDIO EDITION) ---
    if (showThreeDotSheet) {
        val proCardColors = remember {
            listOf(
                Color(0xFF00E5FF), // Playback Speed - Cyan
                Color(0xFFFF2A85), // Audio Track - Neon Pink
                Color(0xFFFFD700), // Subtitles & CC - Gold
                Color(0xFF00E676), // Aspect Ratio - Emerald
                Color(0xFFB388FF), // Screen Rotation - Purple
                Color(0xFFFF6D00), // A-B Looper - Orange
                Color(0xFFFF4081), // Color Filters - Rose
                Color(0xFFAEEA00), // 200% Vol Boost - Lime
                Color(0xFF18FFFF), // BG Audio Mode - Electric Aqua
                Color(0xFF7C4DFF), // Floating PiP - Indigo
                Color(0xFFFFAB00), // Video Queue - Amber
                Color(0xFFFF5252), // Touch Lock - Coral Red
                Color(0xFF64FFDA), // Mute / Unmute - Mint
                Color(0xFF40C4FF), // Media Details - Sky Blue
                Color(0xFFFF1744), // Favorite - Ruby Heart
                Color(0xFFFFD600), // Private Vault - Gold Lock
                Color(0xFF00E676), // Turbo Share - Share Emerald
                Color(0xFFE040FB), // Snapshot Capture - Magenta
                Color(0xFF00B0FF), // Mirror Flip 180 - Ocean Blue
                Color(0xFF9575CD), // Night Eye Comfort - Lavender
                Color(0xFF69F0AE), // Auto-Play Next - Spring Green
                Color(0xFFFF8A65), // Repeat 1 Video - Peach
                Color(0xFFEA80FC), // Shuffle Queue - Orchid
                Color(0xFF00E5FF)  // Decoder Engine - Cyber Cyan
            )
        }

        AlertDialog(
            onDismissRequest = { showThreeDotSheet = false },
            containerColor = Color(0xFF0A101D),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF00E676), Color(0xFF00E5FF))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "25 Pro Tools & Features",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }
                    if (videoSleepRemainingSeconds > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0x33FFD700))
                                .border(1.dp, Color(0xFFFFD700), RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "🌙 ${formatVideoTime(videoSleepRemainingSeconds * 1000L)}",
                                color = Color(0xFFFFD700),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. SLEEP TIMER (Colorful Violet-Cyan Neon Glass Card)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0x387C4DFF),
                                        Color(0xFF121A2E),
                                        Color(0x2E00E5FF)
                                    )
                                )
                            )
                            .border(
                                1.2.dp,
                                Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFF00E5FF))),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x33FFD700)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Bedtime,
                                    contentDescription = null,
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (videoSleepRemainingSeconds > 0) {
                                    "Sleep Timer: ${formatVideoTime(videoSleepRemainingSeconds * 1000L)}"
                                } else {
                                    "Sleep Timer (Auto-Pause)"
                                },
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
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
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (selected) {
                                                Brush.horizontalGradient(listOf(Color(0xFF00E676), Color(0xFF00E5FF)))
                                            } else {
                                                Brush.horizontalGradient(listOf(Color(0x24FFFFFF), Color(0x18FFFFFF)))
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            if (selected) Color(0xFF00E676) else Color(0x387C4DFF),
                                            RoundedCornerShape(10.dp)
                                        )
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
                                        fontWeight = FontWeight.ExtraBold,
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
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (selected) {
                                                Brush.horizontalGradient(listOf(Color(0xFF00E676), Color(0xFF00E5FF)))
                                            } else {
                                                Brush.horizontalGradient(listOf(Color(0x24FFFFFF), Color(0x18FFFFFF)))
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            if (selected) Color(0xFF00E676) else Color(0x3800E5FF),
                                            RoundedCornerShape(10.dp)
                                        )
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
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    // 24 PRO ACTION BUTTONS (Vibrant Colorful 2-Column Grid)
                    val proTools = listOf(
                        Triple(Icons.Default.Speed, "Playback Speed", "${playbackSpeed}x Speed") to {
                            showThreeDotSheet = false
                            showSpeedDialog = true
                        },
                        Triple(Icons.Default.Audiotrack, "Audio Track", "Dual Audio / Stereo") to {
                            showThreeDotSheet = false
                            showAudioTrackDialog = true
                        },
                        Triple(Icons.Default.Subtitles, "Subtitles & CC", if (subtitlesEnabled) "CC Active" else "Off") to {
                            showThreeDotSheet = false
                            showSubtitleDialog = true
                        },
                        Triple(Icons.Default.AspectRatio, "Aspect Ratio", aspectRatioMode) to {
                            val aspectPresets = listOf("Original Fit", "Full Screen", "Zoom Crop", "Stretch 16:9", "Cinema 21:9", "Classic 4:3")
                            val nextIdx = (aspectPresets.indexOf(aspectRatioMode) + 1) % aspectPresets.size
                            aspectRatioMode = aspectPresets[nextIdx]
                            gestureOverlayText = "📐 $aspectRatioMode"
                        },
                        Triple(Icons.Default.ScreenRotation, "Screen Rotation", orientationLabel) to {
                            val activity = context as? Activity
                            if (isCurrentlyLandscape) {
                                orientationLabel = "Portrait"
                                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                            } else {
                                orientationLabel = "Landscape"
                                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            }
                        },
                        Triple(Icons.Default.Repeat, "A-B Looper", if (abPointA != null) "Loop Active" else "Loop Off") to {
                            when {
                                abPointA == null -> {
                                    abPointA = currentPosMs
                                    gestureOverlayText = "A → ${formatVideoTime(currentPosMs)}"
                                }
                                abPointB == null && currentPosMs > (abPointA ?: 0L) + 1000L -> {
                                    abPointB = currentPosMs
                                    gestureOverlayText = "A ⇄ B Loop"
                                }
                                else -> {
                                    abPointA = null
                                    abPointB = null
                                    gestureOverlayText = "A-B Loop OFF"
                                }
                            }
                        },
                        Triple(Icons.Default.Palette, "Color Filters", visualFilter) to {
                            showThreeDotSheet = false
                            showFilterDialog = true
                        },
                        Triple(Icons.Default.GraphicEq, "200% Vol Boost", "${volumePercent}% Vol") to {
                            val nextVol = when {
                                volumePercent < 100 -> 100
                                volumePercent == 100 -> 150
                                volumePercent in 101..175 -> 200
                                else -> 100
                            }
                            updateVolumeWithBoost(nextVol, triggerManualHaptic = true)
                            gestureOverlayText = "🔊 $nextVol%"
                        },
                        Triple(Icons.Default.Headphones, "BG Audio Mode", "Play Screen Off") to {
                            showThreeDotSheet = false
                            val pos = getCurrentVideoPositionMs().takeIf { it > 0L } ?: currentPosMs
                            onProgressUpdate(pos)
                            Toast.makeText(
                                context,
                                "🎧 Background Video Audio Active • Continues with screen off!",
                                Toast.LENGTH_SHORT
                            ).show()
                            if (onBackgroundAudioPlayWithPosition != null) {
                                onBackgroundAudioPlayWithPosition(pos, playbackSpeed)
                            } else {
                                onBackgroundAudioPlay()
                            }
                        },
                        Triple(Icons.Default.PictureInPictureAlt, "Floating PiP", "Mini Window") to {
                            showThreeDotSheet = false
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
                        Triple(Icons.AutoMirrored.Filled.PlaylistPlay, "Video Queue", "${allVideos.size} Videos") to {
                            showThreeDotSheet = false
                            showQueueDialog = true
                        },
                        Triple(Icons.Default.LockOpen, "Touch Lock", "Lock Touches") to {
                            showThreeDotSheet = false
                            isScreenLocked = true
                        },
                        Triple(Icons.AutoMirrored.Filled.VolumeMute, "Mute / Unmute", if (isMuted) "Muted" else "Sound ON") to {
                            toggleMute()
                        },
                        Triple(Icons.Default.Info, "Media Details", "Resolution & Codec") to {
                            showThreeDotSheet = false
                            showMediaInfoDialog = true
                        },
                        Triple(Icons.Default.Favorite, "Favorite", if (isFavorite) "Bookmarked ❤️" else "Add Favorite") to {
                            toggleFavorite()
                        },
                        Triple(Icons.Default.Lock, "Private Vault", "Lock Video") to {
                            showThreeDotSheet = false
                            if (onLockInVault != null) {
                                onLockInVault()
                                Toast.makeText(context, "🔒 Video Moved to Private Vault", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Open video from main library to lock in Vault", Toast.LENGTH_SHORT).show()
                            }
                        },
                        Triple(Icons.Default.Share, "Turbo Share", "Send Video") to {
                            showThreeDotSheet = false
                            try {
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "video/*"
                                    putExtra(android.content.Intent.EXTRA_STREAM, Uri.parse(videoPath))
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Video via"))
                            } catch (_: Exception) {
                                Toast.makeText(context, "Sharing: $displayTitle", Toast.LENGTH_SHORT).show()
                            }
                        },
                        Triple(Icons.Default.CameraAlt, "Snapshot Capture", "Save Frame 📸") to {
                            showThreeDotSheet = false
                            val snapPos = currentPosMs
                            coroutineScope.launch(Dispatchers.IO) {
                                val savedOk = saveSnapshotBitmapToGallery(context, videoPath, snapPos, displayTitle)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        if (savedOk) "📸 Frame saved to Pictures/HybridPlayer" else "Unable to extract video frame",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            Unit
                        },
                        Triple(Icons.Default.Flip, "Mirror Flip 180°", if (isMirrorFlipped) "Flipped ON" else "Normal") to {
                            isMirrorFlipped = !isMirrorFlipped
                            gestureOverlayText = if (isMirrorFlipped) "🪞 Mirror Flip ON" else "Standard View"
                        },
                        Triple(Icons.Default.DarkMode, "Night Eye Comfort", if (isNightEyeActive) "2% Ultra-Dim ON" else "Standard") to {
                            isNightEyeActive = !isNightEyeActive
                            gestureOverlayText = if (isNightEyeActive) "🌙 Night Eye 2% ON" else "☀️ Standard Eye"
                        },
                        Triple(Icons.Default.AutoMode, "Auto-Play Next", if (isAutoPlayNextEnabled) "Auto ON" else "Auto OFF") to {
                            isAutoPlayNextEnabled = !isAutoPlayNextEnabled
                            prefs.edit().putBoolean("auto_play_next_video", isAutoPlayNextEnabled).apply()
                            gestureOverlayText = if (isAutoPlayNextEnabled) "⏭️ Auto-Play ON" else "⏸️ Auto-Play OFF"
                        },
                        Triple(Icons.Default.RepeatOne, "Repeat 1 Video", if (isLoopSingleActive) "Looping 1" else "Loop All") to {
                            isLoopSingleActive = !isLoopSingleActive
                            gestureOverlayText = if (isLoopSingleActive) "🔂 Repeat 1 Video" else "🔁 Repeat All Videos"
                        },
                        Triple(Icons.Default.Shuffle, "Shuffle Queue", if (isShuffleActive) "Shuffle ON" else "Order Play") to {
                            isShuffleActive = !isShuffleActive
                            gestureOverlayText = if (isShuffleActive) "🔀 Shuffle ON" else "➡️ Order Play"
                        },
                        Triple(Icons.Default.Memory, "Decoder Engine", if (isHardwareDecoderActive) "Hardware Codec" else "SW Frame Mode") to {
                            isHardwareDecoderActive = !isHardwareDecoderActive
                            isSoftwareFallbackActive = !isHardwareDecoderActive
                            if (isSoftwareFallbackActive) {
                                try { videoViewRef?.pause() } catch (_: Exception) {}
                                try { mediaPlayerRef?.pause() } catch (_: Exception) {}
                            } else {
                                startVideoPlayback()
                            }
                            gestureOverlayText = if (isHardwareDecoderActive) "⚡ Hardware Codec" else "⚙️ SW Frame Mode"
                        }
                    )

                    // Render in 2-column rows with distinct vibrant colors
                    for (i in proTools.indices step 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val item1 = proTools[i]
                            val color1 = proCardColors[i % proCardColors.size]
                            ProToolButton(
                                icon = item1.first.first,
                                title = item1.first.second,
                                subtitle = item1.first.third,
                                accentColor = color1,
                                onClick = item1.second,
                                modifier = Modifier.weight(1f)
                            )
                            if (i + 1 < proTools.size) {
                                val item2 = proTools[i + 1]
                                val color2 = proCardColors[(i + 1) % proCardColors.size]
                                ProToolButton(
                                    icon = item2.first.first,
                                    title = item2.first.second,
                                    subtitle = item2.first.third,
                                    accentColor = color2,
                                    onClick = item2.second,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThreeDotSheet = false }) {
                    Text("Close", color = Color(0xFF00E676), fontWeight = FontWeight.ExtraBold)
                }
            }
        )
    }

    // --- ACCURATE PLAYBACK SPEED SELECTOR DIALOG (0.25x – 8.0x) ---
    if (showSpeedDialog) {
        val speedOptions = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f, 6.0f, 8.0f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            containerColor = Color(0xFF0F1420),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Playback Speed", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        text = String.format(Locale.US, "%.2fx", playbackSpeed),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Select preset or drag slider (0.25x to 8.0x Turbo)",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )

                    // Continuous Slider from 0.25f to 8.0f
                    Slider(
                        value = playbackSpeed.coerceIn(0.25f, 8.0f),
                        onValueChange = { newSp ->
                            playbackSpeed = (Math.round(newSp * 20f) / 20f).coerceIn(0.25f, 8.0f)
                            applyHardwarePlaybackSpeed(mediaPlayerRef)
                        },
                        valueRange = 0.25f..8.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color(0x38FFFFFF)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Preset Chips (4 per row)
                    for (row in speedOptions.chunked(4)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            row.forEach { sp ->
                                val isSelected = Math.abs(playbackSpeed - sp) < 0.05f
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color.White else Color(0x28FFFFFF))
                                        .border(1.dp, if (isSelected) Color.White else Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                                        .clickable {
                                            playbackSpeed = sp
                                            applyHardwarePlaybackSpeed(mediaPlayerRef)
                                            gestureOverlayText = "⚡ ${sp}x"
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${sp}x",
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
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
    private var aspectMode: String = "Original Fit"
    private var pinchScale: Float = 1.0f
    private var lastOrientationSignature: Int = 0
    private var containerBoundsW: Int = 0
    private var containerBoundsH: Int = 0

    fun onParentBoundsChanged(parentW: Int, parentH: Int) {
        if (parentW <= 0 || parentH <= 0) return
        if (containerBoundsW != parentW || containerBoundsH != parentH) {
            containerBoundsW = parentW
            containerBoundsH = parentH
            if (videoW <= 1920 && videoH <= 1920) {
                try {
                    holder.setSizeFromLayout()
                } catch (_: Exception) {
                }
            }
            requestLayout()
            invalidate()
        }
    }

    fun updateVideoGeometry(
        w: Int,
        h: Int,
        mode: String,
        scale: Float,
        orientationSignature: Int = lastOrientationSignature
    ) {
        val validW = if (w > 0) w else 1920
        val validH = if (h > 0) h else 1080
        val clampedScale = scale.coerceIn(0.5f, 6.0f)
        val orientationChanged = orientationSignature != lastOrientationSignature
        if (videoW != validW || videoH != validH || aspectMode != mode || pinchScale != clampedScale || orientationChanged) {
            videoW = validW
            videoH = validH
            aspectMode = mode
            pinchScale = clampedScale
            lastOrientationSignature = orientationSignature
            if (orientationChanged && validW <= 1920 && validH <= 1920) {
                try {
                    holder.setSizeFromLayout()
                } catch (_: Exception) {
                }
            }
            requestLayout()
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val specW = MeasureSpec.getSize(widthMeasureSpec)
        val specH = MeasureSpec.getSize(heightMeasureSpec)
        val parentWidth = (if (specW > 0) specW else containerBoundsW).coerceAtLeast(1)
        val parentHeight = (if (specH > 0) specH else containerBoundsH).coerceAtLeast(1)

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
                val screenRatio = parentWidth.toFloat() / parentHeight.toFloat().coerceAtLeast(1f)
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
                val screenRatio = parentWidth.toFloat() / parentHeight.toFloat().coerceAtLeast(1f)
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

@Composable
private fun ProToolButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color = Color(0xFF00E5FF),
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        accentColor.copy(alpha = 0.22f),
                        Color(0xFF131B2E),
                        accentColor.copy(alpha = 0.10f)
                    )
                )
            )
            .border(1.1.dp, accentColor.copy(alpha = 0.60f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.24f))
                .border(1.dp, accentColor.copy(alpha = 0.70f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accentColor,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(modifier = Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = accentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
 * Probes the actual downloaded playable duration (in ms) of a partial / incomplete video file
 * directly from its kernel FileDescriptor using MediaExtractor packet inspection.
 * Works even when only 1 second of a video has been downloaded or when container headers report the full movie length.
 */
private fun probePlayableDurationFromFd(context: Context, videoPath: String): Long {
    val extractor = android.media.MediaExtractor()
    var pfd: android.os.ParcelFileDescriptor? = null
    return try {
        var fileBytes = 0L
        if (videoPath.startsWith("content://")) {
            pfd = context.contentResolver.openFileDescriptor(Uri.parse(videoPath), "r")
            if (pfd != null) {
                extractor.setDataSource(pfd.fileDescriptor)
            } else {
                return 0L
            }
        } else {
            val file = File(videoPath)
            if (!file.exists() || file.length() < 512L) return 0L
            fileBytes = file.length()
            pfd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            extractor.setDataSource(pfd.fileDescriptor, 0L, fileBytes)
        }

        var selectedTrack = -1
        var headerDurationUs = 0L
        val trackCount = extractor.trackCount
        for (i in 0 until trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("video/", ignoreCase = true)) {
                selectedTrack = i
                if (format.containsKey(android.media.MediaFormat.KEY_DURATION)) {
                    headerDurationUs = format.getLong(android.media.MediaFormat.KEY_DURATION)
                }
                break
            } else if (selectedTrack < 0 && mime.startsWith("audio/", ignoreCase = true)) {
                selectedTrack = i
                if (format.containsKey(android.media.MediaFormat.KEY_DURATION)) {
                    headerDurationUs = format.getLong(android.media.MediaFormat.KEY_DURATION)
                }
            }
        }

        if (selectedTrack < 0) return 0L
        extractor.selectTrack(selectedTrack)

        // Step packets from current position or seek near the end of available sync frames
        var maxSampleUs = 0L
        if (headerDurationUs > 0L) {
            // Seek to header duration; for a truncated file, MediaExtractor clamps to the last available sync sample on disk
            extractor.seekTo(headerDurationUs, android.media.MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val syncTime = extractor.sampleTime
            if (syncTime > 0L) {
                maxSampleUs = syncTime
            } else {
                extractor.seekTo(0L, android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }
        }

        var steps = 0
        while (steps < 2500) {
            val st = extractor.sampleTime
            if (st > maxSampleUs) {
                maxSampleUs = st
            }
            if (!extractor.advance()) break
            steps++
        }

        val probedMs = (maxSampleUs / 1000L)
        when {
            probedMs >= 1000L -> probedMs
            maxSampleUs > 0L -> 1000L // At least 1 second playable if valid packets exist
            headerDurationUs >= 1_000_000L -> (headerDurationUs / 1000L)
            fileBytes >= 1024L -> 1000L
            else -> 0L
        }
    } catch (_: Throwable) {
        0L
    } finally {
        try {
            extractor.release()
        } catch (_: Throwable) {
        }
        try {
            pfd?.close()
        } catch (_: Throwable) {
        }
    }
}



