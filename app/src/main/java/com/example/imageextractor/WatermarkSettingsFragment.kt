package com.example.imageextractor

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.imageextractor.databinding.FragmentWatermarkSettingsBinding
import com.google.android.material.button.MaterialButton

class WatermarkSettingsFragment : Fragment() {

    private var _binding: FragmentWatermarkSettingsBinding? = null
    private val binding get() = _binding!!

    private val sharedPrefs by lazy {
        requireActivity().getSharedPreferences("WatermarkSettings", Context.MODE_PRIVATE)
    }

    private var currentWatermark = 1
    private lateinit var buttons: List<Button>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWatermarkSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buttons = listOf(binding.buttonWatermark1, binding.buttonWatermark2, binding.buttonWatermark3, binding.buttonWatermark4)
        setupToolbar()
        setupTabButtons()
        loadSettingsForWatermark(currentWatermark)
    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.title = "Ajustes de Marca de Agua"
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    private fun setupTabButtons() {
        buttons.forEachIndexed { index, button ->
            button.setOnClickListener {
                saveSettingsForWatermark(currentWatermark)
                currentWatermark = index + 1
                loadSettingsForWatermark(currentWatermark)
            }
        }
    }

    private fun loadSettingsForWatermark(index: Int) {
        val defaultText = if (index == 1) "Certificado Literal" else ""
        val defaultOpacity = if (index == 1) "25" else "50"
        val defaultSize = if (index == 1) "114" else "72"
        val defaultDx = if (index == 1) "-15" else "0"
        val defaultDy = if (index == 1) "-14" else "0"
        val defaultAngle = if (index == 1) "-55" else "0"

        binding.watermark1TextEditText.setText(sharedPrefs.getString("w${index}_text", defaultText))
        binding.watermark1OpacityEditText.setText(sharedPrefs.getString("w${index}_opacity", defaultOpacity))
        binding.watermark1SizeEditText.setText(sharedPrefs.getString("w${index}_size", defaultSize))
        binding.watermark1ScaleEditText.setText(sharedPrefs.getString("w${index}_scale", "100"))
        binding.watermark1DxEditText.setText(sharedPrefs.getString("w${index}_dx", defaultDx))
        binding.watermark1DyEditText.setText(sharedPrefs.getString("w${index}_dy", defaultDy))
        binding.watermark1AngleEditText.setText(sharedPrefs.getString("w${index}_angle", defaultAngle))
    }

    private fun saveSettingsForWatermark(index: Int) {
        with(sharedPrefs.edit()) {
            putString("w${index}_text", binding.watermark1TextEditText.text.toString())
            putString("w${index}_opacity", binding.watermark1OpacityEditText.text.toString())
            putString("w${index}_size", binding.watermark1SizeEditText.text.toString())
            putString("w${index}_scale", binding.watermark1ScaleEditText.text.toString())
            putString("w${index}_dx", binding.watermark1DxEditText.text.toString())
            putString("w${index}_dy", binding.watermark1DyEditText.text.toString())
            putString("w${index}_angle", binding.watermark1AngleEditText.text.toString())
            apply()
        }
    }

    override fun onPause() {
        super.onPause()
        saveSettingsForWatermark(currentWatermark)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
