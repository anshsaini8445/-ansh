package com.example.ui.video

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
 * Universal Video Resolution presets supporting 8K, 4K, 2K, 1080p, 720p, 480p, 360p, 240p, and Auto
 * across all video formats (MP4, MKV, WEBM, AVI, MOV, FLV, TS, M4V, 3GP, WMV).
 */
enum class VideoResolutionPreset(
    val badge: String,
    val maxWidth: Int,
    val maxHeight: Int,
    val maxBitrateMbps: Int,
    val accent: Color
) {
    AUTO("AUTO", Int.MAX_VALUE, Int.MAX_VALUE, 200, Color(0xFF00E5FF)),
    UHD_8K("8K", 7680, 4320, 160, Color(0xFFFF1744)),
    UHD_4K("4K", 3840, 2160, 85, Color(0xFFFFEA00)),
    QHD_2K("2K", 2560, 1440, 45, Color(0xFFD500F9)),
    FHD_1080P("1080p", 1920, 1080, 25, Color(0xFF00E676)),
    HD_720P("720p", 1280, 720, 12, Color(0xFF2979FF)),
    SD_480P("480p", 854, 480, 6, Color(0xFFFF9100)),
    SD_360P("360p", 640, 360, 3, Color(0xFF1DE9B6)),
    LD_240P("240p", 426, 240, 1, Color(0xFF90A4AE))
}

/**
 * Pure Visual Liquid Glass Control Strip ("Patti") at the bottom of the Video Player.
 * Uses pure visual icon orbs and compact numeric/symbol badges (no hardcoded English text labels)
 * so nothing ever clips on the screen edge and every control works universally in 55+ languages.
 */
@Composable
fun VisualVideoControlsStrip(
    onLockClick: () -> Unit,
    volumeBoostPercent: Int,
    onCycleVolumeBoost: () -> Unit,
    selectedResolution: VideoResolutionPreset,
    onCycleResolution: () -> Unit,
    visualFilterIndex: Int,
    onCycleVisualFilter: () -> Unit,
    abStartMs: Long?,
    abEndMs: Long?,
    onToggleAbRepeat: () -> Unit,
    onCaptureScreenshot: () -> Unit,
    isBgAudioEnabled: Boolean,
    onToggleBgAudio: () -> Unit,
    onEnterPipClick: () -> Unit,
    aspectCycleIndex: Int,
    onCycleAspectRatio: () -> Unit,
    playbackSpeed: Float,
    onCyclePlaybackSpeed: () -> Unit,
    sleepTimerMinutes: Int,
    onOpenProControlSheet: () -> Unit,
    onOpenQueueSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
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

        // 2. 8K / 4K / 2K / 1080p / 720p / 480p Resolution Selector Orb
        PureLiquidControlOrb(
            icon = Icons.Default.HighQuality,
            badgeText = selectedResolution.badge,
            active = selectedResolution != VideoResolutionPreset.AUTO,
            accentColor = selectedResolution.accent,
            onClick = onCycleResolution
        )

        // 3. 200% Hardware Audio Booster Orb
        PureLiquidControlOrb(
            icon = if (volumeBoostPercent > 100) Icons.Default.GraphicEq else Icons.Default.VolumeUp,
            badgeText = "${volumeBoostPercent}%",
            active = volumeBoostPercent > 100,
            accentColor = Color(0xFFFF1744),
            onClick = onCycleVolumeBoost
        )

        // 4. Playback Speed Orb (0.5x - 3.0x)
        PureLiquidControlOrb(
            icon = Icons.Default.Speed,
            badgeText = "${playbackSpeed}x",
            active = playbackSpeed != 1.0f,
            accentColor = Color(0xFF00E676),
            onClick = onCyclePlaybackSpeed
        )

        // 5. Aspect Ratio & Screen Fit Orb
        val aspectSymbol = when (aspectCycleIndex) {
            1 -> "⊞"
            2 -> "16:9"
            else -> "⊡"
        }
        PureLiquidControlOrb(
            icon = Icons.Default.AspectRatio,
            badgeText = aspectSymbol,
            active = aspectCycleIndex != 0,
            accentColor = Color(0xFF00E5FF),
            onClick = onCycleAspectRatio
        )

        // 6. Visual Color Filter Orb
        PureLiquidControlOrb(
            icon = Icons.Default.Palette,
            badgeText = if (visualFilterIndex == 0) null else "#$visualFilterIndex",
            active = visualFilterIndex != 0,
            accentColor = Color(0xFFD500F9),
            onClick = onCycleVisualFilter
        )

        // 7. A-B Loop Orb
        val abBadge = when {
            abStartMs != null && abEndMs != null -> "A⇄B"
            abStartMs != null -> "A→"
            else -> null
        }
        PureLiquidControlOrb(
            icon = Icons.Default.Repeat,
            badgeText = abBadge,
            active = abStartMs != null,
            accentColor = Color(0xFF00E676),
            onClick = onToggleAbRepeat
        )

        // 8. Instant Frame Capture Camera Orb
        PureLiquidControlOrb(
            icon = Icons.Default.CameraAlt,
            badgeText = null,
            active = false,
            accentColor = Color(0xFF00E5FF),
            onClick = onCaptureScreenshot
        )

        // 9. Background Audio Mode Orb
        PureLiquidControlOrb(
            icon = Icons.Default.Headphones,
            badgeText = if (isBgAudioEnabled) "ON" else null,
            active = isBgAudioEnabled,
            accentColor = Color(0xFF00E676),
            onClick = onToggleBgAudio
        )

        // 10. Floating PiP Window Orb
        PureLiquidControlOrb(
            icon = Icons.Default.PictureInPictureAlt,
            badgeText = null,
            active = false,
            accentColor = Color(0xFF2979FF),
            onClick = onEnterPipClick
        )

        // 11. Sleep Timer Orb
        PureLiquidControlOrb(
            icon = Icons.Default.Bedtime,
            badgeText = if (sleepTimerMinutes > 0) "${sleepTimerMinutes}m" else null,
            active = sleepTimerMinutes > 0,
            accentColor = Color(0xFFFFEA00),
            onClick = onOpenProControlSheet
        )

        // 12. Video Playlist Queue Orb
        PureLiquidControlOrb(
            icon = Icons.Default.QueueMusic,
            badgeText = null,
            active = false,
            accentColor = Color(0xFF00E5FF),
            onClick = onOpenQueueSheet
        )
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
            .height(42.dp)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(bgTop, Color(0xCC070C18))
                )
            )
            .border(1.2.dp, borderCol, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = if (badgeText != null) 13.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) accentColor else Color.White,
            modifier = Modifier.size(19.dp)
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
