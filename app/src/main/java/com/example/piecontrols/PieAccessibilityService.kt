package com.example.piecontrols

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.view.accessibility.AccessibilityEvent
import kotlin.math.cos
import kotlin.math.sin

class PieAccessibilityService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: SharedPreferences

    private var triggerView: View? = null
    private var menuContainer: FrameLayout? = null

    // Action Map matching your MainActivity
    private val actionIcons = mapOf(0 to "⌂", 1 to "⎘", 2 to "↩", 3 to "♪", 4 to "⧉", 5 to "🔔", 6 to "★")

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("PiePrefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)
        
        drawUI()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        // INSTANT LIVE UPDATES: Redraws the UI automatically when you change settings!
        drawUI()
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun drawUI() {
        // Remove existing views if they exist to prevent duplicates
        triggerView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
        menuContainer?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }

        if (!prefs.getBoolean("PREF_SERVICE_ENABLED", true)) return

        createTriggerZone()
        createPieMenu()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createTriggerZone() {
        val barHeight = dpToPx(prefs.getInt("PREF_BAR_HEIGHT", 750) / 4) // Scaled for screen
        val barWidth = dpToPx(prefs.getInt("PREF_BAR_WIDTH", 55) / 10)
        val barAlpha = prefs.getInt("PREF_BAR_ALPHA", 100) / 100f
        val barPos = dpToPx(prefs.getInt("PREF_BAR_POS", 0) / 2) // Vertical offset
        val isLightMode = prefs.getBoolean("PREF_IS_LIGHT_MODE", false)

        triggerView = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor(if (isLightMode) "#000000" else "#FFFFFF"))
                cornerRadius = 100f
            }
            alpha = barAlpha * 0.3f // Keep it subtle on the edge

            setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    showPieMenu()
                }
                true
            }
        }

        val params = WindowManager.LayoutParams(
            barWidth,
            barHeight,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END // Right edge
            y = barPos // Custom vertical position
        }

        windowManager.addView(triggerView, params)
    }

    private fun createPieMenu() {
        menuContainer = FrameLayout(this).apply {
            visibility = View.GONE
            
            // Tapping anywhere outside the pie closes the menu
            setOnClickListener { hidePieMenu() }
        }

        val isLightMode = prefs.getBoolean("PREF_IS_LIGHT_MODE", false)
        val themeStyle = prefs.getString("PREF_VISUAL_STYLE", "Simple") ?: "Simple"

        // 1. HARDWARE BLUR & WINDOW SETUP FOR LIQUID GLASS
        val menuParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        if (themeStyle == "LiquidGlass") {
            menuParams.flags = menuParams.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                menuParams.blurBehindRadius = 60 // Heavy iOS Frosted Glass blur!
            }
            
            // Highly translucent background to let the blur shine through
            menuContainer?.setBackgroundColor(Color.parseColor(if (isLightMode) "#33FFFFFF" else "#40000000"))
        } else {
            // Normal dimmed background for Simple/Neon
            menuContainer?.setBackgroundColor(Color.parseColor(if (isLightMode) "#99FFFFFF" else "#B3000000"))
        }

        // 2. BUILD THE PIE WHEEL BACKGROUND
        val pieRadius = dpToPx(180)
        val barPos = dpToPx(prefs.getInt("PREF_BAR_POS", 0) / 2)

        val pieBg = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(pieRadius * 2, pieRadius * 2).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                setMargins(0, barPos, -pieRadius, 0) // Shift half off-screen
            }
            
            if (themeStyle == "LiquidGlass") {
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
                    Color.parseColor(if (isLightMode) "#40FFFFFF" else "#33FFFFFF"),
                    Color.parseColor(if (isLightMode) "#1AFFFFFF" else "#1A000000")
                )).apply {
                    cornerRadius = 1000f
                    setStroke(3, Color.parseColor(if (isLightMode) "#80FFFFFF" else "#4DFFFFFF"))
                }
            } else {
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(if (isLightMode) "#E5E5EA" else "#1C1C22"))
                    cornerRadius = 1000f
                }
            }
        }
        menuContainer?.addView(pieBg)

        // 3. GENERATE THE FLOATING TILES
        val tileActions = prefs.getString("PREF_TILE_ACTIONS", "2,0,6,4,1") ?: "2,0,6,4,1"
        val ids = tileActions.split(",").mapNotNull { it.toIntOrNull() }
        
        val startAngle = -70.0
        val endAngle = 70.0
        val step = if (ids.size > 1) (endAngle - startAngle) / (ids.size - 1) else 0.0

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val centerY = (screenHeight / 2) + barPos

        ids.forEachIndexed { index, actionId ->
            val angleRad = Math.toRadians(startAngle + step * index)
            val dx = -(pieRadius * 0.75f) * cos(angleRad) // Distance from edge
            val dy = (pieRadius * 0.75f) * sin(angleRad)

            val iconStr = actionIcons[actionId] ?: "✦"
            val tile = createPieTile(iconStr, isLightMode)

            val tileSize = dpToPx(56)
            val tileParams = FrameLayout.LayoutParams(tileSize, tileSize)
            
            tile.x = screenWidth.toFloat() + dx.toFloat() - (tileSize / 2f)
            tile.y = centerY.toFloat() + dy.toFloat() - (tileSize / 2f)

            tile.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                executeAction(actionId)
                hidePieMenu()
            }

            menuContainer?.addView(tile, tileParams)
        }

        windowManager.addView(menuContainer, menuParams)
    }

    private fun createPieTile(iconStr: String, isLightMode: Boolean): View {
        val shapeStyle = prefs.getString("PREF_TILE_SHAPE", "Circle") ?: "Circle"
        val themeStyle = prefs.getString("PREF_VISUAL_STYLE", "Simple") ?: "Simple"
        
        val cornerRad = when (shapeStyle) { "Circle" -> 200f; "Square" -> 0f; "Rounded" -> dpToPx(16).toFloat(); "Fur" -> dpToPx(8).toFloat(); else -> 200f }
        
        val cardCol = Color.parseColor(if (isLightMode) "#FFFFFF" else "#2C2C34")
        val strokeCol = Color.parseColor(if (isLightMode) "#D1D1D6" else "#4A4A59")
        val textCol = Color.parseColor(if (isLightMode) "#000000" else "#FFFFFF")

        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            
            if (themeStyle == "LiquidGlass") {
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
                    Color.parseColor(if (isLightMode) "#80FFFFFF" else "#66FFFFFF"), 
                    Color.parseColor(if (isLightMode) "#4DFFFFFF" else "#1AFFFFFF")
                )).apply { 
                    cornerRadius = cornerRad
                    setStroke(4, Color.parseColor(if (isLightMode) "#FFFFFF" else "#80FFFFFF")) 
                }
            } else if (themeStyle == "Neon") {
                background = GradientDrawable().apply { 
                    setColor(cardCol)
                    cornerRadius = cornerRad
                    setStroke(5, Color.parseColor("#2979FF")) // Glowing blue border
                }
            } else {
                background = GradientDrawable().apply { 
                    setColor(cardCol)
                    cornerRadius = cornerRad
                    if (shapeStyle == "Fur") {
                        setStroke(6, strokeCol, 20f, 10f) // Furry dashed border
                    } else {
                        setStroke(2, strokeCol)
                    }
                }
            }
        }
        
        bubble.addView(TextView(this).apply { 
            text = iconStr
            textSize = 24f
            setTextColor(textCol)
            gravity = Gravity.CENTER 
        })
        
        return bubble
    }

    private fun showPieMenu() {
        menuContainer?.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate().alpha(1f).setDuration(150).start()
        }
        triggerView?.visibility = View.GONE
    }

    private fun hidePieMenu() {
        menuContainer?.animate()?.alpha(0f)?.setDuration(150)?.withEndAction {
            menuContainer?.visibility = View.GONE
            triggerView?.visibility = View.VISIBLE
        }?.start()
    }

    private fun executeAction(actionId: Int) {
        when (actionId) {
            0 -> performGlobalAction(GLOBAL_ACTION_HOME)
            1 -> performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            2 -> performGlobalAction(GLOBAL_ACTION_BACK)
            3 -> {
                // Adjust Volume
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            }
            4 -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            5 -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            6 -> {
                // Open Custom App
                val pkg = prefs.getString("PREF_CUSTOM_APP_PKG", "")
                if (!pkg.isNullOrEmpty()) {
                    val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                    } else {
                        Toast.makeText(this, "App not found", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "No App Selected in Settings", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    
    override fun onDestroy() {
        super.onDestroy()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        triggerView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
        menuContainer?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
    }
}
