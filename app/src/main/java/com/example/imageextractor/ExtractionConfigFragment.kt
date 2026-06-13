package com.example.imageextractor

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.imageextractor.databinding.FragmentExtractionConfigBinding

class ExtractionConfigFragment : Fragment() {

    private var _binding: FragmentExtractionConfigBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SharedViewModel by activityViewModels()

    private val oficinas = listOf("ABANCAY", "ANDAHUAYLAS", "AREQUIPA", "AYACUCHO", "BAGUA", "BARRANCA", "CAJAMARCA", "CALLAO", "CAMANA", "CASMA", "CASTILLA_APLAO", "CAÑETE", "CHACHAPOYAS", "CHEPEN", "CHICLAYO", "CHIMBOTE", "CHINCHA", "CHOTA", "CUSCO", "ESPINAR", "HUACHO", "HUAMACHUCO", "HUANCAVELICA", "HUANCAYO", "HUANTA", "HUANUCO", "HUARAL", "HUARAZ", "ICA", "ILO", "ISLAY_MOYENDO", "JAEN", "JUANJUI", "JULIACA", "LA MERCED ( SELVA CENTRAL)", "LIMA", "MADRE DE DIOS", "MAYNAS", "MOQUEGUA", "MOYOBAMBA", "NAZCA", "OTUZCO", "PASCO", "PISCO", "PIURA", "PUCALPA", "PUNO", "QUILLABAMBA", "SAN PEDRO", "SATIPO", "SICUANI", "SULLANA", "TACNA", "TARAPOTO", "TARMA", "TINGO MARIA", "TRUJILLO", "TUMBES", "YURIMAGUAS")
    private val areas = listOf("PROPIEDAD INMUEBLE PREDIAL", "PROPIEDAD INMUEBLE NO PREDIAL", "PERSONAS JURIDICAS", "PERSONAS NATURALES", "PROPIEDAD VEHICULAR", "PROPIEDAD MINERIA", "REGISTRO DE NAVES Y EMBARCACIONES (ANTES REGISTRO DE EMBARCACIONES PESQUERAS)", "PROPIEDAD AERONAVES")

    private val extractionPrefs by lazy {
        requireActivity().getSharedPreferences("ExtractionSettings", android.content.Context.MODE_PRIVATE)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExtractionConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAutofillHighlight()
        setupDropdowns()
        setupLoginModeSelector()
        setupContinueButton()
        setupVisibilitySwitch()
        setupManualStartButtonSwitch()
    }

    override fun onResume() {
        super.onResume()
        loadSavedSettings()
    }

