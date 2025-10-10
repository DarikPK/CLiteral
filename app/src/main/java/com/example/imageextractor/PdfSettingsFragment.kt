package com.example.imageextractor

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.imageextractor.databinding.FragmentPdfSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfSettingsFragment : Fragment() {

    private var _binding: FragmentPdfSettingsBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val PREFS_NAME = "PdfSettingsPrefs"
        private const val KEY_BRIGHTNESS = "brightness"
        private const val KEY_CONTRAST = "contrast"
        private const val KEY_MARGIN_TOP = "margin_top"
        private const val KEY_MARGIN_BOTTOM = "margin_bottom"
        private const val KEY_MARGIN_LEFT = "margin_left"
        private const val KEY_MARGIN_RIGHT = "margin_right"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPdfSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSettings()
        setupListeners()
    }

    private fun setupListeners() {
        binding.saveSettingsButton.setOnClickListener {
            saveSettings()
        }
        binding.generatePdfButton.setOnClickListener {
            showPartidaSelectionDialog(isForPreview = false)
        }
        binding.previewPdfButton.setOnClickListener {
            showPartidaSelectionDialog(isForPreview = true)
        }
        binding.brightnessEditText.doOnTextChanged { text, _, _, _ ->
            validateRange(text.toString(), 0, 100)
        }
        binding.contrastEditText.doOnTextChanged { text, _, _, _ ->
            validateRange(text.toString(), 0, 100)
        }
    }

    private fun validateRange(text: String, min: Int, max: Int) {
        val value = text.toIntOrNull()
        if (value != null && value !in min..max) {
            binding.brightnessEditText.error = "El valor debe estar entre $min y $max"
        } else {
            binding.brightnessEditText.error = null
        }
    }

    private fun loadSettings() {
        val prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        binding.brightnessEditText.setText(prefs.getInt(KEY_BRIGHTNESS, 0).toString())
        binding.contrastEditText.setText(prefs.getInt(KEY_CONTRAST, 100).toString())
        binding.marginTopEditText.setText(prefs.getInt(KEY_MARGIN_TOP, 20).toString())
        binding.marginBottomEditText.setText(prefs.getInt(KEY_MARGIN_BOTTOM, 20).toString())
        binding.marginLeftEditText.setText(prefs.getInt(KEY_MARGIN_LEFT, 20).toString())
        binding.marginRightEditText.setText(prefs.getInt(KEY_MARGIN_RIGHT, 20).toString())
    }

    private fun saveSettings() {
        val brightnessStr = binding.brightnessEditText.text.toString()
        val contrastStr = binding.contrastEditText.text.toString()

        val brightness = brightnessStr.toIntOrNull()
        val contrast = contrastStr.toIntOrNull()

        if (brightness == null || brightness !in 0..100) {
            Toast.makeText(context, "El valor de brillo no es válido.", Toast.LENGTH_SHORT).show()
            return
        }
        if (contrast == null || contrast !in 0..100) {
            Toast.makeText(context, "El valor de contraste no es válido.", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        prefs.putInt(KEY_BRIGHTNESS, brightness)
        prefs.putInt(KEY_CONTRAST, contrast)
        prefs.putInt(KEY_MARGIN_TOP, binding.marginTopEditText.text.toString().toIntOrNull() ?: 20)
        prefs.putInt(KEY_MARGIN_BOTTOM, binding.marginBottomEditText.text.toString().toIntOrNull() ?: 20)
        prefs.putInt(KEY_MARGIN_LEFT, binding.marginLeftEditText.text.toString().toIntOrNull() ?: 20)
        prefs.putInt(KEY_MARGIN_RIGHT, binding.marginRightEditText.text.toString().toIntOrNull() ?: 20)
        prefs.apply()

        Toast.makeText(context, "Configuración guardada.", Toast.LENGTH_SHORT).show()
    }

    private fun showPartidaSelectionDialog(isForPreview: Boolean) {
        val folders = getCapturedFolders()
        if (folders.isEmpty()) {
            Toast.makeText(context, "No se encontraron partidas capturadas.", Toast.LENGTH_SHORT).show()
            return
        }

        val partidaIds = folders.map { it.partidaId }.toTypedArray()
        val title = if (isForPreview) "Seleccionar Partida para Previsualizar" else "Seleccionar Partida para PDF"

        android.app.AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setItems(partidaIds) { _, which ->
                val selectedFolder = folders[which]
                if (isForPreview) {
                    generatePdfPreview(selectedFolder)
                } else {
                    generatePdfForPartida(selectedFolder)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun generatePdfPreview(folder: ImageFolder) {
        if (folder.imagePaths.isEmpty()) {
            Toast.makeText(context, "La partida no tiene imágenes para previsualizar.", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(context, "Generando previsualización...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pdfFile = createPdf(folder, isPreview = true)
                withContext(Dispatchers.Main) {
                    val bundle = Bundle().apply {
                        putString("pdfPath", pdfFile.absolutePath)
                        putString("partidaId", folder.partidaId)
                    }
                    findNavController().navigate(R.id.action_pdfSettingsFragment_to_pdfPreviewFragment, bundle)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error al generar la previsualización: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun getCapturedFolders(): List<ImageFolder> {
        val folders = mutableMapOf<String, MutableList<String>>()
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val imageDir = File(downloadsDir, "capturas_sunarp")

        if (imageDir.exists() && imageDir.isDirectory) {
            val imageFiles = imageDir.listFiles { file ->
                file.isFile && file.name.endsWith(".png") && file.name.contains("-Hoja ")
            }
            imageFiles?.forEach { file ->
                val fileName = file.name
                val partidaId = fileName.substringBefore("-Hoja").trim()
                if (partidaId.isNotEmpty()) {
                    folders.getOrPut(partidaId) { mutableListOf() }.add(file.absolutePath)
                }
            }
        }
        return folders.map { (partidaId, paths) ->
            val sortedPaths = paths.sortedBy { path ->
                path.substringAfter("-Hoja ").substringBefore(".png").toIntOrNull() ?: 0
            }
            ImageFolder(partidaId = partidaId, imagePaths = sortedPaths)
        }
    }

    private fun generatePdfForPartida(folder: ImageFolder) {
        Toast.makeText(context, "Iniciando generación de PDF para la partida ${folder.partidaId}", Toast.LENGTH_LONG).show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pdfFile = createPdf(folder, isPreview = false)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "PDF guardado en ${pdfFile.absolutePath}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error al generar el PDF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun createPdf(folder: ImageFolder, isPreview: Boolean): File {
        val brightness = binding.brightnessEditText.text.toString().toIntOrNull() ?: 0
        val contrast = binding.contrastEditText.text.toString().toIntOrNull() ?: 100
        val marginTop = binding.marginTopEditText.text.toString().toIntOrNull() ?: 20
        val marginBottom = binding.marginBottomEditText.text.toString().toIntOrNull() ?: 20
        val marginLeft = binding.marginLeftEditText.text.toString().toIntOrNull() ?: 20
        val marginRight = binding.marginRightEditText.text.toString().toIntOrNull() ?: 20

        val pdfDocument = PdfDocument()
        // Both preview and final generation will process all images.
        val imagesToProcess = folder.imagePaths

        for ((index, imagePath) in imagesToProcess.withIndex()) {
            val pageWidth = 595
            val pageHeight = 842
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val bitmap = BitmapFactory.decodeFile(imagePath)
            val paint = Paint()

            val contrastValue = (contrast / 100.0f) + 1.0f
            val brightnessValue = (brightness / 100.0f) * 255f
            paint.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                contrastValue, 0f, 0f, 0f, brightnessValue,
                0f, contrastValue, 0f, 0f, brightnessValue,
                0f, 0f, contrastValue, 0f, brightnessValue,
                0f, 0f, 0f, 1f, 0f
            )))

            val drawableWidth = 595 - marginLeft - marginRight
            val drawableHeight = 842 - marginTop - marginBottom
            val bitmapRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val drawableRatio = drawableWidth.toFloat() / drawableHeight.toFloat()
            val finalWidth = if (bitmapRatio > drawableRatio) drawableWidth else (drawableHeight * bitmapRatio).toInt()
            val finalHeight = if (bitmapRatio > drawableRatio) (drawableWidth / bitmapRatio).toInt() else drawableHeight
            val left = marginLeft + (drawableWidth - finalWidth) / 2
            val top = marginTop + (drawableHeight - finalHeight) / 2

            canvas.drawBitmap(bitmap, null, android.graphics.Rect(left, top, left + finalWidth, top + finalHeight), paint)
            pdfDocument.finishPage(page)
            bitmap.recycle()
        }

        val targetFile = if (isPreview) {
            val previewDir = File(requireContext().cacheDir, "previews")
            if (!previewDir.exists()) previewDir.mkdirs()
            File(previewDir, "preview.pdf")
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            File(downloadsDir, "${folder.partidaId}.pdf")
        }

        pdfDocument.writeTo(FileOutputStream(targetFile))
        pdfDocument.close()
        return targetFile
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}