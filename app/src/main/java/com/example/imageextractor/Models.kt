package com.example.imageextractor

import android.net.Uri
import com.google.firebase.firestore.DocumentId

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
 * Representa la configuración completa de un registrador.
 * Esta clase está diseñada para ser almacenada como un documento en Firebase Firestore.
 */
data class Registrador(
    @DocumentId val id: String = "",
    val nombre: String = "",
    val cargo: String = "",
    val zonaRegistral: String = "",

    // Ajustes Sello 1 (Fecha)
    val stampDateEnabled: Boolean = true,
    val stampDateOnFirstLast: Boolean = true,
    val stampDateFontSize: Float = 220f,
    val stampDateWearIntensity: Float = 30f,
    val stampDateWearSize: Float = 50f,
    val stampDateSizePercent: Float = 20f,
    val stampDateMaxRotation: Float = 5f,
    val stampDateBrightness: Float = 50f,
    val stampDateContrast: Float = 50f,

    // Ajustes Sello 2 (Registrador)
    val stampRegistrarEnabled: Boolean = true,
    val stampRegistrarFontSize: Float = 13f,
    val stampRegistrarOffsetX: Float = 0f,
    val stampRegistrarOffsetY: Float = 0f,
    val stampRegistrarVariableRotation: Boolean = true,
    val stampRegistrarRotation: Float = 0f,
    val stampRegistrarRotationTolerance: Float = 5f,
    val stampRegistrarWearIntensity: Float = 30f,
    val stampRegistrarWearSize: Float = 50f,
    val stampRegistrarDotCount: Float = 3f,
    val stampRegistrarDotSize: Float = 13f,
    val stampRegistrarPointTextSeparation: Float = 5f,
    val stampRegistrarBrightness: Float = 50f,
    val stampRegistrarContrast: Float = 50f,

    // Ajustes Firma Principal
    val signatureEnabled: Boolean = false,
    val signatureImageUri: String? = null,
    val signatureOffsetX: Float = 0f,
    val signatureOffsetY: Float = 0f,
    val signatureScale: Float = 100f,
    val signatureRotation: Float = 0f,
    val signaturePoints: String = "", // Puntos de la firma procedural serializados

    // Ajustes Firma Secundaria (se deja la estructura lista)
    val signature2Enabled: Boolean = false,
    val signature2ImageUri: String? = null,
    val signature2OffsetX: Float = 0f,
    val signature2OffsetY: Float = 0f,
    val signature2Scale: Float = 100f,
    val signature2Rotation: Float = 0f,
    val signature2Points: String = ""
) {
    // Constructor sin argumentos requerido por Firestore para la deserialización.
    constructor() : this(
        id = "", nombre = "", cargo = "", zonaRegistral = "",
        stampDateEnabled = true, stampDateOnFirstLast = true, stampDateFontSize = 220f,
        stampDateWearIntensity = 30f, stampDateWearSize = 50f, stampDateSizePercent = 20f,
        stampDateMaxRotation = 5f, stampDateBrightness = 50f, stampDateContrast = 50f,
        stampRegistrarEnabled = true, stampRegistrarFontSize = 13f, stampRegistrarOffsetX = 0f,
        stampRegistrarOffsetY = 0f, stampRegistrarVariableRotation = true, stampRegistrarRotation = 0f,
        stampRegistrarRotationTolerance = 5f, stampRegistrarWearIntensity = 30f,
        stampRegistrarWearSize = 50f, stampRegistrarDotCount = 3f, stampRegistrarDotSize = 13f,
        stampRegistrarPointTextSeparation = 5f, stampRegistrarBrightness = 50f, stampRegistrarContrast = 50f,
        signatureEnabled = false, signatureImageUri = null, signatureOffsetX = 0f, signatureOffsetY = 0f,
        signatureScale = 100f, signatureRotation = 0f, signaturePoints = "",
        signature2Enabled = false, signature2ImageUri = null, signature2OffsetX = 0f, signature2OffsetY = 0f,
        signature2Scale = 100f, signature2Rotation = 0f, signature2Points = ""
    )
}
