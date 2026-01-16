package com.example.imageextractor

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.imageextractor.databinding.FragmentPdfPreviewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Random
import kotlin.math.max
import kotlin.math.min
import android.provider.MediaStore

class PdfPreviewFragment : Fragment() {

    companion object {
        private const val SIGNATURE_CANVAS_WIDTH = 500f
        private const val SIGNATURE_CANVAS_HEIGHT = 200f
    }

    private val random = Random()
    private var _binding: FragmentPdfPreviewBinding? = null
    private val binding get() = _binding!!

    private var partidaId: String? = null
    private var imagePaths: Array<String>? = null
    private val pageBitmaps = mutableListOf<Bitmap>()
    private var currentPageIndex = 0

    private var cleanStampBitmap: Bitmap? = null
    private var firstPageWornStampBitmap: Bitmap? = null
    private var lastPageWornStampBitmap: Bitmap? = null
    private var firstPageStampState: StampState? = null
    private var lastPageStampState: StampState? = null

    // Sello 2
    private var stamp2Bitmap: Bitmap? = null
    private var stamp2WornPreviewBitmap: Bitmap? = null
    private var stamp2State: StampState? = null
    private var isStamp2Enabled: Boolean = false
    private lateinit var stamp2Name: String
    private lateinit var stamp2Position: String
    private lateinit var stamp2Area: String
    private var stamp2FontSize: Float = 13f
    private var stamp2OffsetX: Float = 0f
    private var stamp2OffsetY: Float = 0f
    private var stamp2VariableRotation: Boolean = false
    private var stamp2Rotation: Float = 0f
    private var stamp2RotationTolerance: Float = 5f
    private var stamp2WearIntensity: Float = 30f
    private var stamp2WearSize: Float = 50f
    private var stamp2DotCount: Int = 3
    private var stamp2DotSize: Float = 13f
    private var stamp2PointTextSeparation: Float = 5f
    private var stamp2Brightness: Float = 50f
    private var stamp2Contrast: Float = 50f

    // Signature
    private var isSignatureEnabled: Boolean = false
    private var signatureMarkerContours: List<List<PointF>> = emptyList()
    private var signatureBitmap: Bitmap? = null
    private var signatureState: StampState? = null
    private var signatureImageUri: String? = null
    private var signatureOffsetX: Float = 0f
    private var signatureOffsetY: Float = 0f
    private var signatureScale: Float = 100f
    private var signatureRotation: Float = 0f
    private var randomizationRadius: Float = 20f


    private var isStampEnabled: Boolean = false
    private lateinit var stampDateText: String
    private var stampFontSize: Float = 0f
    private var stampWearIntensity: Float = 0f
    private var stampWearSize: Float = 0f
    private var stampSizePercent: Float = 0f
    private var stampMaxRotation: Float = 0f
    private var stampBrightness: Float = 50f
    private var stampContrast: Float = 50f

    private var brightness: Float = 50f
    private var contrast: Float = 50f

    private var marginTop: Float = 0f
    private var marginBottom: Float = 0f
    private var marginLeft: Float = 0f
    private var marginRight: Float = 0f

    data class Watermark(
        val text: String,
        val opacity: Float,
        val size: Float,
        val scale: Float,
        val dx: Float,
        val dy: Float,
        val angle: Float,
        val align: Int,
        val rightCrop: Float
    )

    private val watermarks = mutableListOf<Watermark>()

    // Dynamic fields for watermark 3
    private var dynamicNumeroPublicidad: String? = null
    private var dynamicAno: String? = null
    private var dynamicDigito1: String? = null
    private var dynamicDigito2: String? = null
    private var dynamicNumeroPartida: String? = null
    private var dynamicTipoPartida: String? = null
    private var dynamicFecha: String? = null
    private var dynamicHoraWm4: String? = null

    data class StampState(var x: Float, var y: Float, var scale: Float, var rotation: Float)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        arguments?.let {
            partidaId = it.getString("partidaId")
            imagePaths = it.getStringArray("imagePaths")
            brightness = it.getFloat("brightness", 50f)
            contrast = it.getFloat("contrast", 50f)

            marginTop = it.getFloat("marginTop", 10f)
            marginBottom = it.getFloat("marginBottom", 10f)
            marginLeft = it.getFloat("marginLeft", 10f)
            marginRight = it.getFloat("marginRight", 10f)

            isStampEnabled = it.getBoolean("isStampEnabled")
            if (isStampEnabled) {
                stampDateText = it.getString("stampDateText", "")
                stampFontSize = it.getFloat("stampFontSize", 220f)
                stampWearIntensity = it.getFloat("stampWearIntensity", 0f)
                stampWearSize = it.getFloat("stampWearSize", 0f)
                stampSizePercent = it.getFloat("stampSizePercent", 10f)
                stampMaxRotation = it.getFloat("stampMaxRotation", 5f)
                stampBrightness = it.getFloat("stampBrightness", 50f)
                stampContrast = it.getFloat("stampContrast", 50f)
            }

