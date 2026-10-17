package com.example.piecontrols

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Collections

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var homeScroll: ScrollView
    private lateinit var settingsScroll: ScrollView
    private lateinit var dragContainer: LinearLayout
    private lateinit var overlayCard: LinearLayout
    private lateinit var accessibilityCard: LinearLayout
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var selectedAppLabel: TextView
    private lateinit var homeNavTab: LinearLayout
    private lateinit var settingsNavTab: LinearLayout
    private lateinit var homeNavIcon: TextView
    private lateinit var homeNavText: TextView
    private lateinit var settingsNavIcon: TextView
    private lateinit var settingsNavText: TextView
    private val themeCards = mutableMapOf<String, LinearLayout>()
    private val currentTiles = mutableListOf<TileData>()

    private val allActions = mapOf(
        0 to "Home", 1 to "Screenshot", 2 to "Back",
        3 to "Volume", 4 to "Recents", 5 to "Notifications", 6 to "Open App"
    )

    private data class TileData(val id: Int, val name: String)

    // Flowspace Coral palette — dynamically switches between Light and Dark M3 Expressive tones.
    private object Coral {
        var isDark = false

        val background get() = if (isDark) "#160F12" else "#FFFAFA"
        val surface get() = if (isDark) "#24191D" else "#FFEDF1"
        val surfaceHigh get() = if (isDark) "#35252B" else "#E4BDC8"
        val selected get() = if (isDark) "#6E4353" else "#926878"
        val primary get() = if (isDark) "#FF85A6" else "#B24465"
        val onPrimary get() = if (isDark) "#2D0815" else "#FFFFFF"
        val text get() = if (isDark) "#FCE8ED" else "#140707"
        val muted get() = if (isDark) "#BFA3AB" else "#4A3239"
        val outline get() = if (isDark) "#4F3941" else "#D9C2C7"
        val success get() = if (isDark) "#66BB88" else "#4BB68B"
        val error get() = if (isDark) "#F2B8B5" else "#D32F2F"
    }

    private fun toggleTheme() {
        val newDarkState = !Coral.isDark
        Coral.isDark = newDarkState
        prefs.edit().putBoolean("PREF_DARK_THEME", newDarkState).apply()
        recreate()
    }

    private fun color(value: String) = Color.parseColor(value)

    private fun rounded(fill: String, stroke: String? = null, radius: Float = 24f) =
        GradientDrawable().apply {
            setColor(color(fill))
            if (stroke != null) setStroke(dp(1), color(stroke))
            cornerRadius = dp(radius).toFloat()
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density)

    private fun text(
        value: String, size: Float, tint: String = Coral.text, bold: Boolean = false
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color(tint))
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setLineSpacing(dp(2).toFloat(), 1.05f)
    }

    private fun card(padding: Int = 20): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
            background = rounded(Coral.surface, Coral.outline, 20f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }

    private fun section(title: String, subtitle: String = ""): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(18), dp(2), dp(12))
            addView(text(title, 13f, Coral.muted, true), LinearLayout.LayoutParams(0, -2, 1f))
            if (subtitle.isNotBlank()) addView(text(subtitle, 11f, Coral.primary))
        }

    private val backupLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let { performBackup(it) }
        }

    private val restoreLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { performRestore(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("PiePrefs", Context.MODE_PRIVATE)
        Coral.isDark = prefs.getBoolean("PREF_DARK_THEME", false)

        val root = FrameLayout(this).apply {
            setBackgroundColor(color(Coral.background))
            fitsSystemWindows = true
        }

        homeScroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(16), dp(20), dp(16), dp(110))
        }
        val home = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        home.addView(createTopBar())
        home.addView(createTopControlPanel())
        home.addView(createHeroCard())
        home.addView(section("YOUR CONTROLS", "FLOWSPACE"))
        home.addView(createQuickActions())
        home.addView(section("APPEARANCE", "Choose a style"))
        home.addView(createThemeStylePanel())
        home.addView(section("CUSTOM APP SHORTCUT", "Tap to change"))
        home.addView(createAppSelectPanel())
        home.addView(section("EDGE BAR SETTINGS"))
        home.addView(createSlidersPanel())
        home.addView(section("RADIAL ACTION ORDER", "Long press to move"))
        home.addView(createDragDropPanel())
        home.addView(section("DEVELOPER", "Open source"))
        home.addView(createDeveloperProfileCard())
        homeScroll.addView(home)
        root.addView(homeScroll)

        settingsScroll = ScrollView(this).apply {
            visibility = View.GONE
            clipToPadding = false
            setPadding(dp(16), dp(24), dp(16), dp(110))
        }
        val settings = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        settings.addView(createTopTitle("Settings & backup", "Your layout stays on this device."))
        settings.addView(section("DATA MANAGEMENT", "Export or import"))
        settings.addView(createBackupRestorePanel())
        settings.addView(section("ACCOUNT & BILLING"))
        settings.addView(createSubscriptionPanel())
        settings.addView(section("DEVELOPER", "Creator profile"))
        settings.addView(createDeveloperProfileCard())
        settingsScroll.addView(settings)
        root.addView(settingsScroll)
        root.addView(createBottomNavBar())

        setContentView(root)
        loadTiles()
    }

    override fun onResume() {
        super.onResume()
        if (::statusDot.isInitialized) refreshPermissionStates()
    }

    private fun createTopTitle(title: String, subtitle: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(18))
            addView(text(title, 27f, Coral.text, true))
            addView(text(subtitle, 14f, Coral.muted))
        }

    private fun createTopBar(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(18))
            val mark = TextView(this@MainActivity).apply {
                text = "◉"
                textSize = 27f
                setTextColor(color("#FED9E4"))
                gravity = Gravity.CENTER
                background = rounded(Coral.selected, radius = 16f)
                layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)).apply {
                    marginEnd = dp(12)
                }
            }
            addView(mark)
            val titleBox = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(text("Flowspace", 25f, Coral.text, true))
                addView(text("Your personal action hub", 12f, Coral.muted))
            }
            addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
            val settingsButton = TextView(this@MainActivity).apply {
                text = "⚙"
                textSize = 24f
                gravity = Gravity.CENTER
                setTextColor(color(Coral.text))
                background = rounded(Coral.surfaceHigh, radius = 24f)
                contentDescription = "Switch theme"
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                setOnClickListener { toggleTheme() }
            }
            addView(settingsButton)
        }

    private fun createHeroCard(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(22))
            background = rounded(Coral.selected, radius = 28f)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
            addView(text("TACTILE MOMENTUM", 11f, "#FED9E4", true))
            addView(text("Your controls,\nin your flow.", 29f, "#FED9E4", true).apply {
                setPadding(0, dp(10), 0, dp(8))
            })
            addView(text(
                "Manage your edge bar, choose a visual style, and arrange the actions you use most.",
                14f, "#FED9E4"
            ))
            val progressRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(18), 0, 0)
                addView(text("RADIAL HUB", 12f, "#FED9E4", true),
                    LinearLayout.LayoutParams(0, -2, 1f))
                addView(text(
                    if (prefs.getBoolean("PREF_SERVICE_ENABLED", true)) "READY TO CONFIGURE" else "PAUSED",
                    10f, "#FED9E4", true
                ))
            }
            addView(progressRow)
        }

    private fun createQuickActions(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val rows = listOf(
                Triple("◉", "Edge bar & permissions", "Toggle the service and manage access"),
                Triple("◈", "Visual style", "Simple, Neon or Glass appearance"),
                Triple("☷", "Action order", "Long-press a tile to move it")
            )
            rows.forEach { (icon, title, desc) ->
                val row = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(14), dp(14), dp(14))
                    background = rounded(Coral.surface, Coral.outline, 18f)
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                        bottomMargin = dp(8)
                    }
                    val iconView = TextView(this@MainActivity).apply {
                        text = icon
                        textSize = 22f
                        gravity = Gravity.CENTER
                        setTextColor(color(Coral.onPrimary))
                        background = rounded(Coral.primary, radius = 14f)
                        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                            marginEnd = dp(12)
                        }
                    }
                    addView(iconView)
                    val labels = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(text(title, 15f, Coral.text, true))
                        addView(text(desc, 12f, Coral.muted))
                    }
                    addView(labels)
                }
                addView(row)
            }
        }

    private fun createBottomNavBar(): View =
        FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
                .apply { setMargins(dp(16), 0, dp(16), dp(16)) }
            val bar = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = rounded(Coral.surfaceHigh, Coral.outline, 28f)
            }
            homeNavTab = createNavTab("⌂", "Hub", true) { switchTab(true) }
            homeNavIcon = homeNavTab.getChildAt(0) as TextView
            homeNavText = homeNavTab.getChildAt(1) as TextView
            settingsNavTab = createNavTab("⚙", "Setup", false) { switchTab(false) }
            settingsNavIcon = settingsNavTab.getChildAt(0) as TextView
            settingsNavText = settingsNavTab.getChildAt(1) as TextView
            bar.addView(homeNavTab)
            bar.addView(settingsNavTab)
            addView(bar)
        }

    private fun createNavTab(icon: String, title: String, active: Boolean, action: () -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(9), dp(10), dp(9))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            background = if (active) rounded(Coral.selected, radius = 22f) else null
            val iconView = text(icon, 21f, if (active) Coral.primary else Coral.muted, true)
            iconView.gravity = Gravity.CENTER
            val titleView = text(title, 11f, if (active) Coral.primary else Coral.muted, true)
            titleView.gravity = Gravity.CENTER
            addView(iconView)
            addView(titleView)
            isClickable = true
            setOnClickListener { action() }
        }

    private fun switchTab(isHome: Boolean) {
        homeScroll.visibility = if (isHome) View.VISIBLE else View.GONE
        settingsScroll.visibility = if (isHome) View.GONE else View.VISIBLE
        homeNavTab.background = if (isHome) rounded(Coral.selected, radius = 22f) else null
        settingsNavTab.background = if (!isHome) rounded(Coral.selected, radius = 22f) else null
        homeNavIcon.setTextColor(color(if (isHome) Coral.primary else Coral.muted))
        homeNavText.setTextColor(color(if (isHome) Coral.primary else Coral.muted))
        settingsNavIcon.setTextColor(color(if (!isHome) Coral.primary else Coral.muted))
        settingsNavText.setTextColor(color(if (!isHome) Coral.primary else Coral.muted))
    }

    private fun createTopControlPanel(): View {
        val panel = card(20)
        val switchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("Radial controls", 18f, Coral.text, true))
            addView(text("Quick toggle edge bar on or off", 12f, Coral.muted))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val masterSwitch = SwitchCompat(this).apply {
            isChecked = prefs.getBoolean("PREF_SERVICE_ENABLED", true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("PREF_SERVICE_ENABLED", checked).apply()
                Toast.makeText(this@MainActivity,
                    if (checked) "Controls enabled in preferences" else "Controls paused in preferences",
                    Toast.LENGTH_SHORT).show()
            }
        }
        switchRow.addView(titleLayout)
        switchRow.addView(masterSwitch)
        panel.addView(switchRow)
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(18), 0, dp(12))
        }
        statusDot = View(this).apply {
            background = rounded(Coral.error, radius = 20f)
            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(8) }
        }
        statusText = text("CHECKING PERMISSIONS", 11f, Coral.error, true)
        statusRow.addView(statusDot)
        statusRow.addView(statusText)
        panel.addView(statusRow)
        val permissionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        overlayCard = permissionBox("Screen overlay").apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) }
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            }
        }
        accessibilityCard = permissionBox("Accessibility").apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) }
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        permissionRow.addView(overlayCard)
        permissionRow.addView(accessibilityCard)
        panel.addView(permissionRow)
        return panel
    }

    private fun permissionBox(title: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(16), dp(14), dp(16))
        background = rounded(Coral.surfaceHigh, Coral.outline, 16f)
        addView(text(title, 13f, Coral.primary, true))
        addView(text("Tap to manage access", 11f, Coral.muted))
    }

    private fun refreshPermissionStates() {
        if (!::statusDot.isInitialized) return
        val granted = Settings.canDrawOverlays(this)
        statusDot.background = rounded(if (granted) Coral.success else Coral.error, radius = 20f)
        statusText.text = if (granted) "OVERLAY PERMISSION GRANTED" else "OVERLAY PERMISSION NEEDED"
        statusText.setTextColor(color(if (granted) Coral.success else Coral.error))
        overlayCard.background = rounded(
            if (granted) "#24382B" else Coral.surfaceHigh, Coral.outline, 16f
        )
    }

    private fun createThemeStylePanel(): View {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val styles = listOf(
            Triple("Simple", "Flat colors", "Simple"),
            Triple("Neon", "Glowing hover", "Neon"),
            Triple("Glass", "Frosted & blurry", "Glass")
        )
        val selected = prefs.getString("PREF_VISUAL_STYLE", "Neon") ?: "Neon"
        styles.forEach { style ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(18), dp(18), dp(18))
                layoutParams = LinearLayout.LayoutParams(dp(180), -2).apply {
                    marginEnd = dp(10); bottomMargin = dp(10)
                }
                addView(text(style.first, 16f, Coral.text, true))
                addView(text(style.second, 12f, Coral.muted))
                setOnClickListener {
                    prefs.edit().putString("PREF_VISUAL_STYLE", style.third).apply()
                    updateThemeSelectionUI(style.third)
                    Toast.makeText(this@MainActivity, "${style.first} style selected",
                        Toast.LENGTH_SHORT).show()
                }
            }
            themeCards[style.third] = item
            row.addView(item)
        }
        scroll.addView(row)
        updateThemeSelectionUI(selected)
        return scroll
    }

    private fun updateThemeSelectionUI(selectedTheme: String) {
        themeCards.forEach { (name, item) ->
            item.background = rounded(
                if (name == selectedTheme) Coral.selected else Coral.surface,
                if (name == selectedTheme) Coral.primary else Coral.outline, 20f
            )
        }
    }

    private fun createAppSelectPanel(): View {
        val panel = card()
        selectedAppLabel = text(
            "Selected app: ${prefs.getString("PREF_CUSTOM_APP_NAME", "YouTube")}",
            15f, Coral.text, true
        )
        panel.addView(selectedAppLabel)
        panel.addView(pillButton("Choose app") { showAppPicker() })
        return panel
    }

    private fun pillButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        setTextColor(color(Coral.onPrimary))
        background = rounded(Coral.primary, radius = 28f)
        minHeight = dp(48)
        setOnClickListener { action() }
    }

    private fun showAppPicker() {
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(intent, 0)
            .map { it.loadLabel(packageManager).toString() to it.activityInfo.packageName }
            .sortedBy { it.first }
        if (apps.isEmpty()) {
            Toast.makeText(this, "No launchable apps found", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Choose app")
            .setItems(apps.map { it.first }.toTypedArray()) { _, index ->
                val (name, packageName) = apps[index]
                prefs.edit().putString("PREF_CUSTOM_APP_NAME", name)
                    .putString("PREF_CUSTOM_APP_PKG", packageName).apply()
                selectedAppLabel.text = "Selected app: $name"
                Toast.makeText(this, "Saved. Reload the service to apply.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createSlidersPanel(): View {
        val panel = card()
        fun sliderRow(title: String, key: String, default: Int, max: Int, offset: Int = 0) {
            val label = text("$title  ${prefs.getInt(key, default)}", 14f, Coral.text, true)
            panel.addView(label)
            val slider = SeekBar(this).apply {
                this.max = max
                progress = (prefs.getInt(key, default) - offset).coerceIn(0, max)
                progressTintList = android.content.res.ColorStateList.valueOf(color(Coral.primary))
                thumbTintList = android.content.res.ColorStateList.valueOf(color(Coral.primary))
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                        val stored = value + offset
                        label.text = "$title  $stored"
                        if (fromUser) prefs.edit().putInt(key, stored).apply()
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            panel.addView(slider)
        }
        sliderRow("Bar height", "PREF_BAR_HEIGHT", 750, 1200, 200)
        sliderRow("Bar width", "PREF_BAR_WIDTH", 55, 80, 20)
        sliderRow("Bar opacity (%)", "PREF_BAR_ALPHA", 100, 100)
        val posLabel = text("Vertical position", 14f, Coral.text, true)
        panel.addView(posLabel)
        val pos = SeekBar(this).apply {
            max = 1000
            progress = (prefs.getInt("PREF_BAR_POS", 0) + 500).coerceIn(0, 1000)
            progressTintList = android.content.res.ColorStateList.valueOf(color(Coral.primary))
            thumbTintList = android.content.res.ColorStateList.valueOf(color(Coral.primary))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                    posLabel.text = "Vertical position  ${value - 500}"
                    if (fromUser) prefs.edit().putInt("PREF_BAR_POS", value - 500).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        panel.addView(pos)
        return panel
    }

    private fun createDragDropPanel(): View {
        dragContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(Coral.surface, Coral.outline, 20f)
        }
        return dragContainer
    }

    private fun loadTiles() {
        val saved = prefs.getString("PREF_TILE_ACTIONS", "2,0,6,4,1") ?: "2,0,6,4,1"
        currentTiles.clear()
        saved.split(",").mapNotNull { it.toIntOrNull() }.forEach { id ->
            currentTiles.add(TileData(id, allActions[id] ?: "Unknown"))
        }
        renderTiles()
    }

    private fun renderTiles() {
        dragContainer.removeAllViews()
        currentTiles.forEachIndexed { index, tile ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = rounded(Coral.surfaceHigh, Coral.outline, 16f)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                    setMargins(0, dp(4), 0, dp(4))
                }
                tag = index
                addView(text("⠿", 22f, Coral.primary, true).apply {
                    setPadding(0, 0, dp(14), 0)
                })
                addView(text(tile.name, 15f, Coral.text, true))
            }
            row.setOnLongClickListener { view ->
                val data = ClipData(
                    view.tag.toString(),
                    arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN),
                    ClipData.Item(index.toString())
                )
                view.startDragAndDrop(data, View.DragShadowBuilder(view), null, 0)
                true
            }
            row.setOnDragListener { view, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> true
                    DragEvent.ACTION_DRAG_ENTERED -> {
                        view.background = rounded(Coral.selected, Coral.primary, 16f); true
                    }
                    DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
                        view.background = rounded(Coral.surfaceHigh, Coral.outline, 16f); true
                    }
                    DragEvent.ACTION_DROP -> {
                        val from = event.clipData.getItemAt(0).text.toString().toIntOrNull()
                        val to = view.tag as? Int
                        if (from != null && to != null && from in currentTiles.indices && to in currentTiles.indices) {
                            Collections.swap(currentTiles, from, to)
                            saveTileOrder()
                            renderTiles()
                        }
                        true
                    }
                    else -> false
                }
            }
            dragContainer.addView(row)
        }
    }

    private fun saveTileOrder() {
        prefs.edit().putString(
            "PREF_TILE_ACTIONS", currentTiles.joinToString(",") { it.id.toString() }
        ).apply()
    }

    private fun createBackupRestorePanel(): View {
        val panel = card()
        panel.addView(text("Create a copy of your settings or restore a previous file.",
            13f, Coral.muted).apply { setPadding(0, 0, 0, dp(12)) })
        panel.addView(pillButton("Create backup") {
            backupLauncher.launch("pie_backup.json")
        })
        panel.addView(pillButton("Restore backup") {
            restoreLauncher.launch(arrayOf("application/json", "*/*"))
        })
        return panel
    }

    private fun performBackup(uri: Uri) {
        try {
            val json = JSONObject()
            prefs.all.forEach { (key, value) -> json.put(key, value) }
            contentResolver.openOutputStream(uri)?.use {
                it.write(json.toString(2).toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("Could not open output file")
            Toast.makeText(this, "Backup saved successfully", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun performRestore(uri: Uri) {
        try {
            val content = contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input)).readText()
            } ?: throw IllegalStateException("Could not read file")
            val json = JSONObject(content)
            val editor = prefs.edit()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                when (val value = json.get(key)) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Double -> editor.putFloat(key, value.toFloat())
                    is String -> editor.putString(key, value)
                    JSONObject.NULL -> editor.remove(key)
                }
            }
            editor.apply()
            Toast.makeText(this, "Backup restored. Refreshing settings…", Toast.LENGTH_SHORT).show()
            recreate()
        } catch (e: Exception) {
            Toast.makeText(this, "Restore failed: invalid or unreadable file", Toast.LENGTH_LONG).show()
        }
    }

    private fun createSubscriptionPanel(): View {
        val panel = card()
        panel.addView(text("FLOWSPACE PRO", 11f, Coral.primary, true))
        panel.addView(text("Unlock all features", 21f, Coral.text, true).apply {
            setPadding(0, dp(8), 0, dp(8))
        })
        panel.addView(text(
            "Manage premium features, themes, and additional action tiles.",
            13f, Coral.muted
        ).apply { setPadding(0, 0, 0, dp(12)) })
        panel.addView(pillButton("Manage subscription") {
            Toast.makeText(this, "Billing is not connected in this build.", Toast.LENGTH_SHORT).show()
        })
        return panel
    }

    // Material 3 Expressive Developer Profile Card
    private fun createDeveloperProfileCard(): View {
        val panel = card(20)

        val profileRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(16))
        }

        // Circular frame with primary accent ring around the profile photo
        val avatarContainer = FrameLayout(this).apply {
            val size = dp(64)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dp(16)
            }
            background = rounded(Coral.surfaceHigh, Coral.primary, 32f)
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }

        val avatarImage = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP

            // Dynamically resolves R.drawable.profile from app/src/main/res/drawable/profile.jpg
            val resId = resources.getIdentifier("profile", "drawable", packageName)
            if (resId != 0) {
                setImageResource(resId)
            }

            // Clips the square JPG into a clean circle so the black corners are hidden
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
        }
        avatarContainer.addView(avatarImage)
        profileRow.addView(avatarContainer)

        val infoColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(text("DEVELOPER", 11f, Coral.primary, true))
            addView(text("Luckybdsh", 20f, Coral.text, true).apply {
                setPadding(0, dp(2), 0, dp(2))
            })
            addView(text("Creator of Flowspace & Radial Controls", 12f, Coral.muted))
        }
        profileRow.addView(infoColumn)
        panel.addView(profileRow)

        // Button with GitHub text logo (</>) before "GitHub" that opens https://github.com/Luckybdsh
        val githubButton = pillButton("</>  GitHub") {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Luckybdsh"))
            startActivity(intent)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        panel.addView(githubButton)

        return panel
    }
}
