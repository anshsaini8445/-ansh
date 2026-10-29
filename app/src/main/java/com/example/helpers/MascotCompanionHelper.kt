package com.example.helpers

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Studio Chibi Companion Breeds featuring the iconic Muso-Style DJ Headphone Puppy.
 */
enum class CompanionBreed(
    val displayName: String,
    val primaryFur: Color,
    val highlightFur: Color,
    val shadowFur: Color,
    val earColor: Color,
    val muzzlePatch: Color,
    val headphoneAccent: Color,
    val wearsHeadphonesAlways: Boolean = true
) {
    MUSO_HEADPHONE_PUPPY(
        displayName = "Muso Headphone Puppy (DJ)",
        primaryFur = Color(0xFFF4A259),
        highlightFur = Color(0xFFFFE0B2),
        shadowFur = Color(0xFFC8762E),
        earColor = Color(0xFF8D4E1F),
        muzzlePatch = Color(0xFFFFF8E7),
        headphoneAccent = Color(0xFF00E676),
        wearsHeadphonesAlways = true
    ),
    GOLDEN_RETRIEVER(
        displayName = "Golden Retriever Puppy",
        primaryFur = Color(0xFFEFA84A),
        highlightFur = Color(0xFFFFDF9E),
        shadowFur = Color(0xFFB56E1D),
        earColor = Color(0xFF9C5814),
        muzzlePatch = Color(0xFFFFF9EC),
        headphoneAccent = Color(0xFF00E5FF),
        wearsHeadphonesAlways = true
    ),
    SIBERIAN_HUSKY(
        displayName = "Siberian Husky",
        primaryFur = Color(0xFF78909C),
        highlightFur = Color(0xFFF5F7FA),
        shadowFur = Color(0xFF455A64),
        earColor = Color(0xFF37474F),
        muzzlePatch = Color(0xFFFFFFFF),
        headphoneAccent = Color(0xFF00FFFF),
        wearsHeadphonesAlways = true
    ),
    POMERANIAN_TEDDY(
        displayName = "Pomeranian Teddy",
        primaryFur = Color(0xFFE58A3B),
        highlightFur = Color(0xFFFFD59E),
        shadowFur = Color(0xFFA65413),
        earColor = Color(0xFF8E430B),
        muzzlePatch = Color(0xFFFFF3E0),
        headphoneAccent = Color(0xFFFF4081),
        wearsHeadphonesAlways = true
    ),
    PLUSH_BEAR(
        displayName = "Plush Bear",
        primaryFur = Color(0xFF9C7259),
        highlightFur = Color(0xFFD7CCC8),
        shadowFur = Color(0xFF5D4037),
        earColor = Color(0xFF4E342E),
        muzzlePatch = Color(0xFFEFEBE9),
        headphoneAccent = Color(0xFFFFD740),
        wearsHeadphonesAlways = true
    ),
    POWER_BULL(
        displayName = "Power Bull",
        primaryFur = Color(0xFF8D6E63),
        highlightFur = Color(0xFFBCAAA4),
        shadowFur = Color(0xFF4E342E),
        earColor = Color(0xFF3E2723),
        muzzlePatch = Color(0xFFF5F5F5),
        headphoneAccent = Color(0xFF2ECC71),
        wearsHeadphonesAlways = true
    ),
    PERSIAN_KITTEN(
        displayName = "Persian Kitten",
        primaryFur = Color(0xFFF5F5F7),
        highlightFur = Color(0xFFFFFFFF),
        shadowFur = Color(0xFFD0D4DC),
        earColor = Color(0xFFFFB2C9),
        muzzlePatch = Color(0xFFFFFFFF),
        headphoneAccent = Color(0xFFFF80AB),
        wearsHeadphonesAlways = true
    ),
    PANDA(
        displayName = "Panda",
        primaryFur = Color(0xFFFAFAFA),
        highlightFur = Color(0xFFFFFFFF),
        shadowFur = Color(0xFFE0E0E0),
        earColor = Color(0xFF212121),
        muzzlePatch = Color(0xFFFFFFFF),
        headphoneAccent = Color(0xFF00E676),
        wearsHeadphonesAlways = true
    ),
    CYBER_PUPPY(
        displayName = "Cyber Puppy",
        primaryFur = Color(0xFF4DD0E1),
        highlightFur = Color(0xFFE0F7FA),
        shadowFur = Color(0xFF00838F),
        earColor = Color(0xFF006064),
        muzzlePatch = Color(0xFFF0FFFF),
        headphoneAccent = Color(0xFF00E676),
        wearsHeadphonesAlways = true
    )
}

