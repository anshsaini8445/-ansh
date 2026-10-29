package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.helpers.AudioPitchHelper
import com.example.ui.theme.*
import com.example.viewmodel.PlayerViewModel
import java.util.Locale
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
    val tracks by viewModel.musicItems.collectAsState()
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackPos by viewModel.playbackPosition.collectAsState()
    val speed by viewModel.playbackSpeed.collectAsState()
    val is200Boost by viewModel.is200BoostEnabled.collectAsState()
    val isShuffle by viewModel.isShuffleMode.collectAsState()
    val favorites by viewModel.favoriteItems.collectAsState()
    val pointA by viewModel.abPointAMs.collectAsState()
    val pointB by viewModel.abPointBMs.collectAsState()
    val sleepMinutes by viewModel.sleepTimerMinutes.collectAsState()
    val activePitchPreset by viewModel.activePitchPreset.collectAsState()

    val currentTrack = nowPlaying ?: tracks.firstOrNull() ?: return
    val currentIndex = remember(tracks, currentTrack) {
        (tracks.indexOfFirst { it.id == currentTrack.id }.takeIf { it >= 0 } ?: 0) + 1
    }
    val nextTrack = remember(tracks, currentTrack) {
        val idx = tracks.indexOfFirst { it.id == currentTrack.id }
        if (idx >= 0 && idx < tracks.lastIndex) tracks[idx + 1] else tracks.firstOrNull()
    }
    val isFavorite = favorites.any { it.id == currentTrack.id }

    var isVinylDiscMode by remember { mutableStateOf(false) }
    var coverSwipeOffsetX by remember { mutableStateOf(0f) }
    var showStudioMenu by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showPitchLabDialog by remember { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            // Minimalist Top Bar: Back arrow, "Now Playing (3/120)", Equalizer icon, 3-Dot icon
            TopAppBar(
                title = {
                    Text(
                        text = "Now Playing ($currentIndex/${tracks.size.coerceAtLeast(1)})",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
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
                    IconButton(onClick = onOpenEqualizer) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = "Equalizer",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { showStudioMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Studio 3-Dot Menu",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HybridDarkBg)
            )
        },
        containerColor = HybridDarkBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // --- CENTER COVER ART (268dp Balanced Card + Single Tap Vinyl Toggle + Swipe Next/Prev) ---
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(268.dp)
                        .offset { IntOffset(animatedSwipeOffset.roundToInt(), 0) }
                        .clip(if (isVinylDiscMode) CircleShape else RoundedCornerShape(26.dp))
                        .border(
                            width = if (isVinylDiscMode) 6.dp else 1.dp,
                            color = if (isVinylDiscMode) Color(0xFF1F2937) else HybridCardBorder,
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
                        // Vinyl Spindle Center Hole
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(HybridDarkBg)
                                .border(4.dp, HybridEmerald, CircleShape),
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

                Spacer(modifier = Modifier.height(12.dp))

                // 3 Swipe Indicator Dots underneath Cover Art
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { idx ->
                        val activeDot = (idx == 0 && !isVinylDiscMode) || (idx == 1 && isVinylDiscMode)
                        Box(
                            modifier = Modifier
                                .width(if (activeDot) 18.dp else 6.dp)
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(if (activeDot) HybridEmerald else Color(0x44FFFFFF))
                        )
                    }
                }
            }

            // --- FULL-WIDTH SONG TITLE (Up to 2 lines, underscore-cleaned) & ARTIST ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = currentTrack.cleanTitle,
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 26.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${currentTrack.artist} • ${currentTrack.albumName}",
                    color = HybridTextSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // --- SECONDARY ACTION STRIP (Above SeekBar): Bezier Heart, "200% Boost", "1.0x" Speed, Share ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.toggleFavorite(currentTrack) }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) HybridCoralRed else Color.White
                    )
                }

                // "200% Boost" Toggle Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (is200Boost) HybridEmerald else HybridCardBg)
                        .border(1.dp, if (is200Boost) HybridEmerald else HybridCardBorder, RoundedCornerShape(18.dp))
                        .clickable { viewModel.toggle200Boost() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (is200Boost) "200% Boost ON" else "200% Boost",
                        color = if (is200Boost) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // "1.0x" Speed Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(HybridCardBg)
                        .border(1.dp, HybridCardBorder, RoundedCornerShape(18.dp))
                        .clickable {
                            val nextSpeed = when (speed) {
                                0.75f -> 1.0f
                                1.0f -> 1.25f
                                1.25f -> 1.5f
                                1.5f -> 2.0f
                                else -> 0.75f
                            }
                            viewModel.setPlaybackSpeed(nextSpeed)
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "%.2fx", speed).replace(".00x", ".0x"),
                        color = HybridCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Listening to ${currentTrack.cleanTitle} on Hybrid Player")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Track"))
                }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Quick Share",
                        tint = Color.White
                    )
                }
            }

            // --- SINGLE SLEEK TIMELINE SEEKBAR + TIMESTAMPS ---
            Column(modifier = Modifier.fillMaxWidth()) {
                val totalDuration = currentTrack.duration.coerceAtLeast(1000L)
                Slider(
                    value = playbackPos.toFloat().coerceIn(0f, totalDuration.toFloat()),
                    onValueChange = { viewModel.seekTo(it.toLong()) },
                    valueRange = 0f..totalDuration.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = HybridEmerald,
                        activeTrackColor = HybridEmerald,
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
                            color = HybridEmerald,
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

            // --- BORDERLESS TRANSPORT ROW (Loop/Shuffle, Prev, Center Play/Pause Circle, Next, Queue) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.toggleShuffleMode() }) {
                    Icon(
                        imageVector = if (isShuffle) Icons.Default.Shuffle else Icons.Default.Repeat,
                        contentDescription = "Shuffle / Loop",
                        tint = if (isShuffle) HybridEmerald else Color.White,
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
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(HybridEmerald)
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
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(HybridCardBg)
                    .border(1.dp, HybridCardBorder, RoundedCornerShape(14.dp))
                    .clickable { showQueueSheet = true }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "UP NEXT",
                    color = HybridEmerald,
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

    // --- 3-DOT AUDIO STUDIO MENU (12 Pure-White Studio Tiles) ---
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
                                if (nextTimer > 0) "Smart Motion Sleep Timer: $nextTimer min" else "Sleep Timer Off",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioTileButton(
                            label = if (is200Boost) "Boost ON" else "200% Boost",
                            icon = Icons.Default.VolumeUp,
                            modifier = Modifier.weight(1f)
                        ) {
                            viewModel.toggle200Boost()
                        }
                        StudioTileButton(
                            label = if (isFavorite) "Favorited" else "Favorite",
                            icon = Icons.Default.Favorite,
                            modifier = Modifier.weight(1f)
                        ) {
                            viewModel.toggleFavorite(currentTrack)
                        }
                        StudioTileButton("Ringtone", Icons.Default.RingVolume, Modifier.weight(1f)) {
                            showStudioMenu = false
                            Toast.makeText(
                                context,
                                "Set '${currentTrack.cleanTitle}' as Phone Ringtone!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioTileButton("A-B Repeat", Icons.Default.RepeatOne, Modifier.weight(1f)) {
                            viewModel.toggleAbRepeatPoint()
                            Toast.makeText(context, "A-B Repeat Point Updated", Toast.LENGTH_SHORT).show()
                        }
                        StudioTileButton("Disc/Cover", Icons.Default.Album, Modifier.weight(1f)) {
                            isVinylDiscMode = !isVinylDiscMode
                            showStudioMenu = false
                        }
                        StudioTileButton("Turbo Share", Icons.Default.Bolt, Modifier.weight(1f)) {
                            showStudioMenu = false
                            onOpenTurboShare()
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioTileButton("Share Apps", Icons.Default.Share, Modifier.weight(1f)) {
                            showStudioMenu = false
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Track: ${currentTrack.cleanTitle}")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share via Apps"))
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
                    Text("Close", color = HybridEmerald, fontWeight = FontWeight.Bold)
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
                                .background(if (active) HybridEmerald.copy(alpha = 0.2f) else HybridCardAlt)
                                .border(1.dp, if (active) HybridEmerald else HybridCardBorder, RoundedCornerShape(12.dp))
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
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = HybridEmerald)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPitchLabDialog = false }) {
                    Text("Done", color = HybridEmerald, fontWeight = FontWeight.Bold)
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
                                .background(if (active) HybridEmerald.copy(alpha = 0.16f) else HybridCardAlt)
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
                                tint = if (active) HybridEmerald else HybridTextSecondary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.cleanTitle,
                                    color = if (active) HybridEmerald else Color.White,
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
                    Text("Close", color = HybridEmerald, fontWeight = FontWeight.Bold)
                }
            }
        )
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
            tint = Color.White, // Pure-White Studio Icon
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
