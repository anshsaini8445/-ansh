package com.example.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.MediaItem
import com.example.data.scanner.DeepMediaScanner
import com.example.ui.components.LiquidGlassBox
import com.example.ui.components.LiquidGlassEqualizerWidget
import com.example.ui.components.LiquidGlassIconOrb
import com.example.ui.components.LiquidGlassShakeOrbWidget
import com.example.ui.components.LiquidGlassVolumeBoosterWidget
import com.example.ui.menus.MediaSevenActionDialog
import com.example.ui.theme.*
import com.example.viewmodel.PlayerViewModel

private enum class AudioVisualCategory(val key: String, val icon: ImageVector, val color: Color) {
    SONGS("Songs", Icons.Default.MusicNote, Color(0xFF00E676)),
    FOLDERS("Folders", Icons.Default.Folder, Color(0xFF00E5FF)),
    ALBUMS("Albums", Icons.Default.Album, Color(0xFFD500F9)),
    ARTISTS("Artists", Icons.Default.MicExternalOn, Color(0xFFFFEA00)),
    FAVORITES("Favorites", Icons.Default.Favorite, Color(0xFFFF1744))
}

@Composable
fun AudioLibraryScreen(
    viewModel: PlayerViewModel,
    onOpenStudioPlayer: (MediaItem) -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenTurboShare: () -> Unit,
    onHybridShareItem: (MediaItem) -> Unit = { _ -> onOpenTurboShare() },
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
    val personaMode by viewModel.demographicMode.collectAsState()

    var selectedCategory by remember { mutableStateOf("Songs") }
    var selectedGroupFilter by remember { mutableStateOf<String?>(null) }
    var menuTargetTrack by remember { mutableStateOf<MediaItem?>(null) }

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
            // --- TOP HEADER: Custom App Logo + Pure Visual Liquid Glass Category Icon Orbs + EQ & Booster Widgets ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_app_custom_logo),
                    contentDescription = "Hybrid Player App Icon",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFFFF2A4B).copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))

                // Pure Visual Category Orbs (No hardcoded English text labels!)
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(AudioVisualCategory.entries) { cat ->
                        val active = selectedCategory == cat.key
                        Box(
                            modifier = Modifier
                                .size(if (active) 40.dp else 36.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.verticalGradient(
                                        if (active) listOf(cat.color.copy(alpha = 0.38f), Color(0xFF0A1222))
                                        else listOf(Color.White.copy(alpha = 0.10f), Color(0xFF0B111E))
                                    )
                                )
                                .border(
                                    width = if (active) 1.6.dp else 1.dp,
                                    color = if (active) cat.color else Color.White.copy(alpha = 0.22f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    selectedCategory = cat.key
                                    selectedGroupFilter = null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = cat.icon,
                                contentDescription = cat.key,
                                tint = if (active) cat.color else Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(if (active) 20.dp else 18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Visual Liquid Glass Equalizer Component
                LiquidGlassEqualizerWidget(
                    bands = eqBands,
                    isPlaying = isPlaying,
                    size = 38.dp,
                    onClick = onOpenEqualizer
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Visual Liquid Glass Volume Booster Component
                LiquidGlassVolumeBoosterWidget(
                    isBoostEnabled = is200Boost,
                    volumePercent = audioVolumePct,
                    size = 38.dp,
                    onClick = { viewModel.toggle200Boost() }
                )
            }

            // --- PURE VISUAL ACTION STRIP: Shuffle Orb + 2x Shake Orb + Folder Picker Orb + Rescan Orb + Track Counter Badge ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pure Visual Shuffle Play Orb
                    LiquidGlassIconOrb(
                        icon = Icons.Default.Shuffle,
                        contentDescription = "Shuffle Play",
                        tintColor = HybridEmerald,
                        size = 38.dp,
                        iconSize = 19.dp,
                        isActive = true,
                        onClick = {
                            viewModel.shufflePlayMusic()
                            tracks.firstOrNull()?.let { onOpenStudioPlayer(nowPlaying ?: it) }
                        }
                    )

                    // Visual Liquid Glass 2x Shake Vibration Orb
                    LiquidGlassShakeOrbWidget(
                        isEnabled = doubleShakeEnabled,
                        size = 38.dp,
                        onClick = { viewModel.toggleDoubleShakeEnabled() }
                    )

                    // Pick Real Songs Orb (Liquid Glass)
                    LiquidGlassIconOrb(
                        icon = Icons.Default.FolderOpen,
                        contentDescription = "Pick Real Songs",
                        tintColor = Color(0xFF00E5FF),
                        size = 38.dp,
                        iconSize = 18.dp,
                        onClick = { pickAudioLauncher.launch(arrayOf("audio/*")) }
                    )

                    // Rescan Real Audio Orb
                    LiquidGlassIconOrb(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Rescan Audio",
                        tintColor = Color(0xFFFFEA00),
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

                    // Direct Hybrid Share Orb
                    LiquidGlassIconOrb(
                        icon = Icons.Default.SwapHoriz,
                        contentDescription = "Hybrid Share",
                        tintColor = Color(0xFFD500F9),
                        size = 38.dp,
                        iconSize = 19.dp,
                        onClick = onOpenTurboShare
                    )
                }

                // Pure Visual Numeric Counter Pill ("🎵 24")
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, HybridEmerald.copy(alpha = 0.45f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = null,
                        tint = HybridEmerald,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "${displayedTracks.size}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (selectedGroupFilter != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(HybridCardBg)
                        .border(1.dp, HybridEmerald.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .clickable { selectedGroupFilter = null }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Back", tint = HybridEmerald, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(selectedGroupFilter ?: "", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            // --- MAIN SCROLLABLE LIST CONTAINER (Modifier.weight(1f) so Mini-Player never overlaps song rows!) ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (selectedGroupFilter == null && selectedCategory in listOf("Folders", "Albums", "Artists")) {
                    val groups = remember(tracks, selectedCategory) {
                        when (selectedCategory) {
                            "Folders" -> tracks.groupBy { it.folderName }
                            "Albums" -> tracks.groupBy { it.albumName }
                            else -> tracks.groupBy { it.artist }
                        }
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(groups.entries.toList(), key = { it.key }) { (groupName, groupTracks) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(HybridCardBg)
                                    .border(1.dp, HybridCardBorder, RoundedCornerShape(16.dp))
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
                                    Text("${groupTracks.size}", color = HybridEmerald, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
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
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                LiquidGlassIconOrb(
                                    icon = Icons.Default.LibraryMusic,
                                    contentDescription = "Real Device Audio",
                                    tintColor = Color(0xFF00E5FF),
                                    size = 72.dp,
                                    iconSize = 36.dp,
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
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    LiquidGlassIconOrb(
                                        icon = Icons.Default.Refresh,
                                        contentDescription = "Scan Storage",
                                        tintColor = HybridEmerald,
                                        size = 54.dp,
                                        iconSize = 26.dp,
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
                                    LiquidGlassIconOrb(
                                        icon = Icons.Default.FolderOpen,
                                        contentDescription = "Pick Audio Files",
                                        tintColor = Color(0xFF00E5FF),
                                        size = 54.dp,
                                        iconSize = 26.dp,
                                        isActive = true,
                                        onClick = { pickAudioLauncher.launch(arrayOf("audio/*")) }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(displayedTracks, key = { it.id }) { track ->
                            val isCurrentTrack = nowPlaying?.id == track.id
                            AudioSongRow(
                                track = track,
                                isCurrentTrack = isCurrentTrack,
                                titleFontSize = personaMode.titleFontSize.value.toInt(),
                                metaFontSize = personaMode.metaFontSize.value.toInt(),
                                showPlayOrb = personaMode.showBigPlayButtonOnRow,
                                accentColor = personaMode.primaryAccent,
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

            // --- DOCKED LIQUID GLASS MINI-PLAYER BAR (Never overlaps song rows!) ---
            val activeTrack = nowPlaying ?: tracks.firstOrNull()
            if (activeTrack != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xF50B1324), Color(0xF5131F36))
                            )
                        )
                        .border(1.2.dp, HybridEmerald.copy(alpha = 0.50f), RoundedCornerShape(20.dp))
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
                            .clip(RoundedCornerShape(12.dp))
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
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                            contentDescription = "Play/Pause",
                            tint = HybridEmerald,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.playNext() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
                    }

                    IconButton(
                        onClick = { menuTargetTrack = activeTrack },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Track Menu", tint = Color.White)
                    }
                }
            }
        }
    }

    // --- 7-ACTION COMPREHENSIVE MEDIA SHEET ---
    if (menuTargetTrack != null) {
        val target = menuTargetTrack!!
        val isFav = favorites.any { it.id == target.id }
        MediaSevenActionDialog(
            item = target,
            isFavorite = isFav,
            onDismiss = { menuTargetTrack = null },
            onHybridShare = { item ->
                menuTargetTrack = null
                onHybridShareItem(item)
            },
            onToggleFavorite = { item ->
                viewModel.toggleFavorite(item)
            },
            onLockInVault = { item ->
                viewModel.toggleVaultLock(item)
                Toast.makeText(context, "🔒 Vault Updated", Toast.LENGTH_SHORT).show()
            },
            onPlayBackgroundAudio = { item ->
                viewModel.playMusic(item)
            },
            onPermanentDeleteConfirmed = { item ->
                viewModel.deletePermanently(item)
            }
        )
    }
}

/**
 * 3-Stage Guaranteed Album Art Engine (<1ms):
 * Stage 1: Instant RAM LruCache lookup
 * Stage 2: Immediate High-Definition Studio Vinyl Cover Generator on Canvas
 * Stage 3: Throttled Background MediaMetadataRetriever.embeddedPicture / Coil artwork
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
    titleFontSize: Int = 15,
    metaFontSize: Int = 12,
    showPlayOrb: Boolean = false,
    accentColor: Color = HybridEmerald,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isCurrentTrack) HybridEmerald.copy(alpha = 0.15f) else HybridCardBg)
            .border(
                width = 1.dp,
                color = if (isCurrentTrack) HybridEmerald else HybridCardBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GuaranteedAlbumArt(
            track = track,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.cleanTitle,
                color = if (isCurrentTrack) HybridEmerald else Color.White,
                fontSize = titleFontSize.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "${track.artist} • ${track.formattedDuration} • ${track.format}",
                color = HybridTextSecondary,
                fontSize = metaFontSize.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (showPlayOrb) {
            IconButton(
                onClick = onClick,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.22f))
                    .border(1.dp, accentColor, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play Song",
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(2.dp))
        }

        IconButton(onClick = onMoreClick) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Track Options",
                tint = HybridTextSecondary
            )
        }
    }
}
