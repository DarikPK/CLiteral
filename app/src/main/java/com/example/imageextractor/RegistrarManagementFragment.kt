package com.example.imageextractor

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.imageextractor.databinding.FragmentRegistrarManagementBinding
import kotlinx.coroutines.launch

class RegistrarManagementFragment : Fragment() {

    private var _binding: FragmentRegistrarManagementBinding? = null
    private val binding get() = _binding!!

    private val sharedPrefs by lazy {
        requireActivity().getSharedPreferences("PdfSettings", Context.MODE_PRIVATE)
    }

    private val firebaseManager = FirebaseManager()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegistrarManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        loadSettings()
        setupListeners()

        // Si regresamos de configurar firma, mostrar detalles directamente
        if (sharedPrefs.getBoolean("is_editing_registrar", false)) {
            binding.initialOptionsLayout.visibility = View.GONE
            binding.registrarDetailsLayout.visibility = View.VISIBLE
        }
    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    private fun loadSettings() {
        binding.showInPdfCheckbox.isChecked = sharedPrefs.getBoolean("show_in_pdf", true)
        binding.registrarNameEditText.setText(sharedPrefs.getString("stamp2_name", ""))
        binding.registrarPositionEditText.setText(sharedPrefs.getString("stamp2_position", ""))
        binding.registrarAreaEditText.setText(sharedPrefs.getString("stamp2_area", ""))

        binding.stamp2VariableRotationCheckbox.isChecked = sharedPrefs.getBoolean("stamp2_variable_rotation", true)
        binding.stamp2RotationEditText.setText(sharedPrefs.getString("stamp2_rotation", "0"))
        binding.stamp2RotationToleranceEditText.setText(sharedPrefs.getString("stamp2_rotation_tolerance", "5"))
        binding.stamp2TranslationToleranceXEditText.setText(sharedPrefs.getString("stamp2_translation_tolerance_x", "0"))
        binding.stamp2TranslationToleranceYEditText.setText(sharedPrefs.getString("stamp2_translation_tolerance_y", "0"))

        binding.stamp2WearIntensitySlider.value = sharedPrefs.getFloat("stamp2_wear_intensity", 30f)
        binding.stamp2WearSizeSlider.value = sharedPrefs.getFloat("stamp2_wear_size", 50f)
    }

    private fun setupListeners() {
        binding.createRegistrarButton.setOnClickListener {
            clearRegistrarFields()
            binding.initialOptionsLayout.visibility = View.GONE
            binding.registrarDetailsLayout.visibility = View.VISIBLE
        }

        binding.selectRegistrarButton.setOnClickListener {
            showLoadProfilesDialog()
        }

        binding.showInPdfCheckbox.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean("show_in_pdf", isChecked).apply()
        }

        binding.registrarNameEditText.doOnTextChanged { text, _, _, _ ->
            sharedPrefs.edit().putString("stamp2_name", text.toString()).apply()
        }
        binding.registrarPositionEditText.doOnTextChanged { text, _, _, _ ->
            sharedPrefs.edit().putString("stamp2_position", text.toString()).apply()
        }
        binding.registrarAreaEditText.doOnTextChanged { text, _, _, _ ->
            sharedPrefs.edit().putString("stamp2_area", text.toString()).apply()
        }

