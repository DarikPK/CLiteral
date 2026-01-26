package com.example.imageextractor

import android.graphics.ImageDecoder
import android.graphics.PointF
import android.net.Uri
import com.example.imageextractor.SignatureCanvasView
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.imageextractor.databinding.FragmentSignatureSettingsBinding
import kotlinx.coroutines.launch

class SignatureSettingsFragment : Fragment() {

    private var _binding: FragmentSignatureSettingsBinding? = null
    private val binding get() = _binding!!

    private var initialRegistrador: Registrador? = null
    private var currentRegistrador: Registrador? = null
    private var isPrimarySignatureSelected = true
    private var saveMenuItem: MenuItem? = null
    private var isLoading = true

    private val pngPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                val source = ImageDecoder.createSource(requireActivity().contentResolver, it)
                val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
                val threshold = binding.brightnessThresholdSlider.value.toInt()
                binding.signatureCanvasView.traceBitmapToMarkers(bitmap, threshold)
                saveMarkersAndUpdateChanges()
                Toast.makeText(requireContext(), "Firma importada correctamente.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error al cargar la imagen: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

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
        _binding = FragmentSignatureSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isLoading = true
        setupToolbar()
        populateUi()
        setupListeners()
        setupBackButtonInterceptor()

        binding.brightnessThresholdSlider.visibility = View.GONE
        binding.brightnessThresholdLabel.visibility = View.GONE

        isLoading = false
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

    private fun populateUi() {
        currentRegistrador?.let { reg ->
            if (isPrimarySignatureSelected) {
                binding.signatureEnabledCheckbox.isChecked = reg.signatureEnabled
                binding.signatureScaleSlider.value = reg.signatureScale
                binding.signatureRotationSlider.value = reg.signatureRotation
                binding.signatureOffsetXEditText.setText(reg.signatureOffsetX.toInt().toString())
                binding.signatureOffsetYEditText.setText(reg.signatureOffsetY.toInt().toString())
                binding.signatureWearIntensitySlider.value = reg.signatureWearIntensity
                binding.signatureWearSizeSlider.value = reg.signatureWearSize
                binding.signatureStartThicknessSlider.value = reg.signatureStartThickness
                binding.signatureMidThicknessSlider.value = reg.signatureMidThickness
                binding.signatureEndThicknessSlider.value = reg.signatureEndThickness
                binding.signatureCanvasView.setMarkerContours(parsePoints(reg.signaturePoints))
            } else {
                binding.signatureEnabledCheckbox.isChecked = reg.signature2Enabled
                binding.signatureScaleSlider.value = reg.signature2Scale
                binding.signatureRotationSlider.value = reg.signature2Rotation
                binding.signatureOffsetXEditText.setText(reg.signature2OffsetX.toInt().toString())
                binding.signatureOffsetYEditText.setText(reg.signature2OffsetY.toInt().toString())
                binding.signatureWearIntensitySlider.value = reg.signature2WearIntensity
                binding.signatureWearSizeSlider.value = reg.signature2WearSize
                binding.signatureStartThicknessSlider.value = reg.signature2StartThickness
                binding.signatureMidThicknessSlider.value = reg.signature2MidThickness
                binding.signatureEndThicknessSlider.value = reg.signature2EndThickness
                binding.signatureCanvasView.setMarkerContours(parsePoints(reg.signature2Points))
            }
        // Poblar campos de configuración del canvas
        binding.signatureNumMarkersEditText.setText(binding.signatureCanvasView.getNumMarkers().toString())
        binding.signatureMarkerSizeEditText.setText(binding.signatureCanvasView.getMarkerRadius().toInt().toString())
            updateSignatureParameters()
        }
    }

    private fun updateSignatureParameters() {
        val intensity = binding.signatureWearIntensitySlider.value
        val size = binding.signatureWearSizeSlider.value
        binding.signatureCanvasView.setWearParameters(intensity, size)

        val startThickness = binding.signatureStartThicknessSlider.value
        val midThickness = binding.signatureMidThicknessSlider.value
        val endThickness = binding.signatureEndThicknessSlider.value
        binding.signatureCanvasView.setThicknessParameters(startThickness, midThickness, endThickness)
    }

    private fun parsePoints(pointsString: String?): List<List<PointF>> {
        if (pointsString.isNullOrEmpty()) return emptyList()
        return pointsString.split("|").map { contourString ->
            contourString.split(";").mapNotNull {
                val parts = it.split(",")
                if (parts.size == 2) PointF(parts[0].toFloat(), parts[1].toFloat()) else null
            }
        }
    }

    private fun setupListeners() {
        binding.signatureEnabledCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isPrimarySignatureSelected) currentRegistrador?.signatureEnabled = isChecked else currentRegistrador?.signature2Enabled = isChecked
            checkForChanges()
        }
        binding.signatureScaleSlider.addOnChangeListener { _, value, _ ->
            if (isPrimarySignatureSelected) currentRegistrador?.signatureScale = value else currentRegistrador?.signature2Scale = value
            checkForChanges()
        }
        binding.signatureRotationSlider.addOnChangeListener { _, value, _ ->
            if (isPrimarySignatureSelected) currentRegistrador?.signatureRotation = value else currentRegistrador?.signature2Rotation = value
            checkForChanges()
        }
        binding.signatureOffsetXEditText.doOnTextChanged { text, _, _, _ ->
            val value = text.toString().toFloatOrNull() ?: 0f
            if (isPrimarySignatureSelected) currentRegistrador?.signatureOffsetX = value else currentRegistrador?.signature2OffsetX = value
            checkForChanges()
        }
        binding.signatureOffsetYEditText.doOnTextChanged { text, _, _, _ ->
            val value = text.toString().toFloatOrNull() ?: 0f
            if (isPrimarySignatureSelected) currentRegistrador?.signatureOffsetY = value else currentRegistrador?.signature2OffsetY = value
            checkForChanges()
        }

