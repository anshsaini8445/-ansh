package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.helpers.MultiLanguageHelper
import com.example.helpers.Music3DThemeEngine
import com.example.helpers.PermanentDeleteHelper
import com.example.ui.components.LiquidGlassBox
import com.example.ui.components.LiquidGlassIconOrb
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
    val isHindi = remember { Locale.getDefault().language == "hi" }
    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    val currentThemeName by viewModel.activeTheme.collectAsState()
    val visualizer3DEnabled by viewModel.visualizer3DEnabled.collectAsState()
    val selected3DThemeName by viewModel.active3DMusicTheme.collectAsState()
    val shakeToSkipEnabled by viewModel.doubleShakeEnabled.collectAsState()
    val companionEnabled by viewModel.companionEnabled.collectAsState()
    val selectedCompanionName by viewModel.companionBreed.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()

    var subScreen by remember { mutableStateOf(MeSubScreen.MAIN_LIST) }
    var activeBottomModal by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = subScreen != MeSubScreen.MAIN_LIST) {
        subScreen = MeSubScreen.MAIN_LIST
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (subScreen != MeSubScreen.MAIN_LIST) {
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
            } else {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(accent.copy(alpha = 0.16f))
                        .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_app_custom_logo),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
            }

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

        when (subScreen) {
            // =========================================================================
            // MAIN ME SECTION: STACKED VERTICAL FOLDER-STYLE BOXES (एक के ऊपर एक)
            // + SINGLE BOTTOM INFO ROW (Rate Us | Report | Latest | Privacy Policy)
            // =========================================================================
            MeSubScreen.MAIN_LIST -> {
                val recycledCount = remember(subScreen) {
                    PermanentDeleteHelper.getRecycledItems(context).size
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    // 1. Hybrid Share (Send & Receive)
                    item {
                        StackedFolderBoxItem(
                            icon = Icons.Default.Share,
                            title = if (isHindi) "हाइब्रिड शेयर (Hybrid Share)" else "Hybrid Share",
                            subtitle = if (isHindi) "भेजें • प्राप्त करें • लाइव QR और रडार" else "Send • Receive • Live QR & Radar",
                            accentColor = accent,
                            testTag = "me_box_hybrid_share",
                            onClick = onOpenTurboShare
                        )
                    }

                    // 2. Private Folder Vault
                    item {
                        StackedFolderBoxItem(
                            icon = Icons.Default.Lock,
                            title = if (isHindi) "प्राइवेट फ़ोल्डर (Private Vault)" else "Private Folder Vault",
                            subtitle = "PIN Protected Hidden Videos & Songs",
                            accentColor = Color(0xFFFFB300),
                            testTag = "me_box_private_vault",
                            onClick = onOpenPrivacyVault
                        )
                    }

                    // 3. Themes (56 Studio Themes)
                    item {
                        StackedFolderBoxItem(
                            icon = Icons.Default.ColorLens,
                            title = if (isHindi) "थीम्स (Themes)" else "Themes",
                            subtitle = "$currentThemeName • ${StudioThemes.size} Custom Themes",
                            accentColor = secondary,
                            testTag = "me_box_themes",
                            onClick = { subScreen = MeSubScreen.THEMES_GALLERY }
                        )
                    }

                    // 4. 3D Visualizer Permanent ON/OFF + 15 3D Themes
                    item {
                        StackedFolderBoxWithToggle(
                            icon = Icons.Default.AutoAwesome,
                            title = "3D Audio Visualizer",
                            subtitle = if (visualizer3DEnabled) "ON ($selected3DThemeName) • Tap for 15 Themes" else "OFF • Disabled permanently",
                            accentColor = Color(0xFF00E5FF),
                            checked = visualizer3DEnabled,
                            onCheckedChange = { viewModel.setVisualizer3DEnabled(it) },
                            onClick = { subScreen = MeSubScreen.VISUALIZER_3D_SETTINGS }
                        )
                    }

                    // 5. 2x Shake to Next Song
                    item {
                        StackedFolderBoxWithToggle(
                            icon = Icons.Default.Vibration,
                            title = if (isHindi) "2x शेक से अगला गाना" else "2x Shake to Next Song",
                            subtitle = if (shakeToSkipEnabled) "Enabled • Shake phone 2x to skip song" else "Disabled",
                            accentColor = Color(0xFF00E676),
                            checked = shakeToSkipEnabled,
                            onCheckedChange = { viewModel.setDoubleShakeEnabled(it) },
                            onClick = { viewModel.toggleDoubleShakeEnabled() }
                        )
                    }

                    // 6. Puppy & Penguin Companions (20 Breeds)
                    item {
                        StackedFolderBoxWithToggle(
                            icon = Icons.Default.Pets,
                            title = if (isHindi) "पपी और पेंगुइन साथी (20)" else "Puppy & Penguin Companions (20)",
                            subtitle = "$selectedCompanionName • Tap to choose breed",
                            accentColor = Color(0xFFFF80AB),
                            checked = companionEnabled,
                            onCheckedChange = { viewModel.setCompanionEnabled(it) },
                            onClick = { subScreen = MeSubScreen.COMPANION_GALLERY }
                        )
                    }

                    // 7. World Languages (56 Languages)
                    item {
                        StackedFolderBoxItem(
                            icon = Icons.Default.Language,
                            title = if (isHindi) "भाषा (Language)" else "Language",
                            subtitle = "$selectedLanguage • ${MultiLanguageHelper.SUPPORTED_LANGUAGES.size} Global Languages",
                            accentColor = Color(0xFF7C4DFF),
                            testTag = "me_box_language",
                            onClick = { subScreen = MeSubScreen.LANGUAGE_PICKER }
                        )
                    }

                    // 8. PDF Reader (Full-Screen Native Renderer)
                    item {
                        StackedFolderBoxItem(
                            icon = Icons.Default.PictureAsPdf,
                            title = if (isHindi) "पीडीएफ रीडर (PDF Reader)" else "PDF Reader",
                            subtitle = "Full-Screen Zoomable Document Viewer",
                            accentColor = Color(0xFFFF5252),
                            testTag = "me_box_pdf_reader",
                            onClick = onOpenPdfReader
                        )
                    }

                    // 9. Video to MP3 Audio Extractor
                    item {
                        StackedFolderBoxItem(
                            icon = Icons.Default.GraphicEq,
                            title = if (isHindi) "वीडियो से ऑडियो कन्वर्टर" else "Video to Audio Converter",
                            subtitle = "Extract M4A / MP3 Audio Track from Videos",
                            accentColor = Color(0xFF18FFFF),
                            testTag = "me_box_mp3_converter",
                            onClick = onOpenMp3Converter
                        )
                    }

                    // 10. Storage Cleaner & Duplicate Finder
                    item {
                        StackedFolderBoxItem(
                            icon = Icons.Default.CleaningServices,
                            title = if (isHindi) "स्टोरेज क्लीनर" else "Storage Analyzer & Cleaner",
                            subtitle = "Scan Large Media Files & Duplicates",
                            accentColor = Color(0xFF69F0AE),
                            testTag = "me_box_cleaner",
                            onClick = onOpenCleanerHub
                        )
                    }

                    // 11. 30-Day Recycle Bin
                    item {
                        StackedFolderBoxItem(
                            icon = Icons.Default.DeleteSweep,
                            title = if (isHindi) "रीसायकल बिन (30 दिन)" else "Recycle Bin (30 Days)",
                            subtitle = "$recycledCount Items • Auto-cleans after 30 days",
                            accentColor = Color(0xFFFFAB40),
                            testTag = "me_box_recycle_bin",
                            onClick = { subScreen = MeSubScreen.RECYCLE_BIN }
                        )
                    }

                    // =================================================================
                    // SINGLE HORIZONTAL ROW AT THE VERY BOTTOM:
                    // Rate Us | Report | Latest | Privacy Policy
                    // =================================================================
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
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
                    contentPadding = PaddingValues(bottom = 120.dp),
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
                        contentPadding = PaddingValues(bottom = 120.dp),
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
            // SUB-SCREEN: 20 PUPPIES & PENGUINS COMPANION GALLERY
            // =========================================================================
            MeSubScreen.COMPANION_GALLERY -> {
                val breeds = CompanionBreed.entries
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 145.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 120.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(breeds.size) { index ->
                        val breed = breeds[index]
                        val isSelected = breed.displayName == selectedCompanionName
                        LiquidGlassBox(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 18.dp,
                            tintColor = breed.headphoneAccent,
                            isActive = isSelected,
                            onClick = { viewModel.selectCompanionBreed(breed.displayName) }
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
                                        .background(breed.primaryFur)
                                        .border(2.dp, breed.headphoneAccent, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (breed.isPenguin) "🐧" else "🐶",
                                        fontSize = 16.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = breed.displayName,
                                        color = Color.White,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (breed.isPenguin) "Penguin" else "Puppy",
                                        color = breed.headphoneAccent,
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
                    contentPadding = PaddingValues(bottom = 120.dp),
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
                            text = "Recycle Bin is Empty",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 120.dp),
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
                        "latest" -> "Hybrid Player v4.5 Latest Build\n• Full-Screen Video & PDF Engine\n• True Non-Repeating Shuffle Queue\n• 15 3D Audio Visualizer Themes with Permanent Switch\n• High-Speed Hybrid Share with Live QR & Radar"
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
