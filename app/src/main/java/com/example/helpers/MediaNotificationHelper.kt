package com.example.helpers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.MediaMetadataRetriever
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.view.KeyEvent
import com.example.R
import java.io.File
import java.lang.ref.WeakReference

/**
 * Full-Featured MediaSession & Notification Panel Controller for Hybrid Player:
 * - Never auto-starts in the background on app launch; activates ONLY when a song is played
 *   or when the user starts Background Video Audio ("BG Audio").
 * - 5-Button Notification Panel Layout:
 *   1. Left ("Ulte Haath"): Shuffle (🔀)
 *   2. Previous (⏮)
 *   3. Play / Pause (▶ / ⏸)
 *   4. Next (⏭)
 *   5. Right ("Seedhe Haath"): Repeat (🔁 / 🔂 / Order)
 * - Swipe-to-Dismiss Lifecycle:
 *   - While playing (`isPlaying == true`): Foreground service + `setOngoing(true)` prevents accidental swipe cuts.
 *   - When paused (`isPlaying == false`): `stopForeground(STOP_FOREGROUND_DETACH)` + `setOngoing(false)`
 *     allows swiping the notification away to cleanly dismiss it and stop background playback completely.
 */
object MediaNotificationHelper {

    const val CHANNEL_ID = "hybrid_playback_channel_v3"
    const val NOTIFICATION_ID = 1001

    const val ACTION_MEDIA_SHUFFLE = "com.example.action.MEDIA_SHUFFLE"
    const val ACTION_MEDIA_PREV = "com.example.action.MEDIA_PREV"
    const val ACTION_MEDIA_REWIND_10S = "com.example.action.MEDIA_REWIND_10S"
    const val ACTION_MEDIA_PLAY_PAUSE = "com.example.action.MEDIA_PLAY_PAUSE"
    const val ACTION_MEDIA_FFWD_10S = "com.example.action.MEDIA_FFWD_10S"
    const val ACTION_MEDIA_NEXT = "com.example.action.MEDIA_NEXT"
    const val ACTION_MEDIA_REPEAT = "com.example.action.MEDIA_REPEAT"
    const val ACTION_MEDIA_STOP = "com.example.action.MEDIA_STOP"

    interface ControlDelegate {
        fun onNotificationPlayPause()
        fun onNotificationRewind10s()
        fun onNotificationFastForward10s()
        fun onNotificationNext()
        fun onNotificationPrevious()
        fun onNotificationSeekTo(positionMs: Long)
        fun onNotificationStop()
        fun onNotificationToggleShuffle() {}
        fun onNotificationToggleRepeat() {}
    }

    private var delegateRef: WeakReference<ControlDelegate>? = null
    private var mediaSession: MediaSession? = null
    private var cachedArtPath: String? = null
    private var cachedArtBitmap: Bitmap? = null

    fun setControlDelegate(delegate: ControlDelegate?) {
        delegateRef = if (delegate != null) WeakReference(delegate) else null
    }

    fun getControlDelegate(): ControlDelegate? {
        return delegateRef?.get()
    }

