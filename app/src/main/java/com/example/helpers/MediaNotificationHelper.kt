package com.example.helpers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaMetadataRetriever
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.LruCache
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.lang.ref.WeakReference
import java.util.Collections

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
    const val ACTION_MEDIA_PLAY = "com.example.action.MEDIA_PLAY"
    const val ACTION_MEDIA_PAUSE = "com.example.action.MEDIA_PAUSE"
    const val ACTION_MEDIA_FFWD_10S = "com.example.action.MEDIA_FFWD_10S"
    const val ACTION_MEDIA_NEXT = "com.example.action.MEDIA_NEXT"
    const val ACTION_MEDIA_REPEAT = "com.example.action.MEDIA_REPEAT"
    const val ACTION_MEDIA_STOP = "com.example.action.MEDIA_STOP"

    interface ControlDelegate {
        fun onNotificationPlayPause()
        fun onNotificationPlay() { onNotificationPlayPause() }
        fun onNotificationPause() { onNotificationPlayPause() }
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
    @Volatile
    private var persistentAudioDelegate: ControlDelegate? = null
    private var mediaSession: MediaSession? = null
    private val artworkCache = LruCache<String, Bitmap>(32)
    private val fallbackArtworkCache = LruCache<String, Bitmap>(24)
    private val artworkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightArtworkKeys: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    @Volatile
    private var lastNotification: Notification? = null
    @Volatile
    internal var lastIsPlaying: Boolean = false
    @Volatile
    private var lastShownKey: String = ""

    fun setPersistentAudioDelegate(delegate: ControlDelegate?) {
        persistentAudioDelegate = delegate
        if (delegate != null && delegateRef?.get() == null) {
            delegateRef = WeakReference(delegate)
        }
    }

    fun setControlDelegate(delegate: ControlDelegate?) {
        delegateRef = if (delegate != null) {
            WeakReference(delegate)
        } else {
            // Fall back to the persistent background audio engine when a transient foreground video view disposes
            persistentAudioDelegate?.let { WeakReference(it) }
        }
    }

    fun getControlDelegate(): ControlDelegate? {
        return delegateRef?.get() ?: persistentAudioDelegate
    }

    fun dispatchAction(context: Context, action: String?) {
        val delegate = getControlDelegate() ?: return
        when (action) {
            ACTION_MEDIA_SHUFFLE -> delegate.onNotificationToggleShuffle()
            ACTION_MEDIA_PLAY -> delegate.onNotificationPlay()
            ACTION_MEDIA_PAUSE -> delegate.onNotificationPause()
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
                    getControlDelegate()?.onNotificationPlay()
                }

                override fun onPause() {
                    getControlDelegate()?.onNotificationPause()
                }

                override fun onSkipToNext() {
                    getControlDelegate()?.onNotificationNext()
                }

                override fun onSkipToPrevious() {
                    getControlDelegate()?.onNotificationPrevious()
                }

                override fun onFastForward() {
                    getControlDelegate()?.onNotificationFastForward10s()
                }

                override fun onRewind() {
                    getControlDelegate()?.onNotificationRewind10s()
                }

                override fun onSeekTo(pos: Long) {
                    getControlDelegate()?.onNotificationSeekTo(pos)
                }

                override fun onStop() {
                    getControlDelegate()?.onNotificationStop()
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
                            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                getControlDelegate()?.onNotificationPlay()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                getControlDelegate()?.onNotificationPause()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                            KeyEvent.KEYCODE_HEADSETHOOK -> {
                                getControlDelegate()?.onNotificationPlayPause()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                getControlDelegate()?.onNotificationNext()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                getControlDelegate()?.onNotificationPrevious()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                getControlDelegate()?.onNotificationFastForward10s()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                                getControlDelegate()?.onNotificationRewind10s()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_STOP -> {
                                getControlDelegate()?.onNotificationStop()
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

    private fun getCachedOrFallbackArtworkAsync(
        context: Context,
        path: String,
        title: String,
        subtitle: String,
        isVideo: Boolean,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        playbackSpeed: Float,
        isShuffle: Boolean,
        repeatMode: Int,
        contentIntentActivity: Class<*>?
    ): Bitmap {
        val cacheKey = "$path|$title|$isVideo"
        val cached = artworkCache.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            return cached
        }

        val fallbackInitial = title.trim().firstOrNull()?.uppercaseChar()?.toString()
            ?: if (isVideo) "▶" else "♪"
        val fallbackKey = "$isVideo|$fallbackInitial"
        val fallback = fallbackArtworkCache.get(fallbackKey)?.takeIf { !it.isRecycled }
            ?: createFallbackGradientArtwork(title, isVideo).also {
                fallbackArtworkCache.put(fallbackKey, it)
            }

        if (path.isNotBlank() && !path.startsWith("http") && inFlightArtworkKeys.add(cacheKey)) {
            val appCtx = context.applicationContext
            artworkScope.launch {
                var extracted: Bitmap? = null
                val retriever = MediaMetadataRetriever()
                try {
                    if (path.startsWith("content://")) {
                        retriever.setDataSource(appCtx, android.net.Uri.parse(path))
                    } else if (File(path).exists()) {
                        retriever.setDataSource(path)
                    }
                    val embedded = retriever.embeddedPicture
                    if (embedded != null && embedded.isNotEmpty()) {
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(embedded, 0, embedded.size, opts)
                        val maxDim = maxOf(opts.outWidth, opts.outHeight)
                        var sample = 1
                        while (maxDim / sample > 512) sample *= 2
                        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                        extracted = BitmapFactory.decodeByteArray(embedded, 0, embedded.size, decodeOpts)
                    }
                    if (extracted == null && isVideo) {
                        extracted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            retriever.getScaledFrameAtTime(
                                1_000_000L,
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                256,
                                256
                            )
                        } else {
                            retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    try {
                        retriever.release()
                    } catch (_: Exception) {
                    }
                }

                if (extracted != null) {
                    val scaled = try {
                        if (extracted.width == 256 && extracted.height == 256) {
                            extracted
                        } else {
                            Bitmap.createScaledBitmap(extracted, 256, 256, true).also { newBmp ->
                                if (newBmp !== extracted && !extracted.isRecycled) {
                                    extracted.recycle()
                                }
                            }
                        }
                    } catch (_: Exception) {
                        if (!extracted.isRecycled) extracted.recycle()
                        null
                    }
                    if (scaled != null) {
                        artworkCache.put(cacheKey, scaled)
                        if (lastShownKey == cacheKey) {
                            showOrUpdateNotification(
                                context = appCtx,
                                title = title,
                                subtitle = subtitle,
                                mediaPath = path,
                                isVideo = isVideo,
                                isPlaying = isPlaying,
                                positionMs = positionMs,
                                durationMs = durationMs,
                                playbackSpeed = playbackSpeed,
                                isShuffle = isShuffle,
                                repeatMode = repeatMode,
                                contentIntentActivity = contentIntentActivity
                            )
                        }
                    }
                }
                inFlightArtworkKeys.remove(cacheKey)
            }
        }
        return fallback
    }

    private fun createFallbackGradientArtwork(title: String, isVideo: Boolean): Bitmap {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (isVideo) {
            // Sleek cinematic widescreen / film frame aesthetic
            paint.shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                intArrayOf(0xFF0A1026.toInt(), 0xFF14243B.toInt(), 0xFF003847.toInt()),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

            // Film reel accent circles
            val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x2200E5FF.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            canvas.drawCircle(size / 2f, size / 2f, size * 0.42f, accentPaint)
            canvas.drawCircle(size / 2f, size / 2f, size * 0.36f, accentPaint)

            // Center glowing badge
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x3300E5FF.toInt()
                style = Paint.Style.FILL
            }
            canvas.drawCircle(size / 2f, size / 2f, size * 0.28f, badgePaint)

            // Video icon / Title Initial
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF00E5FF.toInt()
                textSize = 96f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            val initial = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "▶"
            canvas.drawText(initial, size / 2f, size / 2f + 34f, textPaint)
        } else {
            // Hi-Fi Vinyl Disc Aesthetic
            paint.shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                intArrayOf(0xFF07120D.toInt(), 0xFF0A2417.toInt(), 0xFF05170E.toInt()),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

            // Vinyl Disc Grooves
            val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
            }
            val radii = floatArrayOf(0.44f, 0.40f, 0.36f, 0.32f, 0.28f)
            radii.forEachIndexed { i, r ->
                groovePaint.color = if (i % 2 == 0) 0x1AFFFFFF else 0x0DFFFFFF
                groovePaint.strokeWidth = 2f
                canvas.drawCircle(size / 2f, size / 2f, size * r, groovePaint)
            }

            // Center Vinyl Label Disc with Emerald Glow
            val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    size * 0.3f, size * 0.3f, size * 0.7f, size * 0.7f,
                    intArrayOf(0xFF00E676.toInt(), 0xFF00B0FF.toInt()),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(size / 2f, size / 2f, size * 0.22f, discPaint)

            // Center spindle hole
            val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF07120D.toInt()
            }
            canvas.drawCircle(size / 2f, size / 2f, size * 0.06f, holePaint)

            // Clean Title initial or Note
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                textSize = 58f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            val initial = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "♪"
            canvas.drawText(initial, size / 2f, size / 2f + 20f, textPaint)
        }

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
        lastShownKey = "$mediaPath|$title|$isVideo"
        val artwork = getCachedOrFallbackArtworkAsync(
            context = context,
            path = mediaPath,
            title = title,
            subtitle = subtitle,
            isVideo = isVideo,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            playbackSpeed = playbackSpeed,
            isShuffle = isShuffle,
            repeatMode = repeatMode,
            contentIntentActivity = contentIntentActivity
        )

        val safeDuration = if (durationMs > 0L) durationMs else 180_000L
        val safePosition = positionMs.coerceIn(0L, safeDuration)

        val repeatLabel = when (repeatMode) {
            2 -> "Repeat 1"
            1 -> "Repeat All"
            else -> "Repeat Off"
        }
        val repeatIconRes = when (repeatMode) {
            2 -> R.drawable.ic_notif_repeat_one
            1 -> R.drawable.ic_notif_repeat
            else -> R.drawable.ic_notif_repeat_off
        }
        val shuffleLabel = if (isShuffle) "Shuffle ON" else "Shuffle OFF"
        val shuffleIconRes = if (isShuffle) R.drawable.ic_notif_shuffle_on else R.drawable.ic_notif_shuffle

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
                if (isPlaying) playbackSpeed else 0f,
                SystemClock.elapsedRealtime()
            )
            // Custom actions for Android 11–17 system media player controls:
            // Slot 1 (Far Left) = Shuffle, Slot 2 (Far Right) = Repeat
            .addCustomAction(
                PlaybackState.CustomAction.Builder(
                    ACTION_MEDIA_SHUFFLE,
                    shuffleLabel,
                    shuffleIconRes
                ).build()
            )
            .addCustomAction(
                PlaybackState.CustomAction.Builder(
                    ACTION_MEDIA_REPEAT,
                    repeatLabel,
                    repeatIconRes
                ).build()
            )
            // Secondary custom actions for -10s / +10s seek
            .addCustomAction(
                PlaybackState.CustomAction.Builder(
                    ACTION_MEDIA_REWIND_10S,
                    "-10s",
                    R.drawable.ic_notif_rewind
                ).build()
            )
            .addCustomAction(
                PlaybackState.CustomAction.Builder(
                    ACTION_MEDIA_FFWD_10S,
                    "+10s",
                    R.drawable.ic_notif_ffwd
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
            .setContentText("$subtitle • $shuffleLabel • $repeatLabel")
            .setSubText(if (isVideo) "🎬 Video Background Audio" else "🎵 Hybrid Audio • Hi-Fi Engine")
            .setColor(0xFF00E676.toInt())
            .setColorized(true)
            .setOngoing(isPlaying)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setDeleteIntent(stopPending)
            // 0 (Far Left): Shuffle (🔀)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, shuffleIconRes),
                    shuffleLabel,
                    shufflePending
                ).build()
            )
            // 1: Previous Track (⏮)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_notif_prev),
                    "Previous",
                    prevPending
                ).build()
            )
            // 2 (Center): Play / Pause (▶ / ⏸)
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
            // 3: Next Track (⏭)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_notif_next),
                    "Next",
                    nextPending
                ).build()
            )
            // 4 (Far Right): Repeat (🔁 / 🔂)
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
                    // Compact view shows Previous (1), Play/Pause (2), Next (3); expanded & Android 11+ show Shuffle (0) on Left and Repeat (4) on Right
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
        lastNotification = notification
        lastIsPlaying = isPlaying
        if (!AudioService.syncForegroundWithNotification(notification, isPlaying)) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, notification)
        } else if (isPlaying) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, notification)
        }
    }

    fun buildBootstrapNotification(context: Context): Notification {
        lastNotification?.let { return it }
        ensureChannel(context)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        return builder
            .setSmallIcon(R.drawable.ic_notif_play)
            .setContentTitle("Hybrid Player")
            .setContentText("Preparing audio playback...")
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .build()
    }

    fun cancelNotification(context: Context) {
        lastShownKey = ""
        lastNotification = null
        lastIsPlaying = false
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
            mediaSession = null
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
                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        MediaNotificationHelper.dispatchAction(context, MediaNotificationHelper.ACTION_MEDIA_PLAY)
                    }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        MediaNotificationHelper.dispatchAction(context, MediaNotificationHelper.ACTION_MEDIA_PAUSE)
                    }
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
    private var noisyReceiverRegistered = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                // Explicitly PAUSE (never toggle) so unplugging headphones while paused never blasts audio
                MediaNotificationHelper.getControlDelegate()?.onNotificationPause()
            }
        }
    }

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

        fun syncForegroundWithNotification(notification: Notification, isPlaying: Boolean): Boolean {
            val instance = activeInstance ?: return false
            try {
                if (isPlaying) {
                    instance.acquireWakeLockIfNeeded()
                    instance.promoteNotificationToForeground(notification)
                } else {
                    instance.releaseWakeLockIfHeld()
                    instance.demoteFromForegroundKeepNotification(notification)
                }
                return true
            } catch (_: Exception) {
                return false
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        try {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            ContextCompat.registerReceiver(this, noisyReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            noisyReceiverRegistered = true
        } catch (_: Exception) {
        }
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
        if (action == MediaNotificationHelper.ACTION_MEDIA_STOP) {
            MediaNotificationHelper.dispatchAction(this, action)
            stopForegroundCompletely()
            stopSelf()
            return START_NOT_STICKY
        }
        // Immediately satisfy Android 8+/14+ startForegroundService() contract without overwriting live notification
        promoteNotificationToForeground(MediaNotificationHelper.buildBootstrapNotification(this))
        if (MediaNotificationHelper.lastIsPlaying) {
            acquireWakeLockIfNeeded()
        }
        if (action != null) {
            MediaNotificationHelper.dispatchAction(this, action)
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
        if (MediaNotificationHelper.lastIsPlaying) {
            // Keep background song / video-audio playing seamlessly when Hybrid Player is swiped from Recent Apps
            acquireWakeLockIfNeeded()
            promoteNotificationToForeground(MediaNotificationHelper.buildBootstrapNotification(this))
        } else {
            // Playback is paused or idle: clean up service and notification so nothing lingers in background
            MediaNotificationHelper.getControlDelegate()?.onNotificationStop()
            MediaNotificationHelper.cancelNotification(this)
            stopForegroundCompletely()
            stopSelf()
        }
    }

    override fun onDestroy() {
        if (activeInstance === this) {
            activeInstance = null
        }
        if (noisyReceiverRegistered) {
            try {
                unregisterReceiver(noisyReceiver)
            } catch (_: Exception) {
            }
            noisyReceiverRegistered = false
        }
        releaseWakeLockIfHeld()
        wakeLock = null
        super.onDestroy()
    }
}
