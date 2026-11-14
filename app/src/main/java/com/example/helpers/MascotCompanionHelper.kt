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
import androidx.compose.ui.input.pointer.PointerEventPass
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
    ),
    CYBER_NEON_LION(
        displayName = "Cyber Neon Sher ⚡🦁",
        drawableResId = R.drawable.img_mascot_golden_lion,
        primaryFur = Color(0xFF00E5FF),
        highlightFur = Color(0xFFE0F7FA),
        shadowFur = Color(0xFF00838F),
        earColor = Color(0xFF00E676),
        muzzlePatch = Color(0xFFFFFFFF),
        headphoneAccent = Color(0xFF00E5FF),
        isLion = true,
        wearsCrown = true,
        isStudioHeadphonesMascot = true,
        cassetteLabel = "CYBER SHER",
        hatBadgeText = "CYBER"
    ),
    DISCO_PARTY_BUNNY(
        displayName = "Disco Party Bunny 🪩🐰",
        drawableResId = R.drawable.img_mascot_studio_bunny,
        primaryFur = Color(0xFFFF80AB),
        highlightFur = Color(0xFFFFF0F5),
        shadowFur = Color(0xFFC51162),
        earColor = Color(0xFF00E5FF),
        muzzlePatch = Color(0xFFFFFFFF),
        headphoneAccent = Color(0xFFE040FB),
        isBunny = true,
        isStudioHeadphonesMascot = true,
        cassetteLabel = "DISCO BUNNY",
        hatBadgeText = "DISCO",
        isWhiteFurSubject = true
    ),
    BHANGRA_KING_PANDA(
        displayName = "Bhangra King Panda 🥁🐼",
        drawableResId = R.drawable.img_mascot_panda_dj,
        primaryFur = Color(0xFFFFD54F),
        highlightFur = Color(0xFFFFF8E1),
        shadowFur = Color(0xFFFF6F00),
        earColor = Color(0xFF212121),
        muzzlePatch = Color(0xFFFFFFFF),
        headphoneAccent = Color(0xFFFF9100),
        isFluffyCassetteMascot = true,
        isTeddyBear = true,
        wearsCrown = true,
        cassetteLabel = "BHANGRA KING",
        hatBadgeText = "DHOL",
        isWhiteFurSubject = true
    ),
    HIPHOP_GOLD_PUPPY(
        displayName = "Hip-Hop Rapper Puppy 🎤🐶",
        drawableResId = R.drawable.img_mascot_sky_puppy,
        primaryFur = Color(0xFFFFD700),
        highlightFur = Color(0xFFFFF9C4),
        shadowFur = Color(0xFFF57F17),
        earColor = Color(0xFF6D4C41),
        muzzlePatch = Color(0xFFFFFDE7),
        headphoneAccent = Color(0xFF00E676),
        isFluffyCassetteMascot = true,
        wearsCrown = true,
        cassetteLabel = "RAP PUPPY",
        hatBadgeText = "RAP"
    ),
    ROCKSTAR_CHICK_DJ(
        displayName = "Rockstar Chick DJ 🎸🐥",
        drawableResId = R.drawable.img_mascot_yellow_chick,
        primaryFur = Color(0xFFFFAB40),
        highlightFur = Color(0xFFFFE0B2),
        shadowFur = Color(0xFFE65100),
        earColor = Color(0xFFFF1744),
        muzzlePatch = Color(0xFFFFF3E0),
        headphoneAccent = Color(0xFFFF1744),
        isStudioHeadphonesMascot = true,
        cassetteLabel = "ROCK CHICK",
        hatBadgeText = "ROCK"
    ),
    ROYAL_GOLD_TEDDY(
        displayName = "Royal Maharaja Teddy 👑🧸",
        drawableResId = R.drawable.img_mascot_honey_teddy,
        primaryFur = Color(0xFFFFB300),
        highlightFur = Color(0xFFFFECB3),
        shadowFur = Color(0xFFBF360C),
        earColor = Color(0xFFFF6F00),
        muzzlePatch = Color(0xFFFFF8E1),
        headphoneAccent = Color(0xFFFFD700),
        isFluffyCassetteMascot = true,
        isTeddyBear = true,
        wearsCrown = true,
        cassetteLabel = "ROYAL TEDDY",
        hatBadgeText = "KING"
    ),
    COSMIC_ASTRO_PENGUIN(
        displayName = "Cosmic Astro Penguin 🚀🐧",
        drawableResId = R.drawable.img_mascot_royal_penguin,
        primaryFur = Color(0xFF00B0FF),
        highlightFur = Color(0xFF80D8FF),
        shadowFur = Color(0xFF01579B),
        earColor = Color(0xFF00E676),
        muzzlePatch = Color(0xFFE1F5FE),
        headphoneAccent = Color(0xFF00E5FF),
        isPenguin = true,
        wearsCrown = true,
        cassetteLabel = "ASTRO PENGUIN",
        hatBadgeText = "ASTRO"
    ),
    VELVET_BOOMBOX_BEAR(
        displayName = "Velvet Boombox Bear 📻🐻‍❄️",
        drawableResId = R.drawable.img_mascot_white_bear,
        primaryFur = Color(0xFFB388FF),
        highlightFur = Color(0xFFEDE7F6),
        shadowFur = Color(0xFF4A148C),
        earColor = Color(0xFF7C4DFF),
        muzzlePatch = Color(0xFFFFFFFF),
        headphoneAccent = Color(0xFF69F0AE),
        isPolarBear = true,
        isTeddyBear = true,
        isStudioHeadphonesMascot = true,
        cassetteLabel = "BOOMBOX BEAR",
        hatBadgeText = "BASS",
        isWhiteFurSubject = true
    );

    companion object {
        val FEATURED_STUDIO_ANIMALS: List<CompanionBreed>
            get() = listOf(
                GOLDEN_LION_DJ,
                SNOW_BEAR_DJ,
                YELLOW_CHICK_DJ,
                WHITE_BUNNY_DJ,
                CYBER_NEON_LION,
                DISCO_PARTY_BUNNY,
                ROCKSTAR_CHICK_DJ,
                VELVET_BOOMBOX_BEAR
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
                LAVENDER_PUPPY_TAPE,
                BHANGRA_KING_PANDA,
                HIPHOP_GOLD_PUPPY,
                ROYAL_GOLD_TEDDY,
                COSMIC_ASTRO_PENGUIN
            )

        val PENGUIN_BREEDS: List<CompanionBreed>
            get() = listOf(ROYAL_KING_PENGUIN, COSMIC_ASTRO_PENGUIN)
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
 * Clean 3D Plush Cutout Holder — preserves 100% of the real plush mascot details
 * without tearing or smearing pixels across the face or legs.
 */
data class ArticulatedMascotParts(
    val fullCutoutBmp: ImageBitmap
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
            if (cutout !== scaled) {
                scaled.recycle()
            }
            val fullImg = cutout.asImageBitmap()
            transparentCutoutCache[breed.drawableResId] = fullImg

            val parts = ArticulatedMascotParts(fullCutoutBmp = fullImg)
            articulatedPartsCache[breed.drawableResId] = parts
            parts
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
 * - Quick tap (<800ms): regular click / open (ONLY if no child button like the 3-dot icon consumed the tap!)
 * - 2-second hold: triggers 2s hold action (e.g. select item or play)
 * - 3.8-second hold: opens 3-dot action menu
 */
fun Modifier.timedMediaTouchHold(
    onClick: () -> Unit,
    onHold2sPlay: () -> Unit,
    onHold4sMenu: () -> Unit
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val holdScope = rememberCoroutineScope()
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnHold2s by rememberUpdatedState(onHold2sPlay)
    val currentOnHold4s by rememberUpdatedState(onHold4sMenu)

    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Main)
            if (down.isConsumed) return@awaitEachGesture
            // Also ignore taps on the rightmost 56.dp zone where the 3-dot IconButton sits so 3-dot never accidentally plays the video/song!
            val rightEdgeExclusionPx = 56.dp.toPx()
            if (size.width > 160 && down.position.x >= size.width - rightEdgeExclusionPx) {
                return@awaitEachGesture
            }

            val startTime = System.currentTimeMillis()
            var actionTaken = false
            var childConsumed = false

            val job2s = holdScope.launch {
                delay(2000L)
                if (!childConsumed) {
                    actionTaken = true
                    try {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    } catch (_: Exception) {}
                    currentOnHold2s()
                }
            }
            val job4s = holdScope.launch {
                delay(3800L)
                if (!childConsumed) {
                    actionTaken = true
                    try {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    } catch (_: Exception) {}
                    currentOnHold4s()
                }
            }

            var finished = false
            while (!finished) {
                val event = awaitPointerEvent(pass = PointerEventPass.Final)
                if (event.changes.any { it.isConsumed }) {
                    childConsumed = true
                    job2s.cancel()
                    job4s.cancel()
                    finished = true
                    break
                }
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) {
                    finished = true
                    job2s.cancel()
                    job4s.cancel()
                    val pressDuration = System.currentTimeMillis() - startTime
                    if (!actionTaken && !childConsumed && pressDuration < 800L) {
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
                            val hop = -abs(sin(p * 2f)) * 3.5.dp.toPx()
                            val sway = sin(p) * 4.5f
                            val turn3D = (cos(p) * 8f).coerceIn(-10f, 10f)
                            val pulseScale = 1f + sin(p * 2f) * 0.032f
                            translationY = hop
                            rotationZ = sway
                            rotationY = turn3D
                            scaleX = pulseScale
                            scaleY = pulseScale
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.88f)
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
    nowPlayingTitle: String = "",
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

    // Intelligent DJ Brain Commentary State (Muso-style smart companion reactions)
    var smartSpeechText by remember { mutableStateOf("") }
    LaunchedEffect(nowPlayingTitle, isMusicPlaying, breed, danceMode) {
        if (isMusicPlaying && nowPlayingTitle.isNotBlank()) {
            val shortSong = nowPlayingTitle.take(22)
            smartSpeechText = "🎵 $shortSong 🔥"
            happyJumpTicks = 36
        }
    }

    // Keep mascot on floor when size changes
    LaunchedEffect(floorY) {
        if (posY > floorY) posY = floorY
    }

    // Smooth Living Mascot Animation & Physics Loop (~30fps GPU-only):
    // Always alive with natural gentle breathing, blinking glow & sway when paused,
    // and full energetic Muso-style choreographed bounce, walk, waving paws & DJ turntable when `isMusicPlaying == true`!
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
            delay(30L)
            val airborneNow = posY < floorY - 1.5f || abs(velY) > 0.8f
            val hasActiveStunt = happyJumpTicks > 0 || flip360Ticks > 0 || landingSquashTicks > 0 || airborneNow || isDragging
            val speedFactor = animSpeedMultiplier.coerceIn(0.6f, 2.2f)
            animTick += if (isMusicPlaying || hasActiveStunt) 0.27f * speedFactor else 0.11f

            if (happyJumpTicks > 0) happyJumpTicks--
            if (flip360Ticks > 0) flip360Ticks--
            if (landingSquashTicks > 0) landingSquashTicks--

            if (!isDragging) {
                if (airborneNow) {
                    // Low-gravity sky toss & bounce physics
                    velY += 1.05f
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
                        landingSquashTicks = 10
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

                    // Patrol walk left & right across stage when music plays
                    val shouldPatrol = isMusicPlaying && (autoWalkEnabled || danceMode == MascotDanceMode.PATROL_WALK) && !showMascotFeatureHub
                    if (shouldPatrol) {
                        val walkStep = (if (danceMode == MascotDanceMode.PATROL_WALK) 2.4f else 1.55f) * speedFactor
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
        // 1. MUSO-STYLE LIVING ANIMATED 3D MASCOT GUDDA WITH ARTICULATED LIMBS,
        //    LIVE DJ TURNTABLE STAGE, EQUALIZER VISOR & INTELLIGENT SPEECH BUBBLE
        // =========================================================================
        Box(
            modifier = Modifier
                .offset {
                    val t = animTick
                    val isDancingNow = isMusicPlaying || happyJumpTicks > 0 || flip360Ticks > 0
                    if (!isDancingNow) {
                        val idleBob = -abs(sin(t * 1.5f)) * 4.5f
                        val idleSway = sin(t * 0.8f) * 2.5f
                        IntOffset((posX + idleSway).roundToInt(), (posY + idleBob).roundToInt())
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

                        val celebrationHop = if (happyJumpTicks > 0) -abs(sin(t * 3.8f)) * 20f else 0f

                        val modeBounceY = when (activeStyle) {
                            MascotDanceMode.DJ_BOUNCE -> -abs(sin(t * 3.2f)) * 20f
                            MascotDanceMode.BHANGRA_GROOVE -> -(abs(sin(t * 3.6f)) * 24f + abs(cos(t * 7.2f)) * 7f)
                            MascotDanceMode.SPIN_3D -> -abs(sin(t * 2.8f)) * 18f
                            MascotDanceMode.ZERO_G_FLOAT -> -20f - sin(t * 1.9f) * 15f
                            MascotDanceMode.HEART_DISCO -> -abs(sin(t * 3.3f)) * 20f
                            MascotDanceMode.PATROL_WALK -> -abs(sin(t * 4.4f)) * 16f
                            MascotDanceMode.TRAMPOLINE_ACROBAT -> -abs(sin(t * 2.5f)) * 32f
                            MascotDanceMode.AUTO_MIX -> -abs(sin(t * 3.2f)) * 20f
                        }

                        val modeSwayX = when (activeStyle) {
                            MascotDanceMode.BHANGRA_GROOVE -> sin(t * 1.9f) * 14f
                            MascotDanceMode.DJ_BOUNCE -> sin(t * 1.7f) * 10f
                            MascotDanceMode.ZERO_G_FLOAT -> cos(t * 1.4f) * 12f
                            MascotDanceMode.HEART_DISCO -> sin(t * 2.1f) * 12f
                            else -> sin(t * 1.8f) * 9f
                        }

                        IntOffset(
                            (posX + modeSwayX).roundToInt(),
                            (posY + modeBounceY + celebrationHop).roundToInt()
                        )
                    }
                }
                .size(mascotSizeDp + 28.dp)
                .pointerInput(isMusicPlaying, breed) {
                    detectTapGestures(
                        onTap = {
                            velY = -18f
                            happyJumpTicks = 36
                            flip360Ticks = 28
                            showMascotFeatureHub = !showMascotFeatureHub
                        },
                        onDoubleTap = {
                            velY = -26f
                            happyJumpTicks = 48
                            flip360Ticks = 36
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
                            happyJumpTicks = 20
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            posX = (posX + dragAmount.x).coerceIn(0f, screenWidthPx - mascotSizePx)
                            posY = (posY + dragAmount.y).coerceIn(18f, floorY)
                            velX = (dragAmount.x * 0.90f).coerceIn(-34f, 34f)
                            velY = (dragAmount.y * 1.32f).coerceIn(-54f, 34f)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // =========================================================================
            // A. MUSO-STYLE 3D DJ TURNTABLE STAGE, 9-BAR LIVE EQUALIZER & SONIC AURA
            // =========================================================================
            Canvas(modifier = Modifier.fillMaxSize()) {
                val t = animTick
                val w = size.width
                val h = size.height
                val pulse = if (isMusicPlaying) (sin(t * 2.8f) + 1f) * 0.5f else (sin(t * 1.4f) + 1f) * 0.25f
                val primaryAura = auraEffect.primaryColor
                val secondaryAura = auraEffect.secondaryColor

                // 1. Muso-Style Glowing 3D DJ Turntable / Stage Platform under feet
                drawOval(
                    color = Color(0xCC070D19),
                    topLeft = Offset(w * 0.10f, h * 0.83f),
                    size = Size(w * 0.80f, h * 0.14f)
                )
                drawOval(
                    brush = Brush.horizontalGradient(
                        listOf(primaryAura.copy(alpha = 0.78f), secondaryAura.copy(alpha = 0.85f), primaryAura.copy(alpha = 0.78f))
                    ),
                    topLeft = Offset(w * 0.10f, h * 0.83f),
                    size = Size(w * 0.80f, h * 0.14f),
                    style = Stroke(width = if (isMusicPlaying) 2.6.dp.toPx() else 1.5.dp.toPx())
                )

                // 2. Live 9-Bar Beat-Reactive Equalizer inside the Stage Platform
                val barCount = 9
                val totalEqW = w * 0.56f
                val startEqX = w * 0.22f
                val barGap = totalEqW / barCount
                for (i in 0 until barCount) {
                    val eqAmp = if (isMusicPlaying) {
                        (abs(sin(t * 3.4f + i * 0.75f)) * 0.75f + 0.25f)
                    } else {
                        0.22f
                    }
                    val barH = (h * 0.10f) * eqAmp
                    val bx = startEqX + i * barGap + barGap * 0.25f
                    drawLine(
                        color = if (i % 2 == 0) primaryAura else secondaryAura,
                        start = Offset(bx, h * 0.92f),
                        end = Offset(bx, h * 0.92f - barH),
                        strokeWidth = 2.6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                if (isMusicPlaying) {
                    // 3. Left & Right DJ Headphone Sonic Waves
                    val waveExpand = pulse * (w * 0.06f)
                    drawArc(
                        color = primaryAura.copy(alpha = 0.88f),
                        startAngle = 140f,
                        sweepAngle = 80f,
                        useCenter = false,
                        topLeft = Offset(w * 0.02f - waveExpand, h * 0.20f),
                        size = Size(w * 0.22f, h * 0.28f),
                        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = secondaryAura.copy(alpha = 0.88f),
                        startAngle = -40f,
                        sweepAngle = 80f,
                        useCenter = false,
                        topLeft = Offset(w * 0.76f + waveExpand, h * 0.20f),
                        size = Size(w * 0.22f, h * 0.28f),
                        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // 4. Floating Particles based on Aura Effect & Dance Mode
                    val p1Y = h * (0.34f - ((t * 0.06f) % 0.26f))
                    val p2Y = h * (0.40f - (((t + 1.4f) * 0.06f) % 0.26f))
                    val p3Y = h * (0.26f - (((t + 0.8f) * 0.055f) % 0.22f))

                    when {
                        auraEffect == MascotAuraEffect.HEART_SHOWER || danceMode == MascotDanceMode.HEART_DISCO -> {
                            drawFloatingHeart(center = Offset(w * 0.10f, p1Y), size = w * 0.075f, color = Color(0xFFFF4081))
                            drawFloatingHeart(center = Offset(w * 0.90f, p2Y), size = w * 0.075f, color = Color(0xFFFF80AB))
                            drawFloatingHeart(center = Offset(w * (0.5f + cos(t) * 0.30f), p3Y), size = w * 0.065f, color = Color(0xFFFF1744))
                        }
                        auraEffect == MascotAuraEffect.STAR_GALAXY -> {
                            drawSparkleStar(center = Offset(w * 0.10f, p1Y), radius = w * 0.055f, color = Color(0xFFFFD54F))
                            drawSparkleStar(center = Offset(w * 0.90f, p2Y), radius = w * 0.055f, color = Color(0xFFFFAB40))
                            drawSparkleStar(center = Offset(w * (0.5f + cos(t) * 0.32f), p3Y), radius = w * 0.048f, color = Color(0xFF00E5FF))
                        }
                        else -> {
                            drawFloatingMusicNote(center = Offset(w * 0.09f, p1Y), size = w * 0.055f, color = primaryAura)
                            drawFloatingMusicNote(center = Offset(w * 0.91f, p2Y), size = w * 0.055f, color = secondaryAura)
                            drawSparkleStar(center = Offset(w * (0.5f + cos(t) * 0.32f), p3Y), radius = w * 0.046f, color = Color(0xFFFF4081))
                        }
                    }
                }
            }

            // =========================================================================
            // B. REALISTIC 3D PLUSH CHARACTER WITH MUSO-STYLE ARTICULATED RHYTHM
            // =========================================================================
            val parts = articulatedParts
            if (parts != null) {
                Image(
                    bitmap = parts.fullCutoutBmp,
                    contentDescription = breed.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 18.dp, bottom = 6.dp, start = 4.dp, end = 4.dp)
                        .graphicsLayer {
                            val t = animTick
                            val isDancingNow = isMusicPlaying || happyJumpTicks > 0 || flip360Ticks > 0
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.90f)
                            cameraDistance = 16f * density.density

                            if (!isDancingNow) {
                                val breath = sin(t * 1.5f)
                                scaleX = 1f + breath * 0.028f
                                scaleY = 1f - breath * 0.024f
                                rotationZ = sin(t * 0.95f) * 3.2f
                                rotationY = (cos(t * 0.85f) * 6.0f).coerceIn(-12f, 12f)
                                rotationX = (sin(t * 1.15f) * 3.5f).coerceIn(-8f, 8f)
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
                                val cartwheelZ = if (flip360Ticks > 0) {
                                    (1f - (flip360Ticks / 36f).coerceIn(0f, 1f)) * 360f
                                } else 0f

                                val rotZ = when {
                                    flip360Ticks > 0 -> cartwheelZ
                                    airborneNow && !isDragging -> (velX * 1.2f + sin(t * 2.4f) * 14f).coerceIn(-24f, 24f)
                                    activeStyle == MascotDanceMode.BHANGRA_GROOVE -> sin(t * 3.6f) * 13f
                                    activeStyle == MascotDanceMode.DJ_BOUNCE -> sin(t * 2.3f) * 9.5f
                                    activeStyle == MascotDanceMode.HEART_DISCO -> sin(t * 2.7f) * 11f
                                    activeStyle == MascotDanceMode.PATROL_WALK -> sin(t * 4.0f) * 9.5f * walkDir
                                    activeStyle == MascotDanceMode.SPIN_3D -> sin(t * 2.8f) * 12f
                                    else -> sin(t * 2.2f) * 8.0f
                                }

                                val rotY = when {
                                    activeStyle == MascotDanceMode.PATROL_WALK -> (if (walkDir >= 0f) 11f else -11f)
                                    activeStyle == MascotDanceMode.SPIN_3D -> (sin(t * 2.4f) * 12f).coerceIn(-12f, 12f)
                                    else -> (sin(t * 1.8f) * 10.5f).coerceIn(-12f, 12f)
                                }

                                val rotX = (cos(t * 2.6f) * 7.0f).coerceIn(-8f, 8f)

                                val squash = when {
                                    landingSquashTicks > 0 -> sin(landingSquashTicks * 0.5f) * 0.13f
                                    else -> sin(t * 4.6f) * 0.068f
                                }

                                rotationZ = rotZ
                                rotationY = rotY
                                rotationX = rotX
                                scaleX = 1f + squash
                                scaleY = 1f - squash
                            }
                        }
                )
            }

            // =========================================================================
            // C. MUSO-STYLE FOREGROUND ARTICULATED WAVING PAWS, STEPPING FEET,
            //    CROWN & GLOWING DJ HEADPHONE RINGS OVERLAY
            // =========================================================================
            Canvas(modifier = Modifier.fillMaxSize()) {
                val t = animTick
                val w = size.width
                val h = size.height
                val isDancingNow = isMusicPlaying || happyJumpTicks > 0 || flip360Ticks > 0

                // 1. Articulated Waving 3D Plush Paws (Left & Right Hands bopping to the beat!)
                val leftHandWaveY = if (isDancingNow) sin(t * 3.8f) * (h * 0.085f) else sin(t * 1.4f) * (h * 0.022f)
                val rightHandWaveY = if (isDancingNow) -sin(t * 3.8f) * (h * 0.085f) else cos(t * 1.4f) * (h * 0.022f)
                val pawRadius = w * 0.072f

                // Left Waving Plush Paw + Glowing DJ Wristband
                val leftPawCenter = Offset(w * 0.14f, h * 0.56f + leftHandWaveY)
                drawCircle(color = breed.shadowFur, radius = pawRadius * 1.08f, center = leftPawCenter)
                drawCircle(color = breed.primaryFur, radius = pawRadius, center = leftPawCenter)
                drawCircle(color = breed.muzzlePatch, radius = pawRadius * 0.52f, center = leftPawCenter)
                if (isDancingNow) {
                    drawCircle(
                        color = breed.headphoneAccent,
                        radius = pawRadius * 1.15f,
                        center = leftPawCenter,
                        style = Stroke(width = 1.6.dp.toPx())
                    )
                }

                // Right Waving Plush Paw + Glowing DJ Wristband
                val rightPawCenter = Offset(w * 0.86f, h * 0.56f + rightHandWaveY)
                drawCircle(color = breed.shadowFur, radius = pawRadius * 1.08f, center = rightPawCenter)
                drawCircle(color = breed.primaryFur, radius = pawRadius, center = rightPawCenter)
                drawCircle(color = breed.muzzlePatch, radius = pawRadius * 0.52f, center = rightPawCenter)
                if (isDancingNow) {
                    drawCircle(
                        color = auraEffect.primaryColor,
                        radius = pawRadius * 1.15f,
                        center = rightPawCenter,
                        style = Stroke(width = 1.6.dp.toPx())
                    )
                }

                // 2. Articulated Stepping Plush Feet / Sneakers on the Turntable
                val leftFootStepY = if (isDancingNow) (-abs(sin(t * 3.8f)) * (h * 0.055f)) else 0f
                val rightFootStepY = if (isDancingNow) (-abs(cos(t * 3.8f)) * (h * 0.055f)) else 0f
                drawOval(
                    color = breed.primaryFur,
                    topLeft = Offset(w * 0.25f, h * 0.80f + leftFootStepY),
                    size = Size(w * 0.17f, h * 0.095f)
                )
                drawOval(
                    color = breed.headphoneAccent,
                    topLeft = Offset(w * 0.25f, h * 0.80f + leftFootStepY),
                    size = Size(w * 0.17f, h * 0.095f),
                    style = Stroke(width = 1.4.dp.toPx())
                )
                drawOval(
                    color = breed.primaryFur,
                    topLeft = Offset(w * 0.58f, h * 0.80f + rightFootStepY),
                    size = Size(w * 0.17f, h * 0.095f)
                )
                drawOval(
                    color = breed.headphoneAccent,
                    topLeft = Offset(w * 0.58f, h * 0.80f + rightFootStepY),
                    size = Size(w * 0.17f, h * 0.095f),
                    style = Stroke(width = 1.4.dp.toPx())
                )

                // 3. Royal Golden Crown for Royal/King Mascots
                if (breed.wearsCrown) {
                    val crownBob = if (isDancingNow) -abs(sin(t * 3.2f)) * (h * 0.03f) else 0f
                    val crownPath = Path().apply {
                        moveTo(w * 0.37f, h * 0.20f + crownBob)
                        lineTo(w * 0.35f, h * 0.11f + crownBob)
                        lineTo(w * 0.44f, h * 0.16f + crownBob)
                        lineTo(w * 0.50f, h * 0.09f + crownBob)
                        lineTo(w * 0.56f, h * 0.16f + crownBob)
                        lineTo(w * 0.65f, h * 0.11f + crownBob)
                        lineTo(w * 0.63f, h * 0.20f + crownBob)
                        close()
                    }
                    drawPath(crownPath, color = Color(0xFFFFD700))
                    drawPath(crownPath, color = Color(0xFFFF6F00), style = Stroke(width = 1.2.dp.toPx()))
                }
            }

            // =========================================================================
            // D. INTELLIGENT CONTEXT-AWARE DJ SPEECH BUBBLE ABOVE GUDDA
            // =========================================================================
            val intelligentBubbleMessage = remember(isMusicPlaying, nowPlayingTitle, danceMode, breed, (animTick / 16f).toInt(), happyJumpTicks > 0) {
                when {
                    happyJumpTicks > 15 -> "🔥 ${breed.hatBadgeText} Boost! Kya Beat Hai!"
                    isMusicPlaying -> {
                        val phaseIdx = ((animTick / 16f).toInt().coerceAtLeast(0)) % 4
                        when (phaseIdx) {
                            0 -> if (nowPlayingTitle.isNotBlank()) "🎵 ${nowPlayingTitle.take(18)}" else "${danceMode.shortBadge} • Live Beat 🎶"
                            1 -> "🕺 ${danceMode.shortBadge} Dance Mode!"
                            2 -> "🎧 ${breed.hatBadgeText} DJ • Full Vibe!"
                            else -> "⚡ Tap Me for 20 Mascots!"
                        }
                    }
                    else -> "🧸 ${breed.hatBadgeText} • Tap DJ Studio"
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xEB0A101D), Color(0xEB131F36))
                        )
                    )
                    .border(
                        1.2.dp,
                        if (isMusicPlaying) breed.headphoneAccent else danceMode.accentColor.copy(alpha = 0.85f),
                        RoundedCornerShape(50)
                    )
                    .clickable { showMascotFeatureHub = !showMascotFeatureHub }
                    .padding(horizontal = 8.dp, vertical = 2.5.dp)
            ) {
                Text(
                    text = intelligentBubbleMessage,
                    color = if (isMusicPlaying) breed.headphoneAccent else danceMode.accentColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 8.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
                                    happyJumpTicks = 60
                                    flip360Ticks = 34
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
                                    happyJumpTicks = 44
                                    flip360Ticks = 34
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
                                    happyJumpTicks = 34
                                    flip360Ticks = 34
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
