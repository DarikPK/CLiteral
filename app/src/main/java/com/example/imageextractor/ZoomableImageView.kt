package com.example.imageextractor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs

@SuppressLint("ClickableViewAccessibility")
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val imageMatrix = Matrix()
    private val startPoint = PointF()

    private var currentScale = 1.0f
    private val zoomIncrement = 1.2f
    private val maxScale = 5.0f

    init {
        scaleType = ScaleType.MATRIX
        setOnTouchListener { _, event ->
            if (currentScale > 1.0f) { // Solo permitir paneo si hay zoom
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startPoint.set(event.x, event.y)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - startPoint.x
                        val dy = event.y - startPoint.y
                        imageMatrix.postTranslate(dx, dy)
                        setImageMatrix(imageMatrix)
                        startPoint.set(event.x, event.y)
                        true
                    }
                    else -> false
                }
            } else {
                false // No consumir el evento si no hay zoom
            }
        }
    }

    fun zoomIn() {
        if (currentScale * zoomIncrement <= maxScale) {
            currentScale *= zoomIncrement
            imageMatrix.postScale(zoomIncrement, zoomIncrement, width / 2f, height / 2f)
            setImageMatrix(imageMatrix)
        }
    }

    fun resetZoom() {
        imageMatrix.postScale(1.0f / currentScale, 1.0f / currentScale, width / 2f, height / 2f)
        currentScale = 1.0f
        setImageMatrix(imageMatrix)
    }
}
