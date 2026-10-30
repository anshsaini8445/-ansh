package com.app.mediaplayer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.text.TextUtils
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

class AudioAdapter(
    private val context: Context,
    private var items: MutableList<AudioModel>,
    private val onTrackClick: (AudioModel, Int) -> Unit,
    private val onLibraryMutated: () -> Unit = {}
) : RecyclerView.Adapter<AudioAdapter.AudioViewHolder>() {

    companion object {
        private const val PREF_AUDIO_FAVORITES = "HYBRID_AUDIO_FAVORITES_V1"
        private const val KEY_FAV_PATHS = "fav_audio_paths"

        private val artCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
        }
        private val artExecutor = Executors.newFixedThreadPool(3)
        private val mainHandler = Handler(Looper.getMainLooper())

        fun isFavorite(context: Context, path: String): Boolean {
            val prefs = context.getSharedPreferences(PREF_AUDIO_FAVORITES, Context.MODE_PRIVATE)
            val set = prefs.getStringSet(KEY_FAV_PATHS, emptySet()) ?: emptySet()
            return set.contains(path)
        }

        fun toggleFavorite(context: Context, path: String): Boolean {
            val prefs = context.getSharedPreferences(PREF_AUDIO_FAVORITES, Context.MODE_PRIVATE)
            val current = (prefs.getStringSet(KEY_FAV_PATHS, emptySet()) ?: emptySet()).toMutableSet()
            val added = if (current.contains(path)) {
                current.remove(path)
                false
            } else {
                current.add(path)
                true
            }
            prefs.edit().putStringSet(KEY_FAV_PATHS, current).apply()
            return added
        }
    }

    private var activePlayingPath: String? = null

    fun setActivePlayingPath(path: String?) {
        val oldPath = activePlayingPath
        activePlayingPath = path
        if (oldPath != path) {
            notifyDataSetChanged()
        }
    }

    fun updateData(newItems: List<AudioModel>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun getItems(): List<AudioModel> = items.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioViewHolder {
        val row = buildTrackRowView(parent.context)
        return AudioViewHolder(row)
    }

    override fun onBindViewHolder(holder: AudioViewHolder, position: Int) {
        if (position < 0 || position >= items.size) return
        val audio = items[position]
        val isPlaying = audio.path == activePlayingPath
        val isFav = isFavorite(context, audio.path)

        holder.boundPath = audio.path
        holder.titleText.text = audio.title
        holder.titleText.setTextColor(
            if (isPlaying) Color.parseColor("#00E676") else Color.WHITE
        )

        val ext = File(audio.path).extension.uppercase(Locale.US).ifBlank { "AUDIO" }
        val qualityBadge = when (ext) {
            "FLAC", "WAV", "ALAC", "AIFF" -> "HI-RES $ext"
            "M4A", "AAC" -> "AAC STUDIO"
            "MP3" -> "MP3 320K"
            "OGG", "OPUS" -> "OPUS HQ"
            else -> ext
        }
        holder.badgeText.text = qualityBadge
        holder.subtitleText.text = "${audio.artist} • ${audio.formattedDuration} • ${audio.formattedSize}"
        holder.favButton.text = if (isFav) "♥" else "♡"
        holder.favButton.setTextColor(
            if (isFav) Color.parseColor("#FF5252") else Color.parseColor("#90A4AE")
        )

        // Load embedded album art or draw default note graphic
        val cached = artCache.get(audio.path)
        if (cached != null && !cached.isRecycled) {
            holder.artImage.setImageBitmap(cached)
        } else {
            holder.artImage.setImageBitmap( null )
            artExecutor.execute {
                val extracted = extractEmbeddedAlbumArt(audio.path)
                if (extracted != null) {
                    artCache.put(audio.path, extracted)
                    mainHandler.post {
                        if (holder.boundPath == audio.path) {
                            holder.artImage.setImageBitmap(extracted)
                        }
                    }
                }
            }
        }

        holder.itemView.setOnClickListener {
            val currentPos = holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: position
            onTrackClick(audio, currentPos)
        }

        holder.favButton.setOnClickListener {
            val nowFav = toggleFavorite(context, audio.path)
            holder.favButton.text = if (nowFav) "♥" else "♡"
            holder.favButton.setTextColor(
                if (nowFav) Color.parseColor("#FF5252") else Color.parseColor("#90A4AE")
            )
            Toast.makeText(
                context,
                if (nowFav) "Added to Favorite Songs" else "Removed from Favorites",
                Toast.LENGTH_SHORT
            ).show()
        }

        holder.moreButton.setOnClickListener { anchor ->
            showTrackOverflowMenu(anchor, audio, position)
        }
    }

    override fun getItemCount(): Int = items.size

    private fun showTrackOverflowMenu(anchor: View, audio: AudioModel, position: Int) {
        val popup = PopupMenu(context, anchor)
        popup.menu.add(0, 1, 0, "Play in Audio Studio")
        popup.menu.add(0, 2, 1, if (isFavorite(context, audio.path)) "Remove from Favorites" else "Add to Favorites")
        popup.menu.add(0, 3, 2, "Set as Phone Ringtone")
        popup.menu.add(0, 4, 3, "Share Audio File")
        popup.menu.add(0, 5, 4, "Track Details & Path")
        popup.menu.add(0, 6, 5, "Delete / Move to Recycle Bin")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> onTrackClick(audio, position)
                2 -> {
                    toggleFavorite(context, audio.path)
                    notifyItemChanged(position)
                }
                3 -> setTrackAsRingtone(audio)
                4 -> shareAudioFile(audio)
                5 -> showTrackInfoDialog(audio)
                6 -> {
                    PermanentDeleteHelper.confirmAndDeletePermanently(
                        context = context,
                        video = VideoModel(
                            id = audio.id,
                            title = audio.title,
                            path = audio.path,
                            size = audio.size,
                            duration = audio.duration
                        ),
                        onCompleted = {
                            val idx = items.indexOfFirst { it.path == audio.path }
                            if (idx >= 0) {
                                items.removeAt(idx)
                                notifyItemRemoved(idx)
                            }
                            onLibraryMutated()
                        }
                    )
                }
            }
            true
        }
        popup.show()
    }

    private fun setTrackAsRingtone(audio: AudioModel) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
            Toast.makeText(
                context,
                "Grant 'Modify System Settings' permission to set ringtone",
                Toast.LENGTH_LONG
            ).show()
            try {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
            }
            return
        }
        try {
            val uri = if (audio.id > 0L) {
                Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audio.id.toString())
            } else {
                Uri.fromFile(File(audio.path))
            }
            RingtoneManager.setActualDefaultRingtoneUri(
                context,
                RingtoneManager.TYPE_RINGTONE,
                uri
            )
            Toast.makeText(context, "Ringtone set to: ${audio.title}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to set ringtone: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareAudioFile(audio: AudioModel) {
        try {
            val file = File(audio.path)
            val uri = if (file.exists()) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } else {
                Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audio.id.toString())
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Audio"))
        } catch (_: Exception) {
            Toast.makeText(context, "Sharing track: ${audio.title}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showTrackInfoDialog(audio: AudioModel) {
        android.app.AlertDialog.Builder(context)
            .setTitle(audio.title)
            .setMessage(
                "Artist: ${audio.artist}\n" +
                    "Album: ${audio.album}\n" +
                    "Duration: ${audio.formattedDuration}\n" +
                    "Size: ${audio.formattedSize}\n" +
                    "Path: ${audio.path}"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun extractEmbeddedAlbumArt(path: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val bytes = retriever.embeddedPicture ?: return null
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
                inSampleSize = 2
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun buildTrackRowView(ctx: Context): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 10), dp(ctx, 10))
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(ctx, 8)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(ctx, 14).toFloat()
                setColor(Color.parseColor("#141920"))
                setStroke(dp(ctx, 1), Color.parseColor("#222B35"))
            }
        }

        val artContainer = object : FrameLayout(ctx) {
            private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1D2630")
            }
            private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#00E676")
                style = Paint.Style.STROKE
                strokeWidth = dp(ctx, 2).toFloat()
            }
            private val noteFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#00E676")
                style = Paint.Style.FILL
            }

            override fun dispatchDraw(canvas: Canvas) {
                canvas.drawRoundRect(
                    0f,
                    0f,
                    width.toFloat(),
                    height.toFloat(),
                    dp(ctx, 12).toFloat(),
                    dp(ctx, 12).toFloat(),
                    bgPaint
                )
                val cx = width / 2f
                val cy = height / 2f
                canvas.drawCircle(cx - dp(ctx, 5), cy + dp(ctx, 6), dp(ctx, 4).toFloat(), noteFillPaint)
                canvas.drawCircle(cx + dp(ctx, 6), cy + dp(ctx, 4), dp(ctx, 4).toFloat(), noteFillPaint)
                val path = Path().apply {
                    moveTo(cx - dp(ctx, 1), cy + dp(ctx, 6))
                    lineTo(cx - dp(ctx, 1), cy - dp(ctx, 8))
                    lineTo(cx + dp(ctx, 10), cy - dp(ctx, 10))
                    lineTo(cx + dp(ctx, 10), cy + dp(ctx, 4))
                }
                canvas.drawPath(path, notePaint)
                super.dispatchDraw(canvas)
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 52), dp(ctx, 52))
        }

        val artImage = ImageView(ctx).apply {
            tag = "art_image"
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        artContainer.addView(artImage)
        row.addView(artContainer)

        val textColumn = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(ctx, 12)
                marginEnd = dp(ctx, 8)
            }
        }

        val topTitleRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleText = TextView(ctx).apply {
            tag = "title_text"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val badgeText = TextView(ctx).apply {
            tag = "badge_text"
            setTextColor(Color.parseColor("#00E5FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(ctx, 6), dp(ctx, 2), dp(ctx, 6), dp(ctx, 2))
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 6).toFloat()
                setColor(Color.parseColor("#122C34"))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(ctx, 6)
            }
        }

        topTitleRow.addView(titleText)
        topTitleRow.addView(badgeText)

        val subtitleText = TextView(ctx).apply {
            tag = "subtitle_text"
            setTextColor(Color.parseColor("#90A4AE"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(ctx, 4), 0, 0)
        }

        textColumn.addView(topTitleRow)
        textColumn.addView(subtitleText)
        row.addView(textColumn)

        val favButton = TextView(ctx).apply {
            tag = "fav_button"
            text = "♡"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#90A4AE"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 36), dp(ctx, 36))
        }
        row.addView(favButton)

        val moreButton = TextView(ctx).apply {
            tag = "more_button"
            text = "⋮"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#B0BEC5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 36), dp(ctx, 36))
        }
        row.addView(moreButton)

        return row
    }

    class AudioViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var boundPath: String = ""
        val artImage: ImageView = view.findViewWithTag("art_image")
        val titleText: TextView = view.findViewWithTag("title_text")
        val badgeText: TextView = view.findViewWithTag("badge_text")
        val subtitleText: TextView = view.findViewWithTag("subtitle_text")
        val favButton: TextView = view.findViewWithTag("fav_button")
        val moreButton: TextView = view.findViewWithTag("more_button")
    }

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}
