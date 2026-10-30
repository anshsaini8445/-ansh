package com.app.mediaplayer

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class LockedGestureMode {
    NONE,
    VERTICAL_BRIGHTNESS,
    VERTICAL_VOLUME,
    HORIZONTAL_SEEK,
    DYNAMIC_SPEED_SLIDE
}

enum class HapticPulseType(val durationMs: Long, val amplitude: Int) {
    STEP_TICK(14L, 90),
    MIDPOINT_TICK(20L, 140),
    BOOST_THRESHOLD(28L, 200),
    BOUNDARY_LIMIT(36L, 255)
}

data class GestureHudState(
    val mode: LockedGestureMode = LockedGestureMode.NONE,
    val titleText: String = "",
    val subText: String = "",
    val progressFraction: Float = 0f,
    val isBoostZone: Boolean = false,
    val thresholdReached: Boolean = false
)

object VideoGestureHelper {

    val BRIGHTNESS_THRESHOLDS = setOf(2, 50, 100)
    val VOLUME_THRESHOLDS = setOf(0, 50, 100, 150, 200)
    const val SEEK_HAPTIC_STEP_MS = 10_000L
    private const val DIRECTION_LOCK_RATIO = 1.18f

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

    fun computeHorizontalSeekStep(
        currentPositionMs: Long,
        durationMs: Long,
        dragDx: Float,
        viewportWidth: Float
    ): Pair<Long, HapticPulseType?> {
        val safeDuration = durationMs.coerceAtLeast(1000L)
        if (viewportWidth <= 0f) return currentPositionMs to null

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

    internal fun detectThresholdCrossing(
        previous: Int,
        current: Int,
        minBound: Int,
        maxBound: Int,
        midThresholds: Set<Int>,
        boostThreshold: Int?
    ): HapticPulseType? {
        if (previous == current) return null

        if ((current == minBound && previous > minBound) || (current == maxBound && previous < maxBound)) {
            return HapticPulseType.BOUNDARY_LIMIT
        }

        if (boostThreshold != null) {
            val crossedUp = previous < boostThreshold && current >= boostThreshold
            val crossedDown = previous > boostThreshold && current <= boostThreshold
            if (crossedUp || crossedDown) {
                return HapticPulseType.BOOST_THRESHOLD
            }
        }

        for (threshold in midThresholds) {
            val crossedUp = previous < threshold && current >= threshold
            val crossedDown = previous > threshold && current <= threshold
            if (crossedUp || crossedDown) {
                return HapticPulseType.MIDPOINT_TICK
            }
        }

        return null
    }

    fun applyHardwareBrightness(activity: Activity, brightnessPercent: Int) {
        try {
            val clamped = brightnessPercent.coerceIn(2, 100)
            val lp = activity.window.attributes
            lp.screenBrightness = (clamped / 100f).coerceIn(0.02f, 1.0f)
            activity.window.attributes = lp
        } catch (_: Exception) {
        }
    }

    fun readCurrentBrightnessPercent(activity: Activity): Int {
        return try {
            val current = activity.window.attributes.screenBrightness
            if (current in 0.01f..1.0f) {
                (current * 100f).roundToInt().coerceIn(2, 100)
            } else {
                val sys = android.provider.Settings.System.getInt(
                    activity.contentResolver,
                    android.provider.Settings.System.SCREEN_BRIGHTNESS,
                    140
                )
                ((sys / 255f) * 100f).roundToInt().coerceIn(2, 100)
            }
        } catch (_: Exception) {
            65
        }
    }

    fun readCurrentVolumePercent(context: Context): Int {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 70
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, max)
            ((cur.toFloat() / max.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
        } catch (_: Exception) {
            70
        }
    }

    fun applyHardwareVolumeAndBoost(
        context: Context,
        volumePercent: Int,
        loudnessEnhancer: LoudnessEnhancer?
    ) {
        val clamped = volumePercent.coerceIn(0, 200)
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (am != null) {
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                val basePct = clamped.coerceAtMost(100)
                val targetIndex = ((basePct / 100f) * max).roundToInt().coerceIn(0, max)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, 0)
            }
        } catch (_: Exception) {
        }

