package com.example.imageextractor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
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

    // Zoom & Pan properties
    private var scaleFactor = 1.0f
    private var translateX = 0f
    private var translateY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private val matrix = Matrix()
    private val inverseMatrix = Matrix()

    // History for Undo
    private val history = mutableListOf<List<List<PointF>>>()

    var isDeleteMode = false
    private var isFirstGeneration = true

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
    private var numMarkers = 15

    private var markerListener: (() -> Unit)? = null

    fun setNumMarkers(count: Int) {
        if (count >= 0) {
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
            // Since marker size now controls randomness, we need to regenerate the signature
            // y redraw everything.
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

        canvas.save()
        canvas.concat(matrix)

        // Dibuja el trazo del usuario
        canvas.drawPath(drawingPath, drawingPaint)

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

        // Dibuja cada marcador
        markers.forEach { contour ->
            contour.forEach { marker ->
                canvas.drawCircle(marker.x, marker.y, markerRadius, markerPaint)
            }
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Transform touch coordinates based on the current matrix
        val pts = floatArrayOf(event.x, event.y)
        matrix.invert(inverseMatrix)
        inverseMatrix.mapPoints(pts)
        val transformedX = pts[0]
        val transformedY = pts[1]

        return when (mode) {
            Mode.DRAW -> handleDrawTouchEvent(event, transformedX, transformedY)
            Mode.EDIT -> handleEditTouchEvent(event, transformedX, transformedY)
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
                parent.requestDisallowInterceptTouchEvent(true)
                lastTouchX = event.x
                lastTouchY = event.y
                if (isDeleteMode) {
                    deletePointAt(x, y)
                    return true
                }
                // Flatten the list of lists to find the closest marker across all contours
                draggedMarker = markers.flatten().find {
                    val dx = it.x - x
                    val dy = it.y - y
                    dx * dx + dy * dy < (touchThreshold / scaleFactor) * (touchThreshold / scaleFactor)
                }
                if (draggedMarker != null) {
                    saveToHistory()
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggedMarker != null) {
                    draggedMarker?.set(x, y)
                    regenerateSignature()
                } else if (scaleFactor > 1.0f) {
                    // Panning mode: move the canvas
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    translateX += dx
                    translateY += dy
                    lastTouchX = event.x
                    lastTouchY = event.y
                    updateMatrix()
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
        autoPlaceMarkers(append = false)
        drawingPath.reset()
        isFirstGeneration = true
        regenerateSignature()
        mode = Mode.EDIT
        history.clear()
        invalidate()
    }

    private fun switchToDrawMode() {
        clearCanvas(switchMode = true)
        scaleFactor = 1.0f
        translateX = 0f
        translateY = 0f
        updateMatrix()
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
        history.clear()
        regenerateSignature()
    }

    fun traceBitmap(bitmap: Bitmap) {
        clearCanvas()

        // 1. Resize bitmap to canvas coordinates (500x200) for consistent tracing
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 500, 200, true)

        // 2. Scan pixels to find "dark" points (strokes)
        val width = scaledBitmap.width
        val height = scaledBitmap.height
        val pixels = IntArray(width * height)
        scaledBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val visited = BooleanArray(width * height)
        val threshold = 128 // Lum threshold for stroke

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val color = pixels[idx]
                val lum = (Color.red(color) + Color.green(color) + Color.blue(color)) / 3

                if (lum < threshold && !visited[idx]) {
                    // Start tracing a new contour from this point
                    val contour = mutableListOf<PointF>()
                    traceContour(x, y, width, height, pixels, visited, threshold, contour)
                    if (contour.size > 5) {
                        // Apply Intelligent Marking to this traced contour
                        val path = Path()
                        path.moveTo(contour[0].x, contour[0].y)
                        for (i in 1 until contour.size) path.lineTo(contour[i].x, contour[i].y)

                        // Temporarily set drawingPath to this trace to use autoPlaceMarkers
                        val oldPath = Path(drawingPath)
                        drawingPath.set(path)
                        autoPlaceMarkers(append = true)
                        drawingPath.set(oldPath)
                    }
                }
            }
        }

        mode = Mode.EDIT
        isFirstGeneration = true
        regenerateSignature()
    }

    private fun traceContour(startX: Int, startY: Int, w: Int, h: Int, pixels: IntArray, visited: BooleanArray, threshold: Int, contour: MutableList<PointF>) {
        val queue = mutableListOf<Pair<Int, Int>>()
        queue.add(startX to startY)
        visited[startY * w + startX] = true

        while(queue.isNotEmpty()){
            val (x, y) = queue.removeAt(0)
            contour.add(PointF(x.toFloat(), y.toFloat()))

            // Look in 8 directions for next stroke pixel
            for(dy in -1..1){
                for(dx in -1..1){
                    if(dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if(nx in 0 until w && ny in 0 until h){
                        val nIdx = ny * w + nx
                        val nColor = pixels[nIdx]
                        val nLum = (Color.red(nColor) + Color.green(nColor) + Color.blue(nColor)) / 3
                        if(nLum < threshold && !visited[nIdx]){
                            visited[nIdx] = true
                            queue.add(nx to ny)
                            // Greedy trace: only take first neighbor to form a line, not a blob
                            // Actually, for better tracing, let's just take the first one found
                            break
                        }
                    }
                }
            }
            // Limit contour size to avoid infinite loops or massive blobs
            if (contour.size > 1000) break
        }
    }

    fun clearCanvas(switchMode: Boolean = false) {
        markers.clear()
        drawingPath.reset()
        isFirstGeneration = true
        generateSignaturePath()
        if (switchMode) {
            mode = Mode.DRAW
        }
        scaleFactor = 1.0f
        translateX = 0f
        translateY = 0f
        updateMatrix()
        invalidate()
    }

    fun regenerateSignature(manualRefresh: Boolean = false) {
        if (manualRefresh) {
            isFirstGeneration = false
        }
        generateSignaturePath()
        invalidate()
    }

    fun zoomIn() {
        scaleFactor = 2.0f
        updateMatrix()
    }

    fun zoomNormal() {
        scaleFactor = 1.0f
        translateX = 0f
        translateY = 0f
        updateMatrix()
    }

    private fun updateMatrix() {
        // Clamp translations to prevent sliding out of view
        if (scaleFactor > 1f) {
            val maxTX = 0f
            val minTX = width * (1f - scaleFactor)
            val maxTY = 0f
            val minTY = height * (1f - scaleFactor)

            translateX = Math.max(minTX, Math.min(maxTX, translateX))
            translateY = Math.max(minTY, Math.min(maxTY, translateY))
        } else {
            translateX = 0f
            translateY = 0f
        }

        matrix.reset()
        matrix.setScale(scaleFactor, scaleFactor)
        matrix.postTranslate(translateX, translateY)
        invalidate()
    }

    private fun saveToHistory() {
        // Deep copy markers
        val snapshot = markers.map { contour ->
            contour.map { PointF(it.x, it.y) }
        }
        history.add(snapshot)
        if (history.size > 20) history.removeAt(0)
    }

    fun undo() {
        if (history.isNotEmpty()) {
            val lastState = history.removeAt(history.size - 1)
            markers.clear()
            lastState.forEach { contour ->
                markers.add(contour.toMutableList())
            }
            regenerateSignature()
            markerListener?.invoke()
        }
    }

    private fun deletePointAt(x: Float, y: Float) {
        var pointToRemove: PointF? = null
        var contourOfPoint: MutableList<PointF>? = null

        for (contour in markers) {
            pointToRemove = contour.find {
                val dx = it.x - x
                val dy = it.y - y
                dx * dx + dy * dy < (touchThreshold / scaleFactor) * (touchThreshold / scaleFactor)
            }
            if (pointToRemove != null) {
                contourOfPoint = contour
                break
            }
        }

        if (pointToRemove != null && contourOfPoint != null) {
            saveToHistory()
            contourOfPoint.remove(pointToRemove)
            // If contour is now empty or has 1 point, it might be better to remove it if it's no longer a line,
            // but for now we keep it and let the signature generation handle it.
            if (contourOfPoint.size < 2) {
                markers.remove(contourOfPoint)
            }
            regenerateSignature()
            markerListener?.invoke()
        }
    }


    private fun autoPlaceMarkers(append: Boolean = false) {
        if (!append) markers.clear()
        if (drawingPath.isEmpty) return

        val pathMeasure = PathMeasure(drawingPath, false)

        if (numMarkers == 0) {
            // Intelligent Marker Motor: Place markers only at key points
            do {
                val contourLength = pathMeasure.length
                if (contourLength < 5f) continue

                val newContour = mutableListOf<PointF>()
                val pos = FloatArray(2)
                val tan = FloatArray(2)

                // 1. Start point
                pathMeasure.getPosTan(0f, pos, tan)
                newContour.add(PointF(pos[0], pos[1]))

                // 2. Sample points to detect sharp turns (curvature-based markers)
                val step = 10f // Sample every 10px
                var lastTanX = tan[0]
                var lastTanY = tan[1]

                var d = step
                while (d < contourLength - step) {
                    pathMeasure.getPosTan(d, pos, tan)
                    val currentTanX = tan[0]
                    val currentTanY = tan[1]

                    // Dot product to find angle change
                    val dot = lastTanX * currentTanX + lastTanY * currentTanY
                    if (dot < 0.95f) { // Sharp turn detected (approx > 18 degrees)
                        newContour.add(PointF(pos[0], pos[1]))
                        lastTanX = currentTanX
                        lastTanY = currentTanY
                    }
                    d += step
                }

                // 3. End point
                pathMeasure.getPosTan(contourLength, pos, tan)
                val endPoint = PointF(pos[0], pos[1])
                if (newContour.last().let { distSq(it, endPoint) > 100f }) {
                    newContour.add(endPoint)
                }

                if (newContour.size >= 2) {
                    markers.add(newContour)
                }
            } while (pathMeasure.nextContour())
        } else {
            // Proportional Distribution Motor
            val contourLengths = mutableListOf<Float>()
            var totalLength = 0f

            do {
                val length = pathMeasure.length
                contourLengths.add(length)
                totalLength += length
            } while (pathMeasure.nextContour())

            if (totalLength == 0f || numMarkers < 2) return

            pathMeasure.setPath(drawingPath, false)

            for (contourLength in contourLengths) {
                val contourMarkers = if (totalLength > 0) {
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
                }
                pathMeasure.nextContour()
            }
        }
    }

    private fun distSq(p1: PointF, p2: PointF): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return dx * dx + dy * dy
    }

    private fun generateSignaturePath() {
        if (markers.isEmpty()) {
            signaturePoints = emptyList()
            return
        }

        val newSignaturePoints = mutableListOf<List<PointF>>()

        markers.forEach { contour ->
            if (contour.size >= 2) {
                val currentRadius = if (isFirstGeneration) 0f else markerRadius
                val randomPoints = contour.map { marker ->
                    val angle = random.nextDouble() * 2 * Math.PI
                    val radius = random.nextDouble() * currentRadius
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
                newSignaturePoints.add(interpolatedPoints)
            }
        }
        signaturePoints = newSignaturePoints
    }
}
