package com.example.helpers

import android.graphics.Paint
import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 3D Studio Animals + Fluffy Cassette Puppies & Teddy Bear Mascots + Chibi Companions:
 * Features the signature 3D Golden Lion Cub ("Sher"), Studio Bunny, and Polar Bear wearing
 * over-ear studio DJ headphones with rotary dial volume calibrations, plus 10 Fluffy Cassette
 * Puppies & Teddy Bears and 6 Chibi Penguins!
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
    val wearsHeadphonesAlways: Boolean = true,
    val isFluffyCassetteMascot: Boolean = false,
    val isTeddyBear: Boolean = false,
    val isLion: Boolean = false,
    val isBunny: Boolean = false,
    val isPolarBear: Boolean = false,
    val isStudioHeadphonesMascot: Boolean = false,
    val hatPrimaryColor: Color = Color(0xFFFFB74D),
    val hatCheckColor: Color = Color(0xFF4DD0E1),
    val hornColor: Color = Color(0xFF80CBC4),
    val cassetteShellColor: Color = Color(0xFFFFCC80),
    val cassetteAccentColor: Color = Color(0xFF26C6DA),
    val cassetteLabel: String = "MIX TAPE",
    val hatBadgeText: String = "PLAY"
) {
    // ================= NEW SIGNATURE 3D STUDIO DJ ANIMALS (LION, BUNNY, POLAR BEAR) =================
    GOLDEN_LION_DJ(
        displayName = "Golden Lion DJ 🦁",
        primaryFur = Color(0xFFF9C66B),
        highlightFur = Color(0xFFFFF4D4),
        shadowFur = Color(0xFFD69438),
        earColor = Color(0xFFEA9E34),
        muzzlePatch = Color(0xFFFFF9E8),
        headphoneAccent = Color(0xFF26E7A6),
        isLion = true,
        isStudioHeadphonesMascot = true,
        cassetteLabel = "LION GROOVE",
        hatBadgeText = "SHER"
    ),
    WHITE_BUNNY_DJ(
        displayName = "Studio Bunny DJ 🐰",
        primaryFur = Color(0xFFF7F8FA),
        highlightFur = Color(0xFFFFFFFF),
        shadowFur = Color(0xFFD3D8E2),
        earColor = Color(0xFFFFB2C9),
        muzzlePatch = Color(0xFFFFFFFF),
        headphoneAccent = Color(0xFFFF4081),
        isBunny = true,
        isStudioHeadphonesMascot = true,
        cassetteLabel = "BUNNY BEAT",
        hatBadgeText = "BUNNY"
    ),
    SNOW_BEAR_DJ(
        displayName = "Polar Bear DJ 🐻‍❄️",
        primaryFur = Color(0xFFEFF2F6),
        highlightFur = Color(0xFFFFFFFF),
        shadowFur = Color(0xFFBDC7D5),
        earColor = Color(0xFFE2E7ED),
        muzzlePatch = Color(0xFFFBFDFF),
        headphoneAccent = Color(0xFF00E5FF),
        isPolarBear = true,
        isStudioHeadphonesMascot = true,
        cassetteLabel = "POLAR MIX",
        hatBadgeText = "POLAR"
    ),

    // ================= 10 NEW 100% DISTINCT 3D FLUFFY CASSETTE PUPPY & TEDDY MASCOTS =================
    HONEY_BEAT_TEDDY(
        displayName = "Honey Beat Teddy 🧸",
        primaryFur = Color(0xFFF6B24B),
        highlightFur = Color(0xFFFFECB3),
        shadowFur = Color(0xFFC67C1B),
        earColor = Color(0xFFD98A26),
        muzzlePatch = Color(0xFFFFF8E1),
        headphoneAccent = Color(0xFF26E7A6),
        isFluffyCassetteMascot = true,
        isTeddyBear = true,
        hatPrimaryColor = Color(0xFF4DD0E1),
        hatCheckColor = Color(0xFFFFF59D),
        hornColor = Color(0xFFFF8A65),
        cassetteShellColor = Color(0xFFFFD54F),
        cassetteAccentColor = Color(0xFF00E5FF),
        cassetteLabel = "HONEY TAPE",
        hatBadgeText = "BEAT"
    ),
    SKY_DJ_PUPPY(
        displayName = "Sky DJ Puppy 🐶",
        primaryFur = Color(0xFF64B5F6),
        highlightFur = Color(0xFFE3F2FD),
        shadowFur = Color(0xFF2B6CB0),
        earColor = Color(0xFF4299E1),
        muzzlePatch = Color(0xFFC88A5B),
        headphoneAccent = Color(0xFFFF8A80),
        isFluffyCassetteMascot = true,
        isTeddyBear = false,
        hatPrimaryColor = Color(0xFFFFCC80),
        hatCheckColor = Color(0xFF81D4FA),
        hornColor = Color(0xFF69F0AE),
        cassetteShellColor = Color(0xFF80DEEA),
        cassetteAccentColor = Color(0xFFFFAB91),
        cassetteLabel = "SKY PUPPY",
        hatBadgeText = "PLAY"
    ),
    STRAWBERRY_VIBE_TEDDY(
        displayName = "Berry Vibe Teddy 🧸",
        primaryFur = Color(0xFFFF7597),
        highlightFur = Color(0xFFFFE4EC),
        shadowFur = Color(0xFFC93B63),
        earColor = Color(0xFFE5537D),
        muzzlePatch = Color(0xFFFFF0F5),
        headphoneAccent = Color(0xFF40C4FF),
        isFluffyCassetteMascot = true,
        isTeddyBear = true,
        hatPrimaryColor = Color(0xFF69F0AE),
        hatCheckColor = Color(0xFFFFF59D),
        hornColor = Color(0xFFFFD54F),
        cassetteShellColor = Color(0xFFFF80AB),
        cassetteAccentColor = Color(0xFF64FFDA),
        cassetteLabel = "BERRY MIX",
        hatBadgeText = "VIBE"
    ),
    MOCHA_STAR_PUPPY(
        displayName = "Mocha Star Puppy 🐶",
        primaryFur = Color(0xFFD79968),
        highlightFur = Color(0xFFFFF3E0),
        shadowFur = Color(0xFF8D5524),
        earColor = Color(0xFFA66838),
        muzzlePatch = Color(0xFF6D4021),
        headphoneAccent = Color(0xFF64FFDA),
        isFluffyCassetteMascot = true,
        isTeddyBear = false,
        hatPrimaryColor = Color(0xFFFFD54F),
        hatCheckColor = Color(0xFFFFAB91),
        hornColor = Color(0xFF4DD0E1),
        cassetteShellColor = Color(0xFFFFCC80),
        cassetteAccentColor = Color(0xFF4DD0E1),
        cassetteLabel = "MOCHA TAPE",
        hatBadgeText = "STAR"
    ),
    MINT_GROOVE_TEDDY(
        displayName = "Mint Groove Teddy 🧸",
        primaryFur = Color(0xFF4DB6AC),
        highlightFur = Color(0xFFE0F2F1),
        shadowFur = Color(0xFF00796B),
        earColor = Color(0xFF26A69A),
        muzzlePatch = Color(0xFFF1F8E9),
        headphoneAccent = Color(0xFFFFD740),
        isFluffyCassetteMascot = true,
        isTeddyBear = true,
        hatPrimaryColor = Color(0xFFFF8A65),
        hatCheckColor = Color(0xFFFFE082),
        hornColor = Color(0xFFFFD54F),
        cassetteShellColor = Color(0xFF80CBC4),
        cassetteAccentColor = Color(0xFFFF8A80),
        cassetteLabel = "MINT BEAT",
        hatBadgeText = "GROOVE"
    ),
    SUNNY_BASS_PUPPY(
        displayName = "Sunny Bass Puppy 🐶",
        primaryFur = Color(0xFFFFCA28),
        highlightFur = Color(0xFFFFFDE7),
        shadowFur = Color(0xFFD68910),
        earColor = Color(0xFFF59E0B),
        muzzlePatch = Color(0xFF9C5823),
        headphoneAccent = Color(0xFFFF4081),
        isFluffyCassetteMascot = true,
        isTeddyBear = false,
        hatPrimaryColor = Color(0xFF29B6F6),
        hatCheckColor = Color(0xFFB3E5FC),
        hornColor = Color(0xFFFF8A80),
        cassetteShellColor = Color(0xFFFFE082),
        cassetteAccentColor = Color(0xFF29B6F6),
        cassetteLabel = "SUNNY PUP",
        hatBadgeText = "BASS"
    ),
    PEACH_REMIX_TEDDY(
        displayName = "Peach Remix Teddy 🧸",
        primaryFur = Color(0xFFFF9E80),
        highlightFur = Color(0xFFFFF3E0),
        shadowFur = Color(0xFFD85A34),
        earColor = Color(0xFFF47A55),
        muzzlePatch = Color(0xFFFFF8F2),
        headphoneAccent = Color(0xFF69F0AE),
        isFluffyCassetteMascot = true,
        isTeddyBear = true,
        hatPrimaryColor = Color(0xFF42A5F5),
        hatCheckColor = Color(0xFFBBDEFB),
        hornColor = Color(0xFFFFF176),
        cassetteShellColor = Color(0xFFFFAB91),
        cassetteAccentColor = Color(0xFF80D8FF),
        cassetteLabel = "PEACH TAPE",
        hatBadgeText = "REMIX"
    ),
    SNOWY_TURBO_PUPPY(
        displayName = "Snowy Turbo Puppy 🐶",
        primaryFur = Color(0xFFCFD8DC),
        highlightFur = Color(0xFFFFFFFF),
        shadowFur = Color(0xFF78909C),
        earColor = Color(0xFF90A4AE),
        muzzlePatch = Color(0xFF8D5B4C),
        headphoneAccent = Color(0xFF18FFFF),
        isFluffyCassetteMascot = true,
        isTeddyBear = false,
        hatPrimaryColor = Color(0xFFF06292),
        hatCheckColor = Color(0xFFF8BBD0),
        hornColor = Color(0xFFFFD54F),
        cassetteShellColor = Color(0xFF80DEEA),
        cassetteAccentColor = Color(0xFFF48FB1),
        cassetteLabel = "SNOWY PUP",
        hatBadgeText = "TURBO"
    ),
    PISTACHIO_LOFI_TEDDY(
        displayName = "Pistachio Lofi Teddy 🧸",
        primaryFur = Color(0xFF9CCC65),
        highlightFur = Color(0xFFF1F8E9),
        shadowFur = Color(0xFF558B2F),
        earColor = Color(0xFF7CB342),
        muzzlePatch = Color(0xFFFFFDE7),
        headphoneAccent = Color(0xFF40C4FF),
        isFluffyCassetteMascot = true,
        isTeddyBear = true,
        hatPrimaryColor = Color(0xFFFFAB40),
        hatCheckColor = Color(0xFFFFE0B2),
        hornColor = Color(0xFF80DEEA),
        cassetteShellColor = Color(0xFFC5E1A5),
        cassetteAccentColor = Color(0xFFFFCC80),
        cassetteLabel = "LOFI TAPE",
        hatBadgeText = "LOFI"
    ),
    CINNAMON_WAVE_PUPPY(
        displayName = "Cinnamon Wave Puppy 🐶",
        primaryFur = Color(0xFFE57373),
        highlightFur = Color(0xFFFFEBEE),
        shadowFur = Color(0xFFA93226),
        earColor = Color(0xFFC0392B),
        muzzlePatch = Color(0xFF6E2C00),
        headphoneAccent = Color(0xFFFFD740),
        isFluffyCassetteMascot = true,
        isTeddyBear = false,
        hatPrimaryColor = Color(0xFF26C6DA),
        hatCheckColor = Color(0xFFB2EBF2),
        hornColor = Color(0xFFFFF176),
        cassetteShellColor = Color(0xFFFFAB91),
        cassetteAccentColor = Color(0xFF4DD0E1),
        cassetteLabel = "WAVE PUP",
        hatBadgeText = "WAVE"
    ),

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

    // ================= CLASSIC STUDIO PETS =================
    MUSO_HEADPHONE_PUPPY(
        displayName = "Muso Headphone Puppy (DJ)",
        primaryFur = Color(0xFFF4A259),
        highlightFur = Color(0xFFFFE0B2),
        shadowFur = Color(0xFFC8762E),
        earColor = Color(0xFF8D4E1F),
        muzzlePatch = Color(0xFFFFF8E7),
        headphoneAccent = Color(0xFF00E676),
        isFluffyCassetteMascot = true,
        isTeddyBear = false,
        hatPrimaryColor = Color(0xFF00E676),
        hatCheckColor = Color(0xFFB9F6CA),
        hornColor = Color(0xFFFFD54F),
        cassetteShellColor = Color(0xFFFFCC80),
        cassetteAccentColor = Color(0xFF00E676),
        cassetteLabel = "MUSO PUP",
        hatBadgeText = "DJ"
    ),
    GOLDEN_RETRIEVER(
        displayName = "Golden Retriever Puppy",
        primaryFur = Color(0xFFEFA84A),
        highlightFur = Color(0xFFFFDF9E),
        shadowFur = Color(0xFFB56E1D),
        earColor = Color(0xFF9C5814),
        muzzlePatch = Color(0xFF8D5524),
        headphoneAccent = Color(0xFF00E5FF),
        isFluffyCassetteMascot = true,
        isTeddyBear = false,
        hatPrimaryColor = Color(0xFF00E5FF),
        hatCheckColor = Color(0xFFB2EBF2),
        hornColor = Color(0xFFFFAB91),
        cassetteShellColor = Color(0xFFFFE082),
        cassetteAccentColor = Color(0xFF00E5FF),
        cassetteLabel = "GOLDEN PUP",
        hatBadgeText = "PLAY"
    ),
    POMERANIAN_TEDDY(
        displayName = "Pomeranian Teddy",
        primaryFur = Color(0xFFFF8A50),
        highlightFur = Color(0xFFFFD180),
        shadowFur = Color(0xFFC65100),
        earColor = Color(0xFF9B3A00),
        muzzlePatch = Color(0xFFFFF3E0),
        headphoneAccent = Color(0xFFFF4081),
        isFluffyCassetteMascot = true,
        isTeddyBear = true,
        hatPrimaryColor = Color(0xFFFF4081),
        hatCheckColor = Color(0xFFF8BBD0),
        hornColor = Color(0xFF69F0AE),
        cassetteShellColor = Color(0xFFFFCCBC),
        cassetteAccentColor = Color(0xFFFF4081),
        cassetteLabel = "POM TEDDY",
        hatBadgeText = "CUTE"
    ),
    PLUSH_BEAR(
        displayName = "Plush Bear",
        primaryFur = Color(0xFF26A69A),
        highlightFur = Color(0xFFB2DFDB),
        shadowFur = Color(0xFF00695C),
        earColor = Color(0xFFFFCA28),
        muzzlePatch = Color(0xFFE0F2F1),
        headphoneAccent = Color(0xFFFFD54F),
        isFluffyCassetteMascot = true,
        isTeddyBear = true,
        hatPrimaryColor = Color(0xFFFFCA28),
        hatCheckColor = Color(0xFFFFF59D),
        hornColor = Color(0xFFFF8A65),
        cassetteShellColor = Color(0xFF80CBC4),
        cassetteAccentColor = Color(0xFFFFCA28),
        cassetteLabel = "PLUSH MIX",
        hatBadgeText = "PLAY"
    );

    companion object {
        val FEATURED_STUDIO_ANIMALS: List<CompanionBreed>
            get() = listOf(GOLDEN_LION_DJ, WHITE_BUNNY_DJ, SNOW_BEAR_DJ)

        val FLUFFY_CASSETTE_MASCOTS: List<CompanionBreed>
            get() = entries.filter { it.isFluffyCassetteMascot }

        val PENGUIN_BREEDS: List<CompanionBreed>
            get() = entries.filter { it.isPenguin }
    }
}

