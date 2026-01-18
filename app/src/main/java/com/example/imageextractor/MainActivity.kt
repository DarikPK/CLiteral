package com.example.imageextractor

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.example.imageextractor.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Lanza la migración en una corutina para no bloquear el hilo principal.
        lifecycleScope.launch {
            migrateInitialRegistrador()
        }
    }

    /**
     * Comprueba si es necesario crear el primer registrador y lo migra
     * desde SharedPreferences a Firestore. Esta operación se ejecuta solo una vez.
     */
    private suspend fun migrateInitialRegistrador() {
        if (FirestoreService.isRegistradoresCollectionEmpty()) {
            val sharedPrefs = getSharedPreferences("PdfSettings", Context.MODE_PRIVATE)

            // Obtiene los valores del Sello 2 para el nombre, cargo y zona.
            val nombre = sharedPrefs.getString("stamp2_name", "ALFARO MORILLO MELISSA KARINA") ?: "ALFARO MORILLO MELISSA KARINA"
            val cargo = sharedPrefs.getString("stamp2_position", "Registrador Público") ?: "Registrador Público"
            val zona = sharedPrefs.getString("stamp2_area", "Zona Registral N° IX - Sede Lima") ?: "Zona Registral N° IX - Sede Lima"

            val initialRegistrador = Registrador(
                nombre = nombre,
                cargo = cargo,
                zonaRegistral = zona,

                // Configuración Sello 1 (Fecha)
                stampDateEnabled = sharedPrefs.getBoolean("stamp_enabled", true),
                stampDateOnFirstLast = sharedPrefs.getBoolean("stamp_on_first_last", true),
                stampDateFontSize = sharedPrefs.getString("stamp_font_size", "220")?.toFloatOrNull() ?: 220f,
                stampDateWearIntensity = sharedPrefs.getFloat("stamp_wear_intensity", 30f),
                stampDateWearSize = sharedPrefs.getFloat("stamp_wear_size", 50f),
                stampDateSizePercent = sharedPrefs.getString("stamp_size", "20")?.toFloatOrNull() ?: 20f,
                stampDateMaxRotation = sharedPrefs.getString("stamp_rotation", "5")?.toFloatOrNull() ?: 5f,
                stampDateBrightness = sharedPrefs.getString("stamp_brightness", "50")?.toFloatOrNull() ?: 50f,
                stampDateContrast = sharedPrefs.getString("stamp_contrast", "50")?.toFloatOrNull() ?: 50f,

                // Configuración Sello 2 (Registrador)
                stampRegistrarEnabled = sharedPrefs.getBoolean("stamp2_enabled", true),
                stampRegistrarFontSize = sharedPrefs.getString("stamp2_font_size", "13")?.toFloatOrNull() ?: 13f,
                stampRegistrarOffsetX = sharedPrefs.getString("stamp2_offset_x", "0")?.toFloatOrNull() ?: 0f,
                stampRegistrarOffsetY = sharedPrefs.getString("stamp2_offset_y", "0")?.toFloatOrNull() ?: 0f,
                stampRegistrarVariableRotation = sharedPrefs.getBoolean("stamp2_variable_rotation", true),
                stampRegistrarRotation = sharedPrefs.getString("stamp2_rotation", "0")?.toFloatOrNull() ?: 0f,
                stampRegistrarRotationTolerance = sharedPrefs.getString("stamp2_rotation_tolerance", "5")?.toFloatOrNull() ?: 5f,
                stampRegistrarWearIntensity = sharedPrefs.getFloat("stamp2_wear_intensity", 30f),
                stampRegistrarWearSize = sharedPrefs.getFloat("stamp2_wear_size", 50f),
                stampRegistrarDotCount = sharedPrefs.getString("stamp2DotCount", "3")?.toFloatOrNull() ?: 3f,
                stampRegistrarDotSize = sharedPrefs.getString("stamp2DotSize", "13")?.toFloatOrNull() ?: 13f,
                stampRegistrarPointTextSeparation = sharedPrefs.getString("stamp2_point_text_separation", "5")?.toFloatOrNull() ?: 5f,
                stampRegistrarBrightness = sharedPrefs.getString("stamp2Brightness", "50")?.toFloatOrNull() ?: 50f,
                stampRegistrarContrast = sharedPrefs.getString("stamp2Contrast", "50")?.toFloatOrNull() ?: 50f,

                // Configuración Firma Principal
                signatureEnabled = sharedPrefs.getBoolean("signature_enabled", false),
                signatureImageUri = sharedPrefs.getString("signature_image_uri_primary", null),
                signatureOffsetX = sharedPrefs.getString("signature_offset_x", "0")?.toFloatOrNull() ?: 0f,
                signatureOffsetY = sharedPrefs.getString("signature_offset_y", "0")?.toFloatOrNull() ?: 0f,
                signatureScale = sharedPrefs.getFloat("signature_scale", 100f),
                signatureRotation = sharedPrefs.getFloat("signature_rotation", 0f),
                signaturePoints = sharedPrefs.getString("signature_markers_primary", "") ?: "",

                // Configuración Firma Secundaria
                signature2Enabled = sharedPrefs.getBoolean("signature_enabled_secondary", false), // Suponiendo una clave separada
                signature2ImageUri = sharedPrefs.getString("signature_image_uri_secondary", null),
                signature2OffsetX = sharedPrefs.getString("signature_offset_x_secondary", "0")?.toFloatOrNull() ?: 0f,
                signature2OffsetY = sharedPrefs.getString("signature_offset_y_secondary", "0")?.toFloatOrNull() ?: 0f,
                signature2Scale = sharedPrefs.getFloat("signature_scale_secondary", 100f),
                signature2Rotation = sharedPrefs.getFloat("signature_rotation_secondary", 0f),
                signature2Points = sharedPrefs.getString("signature_markers_secondary", "") ?: ""
            )
            FirestoreService.addRegistrador(initialRegistrador)
        }
    }
}