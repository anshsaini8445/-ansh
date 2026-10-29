package com.example.helpers

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * MotionEvent-based gesture helper for the 8K Pro Video Player.
 * Handles direction-locked vertical swipes for Brightness (left half, 2%..100%)
 * and Volume (right half, 0%..200% Hardware LoudnessEnhancer boost) as well as
 * horizontal swipes for seeking, integrated with [HapticFeedbackConstants] and
 * hardware [Vibrator] to fire haptic pulses at threshold values during interaction.
 */
class PlayerGestureHelper(
    private val context: Context,
    private val hostView: View? = null,
    private val getBrightnessPercent: () -> Int,
    private val getVolumePercent: () -> Int,
    private val getCurrentPositionMs: () -> Long,
    private val getDurationMs: () -> Long,
    private val onBrightnessChanged: (newPercent: Int, hudState: GestureHudState) -> Unit = { _, _ -> },
    private val onVolumeChanged: (newPercent: Int, hudState: GestureHudState) -> Unit = { _, _ -> },
    private val onSeekChanged: (newPositionMs: Long, hudState: GestureHudState) -> Unit = { _, _ -> },
    private val onGestureEnded: (finalMode: LockedGestureMode) -> Unit = {}
) : View.OnTouchListener {

    private val touchSlop: Int = try {
        ViewConfiguration.get(context).scaledTouchSlop.coerceAtLeast(8)
    } catch (_: Exception) {
        16
    }

    private var downX: Float = 0f
    private var downY: Float = 0f
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var seekOriginMs: Long = 0L
    private var activeMode: LockedGestureMode = LockedGestureMode.NONE
    private var isLocked: Boolean = false

    val currentGestureMode: LockedGestureMode
        get() = activeMode

    fun setScreenLocked(locked: Boolean) {
        isLocked = locked
        if (locked && activeMode != LockedGestureMode.NONE) {
            resetGesture()
        }
    }

    override fun onTouch(view: View?, event: MotionEvent?): Boolean {
        if (event == null) return false
        return onTouchEvent(
            event = event,
            viewportWidth = (view?.width ?: hostView?.width ?: 1080).toFloat().coerceAtLeast(1f),
            viewportHeight = (view?.height ?: hostView?.height ?: 1920).toFloat().coerceAtLeast(1f),
            targetView = view ?: hostView
        )
    }

    /**
     * Processes raw [MotionEvent] interactions for vertical brightness/volume swipes
     * and horizontal video seeking, firing [HapticFeedbackConstants] pulses when crossing
     * defined threshold values.
     */
    fun onTouchEvent(
        event: MotionEvent,
        viewportWidth: Float,
        viewportHeight: Float,
        targetView: View? = hostView
    ): Boolean {
        if (isLocked) return false

        val safeWidth = viewportWidth.coerceAtLeast(1f)
        val safeHeight = viewportHeight.coerceAtLeast(1f)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                seekOriginMs = getCurrentPositionMs()
                activeMode = LockedGestureMode.NONE
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val totalDx = event.x - downX
                val totalDy = event.y - downY
                val stepDx = event.x - lastX
                val stepDy = event.y - lastY

                if (activeMode == LockedGestureMode.NONE) {
                    if (abs(totalDx) < touchSlop && abs(totalDy) < touchSlop) {
                        return false
                    }
                    activeMode = VideoGestureHelper.resolveGestureMode(
                        startX = downX,
                        viewportWidth = safeWidth,
                        dragDx = totalDx,
                        dragDy = totalDy,
                        isLongPressSpeedActive = false
                    )
                }

                lastX = event.x
                lastY = event.y

                when (activeMode) {
                    LockedGestureMode.VERTICAL_BRIGHTNESS -> {
                        val current = getBrightnessPercent()
                        val (next, pulse) = VideoGestureHelper.computeBrightnessStep(
                            currentBrightness = current,
                            dragDy = stepDy,
                            viewportHeight = safeHeight
                        )
                        if (pulse != null) {
                            performThresholdHaptic(targetView, pulse)
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
                        return true
                    }

                    LockedGestureMode.VERTICAL_VOLUME -> {
                        val current = getVolumePercent()
                        val (next, pulse) = VideoGestureHelper.computeVolumeStep(
                            currentVolume = current,
                            dragDy = stepDy,
                            viewportHeight = safeHeight
                        )
                        if (pulse != null) {
                            performThresholdHaptic(targetView, pulse)
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
                        return true
                    }

                    LockedGestureMode.HORIZONTAL_SEEK -> {
                        val currentPos = getCurrentPositionMs()
                        val duration = getDurationMs().coerceAtLeast(1000L)
                        val (nextPos, pulse) = VideoGestureHelper.computeHorizontalSeekStep(
                            currentPositionMs = currentPos,
                            durationMs = duration,
                            dragDx = stepDx,
                            viewportWidth = safeWidth
                        )
                        if (pulse != null) {
                            performThresholdHaptic(targetView, pulse)
                        }
                        val diffSec = (nextPos - seekOriginMs) / 1000L
                        val diffPrefix = if (diffSec >= 0) "+${diffSec}s" else "${diffSec}s"
                        onSeekChanged(
                            nextPos,
                            GestureHudState(
                                mode = LockedGestureMode.HORIZONTAL_SEEK,
                                titleText = "${VideoGestureHelper.formatTimestamp(nextPos)} / ${VideoGestureHelper.formatTimestamp(duration)}",
                                subText = "Scrubbing ($diffPrefix)",
                                progressFraction = (nextPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                                thresholdReached = pulse != null
                            )
                        )
                        return true
                    }

                    else -> return false
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val finishedMode = activeMode
                resetGesture()
                if (finishedMode != LockedGestureMode.NONE) {
                    onGestureEnded(finishedMode)
                    return true
                }
                return false
            }

            else -> return false
        }
    }

    private fun resetGesture() {
        activeMode = LockedGestureMode.NONE
    }

    /**
     * Maps [HapticPulseType] to Android's [HapticFeedbackConstants] and triggers both
     * View haptic feedback and hardware [Vibrator] pulses.
     */
    fun performThresholdHaptic(targetView: View?, pulseType: HapticPulseType) {
        val hapticConstant = mapPulseToHapticConstant(pulseType)
        try {
            targetView?.performHapticFeedback(
                hapticConstant,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
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

    companion object {
        /**
         * Returns the exact [HapticFeedbackConstants] constant corresponding to a threshold event.
         */
        fun mapPulseToHapticConstant(pulseType: HapticPulseType): Int {
            return when (pulseType) {
                HapticPulseType.STEP_TICK -> HapticFeedbackConstants.CLOCK_TICK
                HapticPulseType.MIDPOINT_TICK -> HapticFeedbackConstants.VIRTUAL_KEY
                HapticPulseType.BOOST_THRESHOLD -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        HapticFeedbackConstants.CONFIRM
                    } else {
                        HapticFeedbackConstants.LONG_PRESS
                    }
                }
                HapticPulseType.BOUNDARY_LIMIT -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        HapticFeedbackConstants.REJECT
                    } else {
                        HapticFeedbackConstants.LONG_PRESS
                    }
                }
            }
        }
    }
}
