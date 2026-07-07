package com.example.imageextractor

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
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

    private var offsetX = 0f
    private var offsetY = 0f

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogGalleryFilterConfigBinding.inflate(LayoutInflater.from(context))

        loadSettings()

        setupListeners()

        return AlertDialog.Builder(requireContext())
            .setTitle("Configurar Filtro")
            .setView(binding.root)
            .setPositiveButton("Guardar") { _, _ ->
                saveSettings()
            }
            .setNegativeButton("Cancelar", null)
            .create()
    }

    private fun loadSettings() {
        val text = sharedPrefs.getString("gallery_filter_text", "CERTIFICADO LITERAL")
        val textSize = sharedPrefs.getFloat("gallery_filter_text_size", 11f)
        val color = sharedPrefs.getString("gallery_filter_color", "#000000")
        val lineSpacing = sharedPrefs.getFloat("gallery_filter_line_spacing", 1.0f)
        offsetX = sharedPrefs.getFloat("gallery_filter_offset_x", 0f)
        offsetY = sharedPrefs.getFloat("gallery_filter_offset_y", 0f)

        binding.editFilterText.setText(text)
        binding.seekTextSize.progress = textSize.toInt()
        binding.textSizeValue.text = "${textSize.toInt()}%"
        binding.editFilterColor.setText(color)
        binding.seekLineSpacing.progress = (lineSpacing * 100).toInt()
        binding.lineSpacingValue.text = String.format("%.1fx", lineSpacing)
        updateOffsetLabels()
    }

    private fun setupListeners() {
        binding.seekTextSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.textSizeValue.text = "$progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekLineSpacing.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress / 100f
                binding.lineSpacingValue.text = String.format("%.1fx", value)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.btnPosUp.setOnClickListener { offsetY -= 2f; updateOffsetLabels() }
        binding.btnPosDown.setOnClickListener { offsetY += 2f; updateOffsetLabels() }
        binding.btnPosLeft.setOnClickListener { offsetX -= 2f; updateOffsetLabels() }
        binding.btnPosRight.setOnClickListener { offsetX += 2f; updateOffsetLabels() }
    }

    private fun updateOffsetLabels() {
        binding.textOffsetX.text = "X: ${offsetX.toInt()}"
        binding.textOffsetY.text = "Y: ${offsetY.toInt()}"
    }

    private fun saveSettings() {
        sharedPrefs.edit().apply {
            putString("gallery_filter_text", binding.editFilterText.text.toString())
            putFloat("gallery_filter_text_size", binding.seekTextSize.progress.toFloat())
            putString("gallery_filter_color", binding.editFilterColor.text.toString())
            putFloat("gallery_filter_line_spacing", binding.seekLineSpacing.progress / 100f)
            putFloat("gallery_filter_offset_x", offsetX)
            putFloat("gallery_filter_offset_y", offsetY)
            apply()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
