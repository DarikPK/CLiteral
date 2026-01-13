package com.example.imageextractor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.imageextractor.databinding.FragmentSignatureSettingsBinding

class SignatureSettingsFragment : Fragment() {

    private var _binding: FragmentSignatureSettingsBinding? = null
    private val binding get() = _binding!!

    private val sharedPrefs by lazy {
        requireActivity().getSharedPreferences("PdfSettings", Context.MODE_PRIVATE)
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            pickImageLauncher.launch("image/*")
        } else {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Permiso Requerido")
                .setMessage("Para seleccionar una imagen de firma, necesitas conceder el permiso de acceso al almacenamiento. Por favor, actívalo en los ajustes de la aplicación.")
                .setPositiveButton("Ir a Ajustes") { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", requireActivity().packageName, null)
                    intent.data = uri
                    startActivity(intent)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val imagePath = it.toString()
            saveString("signature_image_uri", imagePath)
            loadSignaturePreview()
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
        loadSettings()
        setupListeners()
    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    private fun loadSettings() {
        binding.signatureEnabledCheckbox.isChecked = sharedPrefs.getBoolean("signature_enabled", false)
        binding.signatureScaleSlider.value = sharedPrefs.getFloat("signature_scale", 100f)
        binding.signatureRotationSlider.value = sharedPrefs.getFloat("signature_rotation", 0f)
        binding.signatureOffsetXEditText.setText(sharedPrefs.getString("signature_offset_x", "0"))
        binding.signatureOffsetYEditText.setText(sharedPrefs.getString("signature_offset_y", "0"))
        loadSignaturePreview()
    }

    private fun setupListeners() {
        binding.selectSignatureButton.setOnClickListener {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            requestPermissionLauncher.launch(permission)
        }

        binding.removeSignatureButton.setOnClickListener {
            sharedPrefs.edit().remove("signature_image_uri").apply()
            loadSignaturePreview()
            Toast.makeText(requireContext(), "Firma personalizada eliminada", Toast.LENGTH_SHORT).show()
        }

        binding.signatureEnabledCheckbox.setOnCheckedChangeListener { _, isChecked -> saveBoolean("signature_enabled", isChecked) }
        binding.signatureScaleSlider.addOnChangeListener { _, value, _ -> saveFloat("signature_scale", value) }
        binding.signatureRotationSlider.addOnChangeListener { _, value, _ -> saveFloat("signature_rotation", value) }
        binding.signatureOffsetXEditText.doOnTextChanged { text, _, _, _ -> saveString("signature_offset_x", text.toString()) }
        binding.signatureOffsetYEditText.doOnTextChanged { text, _, _, _ -> saveString("signature_offset_y", text.toString()) }
    }

    private fun loadSignaturePreview() {
        val imageUriString = sharedPrefs.getString("signature_image_uri", null)
        if (imageUriString != null) {
            Glide.with(this)
                .load(Uri.parse(imageUriString))
                .into(binding.signaturePreviewImageView)
        } else {
            // No custom image, show the default digital signature
            val defaultSignature = generateDigitalSignatureBitmap()
            binding.signaturePreviewImageView.setImageBitmap(defaultSignature)
        }
    }

    private fun generateDigitalSignatureBitmap(): Bitmap {
        val width = 500
        val height = 200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            color = Color.parseColor("#2557A8") // A nice blue
            style = Paint.Style.STROKE
            strokeWidth = 10f
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val path = Path()
        // Path meticulously designed to replicate the user's provided signature image.
        path.moveTo(30f, 115f)
        // First major curve, forming the 'M' shape
        path.cubicTo(50f, 20f, 170f, 30f, 180f, 100f)
        // Second curve, forming the 'u' shape
        path.cubicTo(190f, 160f, 280f, 40f, 300f, 100f)
        // Third curve
        path.cubicTo(320f, 140f, 380f, 60f, 400f, 100f)
        // Final tail of the signature
        path.quadTo(440f, 115f, 480f, 105f)

        canvas.drawPath(path, paint)
        return bitmap
    }


    // SharedPreferences helpers
    private fun saveString(key: String, value: String) {
        sharedPrefs.edit().putString(key, value).apply()
    }

    private fun saveBoolean(key: String, value: Boolean) {
        sharedPrefs.edit().putBoolean(key, value).apply()
    }

    private fun saveFloat(key: String, value: Float) {
        sharedPrefs.edit().putFloat(key, value).apply()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
