package com.example.imageextractor

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SharedViewModel : ViewModel() {

    // LiveData que contendrá la lista de URLs de imágenes extraídas.
    // Es privado para que solo pueda ser modificado desde este ViewModel.
    private val _imageUrls = MutableLiveData<List<String>>()

    // Versión pública y de solo lectura de los datos para que los Fragments la observen.
    val imageUrls: LiveData<List<String>> = _imageUrls

    // Función para actualizar la lista de imágenes desde el ExtractionFragment.
    fun setImageUrls(urls: List<String>) {
        _imageUrls.value = urls
    }
}