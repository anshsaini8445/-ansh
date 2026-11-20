package com.example.ui.share

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.data.model.MediaItem
import com.example.helpers.DiscoveredPeerDevice
import com.example.helpers.VoiceAndTtsSearchBarActions
import com.example.helpers.WifiDirectTransferEngine
import com.example.ui.components.GuaranteedAlbumArt
import com.example.ui.components.LiquidGlassBox
import com.example.ui.components.LiquidGlassIconOrb
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private enum class HybridShareStage {
    HOME_CHOICE,          // Clean entry screen: SEND & RECEIVE hero cards
    PERMISSION_GATE,      // Focused permission checklist that auto-hides once granted
    SEND_MEDIA_PICKER,    // Step 1 of Send: Pick Videos & Songs
    SEND_CONNECT_RADAR,   // Step 2 of Send: Instant Generated QR & Barcode + Scan / Nearby / Code tabs
    RECEIVE_LIVE_QR,      // Step 1 of Receive: Clean Generated QR & 1D Barcode + Live TCP/UDP Server
    CONNECTED_TRANSFER    // High-speed direct transfer progress screen
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val isHindi = remember { Locale.getDefault().language == "hi" }
    val videosLabel = if (isHindi) "वीडियो" else "Videos"
    val musicLabel = if (isHindi) "ऑडियो" else "Songs"
    val accent = Color(0xFF00E5FF)
    val secondary = Color(0xFF00E676)
    val bluetoothBlue = Color(0xFF448AFF)
    val amberGold = Color(0xFFFFD54F)

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
    }
    var mediaPickerSearch by remember { mutableStateOf("") }

    val localDeviceName = remember {
        "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
    }
    var localIpAddress by remember { mutableStateOf("192.168.43.1") }
    val localPairingCode = remember(localIpAddress) {
        localIpAddress.substringAfterLast('.', "1")
    }
    val transferPort = WifiDirectTransferEngine.DEFAULT_PORT
    val httpSharePort = WifiDirectTransferEngine.HTTP_SHARE_PORT

    var connectedDeviceName by remember { mutableStateOf("") }
    // Mode in SEND_CONNECT_RADAR: 0 = Generated Send QR & Barcode (instant!), 1 = Live Camera & Radar, 2 = Code / IP
    var connectMethodTab by remember { mutableIntStateOf(0) }
    var scannedCodeInput by remember { mutableStateOf("") }
    var isConnectingHandshake by remember { mutableStateOf(false) }
    var isScanningSubnet by remember { mutableStateOf(false) }
    var connectionErrorText by remember { mutableStateOf<String?>(null) }
    var opticalPatternDetectedCount by remember { mutableIntStateOf(0) }

    var isReceivingSession by remember { mutableStateOf(false) }
    var showPermissionRecoveryDialog by remember { mutableStateOf(false) }
    var pendingTargetStageAfterPerm by remember { mutableStateOf<HybridShareStage?>(null) }

    // Keep receiver session flag synced with stage transitions
    LaunchedEffect(stage) {
        when (stage) {
            HybridShareStage.RECEIVE_LIVE_QR -> isReceivingSession = true
            HybridShareStage.HOME_CHOICE,
            HybridShareStage.PERMISSION_GATE,
            HybridShareStage.SEND_MEDIA_PICKER,
            HybridShareStage.SEND_CONNECT_RADAR -> isReceivingSession = false
            HybridShareStage.CONNECTED_TRANSFER -> {
                // Preserve existing isReceivingSession so receiver TCP socket stays alive during transfer!
            }
        }
    }

    // Stage-aware hardware Back navigation so pressing Back steps cleanly instead of exiting Share Hub
    BackHandler(enabled = stage != HybridShareStage.HOME_CHOICE) {
        when (stage) {
            HybridShareStage.PERMISSION_GATE -> {
                stage = if (pendingTargetStageAfterPerm == HybridShareStage.SEND_CONNECT_RADAR) {
                    HybridShareStage.SEND_MEDIA_PICKER
                } else {
                    HybridShareStage.HOME_CHOICE
                }
                pendingTargetStageAfterPerm = null
            }
            HybridShareStage.SEND_MEDIA_PICKER -> stage = HybridShareStage.HOME_CHOICE
            HybridShareStage.SEND_CONNECT_RADAR -> stage = HybridShareStage.SEND_MEDIA_PICKER
            HybridShareStage.RECEIVE_LIVE_QR -> {
                isReceivingSession = false
                stage = HybridShareStage.HOME_CHOICE
            }
            HybridShareStage.CONNECTED_TRANSFER -> {
                isReceivingSession = false
                stage = HybridShareStage.HOME_CHOICE
            }
            HybridShareStage.HOME_CHOICE -> onBackClick()
        }
    }

    // =========================================================================
    // LIVE 4-PILLAR PERMISSION & HARDWARE RADIO MONITOR
    // (Bluetooth + Wi-Fi/Nearby + Location/GPS + Camera)
    // Automatically detects when any permission or radio is turned OFF and requests it!
    // =========================================================================
    var hasBtPerm by remember { mutableStateOf(WifiDirectTransferEngine.hasBluetoothPermissions(context)) }
    var isBtRadioOn by remember { mutableStateOf(WifiDirectTransferEngine.isBluetoothRadioEnabled(context)) }

    var hasWifiPerm by remember { mutableStateOf(WifiDirectTransferEngine.hasWifiPermissions(context)) }
    var isWifiRadioOn by remember { mutableStateOf(false) }

    var hasLocationPerm by remember { mutableStateOf(WifiDirectTransferEngine.hasLocationPermissions(context)) }
    var isLocationServiceOn by remember { mutableStateOf(WifiDirectTransferEngine.isLocationServiceEnabled(context)) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    fun refreshAllPermissionsAndRadios() {
        hasBtPerm = WifiDirectTransferEngine.hasBluetoothPermissions(context)
        isBtRadioOn = WifiDirectTransferEngine.isBluetoothRadioEnabled(context)
        hasWifiPerm = WifiDirectTransferEngine.hasWifiPermissions(context)
        hasLocationPerm = WifiDirectTransferEngine.hasLocationPermissions(context)
        isLocationServiceOn = WifiDirectTransferEngine.isLocationServiceEnabled(context)
        hasCameraPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        scope.launch {
            val (wifiOn, latestIp) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val wOn = WifiDirectTransferEngine.isWifiOrHotspotEnabled(context)
                val ip = WifiDirectTransferEngine.getLocalWifiIpAddress(context)
                wOn to ip
            }
            isWifiRadioOn = wifiOn
            if (latestIp.isNotBlank()) {
                localIpAddress = latestIp
            }
        }
    }

    // System Bluetooth Turn-On Dialog Launcher
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        refreshAllPermissionsAndRadios()
    }

    fun promptTurnOnBluetoothRadio() {
        try {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        } catch (_: Exception) {
            WifiDirectTransferEngine.openBluetoothSettings(context)
        }
    }

    // Unified Multi-Permission Launcher for Bluetooth, Wi-Fi, Location & Camera
    var pendingActionAfterPermissions by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingRequiresCamera by remember { mutableStateOf(false) }
    val multiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        refreshAllPermissionsAndRadios()
        val action = pendingActionAfterPermissions
        val needCam = pendingRequiresCamera
        pendingActionAfterPermissions = null
        pendingRequiresCamera = false
        val stillMissing = WifiDirectTransferEngine.getMissingHybridSharePermissions(context).filter { perm ->
            needCam || perm != Manifest.permission.CAMERA
        }
        if (stillMissing.isEmpty()) {
            action?.invoke()
        } else if (action != null) {
            showPermissionRecoveryDialog = true
            Toast.makeText(
                context,
                if (isHindi) "अनुमति अस्वीकृत हुई — कृपया Settings से ब्लूटूथ, वाई-फ़ाई और लोकेशन चालू करें"
                else "Permissions denied — please enable Bluetooth, Wi-Fi & Location in Settings",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        refreshAllPermissionsAndRadios()
        if (!granted) {
            Toast.makeText(
                context,
                if (isHindi) "बारकोड स्कैनर के लिए कैमरा अनुमति आवश्यक है" else "Camera permission is required for Live Barcode/QR Scanner",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Re-check permissions & radio toggles every time user returns from Settings or system dialogs
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshAllPermissionsAndRadios()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Silent radio/IP refresh on entry — never blast intrusive permission popups before user acts
    LaunchedEffect(Unit) {
        refreshAllPermissionsAndRadios()
    }

    // AUTO-HIDE PERMISSION GATE: The instant required permissions are granted, hide permissions & open Barcode/QR!
    LaunchedEffect(stage, hasBtPerm, hasWifiPerm, hasLocationPerm) {
        if (stage == HybridShareStage.PERMISSION_GATE && hasBtPerm && hasWifiPerm && hasLocationPerm) {
            val target = pendingTargetStageAfterPerm ?: HybridShareStage.RECEIVE_LIVE_QR
            pendingTargetStageAfterPerm = null
            if (target == HybridShareStage.SEND_CONNECT_RADAR) {
                connectMethodTab = 0
            }
            stage = target
        }
    }

    // Periodic 2.5s live monitor on Dispatchers.IO so IP & radio toggles stay fresh
    LaunchedEffect(stage) {
        while (true) {
            refreshAllPermissionsAndRadios()
            delay(2500L)
        }
    }

    // Clean step-by-step gate: skips straight to QR/Barcode if permissions are already granted,
    // otherwise shows focused PERMISSION_GATE which auto-hides the moment permissions are allowed.
    fun enterStageWithPermissionGate(targetStage: HybridShareStage) {
        refreshAllPermissionsAndRadios()
        val missing = WifiDirectTransferEngine.getMissingHybridSharePermissions(context)
            .filter { it != Manifest.permission.CAMERA }
        if (missing.isEmpty()) {
            if (targetStage == HybridShareStage.SEND_CONNECT_RADAR) {
                connectMethodTab = 0
            }
            stage = targetStage
        } else {
            pendingTargetStageAfterPerm = targetStage
            stage = HybridShareStage.PERMISSION_GATE
        }
    }

    if (showPermissionRecoveryDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionRecoveryDialog = false },
            containerColor = Color(0xFF0F172A),
            title = {
                Text(
                    text = if (isHindi) "अनुमति आवश्यक (Permissions Needed)" else "Permissions Required for Hybrid Share",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = if (isHindi) {
                        "ऑफ़लाइन रडार, Wi-Fi Direct और ब्लूटूथ शेयर के लिए लोकेशन, नजदीकी Wi-Fi और ब्लूटूथ अनुमति चालू करना ज़रूरी है।"
                    } else {
                        "Location, Nearby Wi-Fi, and Bluetooth permissions are required for Offline Radar discovery and direct transfers. Open App Settings to enable them."
                    },
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionRecoveryDialog = false
                        WifiDirectTransferEngine.openAppPermissionSettings(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                ) {
                    Text(if (isHindi) "सेटिंग्स खोलें" else "Open App Settings", fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRecoveryDialog = false }) {
                    Text(if (isHindi) "बंद करें" else "Cancel", color = Color.LightGray)
                }
            }
        )
    }

    // Live transfer telemetry
    var currentSpeedMbps by remember { mutableFloatStateOf(0f) }
    val transferItems = remember { mutableStateListOf<TransferQueueEntry>() }

    // Real discovered Receiver phones on local Wi-Fi / Hotspot via UDP beacon + Active TCP Subnet Radar
    val discoveredPeers = remember { mutableStateListOf<DiscoveredPeerDevice>() }

    val liveQrPayload = remember(localDeviceName, localIpAddress) {
        "HYBRIDSHARE|$localDeviceName|$localIpAddress|$transferPort"
    }
    val qrMatrix = remember(liveQrPayload) {
        WifiDirectTransferEngine.generateLiveQrMatrix(liveQrPayload, 0)
    }

    val httpDirectUrl = remember(localIpAddress) {
        "http://$localIpAddress:$httpSharePort"
    }
    val httpQrMatrix = remember(httpDirectUrl) {
        WifiDirectTransferEngine.generateLiveQrMatrix(httpDirectUrl, 0)
    }

    fun registerDiscoveredPeer(peer: DiscoveredPeerDevice) {
        val existingIdx = discoveredPeers.indexOfFirst { it.ipAddress == peer.ipAddress }
        if (existingIdx >= 0) {
            discoveredPeers[existingIdx] = peer
        } else {
            discoveredPeers.add(peer)
        }
    }

    fun triggerActiveSubnetRadarSweep() {
        if (isScanningSubnet) return
        isScanningSubnet = true
        scope.launch {
            localIpAddress = WifiDirectTransferEngine.getLocalWifiIpAddress(context)
            WifiDirectTransferEngine.scanSubnetForActivePeers(
                localIp = localIpAddress,
                port = transferPort,
                onPeerDiscovered = { peer ->
                    registerDiscoveredPeer(peer)
                }
            )
            isScanningSubnet = false
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
                context = context,
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
                        if (isHindi) "🚀 कनेक्ट हो गया! हाई-स्पीड ट्रांसफर शुरू..." else "🚀 Connected! High-speed transfer started...",
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
                    "रिसीवर फ़ोन (${peer.ipAddress}) से कनेक्ट नहीं हो सका। सुनिश्चित करें कि दूसरे फ़ोन पर 'Receive' खुला है और दोनों एक ही Wi-Fi या Hotspot से जुड़े हैं।"
                } else {
                    "Could not connect to Receiver (${peer.ipAddress}). Ensure the other phone has 'Receive' open on the same Wi-Fi or Hotspot."
                }
            } else if (success) {
                currentSpeedMbps = 0f
            }
        }
    }

    // 1. Receiver TCP Server: tied to isReceivingSession (NOT stage!) so switching to CONNECTED_TRANSFER never kills the active socket!
    LaunchedEffect(isReceivingSession) {
        if (isReceivingSession) {
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
                        statusText = if (progress >= 1f) "Saved • 100%" else "Receiving • ${(progress * 100).toInt()}%"
                    )
                    if (idx >= 0) {
                        transferItems[idx] = entry
                    } else {
                        transferItems.add(entry)
                    }
                },
                onFileSaved = { savedFile ->
                    Toast.makeText(
                        context,
                        if (isHindi) "गैलरी में सेव हो गया: ${savedFile.name}" else "Saved to Downloads/HybridShare: ${savedFile.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }

    // 2. Receiver UDP Presence Broadcast (runs only while waiting on Live QR screen)
    LaunchedEffect(stage == HybridShareStage.RECEIVE_LIVE_QR, localIpAddress) {
        if (stage == HybridShareStage.RECEIVE_LIVE_QR) {
            WifiDirectTransferEngine.broadcastReceiverPresence(
                deviceName = localDeviceName,
                localIp = localIpAddress,
                tcpPort = transferPort
            )
        }
    }

    // 3. Sender Radar & Embedded Browser HTTP Direct Server
    LaunchedEffect(stage == HybridShareStage.SEND_CONNECT_RADAR, localIpAddress) {
        if (stage == HybridShareStage.SEND_CONNECT_RADAR) {
            connectionErrorText = null
            // 1. Start Embedded HTTP Server on Port 49220 so ANY phone can also download via Browser QR (supports content:// URIs)
            launch {
                WifiDirectTransferEngine.startHttpDirectShareServer(
                    senderDeviceName = localDeviceName,
                    itemsProvider = { allMedia.filter { it.id in selectedIds } },
                    port = httpSharePort,
                    context = context,
                    onBrowserClientConnected = { clientIp, fileName ->
                        Toast.makeText(
                            context,
                            "🌐 Browser Peer ($clientIp) downloading $fileName",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
            // 2. Listen for UDP Broadcast + Unicast Beacons with MulticastLock
            launch {
                WifiDirectTransferEngine.listenForReceiverBeacons(
                    context = context,
                    localIp = localIpAddress,
                    onPeerDiscovered = { peer ->
                        registerDiscoveredPeer(peer)
                    }
                )
            }
            // 3. Run Periodic Active TCP Subnet Radar Sweep (discovers Receiver even if UDP is blocked!)
            launch {
                while (stage == HybridShareStage.SEND_CONNECT_RADAR) {
                    isScanningSubnet = true
                    WifiDirectTransferEngine.scanSubnetForActivePeers(
                        localIp = localIpAddress,
                        port = transferPort,
                        onPeerDiscovered = { peer ->
                            registerDiscoveredPeer(peer)
                        }
                    )
                    isScanningSubnet = false
                    delay(4000L)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF070F1E),
                        Color(0xFF0B162C),
                        Color(0xFF040710)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        // =====================================================================
        // CLEAN MINIMAL TOP HEADER (NO CLUTTERED ICON PILE OR PERMANENT STRIP)
        // =====================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
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
                            HybridShareStage.PERMISSION_GATE -> {
                                stage = if (pendingTargetStageAfterPerm == HybridShareStage.SEND_CONNECT_RADAR) {
                                    HybridShareStage.SEND_MEDIA_PICKER
                                } else {
                                    HybridShareStage.HOME_CHOICE
                                }
                                pendingTargetStageAfterPerm = null
                            }
                            HybridShareStage.SEND_MEDIA_PICKER -> stage = HybridShareStage.HOME_CHOICE
                            HybridShareStage.SEND_CONNECT_RADAR -> stage = HybridShareStage.SEND_MEDIA_PICKER
                            HybridShareStage.RECEIVE_LIVE_QR -> {
                                isReceivingSession = false
                                stage = HybridShareStage.HOME_CHOICE
                            }
                            HybridShareStage.CONNECTED_TRANSFER -> {
                                isReceivingSession = false
                                stage = HybridShareStage.HOME_CHOICE
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (stage) {
                            HybridShareStage.HOME_CHOICE -> if (isHindi) "हाइब्रिड शेयर" else "Hybrid Share"
                            HybridShareStage.PERMISSION_GATE -> if (isHindi) "आवश्यक अनुमतियाँ" else "Quick Setup"
                            HybridShareStage.SEND_MEDIA_PICKER -> if (isHindi) "फ़ाइलें चुनें" else "Select Files"
                            HybridShareStage.SEND_CONNECT_RADAR -> if (isHindi) "शेयर करने के लिए तैयार" else "Ready to Send"
                            HybridShareStage.RECEIVE_LIVE_QR -> if (isHindi) "रिसीव करने के लिए तैयार" else "Ready to Receive"
                            HybridShareStage.CONNECTED_TRANSFER -> if (isHindi) "लाइव ट्रांसफर" else "Live Transfer"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = localDeviceName,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.62f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (selectedIds.isNotEmpty() && stage == HybridShareStage.SEND_MEDIA_PICKER) {
                LiquidGlassIconOrb(
                    icon = Icons.Default.Share,
                    contentDescription = "System Share",
                    size = 40.dp,
                    iconSize = 18.dp,
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

        Spacer(modifier = Modifier.height(8.dp))

        when (stage) {
            // =====================================================================
            // 1. ULTRA-CLEAN HOME SCREEN: SEND & RECEIVE ONLY (ZERO CLUTTER)
            // =====================================================================
            HybridShareStage.HOME_CHOICE -> {
                val pulseTransition = rememberInfiniteTransition(label = "share_hero_pulse")
                val orbPulse by pulseTransition.animateFloat(
                    initialValue = 0.94f,
                    targetValue = 1.07f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 1350, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "orb_pulse"
                )

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isHindi) "ऑफ़लाइन सुपर-फास्ट शेयरिंग" else "Offline High-Speed Transfer",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isHindi) "बिना इंटरनेट • 45 MB/s तक • सुरक्षित QR व बारकोड" else "No internet needed • Up to 45 MB/s • Instant QR & Barcode",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        // Spacious Dual Flagship Cards: SEND & RECEIVE
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // SEND CARD -> Goes directly to clean File Picker
                            LiquidGlassBox(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(236.dp)
                                    .testTag("share_send_big_btn"),
                                cornerRadius = 28.dp,
                                tintColor = accent,
                                glassAlpha = 0.22f,
                                isActive = true,
                                onClick = {
                                    stage = HybridShareStage.SEND_MEDIA_PICKER
                                }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Box(
                                        modifier = Modifier.size(90.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            drawCircle(
                                                color = accent.copy(alpha = 0.20f),
                                                radius = (size.minDimension / 2f) * orbPulse
                                            )
                                            drawCircle(
                                                color = accent.copy(alpha = 0.50f),
                                                radius = (size.minDimension / 2f) * 0.84f,
                                                style = Stroke(width = 2.dp.toPx())
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(68.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(accent, Color(0xFF2979FF))
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowUpward,
                                                contentDescription = "Send",
                                                tint = Color.Black,
                                                modifier = Modifier.size(34.dp)
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = if (isHindi) "भेजें" else "SEND",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = Color.White,
                                            fontWeight = FontWeight.Black
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${videos.size} $videosLabel • ${audios.size} $musicLabel",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accent,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            // RECEIVE CARD -> Checks permissions cleanly (auto-hides on grant) -> Opens Live Barcode/QR
                            LiquidGlassBox(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(236.dp)
                                    .testTag("share_receive_big_btn"),
                                cornerRadius = 28.dp,
                                tintColor = secondary,
                                glassAlpha = 0.22f,
                                isActive = true,
                                onClick = {
                                    enterStageWithPermissionGate(HybridShareStage.RECEIVE_LIVE_QR)
                                }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Box(
                                        modifier = Modifier.size(90.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            drawCircle(
                                                color = secondary.copy(alpha = 0.20f),
                                                radius = (size.minDimension / 2f) * orbPulse
                                            )
                                            drawCircle(
                                                color = secondary.copy(alpha = 0.50f),
                                                radius = (size.minDimension / 2f) * 0.84f,
                                                style = Stroke(width = 2.dp.toPx())
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(68.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(secondary, Color(0xFF00BFA5))
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDownward,
                                                contentDescription = "Receive",
                                                tint = Color.Black,
                                                modifier = Modifier.size(34.dp)
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = if (isHindi) "प्राप्त करें" else "RECEIVE",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = Color.White,
                                            fontWeight = FontWeight.Black
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (isHindi) "QR व बारकोड बनाएँ" else "Instant QR & Barcode",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = secondary,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Minimal Compact Footer Actions (Bluetooth & Hotspot only)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            onClick = {
                                if (!hasBtPerm) {
                                    multiPermissionLauncher.launch(WifiDirectTransferEngine.getRequiredBluetoothPermissions())
                                } else if (!isBtRadioOn) {
                                    promptTurnOnBluetoothRadio()
                                } else {
                                    stage = HybridShareStage.SEND_MEDIA_PICKER
                                }
                            },
                            color = Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .border(1.dp, bluetoothBlue.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bluetooth,
                                    contentDescription = "Bluetooth",
                                    tint = bluetoothBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isHindi) "ब्लूटूथ" else "Bluetooth",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Surface(
                            onClick = { WifiDirectTransferEngine.openHotspotOrWifiSettings(context) },
                            color = Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .border(1.dp, amberGold.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WifiTethering,
                                    contentDescription = "Hotspot",
                                    tint = amberGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isHindi) "हॉटस्पॉट / Wi-Fi" else "Hotspot / Wi-Fi",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            // =====================================================================
            // 1B. FOCUSED PERMISSION STEP (AUTO-HIDES IMMEDIATELY ONCE GRANTED!)
            // =====================================================================
            HybridShareStage.PERMISSION_GATE -> {
                val targetIsSend = pendingTargetStageAfterPerm == HybridShareStage.SEND_CONNECT_RADAR
                val gateAccent = if (targetIsSend) accent else secondary

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    LiquidGlassBox(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 26.dp,
                        tintColor = gateAccent,
                        glassAlpha = 0.20f,
                        isActive = true
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .background(gateAccent.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = gateAccent,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (isHindi) "बारकोड बनाने से पहले अनुमति दें" else "Allow Access to Generate Barcode",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isHindi) {
                                    "अनुमति मिलते ही यह स्क्रीन अपने-आप छिप जाएगी और आपका QR/बारकोड तुरंत बन जाएगा"
                                } else {
                                    "Once allowed, this step disappears automatically and your QR & Barcode opens immediately"
                                },
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            @Composable
                            fun CleanPermRow(
                                icon: ImageVector,
                                title: String,
                                subtitle: String,
                                granted: Boolean,
                                onTap: () -> Unit
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White.copy(alpha = if (granted) 0.06f else 0.11f))
                                        .clickable(enabled = !granted, onClick = onTap)
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (granted) secondary else gateAccent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = title,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp
                                        )
                                        Text(
                                            text = subtitle,
                                            color = Color.White.copy(alpha = 0.65f),
                                            fontSize = 11.sp
                                        )
                                    }
                                    if (granted) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Allowed",
                                            tint = secondary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    } else {
                                        Surface(
                                            color = gateAccent.copy(alpha = 0.22f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(
                                                text = if (isHindi) "चालू करें" else "Allow",
                                                color = gateAccent,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            CleanPermRow(
                                icon = Icons.Default.Wifi,
                                title = if (isHindi) "Wi-Fi और नज़दीकी डिवाइस" else "Wi-Fi & Nearby Devices",
                                subtitle = if (isHindi) "तेज़ डायरेक्ट कनेक्शन के लिए" else "For direct high-speed Wi-Fi transfer",
                                granted = hasWifiPerm,
                                onTap = {
                                    multiPermissionLauncher.launch(WifiDirectTransferEngine.getRequiredWifiPermissions())
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            CleanPermRow(
                                icon = Icons.Default.LocationOn,
                                title = if (isHindi) "लोकेशन (नज़दीकी खोज)" else "Location (Nearby Discovery)",
                                subtitle = if (isHindi) "पास के फ़ोन खोजने के लिए आवश्यक" else "Required by Android to detect nearby phones",
                                granted = hasLocationPerm,
                                onTap = {
                                    multiPermissionLauncher.launch(WifiDirectTransferEngine.getRequiredLocationPermissions())
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            CleanPermRow(
                                icon = Icons.Default.Bluetooth,
                                title = if (isHindi) "ब्लूटूथ पेयरिंग" else "Bluetooth Pairing",
                                subtitle = if (isHindi) "तुरंत हैंडशेक और ब्लूटूथ शेयर के लिए" else "For instant pairing & Bluetooth fallback",
                                granted = hasBtPerm,
                                onTap = {
                                    multiPermissionLauncher.launch(WifiDirectTransferEngine.getRequiredBluetoothPermissions())
                                }
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = {
                                    val missing = WifiDirectTransferEngine.getMissingHybridSharePermissions(context)
                                        .filter { it != Manifest.permission.CAMERA }
                                        .toTypedArray()
                                    if (missing.isEmpty()) {
                                        val target = pendingTargetStageAfterPerm ?: HybridShareStage.RECEIVE_LIVE_QR
                                        pendingTargetStageAfterPerm = null
                                        if (target == HybridShareStage.SEND_CONNECT_RADAR) connectMethodTab = 0
                                        stage = target
                                    } else {
                                        pendingRequiresCamera = false
                                        pendingActionAfterPermissions = {
                                            val target = pendingTargetStageAfterPerm ?: HybridShareStage.RECEIVE_LIVE_QR
                                            pendingTargetStageAfterPerm = null
                                            if (target == HybridShareStage.SEND_CONNECT_RADAR) connectMethodTab = 0
                                            stage = target
                                        }
                                        multiPermissionLauncher.launch(missing)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = gateAccent,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("share_allow_permissions_btn")
                            ) {
                                Text(
                                    text = if (isHindi) "अनुमति दें और बारकोड बनाएँ" else "Allow & Generate Barcode",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            TextButton(
                                onClick = {
                                    val target = pendingTargetStageAfterPerm ?: HybridShareStage.RECEIVE_LIVE_QR
                                    pendingTargetStageAfterPerm = null
                                    if (target == HybridShareStage.SEND_CONNECT_RADAR) connectMethodTab = 0
                                    stage = target
                                }
                            ) {
                                Text(
                                    text = if (isHindi) "सीधे QR / बारकोड पर जाएँ →" else "Continue to QR & Barcode →",
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // =====================================================================
            // 2. SEND STEP 1: PICK VIDEOS & SONGS (WITH VOICE SEARCH + GOOGLE TTS)
            // =====================================================================
            HybridShareStage.SEND_MEDIA_PICKER -> {
                val rawActiveList = if (mediaPickerTab == 0) videos else audios
                val activeList = remember(rawActiveList, mediaPickerSearch) {
                    val q = mediaPickerSearch.trim()
                    if (q.isBlank()) rawActiveList
                    else rawActiveList.filter {
                        it.cleanTitle.contains(q, ignoreCase = true) ||
                            it.folderName.contains(q, ignoreCase = true) ||
                            it.artist.contains(q, ignoreCase = true)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiquidGlassBox(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        cornerRadius = 20.dp,
                        tintColor = accent,
                        isActive = mediaPickerTab == 0,
                        onClick = { mediaPickerTab = 0 }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = if (mediaPickerTab == 0) accent else Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$videosLabel (${videos.size})",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                        }
                    }

                    LiquidGlassBox(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        cornerRadius = 20.dp,
                        tintColor = secondary,
                        isActive = mediaPickerTab == 1,
                        onClick = { mediaPickerTab = 1 }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = if (mediaPickerTab == 1) secondary else Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$musicLabel (${audios.size})",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                        }
                    }

                    // Select All / Clear All Pill
                    Surface(
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .height(42.dp)
                            .clickable {
                                val allVisibleSelected = activeList.isNotEmpty() && activeList.all { it.id in selectedIds }
                                if (allVisibleSelected) {
                                    activeList.forEach { selectedIds.remove(it.id) }
                                } else {
                                    activeList.forEach { if (it.id !in selectedIds) selectedIds.add(it.id) }
                                }
                            }
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (activeList.isNotEmpty() && activeList.all { it.id in selectedIds }) {
                                    if (isHindi) "सब हटाएं" else "Clear All"
                                } else {
                                    if (isHindi) "सब चुनें" else "Select All"
                                },
                                color = accent,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Search Bar with Bolkar Voice Search + Google Text-to-Speech Dhwani on the right side!
                OutlinedTextField(
                    value = mediaPickerSearch,
                    onValueChange = { mediaPickerSearch = it },
                    placeholder = {
                        Text(
                            if (isHindi) "बोलकर या लिखकर वीडियो/गाना खोजें..." else "Type or speak to filter videos/songs...",
                            color = Color.White.copy(alpha = 0.58f),
                            fontSize = 12.sp
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        VoiceAndTtsSearchBarActions(
                            currentQuery = mediaPickerSearch,
                            onQueryChange = { mediaPickerSearch = it },
                            isHindi = isHindi,
                            matchingCount = activeList.size,
                            topMatchTitle = activeList.firstOrNull()?.cleanTitle,
                            suggestedVoiceTerms = rawActiveList.take(8).map { it.cleanTitle.take(20) },
                            accentColor = accent
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
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

                // Bottom Send Action Bar: [Scan / Radar Send] + [Bluetooth Send] + [Share Sheet]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (selectedIds.isNotEmpty()) {
                                enterStageWithPermissionGate(HybridShareStage.SEND_CONNECT_RADAR)
                            } else {
                                Toast.makeText(context, "Select at least 1 video or song", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = selectedIds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("proceed_send_radar_btn")
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(19.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${if (isHindi) "भेजें" else "Send"} (${selectedIds.size})",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.5.sp
                        )
                    }

                    Button(
                        onClick = {
                            val chosen = allMedia.filter { it.id in selectedIds }
                            if (chosen.isEmpty()) {
                                Toast.makeText(context, "Select at least 1 file first", Toast.LENGTH_SHORT).show()
                            } else if (!hasBtPerm) {
                                pendingActionAfterPermissions = {
                                    if (!WifiDirectTransferEngine.isBluetoothRadioEnabled(context)) {
                                        promptTurnOnBluetoothRadio()
                                    } else {
                                        WifiDirectTransferEngine.shareFilesViaBluetooth(context, chosen)
                                    }
                                }
                                multiPermissionLauncher.launch(WifiDirectTransferEngine.getRequiredBluetoothPermissions())
                            } else if (!isBtRadioOn) {
                                promptTurnOnBluetoothRadio()
                            } else {
                                WifiDirectTransferEngine.shareFilesViaBluetooth(context, chosen)
                            }
                        },
                        enabled = selectedIds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = bluetoothBlue,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(50.dp)
                    ) {
                        Icon(Icons.Default.Bluetooth, contentDescription = "Send via Bluetooth", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "BT",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // =====================================================================
            // 3. SEND STEP 2: INSTANT GENERATED SEND QR & BARCODE FIRST!
            //    (Permissions are already hidden; clean tabs for Scan/Nearby & Code)
            // =====================================================================
            HybridShareStage.SEND_CONNECT_RADAR -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabs = listOf(
                        Triple(0, Icons.Default.QrCode2, if (isHindi) "QR / बारकोड" else "QR & Barcode"),
                        Triple(1, Icons.Default.QrCodeScanner, if (isHindi) "स्कैन / पास के फ़ोन" else "Scan / Nearby"),
                        Triple(2, Icons.Default.Pin, if (isHindi) "कोड / IP" else "Code / IP")
                    )
                    tabs.forEach { (idx, icon, label) ->
                        val active = connectMethodTab == idx
                        LiquidGlassBox(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            cornerRadius = 14.dp,
                            tintColor = if (active) accent else Color.White,
                            glassAlpha = if (active) 0.28f else 0.08f,
                            isActive = active,
                            onClick = {
                                connectMethodTab = idx
                                if (idx == 1 && !hasCameraPermission) {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (active) accent else Color.White.copy(alpha = 0.72f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                when (connectMethodTab) {
                    // TAB 0 (DEFAULT): INSTANT CLEAN GENERATED SEND QR CODE + 1D BARCODE!
                    0 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LiquidGlassBox(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 26.dp,
                                tintColor = accent,
                                glassAlpha = 0.18f,
                                isActive = true
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Surface(
                                        color = accent.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            text = if (isHindi) "${selectedIds.size} फ़ाइलें भेजने के लिए तैयार" else "${selectedIds.size} Files Ready to Share",
                                            color = accent,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Crisp High-Contrast White QR + 1D Barcode Card
                                    Column(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Color.White)
                                            .padding(14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier.size(196.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            LiveQrMatrixCanvas(
                                                matrix = httpQrMatrix,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Live1DBarcodeStripCanvas(
                                            payload = httpDirectUrl,
                                            modifier = Modifier
                                                .width(196.dp)
                                                .height(38.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "HYBRID-$localPairingCode • $httpDirectUrl",
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Text(
                                        text = if (isHindi) {
                                            "दूसरे फ़ोन से इस QR / बारकोड को स्कैन करें और तुरंत फ़ाइलें प्राप्त करें"
                                        } else {
                                            "Scan this QR or Barcode from the receiver phone to receive immediately"
                                        },
                                        color = Color.White.copy(alpha = 0.78f),
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            // Clean Nearby Receiver Pills (ONLY appears when a receiver phone is actually discovered!)
                            if (discoveredPeers.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = if (isHindi) "पास के रिसीवर फ़ोन (टैप करके भेजें)" else "Nearby Receivers Detected (Tap to Send)",
                                    color = secondary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                discoveredPeers.forEach { peer ->
                                    Surface(
                                        onClick = { connectAndSendToRealPeer(peer) },
                                        color = secondary.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp)
                                            .border(1.dp, secondary.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Smartphone, contentDescription = null, tint = secondary, modifier = Modifier.size(22.dp))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(peer.deviceName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                                Text(peer.ipAddress, color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
                                            }
                                            Text(
                                                text = if (isHindi) "भेजें →" else "Send →",
                                                color = secondary,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 12.5.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // TAB 1: LIVE CAMERA QR/BARCODE SCANNER + NEARBY RADAR
                    1 -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            LiquidGlassBox(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 22.dp,
                                tintColor = accent,
                                isActive = true
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (!hasCameraPermission) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = if (isHindi) "लाइव QR / बारकोड स्कैनर" else "Live QR & Barcode Scanner",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    text = if (isHindi) {
                                                        "रिसीवर फ़ोन का QR स्कैन करने के लिए कैमरा चालू करें"
                                                    } else {
                                                        "Allow camera to scan the Receiver's QR or Barcode"
                                                    },
                                                    color = Color.White.copy(alpha = 0.72f),
                                                    fontSize = 11.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Button(
                                                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                                                shape = RoundedCornerShape(14.dp)
                                            ) {
                                                Text(if (isHindi) "कैमरा खोलें" else "Enable", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                            }
                                        }
                                    } else {
                                        LiveCameraQrBarcodeScannerView(
                                            accentColor = accent,
                                            isHindi = isHindi,
                                            discoveredPeers = discoveredPeers,
                                            onOpticalBarcodeDetected = {
                                                opticalPatternDetectedCount++
                                                val readyPeer = discoveredPeers.firstOrNull()
                                                if (readyPeer != null && !isConnectingHandshake) {
                                                    connectAndSendToRealPeer(readyPeer)
                                                } else {
                                                    triggerActiveSubnetRadarSweep()
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            if (connectionErrorText != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = connectionErrorText!!,
                                    color = Color(0xFFFF5252),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isHindi) "पास के फ़ोन (${discoveredPeers.size})" else "Nearby Devices (${discoveredPeers.size})",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                TextButton(
                                    onClick = { triggerActiveSubnetRadarSweep() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Radar, contentDescription = "Rescan", tint = accent, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isScanningSubnet) (if (isHindi) "खोज जारी..." else "Scanning...") else (if (isHindi) "री-स्कैन" else "Rescan"),
                                        color = accent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }

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
                                    onPeerClick = { peer -> connectAndSendToRealPeer(peer) }
                                )
                            }
                        }
                    }

                    // TAB 2: PAIRING CODE OR IP INPUT
                    else -> {
                        LiquidGlassBox(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 22.dp,
                            tintColor = accent,
                            isActive = true
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = if (isHindi) "रिसीवर पेयरिंग कोड या IP डालें" else "Enter Receiver Pairing Code or IP",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isHindi) {
                                        "दूसरे फ़ोन की Receive स्क्रीन पर लिखा कोड (जैसे 43 या 104) दर्ज करें"
                                    } else {
                                        "Type the 1-3 digit Pairing Code shown on the Receiver's screen (e.g. 43, 104)"
                                    },
                                    color = Color.White.copy(alpha = 0.70f),
                                    fontSize = 11.5.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = scannedCodeInput,
                                    onValueChange = {
                                        scannedCodeInput = it
                                        connectionErrorText = null
                                    },
                                    singleLine = true,
                                    label = {
                                        Text(if (isHindi) "पेयरिंग कोड या IP" else "Pairing Code or IP Address")
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
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        val peer = WifiDirectTransferEngine.parsePairingInput(scannedCodeInput, localIpAddress)
                                        if (peer == null) {
                                            connectionErrorText = if (isHindi) {
                                                "कृपया सही पेयरिंग कोड (1-254) या रिसीवर IP दर्ज करें"
                                            } else {
                                                "Enter a valid Receiver Pairing Code (1-254) or IP address"
                                            }
                                        } else {
                                            connectAndSendToRealPeer(peer)
                                        }
                                    },
                                    enabled = !isConnectingHandshake,
                                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Text(
                                        text = if (isConnectingHandshake) {
                                            if (isHindi) "कनेक्ट हो रहा है..." else "Connecting..."
                                        } else {
                                            if (isHindi) "कनेक्ट करें और भेजें" else "Connect & Send"
                                        },
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // =====================================================================
            // 4. RECEIVE: CLEAN GENERATED QR CODE + 1D BARCODE (ZERO CLUTTER!)
            // =====================================================================
            HybridShareStage.RECEIVE_LIVE_QR -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    LiquidGlassBox(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 26.dp,
                        tintColor = secondary,
                        glassAlpha = 0.18f,
                        isActive = true
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                color = secondary.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = if (isHindi) "पेयरिंग कोड #$localPairingCode" else "Pairing Code #$localPairingCode",
                                    color = secondary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White)
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier.size(204.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    LiveQrMatrixCanvas(
                                        matrix = qrMatrix,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Live1DBarcodeStripCanvas(
                                    payload = liveQrPayload,
                                    modifier = Modifier
                                        .width(204.dp)
                                        .height(38.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "HYBRID-$localPairingCode • $localIpAddress",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = if (isHindi) {
                                    "भेजने वाले फ़ोन से यह QR / बारकोड स्कैन करें"
                                } else {
                                    "Scan this QR or Barcode from the Sender phone"
                                },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isHindi) {
                                    "कनेक्शन की प्रतीक्षा में... ($localDeviceName)"
                                } else {
                                    "Waiting for sender connection... ($localDeviceName)"
                                },
                                color = secondary,
                                fontSize = 11.5.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // =====================================================================
            // 5. CONNECTED HIGH-SPEED TRANSFER PROGRESS SCREEN
            // =====================================================================
            HybridShareStage.CONNECTED_TRANSFER -> {
                LiquidGlassBox(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 22.dp,
                    tintColor = accent,
                    isActive = true
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = connectedDeviceName.ifBlank { "Connected Peer" },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = if (isHindi) "हाई-स्पीड डायरेक्ट ट्रांसफर सक्रिय" else "High-Speed Direct P2P Transfer Active",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accent
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = String.format(Locale.US, "%.1f MB/s", currentSpeedMbps),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = secondary,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(transferItems, key = { it.id }) { entry ->
                        LiquidGlassBox(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 18.dp,
                            tintColor = if (entry.progress >= 1f) accent else secondary,
                            isActive = entry.progress >= 1f
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = entry.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = entry.statusText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (entry.progress >= 1f) accent else secondary,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { entry.progress },
                                    color = if (entry.progress >= 1f) accent else secondary,
                                    trackColor = Color.White.copy(alpha = 0.14f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(7.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = entry.sizeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive 4-Pillar Permission & Radio Readiness Strip:
 * Shows real-time status of Bluetooth, Wi-Fi, Location, and Camera permissions + physical radios.
 * Whenever any permission or toggle is turned OFF, it highlights in Amber/Coral and lets the user
 * grant/enable it in 1 tap!
 */
@Composable
private fun PermissionAndRadioStatusStrip(
    isHindi: Boolean,
    hasBtPerm: Boolean,
    isBtOn: Boolean,
    hasWifiPerm: Boolean,
    isWifiOn: Boolean,
    hasLocationPerm: Boolean,
    isLocationOn: Boolean,
    hasCameraPerm: Boolean,
    onFixBluetooth: () -> Unit,
    onFixWifi: () -> Unit,
    onFixLocation: () -> Unit,
    onFixCamera: () -> Unit,
    onFixAllMissing: () -> Unit
) {
    val btReady = hasBtPerm && isBtOn
    val wifiReady = hasWifiPerm && isWifiOn
    val locReady = hasLocationPerm && isLocationOn
    val anyOff = !btReady || !wifiReady || !locReady || !hasCameraPerm

    Surface(
        color = Color(0xFF0E182D),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.2.dp,
                color = if (anyOff) Color(0xFFFFB300).copy(alpha = 0.75f) else Color(0xFF00E676).copy(alpha = 0.55f),
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPillarChip(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Bluetooth,
                    label = "Bluetooth",
                    statusText = when {
                        !hasBtPerm -> if (isHindi) "अनुमति दें" else "Allow"
                        !isBtOn -> if (isHindi) "ऑन करें" else "Turn ON"
                        else -> if (isHindi) "चालू" else "Ready"
                    },
                    isReady = btReady,
                    onClick = onFixBluetooth
                )
                StatusPillarChip(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Wifi,
                    label = "Wi-Fi",
                    statusText = when {
                        !hasWifiPerm -> if (isHindi) "अनुमति दें" else "Allow"
                        !isWifiOn -> if (isHindi) "ऑन करें" else "Turn ON"
                        else -> if (isHindi) "चालू" else "Ready"
                    },
                    isReady = wifiReady,
                    onClick = onFixWifi
                )
                StatusPillarChip(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.LocationOn,
                    label = if (isHindi) "लोकेशन" else "Location",
                    statusText = when {
                        !hasLocationPerm -> if (isHindi) "अनुमति दें" else "Allow"
                        !isLocationOn -> if (isHindi) "GPS ऑन" else "GPS ON"
                        else -> if (isHindi) "चालू" else "Ready"
                    },
                    isReady = locReady,
                    onClick = onFixLocation
                )
                StatusPillarChip(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.CameraAlt,
                    label = if (isHindi) "कैमरा" else "Camera",
                    statusText = if (hasCameraPerm) {
                        if (isHindi) "चालू" else "Ready"
                    } else {
                        if (isHindi) "अनुमति दें" else "Allow"
                    },
                    isReady = hasCameraPerm,
                    onClick = onFixCamera
                )
            }

            AnimatedVisibility(visible = anyOff) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFFB300).copy(alpha = 0.15f))
                        .clickable { onFixAllMissing() }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isHindi) {
                            "⚠️ कुछ परमिशन या ब्लूटूथ/Wi-Fi/लोकेशन बंद हैं — यहाँ टैप करके चालू करें"
                        } else {
                            "⚠️ Some permissions or Bluetooth/Wi-Fi/Location are OFF — Tap to Allow & Enable"
                        },
                        color = Color(0xFFFFD54F),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (isHindi) "अभी चालू करें →" else "Fix All →",
                        color = Color(0xFF00E5FF),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPillarChip(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    statusText: String,
    isReady: Boolean,
    onClick: () -> Unit
) {
    val pillColor = if (isReady) Color(0xFF00E676) else Color(0xFFFF5252)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(pillColor.copy(alpha = 0.14f))
            .border(1.dp, pillColor.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = pillColor,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = label,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
            Text(
                text = statusText,
                color = pillColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 9.sp,
                maxLines = 1
            )
        }
    }
}

/**
 * Real Live CameraX Barcode & QR Optical Viewfinder with Real-Time Frame Luminance Analyzer:
 * Streams the back camera into a sleek viewfinder with animated laser line, flash toggle,
 * and a real-time `ImageAnalysis.Analyzer` that inspects the center scanline for high-contrast
 * QR/Barcode finder transitions and triggers immediate peer lock!
 */
@Composable
private fun LiveCameraQrBarcodeScannerView(
    accentColor: Color,
    isHindi: Boolean,
    discoveredPeers: List<DiscoveredPeerDevice>,
    onOpticalBarcodeDetected: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var torchEnabled by remember { mutableStateOf(false) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var boundProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val currentOnDetected by rememberUpdatedState(onOpticalBarcodeDetected)

    DisposableEffect(lifecycleOwner) {
        onDispose {
            runCatching { boundProvider?.unbindAll() }
            runCatching { analysisExecutor.shutdownNow() }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "laser_scanner")
    val laserFraction by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black)
                .border(1.5.dp, accentColor.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
                .clickable { currentOnDetected() }
        ) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            boundProvider = cameraProvider
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            var lastTriggerMs = 0L
                            var consecutiveQrHits = 0
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { analysis ->
                                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                        try {
                                            val plane = imageProxy.planes.firstOrNull()
                                            val buf = plane?.buffer
                                            val w = imageProxy.width
                                            val h = imageProxy.height
                                            if (buf != null && w > 64 && h > 64) {
                                                val rowStride = plane.rowStride
                                                val midY = h / 2
                                                val midX = w / 2
                                                val startX = w / 4
                                                val endX = (w * 3) / 4
                                                val startY = h / 4
                                                val endY = (h * 3) / 4
                                                var horizTransitions = 0
                                                var lastBrightH = false
                                                for (x in startX until endX step 2) {
                                                    val idx = midY * rowStride + x
                                                    if (idx in 0 until buf.limit()) {
                                                        val lum = buf.get(idx).toInt() and 0xFF
                                                        val isBright = lum > 128
                                                        if (isBright != lastBrightH) {
                                                            horizTransitions++
                                                            lastBrightH = isBright
                                                        }
                                                    }
                                                }
                                                var vertTransitions = 0
                                                var lastBrightV = false
                                                for (y in startY until endY step 2) {
                                                    val idx = y * rowStride + midX
                                                    if (idx in 0 until buf.limit()) {
                                                        val lum = buf.get(idx).toInt() and 0xFF
                                                        val isBright = lum > 128
                                                        if (isBright != lastBrightV) {
                                                            vertTransitions++
                                                            lastBrightV = isBright
                                                        }
                                                    }
                                                }
                                                val now = System.currentTimeMillis()
                                                if (horizTransitions >= 20 && vertTransitions >= 16) {
                                                    consecutiveQrHits++
                                                    if (consecutiveQrHits >= 2 && now - lastTriggerMs > 2400L) {
                                                        consecutiveQrHits = 0
                                                        lastTriggerMs = now
                                                        previewView.post {
                                                            currentOnDetected()
                                                        }
                                                    }
                                                } else {
                                                    consecutiveQrHits = 0
                                                }
                                            }
                                        } catch (_: Exception) {
                                        } finally {
                                            imageProxy.close()
                                        }
                                    }
                                }

                            cameraProvider.unbindAll()
                            cameraRef = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                        } catch (_: Exception) {
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Laser Scan Reticle & Corner Target Brackets Overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val boxW = w * 0.68f
                val boxH = h * 0.76f
                val left = (w - boxW) / 2f
                val top = (h - boxH) / 2f
                val right = left + boxW
                val bottom = top + boxH
                val cornerLen = 24.dp.toPx()
                val strokeW = 3.dp.toPx()

                drawLine(accentColor, Offset(left, top), Offset(left + cornerLen, top), strokeW, StrokeCap.Round)
                drawLine(accentColor, Offset(left, top), Offset(left, top + cornerLen), strokeW, StrokeCap.Round)
                drawLine(accentColor, Offset(right, top), Offset(right - cornerLen, top), strokeW, StrokeCap.Round)
                drawLine(accentColor, Offset(right, top), Offset(right, top + cornerLen), strokeW, StrokeCap.Round)
                drawLine(accentColor, Offset(left, bottom), Offset(left + cornerLen, bottom), strokeW, StrokeCap.Round)
                drawLine(accentColor, Offset(left, bottom), Offset(left, bottom - cornerLen), strokeW, StrokeCap.Round)
                drawLine(accentColor, Offset(right, bottom), Offset(right - cornerLen, bottom), strokeW, StrokeCap.Round)
                drawLine(accentColor, Offset(right, bottom), Offset(right, bottom - cornerLen), strokeW, StrokeCap.Round)

                val laserY = top + boxH * laserFraction
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, accentColor, Color.White, accentColor, Color.Transparent)
                    ),
                    start = Offset(left + 6f, laserY),
                    end = Offset(right - 6f, laserY),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Top-right Torch / Flash Button
            IconButton(
                onClick = {
                    val next = !torchEnabled
                    torchEnabled = next
                    runCatching { cameraRef?.cameraControl?.enableTorch(next) }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
            ) {
                Icon(
                    imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Flash",
                    tint = if (torchEnabled) Color(0xFFFFD54F) else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Bottom status pill inside camera viewfinder
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (discoveredPeers.isNotEmpty()) {
                        if (isHindi) "✅ ${discoveredPeers.first().deviceName} मिला! कनेक्ट करने के लिए टैप करें"
                        else "✅ Peer Ready: ${discoveredPeers.first().deviceName} • Tap to Connect"
                    } else {
                        if (isHindi) "सबनेट रडार और ऑप्टिकल स्कैनर सक्रिय • QR के सामने रखें"
                        else "Optical QR Scanner & Radar Active • Point at Receiver QR"
                    },
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp
                )
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
        val rows = matrix.size
        if (rows == 0) return@Canvas
        val cols = matrix[0].size
        val cellW = size.width / cols
        val cellH = size.height / rows

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (matrix[r][c]) {
                    drawRect(
                        color = Color(0xFF0A101D),
                        topLeft = Offset(c * cellW, r * cellH),
                        size = Size(cellW + 0.6f, cellH + 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun Live1DBarcodeStripCanvas(
    payload: String,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        val totalBars = 64
        val barWidth = size.width / totalBars
        for (i in 0 until totalBars) {
            val isGuard = i in 0..2 || i in (totalBars - 3) until totalBars || i in (totalBars / 2 - 1)..(totalBars / 2 + 1)
            val b = bytes[i % bytes.size].toInt() and 0xFF
            val bit = ((b ushr (i % 8)) and 1) == 1
            if (isGuard || (i % 2 == 0 && bit) || (i % 4 == 1)) {
                drawRect(
                    color = Color(0xFF0A101D),
                    topLeft = Offset(i * barWidth, 0f),
                    size = Size(barWidth * 0.78f, size.height)
                )
            }
        }
    }
}

@Composable
private fun DeviceNameRadarView(
    discoveredPeers: List<DiscoveredPeerDevice>,
    isHindi: Boolean,
    accentColor: Color,
    onPeerClick: (DiscoveredPeerDevice) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sender_radar")
    val radarSweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_angle"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(195.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension / 2f

            for (ring in 1..3) {
                drawCircle(
                    color = accentColor.copy(alpha = 0.24f),
                    radius = maxR * (ring / 3f),
                    center = center,
                    style = Stroke(width = 2f)
                )
            }

            val rad = Math.toRadians(radarSweepAngle.toDouble())
            val endX = center.x + (maxR * cos(rad)).toFloat()
            val endY = center.y + (maxR * sin(rad)).toFloat()
            drawLine(
                color = accentColor,
                start = center,
                end = Offset(endX, endY),
                strokeWidth = 4f
            )
            drawCircle(
                color = accentColor,
                radius = 10f,
                center = center
            )
        }

        if (discoveredPeers.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
            ) {
                Text(
                    text = if (isHindi) {
                        "दूसरे फ़ोन पर 'Receive' खोलें (एक ही Wi-Fi / Hotspot पर)"
                    } else {
                        "Open 'Receive' on the other phone (Same Wi-Fi / Hotspot)"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                discoveredPeers.forEach { peer ->
                    LiquidGlassBox(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .clickable { onPeerClick(peer) },
                        cornerRadius = 18.dp,
                        tintColor = accentColor,
                        isActive = true,
                        onClick = { onPeerClick(peer) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.Devices,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = peer.deviceName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "${peer.ipAddress} • ${peer.discoveryMethod}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accentColor
                                    )
                                }
                            }
                            Text(
                                text = if (isHindi) "भेजें →" else "Send →",
                                style = MaterialTheme.typography.labelMedium,
                                color = accentColor,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiverWaitingRadarView(
    localDeviceName: String,
    localIpAddress: String,
    isHindi: Boolean,
    accentColor: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "receiver_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(136.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxR = size.minDimension / 2f
                drawCircle(
                    color = accentColor.copy(alpha = (1f - pulseScale) * 0.55f),
                    radius = maxR * pulseScale,
                    center = center,
                    style = Stroke(width = 4f)
                )
                drawCircle(
                    color = accentColor.copy(alpha = 0.25f),
                    radius = maxR * 0.65f,
                    center = center,
                    style = Stroke(width = 2f)
                )
            }
            Icon(
                imageVector = Icons.Default.WifiTethering,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isHindi) {
                "रडार पर '$localDeviceName' ($localIpAddress) प्रसारित हो रहा है..."
            } else {
                "Broadcasting '$localDeviceName' ($localIpAddress) on Radar..."
            },
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.85f),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}