/**
 * Adorable Muso-Player-style Chibi 3D Puppy Companion with Studio DJ Headphones on its ears,
 * expressive anime eyes that blink & smile, rosy blush cheeks, rhythmic beat dancing,
 * floating neon music notes, floor walking, and gravity fling physics.
 */
@Composable
fun RealisticCompanionOverlay(
    enabled: Boolean,
    breedName: String,
    isMusicPlaying: Boolean,
    onQuickPlayTrigger: () -> Unit,
    onHideForSession: () -> Unit
) {
    if (!enabled) return

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val mascotSizePx = with(density) { 102.dp.toPx() }
    val floorY = (screenHeightPx - mascotSizePx - with(density) { 102.dp.toPx() }).coerceAtLeast(100f)

    val breed = remember(breedName) {
        CompanionBreed.entries.find { it.displayName == breedName } ?: CompanionBreed.MUSO_HEADPHONE_PUPPY
    }

    var posX by remember { mutableStateOf(screenWidthPx * 0.68f) }
    var posY by remember { mutableStateOf(floorY) }
    var velX by remember { mutableStateOf(1.8f) }
    var velY by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var walkDirection by remember { mutableStateOf(1f) }
    var animTick by remember { mutableStateOf(0f) }
    var manualDanceMode by remember { mutableStateOf(true) }
    var happyJumpTicks by remember { mutableStateOf(0) }

    val isGrooving = isMusicPlaying || manualDanceMode

    // 60FPS Chibi Animation, Dance Choreography & Physics Loop
    LaunchedEffect(isDragging, isGrooving, floorY, screenWidthPx) {
        while (true) {
            delay(16L)
            animTick += if (isGrooving) 0.19f else 0.09f
            if (happyJumpTicks > 0) happyJumpTicks--

            if (!isDragging) {
                if (posY < floorY - 2f || abs(velY) > 1.5f) {
                    // In-air Gravity & Wall Bounce Physics
                    velY += 1.35f
                    posX += velX
                    posY += velY

                    if (posX <= 8f) {
                        posX = 8f
                        velX = abs(velX) * 0.78f
                    } else if (posX >= screenWidthPx - mascotSizePx - 8f) {
                        posX = screenWidthPx - mascotSizePx - 8f
                        velX = -abs(velX) * 0.78f
                    }

                    if (posY >= floorY) {
                        posY = floorY
                        if (abs(velY) > 5.5f) {
                            velY = -velY * 0.45f
                            velX *= 0.8f
                        } else {
                            velY = 0f
                            velX = 0f
                        }
                    }
                } else {
                    posY = floorY
                    if (!isMusicPlaying && !manualDanceMode) {
                        posX += walkDirection * 1.15f
                        if (posX >= screenWidthPx - mascotSizePx - 16f) {
                            walkDirection = -1f
                        } else if (posX <= 16f) {
                            walkDirection = 1f
                        }
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .offset { IntOffset(posX.roundToInt(), posY.roundToInt()) }
                .size(102.dp)
                .pointerInput(isMusicPlaying) {
                    detectTapGestures(
                        onTap = {
                            velY = -16f
                            happyJumpTicks = 55
                            manualDanceMode = !manualDanceMode
                            val status = if (manualDanceMode) "Dancing with DJ Headphones!" else "Walking Mode"
                            Toast.makeText(
                                context,
                                "🐶 ${breed.displayName}: $status (Double-tap to Play Music)",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onDoubleTap = {
                            velY = -20f
                            happyJumpTicks = 80
                            manualDanceMode = true
                            onQuickPlayTrigger()
                            Toast.makeText(
                                context,
                                "🎧 ${breed.displayName} DJ Beat Drop!",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onLongPress = {
                            onHideForSession()
                            Toast.makeText(
                                context,
                                "Puppy hidden for this session (Re-enable in Me -> 3D Companion)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            velX = 0f
                            velY = 0f
                            happyJumpTicks = 40
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            posX = (posX + dragAmount.x).coerceIn(0f, screenWidthPx - mascotSizePx)
                            posY = (posY + dragAmount.y).coerceIn(40f, floorY)
                            velX = (dragAmount.x * 0.65f).coerceIn(-26f, 26f)
                            velY = (dragAmount.y * 0.65f).coerceIn(-26f, 26f)
                        }
                    )
                }
        ) {
            val w = size.width
            val h = size.height
            val cx = w / 2f

            // Muso Chibi Dance Kinematics
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

            // Blink every ~3.5 seconds, or show happy ^ ^ eyes when tapped/jumping
            val isBlinking = ((animTick * 10).toInt() % 110) in 0..5
            val showHappyArcEyes = happyJumpTicks > 0

            // 1. Soft Floor Shadow (scales with bounce)
            val shadowScale = (1f - (bodySquishY / (h * 0.18f))).coerceIn(0.65f, 1.05f)
            drawOval(
                color = Color(0x55000000),
                topLeft = Offset(cx - (w * 0.28f * shadowScale), h * 0.89f),
                size = Size(w * 0.56f * shadowScale, h * 0.075f)
            )

            // 2. Fluffy Wagging Puppy Tail (Curved Chibi Plume)
            rotate(degrees = tailWag, pivot = Offset(cx + w * 0.16f + hipSwayX, h * 0.72f - bodySquishY)) {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(breed.highlightFur, breed.primaryFur, breed.earColor)
                    ),
                    topLeft = Offset(cx + w * 0.15f + hipSwayX, h * 0.57f - bodySquishY),
                    size = Size(w * 0.13f, h * 0.19f),
                    cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
                )
                // White fluffy tail tip
                drawCircle(
                    color = breed.muzzlePatch,
                    radius = w * 0.052f,
                    center = Offset(cx + w * 0.22f + hipSwayX, h * 0.58f - bodySquishY)
                )
            }

            // 3. Cute Chubby Chibi Torso & Cream Belly Patch
            val bodyLeft = cx - w * 0.21f + hipSwayX
            val bodyTop = h * 0.54f - bodySquishY
            val bodyW = w * 0.42f
            val bodyH = h * 0.30f

            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(breed.highlightFur, breed.primaryFur, breed.shadowFur),
                    center = Offset(cx - w * 0.04f + hipSwayX, bodyTop + bodyH * 0.35f),
                    radius = w * 0.28f
                ),
                topLeft = Offset(bodyLeft, bodyTop),
                size = Size(bodyW, bodyH),
                cornerRadius = CornerRadius(w * 0.18f, w * 0.18f)
            )

            // Soft Cream Tummy Oval
            drawOval(
                color = breed.muzzlePatch,
                topLeft = Offset(cx - w * 0.13f + hipSwayX, bodyTop + bodyH * 0.18f),
                size = Size(w * 0.26f, bodyH * 0.68f)
            )

            // 4. Dancing Chibi Feet with Pink Toe Beans
            // Left Foot
            rotate(degrees = footTap, pivot = Offset(cx - w * 0.12f + hipSwayX, h * 0.80f - bodySquishY)) {
                drawOval(
                    color = breed.primaryFur,
                    topLeft = Offset(cx - w * 0.22f + hipSwayX, h * 0.79f - bodySquishY),
                    size = Size(w * 0.17f, h * 0.11f)
                )
                drawOval(
                    color = breed.muzzlePatch,
                    topLeft = Offset(cx - w * 0.20f + hipSwayX, h * 0.81f - bodySquishY),
                    size = Size(w * 0.13f, h * 0.08f)
                )
                drawCircle(
                    color = Color(0xFFFF80AB),
                    radius = w * 0.024f,
                    center = Offset(cx - w * 0.135f + hipSwayX, h * 0.855f - bodySquishY)
                )
            }

            // Right Foot
            rotate(degrees = -footTap, pivot = Offset(cx + w * 0.12f + hipSwayX, h * 0.80f - bodySquishY)) {
                drawOval(
                    color = breed.primaryFur,
                    topLeft = Offset(cx + w * 0.05f + hipSwayX, h * 0.79f - bodySquishY),
                    size = Size(w * 0.17f, h * 0.11f)
                )
                drawOval(
                    color = breed.muzzlePatch,
                    topLeft = Offset(cx + w * 0.07f + hipSwayX, h * 0.81f - bodySquishY),
                    size = Size(w * 0.13f, h * 0.08f)
                )
                drawCircle(
                    color = Color(0xFFFF80AB),
                    radius = w * 0.024f,
                    center = Offset(cx + w * 0.135f + hipSwayX, h * 0.855f - bodySquishY)
                )
            }

            // 5. Cute Collar & Golden Music Tag
            drawRoundRect(
                color = Color(0xFFFF3366),
                topLeft = Offset(cx - w * 0.16f + hipSwayX * 0.5f, h * 0.54f - bodySquishY),
                size = Size(w * 0.32f, h * 0.048f),
                cornerRadius = CornerRadius(10f, 10f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFF59D), Color(0xFFFFB300)),
                    center = Offset(cx - w * 0.01f, h * 0.59f - bodySquishY),
                    radius = w * 0.05f
                ),
                radius = w * 0.042f,
                center = Offset(cx + hipSwayX * 0.5f, h * 0.595f - bodySquishY)
            )

            // 6. Dancing Front Paws (Grooving Up & Down to the Beat!)
            // Left Paw
            rotate(degrees = leftPawRaise, pivot = Offset(cx - w * 0.18f + hipSwayX, h * 0.58f - bodySquishY)) {
                drawRoundRect(
                    color = breed.primaryFur,
                    topLeft = Offset(cx - w * 0.32f + hipSwayX, h * 0.55f - bodySquishY),
                    size = Size(w * 0.16f, h * 0.11f),
                    cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
                )
                // Cream paw tip + pink pad
                drawCircle(
                    color = breed.muzzlePatch,
                    radius = w * 0.048f,
                    center = Offset(cx - w * 0.29f + hipSwayX, h * 0.605f - bodySquishY)
                )
                drawCircle(
                    color = Color(0xFFFF80AB),
                    radius = w * 0.022f,
                    center = Offset(cx - w * 0.29f + hipSwayX, h * 0.605f - bodySquishY)
                )
            }

            // Right Paw
            rotate(degrees = rightPawRaise, pivot = Offset(cx + w * 0.18f + hipSwayX, h * 0.58f - bodySquishY)) {
                drawRoundRect(
                    color = breed.primaryFur,
                    topLeft = Offset(cx + w * 0.16f + hipSwayX, h * 0.55f - bodySquishY),
                    size = Size(w * 0.16f, h * 0.11f),
                    cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
                )
                drawCircle(
                    color = breed.muzzlePatch,
                    radius = w * 0.048f,
                    center = Offset(cx + w * 0.29f + hipSwayX, h * 0.605f - bodySquishY)
                )
                drawCircle(
                    color = Color(0xFFFF80AB),
                    radius = w * 0.022f,
                    center = Offset(cx + w * 0.29f + hipSwayX, h * 0.605f - bodySquishY)
                )
            }

            // =========================================================================
            // 7. BIG ADORABLE MUSO CHIBI HEAD + FLOPPY EARS + STUDIO DJ HEADPHONES
            // =========================================================================
            val headPivot = Offset(cx, h * 0.34f - bodySquishY)
            rotate(degrees = headTiltDeg, pivot = headPivot) {

                // 7A. Cute Floppy Puppy Ears (Soft Drooping Teardrop Ovals)
                rotate(degrees = 18f + earFlop, pivot = Offset(cx - w * 0.24f, h * 0.23f - bodySquishY)) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(breed.earColor, breed.shadowFur)
                        ),
                        topLeft = Offset(cx - w * 0.38f, h * 0.20f - bodySquishY),
                        size = Size(w * 0.17f, h * 0.27f),
                        cornerRadius = CornerRadius(w * 0.09f, w * 0.09f)
                    )
                }
                rotate(degrees = -18f - earFlop, pivot = Offset(cx + w * 0.24f, h * 0.23f - bodySquishY)) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(breed.earColor, breed.shadowFur)
                        ),
                        topLeft = Offset(cx + w * 0.21f, h * 0.20f - bodySquishY),
                        size = Size(w * 0.17f, h * 0.27f),
                        cornerRadius = CornerRadius(w * 0.09f, w * 0.09f)
                    )
                }

                // 7B. Studio Headphone Band (Arched Over the Top of the Puppy's Head)
                val showHeadphones = breed.wearsHeadphonesAlways || isGrooving
                if (showHeadphones) {
                    // Outer dark metallic studio headband arc
                    drawArc(
                        color = Color(0xFF1A222D),
                        startAngle = 188f,
                        sweepAngle = 164f,
                        useCenter = false,
                        topLeft = Offset(cx - w * 0.33f, h * 0.06f - bodySquishY),
                        size = Size(w * 0.66f, h * 0.50f),
                        style = Stroke(width = w * 0.058f, cap = StrokeCap.Round)
                    )
                    // Inner glowing neon accent strip on headband
                    drawArc(
                        color = breed.headphoneAccent,
                        startAngle = 202f,
                        sweepAngle = 136f,
                        useCenter = false,
                        topLeft = Offset(cx - w * 0.33f, h * 0.06f - bodySquishY),
                        size = Size(w * 0.66f, h * 0.50f),
                        style = Stroke(width = w * 0.022f, cap = StrokeCap.Round)
                    )
                }

                // 7C. Chibi Plush Head (Wide Cheeked Squircle for Maximum Cuteness!)
                val headW = w * 0.58f
                val headH = h * 0.42f
                val headLeft = cx - headW / 2f
                val headTop = h * 0.14f - bodySquishY

                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(breed.highlightFur, breed.primaryFur, breed.shadowFur),
                        center = Offset(cx - w * 0.06f, headTop + headH * 0.35f),
                        radius = w * 0.36f
                    ),
                    topLeft = Offset(headLeft, headTop),
                    size = Size(headW, headH),
                    cornerRadius = CornerRadius(w * 0.24f, w * 0.24f)
                )

                // Extra Chubby Lower Cheeks
                drawOval(
                    color = breed.primaryFur,
                    topLeft = Offset(cx - w * 0.31f, headTop + headH * 0.36f),
                    size = Size(w * 0.62f, headH * 0.62f)
                )

                // Cute Cream Forehead & Muzzle Mask Patch (Shiba / Golden / Husky markings)
                drawOval(
                    color = breed.muzzlePatch,
                    topLeft = Offset(cx - w * 0.22f, headTop + headH * 0.38f),
                    size = Size(w * 0.44f, headH * 0.58f)
                )
                // Cute Cream Eyebrow Dots (Classic Puppy Look!)
                drawOval(
                    color = breed.muzzlePatch,
                    topLeft = Offset(cx - w * 0.16f, headTop + headH * 0.20f),
                    size = Size(w * 0.085f, h * 0.038f)
                )
                drawOval(
                    color = breed.muzzlePatch,
                    topLeft = Offset(cx + w * 0.075f, headTop + headH * 0.20f),
                    size = Size(w * 0.085f, h * 0.038f)
                )

                // Fluffy Top Fur Tuft on Forehead
                val tuftPath = Path().apply {
                    moveTo(cx - w * 0.05f, headTop + h * 0.02f)
                    quadraticBezierTo(cx, headTop - h * 0.045f, cx + w * 0.04f, headTop + h * 0.015f)
                    quadraticBezierTo(cx + w * 0.08f, headTop - h * 0.025f, cx + w * 0.06f, headTop + h * 0.035f)
                    close()
                }
                drawPath(tuftPath, color = breed.highlightFur)

                // 7D. STUDIO DJ HEADPHONE EAR-CUPS ON BOTH EARS (With Pulsing Neon Rings!)
                if (showHeadphones) {
                    val cupPulse = if (isGrooving) (1f + abs(fastWave) * 0.12f) else 1f
                    val cupY = headTop + headH * 0.40f
                    val leftCupCenter = Offset(cx - w * 0.31f, cupY)
                    val rightCupCenter = Offset(cx + w * 0.31f, cupY)

                    drawStudioHeadphoneCup(leftCupCenter, w, h, breed.headphoneAccent, cupPulse, isLeft = true)
                    drawStudioHeadphoneCup(rightCupCenter, w, h, breed.headphoneAccent, cupPulse, isLeft = false)
                }

                // 7E. Rosy Pink Kawaii Blush Cheeks
                val blushAlpha = if (isGrooving) 0.72f else 0.52f
                drawOval(
                    color = Color(0xFFFF6E9C).copy(alpha = blushAlpha),
                    topLeft = Offset(cx - w * 0.24f, headTop + headH * 0.55f),
                    size = Size(w * 0.115f, h * 0.06f)
                )
                drawOval(
                    color = Color(0xFFFF6E9C).copy(alpha = blushAlpha),
                    topLeft = Offset(cx + w * 0.125f, headTop + headH * 0.55f),
                    size = Size(w * 0.115f, h * 0.06f)
                )

                // 7F. Big Expressive Anime/Pixar Puppy Eyes
                val eyeY = headTop + headH * 0.42f
                val leftEyeX = cx - w * 0.125f
                val rightEyeX = cx + w * 0.125f
                val eyeRadius = w * 0.068f

                if (breed == CompanionBreed.PANDA) {
                    // Panda dark eye patches
                    drawOval(
                        color = Color(0xFF212121),
                        topLeft = Offset(leftEyeX - eyeRadius * 1.3f, eyeY - eyeRadius * 1.2f),
                        size = Size(eyeRadius * 2.6f, eyeRadius * 2.5f)
                    )
                    drawOval(
                        color = Color(0xFF212121),
                        topLeft = Offset(rightEyeX - eyeRadius * 1.3f, eyeY - eyeRadius * 1.2f),
                        size = Size(eyeRadius * 2.6f, eyeRadius * 2.5f)
                    )
                }

                if (isBlinking || showHappyArcEyes) {
                    // Cute Happy Smiling Arc Eyes (^ ^)
                    drawArc(
                        color = Color(0xFF231815),
                        startAngle = 200f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(leftEyeX - eyeRadius, eyeY - eyeRadius * 0.6f),
                        size = Size(eyeRadius * 2f, eyeRadius * 1.5f),
                        style = Stroke(width = w * 0.032f, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = Color(0xFF231815),
                        startAngle = 200f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(rightEyeX - eyeRadius, eyeY - eyeRadius * 0.6f),
                        size = Size(eyeRadius * 2f, eyeRadius * 1.5f),
                        style = Stroke(width = w * 0.032f, cap = StrokeCap.Round)
                    )
                } else {
                    // Big Glossy Kawaii Eyes with Iris Gradient & Triple Catchlights
                    drawKawaiiPuppyEye(Offset(leftEyeX, eyeY), eyeRadius)
                    drawKawaiiPuppyEye(Offset(rightEyeX, eyeY), eyeRadius)
                }

                // 7G. Cute 3D Button Nose & Happy Open Puppy Smile (:P)
                val noseY = headTop + headH * 0.56f
                // Cute dark chocolate button nose
                drawRoundRect(
                    color = Color(0xFF231815),
                    topLeft = Offset(cx - w * 0.048f, noseY),
                    size = Size(w * 0.096f, h * 0.055f),
                    cornerRadius = CornerRadius(w * 0.045f, w * 0.045f)
                )
                // Glossy white nose highlight
                drawOval(
                    color = Color.White.copy(alpha = 0.85f),
                    topLeft = Offset(cx - w * 0.028f, noseY + h * 0.008f),
                    size = Size(w * 0.038f, h * 0.018f)
                )

                // Happy Pink Tongue Peeking Out (Bounces with the beat!)
                val tongueLen = if (isGrooving) h * 0.075f + abs(fastWave) * (h * 0.018f) else h * 0.058f
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFFF5286), Color(0xFFFF80AB))
                    ),
                    topLeft = Offset(cx - w * 0.042f, noseY + h * 0.058f),
                    size = Size(w * 0.084f, tongueLen),
                    cornerRadius = CornerRadius(w * 0.045f, w * 0.045f)
                )

                // Cute 'w' Puppy Mouth Cheeks (drawn right above the tongue top)
                val mouthY = noseY + h * 0.042f
                drawArc(
                    color = Color(0xFF3E2723),
                    startAngle = 15f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(cx - w * 0.085f, mouthY),
                    size = Size(w * 0.085f, h * 0.048f),
                    style = Stroke(width = w * 0.022f, cap = StrokeCap.Round)
                )
                drawArc(
                    color = Color(0xFF3E2723),
                    startAngle = 15f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(cx, mouthY),
                    size = Size(w * 0.085f, h * 0.048f),
                    style = Stroke(width = w * 0.022f, cap = StrokeCap.Round)
                )
            }

            // 8. Floating Neon Musical Notes & Sparkles When Dancing!
            if (isGrooving) {
                val noteProgress1 = (animTick * 0.35f) % 1f
                val noteProgress2 = ((animTick * 0.35f) + 0.5f) % 1f

                drawFloatingMusicNote(
                    center = Offset(
                        x = cx - w * 0.36f - sin(noteProgress1 * 6f) * (w * 0.05f),
                        y = h * 0.26f - noteProgress1 * (h * 0.22f)
                    ),
                    scale = w * 0.045f,
                    color = breed.headphoneAccent.copy(alpha = (1f - noteProgress1).coerceIn(0f, 1f))
                )
                drawFloatingMusicNote(
                    center = Offset(
                        x = cx + w * 0.36f + cos(noteProgress2 * 6f) * (w * 0.05f),
                        y = h * 0.24f - noteProgress2 * (h * 0.22f)
                    ),
                    scale = w * 0.045f,
                    color = Color(0xFF00FFFF).copy(alpha = (1f - noteProgress2).coerceIn(0f, 1f))
                )
            }
        }
    }
}

