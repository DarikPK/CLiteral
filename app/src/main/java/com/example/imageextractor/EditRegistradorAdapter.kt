package com.example.imageextractor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.imageextractor.databinding.ItemEditRegistradorBinding

class EditRegistradorAdapter(
    private var registradores: List<Registrador>,
    private val onEditClicked: (Registrador) -> Unit,
    private val onDeleteClicked: (Registrador) -> Unit
) : RecyclerView.Adapter<EditRegistradorAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEditRegistradorBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val registrador = registradores[position]
        holder.bind(registrador)
        holder.binding.editButton.setOnClickListener { onEditClicked(registrador) }
        holder.binding.deleteButton.setOnClickListener { onDeleteClicked(registrador) }
    }

    override fun getItemCount() = registradores.size

    fun updateData(newRegistradores: List<Registrador>) {
        registradores = newRegistradores
        notifyDataSetChanged()
    }

    class ViewHolder(val binding: ItemEditRegistradorBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(registrador: Registrador) {
            binding.registradorNameTextView.text = registrador.nombre
            binding.registradorDetailsTextView.text = "${registrador.cargo} - ${registrador.zonaRegistral}"
        }
    }
}
