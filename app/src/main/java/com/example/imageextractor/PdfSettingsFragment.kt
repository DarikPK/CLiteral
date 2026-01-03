package com.example.imageextractor

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.imageextractor.databinding.FragmentPdfSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.content.ContentUris
import android.provider.MediaStore
import com.google.android.material.slider.Slider

class PdfSettingsFragment : Fragment() {

    private var _binding: FragmentPdfSettingsBinding? = null
    private val binding get() = _binding!!

    private val sharedPrefs by lazy {
        requireActivity().getSharedPreferences("PdfSettings", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPdfSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupMonthSpinner()
        loadSettings()
        setupListeners()
    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.title = "Generar PDF"
    }

    private fun setupMonthSpinner() {
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.months_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.stampMonthSpinner.adapter = adapter
        }
    }

    private fun setupListeners() {
        // Auto-save for all EditTexts
        binding.brightnessEditText.doOnTextChanged { text, _, _, _ -> saveString("brightness", text.toString()) }
        binding.contrastEditText.doOnTextChanged { text, _, _, _ -> saveString("contrast", text.toString()) }
        binding.marginTopEditText.doOnTextChanged { text, _, _, _ -> saveString("margin_top", text.toString()) }
        binding.marginBottomEditText.doOnTextChanged { text, _, _, _ -> saveString("margin_bottom", text.toString()) }
        binding.marginLeftEditText.doOnTextChanged { text, _, _, _ -> saveString("margin_left", text.toString()) }
        binding.marginRightEditText.doOnTextChanged { text, _, _, _ -> saveString("margin_right", text.toString()) }
        binding.stampDayEditText.doOnTextChanged { text, _, _, _ -> saveString("stamp_day", text.toString()) }
        binding.stampYearEditText.doOnTextChanged { text, _, _, _ -> saveString("stamp_year", text.toString()) }
        binding.stampFontSizeEditText.doOnTextChanged { text, _, _, _ -> saveString("stamp_font_size", text.toString()) }
        binding.stampSizeEditText.doOnTextChanged { text, _, _, _ -> saveString("stamp_size", text.toString()) }
        binding.stampRotationEditText.doOnTextChanged { text, _, _, _ -> saveString("stamp_rotation", text.toString()) }
        binding.stampBrightnessEditText.doOnTextChanged { text, _, _, _ -> saveString("stamp_brightness", text.toString()) }
        binding.stampContrastEditText.doOnTextChanged { text, _, _, _ -> saveString("stamp_contrast", text.toString()) }

        // Auto-save for CheckBox
        binding.stampEnabledCheckbox.setOnCheckedChangeListener { _, isChecked -> saveBoolean("stamp_enabled", isChecked) }

        // Auto-save for Sliders
        binding.stampWearIntensitySlider.addOnChangeListener(Slider.OnChangeListener { _, value, _ -> saveFloat("stamp_wear_intensity", value) })
        binding.stampWearSizeSlider.addOnChangeListener(Slider.OnChangeListener { _, value, _ -> saveFloat("stamp_wear_size", value) })

        // Auto-save for Spinner
        binding.stampMonthSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                saveInt("stamp_month_position", position)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun loadSettings() {
        binding.brightnessEditText.setText(sharedPrefs.getString("brightness", "30"))
        binding.contrastEditText.setText(sharedPrefs.getString("contrast", "100"))
        binding.marginTopEditText.setText(sharedPrefs.getString("margin_top", "10"))
        binding.marginBottomEditText.setText(sharedPrefs.getString("margin_bottom", "10"))
        binding.marginLeftEditText.setText(sharedPrefs.getString("margin_left", "10"))
        binding.marginRightEditText.setText(sharedPrefs.getString("margin_right", "10"))
        binding.stampDayEditText.setText(sharedPrefs.getString("stamp_day", "1"))
        binding.stampYearEditText.setText("2026")
        binding.stampFontSizeEditText.setText(sharedPrefs.getString("stamp_font_size", "220"))
        binding.stampSizeEditText.setText(sharedPrefs.getString("stamp_size", "20"))
        binding.stampRotationEditText.setText(sharedPrefs.getString("stamp_rotation", "5"))
        binding.stampBrightnessEditText.setText(sharedPrefs.getString("stamp_brightness", "50"))
        binding.stampContrastEditText.setText(sharedPrefs.getString("stamp_contrast", "50"))

        binding.stampEnabledCheckbox.isChecked = sharedPrefs.getBoolean("stamp_enabled", true)

        binding.stampWearIntensitySlider.value = sharedPrefs.getFloat("stamp_wear_intensity", 30f)
        binding.stampWearSizeSlider.value = sharedPrefs.getFloat("stamp_wear_size", 50f)

        binding.stampMonthSpinner.setSelection(sharedPrefs.getInt("stamp_month_position", 0))
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


    private fun validateAndProceed() {
        if (binding.stampEnabledCheckbox.isChecked && binding.stampDayEditText.text.toString().isBlank()) {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Campo Requerido")
                .setMessage("Por favor, ingrese un día para el sello antes de continuar.")
                .setPositiveButton("Aceptar", null)
                .show()
            return
        }
        showPartidaSelectionDialog()
    }

    private fun showPartidaSelectionDialog() {
        lifecycleScope.launch(Dispatchers.IO) {
            val folders = getCapturedFolders()
            withContext(Dispatchers.Main) {
                if (folders.isEmpty()) {
                    Toast.makeText(context, "No se encontraron partidas capturadas.", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                val partidaIds = folders.map { it.partidaId }.toTypedArray()
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Seleccionar Partida")
                    .setItems(partidaIds) { _, which ->
                        val selectedFolder = folders[which]
                        navigateToPreview(selectedFolder)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
    }

    private fun navigateToPreview(folder: ImageFolder) {
        val bundle = Bundle().apply {
            putString("partidaId", folder.partidaId)
            putStringArray("imagePaths", folder.imageFiles.map { it.path }.toTypedArray())

            putFloat("brightness", binding.brightnessEditText.text.toString().toFloatOrNull() ?: 30f)
            putFloat("contrast", binding.contrastEditText.text.toString().toFloatOrNull() ?: 100f)

            // Pasar los valores de los márgenes
            putFloat("marginTop", binding.marginTopEditText.text.toString().toFloatOrNull() ?: 0f)
            putFloat("marginBottom", binding.marginBottomEditText.text.toString().toFloatOrNull() ?: 0f)
            putFloat("marginLeft", binding.marginLeftEditText.text.toString().toFloatOrNull() ?: 0f)
            putFloat("marginRight", binding.marginRightEditText.text.toString().toFloatOrNull() ?: 0f)

            putBoolean("isStampEnabled", binding.stampEnabledCheckbox.isChecked)
            if (binding.stampEnabledCheckbox.isChecked) {
                val dayInt = binding.stampDayEditText.text.toString().toIntOrNull()
                val stampDay = dayInt?.let { String.format("%02d", it) } ?: ""
                val stampMonth = binding.stampMonthSpinner.selectedItem.toString()
                val stampYear = binding.stampYearEditText.text.toString()
                putString("stampDateText", "$stampDay $stampMonth. $stampYear")
                putFloat("stampFontSize", binding.stampFontSizeEditText.text.toString().toFloatOrNull() ?: 220f)
                putFloat("stampWearIntensity", binding.stampWearIntensitySlider.value)
                putFloat("stampWearSize", binding.stampWearSizeSlider.value)
                putFloat("stampSizePercent", binding.stampSizeEditText.text.toString().toFloatOrNull() ?: 5f)
                putFloat("stampMaxRotation", binding.stampRotationEditText.text.toString().toFloatOrNull() ?: 5f)
                putFloat("stampBrightness", binding.stampBrightnessEditText.text.toString().toFloatOrNull() ?: 50f)
                putFloat("stampContrast", binding.stampContrastEditText.text.toString().toFloatOrNull() ?: 50f)
            }
        }
        findNavController().navigate(R.id.action_pdfSettingsFragment_to_pdfPreviewFragment, bundle)
    }

    private fun getCapturedFolders(): List<ImageFolder> {
        val folders = mutableMapOf<String, MutableList<ImageFile>>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA
        )
        val selection = "${MediaStore.Images.Media.DATA} like ? and ${MediaStore.Images.Media.DATA} like ?"
        val selectionArgs = arrayOf("%/Download/capturas_sunarp/%", "%-Hoja %")

        val cursor = requireContext().contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Images.Media.DISPLAY_NAME} ASC"
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val pathColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val name = it.getString(nameColumn)
                val path = it.getString(pathColumn)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                val partidaId = name.substringBefore("-Hoja").trim()
                if (partidaId.isNotEmpty()) {
                    val imageFile = ImageFile(uri, path, name)
                    folders.getOrPut(partidaId) { mutableListOf() }.add(imageFile)
                }
            }
        }

        return folders.map { (partidaId, files) ->
            val sortedFiles = files.sortedBy { it.name.substringAfter("-Hoja ").substringBefore(".png").toIntOrNull() ?: 0 }
            ImageFolder(partidaId = partidaId, imageFiles = sortedFiles)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.pdf_settings_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_preview -> {
                validateAndProceed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
