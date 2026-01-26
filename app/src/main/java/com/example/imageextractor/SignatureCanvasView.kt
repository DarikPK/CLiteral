// Force re-evaluation
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

// Nueva estructura de datos para cada trazo de la firma
data class SignatureContour(
    val points: MutableList<PointF>,
    var color: Int
)

class SignatureCanvasView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val random = Random()

    // La variable principal ahora es una lista de la nueva data class
    private val markers = mutableListOf<SignatureContour>()
    private var selectedContour: SignatureContour? = null

    private val markerPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val markerStrokePaint = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.YELLOW
        strokeWidth = 3f
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
    private var basePathForMarkers = Path()
    var hasBasePath: Boolean = false
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
    private var numMarkers = 15
    private var wearIntensity = 0f
    private var wearSize = 0f
    private var startThickness = 5f
    private var midThickness = 15f
    private var endThickness = 5f

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

    fun setThicknessParameters(start: Float, mid: Float, end: Float) {
        this.startThickness = start
        this.midThickness = mid
        this.endThickness = end
        regenerateSignature()
    }

    fun setWearParameters(intensity: Float, size: Float) {
        this.wearIntensity = intensity
        this.wearSize = size
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
                        val progress = if (contour.size > 1) i.toFloat() / (contour.size - 2).toFloat().coerceAtLeast(1f) else 0f

                        val currentThickness = when {
                            progress < 0.5 -> {
                                val localProgress = progress * 2
                                startThickness + (midThickness - startThickness) * localProgress
                            }
                            else -> {
                                val localProgress = (progress - 0.5f) * 2
                                midThickness + (endThickness - midThickness) * localProgress
                            }
                        }
                        signaturePaint.strokeWidth = currentThickness
                        canvas.drawLine(p1.x, p1.y, p2.x, p2.y, signaturePaint)
                    }
                }
            }
        }

        // Dibuja cada marcador
        markers.forEach { contour ->
            markerPaint.color = contour.color
            contour.points.forEach { marker ->
                canvas.drawCircle(marker.x, marker.y, markerRadius, markerPaint)
                if (contour == selectedContour) {
                    canvas.drawCircle(marker.x, marker.y, markerRadius, markerStrokePaint)
                }
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
                draggedMarker = markers.flatMap { it.points }.find {
                    val dx = it.x - x
                    val dy = it.y - y
                    dx * dx + dy * dy < touchThreshold * touchThreshold
                }

                if (draggedMarker == null) {
                    // Si no se arrastra un marcador, intentamos seleccionar un trazo.
                    selectedContour = markers.minByOrNull { contour ->
                        contour.points.map {
                            val dx = it.x - x
                            val dy = it.y - y
                            dx * dx + dy * dy
                        }.minOrNull() ?: Float.MAX_VALUE
                    }
                    invalidate() // Redibujar para mostrar la selección
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggedMarker != null) {
                    selectedContour = null // Anula la selección si se empieza a arrastrar
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

    // Nueva función pública para cambiar el color del trazo seleccionado
    fun setSelectedContourColor(color: Int) {
        selectedContour?.let {
            it.color = color
            invalidate() // Redibujar para mostrar el nuevo color
            markerListener?.invoke() // Notificar que ha habido un cambio
        }
    }

    fun switchToEditMode() {
        if (drawingPath.isEmpty) return

        basePathForMarkers = Path(drawingPath)
        markers.clear()

        val pathMeasure = PathMeasure(basePathForMarkers, false)
        do {
            val contourPoints = mutableListOf<PointF>()
            val length = pathMeasure.length
            // Aumentar la densidad de puntos para un trazado inicial fiel
            val numPoints = (length / 15f).toInt().coerceAtLeast(20)

            val pos = FloatArray(2)
            for (i in 0 until numPoints) {
                val distance = if (numPoints > 1) (length / (numPoints - 1)) * i else 0f
                pathMeasure.getPosTan(distance, pos, null)
                contourPoints.add(PointF(pos[0], pos[1]))
            }

            if (contourPoints.isNotEmpty()) {
                markers.add(SignatureContour(contourPoints, Color.BLUE))
            }
        } while (pathMeasure.nextContour())

        drawingPath.reset()
        regenerateSignature()
        mode = Mode.EDIT
        invalidate()
        markerListener?.invoke()
    }

    private fun switchToDrawMode() {
        clearCanvas(switchMode = true)
    }

    fun getDrawingPath(): Path {
        return drawingPath
    }

    // Nueva función para obtener los datos como string con color
    fun getContoursAsString(): String {
        return markers.joinToString("|") { contour ->
            val pointsString = contour.points.joinToString(";") { "${it.x},${it.y}" }
            "${contour.color}:$pointsString"
        }
    }

    // Nueva función para establecer los datos desde un string con color
    fun setContoursFromString(pointsString: String?) {
        markers.clear()
        if (pointsString.isNullOrEmpty()) {
            mode = Mode.DRAW
            regenerateSignature()
            return
        }

        val contoursStrings = pointsString.split("|")
        contoursStrings.forEach { contourString ->
            val parts = contourString.split(":", limit = 2)
            val color: Int
            val pointsData: String

            if (parts.size == 2) {
                color = parts[0].toIntOrNull() ?: Color.BLUE
                pointsData = parts[1]
            } else {
                // Para compatibilidad con el formato antiguo sin color
                color = Color.BLUE
                pointsData = parts[0]
            }

            val points = pointsData.split(";").mapNotNull {
                val pointParts = it.split(",")
                if (pointParts.size == 2) PointF(pointParts[0].toFloat(), pointParts[1].toFloat()) else null
            }.toMutableList()

            if (points.isNotEmpty()) {
                markers.add(SignatureContour(points, color))
            }
        }

        mode = if (markers.isEmpty()) Mode.DRAW else Mode.EDIT
        regenerateSignature()
    }

    fun clearCanvas(switchMode: Boolean = false) {
        markers.clear()
        drawingPath.reset()
        basePathForMarkers.reset()
        this.hasBasePath = false // Restablecer el flag
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
        updateSignatureBitmap()
        invalidate()
    }

    private fun updateSignatureBitmap() {
        val cleanBitmap = generateProceduralSignatureBitmap(false) // Pass false to prevent recursion
        if (cleanBitmap != null) {
            val normalizedIntensity = wearIntensity / 100f
            val normalizedSize = wearSize / 100f
            val wornBitmap = applyInkWear(cleanBitmap, normalizedIntensity, normalizedSize, System.currentTimeMillis())
            setPreviewBitmap(wornBitmap)
        } else {
            setPreviewBitmap(null)
        }
    }

    private fun autoPlaceMarkers() {
        if (markers.isEmpty()) return

        val contoursByColor = markers.groupBy { it.color }
        val newMarkers = mutableListOf<SignatureContour>()

        val totalCurrentMarkers = markers.sumOf { it.points.size }
        if (totalCurrentMarkers == 0) return

        val targetTotalMarkers = numMarkers

        // 1. Calcular cuántos marcadores le corresponden a cada grupo de color
        val targetMarkersPerColor = mutableMapOf<Int, Int>()
        var assignedMarkers = 0
        contoursByColor.forEach { (color, contours) ->
            val pointsInGroup = contours.sumOf { it.points.size }
            val proportion = pointsInGroup.toFloat() / totalCurrentMarkers.toFloat()
            val targetForGroup = (proportion * targetTotalMarkers).toInt().coerceAtLeast(2)
            targetMarkersPerColor[color] = targetForGroup
            assignedMarkers += targetForGroup
        }

        // 2. Ajustar la diferencia para que el total sea exacto
        var diff = targetTotalMarkers - assignedMarkers
        while (diff != 0) {
            val colorToAdjust = targetMarkersPerColor.keys.random()
            if (diff > 0) {
                targetMarkersPerColor[colorToAdjust] = targetMarkersPerColor.getValue(colorToAdjust) + 1
                diff--
            } else {
                if (targetMarkersPerColor.getValue(colorToAdjust) > 2) {
                    targetMarkersPerColor[colorToAdjust] = targetMarkersPerColor.getValue(colorToAdjust) - 1
                    diff++
                }
            }
        }

        // 3. Para cada grupo, unir todos sus trazos y re-muestrear
        contoursByColor.forEach { (color, contours) ->
            val unifiedPath = Path()
            contours.forEach { contour ->
                if (contour.points.isNotEmpty()) {
                    unifiedPath.moveTo(contour.points.first().x, contour.points.first().y)
                    contour.points.drop(1).forEach { unifiedPath.lineTo(it.x, it.y) }
                }
            }

            val pathMeasure = PathMeasure(unifiedPath, false)
            val totalLength = pathMeasure.length
            val numPointsForGroup = targetMarkersPerColor[color] ?: 2

            if (totalLength > 0 && numPointsForGroup >= 2) {
                val newPoints = mutableListOf<PointF>()
                val pos = FloatArray(2)
                for (i in 0 until numPointsForGroup) {
                    val distance = (totalLength / (numPointsForGroup - 1)) * i
                    pathMeasure.getPosTan(distance, pos, null)
                    newPoints.add(PointF(pos[0], pos[1]))
                }
                newMarkers.add(SignatureContour(newPoints, color))
            }
        }

        markers.clear()
        markers.addAll(newMarkers)
        basePathForMarkers.reset()
    }

    fun generateProceduralSignatureBitmap(refreshPoints: Boolean = true): android.graphics.Bitmap? {
        val allPoints = markers.flatMap { it.points }
        if (allPoints.isEmpty()) return null

        if (refreshPoints) {
            generateSignaturePath() // Ensure the points are fresh
        }

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
                    val progress = if (contour.size > 1) i.toFloat() / (contour.size - 2).toFloat().coerceAtLeast(1f) else 0f
                    val currentThickness = when {
                        progress < 0.5 -> {
                            val localProgress = progress * 2
                            startThickness + (midThickness - startThickness) * localProgress
                        }
                        else -> {
                            val localProgress = (progress - 0.5f) * 2
                            midThickness + (endThickness - midThickness) * localProgress
                        }
                    }
                    signaturePaint.strokeWidth = currentThickness
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
        val maxStrokeWidth = Math.max(startThickness, Math.max(midThickness, endThickness))
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
            if (contour.points.size >= 2) {
                // The randomization radius is now directly controlled by the marker size.
                val effectiveRandomization = markerRadius

                val randomPoints = contour.points.map { marker ->
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

    fun traceBitmapToMarkers(bitmap: android.graphics.Bitmap, brightnessThreshold: Int) {
        markers.clear()
        drawingPath.reset()

        // 1. Pixel analysis to find all opaque points
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val opaquePoints = mutableListOf<PointF>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                // Consider a pixel as "ink" if it's not transparent and dark enough
                if (Color.alpha(pixel) > 128) {
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    val brightness = (r + g + b) / 3
                    if (brightness < brightnessThreshold) {
                        opaquePoints.add(PointF(x.toFloat(), y.toFloat()))
                    }
                }
            }
        }
        if (opaquePoints.isEmpty()) {
            invalidate()
            return
        }

        // 2. Point simplification using a grid
        val gridSize = 10
        val grid = mutableMapOf<Pair<Int, Int>, MutableList<PointF>>()
        opaquePoints.forEach { point ->
            val gridX = (point.x / gridSize).toInt()
            val gridY = (point.y / gridSize).toInt()
            grid.computeIfAbsent(Pair(gridX, gridY)) { mutableListOf() }.add(point)
        }
        val simplifiedPoints = grid.values.map { pointsInCell ->
            val centerX = pointsInCell.sumOf { it.x.toDouble() } / pointsInCell.size
            val centerY = pointsInCell.sumOf { it.y.toDouble() } / pointsInCell.size
            PointF(centerX.toFloat(), centerY.toFloat())
        }.toMutableList()

        // 3. Path tracing using nearest-neighbor algorithm
        val contours = mutableListOf<MutableList<PointF>>()
        val distThresholdSq = (gridSize * 3.5) * (gridSize * 3.5)
        while (simplifiedPoints.isNotEmpty()) {
            val newContour = mutableListOf<PointF>()
            var currentPoint = simplifiedPoints.minWithOrNull(compareBy({ it.y }, { it.x }))!!
            newContour.add(currentPoint)
            simplifiedPoints.remove(currentPoint)

            while (true) {
                val closestPoint = simplifiedPoints.minByOrNull { p ->
                    val dx = p.x - currentPoint.x
                    val dy = p.y - currentPoint.y
                    dx * dx + dy * dy
                }
                if (closestPoint != null) {
                    val dx = closestPoint.x - currentPoint.x
                    val dy = closestPoint.y - currentPoint.y
                    if (dx * dx + dy * dy < distThresholdSq) {
                        currentPoint = closestPoint
                        newContour.add(currentPoint)
                        simplifiedPoints.remove(currentPoint)
                    } else {
                        break
                    }
                } else {
                    break
                }
            }
            contours.add(newContour)
        }

        // 4. Scale all contours to fit the canvas view
        val combinedPath = Path()
        contours.forEach { contour ->
            if (contour.isNotEmpty()) {
                combinedPath.moveTo(contour.first().x, contour.first().y)
                contour.drop(1).forEach { p -> combinedPath.lineTo(p.x, p.y) }
            }
        }
        if (combinedPath.isEmpty) {
            invalidate()
            return
        }
        val bounds = android.graphics.RectF()
        combinedPath.computeBounds(bounds, true)
        val scale = minOf(this.width / bounds.width(), this.height / bounds.height()) * 0.9f
        val matrix = android.graphics.Matrix().apply {
            postTranslate(-bounds.centerX(), -bounds.centerY())
            postScale(scale, scale)
            postTranslate(this@SignatureCanvasView.width / 2f, this@SignatureCanvasView.height / 2f)
        }

        // 5. Create a high-fidelity path from the scaled contours
        val highFidelityPath = Path()
        contours.forEach { contour ->
            if (contour.isNotEmpty()) {
                val scaledContour = contour.map { p ->
                    val pointArray = floatArrayOf(p.x, p.y)
                    matrix.mapPoints(pointArray)
                    PointF(pointArray[0], pointArray[1])
                }
                highFidelityPath.moveTo(scaledContour.first().x, scaledContour.first().y)
                scaledContour.drop(1).forEach { p -> highFidelityPath.lineTo(p.x, p.y) }
            }
        }

        // 6. Construir los SignatureContour directamente
        contours.forEach { contour ->
            if (contour.isNotEmpty()) {
                val scaledContour = contour.map { p ->
                    floatArrayOf(p.x, p.y).also { matrix.mapPoints(it) }.let { PointF(it[0], it[1]) }
                }
                if (scaledContour.isNotEmpty()) {
                    markers.add(SignatureContour(scaledContour.toMutableList(), Color.BLUE))
                }
            }
        }

        this.hasBasePath = true
        mode = Mode.EDIT
        regenerateSignature()
        invalidate()
        markerListener?.invoke()
    }

    fun recalculateMarkersFromBasePath() {
        autoPlaceMarkers()
        regenerateSignature()
        invalidate()
        markerListener?.invoke()
    }
}
