package com.example.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * Preserves the exact 12 high-resolution 3D plush studio renders ("mascots par same Rahe")
 * while adding rich, GPU-accelerated 3D animations and interactive on-mascot features.
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
 * 8 Live 3D Mascot Dance & Action Modes ("Mascots guida par animation lage ache sa")
 */
enum class MascotDanceMode(
    val id: String,
    val labelEn: String,
    val labelHi: String,
    val shortBadge: String,
    val accentColor: Color
) {
    AUTO_MIX("AUTO_MIX", "🎭 Auto Mix 8-in-1", "🎭 ऑटो मिक्स डांस", "🎭 Auto", Color(0xFF00E5FF)),
    DJ_BOUNCE("DJ_BOUNCE", "🎧 DJ Bass Bounce", "🎧 डीजे बाउंस", "🎧 DJ", Color(0xFF00E676)),
    BHANGRA_GROOVE("BHANGRA_GROOVE", "🕺 Bhangra Dhol", "🕺 भांगड़ा डांस", "🕺 Bhangra", Color(0xFFFFAB40)),
    SPIN_3D("SPIN_3D", "🌀 360° 3D Spin", "🌀 360° 3D स्पिन", "🌀 3D Spin", Color(0xFFE040FB)),
    ZERO_G_FLOAT("ZERO_G_FLOAT", "🛸 Zero-G Float", "🛸 हवा में तैरना", "🛸 Float", Color(0xFF40C4FF)),
    HEART_DISCO("HEART_DISCO", "💖 Heart Party", "💖 हार्ट डिस्को", "💖 Disco", Color(0xFFFF4081)),
    PATROL_WALK("PATROL_WALK", "🚶 Walk & Dance", "🚶 चलते हुए डांस", "🚶 Walk", Color(0xFF69F0AE)),
    TRAMPOLINE_ACROBAT("TRAMPOLINE_ACROBAT", "🤸 High Jump", "🤸 हाई जम्प", "🤸 Jump", Color(0xFFFFD54F));

    companion object {
        fun fromId(id: String): MascotDanceMode =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: AUTO_MIX
    }
}

/**
 * 6 Glowing Aura & Particle Effects around the Mascot Gudda
 */
enum class MascotAuraEffect(
    val id: String,
    val labelEn: String,
    val labelHi: String,
    val primaryColor: Color,
    val secondaryColor: Color
) {
    NEON_DJ("NEON_DJ", "🎧 Neon Waves", "🎧 नियॉन वेव्स", Color(0xFF00E5FF), Color(0xFF00E676)),
    HEART_SHOWER("HEART_SHOWER", "💖 Hearts Burst", "💖 दिलों की बौछार", Color(0xFFFF4081), Color(0xFFFF80AB)),
    STAR_GALAXY("STAR_GALAXY", "🌟 Gold Stars", "🌟 गोल्डन स्टार्स", Color(0xFFFFD54F), Color(0xFFFFAB40)),
    FIRE_BEAT("FIRE_BEAT", "🔥 Fire Rings", "🔥 फायर रिंग्स", Color(0xFFFF6D00), Color(0xFFFFD600)),
    RAINBOW_HALO("RAINBOW_HALO", "🌈 Rainbow Halo", "🌈 रेनबो हेलो", Color(0xFFE040FB), Color(0xFF00E5FF)),
    LIGHTNING_BASS("LIGHTNING_BASS", "⚡ Cyber Sparks", "⚡ साइबर बिजली", Color(0xFF00E676), Color(0xFF18FFFF));

    companion object {
        fun fromId(id: String): MascotAuraEffect =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: NEON_DJ
    }
}

/**
 * Ultra-Light Bitmap Cache for Mascots:
 * 1) `studioCardCache`: Fast downsampled RGB_565 thumbnails for the Me Section gallery cards.
 * 2) `transparentCutoutCache`: Transparent-cutout 3D Plush Mascot Bitmap computed ONLY for
 *    the active floating mascot at a lightweight 280px resolution.
 */
/**
 * Articulated 3D Plush Mascot Anatomy Layers:
 * Created from the exact 100% real 3D plush studio cutout so that:
 * 1) `leftLegBmp` (baayin tang) & `rightLegBmp` (daayin tang) step forward/backward and lift up/down!
 * 2) `upperBodyBmp` holds the torso & arms while the legs below (`y > 72%`) are cleared to prevent ghosting.
 * 3) `headOverlayBmp` nods/tilts to the beat.
 * 4) `mouthMuzzleBmp` (muh) opens and closes rhythmically as the song plays!
 */
data class ArticulatedMascotParts(
    val fullCutoutBmp: ImageBitmap,
    val upperBodyBmp: ImageBitmap,
    val leftLegBmp: ImageBitmap,
    val rightLegBmp: ImageBitmap,
    val headOverlayBmp: ImageBitmap,
    val mouthMuzzleBmp: ImageBitmap
)

private object MascotTransparentBitmapCache {
    private val studioCardCache = ConcurrentHashMap<Int, ImageBitmap>()
    private val transparentCutoutCache = ConcurrentHashMap<Int, ImageBitmap>()
    private val articulatedPartsCache = ConcurrentHashMap<Int, ArticulatedMascotParts>()

