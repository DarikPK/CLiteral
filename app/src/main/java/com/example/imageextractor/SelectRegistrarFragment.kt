package com.example.imageextractor

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        registradorAdapter = RegistradorAdapter(emptyList()) { registrador ->
            // Guarda el ID del registrador seleccionado en SharedPreferences
            val sharedPrefs = requireActivity().getSharedPreferences("PdfSettings", Context.MODE_PRIVATE)
            sharedPrefs.edit().putString("active_registrar_id", registrador.id).apply()

            // Regresa a la pantalla anterior
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
            registradorAdapter.updateData(registradores)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(false)
        _binding = null
    }
}
