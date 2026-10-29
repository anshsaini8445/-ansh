package com.example.viewmodel

import android.app.Application
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.MediaFile
import com.example.data.model.MediaItem
import com.example.data.model.PlaylistEntity
import com.example.data.repository.MediaRepository
import com.example.helpers.FakeVaultHelper
import com.example.helpers.PermanentDeleteHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = MediaRepository(
        application,
        database.mediaDao(),
        database.userMediaDao(),
        database.mediaFileDao()
    )

    // --- Media lists (Initialized immediately <150ms from DeepMediaScanner RAM/Snapshot cache) ---
    private val _videoItems = MutableStateFlow(repository.getInstantMedia(isVideo = true))
    val videoItems: StateFlow<List<MediaItem>> = _videoItems.asStateFlow()

    private val _musicItems = MutableStateFlow(repository.getInstantMedia(isVideo = false))
    val musicItems: StateFlow<List<MediaItem>> = _musicItems.asStateFlow()

    private val _allMediaItems = MutableStateFlow(_videoItems.value + _musicItems.value)
    val allMediaItems: StateFlow<List<MediaItem>> = _allMediaItems.asStateFlow()

    // --- PLAYit-Style Video Sort & View Mode ---
    private val _sortCriteria = MutableStateFlow("Date") // "Date", "Name", "Size", "Length"
    val sortCriteria: StateFlow<String> = _sortCriteria.asStateFlow()

    private val _sortNewToOld = MutableStateFlow(true) // true: From new to old, false: From old to new
    val sortNewToOld: StateFlow<Boolean> = _sortNewToOld.asStateFlow()

    private val _isVideoGridView = MutableStateFlow(false)
    val isVideoGridView: StateFlow<Boolean> = _isVideoGridView.asStateFlow()

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

    // --- Audio Player Engine & Zero-Blink Background Audio ---
    private var mediaPlayer: MediaPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var hardwareEqualizer: Equalizer? = null
    private var hardwareBassBoost: BassBoost? = null
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null

    private val _nowPlaying = MutableStateFlow<MediaItem?>(null)
    val nowPlaying: StateFlow<MediaItem?> = _nowPlaying.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _playbackPitch = MutableStateFlow(1.0f)
    val playbackPitch: StateFlow<Float> = _playbackPitch.asStateFlow()

    private val _activePitchPreset = MutableStateFlow("Normal Studio")
    val activePitchPreset: StateFlow<String> = _activePitchPreset.asStateFlow()

    // --- Hardware 200% Volume Boost ---
    private val _is200BoostEnabled = MutableStateFlow(false)
    val is200BoostEnabled: StateFlow<Boolean> = _is200BoostEnabled.asStateFlow()

    // --- A-B Repeat ---
    private val _abPointAMs = MutableStateFlow<Long?>(null)
    val abPointAMs: StateFlow<Long?> = _abPointAMs.asStateFlow()

    private val _abPointBMs = MutableStateFlow<Long?>(null)
    val abPointBMs: StateFlow<Long?> = _abPointBMs.asStateFlow()

    // --- Sleep Timer ---
    private val _sleepTimerMinutes = MutableStateFlow(0)
    val sleepTimerMinutes: StateFlow<Int> = _sleepTimerMinutes.asStateFlow()

    // --- Loop / Shuffle Mode ---
    private val _isShuffleMode = MutableStateFlow(false)
    val isShuffleMode: StateFlow<Boolean> = _isShuffleMode.asStateFlow()

    // --- EQ & Sound Enhancements ---
    private val _equalizerPreset = MutableStateFlow("Flat")
    val equalizerPreset: StateFlow<String> = _equalizerPreset.asStateFlow()

    private val _equalizerBands = MutableStateFlow(listOf(50, 50, 50, 50, 50))
    val equalizerBands: StateFlow<List<Int>> = _equalizerBands.asStateFlow()

    private val _bassBoostEnabled = MutableStateFlow(false)
    val bassBoostEnabled: StateFlow<Boolean> = _bassBoostEnabled.asStateFlow()

    // --- Theme & Personalization Studio ---
    private val _activeTheme = MutableStateFlow("Hybrid Emerald")
    val activeTheme: StateFlow<String> = _activeTheme.asStateFlow()

    private val _customWallpaperUri = MutableStateFlow<String?>(null)
    val customWallpaperUri: StateFlow<String?> = _customWallpaperUri.asStateFlow()

    private val _selectedLanguage = MutableStateFlow("English (Default)")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private val _oneHandMode = MutableStateFlow(false)
    val oneHandMode: StateFlow<Boolean> = _oneHandMode.asStateFlow()

    // --- 3D Companion Mascot ---
    private val _companionEnabled = MutableStateFlow(true)
    val companionEnabled: StateFlow<Boolean> = _companionEnabled.asStateFlow()

    private val _companionBreed = MutableStateFlow("Muso Headphone Puppy (DJ)")
    val companionBreed: StateFlow<String> = _companionBreed.asStateFlow()

    // --- Engine Switches ---
    private val _hybrid8kDecoder = MutableStateFlow(true)
    val hybrid8kDecoder: StateFlow<Boolean> = _hybrid8kDecoder.asStateFlow()

    private val _hdrCcBadges = MutableStateFlow(true)
    val hdrCcBadges: StateFlow<Boolean> = _hdrCcBadges.asStateFlow()

    // --- Smart Search Recent History ---
    private val _recentSearches = MutableStateFlow(
        listOf("8K HDR", "Synthwave", "Blender", "Partial", "Lofi")
    )
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    // --- Dual-PIN Private Vault Lock System ---
    private val _vaultPinSet = MutableStateFlow(false)
    val vaultPinSet: StateFlow<Boolean> = _vaultPinSet.asStateFlow()

    private val _vaultUnlocked = MutableStateFlow(false)
    val vaultUnlocked: StateFlow<Boolean> = _vaultUnlocked.asStateFlow()

    private val _isDecoyVault = MutableStateFlow(false)
    val isDecoyVault: StateFlow<Boolean> = _isDecoyVault.asStateFlow()

    // Visualizer spectrum
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
            repository.syncMediaLibrary(videos + music)
        }
    }

    fun setSortOptions(criteria: String, newToOld: Boolean) {
        _sortCriteria.value = criteria
        _sortNewToOld.value = newToOld
    }

    fun toggleVideoViewMode() {
        _isVideoGridView.value = !_isVideoGridView.value
    }

    fun addRecentSearch(query: String) {
        val clean = query.trim()
        if (clean.length < 2) return
        val updated = (_recentSearches.value.filterNot { it.equals(clean, ignoreCase = true) }).toMutableList()
        updated.add(0, clean)
        _recentSearches.value = updated.take(10)
    }

    fun clearRecentSearches() {
        _recentSearches.value = emptyList()
    }

    // --- Recycle Bin & Permanent Delete ---
    fun moveToRecycleBin(item: MediaItem) {
        PermanentDeleteHelper.moveToRecycleBin(getApplication(), item)
        loadMedia()
    }

    fun moveBatchToRecycleBin(items: List<MediaItem>) {
        PermanentDeleteHelper.moveBatchToRecycleBin(getApplication(), items)
        loadMedia()
    }

    fun deletePermanently(item: MediaItem) {
        PermanentDeleteHelper.deletePermanently(getApplication(), item)
        loadMedia()
    }

    private fun checkVaultPinStatus() {
        viewModelScope.launch {
            val pinHash = repository.getVaultPinHash()
            _vaultPinSet.value = pinHash != null
        }
    }

    // --- Music & Background Audio Player Operations ---
    fun playMusic(item: MediaItem) {
        progressJob?.cancel()
        try {
            loudnessEnhancer?.release()
            hardwareEqualizer?.release()
            hardwareBassBoost?.release()
        } catch (_: Exception) {
        }
        loudnessEnhancer = null
        hardwareEqualizer = null
        hardwareBassBoost = null
        mediaPlayer?.release()
        _abPointAMs.value = null
        _abPointBMs.value = null

        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(item.path)
                prepareAsync()
                setOnPreparedListener { mp ->
                    mp.start()
                    _nowPlaying.value = item
                    _isPlaying.value = true
                    applySpeedAndPitch()
                    applyLoudnessBoost()
                    applyHardwareEqualizerAndBass()
                    startProgressTracker()

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
                // Still set nowPlaying so UI studio screen can preview metadata safely
                _nowPlaying.value = item
            }
        }
    }

    fun shufflePlayMusic() {
        val list = _musicItems.value
        if (list.isNotEmpty()) {
            _isShuffleMode.value = true
            playMusic(list.random())
        }
    }

    fun toggleShuffleMode() {
        _isShuffleMode.value = !_isShuffleMode.value
    }

    fun togglePlayPause() {
        mediaPlayer?.let { mp ->
            try {
                if (mp.isPlaying) {
                    mp.pause()
                    _isPlaying.value = false
                    progressJob?.cancel()
                } else {
                    mp.start()
                    _isPlaying.value = true
                    startProgressTracker()
                }
            } catch (_: Exception) {
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.let { mp ->
            try {
                mp.seekTo(positionMs.toInt())
                _playbackPosition.value = positionMs
                viewModelScope.launch {
                    _nowPlaying.value?.let {
                        repository.saveProgress(it.id, positionMs)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed.coerceIn(0.25f, 4.0f)
        applySpeedAndPitch()
    }

    fun applyPitchPreset(name: String, speed: Float, pitch: Float) {
        _activePitchPreset.value = name
        _playbackSpeed.value = speed
        _playbackPitch.value = pitch
        applySpeedAndPitch()
    }

    private fun applySpeedAndPitch() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    try {
                        val params = PlaybackParams().apply {
                            this.speed = _playbackSpeed.value
                            this.pitch = _playbackPitch.value
                        }
                        mp.playbackParams = params
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    fun toggle200Boost() {
        _is200BoostEnabled.value = !_is200BoostEnabled.value
        applyLoudnessBoost()
    }

    fun set200Boost(enabled: Boolean) {
        _is200BoostEnabled.value = enabled
        applyLoudnessBoost()
    }

    private fun applyLoudnessBoost() {
        val sessionId = mediaPlayer?.audioSessionId ?: return
        try {
            if (loudnessEnhancer == null) {
                loudnessEnhancer = LoudnessEnhancer(sessionId)
            }
            loudnessEnhancer?.setTargetGain(if (_is200BoostEnabled.value) 1500 else 0)
            loudnessEnhancer?.enabled = _is200BoostEnabled.value
        } catch (_: Exception) {
        }
    }

    fun toggleAbRepeatPoint() {
        val current = _playbackPosition.value
        when {
            _abPointAMs.value == null -> _abPointAMs.value = current
            _abPointBMs.value == null && current > (_abPointAMs.value ?: 0L) + 1000L -> _abPointBMs.value = current
            else -> {
                _abPointAMs.value = null
                _abPointBMs.value = null
            }
        }
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepTimerMinutes.value = minutes
        if (minutes > 0) {
            sleepTimerJob = viewModelScope.launch {
                delay(minutes * 60_000L)
                if (_isPlaying.value) {
                    togglePlayPause()
                }
                _sleepTimerMinutes.value = 0
            }
        }
    }

    fun playNext() {
        val currentList = _musicItems.value
        if (currentList.isEmpty()) return
        if (_isShuffleMode.value && currentList.size > 1) {
            val others = currentList.filter { it.id != _nowPlaying.value?.id }
            playMusic(others.random())
            return
        }
        val currentIndex = currentList.indexOfFirst { it.id == _nowPlaying.value?.id }
        if (currentIndex != -1 && currentIndex < currentList.size - 1) {
            playMusic(currentList[currentIndex + 1])
        } else {
            playMusic(currentList.first())
        }
    }

    fun playPrevious() {
        val currentList = _musicItems.value
        if (currentList.isEmpty()) return
        val currentIndex = currentList.indexOfFirst { it.id == _nowPlaying.value?.id }
        if (currentIndex > 0) {
            playMusic(currentList[currentIndex - 1])
        } else {
            playMusic(currentList.last())
        }
    }

    private fun startProgressTracker() {
        progressJob = viewModelScope.launch {
            while (mediaPlayer?.isPlaying == true) {
                val currentPos = mediaPlayer?.currentPosition?.toLong() ?: 0L
                _playbackPosition.value = currentPos

                // Check A-B Repeat loop
                val pointA = _abPointAMs.value
                val pointB = _abPointBMs.value
                if (pointA != null && pointB != null && currentPos >= pointB) {
                    mediaPlayer?.seekTo(pointA.toInt())
                }

                _nowPlaying.value?.let {
                    repository.saveProgress(it.id, currentPos)
                }

                _visualizerFrequencies.value = List(16) { index ->
                    val multiplier = when (_equalizerPreset.value) {
                        "Bass Booster" -> if (index < 6) 1.6f else 0.8f
                        "Treble Booster" -> if (index > 10) 1.7f else 0.7f
                        "Vocal Booster" -> if (index in 5..11) 1.5f else 0.8f
                        "3D Surround" -> if (index % 2 == 0) 1.4f else 1.0f
                        else -> 1.0f
                    }
                    val randomWave = (0.15f..0.65f).random()
                    (randomWave * multiplier).coerceIn(0.1f, 1.0f)
                }

                delay(250)
            }
            _visualizerFrequencies.value = List(16) { 0.1f }
        }
    }

    private fun ClosedFloatingPointRange<Float>.random(): Float {
        return (start + Math.random() * (endInclusive - start)).toFloat()
    }

    // --- Equalizer Controls ---
    fun selectEqualizerPreset(preset: String) = setEqualizerPreset(preset)

    fun setEqualizerPreset(preset: String) {
        _equalizerPreset.value = preset
        _equalizerBands.value = when (preset) {
            "Flat" -> listOf(50, 50, 50, 50, 50)
            "Bass Booster" -> listOf(88, 76, 50, 45, 45)
            "Treble Booster" -> listOf(45, 45, 55, 80, 90)
            "Vocal Booster" -> listOf(40, 65, 85, 70, 45)
            "3D Surround" -> listOf(80, 50, 75, 50, 85)
            "Heavy Metal" -> listOf(85, 60, 40, 75, 88)
            else -> listOf(50, 50, 50, 50, 50)
        }
        applyHardwareEqualizerAndBass()
    }

    fun updateEqualizerBand(bandIndex: Int, value: Int) = updateBandLevel(bandIndex, value)

    fun updateBandLevel(bandIndex: Int, value: Int) {
        val current = _equalizerBands.value.toMutableList()
        if (bandIndex in current.indices) {
            current[bandIndex] = value.coerceIn(0, 100)
            _equalizerBands.value = current
            _equalizerPreset.value = "Custom"
            applyHardwareEqualizerAndBass()
        }
    }

    fun toggleBassBoost() {
        _bassBoostEnabled.value = !_bassBoostEnabled.value
        applyHardwareEqualizerAndBass()
    }

    private fun applyHardwareEqualizerAndBass() {
        val sessionId = mediaPlayer?.audioSessionId ?: return
        try {
            if (hardwareEqualizer == null) {
                hardwareEqualizer = Equalizer(0, sessionId).apply { enabled = true }
            }
            hardwareEqualizer?.let { eq ->
                val range = eq.bandLevelRange
                val minLevel = range[0].toInt()
                val maxLevel = range[1].toInt()
                val numBands = eq.numberOfBands.toInt().coerceAtMost(_equalizerBands.value.size)
                for (i in 0 until numBands) {
                    val pct = _equalizerBands.value[i].coerceIn(0, 100) / 100f
                    val levelMb = (minLevel + (maxLevel - minLevel) * pct).toInt().toShort()
                    eq.setBandLevel(i.toShort(), levelMb)
                }
            }
        } catch (_: Exception) {
        }

        try {
            if (hardwareBassBoost == null) {
                hardwareBassBoost = BassBoost(0, sessionId)
            }
            hardwareBassBoost?.let { bb ->
                bb.enabled = _bassBoostEnabled.value
                if (bb.strengthSupported) {
                    bb.setStrength(if (_bassBoostEnabled.value) 800.toShort() else 0.toShort())
                }
            }
        } catch (_: Exception) {
        }
    }

    // --- Dual-PIN Private Vault Operations ---
    fun setupVaultPin(pin: String) {
        viewModelScope.launch {
            val pinHash = pin.hashCode().toString()
            repository.setVaultPin(pinHash)
            _vaultPinSet.value = true
            _isDecoyVault.value = false
            _vaultUnlocked.value = true
        }
    }

    fun verifyVaultPin(pin: String): Boolean {
        if (FakeVaultHelper.isDecoyPin(pin)) {
            _isDecoyVault.value = true
            _vaultUnlocked.value = true
            return true
        }
        val pinHash = pin.hashCode().toString()
        viewModelScope.launch {
            val hash = repository.getVaultPinHash()
            if (hash == pinHash) {
                _isDecoyVault.value = false
                _vaultUnlocked.value = true
            }
        }
        return true
    }

    fun lockVault() {
        _vaultUnlocked.value = false
        _isDecoyVault.value = false
    }

    fun toggleVaultLock(mediaItem: MediaItem) {
        viewModelScope.launch {
            val isPrivate = repository.isPrivateItem(mediaItem.id)
            if (isPrivate) {
                repository.decryptFromVault(mediaItem.id)
            } else {
                repository.encryptToVault(mediaItem.id)
            }
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

    // --- Personalization & Companion Controls ---
    fun selectTheme(themeName: String) {
        _activeTheme.value = themeName
    }

    fun setCustomWallpaper(uriString: String?) {
        _customWallpaperUri.value = uriString
    }

    fun selectLanguage(lang: String) {
        _selectedLanguage.value = lang
    }

    fun toggleOneHandMode() {
        _oneHandMode.value = !_oneHandMode.value
    }

    fun setCompanionEnabled(enabled: Boolean) {
        _companionEnabled.value = enabled
    }

    fun selectCompanionBreed(breedName: String) {
        _companionBreed.value = breedName
        _companionEnabled.value = true
    }

    fun toggleHybrid8kDecoder() {
        _hybrid8kDecoder.value = !_hybrid8kDecoder.value
    }

    fun toggleHdrCcBadges() {
        _hdrCcBadges.value = !_hdrCcBadges.value
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
        sleepTimerJob?.cancel()
        try {
            loudnessEnhancer?.release()
            hardwareEqualizer?.release()
            hardwareBassBoost?.release()
        } catch (_: Exception) {
        }
        mediaPlayer?.release()
    }
}
