package com.example.imageextractor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max
import kotlin.math.min

@SuppressLint("ClickableViewAccessibility")
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var matrixListener: OnMatrixChangeListener? = null
    private val currentMatrix = Matrix()
    private val lastTouchPoint = PointF()
    private var mode = NONE

    // Scales
    private var minScale = 1f
    private var maxScale = 5f
    private var currentScaleValues = FloatArray(9)

    // Scale Gesture Detector
    private val scaleDetector: ScaleGestureDetector

    companion object {
        const val NONE = 0
        const val DRAG = 1
        const val ZOOM = 2
    }

    interface OnMatrixChangeListener {
        fun onMatrixChanged(matrix: Matrix)
    }

    fun setOnMatrixChangeListener(listener: OnMatrixChangeListener) {
        matrixListener = listener
    }


    init {
        super.setClickable(true)
        scaleDetector = ScaleGestureDetector(context, ScaleListener())
        imageMatrix = currentMatrix
        scaleType = ScaleType.MATRIX
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        val currentPoint = PointF(event.x, event.y)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchPoint.set(currentPoint)
                mode = DRAG
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == DRAG) {
                    val dx = currentPoint.x - lastTouchPoint.x
                    val dy = currentPoint.y - lastTouchPoint.y
                    currentMatrix.postTranslate(dx, dy)
                    lastTouchPoint.set(currentPoint.x, currentPoint.y)
                }
            }
            MotionEvent.ACTION_UP -> {
                mode = NONE
            }
            MotionEvent.ACTION_POINTER_UP -> {
                mode = NONE
            }
        }
        imageMatrix = currentMatrix
        matrixListener?.onMatrixChanged(currentMatrix)
        invalidate()
        return true
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        if (drawable != null) {
            configureInitialMatrix()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (drawable != null) {
            configureInitialMatrix()
        }
    }

    private fun configureInitialMatrix() {
        val dwidth = drawable.intrinsicWidth.toFloat()
        val dheight = drawable.intrinsicHeight.toFloat()
        val vwidth = width.toFloat()
        val vheight = height.toFloat()

        val scale: Float
        val dx: Float
        val dy: Float

        if (dwidth * vheight > vwidth * dheight) {
            scale = vheight / dheight
            dx = (vwidth - dwidth * scale) * 0.5f
            dy = 0f
        } else {
            scale = vwidth / dwidth
            dx = 0f
            dy = (vheight - dheight * scale) * 0.5f
        }

        currentMatrix.setScale(scale, scale)
        currentMatrix.postTranslate(dx, dy)
        minScale = scale
        imageMatrix = currentMatrix
        matrixListener?.onMatrixChanged(currentMatrix)
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            mode = ZOOM
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            var mScaleFactor = detector.scaleFactor
            val origScale = getScale()
            val newScale = origScale * mScaleFactor

            if (newScale > maxScale) {
                mScaleFactor = maxScale / origScale
            } else if (newScale < minScale) {
                mScaleFactor = minScale / origScale
            }

            if (drawable != null) {
                currentMatrix.postScale(mScaleFactor, mScaleFactor, detector.focusX, detector.focusY)
            }
            return true
        }
    }

    private fun getScale(): Float {
        currentMatrix.getValues(currentScaleValues)
        return currentScaleValues[Matrix.MSCALE_X]
    }
}
