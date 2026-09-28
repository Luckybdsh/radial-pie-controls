package com.example.piecontrols

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class MainActivity : AppCompatActivity() {

    private lateinit var overlayCard: LinearLayout
    private lateinit var accessibilityCard: LinearLayout
    private lateinit var statusDot: View
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#09090B"))
            isFillViewport = true
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 64)
        }

        mainLayout.addView(createTopControlPanel())
        mainLayout.addView(createSectionTitle("QUICK PRESETS", "Tap to load"))
        mainLayout.addView(createPresetsRow())
        mainLayout.addView(createSectionTitle("CUSTOMIZE PIE DIAL", "Themes"))
        mainLayout.addView(createColorRow())

        rootScroll.addView(mainLayout)
        setContentView(rootScroll)
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStates()
    }

    private fun saveConfig(tileCount: Int, theme: String) {
        val prefs = getSharedPreferences("PiePrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("PREF_TILE_COUNT", tileCount)
            .putString("PREF_THEME", theme)
            .apply()
        
        Toast.makeText(this, "Saved: $tileCount Tiles, $theme Theme", Toast.LENGTH_SHORT).show()
    }

    private fun createTopControlPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            background = createRoundRect(Color.parseColor("#121214"), 40f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 40) }
        }

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 24)
        }

        statusDot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(18, 18).apply { setMargins(0, 0, 16, 0) }
            background = createRoundRect(Color.parseColor("#FF453A"), 90f)
        }
        
        statusText = TextView(this).apply {
            text = "SERVICE STOPPED"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FF453A"))
            letterSpacing = 0.1f
        }
        statusRow.addView(statusDot)
        statusRow.addView(statusText)
        panel.addView(statusRow)

        val permissionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 32, 0, 0)
            weightSum = 2f
        }

        overlayCard = createPermissionBox("Screen Overlay").apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, 16, 0) }
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }
        
        accessibilityCard = createPermissionBox("Accessibility").apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(16, 0, 0, 0) }
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
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
            background = createBorderedRect(Color.parseColor("#1A0909"), Color.parseColor("#3D1616"), 24f)
        }
        val titleView = TextView(this).apply {
            text = title
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FF8A80"))
            tag = "title"
        }
        box.addView(titleView)
        return box
    }

    private fun createSectionTitle(title: String, action: String = ""): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 24)
        }
        row.addView(TextView(this).apply {
            text = title
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#8E8E93"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        return row
    }

    private fun createPresetsRow(): View {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 64) }
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        // Clickable Presets that update SharedPreferences
        val p1 = createPresetCard("Smart 6 (Max)", "Adds Recents & Notifs", "#FFD54F")
        p1.setOnClickListener { saveConfig(6, "Neon") }
        
        val p2 = createPresetCard("Classic 5", "Standard fan layout", "#00E5FF")
        p2.setOnClickListener { saveConfig(5, "Pastel") }

        val p3 = createPresetCard("Minimal 3", "Home, Back, Recents", "#B388FF")
        p3.setOnClickListener { saveConfig(3, "Mono") }

        row.addView(p1)
        row.addView(p2)
        row.addView(p3)
        scroll.addView(row)
        return scroll
    }

    private fun createPresetCard(title: String, desc: String, color: String): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            layoutParams = LinearLayout.LayoutParams(400, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 32, 0) }
            background = createRoundRect(Color.parseColor("#121214"), 32f)
        }
        val dot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(24, 24).apply { setMargins(0, 0, 0, 32) }
            background = createRoundRect(Color.parseColor(color), 90f)
        }
        val titleView = TextView(this).apply {
            text = title
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 8)
        }
        val descView = TextView(this).apply {
            text = desc
            textSize = 10f
            setTextColor(Color.parseColor("#8E8E93"))
        }
        card.addView(dot)
        card.addView(titleView)
        card.addView(descView)
        return card
    }

    private fun createColorRow(): View {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val themes = listOf(
            Pair("Neon", "#00E5FF"), 
            Pair("Pastel", "#FFB3BA"), 
            Pair("Mono", "#FFFFFF")
        )
        
        themes.forEach { theme ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(160, 160).apply { setMargins(0, 0, 32, 0) }
                background = createRoundRect(Color.parseColor("#121214"), 24f)
                setOnClickListener {
                    val prefs = getSharedPreferences("PiePrefs", Context.MODE_PRIVATE)
                    val currentTiles = prefs.getInt("PREF_TILE_COUNT", 5)
                    saveConfig(currentTiles, theme.first)
                }
            }
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(40, 40).apply { setMargins(0, 0, 0, 16) }
                background = createRoundRect(Color.parseColor(theme.second), 90f)
            }
            val label = TextView(this).apply {
                text = theme.first
                textSize = 10f
                setTextColor(Color.parseColor("#8E8E93"))
            }
            card.addView(dot)
            card.addView(label)
            row.addView(card)
        }
        scroll.addView(row)
        return scroll
    }

    private fun refreshPermissionStates() {
        if (Settings.canDrawOverlays(this)) {
            statusDot.background = createRoundRect(Color.parseColor("#34C759"), 90f)
            statusText.text = "SERVICE READY"
            statusText.setTextColor(Color.parseColor("#34C759"))
            overlayCard.background = createBorderedRect(Color.parseColor("#091A0F"), Color.parseColor("#163D22"), 24f)
        }
    }

    private fun createRoundRect(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius }
    private fun createBorderedRect(bg: Int, stroke: Int, radius: Float) = GradientDrawable().apply { setColor(bg); setStroke(3, stroke); cornerRadius = radius }
}
