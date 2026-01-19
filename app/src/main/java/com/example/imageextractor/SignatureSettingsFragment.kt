package com.example.imageextractor

import android.graphics.PointF
import android.net.Uri
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

    private var registradorId: String? = null
    private var initialRegistrador: Registrador? = null
    private var currentRegistrador: Registrador? = null
    private var isPrimarySignatureSelected = true
    private var saveMenuItem: MenuItem? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            if (isPrimarySignatureSelected) {
                currentRegistrador?.signatureImageUri = it.toString()
            } else {
                currentRegistrador?.signature2ImageUri = it.toString()
            }
            binding.signatureCanvasView.clearCanvas(switchMode = true)
            checkForChanges()
            Toast.makeText(requireContext(), "Imagen de firma seleccionada. Se ha borrado la firma dibujada.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            registradorId = it.getString("registradorId")
        }
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSignatureSettingsBinding.inflate(inflater, container, false)
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
                    setupListeners()
                } else {
                    Toast.makeText(requireContext(), "Error: No se pudo cargar el registrador.", Toast.LENGTH_LONG).show()
                    findNavController().popBackStack()
                }
            }
        }
    }

    private fun populateUi() {
        currentRegistrador?.let { reg ->
            if (isPrimarySignatureSelected) {
                binding.signatureEnabledCheckbox.isChecked = reg.signatureEnabled
                binding.signatureScaleSlider.value = reg.signatureScale
                binding.signatureRotationSlider.value = reg.signatureRotation
                binding.signatureOffsetXEditText.setText(reg.signatureOffsetX.toInt().toString())
                binding.signatureOffsetYEditText.setText(reg.signatureOffsetY.toInt().toString())
                binding.signatureCanvasView.setMarkerContours(parsePoints(reg.signaturePoints))
            } else {
                binding.signatureEnabledCheckbox.isChecked = reg.signature2Enabled
                binding.signatureScaleSlider.value = reg.signature2Scale
                binding.signatureRotationSlider.value = reg.signature2Rotation
                binding.signatureOffsetXEditText.setText(reg.signature2OffsetX.toInt().toString())
                binding.signatureOffsetYEditText.setText(reg.signature2OffsetY.toInt().toString())
                binding.signatureCanvasView.setMarkerContours(parsePoints(reg.signature2Points))
            }
        }
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

        binding.signatureCanvasView.setMarkerListener {
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
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun saveMarkersAndUpdateChanges() {
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
