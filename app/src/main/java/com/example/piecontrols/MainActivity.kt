package com.example.piecontrols

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.Collections

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var dragContainer: LinearLayout
    private var currentTiles = mutableListOf<TileData>()

    // Master list of all available actions
    private val allActions = mapOf(
        0 to "Home", 1 to "Screenshot", 2 to "Back",
        3 to "Volume", 4 to "Recents", 5 to "Notifs", 6 to "App"
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

        mainLayout.addView(createSectionTitle("EDGE BAR SETTINGS"))
        mainLayout.addView(createSlidersPanel())

        mainLayout.addView(createSectionTitle("DRAG & DROP TILES", "Long press to move"))
        mainLayout.addView(createDragDropPanel())

        rootScroll.addView(mainLayout)
        setContentView(rootScroll)

        loadTiles()
    }

    private fun createSectionTitle(title: String, subtitle: String = ""): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 32, 0, 24)
        }
        row.addView(TextView(this).apply {
            text = title
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#8E8E93"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (subtitle.isNotEmpty()) {
            row.addView(TextView(this).apply {
                text = subtitle
                textSize = 10f
                setTextColor(Color.parseColor("#2979FF"))
            })
        }
        return row
    }

    private fun createSlidersPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#121214"))
                cornerRadius = 40f
            }
        }

        // --- HEIGHT SLIDER ---
        val heightLabel = TextView(this).apply {
            text = "Bar Height"
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 16)
        }
        val heightSlider = SeekBar(this).apply {
            max = 1200
            progress = prefs.getInt("PREF_BAR_HEIGHT", 750) - 200
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    prefs.edit().putInt("PREF_BAR_HEIGHT", progress + 200).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        // --- POSITION SLIDER ---
        val posLabel = TextView(this).apply {
            text = "Bar Vertical Position"
            setTextColor(Color.WHITE)
            setPadding(0, 48, 0, 16)
        }
        val posSlider = SeekBar(this).apply {
            max = 1000 // -500 to +500
            progress = prefs.getInt("PREF_BAR_POS", 0) + 500
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    prefs.edit().putInt("PREF_BAR_POS", progress - 500).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        panel.addView(heightLabel)
        panel.addView(heightSlider)
        panel.addView(posLabel)
        panel.addView(posSlider)
        return panel
    }

    private fun createDragDropPanel(): View {
        dragContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#121214"))
                cornerRadius = 40f
            }
        }
        return dragContainer
    }

    private fun loadTiles() {
        val savedStr = prefs.getString("PREF_TILE_ACTIONS", "2,0,6,4,1") ?: "2,0,6,4,1"
        val ids = savedStr.split(",").mapNotNull { it.toIntOrNull() }
        
        currentTiles.clear()
        ids.forEach { id ->
            val name = allActions[id] ?: "Unknown"
            currentTiles.add(TileData(id, name))
        }
        renderTiles()
    }

    private fun renderTiles() {
        dragContainer.removeAllViews()

        currentTiles.forEachIndexed { index, tile ->
            val tileView = createTileRow(tile.name)
            tileView.tag = index 

            // Enable starting the drag on long press
            tileView.setOnLongClickListener { v ->
                val item = ClipData.Item(index.toString()) // Pass the index being dragged
                val dragData = ClipData(v.tag.toString(), arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN), item)
                val shadow = View.DragShadowBuilder(v)
                v.startDragAndDrop(dragData, shadow, null, 0)
                true
            }

            // Handle dropping another item onto this one
            tileView.setOnDragListener { v, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> true
                    DragEvent.ACTION_DRAG_ENTERED -> {
                        v.setBackgroundColor(Color.parseColor("#292930"))
                        true
                    }
                    DragEvent.ACTION_DRAG_EXITED -> {
                        v.background = null
                        true
                    }
                    DragEvent.ACTION_DROP -> {
                        v.background = null
                        val draggedIndex = event.clipData.getItemAt(0).text.toString().toInt()
                        val targetIndex = v.tag as Int
                        
                        // Swap the data in the array
                        Collections.swap(currentTiles, draggedIndex, targetIndex)
                        saveTileOrder()
                        renderTiles() // Redraw the new list
                        true
                    }
                    DragEvent.ACTION_DRAG_ENDED -> {
                        v.background = null
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
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 8, 0, 8)
            }
        }
        val grip = TextView(this).apply {
            text = "≡"
            textSize = 20f
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, 0, 32, 0)
        }
        val label = TextView(this).apply {
            text = name
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        row.addView(grip)
        row.addView(label)
        return row
    }

    private fun saveTileOrder() {
        val idString = currentTiles.joinToString(",") { it.id.toString() }
        prefs.edit().putString("PREF_TILE_ACTIONS", idString).apply()
    }
}
