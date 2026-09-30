package com.example.piecontrols

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
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
    private lateinit var dragContainer: FrameLayout 
    private var currentTiles = mutableListOf<TileData>()
    
    private lateinit var overlayCard: LinearLayout
    private lateinit var accessibilityCard: LinearLayout
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var selectedAppLabel: TextView
    private val themeCards = mutableMapOf<String, LinearLayout>()

    private lateinit var mainAppContainer: FrameLayout
    private lateinit var loginContainer: LinearLayout
    private lateinit var homeScroll: ScrollView
    private lateinit var settingsScroll: ScrollView
    private lateinit var pillBar: LinearLayout
    private lateinit var homeNavTab: LinearLayout
    private lateinit var settingsNavTab: LinearLayout
    private lateinit var homeNavIcon: TextView
    private lateinit var homeNavText: TextView
    private lateinit var settingsNavIcon: TextView
    private lateinit var settingsNavText: TextView
    private lateinit var welcomeLabel: TextView

    private val allActions = mapOf(
        0 to "Home", 1 to "Screenshot", 2 to "Back",
        3 to "Volume", 4 to "Recents", 5 to "Notifications", 6 to "Open App"
    )

    data class TileData(val id: Int, val name: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("PiePrefs", Context.MODE_PRIVATE)

        val rootFrame = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#09090B")) }
        val bubbleBg = BubbleBackgroundView(this)
        rootFrame.addView(bubbleBg, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        mainAppContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            visibility = View.GONE 
        }

        homeScroll = ScrollView(this).apply { isFillViewport = true; setPadding(48, 64, 48, 240); clipToPadding = false }
        val homeLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        homeLayout.addView(createTopControlPanel())
        homeLayout.addView(createSectionTitle("TILE THEME", "Visual Style"))
        homeLayout.addView(createThemeStylePanel())
        homeLayout.addView(createSectionTitle("CUSTOM APP SHORTCUT", "Pro Tier Required"))
        homeLayout.addView(createAppSelectPanel())
        homeLayout.addView(createSectionTitle("EDGE BAR SETTINGS"))
        homeLayout.addView(createSlidersPanel())
        homeLayout.addView(createSectionTitle("TILE LAYOUT", "Drag bubbles to swap"))
        homeLayout.addView(createDragDropPanel())
        homeScroll.addView(homeLayout)
        mainAppContainer.addView(homeScroll)

        settingsScroll = ScrollView(this).apply { isFillViewport = true; visibility = View.GONE; setPadding(48, 64, 48, 240); clipToPadding = false }
        val settingsLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        settingsLayout.addView(createSectionTitle("DATA MANAGEMENT", "Pro Tier Required"))
        settingsLayout.addView(createBackupRestorePanel())
        settingsLayout.addView(createSectionTitle("UNLOCK PREMIUM", "Enter passcodes to upgrade"))
        settingsLayout.addView(createSubscriptionPanel())
        settingsLayout.addView(createSectionTitle("ACCOUNT SESSION", "Manage your login"))
        settingsLayout.addView(createLogoutPanel())
        settingsScroll.addView(settingsLayout)
        mainAppContainer.addView(settingsScroll)

        mainAppContainer.addView(createBottomNavBar())
        rootFrame.addView(mainAppContainer)

        loginContainer = createLoginScreen()
        rootFrame.addView(loginContainer)

        setContentView(rootFrame)
        loadTiles()

        if (prefs.getBoolean("PREF_IS_LOGGED_IN", false)) { showMainApp(false) }
    }

    override fun onResume() { super.onResume(); refreshPermissionStates() }
    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun createDragDropPanel(): View {
        dragContainer = FrameLayout(this).apply { 
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(320)).apply { setMargins(0, 0, 0, 40) }
            background = GradientDrawable().apply { setColor(Color.parseColor("#121214")); cornerRadius = 40f }
            clipChildren = true 
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

    private fun getIconForAction(id: Int): String { return when (id) { 0 -> "⌂"; 1 -> "⎘"; 2 -> "↩"; 3 -> "♪"; 4 -> "⧉"; 5 -> "🔔"; 6 -> "★"; else -> "✦" } }

    private fun renderTiles() {
        dragContainer.post {
            dragContainer.removeAllViews()
            dragContainer.addView(TextView(this@MainActivity).apply { text = "Preview:\nDrag & Drop to Reorder"; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#666666")); setPadding(dpToPx(24), dpToPx(24), 0, 0) })

            val width = dragContainer.width; val height = dragContainer.height
            if (width == 0 || height == 0) return@post
            
            val centerX = width.toFloat() + dpToPx(10) 
            val centerY = height / 2f
            val radius = height * 0.38f 

            val pieBg = PiePreviewBackground(this@MainActivity, radius)
            dragContainer.addView(pieBg, FrameLayout.LayoutParams(width, height))

            val startAngle = -70.0
            val endAngle = 70.0
            val step = if (currentTiles.size > 1) (endAngle - startAngle) / (currentTiles.size - 1) else 0.0

            currentTiles.forEachIndexed { index, tile ->
                val angleRad = Math.toRadians(startAngle + step * index)
                val dx = -radius * Math.cos(angleRad)
                val dy = radius * Math.sin(angleRad)

                val tileView = createPieTileBubble(tile.name, getIconForAction(tile.id))
                tileView.tag = index

                tileView.setOnLongClickListener { v ->
                    val dragData = ClipData(v.tag.toString(), arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN), ClipData.Item(index.toString()))
                    v.startDragAndDrop(dragData, View.DragShadowBuilder(v), null, 0); v.alpha = 0.5f; true
                }
                tileView.setOnDragListener { v, event ->
                    when (event.action) {
                        DragEvent.ACTION_DRAG_STARTED -> true
                        DragEvent.ACTION_DRAG_ENTERED -> { v.scaleX = 1.2f; v.scaleY = 1.2f; (v.background as GradientDrawable).setStroke(4, Color.parseColor("#34C759")); true }
                        DragEvent.ACTION_DRAG_EXITED -> { v.scaleX = 1f; v.scaleY = 1f; (v.background as GradientDrawable).setStroke(2, Color.parseColor("#38384C")); true }
                        DragEvent.ACTION_DROP -> {
                            v.scaleX = 1f; v.scaleY = 1f
                            val fromIndex = event.clipData.getItemAt(0).text.toString().toInt()
                            Collections.swap(currentTiles, fromIndex, v.tag as Int)
                            prefs.edit().putString("PREF_TILE_ACTIONS", currentTiles.joinToString(",") { it.id.toString() }).apply()
                            renderTiles(); true
                        }
                        DragEvent.ACTION_DRAG_ENDED -> { v.alpha = 1f; v.scaleX = 1f; v.scaleY = 1f; (v.background as GradientDrawable).setStroke(2, Color.parseColor("#38384C")); true }
                        else -> false
                    }
                }
                
                val size = dpToPx(64)
                dragContainer.addView(tileView, FrameLayout.LayoutParams(size, size))
                tileView.x = centerX + dx.toFloat() - (size / 2f)
                tileView.y = centerY + dy.toFloat() - (size / 2f)
            }
        }
    }

    private fun createPieTileBubble(name: String, iconStr: String): View {
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(Color.parseColor("#1C1C22")); cornerRadius = 200f; setStroke(2, Color.parseColor("#38384C")) }
        }
        bubble.addView(TextView(this).apply { text = iconStr; textSize = 22f; setTextColor(Color.WHITE); gravity = Gravity.CENTER })
        bubble.addView(TextView(this).apply { val shortName = if (name.length > 5) name.substring(0, 4) + "." else name; text = shortName; textSize = 9f; setTextColor(Color.parseColor("#A0A0A5")); gravity = Gravity.CENTER })
        return bubble
    }

    private val backupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { performBackup(it) } }
    private val restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { performRestore(it) } }
    private fun performBackup(uri: Uri) {
        try { val jsonObject = JSONObject(); for ((key, value) in prefs.all) jsonObject.put(key, value); contentResolver.openOutputStream(uri)?.use { it.write(jsonObject.toString().toByteArray()) }; Toast.makeText(this, "Backup saved!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) { Toast.makeText(this, "Backup failed", Toast.LENGTH_LONG).show() }
    }
    private fun performRestore(uri: Uri) {
        try { val sb = java.lang.StringBuilder(); contentResolver.openInputStream(uri)?.use { BufferedReader(InputStreamReader(it)).forEachLine { line -> sb.append(line) } }; val jsonObject = JSONObject(sb.toString()); val editor = prefs.edit(); for (key in jsonObject.keys()) { when (val value = jsonObject.get(key)) { is Boolean -> editor.putBoolean(key, value); is Int -> editor.putInt(key, value); is String -> editor.putString(key, value); is Float -> editor.putFloat(key, value) } }; editor.apply(); Toast.makeText(this, "Backup restored!", Toast.LENGTH_SHORT).show(); finish(); startActivity(intent)
        } catch (e: Exception) { Toast.makeText(this, "Restore failed", Toast.LENGTH_LONG).show() }
    }

    private fun createLoginScreen(): LinearLayout {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(64, 64, 64, 64); layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT); setBackgroundColor(Color.parseColor("#B309090B")) }
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(64, 80, 64, 80); gravity = Gravity.CENTER_HORIZONTAL; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor("#1C1C22"), Color.parseColor("#121214"))).apply { cornerRadius = 64f; setStroke(2, Color.parseColor("#2E2E3C")) } }
        card.addView(TextView(this).apply { text = "Pie Controls"; textSize = 28f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); setPadding(0, 0, 0, 16) })
        card.addView(TextView(this).apply { text = "Welcome back! Please login."; textSize = 14f; setTextColor(Color.parseColor("#8E8E93")); setPadding(0, 0, 0, 64) })
        val usernameInput = EditText(this).apply { hint = "Username"; setHintTextColor(Color.parseColor("#666666")); setTextColor(Color.WHITE); setPadding(48, 48, 48, 48); background = GradientDrawable().apply { setColor(Color.parseColor("#09090B")); cornerRadius = 32f }; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 32) } }
        val passwordInput = EditText(this).apply { hint = "Password"; setHintTextColor(Color.parseColor("#666666")); setTextColor(Color.WHITE); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; setPadding(48, 48, 48, 48); background = GradientDrawable().apply { setColor(Color.parseColor("#09090B")); cornerRadius = 32f }; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 64) } }
        val loginBtn = Button(this).apply { text = "Login"; setBackgroundColor(Color.parseColor("#2979FF")); setTextColor(Color.WHITE); isAllCaps = false; textSize = 16f; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 140).apply { setMargins(0, 0, 0, 24) }; background = GradientDrawable().apply { setColor(Color.parseColor("#2979FF")); cornerRadius = 32f }; setOnClickListener { val user = usernameInput.text.toString().trim(); if (user.isNotEmpty() && passwordInput.text.toString().isNotEmpty()) performLogin(user) else Toast.makeText(this@MainActivity, "Please enter details", Toast.LENGTH_SHORT).show() } }
        val guestBtn = Button(this).apply { text = "Continue as Guest"; setBackgroundColor(Color.TRANSPARENT); setTextColor(Color.parseColor("#A0A0A5")); isAllCaps = false; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); setOnClickListener { performLogin("Guest") } }
        card.addView(usernameInput); card.addView(passwordInput); card.addView(loginBtn); card.addView(guestBtn); container.addView(card); return container
    }

    private fun performLogin(username: String) {
        prefs.edit().putBoolean("PREF_IS_LOGGED_IN", true).putString("PREF_USERNAME", username).apply()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager; imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        showMainApp(true)
    }

    private fun showMainApp(animate: Boolean) {
        val username = prefs.getString("PREF_USERNAME", "Guest")
        if (::welcomeLabel.isInitialized) { welcomeLabel.text = "Welcome, $username" }
        if (animate) {
            loginContainer.startAnimation(AlphaAnimation(1f, 0f).apply { duration = 400 }); loginContainer.visibility = View.GONE
            mainAppContainer.visibility = View.VISIBLE; mainAppContainer.startAnimation(AlphaAnimation(0f, 1f).apply { duration = 400 })
        } else { loginContainer.visibility = View.GONE; mainAppContainer.visibility = View.VISIBLE }
    }

    private fun createLogoutPanel(): View {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40); background = GradientDrawable().apply { setColor(Color.parseColor("#121214")); cornerRadius = 40f }; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 40) } }
        val logoutBtn = Button(this).apply { text = "Log Out"; setBackgroundColor(Color.parseColor("#FF453A")); setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); background = GradientDrawable().apply { setColor(Color.parseColor("#3D1616")); setStroke(2, Color.parseColor("#FF453A")); cornerRadius = 32f }; setOnClickListener { prefs.edit().putBoolean("PREF_IS_LOGGED_IN", false).apply(); finish(); startActivity(intent) } }
        panel.addView(logoutBtn); return panel
    }

    private fun createBottomNavBar(): View {
        val navContainer = FrameLayout(this).apply { layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.BOTTOM; setMargins(48, 0, 48, 48) } }
        pillBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(20, 16, 20, 16); background = GradientDrawable().apply { setColor(Color.parseColor("#181820")); setStroke(2, Color.parseColor("#2E2E3C")); cornerRadius = 64f }; layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) }
        homeNavTab = createNavTab("⌂", "Home", true) { switchTab(isHome = true) }
        homeNavIcon = homeNavTab.getChildAt(0) as TextView; homeNavText = homeNavTab.getChildAt(1) as TextView
        settingsNavTab = createNavTab("⚙", "Settings", false) { switchTab(isHome = false) }
        settingsNavIcon = settingsNavTab.getChildAt(0) as TextView; settingsNavText = settingsNavTab.getChildAt(1) as TextView
        pillBar.addView(homeNavTab); pillBar.addView(settingsNavTab); navContainer.addView(pillBar); return navContainer
    }

    private fun createNavTab(icon: String, title: String, isActive: Boolean, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(0, 16, 0, 16); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); if (isActive) background = GradientDrawable().apply { setColor(Color.parseColor("#202738")); cornerRadius = 48f }; addView(TextView(this@MainActivity).apply { text = icon; textSize = 18f; gravity = Gravity.CENTER; setTextColor(if (isActive) Color.parseColor("#2979FF") else Color.parseColor("#8E8E93")) }); addView(TextView(this@MainActivity).apply { text = title; textSize = 11f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setPadding(0, 4, 0, 0); setTextColor(if (isActive) Color.parseColor("#2979FF") else Color.parseColor("#8E8E93")) }); setOnClickListener { onClick() } }
    }

    private fun switchTab(isHome: Boolean) {
        val viewToShow = if (isHome) homeScroll else settingsScroll; val viewToHide = if (isHome) settingsScroll else homeScroll
        if (viewToShow.visibility == View.VISIBLE) return
        TransitionManager.beginDelayedTransition(pillBar, AutoTransition().apply { duration = 250 })
        homeNavTab.background = if (isHome) GradientDrawable().apply { setColor(Color.parseColor("#202738")); cornerRadius = 48f } else null
        settingsNavTab.background = if (!isHome) GradientDrawable().apply { setColor(Color.parseColor("#202738")); cornerRadius = 48f } else null
        homeNavIcon.setTextColor(Color.parseColor(if (isHome) "#2979FF" else "#8E8E93")); homeNavText.setTextColor(Color.parseColor(if (isHome) "#2979FF" else "#8E8E93"))
        settingsNavIcon.setTextColor(Color.parseColor(if (!isHome) "#2979FF" else "#8E8E93")); settingsNavText.setTextColor(Color.parseColor(if (!isHome) "#2979FF" else "#8E8E93"))
        viewToShow.alpha = 0f; viewToShow.translationY = 40f; viewToShow.visibility = View.VISIBLE
        viewToShow.animate().alpha(1f).translationY(0f).setDuration(300).start()
        viewToHide.animate().alpha(0f).translationY(-40f).setDuration(300).withEndAction { viewToHide.visibility = View.GONE; viewToHide.translationY = 0f }.start()
    }

    private fun createTopControlPanel(): View {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40); background = GradientDrawable().apply { setColor(Color.parseColor("#121214")); cornerRadius = 40f }; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 40) } }
        val username = prefs.getString("PREF_USERNAME", "Guest"); welcomeLabel = TextView(this).apply { text = "Welcome, $username"; textSize = 12f; setTextColor(Color.parseColor("#2979FF")); setPadding(0, 0, 0, 24); typeface = Typeface.DEFAULT_BOLD }; panel.addView(welcomeLabel)
        val switchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, 32) }; val titleTextLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }; titleTextLayout.addView(TextView(this).apply { text = "Pie Controls"; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) }); titleTextLayout.addView(TextView(this).apply { text = "Quick toggle edge bar On or Off"; textSize = 11f; setTextColor(Color.parseColor("#8E8E93")) }); val masterSwitch = SwitchCompat(this).apply { isChecked = prefs.getBoolean("PREF_SERVICE_ENABLED", true); setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("PREF_SERVICE_ENABLED", isChecked).apply() } }; switchRow.addView(titleTextLayout); switchRow.addView(masterSwitch); panel.addView(switchRow)
        val statusRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, 24) }; statusDot = View(this).apply { layoutParams = LinearLayout.LayoutParams(18, 18).apply { setMargins(0, 0, 16, 0) }; background = GradientDrawable().apply { setColor(Color.parseColor("#FF453A")); cornerRadius = 90f } }; statusText = TextView(this).apply { text = "SERVICE STATUS"; textSize = 10f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#FF453A")) }; statusRow.addView(statusDot); statusRow.addView(statusText); panel.addView(statusRow)
        val permissionsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 16, 0, 0); weightSum = 2f }; overlayCard = createPermissionBox("Screen Overlay").apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, 16, 0) }; setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) } }; accessibilityCard = createPermissionBox("Accessibility").apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(16, 0, 0, 0) }; setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }; permissionsRow.addView(overlayCard); permissionsRow.addView(accessibilityCard); panel.addView(permissionsRow)
        return panel
    }

    private fun createPermissionBox(title: String): LinearLayout { val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32); background = GradientDrawable().apply { setColor(Color.parseColor("#1A0909")); setStroke(3, Color.parseColor("#3D1616")); cornerRadius = 24f } }; box.addView(TextView(this).apply { text = title; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#FF8A80")) }); return box }
    private fun refreshPermissionStates() { if (Settings.canDrawOverlays(this)) { statusDot.background = GradientDrawable().apply { setColor(Color.parseColor("#34C759")); cornerRadius = 90f }; statusText.text = "SERVICE READY (Tap Accessibility to Restart)"; statusText.setTextColor(Color.parseColor("#34C759")); overlayCard.background = GradientDrawable().apply { setColor(Color.parseColor("#091A0F")); setStroke(3, Color.parseColor("#163D22")); cornerRadius = 24f } } }

    private fun createThemeStylePanel(): View {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }; val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, 40) }; val styles = listOf(Triple("Simple", "Flat Colors", "Simple"), Triple("Neon", "Requires Beginner", "Neon"), Triple("Glass", "Requires Pro", "Glass")); val currentTheme = prefs.getString("PREF_VISUAL_STYLE", "Simple") ?: "Simple"
        styles.forEach { style -> val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40); layoutParams = LinearLayout.LayoutParams(380, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 32, 0) }; setOnClickListener { val userTier = prefs.getInt("PREF_USER_TIER", 0); val requiredTier = when (style.third) { "Neon" -> 1; "Glass" -> 2; else -> 0 }; if (userTier >= requiredTier) { prefs.edit().putString("PREF_VISUAL_STYLE", style.third).apply(); updateThemeSelectionUI(style.third) } else { Toast.makeText(this@MainActivity, "Requires ${if (requiredTier==1) "Beginner" else "Pro"} Tier!", Toast.LENGTH_SHORT).show() } } }; card.addView(TextView(this).apply { text = style.first; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); setPadding(0, 0, 0, 8) }); card.addView(TextView(this).apply { text = style.second; textSize = 11f; setTextColor(Color.parseColor("#8E8E93")) }); themeCards[style.third] = card; row.addView(card) }
        updateThemeSelectionUI(currentTheme); scroll.addView(row); return scroll
    }
    private fun updateThemeSelectionUI(selectedTheme: String) { themeCards.forEach { (themeName, card) -> card.background = if (themeName == selectedTheme) GradientDrawable().apply { setColor(Color.parseColor("#1C1C22")); setStroke(5, Color.parseColor("#2979FF")); cornerRadius = 32f } else GradientDrawable().apply { setColor(Color.parseColor("#121214")); setStroke(0, Color.TRANSPARENT); cornerRadius = 32f } } }

    private fun createAppSelectPanel(): View { val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40); background = GradientDrawable().apply { setColor(Color.parseColor("#121214")); cornerRadius = 40f }; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 40) } }; val currentAppName = prefs.getString("PREF_CUSTOM_APP_NAME", "None Selected"); selectedAppLabel = TextView(this).apply { text = "Selected: $currentAppName"; setTextColor(Color.WHITE); textSize = 16f; setPadding(0, 0, 0, 32) }; val selectBtn = Button(this).apply { text = "Choose App"; setBackgroundColor(Color.parseColor("#2979FF")); setTextColor(Color.WHITE); setOnClickListener { if (prefs.getInt("PREF_USER_TIER", 0) >= 2) showAppPicker() else Toast.makeText(this@MainActivity, "Custom App Shortcuts require Pro Tier!", Toast.LENGTH_SHORT).show() } }; panel.addView(selectedAppLabel); panel.addView(selectBtn); return panel }
    private fun showAppPicker() { val pm = packageManager; val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER); val resolveInfos = pm.queryIntentActivities(intent, 0); val appList = resolveInfos.map { Pair(it.loadLabel(pm).toString(), it.activityInfo.packageName) }.sortedBy { it.first }; val names = appList.map { it.first }.toTypedArray(); AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Select App").setItems(names) { _, which -> prefs.edit().putString("PREF_CUSTOM_APP_NAME", appList[which].first).putString("PREF_CUSTOM_APP_PKG", appList[which].second).apply(); selectedAppLabel.text = "Selected: ${appList[which].first}"; Toast.makeText(this, "Saved! Toggle Service to reload.", Toast.LENGTH_SHORT).show() }.show() }
    private fun createSectionTitle(title: String, subtitle: String = ""): View { val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 32, 0, 24) }; row.addView(TextView(this).apply { text = title; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#8E8E93")); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }); if (subtitle.isNotEmpty()) row.addView(TextView(this).apply { text = subtitle; textSize = 10f; setTextColor(Color.parseColor("#2979FF")) }); return row }
    private fun createSlidersPanel(): View { val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40); background = GradientDrawable().apply { setColor(Color.parseColor("#121214")); cornerRadius = 40f } }; fun createLabelRow(title: String, percentView: TextView?): LinearLayout { return LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 16, 0, 16); addView(TextView(this@MainActivity).apply { text = title; setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }); if (percentView != null) addView(percentView) } }; val heightPercent = TextView(this).apply { setTextColor(Color.parseColor("#2979FF")); typeface = Typeface.DEFAULT_BOLD }; val heightSlider = SeekBar(this).apply { max = 1200; progress = prefs.getInt("PREF_BAR_HEIGHT", 750) - 200; heightPercent.text = "${(progress * 100 / max)}%"; setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(s: SeekBar?, prog: Int, f: Boolean) { prefs.edit().putInt("PREF_BAR_HEIGHT", prog + 200).apply(); heightPercent.text = "${(prog * 100 / max)}%" }; override fun onStartTrackingTouch(s: SeekBar?) {}; override fun onStopTrackingTouch(s: SeekBar?) {} }) }; panel.addView(createLabelRow("Bar Height", heightPercent)); panel.addView(heightSlider); val widthPercent = TextView(this).apply { setTextColor(Color.parseColor("#2979FF")); typeface = Typeface.DEFAULT_BOLD; setPadding(0,24,0,0) }; val widthSlider = SeekBar(this).apply { max = 100; progress = prefs.getInt("PREF_BAR_WIDTH", 55) - 20; widthPercent.text = "$progress%"; setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(s: SeekBar?, prog: Int, f: Boolean) { prefs.edit().putInt("PREF_BAR_WIDTH", prog + 20).apply(); widthPercent.text = "$prog%" }; override fun onStartTrackingTouch(s: SeekBar?) {}; override fun onStopTrackingTouch(s: SeekBar?) {} }) }; panel.addView(createLabelRow("Bar Width", widthPercent)); panel.addView(widthSlider); val opacityPercent = TextView(this).apply { setTextColor(Color.parseColor("#2979FF")); typeface = Typeface.DEFAULT_BOLD; setPadding(0,24,0,0) }; val opacitySlider = SeekBar(this).apply { max = 100; progress = prefs.getInt("PREF_BAR_ALPHA", 100); opacityPercent.text = "$progress%"; setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(s: SeekBar?, prog: Int, f: Boolean) { prefs.edit().putInt("PREF_BAR_ALPHA", prog).apply(); opacityPercent.text = "$prog%" }; override fun onStartTrackingTouch(s: SeekBar?) {}; override fun onStopTrackingTouch(s: SeekBar?) {} }) }; panel.addView(createLabelRow("Bar Opacity", opacityPercent)); panel.addView(opacitySlider); val posSlider = SeekBar(this).apply { max = 1000; progress = prefs.getInt("PREF_BAR_POS", 0) + 500; setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(s: SeekBar?, prog: Int, f: Boolean) { if (prefs.getInt("PREF_USER_TIER", 0) >= 3) { prefs.edit().putInt("PREF_BAR_POS", prog - 500).apply() } else { progress = 500; Toast.makeText(this@MainActivity, "Custom Position requires Master Tier!", Toast.LENGTH_SHORT).show() } }; override fun onStartTrackingTouch(s: SeekBar?) {}; override fun onStopTrackingTouch(s: SeekBar?) {} }) }; panel.addView(createLabelRow("Vertical Position (Master Tier)", null).apply { setPadding(0, 24, 0, 16) }); panel.addView(posSlider); return panel }
    private fun createBackupRestorePanel(): View { val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40); background = GradientDrawable().apply { setColor(Color.parseColor("#121214")); cornerRadius = 40f }; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 40) } }; val backupBtn = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 16, 0, 32); addView(TextView(this@MainActivity).apply { text = "↑"; textSize = 24f; setTextColor(Color.parseColor("#2979FF")); setPadding(0, 0, 32, 0) }); val textLayout = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }; textLayout.addView(TextView(this@MainActivity).apply { text = "Create Backup"; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) }); textLayout.addView(TextView(this@MainActivity).apply { text = "Save settings to a local file"; textSize = 11f; setTextColor(Color.parseColor("#8E8E93")) }); addView(textLayout); setOnClickListener { if (prefs.getInt("PREF_USER_TIER", 0) >= 2) backupLauncher.launch("pie_backup.json") else Toast.makeText(this@MainActivity, "Backup requires Pro Tier!", Toast.LENGTH_SHORT).show() } }; val restoreBtn = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 16, 0, 16); addView(TextView(this@MainActivity).apply { text = "↓"; textSize = 24f; setTextColor(Color.parseColor("#34C759")); setPadding(0, 0, 32, 0) }); val textLayout = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }; textLayout.addView(TextView(this@MainActivity).apply { text = "Restore Backup"; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) }); textLayout.addView(TextView(this@MainActivity).apply { text = "Load settings from a file"; textSize = 11f; setTextColor(Color.parseColor("#8E8E93")) }); addView(textLayout); setOnClickListener { if (prefs.getInt("PREF_USER_TIER", 0) >= 2) restoreLauncher.launch(arrayOf("application/json", "*/*")) else Toast.makeText(this@MainActivity, "Restore requires Pro Tier!", Toast.LENGTH_SHORT).show() } }; panel.addView(backupBtn); panel.addView(restoreBtn); return panel }
    private fun createSubscriptionPanel(): View { val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 40) } }; val currentTier = prefs.getInt("PREF_USER_TIER", 0); fun createTierCard(tierLevel: Int, titleText: String, descText: String, colorHex: String, unlockCode: String): View { val isUnlocked = currentTier >= tierLevel; val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 48, 48, 48); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 32) }; background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor(if (isUnlocked) "#1C261D" else "#1A1A1E"), Color.parseColor(if (isUnlocked) "#121A13" else "#121214"))).apply { cornerRadius = 40f; setStroke(3, Color.parseColor(if (isUnlocked) "#34C759" else colorHex)) } }; val headerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, 16) }; val title = TextView(this).apply { text = titleText; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }; val statusBadge = TextView(this).apply { text = if (isUnlocked) "ACTIVE" else "LOCKED"; textSize = 10f; typeface = Typeface.DEFAULT_BOLD; setPadding(16, 8, 16, 8); setTextColor(if (isUnlocked) Color.parseColor("#121A13") else Color.WHITE); background = GradientDrawable().apply { cornerRadius = 20f; setColor(Color.parseColor(if (isUnlocked) "#34C759" else "#404040")) } }; headerRow.addView(title); headerRow.addView(statusBadge); val desc = TextView(this).apply { text = descText; textSize = 12f; setTextColor(Color.parseColor("#A0A0A5")); setPadding(0, 0, 0, 24) }; val actionBtn = Button(this).apply { text = if (isUnlocked) "UNLOCKED" else "Enter Passcode"; setBackgroundColor(if (isUnlocked) Color.parseColor("#34C759") else Color.parseColor(colorHex)); setTextColor(if (isUnlocked) Color.BLACK else Color.WHITE); isEnabled = !isUnlocked; setOnClickListener { val input = EditText(this@MainActivity).apply { hint = "Enter Secret Code"; setTextColor(Color.BLACK); setPadding(48, 48, 48, 48) }; AlertDialog.Builder(this@MainActivity, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert).setTitle("Unlock $titleText").setMessage("Enter passcode from developer:").setView(input).setPositiveButton("Unlock") { _, _ -> if (input.text.toString().trim().uppercase() == unlockCode) { prefs.edit().putInt("PREF_USER_TIER", tierLevel).apply(); Toast.makeText(this@MainActivity, "$titleText Unlocked! Restarting...", Toast.LENGTH_LONG).show(); finish(); startActivity(intent) } else Toast.makeText(this@MainActivity, "Invalid Code!", Toast.LENGTH_SHORT).show() }.setNegativeButton("Cancel", null).show() } }; card.addView(headerRow); card.addView(desc); card.addView(actionBtn); return card }; container.addView(createTierCard(1, "Beginner", "Unlocks Neon Theme, Native Haptics, and 5 Tiles.", "#2979FF", "BEGIN26")); container.addView(createTierCard(2, "Pro", "Unlocks Glass Theme, Custom App Shortcuts, and Cloud Backups.", "#6C2BD9", "PRO26")); container.addView(createTierCard(3, "Master", "Unlocks Custom Position Slider, Custom Physics, and VIP Badge.", "#FFC107", "MASTER26")); return container }
}

