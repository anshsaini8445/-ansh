package com.example.helpers

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.util.Locale
import kotlin.math.abs

/**
 * Locked gesture mode once a drag gesture exceeds the directional lock threshold.
 */
enum class LockedGestureMode {
    NONE,
    VERTICAL_BRIGHTNESS,
    VERTICAL_VOLUME,
    HORIZONTAL_SEEK,
    DYNAMIC_SPEED_SLIDE
}

/**
 * Haptic pulse intensity profiles triggered at specific gesture thresholds.
 */
enum class HapticPulseType(val durationMs: Long, val amplitude: Int) {
    STEP_TICK(14L, 90),         // Crossed a 10s seek interval or 0.5x speed notch
    MIDPOINT_TICK(20L, 140),    // Crossed 50% brightness or 100% normal volume threshold
    BOOST_THRESHOLD(28L, 200),  // Entered >100% hardware LoudnessEnhancer boost zone
    BOUNDARY_LIMIT(36L, 255)    // Reached min/max limit (2%/100% brightness, 0%/200% volume, 0s/end seek)
}

/**
 * Live state emitted to the player HUD while a user performs a swipe or hold gesture.
 */
data class GestureHudState(
    val mode: LockedGestureMode = LockedGestureMode.NONE,
    val titleText: String = "",
    val subText: String = "",
    val progressFraction: Float = 0f, // 0.0f..1.0f
    val isBoostZone: Boolean = false,
    val thresholdReached: Boolean = false
)

/**
 * Custom Gesture Detection & Threshold Haptic Engine for the 8K Pro Video Player.
 * Captures vertical swipes (left half for brightness 2%..100%, right half for volume 0%..200% boost)
 * and horizontal swipes for seeking, firing hardware haptic feedback at defined thresholds.
 */
object VideoGestureHelper {

    // Defined thresholds for Brightness (2%..100%), Volume (0%..200%), and Seek (every 10,000ms)
    val BRIGHTNESS_THRESHOLDS = setOf(2, 50, 100)
    val VOLUME_THRESHOLDS = setOf(0, 50, 100, 150, 200)
    const val SEEK_HAPTIC_STEP_MS = 10_000L // Trigger haptic tick every 10 seconds of seek scrubbing
    private const val DIRECTION_LOCK_RATIO = 1.18f

    /**
     * Determines the locked gesture axis from initial drag vector and starting X coordinate.
     */
    fun resolveGestureMode(
        startX: Float,
        viewportWidth: Float,
        dragDx: Float,
        dragDy: Float,
        isLongPressSpeedActive: Boolean
    ): LockedGestureMode {
        if (isLongPressSpeedActive) return LockedGestureMode.DYNAMIC_SPEED_SLIDE
        val absX = abs(dragDx)
        val absY = abs(dragDy)
        return if (absX > absY * DIRECTION_LOCK_RATIO) {
            LockedGestureMode.HORIZONTAL_SEEK
        } else {
            if (startX < viewportWidth / 2f) {
                LockedGestureMode.VERTICAL_BRIGHTNESS
            } else {
                LockedGestureMode.VERTICAL_VOLUME
            }
        }
    }

    /**
     * Computes updated brightness (2%..100%) and returns a [HapticPulseType] if a threshold was crossed.
     */
    fun computeBrightnessStep(
        currentBrightness: Int,
        dragDy: Float,
        viewportHeight: Float
    ): Pair<Int, HapticPulseType?> {
        if (viewportHeight <= 0f) return currentBrightness to null
        val deltaPct = ((-dragDy / viewportHeight) * 135f).toInt()
        if (deltaPct == 0) return currentBrightness to null

        val next = (currentBrightness + deltaPct).coerceIn(2, 100)
        val pulse = detectThresholdCrossing(
            previous = currentBrightness,
            current = next,
            minBound = 2,
            maxBound = 100,
            midThresholds = setOf(50),
            boostThreshold = null
        )
        return next to pulse
    }

