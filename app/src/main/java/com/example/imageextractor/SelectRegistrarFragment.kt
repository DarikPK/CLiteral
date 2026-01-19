package com.example.imageextractor

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.imageextractor.databinding.FragmentSelectRegistrarBinding
import kotlinx.coroutines.launch

class SelectRegistrarFragment : Fragment() {

    private var _binding: FragmentSelectRegistrarBinding? = null
    private val binding get() = _binding!!

    private lateinit var registradorAdapter: RegistradorAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSelectRegistrarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerView()
        loadRegistradores()
    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        val sharedPrefs = requireActivity().getSharedPreferences("PdfSettings", Context.MODE_PRIVATE)
        val activeId = sharedPrefs.getString("active_registrar_id", null)

        registradorAdapter = RegistradorAdapter(emptyList(), activeId) { registrador ->
            // Guarda el ID del registrador seleccionado
            sharedPrefs.edit().putString("active_registrar_id", registrador.id).apply()

            // Muestra confirmación y regresa
            Toast.makeText(requireContext(), "Registrador '${registrador.nombre}' seleccionado.", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
        binding.registradoresRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = registradorAdapter
        }
    }

    private fun loadRegistradores() {
        lifecycleScope.launch {
            val registradores = FirestoreService.getRegistradores()
            val sharedPrefs = requireActivity().getSharedPreferences("PdfSettings", Context.MODE_PRIVATE)
            val activeId = sharedPrefs.getString("active_registrar_id", null)
            registradorAdapter.updateData(registradores, activeId)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(false)
        _binding = null
    }
}
