package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.MediaItem
import com.example.helpers.CompanionBreed
import com.example.helpers.FluffyMascot3DStudioPortrait
import com.example.helpers.MultiLanguageHelper
import com.example.helpers.Music3DThemeEngine
import com.example.helpers.PermanentDeleteHelper
import com.example.ui.components.LiquidGlassBox
import com.example.ui.components.LiquidGlassIconOrb
import com.example.ui.i18n.WorldLanguageEngine
import com.example.ui.theme.HybridCyan
import com.example.ui.theme.HybridEmerald
import com.example.ui.theme.HybridGold
import com.example.ui.theme.StudioThemes
import com.example.viewmodel.PlayerViewModel

private enum class MeSubScreen {
    MAIN_LIST,
    THEMES_GALLERY,
    VISUALIZER_3D_SETTINGS,
    COMPANION_GALLERY,
    LANGUAGE_PICKER,
    RECYCLE_BIN,
    NETWORK_STREAM_STUDIO
}

private data class BuiltInNetworkStreamChannel(
    val title: String,
    val subtitle: String,
    val badge: String,
    val url: String,
    val accentColor: Color
)

private val VERIFIED_WORKING_HD_STREAMS = listOf(
    BuiltInNetworkStreamChannel(
        title = "Big Buck Bunny (Full HD Cinema)",
        subtitle = "1080p 60fps • Blender Open Movie • Direct HTTPS MP4",
        badge = "1080p HD",
        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
        accentColor = Color(0xFF00E676)
    ),
    BuiltInNetworkStreamChannel(
        title = "Blender Sintel (4K Fantasy Epic)",
        subtitle = "Ultra HD Cinema • Surround Audio • Direct HTTPS MP4",
        badge = "4K UHD",
        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
        accentColor = Color(0xFF00E5FF)
    ),
    BuiltInNetworkStreamChannel(
        title = "Tears of Steel (Sci-Fi VFX Action)",
        subtitle = "1080p High Bitrate • Live Action + CGI • Direct Stream",
        badge = "SCI-FI HD",
        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
        accentColor = Color(0xFFFFD54F)
    ),
    BuiltInNetworkStreamChannel(
        title = "For Bigger Blazes (HDR 60fps Demo)",
        subtitle = "Ultra-Fast Instant Start • High Contrast HDR Stream",
        badge = "HDR 60FPS",
        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
        accentColor = Color(0xFFFF5252)
    ),
    BuiltInNetworkStreamChannel(
        title = "For Bigger Escapes (4K Nature Scenic)",
        subtitle = "Mountain & Alpine Panorama • Crystal Clear Stream",
        badge = "4K NATURE",
        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
        accentColor = Color(0xFF69F0AE)
    ),
    BuiltInNetworkStreamChannel(
        title = "Elephants Dream (3D Studio Classic)",
        subtitle = "1080p Remastered • Stereo Soundstage • Direct Stream",
        badge = "3D FILM",
        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
        accentColor = Color(0xFFB388FF)
    ),
    BuiltInNetworkStreamChannel(
        title = "For Bigger Fun (Adventure HD)",
        subtitle = "High-Energy Outdoor Action • Instant Buffer Stream",
        badge = "FAST HD",
        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
        accentColor = Color(0xFFFF80AB)
    ),
    BuiltInNetworkStreamChannel(
        title = "We Are Going On Bullrun (Supercar Rally)",
        subtitle = "Live Motorsport Documentary • Direct HTTPS MP4",
        badge = "RALLY HD",
        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4",
        accentColor = Color(0xFF40C4FF)
    )
)