/**
 * Timed Media Hold Gesture Modifier:
 * - Quick tap (<800ms): regular click / open
 * - 2-second hold: automatically plays track/video
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
 * Reusable 3D Studio Portrait Composable for showcasing and saving the 3D Fluffy Cassette
 * Puppies, Lion & Teddy Bear Mascots in the Me Section & Companion Gallery!
 */
@Composable
fun FluffyMascot3DStudioPortrait(
    breed: CompanionBreed,
    modifier: Modifier = Modifier,
    showStudioBackdrop: Boolean = true,
    animateReels: Boolean = true
) {
    var animTick by remember { mutableFloatStateOf(0.8f) }
    LaunchedEffect(animateReels) {
        if (animateReels) {
            while (true) {
                delay(32L)
                animTick += 0.12f
            }
        }
    }

    Canvas(modifier = modifier) {
        if (breed.isPenguin) {
            if (showStudioBackdrop) {
                drawPastel3DStudioBackdrop(breed)
            }
            drawColorfulChibiPenguin(
                breed = breed,
                animTick = animTick,
                isGrooving = true,
                happyJumpTicks = 0
            )
        } else if (breed.isStudioHeadphonesMascot) {
            if (showStudioBackdrop) {
                drawPastel3DStudioBackdrop(breed)
            }
            draw3DStudioDJMascot(
                breed = breed,
                animTick = animTick,
                isGrooving = true,
                happyJumpTicks = 0
            )
        } else {
            if (showStudioBackdrop) {
                drawPastel3DStudioBackdrop(breed)
            }
            draw3DFluffyCassetteMascot(
                breed = breed,
                animTick = animTick,
                isGrooving = true,
                happyJumpTicks = 0
            )
        }
    }
}

