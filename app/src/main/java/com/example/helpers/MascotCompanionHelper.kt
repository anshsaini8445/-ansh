package com.example.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 100% Real Photorealistic 3D Plush Studio DJ Animals, Teddy Bears, Puppies & Chicks:
 * Directly powered by the high-resolution 3D plush studio renders matching the user's reference photos
 * (Golden Lion DJ "Sher", Polar White Teddy DJ, Fluffy Yellow Chick DJ, Studio Bunny DJ, Honey Teddy DJ,
 * Berry Pink Teddy DJ, Golden Puppy DJ, Baby Panda DJ, Mint Groove Teddy DJ, Royal Crown Penguin DJ,
 * Lavender Mix-Tape Teddy, and Lavender Puppy-Tape DJ).
 */
enum class CompanionBreed(
    val displayName: String,
    @DrawableRes val drawableResId: Int,
    val primaryFur: Color,
    val highlightFur: Color,
    val shadowFur: Color,
    val earColor: Color,
    val muzzlePatch: Color,
    val headphoneAccent: Color,
    val isPenguin: Boolean = false,
    val wearsCrown: Boolean = false,
    val wearsHeadphonesAlways: Boolean = true,
    val isFluffyCassetteMascot: Boolean = false,
    val isTeddyBear: Boolean = false,
    val isLion: Boolean = false,
    val isBunny: Boolean = false,
    val isPolarBear: Boolean = false,
    val isStudioHeadphonesMascot: Boolean = true,
    val cassetteLabel: String = "STUDIO 3D",
    val hatBadgeText: String = "3D DJ",
    val isWhiteFurSubject: Boolean = false
) {
    // ================= 12 REAL PHOTOREALISTIC 3D PLUSH STUDIO MASCOTS =================
    GOLDEN_LION_DJ(
        displayName = "Golden Lion DJ 🦁",
        drawableResId = R.drawable.img_mascot_golden_lion,
        primaryFur = Color(0xFFF9C66B),
        highlightFur = Color(0xFFFFF4D4),
        shadowFur = Color(0xFFD69438),
        earColor = Color(0xFFEA9E34),
        muzzlePatch = Color(0xFFFFF9E8),
        headphoneAccent = Color(0xFF26E7A6),
        isLion = true,
        isStudioHeadphonesMascot = true,
        cassetteLabel = "SHER 3D DJ",
        hatBadgeText = "SHER"
    ),
    SNOW_BEAR_DJ(
        displayName = "Polar White Teddy 🐻‍❄️",
        drawableResId = R.drawable.img_mascot_white_bear,
        primaryFur = Color(0xFFEFF2F6),
        highlightFur = Color(0xFFFFFFFF),
        shadowFur = Color(0xFFBDC7D5),
        earColor = Color(0xFFE2E7ED),
        muzzlePatch = Color(0xFFFBFDFF),
        headphoneAccent = Color(0xFF00E5FF),
        isPolarBear = true,
        isTeddyBear = true,
        isStudioHeadphonesMascot = true,
        cassetteLabel = "WHITE BEAR 3D",
        hatBadgeText = "POLAR",
        isWhiteFurSubject = true
    ),
    YELLOW_CHICK_DJ(
        displayName = "Fluffy Chick DJ 🐥",
        drawableResId = R.drawable.img_mascot_yellow_chick,
        primaryFur = Color(0xFFFFE066),
        highlightFur = Color(0xFFFFF9DB),
        shadowFur = Color(0xFFF59F00),
        earColor = Color(0xFFFF922B),
        muzzlePatch = Color(0xFFFFF3BF),
        headphoneAccent = Color(0xFFFFD43B),
        isStudioHeadphonesMascot = true,
        cassetteLabel = "CHICK 3D DJ",
        hatBadgeText = "CHICK"
    ),
    WHITE_BUNNY_DJ(
        displayName = "Studio Bunny DJ 🐰",
        drawableResId = R.drawable.img_mascot_studio_bunny,
        primaryFur = Color(0xFFF7F8FA),
        highlightFur = Color(0xFFFFFFFF),
        shadowFur = Color(0xFFD3D8E2),
        earColor = Color(0xFFFFB2C9),
        muzzlePatch = Color(0xFFFFFFFF),
        headphoneAccent = Color(0xFFFF4081),
        isBunny = true,
        isStudioHeadphonesMascot = true,
        cassetteLabel = "BUNNY 3D DJ",
        hatBadgeText = "BUNNY",
        isWhiteFurSubject = true
    ),
    HONEY_BEAT_TEDDY(
        displayName = "Honey Brown Teddy 🧸",
        drawableResId = R.drawable.img_mascot_honey_teddy,
        primaryFur = Color(0xFFF6B24B),
        highlightFur = Color(0xFFFFECB3),
        shadowFur = Color(0xFFC67C1B),
        earColor = Color(0xFFD98A26),
        muzzlePatch = Color(0xFFFFF8E1),
        headphoneAccent = Color(0xFFFFB300),
        isFluffyCassetteMascot = true,
        isTeddyBear = true,
        cassetteLabel = "HONEY TEDDY 3D",
        hatBadgeText = "TEDDY"
    ),
    STRAWBERRY_VIBE_TEDDY(
        displayName = "Berry Pink Teddy 🧸",
        drawableResId = R.drawable.img_mascot_pink_teddy,
        primaryFur = Color(0xFFFF7597),
        highlightFur = Color(0xFFFFE4EC),
        shadowFur = Color(0xFFC93B63),
        earColor = Color(0xFFE5537D),
        muzzlePatch = Color(0xFFFFF0F5),
        headphoneAccent = Color(0xFFFF80AB),
        isFluffyCassetteMascot = true,
        isTeddyBear = true,
        cassetteLabel = "PINK TEDDY 3D",
        hatBadgeText = "VIBE"
    ),
    SKY_DJ_PUPPY(
        displayName = "Golden Puppy DJ 🐶",
        drawableResId = R.drawable.img_mascot_sky_puppy,
        primaryFur = Color(0xFFF4A259),
        highlightFur = Color(0xFFFFE0B2),
        shadowFur = Color(0xFFC8762E),
        earColor = Color(0xFF8D4E1F),
        muzzlePatch = Color(0xFFFFF8E7),
        headphoneAccent = Color(0xFF40C4FF),
        isFluffyCassetteMascot = true,
        isTeddyBear = false,
        cassetteLabel = "PUPPY 3D DJ",
        hatBadgeText = "PUPPY"
    ),
    BABY_PANDA_DJ(
        displayName = "Baby Panda DJ 🐼",
        drawableResId = R.drawable.img_mascot_panda_dj,
        primaryFur = Color(0xFFECEFF1),
        highlightFur = Color(0xFFFFFFFF),
        shadowFur = Color(0xFF263238),
        earColor = Color(0xFF212121),
        muzzlePatch = Color(0xFFFFFFFF),
        headphoneAccent = Color(0xFF69F0AE),
        isFluffyCassetteMascot = true,
        isTeddyBear = true,
        cassetteLabel = "PANDA 3D DJ",
        hatBadgeText = "PANDA",
        isWhiteFurSubject = true
    ),
    MINT_GROOVE_TEDDY(
        displayName = "Mint Groove Teddy 🧸",
        drawableResId = R.drawable.img_mascot_mint_teddy,
        primaryFur = Color(0xFF4DB6AC),
        highlightFur = Color(0xFFE0F2F1),
        shadowFur = Color(0xFF00796B),
        earColor = Color(0xFF26A69A),
        muzzlePatch = Color(0xFFF1F8E9),
        headphoneAccent = Color(0xFF64FFDA),
        isFluffyCassetteMascot = true,
        isTeddyBear = true,
        cassetteLabel = "MINT TEDDY 3D",
        hatBadgeText = "GROOVE"
    ),
    ROYAL_KING_PENGUIN(
        displayName = "Royal Penguin DJ 🐧",
        drawableResId = R.drawable.img_mascot_royal_penguin,
        primaryFur = Color(0xFF4A148C),
        highlightFur = Color(0xFFBA68C8),
        shadowFur = Color(0xFF1F063D),
        earColor = Color(0xFFFFD700),
        muzzlePatch = Color(0xFFFFF9E6),
        headphoneAccent = Color(0xFFFFD700),
        isPenguin = true,
        wearsCrown = true,
        cassetteLabel = "ROYAL PENGUIN",
        hatBadgeText = "KING"
    ),
    LAVENDER_MIXTAPE_TEDDY(
        displayName = "Mix-Tape Teddy 🧸",
        drawableResId = R.drawable.img_mascot_lavender_teddy,
        primaryFur = Color(0xFFB39DDB),
        highlightFur = Color(0xFFEDE7F6),
        shadowFur = Color(0xFF673AB7),
        earColor = Color(0xFF9575CD),
        muzzlePatch = Color(0xFFF3E5F5),
        headphoneAccent = Color(0xFFB388FF),
        isFluffyCassetteMascot = true,
        isTeddyBear = true,
        cassetteLabel = "MIX TAPE 3D",
        hatBadgeText = "PLAY"
    ),
    LAVENDER_PUPPY_TAPE(
        displayName = "Puppy-Tape DJ 🐶",
        drawableResId = R.drawable.img_mascot_lavender_puppy,
        primaryFur = Color(0xFFCE93D8),
        highlightFur = Color(0xFFF3E5F5),
        shadowFur = Color(0xFF8E24AA),
        earColor = Color(0xFFAB47BC),
        muzzlePatch = Color(0xFF8D6E63),
        headphoneAccent = Color(0xFFEA80FC),
        isFluffyCassetteMascot = true,
        isTeddyBear = false,
        cassetteLabel = "PUPPY TAPE 3D",
        hatBadgeText = "PLAY"
    );

    companion object {
        val FEATURED_STUDIO_ANIMALS: List<CompanionBreed>
            get() = listOf(
                GOLDEN_LION_DJ,
                SNOW_BEAR_DJ,
                YELLOW_CHICK_DJ,
                WHITE_BUNNY_DJ
            )

        val FLUFFY_CASSETTE_MASCOTS: List<CompanionBreed>
            get() = listOf(
                HONEY_BEAT_TEDDY,
                STRAWBERRY_VIBE_TEDDY,
                SKY_DJ_PUPPY,
                BABY_PANDA_DJ,
                MINT_GROOVE_TEDDY,
                ROYAL_KING_PENGUIN,
                LAVENDER_MIXTAPE_TEDDY,
                LAVENDER_PUPPY_TAPE
            )

        val PENGUIN_BREEDS: List<CompanionBreed>
            get() = listOf(ROYAL_KING_PENGUIN)
    }
}

