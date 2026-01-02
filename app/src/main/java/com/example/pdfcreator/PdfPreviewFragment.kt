package com.example.pdfcreator

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
import com.example.pdfcreator.databinding.FragmentPdfPreviewBinding
import com.example.imageextractor.ZoomableImageView
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
    private var wornStampBitmap: Bitmap? = null
    private var firstPageStampState: StampState? = null
    private var lastPageStampState: StampState? = null

    private var isStampEnabled: Boolean = false
    private lateinit var stampDateText: String
    private var stampFontSize: Float = 0f
    private var stampWearIntensity: Float = 0f
    private var stampSizePercent: Float = 0f
    private var stampMaxRotation: Float = 0f
    private var stampBrightness: Float = 50f
    private var stampContrast: Float = 50f

    private var brightness: Float = 50f
    private var contrast: Float = 50f

    data class StampState(var x: Float, var y: Float, var scale: Float, var rotation: Float)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        arguments?.let {
            partidaId = it.getString("partidaId")
            imagePaths = it.getStringArray("imagePaths")
            brightness = it.getFloat("brightness", 50f)
            contrast = it.getFloat("contrast", 50f)
            isStampEnabled = it.getBoolean("isStampEnabled")
            if (isStampEnabled) {
                stampDateText = it.getString("stampDateText", "")
                stampFontSize = it.getFloat("stampFontSize", 220f)
                stampWearIntensity = it.getFloat("stampWearIntensity", 0.3f)
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
        binding.fabSavePdf.setOnClickListener { savePdfWithInteractiveStamp() }
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
                binding.fabSavePdf.visibility = View.VISIBLE
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
        binding.applyWearButton.isActivated = wornStampBitmap != null

        updateStampOverlay()
    }

    private fun updateStampOverlay() {
        val currentState = when (currentPageIndex) {
            0 -> firstPageStampState
            pageBitmaps.size - 1 -> lastPageStampState
            else -> null
        }

        val bitmapToShow = wornStampBitmap ?: cleanStampBitmap

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

    private fun savePdfWithInteractiveStamp() {
        Toast.makeText(context, "Guardando PDF final...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            val pdfDocument = PdfDocument()
            val finalStampBitmap = wornStampBitmap ?: cleanStampBitmap

            pageBitmaps.forEachIndexed { index, bitmap ->
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)

                val currentState = when(index) {
                    0 -> firstPageStampState
                    pageBitmaps.size - 1 -> lastPageStampState
                    else -> null
                }

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
                // Llama a la nueva función de desgaste procedural
                wornStampBitmap = applyInkWear(it, stampWearIntensity, System.currentTimeMillis())
                withContext(Dispatchers.Main) {
                    binding.applyWearButton.isActivated = true
                    Toast.makeText(context, "Efecto de desgaste aplicado.", Toast.LENGTH_SHORT).show()
                    displayPage(currentPageIndex)
                }
            }
        }
    }

    fun applyInkWear(source: Bitmap, intensity: Float, seed: Long): Bitmap {
        if (intensity <= 0.0f) return source

        val resultBitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        resultBitmap.density = source.density
        val maskBitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        maskBitmap.density = source.density

        val maskCanvas = Canvas(maskBitmap)
        maskCanvas.drawColor(Color.BLACK)

        val random = Random(seed)

        // Capa 1: Ruido Perlin
        val noisePixels = IntArray(source.width * source.height)
        val perlin = PerlinNoise(seed)
        val scale = 10.0 + (1.0 - intensity) * 40.0
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val n = perlin.noise(x / scale, y / scale, 0.8)
                val color = ((n + 1) / 2 * 255).toInt()
                val alpha = (255 * (1.0f - intensity) * 0.5f).toInt()
                noisePixels[y * source.width + x] = Color.argb(alpha, color, color, color)
            }
        }
        maskBitmap.setPixels(noisePixels, 0, source.width, 0, 0, source.width, source.height)

        // Capa 2: Manchas irregulares
        val blotchPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        val blotchCount = (20 * intensity).toInt()
        for (i in 0..blotchCount) {
            val path = Path()
            val startX = random.nextFloat() * source.width
            val startY = random.nextFloat() * source.height
            path.moveTo(startX, startY)
            val segmentCount = random.nextInt(3, 8)
            for (j in 0..segmentCount) {
                val cpx1 = startX + random.nextFloat() * 80 - 40
                val cpy1 = startY + random.nextFloat() * 80 - 40
                val x2 = startX + random.nextFloat() * 80 - 40
                val y2 = startY + random.nextFloat() * 80 - 40
                path.quadTo(cpx1, cpy1, x2, y2)
            }
            path.close()
            val blotchAlpha = (random.nextFloat() * 180 * intensity).toInt()
            blotchPaint.color = Color.argb(blotchAlpha, 255, 255, 255)
            maskCanvas.drawPath(path, blotchPaint)
        }

        val resultCanvas = Canvas(resultBitmap)
        resultCanvas.drawBitmap(source, 0f, 0f, null)
        val maskPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
        resultCanvas.drawBitmap(maskBitmap, 0f, 0f, maskPaint)
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
        val finalStampBitmap = wornStampBitmap ?: cleanStampBitmap ?: return null
        val pdfDocument = PdfDocument()

        pageBitmaps.forEachIndexed { index, bitmap ->
            val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            val currentState = when(index) {
                0 -> firstPageStampState
                pageBitmaps.size - 1 -> lastPageStampState
                else -> null
            }
            if (currentState != null) {
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
        wornStampBitmap?.recycle()
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

class PerlinNoise(private val seed: Long) {
    private val p = IntArray(512)
    init {
        val random = Random(seed)
        val permutation = IntArray(256) { it }
        for (i in 255 downTo 0) {
            val index = random.nextInt(i + 1)
            val temp = permutation[i]
            permutation[i] = permutation[index]
            permutation[index] = temp
        }
        for (i in 0..255) {
            p[i] = permutation[i]
            p[256 + i] = permutation[i]
        }
    }
    fun noise(x: Double, y: Double, z: Double): Double {
        val xi = x.toInt() and 255
        val yi = y.toInt() and 255
        val zi = z.toInt() and 255
        val xf = x - x.toInt()
        val yf = y - y.toInt()
        val zf = z - z.toInt()
        val u = fade(xf)
        val v = fade(yf)
        val w = fade(zf)
        val a = p[xi] + yi
        val aa = p[a] + zi
        val ab = p[a + 1] + zi
        val b = p[xi + 1] + yi
        val ba = p[b] + zi
        val bb = p[b + 1] + zi
        return lerp(w, lerp(v, lerp(u, grad(p[aa], xf, yf, zf), grad(p[ba], xf - 1, yf, zf)),
            lerp(u, grad(p[ab], xf, yf - 1, zf), grad(p[bb], xf - 1, yf - 1, zf))),
            lerp(v, lerp(u, grad(p[aa + 1], xf, yf, zf - 1), grad(p[ba + 1], xf - 1, yf, zf - 1)),
                lerp(u, grad(p[ab + 1], xf, yf - 1, zf - 1), grad(p[bb + 1], xf - 1, yf - 1, zf - 1))))
    }
    private fun fade(t: Double): Double = t * t * t * (t * (t * 6 - 15) + 10)
    private fun lerp(t: Double, a: Double, b: Double): Double = a + t * (b - a)
    private fun grad(hash: Int, x: Double, y: Double, z: Double): Double {
        val h = hash and 15
        val u = if (h < 8) x else y
        val v = if (h < 4) y else if (h == 12 || h == 14) x else z
        return ((if ((h and 1) == 0) u else -u) + (if ((h and 2) == 0) v else -v))
    }
}
