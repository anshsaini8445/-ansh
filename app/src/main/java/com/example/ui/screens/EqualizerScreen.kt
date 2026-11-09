package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit,
    accentColor: Color
) {
    val activePreset by viewModel.equalizerPreset.collectAsState()
    val bands by viewModel.equalizerBands.collectAsState()
    val bassBoostEnabled by viewModel.bassBoostEnabled.collectAsState()
    val is200BoostEnabled by viewModel.is200BoostEnabled.collectAsState()

    val bandLabels = listOf("60Hz", "230Hz", "910Hz", "4kHz", "14kHz")
    val bandSubLabels = listOf("Sub", "Bass", "Mid", "Presence", "Brilliance")
    val presets = listOf(
        "Flat",
        "Bass Booster",
        "Vocal Booster",
        "Treble Booster",
        "3D Surround",
        "Heavy Metal"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Studio Equalizer & DSP",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "5-Band Parametric Calibration • Active: $activePreset",
                                color = accentColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("equalizer_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    FilledTonalIconButton(
                        onClick = { viewModel.selectEqualizerPreset("Flat") },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("equalizer_reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Equalizer to Flat"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- 1. Live Acoustic Frequency Response Curve Card ---
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .border(
                        1.dp,
                        accentColor.copy(alpha = 0.28f),
                        RoundedCornerShape(20.dp)
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopStart),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FREQUENCY RESPONSE CURVE",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = accentColor.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = activePreset.uppercase(),
                                color = accentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 24.dp, bottom = 6.dp)
                    ) {
                        val width = size.width
                        val height = size.height
                        val midY = height / 2f

                        // Draw +12dB, 0dB, -12dB horizontal grid lines
                        listOf(0.15f, 0.5f, 0.85f).forEach { ratio ->
                            drawLine(
                                color = Color.White.copy(alpha = if (ratio == 0.5f) 0.22f else 0.08f),
                                start = Offset(0f, height * ratio),
                                end = Offset(width, height * ratio),
                                strokeWidth = if (ratio == 0.5f) 1.5.dp.toPx() else 1.dp.toPx()
                            )
                        }

                        if (bands.isNotEmpty()) {
                            val stepX = width / (bands.size - 1).coerceAtLeast(1)
                            val points = bands.mapIndexed { idx, pct ->
                                val x = idx * stepX
                                val normalized = (100f - pct.coerceIn(0, 100)) / 100f
                                val y = (height * 0.12f) + normalized * (height * 0.76f)
                                Offset(x, y)
                            }

                            val linePath = Path().apply {
                                moveTo(points.first().x, points.first().y)
                                for (i in 0 until points.size - 1) {
                                    val p0 = points[i]
                                    val p1 = points[i + 1]
                                    val controlX = (p0.x + p1.x) / 2f
                                    cubicTo(controlX, p0.y, controlX, p1.y, p1.x, p1.y)
                                }
                            }

                            val fillPath = Path().apply {
                                addPath(linePath)
                                lineTo(points.last().x, height)
                                lineTo(points.first().x, height)
                                close()
                            }

                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        accentColor.copy(alpha = 0.42f),
                                        accentColor.copy(alpha = 0.02f)
                                    )
                                )
                            )

                            drawPath(
                                path = linePath,
                                color = accentColor,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )

                            points.forEach { pt ->
                                drawCircle(
                                    color = Color.Black,
                                    radius = 5.5.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = accentColor,
                                    radius = 4.dp.toPx(),
                                    center = pt
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- 2. Material3 Sound Profile FilterChips ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sound Profiles",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Tap preset or drag sliders",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    val selected = activePreset == preset
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.selectEqualizerPreset(preset) },
                        label = {
                            Text(
                                text = preset,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Equalizer,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor,
                            selectedLabelColor = Color.Black,
                            selectedLeadingIconColor = Color.Black,
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = Color.White.copy(alpha = 0.14f),
                            selectedBorderColor = accentColor
                        ),
                        modifier = Modifier.testTag("eq_preset_${preset.lowercase().replace(" ", "_")}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- 3. 5-Band Visual Sliders Control Deck ---
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(310.dp)
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.08f),
                        RoundedCornerShape(22.dp)
                    ),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 16.dp, horizontal = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Manual Band Sliders (-12dB to +12dB)",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "0 dB Center",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        bands.forEachIndexed { idx, valPct ->
                            val dbVal = ((valPct - 50) * 12) / 50
                            val dbText = when {
                                dbVal > 0 -> "+$dbVal dB"
                                dbVal < 0 -> "$dbVal dB"
                                else -> "0 dB"
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(1f)
                            ) {
                                // dB Badge
                                Surface(
                                    color = if (dbVal != 0) accentColor.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = dbText,
                                        color = if (dbVal != 0) accentColor else Color.LightGray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Visual Vertical Slider + Tick Marks
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .width(48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Background LED level bars
                                    Column(
                                        modifier = Modifier
                                            .fillMaxHeight(0.84f)
                                            .width(24.dp),
                                        verticalArrangement = Arrangement.SpaceBetween,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        for (tick in 10 downTo 0) {
                                            val tickPct = tick * 10
                                            val isLit = valPct >= tickPct
                                            val isCenter = tick == 5
                                            Box(
                                                modifier = Modifier
                                                    .width(if (isCenter) 22.dp else 12.dp)
                                                    .height(2.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        when {
                                                            isCenter -> Color.White.copy(alpha = 0.45f)
                                                            isLit -> accentColor.copy(alpha = 0.45f)
                                                            else -> Color.White.copy(alpha = 0.08f)
                                                        }
                                                    )
                                            )
                                        }
                                    }

                                    Slider(
                                        value = valPct.toFloat(),
                                        onValueChange = { newVal ->
                                            viewModel.updateEqualizerBand(idx, newVal.toInt())
                                        },
                                        valueRange = 0f..100f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = accentColor,
                                            activeTrackColor = accentColor,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.14f)
                                        ),
                                        modifier = Modifier
                                            .graphicsLayer {
                                                rotationZ = 270f
                                            }
                                            .width(175.dp)
                                            .testTag("eq_band_slider_$idx")
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = bandLabels.getOrElse(idx) { "" },
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = bandSubLabels.getOrElse(idx) { "" },
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- 4. Hardware Bass Boost & 200% Studio Loudness Enhancer ---
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(accentColor.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = "Bass Booster",
                                    tint = accentColor
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "MAX Mega Bass Boost",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Deep sub-bass hardware punch (60Hz–120Hz)",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Switch(
                            checked = bassBoostEnabled,
                            onCheckedChange = { viewModel.toggleBassBoost() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = accentColor,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                            ),
                            modifier = Modifier.testTag("eq_bass_boost_switch")
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = Color.White.copy(alpha = 0.08f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFFB300).copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "200% Loudness Boost",
                                    tint = Color(0xFFFFB300)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "200% Hardware Loudness Pre-Amp",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Amplifies quiet audio & movies up to 200%",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Switch(
                            checked = is200BoostEnabled,
                            onCheckedChange = { viewModel.toggle200VolumeBoost() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFFFFB300),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                            ),
                            modifier = Modifier.testTag("eq_200_boost_switch")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
fun PresetChip(
    name: String,
    isActive: Boolean,
    onClick: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) accentColor else MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            color = if (isActive) Color.Black else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
