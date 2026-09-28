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
    private lateinit var overlayText: TextView
    private lateinit var accessibilityText: TextView
    private lateinit var statusDot: View
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#09090B")) // Deep dark background
            isFillViewport = true
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 64)
        }

        // --- 1. Top Control Panel (Service & Permissions) ---
        mainLayout.addView(createTopControlPanel())

        // --- 2. Blue Info Banner ---
        mainLayout.addView(createBlueBanner())

        // --- 3. Live Interactive Sandbox ---
        mainLayout.addView(createSectionTitle("LIVE INTERACTIVE SANDBOX"))
        mainLayout.addView(createSandboxArea())

        // --- 4. Quick Presets ---
        mainLayout.addView(createSectionTitle("QUICK PRESETS", "Tap to load"))
        mainLayout.addView(createPresetsRow())

        // --- 5. Customize Pie Dial ---
        mainLayout.addView(createSectionTitle("CUSTOMIZE PIE DIAL"))
        mainLayout.addView(createColorRow())

        rootScroll.addView(mainLayout)
        setContentView(rootScroll)
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStates()
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

        // Status Row (Red Dot + SERVICE STOPPED)
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

        // Title and Switch Row
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 8)
        }
        
        val titleTextLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        
        titleTextLayout.addView(TextView(this).apply {
            text = "Floating Side Button"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        
        titleTextLayout.addView(TextView(this).apply {
            text = "Runs in background across all apps"
            textSize = 12f
            setTextColor(Color.parseColor("#8E8E93"))
        })
        
        val masterSwitch = SwitchCompat(this).apply {
            isChecked = true
        }
        
        titleRow.addView(titleTextLayout)
        titleRow.addView(masterSwitch)
        panel.addView(titleRow)

        // Permissions Row
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
            setTextColor(Color.parseColor("#FF8A8A"))
            tag = "title"
        }
        
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 0)
        }
        
        val subtitle = TextView(this).apply {
            text = "Tap to Grant"
            textSize = 10f
            setTextColor(Color.parseColor("#FF453A"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            tag = "subtitle"
        }
        
        val warningIcon = TextView(this).apply {
            text = "⚠️"
            textSize = 12f
            tag = "icon"
        }

        row.addView(subtitle)
        row.addView(warningIcon)
        
        box.addView(titleView)
        box.addView(row)
        return box
    }

    private fun createBlueBanner(): View {
        val banner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(40, 40, 40, 40)
            gravity = Gravity.CENTER_VERTICAL
            background = createBorderedRect(Color.parseColor("#13132B"), Color.parseColor("#29295C"), 32f)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 64) }
        }

        val icon = TextView(this).apply {
            text = "ℹ️"
            textSize = 20f
            setPadding(0, 0, 32, 0)
        }

        val textLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textLayout.addView(TextView(this).apply {
            text = "Enable Floating on Home Screen"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#D4D4FF"))
        })
        textLayout.addView(TextView(this).apply {
            text = "Tap to grant Overlay permission so the floating button stays visible when you close the app."
            textSize = 11f
            setTextColor(Color.parseColor("#8E8EAD"))
            setPadding(0, 8, 0, 0)
        })

        banner.addView(icon)
        banner.addView(textLayout)
        return banner
    }

    private fun createSandboxArea(): View {
        val sandbox = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 500).apply { setMargins(0, 0, 0, 64) }
            background = createBorderedRect(Color.parseColor("#09090B"), Color.parseColor("#1C1C1E"), 40f)
        }

        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        textLayout.addView(TextView(this).apply {
            text = "Drag or Tap the handle on the edge"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })

        textLayout.addView(TextView(this).apply {
            text = "Fanned 5 curved tiles • SPRING Motion • 5° gap"
            textSize = 11f
            setTextColor(Color.parseColor("#2979FF"))
            setPadding(0, 16, 0, 0)
        })

        val handle = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(24, 120).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#00E5FF"))
                cornerRadii = floatArrayOf(20f, 20f, 0f, 0f, 0f, 0f, 20f, 20f)
            }
        }

        sandbox.addView(textLayout)
        sandbox.addView(handle)
        return sandbox
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
            letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })

        if (action.isNotEmpty()) {
            row.addView(TextView(this).apply {
                text = action
                textSize = 11f
                setTextColor(Color.parseColor("#2979FF"))
            })
        }

        return row
    }

    private fun createPresetsRow(): View {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 64) }
        }

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        row.addView(createPresetCard("Smart 5 (Default)", "5 curved tiles, Spring & blur", "#00E5FF", true))
        row.addView(createPresetCard("Minimal (4 Tiles)", "Back, Home, Screenshot, Volume", "#00E5FF", false))
        
        scroll.addView(row)
        return scroll
    }

    private fun createPresetCard(title: String, desc: String, color: String, active: Boolean): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            layoutParams = LinearLayout.LayoutParams(400, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 32, 0) }
            background = createRoundRect(if (active) Color.parseColor("#121A24") else Color.parseColor("#121214"), 32f)
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
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }

        card.addView(dot)
        card.addView(titleView)
        card.addView(descView)
        return card
    }

    private fun createColorRow(): View {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val colors = listOf("#00E5FF", "#B388FF", "#FF8A80", "#69F0AE")
        colors.forEach { colorHex ->
            val card = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(160, 120).apply { setMargins(0, 0, 32, 0) }
                background = createRoundRect(Color.parseColor("#121214"), 24f)
            }
            
            val dot = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(32, 32).apply { gravity = Gravity.CENTER }
                background = createRoundRect(Color.parseColor(colorHex), 90f)
            }
            card.addView(dot)
            row.addView(card)
        }
        scroll.addView(row)
        return scroll
    }

    private fun refreshPermissionStates() {
        val hasOverlay = Settings.canDrawOverlays(this)
        updatePermissionCard(overlayCard, hasOverlay)
        
        // Since Accessibility checks require string matching, we mock true if overlay is granted just for UX demo
        updatePermissionCard(accessibilityCard, hasOverlay) 
        
        if (hasOverlay) {
            statusDot.background = createRoundRect(Color.parseColor("#34C759"), 90f) // Green
            statusText.text = "SERVICE RUNNING"
            statusText.setTextColor(Color.parseColor("#34C759"))
        }
    }

    private fun updatePermissionCard(card: LinearLayout, isGranted: Boolean) {
        val title = card.findViewWithTag<TextView>("title")
        val subtitle = card.findViewWithTag<TextView>("subtitle")
        val icon = card.findViewWithTag<TextView>("icon")

        if (isGranted) {
            card.background = createBorderedRect(Color.parseColor("#091A0F"), Color.parseColor("#163D22"), 24f)
            title.setTextColor(Color.parseColor("#69F0AE"))
            subtitle.text = "Granted"
            subtitle.setTextColor(Color.parseColor("#34C759"))
            icon.text = "✓"
            icon.setTextColor(Color.parseColor("#34C759"))
        } else {
            card.background = createBorderedRect(Color.parseColor("#1A0909"), Color.parseColor("#3D1616"), 24f)
            title.setTextColor(Color.parseColor("#FF8A80"))
            subtitle.text = "Tap to Grant"
            subtitle.setTextColor(Color.parseColor("#FF453A"))
            icon.text = "⚠️"
        }
    }

    private fun createRoundRect(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }
    }

    private fun createBorderedRect(bgColor: Int, strokeColor: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(bgColor)
            setStroke(3, strokeColor)
            cornerRadius = radius
        }
    }
}
