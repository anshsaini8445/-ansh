package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SoundWaveVisualizer(
    frequencies: List<Float>,
    accentColor: Color,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true
) {
    // Generate slight idle fluctuation if playing
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer")
    val idleFluctuation by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_wave"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .padding(vertical = 8.dp)
    ) {
        val barCount = frequencies.size
        val padding = 8.dp.toPx()
        val totalPadding = padding * (barCount - 1)
        val barWidth = (size.width - totalPadding) / barCount
        val maxBarHeight = size.height

        for (i in 0 until barCount) {
            val freq = frequencies[i]
            val animatedHeight = if (isPlaying) {
                (freq * maxBarHeight * idleFluctuation).coerceIn(10.dp.toPx(), maxBarHeight)
            } else {
                12.dp.toPx() // Rest state
            }

            val x = i * (barWidth + padding)
            val y = (size.height - animatedHeight) / 2 // Center vertically

            val brush = Brush.verticalGradient(
                colors = listOf(
                    accentColor,
                    accentColor.copy(alpha = 0.5f)
                ),
                startY = y,
                endY = y + animatedHeight
            )

            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, y),
                size = Size(barWidth, animatedHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

@Composable
fun InteractiveLyricsView(
    lyrics: List<com.example.data.model.LyricLine>,
    playbackPositionMs: Long,
    onLyricLineClick: (Long) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    if (lyrics.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No Premium Lyrics Found",
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Find current line
    val currentLineIndex = remember(lyrics, playbackPositionMs) {
        val index = lyrics.indexOfLast { it.timestampMs <= playbackPositionMs }
        if (index == -1) 0 else index
    }

    // Scroll automatically to center current line
    LaunchedEffect(currentLineIndex) {
        if (lyrics.isNotEmpty()) {
            coroutineScope.launch {
                // Smoothly center the item
                val scrollTarget = (currentLineIndex - 2).coerceAtLeast(0)
                listState.animateScrollToItem(scrollTarget)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 40.dp)
    ) {
        itemsIndexed(lyrics) { idx, line ->
            val isActive = idx == currentLineIndex
            
            val textColor by animateColorAsState(
                targetValue = if (isActive) accentColor else Color.White.copy(alpha = 0.4f),
                animationSpec = tween(300),
                label = "lyric_color"
            )

            val fontSize = if (isActive) 20.sp else 16.sp

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onLyricLineClick(line.timestampMs) }
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = line.text,
                    color = textColor,
                    fontSize = fontSize,
                    style = if (isActive) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    lineHeight = 26.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
