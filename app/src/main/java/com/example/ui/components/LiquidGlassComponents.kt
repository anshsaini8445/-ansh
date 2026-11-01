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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

private object AlbumArtBitmapCache {
    private val cache = object : LruCache<String, Bitmap>(80) {}
    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }
}

/**
 * Extracts and caches real embedded album art (for MP3/M4A/FLAC) or a real video frame (for MP4/MKV)
 * with a vibrant vinyl disc fallback when a file has no embedded cover image.
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
    var bitmap by remember(mediaPath) { mutableStateOf(AlbumArtBitmapCache.get(mediaPath)) }

    LaunchedEffect(mediaPath, isVideo) {
        if (bitmap == null && mediaPath.isNotBlank()) {
            val loaded = withContext(Dispatchers.IO) {
                val cached = AlbumArtBitmapCache.get(mediaPath)
                if (cached != null) return@withContext cached
                val file = File(mediaPath)
                if (!file.exists()) return@withContext null
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(mediaPath)
                    val embedded = retriever.embeddedPicture
                    val rawBmp = if (embedded != null && embedded.isNotEmpty()) {
                        BitmapFactory.decodeByteArray(embedded, 0, embedded.size)
                    } else if (isVideo) {
                        retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } else {
                        null
                    }
                    if (rawBmp != null) {
                        val scaled = Bitmap.createScaledBitmap(rawBmp, 160, 160, true)
                        AlbumArtBitmapCache.put(mediaPath, scaled)
                        scaled
                    } else null
                } catch (_: Exception) {
                    null
                } finally {
                    try {
                        retriever.release()
                    } catch (_: Exception) {
                    }
                }
            }
            if (loaded != null) {
                bitmap = loaded
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
                        accentColor.copy(alpha = 0.25f),
                        Color(0xFF09101D)
                    )
                )
            )
            .border(1.dp, accentColor.copy(alpha = 0.45f), shape),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
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
                    .border(1.5.dp, accentColor.copy(alpha = 0.75f), CircleShape),
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

/**
 * Ultra-Smooth Colorful Liquid Glass Container.
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "liquid_glass_scale"
    )

    val shape = RoundedCornerShape(cornerRadius)
    val baseModifier = modifier
        .scale(scale)
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

            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = if (isActive) {
                        listOf(
                            primaryTint.copy(alpha = (baseAlpha + 0.16f).coerceAtMost(0.65f)),
                            Color(0xD910192B),
                            secondaryTint.copy(alpha = (baseAlpha + 0.08f).coerceAtMost(0.55f))
                        )
                    } else {
                        listOf(
                            primaryTint.copy(alpha = baseAlpha),
                            Color(0xD90E1524),
                            secondaryTint.copy(alpha = (baseAlpha * 0.65f).coerceAtLeast(0.08f))
                        )
                    },
                    start = Offset.Zero,
                    end = Offset(w, h)
                ),
                cornerRadius = CornerRadius(rPx, rPx)
            )

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isActive) 0.28f else 0.16f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = h * 0.52f
                ),
                size = Size(w, h * 0.52f),
                cornerRadius = CornerRadius(rPx, rPx)
            )

            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = if (isActive) {
                        listOf(
                            Color.White.copy(alpha = 0.85f),
                            primaryTint.copy(alpha = 0.95f),
                            secondaryTint.copy(alpha = 0.85f)
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.42f),
                            primaryTint.copy(alpha = 0.38f),
                            secondaryTint.copy(alpha = 0.24f)
                        )
                    },
                    start = Offset.Zero,
                    end = Offset(w, h)
                ),
                cornerRadius = CornerRadius(rPx, rPx),
                style = Stroke(width = if (isActive) 1.6.dp.toPx() else 1.1.dp.toPx())
            )
        }
        content()
    }
}

/**
 * Circular 3D Liquid Glass Icon Orb with Specular Droplet Highlight & Colorful Prism Glow.
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "orb_scale"
    )

    Box(
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .size(size)
            .scale(scale)
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

            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (isActive) {
                        listOf(
                            glowColor.copy(alpha = 0.48f),
                            secondaryColor.copy(alpha = 0.26f),
                            Color(0xE60D1524)
                        )
                    } else {
                        listOf(
                            glowColor.copy(alpha = 0.22f),
                            Color(0xCC162238),
                            Color(0xE60B111E)
                        )
                    },
                    center = Offset(center.x - r * 0.25f, center.y - r * 0.25f),
                    radius = r * 1.25f
                ),
                radius = r
            )

            drawOval(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.42f),
                        Color.Transparent
                    )
                ),
                topLeft = Offset(center.x - r * 0.62f, center.y - r * 0.84f),
                size = Size(r * 1.24f, r * 0.72f)
            )

            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.75f),
                        glowColor.copy(alpha = if (isActive) 0.95f else 0.55f),
                        secondaryColor.copy(alpha = if (isActive) 0.90f else 0.45f),
                        Color.White.copy(alpha = 0.75f)
                    )
                ),
                radius = r - 1.dp.toPx(),
                style = Stroke(width = if (isActive) 1.7.dp.toPx() else 1.2.dp.toPx())
            )
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
 * Pure Visual Liquid Glass 5-Band Animated Equalizer Component (NO "Equalizer" text label!).
 */
@Composable
fun LiquidGlassEqualizerWidget(
    bands: List<Int> = listOf(60, 75, 55, 80, 65),
    isPlaying: Boolean = true,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    isActive: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "eq_liquid_bars")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing)
        ),
        label = "eq_phase"
    )

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
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White, color, color.copy(alpha = 0.6f)),
                        startY = topY,
                        endY = this.size.height
                    ),
                    start = Offset(x, topY),
                    end = Offset(x, this.size.height),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round
                )

                drawCircle(
                    color = Color.White,
                    radius = barWidth * 0.58f,
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
 * Pure Visual Liquid Glass Hardware Volume Booster Rotary Orb (NO "Volume Boost" text label!).
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
    val infiniteTransition = rememberInfiniteTransition(label = "boost_pulse")
    val sonicWave by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sonic_wave"
    )

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
                style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
            )

            val sweep = ((volumePercent.coerceIn(0, 200) / 200f) * 270f).coerceAtLeast(35f)
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF00E676),
                        Color(0xFF00E5FF),
                        Color(0xFFFFD700),
                        Color(0xFFFF2A85),
                        Color(0xFF00E676)
                    )
                ),
                startAngle = 135f,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
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

            val waveScale = if (isBoostActive) sonicWave else 1f
            drawArc(
                color = activeGlow,
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(cx - r * 0.12f, cy - r * 0.24f * waveScale),
                size = Size(r * 0.42f * waveScale, r * 0.48f * waveScale),
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
            )
            if (isBoostActive || volumePercent > 100) {
                drawArc(
                    color = secondaryGlow,
                    startAngle = -50f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(cx - r * 0.04f, cy - r * 0.38f * waveScale),
                    size = Size(r * 0.56f * waveScale, r * 0.76f * waveScale),
                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}

/**
 * Pure Visual Liquid Glass Double-Shake Vibration Component (Vibrating Phone + Neon Shockwaves).
 */
@Composable
fun LiquidGlassShakeOrbWidget(
    isEnabled: Boolean = true,
    enabled: Boolean = isEnabled,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shake_orb")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shake_wiggle"
    )

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
            val cx = w / 2f + (if (enabled) waveOffset * 1.5f else 0f)
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
                brush = Brush.sweepGradient(
                    colors = listOf(Color(0xFF00E676), Color(0xFF00E5FF), Color(0xFFFF2A85), Color(0xFFFFD700))
                ),
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
