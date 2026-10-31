package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.MediaItem
import com.example.helpers.*
import com.example.ui.components.LiquidGlassBox
import com.example.ui.components.LiquidGlassIconOrb
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
    val penguinsOnlyMode by viewModel.penguinsOnlyMode.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val activePitchPreset by viewModel.activePitchPreset.collectAsState()
    val oneHandMode by viewModel.oneHandMode.collectAsState()
    val hybrid8kDecoder by viewModel.hybrid8kDecoder.collectAsState()
    val is200Boost by viewModel.is200BoostEnabled.collectAsState()
    val hdrCcBadges by viewModel.hdrCcBadges.collectAsState()
    val doubleShakeEnabled by viewModel.doubleShakeEnabled.collectAsState()
    val shakeSensitivity by viewModel.shakeSensitivity.collectAsState()
    val active3DMusicTheme by viewModel.active3DMusicTheme.collectAsState()
    val globalFlagSecure by viewModel.globalFlagSecure.collectAsState()
    val autoLockOnMinimize by viewModel.autoLockOnMinimize.collectAsState()
    val panicShakeVaultLock by viewModel.panicShakeVaultLock.collectAsState()
    val vaultPinSet by viewModel.vaultPinSet.collectAsState()
    val intruderLogs by viewModel.intruderLogs.collectAsState()

    var showThemeStudioDialog by remember { mutableStateOf(false) }
    var show3DMusicThemesDialog by remember { mutableStateOf(false) }
    var showSecurityFortressDialog by remember { mutableStateOf(false) }
    var showCompanionDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showPitchDialog by remember { mutableStateOf(false) }
    var showNetworkStreamDialog by remember { mutableStateOf(false) }
    var showRecycleBinDialog by remember { mutableStateOf(false) }
    var showVipManageDialog by remember { mutableStateOf(false) }
    var streamUrlInput by remember { mutableStateOf("") }
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
        // --- 1. COMPACT VIP STRIP ("8K Studio Pass" + Live Auto Point Version Badge) ---
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(HybridCardBg)
                    .border(1.dp, HybridCardBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_custom_logo),
                            contentDescription = "Hybrid Player App Icon",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFFF2A4B).copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "8K Studio Pass • v${com.example.BuildConfig.VERSION_NAME}",
                                color = HybridGold,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Build #${com.example.BuildConfig.VERSION_CODE} • Lock-Screen & BG Notification Active",
                                color = HybridEmerald,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val current = viewModel.nowPlaying.value
                                ?: viewModel.musicItems.value.firstOrNull()
                                ?: viewModel.videoItems.value.firstOrNull()
                            if (current != null) {
                                viewModel.playMusic(current)
                            } else {
                                MediaNotificationHelper.showOrUpdateNotification(
                                    context = context,
                                    title = "Hybrid 8K Studio Ready",
                                    subtitle = "Notification & Lock-Screen Controls Active (v${com.example.BuildConfig.VERSION_NAME})",
                                    mediaPath = null,
                                    isVideo = false,
                                    isPlaying = true,
                                    positionMs = 0L,
                                    durationMs = 180_000L,
                                    contentIntentActivity = com.example.MainActivity::class.java
                                )
                            }
                            Toast.makeText(
                                context,
                                "Notification & Lock-Screen Controls Active (Prev, -10s, Play/Pause, +10s, Next)!",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = HybridEmerald, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Show Notif Controls", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.loadMedia()
                            Toast.makeText(
                                context,
                                "Deep Scanning Real Device Videos & Songs...",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = HybridCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Deep Scan Media", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
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
                    title = "3D Animated Song Player Themes",
                    value = "$active3DMusicTheme (15 3D Themes)",
                    icon = Icons.Default.AutoAwesome,
                    onClick = { show3DMusicThemesDialog = true }
                )

                PersonalizationNavRow(
                    title = "3D Puppy & Penguin Companion (20 Varieties)",
                    value = when {
                        !companionEnabled -> "Disabled"
                        penguinsOnlyMode -> "🐧 Penguins Only Mode ($companionBreed)"
                        else -> "$companionBreed (20 Varieties)"
                    },
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

        // --- 5. PERMANENT DOUBLE-SHAKE ("2x SHAKE + VIBRATION") CONTROL CENTER ---
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(HybridCardBg)
                    .border(
                        1.dp,
                        if (doubleShakeEnabled) HybridCyan.copy(alpha = 0.6f) else HybridCardBorder,
                        RoundedCornerShape(16.dp)
                    )
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Vibration, contentDescription = null, tint = HybridCyan, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Permanent 2x Shake & Vibration (Next Song)",
                            color = HybridCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        text = if (doubleShakeEnabled) "PERMANENT ON" else "PERMANENT OFF",
                        color = if (doubleShakeEnabled) HybridEmerald else HybridCoralRed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                EngineSwitchRow(
                    title = "Double-Shake (2x Shake) to Change Song",
                    subtitle = "Shake phone twice to vibrate & play next song (Active in Audio & BG Video Audio only)",
                    checked = doubleShakeEnabled,
                    onCheckedChange = {
                        viewModel.setDoubleShakeEnabled(it)
                        Toast.makeText(
                            context,
                            if (it) "Permanent 2x Shake + Vibration Enabled!" else "Permanent 2x Shake Disabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )

                Text(
                    text = "Shake Sensor Sensitivity:",
                    color = HybridTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("High", "Normal", "Firm").forEach { level ->
                        val active = shakeSensitivity == level
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) HybridCyan else HybridCardAlt)
                                .border(1.dp, if (active) HybridCyan else HybridCardBorder, RoundedCornerShape(10.dp))
                                .clickable {
                                    viewModel.setShakeSensitivity(level)
                                    Toast.makeText(context, "2x Shake Sensitivity: $level", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = level,
                                color = if (active) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.executeDoubleShakeNextSong() },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Test 2x Shake", color = HybridEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- 6. 7-LAYER CYBER SECURITY FORTRESS & ANTI-TAMPER AUDIT ---
        item {
            val auditReport = remember(globalFlagSecure, autoLockOnMinimize, panicShakeVaultLock, vaultPinSet) {
                SecurityDefenseEngine.runSecurityIntegrityAudit(
                    context = context,
                    flagSecureEnabled = globalFlagSecure,
                    autoLockOnMinimize = autoLockOnMinimize,
                    panicShakeLock = panicShakeVaultLock,
                    vaultPinSet = vaultPinSet
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(HybridCardBg)
                    .border(1.dp, HybridEmerald.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = HybridEmerald, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "7-Layer Cyber Security Fortress",
                            color = HybridEmerald,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(HybridEmerald.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Shield ${auditReport.securityScore}%",
                            color = HybridEmerald,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                EngineSwitchRow(
                    title = "Global Anti-Screenshot Shield (FLAG_SECURE)",
                    subtitle = "Blocks screenshots, screen recording & recent-apps snooping everywhere",
                    checked = globalFlagSecure,
                    onCheckedChange = {
                        viewModel.toggleGlobalFlagSecure()
                        Toast.makeText(
                            context,
                            if (!globalFlagSecure) "🛡️ Global Anti-Screenshot Shield Activated!" else "Global Shield Set to Vault-Only",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )

                EngineSwitchRow(
                    title = "Instant Background Auto-Lock (ON_STOP)",
                    subtitle = "Locks Privacy Vault in <1ms when app minimizes or screen turns off",
                    checked = autoLockOnMinimize,
                    onCheckedChange = { viewModel.toggleAutoLockOnMinimize() }
                )

                EngineSwitchRow(
                    title = "Panic Shake Emergency Vault Lock",
                    subtitle = "Shaking phone inside Privacy Vault immediately locks & hides files",
                    checked = panicShakeVaultLock,
                    onCheckedChange = { viewModel.togglePanicShakeVaultLock() }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { showSecurityFortressDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = HybridEmerald),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Security Audit & Intruder Logs (${intruderLogs.size})",
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }

    // --- 15 3D ANIMATED MUSIC PLAYER THEMES DIALOG ---
    if (show3DMusicThemesDialog) {
        AlertDialog(
            onDismissRequest = { show3DMusicThemesDialog = false },
            containerColor = HybridCardBg,
            title = {
                Text(
                    "15 3D Animated Music Player Themes",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(Music3DThemeEngine.THEMES_3D, key = { it.id }) { spec ->
                        val selected = active3DMusicTheme == spec.name
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) spec.primaryColor.copy(alpha = 0.2f) else HybridCardAlt)
                                .clickable {
                                    viewModel.select3DMusicTheme(spec.name)
                                    show3DMusicThemesDialog = false
                                    Toast.makeText(context, "3D Music Theme: ${spec.name}", Toast.LENGTH_SHORT).show()
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(spec.primaryColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${spec.id}", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(spec.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(spec.subtitle, color = HybridTextSecondary, fontSize = 11.sp)
                            }
                            if (selected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = spec.primaryColor)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { show3DMusicThemesDialog = false }) {
                    Text("Close", color = HybridEmerald)
                }
            }
        )
    }

    // --- 7-LAYER CYBER SECURITY AUDIT & INTRUDER LOGS DIALOG ---
    if (showSecurityFortressDialog) {
        val auditReport = remember(globalFlagSecure, autoLockOnMinimize, panicShakeVaultLock, vaultPinSet) {
            SecurityDefenseEngine.runSecurityIntegrityAudit(
                context = context,
                flagSecureEnabled = globalFlagSecure,
                autoLockOnMinimize = autoLockOnMinimize,
                panicShakeLock = panicShakeVaultLock,
                vaultPinSet = vaultPinSet
            )
        }
        AlertDialog(
            onDismissRequest = { showSecurityFortressDialog = false },
            containerColor = HybridCardBg,
            title = {
                Text(
                    "🛡️ 7-Layer Security Audit (${auditReport.securityScore}/100)",
                    color = HybridEmerald,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(auditReport.layers) { layer ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(HybridCardAlt)
                                .border(1.dp, if (layer.isSecure) HybridEmerald.copy(alpha = 0.4f) else HybridCardBorder, RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(layer.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(layer.description, color = HybridTextSecondary, fontSize = 10.sp)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = layer.statusBadge,
                                color = if (layer.isSecure) HybridEmerald else HybridGold,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Intruder Failed-PIN Logs (${intruderLogs.size}):",
                            color = HybridCoralRed,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }

                    if (intruderLogs.isEmpty()) {
                        item {
                            Text(
                                text = "Zero unauthorized Vault break-in attempts detected.",
                                color = HybridTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    } else {
                        items(intruderLogs) { log ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x22FF5252))
                                    .padding(8.dp)
                            ) {
                                Text("${log.timestamp} • ${log.pinMask}", color = HybridCoralRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(log.detail, color = Color.White, fontSize = 10.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSecurityFortressDialog = false }) {
                    Text("Done", color = HybridEmerald, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                if (intruderLogs.isNotEmpty()) {
                    TextButton(onClick = { viewModel.clearIntruderLogs() }) {
                        Text("Clear Logs", color = HybridCoralRed)
                    }
                }
            }
        )
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

    // --- REALISTIC 3D PUPPY & PENGUIN COMPANION SELECTOR DIALOG (20 Colorful Varieties) ---
    if (showCompanionDialog) {
        AlertDialog(
            onDismissRequest = { showCompanionDialog = false },
            containerColor = HybridCardBg,
            title = {
                Text(
                    "20 Colorful Companions (Penguins & Puppies)",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Show Floating 3D Companion", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Switch(
                            checked = companionEnabled,
                            onCheckedChange = { viewModel.setCompanionEnabled(it) }
                        )
                    }

                    // Penguins Only Mode Toggle
                    LiquidGlassBox(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 14.dp,
                        tintColor = Color(0xFF00E5FF),
                        glassAlpha = if (penguinsOnlyMode) 0.32f else 0.14f,
                        onClick = { viewModel.togglePenguinsOnlyMode() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🐧 Penguins Only Mode",
                                    color = Color(0xFF00E5FF),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Auto-cycles through all 6 colorful 3D DJ Penguins!",
                                    color = HybridTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = penguinsOnlyMode,
                                onCheckedChange = { viewModel.setPenguinsOnlyMode(it) }
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(CompanionBreed.entries) { breed ->
                            val selected = companionBreed == breed.displayName
                            LiquidGlassBox(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 12.dp,
                                tintColor = if (selected) breed.headphoneAccent else breed.primaryFur,
                                glassAlpha = if (selected) 0.34f else 0.16f,
                                onClick = {
                                    viewModel.selectCompanionBreed(breed.displayName)
                                    if (!breed.isPenguin && penguinsOnlyMode) {
                                        viewModel.setPenguinsOnlyMode(false)
                                    }
                                    showCompanionDialog = false
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(breed.primaryFur)
                                            .border(2.dp, breed.headphoneAccent, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = breed.displayName,
                                        color = Color.White,
                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (selected) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = breed.headphoneAccent)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCompanionDialog = false }) {
                    Text("Done", color = HybridEmerald, fontWeight = FontWeight.Bold)
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
    LiquidGlassBox(
        modifier = modifier,
        cornerRadius = 18.dp,
        tintColor = accent,
        glassAlpha = 0.22f,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiquidGlassIconOrb(
                icon = icon,
                contentDescription = title,
                tintColor = accent,
                size = 40.dp,
                iconSize = 20.dp,
                isActive = true,
                onClick = onClick
            )
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