/**
 * Interactive 3D Companion Overlay supporting the 3D Golden Lion DJ ("Sher"),
 * Studio Bunny, Polar Bear, and Fluffy Cassette Puppies & Teddy Bears:
 * - Upward Fling & Gravity: Throwing upward sends mascot flying up, bouncing off top/ceiling
 *   and falling gracefully under gravity over 2 to 3 seconds with floor landing bounce!
 * - Dance: Mascots dance strictly when music is playing (isMusicPlaying == true). When music stops,
 *   they stay calm, peaceful, and still.
 * - Resizable: Accepts dynamic mascotSizeDp (64dp to 132dp) from PlayerViewModel.
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
    val floorY = (screenHeightPx - mascotSizePx - with(density) { 110.dp.toPx() }).coerceAtLeast(80f)

    var penguinCycleIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(penguinsOnlyMode) {
        if (penguinsOnlyMode) {
            while (true) {
                delay(14_000L)
                penguinCycleIndex = (penguinCycleIndex + 1) % CompanionBreed.PENGUIN_BREEDS.size
            }
        }
    }

    val breed = remember(breedName, penguinsOnlyMode, penguinCycleIndex) {
        val selected = CompanionBreed.entries.find {
            it.displayName == breedName || it.name == breedName
        } ?: CompanionBreed.GOLDEN_LION_DJ
        if (penguinsOnlyMode) {
            if (selected.isPenguin && penguinCycleIndex == 0) selected
            else CompanionBreed.PENGUIN_BREEDS[penguinCycleIndex % CompanionBreed.PENGUIN_BREEDS.size]
        } else {
            selected
        }
    }

    var posX by remember { mutableFloatStateOf(with(density) { 16.dp.toPx() }) }
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
            val delayMs = if (isDragging || posY < floorY - 1.5f || abs(velY) > 0.8f) 16L
                          else if (isGrooving) 24L
                          else 64L
            delay(delayMs)

            animTick += if (isGrooving) 0.18f else 0.02f
            if (happyJumpTicks > 0) happyJumpTicks--

            if (!isDragging) {
                if (posY < floorY - 1.5f || abs(velY) > 0.8f) {
                    // Upward flight & smooth realistic gravity
                    velY += 1.25f
                    posX += velX
                    posY += velY

                    // Top ceiling collision bounce ("upar jaakar takra ke wapas aaye")
                    if (posY <= 25f) {
                        posY = 25f
                        velY = abs(velY) * 0.45f
                        velX = (velX * 0.85f) + if (velX >= 0) 3.0f else -3.0f
                    }

                    // Left & Right screen boundary wall bounce
                    if (posX <= 6f) {
                        posX = 6f
                        velX = abs(velX) * 0.72f
                    } else if (posX >= screenWidthPx - mascotSizePx - 6f) {
                        posX = screenWidthPx - mascotSizePx - 6f
                        velX = -abs(velX) * 0.72f
                    }

                    // Floor landing with gentle dampening bounce
                    if (posY >= floorY) {
                        posY = floorY
                        if (abs(velY) > 4.5f) {
                            velY = -velY * 0.42f
                            velX *= 0.75f
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

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .offset { IntOffset(posX.roundToInt(), posY.roundToInt()) }
                .size(mascotSizeDp)
                .pointerInput(isMusicPlaying, penguinsOnlyMode, breed) {
                    detectTapGestures(
                        onTap = {
                            velY = -18f
                            happyJumpTicks = 50
                            if (penguinsOnlyMode || breed.isPenguin) {
                                penguinCycleIndex = (penguinCycleIndex + 1) % CompanionBreed.PENGUIN_BREEDS.size
                            }
                            val icon = when {
                                breed.isLion -> "🦁"
                                breed.isBunny -> "🐰"
                                breed.isPolarBear -> "🐻‍❄️"
                                breed.isPenguin -> "🐧"
                                breed.isTeddyBear -> "🧸"
                                else -> "🐶"
                            }
                            val status = if (isMusicPlaying) "Grooving to Music!" else "Ready to Jam (Play a song!)"
                            Toast.makeText(
                                context,
                                "$icon ${breed.displayName}: $status",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onDoubleTap = {
                            velY = -22f
                            happyJumpTicks = 80
                            onQuickPlayTrigger()
                            Toast.makeText(
                                context,
                                "🎧 ${breed.displayName} DJ Drop!",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onLongPress = {
                            onHideForSession()
                            Toast.makeText(
                                context,
                                "Mascot hidden (Re-enable in Me section)",
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
                            happyJumpTicks = 35
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            posX = (posX + dragAmount.x).coerceIn(0f, screenWidthPx - mascotSizePx)
                            posY = (posY + dragAmount.y).coerceIn(20f, floorY)
                            // Responsive throw fling with high upward sensitivity
                            velX = (dragAmount.x * 0.95f).coerceIn(-35f, 35f)
                            velY = (dragAmount.y * 1.35f).coerceIn(-55f, 35f)
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
            } else if (breed.isStudioHeadphonesMascot) {
                draw3DStudioDJMascot(
                    breed = breed,
                    animTick = animTick,
                    isGrooving = isGrooving,
                    happyJumpTicks = happyJumpTicks
                )
            } else {
                draw3DFluffyCassetteMascot(
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
 * Draws the 3D Pastel Studio Backdrop behind each mascot card in the Me Section & Gallery:
 * Includes pastel split studio wall, 3D squiggles, pixel heart badge, lightning bolt,
 * musical notes, and twinkling 4-point stars.
 */
private fun DrawScope.drawPastel3DStudioBackdrop(breed: CompanionBreed) {
    val w = size.width
    val h = size.height

    // Soft pastel studio background
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                breed.hatPrimaryColor.copy(alpha = 0.35f),
                breed.primaryFur.copy(alpha = 0.25f),
                Color(0xFF151C2C)
            )
        )
    )

    // Diagonal pastel studio accent wall on right
    val wallPath = Path().apply {
        moveTo(w * 1.0f, h * 0.25f)
        lineTo(w * 0.52f, h * 0.80f)
        lineTo(w * 1.0f, h * 0.80f)
        close()
    }
    drawPath(
        path = wallPath,
        color = breed.cassetteShellColor.copy(alpha = 0.25f)
    )

    // Studio floor platform
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                breed.headphoneAccent.copy(alpha = 0.22f),
                Color(0xFF0F172A).copy(alpha = 0.85f)
            )
        ),
        topLeft = Offset(0f, h * 0.78f),
        size = Size(w, h * 0.22f)
    )

    // Top-left 3D wavy squiggle
    val squiggle = Path().apply {
        moveTo(w * 0.06f, h * 0.14f)
        cubicTo(w * 0.12f, h * 0.06f, w * 0.18f, h * 0.22f, w * 0.24f, h * 0.12f)
    }
    drawPath(
        path = squiggle,
        color = breed.hatPrimaryColor.copy(alpha = 0.85f),
        style = Stroke(width = w * 0.032f, cap = StrokeCap.Round)
    )

    // Left Pixel-Heart Sticker
    drawCircle(
        color = Color(0xFFFF4081),
        radius = w * 0.045f,
        center = Offset(w * 0.09f, h * 0.54f)
    )
    drawCircle(
        color = Color(0xFFFF80AB),
        radius = w * 0.022f,
        center = Offset(w * 0.08f, h * 0.53f)
    )

    // Top-Right Neon Lightning Sticker
    val bolt = Path().apply {
        moveTo(w * 0.90f, h * 0.07f)
        lineTo(w * 0.83f, h * 0.17f)
        lineTo(w * 0.89f, h * 0.17f)
        lineTo(w * 0.84f, h * 0.27f)
    }
    drawPath(
        path = bolt,
        color = Color(0xFFFFEA00),
        style = Stroke(width = w * 0.025f, cap = StrokeCap.Round)
    )

    // Floating Music Notes & 4-Point Sparkles
    drawFloatingMusicNote(Offset(w * 0.88f, h * 0.36f), w * 0.035f, breed.headphoneAccent)
    drawFloatingMusicNote(Offset(w * 0.15f, h * 0.42f), w * 0.028f, breed.hatCheckColor)
    drawFourPointSparkle(Offset(w * 0.76f, h * 0.09f), w * 0.042f, Color.White)
    drawFourPointSparkle(Offset(w * 0.24f, h * 0.08f), w * 0.030f, Color(0xFFFFF59D))
    drawFourPointSparkle(Offset(w * 0.86f, h * 0.55f), w * 0.035f, Color.White)
}

/**
 * 3D Studio Animals Renderer (Golden Lion DJ "Sher", Studio Bunny, Polar Bear):
 * 100% faithful to the user's reference images:
 * - Fluffy golden lion cub with warm mane collar, head tuft, tufted brush tail, pink paw pads
 * - Studio Bunny with upright rabbit ears poking through DJ headphones, buck teeth, fluffy tail
 * - Studio Polar Bear with cute round ears, blue eyes, and DJ headphones
 * - Over-ear studio DJ headphones in luxurious cream/ivory with padded leather headband,
 *   concentric rotary volume dial with calibrated tick marks (0, 8, 10, 20...), and knob!
 * - Dancing animation strictly synchronized with music playback (isGrooving == true),
 *   completely calm and peaceful when music is paused.
 */
