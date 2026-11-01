package com.example.ui.share

import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaItem
import com.example.helpers.DiscoveredPeerDevice
import com.example.helpers.WifiDirectTransferEngine
import com.example.ui.components.GuaranteedAlbumArt
import com.example.ui.components.LiquidGlassBox
import com.example.ui.components.LiquidGlassIconOrb
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private enum class HybridShareStage {
    HOME_CHOICE,          // Entry screen: SEND or RECEIVE
    SEND_MEDIA_PICKER,    // Step 1 of Send: Pick Videos & Songs
    SEND_CONNECT_RADAR,   // Step 2 of Send: Scan QR / Enter Receiver Pairing Code or Tap Discovered Receiver on Radar
    RECEIVE_LIVE_QR,      // Step 1 of Receive: Immediate Live QR Code + UDP Broadcast + TCP Server
    CONNECTED_TRANSFER    // Connected high-speed transfer screen with MB/s & item progress list
}

private data class TransferQueueEntry(
    val id: String,
    val title: String,
    val sizeText: String,
    val isVideo: Boolean,
    val progress: Float,
    val statusText: String
)

@Composable
fun ShareKaroTransferScreen(
    allMedia: List<MediaItem>,
    initialPreselectedItem: MediaItem? = null,
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isHindi = remember { Locale.getDefault().language == "hi" }
    val videosLabel = if (isHindi) "वीडियो" else "Videos"
    val musicLabel = if (isHindi) "ऑडियो" else "Songs"
    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    val videos = remember(allMedia) { allMedia.filter { it.isVideo } }
    val audios = remember(allMedia) { allMedia.filter { !it.isVideo } }

    var stage by remember {
        mutableStateOf(
            if (initialPreselectedItem != null) HybridShareStage.SEND_MEDIA_PICKER
            else HybridShareStage.HOME_CHOICE
        )
    }

    val selectedIds = remember {
        mutableStateListOf<String>().apply {
            if (initialPreselectedItem != null) add(initialPreselectedItem.id)
        }
    }
    var mediaPickerTab by remember {
        mutableIntStateOf(if (initialPreselectedItem != null && !initialPreselectedItem.isVideo) 1 else 0)
    } // 0 = Videos, 1 = Songs

    val localDeviceName = remember {
        "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
    }
    val localIpAddress = remember { WifiDirectTransferEngine.getLocalWifiIpAddress() }
    val localPairingCode = remember(localIpAddress) {
        localIpAddress.substringAfterLast('.', "1")
    }
    val transferPort = WifiDirectTransferEngine.DEFAULT_PORT

    var connectedDeviceName by remember { mutableStateOf("") }
    var showQrScannerInput by remember { mutableStateOf(true) }
    var scannedCodeInput by remember { mutableStateOf("") }
    var isConnectingHandshake by remember { mutableStateOf(false) }
    var connectionErrorText by remember { mutableStateOf<String?>(null) }

    // Live transfer telemetry
    var currentSpeedMbps by remember { mutableFloatStateOf(0f) }
    val transferItems = remember { mutableStateListOf<TransferQueueEntry>() }

    // Real discovered Receiver phones on local Wi-Fi / Hotspot via UDP beacon (NO fake devices!)
    val discoveredPeers = remember { mutableStateListOf<DiscoveredPeerDevice>() }

    val liveQrPayload = remember(localDeviceName, localIpAddress) {
        "HYBRIDSHARE|$localDeviceName|$localIpAddress|$transferPort"
    }
    val qrMatrix = remember(liveQrPayload) {
        WifiDirectTransferEngine.generateLiveQrMatrix(liveQrPayload, 0)
    }

    // When in RECEIVE_LIVE_QR: run real TCP ServerSocket AND broadcast UDP presence beacon
    LaunchedEffect(stage) {
        if (stage == HybridShareStage.RECEIVE_LIVE_QR) {
            launch {
                WifiDirectTransferEngine.broadcastReceiverPresence(
                    deviceName = localDeviceName,
                    localIp = localIpAddress,
                    tcpPort = transferPort
                )
            }
            launch {
                WifiDirectTransferEngine.startReceiverSocketServer(
                    context = context,
                    receiverDeviceName = localDeviceName,
                    port = transferPort,
                    onClientConnected = { clientNameAndIp ->
                        connectedDeviceName = clientNameAndIp
                        currentSpeedMbps = 0f
                        stage = HybridShareStage.CONNECTED_TRANSFER
                        Toast.makeText(
                            context,
                            if (isHindi) "सफलतापूर्वक कनेक्ट हो गया: $clientNameAndIp" else "Connected Successfully: $clientNameAndIp",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onFileProgress = { fileName, transferred, total, speedMbps ->
                        val progress = if (total > 0L) (transferred.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 1f
                        currentSpeedMbps = speedMbps
                        val idx = transferItems.indexOfFirst { it.id == fileName }
                        val entry = TransferQueueEntry(
                            id = fileName,
                            title = fileName,
                            sizeText = String.format(Locale.US, "%.1f MB", total / (1024.0 * 1024.0)),
                            isVideo = fileName.endsWith(".mp4", true) || fileName.endsWith(".mkv", true) || fileName.endsWith(".webm", true),
                            progress = progress,
                            statusText = if (progress >= 1f) "Received • 100%" else "Receiving • ${(progress * 100).toInt()}%"
                        )
                        if (idx >= 0) {
                            transferItems[idx] = entry
                        } else {
                            transferItems.add(entry)
                        }
                    },
                    onFileSaved = { savedFile ->
                        Toast.makeText(context, "Saved: ${savedFile.name}", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        } else if (stage == HybridShareStage.SEND_CONNECT_RADAR) {
            discoveredPeers.clear()
            connectionErrorText = null
            WifiDirectTransferEngine.listenForReceiverBeacons(
                localIp = localIpAddress,
                onPeerDiscovered = { peer ->
                    val existingIdx = discoveredPeers.indexOfFirst { it.ipAddress == peer.ipAddress }
                    if (existingIdx >= 0) {
                        discoveredPeers[existingIdx] = peer
                    } else {
                        discoveredPeers.add(peer)
                    }
                }
            )
        }
    }

    fun connectAndSendToRealPeer(peer: DiscoveredPeerDevice) {
        val chosenMedia = allMedia.filter { it.id in selectedIds }
        if (chosenMedia.isEmpty()) return
        if (isConnectingHandshake) return

        isConnectingHandshake = true
        connectionErrorText = null

        scope.launch {
            val preparedQueue = chosenMedia.map { item ->
                TransferQueueEntry(
                    id = item.id,
                    title = item.cleanTitle,
                    sizeText = item.formattedSize,
                    isVideo = item.isVideo,
                    progress = 0f,
                    statusText = "Waiting..."
                )
            }

            val success = WifiDirectTransferEngine.sendFilesOverTcpSocket(
                senderDeviceName = localDeviceName,
                targetIp = peer.ipAddress,
                port = peer.port,
                items = chosenMedia,
                onHandshakeConnected = { receiverAckName ->
                    isConnectingHandshake = false
                    connectedDeviceName = "$receiverAckName (${peer.ipAddress})"
                    transferItems.clear()
                    transferItems.addAll(preparedQueue)
                    stage = HybridShareStage.CONNECTED_TRANSFER
                    Toast.makeText(
                        context,
                        if (isHindi) "कनेक्ट हो गया! ट्रांसफर शुरू..." else "Connected Successfully! Sending files...",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onProgress = { idx, transferred, total, speedMbps ->
                    val progress = if (total > 0L) (transferred.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 1f
                    currentSpeedMbps = speedMbps
                    if (idx in transferItems.indices) {
                        val old = transferItems[idx]
                        transferItems[idx] = old.copy(
                            progress = progress,
                            statusText = if (progress >= 1f) "Sent • 100%" else "Sending • ${(progress * 100).toInt()}%"
                        )
                    }
                }
            )

            isConnectingHandshake = false
            if (!success && stage != HybridShareStage.CONNECTED_TRANSFER) {
                connectionErrorText = if (isHindi) {
                    "रिसीवर फ़ोन (${peer.ipAddress}) से कनेक्ट नहीं हो सका। सुनिश्चित करें कि दूसरे फ़ोन पर 'Receive' खुला है और दोनों एक ही Wi-Fi/Hotspot पर हैं।"
                } else {
                    "Could not connect to Receiver (${peer.ipAddress}). Make sure the other phone has 'Receive' open on the same Wi-Fi / Hotspot."
                }
            } else if (success) {
                currentSpeedMbps = 0f
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        Color(0xFF050811)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiquidGlassIconOrb(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    size = 42.dp,
                    iconSize = 20.dp,
                    tintColor = accent,
                    glowColor = accent,
                    testTag = "share_back_btn",
                    onClick = {
                        when (stage) {
                            HybridShareStage.HOME_CHOICE -> onBackClick()
                            HybridShareStage.SEND_MEDIA_PICKER -> stage = HybridShareStage.HOME_CHOICE
                            HybridShareStage.SEND_CONNECT_RADAR -> stage = HybridShareStage.SEND_MEDIA_PICKER
                            HybridShareStage.RECEIVE_LIVE_QR -> stage = HybridShareStage.HOME_CHOICE
                            HybridShareStage.CONNECTED_TRANSFER -> stage = HybridShareStage.HOME_CHOICE
                        }
                    }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isHindi) "हाइब्रिड शेयर" else "Hybrid Share",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "$localDeviceName • IP: $localIpAddress",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent
                    )
                }
            }

            if (selectedIds.isNotEmpty()) {
                LiquidGlassIconOrb(
                    icon = Icons.Default.Share,
                    contentDescription = "System Share",
                    size = 42.dp,
                    iconSize = 20.dp,
                    tintColor = secondary,
                    glowColor = secondary,
                    testTag = "share_system_btn",
                    onClick = {
                        val chosen = allMedia.filter { it.id in selectedIds }
                        WifiDirectTransferEngine.shareRealFilesViaSystem(context, chosen)
                    }
                )
            }
        }

        when (stage) {
            // =====================================================================
            // 1. HOME SCREEN: ONLY SEND & RECEIVE
            // =====================================================================
            HybridShareStage.HOME_CHOICE -> {
                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // SEND CARD
                    LiquidGlassBox(
                        modifier = Modifier
                            .weight(1f)
                            .height(215.dp)
                            .testTag("share_send_big_btn"),
                        cornerRadius = 28.dp,
                        tintColor = accent,
                        isActive = true,
                        onClick = { stage = HybridShareStage.SEND_MEDIA_PICKER }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(78.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(accent, accent.copy(alpha = 0.45f))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = "Send",
                                    tint = Color.Black,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = if (isHindi) "भेजें (Send)" else "Send",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "${videos.size} $videosLabel • ${audios.size} $musicLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.72f)
                            )
                        }
                    }

                    // RECEIVE CARD
                    LiquidGlassBox(
                        modifier = Modifier
                            .weight(1f)
                            .height(215.dp)
                            .testTag("share_receive_big_btn"),
                        cornerRadius = 28.dp,
                        tintColor = secondary,
                        isActive = true,
                        onClick = { stage = HybridShareStage.RECEIVE_LIVE_QR }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(78.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(secondary, secondary.copy(alpha = 0.45f))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = "Receive",
                                    tint = Color.Black,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = if (isHindi) "प्राप्त करें (Receive)" else "Receive",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Live QR • Auto Radar",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.72f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Device Identity Box
                LiquidGlassBox(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    tintColor = accent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = localDeviceName,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isHindi) {
                                    "कनेक्ट होने के बाद ही हाई-स्पीड ट्रांसफर शुरू होता है"
                                } else {
                                    "Real P2P Handshake • Transfers only after Receiver connects"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.68f)
                            )
                        }
                    }
                }
            }

            // =====================================================================
            // 2. SEND STEP 1: PICK VIDEOS & SONGS
            // =====================================================================
            HybridShareStage.SEND_MEDIA_PICKER -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LiquidGlassBox(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        cornerRadius = 22.dp,
                        tintColor = accent,
                        isActive = mediaPickerTab == 0,
                        onClick = { mediaPickerTab = 0 }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = if (mediaPickerTab == 0) accent else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$videosLabel (${videos.size})",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    LiquidGlassBox(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        cornerRadius = 22.dp,
                        tintColor = secondary,
                        isActive = mediaPickerTab == 1,
                        onClick = { mediaPickerTab = 1 }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = if (mediaPickerTab == 1) secondary else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$musicLabel (${audios.size})",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val activeList = if (mediaPickerTab == 0) videos else audios

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(activeList, key = { it.id }) { item ->
                        val isSelected = item.id in selectedIds
                        LiquidGlassBox(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 16.dp,
                            tintColor = if (isSelected) accent else Color.White.copy(alpha = 0.2f),
                            isActive = isSelected,
                            onClick = {
                                if (isSelected) selectedIds.remove(item.id)
                                else selectedIds.add(item.id)
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GuaranteedAlbumArt(
                                    mediaPath = item.path,
                                    title = item.cleanTitle,
                                    isVideo = item.isVideo,
                                    modifier = Modifier.size(50.dp),
                                    cornerRadius = 12.dp,
                                    accentColor = if (item.isVideo) accent else secondary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.cleanTitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${item.formattedDuration} • ${item.formattedSize}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.65f)
                                    )
                                }
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            if (item.id !in selectedIds) selectedIds.add(item.id)
                                        } else {
                                            selectedIds.remove(item.id)
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = accent,
                                        checkmarkColor = Color.Black
                                    )
                                )
                            }
                        }
                    }
                }

                // Bottom Send Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (selectedIds.isNotEmpty()) {
                                stage = HybridShareStage.SEND_CONNECT_RADAR
                            } else {
                                Toast.makeText(context, "Select at least 1 video or song", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = selectedIds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("proceed_send_radar_btn")
                    ) {
                        Icon(Icons.Default.WifiTethering, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${if (isHindi) "भेजें" else "Send"} (${selectedIds.size})",
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    if (selectedIds.isNotEmpty()) {
                        Button(
                            onClick = {
                                val chosen = allMedia.filter { it.id in selectedIds }
                                WifiDirectTransferEngine.shareRealFilesViaSystem(context, chosen)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.12f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share to Apps")
                        }
                    }
                }
            }

            // =====================================================================
            // 3. SEND STEP 2: CONNECT TO REAL RECEIVER (PAIRING CODE / QR / RADAR)
            // =====================================================================
            HybridShareStage.SEND_CONNECT_RADAR -> {
                LiquidGlassBox(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    tintColor = accent,
                    isActive = showQrScannerInput,
                    onClick = { showQrScannerInput = !showQrScannerInput }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(accent.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = "Connect to Receiver",
                                    tint = accent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isHindi) "रिसीवर फ़ोन से कनेक्ट करें" else "Connect to Receiver Phone",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = if (isHindi) {
                                        "रडार पर दिख रहे रिसीवर फ़ोन को टैप करें या उसका कोड/IP डालें"
                                    } else {
                                        "Tap a discovered Receiver below or enter Receiver's Pairing Code / IP"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.72f)
                                )
                            }
                        }

                        if (showQrScannerInput) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = scannedCodeInput,
                                onValueChange = {
                                    scannedCodeInput = it
                                    connectionErrorText = null
                                },
                                singleLine = true,
                                label = {
                                    Text(
                                        if (isHindi) "रिसीवर पेयरिंग कोड (जैसे 104) या IP पता" else "Receiver Pairing Code (e.g. 104) or IP Address"
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = accent,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (connectionErrorText != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = connectionErrorText!!,
                                    color = Color(0xFFFF5252),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val peer = WifiDirectTransferEngine.parsePairingInput(scannedCodeInput, localIpAddress)
                                    if (peer == null) {
                                        connectionErrorText = if (isHindi) {
                                            "कृपया सही पेयरिंग कोड या रिसीवर IP दर्ज करें"
                                        } else {
                                            "Enter a valid Receiver Pairing Code or IP address"
                                        }
                                    } else {
                                        connectAndSendToRealPeer(peer)
                                    }
                                },
                                enabled = !isConnectingHandshake,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accent,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (isConnectingHandshake) {
                                        if (isHindi) "कनेक्ट हो रहा है..." else "Connecting Handshake..."
                                    } else {
                                        if (isHindi) "कनेक्ट करें और भेजें" else "Connect & Send Now"
                                    },
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Radar showing ONLY real discovered Receiver phones on the local network
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    DeviceNameRadarView(
                        discoveredPeers = discoveredPeers,
                        isHindi = isHindi,
                        accentColor = accent,
                        onPeerClick = { peer ->
                            connectAndSendToRealPeer(peer)
                        }
                    )
                }
            }

            // =====================================================================
            // 4. RECEIVE: LIVE QR CODE AT TOP + PAIRING CODE + RADAR WAITING FOR SENDER
            // =====================================================================
            HybridShareStage.RECEIVE_LIVE_QR -> {
                LiquidGlassBox(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 24.dp,
                    tintColor = secondary,
                    isActive = true
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = null,
                                tint = secondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isHindi) "लाइव रिसीव QR और पेयरिंग कोड" else "Live Receive QR & Pairing Code",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .size(176.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White)
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LiveQrMatrixCanvas(
                                matrix = qrMatrix,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = localDeviceName,
                            style = MaterialTheme.typography.titleSmall,
                            color = secondary,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isHindi) {
                                "पेयरिंग कोड: $localPairingCode  •  IP: $localIpAddress"
                            } else {
                                "Pairing Code: $localPairingCode  •  IP: $localIpAddress"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Radar below waiting for real Sender connection
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    DeviceNameRadarView(
                        discoveredPeers = emptyList(),
                        isHindi = isHindi,
                        waitingForSenderMode = true,
                        accentColor = secondary,
                        onPeerClick = {}
                    )
                }
            }

            // =====================================================================
            // 5. CONNECTED INTERFACE: SPEED AT TOP + FILE PROGRESS LIST BELOW
            // =====================================================================
            HybridShareStage.CONNECTED_TRANSFER -> {
                LiquidGlassBox(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 24.dp,
                    tintColor = Color(0xFF00E676),
                    isActive = true
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isHindi) {
                                        "कनेक्टेड • $connectedDeviceName"
                                    } else {
                                        "Connected Successfully • $connectedDeviceName"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color(0xFF00E676),
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            val completedCount = transferItems.count { it.progress >= 1f }
                            Text(
                                text = "$completedCount / ${transferItems.size} Files Completed",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.78f)
                            )
                        }

                        // Live Speed Badge at top right
                        Surface(
                            color = Color(0xFF00E676).copy(alpha = 0.16f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (currentSpeedMbps > 0f)
                                        "${String.format(Locale.US, "%.1f", currentSpeedMbps)} MB/s"
                                    else "Done",
                                    color = Color(0xFF00E676),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = if (isHindi) "फ़ाइल ट्रांसफर सूची" else "Transfer Progress List",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(transferItems, key = { it.id }) { entry ->
                        LiquidGlassBox(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 18.dp,
                            tintColor = if (entry.progress >= 1f) Color(0xFF00E676) else accent,
                            isActive = entry.progress >= 1f
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (entry.isVideo) Icons.Default.Videocam else Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = if (entry.progress >= 1f) Color(0xFF00E676) else accent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${entry.sizeText} • ${entry.statusText}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (entry.progress >= 1f) Color(0xFF00E676) else Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                    Text(
                                        text = "${(entry.progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (entry.progress >= 1f) Color(0xFF00E676) else accent,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { entry.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape),
                                    color = if (entry.progress >= 1f) Color(0xFF00E676) else accent,
                                    trackColor = Color.White.copy(alpha = 0.12f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveQrMatrixCanvas(
    matrix: Array<BooleanArray>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val modules = matrix.size.coerceAtLeast(1)
        val cellSize = size.width / modules
        for (r in 0 until modules) {
            for (c in 0 until modules) {
                if (matrix[r][c]) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(c * cellSize, r * cellSize),
                        size = Size(cellSize + 0.6f, cellSize + 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceNameRadarView(
    discoveredPeers: List<DiscoveredPeerDevice>,
    isHindi: Boolean,
    waitingForSenderMode: Boolean = false,
    accentColor: Color,
    onPeerClick: (DiscoveredPeerDevice) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "share_radar_sweep")
    val radarAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(220.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2f

            listOf(0.35f, 0.68f, 1.0f).forEach { ratio ->
                drawCircle(
                    color = accentColor.copy(alpha = 0.25f),
                    radius = maxRadius * ratio,
                    center = center,
                    style = Stroke(width = 2f)
                )
            }

            val rad = Math.toRadians(radarAngle.toDouble())
            val sweepEnd = Offset(
                x = center.x + (maxRadius * cos(rad)).toFloat(),
                y = center.y + (maxRadius * sin(rad)).toFloat()
            )
            drawLine(
                color = accentColor.copy(alpha = 0.85f),
                start = center,
                end = sweepEnd,
                strokeWidth = 3f
            )
            drawCircle(
                color = accentColor,
                radius = 12f,
                center = center
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (discoveredPeers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xDD0D1526))
                        .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = if (waitingForSenderMode) {
                            if (isHindi) "सेंडर फ़ोन के कनेक्ट होने की प्रतीक्षा है..." else "Waiting for Sender phone to connect..."
                        } else {
                            if (isHindi) "आस-पास के रिसीवर फ़ोन स्कैन हो रहे हैं..." else "Scanning Wi-Fi / Hotspot for Receiver phone..."
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                discoveredPeers.forEach { peer ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xEE10192B))
                            .border(1.4.dp, accentColor, RoundedCornerShape(50))
                            .clickable { onPeerClick(peer) }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${peer.deviceName} (${peer.ipAddress})",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
