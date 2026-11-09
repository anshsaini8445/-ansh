package com.example.ui.screens

import android.app.Activity
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.MediaItem
import com.example.helpers.PermanentDeleteHelper
import com.example.helpers.ScenicBackdropPresets
import com.example.helpers.WifiDirectTransferEngine
import com.example.helpers.timedMediaTouchHold
import com.example.ui.components.GuaranteedAlbumArt
import com.example.ui.components.LiquidGlassBox
import com.example.ui.components.LiquidGlassIconOrb
import com.example.ui.menus.MediaSevenActionDialog
import com.example.ui.theme.*
import com.example.viewmodel.PlayerViewModel
import java.util.Locale

private enum class AudioSubTab {
    ALL, NEW, VIDEOS, PLAYLIST, FOLDERS
}

/**
 * Clean, Multi-Language Music & Video-Audio Library Screen:
 * - Prominent 1.5x Enlarged Top Header: App Icon (44.dp) + "Hybrid Player" (27.sp).
 * - Labeled Sub-Tabs: All | New | Videos | Playlist | Folders + Multi-Select (Checklist) + Sort (⇅).
 * - Multi-Select Batch Permanent Delete: Select 1 to 1000+ songs via long-press or checklist button
 *   and permanently delete them from device storage in a single batch.
 * - Floating Mini Now-Playing Dock sits cleanly at the bottom (bottom = 8.dp) right above the
 *   Bottom Navigation Bar without overlapping the middle of the song list.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudioLibraryScreen(
    viewModel: PlayerViewModel,
    onOpenStudioPlayer: () -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    onOpenTurboShare: () -> Unit = {},
    onHybridShareItem: (MediaItem) -> Unit = { _ -> onOpenTurboShare() },
    onOpenFullMusicPlayer: () -> Unit = onOpenStudioPlayer,
    onOpenHybridShareForItem: (MediaItem) -> Unit = onHybridShareItem
) {
    val context = LocalContext.current
    val musicItems by viewModel.musicItems.collectAsState()
    val videoItems by viewModel.videoItems.collectAsState()
    val favoriteItems by viewModel.favoriteItems.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val audioSortOrder by viewModel.audioSortOrder.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val activeTheme by viewModel.activeTheme.collectAsState()
    val scenicBackdropPreset by viewModel.scenicBackdropPreset.collectAsState()
    val customWallpaperUri by viewModel.customWallpaperUri.collectAsState()

    val isHindi = remember(selectedLanguage) {
        com.example.ui.i18n.WorldLanguageEngine.isHindiActive(selectedLanguage)
    }

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

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = configuration.screenWidthDp >= 600
    val gridColumns = if (isTablet || isLandscape) 2 else 1

    var activeTab by remember { mutableStateOf(AudioSubTab.ALL) }
    var selectedFolderName by remember { mutableStateOf<String?>(null) }
    var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }
    var viewingFavoritesPlaylist by remember { mutableStateOf(false) }

    var showSortDialog by remember { mutableStateOf(false) }
    var showThemeStudioDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var sevenMenuTargetItem by remember { mutableStateOf<MediaItem?>(null) }

    // Multi-select state for 2-second hold selection + bottom 4-action dock
    var multiSelectMode by remember { mutableStateOf(false) }
    val selectedTrackIds = remember { mutableStateListOf<String>() }
    var pendingBatchDeleteItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var showBatchDetailsDialog by remember { mutableStateOf(false) }
    var showBatchMoreDialog by remember { mutableStateOf(false) }
    var showBatchPlaylistPickerDialog by remember { mutableStateOf(false) }

    val batchDeleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.deleteBatchPermanently(pendingBatchDeleteItems)
            Toast.makeText(
                context,
                if (isHindi) "${pendingBatchDeleteItems.size} गाने हमेशा के लिए हटाए गए" else "Deleted ${pendingBatchDeleteItems.size} songs permanently",
                Toast.LENGTH_SHORT
            ).show()
            pendingBatchDeleteItems = emptyList()
            selectedTrackIds.clear()
            multiSelectMode = false
        }
    }

    fun executeBatchPermanentDelete(itemsToDelete: List<MediaItem>) {
        if (itemsToDelete.isEmpty()) return
        val unDeleted = mutableListOf<MediaItem>()
        itemsToDelete.forEach { item ->
            val deletedDirect = PermanentDeleteHelper.deletePermanently(context, item)
            if (!deletedDirect) {
                unDeleted.add(item)
            }
        }
        viewModel.deleteBatchPermanently(itemsToDelete)
        if (unDeleted.isNotEmpty()) {
            val intentSender = PermanentDeleteHelper.createSystemPermanentDeleteRequest(context, unDeleted)
            if (intentSender != null) {
                pendingBatchDeleteItems = unDeleted
                batchDeleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                return
            }
        }
        Toast.makeText(
            context,
            if (isHindi) "${itemsToDelete.size} गाने हमेशा के लिए हटाए गए" else "Deleted ${itemsToDelete.size} songs permanently",
            Toast.LENGTH_SHORT
        ).show()
        selectedTrackIds.clear()
        multiSelectMode = false
    }

    val videoSongsFolderKey = "__VIDEO_SONGS_FOLDER__"

    BackHandler(
        enabled = multiSelectMode || selectedFolderName != null || selectedPlaylistId != null || viewingFavoritesPlaylist || activeTab != AudioSubTab.ALL
    ) {
        when {
            multiSelectMode -> {
                multiSelectMode = false
                selectedTrackIds.clear()
            }
            selectedFolderName != null -> selectedFolderName = null
            selectedPlaylistId != null -> selectedPlaylistId = null
            viewingFavoritesPlaylist -> viewingFavoritesPlaylist = false
            activeTab != AudioSubTab.ALL -> activeTab = AudioSubTab.ALL
        }
    }

    fun sortTracks(list: List<MediaItem>): List<MediaItem> {
        return when (audioSortOrder) {
            "OldToNew" -> list.sortedBy { it.dateAdded }
            "AtoZ" -> list.sortedBy { it.cleanTitle.lowercase() }
            "ZtoA" -> list.sortedByDescending { it.cleanTitle.lowercase() }
            "ShortToLong" -> list.filter { it.duration >= 5_000L || it.duration == 0L }.sortedBy { it.duration }
            "LongToShort" -> list.sortedByDescending { it.duration }
            else -> list.sortedByDescending { it.dateAdded }
        }
    }

    val sortedMusicItems = remember(musicItems, audioSortOrder) {
        sortTracks(musicItems)
    }

    val newSongsItems = remember(musicItems) {
        musicItems.sortedByDescending { it.dateAdded }.take(40)
    }

    val sortedVideoAudioItems = remember(videoItems, audioSortOrder) {
        sortTracks(videoItems)
    }

    val favoriteAudioAndVideoTracks = remember(favoriteItems, audioSortOrder) {
        sortTracks(favoriteItems)
    }

    val groupedAudioFolders = remember(musicItems) {
        musicItems.groupBy { it.folderName.ifBlank { "Music" } }
            .entries
            .sortedByDescending { it.value.size }
    }

    val currentDisplayedTracks = remember(
        activeTab,
        sortedMusicItems,
        newSongsItems,
        sortedVideoAudioItems,
        selectedFolderName,
        viewingFavoritesPlaylist,
        favoriteAudioAndVideoTracks
    ) {
        when {
            selectedFolderName == videoSongsFolderKey -> sortedVideoAudioItems
            selectedFolderName != null -> sortTracks(
                musicItems.filter { it.folderName.equals(selectedFolderName, ignoreCase = true) }
            )
            viewingFavoritesPlaylist -> favoriteAudioAndVideoTracks
            activeTab == AudioSubTab.ALL -> sortedMusicItems
            activeTab == AudioSubTab.NEW -> newSongsItems
            activeTab == AudioSubTab.VIDEOS -> sortedVideoAudioItems
            else -> sortedMusicItems
        }
    }

    val isCompactSplit = configuration.screenHeightDp < 440

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .pointerInput(activeTab, selectedFolderName, selectedPlaylistId, viewingFavoritesPlaylist) {
                var totalHorizDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalHorizDrag = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        totalHorizDrag += dragAmount
                    },
                    onDragEnd = {
                        if (selectedFolderName == null && selectedPlaylistId == null && !viewingFavoritesPlaylist) {
                            val tabs = AudioSubTab.entries
                            val curIdx = tabs.indexOf(activeTab)
                            if (totalHorizDrag < -95f && curIdx < tabs.lastIndex) {
                                activeTab = tabs[curIdx + 1]
                            } else if (totalHorizDrag > 95f && curIdx > 0) {
                                activeTab = tabs[curIdx - 1]
                            }
                        }
                    }
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Prominent Top Header Bar — App Icon + "Hybrid Player" + Live Time & Battery + Gallery Photo & Scenic Theme Picker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isCompactSplit) 48.dp else 64.dp)
                    .padding(horizontal = if (isTablet) 22.dp else 14.dp),
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
                            .size(if (isCompactSplit) 34.dp else 42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                1.5.dp,
                                Brush.linearGradient(listOf(HybridEmerald, HybridCyan)),
                                RoundedCornerShape(12.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isHindi) "हाइब्रिड प्लेयर" else "Hybrid Player",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = if (isCompactSplit) 19.sp else 22.sp,
                            letterSpacing = 0.2.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (headerClockText.isNotEmpty() && !isCompactSplit) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xCC0D1829))
                                    .border(1.dp, HybridCyan.copy(alpha = 0.45f), RoundedCornerShape(50))
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

                // Quick Gallery Photo Wallpaper & Scenic Theme Picker right in Audio Section Header!
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
                            .testTag("audio_header_gallery_wallpaper_btn")
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
                            .testTag("audio_header_theme_studio_btn")
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

            // Multi-Select Action Bar (Select All + Permanent Batch Delete for 1 to 1000+ songs)
            if (multiSelectMode) {
                LiquidGlassBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isTablet) 20.dp else 12.dp, vertical = 4.dp),
                    cornerRadius = 16.dp,
                    tintColor = HybridCoralRed,
                    glassAlpha = 0.28f
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    multiSelectMode = false
                                    selectedTrackIds.clear()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isHindi) "${selectedTrackIds.size} चुने गए" else "${selectedTrackIds.size} Selected",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    val allIds = currentDisplayedTracks.map { it.id }
                                    if (selectedTrackIds.size == allIds.size) {
                                        selectedTrackIds.clear()
                                    } else {
                                        selectedTrackIds.clear()
                                        selectedTrackIds.addAll(allIds)
                                    }
                                }
                            ) {
                                Text(
                                    text = if (selectedTrackIds.size == currentDisplayedTracks.size && currentDisplayedTracks.isNotEmpty()) {
                                        if (isHindi) "सब हटाएं" else "Clear All"
                                    } else {
                                        if (isHindi) "सभी चुनें (${currentDisplayedTracks.size})" else "Select All (${currentDisplayedTracks.size})"
                                    },
                                    color = HybridCyan,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                )
                            }

                            Button(
                                onClick = {
                                    val combined = (musicItems + videoItems).distinctBy { it.id }
                                    val itemsToDelete = combined.filter { it.id in selectedTrackIds }
                                    executeBatchPermanentDelete(itemsToDelete)
                                },
                                enabled = selectedTrackIds.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = HybridCoralRed,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("audio_batch_delete_btn")
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isHindi) "हटाएं" else "Delete",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // 2. Clear Labeled Sub-Tabs Row: All | New | Videos | Playlist | Folders + Multi-Select + Sort (⇅)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isTablet) 20.dp else 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AudioLabeledTabChip(
                        label = if (isHindi) "सभी (${musicItems.size})" else "All (${musicItems.size})",
                        selected = activeTab == AudioSubTab.ALL && selectedFolderName == null && !viewingFavoritesPlaylist,
                        testTag = "audio_tab_all",
                        onClick = {
                            activeTab = AudioSubTab.ALL
                            selectedFolderName = null
                            selectedPlaylistId = null
                            viewingFavoritesPlaylist = false
                        }
                    )
                    AudioLabeledTabChip(
                        label = if (isHindi) "नए गाने" else "New",
                        selected = activeTab == AudioSubTab.NEW && selectedFolderName == null && !viewingFavoritesPlaylist,
                        testTag = "audio_tab_new",
                        onClick = {
                            activeTab = AudioSubTab.NEW
                            selectedFolderName = null
                            selectedPlaylistId = null
                            viewingFavoritesPlaylist = false
                        }
                    )
                    AudioLabeledTabChip(
                        label = if (isHindi) "वीडियो (${videoItems.size})" else "Videos (${videoItems.size})",
                        selected = activeTab == AudioSubTab.VIDEOS && selectedFolderName == null && !viewingFavoritesPlaylist,
                        testTag = "audio_tab_videos",
                        onClick = {
                            activeTab = AudioSubTab.VIDEOS
                            selectedFolderName = null
                            selectedPlaylistId = null
                            viewingFavoritesPlaylist = false
                        }
                    )
                    AudioLabeledTabChip(
                        label = if (isHindi) "प्लेलिस्ट" else "Playlist",
                        selected = activeTab == AudioSubTab.PLAYLIST || viewingFavoritesPlaylist || selectedPlaylistId != null,
                        testTag = "audio_tab_playlist",
                        onClick = {
                            activeTab = AudioSubTab.PLAYLIST
                            selectedFolderName = null
                        }
                    )
                    AudioLabeledTabChip(
                        label = if (isHindi) "फ़ोल्डर" else "Folders",
                        selected = activeTab == AudioSubTab.FOLDERS || selectedFolderName != null,
                        testTag = "audio_tab_folders",
                        onClick = {
                            activeTab = AudioSubTab.FOLDERS
                            viewingFavoritesPlaylist = false
                            selectedPlaylistId = null
                        }
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Multi-Select Checklist Button
                LiquidGlassIconOrb(
                    icon = Icons.Default.Checklist,
                    contentDescription = if (isHindi) "कई गाने चुनें" else "Multi-Select Songs",
                    tintColor = if (multiSelectMode) HybridCoralRed else HybridCyan,
                    size = 36.dp,
                    iconSize = 18.dp,
                    testTag = "audio_multiselect_btn",
                    onClick = {
                        multiSelectMode = !multiSelectMode
                        if (!multiSelectMode) selectedTrackIds.clear()
                    }
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Compact Sort Button on Right
                LiquidGlassIconOrb(
                    icon = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = if (isHindi) "क्रम बदलें" else "Sort Songs",
                    tintColor = HybridGold,
                    size = 36.dp,
                    iconSize = 18.dp,
                    testTag = "audio_sort_btn",
                    onClick = { showSortDialog = true }
                )
            }

            // Optional Breadcrumb Back Bar when inside a specific Folder or Playlist
            if (selectedFolderName != null || viewingFavoritesPlaylist || selectedPlaylistId != null) {
                val activePlaylistObj = playlists.firstOrNull { it.id == selectedPlaylistId }
                val headerLabel = when {
                    selectedFolderName == videoSongsFolderKey -> if (isHindi) "वीडियो गाने (ऑडियो मोड)" else "Video Songs (Play as Audio)"
                    selectedFolderName != null -> selectedFolderName!!
                    viewingFavoritesPlaylist -> if (isHindi) "पसंदीदा गाने (Favourites)" else "Favourite Songs"
                    activePlaylistObj != null -> activePlaylistObj.name
                    else -> ""
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LiquidGlassIconOrb(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tintColor = HybridEmerald,
                        size = 34.dp,
                        iconSize = 18.dp,
                        onClick = {
                            selectedFolderName = null
                            selectedPlaylistId = null
                            viewingFavoritesPlaylist = false
                        }
                    )
                    Text(
                        text = headerLabel,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 3. Main Content Area (Folders Grid, Playlists List, or Track List)
            when {
                activeTab == AudioSubTab.FOLDERS && selectedFolderName == null -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(
                            start = if (isTablet) 20.dp else 12.dp,
                            end = if (isTablet) 20.dp else 12.dp,
                            top = 6.dp,
                            bottom = 92.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1st Special Folder: Video Songs (Play Device Videos as Audio!)
                        item {
                            AudioFolderRowCard(
                                folderTitle = if (isHindi) "वीडियो गाने (Video Songs)" else "Video Songs",
                                subtitle = if (isHindi) {
                                    "${videoItems.size} वीडियो • ऑडियो में सुनें"
                                } else {
                                    "${videoItems.size} Videos • Listen as Audio"
                                },
                                icon = Icons.Default.VideoLibrary,
                                accent = HybridCyan,
                                onClick = { selectedFolderName = videoSongsFolderKey }
                            )
                        }

                        // All Real Device Audio Folders
                        items(groupedAudioFolders, key = { it.key }) { (folderName, tracks) ->
                            AudioFolderRowCard(
                                folderTitle = folderName,
                                subtitle = if (isHindi) "${tracks.size} गाने" else "${tracks.size} Songs",
                                icon = Icons.Default.Folder,
                                accent = HybridEmerald,
                                onClick = { selectedFolderName = folderName }
                            )
                        }
                    }
                }

                activeTab == AudioSubTab.PLAYLIST && !viewingFavoritesPlaylist && selectedPlaylistId == null -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(
                            start = if (isTablet) 20.dp else 12.dp,
                            end = if (isTablet) 20.dp else 12.dp,
                            top = 6.dp,
                            bottom = 92.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            AudioFolderRowCard(
                                folderTitle = if (isHindi) "पसंदीदा गाने (Favourites)" else "Favourites ❤️",
                                subtitle = if (isHindi) {
                                    "${favoriteAudioAndVideoTracks.size} पसंदीदा गाने"
                                } else {
                                    "${favoriteAudioAndVideoTracks.size} Liked Songs"
                                },
                                icon = Icons.Default.Favorite,
                                accent = Color(0xFFFF4081),
                                onClick = { viewingFavoritesPlaylist = true }
                            )
                        }

                        item {
                            AudioFolderRowCard(
                                folderTitle = if (isHindi) "+ नई प्लेलिस्ट बनाएं" else "+ Create New Playlist",
                                subtitle = if (isHindi) "अपनी कस्टम प्लेलिस्ट जोड़ें" else "Add custom music playlist",
                                icon = Icons.Default.PlaylistAdd,
                                accent = HybridGold,
                                onClick = { showCreatePlaylistDialog = true }
                            )
                        }

                        items(playlists, key = { it.id }) { pl ->
                            AudioFolderRowCard(
                                folderTitle = pl.name,
                                subtitle = if (isHindi) "कस्टम प्लेलिस्ट" else "Custom Playlist",
                                icon = Icons.AutoMirrored.Filled.QueueMusic,
                                accent = HybridEmerald,
                                onClick = { selectedPlaylistId = pl.id }
                            )
                        }
                    }
                }

                selectedPlaylistId != null -> {
                    val allCombined by viewModel.allMediaItems.collectAsState()
                    val playlistTracks by remember(selectedPlaylistId, allCombined) {
                        viewModel.getPlaylistItemsFlow(selectedPlaylistId!!, allCombined)
                    }.collectAsState(initial = emptyList())

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(
                            start = if (isTablet) 20.dp else 12.dp,
                            end = if (isTablet) 20.dp else 12.dp,
                            top = 6.dp,
                            bottom = 92.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (playlistTracks.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                EmptyAudioLibraryState(
                                    isHindi = isHindi,
                                    onRescan = { viewModel.loadMedia() }
                                )
                            }
                        } else {
                            items(playlistTracks, key = { it.id }) { track ->
                                val isSelected = track.id in selectedTrackIds
                                CleanAudioTrackRow(
                                    track = track,
                                    isCurrent = nowPlaying?.id == track.id,
                                    isPlaying = isPlaying && nowPlaying?.id == track.id,
                                    isHindi = isHindi,
                                    multiSelectMode = multiSelectMode,
                                    isSelected = isSelected,
                                    onPlay = {
                                        if (multiSelectMode) {
                                            if (isSelected) selectedTrackIds.remove(track.id)
                                            else selectedTrackIds.add(track.id)
                                        } else {
                                            viewModel.playMusic(track)
                                            onOpenFullMusicPlayer()
                                        }
                                    },
                                    onLongClick = {
                                        if (!multiSelectMode) {
                                            multiSelectMode = true
                                            selectedTrackIds.clear()
                                            selectedTrackIds.add(track.id)
                                        }
                                    },
                                    onOpenMenu = { sevenMenuTargetItem = track }
                                )
                            }
                        }
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(
                            start = if (isTablet) 20.dp else 12.dp,
                            end = if (isTablet) 20.dp else 12.dp,
                            top = 6.dp,
                            bottom = 92.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (currentDisplayedTracks.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                EmptyAudioLibraryState(
                                    isHindi = isHindi,
                                    onRescan = { viewModel.loadMedia() }
                                )
                            }
                        } else {
                            items(currentDisplayedTracks, key = { it.id }) { track ->
                                val isSelected = track.id in selectedTrackIds
                                CleanAudioTrackRow(
                                    track = track,
                                    isCurrent = nowPlaying?.id == track.id,
                                    isPlaying = isPlaying && nowPlaying?.id == track.id,
                                    isHindi = isHindi,
                                    multiSelectMode = multiSelectMode,
                                    isSelected = isSelected,
                                    onPlay = {
                                        if (multiSelectMode) {
                                            if (isSelected) selectedTrackIds.remove(track.id)
                                            else selectedTrackIds.add(track.id)
                                        } else {
                                            viewModel.playMusic(track)
                                            onOpenFullMusicPlayer()
                                        }
                                    },
                                    onLongClick = {
                                        if (!multiSelectMode) {
                                            multiSelectMode = true
                                            selectedTrackIds.clear()
                                            selectedTrackIds.add(track.id)
                                        }
                                    },
                                    onOpenMenu = { sevenMenuTargetItem = track }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Bottom Multi-Select 4-Action Dock (Hybrid Share, Delete, Details, 3-Dot More) OR Floating Mini Now-Playing Dock
        val selectedMediaObjects = remember(selectedTrackIds.toList(), musicItems, videoItems) {
            val pool = (musicItems + videoItems).distinctBy { it.id }
            pool.filter { it.id in selectedTrackIds }
        }

        if (multiSelectMode && selectedMediaObjects.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xF50B1220),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, HybridEmerald.copy(alpha = 0.70f)),
                shadowElevation = 18.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Hybrid Share
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val first = selectedMediaObjects.firstOrNull()
                                if (selectedMediaObjects.size == 1 && first != null) {
                                    onOpenHybridShareForItem(first)
                                } else {
                                    WifiDirectTransferEngine.shareRealFilesViaSystem(context, selectedMediaObjects)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .testTag("audio_multiselect_action_hybrid_share")
                    ) {
                        Icon(Icons.Default.WifiTethering, contentDescription = "Hybrid Share", tint = HybridEmerald, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(if (isHindi) "हाइब्रिड शेयर" else "Hybrid Share", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // 2. Delete
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                executeBatchPermanentDelete(selectedMediaObjects)
                            }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .testTag("audio_multiselect_action_delete")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(if (isHindi) "हटाएं" else "Delete", color = Color(0xFFFF5252), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // 3. Details
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showBatchDetailsDialog = true }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .testTag("audio_multiselect_action_details")
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Details", tint = HybridCyan, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(if (isHindi) "विवरण" else "Details", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // 4. 3-Dot More Menu
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showBatchMoreDialog = true }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .testTag("audio_multiselect_action_more")
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = HybridGold, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(if (isHindi) "और विकल्प" else "More", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            val activeTrack = nowPlaying ?: sortedMusicItems.firstOrNull()
            if (activeTrack != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = if (isTablet) 24.dp else 12.dp)
                        .padding(bottom = 8.dp)
                        .fillMaxWidth()
                ) {
                    LiquidGlassBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_full_music_player_bar"),
                        cornerRadius = 22.dp,
                        tintColor = HybridEmerald,
                        glassAlpha = 0.34f,
                        onClick = onOpenFullMusicPlayer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GuaranteedAlbumArt(
                                mediaPath = activeTrack.path,
                                title = activeTrack.cleanTitle,
                                isVideo = activeTrack.isVideo,
                                modifier = Modifier.size(44.dp),
                                cornerRadius = 12.dp,
                                accentColor = HybridEmerald
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeTrack.cleanTitle,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${activeTrack.artist.ifBlank { if (activeTrack.isVideo) "Video Song" else "Local Artist" }} • ${activeTrack.format}",
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
                                Icon(
                                    Icons.Default.SkipPrevious,
                                    contentDescription = "Previous",
                                    tint = Color.White
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (nowPlaying == null) {
                                        viewModel.playMusic(activeTrack)
                                    } else {
                                        viewModel.togglePlayPause()
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(HybridEmerald)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.Black
                                )
                            }

                            IconButton(
                                onClick = { viewModel.playNext() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.SkipNext,
                                    contentDescription = "Next",
                                    tint = Color.White
                                )
                            }

                            IconButton(
                                onClick = { sevenMenuTargetItem = activeTrack },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "More",
                                    tint = HybridTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val selectedBatchItems = remember(selectedTrackIds.toList(), musicItems, videoItems) {
        val pool = (musicItems + videoItems).distinctBy { it.id }
        pool.filter { it.id in selectedTrackIds }
    }

    if (showBatchDetailsDialog && selectedBatchItems.isNotEmpty()) {
        val totalBytes = selectedBatchItems.sumOf { it.size }
        val totalDurationMs = selectedBatchItems.sumOf { it.duration }
        val totalMb = String.format(Locale.US, "%.2f MB", totalBytes / (1024.0 * 1024.0))
        val totalMins = (totalDurationMs / 60_000L)
        val single = selectedBatchItems.singleOrNull()
        AlertDialog(
            onDismissRequest = { showBatchDetailsDialog = false },
            containerColor = Color(0xFF0E1626),
            title = {
                Text(
                    text = if (single != null) (if (isHindi) "गाने का विवरण" else "Song Details")
                    else (if (isHindi) "${selectedBatchItems.size} गानों का विवरण" else "${selectedBatchItems.size} Songs Details"),
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (single != null) {
                        Text("🎵 ${single.cleanTitle}", color = HybridEmerald, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("🎤 ${if (isHindi) "कलाकार" else "Artist"}: ${single.artist.ifBlank { "Local Artist" }}", color = Color.White, fontSize = 12.sp)
                        Text("📁 ${if (isHindi) "फ़ोल्डर" else "Folder"}: ${single.folderName}", color = Color.White, fontSize = 12.sp)
                        Text("⏱️ ${if (isHindi) "अवधि" else "Duration"}: ${single.formattedDuration} • ${single.format}", color = Color.White, fontSize = 12.sp)
                        Text("💾 ${if (isHindi) "आकार" else "Size"}: ${single.formattedSize}", color = Color.White, fontSize = 12.sp)
                        Text("📍 ${single.path}", color = HybridTextSecondary, fontSize = 11.sp)
                    } else {
                        Text("🎵 ${if (isHindi) "कुल चुने गए गाने" else "Selected Songs"}: ${selectedBatchItems.size}", color = HybridEmerald, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("💾 ${if (isHindi) "कुल आकार" else "Total Size"}: $totalMb", color = Color.White, fontSize = 13.sp)
                        Text("⏱️ ${if (isHindi) "कुल समय" else "Total Duration"}: $totalMins min", color = Color.White, fontSize = 13.sp)
                        Text("📁 ${if (isHindi) "फ़ोल्डर" else "Folders"}: ${selectedBatchItems.map { it.folderName }.distinct().joinToString(", ")}", color = HybridTextSecondary, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBatchDetailsDialog = false }) {
                    Text(if (isHindi) "ठीक है" else "OK", color = HybridEmerald, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showBatchMoreDialog && selectedBatchItems.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showBatchMoreDialog = false },
            containerColor = Color(0xFF0E1626),
            title = {
                Text(
                    text = if (isHindi) "अतिरिक्त विकल्प (${selectedBatchItems.size} चुने गए)" else "More Actions (${selectedBatchItems.size} Selected)",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Play Selected
                    LiquidGlassBox(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 14.dp,
                        tintColor = HybridEmerald,
                        glassAlpha = 0.22f,
                        onClick = {
                            selectedBatchItems.firstOrNull()?.let { first ->
                                viewModel.playMusic(first)
                                showBatchMoreDialog = false
                                multiSelectMode = false
                                selectedTrackIds.clear()
                                onOpenFullMusicPlayer()
                            }
                        }
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = HybridEmerald)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(if (isHindi) "चुने गए गाने चलाएं (Play Now)" else "Play Selected Songs Now", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    // 2. Add to Favourites
                    LiquidGlassBox(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 14.dp,
                        tintColor = Color(0xFFFF4081),
                        glassAlpha = 0.20f,
                        onClick = {
                            selectedBatchItems.forEach { viewModel.toggleFavorite(it) }
                            Toast.makeText(context, if (isHindi) "❤️ पसंदीदा में जोड़े गए!" else "❤️ Updated Favourites!", Toast.LENGTH_SHORT).show()
                            showBatchMoreDialog = false
                            multiSelectMode = false
                            selectedTrackIds.clear()
                        }
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFFF4081))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(if (isHindi) "पसंदीदा (Favourite) में जोड़ें" else "Add to Favourites", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    // 3. Add to Playlist
                    LiquidGlassBox(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 14.dp,
                        tintColor = HybridCyan,
                        glassAlpha = 0.20f,
                        onClick = {
                            showBatchMoreDialog = false
                            showBatchPlaylistPickerDialog = true
                        }
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = HybridCyan)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(if (isHindi) "प्लेलिस्ट में जोड़ें (Add to Playlist)" else "Add to Playlist", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    // 4. Lock in Private Vault
                    LiquidGlassBox(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 14.dp,
                        tintColor = HybridGold,
                        glassAlpha = 0.20f,
                        onClick = {
                            selectedBatchItems.forEach { viewModel.toggleVaultLock(it) }
                            Toast.makeText(context, if (isHindi) "🔒 प्राइवेट वॉल्ट में सुरक्षित किए गए!" else "🔒 Locked in Private Vault!", Toast.LENGTH_SHORT).show()
                            showBatchMoreDialog = false
                            multiSelectMode = false
                            selectedTrackIds.clear()
                        }
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = HybridGold)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(if (isHindi) "प्राइवेट वॉल्ट में छुपाएं (Lock in Vault)" else "Lock in Private Vault", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    // 5. System Share
                    LiquidGlassBox(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 14.dp,
                        tintColor = HybridPurple,
                        glassAlpha = 0.20f,
                        onClick = {
                            WifiDirectTransferEngine.shareRealFilesViaSystem(context, selectedBatchItems)
                            showBatchMoreDialog = false
                        }
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = HybridPurple)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(if (isHindi) "अन्य ऐप्स पर शेयर करें (System Share)" else "Share via Other Apps", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    // 6. Full 7-Action Menu / Rename (when 1 track selected)
                    if (selectedBatchItems.size == 1) {
                        LiquidGlassBox(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 14.dp,
                            tintColor = HybridEmerald,
                            glassAlpha = 0.20f,
                            onClick = {
                                sevenMenuTargetItem = selectedBatchItems.first()
                                showBatchMoreDialog = false
                            }
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, tint = HybridEmerald)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(if (isHindi) "नाम बदलें और 7-एक्शन मेनू" else "Rename & Full 7-Action Menu", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    // 7. Move to Recycle Bin
                    LiquidGlassBox(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 14.dp,
                        tintColor = HybridCoralRed,
                        glassAlpha = 0.20f,
                        onClick = {
                            viewModel.moveBatchToRecycleBin(selectedBatchItems)
                            Toast.makeText(context, if (isHindi) "रीसायकल बिन में भेजे गए" else "Moved to Recycle Bin", Toast.LENGTH_SHORT).show()
                            showBatchMoreDialog = false
                            multiSelectMode = false
                            selectedTrackIds.clear()
                        }
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = HybridCoralRed)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(if (isHindi) "रीसायकल बिन में भेजें" else "Move to Recycle Bin", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBatchMoreDialog = false }) {
                    Text(if (isHindi) "बंद करें" else "Close", color = HybridEmerald, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showBatchPlaylistPickerDialog && selectedBatchItems.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showBatchPlaylistPickerDialog = false },
            containerColor = Color(0xFF0E1626),
            title = {
                Text(
                    text = if (isHindi) "प्लेलिस्ट चुनें" else "Select Playlist",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (playlists.isEmpty()) {
                        Text(
                            text = if (isHindi) "कोई प्लेलिस्ट नहीं है। नीचे नई बनाएं:" else "No playlists yet. Create one:",
                            color = HybridTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    playlists.forEach { pl ->
                        LiquidGlassBox(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 12.dp,
                            tintColor = HybridCyan,
                            glassAlpha = 0.22f,
                            onClick = {
                                selectedBatchItems.forEach { item ->
                                    viewModel.addToPlaylist(pl.id, item.id)
                                }
                                Toast.makeText(context, "Added to ${pl.name}", Toast.LENGTH_SHORT).show()
                                showBatchPlaylistPickerDialog = false
                                multiSelectMode = false
                                selectedTrackIds.clear()
                            }
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = HybridCyan)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(pl.name, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Button(
                        onClick = {
                            showBatchPlaylistPickerDialog = false
                            showCreatePlaylistDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HybridEmerald),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isHindi) "+ नई प्लेलिस्ट बनाएं" else "+ Create New Playlist", color = Color.Black, fontWeight = FontWeight.Bold)
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

    if (showThemeStudioDialog) {
        val colorThemes = listOf(
            "Hybrid Emerald" to HybridEmerald,
            "Cyberpunk Neon" to Color(0xFFFF2A85),
            "Gold Studio" to HybridGold,
            "Ocean Blue" to HybridCyan,
            "Royal Purple" to HybridPurple,
            "Crimson Red" to HybridCoralRed
        )
        AlertDialog(
            onDismissRequest = { showThemeStudioDialog = false },
            containerColor = Color(0xFF0E1626),
            title = {
                Text(
                    text = if (isHindi) "🎨 गैलरी फोटो और सीनिक थीम स्टूडियो" else "🎨 Gallery Wallpaper & Scenic Theme Studio",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Custom Gallery Photo Picker Button
                    Button(
                        onClick = {
                            showThemeStudioDialog = false
                            galleryWallpaperLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HybridEmerald),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Wallpaper, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isHindi) "गैलरी से अपनी फोटो लगाएं (हर जगह)" else "Pick Photo from Gallery (All Screens)",
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }

                    if (customWallpaperUri != null) {
                        OutlinedButton(
                            onClick = {
                                viewModel.setCustomWallpaper(null)
                                Toast.makeText(context, if (isHindi) "डिफ़ॉल्ट सीनिक थीम लागू की गई" else "Restored Scenic Picture Theme", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isHindi) "गैलरी फोटो हटाएं (डिफ़ॉल्ट पिक्चर थीम दिखाएं)" else "Clear Gallery Photo (Use Scenic Theme)", color = HybridCoralRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Text(
                        text = if (isHindi) "🌌 डिफ़ॉल्ट सीनिक पिक्चर बैकग्राउंड (Scenic Themes)" else "🌌 System Default Scenic Picture Backdrops",
                        color = HybridCyan,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                    ScenicBackdropPresets.PRESETS.forEach { preset ->
                        val isSelected = scenicBackdropPreset == preset.name && customWallpaperUri == null
                        LiquidGlassBox(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 14.dp,
                            tintColor = preset.primaryGlow,
                            glassAlpha = if (isSelected) 0.34f else 0.16f,
                            isActive = isSelected,
                            onClick = {
                                viewModel.setCustomWallpaper(null)
                                viewModel.selectScenicBackdropPreset(preset.name)
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Brush.linearGradient(listOf(preset.primaryGlow, preset.secondaryGlow)))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = if (isHindi) preset.titleHi else preset.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = preset.primaryGlow, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    Text(
                        text = if (isHindi) "✨ ऐप कलर एक्सेंट थीम" else "✨ App Accent Color Themes",
                        color = HybridGold,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                    colorThemes.forEach { (tName, tColor) ->
                        val active = activeTheme == tName
                        LiquidGlassBox(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 12.dp,
                            tintColor = tColor,
                            glassAlpha = if (active) 0.32f else 0.14f,
                            onClick = { viewModel.selectTheme(tName) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(tName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                if (active) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = tColor, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeStudioDialog = false }) {
                    Text(if (isHindi) "पूर्ण" else "Done", color = HybridEmerald, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            containerColor = HybridCardBg,
            title = {
                Text(
                    text = if (isHindi) "नई प्लेलिस्ट बनाएं" else "Create New Playlist",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
            },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    singleLine = true,
                    label = {
                        Text(
                            if (isHindi) "प्लेलिस्ट का नाम" else "Playlist Name",
                            color = HybridTextSecondary
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newPlaylistName.trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.addPlaylist(trimmed)
                            newPlaylistName = ""
                        }
                        showCreatePlaylistDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HybridEmerald)
                ) {
                    Text(
                        if (isHindi) "बनाएं" else "Create",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text(if (isHindi) "रद्द करें" else "Cancel", color = HybridTextSecondary)
                }
            }
        )
    }

    if (showSortDialog) {
        val sortOptions = listOf(
            Triple("NewToOld", if (isHindi) "नए से पुराने (Newest First)" else "Newest to Oldest", Icons.Default.Schedule),
            Triple("OldToNew", if (isHindi) "पुराने से नए (Oldest First)" else "Oldest to Newest", Icons.Default.History),
            Triple("AtoZ", if (isHindi) "नाम: A से Z" else "Name: A to Z", Icons.Default.SortByAlpha),
            Triple("ZtoA", if (isHindi) "नाम: Z से A" else "Name: Z to A", Icons.Default.SortByAlpha),
            Triple("ShortToLong", if (isHindi) "छोटे से बड़े गाने (5s+)" else "Shortest to Longest (5s+)", Icons.Default.Timelapse),
            Triple("LongToShort", if (isHindi) "बड़े से छोटे गाने" else "Longest to Shortest", Icons.Default.Timer)
        )
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            containerColor = HybridCardBg,
            title = {
                Text(
                    text = if (isHindi) "गानों का क्रम चुनें (Sort By)" else "Sort Songs By",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sortOptions.forEach { (key, label, icon) ->
                        val active = audioSortOrder == key
                        LiquidGlassBox(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 14.dp,
                            tintColor = if (active) HybridEmerald else Color.White,
                            glassAlpha = if (active) 0.30f else 0.12f,
                            onClick = {
                                viewModel.setAudioSortOrder(key)
                                showSortDialog = false
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (active) HybridEmerald else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = label,
                                        color = if (active) HybridEmerald else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                if (active) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = HybridEmerald,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortDialog = false }) {
                    Text(if (isHindi) "बंद करें" else "Close", color = HybridEmerald, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    sevenMenuTargetItem?.let { item ->
        val isFav = favoriteItems.any { it.id == item.id }
        MediaSevenActionDialog(
            item = item,
            isFavorite = isFav,
            isHindi = isHindi,
            onDismiss = { sevenMenuTargetItem = null },
            onHybridShare = { onOpenHybridShareForItem(it) },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onLockInVault = { viewModel.toggleVaultLock(it) },
            onPermanentDeleteConfirmed = {
                viewModel.deletePermanently(it)
                sevenMenuTargetItem = null
            },
            onMoveToRecycleBin = {
                viewModel.moveToRecycleBin(it)
                sevenMenuTargetItem = null
            },
            onPlayBackgroundAudio = {
                viewModel.playMusic(it)
                onOpenFullMusicPlayer()
            },
            onRenameCompleted = {
                viewModel.loadMedia()
            }
        )
    }
}

@Composable
private fun AudioLabeledTabChip(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) {
                    Brush.horizontalGradient(
                        listOf(HybridEmerald.copy(alpha = 0.34f), HybridCyan.copy(alpha = 0.24f))
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.04f))
                    )
                }
            )
            .border(
                width = if (selected) 1.4.dp else 1.dp,
                color = if (selected) HybridEmerald else Color.White.copy(alpha = 0.16f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) HybridEmerald else Color.White,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun AudioFolderRowCard(
    folderTitle: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    LiquidGlassBox(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        tintColor = accent,
        glassAlpha = 0.20f,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.22f))
                    .border(1.dp, accent.copy(alpha = 0.65f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = folderTitle,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folderTitle,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CleanAudioTrackRow(
    track: MediaItem,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isHindi: Boolean,
    multiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onPlay: () -> Unit,
    onLongClick: () -> Unit = {},
    onOpenMenu: () -> Unit
) {
    val accent = when {
        isSelected -> HybridCoralRed
        isCurrent -> HybridEmerald
        else -> Color.White
    }
    LiquidGlassBox(
        modifier = Modifier
            .fillMaxWidth()
            .timedMediaTouchHold(
                onClick = onPlay,
                onHold2sPlay = {
                    // 2-Second Hold selects the song and activates Multi-Select Mode!
                    onLongClick()
                },
                onHold4sMenu = {
                    onLongClick()
                    onOpenMenu()
                }
            )
            .testTag("audio_track_row_${track.id}"),
        cornerRadius = 16.dp,
        tintColor = accent,
        glassAlpha = if (isCurrent || isSelected) 0.28f else 0.14f,
        isActive = isSelected
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (multiSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onPlay() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = HybridCoralRed,
                        checkmarkColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            Box(contentAlignment = Alignment.Center) {
                GuaranteedAlbumArt(
                    mediaPath = track.path,
                    title = track.cleanTitle,
                    isVideo = track.isVideo,
                    modifier = Modifier.size(48.dp),
                    cornerRadius = 12.dp,
                    accentColor = if (isCurrent) HybridEmerald else HybridCyan
                )
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Playing",
                            tint = HybridEmerald,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.cleanTitle,
                    color = if (isCurrent) HybridEmerald else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val artistLabel = track.artist.ifBlank {
                    if (track.isVideo) {
                        if (isHindi) "वीडियो गाना • ${track.folderName}" else "Video Song • ${track.folderName}"
                    } else {
                        track.folderName.ifBlank { "Local Artist" }
                    }
                }
                Text(
                    text = "$artistLabel • ${track.formattedDuration} • ${track.format}",
                    color = HybridTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onOpenMenu,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("audio_3dot_${track.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Track Options",
                    tint = HybridTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyAudioLibraryState(
    isHindi: Boolean,
    onRescan: () -> Unit
) {
    LiquidGlassBox(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        cornerRadius = 20.dp,
        tintColor = HybridCyan,
        glassAlpha = 0.16f
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LibraryMusic,
                contentDescription = null,
                tint = HybridCyan,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = if (isHindi) "कोई ऑडियो फ़ाइल नहीं मिली" else "No Audio Tracks Found",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp
            )
            Button(
                onClick = onRescan,
                colors = ButtonDefaults.buttonColors(containerColor = HybridEmerald)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isHindi) "फ़ोन स्कैन करें" else "Scan Phone Storage",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
