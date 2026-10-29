package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.MediaItem
import com.example.ui.theme.*
import com.example.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    viewModel: PlayerViewModel,
    onPlayMusic: (MediaItem) -> Unit,
    onPlayVideo: (MediaItem) -> Unit,
    onNavigateToEqualizer: () -> Unit,
    onNavigateToPrivateFolder: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToHistory: () -> Unit,
    accentColor: Color
) {
    val context = LocalContext.current
    var isVideoTab by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var showThemeDialog by remember { mutableStateOf(false) }

    // Observe state from ViewModel
    val videos by viewModel.videoItems.collectAsState()
    val music by viewModel.musicItems.collectAsState()
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val isPlayingMusic by viewModel.isPlaying.collectAsState()
    val activeTheme by viewModel.activeTheme.collectAsState()

    val currentItems = if (isVideoTab) videos else music

    // Filter by search & folder
    val filteredItems = remember(currentItems, searchQuery, selectedFolder) {
        currentItems.filter { item ->
            val matchesSearch = item.title.contains(searchQuery, ignoreCase = true) ||
                    item.artist.contains(searchQuery, ignoreCase = true)
            val matchesFolder = selectedFolder == null || item.folderName == selectedFolder
            matchesSearch && matchesFolder
        }
    }

    // Get unique folders
    val folders = remember(currentItems) {
        currentItems.map { it.folderName }.distinct()
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayCircleFilled,
                            contentDescription = "Logo",
                            tint = accentColor,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MAX Player",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(accentColor.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "PRO",
                                color = accentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Theme Picker Trigger
                    IconButton(onClick = { showThemeDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Themes",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Box
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search premium music & videos...", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                            }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        disabledContainerColor = MaterialTheme.colorScheme.surface,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Hub Features Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HubItem(Icons.Default.Lock, "Safe Folder", onNavigateToPrivateFolder, accentColor)
                    HubItem(Icons.Default.GraphicEq, "Equalizer", onNavigateToEqualizer, accentColor)
                    HubItem(Icons.Default.PlaylistPlay, "Playlists", onNavigateToPlaylists, accentColor)
                    HubItem(Icons.Default.History, "History", onNavigateToHistory, accentColor)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tab Selectors
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(4.dp)
                ) {
                    TabButton(
                        title = "Videos",
                        isActive = isVideoTab,
                        onClick = {
                            isVideoTab = true
                            selectedFolder = null
                        },
                        accentColor = accentColor,
                        modifier = Modifier.weight(1f)
                    )
                    TabButton(
                        title = "Music Tracks",
                        isActive = !isVideoTab,
                        onClick = {
                            isVideoTab = false
                            selectedFolder = null
                        },
                        accentColor = accentColor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        bottomBar = {
            // Mini Music Player persistent overlay
            nowPlaying?.let { track ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onPlayMusic(track) }
                        .padding(12.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = track.coverUrl ?: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=100&auto=format&fit=crop",
                        contentDescription = "Cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            color = Color.Gray,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = { viewModel.togglePlayPause() }) {
                        Icon(
                            imageVector = if (isPlayingMusic) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                            contentDescription = "Play/Pause",
                            tint = accentColor,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            // Folders Row (Horizontal scroll)
            if (folders.isNotEmpty()) {
                Text(
                    text = "Smart Directories",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // "All" item
                    item {
                        FolderChip(
                            name = "All Files",
                            count = currentItems.size,
                            isSelected = selectedFolder == null,
                            onClick = { selectedFolder = null },
                            accentColor = accentColor
                        )
                    }

                    items(folders) { folderName ->
                        val count = currentItems.count { it.folderName == folderName }
                        FolderChip(
                            name = folderName,
                            count = count,
                            isSelected = selectedFolder == folderName,
                            onClick = { selectedFolder = folderName },
                            accentColor = accentColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Media Items List
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Empty", tint = Color.Gray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No items found in this directory", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredItems) { item ->
                        MediaItemRow(
                            item = item,
                            onClick = {
                                if (item.isVideo) {
                                    onPlayVideo(item)
                                } else {
                                    onPlayMusic(item)
                                }
                            },
                            onLongClick = {
                                // Dynamic safety locking toggles
                                viewModel.toggleVaultLock(item)
                            },
                            accentColor = accentColor
                        )
                    }
                }
            }
        }
    }

    // --- Custom Theme Switcher Dialog ---
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose Custom Premium Theme", color = Color.White) },
            text = {
                Column {
                    ThemeOptionRow("Deep Purple", activeTheme == "Deep Purple", AmethystPrimary) {
                        viewModel.selectTheme("Deep Purple")
                        showThemeDialog = false
                    }
                    ThemeOptionRow("Neon Blue", activeTheme == "Neon Blue", NeonBlue) {
                        viewModel.selectTheme("Neon Blue")
                        showThemeDialog = false
                    }
                    ThemeOptionRow("Luxury Gold", activeTheme == "Luxury Gold", LuxuryGold) {
                        viewModel.selectTheme("Luxury Gold")
                        showThemeDialog = false
                    }
                    ThemeOptionRow("Forest Green", activeTheme == "Forest Green", EmeraldNeon) {
                        viewModel.selectTheme("Forest Green")
                        showThemeDialog = false
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Close", color = accentColor)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun ThemeOptionRow(
    name: String,
    isSelected: Boolean,
    color: Color,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = name, color = Color.White, fontSize = 16.sp)
        }
        if (isSelected) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = color)
        }
    }
}

@Composable
fun HubItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    accentColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = accentColor)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun TabButton(
    title: String,
    isActive: Boolean,
    onClick: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isActive) accentColor.copy(alpha = 0.15f) else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isActive) accentColor else Color.White.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
fun FolderChip(
    name: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) accentColor else MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Default.Folder else Icons.Outlined.Folder,
            contentDescription = "Folder",
            tint = if (isSelected) Color.Black else accentColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = name,
            color = if (isSelected) Color.Black else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (isSelected) Color.Black.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
            Text(
                text = count.toString(),
                color = if (isSelected) Color.Black else Color.Gray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaItemRow(
    item: MediaItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail or custom graphic icon
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (item.coverUrl != null) {
                AsyncImage(
                    model = item.coverUrl,
                    contentDescription = "Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.isVideo) Icons.Default.Videocam else Icons.Default.MusicNote,
                        contentDescription = "Icon",
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Small Video length tag
            if (item.isVideo) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.formattedDuration,
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Titles and sizes
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.artist,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Gray)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = item.formattedSize,
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }

        // Action menu icon
        IconButton(onClick = onLongClick) {
            Icon(
                imageVector = if (item.isPrivate) Icons.Default.LockOpen else Icons.Default.Lock,
                contentDescription = "Lock Safe Vault",
                tint = Color.Gray.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
