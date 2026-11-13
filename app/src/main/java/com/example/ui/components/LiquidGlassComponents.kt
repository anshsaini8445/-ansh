package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections
import kotlin.math.cos
import kotlin.math.sin

private object AlbumArtBitmapCache {
    private val cache = object : LruCache<String, ImageBitmap>(100) {}
    private val noArtPaths = Collections.synchronizedSet(HashSet<String>())
    val extractionSemaphore = Semaphore(2)

    fun get(key: String): ImageBitmap? = cache.get(key)
    fun isKnownEmpty(key: String): Boolean = noArtPaths.contains(key)
    fun markEmpty(key: String) {
        if (noArtPaths.size > 400) noArtPaths.clear()
        noArtPaths.add(key)
    }
    fun put(key: String, bitmap: ImageBitmap) {
        cache.put(key, bitmap)
    }
}

/**
 * Ultra-Light, Throttle-Guarded Album Art & Video Thumbnail Extractor:
 * - Caches ImageBitmap directly so scrolling never re-allocates ImageBitmap wrappers.
 * - Uses a 2-permit Semaphore + 30ms scroll-settle debounce so fast scrolling never hangs the phone.
 */
@Composable
fun GuaranteedAlbumArt(
    mediaPath: String,
    title: String,
    isVideo: Boolean,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    accentColor: Color = Color(0xFF00E676)
) {
    var imageBitmap by remember(mediaPath) { mutableStateOf(AlbumArtBitmapCache.get(mediaPath)) }

    LaunchedEffect(mediaPath, isVideo) {
        if (imageBitmap == null && mediaPath.isNotBlank() && !AlbumArtBitmapCache.isKnownEmpty(mediaPath)) {
            delay(30L) // Fast-scroll debounce: cancels immediately if item scrolls off-screen
            val loaded = withContext(Dispatchers.IO) {
                AlbumArtBitmapCache.get(mediaPath)?.let { return@withContext it }
                if (AlbumArtBitmapCache.isKnownEmpty(mediaPath)) return@withContext null

                AlbumArtBitmapCache.extractionSemaphore.withPermit {
                    AlbumArtBitmapCache.get(mediaPath)?.let { return@withPermit it }
                    val file = File(mediaPath)
                    if (!file.exists()) {
                        AlbumArtBitmapCache.markEmpty(mediaPath)
                        return@withPermit null
                    }
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(mediaPath)
                        val embedded = retriever.embeddedPicture
                        val rawBmp = if (embedded != null && embedded.isNotEmpty()) {
                            val opts = BitmapFactory.Options().apply {
                                inSampleSize = 2
                                inPreferredConfig = Bitmap.Config.RGB_565
                            }
                            BitmapFactory.decodeByteArray(embedded, 0, embedded.size, opts)
                        } else if (isVideo) {
                            retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        } else {
                            null
                        }
                        if (rawBmp != null) {
                            val scaled = Bitmap.createScaledBitmap(rawBmp, 136, 136, true)
                            if (scaled !== rawBmp) rawBmp.recycle()
                            val imgBmp = scaled.asImageBitmap()
                            AlbumArtBitmapCache.put(mediaPath, imgBmp)
                            imgBmp
                        } else {
                            AlbumArtBitmapCache.markEmpty(mediaPath)
                            null
                        }
                    } catch (_: Exception) {
                        AlbumArtBitmapCache.markEmpty(mediaPath)
                        null
                    } finally {
                        try {
                            retriever.release()
                        } catch (_: Exception) {
                        }
                    }
                }
            }
            if (loaded != null) {
                imageBitmap = loaded
            }
        }
    }

    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF0D1B1E),
                        accentColor.copy(alpha = 0.24f),
                        Color(0xFF09101D)
                    )
                )
            )
            .border(1.dp, accentColor.copy(alpha = 0.42f), shape),
        contentAlignment = Alignment.Center
    ) {
        val bmp = imageBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize(0.68f)
                    .clip(CircleShape)
                    .background(Color(0xFF0B111A))
                    .border(1.5.dp, accentColor.copy(alpha = 0.72f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isVideo) Icons.Default.Movie else Icons.Default.MusicNote,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.fillMaxSize(0.55f)
                )
            }
        }
    }
}

object SimpleModeUiState {
    var isEnabled by mutableStateOf(false)
    var stylePreset by mutableStateOf("PLAYit Classic Dark")
}

