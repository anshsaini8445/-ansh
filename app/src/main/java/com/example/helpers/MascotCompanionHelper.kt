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
 * 20 Ultra-Colorful 3D Chibi Companion Varieties:
 * Includes 6 vibrant Chibi DJ Penguins (Emperor, Neon Cyber, Disco Rainbow, Arctic Crystal,
 * Royal Crown, Galaxy Star) + 14 ultra-colorful Puppies, Shiba, Corgi, Husky, Fox, Kitten & Panda.
 */
enum class CompanionBreed(
    val displayName: String,
    val primaryFur: Color,
    val highlightFur: Color,
    val shadowFur: Color,
    val earColor: Color,
    val muzzlePatch: Color,
    val headphoneAccent: Color,
    val isPenguin: Boolean = false,
    val wearsCrown: Boolean = false,
    val wearsHeadphonesAlways: Boolean = true
) {
    // ================= 6 ULTRA-COLORFUL CHIBI PENGUIN VARIETIES =================
    EMPEROR_PENGUIN_DJ(
        displayName = "Emperor Penguin DJ 🐧",
        primaryFur = Color(0xFF1A2980),
        highlightFur = Color(0xFF26D0CE),
        shadowFur = Color(0xFF0B1340),
        earColor = Color(0xFFFFB300),
        muzzlePatch = Color(0xFFF5FCFF),
        headphoneAccent = Color(0xFF00E5FF),
        isPenguin = true,
        wearsCrown = false
    ),
    NEON_CYBER_PENGUIN(
        displayName = "Neon Cyber Penguin 🐧",
        primaryFur = Color(0xFF8E2DE2),
        highlightFur = Color(0xFFFF007F),
        shadowFur = Color(0xFF3B096C),
        earColor = Color(0xFF00E676),
        muzzlePatch = Color(0xFFFFF0FA),
        headphoneAccent = Color(0xFF00E676),
        isPenguin = true,
        wearsCrown = false
    ),
    DISCO_RAINBOW_PENGUIN(
        displayName = "Disco Rainbow Penguin 🐧",
        primaryFur = Color(0xFFFF416C),
        highlightFur = Color(0xFFFFD200),
        shadowFur = Color(0xFF8A1253),
        earColor = Color(0xFF00F2FE),
        muzzlePatch = Color(0xFFFFFDE7),
        headphoneAccent = Color(0xFFFFEA00),
        isPenguin = true,
        wearsCrown = true
    ),
    ARCTIC_CRYSTAL_PENGUIN(
        displayName = "Arctic Crystal Penguin 🐧",
        primaryFur = Color(0xFF0083B0),
        highlightFur = Color(0xFF80DEEA),
        shadowFur = Color(0xFF004E66),
        earColor = Color(0xFFFF80AB),
        muzzlePatch = Color(0xFFFFFFFF),
        headphoneAccent = Color(0xFF18FFFF),
        isPenguin = true,
        wearsCrown = false
    ),
    ROYAL_KING_PENGUIN(
        displayName = "Royal Crown Penguin 🐧",
        primaryFur = Color(0xFF4A148C),
        highlightFur = Color(0xFFBA68C8),
        shadowFur = Color(0xFF1F063D),
        earColor = Color(0xFFFFD700),
        muzzlePatch = Color(0xFFFFF9E6),
        headphoneAccent = Color(0xFFFFD700),
        isPenguin = true,
        wearsCrown = true
    ),
    GALAXY_STARLIGHT_PENGUIN(
        displayName = "Galaxy Star Penguin 🐧",
        primaryFur = Color(0xFF0F2027),
        highlightFur = Color(0xFF7F53AC),
        shadowFur = Color(0xFF060B0E),
        earColor = Color(0xFFFF4081),
        muzzlePatch = Color(0xFFF3E5F5),
        headphoneAccent = Color(0xFFFF4081),
        isPenguin = true,
        wearsCrown = true
    ),

    // ================= 14 ULTRA-COLORFUL PUPPIES & STUDIO PETS =================
    MUSO_HEADPHONE_PUPPY(
        displayName = "Muso Headphone Puppy (DJ)",
        primaryFur = Color(0xFFF4A259),
        highlightFur = Color(0xFFFFE0B2),
        shadowFur = Color(0xFFC8762E),
        earColor = Color(0xFF8D4E1F),
        muzzlePatch = Color(0xFFFFF8E7),
        headphoneAccent = Color(0xFF00E676)
    ),
    GOLDEN_RETRIEVER(
        displayName = "Golden Retriever Puppy",
        primaryFur = Color(0xFFEFA84A),
        highlightFur = Color(0xFFFFDF9E),
        shadowFur = Color(0xFFB56E1D),
        earColor = Color(0xFF9C5814),
        muzzlePatch = Color(0xFFFFF9EC),
        headphoneAccent = Color(0xFF00E5FF)
    ),
    SIBERIAN_HUSKY(
        displayName = "Siberian Husky",
        primaryFur = Color(0xFF5C82A6),
        highlightFur = Color(0xFFE3F2FD),
        shadowFur = Color(0xFF2C4359),
        earColor = Color(0xFF1C2D3D),
        muzzlePatch = Color(0xFFFFFFFF),
        headphoneAccent = Color(0xFF00FFFF)
    ),
    POMERANIAN_TEDDY(
        displayName = "Pomeranian Teddy",
        primaryFur = Color(0xFFFF8A50),
        highlightFur = Color(0xFFFFD180),
        shadowFur = Color(0xFFC65100),
        earColor = Color(0xFF9B3A00),
        muzzlePatch = Color(0xFFFFF3E0),
        headphoneAccent = Color(0xFFFF4081)
    ),
    CYBER_PUPPY(
        displayName = "Cyber Puppy",
        primaryFur = Color(0xFF00E5FF),
        highlightFur = Color(0xFFE0FFFF),
        shadowFur = Color(0xFF007C91),
        earColor = Color(0xFF7C4DFF),
        muzzlePatch = Color(0xFFF0FFFF),
        headphoneAccent = Color(0xFF00E676)
    ),
    SAKURA_SHIBA_PUPPY(
        displayName = "Sakura Pink Shiba Puppy",
        primaryFur = Color(0xFFFF6EA7),
        highlightFur = Color(0xFFFFC1E3),
        shadowFur = Color(0xFFC51162),
        earColor = Color(0xFF880E4F),
        muzzlePatch = Color(0xFFFFF5F8),
        headphoneAccent = Color(0xFF00E5FF)
    ),
    EMERALD_CORGI_PUPPY(
        displayName = "Emerald Neon Corgi Puppy",
        primaryFur = Color(0xFF00C853),
        highlightFur = Color(0xFFB9F6CA),
        shadowFur = Color(0xFF00692C),
        earColor = Color(0xFF004D40),
        muzzlePatch = Color(0xFFF1F8E9),
        headphoneAccent = Color(0xFFFFEA00)
    ),
    GALAXY_WOLF_PUPPY(
        displayName = "Galaxy Nebula Wolf Pup",
        primaryFur = Color(0xFF7C4DFF),
        highlightFur = Color(0xFFD1C4E9),
        shadowFur = Color(0xFF311B92),
        earColor = Color(0xFFFF007F),
        muzzlePatch = Color(0xFFEDE7F6),
        headphoneAccent = Color(0xFF18FFFF)
    ),
    SUNSET_POODLE_PUPPY(
        displayName = "Sunset Coral Toy Poodle",
        primaryFur = Color(0xFFFF5252),
        highlightFur = Color(0xFFFFAB91),
        shadowFur = Color(0xFFB71C1C),
        earColor = Color(0xFF880E4F),
        muzzlePatch = Color(0xFFFFEBEE),
        headphoneAccent = Color(0xFFFFD740)
    ),
    ELECTRIC_PIKA_PUPPY(
        displayName = "Electric Volt Puppy",
        primaryFur = Color(0xFFFFD600),
        highlightFur = Color(0xFFFFFF8D),
        shadowFur = Color(0xFFFF8F00),
        earColor = Color(0xFF212121),
        muzzlePatch = Color(0xFFFFFDE7),
        headphoneAccent = Color(0xFFFF1744)
    ),
    CRYSTAL_ARCTIC_FOX(
        displayName = "Crystal Nine-Tail Fox",
        primaryFur = Color(0xFF26C6DA),
        highlightFur = Color(0xFFE0F7FA),
        shadowFur = Color(0xFF006064),
        earColor = Color(0xFFEC407A),
        muzzlePatch = Color(0xFFFFFFFF),
        headphoneAccent = Color(0xFFFF4081)
    ),
    PERSIAN_KITTEN(
        displayName = "Persian Kitten",
        primaryFur = Color(0xFFF48FB1),
        highlightFur = Color(0xFFFFFFFF),
        shadowFur = Color(0xFFAD1457),
        earColor = Color(0xFF00E5FF),
        muzzlePatch = Color(0xFFFFFFFF),
        headphoneAccent = Color(0xFF00E676)
    ),
    PANDA(
        displayName = "Panda",
        primaryFur = Color(0xFFFAFAFA),
        highlightFur = Color(0xFFFFFFFF),
        shadowFur = Color(0xFFE0E0E0),
        earColor = Color(0xFF212121),
        muzzlePatch = Color(0xFFFFFFFF),
        headphoneAccent = Color(0xFF00E676)
    ),
    PLUSH_BEAR(
        displayName = "Plush Bear",
        primaryFur = Color(0xFFAB47BC),
        highlightFur = Color(0xFFE1BEE7),
        shadowFur = Color(0xFF4A148C),
        earColor = Color(0xFFFFCA28),
        muzzlePatch = Color(0xFFF3E5F5),
        headphoneAccent = Color(0xFF00E5FF)
    );

    companion object {
        val PENGUIN_BREEDS: List<CompanionBreed>
            get() = entries.filter { it.isPenguin }
    }
}

