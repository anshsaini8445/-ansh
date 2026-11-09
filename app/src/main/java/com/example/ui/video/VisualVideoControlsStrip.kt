package com.example.ui.video

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Universal Video Resolution presets (used internally by decoder; no cluttered badge on HUD).
 */
enum class VideoResolutionPreset(
    val badge: String,
    val maxWidth: Int,
    val maxHeight: Int,
    val maxBitrateMbps: Int,
    val accent: Color
) {
    AUTO("AUTO", Int.MAX_VALUE, Int.MAX_VALUE, 200, Color.White),
    UHD_8K("UHD", 7680, 4320, 160, Color.White),
    UHD_4K("4K", 3840, 2160, 85, Color.White),
    QHD_2K("2K", 2560, 1440, 45, Color.White),
    FHD_1080P("1080p", 1920, 1080, 25, Color.White),
    HD_720P("720p", 1280, 720, 12, Color.White),
    SD_480P("480p", 854, 480, 6, Color.White),
    SD_360P("360p", 640, 360, 3, Color.White),
    LD_240P("144p", 426, 240, 1, Color.White)
}

/**
 * MX Player-Style Horizontal Smooth Scrollable Pro Control Strip.
 * Featuring 19 working action pills styled with crisp white icons and dark translucent glass.
 */
