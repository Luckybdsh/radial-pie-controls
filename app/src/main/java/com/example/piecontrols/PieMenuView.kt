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
        Slice("Home", Color.parseColor("#2979FF"), 0),        // Vibrant Blue
        Slice("Screenshot", Color.parseColor("#FF6D00"), 1),  // Vibrant Orange
        Slice("Back", Color.parseColor("#22C55E"), 2),        // Vibrant Green
        Slice("Volume", Color.parseColor("#FFB300"), 3)       // Vibrant Yellow
    )

    private var activeSlice = -1
    private var startX = 0f
    private var startY = 0f
    private var animProgress = 0f

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        pathEffect = CornerPathEffect(38f) // Creates organic rounded petal corners
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(6f, 0f, 2f, Color.parseColor("#66000000"))
    }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val anchorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B3222226")
        style = Paint.Style.FILL
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // Required for shadows and path effects
    }

    fun setOrigin(x: Float, y: Float) {
        startX = x
        startY = y
        activeSlice = -1

        // Smooth spring fan-out animation
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 240
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener {
                animProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (animProgress == 0f) return

        // 1. Draw thumb anchor pill at screen edge
        val anchorRect = RectF(startX - 50f, startY - 110f, startX + 30f, startY + 110f)
        canvas.drawRoundRect(anchorRect, 40f, 40f, anchorPaint)

        // Draw "A" letter indicator on thumb anchor
        val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0E0E0")
            textSize = 34f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("A", startX - 16f, startY + 12f, letterPaint)

        // 2. Draw 4 rounded petal slices
        val innerR = 120f * animProgress
        val outerR = 390f * animProgress
        val totalSpan = 150f
        val sweepAngle = totalSpan / slices.size.toFloat()

        slices.forEachIndexed { i, slice ->
            val isSelected = (i == activeSlice)
            val currentOuterR = if (isSelected) outerR + 25f else outerR

            val startAngle = 105f + (i.toFloat() * sweepAngle)

            // Construct rounded petal geometry
            val path = Path()
            val innerRect = RectF(startX - innerR, startY - innerR, startX + innerR, startY + innerR)
            val outerRect = RectF(startX - currentOuterR, startY - currentOuterR, startX + currentOuterR, startY + currentOuterR)

            path.arcTo(outerRect, startAngle + 3f, sweepAngle - 6f)
            path.arcTo(innerRect, startAngle + sweepAngle - 3f, -(sweepAngle - 6f))
            path.close()

            fillPaint.color = slice.color
            fillPaint.alpha = if (isSelected) 255 else 225
            canvas.drawPath(path, fillPaint)

            // Calculate center point for icon and label
            val midAngle = Math.toRadians((startAngle + sweepAngle / 2f).toDouble())
            val centerR = (innerR + currentOuterR) / 2f
            val cx = (startX + centerR * cos(midAngle).toFloat())
            val cy = (startY + centerR * sin(midAngle).toFloat())

            // Draw Icon above text
            drawSliceIcon(canvas, slice.id, cx, cy - 20f)

            // Draw Text label
            textPaint.color = Color.WHITE
            canvas.drawText(slice.title, cx, cy + 42f, textPaint)
        }
    }

    private fun drawSliceIcon(canvas: Canvas, id: Int, cx: Float, cy: Float) {
        when (id) {
            0 -> { // Home icon
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
            1 -> { // Screenshot / Viewfinder icon
                val rect = RectF(cx - 22f, cy - 18f, cx + 22f, cy + 18f)
                canvas.drawRoundRect(rect, 8f, 8f, iconPaint)
                canvas.drawCircle(cx, cy, 7f, iconPaint)
            }
            2 -> { // Back chevron icon (<)
                val backPath = Path().apply {
                    moveTo(cx + 10f, cy - 20f)
                    lineTo(cx - 10f, cy)
                    lineTo(cx + 10f, cy + 20f)
                }
                canvas.drawPath(backPath, iconPaint)
            }
            3 -> { // Volume speaker icon
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
                // Sound wave arc
                canvas.drawArc(RectF(cx + 8f, cy - 12f, cx + 24f, cy + 12f), -45f, 90f, false, iconPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val dx = event.rawX - startX
        val dy = event.rawY - startY
        val dist = hypot(dx.toDouble(), dy.toDouble())

        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                if (dist > 90.0) {
                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
                    if (angle < 0.0) angle += 360.0

                    if (angle in 105.0..255.0) {
                        val normalized = angle - 105.0
                        val sliceIndex = (normalized / (150.0 / slices.size.toDouble())).toInt().coerceIn(0, slices.size - 1)
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
        try {
            vibrator?.vibrate(VibrationEffect.createOneShot(18L, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    data class Slice(val title: String, val color: Int, val id: Int)
}
