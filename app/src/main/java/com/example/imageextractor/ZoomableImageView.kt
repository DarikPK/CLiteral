package com.example.imageextractor

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max
import kotlin.math.min

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val mMatrix = Matrix()
    private val last = PointF()
    private var mode = NONE

    // Scale Gesture Detector
    private val scaleDetector: ScaleGestureDetector

    // For panning
    private var start = PointF()

    // For matrix calculations
    private val matrixValues = FloatArray(9)
    private var saveScale = 1f
    private var minScale = 1f
    private var maxScale = 4f

    init {
        super.setClickable(true)
        scaleDetector = ScaleGestureDetector(context, ScaleListener())
        imageMatrix = mMatrix
        scaleType = ScaleType.MATRIX
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        val curr = PointF(event.x, event.y)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                last.set(curr)
                start.set(last)
                mode = DRAG
            }
            MotionEvent.ACTION_MOVE -> if (mode == DRAG) {
                val deltaX = curr.x - last.x
                val deltaY = curr.y - last.y
                mMatrix.postTranslate(deltaX, deltaY)
                last.set(curr.x, curr.y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                mode = NONE
                if (saveScale < minScale) {
                    animateToDefaultState()
                }
            }
        }
        imageMatrix = mMatrix
        invalidate()
        return true
    }

    private fun animateToDefaultState() {
        mMatrix.postScale(minScale / saveScale, minScale / saveScale, width / 2f, height / 2f)
        saveScale = minScale
        imageMatrix = mMatrix
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            mode = ZOOM
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            var mScaleFactor = detector.scaleFactor
            val origScale = saveScale
            saveScale *= mScaleFactor
            if (saveScale > maxScale) {
                saveScale = maxScale
                mScaleFactor = maxScale / origScale
            } else if (saveScale < minScale) {
                saveScale = minScale
                mScaleFactor = minScale / origScale
            }
            mMatrix.postScale(mScaleFactor, mScaleFactor, detector.focusX, detector.focusY)
            return true
        }
    }

    companion object {
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }
}