    /**
     * Computes updated volume (0%..200% Hardware LoudnessEnhancer) and returns a [HapticPulseType]
     * when crossing 0%, 50%, 100% (Hardware Boost transition), 150%, or 200% limits.
     */
    fun computeVolumeStep(
        currentVolume: Int,
        dragDy: Float,
        viewportHeight: Float
    ): Pair<Int, HapticPulseType?> {
        if (viewportHeight <= 0f) return currentVolume to null
        val deltaPct = ((-dragDy / viewportHeight) * 160f).toInt()
        if (deltaPct == 0) return currentVolume to null

        val next = (currentVolume + deltaPct).coerceIn(0, 200)
        val pulse = detectThresholdCrossing(
            previous = currentVolume,
            current = next,
            minBound = 0,
            maxBound = 200,
            midThresholds = setOf(50, 150),
            boostThreshold = 100
        )
        return next to pulse
    }

    /**
     * Computes updated seek position (0L..durationMs) and returns a [HapticPulseType]
     * whenever a 10-second seek step or video start/end boundary is crossed.
     */
    fun computeHorizontalSeekStep(
        currentPositionMs: Long,
        durationMs: Long,
        dragDx: Float,
        viewportWidth: Float
    ): Pair<Long, HapticPulseType?> {
        val safeDuration = durationMs.coerceAtLeast(1000L)
        if (viewportWidth <= 0f) return currentPositionMs to null

        // Full screen width swipe scrubs up to 90 seconds (or 50% of short clips)
        val maxScrubSpanMs = 90_000L.coerceAtMost((safeDuration * 0.75f).toLong().coerceAtLeast(15_000L))
        val deltaMs = ((dragDx / viewportWidth) * maxScrubSpanMs).toLong()
        if (deltaMs == 0L) return currentPositionMs to null

        val nextPos = (currentPositionMs + deltaMs).coerceIn(0L, safeDuration)
        val pulse = when {
            (nextPos == 0L && currentPositionMs > 0L) ||
                (nextPos == safeDuration && currentPositionMs < safeDuration) -> HapticPulseType.BOUNDARY_LIMIT
            (nextPos / SEEK_HAPTIC_STEP_MS) != (currentPositionMs / SEEK_HAPTIC_STEP_MS) -> HapticPulseType.STEP_TICK
            else -> null
        }
        return nextPos to pulse
    }

    /**
     * Computes dynamic long-press speed slide (0.25x..8.0x) and triggers haptic ticks at preset notches.
     */
    fun computeDynamicSpeedStep(
        currentSpeed: Float,
        dragDx: Float,
        viewportWidth: Float
    ): Pair<Float, HapticPulseType?> {
        if (viewportWidth <= 0f) return currentSpeed to null
        val deltaSpeed = (dragDx / viewportWidth) * 6.0f
        val nextSpeed = (currentSpeed + deltaSpeed).coerceIn(0.25f, 8.0f)

        val prevNotch = (currentSpeed * 2f).toInt()
        val nextNotch = (nextSpeed * 2f).toInt()
        val pulse = when {
            (nextSpeed == 0.25f && currentSpeed > 0.25f) ||
                (nextSpeed == 8.0f && currentSpeed < 8.0f) -> HapticPulseType.BOUNDARY_LIMIT
            prevNotch != nextNotch -> HapticPulseType.STEP_TICK
            else -> null
        }
        return nextSpeed to pulse
    }

    /**
     * Checks whether moving from [previous] to [current] hit or crossed a threshold value.
     */
    internal fun detectThresholdCrossing(
        previous: Int,
        current: Int,
        minBound: Int,
        maxBound: Int,
        midThresholds: Set<Int>,
        boostThreshold: Int?
    ): HapticPulseType? {
        if (previous == current) return null

        // Boundary limits (min or max)
        if ((current == minBound && previous > minBound) || (current == maxBound && previous < maxBound)) {
            return HapticPulseType.BOUNDARY_LIMIT
        }

        // Boost transition threshold (e.g. 100% volume before entering 101-200% boost)
        if (boostThreshold != null) {
            val crossedUp = previous < boostThreshold && current >= boostThreshold
            val crossedDown = previous > boostThreshold && current <= boostThreshold
            if (crossedUp || crossedDown) {
                return HapticPulseType.BOOST_THRESHOLD
            }
        }

        // Midpoint notches (e.g. 50%, 150%)
        for (threshold in midThresholds) {
            val crossedUp = previous < threshold && current >= threshold
            val crossedDown = previous > threshold && current <= threshold
            if (crossedUp || crossedDown) {
                return HapticPulseType.MIDPOINT_TICK
            }
        }

        return null
    }

