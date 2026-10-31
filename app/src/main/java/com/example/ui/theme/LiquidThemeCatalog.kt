package com.example.ui.theme

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.LiquidGlassBox

/**
 * 60 Curated Liquid Glass Themes + User Custom Theme Creator.
 * Every theme defines primary neon accent, secondary refraction glow,
 * deep liquid background, and frosted glass card surface.
 */
object LiquidThemeCatalog {

    private const val PREF_CUSTOM_PRIMARY = "user_custom_theme_primary_argb"
    private const val PREF_CUSTOM_SECONDARY = "user_custom_theme_secondary_argb"
    private const val PREF_CUSTOM_BG = "user_custom_theme_bg_argb"
    private const val PREF_CUSTOM_GLASS_ALPHA = "user_custom_theme_glass_alpha"
    const val CUSTOM_THEME_NAME = "Custom User Liquid Theme"

    val ALL_60_THEMES: List<StudioThemePalette> = listOf(
        // 1 - 10: Signature Liquid Glass & Cyber Series
        StudioThemePalette("Hybrid Emerald", Color(0xFF2ECC71), Color(0xFF00E5FF), Color(0xFF080D16), Color(0xFF141E30)),
        StudioThemePalette("Cyber Cyan", Color(0xFF00E5FF), Color(0xFF2ECC71), Color(0xFF060B12), Color(0xFF101C2B)),
        StudioThemePalette("Crimson Liquid Glass", Color(0xFFFF2A4B), Color(0xFFFF8A80), Color(0xFF0E0507), Color(0xFF1F0C11)),
        StudioThemePalette("Neon Blue", Color(0xFF00B4D8), Color(0xFF00F5D4), Color(0xFF07090E), Color(0xFF131824)),
        StudioThemePalette("Deep Purple", Color(0xFF9D4EDD), Color(0xFFC77DFF), Color(0xFF0B0514), Color(0xFF1A102E)),
        StudioThemePalette("Luxury Gold", Color(0xFFFFD700), Color(0xFFFFA500), Color(0xFF0D0B04), Color(0xFF1E1A0D)),
        StudioThemePalette("Forest Green", Color(0xFF00F5D4), Color(0xFF52B788), Color(0xFF050F0A), Color(0xFF10241A)),
        StudioThemePalette("Coral Sunset", Color(0xFFFF5252), Color(0xFFFF8A80), Color(0xFF120709), Color(0xFF241216)),
        StudioThemePalette("Tokyo Neon", Color(0xFFFF007F), Color(0xFF00F0FF), Color(0xFF080411), Color(0xFF19102C)),
        StudioThemePalette("Arctic Ice", Color(0xFF70E1F5), Color(0xFF38B6FF), Color(0xFF070F16), Color(0xFF132230)),

        // 11 - 20: AMOLED Pitch Black Series
        StudioThemePalette("AMOLED Pitch Black", Color(0xFF00E676), Color(0xFF00E5FF), Color(0xFF000000), Color(0xFF0B0F17)),
        StudioThemePalette("AMOLED Ruby Red", Color(0xFFFF1744), Color(0xFFFF5252), Color(0xFF000000), Color(0xFF14070A)),
        StudioThemePalette("AMOLED Cyber Gold", Color(0xFFFFD600), Color(0xFFFF9100), Color(0xFF000000), Color(0xFF141106)),
        StudioThemePalette("AMOLED Electric Cyan", Color(0xFF00E5FF), Color(0xFF2979FF), Color(0xFF000000), Color(0xFF061018)),
        StudioThemePalette("AMOLED Violet Pulse", Color(0xFFD500F9), Color(0xFF651FFF), Color(0xFF000000), Color(0xFF120718)),
        StudioThemePalette("AMOLED Matrix Code", Color(0xFF00FF66), Color(0xFF00BFA5), Color(0xFF000000), Color(0xFF05140B)),
        StudioThemePalette("AMOLED Sunset Orange", Color(0xFFFF6D00), Color(0xFFFFAB00), Color(0xFF000000), Color(0xFF160C05)),
        StudioThemePalette("AMOLED Frost White", Color(0xFFE0F7FA), Color(0xFF80DEEA), Color(0xFF000000), Color(0xFF0E1317)),
        StudioThemePalette("AMOLED Rose Pink", Color(0xFFFF4081), Color(0xFFF48FB1), Color(0xFF000000), Color(0xFF160810)),
        StudioThemePalette("AMOLED Cobalt Laser", Color(0xFF2979FF), Color(0xFF448AFF), Color(0xFF000000), Color(0xFF060C1A)),

        // 21 - 30: Crystal & Liquid Prism Series
        StudioThemePalette("Solar Flare", Color(0xFFFF9100), Color(0xFFFFD600), Color(0xFF100A03), Color(0xFF21160A)),
        StudioThemePalette("Crimson Blade", Color(0xFFFF1744), Color(0xFFD50000), Color(0xFF0F0406), Color(0xFF220B10)),
        StudioThemePalette("Electric Indigo", Color(0xFF651FFF), Color(0xFF00E5FF), Color(0xFF070514), Color(0xFF15112E)),
        StudioThemePalette("Mint Matrix", Color(0xFF1DE9B6), Color(0xFF00BFA5), Color(0xFF040E0C), Color(0xFF0D2420)),
        StudioThemePalette("Rose Quartz", Color(0xFFF48FB1), Color(0xFFCE93D8), Color(0xFF11090E), Color(0xFF24151F)),
        StudioThemePalette("Cobalt Racer", Color(0xFF2979FF), Color(0xFF00B0FF), Color(0xFF050A14), Color(0xFF111C33)),
        StudioThemePalette("Amber Vintage", Color(0xFFFFAB00), Color(0xFFFF6D00), Color(0xFF100C04), Color(0xFF231B0C)),
        StudioThemePalette("Lavender Mist", Color(0xFFB388FF), Color(0xFF8C9EFF), Color(0xFF0B0914), Color(0xFF19152B)),
        StudioThemePalette("Titanium Slate", Color(0xFF90CAF9), Color(0xFFB0BEC5), Color(0xFF0B0E11), Color(0xFF171D24)),
        StudioThemePalette("Aurora Borealis", Color(0xFF00FFCC), Color(0xFF7C4DFF), Color(0xFF050E14), Color(0xFF112230)),

        // 31 - 40: Galaxy, Nebula & Cosmic Series
        StudioThemePalette("Andromeda Nebula", Color(0xFFE040FB), Color(0xFF00E5FF), Color(0xFF090414), Color(0xFF1B0E30)),
        StudioThemePalette("Supernova Red", Color(0xFFFF3D00), Color(0xFFFFEA00), Color(0xFF120503), Color(0xFF260E09)),
        StudioThemePalette("Starlight Sapphire", Color(0xFF40C4FF), Color(0xFF7C4DFF), Color(0xFF040B16), Color(0xFF0E1D34)),
        StudioThemePalette("Cosmic Emerald", Color(0xFF00E676), Color(0xFF18FFFF), Color(0xFF04100B), Color(0xFF0C261B)),
        StudioThemePalette("Orion Belt Gold", Color(0xFFFFEA00), Color(0xFFFF6E40), Color(0xFF110E04), Color(0xFF241E0A)),
        StudioThemePalette("Pulsar Magenta", Color(0xFFFF00AA), Color(0xFF7C4DFF), Color(0xFF100412), Color(0xFF240B29)),
        StudioThemePalette("Quasar Teal", Color(0xFF64FFDA), Color(0xFF00B0FF), Color(0xFF041012), Color(0xFF0D2529)),
        StudioThemePalette("Dark Matter Obsidian", Color(0xFF8C9EFF), Color(0xFFB388FF), Color(0xFF05060A), Color(0xFF121420)),
        StudioThemePalette("Nebula Flamingo", Color(0xFFFF80AB), Color(0xFFEA80FC), Color(0xFF12070E), Color(0xFF26111F)),
        StudioThemePalette("Saturn Ring Bronze", Color(0xFFFFB74D), Color(0xFFFF8A65), Color(0xFF110B06), Color(0xFF24180E)),

        // 41 - 50: Hypercar, Gaming & RGB Series
        StudioThemePalette("Cyberpunk 2099", Color(0xFFFFEE00), Color(0xFF00F0FF), Color(0xFF090A10), Color(0xFF181A28)),
        StudioThemePalette("Hypercar Lime", Color(0xFFAEEA00), Color(0xFF00E676), Color(0xFF090E04), Color(0xFF17240B)),
        StudioThemePalette("Formula Rosso", Color(0xFFFF1744), Color(0xFFFF9100), Color(0xFF120406), Color(0xFF280B10)),
        StudioThemePalette("Miami Vice 80s", Color(0xFF00E5FF), Color(0xFFFF4081), Color(0xFF070B16), Color(0xFF151E36)),
        StudioThemePalette("Razer Venom Green", Color(0xFF00FF00), Color(0xFF00E676), Color(0xFF030A03), Color(0xFF0B1E0B)),
        StudioThemePalette("PlayStation Cobalt", Color(0xFF2962FF), Color(0xFF00E5FF), Color(0xFF040814), Color(0xFF0D1730)),
        StudioThemePalette("Sunset Horizon", Color(0xFFFF7043), Color(0xFFFFCA28), Color(0xFF120806), Color(0xFF26130E)),
        StudioThemePalette("Glacier Mint", Color(0xFF80CBC4), Color(0xFF4DD0E1), Color(0xFF060E0E), Color(0xFF112222)),
        StudioThemePalette("Velvet Orchid", Color(0xFFBA68C8), Color(0xFFF06292), Color(0xFF0F0712), Color(0xFF221129)),
        StudioThemePalette("Royal Champagne", Color(0xFFFFE082), Color(0xFFFFCA28), Color(0xFF0F0D08), Color(0xFF211C12)),

        // 51 - 60: Nature, Ocean & Zen Liquid Glass Series
        StudioThemePalette("Pacific Deep Ocean", Color(0xFF00B8D4), Color(0xFF00E5FF), Color(0xFF030C12), Color(0xFF0A1F2E)),
        StudioThemePalette("Himalayan Frost", Color(0xFFB3E5FC), Color(0xFF81D4FA), Color(0xFF070C12), Color(0xFF13202E)),
        StudioThemePalette("Kyoto Cherry Blossom", Color(0xFFFF80AB), Color(0xFFFFB2C1), Color(0xFF11080C), Color(0xFF24131B)),
        StudioThemePalette("Amazon Rainforest", Color(0xFF00C853), Color(0xFF69F0AE), Color(0xFF040D07), Color(0xFF0C2214)),
        StudioThemePalette("Sahara Golden Dune", Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFF110C04), Color(0xFF241B0A)),
        StudioThemePalette("Maldives Lagoon", Color(0xFF18FFFF), Color(0xFF1DE9B6), Color(0xFF030F12), Color(0xFF0B242B)),
        StudioThemePalette("Volcanic Magma", Color(0xFFFF3D00), Color(0xFFFF9100), Color(0xFF120502), Color(0xFF280D06)),
        StudioThemePalette("Nordic Pine", Color(0xFF26A69A), Color(0xFF80CBC4), Color(0xFF050D0C), Color(0xFF0F2220)),
        StudioThemePalette("Midnight Espresso", Color(0xFFD7CCC8), Color(0xFFA1887F), Color(0xFF0C0908), Color(0xFF1E1715)),
        StudioThemePalette("Diamond Prism", Color(0xFFFFFFFF), Color(0xFF00E5FF), Color(0xFF080B10), Color(0xFF161C26))
    )

    val QUICK_COLOR_SWATCHES = listOf(
        Color(0xFFFF2A4B), Color(0xFF2ECC71), Color(0xFF00E5FF), Color(0xFFFFD700),
        Color(0xFFFF007F), Color(0xFF9D4EDD), Color(0xFF00FF66), Color(0xFFFF6D00),
        Color(0xFF2979FF), Color(0xFF1DE9B6), Color(0xFFE040FB), Color(0xFFFFEA00),
        Color(0xFF40C4FF), Color(0xFFFF4081), Color(0xFFAEEA00), Color(0xFFFFFFFF)
    )

    fun getCustomPalette(context: Context): StudioThemePalette {
        val prefs = context.getSharedPreferences("HYBRID_PLAYER_PREFS", Context.MODE_PRIVATE)
        val prim = Color(prefs.getInt(PREF_CUSTOM_PRIMARY, Color(0xFFFF2A4B).toArgb()))
        val sec = Color(prefs.getInt(PREF_CUSTOM_SECONDARY, Color(0xFF00E5FF).toArgb()))
        val bg = Color(prefs.getInt(PREF_CUSTOM_BG, Color(0xFF080D16).toArgb()))
        val surface = Color(
            red = (bg.red + prim.red * 0.12f).coerceIn(0f, 1f),
            green = (bg.green + prim.green * 0.12f).coerceIn(0f, 1f),
            blue = (bg.blue + prim.blue * 0.12f).coerceIn(0f, 1f),
            alpha = 1f
        )
        return StudioThemePalette(
            name = CUSTOM_THEME_NAME,
            primary = prim,
            secondary = sec,
            background = bg,
            surface = surface
        )
    }

    fun saveCustomPalette(
        context: Context,
        primary: Color,
        secondary: Color,
        background: Color,
        glassAlpha: Float
    ) {
        context.getSharedPreferences("HYBRID_PLAYER_PREFS", Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_CUSTOM_PRIMARY, primary.toArgb())
            .putInt(PREF_CUSTOM_SECONDARY, secondary.toArgb())
            .putInt(PREF_CUSTOM_BG, background.toArgb())
            .putFloat(PREF_CUSTOM_GLASS_ALPHA, glassAlpha.coerceIn(0.10f, 0.55f))
            .apply()
    }

    fun resolveTheme(context: Context?, themeName: String): StudioThemePalette {
        if (themeName == CUSTOM_THEME_NAME && context != null) {
            return getCustomPalette(context)
        }
        return ALL_60_THEMES.find { it.name == themeName } ?: ALL_60_THEMES.first()
    }
}

