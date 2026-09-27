package com.example.piecontrols

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Dark background matching screenshot
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121114"))
            setPadding(40, 60, 40, 40)
        }

        // Setup Permission Card
        val permissionCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E1D22"))
                cornerRadius = 32f
            }
        }

        val permTitle = TextView(this).apply {
            text = "Permissions Required"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 20)
        }
        permissionCard.addView(permTitle)

        val overlayBtn = Button(this).apply {
            text = "1. Enable 'Draw Over Apps'"
            setBackgroundColor(Color.parseColor("#2979FF"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
            }
        }
        permissionCard.addView(overlayBtn)

        val serviceBtn = Button(this).apply {
            text = "2. Enable Accessibility Service"
            setBackgroundColor(Color.parseColor("#22C55E"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        permissionCard.addView(serviceBtn)
        rootLayout.addView(permissionCard)

        // Spacer
        rootLayout.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(1, 40) })

        // "Customize" Bottom Sheet Panel matching photo inset
        val customizePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 48)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#232228"))
                cornerRadius = 44f
            }
        }

        // Sheet grab handle
        val handle = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(90, 10).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, 0, 0, 36)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#44434B"))
                cornerRadius = 10f
            }
        }
        customizePanel.addView(handle)

        val header = TextView(this).apply {
            text = "←   Customize"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 36)
        }
        customizePanel.addView(header)

        // Options from sample image
        customizePanel.addView(createSettingRow("🎨", "Color Theme", "Selected: Vibrant"))
        customizePanel.addView(createSettingRow("🍕", "Menu Type", "Semi-Circular"))
        customizePanel.addView(createSettingRow("📱", "Bar Position", "Right"))
        customizePanel.addView(createSettingRow("✥", "Tile Size", "Medium"))
        customizePanel.addView(createSettingRow("📈", "Animation", "Edge Fan Out"))

        rootLayout.addView(customizePanel)
        setContentView(rootLayout)
    }

    private fun createSettingRow(icon: String, title: String, subtitle: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 22, 0, 22)
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconView = TextView(this).apply {
            text = icon
            textSize = 20f
            setPadding(0, 0, 32, 0)
        }
        row.addView(iconView)

        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(Color.parseColor("#EDEDED"))
        }
        textLayout.addView(titleView)

        val subtitleView = TextView(this).apply {
            text = subtitle
            textSize = 13f
            setTextColor(Color.parseColor("#9B9AA0"))
        }
        textLayout.addView(subtitleView)

        row.addView(textLayout)
        return row
    }
}