/**
 * Interactive 3D Chibi Companion Overlay supporting 20 ultra-colorful varieties
 * (6 colorful DJ Penguins + 14 colorful Puppies/Companions) and a "Penguins Only" mode
 * that automatically cycles through colorful Penguin varieties!
 */
@Composable
fun RealisticCompanionOverlay(
    enabled: Boolean,
    breedName: String,
    isMusicPlaying: Boolean,
    penguinsOnlyMode: Boolean = false,
    onQuickPlayTrigger: () -> Unit,
    onHideForSession: () -> Unit
) {
    if (!enabled) return

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val mascotSizePx = with(density) { 104.dp.toPx() }
    val floorY = (screenHeightPx - mascotSizePx - with(density) { 104.dp.toPx() }).coerceAtLeast(100f)

    var penguinCycleIndex by remember { mutableIntStateOf(0) }

    // Auto-cycle penguin colors every 14 seconds when Penguins Only Mode is active
    LaunchedEffect(penguinsOnlyMode) {
        if (penguinsOnlyMode) {
            while (true) {
                delay(14_000L)
                penguinCycleIndex = (penguinCycleIndex + 1) % CompanionBreed.PENGUIN_BREEDS.size
            }
        }
    }

    val breed = remember(breedName, penguinsOnlyMode, penguinCycleIndex) {
        val selected = CompanionBreed.entries.find { it.displayName == breedName }
            ?: CompanionBreed.EMPEROR_PENGUIN_DJ
        if (penguinsOnlyMode) {
            if (selected.isPenguin && penguinCycleIndex == 0) selected
            else CompanionBreed.PENGUIN_BREEDS[penguinCycleIndex % CompanionBreed.PENGUIN_BREEDS.size]
        } else {
            selected
        }
    }

    var posX by remember { mutableFloatStateOf(screenWidthPx * 0.68f) }
    var posY by remember { mutableFloatStateOf(floorY) }
    var velX by remember { mutableFloatStateOf(1.8f) }
    var velY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var walkDirection by remember { mutableFloatStateOf(1f) }
    var animTick by remember { mutableFloatStateOf(0f) }
    var manualDanceMode by remember { mutableStateOf(true) }
    var happyJumpTicks by remember { mutableIntStateOf(0) }

    val isGrooving = isMusicPlaying || manualDanceMode

    // 60FPS Chibi Animation, Dance Choreography & Physics Loop
    LaunchedEffect(isDragging, isGrooving, floorY, screenWidthPx) {
        while (true) {
            delay(16L)
            animTick += if (isGrooving) 0.19f else 0.09f
            if (happyJumpTicks > 0) happyJumpTicks--

            if (!isDragging) {
                if (posY < floorY - 2f || abs(velY) > 1.5f) {
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
                .size(104.dp)
                .pointerInput(isMusicPlaying, penguinsOnlyMode, breed) {
                    detectTapGestures(
                        onTap = {
                            velY = -16f
                            happyJumpTicks = 55
                            if (penguinsOnlyMode || breed.isPenguin) {
                                penguinCycleIndex = (penguinCycleIndex + 1) % CompanionBreed.PENGUIN_BREEDS.size
                            }
                            manualDanceMode = !manualDanceMode
                            val icon = if (breed.isPenguin) "🐧" else "🐶"
                            val status = if (manualDanceMode) "Grooving to the Beat!" else "Waddling Mode"
                            Toast.makeText(
                                context,
                                "$icon ${breed.displayName}: $status",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onDoubleTap = {
                            velY = -20f
                            happyJumpTicks = 80
                            manualDanceMode = true
                            onQuickPlayTrigger()
                            val icon = if (breed.isPenguin) "🐧" else "🎧"
                            Toast.makeText(
                                context,
                                "$icon ${breed.displayName} DJ Beat Drop!",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onLongPress = {
                            onHideForSession()
                            Toast.makeText(
                                context,
                                "Companion hidden for session (Re-enable in Me -> 3D Companion)",
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
            if (breed.isPenguin) {
                drawColorfulChibiPenguin(
                    breed = breed,
                    animTick = animTick,
                    isGrooving = isGrooving,
                    happyJumpTicks = happyJumpTicks
                )
            } else {
                drawColorfulChibiPuppy(
                    breed = breed,
                    animTick = animTick,
                    isGrooving = isGrooving,
                    happyJumpTicks = happyJumpTicks
                )
            }
        }
    }
}

/**
 * Dedicated 3D Ultra-Colorful Chibi Penguin Renderer!
 * Features glossy neon/gradient tuxedo, iridescent pearl belly, flapping penguin flippers,
 * waddling orange-gold webbed feet, cute 3D beak, rosy cheeks, optional royal golden crown,
 * glowing DJ headphones, and rainbow musical notes.
 */
private fun DrawScope.drawColorfulChibiPenguin(
    breed: CompanionBreed,
    animTick: Float,
    isGrooving: Boolean,
    happyJumpTicks: Int
) {
    val w = size.width
    val h = size.height
    val cx = w / 2f

    val beatWave = sin(animTick)
    val fastWave = sin(animTick * 2f)
    val waddleTiltDeg = if (isGrooving) beatWave * 10f else beatWave * 4.5f
    val bodySquishY = if (isGrooving) abs(fastWave) * (h * 0.05f) else abs(beatWave) * (h * 0.016f)
    val hipSwayX = if (isGrooving) cos(animTick) * (w * 0.035f) else 0f
    val leftFlipperFlap = if (isGrooving) (sin(animTick * 1.8f) * 34f - 22f) else sin(animTick) * 12f
    val rightFlipperFlap = if (isGrooving) (-sin(animTick * 1.8f) * 34f + 22f) else -sin(animTick) * 12f
    val footWaddle = sin(animTick * 2.2f) * (if (isGrooving) 20f else 12f)

    val isBlinking = ((animTick * 10).toInt() % 105) in 0..5
    val showHappyArcEyes = happyJumpTicks > 0

    // 1. Rainbow Neon Aura Halo under Floor Shadow
    val shadowScale = (1f - (bodySquishY / (h * 0.18f))).coerceIn(0.65f, 1.05f)
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                breed.headphoneAccent.copy(alpha = 0.42f),
                Color(0x55000000),
                Color.Transparent
            )
        ),
        topLeft = Offset(cx - (w * 0.34f * shadowScale), h * 0.86f),
        size = Size(w * 0.68f * shadowScale, h * 0.11f)
    )

    // 2. Waddling Orange-Gold Webbed Penguin Feet
    val feetColor = Color(0xFFFF9100)
    val feetHighlight = Color(0xFFFFD180)
    rotate(degrees = footWaddle, pivot = Offset(cx - w * 0.13f + hipSwayX, h * 0.82f - bodySquishY)) {
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(feetHighlight, feetColor)),
            topLeft = Offset(cx - w * 0.24f + hipSwayX, h * 0.80f - bodySquishY),
            size = Size(w * 0.19f, h * 0.095f),
            cornerRadius = CornerRadius(w * 0.05f, w * 0.05f)
        )
    }
    rotate(degrees = -footWaddle, pivot = Offset(cx + w * 0.13f + hipSwayX, h * 0.82f - bodySquishY)) {
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(feetHighlight, feetColor)),
            topLeft = Offset(cx + w * 0.05f + hipSwayX, h * 0.80f - bodySquishY),
            size = Size(w * 0.19f, h * 0.095f),
            cornerRadius = CornerRadius(w * 0.05f, w * 0.05f)
        )
    }

    // 3. Chubby Egg-Shaped Chibi Penguin Tuxedo Body + Head Unified Silhouette
    rotate(degrees = waddleTiltDeg, pivot = Offset(cx + hipSwayX, h * 0.55f - bodySquishY)) {
        // Flapping Left Flipper Wing
        rotate(degrees = leftFlipperFlap, pivot = Offset(cx - w * 0.25f + hipSwayX, h * 0.48f - bodySquishY)) {
            drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(breed.highlightFur, breed.primaryFur, breed.shadowFur)
                ),
                topLeft = Offset(cx - w * 0.42f + hipSwayX, h * 0.44f - bodySquishY),
                size = Size(w * 0.20f, h * 0.30f)
            )
        }

        // Flapping Right Flipper Wing
        rotate(degrees = rightFlipperFlap, pivot = Offset(cx + w * 0.25f + hipSwayX, h * 0.48f - bodySquishY)) {
            drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(breed.highlightFur, breed.primaryFur, breed.shadowFur)
                ),
                topLeft = Offset(cx + w * 0.22f + hipSwayX, h * 0.44f - bodySquishY),
                size = Size(w * 0.20f, h * 0.30f)
            )
        }

        // Studio DJ Headband Arch over Penguin Head
        drawArc(
            color = Color(0xFF1A222D),
            startAngle = 188f,
            sweepAngle = 164f,
            useCenter = false,
            topLeft = Offset(cx - w * 0.33f + hipSwayX, h * 0.09f - bodySquishY),
            size = Size(w * 0.66f, h * 0.48f),
            style = Stroke(width = w * 0.058f, cap = StrokeCap.Round)
        )
        drawArc(
            color = breed.headphoneAccent,
            startAngle = 202f,
            sweepAngle = 136f,
            useCenter = false,
            topLeft = Offset(cx - w * 0.33f + hipSwayX, h * 0.09f - bodySquishY),
            size = Size(w * 0.66f, h * 0.48f),
            style = Stroke(width = w * 0.024f, cap = StrokeCap.Round)
        )

        // Outer Glossy Colorful Penguin Body (Plump Egg Squircle)
        val bodyW = w * 0.58f
        val bodyH = h * 0.68f
        val bodyLeft = cx - bodyW / 2f + hipSwayX
        val bodyTop = h * 0.16f - bodySquishY

        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(breed.highlightFur, breed.primaryFur, breed.shadowFur),
                center = Offset(cx - w * 0.08f + hipSwayX, bodyTop + bodyH * 0.28f),
                radius = w * 0.42f
            ),
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(w * 0.28f, w * 0.28f)
        )

        // Specular Liquid Glass Sheen on Penguin Forehead
        drawOval(
            color = Color.White.copy(alpha = 0.28f),
            topLeft = Offset(cx - w * 0.18f + hipSwayX, bodyTop + h * 0.04f),
            size = Size(w * 0.22f, h * 0.07f)
        )

        // Snow-White / Pearl Heart-Shaped Penguin Face & Belly Patch
        drawRoundRect(
            color = breed.muzzlePatch,
            topLeft = Offset(cx - w * 0.21f + hipSwayX, bodyTop + bodyH * 0.20f),
            size = Size(w * 0.42f, bodyH * 0.74f),
            cornerRadius = CornerRadius(w * 0.20f, w * 0.20f)
        )

        // Colorful Neon Bow-Tie at Penguin Neck
        val bowY = bodyTop + bodyH * 0.52f
        val bowLeftPath = Path().apply {
            moveTo(cx + hipSwayX, bowY)
            lineTo(cx - w * 0.10f + hipSwayX, bowY - h * 0.035f)
            lineTo(cx - w * 0.10f + hipSwayX, bowY + h * 0.035f)
            close()
        }
        val bowRightPath = Path().apply {
            moveTo(cx + hipSwayX, bowY)
            lineTo(cx + w * 0.10f + hipSwayX, bowY - h * 0.035f)
            lineTo(cx + w * 0.10f + hipSwayX, bowY + h * 0.035f)
            close()
        }
        drawPath(bowLeftPath, color = breed.earColor)
        drawPath(bowRightPath, color = breed.earColor)
        drawCircle(
            color = breed.headphoneAccent,
            radius = w * 0.026f,
            center = Offset(cx + hipSwayX, bowY)
        )

        // Optional Royal Golden Crown on Top of Penguin Head
        if (breed.wearsCrown) {
            val crownBottom = bodyTop + h * 0.02f
            val crownPath = Path().apply {
                moveTo(cx - w * 0.12f + hipSwayX, crownBottom)
                lineTo(cx - w * 0.14f + hipSwayX, crownBottom - h * 0.09f)
                lineTo(cx - w * 0.06f + hipSwayX, crownBottom - h * 0.04f)
                lineTo(cx + hipSwayX, crownBottom - h * 0.11f)
                lineTo(cx + w * 0.06f + hipSwayX, crownBottom - h * 0.04f)
                lineTo(cx + w * 0.14f + hipSwayX, crownBottom - h * 0.09f)
                lineTo(cx + w * 0.12f + hipSwayX, crownBottom)
                close()
            }
            drawPath(
                path = crownPath,
                brush = Brush.verticalGradient(listOf(Color(0xFFFFF59D), Color(0xFFFFB300)))
            )
            drawCircle(
                color = Color(0xFFFF1744),
                radius = w * 0.02f,
                center = Offset(cx + hipSwayX, crownBottom - h * 0.035f)
            )
        }

        // Studio DJ Headphone Ear-Cups on Both Sides of Penguin Head
        val cupPulse = if (isGrooving) (1f + abs(fastWave) * 0.12f) else 1f
        val cupY = bodyTop + bodyH * 0.28f
        drawStudioHeadphoneCup(
            center = Offset(cx - w * 0.30f + hipSwayX, cupY),
            w = w,
            h = h,
            accentColor = breed.headphoneAccent,
            pulseScale = cupPulse,
            isLeft = true
        )
        drawStudioHeadphoneCup(
            center = Offset(cx + w * 0.30f + hipSwayX, cupY),
            w = w,
            h = h,
            accentColor = breed.headphoneAccent,
            pulseScale = cupPulse,
            isLeft = false
        )

        // Rosy Pink Kawaii Blush Cheeks
        val blushAlpha = if (isGrooving) 0.78f else 0.55f
        val eyeY = bodyTop + bodyH * 0.28f
        drawOval(
            color = Color(0xFFFF5286).copy(alpha = blushAlpha),
            topLeft = Offset(cx - w * 0.22f + hipSwayX, eyeY + h * 0.055f),
            size = Size(w * 0.11f, h * 0.055f)
        )
        drawOval(
            color = Color(0xFFFF5286).copy(alpha = blushAlpha),
            topLeft = Offset(cx + w * 0.11f + hipSwayX, eyeY + h * 0.055f),
            size = Size(w * 0.11f, h * 0.055f)
        )

        // Big Expressive Anime Penguin Eyes
        val leftEyeX = cx - w * 0.115f + hipSwayX
        val rightEyeX = cx + w * 0.115f + hipSwayX
        val eyeRadius = w * 0.066f

        if (isBlinking || showHappyArcEyes) {
            drawArc(
                color = Color(0xFF1A110E),
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(leftEyeX - eyeRadius, eyeY - eyeRadius * 0.6f),
                size = Size(eyeRadius * 2f, eyeRadius * 1.5f),
                style = Stroke(width = w * 0.032f, cap = StrokeCap.Round)
            )
            drawArc(
                color = Color(0xFF1A110E),
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(rightEyeX - eyeRadius, eyeY - eyeRadius * 0.6f),
                size = Size(eyeRadius * 2f, eyeRadius * 1.5f),
                style = Stroke(width = w * 0.032f, cap = StrokeCap.Round)
            )
        } else {
            drawKawaiiPuppyEye(Offset(leftEyeX, eyeY), eyeRadius)
            drawKawaiiPuppyEye(Offset(rightEyeX, eyeY), eyeRadius)
        }

        // Cute Plump Orange-Gold 3D Penguin Beak
        val beakY = eyeY + h * 0.045f
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFE082), Color(0xFFFF8F00))
            ),
            topLeft = Offset(cx - w * 0.075f + hipSwayX, beakY),
            size = Size(w * 0.15f, h * 0.075f),
            cornerRadius = CornerRadius(w * 0.05f, w * 0.05f)
        )
        // Beak shine highlight
        drawOval(
            color = Color.White.copy(alpha = 0.75f),
            topLeft = Offset(cx - w * 0.04f + hipSwayX, beakY + h * 0.01f),
            size = Size(w * 0.06f, h * 0.02f)
        )
    }

    // 4. Floating Rainbow Musical Notes When Dancing
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
            color = breed.earColor.copy(alpha = (1f - noteProgress2).coerceIn(0f, 1f))
        )
    }
}

