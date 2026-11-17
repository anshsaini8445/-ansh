package com.example.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaItem
import com.example.helpers.WifiDirectTransferEngine
import com.example.ui.components.GuaranteedAlbumArt
import com.example.ui.components.LiquidGlassBox
import com.example.ui.menus.MediaSevenActionDialog
import com.example.ui.theme.*
import com.example.viewmodel.PlayerViewModel

private enum class SearchCategoryFilter {
    ALL, VIDEOS, AUDIO
}

/**
 * Thumb-Friendly Bottom Search Screen for Videos & Audio:
 * - Search Results scroll cleanly in the upper area.
 * - The Search Input Box and All / Videos / Audio category selector tabs are docked at the
 *   BOTTOM of the screen (right above the navigation bar) for effortless one-handed reach!
 */
@Composable
fun SmartSearchScreen(
    viewModel: PlayerViewModel,
    onPlayVideo: (MediaItem) -> Unit,
    onPlayMusic: (MediaItem) -> Unit = { viewModel.playMusic(it) },
    onOpenMusicPlayer: () -> Unit = {},
    onOpenHybridShare: ((MediaItem) -> Unit)? = null
) {
    val context = LocalContext.current
    val allItems by viewModel.allMediaItems.collectAsState()
    val videoItems by viewModel.videoItems.collectAsState()
    val musicItems by viewModel.musicItems.collectAsState()
    val favoriteItems by viewModel.favoriteItems.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()

    val isHindi = remember(selectedLanguage) {
        com.example.ui.i18n.WorldLanguageEngine.isHindiActive(selectedLanguage)
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = configuration.screenWidthDp >= 600
    val gridColumns = if (isTablet || isLandscape) 2 else 1

    val focusManager = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf(SearchCategoryFilter.ALL) }
    var sevenMenuTargetItem by remember { mutableStateOf<MediaItem?>(null) }

    val basePool = remember(categoryFilter, allItems, videoItems, musicItems) {
        when (categoryFilter) {
            SearchCategoryFilter.ALL -> allItems
            SearchCategoryFilter.VIDEOS -> videoItems
            SearchCategoryFilter.AUDIO -> musicItems
        }
    }

    val results = remember(query, basePool) {
        val q = query.trim().lowercase()
        if (q.isBlank()) {
            basePool
        } else {
            val tokens = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
            basePool
                .mapNotNull { item ->
                    val titleLower = item.cleanTitle.lowercase()
                    val rawLower = item.title.lowercase()
                    val artistLower = item.artist.lowercase()
                    val folderLower = item.folderName.lowercase()
                    val formatLower = item.format.lowercase()

                    var score = 0
                    if (titleLower == q) score += 120
                    if (titleLower.startsWith(q)) score += 80
                    if (titleLower.contains(q)) score += 50
                    if (artistLower.contains(q)) score += 35
                    if (folderLower.contains(q)) score += 30
                    if (formatLower == q) score += 45

                    val matchedAllTokens = tokens.all { tok ->
                        titleLower.contains(tok) ||
                            rawLower.contains(tok) ||
                            artistLower.contains(tok) ||
                            folderLower.contains(tok) ||
                            formatLower.contains(tok)
                    }
                    if (matchedAllTokens) score += 40

                    if (score > 0) item to score else null
                }
                .sortedByDescending { it.second }
                .map { it.first }
        }
    }

    val displayResults = remember(query, results) {
        if (query.isBlank()) results.take(36) else results
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = if (isTablet) 22.dp else 14.dp)
            .padding(top = 8.dp, bottom = 6.dp)
    ) {
        // 1. Upper Scrollable Results Area (Fills all space above the bottom search dock)
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            if (query.isBlank() && recentSearches.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isHindi) "हाल की खोजें" else "Recent Searches",
                                color = HybridTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isHindi) "हटाएं" else "Clear",
                                color = HybridCoralRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { viewModel.clearRecentSearches() }
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            recentSearches.forEach { term ->
                                AssistChip(
                                    onClick = { query = term },
                                    label = { Text(term, color = Color.White, fontSize = 12.sp) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.History,
                                            contentDescription = null,
                                            tint = HybridCyan,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = if (query.isBlank()) {
                        when (categoryFilter) {
                            SearchCategoryFilter.VIDEOS -> if (isHindi) "हाल के वीडियो (${displayResults.size}/${results.size})" else "Recent Videos (${displayResults.size}/${results.size})"
                            SearchCategoryFilter.AUDIO -> if (isHindi) "हाल के ऑडियो (${displayResults.size}/${results.size})" else "Recent Audio (${displayResults.size}/${results.size})"
                            SearchCategoryFilter.ALL -> if (isHindi) "हाल की मीडिया (${displayResults.size}/${results.size})" else "Recent Media (${displayResults.size}/${results.size})"
                        }
                    } else {
                        if (isHindi) "${results.size} परिणाम मिले" else "${results.size} Results Found"
                    },
                    color = HybridEmerald,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            items(displayResults, key = { it.id }) { item ->
                val accent = if (item.isVideo) HybridCyan else HybridEmerald
                LiquidGlassBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_result_${item.id}"),
                    cornerRadius = 16.dp,
                    tintColor = accent,
                    glassAlpha = 0.16f,
                    onClick = {
                        if (query.isNotBlank()) {
                            viewModel.addRecentSearch(query)
                        }
                        focusManager.clearFocus()
                        if (item.isVideo) {
                            onPlayVideo(item)
                        } else {
                            onPlayMusic(item)
                            onOpenMusicPlayer()
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GuaranteedAlbumArt(
                            mediaPath = item.path,
                            title = item.cleanTitle,
                            isVideo = item.isVideo,
                            modifier = Modifier.size(48.dp),
                            cornerRadius = 12.dp,
                            accentColor = accent
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.cleanTitle,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${if (item.isVideo) (if (isHindi) "वीडियो" else "Video") else (if (isHindi) "ऑडियो" else "Audio")} • ${item.folderName} • ${item.formattedDuration}",
                                color = HybridTextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { sevenMenuTargetItem = item },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = HybridTextSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. BOTTOM SEARCH DOCK: Video / Audio Filter Tabs + Search Input Bar right at the bottom!
        LiquidGlassBox(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
            tintColor = HybridCyan,
            glassAlpha = 0.28f
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomSearchFilterTab(
                        modifier = Modifier.weight(1f),
                        label = if (isHindi) "सभी (${allItems.size})" else "All (${allItems.size})",
                        selected = categoryFilter == SearchCategoryFilter.ALL,
                        accent = HybridEmerald,
                        testTag = "search_filter_all",
                        onClick = { categoryFilter = SearchCategoryFilter.ALL }
                    )
                    BottomSearchFilterTab(
                        modifier = Modifier.weight(1f),
                        label = if (isHindi) "वीडियो (${videoItems.size})" else "Videos (${videoItems.size})",
                        selected = categoryFilter == SearchCategoryFilter.VIDEOS,
                        accent = HybridCyan,
                        testTag = "search_filter_videos",
                        onClick = { categoryFilter = SearchCategoryFilter.VIDEOS }
                    )
                    BottomSearchFilterTab(
                        modifier = Modifier.weight(1f),
                        label = if (isHindi) "ऑडियो (${musicItems.size})" else "Audio (${musicItems.size})",
                        selected = categoryFilter == SearchCategoryFilter.AUDIO,
                        accent = HybridGold,
                        testTag = "search_filter_audio",
                        onClick = { categoryFilter = SearchCategoryFilter.AUDIO }
                    )
                }

                val voiceSuggestions = remember(basePool, recentSearches) {
                    (recentSearches + basePool.take(10).map { it.cleanTitle.take(22) })
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .take(8)
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            text = when (categoryFilter) {
                                SearchCategoryFilter.VIDEOS -> if (isHindi) "बोलकर या लिखकर वीडियो खोजें..." else "Type or speak to search videos..."
                                SearchCategoryFilter.AUDIO -> if (isHindi) "बोलकर या लिखकर गाने खोजें..." else "Type or speak to search songs..."
                                SearchCategoryFilter.ALL -> if (isHindi) "बोलकर या लिखकर वीडियो/गाने खोजें..." else "Type or speak to search media..."
                            },
                            color = HybridTextSecondary,
                            fontSize = 12.5.sp
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = HybridEmerald)
                    },
                    trailingIcon = {
                        com.example.helpers.VoiceAndTtsSearchBarActions(
                            currentQuery = query,
                            onQueryChange = { query = it },
                            onSearchConfirmed = { spoken ->
                                viewModel.addRecentSearch(spoken)
                                focusManager.clearFocus()
                            },
                            isHindi = isHindi,
                            matchingCount = results.size,
                            topMatchTitle = results.firstOrNull()?.cleanTitle,
                            suggestedVoiceTerms = voiceSuggestions,
                            accentColor = HybridCyan
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            viewModel.addRecentSearch(query)
                            focusManager.clearFocus()
                        }
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("smart_search_input")
                )
            }
        }
    }

    sevenMenuTargetItem?.let { item ->
        val isFav = favoriteItems.any { it.id == item.id }
        MediaSevenActionDialog(
            item = item,
            isFavorite = isFav,
            isHindi = isHindi,
            onDismiss = { sevenMenuTargetItem = null },
            onHybridShare = {
                if (onOpenHybridShare != null) {
                    onOpenHybridShare(it)
                } else {
                    WifiDirectTransferEngine.shareRealFilesViaSystem(context, listOf(it))
                }
            },
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
            onPlayBackgroundAudio = if (item.isVideo) {
                { videoTrack ->
                    viewModel.playVideoInBackground(videoTrack, 0L)
                }
            } else null,
            onRenameCompleted = {
                viewModel.loadMedia()
            }
        )
    }
}

@Composable
private fun BottomSearchFilterTab(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    accent: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) {
                    Brush.horizontalGradient(listOf(accent.copy(alpha = 0.34f), accent.copy(alpha = 0.18f)))
                } else {
                    Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.04f)))
                }
            )
            .border(
                width = if (selected) 1.4.dp else 1.dp,
                color = if (selected) accent else Color.White.copy(alpha = 0.16f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) accent else Color.White,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
