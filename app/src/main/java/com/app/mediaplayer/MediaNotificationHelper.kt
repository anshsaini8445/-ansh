package com.app.mediaplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaMetadata
import android.media.MediaMetadataRetriever
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.util.LruCache
import java.io.File

/**
 * Universal Foreground & Lock-Screen Media Notification Engine for Hybrid Player.
 * Supports Android 5.0 (API 21) through Android 15+ (API 36) with:
 *  - Lock-screen visibility (VISIBILITY_PUBLIC)
 *  - Native MediaSession + Notification.MediaStyle (Lock-screen scrubbing, Bluetooth/Headset keys)
 *  - 5 Notification Transport Controls: Prev (⏮), Rewind -10s (⏪), Play/Pause (▶/⏸), Forward +10s (⏩), Next (⏭)
 *  - Works seamlessly for both Audio (Music Studio) and Video (Foreground & Background Video Playback).
 */
object MediaNotificationHelper {

    const val CHANNEL_ID = "hybrid_media_playback_channel_v2"
    const val CHANNEL_NAME = "Hybrid Player Media Controls"
    const val NOTIFICATION_ID = 7701

    const val ACTION_MEDIA_PREV = "com.app.mediaplayer.ACTION_MEDIA_PREV"
    const val ACTION_MEDIA_REWIND_10 = "com.app.mediaplayer.ACTION_MEDIA_REWIND_10"
    const val ACTION_MEDIA_PLAY_PAUSE = "com.app.mediaplayer.ACTION_MEDIA_PLAY_PAUSE"
    const val ACTION_MEDIA_FAST_FORWARD_10 = "com.app.mediaplayer.ACTION_MEDIA_FAST_FORWARD_10"
    const val ACTION_MEDIA_NEXT = "com.app.mediaplayer.ACTION_MEDIA_NEXT"
    const val ACTION_MEDIA_STOP = "com.app.mediaplayer.ACTION_MEDIA_STOP"

    interface ControlDelegate {
        fun onNotificationPlayPause()
        fun onNotificationRewind10s()
        fun onNotificationFastForward10s()
        fun onNotificationNext()
        fun onNotificationPrevious()
        fun onNotificationSeekTo(positionMs: Long)
        fun onNotificationStop()
    }

    @Volatile
    private var activeDelegate: ControlDelegate? = null

    @Volatile
    private var lastNotification: Notification? = null

    @Volatile
    private var mediaSession: MediaSession? = null

    private val artworkCache = object : LruCache<String, Bitmap>(20) {}
    private var fallbackVinylArtwork: Bitmap? = null

    fun setControlDelegate(delegate: ControlDelegate?) {
        activeDelegate = delegate
    }

    fun getControlDelegate(): ControlDelegate? = activeDelegate