    fun getCachedStudioCardSync(resId: Int): ImageBitmap? = studioCardCache[resId]
    fun getCachedTransparentSync(resId: Int): ImageBitmap? = transparentCutoutCache[resId]
    fun getCachedArticulatedSync(resId: Int): ArticulatedMascotParts? = articulatedPartsCache[resId]

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
        getOrLoadArticulatedMascot(context, breed)?.fullCutoutBmp
    }

    suspend fun getOrLoadArticulatedMascot(
        context: Context,
        breed: CompanionBreed
    ): ArticulatedMascotParts? = withContext(Dispatchers.Default) {
        articulatedPartsCache[breed.drawableResId]?.let { return@withContext it }
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
            val fullImg = cutout.asImageBitmap()
            transparentCutoutCache[breed.drawableResId] = fullImg

            val parts = sliceIntoArticulatedParts(cutout, fullImg)
            articulatedPartsCache[breed.drawableResId] = parts
            parts
        } catch (_: Exception) {
            null
        }
    }

    private fun sliceIntoArticulatedParts(
        cutout: Bitmap,
        fullImg: ImageBitmap
    ): ArticulatedMascotParts {
        val w = cutout.width
        val h = cutout.height
        val srcPixels = IntArray(w * h)
        cutout.getPixels(srcPixels, 0, w, 0, 0, w, h)

        val upperBodyPixels = IntArray(w * h)
        val leftLegPixels = IntArray(w * h)
        val rightLegPixels = IntArray(w * h)
        val headPixels = IntArray(w * h)
        val mouthPixels = IntArray(w * h)

        val legStartY = (h * 0.66f).toInt()
        val upperEndY = (h * 0.74f).toInt()
        val midX = w / 2

        val headEndY = (h * 0.52f).toInt()
        val mouthStartY = (h * 0.27f).toInt()
        val mouthEndY = (h * 0.48f).toInt()
        val mouthStartX = (w * 0.29f).toInt()
        val mouthEndX = (w * 0.71f).toInt()

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val px = srcPixels[idx]
                val alpha = (px ushr 24) and 0xFF
                if (alpha == 0) continue

                // 1. Upper Body & Arms (0%..74% Y, soft fade at hip joint 68%..74% Y so moving legs don't ghost)
                if (y <= upperEndY) {
                    val hipFade = if (y > legStartY) {
                        1f - ((y - legStartY).toFloat() / (upperEndY - legStartY).coerceAtLeast(1)).coerceIn(0f, 1f)
                    } else 1f
                    val newA = (alpha * hipFade).toInt().coerceIn(0, 255)
                    upperBodyPixels[idx] = (px and 0x00FFFFFF) or (newA shl 24)
                }

                // 2. Left Leg & Right Leg (66%..100% Y)
                if (y >= legStartY) {
                    val topFade = ((y - legStartY).toFloat() / (h * 0.06f).coerceAtLeast(1f)).coerceIn(0.25f, 1f)
                    val legA = (alpha * topFade).toInt().coerceIn(0, 255)
                    val legPx = (px and 0x00FFFFFF) or (legA shl 24)
                    if (x <= midX + 3) {
                        leftLegPixels[idx] = legPx
                    }
                    if (x >= midX - 3) {
                        rightLegPixels[idx] = legPx
                    }
                }

                // 3. Articulated Head & Headphones Overlay (0%..52% Y)
                if (y <= headEndY) {
                    val neckFade = if (y > (h * 0.44f)) {
                        1f - ((y - h * 0.44f) / (h * 0.08f).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    } else 1f
                    val headA = (alpha * neckFade).toInt().coerceIn(0, 255)
                    headPixels[idx] = (px and 0x00FFFFFF) or (headA shl 24)
                }

                // 4. Singing Mouth / Muzzle / Jaw Patch (27%..48% Y, 29%..71% X)
                if (y in mouthStartY..mouthEndY && x in mouthStartX..mouthEndX) {
                    val normX = (x - (mouthStartX + mouthEndX) * 0.5f) / ((mouthEndX - mouthStartX) * 0.5f).coerceAtLeast(1f)
                    val normY = (y - (mouthStartY + mouthEndY) * 0.5f) / ((mouthEndY - mouthStartY) * 0.5f).coerceAtLeast(1f)
                    val radialDistSq = normX * normX + normY * normY
                    if (radialDistSq <= 1.0f) {
                        val edgeFeather = (1f - radialDistSq).coerceIn(0f, 1f)
                        val mouthA = (alpha * edgeFeather).toInt().coerceIn(0, 255)
                        mouthPixels[idx] = (px and 0x00FFFFFF) or (mouthA shl 24)
                    }
                }
            }
        }

        fun buildBmp(pixels: IntArray): ImageBitmap {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.setPixels(pixels, 0, w, 0, 0, w, h)
            return bmp.asImageBitmap()
        }

        return ArticulatedMascotParts(
            fullCutoutBmp = fullImg,
            upperBodyBmp = buildBmp(upperBodyPixels),
            leftLegBmp = buildBmp(leftLegPixels),
            rightLegBmp = buildBmp(rightLegPixels),
            headOverlayBmp = buildBmp(headPixels),
            mouthMuzzleBmp = buildBmp(mouthPixels)
        )
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
 * 3D Plush Portrait Composable for the Me Section & Companion Gallery:
 * Uses pre-scaled cached bitmaps + GPU-only `graphicsLayer` live 3D dance animation
 * when `animateReels = true` so the Mascot Gudda looks alive in the Me Section too!
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

    val phaseState = if (animateReels) {
        val infiniteTransition = rememberInfiniteTransition(label = "mascot_card_3d_vibe")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 6.2831855f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "mascot_card_phase"
        )
    } else {
        null
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
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = phaseState?.value ?: 0f
                        if (animateReels) {
                            val hop = -abs(sin(p * 2f)) * 4.dp.toPx()
                            val sway = sin(p) * 5.5f
                            val turn3D = cos(p) * 10f
                            val pulseScale = 1f + sin(p * 2f) * 0.035f
                            translationY = hop
                            rotationZ = sway
                            rotationY = turn3D
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                    }
            )
        }
    }
}

