package com.example.imageextractor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PointF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.imageextractor.databinding.FragmentSignatureSettingsBinding

class SignatureSettingsFragment : Fragment() {

    private var _binding: FragmentSignatureSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var numMarkersEditText: com.google.android.material.textfield.TextInputEditText
    private lateinit var markerSizeEditText: com.google.android.material.textfield.TextInputEditText

    private var isPrimarySignatureSelected = true

    private val sharedPrefs by lazy {
        requireActivity().getSharedPreferences("PdfSettings", Context.MODE_PRIVATE)
    }

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
            // Persist the URI string
            saveString("signature_image_uri", it.toString())

            // Clear the procedural signature
            binding.signatureCanvasView.clearCanvas(switchMode = true)
            saveMarkers()

            Toast.makeText(requireContext(), "Imagen de firma seleccionada. Se ha borrado la firma dibujada.", Toast.LENGTH_LONG).show()
        }
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
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    private fun loadSettings() {
        // Load general settings that don't depend on signature type
        binding.signatureEnabledCheckbox.isChecked = sharedPrefs.getBoolean("signature_enabled", false)
        binding.signatureScaleSlider.value = sharedPrefs.getFloat("signature_scale", 100f)
        binding.signatureRotationSlider.value = sharedPrefs.getFloat("signature_rotation", 0f)
        binding.signatureOffsetXEditText.setText(sharedPrefs.getString("signature_offset_x", "0"))
        binding.signatureOffsetYEditText.setText(sharedPrefs.getString("signature_offset_y", "0"))

        // Load markers and specific settings for the initial selection (Primary)
        loadMarkersForCurrentSelection()
    }

    private fun loadMarkersForCurrentSelection() {
        // Load settings specific to the selected signature type
        val numMarkers = sharedPrefs.getInt(getKeyForSetting("signature_num_markers"), 40)
        val markerSize = sharedPrefs.getFloat(getKeyForSetting("signature_marker_size"), 5f)
        val randomRadius = sharedPrefs.getFloat(getKeyForSetting("signature_random_radius"), 20f)
        val wearIntensity = sharedPrefs.getFloat(getKeyForSetting("signature_wear_intensity"), 0f)
        val wearSize = sharedPrefs.getFloat(getKeyForSetting("signature_wear_size"), 0f)

        // Update UI components
        numMarkersEditText.setText(numMarkers.toString())
        markerSizeEditText.setText(markerSize.toString())
        binding.signatureRandomRadiusSlider.value = randomRadius
        binding.signatureRandomRadiusLabel.text = "Radio de Aleatoriedad (${randomRadius.toInt()})"
        binding.signatureWearIntensitySlider.value = wearIntensity
        binding.signatureWearIntensityLabel.text = "Intensidad del Desgaste (${wearIntensity.toInt()})"
        binding.signatureWearSizeSlider.value = wearSize
        binding.signatureWearSizeLabel.text = "Tamaño del Desgaste (${wearSize.toInt()})"

        // Update canvas view with these settings
        binding.signatureCanvasView.setNumMarkers(numMarkers)
        binding.signatureCanvasView.setMarkerRadius(markerSize)
        binding.signatureCanvasView.setRandomizationRadius(randomRadius)

        // Load marker points
        val key = getKeyForSetting("signature_markers")
        val markersString = sharedPrefs.getString(key, null)
        val contours = if (!markersString.isNullOrEmpty()) {
            markersString.split("|").map { contourString ->
                contourString.split(";").mapNotNull {
                    val parts = it.split(",")
                    if (parts.size == 2) PointF(parts[0].toFloat(), parts[1].toFloat()) else null
                }
            }
        } else {
            emptyList()
        }
        binding.signatureCanvasView.setMarkerContours(contours)
        updateButtonLabels()
        generateAndShowSignature()
    }

    private fun updateButtonLabels() {
        if (binding.signatureCanvasView.mode == SignatureCanvasView.Mode.DRAW) {
            binding.primaryActionButton.text = "Generar Marcadores"
        } else {
            binding.primaryActionButton.text = "Refrescar Firma"
        }
    }

    private fun generateAndShowSignature() {
        // Generate the base procedural signature
        var signatureBitmap = binding.signatureCanvasView.generateProceduralSignatureBitmap()

        // Apply wear and tear if the bitmap is not null
        if (signatureBitmap != null) {
            val wearIntensity = binding.signatureWearIntensitySlider.value
            val wearSize = binding.signatureWearSizeSlider.value

            if (wearIntensity > 0 && wearSize > 0) {
                val normalizedIntensity = wearIntensity / 100.0f
                val normalizedSize = wearSize / 100.0f
                val seed = System.currentTimeMillis()
                val wornBitmap = applyInkWear(signatureBitmap, normalizedIntensity, normalizedSize, seed)
                // The original bitmap is replaced by the worn one
                signatureBitmap = wornBitmap
            }
        }
        // Update the canvas view with the (potentially worn) signature
        binding.signatureCanvasView.setPreviewBitmap(signatureBitmap)
    }


    private fun setupListeners() {
        binding.primaryActionButton.setOnClickListener {
            if (binding.signatureCanvasView.mode == SignatureCanvasView.Mode.DRAW) {
                // We are in DRAW mode, so the button is "Generate Markers"
                if (binding.signatureCanvasView.getDrawingPath().isEmpty) {
                    Toast.makeText(requireContext(), "Por favor, dibuje una firma primero", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                binding.signatureCanvasView.switchToEditMode()
                updateButtonLabels()
                saveMarkers()
                generateAndShowSignature() // Generate preview after creating markers
            } else {
                // We are in EDIT mode, so the button is "Refresh Signature"
                generateAndShowSignature()
            }
        }

        binding.secondaryActionButton.setOnClickListener {
            // This button is always "Clear Canvas"
            binding.signatureCanvasView.clearCanvas(switchMode = true)
            updateButtonLabels()
            saveMarkers()
            generateAndShowSignature()
        }

        // Setup Spinner
        val signatureTypes = listOf("Firma Principal", "Firma Secundaria")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, signatureTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.signatureSelectionSpinner.adapter = adapter

        binding.signatureSelectionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val isPrimary = position == 0
                if (isPrimary != isPrimarySignatureSelected) {
                    saveMarkers() // Save current canvas before switching
                    isPrimarySignatureSelected = isPrimary
                    loadMarkersForCurrentSelection()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.signatureCanvasView.setMarkerListener {
            saveMarkers()
        }

        binding.signatureEnabledCheckbox.setOnCheckedChangeListener { _, isChecked -> saveBoolean("signature_enabled", isChecked) }
        binding.signatureScaleSlider.addOnChangeListener { _, value, _ -> saveFloat("signature_scale", value) }
        binding.signatureRotationSlider.addOnChangeListener { _, value, _ -> saveFloat("signature_rotation", value) }
        binding.signatureOffsetXEditText.doOnTextChanged { text, _, _, _ -> saveString("signature_offset_x", text.toString()) }
        binding.signatureOffsetYEditText.doOnTextChanged { text, _, _, _ -> saveString("signature_offset_y", text.toString()) }

        numMarkersEditText.doOnTextChanged { text, _, _, _ ->
            val numMarkers = text.toString().toIntOrNull() ?: 40
            saveInt(getKeyForSetting("signature_num_markers"), numMarkers)
            binding.signatureCanvasView.setNumMarkers(numMarkers)
        }

        markerSizeEditText.doOnTextChanged { text, _, _, _ ->
            val markerSize = text.toString().toFloatOrNull() ?: 5f
            saveFloat(getKeyForSetting("signature_marker_size"), markerSize)
            binding.signatureCanvasView.setMarkerRadius(markerSize)
        }

        binding.signatureRandomRadiusSlider.addOnChangeListener { _, value, _ ->
            binding.signatureRandomRadiusLabel.text = "Radio de Aleatoriedad (${value.toInt()})"
            saveFloat(getKeyForSetting("signature_random_radius"), value)
            binding.signatureCanvasView.setRandomizationRadius(value)
        }

        binding.signatureWearIntensitySlider.addOnChangeListener { _, value, _ ->
            binding.signatureWearIntensityLabel.text = "Intensidad del Desgaste (${value.toInt()})"
            saveFloat(getKeyForSetting("signature_wear_intensity"), value)
            generateAndShowSignature()
        }

        binding.signatureWearSizeSlider.addOnChangeListener { _, value, _ ->
            binding.signatureWearSizeLabel.text = "Tamaño del Desgaste (${value.toInt()})"
            saveFloat(getKeyForSetting("signature_wear_size"), value)
            generateAndShowSignature()
        }
    }

    private fun getKeyForSetting(baseKey: String): String {
        return if (isPrimarySignatureSelected) "${baseKey}_primary" else "${baseKey}_secondary"
    }

    private fun saveMarkers() {
        val key = getKeyForSetting("signature_markers")
        val contours = binding.signatureCanvasView.getMarkerContours()
        val markersString = contours.joinToString("|") { contour ->
            contour.joinToString(";") { "${it.x},${it.y}" }
        }
        saveString(key, markersString)
    }

    // SharedPreferences helpers
    private fun saveString(key: String, value: String) {
        sharedPrefs.edit().putString(key, value).apply()
    }

    private fun saveBoolean(key: String, value: Boolean) {
        sharedPrefs.edit().putBoolean(key, value).apply()
    }

    private fun saveFloat(key: String, value: Float) {
        sharedPrefs.edit().putFloat(key, value).apply()
    }

    private fun saveInt(key: String, value: Int) {
        sharedPrefs.edit().putInt(key, value).apply()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
