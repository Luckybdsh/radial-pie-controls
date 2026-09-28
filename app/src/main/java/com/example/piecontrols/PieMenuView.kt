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

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        // REVERTED to 35f: Keeps inner corners sharp while slightly softening edges
        pathEffect = CornerPathEffect(35f)
    }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
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
        } catch (e: Exception) {
            null
        }
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

        val customPkg = prefs.getString("PREF_CUSTOM_APP_PKG", "com.google.android.youtube") ?: "com.google.android.youtube"
        var customAppBitmap: Bitmap? = null
        if (actionIds.contains(6)) {
            customAppBitmap = getAppIconBitmap(customPkg)
        }

        slices.clear()
        actionIds.forEachIndexed { index, actionId ->
            val color = Color.parseColor(themeColors[index % themeColors.size])
            val slice = Slice(color, actionId)
            if (actionId == 6 && customAppBitmap != null) {
                slice.customIcon = customAppBitmap
            }
            slices.add(slice)
        }
    }

    fun setOrigin(x: Float, y: Float) {
        startX = x
        startY = y
        activeSlice = -1

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 350
            interpolator = OvershootInterpolator(1.6f)
            addUpdateListener {
                animProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (animProgress <= 0f) return

        bgDimPaint.alpha = (110 * animProgress.coerceIn(0f, 1f)).toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgDimPaint)

        canvas.save()
        canvas.rotate(-75f * (1f - animProgress), startX, startY)

        val baseInnerR = 140f * animProgress
        val baseOuterR = 390f * animProgress // Slightly reduced to make room for the dome
        val totalSpan = 165f
        val gapAngle = 8f 
        val sweepAngle = totalSpan / slices.size.toFloat()

        slices.forEachIndexed { i, slice ->
            val isSelected = (i == activeSlice)
            
            val currentOuterR = if (isSelected) baseOuterR + 45f else baseOuterR
            val currentInnerR = if (isSelected) baseInnerR - 15f else baseInnerR

            val startAngle = 100f + (i.toFloat() * sweepAngle)
            val actualStart = startAngle + (gapAngle / 2f)
            val actualSweep = sweepAngle - gapAngle
            
            val endAngle = actualStart + actualSweep

            val path = Path()
            val innerRect = RectF(startX - currentInnerR, startY - currentInnerR, startX + currentInnerR, startY + currentInnerR)

            // 1. Draw Inner Arc (Sharp corners protected by 35f path effect)
            path.arcTo(innerRect, actualStart, actualSweep)

            // 2. Line connecting to the Outer Right edge
            val outerEndX = startX + currentOuterR * cos(Math.toRadians(endAngle.toDouble())).toFloat()
            val outerEndY = startY + currentOuterR * sin(Math.toRadians(endAngle.toDouble())).toFloat()
            path.lineTo(outerEndX, outerEndY)

            // 3. NEW: Bezier Curve to create a heavily rounded outer "Dome/Petal"
            val midAngle = Math.toRadians((actualStart + actualSweep / 2f).toDouble())
            val bulgeR = currentOuterR + 85f // The mathematical bulge that makes the outer curve completely rounded
            val controlX = startX + bulgeR * cos(midAngle).toFloat()
            val controlY = startY + bulgeR * sin(midAngle).toFloat()

            val outerStartX = startX + currentOuterR * cos(Math.toRadians(actualStart.toDouble())).toFloat()
            val outerStartY = startY + currentOuterR * sin(Math.toRadians(actualStart.toDouble())).toFloat()

            path.quadTo(controlX, controlY, outerStartX, outerStartY)

            // 4. Close the path back to the inner left edge
            path.close()

            fillPaint.color = slice.color
            if (isSelected) {
                fillPaint.alpha = 255
                fillPaint.setShadowLayer(30f, 0f, 0f, slice.color)
            } else {
                fillPaint.alpha = (210 * animProgress.coerceIn(0f, 1f)).toInt()
                fillPaint.clearShadowLayer()
            }
            
            canvas.drawPath(path, fillPaint)

            val centerR = (currentInnerR + currentOuterR) / 2f
            val cx = (startX + centerR * cos(midAngle).toFloat())
            val cy = (startY + centerR * sin(midAngle).toFloat())

            val scale = if (isSelected) 1.25f else 1.1f
            canvas.save()
            canvas.scale(scale, scale, cx, cy)
            
            drawSliceIcon(canvas, slice, cx, cy)
            canvas.restore()
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
            0 -> { // Home
                val path = Path().apply { moveTo(cx - 24f, cy + 2f); lineTo(cx, cy - 20f); lineTo(cx + 24f, cy + 2f); lineTo(cx + 17f, cy + 2f); lineTo(cx + 17f, cy + 22f); lineTo(cx - 17f, cy + 22f); lineTo(cx - 17f, cy + 2f); close() }
                canvas.drawPath(path, iconPaint)
            }
            1 -> { // Screenshot
                canvas.drawRoundRect(RectF(cx - 24f, cy - 18f, cx + 24f, cy + 18f), 8f, 8f, iconPaint)
                canvas.drawCircle(cx, cy, 7f, iconPaint)
            }
            2 -> { // Back
                val path = Path().apply { moveTo(cx + 10f, cy - 20f); lineTo(cx - 10f, cy); lineTo(cx + 10f, cy + 20f) }
                canvas.drawPath(path, iconPaint)
            }
            3 -> { // Volume
                val path = Path().apply { moveTo(cx - 16f, cy - 8f); lineTo(cx - 6f, cy - 8f); lineTo(cx + 10f, cy - 18f); lineTo(cx + 10f, cy + 18f); lineTo(cx - 6f, cy + 8f); lineTo(cx - 16f, cy + 8f); close() }
                canvas.drawPath(path, iconPaint)
                canvas.drawArc(RectF(cx + 8f, cy - 12f, cx + 24f, cy + 12f), -45f, 90f, false, iconPaint)
            }
            4 -> { // Recents
                canvas.drawRoundRect(RectF(cx - 16f, cy - 16f, cx + 8f, cy + 8f), 4f, 4f, iconPaint)
                val path = Path().apply { moveTo(cx - 6f, cy + 16f); lineTo(cx + 16f, cy + 16f); lineTo(cx + 16f, cy - 6f) }
                canvas.drawPath(path, iconPaint)
            }
            5 -> { // Notifications
                val path = Path().apply { moveTo(cx, cy - 16f); arcTo(RectF(cx - 12f, cy - 16f, cx + 12f, cy + 8f), 180f, 180f); lineTo(cx + 18f, cy + 12f); lineTo(cx - 18f, cy + 12f); close() }
                canvas.drawPath(path, iconPaint)
                canvas.drawArc(RectF(cx - 6f, cy + 12f, cx + 6f, cy + 24f), 0f, 180f, false, iconPaint)
            }
            6 -> { // App Opening Fallback
                val appPaint = Paint(iconPaint).apply { style = Paint.Style.FILL }
                canvas.drawRoundRect(RectF(cx - 16f, cy - 16f, cx - 4f, cy - 4f), 4f, 4f, appPaint)
                canvas.drawRoundRect(RectF(cx + 4f, cy - 16f, cx + 16f, cy - 4f), 4f, 4f, appPaint)
                canvas.drawRoundRect(RectF(cx - 16f, cy + 4f, cx - 4f, cy + 16f), 4f, 4f, appPaint)
                canvas.drawRoundRect(RectF(cx + 4f, cy + 4f, cx + 16f, cy + 16f), 4f, 4f, appPaint)
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
                            triggerHaptic()
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
                    triggerHaptic()
                    onActionSelected(slices[activeSlice].id)
                }
                onDismiss()
            }
            MotionEvent.ACTION_CANCEL -> onDismiss()
        }
        return true
    }

    private fun triggerHaptic() {
        try { vibrator?.vibrate(VibrationEffect.createOneShot(18L, VibrationEffect.DEFAULT_AMPLITUDE)) } catch (_: Exception) {}
    }

    data class Slice(val color: Int, val id: Int, var customIcon: Bitmap? = null)
}
