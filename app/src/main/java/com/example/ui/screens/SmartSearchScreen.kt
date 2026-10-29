package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaItem
import com.example.ui.theme.*
import com.example.viewmodel.PlayerViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartSearchScreen(
    viewModel: PlayerViewModel,
    onPlayVideo: (MediaItem) -> Unit,
    onPlayMusic: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val videos by viewModel.videoItems.collectAsState()
    val music by viewModel.musicItems.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()

    var query by remember { mutableStateOf("") }
    var activeSegment by remember { mutableStateOf("Videos") } // "Videos" | "Audio"

    val matchedVideos = remember(videos, query) {
        if (query.isBlank()) videos else videos.filter {
            it.cleanTitle.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.folderName.contains(query, ignoreCase = true)
        }
    }

    val matchedAudio = remember(music, query) {
        if (query.isBlank()) music else music.filter {
            it.cleanTitle.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.albumName.contains(query, ignoreCase = true)
        }
    }

    // Automatic Tab Switch: If Videos has 0 results and Audio has matches, auto-switch to Audio tab
    LaunchedEffect(query, matchedVideos.size, matchedAudio.size) {
        if (query.isNotBlank()) {
            if (matchedVideos.isEmpty() && matchedAudio.isNotEmpty()) {
                activeSegment = "Audio"
            } else if (matchedAudio.isEmpty() && matchedVideos.isNotEmpty()) {
                activeSegment = "Videos"
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // --- TOP SEARCH BAR WITH 1-TAP CLEAR BUTTON ---
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = {
                Text(
                    text = "Search 8K videos, songs, folders...",
                    color = HybridTextSecondary,
                    fontSize = 14.sp
                )
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = HybridEmerald)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear Search", tint = Color.White)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = HybridCardBg,
                unfocusedContainerColor = HybridCardBg,
                focusedBorderColor = HybridEmerald,
                unfocusedBorderColor = HybridCardBorder,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // --- RECENT SEARCHES HISTORY (Shown when search box is empty) ---
        if (query.isBlank() && recentSearches.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Searches",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { viewModel.clearRecentSearches() }) {
                    Text("Clear All", color = HybridCoralRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                recentSearches.forEach { keyword ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(HybridCardBg)
                            .border(1.dp, HybridCardBorder, RoundedCornerShape(18.dp))
                            .clickable { query = keyword }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = HybridEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(keyword, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // --- SEGMENTED TABS: "Videos (Count)" and "Music / Audio (Count)" ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val videoSelected = activeSegment == "Videos"
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (videoSelected) HybridEmerald else HybridCardBg)
                    .border(1.dp, if (videoSelected) HybridEmerald else HybridCardBorder, RoundedCornerShape(12.dp))
                    .clickable { activeSegment = "Videos" }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Videos (${matchedVideos.size})",
                    color = if (videoSelected) Color.Black else Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }

            val audioSelected = activeSegment == "Audio"
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (audioSelected) HybridEmerald else HybridCardBg)
                    .border(1.dp, if (audioSelected) HybridEmerald else HybridCardBorder, RoundedCornerShape(12.dp))
                    .clickable { activeSegment = "Audio" }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Music / Audio (${matchedAudio.size})",
                    color = if (audioSelected) Color.Black else Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- SEARCH RESULTS WITH INSTANT CHARACTER HIGHLIGHTING (#2ECC71) ---
        val activeList = if (activeSegment == "Videos") matchedVideos else matchedAudio
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(activeList, key = { it.id }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(HybridCardBg)
                        .border(1.dp, HybridCardBorder, RoundedCornerShape(14.dp))
                        .clickable {
                            if (query.isNotBlank()) {
                                viewModel.addRecentSearch(query)
                            }
                            if (item.isVideo) {
                                onPlayVideo(item)
                            } else {
                                onPlayMusic(item)
                            }
                        }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.isVideo) {
                        Box(
                            modifier = Modifier
                                .size(width = 92.dp, height = 56.dp)
                                .clip(RoundedCornerShape(10.dp))
                        ) {
                            GuaranteedVideoThumbnail(video = item, modifier = Modifier.fillMaxSize())
                        }
                    } else {
                        GuaranteedAlbumArt(
                            track = item,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = buildHighlightedText(item.cleanTitle, query),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "${item.artist} • ${item.formattedDuration} • ${item.folderName}",
                            color = HybridTextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Highlights matching characters in bright Emerald Green (#2ECC71).
 */
private fun buildHighlightedText(fullText: String, query: String): AnnotatedString {
    if (query.isBlank()) {
        return AnnotatedString(fullText, SpanStyle(color = Color.White))
    }
    return buildAnnotatedString {
        var startIndex = 0
        val lowerFull = fullText.lowercase()
        val lowerQuery = query.trim().lowercase()

        while (startIndex < fullText.length) {
            val matchIndex = lowerFull.indexOf(lowerQuery, startIndex)
            if (matchIndex == -1) {
                withStyle(SpanStyle(color = Color.White)) {
                    append(fullText.substring(startIndex))
                }
                break
            }
            if (matchIndex > startIndex) {
                withStyle(SpanStyle(color = Color.White)) {
                    append(fullText.substring(startIndex, matchIndex))
                }
            }
            withStyle(
                SpanStyle(
                    color = HybridEmerald,
                    fontWeight = FontWeight.ExtraBold
                )
            ) {
                append(fullText.substring(matchIndex, matchIndex + lowerQuery.length))
            }
            startIndex = matchIndex + lowerQuery.length
        }
    }
}
