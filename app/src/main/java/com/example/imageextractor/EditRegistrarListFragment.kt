package com.example.imageextractor

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.imageextractor.databinding.FragmentEditRegistrarListBinding
import kotlinx.coroutines.launch

class EditRegistrarListFragment : Fragment() {

    private var _binding: FragmentEditRegistrarListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: EditRegistradorAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEditRegistrarListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerView()
        loadData()
    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    private fun setupRecyclerView() {
        adapter = EditRegistradorAdapter(
            registradores = emptyList(),
            onEditClicked = { registrador ->
                val bundle = bundleOf("registradorId" to registrador.id)
                findNavController().navigate(R.id.action_editRegistrarListFragment_to_editRegistrarHubFragment, bundle)
            },
            onDeleteClicked = { registrador ->
                showDeleteConfirmationDialog(registrador)
            }
        )
        binding.editRegistradoresRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.editRegistradoresRecyclerView.adapter = adapter
    }

    private fun loadData() {
        lifecycleScope.launch {
            val registradores = FirestoreService.getRegistradores()
            adapter.updateData(registradores)
        }
    }

    private fun showDeleteConfirmationDialog(registrador: Registrador) {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirmar Eliminación")
            .setMessage("¿Estás seguro de que quieres eliminar al registrador '${registrador.nombre}'? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                lifecycleScope.launch {
                    FirestoreService.deleteRegistrador(registrador.id)
                    loadData() // Recargar la lista
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
