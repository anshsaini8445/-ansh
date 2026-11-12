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
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 100% Real Photorealistic 3D Plush Studio DJ Animals, Teddy Bears, Puppies & Chicks:
 * Directly powered by the high-resolution 3D plush studio renders matching the user's reference photos.
 * Ultra-lightweight memory cache + multi-move 3D dance choreography.
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
 * Ultra-Light Bitmap Cache for Mascots:
 * 1) `studioCardCache`: Fast downsampled RGB_565 thumbnails for the Me Section gallery cards
 *    (avoids decoding full 1024x1024 JPEGs on the UI thread, eliminating scroll lag!).
 * 2) `transparentCutoutCache`: Transparent-cutout 3D Plush Mascot Bitmap computed ONLY for
 *    the single active floating mascot at a lightweight 280px resolution.
 */
private object MascotTransparentBitmapCache {
    private val studioCardCache = ConcurrentHashMap<Int, ImageBitmap>()
    private val transparentCutoutCache = ConcurrentHashMap<Int, ImageBitmap>()

    fun getCachedStudioCardSync(resId: Int): ImageBitmap? = studioCardCache[resId]
    fun getCachedTransparentSync(resId: Int): ImageBitmap? = transparentCutoutCache[resId]

    suspend fun getOrLoadStudioCardBitmap(
        context: Context,
        breed: CompanionBreed
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        studioCardCache[breed.drawableResId]?.let { return@withContext it }
        try {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = 4
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val decoded = BitmapFactory.decodeResource(context.resources, breed.drawableResId, opts)
                ?: return@withContext null
            val imgBmp = decoded.asImageBitmap()
            studioCardCache[breed.drawableResId] = imgBmp
            imgBmp
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getOrLoadTransparentMascot(
        context: Context,
        breed: CompanionBreed
    ): ImageBitmap? = withContext(Dispatchers.Default) {
        transparentCutoutCache[breed.drawableResId]?.let { return@withContext it }
        try {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = 2
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = BitmapFactory.decodeResource(context.resources, breed.drawableResId, opts)
                ?: return@withContext null

            val targetW = 280
            val targetH = ((raw.height.toFloat() / raw.width.coerceAtLeast(1)) * targetW).toInt().coerceAtLeast(280)
            val scaled = Bitmap.createScaledBitmap(raw, targetW, targetH, true)
            if (scaled !== raw) {
                raw.recycle()
            }

            val cutout = removeOuterWhiteStudioBackground(
                source = scaled,
                isWhiteFurSubject = breed.isWhiteFurSubject
            )
            val imgBmp = cutout.asImageBitmap()
            transparentCutoutCache[breed.drawableResId] = imgBmp
            imgBmp
        } catch (_: Exception) {
            null
        }
    }

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
            pixels[idx] = 0x00000000
            val x = idx % w
            val y = idx / w
            if (x > 0) enqueueIfBackdrop(x - 1, y)
            if (x + 1 < w) enqueueIfBackdrop(x + 1, y)
            if (y > 0) enqueueIfBackdrop(x, y - 1)
            if (y + 1 < h) enqueueIfBackdrop(x, y + 1)
        }

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
 * Ultra-Light 3D Plush Portrait Composable for the Me Section & Companion Gallery:
 * Uses pre-scaled cached bitmaps on a background thread so scrolling the Me section
 * has zero UI-thread image decoding and zero background coroutine drain!
 */
@Composable
fun FluffyMascot3DStudioPortrait(
    breed: CompanionBreed,
    modifier: Modifier = Modifier,
    showStudioBackdrop: Boolean = true,
    animateReels: Boolean = true
) {
    val context = LocalContext.current
    var cardBmp by remember(breed.drawableResId, showStudioBackdrop) {
        mutableStateOf(
            if (showStudioBackdrop) {
                MascotTransparentBitmapCache.getCachedStudioCardSync(breed.drawableResId)
            } else {
                MascotTransparentBitmapCache.getCachedTransparentSync(breed.drawableResId)
            }
        )
    }

    LaunchedEffect(breed.drawableResId, showStudioBackdrop) {
        if (cardBmp == null) {
            cardBmp = if (showStudioBackdrop) {
                MascotTransparentBitmapCache.getOrLoadStudioCardBitmap(context, breed)
            } else {
                MascotTransparentBitmapCache.getOrLoadTransparentMascot(context, breed)
            }
        }
    }

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
        val bmp = cardBmp
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = breed.displayName,
                contentScale = if (showStudioBackdrop) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Interactive 100% Real Photorealistic 3D Plush Companion ("Gudda") Overlay with
 * Expressive Multi-Move 3D Dance Choreography & Zero-Idle CPU Drain:
 * - Dances with 4 dynamic moves (Beat Hop, Groove Step, 3D Perspective Turn, Joy Twirl)
 *   + Neon DJ Stage Rings + Soundwaves + Sparkles & Notes ONLY when music is playing
 *   or when tapped!
 * - When music is paused and mascot is resting on the floor, uses 0% CPU (no background loop).
 * - Upward Fling & Gravity: Throwing upward floats the mascot in low-gravity for 2–3 seconds,
 *   bouncing off the ceiling/walls with playful mid-air spin before landing with a spring squash!
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

    var transparentMascotBitmap by remember(breed.drawableResId) {
        mutableStateOf(MascotTransparentBitmapCache.getCachedTransparentSync(breed.drawableResId))
    }
    LaunchedEffect(breed.drawableResId) {
        if (transparentMascotBitmap == null) {
            transparentMascotBitmap = MascotTransparentBitmapCache.getOrLoadTransparentMascot(context, breed)
        }
    }

    var posX by remember { mutableFloatStateOf(with(density) { 14.dp.toPx() }) }
    var posY by remember { mutableFloatStateOf(floorY) }
    var velX by remember { mutableFloatStateOf(0f) }
    var velY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var animTick by remember { mutableFloatStateOf(0f) }
    var happyJumpTicks by remember { mutableIntStateOf(0) }
    var landingSquashTicks by remember { mutableIntStateOf(0) }

    val isGrooving = isMusicPlaying
    val isAirborneState = posY < floorY - 1.5f || abs(velY) > 0.8f
    val needsActiveLoop = isGrooving || isDragging || isAirborneState || happyJumpTicks > 0 || landingSquashTicks > 0

    // Smart Zero-Lag Loop: Runs ONLY when dancing, jumping, dragging, or flying in gravity!
    // Uses 0% CPU when music is paused and mascot is calm!
    LaunchedEffect(needsActiveLoop, isDragging, isGrooving, floorY, screenWidthPx) {
        if (!needsActiveLoop) return@LaunchedEffect
        while (true) {
            val airborneNow = posY < floorY - 1.5f || abs(velY) > 0.8f
            if (!isGrooving && !isDragging && !airborneNow && happyJumpTicks <= 0 && landingSquashTicks <= 0) {
                break
            }
            delay(20L) // Smooth 50fps physics & dance choreography

            if (isGrooving || happyJumpTicks > 0 || airborneNow) {
                animTick += 0.22f
            }
            if (happyJumpTicks > 0) happyJumpTicks--
            if (landingSquashTicks > 0) landingSquashTicks--

            if (!isDragging) {
                if (airborneNow) {
                    // Low-gravity float & bounce ("2-3 second baad takra ke niche aaye")
                    velY += 0.86f
                    posX += velX
                    posY += velY

                    // Top ceiling collision bounce
                    if (posY <= 20f) {
                        posY = 20f
                        velY = abs(velY) * 0.56f
                        velX = (velX * 0.90f) + if (velX >= 0f) 2.8f else -2.8f
                    }

                    // Left & Right screen boundary wall bounce
                    if (posX <= 6f) {
                        posX = 6f
                        velX = abs(velX) * 0.78f
                    } else if (posX >= screenWidthPx - mascotSizePx - 6f) {
                        posX = screenWidthPx - mascotSizePx - 6f
                        velX = -abs(velX) * 0.78f
                    }

                    // Floor landing with spring squash-and-bounce
                    if (posY >= floorY) {
                        posY = floorY
                        landingSquashTicks = 12
                        if (abs(velY) > 3.8f) {
                            velY = -velY * 0.48f
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

    // 4-Style Expressive 3D Dance Choreography when song is playing (or happy jump on tap):
    // Cycles smoothly through: 0 = Beat Bounce, 1 = Side Groove Sway, 2 = 3D Turn Vibe, 3 = Bass Drop Hop
    val dancePhase = ((animTick / 8f).toInt()) % 4
    val isCelebrating = happyJumpTicks > 0

    val danceBouncePx = when {
        isGrooving -> {
            val baseHop = abs(sin(animTick * 2.6f)) * 18f
            val bassBoost = if (dancePhase == 3) abs(sin(animTick * 5.2f)) * 10f else 0f
            val tapBoost = if (isCelebrating) abs(sin(animTick * 4.5f)) * 14f else 0f
            -(baseHop + bassBoost + tapBoost)
        }
        isCelebrating -> -abs(sin(animTick * 3.8f)) * 16f
        else -> 0f
    }

    val danceSwayX = when {
        isGrooving && !isAirborneState && !isDragging -> {
            val multiplier = if (dancePhase == 1) 11f else 6f
            sin(animTick * 1.3f) * multiplier
        }
        isCelebrating && !isAirborneState -> sin(animTick * 2.6f) * 5f
        else -> 0f
    }

    val danceRotationZ = when {
        isAirborneState && !isDragging -> (velX * 1.4f + sin(animTick * 2.2f) * 14f).coerceIn(-28f, 28f)
        isGrooving -> {
            val swing = if (dancePhase == 1) 13.5f else 9.5f
            sin(animTick * 1.95f) * swing
        }
        isCelebrating -> sin(animTick * 3.6f) * 12f
        else -> 0f
    }

    val danceRotationY = when {
        isGrooving -> {
            val turnAmp = if (dancePhase == 2) 22f else 12f
            sin(animTick * 1.3f) * turnAmp
        }
        isCelebrating -> sin(animTick * 2.5f) * 18f
        else -> 0f
    }

    val squashFactor = when {
        landingSquashTicks > 0 -> sin(landingSquashTicks * 0.5f) * 0.12f
        isGrooving -> sin(animTick * 5.2f) * 0.065f
        isCelebrating -> sin(animTick * 5.0f) * 0.075f
        else -> 0f
    }

    val danceScaleX = 1f + squashFactor
    val danceScaleY = 1f - squashFactor

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (posX + danceSwayX).roundToInt(),
                        (posY + danceBouncePx).roundToInt()
                    )
                }
                .size(mascotSizeDp)
                .pointerInput(isMusicPlaying, breed) {
                    detectTapGestures(
                        onTap = {
                            velY = -20f
                            happyJumpTicks = 45
                            val status = if (isMusicPlaying) "Dancing to the Beat! 🎶" else "Happy Jump! (Play a song for non-stop 3D dance!)"
                            Toast.makeText(
                                context,
                                "${breed.displayName}: $status",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onDoubleTap = {
                            velY = -25f
                            happyJumpTicks = 65
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
                            happyJumpTicks = 25
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            posX = (posX + dragAmount.x).coerceIn(0f, screenWidthPx - mascotSizePx)
                            posY = (posY + dragAmount.y).coerceIn(18f, floorY)
                            velX = (dragAmount.x * 0.95f).coerceIn(-36f, 36f)
                            velY = (dragAmount.y * 1.42f).coerceIn(-58f, 36f)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Expressive DJ Stage Aura, Headphone Soundwaves & Floating Notes
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val activeShow = isGrooving || isCelebrating

                // 1. Floor shadow & pulsing DJ stage ring
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            breed.headphoneAccent.copy(alpha = if (activeShow) 0.42f else 0.14f),
                            Color.Black.copy(alpha = 0.28f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.5f, h * 0.93f),
                        radius = w * 0.45f
                    ),
                    topLeft = Offset(w * 0.08f, h * 0.85f),
                    size = Size(w * 0.84f, h * 0.13f)
                )

                if (activeShow) {
                    val pulse = (sin(animTick * 2.6f) + 1f) * 0.5f
                    // Pulsing neon stage ring around feet
                    drawOval(
                        color = breed.headphoneAccent.copy(alpha = 0.45f * (1f - pulse * 0.5f)),
                        topLeft = Offset(w * (0.14f - pulse * 0.08f), h * 0.86f),
                        size = Size(w * (0.72f + pulse * 0.16f), h * 0.10f),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Left & Right DJ Headphone Sonic Waves
                    val waveExpand = pulse * (w * 0.06f)
                    drawArc(
                        color = breed.headphoneAccent.copy(alpha = 0.75f),
                        startAngle = 140f,
                        sweepAngle = 80f,
                        useCenter = false,
                        topLeft = Offset(-waveExpand, h * 0.18f),
                        size = Size(w * 0.22f, h * 0.30f),
                        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = Color(0xFFFFD54F).copy(alpha = 0.75f),
                        startAngle = -40f,
                        sweepAngle = 80f,
                        useCenter = false,
                        topLeft = Offset(w * 0.78f + waveExpand, h * 0.18f),
                        size = Size(w * 0.22f, h * 0.30f),
                        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Floating Musical Notes & Sparkle Stars
                    val note1Y = h * (0.30f - ((animTick * 0.07f) % 0.26f))
                    val note2Y = h * (0.36f - (((animTick + 1.6f) * 0.07f) % 0.26f))
                    val starY = h * (0.22f - (((animTick + 0.8f) * 0.06f) % 0.20f))

                    drawFloatingMusicNote(
                        center = Offset(w * 0.09f, note1Y),
                        size = w * 0.058f,
                        color = breed.headphoneAccent
                    )
                    drawFloatingMusicNote(
                        center = Offset(w * 0.91f, note2Y),
                        size = w * 0.058f,
                        color = Color(0xFFFFD54F)
                    )
                    drawSparkleStar(
                        center = Offset(w * (0.5f + cos(animTick) * 0.36f), starY),
                        radius = w * 0.045f,
                        color = Color(0xFFFF4081)
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
                            rotationZ = danceRotationZ
                            rotationY = danceRotationY
                            scaleX = danceScaleX
                            scaleY = danceScaleY
                            cameraDistance = 14f * density.density
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

private fun DrawScope.drawSparkleStar(
    center: Offset,
    radius: Float,
    color: Color
) {
    drawLine(
        color = color,
        start = Offset(center.x - radius, center.y),
        end = Offset(center.x + radius, center.y),
        strokeWidth = radius * 0.32f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(center.x, center.y - radius),
        end = Offset(center.x, center.y + radius),
        strokeWidth = radius * 0.32f,
        cap = StrokeCap.Round
    )
}
