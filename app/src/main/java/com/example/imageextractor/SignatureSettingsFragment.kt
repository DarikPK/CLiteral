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
import android.widget.LinearLayout
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

    private var tempImageUri: String? = null
    private var tempWhiteThreshold: Float = 210f
    private var editingSignatureIndex: Int = -1

    private val sharedPrefs by lazy {
        requireActivity().getSharedPreferences("PdfSettings", Context.MODE_PRIVATE)
    }

    private val isSecondarySignature: Boolean
        get() = arguments?.getBoolean("is_secondary", false) ?: sharedPrefs.getBoolean("editing_secondary_signature", false)

    private val suffix: String
        get() = if (isSecondarySignature) "_secondary" else ""

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
                tempImageUri = uri.toString()
                tempWhiteThreshold = threshold
                binding.signatureCanvasView.clearCanvas(switchMode = true)

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
                val inputStream = try {
                    if (uri.scheme == "file") {
                        val path = uri.path
                        if (!path.isNullOrBlank()) java.io.FileInputStream(path) else null
                    } else {
                        requireContext().contentResolver.openInputStream(uri)
                    }
                } catch (e: Exception) {
                    null
                }

                if (inputStream == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "No se pudo acceder a la imagen de la firma.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

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
        binding.saveSignatureToCloudButton.visibility = View.VISIBLE

        binding.signatureEnabledCheckbox.isChecked = sharedPrefs.getBoolean("signature_enabled" + suffix, true)
        val sizeX = sharedPrefs.getFloat("signature_size_x" + suffix, 50f)
        val sizeY = sharedPrefs.getFloat("signature_size_y" + suffix, 20f)
        binding.signatureSizeXEditText.setText(sizeX.toString())
        binding.signatureSizeYEditText.setText(sizeY.toString())

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
        if (!imageUriString.isNullOrBlank()) {
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
            binding.signatureCanvasView.clearCanvas(switchMode = true)
            updateButtonLabels()
        }
    }

    private fun updateButtonLabels() {
        val isLoadedMode = binding.signatureTypeSwitch.isChecked
        val hasImage = (tempImageUri ?: sharedPrefs.getString("signature_image_uri" + suffix, null)) != null

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
            } else {
                // We are in EDIT mode, so the button is "Refresh Signature"
                binding.signatureCanvasView.regenerateSignature(manualRefresh = true)
            }
        }

        binding.secondaryActionButton.setOnClickListener {
            // This button is always "Clear Canvas"
            binding.signatureCanvasView.clearCanvas(switchMode = true)
            binding.deletePointsButton.isChecked = false
            binding.signatureCanvasView.isDeleteMode = false
            editingSignatureIndex = -1

            binding.signatureCanvasView.tag = null
            updateButtonLabels()
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
            sharedPrefs.edit().putBoolean("signature_type_loaded" + suffix, isChecked).commit()
            binding.loadedSignaturesButton.visibility = if (isChecked) View.VISIBLE else View.GONE
            binding.selectImageButton.visibility = if (isChecked) View.VISIBLE else View.GONE
            // El botón de guardar ahora siempre será visible según el plan para permitir guardar configuraciones
            binding.saveSignatureToCloudButton.visibility = View.VISIBLE
            updateButtonLabels()
        }

        binding.loadedSignaturesButton.setOnClickListener {
            showSignatureFolderDialog()
        }

        binding.saveSignatureToCloudButton.setOnClickListener {
            saveAllSettingsAndSignature()
        }

        binding.selectImageButton.setOnClickListener {
            editingSignatureIndex = -1
            pickImageLauncher.launch("image/*")
        }


        binding.signatureCanvasView.setMarkerListener {
            // Ya no se guarda automáticamente
        }

        binding.signatureEnabledCheckbox.setOnCheckedChangeListener { _, isChecked -> /* No auto-save */ }

        binding.signatureRotationSlider.addOnChangeListener { _, value, _ -> /* No auto-save */ }
        binding.signatureRotationToleranceSlider.addOnChangeListener { _, value, _ -> /* No auto-save */ }
        binding.signatureStrokeWidthSlider.addOnChangeListener { _, value, _ ->
            binding.signatureCanvasView.setStrokeBaseWidth(value)
        }
        binding.signatureOffsetXEditText.doOnTextChanged { text, _, _, _ -> /* No auto-save */ }
        binding.signatureOffsetYEditText.doOnTextChanged { text, _, _, _ -> /* No auto-save */ }

        numMarkersEditText.doOnTextChanged { text, _, _, _ ->
            val numMarkers = text.toString().toIntOrNull() ?: 0
            binding.signatureCanvasView.setNumMarkers(numMarkers)
        }

        markerSizeEditText.doOnTextChanged { text, _, _, _ ->
            val markerSize = text.toString().toFloatOrNull() ?: 10f
            binding.signatureCanvasView.setMarkerRadius(markerSize)
        }
    }

    private fun saveAllSettingsAndSignature() {
        saveCurrentProfileToFirebase()
        // 1. Persistir configuraciones de la UI en SharedPreferences
        val isLoadedMode = binding.signatureTypeSwitch.isChecked
        saveBoolean("signature_type_loaded" + suffix, isLoadedMode)
        saveBoolean("signature_enabled" + suffix, binding.signatureEnabledCheckbox.isChecked)

        val sizeX = binding.signatureSizeXEditText.text.toString().toFloatOrNull() ?: 50f
        val sizeY = binding.signatureSizeYEditText.text.toString().toFloatOrNull() ?: 20f
        saveFloat("signature_size_x" + suffix, sizeX)
        saveFloat("signature_size_y" + suffix, sizeY)

        val rotation = binding.signatureRotationSlider.value
        saveFloat("signature_rotation" + suffix, rotation)

        saveFloat("signature_rotation_tolerance" + suffix, binding.signatureRotationToleranceSlider.value)
        val strokeWidth = binding.signatureStrokeWidthSlider.value
        saveFloat("signature_stroke_width" + suffix, strokeWidth)

        val offsetX = binding.signatureOffsetXEditText.text.toString().ifBlank { "0" }
        val offsetY = binding.signatureOffsetYEditText.text.toString().ifBlank { "-19" }
        saveString("signature_offset_x" + suffix, offsetX)
        saveString("signature_offset_y" + suffix, offsetY)

        val numMarkers = numMarkersEditText.text.toString().toIntOrNull() ?: 15
        saveInt("signature_num_markers" + suffix, numMarkers)

        val markerSize = markerSizeEditText.text.toString().toFloatOrNull() ?: 10f
        saveFloat("signature_marker_size" + suffix, markerSize)

        // Guardar marcadores procedurales
        val contours = binding.signatureCanvasView.getMarkerContours()
        val markersString = contours.joinToString("|") { contour ->
            contour.joinToString(";") { "${it.x},${it.y}" }
        }
        saveString("signature_markers" + suffix, markersString)

        // Guardar URI de imagen si existe temporalmente (o limpiar si no hay nada nuevo y se borró)
        val hasBitmap = binding.signatureCanvasView.getSignatureBitmap() != null
        val finalUri = if (tempImageUri != null) {
            tempImageUri
        } else if (!hasBitmap) {
            ""
        } else {
            null // No cambiar si ya tiene uno y no se ha borrado/cambiado
        }

        finalUri?.let {
            saveString("signature_image_uri" + suffix, it)
            if (it.isNotEmpty()) saveFloat("signature_white_threshold" + suffix, tempWhiteThreshold)
        }

        // 2. Si estamos en modo cargadas y hay un bitmap, guardarlo como una nueva entrada en la carpeta
        if (isLoadedMode) {
            saveCurrentSignatureToBase64(sizeX, sizeY, offsetX, offsetY)
        } else {
            saveCurrentProfileToFirebase()
            Toast.makeText(context, "Configuración guardada.", Toast.LENGTH_SHORT).show()
        }

        // Limpiar temporales tras guardar
        tempImageUri = null
    }

    private fun saveMarkers() {
        val contours = binding.signatureCanvasView.getMarkerContours()
        val markersString = contours.joinToString("|") { contour ->
            contour.joinToString(";") { "${it.x},${it.y}" }
        }
        saveString("signature_markers" + suffix, markersString)
    }

    private fun saveCurrentProfileToFirebase() {
        val profileId = sharedPrefs.getString("firebase_profile_id", "") ?: ""
        if (profileId.isEmpty()) return

        lifecycleScope.launch(Dispatchers.IO) {
            val profile = RegistrarProfile(
                id = profileId,
                name = sharedPrefs.getString("stamp2_name", "NOMBRE APELLIDO") ?: "NOMBRE APELLIDO",
                position = sharedPrefs.getString("stamp2_position", "CARGO") ?: "CARGO",
                area = sharedPrefs.getString("stamp2_area", "ZONA REGISTRAL") ?: "ZONA REGISTRAL",
                office = sharedPrefs.getString("oficina", "LIMA") ?: "LIMA",

                signatureMarkers = sharedPrefs.getString("signature_markers", "") ?: "",
                signatureRotation = sharedPrefs.getFloat("signature_rotation", 0f),
                signatureRotationTolerance = sharedPrefs.getFloat("signature_rotation_tolerance", 0f),
                signatureWhiteThreshold = sharedPrefs.getFloat("signature_white_threshold", 210f),
                signatureSizeX = sharedPrefs.getFloat("signature_size_x", 50f),
                signatureSizeY = sharedPrefs.getFloat("signature_size_y", 20f),
                signatureOffsetX = sharedPrefs.getString("signature_offset_x", "0") ?: "0",
                signatureOffsetY = sharedPrefs.getString("signature_offset_y", "-19") ?: "-19",
                signatureTypeLoaded = sharedPrefs.getBoolean("signature_type_loaded", false),
                signatureEnabled = sharedPrefs.getBoolean("signature_enabled", true),
                signatureStrokeWidth = sharedPrefs.getFloat("signature_stroke_width", 5f),
                signatureNumMarkers = sharedPrefs.getInt("signature_num_markers", 15),
                signatureMarkerSize = sharedPrefs.getFloat("signature_marker_size", 10f),

                signatureMarkersSecondary = sharedPrefs.getString("signature_markers_secondary", "") ?: "",
                signatureRotationSecondary = sharedPrefs.getFloat("signature_rotation_secondary", 0f),
                signatureRotationToleranceSecondary = sharedPrefs.getFloat("signature_rotation_tolerance_secondary", 0f),
                signatureWhiteThresholdSecondary = sharedPrefs.getFloat("signature_white_threshold_secondary", 210f),
                signatureSizeXSecondary = sharedPrefs.getFloat("signature_size_x_secondary", 50f),
                signatureSizeYSecondary = sharedPrefs.getFloat("signature_size_y_secondary", 20f),
                signatureOffsetXSecondary = sharedPrefs.getString("signature_offset_x_secondary", "0") ?: "0",
                signatureOffsetYSecondary = sharedPrefs.getString("signature_offset_y_secondary", "-19") ?: "-19",
                signatureTypeLoadedSecondary = sharedPrefs.getBoolean("signature_type_loaded_secondary", false),
                signatureEnabledSecondary = sharedPrefs.getBoolean("signature_enabled_secondary", true),
                signatureStrokeWidthSecondary = sharedPrefs.getFloat("signature_stroke_width_secondary", 5f),
                signatureNumMarkersSecondary = sharedPrefs.getInt("signature_num_markers_secondary", 15),
                signatureMarkerSizeSecondary = sharedPrefs.getFloat("signature_marker_size_secondary", 10f),

                signatureImageUris = sharedPrefs.getString("signature_image_uris", "")?.split("|")?.filter { it.isNotBlank() } ?: emptyList(),
                signatureImageUrisSecondary = sharedPrefs.getString("signature_image_uris_secondary", "")?.split("|")?.filter { it.isNotBlank() } ?: emptyList(),
                signatureImagesBase64 = sharedPrefs.getString("signature_images_base64", "")?.split("|")?.filter { it.isNotBlank() } ?: emptyList(),
                signatureImagesBase64Secondary = sharedPrefs.getString("signature_images_base64_secondary", "")?.split("|")?.filter { it.isNotBlank() } ?: emptyList(),
                stamp2FontSize = sharedPrefs.getString("stamp2_font_size", "13") ?: "13",
                stamp2WearIntensity = sharedPrefs.getFloat("stamp2_wear_intensity", 30f),
                stamp2WearSize = sharedPrefs.getFloat("stamp2_wear_size", 50f),
                stamp2VariableRotation = sharedPrefs.getBoolean("stamp2_variable_rotation", true),
                stamp2Rotation = sharedPrefs.getString("stamp2_rotation", "0") ?: "0",
                stamp2RotationTolerance = sharedPrefs.getString("stamp2_rotation_tolerance", "5") ?: "5",
                stamp2TranslationToleranceX = sharedPrefs.getString("stamp2_translation_tolerance_x", "0") ?: "0",
                stamp2TranslationToleranceY = sharedPrefs.getString("stamp2_translation_tolerance_y", "0") ?: "0"
            )
            firebaseManager.saveProfile(profile)
        }
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

    private fun saveCurrentSignatureToBase64(sizeX: Float, sizeY: Float, offsetX: String, offsetY: String) {
        val bitmap = binding.signatureCanvasView.getSignatureBitmap()
        if (bitmap == null) {
            Toast.makeText(context, "No hay firma para guardar.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val outputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            // Usar NO_WRAP para evitar problemas con separadores |
            val base64 = android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.NO_WRAP)
            // Codificar tamaño y offsets: base64:sizeX:sizeY:offsetX:offsetY
            val ox = if (offsetX.isBlank()) "0" else offsetX
            val oy = if (offsetY.isBlank()) "0" else offsetY
            val dataToSave = "$base64:$sizeX:$sizeY:$ox:$oy"

            withContext(Dispatchers.Main) {
                val currentBase64String = sharedPrefs.getString("signature_images_base64" + suffix, "") ?: ""
                val currentList = currentBase64String.split("|").filter { it.isNotBlank() }.toMutableList()

                if (editingSignatureIndex != -1 && editingSignatureIndex < currentList.size) {
                    currentList[editingSignatureIndex] = dataToSave
                    Toast.makeText(context, "Firma actualizada.", Toast.LENGTH_SHORT).show()
                } else {
                    currentList.add(dataToSave)
                    editingSignatureIndex = currentList.size - 1
                    Toast.makeText(context, "Firma guardada en la carpeta.", Toast.LENGTH_LONG).show()
                }

                val newBase64String = currentList.joinToString("|")
                sharedPrefs.edit().putString("signature_images_base64" + suffix, newBase64String).commit()
                saveCurrentProfileToFirebase()
            }
        }
    }

    private fun showSignatureFolderDialog() {
        val base64String = sharedPrefs.getString("signature_images_base64" + suffix, "") ?: ""
        val base64List = base64String.split("|").filter { it.isNotBlank() }.toMutableList()

        if (base64List.isEmpty()) {
            Toast.makeText(context, "No hay firmas guardadas.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_signature_picker, null)
        val container = dialogView.findViewById<android.widget.LinearLayout>(R.id.signature_container)

        // Usar un GridLayout para que parezca más una "carpeta"
        val gridLayout = android.widget.GridLayout(requireContext()).apply {
            columnCount = 2
            alignmentMode = android.widget.GridLayout.ALIGN_BOUNDS
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(gridLayout)

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle("Carpeta de Firmas")
            .setView(dialogView)
            .setNegativeButton("Cerrar", null)
            .create()

        base64List.forEachIndexed { index, data ->
            val itemLayout = android.widget.RelativeLayout(requireContext()).apply {
                val params = android.widget.GridLayout.LayoutParams().apply {
                    width = 0
                    height = android.widget.GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                    setMargins(8, 8, 8, 8)
                }
                layoutParams = params
                setBackgroundResource(R.drawable.dotted_border)
                setPadding(8, 8, 8, 8)
            }

            val imageView = android.widget.ImageView(requireContext()).apply {
                id = android.view.View.generateViewId()
                layoutParams = android.widget.RelativeLayout.LayoutParams(
                    android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                    150
                ).apply {
                    addRule(android.widget.RelativeLayout.CENTER_IN_PARENT)
                }
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER

                // Cargar preview (tomar solo la parte base64)
                val base64 = data.split(":")[0]
                try {
                    val decodedBytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
                    val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    setImageBitmap(bitmap)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                setOnClickListener {
                    loadSignatureFromBase64(data, index)
                    dialog.dismiss()
                }
            }

            // --- BOTÓN EDITAR ---
            val editButton = android.widget.ImageButton(requireContext()).apply {
                id = android.view.View.generateViewId()
                layoutParams = android.widget.RelativeLayout.LayoutParams(
                    60, 60
                ).apply {
                    addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP)
                    addRule(android.widget.RelativeLayout.ALIGN_PARENT_START)
                }
                setImageResource(android.R.drawable.ic_menu_edit)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER)
                setColorFilter(android.graphics.Color.BLUE)
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    loadSignatureFromBase64(data, index)
                    dialog.dismiss()
                }
            }

            // --- BOTÓN ELIMINAR ---
            val deleteButton = android.widget.ImageButton(requireContext()).apply {
                layoutParams = android.widget.RelativeLayout.LayoutParams(
                    60, 60
                ).apply {
                    addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP)
                    addRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
                }
                setImageResource(android.R.drawable.ic_menu_delete)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER)
                setColorFilter(android.graphics.Color.RED)
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    android.app.AlertDialog.Builder(requireContext())
                        .setTitle("Eliminar Firma")
                        .setMessage("¿Estás seguro de que quieres eliminar esta firma?")
                        .setPositiveButton("Eliminar") { _, _ ->
                            base64List.removeAt(index)
                            val newBase64String = base64List.joinToString("|")
                            sharedPrefs.edit().putString("signature_images_base64" + suffix, newBase64String).apply()
                            dialog.dismiss()
                            showSignatureFolderDialog() // Refrescar diálogo
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }
            }

            itemLayout.addView(imageView)
            itemLayout.addView(editButton)
            itemLayout.addView(deleteButton)
            gridLayout.addView(itemLayout)
        }
        dialog.show()
    }

    private fun loadSignatureFromBase64(data: String, index: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                editingSignatureIndex = index
                // Separar base64, tamaño y offsets
                val parts = data.split(":")
                val base64 = parts[0]
                val sizeX = if (parts.size > 1) parts[1].toFloatOrNull() else null
                val sizeY = if (parts.size > 2) parts[2].toFloatOrNull() else null
                val offsetX = if (parts.size > 3) parts[3] else null
                val offsetY = if (parts.size > 4) parts[4] else null

                val decodedBytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)

                if (bitmap != null) {
                    // Guardar en archivo local temporal para consistencia con el flujo de imagen
                    val file = java.io.File(requireContext().filesDir, "temp_sig_${System.currentTimeMillis()}.png")
                    java.io.FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    val localUri = Uri.fromFile(file)

                    withContext(Dispatchers.Main) {
                        tempImageUri = localUri.toString()
                        binding.signatureCanvasView.setSignatureBitmap(bitmap)

                        sizeX?.let {
                            binding.signatureSizeXEditText.setText(it.toString())
                        }

                        sizeY?.let {
                            binding.signatureSizeYEditText.setText(it.toString())
                        }

                        offsetX?.let {
                            binding.signatureOffsetXEditText.setText(it)
                        }

                        offsetY?.let {
                            binding.signatureOffsetYEditText.setText(it)
                        }

                        updateButtonLabels()
                        Toast.makeText(context, "Firma cargada para edición.", Toast.LENGTH_SHORT).show()
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
