package com.example.piecontrols

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
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

    private val slices = listOf(
        Slice("Home", Color.parseColor("#00E5FF"), 0),        // Neon Cyan
        Slice("Screenshot", Color.parseColor("#B388FF"), 1),  // Neon Purple
        Slice("Back", Color.parseColor("#69F0AE"), 2),        // Neon Mint
        Slice("Volume", Color.parseColor("#FF8A80"), 3)       // Neon Coral
    )

    private var activeSlice = -1
    private var startX = 0f
    private var startY = 0f
    private var animProgress = 0f

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    // Increased PathEffect for extreme, buttery-smooth squircle corners
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        pathEffect = CornerPathEffect(65f) 
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 38f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(8f, 0f, 4f, Color.parseColor("#99000000"))
    }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val bgDimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
    }

    init {
        // Required for hardware-accelerated glowing shadows and path effects
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setOrigin(x: Float, y: Float) {
        startX = x
        startY = y
        activeSlice = -1

        // Aggressive spring pop animation
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 350 // Slightly longer for the satisfying spring finish
            interpolator = OvershootInterpolator(1.6f) // High tension bounce
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

        // 1. Draw smooth background screen dim (fades in to 45% opacity)
        bgDimPaint.alpha = (110 * animProgress.coerceIn(0f, 1f)).toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgDimPaint)

        // 2. Global Canvas Rotation for the Unfurling Spin Effect
        canvas.save()
        val spinRotation = -75f * (1f - animProgress) // Spins out from -75 degrees
        canvas.rotate(spinRotation, startX, startY)

        val baseInnerR = 130f * animProgress
        val baseOuterR = 400f * animProgress
        val totalSpan = 160f // Total degrees the fan covers
        val gapAngle = 7f    // Sharp 7-degree gap between tiles
        val sweepAngle = totalSpan / slices.size.toFloat()

        slices.forEachIndexed { i, slice ->
            val isSelected = (i == activeSlice)
            
            // Dramatic pop for the active tile
            val currentOuterR = if (isSelected) baseOuterR + 45f else baseOuterR
            val currentInnerR = if (isSelected) baseInnerR - 15f else baseInnerR

            val startAngle = 100f + (i.toFloat() * sweepAngle)
            val actualStart = startAngle + (gapAngle / 2f)
            val actualSweep = sweepAngle - gapAngle

            // Construct geometry
            val path = Path()
            val innerRect = RectF(startX - currentInnerR, startY - currentInnerR, startX + currentInnerR, startY + currentInnerR)
            val outerRect = RectF(startX - currentOuterR, startY - currentOuterR, startX + currentOuterR, startY + currentOuterR)

            path.arcTo(outerRect, actualStart, actualSweep)
            path.arcTo(innerRect, actualStart + actualSweep, -actualSweep)
            path.close()

            // Dynamic colors and glowing shadow on hover
            fillPaint.color = slice.color
            if (isSelected) {
                fillPaint.alpha = 255
                fillPaint.setShadowLayer(30f, 0f, 0f, slice.color) // Neon glow
            } else {
                fillPaint.alpha = (210 * animProgress.coerceIn(0f, 1f)).toInt()
                fillPaint.clearShadowLayer()
            }
            
            canvas.drawPath(path, fillPaint)

            // Center calculations for text/icons
            val midAngle = Math.toRadians((actualStart + actualSweep / 2f).toDouble())
            val centerR = (currentInnerR + currentOuterR) / 2f
            val cx = (startX + centerR * cos(midAngle).toFloat())
            val cy = (startY + centerR * sin(midAngle).toFloat())

            // Animate text/icon scale with the tile
            val scale = if (isSelected) 1.15f else 1f
            canvas.save()
            canvas.scale(scale, scale, cx, cy)
            
            drawSliceIcon(canvas, slice.id, cx, cy - 20f)
            canvas.drawText(slice.title, cx, cy + 46f, textPaint)
            
            canvas.restore()
        }
        
        canvas.restore() // End spinning rotation

        // 3. Draw static thumb anchor over top of everything
        val anchorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#40FFFFFF") // Frosted white grip
        }
        val anchorRect = RectF(startX - 20f, startY - 90f, startX + 15f, startY + 90f)
        canvas.drawRoundRect(anchorRect, 30f, 30f, anchorPaint)
    }

    private fun drawSliceIcon(canvas: Canvas, id: Int, cx: Float, cy: Float) {
        when (id) {
            0 -> { // Home
                val homePath = Path().apply {
                    moveTo(cx - 24f, cy + 2f)
                    lineTo(cx, cy - 20f)
                    lineTo(cx + 24f, cy + 2f)
                    lineTo(cx + 17f, cy + 2f)
                    lineTo(cx + 17f, cy + 22f)
                    lineTo(cx - 17f, cy + 22f)
                    lineTo(cx - 17f, cy + 2f)
                    close()
                }
                canvas.drawPath(homePath, iconPaint)
            }
            1 -> { // Screenshot
                val rect = RectF(cx - 24f, cy - 18f, cx + 24f, cy + 18f)
                canvas.drawRoundRect(rect, 8f, 8f, iconPaint)
                canvas.drawCircle(cx, cy, 7f, iconPaint)
            }
            2 -> { // Back
                val backPath = Path().apply {
                    moveTo(cx + 10f, cy - 20f)
                    lineTo(cx - 10f, cy)
                    lineTo(cx + 10f, cy + 20f)
                }
                canvas.drawPath(backPath, iconPaint)
            }
            3 -> { // Volume
                val speakerPath = Path().apply {
                    moveTo(cx - 16f, cy - 8f)
                    lineTo(cx - 6f, cy - 8f)
                    lineTo(cx + 10f, cy - 18f)
                    lineTo(cx + 10f, cy + 18f)
                    lineTo(cx - 6f, cy + 8f)
                    lineTo(cx - 16f, cy + 8f)
                    close()
                }
                canvas.drawPath(speakerPath, iconPaint)
                canvas.drawArc(RectF(cx + 8f, cy - 12f, cx + 24f, cy + 12f), -45f, 90f, false, iconPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Prevent touch interactions while the pop-in animation is running
        if (animProgress < 0.9f) return true

        val dx = event.rawX - startX
        val dy = event.rawY - startY
        val dist = hypot(dx.toDouble(), dy.toDouble())

        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                if (dist > 90.0) {
                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
                    if (angle < 0.0) angle += 360.0

                    if (angle in 100.0..260.0) {
                        val normalized = angle - 100.0
                        val sliceIndex = (normalized / (160.0 / slices.size.toDouble())).toInt().coerceIn(0, slices.size - 1)
                        if (sliceIndex != activeSlice) {
                            activeSlice = sliceIndex
                            triggerHaptic()
                            invalidate() // Redraws to show hover pop and glow
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
        try {
            vibrator?.vibrate(VibrationEffect.createOneShot(18L, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    data class Slice(val title: String, val color: Int, val id: Int)
}
