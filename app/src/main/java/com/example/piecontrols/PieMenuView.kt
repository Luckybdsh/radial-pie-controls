package com.example.piecontrols

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.*

class PieMenuView(
    context: Context,
    private val onActionSelected: (Int) -> Unit,
    private val onDismiss: () -> Unit
) : View(context) {

    private val slices = mutableListOf<Slice>()
    private var activeSlice = -1
    private var startX = 0f
    private var startY = 0f
    private var animProgress = 0f
    
    private var visualTheme = "Neon"
    private var isCenterActive = false // Tracks if thumb is hovering the close button

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        pathEffect = CornerPathEffect(15f)
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = CornerPathEffect(15f)
    }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(6f, 0f, 2f, Color.argb(150, 0, 0, 0))
    }

    private val bgDimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        loadCustomTiles()
    }

    private fun getAppIconBitmap(pkgName: String): Bitmap? {
        return try {
            val drawable: Drawable = context.packageManager.getApplicationIcon(pkgName)
            val bitmap = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (e: Exception) { null }
    }

    private fun loadCustomTiles() {
        val prefs = context.getSharedPreferences("PiePrefs", Context.MODE_PRIVATE)
        visualTheme = prefs.getString("PREF_VISUAL_STYLE", "Neon") ?: "Neon"
        
        val savedOrder = prefs.getString("PREF_TILE_ACTIONS", "2,0,6,4,1") ?: "2,0,6,4,1"
        val actionIds = savedOrder.split(",").mapNotNull { it.toIntOrNull() }
        
        val themeColors = listOf("#00E5FF", "#B388FF", "#69F0AE", "#FF8A80", "#FFD54F", "#FF4081")

        val allActions = mapOf(
            0 to "HOME", 1 to "SCREENSHOT", 2 to "BACK",
            3 to "VOLUME", 4 to "RECENTS", 5 to "NOTIFS", 6 to "APP"
        )

        val customPkg = prefs.getString("PREF_CUSTOM_APP_PKG", "com.google.android.youtube") ?: "com.google.android.youtube"
        var customAppBitmap: Bitmap? = null
        if (actionIds.contains(6)) customAppBitmap = getAppIconBitmap(customPkg)

        slices.clear()
        actionIds.forEachIndexed { index, actionId ->
            val color = Color.parseColor(themeColors[index % themeColors.size])
            val name = allActions[actionId] ?: "APP"
            val slice = Slice(name, color, actionId)
            if (actionId == 6 && customAppBitmap != null) slice.customIcon = customAppBitmap
            slices.add(slice)
        }
    }

    fun setOrigin(x: Float, y: Float) {
        startX = x
        startY = y
        activeSlice = -1
        isCenterActive = false // Reset close button

        triggerHaptic(25L)

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener {
                animProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).roundToInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (animProgress <= 0f) return

        val safeProgress = animProgress.coerceIn(0f, 1f) // Prevents alpha values crashing from overshoot

        bgDimPaint.alpha = (120 * safeProgress).toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgDimPaint)

        canvas.save()
        canvas.rotate(-45f * (1f - safeProgress), startX, startY)

        val totalSpan = 165f
        val gapAngle = 4f 
        val sweepAngle = totalSpan / slices.size.toFloat()

        slices.forEachIndexed { i, slice ->
            val staggerOffset = (slices.size - 1 - i) * 0.05f 
            val sliceProgress = (animProgress - staggerOffset).coerceAtLeast(0f)
            if (sliceProgress <= 0f) return@forEachIndexed

            val isSelected = (i == activeSlice)
            
            val baseInnerR = 140f * sliceProgress
            val baseOuterR = 410f * sliceProgress 
            
            val currentOuterR = if (isSelected) baseOuterR + 40f else baseOuterR
            val currentInnerR = if (isSelected) baseInnerR - 15f else baseInnerR

            val startAngle = 100f + (i.toFloat() * sweepAngle)
            val actualStart = startAngle + (gapAngle / 2f)
            val actualSweep = sweepAngle - gapAngle

            val path = Path()
            val innerRect = RectF(startX - currentInnerR, startY - currentInnerR, startX + currentInnerR, startY + currentInnerR)
            val outerRect = RectF(startX - currentOuterR, startY - currentOuterR, startX + currentOuterR, startY + currentOuterR)

            path.arcTo(outerRect, actualStart, actualSweep)
            path.arcTo(innerRect, actualStart + actualSweep, -actualSweep)
            path.close()

            fillPaint.shader = null
            fillPaint.clearShadowLayer()
            borderPaint.color = Color.TRANSPARENT

            when (visualTheme) {
                "Simple" -> {
                    fillPaint.color = slice.color
                    fillPaint.alpha = if (isSelected) 255 else 180
                }
                "Neon" -> {
                    fillPaint.color = slice.color
                    fillPaint.alpha = if (isSelected) 255 else 180
                    if (isSelected) {
                        fillPaint.setShadowLayer(40f, 0f, 0f, slice.color)
                    }
                }
                "Glass" -> {
                    val startColor = adjustAlpha(slice.color, if (isSelected) 0.85f else 0.25f)
                    val endColor = adjustAlpha(slice.color, if (isSelected) 0.35f else 0.05f)
                    fillPaint.shader = RadialGradient(
                        startX, startY, currentOuterR,
                        intArrayOf(startColor, endColor),
                        floatArrayOf(0.3f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    borderPaint.color = adjustAlpha(Color.WHITE, if (isSelected) 0.6f else 0.15f)
                }
            }

            canvas.drawPath(path, fillPaint)
            if (visualTheme == "Glass") canvas.drawPath(path, borderPaint)

            val midAngle = Math.toRadians((actualStart + actualSweep / 2f).toDouble())
            val iconR = currentInnerR + (currentOuterR - currentInnerR) * 0.45f
            val cx = (startX + iconR * cos(midAngle).toFloat())
            val cy = (startY + iconR * sin(midAngle).toFloat())

            val scale = if (isSelected) 1.2f else 1.0f
            canvas.save()
            canvas.scale(scale, scale, cx, cy)
            drawSliceIcon(canvas, slice, cx, cy)
            canvas.restore()

            val textR = currentInnerR + (currentOuterR - currentInnerR) * 0.85f
            val textX = (startX + textR * cos(midAngle).toFloat())
            val textY = (startY + textR * sin(midAngle).toFloat())
            
            textPaint.color = adjustAlpha(Color.WHITE, if (isSelected) 1.0f else 0.6f)
            textPaint.textSize = if (isSelected) 22f else 18f
            canvas.drawText(slice.title, textX, textY, textPaint)
        }
        canvas.restore()

        // -------------------------------------------------
        // NEW: CENTER CLOSE BUTTON (Replaces old static handle)
        // -------------------------------------------------
        val baseCenterR = 90f * animProgress
        val centerR = if (isCenterActive) baseCenterR + 15f else baseCenterR
        
        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (isCenterActive) Color.parseColor("#FF453A") else Color.parseColor("#1C1C1E")
            alpha = if (isCenterActive) 255 else (220 * safeProgress).toInt()
            if (isCenterActive) {
                setShadowLayer(40f, 0f, 0f, Color.parseColor("#FF453A"))
            }
        }
        
        // Draws the semi-circle origin button
        canvas.drawCircle(startX, startY, centerR, centerPaint)

        val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isCenterActive) Color.WHITE else Color.parseColor("#8E8E93")
            style = Paint.Style.STROKE
            strokeWidth = 7f
            strokeCap = Paint.Cap.ROUND
            alpha = (255 * safeProgress).toInt()
        }
        
        val crossSize = 14f * safeProgress
        val crossCx = startX - 45f // Offset inward so it's fully visible on screen
        val crossCy = startY
        
        // Draws the explicit 'X' icon
        canvas.drawLine(crossCx - crossSize, crossCy - crossSize, crossCx + crossSize, crossCy + crossSize, crossPaint)
        canvas.drawLine(crossCx - crossSize, crossCy + crossSize, crossCx + crossSize, crossCy - crossSize, crossPaint)
    }

    private fun drawSliceIcon(canvas: Canvas, slice: Slice, cx: Float, cy: Float) {
        if (slice.id == 6 && slice.customIcon != null) {
            val bmp = slice.customIcon!!
            canvas.drawBitmap(bmp, cx - (bmp.width / 2f), cy - (bmp.height / 2f), null)
            return
        }

        when (slice.id) {
            0 -> { 
                val path = Path().apply { moveTo(cx - 20f, cy + 2f); lineTo(cx, cy - 18f); lineTo(cx + 20f, cy + 2f); lineTo(cx + 14f, cy + 2f); lineTo(cx + 14f, cy + 20f); lineTo(cx - 14f, cy + 20f); lineTo(cx - 14f, cy + 2f); close() }
                canvas.drawPath(path, iconPaint)
            }
            1 -> { 
                canvas.drawRoundRect(RectF(cx - 20f, cy - 16f, cx + 20f, cy + 16f), 6f, 6f, iconPaint)
                canvas.drawCircle(cx, cy, 6f, iconPaint)
            }
            2 -> { 
                val path = Path().apply { moveTo(cx + 8f, cy - 18f); lineTo(cx - 10f, cy); lineTo(cx + 8f, cy + 18f) }
                canvas.drawPath(path, iconPaint)
            }
            3 -> { 
                val path = Path().apply { moveTo(cx - 14f, cy - 6f); lineTo(cx - 6f, cy - 6f); lineTo(cx + 8f, cy - 16f); lineTo(cx + 8f, cy + 16f); lineTo(cx - 6f, cy + 6f); lineTo(cx - 14f, cy + 6f); close() }
                canvas.drawPath(path, iconPaint)
                canvas.drawArc(RectF(cx + 6f, cy - 10f, cx + 20f, cy + 10f), -45f, 90f, false, iconPaint)
            }
            4 -> { 
                canvas.drawRoundRect(RectF(cx - 14f, cy - 14f, cx + 6f, cy + 6f), 4f, 4f, iconPaint)
                val path = Path().apply { moveTo(cx - 4f, cy + 14f); lineTo(cx + 14f, cy + 14f); lineTo(cx + 14f, cy - 4f) }
                canvas.drawPath(path, iconPaint)
            }
            5 -> { 
                val path = Path().apply { moveTo(cx, cy - 14f); arcTo(RectF(cx - 10f, cy - 14f, cx + 10f, cy + 6f), 180f, 180f); lineTo(cx + 16f, cy + 10f); lineTo(cx - 16f, cy + 10f); close() }
                canvas.drawPath(path, iconPaint)
                canvas.drawArc(RectF(cx - 5f, cy + 10f, cx + 5f, cy + 20f), 0f, 180f, false, iconPaint)
            }
            6 -> { 
                val appPaint = Paint(iconPaint).apply { style = Paint.Style.FILL }
                canvas.drawRoundRect(RectF(cx - 14f, cy - 14f, cx - 4f, cy - 4f), 4f, 4f, appPaint)
                canvas.drawRoundRect(RectF(cx + 4f, cy - 14f, cx + 14f, cy - 4f), 4f, 4f, appPaint)
                canvas.drawRoundRect(RectF(cx - 14f, cy + 4f, cx - 4f, cy + 14f), 4f, 4f, appPaint)
                canvas.drawRoundRect(RectF(cx + 4f, cy + 4f, cx + 14f, cy + 14f), 4f, 4f, appPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (animProgress < 0.9f) return true
        val dx = event.rawX - startX
        val dy = event.rawY - startY
        val dist = hypot(dx.toDouble(), dy.toDouble())

        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                // If thumb is pushed OUT into the tiles (distance > 110)
                if (dist > 110.0) { 
                    if (isCenterActive) {
                        isCenterActive = false
                        invalidate() // Turn off the red 'X' center button
                    }
                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
                    if (angle < 0.0) angle += 360.0

                    if (angle in 100.0..265.0) {
                        val normalized = angle - 100.0
                        val sliceIndex = (normalized / (165.0 / slices.size.toDouble())).toInt().coerceIn(0, slices.size - 1)
                        if (sliceIndex != activeSlice) {
                            activeSlice = sliceIndex
                            triggerHaptic(12L) 
                            invalidate()
                        }
                    } else {
                        if (activeSlice != -1) {
                            activeSlice = -1
                            invalidate()
                        }
                    }
                } else {
                    // Thumb pulled BACK into the center (distance < 110)
                    if (activeSlice != -1) {
                        activeSlice = -1
                        invalidate() // Turn off active tiles
                    }
                    if (!isCenterActive) {
                        isCenterActive = true
                        triggerHaptic(15L) // Light haptic tick indicating closure area
                        invalidate() // Light up the red 'X' button
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (activeSlice != -1) {
                    triggerHaptic(18L)
                    onActionSelected(slices[activeSlice].id)
                } else if (isCenterActive) {
                    triggerHaptic(20L) // Distinct haptic pop on manual close
                }
                onDismiss()
            }
            MotionEvent.ACTION_CANCEL -> onDismiss()
        }
        return true
    }

    private fun triggerHaptic(duration: Long) {
        try { vibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)) } catch (_: Exception) {}
    }

    data class Slice(val title: String, val color: Int, val id: Int, var customIcon: Bitmap? = null)
}
