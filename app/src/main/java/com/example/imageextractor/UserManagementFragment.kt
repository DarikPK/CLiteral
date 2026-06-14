package com.example.imageextractor

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.imageextractor.databinding.FragmentUserManagementBinding
import kotlinx.coroutines.launch

class UserManagementFragment : Fragment() {

    private var _binding: FragmentUserManagementBinding? = null
    private val binding get() = _binding!!
    private val firebaseManager = FirebaseManager()
    private var selectedProfileId: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUserManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupDniWatcher()
        setupButtons()
        loadProfiles()
    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupDniWatcher() {
        binding.dniEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val dni = s?.toString() ?: ""
                if (dni.length == 8) {
                    val dv = calculateDV(dni)
                    val numericDV = getNumericDV(dv)
                    val alphaDV = getAlphaDV(dv)
                    binding.dvEditText.setText(numericDV)
                    binding.dvAlphaText.text = alphaDV
                } else {
                    binding.dvEditText.setText("")
                    binding.dvAlphaText.text = "-"
                }
            }
        })
    }

    private fun calculateDV(dni: String): Int {
        if (dni.length != 8) return -1
        val factors = intArrayOf(3, 2, 7, 6, 5, 4, 3, 2)
        var sum = 0
        for (i in 0 until 8) {
            val digit = dni[i].toString().toInt()
            sum += digit * factors[i]
        }
        val remainder = sum % 11
        val sub = 11 - remainder
        val result = if (sub == 11) 0 else sub
        return result + 1
    }

    private fun getAlphaDV(dv: Int): String {
        val series = arrayOf("K", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J")
        return if (dv in 1..11) series[dv - 1] else "-"
    }

    private fun getNumericDV(dv: Int): String {
        val series = arrayOf("6", "7", "8", "9", "0", "1", "1", "2", "3", "4", "5")
        return if (dv in 1..11) series[dv - 1] else "-"
    }

    private fun setupButtons() {
        binding.saveUserButton.setOnClickListener {
            val dni = binding.dniEditText.text.toString()
            val dvNum = binding.dvEditText.text.toString()
            val dvAlpha = binding.dvAlphaText.text.toString()
            val fecha = binding.fechaExpEditText.text.toString()

            if (dni.length != 8 || dvNum.isEmpty() || fecha.isEmpty()) {
                Toast.makeText(context, "Por favor complete todos los campos correctamente", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Guardamos el dígito numérico para el login, ya que es lo más común en SUNARP
            val profile = UserProfile(
                id = selectedProfileId,
                dni = dni,
                digitoVerificador = dvNum,
                fechaExpedicion = fecha,
                label = "DNI $dni ($dvAlpha)"
            )

            lifecycleScope.launch {
                binding.saveUserButton.isEnabled = false
                val result = firebaseManager.saveUserProfile(profile)
                binding.saveUserButton.isEnabled = true

                if (result.isSuccess) {
                    Toast.makeText(context, "Usuario guardado exitosamente", Toast.LENGTH_SHORT).show()
                    clearForm()
                    loadProfiles()
                } else {
                    Toast.makeText(context, "Error al guardar: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun clearForm() {
        selectedProfileId = ""
        binding.dniEditText.setText("")
        binding.fechaExpEditText.setText("")
        binding.saveUserButton.text = "Guardar Usuario en Nube"
    }

    private fun loadProfiles() {
        lifecycleScope.launch {
            val result = firebaseManager.getAllUserProfiles()
            if (result.isSuccess) {
                val profiles = result.getOrNull() ?: emptyList()
                setupRecyclerView(profiles)
            }
        }
    }

    private fun setupRecyclerView(profiles: List<UserProfile>) {
        binding.usersRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.usersRecyclerView.adapter = object : RecyclerView.Adapter<UserViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
                val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
                return UserViewHolder(view)
            }

            override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
                val p = profiles[position]
                holder.text1.text = "${p.dni} - DV: ${p.digitoVerificador}"
                holder.text2.text = "Exp: ${p.fechaExpedicion}"
                holder.itemView.setOnClickListener {
                    selectedProfileId = p.id
                    binding.dniEditText.setText(p.dni)
                    binding.fechaExpEditText.setText(p.fechaExpedicion)
                    binding.saveUserButton.text = "Actualizar Usuario"
                }
                holder.itemView.setOnLongClickListener {
                    showDeleteDialog(p)
                    true
                }
            }

            override fun getItemCount() = profiles.size
        }
    }

    private fun showDeleteDialog(profile: UserProfile) {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Eliminar Usuario")
            .setMessage("¿Deseas eliminar al usuario con DNI ${profile.dni}?")
            .setPositiveButton("Eliminar") { _, _ ->
                lifecycleScope.launch {
                    val res = firebaseManager.deleteUserProfile(profile.id)
                    if (res.isSuccess) {
                        Toast.makeText(context, "Eliminado", Toast.LENGTH_SHORT).show()
                        loadProfiles()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text1: TextView = view.findViewById(android.R.id.text1)
        val text2: TextView = view.findViewById(android.R.id.text2)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
