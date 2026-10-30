package com.app.mediaplayer

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

class ToolkitFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx).apply {
            setBackgroundColor(Color.parseColor("#0B0F14"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 28))
        }

        val headerTitle = TextView(ctx).apply {
            text = "Hybrid Pro Studio Toolkit"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
        }
        val headerSub = TextView(ctx).apply {
            text = "100% Real Hardware Muxer, Storage Cleaner, Private Vault & Status Saver"
            setTextColor(Color.parseColor("#00E676"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(ctx, 4), 0, dp(ctx, 16))
        }
        root.addView(headerTitle)
        root.addView(headerSub)

        // 1. Clean Junk & Storage Broom Dashboard
        root.addView(
            buildToolCard(
                ctx = ctx,
                badge = "CLEANER",
                badgeColorHex = "#FF5C58",
                title = "Clean Junk & Storage Broom",
                subtitle = "Real StatFs storage gauge, Big File Cleaner, Watched Videos & Cache Broom"
            ) {
                HybridCleanerHubHelper.openStandaloneCleanerDialog(ctx) {}
            }
        )

        // 2. Video to MP3 / M4A Hardware Extractor
        root.addView(
            buildToolCard(
                ctx = ctx,
                badge = "MUXER",
                badgeColorHex = "#00E676",
                title = "Video to Audio Extractor (Hardware Muxer)",
                subtitle = "Zero-loss MediaExtractor + MediaMuxer direct audio stream extraction"
            ) {
                ToolkitModulesHelper.showVideoPickerForTool(
                    context = ctx,
                    title = "Select Video to Extract Audio"
                ) { video ->
                    Toast.makeText(ctx, "Extracting audio from ${video.title}...", Toast.LENGTH_SHORT).show()
                    ToolkitModulesHelper.extractAudioFromVideoHardware(ctx, video) { _, _, msg ->
                        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
        )

        // 3. Lossless Hardware Video Trimmer
        root.addView(
            buildToolCard(
                ctx = ctx,
                badge = "TRIMMER",
                badgeColorHex = "#00E5FF",
                title = "Lossless Video Trimmer (MediaMuxer)",
                subtitle = "Extract highlight clip without re-encoding quality loss"
            ) {
                ToolkitModulesHelper.showVideoPickerForTool(
                    context = ctx,
                    title = "Select Video to Trim Highlight"
                ) { video ->
                    val clipEnd = if (video.duration > 15_000L) 15_000L else (video.duration.coerceAtLeast(5_000L))
                    ToolkitModulesHelper.trimVideoLosslessHardware(
                        context = ctx,
                        video = video,
                        startMs = 0L,
                        endMs = clipEnd
                    ) { _, _, msg ->
                        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
        )

        // 4. Private PIN Vault (.vault_media)
        root.addView(
            buildToolCard(
                ctx = ctx,
                badge = "VAULT",
                badgeColorHex = "#FFAB40",
                title = "Private PIN Vault (SHA-256 Protected)",
                subtitle = "Lock & unlock private videos inside encrypted hidden vault storage"
            ) {
                ToolkitModulesHelper.openPrivateVaultDialog(ctx)
            }
        )

        // 5. 30-Day Recycle Bin
        root.addView(
            buildToolCard(
                ctx = ctx,
                badge = "TRASH BIN",
                badgeColorHex = "#FF5252",
                title = "30-Day Safe Recycle Bin",
                subtitle = "Restore accidentally deleted videos/songs or erase permanently"
            ) {
                PermanentDeleteHelper.openRecycleBinDialog(ctx) {}
            }
        )

        // 6. WhatsApp & WA Business Status Saver
        root.addView(
            buildToolCard(
                ctx = ctx,
                badge = "STATUS",
                badgeColorHex = "#00E676",
                title = "WhatsApp & WA Business Status Saver",
                subtitle = "Scans real .Statuses directories and saves status videos to Gallery"
            ) {
                openStatusSaverDialog(ctx)
            }
        )

        // 7. Hardware Stream & Codec Inspector
        root.addView(
            buildToolCard(
                ctx = ctx,
                badge = "CODEC",
                badgeColorHex = "#B388FF",
                title = "Hardware Stream & Codec Inspector",
                subtitle = "Inspect video resolution, bitrate, MIME tracks & partial download state"
            ) {
                ToolkitModulesHelper.showVideoPickerForTool(
                    context = ctx,
                    title = "Select Video to Inspect"
                ) { video ->
                    ToolkitModulesHelper.showMediaCodecInspectorDialog(ctx, video)
                }
            }
        )

        // 8. 3D DJ Headphone Puppy Companion Customizer
        root.addView(
            buildToolCard(
                ctx = ctx,
                badge = "MASCOT",
                badgeColorHex = "#FF80AB",
                title = "3D DJ Headphone Puppy Companion",
                subtitle = "Configure dancing Muso-style Chibi Puppy mood, animations & visibility"
            ) {
                MascotCompanionHelper.showBreedSelectorDialog(ctx) { _, _ -> }
            }
        )

        scroll.addView(root)
        return scroll
    }

    private fun openStatusSaverDialog(ctx: Context) {
        val statuses = ToolkitModulesHelper.scanWhatsAppStatuses()
        if (statuses.isEmpty()) {
            AlertDialog.Builder(ctx)
                .setTitle("WhatsApp Status Saver")
                .setMessage("No cached WhatsApp status files were found in .Statuses folders yet.\n\nWatch a status inside WhatsApp first, then return here to save it immediately.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val names = statuses.map { "${it.name} (${VideoModel.formatMediaSize(it.length())})" }.toTypedArray()
        AlertDialog.Builder(ctx)
            .setTitle("Tap Status to Save to Gallery (${statuses.size} found)")
            .setItems(names) { _, which ->
                val saved = ToolkitModulesHelper.saveStatusToGallery(ctx, statuses[which])
                if (saved != null) {
                    Toast.makeText(ctx, "Saved to Movies/HybridSavedStatuses/${saved.name}", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(ctx, "Could not copy status file", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun buildToolCard(
        ctx: Context,
        badge: String,
        badgeColorHex: String,
        title: String,
        subtitle: String,
        onClick: () -> Unit
    ): LinearLayout {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(ctx, 12)
            }
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 16).toFloat()
                setColor(Color.parseColor("#141B23"))
                setStroke(dp(ctx, 1), Color.parseColor("#243140"))
            }
            setOnClickListener { onClick() }

            val badgeView = TextView(ctx).apply {
                text = badge
                setTextColor(Color.BLACK)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
                background = GradientDrawable().apply {
                    cornerRadius = dp(ctx, 10).toFloat()
                    setColor(Color.parseColor(badgeColorHex))
                }
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 74), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(ctx, 14)
                }
            }

            val textCol = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val titleTv = TextView(ctx).apply {
                text = title
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = Typeface.DEFAULT_BOLD
            }

            val subTv = TextView(ctx).apply {
                text = subtitle
                setTextColor(Color.parseColor("#90A4AE"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(ctx, 3), 0, 0)
            }

            textCol.addView(titleTv)
            textCol.addView(subTv)

            val arrowTv = TextView(ctx).apply {
                text = "›"
                setTextColor(Color.parseColor(badgeColorHex))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(ctx, 8), 0, 0, 0)
            }

            addView(badgeView)
            addView(textCol)
            addView(arrowTv)
        }
    }

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}
