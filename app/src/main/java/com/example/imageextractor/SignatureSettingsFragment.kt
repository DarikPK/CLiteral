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
            pickImageLauncher.launch(arrayOf("image/*"))
        } else {
            Toast.makeText(requireContext(), "Permiso denegado. No se puede seleccionar imagen.", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val contentResolver = requireContext().contentResolver
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
            try {
                contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            showImportSettingsDialog(it)
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
                try {
                    val contentResolver = requireContext().contentResolver
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

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
            saveBoolean("signature_type_loaded" + suffix, isChecked)
            updateButtonLabels()
        }

        binding.loadedSignaturesButton.setOnClickListener {
            val urisString = sharedPrefs.getString("signature_image_uris" + suffix, "") ?: ""
            val uris = urisString.split("|").filter { it.isNotBlank() }
            if (uris.isEmpty()) {
                Toast.makeText(context, "No hay firmas cargadas para este registrador.", Toast.LENGTH_SHORT).show()
            } else {
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Firmas Cargadas")
                    .setItems(uris.toTypedArray()) { _, which ->
                        val selectedUri = uris[which]
                        saveString("signature_image_uri" + suffix, selectedUri)
                        loadAndDisplaySignatureImage(Uri.parse(selectedUri), sharedPrefs.getFloat("signature_white_threshold" + suffix, 210f))
                        updateButtonLabels()
                    }
                    .setNegativeButton("Cerrar", null)
                    .show()
            }
        }

        binding.selectImageButton.setOnClickListener {
            pickImageLauncher.launch(arrayOf("image/*"))
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

    private fun saveProfileToFirebase() {
        lifecycleScope.launch {
            val name = sharedPrefs.getString("stamp2_name", "Desconocido") ?: "Desconocido"
            val position = sharedPrefs.getString("stamp2_position", "") ?: ""
            val area = sharedPrefs.getString("stamp2_area", "") ?: ""
            val office = sharedPrefs.getString("oficina", "LIMA") ?: "LIMA"

            val signatureMarkers = sharedPrefs.getString("signature_markers", "") ?: ""

            val profile = RegistrarProfile(
                id = sharedPrefs.getString("firebase_profile_id", "") ?: "",
                name = name,
                position = position,
                area = area,
                office = office,
                signatureMarkers = signatureMarkers,
                signatureRotationTolerance = sharedPrefs.getFloat("signature_rotation_tolerance", 0f),
                signatureWhiteThreshold = sharedPrefs.getFloat("signature_white_threshold", 210f),
                stamp2FontSize = sharedPrefs.getString("stamp2_font_size", "13") ?: "13",
                stamp2WearIntensity = sharedPrefs.getFloat("stamp2_wear_intensity", 30f),
                stamp2WearSize = sharedPrefs.getFloat("stamp2_wear_size", 50f)
            )

            val result = firebaseManager.saveProfile(profile)
            if (result.isSuccess) {
                Toast.makeText(requireContext(), "Perfil guardado en la nube", Toast.LENGTH_SHORT).show()
            } else {
                val error = result.exceptionOrNull()
                val message = error?.localizedMessage ?: error?.message ?: "Error desconocido"
                Toast.makeText(requireContext(), "Error al guardar: $message", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showLoadProfilesDialog() {
        lifecycleScope.launch {
            val result = firebaseManager.getAllProfiles()
            if (result.isSuccess) {
                val profiles = result.getOrNull() ?: emptyList()
                if (profiles.isEmpty()) {
                    Toast.makeText(requireContext(), "No hay perfiles en la nube", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val profileNames = profiles.map { "${it.name} (${it.office})" }.toTypedArray()
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Cargar Perfil")
                    .setItems(profileNames) { _, which ->
                        loadProfileIntoSettings(profiles[which])
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            } else {
                val error = result.exceptionOrNull()
                val message = error?.localizedMessage ?: error?.message ?: "Error desconocido"
                Toast.makeText(requireContext(), "Error al cargar: $message", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadProfileIntoSettings(profile: RegistrarProfile) {
        sharedPrefs.edit().apply {
            putString("firebase_profile_id", profile.id)
            putString("stamp2_name", profile.name)
            putString("stamp2_position", profile.position)
            putString("stamp2_area", profile.area)
            putString("oficina", profile.office)
            putString("signature_markers", profile.signatureMarkers)
            putFloat("signature_rotation_tolerance", profile.signatureRotationTolerance)
            putFloat("signature_white_threshold", profile.signatureWhiteThreshold)
            putString("stamp2_font_size", profile.stamp2FontSize)
            putFloat("stamp2_wear_intensity", profile.stamp2WearIntensity)
            putFloat("stamp2_wear_size", profile.stamp2WearSize)
            apply()
        }

        // Reload settings in UI
        loadSettings()
        Toast.makeText(requireContext(), "Perfil de ${profile.name} cargado", Toast.LENGTH_SHORT).show()
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
        // Al guardar offsets desde la UI, actualizar también SharedPreferences directamente
        // para que PdfPreviewFragment los vea de inmediato
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
