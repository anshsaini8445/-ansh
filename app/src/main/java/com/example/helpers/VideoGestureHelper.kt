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
                var startBrightness = 75
                var startVolume = 100
                var startSpeed = 1.0f
                var totalDragDx = 0f
                var totalDragDy = 0f

                detectDragGestures(
                    onDragStart = { startOffset ->
                        startTouchX = startOffset.x
                        seekOriginMs = getCurrentPositionMs()
                        startBrightness = getBrightness()
                        startVolume = getVolume()
                        startSpeed = getPlaybackSpeed()
                        totalDragDx = 0f
                        totalDragDy = 0f
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
                        totalDragDx += dragAmount.x
                        totalDragDy += dragAmount.y

                        if (activeMode == LockedGestureMode.NONE) {
                            if (abs(totalDragDx) > 8f || abs(totalDragDy) > 8f) {
                                activeMode = VideoGestureHelper.resolveGestureMode(
                                    startX = startTouchX,
                                    viewportWidth = w,
                                    dragDx = totalDragDx,
                                    dragDy = totalDragDy,
                                    isLongPressSpeedActive = getIsLongPressSpeedActive()
                                )
                            }
                        }

                        when (activeMode) {
                            LockedGestureMode.VERTICAL_BRIGHTNESS -> {
                                val prev = getBrightness()
                                val deltaPct = ((-totalDragDy / h) * 125f).toInt()
                                val next = (startBrightness + deltaPct).coerceIn(2, 100)
                                val pulse = VideoGestureHelper.detectThresholdCrossing(
                                    previous = prev,
                                    current = next,
                                    minBound = 2,
                                    maxBound = 100,
                                    midThresholds = setOf(50),
                                    boostThreshold = null
                                )
                                if (pulse != null) {
                                    VideoGestureHelper.triggerHapticPulse(context, hostView, pulse)
                                }
                                onBrightnessChanged(
                                    next,
                                    GestureHudState(
                                        mode = LockedGestureMode.VERTICAL_BRIGHTNESS,
                                        titleText = "Brightness $next%",
                                        subText = "Slide Up / Down",
                                        progressFraction = (next / 100f).coerceIn(0.02f, 1f),
                                        thresholdReached = next in VideoGestureHelper.BRIGHTNESS_THRESHOLDS
                                    )
                                )
                            }

                            LockedGestureMode.VERTICAL_VOLUME -> {
                                val prev = getVolume()
                                val deltaPct = ((-totalDragDy / h) * 160f).toInt()
                                val next = (startVolume + deltaPct).coerceIn(0, 200)
                                val pulse = VideoGestureHelper.detectThresholdCrossing(
                                    previous = prev,
                                    current = next,
                                    minBound = 0,
                                    maxBound = 200,
                                    midThresholds = setOf(50, 150),
                                    boostThreshold = 100
                                )
                                if (pulse != null) {
                                    VideoGestureHelper.triggerHapticPulse(context, hostView, pulse)
                                }
                                val isBoost = next > 100
                                onVolumeChanged(
                                    next,
                                    GestureHudState(
                                        mode = LockedGestureMode.VERTICAL_VOLUME,
                                        titleText = "Volume $next%",
                                        subText = if (isBoost) "200% Boost Active" else "Slide Up / Down",
                                        progressFraction = (next / 200f).coerceIn(0f, 1f),
                                        isBoostZone = isBoost,
                                        thresholdReached = next in VideoGestureHelper.VOLUME_THRESHOLDS
                                    )
                                )
                            }

                            LockedGestureMode.HORIZONTAL_SEEK -> {
                                val prevPos = getCurrentPositionMs()
                                val total = getDurationMs().coerceAtLeast(1000L)
                                // Smooth slow-or-fast horizontal finger scrubbing across video
                                val maxScrubSpanMs = 120_000L.coerceAtMost((total * 0.85f).toLong().coerceAtLeast(15_000L))
                                val deltaMs = ((totalDragDx / w) * maxScrubSpanMs).toLong()
                                val nextPos = (seekOriginMs + deltaMs).coerceIn(0L, total)
                                val pulse = when {
                                    (nextPos == 0L && prevPos > 0L) ||
                                        (nextPos == total && prevPos < total) -> HapticPulseType.BOUNDARY_LIMIT
                                    (nextPos / VideoGestureHelper.SEEK_HAPTIC_STEP_MS) != (prevPos / VideoGestureHelper.SEEK_HAPTIC_STEP_MS) -> HapticPulseType.STEP_TICK
                                    else -> null
                                }
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
                                        subText = diffPrefix,
                                        progressFraction = (nextPos.toFloat() / total.toFloat()).coerceIn(0f, 1f),
                                        thresholdReached = pulse != null
                                    )
                                )
                            }

                            LockedGestureMode.HORIZONTAL_SEEK -> {
                                val prevPos = getCurrentPositionMs()
                                val total = getDurationMs().coerceAtLeast(1000L)
                                // Smooth slow-or-fast horizontal finger scrubbing across video
                                val maxScrubSpanMs = 120_000L.coerceAtMost((total * 0.85f).toLong().coerceAtLeast(15_000L))
                                val deltaMs = ((totalDragDx / w) * maxScrubSpanMs).toLong()
                                val nextPos = (seekOriginMs + deltaMs).coerceIn(0L, total)
                                val pulse = when {
                                    (nextPos == 0L && prevPos > 0L) ||
                                        (nextPos == total && prevPos < total) -> HapticPulseType.BOUNDARY_LIMIT
                                    (nextPos / VideoGestureHelper.SEEK_HAPTIC_STEP_MS) != (prevPos / VideoGestureHelper.SEEK_HAPTIC_STEP_MS) -> HapticPulseType.STEP_TICK
                                    else -> null
                                }
                                if (pulse != null) {
                                    VideoGestureHelper.triggerHapticPulse(context, hostView, pulse)
                                }
                                val diffMs = nextPos - seekOriginMs
                                val sign = if (diffMs >= 0) "+" else "-"
                                val absMs = abs(diffMs)
                                val diffSec = absMs / 1000L
                                val diffTenth = (absMs % 1000L) / 100L
                                val diffFormatted = String.format(Locale.US, "%s%02d:%02d.%1ds", sign, diffSec / 60, diffSec % 60, diffTenth)
                                onHorizontalSeekChanged(
                                    nextPos,
                                    GestureHudState(
                                        mode = LockedGestureMode.HORIZONTAL_SEEK,
                                        titleText = "${VideoGestureHelper.formatTimestamp(nextPos)} / ${VideoGestureHelper.formatTimestamp(total)}",
                                        subText = diffFormatted,
                                        progressFraction = (nextPos.toFloat() / total.toFloat()).coerceIn(0f, 1f),
                                        thresholdReached = pulse != null
                                    )
                                )
                            }

                            LockedGestureMode.DYNAMIC_SPEED_SLIDE -> {
                                val prevSpd = getPlaybackSpeed()
                                // Fine-grain continuous speed scale from 0.25x to 8.0x
                                val deltaSpd = (totalDragDx / w) * 7.75f
                                val nextSpd = (startSpeed + deltaSpd).coerceIn(0.25f, 8.0f)
                                val pulse = if ((prevSpd * 2f).toInt() != (nextSpd * 2f).toInt()) HapticPulseType.STEP_TICK else null
                                if (pulse != null) {
                                    VideoGestureHelper.triggerHapticPulse(context, hostView, pulse)
                                }
                                val speedFraction = ((nextSpd - 0.25f) / (8.0f - 0.25f)).coerceIn(0f, 1f)
                                val speedTier = when {
                                    nextSpd >= 6.0f -> "⚡ ULTRA TURBO 8X"
                                    nextSpd >= 4.0f -> "⏩ HIGH SPEED 4X"
                                    nextSpd >= 2.0f -> "⚡ FAST FORWARD 2X"
                                    nextSpd >= 1.0f -> "▶ NORMAL TO FAST"
                                    else -> "🐢 SLOW MOTION 0.25X"
                                }
                                onDynamicSpeedChanged(
                                    nextSpd,
                                    true,
                                    GestureHudState(
                                        mode = LockedGestureMode.DYNAMIC_SPEED_SLIDE,
                                        titleText = String.format(Locale.US, "%.2fx", nextSpd),
                                        subText = speedTier,
                                        progressFraction = speedFraction,
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
 * Center HUD Overlay displaying real-time Speedometer Needle Gauge ("sui" & "bindu"),
 * Millisecond/Tenth-second Horizontal Seek scrubbing card, Brightness, and 200% Volume Boost.
 * Styled in razor-sharp clean white & crystal translucent dark glass.
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

        when (state.mode) {
            LockedGestureMode.DYNAMIC_SPEED_SLIDE -> {
                // Realistic Speedometer Gauge with Glowing Needle ("Sui") & Graduation Dots ("Bindu")
                SpeedometerGaugeHud(state = state)
            }
            LockedGestureMode.HORIZONTAL_SEEK -> {
                // High-precision Seek Scrubbing Card with +/- seconds/tenths
                SeekScrubHud(state = state)
            }
            else -> {
                // Standard Clean White/Glass HUD for Brightness & Volume (with 200% Boost Indicator)
                StandardLevelHud(state = state)
            }
        }
    }
}

/**
 * Realistic Speedometer Gauge Dial with an animated glowing needle ("sui"),
 * graduation dots & tick marks ("bindu" at 0.25x, 0.5x, 1x, 2x, 4x, 6x, 8x),
 * and large center digital readout.
 */
@Composable
private fun SpeedometerGaugeHud(state: GestureHudState) {
    Column(
        modifier = Modifier
            .widthIn(min = 260.dp, max = 290.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xE60A0D16))
            .border(1.2.dp, Color(0x55FFFFFF), RoundedCornerShape(26.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "SPEEDOMETER",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Canvas Speedometer Arc & Glowing Needle ("Sui" & "Bindu")
        Box(
            modifier = Modifier.size(190.dp, 120.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height * 0.95f
                val radius = size.width * 0.42f
                val arcStartAngle = 180f
                val arcSweep = 180f

                // Outer Background Arc Track
                drawArc(
                    color = Color(0x33FFFFFF),
                    startAngle = arcStartAngle,
                    sweepAngle = arcSweep,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 8.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    ),
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2f, radius * 2f)
                )

                // Active Filled Arc
                val activeSweep = (arcSweep * state.progressFraction).coerceIn(2f, arcSweep)
                drawArc(
                    color = Color.White,
                    startAngle = arcStartAngle,
                    sweepAngle = activeSweep,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 8.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    ),
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2f, radius * 2f)
                )

                // Graduation Notches & Dots ("Bindu") at 0.25x, 0.5x, 1x, 2x, 4x, 6x, 8x
                val notches = listOf(0.0f, 0.12f, 0.25f, 0.45f, 0.65f, 0.85f, 1.0f)
                for (n in notches) {
                    val angleDeg = arcStartAngle + (arcSweep * n)
                    val rad = Math.toRadians(angleDeg.toDouble())
                    val outerX = cx + (radius + 9.dp.toPx()) * Math.cos(rad).toFloat()
                    val outerY = cy + (radius + 9.dp.toPx()) * Math.sin(rad).toFloat()
                    val innerX = cx + (radius - 9.dp.toPx()) * Math.cos(rad).toFloat()
                    val innerY = cy + (radius - 9.dp.toPx()) * Math.sin(rad).toFloat()

                    // Tick line
                    drawLine(
                        color = Color.White.copy(alpha = if (n <= state.progressFraction) 0.95f else 0.4f),
                        start = Offset(innerX, innerY),
                        end = Offset(outerX, outerY),
                        strokeWidth = 2.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )

                    // Dot ("Bindu") slightly outside the track
                    val dotX = cx + (radius + 18.dp.toPx()) * Math.cos(rad).toFloat()
                    val dotY = cy + (radius + 18.dp.toPx()) * Math.sin(rad).toFloat()
                    drawCircle(
                        color = if (n <= state.progressFraction) Color.White else Color.White.copy(alpha = 0.35f),
                        radius = 2.5.dp.toPx(),
                        center = Offset(dotX, dotY)
                    )
                }

                // Center Needle ("Sui")
                val needleAngleDeg = arcStartAngle + (arcSweep * state.progressFraction)
                val needleRad = Math.toRadians(needleAngleDeg.toDouble())
                val needleLen = radius * 0.88f
                val tipX = cx + needleLen * Math.cos(needleRad).toFloat()
                val tipY = cy + needleLen * Math.sin(needleRad).toFloat()

                // Needle Shadow / Glow
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = Offset(cx, cy),
                    end = Offset(tipX, tipY),
                    strokeWidth = 6.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                // Crisp White Needle Line
                drawLine(
                    color = Color.White,
                    start = Offset(cx, cy),
                    end = Offset(tipX, tipY),
                    strokeWidth = 3.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )

                // Center Hub Pivot
                drawCircle(
                    color = Color.White,
                    radius = 7.dp.toPx(),
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = Color(0xFF0A0D16),
                    radius = 3.5.dp.toPx(),
                    center = Offset(cx, cy)
                )
            }

            // Big Speed Text Centered Above Pivot
            Text(
                text = state.titleText,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = state.subText,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Hold & Slide Left/Right: 0.25x – 8.0x",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp
        )
    }
}