/**
 * In-memory cache of transparent-cutout 3D Plush Mascot Bitmaps so the outer white studio background
 * is 100% removed when floating over the screen while keeping 100% of the photorealistic 3D plush toy!
 */
private object MascotTransparentBitmapCache {
    private val cache = ConcurrentHashMap<Int, ImageBitmap>()

    suspend fun getOrLoadTransparentMascot(
        context: Context,
        breed: CompanionBreed
    ): ImageBitmap? = withContext(Dispatchers.Default) {
        cache[breed.drawableResId]?.let { return@withContext it }
        try {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = BitmapFactory.decodeResource(context.resources, breed.drawableResId, opts)
                ?: return@withContext null

            val targetW = 420
            val targetH = ((raw.height.toFloat() / raw.width.coerceAtLeast(1)) * targetW).toInt().coerceAtLeast(420)
            val scaled = Bitmap.createScaledBitmap(raw, targetW, targetH, true)
            if (scaled !== raw) {
                raw.recycle()
            }

            val cutout = removeOuterWhiteStudioBackground(
                source = scaled,
                isWhiteFurSubject = breed.isWhiteFurSubject
            )
            val imgBmp = cutout.asImageBitmap()
            cache[breed.drawableResId] = imgBmp
            imgBmp
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Performs a fast 4-connected BFS flood-fill starting from the outer border of the studio render.
     * Removes contiguous pure-white studio backdrop pixels while preserving the 3D plush fur,
     * headphones, glossy eyes, and interior white fur details.
     */
    private fun removeOuterWhiteStudioBackground(
        source: Bitmap,
        isWhiteFurSubject: Boolean
    ): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val visited = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0

        // Strict threshold for white-fur subjects (Polar Bear, Bunny, Panda) so flood-fill never enters shaded fur
        val minBrightness = if (isWhiteFurSubject) 246 else 235
        val maxChannelDiff = if (isWhiteFurSubject) 8 else 18

        fun isStudioWhiteBackdrop(pixel: Int): Boolean {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val minC = min(r, min(g, b))
            val maxC = max(r, max(g, b))
            return minC >= minBrightness && (maxC - minC) <= maxChannelDiff
        }

        fun enqueueIfBackdrop(x: Int, y: Int) {
            if (x < 0 || x >= w || y < 0 || y >= h) return
            val idx = y * w + x
            if (visited[idx]) return
            if (isStudioWhiteBackdrop(pixels[idx])) {
                visited[idx] = true
                queue[tail++] = idx
            }
        }

        // Seed from all 4 outer borders
        for (x in 0 until w) {
            enqueueIfBackdrop(x, 0)
            enqueueIfBackdrop(x, h - 1)
        }
        for (y in 0 until h) {
            enqueueIfBackdrop(0, y)
            enqueueIfBackdrop(w - 1, y)
        }

        while (head < tail) {
            val idx = queue[head++]
            pixels[idx] = 0x00000000 // Fully transparent
            val x = idx % w
            val y = idx / w
            if (x > 0) enqueueIfBackdrop(x - 1, y)
            if (x + 1 < w) enqueueIfBackdrop(x + 1, y)
            if (y > 0) enqueueIfBackdrop(x, y - 1)
            if (y + 1 < h) enqueueIfBackdrop(x, y + 1)
        }

        // Soft 1-pixel anti-aliased edge feathering along the plush fur silhouette
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                if (!visited[idx]) {
                    var transparentNeighbors = 0
                    if (visited[idx - 1]) transparentNeighbors++
                    if (visited[idx + 1]) transparentNeighbors++
                    if (visited[idx - w]) transparentNeighbors++
                    if (visited[idx + w]) transparentNeighbors++
                    if (transparentNeighbors > 0) {
                        val c = pixels[idx]
                        val r = (c shr 16) and 0xFF
                        val g = (c shr 8) and 0xFF
                        val b = c and 0xFF
                        val newAlpha = when (transparentNeighbors) {
                            3, 4 -> 90
                            2 -> 165
                            else -> 225
                        }
                        pixels[idx] = (newAlpha shl 24) or (r shl 16) or (g shl 8) or b
                    }
                }
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }
}

/**
 * Timed Media Hold Gesture Modifier:
 * - Quick tap (<800ms): regular click / open
 * - 2-second hold: triggers 2s hold action (e.g. select item or play)
 * - 3.8-second hold: opens 3-dot action menu
 * - Scrolling cancels automatically so list scrolling remains buttery smooth!
 */
fun Modifier.timedMediaTouchHold(
    onClick: () -> Unit,
    onHold2sPlay: () -> Unit,
    onHold4sMenu: () -> Unit
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnHold2s by rememberUpdatedState(onHold2sPlay)
    val currentOnHold4s by rememberUpdatedState(onHold4sMenu)

    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val startTime = System.currentTimeMillis()
            var actionTaken = false

            val scope = CoroutineScope(Dispatchers.Main)
            val job2s = scope.launch {
                delay(2000L)
                actionTaken = true
                try {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                } catch (_: Exception) {}
                currentOnHold2s()
            }
            val job4s = scope.launch {
                delay(3800L)
                actionTaken = true
                try {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                } catch (_: Exception) {}
                currentOnHold4s()
            }

            var finished = false
            while (!finished) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) {
                    finished = true
                    job2s.cancel()
                    job4s.cancel()
                    val pressDuration = System.currentTimeMillis() - startTime
                    if (!actionTaken && pressDuration < 800L) {
                        currentOnClick()
                    }
                } else {
                    val dx = abs(change.position.x - down.position.x)
                    val dy = abs(change.position.y - down.position.y)
                    if (dx > 36f || dy > 36f) {
                        job2s.cancel()
                        job4s.cancel()
                        finished = true
                    }
                }
            }
        }
    }
}

