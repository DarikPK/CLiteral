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
        binding.signatureEnabledCheckbox.isChecked = sharedPrefs.getBoolean("signature_enabled", false)
        binding.signatureScaleSlider.value = sharedPrefs.getFloat("signature_scale", 100f)
        binding.signatureRotationSlider.value = sharedPrefs.getFloat("signature_rotation", 0f)
        binding.signatureOffsetXEditText.setText(sharedPrefs.getString("signature_offset_x", "0"))
        binding.signatureOffsetYEditText.setText(sharedPrefs.getString("signature_offset_y", "0"))

        val numMarkers = sharedPrefs.getInt("signature_num_markers", 15)
        val markerSize = sharedPrefs.getFloat("signature_marker_size", 10f)

        numMarkersEditText.setText(numMarkers.toString())
        markerSizeEditText.setText(markerSize.toString())

        binding.signatureCanvasView.setNumMarkers(numMarkers)
        binding.signatureCanvasView.setMarkerRadius(markerSize)

        // Load wear settings
        val wearIntensity = sharedPrefs.getFloat("signature_wear_intensity", 0f)
        val wearSize = sharedPrefs.getFloat("signature_wear_size", 0f)
        binding.signatureWearIntensitySlider.value = wearIntensity
        binding.signatureWearSizeSlider.value = wearSize
        binding.signatureWearIntensityLabel.text = "Intensidad del Desgaste (${wearIntensity.toInt()})"
        binding.signatureWearSizeLabel.text = "Tamaño del Desgaste (${wearSize.toInt()})"

        // Load markers
        val markersString = sharedPrefs.getString("signature_markers", null)
        if (!markersString.isNullOrEmpty()) {
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
            binding.signatureCanvasView.setMarkerContours(contours)
        }
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
            val wearIntensity = sharedPrefs.getFloat("signature_wear_intensity", 0f)
            val wearSize = sharedPrefs.getFloat("signature_wear_size", 0f)

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

        binding.selectImageButton.setOnClickListener {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    permission
                ) == PackageManager.PERMISSION_GRANTED -> {
                    pickImageLauncher.launch("image/*")
                }
                shouldShowRequestPermissionRationale(permission) -> {
                    // Explain to the user why we need the permission
                    Toast.makeText(requireContext(), "Se necesita permiso para acceder a las imágenes.", Toast.LENGTH_LONG).show()
                    requestPermissionLauncher.launch(permission)
                }
                else -> {
                    requestPermissionLauncher.launch(permission)
                }
            }
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
            val numMarkers = text.toString().toIntOrNull() ?: 15
            saveInt("signature_num_markers", numMarkers)
            binding.signatureCanvasView.setNumMarkers(numMarkers)
        }

        markerSizeEditText.doOnTextChanged { text, _, _, _ ->
            val markerSize = text.toString().toFloatOrNull() ?: 10f
            saveFloat("signature_marker_size", markerSize)
            binding.signatureCanvasView.setMarkerRadius(markerSize)
        }

        binding.signatureWearIntensitySlider.addOnChangeListener { _, value, _ ->
            binding.signatureWearIntensityLabel.text = "Intensidad del Desgaste (${value.toInt()})"
            saveFloat("signature_wear_intensity", value)
            generateAndShowSignature()
        }

        binding.signatureWearSizeSlider.addOnChangeListener { _, value, _ ->
            binding.signatureWearSizeLabel.text = "Tamaño del Desgaste (${value.toInt()})"
            saveFloat("signature_wear_size", value)
            generateAndShowSignature()
        }
    }

    private fun saveMarkers() {
        val contours = binding.signatureCanvasView.getMarkerContours()
        val markersString = contours.joinToString("|") { contour ->
            contour.joinToString(";") { "${it.x},${it.y}" }
        }
        saveString("signature_markers", markersString)
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