        binding.stamp2VariableRotationCheckbox.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean("stamp2_variable_rotation", isChecked).apply()
        }
        binding.stamp2RotationEditText.doOnTextChanged { text, _, _, _ ->
            sharedPrefs.edit().putString("stamp2_rotation", text.toString())
                .putFloat("signature_rotation", text.toString().toFloatOrNull() ?: 0f)
                .apply()
        }
        binding.stamp2RotationToleranceEditText.doOnTextChanged { text, _, _, _ ->
            sharedPrefs.edit().putString("stamp2_rotation_tolerance", text.toString()).apply()
        }
        binding.stamp2TranslationToleranceXEditText.doOnTextChanged { text, _, _, _ ->
            sharedPrefs.edit().putString("stamp2_translation_tolerance_x", text.toString()).apply()
        }
        binding.stamp2TranslationToleranceYEditText.doOnTextChanged { text, _, _, _ ->
            sharedPrefs.edit().putString("stamp2_translation_tolerance_y", text.toString()).apply()
        }

        binding.stamp2WearIntensitySlider.addOnChangeListener { _, value, _ ->
            sharedPrefs.edit().putFloat("stamp2_wear_intensity", value).apply()
        }
        binding.stamp2WearSizeSlider.addOnChangeListener { _, value, _ ->
            sharedPrefs.edit().putFloat("stamp2_wear_size", value).apply()
        }

        binding.configPrimarySignatureButton.setOnClickListener {
            sharedPrefs.edit().putBoolean("editing_secondary_signature", false).apply()
            val bundle = Bundle().apply { putBoolean("is_secondary", false) }
            findNavController().navigate(R.id.action_registrarManagementFragment_to_signatureSettingsFragment, bundle)
        }

        binding.configSecondarySignatureButton.setOnClickListener {
            sharedPrefs.edit().putBoolean("editing_secondary_signature", true).apply()
            val bundle = Bundle().apply { putBoolean("is_secondary", true) }
            findNavController().navigate(R.id.action_registrarManagementFragment_to_signatureSettingsFragment, bundle)
        }

        binding.saveRegistrarButton.setOnClickListener {
            saveProfileToFirebase()
        }
    }

    private fun clearRegistrarFields() {
        sharedPrefs.edit().apply {
            putBoolean("is_editing_registrar", true)
            remove("firebase_profile_id")
            putString("stamp2_name", "")
            putString("stamp2_position", "")
            putString("stamp2_area", "")
            putString("stamp2_rotation", "0")
            putString("stamp2_rotation_tolerance", "5")
            putString("stamp2_translation_tolerance_x", "0")
            putString("stamp2_translation_tolerance_y", "0")
            putBoolean("stamp2_variable_rotation", true)
            // Limpiar firmas y registros Base64
            remove("signature_markers")
            remove("signature_rotation")
            remove("signature_rotation_tolerance")
            remove("signature_white_threshold")
            remove("signature_size_x")
            remove("signature_size_y")
            remove("signature_offset_x")
            remove("signature_offset_y")
            remove("signature_image_uri")
            remove("signature_image_uris")
            remove("signature_images_base64")
            remove("signature_type_loaded")
            remove("signature_enabled")
            remove("signature_stroke_width")
            remove("signature_num_markers")
            remove("signature_marker_size")

            remove("signature_markers_secondary")
            remove("signature_rotation_secondary")
            remove("signature_rotation_tolerance_secondary")
            remove("signature_white_threshold_secondary")
            remove("signature_size_x_secondary")
            remove("signature_size_y_secondary")
            remove("signature_offset_x_secondary")
            remove("signature_offset_y_secondary")
            remove("signature_image_uri_secondary")
            remove("signature_image_uris_secondary")
            remove("signature_images_base64_secondary")
            remove("signature_type_loaded_secondary")
            remove("signature_enabled_secondary")
            remove("signature_stroke_width_secondary")
            remove("signature_num_markers_secondary")
            remove("signature_marker_size_secondary")
            apply()
        }
        loadSettings()
    }

    private fun saveProfileToFirebase() {
        lifecycleScope.launch {
            val profile = RegistrarProfile(
                id = sharedPrefs.getString("firebase_profile_id", "") ?: "",
                name = sharedPrefs.getString("stamp2_name", "") ?: "",
                position = sharedPrefs.getString("stamp2_position", "") ?: "",
                area = sharedPrefs.getString("stamp2_area", "") ?: "",
                office = sharedPrefs.getString("oficina", "LIMA") ?: "LIMA",
                signatureMarkers = sharedPrefs.getString("signature_markers", "") ?: "",
                signatureRotation = sharedPrefs.getFloat("signature_rotation", 0f),
                signatureRotationTolerance = sharedPrefs.getFloat("signature_rotation_tolerance", 0f),
                signatureWhiteThreshold = sharedPrefs.getFloat("signature_white_threshold", 210f),
                signatureSizeX = sharedPrefs.getFloat("signature_size_x", 50f),
                signatureSizeY = sharedPrefs.getFloat("signature_size_y", 20f),
                signatureOffsetX = sharedPrefs.getString("signature_offset_x", "0") ?: "0",
                signatureOffsetY = sharedPrefs.getString("signature_offset_y", "-19") ?: "-19",
                signatureTypeLoaded = sharedPrefs.getBoolean("signature_type_loaded", false),
                signatureEnabled = sharedPrefs.getBoolean("signature_enabled", true),
                signatureStrokeWidth = sharedPrefs.getFloat("signature_stroke_width", 5f),
                signatureNumMarkers = sharedPrefs.getInt("signature_num_markers", 15),
                signatureMarkerSize = sharedPrefs.getFloat("signature_marker_size", 10f),
                signatureMarkersSecondary = sharedPrefs.getString("signature_markers_secondary", "") ?: "",
                signatureRotationSecondary = sharedPrefs.getFloat("signature_rotation_secondary", 0f),
                signatureRotationToleranceSecondary = sharedPrefs.getFloat("signature_rotation_tolerance_secondary", 0f),
                signatureWhiteThresholdSecondary = sharedPrefs.getFloat("signature_white_threshold_secondary", 210f),
                signatureSizeXSecondary = sharedPrefs.getFloat("signature_size_x_secondary", 50f),
                signatureSizeYSecondary = sharedPrefs.getFloat("signature_size_y_secondary", 20f),
                signatureOffsetXSecondary = sharedPrefs.getString("signature_offset_x_secondary", "0") ?: "0",
                signatureOffsetYSecondary = sharedPrefs.getString("signature_offset_y_secondary", "-19") ?: "-19",
                signatureTypeLoadedSecondary = sharedPrefs.getBoolean("signature_type_loaded_secondary", false),
                signatureEnabledSecondary = sharedPrefs.getBoolean("signature_enabled_secondary", true),
                signatureStrokeWidthSecondary = sharedPrefs.getFloat("signature_stroke_width_secondary", 5f),
                signatureNumMarkersSecondary = sharedPrefs.getInt("signature_num_markers_secondary", 15),
                signatureMarkerSizeSecondary = sharedPrefs.getFloat("signature_marker_size_secondary", 10f),
                signatureImageUris = sharedPrefs.getString("signature_image_uris", "")?.split("|")?.filter { it.isNotBlank() } ?: emptyList(),
                signatureImageUrisSecondary = sharedPrefs.getString("signature_image_uris_secondary", "")?.split("|")?.filter { it.isNotBlank() } ?: emptyList(),
                signatureImagesBase64 = sharedPrefs.getString("signature_images_base64", "")?.split("|")?.filter { it.isNotBlank() } ?: emptyList(),
                signatureImagesBase64Secondary = sharedPrefs.getString("signature_images_base64_secondary", "")?.split("|")?.filter { it.isNotBlank() } ?: emptyList(),
                stamp2FontSize = sharedPrefs.getString("stamp2_font_size", "13") ?: "13",
                stamp2WearIntensity = sharedPrefs.getFloat("stamp2_wear_intensity", 30f),
                stamp2WearSize = sharedPrefs.getFloat("stamp2_wear_size", 50f),
                stamp2VariableRotation = sharedPrefs.getBoolean("stamp2_variable_rotation", true),
                stamp2Rotation = sharedPrefs.getString("stamp2_rotation", "0") ?: "0",
                stamp2RotationTolerance = sharedPrefs.getString("stamp2_rotation_tolerance", "5") ?: "5",
                stamp2TranslationToleranceX = sharedPrefs.getString("stamp2_translation_tolerance_x", "0") ?: "0",
                stamp2TranslationToleranceY = sharedPrefs.getString("stamp2_translation_tolerance_y", "0") ?: "0"
            )

            val result = firebaseManager.saveProfile(profile)
            if (result.isSuccess) {
                Toast.makeText(requireContext(), "Registrador guardado en la nube", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Error al guardar: ${result.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
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
                    .setTitle("Seleccionar Registrador")
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
        binding.initialOptionsLayout.visibility = View.GONE
        binding.registrarDetailsLayout.visibility = View.VISIBLE
        sharedPrefs.edit().apply {
            putBoolean("is_editing_registrar", true)
            putString("firebase_profile_id", profile.id)
            putString("stamp2_name", profile.name)
            putString("stamp2_position", profile.position)
            putString("stamp2_area", profile.area)
            putString("oficina", profile.office)
            putString("signature_markers", profile.signatureMarkers)
            putFloat("signature_rotation", profile.signatureRotation)
            putFloat("signature_rotation_tolerance", profile.signatureRotationTolerance)
            putFloat("signature_white_threshold", profile.signatureWhiteThreshold)
            putFloat("signature_size_x", profile.signatureSizeX)
            putFloat("signature_size_y", profile.signatureSizeY)
            putString("signature_offset_x", profile.signatureOffsetX)
            putString("signature_offset_y", profile.signatureOffsetY)
            putBoolean("signature_type_loaded", profile.signatureTypeLoaded)
            putBoolean("signature_enabled", profile.signatureEnabled)
            putFloat("signature_stroke_width", profile.signatureStrokeWidth)
            putInt("signature_num_markers", profile.signatureNumMarkers)
            putFloat("signature_marker_size", profile.signatureMarkerSize)
            putString("signature_markers_secondary", profile.signatureMarkersSecondary)
            putFloat("signature_rotation_secondary", profile.signatureRotationSecondary)
            putFloat("signature_rotation_tolerance_secondary", profile.signatureRotationToleranceSecondary)
            putFloat("signature_white_threshold_secondary", profile.signatureWhiteThresholdSecondary)
            putFloat("signature_size_x_secondary", profile.signatureSizeXSecondary)
            putFloat("signature_size_y_secondary", profile.signatureSizeYSecondary)
            putString("signature_offset_x_secondary", profile.signatureOffsetXSecondary)
            putString("signature_offset_y_secondary", profile.signatureOffsetYSecondary)
            putBoolean("signature_type_loaded_secondary", profile.signatureTypeLoadedSecondary)
            putBoolean("signature_enabled_secondary", profile.signatureEnabledSecondary)
            putFloat("signature_stroke_width_secondary", profile.signatureStrokeWidthSecondary)
            putInt("signature_num_markers_secondary", profile.signatureNumMarkersSecondary)
            putFloat("signature_marker_size_secondary", profile.signatureMarkerSizeSecondary)
            putString("signature_image_uris", profile.signatureImageUris.joinToString("|"))
            putString("signature_image_uris_secondary", profile.signatureImageUrisSecondary.joinToString("|"))
            putString("signature_images_base64", profile.signatureImagesBase64.joinToString("|"))
            putString("signature_images_base64_secondary", profile.signatureImagesBase64Secondary.joinToString("|"))
            putString("stamp2_font_size", profile.stamp2FontSize)
            putFloat("stamp2_wear_intensity", profile.stamp2WearIntensity)
            putFloat("stamp2_wear_size", profile.stamp2WearSize)
            putBoolean("stamp2_variable_rotation", profile.stamp2VariableRotation)
            putString("stamp2_rotation", profile.stamp2Rotation)
            putString("stamp2_rotation_tolerance", profile.stamp2RotationTolerance)
            putString("stamp2_translation_tolerance_x", profile.stamp2TranslationToleranceX)
            putString("stamp2_translation_tolerance_y", profile.stamp2TranslationToleranceY)
            apply()
        }

        loadSettings()
        Toast.makeText(requireContext(), "Registrador ${profile.name} cargado", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // No limpiar is_editing_registrar aquí para que persista al volver de firmas
        _binding = null
    }
}