private fun DrawScope.draw3DStudioDJMascot(
    breed: CompanionBreed,
    animTick: Float,
    isGrooving: Boolean,
    happyJumpTicks: Int
) {
    val w = size.width
    val h = size.height
    val cx = w / 2f

    // Grooving bounces ONLY when music is playing!
    val bounceY = if (isGrooving) {
        val hop = if (happyJumpTicks > 0) abs(sin(animTick * 6f)) * h * 0.12f else 0f
        abs(sin(animTick * 3.5f)) * h * 0.055f + hop
    } else {
        sin(animTick * 0.5f) * (h * 0.004f)
    }

    val headTiltDeg = if (isGrooving) sin(animTick * 2.5f) * 6.5f else 0f
    val tailWag = if (isGrooving) sin(animTick * 4f) * 16f else sin(animTick * 0.5f) * 2f
    val armBob = if (isGrooving) sin(animTick * 3.5f) * 4f else 0f

    // 1. Soft Dynamic Ambient Shadow on the Floor
    val shadowWidth = w * (if (isGrooving) 0.58f + sin(animTick * 3.5f) * 0.04f else 0.58f)
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF000000).copy(alpha = 0.28f), Color.Transparent),
            center = Offset(cx, h * 0.94f),
            radius = shadowWidth / 2f
        ),
        topLeft = Offset(cx - shadowWidth / 2f, h * 0.90f),
        size = Size(shadowWidth, h * 0.08f)
    )

    // 2. Fluffy Paws / Feet with Soft Pink Paw Pads & Toe Beans
    val feetY = h * 0.84f - bounceY * 0.25f
    for (side in listOf(-1f, 1f)) {
        val footCx = cx + side * w * 0.18f
        // Main foot fur
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(breed.highlightFur, breed.primaryFur, breed.shadowFur),
                center = Offset(footCx, feetY),
                radius = w * 0.12f
            ),
            topLeft = Offset(footCx - w * 0.11f, feetY - h * 0.055f),
            size = Size(w * 0.22f, h * 0.11f)
        )
        // Main pink foot pad
        drawOval(
            color = Color(0xFFFFB2C9),
            topLeft = Offset(footCx - w * 0.045f, feetY - h * 0.02f),
            size = Size(w * 0.09f, h * 0.05f)
        )
        // 3 Cute Pink Toe Beans
        for (toe in listOf(-1f, 0f, 1f)) {
            drawCircle(
                color = Color(0xFFFFB2C9),
                radius = w * 0.016f,
                center = Offset(footCx + toe * w * 0.032f, feetY - h * 0.032f)
            )
        }
    }

    // 3. Fluffy Tail
    if (breed.isLion) {
        val tailPivot = Offset(cx + w * 0.18f, h * 0.72f - bounceY)
        rotate(degrees = tailWag, pivot = tailPivot) {
            val tailPath = Path().apply {
                moveTo(tailPivot.x, tailPivot.y)
                cubicTo(
                    tailPivot.x + w * 0.18f, tailPivot.y - h * 0.08f,
                    tailPivot.x + w * 0.26f, tailPivot.y - h * 0.22f,
                    tailPivot.x + w * 0.19f, tailPivot.y - h * 0.32f
                )
            }
            drawPath(
                path = tailPath,
                color = breed.primaryFur,
                style = Stroke(width = w * 0.052f, cap = StrokeCap.Round)
            )
            val tuftTip = Offset(tailPivot.x + w * 0.19f, tailPivot.y - h * 0.32f)
            drawFluffyCluster(
                center = tuftTip,
                radiusX = w * 0.095f,
                radiusY = h * 0.085f,
                primary = breed.earColor,
                highlight = breed.highlightFur,
                shadow = breed.shadowFur,
                tuftCount = 10
            )
        }
    } else if (breed.isBunny) {
        val bunnyTailPivot = Offset(cx + w * 0.22f, h * 0.72f - bounceY)
        drawFluffyCluster(
            center = Offset(bunnyTailPivot.x + tailWag * 0.2f, bunnyTailPivot.y),
            radiusX = w * 0.085f,
            radiusY = h * 0.085f,
            primary = breed.primaryFur,
            highlight = breed.highlightFur,
            shadow = breed.shadowFur,
            tuftCount = 10
        )
    }

    // 4. Plush Body & Soft Cream Belly Patch
    val bodyCy = h * 0.62f - bounceY * 0.7f
    drawFluffyCluster(
        center = Offset(cx, bodyCy),
        radiusX = w * 0.25f,
        radiusY = h * 0.19f,
        primary = breed.primaryFur,
        highlight = breed.highlightFur,
        shadow = breed.shadowFur,
        tuftCount = 18
    )
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFF9EC), breed.highlightFur.copy(alpha = 0.5f)),
            center = Offset(cx, bodyCy + h * 0.02f),
            radius = w * 0.16f
        ),
        topLeft = Offset(cx - w * 0.14f, bodyCy - h * 0.06f),
        size = Size(w * 0.28f, h * 0.21f)
    )

    // 5. Front Arms / Paws with Pink Paw Pads
    for (side in listOf(-1f, 1f)) {
        val handPos = Offset(cx + side * w * 0.26f, bodyCy + h * 0.08f + armBob * side)
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(breed.highlightFur, breed.primaryFur, breed.shadowFur),
                center = handPos,
                radius = w * 0.08f
            ),
            topLeft = Offset(handPos.x - w * 0.075f, handPos.y - h * 0.065f),
            size = Size(w * 0.15f, h * 0.13f)
        )
        drawCircle(
            color = Color(0xFFFFB2C9),
            radius = w * 0.026f,
            center = Offset(handPos.x, handPos.y)
        )
        for (f in listOf(-1f, 0f, 1f)) {
            drawCircle(
                color = Color(0xFFFFB2C9),
                radius = w * 0.012f,
                center = Offset(handPos.x + f * w * 0.022f, handPos.y - h * 0.022f)
            )
        }
    }

    // 6. Fluffy Golden Lion Mane Collar framing neck & cheeks
    val headCy = h * 0.35f - bounceY
    if (breed.isLion) {
        val maneCenter = Offset(cx, headCy + h * 0.09f)
        drawFluffyCluster(
            center = maneCenter,
            radiusX = w * 0.32f,
            radiusY = h * 0.18f,
            primary = Color(0xFFF9C66B),
            highlight = Color(0xFFFFF2D0),
            shadow = Color(0xFFD69438),
            tuftCount = 18
        )
    }

    // 7. Head, Face & Ears
    rotate(degrees = headTiltDeg, pivot = Offset(cx, headCy + h * 0.10f)) {
        if (breed.isBunny) {
            for (side in listOf(-1f, 1f)) {
                val earAngle = side * 10f + if (isGrooving) sin(animTick * 3f + side) * 5f else 0f
                val earRoot = Offset(cx + side * w * 0.12f, headCy - h * 0.16f)
                rotate(degrees = earAngle, pivot = earRoot) {
                    drawOval(
                        brush = Brush.verticalGradient(
                            colors = listOf(breed.highlightFur, breed.primaryFur)
                        ),
                        topLeft = Offset(earRoot.x - w * 0.065f, earRoot.y - h * 0.24f),
                        size = Size(w * 0.13f, h * 0.26f)
                    )
                    drawOval(
                        color = Color(0xFFFFB2C9),
                        topLeft = Offset(earRoot.x - w * 0.038f, earRoot.y - h * 0.20f),
                        size = Size(w * 0.076f, h * 0.20f)
                    )
                }
            }
        } else {
            for (side in listOf(-1f, 1f)) {
                val earCx = cx + side * w * 0.26f
                val earCy = headCy - h * 0.12f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(breed.highlightFur, breed.primaryFur, breed.shadowFur),
                        center = Offset(earCx, earCy),
                        radius = w * 0.09f
                    ),
                    radius = w * 0.085f,
                    center = Offset(earCx, earCy)
                )
                drawCircle(
                    color = Color(0xFFFFB2C9),
                    radius = w * 0.046f,
                    center = Offset(earCx, earCy)
                )
            }
        }

        // Main Fluffy Head Sphere
        drawFluffyCluster(
            center = Offset(cx, headCy),
            radiusX = w * 0.27f,
            radiusY = h * 0.21f,
            primary = breed.primaryFur,
            highlight = breed.highlightFur,
            shadow = breed.shadowFur,
            tuftCount = 20
        )

        // Top head lock / tuft
        if (breed.isLion || breed.isPolarBear) {
            val tuftPath = Path().apply {
                moveTo(cx - w * 0.06f, headCy - h * 0.18f)
                quadraticTo(cx - w * 0.02f, headCy - h * 0.26f, cx + w * 0.02f, headCy - h * 0.27f)
                quadraticTo(cx + w * 0.06f, headCy - h * 0.25f, cx + w * 0.07f, headCy - h * 0.18f)
                close()
            }
            drawPath(path = tuftPath, color = breed.primaryFur)
        }

        // Rosy Airbrushed Pink Cheeks & Whiskers
        val cheekY = headCy + h * 0.055f
        for (side in listOf(-1f, 1f)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFF6E9E).copy(alpha = 0.50f), Color.Transparent),
                    center = Offset(cx + side * w * 0.165f, cheekY),
                    radius = w * 0.065f
                ),
                radius = w * 0.065f,
                center = Offset(cx + side * w * 0.165f, cheekY)
            )
            if (breed.isLion || breed.isBunny) {
                for (wi in 0..1) {
                    val wy = cheekY + (wi - 0.5f) * h * 0.022f
                    drawLine(
                        color = Color(0xFF6B4538).copy(alpha = 0.65f),
                        start = Offset(cx + side * w * 0.14f, wy),
                        end = Offset(cx + side * (w * 0.23f), wy + (wi - 0.5f) * h * 0.015f),
                        strokeWidth = w * 0.012f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // Kawaii Sapphire Blue Galaxy Eyes
        val eyeY = headCy + h * 0.005f
        val eyeRadius = w * 0.068f
        val isBlinking = ((animTick * 10f).toInt() % 100) in 0..2 && happyJumpTicks <= 0
        if (isBlinking) {
            for (side in listOf(-1f, 1f)) {
                drawArc(
                    color = Color(0xFF231815),
                    startAngle = 195f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(cx + side * w * 0.11f - eyeRadius, eyeY - eyeRadius * 0.5f),
                    size = Size(eyeRadius * 2f, eyeRadius),
                    style = Stroke(width = w * 0.022f, cap = StrokeCap.Round)
                )
            }
        } else {
            drawStarryGalaxyEye(Offset(cx - w * 0.11f, eyeY), eyeRadius, Color(0xFF38BDF8))
            drawStarryGalaxyEye(Offset(cx + w * 0.11f, eyeY), eyeRadius, Color(0xFF38BDF8))
        }

        // Muzzle & Soft Nose
        val snoutCy = headCy + h * 0.080f
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFFDF7), breed.highlightFur),
                center = Offset(cx, snoutCy - h * 0.008f),
                radius = w * 0.09f
            ),
            topLeft = Offset(cx - w * 0.08f, snoutCy - h * 0.045f),
            size = Size(w * 0.16f, h * 0.09f)
        )

        val noseColor = if (breed.isPolarBear) Color(0xFF212529) else Color(0xFFE57373)
        drawRoundRect(
            color = noseColor,
            topLeft = Offset(cx - w * 0.030f, snoutCy - h * 0.032f),
            size = Size(w * 0.060f, h * 0.034f),
            cornerRadius = CornerRadius(w * 0.024f, w * 0.024f)
        )

        // Open Happy Smile with Rosy Pink Tongue
        val mouthPath = Path().apply {
            moveTo(cx - w * 0.040f, snoutCy + h * 0.006f)
            quadraticTo(cx, snoutCy + h * 0.016f, cx + w * 0.040f, snoutCy + h * 0.006f)
            quadraticTo(cx + w * 0.030f, snoutCy + h * 0.058f, cx, snoutCy + h * 0.058f)
            quadraticTo(cx - w * 0.030f, snoutCy + h * 0.058f, cx - w * 0.040f, snoutCy + h * 0.006f)
            close()
        }
        drawPath(path = mouthPath, color = Color(0xFF5D232C))
        drawOval(
            color = Color(0xFFFF80AB),
            topLeft = Offset(cx - w * 0.024f, snoutCy + h * 0.026f),
            size = Size(w * 0.048f, h * 0.028f)
        )

        if (breed.isBunny) {
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(cx - w * 0.022f, snoutCy + h * 0.006f),
                size = Size(w * 0.044f, h * 0.022f),
                cornerRadius = CornerRadius(w * 0.008f, w * 0.008f)
            )
            drawLine(
                color = Color(0xFF6B4538).copy(alpha = 0.5f),
                start = Offset(cx, snoutCy + h * 0.006f),
                end = Offset(cx, snoutCy + h * 0.028f),
                strokeWidth = w * 0.008f
            )
        }

        // ================= 8. OVER-EAR STUDIO DJ HEADPHONES WITH ROTARY DIALS =================
        val bandPath = Path().apply {
            moveTo(cx - w * 0.27f, headCy - h * 0.04f)
            cubicTo(
                cx - w * 0.28f, headCy - h * 0.24f,
                cx + w * 0.28f, headCy - h * 0.24f,
                cx + w * 0.27f, headCy - h * 0.04f
            )
        }
        drawPath(
            path = bandPath,
            color = Color(0xFFDED0B6),
            style = Stroke(width = w * 0.075f, cap = StrokeCap.Round)
        )
        drawPath(
            path = bandPath,
            color = Color(0xFFF3ECE0),
            style = Stroke(width = w * 0.045f, cap = StrokeCap.Round)
        )

        for (side in listOf(-1f, 1f)) {
            val cupCx = cx + side * w * 0.285f
            val cupCy = headCy - h * 0.015f

            drawRoundRect(
                color = Color(0xFFC7B9A0),
                topLeft = Offset(cupCx - w * 0.028f, cupCy - h * 0.12f),
                size = Size(w * 0.056f, h * 0.04f),
                cornerRadius = CornerRadius(w * 0.015f, w * 0.015f)
            )

            // Padded Leather Cushion Ring
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFAF5EC), Color(0xFFE4D5BC), Color(0xFFBAA78A)),
                    center = Offset(cupCx, cupCy),
                    radius = w * 0.14f
                ),
                topLeft = Offset(cupCx - w * 0.125f, cupCy - h * 0.13f),
                size = Size(w * 0.25f, h * 0.26f)
            )

            // Outer Earcup Housing
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFEFE6D5), Color(0xFFD6C6AA)),
                    center = Offset(cupCx, cupCy),
                    radius = w * 0.09f
                ),
                radius = w * 0.088f,
                center = Offset(cupCx, cupCy)
            )

            // Rotary Volume Dial Metallic Faceplate
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFE8DDCC), Color(0xFFBAA88C)),
                    center = Offset(cupCx, cupCy),
                    radius = w * 0.072f
                ),
                radius = w * 0.072f,
                center = Offset(cupCx, cupCy)
            )

            // Calibrated Tick Marks around Volume Dial
            val dialSpin = if (isGrooving) animTick * 2.5f else 0f
            for (tick in 0..11) {
                val tickAngle = (tick * 30f + dialSpin) * (PI / 180f).toFloat()
                val innerR = w * (if (tick % 3 == 0) 0.055f else 0.061f)
                val outerR = w * 0.069f
                val p1 = Offset(cupCx + cos(tickAngle) * innerR, cupCy + sin(tickAngle) * innerR)
                val p2 = Offset(cupCx + cos(tickAngle) * outerR, cupCy + sin(tickAngle) * outerR)
                drawLine(
                    color = Color(0xFF5A4D3E),
                    start = p1,
                    end = p2,
                    strokeWidth = w * (if (tick % 3 == 0) 0.012f else 0.007f),
                    cap = StrokeCap.Round
                )
            }

            // Central Rotary Volume Knob with Pointer Indicator Notch
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFF7F3EA), Color(0xFFC7B79D)),
                    center = Offset(cupCx - w * 0.008f, cupCy - h * 0.008f),
                    radius = w * 0.038f
                ),
                radius = w * 0.036f,
                center = Offset(cupCx, cupCy)
            )
            val pointerAngle = (dialSpin * 1.5f)
            val pointerR = w * 0.028f
            drawLine(
                color = Color(0xFF3E3224),
                start = Offset(cupCx, cupCy),
                end = Offset(cupCx + cos(pointerAngle) * pointerR, cupCy + sin(pointerAngle) * pointerR),
                strokeWidth = w * 0.010f,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * 3D Ultra-Fluffy Cassette Puppy & Teddy Bear Renderer:
 * Renders a genuine 3D plush Teddy Bear or Floppy-Eared Puppy with:
 * - Multi-layered 3D radial-shaded fluffy fur tufts around the body, cheeks, ears, and paws
 * - Checkered streetwear bucket hat with 3D curved pastel horns & stitched front badge
 * - Huge starry galaxy eyes with constellations & glossy catchlights
 * - Teddy Bear round fluffy ears OR Puppy floppy furry ears + contrasting plush puppy snout
 * - Cushioned neck headphones with smiley face badge & hanging 3D star charm
 * - Held 3D retro cassette tape ("HONEY TAPE", "SKY PUPPY", "BERRY MIX", etc.) with spinning reels
 */
private fun DrawScope.draw3DFluffyCassetteMascot(
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
    val bounceY = if (isGrooving) abs(beatWave) * (h * 0.028f) else abs(beatWave) * (h * 0.01f)
    val headTiltDeg = if (isGrooving) beatWave * 4.5f else beatWave * 1.5f

    // 1. Soft Studio Ground Shadow
    val shadowScale = (1f - (bounceY / (h * 0.20f))).coerceIn(0.65f, 1f)
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color.Black.copy(alpha = 0.40f), Color.Transparent),
            center = Offset(cx, h * 0.93f),
            radius = w * 0.36f * shadowScale
        ),
        topLeft = Offset(cx - w * 0.34f * shadowScale, h * 0.89f),
        size = Size(w * 0.68f * shadowScale, h * 0.08f)
    )

    // 2. Fluffy Wagging Tail (Right Side behind body)
    val tailAngle = sin(animTick * 2.6f) * (if (isGrooving) 18f else 8f)
    rotate(degrees = tailAngle, pivot = Offset(cx + w * 0.18f, h * 0.72f)) {
        drawFluffyCluster(
            center = Offset(cx + w * 0.28f, h * 0.68f - bounceY * 0.5f),
            radiusX = w * 0.11f,
            radiusY = h * 0.08f,
            primary = breed.primaryFur,
            highlight = breed.highlightFur,
            shadow = breed.shadowFur,
            tuftCount = 10
        )
    }

    // 3. Chubby Fluffy Legs & 3D Plush Feet with Toe Pads
    val legY = h * 0.76f - bounceY * 0.35f
    for (side in listOf(-1f, 1f)) {
        val footCx = cx + side * w * 0.14f
        drawFluffyCluster(
            center = Offset(footCx, legY + h * 0.06f),
            radiusX = w * 0.11f,
            radiusY = h * 0.075f,
            primary = breed.primaryFur,
            highlight = breed.highlightFur,
            shadow = breed.shadowFur,
            tuftCount = 10
        )
        // 3D Toe Claws / Plush Pads
        for (toe in -1..1) {
            drawOval(
                color = breed.highlightFur.copy(alpha = 0.85f),
                topLeft = Offset(footCx + toe * w * 0.032f - w * 0.015f, legY + h * 0.095f),
                size = Size(w * 0.030f, h * 0.024f)
            )
        }
    }

    // 4. Fluffy Plush Torso / Belly
    val bodyCy = h * 0.66f - bounceY * 0.65f
    drawFluffyCluster(
        center = Offset(cx, bodyCy),
        radiusX = w * 0.24f,
        radiusY = h * 0.17f,
        primary = breed.primaryFur,
        highlight = breed.highlightFur,
        shadow = breed.shadowFur,
        tuftCount = 18
    )
    // Soft Cream Plush Belly Patch
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(breed.highlightFur, breed.primaryFur.copy(alpha = 0.2f)),
            center = Offset(cx, bodyCy + h * 0.02f),
            radius = w * 0.16f
        ),
        topLeft = Offset(cx - w * 0.14f, bodyCy - h * 0.06f),
        size = Size(w * 0.28f, h * 0.20f)
    )

    // 5. Tilted Head Group: Ears, Fluffy Head, Bucket Hat with Horns, Galaxy Eyes & Snout
    val headCy = h * 0.36f - bounceY
    rotate(degrees = headTiltDeg, pivot = Offset(cx, headCy + h * 0.08f)) {
        if (breed.isTeddyBear) {
            // Round Fluffy Teddy Bear Ears sticking out from sides of the bucket hat
            for (side in listOf(-1f, 1f)) {
                val earCx = cx + side * w * 0.28f
                val earCy = headCy - h * 0.12f
                drawFluffyCluster(
                    center = Offset(earCx, earCy),
                    radiusX = w * 0.105f,
                    radiusY = h * 0.095f,
                    primary = breed.primaryFur,
                    highlight = breed.highlightFur,
                    shadow = breed.shadowFur,
                    tuftCount = 12
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFFB2C9), breed.earColor),
                        center = Offset(earCx, earCy),
                        radius = w * 0.055f
                    ),
                    radius = w * 0.052f,
                    center = Offset(earCx, earCy)
                )
            }
        } else {
            // Ultra-Fluffy Floppy Puppy Ears hanging down on left & right sides of the face
            for (side in listOf(-1f, 1f)) {
                val earBounce = sin(animTick * 2f + if (side < 0) 0f else 1.2f) * 5f
                val earPivot = Offset(cx + side * w * 0.24f, headCy - h * 0.04f)
                rotate(degrees = side * (14f + earBounce), pivot = earPivot) {
                    drawFluffyCluster(
                        center = Offset(cx + side * w * 0.285f, headCy + h * 0.04f),
                        radiusX = w * 0.085f,
                        radiusY = h * 0.135f,
                        primary = breed.earColor,
                        highlight = breed.highlightFur,
                        shadow = breed.shadowFur,
                        tuftCount = 14
                    )
                }
            }
        }

        // Main Ultra-Fluffy 3D Head Sphere with Multi-Tuft Fur Perimeter
        drawFluffyCluster(
            center = Offset(cx, headCy + h * 0.02f),
            radiusX = w * 0.26f,
            radiusY = h * 0.20f,
            primary = breed.primaryFur,
            highlight = breed.highlightFur,
            shadow = breed.shadowFur,
            tuftCount = 24
        )

        // Forehead Center Highlight Fur Tuft
        drawOval(
            brush = Brush.verticalGradient(
                colors = listOf(breed.highlightFur.copy(alpha = 0.90f), Color.Transparent)
            ),
            topLeft = Offset(cx - w * 0.09f, headCy - h * 0.09f),
            size = Size(w * 0.18f, h * 0.13f)
        )

        // Rosy Pastel Airbrushed Cheeks
        val cheekY = headCy + h * 0.065f
        for (side in listOf(-1f, 1f)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFF6E9E).copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(cx + side * w * 0.165f, cheekY),
                    radius = w * 0.075f
                ),
                radius = w * 0.075f,
                center = Offset(cx + side * w * 0.165f, cheekY)
            )
        }

        // Huge Starry Galaxy Eyes
        val eyeY = headCy + h * 0.015f
        val leftEyeCenter = Offset(cx - w * 0.105f, eyeY)
        val rightEyeCenter = Offset(cx + w * 0.105f, eyeY)
        val eyeRadius = w * 0.064f

        val isBlinking = ((animTick * 10f).toInt() % 95) in 0..2 && happyJumpTicks <= 0
        if (isBlinking) {
            for (eyePos in listOf(leftEyeCenter, rightEyeCenter)) {
                drawArc(
                    color = Color(0xFF231815),
                    startAngle = 195f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(eyePos.x - eyeRadius, eyePos.y - eyeRadius * 0.5f),
                    size = Size(eyeRadius * 2f, eyeRadius),
                    style = Stroke(width = w * 0.024f, cap = StrokeCap.Round)
                )
            }
        } else {
            drawStarryGalaxyEye(leftEyeCenter, eyeRadius, breed.headphoneAccent)
            drawStarryGalaxyEye(rightEyeCenter, eyeRadius, breed.headphoneAccent)
        }

        // Snout / Muzzle & Cute Open Smile (Teddy vs. Puppy!)
        val snoutCy = headCy + h * 0.082f
        if (!breed.isTeddyBear) {
            // Contrasting Plush Brown/Caramel Puppy Snout Patch + Glossy 3D Puppy Nose
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        breed.muzzlePatch,
                        breed.muzzlePatch.copy(
                            red = (breed.muzzlePatch.red * 0.78f).coerceIn(0f, 1f),
                            green = (breed.muzzlePatch.green * 0.78f).coerceIn(0f, 1f),
                            blue = (breed.muzzlePatch.blue * 0.78f).coerceIn(0f, 1f)
                        )
                    ),
                    center = Offset(cx, snoutCy - h * 0.008f),
                    radius = w * 0.085f
                ),
                topLeft = Offset(cx - w * 0.075f, snoutCy - h * 0.042f),
                size = Size(w * 0.15f, h * 0.088f)
            )
            // Glossy 3D Dark Brown Puppy Nose
            drawRoundRect(
                color = Color(0xFF2D1912),
                topLeft = Offset(cx - w * 0.032f, snoutCy - h * 0.030f),
                size = Size(w * 0.064f, h * 0.036f),
                cornerRadius = CornerRadius(w * 0.028f, w * 0.028f)
            )
            drawOval(
                color = Color.White.copy(alpha = 0.85f),
                topLeft = Offset(cx - w * 0.018f, snoutCy - h * 0.025f),
                size = Size(w * 0.024f, h * 0.012f)
            )
        } else {
            // Soft Fluffy Teddy Muzzle
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(breed.highlightFur, breed.primaryFur),
                    center = Offset(cx, snoutCy - h * 0.01f),
                    radius = w * 0.07f
                ),
                topLeft = Offset(cx - w * 0.065f, snoutCy - h * 0.032f),
                size = Size(w * 0.13f, h * 0.072f)
            )
        }

        // Cute Open Happy Mouth with Pink Tongue (matching reference photos!)
        val mouthPath = Path().apply {
            moveTo(cx - w * 0.042f, snoutCy + h * 0.008f)
            quadraticTo(cx, snoutCy + h * 0.018f, cx + w * 0.042f, snoutCy + h * 0.008f)
            quadraticTo(cx + w * 0.032f, snoutCy + h * 0.062f, cx, snoutCy + h * 0.062f)
            quadraticTo(cx - w * 0.032f, snoutCy + h * 0.062f, cx - w * 0.042f, snoutCy + h * 0.008f)
            close()
        }
        drawPath(path = mouthPath, color = Color(0xFF5D232C))
        drawOval(
            color = Color(0xFFFF80AB),
            topLeft = Offset(cx - w * 0.026f, snoutCy + h * 0.028f),
            size = Size(w * 0.052f, h * 0.030f)
        )

        // 6. Signature Checkered Bucket Hat with 3D Curved Horns & Stitched Badge
        drawCheckeredHornedBucketHat(
            cx = cx,
            headTopY = headCy - h * 0.21f,
            w = w,
            h = h,
            breed = breed
        )
    }

    // 7. Pastel Neck Headphones with Smiley Face Badge & Hanging 3D Star Charm
    val neckY = h * 0.525f - bounceY * 0.75f
    drawNeckHeadphonesWithStarCharm(
        cx = cx,
        neckY = neckY,
        w = w,
        h = h,
        breed = breed,
        animTick = animTick
    )

    // 8. Held 3D Cassette Tape in Fluffy Paws ("HONEY TAPE", "SKY PUPPY", "BERRY MIX", etc.)
    val tapeCenter = Offset(cx - w * 0.03f, h * 0.655f - bounceY * 0.7f)
    drawHeld3DCassetteAndFluffyPaws(
        center = tapeCenter,
        cx = cx,
        w = w,
        h = h,
        breed = breed,
        animTick = animTick,
        isGrooving = isGrooving
    )
}

