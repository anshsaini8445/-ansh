package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaItem
import com.example.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateFolderScreen(
    viewModel: PlayerViewModel,
    onPlayMusic: (MediaItem) -> Unit,
    onPlayVideo: (MediaItem) -> Unit,
    onBackClick: () -> Unit,
    accentColor: Color
) {
    val context = LocalContext.current
    val isPinSet by viewModel.vaultPinSet.collectAsState()
    val isUnlocked by viewModel.vaultUnlocked.collectAsState()
    val privateItems by viewModel.privateItems.collectAsState()

    var pinDigits by remember { mutableStateOf("") }
    var setupStep by remember { mutableStateOf(1) } // 1: Enter, 2: Confirm
    var setupFirstPin by remember { mutableStateOf("") }

    var keypadErrorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isUnlocked) {
        if (!isUnlocked) {
            pinDigits = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MAX Encrypted Safe Folder", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.lockVault()
                        onBackClick()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (isUnlocked) {
                        IconButton(onClick = { viewModel.lockVault() }) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock", tint = accentColor)
                        }
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
        ) {
            if (!isPinSet) {
                // PIN Setup Flow
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Security, contentDescription = "Security", tint = accentColor, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (setupStep == 1) "Create Private Safe Folder PIN" else "Confirm Your PIN",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Protect your private media behind an encrypted lock.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // PIN Dots indicators
                    PinIndicatorDots(pinDigits.length)

                    Spacer(modifier = Modifier.height(48.dp))

                    // Keypad
                    SafeKeypad(
                        onDigitClick = { digit ->
                            if (pinDigits.length < 4) {
                                pinDigits += digit
                                if (pinDigits.length == 4) {
                                    if (setupStep == 1) {
                                        setupFirstPin = pinDigits
                                        pinDigits = ""
                                        setupStep = 2
                                    } else {
                                        if (pinDigits == setupFirstPin) {
                                            viewModel.setupVaultPin(pinDigits)
                                            Toast.makeText(context, "PIN Created Successfully!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            keypadErrorMessage = "PINs do not match! Restarting setup..."
                                            pinDigits = ""
                                            setupStep = 1
                                        }
                                    }
                                }
                            }
                        },
                        onBackspaceClick = {
                            if (pinDigits.isNotEmpty()) {
                                pinDigits = pinDigits.dropLast(1)
                            }
                        },
                        onClearClick = {
                            pinDigits = ""
                        }
                    )
                }
            } else if (!isUnlocked) {
                // Unlock Keypad
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Lock, contentDescription = "Lock", tint = accentColor, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Enter Vault PIN",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Decryption key required to view contents",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // PIN Dots indicators
                    PinIndicatorDots(pinDigits.length)

                    if (keypadErrorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = keypadErrorMessage!!, color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    // Keypad
                    SafeKeypad(
                        onDigitClick = { digit ->
                            if (pinDigits.length < 4) {
                                pinDigits += digit
                                keypadErrorMessage = null
                                if (pinDigits.length == 4) {
                                    val verified = viewModel.verifyVaultPin(pinDigits)
                                    if (!verified) {
                                        keypadErrorMessage = "Invalid Passcode. Please try again."
                                        pinDigits = ""
                                    }
                                }
                            }
                        },
                        onBackspaceClick = {
                            if (pinDigits.isNotEmpty()) {
                                pinDigits = pinDigits.dropLast(1)
                            }
                        },
                        onClearClick = {
                            pinDigits = ""
                        }
                    )
                }
            } else {
                // Vault Unlocked & Exploring Files
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = "Encrypted", tint = accentColor)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Premium Local Encryption Active",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${privateItems.size} hidden files isolated from public libraries.",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (privateItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Security, contentDescription = "Safe Empty", tint = Color.Gray, modifier = Modifier.size(64.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Your Vault is Empty", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Long-press any file on the home dashboard to encrypt it and move it to this folder.",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            items(privateItems) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (item.isVideo) onPlayVideo(item) else onPlayMusic(item)
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surface),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (item.isVideo) Icons.Default.Videocam else Icons.Default.MusicNote,
                                            contentDescription = "Safe File",
                                            tint = accentColor
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${item.artist} • ${item.formattedSize}",
                                            color = Color.Gray,
                                            fontSize = 11.sp
                                        )
                                    }

                                    // Decrypt Action
                                    IconButton(
                                        onClick = { viewModel.toggleVaultLock(item) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LockOpen,
                                            contentDescription = "Decrypt",
                                            tint = accentColor
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
}

@Composable
fun PinIndicatorDots(filledCount: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..4) {
            val isFilled = i <= filledCount
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (isFilled) Color.White else Color.White.copy(alpha = 0.2f))
            )
        }
    }
}

@Composable
fun SafeKeypad(
    onDigitClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onClearClick: () -> Unit
) {
    val keypadStructure = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("C", "0", "<-")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        keypadStructure.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                row.forEach { cell ->
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable {
                                when (cell) {
                                    "C" -> onClearClick()
                                    "<-" -> onBackspaceClick()
                                    else -> onDigitClick(cell)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cell,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
