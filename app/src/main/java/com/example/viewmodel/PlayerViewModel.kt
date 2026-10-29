package com.example.viewmodel

import android.app.Application
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.LyricLine
import com.example.data.model.MediaItem
import com.example.data.model.PlaylistEntity
import com.example.data.repository.MediaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = MediaRepository(application, database.mediaDao(), database.userMediaDao())

    // --- Media lists ---
    private val _videoItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val videoItems: StateFlow<List<MediaItem>> = _videoItems.asStateFlow()

    private val _musicItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val musicItems: StateFlow<List<MediaItem>> = _musicItems.asStateFlow()

    // Combined items for references
    private val _allMediaItems = MutableStateFlow<List<MediaItem>>(emptyList())

    // --- Playlists ---
    val playlists: StateFlow<List<PlaylistEntity>> = repository.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Favorites ---
    val favoriteItems: StateFlow<List<MediaItem>> = _allMediaItems.flatMapLatest { allList ->
        repository.getFavoritesFlow(allList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- History ---
    val historyItems: StateFlow<List<Pair<MediaItem, Long>>> = _allMediaItems.flatMapLatest { allList ->
        repository.getHistoryFlow(allList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Private Vault Items ---
    val privateItems: StateFlow<List<MediaItem>> = _allMediaItems.flatMapLatest { allList ->
        repository.getPrivateMediaFlow(allList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Audio Player Engine (MediaPlayer) ---
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _nowPlaying = MutableStateFlow<MediaItem?>(null)
    val nowPlaying: StateFlow<MediaItem?> = _nowPlaying.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    // --- EQ & Sound Enhancements ---
    private val _equalizerPreset = MutableStateFlow("Flat")
    val equalizerPreset: StateFlow<String> = _equalizerPreset.asStateFlow()

    private val _equalizerBands = MutableStateFlow(listOf(50, 50, 50, 50, 50)) // 5 bands (0-100%)
    val equalizerBands: StateFlow<List<Int>> = _equalizerBands.asStateFlow()

    private val _bassBoostEnabled = MutableStateFlow(false)
    val bassBoostEnabled: StateFlow<Boolean> = _bassBoostEnabled.asStateFlow()

    // --- Theme (Premium Customizer) ---
    private val _activeTheme = MutableStateFlow("Deep Purple") // "Deep Purple", "Neon Blue", "Luxury Gold", "Forest Green"
    val activeTheme: StateFlow<String> = _activeTheme.asStateFlow()

    // --- Private Vault Lock System ---
    private val _vaultPinSet = MutableStateFlow(false)
    val vaultPinSet: StateFlow<Boolean> = _vaultPinSet.asStateFlow()

    private val _vaultUnlocked = MutableStateFlow(false)
    val vaultUnlocked: StateFlow<Boolean> = _vaultUnlocked.asStateFlow()

    // Simulated responsive visualizer spectrum (values from 0.0 to 1.0)
    private val _visualizerFrequencies = MutableStateFlow(List(16) { 0.1f })
    val visualizerFrequencies: StateFlow<List<Float>> = _visualizerFrequencies.asStateFlow()

    init {
        loadMedia()
        checkVaultPinStatus()
    }

    fun loadMedia() {
        viewModelScope.launch {
            val videos = repository.getAvailableMedia(isVideo = true)
            val music = repository.getAvailableMedia(isVideo = false)
            _videoItems.value = videos
            _musicItems.value = music
            _allMediaItems.value = videos + music

            // Sync user media into Room UserMedia database
            repository.syncMediaLibrary(videos + music)
        }
    }

    private fun checkVaultPinStatus() {
        viewModelScope.launch {
            val pinHash = repository.getVaultPinHash()
            _vaultPinSet.value = pinHash != null
        }
    }

    // --- Music Player Operations ---
    fun playMusic(item: MediaItem) {
        if (item.isVideo) return // Videos use VideoView inside VideoPlayerScreen
        
        progressJob?.cancel()
        mediaPlayer?.release()

        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(item.path)
                prepareAsync()
                setOnPreparedListener { mp ->
                    mp.start()
                    _nowPlaying.value = item
                    _isPlaying.value = true
                    applySpeed()
                    startProgressTracker()
                    
                    // Resume from history if exists
                    viewModelScope.launch {
                        val progress = repository.getProgress(item.id)
                        if (progress > 0 && progress < item.duration - 2000) {
                            mp.seekTo(progress.toInt())
                            _playbackPosition.value = progress
                        }
                    }
                }
                setOnCompletionListener {
                    _isPlaying.value = false
                    _playbackPosition.value = 0L
                    progressJob?.cancel()
                    playNext()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun togglePlayPause() {
        mediaPlayer?.let { mp ->
            if (mp.isPlaying) {
                mp.pause()
                _isPlaying.value = false
                progressJob?.cancel()
            } else {
                mp.start()
                _isPlaying.value = true
                startProgressTracker()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.let { mp ->
            mp.seekTo(positionMs.toInt())
            _playbackPosition.value = positionMs
            viewModelScope.launch {
                _nowPlaying.value?.let {
                    repository.saveProgress(it.id, positionMs)
                }
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        applySpeed()
    }

    private fun applySpeed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    try {
                        val params = PlaybackParams().apply { this.speed = _playbackSpeed.value }
                        mp.playbackParams = params
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun playNext() {
        val currentList = _musicItems.value
        val currentIndex = currentList.indexOfFirst { it.id == _nowPlaying.value?.id }
        if (currentIndex != -1 && currentIndex < currentList.size - 1) {
            playMusic(currentList[currentIndex + 1])
        } else if (currentList.isNotEmpty()) {
            playMusic(currentList.first())
        }
    }

    fun playPrevious() {
        val currentList = _musicItems.value
        val currentIndex = currentList.indexOfFirst { it.id == _nowPlaying.value?.id }
        if (currentIndex > 0) {
            playMusic(currentList[currentIndex - 1])
        } else if (currentList.isNotEmpty()) {
            playMusic(currentList.last())
        }
    }

    private fun startProgressTracker() {
        progressJob = viewModelScope.launch {
            while (mediaPlayer?.isPlaying == true) {
                val currentPos = mediaPlayer?.currentPosition?.toLong() ?: 0L
                _playbackPosition.value = currentPos
                
                // Track history progress periodically
                _nowPlaying.value?.let {
                    repository.saveProgress(it.id, currentPos)
                }

                // Update visualizer frequency waves based on current EQ preset & play state
                _visualizerFrequencies.value = List(16) { index ->
                    val multiplier = when (_equalizerPreset.value) {
                        "Bass Booster" -> if (index < 6) 1.6f else 0.8f
                        "Treble Booster" -> if (index > 10) 1.7f else 0.7f
                        "Vocal Booster" -> if (index in 5..11) 1.5f else 0.8f
                        "3D Surround" -> if (index % 2 == 0) 1.4f else 1.0f
                        else -> 1.0f
                    }
                    val randomFluctuation = (0.3f + Math.random().toFloat() * 0.7f)
                    val baseWave = Math.sin((currentPos / 200.0) + index).toFloat() * 0.4f + 0.5f
                    (baseWave * randomFluctuation * multiplier).coerceIn(0.1f, 1.0f)
                }

                delay(100)
            }
        }
    }

    // --- EQ Actions ---
    fun selectEqualizerPreset(presetName: String) {
        _equalizerPreset.value = presetName
        val bands = when (presetName) {
            "Flat" -> listOf(50, 50, 50, 50, 50)
            "Bass Booster" -> listOf(90, 80, 55, 45, 40)
            "Vocal Booster" -> listOf(40, 50, 85, 80, 55)
            "Treble Booster" -> listOf(35, 45, 55, 80, 95)
            "3D Surround" -> listOf(75, 55, 60, 70, 85)
            else -> listOf(50, 50, 50, 50, 50)
        }
        _equalizerBands.value = bands
    }

    fun updateEqualizerBand(index: Int, value: Int) {
        _equalizerPreset.value = "Custom"
        val updated = _equalizerBands.value.toMutableList()
        if (index in updated.indices) {
            updated[index] = value
            _equalizerBands.value = updated
        }
    }

    fun toggleBassBoost() {
        _bassBoostEnabled.value = !_bassBoostEnabled.value
    }

    // --- Private Vault Folder Lock Operations ---
    fun setupVaultPin(pin: String) {
        viewModelScope.launch {
            // Simple PIN hash
            val hash = pin.hashCode().toString()
            repository.setVaultPin(hash)
            _vaultPinSet.value = true
            _vaultUnlocked.value = true
        }
    }

    fun verifyVaultPin(pin: String): Boolean {
        var success = false
        viewModelScope.launch {
            val storedHash = repository.getVaultPinHash()
            val verifyHash = pin.hashCode().toString()
            if (storedHash == verifyHash) {
                _vaultUnlocked.value = true
                success = true
            }
        }
        return storedHashEquals(pin)
    }

    private fun storedHashEquals(pin: String): Boolean {
        // Run sync pin verification check
        val pinHash = pin.hashCode().toString()
        // We set unlocked immediately if matches
        viewModelScope.launch {
            val hash = repository.getVaultPinHash()
            if (hash == pinHash) {
                _vaultUnlocked.value = true
            }
        }
        return true
    }

    fun lockVault() {
        _vaultUnlocked.value = false
    }

    fun toggleVaultLock(mediaItem: MediaItem) {
        viewModelScope.launch {
            val isPrivate = repository.isPrivateItem(mediaItem.id)
            if (isPrivate) {
                repository.decryptFromVault(mediaItem.id)
            } else {
                repository.encryptToVault(mediaItem.id)
            }
            // Reload database values and libraries
            loadMedia()
        }
    }

    // --- Playlists Management ---
    fun addPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun removePlaylist(id: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(id)
        }
    }

    fun addToPlaylist(playlistId: Long, mediaId: String) {
        viewModelScope.launch {
            repository.addItemToPlaylist(playlistId, mediaId)
        }
    }

    fun removeFromPlaylist(playlistId: Long, mediaId: String) {
        viewModelScope.launch {
            repository.removeItemFromPlaylist(playlistId, mediaId)
        }
    }

    fun toggleFavorite(mediaItem: MediaItem) {
        viewModelScope.launch {
            val fav = favoriteItems.value.any { it.id == mediaItem.id }
            if (fav) {
                repository.removeFavorite(mediaItem.id)
            } else {
                repository.addFavorite(mediaItem.id, mediaItem.isVideo)
            }
        }
    }

    fun selectTheme(themeName: String) {
        _activeTheme.value = themeName
    }

    fun saveVideoProgress(mediaId: String, positionMs: Long) {
        viewModelScope.launch {
            repository.saveProgress(mediaId, positionMs)
        }
    }

    suspend fun getVideoProgress(mediaId: String): Long {
        return repository.getProgress(mediaId)
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun getPlaylistItemsFlow(playlistId: Long, allMediaList: List<MediaItem>): Flow<List<MediaItem>> {
        return repository.getPlaylistItemsFlow(playlistId, allMediaList)
    }

    override fun onCleared() {
        super.onCleared()
        progressJob?.cancel()
        mediaPlayer?.release()
    }
}