/**
 * Draws a multi-tuft 3D plush fur cluster so the Teddy Bear & Puppy look genuinely fluffy!
 */
private fun DrawScope.drawFluffyCluster(
    center: Offset,
    radiusX: Float,
    radiusY: Float,
    primary: Color,
    highlight: Color,
    shadow: Color,
    tuftCount: Int
) {
    // Outer perimeter fur tufts
    for (i in 0 until tuftCount) {
        val angle = (2.0 * PI * i) / tuftCount
        val tx = center.x + cos(angle).toFloat() * (radiusX * 0.88f)
        val ty = center.y + sin(angle).toFloat() * (radiusY * 0.88f)
        val tuftR = minOf(radiusX, radiusY) * (if (i % 2 == 0) 0.34f else 0.28f)
        val isTopHalf = sin(angle) < 0
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    if (isTopHalf) highlight else primary,
                    if (isTopHalf) primary else shadow
                ),
                center = Offset(tx - tuftR * 0.2f, ty - tuftR * 0.2f),
                radius = tuftR * 1.2f
            ),
            radius = tuftR,
            center = Offset(tx, ty)
        )
    }

    // Core 3D shaded plush body
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(highlight, primary, shadow),
            center = Offset(center.x - radiusX * 0.22f, center.y - radiusY * 0.28f),
            radius = maxOf(radiusX, radiusY) * 1.15f
        ),
        topLeft = Offset(center.x - radiusX, center.y - radiusY),
        size = Size(radiusX * 2f, radiusY * 2f)
    )
}

