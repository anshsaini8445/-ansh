package com.example.ui.screens

import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.helpers.*
import com.example.ui.components.LiquidGlassBox
import com.example.ui.components.LiquidGlassEqualizerWidget
import com.example.ui.components.LiquidGlassIconOrb
import com.example.ui.components.LiquidGlassShakeOrbWidget
import com.example.ui.components.LiquidGlassSpeedDialOrb
import com.example.ui.components.LiquidGlassVolumeBoosterWidget
import com.example.ui.theme.*
import com.example.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerScreen(
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit,
    onOpenEqualizer: () -> Unit = {},
    onOpenTurboShare: () -> Unit = {},
    accentColor: Color = HybridEmerald
) {
    val context = LocalContext.current
    val hostView = LocalView.current
    val tracks by viewModel.musicItems.collectAsState()
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackPos by viewModel.playbackPosition.collectAsState()
    val speed by viewModel.playbackSpeed.collectAsState()
    val is200Boost by viewModel.is200BoostEnabled.collectAsState()
    val audioVolumePct by viewModel.audioVolumePercent.collectAsState()
    val doubleShakeEnabled by viewModel.doubleShakeEnabled.collectAsState()
    val active3DThemeName by viewModel.active3DMusicTheme.collectAsState()
    val syncedLyrics by viewModel.syncedLyrics.collectAsState()
    val frequencies by viewModel.visualizerFrequencies.collectAsState()
    val isShuffle by viewModel.isShuffleMode.collectAsState()
    val favorites by viewModel.favoriteItems.collectAsState()
    val pointA by viewModel.abPointAMs.collectAsState()
    val pointB by viewModel.abPointBMs.collectAsState()
    val sleepMinutes by viewModel.sleepTimerMinutes.collectAsState()
    val activePitchPreset by viewModel.activePitchPreset.collectAsState()
    val eqBands by viewModel.equalizerBands.collectAsState()

    val currentTrack = nowPlaying ?: tracks.firstOrNull() ?: return
    val current3DTheme = remember(active3DThemeName) {
        Music3DThemeEngine.getByName(active3DThemeName)
    }
    val themePrimary = current3DTheme.primaryColor

    val currentIndex = remember(tracks, currentTrack) {
        (tracks.indexOfFirst { it.id == currentTrack.id }.takeIf { it >= 0 } ?: 0) + 1
    }
    val nextTrack = remember(tracks, currentTrack) {
        val idx = tracks.indexOfFirst { it.id == currentTrack.id }
        if (idx >= 0 && idx < tracks.lastIndex) tracks[idx + 1] else tracks.firstOrNull()
    }
    val isFavorite = favorites.any { it.id == currentTrack.id }

    // Center View Mode: "COVER_3D", "LYRICS", "STAGE_3D"
    var centerDisplayMode by remember { mutableStateOf("COVER_3D") }
    var isVinylDiscMode by remember { mutableStateOf(true) }
    var coverSwipeOffsetX by remember { mutableStateOf(0f) }
    var showStudioMenu by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showPitchLabDialog by remember { mutableStateOf(false) }
    var show3DThemesDialog by remember { mutableStateOf(false) }
    var showCustomLrcDialog by remember { mutableStateOf(false) }

    // Screenshot-Style Vertical Volume Pill HUD visibility & auto-hide timer
    var showVerticalVolumeHud by remember { mutableStateOf(false) }
    var lastVolumeHudInteractionMs by remember { mutableStateOf(0L) }

    LaunchedEffect(showVerticalVolumeHud, lastVolumeHudInteractionMs) {
        if (showVerticalVolumeHud) {
            delay(2000L)
            if (System.currentTimeMillis() - lastVolumeHudInteractionMs >= 1950L) {
                showVerticalVolumeHud = false
            }
        }
    }

    // Observe hardware volume button presses so the Vertical Pill HUD pops up automatically!
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                viewModel.syncSystemAudioVolumeFromHardware()
                lastVolumeHudInteractionMs = System.currentTimeMillis()
                showVerticalVolumeHud = true
            }
        }
        try {
            context.contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                observer
            )
        } catch (_: Exception) {
        }
        onDispose {
            try {
                context.contentResolver.unregisterContentObserver(observer)
            } catch (_: Exception) {
            }
        }
    }

    val animatedSwipeOffset by animateFloatAsState(
        targetValue = coverSwipeOffsetX,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "cover_bounce"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_spin")
    val discRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing)
        ),
        label = "disc_angle"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HybridDarkBg)
            // Vertical Swipe on Right Half adjusts Volume (0%..200%) & shows Screenshot-Style Vertical Pill HUD!
            .pointerInput(Unit) {
                var startX = 0f
                var isRightSideVerticalSwipe = false
                detectDragGestures(
                    onDragStart = { offset ->
                        startX = offset.x
                        isRightSideVerticalSwipe = offset.x > size.width * 0.58f
                    },
                    onDragEnd = {
                        isRightSideVerticalSwipe = false
                    },
                    onDragCancel = {
                        isRightSideVerticalSwipe = false
                    },
                    onDrag = { change, dragAmount ->
                        if (isRightSideVerticalSwipe && abs(dragAmount.y) > abs(dragAmount.x) * 1.15f) {
                            change.consume()
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val (nextVol, pulse) = VideoGestureHelper.computeVolumeStep(
                                currentVolume = viewModel.audioVolumePercent.value,
                                dragDy = dragAmount.y,
                                viewportHeight = h
                            )
                            if (pulse != null) {
                                VideoGestureHelper.triggerHapticPulse(context, hostView, pulse)
                            }
                            viewModel.setAudioVolumeWithBoost(nextVol)
                            lastVolumeHudInteractionMs = System.currentTimeMillis()
                            showVerticalVolumeHud = true
                        }
                    }
                )
            }
    ) {
        // --- 15 3D ANIMATED THEME BACKDROP CANVAS ---
        Music3DAnimatedBackdrop(
            themeSpec = current3DTheme,
            isPlaying = isPlaying,
            frequencies = frequencies,
            modifier = Modifier.fillMaxSize()
        )

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Now Playing ($currentIndex/${tracks.size.coerceAtLeast(1)})",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "3D Theme: ${current3DTheme.name}",
                                color = themePrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            // 3D Theme Palette Liquid Glass Orb
                            LiquidGlassIconOrb(
                                icon = Icons.Default.Palette,
                                contentDescription = "15 3D Themes",
                                tintColor = themePrimary,
                                size = 38.dp,
                                iconSize = 18.dp,
                                isActive = true,
                                onClick = { show3DThemesDialog = true }
                            )
                            // Visual Liquid Glass Volume Booster Component (No text label!)
                            LiquidGlassVolumeBoosterWidget(
                                isBoostEnabled = is200Boost,
                                volumePercent = audioVolumePct,
                                size = 38.dp,
                                onClick = {
                                    lastVolumeHudInteractionMs = System.currentTimeMillis()
                                    showVerticalVolumeHud = !showVerticalVolumeHud
                                }
                            )
                            // Visual Liquid Glass Equalizer Component (No text label!)
                            LiquidGlassEqualizerWidget(
                                bands = eqBands,
                                isPlaying = isPlaying,
                                size = 38.dp,
                                onClick = onOpenEqualizer
                            )
                            // 3-Dot Studio Menu Liquid Glass Orb
                            LiquidGlassIconOrb(
                                icon = Icons.Default.MoreVert,
                                contentDescription = "Studio 3-Dot Menu",
                                tintColor = Color.White,
                                size = 38.dp,
                                iconSize = 18.dp,
                                onClick = { showStudioMenu = true }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // --- MODE SWITCHER STRIP: 3D Cover | Synced Lyrics | 3D Stage + 2x Shake Vibration Toggle ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0x99111927))
                            .border(1.dp, HybridCardBorder, RoundedCornerShape(22.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            "COVER_3D" to "3D Cover",
                            "LYRICS" to "Lyrics",
                            "STAGE_3D" to "3D Stage"
                        ).forEach { (modeKey, label) ->
                            val selected = centerDisplayMode == modeKey
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (selected) themePrimary else Color.Transparent)
                                    .clickable { centerDisplayMode = modeKey }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("music_mode_${modeKey.lowercase()}")
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) Color.Black else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    // Front-and-Center Double-Shake ("2x Shake") Vibration Toggle Pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (doubleShakeEnabled) themePrimary.copy(alpha = 0.22f)
                                else Color(0x99111927)
                            )
                            .border(
                                1.dp,
                                if (doubleShakeEnabled) themePrimary else HybridCardBorder,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                viewModel.toggleDoubleShakeEnabled()
                                val status = if (viewModel.doubleShakeEnabled.value) "ON" else "OFF"
                                Toast.makeText(
                                    context,
                                    "📳 2x Shake Vibration to Skip Song: $status",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("double_shake_toggle_audio_player"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Vibration,
                            contentDescription = "2x Shake Vibration",
                            tint = if (doubleShakeEnabled) themePrimary else HybridTextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (doubleShakeEnabled) "2x Shake ON" else "2x Shake OFF",
                            color = if (doubleShakeEnabled) themePrimary else HybridTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // --- CENTER STAGE: 3D Cover Art OR Scrollable Synced Lyrics OR Full 3D Visualizer Stage ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(276.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (centerDisplayMode) {
                        "LYRICS" -> {
                            SyncedLyricsScrollableComponent(
                                lyrics = syncedLyrics,
                                currentPositionMs = playbackPos,
                                accentColor = themePrimary,
                                onSeekToLine = { timeMs ->
                                    VideoGestureHelper.triggerHapticPulse(context, hostView, HapticPulseType.STEP_TICK)
                                    viewModel.seekTo(timeMs)
                                },
                                onEditLrcClick = { showCustomLrcDialog = true },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("synced_lyrics_scrollable_view")
                            )
                        }

                        "STAGE_3D" -> {
                            // Interactive 3D Theme Showcase Card where tapping cycles the 15 3D Themes
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(24.dp))
                                    .border(1.dp, themePrimary.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                                    .clickable { viewModel.cycleNext3DMusicTheme() }
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "✨ ${current3DTheme.name} (#${current3DTheme.id}/15)",
                                        color = themePrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "Tap to Cycle 3D",
                                        color = Color.White.copy(alpha = 0.75f),
                                        fontSize = 11.sp
                                    )
                                }

                                // Live current synced lyric banner inside 3D Stage
                                val activeLyricIdx = SyncedLyricsEngine.findActiveLyricIndex(syncedLyrics, playbackPos)
                                val currentLyricText = syncedLyrics.getOrNull(activeLyricIdx)?.text ?: currentTrack.cleanTitle
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0x99080D16))
                                        .border(1.dp, themePrimary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                        .clickable { centerDisplayMode = "LYRICS" }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = currentLyricText,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        else -> {
                            // 3D Cover / Spinning Vinyl + Live Mini Synced Lyric Pill below
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(222.dp)
                                        .offset { IntOffset(animatedSwipeOffset.roundToInt(), 0) }
                                        .clip(if (isVinylDiscMode) CircleShape else RoundedCornerShape(26.dp))
                                        .border(
                                            width = if (isVinylDiscMode) 5.dp else 1.5.dp,
                                            color = if (isVinylDiscMode) themePrimary.copy(alpha = 0.8f) else HybridCardBorder,
                                            shape = if (isVinylDiscMode) CircleShape else RoundedCornerShape(26.dp)
                                        )
                                        .clickable { isVinylDiscMode = !isVinylDiscMode }
                                        .pointerInput(Unit) {
                                            detectHorizontalDragGestures(
                                                onDragEnd = {
                                                    if (coverSwipeOffsetX > 90f) {
                                                        viewModel.playPrevious()
                                                    } else if (coverSwipeOffsetX < -90f) {
                                                        viewModel.playNext()
                                                    }
                                                    coverSwipeOffsetX = 0f
                                                },
                                                onDragCancel = { coverSwipeOffsetX = 0f },
                                                onHorizontalDrag = { change, dragAmount ->
                                                    change.consume()
                                                    coverSwipeOffsetX = (coverSwipeOffsetX + dragAmount).coerceIn(-160f, 160f)
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    GuaranteedAlbumArt(
                                        track = currentTrack,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .then(
                                                if (isVinylDiscMode && isPlaying) Modifier.rotate(discRotation) else Modifier
                                            )
                                    )

                                    if (isVinylDiscMode) {
                                        Box(
                                            modifier = Modifier
                                                .size(50.dp)
                                                .clip(CircleShape)
                                                .background(HybridDarkBg)
                                                .border(3.5.dp, themePrimary, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Live 1-Line Synced Lyric Preview Pill (Tapping opens full scrollable Synced Lyrics)
                                val activeIdx = SyncedLyricsEngine.findActiveLyricIndex(syncedLyrics, playbackPos)
                                val activeLine = syncedLyrics.getOrNull(activeIdx)?.text ?: "Tap to view Synced Lyrics"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0x99111927))
                                        .border(1.dp, themePrimary.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                        .clickable { centerDisplayMode = "LYRICS" }
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lyrics,
                                        contentDescription = "Synced Lyrics",
                                        tint = themePrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = activeLine,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // --- 15 3D THEMES HORIZONTAL QUICK BAR ---
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(Music3DThemeEngine.THEMES_3D, key = { it.id }) { spec ->
                        val active = spec.name == current3DTheme.name
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (active) spec.primaryColor.copy(alpha = 0.25f)
                                    else Color(0x88111927)
                                )
                                .border(
                                    1.dp,
                                    if (active) spec.primaryColor else HybridCardBorder,
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { viewModel.select3DMusicTheme(spec.name) }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(spec.primaryColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = spec.name,
                                color = if (active) spec.primaryColor else Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        }
                    }
                }

                // --- FULL-WIDTH SONG TITLE & ARTIST ---
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = currentTrack.cleanTitle,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${currentTrack.artist} • ${currentTrack.albumName} • Vol $audioVolumePct%",
                        color = HybridTextSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // --- SECONDARY LIQUID GLASS VISUAL COMPONENTS STRIP (Zero Text Clutter!) ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Liquid Glass Heart Orb
                    LiquidGlassIconOrb(
                        icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tintColor = if (isFavorite) HybridCoralRed else Color.White,
                        size = 44.dp,
                        iconSize = 21.dp,
                        isActive = isFavorite,
                        onClick = { viewModel.toggleFavorite(currentTrack) }
                    )

                    // 2. Visual Liquid Glass Equalizer Component (Opens Equalizer on click, no text!)
                    LiquidGlassEqualizerWidget(
                        bands = eqBands,
                        isPlaying = isPlaying,
                        size = 44.dp,
                        onClick = onOpenEqualizer
                    )

                    // 3. Visual Liquid Glass Volume Booster Component (Toggles 200% boost + opens Volume HUD, no text!)
                    LiquidGlassVolumeBoosterWidget(
                        isBoostEnabled = is200Boost,
                        volumePercent = audioVolumePct,
                        size = 44.dp,
                        onClick = {
                            viewModel.toggle200Boost()
                            lastVolumeHudInteractionMs = System.currentTimeMillis()
                            showVerticalVolumeHud = true
                        }
                    )

                    // 4. Visual Liquid Glass Speed Dial Component
                    LiquidGlassSpeedDialOrb(
                        speed = speed,
                        size = 44.dp,
                        onClick = {
                            val nextSpeed = when (speed) {
                                0.75f -> 1.0f
                                1.0f -> 1.25f
                                1.25f -> 1.5f
                                1.5f -> 2.0f
                                else -> 0.75f
                            }
                            viewModel.setPlaybackSpeed(nextSpeed)
                        }
                    )

                    // 5. Visual Liquid Glass 2x Shake Vibration Orb
                    LiquidGlassShakeOrbWidget(
                        isEnabled = doubleShakeEnabled,
                        size = 44.dp,
                        onClick = {
                            viewModel.executeDoubleShakeNextSong()
                        }
                    )

                    // 6. Liquid Glass Share Orb
                    LiquidGlassIconOrb(
                        icon = Icons.Default.Share,
                        contentDescription = "Quick Share",
                        tintColor = Color(0xFF00E5FF),
                        size = 44.dp,
                        iconSize = 20.dp,
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Listening to ${currentTrack.cleanTitle} on Hybrid Player")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Track"))
                        }
                    )
                }

                // --- SINGLE SLEEK TIMELINE SEEKBAR + TIMESTAMPS ---
                Column(modifier = Modifier.fillMaxWidth()) {
                    val totalDuration = currentTrack.duration.coerceAtLeast(1000L)
                    Slider(
                        value = playbackPos.toFloat().coerceIn(0f, totalDuration.toFloat()),
                        onValueChange = { viewModel.seekTo(it.toLong()) },
                        valueRange = 0f..totalDuration.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = themePrimary,
                            activeTrackColor = themePrimary,
                            inactiveTrackColor = Color(0x33FFFFFF)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatMs(playbackPos),
                            color = HybridTextSecondary,
                            fontSize = 12.sp
                        )
                        if (pointA != null) {
                            Text(
                                text = "A-B Loop Active",
                                color = themePrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = currentTrack.formattedDuration,
                            color = HybridTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                // --- BORDERLESS TRANSPORT ROW ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.toggleShuffleMode() }) {
                        Icon(
                            imageVector = if (isShuffle) Icons.Default.Shuffle else Icons.Default.Repeat,
                            contentDescription = "Shuffle / Loop",
                            tint = if (isShuffle) themePrimary else Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.playPrevious() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Prominent Center Play/Pause Circle
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .clip(CircleShape)
                            .background(themePrimary)
                            .clickable { viewModel.togglePlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.Black,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.playNext() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(onClick = { showQueueSheet = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Playing Queue",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // --- BOTTOM "UP NEXT" STRIP ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xCC111927))
                        .border(1.dp, HybridCardBorder, RoundedCornerShape(14.dp))
                        .clickable { showQueueSheet = true }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "UP NEXT",
                        color = themePrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = nextTrack?.cleanTitle ?: "End of Queue",
                        color = Color.White,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Open Queue",
                        tint = HybridTextSecondary
                    )
                }
            }
        }

        // --- SCREENSHOT-STYLE VERTICAL VOLUME PILL HUD OVERLAY (Right Side of Audio Player) ---
        VerticalVolumeBrightnessPillHud(
            visible = showVerticalVolumeHud,
            isVolume = true,
            percentValue = audioVolumePct,
            maxValue = 200,
            accentColor = themePrimary,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 18.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val delta = ((-dragAmount.y / 220f) * 140f).toInt()
                        if (delta != 0) {
                            viewModel.setAudioVolumeWithBoost((viewModel.audioVolumePercent.value + delta).coerceIn(0, 200))
                            lastVolumeHudInteractionMs = System.currentTimeMillis()
                        }
                    }
                }
        )
    }

    // --- 15 3D ANIMATED THEMES PICKER DIALOG ---
    if (show3DThemesDialog) {
        AlertDialog(
            onDismissRequest = { show3DThemesDialog = false },
            containerColor = HybridCardBg,
            title = {
                Text(
                    "15 3D Animated Music Themes",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(Music3DThemeEngine.THEMES_3D, key = { it.id }) { spec ->
                        val active = spec.name == current3DTheme.name
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) spec.primaryColor.copy(alpha = 0.2f) else HybridCardAlt)
                                .border(1.dp, if (active) spec.primaryColor else HybridCardBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.select3DMusicTheme(spec.name)
                                    show3DThemesDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(listOf(spec.primaryColor, spec.secondaryColor))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${spec.id}",
                                    color = Color.Black,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(spec.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(spec.subtitle, color = HybridTextSecondary, fontSize = 11.sp)
                            }
                            if (active) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = spec.primaryColor)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { show3DThemesDialog = false }) {
                    Text("Done", color = themePrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // --- CUSTOM .LRC SYNCED LYRICS EDITOR DIALOG ---
    if (showCustomLrcDialog) {
        var customLrcInput by remember {
            mutableStateOf(
                syncedLyrics.joinToString("\n") { "[${it.formattedTimestamp}.00]${it.text}" }
            )
        }
        AlertDialog(
            onDismissRequest = { showCustomLrcDialog = false },
            containerColor = HybridCardBg,
            title = { Text("Edit / Paste .LRC Synced Lyrics", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Format: [mm:ss.xx]Lyric Line (Tap any line to seek!)",
                        color = HybridTextSecondary,
                        fontSize = 11.sp
                    )
                    OutlinedTextField(
                        value = customLrcInput,
                        onValueChange = { customLrcInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveCustomLyricsForCurrentTrack(customLrcInput)
                        showCustomLrcDialog = false
                        Toast.makeText(context, "Synced Lyrics Updated!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = themePrimary)
                ) {
                    Text("Save .LRC", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomLrcDialog = false }) {
                    Text("Cancel", color = HybridTextSecondary)
                }
            }
        )
    }

    // --- 3-DOT AUDIO STUDIO MENU ---
    if (showStudioMenu) {
        AlertDialog(
            onDismissRequest = { showStudioMenu = false },
            containerColor = HybridCardBg,
            title = {
                Text("Audio Studio Menu", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioTileButton("5-Band EQ", Icons.Default.Equalizer, Modifier.weight(1f)) {
                            showStudioMenu = false
                            onOpenEqualizer()
                        }
                        StudioTileButton("Pitch Lab", Icons.Default.Tune, Modifier.weight(1f)) {
                            showStudioMenu = false
                            showPitchLabDialog = true
                        }
                        StudioTileButton(
                            label = if (sleepMinutes > 0) "${sleepMinutes}m Timer" else "Sleep Timer",
                            icon = Icons.Default.Bedtime,
                            modifier = Modifier.weight(1f)
                        ) {
                            val nextTimer = when (sleepMinutes) {
                                0 -> 15
                                15 -> 30
                                30 -> 60
                                else -> 0
                            }
                            viewModel.setSleepTimer(nextTimer)
                            Toast.makeText(
                                context,
                                if (nextTimer > 0) "Sleep Timer: $nextTimer min" else "Sleep Timer Off",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioTileButton(
                            label = if (doubleShakeEnabled) "2x Shake ON" else "2x Shake OFF",
                            icon = Icons.Default.Vibration,
                            modifier = Modifier.weight(1f)
                        ) {
                            viewModel.toggleDoubleShakeEnabled()
                        }
                        StudioTileButton(
                            label = "15 3D Themes",
                            icon = Icons.Default.Palette,
                            modifier = Modifier.weight(1f)
                        ) {
                            showStudioMenu = false
                            show3DThemesDialog = true
                        }
                        StudioTileButton("Synced Lyrics", Icons.Default.Lyrics, Modifier.weight(1f)) {
                            showStudioMenu = false
                            centerDisplayMode = "LYRICS"
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioTileButton("A-B Repeat", Icons.Default.RepeatOne, Modifier.weight(1f)) {
                            viewModel.toggleAbRepeatPoint()
                            Toast.makeText(context, "A-B Repeat Point Updated", Toast.LENGTH_SHORT).show()
                        }
                        StudioTileButton("Disc/Cover", Icons.Default.Album, Modifier.weight(1f)) {
                            isVinylDiscMode = !isVinylDiscMode
                            centerDisplayMode = "COVER_3D"
                            showStudioMenu = false
                        }
                        StudioTileButton("Turbo Share", Icons.Default.Bolt, Modifier.weight(1f)) {
                            showStudioMenu = false
                            onOpenTurboShare()
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioTileButton(
                            label = if (is200Boost) "Boost ON" else "200% Boost",
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            modifier = Modifier.weight(1f)
                        ) {
                            viewModel.toggle200Boost()
                            lastVolumeHudInteractionMs = System.currentTimeMillis()
                            showVerticalVolumeHud = true
                        }
                        StudioTileButton("Vault Lock", Icons.Default.Lock, Modifier.weight(1f)) {
                            viewModel.toggleVaultLock(currentTrack)
                            showStudioMenu = false
                            Toast.makeText(context, "Locked in Privacy Vault", Toast.LENGTH_SHORT).show()
                        }
                        StudioTileButton("Delete/Trash", Icons.Default.DeleteOutline, Modifier.weight(1f)) {
                            viewModel.moveToRecycleBin(currentTrack)
                            showStudioMenu = false
                            onBackClick()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStudioMenu = false }) {
                    Text("Close", color = themePrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // --- SOUND & PITCH LAB DIALOG ---
    if (showPitchLabDialog) {
        AlertDialog(
            onDismissRequest = { showPitchLabDialog = false },
            containerColor = HybridCardBg,
            title = { Text("Sound & Pitch Lab", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioPitchHelper.PRESETS.forEach { preset ->
                        val active = activePitchPreset == preset.name
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) themePrimary.copy(alpha = 0.2f) else HybridCardAlt)
                                .border(1.dp, if (active) themePrimary else HybridCardBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.applyPitchPreset(preset.name, preset.speed, preset.pitch)
                                    showPitchLabDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(preset.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(preset.subtitle, color = HybridTextSecondary, fontSize = 11.sp)
                            }
                            if (active) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = themePrimary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPitchLabDialog = false }) {
                    Text("Done", color = themePrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // --- PLAYING QUEUE DIALOG ---
    if (showQueueSheet) {
        AlertDialog(
            onDismissRequest = { showQueueSheet = false },
            containerColor = HybridCardBg,
            title = { Text("Up Next Queue (${tracks.size})", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tracks, key = { it.id }) { item ->
                        val active = item.id == currentTrack.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) themePrimary.copy(alpha = 0.16f) else HybridCardAlt)
                                .clickable {
                                    viewModel.playMusic(item)
                                    showQueueSheet = false
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (active) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = if (active) themePrimary else HybridTextSecondary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.cleanTitle,
                                    color = if (active) themePrimary else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(item.artist, color = HybridTextSecondary, fontSize = 11.sp)
                            }
                            Text(item.formattedDuration, color = HybridTextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQueueSheet = false }) {
                    Text("Close", color = themePrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

/**
 * Scrollable Synced Lyrics Display Component that highlights lines based on current playback time,
 * auto-scrolls smoothly to keep the active line centered, and supports tap-to-seek on any line.
 */
@Composable
fun SyncedLyricsScrollableComponent(
    lyrics: List<SyncedLyricLine>,
    currentPositionMs: Long,
    accentColor: Color,
    onSeekToLine: (Long) -> Unit,
    onEditLrcClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val activeIndex = remember(lyrics, currentPositionMs) {
        SyncedLyricsEngine.findActiveLyricIndex(lyrics, currentPositionMs)
    }

    // Automatically smooth-scroll the LazyColumn so the active lyric line stays centered
    LaunchedEffect(activeIndex, lyrics.size) {
        if (lyrics.isNotEmpty() && activeIndex in lyrics.indices) {
            val targetScrollIndex = (activeIndex - 1).coerceAtLeast(0)
            try {
                listState.animateScrollToItem(targetScrollIndex)
            } catch (_: Exception) {
            }
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xB3090E17))
            .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // Header Row inside Synced Lyrics View
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lyrics,
                    contentDescription = "Synced Lyrics",
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "LIVE SYNCED LYRICS (Tap Line to Seek)",
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Text(
                text = "Edit .LRC",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x33FFFFFF))
                    .clickable(onClick = onEditLrcClick)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(lyrics, key = { idx, item -> "${idx}_${item.timeMs}" }) { index, line ->
                val isCurrent = index == activeIndex
                val isPassed = index < activeIndex

                val scale by animateFloatAsState(
                    targetValue = if (isCurrent) 1.03f else 0.96f,
                    animationSpec = tween(220),
                    label = "lyric_scale"
                )
                val lineColor by animateColorAsState(
                    targetValue = when {
                        isCurrent -> Color.White
                        isPassed -> Color.White.copy(alpha = 0.42f)
                        else -> Color.White.copy(alpha = 0.65f)
                    },
                    animationSpec = tween(220),
                    label = "lyric_color"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer(scaleX = scale, scaleY = scale)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isCurrent) accentColor.copy(alpha = 0.22f)
                            else Color.Transparent
                        )
                        .border(
                            width = if (isCurrent) 1.dp else 0.dp,
                            color = if (isCurrent) accentColor else Color.Transparent,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onSeekToLine(line.timeMs) }
                        .padding(horizontal = 12.dp, vertical = if (isCurrent) 10.dp else 6.dp)
                        .testTag("lyric_line_$index"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = line.formattedTimestamp,
                        color = if (isCurrent) accentColor else HybridTextSecondary.copy(alpha = 0.65f),
                        fontSize = 11.sp,
                        fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Medium,
                        modifier = Modifier.width(42.dp)
                    )

                    Text(
                        text = line.text,
                        color = lineColor,
                        fontSize = if (isCurrent) 16.sp else 13.sp,
                        fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Medium,
                        lineHeight = if (isCurrent) 21.sp else 18.sp,
                        modifier = Modifier.weight(1f)
                    )

                    if (isCurrent) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Active Line",
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioTileButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(HybridCardAlt)
            .border(1.dp, HybridCardBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSecs = (ms / 1000).coerceAtLeast(0L)
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
