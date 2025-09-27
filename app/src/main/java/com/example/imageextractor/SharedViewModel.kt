package com.example.imageextractor

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

// Modelo de datos para almacenar toda la configuración de la extracción.
data class ExtractionConfig(
    val dni: String,
    val digito: String,
    val fechaEmision: String,
    val oficina: String,
    val areaRegistral: String,
    val numeroPartida: String
)

class SharedViewModel : ViewModel() {

    // Contiene la configuración completa para una sesión de extracción.
    private val _config = MutableLiveData<ExtractionConfig>()
    val config: LiveData<ExtractionConfig> = _config

    // Contiene las URLs de las imágenes una vez extraídas.
    private val _imageUrls = MutableLiveData<List<String>>()
    val imageUrls: LiveData<List<String>> = _imageUrls

    fun setExtractionConfig(config: ExtractionConfig) {
        _config.value = config
    }

    fun setImageUrls(urls: List<String>) {
        _imageUrls.value = urls
    }
}