    private fun setupManualStartButtonSwitch() {
        // Sincroniza el switch con el estado actual del ViewModel
        sharedViewModel.isManualStartButtonVisible.observe(viewLifecycleOwner) { isVisible ->
            if (binding.manualStartButtonSwitch.isChecked != isVisible) {
                binding.manualStartButtonSwitch.isChecked = isVisible
            }
        }

        // Notifica al ViewModel cuando el usuario cambia el switch
        binding.manualStartButtonSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (sharedViewModel.isManualStartButtonVisible.value != isChecked) {
                sharedViewModel.toggleManualStartButtonVisibility()
            }
        }
    }

    private fun setupVisibilitySwitch() {
        // Sincroniza el switch con el estado actual del ViewModel
        sharedViewModel.isWebViewVisible.observe(viewLifecycleOwner) { isVisible ->
            if (binding.webviewVisibilitySwitch.isChecked != isVisible) {
                binding.webviewVisibilitySwitch.isChecked = isVisible
            }
        }

        // Notifica al ViewModel cuando el usuario cambia el switch
        binding.webviewVisibilitySwitch.setOnCheckedChangeListener { _, isChecked ->
            // Solo actualiza si el estado realmente cambió para evitar bucles
            if (sharedViewModel.isWebViewVisible.value != isChecked) {
                sharedViewModel.toggleWebViewVisibility()
            }
        }
    }

    private fun setupAutofillHighlight() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val color = ContextCompat.getColor(requireContext(), R.color.button_blue)
                val highlightDrawable = ColorDrawable(color)
                val method = View::class.java.getMethod("setAutofillHighlight", android.graphics.drawable.Drawable::class.java)

                val viewsToHighlight = listOf(
                    binding.dniEditText,
                    binding.digitoEditText,
                    binding.fechaEmisionEditText,
                    binding.oficinaDropdown,
                    binding.areaDropdown,
                    binding.partidaEditText
                )

                viewsToHighlight.forEach { view ->
                    method.invoke(view, highlightDrawable)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupDropdowns() {
        val oficinaAdapter = NoFilterAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, oficinas)
        binding.oficinaDropdown.setAdapter(oficinaAdapter)

        val areaAdapter = NoFilterAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, areas)
        binding.areaDropdown.setAdapter(areaAdapter)

        binding.oficinaDropdown.setOnClickListener {
            binding.oficinaDropdown.showDropDown()
        }
        binding.areaDropdown.setOnClickListener {
            binding.areaDropdown.showDropDown()
        }
    }

    private fun loadSavedSettings() {
        binding.dniEditText.setText(extractionPrefs.getString("dni", ""))
        binding.digitoEditText.setText(extractionPrefs.getString("digito", ""))
        binding.fechaEmisionEditText.setText(extractionPrefs.getString("fecha_emision", ""))

        val oficina = extractionPrefs.getString("oficina", "LIMA")
        binding.oficinaDropdown.setText(oficina, false)

        val area = extractionPrefs.getString("area", "PROPIEDAD INMUEBLE PREDIAL")
        binding.areaDropdown.setText(area, false)

        binding.partidaEditText.setText(extractionPrefs.getString("partida", ""))
        binding.prefixPCheckbox.isChecked = extractionPrefs.getBoolean("prefix_p", false)

        val isManual = extractionPrefs.getBoolean("is_manual_login", false)
        if (isManual) {
            binding.radioManual.isChecked = true
        } else {
            binding.radioRandom.isChecked = true
        }
        binding.manualLoginFields.isVisible = isManual
    }

    private fun saveCurrentSettings() {
        val office = binding.oficinaDropdown.text.toString()
        with(extractionPrefs.edit()) {
            putString("dni", binding.dniEditText.text.toString())
            putString("digito", binding.digitoEditText.text.toString())
            putString("fecha_emision", binding.fechaEmisionEditText.text.toString())
            putString("oficina", office)
            putString("area", binding.areaDropdown.text.toString())
            putString("partida", binding.partidaEditText.text.toString())
            putBoolean("prefix_p", binding.prefixPCheckbox.isChecked)
            putBoolean("is_manual_login", binding.radioManual.isChecked)
            apply()
        }
        // Sync with PdfSettings for Cloud Profiles
        requireActivity().getSharedPreferences("PdfSettings", android.content.Context.MODE_PRIVATE)
            .edit().putString("oficina", office).apply()
    }

    private fun setupLoginModeSelector() {
        // Set initial visibility based on the default selection
        binding.manualLoginFields.isVisible = binding.loginModeRadioGroup.checkedRadioButtonId == R.id.radio_manual

        // Set listener for subsequent changes
        binding.loginModeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            binding.manualLoginFields.isVisible = checkedId == R.id.radio_manual
        }
    }

    private fun setupContinueButton() {
        binding.continueButton.setOnClickListener {
            val numeroPartida = binding.partidaEditText.text?.toString()?.trim() ?: ""
            if (numeroPartida.isBlank()) {
                Toast.makeText(context, "Por favor, ingrese el número de partida", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveCurrentSettings()

            val loginData = if (binding.radioManual.isChecked) {
                val dni = binding.dniEditText.text.toString()
                val digito = binding.digitoEditText.text.toString()
                val fechaEmision = binding.fechaEmisionEditText.text.toString()
                if (dni.isBlank() || digito.isBlank() || fechaEmision.isBlank()) {
                    Toast.makeText(context, "Por favor, complete todos los campos requeridos", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                LoginData(dni, digito, fechaEmision)
            } else {
                sharedViewModel.getRandomLoginData()
            }

            var finalNumeroPartida = numeroPartida
            if (binding.prefixPCheckbox.isChecked) {
                finalNumeroPartida = "P$finalNumeroPartida"
            }

            val config = ExtractionConfig(
                loginData = loginData,
                oficina = binding.oficinaDropdown.text.toString(),
                areaRegistral = binding.areaDropdown.text.toString(),
                numeroPartida = finalNumeroPartida
            )
            sharedViewModel.setExtractionConfig(config)

            findNavController().navigate(R.id.action_extractionConfigFragment_to_extractionFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class NoFilterAdapter(
        context: android.content.Context,
        resource: Int,
        private val items: List<String>
    ) : ArrayAdapter<String>(context, resource, items) {

        private val noFilter = object : android.widget.Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()
                results.values = items
                results.count = items.size
                return results
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                notifyDataSetChanged()
            }

            override fun convertResultToString(resultValue: Any?): CharSequence {
                return resultValue as String
            }
        }

        override fun getFilter(): android.widget.Filter {
            return noFilter
        }
    }
}