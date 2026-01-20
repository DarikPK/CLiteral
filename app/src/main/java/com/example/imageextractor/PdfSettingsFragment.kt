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

class PdfSettingsFragment : Fragment() {
    private var _binding: FragmentPdfSettingsBinding? = null
    private val binding get() = _binding!!

    private val selectedDate = Calendar.getInstance()

    private val sharedPrefs by lazy {
        requireActivity().getSharedPreferences("PdfSettings", Context.MODE_PRIVATE)
    }

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
        setupToolbar()
        setupDynamicFields()
        loadSettings()
        setupListeners()
    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.title = "Generar PDF"
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
        binding.datePickerButton.setOnClickListener { showDatePicker() }

        // Dynamic fields auto-save
        binding.dynamicNumeroPublicidad.doOnTextChanged { text, _, _, _ -> saveString("dynamic_numero_publicidad", text.toString()) }
        binding.dynamicAno.doOnTextChanged { text, _, _, _ -> saveString("dynamic_ano", text.toString()) }
        binding.dynamicDigito1.doOnTextChanged { text, _, _, _ -> saveString("dynamic_digito1", text.toString()) }
        binding.dynamicDigito2.doOnTextChanged { text, _, _, _ -> saveString("dynamic_digito2", text.toString()) }
        binding.dynamicHora.doOnTextChanged { text, _, _, _ ->
            saveString("dynamic_hora", text.toString())
            validateTime()
        }

        // Auto-save for Spinner
        binding.dynamicTipoPartidaSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                saveInt("dynamic_tipo_partida_position", position)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        binding.watermarkSettingsButton.setOnClickListener {
            findNavController().navigate(R.id.action_pdfSettingsFragment_to_watermarkSettingsFragment)
        }

        binding.pageImageSettingsButton.setOnClickListener {
            findNavController().navigate(R.id.action_pdfSettingsFragment_to_pageImageSettingsFragment)
        }

        binding.pageImageSettingsButton.setOnClickListener {
            findNavController().navigate(R.id.action_pdfSettingsFragment_to_pageImageSettingsFragment)
        }