        try {
            if (loudnessEnhancer != null) {
                if (clamped > 100) {
                    val boostGainMb = ((clamped - 100) * 18).coerceIn(0, 1800)
                    loudnessEnhancer.setTargetGain(boostGainMb)
                    if (!loudnessEnhancer.enabled) {
                        loudnessEnhancer.enabled = true
                    }
                } else {
                    loudnessEnhancer.setTargetGain(0)
                    if (loudnessEnhancer.enabled) {
                        loudnessEnhancer.enabled = false
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    fun triggerHapticPulse(
        context: Context,
        view: View? = null,
        pulseType: HapticPulseType = HapticPulseType.MIDPOINT_TICK
    ) {
        try {
            view?.performHapticFeedback(
                PlayerGestureHelper.mapPulseToHapticConstant(pulseType),
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

    fun formatTimestamp(ms: Long): String {
        val totalSecs = (ms / 1000L).coerceAtLeast(0L)
        val hours = totalSecs / 3600L
        val minutes = (totalSecs % 3600L) / 60L
        val seconds = totalSecs % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    class HudProgressBarDrawable : Drawable() {
        var progressFraction: Float = 0.5f
            set(value) {
                field = value.coerceIn(0f, 1f)
                invalidateSelf()
            }

        var isBoostZone: Boolean = false
            set(value) {
                field = value
                invalidateSelf()
            }

        var thresholdReached: Boolean = false
            set(value) {
                field = value
                invalidateSelf()
            }

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#33FFFFFF")
            style = Paint.Style.FILL
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        private val rect = RectF()

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return

            val radius = h / 2f
            rect.set(0f, 0f, w, h)
            canvas.drawRoundRect(rect, radius, radius, trackPaint)

            fillPaint.color = when {
                isBoostZone -> Color.parseColor("#FFD54F")
                thresholdReached -> Color.parseColor("#00FFFF")
                else -> Color.parseColor("#2ECC71")
            }

            val fillWidth = (w * progressFraction).coerceIn(0f, w)
            if (fillWidth > 0f) {
                rect.set(0f, 0f, fillWidth, h)
                canvas.drawRoundRect(rect, radius, radius, fillPaint)
            }

            val midX = w * 0.5f
            canvas.drawLine(midX, 0f, midX, h, tickPaint)
        }

        override fun setAlpha(alpha: Int) {
            trackPaint.alpha = alpha
            fillPaint.alpha = alpha
            tickPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            fillPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    class GestureHudOverlayView(context: Context) : LinearLayout(context) {
        private val titleTv: TextView
        private val subTv: TextView
        private val barDrawable = HudProgressBarDrawable()
        private val cardBg: GradientDrawable

        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val padH = dp(context, 20)
            val padV = dp(context, 14)
            setPadding(padH, padV, padH, padV)

            cardBg = GradientDrawable().apply {
                setColor(Color.parseColor("#D90A0A10"))
                cornerRadius = dp(context, 18).toFloat()
                setStroke(dp(context, 1), Color.parseColor("#2ECC71"))
            }
            background = cardBg

            titleTv = TextView(context).apply {
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            addView(titleTv)

            subTv = TextView(context).apply {
                setTextColor(Color.parseColor("#9AA5B1"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, dp(context, 4), 0, dp(context, 10))
            }
            addView(subTv)

            val barView = View(context).apply {
                background = barDrawable
            }
            addView(barView, LayoutParams(dp(context, 200), dp(context, 8)))
            visibility = View.GONE
        }

        fun renderState(state: GestureHudState?) {
            if (state == null || state.mode == LockedGestureMode.NONE) {
                visibility = View.GONE
                return
            }
            visibility = View.VISIBLE
            titleTv.text = state.titleText
            subTv.text = state.subText
            subTv.setTextColor(
                if (state.isBoostZone) Color.parseColor("#FFD54F") else Color.parseColor("#B0BAC5")
            )
            val strokeColor = when {
                state.isBoostZone -> Color.parseColor("#FFD54F")
                state.thresholdReached -> Color.parseColor("#00FFFF")
                else -> Color.parseColor("#2ECC71")
            }
            cardBg.setStroke(dp(context, if (state.thresholdReached) 2 else 1), strokeColor)

            barDrawable.progressFraction = state.progressFraction
            barDrawable.isBoostZone = state.isBoostZone
            barDrawable.thresholdReached = state.thresholdReached
        }
    }

    private fun dp(context: Context, value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