/**
 * Ultra-Smooth Hardware-Accelerated Liquid Glass Container.
 * Uses GPU graphicsLayer scaling (zero relayout overhead) and lightweight 2-pass glass rendering.
 * When Simple Mode (PLAYit / MX Player Classic Mode) is active, renders a clean, flat, ultra-fast classic card.
 */
@Composable
fun LiquidGlassBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    tintColor: Color = Color(0xFF00E676),
    primaryTint: Color = tintColor,
    secondaryTint: Color = Color(0xFF00E5FF),
    glassAlpha: Float = 0.22f,
    isActive: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    val simpleMode = SimpleModeUiState.isEnabled
    val simplePreset = SimpleModeUiState.stylePreset
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing),
        label = "liquid_glass_scale"
    )

    val shape = RoundedCornerShape(cornerRadius)
    val baseModifier = modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
            } else Modifier
        )

    Box(
        modifier = baseModifier,
        contentAlignment = contentAlignment
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val rPx = cornerRadius.toPx()
            val w = size.width
            val h = size.height
            val baseAlpha = glassAlpha.coerceIn(0.08f, 0.85f)
            val cr = CornerRadius(rPx, rPx)

            if (simpleMode) {
                val cardSurface = when (simplePreset) {
                    "AMOLED Pure Black" -> if (isActive) Color(0xFF18202C) else Color(0xFF11141A)
                    "MX Slate Blue" -> if (isActive) Color(0xFF1E293B) else Color(0xFF151E2E)
                    else -> if (isActive) Color(0xFF1E2430) else Color(0xFF161A22)
                }
                val borderCol = if (isActive) Color(0xFF38BDF8) else Color(0xFF28303F)
                drawRoundRect(
                    color = cardSurface,
                    cornerRadius = cr
                )
                drawRoundRect(
                    color = borderCol,
                    cornerRadius = cr,
                    style = Stroke(width = if (isActive) 1.4.dp.toPx() else 1.dp.toPx())
                )
            } else {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = if (isActive) {
                            listOf(
                                primaryTint.copy(alpha = (baseAlpha + 0.15f).coerceAtMost(0.62f)),
                                Color(0xD910192B),
                                secondaryTint.copy(alpha = (baseAlpha + 0.08f).coerceAtMost(0.52f))
                            )
                        } else {
                            listOf(
                                primaryTint.copy(alpha = baseAlpha),
                                Color(0xD90E1524),
                                secondaryTint.copy(alpha = (baseAlpha * 0.62f).coerceAtLeast(0.08f))
                            )
                        },
                        start = Offset.Zero,
                        end = Offset(w, h)
                    ),
                    cornerRadius = cr
                )

                drawRoundRect(
                    color = if (isActive) primaryTint.copy(alpha = 0.82f) else primaryTint.copy(alpha = 0.36f),
                    cornerRadius = cr,
                    style = Stroke(width = if (isActive) 1.5.dp.toPx() else 1.dp.toPx())
                )
            }
        }
        content()
    }
}

/**
 * Circular 3D Liquid Glass Icon Orb with GPU-accelerated press scaling.
 */
@Composable
fun LiquidGlassIconOrb(
    icon: ImageVector,
    contentDescription: String? = null,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    tintColor: Color = Color(0xFF00E5FF),
    glowColor: Color = tintColor,
    secondaryColor: Color = Color(0xFF00E676),
    isActive: Boolean = false,
    iconTint: Color = Color.White,
    testTag: String? = null
) {
    val simpleMode = SimpleModeUiState.isEnabled
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 85, easing = FastOutSlowInEasing),
        label = "orb_scale"
    )

    Box(
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)

            if (simpleMode) {
                drawCircle(
                    color = if (isActive) Color(0xFF1E293B) else Color(0xFF181D26),
                    radius = r
                )
                drawCircle(
                    color = if (isActive) Color(0xFF38BDF8) else Color(0xFF2D3646),
                    radius = r - 1.dp.toPx(),
                    style = Stroke(width = 1.dp.toPx())
                )
            } else {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = if (isActive) {
                            listOf(
                                glowColor.copy(alpha = 0.45f),
                                secondaryColor.copy(alpha = 0.24f),
                                Color(0xE60D1524)
                            )
                        } else {
                            listOf(
                                glowColor.copy(alpha = 0.20f),
                                Color(0xCC162238),
                                Color(0xE60B111E)
                            )
                        },
                        center = Offset(center.x - r * 0.22f, center.y - r * 0.22f),
                        radius = r * 1.2f
                    ),
                    radius = r
                )

                drawCircle(
                    color = if (isActive) glowColor.copy(alpha = 0.88f) else glowColor.copy(alpha = 0.48f),
                    radius = r - 1.dp.toPx(),
                    style = Stroke(width = if (isActive) 1.5.dp.toPx() else 1.1.dp.toPx())
                )
            }
        }

        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) glowColor else iconTint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Pure Visual Liquid Glass 5-Band Animated Equalizer Component:
 * Runs animation ONLY when `isPlaying == true` (0% CPU when paused!).
 */
