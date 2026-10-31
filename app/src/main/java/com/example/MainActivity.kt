package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.MediaItem
import com.example.helpers.*
import com.example.ui.components.LiquidGlassBox
import com.example.ui.components.LiquidGlassIconOrb
import com.example.ui.components.VideoPlayerView
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

enum class ActiveScreen {
    MainTabs,
    MusicPlayer,
    VideoPlayer,
    Equalizer,
    PrivateFolder,
    CleanerHub,
    TurboShare,
    Mp3Converter,
    StatusSaver,
    Playlists,
    History
}

enum class MainBottomTab(val label: String) {
    VIDEO("Video"),
    MUSIC("Music"),
    SEARCH("Search"),
    ME("Me")
}

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    private val permissionsLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.loadMedia()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MediaNotificationHelper.ensureChannel(this)
        requestNecessaryMediaAndNotificationPermissions()

        setContent {
            val activeTheme by viewModel.activeTheme.collectAsState()
            val customWallpaperUri by viewModel.customWallpaperUri.collectAsState()
            val companionEnabled by viewModel.companionEnabled.collectAsState()
            val companionBreed by viewModel.companionBreed.collectAsState()
            val penguinsOnlyMode by viewModel.penguinsOnlyMode.collectAsState()
            val isMusicPlaying by viewModel.isPlaying.collectAsState()
            val videos by viewModel.videoItems.collectAsState()
            val music by viewModel.musicItems.collectAsState()
            val allMedia by viewModel.allMediaItems.collectAsState()
            val history by viewModel.historyItems.collectAsState()
            val globalFlagSecure by viewModel.globalFlagSecure.collectAsState()

            LaunchedEffect(globalFlagSecure) {
                try {
                    if (globalFlagSecure) {
                        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                    }
                } catch (_: Exception) {
                }
            }

            MyApplicationTheme(themeName = activeTheme) {
                val accentColor = MaterialTheme.colorScheme.primary
                val coroutineScope = rememberCoroutineScope()

                val navigationStack = remember { mutableStateListOf(ActiveScreen.MainTabs) }
                val currentScreen = navigationStack.last()
                var selectedTab by remember { mutableStateOf(MainBottomTab.VIDEO) }

                LaunchedEffect(currentScreen) {
                    viewModel.setForegroundVideoActive(currentScreen == ActiveScreen.VideoPlayer)
                }

                var activeVideoItem by remember { mutableStateOf<MediaItem?>(null) }
                var activeVideoProgress by remember { mutableStateOf(0L) }

                val watchedVideoIds = remember(history) {
                    history.map { it.first.id }.toSet()
                }

                // System Back Button Handler
                BackHandler(enabled = navigationStack.size > 1 || selectedTab != MainBottomTab.VIDEO) {
                    if (navigationStack.size > 1) {
                        if (navigationStack.last() == ActiveScreen.PrivateFolder) {
                            viewModel.lockVault()
                        }
                        navigationStack.removeAt(navigationStack.lastIndex)
                    } else if (selectedTab != MainBottomTab.VIDEO) {
                        selectedTab = MainBottomTab.VIDEO
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // Optional Custom Phone Gallery Wallpaper Overlay
                    if (customWallpaperUri != null) {
                        AsyncImage(
                            model = customWallpaperUri,
                            contentDescription = "Custom Wallpaper",
                            contentScale = ContentScale.Crop,
                            alpha = 0.22f,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Crossfade(targetState = currentScreen, label = "hybrid_screen_router") { screen ->
                        when (screen) {
                            ActiveScreen.MainTabs -> {
                                Scaffold(
                                    containerColor = if (customWallpaperUri != null) Color.Transparent else MaterialTheme.colorScheme.background,
                                    bottomBar = {
                                        LiquidGlassBox(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            cornerRadius = 26.dp,
                                            tintColor = HybridEmerald,
                                            glassAlpha = 0.22f
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceEvenly
                                            ) {
                                                MainBottomTab.entries.forEach { tab ->
                                                    val selected = selectedTab == tab
                                                    val tabTint = when (tab) {
                                                        MainBottomTab.VIDEO -> Color(0xFF00E676)
                                                        MainBottomTab.MUSIC -> Color(0xFF00E5FF)
                                                        MainBottomTab.SEARCH -> Color(0xFFFFD54F)
                                                        MainBottomTab.ME -> Color(0xFFFF4081)
                                                    }
                                                    val icon = when (tab) {
                                                        MainBottomTab.VIDEO -> if (selected) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary
                                                        MainBottomTab.MUSIC -> if (selected) Icons.Filled.LibraryMusic else Icons.Outlined.LibraryMusic
                                                        MainBottomTab.SEARCH -> if (selected) Icons.Filled.Search else Icons.Outlined.Search
                                                        MainBottomTab.ME -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
                                                    }
                                                    LiquidGlassBox(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .padding(horizontal = 4.dp)
                                                            .testTag("nav_tab_${tab.name.lowercase()}"),
                                                        cornerRadius = 20.dp,
                                                        tintColor = if (selected) tabTint else Color.White,
                                                        glassAlpha = if (selected) 0.28f else 0.05f,
                                                        onClick = { selectedTab = tab }
                                                    ) {
                                                        Column(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(vertical = 8.dp),
                                                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                                                        ) {
                                                            Icon(
                                                                imageVector = icon,
                                                                contentDescription = tab.label,
                                                                tint = if (selected) tabTint else Color.White.copy(alpha = 0.72f),
                                                                modifier = Modifier.size(22.dp)
                                                            )
                                                            Spacer(modifier = Modifier.height(3.dp))
                                                            Text(
                                                                text = tab.label,
                                                                fontSize = 11.sp,
                                                                color = if (selected) Color.White else HybridTextSecondary,
                                                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                ) { innerPadding ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(innerPadding)
                                    ) {
                                        when (selectedTab) {
                                            MainBottomTab.VIDEO -> {
                                                VideoSectionScreen(
                                                    viewModel = viewModel,
                                                    onPlayVideo = { video ->
                                                        activeVideoItem = video
                                                        activeVideoProgress = 0L
                                                        coroutineScope.launch {
                                                            activeVideoProgress = viewModel.getVideoProgress(video.id)
                                                            navigationStack.add(ActiveScreen.VideoPlayer)
                                                        }
                                                    },
                                                    onOpenCleanerHub = {
                                                        navigationStack.add(ActiveScreen.CleanerHub)
                                                    },
                                                    onOpenTurboShare = {
                                                        navigationStack.add(ActiveScreen.TurboShare)
                                                    }
                                                )
                                            }
                                            MainBottomTab.MUSIC -> {
                                                AudioLibraryScreen(
                                                    viewModel = viewModel,
                                                    onOpenStudioPlayer = {
                                                        navigationStack.add(ActiveScreen.MusicPlayer)
                                                    },
                                                    onOpenEqualizer = {
                                                        navigationStack.add(ActiveScreen.Equalizer)
                                                    },
                                                    onOpenTurboShare = {
                                                        navigationStack.add(ActiveScreen.TurboShare)
                                                    }
                                                )
                                            }
                                            MainBottomTab.SEARCH -> {
                                                SmartSearchScreen(
                                                    viewModel = viewModel,
                                                    onPlayVideo = { video ->
                                                        activeVideoItem = video
                                                        activeVideoProgress = 0L
                                                        coroutineScope.launch {
                                                            activeVideoProgress = viewModel.getVideoProgress(video.id)
                                                            navigationStack.add(ActiveScreen.VideoPlayer)
                                                        }
                                                    },
                                                    onPlayMusic = { track ->
                                                        viewModel.playMusic(track)
                                                        navigationStack.add(ActiveScreen.MusicPlayer)
                                                    }
                                                )
                                            }
                                            MainBottomTab.ME -> {
                                                MeToolkitScreen(
                                                    viewModel = viewModel,
                                                    onOpenPrivacyVault = {
                                                        navigationStack.add(ActiveScreen.PrivateFolder)
                                                    },
                                                    onOpenTurboShare = {
                                                        navigationStack.add(ActiveScreen.TurboShare)
                                                    },
                                                    onOpenMp3Converter = {
                                                        navigationStack.add(ActiveScreen.Mp3Converter)
                                                    },
                                                    onOpenStatusSaver = {
                                                        navigationStack.add(ActiveScreen.StatusSaver)
                                                    },
                                                    onOpenCleanerHub = {
                                                        navigationStack.add(ActiveScreen.CleanerHub)
                                                    },
                                                    onPlayStreamVideo = { streamItem ->
                                                        activeVideoItem = streamItem
                                                        activeVideoProgress = 0L
                                                        navigationStack.add(ActiveScreen.VideoPlayer)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            ActiveScreen.MusicPlayer -> {
                                MusicPlayerScreen(
                                    viewModel = viewModel,
                                    onBackClick = { navigationStack.removeAt(navigationStack.lastIndex) },
                                    onOpenEqualizer = { navigationStack.add(ActiveScreen.Equalizer) },
                                    onOpenTurboShare = { navigationStack.add(ActiveScreen.TurboShare) },
                                    accentColor = accentColor
                                )
                            }

                            ActiveScreen.VideoPlayer -> {
                                activeVideoItem?.let { video ->
                                    VideoPlayerView(
                                        videoPath = video.path,
                                        initialPositionMs = activeVideoProgress,
                                        onProgressUpdate = { posMs ->
                                            viewModel.saveVideoProgress(video.id, posMs)
                                        },
                                        onBackClick = {
                                            activeVideoItem = null
                                            navigationStack.removeAt(navigationStack.lastIndex)
                                        },
                                        accentColor = accentColor,
                                        videoTitle = video.cleanTitle,
                                        allVideos = videos,
                                        onSelectVideo = { nextVid ->
                                            activeVideoItem = nextVid
                                            activeVideoProgress = 0L
                                        },
                                        onBackgroundAudioPlay = {
                                            viewModel.playMusic(video)
                                            activeVideoItem = null
                                            navigationStack.removeAt(navigationStack.lastIndex)
                                        }
                                    )
                                }
                            }

                            ActiveScreen.Equalizer -> {
                                EqualizerScreen(
                                    viewModel = viewModel,
                                    onBackClick = { navigationStack.removeAt(navigationStack.lastIndex) },
                                    accentColor = accentColor
                                )
                            }

                            ActiveScreen.PrivateFolder -> {
                                PrivateFolderScreen(
                                    viewModel = viewModel,
                                    onPlayMusic = { track ->
                                        viewModel.playMusic(track)
                                        navigationStack.add(ActiveScreen.MusicPlayer)
                                    },
                                    onPlayVideo = { video ->
                                        activeVideoItem = video
                                        activeVideoProgress = 0L
                                        coroutineScope.launch {
                                            activeVideoProgress = viewModel.getVideoProgress(video.id)
                                            navigationStack.add(ActiveScreen.VideoPlayer)
                                        }
                                    },
                                    onBackClick = {
                                        viewModel.lockVault()
                                        navigationStack.removeAt(navigationStack.lastIndex)
                                    },
                                    accentColor = accentColor
                                )
                            }

                            ActiveScreen.CleanerHub -> {
                                CleanJunkHubScreen(
                                    allVideos = videos,
                                    watchedVideoIds = watchedVideoIds,
                                    onMoveToTrash = { item -> viewModel.moveToRecycleBin(item) },
                                    onPlayVideo = { video ->
                                        activeVideoItem = video
                                        activeVideoProgress = 0L
                                        navigationStack.add(ActiveScreen.VideoPlayer)
                                    },
                                    onBackClick = { navigationStack.removeAt(navigationStack.lastIndex) }
                                )
                            }

                            ActiveScreen.TurboShare -> {
                                TurboShareScreen(
                                    allMedia = allMedia,
                                    onBackClick = { navigationStack.removeAt(navigationStack.lastIndex) }
                                )
                            }

                            ActiveScreen.Mp3Converter -> {
                                Mp3ConverterScreen(
                                    videos = videos,
                                    onBackClick = { navigationStack.removeAt(navigationStack.lastIndex) }
                                )
                            }

                            ActiveScreen.StatusSaver -> {
                                StatusSaverScreen(
                                    allVideos = videos,
                                    onPlayVideo = { video ->
                                        activeVideoItem = video
                                        activeVideoProgress = 0L
                                        navigationStack.add(ActiveScreen.VideoPlayer)
                                    },
                                    onBackClick = { navigationStack.removeAt(navigationStack.lastIndex) }
                                )
                            }

                            ActiveScreen.Playlists -> {
                                PlaylistsScreen(
                                    viewModel = viewModel,
                                    onPlayMusic = { track ->
                                        viewModel.playMusic(track)
                                        navigationStack.add(ActiveScreen.MusicPlayer)
                                    },
                                    onBackClick = { navigationStack.removeAt(navigationStack.lastIndex) },
                                    accentColor = accentColor
                                )
                            }

                            ActiveScreen.History -> {
                                HistoryScreen(
                                    viewModel = viewModel,
                                    onPlayMusic = { track ->
                                        viewModel.playMusic(track)
                                        navigationStack.add(ActiveScreen.MusicPlayer)
                                    },
                                    onPlayVideo = { video ->
                                        activeVideoItem = video
                                        activeVideoProgress = 0L
                                        coroutineScope.launch {
                                            activeVideoProgress = viewModel.getVideoProgress(video.id)
                                            navigationStack.add(ActiveScreen.VideoPlayer)
                                        }
                                    },
                                    onBackClick = { navigationStack.removeAt(navigationStack.lastIndex) },
                                    accentColor = accentColor
                                )
                            }
                        }
                    }

                    // --- REALISTIC 3D ARTICULATED PUPPY & PENGUIN COMPANION OVERLAY ---
                    if (currentScreen == ActiveScreen.MainTabs) {
                        RealisticCompanionOverlay(
                            enabled = companionEnabled,
                            breedName = companionBreed,
                            isMusicPlaying = isMusicPlaying,
                            penguinsOnlyMode = penguinsOnlyMode,
                            onQuickPlayTrigger = {
                                music.firstOrNull()?.let { viewModel.playMusic(it) }
                            },
                            onHideForSession = {
                                viewModel.setCompanionEnabled(false)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.onAppMinimizedToBackground()
    }

    private fun requestNecessaryMediaAndNotificationPermissions() {
        val perms = mutableListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
            perms.add(android.Manifest.permission.READ_MEDIA_VIDEO)
            perms.add(android.Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            perms.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val missing = perms.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                it
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            try {
                permissionsLauncher.launch(missing.toTypedArray())
            } catch (_: Exception) {
            }
        }
    }
}

