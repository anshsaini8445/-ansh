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
            val simpleModeEnabled by viewModel.simpleModeEnabled.collectAsState()
            val simpleModeStyle by viewModel.simpleModeStyle.collectAsState()
            val simpleHideMascot by viewModel.simpleHideMascot.collectAsState()
            val selectedLanguage by viewModel.selectedLanguage.collectAsState()
            val isHindi = remember(selectedLanguage) {
                com.example.ui.i18n.WorldLanguageEngine.isHindiActive(selectedLanguage)
            }
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
                var showBgVideoFullScreen by remember { mutableStateOf(false) }

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
                BackHandler(enabled = showBgVideoFullScreen || navigationStack.size > 1 || selectedTab != MainBottomTab.VIDEO) {
                    if (showBgVideoFullScreen) {
                        showBgVideoFullScreen = false
                    } else if (navigationStack.size > 1) {
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

                    if (simpleModeEnabled) {
                        // Simple Mode Clean Flat Backdrop
                        val simpleBgColor = when (simpleModeStyle) {
                            "AMOLED Pitch Black", "AMOLED Pure Black" -> Color(0xFF05070A)
                            "Soft Slate", "MX Slate Blue" -> Color(0xFF0D131F)
                            else -> Color(0xFF0E1117)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(simpleBgColor)
                        )
                    } else {
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
                                                            val tabTint = if (simpleModeEnabled) {
                                                                Color(0xFF38BDF8)
                                                            } else {
                                                                when (tab) {
                                                                    MainBottomTab.VIDEO -> Color(0xFF00E676)
                                                                    MainBottomTab.MUSIC -> Color(0xFF00E5FF)
                                                                    MainBottomTab.SEARCH -> Color(0xFFFFD54F)
                                                                    MainBottomTab.ME -> Color(0xFFFF4081)
                                                                }
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
                                                                isActive = simpleModeEnabled && selected,
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
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .width(if (selected) 22.dp else 6.dp)
                                                                            .height(3.dp)
                                                                            .clip(RoundedCornerShape(50))
                                                                            .background(
                                                                                if (selected) tabTint else Color.White.copy(alpha = 0.22f)
                                                                            )
                                                                    )
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
                                            showBgVideoFullScreen = true
                                            navigationStack.removeAt(navigationStack.lastIndex)
                                        },
                                        onBackgroundAudioPlayWithPosition = { currentPosMs, currentSpeed ->
                                            activeVideoProgress = currentPosMs
                                            viewModel.playVideoInBackground(video, currentPosMs, currentSpeed)
                                            activeVideoItem = null
                                            isFloatingMiniVideoActive = false
                                            showBgVideoFullScreen = true
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

                    // --- FLOATING BACKGROUND VIDEO AUDIO DOCK + UPWARD QUEUE + 8-FEATURE MENU + FULL AUDIO SCREEN ---
                    val bgVideoPlayingItem = nowPlaying?.takeIf { it.isVideo }
                    var showBgVideoQueueSheet by remember { mutableStateOf(false) }
                    var showBgVideoMoreMenu by remember { mutableStateOf(false) }
                    var isConvertingBgVideoMp3 by remember { mutableStateOf(false) }

                    LaunchedEffect(bgVideoPlayingItem) {
                        if (bgVideoPlayingItem == null) {
                            showBgVideoQueueSheet = false
                            showBgVideoMoreMenu = false
                            showBgVideoFullScreen = false
                        }
                    }

                    if (bgVideoPlayingItem != null && currentScreen == ActiveScreen.MainTabs && !isFloatingMiniVideoActive && !isPipMicroWindow) {
                        val bgSpeed by viewModel.playbackSpeed.collectAsState()
                        val bgBoost200 by viewModel.is200BoostEnabled.collectAsState()
                        val bgShuffle by viewModel.isShuffleMode.collectAsState()
                        val bgRepeat by viewModel.repeatMode.collectAsState()
                        val bgSleepMin by viewModel.sleepTimerMinutes.collectAsState()

                        fun returnToVideoPlayerNow() {
                            val resumePos = viewModel.playbackPosition.value.coerceAtLeast(0L)
                            showBgVideoFullScreen = false
                            showBgVideoQueueSheet = false
                            showBgVideoMoreMenu = false
                            viewModel.dismissBackgroundVideoAudio()
                            activeVideoItem = bgVideoPlayingItem
                            activeVideoProgress = resumePos
                            navigationStack.add(ActiveScreen.VideoPlayer)
                        }

                        fun convertPlayingVideoToMp3Now() {
                            if (isConvertingBgVideoMp3) return
                            isConvertingBgVideoMp3 = true
                            android.widget.Toast.makeText(
                                this@MainActivity,
                                if (isHindi) "🎵 MP3 में बदला जा रहा है: ${bgVideoPlayingItem.cleanTitle}..." else "🎵 Converting to MP3: ${bgVideoPlayingItem.cleanTitle}...",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            coroutineScope.launch {
                                val res = Mp3ConverterHelper.extractAudioDetailed(
                                    context = this@MainActivity,
                                    video = bgVideoPlayingItem,
                                    formatPreset = "MP3 320kbps"
                                )
                                isConvertingBgVideoMp3 = false
                                viewModel.loadMedia()
                                android.widget.Toast.makeText(
                                    this@MainActivity,
                                    if (isHindi) "✅ MP3 तैयार हो गया: ${res.fileName} (Music सेक्शन में सेव किया गया)" else "✅ MP3 Ready: ${res.fileName} (Saved to Music Library)",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }

                        // 1. Rich 2-Row Background Video Audio Dock on Main Screen
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 12.dp, vertical = 90.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (simpleModeEnabled) {
                                        Brush.horizontalGradient(listOf(Color(0xF5151922), Color(0xF51A202C)))
                                    } else {
                                        Brush.horizontalGradient(listOf(Color(0xF50A1424), Color(0xF50D2130)))
                                    }
                                )
                                .border(
                                    1.3.dp,
                                    if (simpleModeEnabled) {
                                        Brush.horizontalGradient(listOf(Color(0xFF38BDF8), Color(0xFF64748B)))
                                    } else {
                                        Brush.horizontalGradient(listOf(HybridEmerald, HybridCyan))
                                    },
                                    RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 11.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            // Top Row: Headphone Icon + Title + Prev / Play-Pause / Next + Close (✕)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(HybridEmerald.copy(alpha = 0.22f))
                                        .border(1.dp, HybridEmerald.copy(alpha = 0.65f), CircleShape)
                                        .clickable { showBgVideoFullScreen = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Headphones,
                                        contentDescription = "Open Audio Studio",
                                        tint = HybridEmerald,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(9.dp))
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { showBgVideoFullScreen = true }
                                ) {
                                    Text(
                                        text = bgVideoPlayingItem.cleanTitle,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isHindi) "🎧 बैकग्राउंड ऑडियो • टैप कर बड़ा प्लेयर खोलें" else "🎧 Background Audio • Tap for Full Screen",
                                        color = HybridCyan,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.playPrevious() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipPrevious,
                                        contentDescription = "Previous",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.togglePlayPause() },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(HybridEmerald.copy(alpha = 0.22f))
                                ) {
                                    Icon(
                                        imageVector = if (isMusicPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = HybridEmerald,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.playNext() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Next",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                // Working Close (✕) Button — stops playback AND clears _nowPlaying so dock closes immediately!
                                IconButton(
                                    onClick = {
                                        showBgVideoQueueSheet = false
                                        showBgVideoMoreMenu = false
                                        showBgVideoFullScreen = false
                                        viewModel.dismissBackgroundVideoAudio()
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x33FF5252))
                                        .testTag("bg_video_audio_close_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Background Video Audio",
                                        tint = Color(0xFFFF8A80),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Bottom Row: 4 Shining Action Chips (1. Back to Video, 2. Convert to MP3, 3. Playing Queue, 4. 3-Dot 8 Features)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Back to Video
                                Row(
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .clip(RoundedCornerShape(50))
                                        .background(HybridEmerald.copy(alpha = 0.18f))
                                        .border(1.dp, HybridEmerald.copy(alpha = 0.65f), RoundedCornerShape(50))
                                        .clickable { returnToVideoPlayerNow() }
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OndemandVideo,
                                        contentDescription = "Back to Video",
                                        tint = HybridEmerald,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isHindi) "वापस वीडियो" else "Back Video",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                }

                                // 2. Convert to MP3
                                Row(
                                    modifier = Modifier
                                        .weight(1.15f)
                                        .clip(RoundedCornerShape(50))
                                        .background(HybridCyan.copy(alpha = 0.18f))
                                        .border(1.dp, HybridCyan.copy(alpha = 0.65f), RoundedCornerShape(50))
                                        .clickable { convertPlayingVideoToMp3Now() }
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AudioFile,
                                        contentDescription = "Convert to MP3",
                                        tint = HybridCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isConvertingBgVideoMp3) "MP3..." else if (isHindi) "MP3 बनाएं" else "To MP3",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                }

                                // 3. Upward Playing Queue
                                Row(
                                    modifier = Modifier
                                        .weight(0.95f)
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0x26FFD54F))
                                        .border(1.dp, Color(0xFFFFD54F).copy(alpha = 0.65f), RoundedCornerShape(50))
                                        .clickable { showBgVideoQueueSheet = true }
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QueueMusic,
                                        contentDescription = "Playing Queue",
                                        tint = Color(0xFFFFD54F),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isHindi) "क्यू (${videos.size})" else "Queue",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                }

                                // 4. 3-Dot (8 Features Menu)
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0x26FF4081))
                                        .border(1.dp, Color(0xFFFF80AB).copy(alpha = 0.65f), RoundedCornerShape(50))
                                        .clickable { showBgVideoMoreMenu = true }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "8 Features Menu",
                                        tint = Color(0xFFFF80AB),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "8+",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }

                        // 2. Dedicated Full Background Video Audio Studio Screen ("Dusri Screen")
                        if (showBgVideoFullScreen) {
                            val totalDur = bgVideoPlayingItem.duration.coerceAtLeast(1L)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFA07101C), Color(0xFA0C1B2A), Color(0xFC060B12))
                                        )
                                    )
                                    .clickable(enabled = true) {}
                                    .systemBarsPadding()
                                    .padding(horizontal = 18.dp, vertical = 12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Top Header Bar
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        IconButton(
                                            onClick = { showBgVideoFullScreen = false },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.1f))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Minimize",
                                                tint = Color.White
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = if (isHindi) "🎧 हेडफोन वीडियो ऑडियो मोड" else "🎧 Headphone Video Audio Mode",
                                                color = HybridEmerald,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "${bgVideoPlayingItem.resolution} • ${bgVideoPlayingItem.format}",
                                                color = Color.White.copy(alpha = 0.65f),
                                                fontSize = 11.sp
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            IconButton(
                                                onClick = { showBgVideoMoreMenu = true },
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0x26FF4081))
                                                    .border(1.dp, Color(0xFFFF80AB), CircleShape)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.MoreVert,
                                                    contentDescription = "8 Features",
                                                    tint = Color(0xFFFF80AB)
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    showBgVideoFullScreen = false
                                                    viewModel.dismissBackgroundVideoAudio()
                                                },
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0x33FF5252))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Close",
                                                    tint = Color(0xFFFF8A80)
                                                )
                                            }
                                        }
                                    }

                                    // Center Video Artwork + Headphone Badge
                                    Box(
                                        modifier = Modifier
                                            .size(210.dp)
                                            .clip(RoundedCornerShape(28.dp))
                                            .border(
                                                2.dp,
                                                Brush.linearGradient(listOf(HybridEmerald, HybridCyan)),
                                                RoundedCornerShape(28.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        com.example.ui.components.GuaranteedAlbumArt(
                                            mediaPath = bgVideoPlayingItem.path,
                                            title = bgVideoPlayingItem.cleanTitle,
                                            isVideo = true,
                                            cornerRadius = 28.dp,
                                            accentColor = HybridEmerald,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(10.dp)
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xD908121E))
                                                .border(1.5.dp, HybridEmerald, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Headphones,
                                                contentDescription = null,
                                                tint = HybridEmerald,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }

                                    // Title + Quick Action Buttons (Back to Video + Convert to MP3 + Queue)
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = bgVideoPlayingItem.cleanTitle,
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 18.sp,
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Button(
                                                onClick = { returnToVideoPlayerNow() },
                                                colors = ButtonDefaults.buttonColors(containerColor = HybridEmerald),
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.OndemandVideo,
                                                    contentDescription = null,
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (isHindi) "वापस वीडियो पर जाएं" else "Back to Video",
                                                    color = Color.Black,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            Button(
                                                onClick = { convertPlayingVideoToMp3Now() },
                                                colors = ButtonDefaults.buttonColors(containerColor = HybridCyan),
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AudioFile,
                                                    contentDescription = null,
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (isHindi) "MP3 में बदलें" else "Convert to MP3",
                                                    color = Color.Black,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }

                                    // Seekbar + Transport Controls + Upward Playing Queue Button
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        BgVideoSeekProgressBar(
                                            viewModel = viewModel,
                                            totalDur = totalDur
                                        )

                                        // Transport Row: Shuffle | Prev | -10s | Play/Pause | +10s | Next | Queue
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(onClick = { viewModel.toggleShuffleMode() }) {
                                                Icon(
                                                    imageVector = Icons.Default.Shuffle,
                                                    contentDescription = "Shuffle",
                                                    tint = if (bgShuffle) HybridEmerald else Color.White.copy(alpha = 0.6f)
                                                )
                                            }
                                            IconButton(onClick = { viewModel.playPrevious() }) {
                                                Icon(
                                                    imageVector = Icons.Default.SkipPrevious,
                                                    contentDescription = "Previous",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(30.dp)
                                                )
                                            }
                                            IconButton(onClick = { viewModel.onNotificationRewind10s() }) {
                                                Icon(
                                                    imageVector = Icons.Default.Replay10,
                                                    contentDescription = "-10s",
                                                    tint = HybridCyan,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        Brush.linearGradient(listOf(HybridEmerald, HybridCyan))
                                                    )
                                                    .clickable { viewModel.togglePlayPause() },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (isMusicPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = "Play/Pause",
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(34.dp)
                                                )
                                            }
                                            IconButton(onClick = { viewModel.onNotificationFastForward10s() }) {
                                                Icon(
                                                    imageVector = Icons.Default.Forward10,
                                                    contentDescription = "+10s",
                                                    tint = HybridCyan,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                            IconButton(onClick = { viewModel.playNext() }) {
                                                Icon(
                                                    imageVector = Icons.Default.SkipNext,
                                                    contentDescription = "Next",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(30.dp)
                                                )
                                            }
                                            IconButton(onClick = { showBgVideoQueueSheet = true }) {
                                                Icon(
                                                    imageVector = Icons.Default.QueueMusic,
                                                    contentDescription = "Playing Queue",
                                                    tint = Color(0xFFFFD54F),
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                        }

                                        // Bottom Upward Queue Pull Bar
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color.White.copy(alpha = 0.08f))
                                                .border(1.dp, Color(0xFFFFD54F).copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                                                .clickable { showBgVideoQueueSheet = true }
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.QueueMusic,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFFD54F),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = if (isHindi) "ऊपर की तरफ प्लेइंग क्यू खोलें (${videos.size} वीडियो)" else "Open Upward Playing Queue (${videos.size} Videos)",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowUp,
                                                contentDescription = null,
                                                tint = Color(0xFFFFD54F)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Upward-Sliding Playing Queue Sheet
                        if (showBgVideoQueueSheet) {
                            AlertDialog(
                                onDismissRequest = { showBgVideoQueueSheet = false },
                                containerColor = Color(0xFF0B1522),
                                title = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (isHindi) "🎶 वीडियो ऑडियो प्लेइंग क्यू (${videos.size})" else "🎶 Video Audio Playing Queue (${videos.size})",
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 16.sp
                                        )
                                        IconButton(onClick = { viewModel.toggleShuffleMode() }) {
                                            Icon(
                                                imageVector = Icons.Default.Shuffle,
                                                contentDescription = "Shuffle Queue",
                                                tint = if (bgShuffle) HybridEmerald else Color.White
                                            )
                                        }
                                    }
                                },
                                text = {
                                    androidx.compose.foundation.lazy.LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 360.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(videos.size) { idx ->
                                            val vid = videos[idx]
                                            val isCurrent = vid.id == bgVideoPlayingItem.id
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(
                                                        if (isCurrent) HybridEmerald.copy(alpha = 0.22f)
                                                        else Color.White.copy(alpha = 0.06f)
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (isCurrent) HybridEmerald else Color.White.copy(alpha = 0.12f),
                                                        RoundedCornerShape(12.dp)
                                                    )
                                                    .clickable {
                                                        viewModel.playVideoInBackground(vid, 0L, bgSpeed)
                                                        showBgVideoQueueSheet = false
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                com.example.ui.components.GuaranteedAlbumArt(
                                                    mediaPath = vid.path,
                                                    title = vid.cleanTitle,
                                                    isVideo = true,
                                                    cornerRadius = 8.dp,
                                                    accentColor = HybridEmerald,
                                                    modifier = Modifier.size(42.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = vid.cleanTitle,
                                                        color = if (isCurrent) HybridEmerald else Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "${vid.resolution} • ${vid.formattedSize}",
                                                        color = Color.White.copy(alpha = 0.65f),
                                                        fontSize = 11.sp
                                                    )
                                                }
                                                if (isCurrent) {
                                                    Icon(
                                                        imageVector = Icons.Default.GraphicEq,
                                                        contentDescription = null,
                                                        tint = HybridEmerald
                                                    )
                                                }
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { showBgVideoQueueSheet = false }) {
                                        Text(
                                            text = if (isHindi) "बंद करें" else "Close",
                                            color = HybridEmerald,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            )
                        }

                        // 4. 3-Dot Menu with 8 Colourful Working Features for Background Video Audio
                        if (showBgVideoMoreMenu) {
                            AlertDialog(
                                onDismissRequest = { showBgVideoMoreMenu = false },
                                containerColor = Color(0xFF0B1522),
                                title = {
                                    Text(
                                        text = if (isHindi) "⚡ हेडफोन ऑडियो 8 प्रो फीचर्स" else "⚡ Headphone Audio 8 Pro Features",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp
                                    )
                                },
                                text = {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(7.dp)
                                    ) {
                                        val actions = listOf(
                                            Triple(
                                                if (isHindi) "1. 🎬 वापस वीडियो स्क्रीन पर जाएं" else "1. 🎬 Return to Video Player",
                                                HybridEmerald
                                            ) {
                                                showBgVideoMoreMenu = false
                                                returnToVideoPlayerNow()
                                            },
                                            Triple(
                                                if (isHindi) "2. 🎵 वीडियो को MP3 ऑडियो में बदलें" else "2. 🎵 Convert Video to MP3 Audio",
                                                HybridCyan
                                            ) {
                                                showBgVideoMoreMenu = false
                                                convertPlayingVideoToMp3Now()
                                            },
                                            Triple(
                                                if (isHindi) "3. ⚡ प्लेबैक स्पीड बदलें (${String.format("%.2fx", bgSpeed)})" else "3. ⚡ Playback Speed (${String.format("%.2fx", bgSpeed)})",
                                                Color(0xFFFFD54F)
                                            ) {
                                                val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f)
                                                val nextIdx = (speeds.indexOfFirst { kotlin.math.abs(it - bgSpeed) < 0.05f } + 1) % speeds.size
                                                viewModel.setPlaybackSpeed(speeds[nextIdx])
                                            },
                                            Triple(
                                                if (isHindi) "4. 🔊 200% सुपर बूस्ट (${if (bgBoost200) "चालू" else "बंद"})" else "4. 🔊 200% Super Volume Boost (${if (bgBoost200) "ON" else "OFF"})",
                                                Color(0xFFFF80AB)
                                            ) {
                                                viewModel.toggle200VolumeBoost()
                                            },
                                            Triple(
                                                if (isHindi) "5. 🎚️ 3D इक्वलाइज़र स्टूडियो खोलें" else "5. 🎚️ Open 3D Equalizer Studio",
                                                Color(0xFFB388FF)
                                            ) {
                                                showBgVideoMoreMenu = false
                                                showBgVideoFullScreen = false
                                                navigationStack.add(ActiveScreen.Equalizer)
                                            },
                                            Triple(
                                                if (isHindi) "6. ⏱️ स्लीप टाइमर (${if (bgSleepMin > 0) "${bgSleepMin}m चालू" else "बंद"})" else "6. ⏱️ Sleep Timer (${if (bgSleepMin > 0) "${bgSleepMin}m ON" else "OFF"})",
                                                Color(0xFF40C4FF)
                                            ) {
                                                val nextMin = when (bgSleepMin) {
                                                    0 -> 15
                                                    15 -> 30
                                                    30 -> 60
                                                    else -> 0
                                                }
                                                viewModel.setSleepTimer(nextMin)
                                            },
                                            Triple(
                                                if (isHindi) "7. 🔁 रिपीट / शफल मोड (${if (bgRepeat == 2) "Repeat 1" else if (bgRepeat == 1) "Repeat All" else "Order"})" else "7. 🔁 Repeat / Loop Mode (${if (bgRepeat == 2) "Repeat 1" else if (bgRepeat == 1) "Repeat All" else "Order"})",
                                                Color(0xFF69F0AE)
                                            ) {
                                                viewModel.cycleRepeatMode()
                                            },
                                            Triple(
                                                if (isHindi) "8. 🚀 हाइब्रिड शेयर (Wi-Fi Direct)" else "8. 🚀 Hybrid Share File",
                                                Color(0xFFFFAB40)
                                            ) {
                                                showBgVideoMoreMenu = false
                                                showBgVideoFullScreen = false
                                                preselectedShareItem = bgVideoPlayingItem
                                                navigationStack.add(ActiveScreen.TurboShare)
                                            }
                                        )

                                        actions.forEach { (label, tint, action) ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(tint.copy(alpha = 0.14f))
                                                    .border(1.dp, tint.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                                                    .clickable { action() }
                                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = label,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.ChevronRight,
                                                    contentDescription = null,
                                                    tint = tint,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { showBgVideoMoreMenu = false }) {
                                        Text(
                                            text = if (isHindi) "बंद करें" else "Close",
                                            color = HybridEmerald,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            )
                        }
                    }

                    // --- REALISTIC 3D ANIMATED COMPANION OVERLAY ---
                    // Respects `simpleHideMascot` in Simple Mode and stays raised above any bottom mini-player
                    val hideMascotInSimple = simpleModeEnabled && simpleHideMascot
                    if ((currentScreen == ActiveScreen.MainTabs || currentScreen == ActiveScreen.MusicPlayer) && !isPipMicroWindow && !hideMascotInSimple) {
                        RealisticCompanionOverlay(
                            enabled = companionEnabled && !hideMascotInSimple,
                            breedName = companionBreed,
                            isMusicPlaying = isMusicPlaying,
                            nowPlayingTitle = nowPlaying?.cleanTitle ?: "",
                            penguinsOnlyMode = penguinsOnlyMode,
                            mascotSizeDp = companionSizeDp.dp,
                            bottomClearanceDp = if (nowPlaying != null) 204.dp else 118.dp,
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

@Composable
private fun BgVideoSeekProgressBar(
    viewModel: PlayerViewModel,
    totalDur: Long
) {
    val bgPositionMs by viewModel.playbackPosition.collectAsState()
    Slider(
        value = (bgPositionMs.toFloat() / totalDur.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f),
        onValueChange = { frac ->
            viewModel.seekTo((frac * totalDur).toLong())
        },
        colors = SliderDefaults.colors(
            thumbColor = HybridEmerald,
            activeTrackColor = HybridEmerald,
            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
        )
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val curSec = (bgPositionMs / 1000L).coerceAtLeast(0L)
        val totSec = (totalDur / 1000L).coerceAtLeast(0L)
        Text(
            text = String.format("%02d:%02d", curSec / 60, curSec % 60),
            color = HybridEmerald,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
        Text(
            text = String.format("%02d:%02d", totSec / 60, totSec % 60),
            color = Color.White.copy(alpha = 0.7f),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}


