package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.MediaItem
import com.example.data.scanner.DeepMediaScanner
import com.example.helpers.PermanentDeleteHelper
import com.example.helpers.WifiDirectTransferEngine
import com.example.helpers.timedMediaTouchHold
import com.example.ui.menus.MediaSevenActionDialog
import com.example.ui.persona.DemographicMode
import com.example.ui.theme.*
import com.example.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoSectionScreen(
    viewModel: PlayerViewModel,
    onPlayVideo: (MediaItem) -> Unit,
    onOpenCleanerHub: () -> Unit,
    onOpenTurboShare: () -> Unit,
    onHybridShareItem: (MediaItem) -> Unit = { _ -> onOpenTurboShare() },
    onOpenPdfReader: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val videos by viewModel.videoItems.collectAsState()
    val history by viewModel.historyItems.collectAsState()
    val favorites by viewModel.favoriteItems.collectAsState()
    val sortCriteria by viewModel.sortCriteria.collectAsState()
    val sortNewToOld by viewModel.sortNewToOld.collectAsState()
    val isGridView by viewModel.isVideoGridView.collectAsState()
    val hdrBadges by viewModel.hdrCcBadges.collectAsState()
    val personaMode by viewModel.demographicMode.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val activeTheme by viewModel.activeTheme.collectAsState()
    val customWallpaperUri by viewModel.customWallpaperUri.collectAsState()
    val playlists by viewModel.playlists.collectAsState()

    val isHindi = remember(selectedLanguage) {
        com.example.ui.i18n.WorldLanguageEngine.isHindiActive(selectedLanguage)
    }

    // Live Device Clock & Battery Level for Top Header Bar
    var headerClockText by remember { mutableStateOf("") }
    var headerBatteryPct by remember { mutableIntStateOf(100) }
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val sdf = java.text.SimpleDateFormat("hh:mm a", Locale.getDefault())
                headerClockText = sdf.format(java.util.Date())
                val bm = context.getSystemService(android.content.Context.BATTERY_SERVICE) as? android.os.BatteryManager
                val cap = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
                if (cap in 1..100) headerBatteryPct = cap
            } catch (_: Exception) {
            }
            kotlinx.coroutines.delay(10_000L)
        }
    }

    val subTabKeys = remember { listOf("Video", "Folder", "Status", "Favourite") }
    val subTabPagerState = rememberPagerState(initialPage = 0, pageCount = { subTabKeys.size })
    val activeSubTab = subTabKeys[subTabPagerState.currentPage]
    fun switchSubTab(target: String) {
        val targetIndex = subTabKeys.indexOf(target).coerceAtLeast(0)
        coroutineScope.launch {
            subTabPagerState.animateScrollToPage(targetIndex)
        }
    }
    var openedFolderName by remember { mutableStateOf<String?>(null) }
    var isViewingFavouriteFolder by remember { mutableStateOf(false) }
    var showStatusReelsOverlay by remember { mutableStateOf(false) }
    var initialStatusReelIndex by remember { mutableIntStateOf(0) }

    var showSortDialog by remember { mutableStateOf(false) }
    var showThemeStudioDialog by remember { mutableStateOf(false) }
    var isHistoryCollapsed by remember { mutableStateOf(false) }
    var actionTargetVideo by remember { mutableStateOf<MediaItem?>(null) }

    val galleryWallpaperLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.saveCustomGalleryWallpaperFromUri(uri)
            Toast.makeText(
                context,
                if (isHindi) "✨ आपकी फोटो हर स्क्रीन पर स्थायी रूप से सेट हो गई!" else "✨ Your Gallery Photo is set permanently across all screens!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

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
    }

    val systemBatchDeleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) {
        viewModel.loadMedia()
    }

    // Scan on launch if permission is already granted (MainActivity handles initial permission dialog)
    LaunchedEffect(Unit) {
        val perms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arrayOf(android.Manifest.permission.READ_MEDIA_VIDEO, android.Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val hasPerm = perms.any {
            ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (hasPerm) {
            viewModel.loadMedia()
        }
    }

    // Multi-Select Mode State & Batch Dialog States
    val selectedIds = remember { mutableStateListOf<String>() }
    val isMultiSelectMode = selectedIds.isNotEmpty()
    var pendingDeleteItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var showMultiSelectDetailsDialog by remember { mutableStateOf(false) }
    var showMultiSelectMoreDialog by remember { mutableStateOf(false) }
    var showBatchPlaylistPickerDialog by remember { mutableStateOf(false) }

    val favoriteVideos = remember(favorites, videos) {
        val favIds = favorites.map { it.id }.toSet()
        videos.filter { it.id in favIds }.distinctBy { it.id }
    }

    // Short videos <= 100 seconds (100,000 ms) for Status Reels
    val statusReelVideos = remember(videos) {
        val under100s = videos.filter { !it.isPartialDownload && it.duration in 1L..100_000L }.distinctBy { it.id }
        if (under100s.isNotEmpty()) under100s else videos.distinctBy { it.id }
    }

    // Sort videos while ALWAYS keeping Pending / Half-Downloaded Chrome videos at the VERY TOP
    val sortedVideos = remember(videos, favoriteVideos, sortCriteria, sortNewToOld, openedFolderName, isViewingFavouriteFolder) {
        val base = when {
            isViewingFavouriteFolder -> favoriteVideos
            openedFolderName != null -> videos.filter { it.folderName == openedFolderName }
            else -> videos
        }.distinctBy { it.id }

        val comparator = when (sortCriteria) {
            "Name" -> compareBy<MediaItem> { it.cleanTitle.lowercase() }
            "Size" -> compareBy<MediaItem> { it.size }
            "Length" -> compareBy<MediaItem> { it.duration }
            else -> compareBy<MediaItem> { it.dateAdded } // "Date"
        }
        val sortedNormal = if (sortNewToOld) base.sortedWith(comparator.reversed()) else base.sortedWith(comparator)
        val (pendingList, completeList) = sortedNormal.partition { it.isPartialDownload }
        (pendingList + completeList).distinctBy { it.id }
    }

    val historyVideos = remember(history) {
        history.filter { it.first.isVideo }.distinctBy { it.first.id }
    }

    val historyProgressMap = remember(historyVideos) {
        historyVideos.associate { it.first.id to it.second }
    }

    val historyListState = rememberLazyListState()
    LaunchedEffect(historyVideos.firstOrNull()?.first?.id) {
        if (historyVideos.isNotEmpty()) {
            try {
                historyListState.scrollToItem(0)
            } catch (_: Exception) {
            }
        }
    }

    // Pause background audio automatically when Status Reels playback starts (browsing Video tab keeps audio playing)
    LaunchedEffect(showStatusReelsOverlay, activeSubTab) {
        if (showStatusReelsOverlay || activeSubTab == "Status") {
            viewModel.pauseMusicForVideoPlayback()
        }
    }

    // Back handler when inside a folder or Status Reels overlay
    if (showStatusReelsOverlay || activeSubTab == "Status") {
        BackHandler {
            showStatusReelsOverlay = false
            if (activeSubTab == "Status") {
                switchSubTab("Folder")
            }
        }
    } else if (isMultiSelectMode) {
        BackHandler {
            selectedIds.clear()
        }
    } else if (openedFolderName != null || isViewingFavouriteFolder) {
        BackHandler {
            openedFolderName = null
            isViewingFavouriteFolder = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (customWallpaperUri != null) {
                        Color(0x66050B14)
                    } else {
                        Color(0x33050B14)
                    }
                )
        ) {
            // --- 1. PROMINENT TOP BAR: App Icon + "Hybrid Player" + Clear Device Time & Battery + Theme/Photo Studio ---
            if (isMultiSelectMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(HybridCardBg)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isHindi) "${selectedIds.size} चुने गए" else "${selectedIds.size} Selected",
                        color = HybridEmerald,
                        fontWeight = FontWeight.ExtraBold,
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
                            Text(
                                text = if (isHindi) "सभी (${sortedVideos.size})" else "All (${sortedVideos.size})",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(onClick = { selectedIds.clear() }) {
                            Text(
                                text = if (isHindi) "पूर्ण" else "Done",
                                color = HybridEmerald,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_custom_logo),
                            contentDescription = "Hybrid Player",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    1.5.dp,
                                    Brush.linearGradient(listOf(Color(0xFFFF2A4B), HybridEmerald)),
                                    RoundedCornerShape(12.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isHindi) "हाइब्रिड प्लेयर" else "Hybrid Player",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.2.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (headerClockText.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xCC0D1829))
                                        .border(1.dp, HybridEmerald.copy(alpha = 0.45f), RoundedCornerShape(50))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "🕒 $headerClockText • 🔋 $headerBatteryPct%",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    // Quick Theme & Custom Gallery Photo Picker Button right on Video Screen
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = {
                                galleryWallpaperLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (customWallpaperUri != null) HybridEmerald.copy(alpha = 0.24f)
                                    else HybridCardBg
                                )
                                .border(
                                    1.dp,
                                    if (customWallpaperUri != null) HybridEmerald else HybridCardBorder,
                                    CircleShape
                                )
                                .testTag("video_header_gallery_wallpaper_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wallpaper,
                                contentDescription = if (isHindi) "गैलरी से फोटो लगाएं" else "Set Gallery Photo Wallpaper",
                                tint = if (customWallpaperUri != null) HybridEmerald else Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        IconButton(
                            onClick = { showThemeStudioDialog = true },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(HybridCardBg)
                                .border(1.dp, HybridCardBorder, CircleShape)
                                .testTag("video_header_theme_studio_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = if (isHindi) "थीम बदलें" else "Change Theme",
                                tint = HybridCyan,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }

            // --- 2. WATCH HISTORY RIGHT BELOW "HYBRID PLAYER" HEADER (NEWEST / ABHI KI HISTORY FIRST AT INDEX 0) ---
            if (historyVideos.isNotEmpty() && !isMultiSelectMode && openedFolderName == null && !isViewingFavouriteFolder) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp)
                ) {
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
                                contentDescription = null,
                                tint = personaMode.primaryAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isHindi) "हाल ही में देखे गए (${historyVideos.size})" else "History (${historyVideos.size})",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = if (isHistoryCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                contentDescription = null,
                                tint = HybridTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.clearHistory()
                                Toast.makeText(
                                    context,
                                    if (isHindi) "हिस्ट्री साफ़ की गई" else "Watch History Cleared",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = HybridTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (!isHistoryCollapsed) {
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            state = historyListState,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(historyVideos, key = { _, pair -> "top_hist_${pair.first.id}" }) { index, (video, posMs) ->
                                val progressFraction = if (video.duration > 0) {
                                    (posMs.toFloat() / video.duration.toFloat()).coerceIn(0.12f, 1f)
                                } else 0.35f

                                Box(
                                    modifier = Modifier
                                        .size(width = 132.dp, height = 70.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(HybridCardBg)
                                        .border(
                                            width = if (index == 0) 1.5.dp else 1.dp,
                                            color = if (index == 0) HybridEmerald else HybridCardBorder,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable { onPlayVideo(video) }
                                ) {
                                    GuaranteedVideoThumbnail(
                                        video = video,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (index == 0) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(4.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(HybridEmerald)
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = if (isHindi) "अभी देखा" else "LATEST",
                                                color = Color.Black,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(Color.Transparent, Color(0xCC000000))
                                                )
                                            )
                                    )
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

            // --- 3. CLEAR LABELED SUB-TABS ROW (Video | Folder | Status | Favourite + Multi-Select + Sort + Grid) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val tabs = listOf(
                        Triple("Video", if (isHindi) "वीडियो" else "Video", Icons.Default.SmartDisplay),
                        Triple("Folder", if (isHindi) "फ़ोल्डर" else "Folder", Icons.Default.Folder),
                        Triple("Status", if (isHindi) "स्टेटस" else "Status", Icons.Default.SlowMotionVideo),
                        Triple("Favourite", if (isHindi) "पसंदीदा" else "Favourite", Icons.Default.Favorite)
                    )

                    tabs.forEach { (tabKey, tabLabel, tabIcon) ->
                        val isSelected = activeSubTab == tabKey
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    switchSubTab(tabKey)
                                    openedFolderName = null
                                    isViewingFavouriteFolder = false
                                    if (tabKey == "Status") {
                                        initialStatusReelIndex = 0
                                        showStatusReelsOverlay = true
                                    }
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = tabIcon,
                                    contentDescription = tabLabel,
                                    tint = if (isSelected) personaMode.primaryAccent else HybridTextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = tabLabel,
                                    color = if (isSelected) Color.White else HybridTextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .width(32.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (isSelected) personaMode.primaryAccent else Color.Transparent)
                            )
                        }
                    }
                }

                // Multi-Select Checklist, Sort & View Toggle Icons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (selectedIds.isNotEmpty()) {
                                selectedIds.clear()
                            } else if (sortedVideos.isNotEmpty()) {
                                selectedIds.add(sortedVideos.first().id)
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Checklist,
                            contentDescription = if (isHindi) "कई वीडियो चुनें" else "Multi-Select Videos",
                            tint = if (isMultiSelectMode) HybridCoralRed else HybridTextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    IconButton(
                        onClick = { showSortDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = if (isHindi) "क्रमबद्ध करें" else "Sort by",
                            tint = HybridTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.toggleVideoViewMode() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = if (isHindi) "व्यू बदलें" else "Toggle View",
                            tint = HybridTextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            // Folder / Favourite Back Header when inside a specific folder
            if (openedFolderName != null || isViewingFavouriteFolder) {
                val headerTitle = if (isViewingFavouriteFolder) {
                    if (isHindi) "पसंदीदा (${sortedVideos.size})" else "Favourite (${sortedVideos.size})"
                } else {
                    "$openedFolderName (${sortedVideos.size})"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(HybridCardBg)
                        .clickable {
                            openedFolderName = null
                            isViewingFavouriteFolder = false
                            switchSubTab("Folder")
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (isHindi) "वापस" else "Back",
                        tint = HybridEmerald
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = headerTitle,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                }
            }

            // --- 4. HORIZONTAL PAGER ACROSS THE 4 SUB-TABS (Swipe Left/Right or Tap Tabs!) ---
            HorizontalPager(
                state = subTabPagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                val pageTab = subTabKeys[page]
                when (pageTab) {
                    "Video" -> {
                        if (sortedVideos.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideoLibrary,
                                    contentDescription = null,
                                    tint = HybridTextSecondary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (isHindi) "कोई वीडियो नहीं मिला" else "No videos found",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            val perms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                arrayOf(android.Manifest.permission.READ_MEDIA_VIDEO, android.Manifest.permission.READ_MEDIA_AUDIO)
                                            } else {
                                                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                                            }
                                            permissionLauncher.launch(perms)
                                        }
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = HybridEmerald, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isHindi) "रिफ्रेश करें" else "Refresh",
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { pickVideosLauncher.launch(arrayOf("video/*")) }
                                    ) {
                                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = HybridCyan, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isHindi) "फ़ाइल चुनें" else "Open File",
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        )
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
                                        isHindi = isHindi,
                                        isSelected = isSelected,
                                        isMultiSelectMode = isMultiSelectMode,
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
                                            if (isSelected) selectedIds.remove(video.id) else selectedIds.add(video.id)
                                        },
                                        onMoreClick = {
                                            actionTargetVideo = video
                                        }
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(sortedVideos, key = { it.id }) { video ->
                                    val isSelected = video.id in selectedIds
                                    VideoDetailedRowItem(
                                        video = video,
                                        isHindi = isHindi,
                                        isSelected = isSelected,
                                        isMultiSelectMode = isMultiSelectMode,
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
                                            if (isSelected) selectedIds.remove(video.id) else selectedIds.add(video.id)
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
                            // 1. FIRST FOLDER: STATUS (Reels <= 100s vertical swipe player)
                            item(key = "special_folder_status") {
                                SpecialFolderCard(
                                    title = if (isHindi) "स्टेटस (Status • Reels)" else "Status (Short Reels ≤ 100s)",
                                    subtitle = if (isHindi) {
                                        "${statusReelVideos.size} शॉर्ट वीडियो • ऊपर-नीचे स्वाइप करके देखें"
                                    } else {
                                        "${statusReelVideos.size} Short Videos • Swipe up/down like Reels"
                                    },
                                    icon = Icons.Default.SlowMotionVideo,
                                    accentColor = Color(0xFFFF2A6D),
                                    previewVideos = statusReelVideos,
                                    onClick = {
                                        initialStatusReelIndex = 0
                                        showStatusReelsOverlay = true
                                    }
                                )
                            }

                            // 2. SECOND FOLDER: FAVOURITE (Liked videos from Status & Player)
                            item(key = "special_folder_favourite") {
                                SpecialFolderCard(
                                    title = if (isHindi) "पसंदीदा (Favourite)" else "Favourite",
                                    subtitle = if (isHindi) {
                                        "${favoriteVideos.size} पसंदीदा वीडियो"
                                    } else {
                                        "${favoriteVideos.size} Liked & Saved Videos"
                                    },
                                    icon = Icons.Default.Favorite,
                                    accentColor = Color(0xFFFF5252),
                                    previewVideos = favoriteVideos,
                                    onClick = {
                                        isViewingFavouriteFolder = true
                                        openedFolderName = null
                                        switchSubTab("Video")
                                    }
                                )
                            }

                            // 3. REMAINING DEVICE MEDIA FOLDERS
                            items(groupedFolders.entries.toList(), key = { "folder_${it.key}" }) { (folderName, folderVideos) ->
                                FolderFourThumbnailCard(
                                    folderName = folderName,
                                    isHindi = isHindi,
                                    folderVideos = folderVideos,
                                    onOpenFolder = {
                                        isViewingFavouriteFolder = false
                                        openedFolderName = folderName
                                        switchSubTab("Video")
                                    },
                                    onPlayDirectVideo = onPlayVideo
                                )
                            }
                        }
                    }

                    "Status" -> {
                        StatusReelsVerticalPagerView(
                            reelVideos = statusReelVideos,
                            favorites = favorites,
                            initialIndex = initialStatusReelIndex,
                            isHindi = isHindi,
                            onToggleLike = { vid ->
                                val alreadyFav = favorites.any { it.id == vid.id }
                                viewModel.toggleFavorite(vid)
                                val msg = if (!alreadyFav) {
                                    if (isHindi) "❤️ पसंदीदा में सेव हो गया!" else "❤️ Saved to Favourite!"
                                } else {
                                    if (isHindi) "पसंदीदा से हटाया गया" else "Removed from Favourite"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                            onShareVideo = { vid ->
                                WifiDirectTransferEngine.shareRealFilesViaSystem(context, listOf(vid))
                            },
                            onOpenInFullPlayer = { vid ->
                                showStatusReelsOverlay = false
                                onPlayVideo(vid)
                            },
                            onOpenMoreOptions = { vid ->
                                actionTargetVideo = vid
                            },
                            onClose = {
                                showStatusReelsOverlay = false
                                switchSubTab("Folder")
                            }
                        )
                    }

                    "Favourite" -> {
                        if (favoriteVideos.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FavoriteBorder,
                                    contentDescription = null,
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(52.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (isHindi) "कोई पसंदीदा वीडियो नहीं है" else "No Favourite Videos Yet",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (isHindi) {
                                        "स्टेटस (Status) या वीडियो में ❤️ लाइक बटन दबाने पर वीडियो यहाँ सेव हो जाएंगे।"
                                    } else {
                                        "Tap the ❤️ Like button in Status Reels or Video menu to save videos here."
                                    },
                                    color = HybridTextSecondary,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(favoriteVideos, key = { "fav_${it.id}" }) { video ->
                                    VideoDetailedRowItem(
                                        video = video,
                                        isHindi = isHindi,
                                        isSelected = video.id in selectedIds,
                                        isMultiSelectMode = isMultiSelectMode,
                                        showBadges = hdrBadges,
                                        progressFraction = ((historyProgressMap[video.id] ?: 0L).toFloat() / video.duration.coerceAtLeast(1L)).coerceIn(0f, 1f),
                                        personaMode = personaMode,
                                        onClick = {
                                            if (isMultiSelectMode) {
                                                if (video.id in selectedIds) selectedIds.remove(video.id) else selectedIds.add(video.id)
                                            } else {
                                                onPlayVideo(video)
                                            }
                                        },
                                        onLongClick = {
                                            if (video.id in selectedIds) selectedIds.remove(video.id) else selectedIds.add(video.id)
                                        },
                                        onMoreClick = { actionTargetVideo = video }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- BOTTOM MULTI-SELECT ACTION DOCK (1. Hybrid Share | 2. Delete | 3. Details | 4. 3-Dot More) ---
            if (isMultiSelectMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HybridCardBg)
                        .border(1.dp, HybridEmerald.copy(alpha = 0.35f), RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. HYBRID SHARE
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val selectedItems = videos.filter { it.id in selectedIds }
                                if (selectedItems.size == 1) {
                                    selectedIds.clear()
                                    onHybridShareItem(selectedItems.first())
                                } else {
                                    selectedIds.clear()
                                    onOpenTurboShare()
                                }
                            }
                            .padding(vertical = 6.dp)
                            .testTag("multiselect_hybrid_share_btn")
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = "Hybrid Share", tint = HybridEmerald, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isHindi) "हाइब्रिड शेयर" else "Hybrid Share",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }

                    // 2. DELETE
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                pendingDeleteItems = videos.filter { it.id in selectedIds }
                            }
                            .padding(vertical = 6.dp)
                            .testTag("multiselect_delete_btn")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = HybridCoralRed, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isHindi) "हटाएं (Delete)" else "Delete",
                            color = HybridCoralRed,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }

                    // 3. DETAILS
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showMultiSelectDetailsDialog = true
                            }
                            .padding(vertical = 6.dp)
                            .testTag("multiselect_details_btn")
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Details", tint = HybridCyan, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isHindi) "जानकारी (Details)" else "Details",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }

                    // 4. 3-DOT (MORE OPTIONS)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showMultiSelectMoreDialog = true
                            }
                            .padding(vertical = 6.dp)
                            .testTag("multiselect_more_3dot_btn")
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = Color(0xFFFFD54F), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isHindi) "और विकल्प (⋮)" else "More (⋮)",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // --- FULL-SCREEN VERTICAL SWIPE STATUS REELS OVERLAY (When opened from Folder -> Status) ---
        if (showStatusReelsOverlay && activeSubTab != "Status") {
            StatusReelsVerticalPagerView(
                reelVideos = statusReelVideos,
                favorites = favorites,
                initialIndex = initialStatusReelIndex,
                isHindi = isHindi,
                onToggleLike = { vid ->
                    val alreadyFav = favorites.any { it.id == vid.id }
                    viewModel.toggleFavorite(vid)
                    val msg = if (!alreadyFav) {
                        if (isHindi) "❤️ पसंदीदा में सेव हो गया!" else "❤️ Saved to Favourite!"
                    } else {
                        if (isHindi) "पसंदीदा से हटाया गया" else "Removed from Favourite"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                },
                onShareVideo = { vid ->
                    WifiDirectTransferEngine.shareRealFilesViaSystem(context, listOf(vid))
                },
                onOpenInFullPlayer = { vid ->
                    showStatusReelsOverlay = false
                    onPlayVideo(vid)
                },
                onOpenMoreOptions = { vid ->
                    actionTargetVideo = vid
                },
                onClose = {
                    showStatusReelsOverlay = false
                }
            )
        }
    }

    // --- Sort Dialog (English & Hindi) ---
    if (showSortDialog) {
        var tempCriteria by remember { mutableStateOf(sortCriteria) }
        var tempNewToOld by remember { mutableStateOf(sortNewToOld) }

        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            containerColor = HybridCardBg,
            title = {
                Text(
                    text = if (isHindi) "क्रमबद्ध करें (Sort by)" else "Sort by",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                )
            },
            text = {
                Column {
                    val sortOptions = listOf(
                        "Date" to if (isHindi) "तारीख (Date)" else "Date",
                        "Name" to if (isHindi) "नाम (Name)" else "Name",
                        "Size" to if (isHindi) "साइज़ (Size)" else "Size",
                        "Length" to if (isHindi) "लंबाई (Length)" else "Length"
                    )
                    sortOptions.forEach { (optionKey, optionLabel) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { tempCriteria = optionKey }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = tempCriteria == optionKey,
                                onClick = { tempCriteria = optionKey },
                                colors = RadioButtonDefaults.colors(selectedColor = HybridEmerald)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(optionLabel, color = Color.White, fontSize = 15.sp)
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = HybridCardBorder
                    )

                    listOf(
                        true to if (isHindi) "नए से पुराना (New to Old)" else "From new to old",
                        false to if (isHindi) "पुराने से नया (Old to New)" else "From old to new"
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
                    Text(if (isHindi) "ठीक है" else "OK", color = HybridEmerald, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSortDialog = false }) {
                    Text(if (isHindi) "रद्द करें" else "Cancel", color = HybridTextSecondary)
                }
            }
        )
    }

    // --- Safe Delete Dialog ---
    if (pendingDeleteItems.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingDeleteItems = emptyList() },
            containerColor = HybridCardBg,
            title = {
                Text(
                    text = if (isHindi) {
                        "${pendingDeleteItems.size} वीडियो हटाएं"
                    } else {
                        "Delete (${pendingDeleteItems.size} file${if (pendingDeleteItems.size > 1) "s" else ""})"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            viewModel.moveBatchToRecycleBin(pendingDeleteItems)
                            pendingDeleteItems = emptyList()
                            selectedIds.clear()
                            Toast.makeText(
                                context,
                                if (isHindi) "रीसायकल बिन में भेजा गया" else "Moved to Recycle Bin",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HybridEmerald),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isHindi) "रीसायकल बिन में डालें" else "MOVE TO RECYCLE BIN",
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val batchToDelete = pendingDeleteItems.toList()
                            val sysPending = PermanentDeleteHelper.createSystemPermanentDeleteRequest(context, batchToDelete)
                            viewModel.deleteBatchPermanently(batchToDelete)
                            pendingDeleteItems = emptyList()
                            selectedIds.clear()
                            if (sysPending != null) {
                                try {
                                    systemBatchDeleteLauncher.launch(
                                        androidx.activity.result.IntentSenderRequest.Builder(sysPending.intentSender).build()
                                    )
                                } catch (_: Exception) {
                                }
                            }
                            Toast.makeText(
                                context,
                                if (isHindi) "${batchToDelete.size} वीडियो हमेशा के लिए हटा दिए गए" else "Deleted ${batchToDelete.size} Video(s) Permanently",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isHindi) "हमेशा के लिए हटाएं (Permanent Delete)" else "Delete Permanently",
                            color = HybridCoralRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pendingDeleteItems = emptyList() }) {
                    Text(if (isHindi) "रद्द करें" else "Cancel", color = HybridTextSecondary)
                }
            }
        )
    }

    // --- 3-DOT FILE MANAGEMENT DIALOG ---
    if (actionTargetVideo != null) {
        val target = actionTargetVideo!!
        val isFav = favorites.any { it.id == target.id }
        MediaSevenActionDialog(
            item = target,
            isFavorite = isFav,
            isHindi = isHindi,
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
                Toast.makeText(
                    context,
                    if (isHindi) "🔒 प्राइवेट फ़ोल्डर में सुरक्षित किया गया" else "🔒 Moved to Private Folder",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onPlayBackgroundAudio = { vid ->
                viewModel.playMusic(vid)
                Toast.makeText(
                    context,
                    if (isHindi) "🎧 बैकग्राउंड ऑडियो चालू" else "🎧 Playing in Background Audio",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onPermanentDeleteConfirmed = { vid ->
                viewModel.deletePermanently(vid)
            },
            onMoveToRecycleBin = { vid ->
                viewModel.moveToRecycleBin(vid)
            },
            onRenameCompleted = {
                viewModel.loadMedia()
            }
        )
    }

    // --- THEME & GALLERY PHOTO WALLPAPER STUDIO DIALOG ---
    if (showThemeStudioDialog) {
        ThemeStudio60Dialog(
            activeTheme = activeTheme,
            onSelectTheme = { themeName ->
                viewModel.selectTheme(themeName)
            },
            onPickGalleryWallpaper = {
                galleryWallpaperLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onClearGalleryWallpaper = {
                viewModel.setCustomWallpaper(null)
                viewModel.selectTheme("Hybrid Emerald")
                Toast.makeText(
                    context,
                    if (isHindi) "डिफ़ॉल्ट थीम (Hybrid Emerald) लागू की गई" else "Default Theme (Hybrid Emerald) Restored",
                    Toast.LENGTH_SHORT
                ).show()
            },
            hasCustomWallpaper = customWallpaperUri != null,
            onDismiss = { showThemeStudioDialog = false }
        )
    }

    // --- MULTI-SELECT DETAILS DIALOG (Option 3 in Bottom Action Bar) ---
    if (showMultiSelectDetailsDialog) {
        val selectedItems = remember(selectedIds.toList(), videos) {
            videos.filter { it.id in selectedIds }
        }
        val totalBytes = selectedItems.sumOf { it.size }
        val totalDurationMs = selectedItems.sumOf { it.duration }
        val totalSizeMb = String.format(Locale.US, "%.2f MB", totalBytes / (1024.0 * 1024.0))
        val totalMins = (totalDurationMs / 1000L) / 60L
        val totalSecs = (totalDurationMs / 1000L) % 60L

        AlertDialog(
            onDismissRequest = { showMultiSelectDetailsDialog = false },
            containerColor = Color(0xFF101826),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = HybridCyan)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (selectedItems.size == 1) {
                            if (isHindi) "वीडियो की पूरी जानकारी" else "Video Details"
                        } else {
                            if (isHindi) "${selectedItems.size} चयनित वीडियो की जानकारी" else "${selectedItems.size} Selected Videos Details"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                if (selectedItems.size == 1) {
                    val v = selectedItems.first()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${if (isHindi) "नाम" else "Title"}: ${v.cleanTitle}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("${if (isHindi) "साइज़" else "Size"}: ${v.formattedSize} (${v.size} bytes)", color = HybridEmerald, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("${if (isHindi) "लंबाई" else "Duration"}: ${v.formattedDuration}", color = Color.White, fontSize = 13.sp)
                        Text("${if (isHindi) "क्वालिटी / फॉर्मेट" else "Resolution & Format"}: ${v.resolution} • ${v.format}", color = HybridCyan, fontSize = 13.sp)
                        Text("${if (isHindi) "फ़ोल्डर" else "Folder"}: ${v.folderName}", color = HybridTextSecondary, fontSize = 13.sp)
                        Text("${if (isHindi) "लोकेशन (Path)" else "File Path"}:\n${v.path}", color = HybridTextSecondary, fontSize = 11.sp)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${if (isHindi) "कुल वीडियो" else "Total Selected"}: ${selectedItems.size} Videos",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${if (isHindi) "कुल साइज़" else "Total Size"}: $totalSizeMb",
                            color = HybridEmerald,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${if (isHindi) "कुल समय" else "Total Duration"}: ${totalMins}m ${totalSecs}s",
                            color = HybridCyan,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "${if (isHindi) "फॉर्मेट" else "Formats"}: ${selectedItems.map { it.format }.distinct().joinToString(", ")}",
                            color = HybridTextSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "${if (isHindi) "फ़ोल्डर" else "Folders"}: ${selectedItems.map { it.folderName }.distinct().joinToString(", ")}",
                            color = HybridTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMultiSelectDetailsDialog = false }) {
                    Text(if (isHindi) "ठीक है" else "OK", color = HybridEmerald, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // --- MULTI-SELECT 3-DOT MORE OPTIONS DIALOG (Option 4 in Bottom Action Bar) ---
    if (showMultiSelectMoreDialog) {
        val selectedItems = remember(selectedIds.toList(), videos) {
            videos.filter { it.id in selectedIds }
        }
        AlertDialog(
            onDismissRequest = { showMultiSelectMoreDialog = false },
            containerColor = Color(0xFF101826),
            title = {
                Column {
                    Text(
                        text = if (isHindi) "${selectedItems.size} वीडियो के विकल्प (More Actions)" else "${selectedItems.size} Selected • More Options",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = if (isHindi) "चयनित वीडियो पर एक साथ एक्शन लें" else "Batch actions for selected videos",
                        color = HybridEmerald,
                        fontSize = 12.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 1. Add to / Toggle Favourite
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF192436))
                            .clickable {
                                selectedItems.forEach { vid -> viewModel.toggleFavorite(vid) }
                                showMultiSelectMoreDialog = false
                                selectedIds.clear()
                                Toast.makeText(
                                    context,
                                    if (isHindi) "❤️ पसंदीदा (Favourite) अपडेट किया गया" else "❤️ Updated Favourites for ${selectedItems.size} video(s)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFFF1744))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isHindi) "पसंदीदा में जोड़ें (Add to Favourite)" else "Add to / Toggle Favourite",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }

                    // 2. Play in Background Audio
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF192436))
                            .clickable {
                                val firstVid = selectedItems.firstOrNull()
                                showMultiSelectMoreDialog = false
                                selectedIds.clear()
                                if (firstVid != null) {
                                    viewModel.playMusic(firstVid)
                                    Toast.makeText(
                                        context,
                                        if (isHindi) "🎧 बैकग्राउंड ऑडियो चालू: ${firstVid.cleanTitle}" else "🎧 Playing in Background Audio: ${firstVid.cleanTitle}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Headphones, contentDescription = null, tint = HybridCyan)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isHindi) "बैकग्राउंड ऑडियो में सुनें (BG Audio)" else "Play in Background Audio",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }

                    // 3. Lock in Private Folder (Safe Vault)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF192436))
                            .clickable {
                                selectedItems.forEach { vid -> viewModel.toggleVaultLock(vid) }
                                showMultiSelectMoreDialog = false
                                selectedIds.clear()
                                Toast.makeText(
                                    context,
                                    if (isHindi) "🔒 ${selectedItems.size} वीडियो प्राइवेट फ़ोल्डर में सुरक्षित किए गए" else "🔒 Locked ${selectedItems.size} video(s) in Private Vault",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFB388FF))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isHindi) "प्राइवेट फ़ोल्डर में लॉक करें (Private Vault)" else "Lock in Private Folder (Vault)",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }

                    // 4. Add to Playlist
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF192436))
                            .clickable {
                                showMultiSelectMoreDialog = false
                                showBatchPlaylistPickerDialog = true
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = HybridEmerald)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isHindi) "प्लेलिस्ट में जोड़ें (Add to Playlist)" else "Add to Playlist",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }

                    // 5. System Share (WhatsApp / Bluetooth / Quick Share)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF192436))
                            .clickable {
                                showMultiSelectMoreDialog = false
                                if (selectedItems.isNotEmpty()) {
                                    WifiDirectTransferEngine.shareRealFilesViaSystem(context, selectedItems)
                                }
                                selectedIds.clear()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF40C4FF))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isHindi) "अन्य ऐप्स पर शेयर करें (System Share)" else "Share via Apps (WhatsApp / Quick Share)",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }

                    // 6. Rename File (when 1 video selected) or Open Full Single Sheet
                    if (selectedItems.size == 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF192436))
                                .clickable {
                                    val single = selectedItems.first()
                                    showMultiSelectMoreDialog = false
                                    selectedIds.clear()
                                    actionTargetVideo = single
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, tint = Color(0xFFFFD54F))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (isHindi) "फ़ाइल का नाम बदलें (Rename File)" else "Rename File & Full Options",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // 7. Move to Recycle Bin
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF192436))
                            .clickable {
                                viewModel.moveBatchToRecycleBin(selectedItems)
                                showMultiSelectMoreDialog = false
                                selectedIds.clear()
                                Toast.makeText(
                                    context,
                                    if (isHindi) "♻️ रीसायकल बिन में भेजा गया" else "♻️ Moved ${selectedItems.size} video(s) to Recycle Bin",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.RestoreFromTrash, contentDescription = null, tint = HybridCoralRed)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isHindi) "रीसायकल बिन में डालें (Recycle Bin)" else "Move to Recycle Bin",
                            color = HybridCoralRed,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMultiSelectMoreDialog = false }) {
                    Text(if (isHindi) "बंद करें" else "Close", color = HybridEmerald, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // --- BATCH ADD TO PLAYLIST DIALOG ---
    if (showBatchPlaylistPickerDialog) {
        val selectedItems = remember(selectedIds.toList(), videos) {
            videos.filter { it.id in selectedIds }
        }
        var newPlaylistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBatchPlaylistPickerDialog = false },
            containerColor = Color(0xFF101826),
            title = {
                Text(
                    text = if (isHindi) "प्लेलिस्ट में जोड़ें (${selectedItems.size})" else "Add ${selectedItems.size} Video(s) to Playlist",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        placeholder = {
                            Text(if (isHindi) "नई प्लेलिस्ट का नाम..." else "New Playlist Name...", color = Color.Gray)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (newPlaylistName.isNotBlank()) {
                        Button(
                            onClick = {
                                viewModel.addPlaylist(newPlaylistName.trim())
                                newPlaylistName = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = HybridEmerald),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isHindi) "+ नई प्लेलिस्ट बनाएं" else "+ Create Playlist", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                    playlists.forEach { pl ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF192436))
                                .clickable {
                                    selectedItems.forEach { vid -> viewModel.addToPlaylist(pl.id, vid.id) }
                                    showBatchPlaylistPickerDialog = false
                                    selectedIds.clear()
                                    Toast.makeText(
                                        context,
                                        if (isHindi) "'${pl.name}' में जोड़ा गया" else "Added to '${pl.name}'",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = HybridEmerald)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(pl.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBatchPlaylistPickerDialog = false }) {
                    Text(if (isHindi) "बंद करें" else "Close", color = HybridEmerald)
                }
            }
        )
    }
}

/**
 * Full-Screen Vertical Swipe Status / Reels Player (`<= 100s` videos) + Status Grid Wall Switcher.
 * Supports 8K / 4K / 2K / 1080p `.webm`, `.mkv`, and `.mp4` videos on all phones (including budget devices like Samsung M13)
 * via downscaled hardware surface buffers + automatic software frame fallback.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StatusReelsVerticalPagerView(
    reelVideos: List<MediaItem>,
    favorites: List<MediaItem>,
    initialIndex: Int,
    isHindi: Boolean,
    onToggleLike: (MediaItem) -> Unit,
    onShareVideo: (MediaItem) -> Unit,
    onOpenInFullPlayer: (MediaItem) -> Unit,
    onOpenMoreOptions: (MediaItem) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showReelThreeDotSheetFor by remember { mutableStateOf<MediaItem?>(null) }
    var showReelDetailsDialogFor by remember { mutableStateOf<MediaItem?>(null) }
    var isStatusGridWallMode by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var statusPlaybackSpeed by remember { mutableFloatStateOf(1.0f) }

    if (reelVideos.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.SlowMotionVideo,
                    contentDescription = null,
                    tint = Color(0xFFFF2A6D),
                    modifier = Modifier.size(54.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (isHindi) "कोई स्टेटस वीडियो उपलब्ध नहीं है" else "No Status Videos Found",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(onClick = onClose) {
                    Text(if (isHindi) "वापस जाएं" else "Go Back", color = Color.White)
                }
            }
        }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (reelVideos.size - 1).coerceAtLeast(0)),
        pageCount = { reelVideos.size }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isStatusGridWallMode) {
            // --- STATUS GRID WALL VIEW ---
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(top = 64.dp, start = 12.dp, end = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(reelVideos, key = { _, it -> "status_grid_${it.id}" }) { idx, video ->
                    val isLiked = favorites.any { it.id == video.id }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(HybridCardBg)
                            .border(1.dp, Color(0xFFFF2A6D).copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                            .clickable {
                                isStatusGridWallMode = false
                                coroutineScope.launch {
                                    pagerState.scrollToPage(idx)
                                }
                            }
                    ) {
                        GuaranteedVideoThumbnail(video = video, modifier = Modifier.fillMaxSize())
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color(0xCC000000))
                                    )
                                )
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xCC000000))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "▶ ${video.formattedDuration}",
                                color = HybridEmerald,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        IconButton(
                            onClick = { onToggleLike(video) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (isLiked) Color(0xFFFF1744) else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = video.cleanTitle,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = video.formattedSize,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        } else {
            // --- FULL-SCREEN VERTICAL REELS PAGER WITH UNIVERSAL 8K/4K/2K HARDWARE + SOFTWARE ENGINE ---
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val video = reelVideos[page]
                val isCurrentPage = pagerState.currentPage == page
                val isLiked = favorites.any { it.id == video.id }
                var isPausedByTap by remember(video.id, isCurrentPage) { mutableStateOf(false) }
                var reelCurrentPosMs by remember(video.id, isCurrentPage) { mutableLongStateOf(0L) }
                var reelTotalDurMs by remember(video.id, isCurrentPage) { mutableLongStateOf(video.duration.coerceAtLeast(1000L)) }
                var isSoftwareReelFallback by remember(video.id, isCurrentPage) { mutableStateOf(false) }
                var softwareReelBitmap by remember(video.id, isCurrentPage) { mutableStateOf<Bitmap?>(null) }
                var activeReelMediaPlayer by remember(video.id, isCurrentPage) { mutableStateOf<android.media.MediaPlayer?>(null) }

                // Apply Mute & Speed changes live to active hardware MediaPlayer
                LaunchedEffect(isMuted, statusPlaybackSpeed, activeReelMediaPlayer) {
                    activeReelMediaPlayer?.let { mp ->
                        try {
                            val vol = if (isMuted) 0f else 1f
                            mp.setVolume(vol, vol)
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && mp.isPlaying) {
                                mp.playbackParams = mp.playbackParams.setSpeed(statusPlaybackSpeed)
                            }
                        } catch (_: Exception) {
                        }
                    }
                }

                // Live progress tracker for Status Reel seekbar
                LaunchedEffect(isCurrentPage, isPausedByTap, isSoftwareReelFallback, activeReelMediaPlayer) {
                    if (isCurrentPage && !isSoftwareReelFallback) {
                        while (true) {
                            try {
                                val mp = activeReelMediaPlayer
                                if (mp != null && !isPausedByTap) {
                                    val pos = mp.currentPosition.toLong()
                                    val dur = mp.duration.toLong().coerceAtLeast(1000L)
                                    reelCurrentPosMs = pos.coerceIn(0L, dur)
                                    reelTotalDurMs = dur
                                }
                            } catch (_: Exception) {
                            }
                            kotlinx.coroutines.delay(250L)
                        }
                    }
                }

                // Universal Software Frame-Stream Fallback for 8K/4K/2K Status Reels on budget phones (e.g. Samsung M13)
                LaunchedEffect(isCurrentPage, isSoftwareReelFallback, video.path) {
                    if (isCurrentPage && isSoftwareReelFallback) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val retriever = android.media.MediaMetadataRetriever()
                            try {
                                if (video.path.startsWith("content://")) {
                                    retriever.setDataSource(context, Uri.parse(video.path))
                                } else {
                                    retriever.setDataSource(video.path)
                                }
                                val dur = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                                    ?.toLongOrNull()?.coerceAtLeast(1000L) ?: video.duration.coerceAtLeast(5000L)
                                reelTotalDurMs = dur
                                val vw = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 720
                                val vh = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1280
                                val scaleRatio = minOf(720f / vw.coerceAtLeast(1), 720f / vh.coerceAtLeast(1), 1f)
                                val targetW = (vw * scaleRatio).toInt().coerceAtLeast(240)
                                val targetH = (vh * scaleRatio).toInt().coerceAtLeast(240)

                                var lastMs = System.currentTimeMillis()
                                while (isCurrentPage && isSoftwareReelFallback) {
                                    if (!isPausedByTap) {
                                        val now = System.currentTimeMillis()
                                        val delta = ((now - lastMs) * statusPlaybackSpeed).toLong().coerceIn(30L, 250L)
                                        lastMs = now
                                        val nextPos = (reelCurrentPosMs + delta) % reelTotalDurMs
                                        reelCurrentPosMs = nextPos
                                        val frame = try {
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                                                retriever.getScaledFrameAtTime(
                                                    nextPos * 1000L,
                                                    android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                                    targetW,
                                                    targetH
                                                ) ?: retriever.getFrameAtTime(nextPos * 1000L)
                                            } else {
                                                retriever.getFrameAtTime(nextPos * 1000L)
                                            }
                                        } catch (_: Exception) {
                                            null
                                        }
                                        if (frame != null) {
                                            softwareReelBitmap = frame
                                        }
                                        kotlinx.coroutines.delay(70L)
                                    } else {
                                        lastMs = System.currentTimeMillis()
                                        kotlinx.coroutines.delay(150L)
                                    }
                                }
                            } catch (_: Exception) {
                            } finally {
                                try {
                                    retriever.release()
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable { isPausedByTap = !isPausedByTap }
                ) {
                    if (isCurrentPage) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    fun capReelSurfaceBuffer(vw: Int, vh: Int) {
                                        try {
                                            val maxDim = 1280f
                                            if (vw > 1280 || vh > 1280) {
                                                val ratio = minOf(maxDim / vw.coerceAtLeast(1), maxDim / vh.coerceAtLeast(1))
                                                val safeW = ((vw * ratio).toInt() / 2) * 2
                                                val safeH = ((vh * ratio).toInt() / 2) * 2
                                                if (safeW >= 240 && safeH >= 240) {
                                                    holder.setFixedSize(safeW, safeH)
                                                }
                                            }
                                        } catch (_: Exception) {
                                        }
                                    }

                                    fun configureReelMp(mp: android.media.MediaPlayer) {
                                        isSoftwareReelFallback = false
                                        activeReelMediaPlayer = mp
                                        try {
                                            mp.setVideoScalingMode(android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                                        } catch (_: Exception) {
                                        }
                                        capReelSurfaceBuffer(mp.videoWidth, mp.videoHeight)
                                        mp.isLooping = true
                                        val vol = if (isMuted) 0f else 1f
                                        mp.setVolume(vol, vol)
                                        reelTotalDurMs = mp.duration.toLong().coerceAtLeast(1000L)
                                        if (!isPausedByTap) {
                                            mp.start()
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && statusPlaybackSpeed != 1.0f) {
                                                try {
                                                    mp.playbackParams = mp.playbackParams.setSpeed(statusPlaybackSpeed)
                                                } catch (_: Exception) {
                                                }
                                            }
                                        }
                                    }

                                    fun startReelFdFallback(): Boolean {
                                        return try {
                                            val rawMp = android.media.MediaPlayer()
                                            val pfd = if (video.path.startsWith("content://")) {
                                                ctx.contentResolver.openFileDescriptor(Uri.parse(video.path), "r")
                                            } else {
                                                val f = File(video.path)
                                                if (f.exists()) {
                                                    android.os.ParcelFileDescriptor.open(f, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                                                } else null
                                            }
                                            if (pfd != null) {
                                                rawMp.setDataSource(pfd.fileDescriptor)
                                                pfd.close()
                                                capReelSurfaceBuffer(1280, 720)
                                                rawMp.setDisplay(holder)
                                                rawMp.setOnPreparedListener { mp -> configureReelMp(mp) }
                                                rawMp.setOnErrorListener { _, _, _ ->
                                                    isSoftwareReelFallback = true
                                                    true
                                                }
                                                rawMp.prepareAsync()
                                                true
                                            } else {
                                                isSoftwareReelFallback = true
                                                false
                                            }
                                        } catch (_: Exception) {
                                            isSoftwareReelFallback = true
                                            false
                                        }
                                    }

                                    try {
                                        if (
                                            video.path.startsWith("content://") ||
                                            video.path.startsWith("http://") ||
                                            video.path.startsWith("https://") ||
                                            video.path.startsWith("android.resource://")
                                        ) {
                                            setVideoURI(Uri.parse(video.path))
                                        } else {
                                            val file = File(video.path)
                                            if (file.exists()) setVideoPath(file.absolutePath) else setVideoURI(Uri.parse(video.path))
                                        }
                                        setOnPreparedListener { mp -> configureReelMp(mp) }
                                        setOnErrorListener { _, _, _ ->
                                            if (!startReelFdFallback()) {
                                                isSoftwareReelFallback = true
                                            }
                                            true
                                        }
                                    } catch (_: Exception) {
                                        if (!startReelFdFallback()) {
                                            isSoftwareReelFallback = true
                                        }
                                    }
                                }
                            },
                            update = { view ->
                                if (!isSoftwareReelFallback) {
                                    try {
                                        if (isPausedByTap && view.isPlaying) {
                                            view.pause()
                                        } else if (!isPausedByTap && !view.isPlaying) {
                                            view.start()
                                        }
                                    } catch (_: Exception) {
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .align(Alignment.Center)
                        )

                        if (isSoftwareReelFallback) {
                            if (softwareReelBitmap != null) {
                                Image(
                                    bitmap = softwareReelBitmap!!.asImageBitmap(),
                                    contentDescription = video.cleanTitle,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                GuaranteedVideoThumbnail(
                                    video = video,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    } else {
                        GuaranteedVideoThumbnail(
                            video = video,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Bottom gradient scrim for readable title
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0xE6000000))
                                )
                            )
                    )

                    // Center Play Icon when paused by tap
                    if (isPausedByTap) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .align(Alignment.Center)
                                .clip(CircleShape)
                                .background(Color(0x88000000))
                                .border(1.5.dp, HybridEmerald, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = HybridEmerald,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }

                    // --- RIGHT SIDE REELS CONTROLS (Like ❤️, Mute 🔊, Speed ⚡, Share 📤, Full Player 🎬, 3-Dot ⋮) ---
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 14.dp, bottom = 42.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. LIKE / HEART
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onToggleLike(video) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isLiked) Color(0xFFFF1744).copy(alpha = 0.28f)
                                        else Color(0x88000000)
                                    )
                                    .border(
                                        1.2.dp,
                                        if (isLiked) Color(0xFFFF1744) else Color.White.copy(alpha = 0.35f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = if (isHindi) "पसंदीदा" else "Like",
                                    tint = if (isLiked) Color(0xFFFF1744) else Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (isLiked) (if (isHindi) "सेव्ड" else "Saved") else (if (isHindi) "लाइक" else "Like"),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 2. MUTE / UNMUTE
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { isMuted = !isMuted }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x88000000))
                                    .border(1.dp, if (isMuted) HybridCoralRed else Color.White.copy(alpha = 0.35f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = "Mute",
                                    tint = if (isMuted) HybridCoralRed else Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (isMuted) (if (isHindi) "म्यूट" else "Muted") else (if (isHindi) "आवाज़" else "Sound"),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 3. SPEED PILL (1x -> 1.5x -> 2x)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                statusPlaybackSpeed = when (statusPlaybackSpeed) {
                                    1.0f -> 1.5f
                                    1.5f -> 2.0f
                                    else -> 1.0f
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x88000000))
                                    .border(1.dp, HybridEmerald.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${statusPlaybackSpeed}x",
                                    color = HybridEmerald,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (isHindi) "स्पीड" else "Speed",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 4. SHARE
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onShareVideo(video) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x88000000))
                                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = if (isHindi) "शेयर करें" else "Share",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (isHindi) "शेयर" else "Share",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 5. 3-DOT MORE
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { showReelThreeDotSheetFor = video }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x88000000))
                                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = if (isHindi) "विकल्प" else "More Options",
                                    tint = Color.White,
                                    modifier = Modifier.size(23.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (isHindi) "विकल्प" else "More",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Bottom-Left Video Title, Auto-HD Badge & Duration Info
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, end = 84.dp, bottom = 24.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFFF2A6D))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isHindi) "स्टेटस • ${video.formattedDuration}" else "Status • ${video.formattedDuration}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(HybridEmerald.copy(alpha = 0.22f))
                                    .border(1.dp, HybridEmerald.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isSoftwareReelFallback) "Smart HD Fit" else "${video.resolution} Auto-Fit",
                                    color = HybridEmerald,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Text(
                                text = "${page + 1} / ${reelVideos.size}",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = video.cleanTitle,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${video.folderName} • ${video.formattedSize}",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.sp
                        )
                    }

                    // Bottom Live Progress Bar
                    val progressRatio = (reelCurrentPosMs.toFloat() / reelTotalDurMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter)
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressRatio)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(listOf(Color(0xFFFF2A6D), HybridEmerald))
                                )
                        )
                    }
                }
            }
        }

        // Top Bar over Status Reels / Grid: Back Button + Mode Switcher (Reels Swipe vs Grid Wall)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 12.dp, start = 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xAA000000))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                    .clickable { onClose() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = if (isHindi) "वापस" else "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isHindi) "स्टेटस (${reelVideos.size})" else "Status (${reelVideos.size})",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            // Switcher between Vertical Reels Swipe and Status Grid Wall
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xAA000000))
                    .border(1.dp, HybridEmerald.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .clickable { isStatusGridWallMode = !isStatusGridWallMode }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isStatusGridWallMode) Icons.Default.SlowMotionVideo else Icons.Default.GridView,
                    contentDescription = null,
                    tint = HybridEmerald,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isStatusGridWallMode) {
                        if (isHindi) "रील्स स्वाइप" else "Reels View"
                    } else {
                        if (isHindi) "ग्रिड व्यू" else "Grid Wall"
                    },
                    color = HybridEmerald,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp
                )
            }
        }
    }

    // 3-Dot Clean 4-Options Dialog for Status Reels
    if (showReelThreeDotSheetFor != null) {
        val target = showReelThreeDotSheetFor!!
        val isLiked = favorites.any { it.id == target.id }
        AlertDialog(
            onDismissRequest = { showReelThreeDotSheetFor = null },
            containerColor = Color(0xFF101826),
            title = {
                Text(
                    text = target.cleanTitle,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Option 1: Open in Full Player
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF192436))
                            .clickable {
                                showReelThreeDotSheetFor = null
                                onOpenInFullPlayer(target)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.SmartDisplay, contentDescription = null, tint = HybridEmerald)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isHindi) "पूरे वीडियो प्लेयर में चलाएं" else "Play in Full Video Player",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }

                    // Option 2: Save / Remove Favourite
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF192436))
                            .clickable {
                                showReelThreeDotSheetFor = null
                                onToggleLike(target)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = Color(0xFFFF1744)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isLiked) {
                                if (isHindi) "पसंदीदा से हटाएं" else "Remove from Favourite"
                            } else {
                                if (isHindi) "पसंदीदा में सेव करें" else "Save to Favourite"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }

                    // Option 3: File Details
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF192436))
                            .clickable {
                                showReelThreeDotSheetFor = null
                                showReelDetailsDialogFor = target
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = HybridCyan)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isHindi) "फ़ाइल की जानकारी (Details)" else "Video File Details",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }

                    // Option 4: More File Actions (Rename / Vault / Delete)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF192436))
                            .clickable {
                                val v = target
                                showReelThreeDotSheetFor = null
                                onOpenMoreOptions(v)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, tint = Color(0xFFFFB300))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isHindi) "नाम बदलें / हटाएं (More)" else "Rename / Delete / Vault",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReelThreeDotSheetFor = null }) {
                    Text(if (isHindi) "बंद करें" else "Close", color = HybridEmerald, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showReelDetailsDialogFor != null) {
        val v = showReelDetailsDialogFor!!
        AlertDialog(
            onDismissRequest = { showReelDetailsDialogFor = null },
            containerColor = Color(0xFF101826),
            title = {
                Text(
                    text = if (isHindi) "फ़ाइल की जानकारी" else "File Details",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${if (isHindi) "नाम" else "Name"}: ${v.cleanTitle}", color = Color.White, fontSize = 13.sp)
                    Text("${if (isHindi) "लंबाई" else "Duration"}: ${v.formattedDuration}", color = HybridTextSecondary, fontSize = 13.sp)
                    Text("${if (isHindi) "साइज़" else "Size"}: ${v.formattedSize}", color = HybridTextSecondary, fontSize = 13.sp)
                    Text("${if (isHindi) "फ़ोल्डर" else "Folder"}: ${v.folderName}", color = HybridTextSecondary, fontSize = 13.sp)
                    Text("${if (isHindi) "पाथ" else "Path"}: ${v.path}", color = HybridTextSecondary, fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showReelDetailsDialogFor = null }) {
                    Text("OK", color = HybridEmerald, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

/**
 * Special Folder Card used at the top of the Folder tab for:
 * 1. Status (Short Reels <= 100s)
 * 2. Favourite (Liked Videos)
 */
@Composable
private fun SpecialFolderCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    previewVideos: List<MediaItem>,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(accentColor.copy(alpha = 0.16f), HybridCardBg)
                )
            )
            .border(1.2.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = subtitle,
                        color = HybridTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = accentColor
            )
        }

        if (previewVideos.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                previewVideos.take(4).forEach { previewVideo ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(8.dp))
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
                repeat((4 - previewVideos.size).coerceAtLeast(0)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x11FFFFFF))
                    )
                }
            }
        }
    }
}

