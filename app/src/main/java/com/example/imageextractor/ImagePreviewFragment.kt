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
                            val patchedSummary = applySummaryPatch(bitmap)
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
                        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
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

    private fun applySummaryPatch(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        val paint = Paint()
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL

        // Parche inferior (pie de página)
        val patchLeft = 0f
        val patchRight = bitmap.width.toFloat()
        val patchTop = bitmap.height * 0.935f
        val patchBottom = bitmap.height.toFloat()
        canvas.drawRect(patchLeft, patchTop, patchRight, patchBottom, paint)

        // Parche superior para el título "CERTIFICADO LITERAL"
        val headerHeight = bitmap.height * 0.16f
        val rectW = bitmap.width * 0.40f
        val rectH = headerHeight * 0.25f
        val rectL = (bitmap.width - rectW) / 2f
        val rectT = headerHeight * 0.31f
        canvas.drawRect(rectL, rectT, rectL + rectW, rectT + rectH, paint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = headerHeight * 0.17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("CERTIFICADO LITERAL", bitmap.width / 2f, rectT + rectH * 0.72f, textPaint)

        return result
    }

    private fun applySummaryBoxFilter(extractionBitmap: Bitmap, header: Bitmap): Bitmap {
        val width = extractionBitmap.width
        val originalHeight = extractionBitmap.height

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

        // Parche para ocultar "HOJA DE RESUMEN" y poner "CERTIFICADO LITERAL"
        val patchPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val rectW = width * 0.40f
        val rectH = scaledHeaderHeight * 0.25f
        val rectL = (width - rectW) / 2f
        val rectT = scaledHeaderHeight * 0.31f
        canvas.drawRect(rectL, rectT, rectL + rectW, rectT + rectH, patchPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = scaledHeaderHeight * 0.17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("CERTIFICADO LITERAL", width / 2f, rectT + rectH * 0.72f, textPaint)

        val contentSrc = Rect(0, cutTop, width, cutTop + (originalHeight - destinationTop))
        val contentDst = Rect(0, destinationTop, width, originalHeight)

        if (contentDst.height() > 0) {
            canvas.drawBitmap(extractionBitmap, contentSrc, contentDst, highQualityPaint)
        }

        // Dibujar parche blanco para eliminar texto vertical a la derecha
        // Reutilizar patchPaint ya declarado arriba
        patchPaint.color = Color.WHITE
        patchPaint.style = Paint.Style.FILL

        val patchLeft = width * 0.908f
        val patchRight = width * 0.955f
        val patchTop = originalHeight * 0.18f
        val patchBottom = originalHeight * 0.42f

        canvas.drawRect(patchLeft, patchTop, patchRight, patchBottom, patchPaint)

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
