package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.example.data.model.MediaItem
import com.example.ui.components.VideoPlayerView
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

enum class ActiveScreen {
    Dashboard, MusicPlayer, VideoPlayer, Equalizer, PrivateFolder, Playlists, History
}

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    // Launcher for standard storage access permissions
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            viewModel.loadMedia()
            Toast.makeText(this, "Local Media Library Synced!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Using MAX Curated Streaming Media", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Auto request media permissions based on SDK level
        checkAndRequestPermissions()

        setContent {
            val activeTheme by viewModel.activeTheme.collectAsState()
            
            // Map accent color dynamically to match selected theme
            val accentColor = remember(activeTheme) {
                when (activeTheme) {
                    "Neon Blue" -> NeonBlue
                    "Luxury Gold" -> LuxuryGold
                    "Forest Green" -> EmeraldNeon
                    else -> AmethystPrimary // "Deep Purple"
                }
            }

            MyApplicationTheme(themeName = activeTheme) {
                val coroutineScope = rememberCoroutineScope()
                val navigationStack = remember { mutableStateListOf(ActiveScreen.Dashboard) }
                val currentScreen = navigationStack.last()

                var activeVideoItem by remember { mutableStateOf<MediaItem?>(null) }
                var activeVideoProgress by remember { mutableStateOf(0L) }

                // System Back Button interception
                BackHandler(enabled = navigationStack.size > 1) {
                    if (navigationStack.last() == ActiveScreen.PrivateFolder) {
                        viewModel.lockVault() // Auto lock on back
                    }
                    navigationStack.removeAt(navigationStack.lastIndex)
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Crossfade(targetState = currentScreen, label = "screen_nav") { screen ->
                        when (screen) {
                            ActiveScreen.Dashboard -> {
                                DashboardScreen(
                                    viewModel = viewModel,
                                    onPlayMusic = { track ->
                                        viewModel.playMusic(track)
                                        navigationStack.add(ActiveScreen.MusicPlayer)
                                    },
                                    onPlayVideo = { video ->
                                        activeVideoItem = video
                                        // Retrieve last saved play progress if any
                                        activeVideoProgress = 0L
                                        coroutineScope.launch {
                                            activeVideoProgress = viewModel.getVideoProgress(video.id)
                                            navigationStack.add(ActiveScreen.VideoPlayer)
                                        }
                                    },
                                    onNavigateToEqualizer = { navigationStack.add(ActiveScreen.Equalizer) },
                                    onNavigateToPrivateFolder = { navigationStack.add(ActiveScreen.PrivateFolder) },
                                    onNavigateToPlaylists = { navigationStack.add(ActiveScreen.Playlists) },
                                    onNavigateToHistory = { navigationStack.add(ActiveScreen.History) },
                                    accentColor = accentColor
                                )
                            }
                            ActiveScreen.MusicPlayer -> {
                                MusicPlayerScreen(
                                    viewModel = viewModel,
                                    onBackClick = { navigationStack.removeAt(navigationStack.lastIndex) },
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
                                        accentColor = accentColor
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
                                    onBackClick = { navigationStack.removeAt(navigationStack.lastIndex) },
                                    accentColor = accentColor
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
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}
