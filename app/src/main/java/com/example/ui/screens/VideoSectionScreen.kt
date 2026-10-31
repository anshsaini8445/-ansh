package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import com.example.helpers.HybridCleanerHubHelper
import com.example.helpers.WifiDirectTransferEngine
import com.example.ui.components.LiquidGlassBox
import com.example.ui.components.LiquidGlassIconOrb
import com.example.ui.components.LiquidGlassShakeOrbWidget
import com.example.ui.menus.MediaSevenActionDialog
import com.example.ui.persona.DemographicMode
import com.example.ui.persona.UniversalPersonaSelectorDialog
import com.example.ui.theme.*
import com.example.viewmodel.PlayerViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoSectionScreen(
    viewModel: PlayerViewModel,
    onPlayVideo: (MediaItem) -> Unit,
    onOpenCleanerHub: () -> Unit,
    onOpenTurboShare: () -> Unit,
    onHybridShareItem: (MediaItem) -> Unit = { _ -> onOpenTurboShare() },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val videos by viewModel.videoItems.collectAsState()
    val history by viewModel.historyItems.collectAsState()
    val favorites by viewModel.favoriteItems.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val sortCriteria by viewModel.sortCriteria.collectAsState()
    val sortNewToOld by viewModel.sortNewToOld.collectAsState()
    val isGridView by viewModel.isVideoGridView.collectAsState()
    val hdrBadges by viewModel.hdrCcBadges.collectAsState()
    val doubleShakeEnabled by viewModel.doubleShakeEnabled.collectAsState()
    val personaMode by viewModel.demographicMode.collectAsState()

    var activeSubTab by remember { mutableStateOf("Video") } // "Video" | "Folder" | "Playlist"
    var openedFolderName by remember { mutableStateOf<String?>(null) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showPersonaDialog by remember { mutableStateOf(false) }
    var isHistoryCollapsed by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var actionTargetVideo by remember { mutableStateOf<MediaItem?>(null) }

    val pickVideosLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importPickedMedia(uris, isVideo = true)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.loadMedia()
        Toast.makeText(context, "Scanning device storage for real videos...", Toast.LENGTH_SHORT).show()
    }

    // Multi-Select Mode State
    val selectedIds = remember { mutableStateListOf<String>() }
    val isMultiSelectMode = selectedIds.isNotEmpty()
    var pendingDeleteItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    // Apply PLAYit-style Sort
    val sortedVideos = remember(videos, sortCriteria, sortNewToOld, openedFolderName) {
        val base = if (openedFolderName != null) {
            videos.filter { it.folderName == openedFolderName }
        } else videos

        val comparator = when (sortCriteria) {
            "Name" -> compareBy<MediaItem> { it.cleanTitle.lowercase() }
            "Size" -> compareBy<MediaItem> { it.size }
            "Length" -> compareBy<MediaItem> { it.duration }
            else -> compareBy<MediaItem> { it.dateAdded } // "Date"
        }
        if (sortNewToOld) base.sortedWith(comparator.reversed()) else base.sortedWith(comparator)
    }

    // Real watched history only (never fake/duplicate items taking up half the screen)
    val historyVideos = remember(history) {
        history.filter { it.first.isVideo }
    }

    val historyProgressMap = remember(historyVideos) {
        historyVideos.associate { it.first.id to it.second }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- FIXED-HEIGHT 56.DP SLEEK TOP HEADER (Never stretches vertically!) ---
        if (isMultiSelectMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(HybridCardBg)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${selectedIds.size} Selected",
                    color = HybridEmerald,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        if (selectedIds.size == sortedVideos.size) {
                            selectedIds.clear()
                        } else {
                            selectedIds.clear()
                            selectedIds.addAll(sortedVideos.map { it.id })
                        }
                    }) {
                        Text("All", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { selectedIds.clear() }) {
                        Text("Done", color = HybridEmerald, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
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
                    Text(
                        text = "Hybrid Player",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Universal Experience Mode Orb (Kids 👶 / Senior 👴 / Youth ⚡ / Bharat 🇮🇳 / Global 🌍)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(personaMode.primaryAccent.copy(alpha = 0.32f), Color(0xFF0E1726))
                                )
                            )
                            .border(1.2.dp, personaMode.primaryAccent, CircleShape)
                            .clickable { showPersonaDialog = true }
                            .testTag("persona_mode_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = personaMode.badgeEmoji, fontSize = 15.sp)
                    }

                    // 2. Direct ShareKaro Transfer Orb
                    LiquidGlassIconOrb(
                        icon = Icons.Default.SwapHoriz,
                        contentDescription = "ShareKaro Transfer",
                        tintColor = Color(0xFFD500F9),
                        size = 36.dp,
                        iconSize = 18.dp,
                        isActive = true,
                        onClick = onOpenTurboShare
                    )

                    // 3. Pick Real Videos from Any Folder Orb
                    LiquidGlassIconOrb(
                        icon = Icons.Default.FolderOpen,
                        contentDescription = "Add Real Videos from Device",
                        tintColor = Color(0xFF00E5FF),
                        size = 36.dp,
                        iconSize = 17.dp,
                        isActive = false,
                        onClick = {
                            pickVideosLauncher.launch(arrayOf("video/*"))
                        }
                    )

                    // 4. Visual Liquid Glass 2x Shake Vibration Orb
                    LiquidGlassShakeOrbWidget(
                        isEnabled = doubleShakeEnabled,
                        size = 36.dp,
                        onClick = {
                            viewModel.toggleDoubleShakeEnabled()
                            val st = if (viewModel.doubleShakeEnabled.value) "ON" else "OFF"
                            Toast.makeText(
                                context,
                                "📳 2x Shake (Next Track): $st",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )

                    // 5. Compact 36.dp Cleaner Orb (Fixes the vertical stretching bug from Screenshot!)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("cleaner_bucket_button")
                    ) {
                        LiquidGlassIconOrb(
                            icon = Icons.Default.DeleteSweep,
                            contentDescription = "Cleaner Hub",
                            tintColor = HybridCoralRed,
                            size = 36.dp,
                            iconSize = 18.dp,
                            onClick = onOpenCleanerHub
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(HybridCoralRed)
                                .align(Alignment.TopEnd)
                        )
                    }
                }
            }
        }

        // --- COMPACT 42.DP SUB-TABS & CONTROLS ROW ("Video" | "Folder" | "Playlist" + Rescan + Sort + Grid/List) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    Triple("Video", Icons.Default.SmartDisplay, sortedVideos.size),
                    Triple("Folder", Icons.Default.Folder, videos.map { it.folderName }.distinct().size),
                    Triple("Playlist", Icons.Default.VideoLibrary, playlists.size + 1)
                ).forEach { (tab, tabIcon, count) ->
                    val isSelected = activeSubTab == tab
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            activeSubTab = tab
                            openedFolderName = null
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = tabIcon,
                                contentDescription = tab,
                                tint = if (isSelected) personaMode.primaryAccent else HybridTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "$count",
                                color = if (isSelected) Color.White else HybridTextSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .width(26.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isSelected) personaMode.primaryAccent else Color.Transparent)
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                // Rescan Storage Orb
                LiquidGlassIconOrb(
                    icon = Icons.Default.Refresh,
                    contentDescription = "Scan Real Device Videos",
                    tintColor = HybridEmerald,
                    size = 34.dp,
                    iconSize = 16.dp,
                    onClick = {
                        val perms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            arrayOf(android.Manifest.permission.READ_MEDIA_VIDEO, android.Manifest.permission.READ_MEDIA_AUDIO)
                        } else {
                            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                        permissionLauncher.launch(perms)
                    }
                )

                // Sort Orb
                LiquidGlassIconOrb(
                    icon = Icons.Default.SwapVert,
                    contentDescription = "Sort by",
                    tintColor = Color(0xFF00E5FF),
                    size = 34.dp,
                    iconSize = 16.dp,
                    onClick = { showSortDialog = true }
                )

                // Grid / List Toggle Orb
                LiquidGlassIconOrb(
                    icon = when {
                        activeSubTab == "Playlist" -> Icons.Default.Add
                        isGridView -> Icons.Default.ViewList
                        else -> Icons.Default.GridView
                    },
                    contentDescription = "Toggle View / Create Playlist",
                    tintColor = personaMode.primaryAccent,
                    size = 34.dp,
                    iconSize = 16.dp,
                    onClick = {
                        if (activeSubTab == "Playlist") {
                            showCreatePlaylistDialog = true
                        } else {
                            viewModel.toggleVideoViewMode()
                        }
                    }
                )
            }
        }

        // Optional Folder Back Header when inside a specific folder
        if (openedFolderName != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(HybridCardBg)
                    .clickable { openedFolderName = null }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Folders", tint = HybridEmerald)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$openedFolderName (${sortedVideos.size})",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }
        }

        // --- FULL-SCREEN VIDEO CONTENT AREA (Occupies ~85% of the screen immediately!) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (activeSubTab) {
                "Video" -> {
                    if (sortedVideos.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LiquidGlassBox(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 28.dp,
                                tintColor = personaMode.primaryAccent,
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
                                        icon = Icons.Default.VideoLibrary,
                                        contentDescription = "Real Device Videos",
                                        tintColor = personaMode.primaryAccent,
                                        size = 64.dp,
                                        iconSize = 32.dp,
                                        isActive = true,
                                        onClick = {
                                            val perms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                arrayOf(android.Manifest.permission.READ_MEDIA_VIDEO, android.Manifest.permission.READ_MEDIA_AUDIO)
                                            } else {
                                                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                                            }
                                            permissionLauncher.launch(perms)
                                        }
                                    )
                                    Text(
                                        text = "100% Real Device Video Scanner",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "Grant storage permission to scan all real 8K/4K/HD videos on your phone, or pick videos directly from any folder.",
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
                                                    arrayOf(android.Manifest.permission.READ_MEDIA_VIDEO, android.Manifest.permission.READ_MEDIA_AUDIO)
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
                                                Text("Scan Storage", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                            }
                                        }

                                        LiquidGlassBox(
                                            modifier = Modifier.weight(1f),
                                            cornerRadius = 20.dp,
                                            tintColor = Color(0xFF00E5FF),
                                            glassAlpha = 0.32f,
                                            onClick = {
                                                pickVideosLauncher.launch(arrayOf("video/*"))
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
                                                Text("Pick Videos", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (isGridView) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(sortedVideos, key = { it.id }) { video ->
                                val isSelected = video.id in selectedIds
                                VideoGridCard(
                                    video = video,
                                    isSelected = isSelected,
                                    showBadges = hdrBadges,
                                    progressFraction = ((historyProgressMap[video.id] ?: 0L).toFloat() / video.duration.coerceAtLeast(1L)).coerceIn(0f, 1f),
                                    onClick = {
                                        if (isMultiSelectMode) {
                                            if (isSelected) selectedIds.remove(video.id) else selectedIds.add(video.id)
                                        } else {
                                            onPlayVideo(video)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelected) selectedIds.add(video.id)
                                    },
                                    onMoreClick = {
                                        actionTargetVideo = video
                                    }
                                )
                            }
                        }
                    } else {
                        // Full-Screen Scrollable LazyColumn (History scrolls away cleanly with the list!)
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Scrollable Collapsible History Carousel (Only when real watch history exists)
                            if (historyVideos.isNotEmpty() && openedFolderName == null) {
                                item(key = "scrollable_history_section") {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.clickable { isHistoryCollapsed = !isHistoryCollapsed }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.History,
                                                    contentDescription = "History",
                                                    tint = personaMode.primaryAccent,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "${historyVideos.size}",
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Icon(
                                                    imageVector = if (isHistoryCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                                    contentDescription = "Collapse History",
                                                    tint = HybridTextSecondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    viewModel.clearHistory()
                                                    Toast.makeText(context, "Watch History Cleared", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Clear History",
                                                    tint = HybridTextSecondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        if (!isHistoryCollapsed) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                items(historyVideos, key = { "hist_${it.first.id}" }) { (video, posMs) ->
                                                    val progressFraction = if (video.duration > 0) {
                                                        (posMs.toFloat() / video.duration.toFloat()).coerceIn(0.12f, 1f)
                                                    } else 0.35f

                                                    Box(
                                                        modifier = Modifier
                                                            .size(width = 132.dp, height = 72.dp)
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(HybridCardBg)
                                                            .border(1.dp, HybridCardBorder, RoundedCornerShape(10.dp))
                                                            .clickable { onPlayVideo(video) }
                                                    ) {
                                                        GuaranteedVideoThumbnail(
                                                            video = video,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .background(
                                                                    Brush.verticalGradient(
                                                                        colors = listOf(Color.Transparent, Color(0xCC000000))
                                                                    )
                                                                )
                                                        )
                                                        if (hdrBadges) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .align(Alignment.BottomStart)
                                                                    .padding(start = 5.dp, bottom = 6.dp)
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(HybridGold)
                                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                                            ) {
                                                                Text(
                                                                    text = if (video.is8K) "8k" else video.resolution,
                                                                    color = Color.Black,
                                                                    fontSize = 8.sp,
                                                                    fontWeight = FontWeight.ExtraBold
                                                                )
                                                            }
                                                        }
                                                        Box(
                                                            modifier = Modifier
                                                                .align(Alignment.BottomEnd)
                                                                .padding(end = 5.dp, bottom = 6.dp)
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(Color(0x99000000))
                                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                                        ) {
                                                            Text(
                                                                text = "▶ ${video.formattedDuration}",
                                                                color = Color.White,
                                                                fontSize = 8.sp,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                        }
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(3.dp)
                                                                .align(Alignment.BottomCenter)
                                                                .background(Color(0x44FFFFFF))
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth(progressFraction)
                                                                    .fillMaxHeight()
                                                                    .background(personaMode.primaryAccent)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            items(sortedVideos, key = { it.id }) { video ->
                                val isSelected = video.id in selectedIds
                                VideoDetailedRowItem(
                                    video = video,
                                    isSelected = isSelected,
                                    showBadges = hdrBadges,
                                    progressFraction = ((historyProgressMap[video.id] ?: 0L).toFloat() / video.duration.coerceAtLeast(1L)).coerceIn(0f, 1f),
                                    personaMode = personaMode,
                                    onClick = {
                                        if (isMultiSelectMode) {
                                            if (isSelected) selectedIds.remove(video.id) else selectedIds.add(video.id)
                                        } else {
                                            onPlayVideo(video)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelected) selectedIds.add(video.id)
                                    },
                                    onMoreClick = {
                                        actionTargetVideo = video
                                    }
                                )
                            }
                        }
                    }
                }

                "Folder" -> {
                    val groupedFolders = remember(videos) {
                        videos.groupBy { it.folderName }
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(groupedFolders.entries.toList(), key = { it.key }) { (folderName, folderVideos) ->
                            FolderFourThumbnailCard(
                                folderName = folderName,
                                folderVideos = folderVideos,
                                onOpenFolder = {
                                    openedFolderName = folderName
                                    activeSubTab = "Video"
                                },
                                onPlayDirectVideo = onPlayVideo
                            )
                        }
                    }
                }

                "Playlist" -> {
                    val favVideos = favorites.filter { it.isVideo }
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Clapperboard Design "Favorite Videos" Card
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(HybridCardBg)
                                    .border(1.dp, HybridCardBorder, RoundedCornerShape(16.dp))
                                    .clickable {
                                        if (favVideos.isNotEmpty()) {
                                            onPlayVideo(favVideos.first())
                                        } else if (videos.isNotEmpty()) {
                                            onPlayVideo(videos.first())
                                        }
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(HybridEmerald.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MovieFilter,
                                        contentDescription = "Clapperboard Favorite Videos",
                                        tint = HybridEmerald,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Favorite Videos",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = "${favVideos.size} Clapperboard Favorites",
                                        color = HybridTextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.PlayCircleFilled,
                                    contentDescription = "Play Favorites",
                                    tint = HybridEmerald,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        items(playlists, key = { it.id }) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(HybridCardBg)
                                    .border(1.dp, HybridCardBorder, RoundedCornerShape(16.dp))
                                    .clickable {
                                        if (videos.isNotEmpty()) onPlayVideo(videos.first())
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideoLibrary,
                                    contentDescription = null,
                                    tint = HybridCyan,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(playlist.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("Custom Studio Playlist", color = HybridTextSecondary, fontSize = 12.sp)
                                }
                                IconButton(onClick = { viewModel.removePlaylist(playlist.id) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Playlist", tint = HybridCoralRed)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- BOTTOM MULTI-SELECT ACTION DOCK ("Turbo Share", "Share Apps", "Delete") ---
        if (isMultiSelectMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HybridCardBg)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    selectedIds.clear()
                    onOpenTurboShare()
                }) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = HybridEmerald)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Turbo Share", color = Color.White, fontWeight = FontWeight.Bold)
                }

                TextButton(onClick = {
                    val selectedItems = videos.filter { it.id in selectedIds }
                    if (selectedItems.isNotEmpty()) {
                        WifiDirectTransferEngine.shareRealFilesViaSystem(context, selectedItems)
                    }
                    selectedIds.clear()
                }) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = HybridCyan)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share Apps", color = Color.White, fontWeight = FontWeight.Bold)
                }

                TextButton(onClick = {
                    pendingDeleteItems = videos.filter { it.id in selectedIds }
                }) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = HybridCoralRed)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", color = HybridCoralRed, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // --- PLAYit-Style "Sort by" Dialog ---
    if (showSortDialog) {
        var tempCriteria by remember { mutableStateOf(sortCriteria) }
        var tempNewToOld by remember { mutableStateOf(sortNewToOld) }

        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            containerColor = HybridCardBg,
            title = {
                Text("Sort by", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            },
            text = {
                Column {
                    listOf("Date", "Name", "Size", "Length").forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { tempCriteria = option }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = tempCriteria == option,
                                onClick = { tempCriteria = option },
                                colors = RadioButtonDefaults.colors(selectedColor = HybridEmerald)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(option, color = Color.White, fontSize = 15.sp)
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = HybridCardBorder
                    )

                    listOf(
                        true to "From new to old",
                        false to "From old to new"
                    ).forEach { (isDesc, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { tempNewToOld = isDesc }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = tempNewToOld == isDesc,
                                onClick = { tempNewToOld = isDesc },
                                colors = RadioButtonDefaults.colors(selectedColor = HybridEmerald)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, color = Color.White, fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setSortOptions(tempCriteria, tempNewToOld)
                    showSortDialog = false
                }) {
                    Text("OK", color = HybridEmerald, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSortDialog = false }) {
                    Text("Cancel", color = HybridTextSecondary)
                }
            }
        )
    }

    // --- Create Playlist Dialog ---
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            containerColor = HybridCardBg,
            title = { Text("Create Video Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("Playlist name...", color = HybridTextSecondary) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        viewModel.addPlaylist(newPlaylistName.trim())
                        newPlaylistName = ""
                        showCreatePlaylistDialog = false
                    }
                }) {
                    Text("Create", color = HybridEmerald, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel", color = HybridTextSecondary)
                }
            }
        )
    }

    // --- 30-Day Recycle Bin Safe Delete Dialog ---
    if (pendingDeleteItems.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingDeleteItems = emptyList() },
            containerColor = HybridCardBg,
            title = {
                Text(
                    text = "Safe Delete (${pendingDeleteItems.size} file${if (pendingDeleteItems.size > 1) "s" else ""})",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Keep your videos safe in the 30-Day Recycle Bin so you can restore them anytime, or delete them permanently.",
                        color = HybridTextSecondary,
                        fontSize = 13.sp
                    )
                    Button(
                        onClick = {
                            viewModel.moveBatchToRecycleBin(pendingDeleteItems)
                            pendingDeleteItems = emptyList()
                            selectedIds.clear()
                            Toast.makeText(context, "Moved to 30-Day Recycle Bin", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HybridEmerald),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "MOVE TO RECYCLE BIN (30 DAYS SAFE)",
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            pendingDeleteItems.forEach { viewModel.deletePermanently(it) }
                            pendingDeleteItems = emptyList()
                            selectedIds.clear()
                            Toast.makeText(context, "Deleted Permanently", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete Permanently", color = HybridCoralRed, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pendingDeleteItems = emptyList() }) {
                    Text("Cancel", color = HybridTextSecondary)
                }
            }
        )
    }

    // --- 7-ACTION COMPREHENSIVE VIDEO OPTIONS SHEET ---
    if (actionTargetVideo != null) {
        val target = actionTargetVideo!!
        val isFav = favorites.any { it.id == target.id }
        MediaSevenActionDialog(
            item = target,
            isFavorite = isFav,
            onDismiss = { actionTargetVideo = null },
            onHybridShare = { vid ->
                actionTargetVideo = null
                onHybridShareItem(vid)
            },
            onToggleFavorite = { vid ->
                viewModel.toggleFavorite(vid)
            },
            onLockInVault = { vid ->
                viewModel.toggleVaultLock(vid)
                Toast.makeText(context, "🔒 Moved to Privacy Vault", Toast.LENGTH_SHORT).show()
            },
            onPlayBackgroundAudio = { vid ->
                viewModel.playMusic(vid)
                Toast.makeText(context, "🎧 Playing Video as Background Audio", Toast.LENGTH_SHORT).show()
            },
            onPermanentDeleteConfirmed = { vid ->
                viewModel.deletePermanently(vid)
            }
        )
    }

    // --- UNIVERSAL DEMOGRAPHIC & GLOBAL PERSONA SELECTOR DIALOG (Kids, Seniors, Youth, Bharat, Global) ---
    if (showPersonaDialog) {
        UniversalPersonaSelectorDialog(
            activeMode = personaMode,
            onSelectMode = { mode ->
                viewModel.selectDemographicMode(mode)
                Toast.makeText(
                    context,
                    "${mode.badgeEmoji} ${mode.title} Activated!",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = { showPersonaDialog = false }
        )
    }
}

/**
 * 4-Stage Guaranteed Video Thumbnail Composable:
 * Stage 1: Instant RAM LruCache (<1ms)
 * Stage 2: Immediate Cinema Poster Fallback (Canvas gradient + clapper vector)
 * Stage 3 & 4: Hardware frame / MediaMetadataRetriever / Coil AsyncImage
 */
@Composable
fun GuaranteedVideoThumbnail(
    video: MediaItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var localBitmap by remember(video.path) {
        mutableStateOf<Bitmap?>(DeepMediaScanner.getCachedBitmap(video.path))
    }

    LaunchedEffect(video.path) {
        if (localBitmap == null && !video.path.startsWith("http")) {
            localBitmap = DeepMediaScanner.loadOrExtractBitmap(video.path, isVideo = true, context = context)
        }
    }

    Box(modifier = modifier) {
        // Stage 2: Immediate Cinema Poster Fallback (Never blank/black while scrolling)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF1A2639), Color(0xFF0F172A), Color(0xFF141E30))
                )
            )
            // Subtle film reel rings
            drawCircle(
                color = Color(0x182ECC71),
                radius = size.minDimension * 0.34f
            )
        }

        if (localBitmap != null) {
            Image(
                bitmap = localBitmap!!.asImageBitmap(),
                contentDescription = video.cleanTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (video.coverUrl != null) {
            AsyncImage(
                model = video.coverUrl,
                contentDescription = video.cleanTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoDetailedRowItem(
    video: MediaItem,
    isSelected: Boolean,
    showBadges: Boolean,
    progressFraction: Float,
    personaMode: DemographicMode = DemographicMode.YOUTH_PRO,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val cornerRad = if (personaMode == DemographicMode.KIDS_MAGIC) 20.dp else 14.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRad))
            .background(if (isSelected) HybridEmerald.copy(alpha = 0.16f) else HybridCardBg)
            .border(
                width = if (personaMode == DemographicMode.SENIOR_EASY || personaMode == DemographicMode.KIDS_MAGIC) 1.5.dp else 1.dp,
                color = if (isSelected) HybridEmerald else if (personaMode == DemographicMode.SENIOR_EASY) personaMode.primaryAccent.copy(alpha = 0.45f) else HybridCardBorder,
                shape = RoundedCornerShape(cornerRad)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail with Golden "8k" Badge, "▶ 02:05" Duration Pill, and Bottom Progress Bar
        Box(
            modifier = Modifier
                .size(width = personaMode.thumbnailWidth, height = personaMode.thumbnailHeight)
                .clip(RoundedCornerShape(10.dp))
        ) {
            GuaranteedVideoThumbnail(
                video = video,
                modifier = Modifier.fillMaxSize()
            )

            // Bottom-Left Golden "8k" Badge
            if (showBadges) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 6.dp, bottom = 7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (video.is8K) HybridGold else Color(0xCC141E30))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = video.resolution,
                        color = if (video.is8K) Color.Black else HybridCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Bottom-Right Duration Pill ("▶ 02:05")
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xB3000000))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "▶ ${video.formattedDuration}",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Bottom Resume Progress Line
            if (progressFraction > 0.02f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color(0x44FFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .fillMaxHeight()
                            .background(HybridEmerald)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Info Column: 2-line readable title, "4k | 412.44MB", Folder Source Chip
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.cleanTitle,
                color = Color.White,
                fontSize = personaMode.titleFontSize,
                fontWeight = if (personaMode == DemographicMode.SENIOR_EASY) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = (personaMode.titleFontSize.value + 4f).sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${video.resolution} • ${video.format} • ${video.formattedSize}${if (video.isPartialDownload) " • Partial" else ""}",
                color = HybridTextSecondary,
                fontSize = personaMode.metaFontSize
            )
            Spacer(modifier = Modifier.height(5.dp))
            // Folder Source Chip
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(personaMode.primaryAccent.copy(alpha = 0.14f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = personaMode.primaryAccent,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = video.folderName,
                    color = personaMode.primaryAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // In Senior Easy, Kids Magic, or Bharat Desi Mode, show a bright 1-tap Play Orb right on the row!
        if (personaMode.showBigPlayButtonOnRow) {
            IconButton(
                onClick = onClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(personaMode.primaryAccent.copy(alpha = 0.22f))
                    .border(1.dp, personaMode.primaryAccent, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Instant Play",
                    tint = personaMode.primaryAccent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(2.dp))
        }

        IconButton(onClick = onMoreClick) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "7 Video Actions",
                tint = HybridTextSecondary
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoGridCard(
    video: MediaItem,
    isSelected: Boolean,
    showBadges: Boolean,
    progressFraction: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) HybridEmerald.copy(alpha = 0.16f) else HybridCardBg)
            .border(1.dp, if (isSelected) HybridEmerald else HybridCardBorder, RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .clip(RoundedCornerShape(10.dp))
        ) {
            GuaranteedVideoThumbnail(video = video, modifier = Modifier.fillMaxSize())

            if (showBadges) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (video.is8K) HybridGold else Color(0xCC141E30))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = video.resolution,
                        color = if (video.is8K) Color.Black else HybridCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xB3000000))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "▶ ${video.formattedDuration}",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (progressFraction > 0.02f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color(0x44FFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .fillMaxHeight()
                            .background(HybridEmerald)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.cleanTitle,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${video.resolution} • ${video.formattedSize}",
                    color = HybridTextSecondary,
                    fontSize = 11.sp
                )
            }
            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "7 Video Actions",
                    tint = HybridTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Folder Tab Card with 4-Thumbnail Preview Strip directly below the folder header.
 */
@Composable
private fun FolderFourThumbnailCard(
    folderName: String,
    folderVideos: List<MediaItem>,
    onOpenFolder: () -> Unit,
    onPlayDirectVideo: (MediaItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HybridCardBg)
            .border(1.dp, HybridCardBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        // Folder Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenFolder() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = folderName,
                    tint = HybridEmerald,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = folderName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "${folderVideos.size} Videos",
                        color = HybridTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open Folder",
                tint = HybridTextSecondary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 4-Thumbnail Horizontal Preview Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            folderVideos.take(4).forEach { previewVideo ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPlayDirectVideo(previewVideo) }
                ) {
                    GuaranteedVideoThumbnail(
                        video = previewVideo,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xAA000000))
                            .padding(horizontal = 3.dp)
                    ) {
                        Text(
                            text = previewVideo.formattedDuration,
                            color = Color.White,
                            fontSize = 8.sp
                        )
                    }
                }
            }
            // Fill remaining slots up to 4 so strip stays symmetrical
            repeat((4 - folderVideos.size).coerceAtLeast(0)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x11FFFFFF))
                )
            }
        }
    }
}
