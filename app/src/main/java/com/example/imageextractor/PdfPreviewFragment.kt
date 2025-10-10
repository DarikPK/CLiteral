package com.example.imageextractor

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.imageextractor.databinding.FragmentPdfPreviewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfPreviewFragment : Fragment() {

    private var _binding: FragmentPdfPreviewBinding? = null
    private val binding get() = _binding!!

    private lateinit var pdfRenderer: PdfRenderer
    private lateinit var parcelFileDescriptor: ParcelFileDescriptor
    private lateinit var pageAdapter: PdfPageAdapter
    private var pdfPath: String? = null
    private var partidaId: String? = null
    private val pageBitmaps = mutableListOf<Bitmap>()

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
        setupRecyclerView()
        renderPdf()
        setupSaveButton()
    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.title = "Previsualización: $partidaId"
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun setupRecyclerView() {
        pageAdapter = PdfPageAdapter()
        binding.pdfPagesRecyclerView.adapter = pageAdapter
        binding.pdfPagesRecyclerView.layoutManager = LinearLayoutManager(context)

        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                pageAdapter.moveItem(fromPosition, toPosition)
                return true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        }
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(binding.pdfPagesRecyclerView)
    }

    private fun setupSaveButton() {
        binding.fabSavePdf.setOnClickListener {
            saveReorderedPdf()
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
                    pageAdapter.submitList(pageBitmaps)
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    private fun saveReorderedPdf() {
        val reorderedBitmaps = pageAdapter.getCurrentList()
        if (reorderedBitmaps.isEmpty() || partidaId == null) {
            Toast.makeText(context, "No hay páginas para guardar.", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(context, "Guardando PDF final...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pdfDocument = PdfDocument()
                for ((index, bitmap) in reorderedBitmaps.withIndex()) {
                    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, Paint())
                    pdfDocument.finishPage(page)
                }

                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val pdfFile = File(downloadsDir, "$partidaId.pdf")
                pdfDocument.writeTo(FileOutputStream(pdfFile))
                pdfDocument.close()

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "PDF guardado en ${pdfFile.absolutePath}", Toast.LENGTH_LONG).show()
                    findNavController().popBackStack(R.id.pdfSettingsFragment, false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error al guardar el PDF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
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