private fun DrawScope.drawColorfulChibiPuppy(
    breed: CompanionBreed,
    animTick: Float,
    isGrooving: Boolean,
    happyJumpTicks: Int
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

    val isBlinking = ((animTick * 10).toInt() % 110) in 0..5
    val showHappyArcEyes = happyJumpTicks > 0

    // 1. Soft Colorful Floor Aura Shadow
    val shadowScale = (1f - (bodySquishY / (h * 0.18f))).coerceIn(0.65f, 1.05f)
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                breed.headphoneAccent.copy(alpha = 0.35f),
                Color(0x55000000),
                Color.Transparent
            )
        ),
        topLeft = Offset(cx - (w * 0.32f * shadowScale), h * 0.87f),
        size = Size(w * 0.64f * shadowScale, h * 0.095f)
    )

    // 2. Fluffy Wagging Puppy Tail
    rotate(degrees = tailWag, pivot = Offset(cx + w * 0.16f + hipSwayX, h * 0.72f - bodySquishY)) {
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(breed.highlightFur, breed.primaryFur, breed.earColor)
            ),
            topLeft = Offset(cx + w * 0.15f + hipSwayX, h * 0.57f - bodySquishY),
            size = Size(w * 0.13f, h * 0.19f),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
        )
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

    drawOval(
        color = breed.muzzlePatch,
        topLeft = Offset(cx - w * 0.13f + hipSwayX, bodyTop + bodyH * 0.18f),
        size = Size(w * 0.26f, bodyH * 0.68f)
    )

    // 4. Dancing Chibi Feet with Pink Toe Beans
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

    // 6. Dancing Front Paws
    rotate(degrees = leftPawRaise, pivot = Offset(cx - w * 0.18f + hipSwayX, h * 0.58f - bodySquishY)) {
        drawRoundRect(
            color = breed.primaryFur,
            topLeft = Offset(cx - w * 0.32f + hipSwayX, h * 0.55f - bodySquishY),
            size = Size(w * 0.16f, h * 0.11f),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
        )
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

    // 7. BIG ADORABLE MUSO CHIBI HEAD + FLOPPY EARS + STUDIO DJ HEADPHONES
    val headPivot = Offset(cx, h * 0.34f - bodySquishY)
    rotate(degrees = headTiltDeg, pivot = headPivot) {
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

        val showHeadphones = breed.wearsHeadphonesAlways || isGrooving
        if (showHeadphones) {
            drawArc(
                color = Color(0xFF1A222D),
                startAngle = 188f,
                sweepAngle = 164f,
                useCenter = false,
                topLeft = Offset(cx - w * 0.33f, h * 0.06f - bodySquishY),
                size = Size(w * 0.66f, h * 0.50f),
                style = Stroke(width = w * 0.058f, cap = StrokeCap.Round)
            )
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

        drawOval(
            color = breed.primaryFur,
            topLeft = Offset(cx - w * 0.31f, headTop + headH * 0.36f),
            size = Size(w * 0.62f, headH * 0.62f)
        )

        drawOval(
            color = breed.muzzlePatch,
            topLeft = Offset(cx - w * 0.22f, headTop + headH * 0.38f),
            size = Size(w * 0.44f, headH * 0.58f)
        )
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

        val tuftPath = Path().apply {
            moveTo(cx - w * 0.05f, headTop + h * 0.02f)
            quadraticBezierTo(cx, headTop - h * 0.045f, cx + w * 0.04f, headTop + h * 0.015f)
            quadraticBezierTo(cx + w * 0.08f, headTop - h * 0.025f, cx + w * 0.06f, headTop + h * 0.035f)
            close()
        }
        drawPath(tuftPath, color = breed.highlightFur)

        if (showHeadphones) {
            val cupPulse = if (isGrooving) (1f + abs(fastWave) * 0.12f) else 1f
            val cupY = headTop + headH * 0.40f
            drawStudioHeadphoneCup(Offset(cx - w * 0.31f, cupY), w, h, breed.headphoneAccent, cupPulse, isLeft = true)
            drawStudioHeadphoneCup(Offset(cx + w * 0.31f, cupY), w, h, breed.headphoneAccent, cupPulse, isLeft = false)
        }

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

        val eyeY = headTop + headH * 0.42f
        val leftEyeX = cx - w * 0.125f
        val rightEyeX = cx + w * 0.125f
        val eyeRadius = w * 0.068f

        if (breed == CompanionBreed.PANDA) {
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
            drawKawaiiPuppyEye(Offset(leftEyeX, eyeY), eyeRadius)
            drawKawaiiPuppyEye(Offset(rightEyeX, eyeY), eyeRadius)
        }

        val noseY = headTop + headH * 0.56f
        drawRoundRect(
            color = Color(0xFF231815),
            topLeft = Offset(cx - w * 0.048f, noseY),
            size = Size(w * 0.096f, h * 0.055f),
            cornerRadius = CornerRadius(w * 0.045f, w * 0.045f)
        )
        drawOval(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset(cx - w * 0.028f, noseY + h * 0.008f),
            size = Size(w * 0.038f, h * 0.018f)
        )

        val tongueLen = if (isGrooving) h * 0.075f + abs(fastWave) * (h * 0.018f) else h * 0.058f
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFF5286), Color(0xFFFF80AB))
            ),
            topLeft = Offset(cx - w * 0.042f, noseY + h * 0.058f),
            size = Size(w * 0.084f, tongueLen),
            cornerRadius = CornerRadius(w * 0.045f, w * 0.045f)
        )

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

