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
import androidx.compose.ui.graphics.drawscope.withTransform
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.MediaItem
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
    ),
    SIBERIAN_HUSKY_DJ(
        displayName = "Siberian Husky DJ 🐺",
        drawableResId = R.drawable.img_mascot_sky_puppy,
        primaryFur = Color(0xFF607D8B),
        highlightFur = Color(0xFFECEFF1),
        shadowFur = Color(0xFF263238),
        earColor = Color(0xFF37474F),
        muzzlePatch = Color(0xFFFFFFFF),
        headphoneAccent = Color(0xFF00E5FF),
        isFluffyCassetteMascot = true,
        isTeddyBear = false,
        cassetteLabel = "HUSKY 3D DJ",
        hatBadgeText = "HUSKY",
        isWhiteFurSubject = true
    );

    companion object {
        val FEATURED_4_HEROES: List<CompanionBreed>
            get() = listOf(
                SKY_DJ_PUPPY,
                HONEY_BEAT_TEDDY,
                SIBERIAN_HUSKY_DJ,
                BABY_PANDA_DJ
            )

        val FEATURED_STUDIO_ANIMALS: List<CompanionBreed>
            get() = listOf(
                GOLDEN_LION_DJ,
                SNOW_BEAR_DJ,
                YELLOW_CHICK_DJ,
                WHITE_BUNNY_DJ,
                CYBER_NEON_LION,
                DISCO_PARTY_BUNNY,
                ROCKSTAR_CHICK_DJ,
                VELVET_BOOMBOX_BEAR,
                SIBERIAN_HUSKY_DJ
            )

        val FLUFFY_CASSETTE_MASCOTS: List<CompanionBreed>
            get() = listOf(
                HONEY_BEAT_TEDDY,
                STRAWBERRY_VIBE_TEDDY,
                SKY_DJ_PUPPY,
                SIBERIAN_HUSKY_DJ,
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
 * Custom Fur Color Presets for Puppy, Teddy Bear, Siberian Husky, Panda & All Mascots
 */
enum class MascotFurColorPreset(
    val id: String,
    val label: String,
    val furColor: Color?
) {
    ORIGINAL("ORIGINAL", "✨ Original Fur", null),
    GOLDEN_HONEY("GOLDEN_HONEY", "🍯 Golden Puppy", Color(0xFFF4A259)),
    COCOA_TEDDY("COCOA_TEDDY", "🧸 Fluffy Brown", Color(0xFFC67C3B)),
    HUSKY_SILVER("HUSKY_SILVER", "🐺 Husky Silver", Color(0xFF78909C)),
    SNOW_WHITE("SNOW_WHITE", "❄️ Arctic White", Color(0xFFECEFF1)),
    BERRY_PINK("BERRY_PINK", "🌸 Berry Pink", Color(0xFFFF7597)),
    CYBER_CYAN("CYBER_CYAN", "💎 Cyber Cyan", Color(0xFF26C6DA)),
    ROYAL_LAVENDER("ROYAL_LAVENDER", "🔮 Lavender Plush", Color(0xFFB39DDB));

    companion object {
        fun fromId(id: String): MascotFurColorPreset =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: ORIGINAL
    }
}

/**
 * Global Registry for 2-Second Mascot Drag & Hold Over Any Song Row to Auto-Play
 * + Edge Auto-Scrolling of the Audio List while dragging the Puppy/Teddy Bear.
 */
object MascotSongDropTargetRegistry {
    private val songBoundsMap = ConcurrentHashMap<String, Pair<MediaItem, androidx.compose.ui.geometry.Rect>>()

    var hoveredTrackId by mutableStateOf<String?>(null)
    var hoveredTrackTitle by mutableStateOf("")
    var holdProgress by mutableFloatStateOf(0f)

    @Volatile
    private var autoScrollHandler: ((Float) -> Unit)? = null

    @Volatile
    private var playSongHandler: ((MediaItem) -> Unit)? = null

    fun registerSongRow(track: MediaItem, boundsInRoot: androidx.compose.ui.geometry.Rect) {
        if (boundsInRoot.width > 10f && boundsInRoot.height > 10f) {
            songBoundsMap[track.id] = track to boundsInRoot
        }
    }

    fun unregisterSongRow(trackId: String) {
        songBoundsMap.remove(trackId)
        if (hoveredTrackId == trackId) {
            hoveredTrackId = null
            hoveredTrackTitle = ""
            holdProgress = 0f
        }
    }

    fun clearAllSongRows() {
        songBoundsMap.clear()
        hoveredTrackId = null
        hoveredTrackTitle = ""
        holdProgress = 0f
    }

    fun findSongAt(rootX: Float, rootY: Float): MediaItem? {
        for ((_, pair) in songBoundsMap) {
            val (item, rect) = pair
            if (rootX >= rect.left && rootX <= rect.right && rootY >= rect.top && rootY <= rect.bottom) {
                return item
            }
        }
        return null
    }

    fun registerAutoScrollHandler(handler: ((Float) -> Unit)?) {
        autoScrollHandler = handler
    }

    fun requestAutoScroll(deltaY: Float) {
        autoScrollHandler?.invoke(deltaY)
    }

    fun registerPlaySongHandler(handler: ((MediaItem) -> Unit)?) {
        playSongHandler = handler
    }

    fun triggerPlaySong(item: MediaItem): Boolean {
        val handler = playSongHandler
        return if (handler != null) {
            handler(item)
            true
        } else {
            false
        }
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
    val torsoOnlyLayerBmp: ImageBitmap,
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

            // Central Torso Layer with static outer arms, static lower legs, and upper head erased
            // so moving arms & legs never leave a duplicate static ghost limb behind!
            val torsoOnlyImg = extractFeatheredPlushRegion(
                srcPixels = srcPixels,
                w = w,
                h = h,
                cxRatio = 0.50f,
                cyRatio = 0.56f,
                rxRatio = 0.27f,
                ryRatio = 0.19f
            ).asImageBitmap()

            val headImg = extractFeatheredPlushRegion(
                srcPixels = srcPixels,
                w = w,
                h = h,
                cxRatio = 0.50f,
                cyRatio = 0.26f,
                rxRatio = 0.45f,
                ryRatio = 0.28f
            ).asImageBitmap()

            val leftArmImg = extractFeatheredPlushRegion(
                srcPixels = srcPixels,
                w = w,
                h = h,
                cxRatio = 0.18f,
                cyRatio = 0.55f,
                rxRatio = 0.19f,
                ryRatio = 0.18f
            ).asImageBitmap()

            val rightArmImg = extractFeatheredPlushRegion(
                srcPixels = srcPixels,
                w = w,
                h = h,
                cxRatio = 0.82f,
                cyRatio = 0.55f,
                rxRatio = 0.19f,
                ryRatio = 0.18f
            ).asImageBitmap()

            val leftFootImg = extractFeatheredPlushRegion(
                srcPixels = srcPixels,
                w = w,
                h = h,
                cxRatio = 0.34f,
                cyRatio = 0.84f,
                rxRatio = 0.20f,
                ryRatio = 0.17f
            ).asImageBitmap()

            val rightFootImg = extractFeatheredPlushRegion(
                srcPixels = srcPixels,
                w = w,
                h = h,
                cxRatio = 0.66f,
                cyRatio = 0.84f,
                rxRatio = 0.20f,
                ryRatio = 0.17f
            ).asImageBitmap()

            val parts = ArticulatedMascotParts(
                rawCutoutBitmap = cutout,
                fullCutoutBmp = fullImg,
                torsoOnlyLayerBmp = torsoOnlyImg,
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

/**
 * 100% Real-Time 3D Volumetric Multi-Jointed Plush & Fur Jumping Character Engine:
 * - ZERO static 2D photo stretching! Every single breed is rendered as a true multi-part 3D plush puppet
 *   with 6-stop 3D spherical lighting, rim-glow highlights, procedural fluffy fur tufts around the silhouette,
 *   and 14 independently articulated 3D body parts:
 *   1) Compressing 3D Subwoofer / Trampoline Speaker Pad under the feet with bass-reactive cone & shockwaves
 *   2) Two-segment 3D legs (Hip -> Bending Knee -> 3D Plush Foot with 3 pink toe pads) that squat deep on the
 *      speaker pad and kick joyfully in mid-air or stride front-to-back while walking
 *   3) 3D Plush Torso with breathing belly patch, fluffy perimeter fur tufts, gold chains, or spinning Cassette Deck
 *   4) Two-segment 3D arms (Shoulder -> Elbow -> 3D Plush Paw with 4 pink toe beans & palm pad) that throw HIGH
 *      above the head ("YAY!" cheer jump), pump in Bhangra style, or wave to the beat
 *   5) 3D Spherical Head with breed-specific 3D anatomy (18-lobe Lion Mane, 2-joint floppy Bunny Ears, hanging
 *      floppy Puppy Ears, Panda patches, Chick crown feathers & beak, Penguin tuxedo & Royal Crown, or Devil-Horn Bucket Hat)
 *   6) 3D Studio DJ Headphones with headband arc, pivoting yokes, and bass-thumping RGB neon earcups
 *   7) Deep 3D Galaxy Eyes with moving iris/pupil, dual star catchlights, smooth fur eyelid blinks, happy `^ ^` jump eyes,
 *      and a beat-synced singing 3D mouth with teeth and tongue!
 */
@Composable
fun DeformingJumping3DPlushMascot(
    breed: CompanionBreed,
    parts: ArticulatedMascotParts? = null,
    animTick: Float,
    walkStridePhase: Float,
    walkDir: Float,
    isMusicPlaying: Boolean,
    isActivelyWalking: Boolean,
    happyJumpTicks: Int,
    landingSquashTicks: Int,
    danceMode: MascotDanceMode,
    auraColor: Color,
    customFurColor: Color? = null,
    drawInternalFloorShadow: Boolean = false,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 4f || h <= 4f) return@Canvas

        val t = animTick
        val activeStyle = if (danceMode == MascotDanceMode.AUTO_MIX) {
            when (((t / 7.0f).toInt()) % 6) {
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

        // Active Beat-Synced Dance vs Calm Standing/Breathing Idle Posture:
        // When song is playing (or user triggered a jump stunt), dance energetically!
        // When song is paused/stopped, transition smoothly into calm breathing idle on the floor.
        val isDancingActive = isMusicPlaying || happyJumpTicks > 0
        val energyMult = if (isDancingActive) 1.25f else 0.18f

        // =========================================================================
        // 1. BEAT-SYNCED TRAMPOLINE JUMP & DANCE PHYSICS VS CALM IDLE STOP
        // =========================================================================
        val jumpSpeed = when {
            !isDancingActive -> 1.6f
            activeStyle == MascotDanceMode.TRAMPOLINE_ACROBAT -> 4.1f
            activeStyle == MascotDanceMode.BHANGRA_GROOVE -> 3.8f
            activeStyle == MascotDanceMode.DJ_BOUNCE -> 3.6f
            else -> 3.6f
        }
        val jumpCycle = t * jumpSpeed
        val normPhase = ((jumpCycle / 6.2831855f) % 1f + 1f) % 1f

        val airborneFactor: Float
        val crouchFactor: Float
        if (!isDancingActive) {
            // Calm standing/breathing idle posture on the bottom floor when music is paused/stopped
            airborneFactor = 0f
            crouchFactor = (sin(t * 1.8f) + 1f) * 0.035f
        } else if (normPhase in 0.24f..0.88f) {
            val airU = (normPhase - 0.24f) / 0.64f
            airborneFactor = sin(airU * 3.1415927f).coerceIn(0f, 1f)
            crouchFactor = 0f
        } else {
            airborneFactor = 0f
            val groundU = if (normPhase < 0.24f) {
                (normPhase + 0.12f) / 0.36f
            } else {
                (normPhase - 0.88f) / 0.36f
            }
            crouchFactor = sin(groundU * 3.1415927f).coerceIn(0f, 1f)
        }

        val extraLandingSquash = if (landingSquashTicks > 0) sin(landingSquashTicks * 0.45f) * 0.22f else 0f
        val totalCrouch = (crouchFactor + extraLandingSquash).coerceIn(0f, 1.22f)
        val stuntBoost = if (happyJumpTicks > 0) 1.22f else 1.0f

        // Base unit scale so proportions stay crisp at any Dp size (88dp overlay or 252dp stage)
        val u = min(w, h)
        val centerX = w * 0.5f + (if (isDancingActive) sin(t * 2.4f) * u * 0.025f else 0f)
        val groundY = h * 0.88f

        // High vertical jump trajectory when dancing + knee-squat drop on floor bounce
        val jumpLiftY = -airborneFactor * (h * 0.27f * energyMult * stuntBoost) +
            totalCrouch * (h * 0.055f)

        // Cartoon Squash & Stretch ratios for torso & head (reacts on every multi-bounce impact!)
        val squashX = 1.0f - airborneFactor * 0.09f + totalCrouch * 0.18f
        val stretchY = 1.0f + airborneFactor * 0.12f - totalCrouch * 0.18f

        // Helper for 3D volumetric plush shading (highlight -> mid fur -> deep shadow + rim light)
        fun lighten(c: Color, amt: Float): Color = Color(
            red = (c.red + (1f - c.red) * amt).coerceIn(0f, 1f),
            green = (c.green + (1f - c.green) * amt).coerceIn(0f, 1f),
            blue = (c.blue + (1f - c.blue) * amt).coerceIn(0f, 1f),
            alpha = c.alpha
        )

        fun darken(c: Color, amt: Float): Color = Color(
            red = (c.red * (1f - amt)).coerceIn(0f, 1f),
            green = (c.green * (1f - amt)).coerceIn(0f, 1f),
            blue = (c.blue * (1f - amt)).coerceIn(0f, 1f),
            alpha = c.alpha
        )

        val isPanda = breed == CompanionBreed.BABY_PANDA_DJ || breed == CompanionBreed.BHANGRA_KING_PANDA
        val isHusky = breed == CompanionBreed.SIBERIAN_HUSKY_DJ
        val isBunny = breed.isBunny || breed == CompanionBreed.WHITE_BUNNY_DJ || breed == CompanionBreed.DISCO_PARTY_BUNNY
        val isPuppy = breed == CompanionBreed.SKY_DJ_PUPPY || breed == CompanionBreed.LAVENDER_PUPPY_TAPE || breed == CompanionBreed.HIPHOP_GOLD_PUPPY
        val isLion = breed.isLion || breed == CompanionBreed.GOLDEN_LION_DJ || breed == CompanionBreed.CYBER_NEON_LION
        val isChick = breed == CompanionBreed.YELLOW_CHICK_DJ || breed == CompanionBreed.ROCKSTAR_CHICK_DJ
        val isPenguin = breed.isPenguin || breed == CompanionBreed.ROYAL_KING_PENGUIN || breed == CompanionBreed.COSMIC_ASTRO_PENGUIN
        val holdsDeck = breed == CompanionBreed.LAVENDER_MIXTAPE_TEDDY || breed == CompanionBreed.VELVET_BOOMBOX_BEAR ||
            breed == CompanionBreed.LAVENDER_PUPPY_TAPE

        val mainFur = customFurColor ?: breed.primaryFur
        val lightFur = lighten(mainFur, 0.34f)
        val darkFur = darken(mainFur, 0.38f)
        val limbFur = if (isPanda && customFurColor == null) Color(0xFF1C2026) else mainFur
        val limbLight = if (isPanda && customFurColor == null) Color(0xFF454E5E) else lightFur
        val limbDark = if (isPanda && customFurColor == null) Color(0xFF0B0D11) else darkFur
        val bellyColor = breed.muzzlePatch
        val accentColor = breed.headphoneAccent

        // Helper: Draw a 3D shaded plush sphere/oval with optional fluffy perimeter fur tufts
        fun drawPlush3DOval(
            cx: Float,
            cy: Float,
            rx: Float,
            ry: Float,
            hiColor: Color = lightFur,
            midColor: Color = mainFur,
            loColor: Color = darkFur,
            rimColor: Color = auraColor,
            furTuftCount: Int = 0,
            tuftSize: Float = u * 0.018f
        ) {
            // 1. Optional perimeter fluffy fur tufts so silhouette looks like soft 3D plush fur
            if (furTuftCount > 0 && !isPenguin) {
                for (i in 0 until furTuftCount) {
                    val ang = (i.toFloat() / furTuftCount) * 6.2831855f
                    val tx = cx + cos(ang) * (rx * 0.95f)
                    val ty = cy + sin(ang) * (ry * 0.95f)
                    val tColor = if (sin(ang) < 0f) hiColor else midColor
                    drawCircle(
                        color = tColor,
                        radius = tuftSize,
                        center = Offset(tx, ty)
                    )
                }
            }
            // 2. Main 3D spherical radial gradient fill
            val gradRadius = max(rx, ry) * 1.45f
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(hiColor, midColor, loColor),
                    center = Offset(cx - rx * 0.28f, cy - ry * 0.32f),
                    radius = gradRadius.coerceAtLeast(4f)
                ),
                topLeft = Offset(cx - rx, cy - ry),
                size = Size(rx * 2f, ry * 2f)
            )
            // 3. Subtle 3D rim light on top-left edge
            drawArc(
                color = rimColor.copy(alpha = 0.42f),
                startAngle = 195f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(cx - rx * 0.96f, cy - ry * 0.96f),
                size = Size(rx * 1.92f, ry * 1.92f),
                style = Stroke(width = (u * 0.012f).coerceAtLeast(1.2f), cap = StrokeCap.Round)
            )
        }

        // Helper: Draw a 3D plush limb segment (capsule between two joints)
        fun drawPlushLimbSegment(
            p1: Offset,
            p2: Offset,
            thickness: Float,
            hi: Color = limbLight,
            mid: Color = limbFur,
            lo: Color = limbDark
        ) {
            // Outer dark fur edge for 3D depth separation
            drawLine(
                color = lo,
                start = p1,
                end = p2,
                strokeWidth = thickness * 1.08f,
                cap = StrokeCap.Round
            )
            // Main 3D plush cylinder gradient
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(hi, mid, lo),
                    start = Offset(min(p1.x, p2.x) - thickness * 0.5f, min(p1.y, p2.y) - thickness * 0.5f),
                    end = Offset(max(p1.x, p2.x) + thickness * 0.5f, max(p1.y, p2.y) + thickness * 0.5f)
                ),
                start = p1,
                end = p2,
                strokeWidth = thickness,
                cap = StrokeCap.Round
            )
            // Soft velvet specular highlight along upper edge
            val highlightOffset = Offset(-thickness * 0.14f, -thickness * 0.14f)
            drawLine(
                color = hi.copy(alpha = 0.55f),
                start = p1 + highlightOffset,
                end = p2 + highlightOffset,
                strokeWidth = thickness * 0.34f,
                cap = StrokeCap.Round
            )
        }

        // =========================================================================
        // 2. BOUNCING 3D SUBWOOFER / TRAMPOLINE SPEAKER PAD ON THE FLOOR
        //    Physically compresses downward when the Gudda squats (`totalCrouch`)
        //    and springs upward with glowing bass rings when Gudda launches!
        // =========================================================================
        val padCompressionY = totalCrouch * (u * 0.045f) - airborneFactor * (u * 0.015f)
        val padCenterY = groundY + padCompressionY
        val padRx = u * (0.34f + totalCrouch * 0.05f - airborneFactor * 0.02f)
        val padRy = u * (0.075f - totalCrouch * 0.015f).coerceAtLeast(0.045f)

        if (drawInternalFloorShadow) {
            // Soft ground shadow under the speaker pad
            drawOval(
                color = Color.Black.copy(alpha = 0.50f),
                topLeft = Offset(centerX - padRx * 1.12f, groundY - padRy * 0.4f),
                size = Size(padRx * 2.24f, padRy * 2.2f)
            )
        }

        // 3D Speaker Base Cylinder Wall
        val baseHeight = (u * 0.055f * (1f - totalCrouch * 0.38f)).coerceAtLeast(u * 0.022f)
        drawOval(
            color = Color(0xFF0A0F1D),
            topLeft = Offset(centerX - padRx, padCenterY + baseHeight - padRy),
            size = Size(padRx * 2f, padRy * 2f)
        )
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFF0D1426), Color(0xFF233254), Color(0xFF0D1426)),
                startX = centerX - padRx,
                endX = centerX + padRx
            ),
            topLeft = Offset(centerX - padRx, padCenterY),
            size = Size(padRx * 2f, baseHeight)
        )
        // Neon LED strip around speaker base
        drawOval(
            brush = Brush.horizontalGradient(
                listOf(accentColor, auraColor, accentColor)
            ),
            topLeft = Offset(centerX - padRx, padCenterY + baseHeight * 0.55f - padRy),
            size = Size(padRx * 2f, padRy * 2f),
            style = Stroke(width = (u * 0.018f).coerceAtLeast(1.8f))
        )
        // Top Bouncing Trampoline Woofer Cone
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF28354E), Color(0xFF131B2E), Color(0xFF090D18)),
                center = Offset(centerX, padCenterY - padRy * 0.2f),
                radius = padRx * 1.1f
            ),
            topLeft = Offset(centerX - padRx, padCenterY - padRy),
            size = Size(padRx * 2f, padRy * 2f)
        )
        // Glowing Bass Cone Ring
        drawOval(
            color = accentColor.copy(alpha = (0.65f + totalCrouch * 0.30f).coerceIn(0.4f, 0.98f)),
            topLeft = Offset(centerX - padRx * 0.72f, padCenterY - padRy * 0.72f),
            size = Size(padRx * 1.44f, padRy * 1.44f),
            style = Stroke(width = (u * 0.016f).coerceAtLeast(1.6f))
        )
        // Dynamic shadow of the Gudda on the speaker cone (shrinks as Gudda jumps high!)
        val shadowScale = (1f - airborneFactor * 0.58f + totalCrouch * 0.15f).coerceIn(0.32f, 1.15f)
        drawOval(
            color = Color.Black.copy(alpha = (0.62f * shadowScale).coerceIn(0.18f, 0.68f)),
            topLeft = Offset(centerX - padRx * 0.62f * shadowScale, padCenterY - padRy * 0.58f * shadowScale),
            size = Size(padRx * 1.24f * shadowScale, padRy * 1.16f * shadowScale)
        )
        // Launch / Landing Shockwave Ring
        if (totalCrouch > 0.12f || airborneFactor in 0.01f..0.28f) {
            val waveScale = 1.05f + (1f - airborneFactor) * 0.28f
            drawOval(
                color = auraColor.copy(alpha = (0.70f * (1f - airborneFactor)).coerceIn(0f, 0.78f)),
                topLeft = Offset(centerX - padRx * waveScale, padCenterY - padRy * waveScale),
                size = Size(padRx * 2f * waveScale, padRy * 2f * waveScale),
                style = Stroke(width = (u * 0.016f).coerceAtLeast(1.5f))
            )
        }

        // =========================================================================
        // 2B. TRUE 6-PART SKELETAL PHOTO-PUPPET ENGINE (100% EXACT PHOTO LOOK +
        //     REAL WALKING PHOTO LEGS, SWINGING PHOTO ARMS, TURNING PHOTO HEAD,
        //     AND REAL 3D PINK TONGUE COMING IN & OUT OF THE PHOTO'S MOUTH!)
        // =========================================================================
        if (parts != null) {
            val rig = getFaceRigForBreed(breed)
            val dstW = (w * 0.90f * squashX).roundToInt().coerceAtLeast(16)
            val dstH = (h * 0.84f * stretchY).roundToInt().coerceAtLeast(16)
            val hipSwayPx = sin(t * 3.8f) * (w * 0.032f)
            val walkBounceY = -abs(sin(walkStridePhase * 1.4f)) * (h * 0.042f)
            val totalLiftY = jumpLiftY * 0.72f + walkBounceY
            val dstLeft = ((w - dstW) * 0.5f + hipSwayPx).roundToInt()
            val dstTop = ((h * 0.07f) + totalLiftY).roundToInt()
            val dstOffset = IntOffset(dstLeft, dstTop)
            val dstSize = IntSize(dstW, dstH)

            val legPhase = walkStridePhase * 1.55f + t * 2.2f
            val leftStepSin = sin(legPhase)
            val rightStepSin = -sin(legPhase)

            // 1. Wagging 3D Plush Tail behind the photo-puppet
            val tailBase = Offset(dstLeft + dstW * 0.70f, dstTop + dstH * 0.66f)
            val tailWag = sin(t * 9.5f) * 0.65f
            val tailTip = Offset(
                tailBase.x + cos(tailWag - 0.3f) * (u * 0.13f),
                tailBase.y + sin(tailWag - 0.3f) * (u * 0.10f) - u * 0.03f
            )
            drawPlushLimbSegment(tailBase, tailTip, u * 0.042f, lightFur, mainFur, darkFur)

            // 2. Real Photo Left & Right Legs (stepping forward/backward, lifting up/down, and kicking!)
            val leftHipAnchor = Offset(dstLeft + dstW * 0.36f, dstTop + dstH * 0.71f)
            val rightHipAnchor = Offset(dstLeft + dstW * 0.64f, dstTop + dstH * 0.71f)

            val leftLegStrideX = leftStepSin * (dstW * 0.13f) - airborneFactor * (dstW * 0.06f)
            val leftLegLiftY = -max(0f, leftStepSin) * (dstH * 0.12f) - airborneFactor * (dstH * 0.07f)
            val leftLegAngleDeg = leftStepSin * 28f - airborneFactor * 22f

            val rightLegStrideX = rightStepSin * (dstW * 0.13f) + airborneFactor * (dstW * 0.06f)
            val rightLegLiftY = -max(0f, rightStepSin) * (dstH * 0.12f) - airborneFactor * (dstH * 0.07f)
            val rightLegAngleDeg = rightStepSin * 28f + airborneFactor * 22f

            // Fur-matched 3D thigh bridges so legs stay seamlessly connected during big strides
            val leftAnklePos = Offset(
                leftHipAnchor.x + leftLegStrideX,
                leftHipAnchor.y + dstH * 0.12f + leftLegLiftY
            )
            val rightAnklePos = Offset(
                rightHipAnchor.x + rightLegStrideX,
                rightHipAnchor.y + dstH * 0.12f + rightLegLiftY
            )
            drawPlushLimbSegment(leftHipAnchor, leftAnklePos, u * 0.095f, limbLight, limbFur, limbDark)
            drawPlushLimbSegment(rightHipAnchor, rightAnklePos, u * 0.095f, limbLight, limbFur, limbDark)

            // Draw back leg first, then front leg based on stride phase for true 3D depth!
            withTransform({
                translate(left = leftLegStrideX, top = leftLegLiftY)
                rotate(degrees = leftLegAngleDeg, pivot = leftHipAnchor)
            }) {
                drawImage(
                    image = parts.leftFootLayerBmp,
                    dstOffset = dstOffset,
                    dstSize = dstSize
                )
            }
            withTransform({
                translate(left = rightLegStrideX, top = rightLegLiftY)
                rotate(degrees = rightLegAngleDeg, pivot = rightHipAnchor)
            }) {
                drawImage(
                    image = parts.rightFootLayerBmp,
                    dstOffset = dstOffset,
                    dstSize = dstSize
                )
            }

            // 3. Real Photo Central Torso / Belly (`torsoOnlyLayerBmp` — static arms & legs cleanly removed!)
            val torsoBreathScale = 1f + sin(t * 3.8f) * 0.032f
            withTransform({
                scale(
                    scaleX = torsoBreathScale,
                    scaleY = 1f / torsoBreathScale,
                    pivot = Offset(dstLeft + dstW * 0.50f, dstTop + dstH * 0.56f)
                )
            }) {
                drawImage(
                    image = parts.torsoOnlyLayerBmp,
                    dstOffset = dstOffset,
                    dstSize = dstSize
                )
            }

            // 4. Real Photo Left & Right Arms swinging & waving from shoulder joints!
            val leftShoulderPivot = Offset(dstLeft + dstW * 0.27f, dstTop + dstH * 0.48f)
            val rightShoulderPivot = Offset(dstLeft + dstW * 0.73f, dstTop + dstH * 0.48f)

            val armWaveSin = sin(t * 5.4f + walkStridePhase * 0.8f)
            val armWaveCos = cos(t * 5.4f + walkStridePhase * 0.8f)

            val leftArmDeg = armWaveSin * 36f + airborneFactor * 42f - totalCrouch * 20f
            val rightArmDeg = -armWaveCos * 36f - airborneFactor * 42f + totalCrouch * 20f
            val leftArmLiftY = -max(0f, armWaveSin) * (dstH * 0.07f) - airborneFactor * (dstH * 0.08f)
            val rightArmLiftY = -max(0f, armWaveCos) * (dstH * 0.07f) - airborneFactor * (dstH * 0.08f)
            val leftArmOutX = -abs(armWaveSin) * (dstW * 0.045f)
            val rightArmOutX = abs(armWaveCos) * (dstW * 0.045f)

            // 3D Fur Shoulder Bridges so waving arms stay smoothly joined to torso
            drawPlushLimbSegment(
                leftShoulderPivot,
                Offset(leftShoulderPivot.x - dstW * 0.08f + leftArmOutX, leftShoulderPivot.y + dstH * 0.06f + leftArmLiftY),
                u * 0.082f,
                limbLight,
                limbFur,
                limbDark
            )
            drawPlushLimbSegment(
                rightShoulderPivot,
                Offset(rightShoulderPivot.x + dstW * 0.08f + rightArmOutX, rightShoulderPivot.y + dstH * 0.06f + rightArmLiftY),
                u * 0.082f,
                limbLight,
                limbFur,
                limbDark
            )

            withTransform({
                translate(left = leftArmOutX, top = leftArmLiftY)
                rotate(degrees = leftArmDeg, pivot = leftShoulderPivot)
            }) {
                drawImage(
                    image = parts.leftArmLayerBmp,
                    dstOffset = dstOffset,
                    dstSize = dstSize
                )
            }
            withTransform({
                translate(left = rightArmOutX, top = rightArmLiftY)
                rotate(degrees = rightArmDeg, pivot = rightShoulderPivot)
            }) {
                drawImage(
                    image = parts.rightArmLayerBmp,
                    dstOffset = dstOffset,
                    dstSize = dstSize
                )
            }

            // 5. Real Photo Head Turning Left-Right, Tilting & Nodding + Real 3D Tongue Coming Out of Mouth!
            val neckPivot = Offset(dstLeft + dstW * 0.50f, dstTop + dstH * 0.42f)
            val headTurnX = sin(t * 2.4f) * (dstW * 0.045f) + walkDir * (dstW * 0.024f)
            val headNodY = cos(t * 4.8f) * (dstH * 0.022f) - airborneFactor * (dstH * 0.028f)
            val headTiltDeg = sin(t * 3.1f) * 12.5f + walkDir * 3.5f

            withTransform({
                translate(left = headTurnX, top = headNodY)
                rotate(degrees = headTiltDeg, pivot = neckPivot)
            }) {
                // 5A. Draw the actual photo's 3D Head, Ears & Headphones
                drawImage(
                    image = parts.headLayerBmp,
                    dstOffset = dstOffset,
                    dstSize = dstSize
                )

                // 5B. Animated Blinking Eyes & Moving 3D Specular Catchlights on the Photo's Eyes
                val leftEyeCenter = Offset(dstLeft + rig.leftEyeX * dstW, dstTop + rig.leftEyeY * dstH)
                val rightEyeCenter = Offset(dstLeft + rig.rightEyeX * dstW, dstTop + rig.rightEyeY * dstH)
                val eyeRadius = rig.eyeRadiusRatio * dstW
                val gazeShiftX = sin(t * 2.4f) * (eyeRadius * 0.34f) + walkDir * (eyeRadius * 0.20f)
                val gazeShiftY = cos(t * 3.0f) * (eyeRadius * 0.22f)

                val blinkCycle = (t * 1.7f) % 4.8f
                val blinkAmount = if (blinkCycle > 4.42f) {
                    sin(((blinkCycle - 4.42f) / 0.38f) * 3.1415927f).coerceIn(0f, 1f)
                } else 0f

                listOf(leftEyeCenter, rightEyeCenter).forEach { eyePos ->
                    // Moving 3D glossy catchlights inside the photo's real eyes
                    drawCircle(
                        color = Color.White,
                        radius = eyeRadius * 0.30f,
                        center = Offset(eyePos.x - eyeRadius * 0.22f + gazeShiftX, eyePos.y - eyeRadius * 0.24f + gazeShiftY)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f),
                        radius = eyeRadius * 0.15f,
                        center = Offset(eyePos.x + eyeRadius * 0.26f + gazeShiftX, eyePos.y + eyeRadius * 0.22f + gazeShiftY)
                    )
                    // Natural fur eyelid blink
                    if (blinkAmount > 0.05f) {
                        drawOval(
                            color = mainFur,
                            topLeft = Offset(eyePos.x - eyeRadius * 1.05f, eyePos.y - eyeRadius * 1.05f),
                            size = Size(eyeRadius * 2.1f, eyeRadius * 2.1f * blinkAmount)
                        )
                    }
                }

                // 5C. REAL MOVING MOUTH & 3D PINK TONGUE COMING IN AND OUT ("jeebh andar se bahar aaye")
                val mouthCenter = Offset(dstLeft + rig.mouthX * dstW, dstTop + rig.mouthY * dstH)
                // Smooth cycle where tongue slides OUT from inside the mouth (0.0 -> 1.0) and retracts back IN!
                val rawTongueWave = (sin(t * 4.2f) + 0.35f).coerceIn(0f, 1.35f) / 1.35f
                val tongueOutFactor = (rawTongueWave + airborneFactor * 0.35f).coerceIn(0.12f, 1.0f)

                val mouthW = dstW * 0.115f * (0.85f + tongueOutFactor * 0.25f)
                val mouthCavityH = dstH * 0.042f * (0.55f + tongueOutFactor * 0.65f)

                // Dark 3D inside-mouth cavity
                drawOval(
                    color = Color(0xFF3A0812),
                    topLeft = Offset(mouthCenter.x - mouthW * 0.50f, mouthCenter.y - mouthCavityH * 0.42f),
                    size = Size(mouthW, mouthCavityH)
                )

                // 3D Pink Tongue emerging from inside the mouth, extending downward & wiggling left-right!
                if (tongueOutFactor > 0.18f) {
                    val tongueW = mouthW * 0.72f
                    val tongueLen = (dstH * 0.068f) * tongueOutFactor
                    val tongueWiggleX = sin(t * 8.6f) * (mouthW * 0.18f)
                    val tongueTopY = mouthCenter.y - mouthCavityH * 0.10f

                    val tonguePath = Path().apply {
                        moveTo(mouthCenter.x - tongueW * 0.48f, tongueTopY)
                        quadraticTo(
                            mouthCenter.x - tongueW * 0.54f + tongueWiggleX * 0.5f,
                            tongueTopY + tongueLen * 0.75f,
                            mouthCenter.x + tongueWiggleX,
                            tongueTopY + tongueLen
                        )
                        quadraticTo(
                            mouthCenter.x + tongueW * 0.54f + tongueWiggleX * 0.5f,
                            tongueTopY + tongueLen * 0.75f,
                            mouthCenter.x + tongueW * 0.48f,
                            tongueTopY
                        )
                        close()
                    }
                    drawPath(
                        path = tonguePath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFC2185B),
                                Color(0xFFFF4081),
                                Color(0xFFFF80AB)
                            ),
                            startY = tongueTopY,
                            endY = tongueTopY + tongueLen
                        )
                    )
                    drawPath(
                        path = tonguePath,
                        color = Color(0xFF880E4F),
                        style = Stroke(width = (u * 0.008f).coerceAtLeast(1f))
                    )
                    // Center tongue groove
                    drawLine(
                        color = Color(0xFFAD1457),
                        start = Offset(mouthCenter.x, tongueTopY + tongueLen * 0.08f),
                        end = Offset(mouthCenter.x + tongueWiggleX * 0.75f, tongueTopY + tongueLen * 0.74f),
                        strokeWidth = (u * 0.009f).coerceAtLeast(1.1f),
                        cap = StrokeCap.Round
                    )
                    // Wet glossy white catchlight on the tip of the tongue
                    drawOval(
                        color = Color.White.copy(alpha = 0.72f),
                        topLeft = Offset(
                            mouthCenter.x - tongueW * 0.18f + tongueWiggleX * 0.7f,
                            tongueTopY + tongueLen * 0.58f
                        ),
                        size = Size(tongueW * 0.32f, tongueLen * 0.22f)
                    )
                }
            }
            return@Canvas
        }

        // =========================================================================
        // 3. BODY CORE COORDINATES (TORSO, HIPS, SHOULDERS, NECK)
        // =========================================================================
        val hipSwayX = sin(t * 3.4f) * (u * 0.032f * energyMult)
        val torsoCenterX = centerX + hipSwayX
        val torsoCenterY = (h * 0.60f) + jumpLiftY
        val torsoRx = u * 0.175f * squashX
        val torsoRy = u * 0.155f * stretchY

        // =========================================================================
        // 4. PLAYFUL WAGGING 3D TAIL (Fast wag when dancing, calm slow sway when idle)
        // =========================================================================
        val tailSpeed = if (isDancingActive) 10.5f else 2.2f
        val tailAmp = if (isDancingActive) 0.62f else 0.16f
        val tailWagAngle = sin(t * tailSpeed) * tailAmp + airborneFactor * 0.35f
        val tailBase = Offset(torsoCenterX + torsoRx * 0.72f, torsoCenterY + torsoRy * 0.38f)
        val tailTip = Offset(
            tailBase.x + cos(tailWagAngle - 0.35f) * (u * 0.12f),
            tailBase.y + sin(tailWagAngle - 0.35f) * (u * 0.095f) - u * 0.035f
        )
        if (isBunny) {
            drawPlush3DOval(
                cx = tailBase.x + u * 0.035f,
                cy = tailBase.y + sin(t * tailSpeed) * u * 0.015f,
                rx = u * 0.048f,
                ry = u * 0.046f,
                hiColor = Color.White,
                midColor = Color(0xFFF5F5F7),
                loColor = Color(0xFFD0D4DC),
                furTuftCount = 8,
                tuftSize = u * 0.012f
            )
        } else if (isLion || isPuppy || isHusky) {
            drawPlushLimbSegment(
                p1 = tailBase,
                p2 = tailTip,
                thickness = if (isHusky) u * 0.046f else u * 0.038f,
                hi = if (isHusky) Color.White else lightFur,
                mid = mainFur,
                lo = darkFur
            )
            if (isLion || isHusky) {
                drawPlush3DOval(
                    cx = tailTip.x,
                    cy = tailTip.y,
                    rx = u * 0.036f,
                    ry = u * 0.032f,
                    hiColor = if (isHusky) Color.White else lighten(breed.earColor, 0.2f),
                    midColor = if (isHusky) Color(0xFFECEFF1) else breed.earColor,
                    loColor = darken(breed.earColor, 0.3f),
                    furTuftCount = 6,
                    tuftSize = u * 0.010f
                )
            }
        } else if (!isChick && !isPenguin) {
            drawPlush3DOval(
                cx = tailBase.x + u * 0.025f + sin(t * tailSpeed) * u * 0.014f,
                cy = tailBase.y,
                rx = u * 0.040f,
                ry = u * 0.040f,
                hiColor = lightFur,
                midColor = mainFur,
                loColor = darkFur
            )
        }

        // =========================================================================
        // 5. TWO-JOINT 3D ARTICULATED LEGS, BENDING KNEES & PLUSH FEET WITH TOE BEANS
        //    - Deep outward frog-knee squat on crouch (`totalCrouch`)
        //    - High mid-air foot kicks & ankle flex when jumping (`airborneFactor`)
        //    - Alternating walk stride when patrolling (`walkStridePhase`)
        // =========================================================================
        val legPhase = if (isActivelyWalking) walkStridePhase * 1.4f else t * 4.8f
        val leftStride = sin(legPhase)
        val rightStride = -sin(legPhase)

        val leftHip = Offset(torsoCenterX - torsoRx * 0.52f, torsoCenterY + torsoRy * 0.58f)
        val rightHip = Offset(torsoCenterX + torsoRx * 0.52f, torsoCenterY + torsoRy * 0.58f)

        // Left Knee & Foot kinematics
        val leftKneeOutX = -u * (0.045f + totalCrouch * 0.085f + max(0f, leftStride) * 0.035f)
        val leftKneeY = leftHip.y + u * (0.075f - totalCrouch * 0.040f - airborneFactor * 0.025f)
        val leftKnee = Offset(leftHip.x + leftKneeOutX, leftKneeY)

        val leftFootLift = (max(0f, leftStride) * 0.065f + airborneFactor * 0.045f) * u * energyMult
        val leftFootKickX = -airborneFactor * u * 0.055f - max(0f, leftStride) * u * 0.030f
        val leftFoot = Offset(
            x = leftHip.x - u * 0.025f + leftFootKickX,
            y = (leftHip.y + u * (0.145f - totalCrouch * 0.050f) - leftFootLift).coerceAtMost(padCenterY - u * 0.015f)
        )

        // Right Knee & Foot kinematics
        val rightKneeOutX = u * (0.045f + totalCrouch * 0.085f + max(0f, rightStride) * 0.035f)
        val rightKneeY = rightHip.y + u * (0.075f - totalCrouch * 0.040f - airborneFactor * 0.025f)
        val rightKnee = Offset(rightHip.x + rightKneeOutX, rightKneeY)

        val rightFootLift = (max(0f, rightStride) * 0.065f + airborneFactor * 0.045f) * u * energyMult
        val rightFootKickX = airborneFactor * u * 0.055f + max(0f, rightStride) * u * 0.030f
        val rightFoot = Offset(
            x = rightHip.x + u * 0.025f + rightFootKickX,
            y = (rightHip.y + u * (0.145f - totalCrouch * 0.050f) - rightFootLift).coerceAtMost(padCenterY - u * 0.015f)
        )

        val legThickness = u * 0.078f

        fun draw3DFootWithBeans(footCenter: Offset, isLeft: Boolean, isKickingUp: Boolean) {
            if (isChick || isPenguin) {
                // 3D Orange webbed/clawed bird foot
                val orangeHi = Color(0xFFFFB74D)
                val orangeMid = Color(0xFFFF8F00)
                val orangeLo = Color(0xFFE65100)
                drawPlush3DOval(
                    cx = footCenter.x,
                    cy = footCenter.y,
                    rx = u * 0.056f,
                    ry = u * 0.034f,
                    hiColor = orangeHi,
                    midColor = orangeMid,
                    loColor = orangeLo
                )
                for (toe in -1..1) {
                    drawCircle(
                        color = orangeHi,
                        radius = u * 0.016f,
                        center = Offset(footCenter.x + toe * u * 0.024f, footCenter.y + u * 0.018f)
                    )
                }
            } else {
                // Plush 3D Paw Foot + Pink Sole Pad & 3 Toe Beans when jumping/kicking!
                val footRx = u * 0.062f
                val footRy = u * (if (isKickingUp) 0.052f else 0.042f)
                drawPlush3DOval(
                    cx = footCenter.x + (if (isLeft) -u * 0.006f else u * 0.006f),
                    cy = footCenter.y,
                    rx = footRx,
                    ry = footRy,
                    hiColor = limbLight,
                    midColor = limbFur,
                    loColor = limbDark,
                    furTuftCount = 6,
                    tuftSize = u * 0.010f
                )
                // Pink plush paw sole pad & 3 cute toe beans
                val padColor = Color(0xFFFF80AB)
                drawOval(
                    color = padColor,
                    topLeft = Offset(footCenter.x - footRx * 0.45f, footCenter.y - footRy * 0.25f),
                    size = Size(footRx * 0.90f, footRy * 0.72f)
                )
                for (tIdx in -1..1) {
                    drawCircle(
                        color = padColor,
                        radius = footRx * 0.18f,
                        center = Offset(
                            footCenter.x + tIdx * footRx * 0.36f,
                            footCenter.y - footRy * 0.48f
                        )
                    )
                }
            }
        }

        // Draw Left & Right 2-Segment 3D Legs (Hip -> Knee -> Foot)
        if (!isChick) {
            drawPlushLimbSegment(leftHip, leftKnee, legThickness)
            drawPlushLimbSegment(leftKnee, leftFoot, legThickness * 0.92f)
            drawPlushLimbSegment(rightHip, rightKnee, legThickness)
            drawPlushLimbSegment(rightKnee, rightFoot, legThickness * 0.92f)
        } else {
            // Slender bird legs for Chick
            drawPlushLimbSegment(leftHip, leftFoot, u * 0.032f, Color(0xFFFFB74D), Color(0xFFFF8F00), Color(0xFFE65100))
            drawPlushLimbSegment(rightHip, rightFoot, u * 0.032f, Color(0xFFFFB74D), Color(0xFFFF8F00), Color(0xFFE65100))
        }
        draw3DFootWithBeans(leftFoot, isLeft = true, isKickingUp = airborneFactor > 0.2f || leftStride > 0.2f)
        draw3DFootWithBeans(rightFoot, isLeft = false, isKickingUp = airborneFactor > 0.2f || rightStride > 0.2f)

        // =========================================================================
        // 6. 3D VOLUMETRIC PLUSH TORSO & BELLY PATCH (+ CASSETTE DECK OR GOLD CHAIN)
        // =========================================================================
        drawPlush3DOval(
            cx = torsoCenterX,
            cy = torsoCenterY,
            rx = torsoRx,
            ry = torsoRy,
            hiColor = lightFur,
            midColor = mainFur,
            loColor = darkFur,
            furTuftCount = 16,
            tuftSize = u * 0.016f
        )

        // Soft 3D Cream/White Plush Belly Patch
        val bellyBreath = 1f + sin(t * 3.6f) * 0.045f
        val bellyRx = torsoRx * (if (isPenguin) 0.76f else 0.68f) * bellyBreath
        val bellyRy = torsoRy * (if (isPenguin) 0.80f else 0.70f) * bellyBreath
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    lighten(bellyColor, 0.35f),
                    bellyColor,
                    darken(bellyColor, 0.18f)
                ),
                center = Offset(torsoCenterX - bellyRx * 0.2f, torsoCenterY - bellyRy * 0.2f),
                radius = max(bellyRx, bellyRy) * 1.3f
            ),
            topLeft = Offset(torsoCenterX - bellyRx, torsoCenterY - bellyRy * 0.85f),
            size = Size(bellyRx * 2f, bellyRy * 1.85f)
        )

        // Hip-Hop Gold Chain or Bhangra Jacket Trim OR 3D Spinning Cassette Boombox Deck
        if (holdsDeck) {
            val deckW = torsoRx * 1.28f
            val deckH = torsoRy * 0.86f
            val deckLeft = torsoCenterX - deckW * 0.5f
            val deckTop = torsoCenterY - deckH * 0.42f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF28354A), Color(0xFF121824)),
                    startY = deckTop,
                    endY = deckTop + deckH
                ),
                topLeft = Offset(deckLeft, deckTop),
                size = Size(deckW, deckH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(u * 0.03f, u * 0.03f)
            )
            drawRoundRect(
                color = accentColor,
                topLeft = Offset(deckLeft, deckTop),
                size = Size(deckW, deckH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(u * 0.03f, u * 0.03f),
                style = Stroke(width = (u * 0.012f).coerceAtLeast(1.4f))
            )
            // Dual spinning cassette reels inside chest deck
            val reelR = deckH * 0.26f
            val spinAng = t * 6.8f
            listOf(torsoCenterX - deckW * 0.24f, torsoCenterX + deckW * 0.24f).forEach { rxPos ->
                val rCenter = Offset(rxPos, deckTop + deckH * 0.52f)
                drawCircle(color = Color(0xFF0B0F19), radius = reelR, center = rCenter)
                drawCircle(
                    color = accentColor,
                    radius = reelR,
                    center = rCenter,
                    style = Stroke(width = (u * 0.010f).coerceAtLeast(1.2f))
                )
                val sx = cos(spinAng) * reelR * 0.8f
                val sy = sin(spinAng) * reelR * 0.8f
                drawLine(Color.White, rCenter - Offset(sx, sy), rCenter + Offset(sx, sy), strokeWidth = 1.6.dp.toPx())
                drawLine(Color.White, rCenter - Offset(-sy, sx), rCenter + Offset(-sy, sx), strokeWidth = 1.6.dp.toPx())
            }
            // Signature Red Collar with 3D Shiny Gold Bell around the neck + Gold Chain
            val collarY = torsoCenterY - torsoRy * 0.72f
            drawArc(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFFB71C1C), Color(0xFFFF1744), Color(0xFFD50000))
                ),
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(torsoCenterX - torsoRx * 0.62f, collarY - torsoRy * 0.22f),
                size = Size(torsoRx * 1.24f, torsoRy * 0.48f),
                style = Stroke(width = (u * 0.028f).coerceAtLeast(2.4f), cap = StrokeCap.Round)
            )
            // 3D Shiny Gold Bell with jingle slot & highlight
            val bellJingleX = if (isDancingActive) sin(t * 9.5f) * (u * 0.010f) else 0f
            val bellCenter = Offset(torsoCenterX + bellJingleX, collarY + torsoRy * 0.26f)
            val bellR = u * 0.034f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFF59D), Color(0xFFFFD600), Color(0xFFE65100)),
                    center = Offset(bellCenter.x - bellR * 0.3f, bellCenter.y - bellR * 0.3f),
                    radius = bellR * 1.3f
                ),
                radius = bellR,
                center = bellCenter
            )
            // Bell horizontal rim & dark acoustic slot
            drawLine(
                color = Color(0xFF5D4037),
                start = Offset(bellCenter.x - bellR * 0.55f, bellCenter.y + bellR * 0.22f),
                end = Offset(bellCenter.x + bellR * 0.55f, bellCenter.y + bellR * 0.22f),
                strokeWidth = (u * 0.008f).coerceAtLeast(1.2f),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = Color(0xFF3E2723),
                radius = bellR * 0.22f,
                center = Offset(bellCenter.x, bellCenter.y + bellR * 0.42f)
            )
        }

        // =========================================================================
        // 7. TWO-SEGMENT 3D ARTICULATED ARMS, ELBOWS & PLUSH PAWS WITH PINK TOE BEANS
        //    - Swings energetically up & down when song is playing (`isDancingActive`)
        //    - Rests calmly at sides with gentle breathing when paused/stopped!
        // =========================================================================
        val leftShoulder = Offset(torsoCenterX - torsoRx * 0.82f, torsoCenterY - torsoRy * 0.34f)
        val rightShoulder = Offset(torsoCenterX + torsoRx * 0.82f, torsoCenterY - torsoRy * 0.34f)

        val leftWaveOsc = if (isDancingActive) sin(t * 5.2f) else sin(t * 1.6f) * 0.08f
        val rightWaveOsc = if (isDancingActive) cos(t * 5.2f) else cos(t * 1.6f) * 0.08f

        val leftArmAngle = when {
            !isDancingActive -> 0.68f + leftWaveOsc
            holdsDeck && airborneFactor < 0.45f -> 0.42f + leftWaveOsc * 0.18f
            activeStyle == MascotDanceMode.BHANGRA_GROOVE -> -1.15f + leftWaveOsc * 0.52f - airborneFactor * 0.35f
            else -> -0.35f - airborneFactor * 1.15f + totalCrouch * 0.85f + leftWaveOsc * 0.55f
        }
        val rightArmAngle = when {
            !isDancingActive -> 0.68f - rightWaveOsc
            holdsDeck && airborneFactor < 0.45f -> 0.42f + rightWaveOsc * 0.18f
            activeStyle == MascotDanceMode.BHANGRA_GROOVE -> -1.15f - rightWaveOsc * 0.52f - airborneFactor * 0.35f
            else -> -0.35f - airborneFactor * 1.15f + totalCrouch * 0.85f - rightWaveOsc * 0.55f
        }

        val upperArmLen = u * 0.095f
        val forearmLen = u * 0.095f
        val armThick = u * 0.068f

        val leftElbow = Offset(
            x = leftShoulder.x - cos(leftArmAngle) * upperArmLen,
            y = leftShoulder.y + sin(leftArmAngle) * upperArmLen
        )
        val leftForearmAngle = leftArmAngle - 0.38f - airborneFactor * 0.35f
        val leftPaw = Offset(
            x = leftElbow.x - cos(leftForearmAngle) * forearmLen,
            y = leftElbow.y + sin(leftForearmAngle) * forearmLen
        )

        val rightElbow = Offset(
            x = rightShoulder.x + cos(rightArmAngle) * upperArmLen,
            y = rightShoulder.y + sin(rightArmAngle) * upperArmLen
        )
        val rightForearmAngle = rightArmAngle - 0.38f - airborneFactor * 0.35f
        val rightPaw = Offset(
            x = rightElbow.x + cos(rightForearmAngle) * forearmLen,
            y = rightElbow.y + sin(rightForearmAngle) * forearmLen
        )

        fun draw3DHandPaw(pawCenter: Offset, isRaisedHigh: Boolean) {
            val pawR = u * 0.046f
            drawPlush3DOval(
                cx = pawCenter.x,
                cy = pawCenter.y,
                rx = pawR,
                ry = pawR * 0.94f,
                hiColor = limbLight,
                midColor = limbFur,
                loColor = limbDark,
                furTuftCount = if (isPenguin) 0 else 6,
                tuftSize = u * 0.009f
            )
            if (!isChick && !isPenguin) {
                // Cute pink plush palm pad & 3 toe beans visible when waving/cheering!
                val beanColor = Color(0xFFFF80AB)
                drawCircle(
                    color = beanColor.copy(alpha = if (isRaisedHigh) 0.95f else 0.80f),
                    radius = pawR * 0.40f,
                    center = Offset(pawCenter.x, pawCenter.y + pawR * 0.10f)
                )
                for (b in -1..1) {
                    drawCircle(
                        color = beanColor,
                        radius = pawR * 0.17f,
                        center = Offset(
                            pawCenter.x + b * pawR * 0.38f,
                            pawCenter.y - pawR * 0.36f
                        )
                    )
                }
            }
        }

        drawPlushLimbSegment(leftShoulder, leftElbow, armThick)
        drawPlushLimbSegment(leftElbow, leftPaw, armThick * 0.92f)
        draw3DHandPaw(leftPaw, isRaisedHigh = leftPaw.y < leftShoulder.y)

        drawPlushLimbSegment(rightShoulder, rightElbow, armThick)
        drawPlushLimbSegment(rightElbow, rightPaw, armThick * 0.92f)
        draw3DHandPaw(rightPaw, isRaisedHigh = rightPaw.y < rightShoulder.y)

        // =========================================================================
        // 8. 3D SPHERICAL HEAD, BREED-SPECIFIC EARS / MANE & 3D DJ HEADPHONES
        // =========================================================================
        val headTilt = sin(t * 2.8f) * (0.14f * energyMult)
        val headTurnX = (sin(t * 1.9f) * u * 0.024f * energyMult) + (if (isActivelyWalking) walkDir * u * 0.015f else 0f)
        val headNodY = (-airborneFactor * 0.025f + totalCrouch * 0.032f) * u

        val headCenterX = torsoCenterX + headTurnX + sin(headTilt) * (u * 0.025f)
        val headCenterY = torsoCenterY - torsoRy * 0.88f - u * 0.115f + headNodY
        val headRx = u * 0.215f * (1f - airborneFactor * 0.05f + totalCrouch * 0.08f)
        val headRy = u * 0.195f * (1f + airborneFactor * 0.06f - totalCrouch * 0.07f)

        // Delayed secondary spring for floppy ears & mane
        val earSpring = sin(jumpCycle - 0.85f) * energyMult

        // --- A. 18-LOBE 3D VOLUMETRIC LION MANE (For Golden Lion DJ & Cyber Neon Lion) ---
        if (isLion) {
            val maneHi = lighten(breed.earColor, 0.28f)
            val maneMid = breed.earColor
            val maneLo = darken(breed.earColor, 0.42f)
            val lobeCount = 18
            val maneRx = headRx * 1.32f
            val maneRy = headRy * 1.32f
            for (i in 0 until lobeCount) {
                val ang = (i.toFloat() / lobeCount) * 6.2831855f + sin(t * 3.2f) * 0.04f
                val pulseR = 1f + sin(t * 5.5f + i) * 0.04f + airborneFactor * 0.05f
                val lx = headCenterX + cos(ang) * maneRx * pulseR
                val ly = headCenterY + sin(ang) * maneRy * pulseR - earSpring * u * 0.012f
                drawPlush3DOval(
                    cx = lx,
                    cy = ly,
                    rx = u * 0.064f,
                    ry = u * 0.064f,
                    hiColor = maneHi,
                    midColor = maneMid,
                    loColor = maneLo
                )
            }
        }

        // --- B. BREED-SPECIFIC 3D EARS (Bunny Long 2-Joint Floppy Ears, Teddy/Panda Round Ears, Chick Feathers) ---
        if (isBunny) {
            // Long 2-segment floppy plush bunny ears that bounce & bend in mid-air!
            val earFlop = earSpring * 0.38f - airborneFactor * 0.25f
            listOf(-1f, 1f).forEach { side ->
                val earBase = Offset(headCenterX + side * headRx * 0.46f, headCenterY - headRy * 0.78f)
                val earMid = Offset(
                    earBase.x + side * u * (0.035f + earFlop * 0.03f),
                    earBase.y - u * (0.115f + airborneFactor * 0.025f)
                )
                val earTip = Offset(
                    earMid.x + side * u * (0.055f + earFlop * 0.06f),
                    earMid.y - u * 0.065f + abs(earFlop) * u * 0.04f
                )
                drawPlushLimbSegment(earBase, earMid, u * 0.072f, lightFur, mainFur, darkFur)
                drawPlushLimbSegment(earMid, earTip, u * 0.064f, lightFur, mainFur, darkFur)
                // Pink inner ear canal
                drawLine(
                    color = breed.earColor,
                    start = earBase + Offset(0f, -u * 0.015f),
                    end = earMid + Offset(side * u * 0.01f, -u * 0.01f),
                    strokeWidth = u * 0.032f,
                    cap = StrokeCap.Round
                )
            }
        } else if (isChick) {
            // 3 Bouncy Top Crown Feathers for Baby Chick & Rockstar Chick DJ
            for (f in -1..1) {
                val fx = headCenterX + f * u * 0.034f + sin(t * 5f + f) * u * 0.012f
                val fy = headCenterY - headRy * 1.05f - (1 - abs(f)) * u * 0.025f + earSpring * u * 0.018f
                drawPlush3DOval(
                    cx = fx,
                    cy = fy,
                    rx = u * 0.024f,
                    ry = u * 0.048f,
                    hiColor = lightFur,
                    midColor = mainFur,
                    loColor = darkFur
                )
            }
        } else if (isHusky) {
            // Pointed 3D Siberian Husky Ears with White Inner Fur Triangles
            val earBounceY = earSpring * u * 0.018f
            listOf(-1f, 1f).forEach { side ->
                val baseInner = Offset(headCenterX + side * headRx * 0.30f, headCenterY - headRy * 0.78f)
                val baseOuter = Offset(headCenterX + side * headRx * 0.82f, headCenterY - headRy * 0.48f)
                val tip = Offset(
                    headCenterX + side * headRx * 0.68f,
                    headCenterY - headRy * 1.34f + earBounceY * side
                )
                val outerEarPath = Path().apply {
                    moveTo(baseInner.x, baseInner.y)
                    lineTo(tip.x, tip.y)
                    lineTo(baseOuter.x, baseOuter.y)
                    close()
                }
                drawPath(
                    path = outerEarPath,
                    brush = Brush.verticalGradient(
                        listOf(lightFur, mainFur, darkFur),
                        startY = tip.y,
                        endY = baseOuter.y
                    )
                )
                val innerEarPath = Path().apply {
                    moveTo(baseInner.x + side * u * 0.018f, baseInner.y - u * 0.008f)
                    lineTo(tip.x - side * u * 0.008f, tip.y + u * 0.032f)
                    lineTo(baseOuter.x - side * u * 0.022f, baseOuter.y - u * 0.012f)
                    close()
                }
                drawPath(path = innerEarPath, color = Color(0xFFFFE4EC))
            }
        } else if (!isPenguin && !isPuppy) {
            // Round 3D Teddy / Panda / Lion Plush Ears
            val earBounceY = earSpring * u * 0.022f
            listOf(-1f, 1f).forEach { side ->
                val ex = headCenterX + side * headRx * 0.78f
                val ey = headCenterY - headRy * 0.72f + earBounceY * side
                val earR = u * 0.074f
                drawPlush3DOval(
                    cx = ex,
                    cy = ey,
                    rx = earR,
                    ry = earR,
                    hiColor = limbLight,
                    midColor = limbFur,
                    loColor = limbDark,
                    furTuftCount = 8,
                    tuftSize = u * 0.011f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(lighten(breed.earColor, 0.25f), breed.earColor, darken(breed.earColor, 0.25f)),
                        center = Offset(ex, ey),
                        radius = earR * 0.65f
                    ),
                    radius = earR * 0.56f,
                    center = Offset(ex, ey)
                )
            }
        }

        // --- C. MAIN 3D SPHERICAL PLUSH HEAD ---
        val headFurMain = if (isPanda) Color(0xFFFDFBF7) else mainFur
        val headFurHi = if (isPanda) Color.White else lightFur
        val headFurLo = if (isPanda) Color(0xFFD7DCE4) else darkFur

        drawPlush3DOval(
            cx = headCenterX,
            cy = headCenterY,
            rx = headRx,
            ry = headRy,
            hiColor = headFurHi,
            midColor = headFurMain,
            loColor = headFurLo,
            furTuftCount = 20,
            tuftSize = u * 0.016f
        )

        // Penguin White Heart-Shaped Facial Mask
        if (isPenguin) {
            drawOval(
                color = Color(0xFFFDFBF7),
                topLeft = Offset(headCenterX - headRx * 0.78f, headCenterY - headRy * 0.62f),
                size = Size(headRx * 1.56f, headRy * 1.38f)
            )
        }

        // Puppy Hanging Floppy 3D Ears (Swing outward on jumps!)
        if (isPuppy) {
            val earSwingOut = (airborneFactor * 0.35f + abs(earSpring) * 0.22f) * u * 0.08f
            listOf(-1f, 1f).forEach { side ->
                val earTop = Offset(headCenterX + side * headRx * 0.82f, headCenterY - headRy * 0.32f)
                val earBot = Offset(
                    headCenterX + side * (headRx * 0.98f + earSwingOut),
                    headCenterY + headRy * 0.52f - airborneFactor * u * 0.04f
                )
                drawPlushLimbSegment(
                    p1 = earTop,
                    p2 = earBot,
                    thickness = u * 0.076f,
                    hi = lighten(breed.earColor, 0.22f),
                    mid = breed.earColor,
                    lo = darken(breed.earColor, 0.35f)
                )
            }
        }

        // --- D. 3D STUDIO DJ HEADPHONES (Arc Headband + Bass-Thumping RGB Earcups) ---
        val earcupPulse = (if (isMusicPlaying) abs(sin(t * 6.2f)) * 0.022f else 0.008f) * u
        // Headband arc over the top of the head
        drawArc(
            color = Color(0xFF1A2234),
            startAngle = 185f,
            sweepAngle = 170f,
            useCenter = false,
            topLeft = Offset(headCenterX - headRx * 1.06f, headCenterY - headRy * 1.08f),
            size = Size(headRx * 2.12f, headRy * 1.85f),
            style = Stroke(width = (u * 0.036f).coerceAtLeast(3f), cap = StrokeCap.Round)
        )
        drawArc(
            color = accentColor,
            startAngle = 195f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = Offset(headCenterX - headRx * 1.06f, headCenterY - headRy * 1.08f),
            size = Size(headRx * 2.12f, headRy * 1.85f),
            style = Stroke(width = (u * 0.014f).coerceAtLeast(1.5f), cap = StrokeCap.Round)
        )

        // Left & Right 3D Earcups with glowing neon rings
        listOf(-1f, 1f).forEach { side ->
            val cupX = headCenterX + side * (headRx * 1.02f + earcupPulse)
            val cupY = headCenterY + headRy * 0.02f
            val cupRx = u * 0.044f
            val cupRy = u * 0.074f
            // Inner cushion
            drawOval(
                color = Color(0xFF0E1320),
                topLeft = Offset(cupX - cupRx * 1.15f, cupY - cupRy * 1.05f),
                size = Size(cupRx * 2.3f, cupRy * 2.1f)
            )
            // Outer 3D metallic shell
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(lighten(accentColor, 0.4f), accentColor, Color(0xFF121A2B)),
                    center = Offset(cupX - side * cupRx * 0.2f, cupY - cupRy * 0.3f),
                    radius = cupRy * 1.3f
                ),
                topLeft = Offset(cupX - cupRx, cupY - cupRy),
                size = Size(cupRx * 2f, cupRy * 2f)
            )
            // Glowing RGB ring on earcup
            drawOval(
                color = Color.White.copy(alpha = 0.85f),
                topLeft = Offset(cupX - cupRx * 0.72f, cupY - cupRy * 0.72f),
                size = Size(cupRx * 1.44f, cupRy * 1.44f),
                style = Stroke(width = (u * 0.010f).coerceAtLeast(1.2f))
            )
        }

        // Cool Bucket Hat with Devil Horns for Mixtape / Boombox Breeds
        if (holdsDeck) {
            val hatTopY = headCenterY - headRy * 1.06f
            val hatBrimY = headCenterY - headRy * 0.46f
            val hatPath = Path().apply {
                moveTo(headCenterX - headRx * 0.96f, hatBrimY)
                quadraticTo(headCenterX, hatBrimY - u * 0.035f, headCenterX + headRx * 0.96f, hatBrimY)
                lineTo(headCenterX + headRx * 0.68f, hatTopY)
                quadraticTo(headCenterX, hatTopY - u * 0.025f, headCenterX - headRx * 0.68f, hatTopY)
                close()
            }
            drawPath(
                path = hatPath,
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF2B3548), Color(0xFF121722)),
                    startY = hatTopY,
                    endY = hatBrimY
                )
            )
            drawPath(
                path = hatPath,
                color = accentColor,
                style = Stroke(width = (u * 0.012f).coerceAtLeast(1.4f))
            )
            // Cute neon devil horns on top of bucket hat
            listOf(-1f, 1f).forEach { side ->
                val hornPath = Path().apply {
                    val hx = headCenterX + side * headRx * 0.45f
                    moveTo(hx - side * u * 0.028f, hatTopY + u * 0.01f)
                    quadraticTo(hx + side * u * 0.02f, hatTopY - u * 0.065f, hx + side * u * 0.048f, hatTopY - u * 0.075f)
                    quadraticTo(hx + side * u * 0.035f, hatTopY - u * 0.02f, hx + side * u * 0.025f, hatTopY + u * 0.015f)
                    close()
                }
                drawPath(hornPath, color = accentColor)
            }
        }

        // =========================================================================
        // 9. DEEP 3D GALAXY EYES, SMOOTH EYELID BLINKS, KAWAII CHEEKS & SINGING MOUTH
        // =========================================================================
        val eyeY = headCenterY - headRy * 0.06f + headNodY * 0.3f
        val leftEyeCenter = Offset(headCenterX - headRx * 0.38f + headTurnX * 0.35f, eyeY)
        val rightEyeCenter = Offset(headCenterX + headRx * 0.38f + headTurnX * 0.35f, eyeY)
        val eyeR = u * 0.046f

        // Panda Dark Eye Patches behind the eyes
        if (isPanda) {
            listOf(leftEyeCenter, rightEyeCenter).forEachIndexed { idx, ec ->
                drawOval(
                    color = Color(0xFF1A1E24),
                    topLeft = Offset(ec.x - eyeR * 1.38f, ec.y - eyeR * 1.25f),
                    size = Size(eyeR * 2.76f, eyeR * 2.50f)
                )
            }
        }

        // Rosy Kawaii Cheek Blush
        val blushAlpha = (0.42f + airborneFactor * 0.28f).coerceIn(0.32f, 0.72f)
        listOf(-1f, 1f).forEach { side ->
            val cx = headCenterX + side * headRx * 0.60f + headTurnX * 0.3f
            val cy = eyeY + headRy * 0.30f
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFF4081).copy(alpha = blushAlpha), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = u * 0.048f
                ),
                topLeft = Offset(cx - u * 0.048f, cy - u * 0.030f),
                size = Size(u * 0.096f, u * 0.060f)
            )
        }

        // Eye Blink & Happy ^ ^ Jump Expression
        val blinkPhase = (t * 1.6f) % 5.0f
        val isHappyJumpArc = airborneFactor > 0.78f || happyJumpTicks > 22
        val blinkAmount = when {
            blinkPhase > 4.65f -> sin(((blinkPhase - 4.65f) / 0.35f) * 3.1415927f).coerceIn(0f, 1f)
            else -> 0f
        }

        if (isHappyJumpArc) {
            // Joyful ^ ^ curved anime eyes at the peak of high jumps!
            listOf(leftEyeCenter, rightEyeCenter).forEach { ec ->
                drawArc(
                    color = Color(0xFF1A110B),
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(ec.x - eyeR * 0.95f, ec.y - eyeR * 0.65f),
                    size = Size(eyeR * 1.90f, eyeR * 1.40f),
                    style = Stroke(width = (u * 0.022f).coerceAtLeast(2.4f), cap = StrokeCap.Round)
                )
            }
        } else {
            val gazeX = sin(t * 2.4f) * eyeR * 0.24f + headTurnX * 0.2f
            val gazeY = (-airborneFactor * 0.22f + totalCrouch * 0.16f) * eyeR

            listOf(leftEyeCenter, rightEyeCenter).forEach { ec ->
                // 1. Outer dark plush eye socket rim
                drawCircle(color = Color(0xFF0E0906), radius = eyeR * 1.06f, center = ec)
                // 2. Deep 3D galaxy iris gradient
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF3A2218),
                            Color(0xFF1B0F0A),
                            accentColor.copy(alpha = 0.65f)
                        ),
                        center = Offset(ec.x + gazeX, ec.y + gazeY + eyeR * 0.25f),
                        radius = eyeR
                    ),
                    radius = eyeR * 0.96f,
                    center = ec
                )
                // 3. Center dark pupil
                drawCircle(
                    color = Color(0xFF070504),
                    radius = eyeR * 0.56f,
                    center = Offset(ec.x + gazeX * 0.8f, ec.y + gazeY * 0.8f)
                )
                // 4. Primary big glossy white star catchlight (top-left)
                drawCircle(
                    color = Color.White,
                    radius = eyeR * 0.36f,
                    center = Offset(ec.x - eyeR * 0.28f + gazeX, ec.y - eyeR * 0.28f + gazeY)
                )
                // 5. Secondary neon sparkle catchlight (bottom-right)
                drawCircle(
                    color = Color.White.copy(alpha = 0.88f),
                    radius = eyeR * 0.17f,
                    center = Offset(ec.x + eyeR * 0.30f + gazeX, ec.y + eyeR * 0.28f + gazeY)
                )
                // 6. Smooth progressive fur eyelid blink from top
                if (blinkAmount > 0.05f) {
                    drawArc(
                        color = headFurMain,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(ec.x - eyeR * 1.1f, ec.y - eyeR * 1.1f),
                        size = Size(eyeR * 2.2f, eyeR * 2.2f * blinkAmount)
                    )
                }
            }
            // Expressive 3D eyebrows that lift during jumps
            val browLift = -airborneFactor * u * 0.018f
            listOf(-1f, 1f).forEach { side ->
                val ec = if (side < 0f) leftEyeCenter else rightEyeCenter
                drawArc(
                    color = darken(headFurMain, 0.55f),
                    startAngle = if (side < 0f) 210f else 230f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(ec.x - eyeR * 0.85f, ec.y - eyeR * 1.35f + browLift),
                    size = Size(eyeR * 1.70f, eyeR * 0.85f),
                    style = Stroke(width = (u * 0.016f).coerceAtLeast(1.8f), cap = StrokeCap.Round)
                )
            }
        }

        // --- E. 3D PLUSH MUZZLE, NOSE / BEAK & BEAT-SYNCED SINGING MOUTH ---
        val muzzleCenter = Offset(headCenterX + headTurnX * 0.45f, headCenterY + headRy * 0.30f + headNodY * 0.4f)
        val mouthOpen = if (isMusicPlaying || happyJumpTicks > 0) {
            (abs(sin(t * 6.4f)) * 0.85f + 0.15f + airborneFactor * 0.25f).coerceIn(0.15f, 1.10f)
        } else {
            (airborneFactor * 0.75f + abs(sin(t * 3.2f)) * 0.22f).coerceIn(0.10f, 0.85f)
        }

        if (isChick || isPenguin) {
            // 3D Orange Beak that opens to sing!
            val beakW = u * 0.068f
            val beakH = u * 0.046f
            // Open mouth cavity inside beak
            if (mouthOpen > 0.20f) {
                drawOval(
                    color = Color(0xFF4A0E1C),
                    topLeft = Offset(muzzleCenter.x - beakW * 0.55f, muzzleCenter.y - beakH * 0.2f),
                    size = Size(beakW * 1.1f, beakH * (0.65f + mouthOpen * 0.65f))
                )
                drawOval(
                    color = Color(0xFFFF6E8E),
                    topLeft = Offset(muzzleCenter.x - beakW * 0.38f, muzzleCenter.y + beakH * 0.05f),
                    size = Size(beakW * 0.76f, beakH * 0.48f * mouthOpen)
                )
            }
            // Upper 3D orange beak
            val upperBeak = Path().apply {
                moveTo(muzzleCenter.x - beakW * 0.75f, muzzleCenter.y)
                quadraticTo(muzzleCenter.x, muzzleCenter.y - beakH * 0.95f, muzzleCenter.x + beakW * 0.75f, muzzleCenter.y)
                quadraticTo(muzzleCenter.x, muzzleCenter.y + beakH * 0.35f, muzzleCenter.x - beakW * 0.75f, muzzleCenter.y)
                close()
            }
            drawPath(
                path = upperBeak,
                brush = Brush.verticalGradient(
                    listOf(Color(0xFFFFD54F), Color(0xFFFF8F00)),
                    startY = muzzleCenter.y - beakH,
                    endY = muzzleCenter.y + beakH * 0.4f
                )
            )
        } else {
            // 3D Rounded Plush Snout / Muzzle
            val snoutRx = headRx * 0.38f
            val snoutRy = headRy * 0.29f
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, bellyColor, darken(bellyColor, 0.20f)),
                    center = Offset(muzzleCenter.x - snoutRx * 0.2f, muzzleCenter.y - snoutRy * 0.3f),
                    radius = snoutRx * 1.3f
                ),
                topLeft = Offset(muzzleCenter.x - snoutRx, muzzleCenter.y - snoutRy * 0.85f),
                size = Size(snoutRx * 2f, snoutRy * 1.85f + mouthOpen * u * 0.022f)
            )

            // Animated Singing / Cheering 3D Mouth Cavity + Happy Pink Tongue when dancing
            val mouthY = muzzleCenter.y + snoutRy * 0.22f
            val mouthW = snoutRx * (0.86f + mouthOpen * 0.25f)
            val mouthH = (snoutRy * 0.88f * mouthOpen).coerceAtLeast(u * 0.016f)

            if (isDancingActive) {
                drawOval(
                    color = Color(0xFF3E0A16),
                    topLeft = Offset(muzzleCenter.x - mouthW * 0.5f, mouthY - mouthH * 0.35f),
                    size = Size(mouthW, mouthH)
                )
                // Happy Pink 3D Tongue hanging out and bouncing with the music beat!
                val tongueBounce = abs(sin(t * 7.2f)) * u * 0.016f
                drawOval(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFFFF80AB), Color(0xFFFF4081))
                    ),
                    topLeft = Offset(muzzleCenter.x - mouthW * 0.34f, mouthY - mouthH * 0.05f),
                    size = Size(mouthW * 0.68f, mouthH * 0.85f + tongueBounce)
                )
            } else {
                // Calm cute smiling mouth when paused/idle
                drawArc(
                    color = Color(0xFF3E2723),
                    startAngle = 20f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(muzzleCenter.x - snoutRx * 0.42f, mouthY - u * 0.018f),
                    size = Size(snoutRx * 0.84f, u * 0.032f),
                    style = Stroke(width = (u * 0.014f).coerceAtLeast(1.6f), cap = StrokeCap.Round)
                )
            }
            // Cute white Bunny Buck Teeth or tiny upper smile teeth
            if (isBunny && isDancingActive) {
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(muzzleCenter.x - u * 0.018f, mouthY - mouthH * 0.32f),
                    size = Size(u * 0.017f, u * 0.022f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
                )
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(muzzleCenter.x + u * 0.001f, mouthY - mouthH * 0.32f),
                    size = Size(u * 0.017f, u * 0.022f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
                )
            }

            // Glossy 3D Plush Nose with specular highlight
            val noseCenter = Offset(muzzleCenter.x, muzzleCenter.y - snoutRy * 0.34f)
            val noseRx = snoutRx * 0.38f
            val noseRy = snoutRy * 0.28f
            val noseColor = if (isBunny) Color(0xFFFF6090) else Color(0xFF281812)
            drawOval(
                color = noseColor,
                topLeft = Offset(noseCenter.x - noseRx, noseCenter.y - noseRy),
                size = Size(noseRx * 2f, noseRy * 2f)
            )
            drawOval(
                color = Color.White.copy(alpha = 0.75f),
                topLeft = Offset(noseCenter.x - noseRx * 0.55f, noseCenter.y - noseRy * 0.65f),
                size = Size(noseRx * 0.70f, noseRy * 0.55f)
            )
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
 * Renders the 100% Real-Time 3D Volumetric Multi-Jointed Jumping Mascot at full 60fps DJ tempo
 * with zero static bitmap delay!
 */
