package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.input.pointer.pointerInput
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
import com.example.ui.pdf.PdfReaderScreen
import com.example.ui.screens.*
import com.example.ui.share.ShareKaroTransferScreen
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
    History,
    PdfReader
}

enum class MainBottomTab(val label: String) {
    VIDEO("Video"),
    MUSIC("Music"),
    SEARCH("Search"),
    ME("Me")
}

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()
    private var shouldAutoEnterPipOnLeave: Boolean = false

    private val permissionsLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.loadMedia()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        } catch (_: Throwable) {
        }
        try {
            MediaNotificationHelper.ensureChannel(this)
        } catch (_: Throwable) {
        }
        requestNecessaryMediaAndNotificationPermissions()

        setContent {
            val activeTheme by viewModel.activeTheme.collectAsState()
            val scenicBackdropPreset by viewModel.scenicBackdropPreset.collectAsState()
            val customWallpaperUri by viewModel.customWallpaperUri.collectAsState()
            val companionEnabled by viewModel.companionEnabled.collectAsState()
            val companionBreed by viewModel.companionBreed.collectAsState()
            val companionSizeDp by viewModel.companionSizeDp.collectAsState()
            val penguinsOnlyMode by viewModel.penguinsOnlyMode.collectAsState()
            val companionDanceMode by viewModel.companionDanceMode.collectAsState()
            val companionAuraEffect by viewModel.companionAuraEffect.collectAsState()
            val companionAutoWalk by viewModel.companionAutoWalk.collectAsState()
            val companionAnimSpeed by viewModel.companionAnimSpeed.collectAsState()
            val isMusicPlaying by viewModel.isPlaying.collectAsState()
            val nowPlaying by viewModel.nowPlaying.collectAsState()
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
                var preselectedShareItem by remember { mutableStateOf<MediaItem?>(null) }

                var activeVideoItem by remember { mutableStateOf<MediaItem?>(null) }
                var activeVideoProgress by remember { mutableStateOf(0L) }
                var isFloatingMiniVideoActive by remember { mutableStateOf(false) }

                LaunchedEffect(currentScreen, isFloatingMiniVideoActive, activeVideoItem) {
                    val videoActive = (currentScreen == ActiveScreen.VideoPlayer || isFloatingMiniVideoActive) && activeVideoItem != null
                    viewModel.setForegroundVideoActive(videoActive)
                    shouldAutoEnterPipOnLeave = videoActive
                    updateSystemPipParams(videoActive)
                    if (videoActive) {
                        // Automatically pause background audio when any foreground/mini video starts playing
                        viewModel.pauseMusicForVideoPlayback()
                    }
                    try {
                        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                            isAppearanceLightStatusBars = false
                            isAppearanceLightNavigationBars = false
                        }
                    } catch (_: Exception) {
                    }
                }

                // If user starts a song in the Audio section while Floating Mini Video is open, auto-close Mini Video
                LaunchedEffect(isMusicPlaying) {
                    if (isMusicPlaying && isFloatingMiniVideoActive) {
                        isFloatingMiniVideoActive = false
                    }
                }

                val watchedVideoIds = remember(history) {
                    history.map { it.first.id }.toSet()
                }

                // System Back Button Handler
                BackHandler(enabled = navigationStack.size > 1 || selectedTab != MainBottomTab.VIDEO) {
                    if (navigationStack.size > 1) {
                        if (navigationStack.last() == ActiveScreen.PrivateFolder) {
                            viewModel.lockVault()
                        }
                        if (navigationStack.last() == ActiveScreen.TurboShare) {
                            preselectedShareItem = null
                        }
                        navigationStack.removeAt(navigationStack.lastIndex)
                    } else if (selectedTab != MainBottomTab.VIDEO) {
                        selectedTab = MainBottomTab.VIDEO
                    }
                }

                // Direct instant launch into main app without artificial splash delay or duplicate popups

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isPipMicroWindow = maxHeight < 215.dp || maxWidth < 230.dp
                    val isCompactSplitScreen = !isPipMicroWindow && maxHeight < 430.dp

                    // 1. Default Scenic Picture Theme Backdrop (eliminates plain black background behind songs & videos)
                    GlobalScenicThemeBackdrop(
                        presetName = scenicBackdropPreset,
                        accentColor = accentColor,
                        modifier = Modifier.fillMaxSize()
                    )

                    // 2. Permanent Custom Phone Gallery Wallpaper Overlay across all screens (Video, Audio, Search, Me)
                    if (customWallpaperUri != null) {
                        AsyncImage(
                            model = customWallpaperUri,
                            contentDescription = "Custom Wallpaper",
                            contentScale = ContentScale.Crop,
                            alpha = 0.56f,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Crossfade(targetState = currentScreen, label = "hybrid_screen_router") { screen ->
                        when (screen) {
                            ActiveScreen.MainTabs -> {
                                Scaffold(
                                    containerColor = Color.Transparent,
                                    contentWindowInsets = WindowInsets.systemBars,
                                    bottomBar = {
                                        if (!isPipMicroWindow) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .navigationBarsPadding()
                                                    .padding(
                                                        horizontal = if (isCompactSplitScreen) 10.dp else 16.dp,
                                                        vertical = if (isCompactSplitScreen) 3.dp else 8.dp
                                                    )
                                            ) {
                                                LiquidGlassBox(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    cornerRadius = if (isCompactSplitScreen) 22.dp else 28.dp,
                                                    tintColor = accentColor,
                                                    glassAlpha = 0.24f
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(
                                                                horizontal = 10.dp,
                                                                vertical = if (isCompactSplitScreen) 4.dp else 7.dp
                                                            ),
                                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
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
                                                                MainBottomTab.MUSIC -> if (selected) Icons.Filled.GraphicEq else Icons.Outlined.MusicNote
                                                                MainBottomTab.SEARCH -> if (selected) Icons.Filled.ManageSearch else Icons.Outlined.Search
                                                                MainBottomTab.ME -> if (selected) Icons.Filled.AutoAwesomeMosaic else Icons.Outlined.DashboardCustomize
                                                            }
                                                            LiquidGlassBox(
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .padding(horizontal = 5.dp)
                                                                    .testTag("nav_tab_${tab.name.lowercase()}"),
                                                                cornerRadius = if (isCompactSplitScreen) 16.dp else 22.dp,
                                                                tintColor = if (selected) tabTint else Color.White,
                                                                glassAlpha = if (selected) 0.34f else 0.06f,
                                                                onClick = { selectedTab = tab }
                                                            ) {
                                                                Column(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .padding(vertical = if (isCompactSplitScreen) 5.dp else 9.dp),
                                                                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                                                                ) {
                                                                    Icon(
                                                                        imageVector = icon,
                                                                        contentDescription = tab.label,
                                                                        tint = if (selected) tabTint else Color.White.copy(alpha = 0.72f),
                                                                        modifier = Modifier.size(
                                                                            if (isCompactSplitScreen) {
                                                                                if (selected) 20.dp else 18.dp
                                                                            } else {
                                                                                if (selected) 25.dp else 22.dp
                                                                            }
                                                                        )
                                                                    )
                                                                    Spacer(modifier = Modifier.height(if (isCompactSplitScreen) 2.dp else 4.dp))
                                                                    // Pure Visual Liquid Neon Indicator Pill (Language-Agnostic)
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .width(if (selected) 22.dp else 6.dp)
                                                                            .height(3.dp)
                                                                    ) {
                                                                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                                                            drawRoundRect(
                                                                                color = if (selected) tabTint else Color.White.copy(alpha = 0.22f),
                                                                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }
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
                                                        viewModel.pauseMusicForVideoPlayback()
                                                        isFloatingMiniVideoActive = false
                                                        activeVideoItem = video
                                                        activeVideoProgress = 0L
                                                        coroutineScope.launch {
                                                            val savedPos = viewModel.getVideoProgress(video.id)
                                                            activeVideoProgress = savedPos
                                                            // Immediately record in History so this video is FIRST (index 0)
                                                            viewModel.saveVideoProgress(video.id, savedPos.coerceAtLeast(1000L))
                                                            navigationStack.add(ActiveScreen.VideoPlayer)
                                                        }
                                                    },
                                                    onOpenCleanerHub = {
                                                        navigationStack.add(ActiveScreen.CleanerHub)
                                                    },
                                                    onOpenTurboShare = {
                                                        preselectedShareItem = null
                                                        navigationStack.add(ActiveScreen.TurboShare)
                                                    },
                                                    onHybridShareItem = { item ->
                                                        preselectedShareItem = item
                                                        navigationStack.add(ActiveScreen.TurboShare)
                                                    },
                                                    onOpenPdfReader = {
                                                        navigationStack.add(ActiveScreen.PdfReader)
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
                                                        preselectedShareItem = null
                                                        navigationStack.add(ActiveScreen.TurboShare)
                                                    },
                                                    onHybridShareItem = { item ->
                                                        preselectedShareItem = item
                                                        navigationStack.add(ActiveScreen.TurboShare)
                                                    }
                                                )
                                            }
                                            MainBottomTab.SEARCH -> {
                                                SmartSearchScreen(
                                                    viewModel = viewModel,
                                                    onPlayVideo = { video ->
                                                        viewModel.pauseMusicForVideoPlayback()
                                                        isFloatingMiniVideoActive = false
                                                        activeVideoItem = video
                                                        activeVideoProgress = 0L
                                                        coroutineScope.launch {
                                                            val savedPos = viewModel.getVideoProgress(video.id)
                                                            activeVideoProgress = savedPos
                                                            viewModel.saveVideoProgress(video.id, savedPos.coerceAtLeast(1000L))
                                                            navigationStack.add(ActiveScreen.VideoPlayer)
                                                        }
                                                    },
                                                    onPlayMusic = { track ->
                                                        isFloatingMiniVideoActive = false
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
                                                        preselectedShareItem = null
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
                                                    onOpenPdfReader = {
                                                        navigationStack.add(ActiveScreen.PdfReader)
                                                    },
                                                    onPlayStreamVideo = { streamItem ->
                                                        viewModel.pauseMusicForVideoPlayback()
                                                        isFloatingMiniVideoActive = false
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
                                            activeVideoProgress = posMs
                                            viewModel.saveVideoProgress(video.id, posMs)
                                        },
                                        onBackClick = {
                                            activeVideoItem = null
                                            isFloatingMiniVideoActive = false
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
                                            viewModel.playVideoInBackground(video, activeVideoProgress, 1.0f)
                                            activeVideoItem = null
                                            isFloatingMiniVideoActive = false
                                            navigationStack.removeAt(navigationStack.lastIndex)
                                        },
                                        onBackgroundAudioPlayWithPosition = { currentPosMs, currentSpeed ->
                                            activeVideoProgress = currentPosMs
                                            viewModel.playVideoInBackground(video, currentPosMs, currentSpeed)
                                            activeVideoItem = null
                                            isFloatingMiniVideoActive = false
                                            navigationStack.removeAt(navigationStack.lastIndex)
                                        },
                                        onMinimizeToFloatingMiniPlayer = { currentPosMs ->
                                            activeVideoProgress = currentPosMs
                                            viewModel.saveVideoProgress(video.id, currentPosMs)
                                            isFloatingMiniVideoActive = true
                                            if (navigationStack.size > 1) {
                                                navigationStack.removeAt(navigationStack.lastIndex)
                                            }
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
                                        isFloatingMiniVideoActive = false
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
                                        isFloatingMiniVideoActive = false
                                        activeVideoItem = video
                                        activeVideoProgress = 0L
                                        navigationStack.add(ActiveScreen.VideoPlayer)
                                    },
                                    onBackClick = { navigationStack.removeAt(navigationStack.lastIndex) }
                                )
                            }

                            ActiveScreen.TurboShare -> {
                                ShareKaroTransferScreen(
                                    allMedia = allMedia,
                                    initialPreselectedItem = preselectedShareItem,
                                    onBackClick = {
                                        preselectedShareItem = null
                                        navigationStack.removeAt(navigationStack.lastIndex)
                                    }
                                )
                            }

                            ActiveScreen.Mp3Converter -> {
                                Mp3ConverterScreen(
                                    videos = videos,
                                    onBackClick = { navigationStack.removeAt(navigationStack.lastIndex) },
                                    onPlayExtractedAudio = { extractedTrack ->
                                        viewModel.playMusic(extractedTrack)
                                        navigationStack.add(ActiveScreen.MusicPlayer)
                                    },
                                    onAudioExtracted = {
                                        viewModel.loadMedia()
                                    }
                                )
                            }

                            ActiveScreen.StatusSaver -> {
                                StatusSaverScreen(
                                    allVideos = videos,
                                    onPlayVideo = { video ->
                                        isFloatingMiniVideoActive = false
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
                                        isFloatingMiniVideoActive = false
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

                            ActiveScreen.PdfReader -> {
                                PdfReaderScreen(
                                    onBackClick = { navigationStack.removeAt(navigationStack.lastIndex) }
                                )
                            }
                        }
                    }

                    // --- IN-APP DRAGGABLE & RESIZABLE FLOATING MINI VIDEO PLAYER ("Chhota Karke Chalayen") ---
                    if (isFloatingMiniVideoActive && currentScreen != ActiveScreen.VideoPlayer) {
                        activeVideoItem?.let { floatingVid ->
                            HybridFloatingMiniVideoWindow(
                                video = floatingVid,
                                initialPositionMs = activeVideoProgress,
                                accentColor = accentColor,
                                onProgressUpdate = { posMs ->
                                    activeVideoProgress = posMs
                                    viewModel.saveVideoProgress(floatingVid.id, posMs)
                                },
                                onExpandToFullScreen = { latestPosMs ->
                                    activeVideoProgress = latestPosMs
                                    isFloatingMiniVideoActive = false
                                    if (navigationStack.lastOrNull() != ActiveScreen.VideoPlayer) {
                                        navigationStack.add(ActiveScreen.VideoPlayer)
                                    }
                                },
                                onEnterSystemPip = { latestPosMs ->
                                    activeVideoProgress = latestPosMs
                                    isFloatingMiniVideoActive = false
                                    if (navigationStack.lastOrNull() != ActiveScreen.VideoPlayer) {
                                        navigationStack.add(ActiveScreen.VideoPlayer)
                                    }
                                    enterSystemPipSafely()
                                },
                                onCloseMiniPlayer = { latestPosMs ->
                                    viewModel.saveVideoProgress(floatingVid.id, latestPosMs)
                                    isFloatingMiniVideoActive = false
                                    activeVideoItem = null
                                }
                            )
                        }
                    }

                    // --- FLOATING BACKGROUND VIDEO AUDIO BAR ("Video Background Play" -> Tap to Return to Video!) ---
                    val bgVideoPlayingItem = nowPlaying?.takeIf { it.isVideo }
                    if (bgVideoPlayingItem != null && currentScreen == ActiveScreen.MainTabs && !isFloatingMiniVideoActive && !isPipMicroWindow) {
                        val bgPositionMs by viewModel.playbackPosition.collectAsState()
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 14.dp, vertical = 92.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xF20A1424), Color(0xF20D1F2D))
                                    )
                                )
                                .border(
                                    1.2.dp,
                                    Brush.horizontalGradient(listOf(HybridEmerald, HybridCyan)),
                                    RoundedCornerShape(18.dp)
                                )
                                .clickable {
                                    val resumePos = bgPositionMs.coerceAtLeast(0L)
                                    viewModel.pauseMusicForVideoPlayback()
                                    activeVideoItem = bgVideoPlayingItem
                                    activeVideoProgress = resumePos
                                    navigationStack.add(ActiveScreen.VideoPlayer)
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(HybridEmerald.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Headphones,
                                    contentDescription = "Background Video Audio",
                                    tint = HybridEmerald,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bgVideoPlayingItem.cleanTitle,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "🎧 Background Video Audio • Tap to Return to Video 🎬",
                                    color = HybridCyan,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                            IconButton(
                                onClick = { viewModel.togglePlayPause() },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMusicPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = HybridEmerald
                                )
                            }
                            IconButton(
                                onClick = { viewModel.playNext() },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next",
                                    tint = Color.White
                                )
                            }
                            IconButton(
                                onClick = { viewModel.onNotificationStop() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Stop Background Video",
                                    tint = Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // --- REALISTIC 3D ARTICULATED PUPPY & PENGUIN COMPANION OVERLAY ---
                    if ((currentScreen == ActiveScreen.MainTabs || currentScreen == ActiveScreen.MusicPlayer) && !isPipMicroWindow) {
                        RealisticCompanionOverlay(
                            enabled = companionEnabled,
                            breedName = companionBreed,
                            isMusicPlaying = isMusicPlaying,
                            penguinsOnlyMode = penguinsOnlyMode,
                            mascotSizeDp = companionSizeDp.dp,
                            danceModeId = companionDanceMode,
                            auraEffectId = companionAuraEffect,
                            autoWalkEnabled = companionAutoWalk,
                            animSpeedMultiplier = companionAnimSpeed,
                            onQuickPlayTrigger = {
                                if (nowPlaying != null) {
                                    viewModel.togglePlayPause()
                                } else {
                                    music.firstOrNull()?.let { viewModel.playMusic(it) }
                                }
                            },
                            onNextTrack = {
                                if (nowPlaying != null) {
                                    viewModel.playNext()
                                } else {
                                    music.firstOrNull()?.let { viewModel.playMusic(it) }
                                }
                            },
                            onPrevTrack = {
                                if (nowPlaying != null) {
                                    viewModel.playPrevious()
                                } else {
                                    music.firstOrNull()?.let { viewModel.playMusic(it) }
                                }
                            },
                            onShufflePlay = {
                                viewModel.shufflePlayMusic()
                            },
                            onSelectBreed = { viewModel.selectCompanionBreed(it) },
                            onSelectSizeDp = { viewModel.setCompanionSize(it) },
                            onSelectDanceMode = { viewModel.selectCompanionDanceMode(it) },
                            onSelectAuraEffect = { viewModel.selectCompanionAuraEffect(it) },
                            onToggleAutoWalk = { viewModel.setCompanionAutoWalk(it) },
                            onChangeAnimSpeed = { viewModel.setCompanionAnimSpeed(it) },
                            onHideForSession = {
                                viewModel.setCompanionEnabled(false)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun updateSystemPipParams(isVideoActive: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                val builder = android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(android.util.Rational(16, 9))
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    builder.setAutoEnterEnabled(isVideoActive)
                    builder.setSeamlessResizeEnabled(true)
                }
                setPictureInPictureParams(builder.build())
            } catch (_: Exception) {
            }
        }
    }

    private fun enterSystemPipSafely() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                val params = android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(android.util.Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (_: Exception) {
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (shouldAutoEnterPipOnLeave) {
            enterSystemPipSafely()
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
            } catch (_: Throwable) {
            }
        } else {
            viewModel.loadMedia()
        }
    }
}

@Composable
private fun BoxScope.HybridFloatingMiniVideoWindow(
    video: MediaItem,
    initialPositionMs: Long,
    accentColor: Color,
    onProgressUpdate: (Long) -> Unit,
    onExpandToFullScreen: (Long) -> Unit,
    onEnterSystemPip: (Long) -> Unit,
    onCloseMiniPlayer: (Long) -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var isLargeMiniSize by remember { mutableStateOf(false) }
    var isMiniPlaying by remember { mutableStateOf(true) }
    var currentPosMs by remember(video.path) { mutableStateOf(initialPositionMs) }
    var miniVideoViewRef by remember { mutableStateOf<android.widget.VideoView?>(null) }

    LaunchedEffect(miniVideoViewRef, isMiniPlaying) {
        while (true) {
            try {
                val vv = miniVideoViewRef
                if (vv != null && vv.isPlaying) {
                    val pos = vv.currentPosition.toLong()
                    if (pos > 0L) {
                        currentPosMs = pos
                        onProgressUpdate(pos)
                    }
                }
            } catch (_: Exception) {
            }
            kotlinx.coroutines.delay(500L)
        }
    }

    val boxWidth = if (isLargeMiniSize) 286.dp else 224.dp
    val boxHeight = if (isLargeMiniSize) 168.dp else 134.dp

    Surface(
        modifier = Modifier
            .align(androidx.compose.ui.Alignment.BottomEnd)
            .padding(end = 14.dp, bottom = 94.dp)
            .offset {
                androidx.compose.ui.unit.IntOffset(
                    offsetX.toInt(),
                    offsetY.toInt()
                )
            }
            .size(width = boxWidth, height = boxHeight)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        color = Color.Black,
        border = androidx.compose.foundation.BorderStroke(1.8.dp, accentColor.copy(alpha = 0.85f)),
        shadowElevation = 20.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx ->
                    android.widget.VideoView(ctx).apply {
                        miniVideoViewRef = this
                        try {
                            setVideoPath(video.path)
                            setOnPreparedListener { mp ->
                                try {
                                    mp.setVideoScalingMode(android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                                    val vw = mp.videoWidth.coerceAtLeast(1)
                                    val vh = mp.videoHeight.coerceAtLeast(1)
                                    if (vw > 960 || vh > 960) {
                                        val ratio = minOf(960f / vw, 960f / vh)
                                        holder.setFixedSize(((vw * ratio).toInt() / 2) * 2, ((vh * ratio).toInt() / 2) * 2)
                                    }
                                } catch (_: Exception) {
                                }
                                if (initialPositionMs > 0L) {
                                    mp.seekTo(initialPositionMs.toInt())
                                }
                                mp.start()
                                isMiniPlaying = true
                            }
                            setOnErrorListener { _, _, _ -> true }
                        } catch (_: Exception) {
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Top Compact Bar: Title + Resize + System PiP + Expand + Close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(androidx.compose.ui.Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.58f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = video.cleanTitle,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = { isLargeMiniSize = !isLargeMiniSize },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AspectRatio,
                        contentDescription = "Resize Mini Window",
                        tint = HybridCyan,
                        modifier = Modifier.size(14.dp)
                    )
                }

                IconButton(
                    onClick = { onEnterSystemPip(currentPosMs) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureInPictureAlt,
                        contentDescription = "System PiP",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }

                IconButton(
                    onClick = { onExpandToFullScreen(currentPosMs) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInFull,
                        contentDescription = "Full Screen",
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                }

                IconButton(
                    onClick = { onCloseMiniPlayer(currentPosMs) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Mini Player",
                        tint = HybridCoralRed,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // Center Play/Pause & -10s/+10s Mini Controls
            Row(
                modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        miniVideoViewRef?.let { vv ->
                            val next = (vv.currentPosition - 10_000).coerceAtLeast(0)
                            vv.seekTo(next)
                            currentPosMs = next.toLong()
                        }
                    },
                    modifier = Modifier
                        .size(30.dp)
                        .background(Color.Black.copy(alpha = 0.55f), androidx.compose.foundation.shape.CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "-10s",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = {
                        miniVideoViewRef?.let { vv ->
                            if (vv.isPlaying) {
                                vv.pause()
                                isMiniPlaying = false
                            } else {
                                vv.start()
                                isMiniPlaying = true
                            }
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(accentColor.copy(alpha = 0.9f), androidx.compose.foundation.shape.CircleShape)
                ) {
                    Icon(
                        imageVector = if (isMiniPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = {
                        miniVideoViewRef?.let { vv ->
                            val dur = vv.duration.coerceAtLeast(1000)
                            val next = (vv.currentPosition + 10_000).coerceAtMost(dur)
                            vv.seekTo(next)
                            currentPosMs = next.toLong()
                        }
                    },
                    modifier = Modifier
                        .size(30.dp)
                        .background(Color.Black.copy(alpha = 0.55f), androidx.compose.foundation.shape.CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "+10s",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}


