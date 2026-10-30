package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.mediaplayer.AudioService
import com.app.mediaplayer.MediaNotificationHelper
import com.example.data.database.AppDatabase
import com.example.data.model.MediaItem
import com.example.data.model.PlaylistEntity
import com.example.data.repository.MediaRepository
import com.example.helpers.FakeVaultHelper
import com.example.helpers.IntruderLogEntry
import com.example.helpers.Music3DThemeEngine
import com.example.helpers.PermanentDeleteHelper
import com.example.helpers.SecurityDefenseEngine
import com.example.helpers.SyncedLyricLine
import com.example.helpers.SyncedLyricsEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.sqrt

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel(application: Application) :
    AndroidViewModel(application),
    MediaNotificationHelper.ControlDelegate,
    SensorEventListener {

    private var lastNotificationSec: Long = -1L
    private val prefs = application.getSharedPreferences("HYBRID_PLAYER_PREFS", Context.MODE_PRIVATE)
    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

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

    private val _sortNewToOld = MutableStateFlow(true)
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
    private var lockoutTimerJob: Job? = null

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

    // --- Synced Lyrics State ---
    private val _syncedLyrics = MutableStateFlow<List<SyncedLyricLine>>(emptyList())
    val syncedLyrics: StateFlow<List<SyncedLyricLine>> = _syncedLyrics.asStateFlow()

    // --- Hardware 200% Volume Boost & Audio Player Vertical Volume HUD State ---
    private val _is200BoostEnabled = MutableStateFlow(false)
    val is200BoostEnabled: StateFlow<Boolean> = _is200BoostEnabled.asStateFlow()

    private val _audioVolumePercent = MutableStateFlow(readCurrentSystemVolumePercent())
    val audioVolumePercent: StateFlow<Int> = _audioVolumePercent.asStateFlow()

    // --- Double-Shake (2x Shake to Next Track + Vibration) Engine ---
    private val _doubleShakeEnabled = MutableStateFlow(
        prefs.getBoolean("permanent_double_shake_enabled", true)
    )
    val doubleShakeEnabled: StateFlow<Boolean> = _doubleShakeEnabled.asStateFlow()

    private val _shakeSensitivity = MutableStateFlow(
        prefs.getString("double_shake_sensitivity", "Normal") ?: "Normal"
    )
    val shakeSensitivity: StateFlow<String> = _shakeSensitivity.asStateFlow()

    private val _isForegroundVideoActive = MutableStateFlow(false)
    val isForegroundVideoActive: StateFlow<Boolean> = _isForegroundVideoActive.asStateFlow()

    private var firstShakeTimestampMs: Long = 0L
    private var lastDoubleShakeTriggerMs: Long = 0L

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

    // --- Theme & 15+ 3D Animated Music Player Themes ---
    private val _activeTheme = MutableStateFlow(
        prefs.getString("saved_app_theme", "Hybrid Emerald") ?: "Hybrid Emerald"
    )
    val activeTheme: StateFlow<String> = _activeTheme.asStateFlow()

    private val _active3DMusicTheme = MutableStateFlow(
        prefs.getString("saved_3d_music_theme", Music3DThemeEngine.THEMES_3D.first().name)
            ?: Music3DThemeEngine.THEMES_3D.first().name
    )
    val active3DMusicTheme: StateFlow<String> = _active3DMusicTheme.asStateFlow()

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

    // --- 7-Layer Security Fortress & Dual-PIN Private Vault ---
    private val _vaultPinSet = MutableStateFlow(false)
    val vaultPinSet: StateFlow<Boolean> = _vaultPinSet.asStateFlow()

    private val _vaultUnlocked = MutableStateFlow(false)
    val vaultUnlocked: StateFlow<Boolean> = _vaultUnlocked.asStateFlow()

    private val _isDecoyVault = MutableStateFlow(false)
    val isDecoyVault: StateFlow<Boolean> = _isDecoyVault.asStateFlow()

    private val _globalFlagSecure = MutableStateFlow(
        prefs.getBoolean("security_global_flag_secure", false)
    )
    val globalFlagSecure: StateFlow<Boolean> = _globalFlagSecure.asStateFlow()

    private val _autoLockOnMinimize = MutableStateFlow(
        prefs.getBoolean("security_auto_lock_minimize", true)
    )
    val autoLockOnMinimize: StateFlow<Boolean> = _autoLockOnMinimize.asStateFlow()

    private val _panicShakeVaultLock = MutableStateFlow(
        prefs.getBoolean("security_panic_shake_lock", true)
    )
    val panicShakeVaultLock: StateFlow<Boolean> = _panicShakeVaultLock.asStateFlow()

    private val _intruderLogs = MutableStateFlow(
        SecurityDefenseEngine.getIntruderLogs(application)
    )
    val intruderLogs: StateFlow<List<IntruderLogEntry>> = _intruderLogs.asStateFlow()

    private val _vaultLockoutSeconds = MutableStateFlow(
        SecurityDefenseEngine.getLockoutRemainingSeconds(application)
    )
    val vaultLockoutSeconds: StateFlow<Int> = _vaultLockoutSeconds.asStateFlow()

    // Visualizer spectrum
    private val _visualizerFrequencies = MutableStateFlow(List(16) { 0.1f })
    val visualizerFrequencies: StateFlow<List<Float>> = _visualizerFrequencies.asStateFlow()

    init {
        loadMedia()
        checkVaultPinStatus()
        registerDoubleShakeSensor()
        if (_vaultLockoutSeconds.value > 0) {
            startVaultLockoutCountdown()
        }
        _musicItems.value.firstOrNull()?.let { firstTrack ->
            _syncedLyrics.value = SyncedLyricsEngine.loadLyricsForTrack(getApplication(), firstTrack)
        }
    }

    private fun readCurrentSystemVolumePercent(): Int {
        return try {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(0)
            ((cur.toFloat() / max.toFloat()) * 100f).toInt().coerceIn(0, 100)
        } catch (_: Exception) {
            75
        }
    }

    fun syncSystemAudioVolumeFromHardware(): Int {
        val sysPct = readCurrentSystemVolumePercent()
        if (!_is200BoostEnabled.value || sysPct < 100) {
            _audioVolumePercent.value = sysPct
        }
        return _audioVolumePercent.value
    }

    fun setAudioVolumeWithBoost(newPercent: Int) {
        val clamped = newPercent.coerceIn(0, 200)
        _audioVolumePercent.value = clamped
        try {
            val maxSys = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val targetSys = ((clamped.coerceAtMost(100) / 100f) * maxSys).toInt()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetSys, 0)
        } catch (_: Exception) {
        }
        if (clamped > 100) {
            _is200BoostEnabled.value = true
            applyLoudnessBoostCustomGain((clamped - 100) * 18)
        } else {
            _is200BoostEnabled.value = false
            applyLoudnessBoostCustomGain(0)
        }
    }

    // --- Hardware Accelerometer Double-Shake Registration & Detection ---
    private fun registerDoubleShakeSensor() {
        try {
            if (accelerometer != null) {
                sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            }
        } catch (_: Exception) {
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH
        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)

        val thresholdG = when (_shakeSensitivity.value) {
            "High" -> 1.85f
            "Firm" -> 2.55f
            else -> 2.15f
        }

        if (gForce > thresholdG) {
            val now = System.currentTimeMillis()
            // 1. Check Emergency Panic Shake inside Unlocked Privacy Vault
            if (_vaultUnlocked.value && _panicShakeVaultLock.value && now - lastDoubleShakeTriggerMs > 900L) {
                lastDoubleShakeTriggerMs = now
                triggerDoubleShakeVibration(isSingleTick = false)
                lockVault()
                Toast.makeText(
                    getApplication(),
                    "🛡️ Panic Shake Triggered: Privacy Vault Locked!",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            // 2. Check Double-Shake to Skip Song (Active in Audio Mode OR Background Video Audio Mode; OFF in Foreground Video)
            if (!_doubleShakeEnabled.value || _isForegroundVideoActive.value) return
            if (now - lastDoubleShakeTriggerMs < 1100L) return

            val gap = now - firstShakeTimestampMs
            if (gap in 160L..950L) {
                // Confirmed 2nd Shake (Double Shake)!
                firstShakeTimestampMs = 0L
                lastDoubleShakeTriggerMs = now
                executeDoubleShakeNextSong()
            } else if (gap > 950L) {
                // 1st Shake detected: store timestamp & give a subtle haptic tick
                firstShakeTimestampMs = now
                triggerDoubleShakeVibration(isSingleTick = true)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun executeDoubleShakeNextSong() {
        triggerDoubleShakeVibration(isSingleTick = false)
        playNext()
        val trackTitle = _nowPlaying.value?.cleanTitle ?: "Next Track"
        Toast.makeText(
            getApplication(),
            "📳 2x Shake Vibration -> Playing: $trackTitle",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun triggerDoubleShakeVibration(isSingleTick: Boolean = false) {
        try {
            val app = getApplication<Application>()
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (isSingleTick) {
                        vibrator.vibrate(VibrationEffect.createOneShot(25L, 140))
                    } else {
                        vibrator.vibrate(
                            VibrationEffect.createWaveform(longArrayOf(0L, 65L, 55L, 95L), intArrayOf(0, 220, 0, 255), -1)
                        )
                    }
                } else {
                    @Suppress("DEPRECATION")
                    if (isSingleTick) {
                        vibrator.vibrate(25L)
                    } else {
                        vibrator.vibrate(longArrayOf(0L, 65L, 55L, 95L), -1)
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    fun toggleDoubleShakeEnabled() {
        setDoubleShakeEnabled(!_doubleShakeEnabled.value)
    }

    fun setDoubleShakeEnabled(enabled: Boolean) {
        _doubleShakeEnabled.value = enabled
        prefs.edit().putBoolean("permanent_double_shake_enabled", enabled).apply()
        if (enabled) {
            triggerDoubleShakeVibration(isSingleTick = false)
        }
    }

    fun setShakeSensitivity(level: String) {
        _shakeSensitivity.value = level
        prefs.edit().putString("double_shake_sensitivity", level).apply()
        triggerDoubleShakeVibration(isSingleTick = true)
    }

    fun setForegroundVideoActive(active: Boolean) {
        _isForegroundVideoActive.value = active
    }

    fun loadMedia() {
        viewModelScope.launch {
            val videos = repository.getAvailableMedia(isVideo = true)
            val music = repository.getAvailableMedia(isVideo = false)
            _videoItems.value = videos
            _musicItems.value = music
            _allMediaItems.value = videos + music
            if (_syncedLyrics.value.isEmpty()) {
                val track = _nowPlaying.value ?: music.firstOrNull()
                if (track != null) {
                    _syncedLyrics.value = SyncedLyricsEngine.loadLyricsForTrack(getApplication(), track)
                }
            }
            repository.syncMediaLibrary(videos + music)
        }
    }

    fun saveCustomLyricsForCurrentTrack(rawLrc: String) {
        val current = _nowPlaying.value ?: _musicItems.value.firstOrNull() ?: return
        SyncedLyricsEngine.saveCustomLrcForTrack(getApplication(), current.id, rawLrc)
        _syncedLyrics.value = SyncedLyricsEngine.loadLyricsForTrack(getApplication(), current)
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
        _nowPlaying.value = item
        _syncedLyrics.value = SyncedLyricsEngine.loadLyricsForTrack(getApplication(), item)

        // Ensure notification delegate & foreground service start immediately
        MediaNotificationHelper.setControlDelegate(this@PlayerViewModel)
        AudioService.startServiceSafely(getApplication())

        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(item.path)
                prepareAsync()
                setOnPreparedListener { mp ->
                    mp.start()
                    _nowPlaying.value = item
                    _isPlaying.value = true
                    MediaNotificationHelper.setControlDelegate(this@PlayerViewModel)
                    AudioService.startServiceSafely(getApplication())
                    applySpeedAndPitch()
                    applyLoudnessBoost()
                    applyHardwareEqualizerAndBass()
                    syncMediaNotification()
                    startProgressTracker()

                    viewModelScope.launch {
                        val progress = repository.getProgress(item.id)
                        if (progress > 0 && progress < item.duration - 2000) {
                            mp.seekTo(progress.toInt())
                            _playbackPosition.value = progress
                            syncMediaNotification()
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
                _nowPlaying.value = item
                MediaNotificationHelper.setControlDelegate(this@PlayerViewModel)
                syncMediaNotification()
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
                    MediaNotificationHelper.setControlDelegate(this)
                    AudioService.startServiceSafely(getApplication())
                    startProgressTracker()
                }
                syncMediaNotification()
            } catch (_: Exception) {
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val dur = _nowPlaying.value?.duration?.takeIf { it > 0L }
            ?: (mediaPlayer?.duration?.toLong()?.coerceAtLeast(1L) ?: 180_000L)
        val clamped = positionMs.coerceIn(0L, dur)
        _playbackPosition.value = clamped
        mediaPlayer?.let { mp ->
            try {
                mp.seekTo(clamped.toInt())
            } catch (_: Exception) {
            }
        }
        syncMediaNotification()
        viewModelScope.launch {
            _nowPlaying.value?.let {
                repository.saveProgress(it.id, clamped)
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
        set200Boost(!_is200BoostEnabled.value)
    }

    fun set200Boost(enabled: Boolean) {
        _is200BoostEnabled.value = enabled
        _audioVolumePercent.value = if (enabled) 200 else readCurrentSystemVolumePercent()
        applyLoudnessBoost()
    }

    private fun applyLoudnessBoost() {
        applyLoudnessBoostCustomGain(if (_is200BoostEnabled.value) 1500 else 0)
    }

    private fun applyLoudnessBoostCustomGain(gainMb: Int) {
        val sessionId = mediaPlayer?.audioSessionId ?: return
        try {
            if (loudnessEnhancer == null) {
                loudnessEnhancer = LoudnessEnhancer(sessionId)
            }
            loudnessEnhancer?.setTargetGain(gainMb)
            loudnessEnhancer?.enabled = gainMb > 0
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
        val currentList = _musicItems.value.ifEmpty { _videoItems.value }
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
        val currentList = _musicItems.value.ifEmpty { _videoItems.value }
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

                val sec = currentPos / 1000L
                if (sec != lastNotificationSec) {
                    lastNotificationSec = sec
                    syncMediaNotification()
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

    private fun syncMediaNotification() {
        val current = _nowPlaying.value ?: return
        val dur = current.duration.takeIf { it > 0L } ?: (mediaPlayer?.duration?.toLong()?.coerceAtLeast(0L) ?: 0L)
        MediaNotificationHelper.showOrUpdateNotification(
            context = getApplication(),
            title = current.cleanTitle,
            subtitle = current.artist.ifBlank { if (current.isVideo) "Background Video Audio" else "Hybrid Audio Studio" },
            mediaPath = current.path,
            isVideo = current.isVideo,
            isPlaying = _isPlaying.value,
            positionMs = _playbackPosition.value,
            durationMs = dur,
            playbackSpeed = _playbackSpeed.value,
            contentIntentActivity = com.example.MainActivity::class.java
        )
    }

    override fun onNotificationPlayPause() {
        togglePlayPause()
    }

    override fun onNotificationRewind10s() {
        seekTo((_playbackPosition.value - 10_000L).coerceAtLeast(0L))
    }

    override fun onNotificationFastForward10s() {
        val dur = _nowPlaying.value?.duration?.takeIf { it > 0L } ?: Long.MAX_VALUE
        seekTo((_playbackPosition.value + 10_000L).coerceAtMost(dur))
    }

    override fun onNotificationNext() {
        playNext()
    }

    override fun onNotificationPrevious() {
        playPrevious()
    }

    override fun onNotificationSeekTo(positionMs: Long) {
        seekTo(positionMs)
    }

    override fun onNotificationStop() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (_: Exception) {
        }
        _isPlaying.value = false
        progressJob?.cancel()
        MediaNotificationHelper.cancelNotification(getApplication())
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

    // --- 7-Layer Security Fortress & Dual-PIN Private Vault Operations ---
    fun setupVaultPin(pin: String) {
        viewModelScope.launch {
            val pinHash = SecurityDefenseEngine.hashPinSha256(pin)
            repository.setVaultPin(pinHash)
            SecurityDefenseEngine.resetFailedPinAttempts(getApplication())
            _vaultLockoutSeconds.value = 0
            _vaultPinSet.value = true
            _isDecoyVault.value = false
            _vaultUnlocked.value = true
        }
    }

    fun verifyVaultPin(pin: String, onResult: (Boolean) -> Unit = {}): Boolean {
        val lockout = SecurityDefenseEngine.getLockoutRemainingSeconds(getApplication())
        if (lockout > 0) {
            _vaultLockoutSeconds.value = lockout
            startVaultLockoutCountdown()
            onResult(false)
            return false
        }
        if (FakeVaultHelper.isDecoyPin(pin)) {
            SecurityDefenseEngine.resetFailedPinAttempts(getApplication())
            _isDecoyVault.value = true
            _vaultUnlocked.value = true
            onResult(true)
            return true
        }
        val sha256Hash = SecurityDefenseEngine.hashPinSha256(pin)
        val legacyHash = pin.hashCode().toString()
        viewModelScope.launch {
            val storedHash = repository.getVaultPinHash()
            if (storedHash == sha256Hash || storedHash == legacyHash) {
                SecurityDefenseEngine.resetFailedPinAttempts(getApplication())
                _vaultLockoutSeconds.value = 0
                _isDecoyVault.value = false
                _vaultUnlocked.value = true
                onResult(true)
            } else {
                // Failed attempt! Log intruder & trigger lockout if >= 3 attempts
                triggerDoubleShakeVibration(isSingleTick = false)
                SecurityDefenseEngine.recordFailedPinAttempt(getApplication(), pin)
                _intruderLogs.value = SecurityDefenseEngine.getIntruderLogs(getApplication())
                val remainingLockout = SecurityDefenseEngine.getLockoutRemainingSeconds(getApplication())
                if (remainingLockout > 0) {
                    _vaultLockoutSeconds.value = remainingLockout
                    startVaultLockoutCountdown()
                }
                onResult(false)
            }
        }
        return true
    }

    private fun startVaultLockoutCountdown() {
        lockoutTimerJob?.cancel()
        lockoutTimerJob = viewModelScope.launch {
            while (true) {
                val rem = SecurityDefenseEngine.getLockoutRemainingSeconds(getApplication())
                _vaultLockoutSeconds.value = rem
                if (rem <= 0) break
                delay(1000L)
            }
        }
    }

    fun clearIntruderLogs() {
        SecurityDefenseEngine.clearIntruderLogs(getApplication())
        _intruderLogs.value = emptyList()
    }

    fun toggleGlobalFlagSecure() {
        val next = !_globalFlagSecure.value
        _globalFlagSecure.value = next
        prefs.edit().putBoolean("security_global_flag_secure", next).apply()
    }

    fun toggleAutoLockOnMinimize() {
        val next = !_autoLockOnMinimize.value
        _autoLockOnMinimize.value = next
        prefs.edit().putBoolean("security_auto_lock_minimize", next).apply()
    }

    fun togglePanicShakeVaultLock() {
        val next = !_panicShakeVaultLock.value
        _panicShakeVaultLock.value = next
        prefs.edit().putBoolean("security_panic_shake_lock", next).apply()
    }

    fun onAppMinimizedToBackground() {
        if (_autoLockOnMinimize.value && _vaultUnlocked.value) {
            lockVault()
        }
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

    // --- Personalization, 15 3D Themes & Companion Controls ---
    fun selectTheme(themeName: String) {
        _activeTheme.value = themeName
        prefs.edit().putString("saved_app_theme", themeName).apply()
    }

    fun select3DMusicTheme(theme3DName: String) {
        _active3DMusicTheme.value = theme3DName
        prefs.edit().putString("saved_3d_music_theme", theme3DName).apply()
    }

    fun cycleNext3DMusicTheme() {
        val list = Music3DThemeEngine.THEMES_3D
        val idx = list.indexOfFirst { it.name == _active3DMusicTheme.value }
        val next = list[(idx + 1).coerceAtLeast(0) % list.size]
        select3DMusicTheme(next.name)
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
        lockoutTimerJob?.cancel()
        try {
            sensorManager?.unregisterListener(this)
        } catch (_: Exception) {
        }
        try {
            loudnessEnhancer?.release()
            hardwareEqualizer?.release()
            hardwareBassBoost?.release()
        } catch (_: Exception) {
        }
        mediaPlayer?.release()
    }
}
