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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.content.ContentUris
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlin.random.Random

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
        lifecycleScope.launch(Dispatchers.IO) {
            val folders = getCapturedFolders()
            withContext(Dispatchers.Main) {
                if (folders.isEmpty()) {
                    Toast.makeText(context, "No se encontraron partidas capturadas.", Toast.LENGTH_SHORT).show()
                    return@withContext
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
        }
    }

    private fun generatePdfPreview(folder: ImageFolder) {
        if (folder.imageFiles.isEmpty()) {
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

    private fun generateStampBitmap(dateText: String): Bitmap {
        val context = requireContext()
        // 1. Cargar la imagen base del sello (la imagen PNG proporcionada) y convertirla a un Bitmap mutable
        val baseStampDrawable = ContextCompat.getDrawable(context, R.drawable.ic_stamp_base)!!
        val baseStampBitmap = baseStampDrawable.toBitmap(baseStampDrawable.intrinsicWidth, baseStampDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888)

        // 2. Preparar el Paint para el texto de la fecha
        val textPaint = Paint().apply {
            color = 0xFF003366.toInt() // Color azul oscuro, similar al del sello
            textSize = 45f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        // 3. Dibujar el texto en el Bitmap
        val canvas = Canvas(baseStampBitmap)
        val x = canvas.width / 2f
        val y = (canvas.height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f) - 15f // Ajuste vertical
        canvas.drawText(dateText, x, y, textPaint)

        // 4. Seleccionar y aplicar una textura de desgaste aleatoria
        val textureId = if (Random.nextBoolean()) R.drawable.texture_grunge_1 else R.drawable.texture_grunge_2
        val textureDrawable = ContextCompat.getDrawable(context, textureId)!!
        val textureBitmap = textureDrawable.toBitmap(baseStampBitmap.width, baseStampBitmap.height, Bitmap.Config.ALPHA_8)

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawBitmap(textureBitmap, 0f, 0f, maskPaint)
        textureBitmap.recycle()

        // 5. Aplicar una rotación aleatoria
        val matrix = Matrix()
        val rotation = Random.nextFloat() * 10 - 5 // Rotación entre -5 y 5 grados
        matrix.postRotate(rotation, baseStampBitmap.width / 2f, baseStampBitmap.height / 2f)

        val rotatedBitmap = Bitmap.createBitmap(baseStampBitmap, 0, 0, baseStampBitmap.width, baseStampBitmap.height, matrix, true)
        if (rotatedBitmap != baseStampBitmap) {
            baseStampBitmap.recycle()
        }

        return rotatedBitmap
    }

    private fun createPdf(folder: ImageFolder, isPreview: Boolean): File {
        val brightness = binding.brightnessEditText.text.toString().toIntOrNull() ?: 0
        val contrast = binding.contrastEditText.text.toString().toIntOrNull() ?: 100
        val marginTop = binding.marginTopEditText.text.toString().toIntOrNull() ?: 20
        val marginBottom = binding.marginBottomEditText.text.toString().toIntOrNull() ?: 20
        val marginLeft = binding.marginLeftEditText.text.toString().toIntOrNull() ?: 20
        val marginRight = binding.marginRightEditText.text.toString().toIntOrNull() ?: 20

        val isStampEnabled = binding.stampEnabledCheckbox.isChecked
        val stampDateText = binding.stampDateEditText.text.toString()
        var stampBitmap: Bitmap? = null
        if (isStampEnabled && stampDateText.isNotBlank()) {
            stampBitmap = generateStampBitmap(stampDateText)
        }

        val pdfDocument = PdfDocument()
        val imagesToProcess = folder.imageFiles.map { it.path }

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

            if (stampBitmap != null && (index == 0 || index == imagesToProcess.lastIndex)) {
                val stampWidth = (stampBitmap.width * 0.4f).toInt()
                val stampHeight = (stampBitmap.height * 0.4f).toInt()
                val stampLeft = pageWidth - stampWidth - marginRight - 10
                val stampTop = pageHeight - stampHeight - marginBottom - 10
                canvas.drawBitmap(stampBitmap, null, android.graphics.Rect(stampLeft, stampTop, stampLeft + stampWidth, stampTop + stampHeight), null)
            }

            pdfDocument.finishPage(page)
            bitmap.recycle()
        }

        stampBitmap?.recycle()

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
