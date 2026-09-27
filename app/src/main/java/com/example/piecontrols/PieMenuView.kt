package com.example.piecontrols

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class PieMenuView(
    context: Context,
    private val onActionSelected: (Int) -> Unit,
    private val onDismiss: () -> Unit
) : View(context) {

    private val slices = listOf(
        Slice("Home", Color.parseColor("#2A85FF"), 0),
        Slice("Screenshot", Color.parseColor("#FF6C00"), 1),
        Slice("Back", Color.parseColor("#22C55E"), 2),
        Slice("Volume", Color.parseColor("#FFC800"), 3)
    )

    private var activeSlice = -1
    private var startX = 0f
    private var startY = 0f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    fun setOrigin(x: Float, y: Float) {
        startX = x
        startY = y
        activeSlice = -1
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val innerR = 140f
        val outerR = 400f
        val totalSpan = 150f
        val sweepAngle = totalSpan / slices.size.toFloat()

        slices.forEachIndexed { i, slice ->
            val startAngle = 105f + (i.toFloat() * sweepAngle)

            val path = Path()
            val innerRect = RectF(startX - innerR, startY - innerR, startX + innerR, startY + innerR)
            val outerRect = RectF(startX - outerR, startY - outerR, startX + outerR, startY + outerR)

            path.arcTo(outerRect, startAngle + 2f, sweepAngle - 4f)
            path.arcTo(innerRect, startAngle + sweepAngle - 2f, -(sweepAngle - 4f))
            path.close()

            fillPaint.color = if (i == activeSlice) Color.WHITE else slice.color
            fillPaint.alpha = if (i == activeSlice) 240 else 210
            canvas.drawPath(path, fillPaint)

            val midAngle = Math.toRadians((startAngle + sweepAngle / 2f).toDouble())
            val textR = (innerR + outerR) / 2f
            val tx = (startX + textR * cos(midAngle).toFloat())
            val ty = (startY + textR * sin(midAngle).toFloat()) + 14f

            textPaint.color = if (i == activeSlice) Color.BLACK else Color.WHITE
            canvas.drawText(slice.title, tx, ty, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val dx = event.rawX - startX
        val dy = event.rawY - startY
        val dist = hypot(dx.toDouble(), dy.toDouble())

        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                if (dist > 100.0) {
                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
                    if (angle < 0.0) angle += 360.0

                    if (angle in 105.0..255.0) {
                        val normalized = angle - 105.0
                        val sliceIndex = (normalized / (150.0 / slices.size.toDouble())).toInt().coerceIn(0, slices.size - 1)
                        if (sliceIndex != activeSlice) {
                            activeSlice = sliceIndex
                            invalidate()
                        }
                    } else {
                        activeSlice = -1
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (activeSlice != -1) {
                    onActionSelected(slices[activeSlice].id)
                }
                onDismiss()
            }
            MotionEvent.ACTION_CANCEL -> onDismiss()
        }
        return true
    }

    data class Slice(val title: String, val color: Int, val id: Int)
}
