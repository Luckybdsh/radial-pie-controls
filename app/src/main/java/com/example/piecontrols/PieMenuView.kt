package com.example.piecontrols

import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
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

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    // BASE PAINT (The gradient shader will be applied dynamically per slice)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        pathEffect = CornerPathEffect(40f) // Keeps inner corners sharp, outer edges soft
    }

    // GLASS BORDER (Thin semi-transparent stroke)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = CornerPathEffect(40f)
    }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // CURVED TEXT PAINT
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        letterSpacing = 0.05f
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
        val savedOrder = prefs.getString("PREF_TILE_ACTIONS", "2,0,6,4,1") ?: "2,0,6,4,1"
        val actionIds = savedOrder.split(",").mapNotNull { it.toIntOrNull() }
        
        val theme = prefs.getString("PREF_THEME", "Neon") ?: "Neon"
        val themeColors = when (theme) {
            "Pastel" -> listOf("#FFB3BA", "#FFDFBA", "#FFFFBA", "#BAFFC9", "#BAE1FF", "#E8BAFF")
            "Mono" -> listOf("#FFFFFF", "#CCCCCC", "#A3A3A3", "#7A7A7A", "#525252", "#292929")
            else -> listOf("#00E5FF", "#B388FF", "#69F0AE", "#FF8A80", "#FFD54F", "#FF4081")
        }

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

        // HAPTIC: Initial pop when menu opens
        triggerHaptic(25L)

        // SPRING PHYSICS: Longer duration with high tension overshoot
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

    // Adjusts alpha of a hex color for the Glassmorphism effect
    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).roundToInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (animProgress <= 0f) return

        bgDimPaint.alpha = (120 * animProgress.coerceIn(0f, 1f)).toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgDimPaint)

        canvas.save()
        canvas.rotate(-45f * (1f - animProgress.coerceIn(0f, 1f)), startX, startY)

        val totalSpan = 165f
        val gapAngle = 8f 
        val sweepAngle = totalSpan / slices.size.toFloat()

        slices.forEachIndexed { i, slice ->
            
            // STAGGERED ENTRY: Each slice is delayed slightly based on its index
            val staggerOffset = (slices.size - 1 - i) * 0.05f 
            val sliceProgress = (animProgress - staggerOffset).coerceAtLeast(0f)
            if (sliceProgress <= 0f) return@forEachIndexed

            val isSelected = (i == activeSlice)
            
            // ACTIVE SCALING: Slices pop outward and grow when hovered
            val baseInnerR = 140f * sliceProgress
            val baseOuterR = 390f * sliceProgress 
            
            val currentOuterR = if (isSelected) baseOuterR + 55f else baseOuterR
            val currentInnerR = if (isSelected) baseInnerR - 15f else baseInnerR

            val startAngle = 100f + (i.toFloat() * sweepAngle)
            val actualStart = startAngle + (gapAngle / 2f)
            val actualSweep = sweepAngle - gapAngle
            val endAngle = actualStart + actualSweep

            val path = Path()
            val innerRect = RectF(startX - currentInnerR, startY - currentInnerR, startX + currentInnerR, startY + currentInnerR)

            // Geometry Setup
            path.arcTo(innerRect, actualStart, actualSweep)
            val outerEndX = startX + currentOuterR * cos(Math.toRadians(endAngle.toDouble())).toFloat()
            val outerEndY = startY + currentOuterR * sin(Math.toRadians(endAngle.toDouble())).toFloat()
            path.lineTo(outerEndX, outerEndY)

            val midAngle = Math.toRadians((actualStart + actualSweep / 2f).toDouble())
            val bulgeR = currentOuterR + 35f 
            val controlX = startX + bulgeR * cos(midAngle).toFloat()
            val controlY = startY + bulgeR * sin(midAngle).toFloat()

            val outerStartX = startX + currentOuterR * cos(Math.toRadians(actualStart.toDouble())).toFloat()
            val outerStartY = startY + currentOuterR * sin(Math.toRadians(actualStart.toDouble())).toFloat()

            path.quadTo(controlX, controlY, outerStartX, outerStartY)
            path.close()

            // GLASSMORPHISM GRADIANT & STROKE
            val startColor = adjustAlpha(slice.color, if (isSelected) 0.85f else 0.25f)
            val endColor = adjustAlpha(slice.color, if (isSelected) 0.35f else 0.05f)
            
            fillPaint.shader = RadialGradient(
                startX, startY, currentOuterR + 40f,
                intArrayOf(startColor, endColor),
                floatArrayOf(0.3f, 1f),
                Shader.TileMode.CLAMP
            )
            
            borderPaint.color = adjustAlpha(Color.WHITE, if (isSelected) 0.6f else 0.15f)

            canvas.drawPath(path, fillPaint)
            canvas.drawPath(path, borderPaint) // Draw glass border

            // ICON POSITIONING
            val iconR = currentInnerR + (currentOuterR - currentInnerR) * 0.45f
            val cx = (startX + iconR * cos(midAngle).toFloat())
            val cy = (startY + iconR * sin(midAngle).toFloat())

            val scale = if (isSelected) 1.2f else 1.0f
            canvas.save()
            canvas.scale(scale, scale, cx, cy)
            drawSliceIcon(canvas, slice, cx, cy)
            canvas.restore()

            // CURVED TYPOGRAPHY
            val textPath = Path()
            val textR = currentInnerR + (currentOuterR - currentInnerR) * 0.85f // Pushed near outer edge
            val textRect = RectF(startX - textR, startY - textR, startX + textR, startY + textR)
            
            textPath.addArc(textRect, actualStart, actualSweep)
            
            textPaint.color = adjustAlpha(Color.WHITE, if (isSelected) 1.0f else 0.6f)
            textPaint.textSize = if (isSelected) 26f else 22f
            
            // Draws text perfectly curved along the arc of the slice
            canvas.drawTextOnPath(slice.title, textPath, 0f, 10f, textPaint)
        }
        canvas.restore()

        val anchorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#40FFFFFF") }
        val anchorRect = RectF(startX - 20f, startY - 90f, startX + 15f, startY + 90f)
        canvas.drawRoundRect(anchorRect, 30f, 30f, anchorPaint)
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
                if (dist > 90.0) {
                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
                    if (angle < 0.0) angle += 360.0

                    if (angle in 100.0..265.0) {
                        val normalized = angle - 100.0
                        val sliceIndex = (normalized / (165.0 / slices.size.toDouble())).toInt().coerceIn(0, slices.size - 1)
                        if (sliceIndex != activeSlice) {
                            activeSlice = sliceIndex
                            // HAPTIC: Subtle tactile tick crossing slice boundaries
                            triggerHaptic(12L) 
                            invalidate()
                        }
                    } else {
                        if (activeSlice != -1) {
                            activeSlice = -1
                            invalidate()
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (activeSlice != -1) {
                    triggerHaptic(18L)
                    onActionSelected(slices[activeSlice].id)
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
