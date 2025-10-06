package com.example.imageextractor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Environment
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
                binding.webView?.url?.let {
                    updateButtonStates(it)
                }
            }
        }

        @JavascriptInterface
        fun updateNavigationState(isFirst: Boolean, isLast: Boolean) {
            activity?.runOnUiThread {
                binding.fabGoToFirstItem.isEnabled = !isFirst
                binding.fabPrevious.isEnabled = !isFirst
                binding.fabGoToLastItem.isEnabled = !isLast
                binding.fabNext.isEnabled = !isLast
            }
        }
    }

    private var _binding: FragmentExtractionBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SharedViewModel by activityViewModels()

    private val loginUrl = "https://conoce-aqui.sunarp.gob.pe/conoce-aqui/inicio"
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
        setupWebView()
        setupButtons()
    }

    private fun setupWebView() {
        binding.webView.settings.javaScriptEnabled = true
        binding.webView.addJavascriptInterface(JsBridge(), "AndroidBridge")
        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                currentPageUrl = url
                updateButtonStates(url)
            }
        }
        binding.webView.loadUrl(loginUrl)
    }

    private fun updateButtonStates(url: String?) {
        val isResultsPage = url?.contains(resultsUrlSubstring) == true

        // Screenshot button is only visible on results page
        binding.actionButton.visibility = if (isResultsPage) View.VISIBLE else View.GONE

        // Navigation buttons are also only visible on results page
        binding.fabGoToFirstItem.visibility = if (isResultsPage) View.VISIBLE else View.GONE
        binding.fabGoToLastItem.visibility = if (isResultsPage) View.VISIBLE else View.GONE
        binding.fabPrevious.visibility = if (isResultsPage) View.VISIBLE else View.GONE
        binding.fabNext.visibility = if (isResultsPage) View.VISIBLE else View.GONE

        binding.extractButton.visibility = View.GONE

        if (isResultsPage) {
            // Initial state: user starts at the last item (most recent), which is the first in the DOM.
            binding.fabGoToLastItem.isEnabled = false
            binding.fabNext.isEnabled = false
            binding.fabGoToFirstItem.isEnabled = true
            binding.fabPrevious.isEnabled = true
        }
    }

    private fun setupButtons() {
        binding.actionButton.setOnClickListener {
            if (currentPageUrl?.contains(resultsUrlSubstring) == true) {
                captureScreenshot()
            }
        }

        binding.extractButton.setOnClickListener { extractImagesFromPartida() }

        binding.fabGoToFirstItem.setOnClickListener { navigateTo("first") }
        binding.fabGoToLastItem.setOnClickListener { navigateTo("last") }
        binding.fabPrevious.setOnClickListener { navigateTo("previous") }
        binding.fabNext.setOnClickListener { navigateTo("next") }
    }

    private fun captureScreenshot() {
        val webView = binding.webView
        val bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        webView.draw(canvas)

        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = sdf.format(Date())
        val filename = "captura_sunarp_$timestamp.png"
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val imageDir = File(downloadsDir, "capturas_sunarp")
        if (!imageDir.exists()) {
            imageDir.mkdirs()
        }
        val imageFile = File(imageDir, filename)

        try {
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
            Toast.makeText(context, "Captura guardada en Descargas/capturas_sunarp", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("CaptureScreenshot", "Error guardando captura: ${e.message}", e)
            Toast.makeText(context, "Error al guardar la captura.", Toast.SHORT).show()
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
                try {
                    val json = org.json.JSONObject(result)
                    if (!json.getBoolean("success")) {
                        val error = json.optString("error", "Error desconocido.")
                        Toast.makeText(context, "Error en la navegación: $error", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error al procesar la respuesta de navegación.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun extractImagesFromPartida() {
        Toast.makeText(context, "Iniciando extracción detallada...", Toast.LENGTH_SHORT).show()
        val jsScript = """
            (async function() {
                function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

                function realisticClick(element) {
                    try {
                        const events = ['mousedown', 'mouseup', 'click'];
                        events.forEach(type => element.dispatchEvent(new MouseEvent(type, { bubbles: true, cancelable: true })));
                        return true;
                    } catch (e) {
                        console.error("Error en realisticClick:", e);
                        return false;
                    }
                }

                async function waitForVisibleCanvas(timeout = 15000) {
                    const start = Date.now();
                    while (Date.now() - start < timeout) {
                        const canvas = document.querySelector('canvas');
                        if (canvas && canvas.offsetParent !== null && canvas.height > 100 && canvas.width > 100) {
                            await sleep(300);
                            return canvas;
                        }
                        await sleep(100);
                    }
                    throw new Error(`Canvas visible no encontrado en ${'$'}{timeout}ms`);
                }

                const allImageData = [];
                const asientos = Array.from(document.querySelectorAll('.columna-lista .ant-collapse-item'));

                for (let i = 0; i < asientos.length; i++) {
                    const asiento = asientos[i];
                    const asientoNumber = i + 1;

                    if (!asiento.classList.contains('ant-collapse-item-active')) {
                        const header = asiento.querySelector('.ant-collapse-header');
                        if (header) {
                            realisticClick(header);
                            await sleep(500);
                        }
                    }

                    const pageButtons = Array.from(asiento.querySelectorAll('.pagina .boton-pagina'));

                    for (let j = 0; j < pageButtons.length; j++) {
                        const button = pageButtons[j];
                        const pageNumber = (button.textContent || "").trim();

                        if (button.offsetParent === null) continue;

                        realisticClick(button);
                        await sleep(600);

                        try {
                            await waitForVisibleCanvas();
                            const canvases = Array.from(document.querySelectorAll('canvas')).filter(c => c.offsetParent !== null);

                            for (let k = 0; k < canvases.length; k++) {
                                const canvas = canvases[k];
                                const dataUrl = canvas.toDataURL("image/png");
                                const filename = `asiento_${'$'}{asientoNumber}_pagina_${'$'}{pageNumber}_canvas_${'$'}{k + 1}.png`;
                                allImageData.push({ filename, dataUrl });
                            }
                        } catch (e) {
                            console.error(`Error procesando Página ${'$'}{pageNumber} en Asiento ${'$'}{asientoNumber}: ${'$'}{e.message}`);
                        }
                    }
                }

                return JSON.stringify(allImageData);
            })();
        """.trimIndent()
        binding.webView.evaluateJavascript(jsScript) { result ->
            activity?.runOnUiThread {
                try {
                    if (result == null || result == "null" || result == "[]") {
                        Toast.makeText(context, "No se extrajeron imágenes o hubo un error.", Toast.LENGTH_LONG).show()
                        return@runOnUiThread
                    }

                    val imagesArray = JSONArray(result)
                    if (imagesArray.length() == 0) {
                        Toast.makeText(context, "No se encontraron imágenes para guardar.", Toast.LENGTH_LONG).show()
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
                    Toast.makeText(context, "${'$'}{savedImagePaths.size} imágenes guardadas exitosamente.", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack(R.id.mainMenuFragment, false)

                } catch (e: Exception) {
                    Toast.makeText(context, "Error al procesar o guardar las imágenes: ${'$'}{e.message}", Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                }
            }
        }
    }

    private fun saveImageFromDataUrl(dataUrl: String, filename: String): String? {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val imageDir = File(downloadsDir, "capturas_sunarp")
            if (!imageDir.exists()) {
                imageDir.mkdirs()
            }
            val imageFile = File(imageDir, filename)

            val base64Data = dataUrl.substring(dataUrl.indexOf(",") + 1)
            val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)

            FileOutputStream(imageFile).use { out ->
                out.write(decodedBytes)
            }
            imageFile.absolutePath
        } catch (e: Exception) {
            Log.e("SaveImage", "Error guardando imagen: ${'$'}{e.message}", e)
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}