    fun getLastOrDefaultNotification(context: Context): Notification {
        return lastNotification ?: buildMediaNotification(
            context = context,
            title = "Hybrid 8K Media Player",
            subtitle = "Background Playback & Lock-Screen Controls Active",
            mediaPath = null,
            isVideo = false,
            isPlaying = true,
            positionMs = 0L,
            durationMs = 0L
        )
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Lock-screen and notification bar playback controls for Video and Music"
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun ensureMediaSession(context: Context): MediaSession {
        val current = mediaSession
        if (current != null) return current
        val session = MediaSession(context.applicationContext, "HybridMediaPlayerSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    activeDelegate?.onNotificationPlayPause()
                }

                override fun onPause() {
                    activeDelegate?.onNotificationPlayPause()
                }

                override fun onSkipToNext() {
                    activeDelegate?.onNotificationNext()
                }

                override fun onSkipToPrevious() {
                    activeDelegate?.onNotificationPrevious()
                }

                override fun onFastForward() {
                    activeDelegate?.onNotificationFastForward10s()
                }

                override fun onRewind() {
                    activeDelegate?.onNotificationRewind10s()
                }

                override fun onSeekTo(pos: Long) {
                    activeDelegate?.onNotificationSeekTo(pos.coerceAtLeast(0L))
                }

                override fun onStop() {
                    activeDelegate?.onNotificationStop()
                }
            })
            isActive = true
        }
        mediaSession = session
        return session
    }

    fun buildMediaNotification(
        context: Context,
        title: String,
        subtitle: String,
        mediaPath: String?,
        isVideo: Boolean,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        playbackSpeed: Float = 1.0f,
        contentIntentActivity: Class<*>? = null
    ): Notification {
        val appCtx = context.applicationContext
        ensureChannel(appCtx)
        val session = ensureMediaSession(appCtx)

        val artwork = resolveMediaArtwork(appCtx, mediaPath, isVideo)

        // Update MediaSession Metadata & PlaybackState for Lock Screen + System Media Panel
        try {
            val metaBuilder = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, subtitle)
                .putString(
                    MediaMetadata.METADATA_KEY_ALBUM,
                    if (isVideo) "Hybrid 8K Video Engine" else "Hybrid Hi-Res Audio Studio"
                )
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs.coerceAtLeast(0L))
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork)
                .putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, artwork)
            session.setMetadata(metaBuilder.build())

            val stateActions = PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_REWIND or
                PlaybackState.ACTION_FAST_FORWARD or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SEEK_TO or
                PlaybackState.ACTION_STOP

            val playbackState = PlaybackState.Builder()
                .setActions(stateActions)
                .setState(
                    if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    positionMs.coerceAtLeast(0L),
                    if (isPlaying) playbackSpeed else 0f
                )
                .build()
            session.setPlaybackState(playbackState)
            session.isActive = true
        } catch (_: Exception) {
        }

        val targetActivityClass = contentIntentActivity ?: com.example.MainActivity::class.java
        val openAppIntent = Intent(appCtx, targetActivityClass).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!mediaPath.isNullOrBlank()) {
                putExtra("video_path", mediaPath)
                putExtra("video_title", title)
                putExtra(AudioPlayerActivity.EXTRA_AUDIO_PATH, mediaPath)
                putExtra(AudioPlayerActivity.EXTRA_AUDIO_TITLE, title)
            }
        }
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val contentPendingIntent = PendingIntent.getActivity(appCtx, 100, openAppIntent, piFlags)

        val prevPending = createBroadcastPendingIntent(appCtx, ACTION_MEDIA_PREV, 101, piFlags)
        val rewindPending = createBroadcastPendingIntent(appCtx, ACTION_MEDIA_REWIND_10, 102, piFlags)
        val playPausePending = createBroadcastPendingIntent(appCtx, ACTION_MEDIA_PLAY_PAUSE, 103, piFlags)
        val forwardPending = createBroadcastPendingIntent(appCtx, ACTION_MEDIA_FAST_FORWARD_10, 104, piFlags)
        val nextPending = createBroadcastPendingIntent(appCtx, ACTION_MEDIA_NEXT, 105, piFlags)
        val stopPending = createBroadcastPendingIntent(appCtx, ACTION_MEDIA_STOP, 106, piFlags)

        val posFormatted = VideoModel.formatMediaDuration(positionMs)
        val durFormatted = if (durationMs > 0L) VideoModel.formatMediaDuration(durationMs) else "LIVE"
        val modeTag = if (isVideo) "🎬 Video" else "🎧 Music"
        val subTextLine = "$modeTag • $posFormatted / $durFormatted"

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(appCtx, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(appCtx)
        }

        val smallIconRes = if (isVideo) {
            android.R.drawable.ic_media_play
        } else {
            if (isPlaying) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
        }

        val mediaStyle = Notification.MediaStyle()
            .setMediaSession(session.sessionToken)
            // Show Rewind -10s (index 1), Play/Pause (index 2), Fast-Forward +10s (index 3) in compact shade!
            .setShowActionsInCompactView(1, 2, 3)

        builder
            .setSmallIcon(smallIconRes)
            .setLargeIcon(artwork)
            .setContentTitle(title.ifBlank { "Hybrid Media Player" })
            .setContentText(subtitle.ifBlank { subTextLine })
            .setSubText(subTextLine)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(stopPending)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setStyle(mediaStyle)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_previous,
                    "Prev",
                    prevPending
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_rew,
                    "-10s",
                    rewindPending
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (isPlaying) "Pause" else "Play",
                    playPausePending
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_ff,
                    "+10s",
                    forwardPending
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_next,
                    "Next",
                    nextPending
                ).build()
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setColor(Color.parseColor("#0D261D"))
            builder.setCategory(Notification.CATEGORY_TRANSPORT)
        }

        return builder.build()
    }

    fun showOrUpdateNotification(
        context: Context,
        title: String,
        subtitle: String,
        mediaPath: String?,
        isVideo: Boolean,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        playbackSpeed: Float = 1.0f,
        contentIntentActivity: Class<*>? = null
    ): Notification {
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
            contentIntentActivity = contentIntentActivity
        )
        lastNotification = notification
        AudioService.syncForegroundWithNotification(context.applicationContext, notification, isPlaying)
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
        return notification
    }

    fun cancelNotification(context: Context) {
        lastNotification = null
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {
        }
        try {
            mediaSession?.isActive = false
        } catch (_: Exception) {
        }
    }

    private fun createBroadcastPendingIntent(
        context: Context,
        action: String,
        requestCode: Int,
        flags: Int
    ): PendingIntent {
        val intent = Intent(context, MediaNotificationReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    private fun resolveMediaArtwork(context: Context, mediaPath: String?, isVideo: Boolean): Bitmap {
        if (!mediaPath.isNullOrBlank()) {
            val cached = artworkCache.get(mediaPath)
            if (cached != null && !cached.isRecycled) return cached

            val extracted = extractArtworkFromFile(mediaPath, isVideo)
            if (extracted != null) {
                artworkCache.put(mediaPath, extracted)
                return extracted
            }
        }
        val existingFallback = fallbackVinylArtwork
        if (existingFallback != null && !existingFallback.isRecycled) {
            return existingFallback
        }
        val generated = generateStudioFallbackBitmap()
        fallbackVinylArtwork = generated
        return generated
    }

    private fun extractArtworkFromFile(path: String, isVideo: Boolean): Bitmap? {
        if (path.startsWith("http")) return null
        val file = File(path)
        if (!file.exists()) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val embedded = retriever.embeddedPicture
            if (embedded != null) {
                BitmapFactory.decodeByteArray(embedded, 0, embedded.size)
            } else if (isVideo) {
                retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun generateStudioFallbackBitmap(): Bitmap {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                size.toFloat(),
                size.toFloat(),
                Color.parseColor("#0E2A20"),
                Color.parseColor("#091016"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = Color.parseColor("#00E676")
        }
        val cx = size / 2f
        val cy = size / 2f
        canvas.drawCircle(cx, cy, size * 0.36f, ringPaint)

        val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#00E5FF")
        }
        canvas.drawCircle(cx, cy, size * 0.14f, discPaint)

        val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#091016")
        }
        canvas.drawCircle(cx, cy, size * 0.04f, holePaint)
        return bmp
    }
}

/**
 * BroadcastReceiver that handles notification bar & lock-screen clicks
 * for Prev, -10s Rewind, Play/Pause, +10s Fast-Forward, Next, and Stop.
 */
class MediaNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        val delegate = MediaNotificationHelper.getControlDelegate()
        when (action) {
            MediaNotificationHelper.ACTION_MEDIA_PREV -> delegate?.onNotificationPrevious()
            MediaNotificationHelper.ACTION_MEDIA_REWIND_10 -> delegate?.onNotificationRewind10s()
            MediaNotificationHelper.ACTION_MEDIA_PLAY_PAUSE -> delegate?.onNotificationPlayPause()
            MediaNotificationHelper.ACTION_MEDIA_FAST_FORWARD_10 -> delegate?.onNotificationFastForward10s()
            MediaNotificationHelper.ACTION_MEDIA_NEXT -> delegate?.onNotificationNext()
            MediaNotificationHelper.ACTION_MEDIA_STOP -> {
                delegate?.onNotificationStop()
                if (context != null) {
                    MediaNotificationHelper.cancelNotification(context)
                }
            }
        }
    }
}
