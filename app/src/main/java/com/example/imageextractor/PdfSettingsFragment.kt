package com.example.imageextractor

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
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
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.imageextractor.databinding.FragmentPdfSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ImageFolder(val partidaId: String, val imagePaths: List<String>)

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
            showPartidaSelectionDialog()
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
        binding.contrastEditText.setText(prefs.getInt(KEY_CONTRAST, 0).toString())
        binding.marginTopEditText.setText(prefs.getInt(KEY_MARGIN_TOP, 72).toString())
        binding.marginBottomEditText.setText(prefs.getInt(KEY_MARGIN_BOTTOM, 72).toString())
        binding.marginLeftEditText.setText(prefs.getInt(KEY_MARGIN_LEFT, 72).toString())
        binding.marginRightEditText.setText(prefs.getInt(KEY_MARGIN_RIGHT, 72).toString())
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
        prefs.putInt(KEY_MARGIN_TOP, binding.marginTopEditText.text.toString().toIntOrNull() ?: 72)
        prefs.putInt(KEY_MARGIN_BOTTOM, binding.marginBottomEditText.text.toString().toIntOrNull() ?: 72)
        prefs.putInt(KEY_MARGIN_LEFT, binding.marginLeftEditText.text.toString().toIntOrNull() ?: 72)
        prefs.putInt(KEY_MARGIN_RIGHT, binding.marginRightEditText.text.toString().toIntOrNull() ?: 72)
        prefs.apply()

        Toast.makeText(context, "Configuración guardada.", Toast.LENGTH_SHORT).show()
    }

    private fun showPartidaSelectionDialog() {
        val folders = getCapturedFolders()
        if (folders.isEmpty()) {
            Toast.makeText(context, "No se encontraron partidas capturadas.", Toast.LENGTH_SHORT).show()
            return
        }

        val partidaIds = folders.map { it.partidaId }.toTypedArray()

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Seleccionar Partida para PDF")
            .setItems(partidaIds) { _, which ->
                val selectedFolder = folders[which]
                generatePdfForPartida(selectedFolder)
            }
            .setNegativeButton("Cancelar", null)
            .show()
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
                val prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val brightness = prefs.getInt(KEY_BRIGHTNESS, 0)
                val contrast = prefs.getInt(KEY_CONTRAST, 0)
                val marginTop = prefs.getInt(KEY_MARGIN_TOP, 72)
                val marginBottom = prefs.getInt(KEY_MARGIN_BOTTOM, 72)
                val marginLeft = prefs.getInt(KEY_MARGIN_LEFT, 72)
                val marginRight = prefs.getInt(KEY_MARGIN_RIGHT, 72)

                // A4 page size in points (1/72 of an inch)
                val pageWidth = 595
                val pageHeight = 842

                val pdfDocument = PdfDocument()

                for (imagePath in folder.imagePaths) {
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, folder.imagePaths.indexOf(imagePath) + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas

                    val bitmap = BitmapFactory.decodeFile(imagePath)
                    val paint = Paint()

                    // Apply brightness and contrast
                    val contrastValue = (contrast / 100.0f) + 1.0f // Map 0-100 to 1.0-2.0
                    val brightnessValue = (brightness / 100.0f) * 255f // Map 0-100 to 0-255
                    val colorMatrix = ColorMatrix(floatArrayOf(
                        contrastValue, 0f, 0f, 0f, brightnessValue,
                        0f, contrastValue, 0f, 0f, brightnessValue,
                        0f, 0f, contrastValue, 0f, brightnessValue,
                        0f, 0f, 0f, 1f, 0f
                    ))
                    paint.colorFilter = ColorMatrixColorFilter(colorMatrix)

                    // Calculate drawable area and scale image to fit
                    val drawableWidth = pageWidth - marginLeft - marginRight
                    val drawableHeight = pageHeight - marginTop - marginBottom

                    val bitmapRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                    val drawableRatio = drawableWidth.toFloat() / drawableHeight.toFloat()

                    var finalWidth = drawableWidth
                    var finalHeight = drawableHeight

                    if (bitmapRatio > drawableRatio) {
                        finalHeight = (drawableWidth / bitmapRatio).toInt()
                    } else {
                        finalWidth = (drawableHeight * bitmapRatio).toInt()
                    }

                    val left = marginLeft + (drawableWidth - finalWidth) / 2
                    val top = marginTop + (drawableHeight - finalHeight) / 2

                    canvas.drawBitmap(bitmap, null, android.graphics.Rect(left, top, left + finalWidth, top + finalHeight), paint)
                    pdfDocument.finishPage(page)
                    bitmap.recycle()
                }

                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val pdfFile = File(downloadsDir, "${folder.partidaId}.pdf")
                pdfDocument.writeTo(FileOutputStream(pdfFile))
                pdfDocument.close()

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


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}