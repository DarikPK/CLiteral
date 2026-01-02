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
    private var wornStampBitmap: Bitmap? = null
    private var firstPageStampState: StampState? = null
    private var lastPageStampState: StampState? = null

    private var isStampEnabled: Boolean = false
    private lateinit var stampDateText: String
    private var stampFontSize: Float = 0f
    private var stampWearIntensity: Float = 0f
    private var stampSizePercent: Float = 0f
    private var stampMaxRotation: Float = 0f

    data class StampState(var x: Float, var y: Float, var scale: Float, var rotation: Float)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        arguments?.let {
            partidaId = it.getString("partidaId")
            imagePaths = it.getStringArray("imagePaths")
            isStampEnabled = it.getBoolean("isStampEnabled")
            if (isStampEnabled) {
                stampDateText = it.getString("stampDateText", "")
                stampFontSize = it.getFloat("stampFontSize", 220f)
                stampWearIntensity = it.getFloat("stampWearIntensity", 0.3f)
                stampSizePercent = it.getFloat("stampSizePercent", 5f)
                stampMaxRotation = it.getFloat("stampMaxRotation", 5f)
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
                val bitmap = BitmapFactory.decodeFile(path)
                pageBitmaps.add(bitmap)
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
            firstPageStampState = StampState(
                x = firstPage.width - stampWidth - 25,
                y = firstPage.height - stampHeight - 25,
                scale = scale,
                rotation = random.nextFloat() * (2 * stampMaxRotation) - stampMaxRotation
            )

            if (pageBitmaps.size > 1) {
                val lastPage = pageBitmaps.last()
                lastPageStampState = StampState(
                    x = lastPage.width - stampWidth - 25,
                    y = lastPage.height - stampHeight - 25,
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

        val currentState = when (index) {
            0 -> firstPageStampState
            pageBitmaps.size - 1 -> lastPageStampState
            else -> null
        }

        val bitmapToShow = wornStampBitmap ?: cleanStampBitmap

        if (currentState != null && bitmapToShow != null) {
            binding.stampOverlayView.visibility = View.VISIBLE
            binding.stampOverlayView.setStamp(bitmapToShow, currentState.x, currentState.y, currentState.scale, currentState.rotation)
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

        this.cleanStampBitmap = bitmap
    }

    private fun applyWearEffect() {
        cleanStampBitmap?.let {
            lifecycleScope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Aplicando desgaste...", Toast.LENGTH_SHORT).show()
                }
                wornStampBitmap = applyInkWearMask(it, stampWearIntensity, System.currentTimeMillis())
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Efecto de desgaste aplicado.", Toast.LENGTH_SHORT).show()
                    displayPage(currentPageIndex)
                }
            }
        }
    }

    private fun generateWearMask(width: Int, height: Int, intensity: Float, seed: Long): Bitmap {
        val maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(maskBitmap)
        val random = Random(seed)
        canvas.drawColor(Color.WHITE)
        val erasePaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            isAntiAlias = true
        }
        val baseDefects = (width * height / 500)
        val numDefects = (baseDefects * intensity * 2).toInt()
        for (i in 0 until numDefects) {
            val x = (random.nextGaussian() * (width / 4) + (width / 2)).toFloat()
            val y = (random.nextGaussian() * (height / 4) + (height / 2)).toFloat()
            val maxRadius = height / 25f
            val baseRadius = random.nextFloat() * maxRadius * (0.5f + intensity)
            val numBlobs = random.nextInt(4) + 1
            for (j in 0 until numBlobs) {
                val blobX = x + (random.nextFloat() - 0.5f) * baseRadius * 2
                val blobY = y + (random.nextFloat() - 0.5f) * baseRadius * 2
                val blobRadius = baseRadius * (0.5f + random.nextFloat())
                canvas.drawCircle(blobX, blobY, blobRadius, erasePaint)
            }
        }
        return maskBitmap
    }

    private fun applyInkWearMask(sourceBitmap: Bitmap, intensity: Float, seed: Long): Bitmap {
        if (intensity <= 0f) return sourceBitmap
        val wearMask = generateWearMask(sourceBitmap.width, sourceBitmap.height, intensity, seed)
        val resultBitmap = Bitmap.createBitmap(sourceBitmap.width, sourceBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        canvas.drawBitmap(sourceBitmap, 0f, 0f, null)
        val maskPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawBitmap(wearMask, 0f, 0f, maskPaint)
        wearMask.recycle()
        return resultBitmap
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
                Toast.makeText(context, "Guarda el PDF primero para compartir.", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_zoom_in -> {
                binding.pdfPageZoomableImageView.zoomIn()
                true
            }
            R.id.action_reset_zoom -> {
                binding.pdfPageZoomableImageView.resetZoom()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
