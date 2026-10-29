package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaItem
import com.example.data.model.PlaylistEntity
import com.example.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    viewModel: PlayerViewModel,
    onPlayMusic: (MediaItem) -> Unit,
    onBackClick: () -> Unit,
    accentColor: Color
) {
    val context = LocalContext.current
    val playlists by viewModel.playlists.collectAsState()
    val musicItems by viewModel.musicItems.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    
    var selectedPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    val playlistItems by if (selectedPlaylist != null) {
        viewModel.getPlaylistItemsFlow(selectedPlaylist!!.id, musicItems).collectAsState(emptyList())
    } else {
        remember { mutableStateOf(emptyList<MediaItem>()) }
    }

    var showAddSongsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = selectedPlaylist?.name ?: "MAX Playlists",
                        color = Color.White
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedPlaylist != null) {
                            selectedPlaylist = null
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (selectedPlaylist != null) {
                        IconButton(onClick = { showAddSongsDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Track", tint = accentColor)
                        }
                        IconButton(onClick = {
                            viewModel.removePlaylist(selectedPlaylist!!.id)
                            selectedPlaylist = null
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Playlist", tint = Color.Gray)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (selectedPlaylist == null) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = accentColor,
                    contentColor = Color.Black
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Playlist")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            if (selectedPlaylist == null) {
                // Playlists List
                if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.FeaturedPlayList, contentDescription = "No Playlists", tint = Color.Gray, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No Playlists Created Yet", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(playlists) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable { selectedPlaylist = playlist }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlaylistPlay, contentDescription = "Playlist", tint = accentColor, modifier = Modifier.size(28.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = playlist.name,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Open", tint = Color.Gray)
                            }
                        }
                    }
                }
            } else {
                // Playlist Songs Detail View
                if (playlistItems.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.MusicNote, contentDescription = "No Songs", tint = Color.Gray, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Playlist is empty", color = Color.Gray, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { showAddSongsDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                            ) {
                                Text("Add Songs", color = Color.Black)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(playlistItems) { song ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable { onPlayMusic(song) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = "Song", tint = accentColor)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = song.title,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = song.artist,
                                            color = Color.Gray,
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { viewModel.removeFromPlaylist(selectedPlaylist!!.id, song.id) }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Create Playlist Dialog ---
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Playlist Name", color = Color.White) },
            text = {
                TextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("E.g., Retro Driving") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            viewModel.addPlaylist(newPlaylistName)
                            newPlaylistName = ""
                            showCreateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text("Create", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // --- Add Songs to Playlist Dialog ---
    if (showAddSongsDialog && selectedPlaylist != null) {
        val nonPlaylistSongs = musicItems.filter { song -> playlistItems.none { it.id == song.id } }

        AlertDialog(
            onDismissRequest = { showAddSongsDialog = false },
            title = { Text("Add Premium Tracks", color = Color.White) },
            text = {
                Box(modifier = Modifier.sizeIn(maxHeight = 300.dp)) {
                    if (nonPlaylistSongs.isEmpty()) {
                        Text("All available premium tracks are already in this playlist.", color = Color.Gray)
                    } else {
                        LazyColumn {
                            items(nonPlaylistSongs) { song ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.addToPlaylist(selectedPlaylist!!.id, song.id)
                                            Toast.makeText(context, "${song.title} Added", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.AddCircle, contentDescription = "Add", tint = accentColor)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(song.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text(song.artist, color = Color.Gray, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAddSongsDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text("Done", color = Color.Black)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
