package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NeonBlueColorScheme = darkColorScheme(
    primary = NeonBlue,
    secondary = NeonCyan,
    background = CyberBlack,
    surface = CyberGray,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

private val DeepPurpleColorScheme = darkColorScheme(
    primary = AmethystPrimary,
    secondary = AmethystSecondary,
    background = AmethystDeep,
    surface = AmethystGray,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LuxuryGoldColorScheme = darkColorScheme(
    primary = LuxuryGold,
    secondary = GoldSecondary,
    background = CarbonBlack,
    surface = CarbonGray,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

private val ForestGreenColorScheme = darkColorScheme(
    primary = EmeraldNeon,
    secondary = ForestSecondary,
    background = MossDark,
    surface = ForestGray,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun MyApplicationTheme(
    themeName: String = "Deep Purple",
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeName) {
        "Neon Blue" -> NeonBlueColorScheme
        "Luxury Gold" -> LuxuryGoldColorScheme
        "Forest Green" -> ForestGreenColorScheme
        else -> DeepPurpleColorScheme // "Deep Purple"
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