class PiePreviewBackground(context: Context, val radius: Float) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1C1C22"); style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2979FF"); style = Paint.Style.STROKE; strokeWidth = 5f }
    override fun onDraw(canvas: Canvas) { super.onDraw(canvas); canvas.drawCircle(width.toFloat() + 10f, height / 2f, radius, paint); canvas.drawCircle(width.toFloat() + 10f, height / 2f, radius, borderPaint) }
}

class BubbleBackgroundView(context: Context) : View(context) {
    private data class Bubble(var x: Float, var y: Float, var r: Float, var dx: Float, var dy: Float)
    private val bubbles = mutableListOf<Bubble>()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#181822"); style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#38384C"); style = Paint.Style.STROKE; strokeWidth = 3.5f }
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#18FFFFFF"); style = Paint.Style.FILL }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh); bubbles.clear(); if (w == 0 || h == 0) return
        for (i in 0 until 15) { val radius = 55f + (Math.random() * 110f).toFloat(); val x = radius + (Math.random() * (w - 2f * radius)).toFloat(); val y = radius + (Math.random() * (h - 2f * radius)).toFloat(); val dx = (if (Math.random() > 0.5) 1f else -1f) * (0.35f + (Math.random() * 1.1f).toFloat()); val dy = (if (Math.random() > 0.5) 1f else -1f) * (0.35f + (Math.random() * 1.1f).toFloat()); bubbles.add(Bubble(x, y, radius, dx, dy)) }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (i in bubbles.indices) {
            val b = bubbles[i]; b.x += b.dx; b.y += b.dy
            if (b.x - b.r < 0) { b.x = b.r; b.dx *= -1f }; if (b.x + b.r > width) { b.x = width - b.r; b.dx *= -1f }; if (b.y - b.r < 0) { b.y = b.r; b.dy *= -1f }; if (b.y + b.r > height) { b.y = height - b.r; b.dy *= -1f }
            for (j in i + 1 until bubbles.size) {
                val b2 = bubbles[j]; val diffX = b.x - b2.x; val diffY = b.y - b2.y; val distSq = diffX * diffX + diffY * diffY; val minDist = b.r + b2.r
                if (distSq < minDist * minDist) { val tempDx = b.dx; val tempDy = b.dy; b.dx = b2.dx; b.dy = b2.dy; b2.dx = tempDx; b2.dy = tempDy; val dist = Math.sqrt(distSq.toDouble()).toFloat(); val overlap = minDist - dist; if (dist > 0f) { val nx = diffX / dist; val ny = diffY / dist; b.x += nx * (overlap / 2f); b.y += ny * (overlap / 2f); b2.x -= nx * (overlap / 2f); b2.y -= ny * (overlap / 2f) } }
            }
            canvas.drawCircle(b.x, b.y, b.r, fillPaint); canvas.drawCircle(b.x, b.y, b.r, strokePaint); canvas.drawCircle(b.x - b.r * 0.32f, b.y - b.r * 0.32f, b.r * 0.22f, sheenPaint)
        }
        invalidate()
    }
}
