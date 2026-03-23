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
    val imageFiles: List<ImageFile>
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
    // Opcional: Otros ajustes de sello como tamaño de fuente, etc.
    val stamp2FontSize: String = "13",
    val stamp2WearIntensity: Float = 30f,
    val stamp2WearSize: Float = 50f
)
