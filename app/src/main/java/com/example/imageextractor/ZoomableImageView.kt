package com.example.imageextractor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

@SuppressLint("ClickableViewAccessibility")
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    interface OnMatrixChangedListener {
        fun onMatrixChanged()
    }

    private var onMatrixChangedListener: OnMatrixChangedListener? = null

    fun setOnMatrixChangedListener(listener: OnMatrixChangedListener) {
        this.onMatrixChangedListener = listener
    }

    private val baseMatrix = Matrix()
    private val drawMatrix = Matrix()
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
                        drawMatrix.postTranslate(dx, dy)
                        imageMatrix = drawMatrix
                        onMatrixChangedListener?.onMatrixChanged()
                        startPoint.set(event.x, event.y)
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        }
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        updateBaseMatrix()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateBaseMatrix()
    }

    private fun updateBaseMatrix() {
        if (drawable == null) return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val drawableWidth = drawable.intrinsicWidth.toFloat()
        val drawableHeight = drawable.intrinsicHeight.toFloat()

        baseMatrix.reset()

        val scale = min(viewWidth / drawableWidth, viewHeight / drawableHeight)

        val dx = (viewWidth - drawableWidth * scale) / 2f
        val dy = (viewHeight - drawableHeight * scale) / 2f

        baseMatrix.postScale(scale, scale)
        baseMatrix.postTranslate(dx, dy)

        resetZoom()
    }

    fun zoomIn() {
        if (currentScale * zoomIncrement <= maxScale) {
            currentScale *= zoomIncrement
            drawMatrix.postScale(zoomIncrement, zoomIncrement, width / 2f, height / 2f)
            imageMatrix = drawMatrix
            onMatrixChangedListener?.onMatrixChanged()
        }
    }

    fun resetZoom() {
        drawMatrix.set(baseMatrix)
        currentScale = 1.0f
        imageMatrix = drawMatrix
        onMatrixChangedListener?.onMatrixChanged()
    }

    fun getDrawMatrix(): Matrix {
        return Matrix(drawMatrix)
    }

    fun isZoomed(): Boolean {
        return currentScale > 1.0f
    }
}
