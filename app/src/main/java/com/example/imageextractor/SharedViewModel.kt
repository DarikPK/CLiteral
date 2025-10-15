package com.example.imageextractor

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlin.random.Random

// Modelo para los datos de acceso
data class LoginData(val dni: String, val digito: String, val fechaEmision: String)

// Modelo para la configuración completa de la búsqueda
data class ExtractionConfig(
    val loginData: LoginData,
    val oficina: String,
    val areaRegistral: String,
    val numeroPartida: String
)

class SharedViewModel : ViewModel() {

    // --- Datos de Configuración ---
    private val _config = MutableLiveData<ExtractionConfig>()
    val config: LiveData<ExtractionConfig> = _config

    fun setExtractionConfig(config: ExtractionConfig) {
        _config.value = config
    }

    // --- URLs de Imágenes Extraídas ---
    private val _imageUrls = MutableLiveData<List<String>>()
    val imageUrls: LiveData<List<String>> = _imageUrls

    fun setImageUrls(urls: List<String>) {
        _imageUrls.value = urls
    }

    // --- Estado de Visibilidad de WebView ---
    private val _isWebViewVisible = MutableLiveData(true)
    val isWebViewVisible: LiveData<Boolean> = _isWebViewVisible

    fun toggleWebViewVisibility() {
        _isWebViewVisible.value = !(_isWebViewVisible.value ?: true)
    }

    fun setIsWebViewVisible(isVisible: Boolean) {
        _isWebViewVisible.value = isVisible
    }

    // --- Estado de Visibilidad del Botón de Inicio Manual ---
    private val _isManualStartButtonVisible = MutableLiveData(true)
    val isManualStartButtonVisible: LiveData<Boolean> = _isManualStartButtonVisible

    fun toggleManualStartButtonVisibility() {
        _isManualStartButtonVisible.value = !(_isManualStartButtonVisible.value ?: true)
    }

    // --- Base de Datos para Login Aleatorio ---
    private val randomLoginDatabase = listOf(
        LoginData("46736604", "7", "16/04/2025"),
        LoginData("09842596", "4", "23/06/2022"),
        LoginData("72049916", "4", "31/01/2022"),
        LoginData("72577185", "7", "19/03/2025"),
        LoginData("07784169", "1", "10/01/2023"),
        LoginData("10376014", "9", "08/11/2019"),
        LoginData("41363599", "9", "04/12/2023"),
        LoginData("70312268", "5", "18/12/2023"),
        LoginData("43128393", "5", "20/03/2018"),
        LoginData("10126300", "8", "06/06/2018"),
        LoginData("72291969", "8", "30/12/2022"),
        LoginData("70519334", "2", "05/09/2024"),
        LoginData("45490505", "1", "15/11/2021"),
        LoginData("70341485", "6", "30/06/2020"),
        LoginData("43373773", "9", "22/10/2021")
    )

    fun getRandomLoginData(): LoginData {
        return randomLoginDatabase[Random.nextInt(randomLoginDatabase.size)]
    }

    // --- Listas para Dropdowns ---
    private val officeList = listOf(
        "ABANCAY", "ANDAHUAYLAS", "AREQUIPA", "AYACUCHO", "BAGUA", "BARRANCA", "CAJAMARCA",
        "CALLAO", "CAMANA", "CASMA", "CASTILLA _ APLAO", "CAÑETE", "CHACHAPOYAS", "CHEPEN",
        "CHICLAYO", "CHIMBOTE", "CHINCHA", "CUSCO", "HUACHO", "HUANCAVELICA", "HUANCAYO",
        "HUANUCO", "HUARAL", "HUARAZ", "ICA", "IQUITOS", "JAEN", "JAUJA", "JULIACA",
        "LA MERCED", "LIMA", "LORETO", "MADRE DE DIOS", "MOLLENDO", "MOQUEGUA", "MOYOBAMBA",
        "NASCA", "OXAPAMPA", "PACASMAYO", "PASCO", "PISCO", "PIURA", "PUCALLPA", "PUNO",
        "QUILLABAMBA", "SATIPO", "SICUANI", "SULLANA", "TACNA", "TARAPOTO", "TARMA", "TUMBES",
        "YURIMAGUAS"
    ).map { it.toUpperCase() }

    fun getOfficeListJson(): String {
        // Crea una cadena JSON a partir de la lista
        return officeList.joinToString(prefix = "[\"", separator = "\",\"", postfix = "\"]")
    }
}