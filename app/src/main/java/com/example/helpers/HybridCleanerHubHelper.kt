package com.example.helpers

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaItem
import com.example.data.model.RecycledMediaItem
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class StorageStatsInfo(
    val usedBytes: Long,
    val totalBytes: Long,
    val cleanableBytes: Long
) {
    val usedGbText: String
        get() = String.format(Locale.US, "%.1fGB", usedBytes / (1024.0 * 1024.0 * 1024.0))

    val totalGbText: String
        get() = String.format(Locale.US, "%.1fGB", totalBytes / (1024.0 * 1024.0 * 1024.0))

    val cleanableBadgeText: String
        get() {
            val gb = cleanableBytes / (1024.0 * 1024.0 * 1024.0)
            return if (gb >= 1.0) {
                String.format(Locale.US, "%.2fGB", gb)
            } else {
                val mb = (cleanableBytes / (1024.0 * 1024.0)).coerceAtLeast(0.0)
                if (mb < 1.0 && cleanableBytes > 0L) {
                    val kb = (cleanableBytes / 1024.0).coerceAtLeast(1.0)
                    String.format(Locale.US, "%.0fKB", kb)
                } else {
                    String.format(Locale.US, "%.0fMB", mb)
                }
            }
        }

    val usedFraction: Float
        get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0.05f, 0.98f) else 0.0f
}

object HybridCleanerHubHelper {

    fun findDuplicateVideos(videos: List<MediaItem>): List<MediaItem> {
        if (videos.size < 2) return emptyList()
        val duplicates = mutableListOf<MediaItem>()
        val seenIds = mutableSetOf<String>()

        // 1. Group by identical exact file size (> 64KB) & nearly identical duration
        videos.filter { it.size > 65_536L }.groupBy { it.size }.forEach { (_, group) ->
            if (group.size > 1) {
                val sorted = group.sortedBy { it.dateAdded }
                val firstDur = sorted.first().duration
                val matched = sorted.filter { item ->
                    firstDur <= 0L || item.duration <= 0L || abs(item.duration - firstDur) <= 1500L
                }
                if (matched.size > 1) {
                    matched.drop(1).forEach { dup ->
                        if (seenIds.add(dup.id)) duplicates.add(dup)
                    }
                }
            }
        }

        // 2. Also match normalized title + approximate size (copies like "video (1).mp4")
        val normalizedGroups = videos.groupBy {
            it.cleanTitle.lowercase(Locale.ROOT)
                .replace(Regex("\\s*\\(\\d+\\)$"), "")
                .replace(Regex("_copy\\d*$"), "")
                .trim() to (it.duration / 1000L)
        }
        normalizedGroups.forEach { (key, group) ->
            if (key.first.isNotBlank() && group.size > 1) {
                val sorted = group.sortedBy { it.dateAdded }
                sorted.drop(1).forEach { dup ->
                    if (seenIds.add(dup.id)) duplicates.add(dup)
                }
            }
        }
        return duplicates
    }

    fun getRealCacheBytes(context: Context): Long {
        return getDirSize(context.cacheDir) +
            getDirSize(context.externalCacheDir) +
            getDirSize(context.codeCacheDir)
    }

    /**
     * Reads real device storage stats via StatFs blockCountLong and availableBlocksLong.
     */
    fun getRealStorageStats(context: Context, videos: List<MediaItem>): StorageStatsInfo {
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalBytes = totalBlocks * blockSize
            val freeBytes = availableBlocks * blockSize
            val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)

            val cacheSize = getRealCacheBytes(context)
            val duplicateBytes = findDuplicateVideos(videos).sumOf { it.size.coerceAtLeast(0L) }
            val cleanable = (cacheSize + duplicateBytes).coerceAtLeast(0L)

