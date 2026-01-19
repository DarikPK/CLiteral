package com.example.imageextractor

import android.os.Build
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
    private var isLoading = true

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
        isLoading = true
        populateUi()
        setupChangeListeners()
        isLoading = false
        checkForChanges() // Comprobar una vez después de la carga inicial
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
        val action = { checkForChanges() }
        binding.stampEnabledCheckbox.setOnCheckedChangeListener { _, _ -> action() }
        binding.stampOnFirstLastPageCheckbox.setOnCheckedChangeListener { _, _ -> action() }
        binding.stampSizeEditText.doOnTextChanged { _, _, _, _ -> currentRegistrador?.stampDateSizePercent = binding.stampSizeEditText.text.toString().toFloatOrNull() ?: 20f; action() }
        binding.stampFontSizeEditText.doOnTextChanged { _, _, _, _ -> currentRegistrador?.stampDateFontSize = binding.stampFontSizeEditText.text.toString().toFloatOrNull() ?: 220f; action() }
        binding.stampRotationEditText.doOnTextChanged { _, _, _, _ -> currentRegistrador?.stampDateMaxRotation = binding.stampRotationEditText.text.toString().toFloatOrNull() ?: 5f; action() }
        binding.stampWearIntensitySlider.addOnChangeListener { _, value, _ -> currentRegistrador?.stampDateWearIntensity = value; action() }
        binding.stampWearSizeSlider.addOnChangeListener { _, value, _ -> currentRegistrador?.stampDateWearSize = value; action() }
        binding.stampBrightnessEditText.doOnTextChanged { _, _, _, _ -> currentRegistrador?.stampDateBrightness = binding.stampBrightnessEditText.text.toString().toFloatOrNull() ?: 50f; action() }
        binding.stampContrastEditText.doOnTextChanged { _, _, _, _ -> currentRegistrador?.stampDateContrast = binding.stampContrastEditText.text.toString().toFloatOrNull() ?: 50f; action() }
    }

    private fun checkForChanges() {
        val i = initialRegistrador
        val c = currentRegistrador
        if (i == null || c == null) {
            saveMenuItem?.isEnabled = false
            saveMenuItem?.icon?.alpha = 130 // Deshabilitado
            return
        }

        val hasChanges = i.stampDateEnabled != c.stampDateEnabled ||
                i.stampDateOnFirstLast != c.stampDateOnFirstLast ||
                !i.stampDateSizePercent.isCloseTo(c.stampDateSizePercent) ||
                !i.stampDateFontSize.isCloseTo(c.stampDateFontSize) ||
                !i.stampDateMaxRotation.isCloseTo(c.stampDateMaxRotation) ||
                !i.stampDateWearIntensity.isCloseTo(c.stampDateWearIntensity) ||
                !i.stampDateWearSize.isCloseTo(c.stampDateWearSize) ||
                !i.stampDateBrightness.isCloseTo(c.stampDateBrightness) ||
                !i.stampDateContrast.isCloseTo(c.stampDateContrast)

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

private fun Float.isCloseTo(other: Float, tolerance: Float = 0.01f): Boolean {
    return Math.abs(this - other) < tolerance
}
