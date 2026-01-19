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
        setupToolbar()
        populateUi()
        setupListeners()
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
        if (initialRegistrador == null || currentRegistrador == null) {
            saveMenuItem?.isEnabled = false
            saveMenuItem?.icon?.alpha = 130
            return
        }
        val hasChanges = initialRegistrador?.signatureEnabled != currentRegistrador?.signatureEnabled ||
                initialRegistrador?.signatureImageUri != currentRegistrador?.signatureImageUri ||
                initialRegistrador?.signatureOffsetX != currentRegistrador?.signatureOffsetX ||
                initialRegistrador?.signatureOffsetY != currentRegistrador?.signatureOffsetY ||
                initialRegistrador?.signatureScale != currentRegistrador?.signatureScale ||
                initialRegistrador?.signatureRotation != currentRegistrador?.signatureRotation ||
                initialRegistrador?.signaturePoints != currentRegistrador?.signaturePoints ||
                initialRegistrador?.signature2Enabled != currentRegistrador?.signature2Enabled ||
                initialRegistrador?.signature2ImageUri != currentRegistrador?.signature2ImageUri ||
                initialRegistrador?.signature2OffsetX != currentRegistrador?.signature2OffsetX ||
                initialRegistrador?.signature2OffsetY != currentRegistrador?.signature2OffsetY ||
                initialRegistrador?.signature2Scale != currentRegistrador?.signature2Scale ||
                initialRegistrador?.signature2Rotation != currentRegistrador?.signature2Rotation ||
                initialRegistrador?.signature2Points != currentRegistrador?.signature2Points

        saveMenuItem?.isEnabled = hasChanges
        saveMenuItem?.icon?.alpha = if (hasChanges) 255 else 130
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
