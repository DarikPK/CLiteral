package com.example.imageextractor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.imageextractor.databinding.FragmentCreateRegistrarBinding
import kotlinx.coroutines.launch

class CreateRegistrarFragment : Fragment() {

    private var _binding: FragmentCreateRegistrarBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCreateRegistrarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupListeners()
    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    private fun setupListeners() {
        binding.saveButton.setOnClickListener {
            val name = binding.nameEditText.text.toString().trim()
            val position = binding.positionEditText.text.toString().trim()
            val area = binding.areaEditText.text.toString().trim()

            if (name.isEmpty() || position.isEmpty() || area.isEmpty()) {
                Toast.makeText(requireContext(), "Por favor, complete todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Crear un nuevo registrador con valores por defecto para todo lo demás
            val newRegistrador = Registrador(
                nombre = name,
                cargo = position,
                zonaRegistral = area
            )

            lifecycleScope.launch {
                val newId = FirestoreService.addRegistrador(newRegistrador)
                if (newId != null) {
                    // Navegar al hub de edición para configurar el nuevo registrador
                    val bundle = bundleOf("registradorId" to newId)
                    findNavController().navigate(R.id.action_createRegistrarFragment_to_editRegistrarHubFragment, bundle)
                } else {
                    Toast.makeText(requireContext(), "Error al guardar el registrador", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
