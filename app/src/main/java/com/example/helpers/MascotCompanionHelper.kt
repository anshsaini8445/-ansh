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
import androidx.compose.ui.graphics.nativeCanvas
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

data class ArticulatedMascotParts(
    val rawCutoutBitmap: Bitmap,
    val fullCutoutBmp: ImageBitmap,
    val headLayerBmp: ImageBitmap,
    val leftArmLayerBmp: ImageBitmap,
    val rightArmLayerBmp: ImageBitmap,
    val leftFootLayerBmp: ImageBitmap,
    val rightFootLayerBmp: ImageBitmap
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
            val w = cutout.width
            val h = cutout.height
            val srcPixels = IntArray(w * h)
            cutout.getPixels(srcPixels, 0, w, 0, 0, w, h)

            val fullImg = cutout.asImageBitmap()
            transparentCutoutCache[breed.drawableResId] = fullImg

            val headImg = extractFeatheredPlushRegion(
                srcPixels = srcPixels,
                w = w,
                h = h,
                cxRatio = 0.50f,
                cyRatio = 0.27f,
                rxRatio = 0.43f,
                ryRatio = 0.27f
            ).asImageBitmap()

            val leftArmImg = extractFeatheredPlushRegion(
                srcPixels = srcPixels,
                w = w,
                h = h,
                cxRatio = 0.18f,
                cyRatio = 0.56f,
                rxRatio = 0.18f,
                ryRatio = 0.17f
            ).asImageBitmap()

            val rightArmImg = extractFeatheredPlushRegion(
                srcPixels = srcPixels,
                w = w,
                h = h,
                cxRatio = 0.82f,
                cyRatio = 0.56f,
                rxRatio = 0.18f,
                ryRatio = 0.17f
            ).asImageBitmap()

            val leftFootImg = extractFeatheredPlushRegion(
                srcPixels = srcPixels,
                w = w,
                h = h,
                cxRatio = 0.34f,
                cyRatio = 0.85f,
                rxRatio = 0.19f,
                ryRatio = 0.15f
            ).asImageBitmap()

            val rightFootImg = extractFeatheredPlushRegion(
                srcPixels = srcPixels,
                w = w,
                h = h,
                cxRatio = 0.66f,
                cyRatio = 0.85f,
                rxRatio = 0.19f,
                ryRatio = 0.15f
            ).asImageBitmap()

            val parts = ArticulatedMascotParts(
                rawCutoutBitmap = cutout,
                fullCutoutBmp = fullImg,
                headLayerBmp = headImg,
                leftArmLayerBmp = leftArmImg,
                rightArmLayerBmp = rightArmImg,
                leftFootLayerBmp = leftFootImg,
                rightFootLayerBmp = rightFootImg
            )
            articulatedPartsCache[breed.drawableResId] = parts
            parts
        } catch (_: Exception) {
            null
        }
    }

    private fun extractFeatheredPlushRegion(
        srcPixels: IntArray,
        w: Int,
        h: Int,
        cxRatio: Float,
        cyRatio: Float,
        rxRatio: Float,
        ryRatio: Float
    ): Bitmap {
        val outPixels = IntArray(w * h)
        val cx = cxRatio * w
        val cy = cyRatio * h
        val rx = (rxRatio * w).coerceAtLeast(1f)
        val ry = (ryRatio * h).coerceAtLeast(1f)

        val minX = (cx - rx).toInt().coerceIn(0, w - 1)
        val maxX = (cx + rx).toInt().coerceIn(0, w - 1)
        val minY = (cy - ry).toInt().coerceIn(0, h - 1)
        val maxY = (cy + ry).toInt().coerceIn(0, h - 1)

        for (y in minY..maxY) {
            val dy = (y - cy) / ry
            val dy2 = dy * dy
            val rowOffset = y * w
            for (x in minX..maxX) {
                val src = srcPixels[rowOffset + x]
                val srcAlpha = (src ushr 24) and 0xFF
                if (srcAlpha == 0) continue
                val dx = (x - cx) / rx
                val distSq = dx * dx + dy2
                if (distSq >= 1.0f) continue
                val dist = kotlin.math.sqrt(distSq)
                val featherFactor = if (dist <= 0.60f) {
                    1.0f
                } else {
                    val t = ((1.0f - dist) / 0.40f).coerceIn(0f, 1f)
                    t * t * (3f - 2f * t)
                }
                val newAlpha = (srcAlpha * featherFactor).toInt().coerceIn(0, 255)
                if (newAlpha > 0) {
                    outPixels[rowOffset + x] = (newAlpha shl 24) or (src and 0x00FFFFFF)
                }
            }
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(outPixels, 0, w, 0, 0, w, h)
        return bmp
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

        val minBrightness = if (isWhiteFurSubject) 245 else 232
        val maxChannelDiff = if (isWhiteFurSubject) 9 else 20

        fun isStudioWhiteBackdrop(pixel: Int, x: Int, y: Int): Boolean {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val minC = min(r, min(g, b))
            val maxC = max(r, max(g, b))
            val diff = maxC - minC
            if (minC >= minBrightness && diff <= maxChannelDiff) {
                return true
            }
            // Also remove soft neutral grey studio floor drop-shadows at the bottom & corners
            if (y > (h * 0.84f).toInt() && diff <= 14) {
                val isOutsideFeet = x < (w * 0.25f).toInt() ||
                    x > (w * 0.75f).toInt() ||
                    y > (h * 0.92f).toInt() ||
                    x in (w * 0.46f).toInt()..(w * 0.54f).toInt()
                if (isOutsideFeet && minC >= 188) {
                    return true
                }
            }
            return false
        }

        fun isFlatInteriorWhitePocket(x: Int, y: Int): Boolean {
            if (x <= 1 || x >= w - 2 || y <= 1 || y >= h - 2) return false
            val c = pixels[y * w + x]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val minC = min(r, min(g, b))
            val maxC = max(r, max(g, b))
            if (minC < 246 || (maxC - minC) > 6) return false
            // Verify 3x3 neighborhood is flat untextured studio backdrop (protects white fur!)
            var minLum = 255
            var maxLum = 0
            for (dy in -1..1) {
                val row = (y + dy) * w
                for (dx in -1..1) {
                    val p = pixels[row + x + dx]
                    val lum = ((p shr 16) and 0xFF)
                    if (lum < minLum) minLum = lum
                    if (lum > maxLum) maxLum = lum
                }
            }
            return (maxLum - minLum) <= 4
        }

        fun enqueueIfBackdrop(x: Int, y: Int) {
            if (x < 0 || x >= w || y < 0 || y >= h) return
            val idx = y * w + x
            if (visited[idx]) return
            if (isStudioWhiteBackdrop(pixels[idx], x, y)) {
                visited[idx] = true
                queue[tail++] = idx
            }
        }

        // 1. Seed from all 4 outer borders
        for (x in 0 until w) {
            enqueueIfBackdrop(x, 0)
            enqueueIfBackdrop(x, h - 1)
        }
        for (y in 0 until h) {
            enqueueIfBackdrop(0, y)
            enqueueIfBackdrop(w - 1, y)
        }

        // 2. Seed from enclosed white pockets:
        //    (a) Under the headphone headband arch (between headband and top of head/ears)
        val archMinX = (w * 0.25f).toInt()
        val archMaxX = (w * 0.75f).toInt()
        val archMinY = (h * 0.08f).toInt()
        val archMaxY = (h * 0.24f).toInt()
        for (y in archMinY..archMaxY step 2) {
            for (x in archMinX..archMaxX step 2) {
                if (isFlatInteriorWhitePocket(x, y)) {
                    enqueueIfBackdrop(x, y)
                }
            }
        }

        //    (b) Enclosed gap between the two legs near the bottom
        val legGapMinX = (w * 0.44f).toInt()
        val legGapMaxX = (w * 0.56f).toInt()
        val legGapMinY = (h * 0.76f).toInt()
        val legGapMaxY = (h * 0.96f).toInt()
        for (y in legGapMinY..legGapMaxY step 2) {
            for (x in legGapMinX..legGapMaxX step 2) {
                if (isFlatInteriorWhitePocket(x, y)) {
                    enqueueIfBackdrop(x, y)
                }
            }
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

        // 3. Soft anti-aliased alpha feathering along the plush fur silhouette
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
                            3, 4 -> 75
                            2 -> 150
                            else -> 215
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

private data class PlushFaceRig(
    val leftEyeX: Float,
    val leftEyeY: Float,
    val rightEyeX: Float,
    val rightEyeY: Float,
    val eyeRadiusRatio: Float,
    val mouthX: Float,
    val mouthY: Float,
    val leftCheekX: Float,
    val rightCheekX: Float,
    val cheekY: Float,
    val holdsCassette: Boolean = false
)

private fun getFaceRigForBreed(breed: CompanionBreed): PlushFaceRig = when (breed.drawableResId) {
    R.drawable.img_mascot_golden_lion -> PlushFaceRig(
        leftEyeX = 0.362f, leftEyeY = 0.362f,
        rightEyeX = 0.556f, rightEyeY = 0.362f,
        eyeRadiusRatio = 0.056f,
        mouthX = 0.446f, mouthY = 0.462f,
        leftCheekX = 0.298f, rightCheekX = 0.618f, cheekY = 0.415f
    )
    R.drawable.img_mascot_white_bear -> PlushFaceRig(
        leftEyeX = 0.360f, leftEyeY = 0.320f,
        rightEyeX = 0.542f, rightEyeY = 0.318f,
        eyeRadiusRatio = 0.052f,
        mouthX = 0.440f, mouthY = 0.408f,
        leftCheekX = 0.298f, rightCheekX = 0.605f, cheekY = 0.370f
    )
    R.drawable.img_mascot_yellow_chick -> PlushFaceRig(
        leftEyeX = 0.405f, leftEyeY = 0.355f,
        rightEyeX = 0.635f, rightEyeY = 0.355f,
        eyeRadiusRatio = 0.058f,
        mouthX = 0.525f, mouthY = 0.415f,
        leftCheekX = 0.355f, rightCheekX = 0.675f, cheekY = 0.418f
    )
    R.drawable.img_mascot_studio_bunny -> PlushFaceRig(
        leftEyeX = 0.355f, leftEyeY = 0.415f,
        rightEyeX = 0.560f, rightEyeY = 0.415f,
        eyeRadiusRatio = 0.056f,
        mouthX = 0.448f, mouthY = 0.505f,
        leftCheekX = 0.292f, rightCheekX = 0.618f, cheekY = 0.475f
    )
    R.drawable.img_mascot_honey_teddy -> PlushFaceRig(
        leftEyeX = 0.375f, leftEyeY = 0.352f,
        rightEyeX = 0.558f, rightEyeY = 0.352f,
        eyeRadiusRatio = 0.049f,
        mouthX = 0.455f, mouthY = 0.452f,
        leftCheekX = 0.308f, rightCheekX = 0.622f, cheekY = 0.405f
    )
    R.drawable.img_mascot_pink_teddy -> PlushFaceRig(
        leftEyeX = 0.452f, leftEyeY = 0.355f,
        rightEyeX = 0.640f, rightEyeY = 0.355f,
        eyeRadiusRatio = 0.050f,
        mouthX = 0.555f, mouthY = 0.445f,
        leftCheekX = 0.392f, rightCheekX = 0.695f, cheekY = 0.405f
    )
    R.drawable.img_mascot_sky_puppy -> PlushFaceRig(
        leftEyeX = 0.392f, leftEyeY = 0.292f,
        rightEyeX = 0.565f, rightEyeY = 0.298f,
        eyeRadiusRatio = 0.048f,
        mouthX = 0.455f, mouthY = 0.395f,
        leftCheekX = 0.328f, rightCheekX = 0.622f, cheekY = 0.350f
    )
    R.drawable.img_mascot_panda_dj -> PlushFaceRig(
        leftEyeX = 0.455f, leftEyeY = 0.378f,
        rightEyeX = 0.635f, rightEyeY = 0.378f,
        eyeRadiusRatio = 0.048f,
        mouthX = 0.552f, mouthY = 0.468f,
        leftCheekX = 0.395f, rightCheekX = 0.695f, cheekY = 0.448f
    )
    R.drawable.img_mascot_mint_teddy -> PlushFaceRig(
        leftEyeX = 0.472f, leftEyeY = 0.335f,
        rightEyeX = 0.672f, rightEyeY = 0.335f,
        eyeRadiusRatio = 0.048f,
        mouthX = 0.588f, mouthY = 0.432f,
        leftCheekX = 0.412f, rightCheekX = 0.725f, cheekY = 0.395f
    )
    R.drawable.img_mascot_royal_penguin -> PlushFaceRig(
        leftEyeX = 0.372f, leftEyeY = 0.372f,
        rightEyeX = 0.592f, rightEyeY = 0.372f,
        eyeRadiusRatio = 0.052f,
        mouthX = 0.478f, mouthY = 0.422f,
        leftCheekX = 0.318f, rightCheekX = 0.642f, cheekY = 0.432f
    )
    R.drawable.img_mascot_lavender_teddy -> PlushFaceRig(
        leftEyeX = 0.405f, leftEyeY = 0.318f,
        rightEyeX = 0.595f, rightEyeY = 0.318f,
        eyeRadiusRatio = 0.052f,
        mouthX = 0.498f, mouthY = 0.392f,
        leftCheekX = 0.335f, rightCheekX = 0.660f, cheekY = 0.365f,
        holdsCassette = true
    )
    R.drawable.img_mascot_lavender_puppy -> PlushFaceRig(
        leftEyeX = 0.402f, leftEyeY = 0.340f,
        rightEyeX = 0.602f, rightEyeY = 0.340f,
        eyeRadiusRatio = 0.056f,
        mouthX = 0.498f, mouthY = 0.418f,
        leftCheekX = 0.335f, rightCheekX = 0.665f, cheekY = 0.395f,
        holdsCassette = true
    )
    else -> PlushFaceRig(
        leftEyeX = 0.380f, leftEyeY = 0.355f,
        rightEyeX = 0.570f, rightEyeY = 0.355f,
        eyeRadiusRatio = 0.052f,
        mouthX = 0.475f, mouthY = 0.445f,
        leftCheekX = 0.315f, rightCheekX = 0.635f, cheekY = 0.410f
    )
}

private const val PLUSH_MESH_W = 16
private const val PLUSH_MESH_H = 16
private const val PLUSH_MESH_VERT_COUNT = (PLUSH_MESH_W + 1) * (PLUSH_MESH_H + 1) * 2

/**
 * Real-Time GPU Skeletal-Mesh Deforming & High-Jumping ("Kudne Wala") 3D Plush Character Engine:
 * - Uses true gravity-parabola jumping physics:
 *   1) Ground Crouch & Squash anticipation on the stage
 *   2) High parabolic launch into the air (`internalJumpY` up to 22% of character height!)
 *   3) Full-paw rigid-shoulder rotation (`±50°` high wave & bhangra pump with zero tip pinning!)
 *   4) Alternating mid-air leg & foot kicks + knee bend
 *   5) Floppy ears & bass-thumping headphone earcups with secondary spring physics
 *   6) Per-breed exact `PlushFaceRig` with smooth progressive fur eyelid blinks, happy `^ ^` jump eyes,
 *      moving glossy star catchlights, beat-synced jaw/mouth deformation, and spinning cassette reels!
 */
@Composable
fun DeformingJumping3DPlushMascot(
    breed: CompanionBreed,
    parts: ArticulatedMascotParts,
    animTick: Float,
    walkStridePhase: Float,
    walkDir: Float,
    isMusicPlaying: Boolean,
    isActivelyWalking: Boolean,
    happyJumpTicks: Int,
    landingSquashTicks: Int,
    danceMode: MascotDanceMode,
    auraColor: Color,
    drawInternalFloorShadow: Boolean = false,
    modifier: Modifier = Modifier
) {
    val meshVerts = remember { FloatArray(PLUSH_MESH_VERT_COUNT) }
    val rimMeshVerts = remember { FloatArray(PLUSH_MESH_VERT_COUNT) }
    val faceRig = remember(breed) { getFaceRigForBreed(breed) }

    val mainPaint = remember(breed) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
            val tintInt = when (breed) {
                CompanionBreed.CYBER_NEON_LION -> 0x3800E5FF
                CompanionBreed.DISCO_PARTY_BUNNY -> 0x30FF4081
                CompanionBreed.BHANGRA_KING_PANDA -> 0x2EFFB300
                CompanionBreed.HIPHOP_GOLD_PUPPY -> 0x2E00E676
                CompanionBreed.ROCKSTAR_CHICK_DJ -> 0x33FF1744
                CompanionBreed.ROYAL_GOLD_TEDDY -> 0x30FFD700
                CompanionBreed.COSMIC_ASTRO_PENGUIN -> 0x3800B0FF
                CompanionBreed.VELVET_BOOMBOX_BEAR -> 0x33B388FF
                else -> 0
            }
            if (tintInt != 0) {
                colorFilter = android.graphics.PorterDuffColorFilter(
                    tintInt,
                    android.graphics.PorterDuff.Mode.SRC_ATOP
                )
            }
        }
    }

    val rimPaint = remember(auraColor) {
        val c = ((0x50 shl 24) or
            (((auraColor.red * 255).toInt() and 0xFF) shl 16) or
            (((auraColor.green * 255).toInt() and 0xFF) shl 8) or
            ((auraColor.blue * 255).toInt() and 0xFF))
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
            alpha = 140
            colorFilter = android.graphics.PorterDuffColorFilter(
                c,
                android.graphics.PorterDuff.Mode.SRC_ATOP
            )
        }
    }

    Canvas(modifier = modifier) {
        val rawBmp = parts.rawCutoutBitmap
        if (rawBmp.isRecycled) return@Canvas

        val canvasW = size.width
        val canvasH = size.height
        val bmpAspect = rawBmp.width.toFloat() / rawBmp.height.coerceAtLeast(1).toFloat()

        // Leave generous vertical headroom at the top (24%) so high "Kudne Wala" jumps never clip!
        val drawH = canvasH * 0.76f
        val drawW = (drawH * bmpAspect).coerceAtMost(canvasW * 0.84f)
        val baseLeft = (canvasW - drawW) * 0.5f
        val baseTop = canvasH - drawH - canvasH * 0.05f

        val t = animTick
        val energyMult = if (isMusicPlaying || happyJumpTicks > 0) 1.30f else 1.05f

        // 1. True Gravity-Parabola "Kudne Wala" Jump Cycle (Crouch Squat -> High Air Launch -> Rebound)
        val jumpSpeed = when (danceMode) {
            MascotDanceMode.TRAMPOLINE_ACROBAT -> 3.8f
            MascotDanceMode.BHANGRA_GROOVE -> 3.6f
            MascotDanceMode.DJ_BOUNCE -> 3.4f
            else -> if (isMusicPlaying) 3.4f else 2.9f
        }
        val jumpCycle = t * jumpSpeed
        val normPhase = ((jumpCycle / 6.2831855f) % 1f + 1f) % 1f

        // 0.00..0.24 = Trampoline Crouch & Squash on the stage
        // 0.24..0.86 = High Parabolic Jump in the air!
        // 0.86..1.00 = Landing Impact & Compression
        val airborneFactor: Float
        val crouchFactor: Float
        if (normPhase in 0.24f..0.86f) {
            val airU = (normPhase - 0.24f) / 0.62f
            airborneFactor = sin(airU * 3.1415927f).coerceIn(0f, 1f)
            crouchFactor = 0f
        } else {
            airborneFactor = 0f
            val groundU = if (normPhase < 0.24f) {
                (normPhase + 0.14f) / 0.38f
            } else {
                (normPhase - 0.86f) / 0.38f
            }
            crouchFactor = sin(groundU * 3.1415927f).coerceIn(0f, 1f)
        }

        val extraLandingSquash = if (landingSquashTicks > 0) sin(landingSquashTicks * 0.5f) * 0.15f else 0f

        // Strong Cartoon Squash & Stretch so the jump feels 100% physical and bouncy!
        val squashScaleX = 1.0f - airborneFactor * 0.11f + crouchFactor * 0.18f + extraLandingSquash
        val stretchScaleY = 1.0f + airborneFactor * 0.15f - crouchFactor * 0.18f - extraLandingSquash

        // High internal jump lift inside the canvas (launches up to 22% of character height!)
        val internalJumpY = -airborneFactor * drawH * (0.20f * energyMult) + crouchFactor * drawH * 0.045f

        // Optional floor shadow & bounce shockwave for Gallery / Me Section / Center Stage
        if (drawInternalFloorShadow) {
            val shadowScale = (1f - airborneFactor * 0.48f + crouchFactor * 0.18f).coerceIn(0.42f, 1.22f)
            val shadowW = drawW * 0.68f * shadowScale
            val shadowH = drawH * 0.095f * shadowScale
            val floorYCenter = canvasH * 0.91f
            drawOval(
                color = Color.Black.copy(alpha = (0.48f * (1f - airborneFactor * 0.45f)).coerceIn(0.15f, 0.55f)),
                topLeft = Offset((canvasW - shadowW) * 0.5f, floorYCenter - shadowH * 0.5f),
                size = Size(shadowW, shadowH)
            )
            if (crouchFactor > 0.15f) {
                val ringW = drawW * (0.72f + crouchFactor * 0.26f)
                val ringH = drawH * (0.11f + crouchFactor * 0.04f)
                drawOval(
                    color = auraColor.copy(alpha = (crouchFactor * 0.75f).coerceIn(0f, 0.8f)),
                    topLeft = Offset((canvasW - ringW) * 0.5f, floorYCenter - ringH * 0.5f),
                    size = Size(ringW, ringH),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        // 2. Head Bobble, 3D Perspective Turn, Nod & Floppy Ear/Headphone Spring Bounce
        val headTiltRad = sin(t * 2.7f) * (0.20f * energyMult)
        val cosHead = cos(headTiltRad)
        val sinHead = sin(headTiltRad)
        val headTurn3D = (sin(t * 1.9f) * 0.058f * energyMult) + (if (isActivelyWalking) walkDir * 0.022f else 0f)
        val headNodY = (-airborneFactor * 0.035f + crouchFactor * 0.048f) * drawH * energyMult
        val earcupPulse = abs(sin(t * 5.8f)) * drawW * (if (isMusicPlaying) 0.042f else 0.024f)

        // 3. Left & Right Arm Full-Paw High Wave / Bhangra Cheer Pump (±50 degrees!)
        val leftArmWaveAngle = if (faceRig.holdsCassette) {
            sin(t * 4.4f) * 0.28f - airborneFactor * 0.20f
        } else {
            sin(t * 4.5f) * 0.48f - airborneFactor * 0.52f + crouchFactor * 0.32f
        }
        val rightArmWaveAngle = if (faceRig.holdsCassette) {
            -sin(t * 4.4f) * 0.28f + airborneFactor * 0.20f
        } else {
            -sin(t * 4.5f + 0.65f) * 0.48f + airborneFactor * 0.52f - crouchFactor * 0.32f
        }

        // 4. Belly Hip-Hop Groove Sway & Breathing
        val hipSwayX = sin(t * 3.4f) * drawW * (0.062f * energyMult)
        val bellyBreath = sin(t * 3.1f) * 0.048f + crouchFactor * 0.06f

        // 5. Left & Right Leg High Jumping Kick & Alternating Mid-Air Stride
        val legCycle = if (isActivelyWalking) walkStridePhase * 1.45f else t * 4.6f
        val leftLegKickWave = max(0f, sin(legCycle))
        val rightLegKickWave = max(0f, -sin(legCycle))
        val leftLegLiftY = -(leftLegKickWave * 0.165f + airborneFactor * 0.085f) * drawH * energyMult
        val rightLegLiftY = -(rightLegKickWave * 0.165f + airborneFactor * 0.085f) * drawH * energyMult
        val leftLegKickX = -(leftLegKickWave * 0.095f + airborneFactor * 0.055f) * drawW
        val rightLegKickX = (rightLegKickWave * 0.095f + airborneFactor * 0.055f) * drawW

        // 6. Beat-Synced Jaw / Mouth Drop in the Mesh
        val mouthOpenFactor = if (isMusicPlaying || happyJumpTicks > 0) {
            (abs(sin(t * 6.2f)) * 0.88f + 0.12f)
        } else {
            (airborneFactor * 0.72f + abs(sin(t * 3.4f)) * 0.25f).coerceIn(0f, 0.85f)
        }

        val rimShiftX = if (isActivelyWalking) -walkDir * drawW * 0.028f else sin(t * 1.9f) * drawW * 0.025f
        val rimShiftY = -drawH * 0.016f

        var vIdx = 0
        for (gy in 0..PLUSH_MESH_H) {
            val ny = gy.toFloat() / PLUSH_MESH_H.toFloat()
            for (gx in 0..PLUSH_MESH_W) {
                val nx = gx.toFloat() / PLUSH_MESH_W.toFloat()

                // Base squash & stretch anchored at the feet (ny = 0.92f)
                var px = baseLeft + (0.5f + (nx - 0.5f) * squashScaleX) * drawW
                var py = baseTop + (0.92f + (ny - 0.92f) * stretchScaleY) * drawH + internalJumpY

                // --- A. HEAD, EARS, MANE, JAW & HEADPHONES DEFORMATION (ny < 0.52f) ---
                if (ny < 0.52f) {
                    val headWeight = ((0.52f - ny) / 0.16f).coerceIn(0f, 1f)
                    val smoothHeadW = headWeight * headWeight * (3f - 2f * headWeight)

                    val neckX = baseLeft + 0.50f * drawW
                    val neckY = baseTop + 0.47f * drawH + internalJumpY

                    val dx = px - neckX
                    val dy = py - neckY
                    val rotX = dx * cosHead - dy * sinHead
                    val rotY = dx * sinHead + dy * cosHead

                    // 3D foreshortening when turning head left/right
                    val perspectiveScale = 1f + (nx - 0.5f) * (headTurn3D * 4.8f)
                    val targetX = neckX + rotX * perspectiveScale + headTurn3D * drawW
                    val targetY = neckY + rotY + headNodY

                    px += (targetX - px) * smoothHeadW
                    py += (targetY - py) * smoothHeadW

                    // Secondary floppy spring bounce for ears / top crown / bunny ears (ny < 0.28f)
                    if (ny < 0.28f) {
                        val earWeight = ((0.28f - ny) / 0.28f).coerceIn(0f, 1f) * (abs(nx - 0.5f) * 2.1f).coerceIn(0.3f, 1f)
                        py += sin(jumpCycle - 0.85f) * drawH * 0.056f * earWeight * energyMult
                        px += cos(jumpCycle - 0.65f) * drawW * 0.034f * (if (nx < 0.5f) -1f else 1f) * earWeight
                    }

                    // Bass-reactive DJ headphone earcup thump (left & right sides of head)
                    if (ny in 0.16f..0.45f) {
                        if (nx < 0.29f) {
                            val cupW = ((0.29f - nx) / 0.22f).coerceIn(0f, 1f)
                            px -= earcupPulse * cupW
                        } else if (nx > 0.71f) {
                            val cupW = ((nx - 0.71f) / 0.22f).coerceIn(0f, 1f)
                            px += earcupPulse * cupW
                        }
                    }

                    // Physical Jaw / Lower Mouth Drop on the Beat
                    val jawDy = ny - faceRig.mouthY
                    val jawDx = abs(nx - faceRig.mouthX)
                    if (jawDy in -0.01f..0.09f && jawDx < 0.14f) {
                        val jawW = (1f - jawDx / 0.14f) * (1f - abs(jawDy - 0.035f) / 0.055f).coerceIn(0f, 1f)
                        py += mouthOpenFactor * drawH * 0.034f * jawW
                    }
                }

                // --- B. LEFT ARM / PAW FULL RIGID-BODY HIGH WAVE & PUMP (nx < 0.42f, ny in 0.34f..0.76f) ---
                if (nx < 0.42f && ny in 0.34f..0.76f) {
                    val xW = ((0.42f - nx) / 0.14f).coerceIn(0f, 1f)
                    val smoothXW = xW * xW * (3f - 2f * xW)
                    // Plateau weight = 1.0 across the entire paw (0.40..0.70) so paw tips NEVER stay pinned!
                    val yW = when {
                        ny < 0.40f -> ((ny - 0.34f) / 0.06f).coerceIn(0f, 1f)
                        ny > 0.70f -> ((0.76f - ny) / 0.06f).coerceIn(0f, 1f)
                        else -> 1.0f
                    }
                    val armW = (smoothXW * yW).coerceIn(0f, 1f)
                    if (armW > 0f) {
                        val shoulderX = baseLeft + 0.35f * drawW
                        val shoulderY = baseTop + 0.50f * drawH + internalJumpY
                        val ang = leftArmWaveAngle * armW
                        val cA = cos(ang)
                        val sA = sin(ang)
                        val dx = px - shoulderX
                        val dy = py - shoulderY
                        px = shoulderX + (dx * cA - dy * sA)
                        py = shoulderY + (dx * sA + dy * cA) - (airborneFactor * 0.048f + abs(sin(t * 4.5f)) * 0.036f) * drawH * armW
                    }
                }

                // --- C. RIGHT ARM / PAW FULL RIGID-BODY HIGH WAVE & PUMP (nx > 0.58f, ny in 0.34f..0.76f) ---
                if (nx > 0.58f && ny in 0.34f..0.76f) {
                    val xW = ((nx - 0.58f) / 0.14f).coerceIn(0f, 1f)
                    val smoothXW = xW * xW * (3f - 2f * xW)
                    val yW = when {
                        ny < 0.40f -> ((ny - 0.34f) / 0.06f).coerceIn(0f, 1f)
                        ny > 0.70f -> ((0.76f - ny) / 0.06f).coerceIn(0f, 1f)
                        else -> 1.0f
                    }
                    val armW = (smoothXW * yW).coerceIn(0f, 1f)
                    if (armW > 0f) {
                        val shoulderX = baseLeft + 0.65f * drawW
                        val shoulderY = baseTop + 0.50f * drawH + internalJumpY
                        val ang = rightArmWaveAngle * armW
                        val cA = cos(ang)
                        val sA = sin(ang)
                        val dx = px - shoulderX
                        val dy = py - shoulderY
                        px = shoulderX + (dx * cA - dy * sA)
                        py = shoulderY + (dx * sA + dy * cA) - (airborneFactor * 0.048f + abs(cos(t * 4.5f)) * 0.036f) * drawH * armW
                    }
                }

                // --- D. BELLY GROOVE SWAY & PLUSH BREATHING (ny in 0.44f..0.78f) ---
                if (ny in 0.44f..0.78f) {
                    val torsoW = sin(((ny - 0.44f) / 0.34f).coerceIn(0f, 1f) * 3.1415927f)
                    px += hipSwayX * torsoW + (nx - 0.5f) * bellyBreath * drawW * torsoW
                }

                // --- E. LEFT & RIGHT LEGS HIGH JUMPING KICK & RUNNING STRIDE (ny > 0.66f) ---
                if (ny > 0.66f) {
                    val legW = ((ny - 0.66f) / 0.22f).coerceIn(0f, 1f)
                    val smoothLegW = legW * legW * (3f - 2f * legW)
                    if (nx < 0.50f) {
                        val sideW = ((0.50f - nx) / 0.12f).coerceIn(0f, 1f)
                        val wFactor = smoothLegW * sideW
                        px += leftLegKickX * wFactor
                        py += leftLegLiftY * wFactor
                    } else {
                        val sideW = ((nx - 0.50f) / 0.12f).coerceIn(0f, 1f)
                        val wFactor = smoothLegW * sideW
                        px += rightLegKickX * wFactor
                        py += rightLegLiftY * wFactor
                    }

                    // Playful Wagging Tail on lower right back (nx > 0.66f)
                    if (nx > 0.66f && ny < 0.90f) {
                        val tailW = ((nx - 0.66f) / 0.22f).coerceIn(0f, 1f)
                        px += sin(t * 9.5f) * drawW * 0.052f * tailW
                        py += cos(t * 9.5f) * drawH * 0.026f * tailW
                    }
                }

                meshVerts[vIdx] = px
                meshVerts[vIdx + 1] = py
                rimMeshVerts[vIdx] = px + rimShiftX
                rimMeshVerts[vIdx + 1] = py + rimShiftY
                vIdx += 2
            }
        }

        // Draw 4D Rim-Glow Depth Mesh + Main Deformed 3D Plush Character Mesh
        drawContext.canvas.nativeCanvas.apply {
            drawBitmapMesh(
                rawBmp,
                PLUSH_MESH_W,
                PLUSH_MESH_H,
                rimMeshVerts,
                0,
                null,
                0,
                rimPaint
            )
            drawBitmapMesh(
                rawBmp,
                PLUSH_MESH_W,
                PLUSH_MESH_H,
                meshVerts,
                0,
                null,
                0,
                mainPaint
            )
        }

        // =========================================================================
        // LIVE ANIMATED FACIAL EXPRESSIONS ON DEFORMED HEAD (Exact Per-Breed Eye
        // Sockets, Smooth Eyelid Blinks, Happy ^ ^ Jump Eyes, Gaze Catchlights,
        // Rosy Cheek Blush, Singing Mouth & Spinning Cassette Reels!)
        // =========================================================================
        fun mapFacePoint(nx: Float, ny: Float): Offset {
            val sx = baseLeft + (0.5f + (nx - 0.5f) * squashScaleX) * drawW
            val sy = baseTop + (0.92f + (ny - 0.92f) * stretchScaleY) * drawH + internalJumpY
            val neckX = baseLeft + 0.50f * drawW
            val neckY = baseTop + 0.47f * drawH + internalJumpY
            val dx = sx - neckX
            val dy = sy - neckY
            val rotX = dx * cosHead - dy * sinHead
            val rotY = dx * sinHead + dy * cosHead
            val perspectiveScale = 1f + (nx - 0.5f) * (headTurn3D * 4.8f)
            return Offset(
                x = neckX + rotX * perspectiveScale + headTurn3D * drawW,
                y = neckY + rotY + headNodY
            )
        }

        val leftEyeCenter = mapFacePoint(faceRig.leftEyeX, faceRig.leftEyeY)
        val rightEyeCenter = mapFacePoint(faceRig.rightEyeX, faceRig.rightEyeY)
        val leftCheekCenter = mapFacePoint(faceRig.leftCheekX, faceRig.cheekY)
        val rightCheekCenter = mapFacePoint(faceRig.rightCheekX, faceRig.cheekY)
        val mouthCenter = mapFacePoint(faceRig.mouthX, faceRig.mouthY)

        // 1. Rosy Kawaii Cheek Blush Pulse
        val blushAlpha = (0.34f + airborneFactor * 0.28f).coerceIn(0.24f, 0.62f)
        drawOval(
            color = Color(0xFFFF4081).copy(alpha = blushAlpha),
            topLeft = Offset(leftCheekCenter.x - drawW * 0.052f, leftCheekCenter.y - drawH * 0.022f),
            size = Size(drawW * 0.104f, drawH * 0.044f)
        )
        drawOval(
            color = Color(0xFFFF4081).copy(alpha = blushAlpha),
            topLeft = Offset(rightCheekCenter.x - drawW * 0.052f, rightCheekCenter.y - drawH * 0.022f),
            size = Size(drawW * 0.104f, drawH * 0.044f)
        )

        // 2. Smooth Progressive Eyelid Blink + Happy ^ ^ Jump Eyes at Peak of Jump!
        val blinkPhase = (t * 1.55f) % 5.2f
        val isBlinkingNow = blinkPhase > 4.65f || airborneFactor > 0.82f || happyJumpTicks > 24
        val eyeRadius = drawW * faceRig.eyeRadiusRatio

        if (isBlinkingNow) {
            // Cover the static eye socket with plush fur eyelid
            drawCircle(
                color = breed.primaryFur,
                radius = eyeRadius * 1.12f,
                center = leftEyeCenter
            )
            drawCircle(
                color = breed.primaryFur,
                radius = eyeRadius * 1.12f,
                center = rightEyeCenter
            )
            // Happy ^ ^ curved anime plush eyes when jumping high!
            drawArc(
                color = Color(0xFF1E1410),
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(leftEyeCenter.x - eyeRadius * 0.95f, leftEyeCenter.y - eyeRadius * 0.65f),
                size = Size(eyeRadius * 1.9f, eyeRadius * 1.35f),
                style = Stroke(width = (drawW * 0.022f).coerceAtLeast(2.4f), cap = StrokeCap.Round)
            )
            drawArc(
                color = Color(0xFF1E1410),
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(rightEyeCenter.x - eyeRadius * 0.95f, rightEyeCenter.y - eyeRadius * 0.65f),
                size = Size(eyeRadius * 1.9f, eyeRadius * 1.35f),
                style = Stroke(width = (drawW * 0.022f).coerceAtLeast(2.4f), cap = StrokeCap.Round)
            )
        } else {
            val gazeX = sin(t * 2.5f) * eyeRadius * 0.34f
            val gazeY = (-airborneFactor * 0.28f + crouchFactor * 0.20f) * eyeRadius
            // Large glossy white star catchlight moving inside the eye
            drawCircle(
                color = Color.White.copy(alpha = 0.95f),
                radius = eyeRadius * 0.38f,
                center = Offset(leftEyeCenter.x - eyeRadius * 0.22f + gazeX, leftEyeCenter.y - eyeRadius * 0.24f + gazeY)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.95f),
                radius = eyeRadius * 0.38f,
                center = Offset(rightEyeCenter.x - eyeRadius * 0.22f + gazeX, rightEyeCenter.y - eyeRadius * 0.24f + gazeY)
            )
            // Secondary neon beat reflection glint in lower-right of iris
            drawCircle(
                color = breed.headphoneAccent.copy(alpha = 0.88f),
                radius = eyeRadius * 0.22f,
                center = Offset(leftEyeCenter.x + eyeRadius * 0.28f + gazeX, leftEyeCenter.y + eyeRadius * 0.26f + gazeY)
            )
            drawCircle(
                color = breed.headphoneAccent.copy(alpha = 0.88f),
                radius = eyeRadius * 0.22f,
                center = Offset(rightEyeCenter.x + eyeRadius * 0.28f + gazeX, rightEyeCenter.y + eyeRadius * 0.26f + gazeY)
            )
        }

        // 3. Beat-Reactive Singing / Cheering Mouth & Tongue Pulse
        if (mouthOpenFactor > 0.22f) {
            val mw = drawW * 0.068f * (0.82f + mouthOpenFactor * 0.35f)
            val mh = drawH * 0.038f * mouthOpenFactor
            drawOval(
                color = Color(0xFF3E0D1A).copy(alpha = 0.88f),
                topLeft = Offset(mouthCenter.x - mw * 0.5f, mouthCenter.y - mh * 0.45f),
                size = Size(mw, mh)
            )
            drawOval(
                color = Color(0xFFFF6E8E).copy(alpha = 0.92f),
                topLeft = Offset(mouthCenter.x - mw * 0.34f, mouthCenter.y - mh * 0.02f),
                size = Size(mw * 0.68f, mh * 0.52f)
            )
        }

        // 4. Spinning Cassette Tape Reels for Mixtape Teddy & Puppy-Tape DJ
        if (faceRig.holdsCassette) {
            val leftReel = Offset(
                baseLeft + 0.45f * drawW + hipSwayX * 0.7f,
                baseTop + 0.605f * drawH + internalJumpY
            )
            val rightReel = Offset(
                baseLeft + 0.55f * drawW + hipSwayX * 0.7f,
                baseTop + 0.595f * drawH + internalJumpY
            )
            val reelR = drawW * 0.024f
            val spinAngle = t * 6.5f
            listOf(leftReel, rightReel).forEach { center ->
                drawCircle(
                    color = breed.headphoneAccent,
                    radius = reelR,
                    center = center,
                    style = Stroke(width = 1.6.dp.toPx())
                )
                val spokeDx = cos(spinAngle) * reelR * 0.85f
                val spokeDy = sin(spinAngle) * reelR * 0.85f
                drawLine(
                    color = Color.White,
                    start = Offset(center.x - spokeDx, center.y - spokeDy),
                    end = Offset(center.x + spokeDx, center.y + spokeDy),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
        }
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
    onHold4sMenu: () -> Unit,
    hasLeadingCheckbox: Boolean = false
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val holdScope = rememberCoroutineScope()
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnHold2s by rememberUpdatedState(onHold2sPlay)
    val currentOnHold4s by rememberUpdatedState(onHold4sMenu)

    pointerInput(hasLeadingCheckbox) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Main)
            if (down.isConsumed) return@awaitEachGesture
            val rightEdgeExclusionPx = 64.dp.toPx()
            if (size.width > 140 && down.position.x >= size.width - rightEdgeExclusionPx) {
                return@awaitEachGesture
            }
            if (hasLeadingCheckbox && down.position.x <= 56.dp.toPx()) {
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
 * 3D/4D Plush Portrait Composable for the Me Section, Companion Gallery & Music Player Stage:
 * Uses transparent cutout + GPU `drawBitmapMesh` skeletal limb & high-jumping ("kudne wala") animation
 * at full 60fps DJ tempo so every Mascot Gudda jumps high off its stage, waves its paws, kicks its feet,
 * and blinks like a real living 3D character!
 */
@Composable
fun FluffyMascot3DStudioPortrait(
    breed: CompanionBreed,
    modifier: Modifier = Modifier,
    showStudioBackdrop: Boolean = true,
    animateReels: Boolean = true
) {
    val context = LocalContext.current
    var articulated by remember(breed.drawableResId) {
        mutableStateOf(MascotTransparentBitmapCache.getCachedArticulatedSync(breed.drawableResId))
    }

    LaunchedEffect(breed.drawableResId) {
        if (articulated == null) {
            articulated = MascotTransparentBitmapCache.getOrLoadArticulatedMascot(context, breed)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "mascot_card_3d_jump")
    val liveTick by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 62.831855f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mascot_card_live_tick"
    )
    val staggeredTick = liveTick + (breed.ordinal * 0.75f)

    Box(
        modifier = modifier.background(
            if (showStudioBackdrop) {
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D1526),
                        Color(0xFF162442),
                        breed.primaryFur.copy(alpha = 0.32f)
                    )
                )
            } else {
                Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
            }
        ),
        contentAlignment = Alignment.Center
    ) {
        if (showStudioBackdrop) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawOval(
                    color = breed.headphoneAccent.copy(alpha = 0.22f),
                    topLeft = Offset(w * 0.12f, h * 0.82f),
                    size = Size(w * 0.76f, h * 0.14f)
                )
                drawOval(
                    color = breed.headphoneAccent.copy(alpha = 0.70f),
                    topLeft = Offset(w * 0.12f, h * 0.82f),
                    size = Size(w * 0.76f, h * 0.14f),
                    style = Stroke(width = 1.8.dp.toPx())
                )
            }
        }

        val parts = articulated
        if (parts != null) {
            DeformingJumping3DPlushMascot(
                breed = breed,
                parts = parts,
                animTick = staggeredTick,
                walkStridePhase = staggeredTick * 1.45f,
                walkDir = 1f,
                isMusicPlaying = true,
                isActivelyWalking = true,
                happyJumpTicks = if (animateReels) 12 else 0,
                landingSquashTicks = 0,
                danceMode = MascotDanceMode.TRAMPOLINE_ACROBAT,
                auraColor = breed.headphoneAccent,
                drawInternalFloorShadow = true,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Interactive 100% Real Photorealistic 3D Plush Companion ("Gudda") Overlay with:
 * - True Stage-Separated "Kudne Wala" High Jumping: The 3D Turntable/Trampoline Stage stays planted
 *   on the floor (`posY`), while the 3D Plush Gudda launches HIGH into the air (`modeBounceY`) above
 *   the stage and rebounds with squash & stretch + shockwave rings!
 * - 6 Glowing Aura & Particle Effects (Neon DJ Waves, Floating Hearts 💖, Gold Stars 🌟, Fire Rings 🔥, Rainbow Halo 🌈, Cyber Sparks ⚡).
 * - Built-in Interactive Mascot Feature Studio Dock right on the Gudda.
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
    beatEnergy: Float = 0.45f,
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
    val mascotBoxHeightPx = with(density) { (mascotSizeDp + 44.dp).toPx() }
    val mascotSizePx = with(density) { mascotSizeDp.toPx() }
    val floorY = (screenHeightPx - mascotBoxHeightPx - with(density) { bottomClearanceDp.toPx() }).coerceAtLeast(60f)

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
    var walkStridePhase by remember { mutableFloatStateOf(0f) }
    var isActivelyWalking by remember { mutableStateOf(true) }
    var isDragging by remember { mutableStateOf(false) }
    var animTick by remember { mutableFloatStateOf(0f) }
    var happyJumpTicks by remember { mutableIntStateOf(0) }
    var flip360Ticks by remember { mutableIntStateOf(0) }
    var landingSquashTicks by remember { mutableIntStateOf(0) }
    var showMascotFeatureHub by remember { mutableStateOf(false) }

    // Intelligent DJ Brain Commentary State
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

    // Smooth Living 3D/4D Jumping ("Kudne Wala") & Patrol Physics Loop (~38fps GPU-accelerated):
    LaunchedEffect(
        isDragging,
        isMusicPlaying,
        danceMode,
        autoWalkEnabled,
        animSpeedMultiplier,
        floorY,
        screenWidthPx,
        mascotSizePx,
        showMascotFeatureHub
    ) {
        while (true) {
            delay(26L)
            val airborneNow = posY < floorY - 1.5f || abs(velY) > 0.8f
            val hasActiveStunt = happyJumpTicks > 0 || flip360Ticks > 0 || landingSquashTicks > 0 || airborneNow || isDragging
            val speedFactor = animSpeedMultiplier.coerceIn(0.6f, 2.2f)
            animTick += if (isMusicPlaying || hasActiveStunt) 0.30f * speedFactor else 0.25f * speedFactor

            if (happyJumpTicks > 0) happyJumpTicks--
            if (flip360Ticks > 0) flip360Ticks--
            if (landingSquashTicks > 0) landingSquashTicks--

            if (!isDragging) {
                if (airborneNow) {
                    isActivelyWalking = true
                    walkStridePhase += 0.48f * speedFactor
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
                        landingSquashTicks = 12
                        if (abs(velY) > 3.6f) {
                            velY = -velY * 0.52f
                            velX *= 0.80f
                        } else {
                            velY = 0f
                            velX = 0f
                        }
                    }
                } else {
                    posY = floorY
                    velY = 0f
                    velX = 0f

                    val shouldPatrol = (autoWalkEnabled || danceMode == MascotDanceMode.PATROL_WALK) && !showMascotFeatureHub
                    if (shouldPatrol) {
                        isActivelyWalking = true
                        val baseWalkSpeed = when {
                            danceMode == MascotDanceMode.PATROL_WALK -> 2.55f
                            isMusicPlaying -> 1.95f
                            else -> 1.55f
                        }
                        val walkStep = baseWalkSpeed * speedFactor
                        posX += walkDir * walkStep
                        walkStridePhase += (if (isMusicPlaying) 0.52f else 0.42f) * speedFactor
                        val minX = 10f
                        val maxX = (screenWidthPx - mascotSizePx - 10f).coerceAtLeast(20f)
                        if (posX >= maxX) {
                            posX = maxX
                            walkDir = -1f
                            happyJumpTicks = 18
                        } else if (posX <= minX) {
                            posX = minX
                            walkDir = 1f
                            happyJumpTicks = 18
                        }
                    } else {
                        isActivelyWalking = false
                        walkStridePhase += 0.38f * speedFactor
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // =========================================================================
        // 1. 100% REAL JUMPING ("KUDNE WALA") SKELETAL-MESH 3D ANIMATED GUDDA
        //    Outer Box stays anchored at floor `posY` so the 3D Stage Platform remains
        //    on the ground while the Gudda itself jumps HIGH up into the air above it!
        // =========================================================================
        Box(
            modifier = Modifier
                .offset {
                    val t = animTick
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

                    val modeSwayX = when (activeStyle) {
                        MascotDanceMode.BHANGRA_GROOVE -> sin(t * 1.9f) * 14f
                        MascotDanceMode.DJ_BOUNCE -> sin(t * 1.7f) * 10f
                        MascotDanceMode.ZERO_G_FLOAT -> cos(t * 1.4f) * 12f
                        MascotDanceMode.HEART_DISCO -> sin(t * 2.1f) * 12f
                        else -> sin(t * 1.8f) * 9f
                    }

                    IntOffset(
                        (posX + modeSwayX).roundToInt(),
                        posY.roundToInt()
                    )
                }
                .size(width = mascotSizeDp + 28.dp, height = mascotSizeDp + 44.dp)
                .pointerInput(isMusicPlaying, breed) {
                    detectTapGestures(
                        onTap = {
                            velY = -20f
                            happyJumpTicks = 38
                            flip360Ticks = 28
                            showMascotFeatureHub = !showMascotFeatureHub
                        },
                        onDoubleTap = {
                            velY = -28f
                            happyJumpTicks = 50
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
            // A. 3D/4D DJ TRAMPOLINE STAGE (Planted on the floor while Gudda jumps off it!)
            // =========================================================================
            Canvas(modifier = Modifier.fillMaxSize()) {
                val t = animTick
                val w = size.width
                val h = size.height
                val pulse = (sin(t * 3.2f) + 1f) * 0.5f
                val primaryAura = auraEffect.primaryColor
                val secondaryAura = auraEffect.secondaryColor

                // Match the jump cycle so the stage shadow shrinks when Gudda is high in the air
                // and bursts with a shockwave ring the instant the Gudda lands on the stage!
                val jumpPhase = abs(sin(t * 3.4f))
                val shockwaveExpand = (1f - jumpPhase) * (w * 0.18f)
                drawOval(
                    color = Color(0xCC070D19),
                    topLeft = Offset(w * 0.10f, h * 0.85f),
                    size = Size(w * 0.80f, h * 0.12f)
                )
                drawOval(
                    brush = Brush.horizontalGradient(
                        listOf(primaryAura.copy(alpha = 0.88f), secondaryAura.copy(alpha = 0.95f), primaryAura.copy(alpha = 0.88f))
                    ),
                    topLeft = Offset(w * 0.10f, h * 0.85f),
                    size = Size(w * 0.80f, h * 0.12f),
                    style = Stroke(width = 2.6.dp.toPx())
                )
                // Expanding landing shockwave ring every time the Gudda bounces on the stage!
                drawOval(
                    color = primaryAura.copy(alpha = (1f - jumpPhase) * 0.75f),
                    topLeft = Offset(w * 0.10f - shockwaveExpand * 0.5f, h * 0.85f - shockwaveExpand * 0.15f),
                    size = Size(w * 0.80f + shockwaveExpand, h * 0.12f + shockwaveExpand * 0.30f),
                    style = Stroke(width = 2.0.dp.toPx())
                )

                // Dynamic 3D Left & Right Footstep Contact Shadows (shrink when jumping high!)
                val airShrink = (1f - jumpPhase * 0.55f).coerceIn(0.35f, 1f)
                drawOval(
                    color = Color.Black.copy(alpha = 0.52f * airShrink),
                    topLeft = Offset(w * (0.26f + (1f - airShrink) * 0.04f), h * 0.88f),
                    size = Size(w * 0.20f * airShrink, h * 0.048f * airShrink)
                )
                drawOval(
                    color = Color.Black.copy(alpha = 0.52f * airShrink),
                    topLeft = Offset(w * (0.54f + (1f - airShrink) * 0.04f), h * 0.88f),
                    size = Size(w * 0.20f * airShrink, h * 0.048f * airShrink)
                )

                // Live 9-Bar Beat-Reactive Equalizer inside the Stage Platform
                val barCount = 9
                val totalEqW = w * 0.56f
                val startEqX = w * 0.22f
                val barGap = totalEqW / barCount
                val beatGain = if (isMusicPlaying) (0.72f + beatEnergy.coerceIn(0f, 1f) * 0.65f) else 0.58f
                for (i in 0 until barCount) {
                    val eqAmp = ((abs(sin(t * 3.8f + i * 0.75f)) * 0.72f + 0.25f) * beatGain).coerceIn(0.22f, 1.0f)
                    val barH = (h * 0.095f) * eqAmp
                    val bx = startEqX + i * barGap + barGap * 0.25f
                    drawLine(
                        color = if (i % 2 == 0) primaryAura else secondaryAura,
                        start = Offset(bx, h * 0.93f),
                        end = Offset(bx, h * 0.93f - barH),
                        strokeWidth = 2.6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Left & Right DJ Headphone Sonic Waves & Floating 4D Particles
                val waveExpand = pulse * (w * 0.065f)
                drawArc(
                    color = primaryAura.copy(alpha = if (isMusicPlaying) 0.90f else 0.60f),
                    startAngle = 140f,
                    sweepAngle = 80f,
                    useCenter = false,
                    topLeft = Offset(w * 0.02f - waveExpand, h * 0.22f - jumpPhase * h * 0.10f),
                    size = Size(w * 0.22f, h * 0.26f),
                    style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = secondaryAura.copy(alpha = if (isMusicPlaying) 0.90f else 0.60f),
                    startAngle = -40f,
                    sweepAngle = 80f,
                    useCenter = false,
                    topLeft = Offset(w * 0.76f + waveExpand, h * 0.22f - jumpPhase * h * 0.10f),
                    size = Size(w * 0.22f, h * 0.26f),
                    style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                )

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
                        drawFloatingMusicNote(center = Offset(w * 0.09f, p1Y), size = w * 0.052f, color = primaryAura)
                        drawFloatingMusicNote(center = Offset(w * 0.91f, p2Y), size = w * 0.052f, color = secondaryAura)
                        drawSparkleStar(center = Offset(w * (0.5f + cos(t) * 0.32f), p3Y), radius = w * 0.046f, color = Color(0xFFFF4081))
                    }
                }
            }

            // =========================================================================
            // B. GPU SKELETAL-MESH DEFORMING & HIGH-JUMPING 3D PLUSH CHARACTER ("GUDDA")
            //    Launches upward (`translationY = modeBounceY`) relative to the floor stage!
            // =========================================================================
            val parts = articulatedParts
            if (parts != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 12.dp, bottom = 6.dp, start = 2.dp, end = 2.dp)
                        .graphicsLayer {
                            val t = animTick
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.90f)
                            cameraDistance = 14f * density.density

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

                            val jumpMult = if (isMusicPlaying || happyJumpTicks > 0) 1.32f else 1.0f
                            val celebrationHop = if (happyJumpTicks > 0) -abs(sin(t * 4.0f)) * 18f else 0f
                            val stageJumpY = when (activeStyle) {
                                MascotDanceMode.DJ_BOUNCE -> -abs(sin(t * 3.4f)) * 22f * jumpMult
                                MascotDanceMode.BHANGRA_GROOVE -> -(abs(sin(t * 3.6f)) * 26f + abs(cos(t * 7.2f)) * 7f) * jumpMult
                                MascotDanceMode.SPIN_3D -> -abs(sin(t * 3.2f)) * 20f * jumpMult
                                MascotDanceMode.ZERO_G_FLOAT -> (-18f - sin(t * 2.2f) * 14f) * jumpMult
                                MascotDanceMode.HEART_DISCO -> -abs(sin(t * 3.5f)) * 22f * jumpMult
                                MascotDanceMode.PATROL_WALK -> -abs(sin(walkStridePhase)) * 20f * jumpMult
                                MascotDanceMode.TRAMPOLINE_ACROBAT -> -abs(sin(t * 3.8f)) * 32f * jumpMult
                                MascotDanceMode.AUTO_MIX -> -abs(sin(t * 3.4f)) * 24f * jumpMult
                            }

                            translationY = stageJumpY + celebrationHop

                            val airborneNow = posY < floorY - 1.5f || abs(velY) > 0.8f
                            val cartwheelZ = if (flip360Ticks > 0) {
                                (1f - (flip360Ticks / 36f).coerceIn(0f, 1f)) * 360f
                            } else 0f

                            rotationZ = when {
                                flip360Ticks > 0 -> cartwheelZ
                                airborneNow && !isDragging -> (velX * 1.2f + sin(t * 2.4f) * 14f).coerceIn(-24f, 24f)
                                activeStyle == MascotDanceMode.BHANGRA_GROOVE -> sin(t * 3.6f) * 13f
                                activeStyle == MascotDanceMode.DJ_BOUNCE -> sin(t * 2.6f) * 10.5f
                                activeStyle == MascotDanceMode.HEART_DISCO -> sin(t * 2.8f) * 11.5f
                                activeStyle == MascotDanceMode.PATROL_WALK -> sin(walkStridePhase) * 10.5f * walkDir
                                activeStyle == MascotDanceMode.SPIN_3D -> sin(t * 2.9f) * 13f
                                else -> sin(t * 2.4f) * 9.5f
                            }
                            rotationY = when {
                                isActivelyWalking -> (walkDir * 13f + sin(t * 2.1f) * 7f).coerceIn(-19f, 19f)
                                activeStyle == MascotDanceMode.SPIN_3D -> (sin(t * 2.5f) * 18f).coerceIn(-20f, 20f)
                                else -> (sin(t * 1.9f) * 13f).coerceIn(-17f, 17f)
                            }
                            rotationX = (cos(t * 2.7f) * 7.5f).coerceIn(-10f, 10f)
                        }
                ) {
                    DeformingJumping3DPlushMascot(
                        breed = breed,
                        parts = parts,
                        animTick = animTick,
                        walkStridePhase = walkStridePhase,
                        walkDir = walkDir,
                        isMusicPlaying = isMusicPlaying,
                        isActivelyWalking = isActivelyWalking,
                        happyJumpTicks = happyJumpTicks,
                        landingSquashTicks = landingSquashTicks,
                        danceMode = danceMode,
                        auraColor = auraEffect.primaryColor,
                        drawInternalFloorShadow = false,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // =========================================================================
            // C. ROYAL CROWN ACCENT (Clean 3D-compatible)
            // =========================================================================
            if (breed.wearsCrown) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val t = animTick
                    val w = size.width
                    val h = size.height
                    val crownBob = -abs(sin(t * 3.4f)) * (h * 0.14f)
                    val crownPath = Path().apply {
                        moveTo(w * 0.37f, h * 0.25f + crownBob)
                        lineTo(w * 0.35f, h * 0.16f + crownBob)
                        lineTo(w * 0.44f, h * 0.21f + crownBob)
                        lineTo(w * 0.50f, h * 0.14f + crownBob)
                        lineTo(w * 0.56f, h * 0.21f + crownBob)
                        lineTo(w * 0.65f, h * 0.16f + crownBob)
                        lineTo(w * 0.63f, h * 0.25f + crownBob)
                        close()
                    }
                    drawPath(crownPath, color = Color(0xFFFFD700))
                    drawPath(crownPath, color = Color(0xFFFF6F00), style = Stroke(width = 1.2.dp.toPx()))
                }
            }

            // =========================================================================
            // D. INTELLIGENT CONTEXT-AWARE DJ SPEECH BUBBLE ABOVE GUDDA
            // =========================================================================
            val intelligentBubbleMessage = remember(isMusicPlaying, nowPlayingTitle, danceMode, breed, (animTick / 16f).toInt(), happyJumpTicks > 0, isActivelyWalking) {
                when {
                    happyJumpTicks > 15 -> "🔥 ${breed.hatBadgeText} Boost! Kya Beat Hai!"
                    isMusicPlaying -> {
                        val phaseIdx = ((animTick / 16f).toInt().coerceAtLeast(0)) % 4
                        when (phaseIdx) {
                            0 -> if (nowPlayingTitle.isNotBlank()) "🎵 ${nowPlayingTitle.take(18)}" else "${danceMode.shortBadge} • Live Beat 🎶"
                            1 -> "🕺 ${danceMode.shortBadge} 4D Jump!"
                            2 -> "🎧 ${breed.hatBadgeText} DJ • Full Vibe!"
                            else -> "⚡ Tap Me for 20 Mascots!"
                        }
                    }
                    isActivelyWalking -> "🐾 ${breed.hatBadgeText} • Live 3D Jump"
                    else -> "👋 ${breed.hatBadgeText} • Tap DJ Studio"
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
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = 24.dp,
                    bottom = bottomClearanceDp.coerceAtLeast(108.dp)
                )
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
