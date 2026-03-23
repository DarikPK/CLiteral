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

    // Coordinate System (Logical: 500x200)
    private val LOGICAL_WIDTH = 500f
    private val LOGICAL_HEIGHT = 200f
    private val baseMatrix = Matrix()
    private val inverseBaseMatrix = Matrix()

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

    data class SignaturePoint(val x: Float, val y: Float, val width: Float)

    private val signaturePaint = Paint().apply {
        color = Color.parseColor("#2557A8")
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var signaturePoints = listOf<List<SignaturePoint>>()
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

        // Setup base scale to fit logical coords into physical view
        baseMatrix.reset()
        baseMatrix.setScale(width / LOGICAL_WIDTH, height / LOGICAL_HEIGHT)
        baseMatrix.invert(inverseBaseMatrix)

        canvas.save()
        canvas.concat(baseMatrix)
        canvas.concat(matrix)

        // Dibuja el trazo del usuario
        canvas.drawPath(drawingPath, drawingPaint)

        // Dibuja la firma generada con grosor variable aleatorio y estrechamiento
        signaturePoints.forEach { contour ->
            if (contour.size > 1) {
                for (i in 0 until contour.size - 1) {
                    val p1 = contour[i]
                    val p2 = contour[i + 1]

                    signaturePaint.strokeWidth = p1.width
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
        // 1. Transform from physical to logical coordinates (500x200)
        val logicalPts = floatArrayOf(event.x, event.y)
        inverseBaseMatrix.mapPoints(logicalPts)

        // 2. Transform based on zoom/pan matrix
        val pts = floatArrayOf(logicalPts[0], logicalPts[1])
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
        // Limit total markers to prevent ANR with massive signatures
        var totalPoints = 0
        newMarkers.forEach { contour ->
            if (totalPoints < 3000) {
                markers.add(contour.toMutableList())
                totalPoints += contour.size
            }
        }
        mode = if (markers.isEmpty()) Mode.DRAW else Mode.EDIT
        history.clear()
        regenerateSignature()
    }

    fun traceBitmap(bitmap: Bitmap, customThreshold: Int? = null) {
        clearCanvas()

        // 1. Reduce resolution for faster tracing (500px width is plenty)
        val traceW = 500
        val traceH = (traceW * (bitmap.height.toFloat() / bitmap.width.toFloat())).toInt()
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, traceW, traceH, true)

        val pixels = IntArray(traceW * traceH)
        scaledBitmap.getPixels(pixels, 0, traceW, 0, 0, traceW, traceH)
        val visited = BooleanArray(traceW * traceH)

        // Dynamic thresholding: find min lum to adapt to light signatures
        var minLum = 255
        for (p in pixels) {
            val lum = (Color.red(p) + Color.green(p) + Color.blue(p)) / 3
            if (lum < minLum) minLum = lum
        }

        // Use custom threshold if provided, else auto
        val threshold = customThreshold ?: Math.min(180, minLum + 50)

        val allTracedContours = mutableListOf<List<PointF>>()
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        // 2. Initial trace to find bounds
        for (y in 0 until traceH) {
            for (x in 0 until traceW) {
                val idx = y * traceW + x
                val lum = (Color.red(pixels[idx]) + Color.green(pixels[idx]) + Color.blue(pixels[idx])) / 3

                if (lum < threshold && !visited[idx]) {
                    val contour = mutableListOf<PointF>()
                    traceContour(x, y, traceW, traceH, pixels, visited, threshold, contour)
                    if (contour.size > 5) {
                        allTracedContours.add(contour)
                        contour.forEach {
                            minX = Math.min(minX, it.x)
                            minY = Math.min(minY, it.y)
                            maxX = Math.max(maxX, it.x)
                            maxY = Math.max(maxY, it.y)
                        }
                    }
                }
            }
        }

        if (allTracedContours.isEmpty()) return

        // 2.5 Filter and Sort contours
        // Sort by point count (proxy for length) and take only significant ones (max 7)
        val sortedContours = allTracedContours
            .sortedByDescending { it.size }
            .take(7)

        // 3. Normalize and scale to fit our 500x200 canvas
        val contentW = maxX - minX
        val contentH = maxY - minY

        val margin = 10f
        val targetW = LOGICAL_WIDTH - 2 * margin
        val targetH = LOGICAL_HEIGHT - 2 * margin

        val scale = Math.min(targetW / contentW, targetH / contentH)
        val offsetX = margin + (targetW - contentW * scale) / 2f - minX * scale
        val offsetY = margin + (targetH - contentH * scale) / 2f - minY * scale

        sortedContours.forEach { contour ->
            val path = Path()
            var first = true
            contour.forEach { p ->
                val tx = p.x * scale + offsetX
                val ty = p.y * scale + offsetY
                if (first) {
                    path.moveTo(tx, ty)
                    first = false
                } else {
                    path.lineTo(tx, ty)
                }
            }

            val oldPath = Path(drawingPath)
            drawingPath.set(path)
            autoPlaceMarkers(append = true)
            drawingPath.set(oldPath)
        }

        mode = Mode.EDIT
        isFirstGeneration = true
        regenerateSignature()
    }

    private fun traceContour(startX: Int, startY: Int, w: Int, h: Int, pixels: IntArray, visited: BooleanArray, threshold: Int, contour: MutableList<PointF>) {
        var cx = startX
        var cy = startY
        visited[cy * w + cx] = true
        contour.add(PointF(cx.toFloat(), cy.toFloat()))

        var foundNext = true
        while (foundNext) {
            foundNext = false
            // Look in a wider range (radius 3) for next stroke pixel to jump gaps
            outer@for (r in 1..3) {
                for (dy in -r..r) {
                    for (dx in -r..r) {
                        if (Math.abs(dx) < r && Math.abs(dy) < r) continue // Skip inner already checked
                        val nx = cx + dx
                        val ny = cy + dy
                        if (nx in 0 until w && ny in 0 until h) {
                            val nIdx = ny * w + nx
                            val lum = (Color.red(pixels[nIdx]) + Color.green(pixels[nIdx]) + Color.blue(pixels[nIdx])) / 3
                            if (lum < threshold && !visited[nIdx]) {
                                // Mark all intermediate pixels as visited to avoid double tracing
                                for (iy in Math.min(cy, ny)..Math.max(cy, ny)) {
                                    for (ix in Math.min(cx, nx)..Math.max(cx, nx)) {
                                        visited[iy * w + ix] = true
                                    }
                                }
                                cx = nx
                                cy = ny
                                if (distSq(contour.last(), PointF(cx.toFloat(), cy.toFloat())) > 25f) {
                                    contour.add(PointF(cx.toFloat(), cy.toFloat()))
                                }
                                foundNext = true
                                break@outer
                            }
                        }
                    }
                }
            }
            if (contour.size > 800) break
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
            val minTX = LOGICAL_WIDTH * (1f - scaleFactor)
            val maxTY = 0f
            val minTY = LOGICAL_HEIGHT * (1f - scaleFactor)

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
                val step = 20f // Increased step to 20px for fewer markers
                var lastTanX = tan[0]
                var lastTanY = tan[1]

                var d = step
                while (d < contourLength - step) {
                    pathMeasure.getPosTan(d, pos, tan)
                    val currentTanX = tan[0]
                    val currentTanY = tan[1]

                    // Dot product to find angle change
                    val dot = lastTanX * currentTanX + lastTanY * currentTanY
                    if (dot < 0.90f) { // Slightly less sensitive (approx > 25 degrees)
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

        val newSignaturePoints = mutableListOf<List<SignaturePoint>>()

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

                val interpolatedPoints = mutableListOf<SignaturePoint>()
                val segments = randomPoints.size - 1
                val pointsPerSegment = 8

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

                        // --- Grosor Aleatorio con Tapering ---
                        // 1. Calcular progreso total del trazo para el efecto de inicio/fin delgado
                        val totalEstimatedPoints = segments * pointsPerSegment
                        val currentPointIdx = i * pointsPerSegment + j
                        val progress = currentPointIdx.toFloat() / totalEstimatedPoints.toFloat()

                        // Tapering factor (0 at ends, 1 in middle)
                        val taper = Math.min(progress * 5, (1 - progress) * 5).coerceIn(0f, 1f)

                        // Base width between 2 and 7, plus a stable random jitter
                        // Seed random with point coordinates to keep jitter consistent between redraws
                        val pointRandom = Random((tx * 1000 + ty).toLong())
                        val jitter = pointRandom.nextFloat() * 4f

                        val strokeWidth = (2f + (5f + jitter) * taper)

                        interpolatedPoints.add(SignaturePoint(tx, ty, strokeWidth))
                    }
                }
                newSignaturePoints.add(interpolatedPoints)
            }
        }
        signaturePoints = newSignaturePoints
    }
}
