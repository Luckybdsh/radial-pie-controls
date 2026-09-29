package com.example.piecontrols

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
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

    private val allActions = mapOf(
        0 to "Home", 1 to "Screenshot", 2 to "Back",
        3 to "Volume", 4 to "Recents", 5 to "Notifications", 6 to "Open App"
    )

    data class TileData(val id: Int, val name: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("PiePrefs", Context.MODE_PRIVATE)

        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#09090B"))
            isFillViewport = true
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 64)
        }

        mainLayout.addView(createTopControlPanel())
        
        mainLayout.addView(createSectionTitle("TILE THEME", "Visual Style"))
        mainLayout.addView(createThemeStylePanel())

        mainLayout.addView(createSectionTitle("CUSTOM APP SHORTCUT", "Tap to change"))
        mainLayout.addView(createAppSelectPanel())
        
        mainLayout.addView(createSectionTitle("EDGE BAR SETTINGS"))
        mainLayout.addView(createSlidersPanel())
        
        mainLayout.addView(createSectionTitle("DRAG & DROP TILES", "Long press to move"))
        mainLayout.addView(createDragDropPanel())

        rootScroll.addView(mainLayout)
        setContentView(rootScroll)
        loadTiles()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStates()
    }

    private fun createTopControlPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            background = GradientDrawable().apply { setColor(Color.parseColor("#121214")); cornerRadius = 40f }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 40) }
        }

        val switchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 32)
        }
        val titleTextLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleTextLayout.addView(TextView(this).apply { text = "Pie Controls"; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
        titleTextLayout.addView(TextView(this).apply { text = "Quick toggle edge bar On or Off"; textSize = 11f; setTextColor(Color.parseColor("#8E8E93")) })
        
        val masterSwitch = SwitchCompat(this).apply {
            isChecked = prefs.getBoolean("PREF_SERVICE_ENABLED", true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("PREF_SERVICE_ENABLED", isChecked).apply()
            }
        }
        switchRow.addView(titleTextLayout)
        switchRow.addView(masterSwitch)
        panel.addView(switchRow)

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 24)
        }

        statusDot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(18, 18).apply { setMargins(0, 0, 16, 0) }
            background = GradientDrawable().apply { setColor(Color.parseColor("#FF453A")); cornerRadius = 90f }
        }
        
        statusText = TextView(this).apply {
            text = "SERVICE STATUS"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FF453A"))
        }
        statusRow.addView(statusDot)
        statusRow.addView(statusText)
        panel.addView(statusRow)

        val permissionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 0)
            weightSum = 2f
        }

        overlayCard = createPermissionBox("Screen Overlay").apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, 16, 0) }
            setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        }
        
        accessibilityCard = createPermissionBox("Accessibility").apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(16, 0, 0, 0) }
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }

        permissionsRow.addView(overlayCard)
        permissionsRow.addView(accessibilityCard)
        panel.addView(permissionsRow)

        return panel
    }

    private fun createPermissionBox(title: String): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            background = GradientDrawable().apply { setColor(Color.parseColor("#1A0909")); setStroke(3, Color.parseColor("#3D1616")); cornerRadius = 24f }
        }
        box.addView(TextView(this).apply { text = title; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#FF8A80")) })
        return box
    }

    private fun refreshPermissionStates() {
        if (Settings.canDrawOverlays(this)) {
            statusDot.background = GradientDrawable().apply { setColor(Color.parseColor("#34C759")); cornerRadius = 90f }
            statusText.text = "SERVICE READY (Tap Accessibility to Restart)"
            statusText.setTextColor(Color.parseColor("#34C759"))
            overlayCard.background = GradientDrawable().apply { setColor(Color.parseColor("#091A0F")); setStroke(3, Color.parseColor("#163D22")); cornerRadius = 24f }
        }
    }

    private fun createThemeStylePanel(): View {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, 40) }

        val styles = listOf(
            Triple("Simple", "Flat Colors", "Simple"),
            Triple("Neon", "Glowing Hover", "Neon"),
            Triple("Glass", "Frosted & Blurry", "Glass")
        )
        
        val currentTheme = prefs.getString("PREF_VISUAL_STYLE", "Neon") ?: "Neon"

        styles.forEach { style ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(40, 40, 40, 40)
                layoutParams = LinearLayout.LayoutParams(380, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 32, 0) }
                setOnClickListener {
                    prefs.edit().putString("PREF_VISUAL_STYLE", style.third).apply()
                    updateThemeSelectionUI(style.third) 
                }
            }
            
            val titleView = TextView(this).apply { text = style.first; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); setPadding(0, 0, 0, 8) }
            val descView = TextView(this).apply { text = style.second; textSize = 11f; setTextColor(Color.parseColor("#8E8E93")) }
            
            card.addView(titleView)
            card.addView(descView)
            
            themeCards[style.third] = card
            row.addView(card)
        }
        
        updateThemeSelectionUI(currentTheme) 
        scroll.addView(row)
        return scroll
    }

    private fun updateThemeSelectionUI(selectedTheme: String) {
        themeCards.forEach { (themeName, card) ->
            if (themeName == selectedTheme) {
                card.background = GradientDrawable().apply { 
                    setColor(Color.parseColor("#1C1C22"))
                    setStroke(5, Color.parseColor("#2979FF"))
                    cornerRadius = 32f 
                }
            } else {
                card.background = GradientDrawable().apply { 
                    setColor(Color.parseColor("#121214"))
                    setStroke(0, Color.TRANSPARENT)
                    cornerRadius = 32f 
                }
            }
        }
    }

    private fun createAppSelectPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            background = GradientDrawable().apply { setColor(Color.parseColor("#121214")); cornerRadius = 40f }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 40) }
        }
        
        val currentAppName = prefs.getString("PREF_CUSTOM_APP_NAME", "YouTube")
        selectedAppLabel = TextView(this).apply {
            text = "Selected: $currentAppName"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(0, 0, 0, 32)
        }
        
        val selectBtn = Button(this).apply {
            text = "Choose App"
            setBackgroundColor(Color.parseColor("#2979FF"))
            setTextColor(Color.WHITE)
            setOnClickListener { showAppPicker() }
        }
        
        panel.addView(selectedAppLabel)
        panel.addView(selectBtn)
        return panel
    }

    private fun showAppPicker() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        
        val appList = resolveInfos.map { Pair(it.loadLabel(pm).toString(), it.activityInfo.packageName) }.sortedBy { it.first }
        val names = appList.map { it.first }.toTypedArray()

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Select App")
            .setItems(names) { _, which ->
                val selectedName = appList[which].first
                val selectedPkg = appList[which].second
                prefs.edit().putString("PREF_CUSTOM_APP_NAME", selectedName).putString("PREF_CUSTOM_APP_PKG", selectedPkg).apply()
                selectedAppLabel.text = "Selected: $selectedName"
                Toast.makeText(this, "Saved! Toggle Service to reload.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun createSectionTitle(title: String, subtitle: String = ""): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 32, 0, 24)
        }
        row.addView(TextView(this).apply { text = title; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#8E8E93")); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        if (subtitle.isNotEmpty()) {
            row.addView(TextView(this).apply { text = subtitle; textSize = 10f; setTextColor(Color.parseColor("#2979FF")) })
        }
        return row
    }

    // --- UPGRADED SLIDERS PANEL ---
    private fun createSlidersPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            background = GradientDrawable().apply { setColor(Color.parseColor("#121214")); cornerRadius = 40f }
        }

        // Helper function for creating the Label + Percentage layout
        fun createLabelRow(title: String, percentView: TextView?): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 16, 0, 16)
                addView(TextView(this@MainActivity).apply { 
                    text = title; setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) 
                })
                if (percentView != null) addView(percentView)
            }
        }

        // 1. HEIGHT SLIDER
        val heightPercent = TextView(this).apply { setTextColor(Color.parseColor("#2979FF")); typeface = Typeface.DEFAULT_BOLD }
        val heightSlider = SeekBar(this).apply {
            max = 1200
            progress = prefs.getInt("PREF_BAR_HEIGHT", 750) - 200
            heightPercent.text = "${(progress * 100 / max)}%"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, prog: Int, fromUser: Boolean) { 
                    prefs.edit().putInt("PREF_BAR_HEIGHT", prog + 200).apply() 
                    heightPercent.text = "${(prog * 100 / max)}%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        panel.addView(createLabelRow("Bar Height", heightPercent))
        panel.addView(heightSlider)

        // 2. WIDTH SLIDER
        val widthPercent = TextView(this).apply { setTextColor(Color.parseColor("#2979FF")); typeface = Typeface.DEFAULT_BOLD; setPadding(0,24,0,0) }
        val widthSlider = SeekBar(this).apply {
            max = 100 // 0 to 100 equates to a width between 20px and 120px
            progress = prefs.getInt("PREF_BAR_WIDTH", 55) - 20
            widthPercent.text = "$progress%"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, prog: Int, fromUser: Boolean) { 
                    prefs.edit().putInt("PREF_BAR_WIDTH", prog + 20).apply() 
                    widthPercent.text = "$prog%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        panel.addView(createLabelRow("Bar Width", widthPercent))
        panel.addView(widthSlider)

        // 3. OPACITY (TRANSPARENCY) SLIDER
        val opacityPercent = TextView(this).apply { setTextColor(Color.parseColor("#2979FF")); typeface = Typeface.DEFAULT_BOLD; setPadding(0,24,0,0) }
        val opacitySlider = SeekBar(this).apply {
            max = 100
            progress = prefs.getInt("PREF_BAR_ALPHA", 100)
            opacityPercent.text = "$progress%"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, prog: Int, fromUser: Boolean) { 
                    prefs.edit().putInt("PREF_BAR_ALPHA", prog).apply() 
                    opacityPercent.text = "$prog%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        panel.addView(createLabelRow("Bar Opacity (Transparency)", opacityPercent))
        panel.addView(opacitySlider)

        // 4. POSITION SLIDER (No percentage)
        val posSlider = SeekBar(this).apply {
            max = 1000 
            progress = prefs.getInt("PREF_BAR_POS", 0) + 500
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, prog: Int, fromUser: Boolean) { prefs.edit().putInt("PREF_BAR_POS", prog - 500).apply() }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        panel.addView(createLabelRow("Vertical Position", null).apply { setPadding(0, 24, 0, 16) })
        panel.addView(posSlider)

        return panel
    }

    private fun createDragDropPanel(): View {
        dragContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            background = GradientDrawable().apply { setColor(Color.parseColor("#121214")); cornerRadius = 40f }
        }
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
            val tileView = createTileRow(tile.name)
            tileView.tag = index 

            tileView.setOnLongClickListener { v ->
                val dragData = ClipData(v.tag.toString(), arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN), ClipData.Item(index.toString()))
                v.startDragAndDrop(dragData, View.DragShadowBuilder(v), null, 0)
                true
            }

            tileView.setOnDragListener { v, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> true
                    DragEvent.ACTION_DRAG_ENTERED -> { v.setBackgroundColor(Color.parseColor("#292930")); true }
                    DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> { v.background = null; true }
                    DragEvent.ACTION_DROP -> {
                        v.background = null
                        Collections.swap(currentTiles, event.clipData.getItemAt(0).text.toString().toInt(), v.tag as Int)
                        saveTileOrder()
                        renderTiles() 
                        true
                    }
                    else -> false
                }
            }
            dragContainer.addView(tileView)
        }
    }

    private fun createTileRow(name: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 32, 32, 32)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 8, 0, 8) }
        }
        row.addView(TextView(this).apply { text = "≡"; textSize = 20f; setTextColor(Color.parseColor("#666666")); setPadding(0, 0, 32, 0) })
        row.addView(TextView(this).apply { text = name; textSize = 16f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD })
        return row
    }

    private fun saveTileOrder() {
        prefs.edit().putString("PREF_TILE_ACTIONS", currentTiles.joinToString(",") { it.id.toString() }).apply()
    }
}
