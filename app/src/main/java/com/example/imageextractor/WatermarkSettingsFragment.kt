package com.example.imageextractor

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
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
        // Get theme colors
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)
        val colorPrimary = typedValue.data
        requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
        val colorOnPrimary = typedValue.data

        buttons.forEachIndexed { i, button ->
            val materialButton = button as MaterialButton
            if (i + 1 == index) {
                // Style for selected button (filled)
                materialButton.backgroundTintList = ColorStateList.valueOf(colorPrimary)
                materialButton.setTextColor(colorOnPrimary)
                materialButton.strokeWidth = 0
            } else {
                // Style for unselected buttons (outlined)
                materialButton.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                materialButton.setTextColor(colorPrimary)
                materialButton.strokeColor = ColorStateList.valueOf(colorPrimary)
                materialButton.strokeWidth = 2
            }
        }

        val showWatermark2Controls = index == 2
        binding.textAlignRadioGroup.visibility = if (showWatermark2Controls) View.VISIBLE else View.GONE
        binding.watermarkRightCropInputLayout.visibility = if (showWatermark2Controls) View.VISIBLE else View.GONE

        val defaultText = when (index) {
            1 -> "Certificado Literal"
            2 -> "Sin inscripcion al Dorso\nNo hay Títulos Suspendidos y/o Pendientes de Inscripci\nA las Horas : 8:00 AM"
            3 -> "PUBLICIDAD : \"Número publicidad\" Recibo N° \"Año\"-\"Digito 1\"-\"Digito 2\" Partida N° \"número partida\" CERTI. LITERAL - \"Tipo partida\""
            4 -> "Pág. Solicitadas : Todas  IMPRESION :  \"fecha\" \"Hora\" Página \"x\" de \"y\"\nNo existen Títulos Pendientes y/o Suspendidos  Inmovilización: Ninguno"
            else -> ""
        }
        val defaultOpacity = when (index) {
            1 -> "25"
            2 -> "25"
            3 -> "90"
            4 -> "80"
            else -> "50"
        }
        val defaultSize = when (index) {
            1 -> "114"
            2 -> "71"
            3 -> "23"
            4 -> "15"
            else -> "72"
        }
        val defaultScale = when (index) {
            2 -> "80"
            3 -> "90"
            4 -> "100"
            else -> "100"
        }
        val defaultDx = when (index) {
            1 -> "-15"
            2 -> "29"
            3 -> "0"
            4 -> "165"
            else -> "0"
        }
        val defaultDy = when (index) {
            1 -> "-14"
            2 -> "19"
            3 -> "-232"
            4 -> "0"
            else -> "0"
        }
        val defaultAngle = when (index) {
            1 -> "-55"
            2 -> "55"
            3 -> "0"
            4 -> "-90"
            else -> "0"
        }

        // Update title
        binding.watermarkTitle.text = "Marca de Agua $index"

        binding.watermark1TextEditText.setText(sharedPrefs.getString("w${index}_text", defaultText))
        binding.watermark1OpacityEditText.setText(sharedPrefs.getString("w${index}_opacity", defaultOpacity))
        binding.watermark1SizeEditText.setText(sharedPrefs.getString("w${index}_size", defaultSize))
        binding.watermark1ScaleEditText.setText(sharedPrefs.getString("w${index}_scale", defaultScale))
        binding.watermark1DxEditText.setText(sharedPrefs.getString("w${index}_dx", defaultDx))
        binding.watermark1DyEditText.setText(sharedPrefs.getString("w${index}_dy", defaultDy))
        binding.watermark1AngleEditText.setText(sharedPrefs.getString("w${index}_angle", defaultAngle))

        if (index == 2) {
            binding.watermarkRightCropEditText.setText(sharedPrefs.getString("w2_right_crop", "319"))
            val align = sharedPrefs.getInt("w2_align", 1) // 1 = Center default
            when (align) {
                0 -> binding.alignLeftRadioButton.isChecked = true
                1 -> binding.alignCenterRadioButton.isChecked = true
                2 -> binding.alignRightRadioButton.isChecked = true
            }
        }
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

            if (index == 2) {
                val align = when {
                    binding.alignLeftRadioButton.isChecked -> 0
                    binding.alignCenterRadioButton.isChecked -> 1
                    binding.alignRightRadioButton.isChecked -> 2
                    else -> 1 // Default to center
                }
                putInt("w2_align", align)
                putString("w2_right_crop", binding.watermarkRightCropEditText.text.toString())
            }
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
