package com.example.ui.menus

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
 * Compact, Folder-Box Styled 3-Dot File Management Menu for Video & Audio items:
 * - Top 4 Quick Actions arranged in a compact 2x2 Folder-Box Grid:
 *   1. Favourite (❤️) | 2. Background Audio / Fast Share
 *   3. Fast Share (Phone to Phone) | 4. Share to Apps
 * - Bottom 4 Compact Folder-Style Action Boxes:
 *   5. Rename File
 *   6. File Details & Storage Path
 *   7. Lock in Private Folder
 *   8. Delete from Phone (Permanent Delete or 30-Day Recycle Bin)
 */
@Composable
fun MediaSevenActionDialog(
    item: MediaItem,
    isFavorite: Boolean = false,
    isHindi: Boolean = Locale.getDefault().language == "hi",
    onDismiss: () -> Unit,
    onHybridShare: (MediaItem) -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onLockInVault: (MediaItem) -> Unit,
    onPermanentDeleteConfirmed: (MediaItem) -> Unit,
    onMoveToRecycleBin: (MediaItem) -> Unit = {},
    onPlayBackgroundAudio: ((MediaItem) -> Unit)? = null,
    onRenameCompleted: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var showTrackDetailsSubView by remember { mutableStateOf(false) }
    var showPermanentDeleteConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember(item.cleanTitle) { mutableStateOf(item.cleanTitle) }

    val systemDeleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) {
        onPermanentDeleteConfirmed(item)
        Toast.makeText(
            context,
            if (isHindi) "फ़ोन से हमेशा के लिए हटा दिया गया" else "Deleted from Phone Storage",
            Toast.LENGTH_SHORT
        ).show()
        onDismiss()
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = HybridCardBg,
            title = {
                Text(
                    text = if (isHindi) "फ़ाइल का नाम बदलें" else "Rename File",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
            },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text(if (isHindi) "नया नाम" else "New File Name", color = HybridTextSecondary) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = renameText.trim()
                        if (trimmed.isNotEmpty()) {
                            try {
                                val srcFile = File(item.path)
                                if (srcFile.exists() && srcFile.parentFile != null) {
                                    val ext = srcFile.extension
                                    val destFile = File(srcFile.parentFile, if (ext.isNotEmpty()) "$trimmed.$ext" else trimmed)
                                    srcFile.renameTo(destFile)
                                }
                            } catch (_: Exception) {
                            }
                            onRenameCompleted?.invoke()
                            Toast.makeText(
                                context,
                                if (isHindi) "नाम बदल दिया गया: $trimmed" else "Renamed to $trimmed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        showRenameDialog = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HybridEmerald)
                ) {
                    Text(if (isHindi) "सेव करें" else "Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(if (isHindi) "रद्द करें" else "Cancel", color = HybridTextSecondary)
                }
            }
        )
        return
    }

    if (showTrackDetailsSubView) {
        TrackCodecDetailsDialog(
            item = item,
            isHindi = isHindi,
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
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Option 1: 100% Permanent Delete from Entire Phone
                    LiquidGlassBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("confirm_permanent_phone_delete_btn"),
                        cornerRadius = 14.dp,
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
                                Toast.makeText(
                                    context,
                                    if (isHindi) "फ़ोन से हमेशा के लिए हटा दिया गया" else "Permanently Deleted from Phone Storage",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onDismiss()
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, tint = HybridCoralRed, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isHindi) "फ़ोन से हमेशा के लिए हटाएं" else "Permanent Delete (Entire Phone)",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
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
                        cornerRadius = 14.dp,
                        tintColor = HybridGold,
                        glassAlpha = 0.22f,
                        onClick = {
                            onMoveToRecycleBin(item)
                            Toast.makeText(
                                context,
                                if (isHindi) "रीसायकल बिन में भेजा गया" else "Moved to 30-Day Recycle Bin",
                                Toast.LENGTH_SHORT
                            ).show()
                            onDismiss()
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.RestoreFromTrash, contentDescription = null, tint = HybridGold, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isHindi) "रीसायकल बिन (30 दिन सुरक्षित)" else "Recycle Bin (30 Days Safe)",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPermanentDeleteConfirm = false }) {
                    Text(if (isHindi) "बंद करें" else "Close", color = HybridTextSecondary)
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(HybridEmerald.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.isVideo) Icons.Default.Movie else Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = HybridEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.cleanTitle,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.formattedSize} • ${item.formattedDuration} • ${item.folderName}",
                        color = HybridEmerald,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Top 4 Compact Folder-Style Boxes (2x2 Grid)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CompactFolderActionTile(
                        modifier = Modifier.weight(1f),
                        icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        title = if (isHindi) {
                            if (isFavorite) "पसंदीदा से हटाएं" else "पसंदीदा ❤️"
                        } else {
                            if (isFavorite) "Unfavourite" else "Favourite"
                        },
                        tint = Color(0xFFFF4081),
                        testTag = "menu_action_favorite",
                        onClick = {
                            onToggleFavorite(item)
                            Toast.makeText(
                                context,
                                if (!isFavorite) {
                                    if (isHindi) "पसंदीदा में सेव ❤️" else "Saved to Favourite ❤️"
                                } else {
                                    if (isHindi) "पसंदीदा से हटाया गया" else "Removed from Favourite"
                                },
                                Toast.LENGTH_SHORT
                            ).show()
                            onDismiss()
                        }
                    )

                    if (item.isVideo && onPlayBackgroundAudio != null) {
                        CompactFolderActionTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Headphones,
                            title = if (isHindi) "बैकग्राउंड ऑडियो" else "BG Audio",
                            tint = HybridCyan,
                            testTag = "menu_action_bg_audio",
                            onClick = {
                                onDismiss()
                                onPlayBackgroundAudio.invoke(item)
                            }
                        )
                    } else {
                        CompactFolderActionTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Edit,
                            title = if (isHindi) "नाम बदलें" else "Rename",
                            tint = HybridGold,
                            testTag = "menu_action_copy_link",
                            onClick = { showRenameDialog = true }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CompactFolderActionTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Bolt,
                        title = if (isHindi) "तेज़ शेयर" else "Fast Share",
                        tint = HybridEmerald,
                        testTag = "menu_action_hybrid_share",
                        onClick = {
                            onDismiss()
                            onHybridShare(item)
                        }
                    )

                    CompactFolderActionTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Share,
                        title = if (isHindi) "ऐप्स में शेयर" else "Share App",
                        tint = Color(0xFF40C4FF),
                        testTag = "menu_action_system_share",
                        onClick = {
                            onDismiss()
                            WifiDirectTransferEngine.shareRealFilesViaSystem(context, listOf(item))
                        }
                    )
                }

                // Bottom 4 Compact Folder-Style Rows
                if (item.isVideo && onPlayBackgroundAudio != null) {
                    SevenMenuActionRow(
                        icon = Icons.Default.Edit,
                        title = if (isHindi) "फ़ाइल का नाम बदलें" else "Rename File",
                        subtitle = item.cleanTitle,
                        tint = HybridGold,
                        testTag = "menu_action_copy_link",
                        onClick = { showRenameDialog = true }
                    )
                }

                SevenMenuActionRow(
                    icon = Icons.Default.Info,
                    title = if (isHindi) "फ़ाइल की जानकारी" else "File Details & Path",
                    subtitle = "${item.format} • ${item.formattedSize} • ${item.folderName}",
                    tint = HybridCyan,
                    testTag = "menu_action_track_details",
                    onClick = { showTrackDetailsSubView = true }
                )

                SevenMenuActionRow(
                    icon = Icons.Default.EnhancedEncryption,
                    title = if (isHindi) "प्राइवेट फ़ोल्डर में लॉक" else "Lock in Private Folder",
                    subtitle = if (isHindi) "पिन से सुरक्षित छुपाएं" else "Hide securely with PIN",
                    tint = Color(0xFFB388FF),
                    testTag = "menu_action_lock_vault",
                    onClick = {
                        onDismiss()
                        onLockInVault(item)
                    }
                )

                SevenMenuActionRow(
                    icon = Icons.Default.DeleteForever,
                    title = if (isHindi) "फ़ोन से हटाएं (Delete)" else "Delete from Phone",
                    subtitle = if (isHindi) "हमेशा के लिए या रीसायकल बिन" else "Permanent or Recycle Bin",
                    tint = HybridCoralRed,
                    testTag = "menu_action_permanent_delete",
                    onClick = { showPermanentDeleteConfirm = true }
                )
            }
        },
        confirmButton = {
            LiquidGlassIconOrb(
                icon = Icons.Default.Close,
                contentDescription = "Close",
                tintColor = Color.White,
                size = 34.dp,
                iconSize = 17.dp,
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun CompactFolderActionTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    tint: Color,
    testTag: String,
    onClick: () -> Unit
) {
    LiquidGlassBox(
        modifier = modifier.testTag(testTag),
        cornerRadius = 12.dp,
        tintColor = tint,
        glassAlpha = 0.22f,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tint.copy(alpha = 0.22f))
                    .border(1.dp, tint.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = tint, modifier = Modifier.size(16.dp))
            }
            Text(
                text = title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
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
        cornerRadius = 12.dp,
        tintColor = tint,
        glassAlpha = 0.18f,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tint.copy(alpha = 0.22f))
                    .border(1.dp, tint.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = tint, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (tint == HybridCoralRed) HybridCoralRed else Color.White,
                    fontSize = 12.sp,
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
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun TrackCodecDetailsDialog(
    item: MediaItem,
    isHindi: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
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
                DetailKeyValueRow(
                    if (isHindi) "फ़ॉर्मेट" else "Format",
                    if (item.isPartialDownload) "PENDING (${item.format})" else item.format.uppercase(Locale.US),
                    HybridEmerald
                )
                DetailKeyValueRow(
                    if (isHindi) "समय (Duration)" else "Duration",
                    item.formattedDuration,
                    Color.White
                )
                DetailKeyValueRow(
                    if (isHindi) "फ़ाइल साइज़" else "Exact Size",
                    "${item.formattedSize} ($exactBytes bytes)",
                    HybridCyan
                )
                DetailKeyValueRow(
                    if (isHindi) "बिटरेट" else "Bitrate",
                    "$bitrateKbps kbps",
                    Color.White
                )
                DetailKeyValueRow(
                    if (isHindi) "फ़ोल्डर" else "Folder",
                    item.folderName,
                    HybridEmerald
                )
                DetailKeyValueRow(
                    if (isHindi) "स्टोरेज पाथ" else "Storage Path",
                    item.path,
                    HybridTextSecondary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Path", item.path))
                    Toast.makeText(context, if (isHindi) "पाथ कॉपी हो गया" else "Path copied", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                }
                onClose()
            }) {
                Text(if (isHindi) "पाथ कॉपी करें" else "Copy Path", color = HybridEmerald, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
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
