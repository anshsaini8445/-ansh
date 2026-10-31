package com.example.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.MediaItem
import com.example.data.scanner.DeepMediaScanner
import com.example.ui.components.LiquidGlassBox
import com.example.ui.components.LiquidGlassEqualizerWidget
import com.example.ui.components.LiquidGlassIconOrb
import com.example.ui.components.LiquidGlassShakeOrbWidget
import com.example.ui.components.LiquidGlassVolumeBoosterWidget
import com.example.ui.theme.*
import com.example.viewmodel.PlayerViewModel

@Composable
fun AudioLibraryScreen(
    viewModel: PlayerViewModel,
    onOpenStudioPlayer: (MediaItem) -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenTurboShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tracks by viewModel.musicItems.collectAsState()
    val favorites by viewModel.favoriteItems.collectAsState()
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val doubleShakeEnabled by viewModel.doubleShakeEnabled.collectAsState()
    val eqBands by viewModel.equalizerBands.collectAsState()
    val is200Boost by viewModel.is200BoostEnabled.collectAsState()
    val audioVolumePct by viewModel.audioVolumePercent.collectAsState()

    var selectedCategory by remember { mutableStateOf("Songs") } // "Songs", "Folders", "Albums", "Artists", "Favorites"
    var selectedGroupFilter by remember { mutableStateOf<String?>(null) }
    var menuTargetTrack by remember { mutableStateOf<MediaItem?>(null) }
    var showTopMenu by remember { mutableStateOf(false) }

    val pickAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importPickedMedia(uris, isVideo = false)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.loadMedia()
        Toast.makeText(context, "Scanning device storage for real songs...", Toast.LENGTH_SHORT).show()
    }

    val displayedTracks = remember(tracks, favorites, selectedCategory, selectedGroupFilter) {
        when {
            selectedGroupFilter != null -> tracks.filter {
                it.folderName == selectedGroupFilter || it.albumName == selectedGroupFilter || it.artist == selectedGroupFilter
            }
            selectedCategory == "Favorites" -> favorites.filter { !it.isVideo }
            else -> tracks
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- TOP HEADER: Custom App Logo + Scrollable Category Pills + Borderless Equalizer & 3-Dot Icons ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_app_custom_logo),
                    contentDescription = "Hybrid Player App Icon",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .border(1.dp, Color(0xFFFF2A4B).copy(alpha = 0.65f), RoundedCornerShape(9.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(listOf("Songs", "Folders", "Albums", "Artists", "Favorites")) { pill ->
                        val active = selectedCategory == pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (active) HybridEmerald else HybridCardBg)
                                .border(1.dp, if (active) HybridEmerald else HybridCardBorder, RoundedCornerShape(20.dp))
                                .clickable {
                                    selectedCategory = pill
                                    selectedGroupFilter = null
                                }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = pill,
                                color = if (active) Color.Black else Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Visual Liquid Glass Equalizer Component (No "Equalizer" text label!)
                LiquidGlassEqualizerWidget(
                    bands = eqBands,
                    isPlaying = isPlaying,
                    size = 40.dp,
                    onClick = onOpenEqualizer
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Visual Liquid Glass Volume Booster Component (No "Volume Boost" text label!)
                LiquidGlassVolumeBoosterWidget(
                    isBoostEnabled = is200Boost,
                    volumePercent = audioVolumePct,
                    size = 40.dp,
                    onClick = {
                        viewModel.toggle200Boost()
                    }
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Pick Real Songs Orb (Liquid Glass)
                LiquidGlassIconOrb(
                    icon = Icons.Default.FolderOpen,
                    contentDescription = "Pick Real Songs",
                    tintColor = Color(0xFF00E5FF),
                    size = 38.dp,
                    iconSize = 18.dp,
                    onClick = {
                        pickAudioLauncher.launch(arrayOf("audio/*"))
                    }
                )
            }

            // --- ACTION STRIP: Liquid Glass "Shuffle" + Visual Shake Orb + Rescan Orb + Song Counter ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiquidGlassBox(
                        cornerRadius = 24.dp,
                        tintColor = HybridEmerald,
                        glassAlpha = 0.25f,
                        onClick = {
                            viewModel.shufflePlayMusic()
                            tracks.firstOrNull()?.let { onOpenStudioPlayer(nowPlaying ?: it) }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle Play",
                                tint = HybridEmerald,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Shuffle",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Visual Liquid Glass 2x Shake Vibration Orb
                    LiquidGlassShakeOrbWidget(
                        isEnabled = doubleShakeEnabled,
                        size = 38.dp,
                        onClick = {
                            viewModel.toggleDoubleShakeEnabled()
                            val st = if (viewModel.doubleShakeEnabled.value) "ON" else "OFF"
                            Toast.makeText(
                                context,
                                "📳 2x Shake Vibration (Next Song): $st",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )

                    // Rescan Real Audio Orb
                    LiquidGlassIconOrb(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Rescan Audio",
                        tintColor = HybridCyan,
                        size = 38.dp,
                        iconSize = 18.dp,
                        onClick = {
                            val perms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO, android.Manifest.permission.READ_MEDIA_VIDEO)
                            } else {
                                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                            permissionLauncher.launch(perms)
                        }
                    )
                }

                Text(
                    text = "${displayedTracks.size} Songs",
                    color = HybridTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (selectedGroupFilter != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(HybridCardBg)
                        .clickable { selectedGroupFilter = null }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Back", tint = HybridEmerald, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Showing: $selectedGroupFilter", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            // --- GROUP LIST OR SONG ROWS ---
            if (selectedGroupFilter == null && selectedCategory in listOf("Folders", "Albums", "Artists")) {
                val groups = remember(tracks, selectedCategory) {
                    when (selectedCategory) {
                        "Folders" -> tracks.groupBy { it.folderName }
                        "Albums" -> tracks.groupBy { it.albumName }
                        else -> tracks.groupBy { it.artist }
                    }
                }
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(groups.entries.toList(), key = { it.key }) { (groupName, groupTracks) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(HybridCardBg)
                                .border(1.dp, HybridCardBorder, RoundedCornerShape(14.dp))
                                .clickable { selectedGroupFilter = groupName }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GuaranteedAlbumArt(
                                track = groupTracks.first(),
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(groupName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("${groupTracks.size} Tracks", color = HybridTextSecondary, fontSize = 12.sp)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = HybridTextSecondary)
                        }
                    }
                }
            } else if (displayedTracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LiquidGlassBox(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 28.dp,
                        tintColor = Color(0xFF00E5FF),
                        glassAlpha = 0.24f
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            LiquidGlassIconOrb(
                                icon = Icons.Default.LibraryMusic,
                                contentDescription = "Real Device Audio",
                                tintColor = Color(0xFF00E5FF),
                                size = 64.dp,
                                iconSize = 32.dp,
                                isActive = true,
                                onClick = {
                                    val perms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                        arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO, android.Manifest.permission.READ_MEDIA_VIDEO)
                                    } else {
                                        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                                    }
                                    permissionLauncher.launch(perms)
                                }
                            )
                            Text(
                                text = "100% Real Device Music Scanner",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Zero dummy songs! Grant audio permission to scan all real MP3/FLAC/WAV songs on your phone, or pick audio files directly.",
                                color = HybridTextSecondary,
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                LiquidGlassBox(
                                    modifier = Modifier.weight(1f),
                                    cornerRadius = 20.dp,
                                    tintColor = HybridEmerald,
                                    glassAlpha = 0.32f,
                                    onClick = {
                                        val perms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                            arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO, android.Manifest.permission.READ_MEDIA_VIDEO)
                                        } else {
                                            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                                        }
                                        permissionLauncher.launch(perms)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = HybridEmerald, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Scan Songs", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                    }
                                }

                                LiquidGlassBox(
                                    modifier = Modifier.weight(1f),
                                    cornerRadius = 20.dp,
                                    tintColor = Color(0xFF00E5FF),
                                    glassAlpha = 0.32f,
                                    onClick = {
                                        pickAudioLauncher.launch(arrayOf("audio/*"))
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pick Songs", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayedTracks, key = { it.id }) { track ->
                        val isCurrentTrack = nowPlaying?.id == track.id
                        AudioSongRow(
                            track = track,
                            isCurrentTrack = isCurrentTrack,
                            onClick = {
                                viewModel.playMusic(track)
                                onOpenStudioPlayer(track)
                            },
                            onMoreClick = { menuTargetTrack = track }
                        )
                    }
                }
            }
        }

        // --- FLOATING MINI-PLAYER DOCK (Bottom Card with 3-Dot "..." Button instead of Cross 'X') ---
        val activeTrack = nowPlaying ?: tracks.firstOrNull()
        if (activeTrack != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xF2141E30))
                    .border(1.dp, HybridEmerald.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                    .clickable {
                        if (nowPlaying == null) {
                            viewModel.playMusic(activeTrack)
                        }
                        onOpenStudioPlayer(activeTrack)
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GuaranteedAlbumArt(
                    track = activeTrack,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeTrack.cleanTitle,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${activeTrack.artist} • ${activeTrack.format}",
                        color = HybridEmerald,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { viewModel.playPrevious() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White)
                }

                IconButton(
                    onClick = {
                        if (nowPlaying == null) viewModel.playMusic(activeTrack) else viewModel.togglePlayPause()
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                        contentDescription = "Play/Pause",
                        tint = HybridEmerald,
                        modifier = Modifier.size(34.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.playNext() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
                }

                // 3-Dot ("...") Button instead of Cross ('X')
                IconButton(
                    onClick = { menuTargetTrack = activeTrack },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.MoreHoriz, contentDescription = "Mini Player Menu", tint = Color.White)
                }
            }
        }
    }

    // --- 3-Dot Song Options Sheet ---
    if (menuTargetTrack != null) {
        val track = menuTargetTrack!!
        AlertDialog(
            onDismissRequest = { menuTargetTrack = null },
            containerColor = HybridCardBg,
            title = {
                Text(
                    text = track.cleanTitle,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            viewModel.toggleFavorite(track)
                            menuTargetTrack = null
                            Toast.makeText(context, "Updated Favorites", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = HybridEmerald)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Add / Remove Favorite", color = Color.White, modifier = Modifier.weight(1f))
                    }
                    TextButton(
                        onClick = {
                            menuTargetTrack = null
                            onOpenTurboShare()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = HybridGold)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Turbo Share Track", color = Color.White, modifier = Modifier.weight(1f))
                    }
                    TextButton(
                        onClick = {
                            viewModel.moveToRecycleBin(track)
                            menuTargetTrack = null
                            Toast.makeText(context, "Moved to 30-Day Recycle Bin", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = HybridCoralRed)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Move to Recycle Bin", color = HybridCoralRed, modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { menuTargetTrack = null }) {
                    Text("Close", color = HybridTextSecondary)
                }
            }
        )
    }
}

/**
 * 3-Stage Guaranteed Album Art Engine (<1ms):
 * Stage 1: Instant RAM LruCache lookup
 * Stage 2: Immediate High-Definition Studio Vinyl Cover Generator on Canvas
 * Stage 3: Background MediaMetadataRetriever.embeddedPicture / Coil artwork
 */
@Composable
fun GuaranteedAlbumArt(
    track: MediaItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var localBitmap by remember(track.path) {
        mutableStateOf<Bitmap?>(DeepMediaScanner.getCachedBitmap(track.path))
    }

    LaunchedEffect(track.path) {
        if (localBitmap == null && !track.path.startsWith("http")) {
            localBitmap = DeepMediaScanner.loadOrExtractBitmap(track.path, isVideo = false, context = context)
        }
    }

    Box(modifier = modifier) {
        // Stage 2: Immediate High-Definition Studio Vinyl Cover Generator
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF1A2942), Color(0xFF0F172A), Color(0xFF1E1B4B))
                )
            )
            val center = Offset(size.width / 2f, size.height / 2f)
            val discRadius = size.minDimension * 0.40f
            drawCircle(color = Color(0xFF111115), radius = discRadius, center = center)
            drawCircle(
                color = Color(0x33FFFFFF),
                radius = discRadius * 0.72f,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawCircle(color = HybridEmerald, radius = discRadius * 0.32f, center = center)
            drawCircle(color = Color.Black, radius = discRadius * 0.10f, center = center)
        }

        if (localBitmap != null) {
            Image(
                bitmap = localBitmap!!.asImageBitmap(),
                contentDescription = track.cleanTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (track.coverUrl != null) {
            AsyncImage(
                model = track.coverUrl,
                contentDescription = track.cleanTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun AudioSongRow(
    track: MediaItem,
    isCurrentTrack: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isCurrentTrack) HybridEmerald.copy(alpha = 0.14f) else HybridCardBg)
            .border(
                width = 1.dp,
                color = if (isCurrentTrack) HybridEmerald else HybridCardBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 52dp Rounded Cover Photo
        GuaranteedAlbumArt(
            track = track,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Underscore-cleaned Song Title & "Artist • Duration • Format" Subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.cleanTitle,
                color = if (isCurrentTrack) HybridEmerald else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "${track.artist} • ${track.formattedDuration} • ${track.format}",
                color = HybridTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Borderless 3-Dot Icon
        IconButton(onClick = onMoreClick) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Track Options",
                tint = HybridTextSecondary
            )
        }
    }
}
