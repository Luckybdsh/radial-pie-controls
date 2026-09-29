package com.example.piecontrols

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
    private lateinit var dragContainer: LinearLayout
    private var currentTiles = mutableListOf<TileData>()
    
    private lateinit var overlayCard: LinearLayout
    private lateinit var accessibilityCard: LinearLayout
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var selectedAppLabel: TextView

    private val themeCards = mutableMapOf<String, LinearLayout>()

    // Navigation views
    private lateinit var homeScroll: ScrollView
    private lateinit var settingsScroll: ScrollView
    private lateinit var homeNavTab: LinearLayout
    private lateinit var settingsNavTab: LinearLayout
    private lateinit var homeNavIcon: TextView
    private lateinit var homeNavText: TextView
    private lateinit var settingsNavIcon: TextView
    private lateinit var settingsNavText: TextView

    private val allActions = mapOf(
        0 to "Home", 1 to "Screenshot", 2 to "Back",
        3 to "Volume", 4 to "Recents", 5 to "Notifications", 6 to "Open App"
    )

    data class TileData(val id: Int, val name: String)

    // =======================================================================
    // BACKUP & RESTORE ENGINES (JSON Serializers)
    // =======================================================================

    private val backupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { performBackup(it) }
    }

    private val restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { performRestore(it) }
    }

    private fun performBackup(uri: Uri) {
        try {
            val allPrefs = prefs.all
            val jsonObject = JSONObject()
            for ((key, value) in allPrefs) jsonObject.put(key, value)
            contentResolver.openOutputStream(uri)?.use { it.write(jsonObject.toString().toByteArray()) }
            Toast.makeText(this, "Backup saved successfully!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun performRestore(uri: Uri) {
        try {
            val stringBuilder = StringBuilder()
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) { stringBuilder.append(line); line = reader.readLine() }
                }
            }
            val jsonObject = JSONObject(stringBuilder.toString())
            val editor = prefs.edit()
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                when (val value = jsonObject.get(key)) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is String -> editor.putString(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Long -> editor.putLong(key, value)
                }
            }
            editor.apply()
            Toast.makeText(this, "Backup restored! Restarting app...", Toast.LENGTH_SHORT).show()
            finish(); startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Restore failed: Invalid file", Toast.LENGTH_LONG).show()
        }
    }

    // =======================================================================
    // LIFECYCLE & UI SETUP
    // =======================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("PiePrefs", Context.MODE_PRIVATE)

        val rootFrame = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#09090B")) }
        val bubbleBg = BubbleBackgroundView(this)
        rootFrame.addView(bubbleBg, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // HOME PAGE
        homeScroll = ScrollView(this).apply { isFillViewport = true; setPadding(48, 64, 48, 240); clipToPadding = false }
        val homeLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        homeLayout.addView(createTopControlPanel())
        homeLayout.addView(createSectionTitle("TILE THEME", "Visual Style"))
        homeLayout.addView(createThemeStylePanel())
        homeLayout.addView(createSectionTitle("CUSTOM APP SHORTCUT", "Pro Tier Required"))
        homeLayout.addView(createAppSelectPanel())
        homeLayout.addView(createSectionTitle("EDGE BAR SETTINGS"))
        homeLayout.addView(createSlidersPanel())
        homeLayout.addView(createSectionTitle("DRAG & DROP TILES", "Long press to move"))
        homeLayout.addView(createDragDropPanel())
        homeScroll.addView(homeLayout)
        rootFrame.addView(homeScroll)

        // SETTINGS PAGE
        settingsScroll = ScrollView(this).apply { isFillViewport = true; visibility = View.GONE; setPadding(48, 64, 48, 240); clipToPadding = false }
        val settingsLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        settingsLayout.addView(createSectionTitle("DATA MANAGEMENT", "Pro Tier Required"))
        settingsLayout.addView(createBackupRestorePanel())
        settingsLayout.addView(createSectionTitle("UNLOCK PREMIUM", "Enter passcodes to upgrade"))
        settingsLayout.addView(createSubscriptionPanel())
        settingsScroll.addView(settingsLayout)
        rootFrame.addView(settingsScroll)

        // BOTTOM NAV
        rootFrame.addView(createBottomNavBar())
        setContentView(rootFrame)
        loadTiles()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStates()
    }

    // --- BOTTOM NAVIGATION BAR ---
    private fun createBottomNavBar(): View {
        val navContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.BOTTOM; setMargins(48, 0, 48, 48) }
        }
        val pillBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(20, 16, 20, 16)
            background = GradientDrawable().apply { setColor(Color.parseColor("#181820")); setStroke(2, Color.parseColor("#2E2E3C")); cornerRadius = 64f }
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        homeNavTab = createNavTab("⌂", "Home", true) { switchTab(isHome = true) }
        homeNavIcon = homeNavTab.getChildAt(0) as TextView; homeNavText = homeNavTab.getChildAt(1) as TextView
        settingsNavTab = createNavTab("⚙", "Settings", false) { switchTab(isHome = false) }
        settingsNavIcon = settingsNavTab.getChildAt(0) as TextView; settingsNavText = settingsNavTab.getChildAt(1) as TextView

        pillBar.addView(homeNavTab); pillBar.addView(settingsNavTab)
        navContainer.addView(pillBar)
        return navContainer
    }

    private fun createNavTab(icon: String, title: String, isActive: Boolean, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(0, 16, 0, 16)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (isActive) background = GradientDrawable().apply { setColor(Color.parseColor("#202738")); cornerRadius = 48f }
            addView(TextView(this@MainActivity).apply { text = icon; textSize = 18f; gravity = Gravity.CENTER; setTextColor(if (isActive) Color.parseColor("#2979FF") else Color.parseColor("#8E8E93")) })
            addView(TextView(this@MainActivity).apply { text = title; textSize = 11f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setPadding(0, 4, 0, 0); setTextColor(if (isActive) Color.parseColor("#2979FF") else Color.parseColor("#8E8E93")) })
            setOnClickListener { onClick() }
        }
    }

    private fun switchTab(isHome: Boolean) {
        homeScroll.visibility = if (isHome) View.VISIBLE else View.GONE
        settingsScroll.visibility = if (isHome) View.GONE else View.VISIBLE
        homeNavTab.background = if (isHome) GradientDrawable().apply { setColor(Color.parseColor("#202738")); cornerRadius = 48f } else null
        settingsNavTab.background = if (!isHome) GradientDrawable().apply { setColor(Color.parseColor("#202738")); cornerRadius = 48f } else null
        homeNavIcon.setTextColor(Color.parseColor(if (isHome) "#2979FF" else "#8E8E93")); homeNavText.setTextColor(Color.parseColor(if (isHome) "#2979FF" else "#8E8E93"))
        settingsNavIcon.setTextColor(Color.parseColor(if (!isHome) "#2979FF" else "#8E8E93")); settingsNavText.setTextColor(Color.parseColor(if (!isHome) "#2979FF" else "#8E8E93"))
    }

    // --- HOME COMPONENTS ---
    private fun createTopControlPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40)
            background = GradientDrawable().apply { setColor(Color.parseColor("#121214")); cornerRadius = 40f }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 40) }
        }
        val switchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, 32) }
        val titleTextLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        titleTextLayout.addView(TextView(this).apply { text = "Pie Controls"; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
        titleTextLayout.addView(TextView(this).apply { text = "Quick toggle edge bar On or Off"; textSize = 11f; setTextColor(Color.parseColor("#8E8E93")) })
        val masterSwitch = SwitchCompat(this).apply {
            isChecked = prefs.getBoolean("PREF_SERVICE_ENABLED", true)
            setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("PREF_SERVICE_ENABLED", isChecked).apply() }
        }
        switchRow.addView(titleTextLayout); switchRow.addView(masterSwitch); panel.addView(switchRow)

        val statusRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, 24) }
        statusDot = View(this).apply { layoutParams = LinearLayout.LayoutParams(18, 18).apply { setMargins(0, 0, 16, 0) }; background = GradientDrawable().apply { setColor(Color.parseColor("#FF453A")); cornerRadius = 90f } }
        statusText = TextView(this).apply { text = "SERVICE STATUS"; textSize = 10f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#FF453A")) }
        statusRow.addView(statusDot); statusRow.addView(statusText); panel.addView(statusRow)

        val permissionsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 16, 0, 0); weightSum = 2f }
        overlayCard = createPermissionBox("Screen Overlay").apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, 16, 0) }; setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) } }
        accessibilityCard = createPermissionBox("Accessibility").apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(16, 0, 0, 0) }; setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }
        permissionsRow.addView(overlayCard); permissionsRow.addView(accessibilityCard); panel.addView(permissionsRow)
        return panel
    }

    private fun createPermissionBox(title: String): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32)
            background = GradientDrawable().apply { setColor(Color.parseColor("#1A0909")); setStroke(3, Color.parseColor("#3D1616")); cornerRadius = 24f }
        }
        box.addView(TextView(this).apply { text = title; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#FF8A80")) })
        return box
    }

    private fun refreshPermissionStates() {
        if (Settings.canDrawOverlays(this)) {
            statusDot.background = GradientDrawable().apply { setColor(Color.parseColor("#34C759")); cornerRadius = 90f }
            statusText.text = "SERVICE READY (Tap Accessibility to Restart)"; statusText.setTextColor(Color.parseColor("#34C759"))
            overlayCard.background = GradientDrawable().apply { setColor(Color.parseColor("#091A0F")); setStroke(3, Color.parseColor("#163D22")); cornerRadius = 24f }
        }
    }

    private fun createThemeStylePanel(): View {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, 40) }
        val styles = listOf(Triple("Simple", "Flat Colors", "Simple"), Triple("Neon", "Requires Beginner", "Neon"), Triple("Glass", "Requires Pro", "Glass"))
        val currentTheme = prefs.getString("PREF_VISUAL_STYLE", "Simple") ?: "Simple"

        styles.forEach { style ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40)
                layoutParams = LinearLayout.LayoutParams(380, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 32, 0) }
                setOnClickListener {
                    val userTier = prefs.getInt("PREF_USER_TIER", 0)
                    val requiredTier = when (style.third) { "Neon" -> 1; "Glass" -> 2; else -> 0 }
                    
                    if (userTier >= requiredTier) {
                        prefs.edit().putString("PREF_VISUAL_STYLE", style.third).apply()
                        updateThemeSelectionUI(style.third) 
                    } else {
                        Toast.makeText(this@MainActivity, "Requires ${if (requiredTier==1) "Beginner" else "Pro"} Tier!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            card.addView(TextView(this).apply { text = style.first; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); setPadding(0, 0, 0, 8) })
            card.addView(TextView(this).apply { text = style.second; textSize = 11f; setTextColor(Color.parseColor("#8E8E93")) })
            themeCards[style.third] = card
            row.addView(card)
        }
        updateThemeSelectionUI(currentTheme); scroll.addView(row)
        return scroll
    }

    private fun updateThemeSelectionUI(selectedTheme: String) {
        themeCards.forEach { (themeName, card) ->
            card.background = if (themeName == selectedTheme) GradientDrawable().apply { setColor(Color.parseColor("#1C1C22")); setStroke(5, Color.parseColor("#2979FF")); cornerRadius = 32f }
            else GradientDrawable().apply { setColor(Color.parseColor("#121214")); setStroke(0, Color.TRANSPARENT); cornerRadius = 32f }
        }
    }

    private fun createAppSelectPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40)
            background = GradientDrawable().apply { setColor(Color.parseColor("#121214")); cornerRadius = 40f }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 40) }
        }
        val currentAppName = prefs.getString("PREF_CUSTOM_APP_NAME", "None Selected")
        selectedAppLabel = TextView(this).apply { text = "Selected: $currentAppName"; setTextColor(Color.WHITE); textSize = 16f; setPadding(0, 0, 0, 32) }
        val selectBtn = Button(this).apply {
            text = "Choose App"
            setBackgroundColor(Color.parseColor("#2979FF")); setTextColor(Color.WHITE)
            setOnClickListener { 
                if (prefs.getInt("PREF_USER_TIER", 0) >= 2) showAppPicker() 
                else Toast.makeText(this@MainActivity, "Custom App Shortcuts require Pro Tier!", Toast.LENGTH_SHORT).show()
            }
        }
        panel.addView(selectedAppLabel); panel.addView(selectBtn)
        return panel
    }

    private fun showAppPicker() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val appList = resolveInfos.map { Pair(it.loadLabel(pm).toString(), it.activityInfo.packageName) }.sortedBy { it.first }
        val names = appList.map { it.first }.toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Select App").setItems(names) { _, which ->
            prefs.edit().putString("PREF_CUSTOM_APP_NAME", appList[which].first).putString("PREF_CUSTOM_APP_PKG", appList[which].second).apply()
            selectedAppLabel.text = "Selected: ${appList[which].first}"
            Toast.makeText(this, "Saved! Toggle Service to reload.", Toast.LENGTH_SHORT).show()
        }.show()
    }

    private fun createSectionTitle(title: String, subtitle: String = ""): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 32, 0, 24) }
        row.addView(TextView(this).apply { text = title; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#8E8E93")); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        if (subtitle.isNotEmpty()) row.addView(TextView(this).apply { text = subtitle; textSize = 10f; setTextColor(Color.parseColor("#2979FF")) })
        return row
    }

    private fun createSlidersPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40)
            background = GradientDrawable().apply { setColor(Color.parseColor("#121214")); cornerRadius = 40f }
        }
        fun createLabelRow(title: String, percentView: TextView?): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(0, 16, 0, 16)
                addView(TextView(this@MainActivity).apply { text = title; setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
                if (percentView != null) addView(percentView)
            }
        }
        val heightPercent = TextView(this).apply { setTextColor(Color.parseColor("#2979FF")); typeface = Typeface.DEFAULT_BOLD }
        val heightSlider = SeekBar(this).apply {
            max = 1200; progress = prefs.getInt("PREF_BAR_HEIGHT", 750) - 200; heightPercent.text = "${(progress * 100 / max)}%"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, prog: Int, f: Boolean) { prefs.edit().putInt("PREF_BAR_HEIGHT", prog + 200).apply(); heightPercent.text = "${(prog * 100 / max)}%" }
                override fun onStartTrackingTouch(s: SeekBar?) {}; override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        panel.addView(createLabelRow("Bar Height", heightPercent)); panel.addView(heightSlider)

        val widthPercent = TextView(this).apply { setTextColor(Color.parseColor("#2979FF")); typeface = Typeface.DEFAULT_BOLD; setPadding(0,24,0,0) }
        val widthSlider = SeekBar(this).apply {
            max = 100; progress = prefs.getInt("PREF_BAR_WIDTH", 55) - 20; widthPercent.text = "$progress%"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, prog: Int, f: Boolean) { prefs.edit().putInt("PREF_BAR_WIDTH", prog + 20).apply(); widthPercent.text = "$prog%" }
                override fun onStartTrackingTouch(s: SeekBar?) {}; override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        panel.addView(createLabelRow("Bar Width", widthPercent)); panel.addView(widthSlider)

        val opacityPercent = TextView(this).apply { setTextColor(Color.parseColor("#2979FF")); typeface = Typeface.DEFAULT_BOLD; setPadding(0,24,0,0) }
        val opacitySlider = SeekBar(this).apply {
            max = 100; progress = prefs.getInt("PREF_BAR_ALPHA", 100); opacityPercent.text = "$progress%"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, prog: Int, f: Boolean) { prefs.edit().putInt("PREF_BAR_ALPHA", prog).apply(); opacityPercent.text = "$prog%" }
                override fun onStartTrackingTouch(s: SeekBar?) {}; override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        panel.addView(createLabelRow("Bar Opacity", opacityPercent)); panel.addView(opacitySlider)

        val posSlider = SeekBar(this).apply {
            max = 1000; progress = prefs.getInt("PREF_BAR_POS", 0) + 500
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, prog: Int, f: Boolean) { 
                    if (prefs.getInt("PREF_USER_TIER", 0) >= 3) {
                        prefs.edit().putInt("PREF_BAR_POS", prog - 500).apply()
                    } else {
                        progress = 500 // Snap back to center
                        Toast.makeText(this@MainActivity, "Custom Position requires Master Tier!", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}; override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        panel.addView(createLabelRow("Vertical Position (Master Tier)", null).apply { setPadding(0, 24, 0, 16) }); panel.addView(posSlider)
        return panel
    }

    private fun createDragDropPanel(): View {
        dragContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24); background = GradientDrawable().apply { setColor(Color.parseColor("#121214")); cornerRadius = 40f } }
        return dragContainer
    }

    private fun loadTiles() {
        val savedStr = prefs.getString("PREF_TILE_ACTIONS", "2,0,6,4,1") ?: "2,0,6,4,1"
        val ids = savedStr.split(",").mapNotNull { it.toIntOrNull() }
        currentTiles.clear()
        ids.forEach { id -> currentTiles.add(TileData(id, allActions[id] ?: "Unknown")) }
        renderTiles()
    }

    private fun renderTiles() {
        dragContainer.removeAllViews()
        currentTiles.forEachIndexed { index, tile ->
            val tileView = createTileRow(tile.name).apply { tag = index }
            tileView.setOnLongClickListener { v ->
                val dragData = ClipData(v.tag.toString(), arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN), ClipData.Item(index.toString()))
                v.startDragAndDrop(dragData, View.DragShadowBuilder(v), null, 0); true
            }
            tileView.setOnDragListener { v, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> true
                    DragEvent.ACTION_DRAG_ENTERED -> { v.setBackgroundColor(Color.parseColor("#292930")); true }
                    DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> { v.background = null; true }
                    DragEvent.ACTION_DROP -> {
                        v.background = null
                        Collections.swap(currentTiles, event.clipData.getItemAt(0).text.toString().toInt(), v.tag as Int)
                        prefs.edit().putString("PREF_TILE_ACTIONS", currentTiles.joinToString(",") { it.id.toString() }).apply()
                        renderTiles(); true
                    }
                    else -> false
                }
            }
            dragContainer.addView(tileView)
        }
    }

    private fun createTileRow(name: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(32, 32, 32, 32); gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 8, 0, 8) }
        }
        row.addView(TextView(this).apply { text = "≡"; textSize = 20f; setTextColor(Color.parseColor("#666666")); setPadding(0, 0, 32, 0) })
        row.addView(TextView(this).apply { text = name; textSize = 16f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD })
        return row
    }

    // --- SETTINGS & BACKUP PANELS ---
    private fun createBackupRestorePanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40)
            background = GradientDrawable().apply { setColor(Color.parseColor("#121214")); cornerRadius = 40f }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 40) }
        }

        val backupBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 16, 0, 32)
            addView(TextView(this@MainActivity).apply { text = "↑"; textSize = 24f; setTextColor(Color.parseColor("#2979FF")); setPadding(0, 0, 32, 0) })
            val textLayout = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            textLayout.addView(TextView(this@MainActivity).apply { text = "Create Backup"; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
            textLayout.addView(TextView(this@MainActivity).apply { text = "Save settings to a local file"; textSize = 11f; setTextColor(Color.parseColor("#8E8E93")) })
            addView(textLayout)
            setOnClickListener { 
                if (prefs.getInt("PREF_USER_TIER", 0) >= 2) backupLauncher.launch("pie_backup.json") 
                else Toast.makeText(this@MainActivity, "Backup requires Pro Tier!", Toast.LENGTH_SHORT).show()
            }
        }

        val restoreBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 16, 0, 16)
            addView(TextView(this@MainActivity).apply { text = "↓"; textSize = 24f; setTextColor(Color.parseColor("#34C759")); setPadding(0, 0, 32, 0) })
            val textLayout = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            textLayout.addView(TextView(this@MainActivity).apply { text = "Restore Backup"; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
            textLayout.addView(TextView(this@MainActivity).apply { text = "Load settings from a file"; textSize = 11f; setTextColor(Color.parseColor("#8E8E93")) })
            addView(textLayout)
            setOnClickListener { 
                if (prefs.getInt("PREF_USER_TIER", 0) >= 2) restoreLauncher.launch(arrayOf("application/json", "*/*")) 
                else Toast.makeText(this@MainActivity, "Restore requires Pro Tier!", Toast.LENGTH_SHORT).show()
            }
        }

        panel.addView(backupBtn); panel.addView(restoreBtn)
        return panel
    }

    private fun createSubscriptionPanel(): View {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) }
        val currentTier = prefs.getInt("PREF_USER_TIER", 0)

        fun createTierCard(tierLevel: Int, titleText: String, descText: String, colorHex: String, unlockCode: String): View {
            val isUnlocked = currentTier >= tierLevel
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(48, 48, 48, 48)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 32) }
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor(if (isUnlocked) "#1C261D" else "#1A1A1E"), Color.parseColor(if (isUnlocked) "#121A13" else "#121214"))).apply {
                    cornerRadius = 40f; setStroke(3, Color.parseColor(if (isUnlocked) "#34C759" else colorHex)) 
                }
            }

            val headerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, 16) }
            val title = TextView(this).apply { text = titleText; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
            val statusBadge = TextView(this).apply { 
                text = if (isUnlocked) "ACTIVE" else "LOCKED"
                textSize = 10f; typeface = Typeface.DEFAULT_BOLD; setPadding(16, 8, 16, 8)
                setTextColor(if (isUnlocked) Color.parseColor("#121A13") else Color.WHITE)
                background = GradientDrawable().apply { cornerRadius = 20f; setColor(Color.parseColor(if (isUnlocked) "#34C759" else "#404040")) }
            }
            headerRow.addView(title); headerRow.addView(statusBadge)
            val desc = TextView(this).apply { text = descText; textSize = 12f; setTextColor(Color.parseColor("#A0A0A5")); setPadding(0, 0, 0, 24) }
            val actionBtn = Button(this).apply {
                text = if (isUnlocked) "UNLOCKED" else "Enter Passcode"
                setBackgroundColor(if (isUnlocked) Color.parseColor("#34C759") else Color.parseColor(colorHex)); setTextColor(if (isUnlocked) Color.BLACK else Color.WHITE)
                isEnabled = !isUnlocked
                setOnClickListener {
                    val input = EditText(this@MainActivity).apply { hint = "Enter Secret Code"; setTextColor(Color.BLACK); setPadding(48, 48, 48, 48) }
                    AlertDialog.Builder(this@MainActivity, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert).setTitle("Unlock $titleText").setMessage("Enter passcode from developer:")
                        .setView(input).setPositiveButton("Unlock") { _, _ ->
                            if (input.text.toString().trim().uppercase() == unlockCode) {
                                prefs.edit().putInt("PREF_USER_TIER", tierLevel).apply()
                                Toast.makeText(this@MainActivity, "$titleText Unlocked! Restarting...", Toast.LENGTH_LONG).show()
                                finish(); startActivity(intent) 
                            } else Toast.makeText(this@MainActivity, "Invalid Code!", Toast.LENGTH_SHORT).show()
                        }.setNegativeButton("Cancel", null).show()
                }
            }
            card.addView(headerRow); card.addView(desc); card.addView(actionBtn)
            return card
        }

        container.addView(createTierCard(1, "Beginner", "Unlocks Neon Theme, Native Haptics, and 5 Tiles.", "#2979FF", "BEGIN26"))
        container.addView(createTierCard(2, "Pro", "Unlocks Glass Theme, Custom App Shortcuts, and Cloud Backups.", "#6C2BD9", "PRO26"))
        container.addView(createTierCard(3, "Master", "Unlocks Custom Position Slider, Custom Physics, and VIP Badge.", "#FFC107", "MASTER26"))
        return container
    }
}