@Composable
fun LiquidGlassEqualizerWidget(
    bands: List<Int> = listOf(60, 75, 55, 80, 65),
    isPlaying: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    isActive: Boolean = true
) {
    val phase = if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "eq_liquid_bars")
        val animated by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 6.28318f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = LinearEasing)
            ),
            label = "eq_phase"
        )
        animated
    } else {
        0f
    }

    val barColors = remember {
        listOf(
            Color(0xFF00E5FF),
            Color(0xFF00E676),
            Color(0xFFFF2A85),
            Color(0xFFFFD700),
            Color(0xFFB388FF)
        )
    }

    LiquidGlassBox(
        modifier = modifier.size(width = size * 1.25f, height = size),
        cornerRadius = size / 2,
        primaryTint = Color(0xFF00E5FF),
        secondaryTint = Color(0xFFFF2A85),
        isActive = isActive,
        onClick = onClick
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            val barCount = 5
            val spacing = this.size.width / barCount
            val barWidth = (spacing * 0.52f).coerceAtLeast(3.dp.toPx())

            for (i in 0 until barCount) {
                val bandLevel = (bands.getOrNull(i) ?: 55).coerceIn(15, 100) / 100f
                val wave = if (isPlaying) (sin(phase + i * 1.15f) + 1f) / 2f else bandLevel
                val normalized = (0.28f + (bandLevel * 0.35f) + (wave * 0.37f)).coerceIn(0.28f, 1f)
                val barH = this.size.height * normalized
                val x = i * spacing + spacing / 2f
                val topY = this.size.height - barH
                val color = barColors[i % barColors.size]

                drawLine(
                    color = Color.White.copy(alpha = 0.14f),
                    start = Offset(x, 0f),
                    end = Offset(x, this.size.height),
                    strokeWidth = barWidth * 0.65f,
                    cap = StrokeCap.Round
                )

                drawLine(
                    color = color,
                    start = Offset(x, topY),
                    end = Offset(x, this.size.height),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round
                )

                drawCircle(
                    color = Color.White,
                    radius = barWidth * 0.55f,
                    center = Offset(x, topY)
                )
            }
        }
    }
}

@Composable
fun ColorfulEqualizerOrb(
    onClick: () -> Unit,
    size: Dp = 44.dp
) {
    LiquidGlassEqualizerWidget(
        onClick = onClick,
        size = size,
        isPlaying = false,
        isActive = true
    )
}

@Composable
fun DoubleShakeVibrationOrb(
    isEnabled: Boolean,
    onClick: () -> Unit,
    size: Dp = 44.dp
) {
    LiquidGlassShakeOrbWidget(
        isEnabled = isEnabled,
        onClick = onClick,
        size = size
    )
}

@Composable
fun VisualPrecisionSeekBar(
    progressFraction: Float,
    onSeekFractionChanged: (Float) -> Unit,
    accentColor: Color,
    secondaryAccent: Color,
    modifier: Modifier = Modifier
) {
    Slider(
        value = progressFraction.coerceIn(0f, 1f),
        onValueChange = { onSeekFractionChanged(it.coerceIn(0f, 1f)) },
        colors = SliderDefaults.colors(
            thumbColor = accentColor,
            activeTrackColor = accentColor,
            inactiveTrackColor = secondaryAccent.copy(alpha = 0.24f)
        ),
        modifier = modifier
    )
}

/**
 * Pure Visual Liquid Glass Hardware Volume Booster Rotary Orb (Zero-CPU when boost is off).
 */
