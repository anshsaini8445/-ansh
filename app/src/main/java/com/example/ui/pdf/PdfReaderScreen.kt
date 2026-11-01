package com.example.ui.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.LiquidGlassBox
import com.example.ui.components.LiquidGlassIconOrb
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DevicePdfItem(
    val id: String,
    val title: String,
    val path: String,
    val uri: Uri?,
    val sizeBytes: Long,
    val lastModifiedMs: Long
) {
    val formattedSize: String
        get() {
            val kb = sizeBytes / 1024.0
            val mb = kb / 1024.0
            return if (mb >= 1.0) String.format(Locale.US, "%.2f MB", mb)
            else String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
        }

    val formattedDate: String
        get() = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(lastModifiedMs))
}

enum class PdfEyeCareMode(val label: String) {
    DAY_PAPER("Day Paper"),
    NIGHT_DARK("Night Dark"),
    WARM_SEPIA("Warm Sepia"),
    HIGH_CONTRAST("High Contrast")
}

@Composable
fun PdfReaderScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var pdfFiles by remember { mutableStateOf<List<DevicePdfItem>>(emptyList()) }
    var isScanning by remember { mutableStateOf(true) }
    var activePdf by remember { mutableStateOf<DevicePdfItem?>(null) }
    var isFullScreenImmersed by remember { mutableStateOf(false) }
    var eyeCareMode by remember { mutableStateOf(PdfEyeCareMode.DAY_PAPER) }
    var zoomScale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var searchQuery by remember { mutableStateOf("") }

    BackHandler {
        if (activePdf != null) {
            if (isFullScreenImmersed) {
                isFullScreenImmersed = false
            } else {
                activePdf = null
                zoomScale = 1f
                panOffset = Offset.Zero
            }
        } else {
            onBackClick()
        }
    }

    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Document.pdf"
            val picked = DevicePdfItem(
                id = uri.toString(),
                title = if (name.endsWith(".pdf", true)) name else "$name.pdf",
                path = uri.toString(),
                uri = uri,
                sizeBytes = 1024L * 256L,
                lastModifiedMs = System.currentTimeMillis()
            )
            if (pdfFiles.none { it.id == picked.id }) {
                pdfFiles = listOf(picked) + pdfFiles
            }
            activePdf = picked
        }
    }

    LaunchedEffect(Unit) {
        isScanning = true
        pdfFiles = withContext(Dispatchers.IO) {
            scanDevicePdfFiles(context)
        }
        isScanning = false
    }

    val filteredPdfs = remember(pdfFiles, searchQuery) {
        if (searchQuery.isBlank()) pdfFiles
        else pdfFiles.filter { it.title.contains(searchQuery.trim(), ignoreCase = true) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060A12))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (activePdf == null) {
            // --- FULL-SCREEN PDF DOCUMENT LIBRARY ---
            Column(modifier = Modifier.fillMaxSize()) {
                // Compact 56.dp Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LiquidGlassIconOrb(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tintColor = Color.White,
                            size = 38.dp,
                            iconSize = 19.dp,
                            onClick = onBackClick
                        )
                        Column {
                            Text(
                                text = "Full-Screen PDF Studio",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "${pdfFiles.size} Real PDFs • 100% Full-Screen Engine",
                                color = HybridCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiquidGlassIconOrb(
                            icon = Icons.Default.Refresh,
                            contentDescription = "Rescan Device PDFs",
                            tintColor = HybridEmerald,
                            size = 38.dp,
                            iconSize = 19.dp,
                            onClick = {
                                coroutineScope.launch {
                                    isScanning = true
                                    pdfFiles = withContext(Dispatchers.IO) { scanDevicePdfFiles(context) }
                                    isScanning = false
                                    Toast.makeText(context, "Scanned ${pdfFiles.size} PDF documents", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        LiquidGlassIconOrb(
                            icon = Icons.Default.FolderOpen,
                            contentDescription = "Open Any PDF File",
                            tintColor = HybridGold,
                            size = 38.dp,
                            iconSize = 19.dp,
                            isActive = true,
                            onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.testTag("open_pdf_picker_button")
                        )
                    }
                }

                // Compact Search & Filter Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search PDF books, notes, bills...", color = HybridTextSecondary, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = HybridCyan) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )

                // Full-Screen LazyColumn of PDF Documents (weight(1f) guarantees 100% vertical fill!)
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .testTag("pdf_document_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp)
                ) {
                    items(filteredPdfs, key = { it.id }) { pdf ->
                        LiquidGlassBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pdf_item_card"),
                            cornerRadius = 18.dp,
                            tintColor = Color(0xFFFF5252),
                            glassAlpha = 0.20f,
                            onClick = {
                                zoomScale = 1f
                                panOffset = Offset.Zero
                                isFullScreenImmersed = false
                                activePdf = pdf
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x33FF5252))
                                        .border(1.dp, Color(0xFFFF5252), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = "PDF",
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pdf.title,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "${pdf.formattedSize} • ${pdf.formattedDate} • Full-Screen Ready",
                                        color = HybridTextSecondary,
                                        fontSize = 12.sp
                                    )
                                }

                                LiquidGlassIconOrb(
                                    icon = Icons.Default.OpenInFull,
                                    contentDescription = "Read Full Screen",
                                    tintColor = HybridEmerald,
                                    size = 40.dp,
                                    iconSize = 20.dp,
                                    isActive = true,
                                    onClick = {
                                        zoomScale = 1f
                                        panOffset = Offset.Zero
                                        isFullScreenImmersed = false
                                        activePdf = pdf
                                    }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // --- 100% FULL-SCREEN HARDWARE PDF READER ENGINE ---
            val currentDoc = activePdf!!
            FullScreenPdfRendererView(
                context = context,
                pdfItem = currentDoc,
                isFullScreenImmersed = isFullScreenImmersed,
                onToggleFullScreen = { isFullScreenImmersed = !isFullScreenImmersed },
                eyeCareMode = eyeCareMode,
                onCycleEyeCareMode = {
                    val modes = PdfEyeCareMode.entries
                    eyeCareMode = modes[(modes.indexOf(eyeCareMode) + 1) % modes.size]
                },
                zoomScale = zoomScale,
                panOffset = panOffset,
                onUpdateZoomPan = { newZoom, newPan ->
                    zoomScale = newZoom
                    panOffset = newPan
                },
                onCloseDoc = {
                    activePdf = null
                    zoomScale = 1f
                    panOffset = Offset.Zero
                }
            )
        }
    }
}

@Composable
private fun FullScreenPdfRendererView(
    context: Context,
    pdfItem: DevicePdfItem,
    isFullScreenImmersed: Boolean,
    onToggleFullScreen: () -> Unit,
    eyeCareMode: PdfEyeCareMode,
    onCycleEyeCareMode: () -> Unit,
    zoomScale: Float,
    panOffset: Offset,
    onUpdateZoomPan: (Float, Offset) -> Unit,
    onCloseDoc: () -> Unit
) {
    var pageBitmaps by remember(pdfItem.id) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var pageCount by remember(pdfItem.id) { mutableStateOf(0) }
    var isRendering by remember(pdfItem.id) { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pdfItem.id) {
        isRendering = true
        val rendered = withContext(Dispatchers.IO) {
            renderPdfPagesWithHardware(context, pdfItem)
        }
        pageBitmaps = rendered
        pageCount = rendered.size
        isRendering = false
    }

    val currentPageIndex by remember {
        derivedStateOf { (listState.firstVisibleItemIndex + 1).coerceAtLeast(1) }
    }

    val colorFilter = remember(eyeCareMode) {
        when (eyeCareMode) {
            PdfEyeCareMode.DAY_PAPER -> null
            PdfEyeCareMode.NIGHT_DARK -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            PdfEyeCareMode.WARM_SEPIA -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        0.95f, 0.05f, 0f, 0f, 12f,
                        0f, 0.88f, 0.05f, 0f, 6f,
                        0f, 0f, 0.72f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            PdfEyeCareMode.HIGH_CONTRAST -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        1.4f, 0f, 0f, 0f, -30f,
                        0f, 1.4f, 0f, 0f, -30f,
                        0f, 0f, 1.4f, 0f, -30f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (eyeCareMode == PdfEyeCareMode.NIGHT_DARK) Color(0xFF05070B)
                else Color(0xFF101622)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (zoomScale > 1.05f) {
                            onUpdateZoomPan(1f, Offset.Zero)
                        } else {
                            onUpdateZoomPan(2.2f, Offset.Zero)
                        }
                    },
                    onTap = {
                        onToggleFullScreen()
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (zoomScale * zoom).coerceIn(1f, 4f)
                    val newPan = if (newScale > 1.02f) {
                        Offset(
                            x = (panOffset.x + pan.x).coerceIn(-900f, 900f),
                            y = (panOffset.y + pan.y).coerceIn(-1400f, 1400f)
                        )
                    } else {
                        Offset.Zero
                    }
                    onUpdateZoomPan(newScale, newPan)
                }
            }
    ) {
        if (isRendering) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = HybridCyan)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Rendering Full-Screen HD PDF Pages...", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // 100% FULL-SCREEN VERTICAL CONTINUOUS PAGE READER
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = zoomScale,
                        scaleY = zoomScale,
                        translationX = panOffset.x,
                        translationY = panOffset.y
                    )
                    .testTag("pdf_fullscreen_pages_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(
                    top = if (isFullScreenImmersed) 4.dp else 64.dp,
                    bottom = if (isFullScreenImmersed) 4.dp else 82.dp
                )
            ) {
                items(pageBitmaps.size) { index ->
                    val bmp = pageBitmaps[index]
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isFullScreenImmersed) 0.dp else 6.dp)
                            .clip(RoundedCornerShape(if (isFullScreenImmersed) 0.dp else 8.dp))
                            .background(Color.White)
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "PDF Page ${index + 1}",
                            contentScale = ContentScale.FillWidth,
                            colorFilter = colorFilter,
                            modifier = Modifier.fillMaxWidth()
                        )
                        // Subtle Page Badge in Corner
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.65f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "Page ${index + 1} / $pageCount",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Top Floating Control Bar (Hides in 1-Tap Immersive Full-Screen Mode)
        AnimatedVisibility(
            visible = !isFullScreenImmersed,
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            LiquidGlassBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                cornerRadius = 20.dp,
                tintColor = HybridCyan,
                glassAlpha = 0.30f
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        LiquidGlassIconOrb(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close PDF",
                            tintColor = Color.White,
                            size = 36.dp,
                            iconSize = 18.dp,
                            onClick = onCloseDoc
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = pdfItem.title,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${eyeCareMode.label} • ${(zoomScale * 100).toInt()}% Zoom • Tap page for 100% Full Screen",
                                color = HybridCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Eye-Care Mode Cycle Button (Great for Kids & Seniors!)
                        LiquidGlassIconOrb(
                            icon = Icons.Default.Brightness6,
                            contentDescription = "Eye Care Mode",
                            tintColor = HybridGold,
                            size = 36.dp,
                            iconSize = 18.dp,
                            isActive = eyeCareMode != PdfEyeCareMode.DAY_PAPER,
                            onClick = onCycleEyeCareMode
                        )
                        // Zoom In / Reset Button
                        LiquidGlassIconOrb(
                            icon = if (zoomScale > 1.1f) Icons.Default.ZoomOutMap else Icons.Default.ZoomIn,
                            contentDescription = "Zoom Toggle",
                            tintColor = HybridEmerald,
                            size = 36.dp,
                            iconSize = 18.dp,
                            onClick = {
                                if (zoomScale > 1.1f) {
                                    onUpdateZoomPan(1f, Offset.Zero)
                                } else {
                                    onUpdateZoomPan(2.0f, Offset.Zero)
                                }
                            }
                        )
                        // 100% Full-Screen Expand Toggle
                        LiquidGlassIconOrb(
                            icon = Icons.Default.Fullscreen,
                            contentDescription = "100% Full Screen",
                            tintColor = HybridCyan,
                            size = 36.dp,
                            iconSize = 19.dp,
                            isActive = true,
                            onClick = onToggleFullScreen,
                            modifier = Modifier.testTag("pdf_fullscreen_toggle_orb")
                        )
                    }
                }
            }
        }

        // Bottom Floating Page Scrubber (Hides in 1-Tap Immersive Full-Screen Mode)
        AnimatedVisibility(
            visible = !isFullScreenImmersed && pageCount > 0,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            LiquidGlassBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                cornerRadius = 22.dp,
                tintColor = HybridEmerald,
                glassAlpha = 0.28f
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "$currentPageIndex / $pageCount",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                    Slider(
                        value = currentPageIndex.toFloat(),
                        onValueChange = { pageVal ->
                            val targetIdx = (pageVal.toInt() - 1).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                            coroutineScope.launch {
                                listState.scrollToItem(targetIdx)
                            }
                        },
                        valueRange = 1f..pageCount.coerceAtLeast(1).toFloat(),
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = HybridEmerald,
                            activeTrackColor = HybridEmerald
                        )
                    )
                }
            }
        }
    }
}

