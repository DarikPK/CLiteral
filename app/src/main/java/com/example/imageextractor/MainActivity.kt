package com.example.imageextractor

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.imageextractor.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth
        signInAnonymously()
    }

    private fun signInAnonymously() {
        // Si ya hay un usuario, no es necesario volver a iniciar sesión.
        if (auth.currentUser != null) {
            Log.d("Auth", "User already signed in anonymously.")
            lifecycleScope.launch {
                migrateInitialRegistrador()
            }
            return
        }

        auth.signInAnonymously()
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("Auth", "signInAnonymously:success")
                    lifecycleScope.launch {
                        migrateInitialRegistrador()
                    }
                } else {
                    Log.w("Auth", "signInAnonymously:failure", task.exception)
                    Toast.makeText(baseContext, "Authentication failed. Please enable Anonymous Auth in Firebase.", Toast.LENGTH_LONG).show()
                }
            }
    }

    /**
     * Comprueba si es necesario crear el primer registrador y lo migra
     * desde SharedPreferences a Firestore. Esta operación se ejecuta solo una vez.
     */
    private suspend fun migrateInitialRegistrador() {
        if (FirestoreService.isRegistradoresCollectionEmpty()) {
            val sharedPrefs = getSharedPreferences("PdfSettings", Context.MODE_PRIVATE)

            val nombre = sharedPrefs.getString("stamp2_name", "ALFARO MORILLO MELISSA KARINA") ?: "ALFARO MORILLO MELISSA KARINA"
            val cargo = sharedPrefs.getString("stamp2_position", "Registrador Público") ?: "Registrador Público"
            val zona = sharedPrefs.getString("stamp2_area", "Zona Registral N° IX - Sede Lima") ?: "Zona Registral N° IX - Sede Lima"

            val initialRegistrador = Registrador(
                nombre = nombre,
                cargo = cargo,
                zonaRegistral = zona,
                stampDateEnabled = sharedPrefs.getBoolean("stamp_enabled", true),
                stampDateOnFirstLast = sharedPrefs.getBoolean("stamp_on_first_last", true),
                stampDateFontSize = sharedPrefs.getString("stamp_font_size", "220")?.toFloatOrNull() ?: 220f,
                stampDateWearIntensity = sharedPrefs.getFloat("stamp_wear_intensity", 30f),
                stampDateWearSize = sharedPrefs.getFloat("stamp_wear_size", 50f),
                stampDateSizePercent = sharedPrefs.getString("stamp_size", "20")?.toFloatOrNull() ?: 20f,
                stampDateMaxRotation = sharedPrefs.getString("stamp_rotation", "5")?.toFloatOrNull() ?: 5f,
                stampDateBrightness = sharedPrefs.getString("stamp_brightness", "50")?.toFloatOrNull() ?: 50f,
                stampDateContrast = sharedPrefs.getString("stamp_contrast", "50")?.toFloatOrNull() ?: 50f,
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
                signatureEnabled = sharedPrefs.getBoolean("signature_enabled", false),
                signatureImageUri = sharedPrefs.getString("signature_image_uri_primary", null),
                signatureOffsetX = sharedPrefs.getString("signature_offset_x", "0")?.toFloatOrNull() ?: 0f,
                signatureOffsetY = sharedPrefs.getString("signature_offset_y", "0")?.toFloatOrNull() ?: 0f,
                signatureScale = sharedPrefs.getFloat("signature_scale", 100f),
                signatureRotation = sharedPrefs.getFloat("signature_rotation", 0f),
                signaturePoints = sharedPrefs.getString("signature_markers_primary", "") ?: "",
                signature2Enabled = sharedPrefs.getBoolean("signature_enabled_secondary", false),
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
