package com.example.imageextractor

import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
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
        _binding = FragmentStamp2SettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isLoading = true
        setupToolbar()
        populateUi()
        setupChangeListeners()
        setupBackButtonInterceptor()
        binding.previewStamp2Button.setOnClickListener {
            generateStamp2Preview()
        }
        generateStamp2Preview()
    }

    private fun generateStamp2Preview() {
        val name = binding.stamp2NameEditText.text.toString().ifEmpty { "NOMBRE APELLIDO" }
        val position = binding.stamp2PositionEditText.text.toString().ifEmpty { "CARGO" }
        val area = binding.stamp2AreaEditText.text.toString().ifEmpty { "ZONA REGISTRAL" }
        val fontSize = binding.stamp2FontSizeEditText.text.toString().toFloatOrNull() ?: 13f
        val dotCount = binding.stamp2DotCountEditText.text.toString().toIntOrNull() ?: 3
        val dotSize = binding.stamp2DotSizeEditText.text.toString().toFloatOrNull() ?: 13f
        val pointTextSeparation = binding.stamp2PointTextSeparationEditText.text.toString().toFloatOrNull() ?: 5f
        val wearIntensity = binding.stamp2WearIntensitySlider.value
        val wearSize = binding.stamp2WearSizeSlider.value
        val brightness = binding.stamp2BrightnessEditText.text.toString().toFloatOrNull() ?: 50f
        val contrast = binding.stamp2ContrastEditText.text.toString().toFloatOrNull() ?: 50f

        var previewBitmap = createStamp2Bitmap(name, position, area, fontSize, dotCount, dotSize, pointTextSeparation)
        previewBitmap = applyStamp2Adjustments(previewBitmap, brightness, contrast)

        val normalizedIntensity = (wearIntensity / 100f) / 5f
        val normalizedSize = (wearSize / 100f) / 5f
        previewBitmap = applyInkWear(previewBitmap, normalizedIntensity, normalizedSize, System.currentTimeMillis())

        binding.stamp2PreviewImageView.setImageBitmap(previewBitmap)
        binding.stamp2PreviewImageView.visibility = View.VISIBLE
    }

    private fun createStamp2Bitmap(name: String, position: String, area: String, fontSize: Float, dotCount: Int, dotSize: Float, pointTextSeparation: Float): Bitmap {
        val textPaint = Paint().apply {
            color = Color.parseColor("#00008B")
            textSize = fontSize
            typeface = Typeface.create("Arial", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val dotPaint = Paint().apply {
            color = Color.parseColor("#00008B")
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val textLines = listOf(name.uppercase(), position.uppercase(), area.uppercase())
        val textBounds = Rect()
        textPaint.getTextBounds("A", 0, 1, textBounds)
        val lineHeight = textBounds.height() * 1.5f
        val totalTextHeight = textLines.size * lineHeight
        val maxTextWidth = textLines.maxOf { textPaint.measureText(it) }
        val radius = dotSize / 2f
        val spacing = radius * 2.5f
        val totalDotsWidth = if (dotCount > 0) (dotCount - 1) * spacing + (radius * 2) else 0f
        val bitmapWidth = (kotlin.math.max(maxTextWidth, totalDotsWidth) + 40).toInt()
        val dotsHeight = if (dotCount > 0) (radius * 2) + 5f else 0f
        val bitmapHeight = (totalTextHeight + dotsHeight + pointTextSeparation + 20).toInt()
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val xPos = bitmapWidth / 2f
        var yPos: Float
        if (dotCount > 0) {
            val startX = xPos - ((dotCount - 1) * spacing) / 2f
            yPos = radius + 5f
            repeat(dotCount) { i -> canvas.drawCircle(startX + i * spacing, yPos, radius, dotPaint) }
        }
        yPos = if (dotCount > 0) dotsHeight + pointTextSeparation + lineHeight - textBounds.bottom else 10 + lineHeight - textBounds.bottom
        textLines.forEach { line ->
            canvas.drawText(line, xPos, yPos, textPaint)
            yPos += lineHeight
        }
        return bitmap
    }

    private fun applyStamp2Adjustments(originalBitmap: Bitmap, brightness: Float, contrast: Float): Bitmap {
        if (brightness == 50f && contrast == 50f) return originalBitmap
        val brightnessValue = (brightness - 50) * 5f
        val contrastValue = contrast / 50f
        val colorMatrix = ColorMatrix(floatArrayOf(
            contrastValue, 0f, 0f, 0f, brightnessValue,
            0f, contrastValue, 0f, 0f, brightnessValue,
            0f, 0f, contrastValue, 0f, brightnessValue,
            0f, 0f, 0f, 1f, 0f
        ))
        val adjustedBitmap = Bitmap.createBitmap(originalBitmap.width, originalBitmap.height, originalBitmap.config)
        adjustedBitmap.density = originalBitmap.density
        val canvas = Canvas(adjustedBitmap)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(colorMatrix) }
        canvas.drawBitmap(originalBitmap, 0f, 0f, paint)
        return adjustedBitmap
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
        isLoading = false
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
        if (isLoading) return
        val i = initialRegistrador
        val c = currentRegistrador
        if (i == null || c == null) {
            saveMenuItem?.isEnabled = false
            saveMenuItem?.icon?.alpha = 130
            return
        }

        val hasChanges = i.stampRegistrarEnabled != c.stampRegistrarEnabled ||
                i.nombre != c.nombre ||
                i.cargo != c.cargo ||
                i.zonaRegistral != c.zonaRegistral ||
                !i.stampRegistrarFontSize.isCloseTo(c.stampRegistrarFontSize) ||
                !i.stampRegistrarOffsetX.isCloseTo(c.stampRegistrarOffsetX) ||
                !i.stampRegistrarOffsetY.isCloseTo(c.stampRegistrarOffsetY) ||
                i.stampRegistrarVariableRotation != c.stampRegistrarVariableRotation ||
                !i.stampRegistrarRotation.isCloseTo(c.stampRegistrarRotation) ||
                !i.stampRegistrarRotationTolerance.isCloseTo(c.stampRegistrarRotationTolerance) ||
                !i.stampRegistrarDotCount.isCloseTo(c.stampRegistrarDotCount) ||
                !i.stampRegistrarDotSize.isCloseTo(c.stampRegistrarDotSize) ||
                !i.stampRegistrarPointTextSeparation.isCloseTo(c.stampRegistrarPointTextSeparation) ||
                !i.stampRegistrarBrightness.isCloseTo(c.stampRegistrarBrightness) ||
                !i.stampRegistrarContrast.isCloseTo(c.stampRegistrarContrast) ||
                !i.stampRegistrarWearIntensity.isCloseTo(c.stampRegistrarWearIntensity) ||
                !i.stampRegistrarWearSize.isCloseTo(c.stampRegistrarWearSize)

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