    fun dispatchAction(context: Context, action: String?) {
        val delegate = delegateRef?.get() ?: return
        when (action) {
            ACTION_MEDIA_SHUFFLE -> delegate.onNotificationToggleShuffle()
            ACTION_MEDIA_PLAY_PAUSE -> delegate.onNotificationPlayPause()
            ACTION_MEDIA_REWIND_10S -> delegate.onNotificationRewind10s()
            ACTION_MEDIA_FFWD_10S -> delegate.onNotificationFastForward10s()
            ACTION_MEDIA_NEXT -> delegate.onNotificationNext()
            ACTION_MEDIA_PREV -> delegate.onNotificationPrevious()
            ACTION_MEDIA_REPEAT -> delegate.onNotificationToggleRepeat()
            ACTION_MEDIA_STOP -> {
                delegate.onNotificationStop()
                cancelNotification(context)
                AudioService.stopServiceCompletely(context)
            }
        }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Hybrid Media Playback Controls",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Interactive Lock-Screen & Status-Bar Playback Controls"
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun getOrCreateMediaSession(context: Context): MediaSession {
        val existing = mediaSession
        if (existing != null) return existing

        val session = MediaSession(context.applicationContext, "HybridPlayerMediaSession").apply {
            isActive = true
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    delegateRef?.get()?.onNotificationPlayPause()
                }

                override fun onPause() {
                    delegateRef?.get()?.onNotificationPlayPause()
                }

                override fun onSkipToNext() {
                    delegateRef?.get()?.onNotificationNext()
                }

                override fun onSkipToPrevious() {
                    delegateRef?.get()?.onNotificationPrevious()
                }

                override fun onFastForward() {
                    delegateRef?.get()?.onNotificationFastForward10s()
                }

                override fun onRewind() {
                    delegateRef?.get()?.onNotificationRewind10s()
                }

                override fun onSeekTo(pos: Long) {
                    delegateRef?.get()?.onNotificationSeekTo(pos)
                }

                override fun onStop() {
                    delegateRef?.get()?.onNotificationStop()
                    cancelNotification(context.applicationContext)
                    AudioService.stopServiceCompletely(context.applicationContext)
                }

                override fun onCustomAction(action: String, extras: Bundle?) {
                    dispatchAction(context.applicationContext, action)
                }

                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val keyEvent: KeyEvent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    }
                    if (keyEvent?.action == KeyEvent.ACTION_DOWN) {
                        when (keyEvent.keyCode) {
                            KeyEvent.KEYCODE_MEDIA_PLAY,
                            KeyEvent.KEYCODE_MEDIA_PAUSE,
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                            KeyEvent.KEYCODE_HEADSETHOOK -> {
                                delegateRef?.get()?.onNotificationPlayPause()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                delegateRef?.get()?.onNotificationNext()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                delegateRef?.get()?.onNotificationPrevious()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                delegateRef?.get()?.onNotificationFastForward10s()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                                delegateRef?.get()?.onNotificationRewind10s()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_STOP -> {
                                delegateRef?.get()?.onNotificationStop()
                                cancelNotification(context.applicationContext)
                                AudioService.stopServiceCompletely(context.applicationContext)
                                return true
                            }
                        }
                    }
                    return super.onMediaButtonEvent(mediaButtonIntent)
                }
            })
        }
        mediaSession = session
        return session
    }

    private fun extractOrCreateArtwork(path: String, title: String, isVideo: Boolean): Bitmap {
        if (path.isNotBlank() && path == cachedArtPath && cachedArtBitmap != null) {
            return cachedArtBitmap!!
        }

        var extracted: Bitmap? = null
        if (path.isNotBlank() && File(path).exists()) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(path)
                val embedded = retriever.embeddedPicture
                if (embedded != null && embedded.isNotEmpty()) {
                    extracted = BitmapFactory.decodeByteArray(embedded, 0, embedded.size)
                }
                if (extracted == null && isVideo) {
                    extracted = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }
        }

        val finalBitmap = if (extracted != null) {
            Bitmap.createScaledBitmap(extracted, 256, 256, true)
        } else {
            createFallbackGradientArtwork(title)
        }

        cachedArtPath = path
        cachedArtBitmap = finalBitmap
        return finalBitmap
    }

    private fun createFallbackGradientArtwork(title: String): Bitmap {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f,
            0f,
            size.toFloat(),
            size.toFloat(),
            intArrayOf(0xFF0A1F17.toInt(), 0xFF00E676.toInt(), 0xFF00E5FF.toInt()),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x44000000
        }
        canvas.drawCircle(size / 2f, size / 2f, size * 0.34f, circlePaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 92f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val initial = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "♪"
        canvas.drawText(initial, size / 2f, size / 2f + 32f, textPaint)
        return bmp
    }

    private fun buildActionPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MediaNotificationReceiver::class.java).apply {
            this.action = action
            setPackage(context.packageName)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun buildMediaNotification(
        context: Context,
        title: String,
        subtitle: String,
        mediaPath: String = "",
        isVideo: Boolean = false,
        isPlaying: Boolean,
        positionMs: Long = 0L,
        durationMs: Long = 0L,
        playbackSpeed: Float = 1.0f,
        isShuffle: Boolean = false,
        repeatMode: Int = 0, // 0 = Order List, 1 = Repeat All, 2 = Repeat One
        contentIntentActivity: Class<*>? = null
    ): Notification {
        ensureChannel(context)
        val session = getOrCreateMediaSession(context)
        if (!session.isActive) {
            session.isActive = true
        }
        val artwork = extractOrCreateArtwork(mediaPath, title, isVideo)

        val safeDuration = if (durationMs > 0L) durationMs else 180_000L
        val safePosition = positionMs.coerceIn(0L, safeDuration)

        val repeatLabel = when (repeatMode) {
            2 -> "Repeat 1"
            1 -> "Repeat All"
            else -> "Order"
        }
        val repeatIconRes = if (repeatMode == 2) R.drawable.ic_notif_repeat_one else R.drawable.ic_notif_repeat
        val shuffleLabel = if (isShuffle) "Shuffle ON" else "Shuffle OFF"

        // Update MediaSession metadata & seekbar state
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, subtitle)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, "Hybrid Player")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, safeDuration)
            .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork)
            .putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, artwork)
            .build()
        session.setMetadata(metadata)

        val stateActions =
            PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_REWIND or
                PlaybackState.ACTION_FAST_FORWARD or
                PlaybackState.ACTION_SEEK_TO or
                PlaybackState.ACTION_STOP

        val playbackStateBuilder = PlaybackState.Builder()
            .setActions(stateActions)
            .setState(
                if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                safePosition,
                if (isPlaying) playbackSpeed else 0f
            )
            // Custom actions for Android 13+ system media player controls (Left = Shuffle, Right = Repeat)
            .addCustomAction(
                PlaybackState.CustomAction.Builder(
                    ACTION_MEDIA_SHUFFLE,
                    shuffleLabel,
                    R.drawable.ic_notif_shuffle
                ).build()
            )
            .addCustomAction(
                PlaybackState.CustomAction.Builder(
                    ACTION_MEDIA_REPEAT,
                    repeatLabel,
                    repeatIconRes
                ).build()
            )

        session.setPlaybackState(playbackStateBuilder.build())

        val openAppIntent = contentIntentActivity?.let {
            val intent = Intent(context, it).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            PendingIntent.getActivity(
                context,
                100,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val shufflePending = buildActionPendingIntent(context, ACTION_MEDIA_SHUFFLE, 201)
        val prevPending = buildActionPendingIntent(context, ACTION_MEDIA_PREV, 202)
        val playPausePending = buildActionPendingIntent(context, ACTION_MEDIA_PLAY_PAUSE, 203)
        val nextPending = buildActionPendingIntent(context, ACTION_MEDIA_NEXT, 204)
        val repeatPending = buildActionPendingIntent(context, ACTION_MEDIA_REPEAT, 205)
        val stopPending = buildActionPendingIntent(context, ACTION_MEDIA_STOP, 206)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        builder
            .setSmallIcon(R.drawable.ic_notif_play)
            .setLargeIcon(artwork)
            .setContentTitle(title)
            .setContentText("$subtitle • ${if (isShuffle) "🔀" else "▶"} $repeatLabel")
            .setSubText(formatTimeMs(safePosition) + " / " + formatTimeMs(safeDuration))
            .setOngoing(isPlaying)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setDeleteIntent(stopPending)
            // 1. Leftmost (Ulte Haath): Shuffle (🔀)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_notif_shuffle),
                    shuffleLabel,
                    shufflePending
                ).build()
            )
            // 2. Previous Track (⏮)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_notif_prev),
                    "Previous",
                    prevPending
                ).build()
            )
            // 3. Play / Pause (▶ / ⏸)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(
                        context,
                        if (isPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play
                    ),
                    if (isPlaying) "Pause" else "Play",
                    playPausePending
                ).build()
            )
            // 4. Next Track (⏭)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_notif_next),
                    "Next",
                    nextPending
                ).build()
            )
            // 5. Rightmost (Seedhe Haath): Repeat (🔁 / 🔂 / Order)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, repeatIconRes),
                    repeatLabel,
                    repeatPending
                ).build()
            )
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    // Compact view shows Previous (1), Play/Pause (2), Next (3)
                    .setShowActionsInCompactView(1, 2, 3)
            )

        if (openAppIntent != null) {
            builder.setContentIntent(openAppIntent)
        }

        return builder.build()
    }

    fun showOrUpdateNotification(
        context: Context,
        title: String,
        subtitle: String,
        mediaPath: String = "",
        isVideo: Boolean = false,
        isPlaying: Boolean,
        positionMs: Long = 0L,
        durationMs: Long = 0L,
        playbackSpeed: Float = 1.0f,
        isShuffle: Boolean = false,
        repeatMode: Int = 0,
        contentIntentActivity: Class<*>? = null
    ) {
        val notification = buildMediaNotification(
            context = context,
            title = title,
            subtitle = subtitle,
            mediaPath = mediaPath,
            isVideo = isVideo,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            playbackSpeed = playbackSpeed,
            isShuffle = isShuffle,
            repeatMode = repeatMode,
            contentIntentActivity = contentIntentActivity
        )
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
        AudioService.syncForegroundWithNotification(notification, isPlaying)
    }

    fun cancelNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
        try {
            mediaSession?.isActive = false
        } catch (_: Exception) {
        }
    }

    private fun formatTimeMs(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val mins = totalSec / 60L
        val secs = totalSec % 60L
        return "%02d:%02d".format(mins, secs)
    }
}

class MediaNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_MEDIA_BUTTON) {
            val keyEvent: KeyEvent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            }
            if (keyEvent?.action == KeyEvent.ACTION_DOWN) {
                when (keyEvent.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY,
                    KeyEvent.KEYCODE_MEDIA_PAUSE,
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    KeyEvent.KEYCODE_HEADSETHOOK -> {
                        MediaNotificationHelper.dispatchAction(context, MediaNotificationHelper.ACTION_MEDIA_PLAY_PAUSE)
                    }
                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        MediaNotificationHelper.dispatchAction(context, MediaNotificationHelper.ACTION_MEDIA_NEXT)
                    }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        MediaNotificationHelper.dispatchAction(context, MediaNotificationHelper.ACTION_MEDIA_PREV)
                    }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        MediaNotificationHelper.dispatchAction(context, MediaNotificationHelper.ACTION_MEDIA_FFWD_10S)
                    }
                    KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        MediaNotificationHelper.dispatchAction(context, MediaNotificationHelper.ACTION_MEDIA_REWIND_10S)
                    }
                    KeyEvent.KEYCODE_MEDIA_STOP -> {
                        MediaNotificationHelper.dispatchAction(context, MediaNotificationHelper.ACTION_MEDIA_STOP)
                    }
                }
            }
            return
        }
        MediaNotificationHelper.dispatchAction(context, action)
    }
}

