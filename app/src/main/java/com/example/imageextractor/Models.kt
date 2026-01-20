package com.example.imageextractor

import android.net.Uri
import android.os.Parcelable
import com.google.firebase.firestore.DocumentId
import kotlinx.parcelize.Parcelize

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
@Parcelize
data class Registrador(
    @DocumentId var id: String = "",
    var nombre: String = "",
    var cargo: String = "",
    var zonaRegistral: String = "",

    // Ajustes Sello 1 (Fecha)
    var stampDateEnabled: Boolean = true,
    var stampDateOnFirstLast: Boolean = true,
    var stampDateFontSize: Float = 220f,
    var stampDateWearIntensity: Float = 30f,
    var stampDateWearSize: Float = 50f,
    var stampDateSizePercent: Float = 20f,
    var stampDateMaxRotation: Float = 5f,
    var stampDateBrightness: Float = 50f,
    var stampDateContrast: Float = 50f,

    // Ajustes Sello 2 (Registrador)
    var stampRegistrarEnabled: Boolean = true,
    var stampRegistrarFontSize: Float = 13f,
    var stampRegistrarOffsetX: Float = 0f,
    var stampRegistrarOffsetY: Float = 0f,
    var stampRegistrarVariableRotation: Boolean = true,
    var stampRegistrarRotation: Float = 0f,
    var stampRegistrarRotationTolerance: Float = 5f,
    var stampRegistrarWearIntensity: Float = 30f,
    var stampRegistrarWearSize: Float = 50f,
    var stampRegistrarDotCount: Float = 3f,
    var stampRegistrarDotSize: Float = 13f,
    var stampRegistrarDotSpacing: Float = 13f, // Nueva propiedad
    var stampRegistrarPointTextSeparation: Float = 5f,
    var stampRegistrarBrightness: Float = 50f,
    var stampRegistrarContrast: Float = 50f,

    // Ajustes Firma Principal
    var signatureEnabled: Boolean = false,
    var signatureImageUri: String? = null,
    var signatureOffsetX: Float = 0f,
    var signatureOffsetY: Float = 0f,
    var signatureScale: Float = 100f,
    var signatureRotation: Float = 0f,
    var signaturePoints: String = "", // Puntos de la firma procedural serializados
    var signatureWearIntensity: Float = 30f,
    var signatureWearSize: Float = 50f,

    // Ajustes Firma Secundaria (se deja la estructura lista)
    var signature2Enabled: Boolean = false,
    var signature2ImageUri: String? = null,
    var signature2OffsetX: Float = 0f,
    var signature2OffsetY: Float = 0f,
    var signature2Scale: Float = 100f,
    var signature2Rotation: Float = 0f,
    var signature2Points: String = "",
    var signature2WearIntensity: Float = 30f,
    var signature2WearSize: Float = 50f
) : Parcelable {
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
        stampRegistrarDotSpacing = 13f, stampRegistrarPointTextSeparation = 5f, stampRegistrarBrightness = 50f, stampRegistrarContrast = 50f,
        signatureEnabled = false, signatureImageUri = null, signatureOffsetX = 0f, signatureOffsetY = 0f,
        signatureScale = 100f, signatureRotation = 0f, signaturePoints = "", signatureWearIntensity = 30f, signatureWearSize = 50f,
        signature2Enabled = false, signature2ImageUri = null, signature2OffsetX = 0f, signature2OffsetY = 0f,
        signature2Scale = 100f, signature2Rotation = 0f, signature2Points = "", signature2WearIntensity = 30f, signature2WearSize = 50f
    )
}
