package com.example.imageextractor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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

    fun setMarkerListener(listener: () -> Unit) {
        markerListener = listener
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Dibuja un fondo para que el área del lienzo sea visible
        canvas.drawColor(Color.LTGRAY)

        // Dibuja la firma con grosor variable
        if (signaturePoints.size > 1) {
            for (i in 0 until signaturePoints.size - 1) {
                val p1 = signaturePoints[i]
                val p2 = signaturePoints[i + 1]

                // Calcula el progreso a lo largo de la curva (0.0 a 1.0)
                val progress = i.toFloat() / (signaturePoints.size - 2).toFloat()

                // Crea un efecto de "tapering" (estrechamiento) en los extremos
                val taper = Math.min(progress, 1 - progress) * 2
                val strokeWidth = (2 + taper * 8).toFloat() // Varía de 2 a 10
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
        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x
            val y = event.y
            markers.add(PointF(x, y))
            markerListener?.invoke()
            regenerateSignature()
            return true
        }
        return super.onTouchEvent(event)
    }

    fun getMarkers(): List<PointF> {
        return markers.toList()
    }

    fun setMarkers(newMarkers: List<PointF>) {
        markers.clear()
        markers.addAll(newMarkers)
        regenerateSignature()
    }

    fun clearMarkers() {
        markers.clear()
        generateSignaturePath()
        invalidate()
    }

    fun regenerateSignature() {
        generateSignaturePath()
        invalidate()
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
