package com.example.piecontrols

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 100, 60, 60)
        }

        val title = TextView(this).apply {
            text = "Pie Controls Setup"
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 40)
        }
        layout.addView(title)

        val overlayBtn = Button(this).apply {
            text = "1. Grant Overlay Permission"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            }
        }
        layout.addView(overlayBtn)

        val serviceBtn = Button(this).apply {
            text = "2. Enable Accessibility Service"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        layout.addView(serviceBtn)

        val instructions = TextView(this).apply {
            text = "\nOnce both permissions are granted, swipe from the center of the right edge of your screen inward to open the radial navigation menu."
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 15f
        }
        layout.addView(instructions)

        setContentView(layout)
    }
}