private fun DrawScope.drawKawaiiPuppyEye(center: Offset, radius: Float) {
    drawCircle(
        color = Color(0xFF1A110E),
        radius = radius,
        center = center
    )
    drawOval(
        color = Color(0xFF6D4C41),
        topLeft = Offset(center.x - radius * 0.65f, center.y),
        size = Size(radius * 1.3f, radius * 0.78f)
    )
    drawCircle(
        color = Color.White,
        radius = radius * 0.42f,
        center = Offset(center.x - radius * 0.28f, center.y - radius * 0.28f)
    )
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

    val cushionOffsetX = if (isLeft) cupW * 0.28f else -cupW * 0.28f
    drawRoundRect(
        color = Color(0xFF263238),
        topLeft = Offset(center.x + cushionOffsetX - cupW / 2f, center.y - cupH * 0.46f),
        size = Size(cupW * 0.85f, cupH * 0.92f),
        cornerRadius = CornerRadius(cupW * 0.45f, cupW * 0.45f)
    )

    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFF2C3E50), Color(0xFF141E30))
        ),
        topLeft = Offset(center.x - cupW / 2f, center.y - cupH / 2f),
        size = Size(cupW, cupH),
        cornerRadius = CornerRadius(cupW * 0.5f, cupW * 0.5f)
    )

    drawRoundRect(
        color = accentColor,
        topLeft = Offset(center.x - cupW * 0.34f, center.y - cupH * 0.34f),
        size = Size(cupW * 0.68f, cupH * 0.68f),
        cornerRadius = CornerRadius(cupW * 0.4f, cupW * 0.4f),
        style = Stroke(width = w * 0.018f)
    )

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
    drawOval(
        color = color,
        topLeft = Offset(center.x - scale, center.y),
        size = Size(scale * 1.4f, scale)
    )
    drawLine(
        color = color,
        start = Offset(center.x + scale * 0.3f, center.y + scale * 0.4f),
        end = Offset(center.x + scale * 0.3f, center.y - scale * 1.6f),
        strokeWidth = scale * 0.35f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(center.x + scale * 0.3f, center.y - scale * 1.6f),
        end = Offset(center.x + scale * 1.2f, center.y - scale * 1.0f),
        strokeWidth = scale * 0.35f,
        cap = StrokeCap.Round
    )
}