// =======================================================================
// VISIBLE BUBBLE PHYSICS ENGINE
// =======================================================================

class BubbleBackgroundView(context: Context) : View(context) {
    private data class Bubble(var x: Float, var y: Float, var r: Float, var dx: Float, var dy: Float)
    private val bubbles = mutableListOf<Bubble>()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#181822"); style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#38384C"); style = Paint.Style.STROKE; strokeWidth = 3.5f }
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#18FFFFFF"); style = Paint.Style.FILL }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh); bubbles.clear()
        if (w == 0 || h == 0) return
        for (i in 0 until 15) {
            val radius = 55f + (Math.random() * 110f).toFloat()
            val x = radius + (Math.random() * (w - 2f * radius)).toFloat()
            val y = radius + (Math.random() * (h - 2f * radius)).toFloat()
            val dx = (if (Math.random() > 0.5) 1f else -1f) * (0.35f + (Math.random() * 1.1f).toFloat())
            val dy = (if (Math.random() > 0.5) 1f else -1f) * (0.35f + (Math.random() * 1.1f).toFloat())
            bubbles.add(Bubble(x, y, radius, dx, dy))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (i in bubbles.indices) {
            val b = bubbles[i]; b.x += b.dx; b.y += b.dy
            if (b.x - b.r < 0) { b.x = b.r; b.dx *= -1f }
            if (b.x + b.r > width) { b.x = width - b.r; b.dx *= -1f }
            if (b.y - b.r < 0) { b.y = b.r; b.dy *= -1f }
            if (b.y + b.r > height) { b.y = height - b.r; b.dy *= -1f }

            for (j in i + 1 until bubbles.size) {
                val b2 = bubbles[j]
                val diffX = b.x - b2.x; val diffY = b.y - b2.y
                val distSq = diffX * diffX + diffY * diffY; val minDist = b.r + b2.r
                if (distSq < minDist * minDist) {
                    val tempDx = b.dx; val tempDy = b.dy; b.dx = b2.dx; b.dy = b2.dy; b2.dx = tempDx; b2.dy = tempDy
                    val dist = Math.sqrt(distSq.toDouble()).toFloat()
                    val overlap = minDist - dist
                    if (dist > 0f) {
                        val nx = diffX / dist; val ny = diffY / dist
                        b.x += nx * (overlap / 2f); b.y += ny * (overlap / 2f)
                        b2.x -= nx * (overlap / 2f); b2.y -= ny * (overlap / 2f)
                    }
                }
            }
            canvas.drawCircle(b.x, b.y, b.r, fillPaint); canvas.drawCircle(b.x, b.y, b.r, strokePaint)
            canvas.drawCircle(b.x - b.r * 0.32f, b.y - b.r * 0.32f, b.r * 0.22f, sheenPaint)
        }
        invalidate()
    }
}
