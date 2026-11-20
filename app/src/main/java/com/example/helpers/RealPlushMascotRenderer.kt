package com.example.helpers

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * High-Craft Pixar-Grade Real 3D Plush Photo Animation Engine:
 * - Renders the user's real studio 3D plush characters (all 12 high-resolution transparent cutouts)
 *   with ZERO scary vector outlines, ZERO boxes, and ZERO clutter under the feet or around the sides.
 * - When paused & upright: renders the crisp, seamless full studio 3D plush cutout (0% CPU).
 * - When playing music, dancing, walking, dragging, falling, or flipped upside-down:
 *   independently animates all 6 real plush anatomical photo layers (Left Leg, Right Leg, Torso,
 *   Left Arm, Right Arm, Head) with Pixar squash-and-stretch, rhythmic hop, natural soft fur-tinted
 *   eye blinks, joyful cheek glow, and a cute playful tongue peek!
 * - Zero recompositions per frame: reads MascotLiveMotionState strictly inside the Canvas draw phase.
 */
@Composable
internal fun RealPlush3DAnimatedMascot(
    breed: CompanionBreed,
    parts: ArticulatedMascotParts? = null,
    animTick: Float = 0f,
    walkStridePhase: Float = 0f,
    isMusicPlaying: Boolean = false,
    isActivelyWalking: Boolean = false,
    happyJumpTicks: Int = 0,
    landingSquashTicks: Int = 0,
    danceMode: MascotDanceMode = MascotDanceMode.AUTO_MIX,
    customFurColor: Color? = null,
    isLiftedInAir: Boolean = false,
    danceTransition: Float = if (isMusicPlaying || happyJumpTicks > 0) 1f else 0f,
    liveMotion: MascotLiveMotionState? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var loadedParts by remember(breed.drawableResId, parts) {
        mutableStateOf(parts ?: MascotTransparentBitmapCache.getCachedArticulatedMascot(breed))
    }
    LaunchedEffect(breed.drawableResId, parts) {
        if (parts != null) {
            loadedParts = parts
        } else if (loadedParts == null) {
            loadedParts = MascotTransparentBitmapCache.getOrLoadArticulatedMascot(context, breed)
        }
    }

    val faceRig = remember(breed.drawableResId) { getFaceRigForBreed(breed) }
    val tintFilter = remember(customFurColor) {
        customFurColor?.let { ColorFilter.tint(it, BlendMode.Modulate) }
    }
    val tonguePath = remember { Path() }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 4f || h <= 4f) return@Canvas
        val activeParts = loadedParts ?: return@Canvas

        // Read VSync motion state exclusively in Draw Phase -> 0 recompositions/sec!
        val t = liveMotion?.animTick ?: animTick
        val activeWalkStride = liveMotion?.walkStridePhase ?: walkStridePhase
        val activeWalking = liveMotion?.isActivelyWalking ?: isActivelyWalking
        val activeHappyJump = liveMotion?.happyJumpTicks ?: happyJumpTicks
        val activeLandingSquash = liveMotion?.landingSquashTicks ?: landingSquashTicks
        val activeLifted = liveMotion?.isLiftedInAir ?: isLiftedInAir
        val activeDanceTransition = liveMotion?.danceTransition ?: danceTransition
        val activeTonguePop = liveMotion?.tonguePopTicks ?: 0
        val activeUpsideDownOrFalling = liveMotion?.isUpsideDownOrFalling ?: false
        val activeVelX = liveMotion?.velX ?: 0f
        val activeVelY = liveMotion?.velY ?: 0f

        val activeStyle = if (danceMode == MascotDanceMode.AUTO_MIX) {
            when (((t / 6.5f).toInt()) % 6) {
                0 -> MascotDanceMode.TRAMPOLINE_ACROBAT
                1 -> MascotDanceMode.BHANGRA_GROOVE
                2 -> MascotDanceMode.DJ_BOUNCE
                3 -> MascotDanceMode.SPIN_3D
                4 -> MascotDanceMode.HEART_DISCO
                else -> MascotDanceMode.ZERO_G_FLOAT
            }
        } else {
            danceMode
        }

        // Smooth multi-step transition curve (0f = 100% Stationary Paused, 1f = Full Living 3D Plush Dance)
        val rawDanceWeight = if (activeHappyJump > 0) {
            max(activeDanceTransition, 0.88f).coerceIn(0f, 1f)
        } else {
            activeDanceTransition.coerceIn(0f, 1f)
        }
        val headGrooveW = (rawDanceWeight / 0.40f).coerceIn(0f, 1f)
        val armGrooveW = ((rawDanceWeight - 0.10f) / 0.50f).coerceIn(0f, 1f)
        val legDanceW = ((rawDanceWeight - 0.20f) / 0.70f).coerceIn(0f, 1f)
        val isDancingActive = rawDanceWeight > 0.005f

        val extraLandingSquash = if (activeLandingSquash > 0) sin(activeLandingSquash * 0.48f) * 0.16f else 0f
        val isPhysicsActive = activeLifted || activeUpsideDownOrFalling || abs(activeVelX) > 1.2f || abs(activeVelY) > 1.2f || activeTonguePop > 0
        val isStationaryPaused = !isDancingActive && !isPhysicsActive && extraLandingSquash <= 0.005f

        // Aspect-preserving destination bounds with generous breathing room so jumping/kicking limbs never clip!
        val bmpW = activeParts.fullCutoutBmp.width.coerceAtLeast(1).toFloat()
        val bmpH = activeParts.fullCutoutBmp.height.coerceAtLeast(1).toFloat()
        val scaleFit = min(w / bmpW, h / bmpH) * 0.90f
        val dstW = bmpW * scaleFit
        val dstH = bmpH * scaleFit
        val baseLeft = (w - dstW) * 0.5f
        val baseTop = (h - dstH) * 0.54f
        val dstSizeInt = IntSize(dstW.roundToInt().coerceAtLeast(1), dstH.roundToInt().coerceAtLeast(1))
        val dstOffsetInt = IntOffset(baseLeft.roundToInt(), baseTop.roundToInt())

        // 1. PAUSED & UPRIGHT: Crisp, seamless 100% original studio 3D plush photo (zero flicker, zero artifacts!)
        if (isStationaryPaused) {
            drawImage(
                image = activeParts.fullCutoutBmp,
                dstOffset = dstOffsetInt,
                dstSize = dstSizeInt,
                colorFilter = tintFilter,
                filterQuality = FilterQuality.Medium
            )
            return@Canvas
        }

        // 2. LIVE 3D ANIMATED PLUSH: Real Pixar squash-and-stretch, hop, groove & 6-part limb articulation!
        val jumpSpeed = when {
            !isDancingActive -> 2.0f
            activeStyle == MascotDanceMode.TRAMPOLINE_ACROBAT -> 4.2f
            activeStyle == MascotDanceMode.BHANGRA_GROOVE -> 3.9f
            else -> 3.6f
        }
        val jumpCycle = t * jumpSpeed
        val normPhase = ((jumpCycle / 6.2831855f) % 1f + 1f) % 1f

        val airborneFactor: Float
        val crouchFactor: Float
        if (activeLifted || activeUpsideDownOrFalling) {
            airborneFactor = 1.0f
            crouchFactor = 0f
        } else if (!isDancingActive) {
            airborneFactor = 0f
            crouchFactor = 0f
        } else if (normPhase in 0.24f..0.86f) {
            val airU = (normPhase - 0.24f) / 0.62f
            airborneFactor = (sin(airU * 3.1415927f) * legDanceW).coerceIn(0f, 1f)
            crouchFactor = 0f
        } else {
            airborneFactor = 0f
            val groundU = if (normPhase < 0.24f) (normPhase + 0.14f) / 0.38f else (normPhase - 0.86f) / 0.38f
            crouchFactor = (sin(groundU * 3.1415927f) * legDanceW).coerceIn(0f, 1f)
        }

        val totalCrouch = (crouchFactor + extraLandingSquash).coerceIn(0f, 1.0f)
        val stuntBoost = if (activeHappyJump > 0) 1.18f else 1.0f
        val hopY = when {
            activeLifted || activeUpsideDownOrFalling -> -dstH * 0.03f
            else -> (-airborneFactor * dstH * 0.082f * stuntBoost + totalCrouch * dstH * 0.022f) * legDanceW
        }
        val bodySwayX = if (activeLifted) 0f else sin(t * 2.9f) * dstW * 0.024f * legDanceW
        val bodyTiltDeg = when {
            activeUpsideDownOrFalling -> sin(t * 10f) * 8f
            activeStyle == MascotDanceMode.BHANGRA_GROOVE -> sin(t * 3.8f) * 7.5f * armGrooveW
            activeStyle == MascotDanceMode.ZERO_G_FLOAT -> sin(t * 2.4f) * 6.5f * headGrooveW
            else -> sin(t * 3.2f) * 5.0f * legDanceW
        }
        val squashX = 1.0f - airborneFactor * 0.045f + totalCrouch * 0.085f
        val stretchY = 1.0f + airborneFactor * 0.055f - totalCrouch * 0.075f
        val breathScale = 1.0f + sin(t * 4.4f) * 0.022f * headGrooveW

        // Independent limb kinematics for the 6 real plush photo layers
        val legWave = if (activeLifted || activeUpsideDownOrFalling) {
            sin(t * 13.5f)
        } else if (activeWalking) {
            sin(activeWalkStride * 1.6f)
        } else {
            sin(t * 5.6f)
        }
        val leftLegDeg = legWave * (if (activeLifted || activeUpsideDownOrFalling) 22f else 15f * legDanceW)
        val rightLegDeg = -legWave * (if (activeLifted || activeUpsideDownOrFalling) 22f else 15f * legDanceW)
        val leftLegLiftY = -max(0f, -legWave) * dstH * (if (activeLifted || activeUpsideDownOrFalling) 0.032f else 0.026f * legDanceW)
        val rightLegLiftY = -max(0f, legWave) * dstH * (if (activeLifted || activeUpsideDownOrFalling) 0.032f else 0.026f * legDanceW)

        val armWave = if (activeLifted || activeUpsideDownOrFalling) {
            sin(t * 14.5f)
        } else {
            sin(t * 4.8f)
        }
        val cheerLiftDeg = if (airborneFactor > 0.25f || activeHappyJump > 0) 14f * armGrooveW else 0f
        val leftArmDeg = (-armWave * (if (activeLifted || activeUpsideDownOrFalling) 32f else 18f * armGrooveW)) + cheerLiftDeg
        val rightArmDeg = (armWave * (if (activeLifted || activeUpsideDownOrFalling) 32f else 18f * armGrooveW)) - cheerLiftDeg

        val headBobDeg = when {
            activeUpsideDownOrFalling -> sin(t * 11f) * 10f
            activeStyle == MascotDanceMode.DJ_BOUNCE -> sin(t * 5.2f) * 9.5f * headGrooveW
            else -> sin(t * 3.9f) * 8.0f * headGrooveW
        }
        val headBounceY = sin(t * 5.2f) * dstH * 0.014f * headGrooveW

        val feetPivot = Offset(baseLeft + dstW * 0.5f, baseTop + dstH * 0.90f)
        val leftHipPivot = Offset(baseLeft + dstW * 0.37f, baseTop + dstH * 0.72f)
        val rightHipPivot = Offset(baseLeft + dstW * 0.63f, baseTop + dstH * 0.72f)
        val leftShoulderPivot = Offset(baseLeft + dstW * 0.31f, baseTop + dstH * 0.50f)
        val rightShoulderPivot = Offset(baseLeft + dstW * 0.69f, baseTop + dstH * 0.50f)
        val neckPivot = Offset(baseLeft + dstW * 0.50f, baseTop + dstH * 0.47f)

        withTransform({
            translate(left = bodySwayX, top = hopY)
            rotate(degrees = bodyTiltDeg, pivot = feetPivot)
            scale(scaleX = squashX, scaleY = stretchY, pivot = feetPivot)
        }) {
            // Layer 1: Left & Right Real Fluffy Plush Legs
            withTransform({
                translate(top = leftLegLiftY)
                rotate(degrees = leftLegDeg, pivot = leftHipPivot)
            }) {
                drawImage(
                    image = activeParts.leftFootLayerBmp,
                    dstOffset = dstOffsetInt,
                    dstSize = dstSizeInt,
                    colorFilter = tintFilter,
                    filterQuality = FilterQuality.Medium
                )
            }
            withTransform({
                translate(top = rightLegLiftY)
                rotate(degrees = rightLegDeg, pivot = rightHipPivot)
            }) {
                drawImage(
                    image = activeParts.rightFootLayerBmp,
                    dstOffset = dstOffsetInt,
                    dstSize = dstSizeInt,
                    colorFilter = tintFilter,
                    filterQuality = FilterQuality.Medium
                )
            }

            // Layer 2: Real Plush Torso with soft breathing pulse
            withTransform({
                scale(
                    scaleX = breathScale,
                    scaleY = 2f - breathScale,
                    pivot = Offset(baseLeft + dstW * 0.5f, baseTop + dstH * 0.62f)
                )
            }) {
                drawImage(
                    image = activeParts.torsoOnlyLayerBmp,
                    dstOffset = dstOffsetInt,
                    dstSize = dstSizeInt,
                    colorFilter = tintFilter,
                    filterQuality = FilterQuality.Medium
                )
            }

            // Layer 3: Left & Right Real Fluffy Plush Arms & Pink Paw Pads
            withTransform({
                rotate(degrees = leftArmDeg, pivot = leftShoulderPivot)
            }) {
                drawImage(
                    image = activeParts.leftArmLayerBmp,
                    dstOffset = dstOffsetInt,
                    dstSize = dstSizeInt,
                    colorFilter = tintFilter,
                    filterQuality = FilterQuality.Medium
                )
            }
            withTransform({
                rotate(degrees = rightArmDeg, pivot = rightShoulderPivot)
            }) {
                drawImage(
                    image = activeParts.rightArmLayerBmp,
                    dstOffset = dstOffsetInt,
                    dstSize = dstSizeInt,
                    colorFilter = tintFilter,
                    filterQuality = FilterQuality.Medium
                )
            }

            // Layer 4: Real Plush Head & Headphones with rhythmic bob, natural soft blink & playful tongue peek
            withTransform({
                translate(top = headBounceY)
                rotate(degrees = headBobDeg, pivot = neckPivot)
            }) {
                drawImage(
                    image = activeParts.headLayerBmp,
                    dstOffset = dstOffsetInt,
                    dstSize = dstSizeInt,
                    colorFilter = tintFilter,
                    filterQuality = FilterQuality.Medium
                )

                // Soft natural fur-tinted blink with adorable curved Pixar smiling eyelash arcs
                val blinkPhase = ((t * 0.42f) % 1f + 1f) % 1f
                val blinkClose = if (blinkPhase > 0.92f) {
                    sin(((blinkPhase - 0.92f) / 0.08f) * 3.1415927f).coerceIn(0f, 1f)
                } else 0f
                if (blinkClose > 0.16f) {
                    val eyeR = dstW * faceRig.eyeRadiusRatio
                    val lidColor = (customFurColor ?: breed.primaryFur).copy(alpha = 0.94f * blinkClose)
                    val lashColor = Color(0xFF3E2723).copy(alpha = 0.78f * blinkClose)
                    val lashStroke = Stroke(width = (dstW * 0.011f).coerceAtLeast(1.4f), cap = StrokeCap.Round)
                    val lEye = Offset(baseLeft + dstW * faceRig.leftEyeX, baseTop + dstH * faceRig.leftEyeY)
                    val rEye = Offset(baseLeft + dstW * faceRig.rightEyeX, baseTop + dstH * faceRig.rightEyeY)
                    drawOval(
                        color = lidColor,
                        topLeft = Offset(lEye.x - eyeR * 1.06f, lEye.y - eyeR * 1.02f),
                        size = Size(eyeR * 2.12f, eyeR * 2.02f * blinkClose)
                    )
                    drawOval(
                        color = lidColor,
                        topLeft = Offset(rEye.x - eyeR * 1.06f, rEye.y - eyeR * 1.02f),
                        size = Size(eyeR * 2.12f, eyeR * 2.02f * blinkClose)
                    )
                    if (blinkClose > 0.55f) {
                        val arcTop = lEye.y - eyeR * 0.28f
                        drawArc(
                            color = lashColor,
                            startAngle = 205f,
                            sweepAngle = 130f,
                            useCenter = false,
                            topLeft = Offset(lEye.x - eyeR * 0.85f, arcTop),
                            size = Size(eyeR * 1.70f, eyeR * 1.15f),
                            style = lashStroke
                        )
                        drawArc(
                            color = lashColor,
                            startAngle = 205f,
                            sweepAngle = 130f,
                            useCenter = false,
                            topLeft = Offset(rEye.x - eyeR * 0.85f, arcTop),
                            size = Size(eyeR * 1.70f, eyeR * 1.15f),
                            style = lashStroke
                        )
                    }
                }

                // Soft joyful rosy cheek glow pulse during upbeat jumps
                if (airborneFactor > 0.35f || activeHappyJump > 0) {
                    val blushAlpha = (0.18f * max(airborneFactor, if (activeHappyJump > 0) 0.80f else 0f)).coerceIn(0f, 0.22f)
                    val blushR = dstW * 0.062f
                    drawCircle(
                        color = Color(0xFFFF6E9C).copy(alpha = blushAlpha),
                        radius = blushR,
                        center = Offset(baseLeft + dstW * faceRig.leftCheekX, baseTop + dstH * faceRig.cheekY)
                    )
                    drawCircle(
                        color = Color(0xFFFF6E9C).copy(alpha = blushAlpha),
                        radius = blushR,
                        center = Offset(baseLeft + dstW * faceRig.rightCheekX, baseTop + dstH * faceRig.cheekY)
                    )
                }

                // Cute glossy pink tongue peek on tap, drag, upside-down flip, or joyful peak beat jump (zero Path alloc!)
                val showCuteTongue = activeTonguePop > 0 || activeUpsideDownOrFalling || activeLifted || (activeHappyJump > 4)
                if (showCuteTongue) {
                    val tongueStrength = when {
                        activeUpsideDownOrFalling -> 1.0f
                        activeTonguePop > 0 -> (activeTonguePop / 22f).coerceIn(0.48f, 1.0f)
                        activeHappyJump > 4 -> 0.80f
                        else -> 0.72f
                    }
                    val mouthPos = Offset(baseLeft + dstW * faceRig.mouthX, baseTop + dstH * faceRig.mouthY)
                    val tongueW = dstW * 0.084f
                    val tongueH = dstH * 0.062f * tongueStrength
                    val wagX = sin(t * 14f) * dstW * 0.015f
                    tonguePath.reset()
                    tonguePath.moveTo(mouthPos.x - tongueW * 0.48f, mouthPos.y)
                    tonguePath.cubicTo(
                        mouthPos.x - tongueW * 0.56f + wagX * 0.4f, mouthPos.y + tongueH * 0.80f,
                        mouthPos.x - tongueW * 0.20f + wagX, mouthPos.y + tongueH,
                        mouthPos.x + wagX, mouthPos.y + tongueH
                    )
                    tonguePath.cubicTo(
                        mouthPos.x + tongueW * 0.20f + wagX, mouthPos.y + tongueH,
                        mouthPos.x + tongueW * 0.56f + wagX * 0.4f, mouthPos.y + tongueH * 0.80f,
                        mouthPos.x + tongueW * 0.48f, mouthPos.y
                    )
                    tonguePath.close()
                    drawPath(
                        path = tonguePath,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFFFF80AB), Color(0xFFF06292), Color(0xFFD81B60)),
                            startY = mouthPos.y,
                            endY = mouthPos.y + tongueH
                        )
                    )
                    drawLine(
                        color = Color(0xFFAD1457).copy(alpha = 0.42f),
                        start = Offset(mouthPos.x, mouthPos.y + tongueH * 0.14f),
                        end = Offset(mouthPos.x + wagX * 0.65f, mouthPos.y + tongueH * 0.72f),
                        strokeWidth = (dstW * 0.008f).coerceAtLeast(1f),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}