            isStamp2Enabled = it.getBoolean("isStamp2Enabled")
            if (isStamp2Enabled) {
                stamp2Name = it.getString("stamp2Name", "NOMBRE APELLIDO")
                stamp2Position = it.getString("stamp2Position", "CARGO")
                stamp2Area = it.getString("stamp2Area", "ZONA REGISTRAL")
                stamp2FontSize = it.getFloat("stamp2FontSize", 13f)
                stamp2OffsetX = it.getFloat("stamp2OffsetX", 0f)
                stamp2OffsetY = it.getFloat("stamp2OffsetY", 0f)
                stamp2VariableRotation = it.getBoolean("stamp2VariableRotation", true)
                stamp2Rotation = it.getFloat("stamp2Rotation", 0f)
                stamp2RotationTolerance = it.getFloat("stamp2RotationTolerance", 5f)
                stamp2WearIntensity = it.getFloat("stamp2WearIntensity", 30f)
                stamp2WearSize = it.getFloat("stamp2WearSize", 50f)
                stamp2DotCount = it.getFloat("stamp2DotCount", 3f).toInt()
                stamp2DotSize = it.getFloat("stamp2DotSize", 13f)
                stamp2PointTextSeparation = it.getFloat("stamp2PointTextSeparation", 5f)
                stamp2Brightness = it.getFloat("stamp2Brightness", 50f)
                stamp2Contrast = it.getFloat("stamp2Contrast", 50f)
            }

            for (i in 1..4) {
                val text = it.getString("w${i}_text", "")
                if (text.isNotBlank()) {
                    watermarks.add(Watermark(
                        text = text,
                        opacity = it.getFloat("w${i}_opacity", 50f),
                        size = it.getFloat("w${i}_size", 72f),
                        scale = it.getFloat("w${i}_scale", 100f),
                        dx = it.getFloat("w${i}_dx", 0f),
                        dy = it.getFloat("w${i}_dy", 0f),
                        angle = it.getFloat("w${i}_angle", 0f),
                        align = if (i == 2) it.getInt("w2_align", 1) else 1,
                        rightCrop = if (i == 2) it.getFloat("w2_right_crop", 0f) else 0f
                    ))
                }
            }
            // Read dynamic values for watermark 3
            dynamicNumeroPublicidad = it.getString("dynamic_numero_publicidad")
            dynamicAno = it.getString("dynamic_ano")
            dynamicDigito1 = it.getString("dynamic_digito1")
            dynamicDigito2 = it.getString("dynamic_digito2")
            dynamicNumeroPartida = it.getString("dynamic_numero_partida")
            dynamicTipoPartida = it.getString("dynamic_tipo_partida")

            // Read data for watermark 4
            dynamicFecha = it.getString("dynamic_fecha")
            dynamicHoraWm4 = it.getString("dynamic_hora_wm4")

        // Read signature data
        isSignatureEnabled = it.getBoolean("isSignatureEnabled", false)
        if (isSignatureEnabled) {
            signatureImageUri = it.getString("signatureImageUri")
            signatureOffsetX = it.getFloat("signatureOffsetX", 0f)
            signatureOffsetY = it.getFloat("signatureOffsetY", 0f)
            signatureScale = it.getFloat("signatureScale", 100f)
            signatureRotation = it.getFloat("signatureRotation", 0f)
            // Also load the randomization radius from shared prefs
            val sharedPrefs = requireActivity().getSharedPreferences("PdfSettings", Context.MODE_PRIVATE)
            randomizationRadius = sharedPrefs.getFloat("signature_random_radius", 20f)
        }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPdfPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        loadPages()
        setupNavigationButtons()
        binding.stampOverlayView.setOnStampUpdateListener { x, y, rotation ->
            val currentState = when (currentPageIndex) {
                0 -> firstPageStampState
                pageBitmaps.size - 1 -> lastPageStampState
                else -> null
            }
            currentState?.let {
                it.x = x
                it.y = y
                it.rotation = rotation
            }
        }

        binding.pdfPageZoomableImageView.setOnMatrixChangedListener(object : ZoomableImageView.OnMatrixChangedListener {
            override fun onMatrixChanged() {
                updateStampOverlay()
            }
        })

        binding.stamp2OverlayView.setOnStampUpdateListener { x, y, rotation ->
            stamp2State?.let {
                it.x = x
                it.y = y
                it.rotation = rotation
            }
        }

        binding.signatureOverlayView.setOnStampUpdateListener { x, y, rotation ->
            signatureState?.let {
                it.x = x
                it.y = y
                it.rotation = rotation
            }
        }

        binding.stampOverlayView.bringToFront()
        binding.stamp2OverlayView.bringToFront()
        binding.signatureOverlayView.bringToFront()
    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.title = "Previsualización: $partidaId"
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    private fun setupNavigationButtons() {
        binding.previousPageButton.setOnClickListener {
            if (currentPageIndex > 0) {
                currentPageIndex--
                displayPage(currentPageIndex)
            }
        }
        binding.nextPageButton.setOnClickListener {
            if (currentPageIndex < pageBitmaps.size - 1) {
                currentPageIndex++
                displayPage(currentPageIndex)
            }
        }
    }

