package com.example.ui.screens

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.MediaItem
import com.example.helpers.GlobalScenicThemeBackdrop
import com.example.helpers.Music3DAnimatedBackdrop
import com.example.helpers.Music3DThemeEngine
import com.example.helpers.Music3DThemeSpec
import com.example.helpers.SyncedLyricLine
import com.example.helpers.SyncedLyricsEngine
import com.example.helpers.VerticalVolumeBrightnessPillHud
import com.example.helpers.WifiDirectTransferEngine
import com.example.ui.components.*
import com.example.ui.menus.MediaSevenActionDialog
import com.example.ui.theme.*
import com.example.viewmodel.PlayerViewModel
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private enum class CenterDisplayMode {
    TURNTABLE, LYRICS
}

/**
 * Clean, Streamlined Full-Screen Now Playing Music & Video-Audio Studio:
 * - Top Bar: ONLY Back Button (←), "Now Playing (x/y)" title, and compact Disc/Lyrics toggle.
 * - Center Stage: Rotating Vinyl Turntable with Real Album Art OR Interactive Synced Lyrics.
 * - 3D Visualizer: Only animates when `visualizer3DEnabled` is turned ON inside the 3-Dot menu.
 * - Middle Action Row (5 Thumb-Friendly Buttons right above Seekbar):
 *   1. Favourite (❤️)
 *   2. Equalizer (🎚️)
 *   3. Sleep Timer (⏱️ — 5m, 10m, 20m, 45m, 60m + Custom Minutes input)
 *   4. 2x Shake (📳 — Single place, never duplicated!)
 *   5. 3-Dot Menu (⋮ — Placed on bottom-right for easy thumb reach! Houses Share, 3D Visualizer ON/OFF + 15 Themes, 200% Volume Boost, Speed/Pitch, A-B Repeat, Vault, Delete).
 * - Bottom Transport Row:
 *   - Left ("Ulte Haath"): Shuffle (🔀)
 *   - Center: Previous (⏮), Play/Pause (▶/⏸), Next (⏭)
 *   - Right ("Seedhe Haath"): Repeat (🔁 / 🔂 — Repeat All / Repeat One / Order)
 * - Up Next Queue Bar & Dialog: Displays real album art / video frame thumbnails (`GuaranteedAlbumArt`) for every song!
 */
