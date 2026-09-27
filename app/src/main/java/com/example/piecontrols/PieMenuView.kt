package com.example.piecontrols

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class PieMenuView(
    context: Context,
    private val onActionSelected: (Int) -> Unit,
    private val onDismiss: () -> Unit
) : View(context) {

    private val slices = listOf(
        Slice("Home", 0xFF2A85FF.toInt(), 0),        // Top: Blue
        Slice("Screenshot", 0xFFFF6C00.toInt(), 1),  // Mid-High: Orange
        Slice("Back", 0xFF22C55E.toInt(), 2),        // Mid-Low: Green
        Slice("Volume", 0xFFFFC800.toInt(), 3)       // Bottom: Yellow
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
        val totalSpan = 150.0 // fanning over 150 degrees backwards from the edge
        val sweepAngle = (totalSpan / slices.size).toFloat()

        slices.forEachIndexed { i, slice ->
            val startAngle = 105f + (i * sweepAngle) // spread across left-facing semicircle

            val path = Path()
            val innerRect = RectF(startX - innerR, startY - innerR, startX + innerR, startY + innerR)
            val outerRect = RectF(startX - outerR, startY - outerR, startX + outerR, startY + outerR)

            path.arcTo(outerRect, startAngle + 2f, sweepAngle - 4f)
            path.arcTo(innerRect, startAngle + sweepAngle - 2f, -(sweepAngle - 4f))
            path.close()

            fillPaint.color = if (i == activeSlice) Color.WHITE else slice.color
            fillPaint.alpha = if (i == activeSlice) 240 else 210
            canvas.drawPath(path, fillPaint)

            // Draw Label in center of slice
            val midAngle = Math.toRadians((startAngle + sweepAngle / 2.0))
            val textR = (innerR + outerR) / 2f
            val tx = (startX + textR * cos(midAngle)).toFloat()
            val ty = (startY + textR * sin(midAngle)).toFloat() + 14f

            textPaint.color = if (i == activeSlice) Color.BLACK else Color.WHITE
            canvas.drawText(slice.title, tx, ty, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val dx = event.rawX - startX
        val dy = event.rawY - startY
        val dist = hypot(dx, dy)

        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                if (dist > 100f) {
                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
                    if (angle < 0) angle += 360.0

                    // Map angle to slice index (105° to 255°)
                    if (angle in 105.0..255.0) {
                        val normalized = angle - 105.0
                        val sliceIndex = (normalized / (150.0 / slices.size)).toInt().coerceIn(0, slices.size - 1)
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
