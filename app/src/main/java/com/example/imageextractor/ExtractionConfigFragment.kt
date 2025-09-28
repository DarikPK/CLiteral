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
        val oficinas = listOf("ABANCAY", "ANDAHUAYLAS", "AREQUIPA", "AYACUCHO", "BAGUA", "BARRANCA", "CAJAMARCA", "CALLAO", "CAMANA", "CASMA", "CASTILLA_APLAO", "CAÑETE", "CHACHAPOYAS", "CHEPEN", "CHICLAYO", "CHIMBOTE", "CHINCHA", "CHOTA", "CUSCO", "ESPINAR", "HUACHO", "HUAMACHUCO", "HUANCAVELICA", "HUANCAYO", "HUANTA", "HUANUCO", "HUARAL", "HUARAZ", "ICA", "ILO", "ISLAY_MOYENDO", "JAEN", "JUANJUI", "JULIACA", "LA MERCED ( SELVA CENTRAL)", "LIMA", "MADRE DE DIOS", "MAYNAS", "MOQUEGUA", "MOYOBAMBA", "NAZCA", "OTUZCO", "PASCO", "PISCO", "PIURA", "PUCALPA", "PUNO", "QUILLABAMBA", "SAN PEDRO", "SATIPO", "SICUANI", "SULLANA", "TACNA", "TARAPOTO", "TARMA", "TINGO MARIA", "TRUJILLO", "TUMBES", "YURIMAGUAS")
        val areas = listOf("PROPIEDAD INMUEBLE PREDIAL", "PROPIEDAD INMUEBLE NO PREDIAL", "PERSONAS JURIDICAS", "PERSONAS NATURALES", "PROPIEDAD VEHICULAR", "PROPIEDAD MINERIA", "REGISTRO DE NAVES Y EMBARCACIONES (ANTES REGISTRO DE EMBARCACIONES PESQUERAS)", "PROPIEDAD AERONAVES")

        val oficinaAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, oficinas)
        binding.oficinaDropdown.setAdapter(oficinaAdapter)
        binding.oficinaDropdown.setText("LIMA", false)

        val areaAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, areas)
        binding.areaDropdown.setAdapter(areaAdapter)
        binding.areaDropdown.setText("PROPIEDAD INMUEBLE PREDIAL", false)
    }

    private fun setupLoginModeSelector() {
        binding.loginModeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            binding.manualLoginFields.isVisible = checkedId == R.id.radio_manual
        }
    }

    private fun setupContinueButton() {
        binding.continueButton.setOnClickListener {
            val loginData = if (binding.radioManual.isChecked) {
                LoginData(
                    dni = binding.dniEditText.text.toString(),
                    digito = binding.digitoEditText.text.toString(),
                    fechaEmision = binding.fechaEmisionEditText.text.toString()
                )
            } else {
                sharedViewModel.getRandomLoginData()
            }

            var numeroPartida = binding.partidaEditText.text.toString()
            if (binding.prefixPCheckbox.isChecked) {
                numeroPartida = "P$numeroPartida"
            }

            if ((binding.radioManual.isChecked && (loginData.dni.isBlank() || loginData.digito.isBlank() || loginData.fechaEmision.isBlank())) || numeroPartida.isBlank()) {
                Toast.makeText(context, "Por favor, complete todos los campos requeridos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val config = ExtractionConfig(
                loginData = loginData,
                oficina = binding.oficinaDropdown.text.toString(),
                areaRegistral = binding.areaDropdown.text.toString(),
                numeroPartida = numeroPartida
            )
            sharedViewModel.setExtractionConfig(config)

            findNavController().navigate(R.id.action_extractionConfigFragment_to_extractionFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}