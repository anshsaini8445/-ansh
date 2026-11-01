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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import com.example.data.model.MediaItem
import com.example.helpers.Music3DAnimatedBackdrop
import com.example.helpers.Music3DThemeEngine
import com.example.helpers.Music3DThemeSpec
import com.example.helpers.SyncedLyricLine
import com.example.helpers.SyncedLyricsEngine
import com.example.helpers.WifiDirectTransferEngine
import com.example.ui.components.*
import com.example.ui.menus.MediaSevenActionDialog
import com.example.ui.theme.*
import com.example.viewmodel.PlayerViewModel
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

    var showVolumeHud by remember { mutableStateOf(false) }
    var lastVolumeTouchTimestamp by remember { mutableLongStateOf(0L) }
    var dragStartVolume by remember { mutableIntStateOf(audioVolumePercent) }
    var dragAccumulatedDy by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(lastVolumeTouchTimestamp) {
        if (lastVolumeTouchTimestamp > 0L) {
            kotlinx.coroutines.delay(1300L)
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
                            Color(0xFF0A1412),
                            Color(0xFF060B0E),
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
                        if (offset.x >= size.width * 0.5f) {
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
                            val maxDragPx = (size.height * 0.55f).coerceAtLeast(220f)
                            val deltaPct = ((-dragAccumulatedDy / maxDragPx) * 200f).toInt()
                            viewModel.setAudioVolumeWithBoost((dragStartVolume + deltaPct).coerceIn(0, 200))
                            showVolumeHud = true
                            lastVolumeTouchTimestamp = System.currentTimeMillis()
                        }
                    }
                )
            }
    ) {
        // Optional 3D Animated Backdrop ONLY when 3D Visualizer is enabled in 3-Dot Menu
        if (visualizer3DEnabled) {
            Music3DAnimatedBackdrop(
                themeSpec = themeSpec,
                isPlaying = isPlaying,
                frequencies = frequencies,
                modifier = Modifier.fillMaxSize()
            )
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
                when (centerMode) {
                    CenterDisplayMode.TURNTABLE -> {
                        val discSize: Dp = when {
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
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 10.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = currentTrack?.cleanTitle ?: (if (isHindi) "कोई गाना नहीं चुना गया" else "No Track Selected"),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (isLandscape) 17.sp else 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val artistInfo = buildString {
                        append(currentTrack?.artist?.ifBlank { "Local Artist" } ?: "Hybrid Studio")
                        currentTrack?.folderName?.takeIf { it.isNotBlank() }?.let {
                            append(" • $it")
                        }
                        if (sleepTimerMinutes > 0) {
                            append(" • ⏱️ ${sleepTimerMinutes}m")
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
                }

                // 5 Thumb-Friendly Middle Action Orbs (Right above Seekbar):
                // 1. Favourite (❤️) | 2. Equalizer (🎚️) | 3. Sleep Timer (⏱️) | 4. 2x Shake (📳) | 5. 3-Dot Menu (⋮)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Favourite (❤️)
                    LiquidGlassIconOrb(
                        icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favourite",
                        tintColor = if (isFavorite) Color(0xFFFF4081) else Color.White,
                        size = 46.dp,
                        iconSize = 21.dp,
                        isActive = isFavorite,
                        testTag = "music_fav_btn",
                        onClick = {
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

                    // 2. Colorful Equalizer Orb (🎚️)
                    ColorfulEqualizerOrb(
                        onClick = onOpenEqualizer,
                        size = 46.dp
                    )

                    // 3. Sleep Timer Orb (⏱️ — 5m, 10m, 20m, 45m, 60m + Custom Minutes)
                    LiquidGlassIconOrb(
                        icon = Icons.Default.Timer,
                        contentDescription = "Sleep Timer",
                        tintColor = if (sleepTimerMinutes > 0) HybridGold else Color.White,
                        size = 46.dp,
                        iconSize = 21.dp,
                        isActive = sleepTimerMinutes > 0,
                        testTag = "music_sleep_timer_btn",
                        onClick = { showSleepTimerSheet = true }
                    )

                    // 4. Single 2x Shake Vibration Orb (📳 — Only here, removed from top!)
                    DoubleShakeVibrationOrb(
                        isEnabled = doubleShakeEnabled,
                        onClick = { showShakeConfigSheet = true },
                        size = 46.dp
                    )

                    // 5. 3-Dot Menu Orb (⋮ — Moved to bottom-right of middle row for easy thumb reach!)
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
                // Left: Shuffle (🔀) | Center: Prev (⏮), Play/Pause (▶/⏸), Next (⏭) | Right: Repeat (🔁/🔂)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Leftmost ("Ulte Haath"): Shuffle Toggle Button
                    IconButton(
                        onClick = {
                            viewModel.toggleShuffleMode()
                            val msg = if (!isShuffleMode) {
                                if (isHindi) "शफ़ल चालू (बिना रिपीट के)" else "Shuffle ON (Non-Repeating)"
                            } else {
                                if (isHindi) "शफ़ल बंद" else "Shuffle OFF"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (isShuffleMode) HybridEmerald.copy(alpha = 0.22f) else Color.Transparent
                            )
                            .testTag("music_shuffle_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffleMode) HybridEmerald else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
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

        if (showVolumeHud) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 18.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xEB0B0F1A))
                    .border(
                        1.5.dp,
                        if (audioVolumePercent > 100) Color(0xFFFF6D00) else HybridEmerald,
                        RoundedCornerShape(18.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = if (audioVolumePercent > 100) Color(0xFFFF6D00) else HybridEmerald,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "$audioVolumePercent%",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }

    // --- Sleep Timer Dialog (5m, 10m, 20m, 45m, 60m + Custom Minutes Input) ---
    if (showSleepTimerSheet) {
        var customMinutesText by remember { mutableStateOf("") }
        val presets = listOf(5, 10, 20, 45, 60)

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
                        text = if (isHindi) "स्लीप टाइमर (Sleep Timer)" else "Sleep Timer",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (sleepTimerMinutes > 0) {
                            if (isHindi) "सक्रिय टाइमर: $sleepTimerMinutes मिनट बाद गाना रुक जाएगा" else "Active: Music stops in $sleepTimerMinutes min"
                        } else {
                            if (isHindi) "समय चुनें या नीचे अपना समय लिखें:" else "Select a preset or enter custom minutes below:"
                        },
                        color = if (sleepTimerMinutes > 0) HybridGold else HybridTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                            if (isHindi) "स्लीप टाइमर: $mins मिनट सेट किया गया" else "Sleep Timer set for $mins minutes",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        showSleepTimerSheet = false
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${mins}m",
                                    color = if (selected) HybridGold else Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
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
                                    text = if (isHindi) "कस्टम मिनट (जैसे 15, 30, 90)" else "Custom Minutes (e.g. 15, 30, 90)",
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
                                    showSleepTimerSheet = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = HybridEmerald)
                        ) {
                            Text(
                                text = if (isHindi) "सेट" else "Set",
                                color = Color.Black,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    if (sleepTimerMinutes > 0) {
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
                    Text(if (isHindi) "बंद करें" else "Close", color = HybridTextSecondary)
                }
            }
        )
    }

    // --- 3-Dot More Options Sheet ---
    if (showMoreOptionsSheet) {
        AlertDialog(
            onDismissRequest = { showMoreOptionsSheet = false },
            containerColor = HybridCardBg,
            title = {
                Text(
                    text = currentTrack?.cleanTitle ?: (if (isHindi) "गाने के विकल्प" else "Song Options"),
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = HybridEmerald, modifier = Modifier.size(20.dp))
                                Text(
                                    text = if (isHindi) "तेज़ शेयर" else "Fast Share",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = HybridCyan, modifier = Modifier.size(20.dp))
                                Text(
                                    text = if (isHindi) "ऐप शेयर" else "Share App",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Permanent 3D Visualizer ON/OFF Switch
                    LiquidGlassBox(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 14.dp,
                        tintColor = if (visualizer3DEnabled) HybridEmerald else Color.White,
                        glassAlpha = if (visualizer3DEnabled) 0.26f else 0.14f,
                        onClick = { viewModel.toggleVisualizer3DEnabled() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
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
                                        text = if (isHindi) "3D विज़ुअलाइज़र (ON / OFF)" else "3D Visualizer Animation",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = if (visualizer3DEnabled) {
                                            if (isHindi) "चालू है • बंद करने के लिए टैप करें" else "Active • Tap to turn OFF permanently"
                                        } else {
                                            if (isHindi) "बंद है (बैटरी और मेमोरी सेवर)" else "OFF (Clean & Smooth Mode)"
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

                    // 15 3D Visualizer Themes Picker
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 11.dp),
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
                                        text = if (isHindi) "15 3D म्यूज़िक थीम्स चुनें" else "15 3D Visualizer Themes",
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

                    // 200% Volume Boost & Speed/Pitch Row
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    tint = if (audioVolumePercent > 100) Color(0xFFFF6D00) else HybridGold,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Vol $audioVolumePercent%",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Speed, contentDescription = null, tint = HybridCyan, modifier = Modifier.size(20.dp))
                                Text(
                                    text = "${playbackSpeed}x Speed",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // A-B Repeat & Full File Actions
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(11.dp),
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.FolderShared, contentDescription = null, tint = HybridCoralRed, modifier = Modifier.size(18.dp))
                                Text(
                                    text = if (isHindi) "फ़ाइल/हटाएं" else "File / Delete",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
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

    // --- Up Next Queue Dialog with REAL Album Art / Video Thumbnails (Fixes Screenshot 3) ---
    if (showUpNextQueueSheet) {
        AlertDialog(
            onDismissRequest = { showUpNextQueueSheet = false },
            containerColor = HybridCardBg,
            title = {
                Text(
                    text = if (isHindi) "अगले गाने (${activeQueue.size})" else "Up Next Queue (${activeQueue.size})",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(activeQueue, key = { _, item -> item.id }) { _, item ->
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
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Real Album Art / Video Frame Photo instead of generic music note!
                                GuaranteedAlbumArt(
                                    mediaPath = item.path,
                                    title = item.cleanTitle,
                                    isVideo = item.isVideo,
                                    modifier = Modifier.size(44.dp),
                                    cornerRadius = 10.dp,
                                    accentColor = if (isCurrent) HybridEmerald else HybridCyan
                                )
                                Spacer(modifier = Modifier.width(10.dp))
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
                                        text = item.artist.ifBlank { if (item.isVideo) "Video Song" else "Local Artist" },
                                        color = HybridTextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = item.formattedDuration,
                                    color = HybridTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
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