/**
 * Draws the Starry Galaxy Eyes with cosmic nebula gradient, twinkling stars, and glossy highlights.
 */
private fun DrawScope.drawStarryGalaxyEye(
    center: Offset,
    radius: Float,
    accentGlow: Color
) {
    // Soft outer eye rim
    drawCircle(
        color = Color(0xFF1A1215),
        radius = radius * 1.08f,
        center = center
    )
    // Cosmic galaxy iris gradient
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF120D2B),
                Color(0xFF311B92),
                accentGlow.copy(alpha = 0.72f),
                Color(0xFF0D071A)
            ),
            center = Offset(center.x, center.y + radius * 0.2f),
            radius = radius
        ),
        radius = radius * 0.96f,
        center = center
    )
    // Nebula glow arc at bottom of iris
    drawOval(
        color = Color(0xFFFF80AB).copy(alpha = 0.48f),
        topLeft = Offset(center.x - radius * 0.55f, center.y + radius * 0.15f),
        size = Size(radius * 1.1f, radius * 0.55f)
    )
    // Large top-right glossy catchlight
    drawCircle(
        color = Color.White,
        radius = radius * 0.34f,
        center = Offset(center.x + radius * 0.28f, center.y - radius * 0.28f)
    )
    // Secondary bottom-left catchlight
    drawCircle(
        color = Color.White.copy(alpha = 0.90f),
        radius = radius * 0.15f,
        center = Offset(center.x - radius * 0.32f, center.y + radius * 0.30f)
    )
    // Tiny constellation star inside the galaxy eye
    drawFourPointSparkle(
        center = Offset(center.x - radius * 0.22f, center.y - radius * 0.14f),
        radius = radius * 0.24f,
        color = Color(0xFFFFF59D)
    )
}

/**
 * Draws the Checkered Bucket Hat with 3D Curved Horns, Oval Base Studs, and Front Patch Badge.
 */
