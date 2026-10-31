package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

data class StudioThemePalette(
    val name: String,
    val primary: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color
)

val StudioThemes: List<StudioThemePalette> = LiquidThemeCatalog.ALL_60_THEMES

@Composable
fun MyApplicationTheme(
    themeName: String = "Hybrid Emerald",
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val palette = LiquidThemeCatalog.resolveTheme(context, themeName)

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