/**
 * Interactive 100% Real Photorealistic 3D Plush Companion ("Gudda") Overlay with:
 * - Continuous, GPU-Smooth 8-Mode 3D Dance & Action Choreography (always alive, zero UI recomposition!).
 * - 6 Glowing Aura & Particle Effects (Neon DJ Waves, Floating Hearts 💖, Gold Stars 🌟, Fire Rings 🔥, Rainbow Halo 🌈, Cyber Sparks ⚡).
 * - Built-in Interactive Mascot Feature Studio Dock right on the Gudda:
 *   1) Switch 8 Live Dance Animations
 *   2) Switch 6 Glowing Aura Effects
 *   3) Switch between all 12 Real 3D Mascots live on screen
 *   4) Sky Toss / 360° Acrobat Flip / Auto-Walk Patrol / Speed (1x–2x)
 *   5) Mini DJ Music Controls (Prev / Play-Pause / Next / Shuffle)
 *   6) Instant Size Adjust (S / M / L / XL / XXL)
 */
@Composable
fun RealisticCompanionOverlay(
    enabled: Boolean,
    breedName: String,
    isMusicPlaying: Boolean,
    penguinsOnlyMode: Boolean = false,
    mascotSizeDp: Dp = 88.dp,
    bottomClearanceDp: Dp = 184.dp,
    danceModeId: String = "AUTO_MIX",
    auraEffectId: String = "NEON_DJ",
    autoWalkEnabled: Boolean = true,
    animSpeedMultiplier: Float = 1.15f,
    onQuickPlayTrigger: () -> Unit,
    onNextTrack: () -> Unit = {},
    onPrevTrack: () -> Unit = {},
    onShufflePlay: () -> Unit = {},
    onSelectBreed: (String) -> Unit = {},
    onSelectSizeDp: (Int) -> Unit = {},
    onSelectDanceMode: (String) -> Unit = {},
    onSelectAuraEffect: (String) -> Unit = {},
    onToggleAutoWalk: (Boolean) -> Unit = {},
    onChangeAnimSpeed: (Float) -> Unit = {},
    onHideForSession: () -> Unit
) {
    if (!enabled) return

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val mascotSizePx = with(density) { mascotSizeDp.toPx() }
    val floorY = (screenHeightPx - mascotSizePx - with(density) { bottomClearanceDp.toPx() }).coerceAtLeast(80f)

    val breed = remember(breedName, penguinsOnlyMode) {
        val selected = CompanionBreed.entries.find {
            it.displayName.equals(breedName, ignoreCase = true) ||
                it.name.equals(breedName, ignoreCase = true) ||
                breedName.contains(it.displayName.substringBefore(" "), ignoreCase = true)
        } ?: CompanionBreed.GOLDEN_LION_DJ
        if (penguinsOnlyMode) CompanionBreed.ROYAL_KING_PENGUIN else selected
    }

    val danceMode = remember(danceModeId) { MascotDanceMode.fromId(danceModeId) }
    val auraEffect = remember(auraEffectId) { MascotAuraEffect.fromId(auraEffectId) }

    var articulatedParts by remember(breed.drawableResId) {
        mutableStateOf(MascotTransparentBitmapCache.getCachedArticulatedSync(breed.drawableResId))
    }
    LaunchedEffect(breed.drawableResId) {
        if (articulatedParts == null) {
            articulatedParts = MascotTransparentBitmapCache.getOrLoadArticulatedMascot(context, breed)
        }
    }

    var posX by remember { mutableFloatStateOf(with(density) { 18.dp.toPx() }) }
    var posY by remember { mutableFloatStateOf(floorY) }
    var velX by remember { mutableFloatStateOf(0f) }
    var velY by remember { mutableFloatStateOf(0f) }
    var walkDir by remember { mutableFloatStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }
    var animTick by remember { mutableFloatStateOf(0f) }
    var happyJumpTicks by remember { mutableIntStateOf(0) }
    var flip360Ticks by remember { mutableIntStateOf(0) }
    var landingSquashTicks by remember { mutableIntStateOf(0) }
    var showMascotFeatureHub by remember { mutableStateOf(false) }

    // Keep mascot on floor when size changes
    LaunchedEffect(floorY) {
        if (posY > floorY) posY = floorY
    }

    // GPU-Accelerated Animation & Physics Loop:
    // Animates, walks forward/backward & up/down, and moves legs/mouth ONLY when a song is playing
    // (`isMusicPlaying == true`) or when the user taps/tosses the Gudda!
    LaunchedEffect(
        isDragging,
        isMusicPlaying,
        danceMode,
        autoWalkEnabled,
        animSpeedMultiplier,
        floorY,
        screenWidthPx,
        mascotSizePx
    ) {
        while (true) {
            val airborneNow = posY < floorY - 1.5f || abs(velY) > 0.8f
            val hasActiveStunt = happyJumpTicks > 0 || flip360Ticks > 0 || landingSquashTicks > 0 || airborneNow || isDragging

            if (!isMusicPlaying && !hasActiveStunt) {
                // Song is paused/stopped: Gudda stands calmly on stage with 0% CPU usage
                delay(120L)
                continue
            }

            delay(20L) // ~50fps silky-smooth GPU animation when music is playing
            val speedFactor = animSpeedMultiplier.coerceIn(0.6f, 2.2f) * 1.28f
            animTick += 0.17f * speedFactor

            if (happyJumpTicks > 0) happyJumpTicks--
            if (flip360Ticks > 0) flip360Ticks--
            if (landingSquashTicks > 0) landingSquashTicks--

            if (!isDragging) {
                if (airborneNow) {
                    // Low-gravity sky toss & bounce physics
                    velY += 0.84f
                    posX += velX
                    posY += velY

                    if (posY <= 20f) {
                        posY = 20f
                        velY = abs(velY) * 0.58f
                        velX = (velX * 0.90f) + if (velX >= 0f) 2.8f else -2.8f
                    }

                    if (posX <= 6f) {
                        posX = 6f
                        velX = abs(velX) * 0.78f
                        walkDir = 1f
                    } else if (posX >= screenWidthPx - mascotSizePx - 6f) {
                        posX = screenWidthPx - mascotSizePx - 6f
                        velX = -abs(velX) * 0.78f
                        walkDir = -1f
                    }

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

                    // Walk Forward & Backward across the screen when song is playing!
                    val shouldPatrol = isMusicPlaying && (autoWalkEnabled || danceMode == MascotDanceMode.PATROL_WALK) && !showMascotFeatureHub
                    if (shouldPatrol) {
                        val walkStep = (if (danceMode == MascotDanceMode.PATROL_WALK) 1.95f else 1.25f) * speedFactor
                        posX += walkDir * walkStep
                        val minX = 10f
                        val maxX = (screenWidthPx - mascotSizePx - 10f).coerceAtLeast(20f)
                        if (posX >= maxX) {
                            posX = maxX
                            walkDir = -1f
                        } else if (posX <= minX) {
                            posX = minX
                            walkDir = 1f
                        }
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // =========================================================================
        // 1. FLOATING MASCOT GUDDA WITH ARTICULATED LEGS (`tange`) & MOUTH (`muh`)
        // =========================================================================
        Box(
            modifier = Modifier
                .offset {
                    val t = animTick
                    val isDancingNow = isMusicPlaying || happyJumpTicks > 0 || flip360Ticks > 0
                    if (!isDancingNow) {
                        IntOffset(posX.roundToInt(), posY.roundToInt())
                    } else {
                        val activeStyle = if (danceMode == MascotDanceMode.AUTO_MIX) {
                            when (((t / 7.5f).toInt()) % 6) {
                                0 -> MascotDanceMode.DJ_BOUNCE
                                1 -> MascotDanceMode.BHANGRA_GROOVE
                                2 -> MascotDanceMode.SPIN_3D
                                3 -> MascotDanceMode.ZERO_G_FLOAT
                                4 -> MascotDanceMode.HEART_DISCO
                                else -> MascotDanceMode.TRAMPOLINE_ACROBAT
                            }
                        } else {
                            danceMode
                        }

                        val celebrationHop = if (happyJumpTicks > 0) -abs(sin(t * 4.2f)) * 20f else 0f

                        // Up and Down (`upar niche`) stepping & dancing bounce
                        val modeBounceY = when (activeStyle) {
                            MascotDanceMode.DJ_BOUNCE -> -abs(sin(t * 3.2f)) * 22f
                            MascotDanceMode.BHANGRA_GROOVE -> -(abs(sin(t * 3.8f)) * 26f + abs(cos(t * 7.6f)) * 8f)
                            MascotDanceMode.SPIN_3D -> -abs(sin(t * 2.8f)) * 18f
                            MascotDanceMode.ZERO_G_FLOAT -> -24f - sin(t * 1.8f) * 18f
                            MascotDanceMode.HEART_DISCO -> -abs(sin(t * 3.4f)) * 22f
                            MascotDanceMode.PATROL_WALK -> -abs(sin(t * 4.6f)) * 18f
                            MascotDanceMode.TRAMPOLINE_ACROBAT -> -abs(sin(t * 2.5f)) * 38f
                            MascotDanceMode.AUTO_MIX -> -abs(sin(t * 3.4f)) * 22f
                        }

                        // Forward and Backward (`aage piche`) groove sway on top of walking patrol
                        val modeSwayX = when (activeStyle) {
                            MascotDanceMode.BHANGRA_GROOVE -> sin(t * 1.9f) * 16f
                            MascotDanceMode.DJ_BOUNCE -> sin(t * 1.6f) * 12f
                            MascotDanceMode.ZERO_G_FLOAT -> cos(t * 1.3f) * 14f
                            MascotDanceMode.HEART_DISCO -> sin(t * 2.2f) * 14f
                            else -> sin(t * 1.8f) * 10f
                        }

                        IntOffset(
                            (posX + modeSwayX).roundToInt(),
                            (posY + modeBounceY + celebrationHop).roundToInt()
                        )
                    }
                }
                .size(mascotSizeDp + 22.dp)
                .pointerInput(isMusicPlaying, breed) {
                    detectTapGestures(
                        onTap = {
                            velY = -18f
                            happyJumpTicks = 50
                            flip360Ticks = 36
                            showMascotFeatureHub = !showMascotFeatureHub
                        },
                        onDoubleTap = {
                            velY = -26f
                            happyJumpTicks = 70
                            flip360Ticks = 45
                            onQuickPlayTrigger()
                            Toast.makeText(
                                context,
                                "🎧 ${breed.displayName}: DJ Music Boost!",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onLongPress = {
                            showMascotFeatureHub = true
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
            // Expressive DJ Stage Aura, Headphone Soundwaves, Hearts, Stars & Sparks
            Canvas(modifier = Modifier.fillMaxSize()) {
                val t = animTick
                val w = size.width
                val h = size.height
                val pulse = if (isMusicPlaying) (sin(t * 2.8f) + 1f) * 0.5f else 0.25f
                val primaryAura = auraEffect.primaryColor
                val secondaryAura = auraEffect.secondaryColor

                // 1. Floor shadow & pulsing DJ stage ring
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryAura.copy(alpha = if (isMusicPlaying) 0.50f else 0.25f),
                            secondaryAura.copy(alpha = if (isMusicPlaying) 0.24f else 0.10f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.5f, h * 0.92f),
                        radius = w * 0.46f
                    ),
                    topLeft = Offset(w * 0.08f, h * 0.84f),
                    size = Size(w * 0.84f, h * 0.14f)
                )

                if (isMusicPlaying) {
                    // Pulsing outer neon stage ring around feet
                    drawOval(
                        color = primaryAura.copy(alpha = 0.58f * (1f - pulse * 0.45f)),
                        topLeft = Offset(w * (0.12f - pulse * 0.07f), h * 0.85f),
                        size = Size(w * (0.76f + pulse * 0.14f), h * 0.10f),
                        style = Stroke(width = 2.2.dp.toPx())
                    )

                    // 2. Left & Right DJ Headphone Sonic Waves
                    val waveExpand = pulse * (w * 0.055f)
                    drawArc(
                        color = primaryAura.copy(alpha = 0.85f),
                        startAngle = 140f,
                        sweepAngle = 80f,
                        useCenter = false,
                        topLeft = Offset(w * 0.02f - waveExpand, h * 0.22f),
                        size = Size(w * 0.22f, h * 0.28f),
                        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = secondaryAura.copy(alpha = 0.85f),
                        startAngle = -40f,
                        sweepAngle = 80f,
                        useCenter = false,
                        topLeft = Offset(w * 0.76f + waveExpand, h * 0.22f),
                        size = Size(w * 0.22f, h * 0.28f),
                        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // 3. Rainbow / Fire / Zero-G Halo Ring when selected
                    if (auraEffect == MascotAuraEffect.RAINBOW_HALO ||
                        auraEffect == MascotAuraEffect.FIRE_BEAT ||
                        danceMode == MascotDanceMode.ZERO_G_FLOAT
                    ) {
                        drawOval(
                            brush = Brush.sweepGradient(
                                listOf(
                                    Color(0xFF00E5FF),
                                    Color(0xFFFF4081),
                                    Color(0xFFFFD54F),
                                    Color(0xFF00E676),
                                    Color(0xFFE040FB),
                                    Color(0xFF00E5FF)
                                )
                            ),
                            topLeft = Offset(w * 0.14f, h * (0.52f + sin(t * 2f) * 0.04f)),
                            size = Size(w * 0.72f, h * 0.14f),
                            style = Stroke(width = 2.4.dp.toPx())
                        )
                    }

                    // 4. Floating Particles based on Aura Effect & Dance Mode
                    val p1Y = h * (0.34f - ((t * 0.065f) % 0.28f))
                    val p2Y = h * (0.40f - (((t + 1.5f) * 0.065f) % 0.28f))
                    val p3Y = h * (0.26f - (((t + 0.8f) * 0.06f) % 0.22f))

                    when {
                        auraEffect == MascotAuraEffect.HEART_SHOWER || danceMode == MascotDanceMode.HEART_DISCO -> {
                            drawFloatingHeart(
                                center = Offset(w * 0.12f, p1Y),
                                size = w * 0.075f,
                                color = Color(0xFFFF4081)
                            )
                            drawFloatingHeart(
                                center = Offset(w * 0.88f, p2Y),
                                size = w * 0.075f,
                                color = Color(0xFFFF80AB)
                            )
                            drawFloatingHeart(
                                center = Offset(w * (0.5f + cos(t) * 0.32f), p3Y),
                                size = w * 0.065f,
                                color = Color(0xFFFF1744)
                            )
                        }
                        auraEffect == MascotAuraEffect.STAR_GALAXY -> {
                            drawSparkleStar(
                                center = Offset(w * 0.12f, p1Y),
                                radius = w * 0.055f,
                                color = Color(0xFFFFD54F)
                            )
                            drawSparkleStar(
                                center = Offset(w * 0.88f, p2Y),
                                radius = w * 0.055f,
                                color = Color(0xFFFFAB40)
                            )
                            drawSparkleStar(
                                center = Offset(w * (0.5f + cos(t) * 0.34f), p3Y),
                                radius = w * 0.05f,
                                color = Color(0xFF00E5FF)
                            )
                        }
                        else -> {
                            drawFloatingMusicNote(
                                center = Offset(w * 0.10f, p1Y),
                                size = w * 0.056f,
                                color = primaryAura
                            )
                            drawFloatingMusicNote(
                                center = Offset(w * 0.90f, p2Y),
                                size = w * 0.056f,
                                color = secondaryAura
                            )
                            drawSparkleStar(
                                center = Offset(w * (0.5f + cos(t) * 0.34f), p3Y),
                                radius = w * 0.046f,
                                color = Color(0xFFFF4081)
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // ARTICULATED 5-PART 3D PLUSH MASCOT ("uske tange bhi hilni chahie aur muh bhi hilna chahie"):
            // 1) Left Leg (`leftLegBmp`) & Right Leg (`rightLegBmp`) step forward/backward & lift up/down
            // 2) Upper Body & Arms (`upperBodyBmp`) groove with 3D body rotation
            // 3) Head (`headOverlayBmp`) nods & tilts to the rhythm
            // 4) Mouth / Jaw (`mouthMuzzleBmp`) opens & closes (`scaleY` + jaw drop) when music plays!
            // =========================================================================
            val parts = articulatedParts
            if (parts != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 12.dp, bottom = 4.dp, start = 4.dp, end = 4.dp)
                        .graphicsLayer {
                            val t = animTick
                            val isDancingNow = isMusicPlaying || happyJumpTicks > 0 || flip360Ticks > 0
                            if (!isDancingNow) {
                                rotationZ = 0f
                                rotationY = 0f
                                scaleX = 1f
                                scaleY = 1f
                            } else {
                                val activeStyle = if (danceMode == MascotDanceMode.AUTO_MIX) {
                                    when (((t / 7.5f).toInt()) % 6) {
                                        0 -> MascotDanceMode.DJ_BOUNCE
                                        1 -> MascotDanceMode.BHANGRA_GROOVE
                                        2 -> MascotDanceMode.SPIN_3D
                                        3 -> MascotDanceMode.ZERO_G_FLOAT
                                        4 -> MascotDanceMode.HEART_DISCO
                                        else -> MascotDanceMode.TRAMPOLINE_ACROBAT
                                    }
                                } else {
                                    danceMode
                                }

                                val airborneNow = posY < floorY - 1.5f || abs(velY) > 0.8f
                                val rotZ = when {
                                    airborneNow && !isDragging -> (velX * 1.4f + sin(t * 2.4f) * 16f).coerceIn(-30f, 30f)
                                    activeStyle == MascotDanceMode.BHANGRA_GROOVE -> sin(t * 3.4f) * 12f
                                    activeStyle == MascotDanceMode.DJ_BOUNCE -> sin(t * 2.1f) * 8f
                                    activeStyle == MascotDanceMode.HEART_DISCO -> sin(t * 2.6f) * 10f
                                    activeStyle == MascotDanceMode.PATROL_WALK -> sin(t * 3.6f) * 8f * walkDir
                                    else -> sin(t * 2.0f) * 7f
                                }

                                val rotY = when {
                                    flip360Ticks > 0 -> (flip360Ticks / 36f) * 360f
                                    activeStyle == MascotDanceMode.SPIN_3D -> (t * 58f) % 360f
                                    activeStyle == MascotDanceMode.BHANGRA_GROOVE -> sin(t * 1.7f) * 20f
                                    activeStyle == MascotDanceMode.PATROL_WALK -> if (walkDir >= 0f) 14f else -14f
                                    else -> sin(t * 1.4f) * 15f
                                }

                                val squash = when {
                                    landingSquashTicks > 0 -> sin(landingSquashTicks * 0.5f) * 0.11f
                                    else -> sin(t * 5.0f) * 0.04f
                                }

                                rotationZ = rotZ
                                rotationY = rotY
                                scaleX = 1f + squash
                                scaleY = 1f - squash
                                cameraDistance = 14f * density.density
                            }
                        }
                ) {
                    // 1A. LEFT LEG (`baayin tang`) — steps forward/backward & lifts up/down
                    Image(
                        bitmap = parts.leftLegBmp,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val t = animTick
                                val isDancingNow = isMusicPlaying || happyJumpTicks > 0
                                if (isDancingNow) {
                                    val stridePhase = sin(t * 5.2f)
                                    // Lift leg up/down and kick forward/backward
                                    translationY = (-max(0f, stridePhase) * 9.dp.toPx()) + (min(0f, stridePhase) * 2.5.dp.toPx())
                                    translationX = stridePhase * 5.5.dp.toPx() * walkDir
                                    rotationZ = stridePhase * 16f
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.36f, 0.70f)
                                }
                            }
                    )

                    // 1B. RIGHT LEG (`daayin tang`) — alternates in opposite phase with Left Leg!
                    Image(
                        bitmap = parts.rightLegBmp,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val t = animTick
                                val isDancingNow = isMusicPlaying || happyJumpTicks > 0
                                if (isDancingNow) {
                                    val stridePhase = -sin(t * 5.2f)
                                    // Lift leg up/down and kick forward/backward in opposite rhythm
                                    translationY = (-max(0f, stridePhase) * 9.dp.toPx()) + (min(0f, stridePhase) * 2.5.dp.toPx())
                                    translationX = stridePhase * 5.5.dp.toPx() * walkDir
                                    rotationZ = stridePhase * 16f
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.64f, 0.70f)
                                }
                            }
                    )

                    // 2. UPPER BODY & ARMS
                    Image(
                        bitmap = parts.upperBodyBmp,
                        contentDescription = breed.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

                    // 3. HEAD & HEADPHONES OVERLAY — nods and tilts to the song beat
                    Image(
                        bitmap = parts.headOverlayBmp,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val t = animTick
                                if (isMusicPlaying || happyJumpTicks > 0) {
                                    translationY = -abs(sin(t * 5.2f)) * 2.5.dp.toPx()
                                    rotationZ = cos(t * 2.6f) * 5.5f
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.46f)
                                }
                            }
                    )

                    // 4. SINGING MOUTH / MUZZLE (`muh`) — opens and closes rhythmically when song plays!
                    Image(
                        bitmap = parts.mouthMuzzleBmp,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val t = animTick
                                if (isMusicPlaying) {
                                    val jawOpen = (sin(t * 7.8f) + 1f) * 0.5f // 0f..1f singing beat
                                    scaleY = 1f + jawOpen * 0.18f
                                    scaleX = 1f + jawOpen * 0.06f
                                    translationY = jawOpen * 3.2.dp.toPx()
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.34f)
                                }
                            }
                    )
                }
            }

            // Live Interactive Mini Badge on top of Gudda (Tap to open Gudda Feature Studio)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xD90A101D))
                    .border(1.dp, danceMode.accentColor.copy(alpha = 0.85f), RoundedCornerShape(50))
                    .clickable { showMascotFeatureHub = !showMascotFeatureHub }
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (isMusicPlaying) "${danceMode.shortBadge} • 🎶" else "⏸️ Tap DJ",
                    color = danceMode.accentColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 8.5.sp,
                    maxLines = 1
                )
            }
        }

        // =========================================================================
        // 2. ON-MASCOT INTERACTIVE FEATURE STUDIO DOCK ("features bhi bdhao mascots par")
        //    Opens right above the bottom bar when user taps the Gudda or its badge!
        // =========================================================================
        AnimatedVisibility(
            visible = showMascotFeatureHub,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 96.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xF5101829),
                                Color(0xF5090E1A)
                            )
                        )
                    )
                    .border(
                        width = 1.6.dp,
                        brush = Brush.horizontalGradient(
                            listOf(
                                breed.headphoneAccent,
                                Color(0xFFFF4081),
                                Color(0xFF00E5FF)
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                // Header: Active Gudda Name + Quick Stunt Buttons + Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🧸 ${breed.displayName} • Live Studio",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "8 Dance Animations • 6 Aura FX • Stunts & Mini DJ",
                            color = breed.headphoneAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Sky Toss Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF00E5FF).copy(alpha = 0.22f))
                                .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(50))
                                .clickable {
                                    velY = -36f
                                    velX = if (posX < screenWidthPx * 0.5f) 10f else -10f
                                    happyJumpTicks = 80
                                    flip360Ticks = 45
                                }
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "🚀 Sky Toss",
                                color = Color(0xFF00E5FF),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp
                            )
                        }

                        // 360° Flip Stunt Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFFFF4081).copy(alpha = 0.22f))
                                .border(1.dp, Color(0xFFFF4081), RoundedCornerShape(50))
                                .clickable {
                                    velY = -22f
                                    happyJumpTicks = 60
                                    flip360Ticks = 54
                                }
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "🤸 360° Flip",
                                color = Color(0xFFFF80AB),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp
                            )
                        }

                        // Close Button
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.14f))
                                .clickable { showMascotFeatureHub = false }
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "✕",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // 1. 8 Live Dance Animation Selector Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    MascotDanceMode.entries.forEach { mode ->
                        val selected = mode == danceMode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (selected) mode.accentColor
                                    else mode.accentColor.copy(alpha = 0.14f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) Color.White else mode.accentColor.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    onSelectDanceMode(mode.id)
                                    happyJumpTicks = 35
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = mode.labelEn,
                                color = if (selected) Color.Black else Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.5.sp
                            )
                        }
                    }
                }

                // 2. 6 Glowing Aura & Particle Effects Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    MascotAuraEffect.entries.forEach { effect ->
                        val selected = effect == auraEffect
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (selected) effect.primaryColor
                                    else effect.primaryColor.copy(alpha = 0.14f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) Color.White else effect.primaryColor.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    onSelectAuraEffect(effect.id)
                                    happyJumpTicks = 30
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = effect.labelEn,
                                color = if (selected) Color.Black else Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // 3. Instant 12-Mascot Switcher Row (Same 12 Real 3D Plush Mascots!)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    CompanionBreed.entries.forEach { candidate ->
                        val isCurrent = candidate == breed
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isCurrent) candidate.headphoneAccent
                                    else Color.White.copy(alpha = 0.10f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isCurrent) Color.White else candidate.headphoneAccent.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    onSelectBreed(candidate.displayName)
                                    happyJumpTicks = 40
                                    flip360Ticks = 36
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = candidate.displayName,
                                color = if (isCurrent) Color.Black else Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // 4. Mini DJ Deck + Auto-Walk + Speed + Size Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    // Mini DJ Controls
                    GuddaActionChip("⏮️ Prev", Color(0xFF00E5FF)) { onPrevTrack() }
                    GuddaActionChip(
                        if (isMusicPlaying) "⏸️ Pause" else "▶️ Play DJ",
                        Color(0xFF00E676)
                    ) { onQuickPlayTrigger() }
                    GuddaActionChip("⏭️ Next", Color(0xFF00E5FF)) { onNextTrack() }
                    GuddaActionChip("🔀 Shuffle", Color(0xFFFFD54F)) { onShufflePlay() }

                    // Auto-Walk Toggle
                    GuddaActionChip(
                        if (autoWalkEnabled) "🚶 Walk: ON" else "🚶 Walk: OFF",
                        if (autoWalkEnabled) Color(0xFF69F0AE) else Color(0xFF90A4AE)
                    ) { onToggleAutoWalk(!autoWalkEnabled) }

                    // Speed Multiplier Cycle
                    val speedLabel = when {
                        animSpeedMultiplier >= 1.75f -> "⚡ Speed: 2x"
                        animSpeedMultiplier >= 1.35f -> "⚡ Speed: 1.5x"
                        else -> "⚡ Speed: 1x"
                    }
                    GuddaActionChip(speedLabel, Color(0xFFE040FB)) {
                        val nextSpeed = when {
                            animSpeedMultiplier < 1.3f -> 1.5f
                            animSpeedMultiplier < 1.75f -> 2.0f
                            else -> 1.0f
                        }
                        onChangeAnimSpeed(nextSpeed)
                    }

                    // Size Presets (Larger & Clearer!)
                    listOf(84 to "S", 112 to "M", 136 to "L", 156 to "XL", 176 to "XXL").forEach { (sz, lbl) ->
                        val activeSz = mascotSizeDp.value.roundToInt() == sz
                        GuddaActionChip(
                            label = "📏 $lbl",
                            color = if (activeSz) Color(0xFFFF4081) else Color(0xFF78909C),
                            filled = activeSz
                        ) {
                            onSelectSizeDp(sz)
                        }
                    }

                    // Hide Mascot for Session
                    GuddaActionChip("🙈 Hide", Color(0xFFFF5252)) {
                        showMascotFeatureHub = false
                        onHideForSession()
                    }
                }
            }
        }
    }
}

@Composable
private fun GuddaActionChip(
    label: String,
    color: Color,
    filled: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (filled) color else color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = if (filled) Color.Black else Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 10.sp,
            maxLines = 1
        )
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

private fun DrawScope.drawFloatingHeart(
    center: Offset,
    size: Float,
    color: Color
) {
    val path = Path().apply {
        val half = size * 0.5f
        moveTo(center.x, center.y + half * 0.85f)
        cubicTo(
            center.x - size * 1.1f, center.y + half * 0.1f,
            center.x - size * 0.65f, center.y - half * 0.95f,
            center.x, center.y - half * 0.28f
        )
        cubicTo(
            center.x + size * 0.65f, center.y - half * 0.95f,
            center.x + size * 1.1f, center.y + half * 0.1f,
            center.x, center.y + half * 0.85f
        )
        close()
    }
    drawPath(path = path, color = color)
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