            StorageStatsInfo(
                usedBytes = usedBytes,
                totalBytes = totalBytes,
                cleanableBytes = cleanable
            )
        } catch (_: Exception) {
            StorageStatsInfo(
                usedBytes = 0L,
                totalBytes = 0L,
                cleanableBytes = getRealCacheBytes(context)
            )
        }
    }

    fun cleanAppCacheAndTemp(context: Context): Long {
        var freed = 0L
        listOfNotNull(context.cacheDir, context.externalCacheDir, context.codeCacheDir).forEach { dir ->
            freed += clearDirectoryContents(dir)
        }
        return freed.coerceAtLeast(0L)
    }

    private fun clearDirectoryContents(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var freed = 0L
        try {
            dir.listFiles()?.forEach { child ->
                if (child.isDirectory) {
                    freed += clearDirectoryContents(child)
                    child.delete()
                } else {
                    val len = child.length()
                    if (child.delete()) freed += len
                }
            }
        } catch (_: Exception) {
        }
        return freed
    }

    private fun getDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var result = 0L
        try {
            dir.listFiles()?.forEach { f ->
                result += if (f.isDirectory) getDirSize(f) else f.length()
            }
        } catch (_: Exception) {
        }
        return result
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanJunkHubScreen(
    allVideos: List<MediaItem>,
    watchedVideoIds: Set<String>,
    onMoveToTrash: (MediaItem) -> Unit,
    onPlayVideo: (MediaItem) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isCleaned by remember { mutableStateOf(false) }
    var isCleaningNow by remember { mutableStateOf(false) }
    var activeSubTool by remember { mutableStateOf<String?>(null) }
    var duplicateConfirmStep by remember { mutableStateOf(0) }
    var refreshTrigger by remember { mutableStateOf(0) }

    var stats by remember { mutableStateOf(StorageStatsInfo(0L, 0L, 0L)) }
    var cacheSizeBytes by remember { mutableLongStateOf(0L) }
    var duplicateCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(allVideos, isCleaned, refreshTrigger) {
        withContext(Dispatchers.IO) {
            val realStats = HybridCleanerHubHelper.getRealStorageStats(context, allVideos)
            val realCache = HybridCleanerHubHelper.getRealCacheBytes(context)
            val dups = HybridCleanerHubHelper.findDuplicateVideos(allVideos).size
            withContext(Dispatchers.Main) {
                stats = realStats
                cacheSizeBytes = realCache
                duplicateCount = dups
            }
        }
    }

    val animatedSweep by animateFloatAsState(
        targetValue = if (isCleaned) stats.usedFraction * 220f else stats.usedFraction * 260f,
        animationSpec = tween(900),
        label = "gauge_sweep"
    )

    val recycledItems = remember(refreshTrigger) {
        PermanentDeleteHelper.getRecycledItems(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = activeSubTool ?: "Clean Junk",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (activeSubTool != null) {
                            activeSubTool = null
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HybridDarkBg)
            )
        },
        containerColor = HybridDarkBg
    ) { innerPadding ->
        if (activeSubTool != null) {
            CleanerSubToolView(
                toolName = activeSubTool!!,
                allVideos = allVideos,
                watchedVideoIds = watchedVideoIds,
                recycledItems = recycledItems,
                duplicateConfirmStep = duplicateConfirmStep,
                onDuplicateConfirmStepChange = { duplicateConfirmStep = it },
                onMoveToTrash = { item ->
                    onMoveToTrash(item)
                    refreshTrigger++
                },
                onRestoreFromTrash = { id ->
                    PermanentDeleteHelper.restoreFromRecycleBin(context, id)
                    refreshTrigger++
                    Toast.makeText(context, "Restored to Library", Toast.LENGTH_SHORT).show()
                },
                onPermanentDelete = { item ->
                    PermanentDeleteHelper.deletePermanently(context, item)
                    refreshTrigger++
                    Toast.makeText(context, "Deleted Permanently", Toast.LENGTH_SHORT).show()
                },
                onPlayVideo = onPlayVideo,
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    // Center Radial Broom Gauge
                    Box(
                        modifier = Modifier.size(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val outerRadius = size.minDimension / 2f - 10.dp.toPx()

                            // Outer ring of 48 radial ticks
                            for (i in 0 until 48) {
                                val angleDeg = i * (360f / 48f)
                                val rad = Math.toRadians(angleDeg.toDouble())
                                val startR = outerRadius - 8.dp.toPx()
                                val start = Offset(
                                    center.x + (startR * cos(rad)).toFloat(),
                                    center.y + (startR * sin(rad)).toFloat()
                                )
                                val end = Offset(
                                    center.x + (outerRadius * cos(rad)).toFloat(),
                                    center.y + (outerRadius * sin(rad)).toFloat()
                                )
                                val isActiveTick = i < (stats.usedFraction * 48).toInt()
                                drawLine(
                                    color = if (isActiveTick) {
                                        if (isCleaned) HybridEmerald else HybridCoralRed
                                    } else Color(0x33FFFFFF),
                                    start = start,
                                    end = end,
                                    strokeWidth = 2.5.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }

                            // Inner progress arc
                            val arcInset = 24.dp.toPx()
                            drawArc(
                                color = Color(0x22FFFFFF),
                                startAngle = 140f,
                                sweepAngle = 260f,
                                useCenter = false,
                                topLeft = Offset(arcInset, arcInset),
                                size = Size(size.width - arcInset * 2, size.height - arcInset * 2),
                                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                            )

                            drawArc(
                                color = if (isCleaned) HybridEmerald else HybridCoralRed,
                                startAngle = 140f,
                                sweepAngle = animatedSweep,
                                useCenter = false,
                                topLeft = Offset(arcInset, arcInset),
                                size = Size(size.width - arcInset * 2, size.height - arcInset * 2),
                                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // Center Tilted Coral-Red Broom + Sparkles
                            rotate(degrees = -28f, pivot = center) {
                                // Broom handle
                                drawRoundRect(
                                    color = if (isCleaned) HybridEmerald else HybridCoralRed,
                                    topLeft = Offset(center.x - 4.dp.toPx(), center.y - 42.dp.toPx()),
                                    size = Size(8.dp.toPx(), 42.dp.toPx()),
                                    cornerRadius = CornerRadius(4.dp.toPx())
                                )
                                // Broom collar
                                drawRoundRect(
                                    color = Color.White,
                                    topLeft = Offset(center.x - 16.dp.toPx(), center.y - 4.dp.toPx()),
                                    size = Size(32.dp.toPx(), 8.dp.toPx()),
                                    cornerRadius = CornerRadius(3.dp.toPx())
                                )
                                // Broom bristles trapezoid
                                val bristlePath = Path().apply {
                                    moveTo(center.x - 16.dp.toPx(), center.y + 4.dp.toPx())
                                    lineTo(center.x + 16.dp.toPx(), center.y + 4.dp.toPx())
                                    lineTo(center.x + 28.dp.toPx(), center.y + 38.dp.toPx())
                                    lineTo(center.x - 28.dp.toPx(), center.y + 38.dp.toPx())
                                    close()
                                }
                                drawPath(
                                    path = bristlePath,
                                    color = if (isCleaned) HybridEmerald else HybridCoralRed
                                )
                            }

                            // Shining sparkles around broom
                            val sparkleColor = if (isCleaned) HybridCyan else HybridGold
                            drawCircle(sparkleColor, radius = 3.5.dp.toPx(), center = Offset(center.x + 38.dp.toPx(), center.y - 26.dp.toPx()))
                            drawCircle(Color.White, radius = 2.5.dp.toPx(), center = Offset(center.x - 40.dp.toPx(), center.y - 12.dp.toPx()))
                            drawCircle(sparkleColor, radius = 3.dp.toPx(), center = Offset(center.x + 28.dp.toPx(), center.y + 32.dp.toPx()))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Occupied storage",
                        color = HybridTextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${stats.usedGbText} / ${stats.totalGbText}",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Coral-Red Clean Junk Button
                    Button(
                        onClick = {
                            if (!isCleaningNow) {
                                isCleaningNow = true
                                coroutineScope.launch {
                                    val freedBytes = withContext(Dispatchers.IO) {
                                        HybridCleanerHubHelper.cleanAppCacheAndTemp(context)
                                    }
                                    isCleaningNow = false
                                    isCleaned = true
                                    refreshTrigger++
                                    val freedLabel = if (freedBytes >= 1024L * 1024L) {
                                        String.format(Locale.US, "%.1f MB", freedBytes / (1024.0 * 1024.0))
                                    } else {
                                        "${(freedBytes / 1024L).coerceAtLeast(0L)} KB"
                                    }
                                    Toast.makeText(
                                        context,
                                        if (freedBytes > 0L) "Cleaned $freedLabel of App & Temp Cache!" else "App Cache is already clean!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        enabled = !isCleaningNow,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCleaned) HybridEmerald else HybridCoralRed
                        ),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .height(52.dp)
                    ) {
                        Icon(
                            imageVector = if (isCleaned) Icons.Default.CheckCircle else Icons.Default.CleaningServices,
                            contentDescription = "Clean Junk",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                isCleaningNow -> "Cleaning Cache..."
                                isCleaned -> "Cache Cleaned ✓"
                                else -> "Clean Cache (${stats.cleanableBadgeText})"
                            },
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Free up more space",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 2x3 Working Cards Grid
                item {
                    val bigFilesCount = allVideos.count { it.size > 40_000_000L }
                    val watchedCount = allVideos.count { it.id in watchedVideoIds }
                    val rarelyCount = allVideos.count { it.id !in watchedVideoIds }

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CleanerToolCard(
                                title = "Big File Cleaner",
                                subtitle = "$bigFilesCount files > 40MB",
                                icon = Icons.Default.VideoFile,
                                accent = HybridCoralRed,
                                modifier = Modifier.weight(1f),
                                onClick = { activeSubTool = "Big File Cleaner" }
                            )
                            CleanerToolCard(
                                title = "Watched Videos",
                                subtitle = "$watchedCount watched",
                                icon = Icons.Default.History,
                                accent = HybridEmerald,
                                modifier = Modifier.weight(1f),
                                onClick = { activeSubTool = "Watched Videos" }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CleanerToolCard(
                                title = "Rarely Watch Videos",
                                subtitle = "$rarelyCount unplayed",
                                icon = Icons.Default.VisibilityOff,
                                accent = HybridCyan,
                                modifier = Modifier.weight(1f),
                                onClick = { activeSubTool = "Rarely Watch Videos" }
                            )
                            CleanerToolCard(
                                title = "Duplicate Cleaner",
                                subtitle = "$duplicateCount redundant copies",
                                icon = Icons.Default.FileCopy,
                                accent = HybridGold,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    duplicateConfirmStep = 0
                                    activeSubTool = "Duplicate Cleaner"
                                }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val cacheLabel = if (cacheSizeBytes >= 1024L * 1024L) {
                                String.format(Locale.US, "%.1f MB temp", cacheSizeBytes / (1024.0 * 1024.0))
                            } else {
                                "${(cacheSizeBytes / 1024L).coerceAtLeast(0L)} KB temp"
                            }
                            CleanerToolCard(
                                title = "App & Temp Cache",
                                subtitle = cacheLabel,
                                icon = Icons.Default.Cached,
                                accent = Color(0xFFB388FF),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    coroutineScope.launch {
                                        val freed = withContext(Dispatchers.IO) {
                                            HybridCleanerHubHelper.cleanAppCacheAndTemp(context)
                                        }
                                        refreshTrigger++
                                        val freedStr = if (freed >= 1024L * 1024L) {
                                            String.format(Locale.US, "%.1f MB", freed / (1024.0 * 1024.0))
                                        } else {
                                            "${(freed / 1024L).coerceAtLeast(0L)} KB"
                                        }
                                        Toast.makeText(
                                            context,
                                            if (freed > 0L) "App & Thumbnail Cache Cleared ($freedStr Freed)" else "App Cache Already Clean",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                            CleanerToolCard(
                                title = "Recycle Bin",
                                subtitle = "${recycledItems.size} in 30-Day Vault",
                                icon = Icons.Default.DeleteSweep,
                                accent = HybridEmerald,
                                modifier = Modifier.weight(1f),
                                onClick = { activeSubTool = "Recycle Bin" }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun CleanerToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(HybridCardBg)
            .border(1.dp, HybridCardBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = accent, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            color = HybridTextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CleanerSubToolView(
    toolName: String,
    allVideos: List<MediaItem>,
    watchedVideoIds: Set<String>,
    recycledItems: List<RecycledMediaItem>,
    duplicateConfirmStep: Int,
    onDuplicateConfirmStepChange: (Int) -> Unit,
    onMoveToTrash: (MediaItem) -> Unit,
    onRestoreFromTrash: (String) -> Unit,
    onPermanentDelete: (MediaItem) -> Unit,
    onPlayVideo: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    if (toolName == "Recycle Bin") {
        if (recycledItems.isEmpty()) {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Empty Recycle Bin",
                        tint = HybridEmerald,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Recycle Bin is Empty", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Deleted videos stay safe here for 30 days", color = HybridTextSecondary, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(recycledItems, key = { it.mediaItem.id }) { recycled ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(HybridCardBg)
                            .border(1.dp, HybridCardBorder, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = recycled.mediaItem.cleanTitle,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${recycled.mediaItem.formattedSize} • ${recycled.daysRemaining} days left",
                                color = HybridEmerald,
                                fontSize = 12.sp
                            )
                        }
                        TextButton(onClick = { onRestoreFromTrash(recycled.mediaItem.id) }) {
                            Text("Restore", color = HybridEmerald, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { onPermanentDelete(recycled.mediaItem) }) {
                            Text("Delete", color = HybridCoralRed)
                        }
                    }
                }
            }
        }
        return
    }

    val filteredList = remember(toolName, allVideos, watchedVideoIds) {
        when (toolName) {
            "Big File Cleaner" -> allVideos.filter { it.size > 40_000_000L }.sortedByDescending { it.size }
            "Watched Videos" -> allVideos.filter { it.id in watchedVideoIds }
            "Rarely Watch Videos" -> allVideos.filter { it.id !in watchedVideoIds }
            "Duplicate Cleaner" -> HybridCleanerHubHelper.findDuplicateVideos(allVideos)
            else -> emptyList()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (toolName == "Duplicate Cleaner") {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(HybridCardBg)
                        .border(1.dp, HybridCardBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "2-Step Safe Duplicate Scanner",
                        color = HybridGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (filteredList.isEmpty()) {
                            "No duplicate video copies detected in your library."
                        } else {
                            "Found ${filteredList.size} redundant copies. Original oldest copies are preserved."
                        },
                        color = HybridTextSecondary,
                        fontSize = 13.sp
                    )
                    if (filteredList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (duplicateConfirmStep == 0) {
                                    onDuplicateConfirmStepChange(1)
                                } else {
                                    onDuplicateConfirmStepChange(0)
                                    val count = filteredList.size
                                    filteredList.forEach { dup -> onMoveToTrash(dup) }
                                    Toast.makeText(
                                        context,
                                        "Moved $count Duplicate Copies to 30-Day Recycle Bin!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (duplicateConfirmStep == 1) HybridCoralRed else HybridEmerald
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (duplicateConfirmStep == 0) "Step 1: Verify ${filteredList.size} Duplicates" else "Step 2: Move Duplicates to Recycle Bin",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (filteredList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No files in $toolName", color = HybridTextSecondary, fontSize = 15.sp)
                }
            }
        } else {
            items(filteredList, key = { it.id }) { video ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(HybridCardBg)
                        .border(1.dp, HybridCardBorder, RoundedCornerShape(14.dp))
                        .clickable { onPlayVideo(video) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = video.cleanTitle,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${video.resolution.uppercase()} | ${video.formattedSize} • ${video.folderName}",
                            color = HybridTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = { onMoveToTrash(video) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Move to Recycle Bin",
                            tint = HybridCoralRed
                        )
                    }
                }
            }
        }
    }
}