private fun DrawScope.drawCheckeredHornedBucketHat(
    cx: Float,
    headTopY: Float,
    w: Float,
    h: Float,
    breed: CompanionBreed
) {
    val crownW = w * 0.44f
    val crownH = h * 0.15f
    val crownLeft = cx - crownW / 2f

    // 1. Upper Rounded Bucket Hat Crown
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                breed.hatPrimaryColor,
                breed.hatPrimaryColor.copy(
                    red = (breed.hatPrimaryColor.red * 0.86f).coerceIn(0f, 1f),
                    green = (breed.hatPrimaryColor.green * 0.86f).coerceIn(0f, 1f),
                    blue = (breed.hatPrimaryColor.blue * 0.86f).coerceIn(0f, 1f)
                )
            )
        ),
        topLeft = Offset(crownLeft, headTopY),
        size = Size(crownW, crownH),
        cornerRadius = CornerRadius(crownW * 0.46f, crownW * 0.46f)
    )

    // 2. Slanted Checkered Brim Band
    val brimTopY = headTopY + crownH * 0.58f
    val brimBottomY = brimTopY + h * 0.085f
    val brimPath = Path().apply {
        moveTo(cx - w * 0.22f, brimTopY)
        quadraticTo(cx, brimTopY - h * 0.025f, cx + w * 0.22f, brimTopY)
        lineTo(cx + w * 0.31f, brimBottomY)
        quadraticTo(cx, brimBottomY - h * 0.032f, cx - w * 0.31f, brimBottomY)
        close()
    }
    drawPath(path = brimPath, color = breed.hatPrimaryColor)

    // Checkerboard tiles across the bucket hat brim
    val cols = 9
    val rows = 2
    for (r in 0 until rows) {
        val rowT0 = r / rows.toFloat()
        val rowT1 = (r + 1) / rows.toFloat()
        val y0 = brimTopY + (brimBottomY - brimTopY) * rowT0 - h * 0.01f * (1f - abs(0.5f))
        val y1 = brimTopY + (brimBottomY - brimTopY) * rowT1
        val halfW0 = w * (0.22f + 0.08f * rowT0)
        val halfW1 = w * (0.22f + 0.08f * rowT1)
        for (c in 0 until cols) {
            if ((r + c) % 2 == 0) {
                val c0 = (c / cols.toFloat()) * 2f - 1f
                val c1 = ((c + 1) / cols.toFloat()) * 2f - 1f
                val tile = Path().apply {
                    moveTo(cx + c0 * halfW0, y0)
                    lineTo(cx + c1 * halfW0, y0)
                    lineTo(cx + c1 * halfW1, y1)
                    lineTo(cx + c0 * halfW1, y1)
                    close()
                }
                drawPath(path = tile, color = breed.hatCheckColor.copy(alpha = 0.88f))
            }
        }
    }

    // Soft underside brim shadow
    drawArc(
        color = Color.Black.copy(alpha = 0.22f),
        startAngle = 190f,
        sweepAngle = 160f,
        useCenter = false,
        topLeft = Offset(cx - w * 0.30f, brimBottomY - h * 0.025f),
        size = Size(w * 0.60f, h * 0.045f),
        style = Stroke(width = w * 0.018f, cap = StrokeCap.Round)
    )

    // 3. Cute 3D Curved Pastel Horns on Left & Right of Bucket Hat + Oval Base Studs
    for (side in listOf(-1f, 1f)) {
        val baseX = cx + side * w * 0.145f
        val baseY = headTopY + h * 0.042f
        val hornPath = Path().apply {
            moveTo(baseX - side * w * 0.036f, baseY)
            quadraticTo(
                baseX + side * w * 0.045f,
                baseY - h * 0.075f,
                baseX - side * w * 0.010f,
                baseY - h * 0.095f
            )
            quadraticTo(
                baseX - side * w * 0.055f,
                baseY - h * 0.055f,
                baseX + side * w * 0.025f,
                baseY + h * 0.008f
            )
            close()
        }
        drawPath(
            path = hornPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color.White, breed.hornColor)
            )
        )
        // Pink/Gold Oval Horn Base Stud
        drawOval(
            color = breed.cassetteAccentColor,
            topLeft = Offset(baseX - w * 0.032f, baseY - h * 0.010f),
            size = Size(w * 0.064f, h * 0.028f)
        )
    }

    // 4. Stitched Front Patch Badge ("BEAT", "PLAY", "VIBE", "STAR", etc.)
    val badgeW = w * 0.15f
    val badgeH = h * 0.042f
    val badgeTop = headTopY + h * 0.024f
    drawRoundRect(
        color = Color.White.copy(alpha = 0.92f),
        topLeft = Offset(cx - badgeW / 2f, badgeTop),
        size = Size(badgeW, badgeH),
        cornerRadius = CornerRadius(badgeH * 0.45f, badgeH * 0.45f)
    )
    drawRoundRect(
        color = breed.cassetteShellColor,
        topLeft = Offset(cx - badgeW / 2f, badgeTop),
        size = Size(badgeW, badgeH),
        cornerRadius = CornerRadius(badgeH * 0.45f, badgeH * 0.45f),
        style = Stroke(width = w * 0.006f)
    )

    drawContext.canvas.nativeCanvas.apply {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0xFF2D1B4E).toArgb()
            textSize = badgeH * 0.62f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        drawText(
            breed.hatBadgeText,
            cx,
            badgeTop + badgeH * 0.72f,
            textPaint
        )
    }
}

/**
 * Draws the Cushioned Pastel Headphones worn around the neck with a Smiley Face badge
 * and a dangling 3D Holographic Star Charm!
 */
private fun DrawScope.drawNeckHeadphonesWithStarCharm(
    cx: Float,
    neckY: Float,
    w: Float,
    h: Float,
    breed: CompanionBreed,
    animTick: Float
) {
    // Neck band strap
    drawArc(
        color = breed.headphoneAccent,
        startAngle = 15f,
        sweepAngle = 150f,
        useCenter = false,
        topLeft = Offset(cx - w * 0.19f, neckY - h * 0.03f),
        size = Size(w * 0.38f, h * 0.085f),
        style = Stroke(width = w * 0.032f, cap = StrokeCap.Round)
    )

    // Left & Right Forward-Facing Cushioned Earcups around the neck
    val leftCupCenter = Offset(cx - w * 0.105f, neckY + h * 0.015f)
    val rightCupCenter = Offset(cx + w * 0.105f, neckY + h * 0.015f)
    val cupR = w * 0.068f

    for ((idx, cupCenter) in listOf(leftCupCenter, rightCupCenter).withIndex()) {
        // Outer cushion
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, breed.headphoneAccent),
                center = Offset(cupCenter.x - cupR * 0.2f, cupCenter.y - cupR * 0.2f),
                radius = cupR * 1.2f
            ),
            radius = cupR,
            center = cupCenter
        )
        // Inner face plate
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = cupR * 0.65f,
            center = cupCenter
        )
        drawCircle(
            color = Color(0xFF263238),
            radius = cupR * 0.65f,
            center = cupCenter,
            style = Stroke(width = w * 0.006f)
        )

        // Cute Smiley Face ":)" on the right headphone cup (just like the reference photos!)
        if (idx == 1) {
            drawCircle(
                color = Color(0xFF263238),
                radius = cupR * 0.09f,
                center = Offset(cupCenter.x - cupR * 0.20f, cupCenter.y - cupR * 0.14f)
            )
            drawCircle(
                color = Color(0xFF263238),
                radius = cupR * 0.09f,
                center = Offset(cupCenter.x + cupR * 0.20f, cupCenter.y - cupR * 0.14f)
            )
            drawArc(
                color = Color(0xFF263238),
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(cupCenter.x - cupR * 0.28f, cupCenter.y - cupR * 0.12f),
                size = Size(cupR * 0.56f, cupR * 0.42f),
                style = Stroke(width = w * 0.007f, cap = StrokeCap.Round)
            )
        }
    }

    // Dangling 3D Holographic Star Charm in the center of the neck strap
    val starSwing = sin(animTick * 2.2f) * 8f
    val charmCenter = Offset(cx + w * 0.015f, neckY + h * 0.048f)
    rotate(degrees = starSwing, pivot = Offset(charmCenter.x, charmCenter.y - h * 0.025f)) {
        drawFivePointStar(
            center = charmCenter,
            outerRadius = w * 0.036f,
            innerRadius = w * 0.017f,
            fillColor = Color(0xFFE1F5FE),
            strokeColor = breed.cassetteShellColor
        )
    }
}

/**
 * Draws the Held 3D Retro Cassette Tape in front of the tummy + Two Fluffy Paws holding it!
 */
