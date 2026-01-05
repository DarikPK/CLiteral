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
import android.app.DatePickerDialog
import android.content.ContentUris
import android.provider.MediaStore
import com.google.android.material.slider.Slider
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PdfSettingsFragment : Fragment() {

    private var _binding: FragmentPdfSettingsBinding? = null
    private val binding get() = _binding!!

    private val selectedDate = Calendar.getInstance()

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
        setupDynamicFields()
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

    private fun setupDynamicFields() {
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.tipo_partida_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.dynamicTipoPartidaSpinner.adapter = adapter
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
        // binding.stampDayEditText.doOnTextChanged { text, _, _, _ -> saveString("stamp_day", text.toString()) } // Replaced by DatePicker
        binding.stampYearEditText.doOnTextChanged { text, _, _, _ -> saveString("stamp_year", text.toString()) }
        binding.stampFontSizeEditText.doOnTextChanged { text, _, _, _ -> saveString("stamp_font_size", text.toString()) }
        binding.stampSizeEditText.doOnTextChanged { text, _, _, _ -> saveString("stamp_size", text.toString()) }
        binding.stampRotationEditText.doOnTextChanged { text, _, _, _ -> saveString("stamp_rotation", text.toString()) }
        binding.stampBrightnessEditText.doOnTextChanged { text, _, _, _ -> saveString("stamp_brightness", text.toString()) }
        binding.stampContrastEditText.doOnTextChanged { text, _, _, _ -> saveString("stamp_contrast", text.toString()) }

        // Dynamic fields auto-save
        binding.dynamicNumeroPublicidad.doOnTextChanged { text, _, _, _ -> saveString("dynamic_numero_publicidad", text.toString()) }
        binding.dynamicAno.doOnTextChanged { text, _, _, _ -> saveString("dynamic_ano", text.toString()) }
        binding.dynamicDigito1.doOnTextChanged { text, _, _, _ -> saveString("dynamic_digito1", text.toString()) }
        binding.dynamicDigito2.doOnTextChanged { text, _, _, _ -> saveString("dynamic_digito2", text.toString()) }
        binding.dynamicHora.doOnTextChanged { text, _, _, _ ->
            saveString("dynamic_hora", text.toString())
            validateTime()
        }

        binding.stampDayTextView.setOnClickListener { showDatePicker() }

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

        binding.dynamicTipoPartidaSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                saveInt("dynamic_tipo_partida_position", position)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        binding.watermarkSettingsButton.setOnClickListener {
            findNavController().navigate(R.id.action_pdfSettingsFragment_to_watermarkSettingsFragment)
        }

        binding.stamp2SettingsButton.setOnClickListener {
            findNavController().navigate(R.id.action_pdfSettingsFragment_to_stamp2SettingsFragment)
        }
    }

    private fun loadSettings() {
        binding.brightnessEditText.setText(sharedPrefs.getString("brightness", "60"))
        binding.contrastEditText.setText(sharedPrefs.getString("contrast", "100"))
        binding.marginTopEditText.setText(sharedPrefs.getString("margin_top", "55"))
        binding.marginBottomEditText.setText(sharedPrefs.getString("margin_bottom", "50"))
        binding.marginLeftEditText.setText(sharedPrefs.getString("margin_left", "0"))
        binding.marginRightEditText.setText(sharedPrefs.getString("margin_right", "15"))
        binding.stampDayTextView.text = sharedPrefs.getString("stamp_day", "1")
        binding.stampYearEditText.setText(sharedPrefs.getString("stamp_year", "2026"))
        binding.stampFontSizeEditText.setText(sharedPrefs.getString("stamp_font_size", "220"))
        binding.stampSizeEditText.setText(sharedPrefs.getString("stamp_size", "20"))
        binding.stampRotationEditText.setText(sharedPrefs.getString("stamp_rotation", "5"))
        binding.stampBrightnessEditText.setText(sharedPrefs.getString("stamp_brightness", "50"))
        binding.stampContrastEditText.setText(sharedPrefs.getString("stamp_contrast", "50"))

        binding.stampEnabledCheckbox.isChecked = sharedPrefs.getBoolean("stamp_enabled", true)

        binding.stampWearIntensitySlider.value = sharedPrefs.getFloat("stamp_wear_intensity", 30f)
        binding.stampWearSizeSlider.value = sharedPrefs.getFloat("stamp_wear_size", 50f)

        binding.stampMonthSpinner.setSelection(sharedPrefs.getInt("stamp_month_position", 0))

        // Load dynamic fields
        binding.dynamicNumeroPublicidad.setText(sharedPrefs.getString("dynamic_numero_publicidad", ""))
        binding.dynamicAno.setText(sharedPrefs.getString("dynamic_ano", "2026"))
        binding.dynamicDigito1.setText(sharedPrefs.getString("dynamic_digito1", ""))
        binding.dynamicDigito2.setText(sharedPrefs.getString("dynamic_digito2", ""))
        binding.dynamicTipoPartidaSpinner.setSelection(sharedPrefs.getInt("dynamic_tipo_partida_position", 0))
        binding.dynamicHora.setText(sharedPrefs.getString("dynamic_hora", "08:00:00"))
    }

    private fun showDatePicker() {
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            val tempDate = Calendar.getInstance()
            tempDate.set(year, month, dayOfMonth)

            if (tempDate.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                Toast.makeText(requireContext(), "No se pueden seleccionar domingos", Toast.LENGTH_SHORT).show()
            } else {
                selectedDate.set(Calendar.YEAR, year)
                selectedDate.set(Calendar.MONTH, month)
                selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                binding.stampDayTextView.text = dayOfMonth.toString()
                binding.stampMonthSpinner.setSelection(month)
                binding.stampYearEditText.setText(year.toString())
                validateTime() // Re-validate time when date changes
            }
        }

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            dateSetListener,
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH)
        )

        datePickerDialog.show()
    }

    private fun validateTime() {
        val timeString = binding.dynamicHora.text.toString()
        val dayOfWeek = selectedDate.get(Calendar.DAY_OF_WEEK)

        val (isValid, message) = when (dayOfWeek) {
            Calendar.SATURDAY -> {
                isTimeInValidRange(timeString, "08:30:00", "13:00:00") to "Hora fuera del rango de Sábado (08:30 - 13:00)"
            }
            Calendar.SUNDAY -> {
                true to "" // La validación del domingo ya se hace en el DatePicker
            }
            else -> { // Monday to Friday
                isTimeInValidRange(timeString, "08:30:00", "17:00:00") to "Hora fuera del rango de Lunes a Viernes (08:30 - 17:00)"
            }
        }

        if (!isValid) {
            binding.dynamicHora.error = message
        } else {
            binding.dynamicHora.error = null
        }
    }

    private fun isTimeInValidRange(time: String, minTime: String, maxTime: String): Boolean {
        return try {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val timeDate = sdf.parse(time)
            val minDate = sdf.parse(minTime)
            val maxDate = sdf.parse(maxTime)
            timeDate in minDate..maxDate
        } catch (e: Exception) {
            false
        }
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
        if (binding.stampEnabledCheckbox.isChecked && binding.stampDayTextView.text.toString().isBlank()) {
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
        val watermarkPrefs = requireActivity().getSharedPreferences("WatermarkSettings", Context.MODE_PRIVATE)

        val bundle = Bundle().apply {
            putString("partidaId", folder.partidaId)
            putStringArray("imagePaths", folder.imageFiles.map { it.path }.toTypedArray())

            putFloat("brightness", binding.brightnessEditText.text.toString().toFloatOrNull() ?: 60f)
            putFloat("contrast", binding.contrastEditText.text.toString().toFloatOrNull() ?: 100f)

            // Pasar los valores de los márgenes
            putFloat("marginTop", binding.marginTopEditText.text.toString().toFloatOrNull() ?: 10f)
            putFloat("marginBottom", binding.marginBottomEditText.text.toString().toFloatOrNull() ?: 10f)
            putFloat("marginLeft", binding.marginLeftEditText.text.toString().toFloatOrNull() ?: 10f)
            putFloat("marginRight", binding.marginRightEditText.text.toString().toFloatOrNull() ?: 10f)

            putBoolean("isStampEnabled", binding.stampEnabledCheckbox.isChecked)
            if (binding.stampEnabledCheckbox.isChecked) {
                val dayInt = binding.stampDayTextView.text.toString().toIntOrNull()
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

            // Sello 2 data
            val isStamp2Enabled = sharedPrefs.getBoolean("stamp2_enabled", true)
            putBoolean("isStamp2Enabled", isStamp2Enabled)
            if (isStamp2Enabled) {
                putString("stamp2Name", sharedPrefs.getString("stamp2_name", "NOMBRE APELLIDO"))
                putString("stamp2Position", sharedPrefs.getString("stamp2_position", "CARGO"))
                putString("stamp2Area", sharedPrefs.getString("stamp2_area", "ZONA REGISTRAL"))
                putFloat("stamp2FontSize", sharedPrefs.getString("stamp2_font_size", "13")?.toFloatOrNull() ?: 13f)
                putFloat("stamp2OffsetX", sharedPrefs.getString("stamp2_offset_x", "0")?.toFloatOrNull() ?: 0f)
                putFloat("stamp2OffsetY", sharedPrefs.getString("stamp2_offset_y", "0")?.toFloatOrNull() ?: 0f)
                putBoolean("stamp2VariableRotation", sharedPrefs.getBoolean("stamp2_variable_rotation", true))
                putFloat("stamp2Rotation", sharedPrefs.getString("stamp2_rotation", "0")?.toFloatOrNull() ?: 0f)
                putFloat("stamp2RotationTolerance", sharedPrefs.getString("stamp2_rotation_tolerance", "5")?.toFloatOrNull() ?: 5f)
                putFloat("stamp2WearIntensity", sharedPrefs.getFloat("stamp2_wear_intensity", 30f))
                putFloat("stamp2WearSize", sharedPrefs.getFloat("stamp2_wear_size", 50f))
                putFloat("stamp2_dot_count", sharedPrefs.getString("stamp2_dot_count", "3")?.toFloatOrNull() ?: 3f)
                putFloat("stamp2_dot_size", sharedPrefs.getString("stamp2_dot_size", "13")?.toFloatOrNull() ?: 13f)
            }

            // Watermark 1 data
            putString("w1_text", watermarkPrefs.getString("w1_text", "Certificado Literal"))
            putFloat("w1_opacity", watermarkPrefs.getString("w1_opacity", "25")?.toFloatOrNull() ?: 25f)
            putFloat("w1_size", watermarkPrefs.getString("w1_size", "114")?.toFloatOrNull() ?: 114f)
            putFloat("w1_scale", watermarkPrefs.getString("w1_scale", "100")?.toFloatOrNull() ?: 100f)
            putFloat("w1_dx", watermarkPrefs.getString("w1_dx", "-15")?.toFloatOrNull() ?: -15f)
            putFloat("w1_dy", watermarkPrefs.getString("w1_dy", "-14")?.toFloatOrNull() ?: -14f)
            putFloat("w1_angle", watermarkPrefs.getString("w1_angle", "-55")?.toFloatOrNull() ?: -55f)

            // Pass data for all 4 watermarks
            for (i in 1..4) {
                val defaultText = when (i) {
                    1 -> "Certificado Literal"
                    2 -> "Sin inscripcion al Dorso\nNo hay Títulos Suspendidos y/o Pendientes de Inscripci\nA las Horas : 8:00 AM"
                    3 -> "PUBLICIDAD : \"Número publicidad\" Recibo N° \"Año\"-\"Digito 1\"-\"Digito 2\" Partida N° \"número partida\" CERTI. LITERAL - \"Tipo partida\""
                    4 -> "Pág. Solicitadas : Todas  IMPRESION :  \"fecha\" \"Hora\" Página \"x\" de \"y\"\nNo existen Títulos Pendientes y/o Suspendidos  Inmovilización: Ninguno"
                    else -> ""
                }
                val defaultOpacity = when (i) {
                    1, 2 -> "25"
                    3 -> "90"
                    4 -> "80"
                    else -> "50"
                }
                val defaultSize = when (i) {
                    1 -> "114"
                    2 -> "71"
                    3 -> "23"
                    4 -> "15"
                    else -> "72"
                }
                val defaultScale = when (i) {
                    2 -> "80"
                    3 -> "90"
                    4 -> "100"
                    else -> "100"
                }
                val defaultDx = when (i) {
                    1 -> "-15"
                    2 -> "29"
                    4 -> "165"
                    else -> "0"
                }
                val defaultDy = when (i) {
                    1 -> "-14"
                    2 -> "19"
                    3 -> "-232"
                    4 -> "0"
                    else -> "0"
                }
                val defaultAngle = when (i) {
                    1 -> "-55"
                    2 -> "55"
                    4 -> "-90"
                    else -> "0"
                }

                putString("w${i}_text", watermarkPrefs.getString("w${i}_text", defaultText))
                putFloat("w${i}_opacity", watermarkPrefs.getString("w${i}_opacity", defaultOpacity)?.toFloatOrNull() ?: 50f)
                putFloat("w${i}_size", watermarkPrefs.getString("w${i}_size", defaultSize)?.toFloatOrNull() ?: 72f)
                putFloat("w${i}_scale", watermarkPrefs.getString("w${i}_scale", defaultScale)?.toFloatOrNull() ?: 100f)
                putFloat("w${i}_dx", watermarkPrefs.getString("w${i}_dx", defaultDx)?.toFloatOrNull() ?: 0f)
                putFloat("w${i}_dy", watermarkPrefs.getString("w${i}_dy", defaultDy)?.toFloatOrNull() ?: 0f)
                putFloat("w${i}_angle", watermarkPrefs.getString("w${i}_angle", defaultAngle)?.toFloatOrNull() ?: 0f)

                if (i == 2) {
                    putInt("w2_align", watermarkPrefs.getInt("w2_align", 1)) // 1 = Center
                    putFloat("w2_right_crop", watermarkPrefs.getString("w2_right_crop", "319")?.toFloatOrNull() ?: 319f)
                }
            }

            // Pass dynamic values for watermark 3
            putString("dynamic_numero_publicidad", binding.dynamicNumeroPublicidad.text.toString())
            putString("dynamic_ano", binding.dynamicAno.text.toString())
            putString("dynamic_digito1", binding.dynamicDigito1.text.toString())
            putString("dynamic_digito2", binding.dynamicDigito2.text.toString())
            putString("dynamic_numero_partida", folder.partidaId)
            putString("dynamic_tipo_partida", binding.dynamicTipoPartidaSpinner.selectedItem.toString())

            // Pass data for watermark 4
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            putString("dynamic_fecha", sdf.format(selectedDate.time))
            putString("dynamic_hora_wm4", binding.dynamicHora.text.toString())
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
