package com.example.imageextractor

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.imageextractor.databinding.FragmentStamp2SettingsBinding
import kotlinx.coroutines.launch

class Stamp2SettingsFragment : Fragment() {

    private var _binding: FragmentStamp2SettingsBinding? = null
    private val binding get() = _binding!!

    private var registradorId: String? = null
    private var initialRegistrador: Registrador? = null
    private var currentRegistrador: Registrador? = null
    private var saveMenuItem: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            registradorId = it.getString("registradorId")
        }
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStamp2SettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        loadRegistradorData()
        setupBackButtonInterceptor()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_save, menu)
        saveMenuItem = menu.findItem(R.id.action_save)
        checkForChanges()
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save -> {
                saveChanges()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { handleNavigateBack() }
    }

    private fun loadRegistradorData() {
        registradorId?.let { id ->
            lifecycleScope.launch {
                val registradorFromDb = FirestoreService.getRegistrador(id)
                if (registradorFromDb != null) {
                    initialRegistrador = registradorFromDb.copy()
                    currentRegistrador = registradorFromDb.copy()
                    populateUi()
                    setupChangeListeners()
                }
            }
        }
    }

    private fun populateUi() {
        currentRegistrador?.let {
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

    private fun setupChangeListeners() {
        // Simple fields
        binding.stamp2EnabledCheckbox.setOnCheckedChangeListener { _, isChecked -> currentRegistrador?.stampRegistrarEnabled = isChecked; checkForChanges() }
        binding.stamp2NameEditText.doOnTextChanged { text, _, _, _ -> currentRegistrador?.nombre = text.toString(); checkForChanges() }
        binding.stamp2PositionEditText.doOnTextChanged { text, _, _, _ -> currentRegistrador?.cargo = text.toString(); checkForChanges() }
        binding.stamp2AreaEditText.doOnTextChanged { text, _, _, _ -> currentRegistrador?.zonaRegistral = text.toString(); checkForChanges() }
        binding.stamp2FontSizeEditText.doOnTextChanged { text, _, _, _ -> currentRegistrador?.stampRegistrarFontSize = text.toString().toFloatOrNull() ?: 13f; checkForChanges() }
        binding.stamp2OffsetXEditText.doOnTextChanged { text, _, _, _ -> currentRegistrador?.stampRegistrarOffsetX = text.toString().toFloatOrNull() ?: 0f; checkForChanges() }
        binding.stamp2OffsetYEditText.doOnTextChanged { text, _, _, _ -> currentRegistrador?.stampRegistrarOffsetY = text.toString().toFloatOrNull() ?: 0f; checkForChanges() }
        binding.stamp2VariableRotationCheckbox.setOnCheckedChangeListener { _, isChecked -> currentRegistrador?.stampRegistrarVariableRotation = isChecked; checkForChanges() }
        binding.stamp2RotationEditText.doOnTextChanged { text, _, _, _ -> currentRegistrador?.stampRegistrarRotation = text.toString().toFloatOrNull() ?: 0f; checkForChanges() }
        binding.stamp2RotationToleranceEditText.doOnTextChanged { text, _, _, _ -> currentRegistrador?.stampRegistrarRotationTolerance = text.toString().toFloatOrNull() ?: 5f; checkForChanges() }
        binding.stamp2DotCountEditText.doOnTextChanged { text, _, _, _ -> currentRegistrador?.stampRegistrarDotCount = text.toString().toFloatOrNull() ?: 3f; checkForChanges() }
        binding.stamp2DotSizeEditText.doOnTextChanged { text, _, _, _ -> currentRegistrador?.stampRegistrarDotSize = text.toString().toFloatOrNull() ?: 13f; checkForChanges() }
        binding.stamp2PointTextSeparationEditText.doOnTextChanged { text, _, _, _ -> currentRegistrador?.stampRegistrarPointTextSeparation = text.toString().toFloatOrNull() ?: 5f; checkForChanges() }
        binding.stamp2BrightnessEditText.doOnTextChanged { text, _, _, _ -> currentRegistrador?.stampRegistrarBrightness = text.toString().toFloatOrNull() ?: 50f; checkForChanges() }
        binding.stamp2ContrastEditText.doOnTextChanged { text, _, _, _ -> currentRegistrador?.stampRegistrarContrast = text.toString().toFloatOrNull() ?: 50f; checkForChanges() }

        // Sliders
        binding.stamp2WearIntensitySlider.addOnChangeListener { _, value, _ -> currentRegistrador?.stampRegistrarWearIntensity = value; checkForChanges() }
        binding.stamp2WearSizeSlider.addOnChangeListener { _, value, _ -> currentRegistrador?.stampRegistrarWearSize = value; checkForChanges() }
    }

    private fun checkForChanges() {
        val hasChanges = initialRegistrador != currentRegistrador
        saveMenuItem?.isVisible = hasChanges
    }

    private fun saveChanges() {
        currentRegistrador?.let {
            lifecycleScope.launch {
                val success = FirestoreService.updateRegistrador(it)
                if (success) {
                    initialRegistrador = it.copy()
                    checkForChanges()
                    Toast.makeText(context, "Cambios guardados", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Error al guardar", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupBackButtonInterceptor() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleNavigateBack()
            }
        })
    }

    private fun handleNavigateBack() {
        if (saveMenuItem?.isVisible == true) {
            AlertDialog.Builder(requireContext())
                .setTitle("Cambios no guardados")
                .setMessage("¿Quieres salir sin guardar?")
                .setPositiveButton("Salir") { _, _ -> findNavController().navigateUp() }
                .setNegativeButton("Cancelar", null)
                .show()
        } else {
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
