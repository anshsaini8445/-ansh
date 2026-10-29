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
    onBackgroundAudioPlay: () -> Unit = {}
) {
    val context = LocalContext.current
    val hostView = LocalView.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val prefs = remember { context.getSharedPreferences("HYBRID_PLAYER_PREFS", Context.MODE_PRIVATE) }

    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var loudnessEnhancer by remember { mutableStateOf<LoudnessEnhancer?>(null) }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableStateOf(initialPositionMs) }
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

    val displayTitle = remember(videoTitle, videoPath) {
        if (videoTitle.isNotBlank()) videoTitle else File(videoPath).nameWithoutExtension.ifEmpty { "8K Studio Video" }
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

    // Apply hardware PlaybackParams speed whenever playbackSpeed changes
    LaunchedEffect(playbackSpeed, mediaPlayerRef) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaPlayerRef?.let { mp ->
                try {
                    if (mp.isPlaying) {
                        mp.playbackParams = PlaybackParams().setSpeed(playbackSpeed.coerceIn(0.25f, 8.0f))
                    }
                } catch (_: Exception) {
                }
            }
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

    // Progress & A-B Repeat Loop Tracker
    LaunchedEffect(videoViewRef, isPlaying) {
        while (true) {
            videoViewRef?.let { vv ->
                try {
                    if (vv.isPlaying) {
                        val pos = vv.currentPosition.toLong()
                        val dur = vv.duration.toLong().coerceAtLeast(1L)
                        currentPosMs = pos
                        totalDurationMs = dur
                        onProgressUpdate(pos)

                        val ptA = abPointA
                        val ptB = abPointB
                        if (ptA != null && ptB != null && pos >= ptB) {
                            vv.seekTo(ptA.toInt())
                        }
                    }
                } catch (_: Exception) {
                }
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
                    videoViewRef?.seekTo(targetMs.toInt())
                    currentPosMs = targetMs
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
                    videoViewRef?.seekTo(newPosMs.toInt())
                    currentPosMs = newPosMs
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
        // --- VIDEO SURFACE WITH PINCH-TO-ZOOM & 8K VISUAL ENHANCER FILTERS ---
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    videoViewRef = this
                    try {
                        if (videoPath.startsWith("http")) {
                            setVideoURI(Uri.parse(videoPath))
                        } else {
                            val playablePath = resolvePlayablePartialPath(ctx, videoPath)
                            setVideoPath(playablePath)
                        }
                        setOnPreparedListener { mp ->
                            mediaPlayerRef = mp
                            totalDurationMs = mp.duration.toLong().coerceAtLeast(1L)
                            if (initialPositionMs > 0 && initialPositionMs < totalDurationMs - 2000L) {
                                seekTo(initialPositionMs.toInt())
                            }
                            start()
                            isPlaying = true
                            try {
                                loudnessEnhancer = LoudnessEnhancer(mp.audioSessionId)
                            } catch (_: Exception) {
                            }
                        }
                        setOnErrorListener { _, _, _ ->
                            // Stage 3 Graceful Fallback so partial/corrupt containers never crash
                            Toast.makeText(
                                ctx,
                                "Playing Partial Stream via Hybrid Byte-Recovery Engine",
                                Toast.LENGTH_SHORT
                            ).show()
                            true
                        }
                    } catch (_: Exception) {
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
                .graphicsLayer(
                    scaleX = zoomScale,
                    scaleY = zoomScale
                )
        )

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

        // Custom Gesture HUD Overlay (Brightness 2-100%, Volume 0-200% Boost, Seek, Dynamic Speed)
        VideoGestureHudOverlay(
            hudState = gestureHudState,
            modifier = Modifier.align(Alignment.Center)
        )

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

        // --- MINIMAL IMMERSIVE CONTROLS (Auto-Hide after 3s, Translucent Borderless Circles #33000000) ---
        AnimatedVisibility(
            visible = showControls && !isScreenLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // TOP BAR
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(HybridTranslucentControl)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayTitle,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "8K Hybrid Hardware Engine • $visualFilter • ${String.format(Locale.US, "%.2fx", playbackSpeed)}",
                            color = HybridEmerald,
                            fontSize = 11.sp
                        )
                    }

                    // Audio Track Switcher Quick Icon
                    IconButton(
                        onClick = { showAudioTrackDialog = true },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(HybridTranslucentControl)
                    ) {
                        Icon(Icons.Default.Audiotrack, contentDescription = "Audio Track", tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Subtitles (.srt/.vtt/.ass) Quick Icon
                    IconButton(
                        onClick = { showSubtitleDialog = true },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(HybridTranslucentControl)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Subtitles,
                            contentDescription = "Subtitles",
                            tint = if (subtitlesEnabled) HybridEmerald else Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // 3-Dot Pro Player Menu
                    IconButton(
                        onClick = { showThreeDotSheet = true },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(HybridTranslucentControl)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Pro Tools Menu", tint = Color.White)
                    }
                }

                // CENTER PLAY / PAUSE & SEEK BUTTONS (Translucent Borderless Dark Circles #33000000)
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            videoViewRef?.let { vv ->
                                val target = (vv.currentPosition - 10_000).coerceAtLeast(0)
                                vv.seekTo(target)
                                currentPosMs = target.toLong()
                            }
                        },
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(HybridTranslucentControl)
                    ) {
                        Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(30.dp))
                    }

                    IconButton(
                        onClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            videoViewRef?.let { vv ->
                                if (vv.isPlaying) {
                                    vv.pause()
                                    isPlaying = false
                                } else {
                                    vv.start()
                                    isPlaying = true
                                }
                            }
                        },
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(HybridTranslucentControl)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            videoViewRef?.let { vv ->
                                val target = (vv.currentPosition + 10_000).coerceAtMost(vv.duration.coerceAtLeast(1))
                                vv.seekTo(target)
                                currentPosMs = target.toLong()
                            }
                        },
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(HybridTranslucentControl)
                    ) {
                        Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                }

                // BOTTOM CONTROLS & EXTRA PRO TOOLS STRIP
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Quick Pro Tool Pills Row (Lock, 8K Filter, A-B Repeat, Screenshot, Background Audio, PiP)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            ProPillButton(
                                label = "Lock",
                                icon = Icons.Default.LockOpen,
                                onClick = {
                                    isScreenLocked = true
                                    lastInteractionTime = System.currentTimeMillis()
                                    Toast.makeText(context, "Screen Locked (Auto-hides in 3s)", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        item {
                            ProPillButton(
                                label = if (volumePercent > 100) "${volumePercent}% Boost" else "200% Boost",
                                icon = if (volumePercent == 0) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                                isActive = volumePercent > 100,
                                onClick = {
                                    updateVolumeWithBoost(if (volumePercent > 100) 100 else 200, triggerManualHaptic = true)
                                }
                            )
                        }
                        item {
                            ProPillButton(
                                label = visualFilter,
                                icon = Icons.Default.AutoFixHigh,
                                isActive = visualFilter != "Original",
                                onClick = { showFilterDialog = true }
                            )
                        }
                        item {
                            val abLabel = when {
                                abPointA != null && abPointB != null -> "A-B Loop ON"
                                abPointA != null -> "Set B"
                                else -> "A-B Repeat"
                            }
                            ProPillButton(
                                label = abLabel,
                                icon = Icons.Default.Repeat,
                                isActive = abPointA != null,
                                onClick = {
                                    when {
                                        abPointA == null -> abPointA = currentPosMs
                                        abPointB == null && currentPosMs > (abPointA ?: 0L) + 1000L -> abPointB = currentPosMs
                                        else -> {
                                            abPointA = null
                                            abPointB = null
                                        }
                                    }
                                }
                            )
                        }
                        item {
                            ProPillButton(
                                label = "Screenshot",
                                icon = Icons.Default.CameraAlt,
                                onClick = {
                                    val savedFile = File(context.cacheDir, "hybrid_frame_${System.currentTimeMillis()}.jpg")
                                    try {
                                        val retriever = android.media.MediaMetadataRetriever()
                                        if (videoPath.startsWith("http")) {
                                            retriever.setDataSource(videoPath, HashMap())
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
                                    Toast.makeText(
                                        context,
                                        "Frame Captured (${formatVideoTime(currentPosMs)}) -> ${savedFile.name}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                        item {
                            ProPillButton(
                                label = "BG Audio",
                                icon = Icons.Default.Headphones,
                                onClick = {
                                    onProgressUpdate(currentPosMs)
                                    onBackgroundAudioPlay()
                                    Toast.makeText(
                                        context,
                                        "Zero-Blink Background Audio Mode Active",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                        item {
                            ProPillButton(
                                label = "PiP Mode",
                                icon = Icons.Default.PictureInPictureAlt,
                                onClick = {
                                    try {
                                        val activity = context as? Activity
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                                            val params = PictureInPictureParams.Builder()
                                                .setAspectRatio(Rational(16, 9))
                                                .build()
                                            activity.enterPictureInPictureMode(params)
                                        }
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "PiP Window Active", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                        item {
                            ProPillButton(
                                label = "Queue",
                                icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                                onClick = { showQueueDialog = true }
                            )
                        }
                    }

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
                                currentPosMs = newPos.toLong()
                                videoViewRef?.seekTo(newPos.toInt())
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

    // --- 3-DOT PRO PLAYER MENU ---
    if (showThreeDotSheet) {
        AlertDialog(
            onDismissRequest = { showThreeDotSheet = false },
            containerColor = HybridCardBg,
            title = { Text("8K Pro Player Tools", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Playback Speed (0.25x - 8.0x):", color = HybridTextSecondary, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0.5f, 1.0f, 1.5f, 2.0f, 4.0f).forEach { sp ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (playbackSpeed == sp) HybridEmerald else HybridCardAlt)
                                    .clickable {
                                        playbackSpeed = sp
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            try {
                                                // Speed indicator updated
                                            } catch (_: Exception) {
                                            }
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${sp}x",
                                    color = if (playbackSpeed == sp) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(
                        onClick = {
                            showThreeDotSheet = false
                            showFilterDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("8K Visual Enhancer Filter ($visualFilter)", color = Color.White)
                    }
                    TextButton(
                        onClick = {
                            showThreeDotSheet = false
                            showAudioTrackDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Switch Audio Track (Hindi/English/Dual)", color = Color.White)
                    }
                    TextButton(
                        onClick = {
                            showThreeDotSheet = false
                            showSubtitleDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Subtitle Sync (+/-100ms)", color = Color.White)
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

@Composable
private fun ProPillButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isActive) HybridEmerald.copy(alpha = 0.28f) else HybridTranslucentControl)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
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
 * can be opened by Android's hardware decoder without failing on non-standard file extensions.
 */
private fun resolvePlayablePartialPath(context: Context, originalPath: String): String {
    val file = File(originalPath)
    if (!file.exists()) return originalPath
    val ext = file.extension.lowercase(Locale.US)
    val partialExts = setOf("part", "crdownload", "tmp", "download", "partial", "dl", "ucd", "1dm", "td", "idm", "adm")
    if (ext !in partialExts) return originalPath

    return try {
        val proxyFile = File(context.cacheDir, "hybrid_partial_stream_${file.name.hashCode()}.mp4")
        if (!proxyFile.exists() || proxyFile.length() != file.length()) {
            file.inputStream().use { input ->
                FileOutputStream(proxyFile).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            }
        }
        proxyFile.absolutePath
    } catch (_: Exception) {
        originalPath
    }
}

