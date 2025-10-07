package com.example.imageextractor

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.imageextractor.databinding.FragmentExtractionBinding
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExtractionFragment : Fragment() {

    private inner class JsBridge {
        @JavascriptInterface
        fun notifyUrlChanged() {
            activity?.runOnUiThread {
                if (isViewDestroyed) return@runOnUiThread
                binding.webView?.url?.let {
                    updateButtonStates(it)
                }
            }
        }

        @JavascriptInterface
        fun updateNavigationState(isFirst: Boolean, isLast: Boolean) {
            activity?.runOnUiThread {
                if (isViewDestroyed) return@runOnUiThread
                binding.fabGoToFirstItem.isEnabled = !isFirst
                binding.fabPrevious.isEnabled = !isFirst
                binding.fabGoToLastItem.isEnabled = !isLast
                binding.fabNext.isEnabled = !isLast
            }
        }
    }

    private var _binding: FragmentExtractionBinding? = null
    private val binding get() = _binding!!
    private var isViewDestroyed = false

    private val sharedViewModel: SharedViewModel by activityViewModels()

    private val loginUrl = "https://conoce-aqui.sunarp.gob.pe/conoce-aqui/inicio"
    private val searchUrl = "https://conoce-aqui.sunarp.gob.pe/conoce-aqui/servicio/busqueda"
    private val resultsUrlSubstring = "/servicio/busqueda/visualizar-partida"
    private var currentPageUrl: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExtractionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isViewDestroyed = false
        setupWebView()
        setupButtons()
    }

    private fun setupWebView() {
        binding.webView.settings.javaScriptEnabled = true
        binding.webView.addJavascriptInterface(JsBridge(), "AndroidBridge")
        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (isViewDestroyed) return
                currentPageUrl = url
                updateButtonStates(url)
            }
        }
        binding.webView.loadUrl(loginUrl)
    }

    private fun updateButtonStates(url: String?) {
        if (isViewDestroyed) return

        val isResultsPage = url?.contains(resultsUrlSubstring) == true
        val isLoginPage = url == loginUrl || url?.startsWith(searchUrl) == true

        binding.actionButton.visibility = if (isResultsPage || isLoginPage) View.VISIBLE else View.GONE

        if (isResultsPage) {
            binding.actionButton.setImageResource(android.R.drawable.ic_menu_camera)
            binding.actionButton.contentDescription = "Capturar Pantalla"
        } else if (isLoginPage) {
            binding.actionButton.setImageResource(android.R.drawable.ic_menu_edit)
            binding.actionButton.contentDescription = "Autocompletar Datos"
        }

        binding.fabGoToFirstItem.visibility = if (isResultsPage) View.VISIBLE else View.GONE
        binding.fabGoToLastItem.visibility = if (isResultsPage) View.VISIBLE else View.GONE
        binding.fabPrevious.visibility = if (isResultsPage) View.VISIBLE else View.GONE
        binding.fabNext.visibility = if (isResultsPage) View.VISIBLE else View.GONE

        binding.extractButton.visibility = View.GONE

        if (isResultsPage) {
            binding.fabGoToLastItem.isEnabled = false
            binding.fabNext.isEnabled = false
            binding.fabGoToFirstItem.isEnabled = true
            binding.fabPrevious.isEnabled = true
        }
    }

    private fun setupButtons() {
        binding.actionButton.setOnClickListener {
            val url = currentPageUrl
            if (url != null && url.contains(resultsUrlSubstring)) {
                captureVisibleCanvas()
            } else {
                autofillCurrentPage()
            }
        }

        binding.extractButton.setOnClickListener { extractImagesFromPartida() }

        binding.fabGoToFirstItem.setOnClickListener { navigateTo("first") }
        binding.fabGoToLastItem.setOnClickListener { navigateTo("last") }
        binding.fabPrevious.setOnClickListener { navigateTo("previous") }
        binding.fabNext.setOnClickListener { navigateTo("next") }
    }

    private fun captureVisibleCanvas() {
        val script = """
            (function() {
                const canvas = document.querySelector('canvas:not([style*="display: none"])');
                if (canvas && canvas.offsetParent !== null && canvas.width > 100 && canvas.height > 100) {
                    try {
                        return canvas.toDataURL('image/png');
                    } catch(e) {
                        return 'error:' + e.message;
                    }
                }
                return 'error:NoCanvas';
            })();
        """
        binding.webView.evaluateJavascript(script) { result ->
            activity?.runOnUiThread {
                if (isViewDestroyed) return@runOnUiThread
                if (result != null && result != "null") {
                    val cleanResult = result.trim('"')
                                            .replace("\\u003d", "=")
                                            .replace("\\u002B", "+")
                                            .replace("\\/", "/")

                    if (cleanResult.startsWith("error:")) {
                        val errorMessage = cleanResult.substringAfter("error:")
                        val displayMessage = if (errorMessage == "NoCanvas") "No se encontró un canvas visible para capturar." else errorMessage
                        Toast.makeText(requireContext().applicationContext, displayMessage, Toast.LENGTH_LONG).show()
                    } else {
                        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                        val timestamp = sdf.format(Date())
                        val filename = "captura_sunarp_$timestamp.png"
                        saveImageFromDataUrl(cleanResult, filename)
                    }
                } else {
                    Toast.makeText(requireContext().applicationContext, "Error: no se recibió respuesta de la página.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun navigateTo(direction: String) {
        val script = """
            ((direction) => {
                function getNavigableItems() {
                    return Array.from(document.querySelectorAll('.columna-lista'));
                }

                function findClickableChild(element) {
                    return element.querySelector('.ant-collapse-header, .pagina .boton-pagina, .pagina a');
                }

                function realisticClick(element) {
                    try {
                        element.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true }));
                        element.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true }));
                        element.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
                        return true;
                    } catch (e) { return false; }
                }

                function getCurrentItemIndex(items) {
                    const titleElement = document.querySelector('.visor-subtitle');
                    if (!titleElement) return 0;

                    const titleText = (titleElement.innerText || "").trim().toLowerCase();
                    const asientoMatch = titleText.match(/asiento (\d+)/);
                    const tomoMatch = titleText.match(/tomo: (\d+)/);
                    const folioMatch = titleText.match(/folio: (\d+)/);
                    const fichaMatch = titleText.match(/ficha: (\d+)/);

                    for (let i = 0; i < items.length; i++) {
                        const itemText = (items[i].innerText || "").toLowerCase();
                        let isMatch = false;
                        if (asientoMatch && itemText.includes(`n° asiento: ${'$'}{asientoMatch[1]}`)) {
                            isMatch = true;
                        } else if (tomoMatch && folioMatch && itemText.includes(`tomo: ${'$'}{tomoMatch[1]}`) && itemText.includes(`folio: ${'$'}{folioMatch[1]}`)) {
                            isMatch = true;
                        } else if (fichaMatch && itemText.includes(`ficha: ${'$'}{fichaMatch[1]}`)) {
                            isMatch = true;
                        }
                        if (isMatch) return i;
                    }
                    return 0;
                }

                try {
                    const items = getNavigableItems();
                    if (items.length === 0) return JSON.stringify({ success: false, error: "No se encontraron elementos de navegación." });

                    const currentIndex = getCurrentItemIndex(items);
                    let targetIndex = -1;

                    switch (direction) {
                        case 'first': targetIndex = items.length - 1; break;
                        case 'last': targetIndex = 0; break;
                        case 'next': if (currentIndex > 0) targetIndex = currentIndex - 1; break;
                        case 'previous': if (currentIndex < items.length - 1) targetIndex = currentIndex + 1; break;
                    }

                    if (targetIndex === -1) return JSON.stringify({ success: false, error: "Ya estás en el extremo de la navegación." });

                    const targetItem = items[targetIndex];
                    if (!targetItem.classList.contains('ant-collapse-item-active')) {
                        const header = targetItem.querySelector('.ant-collapse-header');
                        if(header) realisticClick(header);
                    }

                    const clickable = findClickableChild(targetItem);
                    if (!clickable) return JSON.stringify({ success: false, error: "No se encontró un elemento clickeable." });
                    if (!realisticClick(clickable)) return JSON.stringify({ success: false, error: "El clic en el destino falló." });

                    const isFirst = (targetIndex === items.length - 1);
                    const isLast = (targetIndex === 0);

                    if (typeof AndroidBridge !== 'undefined') AndroidBridge.updateNavigationState(isFirst, isLast);

                    return JSON.stringify({ success: true });
                } catch (e) {
                    return JSON.stringify({ success: false, error: e.message });
                }
            })('$direction');
        """.trimIndent()

        binding.webView.evaluateJavascript(script) { result ->
            activity?.runOnUiThread {
                if (isViewDestroyed) return@runOnUiThread
                try {
                    val json = org.json.JSONObject(result)
                    if (!json.getBoolean("success")) {
                        val error = json.optString("error", "Error desconocido.")
                        Toast.makeText(requireContext().applicationContext, "Error en la navegación: $error", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext().applicationContext, "Error al procesar la respuesta de navegación.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun autofillCurrentPage() {
        val currentUrl = binding.webView.url
        when {
            currentUrl == loginUrl -> {
                val newLoginData = sharedViewModel.getRandomLoginData()
                sharedViewModel.config.value?.let {
                    sharedViewModel.setExtractionConfig(it.copy(loginData = newLoginData))
                }
                autofillLoginForm(newLoginData)
            }
            currentUrl?.startsWith(searchUrl) == true -> {
                sharedViewModel.config.value?.let {
                    autofillSearchForm(it)
                } ?: Toast.makeText(requireContext().applicationContext, "No hay configuración de búsqueda guardada.", Toast.LENGTH_SHORT).show()
            }
            else -> Toast.makeText(requireContext().applicationContext, "No hay formulario para autocompletar en esta página.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun autofillLoginForm(loginData: LoginData) {
        val jsScript = """
            (function() {
                document.querySelector('input[formcontrolname="numeroDocumento"]').value = '${loginData.dni}';
                document.querySelector('input[formcontrolname="digito"]').value = '${loginData.digito}';
                document.querySelector('input[formcontrolname="fechaEmision"]').value = '${loginData.fechaEmision}';
                ['input', 'blur'].forEach(eventName => {
                    document.querySelectorAll('input').forEach(input => input.dispatchEvent(new Event(eventName, { bubbles: true })));
                });
                document.querySelector('button[class*="btn-sunarp-green"]').click();
            })();
        """.trimIndent()
        binding.webView.evaluateJavascript(jsScript, null)
    }

    private fun autofillSearchForm(config: ExtractionConfig) {
        val jsScript = """
            (async function() {
                function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }
                // ... (rest of the script is omitted for brevity as it's unchanged)
            })();
        """.trimIndent()
        binding.webView.evaluateJavascript(jsScript, null)
    }

    private fun extractImagesFromPartida() {
        if(isViewDestroyed) return
        Toast.makeText(requireContext().applicationContext, "Iniciando extracción detallada...", Toast.LENGTH_SHORT).show()
        val jsScript = """
            (async function() {
                // ... (rest of the script is omitted for brevity as it's unchanged)
            })();
        """.trimIndent()
        binding.webView.evaluateJavascript(jsScript) { result ->
            activity?.runOnUiThread {
                if (isViewDestroyed) return@runOnUiThread
                try {
                    if (result == null || result == "null" || result == "[]") {
                        Toast.makeText(requireContext().applicationContext, "No se extrajeron imágenes o hubo un error.", Toast.LENGTH_LONG).show()
                        return@runOnUiThread
                    }

                    val imagesArray = JSONArray(result)
                    if (imagesArray.length() == 0) {
                        Toast.makeText(requireContext().applicationContext, "No se encontraron imágenes para guardar.", Toast.LENGTH_LONG).show()
                        return@runOnUiThread
                    }

                    val savedImagePaths = mutableListOf<String>()
                    for (i in 0 until imagesArray.length()) {
                        val imageObject = imagesArray.getJSONObject(i)
                        val filename = imageObject.getString("filename")
                        val dataUrl = imageObject.getString("dataUrl")
                        saveImageFromDataUrl(dataUrl, filename)?.let { path ->
                            savedImagePaths.add(path)
                        }
                    }

                    sharedViewModel.setImageUrls(savedImagePaths)
                    Toast.makeText(requireContext().applicationContext, "${'$'}{savedImagePaths.size} imágenes guardadas exitosamente.", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack(R.id.mainMenuFragment, false)

                } catch (e: Exception) {
                    Toast.makeText(requireContext().applicationContext, "Error al procesar o guardar las imágenes: ${'$'}{e.message}", Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                }
            }
        }
    }

    private fun saveImageFromDataUrl(dataUrl: String, filename: String): String? {
        if(isViewDestroyed) return null
        val base64Data = dataUrl.substring(dataUrl.indexOf(",") + 1)
        val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/capturas_sunarp")
            }
            val resolver = context?.contentResolver ?: return null
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                try {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(decodedBytes)
                    }
                    Toast.makeText(requireContext().applicationContext, "Captura guardada en Descargas/capturas_sunarp", Toast.LENGTH_LONG).show()
                    return it.toString()
                } catch (e: Exception) {
                    Log.e("SaveImage", "Error guardando con MediaStore: ${e.message}", e)
                    Toast.makeText(requireContext().applicationContext, "Error al guardar la captura.", Toast.LENGTH_SHORT).show()
                    return null
                }
            }
            return null
        } else {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val imageDir = File(downloadsDir, "capturas_sunarp")
                if (!imageDir.exists()) {
                    imageDir.mkdirs()
                }
                val imageFile = File(imageDir, filename)
                FileOutputStream(imageFile).use { out ->
                    out.write(decodedBytes)
                }
                Toast.makeText(requireContext().applicationContext, "Captura guardada en Descargas/capturas_sunarp", Toast.LENGTH_LONG).show()
                return imageFile.absolutePath
            } catch (e: Exception) {
                Log.e("SaveImage", "Error guardando imagen: ${e.message}", e)
                Toast.makeText(requireContext().applicationContext, "Error al guardar la captura.", Toast.LENGTH_SHORT).show()
                return null
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isViewDestroyed = true
        _binding = null
    }
}