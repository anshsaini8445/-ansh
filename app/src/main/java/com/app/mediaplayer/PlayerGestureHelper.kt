package com.app.mediaplayer

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import java.util.Locale
import kotlin.math.abs

class PlayerGestureHelper(
    private val context: Context,
    private val hostView: View? = null,
    private val getBrightnessPercent: () -> Int,
    private val getVolumePercent: () -> Int,
    private val getCurrentPositionMs: () -> Long,
    private val getDurationMs: () -> Long,
    private val getPlaybackSpeed: () -> Float = { 1.0f },
    private val onSingleTapConfirmed: () -> Unit = {},
    private val onDoubleTapSeek: (deltaMs: Long, targetPositionMs: Long, hudState: GestureHudState) -> Unit = { _, _, _ -> },
    private val onBrightnessChanged: (newPercent: Int, hudState: GestureHudState) -> Unit = { _, _ -> },
    private val onVolumeChanged: (newPercent: Int, hudState: GestureHudState) -> Unit = { _, _ -> },
    private val onSeekChanged: (newPositionMs: Long, hudState: GestureHudState) -> Unit = { _, _ -> },
    private val onDynamicSpeedChanged: (newSpeed: Float, active: Boolean, hudState: GestureHudState?) -> Unit = { _, _, _ -> },
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
    private var isLongPressSpeedActive: Boolean = false

    val currentGestureMode: LockedGestureMode
        get() = activeMode

    private val tapDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                onSingleTapConfirmed()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isLocked) return false
                val vw = (hostView?.width ?: 1080).toFloat().coerceAtLeast(1f)
                val deltaMs = if (e.x < vw / 2f) -10_000L else 10_000L
                val duration = getDurationMs().coerceAtLeast(1000L)
                val targetMs = (getCurrentPositionMs() + deltaMs).coerceIn(0L, duration)
                performThresholdHaptic(hostView, HapticPulseType.STEP_TICK)
                val label = if (deltaMs < 0) "⏪ Rewind 10s" else "⏩ Fast-Forward 10s"
                onDoubleTapSeek(
                    deltaMs,
                    targetMs,
                    GestureHudState(
                        mode = LockedGestureMode.HORIZONTAL_SEEK,
                        titleText = label,
                        subText = "${VideoGestureHelper.formatTimestamp(targetMs)} / ${VideoGestureHelper.formatTimestamp(duration)}",
                        progressFraction = (targetMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                        thresholdReached = true
                    )
                )
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (isLocked || activeMode != LockedGestureMode.NONE) return
                isLongPressSpeedActive = true
                activeMode = LockedGestureMode.DYNAMIC_SPEED_SLIDE
                val boostSpeed = 2.0f
                performThresholdHaptic(hostView, HapticPulseType.BOOST_THRESHOLD)
                onDynamicSpeedChanged(
                    boostSpeed,
                    true,
                    GestureHudState(
                        mode = LockedGestureMode.DYNAMIC_SPEED_SLIDE,
                        titleText = "⏩ Dynamic Speed 2.00x",
                        subText = "Slide Left / Right (0.25x - 8.0x)",
                        progressFraction = (boostSpeed / 8.0f).coerceIn(0f, 1f),
                        thresholdReached = true
                    )
                )
            }
        }
    )

    fun setScreenLocked(locked: Boolean) {
        isLocked = locked
        if (locked && activeMode != LockedGestureMode.NONE) {
            resetGesture()
        }
    }

    fun isScreenLocked(): Boolean = isLocked

    override fun onTouch(view: View?, event: MotionEvent?): Boolean {
        if (event == null) return false
        tapDetector.onTouchEvent(event)
        return onTouchEvent(
            event = event,
            viewportWidth = (view?.width ?: hostView?.width ?: 1080).toFloat().coerceAtLeast(1f),
            viewportHeight = (view?.height ?: hostView?.height ?: 1920).toFloat().coerceAtLeast(1f),
            targetView = view ?: hostView
        )
    }

    fun onTouchEvent(
        event: MotionEvent,
        viewportWidth: Float,
        viewportHeight: Float,
        targetView: View? = hostView
    ): Boolean {
        if (isLocked) return true

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
                isLongPressSpeedActive = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val totalDx = event.x - downX
                val totalDy = event.y - downY
                val stepDx = event.x - lastX
                val stepDy = event.y - lastY

                if (activeMode == LockedGestureMode.NONE) {
                    if (abs(totalDx) < touchSlop && abs(totalDy) < touchSlop) {
                        return true
                    }
                    activeMode = VideoGestureHelper.resolveGestureMode(
                        startX = downX,
                        viewportWidth = safeWidth,
                        dragDx = totalDx,
                        dragDy = totalDy,
                        isLongPressSpeedActive = isLongPressSpeedActive
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

                    LockedGestureMode.DYNAMIC_SPEED_SLIDE -> {
                        val currentSpd = getPlaybackSpeed()
                        val (nextSpd, pulse) = VideoGestureHelper.computeDynamicSpeedStep(
                            currentSpeed = currentSpd,
                            dragDx = stepDx,
                            viewportWidth = safeWidth
                        )
                        if (pulse != null) {
                            performThresholdHaptic(targetView, pulse)
                        }
                        onDynamicSpeedChanged(
                            nextSpd,
                            true,
                            GestureHudState(
                                mode = LockedGestureMode.DYNAMIC_SPEED_SLIDE,
                                titleText = String.format(Locale.US, "⏩ Speed %.2fx", nextSpd),
                                subText = "Release to restore 1.0x",
                                progressFraction = (nextSpd / 8.0f).coerceIn(0f, 1f),
                                thresholdReached = pulse != null
                            )
                        )
                        return true
                    }

                    LockedGestureMode.NONE -> return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val finishedMode = activeMode
                if (isLongPressSpeedActive || finishedMode == LockedGestureMode.DYNAMIC_SPEED_SLIDE) {
                    isLongPressSpeedActive = false
                    performThresholdHaptic(targetView, HapticPulseType.STEP_TICK)
                    onDynamicSpeedChanged(1.0f, false, null)
                }
                resetGesture()
                if (finishedMode != LockedGestureMode.NONE) {
                    onGestureEnded(finishedMode)
                }
                return true
            }

            else -> return false
        }
    }

    private fun resetGesture() {
        activeMode = LockedGestureMode.NONE
        isLongPressSpeedActive = false
    }

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
