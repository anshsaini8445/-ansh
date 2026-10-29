package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

data class StudioThemePalette(
    val name: String,
    val primary: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color
)

val StudioThemes = listOf(
    StudioThemePalette("Hybrid Emerald", HybridEmerald, HybridCyan, HybridDarkBg, HybridCardBg),
    StudioThemePalette("Cyber Cyan", HybridCyan, HybridEmerald, Color(0xFF060B12), Color(0xFF101C2B)),
    StudioThemePalette("Neon Blue", NeonBlue, NeonCyan, CyberBlack, CyberGray),
    StudioThemePalette("Deep Purple", AmethystPrimary, AmethystSecondary, AmethystDeep, AmethystGray),
    StudioThemePalette("Luxury Gold", LuxuryGold, GoldSecondary, CarbonBlack, CarbonGray),
    StudioThemePalette("Forest Green", EmeraldNeon, ForestSecondary, MossDark, ForestGray),
    StudioThemePalette("Coral Sunset", Color(0xFFFF5252), Color(0xFFFF8A80), Color(0xFF120709), Color(0xFF241216)),
    StudioThemePalette("Tokyo Neon", Color(0xFFFF007F), Color(0xFF00F0FF), Color(0xFF080411), Color(0xFF19102C)),
    StudioThemePalette("Arctic Ice", Color(0xFF70E1F5), Color(0xFF38B6FF), Color(0xFF070F16), Color(0xFF132230)),
    StudioThemePalette("AMOLED Pitch Black", HybridNeonGreen, HybridCyan, Color(0xFF000000), Color(0xFF0D1117)),
    StudioThemePalette("Solar Flare", Color(0xFFFF9100), Color(0xFFFFD600), Color(0xFF100A03), Color(0xFF21160A)),
    StudioThemePalette("Crimson Blade", Color(0xFFFF1744), Color(0xFFD50000), Color(0xFF0F0406), Color(0xFF220B10)),
    StudioThemePalette("Electric Indigo", Color(0xFF651FFF), Color(0xFF00E5FF), Color(0xFF070514), Color(0xFF15112E)),
    StudioThemePalette("Mint Matrix", Color(0xFF1DE9B6), Color(0xFF00BFA5), Color(0xFF040E0C), Color(0xFF0D2420)),
    StudioThemePalette("Rose Quartz", Color(0xFFF48FB1), Color(0xFFCE93D8), Color(0xFF11090E), Color(0xFF24151F)),
    StudioThemePalette("Cobalt Racer", Color(0xFF2979FF), Color(0xFF00B0FF), Color(0xFF050A14), Color(0xFF111C33)),
    StudioThemePalette("Amber Vintage", Color(0xFFFFAB00), Color(0xFFFF6D00), Color(0xFF100C04), Color(0xFF231B0C)),
    StudioThemePalette("Lavender Mist", Color(0xFFB388FF), Color(0xFF8C9EFF), Color(0xFF0B0914), Color(0xFF19152B)),
    StudioThemePalette("Titanium Slate", Color(0xFF90CAF9), Color(0xFFB0BEC5), Color(0xFF0B0E11), Color(0xFF171D24))
)

@Composable
fun MyApplicationTheme(
    themeName: String = "Hybrid Emerald",
    content: @Composable () -> Unit
) {
    val palette = StudioThemes.find { it.name == themeName } ?: StudioThemes.first()

    val colorScheme = darkColorScheme(
        primary = palette.primary,
        secondary = palette.secondary,
        background = palette.background,
        surface = palette.surface,
        onPrimary = Color.Black,
        onSecondary = Color.White,
        onBackground = Color.White,
        onSurface = Color.White
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