/**
 * Clean Seek Scrubbing HUD card displaying +/- seconds & tenths, target/total duration,
 * and a razor-sharp scrubber track.
 */
@Composable
private fun SeekScrubHud(state: GestureHudState) {
    Column(
        modifier = Modifier
            .widthIn(min = 230.dp, max = 280.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xE60A0D16))
            .border(1.2.dp, Color(0x55FFFFFF), RoundedCornerShape(22.dp))
            .padding(horizontal = 22.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            val isForward = !state.subText.startsWith("-")
            Icon(
                imageVector = if (isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = state.subText,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = state.titleText,
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Clean White Scrubber Bar
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        ) {
            val barWidth = size.width
            val barHeight = size.height

            // Background Track
            drawRoundRect(
                color = Color(0x33FFFFFF),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barHeight / 2f)
            )

            // Progress Fill
            val fillW = (barWidth * state.progressFraction.coerceIn(0f, 1f))
            drawRoundRect(
                color = Color.White,
                size = Size(fillW, barHeight),
                cornerRadius = CornerRadius(barHeight / 2f)
            )
        }
    }
}

/**
 * Clean White Level HUD for Brightness & Volume (with 200% Hardware Boost indication).
 */
@Composable
private fun StandardLevelHud(state: GestureHudState) {
    val icon = when (state.mode) {
        LockedGestureMode.VERTICAL_BRIGHTNESS -> Icons.Default.BrightnessHigh
        LockedGestureMode.VERTICAL_VOLUME -> {
            if (state.progressFraction <= 0.005f) Icons.AutoMirrored.Filled.VolumeMute
            else Icons.AutoMirrored.Filled.VolumeUp
        }
        else -> Icons.Default.Info
    }

    Column(
        modifier = Modifier
            .widthIn(min = 210.dp, max = 260.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xE60A0D16))
            .border(1.2.dp, Color(0x55FFFFFF), RoundedCornerShape(22.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = state.titleText,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = state.titleText,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = state.subText,
            color = Color.White.copy(alpha = 0.75f),
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
                color = Color.White,
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