private fun DrawScope.drawKawaiiPuppyEye(center: Offset, radius: Float) {
    // Deep espresso outer eye
    drawCircle(
        color = Color(0xFF1A110E),
        radius = radius,
        center = center
    )
    // Warm amber lower iris glow
    drawOval(
        color = Color(0xFF6D4C41),
        topLeft = Offset(center.x - radius * 0.65f, center.y),
        size = Size(radius * 1.3f, radius * 0.78f)
    )
    // Main big top-left starlight catchlight
    drawCircle(
        color = Color.White,
        radius = radius * 0.42f,
        center = Offset(center.x - radius * 0.28f, center.y - radius * 0.28f)
    )
    // Secondary bottom-right sparkle catchlight
    drawCircle(
        color = Color.White.copy(alpha = 0.88f),
        radius = radius * 0.20f,
        center = Offset(center.x + radius * 0.32f, center.y + radius * 0.28f)
    )
}

private fun DrawScope.drawStudioHeadphoneCup(
    center: Offset,
    w: Float,
    h: Float,
    accentColor: Color,
    pulseScale: Float,
    isLeft: Boolean
) {
    val cupW = w * 0.13f * pulseScale
    val cupH = h * 0.20f * pulseScale

    // Soft inner cushion touching the ear
    val cushionOffsetX = if (isLeft) cupW * 0.28f else -cupW * 0.28f
    drawRoundRect(
        color = Color(0xFF263238),
        topLeft = Offset(center.x + cushionOffsetX - cupW / 2f, center.y - cupH * 0.46f),
        size = Size(cupW * 0.85f, cupH * 0.92f),
        cornerRadius = CornerRadius(cupW * 0.45f, cupW * 0.45f)
    )

    // Outer Studio Shell
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFF2C3E50), Color(0xFF141E30))
        ),
        topLeft = Offset(center.x - cupW / 2f, center.y - cupH / 2f),
        size = Size(cupW, cupH),
        cornerRadius = CornerRadius(cupW * 0.5f, cupW * 0.5f)
    )

    // Glowing Neon LED Ring on the Ear-Cup
    drawRoundRect(
        color = accentColor,
        topLeft = Offset(center.x - cupW * 0.34f, center.y - cupH * 0.34f),
        size = Size(cupW * 0.68f, cupH * 0.68f),
        cornerRadius = CornerRadius(cupW * 0.4f, cupW * 0.4f),
        style = Stroke(width = w * 0.018f)
    )

    // Center glowing LED dot
    drawCircle(
        color = accentColor,
        radius = w * 0.018f * pulseScale,
        center = center
    )
}

private fun DrawScope.drawFloatingMusicNote(
    center: Offset,
    scale: Float,
    color: Color
) {
    // Note head
    drawOval(
        color = color,
        topLeft = Offset(center.x - scale, center.y),
        size = Size(scale * 1.4f, scale)
    )
    // Note stem
    drawLine(
        color = color,
        start = Offset(center.x + scale * 0.3f, center.y + scale * 0.4f),
        end = Offset(center.x + scale * 0.3f, center.y - scale * 1.6f),
        strokeWidth = scale * 0.35f,
        cap = StrokeCap.Round
    )
    // Note flag
    drawLine(
        color = color,
        start = Offset(center.x + scale * 0.3f, center.y - scale * 1.6f),
        end = Offset(center.x + scale * 1.2f, center.y - scale * 1.0f),
        strokeWidth = scale * 0.35f,
        cap = StrokeCap.Round
    )
}
