package com.example.imageextractor

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.imageextractor.databinding.FragmentPdfPreviewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PdfPreviewFragment : Fragment() {

    private var _binding: FragmentPdfPreviewBinding? = null
    private val binding get() = _binding!!

    private lateinit var pdfRenderer: PdfRenderer
    private lateinit var parcelFileDescriptor: ParcelFileDescriptor
    private var pdfPath: String? = null
    private var partidaId: String? = null
    private val pageBitmaps = mutableListOf<Bitmap>()
    private var currentPageIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        arguments?.let {
            pdfPath = it.getString("pdfPath")
            partidaId = it.getString("partidaId")
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
        renderPdf()
        setupNavigationButtons()
    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.title = "Previsualización: $partidaId"
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
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

    private fun renderPdf() {
        if (pdfPath == null) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = File(pdfPath!!)
                parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                pdfRenderer = PdfRenderer(parcelFileDescriptor)

                for (i in 0 until pdfRenderer.pageCount) {
                    val page = pdfRenderer.openPage(i)
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    pageBitmaps.add(bitmap)
                    page.close()
                }
                withContext(Dispatchers.Main) {
                    if (pageBitmaps.isNotEmpty()) {
                        displayPage(currentPageIndex)
                    }
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    private fun displayPage(index: Int) {
        if (index >= 0 && index < pageBitmaps.size) {
            binding.pdfPageZoomableImageView.setImageBitmap(pageBitmaps[index])
            binding.pageNumberTextView.text = "Página ${index + 1} / ${pageBitmaps.size}"
            binding.previousPageButton.visibility = if (index > 0) View.VISIBLE else View.INVISIBLE
            binding.nextPageButton.visibility = if (index < pageBitmaps.size - 1) View.VISIBLE else View.INVISIBLE
            binding.pdfPageZoomableImageView.resetZoom()
        }
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
                true
            }
            R.id.action_reset_zoom -> {
                binding.pdfPageZoomableImageView.resetZoom()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun sharePdf() {
        if (pdfPath == null) {
            Toast.makeText(context, "No hay archivo para compartir.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val file = File(pdfPath!!)
            val fileUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Compartir PDF"))
        } catch (e: Exception) {
            Toast.makeText(context, "Error al compartir el PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up bitmaps
        pageBitmaps.forEach { it.recycle() }
        pageBitmaps.clear()
        if (::pdfRenderer.isInitialized) {
            pdfRenderer.close()
            parcelFileDescriptor.close()
        }
        _binding = null
    }
}
