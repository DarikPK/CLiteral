package com.example.imageextractor

import android.net.Uri

/**
 * Represents a single image file, containing its URI for modern API access
 * and its file path for compatibility.
 */
data class ImageFile(
    val uri: Uri,
    val path: String,
    val name: String
)

/**
 * Represents a folder containing a collection of images for a specific "partida".
 * @param partidaId The unique identifier for the folder, typically the "partida" number.
 * @param imageFiles A list of ImageFile objects contained within this folder.
 */
data class ImageFolder(
    val partidaId: String,
    val imageFiles: List<ImageFile>,
    var tipoPartida: String? = null,
    var lastModified: Long = 0L
)

/**
 * Representa el perfil de un registrador con todos sus datos asociados
 * para ser guardado en Firebase.
 */
data class RegistrarProfile(
    val id: String = "",
    val name: String = "",
    val position: String = "",
    val area: String = "",
    val office: String = "",
    val signatureMarkers: String = "", // Serialized markers string
    val signatureRotation: Float = 0f,
    val signatureRotationTolerance: Float = 0f,
    val signatureWhiteThreshold: Float = 210f,
    val signatureSizeX: Float = 50f,
    val signatureSizeY: Float = 20f,
    val signatureOffsetX: String = "0",
    val signatureOffsetY: String = "0",
    val signatureTypeLoaded: Boolean = false,
    val signatureEnabled: Boolean = true,
    val signatureStrokeWidth: Float = 5f,
    val signatureNumMarkers: Int = 15,
    val signatureMarkerSize: Float = 10f,
    // Firma secundaria (para páginas posteriores)
    val signatureMarkersSecondary: String = "", // Serialized markers string
    val signatureRotationSecondary: Float = 0f,
    val signatureRotationToleranceSecondary: Float = 0f,
    val signatureWhiteThresholdSecondary: Float = 210f,
    val signatureSizeXSecondary: Float = 50f,
    val signatureSizeYSecondary: Float = 20f,
    val signatureOffsetXSecondary: String = "0",
    val signatureOffsetYSecondary: String = "0",
    val signatureTypeLoadedSecondary: Boolean = false,
    val signatureEnabledSecondary: Boolean = true,
    val signatureStrokeWidthSecondary: Float = 5f,
    val signatureNumMarkersSecondary: Int = 15,
    val signatureMarkerSizeSecondary: Float = 10f,
    // Registro de firmas cargadas (URIs)
    val signatureImageUris: List<String> = emptyList(),
    val signatureImageUrisSecondary: List<String> = emptyList(),
    // Imágenes de firma guardadas en Base64 (Firebase)
    val signatureImagesBase64: List<String> = emptyList(),
    val signatureImagesBase64Secondary: List<String> = emptyList(),
    // Opcional: Otros ajustes de sello como tamaño de fuente, etc.
    val stamp2FontSize: String = "13",
    val stamp2WearIntensity: Float = 30f,
    val stamp2WearSize: Float = 50f,
    // Variabilidad Sello 2
    val stamp2VariableRotation: Boolean = true,
    val stamp2Rotation: String = "0",
    val stamp2RotationTolerance: String = "5",
    val stamp2TranslationToleranceX: String = "0",
    val stamp2TranslationToleranceY: String = "0"
)

/**
 * Representa los datos de un usuario (DNI) utilizados para el acceso.
 */
data class UserProfile(
    val id: String = "",
    val dni: String = "",
    val digitoVerificador: String = "",
    val fechaExpedicion: String = "",
    val label: String = "" // Etiqueta opcional para identificar el DNI
)
