package com.example.imageextractor

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.imageextractor.databinding.DialogGalleryFilterConfigBinding

class GalleryFilterConfigDialogFragment : DialogFragment() {

    private var _binding: DialogGalleryFilterConfigBinding? = null
    private val binding get() = _binding!!

    private val sharedPrefs by lazy {
        requireActivity().getSharedPreferences("PdfSettings", Context.MODE_PRIVATE)
    }

    private var currentElementIndex = 0
    private val elementPrefixes = arrayOf("text", "patch_top", "patch_bottom", "patch_side")

    // State for current selected element
    private var offsetX = 0f
    private var offsetY = 0f
    private var widthPercent = 0f
    private var heightPercent = 0f

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogGalleryFilterConfigBinding.inflate(LayoutInflater.from(context))

        setupSpinner()
        setupListeners()

        // Load initial element (Text)
        loadSettingsForElement(0)

        return AlertDialog.Builder(requireContext())
            .setTitle("Configurar Filtro")
            .setView(binding.root)
            .setPositiveButton("Guardar") { _, _ ->
                saveSettingsForElement(currentElementIndex)
            }
            .setNegativeButton("Cancelar", null)
            .create()
    }

    private fun setupSpinner() {
        binding.spinnerElementType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                saveSettingsForElement(currentElementIndex) // Guardar previo
                currentElementIndex = position
                loadSettingsForElement(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }


    private fun loadSettingsForElement(index: Int) {
        val prefix = "gallery_filter_${elementPrefixes[index]}"

        // Visibility
        binding.containerTextSettings.visibility = if (index == 0) View.VISIBLE else View.GONE
        binding.containerHeightPercent.visibility = if (index != 3) View.VISIBLE else View.VISIBLE // All have height except maybe lateral? No, all have it.

        binding.labelSizeMain.text = if (index == 0) "Tamaño del Texto (%)" else "Ancho (%)"

        if (index == 0) {
            binding.editFilterText.setText(sharedPrefs.getString("${prefix}_content", "CERTIFICADO LITERAL"))
            binding.editFilterColor.setText(sharedPrefs.getString("${prefix}_color", "#000000"))
            val spacing = sharedPrefs.getFloat("${prefix}_line_spacing", 1.0f)
            binding.seekLineSpacing.progress = (spacing * 100).toInt()
            binding.lineSpacingValue.text = String.format("%.1fx", spacing)
        }

        offsetX = sharedPrefs.getFloat("${prefix}_offset_x", 0f)
        offsetY = sharedPrefs.getFloat("${prefix}_offset_y", 0f)

        val defaultW = when(index) {
            0 -> 32f
            1 -> 32f
            2 -> 100f
            3 -> 4.7f // 95.5 - 90.8
            else -> 0f
        }
        val defaultH = when(index) {
            0 -> 18f
            1 -> 18f
            2 -> 6.5f
            3 -> 24f // 42 - 18
            else -> 0f
        }

        widthPercent = sharedPrefs.getFloat("${prefix}_width_percent", defaultW)
        heightPercent = sharedPrefs.getFloat("${prefix}_height_percent", defaultH)

        binding.seekWidthPercent.progress = (widthPercent * 10).toInt()
        binding.widthPercentValue.text = String.format("%.1f%%", widthPercent)

        binding.seekHeightPercent.progress = (heightPercent * 10).toInt()
        binding.heightPercentValue.text = String.format("%.1f%%", heightPercent)

        updateOffsetLabels()
    }

    private fun setupListeners() {
        binding.seekLineSpacing.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress / 100f
                binding.lineSpacingValue.text = String.format("%.1fx", value)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekWidthPercent.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                widthPercent = progress / 10.0f
                binding.widthPercentValue.text = String.format("%.1f%%", widthPercent)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekHeightPercent.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                heightPercent = progress / 10.0f
                binding.heightPercentValue.text = String.format("%.1f%%", heightPercent)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.btnPosUp.setOnClickListener { offsetY -= 1f; updateOffsetLabels() }
        binding.btnPosDown.setOnClickListener { offsetY += 1f; updateOffsetLabels() }
        binding.btnPosLeft.setOnClickListener { offsetX -= 1f; updateOffsetLabels() }
        binding.btnPosRight.setOnClickListener { offsetX += 1f; updateOffsetLabels() }
    }

    private fun updateOffsetLabels() {
        binding.textOffsetX.text = "X: ${offsetX.toInt()}"
        binding.textOffsetY.text = "Y: ${offsetY.toInt()}"
    }

    private fun saveSettingsForElement(index: Int) {
        val prefix = "gallery_filter_${elementPrefixes[index]}"
        sharedPrefs.edit().apply {
            if (index == 0) {
                putString("${prefix}_content", binding.editFilterText.text.toString())
                putString("${prefix}_color", binding.editFilterColor.text.toString())
                putFloat("${prefix}_line_spacing", binding.seekLineSpacing.progress / 100f)
            }
            putFloat("${prefix}_offset_x", offsetX)
            putFloat("${prefix}_offset_y", offsetY)
            putFloat("${prefix}_width_percent", widthPercent)
            putFloat("${prefix}_height_percent", heightPercent)
            apply()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