        binding.registrarButton.setOnClickListener {
            findNavController().navigate(R.id.action_pdfSettingsFragment_to_registrarFragment)
        }
    }

    private fun loadSettings() {
        // Load date
        val savedDateMillis = sharedPrefs.getLong("selected_date", System.currentTimeMillis())
        selectedDate.timeInMillis = savedDateMillis
        updateDateButtonText()

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

                // Save the selected date
                saveLong("selected_date", selectedDate.timeInMillis)
                updateDateButtonText()
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

    private fun updateDateButtonText() {
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale("es", "ES"))
        binding.datePickerButton.text = sdf.format(selectedDate.time)
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

    private fun saveLong(key: String, value: Long) {
        sharedPrefs.edit().putLong(key, value).apply()
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
        // La validación del día ya no es necesaria,
        // porque siempre habrá una fecha seleccionada.
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
        lifecycleScope.launch {
            val allRegistradores = FirestoreService.getRegistradores()
            if (allRegistradores.isEmpty()) {
                Toast.makeText(context, "No hay registradores configurados.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val activeRegistrarId = sharedPrefs.getString("active_registrar_id", null)
            val registradorActivo = allRegistradores.find { it.id == activeRegistrarId } ?: allRegistradores.first()

            val watermarkPrefs = requireActivity().getSharedPreferences("WatermarkSettings", Context.MODE_PRIVATE)
            val bundle = Bundle().apply {
                putString("partidaId", folder.partidaId)
                putStringArray("imagePaths", folder.imageFiles.map { it.path }.toTypedArray())

                // Usar siempre los datos del registradorActivo
                // Sello 1
                putBoolean("isStampEnabled", registradorActivo.stampDateEnabled)
                if (registradorActivo.stampDateEnabled) {
                    putBoolean("stampOnFirstLast", registradorActivo.stampDateOnFirstLast)
                    val stampDay = String.format("%02d", selectedDate.get(Calendar.DAY_OF_MONTH))
                    val stampMonth = SimpleDateFormat("MMMM", Locale("es", "ES")).format(selectedDate.time).replaceFirstChar { it.titlecase(Locale("es", "ES")) }
                    val stampYear = selectedDate.get(Calendar.YEAR).toString()
                    putString("stampDateText", "$stampDay $stampMonth. $stampYear")
                    putFloat("stampFontSize", registradorActivo.stampDateFontSize)
                    putFloat("stampWearIntensity", registradorActivo.stampDateWearIntensity)
                    putFloat("stampWearSize", registradorActivo.stampDateWearSize)
                    putFloat("stampSizePercent", registradorActivo.stampDateSizePercent)
                    putFloat("stampMaxRotation", registradorActivo.stampDateMaxRotation)
                    putFloat("stampBrightness", registradorActivo.stampDateBrightness)
                    putFloat("stampContrast", registradorActivo.stampDateContrast)
                }

                // Sello 2
                putBoolean("isStamp2Enabled", registradorActivo.stampRegistrarEnabled)
                if (registradorActivo.stampRegistrarEnabled) {
                    putString("stamp2Name", registradorActivo.nombre)
                    putString("stamp2Position", registradorActivo.cargo)
                    putString("stamp2Area", registradorActivo.zonaRegistral)
                    putFloat("stamp2FontSize", registradorActivo.stampRegistrarFontSize)
                    putFloat("stamp2OffsetX", registradorActivo.stampRegistrarOffsetX)
                    putFloat("stamp2OffsetY", registradorActivo.stampRegistrarOffsetY)
                    putBoolean("stamp2VariableRotation", registradorActivo.stampRegistrarVariableRotation)
                    putFloat("stamp2Rotation", registradorActivo.stampRegistrarRotation)
                    putFloat("stamp2RotationTolerance", registradorActivo.stampRegistrarRotationTolerance)
                    putFloat("stamp2WearIntensity", registradorActivo.stampRegistrarWearIntensity)
                    putFloat("stamp2WearSize", registradorActivo.stampRegistrarWearSize)
                    putFloat("stamp2DotCount", registradorActivo.stampRegistrarDotCount)
                    putFloat("stamp2DotSize", registradorActivo.stampRegistrarDotSize)
                    putFloat("stamp2PointTextSeparation", registradorActivo.stampRegistrarPointTextSeparation)
                    putFloat("stamp2DotSpacing", registradorActivo.stampRegistrarDotSpacing)
                    putFloat("stamp2Brightness", registradorActivo.stampRegistrarBrightness)
                    putFloat("stamp2Contrast", registradorActivo.stampRegistrarContrast)
                }

                // Firma Principal
                putBoolean("isSignatureEnabled", registradorActivo.signatureEnabled)
                if (registradorActivo.signatureEnabled) {
                    putString("signatureImageUri", registradorActivo.signatureImageUri)
                    putFloat("signatureOffsetX", registradorActivo.signatureOffsetX)
                    putFloat("signatureOffsetY", registradorActivo.signatureOffsetY)
                    putFloat("signatureScale", registradorActivo.signatureScale)
                    putFloat("signatureRotation", registradorActivo.signatureRotation)
                    putString("signaturePoints", registradorActivo.signaturePoints)
                }

                // Lógica de Watermarks (sin cambios)
                // Pasar los valores de los márgenes
                putFloat("marginTop", sharedPrefs.getString("margin_top", "55")?.toFloatOrNull() ?: 55f)
                putFloat("marginBottom", sharedPrefs.getString("margin_bottom", "50")?.toFloatOrNull() ?: 50f)
                putFloat("marginLeft", sharedPrefs.getString("margin_left", "0")?.toFloatOrNull() ?: 0f)
                putFloat("marginRight", sharedPrefs.getString("margin_right", "15")?.toFloatOrNull() ?: 15f)
                putFloat("brightness", sharedPrefs.getString("brightness", "27")?.toFloatOrNull() ?: 27f)
                putFloat("contrast", sharedPrefs.getString("contrast", "100")?.toFloatOrNull() ?: 100f)

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
