package com.example.ui.screens

import android.app.Activity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaItem
import com.example.ui.theme.*
import com.example.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateFolderScreen(
    viewModel: PlayerViewModel,
    onPlayMusic: (MediaItem) -> Unit,
    onPlayVideo: (MediaItem) -> Unit,
    onBackClick: () -> Unit,
    accentColor: Color = HybridEmerald
) {
    val context = LocalContext.current
    val isPinSet by viewModel.vaultPinSet.collectAsState()
    val isUnlocked by viewModel.vaultUnlocked.collectAsState()
    val isDecoyVault by viewModel.isDecoyVault.collectAsState()
    val privateItems by viewModel.privateItems.collectAsState()

    var pinDigits by remember { mutableStateOf("") }
    var setupStep by remember { mutableStateOf(1) }
    var setupFirstPin by remember { mutableStateOf("") }
    var keypadErrorMessage by remember { mutableStateOf<String?>(null) }

    // Apply FLAG_SECURE to block screenshots while inside Privacy Vault
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    LaunchedEffect(isUnlocked) {
        if (!isUnlocked) {
            pinDigits = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Privacy Vault (FLAG_SECURE)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.lockVault()
                        onBackClick()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (isUnlocked) {
                        IconButton(onClick = { viewModel.lockVault() }) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock Vault", tint = HybridEmerald)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HybridDarkBg)
            )
        },
        containerColor = HybridDarkBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!isPinSet) {
                // PIN Setup Flow
                VaultPinPadSection(
                    title = if (setupStep == 1) "Create Real Vault PIN (4 Digits)" else "Confirm Your Real PIN",
                    subtitle = "Tip: Entering '0000' later opens an Empty Decoy Vault to protect your privacy.",
                    pinDigits = pinDigits,
                    errorMessage = keypadErrorMessage,
                    onDigitPress = { digit ->
                        if (pinDigits.length < 4) {
                            pinDigits += digit
                            keypadErrorMessage = null
                            if (pinDigits.length == 4) {
                                if (pinDigits == "0000") {
                                    keypadErrorMessage = "0000 is reserved for Fake Decoy Vault. Choose another PIN."
                                    pinDigits = ""
                                } else if (setupStep == 1) {
                                    setupFirstPin = pinDigits
                                    pinDigits = ""
                                    setupStep = 2
                                } else {
                                    if (pinDigits == setupFirstPin) {
                                        viewModel.setupVaultPin(pinDigits)
                                        Toast.makeText(context, "Dual-PIN Privacy Vault Activated!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        keypadErrorMessage = "PINs did not match. Try again."
                                        setupStep = 1
                                        pinDigits = ""
                                    }
                                }
                            }
                        }
                    },
                    onBackspace = {
                        if (pinDigits.isNotEmpty()) pinDigits = pinDigits.dropLast(1)
                    }
                )
            } else if (!isUnlocked) {
                // Unlock Flow (Supports Real PIN or "0000" Decoy PIN)
                VaultPinPadSection(
                    title = "Enter Privacy Vault PIN",
                    subtitle = "Enter your Real PIN (or '0000' for Decoy Empty Vault)",
                    pinDigits = pinDigits,
                    errorMessage = keypadErrorMessage,
                    onDigitPress = { digit ->
                        if (pinDigits.length < 4) {
                            pinDigits += digit
                            keypadErrorMessage = null
                            if (pinDigits.length == 4) {
                                viewModel.verifyVaultPin(pinDigits)
                                if (!viewModel.vaultUnlocked.value) {
                                    keypadErrorMessage = "Incorrect PIN (Try Real PIN or 0000 Decoy)"
                                    pinDigits = ""
                                }
                            }
                        }
                    },
                    onBackspace = {
                        if (pinDigits.isNotEmpty()) pinDigits = pinDigits.dropLast(1)
                    }
                )
            } else {
                // Vault Unlocked Content (Empty if Decoy Mode "0000" was used!)
                val visibleItems = if (isDecoyVault) emptyList() else privateItems

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(HybridCardBg)
                            .border(1.dp, HybridCardBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = HybridEmerald
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isDecoyVault) {
                                "Decoy Vault Active (0000) • Real Files Hidden"
                            } else {
                                "Real Vault Unlocked • Screenshot Protection Active"
                            },
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (visibleItems.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.FolderOff,
                                    contentDescription = "Empty Vault",
                                    tint = HybridTextSecondary,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "No Protected Files in Vault",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                                Text(
                                    text = "Use the 3-dot menu on any video or song to lock it here.",
                                    color = HybridTextSecondary,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(visibleItems, key = { it.id }) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(HybridCardBg)
                                        .border(1.dp, HybridCardBorder, RoundedCornerShape(14.dp))
                                        .clickable {
                                            if (item.isVideo) onPlayVideo(item) else onPlayMusic(item)
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (item.isVideo) Icons.Default.Movie else Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = HybridEmerald
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.cleanTitle,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${item.formattedDuration} • ${item.formattedSize}",
                                            color = HybridTextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                    TextButton(onClick = { viewModel.toggleVaultLock(item) }) {
                                        Text("Unlock", color = HybridCyan, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultPinPadSection(
    title: String,
    subtitle: String,
    pinDigits: String,
    errorMessage: String?,
    onDigitPress: (String) -> Unit,
    onBackspace: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.EnhancedEncryption,
            contentDescription = "Vault Lock",
            tint = HybridEmerald,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            color = HybridTextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 4 PIN Dots
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { index ->
                val filled = index < pinDigits.length
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (filled) HybridEmerald else Color(0x33FFFFFF))
                        .border(1.dp, HybridEmerald, CircleShape)
                )
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(errorMessage, color = HybridCoralRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(28.dp))

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "DEL")
        )

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    row.forEach { key ->
                        if (key.isEmpty()) {
                            Spacer(modifier = Modifier.size(68.dp))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(HybridCardBg)
                                    .border(1.dp, HybridCardBorder, CircleShape)
                                    .clickable {
                                        if (key == "DEL") onBackspace() else onDigitPress(key)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (key == "DEL") {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                                        contentDescription = "Delete",
                                        tint = Color.White
                                    )
                                } else {
                                    Text(
                                        text = key,
                                        color = Color.White,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
