package com.example.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
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
    var isMascotDragging: Boolean = false
    var isMascotDraggingState by mutableStateOf(false)

    @Volatile
    private var autoScrollHandler: ((Float) -> Unit)? = null

    @Volatile
    private var playSongHandler: ((MediaItem) -> Unit)? = null

    fun setDraggingActive(active: Boolean) {
        isMascotDragging = active
        isMascotDraggingState = active
        if (!active) {
            hoveredTrackId = null
            hoveredTrackTitle = ""
            holdProgress = 0f
        }
    }

    fun registerSongRow(track: MediaItem, boundsInRoot: androidx.compose.ui.geometry.Rect) {
        if (!isMascotDragging) return
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

fun Modifier.mascotSongDropTarget(item: MediaItem): Modifier = composed {
    val dragActive = MascotSongDropTargetRegistry.isMascotDraggingState
    if (!dragActive) return@composed this
    DisposableEffect(item.id) {
        onDispose {
            MascotSongDropTargetRegistry.unregisterSongRow(item.id)
        }
    }
    this.onGloballyPositioned { coords ->
        if (MascotSongDropTargetRegistry.isMascotDragging && coords.isAttached) {
            MascotSongDropTargetRegistry.registerSongRow(item, coords.boundsInRoot())
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

internal object MascotTransparentBitmapCache {
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

    fun getCachedArticulatedMascot(breed: CompanionBreed): ArticulatedMascotParts? =
        articulatedPartsCache[breed.drawableResId]

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

            val targetW = 300
            val targetH = ((raw.height.toFloat() / raw.width.coerceAtLeast(1)) * targetW).toInt().coerceAtLeast(300)
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

            // Extract full-coverage anatomical layers (NO clipped small ovals, NO missing pixels!)
            val torsoOnlyImg = extractAnatomicalLayer(srcPixels, w, h, AnatomicalPart.TORSO_CORE).asImageBitmap()
            val headImg = extractAnatomicalLayer(srcPixels, w, h, AnatomicalPart.HEAD_AND_EARS).asImageBitmap()
            val leftArmImg = extractAnatomicalLayer(srcPixels, w, h, AnatomicalPart.LEFT_ARM).asImageBitmap()
            val rightArmImg = extractAnatomicalLayer(srcPixels, w, h, AnatomicalPart.RIGHT_ARM).asImageBitmap()
            val leftFootImg = extractAnatomicalLayer(srcPixels, w, h, AnatomicalPart.LEFT_LEG).asImageBitmap()
            val rightFootImg = extractAnatomicalLayer(srcPixels, w, h, AnatomicalPart.RIGHT_LEG).asImageBitmap()

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

    private enum class AnatomicalPart {
        HEAD_AND_EARS,
        TORSO_CORE,
        LEFT_ARM,
        RIGHT_ARM,
        LEFT_LEG,
        RIGHT_LEG
    }

    private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun extractAnatomicalLayer(
        srcPixels: IntArray,
        w: Int,
        h: Int,
        part: AnatomicalPart
    ): Bitmap {
        val outPixels = IntArray(w * h)
        for (y in 0 until h) {
            val ny = y.toFloat() / h.toFloat()
            val rowOffset = y * w
            for (x in 0 until w) {
                val src = srcPixels[rowOffset + x]
                val srcAlpha = (src ushr 24) and 0xFF
                if (srcAlpha == 0) continue
                val nx = x.toFloat() / w.toFloat()

                // Slight downward curve at the chin/neck so the round chin stays on the head layer
                val centerDistX = abs(nx - 0.50f)
                val neckLineY = 0.485f - (centerDistX * centerDistX) * 0.22f

                val weight: Float = when (part) {
                    AnatomicalPart.HEAD_AND_EARS -> {
                        1f - smoothStep(neckLineY - 0.025f, neckLineY + 0.025f, ny)
                    }
                    AnatomicalPart.LEFT_LEG -> {
                        val topW = smoothStep(0.68f, 0.73f, ny)
                        val sideW = 1f - smoothStep(0.48f, 0.51f, nx)
                        topW * sideW
                    }
                    AnatomicalPart.RIGHT_LEG -> {
                        val topW = smoothStep(0.68f, 0.73f, ny)
                        val sideW = smoothStep(0.49f, 0.52f, nx)
                        topW * sideW
                    }
                    AnatomicalPart.LEFT_ARM -> {
                        val topW = smoothStep(0.40f, 0.45f, ny)
                        val botW = 1f - smoothStep(0.67f, 0.72f, ny)
                        val horizW = 1f - smoothStep(0.28f, 0.35f, nx)
                        topW * botW * horizW
                    }
                    AnatomicalPart.RIGHT_ARM -> {
                        val topW = smoothStep(0.40f, 0.45f, ny)
                        val botW = 1f - smoothStep(0.67f, 0.72f, ny)
                        val horizW = smoothStep(0.65f, 0.72f, nx)
                        topW * botW * horizW
                    }
                    AnatomicalPart.TORSO_CORE -> {
                        // Overlap neck (0.43..0.48) and upper hips (0.68..0.76) so rotating head & kicking legs
                        // never expose a gap, while erasing outer static arms and lower static feet!
                        val topW = smoothStep(0.42f, 0.47f, ny)
                        val botW = 1f - smoothStep(0.73f, 0.77f, ny)
                        val inArmBand = smoothStep(0.41f, 0.45f, ny) * (1f - smoothStep(0.66f, 0.71f, ny))
                        val outerLeftArm = (1f - smoothStep(0.25f, 0.31f, nx)) * inArmBand
                        val outerRightArm = smoothStep(0.69f, 0.75f, nx) * inArmBand
                        val armExclusion = (1f - outerLeftArm) * (1f - outerRightArm)
                        topW * botW * armExclusion
                    }
                }

                if (weight > 0.01f) {
                    val newAlpha = (srcAlpha * weight).roundToInt().coerceIn(0, 255)
                    if (newAlpha > 0) {
                        outPixels[rowOffset + x] = (newAlpha shl 24) or (src and 0x00FFFFFF)
                    }
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

        val minBrightness = if (isWhiteFurSubject) 242 else 218
        val maxChannelDiff = if (isWhiteFurSubject) 10 else 24

        fun isStudioWhiteBackdrop(pixel: Int, x: Int, y: Int): Boolean {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val minC = min(r, min(g, b))
            val maxC = max(r, max(g, b))
            val diff = maxC - minC

            // 1. Standard outer studio white/off-white backdrop
            if (minC >= minBrightness && diff <= maxChannelDiff) {
                return true
            }

            // 2. Bottom studio floor & contact shadow under and around the feet ("iski tangon Ke Niche Jo Hai vah Sara Kuchh Hata Do")
            if (y >= (h * 0.81f).toInt()) {
                val floorMaxDiff = if (isWhiteFurSubject) 16 else 28
                val floorMinBri = if (isWhiteFurSubject) 172 else 130
                if (diff <= floorMaxDiff && minC >= floorMinBri) {
                    return true
                }
            }

            // 3. Hard bottom floor margin below the toes (y >= 93.5% of height)
            if (y >= (h * 0.935f).toInt() && diff <= 32 && minC >= 110) {
                return true
            }

            // 4. Shaded vertical studio gap between the two legs ("iski tang bilkul clear samajhni chahie")
            if (y >= (h * 0.73f).toInt() && x in (w * 0.43f).toInt()..(w * 0.57f).toInt()) {
                val gapMaxDiff = if (isWhiteFurSubject) 15 else 26
                val gapMinBri = if (isWhiteFurSubject) 182 else 142
                if (diff <= gapMaxDiff && minC >= gapMinBri) {
                    return true
                }
            }

            // 5. Shaded studio pocket under the headphone headband arch ("Modi ki bilkul Charon taraf se Kuchh Nahin Hona chahie")
            if (y in (h * 0.08f).toInt()..(h * 0.26f).toInt() && x in (w * 0.23f).toInt()..(w * 0.77f).toInt()) {
                val archMaxDiff = if (isWhiteFurSubject) 10 else 22
                val archMinBri = if (isWhiteFurSubject) 236 else 185
                if (diff <= archMaxDiff && minC >= archMinBri) {
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
            val reqMin = if (isWhiteFurSubject) 242 else 192
            val reqDiff = if (isWhiteFurSubject) 7 else 20
            if (minC < reqMin || (maxC - minC) > reqDiff) return false
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
            return (maxLum - minLum) <= (if (isWhiteFurSubject) 4 else 10)
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

        // 2. Seed from enclosed pockets:
        //    (a) Headphone headband arch above head
        for (y in (h * 0.08f).toInt()..(h * 0.26f).toInt() step 2) {
            for (x in (w * 0.24f).toInt()..(w * 0.76f).toInt() step 2) {
                if (isFlatInteriorWhitePocket(x, y)) {
                    enqueueIfBackdrop(x, y)
                }
            }
        }
        //    (b) Gap between the two legs & under the feet
        for (y in (h * 0.74f).toInt() until h step 2) {
            for (x in (w * 0.43f).toInt()..(w * 0.57f).toInt() step 2) {
                enqueueIfBackdrop(x, y)
            }
        }
        //    (c) Pockets between arms, earcups, and torso/tail
        for (y in (h * 0.36f).toInt()..(h * 0.84f).toInt() step 2) {
            for (x in (w * 0.14f).toInt()..(w * 0.35f).toInt() step 2) {
                if (isFlatInteriorWhitePocket(x, y)) enqueueIfBackdrop(x, y)
            }
            for (x in (w * 0.65f).toInt()..(w * 0.86f).toInt() step 2) {
                if (isFlatInteriorWhitePocket(x, y)) enqueueIfBackdrop(x, y)
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

        // 3. Two-Pass White/Grey Halo Choke: removes pale anti-aliased white studio fringes around fur & limbs
        val fringeMinBri = if (isWhiteFurSubject) 235 else 182
        val fringeMaxDiff = if (isWhiteFurSubject) 10 else 28
        repeat(2) {
            val toClear = IntArray(w * h)
            var clearCount = 0
            for (y in 1 until h - 1) {
                val row = y * w
                for (x in 1 until w - 1) {
                    val idx = row + x
                    if (!visited[idx]) {
                        val touchesTransparent = visited[idx - 1] || visited[idx + 1] || visited[idx - w] || visited[idx + w]
                        if (touchesTransparent) {
                            val c = pixels[idx]
                            val r = (c shr 16) and 0xFF
                            val g = (c shr 8) and 0xFF
                            val b = c and 0xFF
                            val minC = min(r, min(g, b))
                            val maxC = max(r, max(g, b))
                            val diff = maxC - minC
                            if (minC >= fringeMinBri && diff <= fringeMaxDiff) {
                                toClear[clearCount++] = idx
                            }
                        }
                    }
                }
            }
            for (i in 0 until clearCount) {
                val idx = toClear[i]
                visited[idx] = true
                pixels[idx] = 0x00000000
            }
        }

        // 4. Clean anti-aliased edge feathering + RGB color decontamination (pulls interior fur color to the edge)
        for (y in 2 until h - 2) {
            val row = y * w
            for (x in 2 until w - 2) {
                val idx = row + x
                if (!visited[idx]) {
                    var transparentNeighbors = 0
                    if (visited[idx - 1]) transparentNeighbors++
                    if (visited[idx + 1]) transparentNeighbors++
                    if (visited[idx - w]) transparentNeighbors++
                    if (visited[idx + w]) transparentNeighbors++
                    if (transparentNeighbors > 0) {
                        var interiorColor = pixels[idx]
                        // Look 2px inward for a pure fur pixel to eliminate any residual white edge tint
                        val offsets = intArrayOf(-2, 2, -2 * w, 2 * w, -w - 1, -w + 1, w - 1, w + 1)
                        for (off in offsets) {
                            val nIdx = idx + off
                            if (nIdx in 0 until (w * h) && !visited[nIdx]) {
                                interiorColor = pixels[nIdx]
                                break
                            }
                        }
                        val r = (interiorColor shr 16) and 0xFF
                        val g = (interiorColor shr 8) and 0xFF
                        val b = interiorColor and 0xFF
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

internal data class PlushFaceRig(
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

internal fun getFaceRigForBreed(breed: CompanionBreed): PlushFaceRig = when (breed.drawableResId) {
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
 * Zero-recomposition mutable motion state holder for 60/120fps VSync mascot rendering.
 * Values are read exclusively inside Canvas draw phase, Modifier.offset { }, and Modifier.graphicsLayer { }.
 */
@Stable
class MascotLiveMotionState {
    var posX by mutableFloatStateOf(0f)
    var posY by mutableFloatStateOf(0f)
    var velX by mutableFloatStateOf(0f)
    var velY by mutableFloatStateOf(0f)
    var animTick by mutableFloatStateOf(0f)
    var walkStridePhase by mutableFloatStateOf(0f)
    var walkDir by mutableFloatStateOf(-1f)
    var isActivelyWalking by mutableStateOf(false)
    var danceTransition by mutableFloatStateOf(0f)
    var happyJumpTicks by mutableIntStateOf(0)
    var flip360Ticks by mutableIntStateOf(0)
    var landingSquashTicks by mutableIntStateOf(0)
    var tonguePopTicks by mutableIntStateOf(0)
    var isLiftedInAir by mutableStateOf(false)
    // Real-time hardware gravity & tilt telemetry (normalized roughly -1f..1f for 1g)
    var gravityNormX by mutableFloatStateOf(0f)
    var gravityNormY by mutableFloatStateOf(1f)
    var bodyRotationDeg by mutableFloatStateOf(0f)
    var impactSquashX by mutableFloatStateOf(1f)
    var impactSquashY by mutableFloatStateOf(1f)
    var isUpsideDownOrFalling by mutableStateOf(false)
    var bounceCount by mutableIntStateOf(0)
}

@Composable
fun DeformingJumping3DPlushMascot(
    breed: CompanionBreed,
    parts: ArticulatedMascotParts? = null,
    animTick: Float = 0f,
    walkStridePhase: Float = 0f,
    walkDir: Float = 1f,
    isMusicPlaying: Boolean = false,
    isActivelyWalking: Boolean = false,
    happyJumpTicks: Int = 0,
    landingSquashTicks: Int = 0,
    danceMode: MascotDanceMode = MascotDanceMode.AUTO_MIX,
    auraColor: Color = breed.headphoneAccent,
    customFurColor: Color? = null,
    drawInternalFloorShadow: Boolean = false,
    isLiftedInAir: Boolean = false,
    danceTransition: Float = if (isMusicPlaying || happyJumpTicks > 0) 1f else 0f,
    liveMotion: MascotLiveMotionState? = null,
    modifier: Modifier = Modifier
) {
    RealPlush3DAnimatedMascot(
        breed = breed,
        parts = parts,
        animTick = animTick,
        walkStridePhase = walkStridePhase,
        isMusicPlaying = isMusicPlaying,
        isActivelyWalking = isActivelyWalking,
        happyJumpTicks = happyJumpTicks,
        landingSquashTicks = landingSquashTicks,
        danceMode = danceMode,
        customFurColor = customFurColor,
        isLiftedInAir = isLiftedInAir,
        danceTransition = danceTransition,
        liveMotion = liveMotion,
        modifier = modifier
    )
    return
    @Suppress("UNREACHABLE_CODE")
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 4f || h <= 4f) return@Canvas

        // Read live VSync motion state directly in Draw Phase (0 recompositions per frame!)
        val t = liveMotion?.animTick ?: animTick
        val activeWalkStride = liveMotion?.walkStridePhase ?: walkStridePhase
        val activeWalkDir = liveMotion?.walkDir ?: walkDir
        val activeWalking = liveMotion?.isActivelyWalking ?: isActivelyWalking
        val activeHappyJump = liveMotion?.happyJumpTicks ?: happyJumpTicks
        val activeLandingSquash = liveMotion?.landingSquashTicks ?: landingSquashTicks
        val activeLifted = liveMotion?.isLiftedInAir ?: isLiftedInAir
        val activeDanceTransition = liveMotion?.danceTransition ?: danceTransition
        val activeTonguePop = liveMotion?.tonguePopTicks ?: 0
        val activeUpsideDownOrFalling = liveMotion?.isUpsideDownOrFalling ?: false
        val activeVelX = liveMotion?.velX ?: 0f
        val activeVelY = liveMotion?.velY ?: 0f
        val activeGravX = liveMotion?.gravityNormX ?: 0f
        val activeGravY = liveMotion?.gravityNormY ?: 1f

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

        // Smooth multi-step transition curve (`0f` = 100% Stationary Paused, `1f` = Full Living 3D Cartoon Dance)
        val rawDanceWeight = if (activeHappyJump > 0) {
            max(activeDanceTransition, 0.88f).coerceIn(0f, 1f)
        } else {
            activeDanceTransition.coerceIn(0f, 1f)
        }
        // Multi-step activation envelopes for fluid, staggered cartoon transition when playback starts
        val headGrooveW = (rawDanceWeight / 0.42f).coerceIn(0f, 1f)
        val armGrooveW = ((rawDanceWeight - 0.12f) / 0.52f).coerceIn(0f, 1f)
        val legDanceW = ((rawDanceWeight - 0.22f) / 0.72f).coerceIn(0f, 1f)
        val isDancingActive = rawDanceWeight > 0.005f

        val extraLandingSquash = if (activeLandingSquash > 0) sin(activeLandingSquash * 0.48f) * 0.22f else 0f
        val isPhysicsActive = activeLifted || activeUpsideDownOrFalling || abs(activeVelX) > 1.2f || abs(activeVelY) > 1.2f || activeTonguePop > 0
        val isStationaryPaused = !isDancingActive && !isPhysicsActive && extraLandingSquash <= 0.005f

        // =========================================================================
        // 1. MULTI-STEP JUMP, SQUASH & BOUNCE KINEMATICS (100% STILL WHEN PAUSED)
        // =========================================================================
        val jumpSpeed = when {
            !isDancingActive -> 1.8f
            activeStyle == MascotDanceMode.TRAMPOLINE_ACROBAT -> 4.3f
            activeStyle == MascotDanceMode.BHANGRA_GROOVE -> 4.0f
            else -> 3.8f
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

        val totalCrouch = (crouchFactor + extraLandingSquash).coerceIn(0f, 1.25f)
        val stuntBoost = if (activeHappyJump > 0) 1.22f else 1.0f

        val u = min(w, h)
        val centerX = w * 0.5f + (if (!isStationaryPaused && !activeLifted) sin(t * 2.8f) * u * 0.032f * legDanceW else 0f)
        val groundY = h * 0.94f

        val jumpLiftY = when {
            activeLifted || activeUpsideDownOrFalling -> -h * 0.048f
            isStationaryPaused -> 0f
            else -> (-airborneFactor * (h * 0.098f * stuntBoost) + totalCrouch * (h * 0.028f)) * legDanceW
        }

        val squashX = if (isStationaryPaused) 1.0f else 1.0f - airborneFactor * 0.045f + totalCrouch * 0.11f
        val stretchY = if (isStationaryPaused) 1.0f else 1.0f + airborneFactor * 0.055f - totalCrouch * 0.10f

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
        val isPuppy = breed == CompanionBreed.SKY_DJ_PUPPY || breed == CompanionBreed.LAVENDER_PUPPY_TAPE ||
            breed == CompanionBreed.HIPHOP_GOLD_PUPPY
        val isLion = breed.isLion || breed == CompanionBreed.GOLDEN_LION_DJ || breed == CompanionBreed.CYBER_NEON_LION
        val isChick = breed == CompanionBreed.YELLOW_CHICK_DJ || breed == CompanionBreed.ROCKSTAR_CHICK_DJ
        val isPenguin = breed.isPenguin || breed == CompanionBreed.ROYAL_KING_PENGUIN || breed == CompanionBreed.COSMIC_ASTRO_PENGUIN
        val holdsDeck = breed == CompanionBreed.LAVENDER_MIXTAPE_TEDDY || breed == CompanionBreed.VELVET_BOOMBOX_BEAR ||
            breed == CompanionBreed.LAVENDER_PUPPY_TAPE

        val mainFur = customFurColor ?: breed.primaryFur
        val lightFur = lighten(mainFur, 0.36f)
        val darkFur = darken(mainFur, 0.34f)
        val limbFur = if (isPanda && customFurColor == null) Color(0xFF1F242D) else mainFur
        val limbLight = if (isPanda && customFurColor == null) Color(0xFF475266) else lightFur
        val limbDark = if (isPanda && customFurColor == null) Color(0xFF0E1117) else darkFur
        val creamFur = Color(0xFFFFF8EE)
        val creamMid = Color(0xFFF3E6D3)
        val creamShadow = Color(0xFFCBB89E)
        val bellyColor = breed.muzzlePatch
        val accentColor = if (breed == CompanionBreed.SKY_DJ_PUPPY && customFurColor == null) Color(0xFFFF5722) else breed.headphoneAccent

        // Smooth Pixar-grade 3D Volumetric Oval Helper (NO bumpy edge dots!)
        fun drawCartoon3DOval(
            cx: Float,
            cy: Float,
            rx: Float,
            ry: Float,
            hiColor: Color = lightFur,
            midColor: Color = mainFur,
            loColor: Color = darkFur,
            rimHighlightAlpha: Float = 0.36f
        ) {
            val gradRadius = max(rx, ry) * 1.48f
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(hiColor, midColor, loColor),
                    center = Offset(cx - rx * 0.30f, cy - ry * 0.34f),
                    radius = gradRadius.coerceAtLeast(4f)
                ),
                topLeft = Offset(cx - rx, cy - ry),
                size = Size(rx * 2f, ry * 2f)
            )
            if (rimHighlightAlpha > 0.01f) {
                drawArc(
                    color = Color.White.copy(alpha = rimHighlightAlpha),
                    startAngle = 205f,
                    sweepAngle = 95f,
                    useCenter = false,
                    topLeft = Offset(cx - rx * 0.90f, cy - ry * 0.90f),
                    size = Size(rx * 1.80f, ry * 1.80f),
                    style = Stroke(width = (u * 0.010f).coerceAtLeast(1.1f), cap = StrokeCap.Round)
                )
            }
        }

        // Smooth 3D Volumetric Limb Capsule Helper
        fun drawCartoon3DLimb(
            p1: Offset,
            p2: Offset,
            thickness: Float,
            hi: Color = limbLight,
            mid: Color = limbFur,
            lo: Color = limbDark
        ) {
            drawLine(
                color = lo,
                start = p1,
                end = p2,
                strokeWidth = thickness * 1.06f,
                cap = StrokeCap.Round
            )
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(hi, mid, lo),
                    start = Offset(min(p1.x, p2.x) - thickness * 0.45f, min(p1.y, p2.y) - thickness * 0.45f),
                    end = Offset(max(p1.x, p2.x) + thickness * 0.45f, max(p1.y, p2.y) + thickness * 0.45f)
                ),
                start = p1,
                end = p2,
                strokeWidth = thickness,
                cap = StrokeCap.Round
            )
            val specularShift = Offset(-thickness * 0.14f, -thickness * 0.14f)
            drawLine(
                color = hi.copy(alpha = 0.58f),
                start = p1 + specularShift,
                end = p2 + specularShift,
                strokeWidth = thickness * 0.28f,
                cap = StrokeCap.Round
            )
        }

        // =========================================================================
        // 2. BODY CORE COORDINATES (ZERO SHADOW/PAD UNDER FEET — 100% CLEAR)
        // =========================================================================
        val hipSwayX = if (isStationaryPaused || activeLifted) 0f else sin(t * 4.2f) * (u * 0.034f * legDanceW)
        val torsoCenterX = centerX + hipSwayX
        val torsoCenterY = (h * 0.61f) + jumpLiftY
        val torsoRx = u * 0.168f * squashX
        val torsoRy = u * 0.155f * stretchY

        // =========================================================================
        // 3. PLAYFUL WAGGING 3D CARTOON TAIL (ONLY WHEN PLAYING, LIFTED OR FALLING)
        // =========================================================================
        val tailWagAngle = when {
            isStationaryPaused -> -0.20f
            activeLifted || activeUpsideDownOrFalling -> sin(t * 13.5f) * 0.68f + 0.25f
            else -> sin(t * 10.5f) * 0.64f * legDanceW - 0.15f
        }
        val tailBase = Offset(torsoCenterX + torsoRx * 0.72f, torsoCenterY + torsoRy * 0.42f)
        val tailTip = Offset(
            tailBase.x + cos(tailWagAngle - 0.30f) * (u * 0.115f),
            tailBase.y + sin(tailWagAngle - 0.30f) * (u * 0.090f) - u * 0.032f
        )
        if (isBunny) {
            drawCartoon3DOval(
                cx = tailBase.x + u * 0.030f,
                cy = tailBase.y,
                rx = u * 0.044f,
                ry = u * 0.042f,
                hiColor = Color.White,
                midColor = Color(0xFFF5F5F7),
                loColor = Color(0xFFC8CDD8)
            )
        } else if (isLion || isPuppy || isHusky) {
            drawCartoon3DLimb(
                p1 = tailBase,
                p2 = tailTip,
                thickness = if (isHusky) u * 0.044f else u * 0.036f,
                hi = if (isHusky) Color.White else lightFur,
                mid = mainFur,
                lo = darkFur
            )
            if (isLion || isHusky) {
                drawCartoon3DOval(
                    cx = tailTip.x,
                    cy = tailTip.y,
                    rx = u * 0.032f,
                    ry = u * 0.030f,
                    hiColor = if (isHusky) Color.White else lighten(breed.earColor, 0.25f),
                    midColor = if (isHusky) Color(0xFFECEFF1) else breed.earColor,
                    loColor = darken(breed.earColor, 0.32f)
                )
            }
        } else if (!isChick && !isPenguin) {
            drawCartoon3DOval(
                cx = tailBase.x + u * 0.022f,
                cy = tailBase.y,
                rx = u * 0.036f,
                ry = u * 0.036f,
                hiColor = lightFur,
                midColor = mainFur,
                loColor = darkFur
            )
        }

        // =========================================================================
        // 4. CRYSTAL-CLEAR 2-JOINT 3D CARTOON LEGS & FEET WITH REAL RUNNING/KICKING
        //    ("uski donon tang chal rahi ho", bicycle kick in air/upside-down!)
        // =========================================================================
        val legPhase = if (isStationaryPaused) 0f else if (activeWalking) activeWalkStride * 1.55f else t * 5.8f
        val rawLegSine = if (isStationaryPaused) 0f else sin(legPhase)
        val leftKickBack = if (isStationaryPaused || activeLifted) 0f else max(0f, -rawLegSine) * legDanceW
        val leftStepFront = if (isStationaryPaused || activeLifted) 0f else max(0f, rawLegSine) * legDanceW
        val rightKickBack = if (isStationaryPaused || activeLifted) 0f else max(0f, rawLegSine) * legDanceW
        val rightStepFront = if (isStationaryPaused || activeLifted) 0f else max(0f, -rawLegSine) * legDanceW

        val leftHip = Offset(torsoCenterX - torsoRx * 0.46f, torsoCenterY + torsoRy * 0.64f)
        val rightHip = Offset(torsoCenterX + torsoRx * 0.46f, torsoCenterY + torsoRy * 0.64f)

        val legThickness = u * 0.072f

        val leftLegScale = if (activeLifted) 0.98f else (1.0f - leftKickBack * 0.16f + leftStepFront * 0.08f)
        val rightLegScale = if (activeLifted) 0.98f else (1.0f - rightKickBack * 0.16f + rightStepFront * 0.08f)

        val flailSpeed = if (activeUpsideDownOrFalling) 15.5f else 10.5f
        val liftedFootKickY = if (activeLifted || activeUpsideDownOrFalling) sin(t * flailSpeed) * u * 0.044f else 0f
        val liftedFootKickX = if (activeLifted || activeUpsideDownOrFalling) cos(t * flailSpeed) * u * 0.028f else 0f

        val leftKnee = if (activeLifted || activeUpsideDownOrFalling) {
            Offset(leftHip.x - u * 0.056f + liftedFootKickX * 0.5f, leftHip.y + u * 0.050f + liftedFootKickY * 0.5f)
        } else {
            Offset(
                leftHip.x - u * (0.024f + totalCrouch * 0.068f - leftKickBack * 0.028f + leftStepFront * 0.026f),
                leftHip.y + u * (0.078f - totalCrouch * 0.032f - leftKickBack * 0.040f)
            )
        }
        val rightKnee = if (activeLifted || activeUpsideDownOrFalling) {
            Offset(rightHip.x + u * 0.056f - liftedFootKickX * 0.5f, rightHip.y + u * 0.050f - liftedFootKickY * 0.5f)
        } else {
            Offset(
                rightHip.x + u * (0.024f + totalCrouch * 0.068f - rightKickBack * 0.028f + rightStepFront * 0.026f),
                rightHip.y + u * (0.078f - totalCrouch * 0.032f - rightKickBack * 0.040f)
            )
        }

        val leftFoot = if (activeLifted || activeUpsideDownOrFalling) {
            Offset(leftHip.x - u * 0.072f + liftedFootKickX, leftHip.y + u * 0.120f + liftedFootKickY)
        } else {
            val liftY = (leftKickBack * 0.112f + airborneFactor * 0.036f - leftStepFront * 0.014f) * u
            val shiftX = leftKickBack * u * 0.024f - leftStepFront * u * 0.032f
            Offset(
                x = leftHip.x - u * 0.028f + shiftX,
                y = (leftHip.y + u * (0.162f - totalCrouch * 0.038f) - liftY).coerceAtMost(groundY - u * 0.046f)
            )
        }

        val rightFoot = if (activeLifted || activeUpsideDownOrFalling) {
            Offset(rightHip.x + u * 0.072f - liftedFootKickX, rightHip.y + u * 0.120f - liftedFootKickY)
        } else {
            val liftY = (rightKickBack * 0.112f + airborneFactor * 0.036f - rightStepFront * 0.014f) * u
            val shiftX = -rightKickBack * u * 0.024f + rightStepFront * u * 0.032f
            Offset(
                x = rightHip.x + u * 0.028f + shiftX,
                y = (rightHip.y + u * (0.162f - totalCrouch * 0.038f) - liftY).coerceAtMost(groundY - u * 0.046f)
            )
        }

        fun draw3DCartoonLegAndFoot(
            hip: Offset,
            knee: Offset,
            foot: Offset,
            isLeft: Boolean,
            scale: Float
        ) {
            val thighColorHi = if (isPuppy) creamFur else limbLight
            val thighColorMid = if (isPuppy) mainFur else limbFur
            val thighColorLo = if (isPuppy) darkFur else limbDark

            if (!isChick) {
                drawCartoon3DLimb(hip, knee, legThickness * scale, thighColorHi, thighColorMid, thighColorLo)
                drawCartoon3DLimb(
                    knee,
                    foot,
                    legThickness * 0.88f * scale,
                    if (isPuppy) Color.White else thighColorHi,
                    if (isPuppy) creamMid else thighColorMid,
                    if (isPuppy) creamShadow else thighColorLo
                )
            } else {
                drawCartoon3DLimb(hip, foot, u * 0.032f * scale, Color(0xFFFFB74D), Color(0xFFFF8F00), Color(0xFFE65100))
            }

            if (isChick || isPenguin) {
                drawCartoon3DOval(
                    cx = foot.x,
                    cy = foot.y,
                    rx = u * 0.056f * scale,
                    ry = u * 0.034f * scale,
                    hiColor = Color(0xFFFFCC80),
                    midColor = Color(0xFFFF9800),
                    loColor = Color(0xFFE65100)
                )
            } else {
                // Clean, crisp creamy-white 3D cartoon puppy/teddy foot!
                val footRx = u * 0.068f * scale
                val footRy = u * (if (activeLifted || activeUpsideDownOrFalling) 0.054f else 0.042f) * scale
                val footCenterX = foot.x + (if (isLeft) -u * 0.006f else u * 0.006f)
                drawCartoon3DOval(
                    cx = footCenterX,
                    cy = foot.y,
                    rx = footRx,
                    ry = footRy,
                    hiColor = Color.White,
                    midColor = if (isPanda) limbFur else creamFur,
                    loColor = if (isPanda) limbDark else creamShadow,
                    rimHighlightAlpha = 0.50f
                )
                if (activeLifted || activeUpsideDownOrFalling) {
                    // Show cute pink 3D paw pad & 3 toe beans on lifted foot sole
                    val padColor = Color(0xFF8D5540)
                    drawOval(
                        color = padColor,
                        topLeft = Offset(footCenterX - footRx * 0.40f, foot.y - footRy * 0.22f),
                        size = Size(footRx * 0.80f, footRy * 0.66f)
                    )
                    for (tIdx in -1..1) {
                        drawCircle(
                            color = padColor,
                            radius = footRx * 0.16f,
                            center = Offset(footCenterX + tIdx * footRx * 0.34f, foot.y - footRy * 0.45f)
                        )
                    }
                } else {
                    // Clean subtle 3D toe crease lines on top of shoe/paw foot
                    for (toeX in listOf(-0.28f, 0.28f)) {
                        drawLine(
                            color = creamShadow.copy(alpha = 0.72f),
                            start = Offset(footCenterX + footRx * toeX, foot.y + footRy * 0.18f),
                            end = Offset(footCenterX + footRx * toeX * 1.15f, foot.y + footRy * 0.82f),
                            strokeWidth = (u * 0.009f).coerceAtLeast(1.1f),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }

        // True front/back leg Z-ordering helper: draw back-kicking leg BEFORE torso, front-stepping leg AFTER torso!
        val leftBehind = leftKickBack > 0.08f
        val rightBehind = rightKickBack > 0.08f

        if (leftBehind) draw3DCartoonLegAndFoot(leftHip, leftKnee, leftFoot, true, leftLegScale)
        if (rightBehind) draw3DCartoonLegAndFoot(rightHip, rightKnee, rightFoot, false, rightLegScale)

        // =========================================================================
        // 5. 3D CARTOON TORSO / SKY-BLUE HOODIE JACKET / PLUSH SUIT
        // =========================================================================
        val wearsJacket = isPuppy || (!isChick && !isPenguin && !isPanda && !isBunny && !isLion && !holdsDeck)
        val jacketHi = if (isPuppy) Color(0xFF90CAF9) else lightFur
        val jacketMid = if (isPuppy) Color(0xFF42A5F5) else mainFur
        val jacketLo = if (isPuppy) Color(0xFF1565C0) else darkFur

        drawCartoon3DOval(
            cx = torsoCenterX,
            cy = torsoCenterY,
            rx = torsoRx,
            ry = torsoRy,
            hiColor = if (wearsJacket) jacketHi else lightFur,
            midColor = if (wearsJacket) jacketMid else mainFur,
            loColor = if (wearsJacket) jacketLo else darkFur,
            rimHighlightAlpha = 0.38f
        )

        // Cream/White Plush Belly or Zip-Up Inner Shirt Panel
        val bellyBreath = if (isStationaryPaused) 1f else 1f + sin(t * 4.2f) * 0.036f * legDanceW
        val bellyRx = torsoRx * (if (wearsJacket) 0.42f else if (isPenguin) 0.76f else 0.66f) * bellyBreath
        val bellyRy = torsoRy * (if (wearsJacket) 0.82f else 0.74f) * bellyBreath
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, bellyColor, darken(bellyColor, 0.14f)),
                center = Offset(torsoCenterX - bellyRx * 0.2f, torsoCenterY - bellyRy * 0.2f),
                radius = max(bellyRx, bellyRy) * 1.3f
            ),
            topLeft = Offset(torsoCenterX - bellyRx, torsoCenterY - bellyRy * 0.86f),
            size = Size(bellyRx * 2f, bellyRy * 1.80f)
        )

        if (wearsJacket) {
            // Crisp white hoodie collar/zipper placket trim
            drawArc(
                color = Color.White,
                startAngle = 25f,
                sweepAngle = 130f,
                useCenter = false,
                topLeft = Offset(torsoCenterX - torsoRx * 0.56f, torsoCenterY - torsoRy * 0.96f),
                size = Size(torsoRx * 1.12f, torsoRy * 0.42f),
                style = Stroke(width = (u * 0.022f).coerceAtLeast(2f), cap = StrokeCap.Round)
            )
            // White hoodie pocket trim accents
            listOf(-1f, 1f).forEach { side ->
                drawLine(
                    color = Color.White.copy(alpha = 0.85f),
                    start = Offset(torsoCenterX + side * torsoRx * 0.44f, torsoCenterY + torsoRy * 0.18f),
                    end = Offset(torsoCenterX + side * torsoRx * 0.72f, torsoCenterY + torsoRy * 0.44f),
                    strokeWidth = (u * 0.012f).coerceAtLeast(1.2f),
                    cap = StrokeCap.Round
                )
            }
        }

        if (holdsDeck) {
            val deckW = torsoRx * 1.24f
            val deckH = torsoRy * 0.82f
            val deckLeft = torsoCenterX - deckW * 0.5f
            val deckTop = torsoCenterY - deckH * 0.40f
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF28354A), Color(0xFF121824))),
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
            val reelR = deckH * 0.25f
            val spinAng = if (isStationaryPaused) 0.4f else t * 6.8f
            listOf(torsoCenterX - deckW * 0.24f, torsoCenterX + deckW * 0.24f).forEach { rxPos ->
                val rCenter = Offset(rxPos, deckTop + deckH * 0.52f)
                drawCircle(color = Color(0xFF0B0F19), radius = reelR, center = rCenter)
                drawCircle(color = accentColor, radius = reelR, center = rCenter, style = Stroke(width = (u * 0.010f).coerceAtLeast(1.2f)))
                val sx = cos(spinAng) * reelR * 0.8f
                val sy = sin(spinAng) * reelR * 0.8f
                drawLine(Color.White, rCenter - Offset(sx, sy), rCenter + Offset(sx, sy), strokeWidth = 1.6.dp.toPx())
                drawLine(Color.White, rCenter - Offset(-sy, sx), rCenter + Offset(-sy, sx), strokeWidth = 1.6.dp.toPx())
            }
        } else if (!wearsJacket && !isChick && !isPenguin) {
            // Classic cute Red Collar with 3D Gold Bell for Teddy/Bunny/Panda/Lion
            val collarY = torsoCenterY - torsoRy * 0.72f
            drawArc(
                brush = Brush.horizontalGradient(listOf(Color(0xFFB71C1C), Color(0xFFFF1744), Color(0xFFD50000))),
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(torsoCenterX - torsoRx * 0.62f, collarY - torsoRy * 0.22f),
                size = Size(torsoRx * 1.24f, torsoRy * 0.48f),
                style = Stroke(width = (u * 0.026f).coerceAtLeast(2.2f), cap = StrokeCap.Round)
            )
            val bellJingleX = if (!isStationaryPaused) sin(t * 9.5f) * (u * 0.009f) * armGrooveW else 0f
            val bellCenter = Offset(torsoCenterX + bellJingleX, collarY + torsoRy * 0.26f)
            val bellR = u * 0.032f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFF59D), Color(0xFFFFD600), Color(0xFFE65100)),
                    center = Offset(bellCenter.x - bellR * 0.3f, bellCenter.y - bellR * 0.3f),
                    radius = bellR * 1.3f
                ),
                radius = bellR,
                center = bellCenter
            )
        }

        // Draw front-stepping or standing legs IN FRONT of the torso jacket hem!
        if (!leftBehind) draw3DCartoonLegAndFoot(leftHip, leftKnee, leftFoot, true, leftLegScale)
        if (!rightBehind) draw3DCartoonLegAndFoot(rightHip, rightKnee, rightFoot, false, rightLegScale)

        // =========================================================================
        // 6. CRYSTAL-CLEAR 2-JOINT 3D CARTOON ARMS & HANDS ("Hath Chalte hue aur Real")
        // =========================================================================
        val leftShoulder = Offset(torsoCenterX - torsoRx * 0.88f, torsoCenterY - torsoRy * 0.38f)
        val rightShoulder = Offset(torsoCenterX + torsoRx * 0.88f, torsoCenterY - torsoRy * 0.38f)

        val leftWaveOsc = if (isStationaryPaused) 0f else sin(t * 5.8f) * armGrooveW
        val rightWaveOsc = if (isStationaryPaused) 0f else cos(t * 5.8f) * armGrooveW
        val flailArmOsc = sin(t * (if (activeUpsideDownOrFalling) 15.0f else 11.5f))

        val leftArmAngle = when {
            activeUpsideDownOrFalling -> -0.95f + flailArmOsc * 0.72f
            activeLifted -> -0.45f + flailArmOsc * 0.48f
            isStationaryPaused -> 0.82f
            holdsDeck && airborneFactor < 0.45f -> 0.48f + leftWaveOsc * 0.18f
            activeStyle == MascotDanceMode.BHANGRA_GROOVE -> 0.82f - armGrooveW * (1.92f - leftWaveOsc * 0.55f)
            else -> 0.82f - armGrooveW * (1.02f + airborneFactor * 0.78f - leftWaveOsc * 0.60f)
        }
        val rightArmAngle = when {
            activeUpsideDownOrFalling -> -0.95f - flailArmOsc * 0.72f
            activeLifted -> -0.45f - flailArmOsc * 0.48f
            isStationaryPaused -> 0.82f
            holdsDeck && airborneFactor < 0.45f -> 0.48f + rightWaveOsc * 0.18f
            activeStyle == MascotDanceMode.BHANGRA_GROOVE -> 0.82f - armGrooveW * (1.92f + rightWaveOsc * 0.55f)
            else -> 0.82f - armGrooveW * (1.02f + airborneFactor * 0.78f + rightWaveOsc * 0.60f)
        }

        val upperArmLen = u * 0.098f
        val forearmLen = u * 0.092f
        val armThick = u * 0.068f

        val leftElbow = Offset(
            x = leftShoulder.x - cos(leftArmAngle) * upperArmLen,
            y = leftShoulder.y + sin(leftArmAngle) * upperArmLen
        )
        val leftForearmAngle = leftArmAngle - 0.34f - airborneFactor * 0.30f + (if (activeUpsideDownOrFalling) cos(t * 14f) * 0.35f else 0f)
        val leftPaw = Offset(
            x = leftElbow.x - cos(leftForearmAngle) * forearmLen,
            y = leftElbow.y + sin(leftForearmAngle) * forearmLen
        )

        val rightElbow = Offset(
            x = rightShoulder.x + cos(rightArmAngle) * upperArmLen,
            y = rightShoulder.y + sin(rightArmAngle) * upperArmLen
        )
        val rightForearmAngle = rightArmAngle - 0.34f - airborneFactor * 0.30f - (if (activeUpsideDownOrFalling) cos(t * 14f) * 0.35f else 0f)
        val rightPaw = Offset(
            x = rightElbow.x + cos(rightForearmAngle) * forearmLen,
            y = rightElbow.y + sin(rightForearmAngle) * forearmLen
        )

        val sleeveHi = if (wearsJacket) jacketHi else limbLight
        val sleeveMid = if (wearsJacket) jacketMid else limbFur
        val sleeveLo = if (wearsJacket) jacketLo else limbDark

        fun draw3DCartoonHandPaw(pawCenter: Offset, isRaised: Boolean) {
            val pawR = u * 0.049f
            drawCartoon3DOval(
                cx = pawCenter.x,
                cy = pawCenter.y,
                rx = pawR,
                ry = pawR * 0.94f,
                hiColor = if (isPuppy || wearsJacket) Color.White else limbLight,
                midColor = if (isPuppy || wearsJacket) creamFur else limbFur,
                loColor = if (isPuppy || wearsJacket) creamShadow else limbDark,
                rimHighlightAlpha = 0.50f
            )
            if (!isChick && !isPenguin && (isRaised || activeLifted || activeUpsideDownOrFalling)) {
                val beanColor = Color(0xFFFF80AB)
                drawCircle(
                    color = beanColor.copy(alpha = 0.90f),
                    radius = pawR * 0.36f,
                    center = Offset(pawCenter.x, pawCenter.y + pawR * 0.08f)
                )
                for (b in -1..1) {
                    drawCircle(
                        color = beanColor,
                        radius = pawR * 0.16f,
                        center = Offset(pawCenter.x + b * pawR * 0.36f, pawCenter.y - pawR * 0.36f)
                    )
                }
            }
        }

        drawCartoon3DLimb(leftShoulder, leftElbow, armThick, sleeveHi, sleeveMid, sleeveLo)
        drawCartoon3DLimb(leftElbow, leftPaw, armThick * 0.90f, sleeveHi, sleeveMid, sleeveLo)
        if (wearsJacket) {
            drawCircle(color = Color.White, radius = armThick * 0.48f, center = leftPaw)
        }
        draw3DCartoonHandPaw(leftPaw, isRaised = leftPaw.y < leftShoulder.y)

        drawCartoon3DLimb(rightShoulder, rightElbow, armThick, sleeveHi, sleeveMid, sleeveLo)
        drawCartoon3DLimb(rightElbow, rightPaw, armThick * 0.90f, sleeveHi, sleeveMid, sleeveLo)
        if (wearsJacket) {
            drawCircle(color = Color.White, radius = armThick * 0.48f, center = rightPaw)
        }
        draw3DCartoonHandPaw(rightPaw, isRaised = rightPaw.y < rightShoulder.y)

        // =========================================================================
        // 7. EXPRESSIVE PIXAR 3D CARTOON HEAD, EARS & STUDIO DJ HEADPHONES
        // =========================================================================
        val headTilt = when {
            activeUpsideDownOrFalling -> sin(t * 12.5f) * 0.28f
            activeLifted -> sin(t * 9.5f) * 0.22f
            isStationaryPaused -> 0f
            else -> sin(t * 3.4f) * (0.18f * headGrooveW)
        }
        val headTurnX = when {
            activeUpsideDownOrFalling -> (activeVelX * 0.002f).coerceIn(-0.04f, 0.04f) * u + cos(t * 9.2f) * u * 0.022f
            activeLifted -> cos(t * 7.5f) * u * 0.028f
            isStationaryPaused -> 0f
            else -> (sin(t * 2.4f) * u * 0.032f * headGrooveW) + (if (activeWalking) activeWalkDir * u * 0.016f else 0f)
        }
        val headNodY = when {
            activeLifted || activeUpsideDownOrFalling -> -u * 0.016f
            isStationaryPaused -> 0f
            else -> (-airborneFactor * 0.026f + totalCrouch * 0.030f) * u * headGrooveW
        }

        val headCenterX = torsoCenterX + headTurnX + sin(headTilt) * (u * 0.026f)
        val headCenterY = torsoCenterY - torsoRy * 0.84f - u * 0.122f + headNodY
        val headRx = u * 0.232f * (1f - airborneFactor * 0.04f + totalCrouch * 0.06f)
        val headRy = u * 0.205f * (1f + airborneFactor * 0.05f - totalCrouch * 0.05f)

        val earSpring = if (isStationaryPaused) 0f else sin(jumpCycle - 0.85f) * max(headGrooveW, if (activeLifted || activeUpsideDownOrFalling) 1f else 0f)

        // 18-Lobe 3D Lion Mane
        if (isLion) {
            val maneHi = lighten(breed.earColor, 0.28f)
            val maneMid = breed.earColor
            val maneLo = darken(breed.earColor, 0.42f)
            val lobeCount = 16
            val maneRx = headRx * 1.28f
            val maneRy = headRy * 1.28f
            for (i in 0 until lobeCount) {
                val ang = (i.toFloat() / lobeCount) * 6.2831855f + (if (isStationaryPaused) 0f else sin(t * 3.2f) * 0.04f)
                val lx = headCenterX + cos(ang) * maneRx
                val ly = headCenterY + sin(ang) * maneRy - earSpring * u * 0.010f
                drawCartoon3DOval(lx, ly, u * 0.062f, u * 0.062f, maneHi, maneMid, maneLo, rimHighlightAlpha = 0.20f)
            }
        }

        // Breed Ears (Behind Head)
        if (isBunny) {
            val earFlop = earSpring * 0.36f - airborneFactor * 0.22f
            listOf(-1f, 1f).forEach { side ->
                val earBase = Offset(headCenterX + side * headRx * 0.46f, headCenterY - headRy * 0.78f)
                val earMid = Offset(
                    earBase.x + side * u * (0.032f + earFlop * 0.03f),
                    earBase.y - u * (0.115f + airborneFactor * 0.020f)
                )
                val earTip = Offset(
                    earMid.x + side * u * (0.052f + earFlop * 0.05f),
                    earMid.y - u * 0.062f + abs(earFlop) * u * 0.035f
                )
                drawCartoon3DLimb(earBase, earMid, u * 0.072f, lightFur, mainFur, darkFur)
                drawCartoon3DLimb(earMid, earTip, u * 0.062f, lightFur, mainFur, darkFur)
                drawLine(
                    color = breed.earColor,
                    start = earBase + Offset(0f, -u * 0.015f),
                    end = earMid + Offset(side * u * 0.01f, -u * 0.01f),
                    strokeWidth = u * 0.030f,
                    cap = StrokeCap.Round
                )
            }
        } else if (isChick) {
            for (f in -1..1) {
                val fx = headCenterX + f * u * 0.034f + (if (isStationaryPaused) 0f else sin(t * 5f + f) * u * 0.010f)
                val fy = headCenterY - headRy * 1.04f - (1 - abs(f)) * u * 0.025f + earSpring * u * 0.016f
                drawCartoon3DOval(fx, fy, u * 0.024f, u * 0.046f, lightFur, mainFur, darkFur)
            }
        } else if (isHusky) {
            val earBounceY = earSpring * u * 0.016f
            listOf(-1f, 1f).forEach { side ->
                val baseInner = Offset(headCenterX + side * headRx * 0.30f, headCenterY - headRy * 0.78f)
                val baseOuter = Offset(headCenterX + side * headRx * 0.82f, headCenterY - headRy * 0.48f)
                val tip = Offset(headCenterX + side * headRx * 0.68f, headCenterY - headRy * 1.32f + earBounceY * side)
                val outerEarPath = Path().apply {
                    moveTo(baseInner.x, baseInner.y); lineTo(tip.x, tip.y); lineTo(baseOuter.x, baseOuter.y); close()
                }
                drawPath(outerEarPath, Brush.verticalGradient(listOf(lightFur, mainFur, darkFur), tip.y, baseOuter.y))
                val innerEarPath = Path().apply {
                    moveTo(baseInner.x + side * u * 0.018f, baseInner.y - u * 0.008f)
                    lineTo(tip.x - side * u * 0.008f, tip.y + u * 0.032f)
                    lineTo(baseOuter.x - side * u * 0.022f, baseOuter.y - u * 0.012f)
                    close()
                }
                drawPath(innerEarPath, Color(0xFFFFE4EC))
            }
        } else if (!isPenguin && !isPuppy) {
            val earBounceY = earSpring * u * 0.020f
            listOf(-1f, 1f).forEach { side ->
                val ex = headCenterX + side * headRx * 0.76f
                val ey = headCenterY - headRy * 0.70f + earBounceY * side
                val earR = u * 0.074f
                drawCartoon3DOval(ex, ey, earR, earR, limbLight, limbFur, limbDark)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(lighten(breed.earColor, 0.25f), breed.earColor, darken(breed.earColor, 0.25f)),
                        center = Offset(ex, ey),
                        radius = earR * 0.65f
                    ),
                    radius = earR * 0.56f,
                    center = Offset(ex, ey)
                )
            }
        }

        // Main 3D Pixar Spherical Head
        val headFurMain = if (isPanda) Color(0xFFFDFBF7) else mainFur
        val headFurHi = if (isPanda) Color.White else lightFur
        val headFurLo = if (isPanda) Color(0xFFD7DCE4) else darkFur

        drawCartoon3DOval(
            cx = headCenterX,
            cy = headCenterY,
            rx = headRx,
            ry = headRy,
            hiColor = headFurHi,
            midColor = headFurMain,
            loColor = headFurLo,
            rimHighlightAlpha = 0.44f
        )

        // Puppy Creamy-White Face Mask (Center forehead blaze + wide cheerful cheek/muzzle fur)
        if (isPuppy) {
            // Forehead blaze between eyes
            drawOval(
                brush = Brush.verticalGradient(listOf(Color.White, creamFur)),
                topLeft = Offset(headCenterX - headRx * 0.17f, headCenterY - headRy * 0.84f),
                size = Size(headRx * 0.34f, headRy * 0.90f)
            )
            // Lower creamy-white puffy cheek/muzzle mask
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, creamFur, creamMid),
                    center = Offset(headCenterX, headCenterY + headRy * 0.24f),
                    radius = headRx * 0.85f
                ),
                topLeft = Offset(headCenterX - headRx * 0.82f, headCenterY - headRy * 0.04f),
                size = Size(headRx * 1.64f, headRy * 0.98f)
            )
        } else if (isPenguin) {
            drawOval(
                color = Color(0xFFFDFBF7),
                topLeft = Offset(headCenterX - headRx * 0.78f, headCenterY - headRy * 0.62f),
                size = Size(headRx * 1.56f, headRy * 1.38f)
            )
        }

        // Puppy Floppy 3D Ears on Both Sides (With cute white inner ear patches!)
        if (isPuppy) {
            val earSwingOut = if (isStationaryPaused) 0f else (airborneFactor * 0.35f + abs(earSpring) * 0.22f) * u * 0.075f
            listOf(-1f, 1f).forEach { side ->
                val earTop = Offset(headCenterX + side * headRx * 0.86f, headCenterY - headRy * 0.26f)
                val earBot = Offset(
                    headCenterX + side * (headRx * 1.04f + earSwingOut),
                    headCenterY + headRy * 0.56f - airborneFactor * u * 0.038f
                )
                drawCartoon3DLimb(
                    p1 = earTop,
                    p2 = earBot,
                    thickness = u * 0.078f,
                    hi = lighten(breed.earColor, 0.24f),
                    mid = breed.earColor,
                    lo = darken(breed.earColor, 0.34f)
                )
                // Soft inner white spot on floppy puppy ear
                drawOval(
                    color = creamFur.copy(alpha = 0.85f),
                    topLeft = Offset(earBot.x - u * 0.016f, earBot.y - u * 0.040f),
                    size = Size(u * 0.032f, u * 0.056f)
                )
            }
        }

        // 3D Studio DJ Headphones (Sleek headband + glossy 3D earcups)
        val earcupPulse = if (isStationaryPaused) 0f else abs(sin(t * 6.2f)) * 0.018f * u * headGrooveW
        drawArc(
            color = accentColor,
            startAngle = 188f,
            sweepAngle = 164f,
            useCenter = false,
            topLeft = Offset(headCenterX - headRx * 1.04f, headCenterY - headRy * 1.08f),
            size = Size(headRx * 2.08f, headRy * 1.82f),
            style = Stroke(width = (u * 0.034f).coerceAtLeast(3f), cap = StrokeCap.Round)
        )
        drawArc(
            color = Color.White.copy(alpha = 0.72f),
            startAngle = 212f,
            sweepAngle = 116f,
            useCenter = false,
            topLeft = Offset(headCenterX - headRx * 1.00f, headCenterY - headRy * 1.04f),
            size = Size(headRx * 2.00f, headRy * 1.74f),
            style = Stroke(width = (u * 0.012f).coerceAtLeast(1.3f), cap = StrokeCap.Round)
        )

        listOf(-1f, 1f).forEach { side ->
            val cupX = headCenterX + side * (headRx * 0.99f + earcupPulse)
            val cupY = headCenterY - headRy * 0.02f
            val cupRx = u * 0.044f
            val cupRy = u * 0.072f
            drawOval(
                color = Color(0xFF141A29),
                topLeft = Offset(cupX - cupRx * 1.12f, cupY - cupRy * 1.05f),
                size = Size(cupRx * 2.24f, cupRy * 2.10f)
            )
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(lighten(accentColor, 0.45f), accentColor, darken(accentColor, 0.35f)),
                    center = Offset(cupX - side * cupRx * 0.2f, cupY - cupRy * 0.3f),
                    radius = cupRy * 1.3f
                ),
                topLeft = Offset(cupX - cupRx, cupY - cupRy),
                size = Size(cupRx * 2f, cupRy * 2f)
            )
            drawOval(
                color = Color.White.copy(alpha = 0.85f),
                topLeft = Offset(cupX - cupRx * 0.65f, cupY - cupRy * 0.65f),
                size = Size(cupRx * 1.30f, cupRy * 1.30f),
                style = Stroke(width = (u * 0.010f).coerceAtLeast(1.1f))
            )
        }

        // =========================================================================
        // 8. BIG SOULFUL PIXAR 3D EYES, EYEBROWS & ROSY CHEEKS
        // =========================================================================
        val eyeY = headCenterY - headRy * 0.09f + headNodY * 0.25f
        val leftEyeCenter = Offset(headCenterX - headRx * 0.36f + headTurnX * 0.35f, eyeY)
        val rightEyeCenter = Offset(headCenterX + headRx * 0.36f + headTurnX * 0.35f, eyeY)
        val eyeRx = u * 0.048f
        val eyeRy = u * 0.054f

        if (isPanda) {
            listOf(leftEyeCenter, rightEyeCenter).forEach { ec ->
                drawOval(
                    color = Color(0xFF1A1E24),
                    topLeft = Offset(ec.x - eyeRx * 1.38f, ec.y - eyeRy * 1.25f),
                    size = Size(eyeRx * 2.76f, eyeRy * 2.50f)
                )
            }
        }

        val blushAlpha = if (isStationaryPaused) 0.34f else (0.38f + airborneFactor * 0.24f).coerceIn(0.32f, 0.68f)
        listOf(-1f, 1f).forEach { side ->
            val cx = headCenterX + side * headRx * 0.58f + headTurnX * 0.3f
            val cy = eyeY + headRy * 0.33f
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFF6B95).copy(alpha = blushAlpha), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = u * 0.046f
                ),
                topLeft = Offset(cx - u * 0.046f, cy - u * 0.028f),
                size = Size(u * 0.092f, u * 0.056f)
            )
        }

        val blinkPhase = if (isStationaryPaused) 0f else (t * 1.6f) % 5.0f
        val isHappyJumpArc = !isStationaryPaused && !activeLifted && !activeUpsideDownOrFalling && (airborneFactor > 0.82f || activeHappyJump > 26)
        val blinkAmount = if (!isStationaryPaused && !activeUpsideDownOrFalling && blinkPhase > 4.66f) {
            sin(((blinkPhase - 4.66f) / 0.34f) * 3.1415927f).coerceIn(0f, 1f)
        } else 0f

        if (isHappyJumpArc) {
            listOf(leftEyeCenter, rightEyeCenter).forEach { ec ->
                drawArc(
                    color = Color(0xFF1C1009),
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(ec.x - eyeRx * 0.95f, ec.y - eyeRy * 0.60f),
                    size = Size(eyeRx * 1.90f, eyeRy * 1.30f),
                    style = Stroke(width = (u * 0.022f).coerceAtLeast(2.2f), cap = StrokeCap.Round)
                )
            }
        } else {
            // Pupils track velocity & real gravity tilt direction!
            val gazeX = if (isStationaryPaused) 0f else {
                val baseGaze = sin(t * 2.5f) * eyeRx * 0.24f + headTurnX * 0.18f
                val velGaze = (activeVelX * 0.022f + activeGravX * 0.35f).coerceIn(-0.42f, 0.42f) * eyeRx
                (baseGaze + velGaze).coerceIn(-eyeRx * 0.45f, eyeRx * 0.45f)
            }
            val gazeY = if (isStationaryPaused) 0f else {
                val velGazeY = (activeVelY * 0.022f).coerceIn(-0.42f, 0.42f) * eyeRy
                ((-airborneFactor * 0.18f + totalCrouch * 0.14f) * eyeRy + velGazeY).coerceIn(-eyeRy * 0.44f, eyeRy * 0.44f)
            }
            val eyeScaleBoost = if (activeUpsideDownOrFalling || activeLifted) 1.12f else 1.0f

            listOf(leftEyeCenter, rightEyeCenter).forEach { ec ->
                val erx = eyeRx * eyeScaleBoost
                val ery = eyeRy * eyeScaleBoost
                // Clean white sclera highlight behind iris for expressive cartoon look
                drawOval(
                    color = Color.White,
                    topLeft = Offset(ec.x - erx * 1.06f, ec.y - ery * 1.04f),
                    size = Size(erx * 2.12f, ery * 2.08f)
                )
                // Dark outer eye rim
                drawOval(
                    color = Color(0xFF140C08),
                    topLeft = Offset(ec.x - erx * 1.04f, ec.y - ery * 1.02f),
                    size = Size(erx * 2.08f, ery * 2.04f),
                    style = Stroke(width = (u * 0.011f).coerceAtLeast(1.2f))
                )
                // Warm Pixar brown/amber 3D iris
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF6D4023), Color(0xFF3E1F10), Color(0xFF140A05)),
                        center = Offset(ec.x + gazeX, ec.y + gazeY + ery * 0.15f),
                        radius = ery * 1.1f
                    ),
                    topLeft = Offset(ec.x - erx * 0.80f + gazeX * 0.45f, ec.y - ery * 0.84f + gazeY * 0.45f),
                    size = Size(erx * 1.60f, ery * 1.68f)
                )
                // Glossy black center pupil (slightly smaller when surprised/upside-down for classic cartoon look!)
                val pupilScale = if (activeUpsideDownOrFalling) 0.82f else 1.0f
                drawOval(
                    color = Color(0xFF060403),
                    topLeft = Offset(ec.x - erx * 0.48f * pupilScale + gazeX * 0.72f, ec.y - ery * 0.54f * pupilScale + gazeY * 0.72f),
                    size = Size(erx * 0.96f * pupilScale, ery * 1.08f * pupilScale)
                )
                // Dual glossy Pixar catchlights
                drawCircle(
                    color = Color.White,
                    radius = erx * 0.34f,
                    center = Offset(ec.x - erx * 0.26f + gazeX * 0.8f, ec.y - ery * 0.30f + gazeY * 0.8f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.88f),
                    radius = erx * 0.16f,
                    center = Offset(ec.x + erx * 0.26f + gazeX * 0.8f, ec.y + ery * 0.26f + gazeY * 0.8f)
                )
                if (blinkAmount > 0.05f) {
                    drawArc(
                        color = headFurMain,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(ec.x - erx * 1.12f, ec.y - ery * 1.12f),
                        size = Size(erx * 2.24f, ery * 2.24f * blinkAmount)
                    )
                }
            }

            val browLift = when {
                activeUpsideDownOrFalling || activeLifted -> -u * 0.026f
                isStationaryPaused -> 0f
                else -> -airborneFactor * u * 0.018f
            }
            listOf(-1f, 1f).forEach { side ->
                val ec = if (side < 0f) leftEyeCenter else rightEyeCenter
                drawArc(
                    color = darken(mainFur, 0.45f),
                    startAngle = if (side < 0f) 210f else 230f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(ec.x - eyeRx * 0.90f, ec.y - eyeRy * 1.48f + browLift),
                    size = Size(eyeRx * 1.80f, eyeRy * 0.82f),
                    style = Stroke(width = (u * 0.019f).coerceAtLeast(1.9f), cap = StrokeCap.Round)
                )
            }
        }

        // =========================================================================
        // 9. EXPRESSIVE 3D CARTOON MUZZLE, SINGING MOUTH, TEETH & STICKING-OUT TONGUE ("JEEBH")!
        // =========================================================================
        val muzzleCenter = Offset(headCenterX + headTurnX * 0.45f, headCenterY + headRy * 0.28f + headNodY * 0.4f)
        val mouthOpen = when {
            activeUpsideDownOrFalling || activeLifted -> 1.0f
            activeTonguePop > 0 -> 0.88f
            isStationaryPaused -> 0.08f
            else -> (abs(sin(t * 6.8f)) * 0.82f + 0.22f) * headGrooveW
        }

        // Playful Cartoon Tongue ("Jeebh nikalti hui ho") protrusion & side-to-side wag!
        val tongueCycle = (sin(t * 2.9f) + 1f) * 0.5f // smoothly pops in & out during dance
        val tongueOutStrength = when {
            isStationaryPaused -> 0f
            activeTonguePop > 0 -> 1.0f
            activeUpsideDownOrFalling || activeLifted -> 0.95f
            isDancingActive && (tongueCycle > 0.32f || airborneFactor > 0.45f || activeHappyJump > 0) -> {
                ((tongueCycle - 0.15f) * 1.15f).coerceIn(0.35f, 1.0f) * headGrooveW
            }
            else -> 0f
        }
        val tongueWagX = sin(t * (if (activeUpsideDownOrFalling) 14f else 8.8f)) * u * 0.022f * tongueOutStrength

        // Helper to draw a super-expressive 3D sticking-out cartoon tongue ("Jeebh")
        fun drawStickingOutCartoonTongue(
            anchorX: Float,
            anchorY: Float,
            tongueW: Float,
            tongueLen: Float,
            wagOffsetX: Float
        ) {
            if (tongueLen < u * 0.012f) return
            val tipX = anchorX + wagOffsetX
            val tipY = anchorY + tongueLen
            val halfW = tongueW * 0.5f

            val tonguePath = Path().apply {
                moveTo(anchorX - halfW, anchorY)
                cubicTo(
                    anchorX - halfW * 1.18f, anchorY + tongueLen * 0.62f,
                    tipX - halfW * 0.72f, tipY + tongueLen * 0.12f,
                    tipX, tipY
                )
                cubicTo(
                    tipX + halfW * 0.72f, tipY + tongueLen * 0.12f,
                    anchorX + halfW * 1.18f, anchorY + tongueLen * 0.62f,
                    anchorX + halfW, anchorY
                )
                close()
            }
            // Glossy 3D coral-pink cartoon tongue gradient
            drawPath(
                path = tonguePath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFF4F87), Color(0xFFFF7AA8), Color(0xFFE91E63)),
                    startY = anchorY,
                    endY = tipY
                )
            )
            // Crisp dark-rose outline so tongue stands out clearly like real cartoon animation
            drawPath(
                path = tonguePath,
                color = Color(0xFF880E4F),
                style = Stroke(width = (u * 0.010f).coerceAtLeast(1.1f), cap = StrokeCap.Round)
            )
            // Center tongue groove crease line
            drawLine(
                color = Color(0xFFC2185B).copy(alpha = 0.72f),
                start = Offset(anchorX + wagOffsetX * 0.25f, anchorY + tongueLen * 0.14f),
                end = Offset(anchorX + wagOffsetX * 0.78f, anchorY + tongueLen * 0.72f),
                strokeWidth = (u * 0.009f).coerceAtLeast(1.0f),
                cap = StrokeCap.Round
            )
            // Wet glossy specular highlight on tongue
            drawOval(
                color = Color.White.copy(alpha = 0.60f),
                topLeft = Offset(anchorX - halfW * 0.52f + wagOffsetX * 0.4f, anchorY + tongueLen * 0.26f),
                size = Size(tongueW * 0.26f, tongueLen * 0.34f)
            )
        }

        if (isChick || isPenguin) {
            val beakW = u * 0.072f
            val beakH = u * 0.050f
            if (mouthOpen > 0.18f) {
                drawOval(
                    color = Color(0xFF4A0E1C),
                    topLeft = Offset(muzzleCenter.x - beakW * 0.58f, muzzleCenter.y - beakH * 0.20f),
                    size = Size(beakW * 1.16f, beakH * (0.68f + mouthOpen * 0.72f))
                )
                if (tongueOutStrength > 0.15f) {
                    drawStickingOutCartoonTongue(
                        anchorX = muzzleCenter.x,
                        anchorY = muzzleCenter.y + beakH * 0.18f,
                        tongueW = beakW * 0.72f,
                        tongueLen = u * 0.074f * tongueOutStrength,
                        wagOffsetX = tongueWagX
                    )
                }
            }
            val upperBeak = Path().apply {
                moveTo(muzzleCenter.x - beakW * 0.78f, muzzleCenter.y)
                quadraticTo(muzzleCenter.x, muzzleCenter.y - beakH * 0.98f, muzzleCenter.x + beakW * 0.78f, muzzleCenter.y)
                quadraticTo(muzzleCenter.x, muzzleCenter.y + beakH * 0.35f, muzzleCenter.x - beakW * 0.78f, muzzleCenter.y)
                close()
            }
            drawPath(
                path = upperBeak,
                brush = Brush.verticalGradient(listOf(Color(0xFFFFD54F), Color(0xFFFF8F00)), muzzleCenter.y - beakH, muzzleCenter.y + beakH * 0.4f)
            )
        } else {
            val snoutRx = headRx * 0.39f
            val snoutRy = headRy * 0.29f
            if (!isPuppy) {
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, bellyColor, darken(bellyColor, 0.16f)),
                        center = Offset(muzzleCenter.x - snoutRx * 0.2f, muzzleCenter.y - snoutRy * 0.3f),
                        radius = snoutRx * 1.3f
                    ),
                    topLeft = Offset(muzzleCenter.x - snoutRx, muzzleCenter.y - snoutRy * 0.85f),
                    size = Size(snoutRx * 2f, snoutRy * 1.85f + mouthOpen * u * 0.018f)
                )
            }

            val mouthY = muzzleCenter.y + snoutRy * 0.24f
            if (activeUpsideDownOrFalling || activeLifted) {
                // Wide comedic cartoon "WHOA!" open mouth with wagging sticking-out tongue!
                val oRx = snoutRx * 0.56f
                val oRy = snoutRy * 0.76f * (0.90f + sin(t * 11f) * 0.10f)
                drawOval(
                    color = Color(0xFF3E0A16),
                    topLeft = Offset(muzzleCenter.x - oRx, mouthY - oRy * 0.45f),
                    size = Size(oRx * 2f, oRy * 2f)
                )
                // Cute upper cartoon tooth
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(muzzleCenter.x - oRx * 0.42f, mouthY - oRy * 0.40f),
                    size = Size(oRx * 0.84f, oRy * 0.36f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(u * 0.012f, u * 0.012f)
                )
                drawStickingOutCartoonTongue(
                    anchorX = muzzleCenter.x,
                    anchorY = mouthY + oRy * 0.38f,
                    tongueW = oRx * 1.15f,
                    tongueLen = u * 0.088f * tongueOutStrength.coerceAtLeast(0.85f),
                    wagOffsetX = tongueWagX
                )
            } else if (isDancingActive || activeTonguePop > 0) {
                // Expressive singing/laughing cartoon mouth with upper teeth & real sticking-out wagging tongue!
                val mouthW = snoutRx * (1.06f + mouthOpen * 0.26f)
                val mouthH = (snoutRy * 1.05f * mouthOpen).coerceAtLeast(u * 0.024f)
                val mouthTopY = mouthY - mouthH * 0.24f
                drawOval(
                    color = Color(0xFF420917),
                    topLeft = Offset(muzzleCenter.x - mouthW * 0.5f, mouthTopY),
                    size = Size(mouthW, mouthH)
                )
                // Bright cartoon upper smile teeth
                if (mouthH > u * 0.028f) {
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(muzzleCenter.x - mouthW * 0.32f, mouthTopY + u * 0.004f),
                        size = Size(mouthW * 0.64f, mouthH * 0.28f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(u * 0.010f, u * 0.010f)
                    )
                }
                // Inner tongue base
                drawOval(
                    color = Color(0xFFE91E63),
                    topLeft = Offset(muzzleCenter.x - mouthW * 0.34f, mouthTopY + mouthH * 0.42f),
                    size = Size(mouthW * 0.68f, mouthH * 0.52f)
                )
                // Real sticking-out wagging cartoon tongue ("Jeebh") poking past lower lip!
                if (tongueOutStrength > 0.15f) {
                    drawStickingOutCartoonTongue(
                        anchorX = muzzleCenter.x,
                        anchorY = mouthTopY + mouthH * 0.52f,
                        tongueW = mouthW * 0.56f,
                        tongueLen = u * 0.082f * tongueOutStrength,
                        wagOffsetX = tongueWagX
                    )
                }
                // Upper cartoon smile lip outline
                drawArc(
                    color = Color(0xFF2B140E),
                    startAngle = 15f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(muzzleCenter.x - mouthW * 0.54f, mouthTopY - mouthH * 0.18f),
                    size = Size(mouthW * 1.08f, mouthH * 0.55f),
                    style = Stroke(width = (u * 0.013f).coerceAtLeast(1.4f), cap = StrokeCap.Round)
                )
            } else {
                // Calm cute closed smile when playback is paused (100% still!)
                drawArc(
                    color = Color(0xFF3E2723),
                    startAngle = 18f,
                    sweepAngle = 144f,
                    useCenter = false,
                    topLeft = Offset(muzzleCenter.x - snoutRx * 0.44f, mouthY - u * 0.016f),
                    size = Size(snoutRx * 0.88f, u * 0.032f),
                    style = Stroke(width = (u * 0.014f).coerceAtLeast(1.5f), cap = StrokeCap.Round)
                )
            }

            // Cute 3D Shiny Nose
            val noseCenter = Offset(muzzleCenter.x, muzzleCenter.y - snoutRy * 0.34f)
            val noseRx = snoutRx * 0.38f
            val noseRy = snoutRy * 0.27f
            val noseColor = if (isBunny) Color(0xFFFF6090) else Color(0xFF231510)
            drawOval(
                color = noseColor,
                topLeft = Offset(noseCenter.x - noseRx, noseCenter.y - noseRy),
                size = Size(noseRx * 2f, noseRy * 2f)
            )
            drawOval(
                color = Color.White.copy(alpha = 0.80f),
                topLeft = Offset(noseCenter.x - noseRx * 0.54f, noseCenter.y - noseRy * 0.64f),
                size = Size(noseRx * 0.68f, noseRy * 0.52f)
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
    showStudioBackdrop: Boolean = false,
    animateReels: Boolean = true,
    isMusicPlaying: Boolean = false,
    customFurColor: Color? = null
) {
    val portraitMotion = remember { MascotLiveMotionState() }

    // 0% idle CPU + 60/120fps VSync zero-recomposition loop for portrait previews
    LaunchedEffect(isMusicPlaying, breed) {
        portraitMotion.danceTransition = if (isMusicPlaying) 1f else 0f
        portraitMotion.isActivelyWalking = isMusicPlaying
        portraitMotion.happyJumpTicks = if (animateReels && isMusicPlaying) 12 else 0
        var lastFrameNs = 0L
        while (isMusicPlaying || portraitMotion.danceTransition > 0.005f) {
            withFrameNanos { frameNs ->
                val dt = if (lastFrameNs == 0L) 0.016f else ((frameNs - lastFrameNs) / 1_000_000_000f).coerceIn(0.004f, 0.033f)
                lastFrameNs = frameNs
                val step = dt * 60f
                if (isMusicPlaying) {
                    portraitMotion.danceTransition = (portraitMotion.danceTransition + 0.06f * step).coerceAtMost(1f)
                    portraitMotion.animTick += 0.19f * step
                    portraitMotion.walkStridePhase = (portraitMotion.animTick + breed.ordinal * 0.75f) * 1.45f
                    portraitMotion.isActivelyWalking = true
                    portraitMotion.happyJumpTicks = if (animateReels) 12 else 0
                } else {
                    portraitMotion.danceTransition = (portraitMotion.danceTransition - 0.10f * step).coerceAtLeast(0f)
                    if (portraitMotion.danceTransition > 0.005f) {
                        portraitMotion.animTick += 0.12f * portraitMotion.danceTransition * step
                        portraitMotion.walkStridePhase = (portraitMotion.animTick + breed.ordinal * 0.75f) * 1.45f
                    } else {
                        portraitMotion.isActivelyWalking = false
                        portraitMotion.happyJumpTicks = 0
                    }
                }
            }
        }
        if (!isMusicPlaying) {
            portraitMotion.danceTransition = 0f
            portraitMotion.isActivelyWalking = false
            portraitMotion.happyJumpTicks = 0
        }
    }

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
            parts = null,
            isMusicPlaying = isMusicPlaying,
            danceMode = MascotDanceMode.TRAMPOLINE_ACROBAT,
            auraColor = breed.headphoneAccent,
            customFurColor = customFurColor,
            drawInternalFloorShadow = false,
            liveMotion = portraitMotion,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun shortestAngleDeltaDeg(fromDeg: Float, toDeg: Float): Float {
    var diff = ((toDeg - fromDeg + 180f) % 360f + 360f) % 360f - 180f
    if (diff < -180f) diff += 360f
    return diff
}

/**
 * Interactive 100% Real-Time 3D Cartoon Companion ("Gudda") Overlay with:
 * A. Real Hardware Gravity & Accelerometer Sensor (tilting left/right slides & bounces off walls;
 *    turning phone upside-down flips Gudda 180° to the ceiling with flailing limbs & sticking-out tongue;
 *    turning upright drops Gudda back down with 5-7 decreasing tappe bounces!)
 * B. 60/120fps VSync `withFrameNanos` + Zero-Recomposition `MascotLiveMotionState` (0% idle CPU when paused & upright)
 * C. Expressive Cartoon Legs, Arms, Animated Mouth + Teeth + Playful Sticking-Out Wagging Tongue ("Jeebh")
 * D. 2-Second Drag & Hold Over Any Song/Video Row to Auto-Play + Edge Auto-Scrolling
 * E. Double-Tap on Mascot to Immediately Pause/Resume Playback + Long-Press Live Studio Dock
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
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val effectiveMascotSizeDp = mascotSizeDp.coerceAtLeast(112.dp)
    val mascotBoxWidthPx = with(density) { (effectiveMascotSizeDp + 16.dp).toPx() }
    val mascotBoxHeightPx = with(density) { (effectiveMascotSizeDp + 20.dp).toPx() }
    val mascotSizePx = with(density) { effectiveMascotSizeDp.toPx() }

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

    val prefs = remember(context) {
        context.getSharedPreferences("hybrid_mascot_custom_prefs", Context.MODE_PRIVATE)
    }
    var selectedFurPresetId by remember {
        mutableStateOf(prefs.getString("custom_fur_preset_id", MascotFurColorPreset.ORIGINAL.id) ?: MascotFurColorPreset.ORIGINAL.id)
    }
    val activeFurColorPreset = remember(selectedFurPresetId) {
        MascotFurColorPreset.fromId(selectedFurPresetId)
    }
    var gravityPhysicsEnabled by remember {
        mutableStateOf(prefs.getBoolean("mascot_gravity_physics_enabled", true))
    }

    var showMascotFeatureHub by remember { mutableStateOf(false) }

    BackHandler(enabled = showMascotFeatureHub) {
        showMascotFeatureHub = false
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(mascotBoxWidthPx + 24f)
        val screenHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(mascotBoxHeightPx + 80f)
        val topBoundY = with(density) { 22.dp.toPx() }
        val floorY = (screenHeightPx - mascotBoxHeightPx - with(density) { bottomClearanceDp.toPx() }).coerceAtLeast(topBoundY + 40f)
        val minBoundX = 6f
        val maxBoundX = (screenWidthPx - mascotBoxWidthPx - 6f).coerceAtLeast(minBoundX + 12f)

        var overlayRootOffset by remember { mutableStateOf(Offset.Zero) }
        val defaultRightX = (screenWidthPx - mascotBoxWidthPx - with(density) { 10.dp.toPx() }).coerceIn(minBoundX, maxBoundX)

        val motion = remember {
            MascotLiveMotionState().apply {
                posX = defaultRightX
                posY = floorY
                danceTransition = if (isMusicPlaying) 1f else 0f
            }
        }

        var isDragging by remember { mutableStateOf(false) }
        var physicsWakeToken by remember { mutableIntStateOf(0) }
        val isPhysicsLoopRunningRef = remember { BooleanArray(1) }

        DisposableEffect(Unit) {
            onDispose {
                MascotSongDropTargetRegistry.setDraggingActive(false)
            }
        }
        LaunchedEffect(isDragging) {
            MascotSongDropTargetRegistry.setDraggingActive(isDragging)
        }

        // Hardware gravity vector (in screen-pixel space: +X = right, +Y = down, -Y = upside-down up!)
        val sensorGravXRef = remember { floatArrayOf(0f) }
        val sensorGravYRef = remember { floatArrayOf(9.81f) }
        val lastShakeMsRef = remember { longArrayOf(0L) }

        // 2-Second Drag & Hold Over Song Row State
        var hoveredSong by remember { mutableStateOf<MediaItem?>(null) }
        var hoveredSongElapsedMs by remember { mutableLongStateOf(0L) }
        val songHoldProgress = (hoveredSongElapsedMs / 2000f).coerceIn(0f, 1f)

        // =========================================================================
        // REAL HARDWARE GRAVITY & ACCELEROMETER SENSOR (LIFECYCLE-SAFE, 0 PERMISSIONS)
        // Tilt phone left/right -> slides & bounces! Turn phone upside-down -> flips 180° & falls to top!
        // =========================================================================
        DisposableEffect(context, lifecycleOwner, gravityPhysicsEnabled) {
            if (!gravityPhysicsEnabled) {
                sensorGravXRef[0] = 0f
                sensorGravYRef[0] = 9.81f
                motion.gravityNormX = 0f
                motion.gravityNormY = 1f
                onDispose {}
            } else {
                val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
                val gravitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
                val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                val activeSensor = gravitySensor ?: accelSensor
                val isRawAccel = gravitySensor == null

                var filteredX = 0f
                var filteredY = 9.81f
                var filteredZ = 0f
                var isRegistered = false

                fun getDisplayRotation(): Int {
                    return try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            context.display?.rotation ?: Surface.ROTATION_0
                        } else {
                            @Suppress("DEPRECATION")
                            (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.rotation
                                ?: Surface.ROTATION_0
                        }
                    } catch (_: Throwable) {
                        Surface.ROTATION_0
                    }
                }

                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        val rawX = event.values[0]
                        val rawY = event.values[1]
                        val rawZ = event.values[2]

                        // Detect quick shake/jolt impulse for playful cartoon tappe pop
                        if (isRawAccel) {
                            val mag = hypot(hypot(rawX, rawY), rawZ)
                            val linearShock = abs(mag - 9.81f)
                            val nowMs = android.os.SystemClock.elapsedRealtime()
                            if (linearShock > 11.5f && nowMs - lastShakeMsRef[0] > 750L && !isDragging) {
                                lastShakeMsRef[0] = nowMs
                                motion.velY -= 16.5f * (if (sensorGravYRef[0] >= 0f) 1f else -1f)
                                motion.velX += (if (motion.posX < screenWidthPx * 0.5f) 9f else -9f)
                                motion.tonguePopTicks = 55
                                motion.happyJumpTicks = 34
                                motion.bounceCount = 0
                                physicsWakeToken++
                            }
                            // Low-pass filter to isolate clean gravity vector from raw accelerometer
                            val alpha = 0.22f
                            filteredX += alpha * (rawX - filteredX)
                            filteredY += alpha * (rawY - filteredY)
                            filteredZ += alpha * (rawZ - filteredZ)
                        } else {
                            // Dedicated hardware gravity sensor (already clean, light smoothing)
                            val alpha = 0.35f
                            filteredX += alpha * (rawX - filteredX)
                            filteredY += alpha * (rawY - filteredY)
                            filteredZ += alpha * (rawZ - filteredZ)
                        }

                        // Map sensor axes to current Display rotation so gravity matches real Earth down
                        val rot = getDisplayRotation()
                        val sx: Float
                        val sy: Float
                        when (rot) {
                            Surface.ROTATION_90 -> {
                                sx = -filteredY
                                sy = filteredX
                            }
                            Surface.ROTATION_180 -> {
                                sx = -filteredX
                                sy = -filteredY
                            }
                            Surface.ROTATION_270 -> {
                                sx = filteredY
                                sy = -filteredX
                            }
                            else -> {
                                sx = filteredX
                                sy = filteredY
                            }
                        }

                        // Screen-pixel gravity: +X = right edge, +Y = bottom floor, -Y = top ceiling (upside-down!)
                        var screenGx = -sx
                        var screenGy = sy

                        // If phone lies completely flat on a table, keep gentle upright downward gravity
                        val planarMag = hypot(screenGx, screenGy)
                        if (planarMag < 2.2f && abs(filteredZ) > 7.5f) {
                            screenGx = 0f
                            screenGy = 9.81f
                        }

                        val prevGx = sensorGravXRef[0]
                        val prevGy = sensorGravYRef[0]
                        sensorGravXRef[0] = screenGx
                        sensorGravYRef[0] = screenGy

                        // Wake physics loop ONLY when it is currently sleeping (0 recompositions while already running!)
                        val isTiltedOrUpsideDown = abs(screenGx) > 2.8f || screenGy < 5.8f
                        if (!isPhysicsLoopRunningRef[0] && isTiltedOrUpsideDown) {
                            physicsWakeToken++
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }

                fun registerSensor() {
                    if (!isRegistered && sensorManager != null && activeSensor != null) {
                        try {
                            sensorManager.registerListener(listener, activeSensor, SensorManager.SENSOR_DELAY_UI)
                            isRegistered = true
                        } catch (_: Throwable) {
                        }
                    }
                }

                fun unregisterSensor() {
                    if (isRegistered && sensorManager != null) {
                        try {
                            sensorManager.unregisterListener(listener)
                        } catch (_: Throwable) {
                        }
                        isRegistered = false
                    }
                }

                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> registerSensor()
                        Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> unregisterSensor()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    registerSensor()
                }

                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    unregisterSensor()
                }
            }
        }

        LaunchedEffect(nowPlayingTitle, isMusicPlaying, breed, danceMode) {
            if (isMusicPlaying && nowPlayingTitle.isNotBlank()) {
                motion.happyJumpTicks = 26
                motion.tonguePopTicks = 45
                physicsWakeToken++
            } else if (!isMusicPlaying) {
                motion.happyJumpTicks = 0
                motion.flip360Ticks = 0
            }
        }

        LaunchedEffect(screenWidthPx, floorY, topBoundY) {
            motion.posX = motion.posX.coerceIn(minBoundX, maxBoundX)
            val isNormalUpright = sensorGravYRef[0] > 6.5f && abs(sensorGravXRef[0]) < 2.5f
            if (!isDragging && isNormalUpright && motion.bounceCount == 0 && abs(motion.velY) < 0.8f) {
                motion.posY = floorY
            } else {
                motion.posY = motion.posY.coerceIn(topBoundY, floorY)
            }
        }

        // =========================================================================
        // 2-SECOND DRAG & HOLD OVER ANY SONG/VIDEO TO AUTO-PLAY + EDGE AUTO-SCROLL
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
                val centerRootX = overlayRootOffset.x + motion.posX + mascotBoxWidthPx * 0.5f
                val centerRootY = overlayRootOffset.y + motion.posY + mascotBoxHeightPx * 0.52f

                if (centerRootY < screenHeightPx * 0.22f) {
                    MascotSongDropTargetRegistry.requestAutoScroll(-16f)
                } else if (centerRootY > screenHeightPx * 0.76f) {
                    MascotSongDropTargetRegistry.requestAutoScroll(16f)
                }

                val hitTrack = MascotSongDropTargetRegistry.findSongAt(centerRootX, centerRootY)
                val isHeldSteady = abs(motion.velX) < 16f && abs(motion.velY) < 16f

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

                        isDragging = false
                        motion.bounceCount = 0
                        motion.velY = -17f
                        motion.velX = 0f
                        motion.happyJumpTicks = 42
                        motion.tonguePopTicks = 60
                        physicsWakeToken++
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
        // 60/120FPS VSYNC 2D GRAVITY, UPSIDE-DOWN FLIP & 4-WALL TAPPE BOUNCE ENGINE
        // 0% IDLE CPU: suspends cleanly once paused, upright, and settled on floor!
        // =========================================================================
        LaunchedEffect(
            isDragging,
            isMusicPlaying,
            danceMode,
            autoWalkEnabled,
            animSpeedMultiplier,
            floorY,
            topBoundY,
            screenWidthPx,
            mascotSizePx,
            showMascotFeatureHub,
            gravityPhysicsEnabled,
            physicsWakeToken
        ) {
            val baseGravityScale = 0.088f // maps 9.81 m/s² -> ~0.86 px/frame² at 60fps
            val bounceRestitution = 0.74f // crisp 5-7 tappe bounces
            val airDrag = 0.992f
            val floorFriction = 0.88f

            var lastFrameNs = 0L
            var prevGravityFlipped = false
            isPhysicsLoopRunningRef[0] = true
            try {
            while (true) {
                val gxRaw = if (gravityPhysicsEnabled) sensorGravXRef[0] else 0f
                val gyRaw = if (gravityPhysicsEnabled) sensorGravYRef[0] else 9.81f
                val gMag = hypot(gxRaw, gyRaw).coerceAtLeast(0.01f)
                val normGx = (gxRaw / 9.81f).coerceIn(-1.2f, 1.2f)
                val normGy = (gyRaw / 9.81f).coerceIn(-1.2f, 1.2f)
                motion.gravityNormX = normGx
                motion.gravityNormY = normGy

                // Dead-zone near upright so phone resting slightly tilted on hand stays super calm
                val effectiveGx = if (abs(gxRaw) < 1.65f && gyRaw > 6.5f) 0f else gxRaw
                val effectiveGy = if (abs(gxRaw) < 1.65f && gyRaw > 6.5f) 9.81f else gyRaw

                val isPhoneUpsideDown = gravityPhysicsEnabled && effectiveGy < -1.5f
                val isPhoneSideTilted = gravityPhysicsEnabled && abs(effectiveGx) > 2.3f
                val isGravityNonStandard = isPhoneUpsideDown || isPhoneSideTilted

                // Detect sudden upright <-> upside-down flip for instant playful pop & tongue out!
                if (isPhoneUpsideDown != prevGravityFlipped) {
                    prevGravityFlipped = isPhoneUpsideDown
                    motion.tonguePopTicks = 65
                    motion.bounceCount = 0
                }

                // Target cartoon body rotation matching real physical Earth gravity (0° upright, ±90° sideways, 180° upside-down!)
                val targetBodyRotDeg = if (!gravityPhysicsEnabled || (!isGravityNonStandard && !isDragging)) {
                    0f
                } else {
                    (atan2(-effectiveGx, effectiveGy) * (180.0 / PI)).toFloat()
                }

                val isSettledOnFloor = !isGravityNonStandard &&
                    abs(motion.posY - floorY) <= 1.8f &&
                    abs(motion.velY) < 0.65f &&
                    abs(motion.velX) < 0.65f &&
                    abs(shortestAngleDeltaDeg(motion.bodyRotationDeg, 0f)) < 1.5f

                val hasActiveMotion = isDragging ||
                    !isSettledOnFloor ||
                    isGravityNonStandard ||
                    motion.happyJumpTicks > 0 ||
                    motion.flip360Ticks > 0 ||
                    motion.landingSquashTicks > 0 ||
                    motion.tonguePopTicks > 0 ||
                    abs(motion.impactSquashX - 1f) > 0.01f ||
                    abs(motion.impactSquashY - 1f) > 0.01f

                if (!isMusicPlaying && !hasActiveMotion && motion.danceTransition <= 0.005f) {
                    motion.danceTransition = 0f
                    motion.isActivelyWalking = false
                    motion.isLiftedInAir = false
                    motion.isUpsideDownOrFalling = false
                    motion.posY = floorY
                    motion.velX = 0f
                    motion.velY = 0f
                    motion.bodyRotationDeg = 0f
                    motion.impactSquashX = 1f
                    motion.impactSquashY = 1f
                    motion.bounceCount = 0
                    break
                }

                val dt = withFrameNanos { frameNs ->
                    val delta = if (lastFrameNs == 0L) 0.016f else ((frameNs - lastFrameNs) / 1_000_000_000f).coerceIn(0.004f, 0.033f)
                    lastFrameNs = frameNs
                    delta
                }
                val step = dt * 60f // normalized to 1.0 at 60fps, 0.5 at 120fps for identical smooth motion!
                val speedFactor = animSpeedMultiplier.coerceIn(0.6f, 2.2f)

                // Smoothly spring body rotation toward real gravity vector
                val angleDelta = shortestAngleDeltaDeg(motion.bodyRotationDeg, targetBodyRotDeg)
                val rotLerp = (0.16f * step).coerceAtMost(0.45f)
                motion.bodyRotationDeg = (motion.bodyRotationDeg + angleDelta * rotLerp)

                // Smoothly recover squash & stretch back to (1f, 1f)
                val squashRecover = (0.20f * step).coerceAtMost(0.55f)
                motion.impactSquashX += (1f - motion.impactSquashX) * squashRecover
                motion.impactSquashY += (1f - motion.impactSquashY) * squashRecover

                // Fluid multi-step transition into/out of dancing when playback starts/stops
                if (isMusicPlaying || motion.happyJumpTicks > 0) {
                    motion.danceTransition = (motion.danceTransition + 0.055f * step).coerceAtMost(1f)
                } else {
                    motion.danceTransition = (motion.danceTransition - 0.16f * step).coerceAtLeast(0f)
                }

                // Advance cartoon animation clock when playing, falling, tumbling, or performing stunts
                if (isMusicPlaying || hasActiveMotion || motion.danceTransition > 0.005f) {
                    val activeRate = if (isMusicPlaying || hasActiveMotion) 0.21f else 0.14f * motion.danceTransition
                    motion.animTick += activeRate * speedFactor * step
                }

                val tickDec = step.roundToInt().coerceAtLeast(1)
                if (motion.happyJumpTicks > 0) motion.happyJumpTicks = (motion.happyJumpTicks - tickDec).coerceAtLeast(0)
                if (motion.flip360Ticks > 0) motion.flip360Ticks = (motion.flip360Ticks - tickDec).coerceAtLeast(0)
                if (motion.landingSquashTicks > 0) motion.landingSquashTicks = (motion.landingSquashTicks - tickDec).coerceAtLeast(0)
                if (motion.tonguePopTicks > 0) motion.tonguePopTicks = (motion.tonguePopTicks - tickDec).coerceAtLeast(0)

                if (!isDragging) {
                    val isFreeFallingOrTapping = isGravityNonStandard ||
                        motion.posY < floorY - 1.8f ||
                        abs(motion.velY) > 0.7f ||
                        abs(motion.velX) > 0.7f

                    if (isFreeFallingOrTapping) {
                        // Full 2D real-gravity integration (up, down, left, right!)
                        motion.velX = (motion.velX + effectiveGx * baseGravityScale * step) * airDrag
                        motion.velY = (motion.velY + effectiveGy * baseGravityScale * step) * airDrag

                        // Clamp max terminal velocity so character stays readable and cartoon-bouncy
                        motion.velX = motion.velX.coerceIn(-36f, 36f)
                        motion.velY = motion.velY.coerceIn(-38f, 38f)

                        motion.posX += motion.velX * step
                        motion.posY += motion.velY * step
                        motion.walkStridePhase += 0.38f * speedFactor * step

                        // 1. TOP CEILING COLLISION & TAPPE BOUNCE (especially when phone is upside-down!)
                        if (motion.posY <= topBoundY) {
                            motion.posY = topBoundY
                            val impactSpeed = abs(motion.velY)
                            if (impactSpeed > 2.2f) {
                                motion.landingSquashTicks = 12
                                motion.impactSquashX = (1f + (impactSpeed * 0.014f).coerceAtMost(0.28f))
                                motion.impactSquashY = (1f - (impactSpeed * 0.012f).coerceAtMost(0.24f))
                                motion.tonguePopTicks = max(motion.tonguePopTicks, 32)
                                if (motion.bounceCount < 7) {
                                    motion.bounceCount++
                                    motion.velY = impactSpeed * bounceRestitution
                                    motion.velX *= 0.86f
                                } else {
                                    motion.velY = 0f
                                    motion.velX *= floorFriction
                                }
                            } else {
                                motion.velY = 0f
                                motion.velX *= floorFriction
                                if (isPhoneUpsideDown) motion.bounceCount = 0
                            }
                        }

                        // 2. BOTTOM FLOOR COLLISION & 5-7 TAPPE BOUNCES
                        if (motion.posY >= floorY) {
                            motion.posY = floorY
                            val impactSpeed = abs(motion.velY)
                            if (impactSpeed > 2.1f) {
                                motion.landingSquashTicks = 12
                                motion.impactSquashX = (1f + (impactSpeed * 0.014f).coerceAtMost(0.28f))
                                motion.impactSquashY = (1f - (impactSpeed * 0.012f).coerceAtMost(0.24f))
                                val boostedImpact = if (!isGravityNonStandard && motion.bounceCount == 0 && impactSpeed < 16.5f && impactSpeed > 4.5f) {
                                    17.5f
                                } else {
                                    impactSpeed
                                }
                                val nextUp = boostedImpact * bounceRestitution
                                if (motion.bounceCount < 7 && nextUp >= 2.2f) {
                                    motion.bounceCount++
                                    motion.velY = -nextUp
                                    motion.velX *= 0.84f
                                } else {
                                    motion.bounceCount = 0
                                    motion.velY = 0f
                                    motion.velX *= floorFriction
                                }
                            } else {
                                motion.bounceCount = 0
                                motion.velY = 0f
                                motion.velX *= floorFriction
                            }
                        }

                        // 3. LEFT WALL COLLISION & SIDE TAPPE BOUNCE
                        if (motion.posX <= minBoundX) {
                            motion.posX = minBoundX
                            val impactSpeed = abs(motion.velX)
                            if (impactSpeed > 2.2f) {
                                motion.landingSquashTicks = 10
                                motion.impactSquashX = (1f - (impactSpeed * 0.012f).coerceAtMost(0.22f))
                                motion.impactSquashY = (1f + (impactSpeed * 0.014f).coerceAtMost(0.26f))
                                motion.velX = impactSpeed * bounceRestitution
                                motion.walkDir = 1f
                            } else {
                                motion.velX = 0f
                                motion.velY *= floorFriction
                                motion.walkDir = 1f
                            }
                        }

                        // 4. RIGHT WALL COLLISION & SIDE TAPPE BOUNCE
                        if (motion.posX >= maxBoundX) {
                            motion.posX = maxBoundX
                            val impactSpeed = abs(motion.velX)
                            if (impactSpeed > 2.2f) {
                                motion.landingSquashTicks = 10
                                motion.impactSquashX = (1f - (impactSpeed * 0.012f).coerceAtMost(0.22f))
                                motion.impactSquashY = (1f + (impactSpeed * 0.014f).coerceAtMost(0.26f))
                                motion.velX = -impactSpeed * bounceRestitution
                                motion.walkDir = -1f
                            } else {
                                motion.velX = 0f
                                motion.velY *= floorFriction
                                motion.walkDir = -1f
                            }
                        }

                        val speedMag = hypot(motion.velX, motion.velY)
                        motion.isLiftedInAir = (!isGravityNonStandard && motion.posY < floorY - 16f) || speedMag > 5.5f
                        motion.isUpsideDownOrFalling = isPhoneUpsideDown || speedMag > 7.5f || abs(motion.bodyRotationDeg) > 48f
                        motion.isActivelyWalking = speedMag > 1.5f || isMusicPlaying
                    } else {
                        // Calmly grounded on upright floor
                        motion.posY = floorY
                        motion.velX = 0f
                        motion.velY = 0f
                        motion.bounceCount = 0
                        motion.isLiftedInAir = false
                        motion.isUpsideDownOrFalling = false

                        val shouldPatrol = isMusicPlaying && (danceMode == MascotDanceMode.PATROL_WALK || autoWalkEnabled && danceMode == MascotDanceMode.AUTO_MIX && ((motion.animTick / 12f).toInt() % 3 == 2)) && !showMascotFeatureHub
                        if (shouldPatrol) {
                            motion.isActivelyWalking = true
                            val walkStep = 2.0f * speedFactor * motion.danceTransition * step
                            motion.posX += motion.walkDir * walkStep
                            motion.walkStridePhase += 0.44f * speedFactor * motion.danceTransition * step
                            if (motion.posX >= maxBoundX - 4f) {
                                motion.posX = maxBoundX - 4f
                                motion.walkDir = -1f
                            } else if (motion.posX <= minBoundX + 4f) {
                                motion.posX = minBoundX + 4f
                                motion.walkDir = 1f
                            }
                        } else if (isMusicPlaying || motion.danceTransition > 0.01f) {
                            motion.isActivelyWalking = isMusicPlaying
                            motion.walkStridePhase += 0.40f * speedFactor * motion.danceTransition * step
                        } else {
                            motion.isActivelyWalking = false
                        }
                    }
                } else {
                    motion.isLiftedInAir = true
                    motion.isUpsideDownOrFalling = isPhoneUpsideDown
                }
            }
            } finally {
                isPhysicsLoopRunningRef[0] = false
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
            // 1. 100% CLEAN, UNOBSTRUCTED 3D CARTOON MASCOT ("SIRF GUDDA HONA CHAHIYE AUR KUCH NAHI")
            // =========================================================================
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            motion.posX.roundToInt(),
                            motion.posY.roundToInt()
                        )
                    }
                    .size(width = effectiveMascotSizeDp + 16.dp, height = effectiveMascotSizeDp + 20.dp)
                    .pointerInput(isMusicPlaying, breed, danceMode) {
                        detectTapGestures(
                            onTap = {
                                motion.bounceCount = 0
                                val gDirY = if (sensorGravYRef[0] >= 0f) -1f else 1f
                                motion.velY = 20.5f * gDirY
                                motion.velX = if (motion.posX < screenWidthPx * 0.5f) 4.5f else -4.5f
                                motion.happyJumpTicks = 38
                                motion.tonguePopTicks = 55
                                physicsWakeToken++
                            },
                            onDoubleTap = {
                                try {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                } catch (_: Exception) {}
                                val wasPlaying = isMusicPlaying
                                onDoubleTapPauseMusic()
                                if (wasPlaying) {
                                    motion.happyJumpTicks = 0
                                    motion.flip360Ticks = 0
                                    Toast.makeText(
                                        context,
                                        "🐾 Music Paused by ${breed.displayName}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    motion.bounceCount = 0
                                    motion.velY = -19f
                                    motion.happyJumpTicks = 38
                                    motion.tonguePopTicks = 55
                                    physicsWakeToken++
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
                    .pointerInput(screenWidthPx, floorY, topBoundY) {
                        detectDragGestures(
                            onDragStart = {
                                isDragging = true
                                motion.isLiftedInAir = true
                                motion.bounceCount = 0
                                motion.velX = 0f
                                motion.velY = 0f
                                motion.tonguePopTicks = 45
                                physicsWakeToken++
                            },
                            onDragEnd = {
                                isDragging = false
                                motion.bounceCount = 0
                                if (motion.posY < floorY - 8f && abs(motion.velY) < 6f) {
                                    motion.velY = -11.5f
                                }
                                physicsWakeToken++
                            },
                            onDragCancel = {
                                isDragging = false
                                motion.bounceCount = 0
                                physicsWakeToken++
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                motion.posX = (motion.posX + dragAmount.x).coerceIn(minBoundX, maxBoundX)
                                motion.posY = (motion.posY + dragAmount.y).coerceIn(topBoundY, floorY)
                                motion.velX = (dragAmount.x * 0.90f).coerceIn(-34f, 34f)
                                motion.velY = (dragAmount.y * 1.15f).coerceIn(-38f, 32f)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // ONLY draw the 2-second hold progress ring when actively holding the mascot over a song/video row
                if (isDragging && songHoldProgress > 0.01f) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val ringStroke = 5.dp.toPx()
                        val ringDiameter = min(w, h) * 0.92f
                        val ringTopLeft = Offset((w - ringDiameter) * 0.5f, (h - ringDiameter) * 0.50f)
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
                }

                // 100% CLEAN 3D CARTOON MASCOT WITH GPU GRAVITY ROTATION & SQUASH-AND-STRETCH
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.55f)
                            val cartwheelZ = if (motion.flip360Ticks > 0) {
                                (1f - (motion.flip360Ticks / 30f).coerceIn(0f, 1f)) * 360f
                            } else 0f
                            val dragTiltZ = if (isDragging) (motion.velX * 0.75f).coerceIn(-24f, 24f) else 0f
                            rotationZ = motion.bodyRotationDeg + cartwheelZ + dragTiltZ
                            scaleX = motion.impactSquashX
                            scaleY = motion.impactSquashY
                        }
                ) {
                    DeformingJumping3DPlushMascot(
                        breed = breed,
                        parts = null,
                        isMusicPlaying = isMusicPlaying,
                        danceMode = danceMode,
                        auraColor = auraEffect.primaryColor,
                        customFurColor = activeFurColorPreset.furColor,
                        drawInternalFloorShadow = false,
                        liveMotion = motion,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // =========================================================================
            // 2. ON-MASCOT INTERACTIVE FEATURE STUDIO DOCK (Long-Press Gudda)
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
                                text = "Real Gravity Flip • Tappe Bounce • Tongue & 8 Dances",
                                color = breed.headphoneAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Sky Toss + Tappe Bounce Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF00E5FF).copy(alpha = 0.22f))
                                    .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(50))
                                    .clickable {
                                        motion.bounceCount = 0
                                        motion.velY = -36f
                                        motion.velX = if (motion.posX < screenWidthPx * 0.5f) 12f else -12f
                                        motion.happyJumpTicks = 60
                                        motion.flip360Ticks = 32
                                        motion.tonguePopTicks = 70
                                        physicsWakeToken++
                                    }
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "🚀 Tappe Toss",
                                    color = Color(0xFF00E5FF),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.sp
                                )
                            }

                            // 360° Flip + Tongue Stunt Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFFFF4081).copy(alpha = 0.22f))
                                    .border(1.dp, Color(0xFFFF4081), RoundedCornerShape(50))
                                    .clickable {
                                        motion.bounceCount = 0
                                        motion.velY = -24f
                                        motion.happyJumpTicks = 48
                                        motion.flip360Ticks = 32
                                        motion.tonguePopTicks = 75
                                        physicsWakeToken++
                                    }
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "👅 360° Flip",
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
                                        motion.happyJumpTicks = 35
                                        motion.tonguePopTicks = 45
                                        physicsWakeToken++
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
                                        motion.happyJumpTicks = 30
                                        physicsWakeToken++
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
                                        motion.bounceCount = 0
                                        motion.velY = -18f
                                        motion.happyJumpTicks = 36
                                        motion.tonguePopTicks = 50
                                        physicsWakeToken++
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
                                        motion.happyJumpTicks = 28
                                        physicsWakeToken++
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
                                        motion.happyJumpTicks = 34
                                        motion.flip360Ticks = 30
                                        motion.tonguePopTicks = 50
                                        physicsWakeToken++
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

                    // 4. Mini DJ Deck + Real Gravity Toggle + Auto-Walk + Speed + Size Controls
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

                        // Real Hardware Gravity & Upside-Down Flip Toggle
                        GuddaActionChip(
                            if (gravityPhysicsEnabled) "🌍 Gravity: ON" else "🌍 Gravity: OFF",
                            if (gravityPhysicsEnabled) Color(0xFF00E5FF) else Color(0xFF90A4AE),
                            filled = gravityPhysicsEnabled
                        ) {
                            val nextVal = !gravityPhysicsEnabled
                            gravityPhysicsEnabled = nextVal
                            prefs.edit().putBoolean("mascot_gravity_physics_enabled", nextVal).apply()
                            physicsWakeToken++
                        }

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