/**
 * 4-Stage Guaranteed Video Thumbnail Composable.
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
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF1A2639), Color(0xFF0F172A), Color(0xFF141E30))
                )
            )
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
    isHindi: Boolean,
    isSelected: Boolean,
    isMultiSelectMode: Boolean = false,
    showBadges: Boolean,
    progressFraction: Float,
    personaMode: DemographicMode = DemographicMode.YOUTH_PRO,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val cornerRad = if (personaMode == DemographicMode.KIDS_MAGIC) 20.dp else 14.dp
    val displayTitle = if (video.isPartialDownload) {
        if (isHindi) "पेंडिंग (Pending) • ${video.cleanTitle}" else "Pending • ${video.cleanTitle}"
    } else {
        video.cleanTitle
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRad))
            .background(if (isSelected) HybridEmerald.copy(alpha = 0.20f) else HybridCardBg)
            .border(
                width = if (isSelected) 1.8.dp else if (video.isPartialDownload) 1.4.dp else 1.dp,
                color = when {
                    isSelected -> HybridEmerald
                    video.isPartialDownload -> Color(0xFFFF9100)
                    else -> HybridCardBorder
                },
                shape = RoundedCornerShape(cornerRad)
            )
            // 2-Second Hold selects the video and enters Multi-Select Mode; 4-Second Hold opens 3-Dot Menu
            .timedMediaTouchHold(
                onClick = onClick,
                onHold2sPlay = onLongClick,
                onHold4sMenu = onMoreClick
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isMultiSelectMode || isSelected) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Selected" else "Unselected",
                tint = if (isSelected) HybridEmerald else HybridTextSecondary,
                modifier = Modifier
                    .size(22.dp)
                    .clickable { onLongClick() }
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Thumbnail with Pending / Resolution Badge (NO "8K" text!) and Duration Pill
        Box(
            modifier = Modifier
                .size(width = personaMode.thumbnailWidth, height = personaMode.thumbnailHeight)
                .clip(RoundedCornerShape(10.dp))
        ) {
            GuaranteedVideoThumbnail(
                video = video,
                modifier = Modifier.fillMaxSize()
            )

            if (video.isPartialDownload) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 5.dp, top = 5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFFF9100))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = if (isHindi) "पेंडिंग" else "Pending",
                        color = Color.Black,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            } else if (showBadges && !video.resolution.contains("8K", ignoreCase = true)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 6.dp, bottom = 7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC141E30))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = video.resolution,
                        color = HybridCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

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

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayTitle,
                color = if (video.isPartialDownload) Color(0xFFFFD180) else Color.White,
                fontSize = personaMode.titleFontSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = (personaMode.titleFontSize.value + 4f).sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            val cleanRes = if (video.resolution.contains("8K", ignoreCase = true)) "HD" else video.resolution
            Text(
                text = if (video.isPartialDownload) {
                    if (isHindi) "पेंडिंग डाउनलोड • ${video.formattedSize}" else "Pending Download • ${video.formattedSize}"
                } else {
                    "$cleanRes • ${video.format} • ${video.formattedSize}"
                },
                color = if (video.isPartialDownload) Color(0xFFFFAB40) else HybridTextSecondary,
                fontSize = personaMode.metaFontSize
            )
            Spacer(modifier = Modifier.height(5.dp))
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

        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.testTag("video_row_more_button")
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = if (isHindi) "विकल्प" else "Video Options",
                tint = HybridTextSecondary
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoGridCard(
    video: MediaItem,
    isHindi: Boolean,
    isSelected: Boolean,
    isMultiSelectMode: Boolean = false,
    showBadges: Boolean,
    progressFraction: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val displayTitle = if (video.isPartialDownload) {
        if (isHindi) "पेंडिंग • ${video.cleanTitle}" else "Pending • ${video.cleanTitle}"
    } else {
        video.cleanTitle
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) HybridEmerald.copy(alpha = 0.20f) else HybridCardBg)
            .border(
                if (isSelected) 1.8.dp else 1.dp,
                when {
                    isSelected -> HybridEmerald
                    video.isPartialDownload -> Color(0xFFFF9100)
                    else -> HybridCardBorder
                },
                RoundedCornerShape(14.dp)
            )
            // 2-Second Hold selects the video and enters Multi-Select Mode; 4-Second Hold opens 3-Dot Menu
            .timedMediaTouchHold(
                onClick = onClick,
                onHold2sPlay = onLongClick,
                onHold4sMenu = onMoreClick
            )
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .clip(RoundedCornerShape(10.dp))
        ) {
            GuaranteedVideoThumbnail(video = video, modifier = Modifier.fillMaxSize())

            if (isMultiSelectMode || isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xAA000000))
                        .clickable { onLongClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (isSelected) "Selected" else "Unselected",
                        tint = if (isSelected) HybridEmerald else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (video.isPartialDownload) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFFF9100))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = if (isHindi) "पेंडिंग" else "Pending",
                        color = Color.Black,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            } else if (showBadges && !video.resolution.contains("8K", ignoreCase = true)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC141E30))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = video.resolution,
                        color = HybridCyan,
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
                    text = displayTitle,
                    color = if (video.isPartialDownload) Color(0xFFFFD180) else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = video.formattedSize,
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
                    contentDescription = if (isHindi) "विकल्प" else "Video Options",
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
    isHindi: Boolean,
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
                        text = if (isHindi) "${folderVideos.size} वीडियो" else "${folderVideos.size} Videos",
                        color = HybridTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = if (isHindi) "फ़ोल्डर खोलें" else "Open Folder",
                tint = HybridTextSecondary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

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
