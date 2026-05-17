package com.example.imageextractor

import android.content.Context
import android.os.Build
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
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager

class PdfSettingsFragment : Fragment() {

    private var _binding: FragmentPdfSettingsBinding? = null
    private val binding get() = _binding!!

    private val selectedDate = Calendar.getInstance()

    private val sharedPrefs by lazy {
        requireActivity().getSharedPreferences("PdfSettings", Context.MODE_PRIVATE)
    }

    private var currentSortMode = "temporal" // or "alphanumeric"

    private lateinit var folderAdapter: FolderAdapter
    private var allFolders: List<ImageFolder> = emptyList()
    private var selectedFolder: ImageFolder? = null
    private var isListExpanded = false

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            // This launcher is now only used for permission requests in this fragment.
            // The actual image picking is handled in SignatureSettingsFragment.
        } else {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Permiso Requerido")
                .setMessage("Para seleccionar una imagen de firma, necesitas conceder el permiso de acceso al almacenamiento. Por favor, actívalo en los ajustes de la aplicación.")
                .setPositiveButton("Ir a Ajustes") { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", requireActivity().packageName, null)
                    intent.data = uri
                    startActivity(intent)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
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
        currentSortMode = sharedPrefs.getString("partida_sort_mode", "temporal") ?: "temporal"
        setupToolbar()
        setupRecyclerView()
        setupMonthSpinner()
        setupDynamicFields()
        loadSettings()
        setupListeners()
        loadFolders()
    }

    private fun setupRecyclerView() {
        folderAdapter = FolderAdapter(
            onItemClick = { folder ->
                selectedFolder = folder
                val imageCount = folder.imageFiles.size
                val tipoText = folder.tipoPartida?.let { " - $it" } ?: ""
                binding.tvSelectedPartidaHint.setText("${folder.partidaId} (${imageCount} ${if (imageCount == 1) "Hoja" else "Hojas"})$tipoText")
                val pos = folderAdapter.currentList.indexOf(folder)
                folderAdapter.setSingleSelectedPosition(pos)

                // Persistir selección para el Voucher
                sharedPrefs.edit()
                    .putString("selected_partida_id", folder.partidaId)
                    .putInt("selected_partida_pages", imageCount)
                    .apply()

                // Auto-configurar el spinner de tipo de partida basado en la carpeta seleccionada
                folder.tipoPartida?.let { tipo ->
                    val adapter = binding.dynamicTipoPartidaSpinner.adapter
                    for (i in 0 until adapter.count) {
                        if (adapter.getItem(i).toString() == tipo) {
                            binding.dynamicTipoPartidaSpinner.setSelection(i)
                            saveInt("dynamic_tipo_partida_position", i)
                            break
                        }
                    }
                }

                // Colapsar lista al seleccionar
                isListExpanded = false
                updateFolderListVisibility()

                Toast.makeText(context, "Seleccionado: ${folder.partidaId}", Toast.LENGTH_SHORT).show()
            },
            onSelectionChanged = { /* No usado aquí para selección múltiple */ },
            layoutResId = R.layout.folder_dropdown_item
        )
        binding.rvCapturedFolders.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCapturedFolders.adapter = folderAdapter
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
        val toggleList = {
            isListExpanded = !isListExpanded
            updateFolderListVisibility()
        }

        binding.tvSelectedPartidaHint.setOnClickListener { toggleList() }
        binding.expandFoldersLayout.setEndIconOnClickListener { toggleList() }

        // Auto-save for all EditTexts
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
        binding.stampOnFirstLastPageCheckbox.setOnCheckedChangeListener { _, isChecked -> saveBoolean("stamp_on_first_last", isChecked) }


        binding.advancedStampSettingsButton.setOnClickListener {
            val layout = binding.advancedStampSettingsLayout
            layout.visibility = if (layout.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

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


        binding.registrarManagementButton.setOnClickListener {
            sharedPrefs.edit().putBoolean("is_editing_registrar", false).apply()
            findNavController().navigate(R.id.action_pdfSettingsFragment_to_registrarManagementFragment)
        }


        binding.btnGeneratePdf.setOnClickListener {
            validateAndProceed()
        }
    }

    private fun loadSettings() {
        val now = Calendar.getInstance()
        val day = now.get(Calendar.DAY_OF_MONTH)
        val month = now.get(Calendar.MONTH)
        val year = now.get(Calendar.YEAR)

        binding.stampDayTextView.text = day.toString()
        binding.stampMonthSpinner.setSelection(month)
        binding.stampYearEditText.setText(year.toString())
        binding.dynamicAno.setText(year.toString())
        selectedDate.set(year, month, day)

        // Asegurar que los valores por defecto estén persistidos para el Voucher
        if (!sharedPrefs.contains("stamp_day")) saveString("stamp_day", day.toString())
        if (!sharedPrefs.contains("stamp_month_position")) saveInt("stamp_month_position", month)
        if (!sharedPrefs.contains("stamp_year")) saveString("stamp_year", year.toString())
        if (!sharedPrefs.contains("dynamic_hora")) saveString("dynamic_hora", "08:00:00")
        if (!sharedPrefs.contains("dynamic_ano")) saveString("dynamic_ano", year.toString())

        // Los ajustes de imagen y márgenes ahora se cargan en PageImageSettingsFragment
        // binding.stampDayEditText.doOnTextChanged { text, _, _, _ -> saveString("stamp_day", text.toString()) } // Replaced by DatePicker
        binding.stampFontSizeEditText.setText(sharedPrefs.getString("stamp_font_size", "220"))
        binding.stampSizeEditText.setText(sharedPrefs.getString("stamp_size", "8"))
        binding.stampRotationEditText.setText(sharedPrefs.getString("stamp_rotation", "5"))
        binding.stampBrightnessEditText.setText(sharedPrefs.getString("stamp_brightness", "50"))
        binding.stampContrastEditText.setText(sharedPrefs.getString("stamp_contrast", "50"))

        binding.stampEnabledCheckbox.isChecked = sharedPrefs.getBoolean("stamp_enabled", true)
        binding.stampOnFirstLastPageCheckbox.isChecked = sharedPrefs.getBoolean("stamp_on_first_last", true)

        binding.stampWearIntensitySlider.value = sharedPrefs.getFloat("stamp_wear_intensity", 30f)
        binding.stampWearSizeSlider.value = sharedPrefs.getFloat("stamp_wear_size", 50f)

        // Load dynamic fields
        binding.dynamicNumeroPublicidad.setText(sharedPrefs.getString("dynamic_numero_publicidad", ""))

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

                saveString("stamp_day", dayOfMonth.toString())
                saveInt("stamp_month_position", month)
                saveString("stamp_year", year.toString())

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

    private fun getFloatPreferenceSafely(newKey: String, oldKey: String, defaultValue: Float): Float {
        val value = sharedPrefs.all[newKey] ?: sharedPrefs.all[oldKey]
        return when (value) {
            is Float -> value
            is String -> value.toFloatOrNull() ?: defaultValue
            else -> defaultValue
        }
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

        val folder = selectedFolder
        if (folder == null) {
            Toast.makeText(requireContext(), "Por favor, seleccione una partida de la lista superior", Toast.LENGTH_SHORT).show()
            return
        }

        navigateToPreview(folder)
    }

    private fun loadFolders() {
        lifecycleScope.launch(Dispatchers.IO) {
            allFolders = getCapturedFolders()
            withContext(Dispatchers.Main) {
                updateFolderList()
            }
        }
    }

    private fun updateFolderList() {
        // Antes de enviar la lista, asignar el tipo de partida guardado para cada carpeta
        allFolders.forEach { folder ->
            folder.tipoPartida = sharedPrefs.getString("tipo_partida_${folder.partidaId}", null)
        }

        folderAdapter.submitList(allFolders)

        // Seleccionar por defecto la última partida seleccionada si existe,
        // de lo contrario la última capturada, o la primera de la lista.
        if (selectedFolder == null && allFolders.isNotEmpty()) {
            val selectedId = sharedPrefs.getString("selected_partida_id", null)
            val lastCapturedId = sharedPrefs.getString("last_captured_partida_id", null)

            val targetId = selectedId ?: lastCapturedId

            val indexToSelect = if (targetId != null) {
                val foundIndex = allFolders.indexOfFirst { it.partidaId == targetId }
                if (foundIndex != -1) foundIndex else 0
            } else {
                0
            }

            val folder = allFolders[indexToSelect]
            selectedFolder = folder
            val imageCount = folder.imageFiles.size
            val tipoText = folder.tipoPartida?.let { " - $it" } ?: ""
            binding.tvSelectedPartidaHint.setText("${folder.partidaId} (${imageCount} ${if (imageCount == 1) "Hoja" else "Hojas"})$tipoText")
            folderAdapter.setSingleSelectedPosition(indexToSelect)

            // Auto-configurar el spinner de tipo de partida basado en la carpeta seleccionada
            folder.tipoPartida?.let { tipo ->
                val adapter = binding.dynamicTipoPartidaSpinner.adapter
                for (i in 0 until adapter.count) {
                    if (adapter.getItem(i).toString() == tipo) {
                        binding.dynamicTipoPartidaSpinner.setSelection(i)
                        saveInt("dynamic_tipo_partida_position", i)
                        break
                    }
                }
            }

            // Persistir selección por defecto para el Voucher
            sharedPrefs.edit()
                .putString("selected_partida_id", folder.partidaId)
                .putInt("selected_partida_pages", imageCount)
                .apply()
        }
    }

    private fun updateFolderListVisibility() {
        if (isListExpanded) {
            binding.rvCapturedFolders.visibility = View.VISIBLE
            // El componente ya maneja el ícono si usamos el endIconMode del TextInputLayout
        } else {
            binding.rvCapturedFolders.visibility = View.GONE
        }
    }

    private fun navigateToPreview(folder: ImageFolder) {
        val watermarkPrefs = requireActivity().getSharedPreferences("WatermarkSettings", Context.MODE_PRIVATE)

        val bundle = Bundle().apply {
            putString("partidaId", folder.partidaId)
            putStringArray("imagePaths", folder.imageFiles.map { it.path }.toTypedArray())

            putFloat("brightness", sharedPrefs.getString("brightness", "27")?.toFloatOrNull() ?: 27f)
            putFloat("contrast", sharedPrefs.getString("contrast", "100")?.toFloatOrNull() ?: 100f)

            // Pasar los valores de los márgenes
            putFloat("marginTop", sharedPrefs.getString("margin_top", "55")?.toFloatOrNull() ?: 55f)
            putFloat("marginBottom", sharedPrefs.getString("margin_bottom", "50")?.toFloatOrNull() ?: 50f)
            putFloat("marginLeft", sharedPrefs.getString("margin_left", "0")?.toFloatOrNull() ?: 0f)
            putFloat("marginRight", sharedPrefs.getString("margin_right", "15")?.toFloatOrNull() ?: 15f)

            putBoolean("isStampEnabled", binding.stampEnabledCheckbox.isChecked)
            if (binding.stampEnabledCheckbox.isChecked) {
                putBoolean("stampOnFirstLast", binding.stampOnFirstLastPageCheckbox.isChecked)
                val dayInt = binding.stampDayTextView.text.toString().toIntOrNull()
                val stampDay = dayInt?.let { String.format("%02d", it) } ?: ""
                val stampMonth = binding.stampMonthSpinner.selectedItem.toString()
                val stampYear = binding.stampYearEditText.text.toString()
                putString("stampDateText", "$stampDay $stampMonth. $stampYear")
                putFloat("stampFontSize", binding.stampFontSizeEditText.text.toString().toFloatOrNull() ?: 220f)
                putFloat("stampWearIntensity", binding.stampWearIntensitySlider.value)
                putFloat("stampWearSize", binding.stampWearSizeSlider.value)
                putFloat("stampSizePercent", binding.stampSizeEditText.text.toString().toFloatOrNull() ?: 8f)
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
                putFloat("stamp2TranslationToleranceX", sharedPrefs.getString("stamp2_translation_tolerance_x", "0")?.toFloatOrNull() ?: 0f)
                putFloat("stamp2TranslationToleranceY", sharedPrefs.getString("stamp2_translation_tolerance_y", "0")?.toFloatOrNull() ?: 0f)
                putFloat("stamp2WearIntensity", sharedPrefs.getFloat("stamp2_wear_intensity", 30f))
                putFloat("stamp2WearSize", sharedPrefs.getFloat("stamp2_wear_size", 50f))
                putFloat("stamp2DotCount", getFloatPreferenceSafely("stamp2DotCount", "stamp2_dot_count", 3f))
                putFloat("stamp2DotSize", getFloatPreferenceSafely("stamp2DotSize", "stamp2_dot_size", 13f))
                putFloat("stamp2PointTextSeparation", sharedPrefs.getString("stamp2_point_text_separation", "5")?.toFloatOrNull() ?: 5f)
                putFloat("stamp2Brightness", getFloatPreferenceSafely("stamp2Brightness", "stamp2_brightness", 50f))
                putFloat("stamp2Contrast", getFloatPreferenceSafely("stamp2Contrast", "stamp2_contrast", 50f))
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
                    2 -> "-55"
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

            // Visibility
            putBoolean("showInPdf", sharedPrefs.getBoolean("show_in_pdf", true))

            // Signature Data (se leen directamente de SharedPreferences en PdfPreviewFragment,
            // pero pasamos estos por compatibilidad si se usaran)
            val isSignatureEnabled = sharedPrefs.getBoolean("signature_enabled", true)
            putBoolean("isSignatureEnabled", isSignatureEnabled)
        }
        findNavController().navigate(R.id.action_pdfSettingsFragment_to_pdfPreviewFragment, bundle)
    }

    private fun getCapturedFolders(): List<ImageFolder> {
        val folders = mutableMapOf<String, MutableList<ImageFile>>()
        val folderLastModified = mutableMapOf<String, Long>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_MODIFIED
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
            val dateColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val name = it.getString(nameColumn)
                val path = it.getString(pathColumn)
                val date = it.getLong(dateColumn)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                val partidaId = name.substringBefore("-Hoja").trim()
                if (partidaId.isNotEmpty()) {
                    val imageFile = ImageFile(uri, path, name)
                    folders.getOrPut(partidaId) { mutableListOf() }.add(imageFile)

                    val currentMaxDate = folderLastModified[partidaId] ?: 0L
                    if (date > currentMaxDate) {
                        folderLastModified[partidaId] = date
                    }
                }
            }
        }

        val result = folders.map { (partidaId, files) ->
            val sortedFiles = files.sortedBy { it.name.substringAfter("-Hoja ").substringBefore(".png").toIntOrNull() ?: 0 }
            ImageFolder(
                partidaId = partidaId,
                imageFiles = sortedFiles,
                lastModified = folderLastModified[partidaId] ?: 0L
            )
        }

        return if (currentSortMode == "temporal") {
            result.sortedByDescending { it.lastModified }
        } else {
            result.sortedBy { it.partidaId }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.pdf_settings_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sort -> {
                toggleSortMode()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun toggleSortMode() {
        currentSortMode = if (currentSortMode == "alphanumeric") "temporal" else "alphanumeric"
        sharedPrefs.edit().putString("partida_sort_mode", currentSortMode).apply()
        val message = if (currentSortMode == "alphanumeric") "Orden Alfanumérico" else "Orden Temporal (Reciente primero)"
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        loadFolders()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
