package com.example.imageextractor

import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
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

class PdfPreviewFragment : Fragment() {

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
                stampSizePercent = it.getFloat("stampSizePercent", 5f)
                stampMaxRotation = it.getFloat("stampMaxRotation", 5f)
                stampBrightness = it.getFloat("stampBrightness", 50f)
                stampContrast = it.getFloat("stampContrast", 50f)
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
        binding.applyWearButton.setOnClickListener { applyWearEffect() }
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
                initializeStampStates()
            }

            withContext(Dispatchers.Main) {
                if (pageBitmaps.isNotEmpty()) {
                    displayPage(currentPageIndex)
                }
            }
        }
    }

    private fun initializeStampStates() {
        cleanStampBitmap?.let { stamp ->
            if (pageBitmaps.isEmpty()) return

            var scale = stampSizePercent / 100f
            var stampWidth = stamp.width * scale
            var stampHeight = stamp.height * scale

            val page = pageBitmaps[0] // Use first page for dimension checks
            var wasAdjusted = false

            if (stampWidth > page.width || stampHeight > page.height) {
                val widthScale = page.width.toFloat() / stamp.width
                val heightScale = page.height.toFloat() / stamp.height
                scale = minOf(widthScale, heightScale)
                wasAdjusted = true
            }

            stampWidth = stamp.width * scale
            stampHeight = stamp.height * scale

            val random = Random()

            val firstPage = pageBitmaps[0]
            var xPos = firstPage.width - stampWidth - 25
            var yPos = firstPage.height - stampHeight - 25

            firstPageStampState = StampState(
                x = maxOf(0f, xPos),
                y = maxOf(0f, yPos),
                scale = scale,
                rotation = random.nextFloat() * (2 * stampMaxRotation) - stampMaxRotation
            )

            if (pageBitmaps.size > 1) {
                val lastPage = pageBitmaps.last()
                xPos = lastPage.width - stampWidth - 25
                yPos = lastPage.height - stampHeight - 25

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
    }

    private fun displayPage(index: Int) {
        if (index < 0 || index >= pageBitmaps.size) return

        binding.pdfPageZoomableImageView.setImageBitmap(pageBitmaps[index])
        binding.pageNumberTextView.text = "Página ${index + 1} / ${pageBitmaps.size}"
        binding.applyWearButton.isActivated = firstPageWornStampBitmap != null

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
            binding.applyWearButton.visibility = View.VISIBLE
        } else {
            binding.stampOverlayView.visibility = View.GONE
            binding.applyWearButton.visibility = View.GONE
        }
    }

    private fun drawBitmapWithMargins(canvas: Canvas, bitmap: Bitmap, pageW: Int, pageH: Int) {
        val availableW = pageW - marginLeft - marginRight
        val availableH = pageH - marginTop - marginBottom

        if (availableW <= 0 || availableH <= 0) {
            // Fallback for invalid margins, draw original full size
            val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
            val dstRect = RectF(0f, 0f, pageW.toFloat(), pageH.toFloat())
            canvas.drawBitmap(bitmap, srcRect, dstRect, null)
            return
        }

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

        val left = marginLeft + (availableW - finalW) / 2
        val top = marginTop + (availableH - finalH) / 2

        val dstRect = RectF(left, top, left + finalW, top + finalH)
        val srcRect = Rect(0, 0, bitmap.width, bitmap.height)

        canvas.drawBitmap(bitmap, srcRect, dstRect, null)

        val debugPaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 1f
            pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
        }
        val marginRect = RectF(marginLeft, marginTop, pageW - marginRight, pageH - marginBottom)
        canvas.drawRect(marginRect, debugPaint)
    }

    private fun savePdfWithInteractiveStamp() {
        Toast.makeText(context, "Guardando PDF final...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            val pdfDocument = PdfDocument()

            pageBitmaps.forEachIndexed { index, bitmap ->
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                drawBitmapWithMargins(page.canvas, bitmap, 595, 842)

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
                    matrix.postScale(currentState.scale, currentState.scale)
                    matrix.postRotate(currentState.rotation, finalStampBitmap.width * currentState.scale / 2, finalStampBitmap.height * currentState.scale / 2)
                    matrix.postTranslate(currentState.x, currentState.y)
                    page.canvas.drawBitmap(finalStampBitmap, matrix, null)
                }

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

    private fun applyWearEffect() {
        cleanStampBitmap?.let {
            lifecycleScope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Aplicando desgaste...", Toast.LENGTH_SHORT).show()
                }

                val normalizedIntensity = stampWearIntensity / 100.0f
                val normalizedSize = stampWearSize / 100.0f

                // Generate wear for the first page
                firstPageWornStampBitmap = applyInkWear(it, normalizedIntensity, normalizedSize, System.currentTimeMillis())

                // If there is more than one page, generate a different wear pattern for the last page
                if (pageBitmaps.size > 1) {
                    lastPageWornStampBitmap = applyInkWear(it, normalizedIntensity, normalizedSize, System.currentTimeMillis() + 1) // Different seed
                } else {
                    lastPageWornStampBitmap = null // No last page wear if there's only one page
                }

                withContext(Dispatchers.Main) {
                    binding.applyWearButton.isActivated = true
                    Toast.makeText(context, "Efecto de desgaste aplicado.", Toast.LENGTH_SHORT).show()
                    displayPage(currentPageIndex)
                }
            }
        }
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

        pageBitmaps.forEachIndexed { index, bitmap ->
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, index + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            drawBitmapWithMargins(page.canvas, bitmap, 595, 842)
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
                matrix.postScale(currentState.scale, currentState.scale)
                matrix.postRotate(currentState.rotation, finalStampBitmap.width * currentState.scale / 2, finalStampBitmap.height * currentState.scale / 2)
                matrix.postTranslate(currentState.x, currentState.y)
                page.canvas.drawBitmap(finalStampBitmap, matrix, null)
            }
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
        pageBitmaps.forEach { it.recycle() }
        pageBitmaps.clear()
        cleanStampBitmap?.recycle()
        firstPageWornStampBitmap?.recycle()
        lastPageWornStampBitmap?.recycle()
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
}