/**
 * 100% Real Photorealistic 3D Plush Portrait Composable for the Me Section & Companion Gallery!
 * Renders the exact 3D plush animal image with optional studio backdrop and gentle beat motion.
 */
@Composable
fun FluffyMascot3DStudioPortrait(
    breed: CompanionBreed,
    modifier: Modifier = Modifier,
    showStudioBackdrop: Boolean = true,
    animateReels: Boolean = true
) {
    val context = LocalContext.current
    var transparentBmp by remember(breed.drawableResId) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(breed.drawableResId) {
        transparentBmp = MascotTransparentBitmapCache.getOrLoadTransparentMascot(context, breed)
    }

    var animTick by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animateReels) {
        if (animateReels) {
            while (true) {
                delay(32L)
                animTick += 0.14f
            }
        }
    }

    val subtleBobY = if (animateReels) sin(animTick * 2.2f) * 3.5f else 0f
    val subtleTilt = if (animateReels) sin(animTick * 1.6f) * 3.2f else 0f

    Box(
        modifier = modifier.background(
            if (showStudioBackdrop) {
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFF8F9FA),
                        breed.highlightFur.copy(alpha = 0.88f)
                    )
                )
            } else {
                Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
            }
        ),
        contentAlignment = Alignment.Center
    ) {
        val bmp = transparentBmp
        if (!showStudioBackdrop && bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = breed.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = subtleBobY
                        rotationZ = subtleTilt
                    }
            )
        } else {
            // In Me Section Studio Cards: show the 100% crisp original 3D plush photo
            Image(
                painter = painterResource(id = breed.drawableResId),
                contentDescription = breed.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = subtleBobY
                        rotationZ = subtleTilt
                    }
            )
        }
    }
}