/**
 * Smart Background Audio Service:
 * - Only runs when a song or background video audio is actively played.
 * - While playing (`isPlaying == true`), stays in foreground with `PARTIAL_WAKE_LOCK` so screen-off playback never drops.
 * - When paused (`isPlaying == false`), detaches from foreground (`STOP_FOREGROUND_DETACH`) so the user can swipe
 *   the paused notification away to close background playback completely.
 */
class AudioService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        @Volatile
        private var activeInstance: AudioService? = null

        fun startServiceSafely(context: Context) {
            try {
                val intent = Intent(context.applicationContext, AudioService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.applicationContext.startForegroundService(intent)
                } else {
                    context.applicationContext.startService(intent)
                }
            } catch (_: Exception) {
            }
        }

        fun stopServiceCompletely(context: Context) {
            try {
                activeInstance?.stopForegroundCompletely()
                val intent = Intent(context.applicationContext, AudioService::class.java)
                context.applicationContext.stopService(intent)
            } catch (_: Exception) {
            }
        }

        fun syncForegroundWithNotification(notification: Notification, isPlaying: Boolean) {
            val instance = activeInstance ?: return
            try {
                if (isPlaying) {
                    instance.acquireWakeLockIfNeeded()
                    instance.promoteNotificationToForeground(notification)
                } else {
                    instance.releaseWakeLockIfHeld()
                    instance.demoteFromForegroundKeepNotification(notification)
                }
            } catch (_: Exception) {
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
    }

    internal fun acquireWakeLockIfNeeded() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "HybridPlayer::ZeroBlinkAudioWakeLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(6 * 60 * 60 * 1000L)
            }
        } catch (_: Exception) {
        }
    }

    internal fun releaseWakeLockIfHeld() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        activeInstance = this
        val action = intent?.action
        if (action != null) {
            MediaNotificationHelper.dispatchAction(this, action)
            if (action == MediaNotificationHelper.ACTION_MEDIA_STOP) {
                stopForegroundCompletely()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    internal fun promoteNotificationToForeground(notification: Notification) {
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
        } catch (_: Exception) {
        }
    }

    internal fun demoteFromForegroundKeepNotification(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(MediaNotificationHelper.NOTIFICATION_ID, notification)
        } catch (_: Exception) {
        }
    }

    internal fun stopForegroundCompletely() {
        try {
            releaseWakeLockIfHeld()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (wakeLock?.isHeld != true) {
            MediaNotificationHelper.cancelNotification(this)
            stopSelf()
        }
    }

    override fun onDestroy() {
        if (activeInstance === this) {
            activeInstance = null
        }
        releaseWakeLockIfHeld()
        wakeLock = null
        super.onDestroy()
    }
}
