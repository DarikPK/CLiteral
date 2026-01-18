package com.example.imageextractor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.imageextractor.databinding.FragmentStamp1SettingsBinding
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch

class Stamp1SettingsFragment : Fragment() {

    private var _binding: FragmentStamp1SettingsBinding? = null
    private val binding get() = _binding!!

    private var registradorId: String? = null
    private var registrador: Registrador? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            registradorId = it.getString("registradorId")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStamp1SettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        loadRegistradorData()
    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun loadRegistradorData() {
        registradorId?.let { id ->
            lifecycleScope.launch {
                // Suponiendo que FirestoreService tiene una función para obtener un registrador por ID.
                // Esta función la crearemos en el `FirestoreService`.
                // registrador = FirestoreService.getRegistrador(id)
                // Por ahora, simularemos la carga con los datos por defecto.
                registrador = Registrador() // Carga un registrador con valores por defecto
                populateUi()
                setupSaveListeners()
            }
        }
    }

    private fun populateUi() {
        registrador?.let {
            binding.stampEnabledCheckbox.isChecked = it.stampDateEnabled
            binding.stampOnFirstLastPageCheckbox.isChecked = it.stampDateOnFirstLast
            binding.stampSizeEditText.setText(it.stampDateSizePercent.toInt().toString())
            binding.stampFontSizeEditText.setText(it.stampDateFontSize.toInt().toString())
            binding.stampRotationEditText.setText(it.stampDateMaxRotation.toInt().toString())
            binding.stampWearIntensitySlider.value = it.stampDateWearIntensity
            binding.stampWearSizeSlider.value = it.stampDateWearSize
            binding.stampBrightnessEditText.setText(it.stampDateBrightness.toInt().toString())
            binding.stampContrastEditText.setText(it.stampDateContrast.toInt().toString())
        }
    }

    private fun setupSaveListeners() {
        binding.stampEnabledCheckbox.setOnCheckedChangeListener { _, isChecked ->
            registrador?.stampDateEnabled = isChecked
            saveRegistradorData()
        }
        binding.stampOnFirstLastPageCheckbox.setOnCheckedChangeListener { _, isChecked ->
            registrador?.stampDateOnFirstLast = isChecked
            saveRegistradorData()
        }
        binding.stampSizeEditText.doOnTextChanged { text, _, _, _ ->
            registrador?.stampDateSizePercent = text.toString().toFloatOrNull() ?: 20f
            saveRegistradorData()
        }
        binding.stampFontSizeEditText.doOnTextChanged { text, _, _, _ ->
            registrador?.stampDateFontSize = text.toString().toFloatOrNull() ?: 220f
            saveRegistradorData()
        }
        binding.stampRotationEditText.doOnTextChanged { text, _, _, _ ->
            registrador?.stampDateMaxRotation = text.toString().toFloatOrNull() ?: 5f
            saveRegistradorData()
        }
        binding.stampWearIntensitySlider.addOnChangeListener(Slider.OnChangeListener { _, value, _ ->
            registrador?.stampDateWearIntensity = value
            saveRegistradorData()
        })
        binding.stampWearSizeSlider.addOnChangeListener(Slider.OnChangeListener { _, value, _ ->
            registrador?.stampDateWearSize = value
            saveRegistradorData()
        })
        binding.stampBrightnessEditText.doOnTextChanged { text, _, _, _ ->
            registrador?.stampDateBrightness = text.toString().toFloatOrNull() ?: 50f
            saveRegistradorData()
        }
        binding.stampContrastEditText.doOnTextChanged { text, _, _, _ ->
            registrador?.stampDateContrast = text.toString().toFloatOrNull() ?: 50f
            saveRegistradorData()
        }
    }

    private fun saveRegistradorData() {
        registrador?.let {
            lifecycleScope.launch {
                // Suponiendo que FirestoreService tiene una función de actualización.
                // Esta función la crearemos en el `FirestoreService`.
                // FirestoreService.updateRegistrador(it)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