/**
 * Interactive 100% Real Photorealistic 3D Plush Companion Overlay:
 * - Renders the real 3D plush mascot (Golden Lion DJ, Polar White Teddy, Fluffy Chick DJ, Bunny DJ,
 *   Honey Teddy, Pink Teddy, Golden Puppy, Baby Panda, Mint Teddy, Royal Penguin, Mix-Tape Teddy)
 *   with its outer white background transparently removed!
 * - Dance Rule: Dances rhythmically ONLY when a song is playing (isMusicPlaying == true).
 *   When music stops, stays completely calm and still.
 * - Upward Fling & Gravity: Throwing upward sends the mascot flying to the top, bouncing off the
 *   ceiling and falling back down under realistic gravity over 2–3 seconds!
 */
@Composable
fun RealisticCompanionOverlay(
    enabled: Boolean,
    breedName: String,
    isMusicPlaying: Boolean,
    penguinsOnlyMode: Boolean = false,
    mascotSizeDp: Dp = 88.dp,
    onQuickPlayTrigger: () -> Unit,
    onHideForSession: () -> Unit
) {
    if (!enabled) return

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val mascotSizePx = with(density) { mascotSizeDp.toPx() }
    val floorY = (screenHeightPx - mascotSizePx - with(density) { 112.dp.toPx() }).coerceAtLeast(80f)

    val breed = remember(breedName, penguinsOnlyMode) {
        val selected = CompanionBreed.entries.find {
            it.displayName.equals(breedName, ignoreCase = true) ||
                it.name.equals(breedName, ignoreCase = true) ||
                breedName.contains(it.displayName.substringBefore(" "), ignoreCase = true)
        } ?: CompanionBreed.GOLDEN_LION_DJ
        if (penguinsOnlyMode) CompanionBreed.ROYAL_KING_PENGUIN else selected
    }

    var transparentMascotBitmap by remember(breed.drawableResId) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(breed.drawableResId) {
        transparentMascotBitmap = MascotTransparentBitmapCache.getOrLoadTransparentMascot(context, breed)
    }

    var posX by remember { mutableFloatStateOf(with(density) { 14.dp.toPx() }) }
    var posY by remember { mutableFloatStateOf(floorY) }
    var velX by remember { mutableFloatStateOf(0f) }
    var velY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var animTick by remember { mutableFloatStateOf(0f) }
    var happyJumpTicks by remember { mutableIntStateOf(0) }

    // DANCE RULE: Strictly dance only when music is playing!
    val isGrooving = isMusicPlaying

    LaunchedEffect(isDragging, isGrooving, floorY, screenWidthPx) {
        while (true) {
            val isAirborne = posY < floorY - 1.5f || abs(velY) > 0.8f
            val delayMs = when {
                isDragging || isAirborne -> 16L
                isGrooving -> 22L
                else -> 80L
            }
            delay(delayMs)

            if (isGrooving) {
                animTick += 0.19f
            } else if (happyJumpTicks > 0) {
                animTick += 0.15f
            }
            if (happyJumpTicks > 0) happyJumpTicks--

            if (!isDragging) {
                if (isAirborne) {
                    // Realistic low-gravity float & bounce ("2-3 second baad takra ke niche aaye")
                    velY += 0.92f
                    posX += velX
                    posY += velY

                    // Top ceiling collision bounce
                    if (posY <= 22f) {
                        posY = 22f
                        velY = abs(velY) * 0.52f
                        velX = (velX * 0.88f) + if (velX >= 0f) 2.6f else -2.6f
                    }

                    // Left & Right screen boundary wall bounce
                    if (posX <= 6f) {
                        posX = 6f
                        velX = abs(velX) * 0.76f
                    } else if (posX >= screenWidthPx - mascotSizePx - 6f) {
                        posX = screenWidthPx - mascotSizePx - 6f
                        velX = -abs(velX) * 0.76f
                    }

                    // Floor landing with spring bounce
                    if (posY >= floorY) {
                        posY = floorY
                        if (abs(velY) > 4.0f) {
                            velY = -velY * 0.46f
                            velX *= 0.78f
                        } else {
                            velY = 0f
                            velX = 0f
                        }
                    }
                } else {
                    posY = floorY
                    velY = 0f
                    velX = 0f
                }
            }
        }
    }

    // Dance kinematics when song is playing (0 when music is paused!)
    val danceBouncePx = if (isGrooving) {
        val extraHop = if (happyJumpTicks > 0) abs(sin(animTick * 4.5f)) * 18f else 0f
        -(abs(sin(animTick * 2.8f)) * 16f + extraHop)
    } else if (happyJumpTicks > 0) {
        -abs(sin(animTick * 3.5f)) * 12f
    } else {
        0f
    }

    val danceRotationDeg = if (isGrooving) {
        sin(animTick * 1.9f) * 8.5f
    } else {
        0f
    }

    val danceScaleX = if (isGrooving) {
        1f + sin(animTick * 3.8f) * 0.045f
    } else {
        1f
    }

    val danceScaleY = if (isGrooving) {
        1f - sin(animTick * 3.8f) * 0.045f
    } else {
        1f
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset { IntOffset(posX.roundToInt(), (posY + danceBouncePx).roundToInt()) }
                .size(mascotSizeDp)
                .pointerInput(isMusicPlaying, breed) {
                    detectTapGestures(
                        onTap = {
                            velY = -19f
                            happyJumpTicks = 45
                            val status = if (isMusicPlaying) "Dancing to the Beat! 🎶" else "Calm & Ready (Play a song to see me dance!)"
                            Toast.makeText(
                                context,
                                "${breed.displayName}: $status",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onDoubleTap = {
                            velY = -24f
                            happyJumpTicks = 70
                            onQuickPlayTrigger()
                            Toast.makeText(
                                context,
                                "🎧 ${breed.displayName} Started Music!",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onLongPress = {
                            onHideForSession()
                            Toast.makeText(
                                context,
                                "Mascot hidden (Turn back ON in Me section)",
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
                            happyJumpTicks = 30
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            posX = (posX + dragAmount.x).coerceIn(0f, screenWidthPx - mascotSizePx)
                            posY = (posY + dragAmount.y).coerceIn(18f, floorY)
                            velX = (dragAmount.x * 0.95f).coerceIn(-36f, 36f)
                            velY = (dragAmount.y * 1.40f).coerceIn(-56f, 36f)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Soft Floor Pedestal Glow + Floating Musical Notes when dancing
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.36f),
                            breed.headphoneAccent.copy(alpha = if (isGrooving) 0.28f else 0.10f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.5f, h * 0.93f),
                        radius = w * 0.42f
                    ),
                    topLeft = Offset(w * 0.12f, h * 0.86f),
                    size = Size(w * 0.76f, h * 0.12f)
                )

                if (isGrooving) {
                    val note1Y = h * (0.28f - ((animTick * 0.08f) % 0.24f))
                    val note2Y = h * (0.34f - (((animTick + 1.5f) * 0.08f) % 0.24f))
                    drawFloatingMusicNote(
                        center = Offset(w * 0.10f, note1Y),
                        size = w * 0.055f,
                        color = breed.headphoneAccent
                    )
                    drawFloatingMusicNote(
                        center = Offset(w * 0.90f, note2Y),
                        size = w * 0.055f,
                        color = Color(0xFFFFD54F)
                    )
                }
            }

            val cutoutBmp = transparentMascotBitmap
            if (cutoutBmp != null) {
                Image(
                    bitmap = cutoutBmp,
                    contentDescription = breed.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                        .graphicsLayer {
                            rotationZ = danceRotationDeg
                            scaleX = danceScaleX
                            scaleY = danceScaleY
                        }
                )
            } else {
                Image(
                    painter = painterResource(id = breed.drawableResId),
                    contentDescription = breed.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = danceRotationDeg
                            scaleX = danceScaleX
                            scaleY = danceScaleY
                        }
                )
            }
        }
    }
}

private fun DrawScope.drawFloatingMusicNote(
    center: Offset,
    size: Float,
    color: Color
) {
    drawCircle(
        color = color,
        radius = size * 0.65f,
        center = Offset(center.x - size * 0.4f, center.y + size * 0.5f)
    )
    drawCircle(
        color = color,
        radius = size * 0.65f,
        center = Offset(center.x + size * 0.8f, center.y + size * 0.2f)
    )
    val stemPath = Path().apply {
        moveTo(center.x + size * 0.15f, center.y + size * 0.5f)
        lineTo(center.x + size * 0.15f, center.y - size * 1.1f)
        lineTo(center.x + size * 1.35f, center.y - size * 1.4f)
        lineTo(center.x + size * 1.35f, center.y + size * 0.2f)
    }
    drawPath(
        path = stemPath,
        color = color,
        style = Stroke(width = size * 0.30f, cap = StrokeCap.Round)
    )
}
