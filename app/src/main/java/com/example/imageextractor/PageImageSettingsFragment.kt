package com.example.imageextractor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import com.example.imageextractor.databinding.FragmentPageImageSettingsBinding

class PageImageSettingsFragment : Fragment() {

    private var _binding: FragmentPageImageSettingsBinding? = null
    private val binding get() = _binding!!

    private val sharedPrefs by lazy {
        requireActivity().getSharedPreferences("PdfSettings", android.content.Context.MODE_PRIVATE)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPageImageSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        binding.brightnessEditText.setText(sharedPrefs.getString("brightness", "27"))
        binding.contrastEditText.setText(sharedPrefs.getString("contrast", "100"))
        binding.marginTopEditText.setText(sharedPrefs.getString("margin_top", "55"))
        binding.marginBottomEditText.setText(sharedPrefs.getString("margin_bottom", "50"))
        binding.marginLeftEditText.setText(sharedPrefs.getString("margin_left", "0"))
        binding.marginRightEditText.setText(sharedPrefs.getString("margin_right", "15"))
    }

    private fun setupListeners() {
        binding.brightnessEditText.doOnTextChanged { text, _, _, _ -> saveString("brightness", text.toString()) }
        binding.contrastEditText.doOnTextChanged { text, _, _, _ -> saveString("contrast", text.toString()) }
        binding.marginTopEditText.doOnTextChanged { text, _, _, _ -> saveString("margin_top", text.toString()) }
        binding.marginBottomEditText.doOnTextChanged { text, _, _, _ -> saveString("margin_bottom", text.toString()) }
        binding.marginLeftEditText.doOnTextChanged { text, _, _, _ -> saveString("margin_left", text.toString()) }
        binding.marginRightEditText.doOnTextChanged { text, _, _, _ -> saveString("margin_right", text.toString()) }
    }

    private fun saveString(key: String, value: String) {
        sharedPrefs.edit().putString(key, value).apply()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
