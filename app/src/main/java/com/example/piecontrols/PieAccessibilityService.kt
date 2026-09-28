package com.example.piecontrols

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class PieAccessibilityService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var windowManager: WindowManager
    private lateinit var edgeHandle: View
    private lateinit var prefs: SharedPreferences
    private var pieOverlay: PieMenuView? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("PiePrefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)
        setupEdgeHandle()
    }

    private fun setupEdgeHandle() {
        edgeHandle = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#40FFFFFF"))
                cornerRadii = floatArrayOf(45f, 45f, 0f, 0f, 0f, 0f, 45f, 45f)
            }
        }

        val params = getEdgeParams()

        edgeHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    showPieOverlay(event.rawX, event.rawY)
                    true
                }
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                    pieOverlay?.dispatchTouchEvent(event)
                    true
                }
                else -> false
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            edgeHandle.addOnLayoutChangeListener { v, left, top, right, bottom, _, _, _, _ ->
                v.systemGestureExclusionRects = listOf(Rect(0, 0, right - left, bottom - top))
            }
        }

        windowManager.addView(edgeHandle, params)
    }

    private fun getEdgeParams(): WindowManager.LayoutParams {
        val barHeight = prefs.getInt("PREF_BAR_HEIGHT", 750)
        val barPos = prefs.getInt("PREF_BAR_POS", 0)

        return WindowManager.LayoutParams(
            55,
            barHeight,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            y = barPos
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "PREF_BAR_HEIGHT" || key == "PREF_BAR_POS") {
            if (::edgeHandle.isInitialized) {
                windowManager.updateViewLayout(edgeHandle, getEdgeParams())
            }
        }
    }

    private fun showPieOverlay(x: Float, y: Float) {
        if (pieOverlay != null) return
        pieOverlay = PieMenuView(
            context = this,
            onActionSelected = { actionId -> executeAction(actionId) },
            onDismiss = { hidePieOverlay() }
        ).apply { setOrigin(x, y) }

        val overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(pieOverlay, overlayParams)
    }

    private fun hidePieOverlay() {
        pieOverlay?.let {
            windowManager.removeView(it)
            pieOverlay = null
        }
    }

    private fun executeAction(id: Int) {
        when (id) {
            0 -> performGlobalAction(GLOBAL_ACTION_HOME)
            1 -> performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            2 -> performGlobalAction(GLOBAL_ACTION_BACK)
            3 -> {
                val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            }
            4 -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            5 -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            6 -> {
                try {
                    // Reads the custom package name you typed in the app
                    val customPkg = prefs.getString("PREF_CUSTOM_APP_PKG", "com.google.android.youtube") ?: "com.google.android.youtube"
                    val intent = packageManager.getLaunchIntentForPackage(customPkg)
                    
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    } else {
                        Toast.makeText(this, "App not found! Check package name.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        super.onDestroy()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        if (::edgeHandle.isInitialized) windowManager.removeView(edgeHandle)
        hidePieOverlay()
    }
}
