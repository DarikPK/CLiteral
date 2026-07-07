package com.example.imageextractor

import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.imageextractor.databinding.FragmentImagePreviewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import android.text.StaticLayout
import android.text.TextPaint
import android.text.Layout
import android.content.Context

class ImagePreviewFragment : Fragment() {

    private var _binding: FragmentImagePreviewBinding? = null
    private val binding get() = _binding!!

    private var imageUrls: List<String> = emptyList()
    private var initialIndex: Int = 0
    private var filtersEnabled: Boolean = false
    private var partidaId: String? = null
    private var summaryBoxHeaderBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        arguments?.let {
            imageUrls = it.getStringArray("imageUrls")?.toList() ?: emptyList()
            initialIndex = it.getInt("initialIndex", 0)
            filtersEnabled = it.getBoolean("filtersEnabled", false)
            partidaId = it.getString("partidaId")
        }
        loadSummaryHeader()
    }

    private fun loadSummaryHeader() {
        if (filtersEnabled && partidaId?.startsWith("P", ignoreCase = true) == true) {
            val summaryPath = imageUrls.find { it.lowercase().contains("_resumen") }
            if (summaryPath != null) {
                try {
                    val fullSummary = BitmapFactory.decodeFile(summaryPath)
                    if (fullSummary != null) {
                        val headerHeight = (fullSummary.height * 0.16f).toInt()
                        if (headerHeight > 0) {
                            summaryBoxHeaderBitmap = Bitmap.createBitmap(fullSummary, 0, 0, fullSummary.width, headerHeight)
                        }
                        fullSummary.recycle()
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImagePreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupCarousel()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.image_preview_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val currentPosition = binding.viewPager.currentItem
        val currentViewHolder = (binding.viewPager.getChildAt(0) as? RecyclerView)?.findViewHolderForAdapterPosition(currentPosition) as? ImageCarouselAdapter.CarouselViewHolder

        return when (item.itemId) {
            R.id.action_zoom_in -> {
                currentViewHolder?.binding?.zoomableImageView?.zoomIn()
                true
            }
            R.id.action_zoom_reset -> {
                currentViewHolder?.binding?.zoomableImageView?.resetZoom()
                true
            }
            R.id.action_share -> {
                shareCurrentImage()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun shareCurrentImage() {
        val currentPosition = binding.viewPager.currentItem
        val imagePath = imageUrls.getOrNull(currentPosition) ?: return
        val isPartidaP = partidaId?.startsWith("P", ignoreCase = true) == true

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val originalFile = File(imagePath)
                val shareDir = File(requireContext().cacheDir, "images")
                if (!shareDir.exists()) shareDir.mkdirs()

                val shareFile: File
                var mimeType = "image/jpeg"

                if (filtersEnabled) {
                    var bitmap = BitmapFactory.decodeFile(imagePath)

                    // Solo para partidas P: aplicar filtro de cuadro resumen o parche de resumen
                    if (isPartidaP) {
                        if (!imagePath.lowercase().contains("_resumen") && summaryBoxHeaderBitmap != null) {
                            val filteredWithSummary = applySummaryBoxFilter(bitmap, summaryBoxHeaderBitmap!!)
                            bitmap.recycle()
                            bitmap = filteredWithSummary
                        } else if (imagePath.lowercase().contains("_resumen")) {
                            val firstSummaryPath = imageUrls.find { it.lowercase().contains("_resumen") }
                            val patchedSummary = applySummaryPatch(bitmap, imagePath == firstSummaryPath)
                            bitmap.recycle()
                            bitmap = patchedSummary
                        }
                    }

                    // Filtros de imagen (brillo/contraste)
                    val brightness = (35f - 50f) * 5f
                    val contrast = 95f / 50f
                    val cm = ColorMatrix(floatArrayOf(
                        contrast, 0f, 0f, 0f, brightness,
                        0f, contrast, 0f, 0f, brightness,
                        0f, 0f, contrast, 0f, brightness,
                        0f, 0f, 0f, 1f, 0f
                    ))

                    val finalBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(finalBitmap)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    paint.colorFilter = ColorMatrixColorFilter(cm)
                    canvas.drawBitmap(bitmap, 0f, 0f, paint)

                    val tempFile = File(shareDir, "shared_image.jpg")
                    FileOutputStream(tempFile).use { out ->
                        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    shareFile = tempFile
                    bitmap.recycle()
                    finalBitmap.recycle()
                } else {
                    if (isPartidaP) {
                        // Para partidas P sin filtros, compartir la imagen original
                        val extension = originalFile.extension.lowercase()
                        val fileName = "shared_original" + if (extension.isNotEmpty()) ".$extension" else ""
                        val fileToShare = File(shareDir, fileName)
                        originalFile.copyTo(fileToShare, overwrite = true)
                        shareFile = fileToShare
                        mimeType = if (extension == "png") "image/png" else "image/jpeg"
                    } else {
                        // Mantener comportamiento original para otras partidas
                        val tempFile = File(shareDir, "shared_image.jpg")
                        val bitmap = BitmapFactory.decodeFile(imagePath)
                        FileOutputStream(tempFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        }
                        shareFile = tempFile
                        bitmap.recycle()
                    }
                }

                withContext(Dispatchers.Main) {
                    val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", shareFile)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, "Compartir hoja"))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error al preparar la imagen: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun applySummaryPatch(bitmap: Bitmap, showTitle: Boolean): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        val sharedPrefs = requireContext().getSharedPreferences("PdfSettings", Context.MODE_PRIVATE)
        val filterText = sharedPrefs.getString("gallery_filter_text", "CERTIFICADO LITERAL") ?: "CERTIFICADO LITERAL"
        val filterTextSizePercent = sharedPrefs.getFloat("gallery_filter_text_size", 11f) / 100f
        val filterColorStr = sharedPrefs.getString("gallery_filter_color", "#000000") ?: "#000000"
        val filterLineSpacing = sharedPrefs.getFloat("gallery_filter_line_spacing", 1.0f)
        val filterOffsetX = sharedPrefs.getFloat("gallery_filter_offset_x", 0f)
        val filterOffsetY = sharedPrefs.getFloat("gallery_filter_offset_y", 0f)

        val paint = Paint()
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL

        // Parche inferior (pie de página) - Siempre visible
        val pBotL = pBotX
        val pBotT = (bitmap.height * 0.935f) + pBotY
        val pBotR = pBotL + (bitmap.width * pBotW)
        val pBotB = pBotT + (bitmap.height * pBotH)
        canvas.drawRect(pBotL, pBotT, pBotR, pBotB, paint)

        if (showTitle) {
            // Parche superior para el título - Solo visible si showTitle es true
            val headerHeight = bitmap.height * 0.16f
            val rectW = bitmap.width * pTopW
            val rectH = headerHeight * pTopH
            val rectL = (bitmap.width - rectW) / 2f + pTopX
            val rectT = (headerHeight * 0.33f) + pTopY
            canvas.drawRect(rectL, rectT, rectL + rectW, rectT + rectH, paint)

            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                try {
                    color = Color.parseColor(filterColorStr)
                } catch (e: Exception) {
                    color = Color.BLACK
                }
                textSize = headerHeight * filterTextSizePercent
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val staticLayout = StaticLayout.Builder.obtain(filterText, 0, filterText.length, textPaint, rectW.toInt())
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, filterLineSpacing)
                .build()

            canvas.save()
            val ptToPx = bitmap.width / 595f
            val drawX = rectL + (filterOffsetX * ptToPx)
            val drawY = rectT + rectH / 2f + (filterOffsetY * ptToPx) - staticLayout.height / 2f

            canvas.translate(drawX, drawY)
            staticLayout.draw(canvas)
            canvas.restore()
        }

        return result
    }

    private fun applySummaryBoxFilter(extractionBitmap: Bitmap, header: Bitmap): Bitmap {
        val width = extractionBitmap.width
        val originalHeight = extractionBitmap.height

        val sharedPrefs = requireContext().getSharedPreferences("PdfSettings", Context.MODE_PRIVATE)
        val ptToPx = width / 595f

        // --- CONFIGURACIÓN DE PARCHES ---
        val pTopX = sharedPrefs.getFloat("gallery_filter_patch_top_offset_x", 0f) * ptToPx
        val pTopY = sharedPrefs.getFloat("gallery_filter_patch_top_offset_y", 0f) * ptToPx
        val pTopW = sharedPrefs.getFloat("gallery_filter_patch_top_width_percent", 32f) / 100f
        val pTopH = sharedPrefs.getFloat("gallery_filter_patch_top_height_percent", 18f) / 100f

        val pBotX = sharedPrefs.getFloat("gallery_filter_patch_bottom_offset_x", 0f) * ptToPx
        val pBotY = sharedPrefs.getFloat("gallery_filter_patch_bottom_offset_y", 0f) * ptToPx
        val pBotW = sharedPrefs.getFloat("gallery_filter_patch_bottom_width_percent", 100f) / 100f
        val pBotH = sharedPrefs.getFloat("gallery_filter_patch_bottom_height_percent", 6.5f) / 100f

        val pSideX = sharedPrefs.getFloat("gallery_filter_patch_side_offset_x", 0f) * ptToPx
        val pSideY = sharedPrefs.getFloat("gallery_filter_patch_side_offset_y", 0f) * ptToPx
        val pSideW = sharedPrefs.getFloat("gallery_filter_patch_side_width_percent", 4.7f) / 100f
        val pSideH = sharedPrefs.getFloat("gallery_filter_patch_side_height_percent", 24f) / 100f

        // --- CONFIGURACIÓN DE TEXTO ---
        val filterText = sharedPrefs.getString("gallery_filter_text_content", "CERTIFICADO LITERAL") ?: "CERTIFICADO LITERAL"
        val filterTextSizePercent = sharedPrefs.getFloat("gallery_filter_text_width_percent", 11f) / 100f
        val filterColorStr = sharedPrefs.getString("gallery_filter_text_color", "#000000") ?: "#000000"
        val filterLineSpacing = sharedPrefs.getFloat("gallery_filter_text_line_spacing", 1.0f)
        val filterOffsetX = sharedPrefs.getFloat("gallery_filter_text_offset_x", 0f) * ptToPx
        val filterOffsetY = sharedPrefs.getFloat("gallery_filter_text_offset_y", 0f) * ptToPx

        val scale = width.toFloat() / header.width.toFloat()
        val scaledHeaderHeight = (header.height * scale).toInt()

        val cutTop = (originalHeight * 0.14f).toInt()
        val destinationTop = scaledHeaderHeight

        val resultBitmap = Bitmap.createBitmap(width, originalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        canvas.drawColor(Color.WHITE)

        val highQualityPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val headerSrc = Rect(0, 0, header.width, header.height)
        val headerDst = Rect(0, 0, width, scaledHeaderHeight)
        canvas.drawBitmap(header, headerSrc, headerDst, highQualityPaint)

        // DIBUJAR PARCHE SUPERIOR
        val patchPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val rectW = width * pTopW
        val rectH = scaledHeaderHeight * pTopH
        val rectL = (width - rectW) / 2f + pTopX
        val rectT = (scaledHeaderHeight * 0.33f) + pTopY
        canvas.drawRect(rectL, rectT, rectL + rectW, rectT + rectH, patchPaint)

        // DIBUJAR TEXTO
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            try {
                color = Color.parseColor(filterColorStr)
            } catch (e: Exception) {
                color = Color.BLACK
            }
            textSize = scaledHeaderHeight * filterTextSizePercent
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val staticLayout = StaticLayout.Builder.obtain(filterText, 0, filterText.length, textPaint, rectW.toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, filterLineSpacing)
            .build()

        canvas.save()
        val drawX = rectL + filterOffsetX
        val drawY = rectT + rectH / 2f + filterOffsetY - staticLayout.height / 2f

        canvas.translate(drawX, drawY)
        staticLayout.draw(canvas)
        canvas.restore()

        val contentSrc = Rect(0, cutTop, width, cutTop + (originalHeight - destinationTop))
        val contentDst = Rect(0, destinationTop, width, originalHeight)

        if (contentDst.height() > 0) {
            canvas.drawBitmap(extractionBitmap, contentSrc, contentDst, highQualityPaint)
        }

        // DIBUJAR PARCHE LATERAL
        patchPaint.color = Color.WHITE
        patchPaint.style = Paint.Style.FILL

        val pSideLeft = (width * 0.908f) + pSideX
        val pSideTop = (originalHeight * 0.18f) + pSideY
        val pSideRight = pSideLeft + (width * pSideW)
        val pSideBottom = pSideTop + (originalHeight * pSideH)

        canvas.drawRect(pSideLeft, pSideTop, pSideRight, pSideBottom, patchPaint)

        return resultBitmap
    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
        updateToolbarTitle(initialIndex)
    }

    private fun updateToolbarTitle(index: Int) {
        val fileName = imageUrls.getOrNull(index)?.substringAfterLast("/") ?: "Vista Previa"
        (activity as? AppCompatActivity)?.supportActionBar?.title = fileName
    }

    private fun setupCarousel() {
        val adapter = ImageCarouselAdapter(imageUrls, filtersEnabled, partidaId, summaryBoxHeaderBitmap) { isZoomed ->
            binding.viewPager.isUserInputEnabled = !isZoomed
        }
        binding.viewPager.adapter = adapter
        binding.viewPager.setCurrentItem(initialIndex, false)

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateToolbarTitle(position)
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        summaryBoxHeaderBitmap?.recycle()
        summaryBoxHeaderBitmap = null
        _binding = null
    }
}