private fun scanDevicePdfFiles(context: Context): List<DevicePdfItem> {
    val results = mutableListOf<DevicePdfItem>()
    val seenPaths = mutableSetOf<String>()

    // 1. Query MediaStore.Files for real PDF documents on device
    try {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )
        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("application/pdf", "%.pdf")
        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val path = cursor.getString(dataCol) ?: continue
                val file = File(path)
                if (file.exists() && file.length() > 0 && seenPaths.add(path)) {
                    results.add(
                        DevicePdfItem(
                            id = cursor.getLong(idCol).toString(),
                            title = cursor.getString(nameCol) ?: file.name,
                            path = path,
                            uri = Uri.fromFile(file),
                            sizeBytes = cursor.getLong(sizeCol).coerceAtLeast(file.length()),
                            lastModifiedMs = cursor.getLong(dateCol) * 1000L
                        )
                    )
                }
            }
        }
    } catch (_: Exception) {
    }

    // 2. Also scan common Download & Documents folders directly
    try {
        val roots = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        )
        roots.forEach { dir ->
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".pdf", ignoreCase = true) && seenPaths.add(file.absolutePath)) {
                    results.add(
                        DevicePdfItem(
                            id = file.absolutePath,
                            title = file.name,
                            path = file.absolutePath,
                            uri = Uri.fromFile(file),
                            sizeBytes = file.length(),
                            lastModifiedMs = file.lastModified()
                        )
                    )
                }
            }
        }
    } catch (_: Exception) {
    }

    // 3. Ensure at least one real, hardware-generated multi-page PDF manual exists on disk so the user can test 100% Full-Screen PDF rendering immediately!
    val realGuidePdf = ensureRealHardwarePdfManualOnDisk(context)
    if (realGuidePdf != null && seenPaths.add(realGuidePdf.absolutePath)) {
        results.add(
            DevicePdfItem(
                id = realGuidePdf.absolutePath,
                title = "Hybrid Player 8K — Full-Screen User Manual.pdf",
                path = realGuidePdf.absolutePath,
                uri = Uri.fromFile(realGuidePdf),
                sizeBytes = realGuidePdf.length(),
                lastModifiedMs = realGuidePdf.lastModified()
            )
        )
    }

    return results
}