@Composable
fun MeToolkitScreen(
    viewModel: PlayerViewModel,
    onOpenPrivacyVault: () -> Unit = {},
    onOpenTurboShare: () -> Unit = {},
    onOpenMp3Converter: () -> Unit = {},
    onOpenStatusSaver: () -> Unit = {},
    onOpenCleanerHub: () -> Unit = {},
    onOpenPdfReader: () -> Unit = {},
    onPlayStreamVideo: (MediaItem) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("HYBRID_PLAYER_PREFS", Context.MODE_PRIVATE) }
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val isHindi = remember(selectedLanguage) {
        WorldLanguageEngine.isHindiActive(selectedLanguage)
    }
    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    val currentThemeName by viewModel.activeTheme.collectAsState()
    val customWallpaperUri by viewModel.customWallpaperUri.collectAsState()
    val visualizer3DEnabled by viewModel.visualizer3DEnabled.collectAsState()
    val selected3DThemeName by viewModel.active3DMusicTheme.collectAsState()
    val shakeToSkipEnabled by viewModel.doubleShakeEnabled.collectAsState()
    val companionEnabled by viewModel.companionEnabled.collectAsState()
    val selectedCompanionName by viewModel.companionBreed.collectAsState()
    val companionSizeDp by viewModel.companionSizeDp.collectAsState()
    val companionDanceMode by viewModel.companionDanceMode.collectAsState()
    val companionAuraEffect by viewModel.companionAuraEffect.collectAsState()
    val companionAutoWalk by viewModel.companionAutoWalk.collectAsState()
    val companionAnimSpeed by viewModel.companionAnimSpeed.collectAsState()

    val meGalleryWallpaperLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.saveCustomGalleryWallpaperFromUri(uri)
            Toast.makeText(
                context,
                if (isHindi) "✨ आपकी फोटो हर स्क्रीन पर स्थायी रूप से सेट हो गई!" else "✨ Your Gallery Photo is set permanently across all screens!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    var subScreen by remember { mutableStateOf(MeSubScreen.MAIN_LIST) }
    var activeBottomModal by remember { mutableStateOf<String?>(null) }

    // Network Stream Studio State
    var streamUrlInput by remember { mutableStateOf("") }
    var streamTitleInput by remember { mutableStateOf("") }
    var streamBufferProfile by remember { mutableStateOf("Smooth 4K Buffer") }
    var streamPlayAsAudioOnly by remember { mutableStateOf(false) }
    val savedStreamHistory = remember {
        val raw = prefs.getString("saved_network_stream_history_v2", "") ?: ""
        mutableStateListOf<String>().apply {
            raw.split("\n").map { it.trim() }.filter { it.startsWith("http") }.forEach { add(it) }
        }
    }

    fun launchNetworkStreamUrl(rawUrl: String, customTitle: String = "") {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            Toast.makeText(
                context,
                if (isHindi) "कृपया स्ट्रीम URL दर्ज करें या नीचे से चैनल चुनें" else "Please enter a stream URL or tap a channel below",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val normalizedUrl = if (!trimmed.startsWith("http://", true) &&
            !trimmed.startsWith("https://", true) &&
            !trimmed.startsWith("rtsp://", true) &&
            !trimmed.startsWith("rtmp://", true)
        ) {
            "https://$trimmed"
        } else {
            trimmed
        }

        // Save to recent stream history
        savedStreamHistory.remove(normalizedUrl)
        savedStreamHistory.add(0, normalizedUrl)
        while (savedStreamHistory.size > 15) {
            savedStreamHistory.removeAt(savedStreamHistory.lastIndex)
        }
        prefs.edit().putString("saved_network_stream_history_v2", savedStreamHistory.joinToString("\n")).apply()

        val resolvedTitle = customTitle.ifBlank {
            normalizedUrl.substringAfterLast('/').substringBefore('?').ifBlank { "Live Network Stream" }
        }
        val item = MediaItem(
            id = "stream_${normalizedUrl.hashCode()}",
            title = resolvedTitle,
            artist = "Network Stream ($streamBufferProfile)",
            duration = 0L,
            size = 0L,
            path = normalizedUrl,
            isVideo = !streamPlayAsAudioOnly,
            folderName = "Network Streams",
            resolution = "4K/HD",
            format = if (normalizedUrl.contains(".m3u8", true)) "HLS LIVE" else "STREAM"
        )
        if (streamPlayAsAudioOnly) {
            viewModel.playMusic(item)
            Toast.makeText(
                context,
                if (isHindi) "🎵 बैकग्राउंड ऑडियो स्ट्रीम शुरू: $resolvedTitle" else "🎵 Playing Audio Stream: $resolvedTitle",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            onPlayStreamVideo(item)
        }
    }

    BackHandler(enabled = subScreen != MeSubScreen.MAIN_LIST) {
        subScreen = MeSubScreen.MAIN_LIST
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        // Sub-Screen Back Header when inside a sub-page
        if (subScreen != MeSubScreen.MAIN_LIST) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidGlassIconOrb(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    size = 40.dp,
                    iconSize = 20.dp,
                    tintColor = accent,
                    glowColor = accent,
                    onClick = { subScreen = MeSubScreen.MAIN_LIST }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = when (subScreen) {
                        MeSubScreen.MAIN_LIST -> if (isHindi) "हाइब्रिड प्लेयर" else "Hybrid Player"
                        MeSubScreen.THEMES_GALLERY -> "${if (isHindi) "थीम्स" else "Themes"} (${StudioThemes.size})"
                        MeSubScreen.VISUALIZER_3D_SETTINGS -> if (isHindi) "3D विज़ुअलाइज़र (15 थीम्स)" else "3D Visualizer & 15 Themes"
                        MeSubScreen.COMPANION_GALLERY -> if (isHindi) "रियल 3D टेडी और मैस्कॉट (${CompanionBreed.entries.size})" else "Real 3D Teddy & Mascots (${CompanionBreed.entries.size})"
                        MeSubScreen.LANGUAGE_PICKER -> if (isHindi) "ऐप की भाषा (25 भाषाएं)" else "App Language (25 Languages)"
                        MeSubScreen.RECYCLE_BIN -> if (isHindi) "रीसायकल बिन (30 दिन)" else "Recycle Bin (30 Days)"
                        MeSubScreen.NETWORK_STREAM_STUDIO -> if (isHindi) "नेटवर्क स्ट्रीम प्रो स्टूडियो" else "Network Stream Pro Studio"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        when (subScreen) {
            // =========================================================================
            // MAIN ME SECTION
            // =========================================================================
            MeSubScreen.MAIN_LIST -> {
                val recycledCount = remember(subScreen) {
                    PermanentDeleteHelper.getRecycledItems(context).size
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
                ) {
                    // 1. HERO BRANDING CARD WITH FEATURE PILLS
                    item {
                        LiquidGlassBox(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 22.dp,
                            tintColor = HybridEmerald,
                            glassAlpha = 0.24f
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_app_custom_logo),
                                        contentDescription = "Hybrid Player",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .border(
                                                1.5.dp,
                                                Brush.linearGradient(listOf(HybridEmerald, HybridCyan)),
                                                RoundedCornerShape(14.dp)
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isHindi) "हाइब्रिड प्लेयर" else "Hybrid Player",
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 25.sp,
                                            letterSpacing = 0.3.sp
                                        )
                                        Text(
                                            text = if (isHindi) {
                                                "8K/4K अल्ट्रा मीडिया इंजन • लिक्विड ग्लास प्रो"
                                            } else {
                                                "8K/4K Ultra Media Engine • Liquid Glass Pro"
                                            },
                                            color = HybridEmerald,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // Feature Showcase Pills inside Hero Card
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val pills = if (isHindi) {
                                        listOf("⚡ 8K/4K इंजन", "💎 लिक्विड ग्लास", "🔊 200% बूस्ट", "🚀 हाइब्रिड शेयर", "🔒 प्राइवेट वॉल्ट")
                                    } else {
                                        listOf("⚡ 8K/4K Engine", "💎 Liquid Glass", "🔊 200% Boost", "🚀 Hybrid Share", "🔒 Dual-PIN Vault")
                                    }
                                    pills.forEach { pill ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(Color.White.copy(alpha = 0.08f))
                                                .border(1.dp, HybridEmerald.copy(alpha = 0.45f), RoundedCornerShape(50))
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = pill,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // =====================================================================
                    // 100% REAL PHOTOREALISTIC 3D PLUSH LION, TEDDY & CHICK MASCOTS SHOWCASE
                    // =====================================================================
                    item {
                        val studioMascots = remember { CompanionBreed.entries }
                        LiquidGlassBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("me_saved_mascots_section"),
                            cornerRadius = 24.dp,
                            tintColor = Color(0xFFFF80AB),
                            glassAlpha = 0.22f
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color.White)
                                                .border(1.5.dp, Color(0xFFFF80AB), RoundedCornerShape(12.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val activeBreed = CompanionBreed.entries.find {
                                                it.displayName == selectedCompanionName || it.name == selectedCompanionName
                                            } ?: CompanionBreed.GOLDEN_LION_DJ
                                            FluffyMascot3DStudioPortrait(
                                                breed = activeBreed,
                                                showStudioBackdrop = true,
                                                animateReels = false,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (isHindi) "रियल 3D शेर, टेडी और पपी मैस्कॉट" else "Real 3D Lion, Teddy & Pets Mascots",
                                                color = Color.White,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 13.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (isHindi) "सक्रिय: $selectedCompanionName" else "Saved Active: $selectedCompanionName",
                                                color = Color(0xFFFF80AB),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    TextButton(
                                        onClick = { subScreen = MeSubScreen.COMPANION_GALLERY },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isHindi) "सभी (${CompanionBreed.entries.size})" else "All (${CompanionBreed.entries.size})",
                                            color = accent,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }

                                // Mascot Resizable Control + Auto-Walk + Speed
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Text(
                                        text = if (isHindi) "आकार:" else "Size:",
                                        color = Color.White.copy(alpha = 0.78f),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                    listOf(
                                        64 to (if (isHindi) "छोटा (S)" else "S (64dp)"),
                                        88 to (if (isHindi) "मध्यम (M)" else "M (88dp)"),
                                        110 to (if (isHindi) "बड़ा (L)" else "L (110dp)"),
                                        132 to (if (isHindi) "विशाल (XL)" else "XL (132dp)"),
                                        148 to "XXL"
                                    ).forEach { (size, label) ->
                                        val isSelectedSize = companionSizeDp == size
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSelectedSize) Color(0xFFFF80AB) else Color.White.copy(alpha = 0.12f))
                                                .clickable { viewModel.setCompanionSize(size) }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (isSelectedSize) Color.Black else Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.5.sp
                                            )
                                        }
                                    }

                                    // Auto-Walk Toggle Chip
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (companionAutoWalk) Color(0xFF69F0AE) else Color.White.copy(alpha = 0.12f))
                                            .clickable { viewModel.setCompanionAutoWalk(!companionAutoWalk) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (companionAutoWalk) "🚶 Walk: ON" else "🚶 Walk: OFF",
                                            color = if (companionAutoWalk) Color.Black else Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.5.sp
                                        )
                                    }

                                    // Speed Multiplier Chip
                                    val speedLabel = when {
                                        companionAnimSpeed >= 1.75f -> "⚡ 2x Speed"
                                        companionAnimSpeed >= 1.35f -> "⚡ 1.5x Speed"
                                        else -> "⚡ 1x Speed"
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFFE040FB).copy(alpha = 0.25f))
                                            .border(1.dp, Color(0xFFE040FB), RoundedCornerShape(12.dp))
                                            .clickable {
                                                val nextSpeed = when {
                                                    companionAnimSpeed < 1.3f -> 1.5f
                                                    companionAnimSpeed < 1.75f -> 2.0f
                                                    else -> 1.0f
                                                }
                                                viewModel.setCompanionAnimSpeed(nextSpeed)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = speedLabel,
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 10.5.sp
                                        )
                                    }
                                }

                                // 8 Live Mascot Gudda Dance Animations Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    com.example.helpers.MascotDanceMode.entries.forEach { mode ->
                                        val isSelectedMode = companionDanceMode.equals(mode.id, ignoreCase = true)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSelectedMode) mode.accentColor else mode.accentColor.copy(alpha = 0.14f))
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isSelectedMode) Color.White else mode.accentColor.copy(alpha = 0.55f),
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .clickable { viewModel.selectCompanionDanceMode(mode.id) }
                                                .padding(horizontal = 9.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = if (isHindi) mode.labelHi else mode.labelEn,
                                                color = if (isSelectedMode) Color.Black else Color.White,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }

                                // 6 Glowing Aura & Particle Effects Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    com.example.helpers.MascotAuraEffect.entries.forEach { effect ->
                                        val isSelectedAura = companionAuraEffect.equals(effect.id, ignoreCase = true)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSelectedAura) effect.primaryColor else effect.primaryColor.copy(alpha = 0.14f))
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isSelectedAura) Color.White else effect.primaryColor.copy(alpha = 0.55f),
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .clickable { viewModel.selectCompanionAuraEffect(effect.id) }
                                                .padding(horizontal = 9.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = if (isHindi) effect.labelHi else effect.labelEn,
                                                color = if (isSelectedAura) Color.Black else Color.White,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    studioMascots.forEach { breed ->
                                        val isSavedActive = breed.displayName == selectedCompanionName || breed.name == selectedCompanionName
                                        LiquidGlassBox(
                                            modifier = Modifier
                                                .width(128.dp)
                                                .testTag("me_mascot_card_${breed.name.lowercase()}"),
                                            cornerRadius = 18.dp,
                                            tintColor = breed.headphoneAccent,
                                            glassAlpha = if (isSavedActive) 0.34f else 0.16f,
                                            isActive = isSavedActive,
                                            onClick = {
                                                viewModel.selectCompanionBreed(breed.displayName)
                                            }
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(8.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(112.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(Color.White)
                                                        .border(
                                                            width = if (isSavedActive) 2.5.dp else 1.dp,
                                                            color = if (isSavedActive) breed.headphoneAccent else Color.White.copy(alpha = 0.28f),
                                                            shape = RoundedCornerShape(14.dp)
                                                        )
                                                ) {
                                                    FluffyMascot3DStudioPortrait(
                                                        breed = breed,
                                                        showStudioBackdrop = true,
                                                        animateReels = isSavedActive,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = breed.displayName,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = breed.cassetteLabel,
                                                    color = breed.headphoneAccent,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.5.sp,
                                                    maxLines = 1
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(50))
                                                        .background(
                                                            if (isSavedActive) breed.headphoneAccent
                                                            else Color.White.copy(alpha = 0.12f)
                                                        )
                                                        .padding(vertical = 3.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = if (isSavedActive) {
                                                            if (isHindi) "सेव है ✓" else "SAVED ✓"
                                                        } else {
                                                            if (isHindi) "सेव करें" else "SAVE"
                                                        },
                                                        color = if (isSavedActive) Color.Black else Color.White,
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 9.5.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ROW 1: Hybrid Share | Private Vault
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CompactGridBoxItem(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Share,
                                title = if (isHindi) "हाइब्रिड शेयर" else "Hybrid Share",
                                subtitle = if (isHindi) "लाइव स्कैनर • रडार" else "Live Scanner • Radar",
                                accentColor = accent,
                                testTag = "me_box_hybrid_share",
                                onClick = onOpenTurboShare
                            )
                            CompactGridBoxItem(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Lock,
                                title = if (isHindi) "प्राइवेट वॉल्ट" else "Private Vault",
                                subtitle = if (isHindi) "PIN सुरक्षित फ़ोल्डर" else "PIN Locked Media",
                                accentColor = Color(0xFFFFB300),
                                testTag = "me_box_private_vault",
                                onClick = onOpenPrivacyVault
                            )
                        }
                    }

                    // ROW 2: Themes (60) | 3D Visualizer
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CompactGridBoxItem(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.ColorLens,
                                title = if (isHindi) "थीम्स (${StudioThemes.size})" else "Themes (${StudioThemes.size})",
                                subtitle = currentThemeName,
                                accentColor = secondary,
                                testTag = "me_box_themes",
                                onClick = { subScreen = MeSubScreen.THEMES_GALLERY }
                            )
                            CompactGridBoxWithToggle(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.AutoAwesome,
                                title = if (isHindi) "3D विज़ुअलाइज़र" else "3D Visualizer",
                                subtitle = if (visualizer3DEnabled) selected3DThemeName else "OFF • 15 Themes",
                                accentColor = Color(0xFF00E5FF),
                                checked = visualizer3DEnabled,
                                onCheckedChange = { viewModel.setVisualizer3DEnabled(it) },
                                onClick = { subScreen = MeSubScreen.VISUALIZER_3D_SETTINGS }
                            )
                        }
                    }

                    // ROW 3: 2x Shake | Real 3D Mascots (12)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CompactGridBoxWithToggle(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Vibration,
                                title = if (isHindi) "2x शेक स्किप" else "2x Shake Skip",
                                subtitle = if (shakeToSkipEnabled) {
                                    if (isHindi) "चालू • कलाई झटकें" else "ON • Wrist Snap"
                                } else {
                                    if (isHindi) "बंद (OFF)" else "OFF (Pocket Safe)"
                                },
                                accentColor = Color(0xFF00E676),
                                checked = shakeToSkipEnabled,
                                onCheckedChange = { viewModel.setDoubleShakeEnabled(it) },
                                onClick = { viewModel.toggleDoubleShakeEnabled() }
                            )
                            CompactGridBoxWithToggle(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Pets,
                                title = if (isHindi) "3D टेडी (${CompanionBreed.entries.size})" else "3D Mascots (${CompanionBreed.entries.size})",
                                subtitle = selectedCompanionName,
                                accentColor = Color(0xFFFF80AB),
                                checked = companionEnabled,
                                onCheckedChange = { viewModel.setCompanionEnabled(it) },
                                onClick = { subScreen = MeSubScreen.COMPANION_GALLERY }
                            )
                        }
                    }

                    // ROW 4: Language (Hindi / English) | PDF Reader
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CompactGridBoxItem(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Language,
                                title = if (isHindi) "भाषा (25)" else "Language (25)",
                                subtitle = selectedLanguage,
                                accentColor = Color(0xFF7C4DFF),
                                testTag = "me_box_language",
                                onClick = { subScreen = MeSubScreen.LANGUAGE_PICKER }
                            )
                            CompactGridBoxItem(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.PictureAsPdf,
                                title = if (isHindi) "पीडीएफ रीडर" else "PDF Reader",
                                subtitle = if (isHindi) "फुल-स्क्रीन ज़ूम" else "Full-Screen Zoom",
                                accentColor = Color(0xFFFF5252),
                                testTag = "me_box_pdf_reader",
                                onClick = onOpenPdfReader
                            )
                        }
                    }

                    // ROW 5: Video to Audio | Storage Cleaner
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CompactGridBoxItem(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.GraphicEq,
                                title = if (isHindi) "वीडियो से ऑडियो" else "Video to Audio",
                                subtitle = if (isHindi) "MP3/M4A निकालें" else "Extract MP3/M4A",
                                accentColor = Color(0xFF18FFFF),
                                testTag = "me_box_mp3_converter",
                                onClick = onOpenMp3Converter
                            )
                            CompactGridBoxItem(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.CleaningServices,
                                title = if (isHindi) "स्टोरेज क्लीनर" else "Storage Cleaner",
                                subtitle = if (isHindi) "बड़ी और डुप्लीकेट फ़ाइलें" else "Large & Duplicates",
                                accentColor = Color(0xFF69F0AE),
                                testTag = "me_box_cleaner",
                                onClick = onOpenCleanerHub
                            )
                        }
                    }

                    // ROW 6: Recycle Bin (30 Days) | Network Stream Pro Studio
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CompactGridBoxItem(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.DeleteSweep,
                                title = if (isHindi) "रीसायकल बिन" else "Recycle Bin",
                                subtitle = if (isHindi) "$recycledCount आइटम • 30 दिन" else "$recycledCount Items • 30 Days",
                                accentColor = Color(0xFFFFAB40),
                                testTag = "me_box_recycle_bin",
                                onClick = { subScreen = MeSubScreen.RECYCLE_BIN }
                            )
                            CompactGridBoxItem(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Link,
                                title = if (isHindi) "नेटवर्क स्ट्रीम" else "Network Stream",
                                subtitle = if (isHindi) "4K/HD लाइव • 8 चैनल" else "4K/HD URL & 8 Channels",
                                accentColor = HybridGold,
                                testTag = "me_box_stream_url",
                                onClick = { subScreen = MeSubScreen.NETWORK_STREAM_STUDIO }
                            )
                        }
                    }

                    // =================================================================
                    // SINGLE HORIZONTAL ROW AT THE VERY BOTTOM:
                    // Rate Us | Report | Latest | Privacy Policy
                    // =================================================================
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CompactFooterActionBox(
                                icon = Icons.Default.StarRate,
                                label = if (isHindi) "रेट करें" else "Rate Us",
                                accentColor = Color(0xFFFFD54F),
                                modifier = Modifier.weight(1f),
                                onClick = { activeBottomModal = "rate_us" }
                            )
                            CompactFooterActionBox(
                                icon = Icons.Default.BugReport,
                                label = if (isHindi) "रिपोर्ट" else "Report",
                                accentColor = Color(0xFFFF8A65),
                                modifier = Modifier.weight(1f),
                                onClick = { activeBottomModal = "report" }
                            )
                            CompactFooterActionBox(
                                icon = Icons.Default.NewReleases,
                                label = if (isHindi) "नवीनतम" else "Latest",
                                accentColor = accent,
                                modifier = Modifier.weight(1f),
                                onClick = { activeBottomModal = "latest" }
                            )
                            CompactFooterActionBox(
                                icon = Icons.Default.Policy,
                                label = if (isHindi) "गोपनीयता" else "Privacy Policy",
                                accentColor = secondary,
                                modifier = Modifier.weight(1f),
                                onClick = { activeBottomModal = "privacy_policy" }
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // SUB-SCREEN: 60 STUDIO THEMES + PERMANENT GALLERY PHOTO WALLPAPER
            // =========================================================================
            MeSubScreen.THEMES_GALLERY -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    LiquidGlassBox(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        tintColor = HybridEmerald,
                        isActive = customWallpaperUri != null,
                        onClick = {
                            meGalleryWallpaperLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isHindi) "📸 गैलरी से अपनी फोटो लगाएं (Permanent)" else "📸 Set Photo from Phone Gallery (Permanent)",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (customWallpaperUri != null) {
                                        if (isHindi) "आपकी फोटो सभी स्क्रीन पर लगी हुई है • बदलने के लिए टैप करें" else "Custom photo active across all screens • Tap to change"
                                    } else {
                                        if (isHindi) "मेन स्क्रीन और हर जगह अपनी फोटो लगाएं" else "Apply your own photo permanently across Video, Music & Me"
                                    },
                                    color = HybridEmerald,
                                    fontSize = 11.sp
                                )
                            }
                            if (customWallpaperUri != null) {
                                TextButton(
                                    onClick = {
                                        viewModel.setCustomWallpaper(null)
                                        viewModel.selectTheme("Hybrid Emerald")
                                    }
                                ) {
                                    Text(
                                        text = if (isHindi) "हटाएं" else "Reset",
                                        color = Color(0xFFFF5252),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 145.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(StudioThemes, key = { it.name }) { theme ->
                            val isSelected = theme.name == currentThemeName
                            LiquidGlassBox(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 18.dp,
                                tintColor = theme.primary,
                                isActive = isSelected,
                                onClick = { viewModel.selectTheme(theme.name) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(theme.primary)
                                            .border(2.dp, theme.secondary, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = theme.name,
                                            color = Color.White,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (isSelected) "Active" else "Tap to apply",
                                            color = if (isSelected) theme.primary else Color.White.copy(alpha = 0.6f),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // SUB-SCREEN: 3D VISUALIZER ON/OFF + 15 THEMES
            // =========================================================================
            MeSubScreen.VISUALIZER_3D_SETTINGS -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    StackedFolderBoxWithToggle(
                        icon = Icons.Default.AutoAwesome,
                        title = "3D Audio Visualizer Effect",
                        subtitle = if (visualizer3DEnabled) "Enabled in Audio Player" else "Disabled Permanently",
                        accentColor = accent,
                        checked = visualizer3DEnabled,
                        onCheckedChange = { viewModel.setVisualizer3DEnabled(it) },
                        onClick = { viewModel.toggleVisualizer3DEnabled() }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 145.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(Music3DThemeEngine.THEMES_3D, key = { it.id }) { spec ->
                            val isSelected = visualizer3DEnabled && spec.name == selected3DThemeName
                            LiquidGlassBox(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 18.dp,
                                tintColor = spec.primaryColor,
                                isActive = isSelected,
                                onClick = {
                                    viewModel.setVisualizer3DEnabled(true)
                                    viewModel.select3DMusicTheme(spec.name)
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(spec.primaryColor)
                                            .border(2.dp, spec.secondaryColor, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = spec.name,
                                        color = Color.White,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // SUB-SCREEN: 12 REAL PHOTOREALISTIC 3D PLUSH MASCOTS GALLERY
            // =========================================================================
            MeSubScreen.COMPANION_GALLERY -> {
                val breeds = CompanionBreed.entries
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isHindi) "मैस्कॉट आकार:" else "Mascot Size:",
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                        listOf(
                            64 to (if (isHindi) "छोटा (S)" else "S (64dp)"),
                            88 to (if (isHindi) "मध्यम (M)" else "M (88dp)"),
                            110 to (if (isHindi) "बड़ा (L)" else "L (110dp)"),
                            132 to (if (isHindi) "विशाल (XL)" else "XL (132dp)"),
                            148 to "XXL (148dp)"
                        ).forEach { (size, label) ->
                            val isSelectedSize = companionSizeDp == size
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelectedSize) Color(0xFFFF80AB) else Color.White.copy(alpha = 0.14f))
                                    .clickable { viewModel.setCompanionSize(size) }
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelectedSize) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        com.example.helpers.MascotDanceMode.entries.forEach { mode ->
                            val isSelectedMode = companionDanceMode.equals(mode.id, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelectedMode) mode.accentColor else mode.accentColor.copy(alpha = 0.15f))
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelectedMode) Color.White else mode.accentColor.copy(alpha = 0.55f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { viewModel.selectCompanionDanceMode(mode.id) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = if (isHindi) mode.labelHi else mode.labelEn,
                                    color = if (isSelectedMode) Color.Black else Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.5.sp
                                )
                            }
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 152.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 28.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(breeds.size) { index ->
                            val breed = breeds[index]
                            val isSelected = breed.displayName == selectedCompanionName || breed.name == selectedCompanionName
                            LiquidGlassBox(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 20.dp,
                                tintColor = breed.headphoneAccent,
                                glassAlpha = if (isSelected) 0.32f else 0.16f,
                                isActive = isSelected,
                                onClick = { viewModel.selectCompanionBreed(breed.displayName) }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(156.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color.White)
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) breed.headphoneAccent else Color.White.copy(alpha = 0.22f),
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                    ) {
                                        FluffyMascot3DStudioPortrait(
                                            breed = breed,
                                            showStudioBackdrop = true,
                                            animateReels = isSelected,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = breed.displayName,
                                        color = Color.White,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "100% Real 3D Plush • ${breed.cassetteLabel}",
                                        color = breed.headphoneAccent,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(50))
                                            .background(
                                                if (isSelected) breed.headphoneAccent
                                                else Color.White.copy(alpha = 0.12f)
                                            )
                                            .padding(vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isSelected) {
                                                if (isHindi) "सक्रिय और सेव है ✓" else "ACTIVE & SAVED ✓"
                                            } else {
                                                if (isHindi) "सेव और लागू करें" else "SAVE & APPLY"
                                            },
                                            color = if (isSelected) Color.Black else Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // SUB-SCREEN: REAL APP LANGUAGES (SYSTEM DEFAULT + HINDI + ENGLISH ONLY)
            // =========================================================================
            MeSubScreen.LANGUAGE_PICKER -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        LiquidGlassBox(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 18.dp,
                            tintColor = accent
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = if (isHindi) "🌐 25+ असली काम करने वाली भाषाएं (Indian + Global)" else "🌐 25+ Real Working Languages (Indian & Global)",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isHindi) {
                                        "शुरू में सिस्टम डिफ़ॉल्ट (Auto) रहता है। किसी भी भाषा पर टैप करते ही पूरी ऐप तुरंत उसी भाषा में बदल जाएगी।"
                                    } else {
                                        "Starts with System Default (Auto) on install. Tap any of the 25 languages below to switch the entire app UI immediately."
                                    },
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }

                    items(WorldLanguageEngine.SUPPORTED_LANGUAGE_SPECS, key = { it.code }) { spec ->
                        val lang = "${spec.countryFlag} ${spec.displayName}"
                        val isSelected = lang == selectedLanguage ||
                            (spec.code == "auto" && selectedLanguage.contains("System Default", ignoreCase = true)) ||
                            (spec.code != "auto" && selectedLanguage.contains(spec.displayName, ignoreCase = true))
                        val subtitleText = spec.regionName
                        StackedFolderBoxItem(
                            icon = Icons.Default.Language,
                            title = lang,
                            subtitle = if (isSelected) "✓ $subtitleText" else subtitleText,
                            accentColor = if (isSelected) accent else Color.White.copy(alpha = 0.55f),
                            testTag = "lang_item_${spec.code}",
                            onClick = {
                                viewModel.selectLanguage(lang)
                                Toast.makeText(
                                    context,
                                    WorldLanguageEngine.getLanguageActivatedToast(lang),
                                    Toast.LENGTH_SHORT
                                ).show()
                                subScreen = MeSubScreen.MAIN_LIST
                            }
                        )
                    }
                }
            }

            // =========================================================================
            // SUB-SCREEN: 30-DAY RECYCLE BIN
            // =========================================================================
            MeSubScreen.RECYCLE_BIN -> {
                var recycledItems by remember {
                    mutableStateOf(PermanentDeleteHelper.getRecycledItems(context))
                }
                if (recycledItems.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isHindi) "रीसायकल बिन खाली है" else "Recycle Bin is Empty",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(recycledItems, key = { it.mediaItem.id }) { entry ->
                            LiquidGlassBox(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 18.dp,
                                tintColor = accent
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.mediaItem.cleanTitle,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${entry.mediaItem.formattedSize} • ${entry.daysRemaining} days left",
                                            color = Color.White.copy(alpha = 0.65f),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            PermanentDeleteHelper.restoreFromRecycleBin(context, entry.mediaItem.id)
                                            recycledItems = PermanentDeleteHelper.getRecycledItems(context)
                                            viewModel.loadMedia()
                                            Toast.makeText(context, "Restored ${entry.mediaItem.cleanTitle}", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = accent,
                                            contentColor = Color.Black
                                        )
                                    ) {
                                        Icon(Icons.Default.RestoreFromTrash, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Restore", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // SUB-SCREEN: NETWORK STREAM PRO STUDIO (DIRECT URL + 8 WORKING HD CHANNELS + HISTORY)
            // =========================================================================
            MeSubScreen.NETWORK_STREAM_STUDIO -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 1. Direct Stream URL Box + Paste Link + Buffer & Mode Controls
                    item {
                        LiquidGlassBox(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 22.dp,
                            tintColor = HybridGold,
                            isActive = true
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(
                                            imageVector = Icons.Default.LiveTv,
                                            contentDescription = null,
                                            tint = HybridGold,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = if (isHindi) "डायरेक्ट वीडियो / ऑडियो स्ट्रीम चलाएं" else "Direct Video & Live Stream Player",
                                                color = Color.White,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "MP4 • MKV • WebM • HLS (.m3u8) • DASH • MP3",
                                                color = HybridGold,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.5.sp
                                            )
                                        }
                                    }

                                    // 1-Tap Clipboard Paste Button
                                    TextButton(
                                        onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            val clipText = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                                            if (clipText.isNotEmpty()) {
                                                streamUrlInput = clipText
                                                Toast.makeText(
                                                    context,
                                                    if (isHindi) "क्लिपबोर्ड से लिंक पेस्ट हो गया!" else "Stream URL pasted from Clipboard!",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    if (isHindi) "क्लिपबोर्ड खाली है" else "Clipboard is empty",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = accent, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isHindi) "पेस्ट करें" else "Paste URL",
                                            color = accent,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = streamUrlInput,
                                    onValueChange = { streamUrlInput = it },
                                    singleLine = true,
                                    label = {
                                        Text(
                                            if (isHindi) "https://... (.mp4, .m3u8, .mkv, .mp3)" else "https://... (.mp4, .m3u8, .mkv, .mp3)"
                                        )
                                    },
                                    trailingIcon = {
                                        if (streamUrlInput.isNotEmpty()) {
                                            IconButton(onClick = { streamUrlInput = "" }) {
                                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.White.copy(alpha = 0.7f))
                                            }
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = HybridGold,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = streamTitleInput,
                                    onValueChange = { streamTitleInput = it },
                                    singleLine = true,
                                    label = {
                                        Text(
                                            if (isHindi) "स्ट्रीम का नाम (वैकल्पिक)" else "Custom Stream Title (Optional)"
                                        )
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = accent,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Buffer Profile Pills
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        "Smooth 4K Buffer",
                                        "Low-Latency Live",
                                        "Turbo Pre-Load"
                                    ).forEach { profile ->
                                        val selected = streamBufferProfile == profile
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (selected) HybridGold else Color.White.copy(alpha = 0.12f))
                                                .clickable { streamBufferProfile = profile }
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Text(
                                                text = profile,
                                                color = if (selected) Color.Black else Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.5.sp
                                            )
                                        }
                                    }
                                }

                                // Video vs Background Audio Mode + Play Button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(
                                                if (streamPlayAsAudioOnly) HybridCyan.copy(alpha = 0.24f)
                                                else Color.White.copy(alpha = 0.10f)
                                            )
                                            .border(
                                                1.dp,
                                                if (streamPlayAsAudioOnly) HybridCyan else Color.White.copy(alpha = 0.25f),
                                                RoundedCornerShape(14.dp)
                                            )
                                            .clickable { streamPlayAsAudioOnly = !streamPlayAsAudioOnly }
                                            .padding(horizontal = 12.dp, vertical = 12.dp)
                                    ) {
                                        Text(
                                            text = if (streamPlayAsAudioOnly) {
                                                if (isHindi) "🎵 ऑडियो मोड" else "🎵 Audio Mode"
                                            } else {
                                                if (isHindi) "🎬 वीडियो मोड" else "🎬 Video Mode"
                                            },
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            launchNetworkStreamUrl(streamUrlInput, streamTitleInput)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = accent,
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isHindi) "अभी स्ट्रीम चलाएं" else "Play Stream Now",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. 8 BUILT-IN 100% WORKING HD / 4K LIVE & CINEMA STREAMS (1-TAP PLAY)
                    item {
                        Text(
                            text = if (isHindi) {
                                "🎬 1-टैप लाइव 4K/HD स्ट्रीम चैनल्स (तुरंत चलाएं)"
                            } else {
                                "🎬 1-Tap Working 4K / Full HD Stream Channels"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }

                    items(VERIFIED_WORKING_HD_STREAMS, key = { it.url }) { channel ->
                        LiquidGlassBox(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 18.dp,
                            tintColor = channel.accentColor,
                            onClick = {
                                streamUrlInput = channel.url
                                streamTitleInput = channel.title
                                launchNetworkStreamUrl(channel.url, channel.title)
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(channel.accentColor.copy(alpha = 0.22f))
                                        .border(1.dp, channel.accentColor, RoundedCornerShape(13.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayCircleFilled,
                                        contentDescription = channel.title,
                                        tint = channel.accentColor,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = channel.title,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = channel.subtitle,
                                        color = Color.White.copy(alpha = 0.70f),
                                        fontSize = 10.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(channel.accentColor)
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = channel.badge,
                                        color = Color.Black,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }

                    // 3. RECENT STREAM HISTORY & SAVED BOOKMARKS
                    if (savedStreamHistory.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isHindi) "🕒 हाल ही में चलाए गए स्ट्रीम URL" else "🕒 Recent Stream History",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.5.sp
                                )
                                TextButton(
                                    onClick = {
                                        savedStreamHistory.clear()
                                        prefs.edit().remove("saved_network_stream_history_v2").apply()
                                    }
                                ) {
                                    Text(
                                        text = if (isHindi) "इतिहास साफ़ करें" else "Clear All",
                                        color = Color(0xFFFF5252),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                        }

                        items(savedStreamHistory, key = { it }) { savedUrl ->
                            LiquidGlassBox(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 16.dp,
                                tintColor = accent,
                                onClick = {
                                    streamUrlInput = savedUrl
                                    launchNetworkStreamUrl(savedUrl)
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = savedUrl,
                                        color = Color.White,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            savedStreamHistory.remove(savedUrl)
                                            prefs.edit()
                                                .putString("saved_network_stream_history_v2", savedStreamHistory.joinToString("\n"))
                                                .apply()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Delete",
                                            tint = Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
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

    // Footer Dialogs for Rate Us / Report / Latest / Privacy Policy
    if (activeBottomModal != null) {
        val modalType = activeBottomModal!!
        AlertDialog(
            onDismissRequest = { activeBottomModal = null },
            containerColor = Color(0xFF0E1525),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.85f),
            title = {
                Text(
                    text = when (modalType) {
                        "rate_us" -> if (isHindi) "रेट करें" else "Rate Us"
                        "report" -> if (isHindi) "रिपोर्ट" else "Report"
                        "latest" -> if (isHindi) "नवीनतम वर्ज़न" else "Latest Version"
                        else -> if (isHindi) "गोपनीयता नीति" else "Privacy Policy"
                    },
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Text(
                    text = when (modalType) {
                        "rate_us" -> "Enjoying Hybrid Player? Share your 5-star feedback or tell us how we can make your experience even better."
                        "report" -> "Found an issue with a media format, subtitle, or audio track? Send a diagnostic report directly to our engineering team."
                        "latest" -> "Hybrid Player Latest Build\n• Real 3D Plush Studio Mascots with Transparent Cutout & Beat Dance\n• Multi-Tech Hybrid Share with Live Camera Barcode/QR Scanner & 254-IP Subnet Radar\n• Network Stream Pro Studio with 8 Built-In 4K/HD Channels\n• System Default + Native Hindi & English Language Engine"
                        else -> "100% Local On-Device Privacy:\nAll your videos, songs, PDFs, and private vault files remain strictly on your device. No personal media is ever uploaded to external servers."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (modalType == "report") {
                            runCatching {
                                val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:support@hybridplayer.app")
                                    putExtra(Intent.EXTRA_SUBJECT, "Hybrid Player Feedback / Issue Report")
                                }
                                context.startActivity(emailIntent)
                            }
                        }
                        activeBottomModal = null
                    }
                ) {
                    Text("OK", color = accent, fontWeight = FontWeight.ExtraBold)
                }
            }
        )
    }
}

@Composable
private fun CompactGridBoxItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    LiquidGlassBox(
        modifier = modifier
            .height(74.dp)
            .testTag(testTag),
        cornerRadius = 18.dp,
        tintColor = accentColor,
        glassAlpha = 0.20f,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(accentColor.copy(alpha = 0.20f))
                    .border(1.dp, accentColor.copy(alpha = 0.50f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CompactGridBoxWithToggle(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    LiquidGlassBox(
        modifier = modifier.height(74.dp),
        cornerRadius = 18.dp,
        tintColor = accentColor,
        glassAlpha = if (checked) 0.28f else 0.18f,
        isActive = checked,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.20f))
                    .border(1.dp, accentColor.copy(alpha = 0.50f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(7.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = if (checked) accentColor else Color.White.copy(alpha = 0.68f),
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.scale(0.72f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = accentColor
                )
            )
        }
    }
}

@Composable
private fun StackedFolderBoxItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    LiquidGlassBox(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        cornerRadius = 18.dp,
        tintColor = accentColor,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(accentColor.copy(alpha = 0.18f))
                    .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.68f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
private fun StackedFolderBoxWithToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    LiquidGlassBox(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        tintColor = accentColor,
        isActive = checked,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(accentColor.copy(alpha = 0.18f))
                    .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.68f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = accentColor
                )
            )
        }
    }
}

@Composable
private fun CompactFooterActionBox(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    LiquidGlassBox(
        modifier = modifier,
        cornerRadius = 14.dp,
        tintColor = accentColor,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
