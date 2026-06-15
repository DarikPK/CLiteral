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
                        val headerHeight = (fullSummary.height * 0.17f).toInt()
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

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val originalFile = File(imagePath)
                val shareFile: File

                if (filtersEnabled) {
                    val bitmap = BitmapFactory.decodeFile(imagePath)
                    val brightness = (35f - 50f) * 5f
                    val contrast = 95f / 50f
                    val cm = ColorMatrix(floatArrayOf(
                        contrast, 0f, 0f, 0f, brightness,
                        0f, contrast, 0f, 0f, brightness,
                        0f, 0f, contrast, 0f, brightness,
                        0f, 0f, 0f, 1f, 0f
                    ))

                    val filteredBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
                    val canvas = Canvas(filteredBitmap)
                    val paint = Paint()
                    paint.colorFilter = ColorMatrixColorFilter(cm)
                    canvas.drawBitmap(bitmap, 0f, 0f, paint)

                    val shareDir = File(requireContext().cacheDir, "images")
                    if (!shareDir.exists()) shareDir.mkdirs()
                    val tempFile = File(shareDir, "shared_image.jpg")
                    FileOutputStream(tempFile).use { out ->
                        filteredBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    shareFile = tempFile
                    bitmap.recycle()
                    filteredBitmap.recycle()
                } else {
                    // Si no hay filtros, simplemente copiamos a cache con extensión jpg si es necesario o usamos el original
                    val shareDir = File(requireContext().cacheDir, "images")
                    if (!shareDir.exists()) shareDir.mkdirs()
                    val tempFile = File(shareDir, "shared_image.jpg")
                    val bitmap = BitmapFactory.decodeFile(imagePath)
                    FileOutputStream(tempFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    shareFile = tempFile
                    bitmap.recycle()
                }

                withContext(Dispatchers.Main) {
                    val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", shareFile)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
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