@Composable
fun LiquidGlassVolumeBoosterWidget(
    isBoostEnabled: Boolean = false,
    isBoostActive: Boolean = isBoostEnabled,
    volumePercent: Int = if (isBoostActive) 200 else 100,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    size: Dp = 42.dp
) {
    val activeGlow = if (isBoostActive || volumePercent > 100) Color(0xFFFFD700) else Color(0xFF00E5FF)
    val secondaryGlow = if (isBoostActive || volumePercent > 100) Color(0xFFFF2A85) else Color(0xFF00E676)

    LiquidGlassBox(
        modifier = modifier.size(size),
        cornerRadius = size / 2,
        primaryTint = activeGlow,
        secondaryTint = secondaryGlow,
        isActive = isBoostActive || volumePercent > 100,
        onClick = onClick
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(5.dp)
        ) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f
            val r = (minOf(w, h) / 2f)

            drawArc(
                color = Color.White.copy(alpha = 0.18f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )

            val sweep = ((volumePercent.coerceIn(0, 200) / 200f) * 270f).coerceAtLeast(35f)
            drawArc(
                color = activeGlow,
                startAngle = 135f,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
            )

            val conePath = Path().apply {
                moveTo(cx - r * 0.36f, cy - r * 0.14f)
                lineTo(cx - r * 0.16f, cy - r * 0.14f)
                lineTo(cx + r * 0.06f, cy - r * 0.34f)
                lineTo(cx + r * 0.06f, cy + r * 0.34f)
                lineTo(cx - r * 0.16f, cy + r * 0.14f)
                lineTo(cx - r * 0.36f, cy + r * 0.14f)
                close()
            }
            drawPath(
                path = conePath,
                color = if (isBoostActive || volumePercent > 100) Color(0xFFFFD700) else Color.White
            )

            drawArc(
                color = activeGlow,
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(cx - r * 0.12f, cy - r * 0.24f),
                size = Size(r * 0.42f, r * 0.48f),
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Pure Visual Liquid Glass Double-Shake Vibration Component (Zero-CPU static render).
 */
@Composable
fun LiquidGlassShakeOrbWidget(
    isEnabled: Boolean = true,
    enabled: Boolean = isEnabled,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val glow = if (enabled) Color(0xFF00E5FF) else Color(0xFF64748B)
    val secondary = if (enabled) Color(0xFF00E676) else Color(0xFF475569)

    LiquidGlassBox(
        modifier = modifier.size(size),
        cornerRadius = size / 2,
        primaryTint = glow,
        secondaryTint = secondary,
        isActive = enabled,
        onClick = onClick
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(7.dp)
        ) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f

            drawRoundRect(
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.55f),
                topLeft = Offset(cx - w * 0.18f, cy - h * 0.28f),
                size = Size(w * 0.36f, h * 0.56f),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                style = Stroke(width = 1.8.dp.toPx())
            )

            val waveColor = if (enabled) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.3f)
            drawArc(
                color = waveColor,
                startAngle = 135f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(cx - w * 0.44f, cy - h * 0.28f),
                size = Size(w * 0.32f, h * 0.56f),
                style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = waveColor,
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(cx + w * 0.12f, cy - h * 0.28f),
                size = Size(w * 0.32f, h * 0.56f),
                style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Pure Visual Liquid Glass Speed Dial Gauge Widget.
 */
@Composable
fun LiquidGlassSpeedDialOrb(
    speed: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp
) {
    val isModified = speed != 1.0f
    val glow = if (isModified) Color(0xFF00E5FF) else Color(0xFFB388FF)

    LiquidGlassBox(
        modifier = modifier.size(size),
        cornerRadius = size / 2,
        primaryTint = glow,
        secondaryTint = Color(0xFF00E676),
        isActive = isModified,
        onClick = onClick
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
        ) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h * 0.58f
            val r = minOf(w, h) * 0.44f

            drawArc(
                color = glow,
                startAngle = 160f,
                sweepAngle = 220f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
            )

            val normalized = ((speed - 0.5f) / 1.5f).coerceIn(0f, 1f)
            val angleRad = Math.toRadians((160f + normalized * 220f).toDouble())
            val needleEnd = Offset(
                x = cx + (cos(angleRad) * r * 0.78f).toFloat(),
                y = cy + (sin(angleRad) * r * 0.78f).toFloat()
            )
            drawLine(
                color = Color.White,
                start = Offset(cx, cy),
                end = needleEnd,
                strokeWidth = 2.2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = glow,
                radius = 2.8.dp.toPx(),
                center = Offset(cx, cy)
            )
        }
    }
}
