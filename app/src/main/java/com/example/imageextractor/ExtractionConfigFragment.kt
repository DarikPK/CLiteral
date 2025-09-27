package com.example.imageextractor

import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
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
        setupDropdowns()
        setupPartidaInputFilter()
        setupContinueButton()
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

    private fun setupPartidaInputFilter() {
        // Filtro para permitir una 'P' opcional al inicio, seguida solo de números.
        binding.partidaEditText.filters = arrayOf(InputFilter { source, start, end, dest, dstart, dend ->
            val newString = dest.substring(0, dstart) + source.substring(start, end) + dest.substring(dend, dest.length)
            if (newString.matches(Regex("^P?[0-9]*$"))) {
                null // Aceptar el cambio
            } else {
                "" // Rechazar el cambio
            }
        })
    }

    private fun setupContinueButton() {
        binding.continueButton.setOnClickListener {
            val dni = binding.dniEditText.text.toString()
            val digito = binding.digitoEditText.text.toString()
            val fecha = binding.fechaEmisionEditText.text.toString()
            val oficina = binding.oficinaDropdown.text.toString()
            val area = binding.areaDropdown.text.toString()
            val partida = binding.partidaEditText.text.toString()

            if (dni.isBlank() || digito.isBlank() || fecha.isBlank() || partida.isBlank()) {
                Toast.makeText(context, "Por favor, complete todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val config = ExtractionConfig(dni, digito, fecha, oficina, area, partida)
            sharedViewModel.setExtractionConfig(config)

            findNavController().navigate(R.id.action_extractionConfigFragment_to_extractionWebViewFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}