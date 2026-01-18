package com.example.imageextractor

import android.graphics.PointF
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.imageextractor.databinding.FragmentSignatureSettingsBinding
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch

class SignatureSettingsFragment : Fragment() {

    private var _binding: FragmentSignatureSettingsBinding? = null
    private val binding get() = _binding!!

    private var registradorId: String? = null
    private var registrador: Registrador? = null
    private var isPrimarySignatureSelected = true

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            if (isPrimarySignatureSelected) {
                registrador?.signatureImageUri = it.toString()
            } else {
                registrador?.signature2ImageUri = it.toString()
            }
            binding.signatureCanvasView.clearCanvas(switchMode = true)
            saveRegistradorData()
            Toast.makeText(requireContext(), "Imagen de firma seleccionada. Se ha borrado la firma dibujada.", Toast.LENGTH_LONG).show()
        }
    }

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
        _binding = FragmentSignatureSettingsBinding.inflate(inflater, container, false)
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
                registrador = FirestoreService.getRegistrador(id)
                if (registrador != null) {
                    populateUi()
                    setupListeners()
                } else {
                    Toast.makeText(requireContext(), "Error: No se pudo cargar el registrador.", Toast.LENGTH_LONG).show()
                    findNavController().popBackStack()
                }
            }
        }
    }

    private fun populateUi() {
        registrador?.let { reg ->
            val (enabled, scale, rotation, offsetX, offsetY, points) = if (isPrimarySignatureSelected) {
                Sixple(reg.signatureEnabled, reg.signatureScale, reg.signatureRotation, reg.signatureOffsetX, reg.signatureOffsetY, reg.signaturePoints)
            } else {
                Sixple(reg.signature2Enabled, reg.signature2Scale, reg.signature2Rotation, reg.signature2OffsetX, reg.signature2OffsetY, reg.signature2Points)
            }

            binding.signatureEnabledCheckbox.isChecked = enabled
            binding.signatureScaleSlider.value = scale
            binding.signatureRotationSlider.value = rotation
            binding.signatureOffsetXEditText.setText(offsetX.toInt().toString())
            binding.signatureOffsetYEditText.setText(offsetY.toInt().toString())

            val contours = if (points.isNotEmpty()) {
                points.split("|").map { contourString ->
                    contourString.split(";").mapNotNull {
                        val parts = it.split(",")
                        if (parts.size == 2) PointF(parts[0].toFloat(), parts[1].toFloat()) else null
                    }
                }
            } else {
                emptyList()
            }
            binding.signatureCanvasView.setMarkerContours(contours)
            updateButtonLabels()
        }
    }

    private fun setupListeners() {
        binding.signatureEnabledCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isPrimarySignatureSelected) registrador?.signatureEnabled = isChecked else registrador?.signature2Enabled = isChecked
            saveRegistradorData()
        }
        binding.signatureScaleSlider.addOnChangeListener { _, value, _ ->
            if (isPrimarySignatureSelected) registrador?.signatureScale = value else registrador?.signature2Scale = value
            saveRegistradorData()
        }
        binding.signatureRotationSlider.addOnChangeListener { _, value, _ ->
            if (isPrimarySignatureSelected) registrador?.signatureRotation = value else registrador?.signature2Rotation = value
            saveRegistradorData()
        }
        binding.signatureOffsetXEditText.doOnTextChanged { text, _, _, _ ->
            val value = text.toString().toFloatOrNull() ?: 0f
            if (isPrimarySignatureSelected) registrador?.signatureOffsetX = value else registrador?.signature2OffsetX = value
            saveRegistradorData()
        }
        binding.signatureOffsetYEditText.doOnTextChanged { text, _, _, _ ->
            val value = text.toString().toFloatOrNull() ?: 0f
            if (isPrimarySignatureSelected) registrador?.signatureOffsetY = value else registrador?.signature2OffsetY = value
            saveRegistradorData()
        }

        binding.signatureCanvasView.setMarkerListener {
            saveMarkers()
        }

        // Spinner para seleccionar entre firma principal y secundaria
        val signatureTypes = listOf("Firma Principal", "Firma Secundaria")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, signatureTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.signatureSelectionSpinner.adapter = adapter
        binding.signatureSelectionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val isPrimary = position == 0
                if (isPrimary != isPrimarySignatureSelected) {
                    saveMarkers() // Guardar los puntos de la firma actual antes de cambiar
                    isPrimarySignatureSelected = isPrimary
                    populateUi() // Recargar la UI con los datos de la otra firma
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun saveMarkers() {
        val contours = binding.signatureCanvasView.getMarkerContours()
        val markersString = contours.joinToString("|") { contour ->
            contour.joinToString(";") { "${it.x},${it.y}" }
        }
        if (isPrimarySignatureSelected) {
            registrador?.signaturePoints = markersString
        } else {
            registrador?.signature2Points = markersString
        }
        saveRegistradorData()
    }

    private fun updateButtonLabels() {
        if (binding.signatureCanvasView.mode == SignatureCanvasView.Mode.DRAW) {
            binding.primaryActionButton.text = "Generar Marcadores"
        } else {
            binding.primaryActionButton.text = "Refrescar Firma"
        }
    }

    private fun saveRegistradorData() {
        registrador?.let {
            lifecycleScope.launch {
                FirestoreService.updateRegistrador(it)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Helper data class for managing signature data temporarily
private data class Sixple<A, B, C, D, E, F>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E, val sixth: F)
