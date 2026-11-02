package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.ui.theme.HybridCyan
import com.example.ui.theme.HybridEmerald
import com.example.ui.theme.HybridGold
import com.example.ui.theme.StudioThemes
import com.example.viewmodel.PlayerViewModel
import java.util.Locale

private enum class MeSubScreen {
    MAIN_LIST,
    THEMES_GALLERY,
    VISUALIZER_3D_SETTINGS,
    COMPANION_GALLERY,
    LANGUAGE_PICKER,
    RECYCLE_BIN
}

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
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val isHindi = remember(selectedLanguage) {
        selectedLanguage.contains("Hindi", ignoreCase = true) ||
            selectedLanguage.contains("हिन्दी") ||
            Locale.getDefault().language == "hi"
    }
    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    val currentThemeName by viewModel.activeTheme.collectAsState()
    val visualizer3DEnabled by viewModel.visualizer3DEnabled.collectAsState()
    val selected3DThemeName by viewModel.active3DMusicTheme.collectAsState()
    val shakeToSkipEnabled by viewModel.doubleShakeEnabled.collectAsState()
    val companionEnabled by viewModel.companionEnabled.collectAsState()
    val selectedCompanionName by viewModel.companionBreed.collectAsState()

    var subScreen by remember { mutableStateOf(MeSubScreen.MAIN_LIST) }
    var activeBottomModal by remember { mutableStateOf<String?>(null) }
    var showStreamUrlDialog by remember { mutableStateOf(false) }
    var streamUrlInput by remember { mutableStateOf("") }

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
                        MeSubScreen.VISUALIZER_3D_SETTINGS -> "3D Visualizer & 15 Themes"
                        MeSubScreen.COMPANION_GALLERY -> "Puppy & Penguin Companions (20)"
                        MeSubScreen.LANGUAGE_PICKER -> "${if (isHindi) "भाषा" else "Language"} (${MultiLanguageHelper.SUPPORTED_LANGUAGES.size})"
                        MeSubScreen.RECYCLE_BIN -> if (isHindi) "रीसायकल बिन (30 दिन)" else "Recycle Bin (30 Days)"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        when (subScreen) {
            // =========================================================================
            // MAIN ME SECTION:
            // 1. Prominent Hybrid Player Hero Banner with Feature Pills
            // 2. 2-Column Side-by-Side Grid of Compact Liquid-Glass Feature Boxes
            //    ("एक बॉक्स आगे, एक बॉक्स पीछे, फिर उसके नीचे दो बॉक्स")
            // 3. Single Bottom 4-Action Row (Rate Us | Report | Latest | Privacy Policy)
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
                    // SAVED 3D FLUFFY PUPPY & TEDDY BEAR MASCOT STUDIO SHOWCASE (IN ME SECTION)
                    // =====================================================================
                    item {
                        val fluffyMascots = remember { CompanionBreed.FLUFFY_CASSETTE_MASCOTS }
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
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFFFF80AB).copy(alpha = 0.22f))
                                                .border(1.dp, Color(0xFFFF80AB).copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val activeBreed = CompanionBreed.entries.find {
                                                it.displayName == selectedCompanionName || it.name == selectedCompanionName
                                            } ?: CompanionBreed.HONEY_BEAT_TEDDY
                                            FluffyMascot3DStudioPortrait(
                                                breed = activeBreed,
                                                showStudioBackdrop = false,
                                                animateReels = false,
                                                modifier = Modifier.size(34.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (isHindi) "3D पपी और टेडी मैस्कॉट (Saved)" else "3D Puppy & Teddy Mascots (Saved)",
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
                                            text = if (isHindi) "सभी देखें" else "All (${CompanionBreed.entries.size})",
                                            color = accent,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    fluffyMascots.forEach { breed ->
                                        val isSavedActive = breed.displayName == selectedCompanionName || breed.name == selectedCompanionName
                                        LiquidGlassBox(
                                            modifier = Modifier
                                                .width(126.dp)
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
                                                        .size(96.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .border(
                                                            width = if (isSavedActive) 2.dp else 1.dp,
                                                            color = if (isSavedActive) breed.headphoneAccent else Color.White.copy(alpha = 0.22f),
                                                            shape = RoundedCornerShape(14.dp)
                                                        )
                                                ) {
                                                    FluffyMascot3DStudioPortrait(
                                                        breed = breed,
                                                        showStudioBackdrop = true,
                                                        animateReels = false,
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
                                subtitle = if (isHindi) "तेज़ फ़ाइल ट्रांसफर" else "Send & Receive QR",
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

                    // ROW 2: Themes (56) | 3D Visualizer
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

                    // ROW 3: 2x Shake (Default OFF, Wrist-Snap Filtered) | Companions (20)
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
                                title = if (isHindi) "टेडी/पपी (${CompanionBreed.entries.size})" else "Mascots (${CompanionBreed.entries.size})",
                                subtitle = selectedCompanionName,
                                accentColor = Color(0xFFFF80AB),
                                checked = companionEnabled,
                                onCheckedChange = { viewModel.setCompanionEnabled(it) },
                                onClick = { subScreen = MeSubScreen.COMPANION_GALLERY }
                            )
                        }
                    }

                    // ROW 4: Language (56) | PDF Reader
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CompactGridBoxItem(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Language,
                                title = if (isHindi) "भाषा (56)" else "Language (56)",
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

                    // ROW 6: Recycle Bin (30 Days) | Network Stream URL
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
                                subtitle = if (isHindi) "URL वीडियो चलाएं" else "Play Direct URL",
                                accentColor = HybridGold,
                                testTag = "me_box_stream_url",
                                onClick = { showStreamUrlDialog = true }
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
            // SUB-SCREEN: 56 STUDIO THEMES GALLERY
            // =========================================================================
            MeSubScreen.THEMES_GALLERY -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 145.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize()
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
            // SUB-SCREEN: 3D FLUFFY CASSETTE PUPPIES, TEDDY BEARS & PENGUINS GALLERY
            // =========================================================================
            MeSubScreen.COMPANION_GALLERY -> {
                val breeds = CompanionBreed.entries
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
                                        .height(132.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
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
                                    text = when {
                                        breed.isPenguin -> "3D Studio Penguin"
                                        breed.isTeddyBear -> "3D Fluffy Teddy • ${breed.cassetteLabel}"
                                        else -> "3D Fluffy Puppy • ${breed.cassetteLabel}"
                                    },
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
                                            if (isHindi) "Me में सेव है ✓" else "SAVED IN ME ✓"
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

            // =========================================================================
            // SUB-SCREEN: 56 GLOBAL LANGUAGES PICKER
            // =========================================================================
            MeSubScreen.LANGUAGE_PICKER -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(MultiLanguageHelper.SUPPORTED_LANGUAGES) { lang ->
                        val isSelected = lang == selectedLanguage
                        StackedFolderBoxItem(
                            icon = Icons.Default.Language,
                            title = lang,
                            subtitle = if (isSelected) "Active Language" else "Tap to switch language",
                            accentColor = if (isSelected) accent else Color.White.copy(alpha = 0.5f),
                            testTag = "lang_item_$lang",
                            onClick = {
                                viewModel.selectLanguage(lang)
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
        }
    }

    if (showStreamUrlDialog) {
        AlertDialog(
            onDismissRequest = { showStreamUrlDialog = false },
            containerColor = Color(0xFF0E1525),
            title = {
                Text(
                    text = if (isHindi) "नेटवर्क स्ट्रीम URL चलाएं" else "Play Network Stream URL",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                OutlinedTextField(
                    value = streamUrlInput,
                    onValueChange = { streamUrlInput = it },
                    singleLine = true,
                    label = { Text("https://... (.mp4, .mkv, .m3u8)") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = streamUrlInput.trim()
                        if (trimmed.isNotEmpty()) {
                            val item = MediaItem(
                                id = "stream_${trimmed.hashCode()}",
                                title = trimmed.substringAfterLast('/').ifBlank { "Network Stream" },
                                artist = "Network Stream",
                                duration = 0L,
                                size = 0L,
                                path = trimmed,
                                isVideo = true,
                                folderName = "Streams",
                                resolution = "4K",
                                format = "STREAM"
                            )
                            showStreamUrlDialog = false
                            onPlayStreamVideo(item)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                ) {
                    Text(if (isHindi) "चलाएं" else "Play Now", fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStreamUrlDialog = false }) {
                    Text(if (isHindi) "रद्द करें" else "Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
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
                        "latest" -> "Hybrid Player v4.6 Latest Build\n• Instant MediaStore Engine + Pending Movie Playback\n• Full-Screen Video & PDF Engine\n• True Non-Repeating Shuffle Queue\n• 15 3D Audio Visualizer Themes with Permanent Switch\n• High-Speed Hybrid Share with Real Handshake"
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
