package com.example.ui.screens

import android.app.Activity
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaItem
import com.example.ui.components.GuaranteedAlbumArt
import com.example.ui.components.LiquidGlassBox
import com.example.ui.components.VideoPlayerView
import com.example.ui.theme.*
import com.example.viewmodel.PlayerViewModel

private enum class VaultCategoryTab {
    ALL, VIDEOS, AUDIO
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateFolderScreen(
    viewModel: PlayerViewModel,
    onPlayMusic: (MediaItem) -> Unit,
    onPlayVideo: (MediaItem) -> Unit,
    onBackClick: () -> Unit,
    accentColor: Color = HybridEmerald
) {
    val context = LocalContext.current
    val isPinSet by viewModel.vaultPinSet.collectAsState()
    val isUnlocked by viewModel.vaultUnlocked.collectAsState()
    val isDecoyVault by viewModel.isDecoyVault.collectAsState()
    val privateItems by viewModel.privateItems.collectAsState()
    val allPublicMedia by viewModel.allMediaItems.collectAsState()
    val lockoutSeconds by viewModel.vaultLockoutSeconds.collectAsState()
    val intruderLogs by viewModel.intruderLogs.collectAsState()
    val globalFlagSecure by viewModel.globalFlagSecure.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()

    val isHindi = remember(selectedLanguage) {
        selectedLanguage.contains("Hindi", ignoreCase = true) ||
            selectedLanguage.contains("हिन्दी")
    }

    var pinDigits by remember { mutableStateOf("") }
    var setupStep by remember { mutableStateOf(1) }
    var setupFirstPin by remember { mutableStateOf("") }
    var keypadErrorMessage by remember { mutableStateOf<String?>(null) }
    var showIntruderDialog by remember { mutableStateOf(false) }
    var showAddMediaToVaultDialog by remember { mutableStateOf(false) }
    var vaultCategoryTab by remember { mutableStateOf(VaultCategoryTab.ALL) }

    // Built-in Full Video Player & Full Audio Player inside the Private Vault!
    var activeVaultVideo by remember { mutableStateOf<MediaItem?>(null) }
    var showVaultFullAudioPlayer by remember { mutableStateOf(false) }

    // Apply FLAG_SECURE to block screenshots while inside Privacy Vault
    DisposableEffect(globalFlagSecure) {
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (!viewModel.globalFlagSecure.value) {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    LaunchedEffect(isUnlocked) {
        if (!isUnlocked) {
            pinDigits = ""
            activeVaultVideo = null
            showVaultFullAudioPlayer = false
        }
    }

    // If playing a locked video inside the Private Vault, render the full built-in VideoPlayerView!
    if (isUnlocked && activeVaultVideo != null) {
        val currentVideo = activeVaultVideo!!
        val vaultVideos = remember(privateItems, isDecoyVault) {
            if (isDecoyVault) emptyList() else privateItems.filter { it.isVideo }
        }
        BackHandler {
            activeVaultVideo = null
        }
        VideoPlayerView(
            videoPath = currentVideo.path,
            initialPositionMs = 0L,
            onProgressUpdate = { posMs ->
                viewModel.saveVideoProgress(currentVideo.id, posMs)
            },
            onBackClick = { activeVaultVideo = null },
            accentColor = accentColor,
            videoTitle = currentVideo.cleanTitle,
            allVideos = vaultVideos.ifEmpty { listOf(currentVideo) },
            onSelectVideo = { nextVid -> activeVaultVideo = nextVid },
            onBackgroundAudioPlay = {
                viewModel.playMusic(currentVideo)
                activeVaultVideo = null
                showVaultFullAudioPlayer = true
            }
        )
        return
    }

    // If playing a locked song inside the Private Vault, render the full built-in MusicPlayerScreen!
    if (isUnlocked && showVaultFullAudioPlayer) {
        BackHandler {
            showVaultFullAudioPlayer = false
        }
        MusicPlayerScreen(
            viewModel = viewModel,
            onBackClick = { showVaultFullAudioPlayer = false },
            accentColor = accentColor
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isHindi) "प्राइवेट वॉल्ट (Private Vault)" else "Privacy Vault (FLAG_SECURE)",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.lockVault()
                        onBackClick()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (isUnlocked && !isDecoyVault) {
                        IconButton(onClick = { showAddMediaToVaultDialog = true }) {
                            Icon(
                                Icons.Default.AddCircleOutline,
                                contentDescription = "Add Media to Vault",
                                tint = HybridCyan
                            )
                        }
                    }
                    if (intruderLogs.isNotEmpty()) {
                        IconButton(onClick = { showIntruderDialog = true }) {
                            Icon(Icons.Default.Warning, contentDescription = "Intruder Logs", tint = HybridCoralRed)
                        }
                    }
                    if (isUnlocked) {
                        IconButton(onClick = { viewModel.lockVault() }) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock Vault", tint = HybridEmerald)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HybridDarkBg)
            )
        },
        containerColor = HybridDarkBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!isPinSet) {
                VaultPinPadSection(
                    title = if (setupStep == 1) "Create Real Vault PIN (4 Digits)" else "Confirm Your Real PIN",
                    subtitle = "Tip: Entering '0000' later opens an Empty Decoy Vault to protect your privacy.",
                    pinDigits = pinDigits,
                    errorMessage = keypadErrorMessage,
                    onDigitPress = { digit ->
                        if (pinDigits.length < 4) {
                            pinDigits += digit
                            keypadErrorMessage = null
                            if (pinDigits.length == 4) {
                                if (pinDigits == "0000") {
                                    keypadErrorMessage = "0000 is reserved for Fake Decoy Vault. Choose another PIN."
                                    pinDigits = ""
                                } else if (setupStep == 1) {
                                    setupFirstPin = pinDigits
                                    pinDigits = ""
                                    setupStep = 2
                                } else {
                                    if (pinDigits == setupFirstPin) {
                                        viewModel.setupVaultPin(pinDigits)
                                        Toast.makeText(context, "Dual-PIN Privacy Vault Activated!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        keypadErrorMessage = "PINs did not match. Try again."
                                        setupStep = 1
                                        pinDigits = ""
                                    }
                                }
                            }
                        }
                    },
                    onBackspace = {
                        if (pinDigits.isNotEmpty()) pinDigits = pinDigits.dropLast(1)
                    }
                )
            } else if (!isUnlocked) {
                val isLockedOut = lockoutSeconds > 0
                VaultPinPadSection(
                    title = if (isLockedOut) "🛡️ Brute-Force Lockout (${lockoutSeconds}s)" else "Enter Privacy Vault PIN",
                    subtitle = if (isLockedOut) {
                        "Too many wrong attempts! Intruder attempt logged. Wait ${lockoutSeconds}s."
                    } else {
                        "SHA-256 Encrypted • Enter Real PIN (or '0000' for Decoy Vault) • Panic Shake Active"
                    },
                    pinDigits = pinDigits,
                    errorMessage = if (isLockedOut) "Keypad Locked for ${lockoutSeconds}s (Anti-Brute-Force Shield)" else keypadErrorMessage,
                    onDigitPress = { digit ->
                        if (isLockedOut) return@VaultPinPadSection
                        if (pinDigits.length < 4) {
                            pinDigits += digit
                            keypadErrorMessage = null
                            if (pinDigits.length == 4) {
                                val entered = pinDigits
                                viewModel.verifyVaultPin(entered) { matched ->
                                    if (!matched) {
                                        keypadErrorMessage = "Incorrect PIN! Attempt Logged by Intruder Shield."
                                        pinDigits = ""
                                    }
                                }
                            }
                        }
                    },
                    onBackspace = {
                        if (!isLockedOut && pinDigits.isNotEmpty()) pinDigits = pinDigits.dropLast(1)
                    }
                )
            } else {
                val allVaultItems = if (isDecoyVault) emptyList() else privateItems
                val vaultVideos = remember(allVaultItems) { allVaultItems.filter { it.isVideo } }
                val vaultAudios = remember(allVaultItems) { allVaultItems.filter { !it.isVideo } }
                val visibleItems = when (vaultCategoryTab) {
                    VaultCategoryTab.ALL -> allVaultItems
                    VaultCategoryTab.VIDEOS -> vaultVideos
                    VaultCategoryTab.AUDIO -> vaultAudios
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(HybridCardBg)
                            .border(1.dp, HybridCardBorder, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = HybridEmerald
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isDecoyVault) {
                                    "Decoy Vault Active (0000) • Real Files Hidden"
                                } else {
                                    if (isHindi) "वॉल्ट अनलॉक • बिल्ट-इन वीडियो और ऑडियो प्लेयर" else "Built-in Vault Video & Audio Player Ready"
                                },
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (!isDecoyVault) {
                            TextButton(onClick = { showAddMediaToVaultDialog = true }) {
                                Text(
                                    text = if (isHindi) "+ जोड़ें" else "+ Add Media",
                                    color = HybridEmerald,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Category Tabs inside Vault: All | Videos | Audio
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VaultFilterChip(
                            modifier = Modifier.weight(1f),
                            label = if (isHindi) "सभी (${allVaultItems.size})" else "All (${allVaultItems.size})",
                            selected = vaultCategoryTab == VaultCategoryTab.ALL,
                            accent = HybridEmerald,
                            onClick = { vaultCategoryTab = VaultCategoryTab.ALL }
                        )
                        VaultFilterChip(
                            modifier = Modifier.weight(1f),
                            label = if (isHindi) "वीडियो (${vaultVideos.size})" else "Videos (${vaultVideos.size})",
                            selected = vaultCategoryTab == VaultCategoryTab.VIDEOS,
                            accent = HybridCyan,
                            onClick = { vaultCategoryTab = VaultCategoryTab.VIDEOS }
                        )
                        VaultFilterChip(
                            modifier = Modifier.weight(1f),
                            label = if (isHindi) "गाने (${vaultAudios.size})" else "Songs (${vaultAudios.size})",
                            selected = vaultCategoryTab == VaultCategoryTab.AUDIO,
                            accent = HybridGold,
                            onClick = { vaultCategoryTab = VaultCategoryTab.AUDIO }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (visibleItems.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.FolderOff,
                                    contentDescription = "Empty Vault",
                                    tint = HybridTextSecondary,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (isHindi) "वॉल्ट में कोई फ़ाइल नहीं है" else "No Protected Files in Vault",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                                Text(
                                    text = if (isHindi) {
                                        "किसी भी वीडियो या गाने के 3-डॉट मेनू से या '+ Add Media' बटन से यहाँ लॉक करें।"
                                    } else {
                                        "Tap '+ Add Media' above or use the 3-dot menu on any video or song to lock it here."
                                    },
                                    color = HybridTextSecondary,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(visibleItems, key = { it.id }) { item ->
                                val itemAccent = if (item.isVideo) HybridCyan else HybridEmerald
                                LiquidGlassBox(
                                    modifier = Modifier.fillMaxWidth(),
                                    cornerRadius = 16.dp,
                                    tintColor = itemAccent,
                                    glassAlpha = 0.20f,
                                    onClick = {
                                        if (item.isVideo) {
                                            activeVaultVideo = item
                                        } else {
                                            viewModel.playMusic(item)
                                            showVaultFullAudioPlayer = true
                                        }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        GuaranteedAlbumArt(
                                            mediaPath = item.path,
                                            title = item.cleanTitle,
                                            isVideo = item.isVideo,
                                            modifier = Modifier.size(50.dp),
                                            cornerRadius = 12.dp,
                                            accentColor = itemAccent
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
                                                text = "${if (item.isVideo) "Video" else "Audio"} • ${item.formattedDuration} • ${item.formattedSize}",
                                                color = HybridTextSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                if (item.isVideo) {
                                                    activeVaultVideo = item
                                                } else {
                                                    viewModel.playMusic(item)
                                                    showVaultFullAudioPlayer = true
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayCircleFilled,
                                                contentDescription = "Play inside Vault",
                                                tint = itemAccent,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                        TextButton(onClick = { viewModel.toggleVaultLock(item) }) {
                                            Text(
                                                text = if (isHindi) "अनलॉक" else "Unlock",
                                                color = HybridGold,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddMediaToVaultDialog) {
        AlertDialog(
            onDismissRequest = { showAddMediaToVaultDialog = false },
            containerColor = HybridCardBg,
            title = {
                Text(
                    text = if (isHindi) "वॉल्ट में वीडियो या गाने लॉक करें" else "Lock Videos or Songs in Vault",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
            },
            text = {
                if (allPublicMedia.isEmpty()) {
                    Text("No public media found.", color = HybridTextSecondary)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(allPublicMedia, key = { it.id }) { media ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .clickable {
                                        viewModel.toggleVaultLock(media)
                                        Toast.makeText(
                                            context,
                                            "Locked in Vault: ${media.cleanTitle}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (media.isVideo) Icons.Default.Movie else Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = if (media.isVideo) HybridCyan else HybridEmerald,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = media.cleanTitle,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${media.formattedDuration} • ${media.formattedSize}",
                                        color = HybridTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Lock",
                                    tint = HybridGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddMediaToVaultDialog = false }) {
                    Text(if (isHindi) "बंद करें" else "Done", color = HybridEmerald, fontWeight = FontWeight.ExtraBold)
                }
            }
        )
    }

    if (showIntruderDialog) {
        AlertDialog(
            onDismissRequest = { showIntruderDialog = false },
            containerColor = HybridCardBg,
            title = { Text("🚨 Intruder Break-In Logs (${intruderLogs.size})", color = HybridCoralRed, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(intruderLogs) { entry ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x22FF5252))
                                .padding(10.dp)
                        ) {
                            Text("${entry.timestamp} • ${entry.pinMask}", color = HybridCoralRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(entry.detail, color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIntruderDialog = false }) {
                    Text("Close", color = HybridEmerald, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.clearIntruderLogs()
                    showIntruderDialog = false
                }) {
                    Text("Clear Logs", color = HybridCoralRed)
                }
            }
        )
    }
}

@Composable
private fun VaultFilterChip(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (selected) accent else Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) accent else Color.White,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun VaultPinPadSection(
    title: String,
    subtitle: String,
    pinDigits: String,
    errorMessage: String?,
    onDigitPress: (String) -> Unit,
    onBackspace: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.EnhancedEncryption,
            contentDescription = "Vault Lock",
            tint = HybridEmerald,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            color = HybridTextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 4 PIN Dots
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { index ->
                val filled = index < pinDigits.length
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (filled) HybridEmerald else Color(0x33FFFFFF))
                        .border(1.dp, HybridEmerald, CircleShape)
                )
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(errorMessage, color = HybridCoralRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(28.dp))

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "DEL")
        )

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    row.forEach { key ->
                        if (key.isEmpty()) {
                            Spacer(modifier = Modifier.size(68.dp))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(HybridCardBg)
                                    .border(1.dp, HybridCardBorder, CircleShape)
                                    .clickable {
                                        if (key == "DEL") onBackspace() else onDigitPress(key)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (key == "DEL") {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                                        contentDescription = "Delete",
                                        tint = Color.White
                                    )
                                } else {
                                    Text(
                                        text = key,
                                        color = Color.White,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
