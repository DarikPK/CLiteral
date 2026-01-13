package com.example.imageextractor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PathMeasure
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class SignatureCanvasView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val markers = mutableListOf<PointF>()
    private val markerPaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val drawingPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private var drawingPath = Path()
    private var isDrawing = false

    private val signaturePaint = Paint().apply {
        color = Color.parseColor("#2557A8")
        style = Paint.Style.STROKE
        strokeWidth = 10f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var signaturePoints = listOf<PointF>()
    private val randomizationRadius = 20f

    private val markerRadius = 10f
    private var markerListener: (() -> Unit)? = null

    fun setRandomizationRadius(radius: Float) {
        this.randomizationRadius = radius
        regenerateSignature()
    }

    private var draggedMarker: PointF? = null
    private val touchThreshold = 30f // How close to a marker to start dragging

    fun setMarkerListener(listener: () -> Unit) {
        markerListener = listener
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Dibuja un fondo para que el área del lienzo sea visible
        canvas.drawColor(Color.LTGRAY)

        // Dibuja el trazo del usuario
        canvas.drawPath(drawingPath, drawingPaint)

        // Dibuja la firma generada con grosor variable
        if (signaturePoints.size > 1) {
            for (i in 0 until signaturePoints.size - 1) {
                val p1 = signaturePoints[i]
                val p2 = signaturePoints[i + 1]
                val progress = i.toFloat() / (signaturePoints.size - 2).toFloat()
                val taper = Math.min(progress, 1 - progress) * 2
                val strokeWidth = (2 + taper * 8).toFloat()
                signaturePaint.strokeWidth = strokeWidth
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, signaturePaint)
            }
        }

        // Dibuja cada marcador
        markers.forEach { marker ->
            canvas.drawCircle(marker.x, marker.y, markerRadius, markerPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Check if we are dragging an existing marker
                draggedMarker = markers.find {
                    val dx = it.x - x
                    val dy = it.y - y
                    dx * dx + dy * dy < touchThreshold * touchThreshold
                }

                if (draggedMarker == null) {
                    // Not dragging, so start a new drawing
                    clearCanvas()
                    isDrawing = true
                    drawingPath.moveTo(x, y)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggedMarker != null) {
                    draggedMarker?.set(x, y)
                    regenerateSignature()
                } else if (isDrawing) {
                    drawingPath.lineTo(x, y)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (draggedMarker != null) {
                    // Finished dragging
                    draggedMarker = null
                    markerListener?.invoke()
                } else if (isDrawing) {
                    // Finished drawing
                    isDrawing = false
                    autoPlaceMarkers()
                    drawingPath.reset()
                    markerListener?.invoke()
                    regenerateSignature()
                }
            }
            else -> return false
        }

        invalidate()
        return true
    }

    fun getMarkers(): List<PointF> {
        return markers.toList()
    }

    fun setMarkers(newMarkers: List<PointF>) {
        markers.clear()
        markers.addAll(newMarkers)
        regenerateSignature()
    }

    fun clearCanvas() {
        markers.clear()
        drawingPath.reset()
        generateSignaturePath()
        invalidate()
    }

    fun regenerateSignature() {
        generateSignaturePath()
        invalidate()
    }

    private fun autoPlaceMarkers() {
        markers.clear()
        val pathMeasure = PathMeasure(drawingPath, false)
        val pathLength = pathMeasure.length
        if (pathLength == 0f) return

        val numMarkers = 15 // Número de marcadores a colocar
        val pos = FloatArray(2)
        val tan = FloatArray(2)

        for (i in 0 until numMarkers) {
            val distance = (pathLength / (numMarkers - 1)) * i
            pathMeasure.getPosTan(distance, pos, tan)
            markers.add(PointF(pos[0], pos[1]))
        }
    }

    private fun generateSignaturePath() {
        if (markers.size < 2) {
            signaturePoints = emptyList()
            return
        }

        val randomPoints = markers.map { marker ->
            val angle = Math.random() * 2 * Math.PI
            val radius = Math.random() * randomizationRadius
            val x = marker.x + (radius * Math.cos(angle)).toFloat()
            val y = marker.y + (radius * Math.sin(angle)).toFloat()
            PointF(x, y)
        }

        val interpolatedPoints = mutableListOf<PointF>()
        val segments = randomPoints.size - 1
        val pointsPerSegment = 20

        for (i in 0 until segments) {
            val p0 = if (i > 0) randomPoints[i - 1] else randomPoints[i]
            val p1 = randomPoints[i]
            val p2 = randomPoints[i + 1]
            val p3 = if (i < randomPoints.size - 2) randomPoints[i + 2] else p2

            for (j in 0..pointsPerSegment) {
                val t = j.toFloat() / pointsPerSegment
                val tt = t * t
                val ttt = tt * t

                val q1 = -ttt + 2 * tt - t
                val q2 = 3 * ttt - 5 * tt + 2
                val q3 = -3 * ttt + 4 * tt + t
                val q4 = ttt - tt

                val tx = 0.5f * (p0.x * q1 + p1.x * q2 + p2.x * q3 + p3.x * q4)
                val ty = 0.5f * (p0.y * q1 + p1.y * q2 + p2.y * q3 + p3.y * q4)
                interpolatedPoints.add(PointF(tx, ty))
            }
        }
        signaturePoints = interpolatedPoints
    }
}
