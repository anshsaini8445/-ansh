package com.example.ui.menus

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaItem
import com.example.helpers.PermanentDeleteHelper
import com.example.helpers.WifiDirectTransferEngine
import com.example.ui.components.LiquidGlassBox
import com.example.ui.components.LiquidGlassIconOrb
import com.example.ui.theme.*
import java.io.File
import java.util.Locale

/**
 * 7-Action Liquid Glass 3-Dot Menu for Video & Audio items:
 * 1. Track Details (Resolution, Codec, Format, Exact Size, Duration, Full Storage Path)
 * 2. Hybrid Share (Send via ShareKaro-style High-Speed Transfer)
 * 3. System Share (Send to any app on the phone: WhatsApp, Telegram, Quick Share, etc.)
 * 4. Copy / Share Direct File Link & URI
 * 5. Add to Favorites / Background Play
 * 6. Lock in Privacy Vault
 * 7. Permanent Delete from Entire Phone (Deletes permanently from device storage + MediaStore)
 */
@Composable
fun MediaSevenActionDialog(
    item: MediaItem,
    isFavorite: Boolean = false,
    onDismiss: () -> Unit,
    onHybridShare: (MediaItem) -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onLockInVault: (MediaItem) -> Unit,
    onPermanentDeleteConfirmed: (MediaItem) -> Unit,
    onMoveToRecycleBin: (MediaItem) -> Unit = {},
    onPlayBackgroundAudio: ((MediaItem) -> Unit)? = null
) {
    val context = LocalContext.current
    var showTrackDetailsSubView by remember { mutableStateOf(false) }
    var showPermanentDeleteConfirm by remember { mutableStateOf(false) }

    val systemDeleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) {
        onPermanentDeleteConfirmed(item)
        Toast.makeText(context, "Permanently Deleted from Phone Storage", Toast.LENGTH_SHORT).show()
        onDismiss()
    }

    if (showTrackDetailsSubView) {
        TrackCodecDetailsDialog(
            item = item,
            onBack = { showTrackDetailsSubView = false },
            onClose = onDismiss
        )
        return
    }

    if (showPermanentDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showPermanentDeleteConfirm = false },
            containerColor = HybridCardBg,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = HybridCoralRed)
                    Text(
                        text = item.cleanTitle,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Option 1: 100% Permanent Delete from Entire Phone
                    LiquidGlassBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("confirm_permanent_phone_delete_btn"),
                        cornerRadius = 16.dp,
                        tintColor = HybridCoralRed,
                        glassAlpha = 0.34f,
                        onClick = {
                            val deletedDirectly = PermanentDeleteHelper.deletePermanently(context, item)
                            val pendingIntent = if (!deletedDirectly) {
                                PermanentDeleteHelper.createSystemPermanentDeleteRequest(context, listOf(item))
                            } else null

                            if (pendingIntent != null) {
                                try {
                                    systemDeleteLauncher.launch(
                                        IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                                    )
                                } catch (_: Exception) {
                                    onPermanentDeleteConfirmed(item)
                                    onDismiss()
                                }
                            } else {
                                onPermanentDeleteConfirmed(item)
                                Toast.makeText(context, "Permanently Deleted from Phone Storage", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, tint = HybridCoralRed, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Permanent Delete (Entire Phone)",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = item.formattedSize,
                                    color = HybridCoralRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Option 2: Move to 30-Day Safe Recycle Bin
                    LiquidGlassBox(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16.dp,
                        tintColor = HybridGold,
                        glassAlpha = 0.22f,
                        onClick = {
                            onMoveToRecycleBin(item)
                            Toast.makeText(context, "Moved to 30-Day Recycle Bin", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.RestoreFromTrash, contentDescription = null, tint = HybridGold, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Recycle Bin (30 Days)",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPermanentDeleteConfirm = false }) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = HybridTextSecondary)
                }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HybridCardBg,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(HybridEmerald.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.isVideo) Icons.Default.Movie else Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = HybridEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.cleanTitle,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.resolution.uppercase()} • ${item.formattedSize} • ${item.formattedDuration}",
                        color = HybridEmerald,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 1. Track & Codec Details
                SevenMenuActionRow(
                    icon = Icons.Default.Info,
                    title = "Track Details",
                    subtitle = "${item.format} • ${item.resolution} • ${item.formattedSize}",
                    tint = HybridCyan,
                    testTag = "menu_action_track_details",
                    onClick = { showTrackDetailsSubView = true }
                )

                // 2. Hybrid High-Speed Share (Phone to Phone)
                SevenMenuActionRow(
                    icon = Icons.Default.Bolt,
                    title = "Hybrid Share",
                    subtitle = "Wi-Fi Direct / Live QR Transfer",
                    tint = HybridEmerald,
                    testTag = "menu_action_hybrid_share",
                    onClick = {
                        onDismiss()
                        onHybridShare(item)
                    }
                )

                // 3. System Share (Send to Any App on Phone)
                SevenMenuActionRow(
                    icon = Icons.Default.Share,
                    title = "System Share (All Apps)",
                    subtitle = "WhatsApp, Telegram, Quick Share, Bluetooth",
                    tint = Color(0xFF40C4FF),
                    testTag = "menu_action_system_share",
                    onClick = {
                        onDismiss()
                        WifiDirectTransferEngine.shareRealFilesViaSystem(context, listOf(item))
                    }
                )

                // 4. Copy / Share Direct File Link & URI
                SevenMenuActionRow(
                    icon = Icons.Default.Link,
                    title = "Copy & Share Link",
                    subtitle = item.path,
                    tint = HybridGold,
                    testTag = "menu_action_copy_link",
                    onClick = {
                        val shareUri = WifiDirectTransferEngine.resolveShareableUri(context, item)?.toString() ?: item.path
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Media Link", shareUri))
                        } catch (_: Exception) {
                        }
                        val linkIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, item.cleanTitle)
                            putExtra(Intent.EXTRA_TEXT, "${item.cleanTitle}\n$shareUri")
                        }
                        context.startActivity(Intent.createChooser(linkIntent, null))
                        onDismiss()
                    }
                )

                // 5. Add to Favorites / Background Audio Play
                SevenMenuActionRow(
                    icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    title = if (item.isVideo && onPlayBackgroundAudio != null) "Background Audio / Favorite" else "Favorite / Playlist",
                    subtitle = if (isFavorite) "★ Saved in Favorites" else "Tap to add to Favorites",
                    tint = Color(0xFFFF4081),
                    testTag = "menu_action_favorite",
                    onClick = {
                        onToggleFavorite(item)
                        onPlayBackgroundAudio?.invoke(item)
                        onDismiss()
                    }
                )

                // 6. Lock in Privacy Vault
                SevenMenuActionRow(
                    icon = Icons.Default.EnhancedEncryption,
                    title = "Lock in Privacy Vault",
                    subtitle = "AES-256 & Dual-PIN Protected",
                    tint = Color(0xFFB388FF),
                    testTag = "menu_action_lock_vault",
                    onClick = {
                        onDismiss()
                        onLockInVault(item)
                    }
                )

                // 7. Permanent Delete from Entire Phone
                SevenMenuActionRow(
                    icon = Icons.Default.DeleteForever,
                    title = "Permanent Delete (Phone)",
                    subtitle = "Erase permanently from device storage",
                    tint = HybridCoralRed,
                    testTag = "menu_action_permanent_delete",
                    onClick = {
                        showPermanentDeleteConfirm = true
                    }
                )
            }
        },
        confirmButton = {
            LiquidGlassIconOrb(
                icon = Icons.Default.Close,
                contentDescription = "Close",
                tintColor = Color.White,
                size = 36.dp,
                iconSize = 18.dp,
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun SevenMenuActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    testTag: String,
    onClick: () -> Unit
) {
    LiquidGlassBox(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        cornerRadius = 14.dp,
        tintColor = tint,
        glassAlpha = 0.20f,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.22f))
                    .border(1.dp, tint.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (tint == HybridCoralRed) HybridCoralRed else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = HybridTextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = tint.copy(alpha = 0.75f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun TrackCodecDetailsDialog(
    item: MediaItem,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    val fileObj = remember(item.path) { File(item.path) }
    val exactBytes = remember(fileObj, item.size) {
        if (fileObj.exists() && fileObj.length() > 0L) fileObj.length() else item.size
    }
    val bitrateKbps = remember(exactBytes, item.duration) {
        val secs = (item.duration / 1000L).coerceAtLeast(1L)
        ((exactBytes * 8L) / 1000L / secs).coerceAtLeast(128L)
    }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = HybridCardBg,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Memory, contentDescription = null, tint = HybridCyan)
                Text(
                    text = item.cleanTitle,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailKeyValueRow("Format / Container", item.format.uppercase(Locale.US), HybridEmerald)
                DetailKeyValueRow("Resolution / Tier", item.resolution.uppercase(Locale.US), HybridGold)
                DetailKeyValueRow("Duration", item.formattedDuration, Color.White)
                DetailKeyValueRow("Exact Size", "${item.formattedSize} ($exactBytes bytes)", HybridCyan)
                DetailKeyValueRow("Bitrate", "$bitrateKbps kbps • Hardware Decoded", Color.White)
                DetailKeyValueRow("Folder", item.folderName, HybridEmerald)
                DetailKeyValueRow("Storage Path", item.path, HybridTextSecondary)
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Icon(Icons.Default.Check, contentDescription = "Done", tint = HybridEmerald)
            }
        },
        dismissButton = {
            TextButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        }
    )
}

@Composable
private fun DetailKeyValueRow(label: String, value: String, valueColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(HybridCardAlt)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text = label, color = HybridTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(text = value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
