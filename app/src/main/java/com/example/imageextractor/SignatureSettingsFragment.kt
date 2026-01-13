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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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
        binding.signatureOffsetXEditText.setText(sharedPrefs.getString("signature_offset_x", "0"))
        binding.signatureOffsetYEditText.setText(sharedPrefs.getString("signature_offset_y", "0"))

        // Load markers
        val markersJson = sharedPrefs.getString("signature_markers", null)
        if (markersJson != null) {
            val type = object : TypeToken<List<PointF>>() {}.type
            val markers: List<PointF> = Gson().fromJson(markersJson, type)
            binding.signatureCanvasView.setMarkers(markers)
        }
    }

    private fun setupListeners() {
        binding.clearMarkersButton.setOnClickListener {
            binding.signatureCanvasView.clearMarkers()
            saveMarkers()
        }

        binding.refreshSignatureButton.setOnClickListener {
            binding.signatureCanvasView.regenerateSignature()
        }

        binding.signatureCanvasView.setMarkerListener {
            saveMarkers()
        }

        binding.signatureEnabledCheckbox.setOnCheckedChangeListener { _, isChecked -> saveBoolean("signature_enabled", isChecked) }
        binding.signatureScaleSlider.addOnChangeListener { _, value, _ -> saveFloat("signature_scale", value) }
        binding.signatureRotationSlider.addOnChangeListener { _, value, _ -> saveFloat("signature_rotation", value) }
        binding.signatureOffsetXEditText.doOnTextChanged { text, _, _, _ -> saveString("signature_offset_x", text.toString()) }
        binding.signatureOffsetYEditText.doOnTextChanged { text, _, _, _ -> saveString("signature_offset_y", text.toString()) }
    }

    private fun saveMarkers() {
        val markers = binding.signatureCanvasView.getMarkers()
        val json = Gson().toJson(markers)
        saveString("signature_markers", json)
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