@Composable
fun VisualVideoControlsStrip(
    onLockClick: () -> Unit,
    volumeBoostPercent: Int,
    onCycleVolumeBoost: () -> Unit,
    playbackSpeed: Float,
    onCyclePlaybackSpeed: () -> Unit,
    aspectCycleIndex: Int,
    onCycleAspectRatio: () -> Unit,
    onRotateOrientation: () -> Unit = {},
    onEnterPipClick: () -> Unit,
    onCaptureScreenshot: () -> Unit = {},
    isBgAudioEnabled: Boolean = false,
    onToggleBgAudio: () -> Unit = {},
    abStartMs: Long? = null,
    abEndMs: Long? = null,
    onToggleAbRepeat: () -> Unit = {},
    subtitlesEnabled: Boolean = false,
    onOpenSubtitles: () -> Unit = {},
    onOpenAudioTrack: () -> Unit = {},
    visualFilterName: String = "Original",
    onOpenVisualFilters: () -> Unit = {},
    isNightEyeActive: Boolean = false,
    onToggleNightEye: () -> Unit = {},
    isMirrorFlipped: Boolean = false,
    onToggleMirrorFlip: () -> Unit = {},
    isShuffleActive: Boolean = false,
    onToggleShuffle: () -> Unit = {},
    isLoopSingleActive: Boolean = false,
    onToggleLoopSingle: () -> Unit = {},
    onOpenQueueSheet: () -> Unit = {},
    onOpenProControlSheet: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Touch Lock Orb
        PureLiquidControlOrb(
            icon = Icons.Default.LockOpen,
            badgeText = "LOCK",
            active = false,
            onClick = onLockClick
        )

        // 2. Volume Boost Orb (100% - 200%)
        PureLiquidControlOrb(
            icon = if (volumeBoostPercent > 100) Icons.Default.GraphicEq else Icons.AutoMirrored.Filled.VolumeUp,
            badgeText = "${volumeBoostPercent}%",
            active = volumeBoostPercent > 100,
            onClick = onCycleVolumeBoost
        )

        // 3. Playback Speed Orb (0.25x - 8.0x)
        PureLiquidControlOrb(
            icon = Icons.Default.Speed,
            badgeText = "${playbackSpeed}x",
            active = playbackSpeed != 1.0f,
            onClick = onCyclePlaybackSpeed
        )

        // 4. Aspect Ratio & Screen Fit (System Default / Full / Crop / 16:9 / 21:9 / 4:3)
        val aspectSymbol = when (aspectCycleIndex) {
            0 -> "FIT"
            1 -> "FULL"
            2 -> "CROP"
            3 -> "16:9"
            4 -> "21:9"
            else -> "4:3"
        }
        PureLiquidControlOrb(
            icon = Icons.Default.AspectRatio,
            badgeText = aspectSymbol,
            active = aspectCycleIndex != 0,
            onClick = onCycleAspectRatio
        )

        // 5. Screen Rotate (Portrait / Sensor Landscape)
        PureLiquidControlOrb(
            icon = Icons.Default.ScreenRotation,
            badgeText = "ROTATE",
            active = false,
            onClick = onRotateOrientation
        )

        // 6. Floating PiP Mini Player
        PureLiquidControlOrb(
            icon = Icons.Default.PictureInPictureAlt,
            badgeText = "PiP",
            active = false,
            onClick = onEnterPipClick
        )

        // 7. Instant Frame Capture Camera
        PureLiquidControlOrb(
            icon = Icons.Default.CameraAlt,
            badgeText = "SHOT",
            active = false,
            onClick = onCaptureScreenshot
        )

        // 8. Background Audio Mode
        PureLiquidControlOrb(
            icon = Icons.Default.Headphones,
            badgeText = "BG AUDIO",
            active = isBgAudioEnabled,
            onClick = onToggleBgAudio
        )

        // 9. A-B Repeat Loop
        val abBadge = when {
            abStartMs != null && abEndMs != null -> "A⇄B"
            abStartMs != null -> "SET B"
            else -> "A-B"
        }
        PureLiquidControlOrb(
            icon = Icons.Default.Repeat,
            badgeText = abBadge,
            active = abStartMs != null,
            onClick = onToggleAbRepeat
        )

        // 10. Subtitles (.srt / .vtt)
        PureLiquidControlOrb(
            icon = Icons.Default.Subtitles,
            badgeText = if (subtitlesEnabled) "CC ON" else "CC",
            active = subtitlesEnabled,
            onClick = onOpenSubtitles
        )

        // 11. Audio Track / Dual Audio
        PureLiquidControlOrb(
            icon = Icons.Default.Audiotrack,
            badgeText = "TRACK",
            active = false,
            onClick = onOpenAudioTrack
        )

        // 12. 8K/4K Color Visual Enhancer Filter
        PureLiquidControlOrb(
            icon = Icons.Default.Palette,
            badgeText = if (visualFilterName != "Original") visualFilterName else "FILTER",
            active = visualFilterName != "Original",
            onClick = onOpenVisualFilters
        )

        // 13. Night Eye Comfort Mode (2% Ultra-Dim)
        PureLiquidControlOrb(
            icon = Icons.Default.DarkMode,
            badgeText = if (isNightEyeActive) "NIGHT" else "EYE",
            active = isNightEyeActive,
            onClick = onToggleNightEye
        )

        // 14. Horizontal Mirror / Flip 180°
        PureLiquidControlOrb(
            icon = Icons.Default.Flip,
            badgeText = if (isMirrorFlipped) "FLIP" else "MIRROR",
            active = isMirrorFlipped,
            onClick = onToggleMirrorFlip
        )

        // 15. Random Shuffle Mode
        PureLiquidControlOrb(
            icon = Icons.Default.Shuffle,
            badgeText = if (isShuffleActive) "SHUFFLE" else "ORDER",
            active = isShuffleActive,
            onClick = onToggleShuffle
        )

        // 16. Single Video Loop Toggle
        PureLiquidControlOrb(
            icon = Icons.Default.RepeatOne,
            badgeText = if (isLoopSingleActive) "LOOP 1" else "LOOP ALL",
            active = isLoopSingleActive,
            onClick = onToggleLoopSingle
        )

        // 17. Video Queue Playlist
        PureLiquidControlOrb(
            icon = Icons.Default.PlaylistPlay,
            badgeText = "QUEUE",
            active = false,
            onClick = onOpenQueueSheet
        )

        // 18. Full Pro Suite (25 Tools)
        PureLiquidControlOrb(
            icon = Icons.Default.MoreHoriz,
            badgeText = "25 TOOLS",
            active = true,
            onClick = onOpenProControlSheet
        )
    }
}

/**
 * Clean Liquid Glass Action Orb with crisp white typography and high-contrast styling.
 */
@Composable
fun PureLiquidControlOrb(
    icon: ImageVector,
    badgeText: String?,
    active: Boolean,
    onClick: () -> Unit
) {
    val bgCol by animateColorAsState(
        targetValue = if (active) Color(0x55FFFFFF) else Color(0x22FFFFFF),
        label = "orbBg"
    )
    val borderCol by animateColorAsState(
        targetValue = if (active) Color.White else Color(0x38FFFFFF),
        label = "orbBorder"
    )

    Row(
        modifier = Modifier
            .height(38.dp)
            .clip(CircleShape)
            .background(bgCol)
            .border(1.dp, borderCol, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = badgeText,
            tint = Color.White,
            modifier = Modifier.size(17.dp)
        )
        if (badgeText != null) {
            Text(
                text = badgeText,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
    }
}

