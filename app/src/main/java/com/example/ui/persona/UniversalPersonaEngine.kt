package com.example.ui.persona

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.LiquidGlassBox
import com.example.ui.theme.*

/**
 * Deep Global UX & Demographic Persona Engine:
 * Adapts the app's typography, touch targets, visual vibe, and quick-tools for:
 *  1. YOUTH_PRO ("Youth Cyber-Glass ⚡") — High-speed 120Hz Neon Liquid Glass, 3D visualizers, Pro Studio controls.
 *  2. SENIOR_EASY ("Senior Comfort 👴") — Extra-large readable fonts (+3sp), big 52dp 1-tap Play buttons, high-contrast clarity.
 *  3. KIDS_MAGIC ("Kids Safe Magic 👶") — Playful colorful cards, animated Penguin/Puppy companion, Kid-Lock protection.
 *  4. BHARAT_DESI ("Bharat Royal 🇮🇳") — Saffron-Emerald-Gold studio vibe, instant ShareKaro & Status Saver bar, Desi Bass boost.
 *  5. GLOBAL_MINIMAL ("Global Spatial 🌍") — Ultra-clean American/European spatial cinema layout, distraction-free grid.
 */
enum class DemographicMode(
    val id: String,
    val badgeEmoji: String,
    val title: String,
    val subtitle: String,
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val titleFontSize: TextUnit,
    val metaFontSize: TextUnit,
    val thumbnailWidth: Dp,
    val thumbnailHeight: Dp,
    val actionOrbSize: Dp,
    val showBigPlayButtonOnRow: Boolean,
    val showQuickFeaturePills: Boolean,
    val defaultThemeName: String
) {
    YOUTH_PRO(
        id = "YOUTH_PRO",
        badgeEmoji = "⚡",
        title = "Youth Pro Cyber-Glass",
        subtitle = "120Hz Liquid Glass • 3D Visualizers • Full Studio Controls",
        primaryAccent = Color(0xFF00E676),
        secondaryAccent = Color(0xFF00E5FF),
        titleFontSize = 14.sp,
        metaFontSize = 11.sp,
        thumbnailWidth = 128.dp,
        thumbnailHeight = 74.dp,
        actionOrbSize = 36.dp,
        showBigPlayButtonOnRow = false,
        showQuickFeaturePills = true,
        defaultThemeName = "Hybrid Emerald"
    ),
    SENIOR_EASY(
        id = "SENIOR_EASY",
        badgeEmoji = "👴",
        title = "Senior Easy & Clear Vision",
        subtitle = "Extra-Large Text • Big 1-Tap Play Buttons • High Contrast",
        primaryAccent = Color(0xFFFFD54F),
        secondaryAccent = Color(0xFF00E676),
        titleFontSize = 17.sp,
        metaFontSize = 13.sp,
        thumbnailWidth = 136.dp,
        thumbnailHeight = 84.dp,
        actionOrbSize = 44.dp,
        showBigPlayButtonOnRow = true,
        showQuickFeaturePills = true,
        defaultThemeName = "Solar Gold"
    ),
    KIDS_MAGIC(
        id = "KIDS_MAGIC",
        badgeEmoji = "👶",
        title = "Kids Magic & Safe Play",
        subtitle = "Colorful Candy Cards • Dancing Penguin DJ • Auto Child-Lock",
        primaryAccent = Color(0xFFFF4081),
        secondaryAccent = Color(0xFF00E5FF),
        titleFontSize = 16.sp,
        metaFontSize = 12.sp,
        thumbnailWidth = 140.dp,
        thumbnailHeight = 84.dp,
        actionOrbSize = 42.dp,
        showBigPlayButtonOnRow = true,
        showQuickFeaturePills = false,
        defaultThemeName = "Candy Pop"
    ),
    BHARAT_DESI(
        id = "BHARAT_DESI",
        badgeEmoji = "🇮🇳",
        title = "Bharat Royal Masala",
        subtitle = "Instant ShareKaro • Status Saver • Dolby Desi Bass Boost",
        primaryAccent = Color(0xFFFF9100),
        secondaryAccent = Color(0xFF00E676),
        titleFontSize = 15.sp,
        metaFontSize = 11.sp,
        thumbnailWidth = 132.dp,
        thumbnailHeight = 76.dp,
        actionOrbSize = 38.dp,
        showBigPlayButtonOnRow = true,
        showQuickFeaturePills = true,
        defaultThemeName = "Royal Gold Palace"
    ),
    GLOBAL_MINIMAL(
        id = "GLOBAL_MINIMAL",
        badgeEmoji = "🌍",
        title = "Global Spatial Cinema",
        subtitle = "Ultra-Clean Cupertino & Western Aesthetic • Full-Bleed Cinema",
        primaryAccent = Color(0xFF00E5FF),
        secondaryAccent = Color(0xFF7C4DFF),
        titleFontSize = 14.sp,
        metaFontSize = 11.sp,
        thumbnailWidth = 136.dp,
        thumbnailHeight = 76.dp,
        actionOrbSize = 36.dp,
        showBigPlayButtonOnRow = false,
        showQuickFeaturePills = false,
        defaultThemeName = "Arctic Frost"
    );

    companion object {
        private const val PREF_NAME = "HYBRID_PERSONA_PREFS"
        private const val KEY_MODE = "active_demographic_mode"

        fun loadSavedMode(context: Context): DemographicMode {
            val saved = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_MODE, YOUTH_PRO.id)
            return entries.find { it.id == saved } ?: YOUTH_PRO
        }

        fun saveMode(context: Context, mode: DemographicMode) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_MODE, mode.id)
                .apply()
        }
    }
}

@Composable
fun UniversalPersonaSelectorDialog(
    activeMode: DemographicMode,
    onSelectMode: (DemographicMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0B1220),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(activeMode.primaryAccent.copy(alpha = 0.22f))
                        .border(1.dp, activeMode.primaryAccent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = activeMode.badgeEmoji, fontSize = 18.sp)
                }
                Column {
                    Text(
                        text = "Universal Experience Mode",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "Crafted for Kids, Youth, Seniors, India & Global",
                        color = HybridTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(DemographicMode.entries) { mode ->
                    val selected = mode == activeMode
                    LiquidGlassBox(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        tintColor = mode.primaryAccent,
                        glassAlpha = if (selected) 0.34f else 0.12f,
                        onClick = {
                            onSelectMode(mode)
                            onDismiss()
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(mode.primaryAccent, mode.secondaryAccent)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = mode.badgeEmoji, fontSize = 21.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = mode.subtitle,
                                    color = HybridTextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = mode.primaryAccent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = activeMode.primaryAccent, fontWeight = FontWeight.ExtraBold)
            }
        }
    )
}
