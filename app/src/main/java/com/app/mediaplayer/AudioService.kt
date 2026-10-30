package com.app.mediaplayer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

class AudioService : Service(), MediaNotificationHelper.ControlDelegate {

    companion object {
        const val ACTION_KEEP_FOREGROUND = "com.app.mediaplayer.ACTION_KEEP_FOREGROUND"

        @Volatile
        private var runningInstance: AudioService? = null

        fun startServiceSafely(context: Context) {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, AudioService::class.java).apply {
                action = ACTION_KEEP_FOREGROUND
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appCtx.startForegroundService(intent)
                } else {
                    appCtx.startService(intent)
                }
            } catch (_: Exception) {
                try {
                    appCtx.startService(intent)
                } catch (_: Exception) {
                }
            }
        }

        fun syncForegroundWithNotification(
            context: Context,
            notification: android.app.Notification,
            isPlaying: Boolean
        ) {
            val instance = runningInstance
            if (instance != null) {
                instance.promoteNotificationToForeground(notification)
            } else if (isPlaying) {
                startServiceSafely(context)
            }
        }
    }

    enum class RepeatMode {
        OFF, REPEAT_ALL, REPEAT_ONE
    }

    interface PlaybackListener {
        fun onTrackChanged(track: AudioModel?, index: Int)
        fun onPlaybackStateChanged(isPlaying: Boolean, positionMs: Long, durationMs: Long)
        fun onAudioEffectsUpdated()
    }

    inner class LocalBinder : Binder() {
        fun getService(): AudioService = this@AudioService
    }

    private val binder = LocalBinder()
    private var exoPlayer: ExoPlayer? = null
    private val listeners = CopyOnWriteArrayList<PlaybackListener>()

    private val queue = mutableListOf<AudioModel>()
    private var currentIndex: Int = -1
    var isShuffleEnabled: Boolean = false
        private set
    var repeatMode: RepeatMode = RepeatMode.REPEAT_ALL
        private set

    private var isForegroundActive = false
    private var lastNotificationSecond: Long = -1L

    // Hardware Audio Effects
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    var currentSpeed: Float = 1.0f
        private set
    var currentPitch: Float = 1.0f
        private set
    var loudnessGainMb: Int = 0
        private set
    var bassStrengthPermille: Short = 0
        private set
    var virtualizerStrengthPermille: Short = 0
        private set

    // Sleep Timer
    private val mainHandler = Handler(Looper.getMainLooper())
    private var sleepTimerEndRealtimeMs: Long = 0L
    private val sleepTimerRunnable = object : Runnable {
        override fun run() {
            val remaining = getSleepTimerRemainingMs()
            if (remaining <= 0L && sleepTimerEndRealtimeMs > 0L) {
                sleepTimerEndRealtimeMs = 0L
                pause()
            } else if (sleepTimerEndRealtimeMs > 0L) {
                mainHandler.postDelayed(this, 1000L)
            }
        }
    }

    private val progressPoller = object : Runnable {
        override fun run() {
            val p = exoPlayer
            if (p != null) {
                val pos = p.currentPosition.coerceAtLeast(0L)
                val dur = p.duration.takeIf { it > 0L } ?: (getCurrentTrack()?.duration ?: 0L)
                notifyPlaybackState(p.isPlaying, pos, dur)

                val currentSec = pos / 1000L
                if (p.isPlaying && currentSec != lastNotificationSecond) {
                    lastNotificationSecond = currentSec
                    updateForegroundMediaNotification(forceForeground = false)
                }
            }
            mainHandler.postDelayed(this, 400L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        runningInstance = this
        MediaNotificationHelper.ensureChannel(this)
        initializePlayer()
        mainHandler.post(progressPoller)
    }

    fun promoteNotificationToForeground(notification: android.app.Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    MediaNotificationHelper.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(MediaNotificationHelper.NOTIFICATION_ID, notification)
            }
            isForegroundActive = true
        } catch (_: Exception) {
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runningInstance = this
        promoteNotificationToForeground(MediaNotificationHelper.getLastOrDefaultNotification(this))
        val activeDelegate = MediaNotificationHelper.getControlDelegate() ?: this
        when (intent?.action) {
            MediaNotificationHelper.ACTION_MEDIA_PREV -> activeDelegate.onNotificationPrevious()
            MediaNotificationHelper.ACTION_MEDIA_REWIND_10 -> activeDelegate.onNotificationRewind10s()
            MediaNotificationHelper.ACTION_MEDIA_PLAY_PAUSE -> activeDelegate.onNotificationPlayPause()
            MediaNotificationHelper.ACTION_MEDIA_FAST_FORWARD_10 -> activeDelegate.onNotificationFastForward10s()
            MediaNotificationHelper.ACTION_MEDIA_NEXT -> activeDelegate.onNotificationNext()
            MediaNotificationHelper.ACTION_MEDIA_STOP -> activeDelegate.onNotificationStop()
        }
        return START_STICKY
    }

    private fun initializePlayer() {
        if (exoPlayer != null) return
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        exoPlayer = ExoPlayer.Builder(applicationContext)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        val dur = duration.takeIf { it > 0L } ?: (getCurrentTrack()?.duration ?: 0L)
                        notifyPlaybackState(isPlaying, currentPosition.coerceAtLeast(0L), dur)
                        if (isPlaying) {
                            MediaNotificationHelper.setControlDelegate(this@AudioService)
                        }
                        updateForegroundMediaNotification(forceForeground = isPlaying)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            attachAudioEffects(audioSessionId)
                            val dur = duration.takeIf { it > 0L } ?: (getCurrentTrack()?.duration ?: 0L)
                            notifyPlaybackState(isPlaying, currentPosition.coerceAtLeast(0L), dur)
                            updateForegroundMediaNotification(forceForeground = isPlaying)
                        } else if (playbackState == Player.STATE_ENDED) {
                            handleTrackEnded()
                        }
                    }
                })
            }
    }

    private fun updateForegroundMediaNotification(forceForeground: Boolean) {
        val track = getCurrentTrack() ?: return
        val p = exoPlayer ?: return
        val pos = p.currentPosition.coerceAtLeast(0L)
        val dur = p.duration.takeIf { it > 0L } ?: track.duration
        val lowerPath = track.path.lowercase()
        val isVideoFile = lowerPath.endsWith(".mp4") ||
            lowerPath.endsWith(".mkv") ||
            lowerPath.endsWith(".webm") ||
            lowerPath.endsWith(".avi") ||
            lowerPath.endsWith(".mov")

        val notification = MediaNotificationHelper.buildMediaNotification(
            context = this,
            title = track.title,
            subtitle = track.artist.ifBlank { if (isVideoFile) "Background Video Audio" else "Hybrid Audio Studio" },
            mediaPath = track.path,
            isVideo = isVideoFile,
            isPlaying = p.isPlaying,
            positionMs = pos,
            durationMs = dur,
            playbackSpeed = currentSpeed,
            contentIntentActivity = AudioPlayerActivity::class.java
        )

        if (forceForeground || p.isPlaying) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        MediaNotificationHelper.NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(MediaNotificationHelper.NOTIFICATION_ID, notification)
                }
                isForegroundActive = true
            } catch (_: Exception) {
                MediaNotificationHelper.showOrUpdateNotification(
                    context = this,
                    title = track.title,
                    subtitle = track.artist,
                    mediaPath = track.path,
                    isVideo = isVideoFile,
                    isPlaying = p.isPlaying,
                    positionMs = pos,
                    durationMs = dur,
                    playbackSpeed = currentSpeed,
                    contentIntentActivity = AudioPlayerActivity::class.java
                )
            }
        } else {
            MediaNotificationHelper.showOrUpdateNotification(
                context = this,
                title = track.title,
                subtitle = track.artist,
                mediaPath = track.path,
                isVideo = isVideoFile,
                isPlaying = false,
                positionMs = pos,
                durationMs = dur,
                playbackSpeed = currentSpeed,
                contentIntentActivity = AudioPlayerActivity::class.java
            )
        }
    }

    private fun attachAudioEffects(sessionId: Int) {
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId <= 0) return
        try {
            if (equalizer == null) {
                equalizer = Equalizer(0, sessionId).apply { enabled = true }
            }
        } catch (_: Exception) {
        }
        try {
            if (bassBoost == null) {
                bassBoost = BassBoost(0, sessionId).apply {
                    enabled = true
                    if (strengthSupported) setStrength(bassStrengthPermille)
                }
            }
        } catch (_: Exception) {
        }
        try {
            if (virtualizer == null) {
                virtualizer = Virtualizer(0, sessionId).apply {
                    enabled = true
                    if (strengthSupported) setStrength(virtualizerStrengthPermille)
                }
            }
        } catch (_: Exception) {
        }
        try {
            if (loudnessEnhancer == null) {
                loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                    enabled = true
                    setTargetGain(loudnessGainMb)
                }
            }
        } catch (_: Exception) {
        }
        listeners.forEach { it.onAudioEffectsUpdated() }
    }

    fun registerListener(listener: PlaybackListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
        listener.onTrackChanged(getCurrentTrack(), currentIndex)
        val p = exoPlayer
        if (p != null) {
            val dur = p.duration.takeIf { it > 0L } ?: (getCurrentTrack()?.duration ?: 0L)
            listener.onPlaybackStateChanged(p.isPlaying, p.currentPosition.coerceAtLeast(0L), dur)
        }
    }

    fun unregisterListener(listener: PlaybackListener) {
        listeners.remove(listener)
    }

    fun setQueueAndPlay(tracks: List<AudioModel>, startIndex: Int) {
        if (tracks.isEmpty()) return
        queue.clear()
        queue.addAll(tracks)
        currentIndex = startIndex.coerceIn(0, queue.lastIndex)
        playCurrentIndex()
    }

    fun playSingleTrack(track: AudioModel) {
        val existingIdx = queue.indexOfFirst { it.path == track.path }
        if (existingIdx >= 0) {
            currentIndex = existingIdx
        } else {
            queue.add(0, track)
            currentIndex = 0
        }
        playCurrentIndex()
    }

    private fun playCurrentIndex() {
        val track = getCurrentTrack() ?: return
        initializePlayer()
        MediaNotificationHelper.setControlDelegate(this)
        val p = exoPlayer ?: return
        val uri = if (File(track.path).exists()) {
            Uri.fromFile(File(track.path))
        } else {
            Uri.parse(track.path)
        }
        p.setMediaItem(MediaItem.fromUri(uri))
        p.prepare()
        p.playbackParameters = PlaybackParameters(currentSpeed, currentPitch)
        p.playWhenReady = true
        notifyTrackChanged(track, currentIndex)
        updateForegroundMediaNotification(forceForeground = true)
    }

    private fun handleTrackEnded() {
        when (repeatMode) {
            RepeatMode.REPEAT_ONE -> {
                exoPlayer?.seekTo(0L)
                exoPlayer?.playWhenReady = true
            }
            RepeatMode.REPEAT_ALL -> {
                playNext()
            }
            RepeatMode.OFF -> {
                if (currentIndex + 1 < queue.size) {
                    playNext()
                } else {
                    exoPlayer?.playWhenReady = false
                    updateForegroundMediaNotification(forceForeground = false)
                }
            }
        }
    }

    fun togglePlayPause() {
        val p = exoPlayer ?: return
        if (p.isPlaying) {
            p.pause()
        } else {
            if (p.playbackState == Player.STATE_ENDED) {
                p.seekTo(0L)
            }
            p.play()
        }
        updateForegroundMediaNotification(forceForeground = p.isPlaying)
    }

    fun play() {
        MediaNotificationHelper.setControlDelegate(this)
        exoPlayer?.play()
        updateForegroundMediaNotification(forceForeground = true)
    }

    fun pause() {
        exoPlayer?.pause()
        updateForegroundMediaNotification(forceForeground = false)
    }

    fun isPlaying(): Boolean = exoPlayer?.isPlaying == true

    fun getCurrentPositionMs(): Long = exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L

    fun getDurationMs(): Long {
        val d = exoPlayer?.duration ?: 0L
        return if (d > 0L) d else (getCurrentTrack()?.duration ?: 0L)
    }

    fun seekTo(positionMs: Long) {
        val p = exoPlayer ?: return
        val dur = getDurationMs()
        val target = if (dur > 0L) positionMs.coerceIn(0L, dur) else positionMs.coerceAtLeast(0L)
        p.seekTo(target)
        notifyPlaybackState(p.isPlaying, target, dur)
        updateForegroundMediaNotification(forceForeground = false)
    }

    fun seekByDelta(deltaMs: Long) {
        val target = (getCurrentPositionMs() + deltaMs).coerceAtLeast(0L)
        seekTo(target)
    }

    fun playNext() {
        if (queue.isEmpty()) return
        currentIndex = if (isShuffleEnabled && queue.size > 1) {
            Random.nextInt(queue.size)
        } else {
            (currentIndex + 1) % queue.size
        }
        playCurrentIndex()
    }

    fun playPrevious() {
        if (queue.isEmpty()) return
        if (getCurrentPositionMs() > 4000L) {
            seekTo(0L)
            return
        }
        currentIndex = if (currentIndex - 1 < 0) queue.lastIndex else currentIndex - 1
        playCurrentIndex()
    }

    // --- MediaNotificationHelper.ControlDelegate Implementation ---
    override fun onNotificationPlayPause() {
        togglePlayPause()
    }

    override fun onNotificationRewind10s() {
        seekByDelta(-10_000L)
    }

    override fun onNotificationFastForward10s() {
        seekByDelta(10_000L)
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
        pause()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {
        }
        isForegroundActive = false
        MediaNotificationHelper.cancelNotification(this)
    }

    fun toggleShuffle(): Boolean {
        isShuffleEnabled = !isShuffleEnabled
        return isShuffleEnabled
    }

    fun cycleRepeatMode(): RepeatMode {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.REPEAT_ALL
            RepeatMode.REPEAT_ALL -> RepeatMode.REPEAT_ONE
            RepeatMode.REPEAT_ONE -> RepeatMode.OFF
        }
        return repeatMode
    }

    fun setSpeedAndPitch(speed: Float, pitch: Float) {
        currentSpeed = speed.coerceIn(0.25f, 3.0f)
        currentPitch = pitch.coerceIn(0.5f, 2.0f)
        exoPlayer?.playbackParameters = PlaybackParameters(currentSpeed, currentPitch)
        updateForegroundMediaNotification(forceForeground = false)
    }

    // Equalizer & Hardware Audio Controls
    fun getEqualizerBandCount(): Int {
        return try {
            equalizer?.numberOfBands?.toInt() ?: 5
        } catch (_: Exception) {
            5
        }
    }

    fun getEqualizerBandLevelRange(): Pair<Short, Short> {
        return try {
            val range = equalizer?.bandLevelRange
            if (range != null && range.size >= 2) range[0] to range[1] else (-1500).toShort() to 1500.toShort()
        } catch (_: Exception) {
            (-1500).toShort() to 1500.toShort()
        }
    }

    fun getEqualizerCenterFreqHz(band: Int): Int {
        return try {
            val eq = equalizer
            if (eq != null && band < eq.numberOfBands) {
                eq.getCenterFreq(band.toShort()) / 1000
            } else {
                when (band) {
                    0 -> 60
                    1 -> 230
                    2 -> 910
                    3 -> 3600
                    else -> 14000
                }
            }
        } catch (_: Exception) {
            when (band) {
                0 -> 60
                1 -> 230
                2 -> 910
                3 -> 3600
                else -> 14000
            }
        }
    }

    fun getEqualizerBandLevel(band: Int): Short {
        return try {
            val eq = equalizer
            if (eq != null && band < eq.numberOfBands) {
                eq.getBandLevel(band.toShort())
            } else {
                0
            }
        } catch (_: Exception) {
            0
        }
    }

    fun setEqualizerBandLevel(band: Int, levelMb: Short) {
        try {
            val eq = equalizer
            if (eq != null && band < eq.numberOfBands) {
                eq.setBandLevel(band.toShort(), levelMb)
            }
        } catch (_: Exception) {
        }
    }

    fun setBassBoostStrength(strengthPermille: Int) {
        bassStrengthPermille = strengthPermille.coerceIn(0, 1000).toShort()
        try {
            bassBoost?.let {
                if (it.strengthSupported) it.setStrength(bassStrengthPermille)
            }
        } catch (_: Exception) {
        }
    }

    fun setVirtualizerStrength(strengthPermille: Int) {
        virtualizerStrengthPermille = strengthPermille.coerceIn(0, 1000).toShort()
        try {
            virtualizer?.let {
                if (it.strengthSupported) it.setStrength(virtualizerStrengthPermille)
            }
        } catch (_: Exception) {
        }
    }

    fun setLoudnessBoostGainMb(gainMb: Int) {
        loudnessGainMb = gainMb.coerceIn(0, 1500)
        try {
            loudnessEnhancer?.setTargetGain(loudnessGainMb)
        } catch (_: Exception) {
        }
    }

    fun setSleepTimerMinutes(minutes: Int) {
        mainHandler.removeCallbacks(sleepTimerRunnable)
        if (minutes <= 0) {
            sleepTimerEndRealtimeMs = 0L
        } else {
            sleepTimerEndRealtimeMs = System.currentTimeMillis() + minutes * 60_000L
            mainHandler.post(sleepTimerRunnable)
        }
    }

    fun getSleepTimerRemainingMs(): Long {
        if (sleepTimerEndRealtimeMs <= 0L) return 0L
        return (sleepTimerEndRealtimeMs - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun getCurrentTrack(): AudioModel? {
        return if (currentIndex in queue.indices) queue[currentIndex] else null
    }

    fun getQueue(): List<AudioModel> = queue.toList()

    private fun notifyTrackChanged(track: AudioModel?, index: Int) {
        listeners.forEach { it.onTrackChanged(track, index) }
    }

    private fun notifyPlaybackState(isPlaying: Boolean, positionMs: Long, durationMs: Long) {
        listeners.forEach { it.onPlaybackStateChanged(isPlaying, positionMs, durationMs) }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        if (runningInstance === this) {
            runningInstance = null
        }
        mainHandler.removeCallbacksAndMessages(null)
        if (MediaNotificationHelper.getControlDelegate() === this) {
            MediaNotificationHelper.setControlDelegate(null)
        }
        MediaNotificationHelper.cancelNotification(this)
        try {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
            loudnessEnhancer?.release()
        } catch (_: Exception) {
        }
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }
}
