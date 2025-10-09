package com.example.imageextractor

/**
 * Represents a folder containing a collection of images for a specific "partida".
 * @param partidaId The unique identifier for the folder, typically the "partida" number.
 * @param imagePaths A list of file paths for the images contained within this folder.
 */
data class ImageFolder(val partidaId: String, val imagePaths: List<String>)