@Composable
fun ThemeStudio60Dialog(
    activeTheme: String,
    onSelectTheme: (String) -> Unit,
    onPickGalleryWallpaper: () -> Unit,
    onClearGalleryWallpaper: () -> Unit,
    hasCustomWallpaper: Boolean,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0 = 60 Preset Themes, 1 = Custom Theme Creator
    var searchFilter by remember { mutableStateOf("") }

    val initialCustom = remember { LiquidThemeCatalog.getCustomPalette(context) }
    var customPrimary by remember { mutableStateOf(initialCustom.primary) }
    var customSecondary by remember { mutableStateOf(initialCustom.secondary) }
    var customBgMode by remember { mutableStateOf(0) } // 0 = Deep Glass, 1 = AMOLED Black, 2 = Midnight Navy
    var customGlassAlpha by remember { mutableStateOf(0.24f) }

    val filteredThemes = remember(searchFilter) {
        if (searchFilter.isBlank()) LiquidThemeCatalog.ALL_60_THEMES
        else LiquidThemeCatalog.ALL_60_THEMES.filter {
            it.name.contains(searchFilter, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HybridCardBg,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "60 Liquid Themes + Custom Studio",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(HybridEmerald.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("60+ Themes", color = HybridEmerald, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                // Switcher: 60 Presets vs Custom Builder
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(HybridCardAlt)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0 to "60 Preset Themes", 1 to "Custom Builder").forEach { (idx, label) ->
                        val active = selectedTab == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (active) HybridEmerald else Color.Transparent)
                                .clickable { selectedTab = idx }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (active) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        },
        text = {
            if (selectedTab == 0) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                onDismiss()
                                onPickGalleryWallpaper()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = HybridEmerald),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gallery Wallpaper", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, maxLines = 1)
                        }
                        if (hasCustomWallpaper) {
                            OutlinedButton(
                                onClick = onClearGalleryWallpaper,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text("Reset BG", color = HybridCoralRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = searchFilter,
                        onValueChange = { searchFilter = it },
                        placeholder = { Text("Search 60 themes (AMOLED, Neon, Gold, Ocean...)", color = HybridTextSecondary, fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        items(filteredThemes, key = { it.name }) { themePalette ->
                            val selected = activeTheme == themePalette.name
                            LiquidGlassBox(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 14.dp,
                                tintColor = themePalette.primary,
                                glassAlpha = if (selected) 0.34f else 0.16f,
                                onClick = {
                                    onSelectTheme(themePalette.name)
                                    onDismiss()
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(themePalette.primary, themePalette.secondary)
                                                )
                                            )
                                            .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = themePalette.name,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (themePalette.background == Color(0xFF000000)) "True AMOLED 0% Black • Liquid Glass" else "Liquid Glass Refraction",
                                            color = themePalette.primary,
                                            fontSize = 10.sp
                                        )
                                    }
                                    if (selected) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = themePalette.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Custom User Liquid Theme Builder
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "1. Pick Primary Neon Accent:",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(LiquidThemeCatalog.QUICK_COLOR_SWATCHES) { swatch ->
                            val isSelected = customPrimary == swatch
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) Color.White else Color.Black.copy(alpha = 0.4f),
                                        shape = CircleShape
                                    )
                                    .clickable { customPrimary = swatch }
                            )
                        }
                    }

                    Text(
                        text = "2. Pick Secondary Refraction Glow:",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(LiquidThemeCatalog.QUICK_COLOR_SWATCHES.reversed()) { swatch ->
                            val isSelected = customSecondary == swatch
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) Color.White else Color.Black.copy(alpha = 0.4f),
                                        shape = CircleShape
                                    )
                                    .clickable { customSecondary = swatch }
                            )
                        }
                    }

                    Text(
                        text = "3. Choose Base Surface Style:",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            0 to "Liquid Dark",
                            1 to "AMOLED Black",
                            2 to "Cyber Navy"
                        ).forEach { (mode, label) ->
                            val active = customBgMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (active) customPrimary else HybridCardAlt)
                                    .clickable { customBgMode = mode }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (active) Color.Black else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    Text(
                        text = "4. Liquid Glass Frosted Intensity (${(customGlassAlpha * 100).toInt()}%):",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = customGlassAlpha,
                        onValueChange = { customGlassAlpha = it },
                        valueRange = 0.12f..0.48f,
                        colors = SliderDefaults.colors(
                            thumbColor = customPrimary,
                            activeTrackColor = customPrimary
                        )
                    )

                    Button(
                        onClick = {
                            val bg = when (customBgMode) {
                                1 -> Color(0xFF000000)
                                2 -> Color(0xFF050B18)
                                else -> Color(0xFF080D16)
                            }
                            LiquidThemeCatalog.saveCustomPalette(
                                context = context,
                                primary = customPrimary,
                                secondary = customSecondary,
                                background = bg,
                                glassAlpha = customGlassAlpha
                            )
                            onSelectTheme(LiquidThemeCatalog.CUSTOM_THEME_NAME)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = customPrimary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Apply My Custom Liquid Theme",
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = HybridEmerald, fontWeight = FontWeight.Bold)
            }
        }
    )
}