    /**
     * Fires hardware haptic vibration feedback on the device using VibratorManager / Vibrator
     * and View haptic feedback constants.
     */
    fun triggerHapticPulse(
        context: Context,
        view: View? = null,
        pulseType: HapticPulseType = HapticPulseType.MIDPOINT_TICK
    ) {
        try {
            view?.performHapticFeedback(
                when (pulseType) {
                    HapticPulseType.STEP_TICK -> HapticFeedbackConstants.CLOCK_TICK
                    HapticPulseType.MIDPOINT_TICK -> HapticFeedbackConstants.VIRTUAL_KEY
                    HapticPulseType.BOOST_THRESHOLD -> HapticFeedbackConstants.LONG_PRESS
                    HapticPulseType.BOUNDARY_LIMIT -> HapticFeedbackConstants.LONG_PRESS
                }
            )
        } catch (_: Exception) {
        }

        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(pulseType.durationMs, pulseType.amplitude)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pulseType.durationMs)
                }
            }
        } catch (_: Exception) {
        }
    }

    fun formatTimestamp(ms: Long): String {
        val totalSecs = (ms / 1000L).coerceAtLeast(0L)
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val seconds = totalSecs % 60
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}

/**
 * Reusable Compose modifier that attaches Hybrid Player's direction-locked gesture detector
 * (Vertical Brightness, Vertical 200% Volume Boost, Horizontal Seek, Double-Tap 10s Seek,
 * and Long-Press Dynamic Speed Slide) with threshold haptic feedback.
 */
