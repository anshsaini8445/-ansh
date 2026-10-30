package com.app.mediaplayer

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class CompanionBreed(
    val displayName: String,
    val primaryFur: Int,
    val highlightFur: Int,
    val shadowFur: Int,
    val earColor: Int,
    val muzzlePatch: Int,
    val headphoneAccent: Int
) {
    MUSO_HEADPHONE_PUPPY(
        displayName = "Muso Headphone Puppy (DJ)",
        primaryFur = Color.parseColor("#F4A259"),
        highlightFur = Color.parseColor("#FFE0B2"),
        shadowFur = Color.parseColor("#C8762E"),
        earColor = Color.parseColor("#8D4E1F"),
        muzzlePatch = Color.parseColor("#FFF8E7"),
        headphoneAccent = Color.parseColor("#00E676")
    ),
    GOLDEN_RETRIEVER(
        displayName = "Golden Retriever Puppy",
        primaryFur = Color.parseColor("#EFA84A"),
        highlightFur = Color.parseColor("#FFDF9E"),
        shadowFur = Color.parseColor("#B56E1D"),
        earColor = Color.parseColor("#9C5814"),
        muzzlePatch = Color.parseColor("#FFF9EC"),
        headphoneAccent = Color.parseColor("#00E5FF")
    ),
    SIBERIAN_HUSKY(
        displayName = "Siberian Husky",
        primaryFur = Color.parseColor("#78909C"),
        highlightFur = Color.parseColor("#F5F7FA"),
        shadowFur = Color.parseColor("#455A64"),
        earColor = Color.parseColor("#37474F"),
        muzzlePatch = Color.parseColor("#FFFFFF"),
        headphoneAccent = Color.parseColor("#00FFFF")
    ),
    POMERANIAN_TEDDY(
        displayName = "Pomeranian Teddy",
        primaryFur = Color.parseColor("#E58A3B"),
        highlightFur = Color.parseColor("#FFD59E"),
        shadowFur = Color.parseColor("#A65413"),
        earColor = Color.parseColor("#8E430B"),
        muzzlePatch = Color.parseColor("#FFF3E0"),
        headphoneAccent = Color.parseColor("#FF4081")
    ),
    PLUSH_BEAR(
        displayName = "Plush Bear",
        primaryFur = Color.parseColor("#9C7259"),
        highlightFur = Color.parseColor("#D7CCC8"),
        shadowFur = Color.parseColor("#5D4037"),
        earColor = Color.parseColor("#4E342E"),
        muzzlePatch = Color.parseColor("#EFEBE9"),
        headphoneAccent = Color.parseColor("#FFD740")
    ),
    PANDA(
        displayName = "Panda",
        primaryFur = Color.parseColor("#FAFAFA"),
        highlightFur = Color.parseColor("#FFFFFF"),
        shadowFur = Color.parseColor("#E0E0E0"),
        earColor = Color.parseColor("#212121"),
        muzzlePatch = Color.parseColor("#FFFFFF"),
        headphoneAccent = Color.parseColor("#00E676")
    ),
    CYBER_PUPPY(
        displayName = "Cyber Puppy",
        primaryFur = Color.parseColor("#4DD0E1"),
        highlightFur = Color.parseColor("#E0F7FA"),
        shadowFur = Color.parseColor("#00838F"),
        earColor = Color.parseColor("#006064"),
        muzzlePatch = Color.parseColor("#F0FFFF"),
        headphoneAccent = Color.parseColor("#00E676")
    )
}

object MascotCompanionHelper {

    private const val PREF_COMPANION = "HYBRID_COMPANION_PREFS"
    private const val KEY_ENABLED = "companion_enabled"
    private const val KEY_BREED = "companion_breed_name"

