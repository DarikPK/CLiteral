package com.example.imageextractor

import android.content.Context
import android.graphics.PointF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.imageextractor.databinding.FragmentSignatureSettingsBinding

class SignatureSettingsFragment : Fragment() {

    private var _binding: FragmentSignatureSettingsBinding? = null
    private val binding get() = _binding!!

    private val sharedPrefs by lazy {
        requireActivity().getSharedPreferences("PdfSettings", Context.MODE_PRIVATE)
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
        binding.signatureRandomRadiusSlider.value = sharedPrefs.getFloat("signature_random_radius", 20f)
        binding.signatureOffsetXEditText.setText(sharedPrefs.getString("signature_offset_x", "0"))
        binding.signatureOffsetYEditText.setText(sharedPrefs.getString("signature_offset_y", "0"))

        // Load markers
        val markersString = sharedPrefs.getString("signature_markers", null)
        if (!markersString.isNullOrEmpty()) {
            val markers = markersString.split(";").mapNotNull {
                val parts = it.split(",")
                if (parts.size == 2) {
                    PointF(parts[0].toFloat(), parts[1].toFloat())
                } else {
                    null
                }
            }
            binding.signatureCanvasView.setMarkers(markers)
        }
        binding.signatureCanvasView.setRandomizationRadius(binding.signatureRandomRadiusSlider.value)
        updateButtonLabels()
    }

    private fun updateButtonLabels() {
        if (binding.signatureCanvasView.mode == SignatureCanvasView.Mode.DRAW) {
            binding.primaryActionButton.text = "Generar Marcadores"
        } else {
            binding.primaryActionButton.text = "Refrescar Firma"
        }
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
            } else {
                // We are in EDIT mode, so the button is "Refresh Signature"
                binding.signatureCanvasView.regenerateSignature()
            }
        }

        binding.secondaryActionButton.setOnClickListener {
            // This button is always "Clear Canvas"
            binding.signatureCanvasView.clearCanvas(switchMode = true)
            updateButtonLabels()
            saveMarkers()
        }


        binding.signatureCanvasView.setMarkerListener {
            saveMarkers()
        }

        binding.signatureEnabledCheckbox.setOnCheckedChangeListener { _, isChecked -> saveBoolean("signature_enabled", isChecked) }
        binding.signatureScaleSlider.addOnChangeListener { _, value, _ -> saveFloat("signature_scale", value) }
        binding.signatureRotationSlider.addOnChangeListener { _, value, _ -> saveFloat("signature_rotation", value) }
        binding.signatureRandomRadiusSlider.addOnChangeListener { _, value, _ ->
            saveFloat("signature_random_radius", value)
            binding.signatureCanvasView.setRandomizationRadius(value)
        }
        binding.signatureOffsetXEditText.doOnTextChanged { text, _, _, _ -> saveString("signature_offset_x", text.toString()) }
        binding.signatureOffsetYEditText.doOnTextChanged { text, _, _, _ -> saveString("signature_offset_y", text.toString()) }
    }

    private fun saveMarkers() {
        val markers = binding.signatureCanvasView.getMarkers()
        val markersString = markers.joinToString(";") { "${it.x},${it.y}" }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
