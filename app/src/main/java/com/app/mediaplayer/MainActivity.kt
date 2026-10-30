package com.app.mediaplayer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.BuildConfig

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQ_MEDIA_PERMISSIONS = 9001
        private val FRAGMENT_CONTAINER_ID = View.generateViewId()
    }

    private lateinit var rootFrame: FrameLayout
    private lateinit var navVideosTab: TextView
    private lateinit var navMusicTab: TextView
    private lateinit var navToolkitTab: TextView

    private var activeTabIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MediaNotificationHelper.ensureChannel(this)
        buildMainShellUi()
        setContentView(rootFrame)

        // Attach floating Muso-style 3D Chibi DJ Headphone Puppy Companion
        MascotCompanionHelper.attachCompanionOverlay(this, rootFrame)

        requestStoragePermissionsIfNeeded()
        if (savedInstanceState == null) {
            switchTab(0)
        }
    }

    private fun buildMainShellUi() {
        rootFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0B0F14"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val verticalShell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Top Live Version & Notification Status Strip
        val versionStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setBackgroundColor(Color.parseColor("#0E151D"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val appTitleTv = TextView(this).apply {
            text = "HYBRID PRO ENGINE"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val versionBadgeTv = TextView(this).apply {
            text = "v${BuildConfig.VERSION_NAME} (#${BuildConfig.VERSION_CODE}) • BG Notif Ready"
            setTextColor(Color.parseColor("#00E676"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#132820"))
                setStroke(dp(1), Color.parseColor("#00E676"))
            }
        }
        versionStrip.addView(appTitleTv)
        versionStrip.addView(versionBadgeTv)
        verticalShell.addView(versionStrip)

        // Fragment Host Container
        val fragmentHost = FrameLayout(this).apply {
            id = FRAGMENT_CONTAINER_ID
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        verticalShell.addView(fragmentHost)

        // Bottom Navigation Bar
        val bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#11171F"))
                setStroke(dp(1), Color.parseColor("#1F2B38"))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        navVideosTab = createBottomNavTab("🎬 Videos") { switchTab(0) }
        navMusicTab = createBottomNavTab("🎧 Music") { switchTab(1) }
        navToolkitTab = createBottomNavTab("🛠 Toolkit") { switchTab(2) }

        bottomNav.addView(navVideosTab)
        bottomNav.addView(navMusicTab)
        bottomNav.addView(navToolkitTab)

        verticalShell.addView(bottomNav)
        rootFrame.addView(verticalShell)
    }

    private fun createBottomNavTab(label: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun switchTab(index: Int) {
        activeTabIndex = index
        val fragment: Fragment = when (index) {
            0 -> VideoFragment()
            1 -> AudioFragment()
            else -> ToolkitFragment()
        }

        supportFragmentManager.beginTransaction()
            .replace(FRAGMENT_CONTAINER_ID, fragment)
            .commitAllowingStateLoss()

        styleNavTab(navVideosTab, index == 0)
        styleNavTab(navMusicTab, index == 1)
        styleNavTab(navToolkitTab, index == 2)
    }

    private fun styleNavTab(tab: TextView, selected: Boolean) {
        if (selected) {
            tab.setTextColor(Color.BLACK)
            tab.background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#00E676"))
            }
        } else {
            tab.setTextColor(Color.parseColor("#90A4AE"))
            tab.background = null
        }
    }

    private fun requestStoragePermissionsIfNeeded() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_MEDIA_PERMISSIONS)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
