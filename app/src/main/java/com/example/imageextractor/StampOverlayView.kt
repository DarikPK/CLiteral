package com.example.imageextractor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

class StampOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var stampBitmap: Bitmap? = null
    private var posX = 0f
    private var posY = 0f
    private var scale = 1.0f
    private var rotation = 0f

    private var onStampUpdateListener: ((Float, Float, Float) -> Unit)? = null

    private val stampMatrix = Matrix()
    private val imageMatrix = Matrix()
    private val totalMatrix = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#42A5F5") // Light Blue
        style = Paint.Style.FILL
    }
    private val handleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val handleRadius = 30f

    private enum class Mode { NONE, DRAG, ROTATE }
    private var mode = Mode.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var pivotX = 0f
    private var pivotY = 0f

    fun setStamp(bitmap: Bitmap, x: Float, y: Float, scale: Float, rotation: Float, imageMatrix: Matrix) {
        this.stampBitmap = bitmap
        this.posX = x
        this.posY = y
        this.scale = scale
        this.rotation = rotation
        this.imageMatrix.set(imageMatrix)
        invalidate()
    }

    fun setOnStampUpdateListener(listener: (x: Float, y: Float, rotation: Float) -> Unit) {
        this.onStampUpdateListener = listener
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        stampBitmap?.let {
            stampMatrix.reset()
            stampMatrix.postScale(scale, scale, 0f, 0f)
            stampMatrix.postRotate(rotation, it.width * scale / 2, it.height * scale / 2)
            stampMatrix.postTranslate(posX, posY)

            totalMatrix.set(stampMatrix)
            totalMatrix.postConcat(imageMatrix)

            canvas.drawBitmap(it, totalMatrix, paint)

            val points = floatArrayOf(it.width.toFloat(), it.height.toFloat())
            totalMatrix.mapPoints(points)
            val handleX = points[0]
            val handleY = points[1]

            canvas.drawCircle(handleX, handleY, handleRadius, handlePaint)
            canvas.drawCircle(handleX, handleY, handleRadius, handleBorderPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (stampBitmap == null || visibility != VISIBLE) return false

        val transformedPoint = getTransformedPoint(event.x, event.y)
        val x = transformedPoint.x
        val y = transformedPoint.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isInHandle(x, y)) {
                    mode = Mode.ROTATE
                    pivotX = posX + stampBitmap!!.width * scale / 2
                    pivotY = posY + stampBitmap!!.height * scale / 2
                    lastTouchX = x
                    lastTouchY = y
                    return true
                } else if (isInStamp(x, y)) {
                    mode = Mode.DRAG
                    lastTouchX = x
                    lastTouchY = y
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == Mode.NONE) return false

                val dx = x - lastTouchX
                val dy = y - lastTouchY

                if (mode == Mode.DRAG) {
                    posX += dx
                    posY += dy
                } else if (mode == Mode.ROTATE) {
                    val angle = atan2(y - pivotY, x - pivotX) * (180 / Math.PI).toFloat()
                    val lastAngle = atan2(lastTouchY - pivotY, lastTouchX - pivotX) * (180 / Math.PI).toFloat()
                    rotation += angle - lastAngle
                }

                invalidate()
                lastTouchX = x
                lastTouchY = y
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (mode != Mode.NONE) {
                    onStampUpdateListener?.invoke(posX, posY, rotation)
                    mode = Mode.NONE
                    return true
                }
                return false
            }
        }
        return false
    }

    private fun getTransformedPoint(x: Float, y: Float): PointF {
        val invertedMatrix = Matrix()
        imageMatrix.invert(invertedMatrix)
        val points = floatArrayOf(x, y)
        invertedMatrix.mapPoints(points)
        return PointF(points[0], points[1])
    }

    private fun getHandlePosition(): PointF {
        stampBitmap?.let {
            val localMatrix = Matrix()
            localMatrix.postScale(scale, scale, 0f, 0f)
            localMatrix.postRotate(rotation, it.width * scale / 2, it.height * scale / 2)
            localMatrix.postTranslate(posX, posY)

            val points = floatArrayOf(it.width.toFloat(), it.height.toFloat())
            localMatrix.mapPoints(points)
            return PointF(points[0], points[1])
        }
        return PointF(0f, 0f)
    }

    private fun isInHandle(x: Float, y: Float): Boolean {
        val handlePos = getHandlePosition()
        val distance = sqrt((x - handlePos.x).pow(2) + (y - handlePos.y).pow(2))
        return distance <= handleRadius * 1.5
    }

    private fun isInStamp(x: Float, y: Float): Boolean {
        stampBitmap?.let {
            val localMatrix = Matrix()
            localMatrix.postScale(scale, scale, 0f, 0f)
            localMatrix.postRotate(rotation, it.width * scale / 2, it.height * scale / 2)
            localMatrix.postTranslate(posX, posY)

            val invertedMatrix = Matrix()
            localMatrix.invert(invertedMatrix)

            val points = floatArrayOf(x, y)
            invertedMatrix.mapPoints(points)

            val rect = RectF(0f, 0f, it.width.toFloat(), it.height.toFloat())
            return rect.contains(points[0], points[1])
        }
        return false
    }
}
