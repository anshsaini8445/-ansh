package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.database.ContentObserver
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
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.MediaItem
import com.example.data.model.PlaylistEntity
import com.example.data.repository.MediaRepository
import com.example.data.scanner.DeepMediaScanner
import com.example.helpers.AudioService
import com.example.helpers.FakeVaultHelper
import com.example.helpers.IntruderLogEntry
import com.example.helpers.MediaNotificationHelper
import com.example.helpers.Music3DThemeEngine
import com.example.helpers.PermanentDeleteHelper
import com.example.helpers.SecurityDefenseEngine
import com.example.helpers.SyncedLyricLine
import com.example.helpers.SyncedLyricsEngine
import com.example.ui.persona.DemographicMode
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

    // --- Universal Demographic & Global Experience Mode (Kids, Seniors, Youth, Bharat, Global) ---
    private val _demographicMode = MutableStateFlow(DemographicMode.loadSavedMode(application))
    val demographicMode: StateFlow<DemographicMode> = _demographicMode.asStateFlow()

    fun selectDemographicMode(mode: DemographicMode) {
        _demographicMode.value = mode
        DemographicMode.saveMode(getApplication(), mode)
        selectTheme(mode.defaultThemeName)
        if (mode == DemographicMode.KIDS_MAGIC) {
            _companionEnabled.value = true
            _penguinsOnlyMode.value = true
            prefs.edit().putBoolean("companion_penguins_only_mode", true).apply()
        }
    }

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

    // --- Video Sort & View Mode ---
    private val _sortCriteria = MutableStateFlow("Date") // "Date", "Name", "Size", "Length"
    val sortCriteria: StateFlow<String> = _sortCriteria.asStateFlow()

    private val _sortNewToOld = MutableStateFlow(true)
    val sortNewToOld: StateFlow<Boolean> = _sortNewToOld.asStateFlow()

    private val _isVideoGridView = MutableStateFlow(false)
    val isVideoGridView: StateFlow<Boolean> = _isVideoGridView.asStateFlow()

    // --- Audio Library Sort Order ---
    // "NewToOld", "OldToNew", "AtoZ", "ZtoA", "ShortToLong", "LongToShort"
    private val _audioSortOrder = MutableStateFlow(
        prefs.getString("saved_audio_sort_order", "NewToOld") ?: "NewToOld"
    )
    val audioSortOrder: StateFlow<String> = _audioSortOrder.asStateFlow()

    fun setAudioSortOrder(order: String) {
        _audioSortOrder.value = order
        prefs.edit().putString("saved_audio_sort_order", order).apply()
    }

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

    // --- Double-Shake (2x Shake to Next Track + Vibration) Engine (OFF by default until user enables) ---
    private val _doubleShakeEnabled = MutableStateFlow(
        prefs.getBoolean("permanent_double_shake_enabled_v2", false)
    )
    val doubleShakeEnabled: StateFlow<Boolean> = _doubleShakeEnabled.asStateFlow()

    private val _shakeSensitivity = MutableStateFlow(
        prefs.getString("double_shake_sensitivity", "Normal") ?: "Normal"
    )
    val shakeSensitivity: StateFlow<String> = _shakeSensitivity.asStateFlow()

    private val _isForegroundVideoActive = MutableStateFlow(false)
    val isForegroundVideoActive: StateFlow<Boolean> = _isForegroundVideoActive.asStateFlow()

    private var firstShakeTimestampMs: Long = 0L
    private var firstShakeDirectionSign: Int = 0
    private var lastDoubleShakeTriggerMs: Long = 0L
    private val gravityFilter = FloatArray(3) { 0f }
    private var gravityInitialized = false

    // --- A-B Repeat ---
    private val _abPointAMs = MutableStateFlow<Long?>(null)
    val abPointAMs: StateFlow<Long?> = _abPointAMs.asStateFlow()

    private val _abPointBMs = MutableStateFlow<Long?>(null)
    val abPointBMs: StateFlow<Long?> = _abPointBMs.asStateFlow()

    // --- Sleep Timer ---
    private val _sleepTimerMinutes = MutableStateFlow(0)
    val sleepTimerMinutes: StateFlow<Int> = _sleepTimerMinutes.asStateFlow()

    // --- True Non-Repeating Fisher-Yates Shuffle & Repeat Mode ---
    private val _isShuffleMode = MutableStateFlow(
        prefs.getBoolean("music_shuffle_mode", false)
    )
    val isShuffleMode: StateFlow<Boolean> = _isShuffleMode.asStateFlow()

    // 0 = Order List, 1 = Repeat All, 2 = Repeat One
    private val _repeatMode = MutableStateFlow(
        prefs.getInt("music_repeat_mode", 1)
    )
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val unplayedShuffleIds = mutableListOf<String>()
    private val playedHistoryIds = ArrayDeque<String>()

    // --- Permanent 3D Visualizer ON/OFF Switch ---
    private val _visualizer3DEnabled = MutableStateFlow(
        prefs.getBoolean("music_3d_visualizer_enabled", false)
    )
    val visualizer3DEnabled: StateFlow<Boolean> = _visualizer3DEnabled.asStateFlow()

    fun toggleVisualizer3DEnabled() {
        setVisualizer3DEnabled(!_visualizer3DEnabled.value)
    }

    fun setVisualizer3DEnabled(enabled: Boolean) {
        _visualizer3DEnabled.value = enabled
        prefs.edit().putBoolean("music_3d_visualizer_enabled", enabled).apply()
    }

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

    private val _selectedLanguage = MutableStateFlow(
        prefs.getString(
            "saved_app_language",
            if (java.util.Locale.getDefault().language == "hi") "Hindi (हिन्दी)" else "English (Default)"
        ) ?: "English (Default)"
    )
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private val _oneHandMode = MutableStateFlow(false)
    val oneHandMode: StateFlow<Boolean> = _oneHandMode.asStateFlow()

    // --- 3D Companion Mascot (3D Fluffy Cassette Puppies & Teddy Bears + Studio Lion/Bunny/Penguins) ---
    private val _companionEnabled = MutableStateFlow(
        prefs.getBoolean("saved_companion_enabled_v3", true)
    )
    val companionEnabled: StateFlow<Boolean> = _companionEnabled.asStateFlow()

    private val _companionBreed = MutableStateFlow(
        prefs.getString("saved_companion_breed_v3", "Golden Lion DJ 🦁") ?: "Golden Lion DJ 🦁"
    )
    val companionBreed: StateFlow<String> = _companionBreed.asStateFlow()

    private val _companionSizeDp = MutableStateFlow(
        prefs.getInt("saved_companion_size_dp_v2", 88)
    )
    val companionSizeDp: StateFlow<Int> = _companionSizeDp.asStateFlow()

    private val _penguinsOnlyMode = MutableStateFlow(
        prefs.getBoolean("companion_penguins_only_mode_v3", false)
    )
    val penguinsOnlyMode: StateFlow<Boolean> = _penguinsOnlyMode.asStateFlow()

    // --- Engine Switches ---
    private val _hybrid8kDecoder = MutableStateFlow(true)
    val hybrid8kDecoder: StateFlow<Boolean> = _hybrid8kDecoder.asStateFlow()

    private val _hdrCcBadges = MutableStateFlow(true)
    val hdrCcBadges: StateFlow<Boolean> = _hdrCcBadges.asStateFlow()

    // --- Smart Search Recent History (100% Real User Searches, Zero Dummy Data) ---
    private val _recentSearches = MutableStateFlow<List<String>>(
        prefs.getString("real_recent_searches", "")
            ?.split("|")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
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

    private val mediaStoreObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            loadMedia()
        }
    }

    init {
        MediaNotificationHelper.setControlDelegate(this)
        loadMedia()
        checkVaultPinStatus()
        registerDoubleShakeSensor()
        registerMediaStoreObserver()
        if (_vaultLockoutSeconds.value > 0) {
            startVaultLockoutCountdown()
        }
        _musicItems.value.firstOrNull()?.let { firstTrack ->
            _syncedLyrics.value = SyncedLyricsEngine.loadLyricsForTrack(getApplication(), firstTrack)
        }
    }

    private fun registerMediaStoreObserver() {
        try {
            val resolver = getApplication<Application>().contentResolver
            resolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                mediaStoreObserver
            )
            resolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                mediaStoreObserver
            )
        } catch (_: Exception) {
        }
    }

    fun importPickedMedia(uris: List<Uri>, isVideo: Boolean) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            DeepMediaScanner.addPickedMediaUris(getApplication(), uris, isVideo)
            loadMedia()
            Toast.makeText(
                getApplication(),
                "Added ${uris.size} real ${if (isVideo) "video(s)" else "audio track(s)"} to library",
                Toast.LENGTH_SHORT
            ).show()
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

    // --- Hardware Accelerometer Double-Shake Registration & Anti-Pocket / Anti-Walking Filter ---
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

        if (!gravityInitialized) {
            gravityFilter[0] = x
            gravityFilter[1] = y
            gravityFilter[2] = z
            gravityInitialized = true
        }
        val alpha = 0.85f
        gravityFilter[0] = alpha * gravityFilter[0] + (1f - alpha) * x
        gravityFilter[1] = alpha * gravityFilter[1] + (1f - alpha) * y
        gravityFilter[2] = alpha * gravityFilter[2] + (1f - alpha) * z

        val linearX = x - gravityFilter[0]
        val linearY = y - gravityFilter[1]
        val linearZ = z - gravityFilter[2]
        val linearMagnitude = sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ)

        // Anti-Walking / Anti-Pocket Filter:
        // Requires deliberate lateral wrist snap (X-axis dominant over Y-axis vertical walking bounce)
        // plus a sharp direction reversal on the second shake.
        val lateralThreshold = when (_shakeSensitivity.value) {
            "High" -> 12.8f
            "Firm" -> 18.5f
            else -> 15.5f
        }

        val absX = kotlin.math.abs(linearX)
        val absY = kotlin.math.abs(linearY)
        val isIntentionalLateralSnap = absX > lateralThreshold && absX > absY * 1.15f && linearMagnitude > lateralThreshold

        if (isIntentionalLateralSnap) {
            val now = System.currentTimeMillis()
            val currentSign = if (linearX >= 0f) 1 else -1

            // 1. Check Emergency Panic Shake inside Unlocked Privacy Vault
            if (_vaultUnlocked.value && _panicShakeVaultLock.value && now - lastDoubleShakeTriggerMs > 1100L) {
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

            // 2. Check Double-Shake to Skip Song (Only when explicitly enabled by user & not in foreground video)
            if (!_doubleShakeEnabled.value || _isForegroundVideoActive.value || _nowPlaying.value == null) return
            if (now - lastDoubleShakeTriggerMs < 1300L) return

            val gap = now - firstShakeTimestampMs
            if (gap in 160L..680L && firstShakeDirectionSign != 0 && currentSign != firstShakeDirectionSign) {
                firstShakeTimestampMs = 0L
                firstShakeDirectionSign = 0
                lastDoubleShakeTriggerMs = now
                executeDoubleShakeNextSong()
            } else if (gap > 680L || firstShakeTimestampMs == 0L) {
                firstShakeTimestampMs = now
                firstShakeDirectionSign = currentSign
                triggerDoubleShakeVibration(isSingleTick = true)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun executeDoubleShakeNextSong() {
        triggerDoubleShakeVibration(isSingleTick = false)
        playNext(fromAutoCompletion = false)
        val trackTitle = _nowPlaying.value?.cleanTitle ?: "Next Track"
        Toast.makeText(
            getApplication(),
            "📳 2x Shake -> $trackTitle",
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
        prefs.edit().putBoolean("permanent_double_shake_enabled_v2", enabled).apply()
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
        if (!active) {
            MediaNotificationHelper.setControlDelegate(this)
            if (_nowPlaying.value != null) {
                syncMediaNotification()
            }
        }
    }

    fun loadMedia() {
        viewModelScope.launch {
            try {
                // Stage 1: Emit fast MediaStore results (<80ms) so videos/songs appear immediately after permission grant
                val fastVideos = DeepMediaScanner.scanFastMediaStoreFirst(getApplication(), isVideo = true).distinctBy { it.id }
                val fastMusic = DeepMediaScanner.scanFastMediaStoreFirst(getApplication(), isVideo = false).distinctBy { it.id }
                if (fastVideos.isNotEmpty() || _videoItems.value.isEmpty()) {
                    _videoItems.value = fastVideos
                }
                if (fastMusic.isNotEmpty() || _musicItems.value.isEmpty()) {
                    _musicItems.value = fastMusic
                }
                _allMediaItems.value = (_videoItems.value + _musicItems.value).distinctBy { it.id }
                restoreLastPlayedMusicSessionIfIdle(_allMediaItems.value)
            } catch (_: Throwable) {
            }

            try {
                // Stage 2: Full deep directory + WhatsApp Status + partial download scan
                val videos = repository.getAvailableMedia(isVideo = true).distinctBy { it.id }
                val music = repository.getAvailableMedia(isVideo = false).distinctBy { it.id }
                _videoItems.value = videos
                _musicItems.value = music
                val combined = (videos + music).distinctBy { it.id }
                _allMediaItems.value = combined
                restoreLastPlayedMusicSessionIfIdle(combined)
                if (_syncedLyrics.value.isEmpty()) {
                    val track = _nowPlaying.value ?: music.firstOrNull()
                    if (track != null) {
                        _syncedLyrics.value = SyncedLyricsEngine.loadLyricsForTrack(getApplication(), track)
                    }
                }
                repository.syncMediaLibrary(combined)
            } catch (_: Throwable) {
            }
        }
    }

    private fun restoreLastPlayedMusicSessionIfIdle(pool: List<MediaItem>) {
        if (_nowPlaying.value != null || pool.isEmpty()) return
        val savedId = prefs.getString("last_played_music_id", null) ?: return
        val match = pool.firstOrNull { it.id == savedId } ?: return
        val savedPos = prefs.getLong("last_played_music_pos_ms", 0L).coerceAtLeast(0L)
        _nowPlaying.value = match
        if (savedPos > 0L && (match.duration <= 0L || savedPos < match.duration - 1500L)) {
            _playbackPosition.value = savedPos
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
        val top10 = updated.take(10)
        _recentSearches.value = top10
        prefs.edit().putString("real_recent_searches", top10.joinToString("|")).apply()
    }

    fun clearRecentSearches() {
        _recentSearches.value = emptyList()
        prefs.edit().remove("real_recent_searches").apply()
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

    fun deleteBatchPermanently(items: List<MediaItem>) {
        PermanentDeleteHelper.deleteBatchPermanently(getApplication(), items)
        loadMedia()
    }

    private fun checkVaultPinStatus() {
        viewModelScope.launch {
            val pinHash = repository.getVaultPinHash()
            _vaultPinSet.value = pinHash != null
        }
    }

    // --- Music & Background Audio Player Operations ---
    fun playMusic(item: MediaItem, recordHistory: Boolean = true) {
        progressJob?.cancel()
        val previousItem = _nowPlaying.value
        if (recordHistory && previousItem != null && previousItem.id != item.id) {
            if (playedHistoryIds.lastOrNull() != previousItem.id) {
                playedHistoryIds.addLast(previousItem.id)
                if (playedHistoryIds.size > 200) {
                    playedHistoryIds.removeFirst()
                }
            }
        }
        unplayedShuffleIds.remove(item.id)

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
        prefs.edit().putString("last_played_music_id", item.id).apply()
        _syncedLyrics.value = SyncedLyricsEngine.loadLyricsForTrack(getApplication(), item)

        // Start foreground service ONLY when audio playback is explicitly requested
        MediaNotificationHelper.setControlDelegate(this@PlayerViewModel)
        AudioService.startServiceSafely(getApplication())

        mediaPlayer = MediaPlayer().apply {
            try {
                if (
                    item.path.startsWith("content://") ||
                    item.path.startsWith("http://") ||
                    item.path.startsWith("https://") ||
                    item.path.startsWith("android.resource://")
                ) {
                    setDataSource(getApplication(), Uri.parse(item.path))
                } else {
                    val file = java.io.File(item.path)
                    if (file.exists()) {
                        setDataSource(item.path)
                    } else {
                        setDataSource(getApplication(), Uri.parse(item.path))
                    }
                }
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
                    playNext(fromAutoCompletion = true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _nowPlaying.value = item
                MediaNotificationHelper.setControlDelegate(this@PlayerViewModel)
                syncMediaNotification()
            }
        }
    }

    private fun getActiveAudioPool(): List<MediaItem> {
        val current = _nowPlaying.value
        return if (current?.isVideo == true) {
            (_musicItems.value + _videoItems.value).distinctBy { it.id }
        } else {
            _musicItems.value.ifEmpty { _videoItems.value }
        }
    }

    private fun refillShuffleDeck(pool: List<MediaItem>, excludeId: String?) {
        unplayedShuffleIds.clear()
        val ids = pool.map { it.id }.filter { it != excludeId }.shuffled()
        unplayedShuffleIds.addAll(ids)
    }

    fun shufflePlayMusic() {
        val list = getActiveAudioPool()
        if (list.isNotEmpty()) {
            _isShuffleMode.value = true
            prefs.edit().putBoolean("music_shuffle_mode", true).apply()
            refillShuffleDeck(list, _nowPlaying.value?.id)
            val nextId = unplayedShuffleIds.removeFirstOrNull()
            val target = list.firstOrNull { it.id == nextId } ?: list.random()
            playMusic(target)
        }
    }

    fun toggleShuffleMode() {
        val next = !_isShuffleMode.value
        _isShuffleMode.value = next
        prefs.edit().putBoolean("music_shuffle_mode", next).apply()
        if (next) {
            refillShuffleDeck(getActiveAudioPool(), _nowPlaying.value?.id)
        } else {
            unplayedShuffleIds.clear()
        }
        syncMediaNotification()
    }

    fun cycleRepeatMode() {
        val next = (_repeatMode.value + 1) % 3
        _repeatMode.value = next
        prefs.edit().putInt("music_repeat_mode", next).apply()
        syncMediaNotification()
    }

    fun togglePlayPause() {
        val mp = mediaPlayer
        if (mp != null) {
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
        } else {
            val current = _nowPlaying.value ?: _musicItems.value.firstOrNull()
            if (current != null) {
                playMusic(current)
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

    fun playNext(fromAutoCompletion: Boolean = false) {
        val currentList = getActiveAudioPool()
        if (currentList.isEmpty()) return
        val current = _nowPlaying.value

        // Repeat One mode: replay current song when track finishes naturally
        if (fromAutoCompletion && _repeatMode.value == 2 && current != null) {
            playMusic(current, recordHistory = false)
            return
        }

        // True Fisher-Yates Non-Repeating Shuffle Queue
        if (_isShuffleMode.value && currentList.size > 1) {
            val validIds = currentList.map { it.id }.toSet()
            unplayedShuffleIds.retainAll(validIds)
            unplayedShuffleIds.remove(current?.id)

            if (unplayedShuffleIds.isEmpty()) {
                if (fromAutoCompletion && _repeatMode.value == 0) {
                    // Order mode finished all tracks in deck
                    syncMediaNotification()
                    return
                }
                refillShuffleDeck(currentList, current?.id)
            }

            val nextId = unplayedShuffleIds.removeFirstOrNull()
            val nextTrack = currentList.firstOrNull { it.id == nextId }
                ?: currentList.firstOrNull { it.id != current?.id }
                ?: currentList.first()
            playMusic(nextTrack, recordHistory = true)
            return
        }

        val currentIndex = currentList.indexOfFirst { it.id == current?.id }
        if (currentIndex != -1 && currentIndex < currentList.size - 1) {
            playMusic(currentList[currentIndex + 1], recordHistory = true)
        } else {
            if (fromAutoCompletion && _repeatMode.value == 0) {
                // Stop at end of list when Repeat is Off (Order Mode)
                syncMediaNotification()
            } else {
                playMusic(currentList.first(), recordHistory = true)
            }
        }
    }

    fun playPrevious() {
        val currentList = getActiveAudioPool()
        if (currentList.isEmpty()) return

        // If playback is > 4 seconds in, restart current song first on Prev tap? No, user expects immediate previous track,
        // and in shuffle mode step back through actual played history!
        while (playedHistoryIds.isNotEmpty()) {
            val prevId = playedHistoryIds.removeLast()
            val prevItem = currentList.firstOrNull { it.id == prevId }
            if (prevItem != null && prevItem.id != _nowPlaying.value?.id) {
                playMusic(prevItem, recordHistory = false)
                return
            }
        }

        val currentIndex = currentList.indexOfFirst { it.id == _nowPlaying.value?.id }
        if (currentIndex > 0) {
            playMusic(currentList[currentIndex - 1], recordHistory = false)
        } else {
            playMusic(currentList.last(), recordHistory = false)
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
                    prefs.edit()
                        .putString("last_played_music_id", it.id)
                        .putLong("last_played_music_pos_ms", currentPos)
                        .apply()
                    repository.saveProgress(it.id, currentPos)
                }

                val sec = currentPos / 1000L
                if (sec != lastNotificationSec) {
                    lastNotificationSec = sec
                    syncMediaNotification()
                }

                if (_visualizer3DEnabled.value) {
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
            subtitle = current.artist.ifBlank { if (current.isVideo) "Video Audio" else "Hybrid Audio" },
            mediaPath = current.path,
            isVideo = current.isVideo,
            isPlaying = _isPlaying.value,
            positionMs = _playbackPosition.value,
            durationMs = dur,
            playbackSpeed = _playbackSpeed.value,
            isShuffle = _isShuffleMode.value,
            repeatMode = _repeatMode.value,
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
        playNext(fromAutoCompletion = false)
    }

    override fun onNotificationPrevious() {
        playPrevious()
    }

    override fun onNotificationSeekTo(positionMs: Long) {
        seekTo(positionMs)
    }

    override fun onNotificationToggleShuffle() {
        toggleShuffleMode()
    }

    override fun onNotificationToggleRepeat() {
        cycleRepeatMode()
    }

    override fun onNotificationStop() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        _isPlaying.value = false
        progressJob?.cancel()
        MediaNotificationHelper.cancelNotification(getApplication())
        AudioService.stopServiceCompletely(getApplication())
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
        prefs.edit().putString("saved_app_language", lang).apply()
    }

    fun toggleOneHandMode() {
        _oneHandMode.value = !_oneHandMode.value
    }

    fun setCompanionEnabled(enabled: Boolean) {
        _companionEnabled.value = enabled
        prefs.edit().putBoolean("saved_companion_enabled_v3", enabled).apply()
    }

    fun setCompanionSize(sizeDp: Int) {
        val clamped = sizeDp.coerceIn(56, 140)
        _companionSizeDp.value = clamped
        prefs.edit().putInt("saved_companion_size_dp_v2", clamped).apply()
    }

    fun selectCompanionBreed(breedName: String) {
        _companionBreed.value = breedName
        _companionEnabled.value = true
        if (!breedName.contains("Penguin", ignoreCase = true)) {
            _penguinsOnlyMode.value = false
            prefs.edit().putBoolean("companion_penguins_only_mode_v3", false).apply()
        }
        prefs.edit()
            .putString("saved_companion_breed_v3", breedName)
            .putString("saved_companion_breed", breedName)
            .putBoolean("saved_companion_enabled_v3", true)
            .apply()
    }

    fun togglePenguinsOnlyMode() {
        val next = !_penguinsOnlyMode.value
        _penguinsOnlyMode.value = next
        _companionEnabled.value = true
        prefs.edit()
            .putBoolean("companion_penguins_only_mode_v3", next)
            .putBoolean("saved_companion_enabled_v3", true)
            .apply()
    }

    fun setPenguinsOnlyMode(enabled: Boolean) {
        _penguinsOnlyMode.value = enabled
        if (enabled) _companionEnabled.value = true
        prefs.edit().putBoolean("companion_penguins_only_mode_v3", enabled).apply()
    }

    fun toggleHybrid8kDecoder() {
        _hybrid8kDecoder.value = !_hybrid8kDecoder.value
    }

    fun toggleHdrCcBadges() {
        _hdrCcBadges.value = !_hdrCcBadges.value
    }

    fun saveVideoProgress(mediaId: String, positionMs: Long) {
        if (positionMs > 0L) {
            prefs.edit()
                .putString("last_watched_video_id", mediaId)
                .putLong("video_pos_$mediaId", positionMs)
                .apply()
        }
        viewModelScope.launch {
            repository.saveProgress(mediaId, positionMs)
        }
    }

    suspend fun getVideoProgress(mediaId: String): Long {
        val dbPos = repository.getProgress(mediaId)
        val fastPrefPos = prefs.getLong("video_pos_$mediaId", 0L)
        return maxOf(dbPos, fastPrefPos)
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
            getApplication<Application>().contentResolver.unregisterContentObserver(mediaStoreObserver)
        } catch (_: Exception) {
        }
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
