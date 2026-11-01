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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Universal Video Resolution presets (used internally by decoder; no cluttered 8K badge on HUD).
 */
enum class VideoResolutionPreset(
    val badge: String,
    val maxWidth: Int,
    val maxHeight: Int,
    val maxBitrateMbps: Int,
    val accent: Color
) {
    AUTO("AUTO", Int.MAX_VALUE, Int.MAX_VALUE, 200, Color(0xFF00E5FF)),
    UHD_8K("UHD", 7680, 4320, 160, Color(0xFFFF1744)),
    UHD_4K("4K", 3840, 2160, 85, Color(0xFFFFEA00)),
    QHD_2K("2K", 2560, 1440, 45, Color(0xFFD500F9)),
    FHD_1080P("1080p", 1920, 1080, 25, Color(0xFF00E676)),
    HD_720P("720p", 1280, 720, 12, Color(0xFF2979FF)),
    SD_480P("480p", 854, 480, 6, Color(0xFFFF9100)),
    SD_360P("360p", 640, 360, 3, Color(0xFF1DE9B6)),
    LD_240P("144p", 426, 240, 1, Color(0xFF90A4AE))
}

/**
 * Clean, Non-Duplicated Liquid Glass Control Strip at the bottom of the Video Player.
 * Removes all "8K" clutter and duplicate items that live inside the 3-Dot menu.
 */
@Composable
fun VisualVideoControlsStrip(
    onLockClick: () -> Unit,
    volumeBoostPercent: Int,
    onCycleVolumeBoost: () -> Unit,
    selectedResolution: VideoResolutionPreset = VideoResolutionPreset.AUTO,
    onCycleResolution: () -> Unit = {},
    visualFilterIndex: Int = 0,
    onCycleVisualFilter: () -> Unit = {},
    abStartMs: Long? = null,
    abEndMs: Long? = null,
    onToggleAbRepeat: () -> Unit = {},
    onCaptureScreenshot: () -> Unit = {},
    isBgAudioEnabled: Boolean = false,
    onToggleBgAudio: () -> Unit = {},
    onEnterPipClick: () -> Unit,
    aspectCycleIndex: Int,
    onCycleAspectRatio: () -> Unit,
    playbackSpeed: Float,
    onCyclePlaybackSpeed: () -> Unit,
    sleepTimerMinutes: Int = 0,
    onOpenProControlSheet: () -> Unit = {},
    onOpenQueueSheet: () -> Unit = {},
    onRotateOrientation: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Touch Lock Orb
            PureLiquidControlOrb(
                icon = Icons.Default.LockOpen,
                badgeText = null,
                active = false,
                accentColor = Color(0xFFFFAB00),
                onClick = onLockClick
            )

            // 2. Volume / 200% Booster Orb
            PureLiquidControlOrb(
                icon = if (volumeBoostPercent > 100) Icons.Default.GraphicEq else Icons.AutoMirrored.Filled.VolumeUp,
                badgeText = "${volumeBoostPercent}%",
                active = volumeBoostPercent > 100,
                accentColor = Color(0xFF00E676),
                onClick = onCycleVolumeBoost
            )

            // 3. Playback Speed Orb (0.5x - 3.0x)
            PureLiquidControlOrb(
                icon = Icons.Default.Speed,
                badgeText = "${playbackSpeed}x",
                active = playbackSpeed != 1.0f,
                accentColor = Color(0xFF00E5FF),
                onClick = onCyclePlaybackSpeed
            )

            // 4. Aspect Ratio & Screen Fit Orb
            val aspectSymbol = when (aspectCycleIndex) {
                0 -> "FULL"
                1 -> "CROP"
                2 -> "FIT"
                3 -> "16:9"
                4 -> "21:9"
                else -> "4:3"
            }
            PureLiquidControlOrb(
                icon = Icons.Default.AspectRatio,
                badgeText = aspectSymbol,
                active = aspectCycleIndex != 0,
                accentColor = Color(0xFF00E5FF),
                onClick = onCycleAspectRatio
            )

            // 5. Screen Rotate (Portrait / Landscape) Orb
            PureLiquidControlOrb(
                icon = Icons.Default.ScreenRotation,
                badgeText = null,
                active = false,
                accentColor = Color(0xFF00E676),
                onClick = onRotateOrientation
            )

            // 6. Floating PiP Window Orb
            PureLiquidControlOrb(
                icon = Icons.Default.PictureInPictureAlt,
                badgeText = null,
                active = false,
                accentColor = Color(0xFF2979FF),
                onClick = onEnterPipClick
            )

            // 7. Instant Frame Capture Camera Orb
            PureLiquidControlOrb(
                icon = Icons.Default.CameraAlt,
                badgeText = null,
                active = false,
                accentColor = Color(0xFF00E5FF),
                onClick = onCaptureScreenshot
            )
        }
    }
}

@Composable
fun PureLiquidControlOrb(
    icon: ImageVector,
    badgeText: String?,
    active: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val bgTop by animateColorAsState(
        targetValue = if (active) accentColor.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.12f),
        label = "orbBgTop"
    )
    val borderCol by animateColorAsState(
        targetValue = if (active) accentColor else Color.White.copy(alpha = 0.28f),
        label = "orbBorder"
    )

    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(bgTop, Color(0xCC070C18))
                )
            )
            .border(1.1.dp, borderCol, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = if (badgeText != null) 12.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) accentColor else Color.White,
            modifier = Modifier.size(18.dp)
        )
        if (badgeText != null) {
            Text(
                text = badgeText,
                color = if (active) accentColor else Color.White.copy(alpha = 0.92f),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
