package com.example.imageextractor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.imageextractor.databinding.FragmentEditRegistrarHubBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class EditRegistrarHubFragment : Fragment() {

    private var _binding: FragmentEditRegistrarHubBinding? = null
    private val binding get() = _binding!!

    private var registradorId: String? = null
    private var currentRegistrador: Registrador? = null
    private var isNavigating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            registradorId = it.getString("registradorId")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEditRegistrarHubBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        loadHeader()
        setupListeners()

        val navController = findNavController()
        navController.currentBackStackEntry?.savedStateHandle?.getLiveData<Registrador>("updatedRegistrador")?.observe(viewLifecycleOwner) { updatedRegistrador ->
            currentRegistrador = updatedRegistrador
            binding.registradorNameHeader.text = "Editando a: ${updatedRegistrador.nombre}"
        }
    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    private fun loadHeader() {
        registradorId?.let { id ->
            lifecycleScope.launch {
                currentRegistrador = FirestoreService.getRegistrador(id)
                if (_binding != null) {
                    binding.registradorNameHeader.text = "Editando a: ${currentRegistrador?.nombre ?: "Desconocido"}"
                }
            }
        }
    }

    private fun setupListeners() {
        binding.editStamp1Button.setOnClickListener { navigateTo(R.id.action_editRegistrarHubFragment_to_stamp1SettingsFragment) }
        binding.editStamp2Button.setOnClickListener { navigateTo(R.id.action_editRegistrarHubFragment_to_stamp2SettingsFragment) }
        binding.editSignaturesButton.setOnClickListener { navigateTo(R.id.action_editRegistrarHubFragment_to_signatureSettingsFragment) }
    }

    private fun navigateTo(actionId: Int) {
        if (isNavigating) return
        isNavigating = true

        if (currentRegistrador != null) {
            val bundle = bundleOf("registrador" to currentRegistrador)
            findNavController().navigate(actionId, bundle)
        } else {
            // Fallback por si currentRegistrador es nulo
            registradorId?.let { id ->
                lifecycleScope.launch {
                    val registrador = FirestoreService.getRegistrador(id)
                    if (registrador != null && _binding != null) {
                        currentRegistrador = registrador
                        val bundle = bundleOf("registrador" to registrador)
                        findNavController().navigate(actionId, bundle)
                    }
                }
            }
        }

        lifecycleScope.launch {
            delay(500)
            isNavigating = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