fun Modifier.detectVideoPlayerGestures(
    context: Context,
    hostView: View?,
    isLocked: Boolean,
    getBrightness: () -> Int,
    getVolume: () -> Int,
    getCurrentPositionMs: () -> Long,
    getDurationMs: () -> Long,
    getPlaybackSpeed: () -> Float,
    getIsLongPressSpeedActive: () -> Boolean = { false },
    onSingleTap: () -> Unit,
    onDoubleTapSeek: (deltaMs: Long, targetMs: Long) -> Unit,
    onBrightnessChanged: (newPercent: Int, hud: GestureHudState) -> Unit,
    onVolumeChanged: (newPercent: Int, hud: GestureHudState) -> Unit,
    onHorizontalSeekChanged: (newPositionMs: Long, hud: GestureHudState) -> Unit,
    onDynamicSpeedChanged: (newSpeed: Float, isActive: Boolean, hud: GestureHudState?) -> Unit,
    onGestureEnded: () -> Unit
): Modifier {
    return this
        .pointerInput(isLocked) {
            var isLongPressActive = false
            detectTapGestures(
                onTap = { onSingleTap() },
                onDoubleTap = { tapOffset ->
                    if (!isLocked) {
                        val delta = if (tapOffset.x < size.width / 2f) -10_000L else 10_000L
                        val target = (getCurrentPositionMs() + delta).coerceIn(0L, getDurationMs().coerceAtLeast(1L))
                        VideoGestureHelper.triggerHapticPulse(context, hostView, HapticPulseType.STEP_TICK)
                        onDoubleTapSeek(delta, target)
                    }
                },
                onLongPress = {
                    if (!isLocked) {
                        isLongPressActive = true
                        val defaultBoostSpeed = 2.0f
                        VideoGestureHelper.triggerHapticPulse(context, hostView, HapticPulseType.BOOST_THRESHOLD)
                        onDynamicSpeedChanged(
                            defaultBoostSpeed,
                            true,
                            GestureHudState(
                                mode = LockedGestureMode.DYNAMIC_SPEED_SLIDE,
                                titleText = "⏩ Dynamic Speed: 2.00x",
                                subText = "Slide Left / Right (0.25x – 8.0x)",
                                progressFraction = (defaultBoostSpeed / 8.0f).coerceIn(0f, 1f),
                                thresholdReached = true
                            )
                        )
                    }
                },
                onPress = {
                    tryAwaitRelease()
                    if (isLongPressActive) {
                        isLongPressActive = false
                        VideoGestureHelper.triggerHapticPulse(context, hostView, HapticPulseType.STEP_TICK)
                        onDynamicSpeedChanged(1.0f, false, null)
                        onGestureEnded()
                    }
                }
            )
        }
        .pointerInput(isLocked) {
            if (!isLocked) {
                var activeMode = LockedGestureMode.NONE
                var startTouchX = 0f
                var seekOriginMs = 0L

                detectDragGestures(
                    onDragStart = { startOffset ->
                        startTouchX = startOffset.x
                        seekOriginMs = getCurrentPositionMs()
                        activeMode = if (getIsLongPressSpeedActive()) {
                            LockedGestureMode.DYNAMIC_SPEED_SLIDE
                        } else {
                            LockedGestureMode.NONE
                        }
                    },
                    onDragEnd = {
                        if (activeMode == LockedGestureMode.DYNAMIC_SPEED_SLIDE) {
                            onDynamicSpeedChanged(1.0f, false, null)
                        }
                        activeMode = LockedGestureMode.NONE
                        onGestureEnded()
                    },
                    onDragCancel = {
                        if (activeMode == LockedGestureMode.DYNAMIC_SPEED_SLIDE) {
                            onDynamicSpeedChanged(1.0f, false, null)
                        }
                        activeMode = LockedGestureMode.NONE
                        onGestureEnded()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)

                        if (activeMode == LockedGestureMode.NONE) {
                            activeMode = VideoGestureHelper.resolveGestureMode(
                                startX = startTouchX,
                                viewportWidth = w,
                                dragDx = dragAmount.x,
                                dragDy = dragAmount.y,
                                isLongPressSpeedActive = getIsLongPressSpeedActive()
                            )
                        }

                        when (activeMode) {
                            LockedGestureMode.VERTICAL_BRIGHTNESS -> {
                                val current = getBrightness()
                                val (next, pulse) = VideoGestureHelper.computeBrightnessStep(
                                    currentBrightness = current,
                                    dragDy = dragAmount.y,
                                    viewportHeight = h
                                )
                                if (pulse != null) {
                                    VideoGestureHelper.triggerHapticPulse(context, hostView, pulse)
                                }
                                val subLabel = when (next) {
                                    2 -> "Minimum Night Mode (2%)"
                                    50 -> "Balanced Studio (50%)"
                                    100 -> "Maximum Peak Brightness (100%)"
                                    else -> "Vertical Left Swipe"
                                }
                                onBrightnessChanged(
                                    next,
                                    GestureHudState(
                                        mode = LockedGestureMode.VERTICAL_BRIGHTNESS,
                                        titleText = "Brightness $next%",
                                        subText = subLabel,
                                        progressFraction = (next / 100f).coerceIn(0.02f, 1f),
                                        thresholdReached = next in VideoGestureHelper.BRIGHTNESS_THRESHOLDS
                                    )
                                )
                            }

                            LockedGestureMode.VERTICAL_VOLUME -> {
                                val current = getVolume()
                                val (next, pulse) = VideoGestureHelper.computeVolumeStep(
                                    currentVolume = current,
                                    dragDy = dragAmount.y,
                                    viewportHeight = h
                                )
                                if (pulse != null) {
                                    VideoGestureHelper.triggerHapticPulse(context, hostView, pulse)
                                }
                                val isBoost = next > 100
                                val subLabel = when (next) {
                                    0 -> "Muted (0%)"
                                    100 -> "Standard Max (100% Threshold)"
                                    200 -> "MAX 200% Hardware Loudness Boost!"
                                    else -> if (isBoost) "Hardware LoudnessEnhancer Boost" else "Standard Audio Range"
                                }
                                onVolumeChanged(
                                    next,
                                    GestureHudState(
                                        mode = LockedGestureMode.VERTICAL_VOLUME,
                                        titleText = if (isBoost) "Boost Volume $next%" else "Volume $next%",
                                        subText = subLabel,
                                        progressFraction = (next / 200f).coerceIn(0f, 1f),
                                        isBoostZone = isBoost,
                                        thresholdReached = next in VideoGestureHelper.VOLUME_THRESHOLDS
                                    )
                                )
                            }

                            LockedGestureMode.HORIZONTAL_SEEK -> {
                                val current = getCurrentPositionMs()
                                val total = getDurationMs().coerceAtLeast(1000L)
                                val (nextPos, pulse) = VideoGestureHelper.computeHorizontalSeekStep(
                                    currentPositionMs = current,
                                    durationMs = total,
                                    dragDx = dragAmount.x,
                                    viewportWidth = w
                                )
                                if (pulse != null) {
                                    VideoGestureHelper.triggerHapticPulse(context, hostView, pulse)
                                }
                                val diffSec = ((nextPos - seekOriginMs) / 1000L)
                                val diffPrefix = if (diffSec >= 0) "+${diffSec}s" else "${diffSec}s"
                                onHorizontalSeekChanged(
                                    nextPos,
                                    GestureHudState(
                                        mode = LockedGestureMode.HORIZONTAL_SEEK,
                                        titleText = "${VideoGestureHelper.formatTimestamp(nextPos)} / ${VideoGestureHelper.formatTimestamp(total)}",
                                        subText = "Scrubbing ($diffPrefix)",
                                        progressFraction = (nextPos.toFloat() / total.toFloat()).coerceIn(0f, 1f),
                                        thresholdReached = pulse != null
                                    )
                                )
                            }

                            LockedGestureMode.DYNAMIC_SPEED_SLIDE -> {
                                val currentSpd = getPlaybackSpeed()
                                val (nextSpd, pulse) = VideoGestureHelper.computeDynamicSpeedStep(
                                    currentSpeed = currentSpd,
                                    dragDx = dragAmount.x,
                                    viewportWidth = w
                                )
                                if (pulse != null) {
                                    VideoGestureHelper.triggerHapticPulse(context, hostView, pulse)
                                }
                                onDynamicSpeedChanged(
                                    nextSpd,
                                    true,
                                    GestureHudState(
                                        mode = LockedGestureMode.DYNAMIC_SPEED_SLIDE,
                                        titleText = String.format(Locale.US, "⏩ Speed %.2fx", nextSpd),
                                        subText = "Slide Left / Right",
                                        progressFraction = (nextSpd / 8.0f).coerceIn(0f, 1f),
                                        thresholdReached = pulse != null
                                    )
                                )
                            }

                            LockedGestureMode.NONE -> Unit
                        }
                    }
                )
            }
        }
}

