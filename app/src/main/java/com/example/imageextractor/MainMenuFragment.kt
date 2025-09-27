package com.example.imageextractor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.imageextractor.databinding.FragmentMainMenuBinding

class MainMenuFragment : Fragment() {

    private var _binding: FragmentMainMenuBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Configurar el listener para el botón de extracción.
        binding.buttonToExtraction.setOnClickListener {
            // Navegar al ExtractionFragment usando la acción definida en nav_graph.xml.
            findNavController().navigate(R.id.action_mainMenuFragment_to_extractionFragment)
        }

        // Configurar el listener para el botón de edición.
        binding.buttonToEditing.setOnClickListener {
            // Navegar al EditingFragment usando la acción definida en nav_graph.xml.
            findNavController().navigate(R.id.action_mainMenuFragment_to_editingFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}