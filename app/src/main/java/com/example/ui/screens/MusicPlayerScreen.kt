package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.components.InteractiveLyricsView
import com.example.ui.components.SoundWaveVisualizer
import com.example.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerScreen(
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit,
    accentColor: Color
) {
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val progressMs by viewModel.playbackPosition.collectAsState()
    val speed by viewModel.playbackSpeed.collectAsState()
    val activePreset by viewModel.equalizerPreset.collectAsState()
    val visualizerWaves by viewModel.visualizerFrequencies.collectAsState()

    var showLyricsTab by remember { mutableStateOf(false) }

    // Cover art animation scaling (pulsates gently if playing)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val activeScale = if (isPlaying) pulseScale else 1.0f

    // Format current time markers
    val currentSecs = progressMs / 1000
    val totalSecs = (nowPlaying?.duration ?: 1) / 1000
    val formattedCurrent = String.format("%02d:%02d", currentSecs / 60, currentSecs % 60)
    val formattedTotal = String.format("%02d:%02d", totalSecs / 60, totalSecs % 60)

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(32.dp))
                }

                Text(
                    text = "MAX Premium Player",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                // Favorite Button
                IconButton(
                    onClick = { nowPlaying?.let { viewModel.toggleFavorite(it) } }
                ) {
                    val isFavorite = viewModel.favoriteItems.collectAsState().value.any { it.id == nowPlaying?.id }
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) accentColor else Color.White
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Dual Mode Controller Tabs: Cover Art or Lyrics scrolling
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(if (!showLyricsTab) accentColor.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable { showLyricsTab = false }
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Visualizer",
                        color = if (!showLyricsTab) accentColor else Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(if (showLyricsTab) accentColor.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable { showLyricsTab = true }
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Live Lyrics",
                        color = if (showLyricsTab) accentColor else Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main display pane (Switch between album visualizer or lyrics scrolling)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (!showLyricsTab) {
                    // Visualizer mode
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Pulsating Album Cover
                        AsyncImage(
                            model = nowPlaying?.coverUrl ?: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=400&auto=format&fit=crop",
                            contentDescription = "Cover art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(240.dp)
                                .scale(activeScale)
                                .clip(RoundedCornerShape(16.dp))
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // Spectrum Wave drawing
                        SoundWaveVisualizer(
                            frequencies = visualizerWaves,
                            accentColor = accentColor,
                            isPlaying = isPlaying,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    // Scrolling Karaoke Lyrics mode
                    InteractiveLyricsView(
                        lyrics = nowPlaying?.lyrics ?: emptyList(),
                        playbackPositionMs = progressMs,
                        onLyricLineClick = { timeMs -> viewModel.seekTo(timeMs) },
                        accentColor = accentColor,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Titles
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = nowPlaying?.title ?: "No Track Selected",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = nowPlaying?.artist ?: "MAX Artist Pro",
                    color = Color.Gray,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Progress Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                val sliderMax = (nowPlaying?.duration ?: 1000).toFloat()
                Slider(
                    value = progressMs.toFloat().coerceIn(0f, sliderMax),
                    onValueChange = { pos -> viewModel.seekTo(pos.toLong()) },
                    valueRange = 0f..sliderMax,
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = formattedCurrent, color = Color.Gray, fontSize = 12.sp)
                    Text(text = formattedTotal, color = Color.Gray, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Premium Controls Panel (Equalizer shortcut + Speed control slider)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Playback speed control
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, contentDescription = "Speed", tint = accentColor, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Speed: ${String.format("%.1fx", speed)}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Increments speed onClick
                Row {
                    listOf(1.0f, 1.25f, 1.5f, 2.0f).forEach { s ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (speed == s) accentColor.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { viewModel.setPlaybackSpeed(s) }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(text = "${s}x", color = if (speed == s) accentColor else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main Media Controllers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { /* Simulated premium option */ }) {
                    Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = Color.Gray)
                }

                IconButton(onClick = { viewModel.playPrevious() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(36.dp))
                }

                FloatingActionButton(
                    onClick = { viewModel.togglePlayPause() },
                    containerColor = accentColor,
                    contentColor = Color.Black,
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(onClick = { viewModel.playNext() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(36.dp))
                }

                IconButton(onClick = { /* Simulated premium option */ }) {
                    Icon(Icons.Default.Repeat, contentDescription = "Repeat", tint = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
