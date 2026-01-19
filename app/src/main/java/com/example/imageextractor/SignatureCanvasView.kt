package com.example.imageextractor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.util.Random

class SignatureCanvasView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val random = Random()

    // Cada lista interna representa un trazo continuo.
    private val markers = mutableListOf<MutableList<PointF>>()
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

    enum class Mode {
        DRAW, EDIT
    }
    var mode = Mode.DRAW
        private set

    private val signaturePaint = Paint().apply {
        color = Color.parseColor("#2557A8")
        style = Paint.Style.STROKE
        strokeWidth = 10f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var signaturePoints = listOf<List<PointF>>()
    private var markerRadius = 10f
    private var randomizationRadius = 20f // Re-add randomization radius
    private var numMarkers = 15

    fun getNumMarkers(): Int = numMarkers
    fun getMarkerRadius(): Float = markerRadius

    // Nuevo: para mostrar la firma con desgaste
    private var previewBitmap: android.graphics.Bitmap? = null
    private var previewBitmapBounds: android.graphics.RectF? = null
    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private var markerListener: (() -> Unit)? = null

    fun setNumMarkers(count: Int) {
        if (count > 1) { // Need at least 2 markers for a line
            this.numMarkers = count
            // Regenerate signature if we are in edit mode to reflect changes
            if (mode == Mode.EDIT) {
                regenerateSignature()
            }
        }
    }

    fun setMarkerRadius(radius: Float) {
        if (radius > 0) {
            this.markerRadius = radius
            invalidate() // Just redraw markers, no need to regenerate signature
        }
    }

    fun setRandomizationRadius(radius: Float) {
        if (radius >= 0) {
            this.randomizationRadius = radius
            regenerateSignature()
        }
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

        if (mode == Mode.DRAW) {
            // Dibuja el trazo del usuario solo en modo dibujo
            canvas.drawPath(drawingPath, drawingPaint)
        }

        // Dibuja la firma generada
        previewBitmap?.let { bmp ->
            previewBitmapBounds?.let { bounds ->
                canvas.drawBitmap(bmp, null, bounds, previewPaint)
            }
        } ?: run {
            // Dibuja la firma generada con grosor variable
            signaturePoints.forEach { contour ->
                if (contour.size > 1) {
                    for (i in 0 until contour.size - 1) {
                        val p1 = contour[i]
                        val p2 = contour[i + 1]
                        val progress = if (contour.size > 1) i.toFloat() / (contour.size - 2).toFloat() else 0f
                        val taper = Math.min(progress, 1 - progress) * 2
                        val strokeWidth = (2 + taper * 8).toFloat()
                        signaturePaint.strokeWidth = strokeWidth
                        canvas.drawLine(p1.x, p1.y, p2.x, p2.y, signaturePaint)
                    }
                }
            }
        }

        // Dibuja cada marcador
        markers.forEach { contour ->
            contour.forEach { marker ->
                canvas.drawCircle(marker.x, marker.y, markerRadius, markerPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        return when (mode) {
            Mode.DRAW -> handleDrawTouchEvent(event, x, y)
            Mode.EDIT -> handleEditTouchEvent(event, x, y)
        }
    }

    private fun handleDrawTouchEvent(event: MotionEvent, x: Float, y: Float): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                isDrawing = true
                // El path ya no se resetea aquí para permitir el dibujo aditivo.
                drawingPath.moveTo(x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDrawing) {
                    drawingPath.lineTo(x, y)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDrawing = false
                parent.requestDisallowInterceptTouchEvent(false)
            }
            // For any other action, we do not handle it and do not redraw.
            else -> return false
        }

        // For DOWN, MOVE, and UP/CANCEL, we redraw the view and consume the event.
        invalidate()
        return true
    }

    private fun handleEditTouchEvent(event: MotionEvent, x: Float, y: Float): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Flatten the list of lists to find the closest marker across all contours
                draggedMarker = markers.flatten().find {
                    val dx = it.x - x
                    val dy = it.y - y
                    dx * dx + dy * dy < touchThreshold * touchThreshold
                }
                if (draggedMarker == null) {
                    // Tapped on empty space in edit mode, clear everything and go back to draw mode
                    switchToDrawMode()
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggedMarker != null) {
                    draggedMarker?.set(x, y)
                    regenerateSignature()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (draggedMarker != null) {
                    draggedMarker = null
                    markerListener?.invoke()
                }
            }
            else -> return false
        }
        invalidate()
        return true
    }

    fun switchToEditMode() {
        autoPlaceMarkers()
        drawingPath.reset()
        regenerateSignature()
        mode = Mode.EDIT
        invalidate()
    }

    private fun switchToDrawMode() {
        clearCanvas(switchMode = true)
    }

    fun getDrawingPath(): Path {
        return drawingPath
    }

    fun getMarkerContours(): List<List<PointF>> {
        return markers.toList()
    }

    fun setMarkerContours(newMarkers: List<List<PointF>>) {
        markers.clear()
        newMarkers.forEach { contour ->
            markers.add(contour.toMutableList())
        }
        mode = if (markers.isEmpty()) Mode.DRAW else Mode.EDIT
        regenerateSignature()
    }

    fun clearCanvas(switchMode: Boolean = false) {
        markers.clear()
        drawingPath.reset()
        previewBitmap?.recycle()
        previewBitmap = null
        previewBitmapBounds = null // Reset the bounds as well
        generateSignaturePath()
        if (switchMode) {
            mode = Mode.DRAW
        }
        invalidate()
    }

    fun setPreviewBitmap(bitmap: android.graphics.Bitmap?) {
        previewBitmap?.recycle()
        previewBitmap = bitmap
        invalidate()
    }


    fun regenerateSignature() {
        generateSignaturePath()
        invalidate()
    }

    private fun autoPlaceMarkers() {
        markers.clear()
        if (drawingPath.isEmpty) return

        val pathMeasure = PathMeasure(drawingPath, false)
        val contourLengths = mutableListOf<Float>()
        var totalLength = 0f

        // Primero, medimos la longitud de cada trazo (contorno)
        do {
            val length = pathMeasure.length
            contourLengths.add(length)
            totalLength += length
        } while (pathMeasure.nextContour())

        if (totalLength == 0f || numMarkers < 2) return

        // Reiniciamos el pathMeasure para empezar desde el primer contorno de nuevo
        pathMeasure.setPath(drawingPath, false)

        // Distribuimos los marcadores proporcionalmente a la longitud de cada trazo
        var markersPlaced = 0
        for (contourLength in contourLengths) {
            val contourMarkers = if (totalLength > 0) {
                // Asigna al menos 2 marcadores a trazos muy pequeños para que sean visibles
                Math.max(2, (contourLength / totalLength * numMarkers).toInt())
            } else {
                0
            }

            if (contourMarkers > 1) {
                val newContour = mutableListOf<PointF>()
                val pos = FloatArray(2)
                val tan = FloatArray(2)
                for (i in 0 until contourMarkers) {
                    val distance = (contourLength / (contourMarkers - 1)) * i
                    pathMeasure.getPosTan(distance, pos, tan)
                    newContour.add(PointF(pos[0], pos[1]))
                }
                markers.add(newContour)
                markersPlaced += contourMarkers
            }
            pathMeasure.nextContour()
        }

        // Asegurarse de que al menos el número mínimo de marcadores se coloquen si algo falla
        if (markers.isEmpty() && totalLength > 0 && numMarkers > 1) {
            pathMeasure.setPath(drawingPath, false)
            val fallbackContour = mutableListOf<PointF>()
            val pos = FloatArray(2)
            val tan = FloatArray(2)
            for (i in 0 until numMarkers) {
                val distance = (totalLength / (numMarkers - 1)) * i
                pathMeasure.getPosTan(distance, pos, tan)
                fallbackContour.add(PointF(pos[0], pos[1]))
            }
            if (fallbackContour.isNotEmpty()) {
                markers.add(fallbackContour)
            }
        }
    }

    fun generateProceduralSignatureBitmap(): android.graphics.Bitmap? {
        val allPoints = markers.flatten()
        if (allPoints.isEmpty()) return null

        regenerateSignature() // Ensure the points are fresh

        // 1. Calculate bounding box of all generated signature points (not markers)
        val bounds = getSignatureBounds() ?: return null

        // Store the bounds for onDraw to use
        previewBitmapBounds = bounds

        val width = bounds.width().toInt()
        val height = bounds.height().toInt()

        if (width <= 0 || height <= 0) return null

        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Translate the canvas so the signature is drawn relative to its top-left corner
        canvas.translate(-bounds.left, -bounds.top)

        // Draw the signature onto the new bitmap
        signaturePoints.forEach { contour ->
            if (contour.size > 1) {
                for (i in 0 until contour.size - 1) {
                    val p1 = contour[i]
                    val p2 = contour[i + 1]
                    val progress = if (contour.size > 1) i.toFloat() / (contour.size - 2).toFloat() else 0f
                    val taper = Math.min(progress, 1 - progress) * 2
                    val strokeWidth = (2 + taper * 8).toFloat()
                    signaturePaint.strokeWidth = strokeWidth
                    canvas.drawLine(p1.x, p1.y, p2.x, p2.y, signaturePaint)
                }
            }
        }
        return bitmap
    }

    private fun getSignatureBounds(): android.graphics.RectF? {
        val allPoints = signaturePoints.flatten()
        if (allPoints.isEmpty()) return null

        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        allPoints.forEach { point ->
            minX = kotlin.math.min(minX, point.x)
            maxX = kotlin.math.max(maxX, point.x)
            minY = kotlin.math.min(minY, point.y)
            maxY = kotlin.math.max(maxY, point.y)
        }

        // Add padding to avoid clipping the stroke
        val maxStrokeWidth = 10f // Corresponds to the max stroke width used in drawing
        val padding = maxStrokeWidth / 2
        minX -= padding
        minY -= padding
        maxX += padding
        maxY += padding

        return android.graphics.RectF(minX, minY, maxX, maxY)
    }


    private fun generateSignaturePath() {
        if (markers.isEmpty()) {
            signaturePoints = emptyList()
            return
        }

        val newSignaturePoints = mutableListOf<List<PointF>>()

        markers.forEach { contour ->
            if (contour.size >= 2) {
                // Scale randomization by marker size. Assume 10f is a "normal" marker size.
                val effectiveRandomization = randomizationRadius * (markerRadius / 10.0f)

                val randomPoints = contour.map { marker ->
                    val angle = random.nextDouble() * 2 * Math.PI
                    val radius = random.nextDouble() * effectiveRandomization
                    val x = marker.x + (radius * Math.cos(angle)).toFloat()
                    val y = marker.y + (radius * Math.sin(angle)).toFloat()
                    PointF(x, y)
                }

                val interpolatedPoints = mutableListOf<PointF>()
                if (randomPoints.size < 2) return@forEach

                // Add the first point
                interpolatedPoints.add(randomPoints[0])

                val pointsPerSegment = 20 // Density of the curve

                for (i in 0 until randomPoints.size - 1) {
                    val p0 = if (i == 0) randomPoints[i] else randomPoints[i - 1]
                    val p1 = randomPoints[i]
                    val p2 = randomPoints[i + 1]
                    val p3 = if (i + 2 < randomPoints.size) randomPoints[i + 2] else p2

                    for (j in 1..pointsPerSegment) {
                        val t = j.toFloat() / pointsPerSegment
                        val tt = t * t
                        val ttt = tt * t

                        val x = 0.5f * ((2 * p1.x) + (-p0.x + p2.x) * t + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * tt + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * ttt)
                        val y = 0.5f * ((2 * p1.y) + (-p0.y + p2.y) * t + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * tt + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * ttt)
                        interpolatedPoints.add(PointF(x, y))
                    }
                }
                newSignaturePoints.add(interpolatedPoints)
            }
        }
        signaturePoints = newSignaturePoints
    }
}
