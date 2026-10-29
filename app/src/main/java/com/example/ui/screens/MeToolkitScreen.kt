package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaItem
import com.example.helpers.*
import com.example.ui.theme.*
import com.example.viewmodel.PlayerViewModel

@Composable
fun MeToolkitScreen(
    viewModel: PlayerViewModel,
    onOpenPrivacyVault: () -> Unit,
    onOpenTurboShare: () -> Unit,
    onOpenMp3Converter: () -> Unit,
    onOpenStatusSaver: () -> Unit,
    onOpenCleanerHub: () -> Unit,
    onPlayStreamVideo: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeTheme by viewModel.activeTheme.collectAsState()
    val companionEnabled by viewModel.companionEnabled.collectAsState()
    val companionBreed by viewModel.companionBreed.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val activePitchPreset by viewModel.activePitchPreset.collectAsState()
    val oneHandMode by viewModel.oneHandMode.collectAsState()
    val hybrid8kDecoder by viewModel.hybrid8kDecoder.collectAsState()
    val is200Boost by viewModel.is200BoostEnabled.collectAsState()
    val hdrCcBadges by viewModel.hdrCcBadges.collectAsState()

    var showThemeStudioDialog by remember { mutableStateOf(false) }
    var showCompanionDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showPitchDialog by remember { mutableStateOf(false) }
    var showNetworkStreamDialog by remember { mutableStateOf(false) }
    var showRecycleBinDialog by remember { mutableStateOf(false) }
    var showVipManageDialog by remember { mutableStateOf(false) }
    var streamUrlInput by remember {
        mutableStateOf("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
    }
    var recycleRefresh by remember { mutableStateOf(0) }

    // Zero-Permission Android Photo Picker for Custom Gallery Wallpaper
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.setCustomWallpaper(uri.toString())
            Toast.makeText(context, "Custom Gallery Wallpaper Applied!", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = if (oneHandMode) 140.dp else 14.dp, bottom = 32.dp)
    ) {
        // --- 1. COMPACT VIP STRIP ("8K Studio Pass" with Manage Button) ---
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(HybridCardBg)
                    .border(1.dp, HybridCardBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(HybridGold.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = HybridGold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "8K Studio Pass",
                            color = HybridGold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Zero Ads • Hardware 8K & 200% Audio Unlocked",
                            color = HybridTextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Button(
                    onClick = { showVipManageDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = HybridEmerald),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Manage", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // --- 2. 2x3 SYMMETRICAL MEDIA TOOLS GRID ---
        item {
            Text(
                text = "Studio Media Toolkit",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    UniformToolkitGridCard(
                        title = "Privacy Vault",
                        subtitle = "Dual PIN & Secure",
                        icon = Icons.Default.EnhancedEncryption,
                        accent = HybridEmerald,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenPrivacyVault
                    )
                    UniformToolkitGridCard(
                        title = "Turbo Share",
                        subtitle = "6GHz Wi-Fi Direct",
                        icon = Icons.Default.Bolt,
                        accent = HybridCyan,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenTurboShare
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    UniformToolkitGridCard(
                        title = "Video to MP3",
                        subtitle = "1s Hardware Muxer",
                        icon = Icons.Default.AudioFile,
                        accent = HybridGold,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenMp3Converter
                    )
                    UniformToolkitGridCard(
                        title = "Status Saver",
                        subtitle = "WA & Business",
                        icon = Icons.Default.DownloadForOffline,
                        accent = HybridEmerald,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenStatusSaver
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    UniformToolkitGridCard(
                        title = "Storage Cleaner",
                        subtitle = "Duplicates & Cache",
                        icon = Icons.Default.CleaningServices,
                        accent = HybridCoralRed,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenCleanerHub
                    )
                    UniformToolkitGridCard(
                        title = "Recycle Bin",
                        subtitle = "30-Day Safe Vault",
                        icon = Icons.Default.RestoreFromTrash,
                        accent = HybridCyan,
                        modifier = Modifier.weight(1f),
                        onClick = { showRecycleBinDialog = true }
                    )
                }
            }
        }

        // --- 3. PERSONALIZATION & COMPANION CARD (Clean Navigation Rows) ---
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(HybridCardBg)
                    .border(1.dp, HybridCardBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Personalization & Companion",
                    color = HybridEmerald,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                PersonalizationNavRow(
                    title = "Theme & Wallpaper Studio",
                    value = "$activeTheme (19 Themes + Gallery)",
                    icon = Icons.Default.Palette,
                    onClick = { showThemeStudioDialog = true }
                )

                PersonalizationNavRow(
                    title = "Realistic Puppy Companion",
                    value = if (companionEnabled) companionBreed else "Disabled",
                    icon = Icons.Default.Pets,
                    onClick = { showCompanionDialog = true }
                )

                PersonalizationNavRow(
                    title = "App Language",
                    value = selectedLanguage,
                    icon = Icons.Default.Language,
                    onClick = { showLanguageDialog = true }
                )

                PersonalizationNavRow(
                    title = "Sound & Pitch Lab",
                    value = activePitchPreset,
                    icon = Icons.Default.GraphicEq,
                    onClick = { showPitchDialog = true }
                )

                PersonalizationNavRow(
                    title = "Network Stream URL",
                    value = "HTTP / HTTPS / M3U8 Direct Play",
                    icon = Icons.Default.CloudQueue,
                    onClick = { showNetworkStreamDialog = true }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, tint = HybridCyan, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("One-Hand Reachability Mode", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Pulls top controls down for easy thumb reach", color = HybridTextSecondary, fontSize = 11.sp)
                        }
                    }
                    Switch(
                        checked = oneHandMode,
                        onCheckedChange = { viewModel.toggleOneHandMode() }
                    )
                }
            }
        }

        // --- 4. PLAYBACK & ENGINE SWITCHES ---
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(HybridCardBg)
                    .border(1.dp, HybridCardBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Playback & Hardware Engine",
                    color = HybridEmerald,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                EngineSwitchRow(
                    title = "8K Hybrid Hardware Decoder",
                    subtitle = "Hardware MediaCodec + Partial File Recovery",
                    checked = hybrid8kDecoder,
                    onCheckedChange = { viewModel.toggleHybrid8kDecoder() }
                )

                EngineSwitchRow(
                    title = "200% Hardware Volume Booster",
                    subtitle = "LoudnessEnhancer DSP Amplification",
                    checked = is200Boost,
                    onCheckedChange = { viewModel.set200Boost(it) }
                )

                EngineSwitchRow(
                    title = "HDR & 8K Quick Badges",
                    subtitle = "Display Golden 8K & Resolution Badges on Thumbnails",
                    checked = hdrCcBadges,
                    onCheckedChange = { viewModel.toggleHdrCcBadges() }
                )
            }
        }
    }

    // --- THEME & WALLPAPER STUDIO DIALOG (19 Themes + Custom Gallery Photo) ---
    if (showThemeStudioDialog) {
        AlertDialog(
            onDismissRequest = { showThemeStudioDialog = false },
            containerColor = HybridCardBg,
            title = { Text("Theme & Wallpaper Studio (19 Themes)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            showThemeStudioDialog = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HybridEmerald),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick Custom Gallery Photo Background", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(StudioThemes) { themePalette ->
                            val selected = activeTheme == themePalette.name
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) HybridEmerald.copy(alpha = 0.18f) else HybridCardAlt)
                                    .clickable {
                                        viewModel.selectTheme(themePalette.name)
                                        viewModel.setCustomWallpaper(null)
                                        showThemeStudioDialog = false
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(themePalette.primary)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = themePalette.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                if (selected) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = HybridEmerald)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeStudioDialog = false }) {
                    Text("Close", color = HybridEmerald)
                }
            }
        )
    }

    // --- REALISTIC 3D PUPPY COMPANION SELECTOR DIALOG ---
    if (showCompanionDialog) {
        AlertDialog(
            onDismissRequest = { showCompanionDialog = false },
            containerColor = HybridCardBg,
            title = { Text("3D Articulated Companion", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Show Floating 3D Companion", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Switch(
                            checked = companionEnabled,
                            onCheckedChange = { viewModel.setCompanionEnabled(it) }
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(CompanionBreed.entries) { breed ->
                            val selected = companionBreed == breed.displayName
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) HybridEmerald.copy(alpha = 0.2f) else HybridCardAlt)
                                    .clickable {
                                        viewModel.selectCompanionBreed(breed.displayName)
                                        showCompanionDialog = false
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(breed.primaryFur)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(breed.displayName, color = Color.White, modifier = Modifier.weight(1f))
                                if (selected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = HybridEmerald)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCompanionDialog = false }) {
                    Text("Done", color = HybridEmerald)
                }
            }
        )
    }

    // --- 36 APP LANGUAGES DIALOG ---
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            containerColor = HybridCardBg,
            title = { Text("Select App Language (36 Languages)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(MultiLanguageHelper.SUPPORTED_LANGUAGES) { lang ->
                        val active = selectedLanguage == lang
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) HybridEmerald.copy(alpha = 0.18f) else HybridCardAlt)
                                .clickable {
                                    viewModel.selectLanguage(lang)
                                    showLanguageDialog = false
                                    Toast.makeText(context, "Language set to $lang", Toast.LENGTH_SHORT).show()
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(lang, color = Color.White, modifier = Modifier.weight(1f))
                            if (active) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = HybridEmerald)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("Close", color = HybridEmerald)
                }
            }
        )
    }

    // --- SOUND & PITCH LAB DIALOG ---
    if (showPitchDialog) {
        AlertDialog(
            onDismissRequest = { showPitchDialog = false },
            containerColor = HybridCardBg,
            title = { Text("Sound & Pitch Lab", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioPitchHelper.PRESETS.forEach { preset ->
                        val active = activePitchPreset == preset.name
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) HybridEmerald.copy(alpha = 0.2f) else HybridCardAlt)
                                .clickable {
                                    viewModel.applyPitchPreset(preset.name, preset.speed, preset.pitch)
                                    showPitchDialog = false
                                    Toast.makeText(context, "Applied ${preset.name}", Toast.LENGTH_SHORT).show()
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(preset.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(preset.subtitle, color = HybridTextSecondary, fontSize = 11.sp)
                            }
                            if (active) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = HybridEmerald)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPitchDialog = false }) {
                    Text("Done", color = HybridEmerald)
                }
            }
        )
    }

    // --- NETWORK STREAM URL DIALOG ---
    if (showNetworkStreamDialog) {
        AlertDialog(
            onDismissRequest = { showNetworkStreamDialog = false },
            containerColor = HybridCardBg,
            title = { Text("Network Stream URL (HTTP/HTTPS/M3U8)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = streamUrlInput,
                        onValueChange = { streamUrlInput = it },
                        label = { Text("Stream URL", color = HybridTextSecondary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Supports direct MP4, MKV, HLS (.m3u8), and DASH streams.",
                        color = HybridTextSecondary,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (streamUrlInput.isNotBlank()) {
                            showNetworkStreamDialog = false
                            onPlayStreamVideo(
                                MediaItem(
                                    id = "stream_${streamUrlInput.hashCode()}",
                                    title = "Direct Network Stream",
                                    artist = "Live Stream",
                                    duration = 596000L,
                                    size = 0L,
                                    path = streamUrlInput.trim(),
                                    isVideo = true,
                                    folderName = "Network Stream",
                                    resolution = "8k"
                                )
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HybridEmerald)
                ) {
                    Text("Play Stream", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNetworkStreamDialog = false }) {
                    Text("Cancel", color = HybridTextSecondary)
                }
            }
        )
    }

    // --- 30-DAY RECYCLE BIN DIALOG ---
    if (showRecycleBinDialog) {
        val trashed = remember(recycleRefresh) {
            PermanentDeleteHelper.getRecycledItems(context)
        }
        AlertDialog(
            onDismissRequest = { showRecycleBinDialog = false },
            containerColor = HybridCardBg,
            title = { Text("30-Day Recycle Bin (${trashed.size})", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                if (trashed.isEmpty()) {
                    Text("Recycle Bin is empty. Deleted files stay safe here for 30 days.", color = HybridTextSecondary)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(trashed, key = { it.mediaItem.id }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(HybridCardAlt)
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.mediaItem.cleanTitle, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${item.daysRemaining} days left", color = HybridEmerald, fontSize = 11.sp)
                                }
                                TextButton(onClick = {
                                    PermanentDeleteHelper.restoreFromRecycleBin(context, item.mediaItem.id)
                                    viewModel.loadMedia()
                                    recycleRefresh++
                                }) {
                                    Text("Restore", color = HybridEmerald, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (trashed.isNotEmpty()) {
                    TextButton(onClick = {
                        PermanentDeleteHelper.emptyRecycleBinPermanently(context)
                        viewModel.loadMedia()
                        recycleRefresh++
                    }) {
                        Text("Empty All", color = HybridCoralRed, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecycleBinDialog = false }) {
                    Text("Close", color = HybridEmerald)
                }
            }
        )
    }

    // --- VIP PASS INFO DIALOG ---
    if (showVipManageDialog) {
        AlertDialog(
            onDismissRequest = { showVipManageDialog = false },
            containerColor = HybridCardBg,
            title = { Text("8K Studio Pass — Lifetime Active", color = HybridGold, fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    text = "All premium features are permanently unlocked with Zero Ads:\n\n• 8K 60FPS Hardware Decoder\n• 200% LoudnessEnhancer Audio Boost\n• Dual-PIN Privacy Vault with FLAG_SECURE\n• 6GHz Turbo Share & 1s Hardware MP3 Extractor\n• 3D Articulated Puppy Companion",
                    color = Color.White,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showVipManageDialog = false }) {
                    Text("Awesome", color = HybridEmerald, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun UniformToolkitGridCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(HybridCardBg)
            .border(1.dp, HybridCardBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = accent, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = HybridTextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PersonalizationNavRow(
    title: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(value, color = HybridTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = HybridTextSecondary)
    }
}

@Composable
private fun EngineSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = HybridTextSecondary, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
