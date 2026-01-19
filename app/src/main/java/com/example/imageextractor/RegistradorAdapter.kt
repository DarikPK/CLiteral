package com.example.imageextractor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.imageextractor.databinding.ItemRegistradorBinding

class RegistradorAdapter(
    private var registradores: List<Registrador>,
    private var activeRegistradorId: String?,
    private val onItemClicked: (Registrador) -> Unit
) : RecyclerView.Adapter<RegistradorAdapter.RegistradorViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RegistradorViewHolder {
        val binding = ItemRegistradorBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RegistradorViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RegistradorViewHolder, position: Int) {
        val registrador = registradores[position]
        holder.bind(registrador, registrador.id == activeRegistradorId)
        holder.itemView.setOnClickListener {
            onItemClicked(registrador)
        }
    }

    override fun getItemCount(): Int = registradores.size

    fun updateData(newRegistradores: List<Registrador>, newActiveId: String?) {
        registradores = newRegistradores
        activeRegistradorId = newActiveId
        notifyDataSetChanged()
    }

    class RegistradorViewHolder(private val binding: ItemRegistradorBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(registrador: Registrador, isActive: Boolean) {
            binding.registradorNameTextView.text = registrador.nombre
            binding.registradorDetailsTextView.text = "${registrador.cargo} - ${registrador.zonaRegistral}"

            // Muestra u oculta el checkmark en lugar de cambiar el color de fondo
            binding.checkIcon.visibility = if (isActive) View.VISIBLE else View.GONE
        }
    }
}