@Composable
fun MusicPlayerScreen(
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    onOpenTurboShare: () -> Unit = {},
    accentColor: Color = HybridEmerald,
    onBack: () -> Unit = onBackClick,
    onOpenHybridShareForItem: (MediaItem) -> Unit = { _ -> onOpenTurboShare() }
) {
    val context = LocalContext.current
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val musicItems by viewModel.musicItems.collectAsState()
    val videoItems by viewModel.videoItems.collectAsState()
    val favoriteItems by viewModel.favoriteItems.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackPosition by viewModel.playbackPosition.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val activePitchPreset by viewModel.activePitchPreset.collectAsState()
    val audioVolumePercent by viewModel.audioVolumePercent.collectAsState()
    val doubleShakeEnabled by viewModel.doubleShakeEnabled.collectAsState()
    val shakeSensitivity by viewModel.shakeSensitivity.collectAsState()
    val isShuffleMode by viewModel.isShuffleMode.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val sleepTimerMinutes by viewModel.sleepTimerMinutes.collectAsState()
    val sleepTimerRemainingMs by viewModel.sleepTimerRemainingMs.collectAsState()
    val djCrossfadeEnabled by viewModel.djCrossfadeEnabled.collectAsState()
    val isDjCrossfadingNow by viewModel.isDjCrossfadingNow.collectAsState()
    val audioBalanceMode by viewModel.audioBalanceMode.collectAsState()
    val bassBoostEnabled by viewModel.bassBoostEnabled.collectAsState()
    val equalizerPreset by viewModel.equalizerPreset.collectAsState()
    val scenicBackdropPreset by viewModel.scenicBackdropPreset.collectAsState()
    val customWallpaperUri by viewModel.customWallpaperUri.collectAsState()
    val abPointAMs by viewModel.abPointAMs.collectAsState()
    val abPointBMs by viewModel.abPointBMs.collectAsState()
    val syncedLyrics by viewModel.syncedLyrics.collectAsState()
    val active3DThemeName by viewModel.active3DMusicTheme.collectAsState()
    val visualizer3DEnabled by viewModel.visualizer3DEnabled.collectAsState()
    val frequencies by viewModel.visualizerFrequencies.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()

    val isHindi = remember(selectedLanguage) {
        selectedLanguage.contains("Hindi", ignoreCase = true) ||
            selectedLanguage.contains("हिन्दी")
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = configuration.screenWidthDp >= 600

    BackHandler(onBack = onBack)

    val activeQueue = remember(nowPlaying, musicItems, videoItems) {
        if (nowPlaying?.isVideo == true) {
            (musicItems + videoItems).distinctBy { it.id }
        } else {
            musicItems.ifEmpty { videoItems }
        }
    }

    val currentTrack = nowPlaying ?: activeQueue.firstOrNull()
    val currentTrackIndex = remember(currentTrack, activeQueue) {
        val idx = activeQueue.indexOfFirst { it.id == currentTrack?.id }
        if (idx >= 0) idx else 0
    }
    val nextTrackInQueue = remember(currentTrackIndex, activeQueue) {
        if (activeQueue.size > 1) {
            activeQueue[(currentTrackIndex + 1) % activeQueue.size]
        } else {
            currentTrack
        }
    }

    val isFavorite = remember(currentTrack, favoriteItems) {
        currentTrack != null && favoriteItems.any { it.id == currentTrack.id }
    }

    val themeSpec = remember(active3DThemeName) {
        Music3DThemeEngine.getByName(active3DThemeName)
    }

    var centerMode by remember { mutableStateOf(CenterDisplayMode.TURNTABLE) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showMoreOptionsSheet by remember { mutableStateOf(false) }
    var show3DThemePickerDialog by remember { mutableStateOf(false) }
    var showSpeedPitchSheet by remember { mutableStateOf(false) }
    var showShakeConfigSheet by remember { mutableStateOf(false) }
    var showUpNextQueueSheet by remember { mutableStateOf(false) }
    var showEditLyricsDialog by remember { mutableStateOf(false) }
    var showFileActionDialog by remember { mutableStateOf(false) }

    // 7-Heart Burst Animation Trigger (6-7 hearts rising and fading out within 2 seconds)
    var heartBurstTrigger by remember { mutableIntStateOf(0) }

    var showVolumeHud by remember { mutableStateOf(false) }
    var lastVolumeTouchTimestamp by remember { mutableLongStateOf(0L) }
    var dragStartVolume by remember { mutableIntStateOf(audioVolumePercent) }
    var dragAccumulatedDy by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(lastVolumeTouchTimestamp) {
        if (lastVolumeTouchTimestamp > 0L) {
            kotlinx.coroutines.delay(1400L)
            showVolumeHud = false
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_spin")
    val discRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "disc_rotation"
    )

    val durationMs = (currentTrack?.duration?.takeIf { it > 0L } ?: 180_000L).coerceAtLeast(1L)
    val progressFraction = (playbackPosition.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (visualizer3DEnabled) {
                        listOf(
                            themeSpec.bgTopColor,
                            themeSpec.bgBottomColor,
                            Color(0xFF04060B)
                        )
                    } else {
                        listOf(
                            Color(0xFF0A1418),
                            Color(0xFF060B12),
                            Color(0xFF04060B)
                        )
                    }
                )
            )
            .pointerInput(currentTrack?.id) {
                var totalHorizontalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalHorizontalDrag = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        totalHorizontalDrag += dragAmount
                    },
                    onDragEnd = {
                        if (totalHorizontalDrag < -110f) {
                            viewModel.playNext()
                        } else if (totalHorizontalDrag > 110f) {
                            viewModel.playPrevious()
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        if (offset.x >= size.width * 0.45f) {
                            dragStartVolume = viewModel.syncSystemAudioVolumeFromHardware()
                            dragAccumulatedDy = 0f
                            showVolumeHud = true
                            lastVolumeTouchTimestamp = System.currentTimeMillis()
                        } else {
                            dragAccumulatedDy = Float.NaN
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (!dragAccumulatedDy.isNaN()) {
                            change.consume()
                            dragAccumulatedDy += dragAmount
                            // Smooth, gradual volume swipe ("slowly-slowly badhe aur ghate" 1% by 1%)
                            val maxDragPx = (size.height * 1.45f).coerceAtLeast(520f)
                            val deltaPct = ((-dragAccumulatedDy / maxDragPx) * 200f).toInt()
                            viewModel.setAudioVolumeWithBoost((dragStartVolume + deltaPct).coerceIn(0, 200))
                            showVolumeHud = true
                            lastVolumeTouchTimestamp = System.currentTimeMillis()
                        }
                    }
                )
            }
    ) {
        // 1. When 3D Visualizer is ON, render the 15-theme 3D Animated Visualizer Backdrop;
        //    When OFF, render the user's Scenic Theme / Custom Gallery Wallpaper with zero 3D CPU overhead!
        if (visualizer3DEnabled) {
            Music3DAnimatedBackdrop(
                themeSpec = themeSpec,
                isPlaying = isPlaying,
                frequencies = frequencies,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            GlobalScenicThemeBackdrop(
                presetName = scenicBackdropPreset,
                accentColor = accentColor,
                modifier = Modifier.fillMaxSize()
            )
            if (customWallpaperUri != null) {
                AsyncImage(
                    model = customWallpaperUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.42f,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(
                    horizontal = if (isTablet) 28.dp else 16.dp,
                    vertical = if (isLandscape) 6.dp else 10.dp
                ),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Clean Top Bar: ONLY Back Button (←), "Now Playing (x/y)", and Compact Cover/Lyrics Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LiquidGlassIconOrb(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tintColor = Color.White,
                        size = 40.dp,
                        iconSize = 20.dp,
                        testTag = "music_player_back_btn",
                        onClick = onBack
                    )
                    Column {
                        Text(
                            text = if (isHindi) "अभी चल रहा है" else "Now Playing",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "(${(currentTrackIndex + 1).coerceAtMost(activeQueue.size.coerceAtLeast(1))}/${activeQueue.size})",
                            color = HybridTextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // Compact Cover / Lyrics Switcher Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(15.dp))
                            .background(if (centerMode == CenterDisplayMode.TURNTABLE) HybridEmerald else Color.Transparent)
                            .clickable { centerMode = CenterDisplayMode.TURNTABLE }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Album,
                            contentDescription = "Album Disc",
                            tint = if (centerMode == CenterDisplayMode.TURNTABLE) Color.Black else Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(15.dp))
                            .background(if (centerMode == CenterDisplayMode.LYRICS) HybridEmerald else Color.Transparent)
                            .clickable { centerMode = CenterDisplayMode.LYRICS }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = "Synced Lyrics",
                            tint = if (centerMode == CenterDisplayMode.LYRICS) Color.Black else Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            // 2. Center Stage (Vinyl Turntable OR Synced Lyrics)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                val isCompactSplit = configuration.screenHeightDp < 480
                when (centerMode) {
                    CenterDisplayMode.TURNTABLE -> {
                        val discSize: Dp = when {
                            isCompactSplit -> 112.dp
                            isLandscape -> 170.dp
                            isTablet -> 290.dp
                            else -> 240.dp
                        }
                        VinylTurntableCenterpiece(
                            track = currentTrack,
                            theme = themeSpec,
                            rotationDegrees = if (isPlaying) discRotation else 0f,
                            frequencies = frequencies,
                            visualizerEnabled = visualizer3DEnabled,
                            discSize = discSize
                        )
                    }
                    CenterDisplayMode.LYRICS -> {
                        InteractiveSyncedLyricsStage(
                            lyrics = syncedLyrics,
                            positionMs = playbackPosition,
                            accentColor = if (visualizer3DEnabled) themeSpec.primaryColor else accentColor,
                            onSeekToLine = { timestampMs -> viewModel.seekTo(timestampMs) },
                            onOpenEditLyrics = { showEditLyricsDialog = true }
                        )
                    }
                }
            }

            // 3. Bottom Control Deck (Title, 5 Thumb-Friendly Action Orbs, Seekbar, Transport Row, Up Next Queue Bar)
            val isCompactSplitDeck = configuration.screenHeightDp < 480
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(if (isCompactSplitDeck) 4.dp else if (isLandscape) 6.dp else 10.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = currentTrack?.cleanTitle ?: (if (isHindi) "कोई गाना नहीं चुना गया" else "No Track Selected"),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (isCompactSplitDeck) 15.sp else if (isLandscape) 17.sp else 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val artistInfo = buildString {
                        append(currentTrack?.artist?.ifBlank { "Local Artist" } ?: "Hybrid Studio")
                        currentTrack?.folderName?.takeIf { it.isNotBlank() }?.let {
                            append(" • $it")
                        }
                        currentTrack?.format?.takeIf { it.isNotBlank() }?.let {
                            append(" • $it")
                        }
                        if (abPointAMs != null) {
                            append(" • A-B")
                        }
                    }
                    Text(
                        text = artistInfo,
                        color = HybridTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Live Millisecond / Tenth-Second Sleep Timer Pill OR 5s DJ Crossfade Pill
                    if (sleepTimerRemainingMs > 0L || isDjCrossfadingNow) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (sleepTimerRemainingMs > 0L) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0x33FFD54F))
                                        .border(1.dp, HybridGold, RoundedCornerShape(50))
                                        .clickable { showSleepTimerSheet = true }
                                        .padding(horizontal = 10.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "⏱️ ${formatLiveSleepTimerMs(sleepTimerRemainingMs)}",
                                        color = HybridGold,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            if (isDjCrossfadingNow) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0x3300E5FF))
                                        .border(1.dp, HybridCyan, RoundedCornerShape(50))
                                        .padding(horizontal = 10.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = if (isHindi) "🎧 5s DJ क्रॉसफ़ेड मिक्स..." else "🎧 5s DJ Crossfade Mix...",
                                        color = HybridCyan,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 5 Thumb-Friendly Middle Action Orbs (Right above Seekbar):
                // 1. Favourite (❤️ + 7-Heart Burst) | 2. Equalizer (🎚️) | 3. Sleep Timer (⏱️ Live Countdown) | 4. 2x Shake (📳) | 5. 3-Dot Menu (⋮)
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Favourite (❤️) with 7-Heart 2-Second Burst Animation
                        Box(contentAlignment = Alignment.Center) {
                            LiquidGlassIconOrb(
                                icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favourite",
                                tintColor = if (isFavorite) Color(0xFFFF4081) else Color.White,
                                size = 46.dp,
                                iconSize = 21.dp,
                                isActive = isFavorite,
                                testTag = "music_fav_btn",
                                onClick = {
                                    heartBurstTrigger++
                                    currentTrack?.let {
                                        viewModel.toggleFavorite(it)
                                        Toast.makeText(
                                            context,
                                            if (!isFavorite) {
                                                if (isHindi) "पसंदीदा में सेव किया गया ❤️" else "Saved to Favourites ❤️"
                                            } else {
                                                if (isHindi) "पसंदीदा से हटाया गया" else "Removed from Favourites"
                                            },
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                            SevenHeartBurstEffect(trigger = heartBurstTrigger)
                        }

                        // 2. Colorful Equalizer Orb (🎚️)
                        ColorfulEqualizerOrb(
                            onClick = onOpenEqualizer,
                            size = 46.dp
                        )

                        // 3. Sleep Timer Orb (⏱️ — Live MM:SS.d Countdown)
                        Box(contentAlignment = Alignment.TopEnd) {
                            LiquidGlassIconOrb(
                                icon = Icons.Default.Timer,
                                contentDescription = "Sleep Timer",
                                tintColor = if (sleepTimerRemainingMs > 0L) HybridGold else Color.White,
                                size = 46.dp,
                                iconSize = 21.dp,
                                isActive = sleepTimerRemainingMs > 0L,
                                testTag = "music_sleep_timer_btn",
                                onClick = { showSleepTimerSheet = true }
                            )
                            if (sleepTimerRemainingMs > 0L) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = 6.dp, y = (-4).dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(HybridGold)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = formatLiveSleepTimerMs(sleepTimerRemainingMs),
                                        color = Color.Black,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 8.sp
                                    )
                                }
                            }
                        }

                        // 4. Single 2x Shake Vibration Orb (📳)
                        DoubleShakeVibrationOrb(
                            isEnabled = doubleShakeEnabled,
                            onClick = { showShakeConfigSheet = true },
                            size = 46.dp
                        )

                        // 5. 3-Dot Menu Orb (⋮)
                        LiquidGlassIconOrb(
                            icon = Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tintColor = Color.White,
                            size = 46.dp,
                            iconSize = 22.dp,
                            testTag = "music_3dot_menu_btn",
                            onClick = { showMoreOptionsSheet = true }
                        )
                    }
                }

                // Precision Seekbar + Timestamps
                Column(modifier = Modifier.fillMaxWidth()) {
                    VisualPrecisionSeekBar(
                        progressFraction = progressFraction,
                        onSeekFractionChanged = { frac: Float ->
                            viewModel.seekTo((frac * durationMs.toFloat()).toLong())
                        },
                        accentColor = if (visualizer3DEnabled) themeSpec.primaryColor else accentColor,
                        secondaryAccent = if (visualizer3DEnabled) themeSpec.secondaryColor else HybridCyan,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDurationMs(playbackPosition),
                            color = HybridTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = formatDurationMs(durationMs),
                            color = HybridTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Bottom Transport Row:
                // Left: Shuffle (🔀) vs Straight Line-by-Line Order (➡️ 1→2) | Center: Prev, Play/Pause, Next | Right: Repeat
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Leftmost ("Ulte Haath"): Distinct Shuffle vs Straight Line-by-Line Sequential Button
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (isShuffleMode) HybridEmerald.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.10f)
                            )
                            .border(
                                1.2.dp,
                                if (isShuffleMode) HybridEmerald else HybridCyan.copy(alpha = 0.55f),
                                CircleShape
                            )
                            .clickable {
                                viewModel.toggleShuffleMode()
                                val msg = if (!isShuffleMode) {
                                    if (isHindi) "🔀 रैंडम शफ़ल चालू (Shuffle Mode)" else "🔀 Shuffle Mode ON (Random)"
                                } else {
                                    if (isHindi) "➡️ सीधा क्रम चालू (1→2 Line-by-Line)" else "➡️ Straight Line-by-Line Order (1→2)"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                            .testTag("music_shuffle_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isShuffleMode) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle ON",
                                tint = HybridEmerald,
                                modifier = Modifier.size(23.dp)
                            )
                        } else {
                            // Straight Line-by-Line Sequential Icon + "1→2" Badge
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingFlat,
                                    contentDescription = "Straight Line Order",
                                    tint = HybridCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "1→2",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 8.sp
                                )
                            }
                        }
                    }

                    // Previous Track
                    IconButton(
                        onClick = { viewModel.playPrevious() },
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("music_prev_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    // Play / Pause Master Circle
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(
                                if (visualizer3DEnabled) themeSpec.primaryColor else accentColor
                            )
                            .clickable {
                                if (nowPlaying == null && currentTrack != null) {
                                    viewModel.playMusic(currentTrack)
                                } else {
                                    viewModel.togglePlayPause()
                                }
                            }
                            .testTag("music_play_pause_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Next Track
                    IconButton(
                        onClick = { viewModel.playNext() },
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("music_next_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    // Rightmost ("Seedhe Haath"): Repeat Mode Cycle Button (Order -> Repeat All -> Repeat One)
                    IconButton(
                        onClick = {
                            viewModel.cycleRepeatMode()
                            val nextMode = (repeatMode + 1) % 3
                            val msg = when (nextMode) {
                                2 -> if (isHindi) "एक गाना रिपीट (Repeat 1)" else "Repeat One Song 🔂"
                                1 -> if (isHindi) "सभी गाने रिपीट (Repeat All)" else "Repeat All Songs 🔁"
                                else -> if (isHindi) "क्रम से चलाएं (Order List)" else "Play in Order ▶"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (repeatMode > 0) HybridCyan.copy(alpha = 0.22f) else Color.Transparent
                            )
                            .testTag("music_repeat_btn")
                    ) {
                        Icon(
                            imageVector = when (repeatMode) {
                                2 -> Icons.Default.RepeatOne
                                else -> Icons.Default.Repeat
                            },
                            contentDescription = "Repeat Mode",
                            tint = when (repeatMode) {
                                2 -> HybridGold
                                1 -> HybridCyan
                                else -> Color.White.copy(alpha = 0.7f)
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Up Next Queue Bar with Real Song Photo Thumbnail
                LiquidGlassBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("music_up_next_bar"),
                    cornerRadius = 18.dp,
                    tintColor = HybridEmerald,
                    glassAlpha = 0.18f,
                    onClick = { showUpNextQueueSheet = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (nextTrackInQueue != null) {
                            GuaranteedAlbumArt(
                                mediaPath = nextTrackInQueue.path,
                                title = nextTrackInQueue.cleanTitle,
                                isVideo = nextTrackInQueue.isVideo,
                                modifier = Modifier.size(32.dp),
                                cornerRadius = 8.dp,
                                accentColor = HybridEmerald
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = null,
                                tint = HybridEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = nextTrackInQueue?.cleanTitle ?: (if (isHindi) "अगला गाना" else "Up Next Queue"),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Open Queue",
                            tint = HybridEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Smooth Vertical Volume Pill HUD on Right Side
        VerticalVolumeBrightnessPillHud(
            visible = showVolumeHud,
            isVolume = true,
            percentValue = audioVolumePercent,
            maxValue = 200,
            accentColor = if (visualizer3DEnabled) themeSpec.primaryColor else HybridEmerald,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
        )
    }

    // --- Sleep Timer Dialog with Live Millisecond / Tenth-Second Countdown ---
    if (showSleepTimerSheet) {
        var customMinutesText by remember { mutableStateOf("") }
        val presets = listOf(5, 10, 15, 30, 45, 60)

        AlertDialog(
            onDismissRequest = { showSleepTimerSheet = false },
            containerColor = HybridCardBg,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = HybridGold)
                    Text(
                        text = if (isHindi) "लाइव स्लीप टाइमर (Live Countdown)" else "Live Sleep Timer Countdown",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (sleepTimerRemainingMs > 0L) {
                        LiquidGlassBox(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 16.dp,
                            tintColor = HybridGold,
                            glassAlpha = 0.28f
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (isHindi) "लाइव उल्टी गिनती (मिनट : सेकंड : नैनो)" else "LIVE COUNTDOWN (MIN : SEC . TENTHS)",
                                    color = HybridGold,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = formatLiveSleepTimerMs(sleepTimerRemainingMs),
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 28.sp,
                                    letterSpacing = 1.2.sp
                                )
                            }
                        }
                    } else {
                        Text(
                            text = if (isHindi) "समय चुनें या नीचे अपना समय लिखें:" else "Select a preset or enter custom minutes below:",
                            color = HybridTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.forEach { mins ->
                            val selected = sleepTimerMinutes == mins
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) HybridGold.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f)
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) HybridGold else Color.White.copy(alpha = 0.16f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        viewModel.setSleepTimer(mins)
                                        Toast.makeText(
                                            context,
                                            if (isHindi) "स्लीप टाइमर: $mins मिनट चालू!" else "Live Sleep Timer started: $mins min",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${mins}m",
                                    color = if (selected) HybridGold else Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customMinutesText,
                            onValueChange = { input ->
                                customMinutesText = input.filter { it.isDigit() }.take(4)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            label = {
                                Text(
                                    text = if (isHindi) "कस्टम मिनट (जैसे 9, 15, 30)" else "Custom Minutes (e.g. 9, 15, 30)",
                                    fontSize = 12.sp
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val mins = customMinutesText.toIntOrNull() ?: 0
                                if (mins > 0) {
                                    viewModel.setSleepTimer(mins)
                                    Toast.makeText(
                                        context,
                                        if (isHindi) "स्लीप टाइमर: $mins मिनट सेट किया गया" else "Sleep Timer set for $mins minutes",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = HybridEmerald)
                        ) {
                            Text(
                                text = if (isHindi) "शुरू" else "Start",
                                color = Color.Black,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    if (sleepTimerRemainingMs > 0L) {
                        LiquidGlassBox(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 12.dp,
                            tintColor = HybridCoralRed,
                            glassAlpha = 0.22f,
                            onClick = {
                                viewModel.setSleepTimer(0)
                                Toast.makeText(
                                    context,
                                    if (isHindi) "स्लीप टाइमर बंद कर दिया गया" else "Sleep Timer turned OFF",
                                    Toast.LENGTH_SHORT
                                ).show()
                                showSleepTimerSheet = false
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.TimerOff, contentDescription = null, tint = HybridCoralRed, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isHindi) "टाइमर बंद करें (Turn Off)" else "Turn Off Sleep Timer",
                                    color = HybridCoralRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepTimerSheet = false }) {
                    Text(if (isHindi) "बंद करें" else "Close", color = HybridEmerald, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // --- Expanded 16-Tool 3-Dot More Options Studio Sheet ---
    if (showMoreOptionsSheet) {
        AlertDialog(
            onDismissRequest = { showMoreOptionsSheet = false },
            containerColor = HybridCardBg,
            title = {
                Column {
                    Text(
                        text = currentTrack?.cleanTitle ?: (if (isHindi) "गाने के प्रो विकल्प" else "Pro Audio Studio Options"),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isHindi) "16+ एडवांस ऑडियो और डीजे फीचर्स" else "16+ Advanced Audio & DJ Controls",
                        color = HybridEmerald,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Row 1: Fast Share & System Share
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LiquidGlassBox(
                            modifier = Modifier.weight(1f),
                            cornerRadius = 14.dp,
                            tintColor = HybridEmerald,
                            glassAlpha = 0.24f,
                            onClick = {
                                showMoreOptionsSheet = false
                                currentTrack?.let { onOpenHybridShareForItem(it) }
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = HybridEmerald, modifier = Modifier.size(20.dp))
                                Text(if (isHindi) "तेज़ शेयर" else "Fast Share", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            }
                        }

                        LiquidGlassBox(
                            modifier = Modifier.weight(1f),
                            cornerRadius = 14.dp,
                            tintColor = HybridCyan,
                            glassAlpha = 0.24f,
                            onClick = {
                                showMoreOptionsSheet = false
                                currentTrack?.let {
                                    WifiDirectTransferEngine.shareRealFilesViaSystem(context, listOf(it))
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = HybridCyan, modifier = Modifier.size(20.dp))
                                Text(if (isHindi) "ऐप शेयर" else "Share Song", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }

                    // Row 2: Permanent 3D Visualizer ON/OFF Switch
                    LiquidGlassBox(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 14.dp,
                        tintColor = if (visualizer3DEnabled) HybridEmerald else Color.White,
                        glassAlpha = if (visualizer3DEnabled) 0.26f else 0.14f,
                        onClick = { viewModel.toggleVisualizer3DEnabled() }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (visualizer3DEnabled) HybridEmerald else HybridTextSecondary
                                )
                                Column {
                                    Text(
                                        text = if (isHindi) "3D इक्वलाइज़र और विज़ुअलाइज़र" else "3D Visualizer Animation",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = if (visualizer3DEnabled) {
                                            if (isHindi) "चालू है • बंद करने के लिए टैप करें" else "Active • Runs only when ON"
                                        } else {
                                            if (isHindi) "बंद है (जब ऑन करेंगे तभी चलेगा)" else "OFF (Only runs when turned ON)"
                                        },
                                        color = HybridTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Switch(
                                checked = visualizer3DEnabled,
                                onCheckedChange = { viewModel.setVisualizer3DEnabled(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = HybridEmerald)
                            )
                        }
                    }

                    // Row 3: 15 Distinct 3D Visualizer Themes Picker
                    LiquidGlassBox(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 14.dp,
                        tintColor = Color(0xFFB388FF),
                        glassAlpha = 0.20f,
                        onClick = {
                            showMoreOptionsSheet = false
                            show3DThemePickerDialog = true
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Palette, contentDescription = null, tint = Color(0xFFB388FF))
                                Column {
                                    Text(
                                        text = if (isHindi) "15 अलग-अलग 3D थीम्स चुनें" else "15 Distinct 3D Visualizer Themes",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = active3DThemeName,
                                        color = Color(0xFFB388FF),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White)
                        }
                    }

                    // Row 4: 5-Second DJ Crossfade / Slow Mix Toggle
                    LiquidGlassBox(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 14.dp,
                        tintColor = if (djCrossfadeEnabled) HybridCyan else Color.White,
                        glassAlpha = if (djCrossfadeEnabled) 0.24f else 0.12f,
                        onClick = { viewModel.toggleDjCrossfade() }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Album, contentDescription = null, tint = if (djCrossfadeEnabled) HybridCyan else HybridTextSecondary)
                                Column {
                                    Text(
                                        text = if (isHindi) "5s DJ क्रॉसफ़ेड रीमिक्स ट्रांज़िशन" else "5-Second DJ Crossfade Transition",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = if (isHindi) "आखिरी 5 सेकंड में स्मूथ स्लो फेड और अगला गाना मिक्स" else "Smooth 5s slow fade-out & DJ mix into next track",
                                        color = HybridTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Switch(
                                checked = djCrossfadeEnabled,
                                onCheckedChange = { viewModel.toggleDjCrossfade() },
                                colors = SwitchDefaults.colors(checkedThumbColor = HybridCyan)
                            )
                        }
                    }

                    // Row 5: 200% Volume Boost & Speed/Pitch Studio
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LiquidGlassBox(
                            modifier = Modifier.weight(1f),
                            cornerRadius = 14.dp,
                            tintColor = if (audioVolumePercent > 100) Color(0xFFFF6D00) else HybridGold,
                            glassAlpha = 0.22f,
                            onClick = { viewModel.toggle200Boost() }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    tint = if (audioVolumePercent > 100) Color(0xFFFF6D00) else HybridGold,
                                    modifier = Modifier.size(19.dp)
                                )
                                Text("Vol $audioVolumePercent%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        LiquidGlassBox(
                            modifier = Modifier.weight(1f),
                            cornerRadius = 14.dp,
                            tintColor = HybridCyan,
                            glassAlpha = 0.22f,
                            onClick = {
                                showMoreOptionsSheet = false
                                showSpeedPitchSheet = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Speed, contentDescription = null, tint = HybridCyan, modifier = Modifier.size(19.dp))
                                Text("${playbackSpeed}x Speed", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    // Row 6: 5-Band Studio Equalizer & Deep Bass Boost
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LiquidGlassBox(
                            modifier = Modifier.weight(1f),
                            cornerRadius = 14.dp,
                            tintColor = HybridEmerald,
                            glassAlpha = 0.22f,
                            onClick = {
                                showMoreOptionsSheet = false
                                onOpenEqualizer()
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Equalizer, contentDescription = null, tint = HybridEmerald, modifier = Modifier.size(19.dp))
                                Text("EQ: $equalizerPreset", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            }
                        }

                        LiquidGlassBox(
                            modifier = Modifier.weight(1f),
                            cornerRadius = 14.dp,
                            tintColor = if (bassBoostEnabled) HybridGold else Color.White,
                            glassAlpha = if (bassBoostEnabled) 0.28f else 0.16f,
                            onClick = { viewModel.toggleBassBoost() }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Speaker, contentDescription = null, tint = if (bassBoostEnabled) HybridGold else Color.White, modifier = Modifier.size(19.dp))
                                Text(if (bassBoostEnabled) "Bass: ON" else "Bass: OFF", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    // Row 7: Stereo Channel Balance & Live Sleep Timer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LiquidGlassBox(
                            modifier = Modifier.weight(1f),
                            cornerRadius = 14.dp,
                            tintColor = HybridPurple,
                            glassAlpha = 0.22f,
                            onClick = { viewModel.cycleAudioBalanceMode() }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.SurroundSound, contentDescription = null, tint = HybridPurple, modifier = Modifier.size(19.dp))
                                Text(audioBalanceMode, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        LiquidGlassBox(
                            modifier = Modifier.weight(1f),
                            cornerRadius = 14.dp,
                            tintColor = HybridGold,
                            glassAlpha = 0.22f,
                            onClick = {
                                showMoreOptionsSheet = false
                                showSleepTimerSheet = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Timer, contentDescription = null, tint = HybridGold, modifier = Modifier.size(19.dp))
                                Text(
                                    text = if (sleepTimerRemainingMs > 0L) formatLiveSleepTimerMs(sleepTimerRemainingMs) else "Sleep Timer",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Row 8: Synced Lyrics (.LRC) & Up Next Queue Manager
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LiquidGlassBox(
                            modifier = Modifier.weight(1f),
                            cornerRadius = 14.dp,
                            tintColor = HybridCyan,
                            glassAlpha = 0.20f,
                            onClick = {
                                showMoreOptionsSheet = false
                                showEditLyricsDialog = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Lyrics, contentDescription = null, tint = HybridCyan, modifier = Modifier.size(18.dp))
                                Text(if (isHindi) "लिरिक्स (.LRC)" else "Edit Lyrics", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        LiquidGlassBox(
                            modifier = Modifier.weight(1f),
                            cornerRadius = 14.dp,
                            tintColor = HybridEmerald,
                            glassAlpha = 0.20f,
                            onClick = {
                                showMoreOptionsSheet = false
                                showUpNextQueueSheet = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = HybridEmerald, modifier = Modifier.size(18.dp))
                                Text("Queue (${activeQueue.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    // Row 9: A-B Repeat Loop & File / Vault / Delete
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LiquidGlassBox(
                            modifier = Modifier.weight(1f),
                            cornerRadius = 14.dp,
                            tintColor = HybridEmerald,
                            glassAlpha = 0.20f,
                            onClick = { viewModel.toggleAbRepeatPoint() }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Repeat, contentDescription = null, tint = HybridEmerald, modifier = Modifier.size(18.dp))
                                Text(
                                    text = when {
                                        abPointAMs != null && abPointBMs != null -> "A-B ON"
                                        abPointAMs != null -> "Set B..."
                                        else -> "A-B Loop"
                                    },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        LiquidGlassBox(
                            modifier = Modifier.weight(1f),
                            cornerRadius = 14.dp,
                            tintColor = HybridCoralRed,
                            glassAlpha = 0.22f,
                            onClick = {
                                showMoreOptionsSheet = false
                                showFileActionDialog = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.FolderShared, contentDescription = null, tint = HybridCoralRed, modifier = Modifier.size(18.dp))
                                Text(if (isHindi) "फ़ाइल/हटाएं" else "File / Delete", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMoreOptionsSheet = false }) {
                    Text(if (isHindi) "बंद करें" else "Close", color = HybridEmerald, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // --- 15 3D Visualizer Themes Picker Dialog ---
    if (show3DThemePickerDialog) {
        AlertDialog(
            onDismissRequest = { show3DThemePickerDialog = false },
            containerColor = HybridCardBg,
            title = {
                Text(
                    text = if (isHindi) "15 3D विज़ुअलाइज़र थीम्स" else "15 3D Visualizer Themes",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
            },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(Music3DThemeEngine.THEMES_3D, key = { it.id }) { spec ->
                        val active = spec.name == active3DThemeName && visualizer3DEnabled
                        LiquidGlassBox(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 14.dp,
                            tintColor = spec.primaryColor,
                            glassAlpha = if (active) 0.34f else 0.16f,
                            onClick = {
                                viewModel.setVisualizer3DEnabled(true)
                                viewModel.select3DMusicTheme(spec.name)
                                show3DThemePickerDialog = false
                            }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = spec.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = spec.subtitle,
                                    color = spec.primaryColor,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { show3DThemePickerDialog = false }) {
                    Text(if (isHindi) "बंद करें" else "Close", color = HybridEmerald)
                }
            }
        )
    }

    // --- Speed & Pitch Studio Dialog ---
    if (showSpeedPitchSheet) {
        val presets = listOf(
            Triple("Normal Studio", 1.0f, 1.0f),
            Triple("Slowed + Reverb", 0.85f, 0.90f),
            Triple("Nightcore 1.25x", 1.25f, 1.18f),
            Triple("Deep Bass Vocal", 0.95f, 0.86f),
            Triple("Fast Workout 1.5x", 1.5f, 1.0f)
        )
        AlertDialog(
            onDismissRequest = { showSpeedPitchSheet = false },
            containerColor = HybridCardBg,
            title = {
                Text(
                    text = if (isHindi) "स्पीड और पिच स्टूडियो" else "Speed & Pitch Studio",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { (name, spd, ptc) ->
                        val selected = activePitchPreset == name
                        LiquidGlassBox(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 14.dp,
                            tintColor = if (selected) HybridCyan else Color.White,
                            glassAlpha = if (selected) 0.28f else 0.12f,
                            onClick = {
                                viewModel.applyPitchPreset(name, spd, ptc)
                                showSpeedPitchSheet = false
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(text = "${spd}x", color = HybridCyan, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedPitchSheet = false }) {
                    Text(if (isHindi) "बंद करें" else "Close", color = HybridCyan)
                }
            }
        )
    }

    // --- 2x Shake Configuration Sheet ---
    if (showShakeConfigSheet) {
        AlertDialog(
            onDismissRequest = { showShakeConfigSheet = false },
            containerColor = HybridCardBg,
            title = {
                Text(
                    text = if (isHindi) "2x शेक से अगला गाना (Shake Control)" else "2x Shake to Skip Song",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isHindi) "2x फ़ोन हिलाने पर अगला गाना" else "Enable 2x Shake Next Track",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = if (isHindi) "वाइब्रेशन के साथ तुरंत गाना बदलेगा" else "Vibrates & skips track on double wrist shake",
                                color = HybridTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = doubleShakeEnabled,
                            onCheckedChange = { viewModel.setDoubleShakeEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = HybridEmerald)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("High", "Normal", "Firm").forEach { level ->
                            val selected = shakeSensitivity == level
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) HybridEmerald.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, if (selected) HybridEmerald else Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                                    .clickable { viewModel.setShakeSensitivity(level) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = level,
                                    color = if (selected) HybridEmerald else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showShakeConfigSheet = false }) {
                    Text(if (isHindi) "ठीक है" else "Done", color = HybridEmerald, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // --- Up Next Queue Dialog with Shuffle, Repeat, Track Swap Up/Down & Real Album Art ---
    if (showUpNextQueueSheet) {
        AlertDialog(
            onDismissRequest = { showUpNextQueueSheet = false },
            containerColor = HybridCardBg,
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isHindi) "प्लेइंग क्यू (${activeQueue.size})" else "Playing Queue (${activeQueue.size})",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "${currentTrackIndex + 1}/${activeQueue.size}",
                            color = HybridEmerald,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    // Queue Control Bar: Shuffle / Straight Order Toggle + Repeat Mode Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LiquidGlassBox(
                            modifier = Modifier.weight(1f),
                            cornerRadius = 12.dp,
                            tintColor = if (isShuffleMode) HybridEmerald else HybridCyan,
                            glassAlpha = 0.24f,
                            onClick = { viewModel.toggleShuffleMode() }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isShuffleMode) Icons.Default.Shuffle else Icons.AutoMirrored.Filled.TrendingFlat,
                                    contentDescription = null,
                                    tint = if (isShuffleMode) HybridEmerald else HybridCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isShuffleMode) (if (isHindi) "शफ़ल ON" else "Shuffle ON") else (if (isHindi) "क्रम 1→2" else "Order 1→2"),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        LiquidGlassBox(
                            modifier = Modifier.weight(1f),
                            cornerRadius = 12.dp,
                            tintColor = if (repeatMode == 2) HybridGold else HybridCyan,
                            glassAlpha = 0.24f,
                            onClick = { viewModel.cycleRepeatMode() }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                    contentDescription = null,
                                    tint = if (repeatMode == 2) HybridGold else HybridCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when (repeatMode) {
                                        2 -> if (isHindi) "रिपीट 1" else "Repeat 1"
                                        1 -> if (isHindi) "रिपीट ऑल" else "Repeat All"
                                        else -> if (isHindi) "एक बार" else "Play Once"
                                    },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(activeQueue, key = { _, item -> item.id }) { idx, item ->
                        val isCurrent = item.id == currentTrack?.id
                        LiquidGlassBox(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 14.dp,
                            tintColor = if (isCurrent) HybridEmerald else Color.White,
                            glassAlpha = if (isCurrent) 0.28f else 0.12f,
                            onClick = {
                                viewModel.playMusic(item)
                                showUpNextQueueSheet = false
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    color = if (isCurrent) HybridEmerald else HybridTextSecondary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.width(22.dp)
                                )
                                GuaranteedAlbumArt(
                                    mediaPath = item.path,
                                    title = item.cleanTitle,
                                    isVideo = item.isVideo,
                                    modifier = Modifier.size(42.dp),
                                    cornerRadius = 10.dp,
                                    accentColor = if (isCurrent) HybridEmerald else HybridCyan
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.cleanTitle,
                                        color = if (isCurrent) HybridEmerald else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${item.artist.ifBlank { if (item.isVideo) "Video Song" else "Local Artist" }} • ${item.formattedDuration}",
                                        color = HybridTextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Move Song Up / Down Swap Buttons
                                IconButton(
                                    onClick = {
                                        if (idx > 0) viewModel.moveTrackInMusicQueue(idx, idx - 1)
                                    },
                                    enabled = idx > 0,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Move Up",
                                        tint = if (idx > 0) Color.White else Color.White.copy(alpha = 0.2f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (idx < activeQueue.lastIndex) viewModel.moveTrackInMusicQueue(idx, idx + 1)
                                    },
                                    enabled = idx < activeQueue.lastIndex,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Move Down",
                                        tint = if (idx < activeQueue.lastIndex) Color.White else Color.White.copy(alpha = 0.2f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUpNextQueueSheet = false }) {
                    Text(if (isHindi) "बंद करें" else "Close", color = HybridCoralRed, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showEditLyricsDialog) {
        var rawText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showEditLyricsDialog = false },
            containerColor = HybridCardBg,
            title = {
                Text(
                    text = if (isHindi) "लिरिक्स जोड़ें / संपादित करें" else "Add / Edit Track Lyrics (.LRC)",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
            },
            text = {
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    placeholder = { Text("[00:12.00] Lyric line...") },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (rawText.isNotBlank()) {
                            viewModel.saveCustomLyricsForCurrentTrack(rawText)
                        }
                        showEditLyricsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HybridEmerald)
                ) {
                    Text(if (isHindi) "सेव करें" else "Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditLyricsDialog = false }) {
                    Text(if (isHindi) "रद्द करें" else "Cancel", color = HybridTextSecondary)
                }
            }
        )
    }

    if (showFileActionDialog && currentTrack != null) {
        MediaSevenActionDialog(
            item = currentTrack,
            isFavorite = isFavorite,
            isHindi = isHindi,
            onDismiss = { showFileActionDialog = false },
            onHybridShare = { onOpenHybridShareForItem(it) },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onLockInVault = { viewModel.toggleVaultLock(it) },
            onPermanentDeleteConfirmed = {
                viewModel.deletePermanently(it)
                showFileActionDialog = false
            },
            onMoveToRecycleBin = {
                viewModel.moveToRecycleBin(it)
                showFileActionDialog = false
            },
            onRenameCompleted = {
                viewModel.loadMedia()
            }
        )
    }
}

@Composable
private fun VinylTurntableCenterpiece(
    track: MediaItem?,
    theme: Music3DThemeSpec,
    rotationDegrees: Float,
    frequencies: List<Float>,
    visualizerEnabled: Boolean,
    discSize: Dp
) {
    val ringAccent = if (visualizerEnabled) theme.primaryColor else HybridEmerald
    val secondaryAccent = if (visualizerEnabled) theme.secondaryColor else HybridCyan

    Box(contentAlignment = Alignment.Center) {
        if (visualizerEnabled) {
            Canvas(modifier = Modifier.size(discSize + 56.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = size.minDimension * 0.40f
                val bars = 40
                for (i in 0 until bars) {
                    val angleRad = (i.toFloat() / bars) * 2f * Math.PI
                    val freqVal = frequencies[i % frequencies.size]
                    val barLen = 10.dp.toPx() + freqVal * 26.dp.toPx()
                    val start = Offset(
                        center.x + (baseRadius * cos(angleRad)).toFloat(),
                        center.y + (baseRadius * sin(angleRad)).toFloat()
                    )
                    val end = Offset(
                        center.x + ((baseRadius + barLen) * cos(angleRad)).toFloat(),
                        center.y + ((baseRadius + barLen) * sin(angleRad)).toFloat()
                    )
                    drawLine(
                        color = if (i % 2 == 0) ringAccent.copy(alpha = 0.72f) else secondaryAccent.copy(alpha = 0.65f),
                        start = start,
                        end = end,
                        strokeWidth = 3.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(discSize)
                .graphicsLayer { rotationZ = rotationDegrees }
                .clip(CircleShape)
                .border(4.dp, ringAccent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (track != null) {
                GuaranteedAlbumArt(
                    mediaPath = track.path,
                    title = track.cleanTitle,
                    isVideo = track.isVideo,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = discSize / 2,
                    accentColor = ringAccent
                )
            }
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0A0E17))
                    .border(3.dp, ringAccent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}

@Composable
private fun InteractiveSyncedLyricsStage(
    lyrics: List<SyncedLyricLine>,
    positionMs: Long,
    accentColor: Color,
    onSeekToLine: (Long) -> Unit,
    onOpenEditLyrics: () -> Unit
) {
    val activeIdx = remember(lyrics, positionMs) {
        SyncedLyricsEngine.findActiveLyricIndex(lyrics, positionMs)
    }
    val listState = rememberLazyListState()

    LaunchedEffect(activeIdx) {
        if (activeIdx >= 0 && activeIdx < lyrics.size) {
            listState.animateScrollToItem((activeIdx - 1).coerceAtLeast(0))
        }
    }

    LiquidGlassBox(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.88f),
        cornerRadius = 22.dp,
        tintColor = accentColor,
        glassAlpha = 0.16f
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Synced Lyrics",
                    color = accentColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
                TextButton(onClick = onOpenEditLyrics) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit .LRC", color = Color.White, fontSize = 11.sp)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                itemsIndexed(lyrics) { index, line ->
                    val isCurrent = index == activeIdx
                    Text(
                        text = line.text,
                        color = if (isCurrent) accentColor else Color.White.copy(alpha = 0.62f),
                        fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Medium,
                        fontSize = if (isCurrent) 17.sp else 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSeekToLine(line.timeMs) }
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                    )
                }
            }
        }
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return "%02d:%02d".format(mins, secs)
}

private fun formatLiveSleepTimerMs(remMs: Long): String {
    val safe = remMs.coerceAtLeast(0L)
    val mins = safe / 60_000L
    val secs = (safe % 60_000L) / 1000L
    val tenths = (safe % 1000L) / 100L
    return String.format(Locale.US, "%02d:%02d.%d", mins, secs, tenths)
}

/**
 * 7-Heart Burst Animation that erupts when clicking the Favourite Heart Button
 * and smoothly rises & fades out over 2 seconds (2000ms).
 */
@Composable
private fun SevenHeartBurstEffect(trigger: Int) {
    if (trigger <= 0) return
    val progress = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1950, easing = FastOutSlowInEasing)
        )
    }

    val p = progress.value
    if (p >= 0.99f) return

    val heartColors = remember {
        listOf(
            Color(0xFFFF1744),
            Color(0xFFFF4081),
            Color(0xFFF50057),
            Color(0xFFFFD54F),
            Color(0xFFE040FB),
            Color(0xFFFF5252),
            Color(0xFF00E5FF)
        )
    }
    val xAngles = remember { listOf(-52f, -34f, -16f, 0f, 18f, 36f, 54f) }
    val speedFactors = remember { listOf(0.85f, 1.05f, 0.92f, 1.18f, 0.96f, 1.08f, 0.88f) }

    Canvas(modifier = Modifier.size(46.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val alpha = (1f - (p * p)).coerceIn(0f, 1f)

        for (i in 0 until 7) {
            val spreadPx = (xAngles[i] * p * 1.4f).dp.toPx() + sin(p * 6f + i) * 6.dp.toPx()
            val risePx = -(p * 125f * speedFactors[i]).dp.toPx()
            val hx = cx + spreadPx
            val hy = cy + risePx
            val heartSize = (10f + (1f - kotlin.math.abs(p - 0.35f)) * 8f).dp.toPx()

            val path = Path().apply {
                moveTo(hx, hy + heartSize * 0.35f)
                cubicTo(
                    hx - heartSize * 0.8f, hy - heartSize * 0.2f,
                    hx - heartSize * 0.45f, hy - heartSize * 0.85f,
                    hx, hy - heartSize * 0.35f
                )
                cubicTo(
                    hx + heartSize * 0.45f, hy - heartSize * 0.85f,
                    hx + heartSize * 0.8f, hy - heartSize * 0.2f,
                    hx, hy + heartSize * 0.35f
                )
                close()
            }
            drawPath(
                path = path,
                color = heartColors[i].copy(alpha = alpha)
            )
        }
    }
}
