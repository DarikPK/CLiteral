package com.example.imageextractor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.imageextractor.databinding.FragmentEditingBinding
import com.google.android.material.tabs.TabLayoutMediator

class EditingFragment : Fragment() {

    private var _binding: FragmentEditingBinding? = null
    private val binding get() = _binding!!

    private lateinit var editingTabsAdapter: EditingTabsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inicializamos el adaptador para el ViewPager.
        editingTabsAdapter = EditingTabsAdapter(this)
        binding.viewPager.adapter = editingTabsAdapter

        // Conectamos el TabLayout con el ViewPager2 y establecemos los títulos de las pestañas.
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Ver Galería"
                1 -> "Editar Galería"
                else -> null
            }
        }.attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}