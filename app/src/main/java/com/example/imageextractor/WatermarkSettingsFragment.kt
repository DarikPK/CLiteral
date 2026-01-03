package com.example.imageextractor

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.imageextractor.databinding.FragmentWatermarkSettingsBinding

class WatermarkSettingsFragment : Fragment() {

    private var _binding: FragmentWatermarkSettingsBinding? = null
    private val binding get() = _binding!!

    private val sharedPrefs by lazy {
        requireActivity().getSharedPreferences("WatermarkSettings", Context.MODE_PRIVATE)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWatermarkSettingsBinding.inflate(inflater, container, false)
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
        (activity as? AppCompatActivity)?.supportActionBar?.title = "Ajustes de Marca de Agua"
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupListeners() {
        binding.watermark1TextEditText.doOnTextChanged { text, _, _, _ -> saveString("w1_text", text.toString()) }
        binding.watermark1OpacityEditText.doOnTextChanged { text, _, _, _ -> saveString("w1_opacity", text.toString()) }
        binding.watermark1SizeEditText.doOnTextChanged { text, _, _, _ -> saveString("w1_size", text.toString()) }
        binding.watermark1ScaleEditText.doOnTextChanged { text, _, _, _ -> saveString("w1_scale", text.toString()) }
        binding.watermark1DxEditText.doOnTextChanged { text, _, _, _ -> saveString("w1_dx", text.toString()) }
        binding.watermark1DyEditText.doOnTextChanged { text, _, _, _ -> saveString("w1_dy", text.toString()) }
        binding.watermark1AngleEditText.doOnTextChanged { text, _, _, _ -> saveString("w1_angle", text.toString()) }
    }

    private fun loadSettings() {
        binding.watermark1TextEditText.setText(sharedPrefs.getString("w1_text", ""))
        binding.watermark1OpacityEditText.setText(sharedPrefs.getString("w1_opacity", "50"))
        binding.watermark1SizeEditText.setText(sharedPrefs.getString("w1_size", "72"))
        binding.watermark1ScaleEditText.setText(sharedPrefs.getString("w1_scale", "100"))
        binding.watermark1DxEditText.setText(sharedPrefs.getString("w1_dx", "0"))
        binding.watermark1DyEditText.setText(sharedPrefs.getString("w1_dy", "0"))
        binding.watermark1AngleEditText.setText(sharedPrefs.getString("w1_angle", "0"))
    }

    private fun saveString(key: String, value: String) {
        sharedPrefs.edit().putString(key, value).apply()
    }
}