        binding.signatureWearIntensitySlider.addOnChangeListener { _, value, _ ->
            if (isPrimarySignatureSelected) currentRegistrador?.signatureWearIntensity = value else currentRegistrador?.signature2WearIntensity = value
            updateSignatureParameters()
            checkForChanges()
        }

        binding.signatureWearSizeSlider.addOnChangeListener { _, value, _ ->
            if (isPrimarySignatureSelected) currentRegistrador?.signatureWearSize = value else currentRegistrador?.signature2WearSize = value
            updateSignatureParameters()
            checkForChanges()
        }

        binding.signatureStartThicknessSlider.addOnChangeListener { _, value, _ ->
            if (isPrimarySignatureSelected) currentRegistrador?.signatureStartThickness = value else currentRegistrador?.signature2StartThickness = value
            updateSignatureParameters()
            checkForChanges()
        }

        binding.signatureMidThicknessSlider.addOnChangeListener { _, value, _ ->
            if (isPrimarySignatureSelected) currentRegistrador?.signatureMidThickness = value else currentRegistrador?.signature2MidThickness = value
            updateSignatureParameters()
            checkForChanges()
        }

        binding.signatureEndThicknessSlider.addOnChangeListener { _, value, _ ->
            if (isPrimarySignatureSelected) currentRegistrador?.signatureEndThickness = value else currentRegistrador?.signature2EndThickness = value
            updateSignatureParameters()
            checkForChanges()
        }

        binding.signatureCanvasView.setMarkerListener {
            saveMarkersAndUpdateChanges()
        }

        binding.signatureNumMarkersEditText.doOnTextChanged { text, _, _, _ ->
            val numMarkers = text.toString().toIntOrNull()
            if (numMarkers != null) {
                binding.signatureCanvasView.setNumMarkers(numMarkers)
            }
        }

        binding.signatureMarkerSizeEditText.doOnTextChanged { text, _, _, _ ->
            val markerSize = text.toString().toFloatOrNull() ?: 10f
            binding.signatureCanvasView.setMarkerRadius(markerSize)
        }

        binding.importPngButton.setOnClickListener {
            binding.brightnessThresholdSlider.visibility = View.VISIBLE
            binding.brightnessThresholdLabel.visibility = View.VISIBLE
            pngPickerLauncher.launch("image/png")
        }

        binding.secondaryActionButton.setOnClickListener {
            binding.signatureCanvasView.clearCanvas()
            saveMarkersAndUpdateChanges() // Guardar los puntos (ahora vacíos)
        }