/**
 * Center HUD Overlay displaying real-time Brightness, Volume (with 100% / 200% threshold markers),
 * Horizontal Seek progress bar, or Dynamic Speed slide feedback.
 */
@Composable
fun VideoGestureHudOverlay(
    hudState: GestureHudState?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = hudState != null && hudState.mode != LockedGestureMode.NONE,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val state = hudState ?: return@AnimatedVisibility
        val accent = when {
            state.isBoostZone -> HybridGold
            state.thresholdReached -> HybridCyan
            else -> HybridEmerald
        }

        val icon = when (state.mode) {
            LockedGestureMode.VERTICAL_BRIGHTNESS -> Icons.Default.BrightnessHigh
            LockedGestureMode.VERTICAL_VOLUME -> {
                if (state.progressFraction <= 0.005f) Icons.AutoMirrored.Filled.VolumeMute
                else Icons.AutoMirrored.Filled.VolumeUp
            }
            LockedGestureMode.HORIZONTAL_SEEK -> Icons.Default.FastForward
            LockedGestureMode.DYNAMIC_SPEED_SLIDE -> Icons.Default.Speed
            LockedGestureMode.NONE -> Icons.Default.Info
        }

        Column(
            modifier = Modifier
                .widthIn(min = 210.dp, max = 270.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xD90A0A10))
                .border(
                    width = if (state.thresholdReached) 1.5.dp else 1.dp,
                    color = accent,
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = state.titleText,
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = state.titleText,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = state.subText,
                color = if (state.isBoostZone) HybridGold else HybridTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Custom Canvas Progress Bar with Threshold Tick Marks (e.g., 50% / 100% Boost Marker)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            ) {
                val barWidth = size.width
                val barHeight = size.height

                // Track background
                drawRoundRect(
                    color = Color(0x33FFFFFF),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barHeight / 2f)
                )

                // Active progress fill
                val fillW = (barWidth * state.progressFraction.coerceIn(0f, 1f))
                drawRoundRect(
                    color = accent,
                    size = Size(fillW, barHeight),
                    cornerRadius = CornerRadius(barHeight / 2f)
                )

                // Draw midpoint / 100% threshold marker line at 50% of bar width
                val midX = barWidth * 0.5f
                drawLine(
                    color = Color.White.copy(alpha = 0.85f),
                    start = Offset(midX, 0f),
                    end = Offset(midX, barHeight),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
    }
}