@Composable
fun FluffyMascot3DStudioPortrait(
    breed: CompanionBreed,
    modifier: Modifier = Modifier,
    showStudioBackdrop: Boolean = true,
    animateReels: Boolean = true,
    isMusicPlaying: Boolean = true,
    customFurColor: Color? = null
) {
    val context = LocalContext.current
    var articulatedParts by remember(breed.drawableResId) {
        mutableStateOf(MascotTransparentBitmapCache.getCachedArticulatedSync(breed.drawableResId))
    }
    LaunchedEffect(breed.drawableResId) {
        if (articulatedParts == null) {
            articulatedParts = MascotTransparentBitmapCache.getOrLoadArticulatedMascot(context, breed)
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
                        (customFurColor ?: breed.primaryFur).copy(alpha = 0.32f)
                    )
                )
            } else {
                Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
            }
        ),
        contentAlignment = Alignment.Center
    ) {
        DeformingJumping3DPlushMascot(
            breed = breed,
            parts = articulatedParts,
            animTick = staggeredTick,
            walkStridePhase = staggeredTick * 1.45f,
            walkDir = 1f,
            isMusicPlaying = isMusicPlaying,
            isActivelyWalking = true,
            happyJumpTicks = if (animateReels && isMusicPlaying) 12 else 0,
            landingSquashTicks = 0,
            danceMode = MascotDanceMode.TRAMPOLINE_ACROBAT,
            auraColor = breed.headphoneAccent,
            customFurColor = customFurColor,
            drawInternalFloorShadow = true,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Interactive 100% Real-Time 3D Volumetric Multi-Jointed Plush Companion ("Gudda") Overlay with:
 * A. Beat-Synced Dancing & Calm Idle Stop (connected directly to live ExoPlayer `isMusicPlaying` state)
 * B. Multi-Bounce Gravity Physics (5 to 7 realistic bounces with `gravityAccel = 0.85f` & `bounceDamping = 0.76f`,
 *    decreasing height 100% -> 76% -> 58% -> 44% -> 33% -> 25% -> 18% -> settle + squash-and-stretch on every hit)
 * C. 2-Second Drag & Hold Over Any Song Row to Auto-Play (glowing 0%..100% circular progress ring, haptic pulse,
 *    gravity drop to floor, auto-play song, and auto-scrolling near top/bottom edges)
 * D. Double-Tap on Mascot to Immediately Pause/Stop Music ("Music Paused by Puppy/Teddy")
 * E. Character & Custom Fur Color Switcher (Golden Puppy, Fluffy Teddy Bear, Siberian Husky, Panda + all 21 Mascots)
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
    onDoubleTapPauseMusic: () -> Unit = onQuickPlayTrigger,
    onPlaySongFromMascotHover: (MediaItem) -> Unit = {},
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
    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val mascotBoxWidthPx = with(density) { (mascotSizeDp + 30.dp).toPx() }
    val mascotBoxHeightPx = with(density) { (mascotSizeDp + 48.dp).toPx() }
    val mascotSizePx = with(density) { mascotSizeDp.toPx() }
    val floorY = (screenHeightPx - mascotBoxHeightPx - with(density) { bottomClearanceDp.toPx() }).coerceAtLeast(60f)

    val breed = remember(breedName, penguinsOnlyMode) {
        val selected = CompanionBreed.entries.find {
            it.displayName.equals(breedName, ignoreCase = true) ||
                it.name.equals(breedName, ignoreCase = true) ||
                breedName.contains(it.displayName.substringBefore(" "), ignoreCase = true)
        } ?: CompanionBreed.SKY_DJ_PUPPY
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

    val prefs = remember(context) {
        context.getSharedPreferences("hybrid_mascot_custom_prefs", Context.MODE_PRIVATE)
    }
    var selectedFurPresetId by remember {
        mutableStateOf(prefs.getString("custom_fur_preset_id", MascotFurColorPreset.ORIGINAL.id) ?: MascotFurColorPreset.ORIGINAL.id)
    }
    val activeFurColorPreset = remember(selectedFurPresetId) {
        MascotFurColorPreset.fromId(selectedFurPresetId)
    }

    var overlayRootOffset by remember { mutableStateOf(Offset.Zero) }
    var posX by remember { mutableFloatStateOf(with(density) { 18.dp.toPx() }) }
    var posY by remember { mutableFloatStateOf(floorY) }
    var velX by remember { mutableFloatStateOf(0f) }
    var velY by remember { mutableFloatStateOf(0f) }
    var bounceCount by remember { mutableIntStateOf(0) }
    var walkDir by remember { mutableFloatStateOf(1f) }
    var walkStridePhase by remember { mutableFloatStateOf(0f) }
    var isActivelyWalking by remember { mutableStateOf(true) }
    var isDragging by remember { mutableStateOf(false) }
    var animTick by remember { mutableFloatStateOf(0f) }
    var happyJumpTicks by remember { mutableIntStateOf(0) }
    var flip360Ticks by remember { mutableIntStateOf(0) }
    var landingSquashTicks by remember { mutableIntStateOf(0) }
    var showMascotFeatureHub by remember { mutableStateOf(false) }

    // 2-Second Drag & Hold Over Song Row State
    var hoveredSong by remember { mutableStateOf<MediaItem?>(null) }
    var hoveredSongElapsedMs by remember { mutableLongStateOf(0L) }
    val songHoldProgress = (hoveredSongElapsedMs / 2000f).coerceIn(0f, 1f)

    LaunchedEffect(nowPlayingTitle, isMusicPlaying, breed, danceMode) {
        if (isMusicPlaying && nowPlayingTitle.isNotBlank()) {
            happyJumpTicks = 36
        } else if (!isMusicPlaying) {
            // Immediately settle dance stunts when music is paused/stopped
            happyJumpTicks = 0
        }
    }

    LaunchedEffect(floorY) {
        if (posY > floorY) posY = floorY
    }

    // =========================================================================
    // 2-SECOND DRAG & HOLD OVER ANY SONG TO AUTO-PLAY + EDGE AUTO-SCROLL LOOP
    // =========================================================================
    LaunchedEffect(isDragging) {
        if (!isDragging) {
            hoveredSong = null
            hoveredSongElapsedMs = 0L
            MascotSongDropTargetRegistry.hoveredTrackId = null
            MascotSongDropTargetRegistry.hoveredTrackTitle = ""
            MascotSongDropTargetRegistry.holdProgress = 0f
            return@LaunchedEffect
        }
        var lastCandidateId: String? = null
        while (isDragging) {
            delay(25L)
            val centerRootX = overlayRootOffset.x + posX + mascotBoxWidthPx * 0.5f
            val centerRootY = overlayRootOffset.y + posY + mascotBoxHeightPx * 0.52f

            // Auto-scroll the song list if user drags mascot near top or bottom edge
            if (centerRootY < screenHeightPx * 0.22f) {
                MascotSongDropTargetRegistry.requestAutoScroll(-16f)
            } else if (centerRootY > screenHeightPx * 0.76f) {
                MascotSongDropTargetRegistry.requestAutoScroll(16f)
            }

            val hitTrack = MascotSongDropTargetRegistry.findSongAt(centerRootX, centerRootY)
            val isHeldSteady = abs(velX) < 16f && abs(velY) < 16f

            if (hitTrack != null && isHeldSteady) {
                if (hitTrack.id == lastCandidateId) {
                    hoveredSongElapsedMs += 25L
                } else {
                    lastCandidateId = hitTrack.id
                    hoveredSongElapsedMs = 25L
                }
                hoveredSong = hitTrack
                MascotSongDropTargetRegistry.hoveredTrackId = hitTrack.id
                MascotSongDropTargetRegistry.hoveredTrackTitle = hitTrack.cleanTitle
                MascotSongDropTargetRegistry.holdProgress = (hoveredSongElapsedMs / 2000f).coerceIn(0f, 1f)

                if (hoveredSongElapsedMs >= 2000L) {
                    // 2.0 Seconds Reached! Haptic pulse + Drop mascot with multi-bounce gravity + Auto-Play Song!
                    try {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    } catch (_: Exception) {}
                    val playedViaRegistry = MascotSongDropTargetRegistry.triggerPlaySong(hitTrack)
                    if (!playedViaRegistry) {
                        onPlaySongFromMascotHover(hitTrack)
                    }
                    Toast.makeText(
                        context,
                        "🐾 ${breed.displayName} playing: ${hitTrack.cleanTitle}",
                        Toast.LENGTH_SHORT
                    ).show()
                    hoveredSong = null
                    hoveredSongElapsedMs = 0L
                    MascotSongDropTargetRegistry.hoveredTrackId = null
                    MascotSongDropTargetRegistry.hoveredTrackTitle = ""
                    MascotSongDropTargetRegistry.holdProgress = 0f

                    // Release drag so mascot drops to the floor with 5-7 realistic bounces!
                    isDragging = false
                    bounceCount = 0
                    velY = -16f
                    velX = 0f
                    happyJumpTicks = 40
                    break
                }
            } else {
                lastCandidateId = null
                hoveredSong = null
                hoveredSongElapsedMs = 0L
                MascotSongDropTargetRegistry.hoveredTrackId = null
                MascotSongDropTargetRegistry.hoveredTrackTitle = ""
                MascotSongDropTargetRegistry.holdProgress = 0f
            }
        }
    }

    // =========================================================================
    // MULTI-BOUNCE GRAVITY PHYSICS (5 TO 7 BOUNCES) & BEAT-SYNCED DANCE LOOP
    // =========================================================================
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
        val gravityAccel = 0.85f
        val bounceDamping = 0.76f

        while (true) {
            delay(20L)
            val airborneNow = posY < floorY - 1.0f || abs(velY) > 0.6f
            val hasActiveStunt = happyJumpTicks > 0 || flip360Ticks > 0 || landingSquashTicks > 0 || airborneNow || isDragging
            val speedFactor = animSpeedMultiplier.coerceIn(0.6f, 2.2f)
            animTick += if (isMusicPlaying || hasActiveStunt) 0.26f * speedFactor else 0.14f * speedFactor

            if (happyJumpTicks > 0) happyJumpTicks--
            if (flip360Ticks > 0) flip360Ticks--
            if (landingSquashTicks > 0) landingSquashTicks--

            if (!isDragging) {
                if (airborneNow) {
                    isActivelyWalking = true
                    walkStridePhase += 0.44f * speedFactor
                    velY += gravityAccel
                    posX += velX
                    posY += velY

                    // Top ceiling bounce
                    if (posY <= 18f) {
                        posY = 18f
                        velY = abs(velY) * bounceDamping
                        velX *= 0.92f
                    }

                    // Left & Right screen edge bounces when thrown diagonally
                    val maxX = (screenWidthPx - mascotSizePx - 6f).coerceAtLeast(16f)
                    if (posX <= 6f) {
                        posX = 6f
                        velX = abs(velX) * bounceDamping
                        walkDir = 1f
                    } else if (posX >= maxX) {
                        posX = maxX
                        velX = -abs(velX) * bounceDamping
                        walkDir = -1f
                    }

                    // Bottom floor impact -> 5 to 7 realistic bounces with decreasing height (76% restitution)
                    if (posY >= floorY) {
                        posY = floorY
                        landingSquashTicks = 11
                        val impactSpeed = if (bounceCount == 0 && abs(velY) < 18.5f) {
                            19.2f
                        } else {
                            abs(velY)
                        }
                        val nextUpwardSpeed = impactSpeed * bounceDamping
                        if (bounceCount < 7 && nextUpwardSpeed >= 2.1f) {
                            bounceCount++
                            velY = -nextUpwardSpeed
                            velX *= 0.84f
                        } else {
                            bounceCount = 0
                            velY = 0f
                            velX = 0f
                        }
                    }
                } else {
                    posY = floorY
                    velY = 0f
                    velX = 0f
                    bounceCount = 0

                    // Always walk left-to-right and right-to-left across the screen ("chalta Hua ho idhar se Udhar Hota Hua")
                    val shouldPatrol = (autoWalkEnabled || danceMode == MascotDanceMode.PATROL_WALK) && !showMascotFeatureHub
                    if (shouldPatrol) {
                        isActivelyWalking = true
                        val baseWalkSpeed = when {
                            danceMode == MascotDanceMode.PATROL_WALK -> 2.65f
                            isMusicPlaying -> 2.15f
                            else -> 1.75f
                        }
                        val walkStep = baseWalkSpeed * speedFactor
                        posX += walkDir * walkStep
                        walkStridePhase += (if (isMusicPlaying) 0.56f else 0.46f) * speedFactor
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
                        walkStridePhase += 0.36f * speedFactor
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                if (coords.isAttached) {
                    overlayRootOffset = coords.positionInRoot()
                }
            }
    ) {
        // =========================================================================
        // 1. 100% REAL JUMPING ("KUDNE WALA") MULTI-JOINTED 3D VOLUMETRIC GUDDA
        // =========================================================================
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        posX.roundToInt(),
                        posY.roundToInt()
                    )
                }
                .size(width = mascotSizeDp + 30.dp, height = mascotSizeDp + 48.dp)
                .pointerInput(isMusicPlaying, breed, danceMode) {
                    detectTapGestures(
                        onTap = {
                            // Launch into a 6-bounce gravity jump stunt!
                            bounceCount = 0
                            velY = -21f
                            velX = walkDir * 2.8f
                            happyJumpTicks = 44
                            flip360Ticks = 28
                        },
                        onDoubleTap = {
                            // Requirement D: Double-tap directly on Puppy/Teddy Bear stops/pauses music!
                            try {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } catch (_: Exception) {}
                            val wasPlaying = isMusicPlaying
                            onDoubleTapPauseMusic()
                            if (wasPlaying) {
                                happyJumpTicks = 0
                                flip360Ticks = 0
                                Toast.makeText(
                                    context,
                                    "🐾 Music Paused by ${breed.displayName}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                bounceCount = 0
                                velY = -20f
                                happyJumpTicks = 44
                                Toast.makeText(
                                    context,
                                    "🎵 Music Resumed by ${breed.displayName}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onLongPress = {
                            showMascotFeatureHub = !showMascotFeatureHub
                        }
                    )
                }
                .pointerInput(screenWidthPx, floorY) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            bounceCount = 0
                            velX = 0f
                            velY = 0f
                            happyJumpTicks = 22
                        },
                        onDragEnd = {
                            isDragging = false
                            bounceCount = 0
                            // Ensure lifting & releasing always triggers 5 to 7 realistic floor bounces!
                            if (posY < floorY - 8f && abs(velY) < 6f) {
                                velY = -10.5f
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            bounceCount = 0
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            posX = (posX + dragAmount.x).coerceIn(0f, (screenWidthPx - mascotSizePx).coerceAtLeast(1f))
                            posY = (posY + dragAmount.y).coerceIn(18f, floorY)
                            velX = (dragAmount.x * 0.85f).coerceIn(-30f, 30f)
                            velY = (dragAmount.y * 1.15f).coerceIn(-36f, 28f)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // =========================================================================
            // A. FLOATING MUSICAL NOTES (♪ ♫) & GLOWING 2-SECOND SONG HOLD RING
            // =========================================================================
            Canvas(modifier = Modifier.fillMaxSize()) {
                val t = animTick
                val w = size.width
                val h = size.height
                val pulse = (sin(t * 3.2f) + 1f) * 0.5f
                val primaryAura = auraEffect.primaryColor
                val secondaryAura = auraEffect.secondaryColor

                // Glowing 2-Second Drag & Hold Circular Progress Ring (0% -> 100%)
                if (isDragging && songHoldProgress > 0.01f) {
                    val ringStroke = 5.5.dp.toPx()
                    val ringDiameter = min(w, h) * 0.90f
                    val ringTopLeft = Offset((w - ringDiameter) * 0.5f, (h - ringDiameter) * 0.52f)
                    drawArc(
                        color = Color.White.copy(alpha = 0.24f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = ringTopLeft,
                        size = Size(ringDiameter, ringDiameter),
                        style = Stroke(width = ringStroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(Color(0xFF00E676), Color(0xFF00E5FF), Color(0xFFFFD54F), Color(0xFF00E676))
                        ),
                        startAngle = -90f,
                        sweepAngle = 360f * songHoldProgress,
                        useCenter = false,
                        topLeft = ringTopLeft,
                        size = Size(ringDiameter, ringDiameter),
                        style = Stroke(width = ringStroke, cap = StrokeCap.Round)
                    )
                }

                // Floating Musical Notes (♪ ♫) & Sonic Waves when music is actively playing!
                if (isMusicPlaying || happyJumpTicks > 0) {
                    val waveExpand = pulse * (w * 0.065f)
                    drawArc(
                        color = primaryAura.copy(alpha = 0.90f),
                        startAngle = 140f,
                        sweepAngle = 80f,
                        useCenter = false,
                        topLeft = Offset(w * 0.02f - waveExpand, h * 0.22f),
                        size = Size(w * 0.22f, h * 0.26f),
                        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = secondaryAura.copy(alpha = 0.90f),
                        startAngle = -40f,
                        sweepAngle = 80f,
                        useCenter = false,
                        topLeft = Offset(w * 0.76f + waveExpand, h * 0.22f),
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
                            drawFloatingMusicNote(center = Offset(w * 0.09f, p1Y), size = w * 0.054f, color = primaryAura)
                            drawFloatingMusicNote(center = Offset(w * 0.91f, p2Y), size = w * 0.054f, color = secondaryAura)
                            drawFloatingMusicNote(center = Offset(w * (0.5f + cos(t) * 0.32f), p3Y), size = w * 0.048f, color = Color(0xFFFFD54F))
                        }
                    }
                }
            }

            // =========================================================================
            // B. 100% REAL-TIME 3D VOLUMETRIC MULTI-JOINTED JUMPING CHARACTER ("GUDDA")
            // =========================================================================
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 14.dp, bottom = 2.dp, start = 2.dp, end = 2.dp)
                    .graphicsLayer {
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.85f)
                        val cartwheelZ = if (flip360Ticks > 0) {
                            (1f - (flip360Ticks / 28f).coerceIn(0f, 1f)) * 360f
                        } else 0f
                        rotationZ = cartwheelZ
                        // Extra squash-and-stretch on every floor bounce impact!
                        val impactFactor = if (landingSquashTicks > 0) {
                            sin((landingSquashTicks / 11f) * 3.1415927f) * 0.22f
                        } else 0f
                        scaleX = 1f + impactFactor
                        scaleY = 1f - impactFactor * 0.85f
                    }
            ) {
                DeformingJumping3DPlushMascot(
                    breed = breed,
                    parts = articulatedParts,
                    animTick = animTick,
                    walkStridePhase = walkStridePhase,
                    walkDir = walkDir,
                    isMusicPlaying = isMusicPlaying,
                    isActivelyWalking = isActivelyWalking,
                    happyJumpTicks = happyJumpTicks,
                    landingSquashTicks = landingSquashTicks,
                    danceMode = danceMode,
                    auraColor = auraEffect.primaryColor,
                    customFurColor = activeFurColorPreset.furColor,
                    drawInternalFloorShadow = false,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // =========================================================================
            // C. ROYAL CROWN ACCENT (Clean 3D-compatible)
            // =========================================================================
            if (breed.wearsCrown) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val t = animTick
                    val w = size.width
                    val h = size.height
                    val crownBob = if (isMusicPlaying || happyJumpTicks > 0) -abs(sin(t * 3.6f)) * (h * 0.16f) else 0f
                    val crownPath = Path().apply {
                        moveTo(w * 0.37f, h * 0.24f + crownBob)
                        lineTo(w * 0.35f, h * 0.15f + crownBob)
                        lineTo(w * 0.44f, h * 0.20f + crownBob)
                        lineTo(w * 0.50f, h * 0.13f + crownBob)
                        lineTo(w * 0.56f, h * 0.20f + crownBob)
                        lineTo(w * 0.65f, h * 0.15f + crownBob)
                        lineTo(w * 0.63f, h * 0.24f + crownBob)
                        close()
                    }
                    drawPath(crownPath, color = Color(0xFFFFD700))
                    drawPath(crownPath, color = Color(0xFFFF6F00), style = Stroke(width = 1.2.dp.toPx()))
                }
            }

            // =========================================================================
            // D. INTELLIGENT CONTEXT-AWARE DJ SPEECH BUBBLE / 2s SONG HOLD BADGE
            // =========================================================================
            val intelligentBubbleMessage = remember(
                isDragging,
                hoveredSong?.id,
                 (songHoldProgress * 20).toInt(),
                bounceCount,
                isMusicPlaying,
                nowPlayingTitle,
                danceMode,
                breed,
                (animTick / 16f).toInt(),
                happyJumpTicks > 0
            ) {
                when {
                    isDragging && hoveredSong != null -> {
                        "🎵 Hold ${(songHoldProgress * 100).toInt()}%: ${hoveredSong!!.cleanTitle.take(12)}"
                    }
                    bounceCount > 0 -> "🏀 Bounce #$bounceCount • ${breed.hatBadgeText}"
                    happyJumpTicks > 15 -> "🔥 ${breed.hatBadgeText} Jump! 🎛️"
                    isMusicPlaying -> {
                        val phaseIdx = ((animTick / 16f).toInt().coerceAtLeast(0)) % 4
                        when (phaseIdx) {
                            0 -> if (nowPlayingTitle.isNotBlank()) "♪ ${nowPlayingTitle.take(15)} ♫" else "${danceMode.shortBadge} • 🎛️ Studio"
                            1 -> "🕺 ${breed.hatBadgeText} Dancing! 🎛️"
                            2 -> "⏸️ 2x Tap = Pause Music"
                            else -> "🎛️ Tap Here for 21 Mascots"
                        }
                    }
                    else -> "🐾 Idle • 🎛️ Studio (2x Tap Play)"
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
                        when {
                            isDragging && hoveredSong != null -> Color(0xFF00E676)
                            isMusicPlaying -> breed.headphoneAccent
                            else -> danceMode.accentColor.copy(alpha = 0.85f)
                        },
                        RoundedCornerShape(50)
                    )
                    .clickable { showMascotFeatureHub = !showMascotFeatureHub }
                    .padding(horizontal = 8.dp, vertical = 2.5.dp)
            ) {
                Text(
                    text = intelligentBubbleMessage,
                    color = when {
                        isDragging && hoveredSong != null -> Color(0xFF00E676)
                        isMusicPlaying -> breed.headphoneAccent
                        else -> danceMode.accentColor
                    },
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

                // 3. Featured 4 Hero Switcher (Golden Puppy, Fluffy Teddy Bear, Siberian Husky, Panda)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    CompanionBreed.FEATURED_4_HEROES.forEach { hero ->
                        val isCurrent = hero == breed
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isCurrent) Color(0xFFFFD54F)
                                    else Color(0xFFFFD54F).copy(alpha = 0.14f)
                                )
                                .border(
                                    width = 1.2.dp,
                                    color = if (isCurrent) Color.White else Color(0xFFFFD54F).copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    onSelectBreed(hero.displayName)
                                    bounceCount = 0
                                    velY = -18f
                                    happyJumpTicks = 36
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "⭐ ${hero.displayName}",
                                color = if (isCurrent) Color.Black else Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.5.sp
                            )
                        }
                    }
                }

                // 3B. Custom Fur Color Switcher Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    MascotFurColorPreset.entries.forEach { preset ->
                        val isSelectedFur = preset == activeFurColorPreset
                        val chipColor = preset.furColor ?: breed.primaryFur
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelectedFur) chipColor
                                    else chipColor.copy(alpha = 0.18f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelectedFur) Color.White else chipColor.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    selectedFurPresetId = preset.id
                                    prefs.edit().putString("custom_fur_preset_id", preset.id).apply()
                                    happyJumpTicks = 28
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = preset.label,
                                color = if (isSelectedFur) Color.Black else Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // 3C. Instant All 21-Mascot Switcher Row
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