private fun ensureRealHardwarePdfManualOnDisk(context: Context): File? {
    return try {
        val file = File(context.filesDir, "Hybrid_Player_8K_FullScreen_Guide.pdf")
        if (file.exists() && file.length() > 1024L) return file

        val doc = PdfDocument()
        val pagesContent = listOf(
            Triple(
                "HYBRID PLAYER 8K — FULL-SCREEN EDITION",
                "Designed for Kids, Seniors, Youth, India & Global Users",
                listOf(
                    "1. 100% Full-Screen Video & PDF Engine:",
                    "   • Tap any PDF page to toggle edge-to-edge 100% Full-Screen mode.",
                    "   • Pinch with two fingers or double-tap to zoom up to 400%.",
                    "   • Cycle Day Paper, Night Dark, Warm Sepia & High Contrast.",
                    "",
                    "2. Universal Experience Modes (Top Bar Orb):",
                    "   • Youth Pro Studio: 8K 60FPS badges, neon glass controls.",
                    "   • Senior Easy-Touch: Extra-large fonts, 1-tap play buttons.",
                    "   • Kids Magic Mode: Playful colors, safe lock & penguin buddy.",
                    "   • Bharat Desi Super-App: Fast ShareKaro P2P, Status Saver.",
                    "   • Global Minimal Cinema: Pure distraction-free theatre."
                )
            ),
            Triple(
                "BACKGROUND PLAYBACK & 2x SHAKE CONTROLS",
                "Real Hardware Sensors & Lock-Screen Media Session",
                listOf(
                    "1. Notification & Lock-Screen Controls:",
                    "   • Control Songs & Background Videos from Android lock screen.",
                    "   • Includes Previous, -10s Rewind, Play/Pause, +10s Fast-Forward, Next.",
                    "",
                    "2. Double-Shake (2x Wrist Shake) Track Skip:",
                    "   • Shake your phone twice to skip to the next audio track.",
                    "   • Toggle from the top bar orb or permanently in Me -> Settings.",
                    "",
                    "3. ShareKaro Live QR & High-Speed Transfer:",
                    "   • Send videos & songs over local Wi-Fi Direct TCP sockets.",
                    "   • Live dynamic QR code pairing and real-time MB/s speedometer."
                )
            )
        )

        pagesContent.forEachIndexed { idx, (heading, subheading, lines) ->
            val pageInfo = PdfDocument.PageInfo.Builder(1080, 1520, idx + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas: AndroidCanvas = page.canvas

            val bgPaint = Paint().apply { color = AndroidColor.WHITE }
            canvas.drawRect(0f, 0f, 1080f, 1520f, bgPaint)

            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.rgb(10, 25, 47)
            }
            canvas.drawRect(0f, 0f, 1080f, 220f, headerPaint)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.rgb(0, 230, 118)
                textSize = 38f
                isFakeBoldText = true
            }
            canvas.drawText(heading, 60f, 110f, titlePaint)

            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.WHITE
                textSize = 26f
            }
            canvas.drawText(subheading, 60f, 165f, subPaint)

            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.rgb(20, 30, 45)
                textSize = 30f
            }
            var y = 320f
            lines.forEach { line ->
                canvas.drawText(line, 65f, y, bodyPaint)
                y += 64f
            }

            doc.finishPage(page)
        }

        FileOutputStream(file).use { out ->
            doc.writeTo(out)
        }
        doc.close()
        file
    } catch (_: Exception) {
        null
    }
}

private fun renderPdfPagesWithHardware(context: Context, item: DevicePdfItem): List<Bitmap> {
    val bitmaps = mutableListOf<Bitmap>()
    var pfd: ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    try {
        pfd = when {
            File(item.path).exists() -> ParcelFileDescriptor.open(File(item.path), ParcelFileDescriptor.MODE_READ_ONLY)
            item.uri != null -> context.contentResolver.openFileDescriptor(item.uri, "r")
            else -> null
        }
        if (pfd != null) {
            renderer = PdfRenderer(pfd)
            val total = renderer.pageCount.coerceAtMost(35)
            for (i in 0 until total) {
                renderer.openPage(i).use { page ->
                    val targetWidth = 1080
                    val scale = targetWidth.toFloat() / page.width.coerceAtLeast(1)
                    val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(AndroidColor.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmaps.add(bmp)
                }
            }
        }
    } catch (_: Exception) {
    } finally {
        try {
            renderer?.close()
        } catch (_: Exception) {
        }
        try {
            pfd?.close()
        } catch (_: Exception) {
        }
    }
    return bitmaps
}
