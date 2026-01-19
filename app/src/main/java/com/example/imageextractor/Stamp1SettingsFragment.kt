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
import com.example.imageextractor.databinding.FragmentStamp1SettingsBinding
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch

class Stamp1SettingsFragment : Fragment() {

    private var _binding: FragmentStamp1SettingsBinding? = null
    private val binding get() = _binding!!

    private var initialRegistrador: Registrador? = null
    private var currentRegistrador: Registrador? = null
    private var saveMenuItem: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            currentRegistrador = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelable("registrador", Registrador::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getParcelable("registrador")
            }
            initialRegistrador = currentRegistrador?.copy()
        }
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStamp1SettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        populateUi()
        setupChangeListeners()
        setupBackButtonInterceptor()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_save, menu)
        saveMenuItem = menu.findItem(R.id.action_save)
        checkForChanges() // Actualiza la visibilidad inicial del botón
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
        binding.toolbar.setNavigationOnClickListener {
            handleNavigateBack()
        }
    }

    private fun populateUi() {
        currentRegistrador?.let {
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

    private fun setupChangeListeners() {
        binding.stampEnabledCheckbox.setOnCheckedChangeListener { _, isChecked ->
            currentRegistrador?.stampDateEnabled = isChecked
            checkForChanges()
        }
        binding.stampOnFirstLastPageCheckbox.setOnCheckedChangeListener { _, isChecked ->
            currentRegistrador?.stampDateOnFirstLast = isChecked
            checkForChanges()
        }
        binding.stampSizeEditText.doOnTextChanged { text, _, _, _ ->
            currentRegistrador?.stampDateSizePercent = text.toString().toFloatOrNull() ?: 20f
            checkForChanges()
        }
        binding.stampFontSizeEditText.doOnTextChanged { text, _, _, _ ->
            currentRegistrador?.stampDateFontSize = text.toString().toFloatOrNull() ?: 220f
            checkForChanges()
        }
        binding.stampRotationEditText.doOnTextChanged { text, _, _, _ ->
            currentRegistrador?.stampDateMaxRotation = text.toString().toFloatOrNull() ?: 5f
            checkForChanges()
        }
        binding.stampWearIntensitySlider.addOnChangeListener { _, value, _ ->
            currentRegistrador?.stampDateWearIntensity = value
            checkForChanges()
        }
        binding.stampWearSizeSlider.addOnChangeListener { _, value, _ ->
            currentRegistrador?.stampDateWearSize = value
            checkForChanges()
        }
        binding.stampBrightnessEditText.doOnTextChanged { text, _, _, _ ->
            currentRegistrador?.stampDateBrightness = text.toString().toFloatOrNull() ?: 50f
            checkForChanges()
        }
        binding.stampContrastEditText.doOnTextChanged { text, _, _, _ ->
            currentRegistrador?.stampDateContrast = text.toString().toFloatOrNull() ?: 50f
            checkForChanges()
        }
    }

    private fun checkForChanges() {
        if (initialRegistrador == null || currentRegistrador == null) {
            saveMenuItem?.isEnabled = false
            saveMenuItem?.icon?.alpha = 130 // Deshabilitado
            return
        }
        val hasChanges = initialRegistrador?.stampDateEnabled != currentRegistrador?.stampDateEnabled ||
                initialRegistrador?.stampDateOnFirstLast != currentRegistrador?.stampDateOnFirstLast ||
                initialRegistrador?.stampDateSizePercent != currentRegistrador?.stampDateSizePercent ||
                initialRegistrador?.stampDateFontSize != currentRegistrador?.stampDateFontSize ||
                initialRegistrador?.stampDateMaxRotation != currentRegistrador?.stampDateMaxRotation ||
                initialRegistrador?.stampDateWearIntensity != currentRegistrador?.stampDateWearIntensity ||
                initialRegistrador?.stampDateWearSize != currentRegistrador?.stampDateWearSize ||
                initialRegistrador?.stampDateBrightness != currentRegistrador?.stampDateBrightness ||
                initialRegistrador?.stampDateContrast != currentRegistrador?.stampDateContrast

        saveMenuItem?.isEnabled = hasChanges
        saveMenuItem?.icon?.alpha = if (hasChanges) 255 else 130 // Opacidad completa vs. atenuado
    }

    private fun saveChanges() {
        currentRegistrador?.let {
            lifecycleScope.launch {
                val success = FirestoreService.updateRegistrador(it)
                if (success) {
                    initialRegistrador = it.copy() // Actualiza el estado base
                    checkForChanges() // Oculta el botón de guardar
                    Toast.makeText(context, "Cambios guardados", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Error al guardar los cambios", Toast.LENGTH_SHORT).show()
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
                .setMessage("¿Estás seguro de que quieres salir sin guardar los cambios?")
                .setPositiveButton("Salir") { _, _ ->
                    findNavController().navigateUp()
                }
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
