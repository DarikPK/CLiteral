package com.example.imageextractor

import android.content.ContentValues
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.view.drawToBitmap
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.example.imageextractor.databinding.FragmentEditGalleryBinding

class EditGalleryFragment : Fragment() {

    private var _binding: FragmentEditGalleryBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SharedViewModel by activityViewModels()
    private lateinit var imageAdapter: ImageAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()
        setupSeekBarListeners()
        setupPdfButton()
    }

    private fun setupRecyclerView() {
        imageAdapter = ImageAdapter(emptyList())
        binding.editGalleryRecyclerView.apply {
            layoutManager = GridLayoutManager(context, 3)
            adapter = imageAdapter
        }
    }

    private fun observeViewModel() {
        sharedViewModel.imageUrls.observe(viewLifecycleOwner) { urls ->
            imageAdapter.updateImages(urls ?: emptyList())
        }
    }

    private fun setupSeekBarListeners() {
        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) applyImageFilters()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        binding.brightnessSeekbar.setOnSeekBarChangeListener(listener)
        binding.contrastSeekbar.setOnSeekBarChangeListener(listener)
    }

    private fun applyImageFilters() {
        val brightness = binding.brightnessSeekbar.progress.toFloat() - 100f
        val contrast = binding.contrastSeekbar.progress.toFloat() / 100f
        imageAdapter.applyFilter(brightness, contrast)
    }

    private fun setupPdfButton() {
        binding.pdfButton.setOnClickListener {
            generatePdf()
        }
    }

    private fun generatePdf() {
        if (imageAdapter.itemCount == 0) {
            Toast.makeText(context, "No hay imágenes para generar el PDF.", Toast.LENGTH_SHORT).show()
            return
        }

        val pdfDocument = PdfDocument()
        val recyclerView = binding.editGalleryRecyclerView

        for (i in 0 until imageAdapter.itemCount) {
            val holder = recyclerView.findViewHolderForAdapterPosition(i) as? ImageAdapter.ImageViewHolder
            holder?.let {
                val view = it.binding.galleryImageView
                val bitmap = view.drawToBitmap()
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, i + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                pdfDocument.finishPage(page)
            }
        }

        val fileName = "ImageExtractor_${System.currentTimeMillis()}.pdf"
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }

            val resolver = activity?.contentResolver
            val uri = resolver?.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                resolver.openOutputStream(uri).use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                    Toast.makeText(context, "PDF guardado en Descargas", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error al guardar el PDF: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        } finally {
            pdfDocument.close()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}