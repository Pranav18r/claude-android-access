package com.currencyrecognition.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.currencyrecognition.ml.OnnxInferenceHelper

/**
 * OverlayView
 *
 * A transparent view drawn on top of the camera preview.
 * Draws bounding boxes and class labels for detected currency notes.
 *
 * Bounding box coordinates from OnnxInferenceHelper are in [0,1] space.
 * onDraw() scales them to the view's pixel dimensions.
 */
class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var detections: List<OnnxInferenceHelper.Detection> = emptyList()

    private val boxPaint = Paint().apply {
        style       = Paint.Style.STROKE
        color       = Color.parseColor("#00E676")   // bright green
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val bgPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#CC000000")        // semi-transparent black
    }

    private val textPaint = Paint().apply {
        color    = Color.WHITE
        textSize = 52f
        isFakeBoldText = true
        isAntiAlias    = true
    }

    /** Called from the inference thread via runOnUiThread. */
    fun setDetections(newDetections: List<OnnxInferenceHelper.Detection>) {
        detections = newDetections
        invalidate()   // triggers onDraw on the UI thread
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (detections.isEmpty()) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()

        for (det in detections) {
            val left   = det.x1 * viewW
            val top    = det.y1 * viewH
            val right  = det.x2 * viewW
            val bottom = det.y2 * viewH

            // Bounding box
            canvas.drawRect(left, top, right, bottom, boxPaint)

            // Label background + text
            val label       = "${OnnxInferenceHelper.CLASS_NAMES[det.classId]}  ${(det.confidence * 100).toInt()}%"
            val textWidth   = textPaint.measureText(label)
            val textHeight  = textPaint.textSize
            val labelTop    = (top - textHeight - 12f).coerceAtLeast(0f)
            val labelBottom = labelTop + textHeight + 12f
            val labelRight  = (left + textWidth + 16f).coerceAtMost(viewW)

            canvas.drawRoundRect(
                RectF(left, labelTop, labelRight, labelBottom),
                8f, 8f,
                bgPaint
            )
            canvas.drawText(label, left + 8f, labelBottom - 8f, textPaint)
        }
    }
}
