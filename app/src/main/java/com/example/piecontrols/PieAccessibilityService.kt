package com.example.piecontrols

import android.accessibilityservice.AccessibilityService
import android.content.Context
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

class PieAccessibilityService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var edgeHandle: View
    private var pieOverlay: PieMenuView? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupEdgeHandle()
    }

    private fun setupEdgeHandle() {
        // 1. Create a styled, curved sidebar
        edgeHandle = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#40FFFFFF")) // Frosted translucent white
                // Curves the Top-Left and Bottom-Left corners, leaves right side flat against edge
                cornerRadii = floatArrayOf(
                    45f, 45f, // Top-Left
                    0f, 0f,   // Top-Right
                    0f, 0f,   // Bottom-Right
                    45f, 45f  // Bottom-Left
                )
            }
        }

        val params = WindowManager.LayoutParams(
            55, // Slightly wider to show off the curve
            750, // Height of the trigger bar
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }

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

        // 2. Disable system "Back" gesture over the trigger area
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            edgeHandle.addOnLayoutChangeListener { v, left, top, right, bottom, _, _, _, _ ->
                // Tells Android to exclude this specific rectangle from system gestures
                v.systemGestureExclusionRects = listOf(Rect(0, 0, right - left, bottom - top))
            }
        }

        windowManager.addView(edgeHandle, params)
    }

    private fun showPieOverlay(x: Float, y: Float) {
        if (pieOverlay != null) return

        pieOverlay = PieMenuView(
            context = this,
            onActionSelected = { actionId -> executeAction(actionId) },
            onDismiss = { hidePieOverlay() }
        ).apply {
            setOrigin(x, y)
        }

        val overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
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
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (::edgeHandle.isInitialized) {
            windowManager.removeView(edgeHandle)
        }
        hidePieOverlay()
    }
}