    private fun loadPages() {
        if (imagePaths == null) return
        lifecycleScope.launch(Dispatchers.IO) {
            imagePaths!!.forEach { path ->
                val originalBitmap = BitmapFactory.decodeFile(path)
                val adjustedBitmap = applyBitmapAdjustments(originalBitmap)
                pageBitmaps.add(adjustedBitmap)
            }

            if (isStampEnabled && stampDateText.isNotBlank()) {
                generateCleanStamp()
            }
            if (isStamp2Enabled) {
                generateStamp2Bitmap()
                stamp2Bitmap?.let {
                    val normalizedIntensity = (stamp2WearIntensity / 100.0f) / 5.0f
                    val normalizedSize = (stamp2WearSize / 100.0f) / 5.0f
                    stamp2WornPreviewBitmap = applyInkWear(it, normalizedIntensity, normalizedSize, System.currentTimeMillis() - 1000) // Use a fixed seed for preview
                }
            }
            if (isSignatureEnabled) {
                if (signatureImageUri != null) {
                    // Load signature from image URI
                    try {
                        val uri = Uri.parse(signatureImageUri)
                        signatureBitmap = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Error al cargar la imagen de la firma.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    // Using procedural signature, load markers
                    val sharedPrefs = requireActivity().getSharedPreferences("PdfSettings", Context.MODE_PRIVATE)
                    val markersString = sharedPrefs.getString("signature_markers", null)
                    if (!markersString.isNullOrEmpty()) {
                        signatureMarkerContours = markersString.split("|").map { contourString ->
                            contourString.split(";").mapNotNull {
                                val parts = it.split(",")
                                if (parts.size == 2) {
                                    PointF(parts[0].toFloat(), parts[1].toFloat())
                                } else {
                                    null
                                }
                            }
                        }
                        signatureBitmap = generateProceduralSignatureBitmap()
                    }
                }
            }

            initializeStampStates()

            // Apply wear automatically for Stamp 1
            cleanStampBitmap?.let {
                val normalizedIntensity = stampWearIntensity / 100.0f
                val normalizedSize = stampWearSize / 100.0f
                firstPageWornStampBitmap = applyInkWear(it, normalizedIntensity, normalizedSize, System.currentTimeMillis())
                if (pageBitmaps.size > 1) {
                    lastPageWornStampBitmap = applyInkWear(it, normalizedIntensity, normalizedSize, System.currentTimeMillis() + 1)
                } else {
                    lastPageWornStampBitmap = null
                }
            }


            withContext(Dispatchers.Main) {
                if (pageBitmaps.isNotEmpty()) {
                    displayPage(currentPageIndex)
                }
            }
        }
    }

    private fun initializeStampStates() {
        if (pageBitmaps.isEmpty()) return
        val pageW = 1000f
        val pageH = pageW / (595f / 842f)
        val mmToPx = 2.83f

        // Sello 1
        cleanStampBitmap?.let { stamp ->
            var scale = stampSizePercent / 100f
            var stampWidth = stamp.width * scale
            var stampHeight = stamp.height * scale
            var wasAdjusted = false

            if (stampWidth > pageW || stampHeight > pageH) {
                val widthScale = pageW / stamp.width
                val heightScale = pageH / stamp.height
                scale = minOf(widthScale, heightScale)
                wasAdjusted = true
            }

            stampWidth = stamp.width * scale
            stampHeight = stamp.height * scale

            val random = Random()
            val xPos = pageW - stampWidth - 25
            val yPos = pageH - stampHeight - 25

            firstPageStampState = StampState(
                x = maxOf(0f, xPos),
                y = maxOf(0f, yPos),
                scale = scale,
                rotation = random.nextFloat() * (2 * stampMaxRotation) - stampMaxRotation
            )

            if (pageBitmaps.size > 1) {
                lastPageStampState = StampState(
                    x = maxOf(0f, xPos),
                    y = maxOf(0f, yPos),
                    scale = scale,
                    rotation = random.nextFloat() * (2 * stampMaxRotation) - stampMaxRotation
                )
            }

            if (wasAdjusted) {
                lifecycleScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "El tamaño del sello se ajustó para caber en la página.", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Sello 2
        if (isStamp2Enabled && stamp2Bitmap != null) {
            val dx = stamp2OffsetX * mmToPx
            val dy = stamp2OffsetY * mmToPx
            val scale = 1f
            val stampWidth = stamp2Bitmap!!.width * scale
            val stampHeight = stamp2Bitmap!!.height * scale
            val centerX = pageW / 2
            val centerY = pageH / 2
            val x = centerX + dx - stampWidth / 2
            val y = centerY + dy - stampHeight / 2
            stamp2State = StampState(x, y, scale, stamp2Rotation)
        }

        // Firma
        if (isSignatureEnabled && signatureBitmap != null) {
            val dx = signatureOffsetX * mmToPx
            val dy = signatureOffsetY * mmToPx
            val scale = signatureScale / 100f
            val centerX = pageW / 2
            val centerY = pageH / 2
            val x = centerX + dx - (signatureBitmap!!.width * scale) / 2
            val y = centerY + dy - (signatureBitmap!!.height * scale) / 2
            signatureState = StampState(x, y, scale, signatureRotation)
        }
    }

    private fun displayPage(index: Int) {
        if (index < 0 || index >= pageBitmaps.size) return

        val originalBitmap = pageBitmaps[index]
        // Pass the page index to generatePreviewPage
        val previewPageBitmap = generatePreviewPage(originalBitmap, index)
        binding.pdfPageZoomableImageView.setImageBitmap(previewPageBitmap)

        binding.pageNumberTextView.text = "Página ${index + 1} / ${pageBitmaps.size}"

        updateStampOverlay()
    }

    private fun updateStampOverlay() {
        val currentState = when (currentPageIndex) {
            0 -> firstPageStampState
            pageBitmaps.size - 1 -> lastPageStampState
            else -> null
        }

        val wornBitmap = when (currentPageIndex) {
            0 -> firstPageWornStampBitmap
            pageBitmaps.size - 1 -> lastPageWornStampBitmap
            else -> null
        }
        val bitmapToShow = wornBitmap ?: cleanStampBitmap

        if (currentState != null && bitmapToShow != null) {
            binding.stampOverlayView.visibility = View.VISIBLE
            val imageMatrix = binding.pdfPageZoomableImageView.getDrawMatrix()
            binding.stampOverlayView.setStamp(bitmapToShow, currentState.x, currentState.y, currentState.scale, currentState.rotation, imageMatrix)
        } else {
            binding.stampOverlayView.visibility = View.GONE
        }

        // Update Stamp 2 Overlay
        val bitmapToShow2 = stamp2WornPreviewBitmap ?: stamp2Bitmap
        if (isStamp2Enabled && stamp2State != null && bitmapToShow2 != null) {
            binding.stamp2OverlayView.visibility = View.VISIBLE
            val imageMatrix = binding.pdfPageZoomableImageView.getDrawMatrix()
            binding.stamp2OverlayView.setStamp(bitmapToShow2, stamp2State!!.x, stamp2State!!.y, stamp2State!!.scale, stamp2State!!.rotation, imageMatrix)
        } else {
            binding.stamp2OverlayView.visibility = View.GONE
        }

        // Signature overlay is removed from this fragment
        val bitmapToShowSignature = signatureBitmap
        if (isSignatureEnabled && signatureState != null && bitmapToShowSignature != null) {
            binding.signatureOverlayView.visibility = View.VISIBLE
            val imageMatrix = binding.pdfPageZoomableImageView.getDrawMatrix()
            binding.signatureOverlayView.setStamp(bitmapToShowSignature, signatureState!!.x, signatureState!!.y, signatureState!!.scale, signatureState!!.rotation, imageMatrix)
        } else {
            binding.signatureOverlayView.visibility = View.GONE
        }
    }

    private fun generatePreviewPage(originalBitmap: Bitmap, pageIndex: Int): Bitmap {
        val a4Ratio = 595f / 842f
        val previewWidth = 1000
        val previewHeight = (previewWidth / a4Ratio).toInt()

        val previewBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(previewBitmap)
        canvas.drawColor(Color.WHITE)

        drawBitmapWithMargins(canvas, originalBitmap, previewWidth, previewHeight)
        drawWatermarks(canvas, previewWidth, previewHeight, pageIndex + 1, pageBitmaps.size)

        // The stamp is no longer drawn here to avoid the "ghost" image effect.
        // It's now drawn only in the interactive overlay and during the final PDF save.
        return previewBitmap
    }

    private fun drawBitmapWithMargins(canvas: Canvas, bitmap: Bitmap, pageW: Int, pageH: Int) {
        val matrix = Matrix()
        val highQualityPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        // Regla 1: Si todos los márgenes son 0, dibujar a página completa sin cambios.
        if (marginLeft == 0f && marginRight == 0f && marginTop == 0f && marginBottom == 0f) {
            val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
            val dstRect = RectF(0f, 0f, pageW.toFloat(), pageH.toFloat())
            matrix.setRectToRect(RectF(srcRect), dstRect, Matrix.ScaleToFit.FILL)
            canvas.drawBitmap(bitmap, matrix, highQualityPaint)
            return
        }

        // --- Normalización de márgenes ---
        var currentMarginLeft = marginLeft
        var currentMarginRight = marginRight
        var currentMarginTop = marginTop
        var currentMarginBottom = marginBottom

        val totalHorizontalMargin = currentMarginLeft + currentMarginRight
        val totalVerticalMargin = currentMarginTop + currentMarginBottom

        // Si los márgenes horizontales son inválidos, normalizarlos.
        if (totalHorizontalMargin >= pageW) {
            val ratio = (pageW - 1).toFloat() / totalHorizontalMargin
            currentMarginLeft *= ratio
            currentMarginRight *= ratio
        }

        // Si los márgenes verticales son inválidos, normalizarlos.
        if (totalVerticalMargin >= pageH) {
            val ratio = (pageH - 1).toFloat() / totalVerticalMargin
            currentMarginTop *= ratio
            currentMarginBottom *= ratio
        }
        // --- Fin de la normalización ---

        // Regla 2: Calcular el área disponible con los márgenes (normalizados o no).
        val availableW = pageW - currentMarginLeft - currentMarginRight
        val availableH = pageH - currentMarginTop - currentMarginBottom

        val bitmapRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val availableRatio = availableW / availableH

        val finalW: Float
        val finalH: Float

        if (bitmapRatio > availableRatio) {
            finalW = availableW
            finalH = availableW / bitmapRatio
        } else {
            finalH = availableH
            finalW = availableH * bitmapRatio
        }

        val left = currentMarginLeft + (availableW - finalW) / 2
        val top = currentMarginTop + (availableH - finalH) / 2

        val dstRect = RectF(left, top, left + finalW, top + finalH)
        val srcRect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        matrix.setRectToRect(srcRect, dstRect, Matrix.ScaleToFit.CENTER)

        canvas.drawBitmap(bitmap, matrix, highQualityPaint)

    }

    private fun drawPdfPage(canvas: Canvas, pageInfo: PdfDocument.PageInfo, originalBitmap: Bitmap, index: Int, stamp2Seed: Long) {
        val highQualityPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val previewToPdfScale = 595f / 1000f

        // 1. Draw background
        canvas.drawColor(Color.WHITE)

        // 2. Draw original image directly onto PDF canvas with margins
        drawBitmapWithMargins(canvas, originalBitmap, pageInfo.pageWidth, pageInfo.pageHeight)

        // 3. Draw watermarks directly onto PDF canvas
        drawWatermarks(canvas, pageInfo.pageWidth, pageInfo.pageHeight, index + 1, pageBitmaps.size)

        // 4. Draw stamps, scaling coordinates from preview (1000px) to PDF (595px)
        val currentState = when(index) {
            0 -> firstPageStampState
            pageBitmaps.size - 1 -> lastPageStampState
            else -> null
        }
        val wornBitmap = when (index) {
            0 -> firstPageWornStampBitmap
            pageBitmaps.size - 1 -> lastPageWornStampBitmap
            else -> null
        }
        val finalStampBitmap = wornBitmap ?: cleanStampBitmap
        if (currentState != null && finalStampBitmap != null) {
            val matrix = Matrix()
            val scaledScale = currentState.scale * previewToPdfScale
            matrix.postScale(scaledScale, scaledScale)
            matrix.postRotate(currentState.rotation, finalStampBitmap.width * scaledScale / 2, finalStampBitmap.height * scaledScale / 2)
            matrix.postTranslate(currentState.x * previewToPdfScale, currentState.y * previewToPdfScale)
            canvas.drawBitmap(finalStampBitmap, matrix, highQualityPaint)
        }

        // Draw Stamp 2
        if (isStamp2Enabled && stamp2State != null && stamp2Bitmap != null) {
            // Generate unique wear for each page directly here
            val normalizedIntensity = (stamp2WearIntensity / 100.0f) / 5.0f
            val normalizedSize = (stamp2WearSize / 100.0f) / 5.0f
            // Use page index to guarantee a unique seed per page
            val wornBitmapForPage = applyInkWear(stamp2Bitmap!!, normalizedIntensity, normalizedSize, stamp2Seed)

            val matrix = Matrix()
            val scaledScale = stamp2State!!.scale * previewToPdfScale
            var finalRotation = stamp2State!!.rotation
            // Apply variable rotation if enabled
            if (stamp2VariableRotation) {
                val randomRotation = (Random().nextFloat() * 2 * stamp2RotationTolerance) - stamp2RotationTolerance
                finalRotation += randomRotation
            }
            matrix.postScale(scaledScale, scaledScale)
            matrix.postRotate(finalRotation, wornBitmapForPage.width * scaledScale / 2, wornBitmapForPage.height * scaledScale / 2)
            matrix.postTranslate(stamp2State!!.x * previewToPdfScale, stamp2State!!.y * previewToPdfScale)
            canvas.drawBitmap(wornBitmapForPage, matrix, highQualityPaint)

            // Recycle the dynamically created bitmap to save memory
            wornBitmapForPage.recycle()
        }

        // Draw Signature (from bitmap, either procedural or image-based)
        if (isSignatureEnabled && signatureState != null && signatureBitmap != null) {
            canvas.save()

            // Calculate final dimensions and position in PDF coordinates
            val scaledScale = signatureState!!.scale * previewToPdfScale
            val finalWidth = signatureBitmap!!.width * scaledScale
            val finalHeight = signatureBitmap!!.height * scaledScale
            val finalX = signatureState!!.x * previewToPdfScale
            val finalY = signatureState!!.y * previewToPdfScale

            // Define the destination rectangle on the PDF canvas
            val dstRect = RectF(finalX, finalY, finalX + finalWidth, finalY + finalHeight)

            // Rotate the canvas around the center of the destination rectangle
            canvas.rotate(signatureState!!.rotation, dstRect.centerX(), dstRect.centerY())

            // Define the source rectangle (the entire bitmap)
            val srcRect = Rect(0, 0, signatureBitmap!!.width, signatureBitmap!!.height)

            // Draw the bitmap into the destination rectangle, this prevents clipping issues
            canvas.drawBitmap(signatureBitmap!!, srcRect, dstRect, highQualityPaint)

            // Restore canvas to its original state
            canvas.restore()
        }
    }

    private fun generateProceduralSignatureBitmap(): Bitmap? {
        val allPoints = signatureMarkerContours.flatten()
        if (allPoints.isEmpty()) return null

        // 1. Calculate bounding box of all markers
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        allPoints.forEach { point ->
            minX = min(minX, point.x)
            maxX = max(maxX, point.x)
            minY = min(minY, point.y)
            maxY = max(maxY, point.y)
        }

        // 2. Add padding to avoid clipping the generated curve
        val maxStrokeWidth = 10f // Based on (2 + 1 * 8)
        val padding = randomizationRadius + maxStrokeWidth
        minX -= padding
        minY -= padding
        maxX += padding
        maxY += padding

        val width = (maxX - minX).toInt()
        val height = (maxY - minY).toInt()

        if (width <= 0 || height <= 0) return null // Avoid creating an invalid bitmap

        // 3. Create a bitmap with the exact size
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 4. Translate the canvas so drawing starts at (0,0) relative to the signature bounds
        canvas.translate(-minX, -minY)

        val signaturePaint = Paint().apply {
            color = Color.parseColor("#2557A8")
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val signatureContours = generateProceduralSignaturePoints()
        signatureContours.forEach { contour ->
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

    private fun generateProceduralSignaturePoints(): List<List<PointF>> {
        if (signatureMarkerContours.isEmpty()) {
            return emptyList()
        }

        val signatureContours = mutableListOf<List<PointF>>()

        signatureMarkerContours.forEach { contour ->
            if (contour.size >= 2) {
                val randomPoints = contour.map { marker ->
                    val angle = random.nextDouble() * 2 * Math.PI
                    val radius = random.nextDouble() * randomizationRadius
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
                signatureContours.add(interpolatedPoints)
            }
        }
        return signatureContours
    }

    private fun savePdfWithInteractiveStamp() {
        Toast.makeText(context, "Guardando PDF final...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            val pdfDocument = PdfDocument()
            val masterRandom = Random()
            pageBitmaps.forEachIndexed { index, originalBitmap ->
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val stamp2Seed = masterRandom.nextLong()
                drawPdfPage(page.canvas, pageInfo, originalBitmap, index, stamp2Seed)
                pdfDocument.finishPage(page)
            }

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val pdfFile = File(downloadsDir, "$partidaId.pdf")
            pdfDocument.writeTo(FileOutputStream(pdfFile))
            pdfDocument.close()

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "PDF guardado en ${pdfFile.absolutePath}", Toast.LENGTH_LONG).show()
                findNavController().popBackStack(R.id.mainMenuFragment, false)
            }
        }
    }

    private fun generateCleanStamp() {
        val context = requireContext()
        val baseStampDrawable = ContextCompat.getDrawable(context, R.drawable.ic_stamp_base)!!
        val bitmap = baseStampDrawable.toBitmap(baseStampDrawable.intrinsicWidth, baseStampDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888)

        val customTypeface = ResourcesCompat.getFont(context, R.font.d_din_condensed_bold)

        val textPaint = Paint().apply {
            color = Color.RED
            textSize = stampFontSize
            typeface = customTypeface ?: Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val canvas = Canvas(bitmap)
        val x = canvas.width / 2f
        val y = (canvas.height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f) - 25f
        canvas.drawText(stampDateText, x, y, textPaint)

        this.cleanStampBitmap = applyStampAdjustments(bitmap)
    }

    private fun generateStamp2Bitmap() {
        // 1. Setup paints
        val textPaint = Paint().apply {
            color = Color.parseColor("#00008B") // Darker blue
            textSize = stamp2FontSize
            typeface = Typeface.create("Arial", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val dotPaint = Paint().apply {
            color = Color.parseColor("#00008B") // Darker blue
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        // 2. Prepare text lines (no dots)
        val textLines = listOf(
            stamp2Name.uppercase(),
            stamp2Position.uppercase(),
            stamp2Area.uppercase()
        )

        // 3. Calculate dimensions
        val textBounds = Rect()
        textPaint.getTextBounds("A", 0, 1, textBounds)
        val lineHeight = textBounds.height() * 1.5f
        val totalTextHeight = textLines.size * lineHeight

        val nameWidth = textPaint.measureText(textLines[0])
        val posWidth = textPaint.measureText(textLines[1])
        val areaWidth = textPaint.measureText(textLines[2])
        val maxTextWidth = maxOf(nameWidth, posWidth, areaWidth)

        val radius = stamp2DotSize / 2f
        val spacing = radius * 2.5f
        val totalDotsWidth = if (stamp2DotCount > 0) (stamp2DotCount - 1) * spacing + (radius * 2) else 0f

        val bitmapWidth = (maxOf(maxTextWidth, totalDotsWidth) + 40).toInt()
        val dotsHeight = if (stamp2DotCount > 0) (radius * 2) + 5f else 0f // Reduced space
        val bitmapHeight = (totalTextHeight + dotsHeight + 20).toInt()

        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val xPos = bitmapWidth / 2f

        // 4. Draw dynamic dots
        var yPos: Float
        if (stamp2DotCount > 0) {
            val startX = xPos - ((stamp2DotCount - 1) * spacing) / 2f
            yPos = radius + 5f // Reduced padding
            repeat(stamp2DotCount) { i ->
                canvas.drawCircle(startX + i * spacing, yPos, radius, dotPaint)
            }
        }

        // 5. Draw text lines
        yPos = if (stamp2DotCount > 0) {
            dotsHeight + stamp2PointTextSeparation + lineHeight - textBounds.bottom
        } else {
            10 + lineHeight - textBounds.bottom
        }


        // 5. Draw text lines
        for (line in textLines) {
            canvas.drawText(line, xPos, yPos, textPaint)
            yPos += lineHeight
        }

        this.stamp2Bitmap = applyStamp2Adjustments(bitmap)
    }

    fun applyInkWear(source: Bitmap, intensity: Float, size: Float, seed: Long): Bitmap {
        if (intensity <= 0.0f) return source

        val maskBitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        maskBitmap.density = source.density
        val maskCanvas = Canvas(maskBitmap)
        maskCanvas.drawColor(Color.WHITE)

        val erasePaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        val random = Random(seed)

        // Re-calibrated formula for a more pronounced 'middle ground' effect
        val baseSize = 2.0f + size * 25f
        val intensityMultiplier = 0.2f + intensity * 4.0f

        // Layer 1: Fine grain noise
        val noiseCount = (source.width * source.height / 50 * intensityMultiplier).toInt()
        for (i in 0..noiseCount) {
            val x = random.nextFloat() * source.width
            val y = random.nextFloat() * source.height
            val radius = random.nextFloat() * (baseSize * 0.4f)
            maskCanvas.drawCircle(x, y, radius, erasePaint)
        }

        // Layer 2: Irregular blotches
        val blotchCount = (40 * intensityMultiplier).toInt()
        for (i in 0..blotchCount) {
            val path = Path()
            val startX = random.nextFloat() * source.width
            val startY = random.nextFloat() * source.height
            path.moveTo(startX, startY)

            val segmentCount = random.nextInt(5) + 4
            for (j in 0..segmentCount) {
                val pathSize = baseSize * 15f
                val cpx1 = startX + random.nextFloat() * pathSize - (pathSize / 2)
                val cpy1 = startY + random.nextFloat() * pathSize - (pathSize / 2)
                val x2 = startX + random.nextFloat() * pathSize - (pathSize / 2)
                val y2 = startY + random.nextFloat() * pathSize - (pathSize / 2)
                path.quadTo(cpx1, cpy1, x2, y2)
            }
            path.close()
            maskCanvas.drawPath(path, erasePaint)
        }

        // 5. Combinar el sello con la máscara generada.
        val resultBitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        resultBitmap.density = source.density
        val resultCanvas = Canvas(resultBitmap)
        resultCanvas.drawBitmap(source, 0f, 0f, null)

        // DST_IN mantiene los píxeles del destino (sello) solo donde los píxeles de origen (máscara) son opacos.
        // Como perforamos agujeros transparentes en la máscara, esas partes del sello se borrarán.
        val maskPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        resultCanvas.drawBitmap(maskBitmap, 0f, 0f, maskPaint)

        maskBitmap.recycle()

        return resultBitmap
    }

    private fun applyBitmapAdjustments(originalBitmap: Bitmap): Bitmap {
        if (brightness == 50f && contrast == 50f) {
            return originalBitmap
        }
        val brightnessValue = (brightness - 50) * 5f
        val contrastValue = contrast / 50f
        val colorMatrix = ColorMatrix(floatArrayOf(
            contrastValue, 0f, 0f, 0f, brightnessValue,
            0f, contrastValue, 0f, 0f, brightnessValue,
            0f, 0f, contrastValue, 0f, brightnessValue,
            0f, 0f, 0f, 1f, 0f
        ))
        val adjustedBitmap = Bitmap.createBitmap(originalBitmap.width, originalBitmap.height, originalBitmap.config)
        adjustedBitmap.density = originalBitmap.density
        val canvas = Canvas(adjustedBitmap)
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(originalBitmap, 0f, 0f, paint)
        return adjustedBitmap
    }

    private fun applyStampAdjustments(originalBitmap: Bitmap): Bitmap {
        if (stampBrightness == 50f && stampContrast == 50f) {
            return originalBitmap
        }
        val brightnessValue = (stampBrightness - 50) * 5f
        val contrastValue = stampContrast / 50f
        val colorMatrix = ColorMatrix(floatArrayOf(
            contrastValue, 0f, 0f, 0f, brightnessValue,
            0f, contrastValue, 0f, 0f, brightnessValue,
            0f, 0f, contrastValue, 0f, brightnessValue,
            0f, 0f, 0f, 1f, 0f
        ))
        val adjustedBitmap = Bitmap.createBitmap(originalBitmap.width, originalBitmap.height, originalBitmap.config)
        adjustedBitmap.density = originalBitmap.density
        val canvas = Canvas(adjustedBitmap)
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(originalBitmap, 0f, 0f, paint)
        return adjustedBitmap
    }

    private fun applyStamp2Adjustments(originalBitmap: Bitmap): Bitmap {
        if (stamp2Brightness == 50f && stamp2Contrast == 50f) {
            return originalBitmap
        }
        val brightnessValue = (stamp2Brightness - 50) * 5f
        val contrastValue = stamp2Contrast / 50f
        val colorMatrix = ColorMatrix(floatArrayOf(
            contrastValue, 0f, 0f, 0f, brightnessValue,
            0f, contrastValue, 0f, 0f, brightnessValue,
            0f, 0f, contrastValue, 0f, brightnessValue,
            0f, 0f, 0f, 1f, 0f
        ))
        val adjustedBitmap = Bitmap.createBitmap(originalBitmap.width, originalBitmap.height, originalBitmap.config)
        adjustedBitmap.density = originalBitmap.density
        val canvas = Canvas(adjustedBitmap)
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(originalBitmap, 0f, 0f, paint)
        return adjustedBitmap
    }

    private fun sharePdf() {
        lifecycleScope.launch(Dispatchers.IO) {
            val pdfFile = savePdfToDownloads()
            if (pdfFile != null) {
                withContext(Dispatchers.Main) {
                    val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", pdfFile)
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, uri)
                        type = "application/pdf"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Compartir PDF"))
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error al guardar el PDF para compartir.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun savePdfToDownloads(): File? {
        val pdfDocument = PdfDocument()
        val masterRandom = Random()
        pageBitmaps.forEachIndexed { index, originalBitmap ->
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, index + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val stamp2Seed = masterRandom.nextLong()
            drawPdfPage(page.canvas, pageInfo, originalBitmap, index, stamp2Seed)
            pdfDocument.finishPage(page)
        }

        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val pdfFile = File(downloadsDir, "$partidaId-share.pdf")
            pdfDocument.writeTo(FileOutputStream(pdfFile))
            pdfDocument.close()
            pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Save Signature position
        signatureState?.let {
            if (signatureBitmap != null) { // Only save if there is a bitmap to reference
                val sharedPrefs = requireActivity().getSharedPreferences("PdfSettings", Context.MODE_PRIVATE)
                val editor = sharedPrefs.edit()

                val pageW = 1000f
                val pageH = pageW / (595f / 842f)
                val pageCenterX = pageW / 2
                val pageCenterY = pageH / 2
                val mmToPx = 2.83f

                // Calculate the center of the signature bitmap on the canvas
                val signatureWidth = signatureBitmap!!.width * it.scale
                val signatureHeight = signatureBitmap!!.height * it.scale
                val finalSignatureCenterX = it.x + signatureWidth / 2
                val finalSignatureCenterY = it.y + signatureHeight / 2

                // Calculate the offset from the page center
                val offsetXInPx = finalSignatureCenterX - pageCenterX
                val offsetYInPx = finalSignatureCenterY - pageCenterY

                val offsetXInMm = offsetXInPx / mmToPx
                val offsetYInMm = offsetYInPx / mmToPx

                editor.putString("signature_offset_x", offsetXInMm.toInt().toString())
                editor.putString("signature_offset_y", offsetYInMm.toInt().toString())
                editor.apply()
            }
        }

        // Save Stamp 2 position
        stamp2State?.let {
            val sharedPrefs = requireActivity().getSharedPreferences("PdfSettings", Context.MODE_PRIVATE)
            val editor = sharedPrefs.edit()

            val pageW = 1000f
            val pageH = pageW / (595f / 842f)
            val centerX = pageW / 2
            val centerY = pageH / 2
            val mmToPx = 2.83f

            val stampWidth = stamp2Bitmap!!.width * it.scale
            val stampHeight = stamp2Bitmap!!.height * it.scale

            val finalStampCenterX = it.x + stampWidth / 2
            val finalStampCenterY = it.y + stampHeight / 2

            val offsetXInPx = finalStampCenterX - centerX
            val offsetYInPx = finalStampCenterY - centerY

            val offsetXInMm = offsetXInPx / mmToPx
            val offsetYInMm = offsetYInPx / mmToPx

            editor.putString("stamp2_offset_x", offsetXInMm.toInt().toString())
            editor.putString("stamp2_offset_y", offsetYInMm.toInt().toString())
            editor.apply()
        }

        pageBitmaps.forEach { it.recycle() }
        pageBitmaps.clear()
        cleanStampBitmap?.recycle()
        firstPageWornStampBitmap?.recycle()
        lastPageWornStampBitmap?.recycle()
        stamp2Bitmap?.recycle()
        stamp2WornPreviewBitmap?.recycle()
        signatureBitmap?.recycle()
        _binding = null
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.pdf_preview_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_share -> {
                sharePdf()
                true
            }
            R.id.action_zoom_in -> {
                binding.pdfPageZoomableImageView.zoomIn()
                updateStampOverlay()
                true
            }
            R.id.action_reset_zoom -> {
                binding.pdfPageZoomableImageView.resetZoom()
                updateStampOverlay()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun drawWatermarks(canvas: Canvas, pageW: Int, pageH: Int, currentPage: Int, totalPages: Int) {
        val previewWidth = 1000f
        val scaleFactor = pageW / previewWidth

        canvas.save()
        canvas.scale(scaleFactor, scaleFactor)

        val previewHeight = pageH / scaleFactor

        watermarks.forEachIndexed { index, watermark ->
            val paint = Paint().apply {
                color = Color.BLACK
                alpha = (watermark.opacity / 100 * 255).toInt()
                textSize = watermark.size * (watermark.scale / 100f)
                typeface = if (index == 2) Typeface.create("Arial", Typeface.BOLD) else Typeface.create("Arial", Typeface.NORMAL)
            }

            var textToDraw = watermark.text
            when (index) {
                2 -> { // Watermark 3
                    textToDraw = textToDraw.replace("Número publicidad", dynamicNumeroPublicidad ?: "", true)
                    textToDraw = textToDraw.replace("Año", dynamicAno ?: "", true)
                    textToDraw = textToDraw.replace("Digito 1", dynamicDigito1 ?: "", true)
                    textToDraw = textToDraw.replace("Digito 2", dynamicDigito2 ?: "", true)
                    textToDraw = textToDraw.replace("número partida", dynamicNumeroPartida ?: "", true)
                    textToDraw = textToDraw.replace("Tipo partida", dynamicTipoPartida ?: "", true)
                    textToDraw = textToDraw.replace("\"", "")
                }
                3 -> { // Watermark 4
                    textToDraw = textToDraw.replace("\"fecha\"", dynamicFecha ?: "", true)
                    textToDraw = textToDraw.replace("\"Hora\"", dynamicHoraWm4 ?: "", true)
                    textToDraw = textToDraw.replace("\"x\"", currentPage.toString(), true)
                    textToDraw = textToDraw.replace("\"y\"", totalPages.toString(), true)
                }
            }

            val centerX = previewWidth / 2f
            val centerY = previewHeight / 2f
            val mmToPx = 2.83f
            val dx = watermark.dx * mmToPx
            val dy = watermark.dy * mmToPx

            canvas.save()
            canvas.translate(centerX + dx, centerY + dy)

            // --- Clipping Logic ---
            // Apply clipping BEFORE rotating the canvas. This ensures the clip is a straight vertical cut.
            if (index == 1 && watermark.rightCrop > 0) {
                val cropPoints = watermark.rightCrop
                val textWidth = paint.measureText(watermark.text.split("\n").maxByOrNull { it.length } ?: "")

                // Define the visible area. It starts from the left and stops short of the right edge.
                val clipLeft = -textWidth / 2
                val clipRight = (textWidth / 2) - cropPoints
                val clipTop = -centerY
                val clipBottom = centerY

                // Only apply clip if the resulting area is valid
                if (clipLeft < clipRight) {
                    canvas.clipRect(clipLeft, clipTop, clipRight, clipBottom)
                } else {
                    // If crop is larger than width, clip everything
                    canvas.clipRect(0f, 0f, 0f, 0f)
                }
            }

            canvas.rotate(watermark.angle)

            if ((index == 1 || index == 3) && textToDraw.contains("\n")) { // WM2 or WM4 with newlines
                val lines = textToDraw.split("\n")
                paint.textAlign = when (watermark.align) {
                    0 -> Paint.Align.LEFT
                    2 -> Paint.Align.RIGHT
                    else -> Paint.Align.CENTER
                }
                val textHeight = paint.descent() - paint.ascent()
                val totalTextHeight = lines.size * textHeight
                var yPos = -(totalTextHeight / 2f) + textHeight / 2f

                for (line in lines) {
                    val xPos = when (watermark.align) {
                        0 -> -centerX + 20 // Margin for left align
                        2 -> centerX - 20 // Margin for right align
                        else -> 0f
                    }
                    canvas.drawText(line, xPos, yPos, paint)
                    yPos += textHeight
                }
            } else {
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(textToDraw, 0f, 0f, paint)
            }

            canvas.restore()
        }
        canvas.restore()
    }

    private fun clamp(v: Float, min: Float, max: Float) = max(min, min(v, max))
}