        binding.primaryActionButton.setOnClickListener {
            val numMarkers = binding.signatureNumMarkersEditText.text.toString().toIntOrNull()
            if (numMarkers != null) {
                binding.signatureCanvasView.setNumMarkers(numMarkers)
            }

            if (binding.signatureCanvasView.mode == SignatureCanvasView.Mode.DRAW) {
                binding.signatureCanvasView.switchToEditMode()
            } else {
                // Para ambos casos (dibujado o importado), si estamos en modo edición,
                // queremos recalcular los marcadores basados en el trazo original.
                binding.signatureCanvasView.recalculateMarkersFromBasePath()
            }
            saveMarkersAndUpdateChanges()
        }

        val signatureTypes = listOf("Firma Principal", "Firma Secundaria")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, signatureTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.signatureSelectionSpinner.adapter = adapter
        binding.signatureSelectionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val isPrimary = position == 0
                if (isPrimary != isPrimarySignatureSelected) {
                    isPrimarySignatureSelected = isPrimary
                    populateUi()
                    binding.signatureCanvasView.invalidate() // Forzar redibujado
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun saveMarkersAndUpdateChanges() {
        if (_binding == null) return
        val contours = binding.signatureCanvasView.getMarkerContours()
        val markersString = contours.joinToString("|") { c -> c.joinToString(";") { "${it.x},${it.y}" } }
        if (isPrimarySignatureSelected) {
            currentRegistrador?.signaturePoints = markersString
        } else {
            currentRegistrador?.signature2Points = markersString
        }
        checkForChanges()
    }

    private fun checkForChanges() {
        if (isLoading) return
        val i = initialRegistrador
        val c = currentRegistrador
        if (i == null || c == null) {
            saveMenuItem?.isEnabled = false
            saveMenuItem?.icon?.alpha = 130
            return
        }

        val hasChanges = i.signatureEnabled != c.signatureEnabled ||
                i.signatureImageUri != c.signatureImageUri ||
                !i.signatureOffsetX.isCloseTo(c.signatureOffsetX) ||
                !i.signatureOffsetY.isCloseTo(c.signatureOffsetY) ||
                !i.signatureScale.isCloseTo(c.signatureScale) ||
                !i.signatureRotation.isCloseTo(c.signatureRotation) ||
                i.signaturePoints != c.signaturePoints ||
                !i.signatureWearIntensity.isCloseTo(c.signatureWearIntensity) ||
                !i.signatureWearSize.isCloseTo(c.signatureWearSize) ||
                !i.signatureStartThickness.isCloseTo(c.signatureStartThickness) ||
                !i.signatureMidThickness.isCloseTo(c.signatureMidThickness) ||
                !i.signatureEndThickness.isCloseTo(c.signatureEndThickness) ||
                i.signature2Enabled != c.signature2Enabled ||
                i.signature2ImageUri != c.signature2ImageUri ||
                !i.signature2OffsetX.isCloseTo(c.signature2OffsetX) ||
                !i.signature2OffsetY.isCloseTo(c.signature2OffsetY) ||
                !i.signature2Scale.isCloseTo(c.signature2Scale) ||
                !i.signature2Rotation.isCloseTo(c.signature2Rotation) ||
                i.signature2Points != c.signature2Points ||
                !i.signature2WearIntensity.isCloseTo(c.signature2WearIntensity) ||
                !i.signature2WearSize.isCloseTo(c.signature2WearSize) ||
                !i.signature2StartThickness.isCloseTo(c.signature2StartThickness) ||
                !i.signature2MidThickness.isCloseTo(c.signature2MidThickness) ||
                !i.signature2EndThickness.isCloseTo(c.signature2EndThickness)

        saveMenuItem?.isEnabled = hasChanges
        saveMenuItem?.icon?.alpha = if (hasChanges) 255 else 130
    }

    private fun saveChanges() {
        currentRegistrador?.let { registrador ->
            lifecycleScope.launch {
                val success = FirestoreService.updateRegistrador(registrador)
                if (success) {
                    initialRegistrador = registrador.copy()
                    checkForChanges()
                    Toast.makeText(context, "Cambios guardados", Toast.LENGTH_SHORT).show()

                    findNavController().previousBackStackEntry?.savedStateHandle?.set("updatedRegistrador", registrador)
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

private fun Float.isCloseTo(other: Float, tolerance: Float = 0.01f): Boolean {
    return Math.abs(this - other) < tolerance
}
