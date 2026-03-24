package com.example.imageextractor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.imageextractor.databinding.FragmentSignatureSettingsBinding

class SignatureSettingsFragment : Fragment() {

    private var _binding: FragmentSignatureSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var numMarkersEditText: com.google.android.material.textfield.TextInputEditText
    private lateinit var markerSizeEditText: com.google.android.material.textfield.TextInputEditText

    private val sharedPrefs by lazy {
        requireActivity().getSharedPreferences("PdfSettings", Context.MODE_PRIVATE)
    }

    private val isSecondarySignature by lazy {
        sharedPrefs.getBoolean("editing_secondary_signature", false)
    }

    private val suffix get() = if (isSecondarySignature) "_secondary" else ""

    private val firebaseManager = FirebaseManager()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            pickImageLauncher.launch("image/*")
        } else {
            Toast.makeText(requireContext(), "Permiso denegado. No se puede seleccionar imagen.", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Copiar la imagen localmente para evitar problemas de permisos persistentes
            val localUri = saveImageLocally(it)
            if (localUri != null) {
                showImportSettingsDialog(localUri)
            } else {
                Toast.makeText(requireContext(), "Error al procesar la imagen seleccionada.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveImageLocally(uri: Uri): Uri? {
        return try {
            val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return null
            val fileName = "sig_${System.currentTimeMillis()}.png"
            val file = java.io.File(requireContext().filesDir, fileName)
            val outputStream = java.io.FileOutputStream(file)
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun showImportSettingsDialog(uri: Uri) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_threshold_settings, null)
        val thresholdSlider = dialogView.findViewById<com.google.android.material.slider.Slider>(R.id.thresholdSlider)
        // Ocultar el slider de trazos ya que no usaremos trazado vectorial
        dialogView.findViewById<View>(R.id.strokesSlider).visibility = View.GONE
        dialogView.findViewById<View>(R.id.strokesSliderLabel).visibility = View.GONE
        dialogView.findViewById<View>(R.id.strokesSliderDescription).visibility = View.GONE

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Ajustes de Importación")
            .setView(dialogView)
            .setPositiveButton("Importar") { _, _ ->
                val threshold = thresholdSlider.value
                saveFloat("signature_white_threshold" + suffix, threshold)

                val currentUris = sharedPrefs.getString("signature_image_uris" + suffix, "") ?: ""
                if (!currentUris.contains(uri.toString())) {
                    val newUris = if (currentUris.isBlank()) uri.toString() else "$currentUris|${uri}"
                    sharedPrefs.edit().putString("signature_image_uris" + suffix, newUris).apply()
                }

                saveString("signature_image_uri" + suffix, uri.toString())

                binding.signatureCanvasView.clearCanvas(switchMode = true)
                saveMarkers()

                // Cargar y mostrar la imagen procesada inmediatamente
                loadAndDisplaySignatureImage(uri, threshold)
                updateButtonLabels()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun loadAndDisplaySignatureImage(uri: Uri, threshold: Float) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val original = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (original != null) {
                    val processed = makeWhiteTransparent(original, threshold)
                    withContext(Dispatchers.Main) {
                        binding.signatureCanvasView.setSignatureBitmap(processed)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error al cargar imagen: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun makeWhiteTransparent(source: Bitmap, threshold: Float): Bitmap {
        val width = source.width
        val height = source.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF

            if (r > threshold && g > threshold && b > threshold) {
                pixels[i] = 0x00000000
            }
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignatureSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        numMarkersEditText = view.findViewById(R.id.signature_num_markers_edit_text)
        markerSizeEditText = view.findViewById(R.id.signature_marker_size_edit_text)

        setupToolbar()
        loadSettings()
        setupListeners()
    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        (activity as? AppCompatActivity)?.supportActionBar?.title = if (isSecondarySignature) "Firma Secundaria" else "Firma Principal"
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    private fun loadSettings() {
        val isLoadedMode = sharedPrefs.getBoolean("signature_type_loaded" + suffix, false)
        binding.signatureTypeSwitch.isChecked = isLoadedMode
        binding.loadedSignaturesButton.visibility = if (isLoadedMode) View.VISIBLE else View.GONE
        binding.selectImageButton.visibility = if (isLoadedMode) View.VISIBLE else View.GONE
        binding.saveSignatureToCloudButton.visibility = if (isLoadedMode) View.VISIBLE else View.GONE

        binding.signatureEnabledCheckbox.isChecked = sharedPrefs.getBoolean("signature_enabled" + suffix, false)
        binding.signatureScaleSlider.value = sharedPrefs.getFloat("signature_scale" + suffix, 100f)
        binding.signatureRotationSlider.value = sharedPrefs.getFloat("signature_rotation" + suffix, 0f)
        binding.signatureRotationToleranceSlider.value = sharedPrefs.getFloat("signature_rotation_tolerance" + suffix, 0f)
        binding.signatureStrokeWidthSlider.value = sharedPrefs.getFloat("signature_stroke_width" + suffix, 5f)
        binding.signatureOffsetXEditText.setText(sharedPrefs.getString("signature_offset_x" + suffix, "0"))
        binding.signatureOffsetYEditText.setText(sharedPrefs.getString("signature_offset_y" + suffix, "-19"))

        val numMarkers = sharedPrefs.getInt("signature_num_markers" + suffix, 15)
        val markerSize = sharedPrefs.getFloat("signature_marker_size" + suffix, 10f)

        numMarkersEditText.setText(numMarkers.toString())
        markerSizeEditText.setText(markerSize.toString())

        binding.signatureCanvasView.setNumMarkers(numMarkers)
        binding.signatureCanvasView.setMarkerRadius(markerSize)
        binding.signatureCanvasView.setStrokeBaseWidth(sharedPrefs.getFloat("signature_stroke_width" + suffix, 5f))

        // Load image if exists
        val imageUriString = sharedPrefs.getString("signature_image_uri" + suffix, null)
        if (imageUriString != null) {
            val threshold = sharedPrefs.getFloat("signature_white_threshold" + suffix, 210f)
            loadAndDisplaySignatureImage(Uri.parse(imageUriString), threshold)
        }

        // Load markers in background to avoid ANR
        val markersString = sharedPrefs.getString("signature_markers" + suffix, null)
        if (!markersString.isNullOrEmpty()) {
            lifecycleScope.launch(Dispatchers.Default) {
                val contours = markersString.split("|").map { contourString ->
                    contourString.split(";").mapNotNull {
                        val parts = it.split(",")
                        if (parts.size == 2) {
                            PointF(parts[0].toFloat(), parts[1].toFloat())
                        } else {
                            null
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    binding.signatureCanvasView.setMarkerContours(contours)
                    updateButtonLabels()
                }
            }
        } else {
            updateButtonLabels()
        }
    }

    private fun updateButtonLabels() {
        val isLoadedMode = sharedPrefs.getBoolean("signature_type_loaded" + suffix, false)
        val hasImage = sharedPrefs.getString("signature_image_uri" + suffix, null) != null

        if (isLoadedMode) {
            // Modo Firmas Cargadas
            binding.primaryActionButton.visibility = View.GONE
            binding.proceduralControlsLayout.visibility = View.GONE
            binding.editActionsLayout.visibility = View.GONE
            binding.selectImageButton.visibility = View.VISIBLE
            binding.loadedSignaturesButton.visibility = View.VISIBLE
            // Mostrar u ocultar lienzo según si hay imagen
            binding.signatureCanvasView.visibility = if (hasImage) View.VISIBLE else View.GONE
        } else {
            // Modo Firmas Autogeneradas
            binding.selectImageButton.visibility = View.GONE
            binding.loadedSignaturesButton.visibility = View.GONE
            binding.signatureCanvasView.visibility = View.VISIBLE
            binding.primaryActionButton.visibility = View.VISIBLE
            binding.proceduralControlsLayout.visibility = View.VISIBLE
            if (binding.signatureCanvasView.mode == SignatureCanvasView.Mode.DRAW) {
                binding.primaryActionButton.text = "Terminar Dibujo"
                binding.editActionsLayout.visibility = View.GONE
            } else {
                binding.primaryActionButton.text = "Refrescar Firma"
                binding.editActionsLayout.visibility = View.VISIBLE
            }
        }
    }

    private fun setupListeners() {
        binding.primaryActionButton.setOnClickListener {
            if (binding.signatureCanvasView.mode == SignatureCanvasView.Mode.DRAW) {
                // We are in DRAW mode, so the button is "Terminar Dibujo"
                if (binding.signatureCanvasView.getDrawingPath().isEmpty) {
                    Toast.makeText(requireContext(), "Por favor, dibuje una firma primero", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                binding.signatureCanvasView.switchToEditMode()
                updateButtonLabels()
                saveMarkers()
            } else {
                // We are in EDIT mode, so the button is "Refresh Signature"
                binding.signatureCanvasView.regenerateSignature(manualRefresh = true)
                saveMarkers() // Save new markers if regenerated
            }
        }

        binding.secondaryActionButton.setOnClickListener {
            // This button is always "Clear Canvas"
            binding.signatureCanvasView.clearCanvas(switchMode = true)
            binding.deletePointsButton.isChecked = false
            binding.signatureCanvasView.isDeleteMode = false
            updateButtonLabels()
            saveMarkers()
        }

        binding.zoomInButton.setOnClickListener {
            binding.signatureCanvasView.zoomIn()
        }

        binding.zoomNormalButton.setOnClickListener {
            binding.signatureCanvasView.zoomNormal()
        }

        binding.deletePointsButton.setOnClickListener {
            val isChecked = binding.deletePointsButton.isChecked
            binding.signatureCanvasView.isDeleteMode = isChecked
            if (isChecked) {
                Toast.makeText(requireContext(), "Modo eliminación: toque puntos para borrarlos", Toast.LENGTH_SHORT).show()
            }
        }

        binding.undoButton.setOnClickListener {
            binding.signatureCanvasView.undo()
        }

        binding.signatureTypeSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.loadedSignaturesButton.visibility = if (isChecked) View.VISIBLE else View.GONE
            binding.selectImageButton.visibility = if (isChecked) View.VISIBLE else View.GONE
            binding.saveSignatureToCloudButton.visibility = if (isChecked) View.VISIBLE else View.GONE
            saveBoolean("signature_type_loaded" + suffix, isChecked)
            updateButtonLabels()
        }

        binding.loadedSignaturesButton.setOnClickListener {
            val base64String = sharedPrefs.getString("signature_images_base64" + suffix, "") ?: ""
            val base64List = base64String.split("|").filter { it.isNotBlank() }

            if (base64List.isEmpty()) {
                Toast.makeText(context, "No hay firmas guardadas en la nube.", Toast.LENGTH_SHORT).show()
            } else {
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Firmas Guardadas")
                    .setItems(base64List.indices.map { "Firma ${it + 1}" }.toTypedArray()) { _, which ->
                        val selectedBase64 = base64List[which]
                        loadSignatureFromBase64(selectedBase64)
                    }
                    .setNegativeButton("Cerrar", null)
                    .show()
            }
        }

        binding.saveSignatureToCloudButton.setOnClickListener {
            saveCurrentSignatureToBase64()
        }

        binding.selectImageButton.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }


        binding.signatureCanvasView.setMarkerListener {
            saveMarkers()
        }

        binding.signatureEnabledCheckbox.setOnCheckedChangeListener { _, isChecked -> saveBoolean("signature_enabled" + suffix, isChecked) }
        binding.signatureScaleSlider.addOnChangeListener { _, value, _ -> saveFloat("signature_scale" + suffix, value) }
        binding.signatureRotationSlider.addOnChangeListener { _, value, _ -> saveFloat("signature_rotation" + suffix, value) }
        binding.signatureRotationToleranceSlider.addOnChangeListener { _, value, _ -> saveFloat("signature_rotation_tolerance" + suffix, value) }
        binding.signatureStrokeWidthSlider.addOnChangeListener { _, value, _ ->
            saveFloat("signature_stroke_width" + suffix, value)
            binding.signatureCanvasView.setStrokeBaseWidth(value)
        }
        binding.signatureOffsetXEditText.doOnTextChanged { text, _, _, _ -> saveString("signature_offset_x" + suffix, text.toString()) }
        binding.signatureOffsetYEditText.doOnTextChanged { text, _, _, _ -> saveString("signature_offset_y" + suffix, text.toString()) }

        numMarkersEditText.doOnTextChanged { text, _, _, _ ->
            val numMarkers = text.toString().toIntOrNull() ?: 0
            saveInt("signature_num_markers" + suffix, numMarkers)
            binding.signatureCanvasView.setNumMarkers(numMarkers)
        }

        markerSizeEditText.doOnTextChanged { text, _, _, _ ->
            val markerSize = text.toString().toFloatOrNull() ?: 10f
            saveFloat("signature_marker_size" + suffix, markerSize)
            binding.signatureCanvasView.setMarkerRadius(markerSize)
        }
    }

    private fun saveMarkers() {
        val contours = binding.signatureCanvasView.getMarkerContours()
        val markersString = contours.joinToString("|") { contour ->
            contour.joinToString(";") { "${it.x},${it.y}" }
        }
        saveString("signature_markers" + suffix, markersString)
    }

    // SharedPreferences helpers
    private fun saveString(key: String, value: String) {
        sharedPrefs.edit().putString(key, value).apply()
    }

    private fun saveBoolean(key: String, value: Boolean) {
        sharedPrefs.edit().putBoolean(key, value).apply()
    }

    private fun saveFloat(key: String, value: Float) {
        // Sincronizar rotación base de firma hacia sello 2 (solo firma principal)
        if (key == "signature_rotation") {
            sharedPrefs.edit()
                .putFloat("signature_rotation", value)
                .putString("stamp2_rotation", value.toInt().toString())
                .apply()
        } else {
            sharedPrefs.edit().putFloat(key, value).apply()
        }
    }

    private fun saveInt(key: String, value: Int) {
        sharedPrefs.edit().putInt(key, value).apply()
    }

    private fun saveCurrentSignatureToBase64() {
        val bitmap = binding.signatureCanvasView.getSignatureBitmap()
        if (bitmap == null) {
            Toast.makeText(context, "No hay firma para guardar.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val outputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            val base64 = android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.DEFAULT)

            withContext(Dispatchers.Main) {
                val currentBase64String = sharedPrefs.getString("signature_images_base64" + suffix, "") ?: ""
                val newBase64String = if (currentBase64String.isBlank()) base64 else "$currentBase64String|$base64"
                sharedPrefs.edit().putString("signature_images_base64" + suffix, newBase64String).apply()
                Toast.makeText(context, "Firma guardada localmente. Use 'Guardar Registrador' para subir a la nube.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadSignatureFromBase64(base64: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val decodedBytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)

                if (bitmap != null) {
                    // Guardar en archivo local temporal para consistencia con el flujo de imagen
                    val file = java.io.File(requireContext().filesDir, "temp_sig_${System.currentTimeMillis()}.png")
                    java.io.FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    val localUri = Uri.fromFile(file)

                    withContext(Dispatchers.Main) {
                        saveString("signature_image_uri" + suffix, localUri.toString())
                        binding.signatureCanvasView.setSignatureBitmap(bitmap)
                        updateButtonLabels()
                        Toast.makeText(context, "Firma cargada.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error al cargar firma: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
