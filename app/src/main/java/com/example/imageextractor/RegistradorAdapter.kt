package com.example.imageextractor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.imageextractor.databinding.ItemRegistradorBinding

/**
 * Adaptador para mostrar una lista de objetos Registrador en un RecyclerView.
 * @param registradores La lista de registradores a mostrar.
 * @param onItemClicked Una función lambda que se invoca cuando se hace clic en un registrador.
 */
class RegistradorAdapter(
    private var registradores: List<Registrador>,
    private val onItemClicked: (Registrador) -> Unit
) : RecyclerView.Adapter<RegistradorAdapter.RegistradorViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RegistradorViewHolder {
        val binding = ItemRegistradorBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RegistradorViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RegistradorViewHolder, position: Int) {
        val registrador = registradores[position]
        holder.bind(registrador)
        holder.itemView.setOnClickListener {
            onItemClicked(registrador)
        }
    }

    override fun getItemCount(): Int = registradores.size

    /**
     * Actualiza la lista de registradores en el adaptador y notifica los cambios.
     */
    fun updateData(newRegistradores: List<Registrador>) {
        registradores = newRegistradores
        notifyDataSetChanged()
    }

    class RegistradorViewHolder(private val binding: ItemRegistradorBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(registrador: Registrador) {
            binding.registradorNameTextView.text = registrador.nombre
            binding.registradorDetailsTextView.text = "${registrador.cargo} - ${registrador.zonaRegistral}"
        }
    }
}
