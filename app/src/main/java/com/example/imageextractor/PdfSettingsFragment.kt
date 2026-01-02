package com.example.imageextractor

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.imageextractor.databinding.FragmentPdfSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import android.content.ContentUris
import android.provider.MediaStore

class PdfSettingsFragment : Fragment() {

    private var _binding: FragmentPdfSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPdfSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        setupMonthSpinner()
    }

    private fun setupMonthSpinner() {
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.months_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.stampMonthSpinner.adapter = adapter
        }
    }

    private fun setupListeners() {
        binding.generatePdfButton.setOnClickListener {
            validateAndProceed(isForPreview = false)
        }
        binding.previewPdfButton.setOnClickListener {
            validateAndProceed(isForPreview = true)
        }
    }

    private fun validateAndProceed(isForPreview: Boolean) {
        if (binding.stampEnabledCheckbox.isChecked && binding.stampDayEditText.text.toString().isBlank()) {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Campo Requerido")
                .setMessage("Por favor, ingrese un día para el sello antes de continuar.")
                .setPositiveButton("Aceptar", null)
                .show()
            return
        }
        showPartidaSelectionDialog(isForPreview)
    }

    private fun showPartidaSelectionDialog(isForPreview: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            val folders = getCapturedFolders()
            withContext(Dispatchers.Main) {
                if (folders.isEmpty()) {
                    Toast.makeText(context, "No se encontraron partidas capturadas.", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                val partidaIds = folders.map { it.partidaId }.toTypedArray()
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Seleccionar Partida")
                    .setItems(partidaIds) { _, which ->
                        val selectedFolder = folders[which]
                        if (isForPreview) {
                            navigateToPreview(selectedFolder)
                        } else {
                            generateFinalPdf(selectedFolder)
                        }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
    }

    private fun navigateToPreview(folder: ImageFolder) {
        val bundle = Bundle().apply {
            putString("partidaId", folder.partidaId)
            putStringArray("imagePaths", folder.imageFiles.map { it.path }.toTypedArray())

            putBoolean("isStampEnabled", binding.stampEnabledCheckbox.isChecked)
            if (binding.stampEnabledCheckbox.isChecked) {
                val dayInt = binding.stampDayEditText.text.toString().toIntOrNull()
                val stampDay = dayInt?.let { String.format("%02d", it) } ?: ""
                val stampMonth = binding.stampMonthSpinner.selectedItem.toString()
                val stampYear = binding.stampYearEditText.text.toString()
                putString("stampDateText", "$stampDay $stampMonth. $stampYear")
                putFloat("stampFontSize", binding.stampFontSizeEditText.text.toString().toFloatOrNull() ?: 220f)
                putFloat("stampWearIntensity", binding.stampWearIntensitySlider.value / 100f)
                putFloat("stampSizePercent", binding.stampSizeEditText.text.toString().toFloatOrNull() ?: 5f)
                putFloat("stampMaxRotation", binding.stampRotationEditText.text.toString().toFloatOrNull() ?: 5f)
            }
        }
        findNavController().navigate(R.id.action_pdfSettingsFragment_to_pdfPreviewFragment, bundle)
    }

    private fun getCapturedFolders(): List<ImageFolder> {
        // ... (getCapturedFolders logic remains the same)
        val folders = mutableMapOf<String, MutableList<ImageFile>>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA
        )
        val selection = "${MediaStore.Images.Media.DATA} like ? and ${MediaStore.Images.Media.DATA} like ?"
        val selectionArgs = arrayOf("%/Download/capturas_sunarp/%", "%-Hoja %")

        val cursor = requireContext().contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Images.Media.DISPLAY_NAME} ASC"
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val pathColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val name = it.getString(nameColumn)
                val path = it.getString(pathColumn)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                val partidaId = name.substringBefore("-Hoja").trim()
                if (partidaId.isNotEmpty()) {
                    val imageFile = ImageFile(uri, path, name)
                    folders.getOrPut(partidaId) { mutableListOf() }.add(imageFile)
                }
            }
        }

        return folders.map { (partidaId, files) ->
            val sortedFiles = files.sortedBy { it.name.substringAfter("-Hoja ").substringBefore(".png").toIntOrNull() ?: 0 }
            ImageFolder(partidaId = partidaId, imageFiles = sortedFiles)
        }
    }

    private fun generateFinalPdf(folder: ImageFolder) {
        Toast.makeText(context, "Generando PDF final...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // This function now only creates a basic PDF without a stamp.
                // The advanced creation is handled by the preview screen.
                val pdfFile = createPdfWithoutStamp(folder)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "PDF Básico guardado en ${pdfFile.absolutePath}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error al generar el PDF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun createPdfWithoutStamp(folder: ImageFolder): File {
        val pdfDocument = PdfDocument()
        folder.imageFiles.forEachIndexed { index, imageFile ->
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, index + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            val bitmap = BitmapFactory.decodeFile(imageFile.path)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            pdfDocument.finishPage(page)
            bitmap.recycle()
        }

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val pdfFile = File(downloadsDir, "${folder.partidaId}_simple.pdf")
        pdfDocument.writeTo(FileOutputStream(pdfFile))
        pdfDocument.close()
        return pdfFile
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
