package com.example.imageextractor

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.imageextractor.databinding.FragmentStamp2SettingsBinding
import com.google.android.material.textfield.TextInputEditText

class Stamp2SettingsFragment : Fragment() {

    private var _binding: FragmentStamp2SettingsBinding? = null
    private val binding get() = _binding!!

    private val sharedPrefs by lazy {
        requireActivity().getSharedPreferences("PdfSettings", Context.MODE_PRIVATE)
    }

    // Tracks which fields have been clicked for the first time
    private val firstClickTracker = mutableSetOf<Int>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStamp2SettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        loadSettings()
        setupListeners()
    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    private fun loadSettings() {
        binding.stamp2EnabledCheckbox.isChecked = sharedPrefs.getBoolean("stamp2_enabled", true)
        binding.stamp2NameEditText.setText(sharedPrefs.getString("stamp2_name", "NOMBRE APELLIDO"))
        binding.stamp2PositionEditText.setText(sharedPrefs.getString("stamp2_position", "CARGO"))
        binding.stamp2AreaEditText.setText(sharedPrefs.getString("stamp2_area", "ZONA REGISTRAL"))
        binding.stamp2FontSizeEditText.setText(sharedPrefs.getString("stamp2_font_size", "13"))
        binding.stamp2OffsetXEditText.setText(sharedPrefs.getString("stamp2_offset_x", "0"))
        binding.stamp2OffsetYEditText.setText(sharedPrefs.getString("stamp2_offset_y", "0"))
        binding.stamp2VariableRotationCheckbox.isChecked = sharedPrefs.getBoolean("stamp2_variable_rotation", true)
        binding.stamp2RotationEditText.setText(sharedPrefs.getString("stamp2_rotation", "0"))
        binding.stamp2RotationToleranceEditText.setText(sharedPrefs.getString("stamp2_rotation_tolerance", "5"))

        // Initialize first click tracker based on default values
        if (binding.stamp2NameEditText.text.toString() == "NOMBRE APELLIDO") firstClickTracker.add(binding.stamp2NameEditText.id)
        if (binding.stamp2PositionEditText.text.toString() == "CARGO") firstClickTracker.add(binding.stamp2PositionEditText.id)
        if (binding.stamp2AreaEditText.text.toString() == "ZONA REGISTRAL") firstClickTracker.add(binding.stamp2AreaEditText.id)
    }

    private fun setupListeners() {
        // Auto-save listeners
        binding.stamp2EnabledCheckbox.setOnCheckedChangeListener { _, isChecked -> saveBoolean("stamp2_enabled", isChecked) }
        binding.stamp2NameEditText.doOnTextChanged { text, _, _, _ -> saveString("stamp2_name", text.toString()) }
        binding.stamp2PositionEditText.doOnTextChanged { text, _, _, _ -> saveString("stamp2_position", text.toString()) }
        binding.stamp2AreaEditText.doOnTextChanged { text, _, _, _ -> saveString("stamp2_area", text.toString()) }
        binding.stamp2FontSizeEditText.doOnTextChanged { text, _, _, _ -> saveString("stamp2_font_size", text.toString()) }
        binding.stamp2OffsetXEditText.doOnTextChanged { text, _, _, _ -> saveString("stamp2_offset_x", text.toString()) }
        binding.stamp2OffsetYEditText.doOnTextChanged { text, _, _, _ -> saveString("stamp2_offset_y", text.toString()) }
        binding.stamp2VariableRotationCheckbox.setOnCheckedChangeListener { _, isChecked -> saveBoolean("stamp2_variable_rotation", isChecked) }
        binding.stamp2RotationEditText.doOnTextChanged { text, _, _, _ -> saveString("stamp2_rotation", text.toString()) }
        binding.stamp2RotationToleranceEditText.doOnTextChanged { text, _, _, _ -> saveString("stamp2_rotation_tolerance", text.toString()) }

        // First click listeners to clear default text
        setupFirstClickListener(binding.stamp2NameEditText)
        setupFirstClickListener(binding.stamp2PositionEditText)
        setupFirstClickListener(binding.stamp2AreaEditText)
    }

    private fun setupFirstClickListener(editText: TextInputEditText) {
        editText.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus && firstClickTracker.contains(view.id)) {
                (view as EditText).text.clear()
                firstClickTracker.remove(view.id)
            }
        }
    }

    // SharedPreferences helpers
    private fun saveString(key: String, value: String) {
        sharedPrefs.edit().putString(key, value).apply()
    }

    private fun saveBoolean(key: String, value: Boolean) {
        sharedPrefs.edit().putBoolean(key, value).apply()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