    fun isCompanionEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREF_COMPANION, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)
    }

    fun setCompanionEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_COMPANION, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun getSelectedBreed(context: Context): CompanionBreed {
        val savedName = context.getSharedPreferences(PREF_COMPANION, Context.MODE_PRIVATE)
            .getString(KEY_BREED, CompanionBreed.MUSO_HEADPHONE_PUPPY.displayName)
        return CompanionBreed.entries.find { it.displayName == savedName }
            ?: CompanionBreed.MUSO_HEADPHONE_PUPPY
    }

    fun setSelectedBreed(context: Context, breed: CompanionBreed) {
        context.getSharedPreferences(PREF_COMPANION, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BREED, breed.displayName)
            .apply()
    }

    fun showBreedSelectorDialog(
        context: Context,
        onChanged: (CompanionBreed, Boolean) -> Unit
    ) {
        val breeds = CompanionBreed.entries
        val labels = Array(breeds.size + 1) { idx ->
            if (idx < breeds.size) {
                "🎧 ${breeds[idx].displayName}"
            } else {
                "Hide 3D Puppy Companion"
            }
        }

        val currentBreed = getSelectedBreed(context)
        val currentlyEnabled = isCompanionEnabled(context)
        val checkedItem = if (!currentlyEnabled) {
            breeds.size
        } else {
            breeds.indexOf(currentBreed).coerceAtLeast(0)
        }

        AlertDialog.Builder(context)
            .setTitle("Muso 3D DJ Headphone Puppy")
            .setSingleChoiceItems(labels, checkedItem) { dialog, which ->
                if (which in breeds.indices) {
                    val chosen = breeds[which]
                    setCompanionEnabled(context, true)
                    setSelectedBreed(context, chosen)
                    onChanged(chosen, true)
                } else {
                    setCompanionEnabled(context, false)
                    onChanged(currentBreed, false)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    fun attachCompanionOverlay(
        context: Context,
        parentFrame: FrameLayout,
        isMusicPlayingProvider: () -> Boolean = { true },
        onDoubleTapQuickPlay: () -> Unit = {}
    ): MascotCompanionView {
        val existing = parentFrame.findViewWithTag<MascotCompanionView>("HYBRID_MASCOT_VIEW")
        if (existing != null) {
            existing.refreshFromPreferences()
            return existing
        }

        val mascotView = MascotCompanionView(
            context = context,
            isMusicPlayingProvider = isMusicPlayingProvider,
            onDoubleTapQuickPlay = onDoubleTapQuickPlay
        ).apply {
            tag = "HYBRID_MASCOT_VIEW"
        }

        parentFrame.addView(
            mascotView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        return mascotView
    }

    class MascotCompanionView(
        context: Context,
        private val isMusicPlayingProvider: () -> Boolean = { true },
        private val onDoubleTapQuickPlay: () -> Unit = {}
    ) : View(context) {

        private var breed: CompanionBreed = getSelectedBreed(context)
        private var companionVisible: Boolean = isCompanionEnabled(context)

        private val mascotSizePx: Float = dp(context, 102f)
        private var posX: Float = -1f
        private var posY: Float = -1f
        private var velX: Float = 1.6f
        private var velY: Float = 0f
        private var walkDir: Float = 1f
        private var isDragging: Boolean = false
        private var manualDanceMode: Boolean = true
        private var happyJumpTicks: Int = 0
        private var animTick: Float = 0f
        private var lastTouchX: Float = 0f
        private var lastTouchY: Float = 0f

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val ovalRect = RectF()
        private val tuftPath = Path()

        private val gestureDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    velY = -16f
                    happyJumpTicks = 55
                    manualDanceMode = !manualDanceMode
                    val modeText = if (manualDanceMode) "Dancing with DJ Headphones!" else "Walking Mode"
                    Toast.makeText(
                        context,
                        "${breed.displayName}: $modeText (Long-press to change breed)",
                        Toast.LENGTH_SHORT
                    ).show()
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    velY = -20f
                    happyJumpTicks = 80
                    manualDanceMode = true
                    onDoubleTapQuickPlay()
                    Toast.makeText(
                        context,
                        "${breed.displayName} DJ Beat Drop!",
                        Toast.LENGTH_SHORT
                    ).show()
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    showBreedSelectorDialog(context) { newBreed, enabled ->
                        breed = newBreed
                        companionVisible = enabled
                        visibility = if (enabled) VISIBLE else GONE
                        invalidate()
                    }
                }
            }
        )

        fun refreshFromPreferences() {
            breed = getSelectedBreed(context)
            companionVisible = isCompanionEnabled(context)
            visibility = if (companionVisible) VISIBLE else GONE
            invalidate()
        }

        fun setBreed(newBreed: CompanionBreed) {
            breed = newBreed
            invalidate()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!companionVisible) return false

            val hitLeft = posX - dp(context, 12f)
            val hitRight = posX + mascotSizePx + dp(context, 12f)
            val hitTop = posY - dp(context, 12f)
            val hitBottom = posY + mascotSizePx + dp(context, 12f)

            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                val insideMascot = event.x in hitLeft..hitRight && event.y in hitTop..hitBottom
                if (!insideMascot) {
                    return false
                }
                isDragging = true
                velX = 0f
                velY = 0f
                lastTouchX = event.x
                lastTouchY = event.y
                happyJumpTicks = 40
            }

            gestureDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY
                        lastTouchX = event.x
                        lastTouchY = event.y

                        val maxX = (width - mascotSizePx).coerceAtLeast(0f)
                        val maxY = getFloorY()
                        posX = (posX + dx).coerceIn(0f, maxX)
                        posY = (posY + dy).coerceIn(dp(context, 24f), maxY)
                        velX = (dx * 0.65f).coerceIn(-25f, 25f)
                        velY = (dy * 0.65f).coerceIn(-25f, 25f)
                        invalidate()
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        isDragging = false
                        invalidate()
                        return true
                    }
                }
            }
            return isDragging
        }

        private fun getFloorY(): Float {
            return (height - mascotSizePx - dp(context, 82f)).coerceAtLeast(dp(context, 60f))
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!companionVisible || width <= 0 || height <= 0) return

            val floorY = getFloorY()
            if (posX < 0f || posY < 0f) {
                posX = (width - mascotSizePx - dp(context, 18f)).coerceAtLeast(16f)
                posY = floorY
            }

            val isGrooving = isMusicPlayingProvider() || manualDanceMode
            animTick += if (isGrooving) 0.18f else 0.09f
            if (happyJumpTicks > 0) happyJumpTicks--

            if (!isDragging) {
                if (posY < floorY - 2f || abs(velY) > 1.5f) {
                    velY += 1.35f
                    posX += velX
                    posY += velY

                    val maxX = (width - mascotSizePx - 8f).coerceAtLeast(8f)
                    if (posX <= 8f) {
                        posX = 8f
                        velX = abs(velX) * 0.78f
                    } else if (posX >= maxX) {
                        posX = maxX
                        velX = -abs(velX) * 0.78f
                    }

                    if (posY >= floorY) {
                        posY = floorY
                        if (abs(velY) > 5.2f) {
                            velY = -velY * 0.45f
                            velX *= 0.8f
                        } else {
                            velY = 0f
                            velX = 0f
                        }
                    }
                } else {
                    posY = floorY
                    if (!isGrooving) {
                        posX += walkDir * 1.15f
                        val maxX = (width - mascotSizePx - 16f).coerceAtLeast(16f)
                        if (posX >= maxX) walkDir = -1f
                        if (posX <= 16f) walkDir = 1f
                    }
                }
            }

            canvas.save()
            canvas.translate(posX, posY)
            drawChibiHeadphonePuppy(canvas, mascotSizePx, mascotSizePx, isGrooving)
            canvas.restore()

            postInvalidateOnAnimation()
        }

        private fun drawChibiHeadphonePuppy(
            canvas: Canvas,
            w: Float,
            h: Float,
            isGrooving: Boolean
        ) {
            val cx = w / 2f
            val beatWave = sin(animTick)
            val fastWave = sin(animTick * 2f)
            val headTiltDeg = if (isGrooving) beatWave * 11f else beatWave * 3.5f
            val bodySquishY = if (isGrooving) abs(fastWave) * (h * 0.048f) else abs(beatWave) * (h * 0.015f)
            val hipSwayX = if (isGrooving) cos(animTick) * (w * 0.032f) else 0f
            val leftPawRaise = if (isGrooving) (sin(animTick * 1.5f) * 28f - 18f) else sin(animTick) * 10f
            val rightPawRaise = if (isGrooving) (-sin(animTick * 1.5f) * 28f + 18f) else -sin(animTick) * 10f
            val footTap = sin(animTick * 2f) * (if (isGrooving) 18f else 12f)
            val tailWag = sin(animTick * 2.6f) * (if (isGrooving) 34f else 16f)
            val earFlop = abs(fastWave) * (if (isGrooving) 14f else 5f)

            val isBlinking = ((animTick * 10).toInt() % 110) in 0..5
            val showHappyArcEyes = happyJumpTicks > 0

            // 1. Soft Floor Shadow
            val shadowScale = (1f - (bodySquishY / (h * 0.18f))).coerceIn(0.65f, 1.05f)
            fillPaint.shader = null
            fillPaint.color = Color.argb(85, 0, 0, 0)
            ovalRect.set(
                cx - w * 0.28f * shadowScale,
                h * 0.89f,
                cx + w * 0.28f * shadowScale,
                h * 0.965f
            )
            canvas.drawOval(ovalRect, fillPaint)

            // 2. Wagging Fluffy Tail
            canvas.save()
            canvas.rotate(tailWag, cx + w * 0.16f + hipSwayX, h * 0.72f - bodySquishY)
            fillPaint.shader = LinearGradient(
                cx + w * 0.15f + hipSwayX,
                h * 0.57f - bodySquishY,
                cx + w * 0.28f + hipSwayX,
                h * 0.76f - bodySquishY,
                intArrayOf(breed.highlightFur, breed.primaryFur, breed.earColor),
                null,
                Shader.TileMode.CLAMP
            )
            ovalRect.set(
                cx + w * 0.15f + hipSwayX,
                h * 0.57f - bodySquishY,
                cx + w * 0.28f + hipSwayX,
                h * 0.76f - bodySquishY
            )
            canvas.drawRoundRect(ovalRect, w * 0.08f, w * 0.08f, fillPaint)
            fillPaint.shader = null
            fillPaint.color = breed.muzzlePatch
            canvas.drawCircle(cx + w * 0.22f + hipSwayX, h * 0.58f - bodySquishY, w * 0.052f, fillPaint)
            canvas.restore()

            // 3. Chibi Torso & Cream Belly Patch
            val bodyLeft = cx - w * 0.21f + hipSwayX
            val bodyTop = h * 0.54f - bodySquishY
            val bodyW = w * 0.42f
            val bodyH = h * 0.30f

            fillPaint.shader = RadialGradient(
                cx - w * 0.04f + hipSwayX,
                bodyTop + bodyH * 0.35f,
                w * 0.28f,
                intArrayOf(breed.highlightFur, breed.primaryFur, breed.shadowFur),
                null,
                Shader.TileMode.CLAMP
            )
            ovalRect.set(bodyLeft, bodyTop, bodyLeft + bodyW, bodyTop + bodyH)
            canvas.drawRoundRect(ovalRect, w * 0.18f, w * 0.18f, fillPaint)

            fillPaint.shader = null
            fillPaint.color = breed.muzzlePatch
            ovalRect.set(
                cx - w * 0.13f + hipSwayX,
                bodyTop + bodyH * 0.18f,
                cx + w * 0.13f + hipSwayX,
                bodyTop + bodyH * 0.86f
            )
            canvas.drawOval(ovalRect, fillPaint)

            // 4. Dancing Feet with Pink Toe Beans
            drawPuppyFoot(canvas, cx - w * 0.135f + hipSwayX, h * 0.84f - bodySquishY, w, h, footTap)
            drawPuppyFoot(canvas, cx + w * 0.135f + hipSwayX, h * 0.84f - bodySquishY, w, h, -footTap)

            // 5. Collar & Golden Music Tag
            fillPaint.color = Color.parseColor("#FF3366")
            ovalRect.set(
                cx - w * 0.16f + hipSwayX * 0.5f,
                h * 0.54f - bodySquishY,
                cx + w * 0.16f + hipSwayX * 0.5f,
                h * 0.588f - bodySquishY
            )
            canvas.drawRoundRect(ovalRect, 10f, 10f, fillPaint)
            fillPaint.color = Color.parseColor("#FFD54F")
            canvas.drawCircle(cx + hipSwayX * 0.5f, h * 0.595f - bodySquishY, w * 0.04f, fillPaint)

            // 6. Dancing Front Paws
            drawFrontPaw(canvas, cx - w * 0.24f + hipSwayX, h * 0.60f - bodySquishY, w, h, leftPawRaise)
            drawFrontPaw(canvas, cx + w * 0.24f + hipSwayX, h * 0.60f - bodySquishY, w, h, rightPawRaise)

            // 7. Big Chibi Head + Floppy Ears + Studio DJ Headphones
            canvas.save()
            canvas.rotate(headTiltDeg, cx, h * 0.34f - bodySquishY)

            // 7A. Floppy Ears
            drawFloppyEar(canvas, cx - w * 0.29f, h * 0.33f - bodySquishY, w, h, 18f + earFlop)
            drawFloppyEar(canvas, cx + w * 0.29f, h * 0.33f - bodySquishY, w, h, -18f - earFlop)

            // 7B. Studio DJ Headband Arc
            strokePaint.color = Color.parseColor("#1A222D")
            strokePaint.strokeWidth = w * 0.058f
            ovalRect.set(cx - w * 0.33f, h * 0.06f - bodySquishY, cx + w * 0.33f, h * 0.56f - bodySquishY)
            canvas.drawArc(ovalRect, 188f, 164f, false, strokePaint)

            strokePaint.color = breed.headphoneAccent
            strokePaint.strokeWidth = w * 0.022f
            canvas.drawArc(ovalRect, 202f, 136f, false, strokePaint)

            // 7C. Plush Chibi Head Squircle
            val headW = w * 0.58f
            val headH = h * 0.42f
            val headLeft = cx - headW / 2f
            val headTop = h * 0.14f - bodySquishY

            fillPaint.shader = RadialGradient(
                cx - w * 0.06f,
                headTop + headH * 0.35f,
                w * 0.36f,
                intArrayOf(breed.highlightFur, breed.primaryFur, breed.shadowFur),
                null,
                Shader.TileMode.CLAMP
            )
            ovalRect.set(headLeft, headTop, headLeft + headW, headTop + headH)
            canvas.drawRoundRect(ovalRect, w * 0.24f, w * 0.24f, fillPaint)

            fillPaint.shader = null
            fillPaint.color = breed.primaryFur
            ovalRect.set(cx - w * 0.31f, headTop + headH * 0.36f, cx + w * 0.31f, headTop + headH * 0.98f)
            canvas.drawOval(ovalRect, fillPaint)

            // Cream Muzzle Patch & Eyebrow Dots
            fillPaint.color = breed.muzzlePatch
            ovalRect.set(cx - w * 0.22f, headTop + headH * 0.38f, cx + w * 0.22f, headTop + headH * 0.96f)
            canvas.drawOval(ovalRect, fillPaint)

            ovalRect.set(cx - w * 0.16f, headTop + headH * 0.20f, cx - w * 0.075f, headTop + headH * 0.20f + h * 0.038f)
            canvas.drawOval(ovalRect, fillPaint)
            ovalRect.set(cx + w * 0.075f, headTop + headH * 0.20f, cx + w * 0.16f, headTop + headH * 0.20f + h * 0.038f)
            canvas.drawOval(ovalRect, fillPaint)

            // Fluffy Forehead Tuft
            tuftPath.reset()
            tuftPath.moveTo(cx - w * 0.05f, headTop + h * 0.02f)
            tuftPath.quadTo(cx, headTop - h * 0.045f, cx + w * 0.04f, headTop + h * 0.015f)
            tuftPath.close()
            fillPaint.color = breed.highlightFur
            canvas.drawPath(tuftPath, fillPaint)

            // 7D. Studio DJ Headphone Ear-Cups on Both Ears
            val cupPulse = if (isGrooving) (1f + abs(fastWave) * 0.12f) else 1f
            val cupY = headTop + headH * 0.40f
            drawDjHeadphoneCup(canvas, cx - w * 0.31f, cupY, w, h, cupPulse)
            drawDjHeadphoneCup(canvas, cx + w * 0.31f, cupY, w, h, cupPulse)

            // 7E. Rosy Kawaii Blush Cheeks
            fillPaint.color = Color.argb(if (isGrooving) 185 else 135, 255, 110, 156)
            ovalRect.set(cx - w * 0.24f, headTop + headH * 0.55f, cx - w * 0.125f, headTop + headH * 0.55f + h * 0.06f)
            canvas.drawOval(ovalRect, fillPaint)
            ovalRect.set(cx + w * 0.125f, headTop + headH * 0.55f, cx + w * 0.24f, headTop + headH * 0.55f + h * 0.06f)
            canvas.drawOval(ovalRect, fillPaint)

            // 7F. Big Expressive Anime Eyes
            val eyeY = headTop + headH * 0.42f
            val leftEyeX = cx - w * 0.125f
            val rightEyeX = cx + w * 0.125f
            val eyeRadius = w * 0.068f

            if (breed == CompanionBreed.PANDA) {
                fillPaint.color = Color.parseColor("#212121")
                canvas.drawCircle(leftEyeX, eyeY, eyeRadius * 1.3f, fillPaint)
                canvas.drawCircle(rightEyeX, eyeY, eyeRadius * 1.3f, fillPaint)
            }

            if (isBlinking || showHappyArcEyes) {
                strokePaint.color = Color.parseColor("#231815")
                strokePaint.strokeWidth = w * 0.032f
                ovalRect.set(leftEyeX - eyeRadius, eyeY - eyeRadius * 0.6f, leftEyeX + eyeRadius, eyeY + eyeRadius * 0.9f)
                canvas.drawArc(ovalRect, 200f, 140f, false, strokePaint)
                ovalRect.set(rightEyeX - eyeRadius, eyeY - eyeRadius * 0.6f, rightEyeX + eyeRadius, eyeY + eyeRadius * 0.9f)
                canvas.drawArc(ovalRect, 200f, 140f, false, strokePaint)
            } else {
                drawKawaiiEye(canvas, leftEyeX, eyeY, eyeRadius)
                drawKawaiiEye(canvas, rightEyeX, eyeY, eyeRadius)
            }

            // 7G. Button Nose, 'w' Puppy Mouth & Bouncing Pink Tongue
            val noseY = headTop + headH * 0.56f
            fillPaint.color = Color.parseColor("#231815")
            ovalRect.set(cx - w * 0.048f, noseY, cx + w * 0.048f, noseY + h * 0.055f)
            canvas.drawRoundRect(ovalRect, w * 0.045f, w * 0.045f, fillPaint)

            fillPaint.color = Color.WHITE
            ovalRect.set(cx - w * 0.025f, noseY + h * 0.008f, cx + w * 0.01f, noseY + h * 0.024f)
            canvas.drawOval(ovalRect, fillPaint)

            val tongueLen = if (isGrooving) h * 0.072f + abs(fastWave) * (h * 0.018f) else h * 0.055f
            fillPaint.color = Color.parseColor("#FF6E9C")
            ovalRect.set(cx - w * 0.040f, noseY + h * 0.056f, cx + w * 0.040f, noseY + h * 0.056f + tongueLen)
            canvas.drawRoundRect(ovalRect, w * 0.04f, w * 0.04f, fillPaint)

            val mouthY = noseY + h * 0.042f
            strokePaint.color = Color.parseColor("#3E2723")
            strokePaint.strokeWidth = w * 0.022f
            ovalRect.set(cx - w * 0.085f, mouthY, cx, mouthY + h * 0.048f)
            canvas.drawArc(ovalRect, 15f, 150f, false, strokePaint)
            ovalRect.set(cx, mouthY, cx + w * 0.085f, mouthY + h * 0.048f)
            canvas.drawArc(ovalRect, 15f, 150f, false, strokePaint)

            canvas.restore()

            // 8. Floating Musical Notes
            if (isGrooving) {
                val p1 = (animTick * 0.35f) % 1f
                val p2 = ((animTick * 0.35f) + 0.5f) % 1f
                drawFloatingNote(
                    canvas = canvas,
                    x = cx - w * 0.36f - sin(p1 * 6f) * (w * 0.04f),
                    y = h * 0.26f - p1 * (h * 0.20f),
                    scale = w * 0.04f,
                    color = breed.headphoneAccent
                )
                drawFloatingNote(
                    canvas = canvas,
                    x = cx + w * 0.36f + cos(p2 * 6f) * (w * 0.04f),
                    y = h * 0.24f - p2 * (h * 0.20f),
                    scale = w * 0.04f,
                    color = Color.parseColor("#00FFFF")
                )
            }
        }

        private fun drawPuppyFoot(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            w: Float,
            h: Float,
            angle: Float
        ) {
            canvas.save()
            canvas.rotate(angle, cx, cy)
            fillPaint.shader = null
            fillPaint.color = breed.primaryFur
            ovalRect.set(cx - w * 0.085f, cy - h * 0.055f, cx + w * 0.085f, cy + h * 0.055f)
            canvas.drawOval(ovalRect, fillPaint)
            fillPaint.color = breed.muzzlePatch
            ovalRect.set(cx - w * 0.065f, cy - h * 0.035f, cx + w * 0.065f, cy + h * 0.045f)
            canvas.drawOval(ovalRect, fillPaint)
            fillPaint.color = Color.parseColor("#FF80AB")
            canvas.drawCircle(cx, cy + h * 0.01f, w * 0.022f, fillPaint)
            canvas.restore()
        }

        private fun drawFrontPaw(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            w: Float,
            h: Float,
            angle: Float
        ) {
            canvas.save()
            canvas.rotate(angle, cx, cy)
            fillPaint.shader = null
            fillPaint.color = breed.primaryFur
            ovalRect.set(cx - w * 0.08f, cy - h * 0.055f, cx + w * 0.08f, cy + h * 0.055f)
            canvas.drawRoundRect(ovalRect, w * 0.06f, w * 0.06f, fillPaint)
            fillPaint.color = breed.muzzlePatch
            canvas.drawCircle(cx, cy, w * 0.045f, fillPaint)
            fillPaint.color = Color.parseColor("#FF80AB")
            canvas.drawCircle(cx, cy, w * 0.021f, fillPaint)
            canvas.restore()
        }

        private fun drawFloppyEar(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            w: Float,
            h: Float,
            angle: Float
        ) {
            canvas.save()
            canvas.rotate(angle, cx, cy - h * 0.10f)
            fillPaint.shader = LinearGradient(
                cx,
                cy - h * 0.13f,
                cx,
                cy + h * 0.13f,
                breed.earColor,
                breed.shadowFur,
                Shader.TileMode.CLAMP
            )
            ovalRect.set(cx - w * 0.085f, cy - h * 0.13f, cx + w * 0.085f, cy + h * 0.14f)
            canvas.drawRoundRect(ovalRect, w * 0.09f, w * 0.09f, fillPaint)
            fillPaint.shader = null
            canvas.restore()
        }

        private fun drawDjHeadphoneCup(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            w: Float,
            h: Float,
            pulse: Float
        ) {
            val cupW = w * 0.13f * pulse
            val cupH = h * 0.20f * pulse
            fillPaint.shader = null
            fillPaint.color = Color.parseColor("#1E293B")
            ovalRect.set(cx - cupW / 2f, cy - cupH / 2f, cx + cupW / 2f, cy + cupH / 2f)
            canvas.drawRoundRect(ovalRect, cupW * 0.5f, cupW * 0.5f, fillPaint)

            strokePaint.color = breed.headphoneAccent
            strokePaint.strokeWidth = w * 0.018f
            ovalRect.set(cx - cupW * 0.34f, cy - cupH * 0.34f, cx + cupW * 0.34f, cy + cupH * 0.34f)
            canvas.drawRoundRect(ovalRect, cupW * 0.4f, cupW * 0.4f, strokePaint)

            fillPaint.color = breed.headphoneAccent
            canvas.drawCircle(cx, cy, w * 0.018f * pulse, fillPaint)
        }

        private fun drawKawaiiEye(canvas: Canvas, cx: Float, cy: Float, r: Float) {
            fillPaint.shader = null
            fillPaint.color = Color.parseColor("#1A110E")
            canvas.drawCircle(cx, cy, r, fillPaint)

            fillPaint.color = Color.parseColor("#6D4C41")
            ovalRect.set(cx - r * 0.65f, cy, cx + r * 0.65f, cy + r * 0.78f)
            canvas.drawOval(ovalRect, fillPaint)

            fillPaint.color = Color.WHITE
            canvas.drawCircle(cx - r * 0.28f, cy - r * 0.28f, r * 0.42f, fillPaint)
            canvas.drawCircle(cx + r * 0.32f, cy + r * 0.28f, r * 0.20f, fillPaint)
        }

        private fun drawFloatingNote(
            canvas: Canvas,
            x: Float,
            y: Float,
            scale: Float,
            color: Int
        ) {
            fillPaint.shader = null
            fillPaint.color = color
            ovalRect.set(x - scale, y, x + scale * 0.4f, y + scale)
            canvas.drawOval(ovalRect, fillPaint)

            strokePaint.color = color
            strokePaint.strokeWidth = scale * 0.35f
            canvas.drawLine(x + scale * 0.3f, y + scale * 0.4f, x + scale * 0.3f, y - scale * 1.6f, strokePaint)
            canvas.drawLine(x + scale * 0.3f, y - scale * 1.6f, x + scale * 1.2f, y - scale * 1.0f, strokePaint)
        }
    }

    private fun dp(context: Context, value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            context.resources.displayMetrics
        )
    }
}
