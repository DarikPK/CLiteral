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
import com.example.imageextractor.databinding.FragmentStamp2SettingsBinding
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch

class Stamp2SettingsFragment : Fragment() {

    private var _binding: FragmentStamp2SettingsBinding? = null
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
        _binding = FragmentStamp2SettingsBinding.inflate(inflater, container, false)
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
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    private fun loadRegistradorData() {
        registradorId?.let { id ->
            lifecycleScope.launch {
                // registrador = FirestoreService.getRegistrador(id)
                registrador = Registrador() // Simulación
                populateUi()
                setupSaveListeners()
            }
        }
    }

    private fun populateUi() {
        registrador?.let {
            binding.stamp2EnabledCheckbox.isChecked = it.stampRegistrarEnabled
            binding.stamp2NameEditText.setText(it.nombre)
            binding.stamp2PositionEditText.setText(it.cargo)
            binding.stamp2AreaEditText.setText(it.zonaRegistral)
            binding.stamp2FontSizeEditText.setText(it.stampRegistrarFontSize.toInt().toString())
            binding.stamp2OffsetXEditText.setText(it.stampRegistrarOffsetX.toInt().toString())
            binding.stamp2OffsetYEditText.setText(it.stampRegistrarOffsetY.toInt().toString())
            binding.stamp2VariableRotationCheckbox.isChecked = it.stampRegistrarVariableRotation
            binding.stamp2RotationEditText.setText(it.stampRegistrarRotation.toInt().toString())
            binding.stamp2RotationToleranceEditText.setText(it.stampRegistrarRotationTolerance.toInt().toString())
            binding.stamp2DotCountEditText.setText(it.stampRegistrarDotCount.toInt().toString())
            binding.stamp2DotSizeEditText.setText(it.stampRegistrarDotSize.toInt().toString())
            binding.stamp2PointTextSeparationEditText.setText(it.stampRegistrarPointTextSeparation.toInt().toString())
            binding.stamp2BrightnessEditText.setText(it.stampRegistrarBrightness.toInt().toString())
            binding.stamp2ContrastEditText.setText(it.stampRegistrarContrast.toInt().toString())
            binding.stamp2WearIntensitySlider.value = it.stampRegistrarWearIntensity
            binding.stamp2WearSizeSlider.value = it.stampRegistrarWearSize
        }
    }

    private fun setupSaveListeners() {
        binding.stamp2EnabledCheckbox.setOnCheckedChangeListener { _, isChecked ->
            registrador?.stampRegistrarEnabled = isChecked
            saveRegistradorData()
        }
        binding.stamp2NameEditText.doOnTextChanged { text, _, _, _ ->
            registrador?.nombre = text.toString()
            saveRegistradorData()
        }
        // ... (resto de listeners para cada campo)
    }

    private fun saveRegistradorData() {
        registrador?.let {
            lifecycleScope.launch {
                // FirestoreService.updateRegistrador(it)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