private fun DrawScope.drawHeld3DCassetteAndFluffyPaws(
    center: Offset,
    cx: Float,
    w: Float,
    h: Float,
    breed: CompanionBreed,
    animTick: Float,
    isGrooving: Boolean
) {
    val tapeW = w * 0.38f
    val tapeH = h * 0.22f
    val tilt = -5.5f + (if (isGrooving) sin(animTick * 1.5f) * 2.2f else 0f)

    rotate(degrees = tilt, pivot = center) {
        val left = center.x - tapeW / 2f
        val top = center.y - tapeH / 2f

        // 1. 3D Cassette Drop Shadow
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.28f),
            topLeft = Offset(left + w * 0.01f, top + h * 0.012f),
            size = Size(tapeW, tapeH),
            cornerRadius = CornerRadius(w * 0.024f, w * 0.024f)
        )

        // 2. Outer 3D Cassette Shell
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(breed.cassetteShellColor, breed.cassetteAccentColor)
            ),
            topLeft = Offset(left, top),
            size = Size(tapeW, tapeH),
            cornerRadius = CornerRadius(w * 0.024f, w * 0.024f)
        )
        drawRoundRect(
            color = Color(0xFF2D1B4E).copy(alpha = 0.55f),
            topLeft = Offset(left, top),
            size = Size(tapeW, tapeH),
            cornerRadius = CornerRadius(w * 0.024f, w * 0.024f),
            style = Stroke(width = w * 0.006f)
        )

        // 3. Inner Pastel Label Sticker
        val labelLeft = left + tapeW * 0.07f
        val labelTop = top + tapeH * 0.12f
        val labelW = tapeW * 0.86f
        val labelH = tapeH * 0.62f
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.92f),
                    breed.hatCheckColor.copy(alpha = 0.85f),
                    breed.cassetteShellColor.copy(alpha = 0.90f)
                )
            ),
            topLeft = Offset(labelLeft, labelTop),
            size = Size(labelW, labelH),
            cornerRadius = CornerRadius(w * 0.016f, w * 0.016f)
        )

        // 4. Bold Cassette Title ("HONEY TAPE", "SKY PUPPY", "BERRY MIX", etc.)
        drawContext.canvas.nativeCanvas.apply {
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color(0xFF231836).toArgb()
                textSize = tapeH * 0.19f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            drawText(
                breed.cassetteLabel,
                center.x,
                labelTop + labelH * 0.34f,
                titlePaint
            )
        }

        // 5. Center Reel Window & Two Animated Spinning Tape Reels
        val windowW = labelW * 0.64f
        val windowH = labelH * 0.38f
        val windowLeft = center.x - windowW / 2f
        val windowTop = labelTop + labelH * 0.45f
        drawRoundRect(
            color = Color(0xFF2B2338),
            topLeft = Offset(windowLeft, windowTop),
            size = Size(windowW, windowH),
            cornerRadius = CornerRadius(windowH * 0.48f, windowH * 0.48f)
        )

        val reelRadius = windowH * 0.38f
        val leftReel = Offset(windowLeft + windowW * 0.24f, windowTop + windowH / 2f)
        val rightReel = Offset(windowLeft + windowW * 0.76f, windowTop + windowH / 2f)
        for (reelCenter in listOf(leftReel, rightReel)) {
            drawCircle(
                color = Color.White,
                radius = reelRadius,
                center = reelCenter
            )
            drawCircle(
                color = Color(0xFF1F1929),
                radius = reelRadius * 0.55f,
                center = reelCenter
            )
            // Rotating gear spokes
            for (tooth in 0 until 6) {
                val a = animTick * 3f + tooth * (PI.toFloat() / 3f)
                drawLine(
                    color = Color.White,
                    start = Offset(
                        reelCenter.x + cos(a) * reelRadius * 0.35f,
                        reelCenter.y + sin(a) * reelRadius * 0.35f
                    ),
                    end = Offset(
                        reelCenter.x + cos(a) * reelRadius * 0.95f,
                        reelCenter.y + sin(a) * reelRadius * 0.95f
                    ),
                    strokeWidth = w * 0.006f
                )
            }
        }

        // 6. Bottom Cassette Trapezoid Lip & Holes
        val trapTop = top + tapeH * 0.78f
        val trapPath = Path().apply {
            moveTo(left + tapeW * 0.18f, trapTop)
            lineTo(left + tapeW * 0.82f, trapTop)
            lineTo(left + tapeW * 0.87f, top + tapeH * 0.97f)
            lineTo(left + tapeW * 0.13f, top + tapeH * 0.97f)
            close()
        }
        drawPath(
            path = trapPath,
            color = Color.White.copy(alpha = 0.28f)
        )
        for (holeX in listOf(0.28f, 0.42f, 0.58f, 0.72f)) {
            drawCircle(
                color = Color(0xFF231836),
                radius = w * 0.008f,
                center = Offset(left + tapeW * holeX, top + tapeH * 0.90f)
            )
        }
    }

    // 7. Two Ultra-Fluffy Round Paws Holding Both Sides of the Cassette Tape
    val leftPawCenter = Offset(center.x - tapeW * 0.50f, center.y + tapeH * 0.02f)
    val rightPawCenter = Offset(center.x + tapeW * 0.52f, center.y + tapeH * 0.05f)
    drawFluffyCluster(
        center = leftPawCenter,
        radiusX = w * 0.088f,
        radiusY = h * 0.068f,
        primary = breed.primaryFur,
        highlight = breed.highlightFur,
        shadow = breed.shadowFur,
        tuftCount = 10
    )
    drawFluffyCluster(
        center = rightPawCenter,
        radiusX = w * 0.095f,
        radiusY = h * 0.072f,
        primary = breed.primaryFur,
        highlight = breed.highlightFur,
        shadow = breed.shadowFur,
        tuftCount = 10
    )
}

private fun DrawScope.drawFourPointSparkle(
    center: Offset,
    radius: Float,
    color: Color
) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x, center.y, center.x + radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + radius)
        quadraticTo(center.x, center.y, center.x - radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - radius)
        close()
    }
    drawPath(path = path, color = color)
}

private fun DrawScope.drawFivePointStar(
    center: Offset,
    outerRadius: Float,
    innerRadius: Float,
    fillColor: Color,
    strokeColor: Color
) {
    val path = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outerRadius else innerRadius
        val angle = -PI / 2.0 + i * (PI / 5.0)
        val x = center.x + (cos(angle) * r).toFloat()
        val y = center.y + (sin(angle) * r).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path = path, color = fillColor)
    drawPath(path = path, color = strokeColor, style = Stroke(width = outerRadius * 0.18f))
}

/**
 * Dedicated 3D Ultra-Colorful Chibi Penguin Renderer!
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
    val bounceY = if (isGrooving) abs(beatWave) * (h * 0.042f) else abs(beatWave) * (h * 0.012f)
    val waddleTilt = if (isGrooving) beatWave * 6.5f else beatWave * 2.5f

    val shadowScale = (1f - (bounceY / (h * 0.22f))).coerceIn(0.6f, 1f)
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                breed.headphoneAccent.copy(alpha = 0.35f),
                Color.Black.copy(alpha = 0.38f),
                Color.Transparent
            ),
            center = Offset(cx, h * 0.92f),
            radius = w * 0.34f * shadowScale
        ),
        topLeft = Offset(cx - w * 0.32f * shadowScale, h * 0.87f),
        size = Size(w * 0.64f * shadowScale, h * 0.09f)
    )

    val footWave = sin(animTick * 2f) * (if (isGrooving) 4f else 1.5f)
    val feetY = h * 0.83f - bounceY * 0.3f
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFFFFCA28), breed.earColor)),
        topLeft = Offset(cx - w * 0.22f, feetY + footWave),
        size = Size(w * 0.16f, h * 0.075f),
        cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
    )
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFFFFCA28), breed.earColor)),
        topLeft = Offset(cx + w * 0.06f, feetY - footWave),
        size = Size(w * 0.16f, h * 0.075f),
        cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
    )

    val bodyTop = h * 0.20f - bounceY
    val bodyW = w * 0.66f
    val bodyH = h * 0.68f

    rotate(degrees = waddleTilt, pivot = Offset(cx, bodyTop + bodyH * 0.55f)) {
        val wingFlap = if (isGrooving) 24f + fastWave * 18f else 8f + beatWave * 5f
        rotate(degrees = wingFlap, pivot = Offset(cx - bodyW * 0.42f, bodyTop + bodyH * 0.42f)) {
            drawOval(
                brush = Brush.verticalGradient(listOf(breed.highlightFur, breed.primaryFur, breed.shadowFur)),
                topLeft = Offset(cx - bodyW * 0.62f, bodyTop + bodyH * 0.36f),
                size = Size(w * 0.16f, h * 0.30f)
            )
        }
        rotate(degrees = -wingFlap, pivot = Offset(cx + bodyW * 0.42f, bodyTop + bodyH * 0.42f)) {
            drawOval(
                brush = Brush.verticalGradient(listOf(breed.highlightFur, breed.primaryFur, breed.shadowFur)),
                topLeft = Offset(cx + bodyW * 0.46f - w * 0.16f + w * 0.16f, bodyTop + bodyH * 0.36f),
                size = Size(w * 0.16f, h * 0.30f)
            )
        }

        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(breed.highlightFur, breed.primaryFur, breed.shadowFur),
                center = Offset(cx - w * 0.08f, bodyTop + bodyH * 0.25f),
                radius = bodyW * 0.85f
            ),
            topLeft = Offset(cx - bodyW / 2f, bodyTop),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(bodyW * 0.48f, bodyW * 0.48f)
        )

        val bellyW = bodyW * 0.72f
        val bellyH = bodyH * 0.64f
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.White, breed.muzzlePatch, breed.highlightFur.copy(alpha = 0.28f))
            ),
            topLeft = Offset(cx - bellyW / 2f, bodyTop + bodyH * 0.28f),
            size = Size(bellyW, bellyH),
            cornerRadius = CornerRadius(bellyW * 0.48f, bellyW * 0.48f)
        )

        val eyeY = bodyTop + bodyH * 0.29f
        val leftEyeX = cx - bodyW * 0.19f
        val rightEyeX = cx + bodyW * 0.19f
        val eyeRadius = w * 0.068f
        drawStarryGalaxyEye(Offset(leftEyeX, eyeY), eyeRadius, breed.headphoneAccent)
        drawStarryGalaxyEye(Offset(rightEyeX, eyeY), eyeRadius, breed.headphoneAccent)

        val beakY = eyeY + h * 0.045f
        val beakPath = Path().apply {
            moveTo(cx - w * 0.075f, beakY)
            lineTo(cx + w * 0.075f, beakY)
            lineTo(cx, beakY + h * 0.075f)
            close()
        }
        drawPath(
            path = beakPath,
            brush = Brush.verticalGradient(listOf(Color(0xFFFFEA00), breed.earColor))
        )

        val bandRadius = bodyW * 0.52f
        drawArc(
            brush = Brush.horizontalGradient(
                colors = listOf(breed.headphoneAccent, Color.White, breed.headphoneAccent)
            ),
            startAngle = 192f,
            sweepAngle = 156f,
            useCenter = false,
            topLeft = Offset(cx - bandRadius, bodyTop - h * 0.04f),
            size = Size(bandRadius * 2f, bodyH * 0.52f),
            style = Stroke(width = w * 0.042f, cap = StrokeCap.Round)
        )
    }

    if (isGrooving) {
        val noteProgress1 = (animTick * 0.35f) % 1f
        drawFloatingMusicNote(
            center = Offset(
                x = cx - w * 0.36f - sin(noteProgress1 * 6f) * (w * 0.05f),
                y = h * 0.24f - noteProgress1 * (h * 0.20f)
            ),
            scale = w * 0.045f,
            color = breed.headphoneAccent.copy(alpha = (1f - noteProgress1).coerceIn(0f, 1f))
        )
    }
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
