package com.example.imageextractor

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.net.Uri
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.imageextractor.databinding.FragmentStamp2SettingsBinding
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class Stamp2SettingsFragment : Fragment() {

    private var _binding: FragmentStamp2SettingsBinding? = null
    private val binding get() = _binding!!

    private val sharedPrefs by lazy {
        requireActivity().getSharedPreferences("PdfSettings", Context.MODE_PRIVATE)
    }

    private val firebaseManager = FirebaseManager()

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
        binding.stamp2TranslationToleranceXEditText.setText(sharedPrefs.getString("stamp2_translation_tolerance_x", "0"))
        binding.stamp2TranslationToleranceYEditText.setText(sharedPrefs.getString("stamp2_translation_tolerance_y", "0"))
        binding.stamp2DotCountEditText.setText(getStringPreferenceSafely("stamp2DotCount", "stamp2_dot_count", "3"))
        binding.stamp2DotSizeEditText.setText(getStringPreferenceSafely("stamp2DotSize", "stamp2_dot_size", "13"))
        binding.stamp2PointTextSeparationEditText.setText(sharedPrefs.getString("stamp2_point_text_separation", "5"))
        binding.stamp2BrightnessEditText.setText(getStringPreferenceSafely("stamp2Brightness", "stamp2_brightness", "50"))
        binding.stamp2ContrastEditText.setText(getStringPreferenceSafely("stamp2Contrast", "stamp2_contrast", "50"))


        binding.stamp2WearIntensitySlider.value = sharedPrefs.getFloat("stamp2_wear_intensity", 30f)
        binding.stamp2WearSizeSlider.value = sharedPrefs.getFloat("stamp2_wear_size", 50f)

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
        binding.stamp2TranslationToleranceXEditText.doOnTextChanged { text, _, _, _ -> saveString("stamp2_translation_tolerance_x", text.toString()) }
        binding.stamp2TranslationToleranceYEditText.doOnTextChanged { text, _, _, _ -> saveString("stamp2_translation_tolerance_y", text.toString()) }

        // First click listeners to clear default text
        setupFirstClickListener(binding.stamp2NameEditText)
        setupFirstClickListener(binding.stamp2PositionEditText)
        setupFirstClickListener(binding.stamp2AreaEditText)

        binding.stamp2DotCountEditText.doOnTextChanged { text, _, _, _ -> saveString("stamp2DotCount", text.toString()) }
        binding.stamp2DotSizeEditText.doOnTextChanged { text, _, _, _ -> saveString("stamp2DotSize", text.toString()) }

        binding.stamp2PointTextSeparationEditText.doOnTextChanged { text, _, _, _ -> saveString("stamp2_point_text_separation", text.toString()) }
        binding.stamp2BrightnessEditText.doOnTextChanged { text, _, _, _ -> saveString("stamp2Brightness", text.toString()) }
        binding.stamp2ContrastEditText.doOnTextChanged { text, _, _, _ -> saveString("stamp2Contrast", text.toString()) }

        // Auto-save for Sliders
        binding.stamp2WearIntensitySlider.addOnChangeListener { _, value, _ -> saveFloat("stamp2_wear_intensity", value) }
        binding.stamp2WearSizeSlider.addOnChangeListener { _, value, _ -> saveFloat("stamp2_wear_size", value) }

        binding.saveCloudButton.setOnClickListener { saveProfileToFirebase() }
        binding.loadCloudButton.setOnClickListener { showLoadProfilesDialog() }
    }

    private fun saveProfileToFirebase() {
        lifecycleScope.launch {
            val profile = RegistrarProfile(
                id = sharedPrefs.getString("firebase_profile_id", "") ?: "",
                name = sharedPrefs.getString("stamp2_name", "NOMBRE APELLIDO") ?: "NOMBRE APELLIDO",
                position = sharedPrefs.getString("stamp2_position", "CARGO") ?: "CARGO",
                area = sharedPrefs.getString("stamp2_area", "ZONA REGISTRAL") ?: "ZONA REGISTRAL",
                office = sharedPrefs.getString("oficina", "LIMA") ?: "LIMA",
                signatureMarkers = sharedPrefs.getString("signature_markers", "") ?: "",
                signatureRotationTolerance = sharedPrefs.getFloat("signature_rotation_tolerance", 0f),
                signatureWhiteThreshold = sharedPrefs.getFloat("signature_white_threshold", 210f),
                stamp2FontSize = sharedPrefs.getString("stamp2_font_size", "13") ?: "13",
                stamp2WearIntensity = sharedPrefs.getFloat("stamp2_wear_intensity", 30f),
                stamp2WearSize = sharedPrefs.getFloat("stamp2_wear_size", 50f)
            )

            val result = firebaseManager.saveProfile(profile)
            if (result.isSuccess) {
                Toast.makeText(requireContext(), "Perfil unificado guardado en la nube", Toast.LENGTH_SHORT).show()
            } else {
                val error = result.exceptionOrNull()
                Toast.makeText(requireContext(), "Error al guardar: ${error?.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showLoadProfilesDialog() {
        lifecycleScope.launch {
            val result = firebaseManager.getAllProfiles()
            if (result.isSuccess) {
                val profiles = result.getOrNull() ?: emptyList()
                if (profiles.isEmpty()) {
                    Toast.makeText(requireContext(), "No hay perfiles en la nube", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val profileNames = profiles.map { "${it.name} (${it.office})" }.toTypedArray()
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Cargar Perfil Completo")
                    .setItems(profileNames) { _, which ->
                        loadProfileIntoSettings(profiles[which])
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            } else {
                Toast.makeText(requireContext(), "Error al cargar: ${result.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadProfileIntoSettings(profile: RegistrarProfile) {
        sharedPrefs.edit().apply {
            putString("firebase_profile_id", profile.id)
            putString("stamp2_name", profile.name)
            putString("stamp2_position", profile.position)
            putString("stamp2_area", profile.area)
            putString("oficina", profile.office)
            putString("signature_markers", profile.signatureMarkers)
            putFloat("signature_rotation_tolerance", profile.signatureRotationTolerance)
            putFloat("signature_white_threshold", profile.signatureWhiteThreshold)
            putString("stamp2_font_size", profile.stamp2FontSize)
            putFloat("stamp2_wear_intensity", profile.stamp2WearIntensity)
            putFloat("stamp2_wear_size", profile.stamp2WearSize)
            apply()
        }

        loadSettings()
        Toast.makeText(requireContext(), "Perfil de ${profile.name} cargado por completo", Toast.LENGTH_SHORT).show()
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
        // Interceptar la rotación del sello para sincronizarla con la firma
        if (key == "stamp2_rotation") {
            sharedPrefs.edit()
                .putString("stamp2_rotation", value)
                .putFloat("signature_rotation", value.toFloatOrNull() ?: 0f)
                .apply()
        } else {
            sharedPrefs.edit().putString(key, value).apply()
        }
    }

    private fun saveBoolean(key: String, value: Boolean) {
        sharedPrefs.edit().putBoolean(key, value).apply()
    }

    private fun saveFloat(key: String, value: Float) {
        sharedPrefs.edit().putFloat(key, value).apply()
    }

    private fun getStringPreferenceSafely(newKey: String, oldKey: String, defaultValue: String): String {
        val value = sharedPrefs.all[newKey] ?: sharedPrefs.all[oldKey]
        return when (value) {
            is String -> value
            is Float -> value.toInt().toString()
            else -> defaultValue
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
