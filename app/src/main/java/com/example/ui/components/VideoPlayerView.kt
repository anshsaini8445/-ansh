package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AspectScaleMode {
    FIT, STRETCH, CROP, RATIO_16_9, RATIO_4_3
}

@Composable
fun VideoPlayerView(
    videoPath: String,
    initialPositionMs: Long = 0L,
    onProgressUpdate: (Long) -> Unit,
    onBackClick: () -> Unit,
    accentColor: Color
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // --- System Control Managers ---
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    
    // --- Playback State ---
    var videoViewInstance by remember { mutableStateOf<VideoView?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableStateOf(1L) }
    var currentProgressMs by remember { mutableStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }

    // --- Gesture HUD overlays ---
    var brightnessHudValue by remember { mutableStateOf<Float?>(null) }
    var volumeHudValue by remember { mutableStateOf<Int?>(null) }
    var seekHudValue by remember { mutableStateOf<String?>(null) }
    var hudTimeoutJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // --- Aspect ratio controls ---
    var scaleMode by remember { mutableStateOf(AspectScaleMode.FIT) }
    var showControls by remember { mutableStateOf(true) }

    // Controls timeout
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(5000)
            showControls = false
        }
    }

    // Progress poller
    LaunchedEffect(videoViewInstance, isPlaying) {
        while (true) {
            videoViewInstance?.let { vv ->
                if (vv.isPlaying) {
                    currentProgressMs = vv.currentPosition.toLong()
                    onProgressUpdate(currentProgressMs)
                }
            }
            delay(250)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // --- Core Video Native View ---
        AndroidView(
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    val vv = VideoView(ctx).apply {
                        setOnPreparedListener { mp ->
                            durationMs = mp.duration.toLong()
                            isBuffering = false
                            mp.isLooping = true
                            seekTo(initialPositionMs.toInt())
                            start()
                            isPlaying = true
                        }
                        setOnInfoListener { _, what, _ ->
                            if (what == 701) { // BUFFERING START
                                isBuffering = true
                            } else if (what == 702) { // BUFFERING END
                                isBuffering = false
                            }
                            true
                        }
                    }
                    addView(vv)
                    videoViewInstance = vv
                }
            },
            update = { frameLayout ->
                val vv = frameLayout.getChildAt(0) as? VideoView
                vv?.let {
                    val currentUri = Uri.parse(videoPath)
                    // Only update and load if path changes
                    val tag = vv.tag as? String
                    if (tag != videoPath) {
                        vv.tag = videoPath
                        isBuffering = true
                        vv.setVideoPath(videoPath)
                    }

                    // Handle aspect ratio resizing
                    val layoutParams = vv.layoutParams as FrameLayout.LayoutParams
                    when (scaleMode) {
                        AspectScaleMode.FIT -> {
                            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                            layoutParams.gravity = Gravity.CENTER
                        }
                        AspectScaleMode.STRETCH -> {
                            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                            layoutParams.gravity = Gravity.FILL
                        }
                        AspectScaleMode.CROP -> {
                            // Scale beyond layout bounds to crop
                            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                            layoutParams.gravity = Gravity.CENTER
                        }
                        AspectScaleMode.RATIO_16_9 -> {
                            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                            layoutParams.height = (frameLayout.width * 9) / 16
                            layoutParams.gravity = Gravity.CENTER
                        }
                        AspectScaleMode.RATIO_4_3 -> {
                            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                            layoutParams.height = (frameLayout.width * 3) / 4
                            layoutParams.gravity = Gravity.CENTER
                        }
                    }
                    vv.layoutParams = layoutParams
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // --- Interactive Gestures Layer ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            videoViewInstance?.let { vv ->
                                if (vv.isPlaying) {
                                    vv.pause()
                                    isPlaying = false
                                } else {
                                    vv.start()
                                    isPlaying = true
                                }
                                showControls = true
                            }
                        },
                        onTap = {
                            showControls = !showControls
                        }
                    )
                }
                .pointerInput(Unit) {
                    var totalDragY = 0f
                    var totalDragX = 0f
                    detectDragGestures(
                        onDragStart = { offset ->
                            totalDragY = 0f
                            totalDragX = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val width = size.width
                            val startX = change.position.x
                            totalDragY += dragAmount.y
                            totalDragX += dragAmount.x

                            hudTimeoutJob?.cancel()
                            hudTimeoutJob = coroutineScope.launch {
                                delay(1500)
                                brightnessHudValue = null
                                volumeHudValue = null
                                seekHudValue = null
                            }

                            if (Math.abs(totalDragY) > Math.abs(totalDragX)) {
                                // Vertical Drag
                                val percentChange = -dragAmount.y / size.height
                                if (startX < width / 2) {
                                    // --- LEFT HALF: Brightness ---
                                    val activity = context as? Activity
                                    activity?.let { act ->
                                        val attrs = act.window.attributes
                                        var currentBrightness = attrs.screenBrightness
                                        if (currentBrightness < 0) currentBrightness = 0.5f // Default
                                        val newBrightness = (currentBrightness + percentChange).coerceIn(0.01f, 1.0f)
                                        attrs.screenBrightness = newBrightness
                                        act.window.attributes = attrs
                                        brightnessHudValue = newBrightness
                                    }
                                } else {
                                    // --- RIGHT HALF: Volume ---
                                    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    val volumeStep = if (percentChange > 0) 1 else -1
                                    val newVolume = (currentVolume + volumeStep).coerceIn(0, maxVolume)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                                    volumeHudValue = newVolume
                                }
                            } else {
                                // Horizontal Drag: Scrub/Seek
                                videoViewInstance?.let { vv ->
                                    val seekChange = (dragAmount.x * 50).toInt() // Scale scrub speed
                                    val newPos = (vv.currentPosition + seekChange).coerceIn(0, durationMs.toInt())
                                    vv.seekTo(newPos)
                                    currentProgressMs = newPos.toLong()
                                    seekHudValue = if (seekChange > 0) ">> Fast Forward" else "<< Rewind"
                                }
                            }
                        },
                        onDragEnd = {
                            // Retain current states or timeout
                        }
                    )
                }
        )

        // --- Gesture Indicator HUD HUD Overlay ---
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Brightness HUD
            brightnessHudValue?.let { valPct ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Brightness6, contentDescription = "Brightness", tint = accentColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Brightness ${String.format("%.0f%%", valPct * 100)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }
                }
            }

            // Volume HUD
            volumeHudValue?.let { volIdx ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (volIdx == 0) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                            contentDescription = "Volume",
                            tint = accentColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Volume ${String.format("%.0f%%", (volIdx.toFloat() / maxVolume) * 100)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }
                }
            }

            // Seek HUD
            seekHudValue?.let { text ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(16.dp)
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
        }

        // --- Custom HUD Buffering Loader ---
        if (isBuffering) {
            CircularProgressIndicator(
                color = accentColor,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // --- Custom Media Controls Overlay ---
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .statusBarsPadding()
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MAX Premium Player",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    // Scale Mode Cycle Button
                    IconButton(
                        onClick = {
                            scaleMode = when (scaleMode) {
                                AspectScaleMode.FIT -> AspectScaleMode.STRETCH
                                AspectScaleMode.STRETCH -> AspectScaleMode.CROP
                                AspectScaleMode.CROP -> AspectScaleMode.RATIO_16_9
                                AspectScaleMode.RATIO_16_9 -> AspectScaleMode.RATIO_4_3
                                AspectScaleMode.RATIO_4_3 -> AspectScaleMode.FIT
                            }
                        }
                    ) {
                        Icon(
                            imageVector = when (scaleMode) {
                                AspectScaleMode.FIT -> Icons.Default.AspectRatio
                                AspectScaleMode.STRETCH -> Icons.Default.Fullscreen
                                AspectScaleMode.CROP -> Icons.Default.CenterFocusStrong
                                else -> Icons.Default.Crop
                            },
                            contentDescription = "Aspect Ratio",
                            tint = Color.White
                        )
                    }
                }

                // Bottom Seek & Controls Panel
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding()
                        .align(Alignment.BottomCenter)
                ) {
                    // Timeline Slider & Time Markers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val totalSecs = durationMs / 1000
                        val currentSecs = currentProgressMs / 1000
                        val formattedCurrent = String.format("%02d:%02d", currentSecs / 60, currentSecs % 60)
                        val formattedTotal = String.format("%02d:%02d", totalSecs / 60, totalSecs % 60)

                        Text(text = formattedCurrent, color = Color.White, style = MaterialTheme.typography.bodySmall)
                        
                        Slider(
                            value = currentProgressMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                            onValueChange = { valPos ->
                                videoViewInstance?.seekTo(valPos.toInt())
                                currentProgressMs = valPos.toLong()
                            },
                            valueRange = 0f..durationMs.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = accentColor,
                                activeTrackColor = accentColor,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )

                        Text(text = formattedTotal, color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }

                    // Main Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                videoViewInstance?.let { vv ->
                                    val newPos = (vv.currentPosition - 10000).coerceAtLeast(0)
                                    vv.seekTo(newPos)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(28.dp))
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        FloatingActionButton(
                            onClick = {
                                videoViewInstance?.let { vv ->
                                    if (vv.isPlaying) {
                                        vv.pause()
                                        isPlaying = false
                                    } else {
                                        vv.start()
                                        isPlaying = true
                                    }
                                }
                            },
                            containerColor = accentColor,
                            contentColor = Color.Black,
                            shape = RoundedCornerShape(50)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause"
                            )
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        IconButton(
                            onClick = {
                                videoViewInstance?.let { vv ->
                                    val newPos = (vv.currentPosition + 10000).coerceAtMost(durationMs.toInt())
                                    vv.seekTo(newPos)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    }
}
