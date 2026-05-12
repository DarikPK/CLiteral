package com.example.imageextractor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.imageextractor.databinding.FragmentMainMenuBinding

class MainMenuFragment : Fragment() {

    private var _binding: FragmentMainMenuBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonToExtraction.setOnClickListener {
            findNavController().navigate(R.id.action_mainMenuFragment_to_extractionConfigFragment)
        }

        binding.buttonToEditing.setOnClickListener {
            findNavController().navigate(R.id.action_mainMenuFragment_to_editingFragment)
        }

        binding.buttonToPdf.setOnClickListener {
            findNavController().navigate(R.id.action_mainMenuFragment_to_pdfSettingsFragment)
        }

        binding.buttonToVoucher.setOnClickListener {
            findNavController().navigate(R.id.action_mainMenuFragment_to_voucherConfigFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}